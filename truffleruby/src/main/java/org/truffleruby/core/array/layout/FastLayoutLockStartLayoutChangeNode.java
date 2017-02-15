package org.truffleruby.core.array.layout;

import org.truffleruby.core.array.layout.FastLayoutLock.Accessor;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeChild("self")
public abstract class FastLayoutLockStartLayoutChangeNode extends RubyNode {

    private @CompilationFinal int threads = 1;

    public static FastLayoutLockStartLayoutChangeNode create() {
        return FastLayoutLockStartLayoutChangeNodeGen.create(null);
    }

    public abstract int executeStartLayoutChange(FastLayoutLock.Accessor accessor);

    @ExplodeLoop
    @Specialization
    protected int startLayoutChange(FastLayoutLock.Accessor accessor,
            @Cached("createBinaryProfile()") ConditionProfile casFirstProfile,
            @Cached("createBinaryProfile()") ConditionProfile casProfile,
            @Cached("createBinaryProfile()") ConditionProfile multiLayoutChangesProfile) {
        accessor.startLayoutChange();

        return 0;
    }
}
