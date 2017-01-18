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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import org.truffleruby.builtins.CoreClass;
import org.truffleruby.builtins.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.language.objects.shared.SharedObjects;
import org.truffleruby.core.array.ConcurrentArray.StampedLockArray;
import org.truffleruby.Layouts;
import java.util.concurrent.locks.StampedLock;

import static org.truffleruby.core.array.ArrayHelpers.getSize;
import static org.truffleruby.core.array.ArrayHelpers.getStore;

@CoreClass("Truffle::Array")
public class TruffleArrayNodes {

    @CoreMethod(names = "steal_storage", onSingleton = true, required = 2)
    @ImportStatic(ArrayGuards.class)
    public abstract static class StealStorageNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "array == other")
        public DynamicObject stealStorageNoOp(DynamicObject array, DynamicObject other) {
            return array;
        }

        @Specialization(guards = {"array != other", "strategy.matches(array)", "otherStrategy.matches(other)"}, limit = "ARRAY_STRATEGIES")
        public DynamicObject stealStorage(DynamicObject array, DynamicObject other,
                        @Cached("of(array)") ArrayStrategy strategy,
                        @Cached("of(other)") ArrayStrategy otherStrategy) {
            final int size = getSize(other);
            final Object store = getStore(other);
            strategy.setStoreAndSize(array, store, size);
            otherStrategy.setStoreAndSize(other, null, 0);

            return array;
        }

    }

    @CoreMethod(names = "set_strategy", onSingleton = true, required = 2)
    @ImportStatic(ArrayGuards.class)
    public abstract static class SetStrategyNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization(guards = "isRubySymbol(strategy)")
        public DynamicObject setStrategy(DynamicObject array, DynamicObject strategy) {
            if (!(SharedObjects.isShared(array))) {
                throw new UnsupportedOperationException();
            }
            final Thread thread = Thread.currentThread();
            getContext().getSafepointManager().pauseAllThreadsAndExecute(this, false, (rubyThread, currentNode) -> {
                if (Thread.currentThread() == thread) {
                    final ConcurrentArray concurrentArray = (ConcurrentArray) Layouts.ARRAY.getStore(array);
                    final String name = Layouts.SYMBOL.getString(strategy);
                    switch (name) {
                        case "StampedLock":
                            Layouts.ARRAY.setStore(array, new StampedLockArray(concurrentArray.getStore(), new StampedLock()));
                            break;
                        default:
                            throw new UnsupportedOperationException();
                    }
                }
            });
            return array;
        }

    }

}
