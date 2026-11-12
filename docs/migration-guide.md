<!-- 作者：山海／dishanhai -->
# 從現有 UI API 遷移

`ShBetterUiApi`、`UiApiContext`、`UiScreenBuilder` 和 KubeJS 的 `SHUI` 可繼續使用。已編譯的消費模組仍依賴同一 mod ID 及原有簽章。要加入作用域、可取消任務、狀態交易或模組服務時，可以只遷移用到這些能力的頁面。

## 選擇遷移層次

| 目前用法 | 接入內核時可用的入口 |
| --- | --- |
| `ShBetterUiApi.materials()`、`settings()` 和主題 TOML | `ui.find(UIApis.THEME)` 讀同一主題的快照。自己節點的局部 token 用 `ThemeService.scope(context, node)`。既有全域設定保存用原入口。 |
| `ShBetterUiApi.screen()`、原生 `Screen` | 以 `ScreenService.create` 標記 builder 產生的頁面，或直接使用自己模組的 `Screen`。在活動期間 `mount`，原生按鈕、返回和渲染維持現有方法。 |
| `ShBetterUiApi.motion()` 或既有 Spring、時間軸 | 保留時間軸與組合工具。需要單一屬性寫入者及離頁取消時，以 `AnimationService.bind` 綁定已宣告的屬性。 |
| 直接量測排列、碰撞和輸入 | 用 `LayoutService.bind`、`CollisionService.mount` 將已有計算接到髒節點，並以 `InputService`、`FocusService` 選擇活動目標。 |
| 自行維護提示條、停靠、懸停計時和收合動畫 | 透過 `UIApis.WINDOWS` 在原有 Screen 作用域註冊 `MiniWindowSpec`，將既有 ViewModel 接到提示條與內容工廠。受管兄弟節點需要讓出空間時可讀 `LayoutService.dockContentArea`。 [停靠接入](ADAPTIVE_DOCK.md) |
| `UiApiContext.manage`、`ShBetterUiApi.client().run(...)` | 仍可用於現有 Screen 與擴充。需跨服務協調時，用 `ScopeHandle.track` 及 `UIScheduler.submit` 綁定世代和工作。 |
| `ShBetterUiApi.extensions()` | 既有上下文工廠繼續用於依頁建立的物件。需要其他模組查詢的具型別服務時，在 `UIModule.register` 登記 `ServiceKey<T>`。 |
| `SHUI.registerExtension` | 仍由 KubeJS 清理腳本世代。若要讓 Java 模組消費新的服務，另提供 Loader 發現的 Java `UIModule`。 |

## 保留原生控件，接入可取消工作

下例將同一按鈕回呼由立即執行改成可合併的 `STATE_MUTATION` 工作。兩種方式都沿用原生控件和返回。請在 `UIModule.start` 保存自己的 `ModuleContext`，從後續的客戶端操作開啟頁面。`refresh` 應為客戶端短操作，阻塞計算使用背景工作。`example_mod.ui.*` 由消費模組提供翻譯。

```java
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UIPhase;
import com.dishanhai.sh_better_ui.kernel.api.UIPriority;
import com.dishanhai.sh_better_ui.kernel.api.UITask;
import com.dishanhai.sh_better_ui.kernel.api.UiId;

final class RefreshPage {
    private static final UiId TREE = UiId.parse("example_mod:refresh_page");
    private static final UiId REFRESH = UiId.parse("example_mod:refresh");
    private ScopeHandle tree;

    static void openLegacy(Runnable refresh) {
        ShBetterUiApi.screen("example_mod.ui.title")
                .onInit(page -> addRefresh(page, refresh))
                .open();
    }

    void openScoped(ModuleContext module, Runnable refresh) {
        var ui = module.engine();
        var screens = ui.require(UIApis.SCREENS);
        var builder = ShBetterUiApi.screen("example_mod.ui.title").onInit(page -> {
            if (tree == null || !tree.isOpen()) tree = screens.mount(module, page, TREE);
            var target = tree;
            addRefresh(page, () -> ui.scheduler().submit(UITask.ui(target, UIPhase.STATE_MUTATION,
                    UIPriority.USER_INTERACTION, refresh).replacing(REFRESH)));
        });
        if (ui.capabilities().supports(UIApis.DYNAMIC_THEME)) builder.dynamicTheme();
        screens.create(module, builder).open();
    }

    private static void addRefresh(UiScreen page, Runnable refresh) {
        var area = page.viewport();
        page.button(area.x() + 12, area.y() + 12, 100, 20, "example_mod.ui.refresh", refresh);
    }
}
```

每次新建一個 `RefreshPage` 實例。原生 `onInit` 在 GUI Scale 或尺寸改變時重建按鈕，保留仍有效的樹。離開時平台關閉樹，取消尚未執行的刷新。背景回執和受管資源可使用同一範圍。 [生命週期](lifecycle.md) · [調度](scheduler.md)

## KubeJS 腳本

腳本可沿用 `SHUI` 在 `kubejs/client_scripts/` 打開和關閉頁面。下例啟用共用動態主題，控件仍使用 Minecraft 的原生焦點和旁白。`SHUI` 管理腳本重載。具型別內核登記由 Java `UIModule` 綁定 Loader 身分。

```javascript
global.openShuiDone = () => {
  SHUI.screen('gui.done')
    .dynamicTheme()
    .onInit(screen => {
      const area = screen.viewport()
      screen.button(area.x() + 12, area.y() + 12, 100, 20,
        'gui.done', () => screen.onClose())
    })
    .open()
}
```

透過 `SHUI.registerExtension` 建立的工廠及以 `SHUI.screen` 打開的頁面由腳本週期清理。直接建立的 `SHUI.context()` 由腳本負責關閉。使用完整內核範例時，採對應 Loader 的獨立依賴及資源描述檔。 [KubeJS 接入](KUBEJS.md) · [第三方範例](../examples/kernel-extension/README.md) · [平台相容性](platform-compatibility.md)
