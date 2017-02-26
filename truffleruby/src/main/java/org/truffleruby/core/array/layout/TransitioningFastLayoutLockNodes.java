package org.truffleruby.core.array.layout;

import java.util.concurrent.atomic.AtomicInteger;

import org.truffleruby.core.array.layout.FastLayoutLockNodesFactory.FastLayoutLockFinishLayoutChangeNodeGen;
import org.truffleruby.core.array.layout.FastLayoutLockNodesFactory.FastLayoutLockStartLayoutChangeNodeGen;
import org.truffleruby.core.array.layout.FastLayoutLockNodesFactory.FastLayoutLockStartWriteNodeGen;
import org.truffleruby.core.array.layout.TransitioningFastLayoutLockNodesFactory.TransitioningFastLayoutLockFinishLayoutChangeNodeGen;
import org.truffleruby.core.array.layout.TransitioningFastLayoutLockNodesFactory.TransitioningFastLayoutLockStartLayoutChangeNodeGen;
import org.truffleruby.core.array.layout.TransitioningFastLayoutLockNodesFactory.TransitioningFastLayoutLockStartWriteNodeGen;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class TransitioningFastLayoutLockNodes {

    public static abstract class TransitioningFastLayoutLockStartWriteNode extends RubyBaseNode {
        public static TransitioningFastLayoutLockStartWriteNode create() {
            return TransitioningFastLayoutLockStartWriteNodeGen.create();
        }

        public abstract long executeStartWrite(AtomicInteger threadState);

        @Specialization
        protected long transitioningFastLayoutLockStartWrite(AtomicInteger threadState,
                @Cached("createBinaryProfile()") ConditionProfile fastPathProfile) {
            return TransitioningFastLayoutLock.GLOBAL_LOCK.startWrite(threadState, fastPathProfile);
        }
    }

    public static abstract class TransitioningFastLayoutLockStartLayoutChangeNode extends RubyNode {
        public static TransitioningFastLayoutLockStartLayoutChangeNode create() {
            return TransitioningFastLayoutLockStartLayoutChangeNodeGen.create();
        }

        public abstract long executeStartLayoutChange();

        @Specialization
        protected long startLayoutChange(
                @Cached("createBinaryProfile()") ConditionProfile tryLockProfile,
                @Cached("createBinaryProfile()") ConditionProfile waitProfile) {
            return TransitioningFastLayoutLock.GLOBAL_LOCK.startLayoutChange(tryLockProfile, waitProfile);
        }
    }

    public static abstract class TransitioningFastLayoutLockFinishLayoutChangeNode extends RubyBaseNode {
        public static TransitioningFastLayoutLockFinishLayoutChangeNode create() {
            return TransitioningFastLayoutLockFinishLayoutChangeNodeGen.create();
        }

        public abstract void executeFinishLayoutChange(long stamp);

        @Specialization
        protected void finishLayoutChange(long stamp) {
            TransitioningFastLayoutLock.GLOBAL_LOCK.finishLayoutChange(stamp);
        }
    }
}
