package org.truffleruby.core.array.layout;

import org.truffleruby.core.UnsafeHolder;

import sun.misc.Unsafe;

public final class ThreadStateReference {

    private static final Unsafe UNSAFE = UnsafeHolder.UNSAFE;

    final int index;
    final int[] store;
    final int offset;

    public ThreadStateReference(int index, int[] store) {
        this.index = index;
        this.store = store;
        this.offset = UNSAFE.arrayBaseOffset(int[].class) + index * UNSAFE.arrayIndexScale(int[].class);
        set(FastLayoutLock.INACTIVE);
    }

    public boolean compareAndSet(int expect, int update) {
        return UNSAFE.compareAndSwapInt(store, offset, expect, update);
    }

    public int get() {
        return UNSAFE.getIntVolatile(store, offset);
    }

    public void set(int value) {
        UNSAFE.putIntVolatile(store, offset, value);
    }

}