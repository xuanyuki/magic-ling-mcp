# 只读采集与详情读取审计

审计对象为本机 PC JAR **0.9.6.5 / 2026100600** 的 `javap -c -p` 字节码和本项目调用点。版本不匹配不安装采集钩子。本文明确哪些是新增详情白名单、哪些是继承原观察器的显示读取；不宣称对每个子类完成了穷举审计。

## 新增直接详情门禁

| 方法/值 | 审核结果 | 调用条件 |
|---|---|---|
| `Item.info()` | 会动态分派 desc，并可进入开发说明/附注路径；不能统一视作纯读取 | 从不直接调用；只读游戏正常打开检视页后已经渲染的结果 |
| `Item.desc()` 基础实现 | 本地化字符串读取 | 仅 `isIdentified()==true` 且反射确认实际声明者就是基础 Item；任意覆写都转 open_page_required |
| `Item.visiblyUpgraded/visiblyCursed` | 受已知标记约束的可见显示值 | 前者再以 levelKnown、后者再以 cursedKnown 门禁；绝不直接导出内部 level/cursed |
| `HeroClass/SubClass.title/desc` | 当前枚举本地化读取 | 当前英雄职业/转职；不是枚举所有隐藏解锁项 |
| `Talent.title/desc/maxPoints` | 天赋显示说明和点数读取 | 当前英雄已有天赋树；点数来自该树 |
| `Bless.desc()` | 显示文本读取 | 精确类等于 Bless，不允许未知子类覆写 |
| `Haste.desc()` | 显示文本读取 | 精确类等于 Haste |
| `Hunger.desc()` | 饥饿显示/持有物条件和本地化文本读取，无写状态路径 | 精确类等于 Hunger；重复详情测试包括冷却、调度和 RNG |
| `Level.tileName/tileDesc` | 可能被楼层子类覆写；不能一般化批准 | 不动态调用；标准地形由 Policy 使用已审核基础 Level 键映射，秘密地形先掩码 |

允许类型以 `Details.java` 为准。`open_page_required` 是有意保留的信息边界，不触发点击、页面构造、hover、自动切页或滚动。游戏本身打开页面可能调用详情方法，这是用户正常界面流程；观察器只读渲染结果。

## 既有显示读取和发现规则

| 读取面 | 边界 |
|---|---|
| Item 显示名、数量、鉴定、status | 延续原观察器 HUD/背包显示读取，不输出 Java 类型、trueName、trueLevel；未知药剂仅外观名；未鉴定对象不会进入新增 desc 白名单 |
| Buff 图标、name、iconTextDisplay | 只有 icon 不为 NONE 的状态才公开；新增完整 desc 另受精确白名单约束 |
| Mob 名称、位置、血条 | 当前 FOV、sprite 可见、未隐身且存活；未揭露中立 Mimic 排除；仅粗略高/中/低血条 |
| Heap | 当前 FOV 且非 hidden；箱子只给显示名，只有 HEAP/FOR_SALE 读取顶部物品，不读箱内列表 |
| Trap/Plant | 当前 FOV；Trap 还要求 visible；秘密地形提前转墙/普通地面 |
| Blob.tileDesc | 延续原 visibleEffects 显示路径；仅有当前 FOV 活跃格子时调用，不导出内部浓度；没有对所有自定义 Blob 覆写做穷举纯度证明 |
| visited/mapped/heroFOV | 仅建立本层玩家已知地形，不读取全层实体详情；已记忆的视野外地形不实时刷新 |

`name/status/iconTextDisplay/Blob.tileDesc` 等既有虚调用的每个游戏子类并未逐一证明纯度。当前测试证明指定隔离样本重复读取不改变被监测状态；如后续发现特定显示覆写有副作用，应缩减为该类型已渲染的文字，不扩大详情白名单。新增详情不会据此自动获准。

## 界面读取

直接遍历已有 Gizmo/Group 和可见控件，读取 RenderedTextBlock/BitmapText/RenderedText 的现有文本、Button.active、CheckBox.checked、已绑定 ItemSlot/Talent/Buff 和 Tab.selected。跳过不可见/不存在的节点及相机，只采最上层 Window，并使用 camera 和 ScrollPane 可见范围裁剪。部分裁剪的段落只取完全可见的渲染词节点，隐藏 backing text 不返回。

纯图标名称来自具体 owner-field 白名单，如 Toolbar.btnWait、AlchemyScene.cancel 和炼金内部 InputButton.slot。炼金内部类型同时校验完整类名前缀，避免相同简单类名误识别。HeroSelectScene 的 setSelectedHero 字节码确认 startBtn 绑定当前所选职业，因此该按钮 selected=true。不猜测其他图标含义。

没有调用 `onClick/onSelect/hoverText/hide/destroy`、配方生成、合成、动作、寻路或存档加载方法。未知窗口和自定义图形只做通用控件读取，并提示截图。

## 观测时机与截图

Game.render 返回时采集；Actor 正在处理且并非 Hero 等待输入时跳过，采集前后都检查。这不是对游戏线程加全局锁，也不是任意持续动画下的一致事务。超过 10 秒无稳定观测会显式陈旧。

截图请求经队列交给同一渲染采集点，读取 GL framebuffer，写本地 PNG；发布线程强制生成同版本快照并返回 session/seq。HTTP 线程不操作 GL，不发送游戏输入。PNG 文件可能包含玩家画面，只有请求截图时生成。

## 隔离验证边界

BoundaryTest 在隐藏 LWJGL 窗口、临时 FileUtils 根目录和独立偏好文件中运行；断言 100 次重复采集保持 HP、位置、物品数量、RNG 序列化和 Actor 时间/集合/编号/优先级指纹。未知物品 desc/info、隐藏陷阱 name、箱内物品 toString 设为触发即失败的 tripwire。界面逐样本重复采集并比较状态、随机数和调度。

测试为了建立场景会显式调用场景/窗口构造函数。AlchemyScene 夹具暂置 HP=0，绕过不完整合成局的场景进入自动保存，然后恢复；只在测试中调用已审核的 InputButton.item(Food) 绑定槽位。两者均不在观察器路径中。测试结束不调用可能返回原料、打开商店或改变复活状态的游戏 hide/destroy 钩子，直接退出隔离 JVM。动态附加测试只附加自己启动的隐藏夹具 PID。
