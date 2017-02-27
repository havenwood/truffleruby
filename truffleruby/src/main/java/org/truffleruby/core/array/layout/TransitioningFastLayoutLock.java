package org.truffleruby.core.array.layout;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.profiles.ConditionProfile;

public final class TransitioningFastLayoutLock {

    public static final TransitioningFastLayoutLock GLOBAL_LOCK = new TransitioningFastLayoutLock();

    public static final int TRANSITION_THRESHOLD = 4;
    public static final int STATE_STAMPED = 0;
    public static final int STATE_NEEDS_TO_SWITCH = STATE_STAMPED + 1;
    public static final int STATE_SWITCHING = STATE_NEEDS_TO_SWITCH + 1;
    public static final int STATE_LAYOUT_LOCK = STATE_SWITCHING + 1;


    public static final int WRITER_ACTIVE = -1;
    public static final int INACTIVE = 0;
    public static final int LAYOUT_CHANGE = 1;

    AtomicInteger selector = new AtomicInteger(STATE_STAMPED);

    final HashMap<Long, AtomicInteger> threadStates = new HashMap<>();
    public volatile AtomicInteger[] gather = new AtomicInteger[0];

    public final StampedLock baseLock = new StampedLock();


    public TransitioningFastLayoutLock() {
    }

    public long startLayoutChange(ConditionProfile tryLock, ConditionProfile waitProfile) {
        if (selector.get() != STATE_LAYOUT_LOCK) { // stamped lock mode
            return getWriteLock();
        }
        long stamp = baseLock.tryWriteLock();
        if (tryLock.profile(stamp != 0)) {
            final AtomicInteger[] gather = this.gather;
            for (int i = 0; i < gather.length; i++) {
                AtomicInteger state = gather[i];
                if (waitProfile.profile(state.get() != LAYOUT_CHANGE)) {
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

    public long startWrite(AtomicInteger ts, ConditionProfile fastPath) {
        if (selector.get() != STATE_LAYOUT_LOCK) {
            long stamp = getReadLock();
            // what's the count on stamp?
            if (selector.get() == STATE_STAMPED && baseLock.getReadLockCount() > TRANSITION_THRESHOLD) {
                if (selector.compareAndSet(STATE_STAMPED, STATE_NEEDS_TO_SWITCH)) {
                    unlockRead(stamp);
                    stamp = getWriteLock(); // enter exclusive mode (instead of direct upgrade)
                    selector.set(STATE_LAYOUT_LOCK);
                    System.err.println("Transitioned");
                    unlockWrite(stamp);
                    if (ts == null) {
                        ts = getThreadState();
                        changeThreadState(ts, WRITER_ACTIVE);
                        return 0;
                    }
                    
                }
            }
            return stamp;
        }
        if (ts == null)
            ts = getThreadState();
        if (!fastPath.profile(ts.compareAndSet(INACTIVE, WRITER_ACTIVE))) {
            changeThreadState(ts, WRITER_ACTIVE);
        }
        return 0;
    }

    public void finishWrite(AtomicInteger ts, final long stamp) {
        if (stamp != 0 && selector.get() != STATE_LAYOUT_LOCK) {
            unlockRead(stamp);
            return;
        }
        ts.set(INACTIVE);
    }

    public long startRead() {
        if (selector.get() != STATE_LAYOUT_LOCK)
            return baseLock.tryOptimisticRead();
        return 0;
    }

    public boolean finishRead(AtomicInteger ts, ConditionProfile fastPath, final long stamp) {
        if (stamp != 0)
            return baseLock.validate(stamp);
        if (ts == null)
            ts = getThreadState();
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
        unlockRead(stamp);
    }

    @TruffleBoundary
    private long getWriteLock() {
        return baseLock.writeLock();
    }

    @TruffleBoundary
    private void unlockWrite(long stamp) {
        try {
            baseLock.unlockWrite(stamp);
        } catch (IllegalMonitorStateException e) {
            System.out.println("Stamp = " + stamp);
            e.printStackTrace();
            throw e;
        }
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

    public AtomicInteger getThreadState() {
        if (selector.get() == STATE_STAMPED)
            return null;
        long tid = Thread.currentThread().getId();
        AtomicInteger ts = null;
        long stamp = 0;
        do {
            stamp = baseLock.readLock();
            ts = threadStates.get(tid);
        } while (!baseLock.validate(stamp));
        if (ts != null)
            return ts;
        return registerThread(tid);
    }

    private static final ConditionProfile DUMMY_PROFILE = ConditionProfile.createBinaryProfile();

}
