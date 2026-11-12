<!-- 作者：山海／dishanhai -->
# UUK 文字可讀性 Phase 4–5

## 實際背景分析

`ClientTextReadability` 在文字實際繪製時檢查同幀排版、所屬層與裁剪。啟用清晰前景的文字沿用 Phase 3 的層內繪製次序。GPU 路徑先 flush 已排入的 GUI 繪製，將下方主目標的有效 ROI blit 到獨立附件，再將已排版的原生序列繪成字形遮罩。採樣、線性亮度、p10、候選選色、保護、穩定計時與同幀合成都留在 GPU。宣告背景或未知背景的 CPU 路徑沿用 `TextReadabilityReference`。

採樣以分區格網尋找字形內部。每個格最多讀取一個背景樣本，僅接受足夠實心且背景已合成為不透明的點。樣本不足時 GPU 同幀改用宣告背景或未知背景。若 CPU 端已確認預算不足、目標不可採樣或區域無效，就走 CPU 降級路徑。診斷可分辨這些原因與實際背景的低對比。一般繪製路徑無同步像素讀回。

GPU 狀態用 2,048 個整數槽保存，無穩定 ID 的文字走無狀態路徑。作用域、資料列、呈現幀缺口、顏色決策設定和渲染目標切換都會使舊狀態失效。GPU 暫存尺寸上限為 2,048 × 256 framebuffer 像素。實際採樣使用原生 Screen 的主 GUI 目標和完整 viewport。相容原生 GUI 投影的其他 framebuffer 或子 viewport 可走全不透明的 CPU 降級繪製。任意自訂投影及非主目標的組透明度繪製會回傳 `RENDER_UNAVAILABLE`，由呼叫端在自己的渲染階段接手。

### 暫定預算

| 優先級 | 保留文字數 | 每筆最多背景樣本 | 保留複製像素 |
| --- | ---: | ---: | ---: |
| 一般 | 32 | 8 | 196,608 |
| 顯式重要 | 16 | 16 | 196,608 |
| 懸停 | 8 | 32 | 327,680 |
| 焦點 | 8 | 32 | 327,680 |

合計上限為每幀 64 筆、1,024 個背景樣本、1,048,576 個複製像素。高優先級可借用低優先級餘額並使用該配額的密度。頁面可設為 `EXPLICIT_ONLY`，自動偵測的懸停與焦點仍須有控件的顯式標記才會採樣。

校準時可用 JVM 選項 `-Dshb.textReadability.<general|important|hovered|focused>.<runs|samples|pixels>=N` 下調配額。另可設定 `minimumSamples`、`interiorCoveragePercent` 與 `outlinePixels`，預設分別為 4、75、16，使用相同屬性前綴。

`samplingStatistics(module)` 回傳最近結束的原生幀的分析筆數、樣本與複製像素、CPU 端降級次數、CPU 提交與繪製耗時、GPU 資源用量和存活槽位。GPU 時間由可用的時間戳查詢非同步返回，帶來源幀和計時筆數。計時查詢環滿時會略過測量。GPU 樣本不足可從按需讀回的決策辨識。

## 第三方與診斷

第三方 Screen 透過 `UIApis.SCREENS` 掛載本模組的 UI tree，再使用 `UIApis.TEXT_READABILITY` 配置作用域。以 `Run` 傳入已排版的視覺序列、原點和範圍。預設用 `submit`。要即時繪製時，由 `foreground` 取得當幀的作用域上下文，再呼叫 `drawNow`。可在 [Java Screen 範例](../examples/kernel-extension/common/src/main/java/com/dishanhai/shbui_kernel_example/TextReadabilitySdkScreen.java) 查看完整入口。

`/shbettertextreadability` 開關遊戲內疊層。將指標移到接入服務的文字上可查看文字範圍、裁剪、階段結果、採樣狀態、對比與保護原因。Java／Kotlin 可用 `setGpuDiagnostics(module, scope, true)` 開啟自己的子樹採集。詳細結果透過固定容量的 PBO 和 fence 延後返回，來源幀與待回傳幀分開標記。作用域關閉、資料列回收、目標或資源失效時，舊結果不會傳入新世代。停用診斷後停止送出詳細讀回。

開啟 `localColor` 後，`submit` 與 `drawNow` 可在同一個原生 `FormattedCharSequence` 內依視覺字形簇選色。服務沿用 Minecraft 已重排的 sink 順序與字型的逐字形浮點 advance，保留 inline style，並把基底字與組合記號留在同一簇。各簇從同一份繪字前的背景取樣，共用既有幀預算，各自保有穩定狀態。父 run 有任何簇完成 GPU 分析時，診斷的採樣狀態為 `LOCAL_GPU`。

組透明度過渡、缺少可靠邊界的反向組合字、裝飾線、混淆字及依賴整段位置的效果沿用原生整段路徑。有混合行內色且無法局部分色時，保留作者配色與前景保護。關閉 `localColor` 的混合行內色自適應請求仍回傳 `INVALID_REQUEST`。原生 RichTextView 沿用原本排版的片段，虛擬列重用 view 時用 `readabilityDataItem` 切換 ID 並回收上一筆資料的狀態。

