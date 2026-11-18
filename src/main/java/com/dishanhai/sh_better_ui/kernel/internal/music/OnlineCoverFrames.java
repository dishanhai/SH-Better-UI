// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.client.music.MusicSnapshot;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;

/** Nonblocking bridge cover cache. Existing public-CDN transport never receives account credentials. */
final class OnlineCoverFrames implements AutoCloseable {
    interface Loader { byte[] load(String address) throws IOException; }
    private final Loader loader;
    private final ScheduledThreadPoolExecutor worker = new ScheduledThreadPoolExecutor(1, job -> {
        var thread = new Thread(job,"shb-online-frame-cover");thread.setDaemon(true);return thread;
    });
    private final LinkedHashMap<String,MusicSnapshot.Artwork> cache = new LinkedHashMap<>(8,.75f,true);
    private String requested;
    private Future<?> task;
    private long generation;
    private boolean closed;
    OnlineCoverFrames() { this(OnlineArtwork::download); }
    OnlineCoverFrames(Loader loader) { this.loader=loader;worker.setRemoveOnCancelPolicy(true); }
    synchronized MusicSnapshot.Artwork artwork(OnlineTrack track) {
        if (closed) return null;
        String address=track==null?"":track.artworkUrl();
        if (!Objects.equals(address,requested)) {
            generation++;requested=address;
            if(task!=null)task.cancel(true);
            task=null;
            if(!address.isBlank()&&!cache.containsKey(address)) {
                long ticket=generation;
                task=worker.submit(()->load(ticket,address));
            }
        }
        return cache.get(address);
    }
    private void load(long ticket,String address) {
        try {
            var artwork=thumbnail(loader.load(address));
            synchronized(this) {
                if(closed||generation!=ticket||Thread.currentThread().isInterrupted())return;
                cache.put(address,artwork);
                while(cache.size()>8)cache.remove(cache.keySet().iterator().next());
            }
        } catch(IOException ignored) { /* Missing or invalid cover is represented by absent artwork. */ }
    }
    /** Download is normalized to bounded PNG first; resize to the shared 240px / 256KiB artwork contract. */
    static MusicSnapshot.Artwork thumbnail(byte[] normalizedPng) throws IOException {
        if(normalizedPng.length==0||normalizedPng.length>OnlineArtwork.MAX_BYTES)throw new IOException("Invalid normalized artwork size");
        try(var input=new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(normalizedPng))) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw new IOException("Invalid normalized artwork");
            var reader=readers.next();
            try {
                reader.setInput(input,true,true);
                if(!reader.getFormatName().equalsIgnoreCase("png")||reader.getWidth(0)>512||reader.getHeight(0)>512)
                    throw new IOException("Invalid normalized artwork dimensions");
                var source=reader.read(0);
                double scale=Math.min(1,240d/Math.max(source.getWidth(),source.getHeight()));
                var image=new BufferedImage(Math.max(1,(int)(source.getWidth()*scale)),Math.max(1,(int)(source.getHeight()*scale)),BufferedImage.TYPE_INT_ARGB);
                var draw=image.createGraphics();
                try {draw.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);draw.drawImage(source,0,0,image.getWidth(),image.getHeight(),null);}
                finally {draw.dispose();source.flush();}
                try(var output=new ByteArrayOutputStream()) {
                    if(!ImageIO.write(image,"png",output))throw new IOException("PNG encoder unavailable");
                    byte[] png=output.toByteArray();
                    if(png.length>MusicSnapshot.Artwork.MAX_BYTES)throw new IOException("Normalized cover exceeds artwork limit");
                    return new MusicSnapshot.Artwork(png);
                } finally {image.flush();}
            } finally {reader.dispose();}
        }
    }
    @Override public synchronized void close() {
        closed=true;generation++;if(task!=null)task.cancel(true);task=null;cache.clear();worker.shutdownNow();
    }
}
