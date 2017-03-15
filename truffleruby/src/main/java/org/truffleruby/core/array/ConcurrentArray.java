package org.truffleruby.core.array;

import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.StampedLock;

import org.truffleruby.core.array.layout.FastLayoutLock;
import org.truffleruby.core.array.layout.LayoutLock;
import org.truffleruby.core.array.layout.MyBiasedLock;
import org.truffleruby.language.objects.ObjectGraphNode;

import com.oracle.truffle.api.object.DynamicObject;

public abstract class ConcurrentArray implements ObjectGraphNode {

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
        final LayoutLock lock;

        public LayoutLockArray(Object store) {
            super(store);
            lock = new LayoutLock();
        }

        public LayoutLock getLock() {
            return lock;
        }

    }

    public static final class FastLayoutLockArray extends ConcurrentArray {
        final FastLayoutLock lock;

        public FastLayoutLockArray(Object store) {
            super(store);
            lock = new FastLayoutLock();
        }

        public FastLayoutLock getLock() {
            return lock;
        }

    }

}
