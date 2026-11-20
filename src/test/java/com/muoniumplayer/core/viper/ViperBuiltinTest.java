package com.muoniumplayer.core.viper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class ViperBuiltinTest {
    private static final String A = "SHBUI - Surrey Room A.json";
    private static final String B = "SHBUI - Surrey Room B.json";
    private static final String LEGACY = "酷狗HiFi";
    private static final String LATE = "酷狗-低音";

    @TempDir Path root;

    @Test
    void newInstallationAndUserChangesArePreserved() throws Exception {
        assertEquals(2, ViperBuiltin.provision(root.toFile(), 1));
        Path a = root.resolve("presets").resolve(A);
        Path b = root.resolve("presets").resolve(B);
        String text = Files.readString(a);
        ViperPreset preset = ViperPresetParser.parse(text, "fallback");
        assertEquals("Surrey-Room-A.wav", preset.text("convolver", "kernelFile", ""));
        assertTrue(preset.group("convolver").get("enable").getAsBoolean());
        assertEquals(1.0, preset.number("convolver", "crossChannel", 0), 0.0);
        assertFalse(preset.legacyMasterEnabled());
        Files.writeString(a, "user modified");
        Files.delete(b);
        assertEquals(0, ViperBuiltin.provision(root.toFile(), 1));
        assertEquals("user modified", Files.readString(a));
        assertFalse(Files.exists(b));
    }

    @Test
    void markerOnePreservesDeletedLegacyAndDiscoversLaterKernel() throws Exception {
        Files.writeString(root.resolve(ViperBuiltin.BUNDLE_MARKER), "1\n");
        Path kernels = Files.createDirectories(root.resolve("kernels"));
        Files.write(kernels.resolve(LEGACY + ".irs"), new byte[] { 1 });
        ViperBuiltin.provision(root.toFile(), 1);
        assertFalse(Files.exists(root.resolve("presets").resolve(LEGACY + ".json")));
        assertTrue(Files.isRegularFile(root.resolve("presets").resolve(A)));
        Files.write(kernels.resolve(LATE + ".irs"), new byte[] { 1 });
        assertEquals(1, ViperBuiltin.provision(root.toFile(), 1));
        Path preset = root.resolve("presets").resolve(LATE + ".json");
        assertTrue(Files.isRegularFile(preset));
        Files.delete(preset);
        ViperBuiltin.provision(root.toFile(), 1);
        assertFalse(Files.exists(preset));
    }

    @Test
    void failedReceiptRollsBackAndCanRetry() throws Exception {
        Path receipts = Files.createDirectories(root.resolve(ViperBuiltin.RECEIPTS));
        Files.createDirectory(receipts.resolve(A));
        ViperBuiltin.provision(root.toFile(), 1);
        assertFalse(Files.exists(root.resolve("presets").resolve(A)));
        assertTrue(Files.isRegularFile(root.resolve("presets").resolve(B)));
        Files.delete(receipts.resolve(A));
        assertEquals(1, ViperBuiltin.provision(root.toFile(), 1));
        assertTrue(Files.isRegularFile(root.resolve("presets").resolve(A)));
    }

    @Test
    void existingLegacyPresetIsRememberedEvenBeforeItsKernelExists() throws Exception {
        Path presets = Files.createDirectories(root.resolve("presets"));
        Path preset = presets.resolve(LEGACY + ".json");
        Files.writeString(preset, "user content");
        ViperBuiltin.provision(root.toFile(), 1);
        assertEquals("user content", Files.readString(preset));
        Files.delete(preset);
        Path kernels = Files.createDirectories(root.resolve("kernels"));
        Files.write(kernels.resolve(LEGACY + ".irs"), new byte[] { 1 });
        ViperBuiltin.provision(root.toFile(), 1);
        assertFalse(Files.exists(preset));
    }

    @Test
    void concurrentProvisionPublishesCompleteFilesOnce() throws Exception {
        var executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<Integer>> runs = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                runs.add(executor.submit(() -> ViperBuiltin.provision(root.toFile(), 1)));
            }
            int total = 0;
            for (Future<Integer> run : runs) total += run.get();
            assertEquals(2, total);
            for (String name : List.of(A, B)) {
                String text = Files.readString(root.resolve("presets").resolve(name));
                assertTrue(ViperPresetParser.parse(text, "fallback")
                        .group("convolver").get("enable").getAsBoolean());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void publicationFallsBackToNoReplaceMove() throws Exception {
        Path source = root.resolve("temporary");
        Path target = root.resolve("target");
        Files.writeString(source, "complete content");
        ViperBuiltin.publish(target, source, (ignored, path) -> {
            throw new UnsupportedOperationException("hard links unavailable");
        });
        assertEquals("complete content", Files.readString(target));
        assertFalse(Files.exists(source));

        Files.writeString(source, "another version");
        assertThrows(FileAlreadyExistsException.class, () -> ViperBuiltin.publish(target, source,
                (ignored, path) -> { throw new UnsupportedOperationException("hard links unavailable"); }));
        assertEquals("complete content", Files.readString(target));
        assertEquals("another version", Files.readString(source));
    }
}
