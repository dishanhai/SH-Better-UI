<!-- 作者：山海／dishanhai -->
# UUK 自动化实施进展（1.0.11）

日期：2026-11-14。需求基准：`.omc/specs/deep-interview-uuk-auto-discovery.md`。
本批为 NeoForge 1.21.1 的可构建实现增量，**不是完整首版验收完成**；未部署到玩家实例。

## 文件 / 位置 / 内容

| 文件或位置 | 内容 |
| --- | --- |
| `src/kernel/java/.../api/automation/capability/` | 纯数据值、能力描述、参数/结果 schema、引用、请求与结果接口 |
| `src/kernel/java/.../internal/automation/capability/` | 服务端权限复核、注册撤销、冲突拒绝、异步回调与进程内去重 |
| `src/kernel/java/.../internal/ServerAutomationScopes.java` | 复用已有 ScopeLifecycle 管理服务端模块与玩家会话 |
| `src/kernel/java/.../api/automation/rule/`、`internal/automation/rule/` | 补货规则、页面/在线生命周期、持久化意图、有限重试、UNKNOWN 暂停、原子文件存档 |
| `neoforge-1.21.1/src/main/java/.../automation/AutomationRuntime.java` | 服务器启动/tick/退出接线、页面草稿、规则和脚本协调、编辑版本检查 |
| `neoforge-1.21.1/src/main/java/.../automation/ae2/` | AE2 19.2.18 实体终端绑定、真实库存和异步合成、任务回执、未知结果写屏障 |
| `neoforge-1.21.1/src/main/java/.../automation/client/` | 控制中心入口、原界面返回、兼容终端默认接管偏好、节点与多行 JS 编辑 |
| `uuk-script-worker/`、平台 `automation/script/` | Java 21 / Rhino 1.9.1 独立进程、受限纯数据接口、有界协议、超时与退出清理 |
| `ServerPages`、`UiServerConnection`、`ClientPages` 等 | 页面可见实例查询和当前远端页面动作桥；原屏及屏幕代际检查 |
| `src/main/resources/assets/sh_better_ui/lang/` | 英文、简体中文、繁体中文自动化文本 |

已有其他任务的改动未回退。本批位于 `codex/uuk-automation`，未提交或推送。

## 当前使用路径

1. 双端安装匹配 SH-Better-UI；首个适配器精确支持 AE2 **19.2.18**。
2. 打开受支持的物品终端或合成终端。客户端和服务端均通过兼容性检查后请求 UUK 页面；失败时保留原界面。无线/其他终端及第三方子类不自动接管。
3. 页面显示服务器实际绑定的终端。也可从 SHBUI 控制面板的“自动化”进入，或自行分配自动化快捷键。
4. 使用物品、下限、目标、页面/在线模式配置补货；默认铁锭 64 → 256。快捷输入按 Enter 提交草稿，旁边的文本显示服务器当前值。
5. 节点编辑器和 JavaScript 编辑器通过同一页面会话应用草稿；多行脚本上限 4096 字符。保存规则会停用，明确确认后启用。
6. 输入已有规则名称或 UUID 并按 Enter 可加载其已保存配置。暂停只停止新触发，并取消尚未提交的规划；已经交给 AE2 的任务继续。
7. 返回会复用原终端屏幕；原菜单已被服务器关闭时返回游戏。原界面上的默认 UUK 开关按屏幕类型保存偏好。

当前实体终端授权要求玩家在同维度、目标区块已加载且距终端不超过 8 格；不会强制加载区块。离开授权范围时停止新请求，不能把“在线模式”理解为任意距离访问网络。

脚本只产生请求意图。例如：

```javascript
if (input.stored + input.pending < input.lower) {
  uuk.request("sh_better_ui:replenish", {rule: input.rule});
}
```

脚本不能指定其他玩家或规则，不能直接调用 Java、文件或网络。父进程核验完整请求批次，再走相同服务端规则执行器；当前脚本桥只接通补货能力。返回失败时不执行已收集的请求。

## 存储及分发

- 世界数据：`data/sh_better_ui/automation/` 下的 `rules.bin`、`adapter.nbt`、`editors.nbt`。规则库使用文件锁、强制写盘与原子替换。
- 规则与编辑器以 revision 对齐；元数据缺失或不匹配时禁止执行。重新启用前先持久化对应版本，不以缺失脚本退回模板执行。
- 客户端偏好：`config/sh_better_ui/automation-terminal-ui.json`。损坏时保留原界面。
- worker 作为主 JAR 内的独立资源交付，按 SHA-256 释放；不进入游戏模组 classpath，不要求额外安装 Node.js。
- 主产物：`neoforge-1.21.1/build/libs/sh_better_ui-neoforge-1.21.1-1.0.11.jar`。构建同时校验独立 worker 的许可与第三方条目边界。

## 验证记录

NeoForge 命令：`./gradlew --no-daemon test kernelTest build`。
本批最终整合验证：主测试 **1051**、共享内核 **403**、独立 worker **13**，零失败、零错误、零跳过；目标模块 `test kernelTest build` 通过。

Forge 共享回归使用已有 `gradle-install/gradle-8.8/bin/gradle --no-daemon compileJava compileTestJava kernelTest test`，编译通过，主测试 **1041**、内核 **403**，均无失败。该检查不表示 Forge 已接入本批 NeoForge 专属自动化运行时。任务测试进程已退出；最终进程清单无 ScriptWorkerMain，也无本次单次 Gradle 构建新增残留。未关闭此前已存在的其他任务进程。

执行了独立规格审查与代码质量审查；发现并修复编辑保存窗口、旧脚本结果、关闭页面后的提交、规划参数变化、正常停服、历史目标恢复、明确未执行重试与脚本 getter 重入预算等问题。修复已通过源码复核。

未启动 Minecraft 客户端或专用服务器，未进行真实 AE2 多人/重启/合成验收。上述测试数字包含已有回归测试，不全部是本批新增测试。

## 尚未完成的首版范围

- 标准描述文件到第三方服务器能力提供者的完整自动发现，以及任意能力 schema 到操作界面的通用生成；本批接通的是内置 AE2 版本门控、能力目录和补货页面。
- 可视化编辑器目前是“库存条件 → 补货动作”的固定样板，尚非任意节点、分支和跨模组流程图。脚本也仅桥接当前补货能力。
- AE2 重启或断网后的非终态任务保守进入 UNKNOWN；自动可靠重连/核对及人工解除屏障的完整操作界面尚未完成，不能声称活动任务的自动恢复已验收。
- AE2 回执/任务累计容量 4096；终态也占用，重启不释放。需要保留去重证据的归档与容量提示，不能通过清空记录绕过。
- 目前权限包含原菜单绑定、玩家/位置/区块/物理节点及原模组动作检查。第三方领地/保护模组策略还需接入与验证。
- worker 尚无进程总 RSS 限制/监控；JVM 堆、元空间、直接内存预算和硬超时不等同于 OS 沙箱。
- 真实客户端焦点/回退、GUI 缩放、旁白、高对比、减少动效、多人权限、长时间运行以及规格 AC01–AC16 完整矩阵仍待验收。

以上未完成项同步记录在 `TODO.md`，不以核心编译或样板测试代替首版交付。
