package org.truffleruby.core.array;

import java.lang.reflect.Array;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.StampedLock;

import org.truffleruby.core.UnsafeHolder;
import org.truffleruby.core.array.layout.FastLayoutLock;
import org.truffleruby.core.array.layout.LayoutLock;
import org.truffleruby.core.array.layout.MyBiasedLock;
import org.truffleruby.language.objects.ObjectGraphNode;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Layout;
import com.oracle.truffle.api.object.ObjectType;

public abstract class ConcurrentArray implements ObjectGraphNode {

    private static final Layout LAYOUT = Layout.createLayout();
    private static final DynamicObject SOME_OBJECT = LAYOUT.newInstance(LAYOUT.createShape(new ObjectType()));

    private static final long SIZE_OFFSET = UnsafeHolder.getFieldOffset(SOME_OBJECT.getClass(), "primitive1");

    public static int getSizeAndIncrement(DynamicObject array) {
        // TODO: incorrect in enterprise OM!
        return (int) UnsafeHolder.UNSAFE.getAndAddLong(array, SIZE_OFFSET, 1L);
    }

    private final Object store;

    public ConcurrentArray(Object store) {
        assert !(store instanceof ConcurrentArray);
        this.store = store;
    }

    public final Object getStore() {
        return store;
    }

    @Override
    public void getAdjacentObjects(Set<DynamicObject> adjacent) {
        if (store instanceof Object[]) {
            for (Object element : (Object[]) store) {
                if (element instanceof DynamicObject) {
                    adjacent.add((DynamicObject) element);
                }
            }
        }
    }

    public static final class FixedSizeArray extends ConcurrentArray {

        public FixedSizeArray(Object store) {
            super(store);
        }

    }

    public static final class SynchronizedArray extends ConcurrentArray {

        public SynchronizedArray(Object store) {
            super(store);
        }

    }

    public static final class ReentrantLockArray extends ConcurrentArray {

        private final ReentrantLock lock;

        public ReentrantLockArray(Object store, ReentrantLock lock) {
            super(store);
            this.lock = lock;
        }

        public ReentrantLock getLock() {
            return lock;
        }

    }

    public static final class CustomLockArray extends ConcurrentArray {

        private final MyBiasedLock lock;

        public CustomLockArray(Object store, MyBiasedLock lock) {
            super(store);
            this.lock = lock;
        }

        public MyBiasedLock getLock() {
            return lock;
        }

    }

    public static final class StampedLockArray extends ConcurrentArray {

        private final StampedLock lock;

        public StampedLockArray(Object store, StampedLock lock) {
            super(store);
            this.lock = lock;
        }

        public StampedLock getLock() {
            return lock;
        }

    }

    public static final class LayoutLockArray extends ConcurrentArray {

        private final LayoutLock lock;

        public LayoutLockArray(Object store, LayoutLock lock) {
            super(store);
            this.lock = lock;
        }

        public LayoutLock getLock() {
            return lock;
        }

    }

    public static final class FastLayoutLockArray extends ConcurrentArray implements FLLArray {

        private final FastLayoutLock lock;

        public FastLayoutLockArray(Object store, FastLayoutLock lock) {
            super(store);
            this.lock = lock;
        }

        public FastLayoutLock getLock() {
            return lock;
        }

    }

    public static final class FastAppendArray extends ConcurrentArray implements FLLArray {

        private final FastLayoutLock lock;
        private boolean[] tags;

        public FastAppendArray(Object store, FastLayoutLock lock) {
            super(store);
            this.lock = lock;
            this.tags = new boolean[getStoreCapacity(store)];
        }

        private int getStoreCapacity(Object store) {
            if (store == null) {
                return 0;
            } else {
                return Array.getLength(store);
            }
        }

        public FastLayoutLock getLock() {
            return lock;
        }

        public boolean[] getTags() {
            return tags;
        }

        public void setTags(boolean[] tags) {
            this.tags = tags;
        }

    }

    public interface FLLArray {

        public FastLayoutLock getLock();

    }

}
