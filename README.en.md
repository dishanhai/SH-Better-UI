# SH-Better-UI

[简体中文](README.md) · [繁體中文](README.zh-TW.md) · **English**

Maintained by 山海 / dishanhai.

SH-Better-UI is a Minecraft client UI library for Forge 1.20.1 and NeoForge 1.21.1. Both platforms share glass materials, blur, animations, search, and item and fluid pickers, and ship separate standalone mod JARs. Consuming mods use the library as an external dependency; replace the appropriate platform JAR to update it.

[Documentation index](docs/README.md) · [English API reference](docs/API_REFERENCE.en.md) · [Unified UI Kernel](docs/API_REFERENCE.en.md#get-the-kernel-and-look-up-services) · [Third-party example project](examples/kernel-extension/README.md)

## Forge 1.20.1 requirements

- Minecraft 1.20.1, Forge 47.4.16 or later, and Java 17 at runtime.
- [Local music API and Windows bridge build](docs/local-music.md)
- [NetEase Cloud Music / QQ Music and synchronized lyrics](docs/ONLINE_MUSIC_API.md)
- KotlinForForge 4.12.0 (Kotlin 2.2.21 runtime) is required and must be supplied separately by the modpack. The integrated player includes its rendering and decoding dependencies. See the [MuoniumPlayer port notes](docs/MUONIUM_PLAYER.md).
- Install `ldlib-forge-1.20.1-1.0.33.b.jar` when using the LDLib interfaces. Native `Screen` users do not need LDLib.
- For development, place `kotlinforforge-4.12.0-all.jar` and `ldlib-forge-1.20.1-1.0.33.b.jar` in `libs/`.

## Build

The NeoForge port for Minecraft 26.x uses separate builds and is currently an [alpha development version](#neoforge-26x-alpha).

Use `build-local.ps1` to build Forge alone, or `簡易構建.bat` to build Forge and NeoForge in sequence. Both scripts start Gradle with JDK 21. Forge production classes use a JDK 17 toolchain and the existing tests use JDK 21. Forge uses Gradle 8.8 and the build includes JUnit and reobfJar. The batch script clears each platform's `build/libs` and lists the new artifacts.

On the first build, omit `--offline` so Gradle can download the JEI compile-time API; restore it after the cache is populated. Keep `簡易構建.bat` as UTF-8 without BOM with Windows CRLF line endings. It leaves the result window open when double-clicked; automation can pass `--no-pause` and receive the build exit code.

```powershell
.\gradle-install\gradle-8.8\bin\gradle.bat build --offline --no-daemon --console=plain
```

The runtime JAR is `build/libs/sh_better_ui-<version>.jar`, with the raw remapped output at `build/reobfJar/output.jar`. The development JAR is `build/libs/sh_better_ui-<version>-dev.jar` and is only for compilation and development. The `raw` JAR is an intermediate and must not be installed in the game. Use the `mod_version` in the corresponding `gradle.properties`.

## Integrating with a mod

Compile against the dev JAR and install the standalone runtime UI library with the consuming mod. `publishToMavenLocal` publishes `com.dishanhai:sh_better_ui:<version>:dev` and sources; a local file dependency also works:

```groovy
implementation files('../SH-Better-UI/build/libs/sh_better_ui-<version>-dev.jar')
```

Replace `<version>` with the version built locally. Declare `sh_better_ui` in the consumer's `mods.toml` using `[1.0.2,2.0.0)`, and do not bundle duplicate UI classes or shaders. Version `1.0.1` introduced the Xinglü smoke material and two-style tooltip API absent in `1.0.0`; `1.0.2` changed the smoke panel to medium blur (12 GUI units) while retaining the other styles. Register allowed screens during client initialization using `GaussBlur.allowScreen(predicate)`; the library manages the single end-of-frame event and resource lifecycle.

The settings and report screens of `gt_shanhai:ultimate_terminal` and the generator screen of `gt_shanhai:pattern_generator` use the library's glass, blur, spring and button feedback. `PageTransition` supplies page transitions; `GuiMotion` supplies staggered preview entrance and scroll blur intensity. Fields, item data and workflows remain in `gt_shanhai`.

On resource reload, the old pipeline is released on the render thread. If a registered glass screen is still open, the pipeline is warmed up using the new resources without reopening the screen. Logging out only releases resources. In-game verification should include F3+T with the screen open and check that blur returns and text and icons remain clear.

Version `1.0.3` added `GlassTheme.SMOKE`, sharing Xinglü's `GlassColors.SMOKE`. Its material uses densely sampled background blur at 16 GUI units, with subtle brightening and edge glow. Consumers using this theme require `[1.0.3,2.0.0)` or later. A shop can offer classic, dark-blue dynamic glass and neutral smoke together; the picker follows the selected theme, and nested smoke controls do not blur again.

### Glass material API

The shared interfaces are in `com.dishanhai.sh_better_ui.client.glass`, `client.blur` and `client.gui.scaled`. Draw the glass before the panel content, then draw text and icons.

```java
var theme = GlassTheme.GLASS;
theme.rootShadow(graphics, x, y, width, height);
theme.backdrop(graphics, x, y, width, height);
theme.box(graphics, x, y, width, height, GlassPalette.ROOT_EDGE, theme.panelBg);
```

`compat.ldlib.GlassPanelTexture` supplies `root()`, `panel()`, `display()`, `page()` and `button()` material factories. Only LDLib consumers should reference that package; create a separate `button()` for each button so its hover spring is independent.

### Client frosted-glass settings

After the first Forge or NeoForge client launch, adjust the `[glassBackdrop]` section of `config/sh_better_ui-client.toml`. New values apply to subsequent draws after the configuration reloads.

| Setting | Default | Range and effect |
| --- | --- | --- |
| `enabled` | `true` | Set to `false` to skip frosted-glass background post-processing while retaining the translucent base. |
| `blurRadiusScale` | `1.0` | `0.0–2.0`, multiplies each material's blur radius. At `0.0`, tint and grain may still appear. |
| `strength` | `1.0` | `0.0–1.0`, scales the overall effect. At `0.0`, skips background post-processing. |
| `tintScale` | `1.0` | `0.0–2.0`, scales the material's tint blend. |
| `grainScale` | `1.0` | `0.0–2.0`, scales the material's grain strength. |

These settings cover library-drawn frosted backgrounds, including root panels, sections, buttons and tooltips. The classic theme retains its own drawing, while page-transition blur and click refraction waves are independent.

### Control panel feedback and tab settings

The F8 control panel has a rainbow dot-matrix background and top-level Configuration, Developer Configuration and Mod Overview sections. Configuration covers frosted glass, display preferences, motion and feedback, tabs and shortcuts; Developer Configuration provides sampling quality, control backdrop blur and the developer panel. Drafts can be previewed, applied, reset and restored after reset, and remain when changing tabs. Settings live in the client's `[ui]` configuration section and use the same keys on Forge and NeoForge.

Mod Overview refreshes observable mod details, runtime, overall and module status, and system and device information every second. Hardware and loader metadata are cached after the first read. Long device names wrap; information groups support keyboard focus and complete narration. Optional dependencies show only whether they are installed, not a health check result.

| Key | Default | Effect |
| --- | --- | --- |
| `hoverAnimation` | `true` | Hover color transition and card lift; static hover feedback remains when disabled. |
| `pressAnimation` | `true` | Button press scale and release transition; pressed color remains when disabled. |
| `selectionPulse` | `true` | Brief emphasis for a newly selected button; the selection marker always remains. |
| `tooltipDelayMs` | `0` | Hover tooltip delay for buttons, sliders and scrollable tabs, `0–2000` ms. |
| `tabWheelEnabled` | `true` | Browse the tab bar with the wheel without changing selection. |
| `tabDragEnabled` | `true` | Drag to browse; releasing after the drag threshold does not select. Esc cancels and restores the position. |
| `tabAutoReveal` | `true` | Automatically reveal the selected tab; keyboard focus still scrolls into view when disabled. |
| `tabScrollStep` | `32` | GUI units per wheel step, `8–128`; fractional wheel input is supported. |

Reduced motion takes priority over the individual motion switches. `ClientUiSettings.feedback()` and `tabs()` return separate `ControlFeedbackSettings` and `TabScrollSettings`; editing one group preserves the others. Existing three- and four-argument `ClientUiSettings` constructors and the three-argument `UiPreferences` constructor remain supported with defaults for new fields. Controls use the preference snapshot passed at creation; an already open consumer screen can update it via `setPreferences()` and `setSettings()`.

### Scrollable tab bar API

`UiScrollableSectionBar` is a native control built from `UiButton` and `UiSectionBar.Section`. The host registers the bar once; `buttons()` exposes button positions and state, and the internal buttons must not be registered separately.

```java
var tabs = ShBetterUiApi.controls().scrollableSectionBar(
        new Bounds(x, y, width, 22), UiPreferences.configured(), sections,
        selectedIndex, index -> requestedSection = index);
addRenderableWidget(tabs);
```

`sections` is a `List<UiSectionBar.Section>` with localized names and icons. Tabs default to 104 GUI units wide; factory overloads accept explicit `TabScrollSettings` and width. The original equal-width `UiSectionBar` remains suitable for a small number of tabs that fit.

When tabs overflow and there is room for arrows, browsing buttons appear at both ends. They reveal adjacent tabs and move focus without changing the current page; each is disabled at its corresponding boundary.

Forward the host's single- or two-axis `mouseScrolled` input to the bar. Pressing gives immediate feedback, release commits a selection, and dragging does not change pages. After Tab enters the group, Left/Right/Home/End locate and switch tabs, and Enter/Space commits the focused tab. A native `Screen` host should explicitly forward captured `mouseReleased` to `tabs` so release outside the bar ends the drag; pass Esc to the bar before `super.keyPressed` during a drag to avoid closing the screen. Call `cancelDrag()` when the host closes. The control panel demonstrates this integration.

`select(index)` updates the confirmed selection without a callback; `focus(index)` moves and reveals keyboard focus. `scrollTo(offset)` and `reveal(index)` support custom input, and `setBounds(area)` updates the layout. Drawing, hit testing and tooltips are clipped to the viewport.

For scroll calculations alone, use `ShBetterUiApi.interactions().tabScrollState()` or create `TabScrollState` directly. Set the range with `resize(viewportWidth, contentWidth)`, then use `scrollBy`, `scrollTo`, `reveal`, `beginDrag`/`drag`/`endDrag` and `cancelDrag`. This state is independent of Minecraft and reuses `PointerDrag` for dragging.


### Neutral light glass

The design draws on the backdrop blur, edge refraction and illuminated edges of `src_collection/collection/liquid-glass-react-master` while using the library's existing shader. The light variant is soft light gray. `GlassStyle.CLEAR` brightening was reduced from 0.035 to 0.008, with weaker edge lighting and refraction; panel and standalone button blur radii are 14 and 8 GUI units. Light gray and smoke backgrounds use a 65-point Gaussian kernel; the existing flight and page-transition blur retain their original kernel. `GlassSurface.panel()` and `control()` share `GlassColors.CLEAR`: buttons inside a panel use a thin color overlay, while standalone buttons retain the base needed for dark text. A translucent light-gray base remains if the shader is unavailable.

```java
GlassSurface.panel(graphics, x, y, width, height);
GlassSurface.drawString(graphics, font, title, x + 8, y + 8, GlassColors.CLEAR.TEXT);
```

LDLib consumers can use `GlassPanelTexture.clearPanel()` and `clearButton()`; use `clearButtonOverlay()` inside an already blurred panel to avoid blurring separately for every button. `GlassColors.select(clear)` supplies the corresponding text, card and control colors; existing dark APIs and palettes are unchanged.

`GlassSurface.drawString()` uses vanilla glyph outlines with a low-opacity white glow and accepts `String` or a formatted `FormattedCharSequence`. The smoke theme's `GlassTheme.drawCentered()` already uses it. Text drawn directly with vanilla `graphics.drawString()` must switch to this API to receive the glow; the background does not intercept other mods' text rendering.

The main mod's settings page has an option for light glass in the Ultimate Terminal and Pattern Generator. It is stored locally in `config/gt_shanhai/ui-client.toml`, is off by default and takes effect when the relevant screens are reopened. The Ultimate Terminal's settings and report screens share the option.

The library contains no trading, machine, recipe, AE or GTCEu logic. In-game appearance, compatibility with third-party shaders and multiple GUI scales still require in-game verification.

### Slate dark mode

`client.gui.scaled.DarkModeTheme` provides native drawing shared by Forge and NeoForge. `background()` draws an opaque Slate 900 base; `card()`, `button()` and `input()` draw rounded cards, buttons and inputs. Use `State.titleText()`, `secondaryText()` and `controlText()` for text colors and `TEXT_MUTED` for hints. Blue identifies primary actions and focus; pair `SUCCESS` with text or an icon for success.

Keep an `Interaction.card()` or `Interaction.control()` per control and sample its actual hovered, pressed, focused and enabled states each frame:

```java
private final DarkModeTheme.Interaction motion = DarkModeTheme.Interaction.control();

// State comes from the native control; message is a translated Component.
var state = motion.sample(hovered, pressed, focused, enabled, reducedMotion, PageTransition.nowMs());
DarkModeTheme.button(graphics, x, y, width, height, state, true, () -> {
    int textX = x + (width - font.width(message)) / 2;
    int textY = y + (height - font.lineHeight) / 2;
    graphics.drawString(font, message, textX, textY, state.controlText(), false);
});
```

On hover, a card lifts by 2 GUI units; text transitions over 200 ms and the card background over 300 ms. A button scales to 98% on the first pressed frame and returns over 200 ms on release; `motion.cancelPress(nowMs)` immediately clears feedback on cancellation. The focus ring has a 2-unit blue border and a 2-unit dark gap. Leave at least 4 GUI units around controls and 6 above cards to avoid clipping it. Draw the themed input background before the native text, cursor and selection. The host owns native focus, keyboard navigation, narration and hit testing. With `reducedMotion=true`, color and inset feedback update immediately without lift or scale.

### Liquid Glass

Native Forge 1.20.1 and NeoForge 1.21.1 `Screen` implementations can use `client.gui.scaled.LiquidGlassTheme`. Draw background content first, then `toolbar()`, `popover()`, `input()` or `button()`, followed by foreground text and icons. Toolbars and buttons use a transparent rounded material; inputs and popovers add a base to maintain text contrast. Buttons can sample state with `DarkModeTheme.Interaction.control()`.

```java
var preferences = UiPreferences.DEFAULT;
var search = new SearchField<>(font, x, y, width, 20, index, UiStyle.LIQUID_GLASS, preferences);
var button = new UiButton(x, y + 24, width, 20, label, onPress, preferences, true, UiStyle.LIQUID_GLASS);
button.setBackgroundBlurRadius(12f);
```

`UiButton.setBackgroundBlurRadius(float)` sets a liquid-glass button's background blur in GUI units, default `12`. `0` disables sampling and uses a solid base; values above `32` are clamped to `32`; negative, NaN and infinite values throw `IllegalArgumentException`. Read it with `getBackgroundBlurRadius()`. Custom buttons can use `LiquidGlassTheme.button(..., preferences, sampleBackground, blurRadius, primary, selected, emphasis, content)`; the older signature uses the default radius.

Confirm and cancel buttons can use the library's transparent PNG icons, centered with `setIcon()`. Their localized names supply hover tooltips and narration.

```java
confirmButton.setIcon(GraphicIcon.SUCCESS);
cancelButton.setIcon(GraphicIcon.CLOSE);
```

Square buttons are circular and unequal width and height produce a capsule. The outline, stroke and focus ring use an analytic distance shader to calculate pixel coverage, with antialiasing adjusted for GUI scale and press animation. Draw the background, then button, then text and icon. Dense Gaussian background sampling and rounded coverage preserve visible background color. `setSampleBackground(false)` skips independent sampling; `UiPreferences` and global glass settings still decide whether blur is enabled.

Old constructors retain each control's default appearance. Controls inside an already drawn glass toolbar can use `setSampleBackground(false)` to reuse its sampling. Search suggestions still sample independently and are drawn by `renderSuggestions()` after ordinary controls and existing post-processing. High contrast, disabled blur or unavailable post-processing uses an opaque base. Reduced motion retains static hover, press and selection feedback.

### Button and screen transition feedback

`UiButton` provides hover highlighting, native tooltips and a `setSelected` marker. Supply a localized description with `setPreview(Component.translatable(...))`; the default tooltip shows the full button name. `UiAttention` moves the tab indicator continuously and provides a short fade-in and target highlight for new content. Reduced motion shows the target immediately and retains the selection marker. Library disclosure controls and the JEI panel already use this. See the [transition feedback guide](docs/attention-feedback.md) for other screens, coordinate conversion and verification.

### Direct dragging, scrolling and zooming

- Keep one `PointerDrag` per drag area. Draw a handle with `DarkModeTheme.dragHandle()` and use the same rectangle for hit testing; include `state.cardOffset()` if the handle is in a lifted card. When child controls have not consumed the event, call `begin(button, mouseX, mouseY)`, show held feedback with `active()` and add `offsetX()/offsetY()` to the original position so the preview follows the pointer. Mark validated targets with `DarkModeTheme.dropTarget()`. `release()` includes the release position, which the screen must revalidate before committing. Call `cancel()` on focus loss, close or cancellation.
- For list dragging, pass `move()`'s `Delta.y()` to `ScrollPageTurn.dragOffset(scrollOffset, delta.y(), maxScroll)` and apply the new offset immediately. Also apply the last delta from `release()`. Excess movement is clipped at boundaries, so reversing direction responds immediately; paged areas can still use the wheel API below.
- `ViewportTransform.zoomAt(anchorX, anchorY, factor, minScale, maxScale)` accepts a relative scale from a wheel or platform gesture and keeps the content under the anchor stable. Draw using the same transform via `GuiRenderUtil.applyViewport()`, and convert pointer coordinates with `contentX()/contentY()` for hit testing. The screen retains its clipping and keyboard input and receives device gestures.

### User control and accessibility

`UiPreferences` carries high contrast, reduced motion and background blur settings to shared renderers. Consumer screens read the available settings and pass them to `DarkModeTheme.Interaction`, `ButtonFeedback.visual()`, `PageTransition.advance()` or `InterruptibleAnimation.sample()`. High contrast uses clear boundaries and opaque backgrounds; reduced motion retains recognizable states. Text uses the native font and the screen recalculates its layout when GUI scale changes.

- `UiHistory<S>` stores independent snapshots for back navigation and local undo; use a separate instance for each. Save search, filter and scroll position before entering a subpage, and editing state before a reversible change. The screen applies the snapshot returned by `restore()`. Server-side undo depends on business acknowledgments.
- `UiConfirmation.open()` opens a native modal over the current screen. Its `Prompt` takes a localized title, target, consequences and confirm/cancel labels. The confirm callback runs once; cancel and Escape return to the parent and use the provided focus lookup to locate a rebuilt control. An optional drawing-only background callback preserves context; `null` gives a solid base. Long content supports the wheel and Page Up/Page Down.
- `UiDisclosure` offers an expandable button with native keyboard and narration support. Layout changes may rebuild the button while retaining `UiDisclosure` and the draft; its callback changes visibility and layout.
- `ButtonFeedback.status()` reports pending, success, partial completion, failure, cancellation and timeout. Display localized text with `Component.translatable(status.translationKey())` or notify the narrator when it changes. Call `resolveCancelled()` after receiving a cancellation acknowledgment.

### Dialogs, anchored bubbles, action sheets and pull-to-refresh

`UiOverlayHost` provides alerts, confirmations, bottom action sheets and control-anchored bubbles over the original screen. `UiPullRefresh` provides pull-down refresh at the top of a list, keyboard refresh, request deduplication and acknowledgment states. Forge and NeoForge native `Screen` share these APIs, with optional Forge LDLib 1.x Widget and NeoForge LDLib2 `UIElement` adapters. See the [screen interaction examples](docs/SCREEN_INTERACTIONS.md).

### Search, suggestions and completion

`client.gui.search.SearchIndex<T>` and `SearchField<T>` are shared by Forge 1.20.1 and NeoForge 1.21.1. The index snapshots localized plain-text names, any number of IDs and aliases. Every term in a multiword query must match, possibly across different fields; matching ignores case and excess whitespace.

The index generates pinyin for consecutive Han characters in names and aliases. `tieding`, `tie ding` or `td` find both 铁锭 and 鐵錠, and can be combined with Han characters or IDs. Literal name and ID matches rank first in suggestions; accepting a pinyin suggestion fills the original name. Polyphonic characters use default pronunciations; callers can add aliases for alternatives.

Type `@` for mod suggestions and `#` for tag suggestions, for example `铁锭 @mine #forge:ingots/`. Mod IDs come from entry ID namespaces. To search mod names or tags, use `Entry(value, name, ids, aliases, modName, tags)` with a caller-localized name and full `namespace:path` tag strings. The old four-argument constructor remains available. `@` matches mod IDs, names, full Han pinyin and initials; quote names with spaces as `@"Applied Energistics 2"`. `#` matches fragments of the full tag. Filters and ordinary terms constrain the same results; unprefixed search keeps its previous meaning.

Mod and tag suggestions are deduplicated and ranked by exact, prefix, substring and pinyin matches. They come only from entries in the current scope that satisfy the remaining query terms. Completion preserves earlier input and whitespace and replaces only the last term; `Suggestion.label()` supplies the display text and `completion()` the complete query. JEI panels and custom ingredient sources share this behavior, and rebuilt indexes reflect ingredient and tag updates.

Quote ordinary names containing literal `@` or `#`, for example `"Block #1"`. Completion quotes and escapes names as needed so the completed query still matches the original entry.

- `search(query, scope)` returns matches in source order, or all scoped entries for an empty query. `suggest(query, scope, limit)` ranks exact, prefix and substring name/ID matches first, then alias or cross-field matches. Custom inputs can use `Suggestion.suffixFor(query)` for a gray suffix.
- `SearchField` uses the native `EditBox`. When space permits it shows a 16×16 magnifying glass at the right; narrower or shorter fields reserve space for text. Input changes refresh suggestions; a gray suffix appears only with the cursor at the end and no selection. Up/Down select, Tab or click completes, and Esc dismisses suggestions without losing input. `setOnSubmit` receives the query on Enter; Ctrl+Z (⌘Z on Mac) or `undoCompletion()` restores the query before completion.
- `setResponder` fires once when the query actually changes. Use `setScope` when a category changes and `setIndex` when data or language changes; both preserve input.

For a native `Screen`, `refreshResults` and `submitSearch` below are host methods:

```java
// import com.dishanhai.sh_better_ui.client.gui.search.SearchField;
// import com.dishanhai.sh_better_ui.client.gui.search.SearchIndex;
private SearchField<String> search;

// Create in init(); the name comes from game localization.
var entries = List.of(new SearchIndex.Entry<>("iron",
        Component.translatable("item.minecraft.iron_ingot").getString(),
        List.of("minecraft:iron_ingot"), List.of("tieding", "td")));
var index = new SearchIndex<>(entries);
search = addRenderableWidget(new SearchField<>(font, 16, 16, 168, 20, index));
search.setResponder(query -> refreshResults(index.search(query)));
search.setOnSubmit(this::submitSearch);

// In render(), after drawing ordinary controls.
search.renderSuggestions(graphics, mouseX, mouseY, height);

// In mouseClicked(), before normal dispatch.
if (search.mouseClickedSuggestion(mouseX, mouseY, button, height)) return true;

// In mouseScrolled(), before normal dispatch; pass the vertical delta on NeoForge.
if (search.mouseScrolledSuggestions(mouseX, mouseY, verticalDelta, height)) return true;

// In keyPressed(), before super; Screen normally handles Esc to close first.
if (keyCode == GLFW.GLFW_KEY_ESCAPE && search.dismissSuggestions()) return true;
```

The field and suggestion list use the same GUI coordinates. When rebuilding the screen, save `getValue()` and restore it with `setValue()`, using the same scope for results and suggestions.


### JEI integration and recipe preview

JEI is optional. Forge 1.20.1 compiles against JEI `15.49.0.200` and NeoForge 1.21.1 against `19.69.0.456`. Each version is published separately, and consuming mods can depend only on SH-Better-UI's public API. Shared search also works without JEI.

- `client.jei.JeiIntegration` provides ingredient snapshots, icons and tooltips, recipe queries and an API to open JEI. Use `isAvailable()` to check whether JEI has loaded the current world's data; refresh `ingredients()` when `revision()` changes. `Ingredient.key()` distinguishes variants of the same registry ID. `recipes(ingredient, direction, limit)` queries visible recipes and `open(ingredient, direction)` opens the full JEI recipes or uses view.
- `client.gui.search.JeiSearchPanel` embeds search and read-only previews in a native `Screen`. Find items or fluids by name, ID, alias and mod; select a result to see its native tooltip or recipes. Switch between Recipes and Uses in the recipe area or open the full JEI page. If the caller supplies `setOnChoose`, the player can use Choose to return an ingredient.

The panel requires at least `144 × 132` GUI pixels; `320 × 200` or more is recommended for side-by-side results and recipes. The screen owns `panel`; save `snapshot()` on resize and `restore(snapshot)` after rebuilding to preserve search and selection.

```java
// In Screen.init(); the screen layout determines panelLeft, panelTop, panelWidth and panelHeight.
panel = new JeiSearchPanel(font, panelLeft, panelTop, panelWidth, panelHeight);
for (var widget : panel.widgets()) addRenderableWidget(widget);
JeiIntegration.setExtraAreas(this, panel::extraAreas);

// In Screen.render(), draw the panel, then native controls, then overlays.
panel.renderBackground(graphics);
super.render(graphics, mouseX, mouseY, partialTick);
panel.renderOverlays(graphics, mouseX, mouseY, height);

// In Screen.tick().
panel.tick();

// Before normal dispatch in Screen.mouseClicked() and mouseScrolled().
if (panel.mouseClicked(mouseX, mouseY, button, height)) return true;
if (panel.mouseScrolled(mouseX, mouseY, verticalDelta, height)) return true;

// Before the super call in Screen.keyPressed().
if (keyCode == GLFW.GLFW_KEY_ESCAPE && panel.dismissSuggestions()) return true;
```

`setExtraAreas` keeps JEI's sidebar away from the panel and suggestions. Supply `panel::extraAreas`; pass `null` when the panel is permanently removed. On NeoForge pass the vertical scroll amount to `mouseScrolled`.

### Item and fluid filtering and selection

`client.gui.filter.IngredientFilter` is an immutable `Predicate<Ingredient>` shared by JEI and custom catalogs. `withKinds`, `withIds`, `withMods` and `withTags` match full values: any value within a category can match, but all configured categories must match. An empty set clears that category. Combine custom conditions with `and`, `or` and `negate`. Name, ID fragments and aliases continue to use `SearchIndex`; `JeiSearchPanel.setFilter` restricts both results and suggestions.

```java
var allowed = IngredientFilter.items()
        .withMods(Set.of("minecraft", "gtceu"))
        .withTags(Set.of(ResourceLocation.tryParse("forge:ingots/iron")));

var request = new IngredientPicker.Request(pickerTitle, IngredientCatalog.automatic(),
        allowed, 8, initialSelection, uiPreferences);
IngredientPicker.open(this, request, () -> rebuiltPickerButton, selected -> {
    // Commit the draft on return to the parent; an empty list clears the filter.
    applySelection(selected);
});
```

`IngredientPicker`, `IngredientCatalog`, `IngredientValues` and `IngredientSelection` are in `client.gui.selection`. `Request.single` and `Request.multiple` provide defaults; the full constructor accepts business constraints, initial candidates and `UiPreferences`. The example tag uses Forge naming; provide IDs from the target platform’s registry. The picker filters by kind, name/ID, mod and tag. Players can switch to the selected-only view, undo local draft changes and confirm an immutable list. Cancel or Escape discards the draft. Draft and search filters survive returning from JEI recipes or changing GUI scale.

| Source | Ingredients |
| --- | --- |
| `IngredientCatalog.jei()` | Items and fluids currently visible in JEI |
| `IngredientCatalog.registry()` | Default native item forms and source fluids |
| `IngredientCatalog.automatic()` | JEI when ready, otherwise the native registry |
| `IngredientCatalog.of(candidates)` | A fixed snapshot supplied by the caller |

`IngredientValues.item(stack)` and `fluid(stack)` create candidates from native stacks. Forge and NeoForge each accept their platform's `FluidStack`. `Ingredient.copy(ItemStack.class)` or `copy(FluidStack.class)` returns a copy retaining amount and NBT/data components. `Ingredient.key()` identifies a variant during the current session; persist native stack data instead. Initial selections should use candidate keys from the current source. Dynamic catalogs can implement `IngredientCatalog`, advance `revision()` when ingredients, language or tags change, and keep keys stable for the same variant within the source. JEI candidates use their ingredient helper for tags; native candidates read item or fluid registry tags.

Embedded screens can pass a source to the `JeiSearchPanel` constructor or switch with `panel.setCatalog(source)`, then apply `panel.setFilter(allowed)`. `panel.setChosen(...)` marks the host's draft and `refreshSelection()` updates choose/remove buttons. `IngredientSelection` can manage drafts, selection limits and candidate revalidation. Existing `setOnChoose` still returns one explicitly chosen item.

### Page turning at scroll boundaries

Create one `client.gui.scaled.ScrollPageTurn` per paged scroll area. When the pointer is over the area and no child consumed the wheel event, calculate and clamp the target offset and pass it to the library. Scrolling down to the bottom advances a page; scrolling up to the top goes back. Content shorter than a screen can turn directly with the wheel. The first and last pages stop at their boundaries; repeated input in the same direction must be at least 180 ms apart to turn again.

```java
private final ScrollPageTurn pageTurn = new ScrollPageTurn();

// In the scroll area's wheel callback. Pages start at 0; offset increases downward.
double target = Math.clamp(scrollTarget - wheelDelta * pixelsPerStep, 0.0, maxScroll);
int targetPage = pageTurn.targetPage(wheelDelta, target, maxScroll,
        currentPage, pageCount, PageTransition.nowMs());
if (targetPage != currentPage) {
    // Use the existing page action. The next page starts at the top, the previous at the bottom.
    openPage(targetPage, targetPage < currentPage);
    return true;
}
scrollTarget = target;
```

After changing pages the screen calculates the new scroll range and position and updates the existing transition. Call `pageTurn.reset()` when changing the list source or reopening the scroll area.

### Animated dot-matrix background

`client.gui.scaled.UiDotMatrix` can fill a page or a local panel. Keep one instance per background and call `render()` after the host background and before foreground controls. `DotMatrixProfile.VIOLET` supplies purple square dots and glow on black; `setProfile()` changes colors or animation at runtime.

From the control panel open Developer Configuration → Developer Panel → Material Gallery, click the current sample title and choose Dot Matrix Animation. The sample shows a rainbow gradient and random fade. Basic settings adjust hue, effect, floating probability and maximum count live; Animation Settings to the right of the title adjusts fade probability, palette, flow and gradient period. Click the preview or focus it and press Enter to trigger a ripple. View Java Code opens a drawer with complete code and a copyable example.

```java
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.DotMatrixProfile;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiDotMatrix;
import java.util.List;

private final UiDotMatrix dots = new UiDotMatrix(DotMatrixProfile.VIOLET
        .withPalette(new DotMatrixProfile.Palette(0xFF030A11, 0xA02A6984, 0xF07AD5FF, 0.55f))
        .withMotion(new DotMatrixProfile.Motion(DotMatrixProfile.Effect.WAVE, 2800, 0.12f, 24, 9f))
        .withFade(new DotMatrixProfile.Fade(0.25f, 2200))
        .withGradient(new DotMatrixProfile.Gradient(List.of(
                new DotMatrixProfile.ColorStop(0f, 0xFFFF71AA),
                new DotMatrixProfile.ColorStop(0.22f, 0xC079D5FF),
                new DotMatrixProfile.ColorStop(0.66f, 0xFFB2FFB5)),
                3600, (x, y, phase) -> phase - x * x - 0.35f * y)));

// Call while drawing the screen background.
Bounds area = new Bounds(0, 0, width, height);
dots.render(graphics, area, mouseX, mouseY);

// On click or keyboard activation, trigger at the event position or the activated control's center.
// dots.pulse(area, x, y);
```

On Forge 1.20.1 a native `Screen` can draw the base and dots in `render()` before `super.render()` draws controls. NeoForge 1.21.1 `super.render()` calls the four-argument `renderBackground()`, so draw both there.

- `Palette` uses ARGB values for background, dot, highlight and glow intensity. A transparent background reveals previously drawn content.
- `Grid` sets spacing, square size, density and random seed. Large areas automatically widen spacing to limit sampling to 16,384 cells. Dots and glow are clipped to `Bounds`.
- `Motion` sets effect, period in milliseconds, per-dot floating probability per cycle, maximum simultaneously floating and maximum rise. Set probability, maximum count or rise to zero to disable floating. Choose `STEADY`, `TWINKLE` or `WAVE`, or implement `Effect.brightness()`.
- `Fade` separately sets random fade-out/reappearance probability (0–1) and period in milliseconds. `Fade.NONE` disables it.
- `Gradient` accepts `Gradient.RAINBOW` or custom ARGB stops. The first position must be `0`, subsequent positions strictly increase and remain below `1`. Segments interpolate between stops, with the last wrapping to the first. Gradient RGB colors the dots; stop alpha multiplies their existing opacity and can still combine with brightness, floating and interaction. `Gradient.NONE` uses the original palette; `VIOLET` disables gradient and random fade by default.
- Gradient flow can use `Flow.RIGHT`, `DOWN`, `DIAGONAL` or `OUTWARD`, or implement `Flow.position(x, y, phase)`. Coordinates and phase are normalized and the returned position wraps at 1. The example squares the horizontal coordinate to vary band width and adds a vertical offset.
- `Interaction` sets hover and ripple radii; set either to zero to disable it. The background uses pointer and click coordinates supplied by the host, which retains hit testing, focus and event handling. Explicit-clock `render()` and `pulse()` must use the same monotonic millisecond clock.
- Reduced motion freezes dots, pauses random fade and fixes the gradient at its initial phase. Hover highlighting and a fixed-range click fade remain available. High contrast uses a solid base. While a modal overlay is open, the host can pass `Double.NaN` to pause hover and call `clearPulse()`.

### Interruptible animation

`client.gui.scaled.InterruptibleAnimation` reuses `Spring` for opening, reversal and target changes that retain position and velocity. It also supports delayed retargeting, stopping, immediate completion and reduced motion. Forge and NeoForge share the API; keep one instance per control and use the same monotonic millisecond clock for events and rendering. This update also fixes freezing of the previous Spring trajectory during delayed retargeting. See the [interruptible animation guide](docs/interruptible-animation.md) for integration and lifecycle examples.

```java
// Control field; the related types are in client.gui.scaled.
private final InterruptibleAnimation motion = new InterruptibleAnimation(Spring.PANEL);
// On toggle: motion.setOpen(open, PageTransition.nowMs());
// Each frame: float progress = motion.sample(PageTransition.nowMs(), reducedMotion);
// Calling setOpen(true, ...) while closing continues from the current position and velocity.
```

### Recipe transitions, mode indicators and live status

- `UiRecipePageTransition.start(direction, viewport, nowMs, preferences)` runs the recipe modifier's 640 ms two-page slide. Call `sample()` every frame and pass drawing-only old and new page callbacks to `draw()`. A sidebar can read `Frame.companion()` and change content when the mask is deepest. For a scene that only displays the current page, use `enter()` and single-callback `draw()`, subtracting that frame's `incomingX()` and `incomingY()` from mouse coordinates.
- `UiButton.setModeIndicators(count, selectedIndex)` marks the current mode using a zero-based index. `UiToggleButton.setModeIndicatorsVisible(true)` displays off/on positions synchronized with `setValue()`.
- `UiDynamicStatus` accepts a `Supplier<Snapshot>`. A `Snapshot` combines `ButtonFeedback.Status`, a custom `Component` and integer progress from 0 to 100. Each `render()` reads the source once; the returned snapshot can also be narrated. `UiDynamicStatus.icon(status)` exposes the status icon separately.

These APIs can be previewed and their Java examples copied from the in-game developer gallery.

### Loading indicators

- Use `UiSkeleton.card()` while a home card waits for data. For custom layouts, `UiSkeleton.draw()` takes placeholder blocks aligned with the content.
- Use `UiLoading.edgeSpinner()` at the top or bottom while a list refreshes or requests another batch. `UiPullRefresh` already uses the top spinner while waiting for acknowledgment.
- Use `UiLoading.progressBar()` or `topBar()` for measurable upload, download or loading progress as completed divided by total. When the total is unknown, use the `nowMs` overload of the top bar.
- Pass the actual completion fraction to the `float` overload of `UiLoading.spinner()` to show progress through arc length; passing `nowMs` draws an indeterminate spinner. With reduced motion, indeterminate indicators remain static while real progress still updates.

### Optional button and glass effects

`client.gui.scaled` provides three composable decorations that appear only when the consumer calls them:

- `ClickRipple` + `GlassEffectRenderer.drawRipple`: each button owns a ripple that expands and fades over 420 ms; repeated clicks restart it and its origin follows layout changes.
- `GlassEffectRenderer.drawFlowBorder`: a thin highlight travels around a rectangle every 2 seconds.
- `GlassEffectRenderer.drawBreathingGlow`: a soft glow extends 3 GUI pixels outside the control, cycling every 2.4 seconds; its intensity can use the existing hover spring.

After the click callback confirms the action was accepted, call `ripple.trigger(mouseX, mouseY, x, y, w, h, nowMs)`. Draw the base, decorations, then text and icons each frame:

```java
GlassEffectRenderer.drawBreathingGlow(g, x, y, w, h, hover, nowMs, reducedMotion, accent);
GlassEffectRenderer.drawFlowBorder(g, x, y, w, h, nowMs, reducedMotion, accent);
GlassEffectRenderer.drawRipple(g, x, y, w, h, ripple, nowMs, reducedMotion, accent);
```

Each control owns its `new ClickRipple()`; `accent` is the current theme color. Use the same millisecond clock for click and rendering, and call `ripple.clear()` on close/removal. Put decorations and base in the same `withButtonMotion` callback so they retain the control's hit area, click behavior and clipping.

With `reducedMotion=true`, the ripple fades uniformly and border and glow use static brightness. These APIs submit native GUI rectangles only, without new post-processing, materials or dependencies. The local ripple and `GaussBlur.requestWave` screen refraction wave can be used independently. See the [glass effects guide](docs/effects.md) for combinations and LDLib integration.

### Text and layout alignment

`client.gui.display` provides `UiText` and `UiLayout` with the same API on Forge and NeoForge. Coordinates and spacing use logical GUI units.

```java
var area = new GraphicDisplayLayout.Bounds(12, 20, 160, 24);
UiText.drawCentered(graphics, font, Component.translatable("your_mod.title"), area, 0xFFFFFFFF, false);

var buttons = UiLayout.row(area, List.of(new UiLayout.Size(28, 18), new UiLayout.Size(44, 20)),
        4, UiLayout.Horizontal.RIGHT, UiLayout.Vertical.CENTER);
```

`drawLeft`, `drawCentered` and `drawRight` vertically center a single line and align it horizontally using native font width. `draw` accepts dynamic horizontal alignment.

`UiLayout.align` places one element. `row` aligns the group horizontally and each item vertically; `column` aligns the group vertically and each item horizontally. Both return immutable `Bounds` in input order and retain requested sizes when space is insufficient. If the screen clips, draw and hit-test against the intersection of the container and each `Bounds`.


### Graphic display and icons

Native Forge and NeoForge `Screen` implementations can use the icons and status panels in `client.gui.display`. Status and paging icons include `INFO`, `SUCCESS`, `WARNING`, `ERROR`, `PREVIOUS` and `NEXT`; common actions include `ADD`, `REMOVE`, `SEARCH`, `SETTINGS`, `REFRESH` and `CLOSE`. Use `new GraphicIcon(textureId)` for a custom transparent 16×16 PNG; white graphics can be tinted with ARGB. Give `UiButton` a localized name even when using an icon, for tooltips and narration.

Use the [built-in icon reference (Chinese)](artwork/visual-icons/CATALOG.zh-TW.md) to look up names, IDs, SVG sources, and runtime textures.

Built-in 64×64 transparent textures are at `sh_better_ui:textures/gui/display_64/<icon-name>.png` and keep the same names as the 16×16 textures. The built-in catalog has an editable SVG master for every icon. Both PNG sizes are exported independently. Built-in `GraphicIcon` constants still reference the 16×16 PNGs.

- AE2: `AE2_ORDER` (place order), `AE2_PATTERN_UPLOAD`, `AE2_ORDERABLE` and `AE2_STOCK`; textures are `ae2_order.png`, `ae2_pattern_upload.png`, `ae2_orderable.png` and `ae2_stock.png`.
- Shop and tools: `PRODUCT_CARD`, `PRODUCT`, `PRODUCT_OUTPUT`, `PRODUCT_INPUT`, `MY_PROFILE`, `MY_FAVORITES`, `HIDE`, `SELECT_ALL`, `SELECT_PARTIAL`, `RENDER`, `TOOL`, `TOOLBAR`, `INSTALL`, `COMPATIBILITY` and `QUANTUM`.
- Files: `FOLDER`, `FILE`, `DOCUMENT`, `FILE_EXE`, `FILE_MD`, `FILE_JAVA` and `FILE_KT`. Mod and recipe screens can use `KUBEJS` (redrawn from the [KubeJS icon](https://modrinth.com/mod/kubejs)), `AE2_PATTERN` and `CRAFTING`; shops can use `SHOP`.
- Classification and planning: `CATEGORY`, `STATISTICS`, `PLAN`, `GOAL`, `EQUATION`, `FUNCTION`, `UNDO`, `PERMISSIONS` and `ACCESS_DENIED`.
- Content and browsing: `CARD`, `CHANNEL`, `MEMBERSHIP`, `LOCATION`, `BROWSER` (with address bar) and `WEB_SEARCH` (globe and magnifying glass).
- Input: `EMOJI`, `SYMBOL`, `TRANSLATE`, `CLIPBOARD`, `TEXT_INPUT` and `NUMBER_INPUT`.
- Data and guides: `INPUT`, `OUTPUT`, `CONFIG`, `MOD`, `GUIDEME` (redrawn from the [GuideME icon](https://modrinth.com/mod/guideme)), `GUIDE`, `GUI` (window with pointer) and `UI` (layout panel with sidebar).
- Compact HUD row icons: `COMPACT_SIGNAL`, `COMPACT_CLOCK`, `COMPACT_CALENDAR`, `COMPACT_WORLD`, `COMPACT_BIOME`, `COMPACT_LOCATION`, `COMPACT_CHUNK` and `COMPACT_TORCH`. They use an 8-pixel grid and retain their full outline in 8 GUI pixels when drawn with a 0.5× PoseStack.
- Actions: `FILTER`, `SORT`, `EDIT`, `COPY`, `DELETE`, `FAVORITE`, `LOCK`, `UNLOCK`, `EXPAND`, `COLLAPSE`, `UPLOAD` and `DOWNLOAD`. For example, `button.setLeadingIcon(GraphicIcon.FILTER)` puts a funnel beside a filter label.
- Navigation and workflow: `BACK`, `FORWARD`, `PREVIOUS`, `NEXT`, `SWITCH`, `MERGE`, `NEW`, `SAVE`, `EXPORT`, `COMMENT` and `DELETE`. `COMMENT` and `BUBBLE` share the bubble graphic.
- Notices and maintenance: `WARNING`, `CRITICAL_WARNING`, `CANCEL` and `CRITICAL_CANCEL`. `CANCEL` shares the cross with `CLOSE`; critical states have an octagonal outline distinguishable in monochrome themes. Maintenance icons include `REPAIR`, `RESET`, `DISABLE`, `ENABLE`, `HIDE`, `SHOW`, `ORGANIZE` and `SORT`.
- Tools and records: `CACHE`, `LOG`, `MONITOR`, `TODO`, `DOCUMENT`, `CODE`, `TEST`, `DEVELOPMENT`, `COLLECTION`, `SEPARATE`, `HISTORY`, `USER`, `SYSTEM`, `PHONE` and `COMPUTER`. `SEPARATE` splits one path into two and pairs with `MERGE`; `MEMBERS` is available for member lists.
- Operating systems: `WINDOWS`, `MACOS`, `IOS`, `ANDROID` and `LINUX` have tintable 16×16 originals and 64×64 copies.
- Task control: `RUN`, `PAUSE` and `RESUME`. Resume has a pause line to the left of the play triangle to distinguish it from starting.
- Games and devices: `MINECRAFT` (creeper face), `GAMEPAD`, `MOUSE`, `KEYBOARD`, `HEADPHONES` and `MICROPHONE`. Loaders use `FORGE` (anvil) and `NEOFORGE` (fox head); game and loader marks are monochrome pixel graphics suitable for tinting.
- Mods: `AE2` (powered ME controller front), `MEKANISM` (gear and lightning), `FTB_QUESTS` (bound quest book), `KUBEJS` (puzzle block), and `GREGTECH` (GTCEu mark). These icons ship with the library and can be used directly on `UiButton`.
- Content and details: `BLOCK`, `ITEM`, `ATTRIBUTES`, `DESCRIPTION`, `TOOLTIP` and `INTERACTION`.
- Archives and resources: `COMPRESS`, `DECOMPRESS`, `ENCRYPT`, `DECRYPT`, `PLUGIN`, `GITHUB`, `MCMOD` and `WIKI`.
- Administration and infrastructure: `SETTINGS`, `CONFIG`, `MANAGEMENT`, `PERMISSIONS`, `LIST`, `DETAILS`, `EDIT`, `MOD`, `TEMPLATE`, `DATA`, `NETWORK`, `SERVER`, `DATABASE`, `BACKUP`, `MIGRATE`, `UPGRADE`, `SECURITY`, `AUDIT` and `REPORT`.
- Execution and scheduling: `QUEUE`, `POLLING`, `ASYNC`, `CONCURRENCY`, `THREAD`, `PROCESS`, `PARALLEL`, `SCHEDULER`, `TASK` and `REWARD`.
- GitHub: `GITHUB_REPO`, `GITHUB_PULL_REQUEST`, `GITHUB_ISSUE`, `GITHUB_DISCUSSION`, `GITHUB_ACTIONS`, `GITHUB_README`, `GITHUB_RELEASE` and `GITHUB_COMMITS`. Both sizes come from [Octicons](https://github.com/primer/octicons); the license notice ships with the JAR.
- Project information: `LICENSE`, `LICENSE_OPEN_SOURCE`, `LICENSE_CLOSED_SOURCE`, `AUTHORIZATION`, `CONTRIBUTORS`, `AFDIAN`, `SUPPORT` and `ACKNOWLEDGMENTS`.
- AI services: `CHATGPT`, `CLAUDE`, `DEEPSEEK`, `GROK` and `GEMINI` have transparent 16×16 and 64×64 versions. Gemini is a white, concave four-point star.
- Contact and support: `QQ`, `WECHAT`, `X`, `DISCORD`, `TELEGRAM`, `WHATSAPP`, `FACEBOOK`, `EMAIL`, `QQ_MAIL`, `ICLOUD_MAIL` and `GMAIL`. Use `UiButton.setLeadingIcon` alongside localized button text.
- Video platforms: `BILIBILI`, `DOUYIN`, `KUAISHOU` and `YOUTUBE`.
- General entry points: `MUSIC`, `APP`, `QUESTION`, `CALCULATE`, `CPU`, `GPU`, `DISK` and `MAP`. `AE_CHANNEL` represents AE2 ME network channels using the indicator lines on a smart cable.
- Shops and rewards: `SHOP`, `BANK`, `MEMBERSHIP`, `POINTS`, `REDEEM`, `COUPON`, `POWER`, `SPARK` and `CURRENCY`.
- Trading and accounts: `TRADE`, `PAYMENT`, `BILL`, `SETTLEMENT`, `WITHDRAW`, `TRANSFER`, `BALANCE`, `BUY` and `SELL`. Business navigation can use `CURRENCY`, `REDEEM`, `MEMBERSHIP` and `SHOPPING_CART`, with `FAVORITE` and `FILTER`.
- Content fields: `TITLE`, `TAG`, `COVER`, `CATEGORY`, `CHINESE`, `SUMMARY`, `BODY_TEXT` and `TEXTURE`.
- Additional mod and theme entries: `SOPHISTICATED_BACKPACKS`, `SHBUI`, `DARK_MODE`, `LIGHT_MODE` and `CUSTOM_THEME`. The white `SHBUI` outline comes from the mod's colored `cover.png`.

```java
// Panel bounds use Screen logical coordinates; entries is the current page's List<GraphicDisplay.Entry>.
var area = new GraphicDisplayLayout.Bounds(x, y, width, height);
var layout = GraphicDisplayLayout.arrange(area, entries.size(), 28, pageCount > 1);

// Register controls in init. changePage updates the page, layout and both buttons' active state.
var previous = addRenderableWidget(GraphicDisplay.pageButton(
        layout.previous(), false, preferences, button -> changePage(-1)));
var next = addRenderableWidget(GraphicDisplay.pageButton(
        layout.next(), true, preferences, button -> changePage(1)));
previous.active = previous.visible && pageNumber > 1;
next.active = next.visible && pageNumber < pageCount;

// Use the current page's layout in render; page numbers start at 1.
GraphicDisplay.drawRows(graphics, font, layout, entries, 1, textColor, rowColor);
GraphicDisplay.drawPageNumber(graphics, font, layout, pageNumber, pageCount, textColor);
super.render(graphics, mouseX, mouseY, partialTick);
GraphicDisplay.drawRowTooltip(graphics, font, layout, entries, mouseX, mouseY);
```

The default layout uses two columns at widths of at least 240 GUI units, otherwise one. To customize columns, pass `Grid(minimumColumnWidth, maximumColumns)` to `arrange`. For example, `new Grid(68, 4)` reflows between one and four columns depending on available width.

```java
var grid = new GraphicDisplayLayout.Grid(68, 4);
int capacity = GraphicDisplayLayout.pageCapacity(area, 28, true, grid);
var layout = GraphicDisplayLayout.arrange(area, entries.size(), 28, true, grid);
```

`pageCapacity` calculates how many complete entries fit using the same inner bounds, row height, pagination and grid rules, and can split the current page. A capacity of 0 requires more content space. Recalculate layout after page or scale changes. Reserve at least 40 GUI units of row height for 2× icons. Pass localized `Component` status text; hover reveals labels that exceed the row height. If the host adds viewport clipping, show a tooltip only while the pointer is in the host's visible area. The host manages base and page status; paging controls retain `UiButton` click, keyboard focus and narration.

### Network-style graphic UI API

`client.gui.scaled.NetworkTheme` provides dark translucent rounded panels, blue-outlined title and settings rows, native edit boxes and outlined tabs. Forge and NeoForge native `Screen` use the same APIs. Bounds are logical GUI coordinates and must be recalculated in `init()` after scaling.

- `NetworkTheme.drawPanel()` draws a panel; `drawField()` displays a title or read-only setting. `labelColor()` colors the label while values retain their own colors.
- `NetworkTheme.tab()` returns a `UiButton`. Mark the current page with `setSelected()` and pass a localized `Component` for tooltips and narration. Reserve at least 20×20 GUI units for icon buttons. Built-in 16×16 transparent PNG icons are `HOME`, `LIST`, `WIRELESS`, `TOPOLOGY`, `STATISTICS`, `MEMBERS`, `SETTINGS` and `ADD`; custom `GraphicIcon` values also work.
- `NetworkTheme.editBox()` returns a native `EditBox`. Set the initial value before registering `setResponder()`; use `setFilter()` for input restrictions and the consumer's own confirm action to submit.
- `NetworkToggle` updates its display with `setValue()`. Mouse, Enter or Space requests the opposite value. While waiting for a server response, set `active = false`; update the displayed value and re-enable it on response. Toggle rows need at least 40×14 GUI units.
- Status text and graphics use `GraphicDisplay.drawRows()`; `Component` styles can color values. `UiPreferences` controls high contrast and reduced motion.

```java
// Register controls in init(). Bounds is GraphicDisplayLayout.Bounds.
var home = addRenderableWidget(NetworkTheme.tab(new Bounds(x + 12, y - 24, 20, 20),
        Component.translatable("example.tab.home"), GraphicIcon.HOME, preferences,
        button -> showHomePage()));
home.setSelected(isHomePage());

var name = addRenderableWidget(NetworkTheme.editBox(font, new Bounds(x + 12, y + 38, 160, 18),
        Component.translatable("example.setting.name"), preferences));
name.setValue(savedName);
name.setResponder(value -> nameDraft = value);

var bypass = addRenderableWidget(new NetworkToggle(new Bounds(x + 12, y + 100, 160, 18),
        Component.translatable("example.setting.bypass"), serverBypassEnabled, preferences,
        enabled -> requestBypassChange(enabled)));

// In render(), draw the background, title and status rows before super.render(...) draws native controls.
NetworkTheme.drawPanel(graphics, new Bounds(x, y, 184, 132), preferences);
NetworkTheme.drawField(graphics, font, new Bounds(x + 12, y + 10, 160, 18), networkTitle, preferences);
var statusLayout = GraphicDisplayLayout.arrange(new Bounds(x + 12, y + 62, 160, 30),
        statusEntries.size(), 30, false, new GraphicDisplayLayout.Grid(160, 1));
GraphicDisplay.drawRows(graphics, font, statusLayout, statusEntries, 1, DarkModeTheme.TEXT, 0);
```

The consuming mod supplies the example's translation keys, page changes, drafts, status entries and response logic.

### Full-page network layout and Screen template

`client.gui.display.NetworkScreen` can be passed directly to `Minecraft#setScreen`. It arranges 1–7 top icon tabs, a separate Add button on the right, a network title, settings rows, graphic status and toggles. A back icon and Escape return to the parent. Each tab retains its page number, narrow windows wrap, and tall content is paged by complete rows.

```java
// Run on the client thread. The consumer supplies the translation keys, state and otherTabs.
var overview = new NetworkScreen.Tab(
        Component.translatable("your_mod.network.overview"), GraphicIcon.HOME, state::networkTitle,
        List.of(
                new NetworkScreen.Edit(Component.translatable("your_mod.network.name"), box -> {
                    box.setMaxLength(40);
                    box.setValue(state.nameDraft());
                    box.setResponder(state::setNameDraft);
                }),
                new NetworkScreen.Field(Component.translatable("your_mod.network.priority"), state::priorityText),
                new NetworkScreen.Field(Component.translatable("your_mod.network.limit"), state::limitText),
                new NetworkScreen.Status(state::transferAndBufferEntry),
                new NetworkScreen.Toggle(Component.translatable("your_mod.network.surge"),
                        state::surgeEnabled, state::canChangeSurge, state::requestSurge),
                new NetworkScreen.Toggle(Component.translatable("your_mod.network.bypass"),
                        state::bypassEnabled, state::canChangeBypass, state::requestBypass),
                new NetworkScreen.Toggle(Component.translatable("your_mod.network.chunk_loading"),
                        state::chunkLoadingEnabled, state::canChangeChunkLoading, state::requestChunkLoading)
        ));

var tabs = new ArrayList<NetworkScreen.Tab>();
tabs.add(overview);
tabs.addAll(otherTabs); // Add at most 6 more tabs, each with its own title and content rows.
var add = new NetworkScreen.AddAction(Component.translatable("your_mod.network.create"), state::openCreate);
Minecraft.getInstance().setScreen(new NetworkScreen(parent,
        Component.translatable("your_mod.network.screen"), preferences, tabs, add));
```

`Status` returns a `GraphicDisplay.Entry`; its label can join transfer and buffer lines with `CommonComponents.NEW_LINE` and color the values separately. `Edit` configuration rebuilds the control when changing tabs, pages or scale; the consuming mod owns the draft. `Toggle` reads the confirmed value each frame and its submit callback sends only a request. Return `false` from `canChange...` while awaiting confirmation.

Existing screens can use `NetworkLayout.arrange(width, height, tabCount, hasAdd, rowKinds, font.lineHeight, pageIndex)` independently. Row kinds are `FIELD`, `STATUS` and `TOGGLE`; the resulting `Bounds` can be passed to `NetworkTheme`, `GraphicDisplay` or native controls. `firstRow` is the original row index for the current page, and `pageIndex` starts at 0. If there is not enough space for a complete control, the result is empty.

### Toast notifications

On a Forge or NeoForge client, call `client.gui.scaled.UiToast` after receiving a task result. The library displays a notice over the current screen or game HUD without taking mouse or keyboard focus.

```java
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToast;

UiToast.taskCompleted(Component.translatable("your_mod.task.example"));
UiToast.show(UiToast.Kind.WARNING,
        Component.translatable("your_mod.notice.warning"),
        Component.empty(), UiToast.Effect.POP);
```

By default it slides from outside the right edge into the upper-left corner, remains briefly, then exits right. Effects include `DROP_FROM_TOP`, `FADE`, `POP` and `NONE`. Kinds are `INFO`, `SUCCESS`, `WARNING` and `ERROR`.

Notices appear in sequence. Their default base duration is 3500 ms and is affected by Minecraft's notification duration setting. The full `show` overload accepts duration and `UiPreferences`; reduced motion displays the notice immediately. Visual content is limited to six lines, so localized short text is recommended. When system narration is enabled the full content is read.

#### Swipe-to-dismiss notifications

Use `UiSwipeToast` inside a native `Screen`. `BOTTOM_RIGHT` is fixed to the lower-right corner; `INLINE` uses the slot width supplied by the caller, and `occupiedHeight()` helps arrange following items. Both placements must be registered as renderable widgets for drawing and native keyboard focus. Developer Panel → Control Gallery → Floating Notifications provides examples of both.

```java
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSwipeToast;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;

// Create inside a Screen subclass. The caller supplies restore as the undo action.
var toast = addRenderableWidget(new UiSwipeToast(this, UiSwipeToast.Placement.BOTTOM_RIGHT,
        new UiSwipeToast.Content(Component.translatable("your_mod.archive.title"),
                Component.translatable("your_mod.archive.details"), null,
                new UiSwipeToast.Action(Component.translatable("your_mod.archive.undo"), restore)),
        new UiSwipeToast.Options(4000, true, true, true), UiSwipeToast.Style.DEFAULT,
        UiPreferences.configured(), reason -> onToastClosed(reason)));
```

Drag downward past the threshold or flick quickly to dismiss. Short drags, reverse releases and cancellation on focus loss return the toast to its position. After Tab focus, Left/Right select an action or close, Enter/Space activates it, and Esc dismisses a swipe-enabled toast. Hover pause is configurable; dragging, focus and window focus loss pause the timer. `Fuse.TOP`, `BOTTOM` and `NONE` only affect the progress line; `setDurationMs(0)` keeps the notification visible indefinitely.

`setOpen(false)` closes it. The close callback fires once after the exit animation and inline slot collapse, distinguishing `TIMEOUT`, `SWIPE`, `ACTION`, `CLOSE`, `ESCAPE` and `PROGRAMMATIC`. Screen rendering advances the control, so retain it until the close callback. `setOpen(true)` reopens it and restarts the timer. Use `setInlineBounds(x, y, availableWidth)` for inline GUI coordinates.

`SwipeGesture` (displacement, velocity and downward-swipe detection) and `PausableCountdown` (pause, reset and remaining fraction) can also be used independently. They take a caller-supplied monotonic millisecond clock and do not depend on Screen or notifications. `ShBetterUiApi.interactions()` also exposes `swipeGesture()`, `countdown(...)` and `swipeToast(...)`.

### Neutral glass render preview

The earlier glass is on the left and the updated glass on the right. Glass uses the actual shader and palette in an off-screen render; the background, font and layout are a test fixture, not an in-game Minecraft screen. See the [glass maintenance notes](docs/neutral-glass-maintenance.md) for text-glow integration and verification, and [TODO](TODO.md) for in-game acceptance items.

![Neutral glass render comparison](docs/previews/2026-11-07-neutral-glass-comparison.png)


## NeoForge 1.21.1

`neoforge-1.21.1/` is an independent build using Java 21, NeoForge 21.1.251, ModDevGradle 2.0.148 and Gradle 9.2.1. NeoForge shares glass palettes, panels, effects and shaders, with platform-specific mod entry points, client events, tooltips, blur pipeline and native ingredient adapters.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:TEMP = 'C:\jtmp'
$env:TMP = 'C:\jtmp'
.\neoforge-1.21.1\gradlew.bat -p neoforge-1.21.1 test build --no-daemon --console=plain
```

The runtime JAR is `neoforge-1.21.1/build/libs/sh_better_ui-neoforge-1.21.1-<version>.jar`. NeoForge uses Mojang names and does not need the Forge 1.20.1 devJar/reobfJar split; consumers use this runtime JAR for both compilation and the game.

Install **KotlinForForge NeoForge 5.10.0 or a compatible 5.x** separately in the game; do not use Forge 1.20.1's 4.x. The verified 5.10.0 dependency uses Kotlin 2.2.20, on the same version line as the library's Kotlin compiler 2.2.21. The modpack supplies Kotlin/KFF; the library does not embed it. `FTB Quest Nexus/neoforge` directly references this project's artifact for glass UI.

The shared shader declares `WaveOrigin` as GLSL `vec2`, matching Minecraft's `float/count=2` upload. Resource tests check each platform's actual build output.

The NeoForge version supplies native `Screen` APIs and the optional `compat.ldlib2.LdLib2UiBridge`. Install LDLib2 `2.2.44` to use the bridge; the first build must be online to resolve its compile dependency. Forge's `compat.ldlib` package ships with the Forge artifact. See [NeoForge verification notes](docs/neoforge-verification.md) and [TODO](TODO.md) for tests and outstanding checks.

## NeoForge 26.x alpha

`neoforge-26.1.2/` targets Minecraft 26.1.2, NeoForge 26.1.2.126 and Kotlin for Forge 6.2.0. `neoforge-26.2/` targets Minecraft 26.2, NeoForge 26.2.0.93 and Kotlin for Forge 6.3.0. Start Gradle with JDK 25. The shared kernel still compiles to Java 17 bytecode; builds use the existing JDK 21 compiler toolchain and JDK 17 kernel test runtime.

```bash
bash neoforge-1.21.1/gradlew -p neoforge-26.1.2 build --no-daemon --console=plain
bash neoforge-1.21.1/gradlew -p neoforge-26.2 build --no-daemon --console=plain
```

Development JARs are in each target's `build/libs/` and carry the `-alpha.8` version suffix. Both versions share the kernel, platform viewport, ingredient catalog, project editing services, search index, page model and Kotlin DSL. Extension modules must declare `NEOFORGE_26_1_2` or `NEOFORGE_26_2` in `ModuleDescriptor.platforms`.

Use `ShBetterUiApi.controls()` to create buttons, toggles, integer sliders, steppers, fixed or scrollable section bars, underline tabs and disclosure controls. Register native controls on the `Screen`; for toggles register `button()`, and for fixed section bars register each of `buttons()`. Before closing, hosts may pass Esc to the focused control to cancel slider editing or tab dragging. Custom screens can call `GuiGeometry.fill` in `extractRenderState` to submit `FilledGeometry<Geometry2.Local>` with the current GUI pose, clip and ARGB color.

### Background blur, Liquid Glass and control panel

After entering a world, press F8 or open SH-Better-UI configuration from the NeoForge mod list. The panel provides glass, display and appearance settings with preview and application of blur, refraction, edge highlights, theme, high contrast and reduced motion. Successful application writes `config/sh_better_ui/control-panel.properties` in the current instance. Reset can be undone, and leaving an unapplied draft requires confirmation. Consumers can read applied settings or open the panel through `ShBetterUiApi.settings()`.

The Tools page displays mod version, frame rate, heap memory, window scale and the graphics backend actually in use. Feedback links follow Minecraft chat settings for web links and confirmation. Change the Open Panel shortcut through Minecraft's key bindings; the panel retains its draft on return.

Tools → Developer Gallery opens 76 samples: all 75 legacy samples and the scrollable section bar added in 26.x. Native samples cover controls and transitions, spatial materials, dynamic themes, particle effects, real music playback, synchronized lyrics and the gallery's dynamic island. The gallery can save an appearance preview independently and preserves the control-panel draft on return. See the [26.x gallery migration checklist](docs/NEOFORGE_26_GALLERY_GAPS.md) for version coverage.

After the host extracts the background, call `ShBetterUiApi.blur().background(graphics)`. Full-screen blur follows Minecraft's menu background blur video setting; foreground extracted afterward remains clear. To sample locally inside glass controls, first call `ShBetterUiApi.blur().prepareBackdrop(graphics)`, then extract buttons, sliders or `LiquidGlassSurface`. If both effects are needed, request full-screen blur first. Glass uses a same-frame GPU snapshot for local blur, refraction and edge highlights; high contrast uses a solid base.

### Vulkan and GLFW 65540

Minecraft 26.1.2 uses OpenGL. Minecraft 26.2 provides native Vulkan and OpenGL, using the same control extraction code. On 26.2 instances without a saved backend preference, Vulkan is tried first after the NeoForge early loading window is disabled; Minecraft falls back to OpenGL if initialization fails. Saved graphics API preferences and launch arguments remain effective.

The NeoForge 26.2 early loading window uses OpenGL and cannot be reused by Vulkan. The fixed version starts with OpenGL if that window exists and preserves the saved preference. To use Vulkan on 26.2, exit the client, set `earlyWindowControl = false` in the instance's `config/fml.toml`, then choose Prefer Vulkan in video settings and restart. Development `runClient` preconfigures this for a new `run-vulkan` directory; `runClientOpenGl` uses a separate `run-opengl` directory. See the [upstream NeoForge issue](https://github.com/neoforged/NeoForge/issues/3230).

Isolated client smoke tests load the packaged JAR and check control and gallery input, blur and glass pixels, settings application, the mod-list configuration button and the in-world F8 keyboard callback. The final command points the Vulkan driver manifest at a nonexistent file only in that smoke-test process to verify real initialization-failure fallback. The game exits automatically and screenshots are saved under each instance's `build/widget-smoke/` directory.

```bash
bash neoforge-1.21.1/gradlew -p neoforge-26.1.2 -I ../gradle/neoforge-26-widgets-smoke.init.gradle runWidgetSmoke
bash neoforge-1.21.1/gradlew -p neoforge-26.2 -I ../gradle/neoforge-26-widgets-smoke.init.gradle runWidgetSmoke
bash neoforge-1.21.1/gradlew -p neoforge-26.2 -I ../gradle/neoforge-26-widgets-smoke.init.gradle runWidgetSmoke -PwidgetSmokeBackend=opengl
bash neoforge-1.21.1/gradlew -p neoforge-26.2 -I ../gradle/neoforge-26-widgets-smoke.init.gradle runWidgetSmoke -PwidgetSmokeBackend=vulkan -PwidgetSmokeEarlyWindow
bash neoforge-1.21.1/gradlew -p neoforge-26.2 -I ../gradle/neoforge-26-widgets-smoke.init.gradle runWidgetSmoke -PwidgetSmokeVulkanFallback
```

Append `-PwidgetSmokeLayoutReview` to a smoke command to check native GUI Scale 3, narrow layout, keyboard page focus, refraction drag cancellation, and F8 and mod-list entry points in a separate `-layout` instance. Narrower logical dimensions are tested with `Screen.resize` in the same client.

Use `-PwidgetSmokeLayoutReview=4` to repeat in a `-layout-4` instance with a 1280 × 960 window and a long-text test language. This mode additionally checks Tab/Shift+Tab, wrapping of long-label tooltips and text produced by native narration. Control-panel and gallery titles and status, plus gallery button and slider default labels, show ellipses when they overflow; hover reveals the full text.

Append `-PwidgetSmokeNarrator` to a standard smoke command to enable Minecraft's system narrator in a separate `-narrator` instance, switch to Mode 2 through the game's keyboard callback and trigger narration. The smoke test restores that instance's narrator and volume settings afterward. On Windows, `-PwidgetSmokeNarratorAudio` can also attach a process audio loopback recorder to check the speech waveform emitted by the client JVM.

The remaining legacy gallery samples, other panel pages, unified screen host, tinted icons, text readability, rich text, declarative pages, networking and optional mod integrations remain in the porting scope. See [TODO.md](TODO.md).

### Anti-gravity particle API

Create a particle field through `ShBetterUiApi.graphics().antigravity()`; independent input tracking uses `ShBetterUiApi.interactions().pointerTracker()`. The developer control gallery provides four shapes, three palettes, pause and replay. See the [integration and parameter guide](docs/antigravity-api.md) and [documentation index](docs/README.md).
