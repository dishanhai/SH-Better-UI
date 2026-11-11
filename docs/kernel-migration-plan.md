<!-- 作者：山海／dishanhai -->
# Unified UI Kernel 分階段實施計畫

**目標**　以 SH-Better-UI 現有功能作為內建服務，讓第三方透過單一入口查詢能力、使用服務，並在已授權的範圍內協調工作。

**架構**　共用 Java 17 內核和契約測試。現有兩套 Gradle 建置及原生 UI 入口各自接入，服務逐一轉交所有權。見 [架構契約](kernel-architecture.md)。

**技術**　Forge 1.20.1／Java 17、NeoForge 1.21.1／Java 21、Gradle 8.8／9.2.1、JUnit 5.10.2。

## Phase 0　審計與基線

- [x] 記錄工作樹、已提交 HEAD、兩端解析版本、入口、執行緒和資源生命週期。以 [工程審計](kernel-audit.md) 區分工作樹的碰撞和情境主題、現行發行 ABI，以及已驗證的結果。
- [x] 執行工作樹建置、隔離 HEAD 建置及發行包驗證，將兩端編譯阻塞、既有 JUnit 競態和 Python 抽取契約的三項失敗分列。
- [x] 擷取已加入 `TransitionSpec` 的更新工作樹，重跑兩端完整建置及 JAR 檢查。Forge 780、NeoForge 782 項 JUnit 均通過。
- [x] Python 抽取契約分別依消費方的 Gradle 版本展開、兩個 Loader 的 `PlatformUiApi.LdLib` 巢狀選用適配邊界，以及山海在 `ac56631` 提交的 shader 變更複核，7 項全數通過。
- [x] 重跑工作樹的完整建置。歌詞回歸改為等待同一份「新 track + LOADING」快照，避免把工作執行緒中斷誤當作狀態發布。Forge 800 項與 NeoForge 802 項一般測試通過。

Phase 1 至 Phase 6 的純 Java 契約可獨立驗證。合併到既有客戶端與宣布任何 Loader 回歸通過前，必須有可建置且可比較的工作樹基線。

## Phase 1　公共契約

**位置**　新增 `src/kernel/java/com/dishanhai/sh_better_ui/kernel/api/`、`src/kernelTest/java/com/dishanhai/sh_better_ui/kernel/` 和 `gradle/kernel.gradle`。在兩端 `build.gradle` 各接同一源集，維持原有發行邊界。

- [x] 驗證 `UnifiedUI` 未啟動的可用性與錯誤、`UiId` 格式、`ServiceKey<T>` 型別與版本、描述符集合防禦性複製。Java 17 外部消費端可以編譯正確的服務查詢，型別誤用會被 javac 拒絕。
- [x] 新增 `UnifiedUI`、`UIEngine`、`UIService`、`UiId`、`ServiceKey`、`ServiceDescriptor`、`ModuleDescriptor`、`CapabilityKey` 及相關值型別。平台安裝留到 Phase 8，公開版本在整合驗證後固定。
- [x] 兩端 `kernelTest` 都以 JDK 17 執行。`jdeps` 只列出 `java.base`，所有內核 class 都是 major 61。以同源碼建置的前後 JAR 逐位元比較全部既有 class，涵蓋 public／protected ABI 和 record 屬性。

## Phase 2　註冊、依賴與能力解析

**位置**　`kernel/api/` 的 `APIRegistry`、`ServiceRegistrar`、`RegistrationHandle`、`CapabilityResolver`、`UIModule`。在 `kernel/internal/` 分別實作 `ModuleDiscovery`、`DependencyGraph`、註冊表及解析器。

- [x] 使用顯式候選集驗證登記來源、跨 namespace、raw type、不同契約版本、四種衝突策略、舊句柄失效與凍結後讀取。
- [x] 先驗證描述、選擇提供者、建立依賴圖，才交付服務快照。測試輸入重排、同級仲裁、完整循環路徑、必要缺失、選用降級、版本區間及凍結前替換回滾。
- [x] Phase 3 以 latch 同步並行讀者和停用提交，驗證每次讀取只能觀察完整舊版或新版。內核關閉會撤回既有查詢視圖。服務實例的操作與作用域檢查在各附屬 API 接入時驗證。

