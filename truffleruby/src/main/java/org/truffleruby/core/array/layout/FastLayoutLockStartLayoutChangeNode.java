package org.truffleruby.core.array.layout;

import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class FastLayoutLockStartLayoutChangeNode extends RubyNode {

    public static FastLayoutLockStartLayoutChangeNode create() {
        return FastLayoutLockStartLayoutChangeNodeGen.create();
    }

    public abstract long executeStartLayoutChange();

    @Specialization
    protected long startLayoutChange() {
        FastLayoutLock lock = FastLayoutLock.GLOBAL_LOCK;
        return lock.startLayoutChange();

        // long stamp = lock.baseLock.tryWriteLock();
        //
        // if (stamp != 0) {
        // final AtomicInteger gather[] = lock.gather;
        // int l = gather.length;
        // for (int i = 1; i < l; i++) {
        // AtomicInteger state = gather[i];
        // if (state.get() != FastLayoutLock.LAYOUT_CHANGE)
        // while (!state.compareAndSet(FastLayoutLock.INACTIVE, FastLayoutLock.LAYOUT_CHANGE))
        // {
        // LayoutLock.yield();
        // }
        // }
        // } else {
        // stamp = lock.baseLock.writeLock();
        // }
        // lock.baseLockStamp = stamp;
    }

}
