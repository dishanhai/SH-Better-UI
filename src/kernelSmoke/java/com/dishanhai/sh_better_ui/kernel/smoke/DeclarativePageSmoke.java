// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.smoke;

import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiLocalModal;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.RegistrationHandle;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import com.dishanhai.sh_better_ui.kernel.api.page.*;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiErrorCode;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiActionPrompt;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiInteractionTarget;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiOpenResult;
import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;
import com.dishanhai.sh_better_ui.kernel.internal.CorrectnessInspectorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

final class DeclarativePageSmoke {
    private static final UiId PAGE = UiId.parse("sh_better_ui:smoke/declarative");
    private static final UiId MODAL = UiId.parse("sh_better_ui:smoke/modal");
    private static final UiId ACTION = UiId.parse("sh_better_ui:smoke/exchange");
    private static final UiId REGRESSION_PAGE = UiId.parse("sh_better_ui:smoke/page_input");
    private static final Map<String, UiValue> INITIAL = Map.of("balance", new UiValue.Int(5));

    private static final UiValue.ItemReference SAMPLE = new UiValue.ItemReference(UiId.parse("minecraft:stone"), 1);
    private enum Stage {
        LOCAL_REGRESSION_OPEN, LOCAL_REGRESSION_INPUT, LOCAL_REGRESSION_TOOLTIP,
        LOCAL_OPEN, LOCAL_RENDERED, LOCAL_GHOST, LOCAL_SCALE, LOCAL_RELEASE, STUDIO, JOINING, KUBEJS, KUBEJS_RELOAD,
        KUBEJS_READABILITY, CONTAINER,
        ALLOW, DENIED, ALLOW_MODAL, ALLOW_PRIORITY, REMOTE_REQUEST, REMOTE_ACTIVE, REMOTE_CANCEL, REMOTE_RETRY,
        REMOTE_CONFIRM, REMOTE_UPDATED, MODAL_ACTIVE, MODAL_RESTORED, ALLOW_TARGET, TARGET_WRONG,
        TARGET_ACTIVE, TARGET_REPLAY, HUD, NOTIFICATION, CONTAINER_RECONNECT, RECONNECT_DENIED
    }

    private final ModuleContext module;
    private final Screen title;
    private final String world = "page-smoke-" + System.nanoTime();
    private final AtomicInteger localActions = new AtomicInteger();
    private final AtomicInteger authorityActions = new AtomicInteger();
    private Stage stage = Stage.LOCAL_REGRESSION_OPEN;
    private Stage reported;
    private long nextProbe;
    private long ghostReady;
    private int originalScale;
    private RegistrationHandle localRegistration;
    private PageService.Instance local;
    private long regressionDeadline;
    private volatile RegistrationHandle serverRegistration;
    private RegistrationHandle modalRegistration;
    private CompletableFuture<UiOpenResult> remote;
    private CompletableFuture<UiOpenResult> modal;
    private Screen covered;
    private UiInteractionTarget.Block observedTarget;
    private CompletableFuture<UiOpenResult> interaction;
    private long proofAt;
    private PageService.Instance hud;
    private PageService.Instance notification;
    private final NativePageContainerSmoke container = new NativePageContainerSmoke();
    private Screen oldScriptScreen;
    private CompletableFuture<Void> scriptReload;
    private boolean scriptClicked;
    private boolean scriptPopupClicked;

    DeclarativePageSmoke(ModuleContext module, Screen title) {
        this.module = module;
        this.title = title;
    }

    String stage() { return stage.name(); }

