# 🏗️ 项目目录结构设计与评审文档

> **版本**：V1.1（评审整改：Phase 3 出生表补 `BattleUnit`）  
> **依据**：GDD V0.6（`gdd_idea_0.0.0.1.md`）、运行时架构（`architecture_design.md`）、输入控制架构（`user_input_design.md` 1.1）、背景设定（`game_lore_design.md`）  
> **评审对象**：libGDX Liftoff 生成的多模块骨架 + 上述设计文档的目录需求

---

## 一、现状评审结论

| # | 发现 | 结论 / 处理 |
|---|------|-------------|
| 1 | 根目录 `assets/` 被 android（`../assets`）与 lwjgl3（resources + workingDir）双端挂载 | ✅ 沿用，单一资源源 |
| 2 | 根 build.gradle 自动生成 `assets.txt` 资源清单 | ✅ 沿用 |
| 3 | libGDX 1.14.0 / AGP 8.9 / Gradle 8 | ✅ 版本健康，不升级 |
| 4 | core 模块无测试依赖与 `src/test` 源集 | 🔧 **已修复**：补 JUnit 5 + AssertJ（见 §二） |
| 5 | settings.gradle 含 `ios` 模块 | 💤 **已定：保留但休眠**（不构建、不维护，将来上架 iOS 再启用） |
| 6 | `appName` 含 em-dash 特殊字符（`kz_auto_chess———_n`） | 🔧 **已修复**：改为 `ember-legion`，Android 显示名改为 `Ember Legion` |
| 7 | Java 8 语言级别（三处 sourceCompatibility = 8） | ⚠️ 知悉并接受：无 record/var，数据类手写 final 字段 POJO；换取 Android 最大兼容 |
| 8 | GDD §9.6 素材目录遗漏 `audio/` | 🔧 本文档补上 |
| 9 | `gdx-ai` 已引入但当前用不到 | ✅ 保留（未来寻路/行为树可用），不阻塞 |
| 10 | `Main.java` 为 logo 模板、`assets/libgdx.png` 占位 | ⏳ Phase 4 替换为 `Game` + Screen 结构 |
| 11 | `org.gradle.daemon=false` 导致每次构建冷启动 | 💡 可选优化：改 `true` 提升开发期构建速度（未改） |

## 二、已定的构建决策

| 决策 | 内容 | 落点 |
|------|------|------|
| iOS 模块 | **保留休眠**：目录与 include 均不动，永不主动构建 | `settings.gradle` 不变 |
| appName | **`ember-legion`**（jar/exe/项目名） | 根 `build.gradle` `ext.appName` |
| Android 显示名 | **Ember Legion** | `android/res/values/strings.xml` |
| 测试框架 | **JUnit 5（junit-jupiter 5.11.4）+ AssertJ 3.27.3**，`test { useJUnitPlatform() }` | `core/build.gradle` |
| Java 语言级 | **保持 8** | 不改 |

---

## 三、目标目录结构

```
ember-legion/（仓库 kz_auto_chess_n）
├── assets/                          # 唯一资源根（双端已挂载，勿动）
│   ├── data/                        #   静态数据 JSON —— Phase 1 创建
│   │   ├── units.json               #     棋子模板（GDD §10.3）
│   │   ├── synergies.json           #     羁绊（结构化效果字段）
│   │   ├── scenes.json              #     场景/敌人池/强度曲线 —— Phase 2
│   │   ├── equipments.json          #   装备 —— Phase 5
│   │   └── heroes.json              #     英雄（棋手）被动 —— Phase 6
│   ├── units/                       #   棋子精灵 atlas —— Phase 4
│   ├── ui/                          #   UI 九宫格/图标 —— Phase 5
│   ├── fx/                          #   特效 —— Phase 7
│   └── audio/                       #   音效/BGM —— Phase 7
│
├── core/src/
│   ├── main/java/com/voidvvv/kz_auto_chess_n/
│   │   ├── Main.java                # Phase 4 改为 extends Game（Screen 管理）
│   │   ├── screens/                 # 【装配层】六 Screen（Loading/MainMenu/RunSetup/
│   │   │                             #   Codex/Battle/RunResult）+ InputMultiplexer 组装；
│   │   │                             #   设置=复用 Dialog，非 Screen（详见 architecture_design.md §七）
│   │   │                             #   职责对应输入文档 §7.3"Screen 只做点火器"
│   │   ├── input/                   # 【翻译层】BoardInputProcessor / GlobalKeyProcessor
│   │   │                             #   坐标→命令；死区/多触点/模态阻断（输入文档 §2-3）
│   │   ├── command/                 # 【命令层】纯数据 GameCommand + CommandManager
│   │   │                             #   命令承载玩家操作，AI 不入队（输入文档 §7.1）
│   │   ├── systems/                 # 【逻辑核心】BattleSystem / SynergySystem /
│   │   │                             #   WaveGenerator / ShopSystem
│   │   ├── entities/                # 【运行时状态】Unit / Player / BattleState(棋盘)
│   │   │                             #   棋盘归 BattleState，不归 Player（GDD §10.2 注）
│   │   ├── data/                    # 【静态模板】UnitData / SkillData / SynergyData /
│   │   │                             #   EquipmentData（后续 SceneData / HeroData）
│   │   ├── config/                  # JsonLoader + GameBalance（平衡常量，拒绝魔法数字）
│   │   ├── render/                  # 【表现层】棋盘渲染 / 单位视图 / 伤害飘字 / HUD
│   │   │                             #   GDD §10.1 未含此包——Phase 4 起无它 screens/ 会膨胀
│   │   └── utils/
│   └── test/java/com/voidvvv/kz_auto_chess_n/   # 镜像 main 包结构，Phase 1 同步创建
│
├── lwjgl3/                          # PC 启动器：Phase 4 窗口设 1280×720（640×360 的整数 2x）
├── android/                         # Android 启动器：Phase 4+ 设横屏
├── ios/                             # 休眠模块（见 §二）
└── docs/                            # 设计文档
```

