# Phase 4 棋盘渲染开发实施计划

> **日期**：2026-08-22
> **范围**：Phase 4 —— 棋盘渲染与网格交互（GDD §十二路线图 / project_structure §六出生时间表）
> **依据文档版本**：GDD V0.13、render_design V1.3（本期主依据）、architecture V1.8、user_input V1.5、battle_design V1.6、project_structure V1.1、data_schema V1.4
> **状态**：实施计划待审阅（未开工）
> **分支**：`feature/phase_4`
> **前序**：Phase 3 已交付战斗引擎（`BattleSystem` 五阶段主循环 + `BattleConsoleMain` 整场模拟，同 seed 逐字节确定，存量 252 例全绿）；本期把该模拟层接上 Screen / 输入 / 命令 / 渲染，成为"可看可玩"的最小闭环

---

## 一、范围界定（开工前 Q1~Q4 已裁决，见 §三）

**做**：
- `Main` 改造为 `extends Game`（Screen 管理）；`screens/` 出生：`LoadingScreen`（占位图集生成 + Assets 门面装配）/ `MainMenuScreen`（极简）/ `BattleScreen`（装配点 + 帧循环）
- `entities/RunState`（Q1 减配：GamePhase 枚举 + round + sceneId + seed + 怜悯计数 + idIssuer + 敌阵）+ `command/RunContext`（Player / RunState / BattleState 可空 / GameData / RNG；ShopSystem/UnitRegistry 字段位预留）
- `command/` 出生：`GameCommand` / `PlacementTarget` / `MoveUnitCommand` / `StartBattleCommand` / `SurrenderCommand`（Q2 命令集）/ `CommandHandler` / `CommandManager`（队列 + (tick, cmd) 历史 + `onExecuted`）
- `systems/RunFlowSystem`（阶段状态机推进 + 轮开始事件子集 + 演示名单 + 三个命令 handler 注册）+ `systems/MoveUnitExecutor`（MoveUnit 校验与执行，含交换语义）
- `input/BoardInputProcessor`：棋盘域（棋盘 6×7 / 备战席 3×3）点击与拖拽（死区 20 虚拟像素、pointer 独立 DragContext、模态阻断位、松手才入队）
- `render/` 出生：`Assets` 门面 + `PlaceholderArt` 运行时占位图集（Q4 全占位）+ 棋盘域自绘族（`BattleRenderer` / `UnitView` / `UnitAnimState` / `LerpMotion` / `ProjectileView` / `FloatingText` / `FxLayer` / `ObjectPool`）+ UI 域极简 Actor（`TopBar` / `ShoppingHud` / `BattleHud` / `ResultBanner` / `RunEndPanel`）
- `entities/Player` 增 `insertToBench`（bench 槽位语义）；`config/GameBalance` 增 5 常量
- `lwjgl3` 窗口 640×480 → **1280×720**（640×360 整数 2 倍），标题同步 `ember-legion`
- 以上纯逻辑部分全部配套单元测试（TDD：RED → GREEN）

**不做**（防蔓延，均已在裁决中明确）：
- `SellUnit` / 出售区 UI（卖出返还依赖"累计花费"口径与经济系统）、通知面板 `NotificationPanel`、暂停菜单、`UnitDetailDialog`、`GlobalKeyProcessor` / `dialogStage` / `KeyBinding`（无对应命令可发）——Phase 5
- `ShopSystem` / `UnitRegistry` / `RunContext` 两预留字段、RESULT 期 `PickChest` / 宝箱 roll / 商店免费刷新 / 怜悯触发 / 判负同轮重试——Phase 5（本期胜负统一推进轮次，差异声明 #6）
- `RunSetupScreen` / `CodexScreen` / `RunResultScreen`、存档 `save/`——Phase 5~6
- 真素材接入：Fusion Pixel 字体（Hiero 生成）+ Kenney CC0 UI 包 + `assets/units/` 图集目录——**后续独立任务**（Q4；本期 `assets/` 零新增文件）
- `Assets.skin()` / `Assets.sound()`（无 Skin / 音频资产，无验证手段）——Skin 随 Phase 5 UI 补全、音频随 Phase 7
- 背景层 / 死亡 3 帧动画二选一 / ParticleEffect / 震屏——Phase 7（死亡表现本期用缩放淡出占位，口径 #13）
- `RunState` 命令历史持久化与回放轨（`CommandManager.history` 只记录不消费）——Phase 6~7

---

## 二、术语与约定（设计文档 ↔ 代码标识符）

| 设计用语 | 代码标识符 | 备注 |
|----------|-----------|------|
| 逻辑阶段 | `entities/GamePhase`（SHOPPING / BATTLE / RESULT / RUN_END） | 枚举名避开 libGDX `Screen` 词义 |
| 局内运行状态 | `entities/RunState` | Q1 减配出生；发号器归它（architecture §2.3） |
| 命令工具箱 | `command/RunContext` | input §6.1 六件套的 Phase 4 子集 + GameData |
| 命令管理器 | `command/CommandManager` | 队列 + (tick, cmd) 历史 + onExecuted |
| 落点（四合一） | `command/PlacementTarget`（Bench / Cell 内嵌类） | input §4.1 |
| 阶段状态机守卫 | `systems/RunFlowSystem` | 系统行为不入队（input §7.1） |
| 移动执行器 | `systems/MoveUnitExecutor` | 交换语义在 systems 判定 |
| 棋盘域输入 | `input/BoardInputProcessor` | 统辖棋盘 + 备战席（input §2.5） |
| 资源门面 | `render/Assets` | render §7.6 注入式，禁静态持有 |
| 占位图集工厂 | `render/PlaceholderArt` | render §7.5 运行时生成 |
| 棋盘总绘制 | `render/board/BattleRenderer` | 持视图集合，层序 ②~⑧ |
| 单位视图 | `render/board/UnitView` | 生命周期 = BattleState（render 铁律 3） |
| 动画状态机 | `render/board/UnitAnimState` | render §5.1，纯 Java 可测 |
| 跳格插值 | `render/board/LerpMotion` | render §4.2 |
| 帧事件缓冲 | `render/EventInbox` | cursor 游标实现（差异声明 #2） |
| 布局常量与换算 | `render/board/BoardGeometry` | render §九坐标表 |
| 飘字规格映射 | `render/FloatTextFormat` | 事件 → 文本（取整）/ 分色 / 尺寸 |
| 战斗 HUD | `render/ui/BattleHud` | ×1/×2 / 投降 / 60s 计时条（Q2） |

**通用约定**：Java 标识符全英文（中文只进注释与 `@DisplayName`）；`entities/ data/ systems/` 零渲染 import 与 `Gdx.*`（project_structure §四）；命令为纯数据载体（禁 `run()/execute()`）；随机一律经注入 seed 的 `RandomGenerator`，**渲染与占位资源生成零模拟 RNG 消耗**。

---

## 三、口径确认记录

### 3.1 用户裁决（Q1~Q4，2026-08-22，原样记录，不得改回）

| # | 问题 | 用户裁决 |
|---|------|----------|
| Q1 | RunState / RunContext / 阶段状态机是否本期出生 | **选 A：减配出生。** `entities/RunState`（phase 枚举 + round + seed + 怜悯计数字段）+ `command/RunContext`（Player / RunState / BattleState 可空 / RNG；ShopSystem/UnitRegistry 字段位留 Phase 5 增补）；门控矩阵按 architecture §5.2；主循环按 accumulator + executeAll(runContext) + BATTLE 门控组织。Phase 3 spec"RunState 推迟 Phase 5"旧裁决以本次为准（差异声明 #7） |
| Q2 | 本期命令集与交互范围 | **选 A：MoveUnit + StartBattle + Surrender。** 兵源 = 进局发固定演示名单（沿 `BattleConsoleMain.buyAndDeploy` 先例，经济不动）；战斗 HUD 极简版（×1/×2 变速 + 投降 + 60s 计时条）；SellUnit/出售区、通知面板、暂停菜单、UnitDetailDialog 全部推 Phase 5 |
| Q3 | 战斗结束流转 | **选 A：自动回 SHOPPING 最小闭环。** 胜负横幅 → 点击或数秒后自动回 SHOPPING：轮次+1、WaveGenerator 重生成敌阵（轮开始事件的 Phase 4 子集）；25 轮打完显示终局文字可重开；RESULT 仅作横幅展示瞬态，PickChest/宝箱/免费刷新/怜悯推 Phase 5 |
| Q4 | 字体与真素材 | **选 A：全占位。** 文字用 libGDX 内置默认 BitmapFont（观感降级已知并记录）；Fusion Pixel + Hiero + LICENSE 入库、Kenney CC0 UI 包列为后续独立任务，不进本期任务清单与验收；**测试不得依赖任何素材文件存在** |

