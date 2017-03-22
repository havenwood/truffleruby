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

import static org.truffleruby.core.array.ArrayHelpers.setSize;

@NodeChildren({
        @NodeChild("array"),
        @NodeChild("value"),
})
@ImportStatic(ArrayGuards.class)
public abstract class ConcurrentArrayAppendOneNode extends RubyNode {

    public static ConcurrentArrayAppendOneNode create() {
        return ConcurrentArrayAppendOneNodeGen.create(null, null);
    }

    public abstract DynamicObject executeAppendOne(DynamicObject array, Object value);

    // Append of the correct type

    // TODO: guards are not protected by the lock and strategy could change between checking and acting!
    @Specialization(guards = { "strategy.matches(array)", "strategy.accepts(value)" }, limit = "ARRAY_STRATEGIES")
    public DynamicObject appendOneSameType(DynamicObject array, Object value,
            @Cached("of(array)") ArrayStrategy strategy,
            @Cached("strategy.createWriteBarrier()") WriteBarrier writeBarrier,
            @Cached("createCountingProfile()") ConditionProfile extendProfile,
            @Cached("create()") GetThreadStateNode getThreadStateNode,
            @Cached("createBinaryProfile()") ConditionProfile fastPathProfile,
            @Cached("createBinaryProfile()") ConditionProfile tryLockProfile,
            @Cached("createBinaryProfile()") ConditionProfile waitProfile,
            @Cached("createCountingProfile()") ConditionProfile extendInLCProfile) {

        writeBarrier.executeWriteBarrier(value);

        final ThreadStateReference threadState = getThreadStateNode.executeGetThreadState(array);
        final FastAppendArray fastAppendArray = (FastAppendArray) Layouts.ARRAY.getStore(array);
        final FastLayoutLock lock = fastAppendArray.getLock();

        final int size = ConcurrentArray.getSizeAndIncrement(array);
        lock.startWrite(threadState, fastPathProfile);
        try {
            final ArrayMirror storeMirror = strategy.newMirror(array);
            if (extendProfile.profile(size < storeMirror.getLength())) {
                appendInBounds(fastAppendArray, storeMirror, size, value);
                return array;
            }
        } finally {
            lock.finishWrite(threadState);
        }

        final long stamp = lock.startLayoutChange(tryLockProfile, waitProfile);
        try {
            final ArrayMirror storeMirror = strategy.newMirror(array);
            if (extendInLCProfile.profile(size < storeMirror.getLength())) {
                appendInBounds(fastAppendArray, storeMirror, size, value);
            } else {
                final int capacity = ArrayUtils.capacityForOneMore(getContext(), storeMirror.getLength());
                final ArrayMirror newStoreMirror = storeMirror.copyArrayAndMirror(capacity);
                newStoreMirror.set(size, value);
                strategy.setStore(array, newStoreMirror.getArray());
                final boolean[] newTags = ArrayUtils.grow(fastAppendArray.getTags(), capacity);
                newTags[size] = true;
                fastAppendArray.setTags(newTags);
            }
        } finally {
            lock.finishLayoutChange(stamp);
        }

        return array;
    }

    private void appendInBounds(FastAppendArray fastAppendArray, ArrayMirror storeMirror, int index, Object value) {
        storeMirror.set(index, value);
        UnsafeHolder.UNSAFE.storeFence();
        fastAppendArray.getTags()[index] = true;
    }

    // Append forcing a generalization

    // TODO
    @Specialization(guards = {
            "strategy.matches(array)", "!strategy.accepts(value)", "valueStrategy.specializesFor(value)",
    }, limit = "ARRAY_STRATEGIES")
    public DynamicObject appendOneGeneralize(DynamicObject array, Object value,
            @Cached("of(array)") ArrayStrategy strategy,
            @Cached("forValue(value)") ArrayStrategy valueStrategy,
            @Cached("strategy.generalize(valueStrategy)") ArrayStrategy generalizedStrategy,
            @Cached("generalizedStrategy.createWriteBarrier()") WriteBarrier writeBarrier) {
        final int oldSize = strategy.getSize(array);
        final int newSize = oldSize + 1;
        final ArrayMirror currentMirror = strategy.newMirror(array);
        final int oldCapacity = currentMirror.getLength();
        final int newCapacity = newSize > oldCapacity ? ArrayUtils.capacityForOneMore(getContext(), oldCapacity) : oldCapacity;
        final ArrayMirror storeMirror = generalizedStrategy.newArray(newCapacity);
        currentMirror.copyTo(storeMirror, 0, 0, oldSize);
        writeBarrier.executeWriteBarrier(value);
        storeMirror.set(oldSize, value);
        generalizedStrategy.setStore(array, storeMirror.getArray());
        setSize(array, newSize);
        return array;
    }

}
