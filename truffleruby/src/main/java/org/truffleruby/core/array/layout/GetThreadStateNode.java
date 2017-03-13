package org.truffleruby.core.array.layout;

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

    public abstract ThreadStateReference executeGetThreadState(DynamicObject array);

    // Disabled so one thread is not favored.
//    @Specialization(guards = {
//            "getCurrentThread(array) == cachedThread",
//            "array == cachedArray"
//    }, limit = "1")
//    protected ThreadStateReference cachedThreadAndObject(DynamicObject array,
//            @Cached("array") DynamicObject cachedArray,
//            @Cached("getCurrentThread(array)") ThreadWithDirtyFlag cachedThread,
//            @Cached("cachedThread.getThreadStateSlowPath(cachedArray)") ThreadStateReference cachedThreadState) {
//        return cachedThreadState;
//    }
//
//    @Specialization(guards = "getCurrentThread(array) == cachedThread", limit = "1", replaces = "cachedThreadAndObject")
//    protected ThreadStateReference cachedThread(DynamicObject array,
//            @Cached("createCountingProfile()") ConditionProfile fastPathProfile,
//            @Cached("getCurrentThread(array)") ThreadWithDirtyFlag cachedThread) {
//        return cachedThread.getThreadState(array, fastPathProfile);
//    }

    @Specialization // (replaces = "cachedThread")
    protected ThreadStateReference getThreadState(DynamicObject array,
            @Cached("createCountingProfile()") ConditionProfile fastPathProfile) {
         return getCurrentThread(array).getThreadState(array, fastPathProfile);
    }

    protected ThreadWithDirtyFlag getCurrentThread(DynamicObject array) {
        return (ThreadWithDirtyFlag) Thread.currentThread();
    }

}
