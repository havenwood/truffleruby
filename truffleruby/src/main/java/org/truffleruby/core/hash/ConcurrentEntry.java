/*
 * Copyright (c) 2014, 2016 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.hash;

import org.truffleruby.core.UnsafeHolder;

/**
 * An entry in the Ruby hash. That is, a container for a key and a value, and a member of two lists - the chain of
 * buckets for a given index, and the chain of entries for the insertion order across the whole hash.
 */
public final class ConcurrentEntry {

    private int hashed;
    private final Object key;
    private volatile Object value;

    private ConcurrentEntry nextInLookup;

    private ConcurrentEntry previousInSequence;
    private ConcurrentEntry nextInSequence;

    private static final long NEXT_IN_LOOKUP_OFFSET = UnsafeHolder.getFieldOffset(ConcurrentEntry.class, "nextInLookup");
    private static final long PREVIOUS_IN_SEQ_OFFSET = UnsafeHolder.getFieldOffset(ConcurrentEntry.class, "previousInSequence");
    private static final long NEXT_IN_SEQ_OFFSET = UnsafeHolder.getFieldOffset(ConcurrentEntry.class, "nextInSequence");

    public ConcurrentEntry(int hashed, Object key, Object value) {
        this.hashed = hashed;
        this.key = key;
        this.value = value;
    }

    public int getHashed() {
        return hashed;
    }

    public void setHashed(int hashed) {
        this.hashed = hashed;
    }

    public Object getKey() {
        return key;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public ConcurrentEntry getNextInLookup() {
        return nextInLookup;
    }

    public void setNextInLookup(ConcurrentEntry nextInLookup) {
        this.nextInLookup = nextInLookup;
    }

    public boolean compareAndSetNextInLookup(ConcurrentEntry old, ConcurrentEntry nextInLookup) {
        return UnsafeHolder.UNSAFE.compareAndSwapObject(this, NEXT_IN_LOOKUP_OFFSET, old, nextInLookup);
    }

    public ConcurrentEntry getPreviousInSequence() {
        return previousInSequence;
    }

    public void setPreviousInSequence(ConcurrentEntry previousInSequence) {
        this.previousInSequence = previousInSequence;
    }

    public boolean compareAndSetPreviousInSequence(ConcurrentEntry old, ConcurrentEntry previousInSequence) {
        return UnsafeHolder.UNSAFE.compareAndSwapObject(this, PREVIOUS_IN_SEQ_OFFSET, old, previousInSequence);
    }

    public ConcurrentEntry getNextInSequence() {
        return nextInSequence;
    }

    public void setNextInSequence(ConcurrentEntry nextInSequence) {
        this.nextInSequence = nextInSequence;
    }

    public boolean compareAndSetNextInSequence(ConcurrentEntry old, ConcurrentEntry nextInSequence) {
        return UnsafeHolder.UNSAFE.compareAndSwapObject(this, NEXT_IN_SEQ_OFFSET, old, nextInSequence);
    }

}
