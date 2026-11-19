package com.dishanhai.sh_better_ui.kernel.internal.music.view;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MenuContentRevealTest {
 @Test void revealsDownwardWithoutMovingTopOrHitTargets(){
  var r=new MenuContentReveal();assertEquals(0,r.sample(200,100,false));
  int middle=r.sample(200,180,false);assertTrue(middle>0&&middle<200);
  assertTrue(r.contains(10,10));assertFalse(r.contains(10+middle,10));
  assertEquals(200,r.sample(200,300,false));
 }
 @Test void reducedMotionAndKeyboardFinishImmediately(){
  var r=new MenuContentReveal();assertEquals(200,r.sample(200,100,true));
  assertEquals(200,r.sample(200,110,false));
  var keyboard=new MenuContentReveal();keyboard.sample(200,100,false);keyboard.finish();
  assertEquals(200,keyboard.sample(200,101,false));
 }
 @Test void reopenGetsNewTimelineAndRepeatedSamplingDoesNotRestart(){
  var r=new MenuContentReveal();r.sample(200,100,false);
  int mid=r.sample(200,180,false);assertEquals(mid,r.sample(200,180,false));
  r.close();var reopened=new MenuContentReveal();assertEquals(0,reopened.sample(200,400,false));
  assertEquals(200,reopened.sample(200,600,false));
 }
}
