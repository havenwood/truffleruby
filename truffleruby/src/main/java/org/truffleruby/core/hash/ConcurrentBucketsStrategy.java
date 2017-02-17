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
import java.util.concurrent.atomic.AtomicReferenceArray;

import org.truffleruby.Layouts;
import org.truffleruby.RubyContext;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.objects.shared.SharedObjects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.DynamicObject;

public abstract class ConcurrentBucketsStrategy {

    public static void appendInSequence(ConcurrentEntry entry, ConcurrentEntry tail) {
        ConcurrentEntry last;
        do {
            last = tail.getPreviousInSequence();
            entry.setPreviousInSequence(last);
        } while (last.isRemoved() || !last.compareAndSetNextInSequence(tail, entry));

        if (!tail.compareAndSetPreviousInSequence(last, entry)) {
            assert false; // TODO
        }
    }

    public static boolean removeFromSequence(ConcurrentEntry entry) {
        // First mark as deleted to avoid losing concurrent insertions
        ConcurrentEntry nextInSequence;
        ConcurrentEntry nextDeleted;
        do {
            nextInSequence = entry.getNextInSequence();
            nextDeleted = new ConcurrentEntry(true, nextInSequence);
            if (nextInSequence.isRemoved()) {
                // when 2 thread concurrently try to remove the same entry
                return false;
            }
        } while (!entry.compareAndSetNextInSequence(nextInSequence, nextDeleted));
        // Now, nobody can insert between entry, nextDeleted and nextInSequence

        // Link entry.prev -> nextInSequence, as entry.next and nextDeleted.next cannot be changed by insertion
        ConcurrentEntry previousInSequence;
        do {
            previousInSequence = entry.getPreviousInSequence();
        } while (!previousInSequence.compareAndSetNextInSequence(entry, nextInSequence));

        if (!nextInSequence.compareAndSetPreviousInSequence(entry, previousInSequence)) {
            assert false; // TODO
        }

        return true;
    }

    public static ConcurrentEntry removeFirstFromSequence(ConcurrentEntry head, ConcurrentEntry tail) {
        ConcurrentEntry entry = head.getNextInSequence();
        if (entry == tail) {
            // Empty Hash, nothing to remove
            return null;
        }

        // First mark as deleted to avoid losing concurrent insertions
        ConcurrentEntry nextInSequence;
        ConcurrentEntry nextDeleted;
        do {
            nextInSequence = entry.getNextInSequence();
            while (nextInSequence.isRemoved()) {
                // when another thread is removing the same entry, wait and shift the next entry
                entry = head.getNextInSequence();
                if (entry == tail) {
                    return null;
                }
                nextInSequence = entry.getNextInSequence();
            }
            nextDeleted = new ConcurrentEntry(true, nextInSequence);
        } while (!entry.compareAndSetNextInSequence(nextInSequence, nextDeleted));
        // Now, nobody can insert between entry, nextDeleted and nextInSequence

        // Link entry.prev -> nextInSequence, as entry.next and nextDeleted.next cannot be changed by insertion
        ConcurrentEntry previousInSequence;
        do {
            previousInSequence = entry.getPreviousInSequence();
        } while (!previousInSequence.compareAndSetNextInSequence(entry, nextInSequence));

        if (!nextInSequence.compareAndSetPreviousInSequence(entry, previousInSequence)) {
            assert false; // TODO
        }

        return entry;
    }

    public static void removeFromLookup(ConcurrentEntry entry, AtomicReferenceArray<ConcurrentEntry> store, int index, ConcurrentEntry previousEntry) {
        if (previousEntry == null) {
            if (!store.compareAndSet(index, entry, entry.getNextInLookup())) {
                assert false; // TODO
            }
        } else {
            if (!previousEntry.compareAndSetNextInLookup(entry, entry.getNextInLookup())) {
                assert false; // TODO
            }
        }
    }

    public static ConcurrentHashLookupResult searchPreviousLookupEntry(AtomicReferenceArray<ConcurrentEntry> store, ConcurrentEntry entry) {
        // ConcurrentEntry keep identity on rehash/resize, so we just need to find it in the bucket
        final int index = BucketsStrategy.getBucketIndex(entry.getHashed(), store.length());

        ConcurrentEntry previousEntry = null;
        ConcurrentEntry e = store.get(index);

        while (e != null) {
            if (e == entry) {
                return new ConcurrentHashLookupResult(store, entry.getHashed(), index, previousEntry, entry);
            }

            previousEntry = e;
            e = e.getNextInLookup();
        }

        CompilerDirectives.transferToInterpreter();
        throw new AssertionError("Could not find the previous entry in the bucket");
    }

    @TruffleBoundary
    public static void resize(RubyContext context, DynamicObject hash, int newSize) {
        assert HashGuards.isConcurrentHash(hash);
        assert HashOperations.verifyStore(context, hash);

        final int bucketsCount = BucketsStrategy.capacityGreaterThan(newSize) * BucketsStrategy.OVERALLOCATE_FACTOR;
        final ConcurrentHash newConcurrentHash = new ConcurrentHash(bucketsCount);
        final AtomicReferenceArray<ConcurrentEntry> newEntries = newConcurrentHash.getBuckets();

        for (ConcurrentEntry entry : iterableEntries(hash)) {
            final int bucketIndex = BucketsStrategy.getBucketIndex(entry.getHashed(), bucketsCount);
            ConcurrentEntry previousInLookup = newEntries.get(bucketIndex);

            if (previousInLookup == null) {
                newEntries.set(bucketIndex, entry);
            } else {
                while (previousInLookup.getNextInLookup() != null) {
                    previousInLookup = previousInLookup.getNextInLookup();
                }

                previousInLookup.setNextInLookup(entry);
            }

            entry.setNextInLookup(null);
        }

        Layouts.HASH.setStore(hash, newConcurrentHash);
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

        final ConcurrentHash concurrentHash = new ConcurrentHash(buckets.getBuckets().length);
        final AtomicReferenceArray<ConcurrentEntry> newEntries = concurrentHash.getBuckets();

        ConcurrentEntry firstInSequence = null;
        ConcurrentEntry lastInSequence = null;

        Entry entry = buckets.getFirstInSequence();

        while (entry != null) {
            final ConcurrentEntry newEntry = new ConcurrentEntry(entry.getHashed(), entry.getKey(), entry.getValue());

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

        Layouts.HASH.setStore(hash, concurrentHash);
        Layouts.HASH.setSize(hash, buckets.getSize());
        ConcurrentHash.linkFirstLast(hash, firstInSequence, lastInSequence);
    }

    public static Iterator<KeyValue> iterateKeyValues(DynamicObject hash) {
        assert HashGuards.isConcurrentHash(hash);
        final ConcurrentEntry head = ConcurrentHash.getFirstInSequence(hash);
        final ConcurrentEntry tail = ConcurrentHash.getLastInSequence(hash);

        return new Iterator<KeyValue>() {

            private ConcurrentEntry entry = head.getNextInSequence();

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
        final ConcurrentEntry head = ConcurrentHash.getFirstInSequence(hash);
        final ConcurrentEntry tail = ConcurrentHash.getLastInSequence(hash);

        return new Iterator<ConcurrentEntry>() {

            private ConcurrentEntry entry = head.getNextInSequence();

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
