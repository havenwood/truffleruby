/*
 * Copyright (c) 2016, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.array;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;

import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyGuards;

@ImportStatic(ArrayGuards.class)
public abstract class ArrayToObjectArrayNode extends RubyBaseNode {

    public Object[] unsplat(Object[] arguments) {
        assert arguments.length == 1;
        assert RubyGuards.isRubyArray(arguments[0]);
        return executeToObjectArray((DynamicObject) arguments[0]);
    }

    public abstract Object[] executeToObjectArray(DynamicObject array);

    @Specialization(guards = "strategy.matches(array)", limit = "STORAGE_STRATEGIES")
    public Object[] toObjectArrayOther(DynamicObject array,
            @Cached("of(array)") ArrayStrategy strategy) {
        final int size = strategy.getSize(array);
        return strategy.newMirror(array).getBoxedCopy(size);
    }

}
