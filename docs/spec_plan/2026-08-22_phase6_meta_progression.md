# Phase 6 局外成长 技术实施文档

> 版本：V1.0（2026-08-22）　分支：feature/phase_6　前置：Phase 5/5.1/feedback06/07（origin/main b7d091e，631 项测试全绿）
> 本文档按「预授权协议」成稿：GDD 与代码的含糊/矛盾/空洞**不再中断求证**，全部由文档作者自行裁决并逐条记录于 **§4 自动裁决记录**（编号 D1~D20，含理由与影响面）；用户复核时按该章节逆向审阅即可。

---

## 1. 背景与目标

Phase 6 = GDD §十二路线图第 6 阶段「局外成长」：**英雄熟练度、场景解锁、存档系统**（GDD §八）。Phase 5 交付了完整单局闭环（经济/装备/宝箱/判负重试/终局面板）后，本阶段把"单局"接入"跨局成长"：选择英雄开局、局末结算熟练度与解锁、断点续玩。

**范围来源**（四路汇合）：

1. GDD §8.1 英雄熟练度（Lv.5 上限 / 经验表 50/100/150/200 / 通关 +60 + 每轮 +3 / 等级解锁表 / 三英雄被动草案）；
2. GDD §8.2 全局解锁 + §7.4 场景差异化（森林→墓穴→雪山、专属棋子、新 Boss）+ §4.2/§5.1（亡灵、巨人羁绊与棋子随场景解锁进商店池）；
3. Phase 5 明确推迟到 Phase 6 的遗留项（Q3/Q5/heroes.json/文档回写批次，去向表见 §1.2）；
4. 现状 stub/伏笔销账（MasteryCalculator、RunState.masteryAwarded、RunEndPanel 占位文案、StartRunCommand.heroId 扩展位）。

**成功标准**：

1. 主菜单 → RunSetupScreen 选英雄（含熟练度展示）与场景（未解锁灰置）→ 开局；英雄被动与熟练度等级加成在局内真实生效（起始金币 / 商店 3 费概率与刷新折扣 / 专属传奇棋子入池 / 羁绊增幅 / 全队回能）。
2. RUN_END 时档案写入 `save/profile.json`：熟练度经验入账、跨级升级、通关场景登记；首次通关森林后墓穴在 RunSetup 可选，通关墓穴后雪山可选。
3. 备战期任意时刻退出/挂起 → `save/run_snapshot.json` → 主菜单「继续远征」完整复原（轮次/敌阵/商店/名单/装备/RNG 流/发号器）；RUN_END 自动删档。
4. 熟练度结算展示替换 RunEndPanel 的「（Phase 6 接档案）」占位；CodexScreen 可查英雄熟练度与场景解锁。
5. 全量测试绿（gradle XML 核对，MEMORY 口径），每 CP 附测试要点（TDD 先行）。

### 1.2 Phase 5 遗留项去向表（逐项盘点）

| 遗留项 | 出处 | 去向 |
|--------|------|------|
| RunSetupScreen / 英雄选择界面 | Phase 5 Q3（`2026-08-22_phase5_economy_ui_integration.md`:5080） | **本期做**（§6.CP12） |
| `StartRunCommand.heroId` 扩展位启用 | 同上 | **本期做**（§6.CP8/CP17） |
| RunResultScreen | Phase 5 Q5（同文件 :5084） | **本期以 RunEndPanel 扩展形态交付结算展示**（§6.CP14）；独立演出屏（点亮星辰）推 Phase 7（裁决 D6） |
| CodexScreen | 同上 | **本期做 MVP**（§6.CP15：英雄熟练度页 + 场景解锁页，只读） |
| 存档 `save/` 包 | 同上 + project_structure §三 | **本期做**（§6.CP7 档案轨 + CP16 快照轨） |
| MasteryCalculator stub 接档案域 | 同上（Q5 裁决） | **本期做**（§6.CP6/CP8） |
| seed 输入过渡 | 同上 | **不做**（裁决 D9：无 Skin/TextField 资产，Q4 Kenney 包仍 deferred；RunEndPanel 已展示 seed 供复现） |
| `heroes.json`「延后——档案层系统未设计，预写必返工」 | `data_schema_design.md` §一文件表 | **本期做**（§6.CP2/CP3，档案层本期设计落地） |
| 文档回写批次①：data_schema §六 synergies 必填 `desc` 补写 | `2026-08-22_phase5.1_ui_polish.md`:2063（WARNING-8） | **本期做**（§6.CP18） |
| 文档回写批次②：data_schema §三 rarity 词表名 FINISHED vs 代码 RARE | `2026-08-22_feedback06_*.md`:121/:1350（WARNING-2） | **本期做**（§6.CP18，以代码 RARE 为准回写） |
| 文档回写批次③：render_design §九 HUD 表补背包悬停锚点 | `2026-08-22_feedback06_*.md`:186（D3） | **本期做**（§6.CP18） |
| `CommandManager.history` 只记录不消费（回放轨 Phase 6+） | CommandManager.java:96 注释 | **再推迟 Phase 7**（architecture §八明示回放轨「Phase 7 可选」；本期快照轨与命令流解耦，无需消费历史——裁决 D18） |
| `RunEndPanel`「熟练度 +N（Phase 6 接档案）」占位 | RunEndPanel.java:54 | **本期做**（§6.CP14） |
| MainMenu seed 过渡（START 直连 BattleScreen） | MainMenuScreen.java:80-83 | **本期做**（START 改道 RunSetupScreen，§6.CP13） |

### 1.3 明确不做（范围外清单）

| 项 | 推迟到 | 出处 |
|----|--------|------|
| RunResultScreen 独立演出屏（点亮星辰动画） | Phase 7 打磨 | 裁决 D6 |
| 回放轨（录像/命令流重演消费 history） | Phase 7 可选 | architecture §八；裁决 D18 |
| BATTLE/RESULT 期快照存档（存档点仅备战） | 不做（既定决策） | 决策 2026-08-20「存档点仅备战阶段」沿用 |
| seed 手输 UI | 待定清单 | 裁决 D9 |
| 商店锁定 / 拖拽购买 / Kenney UI 包 / 设置 Dialog / 音效 | 各自既定推迟 | Phase 5 差异声明沿用 |
| 中途存档的云同步 / 多存档位 | 不在 GDD | — |
| 第 4+ 英雄、新英雄被动类型 | 内容性后续 | 词表即代码铁律（data_schema §三），扩类型先改引擎 |
| Android 真机专项回归 | T9 手验清单列项执行 | Phase 4 遗留口径 |

---

## 2. 术语与约定（设计文档 ↔ 代码标识符）

| GDD/设计文档用语 | 代码标识符 | 位置 |
|------|------|------|
| 英雄熟练度（经验/等级） | `save/HeroProgress`（exp/level）+ `GameBalance.MASTERY_*` | CP1/CP6 |
| 熟练度结算 | `systems/MasteryCalculator`（口径升级）+ `save/ProfileService.settle` | CP8/CP6 |
| 档案（局外档案态） | `save/Profile`（不可变，version/heroProgress/completedScenes） | CP6 |
| 档案持久化 | `save/ProfileCodec`（纯 String 编解码）+ `save/ProfileStore`（FileHandle IO） | CP7 |
| 档案域门面 | `save/MetaService`（Screen 唯一入口，architecture §三 方案 A） | CP7 |
| 局外修正聚合（英雄被动 × 熟练度等级 × 场景解锁） | `entities/RunModifiers`（不可变值对象，装配期冻结进 RunState） | CP5/CP8 |
| 英雄选择 + 场景选择两步式合一 | `screens/RunSetupScreen` | CP12 |
| 图鉴（Codex） | `screens/CodexScreen` | CP15 |
| 挂起存档（快照轨） | `save/RunSnapshot` + `save/SnapshotCodec` + `save/SnapshotStore` | CP16 |
| 通关场景登记 / 场景解锁判定 | `Profile.completedScenes` + `ProfileService.unlockedSceneIds`（派生不落档） | CP6 |
| 场景商店池门控（亡灵/巨人棋子随场景解锁进池） | `SceneData.shopUnlocks` + `RunModifiers.isShopAllowed` | CP4/CP5/CP9 |
| 英雄专属传奇棋子（Lv.3） | `HeroData.legendaryUnitId` + 池门控 | CP2/CP5/CP9 |
| 「老兵补给」开局金币 +2 | `HeroPassiveType.START_GOLD` → `RunModifiers.startGoldBonus` | CP2/CP5 |
| 「荆语」野兽/游侠羁绊效果 +25% | `HeroPassiveType.SYNERGY_AMP` → `SynergySystem.resolve` 增幅重载 | CP2/CP10 |
| 「战歌」全队能量获取 +15% | `HeroPassiveType.ENERGY_GAIN` → `RunModifiers implements StatModifierSource`（energyGainRate ADD 百分点） | CP2/CP5/CP10 |
| 结算展示文案 | `save/RunSettlementText`（纯函数） | CP14 |
| RNG 流复原（快照） | `RandomGenerator(long seed, int consumedCount)` 重放构造 | CP16 |

---

## 3. 现状盘点（file:line 均为本次实读核对，分支 feature/phase_6 工作区）

### 3.1 可直接复用（零改动）

| 资产 | 位置 | 说明 |
|------|------|------|
| 费阶概率锚点与权重换算 | `config/GameBalance.java:94-100,136-143`（`shopTierProbabilities`/`PROBABILITY_WEIGHT_SCALE`） | Lv.2 加成 = 权重数值调整，入口现成 |
| 回能管线 | `systems/DamagePipeline.java:72`（`gain = baseAmount × effective(ENERGY_GAIN_RATE)/100`）+ `StatPipeline` 派生源列表 | 奥兰多被动零新管线 |
| 修正源列表插点 | `systems/BattleSystem.java:241-251`（deriveUnit `sources = Arrays.asList(synergies, EquipmentStats.of(...))`）+ `entities/StatModifierSource.java:9` | Phase 3 Q4 预留插点，追加第 3 源即可 |
| 确定性 RNG | `utils/RandomGenerator.java:12-67`（`getConsumedCount` 已有；全部消耗点均为单次 nextFloat——weightedPick:48 与暴击 BattleSystem.java:199） | 快照复原可行性已核实 |
| 发号器 | `entities/SequentialIdIssuer.java:9-16` + RunState 持有（RunState.java:21） | 续号仅缺 peek/复原构造（CP16 补） |
| 命令框架与门控 | `command/CommandManager.java`、`StartRunCommand.java:6-26`（heroId 字段已存在恒 null）、RunFlowSystem.java:57-68（StartRun handler 一致性校验先例） | heroId 启用 = 装配点改传参 + 校验一行 |
| 宝箱/经济/名单系统 | `systems/ChestSystem`、`ShopSystem.java:81-118`（reroll/buy）、`RosterSystem` | 零改动（商店加成走 reroll 重载） |
| 场景链校验 | `config/JsonLoader.java:593-614`（unlockAfter 引用/自指/成环校验已齐） | 场景解锁判定只需档案侧派生 |
| 自绘按钮先例 | `render/ui/PauseMenuDialog.java:83-109`（MenuButton）、`MainMenuScreen.java:73-95` | RunSetup/Codex/MainMenu 扩展直接沿用 |
| 测试基建 | `systems/support/BattleTestFixtures` + 67 个测试文件；`RunFlowSystemTest.java:634-653`（熟练度现口径断言） | 断言改写点已定位 |
| 数据现状 | `assets/data/`：units.json（9 可购 + 3 Boss）、skills.json（11 条）、synergies.json（6 条含 desc）、scenes.json（**仅森林 1 场景**）、equipments.json；**无 heroes.json** | 内容缺口 = CP3/CP11 |

### 3.2 需改造

| 文件 | 位置 | 改动 |
|------|------|------|
| `config/GameBalance.java` | :49-56 经济段尾、:151-155 经验表旁 | 增 MASTERY_* 常量段 + `masteryExpToNext`（CP1） |
| `config/JsonLoader.java` | :57-82 入口、:331-416 scenes 段、:513-615 crossValidate | heroes.json 解析 + shopUnlocks 解析 + 交叉校验（CP2/CP4） |
| `data/GameData.java` | :25-41 构造链、:43-53 查询 | heroes 容器 + 兼容重载（CP2） |
| `data/SceneData.java` | :16-39 | shopUnlocks 字段 + 兼容构造（CP4） |
| `entities/RunState.java` | :18-45 字段与构造、:61-99 写方法 | heroId/modifiers 终态字段 + setRound（CP8/CP16） |
| `systems/MasteryCalculator.java` | 全文（21 行） | GDD_BASIC 完整口径（通关 +60）（CP8） |
| `systems/RunFlowSystem.java` | :57-68 StartRun handler、:73-75 startBattle 调用 | heroId 一致性校验 + modifiers 透传（CP8/CP10） |
| `systems/ShopSystem.java` | :46-54 RefreshShop、:81-94 reroll、:202-211 tierPool | 加成重载 + 池门控 + 折扣 + restoreSlots（CP9/CP16） |
| `systems/SynergySystem.java` | :31-67 resolve | 增幅重载（CP10） |
| `systems/BattleSystem.java` | :63-109 startBattle、:241-251 deriveUnit | modifiers 重载 + 玩家侧第 3 修正源（CP10） |
| `render/ui/ShopBar.java` | :54-61 刷新钮、:77-82 refresh、:160-172 ActionButton | 动态价签（CP9） |
| `entities/Player.java` | :27-29 构造 | 复原构造 + restoreRoster（CP16） |
| `entities/SequentialIdIssuer.java` | 全文（16 行） | 复原构造 + peekNext（CP16）；`IdIssuer.java:7-11` 接口同步 |
| `utils/RandomGenerator.java` | :16-18 构造 | 流重放复原构造（CP16） |
| `render/ui/RunEndPanel.java` | 全文（79 行） | 结算行 + 返回主菜单（CP14） |
| `screens/MainMenuScreen.java` | 全文（97 行） | 三按钮 + MetaService（CP13） |
| `screens/LoadingScreen.java` | :27-33 构造、:50-52 导航 | MetaService 透传（CP13） |
| `Main.java` | :21-30 create | MetaService 装配（CP13） |
| `screens/BattleScreen.java` | :63-104 字段、:112-142 构造、:199-239 show、:255-292 render、:350-363 pause/hide、:373-391 newContext/restart | 装配整合（CP17） |
| `.gitignore` | :171-172 文件尾 | 追加 `assets/save/`（CP7） |

### 3.3 需新建

| 类别 | 文件 |
|------|------|
| 数据层 | `data/HeroPassiveType`、`data/HeroData`（CP2） |
| 实体 | `entities/RunModifiers`（CP5） |
| 档案域（新包 `save/`） | `Profile`、`HeroProgress`、`ProfileService`、`ProfileCodec`、`ProfileStore`、`MetaService`、`RunSettlementText`（CP5~CP7/CP14）、`RunSnapshot`、`SnapshotCodec`、`SnapshotStore`（CP16） |
| Screen | `screens/RunSetupScreen`（CP12）、`screens/CodexScreen`（CP15） |
| 数据文件 | `assets/data/heroes.json`（CP3）；scenes.json 重写为 3 场景、units.json 增 12 条、synergies.json 增 2 条（CP11） |
| 图表 | `docs/diagrams/phase6_meta_dataflow.md/.html`、`docs/diagrams/phase6_screen_flow.md/.html`（已随本文档落盘） |
| 测试 | 见 §9 测试用例表（10 新建 + 8 改写） |

---

## 4. 自动裁决记录（预授权协议产出；每条 = 事实矛盾/设计空洞 → 自选方案）

> 本章为本文档的「用户裁决替代物」。D 编号在正文各处以「裁决 Dx」引用；带 **工作值待调** 标记的数值为按 GDD 量级锚点自拟，Phase 7 数值平衡时复核。

| # | 问题一句话 | 备选方案 | 选定方案 | 理由 | 影响面 |
|---|------------|----------|----------|------|--------|
| D1 | Phase 6 体量 ≈ 18 CP，是否切 6.1/6.2 | A 切两期；B 单期 + 任务级裁剪线 | **B** | 系统件互锁（解锁判定需场景内容可测、选屏需档案、结算需选屏）；Phase 5 单期 31 CP 先例；快照轨（T7）与内容铺量（T4）为预定义可后延切出项 | 全局节奏；若执行超载按 T 序裁剪而非重立子阶段 |
| D2 | 熟练度 Lv.1 解锁「初始金币+2」与格雷克被动「开局金币+2」疑似重复/叠加 | A Lv.1 行 = 全英雄基础权益（与被动叠加，格雷克 Lv.1 开局 14 金）；B Lv.1 行即格雷克被动本体（他人无） | **A** | GDD §8.1 表述为「等级解锁表」，独立于「英雄被动草案」两节并列；A 保住三英雄差异化（格雷克开局 14 vs 他人 12）且实现同通道 | `ProfileService.runModifiers`；经济起点整体 +2（数值待调备注） |
| D3 | 现行 `MasteryCalculator.GDD_BASIC` 只算 轮数×3，漏 GDD §8.1「通关 +60」 | A 维持现状；B 完整口径 COMPLETED=60+轮×3 | **B** | GDD §8.1 明文；ABANDONED 维持轮×3（§2.1 口径不变） | RunFlowSystemTest:634 断言 75→135 改写；Profile 数值曲线 |
| D4 | GDD §8.1 Lv.4/Lv.5 解锁「...更多待设计」空洞 | A 预留无加成（显示敬请期待）；B 自拟工作值 | **B：Lv.4 开局金币 +3；Lv.5 商店刷新费 -1（最低实付 1 金）**，均 **工作值待调** | B 兑现「按量级锚点自行补全」授权；两项均走既有通道（startGoldBonus/refreshCostDiscount），零新系统，回滚只删常量 | GameBalance CP1；ShopSystem 折扣；ShopBar 价签 |
| D5 | Lv.2「商店稀有棋子概率 +5%」的生效轮次未定义（锚点表 1~9 轮 3 费概率为 0） | A 无条件 +5pp（第 1 轮可出 3 费）；B 仅基础 3 费概率>0 的轮次生效（约第 10 轮起），自 1 费扣减保三档和 100 | **B** | GDD §3.4「1~3 轮 100% 一费/新手期」是更强约束；A 会在第 1 轮打破新手期节奏 | ShopSystem.reroll 权重调整；确定性不变（消耗点数不变） |
| D6 | architecture §七 列 RunResultScreen 独立屏（演出+结算展示） | A 本期建独立屏；B RunEndPanel 扩展结算展示，独立屏推 Phase 7 | **B** | 结算数据与写入逻辑与屏无关；演出（点亮星辰）属 Phase 7 打磨；B 复用 Phase 5 面板与弹窗栈零迁移成本 | CP14；Phase 7 任务清单 +1 |
| D7 | 场景解锁状态存哪：档案存 unlocked 位 vs 由 completedScenes 派生 | A 存位；B 派生 | **B** | unlockAfter 链已校验无环；派生消除双源漂移风险（改链配置即刻生效） | Profile 字段极简；unlockedSceneIds 每次计算（3 场景 O(1)） |
| D8 | 「亡灵/巨人棋子随场景解锁进商店池」的机制落点：units.json 加字段 vs scenes.json 加 shopUnlocks | A units 加 `sceneUnlock` 字段；B scenes.json 增可选 `shopUnlocks[]`（引用制） | **B** | units 字段表已锁（data_schema §四），加字段动面大；场景→棋子是「场景包含内容」的自然方向（与 enemyPool 同构）；加载期交叉校验沿 scenes 现有模式 | data_schema §七结构变更 + CP18 回写；SceneData/JsonLoader |
| D9 | seed 手输 UI（Phase 5 Q5 提及「过渡」） | A Scene2D TextField；B 不做 | **B** | 项目无 Skin/TextField 样式资产（Q4 Kenney 包 deferred，全 UI 自绘）；手输属调试功能，RunEndPanel seed 展示已可复现 | 无；Phase 7 若引入 Skin 可补 |
| D10 | 快照轨触发口径与持久化范围 | A 仅 pause 存；B 进入 SHOPPING 即存 + pause/hide 补写；logicTick/notices 不存 | **B** | 决策 2026-08-20「存档点仅备战」→ 只在 SHOPPING 写；每轮写一次成本 ~2KB 可忽略；logicTick 只服务命令 tick 戳（快照轨不带历史，恢复后清空） | CP16/CP17；回放轨不受影响 |
| D11 | 档案写入时点：endRun 内（模拟域）vs RUN_END 首帧（Screen 观察） | A RunFlowSystem.endRun 直写档案；B BattleScreen 观察后调 MetaService | **B** | RunFlowSystem 是确定性模拟核心（零 Gdx、可回放），档案 IO 属档案域语义调用（architecture §一/§三）；「Screen 只做点火器」在菜单/终局层的延伸；每局恰一次由 runEndSettled 旗标保证 | CP17；masteryAwarded 仍为纯模拟态（RunState） |
| D12 | 薇拉「羁绊效果 +25%」作用面：当档全效果 vs 仅数值幅度 | A 仅 stat 通道；B 当档全部效果（stat+effect 通道），ADD 通道四舍五入取整、PCT/effect 保留浮点 | **B** | 「羁绊效果」无排除条款；SHIELD（兽人 6 档 30% maxHp）按比例放大直觉一致；取整规则保证整型语义 stat（hp/armor/range）无小数 | SynergySystem 增幅重载；工作值待调 |
| D13 | 奥兰多「全队能量获取 +15%」载体 | A 新能量乘数管线；B energyGainRate ADD +15（百分点刻度，法师羁绊同款机制） | **B** | energyGainRate 词表现成（StatKey:22）、消耗点现成（DamagePipeline:72）；零新管线零特例 | RunModifiers implements StatModifierSource；敌方侧不吃 |
| D14 | 档案/快照存盘路径 | A `Gdx.files.local("save/")`；B Preferences | **A** | architecture §八「文件格式 Phase 6 细化（JSON 起步）」；与 Main.java:23 读 `data/` 同通道；lwjgl3 workingDir=assets → 运行期文件落 `assets/save/`，需 .gitignore（Preferences 不可快照整局） | CP7 .gitignore；Android 落 internal storage 天然隔离 |
| D15 | 新 Boss 专属技能（震地/双生弹/黄金清算/冰风/断星锤）GDD §7.2 点名但未设计 | A 新增 5 条具名技能；B 暂复用既有 11 技能（形状近似），具名化推 Phase 7 | **B** | 本期主线是系统轨；技能内容属铺量（data_schema §五扩展另行立项）；标注 **工作值待调** | CP11 内容表；Phase 7 内容任务 +1 |
| D16 | 亡灵/巨人羁绊数值 GDD 未给 | A 留空不做；B 自拟工作值 | **B：亡灵=吸血线（(2) lifesteal+10 /(4) hp+300·lifesteal+20 /(6) hp+500·attack+25%·lifesteal+30）；巨人=血甲线（(2) hp+200 /(4) hp+450·armor+30 /(6) hp+800·armor+60·attack+20%）**，**工作值待调** | 呼应 GDD §7.4 场景敌人倾向（墓穴吸血减抗 / 雪山高护甲控制）；档位替换制语义与首发 6 羁绊一致 | CP11 synergies.json |
| D17 | 英雄被动数据词表 | A 每英雄自由 JSON 字段；B `HeroPassiveType` 三值枚举 + value + synergyIds（词表即代码） | **B** | data_schema §三铁律：词表先登记再进 JSON；扩新被动类型 = 引擎改动（本期三类型覆盖 GDD 草案三英雄） | CP2；后续新英雄被动先扩枚举 |
| D18 | CommandManager.history 回放轨消费 | A 本期做最小回放；B 推 Phase 7 | **B** | architecture §八明示回放轨 Phase 7 可选；本期快照轨全量状态复原不需要命令流；history 现状（只记录）零改动 | 无代码改动；范围外清单声明 |
| D19 | 场景/单位内容量级 | A 只做森林（系统先行）；B 最小集三场景齐发（每场景 3 商店池单位 + 3 Boss + 2 羁绊 + 3 英雄传奇） | **B** | 场景解锁是本期主打卖点，无第二场景则解锁系统不可验收；量级对齐「最小可验收」而非铺量（每池 3 单位与森林现状同量级） | CP11（内容性任务，可独立裁剪——D1 裁剪线） |
| D20 | 快照读取遇引用悬空（units/heroes/scenes 改版后旧档） | A 启动即死（沿 JsonLoader fail-fast）；B 删档按无存档处理 + 日志 | **B** | JsonLoader 的 fail-fast 保护的是**静态资源**完整性（开发期错误）；玩家存档是**运行期数据**，数据版本演进常态，炸档不可接受；profile.json 同口径（损坏重置） | CP7/CP16；手验清单含旧档回归 |

---

## 5. 总体技术方案

**架构总原则**：三态域不混（architecture §一）——局内模拟域保持确定性零 Gdx；档案域走「方案 A 轻量语义调用」（纯函数 + Screen 点火）；两域唯一交点是装配期冻结进 `RunState` 的 `RunModifiers` 不可变值对象。

**数据流**（图：`docs/diagrams/phase6_meta_dataflow.md` + `.html`）：

1. **开局**：RunSetupScreen 选英雄+场景（解锁态来自 `MetaService.unlockedSceneIds`）→ 域边界事件 `StartRun(seed, sceneId, heroId)`（heroId 扩展位启用，回放第 0 条记录）；BattleScreen 装配点 `newContext` 时按当前 Profile 解析 `RunModifiers` 冻结进 RunState（起始金币 = `START_GOLD + startGoldBonus`）。
2. **局内生效**：商店（reroll 权重调整 + 池门控 + 刷新折扣，RNG 消耗点数不变）、战斗（玩家侧第 3 修正源 + 羁绊增幅）消费 RunModifiers——同 seed + 同 heroId + 同命令流 ⇒ 同结果。
3. **局末**：`RunFlowSystem.endRun` 仍只产纯模拟态 `masteryAwarded`（MasteryCalculator 完整口径）；BattleScreen 观察 RUN_END 首帧 → `MetaService.settleRun`（ProfileService 纯结算 + ProfileStore 落盘 + 清快照）→ RunEndPanel 展示结算行。
4. **挂起**：进入 SHOPPING 即写 `run_snapshot.json`（全量状态 + RNG 消耗计数 + 发号器续号）；主菜单「继续远征」经 `SnapshotCodec.restore` 完整复原，跳过 StartRun。