    boolean advance(Minecraft client, KernelClientSmoke.Platform game) throws Exception {
        if (!client.isWindowActive()) {
            client.setWindowActive(true);
        }
        if (stage != reported) {
            reported = stage;
            System.out.println("DECLARATIVE_PAGE_SMOKE " + stage);
        }
        switch (stage) {
            case LOCAL_REGRESSION_OPEN -> {
                var service = UnifiedUI.pages();
                var page = service.define(REGRESSION_PAGE).title("Page input regressions")
                        .field("hint", UiValueSchema.text(256))
                        .root("root", "column", root -> root
                                .child("layers", "stack", stack -> stack
                                        .child("lower", "tooltip", lower -> lower.text("Lower"))
                                        .child("upper", "tooltip", upper -> upper.text("Upper")
                                                .property("enabled", new UiValue.Bool(false))))
                                .child("hint", "button", button -> button.label("Bound hint").bind("tooltip", "hint"))
                                .child("search", "search_field", search -> search.property("value", new UiValue.Text("Iron")))
                                .child("list", "list_view", list -> list.property("search", new UiValue.Text("search"))
                                        .property("height", new UiValue.Int(20))
                                        .property("items", new UiValue.Sequence(List.of(new UiValue.Text("Iron"),
                                                new UiValue.Text("Copper"), new UiValue.Text("Gold"))))))
                        .build();
                localRegistration = service.register(module, page);
                local = service.open(module, REGRESSION_PAGE, Map.of("hint", new UiValue.Text("Old hint")),
                        ignored -> {});
                stage = Stage.LOCAL_REGRESSION_INPUT;
            }
            case LOCAL_REGRESSION_INPUT -> {
                if (!local.displayed().toCompletableFuture().isDone()) return false;
                require(local.displayed().toCompletableFuture().join(), "Regression page did not present");
                var screen = (UiScreen) client.screen;
                var top = widget(screen, "Upper");
                var bottom = widget(screen, "Lower");
                require(top.getX() == bottom.getX() && top.getY() == bottom.getY(), "Stack fixture does not overlap");
                require(screen.getChildAt(top.getX() + 1, top.getY() + 1).orElse(null) == top,
                        "Pointer hit the bottom of a passive declarative stack");
                KernelClientSmoke.move(client, top.getX() + 2, top.getY() + 2);
                game.flushPointer(client);
                require(widget(screen, "Bound hint").getTooltip() != null, "Bound tooltip did not initialize");
                var search = screen.children().stream().filter(EditBox.class::isInstance)
                        .map(EditBox.class::cast).findFirst().orElseThrow();
                require(search.getValue().equals("Iron"), "Search fixture did not initialize");
                var list = widget(screen, Component.translatable("sh_better_ui.pages.list").getString());
                require(!screen.mouseScrolled(list.getX() + 2, list.getY() + 2, 0, -1),
                        "Prefilled search let the single-result list scroll");
                require(UnifiedUI.pages().update(module, local.snapshot().instanceId(),
                        Map.of("hint", new UiValue.Text(""))), "Could not clear the bound tooltip field");
                regressionDeadline = System.nanoTime() + 1_000_000_000L;
                stage = Stage.LOCAL_REGRESSION_TOOLTIP;
            }
            case LOCAL_REGRESSION_TOOLTIP -> {
                var screen = (UiScreen) client.screen;
                var top = widget(screen, "Upper");
                var bottom = widget(screen, "Lower");
                boolean cleared = widget(screen, "Bound hint").getTooltip() == null;
                if (!cleared || !top.isHovered()) {
                    require(System.nanoTime() - regressionDeadline < 0,
                            !cleared ? "Empty bound tooltip kept the previous message" : "Top passive tooltip did not hover");
                    return false;
                }
                require(!bottom.isHovered(), "Covered passive tooltip also received hover");
                localRegistration.close();
                stage = Stage.LOCAL_OPEN;
            }
            case LOCAL_OPEN -> {
                var service = UnifiedUI.pages();
                localRegistration = service.register(module, definition(UiActionPolicy.LOCAL));
                local = service.open(module, PAGE, INITIAL, id -> {
                    require(ACTION.equals(id), "Undeclared local action");
                    localActions.incrementAndGet();
                });
                stage = Stage.LOCAL_RENDERED;
            }
            case LOCAL_RENDERED -> {
                if (!local.displayed().toCompletableFuture().isDone()) return false;
                require(local.displayed().toCompletableFuture().join(), "Local page never became active");
                click(client.screen, Component.literal("Exchange"));
                require(localActions.get() == 1, "Local action did not run exactly once");
                click(client.screen, Component.translatable("sh_better_ui.pages.toggle_value",
                        Component.literal("Local setting"), Component.translatable("options.off")));
                require(new UiValue.Bool(true).equals(local.localValues().get("toggle")), "Toggle draft did not change");
                var input = client.screen.children().stream().filter(EditBox.class::isInstance)
                        .map(EditBox.class::cast).findFirst().orElseThrow();
                clickWidget(client.screen, input);
                require(client.screen.charTyped('字', 0), "Text field did not receive input");
                require(new UiValue.Text("字").equals(local.localValues().get("editor")), "Text draft did not change");
                click(client.screen, Component.translatable("sh_better_ui.pages.ghost_empty"));
                ghostReady = System.nanoTime() + 550_000_000;
                stage = Stage.LOCAL_GHOST;
            }
            case LOCAL_GHOST -> {
                if (System.nanoTime() - ghostReady < 0) return false;
                var picker = UiLocalModal.current(client.screen);
                require(picker != null, "Ghost selector did not open a local modal");
                require(picker.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0), "Ghost selector did not accept keyboard navigation");
                picker.keyReleased(GLFW.GLFW_KEY_DOWN, 0, 0);
                require(picker.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0), "Ghost selector did not accept keyboard confirmation");
                picker.keyReleased(GLFW.GLFW_KEY_ENTER, 0, 0);
                stage = Stage.LOCAL_SCALE;
            }
            case LOCAL_SCALE -> {
                if (!SAMPLE.equals(local.localValues().get("ghost")) || UiLocalModal.current(client.screen) != null) return false;
                var screen = (UiScreen) client.screen;
                var first = widget(screen, "Scroll item 0");
                var last = widget(screen, "Scroll item 4");
                int before = last.getY();
                require(screen.mouseScrolled(first.getX() + 2, first.getY() + 2, 0, -4)
                        && last.getY() < before, "Nested viewport did not scroll");
                var focus = screen.getFocused();
                screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
                require(screen.getFocused() != null && screen.getFocused() != focus, "Tab did not advance focus");
                originalScale = client.options.guiScale().get();
                client.options.guiScale().set(1);
                client.resizeDisplay();
                stage = Stage.LOCAL_RELEASE;
            }
            case LOCAL_RELEASE -> {
                require(client.getWindow().getGuiScale() == 1 && client.screen instanceof UiScreen,
                        "GUI Scale changed the native page lifecycle");
                require(SAMPLE.equals(local.localValues().get("ghost"))
                        && new UiValue.Text("字").equals(local.localValues().get("editor")), "GUI Scale reset page drafts");
                client.options.guiScale().set(originalScale);
                client.resizeDisplay();
                require(UnifiedUI.pages().update(module, local.snapshot().instanceId(),
                        Map.of("balance", new UiValue.Int(4))), "Local data update failed");
                require(local.snapshot().revision() == 1, "Local revision was not published");
                localRegistration.close();
                require(local.closed().toCompletableFuture().join().state() == PageService.State.CLOSED,
                        "Local registration did not release its page");
                require(client.screen == title, "Closing the page did not restore its parent");
                ClientUiKernel.openPageStudio(client);
                stage = Stage.STUDIO;
            }
            case STUDIO -> {
                require(client.screen instanceof CorrectnessInspectorScreen, "Page Studio did not use the Inspector");
                click(client.screen, Component.translatable("sh_better_ui.correctness.inspector.tab.page_layout"));
                click(client.screen, Component.translatable("sh_better_ui.correctness.inspector.tab.page_protocol"));
                click(client.screen, Component.translatable("sh_better_ui.pages.studio.refresh"));
                client.screen.onClose();
                require(client.screen == title, "Page Studio did not restore its parent");
                stage = Stage.JOINING;
                game.join(client, title, world);
            }
            case JOINING -> {
                if (client.player == null || client.getSingleplayerServer() == null
                        || client.screen != null && !scriptScreen(client)) return false;
                stage = Boolean.getBoolean("shb.kernel.smoke.pages.kubejs") ? Stage.KUBEJS : Stage.CONTAINER;
            }
            case KUBEJS -> {
                if (!scriptScreen(client)) return false;
                if (!scriptClicked) {
                    click(client.screen, Component.literal("KubeJS acceptance action"));
                    scriptClicked = true;
                }
                if (!hasWidget(client.screen, "1")) return false;
                oldScriptScreen = client.screen;
                reloadKubeJs();
                scriptReload = client.reloadResourcePacks();
                scriptClicked = false;
                stage = Stage.KUBEJS_RELOAD;
            }
            case KUBEJS_RELOAD -> {
                if (!scriptReload.isDone() || !scriptScreen(client) || client.screen == oldScriptScreen) return false;
                scriptReload.join();
                if (!scriptClicked) {
                    require(hasWidget(client.screen, "0"), "KubeJS reload reused the retired page state");
                    click(client.screen, Component.literal("KubeJS acceptance action"));
                    scriptClicked = true;
                }
                if (!hasWidget(client.screen, "1")) return false;
                client.screen.onClose();
                System.out.println("SHB_PAGE_KUBEJS_RELOAD_VERIFIED");
                stage = Stage.KUBEJS_READABILITY;
            }
            case KUBEJS_READABILITY -> {
                if (!(client.screen instanceof UiScreen)
                        || !client.screen.getTitle().getString().equals("KubeJS readability acceptance")) return false;
                if (!scriptPopupClicked) {
                    click(client.screen, Component.literal("KubeJS readability popup"));
                    scriptPopupClicked = true;
                }
                if (!hasWidget(client.screen, "KubeJS readability verified")) return false;
                client.screen.onClose();
                stage = Stage.CONTAINER;
            }
            case CONTAINER -> {
                if (!container.advance(client)) return false;
                requestRemote(client);
                stage = Stage.DENIED;
            }
            case DENIED -> {
                if (!remote.isDone()) return false;
                var result = remote.join();
                if (result.error().orElse(null) == UiErrorCode.CLIENT_UNAVAILABLE) {
                    if (System.nanoTime() - nextProbe < 0) return false;
                    nextProbe = System.nanoTime() + 250_000_000;
                    requestRemote(client);
                    return false;
                }
                require(result.error().orElse(null) == UiErrorCode.PERMISSION_DENIED, "Remote opt-in default was bypassed");
                require(game.clientCommand("shbetterpages"), "Client permission command was unavailable");
                stage = Stage.ALLOW;
            }
            case ALLOW -> {
                if (!(client.screen instanceof ConfirmScreen)) return false;
                click(client.screen, Component.translatable("sh_better_ui.pages.permission.allow"));
                require(game.clientCommand("shbetterpages modal"), "Modal permission command was unavailable");
                stage = Stage.ALLOW_MODAL;
            }
            case ALLOW_MODAL -> {
                if (!(client.screen instanceof ConfirmScreen)) return false;
                click(client.screen, Component.translatable("sh_better_ui.pages.permission.allow"));
                require(game.clientCommand("shbetterpages priority_modal"), "Priority permission command was unavailable");
                stage = Stage.ALLOW_PRIORITY;
            }
            case ALLOW_PRIORITY -> {
                if (!(client.screen instanceof ConfirmScreen)) return false;
                click(client.screen, Component.translatable("sh_better_ui.pages.permission.allow"));
                stage = Stage.REMOTE_REQUEST;
            }
            case REMOTE_REQUEST -> {
                if (client.screen != null) return false;
                requestRemote(client);
                stage = Stage.REMOTE_ACTIVE;
            }
            case REMOTE_ACTIVE -> {
                if (!remote.isDone()) return false;
                require(remote.join().status() == UiOpenResult.Status.DISPLAYED, "Server did not receive DISPLAYED: " + remote.join());
                require(client.screen instanceof UiScreen, "Remote page did not mount the native UiScreen");
                click(client.screen, Component.literal("Exchange"));
                stage = Stage.REMOTE_CANCEL;
            }
            case REMOTE_CANCEL -> {
                if (!(client.screen instanceof ConfirmScreen)) return false;
                require(authorityActions.get() == 0, "Confirmed action executed before the player accepted");
                click(client.screen, Component.translatable("gui.cancel"));
                stage = Stage.REMOTE_RETRY;
            }
            case REMOTE_RETRY -> {
                if (!(client.screen instanceof UiScreen) || !widgetActive(client.screen, "Exchange")) return false;
                require(authorityActions.get() == 0, "Cancelled action mutated server state");
                click(client.screen, Component.literal("Exchange"));
                stage = Stage.REMOTE_CONFIRM;
            }
            case REMOTE_CONFIRM -> {
                if (!(client.screen instanceof ConfirmScreen)) return false;
                require(authorityActions.get() == 0, "Retry executed without confirmation");
                click(client.screen, Component.translatable("sh_better_ui.pages.action.confirm"));
                stage = Stage.REMOTE_UPDATED;
            }
            case REMOTE_UPDATED -> {
                if (authorityActions.get() == 0) return false;
                var screen = client.screen;
                boolean updated = screen != null && screen.children().stream().filter(AbstractWidget.class::isInstance)
                        .map(AbstractWidget.class::cast).anyMatch(widget -> widget.getMessage().getString().equals("4"));
                if (!updated) return false;
                require(authorityActions.get() == 1, "Authoritative action ran more than once");
                covered = screen;
                requestModal(client);
                stage = Stage.MODAL_ACTIVE;
            }
            case MODAL_ACTIVE -> {
                if (!modal.isDone()) return false;
                require(modal.join().status() == UiOpenResult.Status.DISPLAYED, "Priority modal was not displayed: " + modal.join());
                require(client.screen instanceof UiScreen && client.screen != covered
                        && hasWidget(client.screen, "Priority modal fixture"), "Priority modal did not take focus");
                client.screen.onClose();
                stage = Stage.MODAL_RESTORED;
            }
            case MODAL_RESTORED -> {
                if (client.screen != covered || !hasWidget(covered, "4")) return false;
                require(authorityActions.get() == 1, "Resuming the page replayed a confirmed action");
                covered.onClose();
                require(game.clientCommand("shbetterpages interaction"), "Target permission command was unavailable");
                stage = Stage.ALLOW_TARGET;
            }
            case ALLOW_TARGET -> {
                if (!(client.screen instanceof ConfirmScreen)) return false;
                click(client.screen, Component.translatable("sh_better_ui.pages.permission.allow"));
                var pos = client.player.blockPosition().below();
                require(!client.level.getBlockState(pos).isAir(), "Interaction fixture did not find its target block");
                var hit = client.hitResult;
                client.hitResult = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
                try {
                    ClientUiKernel.observePageInteraction(client);
                } finally {
                    client.hitResult = hit;
                }
                observedTarget = new UiInteractionTarget.Block(UiId.parse(client.level.dimension().location().toString()),
                        pos.getX(), pos.getY(), pos.getZ());
                proofAt = System.nanoTime();
                requestTarget(client, new UiInteractionTarget.Block(observedTarget.dimension(), pos.getX() + 1,
                        pos.getY(), pos.getZ()));
                stage = Stage.TARGET_WRONG;
            }
            case TARGET_WRONG -> {
                if (!interaction.isDone() || System.nanoTime() - proofAt < 200_000_000L) return false;
                require(interaction.join().error().orElse(null) == UiErrorCode.INVALID_INTERACTION,
                        "Target token was accepted for a different block");
                requestTarget(client, observedTarget);
                stage = Stage.TARGET_ACTIVE;
            }
            case TARGET_ACTIVE -> {
                if (!interaction.isDone()) return false;
                require(interaction.join().status() == UiOpenResult.Status.DISPLAYED
                        && client.screen instanceof UiScreen, "Target page was not displayed: " + interaction.join());
                client.screen.onClose();
                requestTarget(client, observedTarget);
                stage = Stage.TARGET_REPLAY;
            }
            case TARGET_REPLAY -> {
                if (!interaction.isDone()) return false;
                require(interaction.join().error().orElse(null) == UiErrorCode.INVALID_INTERACTION,
                        "A consumed interaction token was accepted again");
                hud = live(UiPageType.HUD, "hud_panel");
                stage = Stage.HUD;
            }
            case HUD -> {
                var shown = hud.displayed().toCompletableFuture();
                if (!shown.isDone()) return false;
                require(shown.join(), "HUD page was never painted");
                hud.close();
                notification = live(UiPageType.NOTIFICATION, "notification");
                stage = Stage.NOTIFICATION;
            }
            case NOTIFICATION -> {
                var shown = notification.displayed().toCompletableFuture();
                if (!shown.isDone()) return false;
                require(shown.join(), "Notification page was never painted");
                notification.close();
                stage = Stage.CONTAINER_RECONNECT;
            }
            case CONTAINER_RECONNECT -> {
                if (!container.disconnectAndRejoin(client, game, title, world)) return false;
                serverRegistration = null;
                nextProbe = 0;
                requestRemote(client);
                stage = Stage.RECONNECT_DENIED;
            }
            case RECONNECT_DENIED -> {
                if (!remote.isDone()) return false;
                var error = remote.join().error().orElse(null);
                if (error == UiErrorCode.CLIENT_UNAVAILABLE) {
                    if (System.nanoTime() - nextProbe >= 0) {
                        nextProbe = System.nanoTime() + 250_000_000;
                        requestRemote(client);
                    }
                    return false;
                }
                require(error == UiErrorCode.PERMISSION_DENIED, "Reconnect retained a remote page grant");
                return true;
            }
        }
        return false;
    }

    private PageService.Instance live(UiPageType type, String nodeType) {
        var id = new UiId("sh_better_ui", "smoke/" + nodeType);
        var service = UnifiedUI.pages();
        service.register(module, service.define(id).title("Live page fixture").type(type)
                .root("root", nodeType, root -> root.child("detail", "text", text -> text.text("Live state")))
                .build());
        return service.open(module, id, Map.of(), ignored -> {});
    }

    private void requestRemote(Minecraft client) {
        var server = client.getSingleplayerServer();
        var playerId = client.player.getUUID();
        var result = new CompletableFuture<UiOpenResult>();
        remote = result;
        server.execute(() -> {
            try {
                if (serverRegistration == null) {
                    serverRegistration = ServerPages.register(server, definition(UiActionPolicy.CONFIRMED), player -> true,
                            Map.of(ACTION, ServerPages.ActionHandler.confirmed(player -> true,
                                    action -> new UiActionPrompt("Fixture points", 1, "Subtract one point"), action -> {
                                require(action.player().getUUID().equals(playerId), "Wrong action player");
                                require(action.state().equals(INITIAL), "Initial server authority was lost");
                                authorityActions.incrementAndGet();
                                return ServerPages.Outcome.success(Map.of("balance", new UiValue.Int(4)));
                            })));
                }
                ServerPages.open(server.getPlayerList().getPlayer(playerId), PAGE, INITIAL)
                        .whenComplete((value, failure) -> {
                            if (failure != null) result.completeExceptionally(failure);
                            else result.complete(value);
                        });
            } catch (RuntimeException | AssertionError failure) {
                result.completeExceptionally(failure);
            }
        });
    }

    private void requestModal(Minecraft client) {
        var server = client.getSingleplayerServer();
        var playerId = client.player.getUUID();
        var result = new CompletableFuture<UiOpenResult>();
        modal = result;
        server.execute(() -> {
            try {
                if (modalRegistration == null) {
                    var definition = UiPageDefinition.builder(MODAL).title("Priority modal")
                            .type(UiPageType.MODAL).root("root", "modal",
                                    root -> root.child("message", "text", text -> text.text("Priority modal fixture")))
                            .build();
                    modalRegistration = ServerPages.register(server, definition, player -> true, Map.of());
                }
                ServerPages.openPriorityModal(server.getPlayerList().getPlayer(playerId), MODAL, Map.of())
                        .whenComplete((value, failure) -> {
                            if (failure == null) result.complete(value);
                            else result.completeExceptionally(failure);
                        });
            } catch (RuntimeException | AssertionError failure) {
                result.completeExceptionally(failure);
            }
        });
    }

    private void requestTarget(Minecraft client, UiInteractionTarget target) {
        var server = client.getSingleplayerServer();
        var playerId = client.player.getUUID();
        var result = new CompletableFuture<UiOpenResult>();
        interaction = result;
        server.execute(() -> {
            try {
                ServerPages.openForInteraction(server.getPlayerList().getPlayer(playerId), target, PAGE, INITIAL)
                        .whenComplete((value, failure) -> {
                            if (failure == null) result.complete(value);
                            else result.completeExceptionally(failure);
                        });
            } catch (RuntimeException | AssertionError failure) {
                result.completeExceptionally(failure);
            }
        });
    }

    private static UiPageDefinition definition(UiActionPolicy policy) {
        return UiPageDefinition.builder(PAGE).title("Page fixture")
                .field("balance", UiValueSchema.integer(0, 100))
                .action(new UiActionDefinition(ACTION, policy, new UiDataSchema(Map.of())))
                .root("root", "column", root -> {
                    root.child("exchange", "button", button -> button.label("Exchange").action(ACTION));
                    root.child("balance", "text", text -> text.bind("text", "balance"));
                    if (policy == UiActionPolicy.LOCAL) {
                        root.child("toggle", "toggle", toggle -> toggle.label("Local setting"));
                        root.child("editor", "text_field", editor -> editor.label("Local draft"));
                        root.child("ghost", "ghost_slot", ghost ->
                                ghost.property("choices", new UiValue.Sequence(List.of(SAMPLE))));
                        root.child("scroll", "scroll_view", scroll -> {
                            scroll.property("height", new UiValue.Int(43));
                            for (int i = 0; i < 5; i++) {
                                int row = i;
                                scroll.child("scroll_" + row, "text", text -> text.text("Scroll item " + row));
                            }
                        });
                    }
                }).build();
    }

    private static void click(Screen screen, Component caption) {
        var button = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(child -> child.getMessage().getString().equals(caption.getString())).findFirst().orElseThrow();
        clickWidget(screen, button);
    }

    private static void clickWidget(Screen screen, AbstractWidget widget) {
        double x = widget.getX() + widget.getWidth() / 2.0;
        double y = widget.getY() + widget.getHeight() / 2.0;
        require(screen.mouseClicked(x, y, 0), "Unable to click " + widget.getMessage().getString());
        screen.mouseReleased(x, y, 0);
    }

    private static boolean scriptScreen(Minecraft client) {
        return client.screen instanceof UiScreen
                && client.screen.getTitle().getString().equals("KubeJS page acceptance");
    }

    private static void reloadKubeJs() {
        try {
            // Both KubeJS branches run this entry point before the resource reload in their F3+T hook.
            Class.forName("dev.latvian.mods.kubejs.client.KubeJSClient")
                    .getMethod("reloadClientScripts").invoke(null);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Unable to invoke the KubeJS client script reload", failure);
        }
    }

    private static boolean hasWidget(Screen screen, String caption) {
        return screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .anyMatch(widget -> widget.getMessage().getString().equals(caption));
    }

    private static AbstractWidget widget(Screen screen, String caption) {
        return screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(widget -> widget.getMessage().getString().equals(caption)).findFirst().orElseThrow();
    }

    private static boolean widgetActive(Screen screen, String caption) {
        return screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .anyMatch(widget -> widget.active && widget.getMessage().getString().equals(caption));
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
