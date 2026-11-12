<!-- 作者：山海／dishanhai -->
# 富文本驗證與效能基線

## Phase 0 建置基線

2026-11-11 於 Windows 11 x64 採集。當時的 HEAD 為 `ab5a80b`，以已有未提交改動的工作樹建置，尚未加入富文本實作。兩端的建置程序由 Oracle JDK 21.0.11 啟動，Forge 1.20.1 開發客戶端與純核心基準使用 Java 17，NeoForge 1.21.1 開發客戶端使用 Java 21。

從專案根目錄在 Git Bash 執行。`JAVA_HOME` 指向本機 JDK 21，臨時目錄須存在並可寫入。

```bash
export JAVA_HOME='C:/Program Files/Java/jdk-21.0.11' TEMP=C:/jtmp TMP=C:/jtmp
./gradle-install/gradle-8.8/bin/gradle build --offline --no-daemon --console=plain
bash neoforge/gradlew -p neoforge build --offline --no-daemon --console=plain
```

| 目標 | Gradle | 基線結果 | `kernelTest`、`test` 的既有 XML |
| --- | --- | --- | --- |
| Forge 1.20.1 / 47.4.16 | 8.8 | 成功，13 秒，執行 `reobfJar`、`releaseJar`、`verifyLibraryJar` | 192、879 項，失敗、錯誤、跳過均為 0 |
| NeoForge 1.21.1 / 21.1.251 | 9.2.1 | 成功，10 秒，執行 `verifyLibraryJar` | 192、881 項，失敗、錯誤、跳過均為 0 |

兩端的 `kernelTest` 和 `test` 都回報 `UP-TO-DATE`。上表的測試數源自當時的 `build/test-results/{kernelTest,test}` 和 `neoforge/build/test-results/{kernelTest,test}`，代表 Gradle 沿用的測試結果。13 秒和 10 秒是本次增量建置耗時。基線發行包分別為 `build/libs/sh_better_ui-1.0.6.jar` 與 `neoforge/build/libs/sh_better_ui-neoforge-1.21.1-1.0.6.jar`。

## Phase 0 CPU 基線

現有 `KernelBenchmark` 的 `local-dirty-1000` 場景以共用核心的 1,000 個節點為固定負載。機器使用 Intel Core i9-14900HX。兩端各使用一個新的 Eclipse Temurin 17.0.18+8 JVM，固定堆 512 MiB、G1、4 個可用處理器及同一種子 `20261110`。各預熱 4,000 次、採樣 3,000 次。每次樣本使用相同的模擬幀長 16.666667 ms，實際處理 9 個 dirty entries，兩端各累計 27,000 個。耗時為單執行緒操作的牆鐘時間，分配量取自 `ThreadMXBean`，P95 和 P99 採 nearest rank。

| 核心平台選擇器 | 耗時平均 / P95 / P99 | 分配平均 / P95 |
| --- | --- | --- |
| `FORGE_1_20_1` | 7.374 / 8.600 / 16.600 µs | 3,880.15 / 3,880 B |
| `NEOFORGE_1_21_1` | 7.102 / 8.400 / 10.799 µs | 3,880.15 / 3,880 B |

本次依序用 Java 17 直接執行 `KernelBenchmark`，classpath 取兩端建置目錄內的 `classes/java/kernelBenchmark` 和 `classes/java/kernel`。原始資料在 `build/reports/rich-text-baseline/{forge,neoforge}`，含 `raw.csv`、`summary.csv`、`work.csv` 和 `environment.properties`。重跑時可用 `kernelBenchmark` 任務編譯執行器並帶入相同場景與 JVM 設定。使用新的輸出目錄留存每組樣本。

```bash
./gradle-install/gradle-8.8/bin/gradle kernelBenchmark --offline --no-daemon --console=plain \
  -PkernelBenchmarkScenario=local-dirty-1000 -PkernelBenchmarkWarmup=4000 -PkernelBenchmarkSamples=3000 \
  -PkernelBenchmarkOutput=build/reports/rich-text-baseline/forge-repeat
bash neoforge/gradlew -p neoforge kernelBenchmark --offline --no-daemon --console=plain \
  -PkernelBenchmarkScenario=local-dirty-1000 -PkernelBenchmarkWarmup=4000 -PkernelBenchmarkSamples=3000 \
  -PkernelBenchmarkOutput=../build/reports/rich-text-baseline/neoforge-repeat
```

