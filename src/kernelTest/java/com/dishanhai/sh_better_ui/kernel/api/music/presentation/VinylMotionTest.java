package com.dishanhai.sh_better_ui.kernel.api.music.presentation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class VinylMotionTest {
 @Test void pausesAndReducedMotionFreezePhase(){var m=new VinylMotion();assertEquals(0,m.sample(0,true,false));assertEquals(1.8f,m.sample(100,true,false),.001f);assertEquals(1.8f,m.sample(200,false,false),.001f);assertEquals(1.8f,m.sample(300,true,true),.001f);assertEquals(3.6f,m.sample(400,true,false),.001f);}
 @Test void timeJumpsAreBoundedAndResetIsStable(){var m=new VinylMotion();m.sample(0,true,false);assertEquals(1.8f,m.sample(1000000,true,false),.001f);assertEquals(1.8f,m.sample(1,true,false),.001f);m.reset();assertEquals(0,m.sample(200,true,false));}
}
