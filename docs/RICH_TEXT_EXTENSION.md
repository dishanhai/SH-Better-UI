<!-- 作者：山海／dishanhai -->
# 富文字擴充

Forge 1.20.1 與 NeoForge 1.21.1 共用固定行盒、版本化資料及客戶端繪製契約。以下入口為 **Experimental**。純 Java 的節點、格式策略和資料快照可以在沒有 Minecraft 客戶端類的環境中使用。原生繪製註冊需在客戶端渲染執行緒完成。

## 在 UUK 視圖綁定原生擴充

在已掛載的 UI tree 中使用其 `ScopeHandle`。登記的 ID 須屬於 scope 所有者的命名空間。同一份登記冊中，renderer 和資料來源各可登記 64 項，同類型的重複 ID 會被拒絕。宿主可明確綁定第三方模組提供的登記冊，第三方仍以自己的 scope 管理其生命週期。

```java
var meter = UiId.parse("example:energy_meter");
var energy = UiId.parse("example:energy");
var extensions = RichTextExtensions.attach(treeScope);
extensions.registerData(energy, () ->
        new RichTextDataSource.Snapshot(energyRevision, Integer.toString(energyPercent)));
extensions.register(meter, 1, context -> {
    var bounds = context.bounds();
    int right = bounds.x() + bounds.width();
    int bottom = bounds.y() + bounds.height();
    int percent = context.data(energy)
            .map(sample -> Integer.parseInt(sample.value()))
            .orElseGet(() -> Integer.parseInt(context.node().payload()));
    int filled = bounds.width() * Math.max(0, Math.min(100, percent)) / 100;
    context.graphics().fill(bounds.x(), bounds.y(), right, bottom, 0xff25364b);
    context.graphics().fill(bounds.x(), bounds.y(), bounds.x() + filled, bottom, 0xff45c6ad);
});
view.bindExtensions(extensions);

var document = RichText.document(UiId.parse("example:machine_status"))
        .extension(meter, 1, 40, 10, 10,
                Component.translatable("example.energy.alt").getString(), "0")
        .build();
```

- `RichExtensionNode` 宣告固有寬高、邏輯高度、替代文字和唯讀字串 payload。固有單邊上限為 4,096 px、面積上限為 4 Mi 像素、邏輯高度上限為 256 GUI px，版本為 1–65,535。替代文字及 payload 分別限 1 KiB 和 4 KiB UTF-8。
- `register`、`registerData` 回傳可個別撤銷的 `Subscription`。關閉登記冊或 scope 會撤銷兩類登記並釋放回呼。關閉後的 sample 為空，登記會拋出 `IllegalStateException`。缺少、撤銷、版本不符和繪製失敗均保留原行盒並使用替代文字。
- 每次視圖呈現先取樣已登記的資料來源，再使用同一份 `Frame` 繪製其節點。資料來源應純讀取、快速且不阻塞，回傳不可變 `Snapshot`。例外或 null 使當次值缺席。生產者決定非負 revision 的含義。修改資料值不會更動行盒。
- `Renderer` 在宿主既有 UUK Pass 內記錄命令。最多 128 條填色或 Minecraft 文字命令。填色裁至行盒，文字須完整放得下。輸出使用已提交的位姿並再和原生 viewport scissor 相交。回呼失敗時捨棄已記錄命令，畫占位。命令提交後畫布失效。重複失敗只記錄一次，成功後可重新報告。
- 可信 Java renderer 可以讀取節點、當幀時間、減少動效設定及註冊的資料快照。支援的 Canvas 操作由宿主管理位姿及 GL 狀態。原生 Java 執行環境仍允許呼叫其他 Java API，請按信任來源審查其程式碼。
- `Component` 投影、複製文字和原生 tooltip 使用替代文字。自訂節點目前為展示型，互動可用既有按鈕及滑桿節點承載。

## 顯式開放文件格式

下例授權 `example:energy_meter` 版本 1 的有界純資料。驗證器接受 canonical payload。可選的第三個 `Format` 參數為純函式，負責將 markup 參數轉為相同表示。

```java
var policy = new RichExtensionInput(Map.of(meter, new RichExtensionInput.Format(1,
        text -> text.matches("[0-9]{1,3}") && Integer.parseInt(text) <= 100,
        text -> text.equals("full") ? "100" : text)));
var markup = "[ext=example:energy_meter;1;40;10;10;Energy;full]";
var document = RichTextMarkup.parse(UiId.parse("example:machine_status"), markup, policy);
var json = RichDocumentCodec.encode(document, policy);
var decoded = RichDocumentCodec.decode(json, policy);
var dfu = RichDocumentCodec.codec(policy);
```

`[ext=...]` 依次使用類型、版本、固有寬、高、邏輯高度、替代文字及 payload。最後欄位可包含分號。JSON／DFU 共用 `type: "extension"` 信封，額外欄位為 `extension`、`version`、`width`、`height`、`logicalHeight`、`alt`、`payload`。既有無策略的 markup、JSON、DFU 入口拒絕這類信封。

解碼先檢查文檔及信封上限，再呼叫對應類型和版本的驗證器。沒有匹配策略時保留節點 ID、宣告尺寸與替代文字，節點在該份文件中維持占位。註冊 renderer 不會啟用先前未授權的節點。可用新策略重新解碼。格式政策只接受有界純資料。外部輸入的字串不可被擴充解讀為動作、URI 或 Java 類別，除非有其他明確受權的宿主入口。

## KubeJS

`SHUI.richText()` 使用既有可選客戶端插件。提供 `markup`、`json`、`encode` 及原生 `open` 預覽。待執行的預覽跟隨腳本世代取消，開啟後的不可變文件不持有腳本回呼。見 [客戶端腳本示例](KUBEJS.md#被动富文本预览)。

第三方自訂 Pass／材質、擴充事件處理及腳本 renderer 登記仍待實作。
