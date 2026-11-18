// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class NativeRichMediaTest {
    private static final Identifier IMAGE=Identifier.parse("example:textures/test.gif");
    static class Backend implements NativeRichMedia.Backend {
        final Object resources=new Object();final AtomicInteger decodes=new AtomicInteger();int uploads,releases;
        int width=4,height=2;
        CountDownLatch entered,blocked;
        public Object resources(){return resources;}
        public RichMediaDecoder.Sheet decode(Identifier id,Object manager){
            decodes.incrementAndGet();if(entered!=null)entered.countDown();
            if(blocked!=null){boolean done=false;while(!done)try{blocked.await();done=true;}catch(InterruptedException ignored){/* emulate uninterruptible resource IO */}}
            return new RichMediaDecoder.Sheet(new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB),width/2,height,
                    List.of(new RichMediaDecoder.Frame(0,0,100),new RichMediaDecoder.Frame(width/2,0,100)),0);
        }
        public Identifier upload(RichMediaDecoder.Sheet image){return Identifier.parse("example:uploaded_"+(++uploads));}
        public void release(Identifier texture){releases++;}
    }
    static void request(NativeRichMedia media,Identifier...ids){media.beginVisibleFrame();for(var id:ids)media.request(id);media.endVisibleFrame();}
    static void ready(NativeRichMedia media,Backend backend,int uploads)throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(backend.uploads<uploads&&System.nanoTime()<until){media.prepare(1000);Thread.sleep(5);}
        assertEquals(uploads,backend.uploads);
    }
    @Test void atlasUsesFullFrameUvAndReducedMotionFreezesFirstFrame()throws Exception {
        var backend=new Backend();var media=new NativeRichMedia(backend);
        try{
            request(media,IMAGE);assertNull(media.region(IMAGE,1000,false));assertEquals(0,backend.uploads);ready(media,backend,1);
            var first=media.region(IMAGE,1000,false);var second=media.region(IMAGE,1150,false);
            assertEquals(0,first.u0());assertEquals(.5f,first.u1());assertEquals(.5f,second.u0());assertEquals(1,second.u1());
            assertEquals(first,media.region(IMAGE,1150,true));assertEquals(1,first.v1());
        }finally{media.close();assertEquals(0,backend.releases);media.prepare(0);}
        assertEquals(1,backend.releases);
    }
    @Test void leavingViewportReleasesAndDoesNotStartHiddenSources()throws Exception {
        var backend=new Backend();var media=new NativeRichMedia(backend);
        try{
            media.prepare(0);assertEquals(0,backend.decodes.get());request(media,IMAGE);ready(media,backend,1);
            request(media);assertNull(media.region(IMAGE,1200,false));media.prepare(1200);assertEquals(1,backend.releases);
        }finally{media.close();media.prepare(0);}
    }
    @Test void sameManagerReloadEpochInvalidatesAndRebuilds()throws Exception {
        var backend=new Backend();var media=new NativeRichMedia(backend);
        try{
            request(media,IMAGE);ready(media,backend,1);NativeRichMedia.resourcesReloaded();
            assertNull(media.region(IMAGE,1000,false));media.prepare(1000);assertEquals(1,backend.releases);
            request(media,IMAGE);ready(media,backend,2);assertEquals(2,backend.decodes.get());
        }finally{media.close();media.prepare(0);}
    }
    @Test void closedOwnerCannotUploadLateUninterruptibleDecode()throws Exception {
        var backend=new Backend();backend.entered=new CountDownLatch(1);backend.blocked=new CountDownLatch(1);
        var media=new NativeRichMedia(backend);
        try{
            request(media,IMAGE);media.prepare(1000);assertTrue(backend.entered.await(5,TimeUnit.SECONDS));
            media.close();backend.blocked.countDown();Thread.sleep(30);media.prepare(1000);
            assertEquals(0,backend.uploads);assertNull(media.region(IMAGE,1000,false));
        }finally{backend.blocked.countDown();media.close();media.prepare(0);}
    }
    @Test void reloadDiscardsLateOldDecodeBeforeNewGenerationUploads()throws Exception {
        var backend=new Backend();backend.entered=new CountDownLatch(1);backend.blocked=new CountDownLatch(1);
        var media=new NativeRichMedia(backend);
        try{
            request(media,IMAGE);media.prepare(1000);assertTrue(backend.entered.await(5,TimeUnit.SECONDS));
            NativeRichMedia.resourcesReloaded();media.prepare(1000);request(media,IMAGE);media.prepare(1000);
            backend.blocked.countDown();ready(media,backend,1);
            for(int i=0;i<5;i++){media.prepare(1000);Thread.sleep(10);}
            assertEquals(1,backend.uploads);
        }finally{backend.blocked.countDown();media.close();media.prepare(0);}
    }
    @Test void resourceAllowListNeverStartsArbitraryFilesOrUrls()throws Exception {
        assertFalse(NativeRichMedia.supports(Identifier.parse("example:fonts/test.gif")));
        assertFalse(NativeRichMedia.supports(Identifier.parse("example:textures/test.svg")));
        assertTrue(NativeRichMedia.supports(Identifier.parse("example:textures/test.apng")));
        var backend=new Backend();var media=new NativeRichMedia(backend);
        try{request(media,Identifier.parse("example:fonts/test.gif"));media.prepare(0);assertEquals(0,backend.decodes.get());}
        finally{media.close();media.prepare(0);}
    }
    @Test void mountBudgetRejectsSecondAtlasWithoutLeakingFirst()throws Exception {
        var backend=new Backend();backend.width=2048;backend.height=2048;var media=new NativeRichMedia(backend);
        try{
            request(media,IMAGE);ready(media,backend,1);
            request(media,IMAGE,Identifier.parse("example:textures/second.png"));
            for(int i=0;i<20;i++){media.prepare(1000);Thread.sleep(10);}
            assertEquals(1,backend.uploads);assertNull(media.region(Identifier.parse("example:textures/second.png"),1000,false));
        }finally{media.close();media.prepare(0);}
        assertEquals(1,backend.releases);
    }
    @Test void pngSidecarOnlyAppliesFromSameOrHigherPriorityPack()throws Exception {
        var image=new BufferedImage(2,4,BufferedImage.TYPE_INT_ARGB);
        var output=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",output);
        byte[] bytes=output.toByteArray();
        byte[] metadata="{\"animation\":{\"width\":2,\"height\":2,\"frametime\":2,\"frames\":[0,1]}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var packType=net.minecraft.server.packs.PackResources.class;
        var low=(net.minecraft.server.packs.PackResources)java.lang.reflect.Proxy.newProxyInstance(packType.getClassLoader(),new Class<?>[]{packType},(proxy,method,args)->null);
        var high=(net.minecraft.server.packs.PackResources)java.lang.reflect.Proxy.newProxyInstance(packType.getClassLoader(),new Class<?>[]{packType},(proxy,method,args)->null);
        for(int selection=0;selection<3;selection++){
            var main=selection==0?high:low;
            var side=selection==2?low:selection==0?low:high;
            var managerType=net.minecraft.server.packs.resources.ResourceManager.class;
            var manager=(net.minecraft.server.packs.resources.ResourceManager)java.lang.reflect.Proxy.newProxyInstance(managerType.getClassLoader(),new Class<?>[]{managerType},(proxy,method,args)->switch(method.getName()){
                case "listPacks"->java.util.stream.Stream.of(low,high);
                case "getResource"->{
                    boolean meta=args[0].toString().endsWith(".mcmeta");
                    yield Optional.of(new net.minecraft.server.packs.resources.Resource(meta?side:main,
                            ()->new java.io.ByteArrayInputStream(meta?metadata:bytes)));
                }
                default->throw new UnsupportedOperationException(method.getName());
            });
            var decoded=NativeRichMedia.decodePack(Identifier.parse("example:textures/sheet.png"),manager);
            try{assertEquals(selection==0?1:2,decoded.frames().size());}
            finally{decoded.pixels().flush();}
        }
    }

}
