package org.javaexperiments;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.truffleruby.core.UnsafeHolder;
import org.truffleruby.core.array.layout.FastLayoutLock;
import org.truffleruby.core.array.layout.ThreadStateProvider;
import org.truffleruby.core.array.layout.ThreadStateReference;

import com.oracle.truffle.api.profiles.ConditionProfile;

public class ConcurrentArrayAppend {

    volatile int[] store = new int[16];
    volatile boolean[] tags = new boolean[16];
    final AtomicInteger size = new AtomicInteger();

    private static final int NIL = -1;

    public void append(int value) {
        final ThreadStateReference ts = getThreadState();
        LAYOUT_LOCK.startWrite(ts, PROFILE);
        int s = size.getAndIncrement();
        try {
            int[] currentStore = store;
            if (s < currentStore.length) {
                appendInBounds(value, s, currentStore);
                return;
            }
        } finally {
            LAYOUT_LOCK.finishWrite(ts);
        }

        long stamp = LAYOUT_LOCK.startLayoutChange(PROFILE, PROFILE);
        try {
            int[] currentStore = store;
            if (s < currentStore.length) {
                appendInBounds(value, s, currentStore);
            } else {
                final int newCapacity = currentStore.length * 2;
                store = Arrays.copyOf(currentStore, newCapacity);
                tags = Arrays.copyOf(tags, newCapacity);
                store[s] = value;
                tags[s] = true;
            }
        } finally {
            LAYOUT_LOCK.finishLayoutChange(stamp);
        }
    }

    private void appendInBounds(int value, int s, int[] currentStore) {
        currentStore[s] = value;
        UnsafeHolder.UNSAFE.storeFence();
        tags[s] = true;
    }

    public int at(int i) {
        final ThreadStateReference ts = getThreadState();
        int result;
        do {
            final int s = size.get();
            if (i < s && tags[i]) {
                UnsafeHolder.UNSAFE.loadFence();
                result = store[i];
            } else {
                result = NIL;
            }
        } while (!LAYOUT_LOCK.finishRead(ts, PROFILE));
        return result;
    }

    public void set(int i, int value) {
        final ThreadStateReference ts = getThreadState();
        LAYOUT_LOCK.startWrite(ts, PROFILE);
        try {
            final int s = size.get();
            if (i < s) {
                store[i] = value;
                return;
            } else {
                // LC and grow
                throw new Error();
            }
        } finally {
            LAYOUT_LOCK.finishWrite(ts);
        }
    }

    public int pop() {
        long stamp = LAYOUT_LOCK.startLayoutChange(PROFILE, PROFILE);
        try {
            int s = size.decrementAndGet();
            final int value = store[s];
            store[s] = NIL;
            tags[s] = false;
            return value;
        } finally {
            LAYOUT_LOCK.finishLayoutChange(stamp);
        }
    }

    static final FastLayoutLock LAYOUT_LOCK = new FastLayoutLock();
    static final ConditionProfile PROFILE = ConditionProfile.createBinaryProfile();

    ThreadStateReference getThreadState() {
        return ((MyThread) Thread.currentThread()).threadState;
    }

    static final class MyThread extends Thread {

        final ThreadStateReference threadState = new ThreadStateProvider().newThreadStateReference();

        public MyThread(Runnable runnable) {
            super(runnable);
            LAYOUT_LOCK.registerThread(threadState);
        }

    }

    static volatile boolean go = false;

    public static void main(String[] args) throws InterruptedException {
        // int n = Integer.valueOf(args[0]);
        int n = 8;

        final ConcurrentArrayAppend array = new ConcurrentArrayAppend();

        Thread[] appenders = new Thread[n];
        for (int i = 0; i < appenders.length; i++) {
            appenders[i] = new MyThread(() -> {
                while (!go) {
                    Thread.yield();
                }
                for (int v = 0; v < 500; v++) {
                    array.append(v + 1);
                }
            });
            appenders[i].start();
        }

        Thread.sleep(1000);
        go = true;

        for (Thread appender : appenders) {
            appender.join();
        }

        System.out.println(array.size.get());
    }

}
