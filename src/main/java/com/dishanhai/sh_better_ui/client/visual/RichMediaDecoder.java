// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.AlphaComposite;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Bounded CPU decoding. Results own heap pixels and may be discarded without touching GL. */
final class RichMediaDecoder {
    static final int MAX_BYTES = 8 * 1024 * 1024;
    static final int MAX_SIDE = 4096;
    static final int MAX_PIXELS = 4 * 1024 * 1024;
    static final int MAX_FRAMES = 32;
    static final int MAX_DURATION = 5 * 60 * 1000;
    static final long PNG_SIGNATURE = 0x89504e470d0a1a0aL;

    record Frame(int x, int y, int durationMs) {}

    record Sheet(BufferedImage pixels, int frameWidth, int frameHeight, List<Frame> frames, long plays) {
        Sheet {
            frames = List.copyOf(frames);
            if (frames.stream().mapToLong(Frame::durationMs).sum() > MAX_DURATION) {
                throw new IllegalArgumentException("Inline image animation is too long");
            }
        }

        Frame frameAt(long elapsed, boolean reducedMotion) {
            return RichMediaDecoder.frameAt(frames, elapsed, frames.stream().mapToLong(Frame::durationMs).sum(),
                    plays, reducedMotion);
        }
    }

    private RichMediaDecoder() {}

    static Frame frameAt(List<Frame> frames, long elapsed, long duration, long plays, boolean reducedMotion) {
        if (reducedMotion || frames.size() == 1) return frames.get(0);
        if (plays != 0 && elapsed / duration >= plays) return frames.get(frames.size() - 1);
        long offset = elapsed % duration;
        for (var frame : frames) {
            if (offset < frame.durationMs()) return frame;
            offset -= frame.durationMs();
        }
        throw new IllegalStateException("Inline animation time is outside its duration");
    }

    static byte[] read(InputStream input, int limit) throws IOException {
        byte[] result = input.readNBytes(limit + 1);
        if (result.length > limit) throw new IOException("Inline image input exceeds its byte limit");
        return result;
    }

