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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import org.truffleruby.builtins.CoreClass;
import org.truffleruby.builtins.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.language.objects.shared.SharedObjects;
import org.truffleruby.core.array.ConcurrentArray.CustomLockArray;
import org.truffleruby.core.array.ConcurrentArray.FastAppendArray;
import org.truffleruby.core.array.ConcurrentArray.FastLayoutLockArray;
import org.truffleruby.core.array.ConcurrentArray.FixedSizeArray;
import org.truffleruby.core.array.ConcurrentArray.LayoutLockArray;
import org.truffleruby.core.array.ConcurrentArray.ReentrantLockArray;
import org.truffleruby.core.array.ConcurrentArray.StampedLockArray;
import org.truffleruby.core.array.ConcurrentArray.SynchronizedArray;
import org.truffleruby.core.array.layout.FastLayoutLock;
import org.truffleruby.core.array.layout.LayoutLock;
import org.truffleruby.core.array.layout.MyBiasedLock;
import org.truffleruby.Layouts;

import java.util.concurrent.locks.ReentrantLock;
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
            final boolean isLocalArray = !SharedObjects.isShared(getContext(), array);
            final String name = Layouts.SYMBOL.getString(strategy);
            if (isLocalArray) {
                SharedObjects.writeBarrier(getContext(), array);
                changeStrategy(array, name);
            } else {
                final Thread thread = Thread.currentThread();
                getContext().getSafepointManager().pauseAllThreadsAndExecute(this, false, (rubyThread, currentNode) -> {
                    if (Thread.currentThread() == thread) {
                        changeStrategy(array, name);
                    }
                });
            }
            return array;
        }

        private void changeStrategy(DynamicObject array, String name) {
            final ConcurrentArray concurrentArray = (ConcurrentArray) Layouts.ARRAY.getStore(array);
            switch (name) {
                case "FixedSize":
                    Layouts.ARRAY.setStore(array, new FixedSizeArray(concurrentArray.getStore()));
                    break;
                case "Synchronized":
                    Layouts.ARRAY.setStore(array, new SynchronizedArray(concurrentArray.getStore()));
                    break;
                case "ReentrantLock":
                    Layouts.ARRAY.setStore(array, new ReentrantLockArray(concurrentArray.getStore(), new ReentrantLock()));
                    break;
                case "CustomLock":
                    Layouts.ARRAY.setStore(array, new CustomLockArray(concurrentArray.getStore(), new MyBiasedLock()));
                    break;
                case "StampedLock":
                    Layouts.ARRAY.setStore(array, new StampedLockArray(concurrentArray.getStore(), new StampedLock()));
                    break;
                case "LayoutLock":
                    Layouts.ARRAY.setStore(array, new LayoutLockArray(concurrentArray.getStore(), new LayoutLock()));
                    break;
                case "FastLayoutLock":
                    Layouts.ARRAY.setStore(array, new FastLayoutLockArray(concurrentArray.getStore(), new FastLayoutLock()));
                    break;
                case "FastAppend":
                    Layouts.ARRAY.setStore(array, new FastAppendArray(concurrentArray.getStore(), new FastLayoutLock()));
                    break;
                default:
                    throw new UnsupportedOperationException("Invalid strategy " + name);
            }
        }

    }

    @CoreMethod(names = "stamped_lock_optimistic_read", onSingleton = true, required = 1)
    public abstract static class StampedLockOptimisticReadNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        public long stampedLock(DynamicObject array) {
            final StampedLockArray stampedLockArray = (StampedLockArray) Layouts.ARRAY.getStore(array);
            final StampedLock lock = stampedLockArray.getLock();
            return lock.tryOptimisticRead();
        }

    }

    @CoreMethod(names = "stamped_lock_validate", onSingleton = true, required = 2)
    public abstract static class StampedLockValidateNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        public DynamicObject stampedLock(DynamicObject array, long stamp) {
            final StampedLockArray stampedLockArray = (StampedLockArray) Layouts.ARRAY.getStore(array);
            final StampedLock lock = stampedLockArray.getLock();
            if (!lock.validate(stamp)) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError();
            }
            return array;
        }

    }

}
