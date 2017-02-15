package org.truffleruby.core.array.layout;

import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;

@NodeChild("array")
public abstract class GetFastLayoutLockAccessorNode extends RubyNode {

    public static GetFastLayoutLockAccessorNode create() {
        return GetFastLayoutLockAccessorNodeGen.create(null);
    }

    public abstract FastLayoutLock.Accessor executeGetAccessor(DynamicObject array);

    @Specialization(guards = "getCurrentThread(array) == cachedThread", limit = "1")
    protected FastLayoutLock.Accessor cachedThread(DynamicObject array,
            @Cached("getCurrentThread(array)") ThreadWithDirtyFlag cachedThread) {
        return cachedThread.getFastLayoutLockAccessor();
    }

    @Specialization
    protected FastLayoutLock.Accessor getAccessor(DynamicObject array) {
        return getCurrentThread(array).getFastLayoutLockAccessor();
    }

    protected ThreadWithDirtyFlag getCurrentThread(DynamicObject array) {
        return (ThreadWithDirtyFlag) Thread.currentThread();
    }

}
