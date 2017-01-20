package org.truffleruby.core.array.layout;

import java.util.concurrent.locks.ReentrantLock;

import org.truffleruby.RubyContext;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public class MyBiasedLock {

    // static final AtomicReferenceFieldUpdater<MyBiasedLock, Thread> BIASED_THREAD_UPDATER = AtomicReferenceFieldUpdater.newUpdater(MyBiasedLock.class, Thread.class, "biasedThread");

    @CompilationFinal private volatile Thread biasedThread = Thread.currentThread();
    @CompilationFinal private volatile ReentrantLock fullLock = null;

    // boolean locked = false;

    public void lock(RubyContext context) {
        final Thread biasedThread = this.biasedThread;
        if (biasedThread == Thread.currentThread()) {
            // Needs barrier?
            // locked = true;
        } else if (biasedThread == null) {
            doLock();
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // TODO: wait until biasedThread gets out of the lock! How?
            // Safepoint to find out? Keep "locked" boolean?
            // Guest-Lang Safepoint with knowledge we are not in a lock when reaching a GLSP
            final Thread thread = Thread.currentThread();
            context.getSafepointManager().pauseAllThreadsAndExecute(null, false, (rubyThread, currentNode) -> {
                // By the time all threads reach this safepoint, we know they are not holding a MyBiasedLock,
                // because it is not used across guest-language safepoint checks.
                if (Thread.currentThread() == thread) {
                    this.biasedThread = null;
                    this.fullLock = new ReentrantLock();
                }
            });
            // if (BIASED_THREAD_UPDATER.compareAndSet(this, biasedThread, null)) {
            // fullLock = new ReentrantLock();
            // }
            doLock();
        }
    }

    @TruffleBoundary
    private void doLock() {
        fullLock.lock();
    }

    public void unlock() {
        // Needs barrier?
        final Thread biasedThread = this.biasedThread;
        if (biasedThread == null) {
            doUnlock();
        } else {
            // locked = false;
        }
    }

    @TruffleBoundary
    private void doUnlock() {
        fullLock.unlock();
    }

}
