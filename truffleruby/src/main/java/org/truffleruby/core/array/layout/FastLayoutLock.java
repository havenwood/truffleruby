package org.truffleruby.core.array.layout;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.profiles.ConditionProfile;

public final class FastLayoutLock {

    public static final int INACTIVE = 0;
    private static final int WRITER_ACTIVE = 2;
    private static final int LAYOUT_CHANGE = 4;
    private static final int LAYOUT_CHANGE_PENDING = 1;

    private ThreadStateReference[] gather = new ThreadStateReference[0];

    private final StampedLock baseLock = new StampedLock();
    private boolean needToRecover = false; // Protected by the baseLock
    // public static AtomicInteger maxLCpressure = new AtomicInteger(0);
    // AtomicInteger pendingLCs = new AtomicInteger(0);
    // public final static boolean DETECT_PENDING_LC_PRESSURE = true;

    public FastLayoutLock() {
    }

    // public static int getMaxLCPressure() {
    // return maxLCpressure.get();
    // }
    public long startLayoutChange(ConditionProfile tryLock, ConditionProfile waitProfile) {
        // if (DETECT_PENDING_LC_PRESSURE) {
        // int waiting = pendingLCs.incrementAndGet();
        // int c = 0;
        // while ((waiting > (c = pendingLCs.get()) && !maxLCpressure.compareAndSet(c, waiting)))
        // ;
        // }
        long stamp = baseLock.tryWriteLock();
        if (tryLock.profile(stamp != 0)) {
            // if (DETECT_PENDING_LC_PRESSURE) {
            // pendingLCs.decrementAndGet();
            // }
            markLCFlags(waitProfile);
            return stamp;
        } else {
            long stamp1 = acquireExclusiveLock();
            // if (DETECT_PENDING_LC_PRESSURE) {
            // pendingLCs.decrementAndGet();
            // }
            if (needToRecover) {
                markLCFlags(waitProfile);
                needToRecover = false;
            }
            return stamp1;
        }
    }

    private void markLCFlags(ConditionProfile waitProfile) {
        final ThreadStateReference[] gather = this.gather;
        for (int i = 0; i < gather.length; i++) {
            ThreadStateReference state = gather[i];
            if (waitProfile.profile(state.get() != LAYOUT_CHANGE)) {
                state.add(LAYOUT_CHANGE_PENDING);
                while (!state.compareAndSet(INACTIVE + LAYOUT_CHANGE_PENDING, LAYOUT_CHANGE)) {
                    LayoutLock.yield();
                }
            }
        }

    }

    public void finishLayoutChange(long stamp) {
        releaseExclusiveLock(stamp);
    }

    public void startWrite(ThreadStateReference ts, ConditionProfile fastPath) {
        if (!fastPath.profile(ts.compareAndSet(INACTIVE, WRITER_ACTIVE))) {
            changeThreadState(ts, WRITER_ACTIVE);
        }
    }

    public void finishWrite(ThreadStateReference ts) {
        ts.add(-WRITER_ACTIVE);
    }

    public boolean finishRead(ThreadStateReference ts, ConditionProfile fastPath) {
        if (fastPath.profile(ts.get() == INACTIVE)) {
            return true;
        }
        changeThreadState(ts, INACTIVE);
        return false;
    }

    @TruffleBoundary
    public void changeThreadState(ThreadStateReference ts, int state) {
        long stamp = acquireSharedLock();
        try {
            // System.err.println("SLOW PATH changeThreadState " + ts.get() + " to " + state);
            ts.set(state);
            needToRecover = true;
        } finally {
            releaseSharedLock(stamp);
        }
    }


    @TruffleBoundary
    private long acquireExclusiveLock() {
        return baseLock.writeLock();
    }

    @TruffleBoundary
    private void releaseExclusiveLock(long stamp) {
        baseLock.unlockWrite(stamp);
    }

    @TruffleBoundary
    private long acquireSharedLock() {
        return baseLock.readLock();
    }

    @TruffleBoundary
    private void releaseSharedLock(long stamp) {
        baseLock.unlockRead(stamp);
    }


    public void registerThread(ThreadStateReference ts) {
        long stamp = acquireExclusiveLock();
        try {
            addToGather(ts);
            needToRecover = true;
        } finally {
            releaseExclusiveLock(stamp);
        }
    }

    public void unregisterThread(ThreadStateReference ts) {
        long stamp = acquireExclusiveLock();
        try {
            removeFromGather(ts);
        } finally {
            releaseExclusiveLock(stamp);
        }
    }

    private void addToGather(ThreadStateReference e) {
        ThreadStateReference[] newGather = new ThreadStateReference[gather.length + 1];
        System.arraycopy(gather, 0, newGather, 0, gather.length);
        newGather[gather.length] = e;
        gather = newGather;
    }

    private void removeFromGather(ThreadStateReference e) {
        for (int i = 0; i < gather.length; i++) {
            if (gather[i] == e) {
                ThreadStateReference[] newGather = new ThreadStateReference[gather.length - 1];
                System.arraycopy(gather, 0, newGather, 0, i);
                System.arraycopy(gather, i + 1, newGather, i, gather.length - i - 1);
                gather = newGather;
                return;
            }
        }
        throw new Error("not found");
    }

}
