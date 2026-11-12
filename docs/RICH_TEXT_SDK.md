<!-- 作者：山海／dishanhai -->
# 第三方富文字 SDK

[獨立範例模組](../examples/kernel-extension/README.md)的 Forge 1.20.1 與 NeoForge 1.21.1 專案共用同一份 Java 原始碼。安裝對應的 SH-Better-UI、KotlinForForge 與範例 JAR 後，從標題畫面或暫停選單開啟「富文字 SDK」。畫面會載入資源中的文件，將鑽石貼圖與文字同行繪製。懸停第二個鑽石可看原生物品提示。點選充能或以 Tab、Enter 操作，可觀察行內能量條更新。Back 或 Esc 會回到原畫面並恢復入口焦點。

## 依賴與載入

| 平台 | 範例編譯依賴 | 遊戲安裝 |
| --- | --- | --- |
| Forge 1.20.1／Java 17 | SH-Better-UI 的 Mojmap `-dev.jar`，以 `compileOnly` 引用 | 同版重新映射後的 SH-Better-UI 發行 JAR、Forge 版 KotlinForForge、消費模組 |
| NeoForge 1.21.1／Java 21 | SH-Better-UI 的 NeoForge 發行 JAR，以 `compileOnly` 引用 | 同一份 NeoForge SH-Better-UI JAR、NeoForge 版 KotlinForForge、消費模組 |

在 Loader metadata 宣告客戶端依賴 `sh_better_ui`。UUK 模組依原有方式透過 `META-INF/sh_better_ui.modules` 被發現。原生 `Screen`、`RichTextView` 和 renderer 只在客戶端入口引用。範例打包檢查會拒絕內嵌的 SH-Better-UI 類別、資源與巢狀依賴。

## 從編輯器文件接入原生畫面

1. 在富文字編輯器匯出 v1 JSON，放到 `assets/<modid>/rich_text/guide.json`。同一格式也可由 `RichDocumentCodec.encode(document)` 產生。使用你自己的穩定節點 ID，將本地化鍵放入 `lang`，並把自訂貼圖放入同命名空間的 `textures`。範例直接引用 Minecraft 的鑽石貼圖。
2. 用 `ResourceManager` 讀取該資源。範例在解碼前檢查 UTF-8 與 256 KiB 上限，再呼叫 `RichDocumentCodec.decode(json)`。預設格式接受文字、翻譯、段落、本機圖片及其樣式。文件載入失敗時會顯示本地化訊息。資源重新載入後會重新讀取文件。範例為宿主節點預留 `shbui_kernel_example:guide/`。
3. 在 `Screen.init` 掛載自己的 `ScopeHandle`，建立 `AlignmentLayout.Node`，並以 `RichTextView.attach(module, scope, node)` 綁定。同一畫面透過 `UIApis.LAYOUT` 持有一份視覺綁定。取樣回呼先 `prepare(document, font, contentWidth)`，再用結果安排高度與局部裁剪。視口外的內容可用滾輪或 Page Up／Page Down 捲動。
4. 在原生 Screen 繪製事件內透過 `binding.renderScreen` 呼叫 `view.draw`。載入遮罩直接呼叫原生 Screen 時略過 UUK 繪製並失效舊快照。原生按鈕保留原有焦點與旁白。物品提示透過 `view.interaction().renderTooltip` 使用已繪製的幾何。畫面移除時關閉 scope。

範例的 JSON 與原生宿主可直接複用。 [guide.json](../examples/kernel-extension/common/src/main/resources/assets/shbui_kernel_example/rich_text/guide.json) · [RichTextSdkScreen.java](../examples/kernel-extension/common/src/main/java/com/dishanhai/shbui_kernel_example/RichTextSdkScreen.java) · [完整 API](RICH_TEXT_API.md)

### 從既有文字畫面遷移

