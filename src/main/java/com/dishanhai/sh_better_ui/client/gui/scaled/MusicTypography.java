// Author: dishanhai. Scoped music typography with bundled OFL-1.1 fonts.
package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import com.dishanhai.sh_better_ui.client.font.LocalFontPack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/** Scoped UI typography: never replaces Minecraft's global font or specialized child icon fonts. */
public final class MusicTypography {
    public static final ResourceLocation FONT = ResourceLocation.tryParse("sh_better_ui:music_ui");
    public static final ResourceLocation SEMIBOLD = ResourceLocation.tryParse("sh_better_ui:music_ui_semibold");
    public static final int LINE_HEIGHT = 12;
    private static final SelectionCache SELECTION = new SelectionCache();
    private MusicTypography() {}
    public static void narrate(Component message) { Minecraft.getInstance().getNarrator().sayNow(message); }
    public static void resourcesReloaded() { SELECTION.invalidate(); }
    static final class SelectionCache {
        private Object manager; private String pack; private boolean legacy;
        synchronized boolean resolve(Object nextManager, String nextPack, java.util.function.BooleanSupplier lookup) {
            if (manager != nextManager || !java.util.Objects.equals(pack,nextPack)) {
                legacy = lookup.getAsBoolean(); manager = nextManager; pack = nextPack;
            }
            return legacy;
        }
        synchronized void invalidate() { manager = null; pack = null; }
    }
    public static ResourceLocation font() { return selectedFont(false, customPackSelected()); }
    public static ResourceLocation semibold() { return selectedFont(true, customPackSelected()); }
    static ResourceLocation selectedFont(boolean bold, boolean custom) {
        return custom ? (bold ? MpTypography.SEMIBOLD : MpTypography.FONT) : (bold ? SEMIBOLD : FONT);
    }
    private static boolean customPackSelected() {
        var client = Minecraft.getInstance();
        if (client == null || client.getResourcePackRepository() == null || client.getResourceManager() == null) return false;
        for (String id : client.getResourcePackRepository().getSelectedIds())
            if (id.startsWith("file/" + LocalFontPack.PREFIX)) {
                // Old generated packs only expose mp_ui. New packs expose music_ui
                // with their own glyph fallbacks, so leave those definitions in charge.
                return SELECTION.resolve(client.getResourceManager(), id, () -> {
                var definition = client.getResourceManager().getResource(ResourceLocation.tryParse("sh_better_ui:font/music_ui.json"));
                return usesLegacyDefinition(definition.map(resource -> resource.sourcePackId()).orElse(""));
                });
            }
        SELECTION.invalidate();
        return false;
    }
    static boolean usesLegacyDefinition(String winningMusicPack) {
        return !winningMusicPack.startsWith("file/") && !winningMusicPack.startsWith("server/");
    }
    public static MutableComponent text(Component source) {
        return source.copy().withStyle(style -> isMusicDefault(style)
                ? style.withFont(SEMIBOLD.equals(style.getFont()) ? semibold() : font()) : style);
    }
    public static MutableComponent bold(Component source) {
        return source.copy().withStyle(style -> isMusicDefault(style) ? style.withFont(semibold()) : style);
    }
    // Explicit caller/resource-pack font choices win, including specialized root icon fonts.
    private static boolean isMusicDefault(Style style) {
        var selected = style.getFont();
        return selected.equals(Style.EMPTY.getFont()) || FONT.equals(selected) || SEMIBOLD.equals(selected);
    }
    public static MutableComponent bold(String source) { return bold(Component.literal(source)); }
    public static MutableComponent text(String source) { return text(Component.literal(source)); }
    public static int width(Font font, Component source) { return font.width(text(source)); }
    public static void draw(GuiGraphics graphics, Font font, Component source, int x, int y, int color) {
        graphics.drawString(font, text(source), x, y, color, false);
    }
}
