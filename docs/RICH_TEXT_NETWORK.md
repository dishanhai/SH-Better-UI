<!-- 作者：山海／dishanhai -->
# 富文字同步

PE6 的選用 PLAY 協定將伺服器權威欄位綁定到客戶端自有的富文字文件。每個文件最多 32 個具名欄位，值為 `RichSyncProtocol.Text` 或 `RichSyncProtocol.Number`。以本機模板的穩定 ID 對應欄位，將字串當作字面文字繪製。網路資料模型只允許這兩種值。圖片、標記、樣式與控件回呼由本機模板宣告。

## Loader 接入

Forge 1.20.1 與 NeoForge 1.21.1 分別註冊可選的雙向 PLAY 傳輸，兩端使用同一個 Java 17 訊息、驗證與編解碼核心。缺少對端傳輸時，客戶端維持 `OFFLINE`，宿主可顯示本機模板。下列 API 屬於實驗性接入。

1. 在伺服器執行緒呼叫 `ServerRichDocuments.register(server, documentId, access)`。同一伺服器的文件 ID 必須唯一。關閉回傳的註冊或停止伺服器即撤銷其供應者。
2. 以 `RichSyncServer.Access<ServerPlayer>` 宣告讀取許可、權威欄位快照及可選的編輯回呼。三者都以 Loader 驗證的連線目前玩家為準。讀取回呼提供不可為 null 的欄位與值。回傳 `false` 的編輯應保持遊戲狀態，驗證、寫入與失敗回滾由宿主在同一伺服器執行緒完成。
3. 在客戶端遊戲執行緒呼叫 `ClientRichDocuments.watch(scope, documentId, observer)`。連線同時支援一份活動訂閱。關閉回傳的 `Watch` 或其 `ScopeHandle` 即解除訂閱。連線結束會釋放伺服器端訂閱與暫存回覆。
4. Observer 接收不可變的 `RichSyncClient.View`。將最新版存入畫面狀態，在下一幀的佈局準備時取用。控件用 `watch.edit(key, value, predict)` 提交提案，並以 `pending`、`lastOutcome` 和 `status` 提供回饋。

```java
var document = UiId.parse("example:energy_panel");
var charge = UiId.parse("example:charge_limit");
var registration = ServerRichDocuments.register(server, document,
        new RichSyncServer.Access<>(
                player -> rules.canRead(player),
                player -> Map.of(charge, new RichSyncProtocol.Number(rules.limitFor(player))),
                (player, request) -> request.key().equals(charge)
                        && request.value() instanceof RichSyncProtocol.Number limit
                        && rules.trySetAuthorizedLimit(player, limit.number())));

// 客戶端的 Screen 已開啟 scope，state 在每次佈局準備時讀取目前 View。
var watch = ClientRichDocuments.watch(scope, document, state::set);
watch.edit(charge, new RichSyncProtocol.Number(60), true);
```

範例的 `rules` 是消費模組自己的伺服器邏輯。`trySetAuthorizedLimit` 需驗證目前身份、可編輯對象、遊戲規則與合法範圍，並原子提交。`watch.edit` 的 `true` 表示本機已提交提案，結果以後續伺服器回覆為準。

## 協定與恢復

