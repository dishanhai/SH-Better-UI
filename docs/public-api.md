<!-- 作者：山海／dishanhai -->
# Unified UI 公開 API

[English API reference](API_REFERENCE.en.md#get-the-kernel-and-look-up-services)

`com.dishanhai.sh_better_ui.kernel.api` 提供 Java 17 內核契約與 Minecraft 客戶端服務。兩個發行包各自包含核心，`kernel.internal`、Loader bridge 和 mixin 由 SH-Better-UI 維護。使用兩端各自的依賴 JAR 編譯。 [平台相容性](platform-compatibility.md)

## 取得引擎和服務

遊戲客戶端完成安裝後，以 `UnifiedUI.get()` 取得共用引擎。這個視圖可讀取公開服務、能力、全域設定和狀態。在 `UIModule.start` 及後續回呼中，使用 `context.engine()` 取得含模組身分的視圖，並以 `context.scope()` 或掛載的樹作為操作目標。啟動前、專用伺服器和停止後用 `isAvailable()` 判斷。此時 `get()` 會拋出 `IllegalStateException`。 [生命週期](lifecycle.md)

```java
import com.dishanhai.sh_better_ui.kernel.api.ThemeService;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UIEngine;
import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import java.util.Optional;

final class ThemeQuery {
    static Optional<ThemeService> currentTheme() {
        if (!UnifiedUI.isAvailable()) return Optional.empty();
        UIEngine ui = UnifiedUI.get();
        return ui.find(UIApis.THEME);
    }
}
```

`find(key)` 在目前視圖查不到相符服務時傳回 `Optional.empty()`。`require(key)` 用於必要服務，缺失時拋出含 ID、型別和契約版本的 `NoSuchElementException`。`ServiceKey<T>` 的三個欄位都須相符。`registry().snapshot()` 提供有序、不可變的可見服務描述和來源。可在所屬頁面或模組存活期間重用已解析的參考。跨生命週期時應重新解析，並檢查相關作用域。 [註冊方式](module-development.md) · [能力查詢](capability-system.md)

## 內建服務

下表的鍵皆在 `UIApis`。查詢可用性和取得服務是兩個獨立操作。 [服務鍵原始碼](../src/main/java/com/dishanhai/sh_better_ui/kernel/api/UIApis.java)

| 鍵及型別 | 公開操作 | 使用邊界 |
| --- | --- | --- |
| `THEME` · `ThemeService` | `snapshot()`、`availableThemes()`、`scope(context, node)` | 快照可跨執行緒讀。樹與組件的 token 分支可繼承與局部覆寫，由節點清理。 |
| `ANIMATION` · `AnimationService` | `motion()`、`bind(...)` | 以節點和宣告的屬性租用動畫軌。以 `Track` 播放、融合、暫停、恢復、取消與領取完成版本。現有 `MotionApi` 提供時間軸和組合。 |
| `LAYOUT` · `LayoutService` | `layout()`、`bind(...)`、`styles(...)` | 對齊、量測與文字自適應工具可獨立呼叫。以宣告的 layout writer 綁定 measure 和 arrange，並接入髒標記。 |
| `INPUT` · `InputService` | `interactions()`、`target(...)`、`capture(...)` | 活動樹的原生 Screen 輸入，透過 `events()` 訂閱 `EVENT` 或 `POINTER_MOVED`。捕獲需要兩項原生授權。 |
| `FOCUS` · `FocusService` | `request(...)`、`target(...)` | 使用原生 `GuiEventListener`，焦點取得回傳可選租約。鍵盤和字元路由跟隨實際焦點。 |
| `COLLISION` · `CollisionService` | `mount(...)`、`target(...)` | 使用既有 `UiCollisionLayout`，命中取已發布的呈現幾何及裁剪。每個 body 可設定 GUI 單位的 hit slop。 |
| `RENDER` · `RenderService` | `graphics()`、`blur()`、`submit(...)` | 單次原生 `SCREEN` 或 `HUD` pass。`Context` 在回呼內有效，提供 GUI 座標填色、裁剪及變換。 |
| `HUD` · `HudService` | `mount(context, draw)` | 持續繪製原生 HUD，需宣告 `NATIVE_HUD` 和申請 `REGISTER_RENDER_PASS`。回呼及掛載由模組作用域管理。 |
| `COMPUTER_STATUS` · `ComputerStatusService` | `read(Set<Metric>)` | 回傳唯讀的本機電腦快照，按請求背景更新。 |
| `GLASS` · `GlassService` | `ui.glass().material(variant)`、`diagnostics()` | 使用 `RenderService.Context.glass(...)` 提交不可變材質和幾何，之後繪製前景。快照可跨執行緒讀取。 |
| `RESOURCES` · `ResourcesService` | `version()`、`observe(...)`、`alignmentAssets(...)` | 成功套用重載並清除受管快取後通知。素材快取隨作用域釋放。 |
| `ACCESSIBILITY` · `AccessibilityService` | `preferences(...)`、`describe(...)`、`semantics(...)`、`narrate(...)` | 可取得介面偏好，並在原生旁白回呼寫入語義角色、名稱和提示。 |
| `SCREENS` · `ScreenService` | `create(...)`、`mount(...)`、`session(...)` | 建立有模組身分的 `UiScreen`，掛載自己擁有的活動畫面，或掛載真實連線會話。 |
| `PLATFORM` · `UIPlatform` | `info()`、`viewport()`、`hasClientSession()`、`isOnUIThread()` | 可用 `ui.platform()` 取得同一服務。視口包含 GUI 邏輯尺寸、比例和視窗焦點。 |
| `ACTIVITIES` · `ActivityService` | `publish(...)`、`updateAll(...)`、版本化活動句柄 | 在模組、會話或頁面作用域發布。不可變內容可從背景提交，中央狀態相位處理合併。 |
| `NOTIFICATIONS` · `NotificationService` | `publish(...)`、`dismissAll(...)` | 使用同一個仲裁與展示引擎，支援作用域內去重、限速、逾時和結果回呼。 |
| `LIVE_SURFACES` · `LiveSurfaceService` | `view()`、`reserve(...)`、`diagnostics()` | 讀取已繪製幾何、預約避讓區域及取得近期 CPU 測量。 [即時表面接入](live-surfaces.md) |

跨平台的純 Java 消費者可使用 `UIPlatform.KEY`、`NativeInput` 和內核契約。引用 `UIApis` 的 Minecraft 服務時，依實際簽名使用該版本的 `Screen`、`GuiEventListener` 或 `ResourceManager`。可在同一畫面保留 `UiScreenBuilder`、原生按鈕、旁白及傳統渲染回呼。 [遷移指南](migration-guide.md)

### 文字自適應

`LayoutApi.fitText` 在客戶端以原版字體量測一行 `FormattedText`。寬高使用 GUI 單位，像素比例使用原版視窗的 `getGuiScale()`。

```java
import com.dishanhai.sh_better_ui.kernel.api.text.TextFit;

var layout = context.engine().require(UIApis.LAYOUT).layout();
var size = new TextFit.Scale(1.5f, 1f, window.getGuiScale());
var line = layout.fitText(font, label, availableWidth, availableHeight, size);
```

以 `line.scale()` 設定繪製比例，再繪製 `line.glyphs()`。`width()` 和 `height()` 已包含比例，可用於對齊。空間不足時先在最低比例允許的範圍內對齊物理像素，仍放不下便保留文字樣式並加上省略號。比例為零表示沒有可繪製的行。保留原始 `label` 用於旁白或提示，並可透過 `truncated()` 判斷是否省略。

需要平滑過渡時，每個標籤在畫面或元件生命週期內保留一個 `ShBetterUiApi.motion().textLayout()` 狀態，在繪製回呼用 `LayoutApi.drawFittedText`。回傳值仍是最終量測，提示和旁白應繼續使用原始文字。

```java
// 建立一次，並在同一個標籤的繪製回呼中重用。
var motion = ShBetterUiApi.motion().textLayout();
var fitted = layout.drawFittedText(graphics, font, label, bounds, size,
        Horizontal.CENTER, motion, color, false, preferences);
```

同一段文字改變比例或位置時，會在 160 毫秒內平滑插值。省略、換行或可見性改變時，舊排版與新排版在當前文字區域內交叉淡化。再次調整時從當前畫面繼續。更換文字、第一次繪製、高對比與減少動效會直接使用最終排版。一般 SHBUI 按鈕的換行使用同一套過渡。

多行面板可用純 Java 的 `TextFit.selectRows(rows, priority, fits, notice)`。整數優先級越小越先保留，同級依原始順序取捨，結果仍按原始順序排列。`fits` 應使用固定的可用空間量測候選列。可傳入一行省略提示預留空間，再用 `omitted()` 生成實際提示。自定義渲染器也可直接用 `TextFit.Scale.within(limit)` 計算比例。

### HUD 與本機電腦狀態

在 `UIModule.start` 以 `context.engine().require(UIApis.HUD).mount(context, draw)` 掛載 HUD。在回呼內使用 `HudService.Frame` 的 GUI 座標、圖形上下文及顯示偏好。開啟畫面、載入遮罩或使用 F1 隱藏 GUI 時會暫停回呼。可用 `UiHudPanel` 測量並繪製帶圖示的通透 Liquid Glass 面板。位置編輯器可共用面板及輕微的字形柔邊。高對比模式或關閉背景模糊時使用主題底板。提早移除時，在 UI 執行緒關閉掛載句柄。

UUK 模組可用 `context.engine().require(UIApis.COMPUTER_STATUS).read(metrics)`，其他客戶端程式碼可用 `ComputerStatusApi.read(metrics)`。兩者共用快取，讀取只排程所選的 `MEMORY`、`CPU`、`GPU`、`DISK`。首次取樣與平台未提供的讀數以空值表示。容量以位元組回傳，CPU 與 GPU 使用率為 0–1。磁碟對應遊戲資料夾所在的磁碟區。GPU 提供顯示卡清單與可取得的顯存容量，Windows 可另外讀取最忙的 GPU 引擎。硬體資料只在本機使用。

### 點陣與音樂律動

Kernel 的預設 `UiScreen` 背景和宣告式全頁畫面會繪製使用者選擇的全域背景，預設為滿版點陣。若頁面已有 `onRender`，可用 `UiScreenBuilder.themeBackground()` 在內容裁切前繪製全域背景，或用 `dotMatrixBackground()` 指定點陣底板。滑鼠位於活動畫面時，點陣透鏡會放大游標附近的點，按住左鍵時加強效果。

在主控面板「顯示偏好」開啟「音樂律動」後，點陣和原生 `SCREEN`／`HUD` 繪製回呼共用當幀的節拍強度。自訂效果可在回呼內讀取 `RenderService.Context.musicPulse()`（0–1）。採集使用 Windows 系統輸出。關閉開關或退出 Minecraft 會停止採集，套用減少動效或高對比時則暫停。

### Liquid Glass

在模組描述宣告 `UIPermission.REGISTER_RENDER_PASS`，以已掛載的 Screen 樹或元件作用域提交材質。在啟動期間用 `find(GlassService.KEY)` 探測服務。GPU 能力和資源於首次光學繪製時檢查。缺少可用的場景捕獲或 Shader、使用者關閉背景效果，或選擇 `FALLBACK` 時，繪製保護前景對比的實色底板。

```java
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.RenderService;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.TaskHandle;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassDraw;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassRegion;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;
import net.minecraft.network.chat.Component;
import java.util.List;

final class StatusGlass {
    static TaskHandle draw(ModuleContext module, ScopeHandle node, Component label) {
        var ui = module.engine();
        var material = ui.glass().material(GlassVariant.REGULAR);
        var draw = new GlassDraw(GlassRegion.capsule(24, 18, 120, 24), material);
        return ui.require(UIApis.RENDER).submit(module, node, RenderService.Surface.SCREEN,
                RenderService.Layer.OVERLAY, 0, pass -> {
                    pass.glass(List.of(draw));
                    pass.text(label, 34, 26, material.foreground().argb());
                });
    }
}
```

- 所有玻璃命令依呼叫及批次順序繪製。每個原生 pass 在首個光學命令之前捕獲一次目前畫面。先提交共同的背景，再提交材質和前景。同一 pass 的後續玻璃會採樣同一份背景。
- 幾何與折射、厚度、模糊半徑均使用 GUI 單位，隨原生縮放與平移／正向縮放變換。支援圓角、膠囊、橢圓（等寬高為圓形）和連續角。用 `GlassRegion.contains` 驗證命中，陰影位於互動區域外。
- `REGULAR` 適用於文字及一般控件，`CLEAR` 適用於豐富背景。可用 `new GlassDraw(region, material, GlassDraw.Profile.SNELL, foregroundBounds)` 選用 Snell 斜邊光學。傳入含少量留白的 `GlassDraw.ForegroundBounds` 可局部保護文字對比，座標與區域同為 GUI 單位。沒有文字時傳入 `null`，高對比模式仍保護整塊玻璃。原有建構子使用 `LEGACY`。
- Snell 的 `CLEAR` 預設保留平坦中央的背景色，斜邊有折射與高光。`BALANCED` 和 `ULTRA` 增加邊緣 RGB 色散，`PERFORMANCE` 使用單次背景採樣。Shader 逐區域採樣背景，GPU 僅向 Java 提供資源及繪製計數。 [上游版本與授權](../src/main/resources/META-INF/third-party/liquid-glass-optics/NOTICE)
- Snell 的 `transmission` 控制背景透色（0–1），`scattering` 控制磨砂混合量（0–1），`blurRadius` 控制取樣範圍（0–32 GUI 單位），`refraction` 控制斜邊位移上限（0–32 GUI 單位）。`refraction=0` 時位移與色散歸零。`scattering=0` 或 `blurRadius=0` 時取清晰背景。大範圍磨砂按需使用同一張場景紋理的 mip 層級，各個材質繼續使用同一份場景捕獲。基底與 mip 層級合計上限 32 MiB。預算不足時使用較小範圍的取樣。
- `GlassOptics`、`GlassRegion` 和 `GlassDraw` 可重用。原生繪製僅在作用域內的回呼中有效，背景、Shader 和 VAO 由內核持有並於重載或停止時釋放。GLSL 以 OpenGL 3.2 為底線，實際可用性由 Shader 編譯及幀緩衝檢查確認。
- 用 `GlassDraw.interpolate(target, progress)` 將同一軌道的進度套用到輪廓、光學、高光及文字範圍。變體、品質、前景、光學模型和形狀族須一致，兩端須同時提供或同時省略文字範圍。圓角矩形與膠囊屬於同一族。控件可用同一份當前 `GlassRegion` 做繪製與輸入命中。

## 內核視圖

| 入口 | 操作 | 執行緒與權限 |
| --- | --- | --- |
| `registry()`、`capabilities()`、`lifecycle()` | 服務／能力解析、狀態與快照 | 公開視圖可查詢。詳細生命週期診斷需要授權。 |
| `scheduler()`、`invalidations()` | 提交有界工作、讀取 `SchedulerSnapshot`、標記節點 | 可從背景提交不可變工作。失效標記及 UI 回呼由客戶端執行緒處理。 |
| `configs()`、`states()` | 定義、讀取、觀察和提交交易 | 讀取發布的快照。寫入與訂閱使用已驗證的模組和作用域。 |
| `commands()` | 登記型別化處理器、同步／非同步提交、交易 | 以呼叫模組的身分驗證。交易內只能暫存更新、失效和後續任務。 |
| `events()` | 同步傳播、佇列發布、訂閱 | 訂閱和同步傳播在客戶端執行緒。回呼持有的事件控制權只在該次傳遞有效。 |
| `permissions()`、`ownership()` | 判定權限、取得宣告資源的租約 | 由內核核對目標、模組來源、資源類別和授權。 |

執行緒與提交規則詳見 [調度](scheduler.md) · [設定與狀態](configuration.md) · [權限與租約](permissions.md)。

## 事件傳播

`UIEventType<T>` 宣告型別和是否可取消。用 `events().subscribe(type, scope, phase, priority, listener)` 綁定訂閱，關閉句柄或作用域時解除。`emit` 在 UI 執行緒同步回傳 `EventResult`，`post` 將不可變 payload 排到 `EVENT_DISPATCH` 並回傳工作句柄。事件帶來源、目標及目前經過的節點。

| 階段或操作 | 行為 |
| --- | --- |
| `CAPTURE → TARGET → BUBBLE` | 先由祖先到目標，再由目標的父節點往上。相同節點與相位的回呼依優先級及登記順序執行。 |
| `stopPropagation()` | 執行完當前節點的回呼後結束傳遞。 |
| `stopImmediatePropagation()` | 結束傳遞，略過當前節點尚未執行的回呼。 |
| `preventDefault()` | 設定可取消事件的 `defaultPrevented`，由發送方處理預設操作。 |
| `markHandled()` | 記錄已處理，繼續傳遞及預設操作。 |
| `consume()` | 結束傳遞。可取消的原生事件由宿主再核對活動作用域及 `BLOCK_VANILLA_INPUT`，決定是否阻斷。 |

同步傳遞有巢狀深度和總交付次數上限。回呼例外寫入診斷，後續回呼按原傳遞條件繼續。需在回呼後修改資料時，將工作提交至 `STATE_MUTATION`。 [原生路由](platform-compatibility.md#原生路由)
