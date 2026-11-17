# SH-Better-UI（煥界 UI 庫）

[简体中文](README.md) · **繁體中文** · [English](README.en.md)

作者：山海／dishanhai。

SH-Better-UI 是 Forge 1.20.1 與 NeoForge 1.21.1 的 Minecraft 客戶端 UI 庫。兩個平台共用玻璃材質、模糊、動畫、搜尋與物品／流體選取元件，並各自提供獨立模組 jar。接入方模組以外部依賴接入，更新時替換對應平台的 UI 庫 jar。

## Forge 1.20.1 環境與依賴

- Minecraft 1.20.1、Forge 47.4.16 或更高、Java 17（執行端）。
- [本機音樂 API 與 Windows 橋建置](docs/local-music.md)
- [網易雲／QQ 線上音樂與同步歌詞](docs/ONLINE_MUSIC_API.md)
- 必需 KotlinForForge 4.12.0（2.2.21 runtime），由整合包單獨提供；本庫不內嵌任何 jar。
- 使用 LDLib 介面時另裝 `ldlib-forge-1.20.1-1.0.33.b.jar`；原生 Screen 使用者無須安裝 LDLib。
- 開發時將 `kotlinforforge-4.12.0-all.jar` 及 `ldlib-forge-1.20.1-1.0.33.b.jar` 放入 `libs/`。

## 建置

