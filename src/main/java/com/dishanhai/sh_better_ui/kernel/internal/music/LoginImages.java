// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicLogin.Image;
import com.dishanhai.sh_better_ui.kernel.internal.music.qrcodegen.QrCode;
import java.io.*;
import java.util.BitSet;
import javax.imageio.ImageIO;

final class LoginImages {
    static Image encode(String text) {
        var qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM);
        int size = qr.size + 8;
        var dark = new BitSet(size * size);
        for (int y = 0; y < qr.size; y++) for (int x = 0; x < qr.size; x++)
            if (qr.getModule(x, y)) dark.set((y + 4) * size + x + 4);
        return new Image(size, size, dark);
    }
    static Image png(byte[] bytes) throws IOException {
        if (bytes.length > 512 * 1024 || bytes.length < 8 || bytes[0] != (byte) 137
                || bytes[1] != 'P' || bytes[2] != 'N' || bytes[3] != 'G') throw new IOException("Invalid QR PNG");
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReadersByFormatName("png");
            if (!readers.hasNext()) throw new IOException("PNG reader unavailable");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 21 || height < 21 || width > 504 || height > 504) throw new IOException("Invalid QR dimensions");
                var image = reader.read(0);
                var dark = new BitSet((width + 8) * (height + 8));
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                    int pixel = image.getRGB(x, y);
                    if ((pixel >>> 24) > 127 && ((pixel >> 16 & 255) + (pixel >> 8 & 255) + (pixel & 255)) < 384)
                        dark.set((y + 4) * (width + 8) + x + 4);
                }
                return new Image(width + 8, height + 8, dark);
            } finally { reader.dispose(); }
        }
    }
}
