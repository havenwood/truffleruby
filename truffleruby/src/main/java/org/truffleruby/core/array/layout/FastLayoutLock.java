package org.truffleruby.core.array.layout;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.profiles.ConditionProfile;

public final class FastLayoutLock {

    public static final FastLayoutLock GLOBAL_LOCK = new FastLayoutLock();

    public static final int INACTIVE = 0;
    public static final int WRITER_ACTIVE = -11;
    public static final int LAYOUT_CHANGE = 1;

    final HashMap<Long, AtomicInteger> threadStates = new HashMap<>();
    public volatile AtomicInteger[] gather = new AtomicInteger[0];

    public final StampedLock baseLock = new StampedLock();

    public FastLayoutLock() {
    }

    public long startLayoutChange() {
        long stamp = baseLock.tryWriteLock();
        if (stamp != 0) {
            final AtomicInteger[] gather = this.gather;
            for (int i = 0; i < gather.length; i++) {
                AtomicInteger state = gather[i];
                if (state.get() != LAYOUT_CHANGE) {
                    while (!state.compareAndSet(INACTIVE, LAYOUT_CHANGE)) {
                        LayoutLock.yield();
                    }
                }
            }
            return stamp;
        } else {
            return getWriteLock();
        }
    }

    public void finishLayoutChange(long stamp) {
        unlockWrite(stamp);
    }

    public void startWrite(AtomicInteger ts) {
        if (!ts.compareAndSet(INACTIVE, WRITER_ACTIVE)) { // check for fast path
            long stamp = getReadLock();
            ts.set(WRITER_ACTIVE);
            unlockRead(stamp);
        }
    }


    public void finishWrite(AtomicInteger ts) {
        ts.set(INACTIVE);
    }

    public boolean finishRead(AtomicInteger ts, ConditionProfile fastPath) {
        if (fastPath.profile(ts.get() == INACTIVE)) {
            return true;
        }
        long stamp = getReadLock();
        ts.set(INACTIVE);
        unlockRead(stamp);
        return false;
    }


    @TruffleBoundary
    private long getWriteLock() {
        return baseLock.writeLock();
    }

    @TruffleBoundary
    private void unlockWrite(long stamp) {
        baseLock.unlockWrite(stamp);
    }

    @TruffleBoundary
    private long getReadLock() {
        return baseLock.readLock();
    }

    @TruffleBoundary
    private void unlockRead(long stamp) {
        baseLock.unlockRead(stamp);
    }

    private void updateGather() {
        gather = new AtomicInteger[threadStates.size()];
        int next = 0;
        for (AtomicInteger ts : threadStates.values())
            gather[next++] = ts;
    }

    public AtomicInteger registerThread(long tid) {
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

    public void unregisterThread() {
        long tid = ((ThreadWithDirtyFlag) Thread.currentThread()).getThreadId();
        long stamp = startLayoutChange();
        try {
            threadStates.remove(tid);
            updateGather();
        } finally {
            finishLayoutChange(stamp);
        }
    }

}
