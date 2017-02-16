package org.truffleruby.core.array.layout;

import java.util.concurrent.atomic.AtomicInteger;

public class ThreadWithDirtyFlag extends Thread {

    public volatile boolean dirty = false;
    public AtomicInteger threadState;

    private final LayoutLock.Accessor layoutLockAccessor;
    private final FastLayoutLock fastLayoutLock;

    public ThreadWithDirtyFlag(Runnable runnable) {
        super(runnable);
        this.layoutLockAccessor = LayoutLock.GLOBAL_LOCK.access();
        this.fastLayoutLock = FastLayoutLock.GLOBAL_LOCK;
        this.threadState = fastLayoutLock.registerThread(Thread.currentThread().getId());
    }

    public LayoutLock.Accessor getLayoutLockAccessor() {
        return layoutLockAccessor;
    }

    public FastLayoutLock getFastLayoutLock() {
        return fastLayoutLock;
    }

    public AtomicInteger getThreadState() {
        return threadState;
    }

}
