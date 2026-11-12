// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.page.PageService;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageType;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiActionState;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiErrorCode;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiInteractionProof;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiInteractionTarget;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiOpenResult;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocol;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolCapabilities;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolChannel;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolFraming;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolTrace;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolMessage.*;
import com.dishanhai.sh_better_ui.kernel.internal.page.PageDataState;
import com.dishanhai.sh_better_ui.kernel.internal.page.network.PlatformPageNetwork;
import com.dishanhai.sh_better_ui.kernel.internal.protocol.UiInteractionLedger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Map;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Client owner-thread endpoint, bound to the actual native connection. */
final class ClientPageNetwork {
    static final Map<UiId, UiVersion> PROTOCOL_CAPABILITIES = Map.of(
            UiProtocol.CONFIRMED_ACTION, UiProtocol.VERSION,
            UiProtocol.INTERACTION_OPEN, UiProtocol.VERSION,
            UiProtocol.PRIORITY_MODAL, UiProtocol.VERSION);
    private static final long TIMEOUT = TimeUnit.SECONDS.toNanos(8);
    private static final long CONFIRMATION_TIMEOUT = TimeUnit.SECONDS.toNanos(30);
    private static final long INTERACTION_TIMEOUT = TimeUnit.SECONDS.toNanos(3);
    private static final long FRAME_WINDOW = TimeUnit.SECONDS.toNanos(1);
    static final boolean TRACE = Boolean.getBoolean("shb.pages.trace");
    private final ClientPages pages;
    private Connection connection;
    private UiProtocolFraming.Reassembler initial;
    private UiProtocolChannel channel;
    private UiProtocolCapabilities peer;
    private final Map<UUID, Mounted> mounted = new LinkedHashMap<>();
    private final Map<UUID, Queued> queued = new LinkedHashMap<>();
    private final Set<UUID> seen = new HashSet<>();
    private final Set<UiPageType> allowed = EnumSet.noneOf(UiPageType.class);
    private final UiInteractionLedger interactions = new UiInteractionLedger();
    private long handshakeDeadline;
    private long frameWindow;
    private int framesReceived;
    private boolean negotiated;
    private boolean stopped;
    private boolean priorityModal;
    private boolean interactionAllowed;
    private UiInteractionProof explicitRequest;
    private long explicitRequestStarted;

    ClientPageNetwork(ClientPages pages) { this.pages = pages; }

    void login(Object nativeConnection) {
        if (!(nativeConnection instanceof Connection next) || next == connection) return;
        logout(connection);
        connection = next;
        initial = new UiProtocolFraming.Reassembler();
        allowed.clear();
        priorityModal = false;
        interactionAllowed = false;
        interactions.clear();
        seen.clear();
        stopped = false;
        frameWindow = System.nanoTime();
        framesReceived = 0;
        PlatformPageNetwork.announce(next);
    }

    void logout(Object nativeConnection) {
        if (connection == null || nativeConnection != null && nativeConnection != connection) return;
        connection = null;
        terminate();
        initial = null;
        allowed.clear();
        priorityModal = false;
        interactionAllowed = false;
        interactions.clear();
        seen.clear();
    }

    void permissions(Minecraft minecraft, UiPageType type) {
        if (!ClientPages.PAGE_TYPES.contains(type)) return;
        permissionPrompt(minecraft, type.name().toLowerCase(java.util.Locale.ROOT), () -> allowed.contains(type),
                granted -> {
                    if (granted) allowed.add(type);
                    else {
                        allowed.remove(type);
                        revoke(Authorization.PUSH, type);
                    }
                });
    }

    void priorityPermissions(Minecraft minecraft) {
        permissionPrompt(minecraft, "priority_modal", () -> priorityModal, granted -> {
            priorityModal = granted;
            if (!granted) revoke(Authorization.PRIORITY, UiPageType.MODAL);
        });
    }

    void interactionPermissions(Minecraft minecraft) {
        permissionPrompt(minecraft, "interaction", () -> interactionAllowed, granted -> {
            interactionAllowed = granted;
            if (!granted) revoke(Authorization.TARGET, null);
        });
    }

