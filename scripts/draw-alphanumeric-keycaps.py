#!/usr/bin/env python3
"""Draw SHBUI's 62 alphanumeric keycap SVGs without font dependencies."""

import argparse
from pathlib import Path

DESTINATION = Path(__file__).resolve().parents[1] / "artwork" / "visual-icons"

# A tiny monoline alphabet, using a four-unit advance; cap height is 5.8 units.
# Lowercase ascenders align with capitals; descenders stop just above the key rim.
GLYPHS = {
    "A": "M0 5.8 2 0 4 5.8 M.7 3.8h2.6",
    "B": "M0 5.8V0h2.1Q4 0 4 1.4T2.1 2.8H0m2.1 0Q4 2.8 4 4.3T2.1 5.8H0",
    "C": "M3.85 .75Q3.15 0 2 0 0 0 0 2.9t2 2.9q1.15 0 1.85-.75",
    "D": "M0 5.8V0h1.55Q4 0 4 2.9T1.55 5.8Z",
    "E": "M3.8 0H0v5.8h3.8M0 2.8h2.9",
    "F": "M3.8 0H0v5.8m0-3h2.9",
    "G": "M3.85 .75Q3.15 0 2 0 0 0 0 2.9t2 2.9q1.1 0 2-.7V3H2.15",
    "H": "M0 0v5.8m4 0V0M0 2.85h4",
    "I": "M.7 0h2.6M2 0v5.8M.7 5.8h2.6",
    "J": "M.7 4.65q.15 1.15 1.5 1.15 1.15 0 1.15-1.45V0H1.65",
    "K": "M0 0v5.8M4 0 0 3.05m1.35-1.05L4 5.8",
    "L": "M.3 0v5.8h3.5",
    "M": "M0 5.8V0l2 3.7L4 0v5.8",
    "N": "M0 5.8V0l4 5.8V0",
    "O": "M4 2.9Q4 0 2 0T0 2.9t2 2.9 2-2.9Z",
    "P": "M0 5.8V0h2Q4 0 4 1.65T2 3.3H0",
    "Q": "M4 2.9Q4 0 2 0T0 2.9t2 2.9 2-2.9Zm-1.55 1.5L4.25 6",
    "R": "M0 5.8V0h2Q4 0 4 1.55T2 3.1H0m2.1 0 1.9 2.7",
    "S": "M3.75 .65Q3.1 0 2 0 .2 0 .2 1.45q0 1.1 1.8 1.45t1.8 1.45Q3.8 5.8 2 5.8q-1.1 0-1.85-.7",
    "T": "M0 0h4M2 0v5.8",
    "U": "M0 0v3.9q0 1.9 2 1.9t2-1.9V0",
    "V": "M0 0l2 5.8L4 0",
    "W": "M-.2 0 .7 5.8 2 2.3l1.3 3.5L4.2 0",
    "X": "M0 0l4 5.8M4 0 0 5.8",
    "Y": "M0 0l2 3.2L4 0M2 3.2v2.6",
    "Z": "M0 0h4L0 5.8h4",
    "0": "M3.85 2.9Q3.85 0 2 0T.15 2.9 2 5.8t1.85-2.9ZM.75 4.6 3.25 1.2",
    "1": "M.85 1.5 2.2 0v5.8M.85 5.8h2.8",
    "2": "M.3 1.15Q.6 0 2 0q1.8 0 1.8 1.45 0 .9-1.1 1.95L.3 5.8h3.5",
    "3": "M.35 .5Q1 0 2 0t1.65 1.35Q3.65 2.8 2 2.8h-.65M2 2.8q1.8 0 1.8 1.55T2 5.8Q.85 5.8.2 5.18",
    "4": "M2.85 5.8V0L.1 3.95h3.85",
    "5": "M3.65 0h-3l-.3 2.7Q1 2.3 2 2.3q1.8 0 1.8 1.7T2 5.8q-1.05 0-1.65-.65",
    "6": "M3.6 .55Q3 0 2.15 0 .2 0 .2 3.1q0 2.7 1.85 2.7 1.75 0 1.75-1.75T2.05 2.3Q.9 2.3.3 3.15",
    "7": "M.15 0h3.7L1.15 5.8",
    "8": "M2 2.75q1.6 0 1.6-1.4T2 0 .4 1.35 2 2.75Zm0 0Q.2 2.75.2 4.25T2 5.8t1.8-1.55T2 2.75Z",
    "9": "M.4 5.25q.6.55 1.45.55 1.95 0 1.95-3.1Q3.8 0 1.95 0 .2 0 .2 1.75T1.95 3.5q1.15 0 1.75-.85",
    "a": "M3.55 5.8v-2.7q0-1.4-1.6-1.4-.8 0-1.45.45m3.05 1.35H1.8Q.3 3.5.3 4.65T1.65 5.8q1.15 0 1.9-1",
    "b": "M.45 0v5.8m0-3.1q.65-1 1.65-1 1.65 0 1.65 2.05T2.1 5.8q-1 0-1.65-.85",
    "c": "M3.45 2.15q-.55-.45-1.45-.45Q.4 1.7.4 3.75T2 5.8q.9 0 1.45-.5",
    "d": "M3.55 0v5.8m0-3.1q-.65-1-1.65-1Q.25 1.7.25 3.75T1.9 5.8q1 0 1.65-.85",
    "e": "M.35 3.65h3.3q0-1.95-1.55-1.95Q.35 1.7.35 3.75T2.2 5.8q.8 0 1.3-.45",
    "f": "M1.55 5.8V1.35Q1.55 0 2.65 0h.7M.7 2.1h2.4",
    "g": "M3.55 1.7v4.35q0 1.25-1.65 1.25-.8 0-1.45-.4m3.1-4.3q-.65-.9-1.65-.9Q.3 1.7.3 3.6t1.6 1.9q1 0 1.65-.8",
    "h": "M.45 0v5.8m0-3.1q.8-1 1.7-1 1.45 0 1.45 1.5v2.6",
    "i": "M2 .15v.04M1.3 1.75H2v4.05",
    "j": "M2.85 .15v.04m-.8 1.56h.8v4.45q0 1.1-1.15 1.1h-.55",
    "k": "M.55 0v5.8m3.15-4.1L.55 4.2m1.45-1.1 1.7 2.7",
    "l": "M1.65 0v4.65q0 1.15 1.1 1.15h.2",
    "m": "M-.15 5.8V1.7m0 .9q.55-.9 1.2-.9Q2 1.7 2 2.9v2.9m0-3.2q.55-.9 1.2-.9.95 0 .95 1.2v2.9",
    "n": "M.45 5.8V1.7m0 1q.8-1 1.7-1 1.45 0 1.45 1.5v2.6",
    "o": "M3.65 3.75q0-2.05-1.65-2.05T.35 3.75 2 5.8t1.65-2.05Z",
    "p": "M.45 7.3V1.7m0 1q.65-1 1.65-1 1.65 0 1.65 2.05T2.1 5.8q-1 0-1.65-.85",
    "q": "M3.55 7.3V1.7m0 1q-.65-1-1.65-1Q.25 1.7.25 3.75T1.9 5.8q1 0 1.65-.85",
    "r": "M.85 5.8V1.7m0 1q.75-1 1.8-1h.45",
    "s": "M3.35 2.15Q2.8 1.7 2 1.7q-1.4 0-1.4.95 0 .7 1.4 1 .7.15 1.1.4t.4.7q0 1.05-1.5 1.05-.85 0-1.45-.5",
    "t": "M1.6 .5v4.15q0 1.15 1.05 1.15h.7M.55 1.7h2.8",
    "u": "M.4 1.7v2.65q0 1.45 1.45 1.45.95 0 1.7-.95m0 .95V1.7",
    "v": "M.35 1.7 2 5.8l1.65-4.1",
    "w": "M-.15 1.7 .75 5.8 2 3.35 3.25 5.8l.9-4.1",
    "x": "M.45 1.7l3.1 4.1m0-4.1L.45 5.8",
    "y": "M.4 1.7 2 5.65m1.6-3.95L1.8 6.15q-.5 1.15-1.25 1.15",
    "z": "M.45 1.7h3.1L.45 5.8h3.1",
}

def svg(character):
    # Apply the offset to a separate coordinate viewport so the paths remain editable.
    height_offset = "4.35" if character.islower() else "4.55"
    # Use translated paths from the same glyph coordinates (no font/text reference).
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16">\n'
        '  <g fill="none" stroke="#fff" stroke-linecap="round" stroke-linejoin="round">\n'
        '    <rect x="2.1" y="2.1" width="11.8" height="11.8" rx="2.2" stroke-width="1.35"/>\n'
        f'    <path d="{GLYPHS[character]}" transform="translate(6 {height_offset})" stroke-width="1.08"/>\n'
        '  </g>\n'
        '</svg>\n'
    )

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="verify existing SVG masters")
    args = parser.parse_args()
    for character in GLYPHS:
        suffix = "lower_" + character if character.islower() else character.lower()
        path = DESTINATION / f"key_{suffix}.svg"
        content = svg(character)
        if args.check:
            if not path.exists() or path.read_text(encoding="utf-8") != content:
                parser.error(f"stale SVG master: {path.name}")
        else:
            path.write_text(content, encoding="utf-8")
    print(f"{len(GLYPHS)} keycap SVG masters {'verified' if args.check else 'generated'}")

if __name__ == "__main__":
    main()
