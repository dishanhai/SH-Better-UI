# 跨版本客戶端 API

適用於 Forge 1.20.1、NeoForge 1.21.1、26.1.2 和 26.2。消費模組用同一份 Java 原始碼，分別對目標版本編譯；這不是跨 Minecraft 版本共用同一個 JAR。

| 用途 | 共用入口 |
| --- | --- |
| 模組內的服務 | `context.engine()`。保留原有模組作用域與權限。 |
| 客戶端啟動後的公開入口 | `ShBetterUiApi.unified()`，等同 `UnifiedUI.get()`。 |
| 宣告式頁面 | `engine.pages()`，以 `pageTypes()` 和 `components()` 判斷可用能力。所有目標的共同頁面路徑是本地 `SCREEN` 與 `MODAL`。 |
| 材質索引與預設 | `engine.materialCatalog()`，共用 ID、查詢和預設編解碼。材質有索引不代表每個後端都能繪製。 |
| 外觀與 HUD | `settings().theme()/saveTheme(...)`、`backgroundIds()/supportsBackground(...)`；共用 `UiHudPanel` 尺寸與位置呼叫方式。 |
| 畫面繪製和對齊 | `UiFrame.canvas()`；使用 GUI 邏輯座標的填色、文字、SVG／貼圖圖示、巢狀裁剪、共用玻璃面板和 `createLayout()`。 |

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

`UiCanvas` 只在取得它的繪圖回呼中使用。若用 `createLayout()`，把佈局保存在頁面狀態，窗口重排時更新它的 viewport，不要每幀重建。用 `canvas.placed(view, ...)` 繪製局部座標，輸入則用同一個 `view.hitTest(...)`。圖示沿用當前平台與使用者偏好，不強制改變 OpenGL／Vulkan 後端。

使用 `canvas.glass(new UiGlassPanel(bounds, radius, GlassVariant.REGULAR, Foreground.LIGHT))` 時，先畫背景、再畫玻璃、最後畫文字。結果是原生光學繪製提交、可讀實色回退或空區域。高對比與停用模糊維持可讀實色；舊版 Vulkan 宿主不會呼叫 OpenGL 效果。這兩種玻璃預設不是任意自訂 shader 的移植。

原有 `frame.graphics()` 和舊版原生 API 保留，供有需要的現有消費模組繼續使用。跨版本入口不宣告 26.x 已經有服務端頁面、舊版任意自訂材質繪圖或相同模組整合。這些仍需要各平台的實作，不應以空接口代替。

## 26.x 的本地對話框

在 `engine.pages().pageTypes()` 確認 `UiPageType.MODAL` 後，可用 `UiPageDefinition.Builder.type(UiPageType.MODAL)` 註冊。26.x 將對話框置中並使用可捲動內容；Minecraft 在對話框開啟時不會把輸入送到上一個畫面。返回／Esc 或由擁有者關閉會返回上一個畫面，外部切換畫面則結束這個實例而不蓋過新畫面。一個客戶端同時只能開啟一個宣告式前景頁面；這不提供遠端對話框、優先級搶佔、`HUD` 或 `NOTIFICATION` 的宣告式掛載。26.x 本地動作僅接受無負載的 `LOCAL`，表單草稿透過 `Instance.localValues()` 讀取。


## 外觀與 HUD

用 `ShBetterUiApi.settings().theme()` 讀已套用的配色，`saveTheme(...)` 只保存主題部分。背景選擇先讀 `backgroundIds()`；跨版讀舊設定先用 `supportsBackground(id)` 檢查，未支援的舊選項不必刪掉。26.x 仍只渲染已接入的原生背景，並不承諾舊版自訂圖片或著色器可用。

玩家 HUD 可用 `new UiHudPanel(font, rows, minimumTextWidth, iconSize)`。同一尺寸只改左欄圖示，不改文字、鍵帽、整體大小或拖曳座標。各版保留自己的繪圖路徑；26.x 的 `Row(GraphicIcon, ...)` 使用同個圖示的靜態貼圖。
