package org.truffleruby.core.array.layout;

import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeChild("self")
public abstract class LayoutLockStartWriteNode extends RubyNode {

    public static LayoutLockStartWriteNode create() {
        return LayoutLockStartWriteNodeGen.create(null);
    }

    public abstract Object executeStartWrite(LayoutLock.Accessor accessor);

    @Specialization
    protected Object layoutLockStartWrite(LayoutLock.Accessor accessor,
            @Cached("createBinaryProfile()") ConditionProfile layoutChangeProfile,
            @Cached("createBinaryProfile()") ConditionProfile stateProfile) {
        while (layoutChangeProfile.profile(accessor.layoutChangeIntended.get() > 0)) {
            Thread.yield();
        }

        while (!stateProfile.profile(accessor.state.compareAndSet(LayoutLock.INACTIVE, LayoutLock.WRITE))) {
            Thread.yield();
        }

        return nil();
    }

}
