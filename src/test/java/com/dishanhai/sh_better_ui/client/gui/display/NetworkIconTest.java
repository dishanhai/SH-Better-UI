package com.dishanhai.sh_better_ui.client.gui.display;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：内置图标必须随库打包，并且可按 ARGB 着色。 */
class NetworkIconTest {
    @Test
    void networkTabIconsAreTransparentSixteenPixelTextures() throws IOException {
        assertIconTextures(List.of(GraphicIcon.HOME, GraphicIcon.LIST, GraphicIcon.WIRELESS, GraphicIcon.TOPOLOGY,
                GraphicIcon.STATISTICS, GraphicIcon.MEMBERS, GraphicIcon.SETTINGS, GraphicIcon.ADD));
    }

    @Test
    void overlayAndRefreshIconsAreTransparentSixteenPixelTextures() throws IOException {
        assertIconTextures(List.of(GraphicIcon.BUBBLE, GraphicIcon.PULL_DOWN, GraphicIcon.REFRESH, GraphicIcon.INFO,
                GraphicIcon.WARNING, GraphicIcon.LIST, GraphicIcon.SUCCESS, GraphicIcon.ERROR, GraphicIcon.CLOSE));
    }

    @Test
    void searchAndActionIconsAreTransparentSixteenPixelTextures() throws IOException {
        assertIconTextures(List.of(GraphicIcon.SEARCH, GraphicIcon.FILTER, GraphicIcon.SORT, GraphicIcon.EDIT,
                GraphicIcon.COPY, GraphicIcon.DELETE, GraphicIcon.FAVORITE, GraphicIcon.LOCK, GraphicIcon.UNLOCK,
                GraphicIcon.EXPAND, GraphicIcon.COLLAPSE, GraphicIcon.UPLOAD, GraphicIcon.DOWNLOAD,
                GraphicIcon.BACK, GraphicIcon.FORWARD, GraphicIcon.SWITCH, GraphicIcon.MERGE, GraphicIcon.NEW,
                GraphicIcon.SAVE, GraphicIcon.EXPORT));
        assertSame(GraphicIcon.BUBBLE, GraphicIcon.COMMENT);
    }

    @Test
    void graphicEditingIconsHaveBothTextureSizes() throws IOException {
        assertIconTextures(List.of(GraphicIcon.COPY, GraphicIcon.MOVE, GraphicIcon.SCALE, GraphicIcon.ROTATE,
                GraphicIcon.CUT, GraphicIcon.MIRROR, GraphicIcon.SYMMETRY, GraphicIcon.FILL, GraphicIcon.STROKE,
                GraphicIcon.FILTER, GraphicIcon.WARP, GraphicIcon.BLUR, GraphicIcon.PASTE));
    }

    @Test
    void navigationGameAndTimingIconsHaveBothTextureSizes() throws IOException {
        assertIconTextures(List.of(GraphicIcon.VISIT, GraphicIcon.PIN, GraphicIcon.SHARE, GraphicIcon.LINK,
                GraphicIcon.WINDOW, GraphicIcon.ARCHIVE, GraphicIcon.CHAT, GraphicIcon.HELP, GraphicIcon.SUPPORT,
                GraphicIcon.CLOCK, GraphicIcon.CALENDAR, GraphicIcon.PLAYER, GraphicIcon.MOB, GraphicIcon.ENTITY,
                GraphicIcon.TICK, GraphicIcon.TPS, GraphicIcon.MSPT, GraphicIcon.JEI));
        assertSame(GraphicIcon.BUBBLE, GraphicIcon.CHAT);
        assertSame(GraphicIcon.QUESTION, GraphicIcon.HELP);
    }

    @Test
    void compactHudIconsHaveBothTextureSizes() throws IOException {
        assertIconTextures(List.of(GraphicIcon.COMPACT_SIGNAL, GraphicIcon.COMPACT_CLOCK, GraphicIcon.COMPACT_CALENDAR,
                GraphicIcon.COMPACT_WORLD, GraphicIcon.COMPACT_BIOME, GraphicIcon.COMPACT_LOCATION,
                GraphicIcon.COMPACT_CHUNK, GraphicIcon.COMPACT_TORCH, GraphicIcon.COMPACT_TPS,
                GraphicIcon.COMPACT_MSPT, GraphicIcon.COMPACT_MEMORY, GraphicIcon.COMPACT_CPU,
                GraphicIcon.COMPACT_GPU, GraphicIcon.COMPACT_DISK));
    }