這組數字作為 UUK 既有工作量的回歸參考。Phase 0 尚無富文本場景，因此富文本佈局耗時、原生 CPU／GPU 幀時間、堆／顯存、重新排版次數、資源快取命中率與 draw call 均無變更前數字。[既有 UUK 原生效能報告](performance.md) 和[材質效能報告](performance-report.md) 使用各自的場景。

後續富文本基準以固定字型、文字、圖片、容器寬度、GUI Scale、視窗尺寸、資源包及排版策略，分別取樣首次開啟、穩定重繪、縮放換行、資源重載、局部更新和長文檔。每端記錄硬體、驅動、JVM、預熱量、樣本數、平均、P95、CPU 與 GPU 幀耗時、堆分配、可用的顯存統計、排版次數、命中率及 draw call。確定同場景工作量後再設定數值門檻。

## Phase 1 驗收矩陣

| 場景 | 自動驗證 | Forge 與 NeoForge 原生門禁 |
| --- | --- | --- |
| 文字與靜態圖片混排 | 合成字型度量下核對同行、來源順序、固有比例、基線與行高 | 展示頁的真實字形和資源圖片同時出現在同一行的 framebuffer |
| 換行、窄視窗、超寬圖片 | 比對多種可用寬度下的行數、原子圖片與行盒，檢查小於內容的容器 | 可見邊界與快照幾何一致，較窄的 GUI 可用寬度讓內容增加行數 |
| GUI Scale | 以同一邏輯座標重跑快照幾何 | 比對至少兩檔 GUI Scale 的快照與 framebuffer，確認圖文保持同行且視口變更後換行更新 |
| 命中邊界 | 核對行盒的空隙、圖片內外與裁剪邊界 | 使用當幀已繪製的位置命中圖片，外側邊界不誤判 |
| 圖片缺失、重載 | 固定宣告比例時，比對占位和正常圖的幾何 | 起始可見占位。安裝測試資源包並完成 reload 後，資源世代前進且同區域顯示補齊的圖片 |
| 雙平台套件 | 兩端執行 `build`，檢查 `kernelTest`、`test` 及 `verifyLibraryJar` | 隔離客戶端產生成功與正常關閉標記，保留各平台的畫面證據 |

### 隔離原生客戶端

從專案根目錄依序執行。煙測使用正式展示頁與真實渲染管線，在 `build/kernel-verification/client-{forge,neoforge}-rich-text/` 開啟開發客戶端並自動退出。煙測在這個目錄放置測試資源包，寫入 `rich-text-scale2-before.png`、`rich-text-scale1-before.png`、`rich-text-scale1-reloaded.png` 與 `success.txt`、`stopped.txt`。三張 PNG 依序呈現窄佈局、寬佈局，以及同一寬佈局補齊缺圖後的畫面。測試會比對文字、鑽石和缺圖替代物的可見像素與快照位置。

```bash
./gradle-install/gradle-8.8/bin/gradle -I gradle/kernel-smoke.init.gradle \
  -PkernelSmokeRichText runClient --offline --no-daemon --console=plain
bash neoforge/gradlew -p neoforge -I ../gradle/kernel-smoke.init.gradle \
  -PkernelSmokeRichText runClient --offline --no-daemon --console=plain
```

2026-11-11 在 800×480 開發客戶端、`en_us`、GeForce RTX 4060 Laptop GPU／NVIDIA 610.88 執行。兩端均驗證圖文同行、8:1 寬圖、同一份快照的命中邊界、GUI Scale 改變時舊快照失效，以及 Screen 關閉後的清理。

