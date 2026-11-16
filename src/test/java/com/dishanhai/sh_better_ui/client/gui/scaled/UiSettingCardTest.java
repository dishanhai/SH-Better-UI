package com.dishanhai.sh_better_ui.client.gui.scaled;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class UiSettingCardTest {
    private final UiPreferences prefs = UiPreferences.fromSettings(ClientUiSettings.DEFAULT);
    private UiButton button(Runnable action) { return new UiButton(0, 0, 100, 20, Component.literal("Main"), b -> action.run(), prefs, false); }
    private UiSettingCard card(UiButton primary, Runnable action) {
        return new UiSettingCard(CardNavTestFont.create(), new Bounds(10, 20, 440, 1),
                Component.literal("Feature"), Component.literal("Explanation"), primary, Component.literal("Details"), prefs, action);
    }
    @Test void independentActionsHaveDistinctHitTargetsAndNativeFocusEntries() {
        var main = new AtomicInteger(); var more = new AtomicInteger();
        var primary = button(main::incrementAndGet); var card = card(primary, more::incrementAndGet);
        assertEquals(List.of(primary, card.detailsButton()), card.widgets());
        assertFalse(primary.isMouseOver(card.detailsButton().getX() + 1, card.detailsButton().getY() + 1));
        primary.onPress(); assertEquals(1, main.get()); assertEquals(0, more.get());
        card.detailsButton().onPress(); assertEquals(1, main.get()); assertEquals(1, more.get());
        assertTrue(card.detailsButton().getMessage().getString().contains("Explanation"));
    }
    @Test void movingCardAndNarrowReflowMoveBothNativeTargets() {
        var primary = button(() -> {}); var card = card(primary, () -> {});
        card.setBounds(new Bounds(30, -40, 180, 1));
        assertTrue(card.layout().stacked());
        assertEquals(30 + card.layout().primary().x(), primary.getX());
        assertEquals(-40 + card.layout().primary().y(), primary.getY());
        assertEquals(30 + card.layout().disclosure().x(), card.detailsButton().getX());
    }
    @Test void collapsePreservesValueAndOriginalDisabledStateAndCancelsFocus() {
        var primary = button(() -> {}); var card = card(primary, () -> {}); var detail = button(() -> {});
        detail.active = false;
        card.setExpanded(true, List.of(detail)); int openHeight = card.height(); detail.setFocused(true);
        card.setExpanded(false, List.of(detail));
        assertFalse(detail.visible); assertFalse(detail.isFocused()); assertTrue(card.height() < openHeight);
        card.setEnabled(false); assertFalse(primary.active); assertFalse(card.detailsButton().active);
        card.setEnabled(true); assertTrue(primary.active); assertFalse(detail.active);
        card.setExpanded(true, List.of(detail)); assertTrue(detail.visible); assertFalse(detail.active);
        assertTrue(card.detailsButton().isSelected());
    }
    @Test void closeIsIdempotentAndCannotReactivateChildren() {
        var primary = button(() -> {}); var card = card(primary, () -> {});
        card.close(); card.close(); card.setEnabled(true);
        assertFalse(primary.active); assertFalse(card.detailsButton().active);
    }
    @Test void detailsCannotAliasMainActions() {
        var primary = button(() -> {}); var card = card(primary, () -> {});
        assertThrows(IllegalArgumentException.class, () -> card.setExpanded(true, List.of(primary)));
        var detail = button(() -> {});
        assertThrows(IllegalArgumentException.class, () -> card.setExpanded(true, List.of(detail, detail)));
    }
}
