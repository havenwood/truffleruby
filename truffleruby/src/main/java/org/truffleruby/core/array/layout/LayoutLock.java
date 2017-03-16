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

    private static final Unsafe UNSAFE = UnsafeHolder.UNSAFE;
    private static final long STATE_OFFSET = UnsafeHolder.getFieldOffset(Accessor.class, "state");
    private static final long LAYOUT_CHANGED_INTENDED_OFFSET = UnsafeHolder.getFieldOffset(Accessor.class, "layoutChangeIntended");

    public class Accessor {

        private volatile int state = INACTIVE;
        private volatile int layoutChangeIntended = 0;
        private volatile boolean dirty = false;

        @SuppressWarnings("unused")
        private long l1, l2, l3, l4, l5, l6, l7, l8; // 64 bytes padding to avoid false sharing


        void setState(int value) {
            state = value;
        }

        int getState() {
            return state;
        }

        boolean compareAndSwapState(int expect, int update) {
            return UNSAFE.compareAndSwapInt(this, STATE_OFFSET, expect, update);
        }

        void setDirty(boolean value) {
            dirty = value;
        }

        boolean getDirty() {
            return dirty;
        }

        int getLayoutChangeIntended() {
            return layoutChangeIntended;
        }

        void incrementLayoutChangeIntended() {
            UNSAFE.getAndAddInt(this, LAYOUT_CHANGED_INTENDED_OFFSET, 1);
        }

        void decrementLayoutChangeIntended() {
            UNSAFE.getAndAddInt(this, LAYOUT_CHANGED_INTENDED_OFFSET, -1);
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
                first.waitAndCAS();
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
                        accessor.waitAndCAS();
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

        public void waitAndCAS() {
            incrementLayoutChangeIntended();
            while (!compareAndSwapState(LayoutLock.INACTIVE, LayoutLock.LAYOUT_CHANGE)) {
                LayoutLock.yield();
            }
            decrementLayoutChangeIntended();
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
