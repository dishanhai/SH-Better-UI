// Modified for SHBUI by dishanhai. See META-INF/third-party/muonium-player-2.6.0/SOURCE.
package com.muoniumplayer.core.viper.dsp;

import com.muoniumplayer.core.settings.ConfigPaths;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * VHE — Virtual Headphone / Virtual Surround (耳机环绕 / 虚拟声场).
 *
 * Based on NEURAX-FX/ViPERDSP {@code viper/effects/VHE.{h,cpp}}.
 *
 * Local float32 impulse responses override the bundled symmetrical HRIR pair.
 * An incomplete local pair or an unsupported sample rate leaves the effect bypassed.
 */
public final class VHE {

    /** Upstream {@code VIPER_DEFAULT_SAMPLING_RATE}. */
    private static final int DEFAULT_SAMPLING_RATE = 44100;
    /** Upstream {@code conv_size_} / segment size passed to LoadKernel. */
    private static final int CONV_SIZE = 4096;
    // Calibration of the original VHE tables, indexed by the selected effect level.
    private static final float[] USER_GAIN_44100 = {2.94595f, .944061f, 1.544582f, 1.584257f, 1.466681f};
    private static final float[] USER_GAIN_48000 = {2.94595f, .944061f, 1.531516f, 1.567789f, 1.487227f};
    private boolean enable_;

    private int samplingRate_;
    private int effectLevel_;
    private int convSize_;

    private final PConvSingle convLeft_ = new PConvSingle();
    private final PConvSingle convRight_ = new PConvSingle();
    private final PConvSingle crossLeft_ = new PConvSingle();
    private final PConvSingle crossRight_ = new PConvSingle();
    private final float[] crossBuffer_ = new float[CONV_SIZE * 2];
    private final Path userDirectory_;
    private final Function<String, InputStream> resources_;
    private boolean bundled_;
    private final WaveBuffer bufA_;
    private final WaveBuffer bufB_;

    public VHE() {
        this(ConfigPaths.VIPER_DIR.toPath().resolve("vhe"), VHE.class::getResourceAsStream);
    }

    VHE(Path userDirectory, Function<String, InputStream> resources) {
        userDirectory_ = userDirectory;
        resources_ = resources;
        enable_ = false;
        samplingRate_ = DEFAULT_SAMPLING_RATE;
        effectLevel_ = 0;
        convSize_ = 0;
        bufA_ = new WaveBuffer(2, 0x1000);
        bufB_ = new WaveBuffer(2, 0x1000);
        reset();
    }

    /**
     * Mirror of {@code uint32_t VHE::Process(const float*, float*, uint32_t)}.
     *
     * @return number of frames written into {@code dest} (equals {@code frameSize} on the
     *         bypass / disabled path).
     */
    public int process(final float[] source, final float[] dest, final int frameSize) {
        if (enable_ && convLeft_.instanceUsable() && convRight_.instanceUsable()) {
            if (bufA_.pushSamples(source, frameSize) != 0) {
                while (bufA_.getBufferOffset() > convSize_) {
                    final float[] buffer = bufA_.getBuffer();
                    if (bundled_) {
                        System.arraycopy(buffer, 0, crossBuffer_, 0, convSize_ * 2);
                        crossLeft_.convolveInterleaved(crossBuffer_, 0);
                        crossRight_.convolveInterleaved(crossBuffer_, 1);
                    }
                    convLeft_.convolveInterleaved(buffer, 0);
                    convRight_.convolveInterleaved(buffer, 1);
                    if (bundled_) {
                        for (int i = 0; i < convSize_; i++) {
                            buffer[i * 2] += crossBuffer_[i * 2 + 1];
                            buffer[i * 2 + 1] += crossBuffer_[i * 2];
                        }
                    }
                    bufB_.pushSamples(buffer, convSize_);
                    bufA_.popSamples(convSize_, true);
                }
                return bufB_.popSamples(dest, frameSize, false);
            }
        }
        if (source != dest) System.arraycopy(source, 0, dest, 0, frameSize * 2);
        return frameSize;
    }