| 客戶端 | `runClient` | GUI Scale 2 → 1 | 資源重載 | 畫面與記錄 |
| --- | --- | --- | --- | --- |
| Forge 1.20.1 | 成功，27 秒 | 4 → 3 行 | 世代 1 → 2，2:1 圖片佔位不變 | [窄版](../build/kernel-verification/client-forge-rich-text/rich-text-scale2-before.png)、[寬版](../build/kernel-verification/client-forge-rich-text/rich-text-scale1-before.png)、[重載後](../build/kernel-verification/client-forge-rich-text/rich-text-scale1-reloaded.png)、[成功](../build/kernel-verification/client-forge-rich-text/success.txt)、[關閉](../build/kernel-verification/client-forge-rich-text/stopped.txt) |
| NeoForge 1.21.1 | 成功，28 秒 | 4 → 3 行 | 世代 1 → 2，2:1 圖片佔位不變 | [窄版](../build/kernel-verification/client-neoforge-rich-text/rich-text-scale2-before.png)、[寬版](../build/kernel-verification/client-neoforge-rich-text/rich-text-scale1-before.png)、[重載後](../build/kernel-verification/client-neoforge-rich-text/rich-text-scale1-reloaded.png)、[成功](../build/kernel-verification/client-neoforge-rich-text/success.txt)、[關閉](../build/kernel-verification/client-neoforge-rich-text/stopped.txt) |

第一次 Forge 執行在重載時發現 `LoadingOverlay` 會直接呼叫 Screen 的 `render`。該呼叫缺少 UUK 的原生 Screen 階段，觸發了 `VisualFrameCoordinator` 的檢查。展示頁現在會在載入遮罩期間停止提交富文本，遮罩移除後按新資源世代繼續繪製。上表使用修正後的重跑結果。 [初次執行的崩潰記錄](../build/kernel-verification/client-forge-rich-text/crash-reports/crash-2026-11-11_01.47.51-client.txt)

## PE2–4 回歸與原生門禁

```bash
export JAVA_HOME=C:/Progra~1/Java/jdk-21.0.11 TEMP=C:/jtmp TMP=C:/jtmp
gradle-install/gradle-8.8/bin/gradle build kernelBenchmarkClasses --offline --no-daemon --console=plain
bash neoforge/gradlew -p neoforge build --offline --no-daemon --console=plain
gradle-install/gradle-8.8/bin/gradle -I gradle/kernel-smoke.init.gradle -PkernelSmokeRichText -PkernelSmokeRichEffects runClient --offline --no-daemon --console=plain
bash neoforge/gradlew -p neoforge -I ../gradle/kernel-smoke.init.gradle -PkernelSmokeRichText -PkernelSmokeRichEffects runClient --offline --no-daemon --console=plain
```

- JUnit 以原始 XML 計數。Forge 209 + 891 項，NeoForge 209 + 893 項，零失敗、錯誤或跳過。PE2 新增 Unicode 及 64 組隨機文件的 JSON／JsonOps／NbtOps 往返、拒絕資料、穩定 ID、段落、字素編輯和隨機視口契約。PE3 覆蓋幀切換、縮放不改盒、控制項手勢、裁剪、焦點及取消。PE4 覆蓋對比、像素預算及減少動效。
- 原生進階頁以 Scale 1 測試中文和補充字元經過 Screen 的 `charTyped`，並以既有 `EditBox` 提交及復原。選取使用同一份幾何。按鈕重複 release／Enter 不會重複提交；滑桿移出視口放開到端點，Esc 保留舊值；拖放只觸發目標的 drop。捲到文件末端後，原按鈕的座標無舊命中。Glass 與 Reveal 各檢查高亮文字、金蘋果、草方塊和水的像素。兩端先在標題畫面呈現活體占位，再開隔離的整合世界檢查原生狐狸在同一盒內的像素與登出清理。
- 效果 probe 在 Scale 2 用黑白條紋背景驗證 Glass、漸變、Reveal、幀末模糊、同幀前景與裁剪，並比較 framebuffer、viewport、scissor、shader、blend 和 depth 的前後狀態。高對比模式驗證可讀文字和不透明底板。 [Forge 結果](../build/kernel-verification/client-forge-rich-text-rich-effects/success.txt) · [NeoForge 結果](../build/kernel-verification/client-neoforge-rich-text-rich-effects/success.txt)

