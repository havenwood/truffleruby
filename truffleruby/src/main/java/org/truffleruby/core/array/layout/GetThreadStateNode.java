package org.truffleruby.core.array.layout;

import java.util.concurrent.atomic.AtomicInteger;

import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;

@NodeChild("array")
public abstract class GetThreadStateNode extends RubyNode {

    public static GetThreadStateNode create() {
        return GetThreadStateNodeGen.create(null);
    }

    public abstract FastLayoutLock.ThreadState executeGetThreadState(DynamicObject array);

    @Specialization(guards = "getCurrentThread(array) == cachedThread", limit = "1")
    protected FastLayoutLock.ThreadState cachedThread(DynamicObject array,
            @Cached("getCurrentThread(array)") ThreadWithDirtyFlag cachedThread) {
        return cachedThread.getThreadState();
    }

    @Specialization
    protected FastLayoutLock.ThreadState getThreadState(DynamicObject array) {
        return getCurrentThread(array).getThreadState();
    }

    protected ThreadWithDirtyFlag getCurrentThread(DynamicObject array) {
        return (ThreadWithDirtyFlag) Thread.currentThread();
    }

}
