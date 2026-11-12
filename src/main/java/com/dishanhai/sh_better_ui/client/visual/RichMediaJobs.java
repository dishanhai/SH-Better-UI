// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Limits queued, running and completed results across mounted views. */
final class RichMediaJobs {
    private static final Pool LOCAL = new Pool("pack", 8);
    private static final Pool REMOTE = new Pool("https", 4);

    private RichMediaJobs() {}

    static <T extends AutoCloseable> Job<T> submit(Callable<T> work, Runnable cancel, boolean remote) {
        return (remote ? REMOTE : LOCAL).submit(work, cancel);
    }

    private static final class Pool {
        private final ThreadPoolExecutor workers;
        private final Semaphore slots;

        private Pool(String name, int capacity) {
            slots = new Semaphore(capacity);
            workers = new ThreadPoolExecutor(2, 2, 10, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(capacity), task -> {
                var thread = new Thread(task, "shb-rich-media-" + name);
                thread.setDaemon(true);
                return thread;
            });
            workers.allowCoreThreadTimeOut(true);
        }

        private <T extends AutoCloseable> Job<T> submit(Callable<T> work, Runnable cancel) {
            if (!slots.tryAcquire()) return null;
            var job = new Job<>(this, work, cancel);
            try {
                workers.execute(job.task);
                return job;
            } catch (RejectedExecutionException full) {
                job.cancel();
                return null;
            }
        }
    }

    static final class Job<T extends AutoCloseable> {
        private final Pool pool;
        private final Runnable abort;
        private final FutureTask<Void> task;
        private T result;
        private boolean ready;
        private boolean released;

        private Job(Pool pool, Callable<T> work, Runnable abort) {
            this.pool = pool;
            this.abort = abort;
            task = new FutureTask<>(() -> {
                T value = null;
                try {
                    value = work.call();
                } finally {
                    finish(value);
                }
                return null;
            });
        }

        private synchronized void finish(T value) {
            if (released) dispose(value);
            else result = value;
            ready = true;
        }

        synchronized boolean ready() { return ready; }

        synchronized T take() {
            if (!ready || released) throw new IllegalStateException("Inline image job has no result to transfer");
            T value = result;
            result = null;
            releaseSlot();
            return value;
        }

        void cancel() {
            synchronized (this) {
                if (released) return;
                dispose(result);
                result = null;
                releaseSlot();
            }
            abort.run();
            task.cancel(true);
            pool.workers.remove(task);
        }

        private void releaseSlot() {
            released = true;
            pool.slots.release();
        }

        private static void dispose(AutoCloseable value) {
            if (value != null) {
                try {
                    value.close();
                } catch (Exception ignored) {
                    // A cancelled job has no consumer.
                }
            }
        }
    }
}
