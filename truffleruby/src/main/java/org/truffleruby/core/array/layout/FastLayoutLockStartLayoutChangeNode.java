package org.truffleruby.core.array.layout;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.truffleruby.core.array.layout.FastLayoutLock.ThreadState;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeChild("self")
public abstract class FastLayoutLockStartLayoutChangeNode extends RubyNode {

    private @CompilationFinal int threads = 1;

    public static FastLayoutLockStartLayoutChangeNode create() {
        return FastLayoutLockStartLayoutChangeNodeGen.create(null);
    }

    public abstract int executeStartLayoutChange(FastLayoutLock.ThreadState threadState);

    // @ExplodeLoop
    @Specialization
    protected int startLayoutChange(FastLayoutLock.ThreadState threadState,
            @Cached("createBinaryProfile()") ConditionProfile waitProfile) {// FastLayoutLock.ThreadInfo
                                                                            // threadInfo) {
        FastLayoutLock lock = FastLayoutLock.GLOBAL_LOCK;
        // lock.startLayoutChange(threadState);
        final boolean unlocked = queue_lock(lock.queue.queue, threadState);
        // if (!unlocked) {
        // final FastLayoutLock.ThreadState gather[] = lock.gather;
        // for (int i = 0; i < gather.length; i++) {
        // AtomicInteger state = gather[i].state;
        // if (state.get() != FastLayoutLock.LAYOUT_CHANGE)
        // while (!state.compareAndSet(FastLayoutLock.INACTIVE, FastLayoutLock.LAYOUT_CHANGE))
        // ;
        // }
        //
        // }

        return 0;
    }

    boolean queue_lock(AtomicReference<FastLayoutLock.ThreadState> queue, ThreadState node) {
        node.next = null;
        ThreadState predecessor = queue.getAndSet(node);

        if (predecessor != null) {
            node.is_locked = true;
            predecessor.next = node;
            while (node.is_locked)
                ;
            return true;
        }
        return false;
    }

}
