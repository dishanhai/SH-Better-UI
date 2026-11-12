// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import org.junit.jupiter.api.Test;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

final class RichMediaDecoderTest {
    private static final int RED = 0xffe22616;
    private static final int GREEN = 0xff08b75d;
    private static final int BLUE = 0xff274bc8;

    @Test void preservesPixelsAndUsesExplicitMcmetaFrameOrderAndDuration() throws IOException {
        var source = new BufferedImage(2, 4, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(1, 0, RED);
        source.setRGB(1, 2, GREEN);
        byte[] metadata = """
                {"animation":{"frametime":3,"frames":[{"index":1,"time":2},0,1]}}
                """.getBytes(StandardCharsets.UTF_8);
        var result = RichMediaDecoder.decode(png(source), metadata);
        assertEquals(2, result.frameWidth());
        assertEquals(2, result.frameHeight());
        assertEquals(GREEN, pixel(result, 0, 1, 0, false));
        assertEquals(RED, pixel(result, 100, 1, 0, false));
        assertEquals(GREEN, pixel(result, 250, 1, 0, false));
        assertEquals(GREEN, pixel(result, 100, 1, 0, true));
        assertEquals(0, pixel(result, 0, 0, 0, false));

        assertThrows(IOException.class, () -> RichMediaDecoder.decode(png(source),
                "{\"animation\":{\"frames\":[-1]}}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test void decodesGifFrameDelaysAndDisposalFromRealImageSequence() throws IOException {
        var output = new ByteArrayOutputStream();
        var writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (var stream = new MemoryCacheImageOutputStream(output)) {
            writer.setOutput(stream);
            writer.prepareWriteSequence(null);
            writeGifFrame(writer, image(2, 2, RED), 0, "none", 4);
            writeGifFrame(writer, image(1, 1, GREEN), 1, "restoreToBackgroundColor", 6);
            writeGifFrame(writer, image(1, 1, BLUE), 0, "none", 3);
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }

        var gif = output.toByteArray();
        var result = RichMediaDecoder.decode(gif, null);
        assertEquals(3, result.frames().size());
        assertEquals(RED, pixel(result, 0, 1, 0, false));
        assertEquals(GREEN, pixel(result, 40, 1, 0, false));
        assertEquals(BLUE, pixel(result, 100, 0, 0, false));
        assertEquals(0, pixel(result, 100, 1, 0, false));
        assertEquals(RED, pixel(result, 100, 0, 1, false));
        assertEquals(BLUE, pixel(result, 400, 0, 0, false));
        assertEquals(RED, pixel(result, 400, 0, 0, true));
        assertThrows(IOException.class, () -> RichMediaDecoder.decode(Arrays.copyOf(gif, gif.length - 1), null));
    }

    @Test void composesApngOffsetsSourceBlendAndRestorePrevious() throws IOException {
        var bytes = new ByteArrayOutputStream();
        var output = new DataOutputStream(bytes);
        output.writeLong(RichMediaDecoder.PNG_SIGNATURE);
        var base = png(image(2, 2, RED));
        chunk(output, "IHDR", payload(base, "IHDR"));
        chunk(output, "acTL", ByteBuffer.allocate(8).putInt(3).putInt(0).array());
        chunk(output, "fcTL", control(0, 2, 2, 0, 0, 1, 0, 0));
        chunk(output, "IDAT", payload(base, "IDAT"));
        chunk(output, "fcTL", control(1, 1, 1, 1, 0, 2, 2, 0));
        frameData(output, 2, image(1, 1, GREEN));
        chunk(output, "fcTL", control(3, 1, 1, 0, 1, 3, 0, 1));
        frameData(output, 4, image(1, 1, BLUE));
        chunk(output, "IEND", new byte[0]);

        var result = RichMediaDecoder.decode(bytes.toByteArray(), null);
        assertEquals(3, result.frames().size());
        assertEquals(RED, pixel(result, 99, 1, 0, false));
        assertEquals(GREEN, pixel(result, 100, 1, 0, false));
        assertEquals(RED, pixel(result, 300, 1, 0, false));
        assertEquals(BLUE, pixel(result, 300, 0, 1, false));
        assertEquals(RED, pixel(result, 600, 0, 1, false));
    }

    @Test void rejectsTruncatedCorruptOversizedAndMalformedAssets() throws IOException {
        var random = new Random(5126);
        byte[] valid = png(image(3, 5, RED));
        for (int i = 0; i < 256; i++) {
            byte[] mutated = valid.clone();
            int offset = random.nextInt(mutated.length);
            mutated[offset] ^= 1 << random.nextInt(8);
            assertThrows(IOException.class, () -> RichMediaDecoder.decode(mutated, null));
        }
        byte[] oversizedGif = {'G', 'I', 'F', '8', '9', 'a', -1, 127, 2, 0, 0, 0, 0};
        assertThrows(IOException.class, () -> RichMediaDecoder.decode(oversizedGif, null));
        assertThrows(IOException.class, () -> RichMediaDecoder.read(new ByteArrayInputStream(valid), 16));
        assertThrows(IOException.class, () -> new RichMediaDecoder.Atlas(2048, 2048, 2));
        assertThrows(IOException.class, () -> RichMediaDecoder.decode(valid, new byte[17 * 1024]));
    }

    private static int pixel(RichMediaDecoder.Sheet sheet, long time, int x, int y, boolean reducedMotion) {
        var frame = sheet.frameAt(time, reducedMotion);
        return sheet.pixels().getRGB(frame.x() + x, frame.y() + y);
    }

    private static BufferedImage image(int width, int height, int argb) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) image.setRGB(x, y, argb);
        }
        return image;
    }

