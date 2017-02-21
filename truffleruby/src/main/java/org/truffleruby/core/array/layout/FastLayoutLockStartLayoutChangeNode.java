package org.truffleruby.core.array.layout;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.truffleruby.core.array.layout.FastLayoutLock.ThreadState;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeChild("self")
public abstract class FastLayoutLockStartLayoutChangeNode extends RubyNode {

    public static FastLayoutLockStartLayoutChangeNode create() {
        return FastLayoutLockStartLayoutChangeNodeGen.create(null);
    }

    public abstract int executeStartLayoutChange(FastLayoutLock.ThreadState threadState);

    @Specialization
    protected int startLayoutChange(FastLayoutLock.ThreadState node,
            @Cached("createCountingProfile()") ConditionProfile predecessorProfile) {
        FastLayoutLock lock = FastLayoutLock.GLOBAL_LOCK;
        AtomicReference<FastLayoutLock.ThreadState> queue = lock.queue.queue;

        node.next = null;
        ThreadState predecessor = queue.getAndSet(node);

        if (predecessorProfile.profile(predecessor != null)) {
            node.is_locked = true;
            predecessor.next = node;
            while (node.is_locked) {
                LayoutLock.yield();
            }
        }

        final FastLayoutLock.ThreadState gather[] = lock.gather;
        for (int i = 0; i < gather.length; i++) {
            AtomicInteger state = gather[i].state;
            if (state.get() != FastLayoutLock.LAYOUT_CHANGE) {
                while (!state.compareAndSet(FastLayoutLock.INACTIVE, FastLayoutLock.LAYOUT_CHANGE)) {
                    LayoutLock.yield();
                }
            }
        }

        return 0;
    }

}
