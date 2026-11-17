// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;
import com.dishanhai.sh_better_ui.client.music.MusicSnapshot;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NativeMusicArtworkTest {
    @Test void imageKeysAreContentAddressedAndCannotAliasResourceIcons() {
        var a=new MusicSnapshot.Artwork(new byte[]{1,2,3});var b=new MusicSnapshot.Artwork(new byte[]{1,2,4});
        assertEquals(NativeMusicArtwork.icon(a),NativeMusicArtwork.icon(new MusicSnapshot.Artwork(a.png())));
        assertNotEquals(NativeMusicArtwork.icon(a),NativeMusicArtwork.icon(b));
        assertTrue(NativeMusicArtwork.isCover(NativeMusicArtwork.icon(a)));
        assertFalse(NativeMusicArtwork.isCover(UiId.parse("other:music/activity_cover/test")));
        assertNull(NativeMusicArtwork.icon(null));
    }
    @Test void invalidOrOversizedImagesAreRejectedBeforeNativeAllocation() {
        assertThrows(IOException.class,()->NativeMusicArtwork.read(new MusicSnapshot.Artwork(new byte[]{1,2,3})));
        var header=ByteBuffer.allocate(24).putLong(0x89504e470d0a1a0aL).putInt(13).putInt(0x49484452).putInt(241).putInt(240).array();
        assertThrows(IOException.class,()->NativeMusicArtwork.read(new MusicSnapshot.Artwork(header)));
    }
}