    @Test
    void severityAndMaintenanceIconsAreTransparentSixteenPixelTextures() throws IOException {
        assertIconTextures(List.of(GraphicIcon.WARNING, GraphicIcon.CRITICAL_WARNING, GraphicIcon.CANCEL,
                GraphicIcon.CRITICAL_CANCEL, GraphicIcon.REPAIR, GraphicIcon.RESET, GraphicIcon.DISABLE,
                GraphicIcon.ENABLE, GraphicIcon.HIDE, GraphicIcon.SHOW, GraphicIcon.ORGANIZE, GraphicIcon.SORT));
        assertSame(GraphicIcon.CLOSE, GraphicIcon.CANCEL);
    }

    @Test
    void utilityAndDeviceIconsAreTransparentSixteenPixelTextures() throws IOException {
        assertIconTextures(List.of(GraphicIcon.CACHE, GraphicIcon.LOG, GraphicIcon.MONITOR, GraphicIcon.TODO,
                GraphicIcon.DOCUMENT, GraphicIcon.CODE, GraphicIcon.TEST, GraphicIcon.DEVELOPMENT,
                GraphicIcon.COLLECTION, GraphicIcon.SEPARATE, GraphicIcon.HISTORY, GraphicIcon.USER,
                GraphicIcon.SYSTEM, GraphicIcon.PHONE, GraphicIcon.COMPUTER));
    }

    @Test
    void blockItemAndDetailIconsAreTransparentSixteenPixelTextures() throws IOException {
        assertIconTextures(List.of(GraphicIcon.BLOCK, GraphicIcon.ITEM, GraphicIcon.ATTRIBUTES,
                GraphicIcon.DESCRIPTION, GraphicIcon.TOOLTIP, GraphicIcon.INTERACTION));
    }

    @Test
    void runPauseAndResumeIconsAreTransparentSixteenPixelTextures() throws IOException {
        assertIconTextures(List.of(GraphicIcon.RUN, GraphicIcon.PAUSE, GraphicIcon.RESUME));
    }

    @Test
    void gameAndPeripheralIconsAreTransparentSixteenPixelTextures() throws IOException {
        assertIconTextures(List.of(GraphicIcon.MINECRAFT, GraphicIcon.GAMEPAD, GraphicIcon.MOUSE,
                GraphicIcon.KEYBOARD, GraphicIcon.HEADPHONES, GraphicIcon.MICROPHONE, GraphicIcon.FORGE,
                GraphicIcon.NEOFORGE));
    }

    @Test
    void modIconsAreTransparentSixteenPixelTextures() throws IOException {
        assertIconTextures(List.of(GraphicIcon.AE2, GraphicIcon.MEKANISM, GraphicIcon.FTB_QUESTS,
                GraphicIcon.KUBEJS, GraphicIcon.GREGTECH));
    }

    @Test
    void archiveSecurityAndReferenceIconsAreTransparentSixteenPixelTextures() throws IOException {
        assertIconTextures(List.of(GraphicIcon.COMPRESS, GraphicIcon.DECOMPRESS, GraphicIcon.ENCRYPT,
                GraphicIcon.DECRYPT, GraphicIcon.PLUGIN, GraphicIcon.GITHUB, GraphicIcon.MCMOD,
                GraphicIcon.WIKI));
    }

    @Test
    void administrationAndInfrastructureIconsHaveBothTextureSizes() throws IOException {
        assertIconTextures(List.of(GraphicIcon.SETTINGS, GraphicIcon.CONFIG, GraphicIcon.MANAGEMENT,
                GraphicIcon.PERMISSIONS, GraphicIcon.LIST, GraphicIcon.DETAILS, GraphicIcon.EDIT, GraphicIcon.MOD,
                GraphicIcon.TEMPLATE, GraphicIcon.DATA, GraphicIcon.NETWORK, GraphicIcon.SERVER,
                GraphicIcon.DATABASE, GraphicIcon.BACKUP, GraphicIcon.MIGRATE, GraphicIcon.UPGRADE,
                GraphicIcon.SECURITY, GraphicIcon.AUDIT, GraphicIcon.REPORT));
    }

    @Test
    void executionAndTaskIconsHaveBothTextureSizes() throws IOException {
        assertIconTextures(List.of(GraphicIcon.QUEUE, GraphicIcon.POLLING, GraphicIcon.ASYNC,
                GraphicIcon.CONCURRENCY, GraphicIcon.THREAD, GraphicIcon.PROCESS, GraphicIcon.PARALLEL,
                GraphicIcon.REWARD, GraphicIcon.SCHEDULER, GraphicIcon.TASK));
    }

    @Test
    void githubNavigationIconsHaveBothTextureSizes() throws IOException {
        assertIconTextures(List.of(GraphicIcon.GITHUB_REPO, GraphicIcon.GITHUB_PULL_REQUEST, GraphicIcon.GITHUB_ISSUE,
                GraphicIcon.GITHUB_DISCUSSION, GraphicIcon.GITHUB_ACTIONS, GraphicIcon.GITHUB_README,
                GraphicIcon.GITHUB_RELEASE, GraphicIcon.GITHUB_COMMITS));
    }

