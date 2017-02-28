/*
 * From java.util.concurrent.atomic.AtomicReferenceArray,
 * with proper transferToInterpreter for index-out-of-bounds.
 * 
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package org.truffleruby.core.hash;

import org.truffleruby.core.UnsafeHolder;

import com.oracle.truffle.api.CompilerDirectives;

import sun.misc.Unsafe;

public final class AtomicReferenceArray<E> {

    private static final Unsafe unsafe = UnsafeHolder.UNSAFE;
    private static final int base;
    private static final int shift;
    private final Object[] array; // must have exact type Object[]

    static {
        try {
            base = unsafe.arrayBaseOffset(Object[].class);
            int scale = unsafe.arrayIndexScale(Object[].class);
            if ((scale & (scale - 1)) != 0)
                throw new Error("data type scale not a power of two");
            shift = 31 - Integer.numberOfLeadingZeros(scale);
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    private long checkedByteOffset(int i) {
        if (i < 0 || i >= array.length) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IndexOutOfBoundsException("index " + i);
        }

        return byteOffset(i);
    }

    private static long byteOffset(int i) {
        return ((long) i << shift) + base;
    }

    public AtomicReferenceArray(int length) {
        array = new Object[length];
    }

    public int length() {
        return array.length;
    }

    public E get(int i) {
        return getRaw(checkedByteOffset(i));
    }

    @SuppressWarnings("unchecked")
    private E getRaw(long offset) {
        return (E) unsafe.getObjectVolatile(array, offset);
    }

    public void set(int i, E newValue) {
        unsafe.putObjectVolatile(array, checkedByteOffset(i), newValue);
    }

    public void lazySet(int i, E newValue) {
        unsafe.putOrderedObject(array, checkedByteOffset(i), newValue);
    }

    @SuppressWarnings("unchecked")
    public E getAndSet(int i, E newValue) {
        return (E) unsafe.getAndSetObject(array, checkedByteOffset(i), newValue);
    }

    public boolean compareAndSet(int i, E expect, E update) {
        return compareAndSetRaw(checkedByteOffset(i), expect, update);
    }

    private boolean compareAndSetRaw(long offset, E expect, E update) {
        return unsafe.compareAndSwapObject(array, offset, expect, update);
    }

}
