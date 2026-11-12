<!-- 作者：山海／dishanhai -->
# 第三方模組範例

本範例是獨立模組，包含用戶端示範和選用的服務端兌換命令。Forge 1.20.1 和 NeoForge 1.21.1 共用 `common/src/main`，各自提供 Loader 入口、相依宣告和資源包格式。模組 ID 為 `shbui_kernel_example`。

## 使用方式

標題畫面和暫停選單右上角有「UI 核心範例」。可用滑鼠點選，也可用 Tab 移動焦點後按 Enter 開啟。計數器每次加 3，能復原上一次操作。按 Esc 或返回鍵會回到原畫面並還原焦點。重開頁面會取得新的作用域及初始計數。

- [ExampleClient](common/src/main/java/com/dishanhai/shbui_kernel_example/ExampleClient.java) 在 UI 引擎可用時從 `UnifiedUI.get()` 查詢自訂能力，再以 `CounterService.KEY` 取得服務。
- [ExampleUiModule](common/src/main/java/com/dishanhai/shbui_kernel_example/ExampleUiModule.java) 在 `register` 註冊同命名空間的服務，在 `start` 取得 `UIApis.SCREENS` 和可選主題服務，並宣告 `STEP` 的型別和驗證範圍。遊戲中的元件層值為 3，其他作用域仍讀到預設值 1。
- 模組宣告可選的 `UIApis.SYSTEM_SCREEN_READER`。缺少該能力時，按鈕使用 Minecraft 內建旁白。動態主題不可用時，畫面使用既有預設樣式。
- [探索描述檔](common/src/main/resources/META-INF/sh_better_ui.modules) 由 Loader 從模組自己的 JAR 讀取。實體畫面關閉時，引擎關閉掛載的樹及元件作用域。引擎停止時，模組也會銷毀可能仍開啟的畫面。

## 宣告式頁面

從標題畫面或暫停選單選擇「宣告式頁面」。本機預覽以 `PageService.register` 註冊固定控件樹，使用 `open` 建立實例，按下兌換後以 `update` 更新該實例的測試點數。在遊戲中開啟時，頁面還可掛載或解除 HUD，並發出通知。HUD 與通知使用同一份頁面 API 的被動根節點，由現有即時表面服務呈現。 [ClientPageExamples](common/src/main/java/com/dishanhai/shbui_kernel_example/ClientPageExamples.java)

把同一範例及 SH-Better-UI 裝到服務端，或使用本機整合伺服器。連線後先在客戶端執行 `/shbetterpages screen`，允許當前連線開啟頁面，再執行 `/shbui-example-exchange`。服務端使用相同的兌換控件結構，將操作宣告為需要確認的權威動作。每人每次伺服器啟動有五個測試點數，每次成功兌換扣一點並發放一點 Minecraft 經驗。餘額由服務端保存並重新核對，點數為零時操作回報失敗。 [ExampleServerPages](common/src/main/java/com/dishanhai/shbui_kernel_example/ExampleServerPages.java)

## 富文字 SDK 範例

從同一畫面開啟「富文字 SDK」。原生 Screen 讀取[模組內的 v1 JSON 文件](common/src/main/resources/assets/shbui_kernel_example/rich_text/guide.json)，使用公開 API 顯示同行鑽石貼圖、原生物品提示和自訂行內能量條。用下方按鈕切換充能，Back 或 Esc 返回。可用滾輪或 Page Up／Page Down 捲動較長文件。將編輯器輸出的 v1 JSON 放到同一個資源位置可替換指南。 [SDK 接入說明](../../docs/RICH_TEXT_SDK.md)

## 文字可讀性範例

從標題畫面或暫停選單開啟「文字可讀性」。兩行文字共用會經過文字下方的亮色背景。第一行由 `submit` 排入清晰前景，第二行在 `foreground` 回呼取得同作用域的有效繪製上下文，再呼叫 `drawNow`。選色不需要背景效果或全域字型攔截。啟用減少動效時亮色區域固定，啟用高對比時使用深色底。

[Java Screen](common/src/main/java/com/dishanhai/shbui_kernel_example/TextReadabilitySdkScreen.java) 示範掛載、配置、已排版文字的提交和關閉。兩端也會編譯 [Kotlin 範例](common/src/main/kotlin/com/dishanhai/shbui_kernel_example/TextReadabilityKotlinExample.kt)。在呼叫 `submit` 和 `foreground` 的同一畫格分別建立不同文字的請求，並為每個放置位置提供自己的穩定 `TextId`。

## 即時活動範例

用滑鼠或 Tab／Enter 操作範例頁的按鈕。頁面底部顯示核心實際選出的主表面與次表面。使用預設即時表面設定時，單擊表面本體即可展開，再用滑鼠或 Tab 操作控件。

