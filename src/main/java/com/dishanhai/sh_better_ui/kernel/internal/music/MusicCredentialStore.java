// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.sun.jna.platform.win32.Crypt32Util;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Opt-in storage for source cookies. The local POSIX key is accessible to the same OS user. */
final class MusicCredentialStore {
    private static final int MAX_COOKIE = 16_384, MAX_FILE = 65_536;
    private static final String SUFFIX = ".credential";
    private static final Set<PosixFilePermission> PRIVATE_FILE = PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> PRIVATE_DIRECTORY = PosixFilePermissions.fromString("rwx------");
    private static final Object KEY_LOCK = new Object();
    private static final Object LEASE_LOCK = new Object();
    private static final Map<LeaseKey, Long> REVISIONS = new HashMap<>();
    private record LeaseKey(Path directory, String source) {}
    record Saved(String cookie, long revision) {
        @Override public String toString() { return "Saved[redacted]"; }
    }
    private final SecureRandom random = new SecureRandom();
    private final Path directory;
    private final boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

    MusicCredentialStore(Path directory) { this.directory = Objects.requireNonNull(directory).toAbsolutePath().normalize(); }

    synchronized Map<String, String> load() throws IOException {
        synchronized (LEASE_LOCK) {
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return Map.of();
            checkDirectory();
            var result = new LinkedHashMap<String, String>();
            try (var paths = Files.list(directory)) {
                for (var path : paths.sorted().toList()) {
                    String file = path.getFileName().toString();
                    if (!file.endsWith(SUFFIX)) continue;
                    String source = file.substring(0, file.length() - SUFFIX.length());
                    if (!validSource(source)) continue;
                    String cookie = readCookie(path);
                    result.put(source, cookie);
                }
            }
            return Map.copyOf(result);
        }
    }

    synchronized void put(String source, String cookie) throws IOException {
        validateSource(source);
        cookie = validatedCookie(cookie);
        synchronized (LEASE_LOCK) {
            if (cookie.isBlank()) { remove(source); return; }
            prepareDirectory();
            byte[] encrypted = encrypt(cookie);
            writeAtomic(directory.resolve(source + SUFFIX), encrypted);
        }
    }

    synchronized void remove(String source) throws IOException {
        validateSource(source);
        synchronized (LEASE_LOCK) {
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return;
            checkDirectory();
            Files.deleteIfExists(directory.resolve(source + SUFFIX));
        }
    }

    synchronized String load(String source) throws IOException {
        validateSource(source);
        synchronized (LEASE_LOCK) { return loadOne(source); }
    }

    synchronized Saved loadWithRevision(String source) throws IOException {
        validateSource(source);
        synchronized (LEASE_LOCK) { return new Saved(loadOne(source), currentRevision(source)); }
    }

    synchronized long revision(String source) {
        validateSource(source);
        synchronized (LEASE_LOCK) { return currentRevision(source); }
    }

    synchronized void revoke(String source) throws IOException {
        validateSource(source);
        synchronized (LEASE_LOCK) {
            REVISIONS.put(new LeaseKey(directory, source), currentRevision(source) + 1);
            remove(source);
        }
    }

    synchronized boolean putIfCurrent(String source, String cookie, long revision) throws IOException {
        validateSource(source);
        synchronized (LEASE_LOCK) {
            if (currentRevision(source) != revision) return false;
            put(source, cookie);
            return true;
        }
    }

    /** A successful replacement invalidates older session writers and returns the new lease. */
    synchronized long replaceIfCurrent(String source, String cookie, long revision) throws IOException {
        validateSource(source);
        synchronized (LEASE_LOCK) {
            if (currentRevision(source) != revision) return -1;
            put(source, cookie);
            long next = revision + 1;
            REVISIONS.put(new LeaseKey(directory, source), next);
            return next;
        }
    }

    /** An expired old account must not remove a newer account saved by another session. */
    synchronized boolean revokeIfCurrent(String source, long revision) throws IOException {
        validateSource(source);
        synchronized (LEASE_LOCK) {
            if (currentRevision(source) != revision) return false;
            revoke(source);
            return true;
        }
    }

    private long currentRevision(String source) { return REVISIONS.getOrDefault(new LeaseKey(directory, source), 0L); }

