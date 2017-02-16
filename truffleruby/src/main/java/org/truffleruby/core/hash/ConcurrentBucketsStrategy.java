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
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReferenceArray;

import org.truffleruby.Layouts;
import org.truffleruby.RubyContext;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.objects.shared.SharedObjects;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.DynamicObject;

public abstract class ConcurrentBucketsStrategy {

    @TruffleBoundary
    public static void resize(RubyContext context, DynamicObject hash, int newSize) {
        assert HashGuards.isConcurrentHash(hash);
        assert HashOperations.verifyStore(context, hash);

        final int bucketsCount = BucketsStrategy.capacityGreaterThan(newSize) * BucketsStrategy.OVERALLOCATE_FACTOR;
        final ConcurrentHash newConcurrentHash = new ConcurrentHash(bucketsCount);
        final AtomicReferenceArray<ConcurrentEntry> newEntries = newConcurrentHash.getBuckets();

        final ConcurrentEntry last = ConcurrentHash.getLastInSequence(hash);
        ConcurrentEntry entry = ConcurrentHash.getFirstInSequence(hash).getNextInSequence();

        while (entry != last) {
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
            entry = entry.getNextInSequence();
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

        final ConcurrentEntry last = ConcurrentHash.getLastInSequence(from);
        ConcurrentEntry entry = ConcurrentHash.getFirstInSequence(from).getNextInSequence();

        while (entry != last) {
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

            entry = entry.getNextInSequence();
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
        final ConcurrentEntry firstInSequence = ConcurrentHash.getFirstInSequence(hash);
        final ConcurrentEntry lastInSequence = ConcurrentHash.getLastInSequence(hash);

        return new Iterator<KeyValue>() {

            private ConcurrentEntry entry = firstInSequence.getNextInSequence();

            @Override
            public boolean hasNext() {
                return entry != lastInSequence;
            }

            @Override
            public KeyValue next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }

                final KeyValue entryResult = new KeyValue(entry.getKey(), entry.getValue());

                entry = entry.getNextInSequence();

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

}
