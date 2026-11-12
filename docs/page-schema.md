<!-- 作者：山海／dishanhai -->
# 统一页面 Schema

`kernel.api.page` 是纯 Java 17 契约。页面定义由稳定 ID、Schema 版本、页面类型、固定控件树、数据字段、动作和选项组成。每次打开页面由运行时创建独立实例及作用域。

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

## 序列化与离线验证

`UiPageCodec.encode/decode` 读写有界的二进制页面定义。v1 使用 `SHPG` 签名、三个大端版本整数、长度前缀的 UTF-8 字符串和带类型标签的数据。映射按键排序编码，保留子节点顺序。解码检查字节数、递归深度、总节点数、数据量、重复键、非法类型、截断及尾随数据。页面协议仍须执行端点身份、会话、权限和能力检查。

```sh
java -cp SH-Better-UI.jar com.dishanhai.sh_better_ui.kernel.api.page.UiPageSchemaCheck page.uip --remote
```

`UiPageSchemaCheck` 读取由编码器生成的文件，针对内置目录进行校验。自定义目录通过 `UiPageValidator(Map<UiId, UiComponentDescriptor>)` 接入。错误项包含代码、路径、属性、期望、实际类型和修改提示。对于实际客户端，使用 `resolve` 检查控件与能力并得到开页后固定的有效树。

默认 Schema 预算包括 256 个控件、16 层深度、每节点 64 个子节点、32 个属性或绑定、128 个数据字段和 64 个动作。运行时可根据宿主配置收紧。编码硬上限为 256 KiB。预算限定输入规模，性能仍取决于渲染器、窗口和设备。
