// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.text;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Contract fixtures, not an installed GTLCore/GTLAdditions integration test. */
class GtshOptionalTextTest {
    private static Object[] arguments;
    private static int initialized;
    public static class Core {
        public static String full_color(String text) { return "rainbow:" + text; }
        public static String dark_purplish_red(String text) { return "dark:" + text; }
        public static String white_blue(String text) { return "white:" + text; }
        public static String purplish_red(String text) { return "purple:" + text; }
        public static String golden(String text) { return "gold:" + text; }
        public static String dark_green(String text) { return "green:" + text; }
        public static String formatting(String text, ChatFormatting[] colors, double interval) {
            arguments = new Object[]{text, colors, interval}; return text;
        }
    }
    public static class Additions {
        public static final Additions INSTANCE = new Additions();
        public static MutableComponent createRainbowComponent(String text) { return Component.literal("rainbow:" + text); }
        public Component createObfuscatedRainbowComponent(String text) { return Component.literal("obfuscated:" + text); }
        public Component createObfuscatedDeleteComponent(String text) { return Component.literal("delete:" + text); }
        public static Component createLanguageRainbowComponent(MutableComponent text) { return text; }
    }
    public static class Cold extends Core { static { initialized++; } }
    public static class WrongReturn { public static int full_color(String text) { return 1; } }
    public static class WrongStatic { public String full_color(String text) { return text; } }
    public static class Throwing extends Core {
        static final IllegalArgumentException FAILURE = new IllegalArgumentException("provider error");
        public static String full_color(String text) { throw FAILURE; }
    }
    public static class Checked {
        public static String full_color(String text) throws Exception { throw new Exception("checked provider error"); }
    }
    public static class MissingSingleton {
        public Component createObfuscatedRainbowComponent(String text) { return Component.literal(text); }
    }

    public static class WrongSingleton {
        public static final String INSTANCE = "wrong";
        public Component createObfuscatedRainbowComponent(String text) { return Component.literal(text); }
    }

    public static class NonStaticSingleton {
        public final NonStaticSingleton INSTANCE = this;
        public Component createObfuscatedRainbowComponent(String text) { return Component.literal(text); }
    }
    public static class ErrorProvider {
        static final AssertionError FAILURE = new AssertionError("provider error");
        public static String full_color(String text) { throw FAILURE; }
    }
    public static class BrokenInitializer {
        static { if (failInitialization()) throw new IllegalStateException("provider initialization"); }
        private static boolean failInitialization() { return true; }
        public static String full_color(String text) { return text; }
    }

    private static GtshOptionalText bridge() {
        return new GtshOptionalText(name -> name.equals(GtshOptionalText.Provider.CORE.type) ? Core.class : Additions.class);
    }

    @Test void missingProvidersAreOptionalAndFailExplicitlyOnlyWhenCalled() {
        var bridge = new GtshOptionalText(name -> { throw new ClassNotFoundException(name); });
        assertFalse(bridge.available(GtshOptionalText.Provider.CORE));
        assertFalse(bridge.available(GtshOptionalText.Provider.ADDITIONS));
        var failure = assertThrows(GtshOptionalText.Unavailable.class,
                () -> bridge.invoke(GtshOptionalText.Operation.RAINBOW, "text"));
        assertInstanceOf(ClassNotFoundException.class, failure.getCause());
        assertTrue(failure.getMessage().contains("org.gtlcore.gtlcore.utils.TextUtil.full_color"));
        // Merely loading the facade and using the native implementation does not resolve either mod.
        assertEquals("plain", ShanhaiTextAPI.styled("plain", null).getString());
    }

    @Test void delegatesEveryCoreOperationAndPreservesArgumentsWithoutRecoloring() {
        var bridge = bridge();
        assertTrue(bridge.available(GtshOptionalText.Provider.CORE));
        var operations = new GtshOptionalText.Operation[]{GtshOptionalText.Operation.RAINBOW,
                GtshOptionalText.Operation.DARK_PURPLISH_RED, GtshOptionalText.Operation.WHITE_BLUE,
                GtshOptionalText.Operation.PURPLISH_RED, GtshOptionalText.Operation.GOLDEN, GtshOptionalText.Operation.DARK_GREEN};
        var prefixes = new String[]{"rainbow:", "dark:", "white:", "purple:", "gold:", "green:"};
        for (int i = 0; i < operations.length; i++) assertEquals(prefixes[i] + "🌊", bridge.invoke(operations[i], "🌊"));
        ChatFormatting[] colors = {ChatFormatting.RED, ChatFormatting.BOLD};
        assertEquals("§craw", bridge.invoke(GtshOptionalText.Operation.FORMAT, "§craw", colors, 2.5));
        assertSame(colors, arguments[1]); assertEquals(2.5, arguments[2]);
        assertNull(bridge.invoke(GtshOptionalText.Operation.FORMAT, null, colors, 0.0));
    }

