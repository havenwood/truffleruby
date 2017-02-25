package org.truffleruby.core.array.layout;

import org.truffleruby.core.array.layout.LayoutLock.Accessor;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeChildren({ @NodeChild("self"), @NodeChild("threads") })
public abstract class LayoutLockFinishLayoutChangeNode extends RubyNode {

    public static LayoutLockFinishLayoutChangeNode create() {
        return LayoutLockFinishLayoutChangeNodeGen.create(null, null);
    }

    public abstract int executeFinishLayoutChange(LayoutLock.Accessor accessor, int n);

    @ExplodeLoop
    @Specialization
    protected int finishLayoutChange(LayoutLock.Accessor layoutLock, int n,
            @Cached("createBinaryProfile()") ConditionProfile multiLayoutChangesProfile) {
        final Accessor[] accessors = layoutLock.getAccessors();

        final Accessor first = accessors[0];
        if (false && multiLayoutChangesProfile.profile(first.layoutChangeIntended.get() > 0)) {
            // Another layout change is going to follow
            layoutLock.setCleanedAfterLayoutChange(false);
            first.state.set(LayoutLock.INACTIVE);
        } else {
            layoutLock.setCleanedAfterLayoutChange(true);
            for (int i = 0; i < n; i++) {
                accessors[i].state.set(LayoutLock.INACTIVE);
            }
        }

        return n;
    }

}
