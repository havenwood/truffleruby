package org.truffleruby.core.array.layout;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;

public final class FastLayoutLock {

    public static final FastLayoutLock GLOBAL_LOCK = new FastLayoutLock();

    public static final int INACTIVE = 0;
    public static final int WRITER_ACTIVE = -11;
    public static final int LAYOUT_CHANGE = 1;

    public final StampedLock baseLock = new StampedLock();

    final HashMap<Long, AtomicInteger> threadStates = new HashMap<>();

    final AtomicInteger lockState = new AtomicInteger(INACTIVE);
    public volatile AtomicInteger[] gather = new AtomicInteger[1];


    public FastLayoutLock() {
        gather[0] = lockState;
    }

    final AtomicInteger getThreadState(long tid) {
        AtomicInteger ts;
        do {
            ts = threadStates.get(tid);
        } while (!finishRead(lockState));
        return ts;
    }

    public final long startLayoutChange() {
        long stamp = baseLock.tryWriteLock();
        if (stamp != 0) {
            final AtomicInteger[] gather = this.gather;
            for (int i = 0; i < gather.length; i++) {
                AtomicInteger state = gather[i];
                if (state.get() != LAYOUT_CHANGE)
                    while (!state.compareAndSet(INACTIVE, LAYOUT_CHANGE)) {
                        LayoutLock.yield();
                    }
            }
            return stamp;
        } else {
            return baseLock.writeLock();
        }
    }

    public final void finishLayoutChange(long stamp) {
        baseLock.unlockWrite(stamp);
    }

    public final void startWrite(AtomicInteger ts) {
        if (!ts.compareAndSet(INACTIVE, WRITER_ACTIVE)) { // check for fast path
            long stamp = baseLock.readLock();
            ts.set(WRITER_ACTIVE);
            baseLock.unlockRead(stamp);
        }
    }

    public final void finishWrite(AtomicInteger ts) {
        ts.set(INACTIVE);
    }

    public final boolean finishRead(AtomicInteger ts) {
        if (ts.get() == INACTIVE) // check for fast path
            return true;
        // slow path
        long stamp = baseLock.readLock();
        ts.set(INACTIVE);
        baseLock.unlockRead(stamp);
        return false;
    }


    private final void updateGather() {
        gather = new AtomicInteger[threadStates.size() + 1];
        gather[0] = lockState;
        int next = 1;
        for (AtomicInteger ts : threadStates.values())
            gather[next++] = ts;
    }

    public final AtomicInteger registerThread(long tid) {
        AtomicInteger ts = new AtomicInteger();
        long stamp = startLayoutChange();
        try {
            threadStates.put(tid, ts);
            updateGather();
        } finally {
            finishLayoutChange(stamp);
        }
        return ts;
    }

    public final void unregisterThread() {
        long tid = ((ThreadWithDirtyFlag) Thread.currentThread()).getThreadId();
        long stamp = startLayoutChange();
        try {
            threadStates.remove(tid);
            updateGather();
        } finally {
            finishLayoutChange(stamp);
        }
    }

    public final void reset() {
        threadStates.clear();
        gather = new AtomicInteger[1];
        gather[0] = lockState;
    }
}
