<!-- Author: dishanhai -->
# UUK 文字可讀性 · Phase 1 公共契約

本頁記錄 Phase 1 的交付基線。實際繪製和更新後的 API 行為見 [Phase 2–3](TEXT_READABILITY_PHASE_2_3.md)。

客戶端模組從 `engine.require(UIApis.TEXT_READABILITY)` 取得服務。`UIApis.TEXT_READABILITY_POLICY` 表示作用域策略與診斷可用。以 `ScreenService.mount` 取得頁面樹，再以 `ScopeKind.COMPONENT` 建立控件及同一 Screen 上的附著式彈層。呼叫應在 UUK 客戶端執行緒，並使用模組擁有的作用域。 [服務介面](../src/main/java/com/dishanhai/sh_better_ui/kernel/api/text/TextReadabilityService.java)

自行繪製 HUD 的模組可為每個面板持有一個 `TextReadabilityPalette`。先繪製背景，再呼叫 `update(sample, darkestArgb, lightestArgb)`，其中背景樣本應取自材質繪製完成後、文字繪製前。取樣尚未完成或後端不支援時，提供面板與場景合成後的最暗和最亮色。透過 `color(argb)` 調整基礎字色，並將 `glyphs(sequence)` 傳給原生字型以調整行內色彩。色彩以 4.5:1 為目標，單一顏色無法涵蓋整個亮度範圍時會選擇最差位置對比較高的黑或白。`UiHudPanel.draw` 已代為處理，呼叫端可傳入穩定的取樣 `UiId`。 [面板配色 API](../src/main/java/com/dishanhai/sh_better_ui/kernel/api/text/TextReadabilityPalette.java)

## 策略與渲染歸屬

- `configure` 替換該節點的覆寫。`null` 逐欄繼承，`false` 明確關閉。兩項能力的預設值都為 `false`。
- `configurePopup` 將彈層及其後代綁定至彈層的渲染作用域。先使用其 `ThemeScope` 的 `text.adaptiveColor` 和 `text.foregroundProtection`。傳入同一 Screen 的所屬頁面樹時再覆蓋該頁已明確設定的欄位，最後套用彈層及其控件的設定。省略所屬頁面時，頁面可讀性設定不會傳入彈層。
- `FixedContrast` 接受 1 到 21 的有限比率並優先採用。用 `ClassifiedContrast.INSTANCE` 可清除繼承的固定門檻。預設按用途分類，`BODY` 為 4.5∶1，`DISPLAY` 為 3∶1。選用 `FRAMEBUFFER_SIZE` 時，以視覺變換後沿行高軸的單行 GUI 高度乘以 GUI scale，在裁剪前換算至 framebuffer px。大字預設門檻為可覆蓋的 24 px，待 Phase 6 校準。 [策略模型](../src/kernel/java/com/dishanhai/sh_better_ui/kernel/api/text/TextReadabilityPolicy.java)

```java
var readability = module.engine().require(UIApis.TEXT_READABILITY);
readability.configure(module, pageTree, new TextReadabilityPolicy(true, false, null, null, null));
readability.configurePopup(module, popupNode, popupTheme, pageTree,
        new TextReadabilityPolicy(false, true, null, null, null));
```

```kotlin
val readability = module.engine().require(UIApis.TEXT_READABILITY)
readability.configure(module, pageTree, TextReadabilityPolicy(true, false, null, null, null))
readability.configurePopup(module, popupNode, popupTheme, pageTree,
    TextReadabilityPolicy(false, true, null, null, null))
```

## 請求與診斷

`Request` 使用已排版的整段 `FormattedCharSequence`，並帶上原始底色與陰影、用途、局部 GUI 座標、視覺變換、有效祖先／視口裁剪及組透明度。富文本可按已量測的樣式片段提交。局部分色需等到有可靠的字形簇映射。 `submit` 可回報 `CLIPPED`、`HIDDEN`、`SCOPE_CLOSED`、`INVALID_REQUEST` 或 `RENDER_UNAVAILABLE`。本階段 `drawNow` 對有效請求回報 `INVALID_STAGE`，既有 `RenderService.Context` 不能當作可讀性前景上下文。Phase 3 會接入同層前景並啟用 `SUBMITTED` 和 `DRAWN` 結果。

以 scope 實例、`TextId.text`、可選的 `dataItem` 及 `fragment` 識別跨幀記錄。呼叫 `retireDataItem` 可在虛擬行更換資料時移除該行及其後代的舊資料項狀態。沒有穩定 ID 的請求可驗證當次結果，且沒有跨幀診斷記錄。`diagnostics` 提供驗證來源幀、控件與渲染層 generation、解析策略、目標對比及裁剪後範圍。歷史最多 2,048 筆，閒置五秒清理，達容量時淘汰最早提交的記錄。關閉或重開作用域、資源重載、主渲染目標或 GUI scale 變更會使相關舊記錄失效。 [客戶端生命週期](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/ClientTextReadability.java)

## 驗證

```bash
./gradle-install/gradle-8.8/bin/gradle build kernelTest verifyLibraryJar --offline --no-daemon --console=plain --max-workers=2
bash neoforge/gradlew -p neoforge build kernelTest verifyLibraryJar --offline --no-daemon --console=plain --max-workers=2
```

| 載入器 | `kernelTest` | `test` | JAR 檢查 |
| --- | ---: | ---: | --- |
| Forge 47.4.16 | 310 通過 | 950 通過 | `build/libs/sh_better_ui-1.0.7.jar` |
| NeoForge 21.1.251 | 310 通過 | 952 通過 | `neoforge/build/libs/sh_better_ui-neoforge-1.21.1-1.0.7.jar` |

兩端均執行六項新增的策略測試和五項客戶端作用域測試，零失敗、零錯誤、零跳過。JAR 包含公共契約與服務實作，煙測類別留在測試來源集。以 Kotlin 2.2.21 對兩端映射後的 Minecraft 與服務類別編譯消費範例，JVM 17 和 21 均成功。 [共用測試](../src/kernelTest/java/com/dishanhai/sh_better_ui/kernel/api/text/TextReadabilityPolicyTest.java) · [客戶端測試](../src/test/java/com/dishanhai/sh_better_ui/kernel/internal/ClientTextReadabilityTest.java)

Phase 2–6 接續 CPU 參考、真實繪製、GPU 決策、UUK／第三方／SHUI 接入與校準。 [階段待辦](../TODO.md)
