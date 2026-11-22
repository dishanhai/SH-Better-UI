// Author: dishanhai
package com.dishanhai.shbui_widgets_smoke;

import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.RegistrationHandle;
import com.dishanhai.sh_better_ui.kernel.api.UIModule;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.kernel.api.page.PageService;
import com.dishanhai.sh_better_ui.kernel.api.page.UiActionDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiActionPolicy;
import com.dishanhai.sh_better_ui.kernel.api.page.UiBuiltinComponents;
import com.dishanhai.sh_better_ui.kernel.api.page.UiDataSchema;
import com.dishanhai.sh_better_ui.kernel.api.page.UiNode;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageType;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValue;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValueSchema;
import net.minecraft.client.InputType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.CommonComponents;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static com.dishanhai.shbui_widgets_smoke.GallerySmoke.key;

/** Exercises the public PageService through a separately loaded consumer mod. */
public final class DeclarativePageSmoke implements UIModule {
    private static final UiId ID = new UiId(WidgetsSmoke.ID, "declarative_page");
    private static final UiId MODAL_ID = new UiId(WidgetsSmoke.ID, "declarative_modal");
    private static final UiId ACTION = new UiId(WidgetsSmoke.ID, "apply");
    private static ModuleContext owner;
    private static PageService pages;
    private static RegistrationHandle registration;
    private static PageService.Instance instance;
    private static Screen parent;
    private static Screen page;
    private static int presses;
    private static int stage;
    private static long deadline;

