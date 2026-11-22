#!/usr/bin/env python3
"""Draw the 58 navigation, function, modifier, and Mac keycap SVG masters."""

import argparse
from pathlib import Path
import runpy

shared = runpy.run_path(str(Path(__file__).with_name("draw-alphanumeric-keycaps.py")))
KEYCAP = shared["keycap"]
DESTINATION = shared["DESTINATION"]

# Condensed monoline glyphs for labels that need to fit in a 16px keycap.
# Each segment is a polyline in a 2 × 4 unit grid; no font is resolved at runtime.
LETTERS = {
    "A": [[(0,4),(1,0),(2,4)],[(.45,2.75),(1.55,2.75)]],
    "C": [[(2,.35),(1.45,0),(.55,0),(0,.65),(0,3.35),(.55,4),(1.45,4),(2,3.65)]],
    "D": [[(0,4),(0,0),(1.25,0),(2,.75),(2,3.25),(1.25,4),(0,4)]],
    "E": [[(2,0),(0,0),(0,4),(2,4)],[(0,2),(1.65,2)]],
    "F": [[(2,0),(0,0),(0,4)],[(0,1.9),(1.65,1.9)]],
    "G": [[(2,.4),(1.4,0),(.55,0),(0,.6),(0,3.4),(.55,4),(2,4),(2,2.2),(1.15,2.2)]],
    "H": [[(0,0),(0,4)],[(2,0),(2,4)],[(0,2),(2,2)]],
    "I": [[(0,0),(2,0)],[(1,0),(1,4)],[(0,4),(2,4)]],
    "L": [[(0,0),(0,4),(2,4)]],
    "M": [[(0,4),(0,0),(1,2.35),(2,0),(2,4)]],
    "N": [[(0,4),(0,0),(2,4),(2,0)]],
    "O": [[(.45,0),(1.55,0),(2,.55),(2,3.45),(1.55,4),(.45,4),(0,3.45),(0,.55),(.45,0)]],
    "P": [[(0,4),(0,0),(1.48,0),(2,.5),(2,1.65),(1.45,2.15),(0,2.15)]],
    "R": [[(0,4),(0,0),(1.48,0),(2,.5),(2,1.65),(1.45,2.1),(0,2.1)],[(1.15,2.1),(2,4)]],
    "S": [[(2,.35),(1.55,0),(.5,0),(0,.5),(0,1.45),(.5,2),(1.5,2),(2,2.55),(2,3.5),(1.55,4),(.45,4),(0,3.7)]],
    "T": [[(0,0),(2,0)],[(1,0),(1,4)]],
    "U": [[(0,0),(0,3.45),(.55,4),(1.45,4),(2,3.45),(2,0)]],
    "0": [[(.5,0),(1.5,0),(2,.6),(2,3.4),(1.5,4),(.5,4),(0,3.4),(0,.6),(.5,0)]],
    "1": [[(.55,.55),(1.15,0),(1.15,4)],[(.45,4),(1.85,4)]],
    "2": [[(0,.55),(.5,0),(1.55,0),(2,.55),(2,1.45),(0,4),(2,4)]],
    "3": [[(0,.25),(.55,0),(1.55,0),(2,.55),(2,1.55),(1.5,2),(2,2.55),(2,3.45),(1.55,4),(.55,4),(0,3.75)],[(1,2),(1.5,2)]],
    "4": [[(1.5,4),(1.5,0),(0,2.7),(2,2.7)]],
    "5": [[(2,0),(0,0),(0,1.9),(1.55,1.9),(2,2.4),(2,3.45),(1.55,4),(.45,4),(0,3.7)]],
    "6": [[(2,.4),(1.5,0),(.65,0),(0,.75),(0,3.45),(.45,4),(1.55,4),(2,3.45),(2,2.55),(1.55,1.95),(0,1.95)]],
    "7": [[(0,0),(2,0),(.6,4)]],
    "8": [[(.5,2),(0,1.55),(0,.55),(.45,0),(1.55,0),(2,.55),(2,1.55),(1.5,2),(.5,2),(0,2.55),(0,3.45),(.45,4),(1.55,4),(2,3.45),(2,2.55),(1.5,2)]],
    "9": [[(0,3.7),(.55,4),(1.35,4),(2,3.25),(2,.55),(1.55,0),(.45,0),(0,.55),(0,1.5),(.45,2.05),(2,2.05)]],
}

def path(d, width=1.1):
    return f'<path d="{d}" stroke-width="{width}"/>'

def legend(word, alphabet=LETTERS):
    height = 4.65
    gap = 1.2
    x0 = (16 - (len(word) * 2 + (len(word) - 1) * gap)) / 2
    d = " ".join(
        "M" + " ".join(f"{x0 + i * (2 + gap) + x:.2f},{(16-height)/2 + y * height/4:.2f}" for x, y in line)
        for i, c in enumerate(word) for line in alphabet[c]
    )
    return path(d, .79 if len(word) == 3 else .98)

def filled(d):
    return f'<path d="{d}" fill="#fff" stroke="none"/>'

