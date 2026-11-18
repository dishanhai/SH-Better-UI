// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GlassControlsLifecycleTest {
 @Test void consumerFailureDoesNotLeaveTheOldLayoutInteractive() {
  var kit = new GlassControls(UiPreferences.DEFAULT);
  var area = new Bounds(0, 0, 120, 24);
  var button = kit.button(area, Component.literal("Action"), false, ignored -> {});
  var first = kit.slider(area, 0, 100, 1, 0, value -> Component.literal("Volume"), ignored -> {});
  var second = kit.slider(area, 0, 100, 1, 0, value -> Component.literal("Other"), ignored -> {});
  first.setOnCommit(ignored -> {}, ignored -> { throw new IllegalStateException("consumer"); });
  first.onClick(60, 12);
  assertTrue(first.value() > 0);
  assertThrows(IllegalStateException.class, kit::close);
  assertFalse(button.visible); assertFalse(button.active);
  assertFalse(first.visible); assertFalse(first.active);
  assertFalse(second.visible); assertFalse(second.active);
  assertEquals(0, first.value()); assertTrue(kit.isClosed());
  assertDoesNotThrow(kit::close);
 }
}
