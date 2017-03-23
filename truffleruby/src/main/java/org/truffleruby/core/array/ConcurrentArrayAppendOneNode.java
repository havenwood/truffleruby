/*
 * Copyright (c) 2015, 2016 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.array;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.ConditionProfile;
import org.truffleruby.Layouts;
import org.truffleruby.core.UnsafeHolder;
import org.truffleruby.core.array.ConcurrentArray.FastAppendArray;
import org.truffleruby.core.array.layout.FastLayoutLock;
import org.truffleruby.core.array.layout.GetThreadStateNode;
import org.truffleruby.core.array.layout.ThreadStateReference;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.objects.shared.WriteBarrier;

@NodeChildren({
        @NodeChild("array"),
        @NodeChild("value"),
})
@ImportStatic(ArrayGuards.class)
public abstract class ConcurrentArrayAppendOneNode extends RubyNode {

    private final ConditionProfile acceptValueInLCProfile = ConditionProfile.createCountingProfile();
    private final ConditionProfile extendInLCProfile = ConditionProfile.createCountingProfile();

    public static ConcurrentArrayAppendOneNode create() {
        return ConcurrentArrayAppendOneNodeGen.create(null, null);
    }

    public abstract DynamicObject executeAppendOne(DynamicObject array, Object value);

    @Specialization(guards = { "strategy.matches(array)", "valueStrategy.specializesFor(value)" }, limit = "ARRAY_STRATEGIES")
    public DynamicObject appendOneSameType(DynamicObject array, Object value,
            @Cached("of(array)") ArrayStrategy strategy,
            @Cached("forValue(value)") ArrayStrategy valueStrategy,
            @Cached("strategy.generalize(valueStrategy)") ArrayStrategy generalizedStrategy,
            @Cached("strategy.createWriteBarrier()") WriteBarrier writeBarrier,
            @Cached("createCountingProfile()") ConditionProfile acceptValueProfile,
            @Cached("createCountingProfile()") ConditionProfile extendProfile,
            @Cached("create()") GetThreadStateNode getThreadStateNode,
            @Cached("createBinaryProfile()") ConditionProfile fastPathProfile,
            @Cached("createBinaryProfile()") ConditionProfile tryLockProfile,
            @Cached("createBinaryProfile()") ConditionProfile waitProfile) {

        writeBarrier.executeWriteBarrier(value);

        // This is fine because FastAppendArray is a leaf strategy and the lock is carried over
        final FastLayoutLock lock = ((FastAppendArray) Layouts.ARRAY.getStore(array)).getLock();
        final ThreadStateReference threadState = getThreadStateNode.executeGetThreadState(array);

        lock.startWrite(threadState, fastPathProfile);
        final int size = ConcurrentArray.getSizeAndIncrement(array); // SIDE EFFECT!
        try {
            if (strategy.matches(array)) {
                final ArrayMirror storeMirror = strategy.newMirror(array);
                if (acceptValueProfile.profile(strategy.accepts(value)) &&
                        extendProfile.profile(size < storeMirror.getLength())) {
                    // Append of the correct type, within capacity
                    appendInBounds(array, storeMirror, size, value);
                    return array;
                }
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                // The array strategy changed between the guard and now
            }
        } finally {
            lock.finishWrite(threadState);
        }

        final long stamp = lock.startLayoutChange(tryLockProfile, waitProfile);
        try {
            if (!strategy.matches(array)) {
                // The array strategy changed between the guard and now
                CompilerDirectives.transferToInterpreterAndInvalidate();
                strategyChanged(array, value, size);
                return array;
            }

            slowPathAppend(array, value, strategy, generalizedStrategy, size);
        } finally {
            lock.finishLayoutChange(stamp);
        }

        return array;
    }

    private void appendInBounds(DynamicObject array, ArrayMirror storeMirror, int index, Object value) {
        storeMirror.set(index, value);
        // UnsafeHolder.UNSAFE.storeFence();
        // ((FastAppendArray) Layouts.ARRAY.getStore(array)).getTags()[index] = true;
    }

    private void slowPathAppend(DynamicObject array, Object value, ArrayStrategy strategy, ArrayStrategy generalizedStrategy, int size) {
        final ArrayMirror currentMirror = strategy.newMirror(array);
        if (acceptValueInLCProfile.profile(strategy.accepts(value))) {
            if (extendInLCProfile.profile(size < currentMirror.getLength())) {
                appendInBounds(array, currentMirror, size, value);
            } else {
                final int capacity = ArrayUtils.capacityForOneMore(getContext(), currentMirror.getLength());
                final ArrayMirror newStoreMirror = currentMirror.copyArrayAndMirror(capacity);
                strategy.setStore(array, newStoreMirror.getArray());
                appendInBounds(array, newStoreMirror, size, value);
            }
        } else if (generalizedStrategy.accepts(value)) {
            // Append forcing a generalization
            final int newSize = size + 1;
            final int oldCapacity = currentMirror.getLength();
            final int newCapacity = newSize > oldCapacity ? ArrayUtils.capacityForOneMore(getContext(), oldCapacity) : oldCapacity;
            final ArrayMirror newStoreMirror = generalizedStrategy.newArray(newCapacity);
            currentMirror.copyTo(newStoreMirror, 0, 0, size);
            generalizedStrategy.setStore(array, newStoreMirror.getArray());
            appendInBounds(array, newStoreMirror, size, value);
        } else {
            // the strategies don't correspond to the array's anymore
            CompilerDirectives.transferToInterpreterAndInvalidate();
            strategyChanged(array, value, size);
        }
    }

    private void strategyChanged(DynamicObject array, Object value, int size) {
        final ArrayStrategy strategy = ArrayStrategy.of(array);
        final ArrayStrategy generalizedStrategy = strategy.generalize(ArrayStrategy.forValue(value));
        slowPathAppend(array, value, strategy, generalizedStrategy, size);
    }

}
