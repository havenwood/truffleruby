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
public final class Entry {

    private int hashed;
    private final Object key;
    private Object value;

    private Entry nextInLookup;

    private Entry previousInSequence;
    private Entry nextInSequence;

    private static final long NEXT_IN_LOOKUP_OFFSET = UnsafeHolder.getFieldOffset(Entry.class, "nextInLookup");
    private static final long NEXT_IN_SEQ_OFFSET = UnsafeHolder.getFieldOffset(Entry.class, "nextInSequence");

    public Entry(int hashed, Object key, Object value) {
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

    public Entry getNextInLookup() {
        return nextInLookup;
    }

    public void setNextInLookup(Entry nextInLookup) {
        this.nextInLookup = nextInLookup;
    }

    public boolean compareAndSetNextInLookup(Entry old, Entry nextInLookup) {
        return UnsafeHolder.UNSAFE.compareAndSwapObject(this, NEXT_IN_LOOKUP_OFFSET, old, nextInLookup);
    }

    public Entry getPreviousInSequence() {
        return previousInSequence;
    }

    public void setPreviousInSequence(Entry previousInSequence) {
        this.previousInSequence = previousInSequence;
    }

    public Entry getNextInSequence() {
        return nextInSequence;
    }

    public void setNextInSequence(Entry nextInSequence) {
        this.nextInSequence = nextInSequence;
    }

    public boolean compareAndSetNextInSequence(Entry old, Entry nextInSequence) {
        return UnsafeHolder.UNSAFE.compareAndSwapObject(this, NEXT_IN_SEQ_OFFSET, old, nextInSequence);
    }

}
