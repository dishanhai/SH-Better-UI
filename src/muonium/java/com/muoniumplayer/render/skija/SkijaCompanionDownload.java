package com.muoniumplayer.render.skija;

import com.muoniumplayer.core.utils.network.HttpDownloadRace;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 按玩家请求在后台下载、校验并安装本机平台对应的 Skija 伴生包。
 * 安装完成后由玩家选择何时重开游戏。
 *
 * <h2>钉死字节数与 sha256</h2>
 * 下载的是「会跑起来变成可执行代码的二进制」，和更新清单一样必须可校验：
 * {@link #PACKS} 每个平台都钉住字节数与 sha256，对不上换下一个镜像重试，全失败才放弃。
 * 换包时三处必须一起改：清单仓库里的 zip、本类的常量、{@code dist/skija-companion} 里的产物；
 * {@code tools/publish-skija-companion.ps1} 会交叉校验这三处，静态测试也会在产物漂移时报红。
 *
 * <h2>落点</h2>
 * 解到 {@link SkijaNatives#nativeRoot()}（{@code config/muonium/native/skija/}），布局与
 * 「用户手动解压伴生包」完全一致：{@code <os>/<arch>/{libskija.*, skija.version}}。
 * 解压先落在 {@code .incoming-<时间戳>/} 暂存目录，整包校验通过后才并入——扫描逻辑不看
 * 点开头的目录，所以写到一半的文件不会进加载试探。安卓上这个目录在外置存储、
 * 链接器不允许从那里 dlopen，那是 {@link SkijaNatives} 的暂存机制负责的事（搬去 libjvm
 * 同源目录再加载），本类不重复处理。
 *
 * <h2>开发期替代源</h2>
 * {@code -Dmuonium.skija.companionDir=...} 可从本地目录取包（例如构建产物
 * {@code dist/skija-companion}），校验（字节数 + sha256）与网络路径完全一致，方便离线核对。
 */
final class SkijaCompanionDownload {

    /**
     * 下载镜像链，前五路 GitHub 系 + 两路官网系（Cloudflare）：Pages 直链与 R2 对象存储直链。
     * Pages {@code dl/} 与 R2 {@code fenfa} 桶是同一批文件的镜像（R2 无 25MiB 单文件限制），
     * 两条互为备份——国内对 CF 边缘的连接波动大，多一路就多一分命中的机会。
     */
    static final List<String> MIRRORS = List.of(
            "https://file.melodifymc.cc.cd/skija-companion/",
            "https://gh-proxy.com/https://raw.githubusercontent.com/zihe114514/MuoniumPlayer-updates/main/skija-companion/",
            "http://muoniummc.cc.cd/dl/skija-companion/",
            "https://melodifymc.cc.cd/dl/skija-companion/",
            "https://ghfast.top/https://raw.githubusercontent.com/zihe114514/MuoniumPlayer-updates/main/skija-companion/",
            "https://gcore.jsdelivr.net/gh/zihe114514/MuoniumPlayer-updates@main/skija-companion/",
            "https://ghproxy.net/https://raw.githubusercontent.com/zihe114514/MuoniumPlayer-updates/main/skija-companion/",
            "https://raw.githubusercontent.com/zihe114514/MuoniumPlayer-updates/main/skija-companion/");

    /** 开发期替代源：从一个本地目录取包，不经网络。 */
    static final String PROP_LOCAL_DIR = "muonium.skija.companionDir";

    /** 暂存目录前缀。扫描层对「点开头」的目录一律不看，两边靠这个约定对齐。 */
    private static final String INCOMING_PREFIX = ".incoming-";

    /** 一个平台对应的一份伴生包：文件名 + 字节数 + sha256，三个都必须精确。 */
    record Pack(String platform, String fileName, long bytes, String sha256) {
    }

    /**
     * 七份伴生包，与 {@code dist/skija-companion} 的 {@code packageSkijaCompanion}
     * 产物同名。字节数与 sha256 以清单仓库里实际托管的文件为准（发布时由
     * {@code tools/publish-skija-companion.ps1} 交叉校验）。安卓两份 2026-10-05
     * 真机复测通过（MobileGlues 渲染器 + 关闭 ANGLE 的组合）。
     * Windows 那份 2026-10-10 起接管原「主包内置」的职责。
     */
    static final List<Pack> PACKS = List.of(
            new Pack("windows/x64", "muonium-skija-windows-x64-0.143.17.zip", 10_560_236L,
                    "1d74e9ac8c5249c9f9d833b3c9e02c542c88185f07901ab4f4f02b446ef2e747"),
            new Pack("android/arm64", "muonium-skija-android-arm64-0.143.17.zip", 11_944_117L,
                    "83e2341715365f5680afa35501913463bacad68e004e778df21eb710f79f5ca5"),
            new Pack("android/x64", "muonium-skija-android-x64-0.143.17.zip", 12_169_687L,
                    "50507d6cda88897c0aafa258cb63a348d6318e51c14593175f1157eee588a60a"),
            new Pack("linux/arm64", "muonium-skija-linux-arm64-0.143.17.zip", 13_091_040L,
                    "ce10e7aab2028e1eda5c04496e382239803d4ecb73c5015fc095aef3830a723b"),
            new Pack("linux/x64", "muonium-skija-linux-x64-0.143.17.zip", 13_339_715L,
                    "e9b3c500bde60538f4e7b810f604f630b6e1c82ac0624a75c185dc3e5cd4c1c1"),
            new Pack("macos/arm64", "muonium-skija-macos-arm64-0.143.17.zip", 10_797_393L,
                    "fa2999a335ea9ab6c2d83d61d2f7591726b259a92657f5c5bae7077f3d92bdeb"),
            new Pack("macos/x64", "muonium-skija-macos-x64-0.143.17.zip", 11_334_535L,
                    "b241d8c4783b0cd23e4abf6b9af134897c6dc1b4412a3ecac7db41c4fb21dde1"));

    /** 下载状态机；提示层照它决定说什么（「缺库」分四种处境，用户动作完全不同）。 */
    enum Phase {
        /** 等待玩家发起下载。 */
        IDLE,
        PREPARING,
        /** 正在下载。 */
        DOWNLOADING,
        /** 下载完成，正在解压安装。 */
        INSTALLING,
        /** 装好了，下一次启动可用。 */
        DONE,
        /** 下载或安装失败，玩家可重试。 */
        FAILED
    }

    /**
     * 给提示层与日志的状态快照。
     *
     * @param percent 0~100；-1 表示总长度未知（拿不到 Content-Length）
     * @param detail  仅 FAILED 用：一句人话的失败原因
     */
    record State(Phase phase, int percent, String detail, Phase failedDuring) {
        State(Phase phase, int percent, String detail) {
            this(phase, percent, detail, null);
        }
    }

    private static volatile State state = new State(Phase.IDLE, 0, null);

    private SkijaCompanionDownload() {
    }

    static boolean isRestartRequired() {
        return state.phase() == Phase.DONE && !SkijaNatives.status().loaded();
    }

    /** 当前（或最近一次）的下载状态；提示层每帧读它，永远不会为 null。 */
    static State state() {
        return state;
    }

    public static State catalogState() {
        return state;
    }

    /** 本机平台对应的伴生包；表里没有的平台（如 Skija 没出包的 windows/arm64）返回 null。 */
    static Pack packFor(String mappedPlatform) {
        for (Pack pack : PACKS) {
            if (pack.platform().equals(mappedPlatform)) {
                return pack;
            }
        }
        return null;
    }

    static boolean canStart(String mappedPlatform) {
        return isIdle() && packFor(mappedPlatform) != null;
    }

    private static boolean isIdle() {
        Phase phase = state.phase();
        return phase == Phase.IDLE || phase == Phase.FAILED;
    }

    static synchronized void request(String mappedPlatform) {
        Pack pack = packFor(mappedPlatform);
        if (pack == null || !isIdle()) {
            return;
        }
        Thread worker = new Thread(() -> run(pack), "MuoniumPlayer-SkijaCompanion");
        worker.setDaemon(true);
        state = new State(Phase.PREPARING, 0, null);
        worker.start();
    }

    private static void run(Pack pack) {
        try {
            File root = SkijaNatives.nativeRoot();
            Files.createDirectories(root.toPath());
            log("开始下载 " + pack.fileName() + "（镜像链 " + MIRRORS.size() + " 条）");
            File archive = new File(root, pack.fileName() + ".part");
            state = new State(Phase.DOWNLOADING, 0, null);
            fetch(pack, archive);

            state = new State(Phase.INSTALLING, 100, null);
            deleteStaleIncoming(root);
            File incoming = new File(root, INCOMING_PREFIX + Long.toHexString(System.nanoTime()));
            try {
                unzip(archive, incoming);
                verifyExtracted(incoming, pack);
                synchronized (SkijaNatives.class) {
                    if (!SkijaNatives.status().loaded()) {
                        copyTree(incoming, root);
                    }
                }
            } finally {
                deleteRecursively(incoming);
            }
            archive.delete();

            state = new State(Phase.DONE, 100, null);
            log("已安装 " + pack.fileName() + " → " + root.getAbsolutePath() + "；下次启动时生效");
        } catch (Throwable failure) {
            state = new State(Phase.FAILED, 0, safeMessage(failure), state.phase());
            log("下载或安装失败：" + describe(failure));
        }
    }

    /**
     * 取包到 {@code archive}。本地替代源优先；否则多路竞速下载（见 {@link HttpDownloadRace}），
     * 开关关掉时退回旧的逐路串行回退（带同镜像 Range 续传）。
     */
    private static void fetch(Pack pack, File archive) throws Exception {
        String localDir = System.getProperty(PROP_LOCAL_DIR);
        if (localDir != null && !localDir.isBlank()) {
            File local = new File(localDir, pack.fileName());
            if (!local.isFile()) {
                throw new IOException("本地伴生包目录里没有 " + pack.fileName() + "：" + localDir);
            }
            verify(local, pack);
            Files.copy(local.toPath(), archive.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        if (HttpDownloadRace.enabled()) {
            List<String> urls = new ArrayList<String>(MIRRORS.size());
            for (String mirror : MIRRORS) {
                urls.add(mirror + pack.fileName());
            }
            HttpDownloadRace.fetchOnce(urls, pack.bytes(), pack.sha256(), archive, percent ->
                    state = new State(Phase.DOWNLOADING, percent, null));
            verify(archive, pack);
            return;
        }
        Exception last = null;
        for (String mirror : MIRRORS) {
            try {
                // 同镜像先续传重试（最多 3 次，download 内部按 archive 已收字节挂 Range）：
                // gh-proxy 这类代理对大文件常在中途截断但支持 Range 续传，接着拉就能拿全。
                for (int retry = 0; retry <= 3; retry++) {
                    try {
                        download(pack, mirror + pack.fileName(), archive);
                        break;
                    } catch (Exception attempt) {
                        if (retry == 3) {
                            throw attempt;
                        }
                        if (attempt instanceof IOException
                                && String.valueOf(attempt.getMessage()).contains("sha256")) {
                            Files.deleteIfExists(archive.toPath());
                        }
                    }
                }
                return;
            } catch (Exception failure) {
                last = failure;
                try {
                    Files.deleteIfExists(archive.toPath());
                } catch (Throwable ignored) {
                }
            }
        }
        throw last == null ? new IOException("没有可用的下载源") : last;
    }

    /** 单次下载：读流与校验同步走，进度按整百分比推给 {@link #state()}。支持断点续传。 */
    private static void download(Pack pack, String url, File archive) throws Exception {
        long already = archive.isFile() ? archive.length() : 0L;
        if (already > pack.bytes()) {
            throw new IOException(".part 超长：已有 " + already + " 字节，应为 " + pack.bytes());
        }
        HttpURLConnection connection = openFollowing(URI.create(url).toURL(), 0, already);
        int code = connection.getResponseCode();
        // 续传被服务端拒绝（不认 Range）时它会给全量 200：把已收部分清掉按从头下处理。
        if (already > 0L && code == 200) {
            connection.disconnect();
            Files.deleteIfExists(archive.toPath());
            connection = openFollowing(URI.create(url).toURL(), 0, 0L);
            code = connection.getResponseCode();
            already = 0L;
        }
        try {
            if (code < 200 || code >= 300 || (already > 0L && code != 206)) {
                throw new IOException("HTTP " + code);
            }
            long done = already;
            int shown = -1;
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream output = new FileOutputStream(archive, already > 0L)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    done += read;
                    if (done > pack.bytes()) {
                        throw new IOException("下超了：已收 " + done + " 字节，应为 " + pack.bytes());
                    }
                    output.write(buffer, 0, read);
                    int percent = (int) (done * 100L / pack.bytes());
                    if (percent != shown) {
                        shown = percent;
                        state = new State(Phase.DOWNLOADING, percent, null);
                    }
                }
            }
        } finally {
            connection.disconnect();
        }
        verify(archive, pack);
    }

    /** 重定向自己跟（镜像会 30x 到 CDN），最多 6 跳，与 Folia 的下载器同款。rangeFrom &gt; 0 时挂 Range 续传头。 */
    private static HttpURLConnection openFollowing(URL url, int depth, long rangeFrom) throws Exception {
        if (depth > 6) {
            throw new IOException("重定向过多");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(60_000);
        connection.setRequestProperty("User-Agent", "MuoniumPlayer-Skija/1.0");
        if (rangeFrom > 0L) {
            connection.setRequestProperty("Range", "bytes=" + rangeFrom + "-");
        }
        int code = connection.getResponseCode();
        if (code >= 300 && code < 400) {
            String location = connection.getHeaderField("Location");
            connection.disconnect();
            if (location == null) {
                throw new IOException("重定向没有 Location");
            }
            return openFollowing(new URL(url, location), depth + 1, rangeFrom);
        }
        return connection;
    }

    /** 本地文件路径的校验（开发期替代源用），与下载路径同一套判据。 */
    private static void verify(File file, Pack pack) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("本机 JRE 没有 SHA-256", impossible);
        }
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        checkAgainst(pack, file.length(), hex(digest.digest()));
    }

    /** 整包校验：字节数与 sha256 都要对上钉住的值。 */
    private static void checkAgainst(Pack pack, long bytes, String sha256) throws IOException {
        if (bytes != pack.bytes()) {
            throw new IOException("大小不符：得到 " + bytes + " 字节，应为 " + pack.bytes());
        }
        if (!pack.sha256().equalsIgnoreCase(sha256)) {
            throw new IOException("sha256 不符：得到 " + sha256);
        }
    }

    /**
     * 并入前先核对包内结构：{@code <os>/<arch>/} 下的版本标记要等于主包钉的那版、
     * 原生库文件名要对该 OS 家族。防的是「上传错了包 / 包结构变了」——
     * 这两种错按 sha256 拦不住（包本身合法，只是不是我们要的），只能按内容校验。
     */
    private static void verifyExtracted(File incoming, Pack pack) throws IOException {
        String platform = pack.platform();
        String os = SkijaNativeCatalog.osOf(platform);
        String arch = platform.substring(platform.indexOf('/') + 1);
        File dir = new File(new File(incoming, os), arch);
        File marker = new File(dir, SkijaNativeCatalog.VERSION_FILE);
        if (!marker.isFile()) {
            throw new IOException("包内缺 " + os + "/" + arch + "/" + SkijaNativeCatalog.VERSION_FILE);
        }
        String version = Files.readString(marker.toPath(), StandardCharsets.UTF_8).trim();
        if (!SkijaNatives.PINNED_SKIJA_VERSION.equals(version)) {
            throw new IOException("包内 Skija 版本是 " + version + "，本模组要 "
                    + SkijaNatives.PINNED_SKIJA_VERSION);
        }
        File library = new File(dir, SkijaNativeCatalog.libraryNameFor(platform));
        if (!library.isFile()) {
            throw new IOException("包内缺 " + library.getName());
        }
    }

    /** 清掉上一次失败/崩溃留下的暂存目录：扫描虽然不看它们，但 30 MB 一份不该堆着。 */
    private static void deleteStaleIncoming(File root) {
        File[] children = root.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory() && child.getName().startsWith(INCOMING_PREFIX)) {
                deleteRecursively(child);
            }
        }
    }

    /** 把暂存目录整棵树并入目标目录；同名文件覆盖——覆盖的正是我们要替换的那几个。 */
    private static void copyTree(File from, File to) throws IOException {
        File[] children = from.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            File target = new File(to, child.getName());
            if (child.isDirectory()) {
                Files.createDirectories(target.toPath());
                copyTree(child, target);
            } else {
                Files.copy(child.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /** 解压（自带 zip-slip 防护，与 Folia 的安装器同款形状）。 */
    private static void unzip(File archive, File target) throws Exception {
        if (!target.exists() && !target.mkdirs() && !target.isDirectory()) {
            throw new IOException("无法创建暂存目录: " + target);
        }
        String root = target.getCanonicalPath() + File.separator;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(archive)))) {
            ZipEntry entry;
            byte[] buffer = new byte[64 * 1024];
            while ((entry = zip.getNextEntry()) != null) {
                File out = new File(target, entry.getName());
                if (!out.getCanonicalPath().startsWith(root)) {
                    throw new IOException("不安全的压缩包条目: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    out.mkdirs();
                } else {
                    File parent = out.getParentFile();
                    if (parent != null) {
                        parent.mkdirs();
                    }
                    try (FileOutputStream output = new FileOutputStream(out)) {
                        int read;
                        while ((read = zip.read(buffer)) >= 0) {
                            output.write(buffer, 0, read);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        try {
            Files.deleteIfExists(file.toPath());
        } catch (Throwable ignored) {
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            out.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return out.toString();
    }

    /** 状态里给用户看的一句人话；空消息退化成异常类名。 */
    private static String safeMessage(Throwable failure) {
        String message = failure == null ? null : failure.getMessage();
        return message == null || message.isEmpty() ? failure.getClass().getSimpleName() : message;
    }

    /** 日志里带类名，排查「到底哪一步挂的」时比只有消息强。 */
    private static String describe(Throwable failure) {
        return failure.getClass().getName() + ": " + failure.getMessage();
    }

    private static void log(String message) {
        System.out.println("[MuoniumPlayer/SkijaCompanionDownload] " + message);
    }
}
