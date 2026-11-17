// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.music.MusicSnapshot;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** One bounded cover for a unified HUD frame. CPU decode is asynchronous; the host owns GPU release. */
final class NativeMusicArtwork implements AutoCloseable {
    private static final AtomicLong IDS=new AtomicLong();
    private final Identifier textureId=Identifier.parse("sh_better_ui:music/native_activity_"+IDS.incrementAndGet());
    private final ScheduledThreadPoolExecutor decoder=new ScheduledThreadPoolExecutor(1,job->{
        var thread=new Thread(job,"shb-native-island-cover");thread.setDaemon(true);return thread;
    });
    record Texture(Identifier id,int width,int height) {}
    private int width,height;
    private String requested;
    private UiId currentIcon;
    private NativeImage pending;
    private boolean registered,closed;
    private Future<?> task;
    private long generation;
    NativeMusicArtwork(){decoder.setRemoveOnCancelPolicy(true);}
    static UiId icon(MusicSnapshot.Artwork art){return art==null?null:UiId.parse("sh_better_ui:music/activity_cover/"+art.id());}
    static boolean isCover(UiId id){return id!=null&&id.namespace().equals("sh_better_ui")&&id.path().startsWith("music/activity_cover/");}

    /** Called exactly once before extracting any content layers; never releases an already-extracted layer's texture. */
    synchronized void beginFrame(MusicSnapshot.Artwork artwork) {
        RenderSystem.assertOnRenderThread();
        if(closed)return;
        String next=artwork==null?null:artwork.id();
        if(!Objects.equals(requested,next)) {
            invalidate();requested=next;
            if(artwork!=null){long ticket=generation;task=decoder.submit(()->decode(ticket,artwork));}
        }
        if(pending!=null) {
            var image=pending;pending=null;
            DynamicTexture texture=null;
            try {
                width=image.getWidth();height=image.getHeight();
                texture=new DynamicTexture(()->"SHBUI music activity cover",image);
                Minecraft.getInstance().getTextureManager().register(textureId,texture);
                registered=true;currentIcon=icon(artwork);
            } catch(RuntimeException failure) {
                if(texture!=null)texture.close();else image.close();
                currentIcon=null;registered=false;
            }
        }
    }
    synchronized Texture texture(UiId id){return registered&&Objects.equals(id,currentIcon)?new Texture(textureId,width,height):null;}
    private void decode(long ticket,MusicSnapshot.Artwork artwork) {
        NativeImage image=null;
        try {
            image=read(artwork);
            synchronized(this) {
                if(!closed&&ticket==generation&&!Thread.currentThread().isInterrupted()){pending=image;image=null;}
            }
        } catch(IOException ignored) { /* Keep the text-only surface if the provider's image is invalid. */ }
        finally {if(image!=null)image.close();}
    }
    static NativeImage read(MusicSnapshot.Artwork artwork) throws IOException {
        byte[] png=artwork.png();
        if(png.length<24)throw new IOException("Incomplete PNG cover");
        var header=ByteBuffer.wrap(png);
        if(header.getLong()!=0x89504e470d0a1a0aL||header.getInt()!=13||header.getInt()!=0x49484452)
            throw new IOException("Only PNG artwork is supported");
        int width=header.getInt(),height=header.getInt();
        if(width<1||height<1||width>240||height>240)throw new IOException("Artwork dimensions exceed limit");
        return NativeImage.read(new ByteArrayInputStream(png));
    }
    synchronized void invalidate() {
        generation++;requested=null;currentIcon=null;
        if(task!=null)task.cancel(true);task=null;
        if(pending!=null)pending.close();pending=null;
        if(registered){RenderSystem.assertOnRenderThread();Minecraft.getInstance().getTextureManager().release(textureId);registered=false;}
    }
    @Override public synchronized void close(){invalidate();closed=true;decoder.shutdownNow();}
}
