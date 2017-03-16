package org.truffleruby.core.array.layout;

import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;

@NodeChildren({ @NodeChild("self"), @NodeChild("threads") })
public abstract class LayoutLockFinishLayoutChangeNode extends RubyNode {

    public static LayoutLockFinishLayoutChangeNode create() {
        return LayoutLockFinishLayoutChangeNodeGen.create(null, null);
    }

    public abstract int executeFinishLayoutChange(LayoutLock.Accessor accessor, int n);

    @Specialization
    protected int finishLayoutChange(LayoutLock.Accessor accessor, int n) {
        accessor.finishLayoutChange(n);
        return n;
    }

}
