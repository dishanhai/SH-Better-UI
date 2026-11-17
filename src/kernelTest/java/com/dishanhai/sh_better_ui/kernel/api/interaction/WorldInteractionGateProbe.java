package com.dishanhai.sh_better_ui.kernel.api.interaction;

/** Author: dishanhai. Pure JDK checks for accidental confirmation and hidden surfaces. */
public final class WorldInteractionGateProbe {
    public static void main(String[] args) throws Exception {
        Class<?> type;
        try { type = Class.forName("com.dishanhai.sh_better_ui.kernel.api.interaction.WorldInteractionGate"); }
        catch (ClassNotFoundException missing) { throw new AssertionError("world AR confirmation API is missing", missing); }
        Object gate = type.getConstructor(long.class).newInstance(3000L);
        var tap = type.getMethod("tap", String.class, long.class);
        check(!((boolean) tap.invoke(gate, "delete A", 100L)), "first tap arms");
        check((boolean) tap.invoke(gate, "delete A", 200L), "same action confirms");
        check(!((boolean) tap.invoke(gate, "delete A", 300L)), "confirmation is single use");
        check(!((boolean) tap.invoke(gate, "delete B", 400L)), "changed action cannot confirm");
        check(!((boolean) tap.invoke(gate, "delete B", 3401L)), "expired action cannot confirm");
        type.getMethod("cancel").invoke(gate);
        check(!((boolean) tap.invoke(gate, "delete B", 3500L)), "cancel discards action");
        check(!((boolean) tap.invoke(gate, "delete B", 3400L)), "backward clock cannot confirm");
        var visible = type.getMethod("visible", double.class, double.class, double.class, double.class);
        check((boolean) visible.invoke(null, 2.0, Double.POSITIVE_INFINITY, 1.0, -1.0), "front surface is visible");
        check(!((boolean) visible.invoke(null, 2.0, 1.0, 1.0, -1.0)), "nearer blocker wins");
        check(!((boolean) visible.invoke(null, 2.0, 3.0, -1.0, 1.0)), "backface is not interactive");
        check(!((boolean) visible.invoke(null, Double.NaN, 3.0, 1.0, -1.0)), "invalid geometry refuses input");
        System.out.println("WorldInteractionGate: 11 checks passed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
