// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPromptBar;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.List;

final class PromptBarDemo {
    static final int STATUS_INSET = 15;
    static final int HEIGHT = UiPromptBar.MIN_HEIGHT + STATUS_INSET;
    static final String CODE = """
            import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPromptBar;
            import net.minecraft.network.chat.Component;
            import java.util.List;
            import java.util.function.Consumer;

            private UiPromptBar addPromptBar(int x, int y,
                    Consumer<UiPromptBar.Submission> submit, Runnable cancel) {
                var model = new UiPromptBar.Option("nova", Component.literal("Nova"), null, null);
                var prompt = ShBetterUiApi.controls().promptBar(font, new Bounds(x, y, 280, 142),
                        Component.translatable("sh_better_ui.prompt_bar.demo.placeholder"),
                        List.of(), List.of(), List.of(model), List.of(),
                        ShBetterUiApi.settings().preferences(), submit, cancel, null, null);
                return addRenderableWidget(prompt);
            }

            // Call prompt.setBusy(true) when starting a request.
            // Clear it on the client thread when the request completes or is cancelled.
            """;

    private UiPromptBar prompt;
    private UiPreferences mountedPreferences;
    private long requestEnds;
    private long dictationEnds;
    private Component status = demo("ready");

    static Bounds bounds(Bounds viewport) {
        int width = Math.min(304, viewport.width());
        int height = Math.min(158, viewport.height());
        return new Bounds(viewport.x() + (viewport.width() - width) / 2,
                viewport.y() + (viewport.height() - height) / 2, width, height - STATUS_INSET);
    }

    UiPromptBar mount(Font font, Bounds area, UiPreferences preferences) {
        if (prompt != null && prompt.getWidth() == area.width() && mountedPreferences.equals(preferences)) {
            prompt.setX(area.x());
            prompt.setY(area.y());
            prompt.setHeight(area.height());
            return prompt;
        }
        String draft = prompt == null ? "" : prompt.draft();
        mountedPreferences = preferences;
        prompt = ShBetterUiApi.controls().promptBar(font, area, demo("placeholder"),
                List.of(new UiPromptBar.Source(option("files", GraphicIcon.FILE), true),
                        new UiPromptBar.Source(option("web", GraphicIcon.WEB_SEARCH), false),
                        new UiPromptBar.Source(option("docs", GraphicIcon.DOCUMENT), false)),
                List.of(option("summarize", GraphicIcon.SUMMARY), option("compare", GraphicIcon.SEPARATE),
                        option("draft", GraphicIcon.EDIT), option("explain", GraphicIcon.QUESTION)),
                List.of(option("nova", null), option("mini", null)),
                List.of(demo("low"), demo("medium"), demo("high"), demo("extra"), demo("max")), preferences,
                this::send, this::stop, () -> prompt.addAttachments(List.of(demo("example_file").getString())),
                this::toggleDictation);
        prompt.setDraft(draft);
        prompt.setBusy(requestEnds != 0);
        prompt.setListening(dictationEnds != 0);
        return prompt;
    }

    Component status() {
        long now = now();
        if (requestEnds != 0 && now >= requestEnds) {
            requestEnds = 0;
            prompt.setBusy(false);
            status = demo("finished");
        }
        if (dictationEnds != 0 && now >= dictationEnds) {
            dictationEnds = 0;
            prompt.setListening(false);
            prompt.appendDictation(demo("transcript").getString());
        }
        return dictationEnds == 0 ? status : demo("transcribing");
    }

    private void send(UiPromptBar.Submission request) {
        requestEnds = now() + 4200;
        prompt.setBusy(true);
        status = demo("running", request.attachments().size());
    }

    private void stop() {
        requestEnds = 0;
        prompt.setBusy(false);
        status = demo("stopped");
    }

    private void toggleDictation() {
        dictationEnds = dictationEnds == 0 ? now() + 1000 : 0;
        prompt.setListening(dictationEnds != 0);
    }

    private static UiPromptBar.Option option(String key, GraphicIcon icon) {
        return new UiPromptBar.Option(key, demo(key), demo(key + "_detail"), icon);
    }

    private static Component demo(String key, Object... args) {
        return Component.translatable("sh_better_ui.prompt_bar.demo." + key, args);
    }

    private static long now() { return System.nanoTime() / 1_000_000L; }
}
