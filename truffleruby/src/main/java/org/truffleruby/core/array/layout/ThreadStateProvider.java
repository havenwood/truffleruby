package org.truffleruby.core.array.layout;

public class ThreadStateProvider {

    private static final int CACHE_LINE_SIZE = 64; // bytes

    private final static int STORE_SIZE = 64;
    private final static int PADDING = CACHE_LINE_SIZE / Integer.BYTES;

    private int[] currentStore = new int[STORE_SIZE];
    int index = 0;

    public ThreadStateReference newThreadStateReference() {
        final ThreadStateReference threadStateReference = new ThreadStateReference(index, currentStore);
        index++;
        if (index > STORE_SIZE - PADDING) {
            currentStore = new int[STORE_SIZE];
            index = 0;
        }
        return threadStateReference;
    }

}
