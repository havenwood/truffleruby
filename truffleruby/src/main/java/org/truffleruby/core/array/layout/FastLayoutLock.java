package org.truffleruby.core.array.layout;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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
    public final AtomicBoolean needToRecover = new AtomicBoolean(false);

    public FastLayoutLock() {
    }

    public long startLayoutChange(ConditionProfile tryLock, ConditionProfile waitProfile) {
        long stamp = baseLock.tryWriteLock();
        if (tryLock.profile(stamp != 0)) {
            markLCFlags(waitProfile);
            return stamp;
        } else {
            long stamp1 = getWriteLock();
            if (needToRecover.get()) {
                markLCFlags(waitProfile);
                needToRecover.set(false);
            }
            return stamp1;
        }
    }

    private void markLCFlags(ConditionProfile waitProfile) {
        for (int i = 0; i < gather.length; i++) {
            final AtomicInteger[] gather = this.gather;
            AtomicInteger state = gather[i];
            if (waitProfile.profile(state.get() != LAYOUT_CHANGE)) {
                while (!state.compareAndSet(INACTIVE, LAYOUT_CHANGE)) {
                    LayoutLock.yield();
                }
            }
        }

    }

    public void finishLayoutChange(long stamp) {
        unlockWrite(stamp);
    }

    public void startWrite(AtomicInteger ts, ConditionProfile fastPath) {
        if (!fastPath.profile(ts.compareAndSet(INACTIVE, WRITER_ACTIVE))) {
            changeThreadState(ts, WRITER_ACTIVE);
        }
    }

    public void finishWrite(AtomicInteger ts) {
        ts.set(INACTIVE);
    }

    public boolean finishRead(AtomicInteger ts, ConditionProfile fastPath) {
        if (fastPath.profile(ts.get() == INACTIVE)) {
            return true;
        }
        changeThreadState(ts, INACTIVE);
        return false;
    }

    @TruffleBoundary
    private void changeThreadState(AtomicInteger ts, int state) {
        long stamp = getReadLock();
        ts.set(state);
        needToRecover.compareAndSet(false, true);
        unlockRead(stamp);
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
        long stamp = startLayoutChange(DUMMY_PROFILE, DUMMY_PROFILE);
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
        long stamp = startLayoutChange(DUMMY_PROFILE, DUMMY_PROFILE);
        try {
            threadStates.remove(tid);
            updateGather();
        } finally {
            finishLayoutChange(stamp);
        }
    }

    private static final ConditionProfile DUMMY_PROFILE = ConditionProfile.createBinaryProfile();

}