狀態列用 `ui.glass().material(REGULAR)` 取得材質，並透過頁面作用域提交到 `RenderService`。啟動或停止活動時，同一條可中斷動畫軌同步改變輪廓、折射和高光。材質繪製後再繪製狀態文字。減少動效時立即切到目標，關閉背景效果時使用實色底板。 [Liquid Glass API](../../docs/public-api.md#liquid-glass)

| 範例按鈕 | 操作及可觀察結果 |
| --- | --- |
| 播放音符盒旋律 | 播放約 6 秒的 Minecraft 豎琴音符序列。共用音樂圖示作為封面，表面顯示進度和節拍。頁面與展開表面可暫停或繼續，展開表面可拖曳進度。 |
| 開始合成範例 | 本機範例後端逐步完成 32 件合成。可取消，也可等合成完成通知。使用實際 AE2 後端時，由整合方以 `ExampleUiModule.installCraftingBackend(...)` 傳入 `CraftingBackend`，提供啟動、輪詢及取消。範例 JAR 無需 AE2。 |
| 啟動範例機器 | 本機機器模型每刻補充能量，完成一次加工會消耗 30 FE。展開表面顯示電量與已完成週期，完成 16 次加工後發出通知。這個模型由範例頁推進。 |
| 監聽伺服器連線 | 開啟後返回多人遊戲，連入任一可用伺服器。連線中、成功、取消／失敗和退出由實際的 Minecraft 畫面及 Loader 事件驅動。監聽由模組作用域持有，離開範例頁後仍會執行。 |
| 執行優先順序範例 | 單獨啟動此項可看到低優先順序背景工作。約 2 秒後，高優先順序工作佔用主表面，約 5 秒後完成。背景工作接著恢復顯示，其進度在等待期間仍持續推進。 |

關閉頁面會取消本頁工作與音符播放，並關閉對應的作用域。若要同時結束連線監聽，可按「停止範例」。[`LiveActivityExamples`](common/src/main/java/com/dishanhai/shbui_kernel_example/LiveActivityExamples.java) 使用公開的 `ActivityService.KEY` 和 `NotificationService.KEY`。[`ExampleUiModule`](common/src/main/java/com/dishanhai/shbui_kernel_example/ExampleUiModule.java) 使用 `LiveSurfaceService.KEY` 觀察仲裁結果，並為返回工具列保留畫面空間。

## 建置

從儲存庫根目錄以 Git Bash 執行。先建置含頁面及文字可讀性 API 的 SH-Better-UI 1.0.7 雙端依賴 JAR。兩端使用 Java 21 和 Kotlin 2.2.21，Forge 使用 Gradle 8.8 並輸出 Java 17 位元組碼，NeoForge 使用 Gradle 9.2.1 並輸出 Java 21。Forge 預設在工作區的 `libs/` 尋找 KotlinForForge 4.12.0，NeoForge 使用 Maven 上的 KotlinForForge 5.10.0。依賴尚未下載時需在線執行一次。`build` 只編譯和封裝範例。

```bash
./gradle-install/gradle-8.8/bin/gradle -p examples/kernel-extension/forge build --offline --no-daemon --console=plain
./neoforge/gradlew -p examples/kernel-extension/neoforge build --offline --no-daemon --console=plain
```

| 平台 | 編譯依賴（預設） | 正式範例成品 |
| --- | --- | --- |
| Forge | `build/libs/sh_better_ui-1.0.7-dev.jar` | `forge/build/libs/shbui-kernel-example-forge-1.0.0.jar` |
| NeoForge | `neoforge/build/libs/sh_better_ui-neoforge-1.21.1-1.0.7.jar` | `neoforge/build/libs/shbui-kernel-example-neoforge-1.0.0.jar` |

將整個範例資料夾複製到另一個位置時，可以用本機 Gradle 從 `forge/` 或 `neoforge/` 建置，並用 `-PshBetterUiJar=<絕對路徑>` 選擇 SDK JAR。Forge 開發啟動也可用 `-PkotlinForForgeJar=<絕對路徑>` 指定 KotlinForForge JAR。Forge 的開發環境使用 Mojmap 的 `-dev.jar`，正式啟動器使用重新映射過的正式 JAR。正式安裝時，把範例、對應版本的 SH-Better-UI 和 KotlinForForge 各自放進 `mods/`。

## 客戶端煙測

```bash
./gradle-install/gradle-8.8/bin/gradle -p examples/kernel-extension/forge runClient -PexampleSmoke --offline --no-daemon --console=plain
./neoforge/gradlew -p examples/kernel-extension/neoforge runClient -PexampleSmoke --offline --no-daemon --console=plain
```

啟動工作將範例和程式庫 JAR 複製到各範例工程的 `build/example-smoke/mods/`。Forge 使用 Java 17 啟動 Mojmap 的兩份開發 JAR，另複製 KotlinForForge 的發行 JAR。NeoForge 使用 Java 21 啟動兩份正式 JAR，並由 ModDevGradle 將 KotlinForForge Maven 依賴加入啟動 classpath。

煙測透過實際按鈕驗證計數與復原、旋律活動暫停和繼續、合成取消與完成通知、機器耗能和進度、優先順序搶佔與恢復，以及頁面關閉後的作用域清理。連線範例驗證監聽開關及其跨頁面狀態。富文字驗證封裝 JSON、鑽石貼圖、物品提示命中和能量條更新後的實際像素。文字可讀性驗證兩種公開繪製入口、Esc 返回時的焦點與作用域清理。結果保存在 `build/example-smoke/` 的 `screen.png`、`rich-text.png`、`text-readability.png`、`success.txt` 及 `stopped.txt`。

省略 `-PexampleSmoke` 可在 `build/example-client/` 手動操作。多人伺服器連線及實際聆聽音效需在遊戲中驗收。
