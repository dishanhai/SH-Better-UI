package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class MusicTypographyScopeTest {
    private static ActivityService.Content content() {
        return new ActivityService.Content(ActivityService.Text.literal("Same title"),
                ActivityService.Text.literal("Same artist"), null, -1, false, List.of(), List.of());
    }
    @Test void equalContentFromOtherOwnersNeverInheritsMusicFontAcrossBothCrossfadeDirections() {
        var scope = new ClientLiveActivities.MusicTypographyScope();
        var thirdParty = content(); var music = content();
        assertEquals(thirdParty, music); assertNotSame(thirdParty, music);
        scope.remember(thirdParty, UiId.parse("other:module"));
        scope.remember(music, UiId.parse("sh_better_ui:music_activity"));
        scope.retain(List.of(thirdParty, music)); // third-party -> music fade
        assertFalse(scope.music(thirdParty)); assertTrue(scope.music(music));
        scope.retain(List.of(music, thirdParty)); // music -> third-party fade
        assertTrue(scope.music(music)); assertFalse(scope.music(thirdParty));
        scope.retain(List.of(thirdParty));
        assertFalse(scope.music(music), "retired layers release ownership metadata");
        assertFalse(scope.music(content()), "unknown content keeps the default renderer");
        scope.retain(List.of()); assertFalse(scope.music(thirdParty));
    }
}
