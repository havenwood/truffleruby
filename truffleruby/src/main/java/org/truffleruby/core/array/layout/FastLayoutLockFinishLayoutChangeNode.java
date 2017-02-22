package org.truffleruby.core.array.layout;

import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class FastLayoutLockFinishLayoutChangeNode extends RubyBaseNode {

    public static FastLayoutLockFinishLayoutChangeNode create() {
        return FastLayoutLockFinishLayoutChangeNodeGen.create();
    }

    public abstract void executeFinishLayoutChange(long stamp);

    @Specialization
    protected void finishLayoutChange(long stamp) {
        FastLayoutLock.GLOBAL_LOCK.finishLayoutChange(stamp);
    }

}
