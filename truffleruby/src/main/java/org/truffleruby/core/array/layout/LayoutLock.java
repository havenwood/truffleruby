package org.truffleruby.core.array.layout;

import org.truffleruby.core.UnsafeHolder;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.profiles.ConditionProfile;

import sun.misc.Unsafe;

/**
 * LayoutLock based on Scalable RW Lock using accessors
 */
public class LayoutLock {

    private static final int MAX_THREADS = 600; // For specs

    private static final int INACTIVE = 0;
    private static final int WRITE = 1;
    private static final int LAYOUT_CHANGE = 2;

    private final Accessor[] accessors = new Accessor[MAX_THREADS];
    private volatile int nextThread = 0;

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
            return lock.nextThread;
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

        public void startWrite(ConditionProfile stateProfile) {
            while (getLayoutChangeIntended() > 0) {
                yield();
            }

            while (!stateProfile.profile(compareAndSwapState(INACTIVE, WRITE))) {
                yield();
            }
        }

        public void finishWrite() {
            setState(INACTIVE);
        }

        public int startLayoutChange(ConditionProfile casProfile) {
            final int threads = lock.nextThread;

            final Accessor[] accessors = getAccessors();

            for (int i = 0; i < threads; i++) {
                final Accessor accessor = accessors[i];
                if (!casProfile.profile(accessor.compareAndSwapState(INACTIVE, LAYOUT_CHANGE))) {
                    accessor.waitAndCAS();
                }
            }

            for (int i = 0; i < threads; i++) {
                accessors[i].dirty = true;
            }

            return threads;
        }

        public void waitAndCAS() {
            incrementLayoutChangeIntended();
            while (!compareAndSwapState(INACTIVE, LAYOUT_CHANGE)) {
                yield();
            }
            decrementLayoutChangeIntended();
        }

        public void finishLayoutChange(int n) {
            final Accessor[] accessors = getAccessors();
            for (int i = 0; i < n; i++) {
                accessors[i].setState(INACTIVE);
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
        final int threads = accessor.startLayoutChange(DUMMY_PROFILE);
        try {
            final int n = nextThread;
            accessors[n] = accessor;
            nextThread = n + 1;
        } finally {
            accessor.finishLayoutChange(threads);
        }
        return accessor;
    }

    private static final ConditionProfile DUMMY_PROFILE = ConditionProfile.createBinaryProfile();

}