### 3.2 实现层口径（文档未明说、本次定的执行细节）

| # | 决定 | 依据/说明 |
|---|------|-----------|
| 1 | `GamePhase` 四态：SHOPPING / BATTLE / RESULT / **RUN_END**（architecture §5.1 状态图本就有 RUN_END 终态；本期终局 = 文字 + 重开按钮） | Q3 |
| 2 | 发号器 `IdIssuer` 归 `RunState` 持有（兑现 architecture §2.3"发号器归 RunState"），跨场持续递增不重置——玩家名单与战斗实例同一 id 空间 | architecture §2.2 |
| 3 | 敌阵 `List<WaveSpec>` 存 `RunState.enemyWave`（轮开始事件产物，轮内固定）；`sceneId` 取 `GameData.getScenes()` 首键（种子仅森林，`tools/BattleConsoleMain.java:53` 先例） | architecture §5.3 |
| 4 | `RunContext` 增 `GameData` 字段（input §6.1 六件套未列的自然补全——`StartBattle` handler 需解析技能/模板；静态模板只读安全） | 差异声明 #8 |
| 5 | 变速 = `speedFactor ∈ {1f, 2f}` 乘在 accumulator 累积侧；**不进模拟路径**（不进命令、不进 RunState） | architecture §4.2 表演层 |
| 6 | RESULT 横幅：`RESULT_BANNER_SECONDS = 3f` 自动 + 点击立即（uiStage 全屏透明 Actor 收点）；期间 `BattleState` 保留只读（横幅读 outcome），推进时置 null | Q3 |
| 7 | 战斗结束检测：BattleScreen 在逻辑 tick 内**观察** `phase==BATTLE && state.isOver()` → 委托 `runFlowSystem.onBattleOver(ctx)`——"Screen 只做点火器/观察者"的兑现，业务判断在 RunFlowSystem | input §7.3 / architecture §七 |
| 8 | MoveUnit 交换语义用 `Player` 现有 API 组合（undeploy/deploy/insertToBench）；`Player` 仅增 `insertToBench(Unit, slotIndex)`（索引钳制 [0,size]，满员抛错同 addToBench）；bench 槽位 = bench List 索引（入席序即展示序） | GDD §4.1 备战席 9 格位置语义 |
| 9 | bench→board 空格须过人口上限校验（deployed+1 ≤ `getPopulationCap()`）；board→bench 须 bench < 9；bench→bench = remove + insert（换位） | architecture §5.2 校验矩阵精神 |
| 10 | `CommandHandler.handle` 返回 `boolean`（true=执行成功）——满足 input §5.1 `onExecuted(cmd, success)` 的成功信号（§5.2 骨架的 void 不足以支撑）；handler 抛异常则向上抛（不入 onExecuted） | input §5.1/§5.2 的合并落定 |
| 11 | 命令历史以 `(tick, cmd)` 二元组存内嵌小类 `CommandManager.StampedCommand`；tick 由管理器逻辑钟计数（与 BattleState.tick 独立，Phase 5 接 RunState 时统一） | input §4.1 tick 配对 |
| 12 | 渲染事件消费：`EventInbox` 持 cursor，`forEachNew(Consumer<CombatEvent>)` 回调式（consumer 为长持有字段引用）——渲染段零分配；`BattleState.getEvents()` 视图每帧调用会新建包装（`entities/BattleState.java:85-87`），inbox 只取一次缓存 | render §八.6 |
| 13 | 渲染侧避免每帧调 `aliveUnits(Side)`（每次新建 ArrayList，`BattleState.java:66-74`）——遍历 `getUnits()` + `isCleaned()` 自行过滤 | render §八.6 |
| 14 | 占位配色确定性：`render/PalettePick` 用 **FNV-1a 纯字符串 hash → 32 色调色板**（同 id 恒同色，可测）；**禁用模拟 RNG** | architecture §六 / render §7.5"确定性" |
| 15 | 占位块内容 = 种族底色 + 职业顶条 + 星级角标（render §7.5"职业首字母居中"不可行——中文职业无点阵字模）；`fx_digit_0~9` 用 3×5 点阵（`render/DigitGlyph` 纯数据） | 差异声明 #5 |
| 16 | 飘字取整：`Math.round(amount)`（Phase 3 口径 #7 遗留"显示层取整留 Phase 4"的兑现）；暴击飘字 ×1.2 尺寸（render §5.4 表现层自由项本期即做）；HIT 且 skillId 非 null → 技能紫，普攻白 / 暴击橙红 / HEALED 绿 / SHIELDED 蓝 | render §5.2 |
| 17 | 死亡表现：缩放淡出 0.5s（render §十一二选一的占位选择，像素规则允许死亡缩放例外）；弹道 `LINE` 旋转本期无实例（Phase 3 Q2），HOMING 弹不旋转 | render §八.3 |
| 18 | 单位插值按 render §4.2（fromCell/切换时刻 × moveSpeed，以渲染帧时钟计时）；**弹道**按 §4.1 alpha 外推 `lerp(prevPos, pos, alpha)`（渲染侧缓存上次 poll 的 pos 作 prev）——文档双口径的落定 | 差异声明 #3 |
| 19 | 血条/能量条/计时条：1×1 白 region 拉伸 + `batch.setColor` tint（单 batch 零换模式）；星级 = 脚下 1~3 个 2px 色点 | render §十一待定项的占位样式 |
| 20 | 输入 multiplexer 本期**两层**：`uiStage` > `boardProcessor`（dialogStage / keyProcessor 位置注释预留）；`GlobalKeyProcessor` 不建（无命令可发） | 差异声明 #9 |
| 21 | 顶栏极简：轮次 / 金币 / 等级单行 Label（`runState.getRound()` 必须可见——Q3 闭环可读性；完整 TopBar 随 Phase 5） | 实现层最小可读性 |
| 22 | 重开 = 同 `DEMO_SEED`（=42，`RunFlowSystem` 常量）重开——确定性对照；Phase 5 `StartRun` 命令化后 seed 由 UI 域边界事件给定 | architecture §一域边界事件 |
| 23 | `CombatEvent` **不池化**（Phase 3 遗留项评估关闭）：事件对象小、一场 ≤ 数千条、战斗作用域整体丢弃，Android GC 可接受；池化留给实测出现问题时再做 | battle §二"建议池化"的裁决 |
| 24 | 渲染层测试边界：gdx 纯 JVM 类（`Color` / `TextureRegion` / `Animation`）可在测试中构造与断言；触碰 GL / native 的类（`SpriteBatch` / `Texture` / `Pixmap` / `Stage`）不 headless 测，靠 `lwjgl3:run` 人工验收 | project_structure §五零后端原则 |
| 25 | 备战期渲染走 `BattleRenderer.drawShopping`（名单 Unit + 敌阵 WaveSpec 预览 = 侦察，render §九"敌区即侦察"），无 FSM 无血条；`UnitView` 只在 BATTLE 期存在——视图生命周期 = BattleState | render 铁律 3 |
| 26 | `undeploy` 回席在 bench 已满 9 时的理论溢出为 Phase 3 遗留假设洞（名单理论 27 上限）；`MoveUnitExecutor` 对 board→bench 预校验 size<9 堵本期路径，名单总数收口归 Phase 5 | `entities/Player.java:110-119` 注释假设 |

---

## 四、现状盘点（file:line 均为本次实读）

### 可直接复用（零改动）

