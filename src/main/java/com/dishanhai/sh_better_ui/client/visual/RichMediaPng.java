// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/** Validates PNG structure and converts each APNG rectangle to a bounded PNG before decoding. */
final class RichMediaPng {
    static final int MAX_METADATA_BYTES = 16 * 1024;
    private static final int IHDR = 0x49484452;
    private static final int PLTE = 0x504c5445;
    private static final int TRNS = 0x74524e53;
    private static final int IDAT = 0x49444154;
    private static final int IEND = 0x49454e44;
    private static final int ACTL = 0x6163544c;
    private static final int FCTL = 0x6663544c;
    private static final int FDAT = 0x66644154;
    private static final int MAX_CHUNKS = 4096;

    private record Chunk(int type, int offset, int length) {}
    private record Control(int width, int height, int x, int y, int delayMs, int dispose, int blend,
                           List<Chunk> data) {}
    private record Timing(int index, int ticks) {}

    private RichMediaPng() {}

    static RichMediaDecoder.Sheet decode(byte[] bytes, byte[] metadata) throws IOException {
        var input = ByteBuffer.wrap(bytes);
        input.position(Long.BYTES);
        var header = nextChunk(input);
        if (header.type() != IHDR || header.length() != 13) throw invalid();
        int width = input.getInt(header.offset()), height = input.getInt(header.offset() + 4);
        RichMediaDecoder.checkSize(width, height, 1);
        byte[] headerBytes = new byte[13];
        System.arraycopy(bytes, header.offset(), headerBytes, 0, headerBytes.length);

        var palette = new ArrayList<Chunk>(2);
        var defaultData = new ArrayList<Chunk>();
        var frames = new ArrayList<Control>();
        int declaredCount = 0, sequence = 0;
        long plays = 0;
        boolean ended = false, defaultIsFrame = false, dataClosed = false;
        for (int count = 1; input.hasRemaining() && count < MAX_CHUNKS && !ended; count++) {
            var chunk = nextChunk(input);
            int offset = chunk.offset();
            if (chunk.type() != IDAT && !defaultData.isEmpty()) dataClosed = true;
            switch (chunk.type()) {
                case PLTE, TRNS -> {
                    if (!defaultData.isEmpty() || chunk.length() > 768
                            || palette.stream().anyMatch(previous -> previous.type() == chunk.type())) throw invalid();
                    palette.add(chunk);
                }
                case ACTL -> {
                    if (declaredCount != 0 || !defaultData.isEmpty() || chunk.length() != 8) throw invalid();
                    declaredCount = input.getInt(offset);
                    plays = Integer.toUnsignedLong(input.getInt(offset + 4));
                    RichMediaDecoder.checkSize(width, height, declaredCount);
                }
                case FCTL -> {
                    if (declaredCount == 0 || frames.size() >= declaredCount || chunk.length() != 26
                            || input.getInt(offset) != sequence++) throw invalid();
                    var control = control(input, offset, width, height);
                    if (defaultData.isEmpty()) {
                        if (!frames.isEmpty() || control.width() != width || control.height() != height) throw invalid();
                        defaultIsFrame = true;
                    }
                    frames.add(control);
                }
                case FDAT -> {
                    if (defaultData.isEmpty() || frames.size() <= (defaultIsFrame ? 1 : 0) || chunk.length() < 4
                            || input.getInt(offset) != sequence++) throw invalid();
                    frames.get(frames.size() - 1).data().add(new Chunk(IDAT, offset + 4, chunk.length() - 4));
                }
                case IDAT -> {
                    if (dataClosed) throw invalid();
                    defaultData.add(chunk);
                }
                case IEND -> {
                    if (chunk.length() != 0) throw invalid();
                    ended = true;
                }
                default -> {
                    // Strip optional metadata such as compressed text and color profiles before ImageIO.
                    if ((chunk.type() & 0x20000000) == 0) throw invalid();
                }
            }
        }
        if (!ended || input.hasRemaining() || defaultData.isEmpty() || frames.size() != declaredCount) throw invalid();
        if (declaredCount == 0) {
            var image = image(bytes, headerBytes, palette, defaultData, width, height);
            return metadata == null ? still(image) : spriteSheet(image, metadata);
        }
        if (defaultIsFrame) frames.get(0).data().addAll(defaultData);
        return animated(bytes, headerBytes, palette, frames, width, height, plays);
    }

