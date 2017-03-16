package org.truffleruby.core.array.layout;

import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeChild("self")
public abstract class LayoutLockStartLayoutChangeNode extends RubyNode {

    public static LayoutLockStartLayoutChangeNode create() {
        return LayoutLockStartLayoutChangeNodeGen.create(null);
    }

    public abstract int executeStartLayoutChange(LayoutLock.Accessor accessor);

    @Specialization
    protected int startLayoutChange(LayoutLock.Accessor accessor,
            @Cached("createBinaryProfile()") ConditionProfile casProfile) {
        return accessor.startLayoutChange(casProfile);
    }

}
