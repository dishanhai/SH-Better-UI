// Author: dishanhai
package com.dishanhai.sh_better_ui.automation.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.google.common.collect.ImmutableSet;
import com.dishanhai.sh_better_ui.kernel.api.automation.rule.AutomationAction;
import com.dishanhai.sh_better_ui.kernel.api.automation.rule.RuleAdapter;
import com.dishanhai.sh_better_ui.automation.AutomationBackend;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.function.BiPredicate;

/** Optional AE2 19.2.18 integration. All methods, including tick/close, run on the server thread. */
public final class Ae2RuleAdapter implements AutomationBackend {
    public static final String SUPPORTED_VERSION = Ae2Compatibility.VERSION;
    private static final int MAX_TRACKED_TASKS = 4096;
    private final MinecraftServer server;
    private final Ae2TerminalBinding.AccessPolicy accessPolicy;
    private final Runnable changed;
    private final Map<String, Ae2TerminalBinding> bindings = new HashMap<>();
    private final Map<String, CompoundTag> savedBindings = new HashMap<>();
    private final Map<String, String> targetAliases = new HashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<String, Tracked> tasks = new HashMap<>();
    private final Ae2ReceiptCache intents = new Ae2ReceiptCache(MAX_TRACKED_TASKS);
    private final Map<IGrid, UUID> gridIds = new IdentityHashMap<>();
    private boolean closed;
    private boolean unknownOutcome;
    private BiPredicate<UUID, AutomationAction> submissionGuard = (intent, action) -> false;

    public Ae2RuleAdapter(MinecraftServer server, Ae2TerminalBinding.AccessPolicy accessPolicy, Runnable changed) {
        this.server = Objects.requireNonNull(server);
        this.accessPolicy = Objects.requireNonNull(accessPolicy);
        this.changed = Objects.requireNonNull(changed);
    }

    /** Called only after version negotiation and a server-validated bind action from the current menu. */
    public String bindOpenTerminal(ServerPlayer player) {
        checkThread();
        var binding = Ae2TerminalBinding.fromOpenMenu(player, accessPolicy);
        String key = binding.targetKey();
        bindings.put(player.getUUID() + "/" + key, binding);
        return key;
    }

    @Override public String bind(ServerPlayer player) { return bindOpenTerminal(player); }

    @Override public void restore(CompoundTag saved) { restoreSuspended(saved); }

    @Override public void setSubmissionGuard(BiPredicate<UUID, AutomationAction> guard) {
        checkThread();
        submissionGuard = Objects.requireNonNull(guard);
    }

    @Override public boolean isOnline(UUID owner) {
        checkThread();
        return !closed && server.getPlayerList().getPlayer(owner) != null;
    }

    @Override public boolean authorized(UUID owner, String targetKey) {
        return !unknownOutcome && resolve(owner, targetKey) != null;
    }

    @Override public boolean dependencyAvailable(String targetKey) {
        checkThread();
        return !closed; // This class is loaded only after the optional dependency/version gate.
    }

    @Override public String canonicalTarget(UUID owner, String targetKey) {
        var resolved = resolve(owner, targetKey);
        if (resolved != null) targetAliases.put(targetKey, "ae2-grid:" + gridIds.computeIfAbsent(
                resolved.node.getGrid(), ignored -> UUID.randomUUID()));
        return targetAliases.getOrDefault(targetKey, targetKey);
    }

    @Override public Inventory inventory(UUID owner, String targetKey, String item) {
        var resolved = requireAccess(owner, targetKey);
        var key = itemKey(item);
        var grid = resolved.node.getGrid();
        long stored = Math.max(0, grid.getStorageService().getCachedInventory().get(key));
        long inFlight = 0;
        // getRequestedAmount excludes final output. CPU plans provide a conservative in-flight upper bound.
        for (var cpu : grid.getCraftingService().getCpus()) {
            var status = cpu.getJobStatus();
            if (status != null && status.crafting() != null && key.equals(status.crafting().what())) {
                inFlight = saturatedAdd(inFlight, Math.max(0, status.crafting().amount()));
            }
        }
        return new Inventory(stored, inFlight);
    }

