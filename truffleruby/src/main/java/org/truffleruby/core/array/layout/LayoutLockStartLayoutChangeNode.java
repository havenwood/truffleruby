package org.truffleruby.core.array.layout;

import org.truffleruby.core.array.layout.LayoutLock.Accessor;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeChild("self")
public abstract class LayoutLockStartLayoutChangeNode extends RubyNode {

    private @CompilationFinal int threads = 1;

    public static LayoutLockStartLayoutChangeNode create() {
        return LayoutLockStartLayoutChangeNodeGen.create(null);
    }

    public abstract int executeStartLayoutChange(LayoutLock.Accessor accessor);

    @ExplodeLoop
    @Specialization
    protected int startLayoutChange(LayoutLock.Accessor layoutLock,
            @Cached("createBinaryProfile()") ConditionProfile casFirstProfile,
            @Cached("createBinaryProfile()") ConditionProfile casProfile,
            @Cached("createBinaryProfile()") ConditionProfile multiLayoutChangesProfile) {
        final int n = layoutLock.getNextThread();
        if (n != threads) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            threads = n;
        }

        final Accessor[] accessors = layoutLock.getAccessors();

        for (int i = 0; i < threads; i++) {
            Accessor accessor = accessors[i];
            while (accessor == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                accessor = accessors[i];
            }
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
