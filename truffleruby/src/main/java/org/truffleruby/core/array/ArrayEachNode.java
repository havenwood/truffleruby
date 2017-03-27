/*
 * Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.array;

import org.truffleruby.Layouts;
import org.truffleruby.builtins.YieldingCoreMethodNode;
import org.truffleruby.core.array.ConcurrentArray.FastLayoutLockArray;
import org.truffleruby.core.array.layout.FastLayoutLock;
import org.truffleruby.core.array.layout.GetThreadStateNode;
import org.truffleruby.core.array.layout.ThreadStateReference;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ImportStatic(ArrayGuards.class)
public abstract class ArrayEachNode extends YieldingCoreMethodNode {

    public static ArrayEachNode create() {
        return ArrayEachNodeFactory.create(null);
    }

    public abstract DynamicObject executeEach(VirtualFrame frame, DynamicObject array, DynamicObject block, int from);

    @Child ArrayReadNormalizedNode readNode = ArrayReadNormalizedNodeGen.create(null, null);

    @Specialization(guards = { "strategy.matches(array)", "!strategy.isConcurrent()" }, limit = "ARRAY_STRATEGIES")
    public Object eachLocal(VirtualFrame frame, DynamicObject array, DynamicObject block, int from,
            @Cached("of(array)") ArrayStrategy strategy) {
        int i = from;
        try {
            for (; i < strategy.getSize(array); i++) {
                yield(frame, block, readNode.executeRead(array, i));
            }
        } finally {
            LoopNode.reportLoopCount(this, i - from);
        }

        return array;
    }

    @Specialization(guards = { "strategy.matches(array)", "isFixedSizeArray(array)" }, limit = "ARRAY_STRATEGIES")
    public DynamicObject eachFixedSize(VirtualFrame frame, DynamicObject array, DynamicObject block, int from,
            @Cached("of(array)") ArrayStrategy strategy,
            @Cached("createCountingProfile()") ConditionProfile strategyMatchesProfile,
            @Cached("create()") ArrayEachNode recurNode) {
        int i = from;
        try {
            for (; i < strategy.getSize(array); i++) {
                if (strategyMatchesProfile.profile(strategy.matches(array))) {
                    yield(frame, block, readNode.executeRead(array, i));
                } else {
                    return recurNode.executeEach(frame, array, block, i);
                }
            }
        } finally {
            LoopNode.reportLoopCount(this, i - from);
        }

        return array;
    }

    @Specialization(guards = { "strategy.matches(array)", "isFastLayoutLockArray(array)" }, limit = "ARRAY_STRATEGIES")
    public DynamicObject eachFLL(VirtualFrame frame, DynamicObject array, DynamicObject block, int from,
            @Cached("of(array)") ArrayStrategy strategy,
            @Cached("create()") GetThreadStateNode getThreadStateNode,
            @Cached("createBinaryProfile()") ConditionProfile fastPathProfile,
            @Cached("createBinaryProfile()") ConditionProfile strategyMatchesProfile,
            @Cached("create()") ArrayEachNode recurNode) {

        final ThreadStateReference threadState = getThreadStateNode.executeGetThreadState(array);
        final FastLayoutLock lock = ((FastLayoutLockArray) Layouts.ARRAY.getStore(array)).getLock();

        int i = 0;
        try {
            while (true) { // retry test if layout changed
                int size = strategy.getSize(array);
                if (lock.finishRead(threadState, fastPathProfile)) {
                    if (i >= size) {
                        break;
                    }

                    final ArrayMirror store = strategy.newMirror(array);
                    final Object value = store.get(i);
                    if (lock.finishRead(threadState, fastPathProfile)) {
                        yield(frame, block, value);
                        i++;
                        continue;
                    }
                }

                if (!strategyMatchesProfile.profile(strategy.matches(array))) {
                    return recurNode.executeEach(frame, array, block, i);
                }
            }
        } finally {
            LoopNode.reportLoopCount(this, i - from);
        }

        return array;
    }

}
