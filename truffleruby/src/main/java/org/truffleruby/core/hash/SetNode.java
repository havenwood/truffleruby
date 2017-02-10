/*
 * Copyright (c) 2013, 2016 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.hash;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

import java.util.concurrent.atomic.AtomicReferenceArray;

import org.truffleruby.Layouts;
import org.truffleruby.core.array.layout.GetLayoutLockAccessorNode;
import org.truffleruby.core.array.layout.LayoutLock;
import org.truffleruby.core.array.layout.LayoutLockFinishLayoutChangeNode;
import org.truffleruby.core.array.layout.LayoutLockStartLayoutChangeNode;
import org.truffleruby.core.array.layout.LayoutLockStartWriteNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.objects.shared.WriteBarrierNode;

@ImportStatic(HashGuards.class)
@NodeChildren({
        @NodeChild(value = "hash", type = RubyNode.class),
        @NodeChild(value = "key", type = RubyNode.class),
        @NodeChild(value = "value", type = RubyNode.class),
        @NodeChild(value = "byIdentity", type = RubyNode.class)
})
public abstract class SetNode extends RubyNode {

    @Child private HashNode hashNode = new HashNode();
    @Child private LookupEntryNode lookupEntryNode;
    @Child private CompareHashKeysNode compareHashKeysNode = new CompareHashKeysNode();
    @Child private FreezeHashKeyIfNeededNode freezeHashKeyIfNeededNode = FreezeHashKeyIfNeededNodeGen.create(null, null);

    protected final boolean onlyIfAbsent;

    public static SetNode create(boolean onlyIfAbsent) {
        return SetNodeGen.create(onlyIfAbsent, null, null, null, null);
    }

    public SetNode(boolean onlyIfAbsent) {
        this.onlyIfAbsent = onlyIfAbsent;
    }

    public abstract Object executeSet(VirtualFrame frame, DynamicObject hash, Object key, Object value, boolean byIdentity);

    @Specialization(guards = "isNullHash(hash)")
    public Object setNull(VirtualFrame frame, DynamicObject hash, Object originalKey, Object value, boolean byIdentity,
                    @Cached("createBinaryProfile()") ConditionProfile byIdentityProfile) {
        assert HashOperations.verifyStore(getContext(), hash);
        boolean compareByIdentity = byIdentityProfile.profile(byIdentity);
        final Object key = freezeHashKeyIfNeededNode.executeFreezeIfNeeded(frame, originalKey, compareByIdentity);

        final int hashed = hashNode.hash(frame, key, compareByIdentity);

        Object store = PackedArrayStrategy.createStore(getContext(), hashed, key, value);
        assert HashOperations.verifyStore(getContext(), store, 1, null, null);
        Layouts.HASH.setStore(hash, store);
        Layouts.HASH.setSize(hash, 1);
        Layouts.HASH.setFirstInSequence(hash, null);
        Layouts.HASH.setLastInSequence(hash, null);

        assert HashOperations.verifyStore(getContext(), hash);
        return value;
    }

    @ExplodeLoop
    @Specialization(guards = "isPackedHash(hash)")
    public Object setPackedArray(VirtualFrame frame, DynamicObject hash, Object originalKey, Object value, boolean byIdentity,
                    @Cached("createBinaryProfile()") ConditionProfile byIdentityProfile,
                    @Cached("createBinaryProfile()") ConditionProfile strategyProfile,
                    @Cached("create()") BranchProfile extendProfile) {
        assert HashOperations.verifyStore(getContext(), hash);
        final boolean compareByIdentity = byIdentityProfile.profile(byIdentity);
        final Object key = freezeHashKeyIfNeededNode.executeFreezeIfNeeded(frame, originalKey, compareByIdentity);

        final int hashed = hashNode.hash(frame, key, compareByIdentity);

        final Object[] store = (Object[]) Layouts.HASH.getStore(hash);
        final int size = Layouts.HASH.getSize(hash);

        for (int n = 0; n < getContext().getOptions().HASH_PACKED_ARRAY_MAX; n++) {
            if (n < size) {
                final int otherHashed = PackedArrayStrategy.getHashed(store, n);
                final Object otherKey = PackedArrayStrategy.getKey(store, n);
                if (equalKeys(frame, compareByIdentity, key, hashed, otherKey, otherHashed)) {
                    if (onlyIfAbsent) {
                        return PackedArrayStrategy.getValue(store, n);
                    } else {
                        PackedArrayStrategy.setValue(store, n, value);
                        assert HashOperations.verifyStore(getContext(), hash);
                        return value;
                    }
                }
            }
        }

        extendProfile.enter();

        if (strategyProfile.profile(size + 1 <= getContext().getOptions().HASH_PACKED_ARRAY_MAX)) {
            PackedArrayStrategy.setHashedKeyValue(store, size, hashed, key, value);
            Layouts.HASH.setSize(hash, size + 1);
            return value;
        } else {
            PackedArrayStrategy.promoteToBuckets(getContext(), store, size).apply(hash);
            BucketsStrategy.addNewEntry(getContext(), hash, hashed, key, value);
        }

        assert HashOperations.verifyStore(getContext(), hash);

        return value;
    }

    @Specialization(guards = "isBucketHash(hash)")
    public Object setBuckets(VirtualFrame frame, DynamicObject hash, Object originalKey, Object value, boolean byIdentity,
                    @Cached("createBinaryProfile()") ConditionProfile byIdentityProfile,
                    @Cached("createBinaryProfile()") ConditionProfile foundProfile,
                    @Cached("createBinaryProfile()") ConditionProfile bucketCollisionProfile,
                    @Cached("createBinaryProfile()") ConditionProfile appendingProfile,
                    @Cached("createBinaryProfile()") ConditionProfile resizeProfile) {
        assert HashOperations.verifyStore(getContext(), hash);
        final boolean compareByIdentity = byIdentityProfile.profile(byIdentity);
        final Object key = freezeHashKeyIfNeededNode.executeFreezeIfNeeded(frame, originalKey, compareByIdentity);

        final HashLookupResult result = lookup(frame, hash, key);
        final Entry entry = result.getEntry();

        if (foundProfile.profile(entry == null)) {
            final Entry[] entries = (Entry[]) Layouts.HASH.getStore(hash);

            final Entry newEntry = new Entry(result.getHashed(), key, value);

            if (bucketCollisionProfile.profile(result.getPreviousEntry() == null)) {
                entries[result.getIndex()] = newEntry;
            } else {
                result.getPreviousEntry().setNextInLookup(newEntry);
            }

            final Entry lastInSequence = Layouts.HASH.getLastInSequence(hash);

            if (appendingProfile.profile(lastInSequence == null)) {
                Layouts.HASH.setFirstInSequence(hash, newEntry);
            } else {
                lastInSequence.setNextInSequence(newEntry);
                newEntry.setPreviousInSequence(lastInSequence);
            }

            Layouts.HASH.setLastInSequence(hash, newEntry);

            final int newSize = Layouts.HASH.getSize(hash) + 1;

            Layouts.HASH.setSize(hash, newSize);

            // TODO CS 11-May-15 could store the next size for resize instead of doing a float operation each time

            if (resizeProfile.profile(newSize / (double) entries.length > BucketsStrategy.LOAD_FACTOR)) {
                BucketsStrategy.resize(getContext(), hash);
            }
        } else {
            if (onlyIfAbsent) {
                return entry.getValue();
            } else {
                entry.setValue(value);
            }
        }

        assert HashOperations.verifyStore(getContext(), hash);

        return value;
    }

    @Specialization(guards = "isConcurrentHash(hash)")
    public Object setConcurrent(VirtualFrame frame, DynamicObject hash, Object originalKey, Object value, boolean byIdentity,
            @Cached("createBinaryProfile()") ConditionProfile byIdentityProfile,
            @Cached("createBinaryProfile()") ConditionProfile foundProfile,
            @Cached("createBinaryProfile()") ConditionProfile insertionProfile,
            @Cached("createBinaryProfile()") ConditionProfile dirtyProfile,
            @Cached("createBinaryProfile()") ConditionProfile resizeProfile,
            @Cached("new()") ConcurrentLookupEntryNode lookupEntryNode,
            @Cached("create()") WriteBarrierNode writeBarrierNode,
            @Cached("create(onlyIfAbsent)") SetNode retrySetNode,
            @Cached("create()") GetLayoutLockAccessorNode getAccessorNode,
            @Cached("create()") LayoutLockStartWriteNode startWriteNode,
            @Cached("create()") LayoutLockStartLayoutChangeNode startLayoutChangeNode,
            @Cached("create()") LayoutLockFinishLayoutChangeNode finishLayoutChangeNode) {
        assert HashOperations.verifyStore(getContext(), hash);
        final boolean compareByIdentity = byIdentityProfile.profile(byIdentity);
        final Object key = freezeHashKeyIfNeededNode.executeFreezeIfNeeded(frame, originalKey, compareByIdentity);

        final HashLookupResult result = lookupEntryNode.lookup(frame, hash, key);
        final Entry entry = result.getEntry();

        if (foundProfile.profile(entry == null)) {
            writeBarrierNode.executeWriteBarrier(value);

            final Entry firstEntry = result.getPreviousEntry();
            final Entry sentinelLast = Layouts.HASH.getLastInSequence(hash);
            final Entry newEntry = new Entry(result.getHashed(), key, value);
            newEntry.setNextInLookup(firstEntry);
            newEntry.setNextInSequence(sentinelLast);

            final LayoutLock.Accessor accessor = getAccessorNode.executeGetAccessor(hash);
            boolean success;
            startWriteNode.executeStartWrite(accessor);
            try {
                final AtomicReferenceArray<Entry> entries = ConcurrentHash.getStore(hash).getBuckets();
                success = entries.compareAndSet(result.getIndex(), firstEntry, newEntry);
            } finally {
                accessor.finishWrite();
            }
            if (!insertionProfile.profile(success)) {
                // An entry got inserted in this bucket concurrently
                // TODO: should avoid recursing too much
                return retrySetNode.executeSet(frame, hash, key, value, compareByIdentity);
            }

            // TODO: is ordering OK here?
            Entry lastInSequence;
            do {
                lastInSequence = sentinelLast.getPreviousInSequence();
                newEntry.setPreviousInSequence(lastInSequence);
            } while (!lastInSequence.compareAndSetNextInSequence(sentinelLast, newEntry));

            if (!sentinelLast.compareAndSetPreviousInSequence(lastInSequence, newEntry)) {
                assert false; // TODO
            }

            int size;
            while (!ConcurrentHash.compareAndSetSize(hash, size = ConcurrentHash.getSize(hash), size + 1)) {
            }
            final int newSize = size + 1;

            boolean resize;
            while (true) {
                int bucketsCount = ((ConcurrentHash) Layouts.HASH.getStore(hash)).getBuckets().length();
                resize = newSize * 4 > bucketsCount * 3;
                if (dirtyProfile.profile(accessor.isDirty())) {
                    accessor.resetDirty();
                } else {
                    break;
                }
            }

            if (resizeProfile.profile(resize)) {
                final int threads = startLayoutChangeNode.executeStartLayoutChange(accessor);
                try {
                    // Check again to make sure another thread did not already resized
                    int bucketsCount = ((ConcurrentHash) Layouts.HASH.getStore(hash)).getBuckets().length();
                    if (newSize * 4 > bucketsCount * 3) {
                        ConcurrentBucketsStrategy.resize(getContext(), hash, newSize);
                    }
                } finally {
                    finishLayoutChangeNode.executeFinishLayoutChange(accessor, threads);
                }
            }
        } else {
            if (onlyIfAbsent) {
                assert HashOperations.verifyStore(getContext(), hash);
                return entry.getValue();
            } else {
                writeBarrierNode.executeWriteBarrier(value);
                // No need for write lock as long as we keep existing Entry instances during layout changes
                entry.setValue(value);
            }
        }

        assert HashOperations.verifyStore(getContext(), hash);

        return value;
    }

    private HashLookupResult lookup(VirtualFrame frame, DynamicObject hash, Object key) {
        if (lookupEntryNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            lookupEntryNode = insert(new LookupEntryNode());
        }
        return lookupEntryNode.lookup(frame, hash, key);
    }

    protected boolean equalKeys(VirtualFrame frame, boolean compareByIdentity, Object key, int hashed, Object otherKey, int otherHashed) {
        return compareHashKeysNode.equalKeys(frame, compareByIdentity, key, hashed, otherKey, otherHashed);
    }

}
