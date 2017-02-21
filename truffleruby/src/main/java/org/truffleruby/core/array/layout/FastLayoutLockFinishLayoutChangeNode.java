package org.truffleruby.core.array.layout;

import java.util.concurrent.atomic.AtomicReference;

import org.truffleruby.core.array.layout.FastLayoutLock.ThreadState;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeChild("self")
public abstract class FastLayoutLockFinishLayoutChangeNode extends RubyNode {

    public static FastLayoutLockFinishLayoutChangeNode create() {
        return FastLayoutLockFinishLayoutChangeNodeGen.create(null);
    }

    public abstract int executeFinishLayoutChange(ThreadState threadState);

    @Specialization
    protected int finishLayoutChange(ThreadState node,
            @Cached("createCountingProfile()") ConditionProfile nextProfile,
            @Cached("createCountingProfile()") ConditionProfile casProfile) {
        FastLayoutLock lock = FastLayoutLock.GLOBAL_LOCK;
        AtomicReference<ThreadState> queue = lock.queue.queue;
        if (nextProfile.profile(node.next == null)) {
            if (casProfile.profile(queue.compareAndSet(node, null))) {
                return 0;
            }
            while (node.next == null) {
                LayoutLock.yield();
            }
        }
        node.next.is_locked = false;

        return 0;
    }

}
