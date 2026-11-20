package com.muoniumplayer.core.viper;

import com.muoniumplayer.core.viper.dsp.ViPER;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ViperMasterProcessorTest {
    @TempDir Path directory;

    private ViperPreset preset(String kernel, String ddc) {
        return ViperPresetParser.parse("{\"schemaVersion\":2.1,"
                + "\"convolver\":{\"enable\":true,\"kernelFile\":\"" + kernel + "\"},"
                + "\"ddc\":{\"enable\":true,\"device\":\"" + ddc + "\"}}", "test");
    }

    private void kernel(String name, short amplitude) throws Exception {
        Path kernels = Files.createDirectories(directory.resolve("kernels"));
        ByteBuffer wav = ByteBuffer.allocate(44 + 64).order(ByteOrder.LITTLE_ENDIAN);
        wav.put(new byte[] {'R','I','F','F'}).putInt(100).put(new byte[] {'W','A','V','E'});
        wav.put(new byte[] {'f','m','t',' '}).putInt(16).putShort((short) 1).putShort((short) 1);
        wav.putInt(44100).putInt(88200).putShort((short) 2).putShort((short) 16);
        wav.put(new byte[] {'d','a','t','a'}).putInt(64).putShort(amplitude);
        Files.write(kernels.resolve(name), wav.array());
    }

    private void ddc(String name, float gain) throws Exception {
        Path folder = Files.createDirectories(directory.resolve("ddc"));
        String coefficients = gain + ",0,0,0,0\n";
        Files.writeString(folder.resolve(name + ".vdc"), "SR_44100:" + coefficients + "SR_48000:" + coefficients);
    }

    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static ViPER engine(ViperMasterProcessor processor) throws Exception {
        return (ViPER) field(processor, "engine");
    }

    private static int kernelId(ViperMasterProcessor processor) throws Exception {
        return engine(processor).getConvolverKernelID();
    }

    private static float ddcGain(ViperMasterProcessor processor) throws Exception {
        Object ddc = field(engine(processor), "viperDdc_");
        float[][] coeffs = (float[][]) field(ddc, "coeffsArr44100_");
        return coeffs == null ? Float.NaN : coeffs[0][0];
    }

    private static void consume(ViperMasterProcessor processor, int rate) {
        processor.process(new double[2048], 2, rate);
    }

    @Test void consecutiveConfigurationsKeepLatestCompleteResources() throws Exception {
        kernel("one.wav", (short) 1000);
        ddc("device", .25f);
        ViperMasterProcessor processor = new ViperMasterProcessor(directory.toFile());
        processor.configure(true, preset("one.wav", "device"));
        processor.configure(true, preset("one.wav", "device"));
        consume(processor, 44100);
        assertNotEquals(0, kernelId(processor));
        assertEquals(.25f, ddcGain(processor));
        kernel("two.wav", (short) 2000);
        ddc("other", .75f);
        processor.configure(true, preset("two.wav", "other"));
        processor.configure(true, preset("one.wav", "device"));
        consume(processor, 44100);
        assertNotEquals(0, kernelId(processor));
        assertEquals(.25f, ddcGain(processor));
        processor.configure(false, null);
        processor.configure(true, preset("one.wav", "device"));
        consume(processor, 44100);
        assertNotEquals(0, kernelId(processor));
        assertEquals(.25f, ddcGain(processor));
    }

    @Test void invalidNewResourcesUnloadPreviousKernelAndDdc() throws Exception {
        kernel("one.wav", (short) 1000);
        ddc("device", .25f);
        ViperMasterProcessor processor = new ViperMasterProcessor(directory.toFile());
        processor.configure(true, preset("one.wav", "device"));
        consume(processor, 44100);
        assertNotEquals(0, kernelId(processor));
        processor.configure(true, preset("missing.wav", "missing"));
        consume(processor, 44100);
        assertEquals(0, kernelId(processor));
        assertTrue(Float.isNaN(ddcGain(processor)));
    }

    @Test void modifiedLocalFileIsReloaded() throws Exception {
        kernel("one.wav", (short) 1000);
        ViperMasterProcessor processor = new ViperMasterProcessor(directory.toFile());
        processor.configure(true, preset("one.wav", ""));
        consume(processor, 44100);
        int first = kernelId(processor);
        assertNotEquals(0, first);
        Files.write(directory.resolve("kernels/one.wav"), new byte[] {1, 2, 3});
        processor.configure(true, preset("one.wav", ""));
        consume(processor, 44100);
        assertEquals(0, kernelId(processor));
    }

    @Test void bundledRoomTracksOutputRateAndLocalFileTakesPriority() throws Exception {
        ViperMasterProcessor processor = new ViperMasterProcessor(directory.toFile());
        ViperPreset room = preset("Surrey-Room-A.wav", "");
        processor.configure(true, room);
        consume(processor, 44100);
        int at44100 = kernelId(processor);
        assertNotEquals(0, at44100);
        assertEquals(0f, (float) field(field(engine(processor), "convolver_"), "cross_delay_ms_"));
        consume(processor, 48000);
        int at48000 = kernelId(processor);
        assertNotEquals(0, at48000);
        assertNotEquals(at44100, at48000);
        consume(processor, 96000);
        assertEquals(0, kernelId(processor));
        consume(processor, 44100);
        assertEquals(at44100, kernelId(processor));
        kernel("Surrey-Room-A.wav", (short) 2000);
        processor.configure(true, room);
        consume(processor, 44100);
        int local = kernelId(processor);
        assertNotEquals(0, local);
        assertEquals(.3125f, (float) field(field(engine(processor), "convolver_"), "cross_delay_ms_"));
        consume(processor, 48000);
        assertEquals(local, kernelId(processor));
        Files.write(directory.resolve("kernels/Surrey-Room-A.wav"), new byte[] {1, 2, 3});
        processor.configure(true, room);
        consume(processor, 44100);
        assertEquals(0, kernelId(processor));
        Files.delete(directory.resolve("kernels/Surrey-Room-A.wav"));
        processor.configure(true, room);
        consume(processor, 44100);
        assertNotEquals(0, kernelId(processor));
    }
}
