// Modified for SHBUI by dishanhai. See META-INF/third-party/muonium-player-2.6.0/SOURCE.
package com.muoniumplayer.core.viper;

import com.muoniumplayer.core.settings.ConfigPaths;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

/** Installs each available bundled preset once without replacing user files. */
public final class ViperBuiltin {
    static final int BUNDLE_VERSION = 1;
    static final String BUNDLE_MARKER = ".builtin-bundle";
    static final String RECEIPTS = ".builtin-preset-receipts";
    private static final String MIGRATED = ".v1-migrated";
    private static final String RES_PRESETS = "/com/muoniumplayer/viper/presets/";
    private static final String[] LEGACY_PRESETS = {
            "【推荐】Dolby Pro Logic II Studio.json", "【黑钻蝰蛇】.json",
            "尊享旗舰·空间音效.json", "至臻-沉浸环绕音效.json", "酷狗-低音.json",
            "酷狗3D丽音[重制版].json", "酷狗HiFi.json", "黑钻蝰蛇耳机.json"
    };
    private static final String[] ROOM_PRESETS = {
            "SHBUI - Surrey Room A.json", "SHBUI - Surrey Room B.json"
    };
    private static final Object LOCK = new Object();

    private ViperBuiltin() {
    }

    /** Called on refresh so newly supplied legacy kernels are discovered. */
    public static void ensureProvisioned() {
        try {
            provision(ConfigPaths.VIPER_DIR, BUNDLE_VERSION);
        } catch (IOException | RuntimeException failure) {
            System.out.println("[MuoniumPlayer/ViPER] 内置预设铺设失败：" + failure);
        }
    }

    /** The v1 marker is read for migration, never advanced on partial success. */
    static int provision(File viperDir, int version) throws IOException {
        synchronized (LOCK) {
            Path root = viperDir.toPath();
            Files.createDirectories(root);
            try (FileChannel channel = FileChannel.open(root.resolve(".builtin-provision.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                Path receipts = root.resolve(RECEIPTS);
                Files.createDirectories(receipts);
                migrateVersionOne(root, receipts);
                int copied = 0;
                for (String name : ROOM_PRESETS) {
                    copied += install(root, receipts, name);
                }
                for (String name : LEGACY_PRESETS) {
                    if (legacyKernelExists(root, name) || presetExists(root, name)) {
                        copied += install(root, receipts, name);
                    }
                }
                return copied;
            }
        }
    }

    private static void migrateVersionOne(Path root, Path receipts) throws IOException {
        Path migrated = receipts.resolve(MIGRATED);
        if (Files.isRegularFile(migrated, LinkOption.NOFOLLOW_LINKS)) return;
        if (readVersion(root.resolve(BUNDLE_MARKER)) >= 1) {
            // A v1 marker cannot distinguish an earlier deletion from a missing
            // copy. Treat kernels present at migration as previously handled.
            for (String name : LEGACY_PRESETS) {
                if (legacyKernelExists(root, name) || presetExists(root, name)) {
                    createReceipt(receipts.resolve(name));
                }
            }
        }
        createReceipt(migrated);
    }

    private static boolean legacyKernelExists(Path root, String name) {
        return Files.isRegularFile(root.resolve("kernels").resolve(
                name.substring(0, name.length() - 5) + ".irs"));
    }

    private static boolean presetExists(Path root, String name) {
        Path directory = root.resolve("presets");
        return Files.isRegularFile(directory.resolve(name), LinkOption.NOFOLLOW_LINKS)
                || Files.isRegularFile(directory.resolve(name.substring(0, name.length() - 5) + ".xml"),
                        LinkOption.NOFOLLOW_LINKS);
    }

    private static int install(Path root, Path receipts, String name) {
        Path receipt = receipts.resolve(name);
        if (Files.isRegularFile(receipt, LinkOption.NOFOLLOW_LINKS)) return 0;
        Path directory = root.resolve("presets");
        Path target = directory.resolve(name);
        Path xml = directory.resolve(name.substring(0, name.length() - 5) + ".xml");
        Path temporary = null;
        boolean created = false;
        Object fileKey = null;
        try {
            Files.createDirectories(directory);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                    || Files.exists(xml, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isRegularFile(xml, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("预设目标不是普通文件：" + target);
                }
                createReceipt(receipt);
                return 0;
            }
            try (InputStream in = ViperBuiltin.class.getResourceAsStream(RES_PRESETS + name)) {
                if (in == null) throw new IOException("内置预设资源缺失：" + name);
                temporary = Files.createTempFile(directory, ".viper-builtin-", ".tmp");
                Files.copy(in, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            fileKey = Files.readAttributes(temporary, BasicFileAttributes.class).fileKey();
            try {
                publish(target, temporary, Files::createLink);
                created = true;
            } catch (FileAlreadyExistsException collision) {
                if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) throw collision;
            }
            try {
                createReceipt(receipt);
            } catch (IOException | RuntimeException failure) {
                if (created && Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    boolean sameFile = Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)
                            ? Files.isSameFile(target, temporary)
                            : fileKey != null && Objects.equals(fileKey,
                                    Files.readAttributes(target, BasicFileAttributes.class,
                                            LinkOption.NOFOLLOW_LINKS).fileKey());
                    if (sameFile) Files.delete(target);
                }
                throw failure;
            }
            return created ? 1 : 0;
        } catch (IOException | RuntimeException failure) {
            System.out.println("[MuoniumPlayer/ViPER] 内置预设写入失败（" + name + "）：" + failure);
            return 0;
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException failure) {
                    System.out.println("[MuoniumPlayer/ViPER] 临时文件清理失败：" + failure);
                }
            }
        }
    }

    @FunctionalInterface
    interface LinkCreator {
        Path create(Path target, Path existing) throws IOException;
    }

    /** Publish complete content without replacing an existing user file. */
    static void publish(Path target, Path temporary, LinkCreator link) throws IOException {
        try {
            // Atomic no-replace on providers supporting hard links.
            link.create(target, temporary);
        } catch (FileAlreadyExistsException collision) {
            throw collision;
        } catch (IOException | UnsupportedOperationException unavailable) {
            // The process/file locks serialize our installers; a regular move
            // keeps Java's no-replace contract on filesystems without links.
            // ATOMIC_MOVE is deliberately omitted: its existing-target
            // behavior is provider-specific and may overwrite user content.
            Files.move(temporary, target);
        }
    }

    private static void createReceipt(Path receipt) throws IOException {
        try {
            Files.createFile(receipt);
        } catch (FileAlreadyExistsException exists) {
            if (!Files.isRegularFile(receipt, LinkOption.NOFOLLOW_LINKS)) throw exists;
        }
    }

    private static int readVersion(Path marker) {
        try {
            return Integer.parseInt(Files.readString(marker, StandardCharsets.UTF_8).trim());
        } catch (IOException | NumberFormatException failure) {
            return -1;
        }
    }
}
