package org.truffleruby.core.array.layout;

import java.util.concurrent.atomic.AtomicInteger;

import org.truffleruby.core.UnsafeHolder;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.profiles.ConditionProfile;

import sun.misc.Unsafe;

/**
 * LayoutLock based on Scalable RW Lock using accessors
 */
public class LayoutLock {

    public static final boolean OPTIMIZE_LC_LC = System.getProperty("optimizeLCLC") != null; // false by default

    public static final LayoutLock GLOBAL_LOCK = new LayoutLock();

    private static final int MAX_THREADS = 600;

    public static final int INACTIVE = 0;
    public static final int WRITE = 1;
    public static final int LAYOUT_CHANGE = 2;

    @CompilationFinal private final Accessor[] accessors = new Accessor[MAX_THREADS];
    private final AtomicInteger nextThread = new AtomicInteger(0);
    private boolean cleanedAfterLayoutChange = true;

    private final static Unsafe unsafe = UnsafeHolder.UNSAFE;

    private static Accessor dummyAccessor = GLOBAL_LOCK.new Accessor(null);

    private static final long stateOffset = UnsafeHolder.getFieldOffset(dummyAccessor.getClass(), "state");
    private static final long dirtyOffset = UnsafeHolder.getFieldOffset(dummyAccessor.getClass(), "dirty");
    private static final long layoutChangeIntendedOffset = UnsafeHolder.getFieldOffset(dummyAccessor.getClass(), "layoutChangeIntended");

    public class Accessor {

        private int state = INACTIVE;
        private int layoutChangeIntended = 0;
        private boolean dirty = false;
        private long l1, l2, l3, l4, l5, l6, l7, l8; // 64 bytes padding


        void setState(int value) {
            unsafe.putIntVolatile(this, stateOffset, value);
        }

        int getState() {
            return unsafe.getIntVolatile(this, stateOffset);
        }

        boolean compareAndSwapState(int expect, int update) {
            return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
        }

        void setDirty(boolean value) {
            unsafe.putBooleanVolatile(this, dirtyOffset, value);
        }

        boolean getDirty() {
            return unsafe.getBooleanVolatile(this, dirtyOffset);
        }

        int getLayoutChangeIntended() {
            return unsafe.getIntVolatile(this, layoutChangeIntendedOffset);
        }

        int getAndIncrementLayoutChangeIntended() {
            return unsafe.getAndAddInt(this, layoutChangeIntendedOffset, 1);
        }

        int getAndDecrementLayoutChangeIntended() {
            return unsafe.getAndAddInt(this, layoutChangeIntendedOffset, -1);
        }

        void setLayoutChangeIntended(int value) {
            unsafe.putIntVolatile(this, layoutChangeIntendedOffset, value);
        }

        private Accessor(LayoutLock layoutLock) {
        }

        public Accessor[] getAccessors() {
            return accessors;
        }

        public int getNextThread() {
            return nextThread.get();
        }

        public boolean getCleanedAfterLayoutChange() {
            if (OPTIMIZE_LC_LC) {
                return cleanedAfterLayoutChange;
            } else {
                return true;
            }
        }

        public void setCleanedAfterLayoutChange(boolean cleaned) {
            if (OPTIMIZE_LC_LC) {
                cleanedAfterLayoutChange = cleaned;
            }
        }

        public void startRead() {
        }

        public boolean finishRead(ConditionProfile dirtyProfile) {
            if (dirtyProfile.profile(getDirty())) {
                resetDirty();
                return false;
            }
            return true;
        }

        public boolean finishRead() {
            if (getDirty()) {
                resetDirty();
                return false;
            }
            return true;
        }

        public void startWrite() {
            while (getLayoutChangeIntended() > 0 || !compareAndSwapState(INACTIVE, WRITE)) {
            }
        }

        public void finishWrite() {
            setState(INACTIVE);
        }

        public int startLayoutChange() {
            final Accessor first = accessors[0];
            if (!first.compareAndSwapState(INACTIVE, LAYOUT_CHANGE)) {
                first.getAndIncrementLayoutChangeIntended();
                while (!first.compareAndSwapState(INACTIVE, LAYOUT_CHANGE)) {
                    yield();
                }
                first.getAndIncrementLayoutChangeIntended();
            }

            final boolean cleaned = cleanedAfterLayoutChange;
            // what if new threads?

            final int n = nextThread.get();

            if (cleaned) {
                for (int i = 1; i < n; i++) {
                    Accessor accessor = accessors[i];
                    while (accessor == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        accessor = accessors[i];
                    }
                    if (!accessor.compareAndSwapState(INACTIVE, LAYOUT_CHANGE)) {
                        accessor.getAndIncrementLayoutChangeIntended();
                        while (!accessor.compareAndSwapState(INACTIVE, LAYOUT_CHANGE)) {
                            yield();
                        }
                        accessor.getAndDecrementLayoutChangeIntended();
                    }
                }

                for (int i = 0; i < n; i++) {
                    accessors[i].setDirty(true);
                }
            } else {
                first.setDirty(true);
            }

            return n;
        }

        public void finishLayoutChange(int n) {
            final Accessor first = accessors[0];
            if (first.getLayoutChangeIntended() > 0) { // Another layout change is going to follow
                cleanedAfterLayoutChange = false;
                first.setState(INACTIVE);
            } else {
                cleanedAfterLayoutChange = true;
                for (int i = n - 1; i >= 0; i--) {
                    accessors[i].setState(INACTIVE);
                }
            }
        }

        private void resetDirty() {
            while (getState() == LAYOUT_CHANGE) {
                yield();
            }
            dirty = false;
            if (getState() == LAYOUT_CHANGE) {
                dirty = true;
            }
        }

    }

    @TruffleBoundary
    public static void yield() {
        Thread.yield();
    }

    public Accessor access() {
        Accessor ac = new Accessor(this);
        final int n = nextThread.getAndIncrement();
        accessors[n] = ac;
        if (n > 0) {
            // Wait for no Layout changes
            while (accessors[0].getState() == LAYOUT_CHANGE) {
                yield();
            }
        }
        return ac;
    }

}
