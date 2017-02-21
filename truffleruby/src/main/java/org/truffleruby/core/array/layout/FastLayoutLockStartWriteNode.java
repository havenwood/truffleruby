package org.truffleruby.core.array.layout;

import java.util.concurrent.atomic.AtomicInteger;

import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;

@NodeChild("self")
public abstract class FastLayoutLockStartWriteNode extends RubyNode {

    public static FastLayoutLockStartWriteNode create() {
        return FastLayoutLockStartWriteNodeGen.create(null);
    }

    public abstract Object executeStartWrite(FastLayoutLock.ThreadState threadState);

    @Specialization
    protected Object fastLayoutLockStartWrite(FastLayoutLock.ThreadState threadState) {// ,
        FastLayoutLock.GLOBAL_LOCK.startWrite(threadState);
        return nil();
    }

}
