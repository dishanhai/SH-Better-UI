<!-- 作者：山海／dishanhai -->
# 富文字 API

## 原生组件与全局 FormattedCharSequence

开发者预览、PNG 别名和 `,rndr:…` 效果的完整用法见 [富文本图标与效果](RICH_ICON_EFFECTS.md)。仅 `<rich:…>` 触发解析，普通 `<item>` 不解析。内置图片可写作 `<rich:vmaterial-shop>`；例如 `<rich:vmaterial-shop,rndr:hologram>` 使用全息效果。

`client.gui.text.UiRichText` 将内联物品接入原生组件、按钮、Tooltip 和 HUD 的文本链路。标记支持 `<rich:item=minecraft:diamond>` 和 `<rich:item>minecraft:diamond</rich:item>`；物品 ID 必须包含命名空间。无效标记保留原文，未注册的 ID 和空气物品显示屏障图标。只引用本地注册表，不下载资源或执行动作。

```java
var label = UiRichText.parse("<rich:item=minecraft:diamond> Diamond");
var item = UiRichText.item("minecraft:diamond");
FormattedCharSequence line = UiRichText.sequence(label);
int width = font.width(line);
// 普通 GuiGraphics.drawString / 26.1.2 GuiGraphicsExtractor.text 即可绘制。
```

静态文案放在消费模组的语言文件中；示例字串仅说明 API。原生入口自动处理组件、字符串及直接传入的正向序列；已重排的文字应在双向排序前使用 `prepare(Component)` / `sequence(Component)`。手动使用 `StringSplitter` 的宿主先 `prepare`，并在语言变更后从原组件重新处理。原组件不会被修改。

组合图标与名称时可用 `UiRichText.withItem(id, localizedLabel)`；它用中性根组件隔离图标专用字体，后续中文不会继承物品字体。`UiRichText.fit(font, component, width)` 会保留样式与行内对象并在空间不足时省略，避免先 `getString()` 再裁切导致样式丢失。

每个物品使用独立字体中的 U+FFFC，占宽 10、绘制大小 9×9 GUI 像素，跟随宿主变换与裁剪。原生量宽、换行、居中、截断和省略号均将物品作为一个整体；SHBUI 的既有文字色板与特效可包围物品标记。物品保留宿主的 click/hover 元数据，占位字形的 font/insertion 由桥接保留使用；不会自动添加鼠标操作或 Tooltip。

Forge 1.20.1 与 NeoForge 1.21.1 在原生 Font 批次内复用 GUI 物品与 PNG 渲染，要求标准 `MultiBufferSource.BufferSource`；其他 buffer 实现只保留占位宽度。NeoForge 26.1.2 在 `GuiGraphicsExtractor.text` 中提交物品与 PNG，直接用于世界渲染的 Font 批次只保留占位宽度。可变尺寸图像、流体、实体和交互控件继续使用下方的 `RichTextView`，不投影到固定行高的原生序列。

## 世界 HUD 文字

`UiHudRichText` 提供單行 HUD 文字入口。`prepare(Component)` 先解析 SHBUI 格式標記，宿主再量寬、截斷及分頁；`prepare(RichDocument)` 接受文字和翻譯節點，保留顏色、字型、粗體、斜體、底線等樣式。HUD 不承載圖片或互動節點，傳入這些節點會明確拒絕。語言或來源變更後須重新 prepare。

```java
var title = UiHudRichText.prepare(RichText.document(UiId.parse("example:hud"))
        .text("Status", new RichStyle(0x66ccff, true, false, false, false, false, null))
        .build());
var animated = UiHudRichText.prepare(UiTextEffects.parse("{fire}Status{/}"));
// 排版並取得已更新背景採樣的 TextReadabilityPalette 後：
UiHudRichText.draw(graphics, font, animated.getVisualOrderText(), x, y, textColor, preferences, palette);
```

