// Author: dishanhai
package com.dishanhai.sh_better_ui.client.font;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.zip.*;

/** Native pack metadata, without changing the legacy pack writer. Local files only. */
public final class NativeLocalFontPack {
    private NativeLocalFontPack() {}
    public static Path create(byte[] font, Path directory, int major, int minor, BooleanSupplier cancelled) throws IOException {
        LocalFontPack.validate(font); check(cancelled);
        Files.createDirectories(directory);
        Path temporary=Files.createTempFile(directory,".shbui-font-",".tmp");
        Path target=directory.resolve(LocalFontPack.PREFIX+UUID.randomUUID()+".zip");
        try {
            try(var zip=new ZipOutputStream(Files.newOutputStream(temporary))) {
                String version="["+major+","+minor+"]";
                put(zip,"pack.mcmeta",("{\"pack\":{\"min_format\":"+version+",\"max_format\":"+version+",\"description\":\"SHBUI local music font\"}}").getBytes(StandardCharsets.UTF_8));
                check(cancelled); put(zip,"assets/sh_better_ui/font/user_ui.ttf",font);
                for(String name:new String[]{"mp_ui","mp_ui_semibold","music_ui","music_ui_semibold"})
                    put(zip,"assets/sh_better_ui/font/"+name+".json",LocalFontPack.fontDefinition(name));
                put(zip,"LICENSE.txt","User-provided local font. SHBUI grants no redistribution permission.".getBytes(StandardCharsets.UTF_8));
            }
            check(cancelled); Files.move(temporary,target); return target;
        } finally { Files.deleteIfExists(temporary); }
    }
    private static void check(BooleanSupplier cancelled) throws IOException {
        if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
    }
    private static void put(ZipOutputStream zip,String name,byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name)); zip.write(data); zip.closeEntry();
    }
}
