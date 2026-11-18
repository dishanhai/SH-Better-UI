// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.client.music.MusicSnapshot;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class OnlineCoverFramesTest {
    static byte[] png(int w,int h) throws IOException {
        var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        var random=new java.util.Random(7);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)image.setRGB(x,y,random.nextInt());
        var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);image.flush();return bytes.toByteArray();
    }
    static OnlineTrack song(String id){return new OnlineTrack("qq",id,id,id,"","",1000,"https://y.gtimg.cn/"+id);}
    @Test void noisyCoverIsBoundedBeforeBecomingSnapshotArtwork() throws Exception {
        var cover=OnlineCoverFrames.thumbnail(png(512,256));
        assertTrue(cover.png().length<=MusicSnapshot.Artwork.MAX_BYTES);
        var image=ImageIO.read(new ByteArrayInputStream(cover.png()));
        assertEquals(240,image.getWidth());assertEquals(120,image.getHeight());image.flush();
        assertThrows(IOException.class,()->OnlineCoverFrames.thumbnail(png(513,1)));
        assertThrows(IOException.class,()->OnlineCoverFrames.thumbnail(new byte[]{1,2,3}));
    }
    @Test void staleDownloadCannotReplaceNewTrackAndRepeatedReadsDoNotRedownload() throws Exception {
        var started=new CountDownLatch(1);var release=new CountDownLatch(1);var calls=new AtomicInteger();
        byte[] bytes=png(12,12);
        try(var covers=new OnlineCoverFrames(url->{
            calls.incrementAndGet();
            if(url.endsWith("a")){started.countDown();try{release.await();}catch(InterruptedException ignored){}}
            return bytes;
        })) {
            assertNull(covers.artwork(song("a")));assertTrue(started.await(2,TimeUnit.SECONDS));
            assertNull(covers.artwork(song("b")));release.countDown();
            MusicSnapshot.Artwork current=null;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            while(current==null&&System.nanoTime()<deadline){current=covers.artwork(song("b"));Thread.sleep(5);}
            assertNotNull(current);assertEquals(2,calls.get());
            assertSame(current,covers.artwork(song("b")));assertEquals(2,calls.get());
        }
    }
    @Test void closeRevokesPendingPublication() throws Exception {
        var started=new CountDownLatch(1);var release=new CountDownLatch(1);byte[] bytes=png(4,4);
        var covers=new OnlineCoverFrames(url->{started.countDown();try{release.await();}catch(InterruptedException ignored){}return bytes;});
        assertNull(covers.artwork(song("a")));assertTrue(started.await(2,TimeUnit.SECONDS));
        covers.close();release.countDown();assertNull(covers.artwork(song("a")));
    }
}
