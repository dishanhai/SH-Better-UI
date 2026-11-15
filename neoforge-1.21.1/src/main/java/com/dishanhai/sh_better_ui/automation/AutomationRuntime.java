// Author: dishanhai
package com.dishanhai.sh_better_ui.automation;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.automation.script.BundledScriptWorker;
import com.dishanhai.sh_better_ui.automation.script.ScriptWorkerSupervisor;
import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.automation.capability.*;
import com.dishanhai.sh_better_ui.kernel.api.automation.capability.CapabilityDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.automation.capability.CapabilityRequest;
import com.dishanhai.sh_better_ui.kernel.api.automation.rule.*;
import com.dishanhai.sh_better_ui.kernel.api.page.*;
import com.dishanhai.sh_better_ui.kernel.api.protocol.*;
import com.dishanhai.sh_better_ui.kernel.internal.ServerAutomationScopes;
import com.dishanhai.sh_better_ui.kernel.internal.ServerCapabilityModules;
import com.dishanhai.sh_better_ui.kernel.internal.automation.capability.ServerCapabilityRegistry;
import com.dishanhai.sh_better_ui.kernel.internal.automation.rule.AtomicFileRuleStore;
import com.dishanhai.sh_better_ui.kernel.internal.automation.rule.RuleEngine;
import com.google.gson.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** NeoForge server owner for discovered capabilities, rules, pages and isolated scripts. */
@EventBusSubscriber(modid = ShBetterUi.MOD_ID)
public final class AutomationRuntime implements AutoCloseable {
    private static final Map<MinecraftServer, AutomationRuntime> SERVERS = new IdentityHashMap<>();
    private static final UiId PROVIDER = UiId.parse("sh_better_ui:automation");
    private static final UiId REPLENISH = UiId.parse("sh_better_ui:replenish");
    private static final List<String> LANGUAGES = List.of("en_us", "zh_cn", "zh_tw");
    private final MinecraftServer server;
    private final Path directory;
    private final List<RegistrationHandle> pages = new ArrayList<>();
    private final Map<UUID, ScopeHandle> sessions = new HashMap<>();
    private final Map<UUID, CompletableFuture<ScriptWorkerSupervisor.Result>> scripts = new HashMap<>();
    private final Map<UUID, Editor> editors = new HashMap<>();
    private final Map<UUID, String> statuses = new HashMap<>();
    private final ServerAutomationScopes scopes;
    private final ServerCapabilityRegistry capabilities;
    private final AtomicFileRuleStore store;
    private final RuleEngine engine;
    private final AutomationBackend backend;
    private ScriptWorkerSupervisor worker;
    private String unavailable;
    private boolean closed;
    private int ticks;

