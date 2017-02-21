package org.truffleruby.core.array.layout;

import java.util.concurrent.atomic.AtomicLong;

public class ThreadWithDirtyFlag extends Thread {

    public volatile boolean dirty = false;
    public final FastLayoutLock.ThreadState threadState;
    public final long threadId = threadIds.incrementAndGet();

    private final LayoutLock.Accessor layoutLockAccessor;
    private final FastLayoutLock fastLayoutLock;
    private static AtomicLong threadIds = new AtomicLong();

    public ThreadWithDirtyFlag(Runnable runnable) {
        super(runnable);
        this.layoutLockAccessor = LayoutLock.GLOBAL_LOCK.access();
        this.fastLayoutLock = FastLayoutLock.GLOBAL_LOCK;
        this.threadState = fastLayoutLock.registerThread(getThreadId());
    }

    public LayoutLock.Accessor getLayoutLockAccessor() {
        return layoutLockAccessor;
    }

    public FastLayoutLock getFastLayoutLock() {
        return fastLayoutLock;
    }

    public FastLayoutLock.ThreadState getThreadState() {
        return threadState;
    }

    public long getThreadId() {
        return threadId;
    }
}
