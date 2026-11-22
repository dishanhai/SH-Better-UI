#!/usr/bin/env python3
"""Draw the 47 built-in gamepad button SVG masters without fonts."""

import argparse
from pathlib import Path
import runpy

SCRIPTS = Path(__file__).resolve().parent
KEYCAPS = runpy.run_path(str(SCRIPTS / "draw-alphanumeric-keycaps.py"))
SPECIAL_KEYS = runpy.run_path(str(SCRIPTS / "draw-special-keycaps.py"))
DESTINATION = KEYCAPS["DESTINATION"]
GLYPHS = KEYCAPS["GLYPHS"]
ALPHABET = {
    **SPECIAL_KEYS["LETTERS"],
    "B": [[(0,4),(0,0),(1.45,0),(2,.55),(2,1.45),(1.45,2),(0,2)],
          [(1.45,2),(2,2.55),(2,3.45),(1.45,4),(0,4)]],
    "Z": [[(0,0),(2,0),(0,4),(2,4)]],
}
# Preserve the Xbox face-button color cues; shells remain white/tintable.
XBOX_RGB = {"a": "#39a872", "b": "#d6485d", "x": "#468bde", "y": "#c59131", "guide": "#39a872"}

SHELLS = {
    "face": '<circle cx="8" cy="8" r="5.5"/>',
    "shoulder": '<rect x="2.1" y="4.4" width="11.8" height="7.2" rx="2.15"/>',
    "trigger": '<path d="M5.65 2.6h4.7q1.5 0 1.85 1.55l1 5.6a2 2 0 0 1-1.95 2.65h-6.5A2 2 0 0 1 2.8 9.75l1-5.6Q4.15 2.6 5.65 2.6Z"/>',
    "stick": '<circle cx="8" cy="8" r="5.5"/><path d="M6.7 12.25h2.6" stroke-width=".6"/>',
    "pad": '<rect x="2.4" y="4.05" width="11.2" height="7.9" rx="1.5"/>',
}

def path(d, width=1.12, stroke=None):
    tint = f' stroke="{stroke}"' if stroke else ""
    return f'<path d="{d}" stroke-width="{width}"{tint}/>'

def letter(glyph, rgb):
    tint = f' stroke="{rgb}"' if rgb else ""
    return f'<path d="{GLYPHS[glyph]}" transform="translate(6 5.1)" stroke-width="1.15"{tint}/>'

def dpad(turns):
    active = [(6, 2.6), (10, 2.6), (10, 6.25), (6, 6.25)]
    for _ in range(turns):
        active = [(16 - y, x) for x, y in active]
    fill = "M" + " ".join(f"{x:g},{y:g}" for x, y in active) + "Z"
    return ('<path d="M6 2.6h4V6h3.4v4H10v3.4H6V10H2.6V6H6Z"/>'
            f'<path d="{fill}" fill="#fff" stroke="none"/>')

def button(shell, mark):
    return SHELLS[shell] + mark

def legend(token):
    return SPECIAL_KEYS["legend"](token, ALPHABET)

def tagged(shell, token):
    return button(shell, legend(token))

ART = {f"dpad_{d}": dpad(i) for i, d in enumerate(("up", "right", "down", "left"))}
for name, mark in {
    "circle": '<circle cx="8" cy="8" r="2.55" stroke-width="1.12"/>',
    "cross": path("m5.9 5.9 4.2 4.2m0-4.2-4.2 4.2"),
    "square": '<rect x="5.65" y="5.65" width="4.7" height="4.7" rx=".3" stroke-width="1.12"/>',
    "triangle": path("M8 5.25 10.75 10.1h-5.5Z"),
    "create": path("M8 5.1v1.65M5.55 6.25l1.2 1.15M5 8.7h1.65"),
    "options": path("M5.5 6.25h5M5.5 8h5m-5 1.75h5", 1),
    "mute": path("M7 6.1a1 1 0 0 1 2 0v2.1a1 1 0 0 1-2 0V6.1Zm-1.1 2.3a2.1 2.1 0 0 0 4.2 0M8 10.5v.85m3-5.95-6 5.2", .85),
    "ps": legend("PS"),
}.items():
    ART[f"playstation_{name}"] = button("face", mark)

for side in "LR":
    for i, shell in enumerate(("shoulder", "trigger", "stick"), start=1):
        ART[f"playstation_{side.lower()}{i}"] = tagged(shell, f"{side}{i}")
ART["playstation_touchpad"] = button("pad", path("M5.1 8h.2m2.6 0h.2m2.6 0h.2", 1.25))

for platform in ("switch", "xbox"):
    for l in "ABXY":
        ART[f"{platform}_{l.lower()}"] = button("face", letter(l, XBOX_RGB.get(l.lower()) if platform=="xbox" else None))
    for side in "LR":
        ART[f"{platform}_{side.lower()}s"] = tagged("stick", side + "S")

for side in "LR":
    ART[f"switch_{side.lower()}"] = tagged("shoulder", side)
    ART[f"switch_z{side.lower()}"] = tagged("trigger", "Z"+side)
    ART[f"xbox_{side.lower()}b"] = tagged("shoulder", side+"B")
    ART[f"xbox_{side.lower()}t"] = tagged("trigger", side+"T")

ART.update({
    "switch_capture": button("face", '<rect x="5.75" y="5.75" width="4.5" height="4.5" rx=".45" stroke-width="1"/><circle cx="8" cy="8" r=".7" fill="#fff" stroke="none"/>'),
    "switch_home": button("face", path("m5.25 7.75 2.75-2.45 2.75 2.45m-1.05-.82v3.7H6.3v-3.7", 1.02)),
    "switch_minus": button("face", path("M5.55 8h4.9")),
    "switch_plus": button("face", path("M5.55 8h4.9M8 5.55v4.9")),
    "xbox_guide": button("face", path("M5.35 6.45Q8 5.65 10.4 10.4M10.65 6.45Q8 5.65 5.6 10.4", 1.25, XBOX_RGB["guide"])),
    "xbox_menu": button("face", path("M5.5 6.15h5M5.5 8h5m-5 1.85h5", 1)),
    "xbox_share": button("face", path("M5.6 8.55v2.05h4.8V8.55M8 9V5.2m-1.3 1.3L8 5.2l1.3 1.3", 1)),
    "xbox_view": button("face", path("M5.4 5.8h3.85v3H5.4Zm1.65 3v1.45h3.55V7.2H9.25", .95)),
})

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="verify existing SVG masters")
    opts = parser.parse_args()
    for name, art in ART.items():
        svg = ('<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16">\n'
               '  <g fill="none" stroke="#fff" stroke-width="1.35" stroke-linecap="round" stroke-linejoin="round">\n'
               f'    {art}\n'
               '  </g>\n'
               '</svg>\n')
        target = DESTINATION / f"gamepad_{name}.svg"
        if opts.check:
            if not target.is_file() or target.read_text(encoding="utf-8") != svg:
                parser.error(f"stale SVG master: {target.name}")
        else:
            target.write_text(svg, encoding="utf-8")
    print(f"{len(ART)} gamepad SVG masters {'verified' if opts.check else 'generated'}")

if __name__ == "__main__":
    main()
