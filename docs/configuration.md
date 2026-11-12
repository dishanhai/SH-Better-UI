<!-- 作者：山海／dishanhai -->
# 設定、狀態與命令

用設定保存偏好和可覆寫的預設，用狀態保存目前業務值，用命令請求修改，用事件通知已發生的事。從 `context.engine()` 取得操作視圖。可跨執行緒讀取已發布的值和快照。定義、提交交易和訂閱由客戶端執行緒持有。幀內的資料提交集中在 `STATE_MUTATION`。

## 設定 schema 與覆蓋

`ConfigKey<T>` 宣告 ID、型別、預設值、可寫層、可變性、持久化分類、版本、說明、驗證器、複製器及遷移函式。以模組 namespace 定義，在 `start` 呼叫 `configs().define(KEY)`。呼叫端共用登記過的鍵實例。`ConfigKey.of(...)` 適用不可變值，其預設為 `RUNTIME`、`SESSION`、版本 1。`STARTUP_ONLY` 的寫入僅在引擎初始化期間允許。驗證、遷移和複製函式須為純函式，複製器還須支援背景讀取。

優先級由低至高。每個鍵須明列可寫的層。對同一層的多個祖先取最近的值，之後回退到較低層。

| 層 | 位置與驗證 |
| --- | --- |
| `ENGINE_DEFAULTS` | `ConfigKey.defaultValue()`，由 schema 提供。 |
| `MODULE_DEFAULTS` | 引擎根，限 schema 擁有者或宿主。 |
| `RESOURCE_THEME` | 引擎根，需要全域設定與全域主題授權。 |
| `USER_GLOBAL` | 引擎根；自己的已綁定持久化鍵可直接寫入，其他鍵需要全域設定授權。 |
| `SCREEN` | `SCREEN` 作用域，核對所有者。 |
| `COMPONENT` | `COMPONENT` 作用域，核對所有者。 |
| `RUNTIME_OVERRIDE` | 指定的有效作用域。引擎根需要全域設定授權。 |

`set(key, value)` 寫入目前模組作用域的 `RUNTIME_OVERRIDE`。由 `ScreenService.mount` 取得的原生掛載是模組擁有的 `UI_TREE`，可在樹上寫 `RUNTIME_OVERRIDE`，也可在子組件寫 `COMPONENT`。原生 `SCREEN` 由平台持有。樹掛載在平台的原生 Screen 下，與模組作用域有清理連結。若需要自己的原生頁面預設，使用樹上的覆寫或讀取模組預設後明確套用。

```java
import com.dishanhai.sh_better_ui.kernel.api.ConfigKey;
import com.dishanhai.sh_better_ui.kernel.api.ConfigLayer;
import com.dishanhai.sh_better_ui.kernel.api.ConfigScope;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import java.util.Set;

final class PageSettings {
    static final ConfigKey<Integer> STEP = ConfigKey.of(UiId.parse("example_mod:step"),
            Integer.class, 1, Set.of(ConfigLayer.COMPONENT, ConfigLayer.RUNTIME_OVERRIDE),
            step -> step >= 1 && step <= 10);

    static void define(ModuleContext context) {
        context.engine().configs().define(STEP);
    }

    static int setStep(ModuleContext context, ScopeHandle component, int value) {
        var config = context.engine().configs();
        config.beginTransaction().set(new ConfigScope(component, ConfigLayer.COMPONENT), STEP, value).commit();
        return config.get(component, STEP);
    }
}
```

## 交易、通知與持久化