| 资产 | 位置 | 说明 |
|------|------|------|
| 战斗组合根 | `systems/BattleSystem.java:60`（startBattle）/ `:106`（step）/ `:162`（runToEnd） | 渲染只消费 `BattleState`；`step` 开头 `isOver` 空操作（`:108`）保证投降后安全 |
| 战斗状态只读查询 | `entities/BattleState.java:43-95` | getUnits（id 序含亡者）/ unitAt / getProjectiles / getEvents（追加式视图）/ getTick / getElapsed / isOver / getOutcome / 双侧 SynergySnapshot |
| 战斗单位只读 | `entities/BattleUnit.java:62-132` | 坐标 / currentHp / energy / star / statuses（不可变视图）/ isCleaned / maxHp / hpRatio / getTemplate().getName() |
| 弹道连续坐标 | `entities/Projectile.java:40-51` | 格单位（格中心 = +0.5）；getSkill() 区分普攻 / 技能弹 |
| 事件流 | `entities/CombatEvent.java:16-19`（9 类 Type）+ `:47-91` 静态工厂 | amount 为 float——显示取整本期落地（口径 #16） |
| 名单 API | `entities/Player.java:74-143` | bench（入席序）/ deploy / undeploy / deployedAt / getDeployedUnits（y↑x↑）/ getGold / getLevel / getPopulationCap |
| 波次生成 | `systems/WaveGenerator`（用法见 `tools/BattleConsoleMain.java:56`） | `generateEnemyWave(round, sceneId, data, rng)`，RNG 消耗 = 杂兵数 |
| 演示名单先例 | `tools/BattleConsoleMain.java:62-64, 87-92` | 三兵固定格 (2,5)/(3,5)/(2,6)，发号 → 入席 → 部署 |
| 数据加载 | `config/JsonLoader.loadFromDirectory`（`:49` 用法）+ `data/GameData`（getScenes 首键取场景 `:53`） | BattleScreen 复用同一入口 |
| 平衡常量 | `config/GameBalance.java:17`（LOGIC_STEP）/ `:18`（BATTLE_TIMEOUT）/ `:28`（PROJECTILE_SPEED）/ `:48-50`（棋盘 / 备战席尺寸）/ `:13`（TOTAL_ROUNDS） | 全部渲染与循环所需尺寸 / 节奏常量已就位 |
| 确定性随机 | `utils/RandomGenerator.java:16-27` | consumedCount 供"渲染零消耗"断言 |
| 测试夹具 | `core/src/test/java/.../systems/support/BattleTestFixtures.java:37` | 直构模板 / 技能 / 微型 GameData，本期命令与流程测试复用 |
| 存量测试 | 252 例（23 文件） | 全绿基线，验收对照 |

### 需改造（4 处生产代码 + 1 处启动器）

| 文件 | 变更 |
|------|------|
| `Main.java`（`:9` ApplicationAdapter 模板） | 全文重写为 `extends Game`：create() 组装 GameData / PlaceholderArt / Assets → LoadingScreen |
| `lwjgl3/.../Lwjgl3Launcher.java:31` | `setWindowedMode(640, 480)` → **(1280, 720)**（640×360 整数 2 倍，render §2.1） |
| `lwjgl3/.../Lwjgl3Launcher.java:20` | 标题 `"kz_auto_chess———_n"`（em-dash 旧名）→ `"ember-legion"`（root `build.gradle:59` appName 已定，此行硬编码漏改） |
| `entities/Player.java:85`（addToBench 之后） | 增 `insertToBench(Unit unit, int slotIndex)`（口径 #8） |
| `config/GameBalance.java:35`（MAX_INLINE_CAST_DEPTH 后） | 增常量组：`MAX_DELTA=0.1f` / `MAX_TICKS_PER_FRAME=5` / `DRAG_DEAD_ZONE_PX=20` / `RESULT_BANNER_SECONDS=3f` / `BATTLE_SPEED_FACTOR_FAST=2f`（input §5.3 / §3 死区 / Q2/Q3） |

### 需新建

见 §六变更清单（`screens/` `input/` `command/` `render/` 四包 + `entities/RunState` + `systems/RunFlowSystem` / `MoveUnitExecutor`）。

---

## 五、总体技术方案

### 5.1 架构分层与数据流

分层依赖遵守 project_structure §四：`screens/` 是唯一装配点；`render/` 只读 `entities/`；`input/` 翻译坐标为命令；`command/` 修改 `systems ⇄ entities`。模拟层本期新增三个类（`entities/RunState` / `systems/RunFlowSystem` / `systems/MoveUnitExecutor`）零 `Gdx.*`，"JUnit 零后端测试"前提不破。

- 帧循环与双通路渲染图：`../diagrams/phase4_render_architecture.md`（`.html` 同名浏览器版）
- Screen 流转与命令链路图：`../diagrams/phase4_screen_flow.md`（`.html` 同名）

数据流一句话：`boardProcessor/uiStage → CommandManager → RunFlowSystem 注册的 handler → Player/BattleSystem → BattleState（+CombatEvent）→ BattleRenderer/EventInbox → 屏幕`；阶段推进（SHOPPING↔BATTLE→RESULT→RUN_END）由 `RunFlowSystem` 在逻辑 tick 内完成，BattleScreen 只观察联动 UI 可用性。

### 5.2 与设计文档的差异声明（如实记录，不改文档）

| # | 文档原文 | 本实施 | 理由 |
|---|----------|--------|------|
| 1 | render §4.1 伪代码 `battleSystem.tick(LOGIC_STEP)` | 实际 API 为 `battleSystem.step(state)`（`systems/BattleSystem.java:106`，无参、内部取 LOGIC_STEP） | Phase 3 已定签名；伪代码示意 |
| 2 | render §4.3"事件消费后**清空缓冲**" | `BattleState.events` 为追加式（`entities/BattleState.java:132` 只增不清）→ 渲染侧 `EventInbox` cursor 游标实现"逻辑帧 ↔ 渲染帧严格对应、不重播" | 不改模拟层既有语义；效果等价 |
| 3 | render §4.1（alpha 插值）与 §4.2（切换时刻 × moveSpeed）双口径 | 单位按 §4.2、弹道按 §4.1 alpha 外推（口径 #18） | §4.2 对跳格更精确；弹道为连续推进实体适用 alpha |
| 4 | render §5.5 通知面板 / §7.6 `skin()`/`sound()` / §九出售区⑦暂停菜单 | Q2/Q4 推 Phase 5~7（本期零调用点、零接口） | 用户裁决 |
| 5 | render §7.5 占位块"职业首字母居中" | 种族底色 + 职业顶条 + 星级角标（口径 #15） | 中文职业无点阵字模 |
| 6 | architecture §5.1 判负 → 同轮重试 + 怜悯计数+1 | 本期胜负统一 round+1 推进（口径见 Q3） | 无经济与成长时重试无意义；重试/怜悯随 Phase 5 RESULT/PickChest 接入 |
| 7 | Phase 3 spec Q1"RunState/UnitRegistry 推迟 Phase 5" | Q1 裁决减配出生 RunState（UnitRegistry 仍 Phase 5） | 出生表把 `command/` 划入 Phase 4，本次裁决为准 |
| 8 | input §6.1 RunContext 六件套 | +GameData 字段（口径 #4）；ShopSystem/UnitRegistry 字段位预留注释 | handler 需要模板解析 |
| 9 | input §2.2 multiplexer 四层 | 本期两层（uiStage > boardProcessor），dialogStage/keyProcessor 位置注释预留（口径 #20） | 无弹窗与快捷键命令 |
| 10 | GDD §十二 Phase 4"FitViewport(640,360) + **Stage** 显示棋子" | 双通路：棋盘域 SpriteBatch 自绘 + UI 域 Stage（render §三） | GDD 示意已被 render_design 细化取代（GDD 决策日志 2026-08-20 已指向 render 文档） |
| 11 | project_structure §六 Phase 4 行含 `assets/units/` | 不建（Q4 全占位、运行时生成）；真素材接入时随后续任务建 | 用户裁决 Q4 |
| 12 | project_structure §一 #10"`Main.java` Phase 4 替换为 Game + Screen" | 照做；另发现 `Lwjgl3Launcher` 标题 em-dash 未随 appName 修正（`:20`），本期一并修正 | §二已定决策的漏改落点 |

