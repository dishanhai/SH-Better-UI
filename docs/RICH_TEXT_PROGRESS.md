<!-- 作者：山海／dishanhai -->
# Rich Text & Inline Content Engine 進度

| 階段 | 狀態 | 證據與邊界 |
| --- | --- | --- |
| Phase 0：審計與設計 | 通過 | 已確認 Forge 1.20.1 和 NeoForge 1.21.1 的既有構建可用，核對 UUK 入口、資源、事件、合成、註冊與權限。見 [架構、依賴與風險](RICH_TEXT_ARCHITECTURE.md)、[構建和 CPU 基線](RICH_TEXT_TESTING.md)。 |
| Phase 1：最小圖文切片 | 通過（Stable，限此切片） | 不可變 `RichDocument`、純文字、靜態 PNG、換行、基線、命中快照、占位及資源重載。共用 Screen 示例 `/shbetterrichtext`。 |
| Phase 2：文件語義與排版 | 核心切片通過 | Stable：Builder、被動標記、JSON／DFU Codec、穩定 ID、樣式、段落、翻譯、字素選取與單 run 編輯。Experimental：原生雙向片段及 Component 適配。OS IME 組字、段落級塑形和視覺選取未完成。 |
| Phase 3：行內節點與互動 | 核心切片通過 | Stable：本機 PNG 幀序列、物品／方塊／流體、內建向量、按鈕／滑桿、Tooltip、焦點及拖放。Experimental：活體預覽已驗證兩端原生狐狸，其他模型、旁白和第三方宿主待驗收。SVG／SDF 匯入與嵌套行內樹未完成。 |
| Phase 4：視覺與負載 | 核心切片通過 | Stable：受管玻璃、漸變、VisualReveal、陰影、清晰前景、裁剪、可見行查詢與同樣式文字批次。Experimental：單機像素預算、CPU／GPU 基準。局部重排、離屏髒區重繪及跨 GPU 門禁未完成。 |
| Phase 5：媒體與擴充 | 核心切片通過 | Stable：本機 PNG／GIF／APNG／`.png.mcmeta` 的有界異步解碼與動畫幀圖集，重載、關頁釋放、占位及狀態查詢。Experimental：顯式 HTTPS 授權、固定行盒 renderer／資料來源、受限格式擴充與 KubeJS 門面。多素材共用圖集、外部 Pass 和實際選用模組驗收待完成。 |
| Phase 6：權威同步 | 核心切片通過 | Experimental：可選 PLAY 版本協商、單一活動訂閱、快照及補丁、伺服器授權、預測校正與重新連線。CRDT／OT、持久離線快取、診斷重播及獨立多人矩陣待完成。 |
| Phase 7：原生編輯與 Web | 核心切片通過（Experimental） | 原生 WYSIWYG、節點與屬性、復原重做、原子存檔、重開、預設、媒體播放及定位、命中疊層、CPU 診斷、資源重載及 JSON／Java 匯出。Web 工作台以 v1 JSON 交換，瀏覽器預覽保留素材占位。 |
| Phase 8：整合與發布準備 | 核心切片通過 | 獨立 Forge／NeoForge 消費範例、公開接入文件、遷移、功能開關、佈局與文件 Golden、負載基準和發布檢查。外部整合包、跨 GPU、OS IME、獨立多人及完整動畫編輯仍待驗收或實作。 |

Phase 1 共用 Java 17 核心在 `src/kernel/java/.../api/richtext`；`RichTextView` 及 `RichTextImages` 是雙端共用客戶端適配。舊文字、布局、模糊與主題入口保留。此版使用既有 Screen、Layout 和 Resources 服務，不新增 `UIApis.RICH_TEXT` 登記。純資料與排版不載入 Minecraft 類；涉及 `Font`、素材、繪製和 scope 變更的 API 在客戶端渲染執行緒使用。

| 2026-11-11 | `kernelTest` | `test` | 構建與 JAR |
| --- | --- | --- | --- |
| Forge 1.20.1 / 47.4.16 | 199 通過 | 879 通過 | `build`、`reobfJar`、`releaseJar`、`verifyLibraryJar` 通過 |
| NeoForge 1.21.1 / 21.1.251 | 199 通過 | 881 通過 | `build`、`verifyLibraryJar` 通過 |

上表為 PE1 結案。PE2–4 最終構建的 Forge 為 209 項 `kernelTest` 和 891 項 `test`，NeoForge 為 209 和 893 項，皆零失敗及零跳過。發布 JAR 內包含共用契約與示例，沒有打入煙測類。 [重跑命令與驗證紀錄](RICH_TEXT_TESTING.md)

兩端隔離開發客戶端皆通過實際 framebuffer 像素比對。GUI Scale 2／1 時，行數為 4／3；資源世代從 1 進到 2，原占位盒內出現測試資源包的圖片。圖片的半開命中邊界、關頁清理及 Kernel 結束標記均通過。Forge 首輪找到 `LoadingOverlay` 直接繪製 Screen 的崩潰；示例在覆層期間略過受管 pass，修正後兩端都重跑成功。

PE2–4 進階頁於兩端驗證樣式及原生 metadata、拖曳選取、中文及補充字元提交、復原、按鈕單次觸發、滑桿預覽／取消／區外放開、拖放與捲動命中。整合世界檢查原生狐狸像素與登出後關頁失效。原生效果 probe 另驗證亮暗背景像素、同幀前景、GL 狀態還原、高對比及 scope 關閉。時間數據為固定場景樣本，記憶體分配、顯存、實際 Draw Call 及下游整合包仍缺驗證。 [API](RICH_TEXT_API.md) · [行盒契約](RICH_TEXT_LAYOUT.md) · [資源限制](RICH_TEXT_MEDIA.md) · [未完成事項](../TODO.md#rich-text--inline-content-engine)

PE5–6 的原生入口為 `/shbetterrichsync`。兩端回歸核對 GIF 幀像素、損壞素材占位、資源包重載、同一行盒內的自訂能量條、接受 65、拒絕 100 後復原、同一連線重開和新連線重取。雙端完整構建中，Forge 通過 231 項 `kernelTest` 及 906 項 `test`，NeoForge 通過 231 項及 908 項。 [媒體](RICH_TEXT_MEDIA.md) · [擴充](RICH_TEXT_EXTENSION.md) · [同步](RICH_TEXT_NETWORK.md) · [驗證](RICH_TEXT_TESTING.md#pe56-雙端驗證)

PE7–8 的原生入口為 `/shbetterricheditor`。文件以本機 v1 JSON 儲存，Web 預覽和第三方範例使用同一格式。原生煙測在整合世界中執行，發行門禁涵蓋兩端構建、匯出編譯、第三方範例、Golden 和 CPU 基準。 [編輯器與發布檢查](RICH_TEXT_RELEASE.md) · [第三方 SDK](RICH_TEXT_SDK.md) · [驗證](RICH_TEXT_TESTING.md#pe78-雙端驗證)