    @Override public CompletionStage<Submission> submit(UUID intentId, AutomationAction action) {
        checkThread();
        var receipt = intents.get(intentId);
        if (receipt != null) return CompletableFuture.completedFuture(receipt);
        if (pending.containsKey(intentId)) return pending.get(intentId).completion;
        if (unknownOutcome) return rejected();
        if (!intents.hasCapacity(pending.size()) || tasks.size() + pending.size() >= MAX_TRACKED_TASKS) return rejected();
        if (!"replenish".equals(action.kind()) || action.amount() > Integer.MAX_VALUE) return rejected();
        var resolved = resolve(action.owner(), action.targetKey());
        if (resolved == null) return rejected();
        var key = itemKey(action.arguments().get("item"));
        if (!resolved.node.getGrid().getCraftingService().isCraftable(key)) return rejected();
        var source = IActionSource.ofPlayer(resolved.player, () -> resolved.node);
        var calculation = resolved.node.getGrid().getCraftingService().beginCraftingCalculation(
                resolved.player.serverLevel(), () -> source, key, action.amount(),
                CalculationStrategy.REPORT_MISSING_ITEMS);
        var request = new Pending(action, resolved.node, resolved.node.getGrid(), calculation);
        pending.put(intentId, request);
        return request.completion;
    }

    /** Cancels only planning; already submitted AE2 jobs remain under AE2's control. */
    @Override public boolean cancelPending(UUID intentId) {
        checkThread();
        var request = pending.get(intentId);
        if (request == null) return false;
        request.calculation.cancel(true);
        finish(intentId, request, new Submission(SubmissionStatus.DEFINITELY_NOT_EXECUTED, null));
        return true;
    }

    /** Polls only completed AE2 futures; never waits on the server thread or starts an extra worker. */
    public void tick() {
        checkThread();
        for (var entry : new ArrayList<>(pending.entrySet())) {
            var request = entry.getValue();
            var resolved = resolve(request.action.owner(), request.action.targetKey());
            if (unknownOutcome || resolved == null || resolved.node != request.node || resolved.node.getGrid() != request.grid
                    || ++request.age > 1200) {
                request.calculation.cancel(true);
                finish(entry.getKey(), request, new Submission(SubmissionStatus.DEFINITELY_NOT_EXECUTED, null));
                continue;
            }
            if (!request.calculation.isDone()) continue;
            Requester requester = null;
            boolean invokedSubmit = false;
            try {
                var plan = request.calculation.get(); // isDone checked above.
                if (plan.simulation()) {
                    finish(entry.getKey(), request, new Submission(SubmissionStatus.DEFINITELY_NOT_EXECUTED, null));
                    continue;
                }
                if (!submissionGuard.test(entry.getKey(), request.action)) {
                    finish(entry.getKey(), request, new Submission(SubmissionStatus.DEFINITELY_NOT_EXECUTED, null));
                    continue;
                }
                requester = new Requester(resolved);
                invokedSubmit = true;
                var result = requester.node.getGrid().getCraftingService().submitJob(plan, requester, null,
                        true, IActionSource.ofPlayer(resolved.player, requester));
                if (!result.successful()) {
                    requester.close();
                    finish(entry.getKey(), request, new Submission(SubmissionStatus.DEFINITELY_NOT_EXECUTED, null));
                } else if (result.link() == null) {
                    requester.close();
                    finish(entry.getKey(), request, new Submission(SubmissionStatus.UNKNOWN, null));
                } else {
                    requester.link = result.link();
                    String taskId = result.link().getCraftingID().toString();
                    tasks.put(taskId, new Tracked(request.action.owner(), request.action.targetKey(), requester,
                            TaskStatus.RUNNING, null));
                    finish(entry.getKey(), request, new Submission(SubmissionStatus.ACCEPTED, taskId));
                }
            } catch (Exception failure) {
                if (requester != null) requester.close();
                finish(entry.getKey(), request, new Submission(invokedSubmit
                        ? SubmissionStatus.UNKNOWN : SubmissionStatus.DEFINITELY_NOT_EXECUTED, null));
            }
        }
        for (var task : tasks.values()) {
            if (task.requester == null) continue;
            var link = task.requester.link;
            if (link.isDone() || link.isCanceled()) {
                task.status = link.isDone() ? TaskStatus.SUCCEEDED : TaskStatus.FAILED;
                task.snapshot = task.requester.snapshot();
                task.requester.close();
                task.requester = null;
                changed.run();
            } else if (!task.requester.physicalHostPresent() || !task.requester.sameGrid()) {
                // A lost physical host/grid is not proof that a CPU has stopped. Never retry it automatically.
                task.snapshot = task.requester.snapshot();
                task.status = TaskStatus.UNKNOWN;
                unknownOutcome = true;
                task.requester.close();
                task.requester = null;
                changed.run();
            }
        }
    }

    @Override public TaskStatus query(UUID owner, String taskId) {
        checkThread();
        var task = tasks.get(taskId);
        if (task == null || !task.owner.equals(owner)) return TaskStatus.UNKNOWN;
        if (task.requester == null) return task.status;
        return task.requester.link.isDone() ? TaskStatus.SUCCEEDED
                : task.requester.link.isCanceled() ? TaskStatus.FAILED : TaskStatus.RUNNING;
    }

