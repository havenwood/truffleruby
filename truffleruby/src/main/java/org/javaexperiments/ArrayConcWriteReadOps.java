package org.javaexperiments;

public class ArrayConcWriteReadOps {

    static volatile boolean go = false;
    static volatile boolean run = false;

    static final int READS = 1024;
    static final int N = 100;

    static final int ROUNDS = 5;

    static volatile long[] OPS;

    static int[] ary;

    public static void main(String[] args) {
        int n = Integer.valueOf(args[0]);
        try {
            runMain(n);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    static void runMain(int nThreads) throws InterruptedException {
        OPS = new long[nThreads];
        ary = new int[READS * nThreads];

        for (int t = 0; t < nThreads; t++) {
            int base = t * READS;
            for (int i = 0; i < READS; i++) {
                ary[base + i] = i;
            }
        }

        int s = 0;
        for (int i = 0; i < READS; i++) {
            s += i;
        }
        final int expectedSum = s;

        Thread[] threads = new Thread[nThreads];
        for (int t1 = 0; t1 < threads.length; t1++) {
            final int t = t1;
            threads[t] = new Thread(() -> {
                for (int r = 0; r < ROUNDS; r++) {
                    while (!go) {
                        Thread.yield();
                    }

                    long ops = 0;
                    while (run) {
                        final int base = t * READS;

                        for (int i = 0; i < N; i++) {
                            ary[base + i] = i;

                            int sum = 0;
                            for (int j = base; j < base + READS; j++) {
                                sum += ary[j];
                            }

                            if (sum != expectedSum) {
                                throw new Error();
                            }
                        }

                        ops += 1;
                    }

                    OPS[t] = ops;
                }
            });
            threads[t].start();
        }

        for (int r = 0; r < ROUNDS; r++) {
            run = true;
            go = true;
            Thread.sleep(5000);
            go = false;
            run = false;

            for (int i = 0; i < OPS.length; i++) {
                while (OPS[i] == 0) {
                    Thread.yield();
                }
            }

            // System.out.println(Arrays.toString(OPS));
            long total = 0;
            for (int i = 0; i < OPS.length; i++) {
                total += OPS[i];
                OPS[i] = 0;
            }
            System.out.println(total);
        }

        for (Thread appender : threads) {
            appender.join();
        }

    }

}
