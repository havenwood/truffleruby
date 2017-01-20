package org.javaexperiments;

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.StampedLock;

public class BiasedLocking {

    final int[] array = new int[2000000 / 200];
    final ReentrantLock reentrantLock = new ReentrantLock();
    final StampedLock stampedLock = new StampedLock();

    public BiasedLocking() {
        for (int i = 0; i < array.length; i++) {
            array[i] = i % 2;
        }
    }

    public int bench() {
        int sum = 0;
        for (int i = 0; i < 100; i++) {
            System.out.println();
            long t0, t1;

            t0 = System.nanoTime();
            for (int j = 0; j < 10000; j++) {
                sum = unsyncSum();
            }
            t1 = System.nanoTime();
            System.out.println("unsync " + (t1 - t0));

            t0 = System.nanoTime();
            for (int j = 0; j < 10000; j++) {
                sum = syncSum();
            }
            t1 = System.nanoTime();
            System.out.println("sync   " + (t1 - t0));

            t0 = System.nanoTime();
            for (int j = 0; j < 10000; j++) {
                sum = reentrantSum();
            }
            t1 = System.nanoTime();
            System.out.println("reent  " + (t1 - t0));

            t0 = System.nanoTime();
            for (int j = 0; j < 10000; j++) {
                sum = stampedSum();
            }
            t1 = System.nanoTime();
            System.out.println("stamp  " + (t1 - t0));
        }
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
            synchronized (array) {
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
