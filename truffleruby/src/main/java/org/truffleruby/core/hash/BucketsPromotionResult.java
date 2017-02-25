/*
 * Copyright (c) 2016 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.hash;

import org.truffleruby.Layouts;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.objects.shared.SharedObjects;

import com.oracle.truffle.api.object.DynamicObject;

public class BucketsPromotionResult {

    final Entry[] buckets;
    final int size;
    final Entry firstInSequence;
    final Entry lastInSequence;

    public static BucketsPromotionResult empty() {
        return new BucketsPromotionResult(new Entry[268], 0, null, null);
    }

    public static BucketsPromotionResult fromBucketHash(DynamicObject hash) {
        return new BucketsPromotionResult((Entry[]) Layouts.HASH.getStore(hash), Layouts.HASH.getSize(hash),
                Layouts.HASH.getFirstInSequence(hash), Layouts.HASH.getLastInSequence(hash));
    }

    public BucketsPromotionResult(Entry[] buckets, int size, Entry firstInSequence, Entry lastInSequence) {
        this.buckets = buckets;
        this.size = size;
        this.firstInSequence = firstInSequence;
        this.lastInSequence = lastInSequence;
    }

    public Entry[] getBuckets() {
        return buckets;
    }

    public int getSize() {
        return size;
    }

    public Entry getFirstInSequence() {
        return firstInSequence;
    }

    public Entry getLastInSequence() {
        return lastInSequence;
    }

    public void apply(DynamicObject hash) {
        assert RubyGuards.isRubyHash(hash);
        assert !HashGuards.isConcurrentHash(hash);
        Layouts.HASH.setStore(hash, buckets);
        Layouts.HASH.setSize(hash, size);
        Layouts.HASH.setFirstInSequence(hash, firstInSequence);
        Layouts.HASH.setLastInSequence(hash, lastInSequence);
    }

    public void applyConcurrent(DynamicObject hash) {
        assert SharedObjects.isShared(hash);
        ConcurrentBucketsStrategy.fromBuckets(this, hash);
    }

}