## Phase 3　生命週期

**位置**　`kernel/api/` 的 `UILifecycle`、`ModuleContext`、`ScopeHandle`。在 `kernel/internal/` 建立狀態機、範圍樹和 `CleanupManager`。

- [x] 實作啟動、關閉和失敗轉移，READY／RUNNING 只發布完整引擎。驗證重複啟動拒絕、停止冪等、替換宿主及同名範圍重新掛載的世代。
- [x] 建立引擎、模組、會話、頁面、樹、組件與重載的資源邊界。驗證反序關閉、物件去重、子範圍先關、失敗後繼續釋放、依賴停用、能力降級及過期租約。
- [x] `UiApiContextTest`、`UiScreenTest`、`KubeJsUiApiTest` 隨雙端既有 JUnit 套件通過。新掛載由內核範圍釋放，原生 Screen 接線由 Phase 8 驗收。

### Phase 1–3 驗證紀錄（2026-11-10）

| 階段 | Forge 1.20.1 | NeoForge 1.21.1 |
| --- | ---: | ---: |
| Phase 1 `kernelTest` | 35 通過 | 35 通過 |
| Phase 2 `kernelTest` | 45 通過 | 45 通過 |
| Phase 3 最終 `kernelTest` | 55 通過 | 55 通過 |
| 最新工作樹快照 `test` | 794 通過 | 796 通過 |
| 最新工作樹快照 `build`／`verifyLibraryJar` | 通過，含 Forge 重映射 | 通過 |

以獨立目錄保留改動前的源碼，僅疊加 Phase 1–3 變更重建。Forge 正式、dev 與 raw JAR 各 887 個既有 class 逐位元相同。NeoForge 正式 JAR 的 866 個既有 class 逐位元相同。各二進位包包含 77 個 Java 17 內核 class，每個名稱只出現一次。兩端 sources JAR 各包含 42 個內核來源。外部範例分別以兩端正式 JAR 為唯一依賴，在 Java 17 編譯及執行成功。

擷取舊基線時，主題測試曾因資源類尚未齊備而中止編譯。上述一般測試與完整建置使用補齊後的另一份工作樹快照。Python 抽取契約仍重現 Phase 0 的三項失敗，涉及選用 LDLib 邊界、消費方版本範圍和 `program/shop_gauss_blur.json` 的基準雜湊。Minecraft 原生啟動與畫面驗收留在 Phase 8。

## Phase 4　調度、髒標記與工作仲裁

**位置**　`kernel/api/` 的 `UIScheduler`、`UIPhase`、`UITask`、`TaskHandle`、`UIDirtyFlag`，以及 `kernel/internal/` 的 `CentralScheduler`、`SchedulerBudget` 和 `DirtyWork`。

- [x] 以注入時鐘及 executor 驗證階段順序、依賴、同級 FIFO、相位重入、延遲、逾時、低優先級前進及過載。按幀、相位及模組限制回呼數。
- [x] 工作綁定提交模組和目標範圍世代。驗證 Screen 關閉、替換、跨執行緒提交，以及背景結果與停模組的競態。
- [x] 失效工作集支援父子傳遞、佈局邊界、同幀合併及重算上限。實際佈局與幾何消費隨附屬服務接入。

## Phase 5　配置與狀態

**位置**　`kernel/api/` 的 `UIConfigCenter`、`ConfigKey`、`ConfigScope`、`ConfigTransaction`、`UIStateStore`、`StateKey` 和快照。Loader 的 TOML 寫入留在各自的 `ShBetterUi.java`。

- [x] `ConfigCenter` 驗證七層覆寫、型別與範圍、schema 遷移、世代、原子提交、併行快照與關閉清理。
- [x] `StateStore` 原子發布普通與派生狀態，按鍵和範圍合併通知。驗證多鍵失敗、過期基線、派生循環、監聽回寫循環與 Screen 重掛載。
- [x] 將既有配置快照映射成單一 User Global 層。一般測試與雙端設定煙測驗證既有 TOML、主題草稿與覆寫。
  - 客戶端橋接已將完整 `ClientUiSettings` 快照透過 `UIApis.CLIENT_SETTINGS` 發布。原 Loader 繼續處理 TOML，Kernel 在客戶端幀首接收成功載入或保存的快照。

