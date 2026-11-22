// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Public-provider cover transport, deliberately separate from authenticated music requests. */
public final class OnlineArtwork {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    private OnlineArtwork() {}

    /** Legacy provider metadata sometimes uses HTTP; upgrade only known CDN hosts to HTTPS. */
    public static URI validated(String value) throws IOException {
        return validated(value, false);
    }

    public static URI validatedAvatar(String value) throws IOException {
        return validated(value, true);
    }

    private static URI validated(String value, boolean avatar) throws IOException {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            boolean known = host != null && (host.equals("y.gtimg.cn") || host.matches("p[1-9][0-9]?\\.music\\.126\\.net"));
            if (avatar && host != null) {
                known |= host.matches("(thirdqq|q[0-9]?)\\.qlogo\\.cn") || host.matches("pic[0-9]+\\.y\\.qq\\.com")
                        || host.equals("y.qq.com") || host.matches("p[0-9]{1,2}\\.music\\.126\\.net")
                        || host.equals("imge.kugou.com") || host.equals("img.kugou.com");
            }
            if (!known || uri.getRawUserInfo() != null || uri.getPort() != -1 || uri.getRawFragment() != null
                    || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || value.length() > 2048)
                throw new IOException("Unsupported artwork address");
            return URI.create("https" + value.substring(value.indexOf(':')));
        } catch (IllegalArgumentException e) { throw new IOException("Invalid artwork address", e); }
    }

    private static final java.net.http.HttpClient HTTP = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(4)).followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();

    public static byte[] download(String value) throws IOException {
        return download(value, false);
    }

    public static byte[] downloadAvatar(String value) throws IOException {
        return download(value, true);
    }

    private static byte[] download(String value, boolean avatar) throws IOException {
        URI uri = validated(value, avatar);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
        for (int redirect = 0; redirect <= 3; redirect++) {
            if (Thread.currentThread().isInterrupted() || System.nanoTime() > deadline) throw new InterruptedIOException();
            // This dedicated client has no cookie handler or authenticator and never inherits music credentials.
            var request = java.net.http.HttpRequest.newBuilder(uri).timeout(java.time.Duration.ofSeconds(8))
                    .header("Accept", "image/png,image/jpeg").GET().build();
            var future = HTTP.sendAsync(request, info -> new BoundedBody());
            java.net.http.HttpResponse<byte[]> response;
            try {
                response = future.get(Math.max(1, deadline - System.nanoTime()), java.util.concurrent.TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                future.cancel(true); Thread.currentThread().interrupt(); throw new InterruptedIOException();
            } catch (java.util.concurrent.TimeoutException | java.util.concurrent.ExecutionException e) {
                future.cancel(true); throw new IOException("Artwork unavailable", e);
            }
            int code = response.statusCode();
            if (code >= 300 && code < 400) {
                String location = response.headers().firstValue("Location").orElse(null);
                if (location == null) throw new IOException("Missing artwork redirect");
                uri = validated(uri.resolve(location).toString(), avatar);
                continue;
            }
            if (code != 200) throw new IOException("Artwork unavailable");
            return normalize(response.body(), avatar ? 64 : 512);
        }
        throw new IOException("Too many artwork redirects");
    }

    /** Limits bytes while receiving, not after an unbounded response has already been allocated. */
    static final class BoundedBody implements java.net.http.HttpResponse.BodySubscriber<byte[]> {
        private final java.util.concurrent.CompletableFuture<byte[]> result = new java.util.concurrent.CompletableFuture<>();
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private java.util.concurrent.Flow.Subscription subscription;
        public java.util.concurrent.CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
            this.subscription = subscription; subscription.request(1);
        }
        public void onNext(java.util.List<java.nio.ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (buffer.remaining() > MAX_BYTES - output.size()) {
                    subscription.cancel(); result.completeExceptionally(new IOException("Artwork too large")); return;
                }
                byte[] data = new byte[buffer.remaining()]; buffer.get(data); output.writeBytes(data);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { result.completeExceptionally(error); }
        public void onComplete() { result.complete(output.toByteArray()); }
    }

    /** Inspect dimensions before allocating pixels, then make a bounded PNG for the GPU decoder. */
    public static byte[] normalize(byte[] bytes) throws IOException {
        return normalize(bytes, 512);
    }

    private static byte[] normalize(byte[] bytes, int maxSide) throws IOException {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IOException("Invalid artwork size");
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported artwork format");
            var reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg")) throw new IOException("Unsupported artwork format");
                reader.setInput(input, true, true);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w < 1 || h < 1 || w > 2048 || h > 2048) throw new IOException("Artwork dimensions too large");
                var source = reader.read(0);
                double scale = Math.min(1d, (double) maxSide / Math.max(w, h));
                var image = new BufferedImage(Math.max(1, (int)(w * scale)), Math.max(1, (int)(h * scale)), BufferedImage.TYPE_INT_ARGB);
                var graphics = image.createGraphics();
                try { graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(source, 0, 0, image.getWidth(), image.getHeight(), null);
                } finally { graphics.dispose(); source.flush(); }
                try (var output = new ByteArrayOutputStream()) {
                    if (!ImageIO.write(image, "png", output)) throw new IOException("PNG encoder unavailable");
                    return output.toByteArray();
                } finally { image.flush(); }
            } finally { reader.dispose(); }
        }
    }
}
