<!-- Author: dishanhai -->
# UUK 文字可讀性 · Phase 2–3

## CPU 參考

`TextReadabilityReference` 按一次 sRGB 解碼及線性相對亮度計算對比度。它接受已合成的 sRGB 實際樣本、已轉換的線性實際樣本，以及不透明的代表背景。實際取樣有效且數量達門檻時優先使用，否則依序採用宣告背景和未知背景保護。實際樣本須位於有效裁剪內並達到字形內部覆蓋率。預設至少 4 個樣本，覆蓋率 0.75，細描邊適用的最低行高為 16 framebuffer px。三個值都可由 `Sampling` 覆蓋，待 Phase 6 校準。 [參考實作](../src/kernel/java/com/dishanhai/sh_better_ui/kernel/api/text/TextReadabilityReference.java)

相對亮度係數為 `0.2126 / 0.7152 / 0.0722`，對比度為 `(Lmax + 0.05) / (Lmin + 0.05)`，判定容差為 `1e-6`。整段判定將對比度由小到大排序，採零基底索引 `floor((N - 1) / 10)`。宣告的代表背景按一個樣本處理。候選選擇依序測原色、同色相明度、黑、白、主題候選。同色相搜尋朝黑白各取 31 個等步幅點，相同步幅先測較深候選，8-bit 色相量化容差為 2°。色相鎖定時只測保留色相的候選。整段達標仍檢查字形附近低對比點，符合行高及覆蓋條件時先用細描邊，否則採用貼合行框的底襯。未知背景以底襯提供獨立對比，並記錄達不到目標的原因。 [CPU 測試](../src/kernelTest/java/com/dishanhai/sh_better_ui/kernel/api/text/TextReadabilityReferenceTest.java)

穩定切色預設等候 120 ms，由作用域的 `stabilityNanos` 覆蓋。穩定 ID 持有候選狀態，候選失效、來源切換或中斷觀測會中止連續計時。當前填色不足時當幀套用保護。切色採離散更新，已切色後原色重新達標時也走同一穩定計時。未知背景和宣告背景可在原生畫面繪製，實際背景資料目前由 CPU 測試向量提供，GPU 決策及預算屬於 Phase 4。

## 畫面與彈層

頁面 `submit` 使用 `Screen.Render.Pre` 建立的同幀渲染作用域。啟用前景保護後，請求進入現有 `VisualFrameCoordinator` 前景佇列，與管理前景共享 Z 值和同 Z 登記順序。畫面 Post 封閉佇列、執行幀末模糊，再繪製清晰前景並合成上層彈層。僅啟用自適應選色時，`submit` 在原本的合法頁面或彈層內容 pass 畫入。未啟用自適應選色時保留原始的字形樣式和顏色。 [前景佇列](../src/main/java/com/dishanhai/sh_better_ui/client/visual/VisualFrameCoordinator.java)

