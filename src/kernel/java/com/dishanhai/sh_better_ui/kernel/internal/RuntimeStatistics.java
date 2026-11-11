package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Objects;

final class RuntimeStatistics {
    private static final Sample SKIPPED = new Sample(null, null, 0, 0, -1, 0);
    private final Thread owner = Thread.currentThread();
    private volatile RuntimeMetrics.Configuration configuration = RuntimeMetrics.Configuration.DISABLED;
    private final EnumMap<RuntimeMetrics.Category, Counter> counters = new EnumMap<>(RuntimeMetrics.Category.class);
    private AllocationReader allocations;
    private long generation;
    private long cacheHits;
    private long cacheMisses;

    RuntimeStatistics() { clear(); }

    RuntimeMetrics view(ModuleAccess access) {
        return new RuntimeMetrics() {
            @Override public Snapshot snapshot() { return RuntimeStatistics.this.snapshot(); }
            @Override public void configure(Configuration next) {
                authorize();
                RuntimeStatistics.this.configure(next);
            }
            @Override public void reset() {
                authorize();
                synchronized (RuntimeStatistics.this) { clear(); }
            }
            private void authorize() {
                assertOwner();
                access.assertSideEffectAllowed();
                access.require(UIPermission.ACCESS_INTERNAL_DIAGNOSTICS, access.defaultScope());
            }
        };
    }

    synchronized void configure(RuntimeMetrics.Configuration next) {
        assertOwner();
        Objects.requireNonNull(next, "configuration");
        if (next.equals(configuration)) return;
        configuration = next;
        if (next.enabled() && next.allocations() && allocations == null) allocations = AllocationReader.open();
        clear();
    }

    Sample begin(RuntimeMetrics.Category category) {
        if (!configuration.enabled()) return SKIPPED;
        synchronized (this) {
            if (!configuration.enabled()) return SKIPPED;
            var counter = counters.get(category);
            long operation = counter.operations++;
            if (operation % configuration.sampleEvery() != 0) return SKIPPED;
            long thread = Thread.currentThread().getId();
            long bytes = allocated(thread);
            return new Sample(this, category, generation, System.nanoTime(), bytes, thread);
        }
    }

    void failure(RuntimeMetrics.Category category) {
        if (!configuration.enabled()) return;
        synchronized (this) {
            if (configuration.enabled()) counters.get(category).failures++;
        }
    }

    void cache(boolean hit) {
        if (!configuration.enabled()) return;
        synchronized (this) {
            if (!configuration.enabled()) return;
            counters.get(RuntimeMetrics.Category.CACHE).operations++;
            if (hit) cacheHits++; else cacheMisses++;
        }
    }

    private synchronized void finish(Sample sample, long elapsed, long allocated) {
        if (sample.generation != generation || !configuration.enabled()) return;
        var counter = counters.get(sample.category);
        counter.samples++;
        counter.totalNanos = add(counter.totalNanos, elapsed);
        counter.maximumNanos = Math.max(counter.maximumNanos, elapsed);
        counter.window[counter.cursor] = elapsed;
        counter.cursor = (counter.cursor + 1) % counter.window.length;
        counter.size = Math.min(counter.window.length, counter.size + 1);
        if (allocated >= 0) {
            counter.allocationSamples++;
            counter.allocatedBytes = add(counter.allocatedBytes, allocated);
        }
    }

    private long allocated(long thread) {
        return configuration.allocations() && allocations != null ? allocations.read(thread) : -1;
    }

    private synchronized RuntimeMetrics.Snapshot snapshot() {
        var measurements = new EnumMap<RuntimeMetrics.Category, RuntimeMetrics.Measurement>(RuntimeMetrics.Category.class);
        counters.forEach((category, counter) -> {
            var sorted = Arrays.copyOf(counter.window, counter.size);
            Arrays.sort(sorted);
            measurements.put(category, new RuntimeMetrics.Measurement(counter.operations, counter.samples,
                    counter.failures, counter.totalNanos, counter.maximumNanos, percentile(sorted, 0.50),
                    percentile(sorted, 0.95), percentile(sorted, 0.99), counter.allocationSamples,
                    counter.allocationSamples == 0 ? -1 : counter.allocatedBytes));
        });
        return new RuntimeMetrics.Snapshot(configuration, generation, allocations != null && allocations.supported,
                cacheHits, cacheMisses, measurements);
    }

    private void clear() {
        generation++;
        cacheHits = 0;
        cacheMisses = 0;
        counters.clear();
        for (var category : RuntimeMetrics.Category.values()) {
            counters.put(category, new Counter(configuration.enabled() ? configuration.windowSamples() : 0));
        }
    }

    private void assertOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Runtime metrics require the UI thread");
    }

    private static long percentile(long[] sorted, double percentile) {
        return sorted.length == 0 ? 0 : sorted[(int) Math.ceil(sorted.length * percentile) - 1];
    }

    private static long add(long left, long right) { return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right; }

    static final class Sample implements AutoCloseable {
        private RuntimeStatistics statistics;
        private final RuntimeMetrics.Category category;
        private final long generation;
        private final long started;
        private final long allocated;
        private final long thread;

        private Sample(RuntimeStatistics statistics, RuntimeMetrics.Category category, long generation,
                       long started, long allocated, long thread) {
            this.statistics = statistics;
            this.category = category;
            this.generation = generation;
            this.started = started;
            this.allocated = allocated;
            this.thread = thread;
        }

        @Override public void close() {
            var target = statistics;
            if (target == null) return;
            if (Thread.currentThread().getId() != thread) {
                throw new IllegalStateException("Runtime sample must close on its originating thread");
            }
            statistics = null;
            long elapsed = Math.max(0, System.nanoTime() - started);
            long after = target.allocated(thread);
            target.finish(this, elapsed, allocated < 0 || after < allocated ? -1 : after - allocated);
        }
    }

    private static final class Counter {
        final long[] window;
        int cursor;
        int size;
        long operations;
        long samples;
        long failures;
        long totalNanos;
        long maximumNanos;
        long allocationSamples;
        long allocatedBytes;

        Counter(int size) { window = new long[size]; }
    }

    private static final class AllocationReader {
        private final java.lang.invoke.MethodHandle read;
        private volatile boolean supported = true;

        private AllocationReader(Object bean, Method read) throws IllegalAccessException {
            this.read = java.lang.invoke.MethodHandles.publicLookup().unreflect(read).bindTo(bean);
        }

        static AllocationReader open() {
            try {
                var management = Class.forName("java.lang.management.ManagementFactory");
                var bean = management.getMethod("getThreadMXBean").invoke(null);
                var extension = Class.forName("com.sun.management.ThreadMXBean");
                if (!extension.isInstance(bean)
                        || !Boolean.TRUE.equals(extension.getMethod("isThreadAllocatedMemorySupported").invoke(bean))) return null;
                if (!Boolean.TRUE.equals(extension.getMethod("isThreadAllocatedMemoryEnabled").invoke(bean))) return null;
                return new AllocationReader(bean, extension.getMethod("getThreadAllocatedBytes", long.class));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
                return null;
            }
        }

        long read(long thread) {
            if (!supported) return -1;
            try {
                return (long) read.invokeExact(thread);
            } catch (Throwable unavailable) {
                if (unavailable instanceof VirtualMachineError fatal) throw fatal;
                if (unavailable instanceof ThreadDeath fatal) throw fatal;
                supported = false;
                return -1;
            }
        }
    }
}
