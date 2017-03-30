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

    // Protect access to nextThread and the accessors array
    private final Accessor lockAccessor = new Accessor(this);
    private final Accessor[] accessors = new Accessor[MAX_THREADS];
    private volatile int nextThread = 0;

    private static class Padding {

        // 64 bytes padding to avoid false sharing
        @SuppressWarnings("unused")
        private long l1, l2, l3, l4, l5, l6, l7, l8;

    }

    public LayoutLock() {
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

        public LayoutLock getLock() {
            return lock;
        }

        boolean compareAndSwapState(int expect, int update) {
            return UNSAFE.compareAndSwapInt(this, STATE_OFFSET, expect, update);
        }

        void incrementLayoutChangeIntended() {
            UNSAFE.getAndAddInt(this, LAYOUT_CHANGED_INTENDED_OFFSET, 1);
        }

        void decrementLayoutChangeIntended() {
            UNSAFE.getAndAddInt(this, LAYOUT_CHANGED_INTENDED_OFFSET, -1);
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

        private void resetDirty() {
            while (state == LAYOUT_CHANGE) {
                yield();
            }
            dirty = false;
            if (state == LAYOUT_CHANGE) {
                dirty = true;
            }
        }

        public void startWrite(ConditionProfile stateProfile) {
            while (layoutChangeIntended > 0) {
                yield();
            }

            while (!stateProfile.profile(compareAndSwapState(INACTIVE, WRITE))) {
                yield();
            }
        }

        public void finishWrite() {
            state = INACTIVE;
        }

        private void waitAndCAS() {
            incrementLayoutChangeIntended();
            while (!compareAndSwapState(INACTIVE, LAYOUT_CHANGE)) {
                yield();
            }
            decrementLayoutChangeIntended();
        }

    }

    public int startLayoutChange(ConditionProfile casProfile) {
        final Accessor lockAccessor = this.lockAccessor;
        if (!casProfile.profile(lockAccessor.compareAndSwapState(INACTIVE, LAYOUT_CHANGE))) {
            while (!lockAccessor.compareAndSwapState(INACTIVE, LAYOUT_CHANGE)) {
                yield();
            }
        }

        final int threads = nextThread;
        final Accessor[] accessors = this.accessors;

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

    public void finishLayoutChange(int n) {
        lockAccessor.state = INACTIVE;
        final Accessor[] accessors = this.accessors;
        for (int i = 0; i < n; i++) {
            accessors[i].state = INACTIVE;
        }
    }

    @TruffleBoundary
    public static void yield() {
        Thread.yield();
    }

    public Accessor access() {
        final Accessor accessor = new Accessor(this);
        final int threads = startLayoutChange(DUMMY_PROFILE);
        try {
            final int n = nextThread;
            accessors[n] = accessor;
            nextThread = n + 1;
        } finally {
            finishLayoutChange(threads);
        }
        return accessor;
    }

    private static final ConditionProfile DUMMY_PROFILE = ConditionProfile.createBinaryProfile();

}