## Phase 6　權限、資源所有權與命令

**位置**　`kernel/api/` 的 `UIPermissionManager`、`UICommandBus`、`UITransaction`、`OwnershipLease`、`UIEventBus`，以及 `kernel/internal/` 的 `ModuleAccess`、`ConflictResolver`、`ResourceOwnership`、`CommandBus` 和 `EventBus`。

- [x] 以宿主簽發的模組身分驗證登記、配置、調度、渲染工作、租約及輸入路由。測跨範圍、父範圍授權、偽造句柄、優先級與撤銷。撤銷會取消不再授權的排程和租約。
- [x] 測每個 Screen 的焦點、原生指標、動畫屬性重定向、有界排隊、期限與舊租約失效。佈局寫入槽按節點仲裁，狀態版本衝突由交易處理。
- [x] 命令在單次提交中驗證狀態、失效與後續工作容量。副作用各有回執及逾時。事件驗證型別、優先級、capture／target／bubble、傳播旗標、遞迴上限與範圍退訂。

### Phase 4–6 驗證紀錄（2026-11-10）

| 獨立驗證 | Forge 1.20.1 | NeoForge 1.21.1 |
| --- | ---: | ---: |
| Phase 4 `kernelTest` | 64 通過 | 64 通過 |
| Phase 5 `kernelTest` | 72 通過 | 72 通過 |
| Phase 6 最終 `kernelTest` | 89 通過 | 89 通過 |
| Phase 3 既有源碼搭配最終內核 `test` | 794 通過 | 796 通過 |
| 整合工作樹快照 `test` | 799 通過 | 801 通過 |
| 兩份快照的 `build`／`verifyLibraryJar` | 通過，含重映射 | 通過 |

兩端以各自 Gradle 啟動器和 JDK 17 逐階段執行獨立契約，皆為 0 失敗、0 錯誤、0 跳過。原生輸入、GPU 階段與宿主幾何的接線仍依後續平台計畫驗收。

以同一份 Phase 3 既有源碼建置，Forge 正式、dev 與 raw JAR 各 911 個既有 class 逐位元相同，NeoForge 正式 JAR 的 890 個既有 class 逐位元相同。既有內核公開介面 `UIEngine` 和 `OwnershipLease` 以 default 方法擴充。用 Phase 3 JAR 編譯的實作者在兩端新版正式 JAR 均能以 Java 17 載入與執行。每個發行包的 186 個內核 class 都是 major 61，內核經 `jdeps` 僅依賴 `java.base`。

## Phase 7　逐項接入附屬服務

平台中立的契約留在 `src/kernel/java`。引用 SH-Better-UI 既有型別的服務介面、`UIApis` 和 `Client*` 適配器放在兩端共享的主源集，由 `ClientUiKernel` 登記。舊公開方法和型別沿用。下表列出整合後的能力及各項首次接入時的回歸數，測試組之間可能重疊。後續調整另由 Phase 8 的整合回歸和遊戲煙測覆蓋。

| 順序與服務 | 復用位置與接入 | 分項回歸，兩端各通過 |
| --- | --- | ---: |
| 1 Theme | 共用 `ThemeManager`、`ThemeRegistry` 和 `ThemeScope`。保留既有 TOML、切換和轉場，提供按 UI 樹節點繼承和釋放的 token 覆寫 | 24 |
| 2 Animation | 沿用 `MotionApi`、`InterruptibleAnimation`、`Spring` 和 `UiTimeline`。以幀時鐘和屬性租約綁定動畫，更新活動軌道 | 48 |
| 3 Layout | 復用 `LayoutApi`、`UiLayout` 和 `AlignmentLayout`。將量測、排列、樣式失效和佈局寫入綁到節點範圍 | 25 |
| 4 Input | 復用 `InteractionApi`、`UiPointerTracker` 和 `UiPointerCapture`。活動目標依圖層和命中判斷選出，原生攔截另受授權約束 | 12 |
| 5 Focus | 復用 `UiScreen`、`UiOverlayHost` 和 `UiLocalModal`。租約對應原生 widget 焦點，模態維持優先，釋放時核對恢復目標 | 44 |
| 6 Collision | 復用 `CollisionWorld` 和 `UiCollisionLayout`。依呈現位置更新命中索引，支援擴大命中範圍，解除幾何綁定時移除輸入目標 | 19 |
| 7 Rendering | 保留 `GraphicsApi`、`BlurApi` 與既有模糊和延遲 tooltip 順序。新增單次渲染任務及有效期內的裁剪和變換 | 10 |
| 8 Resources | 復用 Loader 重載註冊和 `AlignmentAssets`。依重載世代失效快取，關閉範圍時退訂 | 22 |
| 9 Accessibility | 復用 `UiPreferences` 和原生旁白，提供描述、角色與可用能力查詢。系統螢幕閱讀器能力回報 `UNAVAILABLE` | 18 |

