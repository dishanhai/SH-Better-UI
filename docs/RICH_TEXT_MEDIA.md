<!-- 作者：山海／dishanhai -->
# 行內圖片、遊戲物件與資源重載

`RichTextView.image` 接受完整的資源包 PNG、GIF 或 APNG 路徑，例如 `minecraft:textures/item/diamond.png`。PNG 可搭配同名 `.png.mcmeta` 的 `animation` 區段，支援尺寸、預設時間、逐幀索引和時間。時間沿用 Minecraft 每 tick 50 ms 的設定，目前切換整幀。GIF 和 APNG 支援畫布內的矩形幀、透明混合及還原前幀，並依檔案的循環次數播放。GIF 的清除背景處理會還原為透明。啟用減少動效時，視圖固定顯示首幀。

排版盒一律取自節點宣告尺寸。等待解碼、缺圖、損壞、格式無法辨識和超過上限時使用原位置的占位盒。實際圖像依尺寸等比置中，以最近鄰過濾取樣。以 `view.mediaStatus(id)` 區分排隊解碼、就緒與無法載入。可見畫面透過 `Presentation.availableTextures()` 查詢當幀已就緒的貼圖別名。替代文字放得下時會畫在占位內，示例也透過原生 Tooltip 顯示圖片描述。

| 項目 | 上限 |
| --- | --- |
| 單一編碼檔 | 本機 8 MiB，HTTPS 2 MiB |
| PNG 動畫 sidecar | 16 KiB |
| 圖片尺寸 | 單邊 4,096 px，動畫各幀的畫布總面積和圖集皆最多 4,194,304 px |
| 動畫 | 32 幀，單次播放最多 5 分鐘，GIF／APNG 最短幀 16 ms |
| View 素材 | 128 個文件圖片、額外 64 個手動序列貼圖、16 個已授權 HTTPS 別名 |
| 工作數 | 每個 View 4 件，所有 View 合計本機 8 件、HTTPS 4 件，包含待領取的已解碼結果 |
| GPU 貼圖面積 | 每個 View 4,194,304 px，整個客戶端 16,777,216 px |

View 透過既有渲染流程取樣。資源包讀取、檢查、解碼及像素轉換在獨立的背景工作池執行。渲染執行緒每次繪製最多上傳一張貼圖並還原貼圖綁定和 pixel unpack 狀態；每段動畫的所有幀共用一張固定圖集，繪製只變更來源矩形。這版每個素材單獨持有貼圖，多素材合併、線性取樣、`.mcmeta` 幀插值及 SVG／SDF 匯入仍待實作。

完成和失敗結果只保留於 View 的目前資源世代。文件移除資源時釋放對應工作與貼圖。UUK `ResourcesService` 套用新世代時取消舊工作、釋放貼圖和已呈現資料，下一次繪製會重新排入解碼。關頁會丟棄未領取的結果、阻止新工作並撤銷 GPU 貼圖。過期工作即使從背景返回，仍會直接釋放 CPU 像素。解碼後無法在預算內上傳的素材會維持占位，可經重新載入或移除後重新加入來重試。

## 實驗性 HTTPS 圖片

由可信任的 Java 宿主在客戶端渲染執行緒明確授權並綁定別名。預設未啟用。只有 Java 綁定可以建立遠端請求，標記、JSON、同步協定與本機資源路徑沒有 URL 入口。每個 View 綁定的 URI 在下一次繪製後可開始下載，因而應在取得宿主授權後綁定。資源重載會再次讀取該 View 仍綁定的 URI。

```java
var alias = UiId.parse("example:textures/rich/event_banner.png");
var policy = RichTextMediaPolicy.allowHttps(Set.of("assets.example.org"));
view.bindRemoteImage(alias, URI.create("https://assets.example.org/event/banner.png"), policy);
var node = new ImageNode(UiId.parse("example:banner"), alias, 64, 32, altText);
```

僅接受政策中的 1–16 個完整 DNS 主機名稱及 HTTPS 443，不展開子網域。URL 最長 2,048 字元，包含帳密、fragment 或 IP 位址的 URI 會被拒絕。每個重新導向重新驗證政策，最多兩次，TLS 按實際請求主機驗證憑證。連線使用同一 DNS 解析器回傳的實際位址，拒絕含私有、回環、鏈路本地及其他非公開位址的解析結果。下載設 2 秒連線、1.5 秒讀取停滯及 6 秒請求鏈取消時限，拒絕壓縮內容及未知 MIME 類型。兩個 HTTPS 工作執行緒與本機讀取隔離，系統 DNS 查詢可能在取消後由作業系統繼續。此版已用單元測試覆蓋政策、位址過濾和工作取消，尚未用公開 HTTPS 端點驗證實際 TLS 與重導。

## 原生遊戲物件

- `Frames` 仍可用 UUK 幀時鐘輪播本機貼圖，減少動效時固定首幀。每節點 1–32 幀，單幀 16–60,000 ms。
- 物品、方塊庫存模型、流體的 atlas 貼圖與 tint 由原生素材管線繪製。可信 Java 可綁定保留組件／NBT 的物品副本。活體 Entity 預覽需要客戶端世界，預覽實例獨立於世界實體清單。世界切換、重載及關頁會清除預覽暫存。沒有世界或類型無法繪製時使用占位。
- 內建 `CHECK`、`CHEVRON` 和 `STAR` 由向量折線縮放。