| 階段 | 訊息 | 處理方式 |
| --- | --- | --- |
| 協商 | `Hello`、`Welcome`、`Unsupported` | 連線先交換協定範圍與能力旗標。v1 要求 `DELTAS`，`EDITS` 可選。客戶端隨機 nonce 配對握手，伺服器生成獨立連線 epoch。 |
| 訂閱 | `Subscribe`、`Snapshot`、`Unsubscribe` | 伺服器授權後提供第一個完整快照，revision 從 1 開始。每次掛載使用遞增的 watch ID，任何上一掛載或上一連線的回覆均失效。解除訂閱釋放訂閱資料；舊 Subscribe 不會恢復已關閉的訂閱。 |
| 更新 | `Patch`、`Resync` | 變更與刪除集合原子套用至相符的 base revision。重複或舊版補丁捨棄。版本有缺口或補丁無法套用時改用完整快照，等候期間保留最近權威值並停用編輯。 |
| 提案 | `Edit`、`Result` | 每次只允許一筆本機提案，帶已觀測 revision、遞增 request ID、欄位 ID 與同型值。伺服器重查許可及最新值，驗證 revision、欄位與型別，再呼叫宿主編輯；最後讀回實際值。 |
| 拒絕 | `Failure` | 未知文件與拒絕讀取都回傳 `DENIED`。讀取回呼失敗回傳 `FAILED`，例外只送本機伺服器日誌。兩者都撤銷該訂閱並清除客戶端值。 |

一般訂閱會按伺服器 tick 更新。補丁只在欄位有變化時傳送。伺服器保留最近一筆提案的結果，同一請求完全相同且仍具讀取權時可重傳結果。較舊 ID、相同 ID 的改寫、其他連線的 epoch 均失效。送達權威層但因流量上限丟棄的提案 ID 也會列入該訂閱的已見序列。

選用預測只改變 `View.displayed`。`View.authoritative` 來自目前快照與補丁。接受、拒絕或過期時移除預測，顯示權威值。握手與快照最多嘗試三次，每次等待兩秒。編輯等候兩秒後回報本機 `TIMED_OUT`，移除預測並重新取快照。逾時提案不自動重發。成功取回快照時可顯示實際寫入結果，即使原回覆遺失。

開啟的純核心 watch 在斷線期間保留最近一次授權快照供唯讀展示。建立新連線時立即清空舊值，重新協商、授權並取得完整快照。此狀態只存於記憶體，畫面關閉即釋放。跨程序快取、離線編輯佇列和 CRDT／OT 協同編輯待實作。

## 邊界與預算

- 封包外層 v1 有方向別及固定型別標籤。客戶端至伺服器上限 1,024 bytes，伺服器至客戶端上限 28 KiB。ID 上限 128 bytes，單行文字上限 512 UTF-8 bytes。嚴格拒絕截斷、附加尾端資料、無效 UTF-8、無效型別、重複欄位與控制字元。
- 每伺服器最多 128 個註冊文件、1,024 個已見連線狀態。每連線每秒的固定視窗上限為 4 次握手、32 筆一般請求，其中最多 8 次訂閱／重取和 8 次編輯。退訂只釋放目前範圍。權威輪詢最多每 100 ms 取樣一次，兩端的原生伺服器橋每 5 tick 掃描活動玩家。
- Loader 網路執行緒只做有界解碼和伺服器工作排隊。原生排隊層限制每連線每秒 64 筆，最多 16 筆待處理工作，合計 512 筆待處理工作。伺服器授權與遊戲狀態存取在伺服器執行緒，客戶端消費快照在客戶端遊戲執行緒。協定核心無 Minecraft 或 GL 依賴。
- 固定 32 個最大欄位的編碼上限測試中，完整快照為 20,676 bytes，32 項替換加 32 項刪除為 24,844 bytes，單一長整數更新含最大 ID 為 183 bytes。這些數字是協定負載，尚未量測實際整合包頻寬、延遲或長時間分配。現有補丁合併變更並使用 Loader 的傳輸，未引入額外壓縮、磁碟重播或自訂可靠傳輸。

在單人世界執行 `/shbetterrichsync`。示例只開放給整合伺服器的世界擁有者。伺服器的暫存能量從 40 開始，可接受 25 或 65，拒絕高於 80 的提案。按「嘗試 100」會短暫預覽 100，收到拒絕後恢復權威值。專用伺服器的消費模組可以註冊自己的文件。

相容性與雙端煙測見 [驗證紀錄](RICH_TEXT_TESTING.md)。待辦與階段狀態見 [進度](RICH_TEXT_PROGRESS.md)。