`Request.forVisual` 讀取同一份 `FrameViewSnapshot` 的變換和有效裁剪，並以畫面、幀及布局作用域世代驗證快照。手動建構的請求可提供已排版的 `FormattedCharSequence` 和實際有效裁剪。單一原生序列預設整體決策，`localColor` 可啟用[原生字形簇局部分色](TEXT_READABILITY_PHASE_4_5.md#第三方與診斷)。CPU 接入可以把已驗證的視覺字形簇交給 `evaluateVerifiedClusters`，逐簇決策後合併同色的相鄰簇，並保留顯式分段。 [公共服務](../src/main/java/com/dishanhai/sh_better_ui/kernel/api/text/TextReadabilityService.java)

附著式彈層在自己的原生 overlay callback 使用 `renderPopup` 包住內容。其背景和即時效果先完成，彈層前景在外層裁剪仍有效時消費。直接繪製用 `foreground` 回呼取得 `RenderService.Context`，再呼叫 `drawNow`。該 context 限於目前幀、回呼和精確的請求 scope。過期或外來 context 回報 `INVALID_STAGE`。封閉後提交回報 `QUEUE_CLOSED`，同一幀重複消費同一請求回報 `ALREADY_CONSUMED`。畫面取消、關閉、舊布局、目標失效或虛擬行回收會阻止舊請求繪製。 [客戶端服務](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/ClientTextReadability.java)

部分組透明度用兩份裁剪大小的 RGBA8 scratch 保存前後畫面，線性混合已合成的文字、描邊及底襯，再一次寫回裁剪範圍。Minecraft 字形內部保留載入器原生的 atlas 和抗鋸齒行為。組透明度歸零時跳過繪製。GL scratch 在重載及關閉時釋放，遊戲畫面路徑沒有同步像素讀回。原生繪製支援主 GUI framebuffer 的完整 viewport，遇到其他目標回報 `RENDER_UNAVAILABLE`。 [原生繪製器](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/TextReadabilityPainter.java)

## 原生驗證

Git Bash 使用 JDK 21 啟動 Gradle，Forge 煙測的遊戲程序使用 Java 17。

```bash
./gradle-install/gradle-8.8/bin/gradle -I gradle/kernel-smoke.init.gradle runClient -PkernelSmokeTextReadabilityService --offline --no-daemon --console=plain --max-workers=2 -x downloadAssets
bash neoforge/gradlew -p neoforge -I ../gradle/kernel-smoke.init.gradle runClient -PkernelSmokeTextReadabilityService --offline --no-daemon --console=plain --max-workers=2
```

Forge 47.4.16／Java 17.0.18 與 NeoForge 21.1.251／Java 21.0.11 都完成原生測試及正常關閉。RTX 4060 Laptop、OpenGL 4.6、800×480、GUI scale 2。兩端的頁面與彈層截圖各自逐位元組一致。 [原生測試](../src/kernelSmoke/java/com/dishanhai/sh_better_ui/kernel/smoke/TextReadabilityServiceSmoke.java)

| 觀測 | 兩端結果 |
| --- | --- |
| 黑底白字對照／幀末模糊前繪製／幀末模糊後的清晰前景 | 680／0／680 個純白像素 |
| 宣告背景選色／巢狀捲動裁剪內白字 | 680／294 個純白像素 |
| 未知背景底襯／半透明底襯／半透明字形內部 | `FF000000`／`FF5C3838`／`FFCDC2C2` |
| 上層彈層遮住下層文字／彈層裁剪內白字 | 2,160 個遮擋像素／340 個純白像素 |

測試也驗證同 Z 登記順序、保留原始行內字色、選色與前景保護獨立開關、過期布局、錯誤作用域、錯誤階段、重複請求、佇列封閉及回收列取消。證據位於 `build/kernel-verification/client-{forge,neoforge}-text-readability-service/` 的 `success.txt`、`stopped.txt` 與兩張截圖。

## 雙端構建

```bash
./gradle-install/gradle-8.8/bin/gradle build kernelTest verifyLibraryJar --offline --no-daemon --console=plain --max-workers=2
bash neoforge/gradlew -p neoforge build kernelTest verifyLibraryJar --offline --no-daemon --console=plain --max-workers=2
```

| 載入器 | `kernelTest` | `test` | JAR 驗證 |
| --- | ---: | ---: | --- |
| Forge 47.4.16 | 326 通過 | 950 通過 | `build/libs/sh_better_ui-1.0.7.jar` |
| NeoForge 21.1.251 | 326 通過 | 952 通過 | `neoforge/build/libs/sh_better_ui-neoforge-1.21.1-1.0.7.jar` |

兩端零失敗、零錯誤、零跳過。新增 16 項 CPU 參考測試，覆蓋原色、色相鎖定、低分位、局部亮斑、描邊升級、宣告／未知背景、透明度、計時重置、120 ms 邊界及視覺字形簇合併。Kotlin 2.2.21 對兩端映射後的類別編譯 `forVisual`、`renderPopup`、`foreground`、`drawNow` 及診斷範例成功。JAR 包含公共契約與 painter，煙測類別留在測試來源集。

開發客戶端證據涵蓋上述 GPU、解析度和 GUI scale。實際背景 GPU 決策、預算、腳本與原生控件接入、發佈 JAR 客戶端和整合包驗收仍在後續階段。 [待辦](../TODO.md)
