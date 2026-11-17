// Author: dishanhai
package com.dishanhai.sh_better_ui.client.font;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.*;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.zip.*;

/** Local-only, bounded TrueType resource packs. No global Minecraft font replacement. */
public final class LocalFontPack {
    public static final int MAX_BYTES = 32 * 1024 * 1024;
    public static final String PREFIX = "shbui-userfont-";
    public record Source(String name, String root, String font, String hash, String license, String licenseHash) {}
    public static final List<Source> SOURCES = List.of(
        new Source("霞鹜文楷 1.520 (中文, 24 MB)", "https://raw.githubusercontent.com/lxgw/LxgwWenKai/b79944853c0725d9f9c8996f07f2476cff1e9463/", "fonts/TTF/LXGWWenKai-Regular.ttf", "8d6ba638ac9553413354cfaab97637c1cd778444e259441ea1e5f8fb2c697fba", "OFL.txt", "5c0f0d98a8b71a401063c9a9e814e608fef6824fefab0c2ab73fe0955a5551b1"),
        new Source("Source Sans 3.052 (Latin, 421 KB)", "https://raw.githubusercontent.com/adobe-fonts/source-sans/ed1808970eb3c7301c9a523bee26473ba0bb62fa/", "TTF/SourceSans3-Regular.ttf", "4644c81b86ec9caaa76b634889968ed3c4f4f52f054855933acc7c2b21e53b0f", "LICENSE.md", "89ad2c4f66dd29127527493e729c31e731f111cf10faf5774c3db9275ed0c22c"));
    private LocalFontPack() {}
    public static byte[] read(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Choose a regular local TTF file (not a link).");
        try (var in = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = bounded(in, MAX_BYTES, () -> false); validate(bytes); return bytes;
        }
    }
    /** Structural checks before the game's parser; not a sandbox for untrusted fonts. */
    public static void validate(byte[] bytes) throws IOException {
        if(bytes.length < 12 || bytes.length > MAX_BYTES) throw new IOException("TTF size must be between 12 bytes and 32 MiB.");
        var b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        if(b.getInt() != 0x00010000) throw new IOException("Only standalone TrueType TTF is supported; no TTC/OTF/WOFF.");
        int count = Short.toUnsignedInt(b.getShort());
        if(count < 1 || count > 256 || 12L + count * 16L > bytes.length) throw new IOException("Invalid TTF table directory.");
        Set<String> tags = new HashSet<>();
        for(int i=0;i<count;i++) {
            b.position(12 + i * 16); byte[] tag = new byte[4]; b.get(tag); b.getInt();
            long offset=Integer.toUnsignedLong(b.getInt()), size=Integer.toUnsignedLong(b.getInt());
            if(offset < 12L + count * 16L || offset + size > bytes.length) throw new IOException("Invalid TTF table bounds.");
            if(!tags.add(new String(tag, java.nio.charset.StandardCharsets.US_ASCII))) throw new IOException("Duplicate TTF table.");
        }
        if(!tags.containsAll(List.of("cmap","head","hhea","hmtx","maxp","loca","glyf")) || tags.contains("fvar"))
            throw new IOException("Use a static TrueType font with glyf outlines, not a variable font.");
    }
    static byte[] bounded(InputStream in, int limit, BooleanSupplier cancelled) throws IOException {
        var out = new ByteArrayOutputStream(); byte[] buffer = new byte[16384]; int n;
        while((n=in.read(buffer))!=-1) { check(cancelled); if(out.size()+n > limit) throw new IOException("Font download exceeds size limit."); out.write(buffer,0,n); }
        check(cancelled); return out.toByteArray();
    }
    static void check(BooleanSupplier cancelled) throws IOException { if(cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new IOException("Cancelled"); }
    private static byte[] download(String url, String hash, int limit, BooleanSupplier cancelled) throws Exception {
        check(cancelled);
        var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).followRedirects(HttpClient.Redirect.NEVER).build();
        // URL comes exclusively from immutable, built-in official source entries, never user text.
        var request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).GET().build();
        // ofByteArrayConsumer bounds streaming data and honors the request timeout for the entire body.
        var out=new ByteArrayOutputStream();
        var response=client.send(request, HttpResponse.BodyHandlers.ofByteArrayConsumer(part -> part.ifPresent(bytes -> {
            if(cancelled.getAsBoolean() || out.size()+bytes.length > limit) throw new IllegalStateException("Cancelled or oversized download");
            out.writeBytes(bytes);
        })));
        if(response.statusCode()!=200) throw new IOException("Font server returned HTTP " + response.statusCode());
        check(cancelled); byte[] bytes=out.toByteArray();
        if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(hash)) throw new IOException("Font checksum mismatch; not installed.");
        return bytes;
    }
    public static Path fetch(Source source, Path directory, int format, BooleanSupplier cancelled) throws Exception {
        if(!SOURCES.contains(source)) throw new IOException("Unknown font source");
        byte[] bytes=download(source.root()+source.font(),source.hash(),MAX_BYTES,cancelled); validate(bytes);
        byte[] license=download(source.root()+source.license(),source.licenseHash(),65536,cancelled);
        return create(bytes,directory,format,license,cancelled);
    }
    public static Path create(byte[] bytes, Path directory, int format, byte[] license, BooleanSupplier cancelled) throws IOException {
        validate(bytes); check(cancelled); Files.createDirectories(directory);
        Path temp=Files.createTempFile(directory,".shbui-font-",".tmp");
        Path result=directory.resolve(PREFIX+UUID.randomUUID()+".zip");
        try {
            try(var zip=new ZipOutputStream(Files.newOutputStream(temp))) {
                entry(zip,"pack.mcmeta",("{\"pack\":{\"pack_format\":"+format+",\"description\":\"SHBUI local music font (disable to restore)\"}}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                entry(zip,"assets/sh_better_ui/font/user_ui.ttf",bytes);
                String json="{\"providers\":[{\"type\":\"ttf\",\"file\":\"sh_better_ui:user_ui.ttf\",\"size\":11,\"oversample\":3,\"shift\":[0,0]},{\"type\":\"reference\",\"id\":\"minecraft:default\"}]}";
                entry(zip,"assets/sh_better_ui/font/mp_ui.json",json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                entry(zip,"assets/sh_better_ui/font/mp_ui_semibold.json",json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                entry(zip,"LICENSE.txt",license);
            }
            check(cancelled); Files.move(temp,result); return result;
        } finally { Files.deleteIfExists(temp); }
    }
    private static void entry(ZipOutputStream zip,String name,byte[] data) throws IOException { zip.putNextEntry(new ZipEntry(name));zip.write(data);zip.closeEntry(); }
}
