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
        int s = lock.startCount.get();
        AtomicInteger finishCount = lock.finishCount;
        if (s != finishCount.get())
            wait(s, finishCount);

        // add call to notify
        notifyLayoutChange(lock);

        return 0;
    }

    private void notifyLayoutChange(FastLayoutLock lock) {
        AtomicInteger gather[] = lock.gather;
        for (int i = 0; i < gather.length; i++) {
            AtomicInteger ts = gather[i];
            if (ts.get() != FastLayoutLock.LAYOUT_CHANGE)
                while (!ts.compareAndSet(FastLayoutLock.INACTIVE, FastLayoutLock.LAYOUT_CHANGE))
                    ;
        }
    }

    private void wait(int s, AtomicInteger finishCount) {
        while (s > finishCount.get() + 1)
            ; // wait for previous layout changes
    }

}
