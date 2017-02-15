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

import static org.truffleruby.core.array.ArrayHelpers.getSize;
import static org.truffleruby.core.array.ArrayHelpers.setSize;

import org.truffleruby.Layouts;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.control.RaiseException;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ImportStatic(ArrayGuards.class)
public abstract class ConcurrentArrayIndexSetNode extends PrimitiveArrayArgumentsNode {

    @Child private ArrayReadNormalizedNode readNode;
    @Child private ArrayWriteNormalizedNode writeNode;
    @Child private ArrayReadSliceNormalizedNode readSliceNode;

    private final BranchProfile negativeIndexProfile = BranchProfile.create();
    private final BranchProfile negativeLengthProfile = BranchProfile.create();

    public static ConcurrentArrayIndexSetNode create() {
        return ConcurrentArrayIndexSetNodeFactory.create(null);
    }

    public abstract Object executeSet(DynamicObject array, Object index, Object length, Object value);

    // array[index] = object

    @Specialization
    public Object set(DynamicObject array, int index, Object value, NotProvided unused,
            @Cached("createBinaryProfile()") ConditionProfile negativeIndexProfile) {
        final int normalizedIndex = ArrayOperations.normalizeIndex(getSize(array), index, negativeIndexProfile);
        checkIndex(array, index, normalizedIndex);
        return write(array, normalizedIndex, value);
    }

    // array[index] = object with non-int index

    @Specialization(guards = { "!isInteger(indexObject)", "!isRubyRange(indexObject)" })
    public Object set(DynamicObject array, Object indexObject, Object value, NotProvided unused) {
        return FAILURE;
    }

    // array[start, length] = object

    @Specialization(guards = { "!isRubyArray(value)", "wasProvided(value)" })
    public Object setObject(DynamicObject array, int start, int length, Object value) {
        return FAILURE;
    }

    // array[start, length] = other_array, with length == other_array.size

    @Specialization(guards = {
            "isRubyArray(replacement)",
            "length == getArraySize(replacement)"
    })
    public Object setOtherIntArraySameLength(DynamicObject array, int start, int length, DynamicObject replacement,
            @Cached("createBinaryProfile()") ConditionProfile negativeIndexProfile) {
        final int normalizedIndex = ArrayOperations.normalizeIndex(getSize(array), start, negativeIndexProfile);
        checkIndex(array, start, normalizedIndex);

        for (int i = 0; i < length; i++) {
            write(array, normalizedIndex + i, read(replacement, i));
        }
        return replacement;
    }

    // array[start, length] = other_array, with length != other_array.size

    @Specialization(guards = {
            "isRubyArray(replacement)",
            "length != getArraySize(replacement)"
    })
    public Object setOtherArray(DynamicObject array, int rawStart, int length, DynamicObject replacement,
            @Cached("createBinaryProfile()") ConditionProfile negativeIndexProfile,
            @Cached("createBinaryProfile()") ConditionProfile needCopy,
            @Cached("createBinaryProfile()") ConditionProfile recursive,
            @Cached("createBinaryProfile()") ConditionProfile emptyReplacement,
            @Cached("createBinaryProfile()") ConditionProfile grow) {
        checkLengthPositive(length);
        final int start = ArrayOperations.normalizeIndex(getSize(array), rawStart, negativeIndexProfile);
        checkIndex(array, rawStart, start);

        final int end = start + length;
        final int arraySize = getSize(array);
        final int replacementSize = getSize(replacement);
        final int endOfReplacementInArray = start + replacementSize;

        if (recursive.profile(array == replacement)) {
            final DynamicObject copy = readSlice(array, 0, arraySize);
            return executeSet(array, start, length, copy);
        }

        // Make a copy of what's after "end", as it might be erased or at least needs to be moved
        final int tailSize = arraySize - end;
        DynamicObject tailCopy = null;
        final boolean needsTail = needCopy.profile(tailSize > 0);
        if (needsTail) {
            tailCopy = readSlice(array, end, tailSize);
        }

        // Append the replacement array
        for (int i = 0; i < replacementSize; i++) {
            write(array, start + i, read(replacement, i));
        }

        // Append the saved tail
        if (needsTail) {
            for (int i = 0; i < tailSize; i++) {
                write(array, endOfReplacementInArray + i, read(tailCopy, i));
            }
        } else if (emptyReplacement.profile(replacementSize == 0)) {
            // If no tail and the replacement is empty, the array will grow.
            // We need to append nil from index arraySize to index (start - 1).
            // E.g. a = [1,2,3]; a[5,1] = []; a == [1,2,3,nil,nil]
            if (grow.profile(arraySize < start)) {
                for (int i = arraySize; i < start; i++) {
                    write(array, i, nil());
                }
            }
        }

        // Set size
        if (needsTail) {
            setSize(array, endOfReplacementInArray + tailSize);
        } else {
            setSize(array, endOfReplacementInArray);
        }

        return replacement;
    }