### 5.3 render_design 条款映射（逐节 → 实施条目）

| render 章节 | 实施落点 |
|-------------|----------|
| §2.1 双 Viewport 同参数 640×360 | BattleScreen（world FitViewport + uiStage FitViewport，resize 同步 update）；Lwjgl3Launcher 1280×720 |
| §2.2 统一虚拟坐标 | 全部布局走 `BoardGeometry` 虚拟坐标常量 |
| §3.1 双通路两域对照 | 棋盘域 = `render/board/` 自绘；UI 域 = `render/ui/` Actor |
| §3.2 绘制顺序 ①~⑩ | `BattleRenderer.draw` 层序（②~⑧ 单 batch；⑨ uiStage.draw；⑩ 本期无 dialogStage） |
| §4.1 帧循环结构 | BattleScreen.render 逻辑段（+ speedFactor / RESULT 计时，口径 #5/#7） |
| §4.2 跳格插值 | `LerpMotion`（口径 #18）；战斗结束 UnitView 整体销毁重建（`BattleRenderer.rebuild/clear`） |
| §4.3 事件消费时机 | `EventInbox`（口径 #12）；同帧多条各自成飘字 |
| §5.1 UnitView 动画 FSM | `UnitAnimState`（优先级 Death 锁定 > Attack/Cast > Walk > Idle；HitFlash 叠加层独立计时） |
| §5.2 飘字 | `FloatingText` + `ObjectPool` + `FloatTextFormat`（口径 #16；同目标同帧多段错位堆叠 = 每帧槽位 x 偏移） |
| §5.3 弹道与特效 | `ProjectileView`（HOMING 追踪当前格中心渲染 + alpha 外推）；命中闪光 `FxLayer` |
| §5.4 四锚点 × 双命名空间 | 本期落三锚点：单位（起手闪光 `fx_{skillId}`）/ 区域（落点 `fx_{skillId}_burst` 与兜底）/ 单位持续（状态小色点，轮询差分）；弹道锚点 = 弹道本体；全屏锚点 Phase 5+；命名由 `PlaceholderKeys` 生成 |
| §5.5 通知面板 | 不做（Q2）；`CommandManager.onExecuted` 机制本期就位，Phase 5 直接订阅 |
| §六视图类结构与四条铁律 | 结构照建（ShopBar 等推 Phase 5）；铁律 1~4 全部兑现（只读 / 禁反向 import / 生命周期 / Assets 注入） |
| §7.1~7.3 图集命名与帧数 | `PlaceholderKeys` + `PlaceholderArt`（idle2/walk2/attack3/cast2/death3 帧差异可见） |
| §7.5 占位资源流水线 | `PlaceholderArt`（零素材文件全功能）+ `Assets.region(key)` 逐 key 兜底 |
| §7.6 Assets 门面 | 注入式 `Assets`（region 逐 key 兜底 + font）；skin/sound 推后（差异声明 #4） |
| §八像素规则 | Nearest（占位 Texture 生成时统一设置）/ `Math.round` 吸附（BoardGeometry）/ 禁旋转（死亡缩放与弹道为例外，本期 HOMING 弹不旋转）/ 渲染段零分配 |
| §九 HUD 布局 | `BoardGeometry` 常量落坐标表：④ 棋盘 (224,50,192,224)、② 备战席 (20,48,108,120)、① 顶栏、⑥ 开战按钮、战斗 HUD 区；③⑤⑦⑧ 推 Phase 5 |
| §十性能预算 | ≤30 UnitView + 池化飘字特效；单 batch |
| §十一待定项 | 死亡表现占位选择（口径 #13）、血条样式占位（口径 #19）——均 Phase 7 可换 |

---

## 六、变更清单

### 新增：`core/src/main/java/com/voidvvv/kz_auto_chess_n/`

| 文件 | 包 | 职责 | 预估行数 |
|------|-----|------|---------|
| `entities/GamePhase.java` | entities | 逻辑阶段枚举（口径 #1） | ~12 |
| `entities/RunState.java` | entities | 一局运行态：seed / sceneId / idIssuer / round / phase / mercyLossCount / enemyWave（受控可变，写归 RunFlowSystem） | ~120 |
| `command/GameCommand.java` | command | 命令标记接口（纯数据载体） | ~10 |
| `command/PlacementTarget.java` | command | 落点：Bench(slotIndex) / Cell(gridX, gridY) | ~45 |
| `command/MoveUnitCommand.java` | command | 载荷 unitId + target | ~20 |
| `command/StartBattleCommand.java` | command | 无载荷单例 | ~12 |
| `command/SurrenderCommand.java` | command | 无载荷单例 | ~12 |
| `command/CommandHandler.java` | command | `boolean handle(GameCommand, RunContext)`（口径 #10） | ~12 |
| `command/CommandManager.java` | command | 队列 + StampedCommand 历史 + 注册表 + executeAll + onExecuted | ~140 |
| `command/RunContext.java` | command | 工具箱（Q1 减配 + GameData；ShopSystem/UnitRegistry 预留位） | ~70 |
| `systems/MoveUnitExecutor.java` | systems | MoveUnit 校验与执行（bench↔board / 交换 / 人口上限） | ~150 |
| `systems/RunFlowSystem.java` | systems | startNewRun / beginRound / onBattleOver / tickResult / continueAfterResult / restart + 三 handler 注册 | ~280 ⚠ |
| `input/BoardInputProcessor.java` | input | 棋盘域输入：unproject / 死区 / DragContext(pointer) / 松手入队 / ghost 只读暴露 | ~290 ⚠ |
| `render/Assets.java` | render | 资源门面：region 逐 key 兜底 / font / dispose | ~90 |
| `render/PlaceholderArt.java` | render | Pixmap 运行时占位图集（units/skills/status/通用件） | ~300 ⚠ |
| `render/PlaceholderKeys.java` | render | 命名约定纯函数 + GameData 全 key 枚举 | ~70 |
| `render/PalettePick.java` | render | FNV-1a → 32 色调色板（口径 #14） | ~55 |
| `render/DigitGlyph.java` | render | 0~9 的 3×5 点阵（口径 #15） | ~60 |
| `render/EventInbox.java` | render | cursor 游标事件消费（forEachNew） | ~60 |
| `render/FloatTextFormat.java` | render | 事件 → 飘字文本（取整）/ 分色 / 尺寸 | ~70 |
| `render/board/BoardGeometry.java` | render.board | 虚拟坐标区域常量 + 格 ↔ 像素换算 + 吸附（纯 Java） | ~150 |
| `render/board/LerpMotion.java` | render.board | 跳格插值（fromCell/toCell/切换时刻 × moveSpeed） | ~70 |
| `render/board/UnitAnimState.java` | render.board | 动画 FSM（优先级 / HitFlash 叠加 / 帧时长常量） | ~140 |
| `render/board/ObjectPool.java` | render.board | 泛型对象池（飘字 / 特效共用） | ~60 |
| `render/board/FloatingText.java` | render.board | 飘字实例（池化；上浮淡出 0.8s） | ~80 |
| `render/board/UnitView.java` | render.board | 单位视图：BattleUnit 只读 + AnimState + LerpMotion + 血条/能量条/星级绘制 | ~250 ⚠ |
| `render/board/ProjectileView.java` | render.board | 弹道绘制（alpha 外推 + flip 朝向） | ~90 |
| `render/board/FxLayer.java` | render.board | 受击白闪 / 起手与落点闪光（事件驱动）/ 状态色点（轮询差分） | ~160 |
| `render/board/BattleRenderer.java` | render.board | 总绘制：格 / 高亮 / 备战席 / ghost / 视图集合（rebuild=BattleState 作用域） | ~300 ⚠ |
| `render/ui/TopBar.java` | render.ui | ① 区极简顶栏（轮次/金币/等级） | ~70 |
| `render/ui/ShoppingHud.java` | render.ui | ⑥ 区开战按钮（SHOPPING 期） | ~80 |
| `render/ui/BattleHud.java` | render.ui | 战斗 HUD：×1/×2 + 投降 + 60s 计时条（BATTLE 期） | ~150 |
| `render/ui/ResultBanner.java` | render.ui | 胜负横幅 + 点击继续（RESULT 瞬态） | ~90 |
| `render/ui/RunEndPanel.java` | render.ui | 终局文字 + 重开按钮（RUN_END） | ~90 |
| `screens/LoadingScreen.java` | screens | 占位生成进度 + Assets 装配 → MainMenu | ~100 |
| `screens/MainMenuScreen.java` | screens | 极简主菜单 | ~70 |
| `screens/BattleScreen.java` | screens | 装配点 + 帧循环 + 阶段观察联动 + resize/pause/hide | ~320 ⚠ |

