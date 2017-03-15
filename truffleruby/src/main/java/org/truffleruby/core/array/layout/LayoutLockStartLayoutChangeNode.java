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
        final Accessor[] accessors = layoutLock.getAccessors();

        final Accessor first = accessors[0];
        if (!casFirstProfile.profile(first.compareAndSwapState(LayoutLock.INACTIVE, LayoutLock.LAYOUT_CHANGE))) {
            waitAndCAS(first);
        }

        final boolean cleaned = layoutLock.getCleanedAfterLayoutChange();
        final int n = layoutLock.getNextThread();
        if (n != threads) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            threads = n;
        }

        if (multiLayoutChangesProfile.profile(cleaned)) {
            for (int i = 1; i < threads; i++) {
                Accessor accessor = accessors[i];
                while (accessor == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    accessor = accessors[i];
                }
                if (!casProfile.profile(accessor.compareAndSwapState(LayoutLock.INACTIVE, LayoutLock.LAYOUT_CHANGE))) {
                    waitAndCAS(accessor);
                }
            }

            for (int i = 0; i < threads; i++) {
                accessors[i].setDirty(true);
            }
        } else {
            first.setDirty(true);
        }


        return threads;
    }

    private void waitAndCAS(Accessor accessor) {
        accessor.getAndIncrementLayoutChangeIntended();
        while (!accessor.compareAndSwapState(LayoutLock.INACTIVE, LayoutLock.LAYOUT_CHANGE)) {
            LayoutLock.yield();
        }
        accessor.getAndDecrementLayoutChangeIntended();
    }

}
