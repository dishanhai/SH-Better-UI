<!-- 作者：山海／dishanhai -->
# 统一页面 Schema

`kernel.api.page` 是纯 Java 17 契约。页面定义由稳定 ID、Schema 版本、页面类型、固定控件树、数据字段、动作和选项组成。每次打开页面由运行时创建独立实例及作用域。

## NeoForge 26.x 本地声明式页面

NeoForge 26.1.2 和 26.2 共用本地页面运行时。消费模组通过 `context.engine().require(PageService.KEY)` 获取服务，在内核 UI 线程用自己的模块上下文调用 `register` 和 `open`。注册前可用 `pageTypes()` 和 `components()` 选择目标页和备用节点。

- 当前页面类型为 `SCREEN`，可用节点为 `column`、`text`、`button`、`spacer`。按钮支持无载荷的 `LOCAL` 动作。数据字段可以绑定文本、按钮标签、可见性和启用状态。页面正文可以滚动，数据更新后按稳定 ID 恢复仍可交互的节点焦点。
- `Instance.displayed()` 记录首次原生呈现，`closed()` 交付最终快照。Escape、返回按钮、实例关闭和注册撤销均结束页面。原生界面切换结束当前页面，调用方可以在结束后打开新页面。
- 其他页面类型、第三方组件工厂、富文本、逐节点自适应文字策略与远程页面仍待接入。下文的服务端动作示例适用于已接入远程运行时的平台。

独立消费 JAR 烟测从本地打包 JAR 加载 UUK，覆盖能力查询、无效页面和数据拒绝、首次呈现、Tab 滚动、按键提交、数字字段绑定、焦点恢复、Escape、原生界面替换与注册撤销。验收输出位于目标模块的 `build/widget-smoke/<版本>/default-pages/declarative-page.txt`。显式指定 OpenGL 时，目录为 `opengl-pages`。

2026-11-15，26.1.2 与 26.2 完整构建和发布 JAR 校验通过。独立消费模组在 26.1.2 OpenGL、26.2 Vulkan 和 26.2 OpenGL 三条路线通过烟测。

```bash
bash neoforge-1.21.1/gradlew -p neoforge-26.1.2 -I ../gradle/neoforge-26-widgets-smoke.init.gradle runWidgetSmoke -PwidgetSmokePages --no-daemon
bash neoforge-1.21.1/gradlew -p neoforge-26.2 -I ../gradle/neoforge-26-widgets-smoke.init.gradle runWidgetSmoke -PwidgetSmokePages --no-daemon
bash neoforge-1.21.1/gradlew -p neoforge-26.2 -I ../gradle/neoforge-26-widgets-smoke.init.gradle runWidgetSmoke -PwidgetSmokePages -PwidgetSmokeBackend=opengl --no-daemon
```

## 页面模型示例

```java
UiId exchange = UiId.parse("example:exchange");

UiPageDefinition page = UiPageDefinition.builder(exchange)
        .title("兑换中心")
        .field("player.balance", UiValueSchema.integer(0, Integer.MAX_VALUE))
        .action(new UiActionDefinition(exchange, UiActionPolicy.SERVER, UiDataSchema.EMPTY))
        .root("root", "column", column -> column
                .child("balance", "text", text -> text.bind("text", "player.balance"))
                .child("redeem", "button", button -> button.label("兑换").action(exchange)))
        .build();

new UiPageValidator().requireValid(page, true);
```

- 控件 ID 在整棵树内唯一。数据路径为点分隔名称，动作 ID 属于页面命名空间。
- 属性使用字面量或绑定字段，二者择一。字段类型及范围须满足控件属性描述。内置文本属性接受整数、长整型和有限小数的显示绑定。
- 初始数据、全量快照和动作参数要求声明的全部字段。补丁仅替换提交的字段，列表与对象均整体替换。
- `UiBuiltinComponents.descriptors()` 提供内置控件的属性目录。平台通过独立能力映射公布已接入的渲染器。
- 自定义控件描述随 `ScopeHandle` 注册及释放。远程引用需要客户端显式允许控件和相应属性。图标和图片的远程字面量采用命名空间资源 ID。
- 预开页降级可选择已支持的替代控件，保持稳定 ID 和子树。动作节点要求原控件可用。可隐藏的节点须为非根节点，且其子树不含动作。

## 下拉选择

Forge 1.20.1 与 NeoForge 1.21.1 的原生 `dropdown` 使用 GlideSelect 交互。选项按顺序提供，`selected` 和动作载荷使用从 0 开始的索引。只有选中与当前值不同的项才提交动作。

```java
column.child("format", "dropdown", select -> select
        .property("options", new UiValue.Sequence(List.of(
                new UiValue.Text("PNG"), new UiValue.Text("JPG"))))
        .property("tags", new UiValue.Sequence(List.of(
                new UiValue.Text("无损"), new UiValue.Text("体积较小"))))
        .bind("selected", "export.format"));
```

可选属性包括 `tags`（按索引对应选项）、`placeholder`（绑定索引没有匹配项时显示）、`menuWidth`（默认 176 GUI 单位）、`placement`（`Choice`，默认 `bottom`）、`align`（`Choice`，默认 `left`）和 `rememberPosition`（默认开启）。空间不足时菜单会翻转或缩短为可滚动列表。

在选项上按下后可拖到另一行，松开时提交该行。键盘可用方向键、Home、End 和按首字查找，Enter 或空格确认，Esc 或 Tab 收起。

## 序列化与离线验证

`UiPageCodec.encode/decode` 读写有界的二进制页面定义。v1 使用 `SHPG` 签名、三个大端版本整数、长度前缀的 UTF-8 字符串和带类型标签的数据。映射按键排序编码，保留子节点顺序。解码检查字节数、递归深度、总节点数、数据量、重复键、非法类型、截断及尾随数据。页面协议仍须执行端点身份、会话、权限和能力检查。

```sh
java -cp SH-Better-UI.jar com.dishanhai.sh_better_ui.kernel.api.page.UiPageSchemaCheck page.uip --remote
```

`UiPageSchemaCheck` 读取由编码器生成的文件，针对内置目录进行校验。自定义目录通过 `UiPageValidator(Map<UiId, UiComponentDescriptor>)` 接入。错误项包含代码、路径、属性、期望、实际类型和修改提示。对于实际客户端，使用 `resolve` 检查控件与能力并得到开页后固定的有效树。

默认 Schema 预算包括 256 个控件、16 层深度、每节点 64 个子节点、32 个属性或绑定、128 个数据字段和 64 个动作。运行时可根据宿主配置收紧。编码硬上限为 256 KiB。预算限定输入规模，性能仍取决于渲染器、窗口和设备。
