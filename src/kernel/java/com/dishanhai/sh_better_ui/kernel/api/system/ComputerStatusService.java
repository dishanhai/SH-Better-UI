// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.system;

import com.dishanhai.sh_better_ui.kernel.api.CapabilityKey;
import com.dishanhai.sh_better_ui.kernel.api.ServiceKey;
import com.dishanhai.sh_better_ui.kernel.api.UIService;
import com.dishanhai.sh_better_ui.kernel.api.UiId;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;

/** Read-only computer telemetry from the physical client. */
public interface ComputerStatusService extends UIService {
    ServiceKey<ComputerStatusService> KEY = new ServiceKey<>(
            new UiId("sh_better_ui", "computer_status"), ComputerStatusService.class, 1);
    CapabilityKey CAPABILITY = new CapabilityKey(KEY.id());

    enum Metric { MEMORY, CPU, GPU, DISK }

    /**
     * Returns the latest immutable readings for the requested metrics and queues stale readings for
     * background refresh. Empty fields include initial sampling and unsupported platform counters.
     */
    Snapshot read(Set<Metric> metrics);

    record Snapshot(Optional<Memory> memory, Optional<Cpu> cpu, Optional<Gpu> gpu, Optional<Space> disk) {}

    record Space(long usedBytes, long totalBytes) {}

    /** Heap total is the JVM's configured maximum. System memory is optional. */
    record Memory(Space heap, Optional<Space> system) {}

    /** Loads are fractions in [0, 1], normalized to all available logical processors. */
    record Cpu(int logicalProcessors, OptionalDouble systemLoad, OptionalDouble processLoad) {}

    record GraphicsAdapter(String name, OptionalLong vramBytes) {}

    /** Load is the busiest physical GPU engine across all adapters, where the OS exposes it. */
    record Gpu(List<GraphicsAdapter> adapters, OptionalDouble busiestEngineLoad) {
        public Gpu {
            adapters = List.copyOf(adapters);
        }
    }
}
