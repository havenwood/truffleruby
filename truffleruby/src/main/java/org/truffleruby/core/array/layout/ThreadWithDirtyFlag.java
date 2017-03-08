package org.truffleruby.core.array.layout;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.truffleruby.Layouts;
import org.truffleruby.core.array.ConcurrentArray.FastLayoutLockArray;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.ConditionProfile;

public class ThreadWithDirtyFlag extends Thread {
    public final static boolean USE_GLOBAL_FLL = true;

    private static final FastLayoutLock GLOBAL_LOCK = (USE_GLOBAL_FLL) ? new FastLayoutLock() : null;
    private final AtomicInteger fllThreadState = (USE_GLOBAL_FLL) ? GLOBAL_LOCK.registerThread(0) : null;

    private static final AtomicLong threadIds = new AtomicLong();

    public volatile boolean dirty = false;
    public final long threadId = threadIds.incrementAndGet();

    private final LayoutLock.Accessor layoutLockAccessor;
    private final TransitioningFastLayoutLock transitioningFastLayoutLock;

    private final HashMap<DynamicObject, AtomicInteger> lockStates = new HashMap<>();

    private AtomicInteger last = null;
    private DynamicObject lastObject = null;

    public ThreadWithDirtyFlag(Runnable runnable) {
        super(runnable);
        this.layoutLockAccessor = LayoutLock.GLOBAL_LOCK.access();
        this.transitioningFastLayoutLock = TransitioningFastLayoutLock.GLOBAL_LOCK;
    }

    public LayoutLock.Accessor getLayoutLockAccessor() {
        return layoutLockAccessor;
    }

    public TransitioningFastLayoutLock getTransitioningFastLayoutLock() {
        return transitioningFastLayoutLock;
    }

    @TruffleBoundary
    public AtomicInteger getTransitioningThreadState(DynamicObject array) {
        AtomicInteger ts = lockStates.get(array);
        if (ts == null) {
            ts = transitioningFastLayoutLock.registerThread(Thread.currentThread().getId());
            lockStates.put(array, ts);
        }
        return ts;
    }

    public AtomicInteger getThreadState(DynamicObject array, ConditionProfile fastPathProfile) {
        if (USE_GLOBAL_FLL) {
            return fllThreadState;
        } else {
            if (fastPathProfile.profile(lastObject == array)) {
                return last;

            }
            return getThreadStateSlowPath(array);
        }
    }

    public AtomicInteger getThreadState(DynamicObject array) {
        if (USE_GLOBAL_FLL) {
            return fllThreadState;
        } else {
            return (array == lastObject) ? last : getThreadStateSlowPath(array);
        }
    }

    @TruffleBoundary
    public AtomicInteger getThreadStateSlowPath(DynamicObject array) {
        AtomicInteger ts = lockStates.get(array);
        if (ts == null) {
            FastLayoutLockArray fastLayoutLockArray = (FastLayoutLockArray) Layouts.ARRAY.getStore(array);
            ts = fastLayoutLockArray.getLock().registerThread(Thread.currentThread().getId());
            lockStates.put(array, ts);
        }
        lastObject = array;
        last = ts;
        return ts;
    }

    public void cleanup() {
        for (Entry<DynamicObject, AtomicInteger> entry : lockStates.entrySet()) {
            FastLayoutLock lock = ((FastLayoutLockArray) Layouts.ARRAY.getStore(entry.getKey())).getLock();
            lock.unregisterThread(entry.getValue());
        }
    }

    public long getThreadId() {
        return threadId;
    }
}