**Screen 导航**（图：`docs/diagrams/phase6_screen_flow.md` + `.html`）：Loading → MainMenu{开始远征→RunSetup、继续远征（有快照）、图鉴→Codex} → Battle → RUN_END{RESTART（同英雄场景新 seed）/ 返回主菜单}。

**分层与文件规模**：新包 `save/`（档案域 10 类，单类 ≤220 行）；`entities` 增 RunModifiers；`data` 增 HeroData/HeroPassiveType；screens 增 2 屏。全部新类零 Gdx（ProfileStore/SnapshotStore 允许 FileHandle，日志用 System.err——沿 CommandManager.java:84 先例，JUnit 可直测）。

**确定性审计**：本期**零新增 RNG 消耗点**（Lv.2 概率加成只改权重数值；门控只过滤池内容——"消耗序与内容无关"现状口径 ShopSystem.java:26 不变）。快照复原引入 `RandomGenerator(seed, consumedCount)` 流重放，前提不变量「全部消耗点均为单次 nextFloat」已实读核实（weightedPick RandomGenerator.java:48 / 暴击 BattleSystem.java:199，无 nextInt 调用方），并以单测固化。

---

## 6. 改动点清单（评审主入口）

全部改动按依赖序排列；同一段代码的完整改动只出现一次，其余章节引用。

### CP1. GameBalance 增局外成长常量段与熟练度经验表

- **类型**：修改类（新增常量段 + 方法）
- **位置**：`core/src/main/java/com/voidvvv/kz_auto_chess_n/config/GameBalance.java:56`（经济段尾，锚点 `MERCY_CAP_PER_ROUND` 之后）与 `:151-155`（`expToNextLevel` 旁追加方法）
- **改动说明**：GDD §8.1 熟练度三件套与等级解锁表的数值唯一事实源（裁决 D2/D3/D4：Lv.1 全英雄 +2 金、Lv.4/Lv.5 工作值）。GDD 用语「熟练度经验」↔ `MASTERY_EXP_*`；「通关 +60」↔ `MASTERY_COMPLETE_BONUS`。
- **代码**（修改前，:54-56 逐字）：

```java
    public static final int CHEST_GOLD_CAP = 10;
    public static final int MERCY_START_LOSS = 3;
    public static final int MERCY_CAP_PER_ROUND = 3;
```

  （修改后，在其后追加）：

```java

    // —— 局外成长（GDD §8.1；Lv.1 解锁 = 全英雄基础权益，与英雄被动同通道叠加——裁决 D2）——
    /** 熟练度等级上限（GDD §8.1「等级上限 Lv.5」） */
    public static final int MASTERY_MAX_LEVEL = 5;
    /** Lv.1 解锁：初始金币 +2（全英雄，随开局即生效） */
    public static final int MASTERY_LV1_START_GOLD_BONUS = 2;
    /** Lv.2 解锁：商店 3 费概率加成（百分点；仅基础 3 费概率 > 0 的轮次生效——裁决 D5） */
    public static final int MASTERY_LV2_RARE_SHOP_BONUS_PP = 5;
    /** Lv.4 解锁：开局金币额外加成（工作值待调——GDD §8.1「更多待设计」裁决 D4） */
    public static final int MASTERY_LV4_START_GOLD_BONUS = 3;
    /** Lv.5 解锁：商店刷新费减免（工作值待调；实付下限 1 金——裁决 D4） */
    public static final int MASTERY_LV5_REFRESH_DISCOUNT = 1;
    /** 通关一次性熟练度经验（GDD §8.1「通关 +60」——裁决 D3） */
    public static final int MASTERY_COMPLETE_BONUS = 60;
    /** 每已达 1 轮熟练度经验（GDD §8.1「每通过 1 轮 +3」；AbandonRun 同口径 GDD §2.1） */
    public static final int MASTERY_EXP_PER_ROUND = 3;
    /** 熟练度升级经验表：Lv.1→2 起 50/100/150/200；Lv.5 封顶 0（GDD §8.1） */
    private static final int[] MASTERY_EXP_TO_NEXT = {50, 100, 150, 200, 0};
```

  （修改后，`:151-155` `expToNextLevel` 方法之后追加）：

```java

    /** 熟练度等级 → 升到下一级所需经验；Lv.5 封顶返回 0（GDD §8.1） */
    public static int masteryExpToNext(int level) {
        checkMasteryLevel(level);
        return MASTERY_EXP_TO_NEXT[level - 1];
    }
```

  （并在校验工具区追加）：

```java

    private static void checkMasteryLevel(int level) {
        if (level < 1 || level > MASTERY_MAX_LEVEL) {
            throw new IllegalArgumentException(
                    "熟练度等级必须在 1~" + MASTERY_MAX_LEVEL + "，实际=" + level);
        }
    }
```

- **测试要点**：`config/GameBalanceTest` 增——`masteryExpToNext` 五档值 {50,100,150,200,0}；越界（0/6）抛 IllegalArgumentException；各常量与 GDD §8.1 数值逐项对照断言。

### CP2. heroes 数据层（HeroPassiveType/HeroData + GameData 容器 + JsonLoader 解析与交叉校验）

- **类型**：新建文件 ×2 + 修改类 ×2
- **位置**：新建 `core/src/main/java/com/voidvvv/kz_auto_chess_n/data/HeroPassiveType.java`、`data/HeroData.java`；修改 `data/GameData.java:25-53`、`config/JsonLoader.java:57-82,513-615`
- **改动说明**：`data_schema_design.md` §一文件表 heroes.json「延后」行兑现（CP18 回写为字段权威章节）。词表 `HeroPassiveType` 三值（裁决 D17，GDD 用语「英雄被动」↔ `passive`）。加载沿 JsonLoader 显式映射 + fail-fast 口径（未知字段即死）；交叉校验：synergyIds ∈ synergies、legendaryUnitId ∈ units 且非 Boss 且 cost=3、两名英雄不得共用传奇（与 CP4 的 shopUnlocks 互斥校验在同一 `crossValidate`）。
- **代码**（新建 `data/HeroPassiveType.java`，完整）：

```java
package com.voidvvv.kz_auto_chess_n.data;

/**
 * 英雄被动词表（heroes.json "passive.type"；GDD §8.1 首发三英雄草案）。
 * 词表即代码铁律（data_schema §三）：新增被动类型 = 引擎改动，先在此登记再进 JSON（裁决 D17）。
 */
public enum HeroPassiveType implements Vocab {
    /** 开局金币加成（value = 金币数；「老兵补给」格雷克） */
    START_GOLD("START_GOLD"),
    /** 指定羁绊效果增幅（value = 百分比 25 → ×1.25；synergyIds 指定羁绊；「荆语」薇拉） */
    SYNERGY_AMP("SYNERGY_AMP"),
    /** 全队回能加成（value = 百分点 15 → ×1.15；「战歌」奥兰多） */
    ENERGY_GAIN("ENERGY_GAIN");

    private final String jsonName;

    HeroPassiveType(String jsonName) {
        this.jsonName = jsonName;
    }

    @Override
    public String jsonName() {
        return jsonName;
    }
}
```

  （新建 `data/HeroData.java`，完整）：

```java
package com.voidvvv.kz_auto_chess_n.data;

import java.util.Collections;
import java.util.List;

/**
 * 英雄（棋手）模板（heroes.json，GDD §8.1）。完全不可变，加载一次终身只读；
 * 被动 = 类型 × 强度 ×（SYNERGY_AMP 时）作用羁绊集，效果装配归 ProfileService.runModifiers。
 */
public final class HeroData {
    private final String id;
    private final String name;
    private final String desc;
    private final HeroPassiveType passiveType;
    /** 被动强度：START_GOLD=金币 / SYNERGY_AMP=百分比 / ENERGY_GAIN=百分点（HeroPassiveType javadoc） */
    private final float passiveValue;
    /** SYNERGY_AMP 作用的羁绊 id（其余类型恒空表） */
    private final List<String> passiveSynergyIds;
    /** 熟练度 Lv.3 解锁的专属传奇棋子 id（加载期校验 ∈ units 且非 Boss、cost=3）；可空 */
    private final String legendaryUnitId;

    public HeroData(String id, String name, String desc, HeroPassiveType passiveType,
                    float passiveValue, List<String> passiveSynergyIds, String legendaryUnitId) {
        this.id = id;
        this.name = name;
        this.desc = desc;
        this.passiveType = passiveType;
        this.passiveValue = passiveValue;
        this.passiveSynergyIds = Collections.unmodifiableList(
                new java.util.ArrayList<String>(passiveSynergyIds));
        this.legendaryUnitId = legendaryUnitId;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDesc() { return desc; }
    public HeroPassiveType getPassiveType() { return passiveType; }
    public float getPassiveValue() { return passiveValue; }
    public List<String> getPassiveSynergyIds() { return passiveSynergyIds; }
    public String getLegendaryUnitId() { return legendaryUnitId; }
}
```

  （`data/GameData.java` 修改前，:25-41 逐字）：

```java
    /** 兼容重载：无装备表（存量测试构造先例） */
    public GameData(Map<String, UnitData> units, Map<String, SkillData> skills,
                    Map<String, SynergyData> synergies, Map<String, SceneData> scenes,
                    List<String> warnings) {
        this(units, skills, synergies, scenes, new LinkedHashMap<String, EquipmentData>(), warnings);
    }

    public GameData(Map<String, UnitData> units, Map<String, SkillData> skills,
                    Map<String, SynergyData> synergies, Map<String, SceneData> scenes,
                    Map<String, EquipmentData> equipments, List<String> warnings) {
        this.units = Collections.unmodifiableMap(new LinkedHashMap<String, UnitData>(units));
        this.skills = Collections.unmodifiableMap(new LinkedHashMap<String, SkillData>(skills));
        this.synergies = Collections.unmodifiableMap(new LinkedHashMap<String, SynergyData>(synergies));
        this.scenes = Collections.unmodifiableMap(new LinkedHashMap<String, SceneData>(scenes));
        this.equipments = Collections.unmodifiableMap(new LinkedHashMap<String, EquipmentData>(equipments));
        this.warnings = Collections.unmodifiableList(warnings);
    }
```

  （修改后）：

```java
    /** 兼容重载：无装备表（存量测试构造先例） */
    public GameData(Map<String, UnitData> units, Map<String, SkillData> skills,
                    Map<String, SynergyData> synergies, Map<String, SceneData> scenes,
                    List<String> warnings) {
        this(units, skills, synergies, scenes, new LinkedHashMap<String, EquipmentData>(), warnings);
    }

    /** 兼容重载：无英雄表（Phase 5 存量测试构造先例——heroes 为空表） */
    public GameData(Map<String, UnitData> units, Map<String, SkillData> skills,
                    Map<String, SynergyData> synergies, Map<String, SceneData> scenes,
                    Map<String, EquipmentData> equipments, List<String> warnings) {
        this(units, skills, synergies, scenes, equipments,
                new LinkedHashMap<String, HeroData>(), warnings);
    }

    public GameData(Map<String, UnitData> units, Map<String, SkillData> skills,
                    Map<String, SynergyData> synergies, Map<String, SceneData> scenes,
                    Map<String, EquipmentData> equipments, Map<String, HeroData> heroes,
                    List<String> warnings) {
        this.units = Collections.unmodifiableMap(new LinkedHashMap<String, UnitData>(units));
        this.skills = Collections.unmodifiableMap(new LinkedHashMap<String, SkillData>(skills));
        this.synergies = Collections.unmodifiableMap(new LinkedHashMap<String, SynergyData>(synergies));
        this.scenes = Collections.unmodifiableMap(new LinkedHashMap<String, SceneData>(scenes));
        this.equipments = Collections.unmodifiableMap(new LinkedHashMap<String, EquipmentData>(equipments));
        this.heroes = Collections.unmodifiableMap(new LinkedHashMap<String, HeroData>(heroes));
        this.warnings = Collections.unmodifiableList(warnings);
    }
```

  （字段区 `:22` `equipments` 声明后追加 `private final Map<String, HeroData> heroes;`；查询区 `:53` 后追加）：

```java
    public HeroData getHero(String id) { return heroes.get(id); }
    public Map<String, HeroData> getHeroes() { return heroes; }
```

  （`config/JsonLoader.java` 修改前，:57-82 逐字）：

```java
    /** 从目录按标准文件名加载：units.json / skills.json / synergies.json / scenes.json / equipments.json */
    public static GameData loadFromDirectory(FileHandle dataDir) {
        return load(dataDir.child("units.json"), dataDir.child("skills.json"),
                dataDir.child("synergies.json"), dataDir.child("scenes.json"),
                dataDir.child("equipments.json"));
    }

    /** 兼容重载：无装备文件（存量测试路径）——装备表为空 */
    public static GameData load(FileHandle unitsFile, FileHandle skillsFile,
                                FileHandle synergiesFile, FileHandle scenesFile) {
        return load(unitsFile, skillsFile, synergiesFile, scenesFile, null);
    }

    public static GameData load(FileHandle unitsFile, FileHandle skillsFile,
                                FileHandle synergiesFile, FileHandle scenesFile,
                                FileHandle equipmentsFile) {
        Map<String, UnitData> units = parseUnits(unitsFile);
        Map<String, SkillData> skills = parseSkills(skillsFile);
        Map<String, SynergyData> synergies = parseSynergies(synergiesFile);
        Map<String, SceneData> scenes = parseScenes(scenesFile);
        Map<String, EquipmentData> equipments = parseEquipments(equipmentsFile);
        List<String> warnings = new ArrayList<String>();
        crossValidate(units, skills, synergies, scenes, warnings);
        warnEmptyRarityPools(equipments, warnings);
        return new GameData(units, skills, synergies, scenes, equipments, warnings);
    }
```

  （修改后）：

```java
    /** 从目录按标准文件名加载：units / skills / synergies / scenes / equipments / heroes */
    public static GameData loadFromDirectory(FileHandle dataDir) {
        return load(dataDir.child("units.json"), dataDir.child("skills.json"),
                dataDir.child("synergies.json"), dataDir.child("scenes.json"),
                dataDir.child("equipments.json"), dataDir.child("heroes.json"));
    }

    /** 兼容重载：无装备文件（存量测试路径）——装备表为空 */
    public static GameData load(FileHandle unitsFile, FileHandle skillsFile,
                                FileHandle synergiesFile, FileHandle scenesFile) {
        return load(unitsFile, skillsFile, synergiesFile, scenesFile, null);
    }

    /** 兼容重载：无英雄文件（Phase 5 存量测试路径）——英雄表为空 */
    public static GameData load(FileHandle unitsFile, FileHandle skillsFile,
                                FileHandle synergiesFile, FileHandle scenesFile,
                                FileHandle equipmentsFile) {
        return load(unitsFile, skillsFile, synergiesFile, scenesFile, equipmentsFile, null);
    }

    public static GameData load(FileHandle unitsFile, FileHandle skillsFile,
                                FileHandle synergiesFile, FileHandle scenesFile,
                                FileHandle equipmentsFile, FileHandle heroesFile) {
        Map<String, UnitData> units = parseUnits(unitsFile);
        Map<String, SkillData> skills = parseSkills(skillsFile);
        Map<String, SynergyData> synergies = parseSynergies(synergiesFile);
        Map<String, SceneData> scenes = parseScenes(scenesFile);
        Map<String, EquipmentData> equipments = parseEquipments(equipmentsFile);
        Map<String, HeroData> heroes = parseHeroes(heroesFile);
        List<String> warnings = new ArrayList<String>();
        crossValidate(units, skills, synergies, scenes, heroes, warnings);
        warnEmptyRarityPools(equipments, warnings);
        return new GameData(units, skills, synergies, scenes, equipments, heroes, warnings);
    }
```

  （新增解析段，置于 equipments 段之后）：

```java
    // ==================================================================
    // heroes.json（Phase 6；裁决 D17 词表制）
    // ==================================================================

    /** heroesFile 可 null（兼容重载）：null → 空表；生产路径缺文件沿 parseArray 即死 */
    private static Map<String, HeroData> parseHeroes(FileHandle file) {
        Map<String, HeroData> result = new LinkedHashMap<String, HeroData>();
        if (file == null) {
            return result;
        }
        Set<String> ids = new HashSet<String>();
        for (JsonValue e = parseArray(file).child; e != null; e = e.next) {
            requireObject(e, file.name());
            String id = requireString(e, "id", file.name() + "#?");
            String w = file.name() + "#" + id + "/";
            if (!ids.add(id)) {
                fail(w, "id 全文件唯一，重复声明");
            }
            checkUnknownKeys(e, w, "id", "name", "desc", "passive", "legendaryUnitId");

            String name = requireString(e, "name", w);
            String desc = requireString(e, "desc", w);
            JsonValue passiveNode = require(e, "passive", w);
            String pw = w + "passive/";
            requireObject(passiveNode, pw);
            checkUnknownKeys(passiveNode, pw, "type", "value", "synergyIds");
            HeroPassiveType type = requireVocab(passiveNode, "type", HeroPassiveType.class, pw);
            float value = requireFloat(passiveNode, "value", pw);
            if (value <= 0) {
                fail(pw + "value", "必须 > 0，实际=" + value);
            }
            List<String> synergyIds = new ArrayList<String>();
            JsonValue synergyNode = passiveNode.get("synergyIds");
            if (synergyNode != null && !synergyNode.isNull()) {
                if (!synergyNode.isArray()) {
                    fail(pw + "synergyIds", "必须为数组");
                }
                for (JsonValue s = synergyNode.child; s != null; s = s.next) {
                    if (!s.isString() || s.asString().trim().isEmpty()) {
                        fail(pw + "synergyIds", "元素必须为非空字符串（羁绊 id）");
                    }
                    synergyIds.add(s.asString());
                }
            }
            if (type == HeroPassiveType.SYNERGY_AMP && synergyIds.isEmpty()) {
                fail(pw + "synergyIds", "SYNERGY_AMP 必须指定至少 1 个羁绊 id");
            }
            if (type != HeroPassiveType.SYNERGY_AMP && !synergyIds.isEmpty()) {
                fail(pw + "synergyIds", "仅 SYNERGY_AMP 允许 synergyIds");
            }
            String legendaryUnitId = optionalString(e, "legendaryUnitId", w);
            result.put(id, new HeroData(id, name, desc, type, value, synergyIds, legendaryUnitId));
        }
        return result;
    }
```

  （`crossValidate` 签名与新增段——修改前 `:513-515` 逐字）：

```java
    private static void crossValidate(Map<String, UnitData> units, Map<String, SkillData> skills,
                                      Map<String, SynergyData> synergies, Map<String, SceneData> scenes,
                                      List<String> warnings) {
```

  （修改后签名 + 方法尾追加校验块；shopUnlocks 部分与 CP4 合并实现，完整代码见 §6.CP4）：

```java
    private static void crossValidate(Map<String, UnitData> units, Map<String, SkillData> skills,
                                      Map<String, SynergyData> synergies, Map<String, SceneData> scenes,
                                      Map<String, HeroData> heroes, List<String> warnings) {
        // ……（既有 1~9 校验块原样保留）……
        // 10. heroes 交叉校验（Phase 6）：synergyIds ∈ synergies；传奇 ∈ units 且非 Boss、cost=3；传奇不得共用
        Set<String> legendaryUnits = new HashSet<String>();
        for (HeroData hero : heroes.values()) {
            String hw = "heroes.json#" + hero.getId() + "/";
            for (String synergyId : hero.getPassiveSynergyIds()) {
                if (!synergies.containsKey(synergyId)) {
                    fail(hw + "passive/synergyIds", "引用了不存在的羁绊: " + synergyId);
                }
            }
            String legendary = hero.getLegendaryUnitId();
            if (legendary != null) {
                UnitData unit = units.get(legendary);
                if (unit == null) {
                    fail(hw + "legendaryUnitId", "引用了不存在的单位: " + legendary);
                }
                if (unit != null && (unit.isBoss() || unit.getCost() != 3)) {
                    fail(hw + "legendaryUnitId", "传奇棋子必须为非 Boss 且 cost=3: " + legendary);
                }
                if (!legendaryUnits.add(legendary)) {
                    fail(hw + "legendaryUnitId", "传奇棋子被多名英雄共用: " + legendary);
                }
            }
        }
        // 11. 场景 shopUnlocks 交叉校验（与 CP4 合并实现，代码见 §6.CP4）
    }
```

  （import 区追加 `import com.voidvvv.kz_auto_chess_n.data.HeroData;` 与 `import com.voidvvv.kz_auto_chess_n.data.HeroPassiveType;`）
- **测试要点**：新建 `config/JsonLoaderHeroesTest`——合法 3 英雄解析（type/value/synergyIds/legendary 透传）；heroes.json 缺失（null）→ 空表不炸；`GameData` 7 参构造 heroes 不可变视图断言；非法枚举 type / value ≤ 0 / SYNERGY_AMP 无 synergyIds / synergyIds 悬空 / legendary 悬空 / legendary 为 Boss / legendary cost≠3 / 传奇共用 → DataValidationException 且报错含 `heroes.json#id/字段路径`；未知字段 fail。

### CP3. assets/data/heroes.json 种子内容（3 英雄）

- **类型**：新建数据文件
- **位置**：`assets/data/heroes.json`
- **改动说明**：GDD §8.1 三英雄 + lore §五雏形（身份/流派）。desc 手写文案沿 synergies.json desc 先例（Phase 5.1 裁决 2 口径）。数值即 GDD 草案锚点；传奇棋子模板在 CP11 units.json 落地（此处先引用，CP2 加载校验要求二者同批合入——任务拆解 T1/T4 同批提交）。
- **代码**（完整文件）：

```json
[
  {
    "id": "hero_greg", "name": "老兵格雷克",
    "desc": "旧王国军士，熄星之夜的幸存者，深知补给线的价值",
    "passive": { "type": "START_GOLD", "value": 2 },
    "legendaryUnitId": "unit_legend_quartermaster"
  },
  {
    "id": "hero_vera", "name": "荆语者薇拉",
    "desc": "翡翠林地的德鲁伊，能听懂狂化野兽的哀鸣",
    "passive": { "type": "SYNERGY_AMP", "value": 25, "synergyIds": ["syn_beast", "syn_ranger"] },
    "legendaryUnitId": "unit_legend_thornhart"
  },
  {
    "id": "hero_orlando", "name": "灰烬诗人奥兰多",
    "desc": "用歌谣记录远征的吟游诗人，军中的士气之源",
    "passive": { "type": "ENERGY_GAIN", "value": 15 },
    "legendaryUnitId": "unit_legend_warsong_singer"
  }
]
```

- **测试要点**：`config/JsonLoaderTest`（真实资产）增断言——真实 heroes.json 加载 hasSize(3)、三 id 齐、薇拉 synergyIds 含 syn_beast/syn_ranger；加载零软告警不回归。

### CP4. SceneData.shopUnlocks 字段与加载校验（场景门控进商店池）

- **类型**：修改类 ×2
- **位置**：`data/SceneData.java:16-42`；`config/JsonLoader.java:331-416`（scenes 段）+ crossValidate 新增块（CP2 已改签名）
- **改动说明**：裁决 D8——「亡灵/巨人棋子随场景解锁进商店池」由 scenes.json 可选字段 `shopUnlocks[]`（单位 id 引用）承载，units.json 字段表不动。语义：**未列入任何 shopUnlocks 的非 Boss 单位 = 基础池（森林即可购）**；列入某场景 = 该场景解锁后才入池。加载期校验：∈ units、非 Boss、场景内不重复、不跨场景重复、不得为任何英雄传奇（互斥——传奇只经 Lv.3 门控）。GDD 用语「专属棋子（随场景）」↔ `shopUnlocks`；「英雄专属传奇棋子」↔ `HeroData.legendaryUnitId`。
- **代码**（`SceneData.java` 修改前，:16-32 逐字）：

```java
public final class SceneData {
    private final String id;
    private final String name;
    /** 解锁前置场景 id；null = 初始开放（data_schema §七）。档案域判定属 Phase 6，加载期仅校验引用 */
    private final String unlockAfter;
    private final List<EnemyPoolEntry> enemyPool;
    /** {7, 15, 25} → Boss 模板 id（加载期保证三键齐全且被引用模板 isBoss） */
    private final Map<Integer, String> bosses;

    public SceneData(String id, String name, String unlockAfter,
                     List<EnemyPoolEntry> enemyPool, Map<Integer, String> bosses) {
        this.id = id;
        this.name = name;
        this.unlockAfter = unlockAfter;
        this.enemyPool = Collections.unmodifiableList(new ArrayList<EnemyPoolEntry>(enemyPool));
        this.bosses = Collections.unmodifiableMap(new LinkedHashMap<Integer, String>(bosses));
    }
```

  （修改后）：

```java
public final class SceneData {
    private final String id;
    private final String name;
    /** 解锁前置场景 id；null = 初始开放（data_schema §七）。解锁判定 = ProfileService 派生（Phase 6，裁决 D7） */
    private final String unlockAfter;
    private final List<EnemyPoolEntry> enemyPool;
    /** {7, 15, 25} → Boss 模板 id（加载期保证三键齐全且被引用模板 isBoss） */
    private final Map<Integer, String> bosses;
    /** 该场景解锁后进入商店池的单位 id（Phase 6 裁决 D8；空表 = 无场景门控单位） */
    private final List<String> shopUnlocks;

    /** 兼容构造（Phase 5 存量测试先例）：无 shopUnlocks */
    public SceneData(String id, String name, String unlockAfter,
                     List<EnemyPoolEntry> enemyPool, Map<Integer, String> bosses) {
        this(id, name, unlockAfter, enemyPool, bosses, new ArrayList<String>());
    }

    public SceneData(String id, String name, String unlockAfter,
                     List<EnemyPoolEntry> enemyPool, Map<Integer, String> bosses,
                     List<String> shopUnlocks) {
        this.id = id;
        this.name = name;
        this.unlockAfter = unlockAfter;
        this.enemyPool = Collections.unmodifiableList(new ArrayList<EnemyPoolEntry>(enemyPool));
        this.bosses = Collections.unmodifiableMap(new LinkedHashMap<Integer, String>(bosses));
        this.shopUnlocks = Collections.unmodifiableList(new ArrayList<String>(shopUnlocks));
    }
```

  （getter 区 `:37` `getUnlockAfter` 后追加）：