    private void permissionPrompt(Minecraft minecraft, String purpose, BooleanSupplier currentPermission,
                                  Consumer<Boolean> apply) {
        if (connection == null || minecraft.screen != null || minecraft.getOverlay() != null) return;
        var current = connection;
        var server = minecraft.getCurrentServer();
        var identity = server == null ? Component.translatable("menu.singleplayer") : Component.literal(server.ip);
        var permission = "sh_better_ui.pages.permission." + purpose;
        var title = Component.translatable("sh_better_ui.pages.permission.title", identity,
                Component.translatable(permission));
        var message = Component.translatable(currentPermission.getAsBoolean()
                ? "sh_better_ui.pages.permission.allowed" : "sh_better_ui.pages.permission.denied");
        minecraft.setScreen(new ConfirmScreen(granted -> {
            if (connection == current) {
                apply.accept(granted);
                minecraft.setScreen(null);
            }
        }, title, message, Component.translatable("sh_better_ui.pages.permission.allow"),
                Component.translatable("sh_better_ui.pages.permission.revoke")));
    }

    private void revoke(Authorization origin, UiPageType type) {
        for (var request : List.copyOf(queued.values())) {
            if (request.origin == origin && (type == null || request.open.page().type() == type)) {
                queued.remove(request.open.instanceId());
                reject(request.open.instanceId(), UiErrorCode.PERMISSION_DENIED);
            }
        }
        for (var surface : List.copyOf(mounted.values())) {
            if (surface.origin == origin && (type == null || surface.page.definition.type() == type)) surface.page.close();
        }
    }

    boolean requestPage(UiId page) {
        var minecraft = Minecraft.getInstance();
        if (!supports(UiProtocol.INTERACTION_OPEN) || minecraft.screen != null || minecraft.getOverlay() != null
                || minecraft.level == null || explicitRequest != null) return false;
        long now = System.nanoTime();
        var proof = interactions.observe(new UiInteractionTarget.Page(page), now);
        if (proof.isEmpty()) return false;
        explicitRequest = proof.orElseThrow();
        explicitRequestStarted = now;
        send(new InteractionRequest(explicitRequest));
        return negotiated;
    }