> **说明**：`save/`（存档）包在 Phase 6 按需创建，不预建空目录——空包没有文件不会被 git 跟踪，预留无意义。

## 四、分层依赖规则（比目录更重要）

```
screens/  ──组装──▶  input/ ──翻译──▶ command/ ──修改──▶ systems/ ⇄ entities/
   │                                                        ▲         ▲
   └────────────读取────────────▶ render/ ──只读─────────────┘         │
                                         └────读取 data/ ◀────────────┘
```

**三条硬性约束**（code review 检查项）：

1. **`entities/`、`data/`、`systems/` 禁止 import 任何渲染类**（`Texture`/`Stage`/`SpriteBatch`），禁止调用 `Gdx.*` 静态方法——输入文档 §6.2 的延伸，也是"JUnit 零后端测试"成立的前提。
   唯一例外：`data/` 与 `config/` 允许使用 gdx 的 `Json` / `FileHandle`（无 GL 依赖，纯 JVM 可运行）。
2. **`render/` 只读 `entities/`**，`entities/` 反向引用 `render/` 一律禁止。
3. **`screens/` 是唯一装配点**——只有它知道如何把 input / command / render / systems 组装起来，业务判断不写在 Screen 里。

## 五、测试结构策略

- 测试位于 `core/src/test/java/`，**包结构镜像 main**（`data/` 的测试放 `com.voidvvv.kz_auto_chess_n.data` 下）
- **零后端原则**：被测代码不碰 `Gdx.*` 静态，测试无需 `HeadlessApplication`，直接 JUnit 运行
- 测试读 JSON 用相对路径 `../assets/data/units.json`（测试工作目录 = `core/` 模块目录）；`JsonLoader` 设计为接受注入的路径/`FileHandle`，便于替换
- Phase 1 起每个逻辑类与测试同步创建（TDD：RED → GREEN → REFACTOR）

## 六、包出生时间表（对应 GDD §十二路线图）

| Phase | 新增包 / 文件 |
|-------|---------------|
| 1 | `data/`、`config/`（JsonLoader、GameBalance）、`entities/Player`、`utils/` + 镜像测试 |
| 2 | `systems/WaveGenerator`、`assets/data/scenes.json`、控制台模拟入口 |
| 3 | `systems/BattleSystem` + `SynergySystem`、`entities/Unit` + `BattleUnit` + `BattleState`（测试大户） |
| 4 | `Main→Game`、`screens/`、`input/`、`command/`、`render/`（棋盘+棋子）、`assets/units/`、窗口 1280×720 |
| 5 | `render/` UI 补全、`systems/ShopSystem`、`equipments.json` |
| 6 | `save/`、`heroes.json` |
| 7 | `fx/`、`audio/` |

## 七、执行状态

本文档落地时已完成的修改（均已验证）：

- [x] `core/build.gradle`：新增 JUnit 5 + AssertJ 测试依赖、`useJUnitPlatform()`（`:core:test` 退出码 0，依赖解析通过）
- [x] 根 `build.gradle`：`appName` → `ember-legion`
- [x] `android/res/values/strings.xml`：显示名 → `Ember Legion`
- [ ] Phase 1 起按 §六时间表逐包创建（不预建空目录）