| 圖像 | Forge | NeoForge |
| --- | --- | --- |
| 進階視圖 | [Glass](../build/kernel-verification/client-forge-rich-text-rich-effects/rich-text-advanced.png) · [Reveal](../build/kernel-verification/client-forge-rich-text-rich-effects/rich-text-advanced-reveal.png) | [Glass](../build/kernel-verification/client-neoforge-rich-text-rich-effects/rich-text-advanced.png) · [Reveal](../build/kernel-verification/client-neoforge-rich-text-rich-effects/rich-text-advanced-reveal.png) |
| 整合世界實體 | [狐狸](../build/kernel-verification/client-forge-rich-text-rich-effects/rich-text-world.png) | [狐狸](../build/kernel-verification/client-neoforge-rich-text-rich-effects/rich-text-world.png) |
| 條紋背景 probe | [受管效果](../build/kernel-verification/client-forge-rich-text-rich-effects/rich-text-effects.png) · [高對比](../build/kernel-verification/client-forge-rich-text-rich-effects/rich-text-effects-high-contrast.png) | [受管效果](../build/kernel-verification/client-neoforge-rich-text-rich-effects/rich-text-effects.png) · [高對比](../build/kernel-verification/client-neoforge-rich-text-rich-effects/rich-text-effects-high-contrast.png) |

首輪 Forge 煙測發現原生容器重設同一 child 時會先送失焦，取消剛取得的捕獲。修正保留同一視口的焦點，保留切換控件時的取消規則。NeoForge 完整測試有一次既有 `MusicServiceTest.refreshLoadsNewLocalLyricsForThePausedTrackAndBypassesCache` 的時間競態空值；歌詞代碼未改，後續整套重跑通過。

### 效能樣本

純 CPU 量測使用 Windows、Java 17、512 MiB 固定 heap、3,000 個 UTF-8 文字節點、290 px 可用寬和固定度量器。兩個版本都產生 1,996 行。各預熱 150 次並量測 400 次。捲動查詢在每次取樣跑 100 個 180 px 的視口，兩個實作各找到 2,088 行次。 [PE1 原始輸出](../build/reports/rich-text-pe24/phase1-layout.txt) · [PE2–4 原始輸出](../build/reports/rich-text-pe24/phase24-layout.txt)

| 路徑 | 均值 | P95 |
| --- | ---: | ---: |
| PE1 完整排版 | 4.520 ms | 5.050 ms |
| PE2–4 完整排版 | 4.802 ms | 5.408 ms |
| 遍歷全部行後計數／視口 | 2.109 µs | 2.826 µs |
| 二分可見行查詢／視口 | 0.048 µs | 0.102 µs |

```bash
/c/Progra~1/Java/jdk-17/bin/java -Xms512m -Xmx512m \
  -cp 'build/classes/java/kernelBenchmark;build/classes/java/kernel' \
  com.dishanhai.sh_better_ui.kernel.benchmark.RichTextBenchmark
```

原生 probe 使用 800×480、Scale 2、RTX 4060 Laptop、NVIDIA 610.88。每模式預熱 30 幀、量測 100 幀，從視覺綁定進入到前景 flush 取 CPU 時間和非同步 GPU timestamp。未就緒的 GPU 查詢不阻塞，完成樣本數見成功紀錄。幀間隔以連續兩次 Screen render 起點估算。全效果規劃預算為 112,896 加權像素 pass。實際 Draw Call、配置、顯存及髒區重繪仍未量測。 [Forge 資料](../build/kernel-verification/client-forge-rich-text-rich-effects/success.txt) · [NeoForge 資料](../build/kernel-verification/client-neoforge-rich-text-rich-effects/success.txt)

| Loader／效果 | CPU 均值／P95 | GPU 均值／P95 | 幀間隔均值／P95 |
| --- | ---: | ---: | ---: |
| Forge 基線 | 181.05／236.80 µs | 1.12／2.05 µs（n=75） | 16.765／17.312 ms |
| Forge 全效果 | 511.50／763.60 µs | 89.96／92.16 µs（n=99） | 16.790／17.219 ms |
| NeoForge 基線 | 180.85／238.90 µs | 1.10／2.05 µs（n=78） | 16.883／17.335 ms |
| NeoForge 全效果 | 508.95／656.90 µs | 96.68／96.26 µs（n=99） | 16.776／17.470 ms |

## PE5–6 雙端驗證

Windows 開發環境以 Java 21 啟動 Gradle。Forge 的原生遊戲由 Java 17 toolchain 啟動，NeoForge 使用 Java 21。

