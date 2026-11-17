#!/usr/bin/env python3
"""Create a local, opt-in SHBUI music typography resource pack. No downloads."""
import argparse
import json
from pathlib import Path
import zipfile

MAX_BYTES = 32 * 1024 * 1024
FORMATS = {"1.20.1": 15, "1.21.1": 34}


def read_font(path):
    path = Path(path)
    if path.is_symlink() or not path.is_file():
        raise ValueError("font must be a regular, non-symlink local file")
    with path.open("rb") as source:
        data = source.read(MAX_BYTES + 1)
    if len(data) > MAX_BYTES:
        raise ValueError("font exceeds the 32 MiB limit")
    # Only static TrueType outlines are supported by this helper; no TTC/OTC conversion.
    if len(data) < 12 or data[:4] != b"\x00\x01\x00\x00":
        raise ValueError("expected a standalone TrueType font; TTC/OTC/WOFF/CFF are unsupported")
    return data


def definition(file):
    return {"providers": [
        {"type": "ttf", "file": "sh_better_ui:" + file, "size": 11.0,
         "oversample": 3.0, "shift": [0.0, 0.0]},
        {"type": "reference", "id": "minecraft:default"}]}


def create_pack(regular, semibold, output, version, licensed=False):
    if not licensed:
        raise ValueError("confirm that your font license permits this local resource-pack use")
    if version not in FORMATS:
        raise ValueError("unsupported Minecraft version")
    regular_data = read_font(regular)
    semibold_data = read_font(semibold) if semibold else regular_data
    # 'x' never replaces an existing pack or follows an output symlink.
    with zipfile.ZipFile(output, "x", compression=zipfile.ZIP_DEFLATED) as pack:
        pack.writestr("pack.mcmeta", json.dumps({"pack": {
            "pack_format": FORMATS[version], "description": "SHBUI custom music typography (local)"}}))
        for role, data in (("regular", regular_data), ("semibold", semibold_data)):
            filename = "user_ui_" + role + ".ttf"
            pack.writestr("assets/sh_better_ui/font/" + filename, data)
            resource = "mp_ui" if role == "regular" else "mp_ui_semibold"
            pack.writestr("assets/sh_better_ui/font/" + resource + ".json",
                          json.dumps(definition(filename), indent=2))
        pack.writestr("LOCAL-FONT-NOTICE.txt",
                      "User-supplied fonts. This helper grants no font license.\n"
                      "Do not redistribute without permission from the font rights holder.\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--regular", type=Path, required=True)
    parser.add_argument("--semibold", type=Path)
    parser.add_argument("--version", choices=FORMATS, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--license-confirmed", action="store_true")
    args = parser.parse_args()
    try:
        create_pack(args.regular, args.semibold, args.output, args.version, args.license_confirmed)
    except (ValueError, OSError, zipfile.BadZipFile) as error:
        parser.exit(1, str(error) + "\n")
    print("Created local pack. Enable it in Minecraft Resource Packs; disable it to restore SHBUI fonts.")


if __name__ == "__main__":
    main()
