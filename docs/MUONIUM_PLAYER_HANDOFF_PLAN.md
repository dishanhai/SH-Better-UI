<!-- 作者：山海／dishanhai -->
# SHBUI 播放器移植交接计划

本轮交接于 2026-11-21 完成。独立工作树 `C:/jtmp/shbui-muonium-port` 的提交 `3676132f` 已发起 [PR #108](https://github.com/dishanhai/SH-Better-UI/pull/108)，由 `feat/muonium-player-2.6-port` 合入 `main` 待审。创建时远端主分支仍为 `d91e3bd8`。

**验收结果**　四端普通发行构建、完整测试、JAR 校验均通过。26.2 隔离游戏日志 `C:/jtmp/shbui-muonium-port/tmp/mconfig-keyboard-final.log` 已出现 `MCONFIG-KEY-QA PASS`，测试客户端已退出。Forge 成品探针日志在 `tmp/forge-pr-audio.log`。各平台测试统计、成品哈希与剩余外部验证见[移植说明](MUONIUM_PLAYER.md#核验成品)和[项目 TODO](../TODO.md)。下文保留接手时的状态与复验命令。

## 用户决定与版本基线

- 目标是把 MuoniumPlayer 的完整功能移植进 SHBUI。自有欢迎页、播放器、设置和 HUD 使用 **SHBUI** 署名。上游源码归属、原始 LICENSE／NOTICE、MP 网络协议、旧配置路径及预设格式均保留。
- **SHBUI 主题、SHBUI 控件分别可选，默认均关闭。** 切换只改变本机呈现。共用控件与字体属于 UUK。右 Shift 打开播放器，H 打开 HUD 设置。
- 上游基线为 MuoniumPlayer 2.6.0，提交 `eb541684e9d681d8145f5f9d681cf31980646dc7`。[上游源码](https://github.com/zihe114514/MuoniumPlayer-multiversion/tree/eb541684e9d681d8145f5f9d681cf31980646dc7)。本机可直接用 `tmp/mp-upstream-reference` 对照。
- 已验收版本的范围、游戏入口、Apple MusicKit 路线和可选第三方外壳见[移植说明](MUONIUM_PLAYER.md)。上一版四端普通构建合计 9,501 项通过、24 项跳过、0 项失败。上游协议、JAR 校验和双客户端入房、跳转、暂停的证据分别见[校验脚本](../scripts/verify-muonium-jar.py)、`tmp/mp-invite-*.log` 和 `tmp/mp-paired-qa/driver/results/`。

## 这次新增了什么

| 范围 | 关键文件 | 当前证据与待验项 |
| --- | --- | --- |
| 五档耳机环绕 | [VHE](../src/muonium/java/com/muoniumplayer/core/viper/dsp/VHE.java)、[KEMAR 资源生成脚本](../scripts/generate-kemar-responses.py) | 已生成 20 个近／远耳响应，覆盖五个角度和 44.1／48 kHz。独立测试检查了实际资源的跨耳输出、本地覆盖、缺损旁路和分块处理。 |
| 两个房间预设 | [房间核读取](../src/muonium/java/com/muoniumplayer/core/viper/ViperRoomKernels.java)、[安装逻辑](../src/muonium/java/com/muoniumplayer/core/viper/ViperBuiltin.java)、[生成脚本](../scripts/generate-room-responses.py) | 两个 Surrey 房间各有两种采样率的四声道 WAV。文件顺序为 LL、LR、RL、RR。独立测试覆盖新装、旧版标记、后放入的 IRS、删除、冲突及不支持硬链接的退路。 |
| 切换预设及采样率 | [主处理器](../src/muonium/java/com/muoniumplayer/core/viper/ViperMasterProcessor.java)、[回归测试](../src/test/java/com/muoniumplayer/core/viper/ViperMasterProcessorTest.java) | 控制线程发布完整参数、卷积核和 DDC 快照。本机同名文件优先。独立测试的部分依赖使用了替身，须用正式构建及真实 JSON 再验。 |
| H 页键盘操作 | [设置页](../src/uuk-muonium/java/com/muoniumplayer/core/screens/hud/MConfigHudScreen.java)、[针对性测试](../src/test/java/com/muoniumplayer/core/screens/hud/MConfigDetailHitTest.java) | 已补底栏恢复按钮、默认取消、确认与色板焦点。恢复默认应重置外观并保留 HUD 位置。独立编译及测试已通过，游戏内处理见下节。 |

源数据已经随工作树存放，正常构建不需要重新下载生成。数据许可与转换依据见 [MIT KEMAR 声明](../src/muonium/resources/META-INF/third-party/mit-kemar/NOTICE.txt)及 [Surrey BRIR 声明](../src/muonium/resources/META-INF/third-party/surrey-brir/NOTICE.txt)。原版个人使用内核仍允许玩家从本机提供。

## 接手时的验收条件与复验步骤

1. **重跑 26.2 键盘实测，这是首个未通过的门槛。** 上一次会话 `85617` 已退出，Gradle 返回 0，日志 `tmp/mconfig-keyboard-live.log` 中没有 `MCONFIG-KEY-QA PASS`。客户端和本次 SMTC 助手已停止。临时驱动 `tmp/mconfig-keyboard-live-src/KeyboardLiveQa.java` 随后已补入启动警告页的继续操作，尚未重新编译运行。使用下方隔离命令重跑并保留新日志；须实际出现 PASS，且测试进程退出。勿在同一个 26.2 工程并行运行 QA 和普通构建。
2. **用项目工具链验证最新源码。** 先运行 Forge 的针对性测试，再构建四份普通发行包。四个平台的完整测试、资源处理和 `verifyLibraryJar` 均须成功。跑过带 `-I` 的隔离客户端后，用不带 `-I` 的命令重建该平台。
3. **用最新 Forge 成品实测音频。** 运行下方的离线探针。检查全新工作目录中总开关保持默认、两个房间预设可解析、跨耳输出非零、采样率切换生效以及 VHE 档位输出可区分。记录实际退出码和输出。探针由真实引擎处理脉冲，不能当作已完成扬声器试听。
4. **逐个核验交付物。** 用下表指定的文件名对照固定上游，检查 CRC、版权文件、协议源码及测试类泄漏。另核对每个发行包内有 20 个 `assets/sh_better_ui/audio/vhe/*.f32`、4 个 `assets/sh_better_ui/audio/rooms/*.wav`、两份 Surrey 预设和两套数据声明。记录测试统计与 SHA-256。最后将实际结果回填到[项目 TODO](../TODO.md)和[移植说明](MUONIUM_PLAYER.md)。

### 本机命令

从仓库根目录用 Git Bash 执行。先读当前目标目录的 `AGENTS.md`；可按需使用 `sh-project-workflow` 和 `java-project`。根目录的 `gradlew.bat` 配有 Forge 路由，NeoForge 使用明确的 Gradle 9.2.1 路径。

```bash
jdk21='C:/Program Files/Java/jdk-21.0.11'
jdk25='C:/Users/dishanhai/.gradle/jdks/eclipse_adoptium-25-amd64-windows.2'
g9='/c/Users/dishanhai/.gradle/wrapper/dists/gradle-9.2.1-bin/2t0n5ozlw9xmuyvbp7dnzaxug/gradle-9.2.1/bin/gradle.bat'

# 隔离客户端。先确认该工程没有仍在运行的 QA。
JAVA_HOME="$jdk25" "$g9" -p neoforge-26.2 --no-daemon --offline --console=plain \
  -I "$(cygpath -am "$PWD/tmp/mconfig-keyboard-live.gradle")" runClientOpenGl

# 针对性测试，然后构建用于交付的普通 JAR。
JAVA_HOME="$jdk21" ./gradlew.bat --no-pause --offline test \
  --tests 'com.muoniumplayer.core.viper.*' --tests '*MConfigDetailHitTest'
JAVA_HOME="$jdk21" ./gradlew.bat --no-pause --offline build
JAVA_HOME="$jdk21" "$g9" -p neoforge-1.21.1 --offline build
JAVA_HOME="$jdk25" "$g9" -p neoforge-26.1.2 --offline build
JAVA_HOME="$jdk25" "$g9" -p neoforge-26.2 --offline build
```

| 校验参数 | 相对仓库根目录的发行包 |
| --- | --- |
| `forge-1.20.1` | `build/libs/sh_better_ui-forge-1.20.1-1.0.13.jar` |
| `neoforge-1.21.1` | `neoforge-1.21.1/build/libs/sh_better_ui-neoforge-1.21.1-1.0.12.jar` |
| `neoforge-26.1.2` | `neoforge-26.1.2/build/libs/sh_better_ui-neoforge-26.1.2-1.0.13-alpha.8.jar` |
| `neoforge-26.2` | `neoforge-26.2/build/libs/sh_better_ui-neoforge-26.2-1.0.13-alpha.8.jar` |

每一行运行 `python scripts/verify-muonium-jar.py <校验参数> <发行包相对路径> --reference tmp/mp-upstream-reference`。旧 JAR 与新 JAR 同名，须核对本次日志与文件哈希。

**离线音频探针**已准备在 `tmp/viper-offline/OfflineViperProbe.java`。它和上述游戏 QA 都在 Git 忽略目录；换电脑或新克隆时需一并交接这些脚本。下列命令使用全新目录，避免读取玩家配置。本轮已在 PR 工作树的 Forge 正式 JAR 上执行通过。

```bash
jar=$(cygpath -aw "$PWD/build/libs/sh_better_ui-forge-1.20.1-1.0.13.jar")
deps='/c/Users/dishanhai/.gradle/caches/modules-2/files-2.1'
gson=$(cygpath -aw "$deps"/com.google.code.gson/gson/2.10.1/*/gson-2.10.1.jar)
slf4j=$(cygpath -aw "$deps"/org.slf4j/slf4j-api/2.0.9/*/slf4j-api-2.0.9.jar)
mkdir -p tmp/viper-offline/classes
classes=$(cygpath -aw "$PWD/tmp/viper-offline/classes")
run_dir=$(mktemp -d "$PWD/tmp/viper-offline/run-XXXXXX")
"$jdk21/bin/javac.exe" -encoding UTF-8 -cp "$jar;$gson;$slf4j" -d "$classes" tmp/viper-offline/OfflineViperProbe.java &&
  (cd "$run_dir" && "$jdk21/bin/java.exe" -cp "$classes;$jar;$gson;$slf4j" OfflineViperProbe)
```

## 尚需外部条件的验收

- 真实订阅账号、跨设备网络、本人提供的原版 ViPER 内核与真实模型仍待验证。Apple Music 的订阅整曲通过官方 MusicKit 浏览器会话播放；26.2 原生播放器图层需要 OpenGL。历史上一次 GLFW 原生崩溃及复测情况见项目 TODO。
- 早前隔离 QA 曾误将凭据写入临时文件和工具输出。相关文件已删除，用户已获通知，凭据轮换状态未知。接手的 QA 只取明确的 classpath、游戏目录和启动参数；不枚举进程环境、不复制账号配置。经验记录见 `.learnings/ERRORS-muonium-host.md`。
- 本轮已按授权提交并推送功能分支、创建 PR。消费实例部署和发行发布仍待单独安排。
