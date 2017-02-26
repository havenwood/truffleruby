package org.truffleruby.core.array.layout;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ThreadWithDirtyFlag extends Thread {

    private static final AtomicLong threadIds = new AtomicLong();

    public volatile boolean dirty = false;
    public final AtomicInteger threadState;
    public final long threadId = threadIds.incrementAndGet();

    private final LayoutLock.Accessor layoutLockAccessor;
    private final FastLayoutLock fastLayoutLock;
    private final TransitioningFastLayoutLock transitioningFastLayoutLock;

    public ThreadWithDirtyFlag(Runnable runnable) {
        super(runnable);
        this.layoutLockAccessor = LayoutLock.GLOBAL_LOCK.access();
        this.fastLayoutLock = FastLayoutLock.GLOBAL_LOCK;
        this.transitioningFastLayoutLock = TransitioningFastLayoutLock.GLOBAL_LOCK;
        this.threadState = fastLayoutLock.registerThread(getThreadId());
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

    public AtomicInteger getThreadState() {
        return threadState;
    }

    public long getThreadId() {
        return threadId;
    }
}