    void observeInteraction(Minecraft minecraft) {
        if (!interactionAllowed || !supports(UiProtocol.INTERACTION_OPEN) || minecraft.screen != null
                || minecraft.getOverlay() != null || minecraft.level == null) return;
        var dimension = UiId.parse(minecraft.level.dimension().location().toString());
        UiInteractionTarget target;
        if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            var pos = hit.getBlockPos();
            target = new UiInteractionTarget.Block(dimension, pos.getX(), pos.getY(), pos.getZ());
        } else if (minecraft.hitResult instanceof EntityHitResult hit) {
            target = new UiInteractionTarget.Entity(dimension, hit.getEntity().getUUID());
        } else {
            return;
        }
        interactions.observe(target, System.nanoTime()).ifPresent(proof -> send(new InteractionRequest(proof)));
    }

    void tick() {
        var current = connection;
        if (current == null || stopped || !current.isConnected()) return;
        long now = System.nanoTime();
        if (channel == null && PlatformPageNetwork.supported(current)) {
            channel = new UiProtocolChannel(UUID.randomUUID(), frame -> PlatformPageNetwork.sendClient(current, frame));
            channel.setTracing(TRACE);
            handshakeDeadline = now + TIMEOUT;
            send(new Hello(capabilities()));
        }
        if (!negotiated && channel != null && now - handshakeDeadline >= 0) terminate();
        if (channel != null) channel.expireFragments(now);
        if (initial != null) initial.expire(now);
        interactions.expire(now);
        if (explicitRequest != null && now - explicitRequestStarted >= INTERACTION_TIMEOUT) {
            finishExplicitRequest(UiErrorCode.OPEN_TIMEOUT);
        }
        for (var surface : List.copyOf(mounted.values())) {
            surface.page.tick(now);
            if (!surface.page.snapshot().synchronizedData() && surface.syncRetryPending
                    && now - surface.syncRetryAt >= 0 && now - surface.syncDeadline < 0) {
                surface.syncRetryPending = false;
                send(new StateRequest(surface.page.id));
            }
            if (surface.resumePending) {
                if (surface.resumeReady && surface.page.resume()) {
                    surface.resumePending = false;
                    surface.resumeReady = false;
                } else if (now - surface.resumeDeadline >= 0) {
                    surface.page.close();
                } else if (!surface.resumeReady && surface.resumeRetryPending && now - surface.resumeRetryAt >= 0) {
                    surface.resumeRetryPending = false;
                    send(new PageVisibility(surface.page.id, false));
                }
            }
            if (surface.page.snapshot().state() == PageService.State.OPENING && now - surface.deadline >= 0
                    || !surface.page.snapshot().synchronizedData() && now - surface.syncDeadline >= 0) surface.page.close();
        }
        for (var request : queued.values().stream().sorted(java.util.Comparator.comparing(
                candidate -> !candidate.open.highPriority())).toList()) {
            if (!negotiated) break;
            var id = request.open.instanceId();
            if (!pages.available(request.open.page().type())) {
                queued.remove(id);
                reject(id, UiErrorCode.CAPABILITY_MISSING);
            } else if (now - request.deadline >= 0) {
                queued.remove(id);
                reject(id, UiErrorCode.OPEN_TIMEOUT);
            } else if (pages.canOpenRemote(request.open.page().type(), request.open.highPriority())) {
                queued.remove(id);
                mount(request);
            }
        }
    }

    void receive(Connection source, byte[] frame) {
        if (source != connection || stopped) return;
        try {
            long now = System.nanoTime();
            if (now - frameWindow >= FRAME_WINDOW) {
                frameWindow = now;
                framesReceived = 0;
            }
            if (++framesReceived > 160) {
                terminate();
                return;
            }
            if (channel == null) {
                var hello = UiProtocolChannel.acceptInitial(initial, frame, now);
                if (hello.isEmpty()) return;
                var packet = hello.orElseThrow();
                var offer = ((Hello) packet.payload()).capabilities();
                if (!compatible(offer)) { terminate(); return; }
                channel = new UiProtocolChannel(packet, bytes -> PlatformPageNetwork.sendClient(source, bytes));
                channel.setTracing(TRACE);
                send(new Capabilities(capabilities()));
                if (channel != null) negotiated(offer);
                return;
            }
            channel.receive(frame, now).ifPresent(message -> accept(message.payload()));
        } catch (IllegalArgumentException | IllegalStateException | SecurityException invalid) {
            terminate();
        }
    }

    void showTrace(Minecraft minecraft) {
        if (!TRACE || minecraft.player == null || channel == null) return;
        var records = channel.traceSnapshot();
        int start = Math.max(0, records.size() - 8);
        for (var record : records.subList(start, records.size())) {
            minecraft.player.sendSystemMessage(Component.translatable("sh_better_ui.pages.trace.entry",
                    record.direction().name(), record.type().name(), record.version().toString(),
                    Long.toString(record.sequence()), Integer.toString(record.messageBytes()),
                    record.error().map(Enum::name).orElse("")));
        }
    }

    Diagnostics diagnostics() {
        return new Diagnostics(negotiated, Optional.ofNullable(channel).map(UiProtocolChannel::sessionId),
                Optional.ofNullable(peer), Set.copyOf(allowed), priorityModal, interactionAllowed, mounted.size(), queued.size(),
                channel == null ? List.of() : channel.traceSnapshot());
    }

    record Diagnostics(boolean negotiated, Optional<UUID> sessionId, Optional<UiProtocolCapabilities> peer,
                       Set<UiPageType> permissions, boolean priorityModal, boolean interactionAllowed, int mounted, int queued,
                       List<UiProtocolTrace> trace) {}

    UiProtocolCapabilities capabilities() {
        var components = new HashMap<>(pages.remoteComponents());
        components.putAll(PROTOCOL_CAPABILITIES);
        return UiProtocolCapabilities.standard(components, pages.pageTypes());
    }

    private void negotiated(UiProtocolCapabilities remote) {
        channel.limitMessages(remote.maxMessageBytes());
        peer = remote;
        negotiated = true;
    }

    private boolean supports(UiId capability) {
        return negotiated && peer != null && peer.supports(capability, UiProtocol.VERSION);
    }

    private void accept(Payload payload) {
        if (payload instanceof Terminate) {
            terminate();
            return;
        }
        if (!negotiated) {
            if (payload instanceof Capabilities reply && compatible(reply.capabilities())) {
                negotiated(reply.capabilities());
            } else terminate();
            return;
        }
        if (payload instanceof PageOpen open) {
            open(open);
        } else if (payload instanceof PageClose close) {
            close(close.instanceId());
        } else if (payload instanceof HudUnmount close) {
            close(close.instanceId());
        } else if (payload instanceof DataUpdate update) {
            var surface = mounted.get(update.instanceId());
            if (surface == null) return;
            boolean alreadyRequested = !surface.page.snapshot().synchronizedData();
            var applied = surface.page.patch(update.baseRevision(), update.revision(), update.updates());
            if (applied == PageDataState.Update.SNAPSHOT_REQUIRED && !alreadyRequested) {
                requestSnapshot(surface);
            }
        } else if (payload instanceof StateSnapshot snapshot) {
            var surface = mounted.get(snapshot.instanceId());
            if (surface != null) surface.page.restore(snapshot.revision(), snapshot.data());
        } else if (payload instanceof ActionResult result) {
            var surface = mounted.get(result.instanceId());
            if (surface != null) {
                if (surface.page.hasRequest(result.requestId())
                        && result.error().filter(error -> error == UiErrorCode.REVISION_MISMATCH).isPresent()
                        && surface.page.snapshot().synchronizedData()) {
                    surface.page.requireSynchronization();
                    requestSnapshot(surface);
                }
                if (surface.challenge != null && surface.challenge.requestId().equals(result.requestId())) {
                    surface.challenge = null;
                }
                surface.page.result(result.requestId(), result.state());
            }
        } else if (payload instanceof ActionChallenge challenge && supports(UiProtocol.CONFIRMED_ACTION)) {
            confirmAction(challenge);
        } else if (payload instanceof PageResumed resumed && supports(UiProtocol.PRIORITY_MODAL)) {
            var surface = mounted.get(resumed.instanceId());
            if (surface != null && surface.resumePending) {
                if (resumed.error().isPresent() || !surface.page.prepareResume(resumed.revision(), resumed.data())) {
                    close(resumed.instanceId());
                } else {
                    surface.resumeReady = true;
                }
            }
        } else if (payload instanceof InteractionRejected rejected && explicitRequest != null
                && explicitRequest.attempt() == rejected.attempt() && explicitRequest.token().equals(rejected.token())) {
            interactions.consume(explicitRequest, System.nanoTime());
            finishExplicitRequest(rejected.error());
        }
    }

    private void requestSnapshot(Mounted surface) {
        long now = System.nanoTime();
        surface.syncDeadline = now + TIMEOUT;
        surface.syncRetryAt = now + TIMEOUT / 2;
        surface.syncRetryPending = true;
        send(new StateRequest(surface.page.id));
    }

    private void finishExplicitRequest(UiErrorCode error) {
        var proof = explicitRequest;
        explicitRequest = null;
        var player = Minecraft.getInstance().player;
        if (error != null && player != null && proof != null && proof.target() instanceof UiInteractionTarget.Page page) {
            player.sendSystemMessage(Component.translatable("sh_better_ui.pages.request.rejected",
                    page.pageId().toString(), error.name()));
        }
    }

    private void confirmAction(ActionChallenge challenge) {
        var surface = mounted.get(challenge.instanceId());
        if (surface == null || surface.challenge != null) return;
        surface.challenge = challenge;
        boolean shown = surface.page.confirm(challenge.requestId(), challenge.prompt(),
                System.nanoTime() + CONFIRMATION_TIMEOUT, accepted -> {
                    if (mounted.get(challenge.instanceId()) != surface || surface.challenge != challenge) return;
                    surface.page.extendRequest(challenge.requestId(), System.nanoTime() + TIMEOUT);
                    send(new ActionConfirm(challenge.instanceId(), challenge.requestId(), challenge.token(), accepted));
                });
        if (!shown) {
            surface.challenge = null;
            send(new ActionConfirm(challenge.instanceId(), challenge.requestId(), challenge.token(), false));
        }
    }

    private void open(PageOpen open) {
        var id = open.instanceId();
        if (seen.contains(id)) return;
        if (!pages.available(open.page().type())) {
            reject(id, open.page().type() == UiPageType.CONTAINER
                    ? UiErrorCode.CONTAINER_UNAVAILABLE : UiErrorCode.CAPABILITY_MISSING);
            return;
        }
        if (mounted.size() + queued.size() >= 8 || seen.size() >= 4096) {
            reject(id, UiErrorCode.RESOURCE_LIMIT_EXCEEDED);
            return;
        }
        long now = System.nanoTime();
        long deadline = now + TIMEOUT;
        Authorization origin;
        if (open.highPriority()) {
            if (!supports(UiProtocol.PRIORITY_MODAL) || !priorityModal || open.interaction().isPresent()) {
                reject(id, UiErrorCode.PERMISSION_DENIED);
                return;
            }
            origin = Authorization.PRIORITY;
        } else if (open.interaction().isPresent()) {
            var proof = open.interaction().orElseThrow();
            if (!supports(UiProtocol.INTERACTION_OPEN) || !interactionTargetCurrent(proof.target())
                    || proof.target() instanceof UiInteractionTarget.Page && !proof.equals(explicitRequest)) {
                reject(id, UiErrorCode.INVALID_INTERACTION);
                return;
            }
            var expires = interactions.consumeDeadline(proof, now);
            if (expires.isEmpty()) {
                reject(id, UiErrorCode.INVALID_INTERACTION);
                return;
            }
            deadline = expires.getAsLong();
            origin = proof.target() instanceof UiInteractionTarget.Page ? Authorization.REQUEST : Authorization.TARGET;
        } else {
            if (!allowed.contains(open.page().type())) {
                reject(id, UiErrorCode.PERMISSION_DENIED);
                return;
            }
            origin = Authorization.PUSH;
        }
        try {
            pages.validateRemote(open.page(), open.data());
        } catch (ClientPages.Rejected invalid) {
            reject(id, invalid.code);
            return;
        } catch (IllegalArgumentException invalid) {
            reject(id, UiErrorCode.SCHEMA_INVALID);
            return;
        }
        seen.add(id);
        send(new PageOpened(id, UiOpenResult.Status.AUTHORIZED, Optional.empty()));
        if (!negotiated) return;
        var request = new Queued(open, deadline, origin);
        if (!pages.canOpenRemote(open.page().type(), open.highPriority())) {
            if (queued.size() >= 2) reject(id, UiErrorCode.RESOURCE_LIMIT_EXCEEDED);
            else queued.put(id, request);
            return;
        }
        mount(request);
    }

    private boolean interactionTargetCurrent(UiInteractionTarget target) {
        var level = Minecraft.getInstance().level;
        if (level == null) return false;
        if (target instanceof UiInteractionTarget.Page) return true;
        if (!interactionAllowed) return false;
        var dimension = UiId.parse(level.dimension().location().toString());
        if (target instanceof UiInteractionTarget.Block block) return block.dimension().equals(dimension);
        return target instanceof UiInteractionTarget.Entity entity && entity.dimension().equals(dimension);
    }

    private void mount(Queued request) {
        var open = request.open;
        var id = open.instanceId();
        var covered = open.highPriority() ? pages.suspendForModal() : null;
        if (covered != null) {
            send(new PageVisibility(covered.id, true));
            if (!negotiated) return;
        }
        try {
            var created = pages.openRemote(id, open.page(), open.data(), covered, (action, payload) -> {
                var surface = mounted.get(id);
                if (surface == null) return;
                var requestId = UUID.randomUUID();
                surface.page.request(requestId, action, System.nanoTime() + TIMEOUT);
                send(new ActionRequest(id, requestId, action, surface.page.data.revision(), payload));
            }, () -> {
                var surface = mounted.remove(id);
                if (surface != null && surface.displayed && !surface.peerClosed) {
                    send(open.page().type() == UiPageType.HUD ? new HudUnmount(id) : new PageClose(id));
                }
                resumeCovered(covered);
            });
            var surface = new Mounted(created, request.deadline, request.origin);
            mounted.put(id, surface);
            send(new PageOpened(id, UiOpenResult.Status.CONFIRMED, Optional.empty()));
            created.displayed().thenAccept(shown -> {
                if (shown && mounted.get(id) == surface) {
                    surface.displayed = true;
                    if (request.origin == Authorization.REQUEST) finishExplicitRequest(null);
                    send(new PageOpened(id, UiOpenResult.Status.DISPLAYED, Optional.empty()));
                } else if (!surface.peerClosed) {
                    reject(id, pages.available(open.page().type())
                            ? UiErrorCode.SESSION_EXPIRED : UiErrorCode.CAPABILITY_MISSING);
                }
            });
        } catch (ClientPages.Rejected invalid) {
            reject(id, invalid.code);
            resumeCovered(covered);
        } catch (IllegalArgumentException invalid) {
            reject(id, UiErrorCode.SCHEMA_INVALID);
            resumeCovered(covered);
        } catch (IllegalStateException invalid) {
            reject(id, UiErrorCode.INTERNAL_ERROR);
            resumeCovered(covered);
        }
    }

    private void resumeCovered(ClientPages.OpenPage covered) {
        if (covered == null || !negotiated) return;
        var surface = mounted.get(covered.id);
        if (surface == null || surface.resumePending || surface.peerClosed) return;
        long now = System.nanoTime();
        surface.resumePending = true;
        surface.resumeDeadline = now + TIMEOUT;
        surface.resumeRetryAt = now + TIMEOUT / 2;
        surface.resumeRetryPending = true;
        send(new PageVisibility(covered.id, false));
    }

    private void reject(UUID id, UiErrorCode code) {
        send(new PageOpened(id, UiOpenResult.Status.REJECTED, Optional.of(code)));
    }

    private void close(UUID id) {
        queued.remove(id);
        var surface = mounted.get(id);
        if (surface == null) return;
        surface.peerClosed = true;
        surface.page.close();
    }

    private static boolean compatible(UiProtocolCapabilities remote) {
        return remote.protocolVersions().contains(UiProtocol.VERSION)
                && remote.schemaVersions().contains(UiPageDefinition.SCHEMA_VERSION);
    }

    private void send(Payload payload) {
        if (channel == null || connection == null || !connection.isConnected()) return;
        try {
            channel.send(payload);
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            terminate();
        }
    }

    private void terminate() {
        channel = null;
        peer = null;
        negotiated = false;
        stopped = true;
        queued.clear();
        interactions.clear();
        explicitRequest = null;
        for (var surface : List.copyOf(mounted.values())) {
            surface.peerClosed = true;
            surface.page.close();
        }
    }

    private enum Authorization { PUSH, PRIORITY, TARGET, REQUEST }

    private record Queued(PageOpen open, long deadline, Authorization origin) {}

    private static final class Mounted {
        final ClientPages.OpenPage page;
        final long deadline;
        final Authorization origin;
        long syncDeadline;
        long syncRetryAt;
        boolean syncRetryPending;
        long resumeDeadline;
        long resumeRetryAt;
        boolean resumePending;
        boolean resumeRetryPending;
        boolean resumeReady;
        boolean displayed;
        boolean peerClosed;
        ActionChallenge challenge;

        Mounted(ClientPages.OpenPage page, long deadline, Authorization origin) {
            this.page = page;
            this.deadline = deadline;
            this.origin = origin;
        }
    }
}
