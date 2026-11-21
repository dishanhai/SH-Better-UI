package com.dishanhai.sh_better_ui.kernel.internal.mp;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import static org.junit.jupiter.api.Assertions.*;

class MpSettingsScreenTest {
    private MpSettingsScreen.Edit mount() {
        var target=new MpSettingsBridgeTest.Entry(MpSettingsBridge.Kind.COLOR);
        MpSettingsBridgeTest.Model.entries=new MpSettingsBridgeTest.Entry[]{target};
        var bridge=new MpSettingsBridge(getClass().getClassLoader(),MpSettingsBridgeTest.Model.class.getName(), () -> MpSettingsBridgeTest.saves++);
        var screen=new MpSettingsScreen.Edit(new MpSettingsScreen(null),bridge.categories().get(0).groups().get(0).entries().get(0));
        screen.color=target.color.getRGB();screen.mountColor(10,70,240,rgb->{});screen.setFocused(screen.colorEditor);
        return screen;
    }
    @Test void noMpConstructionAndRemovalAreSafe() {
        var screen=new MpSettingsScreen(null);assertNotNull(screen);screen.removed();screen.removed();
    }
    @Test void keyboardEscapeAndRemovalNeverWriteBackend() {
        var host=mount();int initial=host.entry.color();
        // Red already has hue zero: HOME is intentionally a no-op, RIGHT creates a real preview.
        assertTrue(host.keyPressed(GLFW.GLFW_KEY_RIGHT,0,0));assertTrue(host.colorEditor.isEditing());
        assertTrue(host.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0));assertFalse(host.colorEditor.isEditing());assertEquals(initial,host.entry.color());
        host.keyPressed(GLFW.GLFW_KEY_RIGHT,0,0);host.removed();assertEquals(initial,host.entry.color());
    }
    @Test void outsideReleaseOnlyChangesDraftAndApplyWritesExactlyOnce() {
        var host=mount();int initial=host.entry.color(),saves=MpSettingsBridgeTest.saves;
        assertTrue(host.mouseClicked(90,100,0));assertTrue(host.mouseDragged(-100,100,0,-190,0));
        assertTrue(host.mouseReleased(-100,100,0));assertFalse(host.isDragging());assertFalse(host.colorEditor.isEditing());
        assertEquals(initial,host.entry.color());assertEquals(saves,MpSettingsBridgeTest.saves);
        host.acceptColor();host.acceptColor();assertEquals(host.color,host.entry.color());assertEquals(saves+1,MpSettingsBridgeTest.saves);
    }

    @Test void choiceApplyRequiresExplicitResponseAndRetainsFullRiskHint() {
        var target=new MpSettingsBridgeTest.Entry(MpSettingsBridge.Kind.CHOICE);
        MpSettingsBridgeTest.Model.entries=new MpSettingsBridgeTest.Entry[]{target};
        var bridge=new MpSettingsBridge(getClass().getClassLoader(),MpSettingsBridgeTest.Model.class.getName(), () -> MpSettingsBridgeTest.saves++);
        var host=new MpSettingsScreen.Edit(new MpSettingsScreen(null),bridge.categories().get(0).groups().get(0).entries().get(0));
        host.choice="B";host.mountChoiceApply(10,10,120);
        var prompt=new java.util.concurrent.atomic.AtomicReference<com.dishanhai.sh_better_ui.client.gui.scaled.UiConfirmation.Prompt>();
        var response=new java.util.concurrent.atomic.AtomicReference<java.util.function.Consumer<Boolean>>();
        host.confirmationPresenter=(p,r)->{prompt.set(p);response.set(r);};
        int saves=MpSettingsBridgeTest.saves;
        host.applyButton.onPress();
        assertEquals("A",target.choice);assertEquals(saves,MpSettingsBridgeTest.saves);
        assertTrue(prompt.get().details().getString().contains("Sends packets to server; may trigger anticheat"));
        response.get().accept(false);assertEquals("A",target.choice);
        host.applyButton.onPress();host.choice="A"; // Dialog binds the exact B selection approved, not a later draft.
        response.get().accept(true);response.get().accept(true);
        assertEquals("B",target.choice);assertEquals(saves+1,MpSettingsBridgeTest.saves);
    }
    @Test void visibilityRefreshRetiresHiddenFocusedWidgetAndFirstTickKeepsFocus() throws Exception {
        var one=new MpSettingsBridgeTest.Entry(MpSettingsBridge.Kind.TOGGLE);
        var two=new MpSettingsBridgeTest.Entry(MpSettingsBridge.Kind.TOGGLE);
        MpSettingsBridgeTest.Model.entries=new MpSettingsBridgeTest.Entry[]{one,two};
        var bridge=new MpSettingsBridge(getClass().getClassLoader(),MpSettingsBridgeTest.Model.class.getName(), () -> MpSettingsBridgeTest.saves++);
        var host=new MpSettingsScreen(null);host.width=400;host.height=300;
        var field=MpSettingsScreen.class.getDeclaredField("bridge");field.setAccessible(true);field.set(host,bridge);
        var rebuild=MpSettingsScreen.class.getDeclaredMethod("rebuildRows");rebuild.setAccessible(true);rebuild.invoke(host);
        var button=(com.dishanhai.sh_better_ui.client.gui.scaled.UiButton)host.children().get(0);
        host.setFocused(button);host.tick();assertSame(button,host.getFocused());
        one.visible=false;host.tick();
        assertFalse(button.active);assertFalse(button.visible);assertFalse(host.children().contains(button));
        assertNotSame(button,host.getFocused());assertTrue(host.children().contains(host.getFocused()));
        // A retained stale callback is also guarded, even when external code holds the old widget.
        button.onPress();assertFalse(one.on);
    }
}
