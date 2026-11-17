# 自定义字体（第一阶段：玩家本地资源包）

SHBUI 音乐 UI 已使用作用域字体 `sh_better_ui:mp_ui` 和 `mp_ui_semibold`。
本阶段提供本地资源包生成工具，不替换 Minecraft 全局字体，不扫描系统字体、不联网下载、不上传玩家字体。
这不是字体选择 GUI 或全 UUK 字体系统接管。富文本调用方仍可使用自己的 Minecraft 字体资源 ID。

## 使用

准备自己有权以资源包方式使用的独立 TrueType `.ttf` 文件。不要从随机下载站取得字体。
工具不将 TTC/OTC、WOFF 或 CFF OTF 转换为 TTF，也不会自动提取系统苹方。

```sh
python scripts/create-custom-font-pack.py --regular /path/to/regular.ttf --semibold /path/to/semibold.ttf --version 1.21.1 --output /path/to/my-shbui-font.zip --license-confirmed
```

Forge 1.20.1 使用 `--version 1.20.1`。不传 `--semibold` 时两个字重使用同一文件，不伪造粗体。
输出路径必须不存在。将 ZIP 放入对应游戏实例的 `resourcepacks`，在 Minecraft 资源包页面启用并置于其他 SHBUI 字体覆盖包之上。
禁用该资源包即可恢复 SHBUI 原字体；重新启用/资源重载会由 Minecraft 重建字体资源。

字体定义末尾保留 `minecraft:default`，用于自带字体缺失的字符。专用图标字体不被覆盖。
这不保证任意字号/字体度量下布局完全一致；请测试中英混排、长标题、歌词、窄窗、GUI 缩放与字体缺字。
坏字体无法加载时应禁用该包；缺字后备不等于能修复损坏字体。

## 安全与许可边界

- 仅本地显式选择，最大 32 MiB/字体、固定包内路径、不覆盖已有输出。
- 检查 TrueType 文件头，不是完整字体校验器或恶意字体沙箱。只使用可信来源的字体。
- `--license-confirmed` 是使用者确认自己拥有适用权利，不是脚本授予许可或自动法律判定。
- 输出 ZIP 含完整用户字体，不应提交到公开仓库、发送给其他玩家或自动部署，除非许可明确允许。
- 字体著作权、嵌入/再分发和商业用途应分别确认；“免费字体”“个人自用”不自动意味着所有用途被允许。

## 苹方渠道

Apple 的系统字体目录列有 PingFang。正常 Apple 系统使用与将字体文件复制到 Windows/Linux 模组不是同一授权。
macOS SLA 的字体条款允许运行 Apple 软件时显示/打印，内容嵌入仍取决于该字体的嵌入限制。
没有在本次调查中找到可直接打包进跨平台 Minecraft 模组的官方苹方许可或下载渠道。

若确需随产品分发，联系权利方确认针对字体名称、版本、平台、游戏/模组嵌入、最终用户及公开源码仓库分发的书面授权。
华康提供定制/OEM授权咨询渠道，但不能由此推定其一定可向本项目授予“苹方”授权。不要把 Apple Developer 的 SF 字体下载许可当作苹方许可。
默认继续使用仓库已有 OFL Noto 衍生字体，避免新增专有字体依赖。

来源（2026-11-17核对）：
- https://developer.apple.com/fonts/system-fonts/
- https://www.apple.com/legal/sla/docs/macOSSequoia.pdf
- https://developer.apple.com/design/human-interface-guidelines/typography
- https://www.dynacw.com.tw/support/support_contact.aspx
- https://www.dynacw.com.tw/support/useragreement.aspx

## 验证

- `python scripts/test-custom-font-pack.py`：6 项测试，通过。包括两版本包格式、作用域/缺字后备、许可开关、拒绝覆盖、格式/大小/符号链接拒绝。
- 使用仓库已有 OFL 字体生成两版本 ZIP 并逐字节核对字体和 JSON 通过。
- 最初的脚本阶段未修改 Java；后续游戏内入口已添加 Java，实现和最新验收状态见下节及 PR。不能称任意第三方字体都兼容。

## 桌面选择器与官方字体库

`python scripts/custom-font-wizard.py` 提供本地文件选择、许可确认、异步生成和官方库浏览器入口（需要 Python Tk）。
“选择/导入”仅指本地文件，不上传到服务器。取消文件对话框不修改任何内容；生成期间禁止重复操作/关闭。
它是独立桌面辅助工具，不是游戏内设置页，也不自动下载。下节的游戏内字体页另外实现了固定版本、哈希校验及取消的快速下载服务。