```bash
export JAVA_HOME=C:/Progra~1/Java/jdk-21.0.11 TEMP=C:/jtmp TMP=C:/jtmp
gradle-install/gradle-8.8/bin/gradle build --offline --no-daemon --console=plain
bash neoforge/gradlew -p neoforge build --offline --no-daemon --console=plain
gradle-install/gradle-8.8/bin/gradle -I gradle/kernel-smoke.init.gradle \
  -PkernelSmokeLabel=pe56 -PkernelSmokeRichText -PkernelSmokeRichEffects runClient --offline --no-daemon --console=plain
bash neoforge/gradlew -p neoforge -I ../gradle/kernel-smoke.init.gradle \
  -PkernelSmokeLabel=pe56 -PkernelSmokeRichText -PkernelSmokeRichEffects runClient --offline --no-daemon --console=plain
```

| Loader | kernelTest | test | JAR SHA-256 |
| --- | ---: | ---: | --- |
| Forge 1.20.1 | 231 | 906 | `1b1628d872c596024f591fd8ce94de98aac8555d53a0626b30e46ebbdb244968` |
| NeoForge 1.21.1 | 231 | 908 | `a8844d03949fe681be8c5ba1d9bbcbbba0b9f66ab652b63ef519bb16a30ee165` |

雙端完整構建均通過，測試零失敗及零跳過。JAR 核對純協定類別為 Java 17，包含原生橋、SPI、示例與素材，且無煙測類別、KubeJS 類別或重複打包的 Apache HttpClient。三種語言的 60 個富文字鍵與佔位符一致。

- PE5 的單元測試涵蓋 PNG／GIF／APNG／`.png.mcmeta`、256 組損壞媒體、作用域取消、工作壓力、世代失效、DNS 位址政策、擴充信封及 KubeJS 被動門面。原生煙測讀回至少兩種 GIF 幀顏色，確認行盒未移動，損壞 GIF 回報 `UNAVAILABLE` 且畫占位，資源包重載後同一盒改畫新素材，關頁後撤銷呈現及訂閱。
- PE6 的核心覆蓋 10,000 組隨機輸入及 40,000 次方向別解碼路徑，並驗證權限撤銷、補丁缺口、重複／亂序、逾時、請求風暴及舊連線拒絕。原生整合伺服器以實際 PLAY 封包協商，接受 65%，拒絕已預覽的 100% 並恢復 65%。同連線重開畫面仍為 65%，斷開並進入新世界後重新取得 40%。
- 兩端保留 PE1–4 像素、GUI Scale、選取、拖放及合成回歸。以 `gradle/live-surface-server-smoke.init.gradle` 執行 `runServer` 時，NeoForge 的隔離專用服在回環臨時埠達到 `Done` 後正常停止。類別載入紀錄包含兩個同步端點，客戶端富文字與媒體類別未載入。Forge 到達原生 EULA 門禁即停止，完整專用服驗收仍待進行。 [NeoForge 類別載入](../build/live-surface-verification/server-neoforge/class-loading.log) · [Forge 日誌](../build/live-surface-verification/server-forge/logs/latest.log)

| 證據 | Forge | NeoForge |
| --- | --- | --- |
| 原生結果 | [成功](../build/kernel-verification/client-forge-pe56-rich-text-rich-effects/success.txt) · [關閉](../build/kernel-verification/client-forge-pe56-rich-text-rich-effects/stopped.txt) | [成功](../build/kernel-verification/client-neoforge-pe56-rich-text-rich-effects/success.txt) · [關閉](../build/kernel-verification/client-neoforge-pe56-rich-text-rich-effects/stopped.txt) |
| 權威能量 | [接受 65%](../build/kernel-verification/client-forge-pe56-rich-text-rich-effects/rich-text-pipeline-accepted.png) · [拒絕 100%](../build/kernel-verification/client-forge-pe56-rich-text-rich-effects/rich-text-pipeline-denied.png) | [接受 65%](../build/kernel-verification/client-neoforge-pe56-rich-text-rich-effects/rich-text-pipeline-accepted.png) · [拒絕 100%](../build/kernel-verification/client-neoforge-pe56-rich-text-rich-effects/rich-text-pipeline-denied.png) |

32 欄位測試中，協定完整快照為 20,676 bytes，32 項替換加 32 項刪除的補丁為 24,844 bytes，單一長整數與最大長度 ID 的補丁為 183 bytes。這些是序列化大小。媒體解碼和上傳耗時、整合包延遲、實際頻寬、GPU／顯存及長時間分配尚無獨立量測。實際 HTTPS 端點、裝有 KubeJS 的腳本重載、獨立多人和跨維度留待後續驗收。

