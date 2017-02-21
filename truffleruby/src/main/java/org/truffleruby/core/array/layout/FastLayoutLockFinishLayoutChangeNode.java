package org.truffleruby.core.array.layout;

import java.util.concurrent.atomic.AtomicReference;

import org.truffleruby.core.array.layout.FastLayoutLock.ThreadState;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;

//@NodeChildren({}) // { @NodeChild("self"), @NodeChild("threads") })
@NodeChild("self")
public abstract class FastLayoutLockFinishLayoutChangeNode extends RubyNode {

    public static FastLayoutLockFinishLayoutChangeNode create() {
        return FastLayoutLockFinishLayoutChangeNodeGen.create(null); // null, null);
    }

    public abstract int executeFinishLayoutChange(ThreadState threadState);

    @ExplodeLoop
    @Specialization
    protected int finishLayoutChange(ThreadState threadState) {
        FastLayoutLock lock = FastLayoutLock.GLOBAL_LOCK;
        if (lock.gather.length <= 2)
            return 0;
        queue_unlock(lock.queue.queue, threadState);
        return 0;
    }

    void queue_unlock(AtomicReference<ThreadState> queue, ThreadState node) {
        if (node.next == null) {
            if (queue.compareAndSet(node, null))
                return;
            while (node.next == null)
                ;
        }
        node.next.is_locked = false;
    }

}