    private AutomationRuntime(MinecraftServer server) throws IOException {
        this.server = server;
        directory = server.getWorldPath(LevelResource.ROOT).resolve("data/sh_better_ui/automation");
        Files.createDirectories(directory);
        scopes = new ServerAutomationScopes(error -> ShBetterUi.LOGGER.warn("Automation cleanup failed", error));
        capabilities = new ServerCapabilityRegistry(new ServerCapabilityRegistry.ServerThread() {
            public boolean isOnThread() { return server.isSameThread(); }
            public void execute(Runnable action) { server.execute(action); }
        }, (caller, descriptor) -> !closed && server.getPlayerList().getPlayer(caller.playerId()) != null);
        AutomationBackend selected = null;
        var mod = ModList.get().getModContainerById("ae2");
        if (mod.isEmpty()) unavailable = "missing_ae2";
        else if (!mod.orElseThrow().getModInfo().getVersion().toString().equals("19.2.18")) unavailable = "unsupported_ae2";
        else selected = Ae2Factory.create(server);
        backend = selected;
        store = new AtomicFileRuleStore(directory.resolve("rules.bin"));
        try {
            if (backend != null && Files.exists(directory.resolve("adapter.nbt"))) {
                backend.restore(NbtIo.readCompressed(directory.resolve("adapter.nbt"), NbtAccounter.create(8 * 1024 * 1024)));
            }
            loadEditors();
            engine = new RuleEngine(backend != null ? backend : new UnavailableAdapter(server), store,
                    System::currentTimeMillis, server::execute);
            engine.setAutomaticTrigger(rule -> editorMatches(rule) && editors.get(rule.id()).kind != 2);
            if (backend != null) backend.setSubmissionGuard(this::maySubmit);
            registerCapabilities();
            ServerCapabilityModules.install(server, scopes, capabilities);
            for (String language : LANGUAGES) {
                registerPage(pageId(language), language);
                registerPage(UiId.parse(pageId(language) + "/terminal"), language);
            }
            registerPage(AutomationPage.ID, "en_us");
        } catch (IOException | RuntimeException failure) {
            pages.forEach(RegistrationHandle::close);
            if (backend != null) backend.close();
            capabilities.close();
            scopes.close();
            store.close();
            throw failure;
        }
    }

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        try { SERVERS.put(event.getServer(), new AutomationRuntime(event.getServer())); }
        catch (Exception failure) { ShBetterUi.LOGGER.error("UUK automation disabled: initialization failed", failure); }
    }

    public static Optional<CapabilityDirectory> capabilitiesFor(ServerPlayer player) {
        var server = player.server;
        if (!server.isSameThread()) throw new IllegalStateException("UUK capability discovery requires the server thread");
        var runtime = SERVERS.get(server);
        var id = player.getUUID();
        if (runtime == null || runtime.closed || server.getPlayerList().getPlayer(id) != player) {
            return Optional.empty();
        }
        var session = runtime.sessions.get(id);
        return session == null ? Optional.empty() : Optional.of(runtime.capabilities.bind(session, new CapabilityCaller(id)));
    }

    @SubscribeEvent public static void ticking(ServerTickEvent.Post event) {
        var runtime = SERVERS.get(event.getServer());
        if (runtime != null) runtime.tick();
    }

    @SubscribeEvent public static void stopped(ServerStoppingEvent event) {
        var runtime = SERVERS.remove(event.getServer());
        if (runtime != null) runtime.close();
    }

    @SubscribeEvent public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var runtime = SERVERS.get(player.server);
        if (runtime != null) runtime.session(player.getUUID());
    }

    @SubscribeEvent public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var runtime = SERVERS.get(player.server);
        if (runtime == null) return;
        UUID owner = player.getUUID();
        runtime.sessions.remove(owner);
        runtime.scopes.closeSession(owner);
        runtime.engine.serverPlayerOffline(owner);
        runtime.cancelPlanning(owner, null);
        runtime.statuses.remove(owner);
        for (var entry : List.copyOf(runtime.scripts.entrySet())) {
            boolean owned = runtime.rule(owner, entry.getKey()) != null;
            if (owned) { entry.getValue().cancel(true); runtime.scripts.remove(entry.getKey(), entry.getValue()); }
        }
    }

    private static UiId pageId(String language) { return UiId.parse("sh_better_ui:automation/" + language); }

    private void registerPage(UiId id, String language) {
        var definition = AutomationPage.create(id, key -> AutomationText.get(language, key));
        var handlers = new HashMap<UiId, ServerPages.ActionHandler>();
        for (var action : definition.actions().values()) {
            var handler = action.policy() == UiActionPolicy.CONFIRMED
                    ? ServerPages.ActionHandler.confirmed(this::mayUse,
                        this::confirmation, request -> handle(action.id(), request))
                    : new ServerPages.ActionHandler(this::mayUse, request -> handle(action.id(), request));
            handlers.put(action.id(), handler);
        }
        boolean terminal = id.path().endsWith("/terminal");
        pages.add(ServerPages.registerRequested(server, definition,
                player -> mayUse(player) && (!terminal || backend != null), handlers,
                player -> initial(player, terminal)));
    }

    private boolean mayUse(ServerPlayer player) {
        return !closed && player.isAlive() && !player.isSpectator()
                && server.getPlayerList().getPlayer(player.getUUID()) == player;
    }

    private UiActionPrompt confirmation(ServerPages.Action action) {
        UUID id = ruleId(action.player().getUUID(), string(action.state(), "ruleId"));
        var rule = rule(action.player().getUUID(), id);
        return new UiActionPrompt(rule == null ? text(action.player(), "confirm_subject") : rule.item(),
                rule == null ? 1 : rule.target(), text(action.player(), "confirm_effect"));
    }

    private boolean maySubmit(UUID intent, AutomationAction action) {
        var snapshot = engine.snapshot();
        var task = snapshot.tasks().stream().filter(candidate -> candidate.intentId().equals(intent)).findFirst().orElse(null);
        var rule = task == null ? null : rule(action.owner(), task.ruleId());
        var player = server.getPlayerList().getPlayer(action.owner());
        return !closed && rule != null && rule.enabled() && editorMatches(rule)
                && engine.mayFinishSubmission(intent)
                && engine.storageHealthy() && !snapshot.paused().contains(rule.id()) && player != null
                && (rule.mode() == ReplenishmentRule.Mode.PLAYER_ONLINE || !visible(player).isEmpty());
    }

    private Map<String, UiValue> initial(ServerPlayer player, boolean terminal) {
        session(player.getUUID());
        var data = new HashMap<>(AutomationPage.initialData(text(player, unavailable == null ? "ready" : unavailable)));
        if (backend != null) {
            try {
                data.put("target", new UiValue.Text(backend.bind(player)));
                saveBackend();
            } catch (IllegalStateException denied) {
                if (terminal) throw denied;
                data.put("status", new UiValue.Text(text(player, "open_terminal")));
            } catch (IOException failure) {
                data.put("status", new UiValue.Text(text(player, "storage_failed")));
            }
        }
        data.put("rules", ruleRows(player));
        return Map.copyOf(data);
    }

    private ServerPages.Outcome handle(UiId id, ServerPages.Action action) {
        if (!mayUse(action.player())) return ServerPages.Outcome.failed(UiErrorCode.PERMISSION_DENIED);
        var state = new HashMap<>(action.state());
        String operation = id.path().substring("automation_".length());
        try {
            if (operation.startsWith("edit_")) {
                String field = action.payload().keySet().iterator().next();
                if (field.equals("ruleId")) {
                    String name = ((UiValue.Text) action.payload().get(field)).value();
                    var saved = rule(action.player().getUUID(), ruleId(action.player().getUUID(), name));
                    if (saved != null) {
                        var editor = editors.get(saved.id());
                        return ServerPages.Outcome.success(Map.of(
                                "ruleId", new UiValue.Text(name), "target", new UiValue.Text(saved.targetKey()),
                                "item", new UiValue.Text(saved.item()), "lower", new UiValue.Text(Long.toString(saved.lower())),
                                "targetAmount", new UiValue.Text(Long.toString(saved.target())),
                                "mode", new UiValue.Int(saved.mode() == ReplenishmentRule.Mode.PAGE_OPEN ? 0 : 1),
                                "editor", new UiValue.Int(editor == null ? 0 : editor.kind),
                                "script", new UiValue.Text(editor == null ? "" : editor.script)));
                    }
                }
                return ServerPages.Outcome.success(Map.of(field, Objects.requireNonNull(action.payload().get(field))));
            }
            if (operation.equals("apply")) {
                state.putAll(action.payload());
                state.put("status", new UiValue.Text(text(action.player(), "draft_applied")));
                return ServerPages.Outcome.success(state);
            }
            UUID owner = action.player().getUUID();
            UUID ruleId = ruleId(owner, string(state, "ruleId"));
            String outcome;
            switch (operation) {
                case "save" -> {
                    var previous = rule(owner, ruleId);
                    if (engine.snapshot().tasks().stream().anyMatch(task -> task.ruleId().equals(ruleId)
                            && task.state() == RuleStore.TaskState.SUBMITTING)) {
                        return ServerPages.Outcome.success(Map.of("status", new UiValue.Text(text(action.player(), "planning_edit_blocked"))));
                    }
                    if (previous == null && engine.snapshot().rules().stream().filter(r -> r.owner().equals(owner)).count() >= 64) {
                        return ServerPages.Outcome.failed(UiErrorCode.RESOURCE_LIMIT_EXCEEDED);
                    }
                    long revision = previous == null ? 1 : previous.revision() + 1;
                    var editor = new Editor(integer(state, "editor"), string(state, "script"), revision);
                    var rule = new ReplenishmentRule(ruleId, revision, owner,
                            string(state, "target"), string(state, "item"), Long.parseLong(string(state, "lower")),
                            Long.parseLong(string(state, "targetAmount")), integer(state, "mode") == 0
                            ? ReplenishmentRule.Mode.PAGE_OPEN : ReplenishmentRule.Mode.PLAYER_ONLINE,
                            false);
                    if (!engine.put(owner, rule)) return ServerPages.Outcome.failed(UiErrorCode.PERMISSION_DENIED);
                    var running = scripts.remove(ruleId);
                    if (running != null) running.cancel(true);
                    editors.put(ruleId, editor);
                    saveEditors();
                    outcome = "saved";
                }
                case "pause" -> {
                    boolean paused = engine.pause(owner, ruleId);
                    if (paused) cancelPlanning(owner, ruleId);
                    outcome = paused ? "paused" : "denied";
                }
                case "resume" -> {
                    var old = rule(owner, ruleId);
                    if (old == null) return ServerPages.Outcome.failed(UiErrorCode.ACTION_INVALID);
                    if (!editorMatches(old)) return ServerPages.Outcome.failed(UiErrorCode.ACTION_INVALID);
                    if (!old.enabled()) {
                        var editor = editors.get(ruleId);
                        editors.put(ruleId, new Editor(editor.kind, editor.script, old.revision() + 1));
                        saveEditors();
                        if (!engine.put(owner, new ReplenishmentRule(old.id(), old.revision() + 1, owner, old.targetKey(),
                                old.item(), old.lower(), old.target(), old.mode(), true))) {
                            return ServerPages.Outcome.failed(UiErrorCode.PERMISSION_DENIED);
                        }
                    }
                    lease(action.player(), ruleId);
                    outcome = engine.resume(owner, ruleId) ? "resumed" : "reconcile_required";
                }
                case "run_script" -> {
                    var rule = rule(owner, ruleId);
                    if (rule == null) return ServerPages.Outcome.failed(UiErrorCode.ACTION_INVALID);
                    lease(action.player(), ruleId);
                    runScript(action.player(), rule, string(state, "script"));
                    outcome = "script_running";
                }
                case "refresh" -> outcome = unavailable == null ? "ready" : unavailable;
                default -> { return ServerPages.Outcome.failed(UiErrorCode.ACTION_INVALID); }
            }
            statuses.put(action.player().getUUID(), outcome);
            return ServerPages.Outcome.success(Map.of("status", new UiValue.Text(text(action.player(), outcome)),
                    "rules", ruleRows(action.player())));
        } catch (IllegalArgumentException | NullPointerException failure) {
            return ServerPages.Outcome.failed(UiErrorCode.ACTION_INVALID);
        } catch (IOException failure) {
            ShBetterUi.LOGGER.warn("Automation persistence failed", failure);
            return ServerPages.Outcome.success(Map.of("status", new UiValue.Text(text(action.player(), "storage_failed"))));
        }
    }

    private void tick() {
        if (closed) return;
        try {
            if (backend != null) backend.tick();
            if (++ticks % 20 != 0) return;
            for (var player : server.getPlayerList().getPlayers()) {
                session(player.getUUID());
                for (var rule : engine.snapshot().rules()) if (rule.owner().equals(player.getUUID())) lease(player, rule.id());
            }
            engine.tick();
            for (var rule : engine.snapshot().rules()) {
                var editor = editors.get(rule.id());
                var player = server.getPlayerList().getPlayer(rule.owner());
                if (editorMatches(rule) && editor.kind == 2 && rule.enabled() && player != null && !engine.snapshot().paused().contains(rule.id())
                        && (rule.mode() == ReplenishmentRule.Mode.PLAYER_ONLINE || !visible(player).isEmpty())) {
                    runScript(player, rule, editor.script);
                }
            }
            for (var player : server.getPlayerList().getPlayers()) {
                var update = Map.<String, UiValue>of("rules", ruleRows(player), "status", new UiValue.Text(
                        text(player, statuses.getOrDefault(player.getUUID(), unavailable == null ? "ready" : unavailable))));
                for (UUID instance : visible(player)) ServerPages.update(player, instance, update);
            }
            if (backend != null && ticks % 100 == 0) saveBackend();
        } catch (Exception failure) {
            ShBetterUi.LOGGER.error("Automation stopped after runtime failure", failure);
            close();
        }
    }

    private void lease(ServerPlayer player, UUID ruleId) {
        if (visible(player).isEmpty()) engine.serverPageClosed(player.getUUID(), ruleId);
        else engine.serverPageOpened(player.getUUID(), ruleId, 1500);
    }

    private void cancelPlanning(UUID owner, UUID ruleId) {
        if (backend == null) return;
        for (var task : engine.snapshot().tasks()) {
            if (task.state() == RuleStore.TaskState.SUBMITTING
                    && (owner == null || task.action().owner().equals(owner))
                    && (ruleId == null || task.ruleId().equals(ruleId))) backend.cancelPending(task.intentId());
        }
    }

    private List<UUID> visible(ServerPlayer player) {
        var ids = new ArrayList<UUID>(ServerPages.visibleInstances(player, AutomationPage.ID));
        for (String language : LANGUAGES) {
            ids.addAll(ServerPages.visibleInstances(player, pageId(language)));
            ids.addAll(ServerPages.visibleInstances(player, UiId.parse(pageId(language) + "/terminal")));
        }
        return ids;
    }

    private ScopeHandle session(UUID player) { return sessions.computeIfAbsent(player, scopes::openSession); }

    private void registerCapabilities() {
        var scope = scopes.openModule(ModuleDescriptor.builder().id(PROVIDER).version("1.0.0").build());
        var params = new CapabilitySchema(Map.of("rule", new CapabilitySchema.Field(CapabilitySchema.Type.TEXT, true)));
        var result = new CapabilitySchema(Map.of("state", new CapabilitySchema.Field(CapabilitySchema.Type.TEXT, true)));
        capabilities.register(scope, new CapabilityDescriptor(REPLENISH, 1, PROVIDER, new UiVersion(1, 0, 0),
                params, result, CapabilityDescriptor.Access.WRITE, REPLENISH, CapabilityDescriptor.Deduplication.NONE),
                (caller, request) -> {
                    var id = UUID.fromString(((CapabilityValue.Text) request.parameters().get("rule")).value());
                    var rule = rule(caller.playerId(), id);
                    if (rule == null || !editorMatches(rule)) return CompletableFuture.completedStage(CapabilityResult.notExecuted(CapabilityResult.Code.PERMISSION_DENIED));
                    var player = server.getPlayerList().getPlayer(caller.playerId());
                    if (player == null) return CompletableFuture.completedStage(CapabilityResult.notExecuted(CapabilityResult.Code.SESSION_CLOSED));
                    lease(player, rule.id());
                    var execution = engine.execute(caller.playerId(), id, AutomationAction.replenish(rule, rule.target()));
                    return CompletableFuture.completedStage(CapabilityResult.success(Map.of("state", new CapabilityValue.Text(execution.name()))));
                });
    }

    private void runScript(ServerPlayer player, ReplenishmentRule rule, String source) throws IOException {
        if (backend == null || scripts.containsKey(rule.id()) || source.isBlank()) return;
        if (!backend.authorized(player.getUUID(), rule.targetKey())) return;
        if (worker == null) worker = new ScriptWorkerSupervisor(BundledScriptWorker.extract(directory.resolve("worker")), ScriptWorkerSupervisor.Limits.defaults());
        RuleAdapter.Inventory inventory;
        try { inventory = backend.inventory(rule.owner(), rule.targetKey(), rule.item()); }
        catch (Exception unavailable) { statuses.put(rule.owner(), "denied"); return; }
        var input = new JsonObject();
        input.addProperty("rule", rule.id().toString());
        input.addProperty("stored", inventory.stored());
        input.addProperty("pending", inventory.inFlight());
        input.addProperty("lower", rule.lower());
        input.addProperty("target", rule.target());
        ScopeHandle session = session(rule.owner());
        var view = capabilities.bind(session, new CapabilityCaller(rule.owner()));
        var future = worker.submit(source, input.toString());
        scripts.put(rule.id(), future);
        future.whenComplete((result, failure) -> server.execute(() -> {
            scripts.remove(rule.id(), future);
            if (closed || !session.isOpen() || server.getPlayerList().getPlayer(rule.owner()) != player) return;
            var latest = rule(rule.owner(), rule.id());
            if (latest == null || latest.revision() != rule.revision()) return;
            if (failure != null || result == null || !result.success()) {
                engine.pause(rule.owner(), rule.id());
                statuses.put(rule.owner(), "script_failed");
                return;
            }
            try {
                var requests = JsonParser.parseString(result.payloadJson()).getAsJsonObject().getAsJsonArray("requests");
                if (requests.size() > 32) throw new IllegalArgumentException("Too many requests");
                // Validate the complete batch before executing any request; identity and rule are host-bound.
                for (var request : requests) {
                    var intent = request.getAsJsonObject();
                    if (!REPLENISH.toString().equals(intent.get("capability").getAsString())) throw new IllegalArgumentException("Unsupported capability");
                    var payload = intent.getAsJsonObject("payload");
                    if (payload.size() != 1 || !rule.id().toString().equals(payload.get("rule").getAsString())) throw new IllegalArgumentException("Rule mismatch");
                }
                if (!requests.isEmpty()) {
                    var current = rule(rule.owner(), rule.id());
                    if (current == null || current.revision() != rule.revision()) return;
                    var reference = view.find(REPLENISH, 1).orElseThrow();
                    view.invoke(reference, new CapabilityRequest(UUID.randomUUID(), Map.of("rule", new CapabilityValue.Text(rule.id().toString()))));
                }
                statuses.put(rule.owner(), "script_complete");
            } catch (RuntimeException invalid) {
                engine.pause(rule.owner(), rule.id());
                statuses.put(rule.owner(), "script_failed");
            }
        }));
    }

    private UiValue.Sequence ruleRows(ServerPlayer player) {
        var snapshot = engine.snapshot();
        return new UiValue.Sequence(snapshot.rules().stream().filter(rule -> rule.owner().equals(player.getUUID()))
                .limit(64).<UiValue>map(rule -> {
                    String state = !rule.enabled() ? "disabled" : snapshot.paused().contains(rule.id()) ? "paused" : "ready";
                    var task = snapshot.tasks().stream().filter(t -> t.ruleId().equals(rule.id()) && t.blocksTarget()).findFirst();
                    if (task.isPresent()) state = "task_" + task.orElseThrow().state().name().toLowerCase(Locale.ROOT);
                    String label = rule.id() + " · " + rule.item() + " · " + text(player, state);
                    return new UiValue.Text(label.length() <= 256 ? label : label.substring(0, 255) + "…");
                }).toList());
    }

    private ReplenishmentRule rule(UUID owner, UUID id) {
        return engine.snapshot().rules().stream().filter(rule -> rule.id().equals(id) && rule.owner().equals(owner)).findFirst().orElse(null);
    }
    private static UUID ruleId(UUID owner, String name) {
        if (name.isBlank() || name.length() > 128) throw new IllegalArgumentException("Invalid rule name");
        try { return UUID.fromString(name); }
        catch (IllegalArgumentException ignored) { return UUID.nameUUIDFromBytes((owner + ":" + name).getBytes(StandardCharsets.UTF_8)); }
    }
    private static String string(Map<String, UiValue> data, String field) { return ((UiValue.Text) data.get(field)).value(); }
    private static int integer(Map<String, UiValue> data, String field) { return ((UiValue.Int) data.get(field)).value(); }
    private static String text(ServerPlayer player, String key) { return AutomationText.get(player.clientInformation().language(), key); }

    private void saveBackend() throws IOException { if (backend != null) writeNbt(directory.resolve("adapter.nbt"), backend.save()); }
    private void saveEditors() throws IOException {
        var tag = new CompoundTag();
        editors.forEach((id, editor) -> {
            var entry = new CompoundTag(); entry.putInt("kind", editor.kind); entry.putString("script", editor.script);
            entry.putLong("revision", editor.revision); tag.put(id.toString(), entry);
        });
        writeNbt(directory.resolve("editors.nbt"), tag);
    }
    private void loadEditors() throws IOException {
        Path path = directory.resolve("editors.nbt");
        if (!Files.exists(path)) return;
        var tag = NbtIo.readCompressed(path, NbtAccounter.create(8 * 1024 * 1024));
        for (String id : tag.getAllKeys()) {
            var entry = tag.getCompound(id);
            editors.put(UUID.fromString(id), new Editor(entry.getInt("kind"), entry.getString("script"), entry.getLong("revision")));
        }
    }
    private static void writeNbt(Path path, CompoundTag data) throws IOException {
        Path temp = Files.createTempFile(path.getParent(), "uuk-", ".tmp");
        try {
            NbtIo.writeCompressed(data, temp);
            try (var channel = java.nio.channels.FileChannel.open(temp, StandardOpenOption.WRITE)) { channel.force(true); }
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        List.copyOf(scripts.values()).forEach(future -> future.cancel(true));
        scripts.clear();
        if (worker != null) worker.close();
        cancelPlanning(null, null);
        for (var page : pages) page.close();
        capabilities.close(); scopes.close();
        try { saveBackend(); saveEditors(); }
        catch (IOException failure) { ShBetterUi.LOGGER.error("Automation shutdown snapshot failed", failure); }
        finally {
            if (backend != null) backend.close();
            try { store.close(); } catch (IOException failure) { ShBetterUi.LOGGER.warn("Automation store close failed", failure); }
        }
    }

    private boolean editorMatches(ReplenishmentRule rule) {
        var editor = editors.get(rule.id());
        return editor != null && editor.revision == rule.revision();
    }
    private record Editor(int kind, String script, long revision) {
        Editor { if (kind < 0 || kind > 2 || script.length() > 4096 || revision < 1) throw new IllegalArgumentException("Invalid editor"); }
    }
    private static final class Ae2Factory {
        static AutomationBackend create(MinecraftServer server) {
            return new com.dishanhai.sh_better_ui.automation.ae2.Ae2RuleAdapter(server,
                    (player, pos) -> player.serverLevel().mayInteract(player, pos), () -> {});
        }
    }
    private record UnavailableAdapter(MinecraftServer server) implements RuleAdapter {
        public boolean isOnline(UUID owner) { return server.getPlayerList().getPlayer(owner) != null; }
        public boolean authorized(UUID owner, String target) { return false; }
        public boolean dependencyAvailable(String target) { return false; }
        public Inventory inventory(UUID owner, String target, String item) { throw new IllegalStateException("Adapter unavailable"); }
        public java.util.concurrent.CompletionStage<Submission> submit(UUID intent, AutomationAction action) {
            return CompletableFuture.completedStage(new Submission(SubmissionStatus.DEFINITELY_NOT_EXECUTED, null));
        }
        public TaskStatus query(UUID owner, String task) { return TaskStatus.UNKNOWN; }
    }
}
