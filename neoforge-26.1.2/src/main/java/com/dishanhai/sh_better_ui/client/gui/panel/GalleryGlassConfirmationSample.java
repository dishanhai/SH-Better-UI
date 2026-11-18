// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiConfirmation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;

/** Native service demonstration, not a private imitation of modal input routing. */
final class GalleryGlassConfirmationSample extends GallerySampleScreen {
    private UiButton launch, counter;
    private int confirmations, backgroundClicks;
    GalleryGlassConfirmationSample(DeveloperGalleryScreen parent, String id) { super(parent, id); }
    private static Component label(String key, Object... args) {
        return Component.translatable("sh_better_ui.glass_confirmation." + key, args);
    }
    @Override protected int minimumBodyHeight() { return 58; }
    @Override protected String sampleCode() {
        return """
                ShBetterUiApi.interactions().confirmGlass(parent,
                        new UiConfirmation.Prompt(title, details, confirmLabel, cancelLabel),
                        preferences, this::drawInertContext,
                        () -> currentTriggerButton, this::confirmOnce);
                // Drawing only; never forward background input to the parent.
                // The focus supplier looks up a fresh button after parent rebuild.
                """;
    }
    @Override protected void initSample() {
        int width = Math.min(280, body.width());
        int x = body.x() + (body.width() - width) / 2;
        launch = addButton(new Bounds(x, body.y() + 4, width, 24), label("launch"), ignored -> open(), true);
        counter = addButton(new Bounds(x, body.y() + 34, width, 24), status(), ignored -> {
            backgroundClicks++;
            counter.setMessage(status());
        }, false);
        initialFocus = launch;
    }
    private Component status() { return label("status", confirmations, backgroundClicks); }
    private void open() {
        var details = Component.empty();
        for (int i = 0; i < 8; i++) details.append(label("details")).append("\n\n");
        ShBetterUiApi.interactions().confirmGlass(this,
                new UiConfirmation.Prompt(label("title"), details, CommonComponents.GUI_OK, CommonComponents.GUI_CANCEL),
                preferences, graphics -> {
                    for (int x = 0; x < width; x += 48) graphics.fill(x, 0, x + 24, height, 0xff245276);
                }, () -> launch, () -> {
                    confirmations++;
                    if (counter != null) counter.setMessage(status());
                });
    }
}
