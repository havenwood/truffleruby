package org.truffleruby.core.array.layout;

import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;

//@NodeChildren({}) // { @NodeChild("self"), @NodeChild("threads") })
public abstract class FastLayoutLockFinishLayoutChangeNode extends RubyNode {

    public static FastLayoutLockFinishLayoutChangeNode create() {
        return FastLayoutLockFinishLayoutChangeNodeGen.create(); // null, null);
    }

    public abstract int executeFinishLayoutChange();

    @ExplodeLoop
    @Specialization
    protected int finishLayoutChange() {
        FastLayoutLock lock = FastLayoutLock.GLOBAL_LOCK;
        lock.finishCount.incrementAndGet();
        return 0;
    }

}
