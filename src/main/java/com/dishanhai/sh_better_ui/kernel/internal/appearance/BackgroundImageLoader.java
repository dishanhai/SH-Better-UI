// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.appearance;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;

/** CPU decoding; reject oversized images before allocating the raster. No native resources leave this class. */
public final class BackgroundImageLoader {
    public static final int MAX_BYTES = 32 * 1024 * 1024;
    public static final long MAX_PIXELS = 16 * 1024 * 1024;
    public record Pixels(int width, int height, int[] argb) {}
    private BackgroundImageLoader() {}

    public static Pixels read(InputStream source) throws IOException {
        byte[] bytes = source.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw new IOException("Background image exceeds 32 MiB");
        try (var input = ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported background image");
            var reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 8192 || height > 8192 || (long) width * height > MAX_PIXELS) {
                    throw new IOException("Background image dimensions exceed the limit");
                }
                var image = reader.read(0);
                return new Pixels(width, height, image.getRGB(0, 0, width, height, null, 0, width));
            } finally {
                reader.dispose();
            }
        }
    }
}