```java
    /** 该场景解锁后进入商店池的单位 id（不可变视图，声明序） */
    public List<String> getShopUnlocks() { return shopUnlocks; }
```

  （`JsonLoader.parseScenes` 修改前，:341-348 逐字）：

```java
            checkUnknownKeys(e, w, "id", "name", "unlockAfter", "enemyPool", "bosses");

            String name = requireString(e, "name", w);
            String unlockAfter = optionalString(e, "unlockAfter", w);
            List<SceneData.EnemyPoolEntry> enemyPool = parseEnemyPool(require(e, "enemyPool", w), w);
            Map<Integer, String> bosses = parseBosses(require(e, "bosses", w), w);

            result.put(id, new SceneData(id, name, unlockAfter, enemyPool, bosses));
```

  （修改后）：

```java
            checkUnknownKeys(e, w, "id", "name", "unlockAfter", "enemyPool", "bosses", "shopUnlocks");

            String name = requireString(e, "name", w);
            String unlockAfter = optionalString(e, "unlockAfter", w);
            List<SceneData.EnemyPoolEntry> enemyPool = parseEnemyPool(require(e, "enemyPool", w), w);
            Map<Integer, String> bosses = parseBosses(require(e, "bosses", w), w);
            List<String> shopUnlocks = parseShopUnlocks(e.get("shopUnlocks"), w);

            result.put(id, new SceneData(id, name, unlockAfter, enemyPool, bosses, shopUnlocks));
```

  （新增解析方法，置于 `parseBosses` 之后）：

```java
    /** shopUnlocks（可选数组，缺省/null = 空表）：场景解锁后进入商店池的单位 id */
    private static List<String> parseShopUnlocks(JsonValue node, String w) {
        List<String> result = new ArrayList<String>();
        if (node == null || node.isNull()) {
            return result;
        }
        if (!node.isArray()) {
            fail(w + "shopUnlocks", "必须为数组（单位 id）");
        }
        Set<String> seen = new HashSet<String>();
        for (JsonValue u = node.child; u != null; u = u.next) {
            if (!u.isString() || u.asString().trim().isEmpty()) {
                fail(w + "shopUnlocks", "元素必须为非空字符串（单位 id）");
            }
            String unitId = u.asString();
            if (!seen.add(unitId)) {
                fail(w + "shopUnlocks", "场景内重复单位: " + unitId);
            }
            result.add(unitId);
        }
        return result;
    }
```

  （crossValidate 内、CP2 的「10. heroes」块之后追加——`legendaryUnits` 集已在 CP2 块产出）：

```java
        // 11. shopUnlocks 引用校验（裁决 D8）：∈ units、非 Boss、不跨场景重复、不得为英雄传奇
        Set<String> shopUnlockAll = new HashSet<String>();
        for (SceneData scene : scenes.values()) {
            for (String unitId : scene.getShopUnlocks()) {
                String sw = "scenes.json#" + scene.getId() + "/shopUnlocks";
                UnitData unit = units.get(unitId);
                if (unit == null) {
                    fail(sw, "引用了不存在的单位: " + unitId);
                }
                if (unit != null && unit.isBoss()) {
                    fail(sw, "Boss 模板不得进入商店池: " + unitId);
                }
                if (!shopUnlockAll.add(unitId)) {
                    fail(sw, "单位被多个场景 shopUnlocks 重复登记: " + unitId);
                }
                if (legendaryUnits.contains(unitId)) {
                    fail(sw, "英雄专属传奇不得登记进场景商店池（两机制互斥）: " + unitId);
                }
            }
        }
```

- **测试要点**：`config/JsonLoaderScenesTest` 增——shopUnlocks 解析/缺省空表/兼容构造空表；引用悬空、Boss 模板、跨场景重复、与英雄传奇冲突 → DataValidationException（报错路径 `scenes.json#scene_x/shopUnlocks`）；`data/SceneDataTest` 增 getter 不可变断言。

### CP5. entities/RunModifiers（局外修正聚合值对象）

- **类型**：新建文件
- **位置**：`core/src/main/java/com/voidvvv/kz_auto_chess_n/entities/RunModifiers.java`
- **改动说明**：英雄被动 × 熟练度等级 × 场景解锁的**单点聚合**（GDD §8.1 解锁表 + 三被动），由 `ProfileService.runModifiers` 纯函数产出、装配期冻结进 `RunState`（同 seed/sceneId 语义）。实现 `StatModifierSource`（裁决 D13：奥兰多「战歌」= energyGainRate ADD 百分点，进 BattleSystem 修正源列表，走 DamagePipeline:72 现成管线）；商店池门控经 `isShopAllowed`（`shopPoolRestricted=false` 的 EMPTY 语义 = 不门控，兼容存量测试路径）。
- **代码**（完整）：

```java
package com.voidvvv.kz_auto_chess_n.entities;

import com.voidvvv.kz_auto_chess_n.data.EffectOp;
import com.voidvvv.kz_auto_chess_n.data.StatKey;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 局外修正聚合（Phase 6，GDD §8.1）：英雄被动 + 熟练度等级解锁 + 场景解锁门控的不可变快照。
 * 由 ProfileService.runModifiers 纯函数在装配期产出、冻结进 RunState——局内任何系统只读，
 * 同 seed + 同 heroId + 同命令流 ⇒ 同结果（确定性口径不变）。
 *
 * <p>实现 {@link StatModifierSource}（裁决 D13）：当前唯一修正 = 全队回能 ADD 百分点
 * （「战歌」energyGainRate，结算 ÷100 乘数——data_schema §三刻度约定）；敌方侧不注入。
 */
public final class RunModifiers implements StatModifierSource {

    /** 空修正（无英雄/存量测试路径）：全部零增益、不门控商店池 */
    public static final RunModifiers EMPTY = new RunModifiers(0, 0, 0, 0,
            Collections.<String, Float>emptyMap(), null, Collections.<String>emptySet(), false);

    private final int startGoldBonus;
    private final int refreshCostDiscount;
    /** 商店 3 费概率加成（百分点；仅基础 3 费概率 > 0 的轮次生效——裁决 D5） */
    private final int rareShopBonusPp;
    /** 全队回能加成（百分点，energyGainRate ADD） */
    private final int energyGainRateBonus;
    /** 羁绊增幅：synergyId → 比例（0.25 = +25%，「荆语」——裁决 D12） */
    private final Map<String, Float> synergyAmp;
    /** 本英雄专属传奇棋子 id（熟练度 Lv.3 起；可空） */
    private final String legendaryUnitId;
    /** 受门控时的可购单位 id 集 */
    private final Set<String> shopPoolUnitIds;
    /** 是否门控商店池（false = 全量非 Boss 池，兼容路径） */
    private final boolean shopPoolRestricted;

    public RunModifiers(int startGoldBonus, int refreshCostDiscount, int rareShopBonusPp,
                        int energyGainRateBonus, Map<String, Float> synergyAmp,
                        String legendaryUnitId, Set<String> shopPoolUnitIds,
                        boolean shopPoolRestricted) {
        this.startGoldBonus = Math.max(0, startGoldBonus);
        this.refreshCostDiscount = Math.max(0, refreshCostDiscount);
        this.rareShopBonusPp = Math.max(0, rareShopBonusPp);
        this.energyGainRateBonus = Math.max(0, energyGainRateBonus);
        this.synergyAmp = Collections.unmodifiableMap(
                new java.util.LinkedHashMap<String, Float>(synergyAmp));
        this.legendaryUnitId = legendaryUnitId;
        this.shopPoolUnitIds = Collections.unmodifiableSet(
                new LinkedHashSet<String>(shopPoolUnitIds));
        this.shopPoolRestricted = shopPoolRestricted;
    }

    public int getStartGoldBonus() { return startGoldBonus; }
    public int getRefreshCostDiscount() { return refreshCostDiscount; }
    public int getRareShopBonusPp() { return rareShopBonusPp; }
    public int getEnergyGainRateBonus() { return energyGainRateBonus; }
    public Map<String, Float> getSynergyAmp() { return synergyAmp; }
    public String getLegendaryUnitId() { return legendaryUnitId; }
    public boolean isShopPoolRestricted() { return shopPoolRestricted; }

    /** 单位是否可购（门控 = 场景 shopUnlocks 已解锁 + 本英雄传奇 Lv.3；未门控恒 true） */
    public boolean isShopAllowed(String unitId) {
        return !shopPoolRestricted || shopPoolUnitIds.contains(unitId);
    }

    @Override
    public StatModifierBlock modifiers() {
        return energyGainRateBonus == 0
                ? StatModifierBlock.empty()
                : StatModifierBlock.of(StatKey.ENERGY_GAIN_RATE, EffectOp.ADD, energyGainRateBonus);
    }
}
```

- **测试要点**：新建 `entities/RunModifiersTest`——构造防御（负增益钳 0）；`EMPTY.isShopAllowed` 恒 true；`EMPTY.modifiers()` 为空块；synergyAmp/shopPoolUnitIds 不可变视图（put 抛 UnsupportedOperationException）；带 energyGainRateBonus 时 `modifiers().addOf(ENERGY_GAIN_RATE)` 透传。

### CP6. save/Profile + HeroProgress + ProfileService（纯函数档案域核心）

- **类型**：新建文件 ×3（新包 `save/`）
- **位置**：`core/src/main/java/com/voidvvv/kz_auto_chess_n/save/Profile.java`、`save/HeroProgress.java`、`save/ProfileService.java`
- **改动说明**：architecture §三「方案 A」落地——档案实体不可变（整体替换式更新，沿不可变优先约束）；规则全部纯函数零 Gdx。熟练度升级沿 `Player.addExp` 连续升级先例（跨级、Lv.5 封顶余量作废——裁决 D2/D3）；场景解锁 = completedScenes 派生（裁决 D7）。GDD 用语「熟练度经验」↔ `HeroProgress.exp`；「通关场景登记」↔ `Profile.withCompletedScene`。
- **代码**（`save/HeroProgress.java` 完整）：

```java
package com.voidvvv.kz_auto_chess_n.save;

/** 单英雄熟练度进度（不可变：等级 1~5 + 当前级内经验）。 */
public final class HeroProgress {
    private final int level;
    private final int exp;

    public HeroProgress(int level, int exp) {
        if (level < 1 || level > 5) {
            throw new IllegalArgumentException("熟练度等级必须在 1~5，实际=" + level);
        }
        this.level = level;
        this.exp = Math.max(0, exp);
    }

    /** 初始进度（Lv.1 / 0 经验——GDD §8.1 等级表起点） */
    public static HeroProgress initial() {
        return new HeroProgress(1, 0);
    }

    public int getLevel() { return level; }
    public int getExp() { return exp; }
}
```

  （`save/Profile.java` 完整）：

```java
package com.voidvvv.kz_auto_chess_n.save;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 局外档案（architecture §一「局外档案态」；不可变，整体替换式更新）。
 * 场景解锁不落档——由 completedScenes 经 unlockAfter 链派生（裁决 D7，防双源漂移）。
 */
public final class Profile {
    public static final int CURRENT_VERSION = 1;

    private final int version;
    private final Map<String, HeroProgress> heroProgress;
    private final Set<String> completedScenes;

    public Profile(int version, Map<String, HeroProgress> heroProgress, Set<String> completedScenes) {
        this.version = version;
        this.heroProgress = Collections.unmodifiableMap(
                new java.util.LinkedHashMap<String, HeroProgress>(heroProgress));
        this.completedScenes = Collections.unmodifiableSet(
                new LinkedHashSet<String>(completedScenes));
    }

    /** 初始档案（无进度、无通关记录） */
    public static Profile fresh() {
        return new Profile(CURRENT_VERSION,
                new java.util.LinkedHashMap<String, HeroProgress>(),
                new LinkedHashSet<String>());
    }

    public int getVersion() { return version; }
    public Map<String, HeroProgress> getHeroProgress() { return heroProgress; }
    public Set<String> getCompletedScenes() { return completedScenes; }

    /** 替换式更新：写入/覆盖单英雄进度（返回新档案） */
    public Profile withHeroProgress(String heroId, HeroProgress progress) {
        java.util.LinkedHashMap<String, HeroProgress> next =
                new java.util.LinkedHashMap<String, HeroProgress>(heroProgress);
        next.put(heroId, progress);
        return new Profile(version, next, completedScenes);
    }

    /** 替换式更新：登记通关场景（幂等；返回新档案） */
    public Profile withCompletedScene(String sceneId) {
        if (completedScenes.contains(sceneId)) {
            return this;
        }
        Set<String> next = new LinkedHashSet<String>(completedScenes);
        next.add(sceneId);
        return new Profile(version, heroProgress, next);
    }
}
```

  （`save/ProfileService.java` 完整）：

```java
package com.voidvvv.kz_auto_chess_n.save;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.HeroData;
import com.voidvvv.kz_auto_chess_n.data.SceneData;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.RunEndCause;
import com.voidvvv.kz_auto_chess_n.entities.RunModifiers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 档案域纯函数服务（architecture §三：解锁判定/熟练度结算必须纯函数，禁止写进 ClickListener）。
 * 零 Gdx、零副作用；入参 Profile 不可变，产出新 Profile / 值对象。
 */
public final class ProfileService {

    private ProfileService() {
    }

    /** 当前熟练度等级（未记录英雄 = Lv.1 起步） */
    public static int masteryLevel(Profile profile, String heroId) {
        Objects.requireNonNull(profile, "profile 不能为 null");
        HeroProgress progress = profile.getHeroProgress().get(heroId);
        return progress == null ? 1 : progress.getLevel();
    }

    /** 已解锁场景 id 集：unlockAfter == null 或前置场景已通关（GDD §8.2；派生不落档——裁决 D7） */
    public static Set<String> unlockedSceneIds(Profile profile, GameData data) {
        Objects.requireNonNull(profile, "profile 不能为 null");
        Objects.requireNonNull(data, "data 不能为 null");
        Set<String> unlocked = new LinkedHashSet<String>();
        for (SceneData scene : data.getScenes().values()) {
            if (scene.getUnlockAfter() == null
                    || profile.getCompletedScenes().contains(scene.getUnlockAfter())) {
                unlocked.add(scene.getId());
            }
        }
        return Collections.unmodifiableSet(unlocked);
    }

    /**
     * 局末结算（GDD §8.1/§8.2）：熟练度经验入账（连续升级，Lv.5 封顶余量作废——沿
     * Player.addExp 先例）+ 通关场景登记 + 新解锁场景对比产出。
     *
     * @param awardedExp MasteryCalculator 产出的本局熟练度（负值防御钳 0）
     */
    public static Settlement settle(Profile profile, GameData data, String heroId, String sceneId,
                                    RunEndCause cause, int roundsReached, int awardedExp) {
        Objects.requireNonNull(profile, "profile 不能为 null");
        Objects.requireNonNull(data, "data 不能为 null");
        Objects.requireNonNull(cause, "cause 不能为 null");
        int gained = Math.max(0, awardedExp);
        int levelFrom = 1;
        int exp = gained;
        HeroProgress old = heroId == null ? null : profile.getHeroProgress().get(heroId);
        if (old != null) {
            levelFrom = old.getLevel();
            exp = old.getExp() + gained;
        }
        int level = levelFrom;
        while (level < GameBalance.MASTERY_MAX_LEVEL
                && exp >= GameBalance.masteryExpToNext(level)) {
            exp -= GameBalance.masteryExpToNext(level);
            level++;
        }
        if (level >= GameBalance.MASTERY_MAX_LEVEL) {
            exp = 0; // 封顶余量作废（裁决 D2）
        }
        Profile next = profile;
        if (heroId != null) {
            next = next.withHeroProgress(heroId, new HeroProgress(level, exp));
        }
        if (cause == RunEndCause.COMPLETED && sceneId != null) {
            next = next.withCompletedScene(sceneId);
        }
        Set<String> unlockedBefore = unlockedSceneIds(profile, data);
        Set<String> unlockedAfter = unlockedSceneIds(next, data);
        List<String> newlyUnlocked = new ArrayList<String>(unlockedAfter);
        newlyUnlocked.removeAll(unlockedBefore);
        int expToNext = level >= GameBalance.MASTERY_MAX_LEVEL
                ? 0 : GameBalance.masteryExpToNext(level);
        return new Settlement(next, gained, levelFrom, level, exp, expToNext,
                Collections.unmodifiableList(newlyUnlocked));
    }

    /**
     * 局外修正聚合（装配期一次）：英雄被动 + 熟练度等级解锁表（GDD §8.1）+ 场景门控商店池。
     * hero 可空（防御路径：无英雄局——旧测试/异常装配）→ 仅场景门控 + Lv.1 基础权益。
     */
    public static RunModifiers runModifiers(HeroData hero, Profile profile, GameData data) {
        Objects.requireNonNull(profile, "profile 不能为 null");
        Objects.requireNonNull(data, "data 不能为 null");
        int level = hero == null ? 1 : masteryLevel(profile, hero.getId());

        int startGoldBonus = GameBalance.MASTERY_LV1_START_GOLD_BONUS; // Lv.1 全英雄基础权益（裁决 D2）
        int rarePp = 0;
        int refreshDiscount = 0;
        if (level >= 2) {
            rarePp = GameBalance.MASTERY_LV2_RARE_SHOP_BONUS_PP;
        }
        if (level >= 4) {
            startGoldBonus += GameBalance.MASTERY_LV4_START_GOLD_BONUS;
        }
        if (level >= 5) {
            refreshDiscount = GameBalance.MASTERY_LV5_REFRESH_DISCOUNT;
        }

        int energyPp = 0;
        Map<String, Float> amp = new LinkedHashMap<String, Float>();
        if (hero != null) {
            switch (hero.getPassiveType()) {
                case START_GOLD:
                    startGoldBonus += Math.round(hero.getPassiveValue());
                    break;
                case SYNERGY_AMP:
                    for (String synergyId : hero.getPassiveSynergyIds()) {
                        amp.put(synergyId, hero.getPassiveValue() / 100f);
                    }
                    break;
                case ENERGY_GAIN:
                    energyPp = Math.round(hero.getPassiveValue());
                    break;
                default:
                    throw new IllegalStateException("未知英雄被动类型: " + hero.getPassiveType());
            }
        }

        String legendary = level >= 3 && hero != null ? hero.getLegendaryUnitId() : null;
        return new RunModifiers(startGoldBonus, refreshDiscount, rarePp, energyPp, amp, legendary,
                shopPool(profile, data, legendary), true);
    }

    /** 可购单位池（裁决 D8）：非 Boss；场景门控单位需场景已解锁；他人传奇不可见、本英雄传奇 Lv.3 起可见 */
    private static Set<String> shopPool(Profile profile, GameData data, String ownLegendary) {
        Set<String> unlocked = unlockedSceneIds(profile, data);
        Set<String> legendaryAll = new HashSet<String>();
        for (HeroData heroEntry : data.getHeroes().values()) {
            if (heroEntry.getLegendaryUnitId() != null) {
                legendaryAll.add(heroEntry.getLegendaryUnitId());
            }
        }
        Set<String> pool = new LinkedHashSet<String>();
        for (UnitData unit : data.getUnits().values()) {
            if (unit.isBoss()) {
                continue;
            }
            String id = unit.getId();
            boolean sceneOk = true;
            for (SceneData scene : data.getScenes().values()) {
                if (scene.getShopUnlocks().contains(id) && !unlocked.contains(scene.getId())) {
                    sceneOk = false;
                    break;
                }
            }
            boolean legendaryOk = !legendaryAll.contains(id) || id.equals(ownLegendary);
            if (sceneOk && legendaryOk) {
                pool.add(id);
            }
        }
        return pool;
    }

    /** 局末结算产物（不可变；展示文案由 RunSettlementText 生成） */
    public static final class Settlement {
        private final Profile newProfile;
        private final int expGained;
        private final int levelFrom;
        private final int levelTo;
        private final int expIntoLevel;
        private final int expToNextLevel;
        private final List<String> newlyUnlockedSceneIds;

        Settlement(Profile newProfile, int expGained, int levelFrom, int levelTo,
                   int expIntoLevel, int expToNextLevel, List<String> newlyUnlockedSceneIds) {
            this.newProfile = newProfile;
            this.expGained = expGained;
            this.levelFrom = levelFrom;
            this.levelTo = levelTo;
            this.expIntoLevel = expIntoLevel;
            this.expToNextLevel = expToNextLevel;
            this.newlyUnlockedSceneIds = newlyUnlockedSceneIds;
        }

        public Profile getNewProfile() { return newProfile; }
        public int getExpGained() { return expGained; }
        public int getLevelFrom() { return levelFrom; }
        public int getLevelTo() { return levelTo; }
        public int getExpIntoLevel() { return expIntoLevel; }
        public int getExpToNextLevel() { return expToNextLevel; }
        public List<String> getNewlyUnlockedSceneIds() { return newlyUnlockedSceneIds; }
    }
}
```

- **测试要点**：新建 `save/ProfileServiceTest`（夹具手搓 GameData，沿 BattleTestFixtures 先例）——
  - settle：放弃 r1（+3 → Lv.1 exp3）、通关 25 轮（+135 → Lv.1 exp50 即升 Lv.2 余 85/100）、大额跨级（exp 500 → 直达 Lv.5 余 0）、Lv.5 封顶余量作废、COMPLETED 登记 completedScenes 幂等、ABANDONED 不登记、heroId null 容忍、新解锁场景对比（通关森林→墓穴入列、雪山未入）；
  - unlockedSceneIds：fresh 档仅森林；通关森林后墓穴；通关墓穴后雪山；
  - runModifiers：Lv.1 = +2 金；Lv.2 = +5pp；Lv.3 = 本英雄传奇入池且他人传奇被排除；Lv.4 = +5 金合计；Lv.5 = 折扣 1；薇拉 amp map {syn_beast:0.25, syn_ranger:0.25}；奥兰多 energyPp 15；格雷克 Lv.1 startGoldBonus=4（2+2，裁决 D2）；场景门控（未解锁场景的 shopUnlocks 单位不在池、解锁后在池）；hero null → 仅门控+基础权益。

### CP7. save/ProfileCodec + ProfileStore + MetaService（档案持久化与门面）+ .gitignore

- **类型**：新建文件 ×3 + 修改 `.gitignore`
- **位置**：`save/ProfileCodec.java`、`save/ProfileStore.java`、`save/MetaService.java`；`.gitignore:172`（文件尾）
- **改动说明**：裁决 D14——JSON 明文经 `Gdx.files.local("save/")`；Codec 纯 String 双向（写入手拼 JSON——档案无自由文本、id 均经加载校验，无转义需求；读取复用 libGDX JsonReader 显式映射，沿 JsonLoader 口径：未知字段/非法版本 fail）；Store 薄 IO 层（损坏重置不炸——裁决 D20，日志 System.err 沿 CommandManager.java:84 先例）；`MetaService` = 档案域门面（Screen 唯一入口，architecture §三「Screen 只做点火器」延伸；快照方法签名在此定义、实现委托 CP16 的 SnapshotStore）。lwjgl3 workingDir=assets（lwjgl3/build.gradle:46）→ 运行期文件落 `assets/save/`，须 gitignore。
- **代码**（`save/ProfileCodec.java` 完整）：

```java
package com.voidvvv.kz_auto_chess_n.save;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.voidvvv.kz_auto_chess_n.config.DataValidationException;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

/**
 * 档案 JSON 编解码（纯 String 双向，零 FileHandle/Gdx——JUnit 直测）。
 * 格式：{"version":1,"heroes":{"hero_x":{"level":2,"exp":35}},"completedScenes":["scene_forest"]}
 * 读侧沿 JsonLoader 口径：显式映射 + 未知字段即死；版本不符抛错（由 Store 决定重置）。
 */
public final class ProfileCodec {

    private static final JsonReader READER = new JsonReader();

    private ProfileCodec() {
    }

    public static String write(Profile profile) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("{\"version\":").append(profile.getVersion());
        sb.append(",\"heroes\":{");
        boolean first = true;
        for (java.util.Map.Entry<String, HeroProgress> e : profile.getHeroProgress().entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(e.getKey()).append("\":{\"level\":").append(e.getValue().getLevel())
                    .append(",\"exp\":").append(e.getValue().getExp()).append('}');
        }
        sb.append("},\"completedScenes\":[");
        first = true;
        for (String sceneId : profile.getCompletedScenes()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(sceneId).append('"');
        }
        sb.append("]}");
        return sb.toString();
    }

    public static Profile read(String json) {
        JsonValue root = READER.parse(json == null || json.trim().isEmpty() ? "{}" : json);
        if (!root.isObject()) {
            throw new DataValidationException("profile.json: 根节点必须是对象");
        }
        checkUnknownKeys(root, "version", "heroes", "completedScenes");
        int version = requireInt(root, "version");
        if (version != Profile.CURRENT_VERSION) {
            throw new DataValidationException(
                    "profile.json/version: 不支持的档案版本 " + version + "（当前 " + Profile.CURRENT_VERSION + "）");
        }
        LinkedHashMap<String, HeroProgress> heroes = new LinkedHashMap<String, HeroProgress>();
        JsonValue heroesNode = root.get("heroes");
        if (heroesNode != null && heroesNode.isObject()) {
            for (JsonValue h = heroesNode.child; h != null; h = h.next) {
                checkUnknownKeys(h, "level", "exp");
                heroes.put(h.name(), new HeroProgress(requireInt(h, "level"), requireInt(h, "exp")));
            }
        }
        LinkedHashSet<String> completed = new LinkedHashSet<String>();
        JsonValue scenesNode = root.get("completedScenes");
        if (scenesNode != null && scenesNode.isArray()) {
            for (JsonValue s = scenesNode.child; s != null; s = s.next) {
                if (!s.isString() || s.asString().trim().isEmpty()) {
                    throw new DataValidationException("profile.json/completedScenes: 元素必须为非空字符串");
                }
                completed.add(s.asString());
            }
        }
        return new Profile(version, heroes, completed);
    }

    private static void checkUnknownKeys(JsonValue obj, String... allowed) {
        for (JsonValue child = obj.child; child != null; child = child.next) {
            boolean known = false;
            for (String a : allowed) {
                if (a.equals(child.name())) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                throw new DataValidationException("profile.json: 未知字段 " + child.name()
                        + "（允许: " + java.util.Arrays.toString(allowed) + "）");
            }
        }
    }

    private static int requireInt(JsonValue obj, String field) {
        JsonValue child = obj.get(field);
        if (child == null || child.isNull() || !child.isNumber()) {
            throw new DataValidationException("profile.json/" + field + ": 缺失或非数字");
        }
        double d = child.asDouble();
        if (Math.rint(d) != d) {
            throw new DataValidationException("profile.json/" + field + ": 必须为整数，实际=" + d);
        }
        return (int) d;
    }
}
```

  （`save/ProfileStore.java` 完整）：

