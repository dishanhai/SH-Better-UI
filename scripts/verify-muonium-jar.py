# Author: dishanhai
"""Check the compiled player and UUK assets, optionally against the supplied MP 2.6.0 checkout."""
from argparse import ArgumentParser
from pathlib import Path
import json
import re
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parent.parent
PORTS = {
    "forge-1.20.1": "src/muonium-forge",
    "neoforge-1.21.1": "neoforge-1.21.1/src/muonium",
    "neoforge-26.1.2": "neoforge-26.1.2/src/muonium",
    "neoforge-26.2": "neoforge-26.2/src/muonium",
}
REMOVED_SPONSORSHIP_CLASSES = {
    "com/muoniumplayer/core/screens/ncm/SponsorNoticeOverlay",
    "com/muoniumplayer/core/update/SponsorNotice",
    "com/muoniumplayer/core/update/AnnouncementImageCache",
}
SPONSORSHIP_RESOURCE_DIR = "assets/muonium/textures/sponsor/"


def expected_entries(port):
    for source in ("src/muonium", "src/uuk-muonium", PORTS[port]):
        root = ROOT / source
        for language in ("java", "modernui"):
            for file in (root / language).rglob("*.java"):
                if file.name == "package-info.java":
                    continue
                package = re.search(r"^package\s+([\w.]+);", file.read_text("utf-8"), re.MULTILINE)
                if package is None:
                    raise ValueError(f"No package declaration in {file}")
                yield package[1].replace(".", "/") + "/" + file.stem + ".class"
        resources = root / "resources"
        for file in resources.rglob("*"):
            if file.is_file():
                yield file.relative_to(resources).as_posix()


def upstream_classes(checkout, port):
    loader, version = port.split("-", 1)
    tree = checkout / ("1.20.1 recode" if loader == "forge" else version)
    roots = [checkout / module / "src/main/java"
             for module in ("muonium-common", "mpparty-common", "mpserver-core")]
    roots.extend([tree / "src/main/java", tree / loader / "src/main/java"])
    if version in ("1.20.1", "1.21.1"):
        roots.append(tree / "external/modernui/java")
    apple = "com/muoniumplayer/core/ncm/apple/"
    replaced = {apple + name + ".class" for name in
                ("AppleNativeEngine", "media/AppleCencDecryptor", "media/AppleSongAssembler", "protocol/AppleWidevineCdm")}
    replaced.update({
        "com/muoniumplayer/core/screens/GuiAnnouncements.class",
        "com/muoniumplayer/core/screens/ncm/AnnouncementMotion.class",
        "com/muoniumplayer/core/screens/ncm/AnnouncementOverlay.class",
        "com/muoniumplayer/core/update/AnnouncementChecker.class",
    })
    classes = set()
    for root in roots:
        if not root.is_dir():
            raise ValueError(f"Missing upstream source root: {root}")
        for file in root.rglob("*.java"):
            if file.name != "package-info.java":
                classes.add(file.relative_to(root).with_suffix(".class").as_posix())
    return classes - replaced - {name + ".class" for name in REMOVED_SPONSORSHIP_CLASSES}


def upstream_resources(checkout, port):
    loader, version = port.split("-", 1)
    tree = checkout / ("1.20.1 recode" if loader == "forge" else version)
    host_files = {"META-INF/mods.toml", "META-INF/neoforge.mods.toml", "pack.mcmeta"}
    excluded = {"muonium/fonts/" + font for font in
                ("sfregular.otf", "sfbold.otf", "pf_normal.ttf", "pf_middleblack.ttf")}
    excluded.add("muonium/apple/widevine_device.wvd")
    excluded_dirs = ("muonium/yrc/", "com/muoniumplayer/viper/kernels/",
                     "com/muoniumplayer/viper/vhe/", SPONSORSHIP_RESOURCE_DIR)
    for root in [checkout / "muonium-common/src/main/resources", tree / "src/main/resources",
                 tree / loader / "src/main/resources"]:
        for file in root.rglob("*"):
            path = file.relative_to(root).as_posix()
            if file.is_file() and path not in host_files | excluded and not path.startswith(excluded_dirs):
                yield path


def verify_protocol_and_notices(checkout, jar):
    for module, relative in (
        ("mpparty-common", "com/muoniumplayer/party/MpProto.java"),
        ("mpserver-core", "yun/shan/mpsync/net/Proto.java"),
    ):
        source = checkout / module / "src/main/java" / relative
        integrated = ROOT / "src/muonium/java" / relative
        if source.read_text("utf-8") != integrated.read_text("utf-8"):
            raise ValueError(f"Upstream wire codec changed: {relative}")
    for upstream, packaged in (("LICENSE", "LICENSE.apache-2.0.txt"), ("NOTICE", "NOTICE")):
        entry = "META-INF/third-party/muonium-player-2.6.0/" + packaged
        if jar.read(entry).replace(b"\r\n", b"\n") != (checkout / upstream).read_bytes().replace(b"\r\n", b"\n"):
            raise ValueError(f"Upstream attribution differs in {entry}")


def main():
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("port", choices=PORTS)
    parser.add_argument("jar", type=Path)
    parser.add_argument("--reference", type=Path, help="path to the upstream 2.6.0 checkout")
    args = parser.parse_args()
    expected = set(expected_entries(args.port))
    if args.reference:
        upstream = upstream_classes(args.reference, args.port)
        expected.update(upstream)
        expected.update(upstream_resources(args.reference, args.port))
        print(f"Checking {len(upstream)} upstream classes")
    with ZipFile(args.jar) as jar:
        if args.reference:
            verify_protocol_and_notices(args.reference, jar)
        damaged = jar.testzip()
        if damaged:
            raise SystemExit(f"CRC failure: {damaged}")
        names = set(jar.namelist())
        if "muonium.mixins.json" not in names:
            raise SystemExit(f"No Muonium mixin configuration in {args.jar}")
        mixins = json.loads(jar.read("muonium.mixins.json"))
        expected.update(mixins["package"].replace(".", "/") + "/" + mixin + ".class"
                        for mixin in mixins["client"])
        missing = sorted(expected - names)
        unexpected = sorted(name for name in names if name == "muonium/apple/widevine_device.wvd"
                            or (name.startswith(SPONSORSHIP_RESOURCE_DIR) and not name.endswith("/"))
                            or (name.endswith(".class")
                                and name.removesuffix(".class").partition("$")[0] in REMOVED_SPONSORSHIP_CLASSES)
                            or (name.startswith("com/muoniumplayer/viper/kernels/") and name.endswith(".irs"))
                            or (name.startswith("com/muoniumplayer/viper/vhe/") and name.endswith(".f32"))
                            or name.startswith(("com/dishanhai/sh_better_ui/smoke/", "com/muoniumplayer/qa/"))
                            or name.startswith("com/muoniumplayer/core/screens/ncm/QaActions"))
    if missing or unexpected:
        raise SystemExit(f"Missing entries: {missing}\nUnexpected entries: {unexpected}")
    print(f"{args.port}: {len(expected)} source, resource and mixin entries checked; CRC OK")


if __name__ == "__main__":
    main()
