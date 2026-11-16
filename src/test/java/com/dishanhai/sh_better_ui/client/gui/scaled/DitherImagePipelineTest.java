package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.kernel.api.effect.Dither;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class DitherImagePipelineTest {
    private static final class Manual implements Executor {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        boolean reject;
        @Override public void execute(Runnable task) {
            if (reject) throw new RejectedExecutionException();
            queue.add(task);
        }
        void run() { queue.remove().run(); }
    }
    private static BufferedImage sourceImage() {
        var image = new BufferedImage(11, 7, BufferedImage.TYPE_INT_ARGB);
        for (int y=0; y<7; y++) for (int x=0; x<11; x++)
            image.setRGB(x,y, x == 0 ? 0 : (0xff000000 | x*23 <<16 | y*31 <<8 | (x+y)*11));
        return image;
    }
    private static byte[] png() throws IOException {
        var out = new ByteArrayOutputStream(); ImageIO.write(sourceImage(),"png",out); return out.toByteArray();
    }
    private static Dither.Options options(Dither.Mode mode,Dither.ColorMode color,Dither.Fit fit) {
        var p=Dither.Options.DEMO;
        return new Dither.Options(p.gridSize(),mode,color,true,p.pixelRatio(),0xff123456,0xffabcdef,
                java.util.List.of(0xff001122,0xff445566,0xffaabbcc),.1f,1.2f,0x7f101020,fit,.4f,false,.02f);
    }
    private static DitherImagePipeline.Request request(int width, int height) {
        return new DitherImagePipeline.Request(width,height,Dither.Options.DEMO,0);
    }

    @Test void requestNeverOpensOrDecodesOnCallerAndStaticFramesDoNotRecompute() throws Exception {
        var executor=new Manual(); var opens=new AtomicInteger(); byte[] data=png();
        var pipeline=new DitherImagePipeline(()->{opens.incrementAndGet();return new ByteArrayInputStream(data);},executor);
        var request=request(20,12);pipeline.request(request);
        assertEquals(0,opens.get());assertNull(pipeline.poll());assertTrue(pipeline.pending());
        executor.run();assertEquals(1,opens.get());assertNotNull(pipeline.poll());assertFalse(pipeline.pending());
        for(int i=0;i<100;i++)pipeline.request(request);
        assertTrue(executor.queue.isEmpty());assertEquals(1,opens.get());pipeline.close();
    }

    @Test void queuedChangesCoalesceToLatestWithoutGrowingQueue() throws Exception {
        var executor=new Manual();byte[] data=png();var pipeline=new DitherImagePipeline(()->new ByteArrayInputStream(data),executor);
        for(int i=1;i<=50;i++)pipeline.request(request(i,12));
        assertEquals(1,executor.queue.size());executor.run();
        assertEquals(request(50,12),pipeline.poll().request());assertFalse(pipeline.pending());pipeline.close();
    }

    @Test void queueRejectionIsRetriedWithoutCallerRuns() throws Exception {
        var executor=new Manual();executor.reject=true;var opens=new AtomicInteger();byte[] data=png();
        var pipeline=new DitherImagePipeline(()->{opens.incrementAndGet();return new ByteArrayInputStream(data);},executor);
        pipeline.request(request(20,12));assertEquals(0,opens.get());assertTrue(pipeline.pending());
        executor.reject=false;pipeline.request(request(20,12));assertEquals(1,executor.queue.size());
        executor.run();assertNotNull(pipeline.poll());pipeline.close();
    }

    @Test void closingQueuedRequestPreventsOpeningResource() throws Exception {
        var executor=new Manual();var opens=new AtomicInteger();byte[] data=png();
        var pipeline=new DitherImagePipeline(()->{opens.incrementAndGet();return new ByteArrayInputStream(data);},executor);
        pipeline.request(request(20,12));pipeline.close();executor.run();
        assertEquals(0,opens.get());assertNull(pipeline.poll());assertFalse(pipeline.pending());
    }

    @Test void closingDuringDecodeCannotPublishAndReopenedPipelineUsesNewResource() throws Exception {
        byte[] data=png();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var finished=new CountDownLatch(1);
        Executor worker=task->new Thread(()->{try{task.run();}finally{finished.countDown();}},"dither-test").start();
        var old=new DitherImagePipeline(()->{entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IOException("timeout");}
            catch(InterruptedException e){throw new IOException(e);}return new ByteArrayInputStream(data);},worker);
        old.request(request(20,12));assertTrue(entered.await(5,TimeUnit.SECONDS));old.close();release.countDown();
        assertTrue(finished.await(5,TimeUnit.SECONDS));assertNull(old.poll());
        var executor=new Manual();var next=new DitherImagePipeline(()->new ByteArrayInputStream(data),executor);
        next.request(request(30,18));executor.run();assertEquals(request(30,18),next.poll().request());next.close();
    }

    @Test void requestChangingDuringDecodeDropsOldFrameAndKeepsOnlyNewest() throws Exception {
        byte[] data=png();var executor=new Manual();var holder=new DitherImagePipeline[1];
        holder[0]=new DitherImagePipeline(()->{holder[0].request(request(23,17));return new ByteArrayInputStream(data);},executor);
        var pipeline=holder[0];pipeline.request(request(20,12));executor.run();
        assertNull(pipeline.poll());assertEquals(1,executor.queue.size());executor.run();
        assertEquals(request(23,17),pipeline.poll().request());assertFalse(pipeline.pending());pipeline.close();
    }

    @Test void aNewRequestDiscardsUnconsumedOldResult() throws Exception {
        var executor=new Manual();byte[] data=png();var pipeline=new DitherImagePipeline(()->new ByteArrayInputStream(data),executor);
        pipeline.request(request(20,12));executor.run();pipeline.request(request(21,12));
        assertNull(pipeline.poll());executor.run();assertEquals(21,pipeline.poll().request().width());pipeline.close();
    }

    @Test void fittingAndDitheringMatchOldRasterForAllModesColorsAndFits() throws Exception {
        byte[] data=png();var executor=new Manual();var pipeline=new DitherImagePipeline(()->new ByteArrayInputStream(data),executor);
        for(var mode:Dither.Mode.values())for(var color:Dither.ColorMode.values())for(var fit:Dither.Fit.values()){
            var options=options(mode,color,fit);var request=new DitherImagePipeline.Request(19,13,options,.123);
            pipeline.request(request);executor.run();var result=pipeline.poll();assertNull(result.failure());
            int[] expected=Dither.apply(legacyFit(sourceImage(),19,13,fit),19,13,options,.123);
            for(int i=0;i<expected.length;i++){int p=expected[i];expected[i]=p&0xff00ff00|(p>>16&255)|(p&255)<<16;}
            assertArrayEquals(expected,result.pixels(),mode+"/"+color+"/"+fit);
        }
        pipeline.close();
    }

    @Test void animatedFramesReuseDecodeAndCachedFitWithoutMutatingPublishedPixels() throws Exception {
        var executor=new Manual();var opens=new AtomicInteger();byte[] data=png();
        var pipeline=new DitherImagePipeline(()->{opens.incrementAndGet();return new ByteArrayInputStream(data);},executor);
        var options=options(Dither.Mode.NOISE,Dither.ColorMode.GRAYSCALE,Dither.Fit.COVER);
        pipeline.request(new DitherImagePipeline.Request(40,20,options,0));executor.run();var first=pipeline.poll();var saved=first.pixels().clone();
        pipeline.request(new DitherImagePipeline.Request(40,20,options,1));executor.run();var second=pipeline.poll();
        assertEquals(1,opens.get());assertNotSame(first.pixels(),second.pixels());assertArrayEquals(saved,first.pixels());pipeline.close();
    }

    @Test void failureIsDeliveredOnceUntilRequestChanges() {
        var executor=new Manual();var opens=new AtomicInteger();
        var pipeline=new DitherImagePipeline(()->{opens.incrementAndGet();throw new IOException("missing");},executor);
        pipeline.request(request(20,12));executor.run();assertInstanceOf(IOException.class,pipeline.poll().failure());
        pipeline.request(request(20,12));assertFalse(pipeline.pending());assertEquals(1,opens.get());pipeline.close();
    }

    @Test void revertingAfterDiscardAndQueueRejectionStillDeliversRequestedFrame() throws Exception {
        var executor=new Manual();byte[] data=png();var pipeline=new DitherImagePipeline(()->new ByteArrayInputStream(data),executor);
        var a=request(20,12);pipeline.request(a);executor.run(); // A is ready, but not consumed.
        executor.reject=true;pipeline.request(request(21,12));pipeline.request(a);
        assertTrue(pipeline.pending());assertNull(pipeline.poll());
        executor.reject=false;pipeline.request(a);executor.run();assertEquals(a,pipeline.poll().request());pipeline.close();
    }

    @Test void revertingAfterConsumerCancelledStagingStillRecomputesFrame() throws Exception {
        var executor=new Manual();byte[] data=png();var pipeline=new DitherImagePipeline(()->new ByteArrayInputStream(data),executor);
        var a=request(20,12);pipeline.request(a);executor.run();assertNotNull(pipeline.poll());
        pipeline.request(request(21,12));pipeline.request(a);executor.run();assertEquals(a,pipeline.poll().request());pipeline.close();
    }

    @Test void invalidRequestsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->request(0,12));
        assertThrows(IllegalArgumentException.class,()->request(2049,12));
        assertThrows(IllegalArgumentException.class,()->new DitherImagePipeline.Request(1,1,Dither.Options.DEMO,Double.NaN));
    }

    @Test void uploadBudgetCoversEveryRowWithoutExceeding256KiB() {
        for(int width:new int[]{1,17,768,1920,2048})for(int height:new int[]{1,260,1080,2048}){
            int row=0,calls=0;
            while(row<height){int rows=DitherUploadBudget.rows(width,height,row);assertTrue(rows>0);
                assertTrue(rows*width<=DitherUploadBudget.PIXELS_PER_DRAW);row+=rows;calls++;}
            assertEquals(height,row);assertTrue(calls<=64);
        }
        assertThrows(IllegalArgumentException.class,()->DitherUploadBudget.rows(2049,20,0));
    }

    private static int[] legacyFit(BufferedImage image,int w,int h,Dither.Fit fit){
        var fitted=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);var g=fitted.createGraphics();
        try{g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double scale=switch(fit){case COVER->Math.max(w/(double)image.getWidth(),h/(double)image.getHeight());case CONTAIN->Math.min(w/(double)image.getWidth(),h/(double)image.getHeight());default->1;};
            int dw=fit==Dither.Fit.FILL?w:(int)Math.ceil(image.getWidth()*scale),dh=fit==Dither.Fit.FILL?h:(int)Math.ceil(image.getHeight()*scale);
            int dx=fit==Dither.Fit.FILL?0:(int)Math.floor((w-dw)/2d),dy=fit==Dither.Fit.FILL?0:(int)Math.floor((h-dh)/2d);
            g.drawImage(image,dx,dy,dw,dh,null);
        }finally{g.dispose();}
        return fitted.getRGB(0,0,w,h,null,0,w);
    }
}