    private static Chunk nextChunk(ByteBuffer input) throws IOException {
        if (input.remaining() < 12) throw invalid();
        int length = input.getInt();
        int start = input.position();
        int type = input.getInt();
        if (length < 0 || length > input.remaining() - 4) throw invalid();
        int offset = input.position();
        input.position(offset + length);
        int expected = input.getInt();
        var crc = new CRC32();
        crc.update(input.array(), start, 4 + length);
        if ((int) crc.getValue() != expected) throw invalid();
        return new Chunk(type, offset, length);
    }

    private static Control control(ByteBuffer input, int offset, int canvasWidth, int canvasHeight) throws IOException {
        int w = input.getInt(offset + 4), h = input.getInt(offset + 8);
        int x = input.getInt(offset + 12), y = input.getInt(offset + 16);
        RichMediaDecoder.checkRect(x, y, w, h, canvasWidth, canvasHeight);
        int numerator = Short.toUnsignedInt(input.getShort(offset + 20));
        int denominator = Short.toUnsignedInt(input.getShort(offset + 22));
        int delay = Math.max(16, Math.round(numerator * 1000f / (denominator == 0 ? 100 : denominator)));
        int dispose = Byte.toUnsignedInt(input.get(offset + 24)), blend = Byte.toUnsignedInt(input.get(offset + 25));
        if (dispose > 2 || blend > 1 || delay > RichMediaDecoder.MAX_DURATION) throw invalid();
        return new Control(w, h, x, y, delay, dispose, blend, new ArrayList<>());
    }

    private static RichMediaDecoder.Sheet animated(byte[] bytes, byte[] header, List<Chunk> palette,
                                                  List<Control> frames, int width, int height, long plays)
            throws IOException {
        if (frames.stream().mapToLong(Control::delayMs).sum() > RichMediaDecoder.MAX_DURATION) throw invalid();
        var canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var atlas = new RichMediaDecoder.Atlas(width, height, frames.size());
        for (var frame : frames) {
            if (frame.data().isEmpty()) throw invalid();
            var decoded = image(bytes, header, palette, frame.data(), frame.width(), frame.height());
            int x = frame.x(), y = frame.y(), w = frame.width(), h = frame.height();
            int[] restore = frame.dispose() == 2 ? canvas.getRGB(x, y, w, h, null, 0, w) : null;
            RichMediaDecoder.composite(canvas, decoded, x, y, frame.blend() == 0);
            atlas.add(canvas, frame.delayMs());
            if (restore != null) canvas.setRGB(x, y, w, h, restore, 0, w);
            else if (frame.dispose() == 1) RichMediaDecoder.clear(canvas, x, y, w, h);
        }
        return atlas.finish(plays);
    }

    private static BufferedImage image(byte[] bytes, byte[] header, List<Chunk> palette,
                                       List<Chunk> data, int width, int height) throws IOException {
        var payload = new ByteArrayOutputStream();
        var output = new DataOutputStream(payload);
        output.writeLong(RichMediaDecoder.PNG_SIGNATURE);
        ByteBuffer.wrap(header).putInt(width).putInt(height);
        writeChunk(output, IHDR, header, 0, header.length);
        for (var chunk : palette) writeChunk(output, chunk.type(), bytes, chunk.offset(), chunk.length());
        for (var chunk : data) writeChunk(output, IDAT, bytes, chunk.offset(), chunk.length());
        writeChunk(output, IEND, header, 0, 0);
        return RichMediaDecoder.readPng(payload.toByteArray(), width, height);
    }

