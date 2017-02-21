package org.truffleruby.core.array.layout;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import com.sun.corba.se.impl.orbutil.concurrent.Mutex;

public class FastLayoutLock {

    public static final FastLayoutLock GLOBAL_LOCK = new FastLayoutLock();

    public static final int INACTIVE = 0;
    public static final int WRITER_ACTIVE = -11;
    public static final int LAYOUT_CHANGE = 1;

    public ReentrantLock baseLock = new ReentrantLock();


    final HashMap<Long, AtomicInteger> threadStates = new HashMap<>();

    AtomicInteger lockState = new AtomicInteger(INACTIVE);
    public volatile AtomicInteger gather[] = new AtomicInteger[1];


    public FastLayoutLock() {
        gather[0] = lockState;
    }

    AtomicInteger getThreadState(long tid) {
        AtomicInteger ts;
        do {
            ts = threadStates.get(tid);
        } while (!finishRead(lockState));
        return ts;
    }



    public void startLayoutChange() {
        boolean acquired = false;
        acquired = baseLock.tryLock();
        if (acquired) {
            for (int i = 0; i < gather.length; i++) {
                AtomicInteger state = gather[i];
                if (state.get() != LAYOUT_CHANGE)
                    while (!state.compareAndSet(INACTIVE, LAYOUT_CHANGE)) {
                        LayoutLock.yield();
                    }
            }
        } else {
            baseLock.lock();
        }
    }

    public void finishLayoutChange() {
        baseLock.unlock();
    }


    public void startWrite(AtomicInteger ts) {
        if (!ts.compareAndSet(INACTIVE, WRITER_ACTIVE)) { // check for fast path
            baseLock.lock();
            ts.set(WRITER_ACTIVE);
            baseLock.unlock();
        }
    }

    public void finishWrite(AtomicInteger ts) {
        ts.set(INACTIVE);
    }

    public boolean finishRead(AtomicInteger ts) {
        if (ts.get() == INACTIVE) // check for fast path
            return true;
        // slow path
        baseLock.lock();
        ts.set(INACTIVE);
        baseLock.unlock();
        return false;
    }


    private void updateGather() {
        gather = new AtomicInteger[threadStates.size() + 1];
        gather[0] = lockState;
        int next = 1;
        for (AtomicInteger ts : threadStates.values())
            gather[next++] = ts;
    }

    // block layout changes, but allow other changes to proceed
    public AtomicInteger registerThread(long tid) {
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
    public void unregisterThread() {
        long tid = ((ThreadWithDirtyFlag) Thread.currentThread()).getThreadId();
        startLayoutChange();
        threadStates.remove(tid);
        updateGather();
        finishLayoutChange();
    }

    public void reset() {
        threadStates.clear();
        gather = new AtomicInteger[1];
        gather[0] = lockState;
    }

    public void startRead(AtomicInteger ts) {
    }

    public String getDescription() {
        return "FastLayoutLock";
    }
}
