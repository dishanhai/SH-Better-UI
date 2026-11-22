package com.muoniumplayer.core.screens.hud;

import com.muoniumplayer.core.settings.HudConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Author: dishanhai
class HudOverlayStageTest {
    private static final int WIDTH = 800;
    private static final int HEIGHT = 450;

    @Test
    void releasingAMovedCardRequestsOneSave() {
        float originalX = HudConfig.infoX;
        float originalY = HudConfig.infoY;
        float originalScale = HudConfig.infoScale;
        try {
            var stage = new HudOverlayStage();
            HudConfig.infoScale = 1f;
            HudConfig.infoX = 100f / (WIDTH - 230);
            HudConfig.infoY = 80f / (HEIGHT - 56);
            float startX = HudConfig.infoX;

            stage.press(160, 110, WIDTH, HEIGHT, 48, 48);
            stage.drag(200, 130, WIDTH, HEIGHT);
            assertNotEquals(startX, HudConfig.infoX);
            float previewX = HudConfig.infoX;
            assertTrue(stage.release(240, 130, WIDTH, HEIGHT));
            assertTrue(HudConfig.infoX > previewX);
            assertFalse(stage.release(240, 130, WIDTH, HEIGHT));
        } finally {
            HudConfig.infoX = originalX;
            HudConfig.infoY = originalY;
            HudConfig.infoScale = originalScale;
        }
    }

    @Test
    void titleClickBetweenFramesSelectsLyricsAndOuterHandleCanResize() {
        float originalX = HudConfig.lyricX;
        float originalY = HudConfig.lyricY;
        float originalW = HudConfig.lyricWidth;
        float originalH = HudConfig.lyricHeight;
        float originalScale = HudConfig.lyricScale;
        try {
            HudConfig.lyricScale = 1f;
            HudConfig.lyricWidth = 450f;
            HudConfig.lyricHeight = 140f;
            HudConfig.lyricX = 0.5f;
            HudConfig.lyricY = 0.5f;
            var stage = new HudOverlayStage();
            int[] lyrics = HudOverlayStage.widgetRect(HudOverlayStage.Kind.LYRICS, WIDTH, HEIGHT);
            int titleX = lyrics[0] + 30;
            int titleY = HudOverlayStage.labelY(lyrics);
            stage.press(titleX, titleY, WIDTH, HEIGHT, 48, 48);
            assertFalse(stage.release(titleX, titleY, WIDTH, HEIGHT));
            assertEquals(HudOverlayStage.Kind.LYRICS, stage.selected());

            int rightHandleX = lyrics[0] + lyrics[2] + 2;
            int rightHandleY = lyrics[1] + lyrics[3] / 2;
            stage.press(rightHandleX, rightHandleY, WIDTH, HEIGHT, 48, 48);
            stage.drag(rightHandleX + 30, rightHandleY, WIDTH, HEIGHT);
            assertEquals(480f, HudConfig.lyricWidth);
            assertEquals(140f, HudConfig.lyricHeight);
            assertEquals(lyrics[0], HudOverlayStage.widgetRect(HudOverlayStage.Kind.LYRICS, WIDTH, HEIGHT)[0]);

            assertTrue(stage.cancelGesture());
            assertEquals(450f, HudConfig.lyricWidth);
            assertEquals(0.5f, HudConfig.lyricX);
            assertFalse(stage.release(rightHandleX + 30, rightHandleY, WIDTH, HEIGHT));
        } finally {
            HudConfig.lyricX = originalX;
            HudConfig.lyricY = originalY;
            HudConfig.lyricWidth = originalW;
            HudConfig.lyricHeight = originalH;
            HudConfig.lyricScale = originalScale;
        }
    }
}
