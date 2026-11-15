<!-- 作者：山海／dishanhai -->
# Unified UI Kernel API 耦合与交互复核：修复交接

## 修复进度（2026-11-14）

- 本文下方保留修复前的链路与复现条件。P1 和 P2 的修复及构建更新已落地，剩余验收见 [TODO](../TODO.md#unified-ui-kernel-api-耦合与交互修复2026-11-14)。
- 远程请求逾时后，仅在原连接和原页面实例内重发一次。若没有权威回执，页面显示结果未知并阻止新动作，仍接受迟到的有效回执。跨会话的幂等由消费方实现。关闭、回执与确认边界见 [协议](protocol.md)。
- Forge 1.20.1 的 `test` 1,020 项和 `kernelTest` 368 项通过。NeoForge 1.21.1 的 `test` 1,022 项和 `kernelTest` 368 项通过，两端均零失败、错误和跳过。两端 `verifyLibraryJar` 及第三方示例 `verifyExampleJar` 通过。
- 两端隔离客户端生成 `success.txt` 和 `stopped.txt`。页面冒烟覆盖 GUI Scale、确认动作、优先模态、容器重连及 KubeJS 重载。文字可读性冒烟检查模糊后的前景、裁切、透明组及局部浮层的像素。运行记录在 `build/kernel-verification/client-<loader>-uuk-coupling-repair-*`。
- 根 `gradlew.bat` 已可接收显式 Forge 参数。默认目标 Forge 1.20.1、NeoForge 1.21.1、NeoForge 26.1.2 已由 `gradlew.bat --no-pause` 顺序构建通过。Forge 1.20.1 和 NeoForge 1.21.1 的 `GaussBlur` 都提供相同的原生参数重载，保留旧加载器事件参数重载以兼容已编译调用方。

## 原交接范围

- 用户先要求对 SH-Better-UI 的 API 耦合和交互做源码级复核，随后明确要求“开始修复”；当前回合因上下文窗口接近上限，要求先写文档和 TODO。**本次只整理交接，复核发现的实现问题尚未修复。**
- 下一阶段继续修复本文件所列问题，优先处理 P1；先写能观察行为的回归，再做最小修复。完成一组后进行相应验证，最终串行核对两个目标平台。
- 仓库：`C:\Users\dishanhai\Desktop\SH-Better-UI`，分支 `main`，复核时 HEAD 为 `a2ec31de7551d0472fdf52f12f3f5342bd2ed0a8`，两端构建配置版本为 `1.0.11`。开始修复前重新检查 HEAD、`git status` 和受影响文件内容。
- 工作区已有多处未提交和未跟踪内容，包含 `TODO.md`、中英文 API 文档、主题／文字／JEI 文件，以及未跟踪的 [既有交互复查](UUK_INTERACTION_REVIEW_2026-11-14.md)。保留它们，不重置或覆盖；本交接文档与 TODO 新增段落也是当前工作区内容。
- 遵循仓库 `AGENTS.md`：中文沟通，只修改山海／dishanhai 署名或已授权文件；用户可见文案走语言文件，中文 tooltip 使用简体中文；优先复用服务、控件、快照及作用域。先读后改，不默认提交、推送或部署。
- 本复核目标为 Forge 1.20.1 与 NeoForge 1.21.1。NeoForge 26.1.2／26.2 有独立适配，不能因共享名称推定问题相同；既有交互复查中对 26.x 的事项单独跟踪。
- 推荐后续使用 `systematic-debugging`、`test-driven-development`、`verification-before-completion`；涉及修复后的回归时审查完整调用流与资源清理。

## 复核依据与公共边界

- 优先读 [API 参考](API_REFERENCE.md#visual-layout--render-composition)、[视觉对齐](VISUAL_ALIGNMENT.md)、[效果](effects.md#视觉组合的即时效果与帧末效果)、[Kernel 架构](kernel-architecture.md)、[富文本 API](RICH_TEXT_API.md) 和 [文字可读性](TEXT_READABILITY_PHASE_4_5.md)。这些文档的当前工作区版本可能包含用户未提交修改。
- 指定名称均有实现：`LayoutService` 为客户端服务，`ClientLayout` 为实现；`AlignmentLayout`、`VisualLayoutBinding`、`FrameViewSnapshot` 共同管理呈现几何；`ScopeHandle` 是纯 Kernel 生命周期契约。`BlurApi`、Kotlin `GlassBlur`、`VisualReveal` 和旧 `GaussBlur` 均存在；两端 `UiClient` 是各自的平台事件桥。
- NeoForge 1.21.1 在 [构建配置](../neoforge-1.21.1/build.gradle#L27) 中列出替换类，其余客户端实现及 `src/kernel/java` 基本共享。共享源码只能支持静态归因，不能替代双端运行验证。
- 视觉布局从 `UnifiedUI.get().require(UIApis.LAYOUT)` 经已挂载的作用域进入 `bindVisual`。一个原生 Screen 挂载只允许一个活动绑定；采样和求解后，Screen Post 完成前景绘制，再发布输入使用的快照。旧主模糊请求优先于受管主请求，冲突应有可辨识结果。
- 先区分有意的隔离与实际缺陷：节点布局写入租约按节点世代排他；声明式 `stack` 当前禁止交互子项；`UiLocalModal` 是局部非阻塞浮层。不要为了修复以下问题取消这些边界。

## P1：优先修复

### 1. 远程动作超时后可能重复执行业务

- **链路：**[ClientPageNetwork.mount](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/ClientPageNetwork.java#L490) 每次动作创建新 UUID；[ClientPages.OpenPage](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/ClientPages.java#L586) 8 秒超时后清除请求并重新允许动作；[UiServerConnection.performAction](../src/kernel/java/com/dishanhai/sh_better_ui/kernel/internal/protocol/UiServerConnection.java#L538) 只按请求 ID 去重。
- **最小序列：**版本 0 → 请求 A 已执行，返回空更新且回执延迟 → 客户端超时 → 用户重试生成 B → 页面版本仍为 0，B 可再次执行。两端独立 JVM 协议探针验证：相同 ID 重发只执行一次；换新 ID 后执行次数由 1 变 2。完整客户端弱网超时尚未复现。
- **修复方向：**保留“结果未知”的原请求身份，不让重试变成新业务意图；优先利用已有同 ID 回执重放。明确会话关闭、回执失效和重连后的行为，不把未知结果当作已失败。跨重连的持久幂等范围需结合现有协议和消费方能力设计。
- **回归／验收：**空更新与未推进版本、迟到／丢失回执、同 ID 重放、失败、重复点击、关闭与重连；单次意图不会重复扣费或发奖。已有服务端对同 ID 的保护见 [UiServerConnectionTest](../src/kernelTest/java/com/dishanhai/sh_better_ui/kernel/internal/protocol/UiServerConnectionTest.java#L120)。

### 2. 浮层外开始的原生拖动，移入浮层后丢失释放

- **链路：**[UiLocalInputRouter](../src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/UiLocalInputRouter.java#L36) 在无浮层捕获时用当前坐标选择浮层；[UiLocalModal](../src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/UiLocalModal.java#L227) 消费拖动／释放并清除 Screen dragging；[NativeClientBridge](../src/main/java/com/dishanhai/sh_better_ui/client/NativeClientBridge.java#L177) 据此阻断原生事件。
- **最小序列：**局部浮层已打开 → 浮层外按下普通原生控件并开始拖动 → 指针进入浮层 → 放开。两端 JVM 浮层探针中，浮层外按下不消费，进入后的 drag/release 被消费，原控件 release 次数为 0，而 Screen dragging 已清除。已成功使用 `UiPointerCapture` 的手势先由捕获器处理，不应扩大结论。
- **修复方向：**按按键保留按下时的所有者；无浮层捕获时不要依据后来的位置夺取原生拖动，或明确取消原控件。继续让浮层自己捕获的手势在移出后收到释放；中断时保留对应放开防穿透。
- **回归／验收：**覆盖浮层外按下移入、浮层内按下移出、浮层退场、控件删除、不同按键、失焦和缩放；原控件恰好收到一次释放或取消。现有 [UiLocalModalTest](../src/test/java/com/dishanhai/sh_better_ui/client/gui/scaled/UiLocalModalTest.java#L133) 主要覆盖浮层自身的捕获。

### 3. 共享 Screen 叠层悬停与点击目标不一致

- **链路：**[UiScreen.getChildAt](../src/main/java/com/dishanhai/sh_better_ui/client/api/UiScreen.java#L334) 对同栈按绘制逆序选取；`mouseClicked` 从添加顺序遍历，导致下层先消费。`addClipped(..., stackId)` 的公开注释明确承诺逆序。
- **复现：**两个重叠的原生 widget 以同一 stack ID 添加。两端 JVM 探针：非重叠点击正常；重叠处命中查询选上层，点击调用下层。声明式 `stack` 当前禁止交互后代，因此不能把该问题泛化为声明式 stack 已支持重叠按钮。
- **修复方向：**悬停、点击及滚轮共享一致的命中顺序、裁剪和可用性规则，保持实际按下控件的焦点和释放。
- **回归／验收：**上层正常／隐藏／停用、不同 stack、部分裁剪、透明区域、拖动与释放；同时保留已有 [UiScreenTest](../src/test/java/com/dishanhai/sh_better_ui/client/api/UiScreenTest.java) 的原生焦点与释放行为。

### 4. 声明式滑杆拖动时权威更新覆盖草稿

- **链路：**[NativePageView.input](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/NativePageView.java#L515) 发现权威字段变化后删除草稿；[NativePageInputs.slider](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/NativePageInputs.java#L61) 刷新时调用 `slider.setValue`；[UiSlider.onRelease](../src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/UiSlider.java#L85) 提交当前值。
- **最小序列：**原值 20 → 用户拖到 60 → 收到权威值 40 → 松开。两端控件级探针中正常提交 60，插入 `setValue(40)` 后提交 40。该探针使用假客户端，Loader bootstrap 有初始化异常，不能当作完整页面或游戏验收。
- **修复方向：**区分拖动草稿和权威数据版本，拖动时缓冲外部更新；释放／取消后以明确的冲突策略处理，不提交用户未选择的值。保持现有键盘操作与取消行为。
- **回归／验收：**外部更新、节点隐藏／禁用、焦点丢失、鼠标释放与取消；确认一次提交、草稿和权威结果可解释。

## P2：后续修复与架构选择

### 5. 富文本子作用域不能消费父视觉快照

[ClientLayout.bindVisual](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/ClientLayout.java#L70) 限制一个 Screen 一个绑定；[RichTextView.draw](../src/main/java/com/dishanhai/sh_better_ui/client/visual/RichTextView.java#L238) 又要求 view 与快照的 scope ID／世代完全相同。父 `tree` 绑定后，两个组件各用子作用域 attach 并在父 pass 绘制会被拒绝；文字服务自身对快照作用域的后代有校验支持。该限制已写入当前 Javadoc，不能称为既有“任意子作用域均支持”的承诺。现有替代是附着父作用域并手动管理 view 与逐 view 策略。

建议保持单绑定和模块隔离，评估通过受控只读关系验证允许同模块、同挂载的后代消费快照；若需要新增公共入口，说明现有句柄无法安全验证祖先关系。验收两个独立组件共用一帧、关闭一个不影响另一个，跨模块和旧世代仍拒绝。

### 6. 单节点绘制失败仍可能发布可命中的几何

[VisualFrameCoordinator.Pass.at](../src/main/java/com/dishanhai/sh_better_ui/client/visual/VisualFrameCoordinator.java#L277) 捕获节点绘制异常并清理该节点效果，但前景阶段仍可发布整份快照；[FrameViewSnapshot.hitTest](../src/main/java/com/dishanhai/sh_better_ui/client/visual/FrameViewSnapshot.java#L42) 不知道节点是否成功绘制。回调在实际绘制前失败时可能出现不可见但可命中的节点。

建议把节点呈现状态纳入当帧命中，或提供明确回退；不得因一个节点失败阻断无关节点。验收注入异常、失效节点不可激活、兄弟节点正常、下一帧可恢复。此项为源码证实，尚未做原生异常注入。

### 7. 排队文字的最终绘制失败缺少同阶段回退通知

[ClientTextReadability.submit](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/ClientTextReadability.java#L300) 在前景保护开启时先返回 `SUBMITTED`，稍后绘制结果未返回给原调用。若 `groupOpacity < 1` 且组透明目标／shader 创建失败，[TextReadabilityPainter](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/TextReadabilityPainter.java#L193) 可返回 `RENDER_UNAVAILABLE`，调用方可能已跳过普通绘字。普通不透明文字在 GPU 采样不可用时已有 CPU 降级，不应泛化为所有 shader 缺失都导致文字消失。

现有替代为 `foreground` 回调中 `drawNow` 并检查结果；建议复用前景队列提供失败回退或完成结果，并更新示例。验收资源创建失败、重载与恢复、原字／回退不重复绘制及诊断。需实际 GPU 或故障注入验证。

### 8. 富文本便捷捕获绕过模块化捕获的身份与权限

[RichTextWorkbench](../src/main/java/com/dishanhai/sh_better_ui/client/gui/panel/RichTextWorkbench.java#L119) 调用 `RichInlineInteraction.capture`，其走旧 [UiPointerCapture.start](../src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/UiPointerCapture.java#L51)，在 [ClientUiKernel.claimNativePointer](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/ClientUiKernel.java#L555) 使用物理 Screen 作用域和空模块身份；`InputService.capture` 则检查模块声明和捕获／阻断权限。两条路径共享单个槽位，但旧捕获无法按模块权限撤销关联。

旧入口是已有兼容 API，框架权限不是 JVM 安全沙箱。可优先为富文本增加可选的模块作用域接入并复用 `InputService.capture`，保留旧行为；明确拒绝、取消、关闭和未收到释放的处理。验收权限撤销／scope 关闭、同槽冲突和旧消费方兼容。

### 9. 等待远程动作回执会丢失焦点

[NativePageView.refresh](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/NativePageView.java#L384) 在 `PENDING` 时禁用带动作控件并清焦点；[ClientPages](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/ClientPages.java#L592) 的一般结果路径没有按稳定节点恢复。确认挑战若在清焦点后到达，也可能保存空值。

建议在提交前保存来源节点 ID，仅在控件仍可用且用户没有主动转移焦点时恢复。验收成功、失败、超时、确认／取消、节点消失及纯键盘操作。

### 10. 声明式下拉滚轮与键盘游标脱节

[NativePageOptions](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/NativePageOptions.java#L130) 的滚轮只更新第一可见行，Enter 仍提交旧 `cursor`；滚走旧游标后可能提交不可见选项或按方向键跳回。明确滚动时的焦点策略并保持可见提示；覆盖滚轮后 Enter、方向键、Esc、旁白和空列表。局部浮层的非阻塞语义不应全局改变。

### 11. 根构建脚本与第三方示例路径过期

[根 gradlew.bat](../gradlew.bat#L19) 忽略传入任务参数、先删除 `build/libs`，随后调用不存在的旧 `neoforge/gradlew.bat`。第三方示例 [README](../examples/kernel-extension/README.md#L47) 和 Forge 默认 JAR 仍写旧路径／`1.0.7`；NeoForge 1.21.1 实际工程位于 `neoforge-1.21.1`。

本次复核误调用了根脚本：它删除并重新生成了 `build/libs` 的四个 Forge JAR，Gradle 构建成功后因旧 NeoForge 路径失败退出。后续测试**不要使用根 `gradlew.bat`**，直到修复该脚本；用下方真实 Gradle 入口。修复脚本时保留 Windows CRLF，验证参数、错误退出码、成功／失败暂停行为及避免先删除唯一旧产物。

## P3：公开签名与文档

双端构建产物的 `javap -public` 抽查了 21 个相关类，20 个签名一致；`GaussBlur.onScreenRenderPost` 分别暴露 Forge／NeoForge `ScreenEvent.Render.Post`。调用点当前仅见两端 `UiClient`。把平台事件留在适配层时需考虑旧公共方法的二进制兼容；该项尚无已证实的普通消费方运行故障。

## 本次复核的验证记录

- Forge：根脚本内部实际执行的 `gradle-install/gradle-8.8/bin/gradle.bat build --offline --no-daemon --console=plain` 成功；`test` 1005 项、`kernelTest` 367 项，失败／错误／跳过均为 0。**根脚本总退出码为 1**，原因见问题 11。
- NeoForge 1.21.1：在 `neoforge-1.21.1` 目录运行 `./gradlew.bat build --offline --no-daemon --console=plain` 成功；`test` 1007 项、`kernelTest` 367 项，失败／错误／跳过均为 0。
- 第三方示例：Forge 使用 `-PshBetterUiJar=../../../build/libs/sh_better_ui-1.0.11-dev.jar`，NeoForge 使用当前 `1.0.11` JAR，两端 Java／Kotlin 编译和 `verifyExampleJar` 均通过；示例无测试源码。两端库 JAR 含 `UnifiedUI.class` 和 `kubejs.plugins.txt`，示例 JAR 含 Kotlin 示例且未打包共享库。
- 用现有 Gradle 测试运行时类路径和 `jshell` 做了叠层、局部浮层、服务端请求 ID 与滑杆控件探针；没有持久化探针源码。具体结论和局限见各问题。
- 这些是**修复前**的记录。未启动 Minecraft、KubeJS 客户端或多人服务器；没有 GPU 像素、真实 GUI Scale／旁白、脚本重载或弱网验收。

## 继续工作时的构建方式与验收

Git Bash 中使用 JDK 21，并让 Windows 进程的 `TEMP`、`TMP` 指向 `C:\jtmp`。不要并行执行共享构建／测试输出目录的 Gradle 任务，避免结果覆盖或锁冲突。示例命令如下，按当前需求选择测试过滤器；`test` 和 `kernelTest` 分别使用各自的 `--tests` 参数。

```bash
export JAVA_HOME='C:\Program Files\Java\jdk-21.0.11'
export TEMP='C:\jtmp' TMP='C:\jtmp'

./gradle-install/gradle-8.8/bin/gradle.bat test --tests '*UiScreenTest' --offline --no-daemon --console=plain
(cd neoforge-1.21.1 && ./gradlew.bat test --tests '*UiScreenTest' --offline --no-daemon --console=plain)
```

完成修复后，分别运行覆盖修改的双端测试、完整构建／JAR 校验与第三方示例；按实际修改决定是否启动隔离客户端。运行实例测试前遵守项目对关闭实例和后台 JVM／脚本的要求，完成后清理本次启动的程序。游戏内再核对动画中断、按下／移动／放开／取消、焦点恢复、GUI Scale、shader／资源重建、KubeJS 重载及独立多人弱网。构建成功不能替代客户端验收；未完成事项继续留在 [TODO](../TODO.md)。
