package org.truffleruby.core.array;

public abstract class ConcurrentArray {

    private final Object store;

    public ConcurrentArray(Object store) {
        assert !(store instanceof ConcurrentArray);
        this.store = store;
    }

    public final Object getStore() {
        return store;
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

}
