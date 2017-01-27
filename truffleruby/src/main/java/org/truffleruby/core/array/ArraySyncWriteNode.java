package org.truffleruby.core.array;

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.StampedLock;

import org.truffleruby.Layouts;
import org.truffleruby.core.array.ConcurrentArray.CustomLockArray;
import org.truffleruby.core.array.ConcurrentArray.ReentrantLockArray;
import org.truffleruby.core.array.ConcurrentArray.StampedLockArray;
import org.truffleruby.core.array.layout.LayoutLock;
import org.truffleruby.core.array.layout.MyBiasedLock;
import org.truffleruby.core.array.layout.ThreadWithDirtyFlag;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.CompilerDirectives;
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
public abstract class ArraySyncWriteNode extends RubyNode {

    @Child RubyNode builtinNode;

    public ArraySyncWriteNode(RubyNode builtinNode) {
        this.builtinNode = builtinNode;
    }

    @Specialization(guards = "!isConcurrentArray(array)")
    public Object local(VirtualFrame frame, DynamicObject array) {
        return builtinNode.execute(frame);
    }

    @Specialization(guards = "isFixedSizeArray(array)")
    public Object fixedSize(VirtualFrame frame, DynamicObject array) {
        return builtinNode.execute(frame);
    }

    @Specialization(guards = "isSynchronizedArray(array)")
    public Object synchronizedWrite(VirtualFrame frame, DynamicObject array) {
        synchronized (array) {
            return builtinNode.execute(frame);
        }
    }

    @Specialization(guards = "isReentrantLockArray(array)")
    public Object reentrantLock(VirtualFrame frame, DynamicObject array) {
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
    public Object customLockCached(VirtualFrame frame, DynamicObject array,
            @Cached("array") DynamicObject cachedArray,
            @Cached("getCustomLock(cachedArray)") MyBiasedLock lock) {
        try {
            lock.lock(getContext());
            return builtinNode.execute(frame);
        } finally {
            lock.unlock();
        }
    }

    @Specialization(guards = "isCustomLockArray(array)", replaces = "customLockCached")
    public Object customLock(VirtualFrame frame, DynamicObject array) {
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
    public Object stampedLock(VirtualFrame frame, DynamicObject array) {
        final StampedLockArray stampedLockArray = (StampedLockArray) Layouts.ARRAY.getStore(array);
        final StampedLock lock = stampedLockArray.getLock();
        final long stamp = stampedLockRead(lock);
        try {
            return builtinNode.execute(frame);
        } finally {
            stampedLockUnlock(lock, stamp);
        }
    }

    @TruffleBoundary
    private long stampedLockRead(StampedLock lock) {
        return lock.readLock();
    }

    @TruffleBoundary
    private void stampedLockUnlock(StampedLock lock, long stamp) {
        lock.unlockRead(stamp);
    }

    @Specialization(guards = "isLayoutLockArray(array)")
    public Object layoutLockWrite(VirtualFrame frame, DynamicObject array,
            @Cached("createBinaryProfile()") ConditionProfile dirtyProfile) {
        final LayoutLock.Accessor accessor = ((ThreadWithDirtyFlag) Thread.currentThread()).getLayoutLockAccessor();
        accessor.startWrite();
        try {
            return builtinNode.execute(frame);
        } finally {
            accessor.finishWrite();
        }
    }

}
