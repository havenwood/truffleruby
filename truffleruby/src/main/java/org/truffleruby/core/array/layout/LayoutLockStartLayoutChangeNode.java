package org.truffleruby.core.array.layout;

import org.truffleruby.core.array.layout.LayoutLock.Accessor;
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
    protected int startLayoutChange(LayoutLock.Accessor layoutLock,
            @Cached("createBinaryProfile()") ConditionProfile casProfile) {
        final int threads = layoutLock.getNextThread();

        final Accessor[] accessors = layoutLock.getAccessors();

        for (int i = 0; i < threads; i++) {
            final Accessor accessor = accessors[i];
            if (!casProfile.profile(accessor.compareAndSwapState(LayoutLock.INACTIVE, LayoutLock.LAYOUT_CHANGE))) {
                accessor.waitAndCAS();
            }
        }

        for (int i = 0; i < threads; i++) {
            accessors[i].dirty = true;
        }


        return threads;
    }

}
