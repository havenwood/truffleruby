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

    public final static boolean USE_GLOBAL_FLL = false;

    private final ThreadStateProvider threadStateProvider = new ThreadStateProvider();
    private static final FastLayoutLock GLOBAL_LOCK = USE_GLOBAL_FLL ? new FastLayoutLock() : null;
    private final ThreadStateReference fllThreadState = USE_GLOBAL_FLL ? threadStateProvider.newThreadStateReference() : null;

    private static final AtomicLong threadIds = new AtomicLong();
    public final long threadId = threadIds.incrementAndGet();

    public volatile boolean dirty = false;
    private final LayoutLock.Accessor layoutLockAccessor;

    private final HashMap<DynamicObject, ThreadStateReference> lockStates = new HashMap<>();

    private ThreadStateReference last = null;
    private DynamicObject lastObject = null;

    public ThreadWithDirtyFlag(Runnable runnable) {
        super(runnable);
        this.layoutLockAccessor = LayoutLock.GLOBAL_LOCK.access();
        if (USE_GLOBAL_FLL) {
            GLOBAL_LOCK.registerThread(fllThreadState);
        }
    }

    public LayoutLock.Accessor getLayoutLockAccessor() {
        return layoutLockAccessor;
    }

    public ThreadStateReference getThreadState(DynamicObject array, ConditionProfile fastPathProfile) {
        if (USE_GLOBAL_FLL) {
            return fllThreadState;
        } else {
            if (fastPathProfile.profile(lastObject == array)) {
                return last;

            }
            return getThreadStateSlowPath(array);
        }
    }

    public ThreadStateReference getThreadState(DynamicObject array) {
        if (USE_GLOBAL_FLL) {
            return fllThreadState;
        } else {
            return (array == lastObject) ? last : getThreadStateSlowPath(array);
        }
    }

    @TruffleBoundary
    private ThreadStateReference getThreadStateSlowPath(DynamicObject array) {
        ThreadStateReference ts = lockStates.get(array);
        if (ts == null) {
            FastLayoutLockArray fastLayoutLockArray = (FastLayoutLockArray) Layouts.ARRAY.getStore(array);
            FastLayoutLock lock = fastLayoutLockArray.getLock();
            ts = threadStateProvider.newThreadStateReference();
            lock.registerThread(ts);
            lockStates.put(array, ts);
        }
        lastObject = array;
        last = ts;
        return ts;
    }

    public void cleanup() {
        for (Entry<DynamicObject, ThreadStateReference> entry : lockStates.entrySet()) {
            FastLayoutLock lock = ((FastLayoutLockArray) Layouts.ARRAY.getStore(entry.getKey())).getLock();
            lock.unregisterThread(entry.getValue());
        }
    }

    public long getThreadId() {
        return threadId;
    }
}
