// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.text;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.EnumMap;

/** Optional delegation to the installed provider; no copied algorithms, bundled jars or fallback imitation. */
final class GtshOptionalText {
    enum Provider {
        CORE("org.gtlcore.gtlcore.utils.TextUtil"),
        ADDITIONS("com.gtladd.gtladditions.utils.CommonUtils");
        final String type;
        Provider(String type) { this.type = type; }
    }
    enum Operation {
        RAINBOW("full_color"), DARK_PURPLISH_RED("dark_purplish_red"), WHITE_BLUE("white_blue"),
        PURPLISH_RED("purplish_red"), GOLDEN("golden"), DARK_GREEN("dark_green"),
        FORMAT(Provider.CORE, "formatting", String.class, false, String.class, ChatFormatting[].class, double.class),
        ADD_RAINBOW(Provider.ADDITIONS, "createRainbowComponent", Component.class, false, String.class),
        ADD_OBFUSCATED_RAINBOW(Provider.ADDITIONS, "createObfuscatedRainbowComponent", Component.class, true, String.class),
        ADD_OBFUSCATED_DELETE(Provider.ADDITIONS, "createObfuscatedDeleteComponent", Component.class, true, String.class),
        ADD_RAINBOW_ON(Provider.ADDITIONS, "createLanguageRainbowComponent", Component.class, false, MutableComponent.class);

        final Provider provider;
        final String method;
        final Class<?> result;
        final boolean instance;
        final Class<?>[] parameters;
        Operation(String method) { this(Provider.CORE, method, String.class, false, String.class); }
        Operation(Provider provider, String method, Class<?> result, boolean instance, Class<?>... parameters) {
            this.provider = provider; this.method = method; this.result = result;
            this.instance = instance; this.parameters = parameters;
        }
    }
    @FunctionalInterface interface Lookup { Class<?> find(String name) throws ClassNotFoundException; }
    private record Binding(Method method, Field instance) {}
    private final Lookup lookup;
    private final EnumMap<Operation, Binding> bindings = new EnumMap<>(Operation.class);

    GtshOptionalText() { this(name -> Class.forName(name, false, GtshOptionalText.class.getClassLoader())); }
    GtshOptionalText(Lookup lookup) { this.lookup = lookup; }

    /** Checks exact public signatures without initializing provider classes or invoking their methods. */
    boolean available(Provider provider) {
        try {
            for (Operation operation : Operation.values()) if (operation.provider == provider) binding(operation);
            return true;
        } catch (Unavailable absent) { return false; }
    }

    Object invoke(Operation operation, Object... arguments) {
        Binding binding = binding(operation);
        try {
            Object receiver = binding.instance == null ? null : binding.instance.get(null);
            return binding.method.invoke(receiver, arguments);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Optional text provider failed: " + operation.provider.type + "." + operation.method, cause);
        } catch (IllegalAccessException failure) {
            throw new Unavailable(operation, failure);
        }
    }

    private synchronized Binding binding(Operation operation) {
        Binding existing = bindings.get(operation);
        if (existing != null) return existing;
        try {
            Class<?> type = lookup.find(operation.provider.type);
            Method method = type.getMethod(operation.method, operation.parameters);
            if (!operation.result.isAssignableFrom(method.getReturnType())
                    || Modifier.isStatic(method.getModifiers()) == operation.instance) {
                throw new NoSuchMethodException("Incompatible return type or static/instance contract");
            }
            Field instance = null;
            if (operation.instance) {
                instance = type.getField("INSTANCE");
                if (!Modifier.isStatic(instance.getModifiers()) || !type.isAssignableFrom(instance.getType())) {
                    throw new NoSuchFieldException("Incompatible INSTANCE singleton");
                }
            }
            Binding resolved = new Binding(method, instance);
            bindings.put(operation, resolved);
            return resolved;
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new Unavailable(operation, failure);
        }
    }

    static final class Unavailable extends IllegalStateException {
        Unavailable(Operation operation, Throwable cause) {
            super("Optional text provider unavailable or incompatible: " + operation.provider.type + "." + operation.method
                    + ". Install a provider built for this Minecraft/loader version with the GTSH source API contract.", cause);
        }
    }
}
