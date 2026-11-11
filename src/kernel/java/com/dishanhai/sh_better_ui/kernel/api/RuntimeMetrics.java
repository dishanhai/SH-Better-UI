package com.dishanhai.sh_better_ui.kernel.api;

import java.util.Map;
import java.util.Objects;

public interface RuntimeMetrics {
    enum Category { MEASURE, LAYOUT, ANIMATION, EVENT, RENDER, CACHE }

    record Configuration(boolean enabled, int sampleEvery, int windowSamples, boolean allocations) {
        public static final Configuration DISABLED = new Configuration(false, 1, 1024, false);

        public Configuration {
            if (sampleEvery < 1 || sampleEvery > 1_000_000) {
                throw new IllegalArgumentException("Sampling interval must be between 1 and 1000000");
            }
            if (windowSamples < 16 || windowSamples > 65536) {
                throw new IllegalArgumentException("Sample window must be between 16 and 65536");
            }
        }
    }

    record Measurement(long operations, long samples, long failures, long totalNanos,
                       long maximumNanos, long p50Nanos, long p95Nanos, long p99Nanos,
                       long allocationSamples, long allocatedBytes) {}

    record Snapshot(Configuration configuration, long generation, boolean allocationSupported,
                    long cacheHits, long cacheMisses, Map<Category, Measurement> measurements) {
        public Snapshot {
            Objects.requireNonNull(configuration, "configuration");
            measurements = Map.copyOf(measurements);
        }
    }

    Snapshot snapshot();

    void configure(Configuration configuration);

    void reset();
}
