package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImmersiveCrossfadeFeedbackTest {
    private static final String PREFIX="sh_better_ui.online_music.crossfade.";
    @Test void pausedBusyFeedbackMakesRecoveryActionVisible() {
        var full=Component.translatable(PREFIX+"busy");
        var shortText=OnlineMusicViewImpl.immersiveFeedback(full,OnlineMusicPlayer.State.PAUSED);
        assertEquals(PREFIX+"resume_short",((TranslatableContents)shortText.getContents()).getKey());
        assertEquals(PREFIX+"busy",((TranslatableContents)full.getContents()).getKey());
    }
    @Test void ongoingFadeUsesShortWaitInsteadOfPauseInstruction() {
        var text=OnlineMusicViewImpl.immersiveFeedback(Component.translatable(PREFIX+"busy"),OnlineMusicPlayer.State.PLAYING);
        assertEquals(PREFIX+"wait_short",((TranslatableContents)text.getContents()).getKey());
    }
    @Test void unrelatedErrorsAndEmptyFeedbackStayUnchanged() {
        var error=Component.translatable("sh_better_ui.online_music.feedback.failed");
        assertSame(error,OnlineMusicViewImpl.immersiveFeedback(error,OnlineMusicPlayer.State.PAUSED));
        var empty=Component.empty();assertSame(empty,OnlineMusicViewImpl.immersiveFeedback(empty,OnlineMusicPlayer.State.PLAYING));
    }
}