⚠ = 触线风险文件，§十一已预留拆分位；全部文件 ≤400 行目标、800 硬上限。

### 修改

| 文件 | 变更 |
|------|------|
| `Main.java` | ApplicationAdapter → `extends Game`（create/dispose 全文重写） |
| `lwjgl3/.../Lwjgl3Launcher.java` | `:20` 标题 → ember-legion；`:31` 窗口 → 1280×720 |
| `entities/Player.java` | `:85` 后增 `insertToBench(Unit, int)` |
| `config/GameBalance.java` | `:35` 后增输入 / 帧循环常量组（5 个） |

### 明确不动

`BattleSystem` / `BattleState` / `BattleUnit` / `CombatEvent` / `Projectile` / `WaveGenerator` / `JsonLoader` / `GameData` 及全部 data 包；`android/`（横屏已配，`AndroidManifest.xml:17`）；`assets/` 零新增文件。

---

## 七、详细设计

### 7.1 `entities/RunState` 与 `command/RunContext`

```java
/** 逻辑阶段（architecture §5.1；命名避开 libGDX Screen） */
public enum GamePhase { SHOPPING, BATTLE, RESULT, RUN_END }

/** 一局运行态（architecture §2.3；Q1 减配出生）。
 *  受控可变：写方法仅供 RunFlowSystem / 命令 handler 在局内调用（沿 BattleUnit 的
 *  framework-internal 纪律）。idIssuer 归本类持有（口径 #2），跨场递增。 */
public final class RunState {
    private final long seed;
    private final String sceneId;
    private final IdIssuer idIssuer;
    private int round = 1;
    private GamePhase phase = GamePhase.SHOPPING;
    private int mercyLossCount;              // Q1 字段建好，触发逻辑 Phase 5
    private List<WaveSpec> enemyWave = Collections.emptyList(); // 轮开始产物（口径 #3）

    // 读：getSeed / getSceneId / getIdIssuer / getRound / getPhase / getMercyLossCount / getEnemyWave
    // 写（framework-internal）：setPhase / advanceRound / setMercyLossCount / setEnemyWave
}

/** 命令工具箱（input §6.1 的 Phase 4 子集 + GameData；口径 #4）。
 *  battleState 可空：仅 BATTLE/RESULT 期非空，回 SHOPPING 即弃（双实体语义）。 */
public final class RunContext {
    private final Player player;
    private final RunState runState;
    private final GameData gameData;
    private final RandomGenerator rng;
    private BattleState battleState;   // @Nullable
    // Phase 5 预留字段位（本期仅注释声明，不建字段）：
    //   ShopSystem shop;  UnitRegistry registry;

    public Player getPlayer(); public RunState getRunState(); public GameData getGameData();
    public RandomGenerator getRng();
    public BattleState getBattleState();            // 可能为 null
    public void setBattleState(BattleState s);      // StartBattle handler / RunFlowSystem 调用
}
```

### 7.2 命令层（`command/`）

```java
/** 纯数据标记接口（input §4.1：禁业务方法，可序列化承诺） */
public interface GameCommand { }

/** 落点四合一（上场/下场/走位/交换——交换语义归 systems 判定） */
public abstract class PlacementTarget {
    public static final class Bench extends PlacementTarget { public final int slotIndex; }
    public static final class Cell extends PlacementTarget { public final int gridX, gridY; }
}

public final class MoveUnitCommand implements GameCommand {
    public final int unitId; public final PlacementTarget target;
}
public final class StartBattleCommand implements GameCommand { public static final StartBattleCommand INSTANCE; }
public final class SurrenderCommand implements GameCommand { public static final SurrenderCommand INSTANCE; }

/** 返回 true = 执行成功（onExecuted 信号，口径 #10）；false = 静默忽略（校验不过） */
public interface CommandHandler { boolean handle(GameCommand cmd, RunContext ctx); }

public final class CommandManager {
    private final Queue<GameCommand> commandQueue = new ConcurrentLinkedQueue<GameCommand>();
    private final List<StampedCommand> history = new ArrayList<StampedCommand>(); // (tick, cmd)，口径 #11
    private final Map<Class<?>, CommandHandler> handlers = new HashMap<Class<?>, CommandHandler>();
    private final List<CommandExecutedListener> listeners = new ArrayList<CommandExecutedListener>(); // onExecuted
    private int logicTick;                                  // 管理器逻辑钟（Phase 5 统一入 RunState）

    public void addCommand(GameCommand cmd);               // 入队 + 盖 tick 戳入历史
    public void registerHandler(Class<?> type, CommandHandler handler);
    public void addListener(CommandExecutedListener l);    // 通知面板数据源（Phase 5 订阅）
    /** 固定逻辑 tick 内消费全部命令（BattleScreen 调用）；无 handler 的命令丢弃+记日志 */
    public void executeAll(RunContext ctx);
    public List<StampedCommand> getHistory();              // 不可变视图（回放轨 Phase 6+）
    public void clearHistory();
}
```

### 7.3 `systems/MoveUnitExecutor`（交换语义唯一归属）

```java
/** MoveUnit 校验与执行（Phase 5 拆归名单/商店系统；本期由 RunFlowSystem 注册）。
 *  全部纯确定性校验，失败返回 false（不抛错——静默忽略+记日志口径）。 */
public final class MoveUnitExecutor {
    /** bench↔board / board↔board（交换）/ bench↔bench（换位）；
     *  校验链： SHOPPING 期（phase 由调用方保证）→ unit 在名单 → 落点合法：
     *  Cell: y∈[4,6]（Player.deploy 同域校验兜底）· bench→空格须 deployed+1 ≤ populationCap
     *  Bench: slotIndex∈[0,8] · board→bench 须 bench < 9（口径 #9/#26）
     *  交换组合全部用 undeploy/deploy/insertToBench 拼装，同一调用内原子完成 */
    public boolean move(Player player, int unitId, PlacementTarget target);
}
```

### 7.4 `systems/RunFlowSystem`（阶段状态机守卫 + 轮开始子集 + 演示名单）

```java
/** 局内流程守卫：阶段推进 / 轮开始事件子集 / 演示名单（Q2/Q3）。
 *  系统行为不经命令队列（input §7.1）。零 Gdx。 */
public final class RunFlowSystem {
    public static final long DEMO_SEED = 42L;              // 口径 #22
    private final WaveGenerator waveGenerator = new WaveGenerator();

    /** 注册本期三个命令 handler（input §6.1 注：handler 由所属 system 注册；Phase 5 拆分）：
     *  MoveUnit → 门控 SHOPPING → moveUnitExecutor.move
     *  StartBattle → 门控 SHOPPING → battleSystem.startBattle(player, runState.getEnemyWave(),
     *      gameData, rng, runState.getIdIssuer()) → ctx.setBattleState + phase=BATTLE（零棋子允许开战）
     *  Surrender → 门控 BATTLE → ctx.getBattleState().finish(BattleOutcome.ENEMY_WIN)（幂等） */
    public void registerHandlers(CommandManager manager);

    /** 新开一局：round=1 / phase=SHOPPING / 演示名单（沿 BattleConsoleMain.java:62-64 先例：
     *  战士(2,5) / 刺客(3,5) / 游侠(2,6)，1 星）→ beginRound */
    public void startNewRun(RunContext ctx);
    /** 轮开始事件子集（Q3）：enemyWave = generateEnemyWave(round, sceneId, data, rng)
     *  （商店免费刷新/怜悯推 Phase 5；RNG 消耗 = 杂兵数，Phase 2 口径） */
    public void beginRound(RunContext ctx);
    /** BATTLE→RESULT（BattleScreen 观察 isOver 后委托，口径 #7）；battleState 保留供横幅读 outcome */
    public void onBattleOver(RunContext ctx);
    /** RESULT 横幅计时（RESULT_BANNER_SECONDS 自动推进） */
    public void tickResult(RunContext ctx, float dt);
    /** 点击继续（横幅 Actor 调用）：round==TOTAL_ROUNDS → RUN_END；否则 round+1 + beginRound
     *  + battleState=null + phase=SHOPPING */
    public void continueAfterResult(RunContext ctx);
    /** RUN_END 重开：同 seed 重建（startNewRun 复入） */
    public void restart(RunContext ctx);
}
```

