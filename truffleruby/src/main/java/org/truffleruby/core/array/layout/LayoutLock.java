package org.truffleruby.core.array.layout;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * LayoutLock based on Scalable RW Lock using accessors
 */
public class LayoutLock {

    public static final LayoutLock GLOBAL_LOCK = new LayoutLock();

    private static final int MAX_THREADS = 66;

    private static final int INACTIVE = 0;
    private static final int WRITE = 1;
    private static final int LAYOUT_CHANGE = 2;

    private final Accessor accessors[] = new Accessor[MAX_THREADS];
    private final Accessor accessorsByTid[] = new Accessor[MAX_THREADS];
    private final AtomicInteger nextThread = new AtomicInteger(0);

    public class Accessor {

        private final AtomicInteger state = new AtomicInteger();
        private final AtomicBoolean dirty = new AtomicBoolean();
        private final AtomicInteger writeIntended = new AtomicInteger(0);

        private Accessor(LayoutLock layoutLock) {
            this.state.set(INACTIVE);
            this.dirty.set(false);
            this.writeIntended.set(0);
        }

        public void startRead() {
        }

        public boolean finishRead() {
            if (dirty.get()) {
                resetDirty();
                return false;
            }
            return true;
        }

        public void startWrite() {
            while (writeIntended.get() > 0 || !state.compareAndSet(INACTIVE, WRITE)) {
            }
        }

        public void finishWrite() {
            state.set(INACTIVE);
        }

        @TruffleBoundary
        public void startLayoutChange() {
            final int n = nextThread.get();
            for (int i = 0; i < n; i++) {
                while (accessors[i] == null) {
                }
                accessors[i].writeIntended.getAndIncrement();
            }
            for (int i = 0; i < n; i++)
                while (!accessors[i].state.compareAndSet(INACTIVE, LAYOUT_CHANGE)) {
                }
            for (int i = 0; i < n; i++) {
                accessors[i].writeIntended.getAndDecrement();
            }

            for (int i = 0; i < n; i++) {
                while (accessors[i] == null) {
                }
                accessors[i].dirty.set(true);
            }

        }

        @TruffleBoundary
        public void finishLayoutChange() {
            final int n = nextThread.get();
            for (int i = 0; i < n; i++) {
                while (accessors[i] == null) {
                }
                accessors[i].state.set(INACTIVE);
            }
        }

        public boolean isDirty() {
            return dirty.get();
        }

        void resetDirty() {
            while (state.get() == LAYOUT_CHANGE) {
            }
            dirty.set(false);
            if (state.get() == LAYOUT_CHANGE) {
                dirty.set(true);
            }
        }

    }

    public Accessor access() {
        Accessor ac = new Accessor(this);
        accessors[nextThread.getAndIncrement()] = ac;
        return ac;
    }

    public Accessor access(int tid) {
        if (accessorsByTid[tid] != null) {
            return accessorsByTid[tid];
        }
        return accessorsByTid[tid] = access();
    }

}
