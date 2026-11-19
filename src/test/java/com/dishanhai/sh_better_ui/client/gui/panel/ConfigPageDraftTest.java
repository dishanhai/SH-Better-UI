// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.kernel.api.config.ConfigPage;
import com.dishanhai.sh_better_ui.kernel.api.config.ConfigPage.NumberOption;
import com.dishanhai.sh_better_ui.kernel.api.config.ConfigPage.Preset;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConfigPageDraftTest {
    private record Settings(double speed, int durability, boolean passive) {}

    private static final Component TEXT = Component.empty();
    private static final NumberOption<Settings> SPEED = new NumberOption<>("speed", TEXT, TEXT, .25, 20, false,
            Settings::speed, (s, value) -> new Settings(value, s.durability(), s.passive()));
    private static final NumberOption<Settings> DURABILITY = new NumberOption<>("durability", TEXT, TEXT, 1, 100000, true,
            Settings::durability, (s, value) -> new Settings(s.speed(), (int) value, s.passive()));
    private static final Preset<Settings> FAST = new Preset<>(TEXT, TEXT, Map.of(SPEED, 2.0));
    private static final Settings ORIGINAL = new Settings(.9, 250, true);

    private ConfigPageDraft<Settings> draft() {
        return new ConfigPageDraft<>(new ConfigPage<>(TEXT, TEXT, List.of(SPEED, DURABILITY), List.of(FAST)), ORIGINAL);
    }

    @Test
    void presetOnlyReplacesItsOwnFieldsAndSaveCanBeRetried() {
        var draft = draft();
        draft.set(1, "500");
        draft.apply(FAST);
        assertTrue(draft.selected(FAST));
        assertTrue(draft.dirty());
        assertFalse(draft.commit(value -> false));
        assertTrue(draft.commit(value -> {
            assertEquals(new Settings(2, 500, true), value);
            return true;
        }));
        assertTrue(draft.commit(value -> fail("The same draft must not be written twice")));
    }

    @Test
    void fivePresetsCanSelectAndCommitTheMaximum() {
        var presets = List.of(1.0, 2.0, 4.0, 10.0, 20.0).stream()
                .map(value -> new Preset<>(TEXT, TEXT, Map.of(SPEED, value))).toList();
        var draft = new ConfigPageDraft<>(new ConfigPage<>(TEXT, TEXT, List.of(SPEED, DURABILITY), presets), ORIGINAL);
        for (var preset : presets) {
            draft.apply(preset);
            assertTrue(draft.selected(preset));
        }
        assertTrue(draft.commit(value -> {
            assertEquals(new Settings(20, ORIGINAL.durability(), ORIGINAL.passive()), value);
            return true;
        }));
    }

    @Test
    void invalidAndUnchangedDraftsDoNotWrite() {
        var draft = draft();
        var saves = new AtomicInteger();
        draft.set(0, "0,90");
        assertFalse(draft.dirty());
        assertTrue(draft.commit(value -> saves.incrementAndGet() > 0));
        draft.set(1, "1.5");
        assertEquals(1, draft.firstInvalid());
        assertFalse(draft.commit(value -> saves.incrementAndGet() > 0));
        draft.set(1, "100001");
        assertEquals(1, draft.firstInvalid());
        assertEquals(0, saves.get());
    }

    @Test
    void invalidPresetFailsBeforeAnyScreenCanOpen() {
        assertThrows(IllegalArgumentException.class, () -> new ConfigPage<>(TEXT, TEXT, List.of(SPEED),
                List.of(new Preset<>(TEXT, TEXT, Map.of(SPEED, Double.NaN)))));
        assertThrows(IllegalArgumentException.class, () -> new ConfigPage<>(TEXT, TEXT, List.of(DURABILITY), List.of(FAST)));
    }
}
