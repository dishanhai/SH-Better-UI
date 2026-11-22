#!/usr/bin/env python3
"""Draw the Linux keycap SVG masters with the standard SHBUI key outline."""

import argparse
from pathlib import Path
from runpy import run_path

ALPHANUMERIC = run_path(str(Path(__file__).with_name("draw-alphanumeric-keycaps.py")))
KEYCAP = ALPHANUMERIC["keycap"]
DESTINATION = ALPHANUMERIC["DESTINATION"]

# Compact, original outlines: a penguin for Linux Super, two converging
# inputs for Compose, compact AG/Rq legends, and a pulldown for Menu.
MARKS = {
    "super": (
        '<path stroke-width="1.05" d="M8 4.15c-1.3 0-2.05 1-2.05 2.3v.6C4.9 8 4.7 9.05 5.1 9.9l.8-.35'
        'C6.3 10.55 7.05 11 8 11s1.7-.45 2.1-1.45l.8.35c.4-.85.2-1.9-.85-2.85v-.6'
        'C10.05 5.15 9.3 4.15 8 4.15Z"/>'
        '<path fill="#fff" stroke="none" d="M7.15 7.1h1.7L8 8.15Z"/>'
        '<circle cx="7.2" cy="6.25" r=".4" fill="#fff" stroke="none"/>'
        '<circle cx="8.8" cy="6.25" r=".4" fill="#fff" stroke="none"/>'
    ),
    "compose": '<path stroke-width="1.2" d="M4.8 5.2 8 8l-3.2 2.8M8 8h3.25m-1.35-1.35L11.25 8 9.9 9.35"/>',
    "altgr": (
        '<path stroke-width=".95" d="M4.55 10.3 6.05 5.7 7.55 10.3m-2.45-1.55h1.9'
        'm4.55-2.5a1.5 1.5 0 0 0-2.7.85v1.8a1.4 1.4 0 0 0 2.8 0v-.75h-1"/>'
    ),
    "sysrq": (
        '<path stroke-width=".95" d="M4.65 10.35v-4.7h1.2a1.3 1.3 0 0 1 0 2.6h-1.2m1.2 0 1.3 2.1'
        'm3.05-.15q-1.4 0-1.4-1.35v-1.8q0-1.4 1.4-1.4t1.4 1.4v1.8q0 1.35-1.4 1.35Zm.25-1.2 1.35 1.45"/>'
    ),
    "menu": '<path stroke-width="1.15" d="M4.8 5.55h6.4M4.8 7.9h4.6m-4.6 2.35h2.65m2.3-.5 1.2 1.2 1.2-1.2"/>',
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="verify without rewriting files")
    args = parser.parse_args()

    stale = []
    for name, mark in MARKS.items():
        path = DESTINATION / f"key_linux_{name}.svg"
        artwork = KEYCAP(mark).encode("utf-8")
        if not path.is_file() or path.read_bytes() != artwork:
            stale.append(path.name)
            if not args.check:
                path.write_bytes(artwork)
    if args.check and stale:
        parser.exit(1, "Out-of-date Linux keycap masters: " + ", ".join(stale) + "\n")
    print(f"{len(MARKS)} Linux keycap masters: {'verified' if args.check else 'generated'}")


if __name__ == "__main__":
    main()
