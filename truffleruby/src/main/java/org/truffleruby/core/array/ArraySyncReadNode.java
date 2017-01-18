package org.truffleruby.core.array;

import java.util.concurrent.locks.StampedLock;

import org.truffleruby.Layouts;
import org.truffleruby.core.array.ConcurrentArray.StampedLockArray;
import org.truffleruby.core.array.layout.ThreadWithDirtyFlag;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.object.DynamicObject;

@NodeInfo(cost = NodeCost.NONE)
@NodeChild("self")
@ImportStatic(ArrayGuards.class)
public abstract class ArraySyncReadNode extends RubyNode {

    @Child RubyNode builtinNode;

    public ArraySyncReadNode(RubyNode builtinNode) {
        this.builtinNode = builtinNode;
    }

    @Specialization(guards = "!isConcurrentArray(array)")
    public Object localRead(VirtualFrame frame, DynamicObject array) {
        return builtinNode.execute(frame);
    }

    @Specialization(guards = "isFixedSizeArray(array)")
    public Object fixedSizeRead(VirtualFrame frame, DynamicObject array) {
        return builtinNode.execute(frame);
    }

    @Specialization(guards = "isSynchronizedArray(array)")
    public Object synchronizedRead(VirtualFrame frame, DynamicObject array) {
        synchronized (array) {
            return builtinNode.execute(frame);
        }
    }

    @Specialization(guards = "isStampedLockArray(array)")
    public Object stampedLockRead(VirtualFrame frame, DynamicObject array) {
        final StampedLockArray stampedLockArray = (StampedLockArray) Layouts.ARRAY.getStore(array);
        final StampedLock lock = stampedLockArray.getLock();
        final long stamp = lock.tryOptimisticRead();
        Object result = builtinNode.execute(frame);
        if (!lock.validate(stamp)) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError();
        }
        return result;
    }

    @Specialization(guards = "isLayoutLockArray(array)")
    public Object layoutLockRead(VirtualFrame frame, DynamicObject array) {
        Object result = builtinNode.execute(frame);
        if (((ThreadWithDirtyFlag) Thread.currentThread()).dirty) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError();
        }
        return result;
    }

}