GENERIC = {
    "alt": legend("ALT"),
    "arrow_down": path("M8 4.5v7m-2.35-2.4L8 11.5l2.35-2.4"),
    "arrow_left": path("M11.5 8h-7m2.4-2.35L4.5 8l2.4 2.35"),
    "arrow_right": path("M4.5 8h7m-2.4-2.35L11.5 8l-2.4 2.35"),
    "arrow_up": path("M8 11.5v-7m-2.35 2.4L8 4.5l2.35 2.4"),
    "backspace": path("M4.35 8 6.55 5.3h4.9v5.4h-4.9L4.35 8ZM7.85 7.15l1.7 1.7m0-1.7-1.7 1.7", .94),
    "caps_lock": path("M5 8.45 8 5.15l3 3.3H9.4v1.75H6.6V8.45ZM6.3 11.55h3.4", .95),
    "ctrl": legend("CTL"),
    "delete": legend("DEL"),
    "end": legend("END"),
    "enter": path("M11.3 5.1v3.75H4.8m2-2L4.8 8.85l2 2"),
    "escape": legend("ESC"),
    "home": legend("HM"),
    "insert": legend("INS"),
    "menu": path("M5 5.5h6v5H5Zm1.55 1.7h2.9m-2.9 1.6h2.9", .86),
    "num_lock": legend("NL"),
    "page_down": path("M5.2 5.25h2.3M5.2 7.55h1.6M9.7 5.45v5.6m-1.6-1.6 1.6 1.6 1.6-1.6", .96),
    "page_up": path("M5.2 5.25h2.3M5.2 7.55h1.6m2.9 3.5v-5.6m-1.6 1.6 1.6-1.6 1.6 1.6", .96),
    "pause": filled("M5.2 4.95h1.9v6.1H5.2ZM8.9 4.95h1.9v6.1H8.9Z"),
    "print_screen": legend("PRT"),
    "scroll_lock": legend("SCR"),
    "shift": path("M4.55 8.45 8 4.85l3.45 3.6H9.4v2.65H6.6V8.45Z", .98),
    "space": path("M4.35 8.2v2h7.3v-2"),
    "super": legend("OS"),
    "tab": path("M4.35 8h5.05m-1.9-1.9 1.9 1.9-1.9 1.9m3.6-4.4v5"),
}
MAC = {
    "arrow_down": filled("M4.95 6.35h6.1L8 10.8Z"),
    "arrow_left": filled("M9.7 4.95v6.1L5.2 8Z"),
    "arrow_right": filled("M6.3 4.95v6.1L10.8 8Z"),
    "arrow_up": filled("M4.95 9.65h6.1L8 5.2Z"),
    "caps_lock": GENERIC["caps_lock"],
    "command": path("M6.65 6.65H5.4a1.25 1.25 0 1 1 1.25-1.25v5.2a1.25 1.25 0 1 1-1.25-1.25h5.2a1.25 1.25 0 1 1-1.25 1.25V5.4a1.25 1.25 0 1 1 1.25 1.25Z", .88),
    "control": path("M5.1 9.4 8 6.5l2.9 2.9"),
    "delete": GENERIC["backspace"],
    "eject": filled("M5 8.7h6L8 4.7ZM5 10.1h6v1.15H5Z"),
    "end": path("M5.3 5.3l5.4 5.4m0-3.55v3.55H7.15"),
    "escape": path("M10.3 6.3a3 3 0 1 1-2.55-1.3M4.65 7.4V4.65H7.4m-2.75 0L8.1 8.1", .99),
    "fn": legend("FN"),
    "forward_delete": path("M11.65 8 9.45 5.3h-4.9v5.4h4.9L11.65 8ZM6.45 7.15l1.7 1.7m0-1.7-1.7 1.7", .94),
    "globe": '<circle cx="8" cy="8" r="3.45" stroke-width=".92"/><ellipse cx="8" cy="8" rx="1.45" ry="3.45" stroke-width=".83"/>' + path("M4.55 8h6.9", .83),
    "home": path("m10.7 10.7-5.4-5.4m0 3.55V5.3h3.55"),
    "option": path("M4.65 5.55h1.65l3.15 4.9h1.9M9.15 5.55h2.2"),
    "page_down": path("M8 4.65v6.7m-2-2 2 2 2-2M6.7 6.25h2.6", .99),
    "page_up": path("M8 11.35v-6.7m-2 2 2-2 2 2m-3.3 3.1h2.6", .99),
    "return": GENERIC["enter"],
    "shift": GENERIC["shift"],
    "tab": GENERIC["tab"],
}
MARKS = {**GENERIC, **{f"f{i}": legend(f"F{i}") for i in range(1, 13)},
         **{f"mac_{name}": icon for name, icon in MAC.items()}}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="verify existing SVG masters")
    args = parser.parse_args()
    for name, mark in MARKS.items():
        target = DESTINATION / f"key_{name}.svg"
        content = KEYCAP(mark)
        if args.check:
            if not target.is_file() or target.read_text(encoding="utf-8") != content:
                parser.error(f"stale SVG master: {target.name}")
        else:
            target.write_text(content, encoding="utf-8")
    print(f"{len(MARKS)} special keycap SVG masters {'verified' if args.check else 'generated'}")

if __name__ == "__main__":
    main()
