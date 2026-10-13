# 万象之杖独立插件

这是针对 `Magic Ling Pixel Dungeon 0.9.6.5` 的独立插件工程草案。它只在 `magic-ling-plugin` 目录维护；工作区中的原始 JAR、APK 以及两个解包目录保持只读，不作为构建输出目录。

插件目标：在查看 `万象之杖` 时打开一个图鉴窗口，完整列出怪物，并分别标记“本杖已击杀”和“本杖未击杀”。窗口只读取法杖已有的 `killedTypes` 数据和游戏已有的 `Bestiary` 数据，不改变伤害、成长、充能或击杀判定。

## 当前边界

0.9.6.5 的公开源码没有发现外置 mod loader、`ServiceLoader` 扫描或插件 JAR 自动发现入口。桌面端的 release 构建还会把运行时依赖合并进单一 fat JAR，Android 则把 `core` 编译进 APK。因此，把本工程单独编译成一个 JAR 后，现成游戏不会自动加载它。

本工程采用“独立模块 + 明确宿主接入层”设计：

- `api` 是不依赖游戏 UI 的数据模型，可以单独测试和演进。
- `host` 通过反射读取法杖的私有 `killedTypes`，并通过宿主适配器提供百科条目；这样核心逻辑不需要复制解包代码。
- 仅需在源码构建中增加一个很小的入口，把法杖详情菜单的按钮/长按动作接到窗口。窗口实现可以复用 `WndJournal` 的 `ScrollingGridPane` 和 `WndJournalItem`。
- PC 端若坚持不重建游戏，可以另做 Java Agent/字节码注入版；该路线对版本和启动参数敏感，且不能覆盖 Android，不作为第一版。

## 构建

需要 JDK 11 或更高版本。核心模块没有第三方依赖。当前工作区没有提交 Gradle wrapper，可用项目自己的 Gradle 安装运行 `test`；也可以用下面的零依赖命令验证模型：

```powershell
$out = "out/classes"
New-Item -ItemType Directory -Force $out | Out-Null
$files = Get-ChildItem src/main/java,src/test/java -Recurse -Filter *.java
javac -encoding UTF-8 -source 11 -target 11 -d $out $files.FullName
java -ea -cp $out com.magicling.myriad.api.MyriadCodexModelSelfTest
```

仓库没有把上游源码复制进来，也不会把生成物写入解包目录。实际宿主接入时，使用与游戏完全相同的 `0.9.6.5 / 2026100600` 源码构建，并把本工程作为独立 Gradle 子项目或源码依赖加入；发布包仍输出到单独的 `dist/`。

## 数据口径

- `killedTypes` 是每一把法杖实例自己的集合，按敌人运行时类名去重，随物品存档。
- `Bestiary` 的 `seen`/`encounterCount` 是全局百科记录，跨局且包含其他武器击杀；它只负责提供完整条目、名称和图标信息。
- 插件的“已击杀”标记只看 `killedTypes`，不会把全局百科击杀误算成本杖击杀。
- 如果存档里出现百科未收录的类名，模型会保留一个“未映射条目”，避免静默丢失数据。

## 版本锁定

当前适配目标：

- 游戏版本：`0.9.6.5`
- 构建标识：`2026100600`
- 法杖类：`com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.spdtomlpd.StaffofMyriadThings`
- 关键字段：`killedTypes`

如果上游改名、混淆字段或调整 `Bestiary`，宿主适配器应先失败并显示兼容性提示，不应悄悄把列表当成空列表。

## 许可与发布

公开仓库标注 GPL-3.0。发布可分发构建前，应保留相应许可证和版权声明，并向原作者确认该版本的 mod/再分发政策。这个工程目前只包含新写的插件代码和适配文档，不包含上游游戏源码或原包。


