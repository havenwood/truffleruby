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
    public long baseLockStamp = 0;


    final HashMap<Long, AtomicInteger> threadStates = new HashMap<>();

    final AtomicInteger lockState = new AtomicInteger(INACTIVE);
    public volatile AtomicInteger gather[] = new AtomicInteger[1];


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

    public final void startLayoutChange() {
        long stamp = baseLock.tryWriteLock();
        if (stamp != 0) {
            for (int i = 0; i < gather.length; i++) {
                AtomicInteger state = gather[i];
                if (state.get() != LAYOUT_CHANGE)
                    while (!state.compareAndSet(INACTIVE, LAYOUT_CHANGE)) {
                        LayoutLock.yield();
                    }
            }
        } else {
            stamp = baseLock.writeLock();
        }
        baseLockStamp = stamp;
    }

    public final void finishLayoutChange() {
        baseLock.unlockWrite(baseLockStamp);
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

    // block layout changes, but allow other changes to proceed
    public final AtomicInteger registerThread(long tid) {
        AtomicInteger ts = new AtomicInteger();
        // System.err.println("call startLayoutChange");
        startLayoutChange();
        // System.err.println("after call startLayoutChange " + counts.get());
        threadStates.put(tid, ts);
        updateGather();
        // finish the layout change, no need to reset the LC flags
        // System.err.println("call finishLayoutChange");
        finishLayoutChange();
        // System.err.println("after call finishLayoutChange " + counts.get());
        return ts;
    }

    // block layout changes, but allow other changes to proceed
    public final void unregisterThread() {
        long tid = ((ThreadWithDirtyFlag) Thread.currentThread()).getThreadId();
        startLayoutChange();
        threadStates.remove(tid);
        updateGather();
        finishLayoutChange();
    }

    public final void reset() {
        threadStates.clear();
        gather = new AtomicInteger[1];
        gather[0] = lockState;
    }

    public final void startRead(AtomicInteger ts) {
    }

    public final String getDescription() {
        return "FastLayoutLock";
    }
}
