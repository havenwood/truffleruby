package org.truffleruby.core.array.layout;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.profiles.ConditionProfile;

public final class FastLayoutLock {

    public static final int INACTIVE = 0;
    public static final int WRITER_ACTIVE = -11;
    public static final int LAYOUT_CHANGE = 1;

    public AtomicInteger[] gather = new AtomicInteger[0];

    public final StampedLock baseLock = new StampedLock();
    public boolean needToRecover = false; // Protected by the baseLock

    public FastLayoutLock() {
    }

    public long startLayoutChange(ConditionProfile tryLock, ConditionProfile waitProfile) {
        long stamp = baseLock.tryWriteLock();
        if (tryLock.profile(stamp != 0)) {
            markLCFlags(waitProfile);
            return stamp;
        } else {
            long stamp1 = getWriteLock();
            if (needToRecover) {
                markLCFlags(waitProfile);
                needToRecover = false;
            }
            return stamp1;
        }
    }

    private void markLCFlags(ConditionProfile waitProfile) {
        final AtomicInteger[] gather = this.gather;
        for (int i = 0; i < gather.length; i++) {
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
    public void changeThreadState(AtomicInteger ts, int state) {
        long stamp = getReadLock();
        ts.set(state);
        needToRecover = true;
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


    public AtomicInteger registerThread() {
        AtomicInteger ts = new AtomicInteger(INACTIVE);
        long stamp = baseLock.writeLock();
        addToGather(ts);
        needToRecover = true;
        baseLock.unlockWrite(stamp);
        return ts;
    }

    public void unregisterThread(AtomicInteger ts) {
        long stamp = baseLock.writeLock();
        removeFromGather(ts);
        baseLock.unlockWrite(stamp);
    }

    private void addToGather(AtomicInteger e) {
        AtomicInteger[] newGather = new AtomicInteger[gather.length + 1];
        System.arraycopy(gather, 0, newGather, 0, gather.length);
        newGather[gather.length] = e;
        gather = newGather;
    }

    private void removeFromGather(AtomicInteger e) {
        for (int i = 0; i < gather.length; i++) {
            if (gather[i] == e) {
                AtomicInteger[] newGather = new AtomicInteger[gather.length - 1];
                System.arraycopy(gather, 0, newGather, 0, i);
                System.arraycopy(gather, i + 1, newGather, i, gather.length - i - 1);
                gather = newGather;
                return;
            }
        }
        throw new Error("not found");
    }

    // private static final ConditionProfile DUMMY_PROFILE = ConditionProfile.createBinaryProfile();

}
