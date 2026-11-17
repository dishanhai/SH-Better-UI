// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiScreenCloseTest {
    private com.dishanhai.sh_better_ui.client.api.NativeTestClient clientFixture;
    @org.junit.jupiter.api.BeforeEach void installNativeClient() throws Exception {
        clientFixture = com.dishanhai.sh_better_ui.client.api.NativeTestClient.install();
    }
    @org.junit.jupiter.api.AfterEach void restoreNativeClient() throws Exception {
        if (clientFixture != null) clientFixture.close();
    }

    private static Screen screen() { return new Screen(Component.empty()) {}; }

    @Test void staleCloseNeverInvokesGuardOrReplacesCurrentPage() {
        var owner = screen(); var other = screen(); var current = new AtomicReference<>(other);
        var calls = new AtomicInteger();
        UiScreen.closeIfCurrent(owner, null, current::get, current::set, () -> { calls.incrementAndGet(); return true; }, () -> null);
        assertSame(other, current.get()); assertEquals(0, calls.get());
    }

    @Test void guardOpeningAnotherPageWinsOverOldClose() {
        var owner = screen(); var other = screen(); var current = new AtomicReference<>(owner);
        UiScreen.closeIfCurrent(owner, null, current::get, current::set, () -> { current.set(other); return true; }, () -> null);
        assertSame(other, current.get());
    }

    @Test void deniedCloseKeepsOwnerAndSuccessfulCloseRestoresParent() {
        var owner = screen(); var parent = screen(); var current = new AtomicReference<>(owner);
        var focusCalls = new AtomicInteger();
        UiScreen.closeIfCurrent(owner, parent, current::get, current::set, () -> false, () -> { focusCalls.incrementAndGet(); return null; });
        assertSame(owner, current.get()); assertEquals(0, focusCalls.get());
        UiScreen.closeIfCurrent(owner, parent, current::get, current::set, () -> true, () -> { focusCalls.incrementAndGet(); return null; });
        assertSame(parent, current.get()); assertEquals(1, focusCalls.get());
    }

    @Test void parentOpeningAnotherPageDoesNotEvaluateStaleFocusSupplier() {
        var owner = screen(); var parent = screen(); var other = screen(); var current = new AtomicReference<>(owner);
        var focusCalls = new AtomicInteger();
        UiScreen.closeIfCurrent(owner, parent, current::get, target -> current.set(other), () -> true,
                () -> { focusCalls.incrementAndGet(); return null; });
        assertSame(other, current.get()); assertEquals(0, focusCalls.get());
    }
}
