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

import org.truffleruby.Layouts;

import com.oracle.truffle.api.object.DynamicObject;

public abstract class ConcurrentBucketsStrategy {

    public static Iterator<KeyValue> iterateKeyValues(DynamicObject hash) {
        final Entry firstInSequence = Layouts.HASH.getFirstInSequence(hash);
        final Entry lastInSequence = Layouts.HASH.getLastInSequence(hash);

        return new Iterator<KeyValue>() {

            private Entry entry = firstInSequence.getNextInSequence();

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
