package org.truffleruby.core.array.layout;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;

//@NodeChild("self")
public abstract class FastLayoutLockStartLayoutChangeNode extends RubyNode {

    public static FastLayoutLockStartLayoutChangeNode create() {
        return FastLayoutLockStartLayoutChangeNodeGen.create();
    }

    public abstract int executeStartLayoutChange();

    @Specialization
    protected int startLayoutChange() {
        FastLayoutLock lock = FastLayoutLock.GLOBAL_LOCK;
        lock.startLayoutChange();

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
        return 0;
    }

}