分項回歸涵蓋動畫與佈局、輸入與焦點或碰撞、重載與快取失效。主題分支與動畫租約隨節點釋放，原生捕獲在新舊入口共用槽位。

### Phase 7 獨立驗證（2026-11-10）

| 驗證 | Forge 1.20.1 | NeoForge 1.21.1 |
| --- | ---: | ---: |
| `kernelTest` | 89 通過 | 89 通過 |
| `test` | 808 通過 | 810 通過 |
| `build`／`verifyLibraryJar` | 通過，含重映射 | 通過 |

使用 `C:/jtmp/shbui-kernel7.syP6TvTX` 的獨立快照保留這一階段的完整結果。遊戲原生回呼的結果列於 Phase 8。

## Phase 8　雙端平台接線

**位置**　兩端的 `UiClient`、各自的 `ForgePlatformBridge`／`NeoForgePlatformBridge`、共用的 `NativeClientBridge` 和 `ClientUiKernel`，以及 `sh_better_ui.kernel.mixins.json` 內的客戶端 mixin。

- [x] 客戶端 setup 從 Loader 的 mod 檔驗證並發現模組。接上登入／登出、原生 Screen 掛載、GUI Scale、視窗焦點、鍵鼠、重載及關閉。
- [x] 原生幀首推進 CPU 階段。Screen 或 HUD 後置事件交付 `RENDER` 上下文，幀尾執行 `CLEANUP`。世界切換期間的重入渲染由外層幀協調。
- [x] 雙端執行契約、一般測試和發行包驗證。隔離工作樹比較舊版 ABI，並以舊版 JAR 編譯的消費程式執行新版。
- [x] 雙端各跑無選用整合及載入 LDLib 的隔離 `runClient` 煙測。NeoForge 專用服到達 `Done` 後停止。
- [ ] Forge 專用服啟動到達 EULA 確認，完整伺服器啟動待驗。實體 F3+T 快捷鍵、正式 JAR 裝入客戶端及長時間資源壓力測試也待驗。

### Phase 8 建置與 ABI（2026-11-10）

| 驗證 | Forge 1.20.1 | NeoForge 1.21.1 |
| --- | ---: | ---: |
| 僅疊加本階段的 ABI 快照 `kernelTest` | 94 通過 | 94 通過 |
| ABI 快照 `test` | 812 通過 | 814 通過 |
| 整合工作樹 1.0.5 `kernelTest` | 94 通過 | 94 通過 |
| 整合工作樹 1.0.5 `test` | 814 通過 | 816 通過 |
| 兩份快照的 `build`／`verifyLibraryJar` | 通過，含重映射 | 通過 |
| 既有公開型別成員 ABI | 672 個型別，0 處破壞 | 668 個型別，0 處破壞 |
| ABI 快照既有非內核 class 逐位元相同 | 正式、dev、raw 各 895／912 | 正式包 874／891 |

ABI 快照位於 `C:/jtmp/shbui-kernel78-abi.OtMK3SqN`。兩端各 196 個核心 class 為 major 61，`jdeps` 只列 `java.base`，sources JAR 各有 93 份核心來源。兩端以舊正式包編譯的消費端皆在新版包上以 Java 17 執行成功。`UIEngine.platform()` 與 `ThemeService.scope(...)` 以 default 方法擴充。工作樹的 Python 抽取契約 7 項均通過。