選一塊需要行內內容的區域開始。現有 `Component` 可經 `RichTextComponents.resolve(prefix, component)` 產生快照，再交給 `view.prepare(snapshot, font, width)`。宿主沿用原生按鈕與動作派送，可從 `view.componentStyleAt(x, y)` 取得已呈現位置的可信樣式。圖片的固有尺寸決定比例，畫面上的邏輯高度隨原生字型行高。將需要固定行盒的原生物件交由 `RichObjectNode` 或 `RichExtensionNode` 宣告。 [Component 互操作](RICH_TEXT_API.md#文件交換與選取) · [行盒與字型](RICH_TEXT_LAYOUT.md)

## 自訂節點與即時資料

範例宿主在匯入文件後加入 `RichObjectNode.Item` 與版本 1 的 `RichExtensionNode`。renderer 和資料來源使用同一個畫面 scope 及範例命名空間，透過 `RichTextExtensions.attach(scope)`、`register`、`registerData` 和 `view.bindExtensions` 接入。

- 按下充能時更新能量和 revision。資料來源在每次呈現取樣一次，回傳不可變的 `Snapshot`。行內盒寬高固定，畫布以當次的數值改變填色長度。
- renderer 使用回呼提供的有界 `Canvas`。尺寸、裁剪、資源重載及關閉由 UUK 與宿主 scope 管理。缺少 renderer、版本不合或繪製失敗時保留原盒並顯示替代文字。
- 此擴充屬於 Experimental。可匯入的自訂節點另需顯式提供 `RichExtensionInput` 策略，格式驗證器只接受有界資料。範例的能量條由可信 Java 宿主建立。

[擴充契約與格式策略](RICH_TEXT_EXTENSION.md) · [圖片、動畫與素材政策](RICH_TEXT_MEDIA.md)

## 選用接入

**KubeJS**。將 [範例腳本](../examples/kernel-extension/optional/kubejs/client_scripts/rich_text.js) 放到已裝有相容 KubeJS 的實例的 `kubejs/client_scripts/`。從你現有的客戶端事件或按鍵回呼呼叫 `global.openExampleRichText()`。腳本示範被動標記、JSON 匯出與重新讀取，再開啟原生預覽。Java 範例模組可獨立執行。 [KubeJS 安裝與生命週期](KUBEJS.md#被动富文本预览)

**伺服器資料**。可將範例的本機充能值替換成伺服器供應的 `RichSyncProtocol.Number`。伺服器在啟動時使用 `ServerRichDocuments.register`，由 `Access` 檢查讀取者與編輯權限。客戶端可使用以下綁定方式：

```java
var documentId = UiId.parse("example:machine");
var energyField = UiId.parse("example:energy");
var extensions = RichTextExtensions.attach(screenScope);
if (!ClientRichDocuments.hasActiveWatch()) {
    var latest = new AtomicReference<RichSyncClient.View>();
    ClientRichDocuments.watch(screenScope, documentId, latest::set);
    extensions.registerData(energyField, () -> {
        var state = latest.get();
        if (state == null) return new RichTextDataSource.Snapshot(0, "0");
        var number = state.authoritative().get(energyField);
        long percent = number instanceof RichSyncProtocol.Number value ? value.number() : 0;
        return new RichTextDataSource.Snapshot(state.revision(), Long.toString(Math.max(0, Math.min(100, percent))));
    });
}
```

宿主可用 `View.status()` 表示離線、等待或已同步。編輯用 `Watch.edit` 提交提案，由伺服器確認結果。scope 關閉時會解除訂閱。網路協定屬於 Experimental，現成單人示例可用 `/shbetterrichsync` 查看訂閱與預測回復。 [同步契約及伺服器範例](RICH_TEXT_NETWORK.md)

## 驗證

從儲存庫根目錄依[範例建置指令](../examples/kernel-extension/README.md#建置)執行兩端 `build` 及 `runClient -PexampleSmoke`。煙測從 Loader 分開載入範例和程式庫 JAR，經由原生 UI 驗證封裝文件、鑽石貼圖就緒、物品提示命中、能量由 25% 變為 50% 時的實際像素、相同行盒與 scope 清理。輸出為各範例的 `build/example-smoke/rich-text.png`、`success.txt` 和 `stopped.txt`。

此範例的選用 KubeJS 腳本已作語法檢查，實際的 KubeJS 載入與選用的伺服器訂閱接法需在目標整合包驗收。同步核心及原生單人測試見[驗證紀錄](RICH_TEXT_TESTING.md)。
