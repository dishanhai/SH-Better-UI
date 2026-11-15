// Author: dishanhai
package com.dishanhai.sh_better_ui.automation.script;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;

/** Extracts only the bundled worker, never an uploaded executable or a mod classpath. */
public final class BundledScriptWorker {
    public static final String RESOURCE = "/sh_better_ui/automation/uuk-script-worker.jar";
    private static final int MAX_JAR_BYTES = 8 * 1024 * 1024;
    private BundledScriptWorker() {}
    public static Path extract(Path directory) throws IOException {
        Files.createDirectories(directory);
        byte[] bytes;
        try (InputStream input = BundledScriptWorker.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new FileNotFoundException("Bundled script worker missing");
            bytes = input.readNBytes(MAX_JAR_BYTES + 1);
            if (bytes.length > MAX_JAR_BYTES) throw new IOException("Bundled worker size limit");
        }
        String digest;
        try { digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        Path target = directory.resolve("uuk-script-worker-" + digest + ".jar");
        Path temp = Files.createTempFile(directory, "uuk-script-worker-", ".tmp");
        try {
            Files.write(temp, bytes);
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
        return target;
    }
}
