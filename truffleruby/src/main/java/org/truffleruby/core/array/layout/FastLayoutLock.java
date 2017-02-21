package org.truffleruby.core.array.layout;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class FastLayoutLock {

    public static final FastLayoutLock GLOBAL_LOCK = new FastLayoutLock();

    static final int INACTIVE = 0;
    static final int WRITER_ACTIVE = -11;
    static final int LAYOUT_CHANGE = 1;


    public class ThreadState {
        final AtomicInteger state = new AtomicInteger(INACTIVE);
        volatile ThreadState next = null;
        volatile boolean is_locked = false;
    }

    class ts_queue {
        final AtomicReference<ThreadState> queue = new AtomicReference<>();

        // return true iff node was unlocked by previous node
        boolean lock(ThreadState node) {
            node.next = null;
            ThreadState predecessor = queue.getAndSet(node);

            if (predecessor != null) {
                node.is_locked = true;
                predecessor.next = node;
                while (node.is_locked) {
                    LayoutLock.yield();
                }
                return true;
            }
            return false;
        }

        void unlock(ThreadState node) {
            if (node.next == null) {
                if (queue.compareAndSet(node, null))
                    return;
                while (node.next == null) {
                    LayoutLock.yield();
                }
            }
            node.next.is_locked = false;
        }
    }

    public final ts_queue queue = new ts_queue();

    final HashMap<Long, ThreadState> threadStates = new HashMap<>();
    final ThreadState lockState = new ThreadState();

    public volatile ThreadState gather[] = new ThreadState[1];

    public FastLayoutLock() {
        gather[0] = lockState;
    }

    ThreadState getThreadState(long tid) {
        ThreadState ts;
        do {
            ts = threadStates.get(tid);
        } while (!finishRead(lockState));
        return ts;
    }



    public void startLayoutChange(ThreadState ts) {
        boolean unlocked = queue.lock(ts);
        if (!unlocked) {
            for (int i = 0; i < gather.length; i++) {
                AtomicInteger state = gather[i].state;
                if (state.get() != LAYOUT_CHANGE)
                    while (!state.compareAndSet(INACTIVE, LAYOUT_CHANGE)) {
                        LayoutLock.yield();
                    }
            }

        }
    }

    public void finishLayoutChange(ThreadState ts) {
        queue.unlock(ts);
    }


    public void startWrite(ThreadState ts) {
        if (!ts.state.compareAndSet(INACTIVE, WRITER_ACTIVE)) { // check for fast path
            // LC must be on, so switch to slow path
            do { // wait for queue to empty
                while (queue.queue.get() != null) { // <=== contention on queue head
                    LayoutLock.yield();
                }
                ts.state.set(INACTIVE);
                // restore to layout change state if one just started, after we set the flag to
                // inactive
                if (queue.queue.get() != null)
                    ts.state.set(LAYOUT_CHANGE);
                // if LC comes in now, it will have to be a first, so it may try to turn on
                // LC flags
            } while (!ts.state.compareAndSet(INACTIVE, WRITER_ACTIVE));
        }

    }

    public void finishWrite(ThreadState ts) {
        ts.state.set(INACTIVE);
    }

    public boolean finishRead(ThreadState ts) {
        if (ts.state.get() == INACTIVE) // check for fast path
            return true;
        // slow path
        while (queue.queue.get() != null) {
            LayoutLock.yield();
        }
        ts.state.set(INACTIVE);
        if (queue.queue.get() != null)
            ts.state.set(LAYOUT_CHANGE);
        return false;
    }


    private void updateGather() {
        gather = new ThreadState[threadStates.size() + 1];
        gather[0] = lockState;
        int next = 1;
        for (ThreadState ts : threadStates.values())
            gather[next++] = ts;
    }

    // block layout changes, but allow other changes to proceed
    public ThreadState registerThread(long tid) {
        ThreadState ts = new ThreadState();
        // System.err.println("call startLayoutChange");
        startLayoutChange(lockState);
        // System.err.println("after call startLayoutChange " + counts.get());
        threadStates.put(tid, ts);
        updateGather();
        // finish the layout change, no need to reset the LC flags
        // System.err.println("call finishLayoutChange");
        finishLayoutChange(lockState);
        // System.err.println("after call finishLayoutChange " + counts.get());
        return ts;
    }

    // block layout changes, but allow other changes to proceed
    public void unregisterThread() {
        long tid = ((ThreadWithDirtyFlag) Thread.currentThread()).getThreadId();
        startLayoutChange(lockState);
        threadStates.remove(tid);
        updateGather();
        finishLayoutChange(lockState);
    }

    public void reset() {
        threadStates.clear();
        gather = new ThreadState[1];
        gather[0] = lockState;
    }

    public void startRead(AtomicInteger ts) {
    }

    public String getDescription() {
        return "FastLayoutLock";
    }
}