### 7.5 `input/BoardInputProcessor`（棋盘域）

```java
/** 棋盘域输入（input §2.3/§2.4/§3）：统辖棋盘 6×7 与备战席 3×3（出售区 Phase 5）。
 *  unproject 用棋盘 viewport（含其 camera）——坐标陷阱防御（input §3 第四行）。 */
public final class BoardInputProcessor implements InputProcessor {
    /** modalBlocked：模态阻断位（本期常 false，Phase 5 接 UIDialogManager.isShowing()；
     *  方法首行 return true 吞事件——input §3 第三陷阱） */
    public BoardInputProcessor(Viewport boardViewport, CommandManager commandManager,
                               Supplier<RunContext> context, BooleanSupplier modalBlocked);

    // touchDown：phase!=SHOPPING 或 modalBlocked → 消费返回 true 不动作；
    //   命中棋盘玩家区/备战席单位 → 记 DragContext(pointer, unitId, 源落点, 起始虚拟坐标)
    // touchDragged：同 pointer 才处理；位移 < DRAG_DEAD_ZONE_PX（20，unproject 后虚拟坐标）
    //   = 仍处"点击死区"未进入拖拽（进入拖拽后才允许移动 ghost）
    // touchUp：进入过拖拽 → 判落点：棋盘格/备战槽 → addCommand(MoveUnitCommand)；
    //   非法落点（第 0~3 行、界外）→ 不产生命令（回弹由渲染层 ghost 消失自然实现）；
    //   未出死区的松手 = 点击（本期无命令，Phase 5 详情面板挂点位）
    // 同时拖拽 ≤ 1（HashMap<Integer, DragContext> 按 pointer 独立，input §3 第一陷阱）

    // —— 渲染只读暴露（ghost 绘制与高亮）——
    public boolean isDragging(); public int getDragUnitId();
    public float getDragVirtualX(); public float getDragVirtualY();
    public PlacementTarget getDropPreview();   // 当前悬停落点（合法绿/非法红高亮）
}
```

### 7.6 渲染层结构与关键纯逻辑类

```java
/** 布局常量与换算（render §九坐标表；纯 Java，可测） */
public final class BoardGeometry {
    public static final int VIRTUAL_W = 640, VIRTUAL_H = 360;
    public static final int BOARD_X = 224, BOARD_Y = 50, BOARD_W = 192, BOARD_H = 224; // ④ 6×7×32
    public static final int BENCH_X = 20, BENCH_Y = 48, BENCH_W = 108, BENCH_H = 120;  // ② 3×3
    public static final int CELL = 32;
    // cellCenter(gridX, gridY) → (px, py)：行 0（敌区）在顶 —— py = BOARD_Y + BOARD_H - (gridY+1)*CELL + CELL/2，
    //   输出 Math.round 吸附整数虚拟像素（render §八.2）
    // pixelToCell(px, py) → int[2] 或 null（boardProcessor 命中判定）
    // benchSlotCenter(slotIndex) / pixelToBenchSlot(px, py)（槽 36×40，列主序）
}

/** 跳格插值（render §4.2；纯 Java） */
public final class LerpMotion {
    // onCellPolled(gridX, gridY, clock)：坐标变化 → from=旧 to、to=新格、startTime=clock
    // positionX/Y(clock)：t = clamp((clock - startTime) × moveSpeed, 0, 1)；到达停稳待机
    // reset(gridX, gridY)：战斗重建时直落
}

/** 动画 FSM（render §5.1；纯 Java） */
public final class UnitAnimState {
    public enum Anim { IDLE, WALK, ATTACK, CAST, DEATH }
    // onEvent(CombatEvent.Type)：AttackLaunched/Hit→ATTACK（近战即时）、Cast→CAST、UnitDied→DEATH（锁定）
    // onCellPolled(变化)→WALK；update(dt)：动画播完回落 Idle/Walk；死亡淡出 0.5s 计时（口径 #13）
    // hitFlash()：受击白闪 0.1s 叠加层（独立计时、可叠加，不占状态位）
    // 帧时长常量：idle 0.4 / walk 0.2 / attack 0.1 / death 0.15（秒/帧，render §7.3）
}

/** 帧事件缓冲（render §4.3；cursor 游标，差异声明 #2） */
public final class EventInbox {
    public void attach(BattleState state);    // 缓存 getEvents() 视图一次（口径 #12）
    public void detach();
    public void forEachNew(Consumer<CombatEvent> consumer); // [cursor, size) 逐条回调，cursor 前进
}

/** 飘字规格（render §5.2；纯 JVM Color，可测，口径 #24） */
public final class FloatTextFormat {
    public static final class Spec { String text; Color color; float scale; } // text = Math.round(amount)
    /** HIT（普攻白/暴击橙红×1.2/技能紫，skillId 判紫）/ HEALED 绿 / SHIELDED 蓝 → Spec；
     *  其余事件返回 null（ATTACK_LAUNCHED/CAST/STATUS_APPLIED/UNIT_DIED/...） */
    public static Spec of(CombatEvent event);
}

/** 资源门面（render §7.6 注入式；只允许出现在 render/ 与 screens/） */
public final class Assets {
    public TextureRegion region(String key);  // 真图集(本期 null) → 占位 map 兜底，永不 null（断言）
    public BitmapFont font();                 // libGDX 内置默认（Q4；Phase 5 换 Fusion Pixel 零改调用方）
    public void dispose();                    // 占位 Texture 全弃（Main.dispose 调）
}

/** 占位图集工厂（render §7.5；GL 线程一次性生成，不 headless 测）
 *  units：{unitId}_{anim}_{frame} 32×32 —— 种族底色(PalettePick) + 职业顶条 + 帧差异
 *  （idle 亮度±5% / walk y±1px / attack 前移2px / death 透明度递减）
 *  skills：fx_{skillId} / fx_{skillId}_burst；StatusType：fx_status_{type}
 *  通用件：fx_cast_default / fx_hit_default / fx_digit_0~9(DigitGlyph) / ui_panel_9slice / fx_white(1×1) */
public final class PlaceholderArt { public PlaceholderArt(GameData data); /* + dispose() */ }
```

`UnitView`（battle 作用域）：持 `BattleUnit` 只读引用 + `UnitAnimState` + `LerpMotion`；绘制 = 占位 region（`flipX` 按阵营，敌我相对而立）+ 血条（hp/maxHp，红绿 2px）+ 能量条（黄 1px）+ 星级色点 + HitFlash tint；`update(clock, dt)` 轮询坐标/状态差分。`BattleRenderer.rebuild(BattleState)` 于每次 `startBattle` 后整体重建（id → UnitView 映射，避免 `getUnitById` 线性查找），`clear()` 于回 SHOPPING。

`FxLayer`：事件驱动一次性（Cast→`fx_{skillId}` 单位锚点；Hit/Healed/Shielded→落点 `fx_{skillId}_burst` 或兜底，区域锚点 ≤0.5s）+ 状态持续色点（轮询 `getStatuses()` 差分，render §5.4 第四行）+ 受击白闪（叠加层）。全部池化（`ObjectPool`）。

### 7.7 `screens/BattleScreen`（装配点 + 帧循环）

