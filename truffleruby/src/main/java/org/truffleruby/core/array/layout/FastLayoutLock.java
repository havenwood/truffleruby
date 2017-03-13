package org.truffleruby.core.array.layout;

import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.locks.StampedLock;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.profiles.ConditionProfile;

public final class FastLayoutLock {

    public static final int INACTIVE = 0;
    public static final int WRITER_ACTIVE = 2;
    public static final int LAYOUT_CHANGE = 4;

    public ThreadStateReference[] gather = new ThreadStateReference[1];

    public final StampedLock baseLock = new StampedLock();
    public boolean needToRecover = false; // Protected by the baseLock

    public class ThreadStateReference {
        final int index;
        final AtomicIntegerArray store;

        public ThreadStateReference(int index, AtomicIntegerArray store) {
            this.index = index;
            this.store = store;
            set(INACTIVE);
        }

        public boolean compareAndSet(int expect, int update) {
            return store.compareAndSet(index, expect, update);
        }

        public int get() {
            return store.get(index);
        }

        public void set(int val) {
            store.set(index, val);
        }

    }

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
        final ThreadStateReference[] gather = this.gather;
        for (int i = 0; i < gather.length; i++) {
            ThreadStateReference state = gather[i];
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

    public void startWrite(ThreadStateReference ts, ConditionProfile fastPath) {
        if (!fastPath.profile(ts.compareAndSet(INACTIVE, WRITER_ACTIVE))) {
            System.out.println("W:ts.get() = " + ts.get());
            changeThreadState(ts, WRITER_ACTIVE);
        }
    }

    public void finishWrite(ThreadStateReference ts) {
        ts.set(INACTIVE);
    }

    public boolean finishRead(ThreadStateReference ts, ConditionProfile fastPath) {
        if (fastPath.profile(ts.get() == INACTIVE)) {
            return true;
        }
        System.out.println("R:ts.get() = " + ts.get());
        changeThreadState(ts, INACTIVE);
        return false;
    }

    @TruffleBoundary
    public void changeThreadState(ThreadStateReference ts, int state) {
        long stamp = getReadLock();
        System.out.println("slow path");
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


    public void registerThread(ThreadStateReference ts) {
        long stamp = baseLock.writeLock();
        addToGather(ts);
        needToRecover = true;
        baseLock.unlockWrite(stamp);
    }

    public void unregisterThread(ThreadStateReference ts) {
        long stamp = baseLock.writeLock();
        removeFromGather(ts);
        baseLock.unlockWrite(stamp);
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

    // private static final ConditionProfile DUMMY_PROFILE = ConditionProfile.createBinaryProfile();

}
