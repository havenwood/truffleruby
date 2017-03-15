package org.javaexperiments;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.truffleruby.core.array.layout.LayoutLock.Accessor;
import org.truffleruby.core.array.layout.ThreadWithDirtyFlag;

import com.oracle.truffle.api.profiles.ConditionProfile;

public final class ConcurrentLinkedListResizing {
    static final class Entry {

        private static final AtomicReferenceFieldUpdater<Entry, Entry> NEXT_UPDATER =
                AtomicReferenceFieldUpdater.newUpdater(Entry.class, Entry.class, "next");

        final Object key;
        volatile Entry next;
        final boolean removed;

        public Entry(Object key) {
            this.key = key;
            this.removed = false;
        }

        public Entry(boolean removed, Entry next) {
            this.key = null;
            this.next = next;
            this.removed = removed;
        }

        public Entry getNext() {
            return next;
        }

        public void setNext(Entry next) {
            this.next = next;
        }

        public boolean compareAndSetNext(Entry expected, Entry newNext) {
            return NEXT_UPDATER.compareAndSet(this, expected, newNext);
        }

        public boolean isRemoved() {
            return removed;
        }

    }

    private static final ConditionProfile DUMMY_PROFILE = ConditionProfile.createBinaryProfile();

    final AtomicInteger size = new AtomicInteger(0);
    volatile AtomicReferenceArray<Entry> first = new AtomicReferenceArray<>(1);
    volatile int bucket = 0;

    public ConcurrentLinkedListResizing() {
    }

    private void resize() {
        final Accessor accessor = getAccessor();
        int threads = accessor.startLayoutChange();
        try {
            bucket++;
            final AtomicReferenceArray<Entry> old = first;
            first = new AtomicReferenceArray<>(bucket + 1);
            first.set(bucket, old.get(bucket - 1));
        } finally {
            accessor.finishLayoutChange(threads);
        }
    }

    private Accessor getAccessor() {
        // FIXME return ((ThreadWithDirtyFlag) Thread.currentThread()).getLayoutLockAccessor(this,
        // DUMMY_PROFILE);
        return null;
    }

    public void append(Object key) {
        final Entry newEntry = new Entry(key);

        final Accessor accessor = getAccessor();
        accessor.startWrite();
        try {
            Entry head;
            do {
                head = first.get(bucket);
                newEntry.setNext(head);
            } while (!first.compareAndSet(bucket, head, newEntry));

            size.incrementAndGet();
        } finally {
            accessor.finishWrite();
        }

        resize();
    }

    public boolean delete(Object key) {
        final Accessor accessor = getAccessor();

        Entry entry = searchEntry(key, accessor);

        if (entry == null) {
            return false;
        }

        // Need to start the lock here, otherwise a first thread might mark it as removed,
        // and the thread deleting the next element will keep its write lock while waiting for full
        // removal, preventing LC, while waiting for the first thread to try to acquire a write lock
        // (which it can't as LC won the CAS for that first thread)
        accessor.startWrite();
        try {
            // Prevent insertions after entry so adjacent concurrent deletes serialize
            Entry next, nextDeleted;
            do {
                next = entry.getNext();
                if (next != null && next.isRemoved()) {
                    // Concurrent delete on entry
                    return false;
                }
                nextDeleted = new Entry(true, next);
            } while (!entry.compareAndSetNext(next, nextDeleted));

            size.decrementAndGet();

            while (true) {
                final Entry prev = searchPreviousEntry(entry);
                if (prev == null) {
                    if (first.compareAndSet(bucket, entry, next)) {
                        break;
                    }
                } else if (!prev.isRemoved()) {
                    if (prev.compareAndSetNext(entry, next)) {
                        break;
                    }
                } else {
                    // Concurrent delete on prev, wait for its complete removal
                    Thread.yield();
                }
            }
        } finally {
            accessor.finishWrite();
        }

        return true;
    }

    private Entry searchEntry(Object key, Accessor accessor) {
        Entry entry = null;
        AtomicReferenceArray<Entry> store;
        int index;
        do {
            store = first;
            index = bucket;
            if (index < store.length()) {
                entry = store.get(index);
            }
        } while (!accessor.finishRead(DUMMY_PROFILE));
        assert index + 1 == store.length();

        while (entry != null) {
            if (!entry.isRemoved() && entry.key.equals(key)) {
                return entry;
            }
            entry = entry.getNext();
        }

        return null;
    }

    private Entry searchPreviousEntry(Entry entry) {
        Entry previousEntry = null;
        Entry e = first.get(bucket);

        while (e != null) {
            if (e == entry) {
                return previousEntry;
            }

            previousEntry = e;
            e = e.getNext();
        }

        throw new AssertionError("Could not find the previous entry in the bucket");
    }

    static volatile boolean go = false;

    public static void main(String[] args) {
        int n = Integer.valueOf(args[0]);
        try {
            runMain(n);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    static void runMain(int n) throws InterruptedException {
        ConcurrentLinkedListResizing list = new ConcurrentLinkedListResizing();

        Thread[] appenders = new Thread[n];
        for (int i = 0; i < appenders.length; i++) {
            final int e = i;
            appenders[i] = new ThreadWithDirtyFlag(() -> {
                while (!go) {
                    Thread.yield();
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    throw new Error(ie);
                }
                list.append(e);
            });
            appenders[i].start();
        }

        Thread[] removers = new Thread[n];
        for (int i = 0; i < removers.length; i++) {
            final int e = i;
            removers[i] = new ThreadWithDirtyFlag(() -> {
                while (!go) {
                    Thread.yield();
                }
                while (!list.delete(e)) {
                    Thread.yield();
                }
                System.out.println(e);
            });
            removers[i].start();
        }

        Thread.sleep(1000);
        go = true;

        for (Thread appender : appenders) {
            appender.join();
        }
        for (Thread remover : removers) {
            remover.join();
        }
    }

}
