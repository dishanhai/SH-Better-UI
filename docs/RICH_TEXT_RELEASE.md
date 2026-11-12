<!-- 作者：山海／dishanhai -->
# 富文字編輯器與發布準備

## Minecraft 編輯器

在安裝 SH-Better-UI 的 Forge 1.20.1 或 NeoForge 1.21.1 客戶端進入世界，執行 `/shbetterricheditor`。預覽使用原生字型、資源包及 RichTextView。可從節點樹選取節點，也可直接在預覽點選可見內容，再編輯屬性並套用。新增、刪除、調整順序和套用屬性都可復原、重做。可切換行盒與命中疊層，查看佈局、繪製診斷，或使用時間軸檢查圖片動畫。

檔案頁提供預設、儲存、重新開啟、匯入和匯出。路徑相對於本機遊戲目錄 `sh_better_ui/rich_text_editor/`。

| 檔案 | 用途 |
| --- | --- |
| `document.json` | 儲存的草稿。再次開啟編輯器會讀取。 |
| `export.json` | 可在 Web 工作台或消費模組讀入的 v1 文件。 |
| `ExportedRichText.java` | 可用消費端同版本的 SH-Better-UI 編譯，呼叫 `ExportedRichText.document()` 建立文件。 |
| `preset.json` | 單一自訂預設。載入自訂或內建預設後可以復原。 |

將 v1 JSON 全文複製到剪貼簿，按「匯入 JSON」即可載入為草稿。本機草稿以 UTF-8 儲存。關閉或重新開啟可能捨棄尚未儲存的內容時，依畫面提示確認或返回編輯。切換資源包後，預覽會沿原生資源世代重新讀取圖像和字型。時間軸位置、選取及診斷顯示屬於編輯工作階段。

## Web 工作台

在專案根目錄執行 `npm --prefix web run build` 和 `npm --prefix web run serve`，從材質實驗室進入富文字工作台，或開啟同一服務的 `rich-text.html`。可開啟本機 JSON，編輯後下載並交給 Minecraft 匯入。兩端交換使用相同的文件版本和穩定節點 ID。

Web 使用瀏覽器的字型、斷行及顯示尺寸。原生翻譯鍵使用替代文字，資源包圖像及擴充使用對應占位，資源 ID 不會變成瀏覽器網路請求。原生字型、命中、材質、動畫取樣和遊戲物件以 Minecraft 預覽為準。未知資料可在 Web 保留供檢查，Minecraft v1 codec 仍會拒絕未知節點類型和未知文件版本。已定義的擴充信封可依消費端的明確策略作為惰性占位或啟用。

## 既有模組遷移

1. 在需要同行圖文或多樣式的畫面採用 `RichDocument`，為要跨版次定位的內容指定固定 `UiId`。保留宿主的原有控制、權限和旁白流程。
2. 從原生 `Component` 遷移時，用 `RichTextComponents.resolve` 保留可信本機的 hover、click 和 insertion。可分享的 v1 JSON 使用被動節點，Java 回呼留在宿主的掛載作用域。
3. 掛載 `RichTextView` 並在同一個 UUK 視覺佈局 prepare／draw。使用最後一次成功呈現的 snapshot 處理命中，在 resize 或資源世代變更時讓舊幾何失效。
4. 依 Loader 選用對應 JAR，在 Gradle 宣告依賴。套件及其執行期平台依賴由遊戲提供。 [SDK 與雙端範例](RICH_TEXT_SDK.md)

## 功能狀態與啟用條件

| 能力 | 狀態 | 啟用方式 |
| --- | --- | --- |
| v1 被動文件、純 Java 17 佈局、原生文字與本機媒體 | Stable 核心 | 消費方在其畫面掛載。 |
| 遊戲內編輯器與 Web 工作台 | Experimental | 編輯器命令預設可用。客戶端 JVM 設定 `-Dshb.richText.editor=false` 可停用內建編輯器入口。 |
| HTTPS、Java 擴充、KubeJS、資料同步 | Experimental | HTTPS 須明確授權別名及主機。擴充須登記 renderer 和輸入策略。KubeJS 須有對應 Loader 模組。同步須登記服務端資料來源並完成能力協商。 |
| OS IME 預編輯、跨 run 複雜文字塑形、外部 Render Graph Pass、多人協同文檔 | Unavailable | 已知限制和後續場景見 [TODO](../TODO.md#rich-text--inline-content-engine)。 |

## 發布檢查表

- 在 Forge 1.20.1／Java 17 與 NeoForge 1.21.1／Java 21 執行完整構建及 JAR 驗證，核對純核心保持 Java 17。Forge 發行包需通過 reobf。
- 重跑雙端原生煙測、固定佈局與文件 Golden、Web 單元測試和 Chromium 操作流程。畫面證據保留在各次隔離執行目錄。
- 用兩端各自的發行 JAR 編譯 SDK 範例和編輯器產生的 Java。檢查範例套件中的共享庫、測試類別及第三方依賴邊界。
- 以相同 Java 17／heap／樣本重跑長文件、可見行查詢與同步壓力基準。記下硬體、樣本數和量測範圍。
- 以 [驗證矩陣](RICH_TEXT_TESTING.md) 及 [TODO](../TODO.md#rich-text--inline-content-engine) 複核功能狀態、已知失敗的重現步驟和仍待完成的目標環境驗收。