整合包比對也涵蓋同時更換 Windows 媒體後端的工作。補回兩個舊公開建構式，保留封面開關。舊 `Path` 參數作為相容簽章保留，媒體讀取沿用現行 JNA 後端。

### Phase 8 客戶端煙測（2026-11-10）

| 平台 | 無選用整合 | 含選用整合 |
| --- | --- | --- |
| Forge 1.20.1／JDK 17 | 通過。 [執行紀錄](../build/kernel-verification/forge-reviewed-game.log) | LDLib 1.x 通過。 [執行紀錄](../build/kernel-verification/forge-reviewed-game-optional.log) |
| NeoForge 1.21.1／JDK 21 | 通過。 [執行紀錄](../build/kernel-verification/neo-reviewed-game.log) | LDLib 2.x 通過。 [執行紀錄](../build/kernel-verification/neo-reviewed-game-optional.log) |

四組均經 Loader 發現測試模組，驗證原版選單 Tab、原生焦點、鍵盤 press／repeat／release、UTF-16 字元、滑鼠按下／拖曳／放開／移動、滾輪、GUI Scale、失焦、framebuffer 裁剪、主題範圍、動畫及頁面重掛載與取消。煙測也覆蓋視口回呼換頁、拖曳回呼失敗後取消，以及幀外輸入回呼同步開啟世界。資源包透過遊戲的 `reloadResourcePacks()` 重載並驗證快取失效。單機世界驗證登入、會話範圍內的 HUD pass 及登出釋放，遊戲停止後驗證模組停止及入口撤回。各組的 `screen.png`、`success.txt` 和 `stopped.txt` 位於 `build/kernel-verification/client-{forge,neoforge}[-optional]/`。

從專案根目錄以 Git Bash 執行以下命令。兩端各用自己的 Gradle 啟動器和遊戲工具鏈。加入 `-PkernelSmokeOptional=<對應版本的 LDLib JAR>` 可重跑選用組合。Forge 會在煙測目錄暫存並重映射該 JAR。

```bash
./gradle-install/gradle-8.8/bin/gradle -I gradle/kernel-smoke.init.gradle runClient --offline --no-daemon --console=plain
./neoforge/gradlew -p neoforge -I ../gradle/kernel-smoke.init.gradle runClient --offline --no-daemon --console=plain
```

## Phase 9　效能基準

**位置**　新增獨立 `src/kernelBenchmark/java`，共用 `kernelBenchmark` 入口；結果與執行環境記入 `docs/performance.md`。

- [x] 用獨立 Java 17 基準固定時鐘、工作量與相位預算，兩端另用同一份原生客戶端 fixture 量測 GUI、GPU、Screen 重掛和資源重載。
- [x] 保留 Phase 8 基線和 Phase 9 原始結果，對照平均、P95、P99、分配及工作量。見 [效能報告](performance.md) 和 [完整資料](kernel-performance-results.csv)。
- [x] 根據 JFR 指向的訂閱清單熱點，重用索引快照並以索引派送。回歸測試覆蓋快照失效、同級次序、巢狀派送及取消。

### Phase 9 驗證（2026-11-10）

| 平台 | `kernelTest` | 一般測試／建置 | 客戶端 |
| --- | ---: | --- | --- |
| Forge 1.20.1 | 96 通過 | 814 通過，`reobfJar`、`verifyLibraryJar` 通過。 [建置紀錄](../build/kernel-verification/forge-phase9-indexed.log) | 優化前後原生基準均完成，退出與模組停止標記齊全。 [前](../build/kernel-benchmark/forge-native-measured-before.log) [後](../build/kernel-benchmark/forge-native-measured-after.log) |
| NeoForge 1.21.1 | 96 通過 | 816 通過，`verifyLibraryJar` 通過。 [建置紀錄](../build/kernel-verification/neo-phase9-indexed.log) | 優化前後原生基準均完成，退出與模組停止標記齊全。 [前](../build/kernel-benchmark/neoforge-native-measured-before.log) [後](../build/kernel-benchmark/neoforge-native-measured-after.log) |

