package org.truffleruby.core.array.layout;

import java.util.concurrent.atomic.AtomicInteger;

import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;

@NodeChild("array")
public abstract class GetTransitioningThreadStateNode extends RubyNode {

    public static GetTransitioningThreadStateNode create() {
        return GetTransitioningThreadStateNodeGen.create(null);
    }

    public abstract AtomicInteger executeGetTransitioningThreadState(DynamicObject array);

    // Disabled so one thread is not favored.
//    @Specialization(guards = "getCurrentThread(array) == cachedThread", limit = "1")
//    protected AtomicInteger cachedThread(DynamicObject array,
//            @Cached("getCurrentThread(array)") ThreadWithDirtyFlag cachedThread) {
//        return cachedThread.getTransitioningThreadState(array);
//    }

    @Specialization // (contains = "cachedThread")
    protected AtomicInteger getTransitioningThreadState(DynamicObject array) {
        return getCurrentThread(array).getTransitioningThreadState(array);
    }

    protected ThreadWithDirtyFlag getCurrentThread(DynamicObject array) {
        return (ThreadWithDirtyFlag) Thread.currentThread();
    }

}
