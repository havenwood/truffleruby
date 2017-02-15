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
    static final int READER_ACTIVE = 1;
    static final int WRITER_ACTIVE = -1;
    AtomicInteger writerActive = new AtomicInteger(0);

    HashMap<Long, ThreadInfo> threads = new HashMap<>();

    // to be used with startLayoutChange/finishLayoutChange
    ThreadInfo gather[];

    class ThreadInfo {
        public AtomicBoolean dirty = new AtomicBoolean(false);
        public AtomicInteger state = new AtomicInteger(INACTIVE);
        public AtomicInteger writeIntended = new AtomicInteger(0);
        int activeReaders = 0;
    }

    ThreadInfo getThreadInfo(long tid) {
        long stamp;
        do {
            while ((writerActive.get() & 1) != 0)
                ; // layout change is happening
            stamp = writerActive.get();
            if ((stamp & 1) == 1)
                continue;
            ThreadInfo ti = threads.get(tid);
            if (stamp != writerActive.get())
                continue;
            return ti;
        } while (true);
    }

    public ThreadInfo registerThread(long tid) {
        ThreadInfo ti = getThreadInfo(tid);
        if (ti != null)
            return ti;
        // registering a thread must block layout changes while registering
        int c = writerActive.get();
        while ((c & 1) == 1 || !writerActive.compareAndSet(c, c + 1))
            c = writerActive.get();
        if ((writerActive.get() & 1) == 0) {
            System.err.println("Bad lock(2) " + writerActive.get() + ", c = " + c);
        }

        ti = new ThreadInfo();
        threads.put(tid, ti);
        writerActive.getAndIncrement();
        return ti;
    }

    public void reset() {
        gather = null;
        threads = new HashMap<>();
        writerActive.set(0);
    }

    public void startRead(int tid) {
    }

    public void startRead(ThreadInfo ti) {
    }

    public boolean finishRead(int tid) {
        ThreadInfo ti = getThreadInfo(tid);
        return finishRead(ti);
    }

    public boolean finishRead(ThreadInfo ti) {
        if (ti.dirty.get()) {
            AtomicInteger state = ti.state;
            while (state.get() == WRITER_ACTIVE)
                ;
            ti.dirty.set(false);
            if (state.get() == WRITER_ACTIVE)
                ti.dirty.set(true);
            return false;
        }
        return true;
    }

    public void startWrite(long tid) {
        ThreadInfo ti = getThreadInfo(tid);
        startWrite(ti);
    }

    public void startWrite(ThreadInfo ti) {
        if (ti.activeReaders > 0) {
            ti.activeReaders++;
            return;
        }
        AtomicInteger writeIntended = ti.writeIntended;
        AtomicInteger state = ti.state;
        while (writeIntended.get() > 0 || !state.compareAndSet(INACTIVE, READER_ACTIVE))
            ;
        ti.activeReaders = 1;
    }

    public void finishWrite(long tid) {
        ThreadInfo ti = getThreadInfo(tid);
        finishWrite(ti);
    }

    public void finishWrite(ThreadInfo ti) {
        if (ti.activeReaders-- == 1)
            ti.state.set(INACTIVE);
    }

    public void startLayoutChange() {
        int c = writerActive.get();
        while ((c & 1) == 1 || !writerActive.compareAndSet(c, c + 1))
            c = writerActive.get();
        // if (false) {
        // if ((writerActive.get() & 1) == 0) {
        // System.err.println("Bad lock " + writerActive.get() + ", c = " + c);
        // }
        // }
        // use gather scatter
        // layout-change preferring: announce intent to layout change, to block writers
        // as a side-effect, gather registered threads for next stages
        if (gather == null || gather.length != threads.size()) {
            int next = 0;
            gather = new ThreadInfo[threads.size()];
            for (ThreadInfo ti : threads.values()) {
                gather[next++] = ti;
            }
        }       // switch all registered thread-infos to WRITER_ACTIVE
        for (int i = 0; i < gather.length; i++) {
            ThreadInfo ti = gather[i];
            ti.writeIntended.getAndIncrement();
            AtomicInteger state = ti.state;
            if (state.get() != WRITER_ACTIVE)
                while (!state.compareAndSet(INACTIVE, WRITER_ACTIVE))
                    ;
        }
        // after all states successfully switched to WRITER_ACTIVE, reduce intent to write, and mark
        // dirty bits
        for (int i = 0; i < gather.length; i++) {
            ThreadInfo ti = gather[i];
            ti.writeIntended.decrementAndGet();
            ti.dirty.set(true);
        }
        // TODO layout changer will not turn off LC flag
        // TODO writer that sees LC flag will need to somehow turn it off if
        // TODO writerActive & 1 == 0 (race?)

    }

    public void finishLayoutChange() {
        // if (false) {
        // if (gather == null) {
        // System.err.println("gather is null " + writerActive.get() + " for lock " + locknum);
        // return;
        // }
        // for (int i = 0; i < gather.length; i++) {
        // gather[i].state.set(INACTIVE);
        // }
        // }

        writerActive.getAndIncrement();

    }

    public boolean isWriterActive() {
        return (writerActive.get() & 1) != 0;
    }

    public Accessor access(long tid) {
        return new Accessor(this, tid);
    }

    public Accessor access() {
        return new Accessor(this, Thread.currentThread().getId());
    }

    // simplify access through use of accessors. But lock could be used either way, compared to
    // LayoutLock
    public class Accessor {
        FastLayoutLock lock;
        ThreadInfo ti;
        long tid = -1;

        public Accessor(FastLayoutLock lock, long tid) {
            this.tid = tid;
            this.lock = lock;
            this.ti = lock.registerThread(tid);
        }

        public Accessor(FastLayoutLock lock) {
            // this(lock, Thread.currentThread().getId());
            tid = Thread.currentThread().getId();
            this.lock = lock;
            this.ti = lock.registerThread(tid);
        }

        public boolean isWriterActive() {
            return lock.isWriterActive();
        }

        public void startRead() {
        }

        public boolean finishRead() {
            return lock.finishRead(ti);
        }

        public void startLayoutChange() {
            lock.startLayoutChange();
        }

        public void finishLayoutChange() {
            lock.finishLayoutChange();
        }

        public void startWrite() {
            lock.startWrite(ti);
        }

        public void finishWrite() {
            lock.finishWrite(ti);
        }

        public boolean isDirty() {
            return ti.dirty.get();
        }
    }

    public String getDescription() {
        return "FastLayoutLock";
    }
}