    static Sheet decode(byte[] bytes, byte[] metadata) throws IOException {
        if (bytes.length < 13 || bytes.length > MAX_BYTES) throw new IOException("Invalid inline image size");
        if (ByteBuffer.wrap(bytes).getLong() == PNG_SIGNATURE) {
            return RichMediaPng.decode(bytes, metadata);
        }
        if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8'
                && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a') {
            return gif(bytes);
        }
        throw new IOException("Unsupported inline image format");
    }

    static void checkSize(int width, int height, int frames) throws IOException {
        if (width <= 0 || height <= 0 || width > MAX_SIDE || height > MAX_SIDE
                || frames < 1 || frames > MAX_FRAMES || (long) width * height * frames > MAX_PIXELS) {
            throw new IOException("Inline image exceeds dimension or frame limits");
        }
    }

    static void checkRect(int x, int y, int width, int height, int canvasWidth, int canvasHeight) throws IOException {
        if (x < 0 || y < 0 || width <= 0 || height <= 0
                || (long) x + width > canvasWidth || (long) y + height > canvasHeight) {
            throw new IOException("Inline image frame is outside its canvas");
        }
    }

    static void checkInterrupted() throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Inline image request cancelled");
    }

    static ImageReader reader(String format) throws IOException {
        var readers = ImageIO.getImageReadersByFormatName(format);
        if (!readers.hasNext()) throw new IOException("Missing inline image decoder");
        return readers.next();
    }

    static BufferedImage readPng(byte[] bytes, int width, int height) throws IOException {
        checkInterrupted();
        var reader = reader("PNG");
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            reader.setInput(input, true, true);
            if (reader.getWidth(0) != width || reader.getHeight(0) != height) {
                throw new IOException("Inline PNG dimensions disagree with the header");
            }
            return reader.read(0);
        } finally {
            reader.dispose();
        }
    }

    private static Sheet gif(byte[] bytes) throws IOException {
        int width = unsignedShort(bytes, 6), height = unsignedShort(bytes, 8);
        checkSize(width, height, 1);
        int frames = inspectGif(bytes, width, height);
        checkSize(width, height, frames);
        var reader = reader("GIF");
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            reader.setInput(input);
            var canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            var atlas = new Atlas(width, height, frames);
            long plays = 1;
            for (int i = 0; i < frames; i++) {
                checkInterrupted();
                var metadata = reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
                var descriptor = child(metadata, "ImageDescriptor");
                var control = child(metadata, "GraphicControlExtension");
                int x = attribute(descriptor, "imageLeftPosition", 0);
                int y = attribute(descriptor, "imageTopPosition", 0);
                int w = reader.getWidth(i), h = reader.getHeight(i);
                checkRect(x, y, w, h, width, height);
                String disposal = attribute(control, "disposalMethod", "none");
                int[] restore = "restoreToPrevious".equals(disposal)
                        ? canvas.getRGB(x, y, w, h, null, 0, w) : null;
                composite(canvas, reader.read(i), x, y, false);
                atlas.add(canvas, Math.max(16, attribute(control, "delayTime", 10) * 10));
                if (restore != null) canvas.setRGB(x, y, w, h, restore, 0, w);
                else if ("restoreToBackgroundColor".equals(disposal)) clear(canvas, x, y, w, h);
                if (i == 0) plays = gifPlays(metadata);
            }
            return atlas.finish(plays);
        } finally {
            reader.dispose();
        }
    }

    private static int inspectGif(byte[] bytes, int width, int height) throws IOException {
        var input = ByteBuffer.wrap(bytes);
        input.position(13);
        skipGifPalette(input, Byte.toUnsignedInt(bytes[10]));
        int frames = 0;
        while (input.hasRemaining()) {
            checkInterrupted();
            int type = Byte.toUnsignedInt(input.get());
            if (type == 0x3b && !input.hasRemaining()) return frames;
            if (type == 0x21) {
                skipGifBytes(input, 1); // Extension label.
            } else if (type == 0x2c && input.remaining() >= 10 && ++frames <= MAX_FRAMES) {
                int offset = input.position();
                checkRect(unsignedShort(bytes, offset), unsignedShort(bytes, offset + 2),
                        unsignedShort(bytes, offset + 4), unsignedShort(bytes, offset + 6), width, height);
                input.position(offset + 9);
                skipGifPalette(input, Byte.toUnsignedInt(bytes[offset + 8]));
                skipGifBytes(input, 1); // LZW minimum code size.
            } else {
                break;
            }
            skipGifBlocks(input);
        }
        throw new IOException("Invalid inline GIF structure");
    }

    private static void skipGifPalette(ByteBuffer input, int flags) throws IOException {
        if ((flags & 0x80) != 0) skipGifBytes(input, 3 * (2 << (flags & 7)));
    }

    private static void skipGifBlocks(ByteBuffer input) throws IOException {
        while (input.hasRemaining()) {
            int size = Byte.toUnsignedInt(input.get());
            if (size == 0) return;
            skipGifBytes(input, size);
        }
        throw new IOException("Truncated inline GIF block");
    }

    private static void skipGifBytes(ByteBuffer input, int size) throws IOException {
        if (input.remaining() < size) throw new IOException("Truncated inline GIF data");
        input.position(input.position() + size);
    }

    private static long gifPlays(Node metadata) {
        var extensions = child(metadata, "ApplicationExtensions");
        if (extensions == null) return 1;
        for (var node = extensions.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof javax.imageio.metadata.IIOMetadataNode data
                    && "NETSCAPE".equals(attribute(node, "applicationID", ""))
                    && "2.0".equals(attribute(node, "authenticationCode", ""))
                    && data.getUserObject() instanceof byte[] raw && raw.length == 3 && raw[0] == 1) {
                int repeats = unsignedShort(raw, 1);
                return repeats == 0 ? 0 : repeats + 1L;
            }
        }
        return 1;
    }

    private static int unsignedShort(byte[] bytes, int offset) {
        return Byte.toUnsignedInt(bytes[offset]) | Byte.toUnsignedInt(bytes[offset + 1]) << 8;
    }

    private static Node child(Node parent, String name) {
        for (var node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (name.equals(node.getNodeName())) return node;
        }
        return null;
    }

    private static int attribute(Node node, String key, int fallback) {
        return Integer.parseInt(attribute(node, key, Integer.toString(fallback)));
    }

    private static String attribute(Node node, String key, String fallback) {
        var value = node == null ? null : node.getAttributes().getNamedItem(key);
        return value == null ? fallback : value.getNodeValue();
    }

    static void composite(BufferedImage canvas, BufferedImage frame, int x, int y, boolean source) {
        var graphics = canvas.createGraphics();
        try {
            graphics.setComposite(source ? AlphaComposite.Src : AlphaComposite.SrcOver);
            graphics.drawImage(frame, x, y, null);
        } finally {
            graphics.dispose();
        }
    }

    static void clear(BufferedImage canvas, int x, int y, int width, int height) {
        var graphics = canvas.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Clear);
            graphics.fillRect(x, y, width, height);
        } finally {
            graphics.dispose();
        }
    }

    static final class Atlas {
        private final int width;
        private final int height;
        private final int columns;
        private final BufferedImage pixels;
        private final List<Frame> frames = new ArrayList<>();

        Atlas(int width, int height, int count) throws IOException {
            checkSize(width, height, count);
            int bestColumns = 0, bestArea = Integer.MAX_VALUE, bestSide = Integer.MAX_VALUE;
            for (int c = 1; c <= count && width * c <= MAX_SIDE; c++) {
                int sheetWidth = width * c;
                int sheetHeight = height * ((count + c - 1) / c);
                int area = sheetWidth * sheetHeight;
                int side = Math.max(sheetWidth, sheetHeight);
                if (sheetHeight <= MAX_SIDE && area <= MAX_PIXELS
                        && (area < bestArea || area == bestArea && side < bestSide)) {
                    bestColumns = c;
                    bestArea = area;
                    bestSide = side;
                }
            }
            if (bestColumns == 0) throw new IOException("Inline animation exceeds atlas limits");
            this.width = width;
            this.height = height;
            this.columns = bestColumns;
            pixels = new BufferedImage(width * columns, height * ((count + columns - 1) / columns),
                    BufferedImage.TYPE_INT_ARGB);
        }

        void add(BufferedImage frame, int durationMs) {
            int index = frames.size();
            int x = index % columns * width, y = index / columns * height;
            composite(pixels, frame, x, y, true);
            frames.add(new Frame(x, y, durationMs));
        }

        Sheet finish(long plays) { return new Sheet(pixels, width, height, frames, plays); }
    }
}