可选来源：
- Noto CJK：https://github.com/notofonts/noto-cjk （OFL，中文）
- 思源黑体：https://github.com/adobe-fonts/source-han-sans （OFL，中文；选兼容TTF，不能直接导入OTF/CFF）
- 霞鹜文楷：https://github.com/lxgw/LxgwWenKai （OFL，中文）
- Source Sans：https://github.com/adobe-fonts/source-sans （OFL，西文，中文会走后备）

获取时保留随附许可与版权说明。不同发布包的格式/字重/字符覆盖不一样，这份列表不是所有发布包的兼容性保证。

## 微软雅黑

微软雅黑是 Windows 字体，不是可直接再分发的开源字体。微软官方字体页列出 Microsoft/Founder 权利信息，并提供软件/硬件再分发授权咨询链接。
Windows 字体 FAQ 区分在 Windows 本机应用中显示文字和将字体文件复制给别人；系统安装并不自动授予模组打包权利。
因此不自动扫描、复制或转换 Windows 的微软雅黑 TTC；本工具也不支持 TTC。需要嵌入式分发时应先取得覆盖具体产品用途的许可。

- https://learn.microsoft.com/en-us/typography/font-list/microsoft-yahei
- https://learn.microsoft.com/en-us/typography/fonts/font-faq

桌面助手语法检查通过；没有进行 Tk 桌面交互实测。桌面助手的字体库按钮仅为官方链接，与下节游戏内下载功能不同。

## 游戏内字体入口（新增，验收状态见提交说明）

在控制面板的“偏好设置”中向下滚动到“自定义字体”，点击“打开”：

- 将可信的本地 `.ttf` 拖入游戏窗口，或者粘贴完整路径，再点击“导入并应用”。确认你有权使用后才会读取和安装；文件不会上传。
- 点击“霞鹜文楷 1.520”或“Source Sans 3.052”可快速获取开放字体。确认页明确显示官方 GitHub 原始文件服务域名、许可和下载行为。固定官方 commit、字体/许可各自 SHA-256 校验、不跟随重定向、有限超时和 32 MiB 字体上限。
- 准备和下载阶段允许取消；资源重载阶段显示等待且阻止重复操作。加载失败会尝试恢复原来的资源包选择。恢复内置字体不会删除玩家的字体文件，也不删除生成的包。
- 字体应用独立于偏好设置的其他未保存草稿。生成的包存在当前实例的 `resourcepacks` 下，名称以 `shbui-userfont-` 开头。可在原版资源包界面手动管理。
- 仅覆盖 `mp_ui` / `mp_ui_semibold` 音乐和采用该字族的面板文字；不会替换整个游戏或全体 UUK 控件。一个字体文件用于两个字重，不制造假粗体。缺字交给原版字体回退。
- 当前仅支持静态、独立的 TrueType glyf TTF。不支持 TTC、OTF/CFF、WOFF 或可变字体；不自动提取或转换系统字体。执行基本目录/表边界校验，但这不是恶意字体沙箱，请勿导入不可信文件。
- 没有本地文件浏览对话框；使用系统文件管理器拖放/复制路径。小窗口（小于 280×256 GUI 像素）提示减小 GUI 缩放。

预置源：

- 霞鹜文楷：[官方提交 b799448](https://github.com/lxgw/LxgwWenKai/tree/b79944853c0725d9f9c8996f07f2476cff1e9463)，24 MB，包含中文，SIL OFL 1.1。
- Source Sans：[官方提交 ed18089](https://github.com/adobe-fonts/source-sans/tree/ed1808970eb3c7301c9a523bee26473ba0bb62fa)，421 KB，主要拉丁字符，中文使用回退，SIL OFL 1.1。

这两个字体都不是苹方或微软雅黑替代授权。其他官方开源字体库仍可通过前面的链接自行下载后导入；不能将任意 URL 加入快速下载来源。

### 本轮自动验证

- Forge 1.20.1 与 NeoForge 1.21.1：编译及 `LocalFontPackTest` 各 5 项通过（真实 OFL 字体 ZIP、格式15/34、路径隔离、格式/表边界、取消、符号链接/大小限制、固定官方源）。不是全仓回归。
- Python 脚本 6 项测试通过。
- 使用生产 `LocalFontPack.fetch` 在 JDK 17 下实际下载两个官方预置，字体及许可哈希通过并生成 ZIP（环境代理由测试命令提供，不写入产品代码）。
- 尚未完成游戏内资源重载/中文缺字回退/取消交互视觉验收，没有截图。草稿 PR 保留此限制。