    private static byte[] png(BufferedImage image) throws IOException {
        var output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", output));
        return output.toByteArray();
    }

    private static byte[] payload(byte[] png, String wanted) throws IOException {
        var buffer = ByteBuffer.wrap(png);
        buffer.position(8);
        int type = ByteBuffer.wrap(wanted.getBytes(StandardCharsets.US_ASCII)).getInt();
        while (buffer.hasRemaining()) {
            int size = buffer.getInt();
            int id = buffer.getInt();
            if (id == type) {
                byte[] data = new byte[size];
                buffer.get(data);
                return data;
            }
            buffer.position(buffer.position() + size + 4);
        }
        throw new IOException("Missing fixture PNG chunk");
    }

    private static byte[] control(int sequence, int width, int height, int x, int y,
                                  int delayTenths, int disposal, int blend) {
        return ByteBuffer.allocate(26).putInt(sequence).putInt(width).putInt(height).putInt(x).putInt(y)
                .putShort((short) delayTenths).putShort((short) 10).put((byte) disposal).put((byte) blend).array();
    }

    private static void frameData(DataOutputStream output, int sequence, BufferedImage image) throws IOException {
        byte[] data = payload(png(image), "IDAT");
        chunk(output, "fdAT", ByteBuffer.allocate(data.length + 4).putInt(sequence).put(data).array());
    }

    private static void chunk(DataOutputStream output, String name, byte[] data) throws IOException {
        byte[] type = name.getBytes(StandardCharsets.US_ASCII);
        output.writeInt(data.length);
        output.write(type);
        output.write(data);
        var crc = new CRC32();
        crc.update(type);
        crc.update(data);
        output.writeInt((int) crc.getValue());
    }

    private static void writeGifFrame(javax.imageio.ImageWriter writer, BufferedImage image, int left, String disposal,
                                      int delay) throws IOException {
        var parameters = writer.getDefaultWriteParam();
        var metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), parameters);
        String format = metadata.getNativeMetadataFormatName();
        var tree = (IIOMetadataNode) metadata.getAsTree(format);
        var descriptor = (IIOMetadataNode) tree.getElementsByTagName("ImageDescriptor").item(0);
        descriptor.setAttribute("imageLeftPosition", Integer.toString(left));
        var control = (IIOMetadataNode) tree.getElementsByTagName("GraphicControlExtension").item(0);
        control.setAttribute("disposalMethod", disposal);
        control.setAttribute("delayTime", Integer.toString(delay));
        metadata.setFromTree(format, tree);
        writer.writeToSequence(new IIOImage(image, null, metadata), parameters);
    }
}
