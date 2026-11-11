<!-- 作者：山海／dishanhai -->
# 能力系統

服務是可呼叫的型別介面，以 `ServiceKey<T>` 取得。能力是功能契約，以 `CapabilityKey` 查詢。提供者可組合數個服務達成同一功能。即使能力與服務共用相同 `UiId`，使用端仍須分別驗證能力條件和所需服務。

## 版本與解析

模組透過 `ModuleDescriptor.builder().providedCapability(key, version)` 宣告供給。完整的 `CapabilityOffer` 可指定優先級和 `fallback`。`UiVersion` 接受三段非負整數，例如 `1.0.0`。`VersionRange.between(a, b)` 使用含下界、不含上界的區間，`atLeast(a)` 可省略上界。

解析先篩選版本及適用的提供者，依主要實作先於 fallback、優先級由高至低、版本由高至低、提供者 `UiId` 順序選擇。同一請求只能把一個鍵列為必要或選用。`available()` 列出已啟動模組的所有供給，`resolve()` 傳回每個鍵的選擇與 `requiredMissing()`、`optionalMissing()`。

| 狀態 | 行為 |
| --- | --- |
| `AVAILABLE` | 有符合條件的主要供給。 |
| `DEGRADED` | 只找到相容的 fallback。`supports(key)` 也傳回 `true`。 |
| `UNAVAILABLE` | 無相容供給。缺少選用能力時可走本地路徑。 |

```java
import com.dishanhai.sh_better_ui.kernel.api.CapabilityRequest;
import com.dishanhai.sh_better_ui.kernel.api.CapabilityResolution;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UIEngine;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.kernel.api.VersionRange;
import java.util.Map;

final class FeatureRequirements {
    static CapabilityResolution check(UIEngine ui) {
        return ui.capabilities().resolve(new CapabilityRequest(
                Map.of(UIApis.DYNAMIC_THEME, VersionRange.between(new UiVersion(1, 0, 0), new UiVersion(2, 0, 0))),
                Map.of(UIApis.SYSTEM_SCREEN_READER, VersionRange.ANY)));
    }
}
```

`supports(key)` 與 `status(key)` 適合只需要可用性時呼叫。有最低版本或必須取得主要實作的需求時，用 `resolve(request)` 的版本條件和 `status(key)`。客戶端提供 `MINECRAFT_NARRATION`，`SYSTEM_SCREEN_READER` 目前沒有內建提供者。從 Minecraft 旁白回呼發布名稱、角色和提示。 [服務清單](public-api.md)

## 系統讀屏橋接評估

現行程式碼的結論是「沒有內建系統讀屏提供者」，而不是「系統未安裝讀屏軟體」。雙端共用 `ClientUiKernel` 的內建服務登記與 `ClientAccessibility`；後者只把名稱和提示交給 Minecraft 的 `NarrationElementOutput`，沒有向作業系統匯出可導覽的 UI 樹，也未將 `Semantics.role` 轉成系統控制項角色。`SYSTEM_SCREEN_READER` 只有能力鍵，沒有內建供給；因此預設仍解析為 `UNAVAILABLE`。使用文字轉語音、Minecraft 旁白，或偵測到 Narrator／NVDA／VoiceOver／Orca 程式，都不能單獨把此能力標為可用。第三方供給必須代表其實際實作，不能拿能力宣告代替橋接。

| 作業系統 | 應評估的系統介面 | Forge 1.20.1 | NeoForge 1.21.1 |
| --- | --- | --- | --- |
| Windows | UI Automation 的 HWND host、fragment provider、控制項 patterns 與事件 | 共用登記路徑無提供者；遊戲內讀屏未驗證 | 共用登記路徑無提供者；遊戲內讀屏未驗證 |
| macOS | AppKit 的 `NSAccessibilityElement`、角色、動作與通知 | 無提供者；未在 macOS 執行驗證 | 無提供者；未在 macOS 執行驗證 |
| Linux | AT-SPI2 的 Accessible、Component、Action、Text、Value 及狀態／焦點事件 | 無提供者；未在 Linux 執行驗證 | 無提供者；未在 Linux 執行驗證 |

