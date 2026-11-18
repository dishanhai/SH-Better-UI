// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
class NativeConfirmationInputTest {
 @Test void defaultCancelTabCycleAndEscapeAreIsolatedFromParent() throws Exception {
  try (var fixture = com.dishanhai.sh_better_ui.client.api.NativeTestClient.install()) {
  Screen parent=new Screen(Component.literal("Parent")) {};
  var current=new AtomicReference<Screen>(parent); var calls=new AtomicInteger();
  var choice=new ConfirmationChoice<>(parent,current::get,current::set,()->{},calls::incrementAndGet);
  var prompt=new UiConfirmation.Prompt(Component.literal("Title"),Component.literal("Details"),Component.literal("Confirm"),Component.literal("Cancel"));
  var modal=new UiConfirmation.ConfirmationScreen(prompt,UiPreferences.DEFAULT,null,choice,null,true);
  choice.attach(modal); current.set(modal); modal.width=320; modal.height=240; modal.initButtons();
  assertEquals(2,modal.children().size());
  assertSame(modal.children().get(1),modal.getFocused());
  assertTrue(modal.keyPressed(new KeyEvent(258,0,0))); assertSame(modal.children().get(0),modal.getFocused());
  assertTrue(modal.keyPressed(new KeyEvent(258,0,0))); assertSame(modal.children().get(1),modal.getFocused());
  assertTrue(modal.keyPressed(new KeyEvent(258,0,1))); assertSame(modal.children().get(0),modal.getFocused());
  assertTrue(modal.keyPressed(new KeyEvent(256,0,0))); assertSame(parent,current.get()); assertEquals(0,calls.get());
  choice.finish(true); assertEquals(0,calls.get());
  }
 }
}
