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

    private static final int MAX_THREADS = 600; // For specs

    public static final int INACTIVE = 0;
    public static final int WRITE = 1;
    public static final int LAYOUT_CHANGE = 2;

    @CompilationFinal private final Accessor[] accessors = new Accessor[MAX_THREADS];
    private final AtomicInteger nextThread = new AtomicInteger(0);

    private static class Padding {

        // 64 bytes padding to avoid false sharing
        @SuppressWarnings("unused")
        private long l1, l2, l3, l4, l5, l6, l7, l8;

    }

    public static class Accessor extends Padding {

        private static final Unsafe UNSAFE = UnsafeHolder.UNSAFE;
        private static final long STATE_OFFSET = UnsafeHolder.getFieldOffset(Accessor.class, "state");
        private static final long LAYOUT_CHANGED_INTENDED_OFFSET = UnsafeHolder.getFieldOffset(Accessor.class, "layoutChangeIntended");

        private final LayoutLock lock;

        private volatile int state = INACTIVE;
        private volatile int layoutChangeIntended = 0;
        public volatile boolean dirty = false;

        private Accessor(LayoutLock lock) {
            this.lock = lock;
        }

        void setState(int value) {
            state = value;
        }

        int getState() {
            return state;
        }

        boolean compareAndSwapState(int expect, int update) {
            return UNSAFE.compareAndSwapInt(this, STATE_OFFSET, expect, update);
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

        public Accessor[] getAccessors() {
            return lock.accessors;
        }

        public int getNextThread() {
            return lock.nextThread.get();
        }

        public void startRead() {
        }

        public boolean finishRead(ConditionProfile dirtyProfile) {
            if (dirtyProfile.profile(dirty)) {
                resetDirty();
                return false;
            }
            return true;
        }

        public boolean finishRead() {
            if (dirty) {
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
            // what if new threads?
            final int n = lock.nextThread.get();

            final Accessor[] accessors = getAccessors();

            for (int i = 0; i < n; i++) {
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
                accessors[i].dirty = true;
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
            final Accessor[] accessors = getAccessors();
            for (int i = 0; i < n; i++) {
                accessors[i].setState(LayoutLock.INACTIVE);
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
        final Accessor accessor = new Accessor(this);
        final int threads = accessor.startLayoutChange();
        try {
            final int n = nextThread.getAndIncrement();
            accessors[n] = accessor;
        } finally {
            accessor.finishLayoutChange(threads);
        }
        return accessor;
    }

}
