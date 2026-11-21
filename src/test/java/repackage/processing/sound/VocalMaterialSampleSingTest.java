package repackage.processing.sound;

import org.junit.jupiter.api.Test;
import repackage.com.jsyn.data.FloatSample;

import static org.junit.jupiter.api.Assertions.*;

class VocalMaterialSampleSingTest {
    @Test void neverChangesTheSourceUntilACompleteAlignedStemIsUsed() {
        VocalMaterialSample sample = stereo();
        assertFalse(sample.attachSing(new float[]{.25f}, 44100));
        assertFalse(sample.attachSing(new float[]{.25f, -.25f}, 48000));
        sample.setSingLevel(0);
        assertEquals(.75, sample.readDouble(0));
        assertTrue(sample.attachSing(new float[]{.25f, -.25f}, 44100));
        assertEquals(.75, sample.readDouble(0));
        assertEquals(-.5, sample.readDouble(3));
    }

    @Test void independentlyScalesVocalsInBothChannelsAndDoesNotDoubleSubtractAutoMix() {
        VocalMaterialSample sample = stereo();
        assertTrue(sample.attach(new float[]{.25f, -.25f}, 0, 44100, 1));
        assertTrue(sample.attachSing(new float[]{.25f, -.25f}, 44100));
        sample.setSingLevel(.5);
        sample.setLevel(.5);
        settle(sample);
        assertEquals(.5625, sample.readDouble(0), 1e-8);
        assertEquals(.3125, sample.readDouble(1), 1e-8);
        assertEquals(-.3125, sample.readDouble(3), 1e-8);
        sample.detach();
        assertTrue(sample.hasSing());
        sample.setSingLevel(0);
        settle(sample);
        assertEquals(.5, sample.readDouble(0), 1e-8);
        assertEquals(.25, sample.readDouble(1), 1e-8);
        assertEquals(-.25, sample.readDouble(3), 1e-8);
        sample.setSingLevel(1);
        settle(sample);
        assertEquals(.75, sample.readDouble(0));
        sample.cleanUp();
        assertFalse(sample.hasSing());
    }

    @Test void preparingSingDoesNotReplaceTrustedAutoMixMaterial() {
        VocalMaterialSample sample = stereo();
        assertTrue(sample.attach(new float[]{.25f}, 0, 44100, 1));
        sample.setLevel(0);
        assertEquals(.5, sample.readDouble(0));
        assertTrue(sample.attachSing(new float[]{0f, -.25f}, 44100));
        assertEquals(.5, sample.readDouble(0), "A low-confidence Sing frame must not un-duck AutoMix");
        assertEquals(-.5, sample.readDouble(3), "Neutral Sing must not extend the AutoMix window");

        sample.setSingLevel(0);
        settle(sample);
        assertEquals(.5, sample.readDouble(0));
        assertEquals(-.25, sample.readDouble(3), "Outside the AutoMix window the fader keeps its full range");
    }

    private static VocalMaterialSample stereo() {
        FloatSample raw = new FloatSample(new float[]{.75f, .5f, -.75f, -.5f}, 2);
        raw.setFrameRate(44100);
        return new VocalMaterialSample(raw);
    }

    private static void settle(VocalMaterialSample sample) {
        for (int i = 0; i < 1000; i++) sample.readDouble(i % 4);
    }
}
