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

    @Specialization(guards = "getCurrentThread(array) == cachedThread", limit = "1")
    protected AtomicInteger cachedThread(DynamicObject array,
            @Cached("createBinaryProfile()") ConditionProfile fastPathProfile,
            @Cached("getCurrentThread(array)") ThreadWithDirtyFlag cachedThread) {
        return cachedThread.getThreadState(array, fastPathProfile);
    }

    @Specialization(contains = "cachedThread")
    protected AtomicInteger getThreadState(DynamicObject array,
            @Cached("createBinaryProfile()") ConditionProfile fastPathProfile) {
        return getCurrentThread(array).getThreadState(array, fastPathProfile);
    }

    protected ThreadWithDirtyFlag getCurrentThread(DynamicObject array) {
        return (ThreadWithDirtyFlag) Thread.currentThread();
    }

}