    @Test void delegatesStaticAndKotlinSingletonMethodsAndPreservesComponentIdentity() {
        var bridge = bridge();
        assertTrue(bridge.available(GtshOptionalText.Provider.ADDITIONS));
        assertEquals("rainbow:x", ((Component) bridge.invoke(GtshOptionalText.Operation.ADD_RAINBOW, "x")).getString());
        assertEquals("obfuscated:x", ((Component) bridge.invoke(GtshOptionalText.Operation.ADD_OBFUSCATED_RAINBOW, "x")).getString());
        assertEquals("delete:x", ((Component) bridge.invoke(GtshOptionalText.Operation.ADD_OBFUSCATED_DELETE, "x")).getString());
        var component = Component.literal("styled").withStyle(ChatFormatting.BOLD);
        assertSame(component, bridge.invoke(GtshOptionalText.Operation.ADD_RAINBOW_ON, component));
    }

    @Test void availabilityDoesNotInitializeProviders() {
        int before = initialized;
        var bridge = new GtshOptionalText(name -> Class.forName(Cold.class.getName(), false, getClass().getClassLoader()));
        assertTrue(bridge.available(GtshOptionalText.Provider.CORE));
        assertEquals(before, initialized);
    }

    @Test void rejectsWrongSignaturesMissingSingletonAndLinkageMismatch() {
        for (Class<?> invalid : new Class<?>[]{WrongReturn.class, WrongStatic.class}) {
            var bridge = new GtshOptionalText(name -> invalid);
            assertFalse(bridge.available(GtshOptionalText.Provider.CORE));
            assertThrows(GtshOptionalText.Unavailable.class, () -> bridge.invoke(GtshOptionalText.Operation.RAINBOW, "x"));
        }
        assertThrows(GtshOptionalText.Unavailable.class, () -> new GtshOptionalText(name -> MissingSingleton.class)
                .invoke(GtshOptionalText.Operation.ADD_OBFUSCATED_RAINBOW, "x"));
        assertThrows(GtshOptionalText.Unavailable.class, () -> new GtshOptionalText(name -> WrongSingleton.class)
                .invoke(GtshOptionalText.Operation.ADD_OBFUSCATED_RAINBOW, "x"));
        assertThrows(GtshOptionalText.Unavailable.class, () -> new GtshOptionalText(name -> NonStaticSingleton.class)
                .invoke(GtshOptionalText.Operation.ADD_OBFUSCATED_RAINBOW, "x"));
        var mismatch = new GtshOptionalText(name -> { throw new NoClassDefFoundError("wrong game version"); });
        assertFalse(mismatch.available(GtshOptionalText.Provider.CORE));
    }

    @Test void providerFailuresAreNotReplacedByFakeFallbackText() {
        var throwing = new GtshOptionalText(name -> Throwing.class);
        assertSame(Throwing.FAILURE, assertThrows(IllegalArgumentException.class,
                () -> throwing.invoke(GtshOptionalText.Operation.RAINBOW, "x")));
        var checked = assertThrows(IllegalStateException.class,
                () -> new GtshOptionalText(name -> Checked.class).invoke(GtshOptionalText.Operation.RAINBOW, "x"));
        assertEquals("checked provider error", checked.getCause().getMessage());
    }
    @Test void providerErrorsAndInitializationFailuresRemainVisible() {
        var error = new GtshOptionalText(name -> ErrorProvider.class);
        assertSame(ErrorProvider.FAILURE, assertThrows(AssertionError.class,
                () -> error.invoke(GtshOptionalText.Operation.RAINBOW, "x")));
        var initialization = new GtshOptionalText(name -> BrokenInitializer.class);
        var failure = assertThrows(ExceptionInInitializerError.class,
                () -> initialization.invoke(GtshOptionalText.Operation.RAINBOW, "x"));
        assertEquals("provider initialization", failure.getCause().getMessage());
    }

}
