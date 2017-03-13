package org.truffleruby.core.array.layout;

import org.truffleruby.core.UnsafeHolder;

public final class ThreadStateReference {
    final int index;
    final int[] store;
    final int offset;

    public ThreadStateReference(int index, int[] store) {
        this.index = index;
        this.store = store;
        this.offset = UnsafeHolder.UNSAFE.arrayBaseOffset(store.getClass()) + index * UnsafeHolder.UNSAFE.arrayIndexScale(store.getClass());
        set(FastLayoutLock.INACTIVE);
    }

    public boolean compareAndSet(int expect, int update) {
        return UnsafeHolder.UNSAFE.compareAndSwapInt(store, offset, expect, update);
        // return store.compareAndSet(index, expect, update);
    }

    public int get() {
        return store[index];
    }

    public void set(int val) {
        store[index] = val;
    }

}