快照重用測試先在 Phase 8 邏輯下得到預期的失敗，再於兩端通過。兩端的 Gradle `kernelBenchmark` 入口另以短樣本通過；正式核心統計使用每端每版 9,000 筆／場景。 [預期失敗紀錄](../build/kernel-verification/forge-phase9-red.log)

## Phase 10　文件與消費端範例

- [x] 從 [公共 API](public-api.md) 連到模組開發、能力、調度、配置、權限、生命週期、雙端相容與效能文檔，並更新 [架構](kernel-architecture.md) 和 [遷移指南](migration-guide.md)。
- [x] [第三方範例](../examples/kernel-extension/README.md) 以實際依賴 JAR 編譯。兩個 Loader 分別從獨立具名 JAR 發現程式庫和範例。驗證服務、選用能力降級、作用域設定、原生控件鍵盤操作與退出。

### Phase 10 驗證（2026-11-10）

| 平台 | `kernelTest` | 一般測試／建置 | 第三方客戶端 |
| --- | ---: | --- | --- |
| Forge 1.20.1／JDK 17 | 96 通過 | 814 通過，程式庫與範例均通過正式重映射和封裝檢查。 [程式庫建置](../build/kernel-verification/forge-phase10-final.log) | 獨立客戶端載入兩份 Mojmap 開發 JAR，煙測及停機通過。 [執行紀錄](../build/kernel-verification/forge-phase10-example-accepted.log) [畫面](../examples/kernel-extension/forge/build/example-smoke/screen.png) |
| NeoForge 1.21.1／JDK 21 | 96 通過 | 816 通過，程式庫與範例均通過封裝檢查。 [程式庫建置](../build/kernel-verification/neo-phase10-final.log) | 獨立客戶端載入兩份正式 JAR，煙測及停機通過。 [執行紀錄](../build/kernel-verification/neoforge-phase10-example-accepted.log) [畫面](../examples/kernel-extension/neoforge/build/example-smoke/screen.png) |

每端六段完整 Java 文件範例均編譯通過，`SHUI` 片段通過 JavaScript 語法檢查。Python 抽取契約 7 項通過。相對 1.0.4 和 Phase 8 的 1.0.5 基線，Forge 正式／dev／raw 和 NeoForge 正式 JAR 均無既有 ABI 破壞。預編譯的舊消費端與實作者在新 JAR 上通過連結探針，核心仍為 Java 17 classfile。Forge 正式 SRG JAR 的生產客戶端啟動、實體 F3+T 和長時間資源壓測另行記錄於 TODO。

### Java 21 待交付快照複核（2026-11-10）

本輪使用 `C:\GRAALVM\graalvm-jdk-21.0.6+8.1`，先執行 `cleanCompileJava cleanProcessResources` 清除原生基準注入的編譯及資源輸出，再執行 `kernelTest test build --continue`。沒有新增或修改測試，亦未排除失敗用例。以下是本輪結果，不覆蓋上方歷史 Phase 10 紀錄：

| 平台 | 核心測試 | 一般測試 | 封裝與建置結果 |
| --- | --- | --- | --- |
| Forge 1.20.1／GraalVM 21 | 96 項，95 通過、1 失敗 | 814 項全部通過 | `reobfJar`、`releaseJar`、`verifyLibraryJar` 完成；整體 `build` 因核心測試失敗而未通過 |
| NeoForge 1.21.1／GraalVM 21 | 96 項，95 通過、1 失敗 | 816 項全部通過 | `jar`、`verifyLibraryJar` 完成；整體 `build` 因核心測試失敗而未通過 |

雙端唯一核心失敗皆為 `UnifiedUIContractTest.contractTestsRunOnJava17WithoutGameOrKotlinClasses` 第 22 行硬性要求 Java 17 執行期，與本輪指定 Java 21 不符；保留測試原狀及失敗紀錄。日誌為 `build/reports/delivery-forge-java21.log`（另有保留非 UTF-8 位元組轉義的 `.utf8.log` 閱讀副本）及 `build/reports/delivery-neoforge-java21-online.log`。NeoForge 首次離線執行因 JUnit 聚合 JAR 未快取而失敗，恢復線上依賴解析後完成上述測試。

