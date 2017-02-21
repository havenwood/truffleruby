package org.truffleruby.core.array.layout;

import org.truffleruby.core.array.layout.FastLayoutLock.mcs_node;
import org.truffleruby.core.array.layout.FastLayoutLock.mcs_queue;
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

    public abstract int executeFinishLayoutChange(FastLayoutLock.ThreadState threadState);

    @ExplodeLoop
    @Specialization
    protected int finishLayoutChange(FastLayoutLock.ThreadState threadState) {
        FastLayoutLock lock = FastLayoutLock.GLOBAL_LOCK;
        if (lock.gather.length <= 2)
            return 0;
        queue_unlock(lock.queue, threadState);
        return 0;
    }

    void queue_unlock(mcs_queue queue, mcs_node node) {
        if (node.next == null) {
            if (queue.queue.compareAndSet(node, null))
                return;
            while (node.next == null)
                ;
        }
        node.next.is_locked = false;
    }

}
