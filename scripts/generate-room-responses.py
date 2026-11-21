#!/usr/bin/env python3
"""Rebuild the bundled Surrey BRIR matrices from the upstream SOFA files.

Requires numpy, scipy, h5py, and soundfile. See the accompanying third-party
notice for the pinned source, license, channel order, and processing details.
"""

import hashlib
import io
from pathlib import Path
from urllib.request import urlopen

import h5py
import numpy as np
from scipy.signal import resample_poly
import soundfile as sf


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/muonium/resources/assets/sh_better_ui/audio/rooms"
REVISION = "b41d237c03d1fe535810954ac4a8496f4e88f8fc"
SOURCE = "https://raw.githubusercontent.com/IoSR-Surrey/RealRoomBRIRs/" + REVISION
CHECKSUMS = {
    "A": "b956556239b79a47961ad0d2dac39147eaf2081abb530162b16eb186bae36547",
    "B": "f42eea9f6043dd887e02596ff201879886d67f403f4946b7bf72bdb1fec079f4",
}


def read_room(room):
    filename = f"UniS_Room_{room}_BRIR_48k.sofa"
    with urlopen(f"{SOURCE}/{filename}", timeout=120) as response:
        data = response.read()
    if hashlib.sha256(data).hexdigest() != CHECKSUMS[room]:
        raise ValueError(f"Unexpected source checksum: {filename}")
    with h5py.File(io.BytesIO(data), "r") as sofa:
        if sofa.attrs["License"] != b"MIT license":
            raise ValueError(f"Unexpected embedded license: {filename}")
        if not np.array_equal(sofa["Data.SamplingRate"][:], [48000]):
            raise ValueError(f"Unexpected sampling rate: {filename}")
        if not np.all(sofa["Data.Delay"][:] == 0):
            raise ValueError(f"Nonzero SOFA delay requires explicit handling: {filename}")
        if not np.allclose(sofa["ListenerView"][:], [[1, 0, 0]]):
            raise ValueError(f"Unexpected listener view: {filename}")
        receivers = sofa["ReceiverPosition"][:, :, 0]
        if not np.allclose(receivers, [[0, 0.09, 0], [0, -0.09, 0]]):
            raise ValueError(f"Unexpected receiver order: {filename}")
        positions = sofa["SourcePosition"][:]
        left = np.flatnonzero(np.all(np.isclose(positions, [30, 0, 1.5]), axis=1))
        right = np.flatnonzero(np.all(np.isclose(positions, [330, 0, 1.5]), axis=1))
        if len(left) != 1 or len(right) != 1:
            raise ValueError(f"Expected measured left/right 30 degree sources: {filename}")
        ir = sofa["Data.IR"]
        matrix = np.stack((ir[left[0], 0], ir[left[0], 1],
                           ir[right[0], 0], ir[right[0], 1]), axis=1)
        if not np.isfinite(matrix).all():
            raise ValueError(f"Non-finite impulse response: {filename}")
        return matrix


def main():
    rooms = {name: read_room(name) for name in CHECKSUMS}
    # One shared gain preserves both interaural ratios and relative room levels.
    # Bound the summed transfer magnitude per ear on a dense frequency grid.
    max_response = 0.0
    for matrix in rooms.values():
        response = np.abs(np.fft.rfft(matrix, n=131072, axis=0))
        max_response = max(max_response, np.max(response[:, 0] + response[:, 2]),
                           np.max(response[:, 1] + response[:, 3]))
    gain = 0.5 / max_response
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for room, matrix in rooms.items():
        matrix = matrix * gain
        for rate, samples in ((48000, matrix),
                              (44100, resample_poly(matrix, 147, 160, axis=0) * (160 / 147))):
            suffix = "" if rate == 48000 else "-44100"
            path = OUTPUT / f"Surrey-Room-{room}{suffix}.wav"
            sf.write(path, samples, rate, format="WAV", subtype="PCM_24")
            print(f"{path}: {len(samples)} frames, {rate} Hz, shared gain {gain:.12g}")


if __name__ == "__main__":
    main()
