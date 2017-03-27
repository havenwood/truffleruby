package org.truffleruby.core.array;

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.StampedLock;

import org.truffleruby.Layouts;
import org.truffleruby.core.array.ConcurrentArray.CustomLockArray;
import org.truffleruby.core.array.ConcurrentArray.FastAppendArray;
import org.truffleruby.core.array.ConcurrentArray.FastLayoutLockArray;
import org.truffleruby.core.array.ConcurrentArray.ReentrantLockArray;
import org.truffleruby.core.array.ConcurrentArray.StampedLockArray;
import org.truffleruby.core.array.layout.FastLayoutLock;
import org.truffleruby.core.array.layout.GetLayoutLockAccessorNode;
import org.truffleruby.core.array.layout.GetThreadStateNode;
import org.truffleruby.core.array.layout.LayoutLock;
import org.truffleruby.core.array.layout.MyBiasedLock;
import org.truffleruby.core.array.layout.ThreadStateReference;
import org.truffleruby.core.array.layout.ThreadWithDirtyFlag;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeInfo(cost = NodeCost.NONE)
@NodeChild("self")
@ImportStatic(ArrayGuards.class)
public abstract class ArraySyncReadNode extends RubyNode {

    @Child RubyNode builtinNode;

    public ArraySyncReadNode(RubyNode builtinNode) {
        this.builtinNode = builtinNode;
    }

    @Specialization(guards = "!isConcurrentArray(array)")
    public Object localRead(VirtualFrame frame, DynamicObject array) {
        return builtinNode.execute(frame);
    }

    @Specialization(guards = "isFixedSizeArray(array)")
    public Object fixedSizeRead(VirtualFrame frame, DynamicObject array) {
        return builtinNode.execute(frame);
    }

    protected ThreadWithDirtyFlag getCurrentThread(DynamicObject array) {
        return (ThreadWithDirtyFlag) Thread.currentThread();
    }

    @Specialization(guards = "isSynchronizedArray(array)")
    public Object synchronizedRead(VirtualFrame frame, DynamicObject array) {
        synchronized (array) {
            return builtinNode.execute(frame);
        }
    }

    @Specialization(guards = "isReentrantLockArray(array)")
    public Object reentrantLockRead(VirtualFrame frame, DynamicObject array) {
        final ReentrantLockArray stampedLockArray = (ReentrantLockArray) Layouts.ARRAY.getStore(array);
        final ReentrantLock lock = stampedLockArray.getLock();
        try {
            lock(lock);
            return builtinNode.execute(frame);
        } finally {
            unlock(lock);
        }
    }

    @TruffleBoundary
    private void lock(ReentrantLock lock) {
        lock.lock();
    }

    @TruffleBoundary
    private void unlock(ReentrantLock lock) {
        lock.unlock();
    }

    @Specialization(guards = { "array == cachedArray", "isCustomLockArray(cachedArray)" })
    public Object customLockReadCached(VirtualFrame frame, DynamicObject array,
            @Cached("array") DynamicObject cachedArray,
            @Cached("getCustomLock(cachedArray)") MyBiasedLock lock) {
        try {
            lock.lock(getContext());
            return builtinNode.execute(frame);
        } finally {
            lock.unlock();
        }
    }

    @Specialization(guards = "isCustomLockArray(array)", replaces = "customLockReadCached")
    public Object customLockRead(VirtualFrame frame, DynamicObject array) {
        final MyBiasedLock lock = getCustomLock(array);
        try {
            lock.lock(getContext());
            return builtinNode.execute(frame);
        } finally {
            lock.unlock();
        }
    }

    protected MyBiasedLock getCustomLock(DynamicObject array) {
        return ((CustomLockArray) Layouts.ARRAY.getStore(array)).getLock();
    }

    @Specialization(guards = "isStampedLockArray(array)")
    public Object stampedLockRead(VirtualFrame frame, DynamicObject array,
            @Cached("createBinaryProfile()") ConditionProfile validateProfile) {
        final StampedLockArray stampedLockArray = (StampedLockArray) Layouts.ARRAY.getStore(array);
        final StampedLock lock = stampedLockArray.getLock();

        Object result;
        long stamp;
        do {
            stamp = lock.tryOptimisticRead();
            result = builtinNode.execute(frame);
        } while (!validateProfile.profile(lock.validate(stamp)));
        return result;
    }

    @Specialization(guards = "isLayoutLockArray(array)")
    public Object layoutLockRead(VirtualFrame frame, DynamicObject array,
            @Cached("create()") GetLayoutLockAccessorNode getAccessorNode,
            @Cached("createBinaryProfile()") ConditionProfile dirtyProfile) {
        final LayoutLock.Accessor accessor = getAccessorNode.executeGetAccessor(array);
        Object result;
        do {
            // TODO: this might throw ArrayIndexOutOfBoundsException, or we need StoreStore+LoadLoad
            // Out-of-thin-air values are prevented by the dirty flag check
            result = builtinNode.execute(frame);
        } while (!accessor.finishRead(dirtyProfile));
        return result;
    }

    @Specialization(guards = "isFastLayoutLockArray(array)")
    public Object fastLayoutLockRead(VirtualFrame frame, DynamicObject array,
            @Cached("create()") GetThreadStateNode getThreadStateNode,
            @Cached("createBinaryProfile()") ConditionProfile fastPathProfile) {
        final ThreadStateReference threadState = getThreadStateNode.executeGetThreadState(array);
        final FastLayoutLock lock = ((FastLayoutLockArray) Layouts.ARRAY.getStore(array)).getLock();

        Object result;
        while (true) {
            // TODO: this might throw ArrayIndexOutOfBoundsException, or we need StoreStore+LoadLoad
            // Out-of-thin-air values are prevented by the dirty flag check
            result = builtinNode.execute(frame);
            
            if (lock.finishRead(threadState, fastPathProfile)) {
                return result;
            }
        }
    }

    @Specialization(guards = "isFastAppendArray(array)")
    public Object fastAppendRead(VirtualFrame frame, DynamicObject array,
            @Cached("create()") GetThreadStateNode getThreadStateNode,
            @Cached("createBinaryProfile()") ConditionProfile fastPathProfile) {
        final ThreadStateReference threadState = getThreadStateNode.executeGetThreadState(array);
        final FastLayoutLock lock = ((FastAppendArray) Layouts.ARRAY.getStore(array)).getLock();

        Object result;
        while (true) {
            // TODO: this might throw ArrayIndexOutOfBoundsException, or we need StoreStore+LoadLoad
            // Out-of-thin-air values are prevented by the dirty flag check
            result = builtinNode.execute(frame);

            if (lock.finishRead(threadState, fastPathProfile)) {
                return result;
            }
        }
    }

}
