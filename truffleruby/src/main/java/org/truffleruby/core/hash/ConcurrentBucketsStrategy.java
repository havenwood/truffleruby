/*
 * Copyright (c) 2015, 2016 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.hash;

import java.util.Iterator;

import org.truffleruby.Layouts;
import org.truffleruby.RubyContext;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.objects.shared.SharedObjects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.DynamicObject;

public abstract class ConcurrentBucketsStrategy {

    public static final int INITIAL_CAPACITY = BucketsStrategy.INITIAL_CAPACITY;

    // The general technique to handle removal in a linked list is to first CAS node.next to a
    // removed node with removed.next = next and then CAS node.next to next. Otherwise,
    // concurrent insertions would be lost.
    // From Harris T. - A Pragmatic Implementation of Non-Blocking Linked-List,
    // using an extra Node as a way to mark the next reference as "deleted".

    public static void appendInSequence(ConcurrentEntry entry, ConcurrentEntry tail) {
        ConcurrentEntry last;
        do {
            last = tail.getPreviousInSequence();
            entry.setPreviousInSequence(last);
        } while (last.isRemoved() || !last.compareAndSetNextInSequence(tail, entry));

        // previousInSequence is only changed for deletion and set for append. tail.prev is changed
        // for append.
        if (!tail.compareAndSetPreviousInSequence(last, entry)) {
            assert false; // TODO
        }

        entry.setPublished(true);
    }

    public static boolean removeFromSequence(ConcurrentEntry entry) {
        assert !entry.isRemoved() && entry.getKey() != null;
        while (!entry.isPublished()) {
            Thread.yield();
        }

        // First mark as deleted to avoid losing concurrent insertions

        // Block entry -> nextDeleted -> next
        ConcurrentEntry next;
        ConcurrentEntry nextDeleted;
        while (true) {
            next = entry.getNextInSequence();
            if (next.isLock()) {
                // Concurrent delete on next
                while (entry.getNextInSequence().isLock()) {
                    Thread.yield();
                }
                // CAS will fail as entry.next is no longer the lock
            } else if (next.isRemoved()) {
                // when 2 thread concurrently try to remove the same entry
                return false;
            } else {
                nextDeleted = new ConcurrentEntry(true, false, null, next, null);
                if (entry.compareAndSetNextInSequence(next, nextDeleted)) {
                    break;
                }
            }
        }

        // Block prev -> lockPrevDelete -> entry
        ConcurrentEntry prev;
        ConcurrentEntry prevNext;
        final ConcurrentEntry lockPrevDelete = new ConcurrentEntry(true, true, null, entry, null);
        while (true) {
            prev = entry.getPreviousInSequence();
            assert !prev.isRemoved();
            prevNext = prev.getNextInSequence();
            if (prevNext.isRemoved()) {
                // Concurrent delete on prev
                while (entry.getPreviousInSequence() == prev) {
                    Thread.yield();
                }
                // CAS will fail as prev.next is not entry
            } else {
                assert prevNext == entry;
                if (prev.compareAndSetNextInSequence(entry, lockPrevDelete)) {
                    break;
                }
            }
        }

        // Block prev <- prevDeleted <- entry
        ConcurrentEntry prevDeleted;
        prevDeleted = new ConcurrentEntry(true, false, prev, null, null);
        if (!entry.compareAndSetPreviousInSequence(prev, prevDeleted)) {
            assert false; // TODO
        }

//        do {
//            prev = entry.getPreviousInSequence();
//            assert !prev.isRemoved();
//            prevDeleted = new ConcurrentEntry(true, false, prev, null, null);
//        } while (!entry.compareAndSetPreviousInSequence(prev, prevDeleted));

        // Now, nobody can insert or remove between
        // prev <-> prevDeleted <-> entry -> nextDeleted -> next

        // prev -> next
        if (!prev.compareAndSetNextInSequence(lockPrevDelete, next)) {
            assert false; // TODO
            // prev = prevDeleted.getPreviousInSequence();
        }

        // prev <- next
        if (!next.compareAndSetPreviousInSequence(entry, prev)) {
            assert false; // TODO failed!
            // prev = prevDeleted.getPreviousInSequence();
        }

        return true;
    }

    public static ConcurrentEntry removeFirstFromSequence(DynamicObject hash, ConcurrentEntry tail) {
        // TODO: should skip to next instead of busy waiting on first?
        ConcurrentEntry entry = ConcurrentHash.getFirstEntry(hash);
        while (entry != tail) {
            if (removeFromSequence(entry)) {
                return entry;
            }

            entry = ConcurrentHash.getFirstEntry(hash);
        }

        // Empty Hash, nothing to remove
        return null;
    }

    public static boolean insertInLookup(AtomicReferenceArray<ConcurrentEntry> buckets, int index, ConcurrentEntry firstEntry, ConcurrentEntry newEntry) {
        assert firstEntry == null || !firstEntry.isRemoved();
        assert BucketsStrategy.getBucketIndex(newEntry.getHashed(), buckets.length()) == index;
        assert firstEntry == null || BucketsStrategy.getBucketIndex(firstEntry.getHashed(), buckets.length()) == index;
        newEntry.setNextInLookup(firstEntry);
        return buckets.compareAndSet(index, firstEntry, newEntry);
    }

    public static void removeFromLookup(DynamicObject hash, ConcurrentEntry entry, AtomicReferenceArray<ConcurrentEntry> store) {
        final int index = BucketsStrategy.getBucketIndex(entry.getHashed(), store.length());

        // Prevent insertions after entry so adjacent concurrent deletes serialize
        ConcurrentEntry next, nextDeleted;
        do {
            next = entry.getNextInLookup();
            assert next == null || !next.isRemoved();
            nextDeleted = new ConcurrentEntry(true, false, null, null, next);
        } while (!entry.compareAndSetNextInLookup(next, nextDeleted));

        while (true) {
            final ConcurrentEntry previousEntry = searchPreviousLookupEntry(hash, store, entry, index);
            if (previousEntry == null) {
                if (store.compareAndSet(index, entry, next)) {
                    break;
                }
            } else if (!previousEntry.isRemoved()) {
                if (previousEntry.compareAndSetNextInLookup(entry, next)) {
                    break;
                }
            } else {
                Thread.yield();
            }
        }
    }

    private static ConcurrentEntry searchPreviousLookupEntry(DynamicObject hash, AtomicReferenceArray<ConcurrentEntry> store, ConcurrentEntry entry, int index) {
        assert store == ConcurrentHash.getStore(hash).getBuckets();
        assert BucketsStrategy.getBucketIndex(entry.getHashed(), store.length()) == index;

        // ConcurrentEntry keep identity on rehash/resize, so we just need to find it in the bucket
        ConcurrentEntry previousEntry = null;
        ConcurrentEntry e = store.get(index);

        while (e != null) {
            if (e == entry) {
                return previousEntry;
            }

            previousEntry = e;
            e = e.getNextInLookup();
        }

        assert store == ConcurrentHash.getStore(hash).getBuckets();
        assert BucketsStrategy.getBucketIndex(entry.getHashed(), store.length()) == index;

        CompilerDirectives.transferToInterpreter();
        StringBuilder builder = new StringBuilder();
        builder.append('\n');
        e = store.get(index);
        builder.append("Searched for " + entry.getKey() + "(bucket " + index + ") among " + e + ":\n");
        while (e != null) {
            if (!e.isRemoved()) {
                builder.append(e.getKey() + "\n");
            }
            e = e.getNextInLookup();
        }
        builder.append('\n');
        builder.append("size = " + ConcurrentHash.getSize(hash) + "\n");
        for (int i = 0; i < store.length(); i++) {
            builder.append(i + ":");
            e = store.get(i);
            while (e != null) {
                if (!e.isRemoved()) {
                    builder.append(" " + e.getKey());
                }
                e = e.getNextInLookup();
            }
            builder.append('\n');
        }
        builder.append('\n');
        System.err.println(builder.toString());

        throw new AssertionError("Could not find the previous entry in the bucket");
    }

    @TruffleBoundary
    public static void resize(RubyContext context, DynamicObject hash, int newSize) {
        System.err.println("RESIZE " + newSize);
        assert HashGuards.isConcurrentHash(hash);
        assert HashOperations.verifyStore(context, hash);

        final int bucketsCount = BucketsStrategy.capacityGreaterThan(newSize) * BucketsStrategy.OVERALLOCATE_FACTOR;
        final AtomicReferenceArray<ConcurrentEntry> newEntries = new AtomicReferenceArray<>(bucketsCount);

        for (ConcurrentEntry entry : iterableEntries(hash)) {
            final int bucketIndex = BucketsStrategy.getBucketIndex(entry.getHashed(), bucketsCount);

            entry.setNextInLookup(newEntries.get(bucketIndex));
            newEntries.set(bucketIndex, entry);
        }

        ConcurrentHash.getStore(hash).setBuckets(newEntries);
        assert HashOperations.verifyStore(context, hash);
    }

    public static void copyInto(RubyContext context, DynamicObject from, DynamicObject to) {
        assert RubyGuards.isRubyHash(from);
        assert HashGuards.isConcurrentHash(from);
        assert HashOperations.verifyStore(context, from);
        assert RubyGuards.isRubyHash(to);
        assert !HashGuards.isConcurrentHash(to) && !SharedObjects.isShared(to);
        assert HashOperations.verifyStore(context, to);

        final Entry[] newEntries = new Entry[ConcurrentHash.getStore(from).getBuckets().length()];

        Entry firstInSequence = null;
        Entry lastInSequence = null;

        for (ConcurrentEntry entry : iterableEntries(from)) {
            final Entry newEntry = new Entry(entry.getHashed(), entry.getKey(), entry.getValue());

            final int index = BucketsStrategy.getBucketIndex(entry.getHashed(), newEntries.length);

            newEntry.setNextInLookup(newEntries[index]);
            newEntries[index] = newEntry;

            if (firstInSequence == null) {
                firstInSequence = newEntry;
            }

            if (lastInSequence != null) {
                lastInSequence.setNextInSequence(newEntry);
                newEntry.setPreviousInSequence(lastInSequence);
            }

            lastInSequence = newEntry;
        }

        int size = ConcurrentHash.getSize(from);
        Layouts.HASH.setStore(to, newEntries);
        Layouts.HASH.setSize(to, size);
        Layouts.HASH.setFirstInSequence(to, firstInSequence);
        Layouts.HASH.setLastInSequence(to, lastInSequence);
        assert HashOperations.verifyStore(context, to);
    }

    public static void fromBuckets(BucketsPromotionResult buckets, DynamicObject hash) {
        assert RubyGuards.isRubyHash(hash);

        final int capacity = Math.max(buckets.getBuckets().length, INITIAL_CAPACITY);
        final AtomicReferenceArray<ConcurrentEntry> newEntries = new AtomicReferenceArray<>(capacity);

        ConcurrentEntry firstInSequence = null;
        ConcurrentEntry lastInSequence = null;

        Entry entry = buckets.getFirstInSequence();

        while (entry != null) {
            // Immediately mark as published as we have the layout lock or we are the sharing a Hash
            final ConcurrentEntry newEntry = new ConcurrentEntry(entry.getHashed(), entry.getKey(), entry.getValue(), true);

            final int index = BucketsStrategy.getBucketIndex(entry.getHashed(), newEntries.length());

            newEntry.setNextInLookup(newEntries.get(index));
            newEntries.set(index, newEntry);

            if (firstInSequence == null) {
                firstInSequence = newEntry;
            }

            if (lastInSequence != null) {
                lastInSequence.setNextInSequence(newEntry);
                newEntry.setPreviousInSequence(lastInSequence);
            }

            lastInSequence = newEntry;

            entry = entry.getNextInSequence();
        }

        ConcurrentHash.getStore(hash).setBuckets(newEntries);
        ConcurrentHash.setSize(hash, buckets.getSize());
        ConcurrentHash.linkFirstLast(hash, firstInSequence, lastInSequence);
    }

    public static Iterator<KeyValue> iterateKeyValues(DynamicObject hash) {
        assert HashGuards.isConcurrentHash(hash);
        final ConcurrentEntry first = ConcurrentHash.getFirstEntry(hash);
        final ConcurrentEntry tail = ConcurrentHash.getLastInSequence(hash);

        return new Iterator<KeyValue>() {

            private ConcurrentEntry entry = first;

            @Override
            public boolean hasNext() {
                return entry != tail;
            }

            @Override
            public KeyValue next() {
                assert hasNext();

                final KeyValue entryResult = new KeyValue(entry.getKey(), entry.getValue());

                // Goes through "being deleted" entries, much simpler to check
                entry = entry.getNextInSequence();
                if (entry.isRemoved()) {
                    entry = entry.getNextInSequence();
                }

                return entryResult;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

        };
    }

    public static Iterable<KeyValue> iterableKeyValues(DynamicObject hash) {
        return () -> iterateKeyValues(hash);
    }

    public static Iterator<ConcurrentEntry> iterateEntries(DynamicObject hash) {
        assert HashGuards.isConcurrentHash(hash);
        final ConcurrentEntry first = ConcurrentHash.getFirstEntry(hash);
        final ConcurrentEntry tail = ConcurrentHash.getLastInSequence(hash);

        return new Iterator<ConcurrentEntry>() {

            private ConcurrentEntry entry = first;

            @Override
            public boolean hasNext() {
                return entry != tail;
            }

            @Override
            public ConcurrentEntry next() {
                assert hasNext();

                final ConcurrentEntry current = entry;

                // Goes through "being deleted" entries, much simpler to check
                entry = entry.getNextInSequence();
                if (entry.isRemoved()) {
                    entry = entry.getNextInSequence();
                }

                return current;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

        };
    }

    public static Iterable<ConcurrentEntry> iterableEntries(DynamicObject hash) {
        return () -> iterateEntries(hash);
    }

}
