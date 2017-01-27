package org.truffleruby.core.array.layout;

public class ThreadWithDirtyFlag extends Thread {

    public volatile boolean dirty = false;

    private final LayoutLock.Accessor layoutLockAccessor;

    public ThreadWithDirtyFlag(Runnable runnable) {
        super(runnable);
        this.layoutLockAccessor = LayoutLock.GLOBAL_LOCK.access();
    }

    public LayoutLock.Accessor getLayoutLockAccessor() {
        return layoutLockAccessor;
    }

}
