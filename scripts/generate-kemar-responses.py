"""Rebuild SHBUI's headphone kernels from MIT's original compact.zip archive."""
from argparse import ArgumentParser
from hashlib import sha256
from io import BytesIO
from pathlib import Path
from zipfile import ZipFile
import wave

import numpy as np
from scipy.signal import resample_poly


ARCHIVE_SHA256 = "0bcd69f8e8760cf8eacff4a89594ceca7d44fb94f28ecf0bf19970003bbfb3e0"
OUTPUT = Path(__file__).resolve().parents[1] / "src/muonium/resources/assets/sh_better_ui/audio/vhe"
AZIMUTHS = (15, 30, 45, 60, 75)


def responses(archive):
    pairs = {}
    for level, angle in enumerate(AZIMUTHS):
        # Source at positive azimuth lies to the listener's right. WAV stores L, R.
        with wave.open(BytesIO(archive.read(f"elev0/H0e{angle:03d}a.wav"))) as wav:
            if (wav.getframerate(), wav.getnchannels(), wav.getsampwidth(), wav.getnframes()) != (44100, 2, 2, 128):
                raise ValueError(f"Unexpected MIT compact response at {angle} degrees")
            pcm = np.frombuffer(wav.readframes(128), dtype="<i2").reshape(-1, 2) / 32768.0
        near_far = pcm[:, ::-1]
        pairs[level, 44100] = near_far
        # Preserve filter gain, including DC, when changing the sample spacing of an IR.
        pairs[level, 48000] = resample_poly(near_far, 160, 147, axis=0) * (147 / 160)
    return pairs


def main():
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path, help="compact.zip from sound.media.mit.edu/resources/KEMAR/")
    args = parser.parse_args()
    data = args.archive.read_bytes()
    if sha256(data).hexdigest() != ARCHIVE_SHA256:
        parser.error("Archive SHA-256 does not match the cited MIT release")
    with ZipFile(BytesIO(data)) as archive:
        pairs = responses(archive)
    # A symmetric two-speaker filter has sum and difference transfer functions.
    # Use one common gain for all directions/rates to preserve measured level differences.
    peak = max(np.abs(np.fft.rfft(pair[:, 0] + sign * pair[:, 1], 16384)).max()
               for pair in pairs.values() for sign in (-1, 1))
    gain = min(1.0, 10 ** (-1 / 20) / peak)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for (level, rate), pair in pairs.items():
        for channel, name in enumerate(("near", "far")):
            (OUTPUT / f"L{level}_{rate}_{name}.f32").write_bytes(
                (pair[:, channel] * gain).astype("<f4").tobytes())
    print(f"Wrote {2 * len(pairs)} kernels; common gain {gain:.9f}")


if __name__ == "__main__":
    main()