    private static void writeChunk(DataOutputStream output, int type, byte[] bytes, int offset, int size)
            throws IOException {
        output.writeInt(size);
        output.writeInt(type);
        output.write(bytes, offset, size);
        var crc = new CRC32();
        for (int shift = 24; shift >= 0; shift -= 8) crc.update(type >>> shift);
        crc.update(bytes, offset, size);
        output.writeInt((int) crc.getValue());
    }

    private static RichMediaDecoder.Sheet still(BufferedImage image) {
        return new RichMediaDecoder.Sheet(image, image.getWidth(), image.getHeight(),
                List.of(new RichMediaDecoder.Frame(0, 0, 100)), 1);
    }

    private static RichMediaDecoder.Sheet spriteSheet(BufferedImage image, byte[] metadata) throws IOException {
        if (metadata.length > MAX_METADATA_BYTES) throw invalid();
        try (var reader = new JsonReader(new StringReader(new String(metadata, StandardCharsets.UTF_8)))) {
            RichMediaDecoder.Sheet sheet = null;
            reader.beginObject();
            while (reader.hasNext()) {
                if ("animation".equals(reader.nextName())) {
                    if (sheet != null) throw invalid();
                    sheet = animation(reader, image);
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid();
            return sheet == null ? still(image) : sheet;
        }
    }

    private static RichMediaDecoder.Sheet animation(JsonReader reader, BufferedImage image) throws IOException {
        int width = 0, height = 0, ticks = 1;
        List<Timing> timings = new ArrayList<>();
        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "width" -> width = positive(reader);
                case "height" -> height = positive(reader);
                case "frametime" -> ticks = positive(reader);
                case "frames" -> timings = timings(reader);
                default -> reader.skipValue();
            }
        }
        reader.endObject();
        if (width == 0 && height == 0) {
            width = height = Math.min(image.getWidth(), image.getHeight());
        } else {
            if (width == 0) width = image.getWidth();
            if (height == 0) height = image.getHeight();
        }
        if (image.getWidth() % width != 0 || image.getHeight() % height != 0) throw invalid();
        int columns = image.getWidth() / width, total = columns * (image.getHeight() / height);
        if (timings.isEmpty()) {
            if (total > RichMediaDecoder.MAX_FRAMES) throw invalid();
            for (int i = 0; i < total; i++) timings.add(new Timing(i, 0));
        }
        var frames = new ArrayList<RichMediaDecoder.Frame>();
        for (var timing : timings) {
            if (timing.index() < 0 || timing.index() >= total) throw invalid();
            int frameTicks = timing.ticks() == 0 ? ticks : timing.ticks();
            if (frameTicks > RichMediaDecoder.MAX_DURATION / 50) throw invalid();
            frames.add(new RichMediaDecoder.Frame(timing.index() % columns * width,
                    timing.index() / columns * height, frameTicks * 50));
        }
        return new RichMediaDecoder.Sheet(image, width, height, frames, 0);
    }

    private static List<Timing> timings(JsonReader reader) throws IOException {
        var timings = new ArrayList<Timing>();
        reader.beginArray();
        while (reader.hasNext()) {
            if (timings.size() >= RichMediaDecoder.MAX_FRAMES) throw invalid();
            if (reader.peek() == JsonToken.NUMBER) {
                timings.add(new Timing(reader.nextInt(), 0));
            } else {
                int index = -1, duration = 0;
                reader.beginObject();
                while (reader.hasNext()) {
                    switch (reader.nextName()) {
                        case "index" -> index = integer(reader);
                        case "time" -> duration = positive(reader);
                        default -> reader.skipValue();
                    }
                }
                reader.endObject();
                timings.add(new Timing(index, duration));
            }
        }
        reader.endArray();
        return timings;
    }

    private static int integer(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.NUMBER) throw invalid();
        return reader.nextInt();
    }

    private static int positive(JsonReader reader) throws IOException {
        int value = integer(reader);
        if (value <= 0) throw invalid();
        return value;
    }

    private static IOException invalid() { return new IOException("Invalid inline PNG animation data"); }
}
