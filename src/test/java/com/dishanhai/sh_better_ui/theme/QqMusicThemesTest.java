package com.dishanhai.sh_better_ui.theme;

import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class QqMusicThemesTest {
    @Test void bothPresetsRemainRegisteredAfterResourceReload() {
        try(var manager=new ThemeManager()) {
            manager.replaceResourceThemes(List.of(),List.of());
            for(var id:List.of(QqMusicThemes.AURORA,QqMusicThemes.VINYL)) {
                assertTrue(BuiltinThemes.IDS.contains(id));
                manager.preferences(manager.preferences().withReducedMotion(true).withFixedTheme(id));
                assertFalse(manager.snapshot().resolve(ThemeTokens.LIGHT));
                assertEquals(0xff35d89a,manager.snapshot().resolve(ThemeTokens.ACCENT).argb());
            }
            manager.preferences(manager.preferences().withReducedMotion(true).withFixedTheme(BuiltinThemes.DARK));
            assertNotEquals(0xff35d89a,manager.snapshot().resolve(ThemeTokens.ACCENT).argb());
        }
    }
    @Test void galleryCanPreviewWithoutChangingLiveTheme() {
        try(var manager=new ThemeManager();var preview=new ThemePreviewSession(null)) {
            var original=manager.preferences();preview.sync(manager,original.withReducedMotion(true));
            for(var id:List.of(QqMusicThemes.AURORA,QqMusicThemes.VINYL)) {
                assertTrue(preview.themes().stream().anyMatch(t->t.id().equals(id)));
                preview.select(id);assertEquals(id,preview.selected());assertEquals(original,manager.preferences());
                manager.preferences(original.withFixedTheme(id).withHighContrast(true).withReducedMotion(true));
                assertEquals(0f,manager.global().resolve(ThemeTokens.BLUR));
                manager.preferences(original);
            }
        }
    }
    @Test void namesDoNotAccidentallySelectUnrelatedThemes() {
        assertTrue(QqMusicThemes.matches(QqMusicThemes.AURORA));
        assertTrue(QqMusicThemes.matches(QqMusicThemes.VINYL));
        assertFalse(QqMusicThemes.matches(null));
        assertFalse(QqMusicThemes.matches(BuiltinThemes.DARK));
    }
}
