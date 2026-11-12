// Author: dishanhai
package com.dishanhai.shbui_kernel_example;

import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.api.UiScreenBuilder;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.AnimationService;
import com.dishanhai.sh_better_ui.kernel.api.CapabilityKey;
import com.dishanhai.sh_better_ui.kernel.api.CapabilityStatus;
import com.dishanhai.sh_better_ui.kernel.api.ConfigKey;
import com.dishanhai.sh_better_ui.kernel.api.ConfigLayer;
import com.dishanhai.sh_better_ui.kernel.api.ConfigScope;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.RenderService;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.ScopeKind;
import com.dishanhai.sh_better_ui.kernel.api.ScreenService;
import com.dishanhai.sh_better_ui.kernel.api.ServiceDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.TaskHandle;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UIConfigCenter;
import com.dishanhai.sh_better_ui.kernel.api.UIModule;
import com.dishanhai.sh_better_ui.kernel.api.UIPermission;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.kernel.api.VersionRange;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassDraw;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassOptics;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassRegion;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService;
import com.dishanhai.sh_better_ui.kernel.api.live.LiveSurfaceService;
import com.dishanhai.sh_better_ui.kernel.api.live.NotificationService;
import com.dishanhai.sh_better_ui.kernel.api.live.SurfacePlacement;
import com.dishanhai.sh_better_ui.kernel.api.page.PageService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** Discovered from this mod's META-INF/sh_better_ui.modules on both loaders. */
public final class ExampleUiModule implements UIModule {
    public static final String MOD_ID = "shbui_kernel_example";
    public static final UiId ID = id("controls");
    public static final CapabilityKey CONTROLS = new CapabilityKey(ID);
    private static final UiId STATUS_MOTION = id("status_motion");
    public static final ConfigKey<Integer> STEP = ConfigKey.of(id("step"), Integer.class, 1,
            Set.of(ConfigLayer.COMPONENT), step -> step >= 1 && step <= 5);
    private static final UiVersion VERSION = UiVersion.parse("1.0.0");
    private static final CraftingBackend LOCAL_CRAFTING = quantity -> new CraftingBackend.Job() {
        private int ticks;

        @Override public int poll() { return Math.min(quantity, ++ticks / 4); }
        @Override public void close() {}
    };
    private static volatile CraftingBackend craftingBackend = LOCAL_CRAFTING;

    private ModuleContext module;
    private ScreenService screens;
    private UIConfigCenter configs;
    private boolean dynamicTheme;
    private boolean systemReader;
    private CounterPage page;
    private ActivityService activities;
    private NotificationService notifications;
    private LiveSurfaceService liveSurfaces;
    private ConnectionExample connection;
    private ClientPageExamples declarativePages;

    /** Installs an optional public crafting adapter for future jobs. */
    public static void installCraftingBackend(CraftingBackend backend) {
        craftingBackend = Objects.requireNonNull(backend);
    }