```java
package com.voidvvv.kz_auto_chess_n.save;

import com.badlogic.gdx.files.FileHandle;

/**
 * 档案文件 IO（薄层）：缺失 → 初始档案；解析/校验失败 → 记日志并重置（裁决 D20，
 * 玩家数据演进常态不炸档）；写入失败 → 记日志返回 false（调用方决定表现，不中断局内）。
 * 日志走 System.err（沿 CommandManager 先例，JUnit 零 Gdx.app 可测）。
 */
public final class ProfileStore {

    private final FileHandle file;

    public ProfileStore(FileHandle file) {
        this.file = file == null ? null : file;
    }

    public Profile load() {
        if (file == null || !file.exists()) {
            return Profile.fresh();
        }
        try {
            return ProfileCodec.read(file.readString("UTF-8"));
        } catch (RuntimeException ex) {
            System.err.println("[ProfileStore] 档案损坏，重置为初始档案: "
                    + (file == null ? "?" : file.path()) + " / " + ex.getMessage());
            return Profile.fresh();
        }
    }

    public boolean save(Profile profile) {
        if (file == null) {
            return false;
        }
        try {
            if (file.parent() != null) {
                file.parent().mkdirs();
            }
            file.writeString(ProfileCodec.write(profile), false, "UTF-8");
            return true;
        } catch (RuntimeException ex) {
            System.err.println("[ProfileStore] 档案写入失败: " + file.path() + " / " + ex.getMessage());
            return false;
        }
    }
}
```

  （`save/MetaService.java` 完整——快照方法体依赖 CP16 的 SnapshotStore/SnapshotCodec，本 CP 先定签名与档案侧实现）：

```java
package com.voidvvv.kz_auto_chess_n.save;

import com.badlogic.gdx.files.FileHandle;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.HeroData;
import com.voidvvv.kz_auto_chess_n.entities.RunModifiers;

import java.util.Objects;
import java.util.Set;

/**
 * 档案域门面（architecture §三 方案 A）：Screen 层唯一入口。
 * 当前 Profile 整体替换式更新；规则全部委托 ProfileService/SnapshotCodec 纯函数，
 * 本类只做状态持有 + IO 编排（profile/快照句柄经构造注入——Main 装配，裁决 D14）。
 */
public final class MetaService {

    private final ProfileStore profileStore;
    private final SnapshotStore snapshotStore;
    private Profile profile = Profile.fresh();

    public MetaService(FileHandle profileFile, FileHandle snapshotFile) {
        this.profileStore = new ProfileStore(profileFile);
        this.snapshotStore = new SnapshotStore(snapshotFile);
    }

    /** 启动装载（Main.create 调一次；损坏自动重置——裁决 D20） */
    public void loadProfile() {
        this.profile = profileStore.load();
    }

    public Profile getProfile() {
        return profile;
    }

    /** 已解锁场景 id 集（ProfileService 派生——裁决 D7） */
    public Set<String> unlockedSceneIds(GameData data) {
        return ProfileService.unlockedSceneIds(profile, data);
    }

    public boolean isSceneUnlocked(String sceneId, GameData data) {
        return unlockedSceneIds(data).contains(sceneId);
    }

    /** 装配期局外修正（RunSetup 选定 heroId → BattleScreen.newContext 消费） */
    public RunModifiers resolveRunModifiers(String heroId, GameData data) {
        HeroData hero = heroId == null ? null : data.getHero(heroId);
        return ProfileService.runModifiers(hero, profile, data);
    }

    /**
     * RUN_END 结算（BattleScreen 观察触发，每局恰一次——裁决 D11）：
     * 纯结算 → 内存档案替换 → 落盘；写失败记日志不炸（裁决 D20）。
     */
    public ProfileService.Settlement settleRun(GameData data, RunContext ctx) {
        Objects.requireNonNull(ctx, "ctx 不能为 null");
        ProfileService.Settlement settlement = ProfileService.settle(profile, data,
                ctx.getRunState().getHeroId(), ctx.getRunState().getSceneId(),
                ctx.getRunState().getEndCause(), ctx.getRunState().getRound(),
                ctx.getRunState().getMasteryAwarded());
        this.profile = settlement.getNewProfile();
        profileStore.save(profile);
        return settlement;
    }

    // —— 快照轨（实现委托 CP16）——

    public boolean hasRunSnapshot() {
        return snapshotStore.exists();
    }

    /** 读快照（主菜单「继续远征」）；不存在/损坏/引用悬空 → 删档并返回 null（裁决 D20） */
    public RunSnapshot loadRunSnapshot(GameData data) {
        return snapshotStore.load(data);
    }

    public void saveRunSnapshot(RunContext ctx) {
        snapshotStore.save(SnapshotCodec.capture(ctx));
    }

    public void clearRunSnapshot() {
        snapshotStore.delete();
    }
}
```

  （`.gitignore` 修改前，:171-172 逐字）：

```
## Local playtest feedback (not tracked):
/feedback/
```

  （修改后，文件尾追加）：

```

## Runtime save files (lwjgl3 workingDir = assets/; not tracked):
/assets/save/
```
- **测试要点**：新建 `save/ProfileCodecTest`——round-trip（含多英雄/多通关场景）等价断言；空档案 `{}` → fresh 语义；版本不符/未知字段/level 越界 → DataValidationException；`save/ProfileStoreTest`（`new FileHandle(java.io.File)` 临时目录）——不存在 → fresh；写入损坏 JSON 后 load → fresh 且不炸；save→load round-trip。

### CP8. MasteryCalculator 完整口径 + RunState heroId/modifiers 扩展 + StartRun.heroId 启用

- **类型**：修改类 ×3
- **位置**：`systems/MasteryCalculator.java`（全文替换）；`entities/RunState.java:18-59`；`command/StartRunCommand.java:5`（注释）；`systems/RunFlowSystem.java:57-68`
- **改动说明**：裁决 D3——GDD_BASIC 补通关加成（现实现 MasteryCalculator.java:17-19 只有 轮×3，GDD §8.1「通关 +60」缺失）。`RunState` 增 `heroId`/`modifiers` 终态字段（与 seed/sceneId 同语义：装配期给定、局内只读）；兼容构造保存量测试零改动（heroId=null + EMPTY）。`StartRunCommand.heroId` 扩展位启用（Phase 5 Q3 预留），handler 一致性校验沿 seed/sceneId 同款（RunFlowSystem.java:62-63 口径 #11）。
- **代码**（`MasteryCalculator.java` 全文替换——修改前见 MasteryCalculator.java:1-21，其中 GDD_BASIC 实现为 `return roundsReached * 3;`）：

```java
package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.entities.RunEndCause;

/**
 * 熟练度结算纯函数接口（GDD §8.1：通关 +60 + 每已达 1 轮 +3；放弃远征同口径按已达轮数——GDD §2.1）。
 * 产出为纯模拟态（RunState.masteryAwarded）；档案入账归 ProfileService.settle（Phase 6，
 * Screen 观察触发——裁决 D11），本接口不做任何 IO。
 */
@FunctionalInterface
public interface MasteryCalculator {

    int settle(RunEndCause cause, int roundsReached);

    /** GDD 基线口径（裁决 D3）：COMPLETED = 通关加成 + 轮数×3；ABANDONED = 轮数×3 */
    MasteryCalculator GDD_BASIC = new MasteryCalculator() {
        @Override
        public int settle(RunEndCause cause, int roundsReached) {
            if (cause == RunEndCause.COMPLETED) {
                return GameBalance.MASTERY_COMPLETE_BONUS
                        + roundsReached * GameBalance.MASTERY_EXP_PER_ROUND;
            }
            return roundsReached * GameBalance.MASTERY_EXP_PER_ROUND;
        }
    };
}
```

  （`RunState.java` 修改前，:41-45 逐字）：

```java
    public RunState(long seed, String sceneId, IdIssuer idIssuer) {
        this.seed = seed;
        this.sceneId = Objects.requireNonNull(sceneId, "sceneId 不能为 null");
        this.idIssuer = Objects.requireNonNull(idIssuer, "idIssuer 不能为 null");
    }
```

  （修改后）：

```java
    /** 兼容构造（存量测试）：无英雄（heroId = null、修正 = EMPTY） */
    public RunState(long seed, String sceneId, IdIssuer idIssuer) {
        this(seed, sceneId, null, RunModifiers.EMPTY, idIssuer);
    }

    /** canonical（Phase 6）：heroId + 局外修正随上下文装配冻结（同 seed/sceneId 语义） */
    public RunState(long seed, String sceneId, String heroId, RunModifiers modifiers, IdIssuer idIssuer) {
        this.seed = seed;
        this.sceneId = Objects.requireNonNull(sceneId, "sceneId 不能为 null");
        this.heroId = heroId;
        this.modifiers = Objects.requireNonNull(modifiers, "modifiers 不能为 null");
        this.idIssuer = Objects.requireNonNull(idIssuer, "idIssuer 不能为 null");
    }
```

  （字段区 `:21` `idIssuer` 声明后追加）：

```java
    /** 本局英雄 id（StartRun 参数；null = 无英雄防御路径） */
    private final String heroId;
    /** 局外修正聚合（ProfileService 产出，装配期冻结） */
    private final RunModifiers modifiers;
```

  （getter 区 `:49` `getIdIssuer` 后追加；写方法区追加 setRound）：

```java
    public String getHeroId() { return heroId; }
    public RunModifiers getModifiers() { return modifiers; }
```

```java
    /** 轮次复原（快照轨恢复唯一调用点；仅装配期，运行期推进走 advanceRound） */
    public void setRound(int round) {
        if (round < 1 || round > GameBalance.TOTAL_ROUNDS) {
            throw new IllegalArgumentException("轮次必须在 1~" + GameBalance.TOTAL_ROUNDS + "，实际=" + round);
        }
        this.round = round;
    }
```

  （`RunState.java` 头部 import 补 `GameBalance`——注意 `entities.RunState` 引 `config.GameBalance` 分层合法（Player.java:3 先例）。）

  （`StartRunCommand.java:5` 注释修改前逐字）：

```java
/** 开局域边界事件（architecture §一：回放流第 0 条记录；Q3 裁决：seed 由 UI 给定，heroId 留 Phase 6 扩展位恒 null） */
```

  （修改后）：

```java
/** 开局域边界事件（architecture §一：回放流第 0 条记录；seed/heroId 由 UI 给定——Phase 6 heroId 启用） */
```

  （`RunFlowSystem.java` StartRun handler 修改前，:59-64 逐字）：

```java
            RunState runState = ctx.getRunState();
            if (runState.isRunStarted() || runState.getRound() != 1
                    || runState.getPhase() != GamePhase.SHOPPING
                    || start.getSeed() != runState.getSeed()
                    || !start.getSceneId().equals(runState.getSceneId())) {
                return false; // 非新鲜上下文或装配点错位（口径 #11，静默防线）
            }
```

  （修改后）：

```java
            RunState runState = ctx.getRunState();
            if (runState.isRunStarted() || runState.getRound() != 1
                    || runState.getPhase() != GamePhase.SHOPPING
                    || start.getSeed() != runState.getSeed()
                    || !start.getSceneId().equals(runState.getSceneId())
                    || !Objects.equals(start.getHeroId(), runState.getHeroId())) {
                return false; // 非新鲜上下文或装配点错位（口径 #11，静默防线；heroId 同款校验 Phase 6）
            }
```
- **测试要点**：`systems/RunFlowSystemTest`——**:634-635 断言改写**：修改前 `.isEqualTo(GameBalance.TOTAL_ROUNDS * 3)` → 修改后 `.isEqualTo(GameBalance.MASTERY_COMPLETE_BONUS + GameBalance.TOTAL_ROUNDS * 3)`（135）；:653 放弃 r1=3 不变；新增「StartRun heroId 与上下文不一致 → false」；`entities/RunStateTest` 增 heroId/modifiers 透传 + setRound 越界抛错 + 兼容构造 heroId null/modifiers EMPTY。

### CP9. ShopSystem 英雄加成接入（稀有概率 / 池门控 / 刷新折扣 / 槽位复原）+ ShopBar 动态价签

- **类型**：修改类 ×2
- **位置**：`systems/ShopSystem.java:46-54,81-94,202-211`；`render/ui/ShopBar.java:54-61,77-82,159-172`
- **改动说明**：消费 `RunState.getModifiers()`（CP8）——Lv.2 费阶权重 +5pp（裁决 D5：仅基础 3 费概率>0 轮次、自 1 费扣减，**RNG 消耗点数与序不变**，architecture §六口径保持）；池内抽取按 `isShopAllowed` 门控（裁决 D8）；Lv.5 刷新折扣（实付下限 1 金）。`restoreSlots` 为快照轨唯一槽位写入口（CP16 消费）。ShopBar 刷新钮价签改动态（折扣后实付价）。
- **代码**（ShopSystem RefreshShop handler 修改前，:46-54 逐字）：

```java
        manager.registerHandler(RefreshShopCommand.class, (cmd, ctx) -> {
            if (ctx.getRunState().getPhase() != GamePhase.SHOPPING
                    || !ctx.getPlayer().canAfford(GameBalance.SHOP_REFRESH_COST)) {
                return false;
            }
            ctx.getPlayer().addGold(-GameBalance.SHOP_REFRESH_COST);
            reroll(ctx.getRunState().getRound(), ctx.getGameData(), ctx.getRng());
            return true;
        });
```

  （修改后）：

```java
        manager.registerHandler(RefreshShopCommand.class, (cmd, ctx) -> {
            int cost = refreshCost(ctx.getRunState().getModifiers());
            if (ctx.getRunState().getPhase() != GamePhase.SHOPPING
                    || !ctx.getPlayer().canAfford(cost)) {
                return false;
            }
            ctx.getPlayer().addGold(-cost);
            reroll(ctx.getRunState().getRound(), ctx.getGameData(), ctx.getRng(),
                    ctx.getRunState().getModifiers());
            return true;
        });
```

  （reroll 修改前，:81-94 逐字）：

```java
    /** 整批重掷 5 槽：每槽 = 费阶 roll + 同费池均匀抽取（RNG 恒 2/槽，共 10） */
    public void reroll(int round, GameData data, RandomGenerator rng) {
        float[] probabilities = GameBalance.shopTierProbabilities(round);
        int[] tierWeights = {
                Math.round(probabilities[0] * GameBalance.PROBABILITY_WEIGHT_SCALE),
                Math.round(probabilities[1] * GameBalance.PROBABILITY_WEIGHT_SCALE),
                Math.round(probabilities[2] * GameBalance.PROBABILITY_WEIGHT_SCALE)};
        for (int i = 0; i < slots.length; i++) {
            int tier = rng.weightedPick(tierWeights);                          // RNG #1（费阶 0/1/2 → cost 1/2/3）
            List<UnitData> pool = tierPool(data, tier + 1);
            int pick = rng.weightedPick(uniform(pool.size()));                 // RNG #2（池空也消耗）
            slots[i] = pool.isEmpty() ? null : pool.get(pick);
        }
    }
```

  （修改后）：

```java
    /** 整批重掷 5 槽（存量签名：无局外修正——测试路径） */
    public void reroll(int round, GameData data, RandomGenerator rng) {
        reroll(round, data, rng, RunModifiers.EMPTY);
    }

    /**
     * 整批重掷（带局外修正，Phase 6）：Lv.2 起 3 费概率 +5pp——仅当该轮基础 3 费概率 > 0
     * （防 1~9 轮提前出 3 费打破新手期节奏，GDD §3.4——裁决 D5），自 1 费扣减；
     * 池内抽取按 RunModifiers 商店池门控（场景 shopUnlocks + 本英雄传奇，裁决 D8）。
     * RNG 消耗序与点数不变（权重是数值调整非新掷——architecture §六）。
     */
    public void reroll(int round, GameData data, RandomGenerator rng, RunModifiers modifiers) {
        float[] probabilities = GameBalance.shopTierProbabilities(round);
        int bonusPp = Math.max(0, modifiers.getRareShopBonusPp());
        if (bonusPp > 0 && probabilities[2] > 0f) {
            probabilities[2] = Math.min(100f, probabilities[2] + bonusPp);
            probabilities[0] = Math.max(0f, probabilities[0] - bonusPp);
        }
        int[] tierWeights = {
                Math.round(probabilities[0] * GameBalance.PROBABILITY_WEIGHT_SCALE),
                Math.round(probabilities[1] * GameBalance.PROBABILITY_WEIGHT_SCALE),
                Math.round(probabilities[2] * GameBalance.PROBABILITY_WEIGHT_SCALE)};
        for (int i = 0; i < slots.length; i++) {
            int tier = rng.weightedPick(tierWeights);                          // RNG #1（费阶 0/1/2 → cost 1/2/3）
            List<UnitData> pool = allowedPool(tierPool(data, tier + 1), modifiers);
            int pick = rng.weightedPick(uniform(pool.size()));                 // RNG #2（池空也消耗）
            slots[i] = pool.isEmpty() ? null : pool.get(pick);
        }
    }

    /** 刷新实付价（GDD §3.4 基价 2 - Lv.5 折扣，下限 1 金——裁决 D4） */
    static int refreshCost(RunModifiers modifiers) {
        return Math.max(1, GameBalance.SHOP_REFRESH_COST - modifiers.getRefreshCostDiscount());
    }

    /** 商店池门控过滤（EMPTY 不门控——兼容路径全量非 Boss 池） */
    private static List<UnitData> allowedPool(List<UnitData> pool, RunModifiers modifiers) {
        if (!modifiers.isShopPoolRestricted()) {
            return pool;
        }
        List<UnitData> allowed = new ArrayList<UnitData>(pool.size());
        for (UnitData template : pool) {
            if (modifiers.isShopAllowed(template.getId())) {
                allowed.add(template);
            }
        }
        return allowed;
    }

    /** 槽位复原（快照轨恢复唯一写入口；长度必须 = SHOP_SLOTS，null 槽原样保留） */
    public void restoreSlots(List<UnitData> templates) {
        Objects.requireNonNull(templates, "templates 不能为 null");
        if (templates.size() != slots.length) {
            throw new IllegalArgumentException("商店槽位数必须 = " + slots.length + "，实际=" + templates.size());
        }
        for (int i = 0; i < slots.length; i++) {
            slots[i] = templates.get(i);
        }
    }
```

  （import 区追加 `import com.voidvvv.kz_auto_chess_n.entities.RunModifiers;` 与 `import java.util.Objects;`）

  （`ShopBar.java` 修改前，:54-61 逐字）：

```java
        Actor refresh = new ActionButton("刷新 2金") {
            @Override
            protected void onClicked() {
                commandManager.addCommand(RefreshShopCommand.INSTANCE);
            }
        };
        refresh.setPosition(BUTTON_X_REFRESH, 14f);
        addActor(refresh);
```

  （修改后——按钮持有引用，价签每帧按 modifiers 刷新）：

```java
        this.refreshButton = new ActionButton("刷新 2金") {
            @Override
            protected void onClicked() {
                commandManager.addCommand(RefreshShopCommand.INSTANCE);
            }
        };
        refreshButton.setPosition(BUTTON_X_REFRESH, 14f);
        addActor(refreshButton);
```

  （`:77-82` refresh 修改前逐字）：

```java
    /** 每帧刷新预校验态（只读；SHOPPING 期 Screen 调用） */
    public void refresh(RunContext ctx) {
        for (int i = 0; i < affordable.length; i++) {
            affordable[i] = canBuy(ctx, i);
        }
    }
```

  （修改后）：

```java
    /** 每帧刷新预校验态（只读；SHOPPING 期 Screen 调用）+ 刷新钮价签（Lv.5 折扣——裁决 D4） */
    public void refresh(RunContext ctx) {
        for (int i = 0; i < affordable.length; i++) {
            affordable[i] = canBuy(ctx, i);
        }
        int cost = Math.max(1, GameBalance.SHOP_REFRESH_COST
                - ctx.getRunState().getModifiers().getRefreshCostDiscount());
        refreshButton.setText("刷新 " + cost + "金");
    }
```

  （`:160-164` ActionButton 修改前逐字）：

```java
    private abstract class ActionButton extends Actor {
        private final String text;

        ActionButton(String text) {
            this.text = text;
```

  （修改后）：

```java
    private abstract class ActionButton extends Actor {
        private String text;

        ActionButton(String text) {
            this.text = text;
```

  （并在 ActionButton 内追加 `void setText(String text) { this.text = Objects.requireNonNull(text); }`；ShopBar 字段区追加 `private ActionButton refreshButton;`，import 补 `java.util.Objects`）
- **测试要点**：`systems/ShopSystemTest` 增——reroll 带 +5pp 修正：p3>0 轮次（r10）3 费权重 ×1000 后 +5000、1 费 -5000；p3==0 轮次（r3/r5）权重与无修正**逐位相同**；RNG 消耗断言恒 10（修正不增消耗）；门控：他人传奇/未解锁场景单位永不出现在槽位、本英雄 Lv.3 修正下传奇可出现（固定 seed 断言或直接断言 allowedPool 语义——经公开 reroll 以受控 GameData 断言槽位 ∈ 允许集）；`refreshCost`：EMPTY=2、折扣 1=2、折扣 2=1（下限）；restoreSlots 长度错抛错；`render/ui/ShopBarLogicTest` 增 refresh 后价签文案断言（ctx 带 Lv.5 修正 → "刷新 1金"）。

### CP10. SynergySystem 增幅重载 + BattleSystem 英雄被动源接入

- **类型**：修改类 ×2 + RunFlowSystem 透传一行
- **位置**：`systems/SynergySystem.java:31-67`；`systems/BattleSystem.java:63-109,241-251`；`systems/RunFlowSystem.java:73-75`
- **改动说明**：裁决 D12——薇拉「荆语」经 `resolve(templates, data, synergyAmp)` 重载：增幅作用于该羁绊当档**全部**效果，ADD 通道四舍五入取整（hp/armor 等整型语义）、PCT/effect 通道保留浮点；存量 2 参签名委托空映射（敌方侧与 SynergyPanel 预演零改动——预演天然显示增幅后数值，符合「显示实际生效档」render §九口径）。裁决 D13——奥兰多「战歌」= `RunModifiers implements StatModifierSource` 作为玩家侧派生修正源列表第 3 源（Phase 3 Q4 插点兑现），敌方侧恒不注入；`startBattle` 增 modifiers 重载（旧签名委托 EMPTY，存量测试零改动）。
- **代码**（SynergySystem 修改前，:31-32 逐字）：

```java
    public SynergySnapshot resolve(Collection<UnitData> templates, GameData data) {
        Objects.requireNonNull(templates, "templates 不能为 null");
```

  （修改后——签名改 3 参 + 新增 2 参兼容重载，方法体在 `for (SynergyData synergy ...)` 循环内的效果落地段 `:53-59` 插入增幅）：

```java
    /** 兼容重载（敌方侧 / SynergyPanel 预演 / 存量测试）：无增幅 */
    public SynergySnapshot resolve(Collection<UnitData> templates, GameData data) {
        return resolve(templates, data, Collections.<String, Float>emptyMap());
    }

    /**
     * 结算一侧阵容的羁绊快照（带增幅，Phase 6 裁决 D12）：synergyAmp = synergyId → 比例
     * （0.25 = +25%，「荆语」）。增幅作用于该羁绊当档全部效果（档位替换制语义不变）：
     * ADD 通道四舍五入取整（整型 stat 语义）、PCT 与 effect 通道保留浮点。
     */
    public SynergySnapshot resolve(Collection<UnitData> templates, GameData data,
                                   Map<String, Float> synergyAmp) {
        Objects.requireNonNull(templates, "templates 不能为 null");
```

  （循环内效果落地段修改前，:53-59 逐字）：

```java
            actives.add(new SynergySnapshot.ActiveSynergy(synergy.getId(), synergy.getName(), tier.getCount()));
            for (EffectData effect : tier.getEffects()) {
                if (effect.isStatChannel()) {
                    statModifiers = statModifiers.plus(StatModifierBlock.of(
                            effect.getStat(), effect.getOp(), effect.getValue()));
                } else {
                    openingEffects.add(effect);
                }
            }
```

  （修改后）：

```java
            actives.add(new SynergySnapshot.ActiveSynergy(synergy.getId(), synergy.getName(), tier.getCount()));
            Float amp = synergyAmp.get(synergy.getId());
            for (EffectData effect : tier.getEffects()) {
                EffectData scaled = amp == null ? effect : amplify(effect, amp);
                if (scaled.isStatChannel()) {
                    statModifiers = statModifiers.plus(StatModifierBlock.of(
                            scaled.getStat(), scaled.getOp(), scaled.getValue()));
                } else {
                    openingEffects.add(scaled);
                }
            }
```

  （文件尾新增私有方法 + import 补 `java.util.Map`）：

```java
    /** 增幅单条效果（裁决 D12）：ADD 四舍五入取整；PCT/effect 保留浮点 */
    private static EffectData amplify(EffectData effect, float amp) {
        float scaled = effect.getOp() == EffectOp.ADD
                ? Math.round(effect.getValue() * (1f + amp))
                : effect.getValue() * (1f + amp);
        return new EffectData(effect.getStat(), effect.getEffect(), effect.getOp(), scaled,
                effect.getTarget());
    }
```

  （BattleSystem 修改前，:63-64 逐字）：

