// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.benchmark.client;

import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentAssets;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation.BlendCurve;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;
import com.dishanhai.sh_better_ui.spatial.SpatialScene;
import com.dishanhai.sh_better_ui.spatial.SpatialUI;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import com.sun.management.ThreadMXBean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class NativeKernelBenchmark implements UIModule {
    private static final long SEED = 19316813;
    private static final UiId TREE = id("bench/tree");
    private static final UiId LAYOUT = id("bench/layout");
    private static final UiId ANIMATION = id("bench/animation");
    private static final ResourceLocation TEXTURE = ResourceLocation.tryParse("sh_better_ui:textures/gui/display_64/category.png");
    private static final int POINTERS_PER_FRAME = 16;
    private static final int RELOAD_WARMUP = 3;

    private enum Work {
        IDLE, DIRTY, ANIMATION, POINTER, THEME, CONFIG, CHURN, RENDER, RELOAD,
        SPATIAL, SPATIAL_VISUAL, SPATIAL_RELOAD, SPATIAL_FALLBACK
    }

    private record Scenario(String name, Work work, int count, SpatialBenchmark.Mode spatialMode) {
        Scenario(String name, Work work, int count) { this(name, work, count, null); }
    }

    private static final List<Scenario> SCENARIOS = List.of(
            new Scenario("idle-100", Work.IDLE, 100), new Scenario("idle-1000", Work.IDLE, 1000),
            new Scenario("idle-10000", Work.IDLE, 10000), new Scenario("dirty-100", Work.DIRTY, 100),
            new Scenario("dirty-1000", Work.DIRTY, 1000), new Scenario("dirty-10000", Work.DIRTY, 10000),
            new Scenario("animation-1000", Work.ANIMATION, 1000), new Scenario("pointer-1024", Work.POINTER, 1024),
            new Scenario("theme-1000", Work.THEME, 1000), new Scenario("config-64", Work.CONFIG, 64),
            new Scenario("screen-churn-100", Work.CHURN, 100), new Scenario("render-1000", Work.RENDER, 1000),
            new Scenario("reload", Work.RELOAD, 0));

    private static List<Scenario> spatialScenarios() {
        var list = new ArrayList<Scenario>();
        list.add(new Scenario("spatial-visual", Work.SPATIAL_VISUAL, 0));
        list.add(new Scenario("spatial-reload", Work.SPATIAL_RELOAD, 0));
        list.add(new Scenario("spatial-fallback", Work.SPATIAL_FALLBACK, 0));
        for (var mode : SpatialBenchmark.Mode.values()) {
            for (int count : new int[]{100, 500, 1000, 5000}) {
                list.add(new Scenario("spatial-" + mode.label() + "-" + count, Work.SPATIAL, count, mode));
            }
        }
        return list;
    }

    private static NativeKernelBenchmark running;
    private static boolean finished;
    private static long lastProgress;
    private ModuleContext module;
    private UIEngine engine;
    private Path output;
    private List<Scenario> scenarios;
    private int scenarioIndex;
    private Scenario scenario;
    private ProbeScreen screen;
    private RenderService render;
    private ResourcesService resources;
    private ThreadMXBean allocation;
    private java.lang.management.ThreadMXBean cpu;
    private boolean gpuAvailable;
    private boolean cpuAvailable;
    private boolean replayingPointer;
    private int warmup;
    private int samples;
    private int reloadSamples;
    private int iteration;
    private boolean stopped;
    private boolean benchmarkComplete;
    private SplittableRandom random;
    private final List<Row> rows = new ArrayList<>();
    private final List<ReloadRow> reloadRows = new ArrayList<>();
    private final List<ConfigKey<Integer>> keys = new ArrayList<>();
    private Row pending;
    private long frameStart;
    private long frameCpu;
    private long frameAllocated;
    private boolean frameOpen;
    private CompletableFuture<Void> reload;
    private long reloadStart;
    private long reloadAllocation;
    private long resourceGeneration;
    private long resourceApplied;
    private boolean recoveringSpatialShader;
    private long previousHits;
    private long previousMisses;
    private AlignmentAssets assets;

    @Override public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder().id(id("benchmark_client")).version("1.0.0")
                .claimResource(new ModuleDescriptor.ResourceClaim(ModuleDescriptor.ResourceKind.LAYOUT_MUTATION, LAYOUT))
                .claimResource(new ModuleDescriptor.ResourceClaim(ModuleDescriptor.ResourceKind.ANIMATION_PROPERTY, ANIMATION))
                .requestPermission(UIPermission.REGISTER_RENDER_PASS).build();
    }

    @Override public void register(ModuleContext context) {}

    @Override public void start(ModuleContext context) {
        module = context;
        running = this;
    }

    @Override public void stop(ModuleContext context) { stopped = true; }

    public static void beforeFrame() {
        var probe = running;
        if (finished || probe == null || probe.pending == null || probe.frameOpen
                || Minecraft.getInstance().screen != probe.screen) return;
        probe.frameAllocated = probe.allocated();
        probe.frameCpu = probe.cpuTime();
        probe.frameStart = System.nanoTime();
        probe.frameOpen = true;
    }

    public static void afterFrame() {
        String directory = System.getProperty("shb.kernel.bench.output");
        if (directory == null || finished) return;
        var client = Minecraft.getInstance();
        if (lastProgress == 0) lastProgress = System.nanoTime();
        try {
            require(System.nanoTime() - lastProgress < TimeUnit.MINUTES.toNanos(1), "Native benchmark stalled");
            if (running == null) {
                require(!(client.screen instanceof TitleScreen && client.getOverlay() == null), "Benchmark module was not discovered");
                return;
            }
            if (running.output == null) {
                if (!(client.screen instanceof TitleScreen) || client.getOverlay() != null) return;
                running.initialize(client, Path.of(directory));
            }
            running.advance(client);
        } catch (Exception | AssertionError failure) {
            System.err.println("KERNEL_NATIVE_BENCHMARK_FAILED");
            failure.printStackTrace();
            finished = true;
            client.stop();
        }
    }

    static void shutdown() {
        if (running != null && running.benchmarkComplete && running.stopped && !UnifiedUI.isAvailable()) {
            try {
                Files.writeString(running.output.resolve("stopped.txt"), "module stopped and facade withdrawn\n");
            } catch (IOException failure) {
                throw new IllegalStateException("Could not record benchmark shutdown", failure);
            }
        }
    }

    private void initialize(Minecraft client, Path directory) throws IOException {
        output = directory;
        warmup = count("warmup", 120, 8, 5000);
        samples = count("samples", 300, 1, 5000);
        reloadSamples = count("reload", 20, 1, 120);
        String filter = System.getProperty("shb.kernel.bench.filter", "");
        var names = List.of(filter.split(","));
        if (filter.isEmpty()) {
            scenarios = SCENARIOS;
        } else if (filter.equals("spatial")) {
            scenarios = spatialScenarios();
        } else {
            var available = new ArrayList<>(SCENARIOS);
            available.addAll(spatialScenarios());
            scenarios = available.stream().filter(value -> names.contains(value.name())).toList();
            require(scenarios.size() == names.size(), "Unknown or duplicate scenario");
        }
        cpu = ManagementFactory.getThreadMXBean();
        cpuAvailable = cpu.isCurrentThreadCpuTimeSupported();
        if (cpuAvailable && !cpu.isThreadCpuTimeEnabled()) cpu.setThreadCpuTimeEnabled(true);
        if (cpu instanceof ThreadMXBean bean && bean.isThreadAllocatedMemorySupported()) {
            bean.setThreadAllocatedMemoryEnabled(true);
            allocation = bean;
        }
        gpuAvailable = GL.getCapabilities().OpenGL33 && GL15.glGetQueryi(GL33.GL_TIMESTAMP, GL15.GL_QUERY_COUNTER_BITS) > 0;
        engine = UnifiedUI.get();
        render = engine.require(UIApis.RENDER);
        resources = engine.require(UIApis.RESOURCES);
        if (scenarios.stream().anyMatch(value -> value.work() == Work.CONFIG)) {
            for (int index = 0; index < 64; index++) {
                var key = ConfigKey.of(id("bench/config/" + index), Integer.class, 0,
                        Set.of(ConfigLayer.RUNTIME_OVERRIDE), value -> value >= 0);
                module.engine().configs().define(key);
                keys.add(key);
            }
        }
        require(Runtime.version().feature() == 21, "Expected game Java 21, got " + Runtime.version());
        int scale = count("scale", 2, 1, 2);
        require(client.getWindow().getGuiScale() == scale, "Unexpected GUI Scale");
        client.setWindowActive(true);
        Files.writeString(output.resolve("environment.txt"),
                "platform=" + engine.platform().info() + "\nlabel=" + System.getProperty("shb.kernel.bench.label")
                + "\njava=" + Runtime.version() + "\nvm=" + System.getProperty("java.vm.name")
                + "\nvmArgs=" + ManagementFactory.getRuntimeMXBean().getInputArguments()
                + "\nos=" + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + "\nprocessors=" + Runtime.getRuntime().availableProcessors()
                + "\ngpu=" + GL11.glGetString(GL11.GL_RENDERER) + "\nGL=" + GL11.glGetString(GL11.GL_VERSION)
                + "\ntimerQuery=" + gpuAvailable + "\nallocation=" + (allocation != null) + "\ncpuTime=" + cpuAvailable
                + "\nwidth=" + client.getWindow().getWidth() + "\nheight=" + client.getWindow().getHeight()
                + "\nguiScale=" + client.getWindow().getGuiScale()
                + "\nseed=" + SEED + "\nwarmup=" + warmup + "\nsamples=" + samples
                + "\nreloadWarmup=" + RELOAD_WARMUP + "\nreloadSamples=" + reloadSamples + "\n");
        beginScenario(client);
    }

    private void beginScenario(Minecraft client) throws IOException {
        lastProgress = System.nanoTime();
        scenario = scenarios.get(scenarioIndex);
        System.out.println("KERNEL_NATIVE_BENCHMARK " + scenario.name());
        iteration = 0;
        random = new SplittableRandom(SEED);
        screen = new ProbeScreen();
        client.setScreen(screen);
        if (scenario.work() == Work.RELOAD) {
            assets = resources.alignmentAssets(module, screen.tree, client.getResourceManager());
            require(assets.texture(TEXTURE, 1).isPresent(), "Benchmark texture is missing");
            resources.observe(module, screen.tree, generation -> resourceApplied = System.nanoTime() - reloadStart);
            beginReload(client);
        } else if (scenario.work() == Work.SPATIAL_RELOAD || scenario.work() == Work.SPATIAL_FALLBACK) {
            resources = engine.require(UIApis.RESOURCES);
            recoveringSpatialShader = false;
            if (scenario.work() == Work.SPATIAL_FALLBACK) installBrokenSpatialShader(client);
            resourceGeneration = resources.version();
            reload = client.reloadResourcePacks();
        } else {
            prepareFrame(client);
        }
    }

    private void advance(Minecraft client) throws IOException {
        require(client.screen == screen, "Benchmark Screen changed during " + scenario.name());
        if (spatialVisual() && reload != null && (scenario.work() != Work.SPATIAL_VISUAL)) {
            if (!reload.isDone() || client.getOverlay() != null) return;
            reload.join();
            require(resources.version() > resourceGeneration, "Spatial reload did not publish a resource generation");
            boolean fallback = scenario.work() == Work.SPATIAL_FALLBACK && !recoveringSpatialShader;
            require(engine.require(SpatialUI.KEY).capability(SpatialUI.Capability.SDF_SHADOWS)
                    == (fallback ? SpatialUI.Support.DEGRADED : SpatialUI.Support.SUPPORTED),
                    "Unexpected SDF shader state after spatial reload");
            reload = null;
            lastProgress = System.nanoTime();
        }
        if (scenario.work() == Work.RELOAD) {
            if (!reload.isDone()) return;
            if (reloadStart != 0) {
                long elapsed = System.nanoTime() - reloadStart;
                long bytes = difference(allocated(), reloadAllocation);
                reloadStart = 0;
                reload.join();
                require(assets.texture(TEXTURE, 1).isPresent() && assets.texture(TEXTURE, 1).isPresent(),
                        "Benchmark texture is missing after reload");
                long hits = assets.stats().hits() - previousHits;
                long misses = assets.stats().misses() - previousMisses;
                require(resources.version() > resourceGeneration && resourceApplied > 0 && hits == 1 && misses == 1,
                        "Resource generation or cache invalidation was lost");
                if (iteration >= RELOAD_WARMUP) {
                    reloadRows.add(new ReloadRow(iteration - RELOAD_WARMUP, elapsed, bytes, resourceApplied,
                            resources.version(), hits, misses));
                }
            }
            if (client.getOverlay() != null) return;
            lastProgress = System.nanoTime();
            if (++iteration == RELOAD_WARMUP + reloadSamples) nextScenario(client);
            else beginReload(client);
            return;
        }
        if (pending != null && frameOpen) {
            long elapsed = System.nanoTime() - frameStart;
            long cpuTime = difference(cpuTime(), frameCpu);
            long bytes = difference(allocated(), frameAllocated);
            frameOpen = false;
            require(pending.drawn, "Render pass did not run for " + scenario.name());
            if (iteration >= warmup) {
                pending.frameNanos = elapsed;
                pending.cpuNanos = cpuTime;
                pending.frameBytes = bytes;
                pending.measures = screen.measures - pending.measures;
                pending.arranges = screen.arranges - pending.arranges;
                pending.animations = screen.animations - pending.animations;
                pending.moves = screen.moves - pending.moves;
                var scheduler = engine.scheduler().snapshot();
                pending.queued = scheduler.queued();
                pending.deferred = scheduler.deferred() - pending.deferred;
                if (scenario.work() == Work.DIRTY) {
                    require(pending.measures == 1 && pending.arranges == 1, "Local dirty work did not stay local");
                }
                if (scenario.work() == Work.IDLE) {
                    require(pending.measures == 0 && pending.arranges == 0, "Idle layout recomputed");
                }
                if (scenario.work() == Work.ANIMATION) {
                    require(pending.animations == scenario.count(), "Not all animation tracks advanced");
                }
                if (scenario.work() == Work.POINTER) require(pending.moves == POINTERS_PER_FRAME, "Pointer events were lost");
                if (spatialVisual() && iteration == warmup + samples - 1) {
                    try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                        image.writeToFile(output.resolve(scenario.name() + ".png").toFile());
                        SpatialVisualCheck.verify(image, client.getWindow().getGuiScaledWidth(),
                                client.getWindow().getGuiScaledHeight(), scenario.work() != Work.SPATIAL_FALLBACK);
                    }
                }
                rows.add(pending);
            }
            pending = null;
            iteration++;
            lastProgress = System.nanoTime();
        }
        if (iteration < warmup + samples) {
            if (pending == null) prepareFrame(client);
        } else if (collectGpuResults()) {
            if (scenario.work() == Work.SPATIAL_FALLBACK && !recoveringSpatialShader) {
                require(client.getResourcePackRepository().removePack("file/spatial-broken-shader"),
                        "Could not deselect the broken spatial shader pack");
                recoveringSpatialShader = true;
                resourceGeneration = resources.version();
                reload = client.reloadResourcePacks();
            } else {
                nextScenario(client);
            }
        }
    }

    private void installBrokenSpatialShader(Minecraft client) throws IOException {
        var pack = output.resolve("resourcepacks/spatial-broken-shader");
        var shaders = pack.resolve("assets/sh_better_ui/shaders/core");
        Files.createDirectories(shaders);
        int format = SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES);
        Files.writeString(pack.resolve("pack.mcmeta"),
                "{\"pack\":{\"pack_format\":" + format + ",\"description\":\"Spatial shader fallback fixture\"}}");
        Files.writeString(shaders.resolve("spatial.json"), "{}");
        var repository = client.getResourcePackRepository();
        repository.reload();
        require(repository.addPack("file/spatial-broken-shader"), "Could not select the broken spatial shader pack");
    }

    private void beginReload(Minecraft client) {
        resourceGeneration = resources.version();
        resourceApplied = 0;
        previousHits = assets.stats().hits();
        previousMisses = assets.stats().misses();
        reloadAllocation = allocated();
        reloadStart = System.nanoTime();
        reload = client.reloadResourcePacks();
    }

    private void prepareFrame(Minecraft client) {
        Row row = new Row(scenario.name(), iteration - warmup);
        // Replay the focused input path regardless of desktop focus changes between frames.
        if (scenario.work() == Work.POINTER) client.setWindowActive(true);
        if (row.sample >= 0 && gpuAvailable) {
            row.gpuStart = GL15.glGenQueries();
            row.gpuEnd = GL15.glGenQueries();
        }
        if (row.sample >= 0) row.deferred = engine.scheduler().snapshot().deferred();
        row.moves = screen.moves;
        long bytes = allocated();
        long start = System.nanoTime();
        int count = scenario.count();
        switch (scenario.work()) {
            case DIRTY -> screen.layout[random.nextInt(count)].invalidate();
            case POINTER -> {
                replayingPointer = true;
                try {
                    for (int i = 0; i < POINTERS_PER_FRAME; i++) {
                        int coordinate = random.nextInt(count);
                        ClientUiKernel.nativeInput(screen, new NativeInput.Pointer(NativeInput.PointerAction.MOVE,
                                coordinate % 32, coordinate / 32, -1, 0, 0));
                    }
                } finally {
                    replayingPointer = false;
                }
            }
            case THEME -> {
                float spacing = 7 + (iteration & 1);
                screen.theme.configure(overrides -> overrides.override(ThemeTokens.SPACING_SM, spacing));
                double sum = 0;
                for (int i = 0; i < count; i++) sum += screen.themes[i].resolve(ThemeTokens.SPACING_SM);
                require(sum == count * spacing, "Theme inheritance mismatch");
                row.themeReads = count;
            }
            case CONFIG -> {
                var configs = module.engine().configs();
                var target = new ConfigScope(screen.tree, ConfigLayer.RUNTIME_OVERRIDE);
                var tx = configs.beginTransaction();
                for (var key : keys) tx.set(target, key, iteration & 1);
                tx.commit();
                require(configs.get(screen.tree, keys.get(0)) == (iteration & 1), "Configuration was not committed");
            }
            case CHURN -> {
                var previous = screen.tree;
                screen = new ProbeScreen();
                client.setScreen(screen);
                require(!previous.isOpen() && screen.tree.isOpen(), "Screen scope did not remount");
            }
            case SPATIAL -> screen.spatialBenchmark.update(iteration);
            default -> {}
        }
        row.workNanos = System.nanoTime() - start;
        row.workBytes = difference(allocated(), bytes);
        row.measures = screen.measures;
        row.arranges = screen.arranges;
        row.animations = screen.animations;
        pending = row;
    }

    private boolean collectGpuResults() {
        boolean ready = true;
        for (int index = rows.size() - samples; index < rows.size(); index++) {
            Row row = rows.get(index);
            if (row.gpuEnd == 0) continue;
            if (GL15.glGetQueryObjecti(row.gpuEnd, GL15.GL_QUERY_RESULT_AVAILABLE) == 0) {
                ready = false;
                continue;
            }
            row.gpuNanos = GL33.glGetQueryObjectui64(row.gpuEnd, GL15.GL_QUERY_RESULT)
                    - GL33.glGetQueryObjectui64(row.gpuStart, GL15.GL_QUERY_RESULT);
            GL15.glDeleteQueries(row.gpuStart);
            GL15.glDeleteQueries(row.gpuEnd);
            row.gpuEnd = 0;
        }
        return ready;
    }

    private void nextScenario(Minecraft client) throws IOException {
        if (++scenarioIndex < scenarios.size()) {
            beginScenario(client);
        } else {
            var previous = screen.tree;
            client.setScreen(new TitleScreen());
            require(!previous.isOpen(), "Benchmark screen leaked its scope");
            writeResults();
            Files.writeString(output.resolve("success.txt"), "native samples validated and written\n");
            benchmarkComplete = true;
            finished = true;
            client.stop();
        }
    }

    private void writeResults() throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(output.resolve("frames.csv"))) {
            out.write("scenario,sample,work_ns,work_allocated_bytes,frame_wall_ns,frame_cpu_ns,frame_allocated_bytes,"
                    + "pass_wall_ns,pass_allocated_bytes,gpu_ns,measures,arranges,animations,pointer_events,"
                    + "theme_reads,rectangles,queued,deferred\n");
            for (Row row : rows) out.write(row.csv());
        }
        try (BufferedWriter out = Files.newBufferedWriter(output.resolve("reload.csv"))) {
            out.write("scenario,sample,reload_wall_ns,reload_main_thread_allocated_bytes,"
                    + "kernel_applied_ns,resource_generation,cache_hits,cache_misses\n");
            for (var row : reloadRows) {
                out.write("reload," + row.sample() + "," + row.elapsed() + "," + row.bytes() + ","
                        + row.applied() + "," + row.generation() + "," + row.hits() + "," + row.misses() + "\n");
            }
        }
        if (rows.stream().anyMatch(row -> row.spatial != null)) {
            try (BufferedWriter out = Files.newBufferedWriter(output.resolve("spatial.csv"))) {
                out.write("scenario,sample,scene_cpu_ns,visible_nodes,shadow_commands,paint_commands,"
                        + "active_lights,cache_hits,cache_misses,quality,batches,backdrop_samples,submission_ns,engine_gpu_ns\n");
                for (Row row : rows) {
                    var profile = row.spatial;
                    if (profile == null) continue;
                    out.write(row.scenario + "," + row.sample + "," + profile.cpuNanos() + ","
                            + profile.visibleNodes() + "," + profile.shadowCommands() + "," + profile.paintCommands()
                            + "," + profile.activeLights() + "," + profile.cacheHits() + "," + profile.cacheMisses()
                            + "," + profile.quality() + "," + profile.batches() + "," + profile.backdropSamples()
                            + "," + profile.submissionNanos() + "," + profile.gpuNanos() + "\n");
                }
            }
        }
    }

    private long allocated() { return allocation == null ? -1 : allocation.getCurrentThreadAllocatedBytes(); }
    private long cpuTime() { return cpuAvailable ? cpu.getCurrentThreadCpuTime() : -1; }

    private boolean spatialVisual() {
        return switch (scenario.work()) {
            case SPATIAL_VISUAL, SPATIAL_RELOAD, SPATIAL_FALLBACK -> true;
            default -> false;
        };
    }

    private static long difference(long end, long begin) { return begin < 0 || end < 0 ? -1 : end - begin; }

    private final class ProbeScreen extends Screen {
        private ScopeHandle tree;
        private LayoutService.Binding[] layout;
        private ThemeService.Scope theme;
        private ThemeService.Scope[] themes;
        private long measures;
        private long arranges;
        private long animations;
        private long moves;
        private SpatialScene spatial;
        private SpatialBenchmark spatialBenchmark;

        ProbeScreen() { super(Component.translatable("gui.done")); }

        @Override protected void init() {
            if (tree != null && tree.isOpen()) return;
            tree = engine.require(UIApis.SCREENS).mount(module, this, TREE);
            Work work = scenario.work();
            int count = scenario.count();
            if (work == Work.SPATIAL || spatialVisual()) {
                var service = engine.require(SpatialUI.KEY);
                require(service.capability(SpatialUI.Capability.SDF_SHADOWS) == SpatialUI.Support.SUPPORTED,
                        "The spatial SDF shader is unavailable");
                spatial = service.mount(module, tree);
                if (spatialVisual()) SpatialVisualCheck.install(spatial);
                else spatialBenchmark = new SpatialBenchmark(service, spatial, scenario.spatialMode(), count);
                return;
            }
            if (work == Work.IDLE || work == Work.DIRTY) layout = new LayoutService.Binding[count];
            if (work == Work.THEME) {
                theme = engine.require(UIApis.THEME).scope(module, tree);
                themes = new ThemeService.Scope[count];
            }
            var layoutService = engine.require(UIApis.LAYOUT);
            var animationService = engine.require(UIApis.ANIMATION);
            var themeService = engine.require(UIApis.THEME);
            var inputService = engine.require(UIApis.INPUT);
            for (int i = 0; i < count; i++) {
                var node = tree.openChild(ScopeKind.COMPONENT, id("bench/node/" + i));
                switch (work) {
                    case IDLE, DIRTY -> layout[i] = layoutService.bind(module, node, LAYOUT, () -> measures++, () -> arranges++);
                    case ANIMATION -> {
                        var track = animationService.bind(module, node, ANIMATION, Spring.PANEL, 0, () -> false,
                                ignored -> animations++);
                        track.blend(1, 0, 600_000, BlendCurve.LINEAR);
                    }
                    case POINTER -> {
                        int x = i % 32;
                        int y = i / 32;
                        inputService.target(module, node, 0,
                                input -> input instanceof NativeInput.Pointer pointer && pointer.x() == x && pointer.y() == y);
                        module.engine().events().subscribe(InputService.POINTER_MOVED, node, UIEventPhase.TARGET, 0,
                                event -> { if (replayingPointer) moves++; });
                    }
                    case THEME -> themes[i] = themeService.scope(module, node);
                    default -> {}
                }
            }
        }

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            var row = pending;
            if (row == null || row.drawn || !frameOpen) return;
            int rectangles = spatial != null ? 0 : scenario.work() == Work.RENDER ? scenario.count() : 1;
            render.submit(module, tree, RenderService.Surface.SCREEN, RenderService.Layer.CONTENT, 0,
                    context -> draw(graphics, context, row, rectangles));
        }
    }

    private void draw(GuiGraphics graphics, RenderService.Context context, Row row, int rectangles) {
        graphics.flush();
        if (row.gpuStart != 0) GL33.glQueryCounter(row.gpuStart, GL33.GL_TIMESTAMP);
        long bytes = allocated();
        long start = System.nanoTime();
        try {
            if (screen.spatial != null) {
                boolean visual = spatialVisual();
                if (visual) context.fill(0, 0, context.frame().width(), context.frame().height(), SpatialVisualCheck.BACKGROUND);
                context.spatial(screen.spatial);
                if (visual) context.fill(32, 72, 52, 86, SpatialVisualCheck.MARKER);
                row.spatial = screen.spatial.profile();
                require(row.spatial.visibleNodes() > 0 && row.spatial.paintCommands() > 0,
                        "Spatial draw did not submit visible geometry");
            }
            for (int i = 0; i < rectangles; i++) {
                int x = 2 + 3 * (i % 120);
                int y = 2 + 3 * (i / 120);
                context.fill(x, y, x + 2, y + 2, 0xff4466aa);
            }
            graphics.flush();
        } finally {
            row.passNanos = System.nanoTime() - start;
            row.passBytes = difference(allocated(), bytes);
            if (row.gpuEnd != 0) GL33.glQueryCounter(row.gpuEnd, GL33.GL_TIMESTAMP);
        }
        row.rectangles = rectangles;
        row.drawn = true;
    }

    private static final class Row {
        final String scenario;
        final int sample;
        long workNanos, workBytes, frameNanos, cpuNanos, frameBytes, passNanos, passBytes;
        long gpuNanos = -1;
        long measures, arranges, animations, moves, themeReads, rectangles, queued, deferred;
        int gpuStart, gpuEnd;
        boolean drawn;
        SpatialScene.Profile spatial;

        Row(String scenario, int sample) {
            this.scenario = scenario;
            this.sample = sample;
        }

        String csv() {
            return scenario + "," + sample + "," + workNanos + "," + workBytes + "," + frameNanos + "," + cpuNanos
                    + "," + frameBytes + "," + passNanos + "," + passBytes + "," + gpuNanos + "," + measures
                    + "," + arranges + "," + animations + "," + moves + "," + themeReads + "," + rectangles
                    + "," + queued + "," + deferred + "\n";
        }
    }

    private record ReloadRow(int sample, long elapsed, long bytes, long applied, long generation, long hits, long misses) {}

    private static int count(String name, int fallback, int minimum, int maximum) {
        int value = Integer.parseInt(System.getProperty("shb.kernel.bench." + name, Integer.toString(fallback)));
        require(value >= minimum && value <= maximum, "Out-of-range sample setting " + name);
        return value;
    }

    private static UiId id(String path) { return new UiId("sh_better_ui", path); }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
