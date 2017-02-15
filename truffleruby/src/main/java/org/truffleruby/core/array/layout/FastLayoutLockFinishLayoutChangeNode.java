package org.truffleruby.core.array.layout;

import org.truffleruby.core.array.layout.FastLayoutLock.Accessor;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeChildren({ @NodeChild("self"), @NodeChild("threads") })
public abstract class FastLayoutLockFinishLayoutChangeNode extends RubyNode {

    public static FastLayoutLockFinishLayoutChangeNode create() {
        return FastLayoutLockFinishLayoutChangeNodeGen.create(null, null);
    }

    public abstract int executeFinishLayoutChange(FastLayoutLock.Accessor accessor, int n);

    @ExplodeLoop
    @Specialization
    protected int finishLayoutChange(FastLayoutLock.Accessor fastLayoutLock, int n,
            @Cached("createBinaryProfile()") ConditionProfile multiLayoutChangesProfile) {
        fastLayoutLock.finishLayoutChange();
        return n;
    }

}
