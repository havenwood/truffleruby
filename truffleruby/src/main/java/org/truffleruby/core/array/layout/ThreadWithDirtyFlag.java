package org.truffleruby.core.array.layout;

public class ThreadWithDirtyFlag extends Thread {

    public volatile boolean dirty = false;

    public ThreadWithDirtyFlag(Runnable runnable) {
        super(runnable);
    }

}
