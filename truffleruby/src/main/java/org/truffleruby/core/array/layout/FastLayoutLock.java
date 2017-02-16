package org.truffleruby.core.array.layout;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class FastLayoutLock {
    public static final FastLayoutLock GLOBAL_LOCK = new FastLayoutLock();

    /*
     * Trying to convert the threads array into a map. HashMap (RB-Tree) would probably work best,
     * but it is not concurrent. The problem is that threads may keep registering while other
     * threads are trying to use the tree. So an incoming thread seeking its ThreadInfo object needs
     * to busy-wait until writeActive is *false*. As a reviewer suggested, convert writerActive to
     * an AtomicLong. Inc on layout change, and Inc again on finishing layout change.
     */
    static final int INACTIVE = 0;
    static final int WRITER_ACTIVE = 1;
    static final int LAYOUT_CHANGE = -1;
    public AtomicInteger startCount = new AtomicInteger(0);
    public AtomicInteger finishCount = new AtomicInteger(0);

    HashMap<Long, AtomicInteger> threadStates = new HashMap<>();

    public AtomicInteger gather[] = new AtomicInteger[0];

    AtomicInteger getThreadState(long tid) {
        do {
            long stamp = startCount.get();
            while (finishCount.get() < stamp)
                ; // layout change is happening
            AtomicInteger ts = threadStates.get(tid);
            if (stamp != startCount.get()) // did another layout change start?
                continue;
            return ts;
        } while (true);
    }

    private void notifyLayoutChange() {
        for (int i = 0; i < gather.length; i++) {
            AtomicInteger ts = gather[i];
            if (ts.get() != LAYOUT_CHANGE)
                while (!ts.compareAndSet(INACTIVE, LAYOUT_CHANGE))
                    ;
        }
    }

    public void startLayoutChange() {
        int s = startCount.incrementAndGet();
        while (s > finishCount.get() + 1)
            ; // wait for previous layout changes
        notifyLayoutChange();
    }

    public void finishLayoutChange() {
        finishCount.incrementAndGet();
    }

    private void updateGather() {
        gather = new AtomicInteger[threadStates.size()];
        int next = 0;
        for (AtomicInteger ts : threadStates.values())
            gather[next++] = ts;
    }

    // block layout changes, but allow other changes to proceed
    public AtomicInteger registerThread(long tid) {
        startLayoutChange();
        AtomicInteger ts = new AtomicInteger(LAYOUT_CHANGE); // since we're currently in a layout
                                                             // change
        threadStates.put(tid, ts);
        updateGather();
        // finish the layout change, no need to reset the LC flags
        finishLayoutChange();
        return ts;
    }

    // block layout changes, but allow other changes to proceed
    public void unregisterThread(long tid) {
        startLayoutChange();
        threadStates.remove(tid);
        updateGather();
        finishLayoutChange();
    }

    public void reset() {
        threadStates = new HashMap<>();
        startCount.set(0);
        finishCount.set(0);
    }

    public void startRead(AtomicInteger ts) {
    }

    public boolean finishRead(AtomicInteger ts) {
        if (ts.get() == LAYOUT_CHANGE) {
            while (startCount.get() > finishCount.get())
                ; // wait for layout changes to finish
            ts.set(INACTIVE);
            if (startCount.get() > finishCount.get()) // another one started and not finished
                ts.set(LAYOUT_CHANGE);
            return false;
        }
        return true;
    }

    public void startWrite(AtomicInteger ts) {
        if (!ts.compareAndSet(INACTIVE, WRITER_ACTIVE)) {
            do {
                while (startCount.get() > finishCount.get())
                    ; // wait for layout changes to finish
                ts.set(WRITER_ACTIVE);
                if (startCount.get() == finishCount.get())
                    return;
                ts.set(LAYOUT_CHANGE);
            } while (true);
        }
    }


    public void finishWrite(AtomicInteger ts) {
        ts.set(INACTIVE);
    }


    public String getDescription() {
        return "FastLayoutLock";
    }
}