## PE7–8 雙端驗證

2026-11-11 在相同 Windows／RTX 4060 Laptop 開發環境，以原生整合世界驗證 `/shbetterricheditor`。編輯器與 Web 工作台屬於 Experimental。測試以各自的隔離遊戲目錄讀寫草稿和預設。

| 平台 | kernelTest | test | 完整構建 |
| --- | ---: | ---: | --- |
| Forge 1.20.1／47.4.16 | 231 | 911 | `build`、`reobfJar`、`releaseJar`、`verifyLibraryJar` 通過 |
| NeoForge 1.21.1／21.1.251 | 231 | 913 | `build`、`verifyLibraryJar` 通過 |

JUnit XML 的失敗、錯誤與跳過均為零。新增兩項固定文件／佈局 Golden，鎖定 v1 編碼順序、Unicode、惰性擴充、寬窄行盒、基線與半開命中。新增三項編輯器測試，檢查穩定 ID、屬性、撤銷及儲存基線、無效交易原子拒絕、Windows 原子檔案替換、UTF-8 與大小限制。

```bash
export JAVA_HOME=C:/Progra~1/Java/jdk-21.0.11 TEMP=C:/jtmp TMP=C:/jtmp
gradle-install/gradle-8.8/bin/gradle build --offline --no-daemon --console=plain
bash neoforge/gradlew -p neoforge build --offline --no-daemon --console=plain

gradle-install/gradle-8.8/bin/gradle -I gradle/kernel-smoke.init.gradle \
  -PkernelSmokeLabel=pe78 -PkernelSmokeRichEditor runClient --offline --no-daemon --console=plain
bash neoforge/gradlew -p neoforge -I ../gradle/kernel-smoke.init.gradle \
  -PkernelSmokeLabel=pe78 -PkernelSmokeRichEditor runClient --offline --no-daemon --console=plain
```

- 兩端由實際 Loader 的 `ClientCommandHandler` 執行命令，進入正式編輯器。GUI Scale 2／1、Unicode 屬性編輯、穩定 ID、復原重做、新增節點、可見圖片點選及幾何命中皆覆蓋。
- 煙測在實際 framebuffer 讀回媒體時間軸 0 ms 和 450 ms 的綠色／藍色 GIF 幀，以及佈局疊層的基線像素。預覽沿用同一份 UUK 呈現幾何。
- 儲存、屬性頁儲存後繼續編輯、JSON／Java 匯出、本機預設往返、關閉前取消／捨棄、全新 Screen 載入已存文件及畫面按鈕觸發資源重載均覆蓋。每端保留 `success.txt` 和 `stopped.txt`，離開後呈現、媒體和作用域均清理。
- 相同命令加上 `-PkernelSmokeRichEditorDisabled` 另開獨立程序，驗證 JVM 停用旗標會移除命令入口，直接開啟方法亦保持停用。
- 兩端另用 `-PkernelSmokeLabel=pe78 -PkernelSmokeRichText -PkernelSmokeRichEffects` 重跑 PE1–6 的圖像、互動、GL 狀態、世界實體、媒體和權威封包回歸。

| 證據 | Forge | NeoForge |
| --- | --- | --- |
| 編輯器 | [成功](../build/kernel-verification/client-forge-pe78-rich-editor/success.txt) · [關閉](../build/kernel-verification/client-forge-pe78-rich-editor/stopped.txt) | [成功](../build/kernel-verification/client-neoforge-pe78-rich-editor/success.txt) · [關閉](../build/kernel-verification/client-neoforge-pe78-rich-editor/stopped.txt) |
| 視覺 | [窄版](../build/kernel-verification/client-forge-pe78-rich-editor/rich-text-editor-narrow.png) · [疊層](../build/kernel-verification/client-forge-pe78-rich-editor/rich-text-editor-overlay.png) · [時間軸](../build/kernel-verification/client-forge-pe78-rich-editor/rich-text-editor-timeline.png) · [重開](../build/kernel-verification/client-forge-pe78-rich-editor/rich-text-editor-reopened.png) | [窄版](../build/kernel-verification/client-neoforge-pe78-rich-editor/rich-text-editor-narrow.png) · [疊層](../build/kernel-verification/client-neoforge-pe78-rich-editor/rich-text-editor-overlay.png) · [時間軸](../build/kernel-verification/client-neoforge-pe78-rich-editor/rich-text-editor-timeline.png) · [重開](../build/kernel-verification/client-neoforge-pe78-rich-editor/rich-text-editor-reopened.png) |
| 停用開關 | [成功](../build/kernel-verification/client-forge-pe78-rich-editor-disabled/success.txt) | [成功](../build/kernel-verification/client-neoforge-pe78-rich-editor-disabled/success.txt) |
| PE1–6 回歸 | [成功](../build/kernel-verification/client-forge-pe78-rich-text-rich-effects/success.txt) | [成功](../build/kernel-verification/client-neoforge-pe78-rich-text-rich-effects/success.txt) |