    @Test
    void licenseAndProjectCreditIconsHaveBothTextureSizes() throws IOException {
        assertIconTextures(List.of(GraphicIcon.LICENSE, GraphicIcon.LICENSE_OPEN_SOURCE,
                GraphicIcon.LICENSE_CLOSED_SOURCE, GraphicIcon.AUTHORIZATION, GraphicIcon.CONTRIBUTORS,
                GraphicIcon.AFDIAN, GraphicIcon.SUPPORT, GraphicIcon.ACKNOWLEDGMENTS));
    }

    @Test
    void aiBrandIconsHaveBothTextureSizes() throws IOException {
        assertIconTextures(List.of(GraphicIcon.CHATGPT, GraphicIcon.CLAUDE, GraphicIcon.DEEPSEEK,
                GraphicIcon.GROK, GraphicIcon.GEMINI));
    }

    @Test
    void contactIconsHaveBothTextureSizes() throws IOException {
        assertIconTextures(List.of(GraphicIcon.QQ, GraphicIcon.WECHAT, GraphicIcon.X, GraphicIcon.EMAIL,
                GraphicIcon.QQ_MAIL, GraphicIcon.ICLOUD_MAIL, GraphicIcon.GMAIL, GraphicIcon.DISCORD,
                GraphicIcon.TELEGRAM, GraphicIcon.WHATSAPP, GraphicIcon.FACEBOOK));
    }

    @Test
    void videoPlatformIconsHaveBothTextureSizes() throws IOException {
        assertIconTextures(List.of(GraphicIcon.BILIBILI, GraphicIcon.DOUYIN, GraphicIcon.KUAISHOU,
                GraphicIcon.YOUTUBE));
    }

    @Test
    void utilityAndAeChannelIconsHaveBothTextureSizes() throws IOException {
        assertIconTextures(List.of(GraphicIcon.MUSIC, GraphicIcon.APP, GraphicIcon.QUESTION,
                GraphicIcon.CALCULATE, GraphicIcon.CPU, GraphicIcon.GPU, GraphicIcon.DISK,
                GraphicIcon.MAP, GraphicIcon.AE_CHANNEL));
    }

    @Test
    void commerceIconsHaveBothTextureSizes() throws IOException {
        assertIconTextures(List.of(GraphicIcon.SHOP, GraphicIcon.BANK, GraphicIcon.MEMBERSHIP,
                GraphicIcon.POINTS, GraphicIcon.REDEEM, GraphicIcon.COUPON, GraphicIcon.POWER,
                GraphicIcon.SPARK, GraphicIcon.CURRENCY, GraphicIcon.TRADE, GraphicIcon.PAYMENT,
                GraphicIcon.BILL, GraphicIcon.SETTLEMENT, GraphicIcon.WITHDRAW, GraphicIcon.TRANSFER,
                GraphicIcon.BALANCE, GraphicIcon.BUY, GraphicIcon.SELL, GraphicIcon.SHOPPING_CART));
    }

    @Test
    void contentAndModIconsHaveBothTextureSizes() throws IOException {
        assertIconTextures(List.of(GraphicIcon.TITLE, GraphicIcon.TAG, GraphicIcon.COVER,
                GraphicIcon.CATEGORY, GraphicIcon.CHINESE, GraphicIcon.SUMMARY, GraphicIcon.BODY_TEXT,
                GraphicIcon.SOPHISTICATED_BACKPACKS, GraphicIcon.TEXTURE, GraphicIcon.SHBUI));
    }

    @Test
    void themeIconsHaveBothTextureSizes() throws IOException {
        assertIconTextures(List.of(GraphicIcon.DARK_MODE, GraphicIcon.LIGHT_MODE, GraphicIcon.CUSTOM_THEME));
    }

    @Test
    void fileAndCraftingIconsHaveBothTextureSizes() throws IOException {
        assertIconTextures(List.of(GraphicIcon.FOLDER, GraphicIcon.FILE, GraphicIcon.FILE_EXE,
                GraphicIcon.FILE_MD, GraphicIcon.FILE_JAVA, GraphicIcon.FILE_KT,
                GraphicIcon.AE2_PATTERN, GraphicIcon.CRAFTING));
    }

    @Test
    void patternRecipeAndBookmarkIconsHaveBothTextureSizes() throws IOException {
        assertIconTextures(List.of(GraphicIcon.VIRTUAL_ITEM_PROVIDER, GraphicIcon.VIRTUAL_ITEM, GraphicIcon.VIRTUAL,
                GraphicIcon.QUICK_PATTERN_ENCODE, GraphicIcon.QUICK, GraphicIcon.CLOUD_PATTERN_LIBRARY,
                GraphicIcon.CLOUD, GraphicIcon.RECIPE_TYPE, GraphicIcon.RECIPE, GraphicIcon.BOOKMARK_FAVORITE,
                GraphicIcon.BOOKMARK, GraphicIcon.RECIPE_ID, GraphicIcon.ID));
    }

