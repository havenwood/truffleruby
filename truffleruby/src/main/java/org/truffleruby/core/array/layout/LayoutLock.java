package org.truffleruby.core.array.layout;

import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * LayoutLock based on Scalable RW Lock using accessors
 */
public class LayoutLock {

    public static final LayoutLock GLOBAL_LOCK = new LayoutLock();

    private static final int MAX_THREADS = 200;

    public static final int INACTIVE = 0;
    public static final int WRITE = 1;
    public static final int LAYOUT_CHANGE = 2;

    @CompilationFinal private final Accessor[] accessors = new Accessor[MAX_THREADS];
    private final AtomicInteger nextThread = new AtomicInteger(0);
    private boolean cleanedAfterLayoutChange = true;

    public class Accessor {

        public final AtomicInteger state = new AtomicInteger();
        volatile boolean dirty;
        public final AtomicInteger layoutChangeIntended = new AtomicInteger(0);

        private Accessor(LayoutLock layoutLock) {
            this.state.set(INACTIVE);
            this.dirty = false;
            this.layoutChangeIntended.set(0);
        }

        public Accessor[] getAccessors() {
            return accessors;
        }

        public int getNextThread() {
            return nextThread.get();
        }

        public boolean getCleanedAfterLayoutChange() {
            return cleanedAfterLayoutChange;
        }

        public void setCleanedAfterLayoutChange(boolean cleaned) {
            cleanedAfterLayoutChange = cleaned;
        }

        public void startRead() {
        }

        public boolean finishRead() {
            if (dirty) {
                resetDirty();
                return false;
            }
            return true;
        }

        public void startWrite() {
            while (layoutChangeIntended.get() > 0 || !state.compareAndSet(INACTIVE, WRITE)) {
            }
        }

        public void finishWrite() {
            state.set(INACTIVE);
        }

        public int startLayoutChange() {
            final Accessor first = accessors[0];
            if (!first.state.compareAndSet(INACTIVE, LAYOUT_CHANGE)) {
                first.layoutChangeIntended.getAndIncrement();
                while (!first.state.compareAndSet(INACTIVE, LAYOUT_CHANGE)) {
                    yield();
                }
                first.layoutChangeIntended.getAndDecrement();
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
                    if (!accessor.state.compareAndSet(INACTIVE, LAYOUT_CHANGE)) {
                        accessor.layoutChangeIntended.getAndIncrement();
                        while (!accessor.state.compareAndSet(INACTIVE, LAYOUT_CHANGE)) {
                            yield();
                        }
                        accessor.layoutChangeIntended.getAndDecrement();
                    }
                }

                for (int i = 0; i < n; i++) {
                    accessors[i].dirty = true;
                }
            } else {
                first.dirty = true;
            }

            return n;
        }

        public void finishLayoutChange(int n) {
            final Accessor first = accessors[0];
            if (first.layoutChangeIntended.get() > 0) { // Another layout change is going to follow
                cleanedAfterLayoutChange = false;
                first.state.set(INACTIVE);
            } else {
                cleanedAfterLayoutChange = true;
                for (int i = n - 1; i >= 0; i--) {
                    accessors[i].state.set(INACTIVE);
                }
            }
        }

        public boolean isDirty() {
            return dirty;
        }

        public void resetDirty() {
            while (state.get() == LAYOUT_CHANGE) {
                yield();
            }
            dirty = false;
            if (state.get() == LAYOUT_CHANGE) {
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
            while (accessors[0].state.get() == LAYOUT_CHANGE) {
                yield();
            }
        }
        return ac;
    }

}
