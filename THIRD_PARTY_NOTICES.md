# 第三方组件与许可说明

本项目采用 `GPL-3.0-or-later`，但不改变第三方代码、游戏及素材原有的权利归属与许可。本文件说明主要直接依赖和打包组件，不是完整依赖审计报告。

## 魔绫地牢及其上游

适配游戏为 `LingASDJ/Magic_Ling_Pixel_Dungeon` 的 `0.9.6.5 / 2026100600` 版本。其 `LICENSE` 为 GNU GPL v3；核心源码中的许可声明允许 GPL v3 或任何后续版本。

原游戏及其上游 Pixel Dungeon、Shattered Pixel Dungeon 的相关权利归各自权利人所有。本项目不是原作者发布的官方插件，不表示已获得原作者背书。引用游戏名称和 Java 包名仅用于说明适配对象、编译及接口集成。

本项目通过用户自行提供的游戏 JAR 编译和运行，不将游戏主体、图片或音乐打入观察器 JAR。游戏素材可能有单独的许可，尚未逐项审计；本项目的许可证不授权重新分发这些素材。运行生成的截图、日志和游戏文字也不因被本工具输出就自动变为本项目授权的内容。

上游与许可核对地址：

```text
https://github.com/LingASDJ/Magic_Ling_Pixel_Dungeon
https://github.com/LingASDJ/Magic_Ling_Pixel_Dungeon/blob/0.9.6.5/LICENSE
https://github.com/LingASDJ/Magic_Ling_Pixel_Dungeon/blob/0.9.6.5/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/Dungeon.java
```

## Agent 打包依赖

| 组件 | 当前版本 | 原许可 | 发行方式 |
|---|---|---|---|
| Byte Buddy | 1.17.7 | Apache-2.0 | 随观察器 JAR 打包 |
| ASM（Byte Buddy 内含） | 9.8 | BSD-3-Clause | 随 Byte Buddy 打包 |

Byte Buddy 原始 `META-INF/NOTICE` 中的归属为 `Copyright 2014 - Present Rafael Winterhalter`。其内含 ASM 原始声明为 `Copyright (c) 2000-2011 INRIA, France Telecom`。

打包脚本保留依赖原有的 `META-INF/LICENSE`、`META-INF/NOTICE` 与 `META-INF/licenses/ASM`。本项目的 `LICENSE`、`NOTICE` 和本文件单独存放在 `META-INF/game-text-bridge/`，不覆盖依赖的许可。

## Python MCP 环境

官方 Python MCP SDK 的当前锁定版本为 `1.30.0`，采用 MIT 许可证，原始声明为 `Copyright (c) 2024 Anthropic, PBC`。

SDK 及其传递依赖由 `uv.lock` 锁定，通过 `uv sync --frozen` 安装，不包含在 Agent JAR 中。各依赖的授权以所安装发行包内的许可证、版权及 NOTICE 为准；若另行分发 Python 环境或这些依赖，应同时保留对应声明。

## 发布说明

发布观察器二进制时，应按 GPL 提供对应版本的源代码、必要构建脚本和许可说明，并满足随包第三方组件的许可要求。建议在同一个 GitHub Release 提供 Agent JAR、对应源代码和构建说明，不要只提供 JAR 而不说明源码取得方式。

本项目的许可范围仅为 `game-text-bridge` 项目自身内容，不延伸到工作区相邻项目、原游戏发行文件、运行输出或第三方依赖。AI 开发与风险声明是风险告知，不额外限制 GPL 已授予的权利，也不代替针对具体发行内容的许可核查。
