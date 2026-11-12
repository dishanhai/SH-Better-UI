// Author: dishanhai
package com.dishanhai.sh_better_ui.client.system;

import com.dishanhai.sh_better_ui.kernel.api.system.ComputerStatusService;
import com.sun.management.OperatingSystemMXBean;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import oshi.SystemInfo;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class ClientComputerStatus implements ComputerStatusService {
    public static final ClientComputerStatus INSTANCE = new ClientComputerStatus();

    private final Slot<Memory> memory = new Slot<>(1);
    private final Slot<Cpu> cpu = new Slot<>(1);
    private final Slot<Gpu> gpu = new Slot<>(3);
    private final Slot<Space> disk = new Slot<>(5);
    private final Slot<List<GraphicsAdapter>> adapters = new Slot<>(60);

    private ClientComputerStatus() {}

    @Override
    public Snapshot read(Set<Metric> metrics) {
        return new Snapshot(
                metrics.contains(Metric.MEMORY) ? memory.read(ClientComputerStatus::sampleMemory) : Optional.empty(),
                metrics.contains(Metric.CPU) ? cpu.read(ClientComputerStatus::sampleCpu) : Optional.empty(),
                metrics.contains(Metric.GPU) ? gpu.read(this::sampleGpu) : Optional.empty(),
                metrics.contains(Metric.DISK) ? disk.read(ClientComputerStatus::sampleDisk) : Optional.empty());
    }

    private static Memory sampleMemory() {
        var runtime = Runtime.getRuntime();
        var heap = new Space(runtime.totalMemory() - runtime.freeMemory(), runtime.maxMemory());
        var os = operatingSystem();
        return new Memory(heap, os == null ? Optional.empty()
                : space(os.getTotalMemorySize(), os.getFreeMemorySize()));
    }

    private static Cpu sampleCpu() {
        var os = operatingSystem();
        return new Cpu(Runtime.getRuntime().availableProcessors(),
                os == null ? OptionalDouble.empty() : load(os.getCpuLoad()),
                os == null ? OptionalDouble.empty() : load(os.getProcessCpuLoad()));
    }

    private Gpu sampleGpu() {
        var cards = adapters.read(ClientComputerStatus::sampleAdapters).orElse(List.of());
        OptionalDouble busy = SystemInfo.getCurrentPlatform() == oshi.PlatformEnum.WINDOWS
                ? WindowsGpuStatus.load() : OptionalDouble.empty();
        return cards.isEmpty() && busy.isEmpty() ? null : new Gpu(cards, busy);
    }

    private static List<GraphicsAdapter> sampleAdapters() {
        return new SystemInfo().getHardware().getGraphicsCards().stream().map(card -> {
            long bytes = card.getVRam();
            return new GraphicsAdapter(card.getName(), bytes > 0 ? OptionalLong.of(bytes) : OptionalLong.empty());
        }).toList();
    }

    private static Space sampleDisk() {
        try {
            var store = Files.getFileStore(Minecraft.getInstance().gameDirectory.toPath());
            return space(store.getTotalSpace(), store.getUnallocatedSpace()).orElse(null);
        } catch (IOException | SecurityException ignored) {
            return null;
        }
    }

    private static OperatingSystemMXBean operatingSystem() {
        return ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean os ? os : null;
    }

    private static Optional<Space> space(long total, long free) {
        return total > 0 && free >= 0 && free <= total ? Optional.of(new Space(total - free, total)) : Optional.empty();
    }

    private static OptionalDouble load(double value) {
        return value >= 0 && value <= 1 ? OptionalDouble.of(value) : OptionalDouble.empty();
    }

    private static final class Slot<T> {
        private final long interval;
        private Optional<T> last = Optional.empty();
        private long lastCompleted = System.nanoTime();
        private boolean sampled;
        private boolean pending;

        Slot(long seconds) {
            interval = TimeUnit.SECONDS.toNanos(seconds);
        }

        synchronized Optional<T> read(Supplier<T> sampler) {
            if (!pending && (!sampled || System.nanoTime() - lastCompleted >= interval)) {
                pending = true;
                Util.ioPool().execute(() -> sample(sampler));
            }
            return last;
        }

        private void sample(Supplier<T> sampler) {
            T value = null;
            try {
                value = sampler.get();
            } catch (RuntimeException | LinkageError ignored) {
                // Individual OS counters and native libraries may be unavailable.
            } finally {
                synchronized (this) {
                    last = Optional.ofNullable(value);
                    lastCompleted = System.nanoTime();
                    sampled = true;
                    pending = false;
                }
            }
        }
    }
}
