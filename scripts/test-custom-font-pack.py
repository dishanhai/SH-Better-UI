import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location("font_pack", Path(__file__).with_name("create-custom-font-pack.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class FontPackTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.font = self.root / "test.ttf"
        self.font.write_bytes(b"\x00\x01\x00\x00" + b"\x00" * 8)
        self.output = self.root / "pack.zip"

    def tearDown(self):
        self.temp.cleanup()

    def test_versions_and_scoped_fallback(self):
        for version, number in module.FORMATS.items():
            output = self.root / (version + ".zip")
            module.create_pack(self.font, None, output, version, True)
            with zipfile.ZipFile(output) as pack:
                self.assertEqual(number, json.loads(pack.read("pack.mcmeta"))["pack"]["pack_format"])
                self.assertFalse(any("assets/minecraft/" in p for p in pack.namelist()))
                regular = json.loads(pack.read("assets/sh_better_ui/font/mp_ui.json"))
                self.assertEqual("minecraft:default", regular["providers"][-1]["id"])
                self.assertEqual(pack.read("assets/sh_better_ui/font/user_ui_regular.ttf"),
                                 pack.read("assets/sh_better_ui/font/user_ui_semibold.ttf"))

    def test_license_required(self):
        with self.assertRaises(ValueError):
            module.create_pack(self.font, None, self.output, "1.20.1")
        self.assertFalse(self.output.exists())

    def test_no_overwrite(self):
        self.output.write_bytes(b"keep")
        with self.assertRaises(FileExistsError):
            module.create_pack(self.font, None, self.output, "1.20.1", True)
        self.assertEqual(b"keep", self.output.read_bytes())

    def test_invalid_font_rejected_before_output(self):
        for header in (b"ttcf", b"OTTO", b"wOFF", b"bad"):
            self.font.write_bytes(header + b"\x00" * 8)
            with self.assertRaises(ValueError):
                module.create_pack(self.font, None, self.output, "1.20.1", True)
        self.assertFalse(self.output.exists())

    def test_limit(self):
        old = module.MAX_BYTES
        module.MAX_BYTES = 8
        try:
            with self.assertRaises(ValueError):
                module.read_font(self.font)
        finally:
            module.MAX_BYTES = old

    def test_symlink_rejected(self):
        link = self.root / "link.ttf"
        link.symlink_to(self.font)
        with self.assertRaises(ValueError):
            module.read_font(link)


if __name__ == "__main__":
    unittest.main()
