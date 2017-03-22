package org.truffleruby.core.hash;

import java.util.Set;

import org.truffleruby.Layouts;
import org.truffleruby.core.UnsafeHolder;
import org.truffleruby.core.array.layout.LayoutLock;
import org.truffleruby.language.objects.ObjectGraphNode;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Layout;
import com.oracle.truffle.api.object.ObjectType;

public final class ConcurrentHash implements ObjectGraphNode {

    private final ConcurrentEntry head;
    private final ConcurrentEntry tail;
    private volatile AtomicReferenceArray<ConcurrentEntry> buckets;

    private final LayoutLock layoutLock = new LayoutLock();

    public void linkFirstLast(ConcurrentEntry first, ConcurrentEntry last) {
        if (first != null) {
            head.setNextInSequence(first);
            first.setPreviousInSequence(head);

            tail.setPreviousInSequence(last);
            last.setNextInSequence(tail);
        } else {
            head.setNextInSequence(tail);
            tail.setPreviousInSequence(head);
        }
    }

    public ConcurrentHash(DynamicObject hash) {
        this.head = new ConcurrentEntry(0, null, null, false);
        this.tail = new ConcurrentEntry(0, null, null, false);
        this.buckets = new AtomicReferenceArray<>(ConcurrentBucketsStrategy.INITIAL_CAPACITY);
    }

    public AtomicReferenceArray<ConcurrentEntry> getBuckets() {
        return buckets;
    }

    public void setBuckets(AtomicReferenceArray<ConcurrentEntry> buckets) {
        this.buckets = buckets;
    }

    public ConcurrentEntry getHead() {
        return head;
    }

    public ConcurrentEntry getTail() {
        return tail;
    }

    public LayoutLock getLayoutLock() {
        return layoutLock;
    }

    /** Returns first non-removed entry, possibly tail */
    public ConcurrentEntry getFirstEntry() {
        ConcurrentEntry entry = head.getNextInSequence();
        if (entry.isRemoved()) {
            entry = entry.getNextInSequence();
        }
        assert !entry.isRemoved();
        return entry;
    }

    public void getAdjacentObjects(Set<DynamicObject> reachable) {
        for (int i = 0; i < buckets.length(); i++) {
            ConcurrentEntry entry = buckets.get(i);
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

    // @{"compareByIdentity":boolean@1,
    // "defaultValue":Object[0],
    // "defaultBlock":DynamicObject@3,
    // "lastInSequence":Entry@2,
    // "firstInSequence":Entry@1,
    // "size":int@0,
    // "store":Object@0}


    private static final Layout LAYOUT = Layout.createLayout();
    private static final DynamicObject SOME_OBJECT = LAYOUT.newInstance(LAYOUT.createShape(new ObjectType()));

    private static final long SIZE_OFFSET = UnsafeHolder.getFieldOffset(SOME_OBJECT.getClass(), "primitive1");
    private static final long COMPARE_BY_IDENTITY_OFFSET = UnsafeHolder.getFieldOffset(SOME_OBJECT.getClass(), "primitive2");

    public static ConcurrentHash getStore(DynamicObject hash) {
        return (ConcurrentHash) Layouts.HASH.getStore(hash);
    }

    public static int getSize(DynamicObject hash) {
        return (int) UnsafeHolder.UNSAFE.getLongVolatile(hash, SIZE_OFFSET);
    }

    public static void setSize(DynamicObject hash, int size) {
        UnsafeHolder.UNSAFE.putLongVolatile(hash, SIZE_OFFSET, size);
    }

    public static int incrementAndGetSize(DynamicObject hash) {
        // TODO: incorrect in enterprise OM!
        return (int) UnsafeHolder.UNSAFE.getAndAddLong(hash, SIZE_OFFSET, 1L) + 1;
    }

    public static void decrementSize(DynamicObject hash) {
        UnsafeHolder.UNSAFE.getAndAddLong(hash, SIZE_OFFSET, -1L);
    }

    public static boolean getCompareByIdentity(DynamicObject hash) {
        return UnsafeHolder.UNSAFE.getLongVolatile(hash, COMPARE_BY_IDENTITY_OFFSET) == 1L;
    }

    public static boolean compareAndSetCompareByIdentity(DynamicObject hash, boolean old, boolean byIdentity) {
        return UnsafeHolder.UNSAFE.compareAndSwapLong(hash, COMPARE_BY_IDENTITY_OFFSET, old ? 1L : 0L, byIdentity ? 1L : 0L);
    }

}
