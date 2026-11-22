// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.client.gui.display.BuiltInSvgIcons;
import com.dishanhai.sh_better_ui.kernel.internal.NativePort;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Resource-pack-only media owner. CPU jobs never access textures; all GPU changes occur at frame Pre. */
public final class NativeRichMedia implements AutoCloseable {
    private static final int MOUNT_PIXELS=4*1024*1024, CLIENT_PIXELS=16*1024*1024, SOURCES=32;
    private static final List<NativeRichMedia> OWNERS=new ArrayList<>();
    private static final AtomicLong IDS=new AtomicLong(), EPOCH=new AtomicLong();
    private static int clientPixels;
    public record Region(Identifier texture,int width,int height,float u0,float u1,float v0,float v1) {}
    interface Backend {
        Object resources();
        RichMediaDecoder.Sheet decode(Identifier id,Object resources) throws IOException;
        Identifier upload(RichMediaDecoder.Sheet sheet);
        void release(Identifier id);
    }
    private static final class Decoded implements AutoCloseable {
        final RichMediaDecoder.Sheet sheet;
        Decoded(RichMediaDecoder.Sheet sheet){this.sheet=sheet;}
        public void close(){sheet.pixels().flush();}
    }
    private static final class Entry {
        RichMediaJobs.Job<Decoded> job;
        Identifier texture;
        List<RichMediaDecoder.Frame> frames;
        int width,height,sheetWidth,sheetHeight,area;
        long plays,duration,started;
        boolean failed;
    }
    private final Backend backend;
    private final Map<Identifier,Entry> entries=new LinkedHashMap<>();
    private final Set<Identifier> visible=new HashSet<>();
    private final List<Entry> retiring=new ArrayList<>();
    private Object resources;
    private long epoch=EPOCH.get();
    private int pixels;
    private boolean closed;
    public NativeRichMedia(){this(new Backend(){
        public Object resources(){return Minecraft.getInstance().getResourceManager();}
        public RichMediaDecoder.Sheet decode(Identifier id,Object manager)throws IOException{return decodePack(id,(ResourceManager)manager);}
        public Identifier upload(RichMediaDecoder.Sheet sheet){
            int maximum=NativePort.maxRgbaTextureSize();
            if(sheet.pixels().getWidth()>maximum||sheet.pixels().getHeight()>maximum)
                throw new IllegalArgumentException("Inline image exceeds device texture limits");
            var image=new NativeImage(sheet.pixels().getWidth(),sheet.pixels().getHeight(),false);
            DynamicTexture texture=null;
            try {
                for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)image.setPixel(x,y,sheet.pixels().getRGB(x,y));
                texture=new DynamicTexture(()->"SHBUI inline media",image);
                var id=Identifier.fromNamespaceAndPath("sh_better_ui","rich_media/native_"+IDS.incrementAndGet());
                Minecraft.getInstance().getTextureManager().register(id,texture);return id;
            }catch(RuntimeException failure){if(texture!=null)texture.close();else image.close();throw failure;}
        }
        public void release(Identifier id){Minecraft.getInstance().getTextureManager().release(id);}
    });}
    NativeRichMedia(Backend backend){this.backend=backend;synchronized(OWNERS){OWNERS.add(this);}}
    public static boolean supports(Identifier id){
        String path=id.getPath();return path.startsWith("textures/")&&!path.contains("..")
                &&(path.endsWith(".png")||path.endsWith(".gif")||path.endsWith(".apng"));
    }
    public void beginVisibleFrame(){visible.clear();syncResources();}
    public void request(Identifier id){
        if(closed||!supports(id))return;
        syncResources();
        if(entries.containsKey(id)){visible.add(id);return;}
        if(entries.size()>=SOURCES)return;
        visible.add(id);entries.put(id,new Entry());
    }
    public Region region(Identifier id,long nowMs,boolean reducedMotion){
        if(closed||epoch!=EPOCH.get())return null;
        var entry=entries.get(id);if(entry==null||entry.texture==null)return null;
        var frame=RichMediaDecoder.frameAt(entry.frames,Math.max(0,nowMs-entry.started),entry.duration,entry.plays,reducedMotion);
        return new Region(entry.texture,entry.width,entry.height,(float)frame.x()/entry.sheetWidth,
                (float)(frame.x()+entry.width)/entry.sheetWidth,(float)frame.y()/entry.sheetHeight,
                (float)(frame.y()+entry.height)/entry.sheetHeight);
    }
    public void endVisibleFrame(){
        var iterator=entries.entrySet().iterator();while(iterator.hasNext()){var entry=iterator.next();if(!visible.contains(entry.getKey())){retire(entry.getValue());iterator.remove();}}
    }
    /** Explicit retry/reload without losing the document or starting hidden media. */
    public void refresh(){entries.values().forEach(this::retire);entries.clear();visible.clear();epoch=EPOCH.get();resources=backend.resources();}
    private void syncResources(){if(!closed&&(epoch!=EPOCH.get()||resources!=backend.resources()))refresh();}
    private void retire(Entry entry){if(entry.job!=null){entry.job.cancel();entry.job=null;}if(entry.texture!=null)retiring.add(entry);}
    @Override public void close(){if(closed)return;closed=true;entries.values().forEach(this::retire);entries.clear();visible.clear();}
    /** Reload callbacks can run off-thread: invalidate atomically; dispose at next render Pre. */
    public static void resourcesReloaded(){EPOCH.incrementAndGet();}
    public static void prepareAll(long nowMs){
        RenderSystem.assertOnRenderThread();
        synchronized(OWNERS){for(var owner:OWNERS)owner.prepare(nowMs);OWNERS.removeIf(owner->owner.closed&&owner.retiring.isEmpty());}
    }
    public static void closeAll(){
        RenderSystem.assertOnRenderThread();
        synchronized(OWNERS){for(var owner:OWNERS){owner.close();owner.prepare(0);}OWNERS.clear();}
    }
    void prepare(long nowMs){
        syncResources();
        for(var entry:retiring){backend.release(entry.texture);pixels-=entry.area;clientPixels-=entry.area;entry.texture=null;}
        retiring.clear();if(closed)return;
        int uploads=0;
        for(var binding:entries.entrySet()){
            var entry=binding.getValue();
            if(entry.failed||entry.texture!=null)continue;
            if(entry.job==null){
                Object captured=resources;Identifier id=binding.getKey();
                entry.job=RichMediaJobs.submit(()->new Decoded(backend.decode(id,captured)),()->{},false);
            }
            if(entry.job==null||!entry.job.ready()||uploads>=1)continue;
            try(var decoded=entry.job.take()){
                entry.job=null;
                if(decoded==null){entry.failed=true;continue;}
                var sheet=decoded.sheet;int area=Math.multiplyExact(sheet.pixels().getWidth(),sheet.pixels().getHeight());
                if(area>MOUNT_PIXELS-pixels||area>CLIENT_PIXELS-clientPixels){entry.failed=true;continue;}
                entry.texture=backend.upload(sheet);uploads++;
                entry.frames=sheet.frames();entry.width=sheet.frameWidth();entry.height=sheet.frameHeight();
                entry.sheetWidth=sheet.pixels().getWidth();entry.sheetHeight=sheet.pixels().getHeight();entry.area=area;
                entry.plays=sheet.plays();entry.duration=sheet.frames().stream().mapToLong(RichMediaDecoder.Frame::durationMs).sum();
                entry.started=nowMs;pixels+=area;clientPixels+=area;
            }catch(RuntimeException failed){entry.failed=true;}
        }
    }
    static RichMediaDecoder.Sheet decodePack(Identifier id,ResourceManager resources)throws IOException {
        if(!supports(id))throw new IOException("Only resource-pack textures are supported");
        var resource = resources.getResource(id).orElseThrow(() -> new IOException("Missing inline image"));
        String svgName = BuiltInSvgIcons.select(id.getNamespace(), id.getPath(), resource::open);
        if (svgName != null) {
            int side = 128;
            return new RichMediaDecoder.Sheet(BuiltInSvgIcons.render(svgName, side), side, side,
                    java.util.List.of(new RichMediaDecoder.Frame(0, 0, 0)), 1);
        }
        byte[] metadata=null;
        if(id.getPath().endsWith(".png")){
            var sidecar=resources.getResource(Identifier.parse(id+".mcmeta")).orElse(null);
            if(sidecar!=null&&(sidecar.source()==resource.source()||resources.listPacks().dropWhile(pack->pack!=resource.source()).anyMatch(pack->pack==sidecar.source())))
                try(var input=sidecar.open()){metadata=RichMediaDecoder.read(input,RichMediaPng.MAX_METADATA_BYTES);}
        }
        try(var input=resource.open()){return RichMediaDecoder.decode(RichMediaDecoder.read(input,RichMediaDecoder.MAX_BYTES),metadata);}
    }
}