- `beginTransaction()` 擷取基線。以 `set`、`clear`、`load` 暫存同一批修改，再呼叫 `commit()`。型別、允許層、權限、作用域與基線版本都會驗證。失敗保留先前發布值，過期基線會拋出 `ConcurrentModificationException`。重新讀取後建立新交易。
- `observe(scope, key, listener)` 以該作用域的有效值發出 `ValueChange<T>`。多次暫存同一鍵以最終有效值通知。關閉觀察的作用域或模組會移除訂閱。監聽器可排隊或提交下一批，內核診斷循環因果並限制連續通知批次。快照只讀，保留取得時的版本和值。
- `ConfigTransaction.load(...)` 在 `USER_GLOBAL` 層載入 `Persistence.USER_GLOBAL` 的鍵，逐版應用同型別遷移再驗證。控制面板與 `ShBetterUiApi.settings()` 讀寫同一個 `UIApis.CLIENT_SETTINGS`。具備 `MODIFY_GLOBAL_CONFIG` 的模組可透過 User Global 交易更新它。資源包主題沿用主題解析與資源重載。 [設定入口](API_REFERENCE.md#全库客户端主题) · [平台相容性](platform-compatibility.md)

### 共用持久化

兩端原生客戶端將儲存目錄接到遊戲實例的 `config/sh_better_ui/modules/`。第三方在 `start` 中先 `define(key)`，再呼叫 `persist(key, codec)`；鍵必須宣告 `Persistence.USER_GLOBAL` 並允許 `USER_GLOBAL` 層。只允許 schema 擁有模組綁定，回傳的訂閱可停止後續保存，不會刪除磁碟資料。模組停止時自動解除綁定。未配置儲存的獨立核心宿主會明確拒絕綁定。

在主控面板的「顯示偏好」選擇自訂主題，開啟背景分組的設定並按「完成」，即可預覽該全域背景。按「套用」會將背景 ID 與參數寫入同一筆 `UIApis.CLIENT_SETTINGS`。舊設定檔預設使用點陣。高對比模式或背景提供者未載入時，畫面會回到預設背景。

內建的 `CLIENT_SETTINGS` 由 Kernel 保存於 `config/sh_better_ui/modules/sh_better_ui.properties`。啟動時若該鍵尚無持久化值，會從 Loader 客戶端 TOML 匯入。停用 Kernel 時，原有 TOML 入口繼續提供設定和保存。

`ConfigCodec` 提供字串、整數、長整數、浮點數、嚴格布林及 enum 工廠，自訂型別用 `ConfigCodec.of(encode, decode)`。編解碼器與 schema 驗證器一樣須為純函式。讀取時核對儲存型別及版本，先解碼，再逐版遷移和驗證；失敗不發布值，也不覆寫原檔。`STARTUP_ONLY` 的載入仍只能在初始化階段執行。

`userGlobal()` 提供交易所需的引擎全域層參照，不授予建立或關閉引擎作用域的權限。模組可以交易修改自己已綁定的持久化鍵，不需申請修改其他模組設定的權限；其他全域鍵仍需 `MODIFY_GLOBAL_CONFIG`。以 `beginTransaction().set(configs.userGlobal(), key, value).commit()` 保存值，以 `clear` 撤回該層覆寫並刪除對應磁碟條目。其他層的暫存覆寫不會被保存。

每個 namespace 保存一個 `.properties` 檔，包含鍵的版本、Java 型別及編碼值。未知鍵保留，以便可選模組暫時移除後重新載入。輸入及輸出各限 1 MiB；編碼與大小驗證先於記憶體交易發布。背景單一寫入器按 namespace 合併尚未寫入的快照，使用同目錄暫存檔、檔案同步及原子替換。原子性以單一 namespace 檔為單位，不宣稱跨檔磁碟交易。

記憶體 `commit` 成功不代表落盤完成。`persistence()` 提供提交／完成序號、寫入中狀態與最近失敗；`flush()` 回傳 `CompletionStage<Void>`，完成才表示其涵蓋的寫入已完成。寫入失敗保留先前檔案並記錄診斷；再次 `flush()` 會以目前最新快照重試失敗 namespace。完成回呼在背景寫入器執行，不在內部鎖內執行；不要在其中直接改 UI，應交回中央調度。引擎停止時等待待保存工作，超時或失敗仍留下診斷，不冒充成功。

GraalVM 21 的獨立程序驗證已完成首次保存 7、重新啟動讀回 7、schema v1→v2 遷移後讀回並保存 8，未授予一般全域修改權限。實際檔案位於 `build/reports/config-persistence-20261110/persist_demo.properties`。另以同一 GraalVM 21 透過 JShell 直接呼叫磁碟層，使用非空目錄阻擋原子替換：`flush` 如預期失敗且完成序號維持 0，移除阻擋後再次 `flush` 成功，提交／完成序號均為 3、失敗狀態清空，未知 properties 鍵仍保留。日誌為 `build/reports/config-disk-boundaries-java21.utf8.log`。該次手寫損壞檔輸入未轉義 properties 的冒號，不能用來證明不完整條目拒絕；後續以 `Properties.store` 產生正確鍵名的獨立重驗已確認拒絕，見 `build/reports/config-disk-incomplete-java21.log` 的 `INCOMPLETE_ENTRY_REJECTED_VERIFIED`。這些直接磁碟層結果不替代完整模組權限與原生客戶端驗收；兩端生產客戶端的完整持久化驗收仍待進行。 [平台相容性](platform-compatibility.md)

## 狀態與派生資料

使用 `states().define(StateKey)` 和 `states().beginTransaction()`。`GLOBAL` 的值隨鍵的擁有模組，`SCREEN` 需要 `SCREEN`，`COMPONENT` 需要 `COMPONENT`，`EPHEMERAL` 存在指定作用域。將原生掛載的暫存值放在 `UI_TREE` 時可用 `EPHEMERAL`。關閉相應作用域會移除暫存值及訂閱。

`derive(scope, result, dependencies, compute)` 註冊純計算。依賴由同一擁有者宣告，來源可用全域或同一作用域值。派生函式只能讀宣告的輸入。依賴改變後重算受影響的派生圖，註冊時拒絕循環。派生值以輸入更新，不能直接寫入。設定與狀態各有發布版本和交易。

## 命令與副作用

用 `CommandKey<T>` 和 `UICommand<T>` 定義操作。`commands().register(...)` 在模組的 namespace 登記處理器，可要求權限。`execute(command)` 同步提交，`submit(command, timeoutNanos)` 回傳 `CommandHandle`，經 `STATE_MUTATION` 執行。命令處理器收到目標參照和純資料。交易以呼叫者權限處理，提供者的身分不會轉給呼叫者。

`commands().transaction(tx -> …)` 可在一個提交內暫存狀態更新、髒旗標及後續任務。成功才發布狀態，並回傳 `CommandResult` 與每個後續 `TaskHandle`。`afterCommit` 接受獨立任務，沒有前置依賴或替換鍵。若外部副作用稍後失敗、逾時或被取消，查看任務句柄狀態。已發布的狀態版本仍然有效。 [調度](scheduler.md) · [權限](permissions.md)
