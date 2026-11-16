package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.kernel.api.effect.Dither;
import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/** Manual measurement, deliberately not a timing assertion in the unit-test suite. No OpenGL calls. */
public final class DitherPipelineBenchmark {
    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[0]);
        if (args.length > 1 && args[1].equals("warmup")) {
            long warm = System.nanoTime(); DitherImagePipeline.warmUp();
            System.out.printf("Resource-stage warmup dispatch %.3f ms (no image decode)%n", ms(warm));
            Thread.sleep(200); // Benchmark isolation only. Production does not wait.
        }
        long start=System.nanoTime(); BufferedImage image=ImageIO.read(source.toFile());
        System.out.printf("JVM %s; source %dx%d; cold ImageIO decode %.3f ms%n",System.getProperty("java.version"),image.getWidth(),image.getHeight(),ms(start));
        for(int[] size:new int[][]{{768,260},{1280,720},{1920,1080},{2048,2048}}){
            int width=size[0],height=size[1];
            for(int iteration=0;iteration<3;iteration++){
                start=System.nanoTime();int[] input=legacyFit(image,width,height);double fit=ms(start);
                start=System.nanoTime();int[] output=Dither.apply(input,width,height,Dither.Options.DEMO,0);double dither=ms(start);
                start=System.nanoTime();long checksum=0;for(int p:output)checksum+=p&0xff00ff00|(p>>16&255)|(p&255)<<16;double pack=ms(start);
                System.out.printf("legacy %dx%d #%d fit+getRGB %.3f dither %.3f pack %.3f total %.3f ms checksum %d%n",width,height,iteration,fit,dither,pack,fit+dither+pack,checksum);
            }
            try(var pipeline=new DitherImagePipeline(()->java.nio.file.Files.newInputStream(source))){
                for(int frame=0;frame<3;frame++){
                    var request=new DitherImagePipeline.Request(width,height,Dither.Options.DEMO,frame*.02);
                    start=System.nanoTime();pipeline.request(request);double dispatch=ms(start);long began=System.nanoTime();
                    DitherImagePipeline.Result result;
                    while((result=pipeline.poll())==null){if(ms(began)>10000)throw new IllegalStateException("Timed out");Thread.sleep(1);}
                    if(result.failure()!=null)throw result.failure();
                    int row=0,calls=0;while(row<height){row+=DitherUploadBudget.rows(width,height,row);calls++;}
                    System.out.printf("async %dx%d #%d caller request %.3f ms, wall wait %.3f ms, pixels %d, upload draws %d, per-draw <=%d pixels%n",
                            width,height,frame,dispatch,ms(began),result.pixels().length,calls,DitherUploadBudget.PIXELS_PER_DRAW);
                }
            }
        }
        System.out.println("CPU-only: does not measure NativeImage calls, allocation, glTexImage/subimage, FPS or Minecraft frame latency.");
    }
    private static double ms(long start){return(System.nanoTime()-start)/1e6;}
    private static int[] legacyFit(BufferedImage image,int width,int height){
        var fitted=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);var g=fitted.createGraphics();
        try{g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double scale=Math.max(width/(double)image.getWidth(),height/(double)image.getHeight());
            int dw=(int)Math.ceil(image.getWidth()*scale),dh=(int)Math.ceil(image.getHeight()*scale);
            g.drawImage(image,(int)Math.floor((width-dw)/2d),(int)Math.floor((height-dh)/2d),dw,dh,null);
        }finally{g.dispose();}
        return fitted.getRGB(0,0,width,height,null,0,width);
    }
}
