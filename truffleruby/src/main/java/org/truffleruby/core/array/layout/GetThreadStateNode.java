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

    public abstract AtomicInteger executeGetThreadState(DynamicObject array);

    public abstract AtomicInteger executeGetTransitioningThreadState(DynamicObject array);

    @Specialization(guards = "getCurrentThread(array) == cachedThread", limit = "1")
    protected AtomicInteger cachedThread(DynamicObject array,
            @Cached("getCurrentThread(array)") ThreadWithDirtyFlag cachedThread) {
        return cachedThread.getThreadState();
    }

    @Specialization(contains = "cachedThread")
    protected AtomicInteger getThreadState(DynamicObject array) {
        return getCurrentThread(array).getThreadState();
    }

    @Specialization(contains = "cachedThread")
    protected AtomicInteger getTransitioningThreadState(DynamicObject array) {
        return getCurrentThread(array).getTransitioningThreadState();
    }

    protected ThreadWithDirtyFlag getCurrentThread(DynamicObject array) {
        return (ThreadWithDirtyFlag) Thread.currentThread();
    }

}
