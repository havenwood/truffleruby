package org.truffleruby.core.hash;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReferenceArray;

import org.truffleruby.Layouts;
import org.truffleruby.core.UnsafeHolder;
import org.truffleruby.language.objects.ObjectGraphNode;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.object.basic.DynamicObjectBasic;

public final class ConcurrentHash implements ObjectGraphNode {

    private final AtomicReferenceArray<Entry> buckets;

    public static void initialize(DynamicObject hash) {
        Layouts.HASH.setFirstInSequence(hash, new Entry(0, null, null));
        Layouts.HASH.setLastInSequence(hash, new Entry(0, null, null));
    }

    public static void linkFirstLast(DynamicObject hash, Entry first, Entry last) {
        Entry sentinelFirst = Layouts.HASH.getFirstInSequence(hash);
        Entry sentinelLast = Layouts.HASH.getLastInSequence(hash);

        if (first != null) {
            sentinelFirst.setNextInSequence(first);
            first.setPreviousInSequence(sentinelFirst);

            sentinelLast.setPreviousInSequence(last);
            last.setNextInSequence(sentinelLast);
        } else {
            sentinelFirst.setNextInSequence(sentinelLast);
            sentinelLast.setPreviousInSequence(sentinelFirst);
        }
    }

    public ConcurrentHash() {
        this(BucketsStrategy.INITIAL_CAPACITY);
    }

    public ConcurrentHash(int capacity) {
        this.buckets = new AtomicReferenceArray<>(capacity);
    }

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

    public void getAdjacentObjects(Set<DynamicObject> reachable) {
        for (int i = 0; i < buckets.length(); i++) {
            Entry entry = buckets.get(i);
            while (entry != null) {
                if (entry.getKey() instanceof DynamicObject) {
                    reachable.add((DynamicObject) entry.getKey());
                }
                if (entry.getValue() instanceof DynamicObject) {
                    reachable.add((DynamicObject) entry.getValue());
                }
                entry = entry.getNextInLookup();
            }
        }
    }

    // {"compareByIdentity":boolean@1,
    // "defaultValue":Object[0],
    // "defaultBlock":Object@3,
    // "lastInSequence":Object@2,
    // "firstInSequence":Object@1,
    // "size":int@0,
    // "store":Object@0}

    private static final long STORE_OFFSET = UnsafeHolder.getFieldOffset(DynamicObjectBasic.class, "object1");
    private static final long SIZE_OFFSET = UnsafeHolder.getFieldOffset(DynamicObjectBasic.class, "primitive1");
    private static final long COMPARE_BY_IDENTITY_OFFSET = UnsafeHolder.getFieldOffset(DynamicObjectBasic.class, "primitive2");

    public static ConcurrentHash getStore(DynamicObject hash) {
        return (ConcurrentHash) UnsafeHolder.UNSAFE.getObject(hash, STORE_OFFSET);
    }

    public static int getSize(DynamicObject hash) {
        return (int) UnsafeHolder.UNSAFE.getLongVolatile(hash, SIZE_OFFSET);
    }

    public static boolean compareAndSetSize(DynamicObject hash, int old, int newSize) {
        return UnsafeHolder.UNSAFE.compareAndSwapLong(hash, SIZE_OFFSET, old, newSize);
    }

    public static boolean compareAndSetCompareByIdentity(DynamicObject hash, boolean old, boolean newSize) {
        return UnsafeHolder.UNSAFE.compareAndSwapLong(hash, COMPARE_BY_IDENTITY_OFFSET, old ? 1L : 0L, newSize ? 1L : 0L);
    }

}
