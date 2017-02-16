package org.truffleruby.core.array.layout;

import java.util.concurrent.atomic.AtomicInteger;

import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;

//@NodeChild("self")
public abstract class FastLayoutLockStartLayoutChangeNode extends RubyNode {

    private @CompilationFinal int threads = 1;

    public static FastLayoutLockStartLayoutChangeNode create() {
        return FastLayoutLockStartLayoutChangeNodeGen.create();// null);
    }

    public abstract int executeStartLayoutChange();

    // @ExplodeLoop
    @Specialization
    protected int startLayoutChange(
            @Cached("createBinaryProfile()") ConditionProfile waitProfile) {// FastLayoutLock.ThreadInfo
                                                                            // threadInfo) {
        FastLayoutLock lock = FastLayoutLock.GLOBAL_LOCK;
        int s = lock.startCount.incrementAndGet();
        AtomicInteger finishCount = lock.finishCount;

        if (!(s == finishCount.get() + 1))
            wait(s, finishCount);

        return 0;
    }

    private void wait(int s, AtomicInteger finishCount) {
        while (s > finishCount.get() + 1)
            ;
    }
}
