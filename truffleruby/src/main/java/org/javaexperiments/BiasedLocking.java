package org.javaexperiments;

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.StampedLock;

public class BiasedLocking {

    static final int ITERS = 100000;

    final int[] array = new int[1000];
    final Object MONITOR = new Object();
    final ReentrantLock reentrantLock = new ReentrantLock();
    final StampedLock stampedLock = new StampedLock();

    public BiasedLocking() {
        for (int i = 0; i < array.length; i++) {
            array[i] = i % 2;
        }
    }

    public int bench() {
        int sum = 0;
        long t0, t1;

        t0 = System.nanoTime();
        for (int j = 0; j < ITERS; j++) {
            sum = unsyncSum();
        }
        t1 = System.nanoTime();
        System.out.println("unsync " + (t1 - t0) / 1000);

        t0 = System.nanoTime();
        for (int j = 0; j < ITERS; j++) {
            sum = syncSum();
        }
        t1 = System.nanoTime();
        System.out.println("sync   " + (t1 - t0) / 1000);

        t0 = System.nanoTime();
        for (int j = 0; j < ITERS; j++) {
            sum = reentrantSum();
        }
        t1 = System.nanoTime();
        System.out.println("reent  " + (t1 - t0) / 1000);

        t0 = System.nanoTime();
        for (int j = 0; j < ITERS; j++) {
            sum = stampedSum();
        }
        t1 = System.nanoTime();
        System.out.println("stamp  " + (t1 - t0) / 1000);
        return sum;
    }

    public int unsyncSum() {
        int sum = 0;
        for (int i = 0; i < array.length; i++) {
            sum += array[i];
        }
        return sum;
    }

    public int syncSum() {
        int sum = 0;
        for (int i = 0; i < array.length; i++) {
            int e;
            synchronized (MONITOR) {
                e = array[i];
            }
            sum += e;
        }
        return sum;
    }

    public int reentrantSum() {
        int sum = 0;
        for (int i = 0; i < array.length; i++) {
            int e;
            try {
                reentrantLock.lock();
                e = array[i];
            } finally {
                reentrantLock.unlock();
            }
            sum += e;
        }
        return sum;
    }

    public int stampedSum() {
        int sum = 0;
        for (int i = 0; i < array.length; i++) {
            int e;
            long stamp = stampedLock.tryOptimisticRead();
            e = array[i];
            if (!stampedLock.validate(stamp)) {
                throw new AssertionError();
            }
            sum += e;
        }
        return sum;
    }

    public static void main(String[] args) {
        BiasedLocking biasedLocking = new BiasedLocking();
        System.out.println(biasedLocking.bench());
    }

}
