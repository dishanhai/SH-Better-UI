// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassDraw;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GlassMediaStyleTest {
 @Test void bothLabelsRemainReadableAcrossArbitraryGrayScenes() {
  var style = new GlassMediaStyle();
  for (int fill : new int[]{0xff000000,0xffffffff,0xff777777,0xff667788,0xff2266cc}) {
   for (int ink : new int[]{0xff000000,0xffffffff,0xff757575}) {
    var p = style.palette(fill, ink, 0xff888888, true);
    assertTrue((p.fill() >>> 24) >= 184);
    for (int scene = 0; scene <= 255; scene++) {
     int bg = 0xff000000 | scene * 0x010101;
     assertTrue(ContrastPolicy.ratio(p.title(),p.fill(),bg) >= 4.5);
     assertTrue(ContrastPolicy.ratio(p.subtitle(),p.fill(),bg) >= 4.5);
    }
    assertEquals(p, style.palette(fill, ink, 0xff888888, true));
    var solid = style.palette(fill, ink, 0xff888888, false);
    assertEquals(fill, solid.fill());
    assertEquals(p.title(), solid.title());
    assertEquals(p.subtitle(), solid.subtitle());
   }
  }
 }
 @Test void surfaceHasRealSnellOpticsAndExactCardBounds() {
  var draw = GlassMediaStyle.surface(10,20,180,64,0xffffffff);
  assertEquals(GlassDraw.Profile.SNELL,draw.profile());
  assertTrue(draw.material().optics().refraction()>0);
  assertEquals(10,draw.region().x()); assertEquals(20,draw.region().y());
  assertEquals(180,draw.region().width()); assertEquals(64,draw.region().height());
 }
}
