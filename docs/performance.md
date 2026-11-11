<!-- 作者：山海／dishanhai -->
# Unified UI Kernel 效能基準

## 重跑方式

兩個 Gradle 工程以獨立的 Java 17 程序執行同一份核心測試，並使用各自的預設平台選擇器。基準 source set 只依賴核心的 class，維持與發行包隔離。從專案根目錄使用 Git Bash。每次重跑可用 `-PkernelBenchmarkOutput` 指定輸出目錄。

```bash
./gradle-install/gradle-8.8/bin/gradle kernelBenchmark --offline --no-daemon
./neoforge/gradlew -p neoforge kernelBenchmark --offline --no-daemon
```

`kernelBenchmarkWarmup`、`kernelBenchmarkSamples` 和 `kernelBenchmarkScenario` 可調整預熱、樣本數及場景，`kernelBenchmarkPlatform` 可指定 `FORGE_1_20_1` 或 `NEOFORGE_1_21_1`。預設每場各取 2,000 次預熱與樣本。每端寫出 `build/reports/kernel-benchmark/{raw.csv,summary.csv,work.csv,environment.properties}`。先檢查工作量一致，再比較數值。場景涵蓋 100／1,000／10,000 元素的靜止與局部失效、ANIMATION 階段 32 個回呼、每批 64 次的指標路由、256 項配置／狀態提交、Screen scope 清理和資源重載 scope 租約釋放。核心時鐘每步推進 16,666,667 ns。模擬時鐘固定了回呼內的時間，因此核心結果只比較工作量與單執行緒開銷。真實的時間預算與原生路徑由遊戲端量測。

對照留存的 Phase 8 JAR 時，先用 `kernelBenchmarkClasses` 編譯相同的執行器。設定 `JDK17` 和 `BASELINE_JAR` 為對應路徑，下例的 `;` 是 Windows JVM 的 classpath 分隔符。將 `--platform` 換成 NeoForge 的平台選擇器，即可以 NeoForge JAR 重跑。

```bash
./gradle-install/gradle-8.8/bin/gradle kernelBenchmarkClasses --offline --no-daemon
"$JDK17/bin/java" -Xms512m -Xmx512m -XX:+UseG1GC -XX:ActiveProcessorCount=4 \
  -cp "build/classes/java/kernelBenchmark;$BASELINE_JAR" \
  com.dishanhai.sh_better_ui.kernel.internal.KernelBenchmark \
  --platform FORGE_1_20_1 --warmup 4000 --samples 3000 --output build/kernel-benchmark/forge-baseline-1
```

原生基準在隔離開發客戶端執行，使用遊戲各自的 Java 版本及同一份場景。執行時保持窗口可見，使用同一電源方案，依序跑各端。停用其他重載、圖形擷取和並行建置。可用 `-PkernelNativeScenario=dirty-10000,pointer-1024` 只跑指定場景。

```bash
./gradle-install/gradle-8.8/bin/gradle -I gradle/kernel-client-benchmark.init.gradle runClient -PkernelNativeLabel=sample --offline --no-daemon
./neoforge/gradlew -p neoforge -I ../gradle/kernel-client-benchmark.init.gradle runClient -PkernelNativeLabel=sample --offline --no-daemon
python -B src/kernelBenchmark/report.py build/kernel-benchmark/native-summary.csv build/kernel-benchmark/native-forge-sample
```

`kernelNativeWarmup`、`kernelNativeSamples` 和 `kernelNativeReloadSamples` 預設為 120、300、20。原生報告位於 `build/kernel-benchmark/native-<platform>-<label>/`，包含每幀 `frames.csv`、每次重載 `reload.csv`、環境與驗證標記。若需要合併同一平台、同一版本的多個 fresh JVM，將它們的原始資料目錄依序傳給 `report.py`。平均值取算術平均，P95 與 P99 採 nearest rank，`ceil(n × p)`。原始欄位以 ns 和 B 表示。計數器不可用時原始資料記為 `-1`，彙總為 `NA`。

## 取樣範圍

