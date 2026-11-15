<!-- 作者：山海／dishanhai -->
# 第三方模組開發

## 讓 Loader 發現模組

在消費模組的資源中加入 `META-INF/sh_better_ui.modules`。每一行以空白分隔宣告者的 Loader mod ID 和實作類全名。檔案使用 UTF-8，空行和以 `#` 開頭的行會略過。

```text
# examples of module declarations
example_mod com.example.client.DemoModule
```

宣告和實作類需同屬該 mod 檔案及其具名 Java `Module`。實作類須公開、實作 `UIModule`、提供公開無參數建構式。`ModuleDescriptor.id()` 的 namespace 須與該行的 mod ID 相同。每個描述檔上限 16,384 bytes，整個客戶端至多接受 128 個外部候選。Loader 安裝時讀取這個固定檔名，資源包重載只會更新資源。 [模組發現原始碼](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/NativeModuleSources.java)

## 一個具型別的服務

將下列類別放到 `src/main/java/com/example/client/DemoModule.java`，並將描述檔放到 `src/main/resources/META-INF/sh_better_ui.modules`。實際專案的 Loader metadata 必須宣告 SH-Better-UI 為依賴。

```java
package com.example.client;

import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.ServiceDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.ServiceKey;
import com.dishanhai.sh_better_ui.kernel.api.UIModule;
import com.dishanhai.sh_better_ui.kernel.api.UIService;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;

public final class DemoModule implements UIModule {
    private static final UiVersion VERSION = UiVersion.parse("1.0.0");
    public static final ServiceKey<Counter> COUNTER =
            new ServiceKey<>(UiId.parse("example_mod:counter"), Counter.class, 1);

    public interface Counter extends UIService {
        int initialValue();
    }

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder().id("example_mod:demo").version(VERSION).build();
    }

    @Override
    public void register(ModuleContext context) {
        context.services().register(new ServiceDescriptor<>(COUNTER, VERSION,
                ServiceDescriptor.Visibility.PUBLIC, 0, false), () -> 0);
    }

    @Override
    public void start(ModuleContext context) {
        context.engine().require(COUNTER);
    }

    @Override
    public void stop(ModuleContext context) {
        // This module's counter holds no external resources.
    }
}
```

## 登記、啟動與停止

1. `descriptor()` 提供 `UiVersion`、引擎契約版本、支援平台、依賴、衝突、能力、權限、設定 schema 參照和資源宣告。模組版本和服務版本可獨立演進。一般擴充使用預設 `EXTENSION`。`CORE` 階段由宿主保留。`ExecutionAffinity` 是描述欄位，三個生命週期回呼由 UI 執行緒呼叫。背景工作另以 `UITask.background` 提交。
2. `register(context)` 用 `context.services()` 登記。回呼結束後 registrar 失效。`RegistrationHandle.status()` 分別回報 `ACTIVE`、`SKIPPED` 或 `REVOKED`。主動撤銷只在登記回呼內可用。`PUBLIC` 對所有視圖可見，`MODULE_PRIVATE` 僅對登記模組可見，`KERNEL_PRIVATE` 由內建核心登記和查詢。
3. `start(context)` 使用 `context.engine()`。此時可查詢自己的服務與已啟動的依賴。定義 `ConfigKey` 和 `StateKey` 也在這個階段。要留待操作時使用的 context 可存入模組實例，向外提供窄介面，避免轉交整個授權視圖。`UnifiedUI.get()` 要等引擎公開後才可用。
4. `stop(context)` 開始時模組作用域已失效。以先前保存的參考清理外部資源，清空引用。可在建立資源時 `context.scope().track(resource)`，交由作用域釋放。`register` 失敗會撤銷該次登記並釋放作用域。不要把已失效的 scope 傳給下一次開啟的頁面。

