package org.truffleruby.core.array;

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.StampedLock;

import org.truffleruby.Layouts;
import org.truffleruby.core.array.ConcurrentArray.CustomLockArray;
import org.truffleruby.core.array.ConcurrentArray.FixedSizeArray;
import org.truffleruby.core.array.ConcurrentArray.ReentrantLockArray;
import org.truffleruby.core.array.ConcurrentArray.StampedLockArray;
import org.truffleruby.core.array.ConcurrentArray.SynchronizedArray;
import org.truffleruby.core.array.layout.MyBiasedLock;
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

@NodeInfo(cost = NodeCost.NONE)
@NodeChild("self")
@ImportStatic(ArrayGuards.class)
public abstract class ArraySyncSetStoreNode extends RubyNode {

    @Child RubyNode builtinNode;

    public static ArraySyncSetStoreNode create(RubyNode builtinNode) {
        return ArraySyncSetStoreNodeGen.create(builtinNode, null);
    }

    public ArraySyncSetStoreNode(RubyNode builtinNode) {
        this.builtinNode = builtinNode;
    }

    public abstract Object executeWithSync(VirtualFrame frame, DynamicObject array);

    @Specialization(guards = "!isConcurrentArray(array)")
    public Object localArray(VirtualFrame frame, DynamicObject array) {
        return builtinNode.execute(frame);
    }

    @Specialization(guards = "isFixedSizeArray(array)")
    public Object migrateFixedSizeToSynchronized(VirtualFrame frame, DynamicObject array,
            @Cached("create(builtinNode)") ArraySyncSetStoreNode recursiveNode) {
        migrateInSafepoint(array);
        return recursiveNode.executeWithSync(frame, array);
    }

    @TruffleBoundary
    private void migrateInSafepoint(DynamicObject array) {
        final Thread thread = Thread.currentThread();
        getContext().getSafepointManager().pauseAllThreadsAndExecute(this, false, (rubyThread, currentNode) -> {
            if (Thread.currentThread() == thread) {
                final Object store = Layouts.ARRAY.getStore(array);
                if (store instanceof FixedSizeArray) { // Was not already migrated by another thread
                    FixedSizeArray fixedSizeArray = (FixedSizeArray) store;
                    SynchronizedArray synchronizedArray = new SynchronizedArray(fixedSizeArray.getStore());
                    Layouts.ARRAY.setStore(array, synchronizedArray);
                }
            }
        });
    }

    @Specialization(guards = "isSynchronizedArray(array)")
    public Object synchronizedArray(VirtualFrame frame, DynamicObject array) {
        synchronized (array) {
            return builtinNode.execute(frame);
        }
    }

    @Specialization(guards = "isReentrantLockArray(array)")
    public Object reentrantLockWrite(VirtualFrame frame, DynamicObject array) {
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
    public Object customLockWriteCached(VirtualFrame frame, DynamicObject array,
            @Cached("array") DynamicObject cachedArray,
            @Cached("getCustomLock(cachedArray)") MyBiasedLock lock) {
        try {
            lock.lock(getContext());
            return builtinNode.execute(frame);
        } finally {
            lock.unlock();
        }
    }

    @Specialization(guards = "isCustomLockArray(array)", replaces = "customLockWriteCached")
    public Object customLockWrite(VirtualFrame frame, DynamicObject array) {
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
    public Object stampedLockWrite(VirtualFrame frame, DynamicObject array) {
        final StampedLockArray stampedLockArray = (StampedLockArray) Layouts.ARRAY.getStore(array);
        final StampedLock lock = stampedLockArray.getLock();
        final long stamp = lock.tryWriteLock();
        if (stamp == 0L) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError();
        }
        try {
            return builtinNode.execute(frame);
        } finally {
            stampedUnlockWrite(lock, stamp);
        }
    }

    @TruffleBoundary
    private void stampedUnlockWrite(final StampedLock lock, final long stamp) {
        lock.unlockWrite(stamp);
    }

    @Specialization(guards = "isLayoutLockArray(array)")
    public Object layoutLockChangeLayout(VirtualFrame frame, DynamicObject array) {
        builtinNode.execute(frame);
        throw new AssertionError("TODO");
    }

}