Minecraft 26.1.2 的 NeoForge 移植使用獨立建置入口，當前為 [alpha 開發版](#neoforge-26x-alpha)。

使用 `build-local.ps1` 單獨建置 Forge，或執行 `簡易構建.bat` 依次建置 Forge 與 NeoForge。兩個指令碼以 JDK 21 啟動 Gradle。Forge 生產類使用 JDK 17 工具鏈，現有測試使用 JDK 21。Forge 使用 Gradle 8.8，建置包含 JUnit 與 reobfJar。批處理指令碼會清理兩個平台各自的 `build/libs`，並列出新產物。

首次建置時先移除命令中的 `--offline`，讓 Gradle 下載 JEI 編譯用 API。快取建立後可恢復離線建置。

`簡易構建.bat` 使用 UTF-8 無 BOM／Windows CRLF 換行，請保留此格式。雙擊執行後會保留結果視窗；自動化呼叫可傳入 `--no-pause`，退出碼沿用建置結果。

```powershell
.\gradle-install\gradle-8.8\bin\gradle.bat build --offline --no-daemon --console=plain
```

請以對應 `gradle.properties` 的 `mod_version` 取代 `<version>`。正式包為 `build/libs/sh_better_ui-<version>.jar`，原始重對映結果為 `build/reobfJar/output.jar`。開發包為 `build/libs/sh_better_ui-<version>-dev.jar`，只能用於編譯與開發環境。`raw` 包是建置中間產物，不能放入遊戲。

## 接入方接入

編譯時使用 dev 包，發行時同時安裝獨立正式 UI 庫。可透過 `publishToMavenLocal` 釋出 `com.dishanhai:sh_better_ui:<version>:dev` 及 sources，或使用本地檔案依賴：

```groovy
implementation files('../SH-Better-UI/build/libs/sh_better_ui-<version>-dev.jar')
```

接入方 `mods.toml` 宣告 `sh_better_ui` 版本範圍 `[1.0.2,2.0.0)`，不得再打入同一份 UI 類或 shader。`1.0.1` 提供星律煙灰材質及雙樣式 tooltip API；原 `1.0.0` 不具備這些入口。`1.0.2` 將煙灰面板調為中等模糊（12 GUI 單位半徑），保留其他樣式。接入方在客戶端初始化時以 `GaussBlur.allowScreen(predicate)` 註冊允許的介面；庫負責唯一的幀末事件與資源生命週期。

`gt_shanhai:ultimate_terminal` 的設定與報告介面、`gt_shanhai:pattern_generator` 的生成介面使用本庫的玻璃材質、模糊、彈簧與按鈕回饋。換頁時間軸由 `PageTransition` 提供；生成預覽的錯峰入場及滾動模糊強度由 `GuiMotion` 提供。介面的欄位、物品資料與操作流程仍由 `gt_shanhai` 處理。

資源重載會在渲染執行緒釋放舊管線；若目前仍開著已註冊的玻璃介面，立即以新資源預熱管線，無須關閉重開。登出則只釋放資源。遊戲驗收應包含保持介面開啟時 F3+T，確認模糊恢復且文字／圖示仍清晰。

`1.0.3` 新增 `GlassTheme.SMOKE`，共用星律的 `GlassColors.SMOKE`。本次材質維護改用 16 GUI 單位的密採樣背景模糊，保留微量提亮與邊緣柔光。接入方使用此主題時，最低依賴版本須為 `[1.0.3,2.0.0)`。商店可並存經典、深藍動態玻璃與中性煙灰；選擇器跟隨所選主題，煙灰內層控件不重複模糊。

### 玻璃材質介面

通用介面位於 `com.dishanhai.sh_better_ui.client.glass`、`client.blur` 與 `client.gui.scaled`。在面板內容之前繪製玻璃底，再畫文字與圖示：

```java
var theme = GlassTheme.GLASS;
theme.rootShadow(graphics, x, y, width, height);
theme.backdrop(graphics, x, y, width, height);
theme.box(graphics, x, y, width, height, GlassPalette.ROOT_EDGE, theme.panelBg);
```

`compat.ldlib.GlassPanelTexture` 提供 `root()`、`panel()`、`display()`、`page()` 與 `button()` 材質工廠。只有 LDLib 接入方引用此包；每個按鈕各自建立 `button()`，讓 hover 彈簧保持獨立。

### 客戶端毛玻璃配置

Forge 和 NeoForge 客戶端首次啟動後，在 `config/sh_better_ui-client.toml` 的 `[glassBackdrop]` 段調整毛玻璃背景。配置重新載入後，接下來的繪製會採用新值。

| 設定 | 預設值 | 範圍與效果 |
| --- | --- | --- |
| `enabled` | `true` | 設為 `false` 跳過毛玻璃背景後處理，保留半透明底板。 |
| `blurRadiusScale` | `1.0` | `0.0–2.0`，乘以各材質原有的模糊半徑。設為 `0.0` 時，仍可顯示色調和顆粒。 |
| `strength` | `1.0` | `0.0–1.0`，縮放整體效果強度。設為 `0.0` 跳過毛玻璃背景後處理。 |
| `tintScale` | `1.0` | `0.0–2.0`，縮放材質原有的色調混合量。 |
| `grainScale` | `1.0` | `0.0–2.0`，縮放材質原有的顆粒強度。 |

設定適用於透過本庫繪製的毛玻璃背景，包括根面板、分區、按鈕與提示框。經典主題沿用自己的繪製方式，換頁模糊和點擊折射波各自獨立。

### 主控面板的回饋與標籤欄設定

F8 主控面板使用彩虹點陣背景，頂層分為「配置」「開發配置」「模組概覽」。配置頁包含毛玻璃、顯示偏好、動效與回饋、標籤欄和快捷鍵；開發配置提供採樣品質、控件底板模糊及開發者面板入口。支援草稿預覽、應用、重置及撤銷重置，切換標籤保留草稿。設定儲存在客戶端配置的 `[ui]` 段，Forge 與 NeoForge 使用相同鍵。

模組概覽每秒重新整理可觀測的模組詳情、執行狀態、總體狀態、模塊狀態、系統與裝置資訊；硬體和載入器後設資料首次讀取後快取。長裝置名稱自動換行，各資訊組支援鍵盤聚焦和完整旁白。可選依賴只顯示安裝狀態，未將其標記為健康檢查結果。

| 配置鍵 | 預設值 | 作用 |
| --- | --- | --- |
| `hoverAnimation` | `true` | 懸停顏色過渡與卡片抬升；關閉後保留靜態懸停回饋。 |
| `pressAnimation` | `true` | 按鈕按壓縮放與釋放過渡；關閉後保留按下顏色。 |
| `selectionPulse` | `true` | 新選中按鈕的短暫強調動畫；選中標記始終保留。 |
| `tooltipDelayMs` | `0` | 按鈕、滑桿、滾動標籤欄的懸停提示延遲，範圍 `0–2000` 毫秒。 |
| `tabWheelEnabled` | `true` | 在標籤欄上使用滾輪瀏覽，不改變選擇。 |
| `tabDragEnabled` | `true` | 拖動瀏覽，超過閾值後放開不會誤選；Esc 取消並恢復原位置。 |
| `tabAutoReveal` | `true` | 自動顯示選中項；關閉後鍵盤焦點仍會滾入視口。 |
| `tabScrollStep` | `32` | 每格滾輪移動的 GUI 單位，範圍 `8–128`，支援小數滾輪輸入。 |

「減少動效」優先於各項動效開關。`ClientUiSettings.feedback()` 與 `tabs()` 分別回傳獨立的 `ControlFeedbackSettings`、`TabScrollSettings`；編輯一組設定會保留其餘組。已有 `ClientUiSettings` 三／四參數和 `UiPreferences` 三參數建構子仍可使用，新增項取預設值。控件使用建立時傳入的偏好快照；已開啟的呼叫方畫面可用 `setPreferences()` 和 `setSettings()` 更新。

### 可捲動標籤欄 API

`UiScrollableSectionBar` 是一個原生控件，內部複用 `UiButton` 與 `UiSectionBar.Section`。宿主只註冊標籤欄一次；`buttons()` 供讀取按鈕位置與狀態，不再單獨註冊內部按鈕。

```java
var tabs = ShBetterUiApi.controls().scrollableSectionBar(
        new Bounds(x, y, width, 22), UiPreferences.configured(), sections,
        selectedIndex, index -> requestedSection = index);
addRenderableWidget(tabs);
```

`sections` 為含本地化名稱與圖示的 `List<UiSectionBar.Section>`。預設標籤寬度為 104 GUI 單位；工廠多載可顯式傳入 `TabScrollSettings` 與標籤寬度。原有等寬 `UiSectionBar` 繼續可用，適合不會溢出的少量標籤。

標籤放不下且有空間容納箭頭時，標籤欄兩側顯示瀏覽按鈕。箭頭顯示相鄰標籤並移動焦點，當前頁保持不變；到達首尾時對應按鈕禁用。

宿主將 `mouseScrolled` 的單軸／雙軸參數轉給標籤欄。滑鼠按下立即回饋，放開提交選擇，拖動不會切頁；Tab 進入標籤組後，用左右鍵／Home／End 定位並切換，Enter／空格提交當前焦點。若宿主繼承原生 `Screen`，應將捕獲期間的 `mouseReleased` 顯式轉給 `tabs`，確保在欄外放開也能結束；在呼叫 `super.keyPressed` 前讓標籤欄處理拖動期間的 Esc，避免關閉整個畫面。關閉宿主時呼叫 `cancelDrag()`。主控面板包含這一接入實現。

`select(index)` 更新已確認選擇而不回調，`focus(index)` 移動鍵盤焦點並確保可見，`scrollTo(offset)` 與 `reveal(index)` 可接自訂輸入，`setBounds(area)` 更新佈局。繪製、滑鼠命中和提示均限制在視口內。

只需滾動計算時，使用 `ShBetterUiApi.interactions().tabScrollState()` 或直接建立 `TabScrollState`。透過 `resize(viewportWidth, contentWidth)` 設定範圍，再使用 `scrollBy`、`scrollTo`、`reveal`、`beginDrag`／`drag`／`endDrag` 和 `cancelDrag`；該狀態不依賴 Minecraft，拖動讀取複用 `PointerDrag`。

### 中性淺色玻璃

參考 `src_collection/collection/liquid-glass-react-master` 的背景模糊、邊緣折射與受光亮邊思路，沿用本庫既有 shader 實作。淺色版改為柔和淺灰，`GlassStyle.CLEAR` 的提亮由 0.035 降至 0.008，保留較弱的受光亮邊與折射；面板／獨立按鈕模糊半徑為 14／8 GUI 單位。淺灰與煙灰底使用 65 點高斯核，舊有飛行及切頁模糊保留原核。`GlassSurface.panel()` 與 `control()` 共用 `GlassColors.CLEAR`，面板內按鈕只薄疊色；獨立按鈕保留深色文字所需的底色。shader 不可用時仍有淺灰半透明底。

```java
GlassSurface.panel(graphics, x, y, width, height);
GlassSurface.drawString(graphics, font, title, x + 8, y + 8, GlassColors.CLEAR.TEXT);
```

LDLib 可用 `GlassPanelTexture.clearPanel()` 與 `clearButton()`；按鈕已位於毛玻璃面板內時用 `clearButtonOverlay()`，避免每個按鈕重複執行背景模糊。`GlassColors.select(clear)` 提供相應文字、卡片與控制項顏色。原有深色入口與色板保持不變。

`GlassSurface.drawString()` 復用原版字形描邊，以低透明度白色柔光托住字形，主文字保持清晰；可傳入 `String` 或已排版的 `FormattedCharSequence`。煙灰主題的 `GlassTheme.drawCentered()` 已使用此入口。消費端自行調用原版 `graphics.drawString()` 的文字需改用上述接口才會有字形柔光；底板材質不會攔截其他模組的文字渲染。

主模組設定頁新增「終極終端與樣板生成工具使用淺色玻璃」選項，寫入本機 `config/gt_shanhai/ui-client.toml`，預設關閉，重新開啟相關畫面後生效；終極終端的設定與報告畫面共用此選項。

庫不包含交易、機器、配方、AE 或 GTCEu 邏輯。遊戲內外觀、第三方 shader 相容及多種 GUI scale 仍須實機驗收。

### Slate 暗黑模式

`client.gui.scaled.DarkModeTheme` 提供 Forge／NeoForge 共用的原生繪製接口。`background()` 畫不透明的 Slate 900 底色，`card()`、`button()`、`input()` 分別畫圓角卡片、按鈕與輸入框。以 `State.titleText()`、`secondaryText()`、`controlText()` 取得文字色，提示文字可用 `TEXT_MUTED`。主要操作與焦點用藍色，成功狀態可搭配 `SUCCESS` 與文字或圖示。

每個控件保留一個 `Interaction.card()` 或 `Interaction.control()`，每幀傳入實際懸停、按住、焦點與啟用狀態。以按鈕為例：

```java
private final DarkModeTheme.Interaction motion = DarkModeTheme.Interaction.control();

// 由原生控件提供狀態，message 為已翻譯的 Component。
var state = motion.sample(hovered, pressed, focused, enabled, reducedMotion, PageTransition.nowMs());
DarkModeTheme.button(graphics, x, y, width, height, state, true, () -> {
    int textX = x + (width - font.width(message)) / 2;
    int textY = y + (height - font.lineHeight) / 2;
    graphics.drawString(font, message, textX, textY, state.controlText(), false);
});
```

卡片懸停時抬升 2 個 GUI 單位，文字過渡 200 毫秒，卡片底板過渡 300 毫秒。按鈕在按下的第一幀即縮至 98%，放開後以 200 毫秒復位，取消時用 `motion.cancelPress(nowMs)` 即時清除回饋。焦點環為 2 單位藍邊加 2 單位深色間隔。控件周圍預留至少 4 個 GUI 單位，卡片上方預留 6 個，避免裁切焦點環。輸入框先畫主題底板，再由原生控件畫文字、游標與選取區。畫面負責原生焦點、鍵盤導航、旁白及命中區。傳入 `reducedMotion=true` 時，顏色與內凹回饋即時更新，省去抬升與縮放。

### Liquid Glass

Forge 1.20.1 與 NeoForge 1.21.1 的原生 `Screen` 可呼叫 `client.gui.scaled.LiquidGlassTheme`。先畫後方內容，再呼叫 `toolbar()`、`popover()`、`input()` 或 `button()`，最後畫前景文字與圖示。工具列和按鈕使用偏通透的圓角材質，輸入框與浮層增加底色以穩定文字對比。按鈕可沿用 `DarkModeTheme.Interaction.control()` 的狀態採樣。

```java
var preferences = UiPreferences.DEFAULT;
var search = new SearchField<>(font, x, y, width, 20, index, UiStyle.LIQUID_GLASS, preferences);
var button = new UiButton(x, y + 24, width, 20, label, onPress, preferences, true, UiStyle.LIQUID_GLASS);
button.setBackgroundBlurRadius(12f);
```

`UiButton.setBackgroundBlurRadius(float)` 設定液態玻璃按鍵的背景模糊半徑，單位為 GUI 單位，預設 `12`。`0` 關閉採樣並使用實色底板，大於 `32` 的值按 `32` 處理，負數及 NaN／Infinity 會拋出 `IllegalArgumentException`。`getBackgroundBlurRadius()` 可讀取當前值。自繪按鈕可使用 `LiquidGlassTheme.button(..., preferences, sampleBackground, blurRadius, primary, selected, emphasis, content)`，原有簽名使用預設半徑。

確認與取消按鈕使用庫內透明 PNG 圖示，透過 `setIcon()` 居中繪製。本地化按鈕名稱用於懸停提示和旁白。

```java
confirmButton.setIcon(GraphicIcon.SUCCESS);
cancelButton.setIcon(GraphicIcon.CLOSE);
```

按鈕寬高相等時呈圓形，寬高不等時呈膠囊形。輪廓、描邊與焦點環由解析距離 shader 計算像素覆蓋率，GUI 縮放及按下動畫會自動調整抗鋸齒寬度。先繪製背景，再繪製按鈕，最後繪製文字與圖示。背景採用密集高斯採樣與圓角覆蓋率過渡，按鈕底色保留可見的背景顏色。`setSampleBackground(false)` 會跳過獨立採樣，`UiPreferences` 與全域玻璃設定繼續決定模糊是否啟用。

舊建構子繼續使用各控件的預設外觀。位於已繪製玻璃工具列裡的控件可呼叫 `setSampleBackground(false)` 共用底層採樣。搜尋候選浮層仍單獨採樣，繼續透過 `renderSuggestions()` 在普通控件與現有後處理之後繪製。高對比、關閉背景模糊或後處理不可用時使用不透明底板。減少動效時保留靜態的懸停、按下和選中回饋。

### 按鈕與畫面切換提示

公開的 `UiButton` 提供懸停高亮、原生功能提示及 `setSelected` 選中標記。用 `setPreview(Component.translatable(...))` 指定功能說明，預設提示顯示完整按鈕名稱。`UiAttention` 讓頁籤指示條連續移動，並提供新內容的短距淡入與目標亮邊。減少動效時立即顯示目標，保留選中標記。庫內展開控件與 JEI 面板已接入。其他消費畫面的接入、座標換算與驗證範圍見 [切換回饋說明](docs/attention-feedback.md)。

### 直接拖曳、滑動與縮放

- 每個拖曳區持有一個 `PointerDrag`。用 `DarkModeTheme.dragHandle()` 畫把手，以同一矩形命中。若把手畫在會抬升的卡片內，命中也要計入 `state.cardOffset()`。在子控件未消耗事件時呼叫 `begin(button, mouseX, mouseY)`，用 `active()` 顯示按住狀態，再將 `offsetX()/offsetY()` 加到物件原座標，讓預覽跟隨指標。拖動期間可用 `DarkModeTheme.dropTarget()` 標記經畫面驗證的落點。`release()` 會納入放開位置，畫面在提交前應重新驗證。失焦、關閉或取消時呼叫 `cancel()`。
- 列表拖動時，將 `move()` 回傳的 `Delta.y()` 交給 `ScrollPageTurn.dragOffset(scrollOffset, delta.y(), maxScroll)`，並立即套用新偏移。放開時也套用 `release()` 回傳的最後一段位移。超出邊界的位移會被截去，反向時列表立即跟手；需要捲動換頁的區域仍可使用下面的滾輪入口。
- `ViewportTransform.zoomAt(anchorX, anchorY, factor, minScale, maxScale)` 接收滾輪或平台手勢提供的相對比例，維持錨點下的內容位置。將同一個結果交給 `GuiRenderUtil.applyViewport()` 繪製，並用 `contentX()/contentY()` 將指標換回內容座標做命中測試。畫面保留既有裁切與鍵盤操作，並負責接收裝置手勢。

### 使用者掌控與無障礙

`UiPreferences` 將高對比、減少動效與背景模糊設定交給共用繪製入口。由消費畫面讀取可用的設定並傳入 `DarkModeTheme.Interaction`、`ButtonFeedback.visual()`、`PageTransition.advance()` 或 `InterruptibleAnimation.sample()`。高對比使用清楚的邊界與不透明底板，減少動效時保留可辨識的狀態。文字排版沿用原生字體，畫面在 GUI 比例變更時重算版面。

- `UiHistory<S>` 可保存返回和本地撤銷需要的獨立快照。返回與撤銷各用一個實例。切入子頁前保存搜尋、篩選和捲動位置；提交可逆編輯前保存編輯狀態。由畫面套用 `restore()` 取出的快照。伺服器資料的撤銷則由業務回執確認。
- `UiConfirmation.open()` 在當前畫面上開啟原生模態確認。`Prompt` 由畫面傳入本地化標題、操作對象與後果，以及確認和取消按鈕名稱。確認回調只執行一次；取消與 Escape 回到父畫面，並用提供的焦點查詢找到重建後的控件。可提供僅繪圖的背景回調保留原畫面脈絡，傳 `null` 時使用純色底。長內容支援滾輪及 Page Up／Page Down。
- `UiDisclosure` 提供有原生鍵盤操作與旁白的展開按鈕。畫面重排時可重建按鈕，保留 `UiDisclosure` 與草稿，回調只切換可見性與版面。
- `ButtonFeedback.status()` 提供等待、成功、部分完成、失敗、取消及逾時等狀態。可透過 `Component.translatable(status.translationKey())` 顯示本地化文字，或在狀態變更時通知旁白。收到取消回執後再呼叫 `resolveCancelled()`。

### 對話框、氣泡提示、動作面板與下拉刷新

`UiOverlayHost` 提供原畫面的提示框、確認框、底部動作面板與控件錨點氣泡。`UiPullRefresh` 提供列表頂部下拉、鍵盤重新整理、請求去重及回執狀態。Forge 與 NeoForge 原生 `Screen` 共用這些介面，另有可選的 Forge LDLib 1.x Widget 和 NeoForge LDLib2 `UIElement` 適配。示例見[畫面互動 API](docs/SCREEN_INTERACTIONS.md)。

### 搜尋、預測與自動填充

`client.gui.search.SearchIndex<T>` 和 `SearchField<T>` 由 Forge 1.20.1／NeoForge 1.21.1 共用。索引以已本地化的純文字名稱、任意數量的 ID 和別名建立快照。多詞查詢要求每個詞都命中，詞可落在不同欄位。比對忽略大小寫與多餘空白。

索引會為名稱和別名中的連續漢字生成拼音。輸入 `tieding`、`tie ding` 或 `td` 都可以找到「鐵錠」和「鐵錠」，也可和漢字或 ID 一起搜尋。字面名稱和 ID 優先出現在聯想中，選中拼音候選時填入原名稱。多音字採用預設讀音，呼叫方可以用別名補充其他讀音。

輸入 `@` 聯想模組，輸入 `#` 聯想 tag，例如 `铁锭 @mine #forge:ingots/`。模組 ID 從條目 ID 的名稱空間取得；需要按模組名稱或 tag 搜尋時，使用 `Entry(value, name, ids, aliases, modName, tags)`，其中名稱來自呼叫方本地化，tags 為完整的 `namespace:path` 字串。舊四參數構造繼續可用。`@` 支援模組 ID、名稱、漢字全拼和首字母；含空格的模組名稱可寫成 `@"Applied Energistics 2"`。`#` 比對完整 tag 的片段。篩選詞與普通詞共同限制結果；沒有字首的普通搜尋保持原有含義。

模組和 tag 候選按精確、前綴、包含、拼音匹配排序並去重，只來自當前範圍內且符合其餘查詢詞的條目。填充保留此前輸入與空白，僅替換最後一個詞；`Suggestion.label()` 提供候選行文字，`completion()` 提供填入的完整查詢。JEI 面板和自建素材來源共用這一行為，素材或標籤版本更新後隨索引重建重新整理。

名稱中作為普通文字使用的 `@`、`#` 可放在雙引號內，例如 `"Block #1"`；自動填充會按需引用並轉義名稱，保證填充後仍能匹配原條目。

- `search(query, scope)` 按來源順序返回命中項目，空查詢返回範圍內全部項目。`suggest(query, scope, limit)` 依名稱、ID 的精確／前綴／包含命中排序，最後列出別名或跨欄位命中。自繪輸入框可用 `Suggestion.suffixFor(query)` 取得可直接追加的灰字後綴。
- `SearchField` 沿用原生 `EditBox`。空間足夠時在右側顯示 16×16 放大鏡，較窄或較矮的輸入框把空間留給文字。輸入變更會更新聯想，游標在末端且沒有選取文字時才顯示灰字。上下鍵選擇，`Tab` 或點擊填充，`Esc` 收起聯想並保留輸入。`setOnSubmit` 接收 `Enter` 提交的當前查詢，`Ctrl+Z`（Mac 為 `⌘Z`）或 `undoCompletion()` 恢復填充前的查詢。
- `setResponder` 在查詢實際變更時通知一次。分類切換時以 `setScope` 重新提供範圍，資料或語言變更時以 `setIndex` 替換索引；兩者都保留輸入文字。

原生 `Screen` 接入示例，`refreshResults` 和 `submitSearch` 為介面自己的方法。

```java
// import com.dishanhai.sh_better_ui.client.gui.search.SearchField;
// import com.dishanhai.sh_better_ui.client.gui.search.SearchIndex;
private SearchField<String> search;

// 在 init() 建立；名稱來自遊戲本地化。
var entries = List.of(new SearchIndex.Entry<>("iron",
        Component.translatable("item.minecraft.iron_ingot").getString(),
        List.of("minecraft:iron_ingot"), List.of("tieding", "td")));
var index = new SearchIndex<>(entries);
search = addRenderableWidget(new SearchField<>(font, 16, 16, 168, 20, index));
search.setResponder(query -> refreshResults(index.search(query)));
search.setOnSubmit(this::submitSearch);

// 在 render() 的普通控件繪製完成後。
search.renderSuggestions(graphics, mouseX, mouseY, height);

// 在 mouseClicked() 的普通分派前。
if (search.mouseClickedSuggestion(mouseX, mouseY, button, height)) return true;

// 在 mouseScrolled() 的普通分派前；NeoForge 請傳入垂直滾動量。
if (search.mouseScrolledSuggestions(mouseX, mouseY, verticalDelta, height)) return true;

// 在 keyPressed() 的 super 呼叫前。Screen 預設會先處理 Esc 關閉。
if (keyCode == GLFW.GLFW_KEY_ESCAPE && search.dismissSuggestions()) return true;
```

搜尋框與聯想清單使用同一套 GUI 座標。重建介面時可先儲存 `getValue()`，再用 `setValue()` 還原，並以同一個範圍條件更新結果和聯想。

### JEI 接入與配方預覽

JEI 為可選前置。Forge 1.20.1 以 JEI `15.49.0.200` 編譯，NeoForge 1.21.1 以 JEI `19.69.0.456` 編譯。兩版各自發布，消費模組可以只依賴 SH-Better-UI 的公開接口。未安裝 JEI 時，共用搜尋仍可使用。

- `client.jei.JeiIntegration` 提供素材快照、圖示與提示、配方查詢及開啟 JEI 的接口。使用 `isAvailable()` 判斷 JEI 是否已載入當前世界的資料，`revision()` 變更時重新取得 `ingredients()`。使用 `Ingredient.key()` 區分同一註冊 ID 的變體。`recipes(ingredient, direction, limit)` 查詢可見配方，`open(ingredient, direction)` 開啟 JEI 的完整取得方式或用途。
- `client.gui.search.JeiSearchPanel` 提供可嵌入原生 `Screen` 的搜尋與唯讀預覽。用名稱、ID、別名及模組篩選找物品／流體，選中結果後查看原生提示或配方。在配方區切換「取得方式」和「用途」，也可開啟完整 JEI 頁面。若呼叫端設定 `setOnChoose`，玩家可用「選用」把素材交回呼叫端。

嵌入方式如下。面板需要 `144 × 132` GUI 像素，建議預留 `320 × 200` 以上讓結果和配方並排。`panel` 由畫面持有，畫面調整尺寸時可保存 `snapshot()`，重建後用 `restore(snapshot)` 還原搜尋和選取。

```java
// 在 Screen.init()，panelLeft、panelTop、panelWidth、panelHeight 由畫面排版決定。
panel = new JeiSearchPanel(font, panelLeft, panelTop, panelWidth, panelHeight);
for (var widget : panel.widgets()) addRenderableWidget(widget);
JeiIntegration.setExtraAreas(this, panel::extraAreas);

// 在 Screen.render()，先畫面板，再由 Screen 繪製原生控件，最後畫覆層。
panel.renderBackground(graphics);
super.render(graphics, mouseX, mouseY, partialTick);
panel.renderOverlays(graphics, mouseX, mouseY, height);

// 在 Screen.tick()。
panel.tick();

// 在 Screen.mouseClicked() 和 mouseScrolled() 的普通分派前。
if (panel.mouseClicked(mouseX, mouseY, button, height)) return true;
if (panel.mouseScrolled(mouseX, mouseY, verticalDelta, height)) return true;

// 在 Screen.keyPressed() 的 super 呼叫前。
if (keyCode == GLFW.GLFW_KEY_ESCAPE && panel.dismissSuggestions()) return true;
```

`setExtraAreas` 讓 JEI 側欄避開當前畫面的面板和聯想清單。畫面提供者應使用 `panel::extraAreas`，面板永久移除時可傳入 `null` 撤銷。NeoForge 的 `mouseScrolled` 需傳入垂直滾動量。

### 物品與流體篩選、選取

`client.gui.filter.IngredientFilter` 是 JEI 與自建素材庫共用的不可變 `Predicate<Ingredient>`。`withKinds`、`withIds`、`withMods`、`withTags` 採完整值比對，同類任一符合，跨類全部符合，空集合解除該類限制。自訂條件使用 `and`、`or`、`negate`。名稱、ID 片段與別名仍沿用 `SearchIndex`，`JeiSearchPanel.setFilter` 同步限制搜尋結果和聯想。

```java
var allowed = IngredientFilter.items()
        .withMods(Set.of("minecraft", "gtceu"))
        .withTags(Set.of(ResourceLocation.tryParse("forge:ingots/iron")));

var request = new IngredientPicker.Request(pickerTitle, IngredientCatalog.automatic(),
        allowed, 8, initialSelection, uiPreferences);
IngredientPicker.open(this, request, () -> rebuiltPickerButton, selected -> {
    // 回到父畫面後提交草稿；空清單可用來清除篩選。
    applySelection(selected);
});
```

`IngredientPicker`、`IngredientCatalog`、`IngredientValues` 和 `IngredientSelection` 位於 `client.gui.selection`。`Request.single` 和 `Request.multiple` 提供預設值，完整建構子可設定業務條件、初始候選和 `UiPreferences`。選取器可依種類、名稱／ID、模組及標籤縮小結果。玩家可切換已選檢視、撤銷本地草稿修改，並透過確認回傳不可變清單。取消及 Escape 放棄草稿。從 JEI 配方返回或調整 GUI 比例後保留草稿與搜尋條件。

| 來源 | 素材範圍 |
| --- | --- |
| `IngredientCatalog.jei()` | 目前 JEI 可見的物品與流體 |
| `IngredientCatalog.registry()` | 原生物品預設形態及源流體 |
| `IngredientCatalog.automatic()` | 優先使用 JEI，未就緒時使用原生註冊表 |
| `IngredientCatalog.of(candidates)` | 呼叫端提供的固定候選快照 |

`IngredientValues.item(stack)` 和 `fluid(stack)` 可用原生 stack 建立自訂候選。Forge 和 NeoForge 各自接收其平台的 `FluidStack`。`Ingredient.copy(ItemStack.class)` 或 `copy(FluidStack.class)` 回傳保留 NBT／資料元件及數量的副本。`Ingredient.key()` 用於目前執行期的形態辨識，持久化請儲存原生 stack 資料。初始選取應使用當前來源的候選 key。動態來源可自行實作 `IngredientCatalog`，在素材、語言或標籤變更後推進 `revision()`，並維持來源內同一形態的 key 穩定。JEI 候選的標籤遵循其素材 helper，原生候選讀取物品或流體註冊表標籤。

嵌入式畫面可在 `JeiSearchPanel` 建構時傳入素材來源，或以 `panel.setCatalog(source)` 切換，再用 `panel.setFilter(allowed)` 套用條件。`panel.setChosen(...)` 讓宿主標記草稿，`refreshSelection()` 更新選用／移除按鈕。`IngredientSelection` 可供宿主維護草稿、限制選取數與重新驗證候選。既有 `setOnChoose` 仍在明確選用時回傳單項。

### 捲動到邊界時換頁

每個分頁捲動區各自建立一個 `client.gui.scaled.ScrollPageTurn`。指標命中捲動區、子控件未消耗事件時，用本次滾輪事件算出並限制目標偏移，再交給庫判斷。向下捲到頁底時切下一頁，向上捲到頁頂時切上一頁。內容不足一屏時可直接用滾輪換頁。首尾頁停在邊界，同方向的連續輸入需間隔至少 180 毫秒才能再次換頁。

```java
private final ScrollPageTurn pageTurn = new ScrollPageTurn();

// 在捲動區的滾輪回調中。頁碼從 0 開始，偏移向下遞增。
double target = Math.clamp(scrollTarget - wheelDelta * pixelsPerStep, 0.0, maxScroll);
int targetPage = pageTurn.targetPage(wheelDelta, target, maxScroll,
        currentPage, pageCount, PageTransition.nowMs());
if (targetPage != currentPage) {
    // 使用原換頁入口。下一頁從頂端開始，上一頁從底端開始。
    openPage(targetPage, targetPage < currentPage);
    return true;
}
scrollTarget = target;
```

由畫面在換頁後計算新頁面的滾動範圍、定位並更新現有轉場。更換列表來源或重新開啟捲動區時呼叫 `pageTurn.reset()`。

### 點陣動畫背景

`client.gui.scaled.UiDotMatrix` 可用於整頁或局部面板。每塊背景持有一個實例，在宿主底板之後、前景控件之前呼叫 `render()`。`DotMatrixProfile.VIOLET` 提供黑底紫色方點和柔光，`setProfile()` 可在執行時換配色或調整動畫。

從主控面板進入「開發配置 → 開發者面板 → 材質圖庫」，點當前樣例標題，在目錄選擇「點陣動畫」。此樣例演示彩虹漸變與隨機淡出。基礎設定可即時調節色相、效果、浮動機率和數量上限。標題右側的「動效設定」可調節淡出機率、色板、流向和漸變週期。點擊預覽區或聚焦後按 Enter 可觸發波紋。點「查看 Java 程式碼」開啟抽屜，裡面可以查看完整程式碼和複製示例。

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

// 在畫面的背景繪製階段呼叫
Bounds area = new Bounds(0, 0, width, height);
dots.render(graphics, area, mouseX, mouseY);

// 宿主處理點擊或鍵盤啟用時，可從事件位置或已啟用控件的中心觸發
// dots.pulse(area, x, y);
```

原生 `Screen` 在 Forge 1.20.1 可於 `render()` 先畫底圖和點陣，再由 `super.render()` 繪製控件。NeoForge 1.21.1 的 `super.render()` 會呼叫四參數的 `renderBackground()`，將底圖和點陣畫在該方法中即可。

- `Palette` 使用 ARGB，依次設定底色、點色、高亮色和光暈強度。透明底色可露出已繪製的內容。
- `Grid` 設定間距、方點大小、密度和隨機種子。區域過大時自動放寬間距，使網格採樣不超過 16,384 格。方點及光暈裁切在 `Bounds` 內。
- `Motion` 設定效果、週期毫秒數、每輪每點浮動機率、同時浮動上限和最大上浮高度。機率、上限或上浮高度設為零即可停用浮動。可選常亮 `STEADY`、閃爍 `TWINKLE`、行波 `WAVE`，也可實現 `Effect.brightness()` 自行計算亮度。
- `Fade` 單獨設定隨機淡出再浮現的機率（0–1）和週期毫秒數。`Fade.NONE` 關閉淡出。
- `Gradient` 可使用 `Gradient.RAINBOW`，也可傳入自訂 ARGB 色標。首個位置為 `0`，後續位置嚴格遞增且小於 `1`。各段按色標位置插值，末色接回首色。漸變提供方點的 RGB，色標 Alpha 乘以原有透明度，仍可疊加亮度、浮動和互動。`Gradient.NONE` 使用原配色，`VIOLET` 預設關閉漸變與隨機淡出。
- 漸變方向可使用 `Flow.RIGHT`、`DOWN`、`DIAGONAL` 和 `OUTWARD`，也可實現 `Flow.position(x, y, phase)`。座標和週期進度歸一化，返回的位置按 1 迴圈。示例用橫座標的平方改變橫向色帶寬度，並疊加縱向偏移。
- `Interaction` 設定懸停和波紋半徑，各自設為零即可關閉。背景使用宿主給定的指標與點擊座標，沿用宿主的命中、焦點和事件回傳。顯式時鐘的 `render()` 和 `pulse()` 須使用同一個單調毫秒時鐘。
- 減少動效時方點靜止、隨機淡出暫停，漸變凍結在起始相位。懸停高亮和固定範圍的點擊淡出仍可使用。高對比時使用純色底板。模態覆層開啟時，宿主可傳入 `Double.NaN` 暫停懸停，並用 `clearPulse()` 清除波紋。

### 可打斷動畫

`client.gui.scaled.InterruptibleAnimation` 復用 `Spring`，提供保留位置與速度的開關、反向及換目標，並支援延遲換目標、停止、立即完成與減少動效模式。Forge／NeoForge 共用同一接口；每個控件獨立持有實例，事件與繪製使用相同的單調毫秒時鐘。本次亦修正 Spring 延遲換目標時凍結舊軌跡的問題。完整接入及生命週期示例見[可打斷動畫](docs/interruptible-animation.md)。

```java
// 控件欄位；相關類型均位於 client.gui.scaled。
private final InterruptibleAnimation motion = new InterruptibleAnimation(Spring.PANEL);
// 開關事件：motion.setOpen(open, PageTransition.nowMs());
// 每幀繪製：float progress = motion.sample(PageTransition.nowMs(), reducedMotion);
// 關閉途中再次 setOpen(true, ...) 會從當時的位置與速度繼續。
```

### 配方切換、模式指示與即時狀態

- `UiRecipePageTransition.start(direction, viewport, nowMs, preferences)` 使用配方修改器的 640 ms 雙頁滑動。每幀取 `sample()`，將只負責繪製的舊頁和新頁回調交給 `draw()`。側欄可讀取 `Frame.companion()`，在遮罩最深時換內容。只顯示當前頁的場景用 `enter()` 和單回調 `draw()`，滑鼠座標減去同一幀的 `incomingX()`、`incomingY()`。
- `UiButton.setModeIndicators(count, selectedIndex)` 用零基索引標記當前模式。開關可用 `UiToggleButton.setModeIndicatorsVisible(true)`，顯示關和開兩檔並隨 `setValue()` 同步。
- `UiDynamicStatus` 接收 `Supplier<Snapshot>`。`Snapshot` 可組合 `ButtonFeedback.Status`、自訂 `Component` 和 0–100 的整數進度。每次 `render()` 只讀取一次來源，返回的快照也可用於旁白。狀態圖示可透過 `UiDynamicStatus.icon(status)` 單獨使用。

這些 API 可在遊戲內的開發者圖庫預覽並複製 Java 示例。

### 載入提示

- 首頁卡片等待資料時用 `UiSkeleton.card()` 佔位。自訂佈局可用 `UiSkeleton.draw()` 傳入與內容位置對應的佔位塊。
- 列表正在重新整理或請求下一批資料時，用 `UiLoading.edgeSpinner()` 在頂部或底部顯示小圓環。`UiPullRefresh` 在等待回執期間已接入頂部圓環。
- 上傳、下載與可測量的載入用 `UiLoading.progressBar()` 或 `topBar()` 直接繪製完成量除以總量。總量未知時，頂部載入條可使用 `nowMs` 多載。
- 整理任務可把真實完成比例傳給 `UiLoading.spinner()` 的 `float` 多載，弧長表達進度。傳入 `nowMs` 則繪製未知進度的圓環。減少動效時未知進度提示停留在靜態位置，真實進度仍隨資料更新。

### 可選按鈕與玻璃動效

`client.gui.scaled` 提供三項可組合的裝飾接口，消費端調用後才顯示：

- `ClickRipple` + `GlassEffectRenderer.drawRipple`：每個按鈕獨立持有點擊漣漪，420ms 擴散淡出；連點會重啟，原點隨控件重排。
- `GlassEffectRenderer.drawFlowBorder`：沿矩形四邊循環的細流光，2 秒一輪。
- `GlassEffectRenderer.drawBreathingGlow`：控件外側 3 GUI 像素柔光，2.4 秒一輪，強度可直接使用已有 hover 彈簧。

點擊回調確認操作被接受後，調用 `ripple.trigger(mouseX, mouseY, x, y, w, h, nowMs)`；每幀先畫底板，再畫裝飾，最後畫文字與圖示：

```java
GlassEffectRenderer.drawBreathingGlow(g, x, y, w, h, hover, nowMs, reducedMotion, accent);
GlassEffectRenderer.drawFlowBorder(g, x, y, w, h, nowMs, reducedMotion, accent);
GlassEffectRenderer.drawRipple(g, x, y, w, h, ripple, nowMs, reducedMotion, accent);
```

`ripple` 為控件自己的 `new ClickRipple()`，`accent` 使用當前主題色；點擊與繪製使用相同毫秒時鐘。關閉／移除時調用 `ripple.clear()`。裝飾與底板放在同一個 `withButtonMotion` 回調中，沿用原控件的命中區、點擊行為及裁切。

`reducedMotion=true` 時，漣漪改成均勻淡出，流光與呼吸改成靜態亮度。這些接口只提交原生 GUI 矩形，不新增後處理、材質或依賴；局部漣漪與 `GaussBlur.requestWave` 的屏幕折射波可分別使用。完整組合與 LDLib 接入示例見[可選玻璃效果](docs/effects.md)。

### 文字與佈局對齊

`client.gui.display` 提供 `UiText` 和 `UiLayout`，Forge 與 NeoForge 使用相同介面。座標和間距均為 GUI 邏輯單位。

```java
var area = new GraphicDisplayLayout.Bounds(12, 20, 160, 24);
UiText.drawCentered(graphics, font, Component.translatable("your_mod.title"), area, 0xFFFFFFFF, false);

var buttons = UiLayout.row(area, List.of(new UiLayout.Size(28, 18), new UiLayout.Size(44, 20)),
        4, UiLayout.Horizontal.RIGHT, UiLayout.Vertical.CENTER);
```

`drawLeft`、`drawCentered`、`drawRight` 將單行文字垂直居中，並按原生字型寬度水平對齊。可使用 `draw` 傳入動態的水平對齊值。

`UiLayout.align` 定位單個元件。`row` 水平對齊整組、垂直對齊各項，`column` 垂直對齊整組、水平對齊各項。兩者都返回與輸入順序一致的不可變 `Bounds` 列表。空間不足時保留原尺寸。若介面啟用裁切，繪製與命中均使用容器與對應 `Bounds` 的交集。

### 圖形化顯示

Forge 與 NeoForge 的原生 `Screen` 可使用 `client.gui.display` 下的圖示與狀態面板。狀態和翻頁圖示有 `INFO`、`SUCCESS`、`WARNING`、`ERROR`、`PREVIOUS`、`NEXT`。通用操作圖示有 `ADD`、`REMOVE`、`SEARCH`、`SETTINGS`、`REFRESH`、`CLOSE`。可用 `new GraphicIcon(textureId)` 引用自己的 16×16 透明 PNG，白色圖形可按 ARGB 著色。給 `UiButton` 設定圖示時仍應傳入本地化按鈕名稱，供懸停提示和旁白使用。

內建圖示的 64×64 透明副本位於 `sh_better_ui:textures/gui/display_64/<圖示名稱>.png`，與 16×16 原圖同名。副本按最近鄰放大 4 倍，適用於按 64×64 讀取完整紋理的呼叫方。內建 `GraphicIcon` 常數引用 16×16 原圖。

AE2 介面可用 `AE2_ORDER`（下單）、`AE2_PATTERN_UPLOAD`（樣板上傳）、`AE2_ORDERABLE`（可下單）和 `AE2_STOCK`（AE 庫存）。對應紋理名為 `ae2_order.png`、`ae2_pattern_upload.png`、`ae2_orderable.png`、`ae2_stock.png`。

商城介面可用 `PRODUCT_CARD`（商品卡片）、`PRODUCT`（商品）、`PRODUCT_OUTPUT`（商品產出）、`PRODUCT_INPUT`（商品輸入）、`MY_PROFILE`（我的）、`MY_FAVORITES`（我的收藏）、`HIDE`（隱藏）、`SELECT_ALL`（全部）和 `SELECT_PARTIAL`（部分）。工具與功能入口可用 `RENDER`（渲染）、`TOOL`（工具）、`TOOLBAR`（工具欄）、`INSTALL`（安裝）、`COMPATIBILITY`（相容）和 `QUANTUM`（量子）。

檔案類圖示有 `FOLDER`（資料夾）、`FILE`（通用檔案）、`DOCUMENT`（文件）、`FILE_EXE`（.exe）、`FILE_MD`（.md）、`FILE_JAVA`（.java）和 `FILE_KT`（.kt）。模組與配方介面可用 `KUBEJS`（按 [KubeJS 專案圖示](https://modrinth.com/mod/kubejs)重繪）、`AE2_PATTERN`（AE2 樣板）和 `CRAFTING`（合成）。商店入口可用 `SHOP`。

分類和統計可用 `CATEGORY`、`STATISTICS`，計劃和目標可用 `PLAN`、`GOAL`。數學標記可用 `EQUATION`（方程）和 `FUNCTION`（函數）。撤銷操作可用 `UNDO`，權限入口和拒絕訪問狀態分別用 `PERMISSIONS`、`ACCESS_DENIED`。

內容卡片、通用頻道、會員和地點可用 `CARD`、`CHANNEL`、`MEMBERSHIP`、`LOCATION`。瀏覽器入口使用帶網址列的 `BROWSER`，網頁搜尋使用地球加放大鏡的 `WEB_SEARCH`。

輸入工具欄可用 `EMOJI`（表情）、`SYMBOL`（符號）、`TRANSLATE`（翻譯）和 `CLIPBOARD`（剪貼簿）。文字輸入框和數字輸入分別使用 `TEXT_INPUT`、`NUMBER_INPUT`。

資料流向可用 `INPUT`（輸入）和 `OUTPUT`（輸出）。配置和模組入口可用 `CONFIG`、`MOD`。`GUIDEME` 按 [GuideME 模組圖示](https://modrinth.com/mod/guideme)重繪，通用指南可用 `GUIDE`。介面相關入口可用 `GUI`（帶指標的操作視窗）和 `UI`（帶側欄的佈局面板）。

HUD 行首可使用按 8 像素網格設計的 `COMPACT_SIGNAL`、`COMPACT_CLOCK`、`COMPACT_CALENDAR`、`COMPACT_WORLD`、`COMPACT_BIOME`、`COMPACT_LOCATION`、`COMPACT_CHUNK` 和 `COMPACT_TORCH`。用 0.5 倍 PoseStack 繪製時，8 個 GUI 像素即可保留完整輪廓。

其他操作可用 `FILTER`、`SORT`、`EDIT`、`COPY`、`DELETE`、`FAVORITE`、`LOCK`、`UNLOCK`、`EXPAND`、`COLLAPSE`、`UPLOAD` 和 `DOWNLOAD`。例如用 `button.setLeadingIcon(GraphicIcon.FILTER)` 在篩選按鈕的文字旁顯示漏斗。

導航用 `BACK`（返回）、`FORWARD`（前進），列表翻頁用 `PREVIOUS` 和 `NEXT`。工作流按鈕可用 `SWITCH`（切換）、`MERGE`（合併）、`NEW`（新建）、`SAVE`（儲存）、`EXPORT`（匯出）、`COMMENT`（評論）和 `DELETE`（刪除）。`COMMENT` 與 `BUBBLE` 共用氣泡圖案。

提示和取消可用 `WARNING`（警告）、`CRITICAL_WARNING`（嚴重警告）、`CANCEL`（普通取消，與 `CLOSE` 共用叉號）和 `CRITICAL_CANCEL`（嚴重取消）。嚴重狀態使用八邊形輪廓，在單色主題中也能區分。維護與顯示操作可用 `REPAIR`（修復）、`RESET`（重置）、`DISABLE`（禁用）、`ENABLE`（啟用）、`HIDE`（隱藏）、`SHOW`（顯示）、`ORGANIZE`（整理）和 `SORT`（排序）。

工具和分類圖示包括 `CACHE`（快取）、`LOG`（日誌）、`MONITOR`（監控）、`TODO`（待辦）、`DOCUMENT`（文件）、`CODE`（程式碼）、`TEST`（測試）、`DEVELOPMENT`（開發）、`COLLECTION`（集合）和 `SEPARATE`（分離）。`SEPARATE` 的箭頭從一條路徑分成兩條，與 `MERGE` 配對使用。

記錄、帳戶和裝置圖示包括 `HISTORY`（歷史記錄）、`USER`（單個使用者）、`SYSTEM`（系統）、`PHONE`（手機）和 `COMPUTER`（電腦）。成員列表仍可用 `MEMBERS`，監控面板可用 `MONITOR`。

作業系統圖示可用 `WINDOWS`、`MACOS`、`IOS`、`ANDROID` 和 `LINUX`，均提供可著色的 16×16 原圖和 64×64 副本。

任務控制按鈕可用 `RUN`（執行）、`PAUSE`（暫停）和 `RESUME`（繼續）。繼續圖示在播放三角左側保留一條暫停線，便於區分開始執行與恢復執行。

遊戲和裝置圖示包括 `MINECRAFT`（苦力怕臉）、`GAMEPAD`（遊戲手把／遊玩）、`MOUSE`（滑鼠）、`KEYBOARD`（鍵盤）、`HEADPHONES`（耳機）和 `MICROPHONE`（麥克風／語音）。載入器可用 `FORGE`（鐵砧）和 `NEOFORGE`（狐狸頭）。遊戲和載入器標識是適合主題著色的單色像素圖案。

模組圖示包括 `AE2`（ME 控制器通電正面）、`MEKANISM`（齒輪與閃電）、`FTB_QUESTS`（束帶任務書）、`KUBEJS`（拼圖方塊）和 `GREGTECH`（GTCEu 徽記）。圖示由本庫隨包提供，可直接用於 `UiButton`。

內容分類用 `BLOCK`（立體方塊）和 `ITEM`（錠形物品）。詳情入口用 `ATTRIBUTES`（屬性）、`DESCRIPTION`（描述）、`TOOLTIP`（懸停提示）和 `INTERACTION`（互動）。

歸檔和安全操作用 `COMPRESS`（壓縮）、`DECOMPRESS`（解壓）、`ENCRYPT`（加密）和 `DECRYPT`（解密）。外掛與資料入口用 `PLUGIN`（外掛）、`GITHUB`（GitHub）、`MCMOD`（MC 百科）和 `WIKI`（通用 Wiki）。

管理入口可用 `SETTINGS`（設定）、`CONFIG`（配置）、`MANAGEMENT`（管理）、`PERMISSIONS`（權限）、`LIST`（列表）、`DETAILS`（詳情）、`EDIT`（編輯）、`MOD`（模組）和 `TEMPLATE`（模板）。基礎設施用 `DATA`（資料）、`NETWORK`（網路）、`SERVER`（伺服器）和 `DATABASE`（資料庫）。運維用 `BACKUP`（備份）、`MIGRATE`（遷移）、`UPGRADE`（升級）、`SECURITY`（安全）、`AUDIT`（審計）和 `REPORT`（報告）。

執行與排程用 `QUEUE`（佇列）、`POLLING`（輪詢）、`ASYNC`（非同步）、`CONCURRENCY`（併發）、`THREAD`（執行緒）、`PROCESS`（程序）、`PARALLEL`（並行）和 `SCHEDULER`（排程）。任務與獎勵用 `TASK` 和 `REWARD`。

GitHub 倉庫入口可用 `GITHUB_REPO`（倉庫）、`GITHUB_PULL_REQUEST`（Pull requests）、`GITHUB_ISSUE`（Issues）、`GITHUB_DISCUSSION`（Discussions）、`GITHUB_ACTIONS`（Actions）、`GITHUB_README`（README）、`GITHUB_RELEASE`（Releases）和 `GITHUB_COMMITS`（Commits）。兩種尺寸取自 GitHub 的 Octicons，授權宣告隨 JAR 一起提供。[Octicons](https://github.com/primer/octicons)

專案許可用 `LICENSE`（通用許可）、`LICENSE_OPEN_SOURCE`（開源許可）和 `LICENSE_CLOSED_SOURCE`（閉源許可）。專案資訊入口可用 `AUTHORIZATION`（授權）、`CONTRIBUTORS`（貢獻者）、`AFDIAN`（愛發電品牌）、`SUPPORT`（發電／支援創作者）和 `ACKNOWLEDGMENTS`（鳴謝）。

AI 服務入口可用 `CHATGPT`、`CLAUDE`、`DEEPSEEK`、`GROK` 和 `GEMINI`。圖示提供 16×16 與 64×64 兩種透明尺寸，Gemini 使用白色像素繪製的內凹四角星。

聯絡和獲取支援的入口可用 `QQ`、`WECHAT`（微信）、`X`（原 Twitter）、`DISCORD`、`TELEGRAM`、`WHATSAPP` 和 `FACEBOOK`。郵件入口可用 `EMAIL`（通用信封）、`QQ_MAIL`、`ICLOUD_MAIL` 和 `GMAIL`。呼叫方可透過 `UiButton.setLeadingIcon` 將圖示與本地化按鈕文字配合使用。

影片平台入口可用 `BILIBILI`（嗶哩嗶哩）、`DOUYIN`（抖音）、`KUAISHOU`（快手）和 `YOUTUBE`。

通用入口可用 `MUSIC`（音樂）、`APP`（應用）、`QUESTION`（疑問）、`CALCULATE`（計算）、`CPU`、`GPU`（顯示卡）、`DISK`（磁碟）和 `MAP`（地圖）。AE2 的 ME 網路頻道使用 `AE_CHANNEL`，圖案取自智慧線纜的頻道指示線。

- 商店和獎勵入口使用 `SHOP`（商店）、`BANK`（銀行）、`MEMBERSHIP`（會員）、`POINTS`（積分）、`REDEEM`（兌換）、`COUPON`（優惠券）、`POWER`（電力）、`SPARK`（星火）和 `CURRENCY`（貨幣）。
- 交易和帳務操作使用 `TRADE`（交易）、`PAYMENT`（支付）、`BILL`（帳單）、`SETTLEMENT`（結算）、`WITHDRAW`（提現）、`TRANSFER`（轉帳）、`BALANCE`（餘額）、`BUY`（購買）和 `SELL`（出售）。
- 商業導航可用 `CURRENCY`（貨幣中心）、`REDEEM`（兌換中心）、`MEMBERSHIP`（銀行會員中心）和 `SHOPPING_CART`（購物車）。收藏和篩選使用 `FAVORITE` 和 `FILTER`。

- 內容欄位可用 `TITLE`（標題）、`TAG`（標籤）、`COVER`（封面）、`CATEGORY`（分類）、`CHINESE`（中文）、`SUMMARY`（簡述）、`BODY_TEXT`（正文）和 `TEXTURE`（材質）。
- 模組入口可用 `SOPHISTICATED_BACKPACKS`（精妙揹包）和 `SHBUI`（煥界 UI 庫）。`SHBUI` 的白色輪廓取自模組彩色封面 `cover.png`。
- 主題入口可用 `DARK_MODE`（深色模式）、`LIGHT_MODE`（淺色模式）和 `CUSTOM_THEME`（自訂主題）。

```java
// 面板內框使用 Screen 的邏輯座標。entries 是當前頁的 List<GraphicDisplay.Entry>。
var area = new GraphicDisplayLayout.Bounds(x, y, width, height);
var layout = GraphicDisplayLayout.arrange(area, entries.size(), 28, pageCount > 1);

// 在 init 中註冊控件。changePage 更新當前頁、佈局及兩個按鈕的 active。
var previous = addRenderableWidget(GraphicDisplay.pageButton(
        layout.previous(), false, preferences, button -> changePage(-1)));
var next = addRenderableWidget(GraphicDisplay.pageButton(
        layout.next(), true, preferences, button -> changePage(1)));
previous.active = previous.visible && pageNumber > 1;
next.active = next.visible && pageNumber < pageCount;

// 在 render 中使用當前頁的 layout，頁碼從 1 開始。
GraphicDisplay.drawRows(graphics, font, layout, entries, 1, textColor, rowColor);
GraphicDisplay.drawPageNumber(graphics, font, layout, pageNumber, pageCount, textColor);
super.render(graphics, mouseX, mouseY, partialTick);
GraphicDisplay.drawRowTooltip(graphics, font, layout, entries, mouseX, mouseY);
```

預設在寬度達 240 GUI 單位時排兩列，窄版排一列。需要自訂列數時，將 `Grid(最小欄寬, 最多欄數)` 傳給 `arrange`。例如 `new Grid(68, 4)` 會隨可用寬度在一至四列之間重排。

```java
var grid = new GraphicDisplayLayout.Grid(68, 4);
int capacity = GraphicDisplayLayout.pageCapacity(area, 28, true, grid);
var layout = GraphicDisplayLayout.arrange(area, entries.size(), 28, true, grid);
```

`pageCapacity` 用相同的內框、行高、分頁開關及網格規則計算可完整顯示的條目數，可用於切分當前頁。容量為 0 時需給內容留出足夠空間。切頁或縮放後按當前內框和條目數重算佈局。使用 2× 圖示時應預留至少 40 GUI 單位行高。狀態文字傳入本地化 `Component`，超出行高可懸停查看完整標籤。如果宿主還套了一層視口裁切，只在滑鼠位於宿主可見區時顯示提示。底板和頁碼狀態由宿主管理，翻頁控件沿用 `UiButton` 的點擊、鍵盤焦點和旁白。

### 網路風格圖形化 UI API

`client.gui.scaled.NetworkTheme` 提供深色半透明圓角面板、藍色描邊的標題／設定行、原生編輯框和描邊頁籤。Forge 與 NeoForge 的原生 `Screen` 使用同一套介面。所有邊界均為 GUI 邏輯座標，縮放後在 `init()` 重算。

- `NetworkTheme.drawPanel()` 繪製面板，`drawField()` 顯示標題或只讀設定行。`labelColor()` 可給標籤部分著色，數值部分保留自己的顏色。
- `NetworkTheme.tab()` 返回 `UiButton`。以 `setSelected()` 標記當前頁，標籤傳入本地化 `Component`，供提示與旁白讀取。圖示按鈕預留至少 20×20 GUI 單位。內建圖示為 16×16 px 透明 PNG，依次為 `HOME`、`LIST`、`WIRELESS`、`TOPOLOGY`、`STATISTICS`、`MEMBERS`、`SETTINGS` 和 `ADD`，也可以傳入自己的 `GraphicIcon`。
- `NetworkTheme.editBox()` 返回原生 `EditBox`。先用 `setValue()` 寫入初值，再註冊 `setResponder()`。用 `setFilter()` 限制輸入，用接入方自己的確認操作提交。
- `NetworkToggle` 由 `setValue()` 更新顯示。滑鼠、Enter 或空格會請求取反。接入方等待回包時可設定 `active = false`，收到結果後更新顯示值並恢復操作。開關行至少 40×14 GUI 單位。
- 狀態圖文沿用 `GraphicDisplay.drawRows()`，可透過 `Component` 樣式為不同數值著色。`UiPreferences` 控制高對比與減少動效。

```java
// init() 中註冊控件。Bounds 是 GraphicDisplayLayout.Bounds。
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

// render() 中先畫底板、標題與狀態行，再讓 super.render(...) 繪製原生控件。
NetworkTheme.drawPanel(graphics, new Bounds(x, y, 184, 132), preferences);
NetworkTheme.drawField(graphics, font, new Bounds(x + 12, y + 10, 160, 18), networkTitle, preferences);
var statusLayout = GraphicDisplayLayout.arrange(new Bounds(x + 12, y + 62, 160, 30),
        statusEntries.size(), 30, false, new GraphicDisplayLayout.Grid(160, 1));
GraphicDisplay.drawRows(graphics, font, statusLayout, statusEntries, 1, DarkModeTheme.TEXT, 0);
```

示例中的翻譯鍵、頁面切換、草稿、狀態條目與回包邏輯由接入方提供。

### 整頁網路佈局與 Screen 模板

`client.gui.display.NetworkScreen` 可以直接傳給 `Minecraft#setScreen`。模板會佈置 1–7 個頂部圖示頁籤、獨立的右側新增按鈕、網路標題、設定行、狀態圖文和開關。畫面有返回圖示，也支援 Escape 返回父畫面。每個頁籤保留自己的頁碼，窄視窗自動折行，內容過高時按完整行分頁。

```java
// 在客戶端執行緒執行。以下翻譯鍵及 state、otherTabs 由接入方提供。
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
tabs.addAll(otherTabs); // 最多再加 6 頁，每頁提供自己的標題和內容行。
var add = new NetworkScreen.AddAction(Component.translatable("your_mod.network.create"), state::openCreate);
Minecraft.getInstance().setScreen(new NetworkScreen(parent,
        Component.translatable("your_mod.network.screen"), preferences, tabs, add));
```

`Status` 返回一個 `GraphicDisplay.Entry`。它的標籤可用 `CommonComponents.NEW_LINE` 連線傳輸與快取兩行，並分別設定數值顏色。`Edit` 的配置會在切頁、翻頁或縮放後重建控件，草稿儲存在接入方的狀態裡。`Toggle` 每幀讀取已確認的值，提交回調只發出請求。等待確認時讓 `canChange...` 返回 `false`。

已有畫面可單獨呼叫 `NetworkLayout.arrange(width, height, tabCount, hasAdd, rowKinds, font.lineHeight, pageIndex)`。傳入的行型別為 `FIELD`、`STATUS`、`TOGGLE`，返回的 `Bounds` 可直接交給 `NetworkTheme`、`GraphicDisplay` 或原生控件。結果的 `firstRow` 是當前頁對應的原始行下標，`pageIndex` 從 0 開始。空間不足以容納完整控件時返回空值。

### 小型彈出通知

在 Forge 或 NeoForge 的客戶端收到任務結果後，呼叫 `client.gui.scaled.UiToast`。庫會在當前介面或遊戲 HUD 上顯示提示，不佔用滑鼠或鍵盤焦點。

```java
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToast;

UiToast.taskCompleted(Component.translatable("your_mod.task.example"));
UiToast.show(UiToast.Kind.WARNING,
        Component.translatable("your_mod.notice.warning"),
        Component.empty(), UiToast.Effect.POP);
```

預設從螢幕右側外滑入左上角，停留後向右退出。可選 `DROP_FROM_TOP`（從頂部滑落）、`FADE`（淡入淡出）、`POP`（縮放彈出）和 `NONE`（直接顯示）。狀態有 `INFO`、`SUCCESS`、`WARNING` 和 `ERROR`。

提示依次顯示，預設基礎停留時間為 3500 毫秒，受 Minecraft 通知時長設定影響。完整 `show` 多載可傳入停留時間與 `UiPreferences`，啟用減少動效時會直接顯示。視覺內容最多六行，建議傳入本地化短句。系統旁白啟用時會朗讀完整內容。

#### 可滑動關閉的通知

在原生 `Screen` 內使用 `UiSwipeToast`。`BOTTOM_RIGHT` 固定在畫面右下角；`INLINE` 使用呼叫方傳入的槽位寬度，可用 `occupiedHeight()` 排列後續項目。兩種位置都要註冊成可渲染控件，才能繪製並參與原生鍵盤焦點。開發者面板 → 控件圖庫 → 浮動通知提供兩種示例。

```java
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSwipeToast;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;

// 在 Screen 子類內建立。restore 是由呼叫方提供的撤銷操作。
var toast = addRenderableWidget(new UiSwipeToast(this, UiSwipeToast.Placement.BOTTOM_RIGHT,
        new UiSwipeToast.Content(Component.translatable("your_mod.archive.title"),
                Component.translatable("your_mod.archive.details"), null,
                new UiSwipeToast.Action(Component.translatable("your_mod.archive.undo"), restore)),
        new UiSwipeToast.Options(4000, true, true, true), UiSwipeToast.Style.DEFAULT,
        UiPreferences.configured(), reason -> onToastClosed(reason)));
```

向下拖過閾值或快速輕甩可關閉。短拖、反向釋放及失焦取消會歸位。Tab 聚焦後可用左右鍵選擇操作或關閉，按 Enter／空格啟用，或按 Esc 關閉支援滑動的通知。懸停暫停可配置；拖動、聚焦和視窗失焦時暫停。`Fuse.TOP`、`BOTTOM` 和 `NONE` 只影響進度線的繪製；`setDurationMs(0)` 讓通知持續顯示。

`setOpen(false)` 主動關閉；關閉回調在退出動畫和行內槽位收合結束後觸發一次，可區分 `TIMEOUT`、`SWIPE`、`ACTION`、`CLOSE`、`ESCAPE` 和 `PROGRAMMATIC`。控件由畫面渲染推進，請保留到關閉回調後再移除。`setOpen(true)` 可重新開啟並重新計時。行內模式用 `setInlineBounds(x, y, availableWidth)` 指定 GUI 座標。

可直接使用 `SwipeGesture`（位移、速度和向下滑動判定）和 `PausableCountdown`（暫停、重置和剩餘比例）。兩者接收呼叫方提供的單調毫秒時間，不依賴 Screen 或通知。`ShBetterUiApi.interactions()` 同樣提供 `swipeGesture()`、`countdown(...)` 和 `swipeToast(...)`。

### 中性玻璃渲染預覽

左為調整前，右為調整後。玻璃使用實際 shader 離屏渲染與實際色板；背景、字體與排版為測試示意，並非 Minecraft 實機畫面。文字柔光的消費端接入範圍及驗證結果見[玻璃維護紀錄](docs/neutral-glass-maintenance.md)，遊戲驗收項目見 [TODO](TODO.md)。

![中性玻璃渲染對照](docs/previews/2026-11-07-neutral-glass-comparison.png)

## NeoForge 1.21.1

`neoforge-1.21.1/` 是獨立建置，使用 Java 21、NeoForge 21.1.251、ModDevGradle 2.0.148 與 Gradle 9.2.1。NeoForge 共用玻璃色板、面板、動效與 shader，並以平台來源處理模組入口、客戶端事件、tooltip、模糊管線與原生素材轉接。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:TEMP = 'C:\jtmp'
$env:TMP = 'C:\jtmp'
.\neoforge-1.21.1\gradlew.bat -p neoforge-1.21.1 test build --no-daemon --console=plain
```

正式包為 `neoforge-1.21.1/build/libs/sh_better_ui-neoforge-1.21.1-<version>.jar`。NeoForge 使用 Mojang 命名，不需要 Forge 1.20.1 的 devJar／reobfJar 分流；接入方專案編譯與遊戲端均依賴此正式 jar。

遊戲端另外安裝 **KotlinForForge NeoForge 5.10.0 或相容的 5.x**，不可使用 Forge 1.20.1 的 4.x。已驗證前置 5.10.0 使用 Kotlin 2.2.20，與本庫 Kotlin compiler 2.2.21 同一條版本線。前置由整合包提供，本庫不內嵌 Kotlin／KFF。`FTB Quest Nexus/neoforge` 直接引用此專案產物提供玻璃 UI。

兩平台共用的 shader 將 `WaveOrigin` 宣告為 GLSL `vec2`，匹配 Minecraft 的 `float/count=2` 上傳。資源測試檢查各平台的實際建置輸出。

NeoForge 版本提供原生 `Screen` API 和可選的 `compat.ldlib2.LdLib2UiBridge`。使用橋接時安裝 LDLib2 `2.2.44`，首次建置時聯網解析編譯依賴。Forge 1.20.1 的 `compat.ldlib` 套件隨 Forge 產物釋出。測試與待驗項目見 `docs/neoforge-verification.md` 與 `TODO.md`。

## NeoForge 26.x alpha

`neoforge-26.1.2/` 面向 Minecraft 26.1.2、NeoForge 26.1.2.126 和 Kotlin for Forge 6.2.0。`neoforge-26.2/` 面向 Minecraft 26.2、NeoForge 26.2.0.93 和 Kotlin for Forge 6.3.0。用 JDK 25 啟動 Gradle。共享核心仍編譯為 Java 17 位元組碼，建置會使用專案原有的 JDK 21 編譯工具鏈和 JDK 17 核心測試執行時。

```bash
bash neoforge-1.21.1/gradlew -p neoforge-26.1.2 build --no-daemon --console=plain
bash neoforge-1.21.1/gradlew -p neoforge-26.2 build --no-daemon --console=plain
```

開發包位於各目標的 `build/libs/`，版本帶 `-alpha.8`。兩版共享核心、平台視口、素材目錄、專案編輯服務、搜尋索引、頁面模型和 Kotlin DSL。擴充模組須在 `ModuleDescriptor.platforms` 中宣告對應的 `NEOFORGE_26_1_2` 或 `NEOFORGE_26_2`。

使用 `ShBetterUiApi.controls()` 建立按鈕、開關、整數滑桿、步驟器、固定或可滾動分區欄、下劃線標籤頁和展開控件。將原生控件註冊到 `Screen`，開關註冊 `button()`，固定分區欄逐一註冊 `buttons()`。宿主可在關閉畫面前先將 Esc 交給焦點控件，以取消滑桿編輯或標籤欄拖動。自繪介面可在 `extractRenderState` 中呼叫 `GuiGeometry.fill` 提交 `FilledGeometry<Geometry2.Local>`，使用當前 GUI 姿態、裁剪和 ARGB 顏色。

### 背景模糊、液態玻璃與控制面板

進入世界後按 F8，或從 NeoForge 的模組列表開啟 SH-Better-UI 配置。面板提供玻璃、顯示和外觀設定，可預覽後應用模糊、折射、邊緣高光、主題、高對比和減少動效。應用成功後寫入當前實例的 `config/sh_better_ui/control-panel.properties`。恢復預設可以撤銷，離開未應用的草稿會要求確認。消費模組可透過 `ShBetterUiApi.settings()` 讀取已應用設定或開啟面板。

「工具」頁可查看模組版本、幀率、堆記憶體、視窗縮放和實際啟用的圖形後端。回饋連結遵循 Minecraft 聊天設定的網頁連結和確認選項。「開啟面板」快捷鍵透過 Minecraft 的按鍵綁定介面修改，返回時保留面板草稿。

從「工具 → 開發者圖庫」可開啟 76 個樣例，涵蓋舊版全部 75 個樣例和 26.x 新增的滾動分區欄。原生樣例包含控件與轉場、空間材質、動態主題、粒子特效、真實音樂播放、同步歌詞和圖庫內活動島。圖庫可單獨儲存外觀預覽，返回後保留控制面板草稿。各版本覆蓋範圍見 [26.x 圖庫遷移清單](docs/NEOFORGE_26_GALLERY_GAPS.md)。

宿主完成背景提取後，可呼叫 `ShBetterUiApi.blur().background(graphics)`。全屏模糊沿用 Minecraft 視訊設定中的選單背景模糊強度，後續提取的前景保持清晰。要在玻璃控件內局部採樣，先呼叫 `ShBetterUiApi.blur().prepareBackdrop(graphics)`，再提取按鈕、滑桿或 `LiquidGlassSurface`。需要兩種效果時先請求全屏模糊。玻璃透過同幀 GPU 快照實現局部模糊、折射和邊緣高光，高對比模式使用實色底板。

### Vulkan 與 GLFW 65540

Minecraft 26.1.2 使用 OpenGL。Minecraft 26.2 提供原生 Vulkan 和 OpenGL，兩者使用同一套控件提取程式碼。未儲存後端偏好的 26.2 實例在關閉 NeoForge 早期載入視窗後優先嘗試 Vulkan，初始化失敗時由 Minecraft 回退到 OpenGL。已儲存的圖形 API 和啟動參數繼續生效。

NeoForge 26.2 的早期載入視窗使用 OpenGL，Vulkan 無法複用該視窗。修復版在早期視窗存在時以 OpenGL 啟動，保留已儲存的偏好。若要在 26.2 使用 Vulkan，退出客戶端，在該實例的 `config/fml.toml` 中設定 `earlyWindowControl = false`，再從視訊設定選擇優先 Vulkan 並重啟。開發環境的 `runClient` 會為新建的 `run-vulkan` 目錄預設此配置，`runClientOpenGl` 使用獨立的 `run-opengl` 目錄。 [NeoForge 上游問題](https://github.com/neoforged/NeoForge/issues/3230)

隔離客戶端煙測會載入打包 JAR，驗證控件和圖庫輸入、模糊與玻璃像素、設定應用、模組列表配置按鈕和世界內 F8 鍵盤回調。最後一條命令只在該煙測行程中把 Vulkan 驅動清單指向不存在的檔案，以驗證真正的初始化失敗回退。遊戲自動退出，截圖儲存在各自的 `build/widget-smoke/` 實例目錄。

```bash
bash neoforge-1.21.1/gradlew -p neoforge-26.1.2 -I ../gradle/neoforge-26-widgets-smoke.init.gradle runWidgetSmoke
bash neoforge-1.21.1/gradlew -p neoforge-26.2 -I ../gradle/neoforge-26-widgets-smoke.init.gradle runWidgetSmoke
bash neoforge-1.21.1/gradlew -p neoforge-26.2 -I ../gradle/neoforge-26-widgets-smoke.init.gradle runWidgetSmoke -PwidgetSmokeBackend=opengl
bash neoforge-1.21.1/gradlew -p neoforge-26.2 -I ../gradle/neoforge-26-widgets-smoke.init.gradle runWidgetSmoke -PwidgetSmokeBackend=vulkan -PwidgetSmokeEarlyWindow
bash neoforge-1.21.1/gradlew -p neoforge-26.2 -I ../gradle/neoforge-26-widgets-smoke.init.gradle runWidgetSmoke -PwidgetSmokeVulkanFallback
```

給煙測命令追加 `-PwidgetSmokeLayoutReview` 可在獨立的 `-layout` 實例中驗證原生 GUI Scale 3、窄佈局、鍵盤翻頁焦點、折射拖動取消，以及 F8 和模組列表入口。較窄的邏輯尺寸透過 `Screen.resize` 在同一客戶端中測試。

用 `-PwidgetSmokeLayoutReview=4` 可在 `-layout-4` 實例以 1280 × 960 視窗複驗，並載入長文案測試語言。此模式額外檢查 Tab／Shift+Tab、長標籤提示換行和原生旁白生成的文字。控制面板與圖庫的標題、狀態，以及圖庫按鈕和滑桿的預設標籤，超出空間時會顯示省略號，懸停可讀完整文案。

給標準煙測命令追加 `-PwidgetSmokeNarrator`，可在獨立的 `-narrator` 實例啟用 Minecraft 系統旁白，經遊戲鍵盤回調切到「模式 2」並觸發朗讀。煙測結束時恢復該實例的旁白與音量設定。Windows 實測還可透過 `-PwidgetSmokeNarratorAudio` 接入行程音訊迴環錄製器，檢查客戶端 JVM 輸出的語音波形。

舊版圖庫其餘樣例、其他面板頁面、統一螢幕宿主、著色圖示、文字可讀性、富文字、宣告式頁面、網路和可選模組整合仍在移植範圍內。詳見 [TODO.md](TODO.md)。

### 反重力粒子 API

[文件索引](docs/README.md)

透過 `ShBetterUiApi.graphics().antigravity()` 建立粒子場，獨立輸入跟蹤使用 `ShBetterUiApi.interactions().pointerTracker()`。開發者控件圖庫提供四種形狀、三種配色、暫停與重播。[接入與參數說明](docs/antigravity-api.md)。
