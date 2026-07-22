# Gemini Client

> 本项目仅用于 AI 编程、Minecraft 客户端架构、渲染管线与模组开发学习。  
> 项目不会提供、维护或承诺任何反作弊绕过能力。请遵守游戏服务器规则与相关平台条款。

Gemini 是一个基于 **NeoForge** 的 Minecraft 客户端研究项目，重点探索自研 UI/VFX 渲染、模块化客户端结构、事件系统、配置管理与 Shader 工作流。项目不依赖 Skija、MCef 等外置渲染库，而是围绕 Minecraft 原生渲染环境与 GLSL/Slang 构建视觉能力。

## 项目信息

| 项目 | 内容 |
| --- | --- |
| Mod ID | `gemini` |
| 当前版本 | `1.0-SNAPSHOT` |
| Minecraft | `26.2` |
| Mod Loader | NeoForge `26.2.0.25-beta` |
| Java | 25 |
| 主要作者 | XeContrast / AirZone-Team |
| 许可证 | 见 [LICENSE](LICENSE) |

## 功能概览

- **模块系统**：按 Combat、Movement、Player、Visual 等分类组织功能模块。
- **事件系统**：封装攻击、移动、渲染、按键、网络包等客户端事件。
- **自研 ClickGUI**：包含经典主题与 Material Design 3 风格界面。
- **GPU 渲染管线**：使用 GLSL/Slang 实现 2D、3D、后处理与粒子效果。
- **视觉效果模块**：包含 Trail、Glow、JumpCircle、KillEffect、TargetDisplay、SkyLantern 等渲染实验。
- **配置系统**：支持模块配置、按键绑定与本地配置读写。
- **资源与字体支持**：内置中文字体资源，并支持扫描可用 TTF 字体。

## 环境要求

在构建或运行前，请确认本地环境满足：

- Java 25
- Gradle Wrapper，仓库已包含 `gradlew` / `gradlew.bat`
- Minecraft `26.2`
- NeoForge `26.2.0.25-beta`

## 构建

Windows:

```powershell
.\gradlew.bat build
```

Linux / macOS:

```bash
./gradlew build
```

构建完成后，可安装的 JarJar 产物通常位于：

```text
build/libs/gemini-1.0-SNAPSHOT-installable.jar
```

如果只需要验证最终可安装产物：

```bash
./gradlew verifyDistribution
```

## 安装

1. 安装与项目版本匹配的 Minecraft 和 NeoForge。
2. 执行构建命令，生成 `installable` JAR。
3. 将生成的 JAR 放入 `.minecraft/mods` 目录。
4. 启动游戏并选择对应的 NeoForge 配置文件。

## 开发运行

启动 NeoForge UserDev 客户端环境：

```bash
./gradlew runClient
```

常用 Gradle 任务：

| 命令 | 说明 |
| --- | --- |
| `./gradlew build` | 编译并打包项目 |
| `./gradlew runClient` | 启动开发客户端 |
| `./gradlew processResources` | 处理资源与生成的 Shader 输出 |
| `./gradlew verifyDistribution` | 校验可安装发行包 |

## 项目结构

```text
src/main/java/geminiclient/gemini
├── base              # 基础服务、配置、按键、账号与菜单
├── commands          # 命令系统
├── customRenderer    # CPU / GPU 自定义渲染器
├── event             # 事件总线与事件类型
├── modules           # 客户端模块与分类实现
├── utils             # 数学、移动、渲染等工具类
└── values            # 模块配置值类型

src/main/slang        # Slang Shader 源文件与变体配置
src/main/resources    # NeoForge 元数据、Mixin 配置、字体和图标资源
buildSrc              # 自定义构建逻辑与 Slang 编译支持
```

## Shader 工作流

项目以 `src/main/slang/gemini` 中的 Slang 文件作为 Shader 源，构建时通过 `gemini.slang` 插件生成 Minecraft 可加载的资源。生成输出会接入 `processResources`，因此正常执行 `build` 即可完成 Shader 编译与资源处理。

## 贡献

欢迎提交 Pull Request 改进代码质量、文档、渲染效果、构建流程或兼容性。建议在提交前至少运行：

```bash
./gradlew build
```

提交内容请尽量保持单一主题，方便 review 与回滚。

## 免责声明

本项目仅用于学习与技术研究。使用者应自行确认使用场景是否符合游戏、服务器、平台和当地法律法规要求。因不当使用造成的账号限制、数据损失或其他后果，项目作者与维护者不承担责任。

© 2025-2026 AirZone-Team.