    /** Persist this server-owned NBT along with rules. It includes the actual AE2 link, never a fake task id. */
    public CompoundTag save() {
        checkThread();
        var result = new CompoundTag();
        result.putInt("format", 1);
        result.putBoolean("unknownOutcome", unknownOutcome);
        var bindingTags = new CompoundTag();
        savedBindings.forEach((id, tag) -> bindingTags.put(id, tag.copy()));
        bindings.forEach((id, binding) -> bindingTags.put(id, binding.save()));
        result.put("bindings", bindingTags);
        var savedTasks = new CompoundTag();
        tasks.forEach((id, task) -> {
            var tag = new CompoundTag();
            tag.putUUID("owner", task.owner);
            tag.putString("target", task.targetKey);
            tag.putString("status", task.status.name());
            if (task.requester != null) tag.put("ae2", task.requester.snapshot());
            else if (task.snapshot != null) tag.put("ae2", task.snapshot.copy());
            savedTasks.put(id, tag);
        });
        result.put("tasks", savedTasks);
        var savedIntents = new CompoundTag();
        intents.forEach((id, submission) -> {
            var tag = new CompoundTag();
            tag.putString("status", submission.status().name());
            if (submission.taskId() != null) tag.putString("task", submission.taskId());
            savedIntents.put(id.toString(), tag);
        });
        pending.keySet().forEach(id -> {
            var tag = new CompoundTag();
            tag.putString("status", SubmissionStatus.UNKNOWN.name());
            savedIntents.put(id.toString(), tag);
        });
        result.put("intents", savedIntents);
        return result;
    }

    /** Restart recovery is deliberately conservative: CPU reconnection is not inferred from saved NBT. */
    public void restoreSuspended(CompoundTag saved) {
        checkThread();
        if (!tasks.isEmpty() || !pending.isEmpty() || !intents.isEmpty()) throw new IllegalStateException("Already active");
        if (saved.getInt("format") != 1) throw new IllegalArgumentException("Unsupported AE2 snapshot");
        unknownOutcome = saved.getBoolean("unknownOutcome");
        var bindingTags = saved.getCompound("bindings");
        for (String id : bindingTags.getAllKeys()) savedBindings.put(id, bindingTags.getCompound(id).copy());
        var savedTasks = saved.getCompound("tasks");
        if (savedTasks.size() > MAX_TRACKED_TASKS) throw new IllegalArgumentException("AE2 task capacity exceeded");
        for (String id : savedTasks.getAllKeys()) {
            UUID.fromString(id);
            var tag = savedTasks.getCompound(id);
            var previous = TaskStatus.valueOf(tag.getString("status"));
            var status = previous == TaskStatus.SUCCEEDED || previous == TaskStatus.FAILED
                    ? previous : TaskStatus.UNKNOWN;
            unknownOutcome |= status == TaskStatus.UNKNOWN;
            tasks.put(id, new Tracked(tag.getUUID("owner"), tag.getString("target"), null,
                    status, tag.getCompound("ae2").copy()));
        }
        var savedIntents = saved.getCompound("intents");
        for (String id : savedIntents.getAllKeys()) {
            var tag = savedIntents.getCompound(id);
            var status = SubmissionStatus.valueOf(tag.getString("status"));
            unknownOutcome |= status == SubmissionStatus.UNKNOWN;
            intents.record(UUID.fromString(id), new Submission(status,
                    tag.contains("task") ? tag.getString("task") : null));
        }
    }

    @Override public void close() {
        checkThread();
        for (var entry : new ArrayList<>(pending.entrySet())) {
            entry.getValue().calculation.cancel(true);
            finish(entry.getKey(), entry.getValue(), new Submission(SubmissionStatus.DEFINITELY_NOT_EXECUTED, null));
        }
        for (var task : tasks.values()) {
            if (task.requester != null) {
                task.snapshot = task.requester.snapshot();
                task.status = TaskStatus.UNKNOWN;
                unknownOutcome = true;
                task.requester.close();
                task.requester = null;
            }
        }
        bindings.clear();
        gridIds.clear();
        closed = true;
    }

    private void finish(UUID id, Pending request, Submission result) {
        pending.remove(id);
        intents.record(id, result);
        unknownOutcome |= result.status() == SubmissionStatus.UNKNOWN;
        changed.run();
        request.completion.complete(result);
    }

    /** Requires explicit operator reconciliation; elapsed time or a later tick never clears this barrier. */
    public boolean hasUnknownOutcome() {
        checkThread();
        return unknownOutcome;
    }

    private static CompletionStage<Submission> rejected() {
        return CompletableFuture.completedFuture(new Submission(SubmissionStatus.DEFINITELY_NOT_EXECUTED, null));
    }

