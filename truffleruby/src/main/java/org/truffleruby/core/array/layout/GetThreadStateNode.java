package org.truffleruby.core.array.layout;

import java.util.concurrent.atomic.AtomicInteger;

import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeChild("array")
public abstract class GetThreadStateNode extends RubyNode {

    public static GetThreadStateNode create() {
        return GetThreadStateNodeGen.create(null);
    }

    public abstract AtomicInteger executeGetThreadState(DynamicObject array);

    @Specialization(guards = {
            "getCurrentThread(array) == cachedThread",
            "array == cachedArray"
    }, limit = "1")
    protected AtomicInteger cachedThreadAndObject(DynamicObject array,
            @Cached("array") DynamicObject cachedArray,
            @Cached("getCurrentThread(array)") ThreadWithDirtyFlag cachedThread,
            @Cached("cachedThread.getThreadStateSlowPath(cachedArray)") AtomicInteger cachedThreadState) {
        return cachedThreadState;
    }

    @Specialization(guards = "getCurrentThread(array) == cachedThread", limit = "1", replaces = "cachedThreadAndObject")
    protected AtomicInteger cachedThread(DynamicObject array,
            @Cached("createCountingProfile()") ConditionProfile fastPathProfile,
            @Cached("getCurrentThread(array)") ThreadWithDirtyFlag cachedThread) {
        return cachedThread.getThreadState(array, fastPathProfile);
    }

    @Specialization(replaces = "cachedThread")
    protected AtomicInteger getThreadState(DynamicObject array,
            @Cached("createCountingProfile()") ConditionProfile fastPathProfile) {
         return getCurrentThread(array).getThreadState(array, fastPathProfile);
//        return getCurrentThread(array).getThreadState(array);
    }

    protected ThreadWithDirtyFlag getCurrentThread(DynamicObject array) {
        return (ThreadWithDirtyFlag) Thread.currentThread();
    }

}
