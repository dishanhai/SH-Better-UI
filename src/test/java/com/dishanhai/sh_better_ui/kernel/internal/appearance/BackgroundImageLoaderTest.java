// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.appearance;

import com.dishanhai.sh_better_ui.kernel.api.appearance.BackgroundImage;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class BackgroundImageLoaderTest {
    @Test void acceptsLocalAndResourcePathsButRejectsUnsupportedFormats() {
        assertDoesNotThrow(() -> new BackgroundImage("C:\\Pictures\\背景.PNG", BackgroundImage.Fit.COVER));
        assertDoesNotThrow(() -> new BackgroundImage("config/sh_better_ui/background.jpg", BackgroundImage.Fit.CONTAIN));
        assertDoesNotThrow(() -> new BackgroundImage("pack:textures/background.png", BackgroundImage.Fit.STRETCH));
        assertFalse(BackgroundImage.resource("c:/pictures/background.png"));
        assertTrue(BackgroundImage.resource("pack:textures/background.png"));
        assertThrows(IllegalArgumentException.class, () -> new BackgroundImage("https://example.com/a.png", BackgroundImage.Fit.COVER));
        assertThrows(IllegalArgumentException.class, () -> new BackgroundImage("background.gif", BackgroundImage.Fit.COVER));
    }

    @Test void decodesPngWithAlphaAndJpeg() throws IOException {
        var image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(1, 0, 0x8055AACC);
        var bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", bytes));
        var decoded = BackgroundImageLoader.read(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(3, decoded.width());
        assertEquals(2, decoded.height());
        assertEquals(0x8055AACC, decoded.argb()[1]);
        bytes.reset();
        assertTrue(ImageIO.write(new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB), "jpg", bytes));
        assertEquals(12, BackgroundImageLoader.read(new ByteArrayInputStream(bytes.toByteArray())).argb().length);
    }

    @Test void rejectsCorruptAndOversizedInputs() throws IOException {
        assertThrows(IOException.class, () -> BackgroundImageLoader.read(new ByteArrayInputStream(new byte[]{1, 2, 3})));
        assertThrows(IOException.class, () -> BackgroundImageLoader.read(new ByteArrayInputStream(new byte[BackgroundImageLoader.MAX_BYTES + 1])));
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", bytes);
        byte[] png = bytes.toByteArray();
        // IHDR dimensions are inspected before attempting to decode the one-pixel raster.
        java.nio.ByteBuffer.wrap(png).putInt(16, 8193);
        assertThrows(IOException.class, () -> BackgroundImageLoader.read(new ByteArrayInputStream(png)));
    }
}