`-PkernelSmokeTextReadabilityLocal -PkernelSmokeLabel=uuk-local-color-focused` 已在 Forge Java 17 與 NeoForge Java 21 通過。煙測核對單一序列跨越明暗背景的字形像素、混合行內色與粗體、`submit`、`drawNow` 和退出清理。Forge 的完整 GPU 煙測通過這些斷言後，停在原生診斷浮層的視窗懸停探針。 [Forge 結果](../build/kernel-verification/client-forge-uuk-local-color-focused-text-readability-gpu/success.txt) · [NeoForge 結果](../build/kernel-verification/client-neoforge-uuk-local-color-focused-text-readability-gpu/success.txt)

SHUI 支援宣告式頁面、控件、附著彈層及受限腳本提交。附著彈層預設從原生主題取得設定，`inheritPage: true` 再套用頁面覆蓋。完整欄位和腳本範例見 [SHUI API](kubejs-api.md)。

## 驗證

Forge 1.20.1／47.4.16 和 NeoForge 1.21.1／21.1.251 均通過 `build kernelTest verifyLibraryJar`。共用測試各 328 項，一般測試分別 957 和 959 項，無失敗或跳過。兩份 1.0.7 程式庫 JAR 含公用 API、SHUI adapter 和四份 GLSL 資源，均排除煙測類別。外部 SDK 專案的 Java 與 Kotlin 2.2.21 均以對應版本的程式庫 JAR 編譯和封裝。

獨立 OpenGL 3.2 core 路徑以固定種子的 800 組向量對照 CPU，涵蓋實際、宣告與未知背景、色相鎖、透明度及保護。背景來源、填色及保護類型一致，p10 最大絕對誤差為 `7.13e-6`，容差為 `2e-5`。另直接驗證 GPU 在 `119,999,999 ns` 保留既有填色，在 `120,000,000 ns` 達成切換。遊戲內煙測用 400 ms 覆蓋值保留足夠時間擷取第一個亮背景幀。

從專案根目錄重跑開發客戶端 GPU 驗證。

```bash
./gradle-install/gradle-8.8/bin/gradle -I gradle/kernel-smoke.init.gradle runClient -PkernelSmokeTextReadabilityGpu -PkernelSmokeLabel=phase45-final --offline --no-daemon --console=plain --max-workers=2 -x downloadAssets
bash neoforge/gradlew -p neoforge -I ../gradle/kernel-smoke.init.gradle runClient -PkernelSmokeTextReadabilityGpu -PkernelSmokeLabel=phase45-final --offline --no-daemon --console=plain --max-workers=2
```

SHUI 腳本沿用同一個 init script。將變體改為 `-PkernelSmokePages -PkernelSmokeLabel=phase45-final -PkernelSmokeKubeJsMods=<loader 對應模組目錄>`。Forge 使用 KubeJS 2001.6.5-build.26、Rhino 2001.2.3-build.10 和 Architectury 9.2.14，NeoForge 使用 KubeJS 2101.7.2-build.381 和 Rhino 2101.2.7-build.81。以 `SHB_TEXT_KUBEJS_VERIFIED` 和煙測完成標記確認腳本路徑。

雙端開發客戶端均在 RTX 4060 Laptop、GL 4.6、800×480、GUI Scale 2 下通過。截圖和逐幀 TSV 位於 `build/kernel-verification/client-{forge,neoforge}-phase45-final-text-readability-gpu/`。兩端在首個亮背景幀保留白字並加黑底，穩定後改為黑字並撤掉底襯。跨明暗文字、動態 Liquid Clear 模糊、富文本片段、原生疊層和樣本位置均經像素檢查。另一個 framebuffer 的子 viewport 驗證了聲明背景降級、文字實際寫入和讀寫目標及 viewport 恢復。分開驗證無效區域、stencil 路徑、字形樣本不足、樣式混色和過期繪製上下文。

| 開發客戶端 | 穩態 4 筆 CPU 中位／P95 | 穩態 4 筆 GPU 中位／P95 | 壓力 49 筆 CPU 中位／P95 | 壓力 49 筆 GPU 中位／P95 |
| --- | ---: | ---: | ---: | ---: |
| Forge | 2.46／3.12 ms | 0.16／0.17 ms | 7.71／14.02 ms | 6.16／12.11 ms |
| NeoForge | 2.58／3.71 ms | 0.17／0.37 ms | 7.50／12.87 ms | 6.37／11.38 ms |

穩態取 30 幀，壓力取 20 幀，其中有 18 幀的 GPU 時間在測試結束前已完成。P95 使用最近排名。壓力場景每幀分析 49 筆，包含 48 筆重要文字及保留配額的焦點文字，另有 22 筆降級，總計 544 個樣本及 69,088 個複製像素。兩端暫存與診斷配置峰值均為 134,368 bytes。表中 CPU 包含渲染命令提交，GPU 為有對應來源幀的時間戳資料。首次 shader 編譯及真實整合包的數值另待 Phase 6 校準。

外部 SDK 的兩個 `runClient -PexampleSmoke` 均以分開的範例和程式庫 JAR 啟動。Forge 使用 Mojmap 開發 JAR 和 Java 17，NeoForge 使用正式 JAR 和 Java 21。兩端都從第三方 Screen 驗證 `submit`、有效前景的 `drawNow` 及 Esc 關閉後的作用域釋放。結果位於 `examples/kernel-extension/{forge,neoforge}/build/example-smoke/`，重跑命令見 [範例 README](../examples/kernel-extension/README.md#客戶端煙測)。