```java
public final class BattleScreen implements Screen {
    // —— show()：装配 ——
    // worldViewport = new FitViewport(640, 360, worldCamera)；uiStage = new Stage(new FitViewport(640, 360))
    // batch / battleRenderer(assets) / TopBar / ShoppingHud / BattleHud / ResultBanner / RunEndPanel
    // runContext = new RunContext(player, new RunState(DEMO_SEED, 首场景, new SequentialIdIssuer()), data, rng)
    // commandManager + runFlowSystem.registerHandlers + runFlowSystem.startNewRun
    // multiplexer: uiStage → boardProcessor（dialogStage/keyProcessor 位预留注释）→ Gdx.input.setInputProcessor

    // —— render(delta)：帧循环（input §5.3 + 口径 #5/#7）——
    // if (!paused) accumulator += Math.min(delta, GameBalance.MAX_DELTA) * speedFactor;
    // while (accumulator >= LOGIC_STEP && ticks++ < MAX_TICKS_PER_FRAME) {
    //     commandManager.executeAll(runContext);
    //     if (phase == BATTLE && battleState != null) {
    //         battleSystem.step(battleState);
    //         if (battleState.isOver()) runFlowSystem.onBattleOver(runContext);   // 观察-委托
    //     } else if (phase == RESULT) runFlowSystem.tickResult(runContext, LOGIC_STEP); // 自动推进
    //     accumulator -= LOGIC_STEP;
    // }
    // if (accumulator >= LOGIC_STEP) accumulator = 0f;                 // 超限丢弃（死亡螺旋防御）
    // float alpha = accumulator / LOGIC_STEP;                          // 渲染段
    // battleRenderer.draw(batch, runContext, alpha, renderClock, boardProcessor);  // ②~⑧（SHOPPING 走 drawShopping）
    // uiStage.act(delta); uiStage.draw();                              // ⑨（Actor 可见性按 phase 联动）

    // resize：双 viewport 同步 update（render §2.1）
    // pause：冻结 accumulator（Android 挂起）；resume；hide：解绑 setInputProcessor（input §2.3 防僵尸监听）
    // dispose：batch / stage / battleRenderer（Assets 归 Main 持有，不在此弃）
}
```

`Main`：`create()` = `JsonLoader.loadFromDirectory(Gdx.files.local("data/"))`（软告警打印）→ `new PlaceholderArt(data)` → `new Assets(art)` → `setScreen(new LoadingScreen(this, assets, data))`；`dispose()` 弃 Assets 与 Screen。`LoadingScreen` 首帧完成占位生成后切 `MainMenuScreen`；后者"开始"按钮 → `new BattleScreen(game, assets, data)`。

---

## 八、TDD 测试计划（RED → GREEN → REFACTOR）

新增测试镜像包结构；全部 headless JUnit（零后端原则）。渲染纯逻辑类（BoardGeometry / LerpMotion / UnitAnimState / EventInbox / FloatTextFormat / PlaceholderKeys / PalettePick / DigitGlyph）**零 `Gdx.*` 静态与零 GL 构造**，直测；GL 触碰类（PlaceholderArt / UnitView / BattleRenderer / Stage Actor / Screen）不单测，走 §九人工验收。

| 测试类 | 预估例数 | 覆盖要点 |
|--------|---------|----------|
| `entities/RunStateTest` | ≈8 | 初始态（round=1/SHOPPING/空敌阵）；setPhase/advanceRound/getter；enemyWave 不可变视图；seed/sceneId/idIssuer 只读 |
| `entities/PlayerTest`（扩展） | +≈3 | insertToBench 指定槽 / 索引钳制边界（0 与超 size）/ 满员抛错 |
| `command/CommandManagerTest` | ≈10 | 入队+tick 戳历史；executeAll 分发到正确 handler；返回 false 不触发 onExecuted、true 触发（cmd 透传）；无 handler 丢弃不炸；多命令按序；listener 可增删；clearHistory；历史不可变视图 |
| `systems/MoveUnitExecutorTest` | ≈14 | bench→board 空格（deploy 成功+自动摘席）；bench→board 占用格交换（原板员去 bench 源槽）；board→board 交换（互易位）；board→bench 指定槽；bench→bench 换位；人口上限拒绝（cap=3 时第 4 个上场 false）；bench 满拒绝 board→bench；越界（y=3/y=7/x=6/槽 9）拒绝；unitId 不在名单 false；全部失败路径零状态残留（操作原子性） |
| `systems/RunFlowSystemTest` | ≈14 | startNewRun：3 演示兵固定格与 `BattleConsoleMain.java:62-64` 一致、round=1、敌阵已生成；beginRound 敌阵确定性（同 seed 两 run 逐位 equals）与 RNG 消耗 = 杂兵数；门控：BATTLE 期 MoveUnit/StartBattle 均 false、SHOPPING 期 Surrender false；StartBattle：派生 BattleState 非空 + phase=BATTLE + 零棋子允许；Surrender：finish(ENEMY_WIN) + 幂等；onBattleOver → RESULT；continueAfterResult：round+1 + 敌阵重生成 + battleState=null + SHOPPING；round=25 战毕 → RUN_END；restart 同 seed 重演一致（两 run 全事件流 equals） |
| `render/BoardGeometryTest` | ≈12 | 常量自洽（BOARD_W=6×32、BOARD_H=7×32、BENCH 3×3）；cellCenter 行 0 在顶（y 翻转正确）；cellCenter↔pixelToCell 往返全 42 格；界外/半格像素 null；benchSlotCenter↔pixelToBenchSlot 往返；输出整数吸附 |
| `render/LerpMotionTest` | ≈6 | 跳格后按 moveSpeed 推进中点值；t≥1 停稳；连续两跳 from 链正确；reset 直落 |
| `render/UnitAnimStateTest` | ≈10 | 优先级：Death 锁定不可被任何事件打断；Attack/Cast 打断 Idle/Walk；Idle↔Walk 随格变化切换；同类型动画重触发取最新；HitFlash 独立叠加且 0.1s 衰减；Death 淡出计时；帧时长常量与 render §7.3 一致 |
| `render/EventInboxTest` | ≈6 | attach 后首批全量送达；无新事件零回调；追加后仅新段送达；cursor 不回退（同事件不重播）；detach 后零回调 |
| `render/FloatTextFormatTest` | ≈8 | HIT 普攻白+取整文本；crit 橙红+scale 1.2；HIT(skillId≠null) 紫；HEALED 绿 / SHIELDED 蓝；amount 0.4→"0"、0.5→"1"（Math.round 口径）；ATTACK_LAUNCHED/CAST/UNIT_DIED 返回 null |
| `render/PlaceholderKeysTest` + `PalettePickTest` + `DigitGlyphTest` | ≈10 | 命名约定格式；`enumerateFor(GameData)` 覆盖全部 unitId×5 动画×帧数与 skillId/StatusType（防漏生成）；FNV-1a 同 id 同色、不同 id 大概率异色；点阵 0~9 齐全且每字至少 2 笔 |
| 合计 | **≈101** | 存量 252 例不动，全量预期 ≈353 |

**TDD 纪律**：每任务先写测试（RED）→ 最小实现（GREEN）→ 重构（IMPROVE）；命令/流程测试复用 `BattleTestFixtures` 直构微型 GameData，不读 JSON 不依赖素材。

---

## 九、验收标准

1. `gradlew core:test` 全绿：存量 252 例保持全绿 + 新增 ≈101 例（以全绿为准，不设硬数）
2. `gradlew lwjgl3:run` 完整人工链路（1280×720 窗口，标题 ember-legion）：Loading → 主菜单 → 战斗屏 → 备战期拖拽布阵（bench↔board / 格间交换 / 非法落点回弹无命令 / 人口上限灰拒）→ 开战 → 观战（跳格插值平滑、攻击/施法/受击白闪、分色飘字取整、弹道飞行、血条能量条星级、60s 计时条、×2 变速、投降立即判负）→ 胜负横幅 → 点击/3s 自动进下一轮（轮次+1、敌阵更新）→ 第 25 轮战毕终局文字 → 重开（同 seed 阵容敌阵一致）
3. 确定性：`RunFlowSystemTest` 断言同 seed 两 run 敌阵与全事件流逐位 equals；渲染与占位生成零模拟 RNG 消耗（`rng.getConsumedCount()` = Σ enemyCount + 暴击 roll 数，测试锁定）
4. 分层复查（grep 验证）：`entities/ data/ systems/` 新增类零渲染 import、零 `Gdx.*`；`render/` 纯逻辑类（§八所列）零 `Gdx.*` 静态调用、零 GL 构造；`Assets` 仅出现于 `render/` 与 `screens/`；`render/` 不写任何模拟态（对 BattleUnit/Player 只调读方法）
5. 素材零依赖：`assets/` 不新增任何文件；改名/移动 `assets/libgdx.png` 不影响运行（Main 不再引用）
6. 像素规则抽查：占位 Texture 全部 Nearest；绘制坐标整数吸附；除死亡缩放淡出外无旋转（render §八）
7. 行数红线：新增文件 ≤400 行（⚠ 文件触线按 §十一拆分位处理，不超 800）
8. 口径抽查：BATTLE 期拖拽被门控拒绝（handler 层）且 boardProcessor 表现层不产生命令（双层校验，input §4.3）；变速 ×2 只改 accumulator 消费速率（模拟路径零感知——事件流与 ×1 逐位一致）

