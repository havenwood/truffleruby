package org.javaexperiments;

public class BiasedLocking2 {

    static final int ITERS = 100000;

    final Object MONITOR = new Object();

    final int[] array = new int[1000];

    public BiasedLocking2() {
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

    public static void main(String[] args) {
        BiasedLocking2 biasedLocking = new BiasedLocking2();
        System.out.println(biasedLocking.bench());
    }

}
