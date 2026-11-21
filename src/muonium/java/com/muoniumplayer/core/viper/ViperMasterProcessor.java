package com.muoniumplayer.core.viper;

import com.muoniumplayer.core.settings.ConfigPaths;
import com.muoniumplayer.core.viper.dsp.ViPER;
import com.muoniumplayer.core.viper.dsp.ViPERParams;
import com.muoniumplayer.core.viper.dsp.WavReader;

import repackage.com.jsyn.engine.SynthesisEngine;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ViPER 主链的实时处理器：驱动 {@link ViPER} 全链引擎（ViPERDSP 的 Java 移植）。
 *
 * <p><b>处理时机</b>：由 JSyn 引擎线程在写完一个缓冲、写下设备<b>之前</b>调用
 * （{@link SynthesisEngine.OutputProcessor}）。调用把交织 double 缓冲转成 float、
 * 交给引擎（冻结的立体声链），再写回——所有 DSP（包括最后一环的双软件限幅器）都在引擎内完成。</p>
 *
 * <p><b>线程模型</b>：控制线程（{@link ViperRuntime} 重载）只做三件事——映射参数、
 * 读磁盘资源（卷积核 WAV / DDC 系数文件）、把 {@link Pending} 通过原子引用发布；
 * 音频线程在每个缓冲开头消费 {@link Pending}（装载核/DDC、applyParams）、按需更新采样率。
 * 处理函数实时安全：稳态零分配、无锁、无日志；参数变化在下个缓冲生效。</p>
 *
 * <p><b>失败语义</b>：处理链一旦抛异常即整体置为直通（{@code active=false}）并打印一次，
 * 之后不再重试——保证音效任何故障都不会影响出声。</p>
 */
public final class ViperMasterProcessor implements SynthesisEngine.OutputProcessor {

    /** 音频线程的 float 转换缓冲初始容量（帧 × 2 声道；不足时一次性扩容）。 */
    private static final int INITIAL_BUFFER_FLOATS = 8192;

    private final ViPER engine = new ViPER();

    private volatile boolean active;
    private int lastSampleRate = -1;
    private float[] floatBuffer = new float[INITIAL_BUFFER_FLOATS];

    private final File viperDir;
    private FileKey kernelKey;
    private Kernel cachedKernel;
    private FileKey ddcKey;
    private ViperDdcFile.Vdc cachedDdc;
    private int nextKernelId = 1;

    // The current and applied snapshots belong exclusively to the audio thread.
    private Kernel selectedKernel;
    private Kernel appliedKernel;
    private WavReader.WavData appliedWave;
    private ViperDdcFile.Vdc appliedDdc;

    private final AtomicReference<Pending> pending = new AtomicReference<>();

    /** 控制线程预构建、音频线程消费的一次性变更集合。 */
    private static final class Pending {
        ViPERParams params;

        Kernel kernel;
        ViperDdcFile.Vdc ddc;
    }

    private record FileKey(String path, long size, FileTime modified, Object identity) {}

    private static final class Kernel {
        final WavReader.WavData local;
        final ViperRoomKernels.Pair room;
        final int id44100;
        final int id48000;

        Kernel(WavReader.WavData local, ViperRoomKernels.Pair room, int id44100, int id48000) {
            this.local = local;
            this.room = room;
            this.id44100 = id44100;
            this.id48000 = id48000;
        }

        WavReader.WavData at(int rate) { return room == null ? local : room.at(rate); }
        int id(int rate) { return room != null && rate == 48000 ? id48000 : id44100; }
    }

    public ViperMasterProcessor() {
        this(ConfigPaths.VIPER_DIR);
    }

    ViperMasterProcessor(File viperDir) {
        this.viperDir = viperDir;
    }