    private String loadOne(String source) throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return "";
        checkDirectory();
        Path path = directory.resolve(source + SUFFIX);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return "";
        return readCookie(path);
    }

    private String readCookie(Path path) throws IOException {
        String cookie = decrypt(readBounded(path, MAX_FILE));
        try {
            cookie = validatedCookie(cookie);
            if (cookie.isBlank()) throw failure();
            return cookie;
        } catch (IllegalArgumentException ignored) { throw failure(); }
    }

    private byte[] encrypt(String cookie) throws IOException {
        try {
            byte[] plain = cookie.getBytes(StandardCharsets.UTF_8);
            if (windows) {
                byte[] encrypted = Crypt32Util.cryptProtectData(plain);
                byte[] out = new byte[encrypted.length + 1]; out[0] = 'W';
                System.arraycopy(encrypted, 0, out, 1, encrypted.length);
                return out;
            }
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(true), "AES"), new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(plain);
            byte[] out = new byte[1 + nonce.length + encrypted.length]; out[0] = 'P';
            System.arraycopy(nonce, 0, out, 1, nonce.length);
            System.arraycopy(encrypted, 0, out, 1 + nonce.length, encrypted.length);
            return out;
        } catch (GeneralSecurityException | RuntimeException | LinkageError error) { throw failure(); }
    }

    private String decrypt(byte[] input) throws IOException {
        try {
            byte[] plain;
            if (windows && input.length > 1 && input[0] == 'W') {
                plain = Crypt32Util.cryptUnprotectData(Arrays.copyOfRange(input, 1, input.length));
            } else if (!windows && input.length >= 29 && input[0] == 'P') {
                var cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(false), "AES"),
                        new GCMParameterSpec(128, input, 1, 12));
                plain = cipher.doFinal(input, 13, input.length - 13);
            } else throw failure();
            if (plain.length > MAX_COOKIE) throw failure();
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | RuntimeException | LinkageError error) { throw failure(); }
    }

    private byte[] key(boolean create) throws IOException {
        synchronized (KEY_LOCK) {
            Path keyFile = directory.resolve(".key");
            if (!Files.exists(keyFile, LinkOption.NOFOLLOW_LINKS)) {
                if (!create) throw failure();
                byte[] created = new byte[32]; random.nextBytes(created);
                Path temporary = Files.createTempFile(directory, ".key-", ".tmp",
                        PosixFilePermissions.asFileAttribute(PRIVATE_FILE));
                try {
                    Files.write(temporary, created, StandardOpenOption.TRUNCATE_EXISTING);
                    try { Files.createLink(keyFile, temporary); }
                    catch (FileAlreadyExistsException ignored) { /* Another process installed its key. */ }
                } finally { Files.deleteIfExists(temporary); }
            }
            if (!Files.isRegularFile(keyFile, LinkOption.NOFOLLOW_LINKS)
                    || !PRIVATE_FILE.containsAll(Files.getPosixFilePermissions(keyFile, LinkOption.NOFOLLOW_LINKS))) throw failure();
            byte[] bytes = readBounded(keyFile, 32);
            if (bytes.length != 32) throw failure();
            return bytes;
        }
    }

    private void prepareDirectory() throws IOException {
        if (!windows && !FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) throw failure();
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            if (windows) Files.createDirectories(directory);
            else Files.createDirectories(directory, PosixFilePermissions.asFileAttribute(PRIVATE_DIRECTORY));
        }
        checkDirectory();
    }

    private void checkDirectory() throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw failure();
        if (!windows && (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")
                || !PRIVATE_DIRECTORY.containsAll(Files.getPosixFilePermissions(directory, LinkOption.NOFOLLOW_LINKS)))) throw failure();
    }

    private void writeAtomic(Path destination, byte[] bytes) throws IOException {
        Path temporary = windows ? Files.createTempFile(directory, ".music-", ".tmp")
                : Files.createTempFile(directory, ".music-", ".tmp", PosixFilePermissions.asFileAttribute(PRIVATE_FILE));
        try {
            Files.write(temporary, bytes, StandardOpenOption.TRUNCATE_EXISTING);
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static byte[] readBounded(Path file, int limit) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > limit) throw failure();
        try (var stream = Files.newInputStream(file)) {
            byte[] bytes = stream.readNBytes(limit + 1);
            if (bytes.length > limit) throw failure();
            return bytes;
        }
    }

    private static boolean validSource(String source) { return source != null && source.matches("[a-z0-9_-]{1,40}"); }
    private static void validateSource(String source) {
        if (!validSource(source)) throw new IllegalArgumentException("Invalid music source");
    }
    private static String validatedCookie(String cookie) {
        String result = MusicHttp.cookie(cookie);
        if (result.getBytes(StandardCharsets.UTF_8).length > MAX_COOKIE)
            throw new IllegalArgumentException("Invalid music credentials");
        return result;
    }
    private static IOException failure() { return new IOException("Music credential storage failed"); }
}
