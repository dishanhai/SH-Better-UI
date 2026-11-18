# Local music-font management

This slice ports player-managed local static TrueType fonts to NeoForge 26.1.2.
It does not replace Minecraft's global font. It targets the `mp_ui` and
`mp_ui_semibold` font resources consumed by music/MP typography and the explicit
sample in the font-management page.

## Local-only scope

Players select a local file and confirm import. No local font or path is uploaded.
Only standalone static TrueType fonts with glyf outlines are accepted, subject to
the existing 32 MiB bound and structural validation. TTC collections, OTF, WOFF and
variable fonts are outside this slice. Structural validation is not a sandbox for
malicious font files; import only files from trusted sources whose license allows
local use. Proprietary PingFang/Microsoft YaHei files are not distributed.

The generated resource pack is specific to the running resource-pack format and
contains font definitions, the copied TTF and a local-use notice. File reads and
pack generation happen off the rendering thread. Resource-pack selection and
reload happen on the client thread, retaining unrelated selected packs. The
manager must not publish a successful selection until the reload succeeds;
failed/cancelled work restores the previous selection or reports recovery failure.
Closing or replacing the screen must not allow a late callback to reopen it.

Restoring defaults deselects SHBUI-managed font packs. It is not a destructive
cleanup command for the player's original TTF or unrelated resource packs.
Pinned online font downloads and a broader font-library browser are not exposed
until their complete native workflow is migrated.

## Validation

The accompanying PR records metadata/pack tests, reload lifecycle tests, legacy
regression scope and actual-game import/restore evidence. Native rendering,
font availability and resource reload are verified separately from pure ZIP tests.