    /**
     * 装载（或卸载）一条预设：映射全量参数，预读卷积核/DDC 资源，发布到音频线程。
     *
     * @param enabled 总开关；为 false 时不处理任何样本
     * @param preset  当前预设；为 {@code null} 时不处理
     */
    public synchronized void configure(boolean enabled, ViperPreset preset) {
        if (!enabled || preset == null) {
            active = false;
            pending.set(null);
            return;
        }

        ViperParamMapper.Mapped mapped = ViperParamMapper.map(preset);
        Pending next = new Pending();
        next.params = mapped.params;

        // Each publication is complete, even when an earlier publication was not consumed.
        String kernel = mapped.convolverKernelFile == null ? "" : mapped.convolverKernelFile.trim();
        if (mapped.params.convolver.enable && !kernel.isEmpty()) {
            next.kernel = prepareKernel(kernel);
            if (next.kernel != null && next.kernel.room != null) {
                // Measured room responses already contain their interaural delays.
                next.params.convolver.crossDelayMs = 0.0f;
            }
        }

        // —— DDC（仅当 DDC 启用且设备名非空；键变化才重新装载） ——
        String ddc = mapped.ddcDevice == null ? "" : mapped.ddcDevice.trim();
        if (mapped.params.ddc.enable && !ddc.isEmpty()) {
            next.ddc = prepareDdc(ddc);
        }

        pending.set(next);
        active = true;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public void process(double[] interleaved, int channels, int sampleRate) {
        if (!active || interleaved == null || channels <= 0) {
            return;
        }
        try {
            boolean rateChanged = sampleRate != lastSampleRate;
            if (rateChanged) {
                engine.setSampleRate(sampleRate);
                lastSampleRate = sampleRate;
            }

            Pending next = pending.getAndSet(null);
            if (rateChanged || next != null) engine.resetAllEffects();
            if (next != null) {
                engine.applySnapshot(next.params);
                selectedKernel = next.kernel;
                if (next.ddc != appliedDdc) {
                    engine.unloadDdcCoefficients();
                    if (next.ddc != null) {
                        engine.loadDdcCoefficients(next.ddc.sections44100, next.ddc.sections48000,
                                next.ddc.sectionCount());
                    }
                    appliedDdc = next.ddc;
                }
            }
            WavReader.WavData wave = selectedKernel == null ? null : selectedKernel.at(sampleRate);
            if (selectedKernel != appliedKernel || wave != appliedWave) {
                engine.unloadConvolverKernel();
                if (wave != null) {
                    engine.loadConvolverKernel(wave.samples, wave.frameCount, wave.channels,
                            selectedKernel.id(sampleRate));
                }
                appliedKernel = selectedKernel;
                appliedWave = wave;
            }

            final int frames = interleaved.length / channels;
            if (frames == 0) {
                return;
            }
            final int needed = frames * 2;
            if (floatBuffer.length < needed) {
                floatBuffer = new float[needed];
            }
            final float[] buffer = floatBuffer;

            int index = 0;
            for (int frame = 0; frame < frames; frame++) {
                buffer[frame * 2] = (float) interleaved[index];
                buffer[frame * 2 + 1] = (float) interleaved[channels > 1 ? index + 1 : index];
                index += channels;
            }

            engine.process(buffer, frames);

            index = 0;
            for (int frame = 0; frame < frames; frame++) {
                interleaved[index] = buffer[frame * 2];
                if (channels > 1) {
                    interleaved[index + 1] = buffer[frame * 2 + 1];
                }
                index += channels;
            }
        } catch (Throwable failure) {
            // 音效故障绝不影响出声：整体切回直通。
            active = false;
            System.out.println("[MuoniumPlayer/ViPER] 处理链异常，已切换为直通：" + failure);
        }
    }

    /** 一行状态（供调试）。 */
    public String statusLine() {
        return "engine=" + (active ? "active" : "bypass")
                + ", rate=" + engine.getSampleRate()
                + ", frames=" + engine.getProcessedFrames()
                + ", kernel=" + (engine.getConvolverKernelID() != 0 ? "loaded" : "none");
    }

    // —— 资源路径解析（控制线程） ——

    private Kernel prepareKernel(String name) {
        File file = resolveKernelFile(name);
        if (file != null) {
            FileKey key = fileKey(file);
            if (key == null) return null;
            if (key.equals(kernelKey)) return cachedKernel;
            WavReader.WavData wav = new WavReader.WavData();
            if (!WavReader.readWavFile(file.getAbsolutePath(), wav) || !validWave(wav)
                    || !key.equals(fileKey(file))) return null;
            cachedKernel = new Kernel(wav, null, allocateKernelId(), 0);
            kernelKey = key;
            return cachedKernel;
        }
        if (!ViperRoomKernels.contains(name)) return null;
        FileKey key = new FileKey("builtin:" + name, 0, null, null);
        if (key.equals(kernelKey)) return cachedKernel;
        ViperRoomKernels.Pair pair = ViperRoomKernels.load(name);
        if (pair == null || !validWave(pair.at(44100)) || !validWave(pair.at(48000))) return null;
        cachedKernel = new Kernel(null, pair, allocateKernelId(), allocateKernelId());
        kernelKey = key;
        return cachedKernel;
    }

    private ViperDdcFile.Vdc prepareDdc(String name) {
        File file = resolveDdcFile(name);
        FileKey key = file == null ? null : fileKey(file);
        if (key == null) return null;
        if (key.equals(ddcKey)) return cachedDdc;
        ViperDdcFile.Vdc parsed = ViperDdcFile.parse(file);
        if (parsed == null || !finite(parsed.sections44100) || !finite(parsed.sections48000)
                || !key.equals(fileKey(file))) return null;
        ddcKey = key;
        cachedDdc = parsed;
        return parsed;
    }

    private int allocateKernelId() {
        int id = nextKernelId++;
        if (id == 0) id = nextKernelId++;
        return id;
    }

    private static boolean validWave(WavReader.WavData wave) {
        return wave != null && wave.frameCount >= 16 && wave.samples != null
                && (wave.channels == 1 || wave.channels == 2 || wave.channels == 4)
                && (long) wave.frameCount * wave.channels == wave.samples.length && finite(wave.samples);
    }

    private static boolean finite(float[] samples) {
        for (float sample : samples) if (!Float.isFinite(sample)) return false;
        return true;
    }

    private static FileKey fileKey(File file) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            return attributes.isRegularFile()
                    ? new FileKey(file.getAbsolutePath(), attributes.size(), attributes.lastModifiedTime(), attributes.fileKey())
                    : null;
        } catch (java.io.IOException | SecurityException failure) {
            return null;
        }
    }

    private static boolean present(File file) {
        return !Files.notExists(file.toPath(), LinkOption.NOFOLLOW_LINKS);
    }

    /** 卷积核候选：绝对路径 → {@code viper/kernels/} → {@code viper/}。 */
    private File resolveKernelFile(String name) {
        File direct = new File(name);
        if (direct.isAbsolute() && present(direct)) {
            return direct;
        }
        File inKernels = new File(new File(viperDir, "kernels"), name);
        if (present(inKernels)) {
            return inKernels;
        }
        File inViper = new File(viperDir, name);
        return present(inViper) ? inViper : null;
    }

    /** DDC 候选：绝对路径 → {@code viper/ddc/} → {@code viper/}（自动补 {@code .vdc}）。 */
    private File resolveDdcFile(String name) {
        String fileName = name.endsWith(".vdc") ? name : name + ".vdc";
        File direct = new File(fileName);
        if (direct.isAbsolute() && direct.isFile()) {
            return direct;
        }
        File inDdc = new File(new File(viperDir, "ddc"), fileName);
        if (inDdc.isFile()) {
            return inDdc;
        }
        File inViper = new File(viperDir, fileName);
        return inViper.isFile() ? inViper : null;
    }
}
