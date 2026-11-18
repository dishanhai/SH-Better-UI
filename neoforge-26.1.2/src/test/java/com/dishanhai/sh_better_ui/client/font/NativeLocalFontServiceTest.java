// Author: dishanhai
package com.dishanhai.sh_better_ui.client.font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class NativeLocalFontServiceTest {
    @TempDir Path directory;
    final class Fake implements NativeLocalFontService.Backend {
        List<String> selected=List.of("vanilla","file/other.zip","file/"+LocalFontPack.PREFIX+"old.zip");
        final List<CompletableFuture<Void>> reloads=new ArrayList<>();
        Path pack; int saves; boolean failNextPersist;
        public Path prepare(Path file,AtomicBoolean cancelled)throws Exception {return pack=Files.writeString(directory.resolve(LocalFontPack.PREFIX+"new.zip"),"fixture");}
        public void execute(Runnable r){r.run();}
        public List<String> selected(){return selected;}
        public void select(List<String> ids){selected=List.copyOf(ids);}
        public boolean discover(String id){return true;}
        public CompletableFuture<Void> reload(){var f=new CompletableFuture<Void>();reloads.add(f);return f;}
        public void persist(){if(failNextPersist){failNextPersist=false;throw new IllegalStateException("fixture persistence failure");}saves++;}
    }
    @Test void applyThenRestorePreservesOtherPacksAndOnlyPersistsSuccess(){
        var backend=new Fake();var service=new NativeLocalFontService(backend,Runnable::run);
        var result=service.importConfirmed(directory.resolve("user.ttf"));
        assertTrue(service.applying()); assertEquals(0,backend.saves);
        backend.reloads.get(0).complete(null);
        assertEquals(NativeLocalFontService.Result.APPLIED,result.join());assertEquals(1,backend.saves);
        assertTrue(backend.selected.contains("file/other.zip"));
        var restored=service.restore();backend.reloads.get(1).complete(null);
        assertEquals(NativeLocalFontService.Result.RESTORED,restored.join());
        assertEquals(List.of("vanilla","file/other.zip"),backend.selected);assertTrue(Files.exists(backend.pack));
    }
    @Test void failedReloadRestoresPreviousSelectionAndDeletesGeneratedPack(){
        var backend=new Fake();var before=backend.selected;var service=new NativeLocalFontService(backend,Runnable::run);
        var result=service.importConfirmed(directory.resolve("user.ttf"));backend.reloads.get(0).completeExceptionally(new IllegalStateException("font rejected"));
        assertEquals(before,backend.selected);assertFalse(result.isDone());backend.reloads.get(1).complete(null);
        assertEquals(NativeLocalFontService.Result.FAILED,result.join());assertFalse(Files.exists(backend.pack));
    }
    @Test void closingDuringReloadRollsBackAndBlocksAnotherConcurrentTransaction(){
        var backend=new Fake();var before=backend.selected;var service=new NativeLocalFontService(backend,Runnable::run);
        var result=service.importConfirmed(directory.resolve("user.ttf"));
        var other=new NativeLocalFontService(new Fake(),Runnable::run);
        assertEquals(NativeLocalFontService.Result.FAILED,other.restore().join());
        service.close();backend.reloads.get(0).complete(null);backend.reloads.get(1).complete(null);
        assertEquals(NativeLocalFontService.Result.CANCELLED,result.join());assertEquals(before,backend.selected);
    }
    @Test void latePreparedPackCannotChangeSelectionAfterClose(){
        var backend=new Fake();var before=backend.selected;var tasks=new ArrayList<Runnable>();
        var service=new NativeLocalFontService(backend,tasks::add);var result=service.importConfirmed(directory.resolve("user.ttf"));
        service.close();tasks.removeFirst().run();
        assertEquals(NativeLocalFontService.Result.CANCELLED,result.join());assertEquals(before,backend.selected);
        assertEquals(0,backend.reloads.size());assertFalse(Files.exists(backend.pack));
    }
    @Test void failedRecoveryIsNotReportedAsRestoredOrSaved(){
        var backend=new Fake();var service=new NativeLocalFontService(backend,Runnable::run);
        var result=service.restore();backend.reloads.get(0).completeExceptionally(new IllegalStateException());
        backend.reloads.get(1).completeExceptionally(new IllegalStateException());
        assertEquals(NativeLocalFontService.Result.RECOVERY_FAILED,result.join());assertEquals(0,backend.saves);
    }
    @Test void silentlyRecoveredMinecraftReloadStillRejectsMissingFont(){
        var backend=new Fake();var before=backend.selected;var service=new NativeLocalFontService(backend,Runnable::run);
        var result=service.importConfirmed(directory.resolve("user.ttf"));backend.selected=List.of("vanilla");
        backend.reloads.get(0).complete(null);assertEquals(before,backend.selected);backend.reloads.get(1).complete(null);
        assertEquals(NativeLocalFontService.Result.FAILED,result.join());
    }
    @Test void persistenceExceptionRollsBackAndPersistsOnlyOriginalSelection(){
        var backend=new Fake();var before=backend.selected;var service=new NativeLocalFontService(backend,Runnable::run);
        var result=service.importConfirmed(directory.resolve("user.ttf"));backend.failNextPersist=true;
        backend.reloads.get(0).complete(null);
        assertEquals(before,backend.selected);assertFalse(result.isDone());assertEquals(0,backend.saves);
        backend.reloads.get(1).complete(null);
        assertEquals(NativeLocalFontService.Result.FAILED,result.join());assertEquals(1,backend.saves);
        assertFalse(service.busy());assertFalse(Files.exists(backend.pack));
    }
    @Test void nativePackUsesExactModernMetadataAndOnlyMusicFontIds()throws Exception {
        byte[] data;try(var input=getClass().getResourceAsStream("/assets/sh_better_ui/font/sh_sans_ui.ttf")){assertNotNull(input);data=input.readAllBytes();}
        Path pack=NativeLocalFontPack.create(data,directory,84,0,()->false);
        try(var zip=new ZipFile(pack.toFile())){
            var meta=com.google.gson.JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("pack.mcmeta")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("pack");
            // Full Component/PackMetadataSection decoding requires a running FML client.
            // Exercise the official format-range codec here, not a hand-written version parser.
            var decoded=net.minecraft.server.packs.metadata.pack.PackFormat.packCodec(net.minecraft.server.packs.PackType.CLIENT_RESOURCES).codec()
                    .parse(com.mojang.serialization.JsonOps.INSTANCE,meta).getOrThrow();
            var version=net.minecraft.server.packs.metadata.pack.PackFormat.of(84,0);
            assertEquals(new net.minecraft.util.InclusiveRange<>(version,version),decoded);
            assertEquals("SHBUI local music font",meta.get("description").getAsString());
            assertEquals("[84,0]",meta.get("min_format").toString());assertEquals(meta.get("min_format"),meta.get("max_format"));assertFalse(meta.has("pack_format"));
            assertNotNull(zip.getEntry("assets/sh_better_ui/font/mp_ui.json"));assertNotNull(zip.getEntry("assets/sh_better_ui/font/mp_ui_semibold.json"));assertNull(zip.getEntry("assets/minecraft/font/default.json"));
            assertArrayEquals(data,zip.getInputStream(zip.getEntry("assets/sh_better_ui/font/user_ui.ttf")).readAllBytes());
        }
        assertThrows(java.io.IOException.class,()->NativeLocalFontPack.create(data,directory,84,0,()->true));
        try(var files=Files.list(directory)){assertEquals(1,files.count());}
    }
}
