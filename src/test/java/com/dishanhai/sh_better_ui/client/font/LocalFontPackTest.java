package com.dishanhai.sh_better_ui.client.font;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.ByteBuffer;
import java.io.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class LocalFontPackTest {
    @TempDir Path temp;
    private byte[] font() throws Exception {
        try(var in=getClass().getResourceAsStream("/assets/sh_better_ui/font/sh_sans_ui.ttf")) {
            assertNotNull(in); return in.readAllBytes();
        }
    }
    @Test void realBundledStaticFontCreatesBothPackFormats() throws Exception {
        for(int format:new int[]{15,34}) {
            byte[] data=font();Path pack=LocalFontPack.create(data,temp,format,"OFL fixture".getBytes(),()->false);
            try(var zip=new ZipFile(pack.toFile())) {
                assertEquals(5,zip.size());
                assertTrue(new String(zip.getInputStream(zip.getEntry("pack.mcmeta")).readAllBytes()).contains("\"pack_format\":"+format));
                assertArrayEquals(data,zip.getInputStream(zip.getEntry("assets/sh_better_ui/font/user_ui.ttf")).readAllBytes());
                String json=new String(zip.getInputStream(zip.getEntry("assets/sh_better_ui/font/mp_ui.json")).readAllBytes());
                assertTrue(json.contains("minecraft:default"));assertNull(zip.getEntry("assets/minecraft/font/default.json"));
            }
        }
    }
    @Test void rejectsFakeHeaderTruncatedAndOutOfBoundsTable() throws Exception {
        assertThrows(IOException.class,()->LocalFontPack.validate(new byte[]{0,1,0,0}));
        byte[] bytes=font();ByteBuffer.wrap(bytes).putInt(20,Integer.MAX_VALUE);
        assertThrows(IOException.class,()->LocalFontPack.validate(bytes));
    }
    @Test void cancelledBuildLeavesNoPack() throws Exception {
        assertThrows(IOException.class,()->LocalFontPack.create(font(),temp,34,new byte[0],()->true));
        try(var entries=Files.list(temp)){assertEquals(0,entries.count());}
    }
    @Test void rejectsSymlinkAndBoundedStreamOverflow() throws Exception {
        Path real=temp.resolve("real.ttf");Files.write(real,font());Path link=temp.resolve("link.ttf");Files.createSymbolicLink(link,real);
        assertThrows(IOException.class,()->LocalFontPack.read(link));
        assertThrows(IOException.class,()->LocalFontPack.bounded(new ByteArrayInputStream(new byte[40]),32,()->false));
    }
    @Test void presetsUseImmutableOfficialHttpsAndDigests() {
        for(var preset:LocalFontPack.SOURCES) {
            assertTrue(preset.root().matches("https://raw\\.githubusercontent\\.com/(lxgw/LxgwWenKai|adobe-fonts/source-sans)/[0-9a-f]{40}/"));
            assertTrue(preset.hash().matches("[0-9a-f]{64}"));assertTrue(preset.licenseHash().matches("[0-9a-f]{64}"));
        }
    }
}
