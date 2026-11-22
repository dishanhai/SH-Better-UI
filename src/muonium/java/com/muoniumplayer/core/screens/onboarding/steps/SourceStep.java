package com.muoniumplayer.core.screens.onboarding.steps;

import com.muoniumplayer.core.ncm.music.MusicPlatform;
import com.muoniumplayer.core.rendering.FoliaLucideIcons;
import com.muoniumplayer.core.screens.onboarding.OnboardingController;
import com.muoniumplayer.core.screens.onboarding.OnboardingMetrics;
import com.muoniumplayer.core.screens.onboarding.OnboardingStepView;
import com.muoniumplayer.core.screens.onboarding.OnboardingUi;
import com.muoniumplayer.core.screens.ncm.NcmExpressive;
import com.muoniumplayer.party.ui.PartyIcons;
import net.minecraft.network.chat.Component;

/** 音乐源页。选择立即应用并由控制器持久化。 */
public final class SourceStep implements OnboardingStepView {
    private static final MusicPlatform[] PLATFORMS = {
            MusicPlatform.NETEASE, MusicPlatform.QQ, MusicPlatform.KUGOU,
            MusicPlatform.YOUTUBE, MusicPlatform.BILIBILI, MusicPlatform.QISHUI, MusicPlatform.GD
    };

    /** 两列排得下所需的最小内容带宽：再窄就退成一列。 */
    private static final double MIN_TWO_COLUMN_WIDTH = 380;

    /** 两列卡：行高、列间距、卡片圆角。绘制与命中测试读同一份。 */
    private static final double BASE_CARD_H = 56;
    private static final double CARD_GAP = 12;
    private static final double NOTE_GAP = 18;
    private static final double NOTE_INSET = 22;

    @Override
    public void render(OnboardingController controller,
                       double x, double y, double width, double height,
                       double mouseX, double mouseY) {
        OnboardingUi.drawHeader("SOURCES", "选择音源",
                "选中的来源会立即应用到播放器并保存。", x, y, width);

        for (int i = 0; i < PLATFORMS.length; i++) {
            MusicPlatform platform = PLATFORMS[i];
            double cardX = cardX(x, width, i);
            double cardY = cardY(y, width, i);
            boolean selected = controller.selectedPlatform() == platform;
            boolean hover = OnboardingUi.inside(mouseX, mouseY, cardX, cardY, cardWidth(width), cardHeight());
            int brand = platform.getBrandColor();
            OnboardingUi.CardState state = selected ? OnboardingUi.CardState.SELECTED
                    : (hover ? OnboardingUi.CardState.HOVER : OnboardingUi.CardState.IDLE);
            double[] content = OnboardingUi.card(cardX, cardY, cardWidth(width), cardHeight(),
                    state, brand);

            // 徽标压在一块底盘上：七家的品牌图形一大一小（网易云是个圆、bilibili 是一对耳朵），
            // 直接按同一个尺寸画，读起来就是「这七张卡没对齐」。图标走 cardIcon 那个统一入口，
            // 但平台徽标是品牌矢量，不是 lucide，所以底盘单独画、位置从 content 推。
            double box = Math.min(30, content[3] * .66);
            double iconCenterY = content[1] + content[3] * .5;
            OnboardingUi.roundedRect(content[0], iconCenterY - box * .5, box, box, box * .32,
                    OnboardingUi.colorWithAlpha(selected ? OnboardingUi.BRAND_2 : brand,
                            selected ? 60 : 42));
            OnboardingUi.drawPlatformIcon(platform, content[0] + box * .5, iconCenterY, box,
                    selected ? OnboardingUi.CYAN : OnboardingUi.textSecondary());

            double textX = content[0] + box + 12;
            double badgeW = selected ? 22 : 0;
            OnboardingUi.cardText(content, textX, platform.getDisplayName(),
                    selected ? "当前使用" : "点击选择", badgeW + 6, state);
            if (selected) {
                OnboardingUi.cardSelectedBadge(content, OnboardingUi.CYAN, 18);
            }
        }

        double noteY = noteY(y, width);
        var font = OnboardingUi.metric().bodyFont();
        double lineHeight = OnboardingMetrics.lineHeight(font);
        OnboardingUi.drawIcon(FoliaLucideIcons.INFO, PartyIcons.INFO, x + 7, noteY + lineHeight * .5, 13,
                OnboardingUi.textMuted());
        for (String line : noteLines(width)) {
            OnboardingUi.drawText(font, line, x + NOTE_INSET, noteY, OnboardingUi.textMuted());
            noteY += lineHeight;
        }
    }

    /**
     * 卡片宽：常规是两列，内容带窄到 {@link #MIN_TWO_COLUMN_WIDTH} 以下时退成一列。
     *
     * <p>两列是为了在同一屏里放下七家；但每一列本身要装得下「图标 + 名称」才算数——
     * 内容带只剩 170 宽时，一列只有 79，连「YouTube Music」都放不下，读起来是七个被截断的词。
     * 那时改成一列，卡片变宽、能滚出七行（多出来的高度由内容带的渐隐交代）。</p>
     */
    static double cardWidth(double width) {
        return columnsFor(width) == 1 ? width : (width - CARD_GAP) * .5;
    }

    static int columnsFor(double width) {
        return width < MIN_TWO_COLUMN_WIDTH ? 1 : 2;
    }

    static double cardX(double x, double width, int index) {
        return x + (index % columnsFor(width)) * (cardWidth(width) + CARD_GAP);
    }

    static double cardY(double y, double width, int index) {
        return y + OnboardingUi.metric().bodyOffset
                + (index / columnsFor(width)) * (cardHeight() + CARD_GAP);
    }

    /** 卡片高度跟着排版档走，绘制与命中测试读同一份。 */
    static double cardHeight() {
        return Math.max(44, BASE_CARD_H + (OnboardingUi.metric().cardHeight - 56));
    }

    static int cardCount() {
        return PLATFORMS.length;
    }

    static double noteY(double y, double width) {
        return cardY(y, width, PLATFORMS.length - 1) + cardHeight() + NOTE_GAP;
    }

    private static String[] noteLines(double width) {
        String note = Component.translatable("sh_better_ui.muonium.experience.source_note").getString();
        return NcmExpressive.wrap(OnboardingUi.metric().bodyFont(), note, Math.max(1, width - NOTE_INSET));
    }

    @Override
    public double minimumHeight(double width) {
        return noteY(0, width)
                + noteLines(width).length * OnboardingMetrics.lineHeight(OnboardingUi.metric().bodyFont());
    }

    @Override
    public boolean mouseClicked(OnboardingController controller,
                                 double x, double y, double width, double height,
                                 double mouseX, double mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return false;
        }
        for (int i = 0; i < PLATFORMS.length; i++) {
            if (OnboardingUi.inside(mouseX, mouseY, cardX(x, width, i), cardY(y, width, i),
                    cardWidth(width), cardHeight())) {
                controller.selectPlatform(PLATFORMS[i]);
                return true;
            }
        }
        return false;
    }
}