    @Override public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder().id(ID).version(new UiVersion(1, 0, 0))
                .platforms(Set.of(ModuleDescriptor.Platform.NEOFORGE_26_1_2, ModuleDescriptor.Platform.NEOFORGE_26_2))
                .build();
    }

    @Override public void register(ModuleContext context) {}
    @Override public void start(ModuleContext context) { owner = context; }
    @Override public void stop(ModuleContext context) { owner = null; }

    static void frame(Minecraft client) {
        if (stage == 7) return;
        if (deadline == 0) deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
        require(System.nanoTime() < deadline, "timed out at stage " + stage);
        if (WidgetSmokePlatform.hasOverlay(client)) return;
        switch (stage) {
            case 0 -> {
                require(WidgetSmokePlatform.backend().equalsIgnoreCase(System.getProperty("shbui.widgetSmoke.expectedBackend")),
                        "expected graphics backend");
                pages = owner.engine().pages();
                require(pages.pageTypes().equals(Set.of(UiPageType.SCREEN, UiPageType.MODAL))
                                && pages.components().containsKey(UiBuiltinComponents.id("button"))
                                && !pages.components().containsKey(UiBuiltinComponents.id("rich_text")),
                        "only available renderers are advertised");
                var before = WidgetSmokePlatform.screen(client);
                reject(() -> pages.register(owner, UiPageDefinition.builder(ID)
                        .root(UiNode.builtin("unsupported", "rich_text").text("").build()).build()));
                reject(() -> pages.register(owner, UiPageDefinition.builder(ID).type(UiPageType.HUD)
                        .root(UiNode.builtin("body", "text").text("").build()).build()));
                require(WidgetSmokePlatform.screen(client) == before, "incompatible pages leave the screen unchanged");
                var definition = pages.define(ID).title(CommonComponents.GUI_PROCEED.getString())
                        .field("status", UiValueSchema.integer(0, 2))
                        .field("allowed", UiValueSchema.bool())
                        .action(new UiActionDefinition(ACTION, UiActionPolicy.LOCAL, UiDataSchema.EMPTY))
                        .root("root", "column", root -> root
                                .child(UiNode.builtin("status", "text").bind("text", "status").build())
                                .child(UiNode.builtin("space", "spacer").property("height", new UiValue.Int(320)).build())
                                .child(UiNode.builtin("execute", "button").label(CommonComponents.GUI_YES.getString())
                                        .bind("enabled", "allowed").action(ACTION).build()))
                        .build();
                registration = pages.register(owner, definition);
                require(pages.find(owner, ID).orElseThrow().equals(definition), "registered definition");
                parent = before;
                client.setLastInputType(InputType.KEYBOARD_TAB);
                instance = open();
                page = WidgetSmokePlatform.screen(client);
                require(page != parent && instance.snapshot().state() == PageService.State.OPENING
                                && !instance.displayed().toCompletableFuture().isDone(),
                        "opening waits for extraction");
                stage++;
            }
            case 1 -> {
                if (!instance.displayed().toCompletableFuture().isDone()) return;
                require(instance.displayed().toCompletableFuture().join()
                                && instance.snapshot().state() == PageService.State.ACTIVE
                                && label().getMessage().getString().equals("1"), "first presentation and numeric text binding");
                var scroll = scroll();
                if (scroll.getFocused() != button()) page.keyPressed(key(GLFW.GLFW_KEY_TAB));
                require(scroll.getFocused() == button() && scroll.scrollAmount() > 0, "Tab focuses and scrolls to the action");
                reject(() -> pages.update(owner, instance.snapshot().instanceId(), Map.of("allowed", new UiValue.Int(1))));
                require(instance.snapshot().revision() == 0, "invalid patch preserves the revision");
                require(page.keyPressed(key(GLFW.GLFW_KEY_ENTER)) && presses == 1
                                && instance.snapshot().revision() == 1, "keyboard invokes the declared action");
                stage++;
            }
            case 2 -> {
                if (!label().getMessage().getString().equals("2")) return;
                require(scroll().getFocused() == button() && scroll().scrollAmount() > 0,
                        "data update retains focused node and scroll");
                require(pages.update(owner, instance.snapshot().instanceId(), Map.of("allowed", new UiValue.Bool(false))),
                        "owner disables the bound button");
                page.keyPressed(key(GLFW.GLFW_KEY_ENTER));
                require(presses == 1, "stale widgets cannot dispatch actions after a revision changes");
                stage++;
            }
            case 3 -> {
                if (button().active) return;
                require(scroll().getFocused() == null && presses == 1, "disabled action loses focus");
                page.keyPressed(key(GLFW.GLFW_KEY_ESCAPE));
                require(WidgetSmokePlatform.screen(client) == parent && !pages.update(owner, instance.snapshot().instanceId(), Map.of()),
                        "Escape restores the parent and removes the instance");
                stage++;
            }
            case 4 -> {
                if (!instance.closed().toCompletableFuture().isDone()) return;
                require(instance.closed().toCompletableFuture().join().state() == PageService.State.CLOSED, "terminal state");
                instance = open();
                parent = new TitleScreen();
                WidgetSmokePlatform.open(client, parent);
                stage++;
            }
            case 5 -> {
                if (!instance.closed().toCompletableFuture().isDone()) return;
                require(WidgetSmokePlatform.screen(client) == parent
                                && instance.closed().toCompletableFuture().join().state() == PageService.State.CLOSED
                                && !instance.displayed().toCompletableFuture().join(),
                        "native screen replacement closes the page without restoring the old parent");
                var reopened = open();
                registration.close();
                require(WidgetSmokePlatform.screen(client) == parent
                                && reopened.closed().toCompletableFuture().join().state() == PageService.State.CLOSED
                                && !reopened.displayed().toCompletableFuture().join()
                                && registration.status() == RegistrationHandle.Status.REVOKED
                                && pages.find(owner, ID).isEmpty(), "revoke closes an unpresented instance and restores the parent");

                registration = pages.register(owner, pages.define(MODAL_ID).type(UiPageType.MODAL)
                        .title("Declarative modal")
                        .root("modal", "modal", root -> root.child("notice", "text", text -> text.text("Notice")))
                        .build());
                instance = pages.open(owner, MODAL_ID, Map.of(), ignored -> { throw new AssertionError("No modal action declared"); });
                page = WidgetSmokePlatform.screen(client);
                require(page != parent && page.getFocused() instanceof AbstractWidget back
                                && back.getMessage().equals(CommonComponents.GUI_BACK), "modal initially focuses Back");
                stage++;
            }
            case 6 -> {
                if (!instance.displayed().toCompletableFuture().isDone()) return;
                require(instance.displayed().toCompletableFuture().join()
                                && instance.snapshot().state() == PageService.State.ACTIVE, "modal is presented");
                require(page.keyPressed(key(GLFW.GLFW_KEY_ESCAPE))
                                && WidgetSmokePlatform.screen(client) == parent, "Escape closes the modal and restores its parent");
                require(instance.closed().toCompletableFuture().join().state() == PageService.State.CLOSED, "modal terminal state");
                registration.close();
                try {
                    Files.writeString(client.gameDirectory.toPath().resolve("declarative-page.txt"),
                            "SHBUI_DECLARATIVE_PAGE_PASS backend=" + WidgetSmokePlatform.backend() + "\n");
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
                stage++;
                client.stop();
            }
        }
    }

    private static PageService.Instance open() {
        return pages.open(owner, ID, Map.of("status", new UiValue.Int(1), "allowed", new UiValue.Bool(true)), id -> {
            require(id.equals(ACTION), "declared action ID");
            presses++;
            require(pages.update(owner, instance.snapshot().instanceId(), Map.of("status", new UiValue.Int(2))),
                    "action updates its owned page");
        });
    }

    private static AbstractContainerWidget scroll() {
        return page.children().stream().filter(AbstractContainerWidget.class::isInstance)
                .map(AbstractContainerWidget.class::cast).findFirst().orElseThrow();
    }

    private static AbstractWidget button() {
        return scroll().children().stream().map(AbstractWidget.class::cast)
                .filter(widget -> widget.getMessage().getString().equals(CommonComponents.GUI_YES.getString()))
                .findFirst().orElseThrow();
    }

    private static MultiLineTextWidget label() {
        return scroll().children().stream().filter(MultiLineTextWidget.class::isInstance)
                .map(MultiLineTextWidget.class::cast).findFirst().orElseThrow();
    }

    private static void reject(Runnable task) {
        try {
            task.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new IllegalStateException("Declarative page smoke: accepted incompatible input");
    }

    private static void require(boolean condition, String description) {
        if (!condition) throw new IllegalStateException("Declarative page smoke: " + description);
    }
}
