/*
 * Copyright (c) 2016 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.array;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.object.DynamicObject;

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.StampedLock;

import org.truffleruby.Layouts;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.core.array.ConcurrentArray.CustomLockArray;
import org.truffleruby.core.array.ConcurrentArray.FixedSizeArray;
import org.truffleruby.core.array.ConcurrentArray.LayoutLockArray;
import org.truffleruby.core.array.ConcurrentArray.ReentrantLockArray;
import org.truffleruby.core.array.ConcurrentArray.SynchronizedArray;
import org.truffleruby.core.array.layout.MyBiasedLock;
import org.truffleruby.core.array.ConcurrentArray.StampedLockArray;
import org.truffleruby.language.objects.shared.NoWriteBarrierNode;
import org.truffleruby.language.objects.shared.SharedObjects;
import org.truffleruby.language.objects.shared.WriteBarrier;
import org.truffleruby.language.objects.shared.WriteBarrierNode;

public abstract class ArrayStrategy {

    // ArrayStrategy interface

    protected Class<?> type() {
        throw unsupported();
    }

    public boolean canStore(Class<?> type) {
        throw unsupported();
    }

    public abstract boolean accepts(Object value);

    public boolean specializesFor(Object value) {
        throw unsupported();
    }

    public boolean isDefaultValue(Object value) {
        throw unsupported();
    }

    public final boolean matches(DynamicObject array) {
        return matchesStore(Layouts.ARRAY.getStore(array));
    }

    protected abstract boolean matchesStore(Object store);

    public int getSize(DynamicObject array) {
        return Layouts.ARRAY.getSize(array);
    }

    public boolean isConcurrent() {
        return this instanceof ConcurrentArrayStrategy;
    }

    public abstract ArrayMirror newArray(int size);

    public final ArrayMirror newMirror(DynamicObject array) {
        return newMirrorFromStore(Layouts.ARRAY.getStore(array));
    }

    protected ArrayMirror newMirrorFromStore(Object store) {
        throw unsupported();
    }

    public void setStore(DynamicObject array, Object store) {
        assert !(store instanceof ArrayMirror);
        assert !SharedObjects.isShared(array);
        Layouts.ARRAY.setStore(array, store);
    }

    public void setStoreAndSize(DynamicObject array, Object store, int size) {
        setStore(array, store);
        ArrayHelpers.setSize(array, size);
    }

    @Override
    public abstract String toString();

    public ArrayStrategy generalize(ArrayStrategy other) {
        CompilerAsserts.neverPartOfCompilation();
        if (other == this) {
            return this;
        }

        if (other instanceof ConcurrentArrayStrategy) {
            // local.generalize(shared) => local
            other = ((ConcurrentArrayStrategy) other).typeStrategy;
        }

        if (other instanceof NullArrayStrategy) {
            return this;
        } else if (this instanceof NullArrayStrategy) {
            return other;
        }

        for (ArrayStrategy generalized : TYPE_STRATEGIES) {
            if (generalized.canStore(type()) && generalized.canStore(other.type())) {
                return generalized;
            }
        }
        throw unsupported();
    }

    public ArrayStrategy generalizeNew(ArrayStrategy other) {
        return generalize(other);
    }

    public ArrayStrategy generalizeFor(Object value) {
        return generalize(ArrayStrategy.forValue(value));
    }

    public WriteBarrier createWriteBarrier() {
        return new NoWriteBarrierNode();
    }

    // Helpers

    protected RuntimeException unsupported() {
        return new UnsupportedOperationException(toString());
    }

    public static final ArrayStrategy[] TYPE_STRATEGIES = {
            IntArrayStrategy.INSTANCE,
            LongArrayStrategy.INSTANCE,
            DoubleArrayStrategy.INSTANCE,
            ObjectArrayStrategy.INSTANCE
    };

    private static ArrayStrategy ofStore(Object store) {
        CompilerAsserts.neverPartOfCompilation();

        if (store == null) {
            return NullArrayStrategy.INSTANCE;
        } else if (store instanceof int[]) {
            return IntArrayStrategy.INSTANCE;
        } else if (store instanceof long[]) {
            return LongArrayStrategy.INSTANCE;
        } else if (store instanceof double[]) {
            return DoubleArrayStrategy.INSTANCE;
        } else if (store.getClass() == Object[].class) {
            return ObjectArrayStrategy.INSTANCE;
        } else {
            throw new UnsupportedOperationException(store.getClass().getName());
        }
    }

    public static ArrayStrategy of(DynamicObject array) {
        ArrayStrategy strategy = ofImpl(array);
        assert strategy instanceof ConcurrentArrayStrategy == SharedObjects.isShared(array);
        return strategy;
    }

    private static ArrayStrategy ofImpl(DynamicObject array) {
        CompilerAsserts.neverPartOfCompilation();

        if (!RubyGuards.isRubyArray(array)) {
            return FallbackArrayStrategy.INSTANCE;
        }

        if (ArrayGuards.isConcurrentArray(array)) {
            final ConcurrentArray concurrentArray = (ConcurrentArray) Layouts.ARRAY.getStore(array);
            if (concurrentArray instanceof FixedSizeArray) {
                return new FixedSizeSafepointArrayStrategy(ofStore(concurrentArray.getStore()));
            } else if (concurrentArray instanceof SynchronizedArray) {
                return new SynchronizedArrayStrategy(ofStore(concurrentArray.getStore()));
            } else if (concurrentArray instanceof ReentrantLockArray) {
                return new ReentrantLockArrayStrategy(ofStore(concurrentArray.getStore()));
            } else if (concurrentArray instanceof CustomLockArray) {
                return new CustomLockArrayStrategy(ofStore(concurrentArray.getStore()));
            } else if (concurrentArray instanceof StampedLockArray) {
                return new StampedLockArrayStrategy(ofStore(concurrentArray.getStore()));
            } else if (concurrentArray instanceof LayoutLockArray) {
                return new LayoutLockArrayStrategy(ofStore(concurrentArray.getStore()));
            } else {
                throw new UnsupportedOperationException(concurrentArray.getStore().getClass().getName());
            }
        } else {
            return ofStore(Layouts.ARRAY.getStore(array));
        }
    }

    public static ArrayStrategy forValue(Object value) {
        CompilerAsserts.neverPartOfCompilation();
        if (value instanceof Integer) {
            return IntArrayStrategy.INSTANCE;
        } else if (value instanceof Long) {
            return LongArrayStrategy.INSTANCE;
        } else if (value instanceof Double) {
            return DoubleArrayStrategy.INSTANCE;
        } else {
            return ObjectArrayStrategy.INSTANCE;
        }
    }

    // Type strategies (int, long, double, Object)

    private static class IntArrayStrategy extends ArrayStrategy {

        static final ArrayStrategy INSTANCE = new IntArrayStrategy();

        @Override
        public Class<?> type() {
            return Integer.class;
        }

        @Override
        public boolean canStore(Class<?> type) {
            return type == Integer.class;
        }

        @Override
        public boolean accepts(Object value) {
            return value instanceof Integer;
        }

        @Override
        public boolean specializesFor(Object value) {
            return value instanceof Integer;
        }

        @Override
        public boolean isDefaultValue(Object value) {
            return (int) value == 0;
        }

        @Override
        public boolean matchesStore(Object store) {
            return store instanceof int[];
        }

        @Override
        public ArrayStrategy generalize(ArrayStrategy other) {
            CompilerAsserts.neverPartOfCompilation();
            if (other == this) {
                return this;
            } else if (other == LongArrayStrategy.INSTANCE) {
                return LongArrayStrategy.INSTANCE;
            } else {
                return IntToObjectGeneralizationArrayStrategy.INSTANCE;
            }
        }

        @Override
        public ArrayMirror newArray(int size) {
            return new IntegerArrayMirror(new int[size]);
        }

        @Override
        protected ArrayMirror newMirrorFromStore(Object store) {
            return new IntegerArrayMirror((int[]) store);
        }

        @Override
        public String toString() {
            return "int[]";
        }

    }

    private static class LongArrayStrategy extends ArrayStrategy {

        static final ArrayStrategy INSTANCE = new LongArrayStrategy();

        @Override
        public Class<?> type() {
            return Long.class;
        }

        @Override
        public boolean canStore(Class<?> type) {
            return type == Long.class || type == Integer.class;
        }

        @Override
        public boolean accepts(Object value) {
            return value instanceof Long;
        }

        @Override
        public boolean specializesFor(Object value) {
            return value instanceof Long;
        }

        @Override
        public boolean isDefaultValue(Object value) {
            return (long) value == 0L;
        }

        @Override
        public boolean matchesStore(Object store) {
            return store instanceof long[];
        }

        @Override
        public ArrayMirror newArray(int size) {
            return new LongArrayMirror(new long[size]);
        }

        @Override
        public ArrayMirror newMirrorFromStore(Object store) {
            return new LongArrayMirror((long[]) store);
        }

        @Override
        public String toString() {
            return "long[]";
        }

    }

    private static class DoubleArrayStrategy extends ArrayStrategy {

        static final ArrayStrategy INSTANCE = new DoubleArrayStrategy();

        @Override
        public Class<?> type() {
            return Double.class;
        }

        @Override
        public boolean canStore(Class<?> type) {
            return type == Double.class;
        }

        @Override
        public boolean accepts(Object value) {
            return value instanceof Double;
        }

        @Override
        public boolean specializesFor(Object value) {
            return value instanceof Double;
        }

        @Override
        public boolean isDefaultValue(Object value) {
            return (double) value == 0.0;
        }

        @Override
        public boolean matchesStore(Object store) {
            return store instanceof double[];
        }

        @Override
        public ArrayMirror newArray(int size) {
            return new DoubleArrayMirror(new double[size]);
        }

        @Override
        public ArrayMirror newMirrorFromStore(Object store) {
            return new DoubleArrayMirror((double[]) store);
        }

        @Override
        public String toString() {
            return "double[]";
        }

    }

    private static class ObjectArrayStrategy extends ArrayStrategy {

        static final ArrayStrategy INSTANCE = new ObjectArrayStrategy();

        @Override
        public Class<?> type() {
            return Object.class;
        }

        @Override
        public boolean canStore(Class<?> type) {
            return true;
        }

        @Override
        public boolean accepts(Object value) {
            return true;
        }

        @Override
        public boolean specializesFor(Object value) {
            return !(value instanceof Integer) && !(value instanceof Long) && !(value instanceof Double);
        }

        @Override
        public boolean isDefaultValue(Object value) {
            return value == null;
        }

        @Override
        public boolean matchesStore(Object store) {
            return store != null && store.getClass() == Object[].class;
        }

        @Override
        public ArrayMirror newArray(int size) {
            return new ObjectArrayMirror(new Object[size]);
        }

        @Override
        public ArrayMirror newMirrorFromStore(Object store) {
            return new ObjectArrayMirror((Object[]) store);
        }

        @Override
        public String toString() {
            return "Object[]";
        }

    }

    /** Object[] not accepting long */
    private static class IntToObjectGeneralizationArrayStrategy extends ArrayStrategy {

        static final ArrayStrategy INSTANCE = new IntToObjectGeneralizationArrayStrategy();

        @Override
        public boolean accepts(Object value) {
            return !(value instanceof Long);
        }

        @Override
        public boolean matchesStore(Object store) {
            return store != null && store.getClass() == Object[].class;
        }

        @Override
        public ArrayMirror newArray(int size) {
            return new ObjectArrayMirror(new Object[size]);
        }

        @Override
        public ArrayMirror newMirrorFromStore(Object store) {
            return new ObjectArrayMirror((Object[]) store);
        }

        @Override
        public String toString() {
            return "Object[] (not accepting long)";
        }

    }

    // Null/empty strategy

    private static class NullArrayStrategy extends ArrayStrategy {

        static final ArrayStrategy INSTANCE = new NullArrayStrategy();

        @Override
        public Class<?> type() {
            throw unsupported();
        }

        @Override
        public boolean canStore(Class<?> type) {
            return type == null;
        }

        @Override
        public boolean accepts(Object value) {
            return false;
        }

        @Override
        public boolean matchesStore(Object store) {
            return store == null;
        }

        @Override
        public int getSize(DynamicObject array) {
            return 0;
        }

        @Override
        public ArrayMirror newArray(int size) {
            assert size == 0;
            return EmptyArrayMirror.INSTANCE;
        }

        @Override
        protected ArrayMirror newMirrorFromStore(Object store) {
            return EmptyArrayMirror.INSTANCE;
        }

        @Override
        public String toString() {
            return "null";
        }

    }

    // Concurrent strategies

    private static abstract class ConcurrentArrayStrategy extends ArrayStrategy {

        protected final ArrayStrategy typeStrategy;

        public ConcurrentArrayStrategy(ArrayStrategy typeStrategy) {
            this.typeStrategy = typeStrategy;
        }

        @Override
        public Class<?> type() {
            return typeStrategy.type();
        }

        @Override
        public boolean canStore(Class<?> type) {
            return typeStrategy.canStore(type);
        }

        @Override
        public boolean accepts(Object value) {
            return typeStrategy.accepts(value);
        }

        @Override
        public boolean specializesFor(Object value) {
            return typeStrategy.specializesFor(value);
        }

        @Override
        public boolean isDefaultValue(Object value) {
            return typeStrategy.isDefaultValue(value);
        }

        @Override
        public void setStore(DynamicObject array, Object store) {
            assert !(store instanceof ArrayMirror);
            assert SharedObjects.isShared(array);
            Layouts.ARRAY.setStore(array, wrap(array, store));
        }

        protected ArrayStrategy generalizeTypeStrategy(ArrayStrategy other) {
            final ArrayStrategy otherTypeStrategy;
            if (other instanceof ConcurrentArrayStrategy) {
                otherTypeStrategy = ((ConcurrentArrayStrategy) other).typeStrategy;
            } else {
                otherTypeStrategy = other;
            }

            ArrayStrategy generalizedTypeStrategy = typeStrategy.generalize(otherTypeStrategy);

            return generalizedTypeStrategy;
        }

        @Override
        public abstract ArrayStrategy generalize(ArrayStrategy other);

        @Override
        public ArrayStrategy generalizeNew(ArrayStrategy other) {
            return generalizeTypeStrategy(other);
        }

        @Override
        public ArrayMirror newArray(int size) {
            // Creating a new array, by definition local and no need for synchronization
            return typeStrategy.newArray(size);
        }

        @Override
        public ArrayMirror newMirrorFromStore(Object store) {
            return typeStrategy.newMirrorFromStore(unwrap(store));
        }

        @Override
        public WriteBarrier createWriteBarrier() {
            return WriteBarrierNode.create();
        }

        protected abstract Object wrap(DynamicObject array, Object store);

        protected abstract Object unwrap(Object store);

    }

    private static class FixedSizeSafepointArrayStrategy extends ConcurrentArrayStrategy {

        public FixedSizeSafepointArrayStrategy(ArrayStrategy typeStrategy) {
            super(typeStrategy);
        }

        @Override
        protected Object wrap(DynamicObject array, Object store) {
            return new FixedSizeArray(store);
        }

        @Override
        protected Object unwrap(Object store) {
            final FixedSizeArray fixedSizeArray = (FixedSizeArray) store;
            return fixedSizeArray.getStore();
        }

        @Override
        public boolean matchesStore(Object store) {
            return store instanceof FixedSizeArray && typeStrategy.matchesStore(((FixedSizeArray) store).getStore());
        }

        @Override
        public ArrayStrategy generalize(ArrayStrategy other) {
            ArrayStrategy generalizedTypeStrategy = generalizeTypeStrategy(other);

            if (other instanceof SynchronizedArrayStrategy) {
                return new SynchronizedArrayStrategy(generalizedTypeStrategy);
            } else {
                return new FixedSizeSafepointArrayStrategy(generalizedTypeStrategy);
            }
        }

        @Override
        public String toString() {
            return "FixedSize(" + typeStrategy + ")";
        }

    }

    private static class SynchronizedArrayStrategy extends ConcurrentArrayStrategy {

        public SynchronizedArrayStrategy(ArrayStrategy typeStrategy) {
            super(typeStrategy);
        }

        @Override
        protected Object wrap(DynamicObject array, Object store) {
            return new SynchronizedArray(store);
        }

        @Override
        protected Object unwrap(Object store) {
            final SynchronizedArray synchronizedArray = (SynchronizedArray) store;
            return synchronizedArray.getStore();
        }

        @Override
        public boolean matchesStore(Object store) {
            return store instanceof SynchronizedArray && typeStrategy.matchesStore(((SynchronizedArray) store).getStore());
        }

        @Override
        public ArrayStrategy generalize(ArrayStrategy other) {
            ArrayStrategy generalizedTypeStrategy = generalizeTypeStrategy(other);

            return new SynchronizedArrayStrategy(generalizedTypeStrategy);
        }

        @Override
        public String toString() {
            return "Synchronized(" + typeStrategy + ")";
        }

    }

    private static class ReentrantLockArrayStrategy extends ConcurrentArrayStrategy {

        public ReentrantLockArrayStrategy(ArrayStrategy typeStrategy) {
            super(typeStrategy);
        }

        @Override
        protected Object wrap(DynamicObject array, Object store) {
            final ReentrantLock lock = ((ReentrantLockArray) Layouts.ARRAY.getStore(array)).getLock();
            return new ReentrantLockArray(store, lock);
        }

        @Override
        protected Object unwrap(Object store) {
            final ReentrantLockArray reentrantLockArray = (ReentrantLockArray) store;
            return reentrantLockArray.getStore();
        }

        @Override
        public boolean matchesStore(Object store) {
            return store instanceof ReentrantLockArray && typeStrategy.matchesStore(((ReentrantLockArray) store).getStore());
        }

        @Override
        public ArrayStrategy generalize(ArrayStrategy other) {
            ArrayStrategy generalizedTypeStrategy = generalizeTypeStrategy(other);

            return new ReentrantLockArrayStrategy(generalizedTypeStrategy);
        }

        @Override
        public String toString() {
            return "ReentrantLock(" + typeStrategy + ")";
        }

    }

    private static class CustomLockArrayStrategy extends ConcurrentArrayStrategy {

        public CustomLockArrayStrategy(ArrayStrategy typeStrategy) {
            super(typeStrategy);
        }

        @Override
        protected Object wrap(DynamicObject array, Object store) {
            final MyBiasedLock lock = ((CustomLockArray) Layouts.ARRAY.getStore(array)).getLock();
            return new CustomLockArray(store, lock);
        }

        @Override
        protected Object unwrap(Object store) {
            final CustomLockArray customLockArray = (CustomLockArray) store;
            return customLockArray.getStore();
        }

        @Override
        public boolean matchesStore(Object store) {
            return store instanceof CustomLockArray && typeStrategy.matchesStore(((CustomLockArray) store).getStore());
        }

        @Override
        public ArrayStrategy generalize(ArrayStrategy other) {
            ArrayStrategy generalizedTypeStrategy = generalizeTypeStrategy(other);

            return new CustomLockArrayStrategy(generalizedTypeStrategy);
        }

        @Override
        public String toString() {
            return "CustomLock(" + typeStrategy + ")";
        }

    }

    private static class StampedLockArrayStrategy extends ConcurrentArrayStrategy {

        public StampedLockArrayStrategy(ArrayStrategy typeStrategy) {
            super(typeStrategy);
        }

        @Override
        protected Object wrap(DynamicObject array, Object store) {
            final StampedLock lock = ((StampedLockArray) Layouts.ARRAY.getStore(array)).getLock();
            return new StampedLockArray(store, lock);
        }

        @Override
        protected Object unwrap(Object store) {
            final StampedLockArray stampedLockArray = (StampedLockArray) store;
            return stampedLockArray.getStore();
        }

        @Override
        public boolean matchesStore(Object store) {
            return store instanceof StampedLockArray && typeStrategy.matchesStore(((StampedLockArray) store).getStore());
        }

        @Override
        public ArrayStrategy generalize(ArrayStrategy other) {
            ArrayStrategy generalizedTypeStrategy = generalizeTypeStrategy(other);

            return new StampedLockArrayStrategy(generalizedTypeStrategy);
        }

        @Override
        public String toString() {
            return "StampedLock(" + typeStrategy + ")";
        }

    }

    private static class LayoutLockArrayStrategy extends ConcurrentArrayStrategy {

        public LayoutLockArrayStrategy(ArrayStrategy typeStrategy) {
            super(typeStrategy);
        }

        @Override
        protected Object wrap(DynamicObject array, Object store) {
            return new LayoutLockArray(store);
        }

        @Override
        protected Object unwrap(Object store) {
            final LayoutLockArray layoutLockArray = (LayoutLockArray) store;
            return layoutLockArray.getStore();
        }

        @Override
        public boolean matchesStore(Object store) {
            return store instanceof LayoutLockArray && typeStrategy.matchesStore(((LayoutLockArray) store).getStore());
        }

        @Override
        public ArrayStrategy generalize(ArrayStrategy other) {
            ArrayStrategy generalizedTypeStrategy = generalizeTypeStrategy(other);

            return new LayoutLockArrayStrategy(generalizedTypeStrategy);
        }

        @Override
        public String toString() {
            return "LayoutLock(" + typeStrategy + ")";
        }

    }

    // Fallback strategy

    private static class FallbackArrayStrategy extends ArrayStrategy {

        static final ArrayStrategy INSTANCE = new FallbackArrayStrategy();

        @Override
        public boolean accepts(Object value) {
            return false;
        }

        @Override
        public boolean matchesStore(Object store) {
            return false;
        }

        @Override
        public ArrayStrategy generalize(ArrayStrategy other) {
            return other;
        }

        @Override
        public ArrayMirror newArray(int size) {
            throw unsupported();
        }

        @Override
        public ArrayMirror newMirrorFromStore(Object store) {
            throw unsupported();
        }

        @Override
        public String toString() {
            return "fallback";
        }

    }

}