    private Resolved resolve(UUID owner, String target) {
        checkThread();
        if (closed) return null;
        var player = server.getPlayerList().getPlayer(owner);
        String bindingId = owner + "/" + target;
        var binding = bindings.get(bindingId);
        if (player != null && binding == null && savedBindings.containsKey(bindingId)) {
            binding = Ae2TerminalBinding.restore(player, savedBindings.get(bindingId), accessPolicy);
            if (binding != null) {
                bindings.put(bindingId, binding);
                savedBindings.remove(bindingId);
            }
        }
        if (player == null || binding == null) return null;
        var node = binding.resolve(player, accessPolicy);
        return node == null ? null : new Resolved(player, binding, node);
    }

    private Resolved requireAccess(UUID owner, String target) {
        return Objects.requireNonNull(resolve(owner, target), "Terminal unavailable or access denied");
    }

    private void checkThread() {
        if (!server.isSameThread()) throw new IllegalStateException("AE2 access requires server thread");
    }

    private static AEItemKey itemKey(String item) {
        var id = item == null ? null : ResourceLocation.tryParse(item);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalArgumentException("Unknown item");
        var stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
        return Objects.requireNonNull(AEItemKey.of(stack), "Empty item");
    }

    static long saturatedAdd(long a, long b) {
        return Long.MAX_VALUE - a < b ? Long.MAX_VALUE : a + b;
    }

    private record Resolved(ServerPlayer player, Ae2TerminalBinding binding, IGridNode node) {}

    private static final class Pending {
        final AutomationAction action;
        final IGridNode node;
        final IGrid grid;
        final Future<ICraftingPlan> calculation;
        final CompletableFuture<Submission> completion = new CompletableFuture<>();
        int age;
        Pending(AutomationAction action, IGridNode node, IGrid grid, Future<ICraftingPlan> calculation) {
            this.action = action; this.node = node; this.grid = grid; this.calculation = calculation;
        }
    }

    private static final class Tracked {
        final UUID owner;
        final String targetKey;
        Requester requester;
        TaskStatus status;
        CompoundTag snapshot;
        Tracked(UUID owner, String targetKey, Requester requester, TaskStatus status, CompoundTag snapshot) {
            this.owner = owner; this.targetKey = targetKey; this.requester = requester;
            this.status = status; this.snapshot = snapshot;
        }
    }

    /** A separate public-API node; no changes or services are injected into AE2's terminal node. */
    private final class Requester implements ICraftingRequester {
        private final Resolved origin;
        private final IManagedGridNode node;
        private ICraftingLink link;
        Requester(Resolved origin) {
            this.origin = origin;
            node = GridHelper.createManagedNode(this, (owner, ignored) -> changed.run())
                    .setInWorldNode(false).setFlags(GridFlags.CANNOT_CARRY).setIdlePowerUsage(0)
                    .addService(ICraftingRequester.class, this);
            node.setOwningPlayer(origin.player);
            try {
                node.create(origin.player.serverLevel(), origin.binding.position());
                GridHelper.createConnection(Objects.requireNonNull(node.getNode()), origin.node);
            } catch (RuntimeException failure) {
                node.destroy();
                throw failure;
            }
        }
        @Override public IGridNode getActionableNode() { return node.getNode(); }
        @Override public ImmutableSet<ICraftingLink> getRequestedJobs() {
            return link == null ? ImmutableSet.of() : ImmutableSet.of(link);
        }
        @Override public long insertCraftedItems(ICraftingLink delivered, AEKey what, long amount, Actionable mode) {
            if (delivered != link || !physicalHostPresent() || !sameGrid()) return 0;
            // Only AE2 calls this with actual completed products. No fabricated input is inserted.
            return node.getGrid().getStorageService().getInventory().insert(what, amount, mode,
                    IActionSource.ofMachine(this));
        }
        @Override public void jobStateChange(ICraftingLink changedLink) { changed.run(); }
        boolean sameGrid() {
            try {
                return node.getNode() != null && node.getGrid() == origin.node.getGrid();
            } catch (IllegalStateException unavailableNode) {
                return false;
            }
        }
        boolean physicalHostPresent() {
            var level = server.getLevel(origin.binding.dimension());
            if (level == null || !level.hasChunkAt(origin.binding.position())) return false;
            return appeng.api.parts.PartHelper.getPart(level, origin.binding.position(), origin.binding.side())
                    == origin.binding.originalPart();
        }
        CompoundTag snapshot() {
            var tag = new CompoundTag();
            node.saveToNBT(tag);
            if (link != null) {
                var linkTag = new CompoundTag();
                link.writeToNBT(linkTag);
                tag.put("craftingLink", linkTag);
            }
            return tag;
        }
        void close() { node.destroy(); }
    }
}
