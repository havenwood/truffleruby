package org.truffleruby.core.array.layout;

import java.util.HashMap;
import java.util.Map.Entry;

import org.truffleruby.Layouts;
import org.truffleruby.core.array.ConcurrentArray.FastLayoutLockArray;
import org.truffleruby.core.array.ConcurrentArray.LayoutLockArray;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.ConditionProfile;

public class ThreadWithDirtyFlag extends Thread {

    public final static boolean USE_GLOBAL_FLL = false;
    public final static boolean USE_GLOBAL_LL = false;

    private final ThreadStateProvider threadStateProvider = new ThreadStateProvider();
    private static final FastLayoutLock GLOBAL_LOCK = USE_GLOBAL_FLL ? new FastLayoutLock() : null;
    private final ThreadStateReference fllThreadState = USE_GLOBAL_FLL ? threadStateProvider.newThreadStateReference() : null;

    public volatile boolean dirty = false;
    private final LayoutLock.Accessor layoutLockAccessor = LayoutLock.GLOBAL_LOCK.access();

    private final HashMap<DynamicObject, ThreadStateReference> lockStates = new HashMap<>();
    private final HashMap<Object, LayoutLock.Accessor> lockAccessors = new HashMap<>();

    private ThreadStateReference last = null;
    private LayoutLock.Accessor lastAccessor = null;
    private DynamicObject lastObject = null;

    public ThreadWithDirtyFlag(Runnable runnable) {
        super(runnable);
        if (USE_GLOBAL_FLL) {
            GLOBAL_LOCK.registerThread(fllThreadState);
        }
    }

    public LayoutLock.Accessor getLayoutLockAccessor(DynamicObject array, ConditionProfile fastPathProfile) {
        if (USE_GLOBAL_LL) {
            return layoutLockAccessor;
        } else {
            if (fastPathProfile.profile(lastObject == array)) {
                return lastAccessor;
            }
            return getLayoutLockAccessorSlowPath(array);
        }
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

    @TruffleBoundary
    private LayoutLock.Accessor getLayoutLockAccessorSlowPath(DynamicObject array) {
        System.err.println("slow path");
        LayoutLock.Accessor accessor = lockAccessors.get(array);
        if (accessor == null) {
            if (Layouts.ARRAY.getStore(array) instanceof LayoutLockArray) {
                accessor = ((LayoutLockArray) Layouts.ARRAY.getStore(array)).getLock().access();
            } else {
                accessor = layoutLockAccessor;
            }
            lockAccessors.put(array, accessor);
        }
        lastObject = array;
        lastAccessor = accessor;
        return accessor;
    }

    public void cleanup() {
        for (Entry<DynamicObject, ThreadStateReference> entry : lockStates.entrySet()) {
            FastLayoutLock lock = ((FastLayoutLockArray) Layouts.ARRAY.getStore(entry.getKey())).getLock();
            lock.unregisterThread(entry.getValue());
        }
    }

}