### Web、匯出與第三方 SDK

從根目錄執行以下命令。Web 建置包含 51 個既有材質項和新增工作台。

```bash
npm --prefix web run build
npm --prefix web test
npm --prefix web run test:rich-text-browser
npm --prefix web run test:browser
```

八項 Node 測試、Chromium 工作台操作和既有 WebGL 材質頁回歸均通過。工作台覆蓋真實本機檔案／貼上匯入、節點和樣式、鍵盤套用／取消、撤銷重做、插入刪除、JSON 下載、唯讀未知資料的原文與大整數保留、高對比，以及 390 px 窄視口。 [桌面](../build/rich-text/web/workbench-desktop.png) · [窄版](../build/rich-text/web/workbench-narrow.png) · [未知資料](../build/rich-text/web/workbench-protected.png)

真實編輯器匯出的 `ExportedRichText.java` 分別由 Java 17／21 對應發行 JAR 編譯並執行，結果與儲存的四節點文件相等。相同程序亦用兩端正式 Codec 讀取瀏覽器下載的五節點 v1 文件及 SDK 的四節點資源文件，重新編碼再解碼後相等。 [Forge 結果](../build/rich-text/exports/forge.txt) · [NeoForge 結果](../build/rich-text/exports/neoforge.txt) · [瀏覽器輸出](../build/rich-text/web/workbench-native-v1.json)

`examples/kernel-extension` 的兩個獨立工程均透過各自的程式庫 JAR 編譯、封裝並啟動。原生 Screen 驗證載入文件、鑽石 PNG、原生物品提示命中、自訂能量條 25% → 50% 的實際像素、固定行盒、返回焦點和 scope 清理。封裝檢查拒絕內嵌共享庫。 [SDK 指南與重跑指令](RICH_TEXT_SDK.md) · [Forge 結果](../examples/kernel-extension/forge/build/example-smoke/success.txt) · [NeoForge 結果](../examples/kernel-extension/neoforge/build/example-smoke/success.txt)

### 功能與發布邊界

正式 JAR 分別為 `build/libs/sh_better_ui-1.0.6.jar` 與 `neoforge/build/libs/sh_better_ui-neoforge-1.21.1-1.0.6.jar`。SHA-256 依序為 `40ea464fd045706ce9169f0848d9ab480c2b9cf92a0bc328038e3732cc053dec` 和 `749392dd5639ef6bc71c4916181200fad16e9b8de19bb2ca1b348b20f1554c84`。已核對原生編輯器、核心 API、媒體資源、Java 17 核心 bytecode 和 ZIP 完整性，測試／煙測及 KubeJS 類別的封裝邊界通過。三種語言的 98 個編輯器鍵與格式參數一致。

| 項目 | 現況及後續重現場景 |
| --- | --- |
| 編輯範圍 | 本機單草稿、單自訂預設及被動 v1 節點。可信 Java 控件和外部材質仍由宿主建立。 |
| 時間軸 | 圖片媒體播放／暫停、定位與循環長度。多軌動畫與關鍵幀作者工具仍待實作。 |
| 診斷 | CPU prepare／提交、佈局重建、行數、就緒素材和資源世代。GPU、driver VRAM、實際 draw call 需補獨立量測。 |
| Web 差異 | 瀏覽器字型、斷行和素材占位。原生遊戲物件、GPU 材質、字型及互動以 Minecraft 驗收。 |
| 環境矩陣 | 原生測試使用單一 NVIDIA GPU、GUI Scale 1／2、開發客戶端和整合世界。Scale 3／4、高 DPI、旁白、獨立多人、KubeJS、實際 HTTPS 和跨 GPU 仍沿 TODO 重現。 |

