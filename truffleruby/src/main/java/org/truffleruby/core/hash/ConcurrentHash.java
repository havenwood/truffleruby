package org.truffleruby.core.hash;

import java.util.concurrent.atomic.AtomicReferenceArray;

import org.truffleruby.core.UnsafeHolder;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.object.basic.DynamicObjectBasic;

public final class ConcurrentHash {

    private final AtomicReferenceArray<Entry> buckets;

    public ConcurrentHash(Entry[] buckets) {
        this.buckets = new AtomicReferenceArray<>(buckets);
    }

    public AtomicReferenceArray<Entry> getBuckets() {
        return buckets;
    }

    public Entry[] toBucketArray() {
        Entry[] array = new Entry[buckets.length()];
        for (int i = 0; i < array.length; i++) {
            array[i] = buckets.get(i);
        }
        return array;
    }

    // {"compareByIdentity":boolean@1,
    // "defaultValue":Object[0],
    // "defaultBlock":Object@3,
    // "lastInSequence":Object@2,
    // "firstInSequence":Object@1,
    // "size":int@0,
    // "store":Object@0}

    private static final long FIRST_IN_SEQ_OFFSET = UnsafeHolder.getFieldOffset(DynamicObjectBasic.class, "object2");
    private static final long LAST_IN_SEQ_OFFSET = UnsafeHolder.getFieldOffset(DynamicObjectBasic.class, "object3");

    private static final long SIZE_OFFSET = UnsafeHolder.getFieldOffset(DynamicObjectBasic.class, "primitive1");

    public static boolean compareAndSetFirstInSeq(DynamicObject hash, Entry old, Entry newFirst) {
        return UnsafeHolder.UNSAFE.compareAndSwapObject(hash, FIRST_IN_SEQ_OFFSET, old, newFirst);
    }

    public static boolean compareAndSetLastInSeq(DynamicObject hash, Entry old, Entry newLast) {
        return UnsafeHolder.UNSAFE.compareAndSwapObject(hash, LAST_IN_SEQ_OFFSET, old, newLast);
    }

    public static boolean compareAndSetSize(DynamicObject hash, int old, int newSize) {
        return UnsafeHolder.UNSAFE.compareAndSwapLong(hash, SIZE_OFFSET, old, newSize);
    }

}