服務 ID 在整個登記表中唯一，登記時同時驗證實例型別和契約。重複服務預設 `REJECT`。`KEEP_EXISTING` 只接受契約相符的競爭，並回傳 `SKIPPED`。`REPLACE_IF_ALLOWED` 或 `SELECT_BY_PRIORITY` 還需替換授權、相符的可見性與可替換的原服務。優先級策略亦要求新服務可替換。登記結束時凍結。查詢時依仲裁次序選擇已啟動的提供者，模組也可在自己的 `start()` 中查詢本模組的登記。替換者初始化失敗或停止時，宿主撤銷其登記，仍存活的前任可恢復為 `ACTIVE`。跨提供者持有的服務需在各自生命週期內使用，切換後可重新解析同一個鍵。

## 依賴與降級

使用 `.dependency(moduleId, versions, required)` 指定模組相依，或用 `.requiredCapability(...)`、`.optionalCapability(...)` 指定功能相依。相容提供者會先於消費者啟動。缺少必要依賴時擴充會被拒絕或阻擋。缺少選用依賴時檢查 `capabilities().resolve(...)` 或 `find(...)`，走本地備援。對可卸除提供者，請在下一次使用時確認有效性。 [能力系統](capability-system.md)

## 自動發現開發者預覽

讓描述檔中宣告的用戶端模組實作 `DeveloperPreviewProvider`，並從 `developerPreviews()` 傳回 `List<PreviewSpec>`。預覽 ID 的命名空間須與模組 ID 一致，`titleKey` 指向模組語言檔，場景則由 `PreviewSpec` 的工廠建立。UUK 在模組成功啟動後將宣告加入控制項或材質圖庫的目錄和搜尋結果，模組作用域關閉時撤銷。選取預覽時才呼叫場景工廠。

[範例模組及使用步驟](../examples/kernel-extension/README.md#自動發現開發者預覽)

NeoForge 26.1.2 和 26.2 使用同一個描述檔與 `DeveloperPreviewProvider`。用對應版本的 SH-Better-UI JAR 編譯；場景使用 26.x 的 `PreviewContext`、`GuiGraphicsExtractor` 和 `controls()`。 [26.x 範例與驗收命令](NEOFORGE_26_GALLERY_GAPS.md#第三方预览自动发现)

## 自動發現伺服器能力（NeoForge 1.21.1）

在模組 JAR 中提供 `META-INF/sh_better_ui.automation`，每行為 Loader mod ID 和公開實作類全名。格式、大小上限和模組來源驗證沿用用戶端描述檔規則。實作類須提供公開無參數建構式、實作 `ServerCapabilityModule`，並可在獨立伺服器載入。

伺服器啟動時，UUK 呼叫 `register(MinecraftServer, Registrar)`。模組 ID 和能力 ID 必須屬於宣告模組的命名空間，能力描述須使用相同的提供者 ID 和版本。重複的模組 ID 會被拒絕。註冊失敗會撤銷該模組已註冊的能力，伺服器停止會釋放全部能力。Registrar 的有效期到該次呼叫結束。

伺服器端消費者以 `ServerCapabilities.forPlayer(player)` 取得目前在線玩家的目錄，再用 `find(id, contractVersion)` 和 `invoke(reference, request)` 查詢及執行能力。提供者以 `caller.playerId()` 識別呼叫者，應在服務端校驗對目標資源的實際權限。玩家離線時目錄失效。目前通用頁面生成和腳本編排仍在開發中。

## 可執行範例

`shbui_kernel_example:controls` 提供型別化 `CounterService` 和自訂能力，透過 `SCREENS` 掛載原生頁面，使用 `THEME` 讀取主題並套用自己的 `STEP` 設定。缺少系統螢幕閱讀器能力時沿用 Minecraft 按鈕旁白。Forge 與 NeoForge 範例分別產出消費模組 JAR，使用對應的 SH-Better-UI 作為獨立依賴。 [範例專案與命令](../examples/kernel-extension/README.md) · [平台相容性](platform-compatibility.md)
