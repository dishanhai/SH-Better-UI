#!/usr/bin/env python3
"""Rebuild the checked-in Minecraft textures from the bundled SVG masters."""
import argparse
from pathlib import Path
import re
import shutil
import struct
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
MASTER = ROOT / "artwork" / "visual-icons"
GUI = ROOT / "src" / "main" / "resources" / "assets" / "sh_better_ui" / "textures" / "gui"
SVG_NS = "{http://www.w3.org/2000/svg}"
GEOMETRY = {"svg", "g", "path", "rect", "circle", "ellipse"}
ATTRIBUTES = {
    "width", "height", "viewBox", "fill", "stroke", "stroke-width",
    "stroke-linecap", "stroke-linejoin", "transform", "d", "x", "y", "rx", "ry", "cx", "cy", "r",
}


def validate_master(master):
    data = master.read_bytes()
    if b"<!" in data:
        raise ValueError(f"{master.name}: declarations are not supported")
    root = ET.fromstring(data)
    if root.tag != SVG_NS + "svg" or root.attrib.get("viewBox") != "0 0 16 16":
        raise ValueError(f"{master.name}: expected a 16-unit SVG canvas")
    if any(e.tag not in {SVG_NS + name for name in GEOMETRY}
           or e.attrib.keys() - ATTRIBUTES
           or any("url(" in v.lower() for v in e.attrib.values())
           or ("transform" in e.attrib and not re.fullmatch(
               r"translate\(-?(?:[0-9]+(?:\.[0-9]+)?) -?(?:[0-9]+(?:\.[0-9]+)?)\)", e.attrib["transform"]))
           for e in root.iter()):
        raise ValueError(f"{master.name}: only local vector geometry is allowed")


def render(binary, master, size, target):
    subprocess.run([binary, str(master), f"--export-width={size}", f"--export-height={size}",
                    f"--export-filename={target}"], check=True, capture_output=True)
    png = target.read_bytes()
    if (png[:8] != b"\x89PNG\r\n\x1a\n" or png[12:16] != b"IHDR"
            or struct.unpack(">IIBB", png[16:26]) != (size, size, 8, 6)):
        raise ValueError(f"{master.name}: expected transparent {size}×{size} RGBA PNG")
    return png


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="verify without rewriting files")
    parser.add_argument("--inkscape", default="inkscape", help="Inkscape binary or absolute path")
    args = parser.parse_args()
    binary = shutil.which(args.inkscape)
    if not binary:
        parser.error("Inkscape is required to regenerate or verify these files")
    masters = sorted(MASTER.glob("*.svg"))
    if not masters:
        parser.error("no SVG masters found")
    outputs = {}
    with tempfile.TemporaryDirectory(prefix="shbui-visual-icons-") as path:
        for master in masters:
            validate_master(master)
            for size, folder in ((16, "display"), (64, "display_64")):
                target = GUI / folder / (master.stem + ".png")
                if not target.is_file():
                    parser.error(f"no existing texture ID for {target.relative_to(ROOT)}")
                png = render(binary, master, size, Path(path) / f"{master.stem}-{size}.png")
                outputs[target] = png
                if master.stem == "shop" and size == 64:
                    outputs[GUI / "rich" / "material-shop.png"] = png
    stale = [target.relative_to(ROOT) for target, data in outputs.items()
             if not target.is_file() or target.read_bytes() != data]
    if args.check and stale:
        parser.exit(1, "Out-of-date textures:\n" + "\n".join(map(str, stale)) + "\n")
    if not args.check:
        for target, data in outputs.items():
            if target.relative_to(ROOT) in stale:
                target.write_bytes(data)
    print(f"{len(masters)} SVG masters, {len(outputs)} PNG resources: {'verified' if args.check else 'generated'}")


if __name__ == "__main__":
    main()
