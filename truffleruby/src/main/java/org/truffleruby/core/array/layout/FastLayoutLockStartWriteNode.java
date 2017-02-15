package org.truffleruby.core.array.layout;

import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeChild("self")
public abstract class FastLayoutLockStartWriteNode extends RubyNode {

    public static FastLayoutLockStartWriteNode create() {
        return FastLayoutLockStartWriteNodeGen.create(null);
    }

    public abstract Object executeStartWrite(FastLayoutLock.Accessor accessor);

    @Specialization
    protected Object FastLayoutLockStartWrite(FastLayoutLock.Accessor accessor,
            @Cached("createBinaryProfile()") ConditionProfile layoutChangeProfile,
            @Cached("createBinaryProfile()") ConditionProfile stateProfile) {
        accessor.startWrite();
        return nil();
    }

}
