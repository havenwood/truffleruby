package org.javaexperiments;

import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public final class ConcurrentLinkedList {

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

    final AtomicReferenceArray<Entry> first = new AtomicReferenceArray<>(1);

    public ConcurrentLinkedList() {
    }

    public void append(Object key) {
        final Entry newEntry = new Entry(key);
        final int index = 0;

        Entry head;
        do {
            head = first.get(0);
            newEntry.setNext(head);
        } while (!first.compareAndSet(index, head, newEntry));
    }

    public boolean delete(Object key) {
        Entry entry = searchEntry(key);
        if (entry == null) {
            return false;
        }

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

        while (true) {
            final Entry prev = searchPreviousEntry(entry);
            if (prev == null) {
                if (first.compareAndSet(0, entry, next)) {
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

        return true;
    }

    private Entry searchEntry(Object key) {
        Entry entry = first.get(0);
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
        Entry e = first.get(0);

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
        ConcurrentLinkedList list = new ConcurrentLinkedList();

        Thread[] appenders = new Thread[n];
        for (int i = 0; i < appenders.length; i++) {
            final int e = i;
            appenders[i] = new Thread(() -> {
                while (!go) {
                    Thread.yield();
                }
                list.append(e);
            });
            appenders[i].start();
        }

        Thread[] removers = new Thread[n];
        for (int i = 0; i < removers.length; i++) {
            final int e = i;
            removers[i] = new Thread(() -> {
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