    /** Mirror of {@code void VHE::Reset()}. */
    public void reset() {
        bufA_.reset();
        bufB_.reset();

        convLeft_.reset();
        convLeft_.unloadKernel();
        convRight_.reset();
        convRight_.unloadKernel();
        crossLeft_.unloadKernel();
        crossRight_.unloadKernel();
        bundled_ = false;

        if (!enable_ || effectLevel_ < 0 || effectLevel_ >= USER_GAIN_44100.length
                || (samplingRate_ != 44100 && samplingRate_ != 48000)) return;

        String prefix = "L" + effectLevel_ + "_" + samplingRate_;
        Path left = userDirectory_.resolve(prefix + "_L.f32");
        Path right = userDirectory_.resolve(prefix + "_R.f32");
        boolean noOverrides = Files.notExists(left, LinkOption.NOFOLLOW_LINKS)
                && Files.notExists(right, LinkOption.NOFOLLOW_LINKS);
        if (noOverrides) {
            String base = "/assets/sh_better_ui/audio/vhe/" + prefix;
            float[] near = loadResource(base + "_near.f32");
            float[] far = loadResource(base + "_far.f32");
            if (near == null || far == null) return;
            convLeft_.loadKernel(near, near.length, CONV_SIZE);
            convRight_.loadKernel(near, near.length, CONV_SIZE);
            crossLeft_.loadKernel(far, far.length, CONV_SIZE);
            crossRight_.loadKernel(far, far.length, CONV_SIZE);
            bundled_ = true;
        } else {
            float[] arrLeft = loadF32(prefix + "_L.f32");
            float[] arrRight = loadF32(prefix + "_R.f32");
            if (arrLeft == null || arrRight == null) return;
            float gain = (samplingRate_ == 44100 ? USER_GAIN_44100 : USER_GAIN_48000)[effectLevel_];
            convLeft_.loadKernel(arrLeft, gain, arrLeft.length, CONV_SIZE);
            convRight_.loadKernel(arrRight, gain, arrRight.length, CONV_SIZE);
        }
        convSize_ = CONV_SIZE;
    }

    /** Mirror of {@code bool VHE::GetEnable() const}. */
    public boolean getEnable() {
        return enable_;
    }

    /** Mirror of {@code void VHE::SetEnable(bool)}. */
    public void setEnable(final boolean enable) {
        if (enable_ != enable) {
            enable_ = enable;
            reset();
        }
    }

    /** Mirror of {@code void VHE::SetEffectLevel(uint32_t)}. */
    public void setEffectLevel(final int value) {
        if (effectLevel_ != value) {
            if (value < 5) {
                effectLevel_ = value;
                reset();
            }
        }
    }

    /** Mirror of {@code void VHE::SetSamplingRate(uint32_t)}. */
    public void setSampleRate(final int samplingRate) {
        if (samplingRate_ != samplingRate) {
            samplingRate_ = samplingRate;
            reset();
        }
    }

    /**
     * Loads a locally installed little-endian float32 impulse response.
     *
     * @return the float array, or {@code null} if the resource is missing or unreadable.
     *         Callers treat {@code null} as "do not load kernel" (bypass path); this
     *         method never throws.
     */
    private float[] loadF32(final String name) {
        try (InputStream in = Files.newInputStream(userDirectory_.resolve(name))) {
            return readF32(in);
        } catch (final IOException | SecurityException ignored) {
            return null;
        }
    }

    private float[] loadResource(String name) {
        try (InputStream in = resources_.apply(name)) {
            return in == null ? null : readF32(in);
        } catch (IOException | SecurityException ignored) {
            return null;
        }
    }

    private static float[] readF32(InputStream in) throws IOException {
        final int maxBytes = 64 * 1024;
        final byte[] bytes = in.readNBytes(maxBytes + 1);
        if (bytes.length < 2 * Float.BYTES || bytes.length > maxBytes || bytes.length % Float.BYTES != 0) {
            return null;
        }
        final ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        final float[] out = new float[bytes.length / Float.BYTES];
        bb.asFloatBuffer().get(out);
        for (float tap : out) if (!Float.isFinite(tap)) return null;
        return out;
    }
}
