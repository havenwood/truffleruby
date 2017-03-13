package org.truffleruby.core.array.layout;

import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;

@NodeChild("array")
public abstract class GetLayoutLockAccessorNode extends RubyNode {

    public static GetLayoutLockAccessorNode create() {
        return GetLayoutLockAccessorNodeGen.create(null);
    }

    public abstract LayoutLock.Accessor executeGetAccessor(DynamicObject array);

    // Disabled so one thread is not favored.
//    @Specialization(guards = "getCurrentThread(array) == cachedThread", limit = "1")
//    protected LayoutLock.Accessor cachedThread(DynamicObject array,
//            @Cached("getCurrentThread(array)") ThreadWithDirtyFlag cachedThread) {
//        return cachedThread.getLayoutLockAccessor();
//    }

    @Specialization // (contains = "cachedThread")
    protected LayoutLock.Accessor getAccessor(DynamicObject array) {
        return getCurrentThread(array).getLayoutLockAccessor();
    }

    protected ThreadWithDirtyFlag getCurrentThread(DynamicObject array) {
        return (ThreadWithDirtyFlag) Thread.currentThread();
    }

}