- 核心 CSV 的每筆樣本對應一個場景操作，單位和回呼數在 `work.csv`。分配以 `ThreadMXBean` 讀取取樣執行緒的 JVM heap 分配差。背景執行緒、JNI 與 GPU 記憶體各自位於取樣範圍外。 [JDK 17 ThreadMXBean](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.management/com/sun/management/ThreadMXBean.html#getCurrentThreadAllocatedBytes())
- 遊戲端 `work_*` 量測兩幀間的操作，如局部失效、16 個種子重放指標事件、1,000 次主題讀取、64 項配置或重掛原生 Screen。指標場景固定視窗焦點前提，從核心原生路由入口重放。佈局場景透過真實服務綁定節點，量測範例中的回呼計數。字型量測與第三方 widget 內容成本取決於消費端實作。
- `frame_wall_ns` 是原生橋幀首到幀尾的經過時間，包含其中的 GUI 提交與核心階段。這段區間在原生幀率等待與 swap 之前。Windows 的 `frame_cpu_ns` 可能量化到 15.625 ms，保留該原始欄位供判讀，短幀比較以 `frame_wall_ns` 為準。
- `pass_wall_ns` 量測 draw callback 內的填色與 flush。GPU 使用兩個 OpenGL timestamp 界定同一批命令，等回傳可讀後才收集。這是 GPU 指令區間，數值可能包含驅動排程與命令提交空檔。1000 方塊場景使用既有原生 GUI API。兩端的原生填色和 flush 行為各有其版本實作。 [Khronos ARB_timer_query](https://registry.khronos.org/OpenGL/extensions/ARB/ARB_timer_query.txt)
- 重載樣本含 Minecraft 資源管線與 loading overlay 的完成通知門檻。另記從呼叫到中央資源世代發布的 `kernel_applied_ns`。已發布後，以同一張庫內圖做一次快取 miss 和一次 hit 並核對新世代。重載分配包含這段間隔在客戶端執行緒的分配，沒有統計工作執行緒的分配。
- 核心的回呼量、任務佇列與 deferred 計數，遊戲端的佈局回呼、實際推進的動畫軌、指標事件、主題讀取、繪製矩形、資源世代與命中數皆寫入原始資料。測量模式本身有固定成本，前後均使用同一份量測程式。

## 可選執行期統計

`UIEngine.metrics()` 提供引擎級的 `RuntimeMetrics`。預設關閉；停用時執行路徑只檢查開關，不讀時鐘、不讀分配計數器，也不為每次操作建立取樣物件。以持有 `ACCESS_INTERNAL_DIAGNOSTICS` 的模組 context 呼叫 `configure(new RuntimeMetrics.Configuration(true, 16, 1024, true))`，即每類操作每 16 次取一次樣本，保留最近 1,024 筆延遲。開關、採樣間隔或窗口變更會開始新的統計世代，`reset()` 也會清空；舊世代尚未結束的樣本不寫入新世代。

實際接線包括中央佈局服務的每次 measure／arrange、動畫服務的每條活動軌道取樣、中央事件總線的每次監聽回呼、渲染服務的每個 draw pass，以及資源服務建立的 `AlignmentAssets` 紋理與字形快取命中／缺失。快取操作只計命中／缺失，不宣稱量到 GPU 或磁碟延遲。既有未經這些中央服務呼叫的第三方繪製與動畫不在統計範圍內。

`Snapshot.measurements()` 分類回報操作數、樣本數、失敗數、累計與最大樣本耗時、P50／P95／P99、有效分配樣本數及分配總量。延遲以 ns、分配以 B 表示。百分位使用窗口內樣本的 nearest rank；累計耗時與分配是這個統計世代的全部有效樣本，不以採樣間隔外推。快取命中／缺失為啟用期間的實際查詢計數。快照只在查詢時複製和排序有界窗口，背景可讀，不會執行模組回呼。

分配計數透過 JVM 可選的 ThreadMXBean 擴充取得；不支援、未啟用或讀取失敗時保留不可用狀態，`allocationSamples` 為 0、`allocatedBytes` 為 -1。不自動啟用全 JVM 的分配監控。分配只涵蓋同執行緒的採樣區間，包含取樣本身的固定成本，巢狀回呼採 inclusive 統計，因此不可把各分類直接相加當成整幀分配。背景執行緒、原生記憶體和 GPU 記憶體仍須以 JFR 或原生工具另行驗證。這些指標不取代 `SchedulerSnapshot` 的排程與佇列計數。

核心基準執行器接受 JVM 屬性 `-Dsh_better_ui.benchmark.runtimeSampleEvery=16`（0 表示停用），為每個場景額外輸出 `runtime-<scenario>.csv`。預熱後經停用／重新啟用清空統計，再記錄正式樣本；一般 `raw.csv` 仍量測含統計成本的整體操作。GraalVM 21 的事件路由實跑以 1,000 次預熱、2,000 次正式操作、每次 64 個事件重放，啟用期間均記錄 128,000 次回呼：每 16 次採樣得到 8,000 筆，全量採樣得到 128,000 筆；停用組計數與樣本均為 0，三組工作量檢查皆通過。原始資料位於 `build/reports/kernel-runtime-{0,16,1}/`。這次只證明核心事件路徑與開關／世代重設，不代表已完成原生佈局、動畫或繪製驗收。

本輪建置與基準執行器改用 GraalVM 21，核心仍以 `--release 17` 編譯。下方歷史基線保留原本的 Java 17 環境識別，兩種 JVM 的數字不可直接視為同環境前後比較。

## 基線與調整

基線為 Phase 8 工作樹快照。核心使用留存的 1.0.5 兩端 JAR，遊戲端從同一份快照啟動。JFR 錄製只跑有優先級的指標路由場景。核心正式比較使用三個全新 JVM，每個場景每個 JVM 預熱 4,000 次，採樣 3,000 次。JFR 單獨執行，以隔離 profiler 的開銷。

2026-11-10 的比較使用 Windows 11、Intel Core i9-14900HX（32 邏輯執行緒）、GeForce RTX 4060 Laptop GPU 和 NVIDIA 610.88。核心使用 Temurin 17.0.18+8、512 MiB 固定堆、G1、`ActiveProcessorCount=4`、種子 20261110。核心和遊戲調度使用相同預設計數預算，每相位的時間預算為 1 ms。遊戲分別使用 Forge／Temurin 17.0.18+8 和 NeoForge／Oracle JDK 21.0.11+9，G1 堆 1–2 GiB，800 × 480，GUI Scale 2，關閉 VSync，選單受 Minecraft 幀率上限約束。原生種子為 19316813，每場預熱 90 幀並取 240 幀。資源重載單獨預熱 3 次、量測 15 次，其 nearest-rank P95 和 P99 都會取到最大樣本。

留存的兩份 Phase 8 正式 JAR SHA-256 分別為 Forge `445b2ec7c0f1294d3f1f2f38c39331264147bedff5fb49e44efed4897b34d148`、NeoForge `8c3cbc194cc292abf8ee1703a38781400134f8a89512912465a6199ac08bcb86`。核心與原生原始結果、環境、JFR 和日誌位於 `build/kernel-benchmark/`。以原生 fixture 重跑同一取樣規模時傳入 `-PkernelNativeWarmup=90 -PkernelNativeSamples=240 -PkernelNativeReloadSamples=15`。

`jdk.ObjectAllocationSample` 的抽樣堆疊將部分指標路由分配指向 `ScopedSubscriptions.forKey()` 和 `EventBus.deliver()` 的清單複製及排序。調整將每個訂閱索引的不可變快照保留到該索引下一次增刪時。EventBus 在發布快照時按優先級排序，同級沿用登記順序。回呼中的重新登記影響後續查詢與巢狀派送，當前派送保留其既有序列並逐一檢查活性。核心重入測試和兩端原生路由共同驗證這條語義。

第一輪使用快照的 iterator，單訂閱路由的平均分配仍增加約 7%。改以索引走訪後，兩端同場景分配都下降。下表每格延遲為平均／P95／P99，單位 µs。核心每批重放 64 次，原生每批重放 16 次。完整場景包含所有延遲與分配量的平均、P95 和 P99。 [對照 CSV](kernel-performance-results.csv)

| 平台 | 場景 | 優化前 µs | 優化後 µs | 平均 B／批，前 → 後 |
| --- | --- | ---: | ---: | ---: |
| Forge | 核心單訂閱 | 18.14／39.90／47.50 | 11.78／15.20／19.10 | 39,505 → 23,382 |
| Forge | 核心多訂閱與優先級 | 32.34／36.50／63.40 | 13.62／14.70／19.90 | 62,976 → 22,016 |
| NeoForge | 核心單訂閱 | 13.81／15.90／21.20 | 12.89／16.40／23.60 | 39,862 → 24,064 |
| NeoForge | 核心多訂閱與優先級 | 31.75／34.50／52.70 | 14.03／15.60／21.60 | 62,976 → 22,016 |
| Forge | 原生 1,024 目標 | 92.95／109.90／120.50 | 90.40／110.30／159.90 | 10,793 → 6,873 |
| NeoForge | 原生 1,024 目標 | 107.27／138.90／194.70 | 92.70／113.40／147.80 | 9,769 → 6,875 |

核心多訂閱場景分配降低約 65%，兩端每批皆送達 512 次監聽回呼。原生每幀都送達 16 次重放指標事件，Forge、NeoForge 平均分配分別降低約 36% 和 30%。Forge 原生指標的 P99 上升，NeoForge 核心單訂閱的 P95 和 P99 小幅上升。反覆建立與關閉 8 個核心元件的場景每批平均多配置 218 B／288 B，對應每個新訂閱地址的快照容器成本。CPU/GPU 繪製和重載的波動留在原始資料和 CSV，這次調整針對的是訂閱清單的重複配置。

兩端靜止場景的佈局回呼為 0，局部失效場景每幀各量測、排列 1 個節點。動畫每幀有 1,000 個值更新，重載後第一、第二次紋理查詢分別出現 1 次 miss、1 次 hit。全部正式原生測試包含 12 組各 240 幀和 15 次重載，前後合計 11,520 幀與 60 次重載。效能資料限於這台機器、這些隔離開發場景與取樣區間。 [四組原生資料與日誌](../build/kernel-benchmark/)

### Java 21 指標邊界複核

透過 GraalVM 21 的 JShell 直接呼叫指標聚合器，已確認停用時不累計操作；每兩次取樣的五次 EVENT 操作產生三筆樣本；失敗數、cache hit／miss 分別累計；未啟用分配時回傳 -1；變更配置後關閉的舊世代樣本不污染新世代；同一 sample 重複 close 不重複計數。日誌為 `build/reports/runtime-metrics-boundaries-java21.log`。此處是聚合器實際呼叫，不是原生 layout／render 接線、效能門檻或讀取權限的驗收，也沒有載入 Minecraft。

### Java 21 待交付快照的原生複測狀態

本輪固定使用 `C:\GRAALVM\graalvm-jdk-21.0.6+8.1`，既有原生基準的執行期檢查已同步要求雙端 Java 21；工作量與行為斷言未移除。Forge 及 NeoForge 均已實際啟動至圖形資源載入；完整場景曾進入 `idle-100`（Forge 亦到達 `idle-1000`），隨後客戶端提前結束，未產生 `success.txt`／`stopped.txt` 或完整 CSV。另一次 Forge 僅重載場景的診斷也在初始載入期間提前結束，尚未完成有效重載樣本。因此這些執行**不算通過**，不可替代上述歷史結果、正式生產實例、實體 F3+T 或長時間壓測。

失敗現場分別保留於 `build/kernel-benchmark/native-forge-java21-resources`、`native-forge-java21-reload`、`native-forge-java21-reload-diagnostic` 及 `native-neoforge-java21-resources`；Forge 詳細啟動日誌為 `build/reports/kernel-native-forge-diagnostic.log`。目前可見日誌沒有指出提前退出的原因，工作區也未找到本輪 JVM `hs_err_pid*.log`；不能據此判定為應用邏輯、JVM 或驅動問題，仍須定位並重新完成驗證。