    static CraftingBackend craftingBackend() { return craftingBackend; }
    static boolean localCraftingBackend(CraftingBackend backend) { return backend == LOCAL_CRAFTING; }

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder().id(ID).version(VERSION)
                .requestPermission(UIPermission.REGISTER_RENDER_PASS)
                .claimResource(new ModuleDescriptor.ResourceClaim(ModuleDescriptor.ResourceKind.ANIMATION_PROPERTY,
                        STATUS_MOTION))
                .requiredCapability(UIApis.NATIVE_SCREEN, VersionRange.ANY)
                .optionalCapability(UIApis.DYNAMIC_THEME, VersionRange.ANY)
                .optionalCapability(UIApis.SYSTEM_SCREEN_READER, VersionRange.ANY)
                .providedCapability(CONTROLS, VERSION)
                .build();
    }

    @Override
    public void register(ModuleContext context) {
        CounterService counter = this::open;
        context.services().register(new ServiceDescriptor<>(CounterService.KEY, VERSION,
                ServiceDescriptor.Visibility.PUBLIC, 0, false), counter);
    }

    @Override
    public void start(ModuleContext context) {
        var ui = context.engine();
        screens = ui.require(UIApis.SCREENS);
        configs = ui.configs();
        configs.define(STEP);
        dynamicTheme = ui.capabilities().supports(UIApis.DYNAMIC_THEME) && ui.find(UIApis.THEME).isPresent();
        systemReader = ui.capabilities().status(UIApis.SYSTEM_SCREEN_READER) == CapabilityStatus.AVAILABLE;
        activities = ui.find(ActivityService.KEY).orElse(null);
        notifications = ui.find(NotificationService.KEY).orElse(null);
        liveSurfaces = ui.find(LiveSurfaceService.KEY).orElse(null);
        declarativePages = ui.find(PageService.KEY).map(pages -> new ClientPageExamples(context, pages)).orElse(null);
        module = context;
        ExampleClient.module(this);
        if (activities != null && notifications != null && liveSurfaces != null) {
            connection = new ConnectionExample(context, activities, notifications);
            ExampleClient.connection(connection);
        }
        ExampleSmoke.started(this);
    }

    @Override
    public void stop(ModuleContext context) {
        try {
            if (page != null) page.screen.dispose();
        } finally {
            var watch = connection;
            ExampleClient.module(null);
            ExampleClient.connection(null);
            connection = null;
            page = null;
            module = null;
            screens = null;
            configs = null;
            activities = null;
            notifications = null;
            liveSurfaces = null;
            declarativePages = null;
            if (watch != null) watch.close();
        }
    }

    private void open(Screen parent, Supplier<? extends GuiEventListener> returnFocus) {
        if (module == null) throw new IllegalStateException("Example UI module is inactive");
        if (!module.engine().platform().isOnUIThread()) throw new IllegalStateException("Client thread required");
        if (page != null) return;
        var next = new CounterPage();
        var builder = new UiScreenBuilder(label("title")).parent(parent).returnFocus(returnFocus)
                .onInit(next::init).onTick(screen -> next.tick()).onForeground(next::paintStatus)
                .onRemoved(screen -> next.removed());
        if (dynamicTheme) builder.dynamicTheme();
        next.screen = screens.create(module, builder);
        page = next;
        next.screen.open();
    }

    void openRichText(Screen parent, Supplier<? extends GuiEventListener> returnFocus) {
        if (module != null) Minecraft.getInstance().setScreen(new RichTextSdkScreen(module, parent, returnFocus));
    }

    boolean hasTextReadability() { return module != null && module.engine().find(UIApis.TEXT_READABILITY).isPresent(); }

    void openTextReadability(Screen parent, Supplier<? extends GuiEventListener> returnFocus) {
        if (hasTextReadability()) Minecraft.getInstance().setScreen(new TextReadabilitySdkScreen(module, parent, returnFocus));
    }

    boolean hasDeclarativePages() { return declarativePages != null; }

    void openDeclarativePages() {
        if (declarativePages != null) declarativePages.open(Minecraft.getInstance().level != null);
    }

    static UiId id(String path) {
        return new UiId(MOD_ID, path);
    }

    static Component label(String name, Object... arguments) {
        return Component.translatable(MOD_ID + "." + name, arguments);
    }

    CounterPage page() { return page; }
    boolean stopped() { return module == null && page == null; }

    final class CounterPage {
        private Integer previous;
        UiScreen screen;
        ScopeHandle tree;
        ScopeHandle counter;
        UiButton increase;
        UiButton undo;
        private UiButton music, craft, machine, watch;
        private LiveActivityExamples demos;
        private LiveSurfaceService.Reservation toolbar;
        private TaskHandle statusPaint;
        private AnimationService.Track statusMotion;
        private GlassMaterial statusRest;
        private GlassMaterial statusRaised;
        private boolean statusActive;
        private int statusY;
        int count;

        void init(UiScreen active) {
            if (tree == null) {
                tree = screens.mount(module, active, id("counter_screen"));
                counter = tree.openChild(ScopeKind.COMPONENT, id("counter_button"));
                configs.beginTransaction().set(new ConfigScope(counter, ConfigLayer.COMPONENT), STEP, 3).commit();
                if (connection != null) demos = new LiveActivityExamples(module, counter, activities, notifications);
                var ui = module.engine();
                ui.find(UIApis.GLASS).ifPresent(glass -> {
                    statusRest = glass.material(GlassVariant.REGULAR);
                    var optics = statusRest.optics();
                    statusRaised = new GlassMaterial(statusRest.variant(),
                            new GlassOptics(optics.transmission(), 5, optics.lensing(), optics.scattering(),
                                    optics.edgeHighlight(), 4.2f, optics.blurRadius()),
                            statusRest.quality(), statusRest.foreground());
                    statusMotion = ui.require(UIApis.ANIMATION).bind(module, counter, STATUS_MOTION,
                            Spring.PANEL, 0, () -> UiPreferences.configured().reducedMotion(), ignored -> {});
                });
            }
            var bounds = active.viewport();
            int width = Math.min(demos == null ? 180 : 302, Math.max(12, bounds.width() - 12));
            int x = bounds.x() + (bounds.width() - width) / 2;
            int height = demos == null ? 22 : Math.max(14, Math.min(22, (bounds.height() - 32) / 4));
            int step = height + 5;
            int rows = demos == null ? 2 : 4;
            int y = bounds.y() + Math.max(0, (bounds.height() - rows * step - 12) / 2);
            int column = demos == null ? width : (width - 6) / 2;
            int right = x + column + 6;
            increase = active.button(x, y, column, height, countLabel(), () -> {
                previous = count;
                count += step();
                refresh();
            });
            undo = active.button(demos == null ? x : right, demos == null ? y + step : y, column, height, label("undo"), () -> {
                if (previous != null) count = previous;
                previous = null;
                refresh();
            });
            if (demos != null) {
                music = demoButton(x, y + step, column, height, demos.musicButton(), "music_tip", demos::toggleMusic);
                craft = demoButton(right, y + step, column, height, demos.craftingButton(), "craft_tip", demos::toggleCrafting);
                machine = demoButton(x, y + step * 2, column, height, demos.machineButton(), "machine_tip", demos::toggleMachine);
                watch = demoButton(right, y + step * 2, column, height, watchLabel(), "connection_tip", connection::toggle);
                demoButton(x, y + step * 3, column, height, "priority_start", "priority_tip", demos::startPriority);
                demoButton(right, y + step * 3, column, height, "stop", "stop_tip", () -> {
                    demos.close();
                    connection.close();
                });
                statusY = y + step * 4 + 3;
                var reserved = new SurfacePlacement.Rect(0, 0, Math.min(110, active.width), bounds.y());
                if (toolbar == null) toolbar = liveSurfaces.reserve(module, counter, id("live/toolbar"), reserved);
                else toolbar.update(reserved);
            }
            refresh();
        }

        private UiButton demoButton(int x, int y, int width, int height, String name, String tip, Runnable run) {
            var button = screen.button(x, y, width, height, label("live." + name), () -> {
                run.run();
                refreshDemos();
            });
            button.setTooltip(Tooltip.create(label("live." + tip)));
            return button;
        }

        private void tick() {
            if (demos == null) return;
            demos.tick();
            refreshDemos();
        }

        private void paintStatus(UiScreen active, UiFrame frame) {
            if (demos == null || statusY + 10 > active.height) return;
            var plan = liveSurfaces.view().plan();
            var text = label("live.plan", activityName(plan.primary()), activityName(plan.secondary()));
            var client = Minecraft.getInstance();
            var font = client.font;
            var engine = module.engine();
            if (client.screen == active && active.width >= 40 && statusY >= 5 && statusY + 13 <= active.height
                    && statusMotion != null && statusMotion.isActive()) {
                if (statusPaint == null || statusPaint.status().isTerminal()) {
                    boolean hasActivity = plan.primary() != null;
                    if (statusActive != hasActivity) {
                        statusActive = hasActivity;
                        statusMotion.play(statusActive ? 1f : 0f, InterruptibleAnimation.Policy.RETARGET, 0);
                    }
                    float progress = frame.preferences().reducedMotion() ? (statusActive ? 1f : 0f)
                            : Math.max(0f, Math.min(1f, statusMotion.value()));
                    int width = Math.min(252, active.width - 20);
                    String line = font.plainSubstrByWidth(text.getString(), width - 16);
                    int x = (active.width - font.width(line)) / 2;
                    int y = statusY;
                    int left = (active.width - width) / 2;
                    var rest = new GlassDraw(GlassRegion.capsule(left + 4, y - 3, width - 8, 14), statusRest);
                    var raised = new GlassDraw(GlassRegion.capsule(left, y - 5, width, 18),
                            statusRaised, -.5f, -.65f, .55f);
                    var surface = rest.interpolate(raised, progress);
                    statusPaint = engine.require(UIApis.RENDER).submit(module, tree, RenderService.Surface.SCREEN,
                            RenderService.Layer.OVERLAY, 0, pass -> {
                                pass.glass(List.of(surface));
                                pass.text(Component.literal(line), x, y, surface.material().foreground().argb());
                            });
                }
                return;
            }
            int width = Math.max(1, active.width - 12);
            String line = font.plainSubstrByWidth(text.getString(), width);
            frame.graphics().drawString(font, line, (active.width - font.width(line)) / 2, statusY, 0xffd7e3f1, false);
        }

        private Component activityName(ActivityService.Snapshot activity) {
            if (activity == null) return label("live.empty");
            var title = activity.content().title();
            return title.translated() ? Component.translatable(title.value()) : Component.literal(title.value());
        }

        private void refreshDemos() {
            setDemoLabel(music, demos.musicButton());
            setDemoLabel(craft, demos.craftingButton());
            setDemoLabel(machine, demos.machineButton());
            setDemoLabel(watch, watchLabel());
        }

        private void setDemoLabel(UiButton button, String name) {
            var next = label("live." + name);
            if (!next.equals(button.getMessage())) button.setMessage(next);
        }

        private String watchLabel() { return connection.enabled() ? "connection_disable" : "connection_enable"; }

        private void removed() {
            try {
                if (demos != null) demos.close();
            } finally {
                // The kernel closes the mounted tree with its native Screen.
                if (page == this) page = null;
            }
        }

        boolean systemReader() { return systemReader; }

        int step() { return configs.get(counter, STEP); }

        private void refresh() {
            increase.setMessage(countLabel());
            increase.setTooltip(Tooltip.create(label(systemReader ? "system_reader_available" : "minecraft_narration")));
            undo.active = previous != null;
        }

        private Component countLabel() {
            return label("increase", count, step());
        }
    }
}
