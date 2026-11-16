// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CardNavInputTest {
    private static final Font FONT = CardNavTestFont.create();

    @Test void closedCardsCannotBeClickedOrReachedByKeyboard() {
        var selected = new AtomicInteger();
        var nav = nav(selected, CardNavControl.Options.DEFAULT);
        assertEquals(-1, nav.hit(20, 60));
        nav.moveFocus(1);
        nav.activate(); // Only the menu exists when closed.
        assertTrue(nav.isOpen());
        assertEquals(0, selected.get());
    }

    @Test void selectionClosesAndPointerCancellationDoesNotSelect() {
        var selected = new AtomicInteger();
        var nav = nav(selected, CardNavControl.Options.DEFAULT);
        nav.setOpen(true);
        nav.update(-1, -1, 0, true);
        for (int i = 1; i < 300; i++) nav.update(-1, -1, i * 16L, true);
        nav.moveFocus(1);
        nav.activate();
        assertEquals(1, selected.get());
        assertFalse(nav.isOpen());
        assertTrue(nav.press(8, 10));
        nav.cancel();
        assertFalse(nav.release(8, 10));
        assertFalse(nav.isOpen());
    }

    @Test void controlledOpenOnlyRequestsAChangeUntilTheHostSynchronizes() {
        var requested = new ArrayList<Boolean>();
        var state = new AtomicBoolean();
        var nav = new CardNavControl(FONT, 300, 180, Component.empty(), null, List.of(), null,
                CardNavControl.Options.DEFAULT, UiPreferences.DEFAULT, requested::add);
        nav.setOpenController(state::get);
        nav.setOpen(true);
        assertEquals(List.of(true), requested);
        assertFalse(nav.isOpen());
        state.set(true);
        nav.update(-1, -1, 0, true);
        assertTrue(nav.isOpen());
        assertEquals(List.of(true), requested);
    }

    @Test void hoverLeaveDelayAndDismissSuppressionPreventImmediateReopening() {
        var d = CardNavControl.Options.DEFAULT;
        var options = new CardNavControl.Options(d.theme(), d.surface(), CardNavControl.Trigger.HOVER,
                d.motion(), d.cardHeight(), d.maxWidth(), null, true, false, false);
        var nav = nav(new AtomicInteger(), options);
        nav.update(10, 10, 0, true);
        assertTrue(nav.isOpen());
        nav.update(-1, -1, 20, true);
        nav.update(-1, -1, 179, true);
        assertTrue(nav.isOpen());
        nav.update(-1, -1, 180, true);
        assertFalse(nav.isOpen());
        nav.update(10, 10, 200, true);
        assertFalse(nav.isOpen());
        nav.update(-1, -1, 210, true);
        nav.update(10, 10, 220, true);
        assertTrue(nav.isOpen());
    }

    @Test void narrowViewportScrollsAndKeyboardKeepsAllLinksReachable() {
        var selected = new AtomicInteger();
        var nav = nav(selected, CardNavControl.Options.DEFAULT);
        nav.resize(140, 65);
        nav.setOpen(true);
        assertTrue(nav.maxScroll() > 0);
        assertTrue(nav.scroll(-10));
        nav.moveFocus(1);
        nav.activate();
        assertEquals(1, selected.get());
    }

    private static CardNavControl nav(AtomicInteger selected, CardNavControl.Options options) {
        var link = new CardNavControl.Link(Component.literal("Link"), selected::incrementAndGet);
        var item = new CardNavControl.Item(Component.literal("Card"), Component.literal("Description"), List.of(link));
        return new CardNavControl(FONT, 300, 180, Component.literal("Main"), null, List.of(item), null,
                options, UiPreferences.DEFAULT, open -> {});
    }
}