兩端正式 JAR 均檢查到 224 個純核心 class，全部為 Java 17 class major 61；未發現重複 ZIP 項目、原生 benchmark／smoke 類或 benchmark mixin 資源混入。這僅驗證封裝與 classfile 邊界，不能替代舊版預編譯消費方連結。此時產出的 SHA-256：

配置檔讀取改為有界 `readNBytes(1 MiB + 1)` 後，雙端再執行 `kernelTest test build --continue --offline`：結果仍為各 96 項核心測試僅 Java 17 執行期斷言失敗，一般測試分別 814／816 項全過，兩端封裝檢查通過。日誌為 `build/reports/delivery-{forge,neoforge}-bounded-read-java21.utf8.log`。更新後正式成品 SHA-256：

- Forge `build/libs/sh_better_ui-1.0.5.jar`：`752e43ad4c612b798e2565e71aa37ebf7edbc920b3be3eda90ebc172db001077`
- NeoForge `neoforge/build/libs/sh_better_ui-neoforge-1.21.1-1.0.5.jar`：`ebbe834833aa140f4aac0d29d43c7fc7277fdd64a937812f6180c9f7f1a048be`

有界讀取的非遊戲實際呼叫拒絕 1,048,577 bytes 檔案並成功讀取合法條目，見 `build/reports/config-bounded-read-java21.log`。未模擬競爭寫入，僅由實作確認讀取配置本身的陣列上限。

Python 抽取契約本輪執行 7 項，3 通過、4 錯誤；四項均因預期的相鄰 `gt_shanhai` 消費工程檔案不存在，沒有偽造工程或略過檢查。獨立擴展範例的 Forge 與 NeoForge `build --offline` 均已在 GraalVM 21 通過，包括 `verifyExampleJar`；Forge 範例工具鏈已改為 Java 21，仍以 `--release 17` 輸出。日誌為 `build/reports/example-forge-java21.log` 和 `build/reports/example-neoforge-java21.log`。未執行 `runClient`，因此不能將範例編譯視為獨立 JAR 的遊戲內發現驗收。`public-api`、`module-development`、`capability-system`、`configuration`、`scheduler`、`migration-guide` 的六段完整 Java 範例已由 GraalVM 21 的 `javac` 分別對 Forge dev JAR／1.20.1-47.4.16 映射依賴及 NeoForge 正式 JAR／21.1.251 merged 依賴編譯通過，輸出日誌為 `build/doc-compile-java21/{forge,neoforge}.log`。編譯停用註解處理並指定空處理器搜尋路徑，避免與本專案無關的全域快取編譯器插件自動載入；未執行文件中的遊戲操作。歷史 ABI 快照所指的 `C:/jtmp/shbui-kernel78-abi.OtMK3SqN` 目前不存在，舊 ABI 連結仍待本輪重新核驗。使用者已要求不啟動遊戲，因此後續不執行客戶端或專用服啟動；生產實例、實體 F3+T、長時間資源壓測及原生效能驗收保持未驗證，不能以非遊戲建置替代。

## 共用驗證命令與交付門檻

在 Git Bash 由專案根目錄執行。設定環境與路徑依 [工程審計](kernel-audit.md)。Python 抽取契約使用 Python 3.11 或更新版本讀取 TOML。

```bash
./gradle-install/gradle-8.8/bin/gradle kernelTest --offline --no-daemon --console=plain
./neoforge/gradlew -p neoforge kernelTest --offline --no-daemon --console=plain
./gradle-install/gradle-8.8/bin/gradle test build --offline --no-daemon --console=plain
./neoforge/gradlew -p neoforge test build --offline --no-daemon --console=plain
python -B -m unittest discover -s src/test/python -v
```

`kernelTest` 已接入兩端 `check`，也可獨立執行。每次記錄檔案／位置／內容、測試數與失敗、剩餘風險及未執行的遊戲驗收。保留基線失敗和修復證據。正式結案要求兩端建置、契約及既有功能回歸、遊戲驗收、可重跑基準、使用文檔和擴展示例全部有結果。