`UiHudPanel.Row` 會自動 prepare，面板繪製時使用同一入口；NeoForge 26.1.2 的 `GuiGraphicsExtractor` 提供對應簽名。效果先取樣、再校正顏色對比；高對比與減少動效沿用 `UiPreferences`。有位移或描邊效果的文字需要逐字繪製，普通文字仍按整行繪製。效果註冊及標記見 [文字效果](effects.md#格式文字與逐字效果)。

Forge 1.20.1 與 NeoForge 1.21.1 使用同一套 Java API。進入世界後執行 `/shbetterrichtext`。基礎頁展示同行 PNG、寬圖及缺圖占位。進階頁提供文件交換示例、選取、編輯、復原、行內控件和效果切換。另可用 `/shbetterrichsync` 開啟媒體與同步示例，查看 GIF、占位和由整合伺服器提供數值的自訂能量條。Back 或 Esc 返回。可用 `/shbetterricheditor` 開啟遊戲內編輯器，以同一套渲染管線預覽本機文件。 [編輯、交換與設定](RICH_TEXT_RELEASE.md)

| 入口 | 契約 |
| --- | --- |
| `kernel.api.richtext.RichDocument(List<InlineNode>)` | 複製節點列表，保持順序。節點 ID 由文件擁有者提供，同一文件不可重複。文件及其節點可跨執行緒讀取。 |
| `InlineNode`、`TextRun(UiId, String, RichStyle)`、`ImageNode(UiId, UiId, int, int, String)` | `TextRun(UiId, String)` 仍可使用。`RichStyle.EMPTY` 繼承宿主預設顏色和字型。圖片參數依序為節點 ID、素材 ID、宣告寬／高和替代文字。寬高須為正。`RichTextView.image` 接受 Minecraft `ResourceLocation` 並轉成純資料 ID。 |
| `RichTextLayout.layout(document, maxWidth, font)` | 算出 `RichLayoutSnapshot`；寬度使用邏輯 GUI 單位，零寬回傳空快照。`FontMetrics` 必須提供正 ascent、非負 descent 及非負且隨字素前綴單調的文字寬度；若使用原生 Minecraft 字型，在客戶端渲染執行緒量測。 |
| `RichLayoutSnapshot`、`LineBox`、`Fragment`、`Rect` | 列表不可修改，所有位置相對於內容原點。`Fragment.textOffset()` 是文字片段在原始 run 的 UTF-16 偏移，舊三參數建構式仍可用。`hitTest` 查詢半開矩形。`visibleLines(top, bottom)` 用二分搜尋回傳相交行。 |
| `client.visual.RichTextView.attach(module, scope, node)` | 於客戶端渲染執行緒掛載。`scope` 必須是模組在本次 Screen 掛載中用於 `bindVisual` 的作用域，或該作用域之下同模組且仍開啟的子作用域。Screen 只有一份 `bindVisual`，各子作用域的視圖可共用同一個 pass 並獨立釋放。`node` 使用該綁定的 `AlignmentLayout.Node`。 |
| `prepare(document, font, maxWidth)`、`draw(pass, argb)` | 在 `bindVisual` 的取樣回呼內先 `prepare` 再安排節點；在既有 `LayoutPass` 內繪製。對節點設置從 `(0,0)` 起且寬度不超過 `maxWidth` 的 `AlignmentFrame` 局部裁剪。`argb` 是預設文字顏色；圖像使用自身 RGBA。 |
| `currentSnapshot()`、`hitTest(guiX, guiY)` | 在客戶端渲染執行緒讀取最後成功繪製且仍有效的 presentation，可取得外層幀、局部幾何、轉換與資源世代。命中接受 Screen GUI 座標，不消費事件。首次呈現前、完全裁剪、所屬視覺節點繪製失敗、作用域關閉或重載後未再繪製時回傳空結果。 |
| `invalidate()`、`isActive()`、`close()` | 視口重設前可清除量測和呈現；尺寸、文件、字型或資源世代變更會使原結果失效。可提前關閉；作用域關閉時自動釋放資源觀察及快取。 |
| `view.bindRemoteImage(textureId, uri, policy)` | 渲染執行緒上的顯式授權，將一個本機貼圖別名交給 HTTPS 媒體管線。下載完成前照常保留占位。作用域關閉取消工作，資源重載保留別名授權並重新取得。 |
| `view.mediaStatus(textureId)` | 渲染執行緒上的最新狀態。未請求、排隊或解碼中、可用及失敗分別以 `UNREQUESTED`、`LOADING`、`READY`、`UNAVAILABLE` 表示。當幀繪製狀態以 `Presentation.availableTextures()` 為準。 |
| `view.drawAtMediaTime(pass, argb, preferences, scrollY, mouseX, mouseY, mediaElapsedMs)`、`view.mediaCycleMs()` | 實驗性編輯預覽。以非負毫秒位置取樣圖像媒體。佈局、命中及資源世代仍隨當幀。手動定位可檢查減少動效時通常凍結的幀。`mediaCycleMs()` 回傳已載入圖像的最長循環時間，只有靜態圖像或仍在載入時為 0。 |
| `view.bindExtensions(registry)` | 借用由提供方 scope 管理的登記冊。每次呈現取樣一次資料來源，渲染器透過有界畫布使用相同的行盒和裁剪。 |

必填值傳入 `null` 時會失敗。負寬度、重複 ID、非法圖片尺寸與錯誤作用域配對以參數異常拒絕；呼叫未啟用的 view，或未 prepare 就繪製，屬於呼叫契約錯誤。等待解碼、缺圖、格式不支援與超額素材顯示占位。可透過 `Presentation.availableTextures()` 判別該次呈現已可繪製的素材，幾何仍以節點宣告為準。

下列片段假定宿主已透過 `ScreenService.mount` 取得 `tree`，以及一個 `AlignmentLayout` 節點 `node`。靜態文字與替代文字由宿主本地化，也可使用下方的 `LocalizedText`。

```java
var document = new RichDocument(List.of(
        new TextRun(new UiId("example", "intro"), intro.getString()),
        RichTextView.image(new UiId("example", "diamond"),
                ResourceLocation.tryParse("minecraft:textures/item/diamond.png"),
                16, 16, diamondAlt.getString()),
        new TextRun(new UiId("example", "tail"), tail.getString())));
var component = tree.openChild(ScopeKind.COMPONENT, new UiId("example", "rich_content"));
var view = RichTextView.attach(module, component, node);
var binding = module.engine().require(UIApis.LAYOUT).bindVisual(module, tree, layout, time -> {
    int w = contentWidth();
    var content = view.prepare(document, font, w);
    int h = Math.min(content.height(), visibleHeight());
    node.update(AlignmentFrame.box(new Rect(left(), top(), w, h)).withClip(new Rect(0, 0, w, h)));
});
// Screen.render 中畫完背景後：
if (minecraft.getOverlay() == null) {
    binding.renderScreen(frame, pass -> view.draw(pass, 0xfff4fbff));
} else {
    view.invalidate();
}
```

`renderScreen` 只能進入原生 Screen Pre 與 Post 之間的 UUK 階段。資源重載時，Minecraft 的 `LoadingOverlay` 可能直接呼叫底下的 Screen，因此此分支只畫原生背景與控件，略過富文字呈現和懸停查詢。

## 文件、交換與選取

```java
var prefix = UiId.parse("example:guide");
var draft = RichText.document(prefix)
        .localized("example.guide.title", "Guide", List.of(), RichStyle.EMPTY)
        .paragraph(4)
        .text(new UiId("example", "guide/body"), body, RichStyle.EMPTY)
        .build();
var copy = RichDocumentCodec.decode(RichDocumentCodec.encode(draft));
var styled = RichTextMarkup.parse(prefix, "[b][color=#66ccff]Guide[/color][/b][p]"
        + RichTextMarkup.escape(body));
```

- Builder 的自動 ID 為 `prefix.path()/node_N`，相同輸入順序產生相同 ID。插入節點後仍須跨版次引用的節點應提供顯式 `UiId`。重複 ID、必填值缺失、非法尺寸或樣式以參數異常拒絕。
- 標記允許巢狀 `[b]`、`[i]`、`[u]`、`[s]`、`[color=#RRGGBB]`、`[font=namespace:id]`，以及 `[br]`、`[p]`、`[tr=key;fallback;arg]` 和 `[img=namespace:path;width;height;alt]`。字面左括號用 `[[` 或 `escape`。解析結果是被動資料。
- v1 JSON 外層是 `{"version":1,"nodes":[...]}`，節點皆含 `id` 和 `type`。基本類型為 `text`、`localized`、`paragraph`、`image`。標記和交換格式的圖片可引用本機 PNG、GIF 或 APNG。解碼保留穩定 ID，拒絕未知類型、版本、字段、原始 JSON 重複字段及無效 Unicode。原始 JSON 上限 256 KiB UTF-8、4,096 個節點、16 層巢狀、每組翻譯 32 個字面參數。Loader 已提供的 DFU `CODEC` 支援 JsonOps／NbtOps。呼叫者須在將網路位元組交給其他 ops 解析前做傳輸限額。行內物件與執行回呼由 Java 宿主組裝。
- `RichTextComponents.resolve(prefix, component)` 在客戶端產生 `Snapshot(document, nativeStyles)`。傳給 `prepare(snapshot, font, width)` 可保留繼承樣式及可信的 hover、click、insertion。原生 Component 由宿主保留，翻譯或來源改變時重新解析。View 的 `componentStyleAt` 只回傳已呈現的原生樣式，動作由宿主明確派送。 `toComponent(document)` 做被動文字投影，`toComponent(snapshot)` 可還原可信樣式，圖片與物件投影為替代文字。
- `RichTextSelection.Position(id, offset)` 使用文字 run ID 和 UTF-16 字素邊界。`view.caretAt`、`select`、`selectedText` 以已呈現幾何取樣。跨節點複製保留原換行、被軟換行裁掉的分隔空白及物件替代文字。`RichTextEdit.replace` 原子替換單一字面 run 的範圍，保持 ID 和樣式；宿主擁有撤銷歷史、焦點、剪貼簿和 IME 的組字階段。

## 行內互動

```java
var id = UiId.parse("example:accept");
var control = new RichObjectNode(id, new RichObjectNode.Button(buttonLabel), 6, 1, 16, description);
var document = RichText.document(UiId.parse("example:controls")).add(control).build();
view.interaction().onButton(id, this::accept);
```

`RichObjectNode` 包含 `Frames`、`Item`、`Block`、`Fluid`、`Entity`、`Icon`、`Button`、`Slider`。宣告比例和邏輯高度決定位置，缺失內容使用同一盒。物品可由宿主呼叫 `bindItem` 綁定同 ID 的原生 stack 副本。以 `onSlider` 收到放開後的提交，或鍵盤步進；`onGesture` 為選用次要、雙擊及長按，`draggable` 和 `onDrop` 交換節點 ID。回呼屬掛載的客戶端執行緒，關閉時清除。

宿主按原生焦點及 UUK 的遮擋順序轉送 `press`、`drag`、`release`、`keyPressed`、`keyReleased`；已處理事件應直接回傳。Screen 在按下成功時可呼叫 `capture(screen, button)`，沿用 UUK 的指標捕獲和取消。當焦點移出呼叫 `clearFocus`，用 `narrate` 和 `renderTooltip` 接原生旁白與 Tooltip。採用可信回呼時，遊戲動作仍由宿主原有權限流程決定。

### 以模組作用域捕獲指標

在模組描述中宣告 `POINTER_CAPTURE` 資源，並在原生 Screen 掛載的樹或元件作用域取得 `CAPTURE_INPUT` 與 `BLOCK_VANILLA_INPUT` 授權。以掛載時使用的模組身分、節點和宣告的資源 ID 捕獲已接受的按下。範例沿用上方按鈕的 `id`，`componentScope` 是富文字視圖所屬的掛載節點。

```java
var claim = UiId.parse("example:rich_pointer");
var engine = module.engine();
var interaction = view.interaction();
engine.require(UIApis.INPUT).target(module, componentScope, 0,
        event -> event instanceof NativeInput.Pointer pointer
                && pointer.action() == NativeInput.PointerAction.PRESS
                && view.hitTest(pointer.x(), pointer.y()).filter(hit -> hit.node().id().equals(id)).isPresent());
engine.events().subscribe(InputService.EVENT, componentScope, UIEventPhase.TARGET, 0, event -> {
    if (event.payload() instanceof NativeInput.Pointer pointer
            && pointer.action() == NativeInput.PointerAction.PRESS
            && interaction.press(pointer.x(), pointer.y(), pointer.button())
            && interaction.capture(module, componentScope, claim, pointer.button())) {
        event.consume();
    }
});
```

捕獲成功後，由原生橋接傳送拖動和放開。作用域關閉或撤回授權時，共用捕獲服務會取消手勢並接住該按鍵尚未送達的放開。資源槽已被占用時 `capture` 回傳 `false`，缺少必要權限時拋出權限異常，兩者都會清除該次暫存手勢。採用舊 Screen 入口的宿主仍可透過 `capture(screen, button)` 使用原有派送。

## 視覺配置

`RichTextEffects.NONE`、`GLASS`、`REVEAL` 可傳給 `view.draw(pass, color, preferences, effects, scrollY, mouseX, mouseY)`。捲動偏移採邏輯 GUI 像素。選用的最後一個 `Consumer<GuiGraphics>` 可在延後前景完成後，以 Screen 座標補畫 Tooltip。正常前景的 Tooltip 由宿主繪製。Backdrop 依宿主 blur allowlist、偏好、裁剪和 framebuffer 像素預算調度。高對比時使用不透明底板並校正文字顏色；減少動效凍結動態效果和 PNG 幀。測量與繪製仍沿用同一個 native font 和片段。

在已有的 Screen 掛載範圍內，可為特定富文字視圖啟用 UUK 文字可讀性。

```java
var view = RichTextView.attach(module, scope, node)
        .readability(module, new TextReadabilityPolicy(true, true,
                new TextReadabilityPolicy.FixedContrast(4.5), null, null));
```

服務沿用視圖已排版的原生樣式片段、捲動變換和有效裁剪。文字 ID 由內容節點、視圖節點與排版偏移組合。同一行相鄰的片段須有相同完整樣式、連續座標及精確的原生字形步進，才會共用一個選色結果。設定 `withLocalColor(true)` 可讓這些已排版的片段各自選色。單一原生字形序列仍按整段取樣，保留 Minecraft 的字形順序與組合字形。序列內更細的逐字形簇變色與合併仍待實作。 [富文字接入](../src/main/java/com/dishanhai/sh_better_ui/client/visual/RichTextView.java)

[圖文與互動示例](../src/main/java/com/dishanhai/sh_better_ui/client/gui/panel/RichTextExampleScreen.java) · [媒體與同步示例](../src/main/java/com/dishanhai/sh_better_ui/client/gui/panel/RichTextPipelineScreen.java) · [排版](RICH_TEXT_LAYOUT.md) · [資源](RICH_TEXT_MEDIA.md) · [擴充與 KubeJS](RICH_TEXT_EXTENSION.md) · [伺服器同步](RICH_TEXT_NETWORK.md) · [進度](RICH_TEXT_PROGRESS.md)