    // array[start, length] = object_or_array with non-int start or length

    @Specialization(guards = { "!isInteger(startObject) || !isInteger(lengthObject)", "wasProvided(value)" })
    public Object setStartLengthNotInt(DynamicObject array, Object startObject, Object lengthObject, Object value) {
        return FAILURE;
    }

    // array[start..end] = object_or_array

    @Specialization(guards = "isIntRange(range)")
    public Object setRange(DynamicObject array, DynamicObject range, Object value, NotProvided unused,
            @Cached("createBinaryProfile()") ConditionProfile negativeBeginProfile,
            @Cached("createBinaryProfile()") ConditionProfile negativeEndProfile,
            @Cached("create()") BranchProfile errorProfile) {
        final int size = getSize(array);
        final int begin = Layouts.INT_RANGE.getBegin(range);
        final int start = ArrayOperations.normalizeIndex(size, begin, negativeBeginProfile);
        if (start < 0) {
            errorProfile.enter();
            throw new RaiseException(coreExceptions().rangeError(range, this));
        }
        final int end = ArrayOperations.normalizeIndex(size, Layouts.INT_RANGE.getEnd(range), negativeEndProfile);
        int inclusiveEnd = Layouts.INT_RANGE.getExcludedEnd(range) ? end - 1 : end;
        if (inclusiveEnd < 0) {
            inclusiveEnd = -1;
        }
        final int length = inclusiveEnd - start + 1;
        final int normalizeLength = length > -1 ? length : 0;
        return executeSet(array, start, normalizeLength, value);
    }

    @Specialization(guards = { "!isIntRange(range)", "isRubyRange(range)" })
    public Object setOtherRange(DynamicObject array, DynamicObject range, Object value, NotProvided unused) {
        return FAILURE;
    }

    // Helpers

    private void checkIndex(DynamicObject array, int index, int normalizedIndex) {
        if (normalizedIndex < 0) {
            negativeIndexProfile.enter();
            throw new RaiseException(coreExceptions().indexTooSmallError("array", index, getSize(array), this));
        }
    }

    public void checkLengthPositive(int length) {
        if (length < 0) {
            negativeLengthProfile.enter();
            throw new RaiseException(coreExceptions().negativeLengthError(length, this));
        }
    }

    protected int getArraySize(DynamicObject array) {
        return getSize(array);
    }

    private Object read(DynamicObject array, int index) {
        if (readNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            readNode = insert(ArrayReadNormalizedNodeGen.create(null, null));
        }
        return readNode.executeRead(array, index);
    }

    private Object write(DynamicObject array, int index, Object value) {
        if (writeNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            writeNode = insert(ArrayWriteNormalizedNodeGen.create(null, null, null));
        }
        return writeNode.executeWrite(array, index, value);
    }

    private DynamicObject readSlice(DynamicObject array, int start, int length) {
        if (readSliceNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            readSliceNode = insert(ArrayReadSliceNormalizedNodeGen.create(null, null, null));
        }
        return readSliceNode.executeReadSlice(array, start, length);
    }

}
