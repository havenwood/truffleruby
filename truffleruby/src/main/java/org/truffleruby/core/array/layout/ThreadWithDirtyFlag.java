package org.truffleruby.core.array.layout;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.truffleruby.core.array.ConcurrentArray;

import com.oracle.truffle.api.object.DynamicObject;

public class ThreadWithDirtyFlag extends Thread {

    private static final AtomicLong threadIds = new AtomicLong();

    public volatile boolean dirty = false;
    public final long threadId = threadIds.incrementAndGet();

    private final LayoutLock.Accessor layoutLockAccessor;
    private final FastLayoutLock fastLayoutLock;
    private final TransitioningFastLayoutLock transitioningFastLayoutLock;
    private HashMap<DynamicObject, AtomicInteger> lockStates = new HashMap<>();

    public ThreadWithDirtyFlag(Runnable runnable) {
        super(runnable);
        this.layoutLockAccessor = LayoutLock.GLOBAL_LOCK.access();
        this.fastLayoutLock = FastLayoutLock.GLOBAL_LOCK;
        this.transitioningFastLayoutLock = TransitioningFastLayoutLock.GLOBAL_LOCK;
    }

    public LayoutLock.Accessor getLayoutLockAccessor() {
        return layoutLockAccessor;
    }

    public FastLayoutLock getFastLayoutLock() {
        return fastLayoutLock;
    }

    public TransitioningFastLayoutLock getTransitioningFastLayoutLock() {
        return transitioningFastLayoutLock;
    }

    public AtomicInteger getTransitioningThreadState(DynamicObject array) {
        AtomicInteger ts = lockStates.get(array);
        if (ts == null) {
            ts = transitioningFastLayoutLock.registerThread(Thread.currentThread().getId());
            lockStates.put(array, ts);
        }
        return ts;
    }

    public AtomicInteger getThreadState(DynamicObject array) {
        AtomicInteger ts = lockStates.get(array);
        if (ts == null) {
            ts = fastLayoutLock.registerThread(Thread.currentThread().getId());
            lockStates.put(array, ts);
        }
        return ts;
    }

    public long getThreadId() {
        return threadId;
    }
}
