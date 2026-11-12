<!-- 作者：山海／dishanhai -->
# Unified UI 页面协议

页面协议 `1.0.0` 和页面 Schema `1.0.0` 独立版本化。共享实现位于 Java 17 内核。两端适配器通过 `sh_better_ui:pages` 交换应用层消息。

## 握手与连接

已知对端订阅通道时，传输适配器选定一方发送 `HELLO`。发起方生成随机连接 UUID，初始序号为 1。接收方以实际网络连接为范围重组完整的 HELLO，核对版本后绑定 UUID，并回 `CAPABILITIES`。两端按各自声明选择共同支持的协议版本和 Schema 版本。版本不兼容时返回 `PROTOCOL_INCOMPATIBLE`。客户端未参与握手时，服务端开页返回 `CLIENT_UNAVAILABLE`。

握手交换页面类型、能力 ID 与版本、可接收的最大消息字节数。能力版本须与要求的主版本相同，且达到最低版本。能力表可以包含实际可呈现的控件、材质与宿主功能。客户端在开页时再次验证 Schema 和当前权限。

`UiProtocolChannel` 按方向维护各自的序号。每个完整消息的连接 UUID 必须与通道绑定的 UUID 相符，序号须高于上一条已接受的消息。应用层可以识别重放并丢弃过期序号。客户端用页面实例 UUID 标识每次打开，连接结束后释放该连接的实例、授权相关状态和分片。

## 编码和分片

`UiProtocolCodec` 使用大端序。消息依次包含 4 字节 magic `SHUP`、三段 32 位版本、16 字节连接 UUID、64 位正序号、1 字节消息类型和对应的数据字段。符号采用有长度前缀的 ASCII，页面定义及类型化字段使用 [页面 Schema](page-schema.md) 的编码。解码检查字段边界并要求读完完整消息。

| 标签 | 消息 |
|---|---|
| 1, 2 | HELLO、CAPABILITIES |
| 3, 4, 5 | PAGE_OPEN_REQUEST、PAGE_OPEN_RESULT、PAGE_CLOSE |
| 6 | DATA_UPDATE |
| 7, 8 | ACTION_REQUEST、ACTION_RESULT |
| 9, 10 | HUD_MOUNT、HUD_UNMOUNT |
| 11 | NOTIFICATION_PUSH |
| 12, 13 | SESSION_TERMINATE、ERROR |
| 14, 15 | STATE_REQUEST、STATE_SNAPSHOT |

每个分片包含 4 字节 magic `SHUF`、16 字节传输 UUID、4 字节完整消息大小、2 字节从 0 起的分片下标和分片数据。分片的实际长度由完整消息大小和下标唯一决定。默认每条消息最多 256 KiB，每个分片最多 24 KiB，协商后消息上限取双方较小值。每条连接最多同时重组 4 条消息，部分重组保留 5 秒。限期内的重复分片和已完成传输 UUID 会被丢弃。进度超时后，宿主可通过状态快照恢复。

## 页面状态和数据同步

开页调用先返回异步回执。服务端协调器支持接收进度、客户端授权、确认和实际显示。收到 `DISPLAYED` 才完成成功回执。开页请求可由客户端拒绝，也会随会话关闭或在 10 秒后超时。

新实例的 revision 从 0 开始。服务端逐字段整值替换，能将多个逻辑更新合并为一条 `DATA_UPDATE`。接收端仅在 `baseRevision` 与本地 revision 相同且 `revision` 前进时应用。发现缺失时，可暂停依赖旧数据的操作并发送 `STATE_REQUEST`。服务端以 `STATE_SNAPSHOT` 返回当前整份数据。客户端恢复前再次校验 Schema 和 revision。

`ACTION_REQUEST` 使用当前页面实例 ID、唯一 requestId、声明过的 action ID、revision 和类型化参数。服务端返回权威 `ACTION_RESULT`。如果操作更新页面状态，先发布 `DATA_UPDATE` 再回操作结果。会话期内已接收的操作收据使用 requestId 去重，并受权限重新校验。过期版本会返回 `REVISION_MISMATCH`，同窗口的快照预算可用时会附发当前完整状态。

## 预算与调试

`UiProtocolLimits` 可收紧每连接同时存在的页面、HUD 和 action 注册数量，以及分片处理、操作、状态更新、快照和通知的限额。默认每连接 8 个页面、3 个 HUD、128 个操作处理器，每页最多保留 128 个操作收据。默认每秒处理 192 个入站分片、8 次操作、8 份快照和 20 条定时合并更新，每 5 秒接受 4 个通知开页。通知实例最多保留 15 秒，其余活动实例最多保留 30 分钟。默认值是当前实现的资源预算，实际吞吐需在目标服务端和客户端验证。

`UiProtocolChannel.setTracing(true)` 开启连接调试，`traceSnapshot()` 返回最近 64 条已编码出站或已接受入站消息的元数据。记录连接 UUID、序号、方向、消息类型、协议版本、字节数、单调时钟，以及消息存在的页面、实例、请求 ID 和错误码。Trace 不保留定义正文、字段值和操作参数。关闭调试时清空缓冲区。

共享 Java 17 测试覆盖消息往返、截断、超限、乱序、分片、重放、会话关闭、权限和快照恢复。对应 Minecraft 版本的原生通道注册、入服协商、传输和原版降级仍需按平台矩阵实测。