```java
    public BattleState startBattle(Player player, List<WaveSpec> enemyWave,
                                   GameData data, RandomGenerator rng, IdIssuer idIssuer) {
```

  （修改后——旧签名委托 + 新 6 参重载）：

```java
    public BattleState startBattle(Player player, List<WaveSpec> enemyWave,
                                   GameData data, RandomGenerator rng, IdIssuer idIssuer) {
        return startBattle(player, enemyWave, data, rng, idIssuer, RunModifiers.EMPTY);
    }

    /**
     * 开战（带局外修正，Phase 6）：玩家侧羁绊结算吃增幅（「荆语」——CP10）；玩家侧派生
     * 修正源列表追加 RunModifiers（「战歌」全队回能 ADD 百分点，走 DamagePipeline 现成
     * 管线——裁决 D13）；敌方侧恒不注入。
     */
    public BattleState startBattle(Player player, List<WaveSpec> enemyWave,
                                   GameData data, RandomGenerator rng, IdIssuer idIssuer,
                                   RunModifiers modifiers) {
```

  （方法体内 `:72` 修改前逐字）：

```java
        SynergySnapshot playerSynergies = synergySystem.resolve(templatesOfDeployed(player), data);
```

  （修改后）：

```java
        SynergySnapshot playerSynergies = synergySystem.resolve(templatesOfDeployed(player), data,
                modifiers.getSynergyAmp());
```

  （玩家侧派生调用 `:88-89` 修改前逐字）：

```java
                units.add(deriveUnit(idIssuer.nextId(), unit.getTemplate(), unit.getStar(),
                        Side.PLAYER, 1.0f, playerSynergies, data, x, y, unit.getEquipped()));
```

  （修改后）：

```java
                units.add(deriveUnit(idIssuer.nextId(), unit.getTemplate(), unit.getStar(),
                        Side.PLAYER, 1.0f, playerSynergies, data, x, y, unit.getEquipped(),
                        modifiers.shopPoolRestricted() ? modifiers : null));
```

  > 注：`RunModifiers` 未公开 `shopPoolRestricted()`——统一改为恒传 `modifiers`（EMPTY 的 `modifiers()` 为空块，注入无害且省分支）。落定代码：

```java
                units.add(deriveUnit(idIssuer.nextId(), unit.getTemplate(), unit.getStar(),
                        Side.PLAYER, 1.0f, playerSynergies, data, x, y, unit.getEquipped(),
                        modifiers));
```

  （敌方侧派生调用 `:95-97` 修改前逐字）：

```java
            units.add(deriveUnit(idIssuer.nextId(), spec.getTemplate(), spec.getStar(),
                    Side.ENEMY, spec.getScale(), enemySynergies, data, spec.getGridX(), spec.getGridY(),
                    Collections.<Equipment>emptyList()));
```

  （修改后——末参 null）：

```java
            units.add(deriveUnit(idIssuer.nextId(), spec.getTemplate(), spec.getStar(),
                    Side.ENEMY, spec.getScale(), enemySynergies, data, spec.getGridX(), spec.getGridY(),
                    Collections.<Equipment>emptyList(), null));
```

  （deriveUnit 修改前，:241-246 逐字）：

```java
    private static BattleUnit deriveUnit(int id, UnitData template, int star, Side side, float scale,
                                         SynergySnapshot synergies, GameData data, int x, int y,
                                         List<Equipment> equipped) {
        List<StatModifierSource> sources = Arrays.asList(
                synergies, EquipmentStats.of(equipped)); // Q4 修正源列表：羁绊（侧全体）+ 装备（单体）
        BattleStats baseStats = StatPipeline.deriveBaseline(template, star, scale, sources);
```

  （修改后）：

```java
    private static BattleUnit deriveUnit(int id, UnitData template, int star, Side side, float scale,
                                         SynergySnapshot synergies, GameData data, int x, int y,
                                         List<Equipment> equipped, RunModifiers modifiers) {
        List<StatModifierSource> sources = new ArrayList<StatModifierSource>(3);
        sources.add(synergies);                     // 羁绊（侧全体）
        sources.add(EquipmentStats.of(equipped));   // 装备（单体）
        if (modifiers != null) {
            sources.add(modifiers);                 // 局外修正（全队回能——Phase 6，仅玩家侧注入）
        }
        BattleStats baseStats = StatPipeline.deriveBaseline(template, star, scale, sources);
```

  （import 补 `entities.RunModifiers`；`Arrays` import 若无他用可移除）

  （RunFlowSystem `:73-75` 修改前逐字）：

```java
            BattleState state = battleSystem.startBattle(ctx.getPlayer(), runState.getEnemyWave(),
                    ctx.getGameData(), ctx.getRng(), runState.getIdIssuer()); // 零棋子允许开战
```

  （修改后）：

```java
            BattleState state = battleSystem.startBattle(ctx.getPlayer(), runState.getEnemyWave(),
                    ctx.getGameData(), ctx.getRng(), runState.getIdIssuer(),
                    runState.getModifiers()); // 零棋子允许开战；局外修正透传（Phase 6）
```
- **测试要点**：`systems/SynergySystemTest` 增——2 参重载与旧实现输出全等（回归锚）；增幅 map 作用：syn_beast (2) attackSpeed PCT 15 → 18.75、syn_orc (2) hp ADD 150 → 188（四舍五入）、兽人 (6) SHIELD 0.3 → 0.375；未列入 map 的羁绊零影响；`systems/BattleSystemTest` 增——奥兰多修正（energyPp 15）：玩家侧派生单位 `getEffective(StatKey.ENERGY_GAIN_RATE)` 基准 115（法师(4) 羁绊叠加时 130）、敌方侧恒 100；薇拉修正：双野兽阵容 attackSpeed 生效值 = 无修正 × 1.25；EMPTY 重载与旧 5 参输出全等（回归锚）。

### CP11. 内容种子批次（scenes ×3 / units +12 / synergies +2，工作值待调）

- **类型**：修改数据文件 ×3
- **位置**：`assets/data/scenes.json`（全文重写）、`assets/data/units.json`（追加 12 条）、`assets/data/synergies.json`（追加 2 条）
- **改动说明**：裁决 D15/D16/D19——最小可验收内容集：三场景全链（解锁链/敌池/Boss/商店门控）、每场景 3 门控单位、6 新 Boss（数值按 data_schema §4.2 烘焙口径：普通 Boss ×2.5HP/×2.0攻、最终 ×3.0/×2.5）、亡灵/巨人羁绊（工作值）、3 英雄专属传奇。**全部数值标注工作值待调**；新 Boss 技能暂复用既有 11 技能（具名化 Phase 7——裁决 D15；星骸守卫按 data_schema §五示例用 skill_starfall）。units 条目字段与 §4.1 终版一致，技能全部引用既有 skillId。
- **代码**（`scenes.json` 全文）：

```json
[
  {
    "id": "scene_forest", "name": "翡翠林地", "unlockAfter": null,
    "enemyPool": [
      { "unitId": "unit_warrior_01",  "weight": 3, "minRound": 1 },
      { "unitId": "unit_ranger_01",   "weight": 2, "minRound": 2 },
      { "unitId": "unit_assassin_01", "weight": 2, "minRound": 5 }
    ],
    "bosses": { "7": "boss_thorn_mother", "15": "boss_one_eye", "25": "boss_thorn_true" },
    "shopUnlocks": []
  },
  {
    "id": "scene_crypt", "name": "亡者墓穴", "unlockAfter": "scene_forest",
    "enemyPool": [
      { "unitId": "unit_skeleton_soldier", "weight": 3, "minRound": 1 },
      { "unitId": "unit_wraith",           "weight": 2, "minRound": 4 },
      { "unitId": "unit_death_knight",     "weight": 1, "minRound": 10 }
    ],
    "bosses": { "7": "boss_tomb_colossus", "15": "boss_twin_lich", "25": "boss_bone_duke" },
    "shopUnlocks": ["unit_skeleton_soldier", "unit_wraith", "unit_death_knight"]
  },
  {
    "id": "scene_snow", "name": "寒峰雪山", "unlockAfter": "scene_crypt",
    "enemyPool": [
      { "unitId": "unit_frost_imp",      "weight": 3, "minRound": 1 },
      { "unitId": "unit_frost_giant",    "weight": 2, "minRound": 6 },
      { "unitId": "unit_glacial_giant",  "weight": 1, "minRound": 12 }
    ],
    "bosses": { "7": "boss_frost_howler", "15": "boss_star_breaker", "25": "boss_star_warden" },
    "shopUnlocks": ["unit_frost_imp", "unit_frost_giant", "unit_glacial_giant"]
  }
]
```

  （`units.json` 追加条目——墓穴 3 + 雪山 3 + 英雄传奇 3 + Boss 6）：

```json
  { "id": "unit_skeleton_soldier", "name": "骸骨士兵", "race": "亡灵", "class": "战士", "cost": 1,
    "baseStats": { "hp": 90, "attack": 14, "armor": 8, "attackSpeed": 1.0, "range": 1, "moveSpeed": 1.0 },
    "upgradeMultiplier": 1.8, "skillId": "skill_warcry" },

  { "id": "unit_wraith", "name": "怨灵", "race": "亡灵", "class": "法师", "cost": 2,
    "baseStats": { "hp": 75, "attack": 16, "armor": 2, "attackSpeed": 0.9, "range": 3, "moveSpeed": 0.9 },
    "upgradeMultiplier": 1.8, "skillId": "skill_poison_cloud" },

  { "id": "unit_death_knight", "name": "死亡骑士", "race": "亡灵", "class": "战士", "cost": 3,
    "baseStats": { "hp": 130, "attack": 24, "armor": 14, "attackSpeed": 0.9, "range": 1, "moveSpeed": 1.0 },
    "upgradeMultiplier": 1.8, "skillId": "skill_rampage" },

  { "id": "unit_frost_imp", "name": "小雪怪", "race": "巨人", "class": "刺客", "cost": 1,
    "baseStats": { "hp": 85, "attack": 15, "armor": 5, "attackSpeed": 1.2, "range": 1, "moveSpeed": 1.4 },
    "upgradeMultiplier": 1.8, "skillId": "skill_execute" },

  { "id": "unit_frost_giant", "name": "霜巨人", "race": "巨人", "class": "战士", "cost": 2,
    "baseStats": { "hp": 150, "attack": 18, "armor": 16, "attackSpeed": 0.7, "range": 1, "moveSpeed": 0.8 },
    "upgradeMultiplier": 1.8, "skillId": "skill_warcry" },

  { "id": "unit_glacial_giant", "name": "冰霜巨人", "race": "巨人", "class": "战士", "cost": 3,
    "baseStats": { "hp": 170, "attack": 26, "armor": 18, "attackSpeed": 0.7, "range": 1, "moveSpeed": 0.8 },
    "upgradeMultiplier": 1.8, "skillId": "skill_rampage" },

  { "id": "unit_legend_quartermaster", "name": "王家军需官", "race": "人类", "class": "战士", "cost": 3,
    "baseStats": { "hp": 120, "attack": 20, "armor": 12, "attackSpeed": 1.0, "range": 1, "moveSpeed": 1.0 },
    "upgradeMultiplier": 1.8, "skillId": "skill_warcry" },

  { "id": "unit_legend_thornhart", "name": "荆棘圣鹿", "race": "野兽", "class": "游侠", "cost": 3,
    "baseStats": { "hp": 110, "attack": 22, "armor": 6, "attackSpeed": 1.1, "range": 3, "moveSpeed": 1.0 },
    "upgradeMultiplier": 1.8, "skillId": "skill_pierce" },

  { "id": "unit_legend_warsong_singer", "name": "银弦歌者", "race": "精灵", "class": "法师", "cost": 3,
    "baseStats": { "hp": 95, "attack": 15, "armor": 5, "attackSpeed": 0.9, "range": 3, "moveSpeed": 0.9 },
    "upgradeMultiplier": 1.8, "skillId": "skill_mass_heal" },

  { "id": "boss_tomb_colossus", "name": "守陵巨像", "race": "构造", "class": "Boss", "cost": 0,
    "baseStats": { "hp": 1250, "attack": 42, "armor": 30, "attackSpeed": 0.7, "range": 1, "moveSpeed": 0.6 },
    "upgradeMultiplier": 1.0, "skillId": "skill_thorn_vine", "boss": true },

  { "id": "boss_twin_lich", "name": "双生巫妖", "race": "亡灵", "class": "Boss", "cost": 0,
    "baseStats": { "hp": 1250, "attack": 42, "armor": 12, "attackSpeed": 1.0, "range": 3, "moveSpeed": 0.8 },
    "upgradeMultiplier": 1.0, "skillId": "skill_poison_cloud", "boss": true },

  { "id": "boss_bone_duke", "name": "白骨大公", "race": "亡灵", "class": "Boss", "cost": 0,
    "baseStats": { "hp": 1500, "attack": 52, "armor": 22, "attackSpeed": 0.9, "range": 1, "moveSpeed": 0.7 },
    "upgradeMultiplier": 1.0, "skillId": "skill_starfall", "boss": true },

  { "id": "boss_frost_howler", "name": "霜啸雪怪", "race": "巨人", "class": "Boss", "cost": 0,
    "baseStats": { "hp": 1250, "attack": 40, "armor": 24, "attackSpeed": 0.8, "range": 1, "moveSpeed": 0.7 },
    "upgradeMultiplier": 1.0, "skillId": "skill_thorn_vine", "boss": true },

  { "id": "boss_star_breaker", "name": "断星者·格罗姆", "race": "巨人", "class": "Boss", "cost": 0,
    "baseStats": { "hp": 1400, "attack": 50, "armor": 18, "attackSpeed": 0.9, "range": 1, "moveSpeed": 0.8 },
    "upgradeMultiplier": 1.0, "skillId": "skill_pierce_sky", "boss": true },

  { "id": "boss_star_warden", "name": "星骸守卫", "race": "巨人", "class": "Boss", "cost": 0,
    "baseStats": { "hp": 1500, "attack": 52, "armor": 28, "attackSpeed": 0.8, "range": 1, "moveSpeed": 0.7 },
    "upgradeMultiplier": 1.0, "skillId": "skill_starfall", "boss": true }
```

  （`synergies.json` 追加条目——裁决 D16 工作值）：

```json
  { "id": "syn_undead", "name": "亡灵", "desc": "不接受死亡、也不接受停战的行尸军团",
    "source": "RACE", "key": "亡灵",
    "thresholds": [
      { "count": 2, "effects": [ { "stat": "lifesteal", "op": "ADD", "value": 10 } ] },
      { "count": 4, "effects": [ { "stat": "hp", "op": "ADD", "value": 300 },
                                  { "stat": "lifesteal", "op": "ADD", "value": 20 } ] },
      { "count": 6, "effects": [ { "stat": "hp", "op": "ADD", "value": 500 },
                                  { "stat": "attack", "op": "PCT", "value": 25 },
                                  { "stat": "lifesteal", "op": "ADD", "value": 30 } ] }
    ] },
  { "id": "syn_giant", "name": "巨人", "desc": "寒峰古族的血与岩，越厚重越难撼动",
    "source": "RACE", "key": "巨人",
    "thresholds": [
      { "count": 2, "effects": [ { "stat": "hp", "op": "ADD", "value": 200 } ] },
      { "count": 4, "effects": [ { "stat": "hp", "op": "ADD", "value": 450 },
                                  { "stat": "armor", "op": "ADD", "value": 30 } ] },
      { "count": 6, "effects": [ { "stat": "hp", "op": "ADD", "value": 800 },
                                  { "stat": "armor", "op": "ADD", "value": 60 },
                                  { "stat": "attack", "op": "PCT", "value": 20 } ] }
    ] }
```

  （风味种族说明：亡灵/巨人入羁绊登记后即告别风味标签（JsonLoader 风味校验自动消解）；「人类/构造/独眼」维持风味。）
