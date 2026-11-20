// Author: dishanhai. Optional public-API adapter; no upstream code/assets embedded.
package com.dishanhai.sh_better_ui.kernel.internal.mp;

import java.awt.Color;
import java.lang.reflect.*;
import java.util.*;

/** MP owns the values and persistence. Never cache credentials or MP singleton instances globally. */
public final class MpSettingsBridge {
    public enum Kind { SLIDER, TOGGLE, COLOR, CHOICE, THEME_GRID, ACTION }
    public record Group(String title, List<Entry> entries) {}
    public record Category(String title, List<Group> groups) {}
    public static final class Failure extends RuntimeException {
        public Failure(String message, Throwable cause) { super(message, cause); }
    }
    private final ClassLoader loader;
    private final List<Category> categories;
    public MpSettingsBridge(ClassLoader loader) {
        this(loader, "com.muoniumplayer.core.screens.hud.HudSettingsModel");
    }
    // Alternate public model name allows pure JVM compatibility fixtures without loading Minecraft.
    private final Runnable saveAction;

    public MpSettingsBridge(ClassLoader loader, String modelName) {
        this(loader, modelName, null);
    }

    MpSettingsBridge(ClassLoader loader, String modelName, Runnable saveAction) {
        this.saveAction = saveAction == null ? () -> call(type("com.muoniumplayer.core.settings.HudConfig"), "save") : saveAction;
        this.loader = Objects.requireNonNull(loader);
        List<Category> result = new ArrayList<>();
        for (Object category : array(call(type(modelName), "categories"))) {
            List<Group> groups = new ArrayList<>();
            for (Object group : array(field(category, "groups"))) {
                List<Entry> entries = new ArrayList<>();
                for (Object entry : array(field(group, "entries"))) entries.add(new Entry(entry));
                groups.add(new Group(string(field(group, "title")), List.copyOf(entries)));
            }
            result.add(new Category(string(field(category, "title")), List.copyOf(groups)));
        }
        categories = List.copyOf(result);
    }
    public boolean upstreamPanelAvailable() {
        return (boolean) call(type("com.muoniumplayer.core.screens.hud.OneConfigHudBridge"), "isAvailable");
    }
    public void openUpstreamPanel() {
        if (!upstreamPanelAvailable()) throw new IllegalStateException("Install compatible MuoniumConfig / OneConfig to open the full upstream panel");
        if (!(boolean) call(type("com.muoniumplayer.core.screens.hud.OneConfigHudBridge"), "open")) throw new IllegalStateException("MuoniumConfig failed to open; inspect MP logs");
    }
    public List<Category> categories() { return categories; }
    public void save() { saveAction.run(); }
    public Object newHudEditor() {
        try { return type("com.muoniumplayer.core.screens.hud.GuiHudEditorPro").getConstructor().newInstance(); }
        catch (ReflectiveOperationException | LinkageError e) { throw failure(e); }
    }
    public List<String> themes() {
        List<String> names = new ArrayList<>();
        for (Object p : themeType().getEnumConstants()) if ((boolean) call(theme(), "isSelectable", new Class<?>[]{themeType()}, p)) names.add(string(call(p, "getDisplayName")));
        return names;
    }
    public String currentTheme() { return string(call(theme(), "getCurrentName")); }
    public void theme(String name) {
        for (Object p : themeType().getEnumConstants()) if (name.equals(call(p, "getDisplayName"))) {
            if (!(boolean) call(theme(), "isSelectable", new Class<?>[]{themeType()}, p)) throw new IllegalStateException("Theme is locked");
            call(theme(), "setCurrent", new Class<?>[]{themeType()}, p); save(); return;
        }
        throw new IllegalArgumentException("Unknown MP theme");
    }
    private Class<?> theme() { return type("com.muoniumplayer.core.screens.ncm.NCMTheme"); }
    private Class<?> themeType() { return type("com.muoniumplayer.core.screens.ncm.NCMTheme$ThemePreset"); }
    public final class Entry {
        private final Object target;
        private final Kind kind;
        Entry(Object target) { this.target = target; kind = Kind.valueOf(string(field(target, "kind"))); }
        public Kind kind() { return kind; }
        public String label() { return string(call(target, "getLabel")); }
        public String description() { return string(field(target, "description")); }
        public boolean visible() { return (boolean) call(target, "isVisible"); }
        public boolean locked() { return (boolean) call(target, "isLocked"); }
        public boolean on() { return (boolean) call(target, "isOn"); }
        public String valueText() { return string(call(target, "getValueText")); }
        public List<String> choiceHints() { return List.of((String[]) call(target, "getChoiceHints")); }
        public List<String> choices() { return List.of((String[]) call(target, "getChoices")); }
        public float min() { return number("getMin"); }
        public float max() { return number("getMax"); }
        public float value() { return number("getValue"); }
        private float number(String method) { return ((Number) call(field(target, "setting"), method)).floatValue(); }
        public int color() { return ((Color) call(target, "getColor")).getRGB(); }
        private void writable() { if (!visible() || locked()) throw new IllegalStateException("Setting unavailable or locked"); }
        public void activate() { writable(); call(target, "activate"); save(); }
        public void choice(String choice) {
            writable(); if (!choices().contains(choice)) throw new IllegalArgumentException("Unknown option");
            call(target, "setChoice", new Class<?>[]{String.class}, choice); save();
        }
        public void value(float value) {
            writable(); if (!Float.isFinite(value) || value < min() || value > max()) throw new IllegalArgumentException("Outside slider range");
            call(field(target, "setting"), "setValue", new Class<?>[]{float.class}, value); save();
        }
        public void color(int rgb) { writable(); call(target, "setColor", new Class<?>[]{Color.class}, new Color(rgb, true)); save(); }
    }
    private Class<?> type(String name) {
        try { return Class.forName(name, false, loader); }
        catch (ClassNotFoundException | LinkageError e) { throw failure(e); }
    }
    private static Object[] array(Object value) { return (Object[]) value; }
    private static String string(Object value) { return value == null ? "" : value.toString(); }
    private static Object field(Object target, String name) {
        try { return target.getClass().getField(name).get(target); }
        catch (ReflectiveOperationException | LinkageError e) { throw failure(e); }
    }
    private static Object call(Object target, String name) { return call(target, name, new Class<?>[0]); }
    private static Object call(Object target, String name, Class<?>[] signature, Object... args) {
        try {
            Class<?> owner = target instanceof Class<?> c ? c : target.getClass();
            return owner.getMethod(name, signature).invoke(target instanceof Class<?> ? null : target, args);
        } catch (ReflectiveOperationException | LinkageError e) { throw failure(e); }
    }
    private static Failure failure(Throwable e) {
        Throwable cause = e instanceof InvocationTargetException i && i.getCause() != null ? i.getCause() : e;
        return new Failure("MuoniumPlayer unavailable or incompatible: " + cause.getClass().getSimpleName(), cause);
    }
}
