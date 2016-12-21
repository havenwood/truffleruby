package org.truffleruby.core.array;

import org.truffleruby.Layouts;
import org.truffleruby.core.array.ConcurrentArray.FixedSizeArray;
import org.truffleruby.core.array.ConcurrentArray.SynchronizedArray;
import org.truffleruby.language.RubyNode;
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
public abstract class ArraySyncSetStoreNode extends RubyNode {

    @Child RubyNode builtinNode;

    public ArraySyncSetStoreNode(RubyNode builtinNode) {
        this.builtinNode = builtinNode;
    }

    @Specialization(guards = "!isConcurrentArray(array)")
    public Object localArray(VirtualFrame frame, DynamicObject array) {
        return builtinNode.execute(frame);
    }

    @Specialization(guards = "isFixedSizeArray(array)")
    public Object migrateFixedSizeToSynchronized(VirtualFrame frame, DynamicObject array) {
        final Thread thread = Thread.currentThread();
        getContext().getSafepointManager().pauseAllThreadsAndExecute(this, false, (rubyThread, currentNode) -> {
            if (Thread.currentThread() == thread) {
                final Object store = Layouts.ARRAY.getStore(array);
                if (!(store instanceof SynchronizedArray)) { // Was just migrated by another thread
                    FixedSizeArray fixedSizeArray = (FixedSizeArray) store;
                    SynchronizedArray synchronizedArray = new SynchronizedArray(fixedSizeArray.getStore());
                    Layouts.ARRAY.setStore(array, synchronizedArray);
                }
            }
        });

        return synchronizedArray(frame, array);
    }

    @Specialization(guards = "isSynchronizedArray(array)")
    public Object synchronizedArray(VirtualFrame frame, DynamicObject array) {
        synchronized (array) {
            return builtinNode.execute(frame);
        }
    }

}
