package org.truffleruby.core.array.layout;

import java.util.concurrent.atomic.AtomicReference;

import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;

//@NodeChild("self")
public abstract class FastLayoutLockFinishLayoutChangeNode extends RubyNode {

    public static FastLayoutLockFinishLayoutChangeNode create() {
        return FastLayoutLockFinishLayoutChangeNodeGen.create();
    }

    public abstract int executeFinishLayoutChange();

    @Specialization
    protected int finishLayoutChange() {
        FastLayoutLock lock = FastLayoutLock.GLOBAL_LOCK;
        lock.baseLock.unlock();
        return 0;
    }

}
