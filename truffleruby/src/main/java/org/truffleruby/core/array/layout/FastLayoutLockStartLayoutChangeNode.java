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
    }

}
