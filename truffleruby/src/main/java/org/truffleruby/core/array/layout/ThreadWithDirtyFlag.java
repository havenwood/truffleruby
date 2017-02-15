package org.truffleruby.core.array.layout;

public class ThreadWithDirtyFlag extends Thread {

    public volatile boolean dirty = false;

    private final LayoutLock.Accessor layoutLockAccessor;
    private final FastLayoutLock.Accessor fastLayoutLockAccessor;

    public ThreadWithDirtyFlag(Runnable runnable) {
        super(runnable);
        this.layoutLockAccessor = LayoutLock.GLOBAL_LOCK.access();
        this.fastLayoutLockAccessor = FastLayoutLock.GLOBAL_LOCK.access();
    }

    public LayoutLock.Accessor getLayoutLockAccessor() {
        return layoutLockAccessor;
    }

    public FastLayoutLock.Accessor getFastLayoutLockAccessor() {
        return fastLayoutLockAccessor;
    }

}
