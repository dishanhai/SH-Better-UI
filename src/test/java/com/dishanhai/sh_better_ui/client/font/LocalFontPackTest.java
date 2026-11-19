package com.dishanhai.sh_better_ui.client.font;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.ByteBuffer;
import java.io.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
                assertEquals(7,zip.size());
                assertTrue(new String(zip.getInputStream(zip.getEntry("pack.mcmeta")).readAllBytes()).contains("\"pack_format\":"+format));
                assertArrayEquals(data,zip.getInputStream(zip.getEntry("assets/sh_better_ui/font/user_ui.ttf")).readAllBytes());
                assertScopedProviders(zip);
            }
        }
    }
    static void assertScopedProviders(ZipFile zip) throws IOException {
        assertNull(zip.getEntry("assets/minecraft/font/default.json"));
        assertEquals(java.util.Set.of("pack.mcmeta","LICENSE.txt","assets/sh_better_ui/font/user_ui.ttf",
                        "assets/sh_better_ui/font/mp_ui.json","assets/sh_better_ui/font/mp_ui_semibold.json",
                        "assets/sh_better_ui/font/music_ui.json","assets/sh_better_ui/font/music_ui_semibold.json"),
                zip.stream().map(java.util.zip.ZipEntry::getName).collect(java.util.stream.Collectors.toSet()));
        for(String id:java.util.List.of("mp_ui","mp_ui_semibold","music_ui","music_ui_semibold")) {
            var entry=zip.getEntry("assets/sh_better_ui/font/"+id+".json");
            assertNotNull(entry,id);
            var json=com.google.gson.JsonParser.parseString(new String(zip.getInputStream(entry).readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var definitions=json.getAsJsonArray("providers");
            var order=new java.util.ArrayList<String>();
            for(var definition:definitions) {
                var item=definition.getAsJsonObject();
                order.add(switch(item.get("type").getAsString()) {
                    case "ttf" -> item.get("file").getAsString();
                    case "reference" -> item.get("id").getAsString();
                    default -> throw new AssertionError(item);
                });
            }
            var expected=switch(id) {
                case "music_ui" -> java.util.List.of("sh_better_ui:user_ui.ttf","sh_better_ui:sh_music_sans_medium.ttf","sh_better_ui:sh_sans_ui.ttf","minecraft:default");
                case "music_ui_semibold" -> java.util.List.of("sh_better_ui:user_ui.ttf","sh_better_ui:sh_music_sans_semibold.ttf","sh_better_ui:sh_sans_ui_semibold.ttf","minecraft:default");
                default -> java.util.List.of("sh_better_ui:user_ui.ttf","minecraft:default");
            };
            assertEquals(expected,order,id);
            assertEquals(0,definitions.get(0).getAsJsonObject().getAsJsonArray("shift").get(1).getAsInt(),"User baseline is unchanged");
            if(id.startsWith("music_"))for(int i=1;i<=2;i++)
                assertEquals(3,definitions.get(i).getAsJsonObject().getAsJsonArray("shift").get(1).getAsInt());
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
    @Test void rejectsSymlink() throws Exception {
        Path real = Files.write(temp.resolve("real.ttf"), font());
        Path link = temp.resolve("link.ttf");
        try {
            Files.createSymbolicLink(link, real);
        } catch (UnsupportedOperationException | FileSystemException unavailable) {
            assumeTrue(false, () -> "Symbolic link creation is unavailable: " + unavailable.getMessage());
        }
        assertThrows(IOException.class, () -> LocalFontPack.read(link));
    }

    @Test void rejectsBoundedStreamOverflow() {
        assertThrows(IOException.class, () -> LocalFontPack.bounded(new ByteArrayInputStream(new byte[40]), 32, () -> false));
    }
    @Test void presetsUseImmutableOfficialHttpsAndDigests() {
        for(var preset:LocalFontPack.SOURCES) {
            assertTrue(preset.root().matches("https://raw\\.githubusercontent\\.com/(lxgw/LxgwWenKai|adobe-fonts/source-sans)/[0-9a-f]{40}/"));
            assertTrue(preset.hash().matches("[0-9a-f]{64}"));assertTrue(preset.licenseHash().matches("[0-9a-f]{64}"));
        }
    }
}
