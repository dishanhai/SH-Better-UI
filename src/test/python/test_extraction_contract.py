"""Author: dishanhai. Verify ownership and independent UI-library delivery."""
import hashlib
import json
import pathlib
import re
import tomllib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[3]
CONSUMER = ROOT.parent / "gt_shanhai"


class ExtractionContract(unittest.TestCase):
    def test_independent_identity_and_explicit_consumer_dependency(self):
        properties = (ROOT / "gradle.properties").read_text(encoding="utf-8-sig")
        self.assertRegex(properties, r"(?m)^mod_id=sh_better_ui$")
        manifest = (CONSUMER / "src/main/resources/META-INF/mods.toml").read_text(encoding="utf-8")
        dependencies = tomllib.loads(manifest)["dependencies"]["gt_shanhai"]
        entries = [item for item in dependencies if item["modId"] == "sh_better_ui"]
        self.assertEqual(1, len(entries))
        dependency = entries[0]
        self.assertTrue(dependency["mandatory"])
        self.assertEqual("AFTER", dependency["ordering"])
        self.assertEqual("BOTH", dependency["side"])
        properties = (CONSUMER / "gradle.properties").read_text(encoding="utf-8-sig")
        version = re.search(r"(?m)^sh_better_ui_version=(1\.\d+\.\d+)$", properties)
        self.assertIsNotNone(version, "Consumer must pin its minimum SH-Better-UI version")
        build = (CONSUMER / "build.gradle").read_text(encoding="utf-8")
        self.assertRegex(build, r"\bsh_better_ui_version:\s*sh_better_ui_version\b")
        expanded = dependency["versionRange"].replace("${sh_better_ui_version}", version[1])
        self.assertEqual(f"[{version[1]},2.0.0)", expanded)

    def test_core_does_not_depend_on_consumer_or_optional_ldlib(self):
        roots = [ROOT / "src/main", ROOT / "src/kernel", ROOT / "neoforge-1.21.1/src/main"]
        sources = [path for root in roots for suffix in ("*.java", "*.kt") for path in root.rglob(suffix)]
        self.assertGreater(len(sources), 10, "UI implementation has not been extracted")
        for path in sources:
            source = path.read_text(encoding="utf-8")
            self.assertNotIn("com.dishanhai.gt_shanhai", source, str(path))
            if "compat" in path.parts:
                continue
            if path.name == "PlatformUiApi.java":
                # The published nested optional facade keeps its original JVM signatures.
                outer, nested, _ = source.partition("public static final class LdLib {")
                self.assertTrue(nested, str(path))
                imports = re.findall(r"(?m)^import (?:com\.lowdragmc|com\.dishanhai\.sh_better_ui\.compat\.ldlib)[^;]+;", outer)
                for line in imports:
                    outer = outer.replace(line, "")
                for line in imports:
                    imported_type = line.removesuffix(";").rsplit(".", 1)[1]
                    self.assertNotRegex(outer, rf"\b{re.escape(imported_type)}\b", str(path))
                self.assertNotIn("compat.ldlib", outer, str(path))
                source = outer
            self.assertNotIn("com.lowdragmc", source, str(path))

    def test_shader_has_single_owner_and_resolves_own_namespace(self):
        relative = pathlib.Path("src/main/resources/assets")
        for suffix in ("post/shop_gauss_blur.json", "program/shop_gauss_blur.json",
                       "program/shop_gauss_blur.fsh", "program/shop_gauss_blur.vsh"):
            migrated = ROOT / relative / "sh_better_ui/shaders" / suffix
            self.assertTrue(migrated.is_file(), str(migrated))
            self.assertFalse((CONSUMER / relative / "gt_shanhai/shaders" / suffix).exists(), suffix)
            self.assertNotIn("gt_shanhai:", migrated.read_text(encoding="utf-8"))

    def test_shader_formulas_match_approved_baseline(self):
        baseline = json.loads((ROOT / "src/test/resources/extraction-baseline.json").read_text(encoding="utf-8"))
        # Author: dishanhai. Retain migration history while pinning explicitly maintained shaders.
        approved = baseline["shader_hashes"] | baseline.get("maintained_shader_hashes", {})
        for suffix, expected in approved.items():
            path = ROOT / "src/main/resources/assets/sh_better_ui/shaders" / suffix
            source = path.read_text(encoding="utf-8").replace("sh_better_ui:", "UI_NAMESPACE:")
            self.assertEqual(expected, hashlib.sha256(source.encode()).hexdigest(), suffix)

    def test_post_event_is_registered_once_and_not_by_consumer(self):
        registration = "GaussBlur.onScreenRenderPost"
        library = "\n".join(p.read_text(encoding="utf-8") for p in (ROOT / "src/main").rglob("*.java"))
        self.assertEqual(1, library.count(registration))
        consumer = (CONSUMER / "src/main/java/com/dishanhai/gt_shanhai/client/ClientInit.java").read_text(encoding="utf-8")
        self.assertNotIn("GaussBlur::onScreenRenderPost", consumer)
        self.assertNotIn("GaussBlur.onScreenRenderPost", consumer)

    def test_ui_library_is_never_embedded_or_recompiled_in_consumer(self):
        build = (CONSUMER / "build.gradle").read_text(encoding="utf-8")
        self.assertIn("shBetterUiJar", build)
        self.assertNotRegex(build, r"from\([^\n]*shBetterUi")
        self.assertNotRegex(build, r"jarJar[^\n]*sh_better_ui")
        for relative in (
            "kotlin/com/dishanhai/gt_shanhai/client/gui/glass/GlassGeometry.kt",
            "java/com/dishanhai/gt_shanhai/client/gui/shop/ShopGaussBlur.java",
            "java/com/dishanhai/gt_shanhai/client/gui/shop/ShopGlassStyle.java",
        ):
            self.assertFalse((CONSUMER / "src/main" / relative).exists(), relative)

    def test_cold_machine_screen_and_open_screen_resource_reload_are_warmed(self):
        client = (ROOT / "src/main/java/com/dishanhai/sh_better_ui/client/UiClient.java").read_text(encoding="utf-8")
        init = (CONSUMER / "src/main/java/com/dishanhai/gt_shanhai/client/ClientInit.java").read_text(encoding="utf-8")
        self.assertIn("GaussBlur.isAllowed(event.getScreen())", client)
        self.assertIn("container.modularUI.holder instanceof com.dishanhai.gt_shanhai.common.machine.part.DShanhaiMaintenanceHatchMachine", init)
        self.assertIn("container.modularUI.holder instanceof com.dishanhai.gt_shanhai.common.machine.part.RecipeThreadHatchPartMachine", init)
        reload_method = client.split("void reloadCurrentScreen()", 1)[1]
        self.assertLess(reload_method.index("GaussBlur.release()"), reload_method.index("GaussBlur.warmUp()"))
        self.assertIn("GaussBlur.isAllowed(screen)", reload_method)


if __name__ == "__main__":
    unittest.main()