本輪首輪構建的四參數媒體方法呼叫失敗，已透過保留原有簽名修正並重跑雙端完整構建。SDK 首輪的固定 16 px 邏輯尺寸假設亦改為實際字型行盒契約。已知進階缺口在 [TODO](../TODO.md#rich-text--inline-content-engine) 和 [功能狀態](RICH_TEXT_RELEASE.md#功能狀態與啟用條件) 追蹤。

### PE8 CPU 與配置量基準

Windows 11、Intel Core i9-14900HX、Temurin 17.0.18，固定 G1、512 MiB heap 和 4 個可用處理器。兩端分開啟動新的 Java 17 程序，每項預熱 150 次、量測 400 次。時間為單執行緒工作的牆鐘時間，配置量來自 `ThreadMXBean`。佈局量測包含完整文件重排，視圖穩定重繪會沿用其已準備的佈局。 [Forge 原始輸出](../build/reports/rich-text-pe78/forge.txt) · [NeoForge 原始輸出](../build/reports/rich-text-pe78/neoforge.txt)

```bash
gradle-install/gradle-8.8/bin/gradle richTextBenchmark --offline --no-daemon --console=plain
bash neoforge/gradlew -p neoforge richTextBenchmark --offline --no-daemon --console=plain
```

| 工作量／單次操作 | Forge 均值／P95 µs | NeoForge 均值／P95 µs | 平均配置量 bytes（Forge／NeoForge） |
| --- | ---: | ---: | ---: |
| 3,000 文字節點完整排版，1,996 行 | 4,466.523／5,064.300 | 4,198.590／4,661.300 | 18,234,992／15,385,552 |
| 4,000 節點含 1,000 圖片盒，1,999 行 | 4,944.265／5,593.400 | 4,510.438／5,046.300 | 18,590,056／15,740,616 |
| 二分可見行查詢 | 0.061／0.114 | 0.081／0.180 | 24／24 |
| 32 欄位快照編解碼，20,676 bytes | 88.260／108.600 | 82.758／100.000 | 264,723.160／264,723.160 |
| 128 項連續補丁流的單包編解碼，183 bytes | 1.424／1.852 | 1.466／1.980 | 3,745.718／3,745.175 |

可見行查詢每個樣本有 100 個視口，並驗證與全行遍歷都找到 2,088 行次。補丁流每個樣本處理 128 包，記錄每包時間和配置。圖片盒基準測量純資料佈局。實際媒體解碼、GPU、網路延遲及長時間 heap／顯存壓力仍按環境矩陣補測。

## 後續 Golden 場景與門禁

| Phase | 預定 Golden／壓力場景 | 門禁 |
| --- | --- | --- |
| 2 | 中英文、CJK、emoji、組合符號、RTL、字型缺失、翻譯鍵切換、IME | 穩定 NodeId、序列化往返、選擇映射及屬性測試，並確認啟用的字型與文字後端 |
| 3 | 16×16、64×64、128×128、非方形及透明圖片，以及動圖、物品、按鈕、Tooltip | 遮擋、裁剪、焦點、旁白、拖放取消、巢狀事件及資源釋放 |
| 4 | 字級 8／12／16／24、不同視窗、高複雜背景、清晰前景、玻璃與遮罩、數千節點 | 真實 GPU 的同幀幾何、讀寫順序與 GL 狀態恢復，並保留視覺容差與量測記錄 |
| 5 | 損壞圖片、異步占位、熱重載、快速反覆開關、長文檔及快取壓力 | 取消與釋放、壓力與 fuzz 測試、協議與媒體限制，以及可選擴充缺席時的行為 |
| 6 | 高頻綁定、補丁風暴、未授權操作、亂序、重複、重連及未知能力 | 服務端權威驗證、排序與冪等、斷線恢復，並驗證執行緒邊界 |
| 7 | Minecraft 編輯、撤銷、預覽、儲存與重開，對照 Web 輸出 | 編輯器在真實客戶端可重現操作，匯出範例可編譯，原生與 Web 差異有對照 |
| 8 | 原版與第三方 UI、雙平台公開 API、不同 GPU、整合場景與長時間運行 | 核心門禁全數通過，兼容、效能與實驗功能狀態均可核對 |