- **测试要点**：`config/JsonLoaderTest`（真实资产）改断言——units hasSize(9+12=21 非含 Boss 计 15 可购 + 9 Boss? 以实际计数断言：非 Boss 可购 = 9 存量 + 9 新增（6 场景 + 3 传奇）= 18，Boss = 3 存量 + 6 新 = 9，总计 27）；scenes hasSize(3) 且解锁链 forest→crypt→snow；synergies hasSize(8)；heroes hasSize(3)；**加载零软告警不回归**（亡灵/巨人登记后风味集合缩减）；shopUnlocks 与传奇互斥断言（当前数据合法加载即证明）。`systems/WaveGeneratorTest` 增墓穴/雪山场景生成（minRound 门控生效）。

### CP12. screens/RunSetupScreen（英雄选择 + 场景选择两步式合一）

- **类型**：新建文件
- **位置**：`core/src/main/java/com/voidvvv/kz_auto_chess_n/screens/RunSetupScreen.java`
- **改动说明**：architecture §七既定 Screen（Phase 5 Q3 推迟项销账）。自绘 Actor（沿 MainMenuScreen/PauseMenuDialog 先例，无 Skin——Q4 遗留口径）；英雄卡显示熟练度（MetaService 档案），场景卡按解锁态灰置 + 前置名提示；「开始远征」= 域边界事件结算 `StartRun(seed, sceneId, heroId)` 参数（seed 本期仍 `System.nanoTime()`，裁决 D9 不做手输）。
- **代码**（完整）：

```java
package com.voidvvv.kz_auto_chess_n.screens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.HeroData;
import com.voidvvv.kz_auto_chess_n.data.SceneData;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;
import com.voidvvv.kz_auto_chess_n.save.MetaService;
import com.voidvvv.kz_auto_chess_n.save.ProfileService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 远征准备屏（architecture §七 RunSetupScreen：英雄选择 + 场景选择两步式合一）。
 * 纯 UI 态——高亮/选中在本屏；「开始远征」为域边界事件，结算 seed/sceneId/heroId
 * 三参传 BattleScreen（StartRun 命令在 BattleScreen.show 入队 = 回放第 0 条记录）。
 * 解锁判定/熟练度查询走 MetaService（档案语义调用，本屏零规则逻辑）。
 */
public final class RunSetupScreen implements Screen {
    private static final float CARD_W = 186f;
    private static final float CARD_H = 84f;
    private static final float CARD_GAP = 16f;
    private static final float HERO_Y = 232f;
    private static final float SCENE_Y = 116f;
    private static final float SCENE_H = 56f;

    private final Game game;
    private final Assets assets;
    private final GameData data;
    private final MetaService metaService;
    private final Stage stage;

    private final List<HeroData> heroes = new ArrayList<HeroData>();
    private final List<SceneData> scenes = new ArrayList<SceneData>();
    private final List<String> unlockedSceneIds;
    private int selectedHero = -1;
    private int selectedScene = -1;

    public RunSetupScreen(Game game, Assets assets, GameData data, MetaService metaService) {
        this.game = game;
        this.assets = assets;
        this.data = data;
        this.metaService = metaService;
        this.stage = new Stage(new FitViewport(BoardGeometry.VIRTUAL_W, BoardGeometry.VIRTUAL_H));
        heroes.addAll(data.getHeroes().values());
        scenes.addAll(data.getScenes().values());
        this.unlockedSceneIds = new ArrayList<String>(metaService.unlockedSceneIds(data));
        buildUi();
    }

    private void buildUi() {
        for (int i = 0; i < heroes.size(); i++) {
            stage.addActor(new HeroCard(i));
        }
        for (int i = 0; i < scenes.size(); i++) {
            stage.addActor(new SceneCard(i));
        }
        Actor start = new TextButton("开始远征", new Runnable() {
            @Override
            public void run() {
                startRun();
            }
        });
        start.setSize(160f, 36f);
        start.setPosition((BoardGeometry.VIRTUAL_W - 160f) / 2f, 44f);
        stage.addActor(start);
        Actor back = new TextButton("返回", new Runnable() {
            @Override
            public void run() {
                game.setScreen(new MainMenuScreen(game, assets, data, metaService));
            }
        });
        back.setSize(96f, 28f);
        back.setPosition((BoardGeometry.VIRTUAL_W - 96f) / 2f, 8f);
        stage.addActor(back);
    }

    /** 域边界事件：结算 StartRun 参数（hero/scene 必选，seed 由 UI 给定——Q3 裁决口径） */
    private void startRun() {
        if (selectedHero < 0 || selectedScene < 0) {
            return; // 未选齐：忽略（UI 已用提示文案引导）
        }
        long runSeed = System.nanoTime();
        game.setScreen(new BattleScreen(game, assets, data, metaService,
                runSeed, scenes.get(selectedScene).getId(), heroes.get(selectedHero).getId()));
    }

    private float cardX(int index) {
        float total = scenes.size() * CARD_W + (scenes.size() - 1) * CARD_GAP;
        return (BoardGeometry.VIRTUAL_W - total) / 2f + index * (CARD_W + CARD_GAP);
    }

    private boolean isSceneUnlocked(int index) {
        return unlockedSceneIds.contains(scenes.get(index).getId());
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.05f, 0.04f, 0.08f, 1f);
        stage.act(delta);
        stage.draw();
        // 标题与提示绘制在 stage 后（同帧直接画在 batch 上——沿 LoadingScreen 先例）
        stage.getBatch().begin();
        stage.getBatch().end();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        stage.dispose();
    }

    /** 英雄卡：名 + 被动一行 + 熟练度 Lv/经验 + 选中高亮 */
    private final class HeroCard extends Actor {
        private final int index;

        HeroCard(int index) {
            this.index = index;
            setSize(CARD_W, CARD_H);
            setPosition(cardX(index), HERO_Y);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    selectedHero = HeroCard.this.index;
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            HeroData hero = heroes.get(index);
            boolean selected = selectedHero == index;
            Color old = batch.getColor();
            batch.setColor(selected ? 0.4f : 0.24f, selected ? 0.46f : 0.3f,
                    selected ? 0.4f : 0.36f, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            int level = ProfileService.masteryLevel(metaService.getProfile(), hero.getId());
            assets.font().draw(batch, hero.getName(), getX() + 10f, getY() + 66f);
            assets.font().draw(batch, passiveText(hero), getX() + 10f, getY() + 48f);
            assets.font().draw(batch, masteryText(level), getX() + 10f, getY() + 30f);
            assets.font().draw(batch, selected ? "（已选）" : "点击选择", getX() + 10f, getY() + 12f);
        }
    }

    /** 场景卡：名 + 解锁态（未解锁灰置 + 前置名提示） */
    private final class SceneCard extends Actor {
        private final int index;

        SceneCard(int index) {
            this.index = index;
            setSize(CARD_W, SCENE_H);
            setPosition(cardX(index), SCENE_Y);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (isSceneUnlocked(SceneCard.this.index)) {
                        selectedScene = SceneCard.this.index;
                    }
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            SceneData scene = scenes.get(index);
            boolean unlocked = isSceneUnlocked(index);
            boolean selected = selectedScene == index;
            Color old = batch.getColor();
            if (unlocked) {
                batch.setColor(selected ? 0.4f : 0.24f, selected ? 0.46f : 0.3f,
                        selected ? 0.4f : 0.36f, parentAlpha);
            } else {
                batch.setColor(0.16f, 0.16f, 0.18f, parentAlpha);
            }
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            assets.font().draw(batch, scene.getName(), getX() + 10f, getY() + 38f);
            if (unlocked) {
                assets.font().draw(batch, selected ? "（已选）" : "点击选择", getX() + 10f, getY() + 18f);
            } else {
                SceneData prerequisite = data.getScene(scene.getUnlockAfter());
                String gate = prerequisite == null ? scene.getUnlockAfter() : prerequisite.getName();
                assets.font().draw(batch, "通关「" + gate + "」解锁", getX() + 10f, getY() + 18f);
            }
        }
    }

    /** 通用文字按钮（自绘壳，沿 PauseMenuDialog.MenuButton 形制独立实现避免跨包复用） */
    private final class TextButton extends Actor {
        private final Runnable action;
        private final String text;

        TextButton(String text, Runnable action) {
            this.text = text;
            this.action = action;
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    TextButton.this.action.run();
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Color old = batch.getColor();
            batch.setColor(0.75f, 0.35f, 0.25f, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            assets.font().draw(batch, text, getX() + getWidth() / 2f - text.length() * 6f, getY() + 23f);
        }
    }

    /** 被动一行文案（HeroData → 展示文本；中文文案、英文标识符） */
    static String passiveText(HeroData hero) {
        switch (hero.getPassiveType()) {
            case START_GOLD:
                return "被动：开局金币 +" + Math.round(hero.getPassiveValue());
            case SYNERGY_AMP:
                return "被动：" + hero.getPassiveSynergyIds().size() + " 系羁绊效果 +"
                        + Math.round(hero.getPassiveValue()) + "%";
            case ENERGY_GAIN:
                return "被动：全队能量获取 +" + Math.round(hero.getPassiveValue()) + "%";
            default:
                return "被动：？";
        }
    }

    /** 熟练度一行文案 */
    static String masteryText(int level) {
        if (level >= GameBalance.MASTERY_MAX_LEVEL) {
            return "熟练度 Lv." + level + "（满级）";
        }
        return "熟练度 Lv." + level + "（升级解锁加成）";
    }
}
```

  > 布局常量（CARD_W/HERO_Y 等）为工作值，执行期以 640×360 实机微调 ±4px（沿 render §九容差惯例）；标题「远征准备」与「选择英雄与场景」提示行绘制在 `render` 中 stage 前（执行期落位，非本 CP 关键逻辑）。
- **测试要点**：本屏为 Gdx 绑定 UI（沿 MainMenuScreen 无测试先例）；逻辑断言抽到纯函数——`passiveText/masteryText` 为包级静态，可新建 `screens/RunSetupScreenTextTest` 断言三类被动文案与满级文案；解锁灰置语义由 `ProfileService.unlockedSceneIds` 单测覆盖（CP6）；「未选齐点开始无跳转」入手验清单（§10）。

### CP13. MainMenuScreen 扩展 + Main/LoadingScreen 装配 MetaService

- **类型**：修改类 ×3
- **位置**：`screens/MainMenuScreen.java`（全文重写）；`Main.java:21-30`；`screens/LoadingScreen.java:27-33,50-52`
- **改动说明**：主菜单按 architecture §七（继续/新远征/图鉴）落 MVP 三按钮：「开始远征」→ RunSetupScreen（Q3 销账）、「继续远征」→ 快照续玩（仅 `hasRunSnapshot()` 时可见——CP16/CP17 配套）、「图鉴」→ CodexScreen（CP15）。`Main.create` 装配 `MetaService`（profile.json + run_snapshot.json 两句柄，裁决 D14 路径 `save/`）并透传。
- **代码**（`Main.java` 修改前，:21-30 逐字）：

```java
    @Override
    public void create() {
        GameData data = JsonLoader.loadFromDirectory(Gdx.files.local("data/"));
        for (String warning : data.getWarnings()) {
            Gdx.app.log("Main", "[软告警] " + warning);
        }
        PlaceholderArt art = new PlaceholderArt(data); // GL 线程一次性生成
        this.assets = new Assets(art);
        setScreen(new LoadingScreen(this, assets, data));
    }
```

  （修改后）：

```java
    @Override
    public void create() {
        GameData data = JsonLoader.loadFromDirectory(Gdx.files.local("data/"));
        for (String warning : data.getWarnings()) {
            Gdx.app.log("Main", "[软告警] " + warning);
        }
        // 档案域门面（Phase 6，裁决 D14：save/ 目录随首次写入创建）
        MetaService metaService = new MetaService(
                Gdx.files.local("save/profile.json"), Gdx.files.local("save/run_snapshot.json"));
        metaService.loadProfile();
        PlaceholderArt art = new PlaceholderArt(data); // GL 线程一次性生成
        this.assets = new Assets(art);
        setScreen(new LoadingScreen(this, assets, data, metaService));
    }
```

  （`LoadingScreen.java` 修改前，:27-33 逐字）：

```java
    public LoadingScreen(Game game, Assets assets, GameData data) {
        this.game = game;
        this.assets = assets;
        this.data = data;
```

  （修改后）：

```java
    public LoadingScreen(Game game, Assets assets, GameData data, MetaService metaService) {
        this.game = game;
        this.assets = assets;
        this.data = data;
        this.metaService = metaService;
```

  （字段区追加 `private final MetaService metaService;`；`:51` 修改前逐字 `game.setScreen(new MainMenuScreen(game, assets, data));` → 修改后 `game.setScreen(new MainMenuScreen(game, assets, data, metaService));`；import 补 `save/MetaService`）

  （`MainMenuScreen.java` 全文重写——修改前为 97 行极简版（MainMenuScreen.java:1-96，START 按钮 System.nanoTime 直连 BattleScreen），修改后）：

```java
package com.voidvvv.kz_auto_chess_n.screens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;
import com.voidvvv.kz_auto_chess_n.save.MetaService;
import com.voidvvv.kz_auto_chess_n.save.RunSnapshot;

/**
 * 主菜单（architecture §七：开始远征/继续远征/图鉴）。快照存在性决定「继续远征」可见性；
 * 续玩 = loadRunSnapshot → BattleScreen 快照构造（跳过 StartRun——CP16/CP17 配套）。
 * 自绘 Actor（无 Skin 资产，Q4 遗留口径）。
 */
public final class MainMenuScreen implements Screen {
    private final Game game;
    private final Assets assets;
    private final GameData data;
    private final MetaService metaService;
    private final Stage stage;

    public MainMenuScreen(Game game, Assets assets, GameData data, MetaService metaService) {
        this.game = game;
        this.assets = assets;
        this.data = data;
        this.metaService = metaService;
        this.stage = new Stage(new FitViewport(BoardGeometry.VIRTUAL_W, BoardGeometry.VIRTUAL_H));
        addMenuButton("开始远征", 190f, new Runnable() {
            @Override
            public void run() {
                game.setScreen(new RunSetupScreen(game, assets, data, metaService));
            }
        });
        if (metaService.hasRunSnapshot()) {
            addMenuButton("继续远征", 146f, new Runnable() {
                @Override
                public void run() {
                    RunSnapshot snapshot = metaService.loadRunSnapshot(data);
                    if (snapshot != null) {
                        game.setScreen(new BattleScreen(game, assets, data, metaService, snapshot));
                    }
                }
            });
        }
        addMenuButton("图鉴", 102f, new Runnable() {
            @Override
            public void run() {
                game.setScreen(new CodexScreen(game, assets, data, metaService));
            }
        });
        Gdx.input.setInputProcessor(stage);
    }

    private void addMenuButton(String text, float y, final Runnable action) {
        Actor button = new MenuButton(text) {
            @Override
            protected void onClicked() {
                action.run();
            }
        };
        button.setPosition((BoardGeometry.VIRTUAL_W - 160f) / 2f, y);
        stage.addActor(button);
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.05f, 0.04f, 0.08f, 1f);
        stage.act(delta);
        stage.getBatch().begin();
        stage.getBatch().end();
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void hide() {
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {
        stage.dispose();
    }

    /** 自绘菜单按钮（标题「余烬军团」绘制在 render 内 stage 前——执行期落位） */
    private abstract class MenuButton extends Actor {
        private final String text;

        MenuButton(String text) {
            this.text = text;
            setSize(160f, 36f);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    onClicked();
                }
            });
        }

        protected abstract void onClicked();

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Color old = batch.getColor();
            batch.setColor(0.75f, 0.35f, 0.25f, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            assets.font().draw(batch, text, getX() + 160f / 2f - text.length() * 6f, getY() + 23f);
        }
    }
}
```
- **测试要点**：屏为 Gdx 绑定（无直测，沿先例）；MetaService 句柄/hasRunSnapshot 行为由 CP7/CP16 单测覆盖；手验清单覆盖三按钮流转（§10）。

### CP14. save/RunSettlementText + RunEndPanel 结算展示（RunResult 形态 MVP）

- **类型**：新建文件 ×1 + 修改类 ×1
- **位置**：`save/RunSettlementText.java`；`render/ui/RunEndPanel.java`（全文重写，79 行）
- **改动说明**：裁决 D6——RunResultScreen 独立屏推 Phase 7，本期以 RunEndPanel 扩展承载：结算行（熟练度/升级/当前经验/解锁场景）替换「熟练度 +N（Phase 6 接档案）」占位（RunEndPanel.java:54）；新增「返回主菜单」按钮（RESTART 保留 = 同英雄同场景新 seed）。文案纯函数化（沿 UnitInfoText 先例，JUnit 可测）。
- **代码**（`save/RunSettlementText.java` 完整）：

```java
package com.voidvvv.kz_auto_chess_n.save;

import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.SceneData;

import java.util.ArrayList;
import java.util.List;

/** 局末结算文案纯函数（RunEndPanel 逐行绘制；中文文案、英文标识符——沿 UnitInfoText 先例）。 */
public final class RunSettlementText {

    private RunSettlementText() {
    }

    public static List<String> lines(ProfileService.Settlement settlement, GameData data) {
        List<String> lines = new ArrayList<String>(4);
        lines.add("熟练度 +" + settlement.getExpGained());
        if (settlement.getLevelTo() > settlement.getLevelFrom()) {
            lines.add("英雄等级 Lv." + settlement.getLevelFrom() + " → Lv." + settlement.getLevelTo());
        }
        if (settlement.getExpToNextLevel() > 0) {
            lines.add("当前 Lv." + settlement.getLevelTo() + "（经验 "
                    + settlement.getExpIntoLevel() + "/" + settlement.getExpToNextLevel() + "）");
        } else {
            lines.add("英雄等级已满（Lv." + settlement.getLevelTo() + "）");
        }
        for (String sceneId : settlement.getNewlyUnlockedSceneIds()) {
            SceneData scene = data.getScene(sceneId);
            lines.add("解锁场景：" + (scene == null ? sceneId : scene.getName()));
        }
        return lines;
    }
}
```

  （`RunEndPanel.java` 全文重写——修改前见 RunEndPanel.java:1-78（核心占位行 `:54` `assets.font().draw(batch, "熟练度 +" + ctx.getRunState().getMasteryAwarded() + "（Phase 6 接档案）", 262f, 180f);`），修改后）：

```java
package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;

import java.util.List;

/**
 * 终局面板（RUN_END，Phase 6 = RunResult 形态 MVP——裁决 D6）：成因文案 + 存活轮次 +
 * 档案结算行（熟练度/升级/解锁，BattleScreen 观察注入）+ 本局 seed + RESTART + 返回主菜单。
 * 档案写入归 BattleScreen→MetaService（Screen 点火器——裁决 D11），本类只读展示。
 */
public final class RunEndPanel extends Group {

    /** 重开回调（Screen 实现：同英雄同场景新 seed 组装新鲜 RunContext 后 restart） */
    public interface RestartListener {
        void onRestart();
    }

    /** 返回主菜单回调（Screen 实现：setScreen(MainMenuScreen)） */
    public interface MenuListener {
        void onMenuRequested();
    }

    private final Assets assets;
    private final RestartListener restartListener;
    private final MenuListener menuListener;
    private final java.util.function.Supplier<RunContext> context;
    /** 结算行（RUN_END 首帧由 Screen 注入；null = 尚未结算，回退旧行） */
    private List<String> settlementLines;

    public RunEndPanel(Assets assets, RestartListener restartListener, MenuListener menuListener,
                       java.util.function.Supplier<RunContext> context) {
        this.assets = assets;
        this.restartListener = restartListener;
        this.menuListener = menuListener;
        this.context = context;
        Actor restart = new EndButton("重新开始", 120f, 300f, new Runnable() {
            @Override
            public void run() {
                restartListener.onRestart();
            }
        });
        addActor(restart);
        Actor menu = new EndButton("返回主菜单", 220f, 300f, new Runnable() {
            @Override
            public void run() {
                menuListener.onMenuRequested();
            }
        });
        addActor(menu);
    }

    /** 结算行注入（BattleScreen 观察 RUN_END 首帧调用——每局一次） */
    public void setSettlementLines(List<String> lines) {
        this.settlementLines = lines;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
        batch.setColor(0f, 0f, 0f, 0.65f * parentAlpha);
        batch.draw(assets.region(PlaceholderKeys.WHITE), 90f, 74f, 460f, 216f);
        batch.setColor(Color.WHITE);
        RunContext ctx = context.get();
        boolean abandoned = ctx.getRunState().getEndCause()
                == com.voidvvv.kz_auto_chess_n.entities.RunEndCause.ABANDONED;
        assets.font().getData().setScale(2f);
        assets.font().draw(batch, abandoned ? "远征已放弃" : "远征通关", 272f, 268f); // 4 字 ×24px 居中
        assets.font().getData().setScale(1f);
        int round = ctx.getRunState().getRound();
        assets.font().draw(batch, "抵达第 " + round + "/" + GameBalance.TOTAL_ROUNDS + " 轮", 280f, 240f);
        if (settlementLines != null) {
            float y = 218f;
            for (String line : settlementLines) {
                assets.font().draw(batch, line, 252f, y);
                y -= 20f;
            }
        } else {
            assets.font().draw(batch, "熟练度 +" + ctx.getRunState().getMasteryAwarded(), 262f, 218f);
        }
        assets.font().draw(batch, "种子 " + ctx.getRunState().getSeed(), 285f, 126f);
    }

    /** 终局双钮共用壳 */
    private final class EndButton extends Actor {
        private final String text;

        EndButton(String text, float x, float y, final Runnable action) {
            this.text = text;
            setSize(150f, 32f);
            setPosition(x, y);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    action.run();
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Color old = batch.getColor();
            batch.setColor(0.75f, 0.35f, 0.25f, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            assets.font().draw(batch, text, getX() + 75f - text.length() * 6f, getY() + 21f);
        }
    }
}
```
- **测试要点**：新建 `save/RunSettlementTextTest`——未升级/升级/满级/带新解锁四形态行数与文案断言；`RunEndPanel` 为绘制壳（沿既有无直测先例）；手验：结算行显示、双按钮流转（§10）。

### CP15. screens/CodexScreen（图鉴 MVP：英雄熟练度 + 场景解锁只读页）

- **类型**：新建文件
- **位置**：`core/src/main/java/com/voidvvv/kz_auto_chess_n/screens/CodexScreen.java`
- **改动说明**：architecture §七 CodexScreen MVP（Phase 5 Q5 销账）——只读两区：英雄块（名/被动/熟练度 Lv（经验 x/y）/专属传奇名与解锁态）与场景块（名/解锁态/前置/是否已通关）；棋子/装备图鉴页推后续（范围外清单）。文本行构造纯函数 `codexHeroLines/codexSceneLines` 包级静态可测。
- **代码**（完整）：

```java
package com.voidvvv.kz_auto_chess_n.screens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.HeroData;
import com.voidvvv.kz_auto_chess_n.data.SceneData;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;
import com.voidvvv.kz_auto_chess_n.save.HeroProgress;
import com.voidvvv.kz_auto_chess_n.save.MetaService;
import com.voidvvv.kz_auto_chess_n.save.ProfileService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 图鉴屏 MVP（architecture §七 CodexScreen，Phase 5 Q5 销账）：英雄熟练度 + 场景解锁只读。 */
public final class CodexScreen implements Screen {
    private final Game game;
    private final Assets assets;
    private final GameData data;
    private final MetaService metaService;
    private final Stage stage;
    private final List<String> lines = new ArrayList<String>();

    public CodexScreen(Game game, Assets assets, GameData data, MetaService metaService) {
        this.game = game;
        this.assets = assets;
        this.data = data;
        this.metaService = metaService;
        this.stage = new Stage(new FitViewport(BoardGeometry.VIRTUAL_W, BoardGeometry.VIRTUAL_H));
        Actor back = new BackButton();
        back.setPosition(12f, 12f);
        stage.addActor(back);
        Set<String> unlocked = metaService.unlockedSceneIds(data);
        for (HeroData hero : data.getHeroes().values()) {
            lines.addAll(codexHeroLines(hero, metaService.getProfile(), data));
        }
        for (SceneData scene : data.getScenes().values()) {
            lines.addAll(codexSceneLines(scene, unlocked, metaService.getProfile()));
        }
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.05f, 0.04f, 0.08f, 1f);
        stage.act(delta);
        stage.draw();
        stage.getBatch().begin();
        float y = BoardGeometry.VIRTUAL_H - 20f;
        for (int i = 0; i < lines.size() && y > 40f; i++) {
            assets.font().draw(stage.getBatch(), lines.get(i), 16f, y);
            y -= 18f;
        }
        stage.getBatch().end();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        stage.dispose();
    }

    private final class BackButton extends Actor {
        BackButton() {
            setSize(96f, 28f);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    game.setScreen(new MainMenuScreen(game, assets, data, metaService));
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Color old = batch.getColor();
            batch.setColor(0.75f, 0.35f, 0.25f, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            assets.font().draw(batch, "返回", getX() + 36f, getY() + 19f);
        }
    }

    /** 英雄块文案（包级静态可测） */
    static List<String> codexHeroLines(HeroData hero, com.voidvvv.kz_auto_chess_n.save.Profile profile,
                                       GameData data) {
        List<String> result = new ArrayList<String>(2);
        HeroProgress progress = profile.getHeroProgress().get(hero.getId());
        int level = progress == null ? 1 : progress.getLevel();
        int exp = progress == null ? 0 : progress.getExp();
        int need = GameBalance.masteryExpToNext(level);
        String mastery = need > 0
                ? "Lv." + level + "（经验 " + exp + "/" + need + "）"
                : "Lv." + level + "（满级）";
        result.add("【" + hero.getName() + "】" + RunSetupScreen.passiveText(hero) + " · " + mastery);
        UnitData legendary = hero.getLegendaryUnitId() == null
                ? null : data.getUnit(hero.getLegendaryUnitId());
        String legendaryText = legendary == null ? "" : " · 专属传奇：" + legendary.getName()
                + (level >= 3 ? "（已解锁）" : "（Lv.3 解锁）");
        result.add("   " + hero.getDesc() + legendaryText);
        return result;
    }

    /** 场景块文案（包级静态可测） */
    static List<String> codexSceneLines(SceneData scene, Set<String> unlocked,
                                        com.voidvvv.kz_auto_chess_n.save.Profile profile) {
        List<String> result = new ArrayList<String>(1);
        boolean isUnlocked = unlocked.contains(scene.getId());
        boolean completed = profile.getCompletedScenes().contains(scene.getId());
        String status = completed ? "已通关" : (isUnlocked ? "已解锁" : "未解锁");
        result.add("【" + scene.getName() + "】" + status);
        return result;
    }
}
```
- **测试要点**：新建 `screens/CodexScreenTextTest`——codexHeroLines：无进度（Lv.1 经验 0/50）、满级、传奇已解锁/未解锁四形态；codexSceneLines：已通关/已解锁/未解锁三态；手验：返回按钮与列数（§10）。

### CP16. 快照轨（RunSnapshot/SnapshotCodec/SnapshotStore + 实体恢复 API）

- **类型**：新建文件 ×3 + 修改类 ×4
- **位置**：`save/RunSnapshot.java`、`save/SnapshotCodec.java`、`save/SnapshotStore.java`（新建）；`utils/RandomGenerator.java:16-18`、`entities/SequentialIdIssuer.java`（全文）、`entities/IdIssuer.java:7-11`、`entities/Player.java:27-29`、`systems/ShopSystem.java`（restoreSlots 已在 §6.CP9 落地）
- **改动说明**：architecture §八快照轨 MVP（存档点仅备战——决策 2026-08-20 沿用，裁决 D10）。**RNG 流复原**是确定性关键：`RandomGenerator(seed, consumedCount)` 重放 nextFloat 对齐底层流（前提不变量「全部消耗点均为单次 nextFloat」——实读核实 weightedPick:48/暴击 BattleSystem:199，无 nextInt 调用方，单测固化）；发号器续号保单一 id 空间不断档；敌阵 WaveSpec 持久化保「轮内敌阵不变」不变量（1C-R 重试语义）；logicTick/notices 不存（命令历史快照轨不携带，恢复后历史清空）。快照不存 RunModifiers——档案仅在 RUN_END 变更且 RUN_END 已删快照，中途不可能漂移（裁决 D10 补充）。读取引用悬空 → 删档按无存档处理（裁决 D20）。
- **代码**（`save/RunSnapshot.java` 完整——纯数据载体，嵌套三类快照；getter 全套省略样式与 CP6 一致，执行器按字段全生成）：

```java
package com.voidvvv.kz_auto_chess_n.save;

import java.util.Collections;
import java.util.List;

/** 挂起存档（快照轨 MVP，仅在 SHOPPING 期捕获——裁决 D10）。完全不可变。 */
public final class RunSnapshot {
    public static final int CURRENT_VERSION = 1;

    private final int version;
    private final long seed;
    /** RNG 底层流消耗计数（恢复 = new RandomGenerator(seed, n) 重放对齐） */
    private final int rngConsumedCount;
    private final String sceneId;
    /** 可空（防御旧档/无英雄局） */
    private final String heroId;
    private final int round;
    private final int mercyLossCount;
    private final int mercyGoldThisRound;
    /** 发号器下一待发号（单一 id 空间续号） */
    private final int idIssuerNext;
    private final int playerGold;
    private final int playerLevel;
    private final int playerExp;
    /** 全部名单棋子（benchUnitIndex/deploymentUnitIndex 引用其下标） */
    private final List<UnitSnapshot> units;
    /** 备战席入席序（units 下标） */
    private final List<Integer> benchUnitIndex;
    /** 部署表 18 格（units 下标；-1 = 空格） */
    private final List<Integer> deploymentUnitIndex;
    /** 背包装备（未穿戴） */
    private final List<EquipmentSnapshot> inventory;
    /** 全部装备实例（含已穿；units.equippedItemIds 引用其下标） */
    private final List<EquipmentSnapshot> equipments;
    /** 商店 5 槽模板 id（null 槽 = JSON null） */
    private final List<String> shopSlotUnitIds;
    /** 敌阵（轮内固定的重试不变量） */
    private final List<WaveEntrySnapshot> enemyWave;

    public RunSnapshot(int version, long seed, int rngConsumedCount, String sceneId, String heroId,
                       int round, int mercyLossCount, int mercyGoldThisRound, int idIssuerNext,
                       int playerGold, int playerLevel, int playerExp,
                       List<UnitSnapshot> units, List<Integer> benchUnitIndex,
                       List<Integer> deploymentUnitIndex, List<EquipmentSnapshot> inventory,
                       List<EquipmentSnapshot> equipments, List<String> shopSlotUnitIds,
                       List<WaveEntrySnapshot> enemyWave) {
        this.version = version;
        this.seed = seed;
        this.rngConsumedCount = rngConsumedCount;
        this.sceneId = sceneId;
        this.heroId = heroId;
        this.round = round;
        this.mercyLossCount = mercyLossCount;
        this.mercyGoldThisRound = mercyGoldThisRound;
        this.idIssuerNext = idIssuerNext;
        this.playerGold = playerGold;
        this.playerLevel = playerLevel;
        this.playerExp = playerExp;
        this.units = Collections.unmodifiableList(new java.util.ArrayList<UnitSnapshot>(units));
        this.benchUnitIndex = Collections.unmodifiableList(new java.util.ArrayList<Integer>(benchUnitIndex));
        this.deploymentUnitIndex = Collections.unmodifiableList(new java.util.ArrayList<Integer>(deploymentUnitIndex));
        this.inventory = Collections.unmodifiableList(new java.util.ArrayList<EquipmentSnapshot>(inventory));
        this.equipments = Collections.unmodifiableList(new java.util.ArrayList<EquipmentSnapshot>(equipments));
        this.shopSlotUnitIds = Collections.unmodifiableList(new java.util.ArrayList<String>(shopSlotUnitIds));
        this.enemyWave = Collections.unmodifiableList(new java.util.ArrayList<WaveEntrySnapshot>(enemyWave));
    }

    // —— getter 全套（version/seed/rngConsumedCount/sceneId/heroId/round/mercyLossCount/
    //    mercyGoldThisRound/idIssuerNext/playerGold/playerLevel/playerExp/units/benchUnitIndex/
    //    deploymentUnitIndex/inventory/equipments/shopSlotUnitIds/enemyWave）——

    /** 名单棋子快照 */
    public static final class UnitSnapshot {
        private final int id;
        private final String unitId;
        private final int star;
        private final int spend;
        private final List<Integer> equippedItemIndex; // equipments 下标

        public UnitSnapshot(int id, String unitId, int star, int spend, List<Integer> equippedItemIndex) {
            this.id = id;
            this.unitId = unitId;
            this.star = star;
            this.spend = spend;
            this.equippedItemIndex = Collections.unmodifiableList(new java.util.ArrayList<Integer>(equippedItemIndex));
        }

        public int getId() { return id; }
        public String getUnitId() { return unitId; }
        public int getStar() { return star; }
        public int getSpend() { return spend; }
        public List<Integer> getEquippedItemIndex() { return equippedItemIndex; }
    }

    /** 装备快照（背包与已穿共池，按 id 幂等） */
    public static final class EquipmentSnapshot {
        private final int id;
        private final String templateId;

        public EquipmentSnapshot(int id, String templateId) {
            this.id = id;
            this.templateId = templateId;
        }

        public int getId() { return id; }
        public String getTemplateId() { return templateId; }
    }

    /** 敌阵条目快照（WaveSpec 平面化） */
    public static final class WaveEntrySnapshot {
        private final String unitId;
        private final int star;
        private final float scale;
        private final int gridX;
        private final int gridY;

        public WaveEntrySnapshot(String unitId, int star, float scale, int gridX, int gridY) {
            this.unitId = unitId;
            this.star = star;
            this.scale = scale;
            this.gridX = gridX;
            this.gridY = gridY;
        }

        public String getUnitId() { return unitId; }
        public int getStar() { return star; }
        public float getScale() { return scale; }
        public int getGridX() { return gridX; }
        public int getGridY() { return gridY; }
    }
}
```

  （`RandomGenerator.java` 修改前，:16-18 逐字）：

```java
    public RandomGenerator(long seed) {
        this.random = new Random(seed);
    }
```

  （修改后——追加复原构造）：

```java
    public RandomGenerator(long seed) {
        this.random = new Random(seed);
    }

    /**
     * 复原构造（快照轨）：重放 consumedCount 次 nextFloat() 对齐底层流。
     * 前提不变量：全部消耗点均为单次 nextFloat（weightedPick/暴击——architecture §六
     * 消耗点清单）；未来若新增 nextInt 通道消耗点，必须同步改造本恢复逻辑与对应单测。
     */
    public RandomGenerator(long seed, int consumedCount) {
        this.random = new Random(seed);
        if (consumedCount < 0) {
            throw new IllegalArgumentException("消耗计数必须 ≥ 0，实际=" + consumedCount);
        }
        for (int i = 0; i < consumedCount; i++) {
            this.random.nextFloat();
        }
        this.consumedCount = consumedCount;
    }
```

  （`IdIssuer.java` 修改前，:7-11 逐字）：

```java
public interface IdIssuer {

    /** 发出下一个唯一 id（实现保证同实例内严格不重复） */
    int nextId();
}
```

  （修改后）：

```java
public interface IdIssuer {

    /** 发出下一个唯一 id（实现保证同实例内严格不重复） */
    int nextId();

    /** 下一待发号（快照捕获用；不消耗） */
    int peekNext();
}
```

  （`SequentialIdIssuer.java` 全文重写——修改前 16 行见现状，修改后）：

```java
package com.voidvvv.kz_auto_chess_n.entities;

/**
 * 顺序发号默认实现：从 1 起严格递增（测试与控制台用）。
 * 复原构造（快照轨）从指定下一号续发——单一 id 空间跨挂起不断档。
 * 非线程安全——战斗逻辑在单线程固定步内推进（architecture §六）。
 */
public final class SequentialIdIssuer implements IdIssuer {
    private int next = 1;

    public SequentialIdIssuer() {
    }

    /** 复原构造：从指定下一号续发（≥ 1） */
    public SequentialIdIssuer(int next) {
        if (next < 1) {
            throw new IllegalArgumentException("下一号必须 ≥ 1，实际=" + next);
        }
        this.next = next;
    }

    @Override
    public int nextId() {
        return next++;
    }

    @Override
    public int peekNext() {
        return next;
    }
}
```

  （`Player.java` 修改前，:27-29 逐字）：

```java
    public Player(int startGold) {
        this.gold = Math.max(0, startGold);
    }
```

  （修改后——追加复原构造与名单整体替换）：

```java
    public Player(int startGold) {
        this.gold = Math.max(0, startGold);
    }

    /** 复原构造（快照轨唯一入口；不变量由 SnapshotCodec 保证，此处防御校验） */
    public Player(int startGold, int level, int currentExp) {
        this.gold = Math.max(0, startGold);
        if (level < 1 || level > GameBalance.MAX_PLAYER_LEVEL) {
            throw new IllegalArgumentException(
                    "棋手等级必须在 1~" + GameBalance.MAX_PLAYER_LEVEL + "，实际=" + level);
        }
        this.level = level;
        this.currentExp = Math.max(0, currentExp);
    }

    /** 名单复原：整体替换备战席（≤ BENCH_SIZE）与部署表（长度恒 18，元素可 null） */
    public void restoreRoster(java.util.List<Unit> benchUnits, Unit[] deploymentGrid) {
        Objects.requireNonNull(benchUnits, "benchUnits 不能为 null");
        Objects.requireNonNull(deploymentGrid, "deploymentGrid 不能为 null");
        if (benchUnits.size() > GameBalance.BENCH_SIZE) {
            throw new IllegalArgumentException(
                    "备战席超过 " + GameBalance.BENCH_SIZE + " 格，实际=" + benchUnits.size());
        }
        if (deploymentGrid.length != deployment.length) {
            throw new IllegalArgumentException(
                    "部署表长度必须 = " + deployment.length + "，实际=" + deploymentGrid.length);
        }
        bench.clear();
        bench.addAll(benchUnits);
        System.arraycopy(deploymentGrid, 0, deployment, 0, deployment.length);
    }
```

  （`save/SnapshotCodec.java` 完整——capture/restore 纯函数 + JSON 编解码）：

```java
package com.voidvvv.kz_auto_chess_n.save;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.config.DataValidationException;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.Equipment;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.RunState;
import com.voidvvv.kz_auto_chess_n.entities.SequentialIdIssuer;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.entities.WaveSpec;
import com.voidvvv.kz_auto_chess_n.systems.ShopSystem;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 快照编解码 + 捕获/复原（纯函数，零 FileHandle——JUnit 直测）。
 * 捕获仅在 SHOPPING 期合法（battleState 恒 null——存档点决策）；复原产物 = 完整 RunContext
 * （runStarted=true / phase=SHOPPING / 敌阵/商店/名单/RNG 流/发号器全复原）。
 * 引用悬空（数据改版）抛 DataValidationException——由 Store 决定删档（裁决 D20）。
 */
public final class SnapshotCodec {

    private static final JsonReader READER = new JsonReader();

    private SnapshotCodec() {
    }

    /** 捕获（BattleScreen 经 MetaService 调用；要求 phase == SHOPPING 且 runStarted） */
    public static RunSnapshot capture(RunContext ctx) {
        Objects.requireNonNull(ctx, "ctx 不能为 null");
        RunState runState = ctx.getRunState();
        if (!runState.isRunStarted() || runState.getPhase() != GamePhase.SHOPPING) {
            throw new IllegalStateException("快照捕获仅限备战期（存档点决策 2026-08-20）");
        }
        List<UnitSnapshot> units = new ArrayList<UnitSnapshot>();
        List<Integer> benchIndex = new ArrayList<Integer>();
        List<Integer> deploymentIndex = new ArrayList<Integer>();
        // units 池序：备战席入席序在前、部署扫描序 y↑x↑ 在后（确定性序）
        for (Unit unit : ctx.getPlayer().getBench()) {
            benchIndex.add(units.size());
            units.add(unitSnapshot(unit));
        }
        for (Unit unit : ctx.getPlayer().getDeployedUnits()) {
            deploymentIndex.add(units.size());
            units.add(unitSnapshot(unit));
        }
        // 部署表 18 格按下标对齐（bench 段填 -1）
        List<Integer> grid = new ArrayList<Integer>(18);
        for (int i = 0; i < 18; i++) {
            grid.add(-1);
        }
        for (int i = 0; i < deploymentIndex.size(); i++) {
            grid.set(deploymentIndex.get(i) - benchIndex.size(), deploymentIndex.get(i));
        }
        List<RunSnapshot.EquipmentSnapshot> inventory = new ArrayList<RunSnapshot.EquipmentSnapshot>();
        for (Equipment item : ctx.getPlayer().getInventory()) {
            inventory.add(new RunSnapshot.EquipmentSnapshot(item.getId(), item.getTemplate().getId()));
        }
        List<String> shopSlots = new ArrayList<String>();
        for (UnitData template : ctx.getShop().getSlots()) {
            shopSlots.add(template == null ? null : template.getId());
        }
        List<RunSnapshot.WaveEntrySnapshot> wave = new ArrayList<RunSnapshot.WaveEntrySnapshot>();
        for (WaveSpec spec : runState.getEnemyWave()) {
            wave.add(new RunSnapshot.WaveEntrySnapshot(spec.getTemplate().getId(), spec.getStar(),
                    spec.getScale(), spec.getGridX(), spec.getGridY()));
        }
        return new RunSnapshot(RunSnapshot.CURRENT_VERSION, runState.getSeed(),
                ctx.getRng().getConsumedCount(), runState.getSceneId(), runState.getHeroId(),
                runState.getRound(), runState.getMercyLossCount(), runState.getMercyGoldThisRound(),
                runState.getIdIssuer().peekNext(), ctx.getPlayer().getGold(),
                ctx.getPlayer().getLevel(), ctx.getPlayer().getCurrentExp(),
                units, benchIndex, grid, inventory, equipmentsOf(units, inventory, ctx),
                shopSlots, wave);
    }

    private static UnitSnapshot unitSnapshot(Unit unit) {
        List<Integer> equipped = new ArrayList<Integer>();
        for (Equipment item : unit.getEquipped()) {
            equipped.add(item.getId()); // 暂存装备 id，equipmentsOf 统一折算下标
        }
        return new UnitSnapshot(unit.getId(), unit.getTemplate().getId(), unit.getStar(),
                unit.getSpend(), equipped);
    }

    /** 装备实例全池（units.equippedItemIndex 存的是池下标；捕获时 id→下标折算在 write/restore 之间统一处理） */
    private static List<RunSnapshot.EquipmentSnapshot> equipmentsOf(List<UnitSnapshot> units,
                                                                    List<RunSnapshot.EquipmentSnapshot> inventory,
                                                                    RunContext ctx) {
        // 实现口径：equipments 池 = 已穿装备（units 遍历序）+ 背包序；
        // UnitSnapshot.equippedItemIndex 在 capture 后统一折算（见折算循环）
        List<RunSnapshot.EquipmentSnapshot> pool =
                new ArrayList<RunSnapshot.EquipmentSnapshot>();
        for (UnitSnapshot unit : units) {
            for (int i = 0; i < unit.getEquippedItemIndex().size(); i++) {
                int equipmentId = unit.getEquippedItemIndex().get(i);
                Equipment worn = findEquipment(ctx, equipmentId);
                pool.add(new RunSnapshot.EquipmentSnapshot(equipmentId, worn.getTemplate().getId()));
            }
        }
        pool.addAll(inventory);
        // 折算：UnitSnapshot 里的 id 序改写为池下标（快照内自洽）
        int cursor = 0;
        for (UnitSnapshot unit : units) {
            List<Integer> asIndex = new ArrayList<Integer>();
            for (int i = 0; i < unit.getEquippedItemIndex().size(); i++) {
                asIndex.add(cursor++);
            }
            // UnitSnapshot 不可变——折算产物在 write() 阶段按下标规则输出，restore 对称解析
            // 本口径详见 write/restore 的 equippedItemIndex 直存池下标实现
        }
        return pool;
    }
```

  > **执行器注意（TODO(executor)）**：上述 `equipmentsOf` 的「id→池下标折算」存在中间态复杂度。落定实现口径简化为：**`capture` 时直接以池下标填充 `UnitSnapshot.equippedItemIndex`**（先扫名单构建 id→模板映射，再二次遍历写快照），删除 `equipmentsOf` 的折算循环；`write/read` 对称直存下标。本 CP 评审通过后由执行器按此口径一次成型，接口签名不变。

```java
    /** 复原：RunContext（runStarted=true / phase=SHOPPING）；引用悬空抛 DataValidationException */
    public static RunContext restore(RunSnapshot s, GameData data, Profile profile, ShopSystem shop) {
        Objects.requireNonNull(s, "snapshot 不能为 null");
        Objects.requireNonNull(data, "data 不能为 null");
        Objects.requireNonNull(profile, "profile 不能为 null");
        Objects.requireNonNull(shop, "shop 不能为 null");
        if (s.getVersion() != RunSnapshot.CURRENT_VERSION) {
            throw new DataValidationException("run_snapshot.json: 不支持的快照版本 " + s.getVersion());
        }
        if (data.getScene(s.getSceneId()) == null) {
            throw new DataValidationException("run_snapshot.json/sceneId: 场景不存在: " + s.getSceneId());
        }
        if (s.getHeroId() != null && data.getHero(s.getHeroId()) == null) {
            throw new DataValidationException("run_snapshot.json/heroId: 英雄不存在: " + s.getHeroId());
        }
        // 装备实例池
        List<Equipment> equipmentPool = new ArrayList<Equipment>();
        for (RunSnapshot.EquipmentSnapshot es : s.getEquipments()) {
            com.voidvvv.kz_auto_chess_n.data.EquipmentData template =
                    data.getEquipment(es.getTemplateId());
            if (template == null) {
                throw new DataValidationException("run_snapshot.json/equipments: 装备模板不存在: "
                        + es.getTemplateId());
            }
            equipmentPool.add(new Equipment(es.getId(), template));
        }
        // 名单
        List<Unit> unitPool = new ArrayList<Unit>();
        for (UnitSnapshot us : s.getUnits()) {
            UnitData template = data.getUnit(us.getUnitId());
            if (template == null) {
                throw new DataValidationException("run_snapshot.json/units: 单位模板不存在: "
                        + us.getUnitId());
            }
            Unit unit = new Unit(us.getId(), template, us.getStar(), us.getSpend());
            for (int index : us.getEquippedItemIndex()) {
                unit.equip(equipmentPool.get(index)); // 槽位唯一性由捕获端保证，冲突即抛（防坏档）
            }
            unitPool.add(unit);
        }
        Player player = new Player(s.getPlayerGold(), s.getPlayerLevel(), s.getPlayerExp());
        List<Unit> bench = new ArrayList<Unit>();
        for (int index : s.getBenchUnitIndex()) {
            bench.add(unitPool.get(index));
        }
        Unit[] deployment = new Unit[18];
        for (int i = 0; i < 18; i++) {
            int index = s.getDeploymentUnitIndex().get(i);
            deployment[i] = index < 0 ? null : unitPool.get(index);
        }
        player.restoreRoster(bench, deployment);
        for (int i = 0; i < s.getInventory().size(); i++) {
            RunSnapshot.EquipmentSnapshot es = s.getInventory().get(i);
            player.addToInventory(equipmentPool.get(equipmentPool.size()
                    - s.getInventory().size() + i)); // 池尾 inventory 段（capture 序）
        }
        // 商店槽
        List<UnitData> slots = new ArrayList<UnitData>();
        for (String unitId : s.getShopSlotUnitIds()) {
            if (unitId == null) {
                slots.add(null);
                continue;
            }
            UnitData template = data.getUnit(unitId);
            if (template == null) {
                throw new DataValidationException("run_snapshot.json/shopSlots: 单位模板不存在: " + unitId);
            }
            slots.add(template);
        }
        shop.restoreSlots(slots);
        // RunState（modifiers 按当前档案 + heroId 重算——档案局中不可能变更，裁决 D10 补充）
        RunModifiers modifiers = ProfileService.runModifiers(
                s.getHeroId() == null ? null : data.getHero(s.getHeroId()), profile, data);
        RunState runState = new RunState(s.getSeed(), s.getSceneId(), s.getHeroId(), modifiers,
                new SequentialIdIssuer(s.getIdIssuerNext()));
        runState.setRound(s.getRound());
        runState.setMercyLossCount(s.getMercyLossCount());
        runState.setMercyGoldThisRound(s.getMercyGoldThisRound());
        runState.markRunStarted();
        runState.setPhase(GamePhase.SHOPPING);
        List<WaveSpec> wave = new ArrayList<WaveSpec>();
        for (RunSnapshot.WaveEntrySnapshot we : s.getEnemyWave()) {
            UnitData template = data.getUnit(we.getUnitId());
            if (template == null) {
                throw new DataValidationException("run_snapshot.json/enemyWave: 单位模板不存在: "
                        + we.getUnitId());
            }
            wave.add(new WaveSpec(template, we.getStar(), we.getScale(), we.getGridX(), we.getGridY()));
        }
        runState.setEnemyWave(wave);
        return new RunContext(player, runState, data,
                new RandomGenerator(s.getSeed(), s.getRngConsumedCount()), shop);
    }

    /** 序列化（手拼 JsonWriter——字段序固定，确定性输出）与反序列化（JsonReader 显式映射）。
     *  TODO(executor): write/read 按 RunSnapshot 字段全量映射（数值/字符串/嵌套数组三型），
     *  口径与 ProfileCodec 一致（未知字段即死、整数校验、null 元素合法于 shopSlots）。 */
    public static String write(RunSnapshot s) {
        StringWriter writer = new StringWriter(512);
        JsonWriter json = new JsonWriter(writer);
        try {
            json.object();
            json.writeValue("version", s.getVersion());
            json.writeValue("seed", s.getSeed());
            json.writeValue("rngConsumedCount", s.getRngConsumedCount());
            json.writeValue("sceneId", s.getSceneId());
            json.writeValue("heroId", s.getHeroId());
            json.writeValue("round", s.getRound());
            json.writeValue("mercyLossCount", s.getMercyLossCount());
            json.writeValue("mercyGoldThisRound", s.getMercyGoldThisRound());
            json.writeValue("idIssuerNext", s.getIdIssuerNext());
            json.writeValue("playerGold", s.getPlayerGold());
            json.writeValue("playerLevel", s.getPlayerLevel());
            json.writeValue("playerExp", s.getPlayerExp());
            json.writeArrayStart("units");
            for (UnitSnapshot u : s.getUnits()) {
                json.object();
                json.writeValue("id", u.getId());
                json.writeValue("unitId", u.getUnitId());
                json.writeValue("star", u.getStar());
                json.writeValue("spend", u.getSpend());
                json.writeArrayStart("equippedItemIndex");
                for (int index : u.getEquippedItemIndex()) {
                    json.writeValue(index);
                }
                json.writeArrayEnd();
                json.pop();
            }
            json.writeArrayEnd();
            writeIntArray(json, "benchUnitIndex", s.getBenchUnitIndex());
            writeIntArray(json, "deploymentUnitIndex", s.getDeploymentUnitIndex());
            writeEquipmentArray(json, "inventory", s.getInventory());
            writeEquipmentArray(json, "equipments", s.getEquipments());
            json.writeArrayStart("shopSlotUnitIds");
            for (String unitId : s.getShopSlotUnitIds()) {
                json.writeValue(unitId);
            }
            json.writeArrayEnd();
            json.writeArrayStart("enemyWave");
            for (RunSnapshot.WaveEntrySnapshot we : s.getEnemyWave()) {
                json.object();
                json.writeValue("unitId", we.getUnitId());
                json.writeValue("star", we.getStar());
                json.writeValue("scale", we.getScale());
                json.writeValue("gridX", we.getGridX());
                json.writeValue("gridY", we.getGridY());
                json.pop();
            }
            json.writeArrayEnd();
            json.pop();
            return writer.toString();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("快照序列化失败（不可达——StringWriter 不抛 IO）", ex);
        }
    }

    private static void writeIntArray(JsonWriter json, String name, List<Integer> values)
            throws java.io.IOException {
        json.writeArrayStart(name);
        for (int value : values) {
            json.writeValue(value);
        }
        json.writeArrayEnd();
    }

    private static void writeEquipmentArray(JsonWriter json, String name,
                                            List<RunSnapshot.EquipmentSnapshot> list)
            throws java.io.IOException {
        json.writeArrayStart(name);
        for (RunSnapshot.EquipmentSnapshot es : list) {
            json.object();
            json.writeValue("id", es.getId());
            json.writeValue("templateId", es.getTemplateId());
            json.pop();
        }
        json.writeArrayEnd();
    }

    /** 反序列化（显式映射；TODO(executor): 与 write 字段一一对称实现，约 90 行样板） */
    public static RunSnapshot read(String json) {
        // TODO(executor): JsonReader 显式映射全字段（口径同 ProfileCodec：未知字段即死、
        // 版本不符抛错交 Store 删档）；与 write() 对称，单测以 round-trip 锁定
        throw new UnsupportedOperationException("Phase 6 执行期实现（与本 CP 测试同批落地）");
    }

    private static Equipment findEquipment(RunContext ctx, int equipmentId) {
        Equipment inInventory = ctx.getPlayer().findInventoryItem(equipmentId);
        if (inInventory != null) {
            return inInventory;
        }
        Unit owner = ctx.getPlayer().findEquipOwner(equipmentId);
        if (owner == null) {
            throw new IllegalStateException("快照捕获遇到悬空装备 id: " + equipmentId);
        }
        for (Equipment item : owner.getEquipped()) {
            if (item.getId() == equipmentId) {
                return item;
            }
        }
        throw new IllegalStateException("快照捕获遇到悬空装备 id: " + equipmentId);
    }
}
```

  > **TODO(executor) 汇总（本 CP 两处，均有锁定测试）**：① `capture` 的 equippedItemIndex 直存池下标口径（评审注记在上文）；② `read()` 全字段映射（write 的对称样板）。其余代码为定稿。

  （`save/SnapshotStore.java` 完整）：

```java
package com.voidvvv.kz_auto_chess_n.save;

import com.badlogic.gdx.files.FileHandle;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.systems.ShopSystem;

/**
 * 快照文件 IO：写 = capture 序列化直存；读 = 反序列化 + 引用完整性干跑校验
 * （restore 到临时 ShopSystem/初始 Profile——校验模板引用不落真档），
 * 悬空/损坏 → 删档 + 日志 + 返回 null（裁决 D20；与静态资源 fail-fast 口径区分）。
 */
public final class SnapshotStore {

    private final FileHandle file;

    public SnapshotStore(FileHandle file) {
        this.file = file;
    }

    public boolean exists() {
        return file != null && file.exists();
    }

    /** 读快照；不存在/损坏/引用悬空 → 删档返回 null（主菜单按钮随即不可见） */
    public RunSnapshot load(GameData data) {
        if (!exists()) {
            return null;
        }
        try {
            RunSnapshot snapshot = SnapshotCodec.read(file.readString("UTF-8"));
            SnapshotCodec.restore(snapshot, data, Profile.fresh(), new ShopSystem()); // 干跑校验
            return snapshot;
        } catch (RuntimeException ex) {
            System.err.println("[SnapshotStore] 快照损坏或引用悬空，删除: "
                    + (file == null ? "?" : file.path()) + " / " + ex.getMessage());
            delete();
            return null;
        }
    }

    public boolean save(RunSnapshot snapshot) {
        if (file == null) {
            return false;
        }
        try {
            if (file.parent() != null) {
                file.parent().mkdirs();
            }
            file.writeString(SnapshotCodec.write(snapshot), false, "UTF-8");
            return true;
        } catch (RuntimeException ex) {
            System.err.println("[SnapshotStore] 快照写入失败: " + file.path() + " / " + ex.getMessage());
            return false;
        }
    }

    public void delete() {
        if (file != null && file.exists()) {
            try {
                file.delete();
            } catch (RuntimeException ex) {
                System.err.println("[SnapshotStore] 快照删除失败: " + file.path() + " / " + ex.getMessage());
            }
        }
    }
}
```
- **测试要点**：新建 `save/SnapshotCodecTest`（核心验收件）——
  - round-trip：SHOPPING 期富态上下文（金/等级/经验/备战席含装备/部署表含空格/背包/商店含空槽/敌阵含 Boss/怜悯双计数/RNG 已消耗 N 次/id 已发 M 号）→ capture → write → read → restore → 逐项等价断言（gold/level/exp/round/mercy×2/bench 序与星/spend/装备归属/deployment 18 格/shop 槽模板/enemyWave equals（WaveSpec 自带 equals:43-57）/runStarted/phase）；
  - **续战等价**（最强断言）：restore 后继续 advanceAfterVictory 一轮 → 敌阵重生成与商店刷新结果，和「未挂起的同 seed 上下文直接推进」**逐位相同**（RNG 流对齐的直接证明）；
  - 非 SHOPPING 期 capture 抛 IllegalStateException；read 版本不符/未知字段抛错；restore 引用悬空（unitId/equipment templateId/sceneId/heroId 四路）抛 DataValidationException；
  - `utils/RandomGeneratorTest` 增——`(seed, n)` 复原后与原生消耗 n 次的实例**后续序列逐位相同**（1000 次 nextFloat 对照）；
  - `entities/SequentialIdIssuerTest` 增——复原构造续号、peekNext 不消耗、next<1 抛错；`entities/PlayerTest` 增——复原构造校验与 restoreRoster 长度/超席抛错。

### CP17. BattleScreen 装配整合（scene/hero/modifiers + 结算观察 + 快照触发 + 续玩路径）

- **类型**：修改类
- **位置**：`screens/BattleScreen.java:63-104`（字段）、`:112-142`（构造与 RunEndPanel 装配）、`:199-239`（show）、`:255-292`（render 观察段）、`:350-363`（pause/hide）、`:373-391`（newContext/restartRun）
- **改动说明**：Phase 6 装配点汇总——①新局构造（seed/sceneId/heroId + MetaService）；②续玩构造（RunSnapshot → show 内 restore，跳过 StartRun）；③RUN_END 首帧结算观察（裁决 D11：MetaService.settleRun + RunEndPanel.setSettlementLines + 清快照，runEndSettled 每局一次）；④快照触发（进入 SHOPPING 首帧 + pause/hide 补写，snapshotCurrent 旗标——裁决 D10）；⑤RESTART 同英雄同场景新 seed；⑥返回主菜单按钮。起始金币含 hero 加成在 newContext（`START_GOLD + modifiers.getStartGoldBonus()`，格雷克 Lv.1 = 14 金——裁决 D2）。
- **代码**（字段区修改前，:101-104 逐字）：

```java
    /** 本局 seed（UI 域边界给定——MainMenu START 传入；RESTART 换新，Q3 裁决） */
    private long seed;
    /** 宝箱弹窗在栈标记（防重复 push；领取/离开 RESULT 后收起，CP29） */
    private boolean chestShown;
```

  （修改后）：

```java
    /** 本局 seed（UI 域边界给定——RunSetup 传入；RESTART 换新，Q3 裁决口径 Phase 6 落地） */
    private long seed;
    /** 本局场景/英雄（RunSetup 选定；RESTART 沿用——裁决见 §4） */
    private final String selectedSceneId;
    private final String selectedHeroId;
    /** 续玩快照（null = 新局；非 null 时 show() 走恢复路径，不发 StartRun） */
    private final RunSnapshot resumedSnapshot;
    /** RUN_END 结算观察旗标（每局一次——裁决 D11） */
    private boolean runEndSettled;
    /** 本轮快照已写标记（进入 SHOPPING 首帧写、离开 SHOPPING 复位——裁决 D10） */
    private boolean snapshotCurrent;
    /** 宝箱弹窗在栈标记（防重复 push；领取/离开 RESULT 后收起，CP29） */
    private boolean chestShown;
```

  （构造器修改前，:112-116 逐字）：

```java
    public BattleScreen(Game game, Assets assets, GameData data, long seed) {
        this.game = game;
        this.assets = assets;
        this.data = data;
        this.seed = seed;
```

  （修改后——双构造）：

```java
    /** 新局（RunSetup 域边界事件三参 + 档案门面） */
    public BattleScreen(Game game, Assets assets, GameData data, MetaService metaService,
                        long seed, String sceneId, String heroId) {
        this(game, assets, data, metaService, seed, sceneId, heroId, null);
    }

    /** 续玩（主菜单「继续远征」：快照在 show() 复原，跳过 StartRun） */
    public BattleScreen(Game game, Assets assets, GameData data, MetaService metaService, RunSnapshot snapshot) {
        this(game, assets, data, metaService, snapshot.getSeed(), snapshot.getSceneId(),
                snapshot.getHeroId(), snapshot);
    }

    private BattleScreen(Game game, Assets assets, GameData data, MetaService metaService,
                         long seed, String sceneId, String heroId, RunSnapshot resumedSnapshot) {
        this.game = game;
        this.assets = assets;
        this.data = data;
        this.metaService = metaService;
        this.seed = seed;
        this.selectedSceneId = sceneId;
        this.selectedHeroId = heroId;
        this.resumedSnapshot = resumedSnapshot;
```

  （字段区追加 `private final MetaService metaService;`；RunEndPanel 装配 `:137-142` 修改前逐字）：

```java
        this.runEndPanel = new RunEndPanel(assets, new RunEndPanel.RestartListener() {
            @Override
            public void onRestart() {
                restartRun();
            }
        }, contextSupplier());
```

  （修改后——增 MenuListener）：

```java
        this.runEndPanel = new RunEndPanel(assets, new RunEndPanel.RestartListener() {
            @Override
            public void onRestart() {
                restartRun();
            }
        }, new RunEndPanel.MenuListener() {
            @Override
            public void onMenuRequested() {
                game.setScreen(new MainMenuScreen(game, assets, data, metaService));
            }
        }, contextSupplier());
```

  （show `:199-206` 修改前逐字）：

```java
    @Override
    public void show() {
        this.runContext = newContext(seed);
        runFlowSystem.registerHandlers(commandManager);   // 流程五命令（StartRun/StartBattle/Surrender/PickChest/AbandonRun）
        shopSystem.registerHandlers(commandManager);      // BuyUnit/RefreshShop/BuyExp
        rosterSystem.registerHandlers(commandManager);    // MoveUnit/SellUnit
        equipmentSystem.registerHandlers(commandManager); // EquipItem/UnequipItem
        commandManager.addCommand(new StartRunCommand(
                seed, runContext.getRunState().getSceneId(), null)); // 回放第 0 条记录（Q3 裁决）
```

  （修改后）：

```java
    @Override
    public void show() {
        if (resumedSnapshot != null) {
            try {
                this.runContext = SnapshotCodec.restore(resumedSnapshot, data,
                        metaService.getProfile(), shopSystem);
            } catch (RuntimeException ex) {
                System.err.println("[BattleScreen] 快照恢复失败，回退新局: " + ex.getMessage());
                this.runContext = newContext(seed);
            }
        } else {
            this.runContext = newContext(seed);
        }
        runFlowSystem.registerHandlers(commandManager);   // 流程五命令（StartRun/StartBattle/Surrender/PickChest/AbandonRun）
        shopSystem.registerHandlers(commandManager);      // BuyUnit/RefreshShop/BuyExp
        rosterSystem.registerHandlers(commandManager);    // MoveUnit/SellUnit
        equipmentSystem.registerHandlers(commandManager); // EquipItem/UnequipItem
        if (!resumed && runContext.getRunState().getRound() == 1
                && !runContext.getRunState().isRunStarted()) {
            commandManager.addCommand(new StartRunCommand(
                    seed, runContext.getRunState().getSceneId(), selectedHeroId)); // 回放第 0 条记录（heroId 启用）
        }
```

  > `resumed` 布尔字段 = `resumedSnapshot != null` 的派生缓存（构造器赋值 `this.resumed = resumedSnapshot != null;`），语义同上文注释；恢复失败回退分支置 `resumed = false` 保证 StartRun 仍发（极端防御路径，正常已被 Store 删档前置拦截）。

  （render 观察段 `:256-257` 修改前逐字）：

```java
        GamePhase phase = runContext.getRunState().getPhase();
        topBar.refresh(runContext);
```

  （修改后——插入档案结算与快照观察）：

```java
        GamePhase phase = runContext.getRunState().getPhase();
        if (phase == GamePhase.RUN_END && !runEndSettled) { // 档案结算每局一次（裁决 D11）
            runEndSettled = true;
            ProfileService.Settlement settlement = metaService.settleRun(data, runContext);
            runEndPanel.setSettlementLines(RunSettlementText.lines(settlement, data));
            metaService.clearRunSnapshot();
        }
        if (phase != GamePhase.SHOPPING) { // 快照轨：进入备战首帧写、离开复位（裁决 D10）
            snapshotCurrent = false;
        } else if (runContext.getRunState().isRunStarted() && !snapshotCurrent) {
            metaService.saveRunSnapshot(runContext);
            snapshotCurrent = true;
        }
        topBar.refresh(runContext);
```

  （pause/hide `:350-363` 修改前逐字）：

```java
    @Override
    public void pause() {
        paused = true; // 冻结 accumulator（Android 挂起）
    }

    @Override
    public void resume() {
        paused = false;
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null); // 防僵尸监听（input §2.3）
    }
```

  （修改后）：

```java
    @Override
    public void pause() {
        paused = true; // 冻结 accumulator（Android 挂起）
        saveSnapshotIfShopping(); // 快照补写（仅备战期——存档点决策）
    }

    @Override
    public void resume() {
        paused = false;
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null); // 防僵尸监听（input §2.3）
        saveSnapshotIfShopping();
    }

    /** 快照补写（pause/hide；战斗/结算期不写——存档点仅备战） */
    private void saveSnapshotIfShopping() {
        if (runContext != null && runContext.getRunState().isRunStarted()
                && runContext.getRunState().getPhase() == GamePhase.SHOPPING) {
            metaService.saveRunSnapshot(runContext);
        }
    }
```

  （newContext/restartRun `:373-391` 修改前逐字）：

```java
    /** 组装新鲜上下文（seed 来自 UI 域边界事件——Q3 裁决；首场景 MVP 仅森林） */
    private RunContext newContext(long runSeed) {
        String sceneId = data.getScenes().keySet().iterator().next(); // 首场景（种子仅森林）
        return new RunContext(new Player(GameBalance.START_GOLD),
                new RunState(runSeed, sceneId, new SequentialIdIssuer()),
                data, new RandomGenerator(runSeed), shopSystem);
    }

    /** RUN_END 重开：新 seed + 清弹窗/残留命令 + 复入 startRun（RunFlowSystem.restart 契约） */
    private void restartRun() {
        this.seed = System.nanoTime(); // UI 边界新 seed（口径 #12）
        this.runContext = newContext(seed);
        commandManager.discardPending();       // 跨局残留命令防泄漏（口径 #12）
        dialogManager.clearAll();
        chestShown = false;
        runFlowSystem.restart(runContext);
        accumulator = 0f;
        battleHud.resetSpeed();
    }
```

  （修改后）：

```java
    /** 组装新鲜上下文（seed/sceneId/heroId 来自 RunSetup 域边界；起始金含英雄加成——裁决 D2） */
    private RunContext newContext(long runSeed) {
        String sceneId = selectedSceneId != null && data.getScene(selectedSceneId) != null
                ? selectedSceneId : data.getScenes().keySet().iterator().next(); // 防御回退首场景
        RunModifiers modifiers = metaService.resolveRunModifiers(selectedHeroId, data);
        return new RunContext(new Player(GameBalance.START_GOLD + modifiers.getStartGoldBonus()),
                new RunState(runSeed, sceneId, selectedHeroId, modifiers, new SequentialIdIssuer()),
                data, new RandomGenerator(runSeed), shopSystem);
    }

    /** RUN_END 重开：同英雄同场景新 seed + 清弹窗/残留命令/结算旗标 + 复入 startRun */
    private void restartRun() {
        this.seed = System.nanoTime(); // UI 边界新 seed（口径 #12）
        this.runContext = newContext(seed);
        commandManager.discardPending();       // 跨局残留命令防泄漏（口径 #12）
        dialogManager.clearAll();
        chestShown = false;
        runEndSettled = false; // 新局重新观察结算
        snapshotCurrent = false;
        runFlowSystem.restart(runContext);
        accumulator = 0f;
        battleHud.resetSpeed();
    }
```

  （import 补：`entities/RunModifiers`、`save/MetaService`、`save/ProfileService`、`save/RunSettlementText`、`save/RunSnapshot`、`save/SnapshotCodec`；`MetaService` 完整代码见 §6.CP7——`settleRun(GameData, RunContext)`/`resolveRunModifiers(heroId, data)`/`saveRunSnapshot(RunContext)`/`loadRunSnapshot(data)`/`hasRunSnapshot()`/`clearRunSnapshot()`，不在此重复。）
- **测试要点**：本屏 Gdx 绑定，逻辑断言已下沉（settle→CP6、capture/restore→CP16、modifiers→CP5/CP6）；手验清单全链覆盖（§10）。

### CP18. 文档回写批次（Phase 5/5.1/feedback06 遗留 + 本期结构变更）

- **类型**：修改设计文档 ×3
- **位置**：`docs/data_schema_design.md`、`docs/render_design.md`、`docs/architecture_design.md`
- **改动说明**：销账三笔历史 WARNING + 回写本期结构变更。**本期不改 GDD**（沿 Phase 5 惯例：实施文档承担差异声明，GDD 数值待调清单维持）。
- **代码**（逐项「修改前 → 修改后」）：

  ① data_schema §三词表 Rarity 行（销 feedback06 W2——代码为尊）：

```markdown
| **Rarity** | `WHITE`（白）`FINISHED`（成）`LEGENDARY`（传说） |
```

```markdown
| **Rarity**（V1.5 对齐代码 `EquipmentRarity`） | `WHITE`（白）`RARE`（成）`LEGENDARY`（传说） |
```

  ② data_schema §六 synergies 字段表补 `desc`（销 phase5.1 WARNING-8——Phase 5.1 已实装）：在 `name` 行后插入：

```markdown
| `desc` | string | ✓ | — | 一句主题描述（手写文案；档位数值行由 thresholds 结构化生成——Phase 5.1 裁决 2） |
```

  ③ data_schema §七 scenes 表与示例补 `shopUnlocks`（本期 CP4 结构变更）：字段表追加：

```markdown
| `shopUnlocks` | string[] | ✗ | 缺省空表；元素 ∈ units 且非 Boss；不跨场景重复；不得为任何英雄传奇（互斥） | 该场景解锁后进入商店池的单位（Phase 6 场景门控；未列入者 = 基础池恒可购） |
```

  ④ data_schema §一文件表 heroes.json 行（销「延后」）：

```markdown
| `heroes.json` | Phase 6 | **延后**——档案层系统未设计，预写必返工 |
```

```markdown
| `heroes.json` | Phase 6 | **字段终版 + 完整示例**（V1.5：id/name/desc/passive{type∈HeroPassiveType,value,synergyIds}/legendaryUnitId；词表三值 START_GOLD/SYNERGY_AMP/ENERGY_GAIN；交叉校验见 §九与 spec CP2） |
```

  ⑤ data_schema 版本头 V1.4 → V1.5 + 决策日志追加一行（heroes 落地/shopUnlocks/Rarity 对齐三合一）；

  ⑥ render_design §九表末追加悬停锚点注记（销 feedback06 D3）：

```markdown
- **悬停预览锚点（Phase 5.1 R1 + feedback07，表外补充常量）**：棋盘悬停 (128,48,94,192)、商店悬停 (508,48,112,192)、背包悬停 (132,140,90,100)——`BoardGeometry` BOARD_HOVER/SHOP_HOVER/INVENTORY_HOVER 组；源：① ShopBar 槽位（250ms 驻留）② 棋盘单位（点击候选）③ 背包槽位（BATTLE 置灰/空槽/拖拽中归一抑制）
```

  ⑦ architecture §八持久化双轨表补快照轨落地注记：

```markdown
| **快照轨**（挂起存档，Phase 6 落地） | 全量状态序列化 | RunState(seed/heroId/round/怜悯/idIssuer/RNG 消耗计数) + Player(名单/装备/背包) + 商店槽 + 敌阵；`save/run_snapshot.json`；仅备战期写、RUN_END 删 | 进入 SHOPPING / pause / hide |
```
- **测试要点**：文档改动无单测；手验：markdown 表格渲染无断行（§10）。

---

## 7. 分阶段任务拆解

按执行顺序组织 CP（实现与代码见 §6 各 CP，此处不复述）。提交切分沿用 Phase 5 惯例（每任务 1~2 个 feat 提交）。**裁剪线**（裁决 D1）：T4（内容）与 T7（快照）为预定义可后延项——后延时相应手验项顺延，其余任务不受阻（快照后延时 MainMenu「继续远征」按钮整段不出现，hasRunSnapshot 恒 false）。

| 任务 | 所含 CP | 前置 | 验收标准（要点） |
|------|---------|------|------|
| T1 数据层与常量基座 | CP1、CP2、CP4 | 无 | 新测试全绿（GameBalance/JsonLoaderHeroes/JsonLoaderScenes 增例）；GameData/JsonLoader 兼容重载保旧签名（存量测试零回归）；heroes.json + scenes.json（shopUnlocks）可加载 |
| T2 档案域核心 | CP5、CP6、CP7、CP8 | T1 | ProfileService/ProfileCodec/ProfileStore/RunModifiers/RunState 增例全绿；RunFlowSystemTest :634 新口径（135）绿；StartRun heroId 一致性绿；profile.json round-trip 绿 |
| T3 加成局内接入 | CP9、CP10 | T2 | ShopSystem 加成/门控/折扣/RNG 消耗恒定断言绿；SynergySystem 增幅与 BattleSystem 玩家侧源/敌方侧隔离断言绿；EMPTY 重载与旧实现输出全等（回归锚） |
| T4 内容种子（可裁剪） | CP3、CP11 | T1 | 三场景/27 单位/8 羁绊/3 英雄加载零软告警零悬空；WaveGenerator 新场景生成绿；费阶池分布抽验 |
| T5 选屏与导航 | CP12、CP13、CP15 | T2、T4 | lwjgl3 手验：主菜单三按钮、RunSetup 英雄/场景选择与灰置、Codex 两区展示与返回；未选齐点开始无跳转 |
| T6 结算展示 | CP14 | T2 | RunSettlementTextTest 四形态绿；手验：通关/放弃两路径结算行、双按钮 |
| T7 快照轨（可裁剪） | CP16 | T2 | SnapshotCodecTest round-trip + **续战等价**断言绿；RandomGenerator 流重放断言绿；Player/SequentialIdIssuer 恢复 API 绿 |
| T8 装配整合 | CP17 | T3、T5、T6、T7 | 手验全清单（§10）逐项过：开局加成生效（14 金）、Lv2/Lv3/Lv5 加成、结算入档、退出重进复原、RUN_END 删档 |
| T9 文档回写与全量回归 | CP18 | T8 | 设计文档三笔回写完成；`gradlew :core:test` 退出码 0 + TEST-*.xml 聚合全绿（MEMORY 口径）；Android 真机回归（BACK 键/挂起恢复/横屏 + 存档路径） |

---

## 8. 风险与开放问题

### WARNING（不阻塞，已按口径落地或观察项）

| # | 项 | 处置 |
|---|----|------|
| 1 | 起始金整体 +2（Lv.1 全英雄权益——裁决 D2）：经济曲线整体左移，GDD §3.2「起始 10 金」锚点事实上变为 10~15 | 数值待调（Lv.1 权益/格雷克被动/ Lv.4 三值都在 GameBalance 单点可调）；手验关注第 1~3 轮节奏 |
| 2 | 可购池扩至 18 单位（1 费 8 / 2 费 6 / 3 费 4+3 传奇）：商店同质化缓解但分布变化；传奇棋子 3 费池额外 +3 | CP11 数值全部工作值待调；Phase 7 数值平衡批处理 |
| 3 | 门控池在早期轮可能为空（如 3 费池全被门控）→ 槽位置 null | 沿「池空也消耗 RNG、槽位 null」现状（ShopSystem.java:92），UI 空槽不绘制——无回归；内容铺量后自然消解 |
| 4 | RNG 复原依赖「全部消耗点均为单次 nextFloat」不变量 | CP16 单测 1000 次序列对照固化；未来新增 nextInt 通道消耗点必须改 RandomGenerator 复原构造（javadoc 警示） |
| 5 | 快照不含命令历史/logicTick：恢复局不可再走回放轨重演 | 两轨本就独立（architecture §八）；回放轨 Phase 7 立项时以「快照局排除在回放库外」为口径 |
| 6 | `assets/save/` 运行期产物与 assets 打包边界：Android 打包时目录不存在（运行期才创建），无打包污染 | CP7 .gitignore；Android local = internal storage 天然隔离；手验含 Android 路径抽查 |
| 7 | ProfileCodec 手拼 JSON 无转义：依赖「id 均经加载校验无引号」前提 | 加载校验链（units/heroes/scenes id 全部经 requireString 白名单语境）+ codec 单测覆盖含中文 id（中文名不进 id 字段——数据现状确认） |
| 8 | RunSetupScreen/CodexScreen/MainMenu 重写后无 Skin，布局常量为工作值 | ±4px 容差手验（render §九惯例）；Kenney UI 包接入（Q4 遗留）时统一换装 |
| 9 | BattleScreen 预计 ~430 行（+40） | 800 上限内；Phase 7 若再膨胀优先拆「装配工厂 + 观察器」（WARNING-12 沿革） |
| 10 | 结算在 RUN_END 首帧同步写盘：极低端机帧尖峰（~2KB 写） | 单次写、可忽略；若实测超标改异步（Phase 7） |
| 11 | 旧档回归：内容改版后 profile.json 内 heroId/sceneId 悬空 | Profile 查询天然容忍（map miss → 默认值）；unlockedSceneIds 对未知 completedScenes 无感；手验含构造旧档回归 |
| 12 | 薇拉增幅使 SynergyPanel 预演显示增幅后数值（resolve 单一管线） | 符合「显示实际生效档」口径（render §九）；差异声明不再需要 |
| 13 | masteryAwarded 与 Settlement 双轨：RunState 暂存值仅展示兜底（settlementLines null 时） | 正常路径恒有 settlement；RESTART 后旗标复位已测 |
| 14 | 文档回写未覆盖 GDD §8.1 表的 Lv.4/5 工作值 | GDD 待调清单本就挂账（§十一）；数值定稿时 GDD 与 GameBalance 同步 |

### 开放问题（遗留，不阻塞）

1. **回放轨**（录像）：history 消费模型与快照局的互斥口径——Phase 7 立项时定（WARNING-5）。
2. **具名 Boss 技能**（震地/双生弹/黄金清算/冰风/断星锤）：GDD §7.2 点名形状与现有 11 技能不完全匹配（如 LINE+STUN 无对应）——Phase 7 内容任务（裁决 D15）。
3. **每场景池铺量**（每池 3 → 6+）与亡灵/巨人羁绊平衡——Phase 7 数值平衡批（裁决 D19 裁剪线内未铺）。
4. **UnitRegistry**（architecture §2.2/Phase 5 开放问题-1 沿革）：快照轨落地后仍未需要全局登记（恢复按引用直连）——继续观察，回放轨立项时再评。

---

## 9. 测试用例表（JUnit 汇总；TDD 先行）

| 测试类（包） | 状态 | 覆盖要点（断言意图） |
|--------------|------|----------------------|
| `config/GameBalanceTest` | 改写+ | masteryExpToNext 五档/越界；MASTERY_* 常量对照 GDD §8.1 |
| `config/JsonLoaderHeroesTest` | 新建 | 合法解析/缺文件空表/8 类非法输入 fail 且报错路径精确 |
| `config/JsonLoaderScenesTest` | 改写+ | shopUnlocks 解析/缺省/悬空/Boss/跨场景重复/传奇互斥 |
| `config/JsonLoaderTest` | 改写+ | 真实资产计数（units 27/scenes 3/synergies 8/heroes 3）；零软告警 |
| `data/SceneDataTest` | 改写+ | shopUnlocks getter 不可变/兼容构造 |
| `entities/RunModifiersTest` | 新建 | 构造防御/EMPTY 语义/门控/修正块 |
| `entities/RunStateTest` | 改写+ | heroId·modifiers 透传/setRound 越界/兼容构造 |
| `entities/PlayerTest` | 改写+ | 复原构造校验/restoreRoster 长度与超席 |
| `entities/SequentialIdIssuerTest` | 改写+ | 复原续号/peekNext 不消耗/非法起始 |
| `utils/RandomGeneratorTest` | 改写+ | 流重放 1000 次逐位等价/负计数抛错 |
| `save/ProfileServiceTest` | 新建 | settle 升级链/封顶/登记/新解锁派生；runModifiers 六档三被动/门控池 |
| `save/ProfileCodecTest` | 新建 | round-trip/空档/版本/未知字段/非法 level |
| `save/ProfileStoreTest` | 新建 | 缺失 fresh/损坏重置/写读 round-trip（临时目录） |
| `save/SnapshotCodecTest` | 新建 | 富态 round-trip/**续战等价**/非备战捕获抛错/悬空四路 |
| `save/RunSettlementTextTest` | 新建 | 四形态行文案 |
| `systems/RunFlowSystemTest` | 改写 | :634 → 135；heroId 一致性 false；放弃 3 不变 |
| `systems/ShopSystemTest` | 改写+ | +5pp 权重（p3>0 生效轮/p3=0 轮不变）/RNG 恒 10/门控池/refreshCost/restoreSlots |
| `systems/SynergySystemTest` | 改写+ | 增幅重载三通道（ADD 取整/PCT 浮点/effect 浮点）/未列入零影响/2 参回归锚 |
| `systems/BattleSystemTest` | 改写+ | 玩家侧回能源/敌方隔离/薇拉增幅生效值/EMPTY 回归锚 |
| `systems/WaveGeneratorTest` | 改写+ | 墓穴/雪山生成与 minRound 门控 |
| `screens/RunSetupScreenTextTest` | 新建 | 三类被动文案/满级文案 |
| `screens/CodexScreenTextTest` | 新建 | 英雄四形态/场景三态文案 |
| `render/ui/ShopBarLogicTest` | 改写+ | 动态价签（Lv.5 → 刷新 1 金） |

---

## 10. 手验清单（lwjgl3:run，Windows 优先；§7 各任务验收引用）

| # | 流程 | 预期 |
|---|------|------|
| 1 | 冷启动（删 `assets/save/`） | 主菜单三按钮中无「继续远征」；Codex 全 Lv.1/仅森林已解锁 |
| 2 | 开始远征 → RunSetup | 英雄 3 卡（被动文案/熟练度 Lv.1）、场景 3 卡（墓穴/雪山灰置「通关「翡翠林地」解锁」） |
| 3 | 选格雷克 + 森林开局 | 顶栏金币 **14**（10 + Lv.1 2 + 被动 2——裁决 D2）；通知含第 1 轮开始 |
| 4 | 选薇拉开局并上阵 2 野兽 | 羁绊面板野兽 (2) 显示且战斗攻速生效值 = 基础 ×1.15×1.25（增幅叠加验证） |
| 5 | 选奥兰多开局 | 战斗中玩家单位受击/命中回能 = 10×1.15 / 5×1.15（飘字或通知间接验证） |
| 6 | 放弃远征（第 r 轮暂停菜单） | RunEndPanel：远征已放弃 + 抵达 r/25 + 「熟练度 +3r」+ Lv 行；`assets/save/profile.json` 出现且经验=r×3；RESTART 同英雄场景新 seed |
| 7 | 通关森林 25 轮（可用降速/控制台辅助） | 结算行「熟练度 +135」+「英雄等级 Lv.1 → Lv.2」+「解锁场景：亡者墓穴」；主菜单图鉴墓穴已解锁；RunSetup 墓穴可选 |
| 8 | 通关墓穴 | 雪山解锁（链式）；墓穴/雪山商店出现亡灵/巨人棋子（此前森林局不出现） |
| 9 | 格雷克练至 Lv.3（可反复放弃刷 3 经验/轮 + 通关 135） | 商店出现「王家军需官」（他英雄局不出现）；薇拉/奥兰多局各自传奇同理 |
| 10 | Lv.2 英雄商店抽验（第 10+ 轮） | 3 费出现频率肉眼提升（+5pp；1~9 轮无变化） |
| 11 | 备战期直接关窗重开 | 主菜单出现「继续远征」；进入后轮次/金币/名单/装备/商店/敌阵与关窗前一致；「同轮重试敌阵不变」仍成立 |
| 12 | 续玩后打完当轮胜利 | 快照更新到新轮（再关窗重开验证）；RNG 续流正确（商店/宝箱序列无重置迹象——续战等价单测的人工旁证） |
| 13 | RUN_END 后回主菜单 | 「继续远征」消失（快照已删）；图鉴熟练度已更新 |
| 14 | Battle/RESULT 期关窗 | 快照停留在最后一次备战期（回到该备战态，不回滚轮次也不前进） |
| 15 | 档案抗损：手工写坏 profile.json / run_snapshot.json | 启动/读档不炸，档案重置为初始、快照删除（System.err 有日志） |
| 16 | Escape/BACK/L 键、弹窗链、宝箱/详情/悬停 | Phase 5 全量回归无破坏（feedback01~07 行为不变） |
| 17 | Android 真机（T9） | 挂起恢复走快照、BACK 键链、横屏；存档落 internal storage 不入 APK |
| 18 | 文档回写后 markdown 表格渲染 | data_schema/render_design/architecture 三文件无断行错位 |

---

## 11. 附录：自动裁决摘要表（§4 速查索引）

| # | 问题一句话 | 选定方案一句话 |
|---|------------|----------------|
| D1 | Phase 6 是否切子阶段 | 不切（单期 18 CP），T4 内容/T7 快照为可裁剪任务线 |
| D2 | Lv.1 金币解锁 vs 格雷克被动重复 | 两线叠加（全英雄 +2、格雷克另 +2） |
| D3 | 熟练度漏通关 +60 | COMPLETED = 60 + 轮×3；ABANDONED 不变 |
| D4 | Lv.4/5 解锁空洞 | Lv.4 开局金 +3、Lv.5 刷新 -1（工作值待调） |
| D5 | +5pp 生效轮次 | 仅基础 3 费概率 > 0 轮，自 1 费扣减 |
| D6 | RunResultScreen 独立屏 | RunEndPanel 扩展 MVP，独立演出屏推 Phase 7 |
| D7 | 解锁状态存储 | completedScenes 派生，不落 unlocked 位 |
| D8 | 场景门控棋子机制 | scenes.json `shopUnlocks[]` 引用制（与英雄传奇互斥） |
| D9 | seed 手输 UI | 不做（无 Skin 资产） |
| D10 | 快照触发与范围 | 进 SHOPPING 即写 + pause/hide 补写；logicTick/notices 不存 |
| D11 | 档案写入时点 | RUN_END 首帧 Screen 观察触发，模拟域零 IO |
| D12 | 薇拉增幅口径 | 当档全效果 ×1.25；ADD 四舍五入、PCT/effect 浮点 |
| D13 | 奥兰多载体 | energyGainRate ADD +15 百分点（第 3 修正源，仅玩家侧） |
| D14 | 存档路径 | Gdx.files.local("save/")，JSON 明文；.gitignore assets/save/ |
| D15 | 新 Boss 技能 | 暂复用既有 11 技能，具名化 Phase 7 |
| D16 | 亡灵/巨人数值 | 自拟工作值（吸血线/血甲线） |
| D17 | 英雄被动词表 | HeroPassiveType 三值枚举（词表即代码） |
| D18 | 回放轨 | 推 Phase 7，history 现状不动 |
| D19 | 内容量级 | 最小集三场景齐发（3+3+3 单位、6 Boss、2 羁绊、3 传奇） |
| D20 | 坏档策略 | 删档重置 + 日志，不炸（与静态资源 fail-fast 区分） |

---

（完）

