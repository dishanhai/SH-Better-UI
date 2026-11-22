# 跨版本客戶端 API

適用於 Forge 1.20.1、NeoForge 1.21.1、26.1.2 和 26.2。消費模組用同一份 Java 原始碼，分別對目標版本編譯；這不是跨 Minecraft 版本共用同一個 JAR。

| 用途 | 共用入口 |
| --- | --- |
| 模組內的服務 | `context.engine()`。保留原有模組作用域與權限。 |
| 客戶端啟動後的公開入口 | `ShBetterUiApi.unified()`，等同 `UnifiedUI.get()`。 |
| 宣告式頁面 | `engine.pages()`，以 `pageTypes()` 和 `components()` 判斷可用能力。所有目標的共同頁面路徑是本地 `SCREEN`。 |
| 材質索引與預設 | `engine.materialCatalog()`，共用 ID、查詢和預設編解碼。材質有索引不代表每個後端都能繪製。 |
| 畫面繪製和對齊 | `UiFrame.canvas()`；使用 GUI 邏輯座標的填色、文字、SVG／貼圖圖示、巢狀裁剪和 `createLayout()`。 |

```java
var engine = context.engine();
var material = engine.materialCatalog().find("sh_better_ui:liquid_glass");
boolean localScreens = engine.pages().pageTypes().contains(UiPageType.SCREEN);

ShBetterUiApi.screen("example.preview.title").onRender((screen, frame) -> {
    var canvas = frame.canvas();
    var area = canvas.viewport();
    canvas.clipped(area, () -> {
        canvas.fill(area, 0xff18202c);
        canvas.icon(GraphicIcon.INFO, area.x() + 8, area.y() + 8, 1, 0xffffffff);
    });
}).open();
```

`UiCanvas` 只在取得它的繪圖回呼中使用。若用 `createLayout()`，把佈局保存在頁面狀態，窗口重排時更新它的 viewport，不要每幀重建。圖示沿用當前平台與使用者偏好，不強制改變 OpenGL／Vulkan 後端。

原有 `frame.graphics()` 和舊版原生 API 保留，供有需要的現有消費模組繼續使用。跨版本入口不宣告 26.x 已經有服務端頁面、舊版自訂材質繪圖、獨立背景選擇或相同模組整合。這些仍需要各平台的實作，不應以空接口代替。
