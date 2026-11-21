package com.dishanhai.sh_better_ui.kernel.api.controls;

import com.dishanhai.sh_better_ui.kernel.api.text.FontServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WidgetHostTest {
    @AfterEach
    void resetProviders() {
        WidgetTouch.Host.set(WidgetTouch.NONE);
        FontServices.Host.set(FontServices.NONE);
        WidgetTheme.reset();
    }

    @Test
    void providersReadLiveHostState() {
        int[] color = {17};
        WidgetTheme.setColors(role -> color[0]);
        assertEquals(17, WidgetTheme.color(WidgetTheme.Role.BORDER));
        color[0] = 42;
        assertEquals(42, WidgetTheme.color(WidgetTheme.Role.BORDER));
        WidgetTheme.setShowBounds(() -> true);
        assertTrue(WidgetTheme.showBounds());

        Object owner = new Object();
        WidgetTouch.Host.set(new WidgetTouch() {
            @Override public boolean enabled() { return true; }
            @Override public boolean claim(Object value) { return value == owner; }
        });
        assertTrue(WidgetTouch.Host.get().claim(owner));
        WidgetTouch.Host.set(WidgetTouch.NONE);
        assertFalse(WidgetTouch.Host.get().enabled());

        FontServices.Host.set(new FontServices() {
            @Override public long pinnedBytes(String name) { return 42; }
        });
        assertEquals(42, FontServices.Host.get().pinnedBytes("font"));
    }
}
