<!-- 作者：dishanhai -->
# UUK JavaScript worker

Java 21 独立构建，锁定上游 `org.mozilla:rhino:1.9.1`，不使用 KubeJS 的 Rhino fork。
在仓库根目录运行：`./neoforge-1.21.1/gradlew -p uuk-script-worker build`。
产物为 `build/libs/uuk-script-worker.jar`，包含 worker、Rhino 与许可材料；没有 Minecraft、NeoForge 或 KubeJS classpath。

父模组将产物作为 `/sh_better_ui/automation/uuk-script-worker.jar` 资源打包，调用
`BundledScriptWorker.extract(cacheDirectory)` 后用 `ScriptWorkerSupervisor` 管理。
Windows 使用当前 Java 安装中的 `javaw.exe`，不弹控制台，不需要 Node。
关服必须显式 `close()`；JVM shutdown hook 和 worker 的父进程 PID／启动时间监视作为补充。

脚本读取纯 JSON `input`，最后一个表达式作为结果。`uuk.request('capability.id', payload)`
只记录纯数据意图，返回 `{queued:true,index:0}`。成功返回 JSON 的结构是
`{result:...,requests:[{capability:'...',payload:...}]}`。
请求尚未执行，父端必须再次验证能力、权限、用户身份、参数、预算和当前游戏状态，才能在所属线程执行。
能力执行后的纯数据可作为下一次运行的 `input`，每次运行不保留全局状态。

默认限制：source 64 KiB、input 32 KiB、output 64 KiB、每个请求 payload 8 KiB、16 个意图、
2 个并发进程、16 个排队请求、5 秒进程超时、64 MiB Java heap。JSON 深度上限 32、节点上限 4096。
IPC 采用固定 magic、带长度 UTF-8 帧，先检查长度再分配。
队列满即时返回 `BUSY`，超预算失败不会执行已记录的意图。禁止返回函数、Java 包装对象、循环引用等。
请求在读取 payload 前预占预算与返回索引；getter 重入也受相同预算约束，脚本捕获异常不能恢复超预算或部分序列化的请求批次。
class shutter 全拒绝、safe standard objects、解释器模式和拒绝 Java wrapping 是语言边界；
父进程硬超时可以终止无限循环、昂贵正则和其他不经过指令观察器的内建调用。

**这里不是操作系统沙箱。** Java heap 上限不等于进程 RSS 上限；JVM、线程栈、元空间和本地分配也占用内存。
进程运行于当前用户权限；若引擎或 JVM 存在可利用漏洞，语言层限制不能替代 OS 权限隔离。
不应在这层提供文件、网络、类加载、原始 Java 对象或直接游戏对象。父端不得把 worker 返回的 JSON 当作授权证明。

测试覆盖正常结果、能力意图、Java/文件/网络入口、反射、函数/循环/深度/输出限制、请求预算、
输入和 IPC 边界、无限循环、昂贵正则、排队、取消和 close 清理。`-Xmx` 验证不代表 RSS 限额或 OS 沙箱验证。
另有 getter 重入请求预算、重入调用顺序、捕获异常后部分批次拒绝的回归测试。