---

## 十、实现顺序（建议提交切分，9 个 feat 提交）

| 步 | 提交内容 | 依赖 |
|----|---------|------|
| 1 | `GameBalance` 常量组 + `GamePhase` / `RunState` + `RunContext` + RunStateTest | 无 |
| 2 | `command/` 全家（接口 / PlacementTarget / 三命令 / CommandManager）+ CommandManagerTest | 1 |
| 3 | `Player.insertToBench` + `MoveUnitExecutor` + 两测试（PlayerTest 扩展 / MoveUnitExecutorTest） | 2 |
| 4 | `RunFlowSystem`（含三 handler 注册）+ RunFlowSystemTest | 3 |
| 5 | render 纯逻辑件：BoardGeometry / LerpMotion / UnitAnimState / EventInbox / FloatTextFormat / PlaceholderKeys / PalettePick / DigitGlyph + 各测试（本步后 `core:test` 全绿，全程零 GL） | 无（可与 1~4 并行） |
| 6 | `PlaceholderArt` + `Assets` + `Main→Game` + `LoadingScreen` + `MainMenuScreen` + Lwjgl3Launcher（1280×720 / 标题）——`lwjgl3:run` 出主菜单 | 5 |
| 7 | `BattleScreen` 骨架 + `TopBar` + `ShoppingHud` + `BattleRenderer` 静态层（格 / 备战席 / 敌阵预览 / 高亮）——可见可点开战（直开无拖拽版本：开战按钮经 CommandManager） | 4/6 |
| 8 | `UnitView` / `UnitAnimState` 接线 / `ProjectileView` / `FloatingText` + `ObjectPool` / `FxLayer` / `EventInbox` 接线——整场战斗可观 | 7 |
| 9 | `BoardInputProcessor`（拖拽 + ghost + 死区）+ `BattleHud`（变速/投降/计时）+ `ResultBanner` + `RunEndPanel` + 全量验收（§九） | 8 |

> 每步完成时 `core:test` 必须全绿（测试与实现同提交，沿 Phase 2/3 切分纪律）。

---

## 十一、风险与开放问题

### WARNING 级既定发现（已按口径落地，无需再求证）

| # | 项 | 处置 |
|---|----|------|
| 1 | render §4.1 `tick()` 伪代码 / §4.3"清空缓冲" / §4.1~4.2 插值双口径 | 差异声明 #1/#2/#3 + 口径 #18 |
| 2 | 内置默认字体观感降级（非像素风） | Q4 已知接受；后续任务替换（调用方零改） |
| 3 | `undeploy` 在 bench 满时的理论溢出（Phase 3 遗留假设） | 口径 #26：MoveUnitExecutor 预校验堵本期路径；名单收口 Phase 5 |
| 4 | `CombatEvent` 池化 | 口径 #23 评估关闭（不池化）；实测 GC 问题再立项 |
| 5 | 掉帧时弹道 alpha 外推的视觉近似 | 误差 ≤1 tick（16ms），不可感知（render §4.2 同理） |
| 6 | Pixmap 占位生成一次性成本 | region 数 ≈ 模板数×12 + 技能/状态/通用 ≈ 100 个小图，毫秒级 |
| 7 | 640×360 单 batch drawcall | render §十预算内，无压力 |
| 8 | Android 端本期不实测 | 横屏已配（AndroidManifest.xml:17）；验收以 lwjgl3 为准，Phase 5 真机回归 |

### 技术风险

| 项 | 说明 | 缓解 |
|----|------|------|
| `BattleScreen` / `BattleRenderer` / `BoardInputProcessor` / `PlaceholderArt` 体量 | 四个 ⚠ 触线文件 | 预拆位：BattleScreen→`BattleScreenLogic`（帧循环参数计算纯化）；BattleRenderer→`BoardLayer`（格与高亮）+`ShoppingLayer`（备战期绘制）；BoardInputProcessor→`DragStateMachine`（纯逻辑可测）；PlaceholderArt→`UnitPlaceholderPainter` / `FxPlaceholderPainter` |
| 渲染段零分配纪律被无意破坏（每帧 new） | code review 逐项过 `getEvents()`/`aliveUnits()`/字符串拼接 | 口径 #12/#13；飘字文本预格式化入池 |
| Stage 与棋盘域双 batch 的状态串扰（projection matrix / color 残留） | uiStage.draw 前后 batch 必须已 end | BattleRenderer 单入口保证 begin/end 配对 |
| 命令门控与表现层预校验双账本失同步 | 表现层灰拒与 handler 拒绝口径不一 | 两层各自测试锁定；口径以 architecture §5.2 矩阵为唯一事实源 |
| Java 8 语法约束（无 var/record） | 手写样板 | 全部骨架按 Java 8 拟写 |

### 开放问题（遗留，不阻塞）

- SellUnit 返还口径（累计花费 100% 的字段来源）——Phase 5 随经济系统
- 通知面板 / 暂停菜单 / UnitDetailDialog / dialogStage / GlobalKeyProcessor / KeyBinding——Phase 5
- `RunState` 命令历史与回放轨序列化（`CommandManager.history` 已记录不消费）——Phase 6~7
- Fusion Pixel 字体 + Kenney CC0 UI 包接入（Q4 后续独立任务：下载 / Hiero / LICENSE 入库 / `assets/units/` 建目录）
- 震屏、暴击尺寸之外的表演层自由项、背景层、ParticleEffect——Phase 7
- 变速档位扩展（×3/慢放）——待玩法反馈

---

## 十二、附录：用户确认记录（2026-08-22，原样存档）

**Q1 RunState/RunContext → 选 A：减配出生。**
本期出生 `entities/RunState`（phase 枚举 + round + seed + 怜悯计数字段）与 `command/RunContext`（Player / RunState / BattleState 可空 / RNG；ShopSystem/UnitRegistry 字段留 Phase 5 增补，文档标注预留字段位）。门控矩阵按 architecture §5.2；主循环按 accumulator + executeAll(runContext) + BATTLE 门控组织。Phase 3 spec 旧裁决以本次为准，文档作差异声明。

**Q2 命令集与交互 → 选 A：MoveUnit + 开战 + 投降。**
玩家兵源 = 进局发固定演示名单（沿 BattleConsoleMain.buyAndDeploy 先例，经济不动）。战斗 HUD 极简版：×1/×2 变速 + 投降按钮 + 60s 计时条。SellUnit/出售区、通知面板、暂停菜单、UnitDetailDialog 全部推 Phase 5（列入"不属于本期"边界清单）。

**Q3 战斗结束流转 → 选 A：自动回 SHOPPING 最小闭环。**
胜负横幅 → 点击或数秒后自动回 SHOPPING：轮次+1、WaveGenerator 重生成敌阵（轮开始事件的 Phase 4 子集）。25 轮打完显示终局文字可重开。RESULT 仅作横幅展示瞬态（等价最小实现由文档按门控矩阵定），PickChest/宝箱/免费刷新/怜悯推 Phase 5。

**Q4 字体与真素材 → 选 A：全占位。**
文字全部用 libGDX 内置默认 BitmapFont，观感降级已知（风险章节记录）。Fusion Pixel 下载 + Hiero 生成 + LICENSE 入库、Kenney CC0 UI 包接入列为紧随其后的独立任务，不进本期任务清单与验收标准。测试不得依赖任何素材文件存在。