    @Test
    void encodingStateIconsHaveBothTextureSizes() throws IOException {
        assertIconTextures(List.of(GraphicIcon.PATTERN_WRAP_AUTO, GraphicIcon.PATTERN_WRAP_ON,
                GraphicIcon.PATTERN_WRAP_OFF, GraphicIcon.PATTERN_RECIPE_ID_ON, GraphicIcon.PATTERN_RECIPE_ID_OFF,
                GraphicIcon.PATTERN_BYPRODUCT_ON, GraphicIcon.PATTERN_BYPRODUCT_OFF,
                GraphicIcon.PATTERN_VIRTUAL_PROVIDER_ON, GraphicIcon.PATTERN_VIRTUAL_PROVIDER_OFF));
    }

    @Test
    void planningMathAndAccessIconsHaveBothTextureSizes() throws IOException {
        assertIconTextures(List.of(GraphicIcon.PLAN, GraphicIcon.GOAL, GraphicIcon.EQUATION,
                GraphicIcon.FUNCTION, GraphicIcon.UNDO, GraphicIcon.ACCESS_DENIED));
    }

    @Test
    void cardChannelLocationAndWebIconsHaveBothTextureSizes() throws IOException {
        assertIconTextures(List.of(GraphicIcon.CARD, GraphicIcon.CHANNEL, GraphicIcon.LOCATION,
                GraphicIcon.BROWSER, GraphicIcon.WEB_SEARCH));
    }

    @Test
    void inputAndTextToolIconsHaveBothTextureSizes() throws IOException {
        assertIconTextures(List.of(GraphicIcon.EMOJI, GraphicIcon.SYMBOL, GraphicIcon.TEXT_INPUT,
                GraphicIcon.TRANSLATE, GraphicIcon.CLIPBOARD, GraphicIcon.NUMBER_INPUT));
    }

    @Test
    void dataFlowGuideAndInterfaceIconsHaveBothTextureSizes() throws IOException {
        assertIconTextures(List.of(GraphicIcon.INPUT, GraphicIcon.OUTPUT, GraphicIcon.GUIDEME,
                GraphicIcon.GUIDE, GraphicIcon.GUI, GraphicIcon.UI));
    }

    @Test
    void shopAndToolIconsHaveBothTextureSizes() throws IOException {
        assertIconTextures(List.of(GraphicIcon.RENDER, GraphicIcon.PRODUCT_CARD, GraphicIcon.PRODUCT,
                GraphicIcon.TOOL, GraphicIcon.TOOLBAR, GraphicIcon.HIDE, GraphicIcon.MY_PROFILE,
                GraphicIcon.MY_FAVORITES, GraphicIcon.PRODUCT_OUTPUT, GraphicIcon.PRODUCT_INPUT,
                GraphicIcon.INSTALL, GraphicIcon.COMPATIBILITY, GraphicIcon.QUANTUM,
                GraphicIcon.SELECT_ALL, GraphicIcon.SELECT_PARTIAL));
    }

    private static void assertIconTextures(List<GraphicIcon> icons) throws IOException {
        for (var icon : icons) {
            var id = icon.texture();
            String path = "/assets/" + id.getNamespace() + "/" + id.getPath();
            try (var stream = GraphicIcon.class.getResourceAsStream(path)) {
                assertNotNull(stream, path);
                var image = ImageIO.read(stream);
                assertNotNull(image, path);
                assertEquals(16, image.getWidth(), path);
                assertEquals(16, image.getHeight(), path);
                boolean transparent = false;
                boolean painted = false;
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int pixel = image.getRGB(x, y);
                        if ((pixel >>> 24) == 0) {
                            transparent = true;
                        } else {
                            assertEquals(0xFFFFFF, pixel & 0xFFFFFF, path);
                            painted = true;
                        }
                    }
                }
                assertTrue(transparent && painted, path);
                assertEnlargedCopy(path.replace("/display/", "/display_64/"), image);
            }
        }
    }

    private static void assertEnlargedCopy(String path, BufferedImage original) throws IOException {
        try (var stream = GraphicIcon.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            var copy = ImageIO.read(stream);
            assertNotNull(copy, path);
            assertEquals(64, copy.getWidth(), path);
            assertEquals(64, copy.getHeight(), path);
            for (int y = 0; y < copy.getHeight(); y++) {
                for (int x = 0; x < copy.getWidth(); x++) {
                    assertEquals(original.getRGB(x / 4, y / 4), copy.getRGB(x, y), path);
                }
            }
        }
    }
}
