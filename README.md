# 万象之杖外置图鉴 Agent

本项目为 `Magic Ling Pixel Dungeon 0.9.6.5` 的桌面版提供一个外置 Java Agent。可以在启动时用 `-javaagent` 加载，也可以在游戏运行后用 JDK Attach API 注入。Agent 拦截万象之杖的物品详情窗口并添加“击杀图鉴”按钮。游戏 JAR 不会被改写，也不重打包或重签名。

它会在游戏进程内存中改动窗口行为；“不改游戏包”不等同于“运行时完全不注入”。当前已实现 PC Agent。Android APK 没有 Java Agent 启动入口，但本 APK 静态分析显示 `debuggable=true`，因此可评估通过 ADB `am attach-agent` 加载 Android 原生 JVMTI Agent；这条 Android 实现尚未开发和设备验证。

## PC 使用

1. 确认游戏 JAR 位于本工程上一级目录，文件名为 `魔绫地牢-V0.9.6.5.jar`。
2. 安装能运行该游戏的 JDK 和 Python 3；本项目使用 JDK 18 验证。Agent 源码的编译目标为 Java 11，构建时仍需使用能读取原游戏类文件的 JDK。首次构建需联网下载 Byte Buddy。
3. 在 PowerShell 运行：

   ```powershell
   .\magic-ling-plugin\build-agent.ps1
   .\magic-ling-plugin\test-agent.ps1
   ```

4. 双击 `magic-ling-plugin\run-with-agent.bat` 启动，查看万象之杖详情时点击“击杀图鉴”。

如果游戏已经运行，可在同一 Windows 用户和兼容的 JDK 权限下用 Attach API 注入：

```powershell
.\magic-ling-plugin\attach-agent.ps1 -ProcessId 12345
```

将 `12345` 换成游戏 Java 进程 PID。注入成功后重新打开万象之杖详情。若游戏使用不同 Java 运行时、禁用了 Attach，或权限不同，附加可能失败；此时用上面的外置启动器启动。

也可以直接用命令行启动：

```powershell
java -javaagent:".\magic-ling-plugin\dist\myriad-codex-agent-0.1.0-SNAPSHOT.jar" -jar ".\魔绫地牢-V0.9.6.5.jar"
```

Agent JAR 和依赖放在 `magic-ling-plugin` 工程中；启动时读取原游戏 JAR。无需覆盖或修改原包。请用游戏原本支持的 Java 运行时启动。

## 图鉴数据

- 该武器实例的 `killedTypes` 决定“本杖已击杀/未击杀”；同一物种按类名去重。
- 游戏 `Bestiary` 提供完整怪物条目、百科发现状态和分类。全局百科击杀数不会被误算成这把杖的击杀。
- 未在当前百科列表中映射的法杖存档记录会显示为“未映射”，不静默丢失。
- 未发现且未被这把杖击杀的怪物继续按原图鉴习惯显示为 `???`。

## 当前验证范围

`build-agent.ps1` 会编译外置 Agent 并产出 `dist/myriad-codex-agent-0.1.0-SNAPSHOT.jar`。`test-agent.ps1` 检查 Agent 能否在独立 JVM 中拦截匹配的详情窗口构造器，并检查它能否转换本地原游戏 JAR 里的 `WndUseItem` 类；`test-attach.ps1` 用临时 Java 进程验证外部 Attach 和重新转换。这两项不启动游戏或更改存档。

`test-runtime-ui.ps1` 会在隐藏 OpenGL 窗口中运行原游戏的 `GameScene`、`WndUseItem` 和图鉴窗口。它在输出目录中创建独立测试角色、配置和临时存档，通过代码调用按钮事件并保存真实渲染截图；不会操作桌面鼠标键盘。横屏与竖屏分别运行：

```powershell
.\magic-ling-plugin\test-runtime-ui.ps1
.\magic-ling-plugin\test-runtime-ui.ps1 -Orientation portrait
.\magic-ling-plugin\test-runtime-ui.ps1 -Scenario combat
.\magic-ling-plugin\test-runtime-ui.ps1 -Scenario combat -Orientation portrait
.\magic-ling-plugin\test-runtime-ui.ps1 -Scenario evidence
```

`evidence` 场景按顺序截取同一把已装备法杖的装备栏、首次击杀前后原始数据、新增图鉴弹窗和已击杀怪物详情；同时保存前后武器描述及序列化数据。击杀由原游戏战斗代码产生。

2026-10-15 已在宿主机隐藏进程中通过横屏、竖屏的图鉴和战斗测试。237 个怪物图鉴条目及额外未映射记录完整显示；十二条图鉴击杀记录突破原描述列表的十项显示上限，物品按钮、条目详情、滚动末尾和未发现条目遮罩可用。读取及开窗前后的法杖、角色、活动 Actor、全局图鉴及游戏随机数生成器状态一致。

战斗场景通过原游戏 `HeroAction.Attack`、攻击动画和 Actor 调度器生成击杀记录：小鼠首次击杀计数、重复同种去重、豺狼新增种类、图鉴未收录的 Snake 保留为未映射条目、原始法杖序列化往返均通过；再次开窗显示最新记录。二十轮开窗、查看详情、滚动及关闭后，检查到的角色、法杖、Actor 集合、图鉴和随机数状态保持一致。测试只在独立临时角色中设置装备、力量和相邻怪物，未直接写入这些战斗击杀记录；仍未覆盖完整自然游玩或长时间运行。

2026-11-02 重新通过构建、Agent、动态附加、模型、横竖屏图鉴及战斗回归，并验证含空格路径和无效 Agent 的拒绝行为。具体范围见 [提交前检查记录](docs/检查记录-2026-11-02.md)。

修复了图标构造错误、大型图标越界及预览消耗游戏随机数的问题。原游戏读档会打印缺少 `boss_types` 的警告，但击杀集合往返成功；未改动原游戏来消除此警告。

虚拟机 VMware Tools 执行通道异常，未获得虚拟机通过结果；用户已改为本机后台测试，并要求清理测试镜像。测试虚拟机、快照、测试 ISO 及临时副本已清理，原有系统安装 ISO 保留。测试记录及各组截图路径见 `docs/测试记录-2026-10-15.md`。

Agent 锁定 `0.9.6.5 / 2026100600`。其他版本、字段改名、窗口改版或 JVM 兼容差异可能导致入口失效。源码和外置产物在工程内维护，原始 JAR/APK 与解包目录保持不动。

Android 没有对应的 `-javaagent` 启动参数。Android 14 的系统 shell 提供 `am attach-agent`，可向 `debuggable` 应用加载原生 JVMTI `.so`；本 APK 的清单静态分析显示 `debuggable=true`，因此可能在不改 APK 的情况下通过 USB 调试注入，但需要按目标 ABI 编译 Android 原生 Agent，并实现 ART/JVMTI 到游戏 UI 的桥接。本项目尚未实现或在设备上验证这条路线；若设备/系统不允许 shell attach，才需要另评估 root 注入框架或源码构建。

## 上游许可

上游公开仓库标注 GPL-3.0。发布可分发版本前请核对适用许可证与原作者对外置修改的政策。当前工程没有包含上游游戏源码。
