# 余烬军团（Ember Legion）

> **像素风单机 PVE 自走棋肉鸽游戏** —— Roguelike 爬塔 + 自走棋摆阵 + 开箱爽感

星辰一颗颗熄灭的"熄星之夜"后，黑潮侵染大陆。你是一名**执棋者**，以"战棋之约"召集佣兵与部族组成军团，穿越翡翠林地、亡者墓穴与霜之峰，在星辰彻底熄灭之前，把光点回夜空。

- **技术栈**：Java + [LibGDX](https://libgdx.com/)
- **目标平台**：单机 PC（LWJGL3）/ Android（横屏）
- **美术风格**：像素风（32px 格子 / 640×360 虚拟分辨率）
- **单局时长**：20~30 分钟（固定 25 轮）

## 游戏特性

- **自走棋战斗**：6×7 共享棋盘布阵，棋子自动索敌、移动、攻击、施放技能（实时 60Hz 逻辑 tick）
- **Roguelike 爬塔**：固定 25 轮、3 个 Boss 节点（第 7 / 15 / 25 轮），三个场景（森林 → 墓穴 → 雪山）递进解锁
- **失败原地重试**：战败不出局、不推进轮次，敌阵保持不变——侦察与针对性调整始终有价值
- **纯宝箱经济**：无工资 / 无利息 / 无连胜奖，金币几乎全部来自宝箱三选一与卖棋子，每一金都要精打细算
- **羁绊与升星**：6 大首发羁绊（2/4/6 门槛分级），3 合 1 自动升星（上限 3 星）
- **局外成长**：英雄熟练度跨局积累，解锁专属加成、专属传奇棋子与新场景
- **确定性回放**：随机消耗点全清单管理（敌阵 / 商店 / 宝箱 / 暴击），同种子 = 同战局，支撑录像回放与零后端单元测试

完整规则见 [docs/gdd_idea_0.0.0.1.md](docs/gdd_idea_0.0.0.1.md)（GDD），世界观设定见 [docs/game_lore_design.md](docs/game_lore_design.md)。

## 技术栈

| 项 | 选型 | 说明 |
|----|------|------|
| 语言 | Java（语言级别 8） | 换取 Android 最大兼容；数据类手写 final 字段 POJO |
| 游戏框架 | LibGDX 1.14.0 | 2D 像素渲染 / Scene2D UI / 多平台一套代码 |
| 构建 | Gradle（wrapper 9.2.1） | 含 foojay 工具链自动解析 |
| 测试 | JUnit 5 + AssertJ（core 模块） | 逻辑层零 GL 依赖，无需启动后端即可直测 |
| AI / 寻路 | gdx-ai（已引入备用） | MVP 用优先链决策，Boss 行为树后置评估 |
| PC 后端 | LWJGL3 | 默认窗口 1280×720（640×360 整数 2 倍缩放） |
| 移动后端 | Android（横屏） | iOS 模块保留休眠，暂不构建 |

## 快速开始（Quick Start）

### 环境要求

- **JDK 17+**（运行 Gradle 构建；项目代码语言级别为 Java 8，foojay 插件可自动补齐工具链）
- Android 构建另需 **Android SDK**（`local.properties` 指定路径）；仅跑 PC 无需

### 克隆并运行（PC）

```bash
git clone git@github.com:voidvvv/ash_legion_auto_chess.git
cd ash_legion_auto_chess

# Windows
gradlew lwjgl3:run

# macOS / Linux
./gradlew lwjgl3:run
```

> **当前状态**：项目处于"设计文档齐备、业务代码起步"阶段，`lwjgl3:run` 目前运行为 libGDX logo 模板画面，核心玩法按 GDD §十二路线图逐阶段实现。

### 运行测试

```bash
gradlew core:test        # JUnit 5 单元测试（逻辑层零后端直测）
```

## 构建（Build）

```bash
# PC 可运行 jar（输出：lwjgl3/build/libs/ember-legion-<version>.jar）
gradlew lwjgl3:jar

# Android Debug APK（输出：android/build/outputs/apk/debug/）
gradlew android:assembleDebug

# 全模块构建 / 清理
gradlew build
gradlew clean

# 其他常用
gradlew core:test          # 测试
gradlew android:lint       # Android 静态检查
```

`ios` 模块为休眠状态（保留在 settings.gradle 中但不构建），将来上架 iOS 再启用。

## 目录结构

```
├── assets/                        # 唯一资源根（PC/Android 双端挂载）
│   ├── data/                      #   静态数据 JSON（units/synergies/scenes/equipments/heroes）
│   ├── units/  ui/  fx/  audio/   #   精灵图集 / UI / 特效 / 音频（按阶段创建）
├── core/                          # 主逻辑模块（全平台共享）
│   └── src/main/java/com/voidvvv/kz_auto_chess_n/
│       ├── Main.java              # 入口（Phase 4 改为 extends Game）
│       ├── screens/               # 装配层：六 Screen + InputMultiplexer 组装
│       ├── input/                 # 翻译层：坐标 → 命令（死区/多触点/模态阻断）
│       ├── command/               # 命令层：纯数据命令 + CommandManager
│       ├── systems/               # 逻辑核心：BattleSystem / SynergySystem / WaveGenerator / ShopSystem
│       ├── entities/              # 运行时状态：Unit / BattleUnit / Player / BattleState
│       ├── data/                  # 静态模板：UnitData / SkillData / SynergyData / EquipmentData
│       ├── config/                # JsonLoader + GameBalance（平衡常量）
│       ├── render/                # 表现层：棋盘渲染 / 单位视图 / 飘字 / HUD
│       └── utils/
├── lwjgl3/                        # PC 启动器（LWJGL3）
├── android/                       # Android 启动器（横屏）
├── ios/                           # 休眠模块
├── docs/                          # 设计文档（见下）
└── .report/                       # 评审报告
```

> 除 `Main.java` 启动模板外，core 内各包按开发阶段逐包创建（详见 [docs/project_structure_design.md](docs/project_structure_design.md) §六出生时间表），不预建空目录。分层硬约束：`entities/data/systems` 禁止 import 渲染类与 `Gdx.*`。

## 文档索引（docs/）

| 文档 | 内容 |
|------|------|
| [gdd_idea_0.0.0.1.md](docs/gdd_idea_0.0.0.1.md) | **GDD 主文档**：玩法规则 / 经济 / 棋盘棋子 / 羁绊装备 / 关卡波次 / 美术规格 / 路线图 |
| [game_lore_design.md](docs/game_lore_design.md) | 世界观：熄星之夜 / 三章剧情 / 机制↔叙事对应表 |
| [architecture_design.md](docs/architecture_design.md) | 运行时架构：三态域 / 双实体 / 命令系统 / 阶段状态机 / Screen / 持久化 |
| [battle_design.md](docs/battle_design.md) | 战斗实现级设计：主循环（H 语义）/ AI 优先链 / 移动 / 弹道 / 技能 / 异常状态 / 属性管线 |
| [data_schema_design.md](docs/data_schema_design.md) | 数据层 Schema：JSON 字段终版 / 同名词表 / 校验规则（Phase 1 依据） |
| [render_design.md](docs/render_design.md) | 渲染架构：视口坐标 / 双通路渲染 / 插值 / 事件驱动表现 / HUD 布局（Phase 4 依据） |
| [user_input_design.md](docs/user_input_design.md) | 输入控制与命令系统：四层流向 / 输入陷阱 / CommandManager |
| [project_structure_design.md](docs/project_structure_design.md) | 目录结构 / 构建决策 / 分层依赖规则 / 测试策略 |
| [diagrams/](docs/diagrams) | 交互流程图 / 战斗流程图 / 战场布局（HTML + Markdown） |

## 开发路线图

| 阶段 | 里程碑 | 状态 |
|------|--------|------|
| Phase 1 | 数据层：JSON 加载器 / GameBalance / 单元测试 | 🚧 起步（Schema 已定稿，测试基建就绪，业务代码未开始） |
| Phase 2 | 波次生成：WaveGenerator 半随机逻辑 + 控制台模拟 | ⏳ |
| Phase 3 | 战斗引擎：索敌 / 移动 / 普攻 / 技能 / 超时判定 | ⏳ |
| Phase 4 | 棋盘渲染：Screen 架构 / 网格交互 / 1280×720 | ⏳ |
| Phase 5 | UI 整合：商店 / 备战席 / 宝箱 / 英雄选择 | ⏳ |
| Phase 6 | 局外成长：熟练度 / 场景解锁 / 存档 | ⏳ |
| Phase 7 | 打磨：动画 / 音效 / 数值平衡 | ⏳ |

---

> 本项目由设计文档驱动开发：所有机制先在 `docs/` 讨论定稿、再落地代码，文档冲突时以 [docs/architecture_design.md](docs/architecture_design.md) 为准。