Microsoft 明確要求自訂控件實作 provider，否則僅憑視窗 handle 能取得的資訊不足以表示內部控件。Apple 對非 AppKit 自訂元素建議使用 `NSAccessibilityElement`，並提供資訊、動作與通知；不要採用其不再建議的舊 key-based informal protocol。Linux 的 AT-SPI2 同樣包含物件、幾何、動作、文字、數值與事件介面，而不是只有說話入口。參考：[Microsoft UI Automation Providers](https://learn.microsoft.com/en-us/windows/win32/winauto/uiauto-providersoverview)、[Apple NSAccessibilityProtocol](https://developer.apple.com/documentation/appkit/nsaccessibilityprotocol)、[AT-SPI2 介面](https://gnome.pages.gitlab.gnome.org/at-spi2-core/libatspi/)。

目前 `Semantics` 只有角色、名稱與提示，尚不足以建立完整系統橋接。正式提供者至少還須處理：穩定且隨 scope 世代失效的元素身分；父子結構、可見性、裁剪及 GUI／實體螢幕座標；啟用、焦點、選取與數值狀態；文字與編輯動作；模態開關的焦點限制及恢復；更新合併與銷毀通知。系統回呼不得直接在任意執行緒操作 Minecraft，應讀取不可變快照，將獲准動作交回 UI 執行緒並再次核對世代。密碼或敏感文字不應匯出；關閉或重載時必須解除原生註冊，讀屏進程失聯不能阻塞幀執行緒。

本輪不新增未經平台驗證的 native 依賴，也不以空實作宣告能力。遵照「不啟動遊戲」要求，只完成來源及介面評估，不能宣稱上表的遊戲內讀屏已通過。未來提供者驗收須分別在兩個 Loader 與對應 OS 上，檢查純鍵盤導覽、名稱／角色／狀態、讀屏動作、GUI Scale、模態、重載、返回與退出，且在缺少提供者或初始化失敗時仍保留 `UNAVAILABLE`。

## 模組順序及生命週期

必要能力在啟動圖中綁定到一個相容提供者。選用能力可增加啟動順序，遇到缺失或循環時記錄診斷並跳過該選用邊。`start` 中只能看見已啟動模組的能力，自己的供給在本模組啟動完成後才可被其他模組解析。宿主移除提供者時，重新解析仍存活的相容供給；有替代提供者的必要依賴方繼續運行，沒有替代供給才遞迴停止。明列的必要模組依賴不會被能力替代。

## 分層動態供給

`context.engine().capabilities().provide(scope, key, offer)` 接受模組自身的 `MODULE`、`CLIENT_SESSION`、`SCREEN`、`UI_TREE` 或 `COMPONENT` 作用域，回傳可撤銷的 `Subscription`。模組級動態供給對引擎可見；其他供給只對該作用域及其後代可見，不外溢至平行頁面或元件。以 `capabilities().at(scope)` 取得分層解析器，版本、主要／fallback、優先級及提供者排序沿用同一規則。動態供給是額外供給，撤銷不會撤回描述中宣告的靜態供給。

登記、撤銷和作用域關閉在 UI 執行緒進行，供給變更於下一個幀首安全點成批發布。讀取已發布解析結果不會執行提供者程式碼。沒有變更的幀不重建索引，解析器按發布版本快取分層索引。動態模組供給撤銷後也重新檢查必要能力，失去所有相容提供者的模組進入故障清理；選用缺口由使用端降級。

`observe(scope, request, listener)` 比較所觀察作用域的完整 `CapabilityResolution`，僅在選定提供者、版本或缺口改變時發送 `CapabilityChange`，其中包含發布世代及前後結果。每個觀察者以登記時可見的解析結果作為比較基準，不重播登記前的啟動或撤銷；登記時不發送初始通知，使用端先 `resolve(request)` 取得當前結果。通知中登記或撤銷的供給留到下一個安全點；必要依賴檢查也使用當次已發布的供給，回呼中等價替換供給不會因尚未發布的暫態缺口停止消費模組。單一監聽器失敗會記錄診斷而不阻止其他監聽器。觀察作用域或發起模組關閉後不再交付，重新掛載相同 ID 不會恢復舊訂閱。頁面和元件應依 `requiredMissing()` 停用相應操作，依 `optionalMissing()` 選擇本地備援，不必為局部缺口停止整個模組。

GraalVM 21 的無遊戲獨立核心宿主已實際驗證：供給在幀前不可見、下一幀發布；元件供給對平行元件及模組解析器不可見；撤銷到下一幀才發布；模組供給對其他元件可見；關閉觀察元件後不再收到撤銷通知。共收到預期的三次通知，日誌為 `build/reports/dynamic-capabilities-java21-final.log`。首次探針的宿主關閉方法名、第二次的作用域階層使用有誤，均未算通過；最終使用 `MODULE → SCREEN → UI_TREE → COMPONENT` 及宿主 `close()`，未載入 Minecraft。另以三個獨立模組確認跨模組替代：先將 B 的動態模組級供給發布，再停止原靜態提供者 A，必要消費者仍存活並能解析；撤銷最後的 B 供給後，下一幀只停止失去必要能力的消費者，B 自身仍存活。日誌為 `build/reports/dynamic-provider-failover-java21.utf8.log`；其中預期的 required capability withdrawn 故障診斷與消費者清理一致。這些呼叫仍不涵蓋撤權、原生會話及真實畫面接線。

`DYNAMIC_THEME` 宣告能切換主題；具體主題值仍監看主題或資源世代，而不是以能力通知替代資料通知。 [模組開發](module-development.md) · [生命週期](lifecycle.md)
