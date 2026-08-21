# Phase 3 战斗引擎开发实施计划

> **日期**：2026-08-21
> **范围**：Phase 3 —— 战斗引擎（GDD §十二路线图 / project_structure §六出生时间表）
> **依据文档版本**：GDD V0.13、battle_design V1.6（战斗主设计）、data_schema V1.4、architecture V1.8、project_structure V1.1
> **状态**：📋 实施计划待审阅（未开工）
> **分支**：`feature/phase_3`
> **前序**：Phase 2 已交付 WaveSpec 敌阵规格（`coding_doc/2026-08-21_phase2_wave_generator.md:7`：104 例全绿）；本期由 BattleSystem 消费 WaveSpec 派生 BattleUnit

---

## 一、范围界定（开工前 Q1~Q4 已裁决，见 §三）

**做**：
- `entities/Unit`（名单实体，本期无装备槽——Q4）+ `entities/IdIssuer` 发号器接口占位（Q1）
- `entities/Player` 名单字段增补（兑现 `core/src/main/java/com/voidvvv/kz_auto_chess_n/entities/Player.java:6` 注释：bench 9 格 + 上场部署表 18 格）
- `entities/`：`BattleStats` / `StatModifierBlock` / `StatModifierSource` / `ActiveStatus` / `BattleUnit` / `Projectile` / `CombatEvent`（含 Type）/ `BattleOutcome` / `Side` / `BattleState`
- `systems/`：`StatPipeline`（两级属性管线）/ `SynergySystem` + `SynergySnapshot` / `TargetingSystem` / `MovementSystem` / `DamagePipeline` / `StatusSystem` / `SkillExecutor` / `ProjectileSystem` / `BattleSystem`（主循环）
- `tools/BattleConsoleMain`（控制台整场战斗模拟：打印事件流 + 结局，沿 `tools/WaveConsoleMain.java:21-56` 先例）
- `config/GameBalance` 增补 1 个常量（`MAX_INLINE_CAST_DEPTH`，拒绝魔法数字）
- 以上全部配套单元测试（TDD：RED → GREEN；本阶段为 project_structure §六 所称"测试大户"）

**不做**（防蔓延）：
- `RunState` / `UnitRegistry` / `command/` 命令系统（Phase 5 接入时出生，Q1 裁决）；人口上限校验、3 合 1、买卖规则随之推后
- `Delivery.LINE` 弹道的规则细化与实现——词表枚举保留（`data/Delivery.java:7`），技能执行器遇 LINE 抛错，测试夹具不构造 LINE 技能（Q2；与 battle §九待定清单一致）
- `Unit` 的装备三槽字段与装备修正源实现（Q4：管线按修正源列表设计，装备源 Phase 5 零改插入）
- 任何 Screen / 渲染 / `Main.java` 改造（Phase 4）；`CombatEvent` 对象池化（Phase 4 渲染接入前评估）
- gdx-ai（`core/build.gradle:5` 已有依赖，本期零 import——普通棋子无状态仲裁，battle §三）
- 商店 / 经济 / 宝箱（Phase 5）；亡语 / 死亡触发钩子（battle §九，清扫阶段已预留位）
- 种子 JSON 增补：`assets/data/skills.json` 现有 11 条技能 delivery 仅 MELEE_INSTANT / HOMING（`assets/data/skills.json:3-46`），足以覆盖本期全部机制验证，不新增内容

---

## 二、术语与约定（GDD / 设计文档 ↔ 代码标识符）

| 设计用语 | 代码标识符 | 备注 |
|----------|-----------|------|
| 名单实体 | `entities/Unit` | architecture §2.1；准不变，本期完全不可变 |
| 战斗实例 | `entities/BattleUnit` | 开战派生、战毕整体丢弃 |
| 战斗状态（棋盘归它） | `entities/BattleState` | GDD §10.2 注 / architecture §2.3 |
| 战斗系统（主循环） | `systems/BattleSystem` | `step` 60Hz 固定步 |
| 羁绊系统 | `systems/SynergySystem` | 双通道统计 + 替换制档位 |
| 第一级 / 基准快照 | `StatPipeline.deriveBaseline` → `BattleUnit.baseStats` | battle §8.1 |
| 第二级 / 有效属性 | `StatPipeline.deriveEffective` → `BattleUnit` 脏标记缓存 | battle §8.2 |
| 修正源（Q4 列表化） | `entities/StatModifierSource` / `StatModifierBlock` | 本期源：羁绊（星级并入 raw 阶段） |
| 活跃状态 | `entities/ActiveStatus` | battle §7.1 结构 |
| 事件流 | `entities/CombatEvent`（+ 内嵌 `Type`）/ `BattleOutcome` | battle §二事件表 |
| 逻辑弹道（锁定弹） | `entities/Projectile` + `systems/ProjectileSystem` | 仅 HOMING（Q2） |
| 伤害管线（唯一管线） | `systems/DamagePipeline` | battle §5.2，普攻与技能共用 |
| 状态系统 | `systems/StatusSystem` | battle §七统一框架（技能/装备/羁绊三入口） |
| 技能执行器 | `systems/SkillExecutor` | battle §六三步执行模型 |
| 索敌 | `systems/TargetingSystem` | battle §三优先链 |
| 移动（贪婪步） | `systems/MovementSystem` | battle §四离散跳格 |
| 发号器 | `entities/IdIssuer`（接口）+ `SequentialIdIssuer` | 单一 int id 空间（architecture §2.2） |
| 阵营 | `entities/Side`（PLAYER / ENEMY） | — |
| 敌阵规格 | `entities/WaveSpec`（Phase 2 既有） | 本期输入 |

**通用约定**：Java 标识符全英文（中文只进注释与 `@DisplayName`）；`data/entities` 零 `Gdx.*`（project_structure §四）；`BattleUnit` 等战斗态为"受控可变"（architecture §2.4 第三层的显式例外）；随机一律经注入 seed 的 `RandomGenerator`。

---

## 三、口径确认记录

### 3.1 用户裁决（Q1~Q4，原样记录，不得改回）

| # | 问题 | 用户裁决 |
|---|------|----------|
| Q1 | 交付物边界 | **选 B：按出生表。** 只建 `docs/project_structure_design.md:113` 所列：`Unit` / `BattleUnit` / `BattleState` / `BattleSystem` / `SynergySystem`，外加 `Player` 名单字段增补。战斗实例 id 用**注入的轻量发号器接口占位**；`RunState` / `UnitRegistry` 推迟到 Phase 5 接命令系统时出生 |
| Q2 | LINE 弹道 | **选 B：本期不做。** `Delivery.LINE` 词表枚举保留；技能执行器遇 LINE 技能抛错（测试夹具不构造 LINE 技能）；LINE 的射程上限/方向量化/棋盘边界/友军挡弹规则细化与实现随内容需要推后（与 battle §九待定清单一致） |
| Q3 | lifesteal 吸血 | **选 A：普攻触发。** 仅普攻直伤命中触发：回复 = 护甲后实际伤害 × lifesteal/100，不溢出 maxHp；DOT / 技能伤害 / 落空不触发（常规自走棋口径）。吸血环节补进 battle §5.2 伤害管线的实现设计中 |
| Q4 | 装备修正源 | **选 A：修正源列表。** `Unit` 本期不含装备槽字段；属性派生管线按"修正源列表"设计（本期两源：星级 + 羁绊），Phase 5 装备源作为新修正源插入时零改结算器 |

> 说明（Q4 的"两源"落位）：星级以 `raw = baseStats × starStatMultiplier × scale` 的模板缩放阶段并入第一级（battle §8.1 合成序 + Phase 2 交付的派生公式，`coding_doc/2026-08-21_phase2_wave_generator.md:145`），修正源列表承载 ADD/PCT 段（本期唯一源 = 羁绊）。装备源 Phase 5 只是向列表追加一个元素，`StatPipeline` 结算器零改动——满足裁决的字面与意图。

### 3.2 实现层口径（文档未明说、本次定的执行细节）

| # | 决定 | 依据/说明 |
|---|------|-----------|
| 1 | 棋盘 6×7 全场可通行可停留（含缓冲第 3 行与对方布阵区）；`grid[x][y]` 索引 [列][行]（沿 `WaveGenerator.java:63` 的 `occupied[x][y]` 先例，GDD §4.4 `boardGrid[6][7]`） | 敌区 0~2 与玩家区 4~6 不相邻，近战必须穿第 3 行才能接敌 |
| 2 | 主循环在 battle §二四阶段中**插入弹道推进**：①状态推进 → ②弹道推进 → ③逐单位行动 → ④死亡清扫 → ⑤胜负判定 | §二未列弹道相位；暴击 RNG 仅在发射时消耗，插入位置不影响 RNG 序，只影响到达伤害先于/后于本 tick 后续单位行动——定为"先落地"，与"伤害立即落地，后行动者看到最新血量"一致 |
| 3 | 行动链**严格互斥**（battle §二 if/else 链为准）：被控制→跳过 / 目标失效→重选 / 能量满→施放 / 射程内→出手 / 否则→走一步，每 tick 每单位至多一个行动。§5.1"同 tick 可先后各触发一次"解读为**两种计时器独立累计、互不重置、结转余数**，非一 tick 双动作 | 避免移动当 tick 即出手等效攻速膨胀；改回为"先移后攻"仅一行变更，留作数值调优开关 |
| 4 | 攻击/移动计时器每 tick 恒累计（与是否在射程无关），出手/走步消耗后**结转余数**；开局即就绪 | battle §5.1"开战即就绪（无前摇）"——计时器是冷却不是蓄力 |
| 5 | 能量封顶：延后施放期间能量钳制 100 不上溢；回能乘数取**获得者自身** `energyGainRate/100`；被控制期间回能完全冻结（+10/+5 均不获得） | GDD §6.5"眩晕时暂停积攒"；战歌号角类"全体友军回能 +15%"即每单位各自 ×1.15 |
| 6 | 技能直伤（DAMAGE 效果）与普攻同走唯一管线 → **同样触发攻守回能**（+10/+5）；HEAL / SHIELD / APPLY_STATUS / DOT / 落空不触发 | battle §5.2 回能步写在共用管线内部（`docs/battle_design.md:159`），按字面执行 |
| 7 | 数值精度：管线内 **float 全精度直存**（currentHp / energy / 事件 amount 均 float，无中间取整）；显示层取整留 Phase 4；断言用 AssertJ `isCloseTo` | 避免多次取整漂移；JVM 浮点运算按规范舍入，同 seed 回放逐位一致（配 `RandomGenerator` 位级确定） |
| 8 | StatusType→StatKey 修正映射**写死于 `StatPipeline`**：`ATK_UP→attack·PCT(+v)`、`ATK_DOWN→attack·PCT(−v)`、`ASPD_UP→attackSpeed·PCT(+v)`、`SLOW→moveSpeed·PCT(−v)`（v = 百分点）；STUN/BLEED/POISON/REGEN/SHIELD 非属性类 | WARNING #4 兑现；与 data_schema §5.4"ATK_UP 30 = +30%"对齐；SLOW 若未来要改为平格减速，只动映射表一处 |
| 9 | SHIELD 特殊吸收条目：`power` = 吸收点数；duration 缺省**无限**（战斗期常驻）；同类刷新取 `max(吸收量)`（不叠加）；被消耗至 ≤ 0 移除并打脏标记；DOT 真伤也先扣盾（仅跳过护甲公式） | battle §7.2 "同 type 不叠加" 对吸收量的自然延伸；"无视护甲"未含"穿盾" |
| 10 | DOT：施加时 `tickTimer=0`，首个心跳在满 1s 时（不立即结算）；每跳伤害 = `power`（= 施放时攻击力快照 × value，星级与 skillPower **不**缩放，data_schema §5.4）；真伤可致死；不触发回能。REGEN 同心跳节奏，`power` = maxHp 比例/跳 | data_schema §5.4"时长与强度固定"；battle §7.2 |
| 11 | 同 type 状态刷新：duration 取更长、power 取更大 | battle §7.2 只写了时长，power 对称延伸（防御性，语义一致） |
| 12 | 普攻载体按 range 推导：`range ≥ 2` → HOMING 锁定弹；`range ≤ 1` → MELEE_INSTANT 即时结算 | 模板无 delivery 字段（delivery 是 SkillData 的属性）；battle §5.3"适用：远程普攻" |
| 13 | 载荷冻结口径：发射/施放时冻结**攻击力快照 + 暴击标志 + 技能参数**（value × 星级缩放 × (1+skillPower/100) 的乘积）；护甲公式按**命中时**目标有效护甲结算 | battle §5.2 时序：roll 在发射、公式在命中（`docs/battle_design.md:154-157`） |
| 14 | 弹道推进：欧氏直线逼近目标**当前格中心**，速度 `PROJECTILE_SPEED`（6 格/秒）；本 tick 步长可达即命中；每次推进前目标已被清扫 → 立即消散；施放者被清扫**不影响**在途弹；战斗结束后在途弹随 BattleState 丢弃（不补发消散事件） | battle §5.3；"战斗结束在途弹道全部消散"表现为状态终结 |
| 15 | 胜负判定序：① 玩家侧全灭（**含同 tick 双灭，从严判负**）→ ENEMY_WIN；② 敌方全灭 → PLAYER_WIN；③ `elapsed ≥ 60s` → TIMEOUT（= 玩家判负，GDD §6.4） | H 语义延迟清扫使同 tick 双灭可达；TIMEOUT 独立枚举供 UI 区分文案 |
| 16 | id 发号序：玩家侧先（部署表扫描序 y↑x↑）→ 敌方后（WaveSpec 列表序 = 抽取序 + Boss 殿后）；行动序 = id 升序 → 同 tick 玩方单位先手 | 固定序确定性的最后一道保险（battle §二）；发号零 RNG 消耗 |
| 17 | 兽人 6 等开局效果（effect 通道，如 SHIELD 30% maxHp）对**两侧各自**结算（敌方凑齐门槛同样生效）；开局盾落地发 `SHIELDED` 事件（tick 0） | Phase 2 已为敌方同名凑对留口（有放回抽取，`coding_doc:145`） |
| 18 | LOWEST_ALLY 延后施放：能量保留于 100，此后**每 tick 重试**；SINGLE_TARGET / AOE 无有效主目标时同样延后（战场将终，理论短暂） | GDD §6.5"无可作用目标时延后施放，能量保留" |
| 19 | 就地施放重入保护：能量跨百触发的嵌套施放链深度上限 `MAX_INLINE_CAST_DEPTH = 16`，超限推迟到该单位下一行动 tick | 能量经济学证明常态深度远小于 16（每次施放清空自身 100 能量）；防御性保险 |
| 20 | 移动**不产** CombatEvent | battle §二事件表未列；渲染层只读 entities 轮询坐标插值（Phase 4） |
| 21 | `CombatEvent` 扁平结构 + 静态工厂；`STATUS_APPLIED` 的 `amount`=power、`amount2`=duration；SHIELD 落地统一发 `SHIELDED`（不发 STATUS_APPLIED，防双事件）；能量变化不入事件（常量 × 回能率可推导） | 实现 `equals/hashCode` 供确定性对拍（沿 `WaveSpec.java:43-68` 先例） |
| 22 | `BattleUnit` 的公开写方法标注 `framework-internal`（javadoc）：仅供 systems 包在战斗作用域内调用，列为 code review 检查项 | architecture §2.4 第三层"受控可变"的纪律化 |
| 23 | `BACKLINE` 纵深 = `\|y − 3\|`（到缓冲行第 3 行的距离），两侧对称成立 | battle §三索引细则的公式化（敌区 y∈0~2、玩家区 y∈4~6） |

---

## 四、现状盘点（file:line 均为本次实读）

### 可直接复用（零改动）

| 资产 | 位置 | 说明 |
|------|------|------|
| 战斗常量全套 | `core/src/main/java/com/voidvvv/kz_auto_chess_n/config/GameBalance.java:17-30` | LOGIC_STEP / BATTLE_TIMEOUT / CRIT_CHANCE / CRIT_MULTIPLIER / ENERGY_* / PROJECTILE_SPEED / RETARGET_INTERVAL / DOT_TICK_INTERVAL |
| 星级与技能缩放公式 | `config/GameBalance.java:77-87` | `starStatMultiplier(upgradeMultiplier, star)`、`skillStarScale(star)` |
| 棋盘尺寸 | `config/GameBalance.java:46-47` | BOARD_COLS=6 / BOARD_ROWS=7 |
| 确定性随机 | `utils/RandomGenerator.java:20-28` | `nextFloat()` 承载暴击 roll（消耗计数自动累计） |
| 词表枚举全套 | `data/StatKey.java:14-23`（9 键 + `isPercentScale()`）、`data/StatusType.java:4-13`、`data/Delivery.java:4-7`、`data/SkillShape.java:4-11`（含 AOE_2）、`data/TargetPriority.java:4-8`、`data/SkillEffectType.java`、`data/EffectOp.java:4-7`、`data/EffectTarget.java`、`data/SynergySource.java` | 三处同名（JSON/枚举/代码）铁律已就位 |
| 数据类公共构造器 | `data/UnitData.java:25`、`data/BaseStats.java:23`（9 键）、`data/SkillData.java:20`、`data/SkillEffect.java:22`、`data/EffectData.java:21`、`data/SynergyData.java:23` | 测试夹具可直构，无需 JSON |
| 羁绊档位判定 | `data/SynergyData.java:44-54` | `activeThreshold(count)` 替换制语义已实现并有测试 |
| 敌阵输入 | `entities/WaveSpec.java:15-38` | template 直接引用 / star / scale / gridX / gridY |
| 静态数据聚合 | `data/GameData.java:34-42` | getUnit / getSkill / getSynergy（LinkedHashMap 声明序） |
| 加载入口 | `config/JsonLoader.loadFromDirectory`（用法见 `tools/WaveConsoleMain.java:32`） | 控制台工具直接复用 |
| 控制台先例 | `tools/WaveConsoleMain.java:21-56` | 普通 main、纯 JVM FileHandle、args 约定 |

### 需改造（仅 2 处生产代码）

| 文件 | 变更 |
|------|------|
| `entities/Player.java` | 兑现 `:6` 注释：增名单字段（bench 9 格 + 部署表 18 格）与最小存取方法（见 §7.1） |
| `config/GameBalance.java:33` 附近 | 增 1 常量 `MAX_INLINE_CAST_DEPTH = 16`（口径 #19） |

### 需新建

见 §六变更清单。

---

## 五、总体技术方案

### 5.1 架构分层与数据流

无渲染纯逻辑层（headless），全部由 JUnit 直测（零后端原则，project_structure §五）。组合根为 `BattleSystem`：以构造器组装六个子系统，`startBattle` 产出 `BattleState`，`step` 按 60Hz 固定序推进，事件流是唯一正式产出。

- 数据流图：`../diagrams/battle_dataflow.md`（`.html` 同名浏览器版，双击打开）
- 主循环图：`../diagrams/battle_main_loop.md`
- 属性管线图：`../diagrams/stat_pipeline.md`
- 伤害管线图：`../diagrams/damage_pipeline.md`

### 5.2 与设计文档的差异声明（如实记录，不改文档）

| 文档原文 | 本实施 | 理由 |
|----------|--------|------|
| GDD §10.2 `BattleUnit` 示意为纯 final 快照 + final SkillData（`docs/gdd_idea_0.0.0.1.md:421-435`） | 按 battle §八两级管线：基准 `baseStats` final + 有效属性缓存受控可变 | WARNING #2 既定：以 battle_design 为准（GDD §10.3 已立"字段以 data_schema 为准"的同款示意声明先例，`gdd:461-463`） |
| GDD §10.2 `Unit` 含三装备槽（`gdd:417`） | 本期无装备字段 | Q4 裁决 |
| GDD §10.2 `WaveGenerator` 返回 `List<BattleUnit>`（`gdd:453`） | Phase 2 已定返回 `List<WaveSpec>`，本期由 BattleSystem 派生 | Phase 2 Q1 既定 + 本期 Q1 重申 |
| battle §二主循环四阶段 | 五阶段（插入"弹道推进"） | 实现层口径 #2 |
| battle §5.1"同 tick 可先后各触发一次" | 互斥单行动 + 计时器独立结转 | 实现层口径 #3 |
| GDD §10.2 `Player` 含 `List<Unit> bench`（`gdd:443`） | bench 为 List + 部署表为 18 格数组（位置即数据，战斗派生需要坐标） | GDD §10.2 注"上场部署表归 Player"（`gdd:445-446`） |

---

## 六、变更清单

### 新增：`core/src/main/java/com/voidvvv/kz_auto_chess_n/`

| 文件 | 包 | 职责 | 预估行数 |
|------|-----|------|---------|
| `entities/Unit.java` | entities | 名单实体（不可变）：id + 模板引用 + 星级 | ~70 |
| `entities/IdIssuer.java` | entities | 发号器接口（Q1 占位，Phase 5 归 RunState） | ~15 |
| `entities/SequentialIdIssuer.java` | entities | 顺序发号默认实现（测试/控制台用） | ~30 |
| `entities/Side.java` | entities | PLAYER / ENEMY | ~10 |
| `entities/BattleOutcome.java` | entities | PLAYER_WIN / ENEMY_WIN / TIMEOUT + `playerWon()` | ~20 |
| `entities/StatModifierBlock.java` | entities | 按 StatKey 聚合的 ΣADD/ΣPCT（不可变，`plus` 合并） | ~120 |
| `entities/StatModifierSource.java` | entities | 修正源接口（Q4） | ~15 |
| `entities/BattleStats.java` | entities | 不可变 9 键属性块（float） | ~110 |
| `entities/ActiveStatus.java` | entities | 状态实例（battle §7.1 结构，受控可变） | ~80 |
| `entities/BattleUnit.java` | entities | 战斗实例：身份/基准不可变 + 受控战斗态 | ~260 ⚠ |
| `entities/Projectile.java` | entities | 在途锁定弹 + 冻结载荷 | ~100 |
| `entities/CombatEvent.java` | entities | 纯数据事件（内嵌 Type 枚举 + 9 静态工厂 + equals） | ~190 |
| `entities/BattleState.java` | entities | 一场战斗的全部状态 + 只读查询 | ~190 |
| `systems/SynergySnapshot.java` | systems | 羁绊结算产物（实现 StatModifierSource + 开局效果） | ~100 |
| `systems/SynergySystem.java` | systems | 双通道统计 → 替换制档位 → 快照 | ~130 |
| `systems/StatPipeline.java` | systems | 两级派生 + StatusType 映射表（口径 #8） | ~190 |
| `systems/TargetingSystem.java` | systems | findTarget / retargetAll / retargetOnDeath | ~120 |
| `systems/MovementSystem.java` | systems | 贪婪跳格（平局 上>下>左>右） | ~110 |
| `systems/DamagePipeline.java` | systems | 唯一伤害管线 + 能量 + 吸血（Q3）+ 真伤/治疗 | ~180 |
| `systems/StatusSystem.java` | systems | 挂载/刷新 + tick 推进（DOT/REGEN/时长） | ~200 ⚠ |
| `systems/SkillExecutor.java` | systems | 三步执行：shape→载体→效果；LINE 抛错（Q2） | ~240 ⚠ |
| `systems/ProjectileSystem.java` | systems | 弹道推进与命中分发 | ~150 |
| `systems/BattleSystem.java` | systems | 组合根：startBattle / step / runToEnd | ~340 ⚠ |
| `tools/BattleConsoleMain.java` | tools | 控制台整场模拟（事件流 + 结局） | ~120 |

⚠ = 触线风险文件，§7 已预留拆分位；全部文件遵循 ≤400 行目标、800 硬上限。

### 修改

| 文件 | 变更 |
|------|------|
| `entities/Player.java` | 名单字段增补（§7.1） |
| `config/GameBalance.java` | +`MAX_INLINE_CAST_DEPTH` |

### 接线点（精确位置）

- `entities/Player.java:14`（`currentExp` 字段后）插入名单字段；方法追加于 `addExp` 之后
- `config/GameBalance.java:33`（`MAX_EFFECTS_PER_SKILL` 后）插入新常量
- 其余全部为新建文件；`Main.java` 不动（Phase 4）

---

## 七、详细设计

### 7.1 `entities/Unit` 与 `entities/Player` 增补

```java
/** 名单实体（architecture §2.1）：模板引用 + 星级。本期完全不可变——
 *  装备槽（Q4 裁决）与升星替换（3 合 1 为 Phase 5 BuyUnit 的系统后果）均推迟。 */
public final class Unit {
    private final int id;            // IdIssuer 发号（单一 id 空间，architecture §2.2）
    private final UnitData template; // 直接引用（模板终身只读，沿 WaveSpec.java:16 先例）
    private final int star;          // 1~3，构造校验

    public int getId();
    public UnitData getTemplate();
    public int getStar();
    // equals/hashCode 按 id（id 空间全局唯一）
}
```

```java
// Player.java 增补（业务校验——人口上限 vs 等级、买卖规则——归命令层 Phase 5，此处只做存储完整性）
private final List<Unit> bench = new ArrayList<Unit>();     // 备战席 ≤ BENCH_SIZE(9)
private final Unit[] deployment = new Unit[BOARD_COLS * 3]; // 玩家区 18 格，索引 (y-4)*BOARD_COLS + x

public List<Unit> getBench();                 // 不可变视图
public void addToBench(Unit unit);            // 满员抛 IllegalStateException（防御兜底）
public void removeFromBench(Unit unit);       // 不在席抛 IllegalArgumentException
public void deploy(Unit unit, int gridX, int gridY); // 玩家区越界/占用抛错；自动从 bench 摘除
public void undeploy(int gridX, int gridY);   // 清格并回 bench
public Unit deployedAt(int gridX, int gridY);
public List<Unit> getDeployedUnits();         // 扫描序 y↑x↑ —— 确定性序 = 开战发号序（口径 #16）
public int getRosterSize();                   // bench + deployed
```

### 7.2 属性管线（`StatModifierBlock` / `BattleStats` / `StatPipeline`）

```java
/** 按 StatKey 聚合的修正块（不可变；plus 返回新对象） */
public final class StatModifierBlock {
    public static StatModifierBlock empty();
    public static StatModifierBlock of(StatKey key, EffectOp op, float value); // ADD/PCT 分别累计
    public StatModifierBlock plus(StatModifierBlock other);
    public float addOf(StatKey key);   // ΣADD
    public float pctOf(StatKey key);   // ΣPCT（百分点）
    public boolean isEmpty();
}

/** 修正源接口（Q4）：本期唯一实现 = SynergySnapshot；Phase 5 装备源追加即插 */
public interface StatModifierSource { StatModifierBlock modifiers(); }

/** 不可变 9 键属性块（float；百分比键仍以百分点存储，结算处 ÷100） */
public final class BattleStats { /* hp/attack/armor/attackSpeed/moveSpeed/range/lifesteal/energyGainRate/skillPower */ }
```

```java
public final class StatPipeline {
    /** 第一级基准（battle §8.1）：
     *  raw = 模板值 × GameBalance.starStatMultiplier(upgradeMultiplier, star) × scale
     *  基准 = (raw + Σ sources.ADD) × (1 + Σ sources.PCT)   —— 先加后乘，顺序写死 */
    public static BattleStats deriveBaseline(UnitData template, int star, float scale,
                                             List<StatModifierSource> sources)

    /** 第二级有效（battle §8.2）：(基准 + Σ状态ADD) × (1 + Σ状态PCT) */
    public static BattleStats deriveEffective(BattleStats base, StatModifierBlock statusModifiers)

    /** StatusType → StatKey 映射（口径 #8，非属性类返回 empty） */
    public static StatModifierBlock statusModifiers(List<ActiveStatus> statuses)
}
```

### 7.3 羁绊结算（`SynergySystem` / `SynergySnapshot`）

```java
public final class SynergySystem {
    /** 双通道统计：RACE 按 race、CLASS 按 unitClass；仅匹配已登记 synergy.key 的值计数
     *  （风味值不计，data_schema §六 V1.3）；每单位在其种族羁绊与职业羁绊各计 1 次。
     *  各羁绊取 SynergyData.activeThreshold(count)（替换制，SynergyData.java:44-54）。 */
    public SynergySnapshot resolve(Collection<UnitData> templates, GameData data)
    // 玩家侧：BattleSystem 由 getDeployedUnits() 映射 getTemplate()；敌方侧：WaveSpec.getTemplate()
}

public final class SynergySnapshot implements StatModifierSource {
    // actives: List<ActiveSynergy>（synergyId/name/thresholdCount —— 供 UI 与测试断言）
    // statModifiers: StatModifierBlock（stat 通道 ΣADD/ΣPCT）
    // openingEffects: List<EffectData>（effect 通道，如兽人6 SHIELD 0.3 —— startBattle 落地，口径 #17）
    public static final SynergySnapshot EMPTY;
}
```

### 7.4 战斗实体

```java
/** battle §7.1 结构；受控可变 */
public final class ActiveStatus {
    private final StatusType type;
    private final int sourceId;
    private float remainingTime;  // SHIELD 无限 = Float.POSITIVE_INFINITY
    private float tickTimer;      // DOT/HOT 1s 心跳累积
    private float power;          // DOT=每跳点数 / REGEN=maxHp比例 / 属性类=百分点 / SHIELD=吸收点数
}

public final class BattleUnit {
    // —— 身份与基准（不可变）——
    private final int id;
    private final UnitData template;
    private final int star;
    private final Side side;
    private final SkillData skill;        // GameData.getSkill(template.getSkillId())，加载校验保证非 null
    private final BattleStats baseStats;  // 第一级基准
    // —— 战斗态（受控可变，architecture §2.4；写方法 framework-internal，口径 #22）——
    private float currentHp;
    private float energy;                 // 0~100，封顶口径 #5
    private int gridX, gridY;
    private int targetId;                 // -1 = 无
    private float attackTimer, moveTimer; // 独立累计结转（口径 #3/#4）
    private final List<ActiveStatus> statuses = new ArrayList<ActiveStatus>();
    private BattleStats effectiveStats;   // 脏标记缓存
    private boolean statsDirty;
    private boolean cleaned;              // 已清扫（死亡标记）

    // 读：getId/getTemplate/getStar/getSide/getSkill/getBaseStats/getCurrentHp/getEnergy/
    //    getGridX/getGridY/getTargetId/getStatuses(不可变视图)/isCleaned/
    //    getEffective(StatKey)（脏则重算）/ hasControl()/isAlive()(!cleaned，濒死未清扫亦"活")/
    //    hpRatio()/attackInterval()/moveCooldown()
    // 写（framework-internal）：setPosition/setTargetId/advanceTimers/consumeAttackTimer/
    //    consumeMoveTimer/modifyHp/modifyEnergy/setEnergy/addStatus/removeStatus/markCleaned/
    //    invalidateStats/recomputeEffective
}

/** 在途锁定弹（HOMING 唯一形态，Q2） */
public final class Projectile {
    private final int sourceId;
    private final int targetId;
    private final float posX, posY;          // 连续坐标（格单位），出生 = 施放者格中心
    private final float attackSnapshot;      // 出手时有效攻击快照（口径 #13）
    private final boolean crit;              // 普攻发射时已 roll
    private final SkillData skill;           // null = 普攻弹
    private final float skillMultiplier;     // 技能：value × skillStarScale × (1+skillPower快照/100)；普攻 = 1
    // 写：advance(dt, targetX, targetY)（朝目标当前格中心逼近；是否到达由 ProjectileSystem 判定）
}

/** 纯数据事件（battle §二事件表 9 类；口径 #21） */
public final class CombatEvent {
    public enum Type { ATTACK_LAUNCHED, HIT, CAST, STATUS_APPLIED, HEALED, SHIELDED,
                       UNIT_DIED, PROJECTILE_FIZZLED, BATTLE_ENDED }
    // tick / type / sourceId / targetId / amount / amount2 / crit / statusType? / skillId? / outcome?
    // 9 个静态工厂 + 全只读 getter + equals/hashCode（确定性对拍）+ toString（控制台）
}

public final class BattleState {
    private final List<BattleUnit> units;         // 构造序 = id 升序（口径 #16）
    private final BattleUnit[][] grid;            // [x][y]，null = 空（GDD §4.4）
    private final List<Projectile> projectiles;
    private final List<CombatEvent> events;       // 追加式
    private final RandomGenerator rng;
    private final SynergySnapshot playerSynergies; // 供 UI（Phase 5）与测试
    private final SynergySnapshot enemySynergies;
    private int tick;
    private float elapsed;
    private boolean over;
    private BattleOutcome outcome;
    // 查询：getUnits/getUnitById/unitAt/aliveUnits(Side)/aliveCount(Side)/getProjectiles/
    //      getEvents(不可变视图)/getTick/getElapsed/isOver/getOutcome/getRng/getPlayerSynergies/getEnemySynergies
    // 写（framework-internal）：placeUnit/removeFromGrid/spawnProjectile/removeProjectile/
    //      beginTick()/record(event)/finish(outcome)
}
```

### 7.5 子系统

```java
public final class TargetingSystem {
    /** 优先级：specialPriority != null ? specialPriority : defaultPriority（UnitData.java:52-53）
     *  NEAREST=min曼哈顿 | BACKLINE=max|y-3|（口径 #23）| LOWEST_HP=min hpRatio | HIGHEST_ATK=max 有效attack
     *  候选 = 存活敌方（含濒死未清扫）；平局链：距离 → id 升序；无候选返回 null */
    public BattleUnit findTarget(BattleState state, BattleUnit self);
    public void retargetAll(BattleState state);                    // 每 120 tick 全局强制（id 序）
    public void retargetOnDeath(BattleState state, int deadId);    // 清扫后立即重选（battle §三）
}

public final class MovementSystem {
    /** 贪婪步：4 邻空格取到目标曼哈顿最小者；平局 上>下>左>右；无递减空格 → 等待。
     *  全场 6×7 可通行（口径 #1）；同 tick 抢格由行动序天然解决（先行动者先占）。 */
    public boolean tryStep(BattleState state, BattleUnit mover, BattleUnit target);
}

public interface CastTrigger { void tryCast(BattleState state, BattleUnit caster); }

public final class DamagePipeline {
    public DamagePipeline(CastTrigger castTrigger);
    /** 唯一伤害管线（battle §5.2 + Q3 吸血）：
     *  伤害 = attackPower × multiplier × (crit ? 1.5 : 1) × 100/(100 + 目标命中时有效护甲)
     *  → 先扣盾 → 扣 HP（溢出作废）→ Hit 事件
     *  → 攻击者 +10×回能率 / 受击者 +5×回能率（口径 #5/#6；控制期冻结）
     *  → basicAttack 时吸血：实际伤害 × lifesteal/100，cap maxHp，Healed 事件（Q3） */
    public void applyDirectHit(BattleState state, BattleUnit attacker, BattleUnit target,
                               float attackPower, float multiplier, boolean crit,
                               boolean basicAttack, String skillId);
    /** DOT 真伤：无视护甲、先扣盾、可致死、无回能无吸血（口径 #10） */
    public void applyTrueDamage(BattleState state, BattleUnit source, BattleUnit target, float amount);
    /** 治疗：cap maxHp、溢出作废、Healed 事件 */
    public void applyHeal(BattleState state, BattleUnit target, float amount);
    /** 回能：乘获得者 energyGainRate/100、控制期冻结、跨 100 回调 castTrigger（口径 #19 深度保护） */
    public void gainEnergy(BattleState state, BattleUnit unit, float baseAmount);
}

public final class StatusSystem {
    public StatusSystem(DamagePipeline damagePipeline);
    /** 挂载/刷新：同 type 不叠加；duration 取更长、power 取更大（口径 #10/#11）；
     *  属性类挂载即 invalidateStats；SHIELD 走吸收条目发 SHIELDED（口径 #9） */
    public void apply(BattleState state, BattleUnit target, StatusType type,
                      float power, float duration, int sourceId);
    /** 阶段①：DOT/REGEN 1s 心跳（DOT 走 applyTrueDamage，可致死）· 时长递减 · 到期移除+脏标记 */
    public void tickStatuses(BattleState state, float dt);
}

public final class SkillExecutor {
    public SkillExecutor(DamagePipeline damagePipeline, StatusSystem statusSystem);
    /** 施放（能量满或就地触发）：
     *  ① shape 解析目标（SINGLE_TARGET=锁定目标 / SELF / LOWEST_ALLY=HP%最低友军含自己，全满延后 /
     *     ALL_ALLIES / AOE_1·AOE_2=落点几何 / ALL_ENEMIES）
     *  ② 载体投送：MELEE_INSTANT 即时 / HOMING 冻结载荷 spawn 弹 / LINE → UnsupportedOperationException（Q2）
     *  ③ 逐效果应用：DAMAGE→管线(multiplier=快照) / HEAL·SHIELD→maxHp×value×星级×(1+skillPower/100) /
     *     APPLY_STATUS→statusSystem.apply（DOT power=施放时攻击快照×value）
     *  施放成功：能量清零、Cast 事件（skillId+主目标）；延后：保留能量返回 false（口径 #18）
     *  防御：ally 形状遇 HOMING → IllegalStateException；普攻载体规则见口径 #12 */
    public boolean cast(BattleState state, BattleUnit caster);
    /** HOMING 技能弹到达后的落点应用（AOE 以命中点=目标到达时刻所在格；单体以目标为对象） */
    public void applyAtImpact(BattleState state, BattleUnit caster, BattleUnit impactTarget,
                              SkillData skill, float multiplier);
}

public final class ProjectileSystem {
    public ProjectileSystem(DamagePipeline damagePipeline, SkillExecutor skillExecutor);
    /** 阶段②：推进所有在途弹（口径 #14）；到达：
     *  普攻弹 → damagePipeline.applyDirectHit(快照, 1, crit, basicAttack=true, null)
     *  技能弹 → skillExecutor.applyAtImpact(...)；目标已清扫 → ProjectileFizzled 消散 */
    public void advanceAll(BattleState state, float dt);
}
```

### 7.6 组合根 `BattleSystem`

```java
public final class BattleSystem {
    // 构造器组装（无构造环：DamagePipeline 收 CastTrigger 接口，由本类方法引用延迟绑定）
    private final TargetingSystem targeting = new TargetingSystem();
    private final MovementSystem movement = new MovementSystem();
    private final DamagePipeline damagePipeline;   // new DamagePipeline(this::tryCastInline)
    private final StatusSystem statusSystem;       // new StatusSystem(damagePipeline)
    private final SkillExecutor skillExecutor;     // new SkillExecutor(damagePipeline, statusSystem)
    private final ProjectileSystem projectileSystem;

    /** 开战（tick 0，零 RNG 消耗）：
     *  玩家侧 getDeployedUnits()（扫描序）+ 敌方 WaveSpec 列表序 → IdIssuer 发号（口径 #16）
     *  → 两侧 SynergySystem.resolve → StatPipeline.deriveBaseline（scale：玩家 1.0 / 敌方 spec.getScale()）
     *  → BattleState 布格 → 开局效果落地（openingEffects，口径 #17）
     *  → HP=maxHp、能量 0、计时器就绪 → 按 id 序初始 findTarget */
    public BattleState startBattle(Player player, List<WaveSpec> enemyWave,
                                   GameData data, RandomGenerator rng, IdIssuer idIssuer);

    /** 推进一个 LOGIC_STEP（五阶段固定序，见 ../diagrams/battle_main_loop.md）：
     *  beginTick（每 120 tick 先全局 retargetAll）→ ①statusSystem.tickStatuses
     *  → ②projectileSystem.advanceAll → ③逐单位行动（id 序，互斥链，普攻发射处 roll 暴击——
     *  RNG 消耗序=发射序，architecture §六第 4 点）→ ④死亡清扫（markCleaned/腾格/状态销毁/
     *  UnitDied/retargetOnDeath）→ ⑤判定（口径 #15） */
    public void step(BattleState state);

    /** 便利推进至战斗结束（runToEnd(state, maxTicks) 上限防御）；控制台与测试用 */
    public void runToEnd(BattleState state, int maxTicks);
}
```

### 7.7 `tools/BattleConsoleMain`

```java
/** args[0]=seed（缺省 42）；args[1]=round（缺省 5，越过 minRound 门控有杂兵多样性）；
 *  args[2]=dataDir（缺省 ../assets/data）。沿 WaveConsoleMain 先例：纯 JVM FileHandle、不动 Main.java。 */
public static void main(String[] args);
// 流程：loadFromDirectory → 软告警 → rng → generateEnemyWave(round, scene_forest, data, rng)
//      → Player（10 金）+ 固定演示部署（近战行 5 / 远程行 6，1 星三兵：战士/刺客/游侠）
//      → new SequentialIdIssuer() → startBattle → runToEnd
// 输出：① 双方阵容表（id/名称/阵营/基准属性） ② 逐条事件流（tick | type | src→tgt | amount | 附加）
//      ③ 结局 + tick 数 + RNG 消耗合计（= 波次生成消耗 + 暴击 roll 次数）
```

---

## 八、TDD 测试计划（RED → GREEN → REFACTOR）

新增测试镜像包结构；共享夹具 `core/src/test/java/com/voidvvv/kz_auto_chess_n/systems/support/BattleTestFixtures.java`（默认模板/技能构造器、微型 GameData、快速终局单位——高攻低速对拍用）。**全部夹具不构造 LINE 技能**（Q2）。

| 测试类 | 预估例数 | 覆盖要点 |
|--------|---------|---------|
| `entities/UnitTest` | ≈5 | 字段只读、star 边界（0/4 抛错）、equals 按 id |
| `entities/SequentialIdIssuerTest` | ≈2 | 递增发号、独立实例互不干扰 |
| `entities/PlayerTest`（扩展） | +≈9 | bench 增删/满员抛错；deploy 越界（y=3/y=7/x=6）/占用抛错；deploy 自动摘 bench；undeploy 回席；getDeployedUnits 扫描序 y↑x↑；rosterSize；存量 6 例不动 |
| `systems/StatPipelineTest` | ≈14 | raw=模板×星级×scale 抽检（1/2/3 星、k=1.4/3.4/1.0）；先加后乘合成序（ADD 与 PCT 并存时的唯一正确值）；多修正源合并（羁绊+模拟装备源——验证 Q4 零改插入）；HP 键作用于 maxHp；第二级公式；ATK_UP/ATK_DOWN/ASPD_UP/SLOW 映射值与符号；同 type 双状态不叠加修正；empty 块恒等；9 键齐全 |
| `systems/SynergySystemTest` | ≈12 | 双通道计数（种族+职业同时计）；同名重复各计 1；风味值不计；档位替换制（2/4/6 取最高档全量）；未达最低档为空；stat 通道 ΣADD/ΣPCT；effect 通道（SHIELD）进 openingEffects；EMPTY 常量；actives 含档位信息；快照不可变；敌方侧（WaveSpec 模板集）同一套语义 |
| `entities/BattleUnitTest` | ≈8 | 基准不可变；getEffective 脏标记重算（改状态前后值变化）；hasControl；isAlive 语义（hp≤0 未清扫仍 true）；maxHp 降低 currentHp 钳制；attackInterval/moveCooldown 换算；markCleaned 后状态清空 |
| `entities/BattleStateTest` | ≈6 | placeUnit/unitAt 记账一致；removeFromGrid 腾格；events 视图不可变且追加生效；beginTick 计数；finish 置 outcome 后 isOver |
| `systems/TargetingSystemTest` | ≈12 | 四种优先级各正确选靶；每种的平局链（距离→id）；排除友方与已清扫单位；濒死未清扫仍是合法目标；BACKLINE 纵深 \|y-3\| 两侧对称；无候选 null；retargetAll 按 id 序；retargetOnDeath 只影响指向亡者 |
| `systems/MovementSystemTest` | ≈10 | 朝目标贪婪步；平局上>下>左>右；四邻全被占→等待不动；穿缓冲行第 3 行；停留对方布阵区；grid 记账同步；先行动者占格后后者改选；移动方向不增距即等待；连续多步逼近路径 |
| `systems/DamagePipelineTest` | ≈14 | 护甲公式（含 0 甲/高甲）；暴击 ×1.5；倍率乘序；先扣盾→盾尽透血→溢出作废；攻守回能（+10/+5×回能率、115→×1.15）；控制期回能冻结；能量封顶 100；**吸血（Q3）：普攻命中回复=护甲后伤害×lifesteal/100、不溢出、技能/DOT/落空不触发**；applyTrueDamage 无视护甲可致死先扣盾；applyHeal 溢出作废；Hit/Healed/SHIELDED 事件字段；CastTrigger 跨百回调 |
| `systems/StatusSystemTest` | ≈14 | 同 type 刷新 duration 取长/power 取大；不同 type 独立；属性类挂载即脏标记；到期移除恢复属性；DOT 首跳在 1s（非立即）、每秒一跳、可致死、无回能；REGEN 按比例回；SHIELD 吸收/耗尽移除/同类取大；STUN 控制；死亡销毁全部状态；STATUS_APPLIED 事件（amount=power、amount2=duration） |
| `systems/SkillExecutorTest` | ≈16 | 七种 shape 各自目标集合；LOWEST_ALLY 全满延后（能量保留、下 tick 重试成功）；**LINE 抛 UnsupportedOperationException（Q2）**；ally 形状+HOMING 抛 IllegalStateException；DAMAGE 走护甲公式且带 skillId；HEAL/SHIELD = maxHp×value×星级×(1+skillPower/100) 抽检验算；APPLY_STATUS 时长/强度不随星级；DOT power=施放时攻击快照×value；AOE_1=落点+4邻、AOE_2=13格菱形（边界格）；HOMING 冻结载荷并 spawn 弹；Cast 事件主目标口径；施放清零能量；控制期不施放 |
| `systems/ProjectileSystemTest` | ≈10 | 追踪目标移动后的格（改向）；6格/秒到达 tick 数算例；普攻弹命中=快照×当前护甲；暴击标志原样落地；目标清扫→Fizzled 无伤害无能量；施放者清扫弹仍命中；多弹互不碰撞；MELEE_INSTANT 技能不产弹 |
| `systems/BattleSystemTest` | ≈22 | 开局：基准快照终值抽检（含羁绊源+星级+scale 三合一）、开局盾（兽人6 两侧）、初始索敌、HP=maxHp；主循环：行动序=id 升序；互斥行动链；计时器结转（高攻速不掉次数）；能量满自动施放（含"施放发生在他人攻击结算中间"的就地语义——受击者跨百当 tick 反打）；**H 语义互秒**（同 tick 双双 HP≤0 均进清扫、BattleEnded=ENEMY_WIN 从严）；濒死未清扫仍可行动；死亡腾格+立即重选；120 tick 全局重评估（LOWEST_HP 切火）；超时 60s→TIMEOUT；敌方全灭→PLAYER_WIN；**确定性：同 seed 同输入两次 runToEnd 事件流逐位 equals**；事件流含全部 9 类事件的出现时机；零 RNG 消耗断言（除暴击 roll：构造 0% 暴击夹具对比消耗数） |
| 合计 | **≈153** | 存量 104 例不动，全量预期 ≈257 |

**TDD 纪律**：每任务先写测试（RED）→ 最小实现（GREEN）→ 重构（IMPROVE）；战斗时序类测试用"快速终局"夹具（高攻/低速/少血）压缩 tick 数，确定性回放测试设 `runToEnd` 上限防死循环。

---

## 九、验收标准

1. `gradlew core:test` 全绿：存量 104 例保持全绿 + 新增 ≈153 例（以全绿为准，不设硬数）
2. `tools.BattleConsoleMain`（seed=42、round=5）完整输出一场战斗：阵容表 → 事件流（含 AttackLaunched/Hit/Cast/Healed 或 Shielded/UnitDied/BattleEnded）→ 结局与 RNG 消耗；**同 seed 两次运行输出逐位一致**
3. RNG 审计：整场消耗 = 波次生成消耗（round=5 为 3）+ 暴击 roll 次数（控制台打印并在文档记录实测值）
4. 确定性：同 seed 同输入两次 `startBattle`+`runToEnd` 事件流 `equals`（测试断言 + 控制台人工 diff）
5. Q2 合规：`Delivery.LINE` 无任何执行路径（执行器抛错有测试），全部测试夹具零 LINE 构造
6. 分层约束：新增类零 `Gdx.*` 调用（`tools` 的纯 JVM `FileHandle` 例外沿 `WaveConsoleMain.java:19` 先例）
7. 行数红线：新增文件 ≤400 行（⚠ 文件触线时按预留拆分位再切，不超过 800 硬上限）
8. 口径抽查：吸血（Q3）、技能直伤回能（口径 #6）、超时判负（口径 #15）、互斥行动链（口径 #3）与本文档 §三 一致

---

## 十、实现顺序（建议提交切分，8 个 feat 提交）

| 步 | 提交内容 | 依赖 |
|----|---------|------|
| 1 | `Unit` + `IdIssuer`/`SequentialIdIssuer` + `Player` 名单增补（含 PlayerTest 扩展） | 无 |
| 2 | `StatModifierBlock`/`StatModifierSource`/`BattleStats` + `StatPipeline`（含映射表） | 1 |
| 3 | `SynergySystem` + `SynergySnapshot` | 2 |
| 4 | `ActiveStatus`/`Side`/`BattleOutcome`/`BattleUnit` + `CombatEvent` + `BattleState`（含 `GameBalance` 常量） | 2 |
| 5 | `TargetingSystem` + `MovementSystem` | 4 |
| 6 | `DamagePipeline` + `StatusSystem`（Q3 吸血入管线） | 4 |
| 7 | `SkillExecutor` + `ProjectileSystem`（Q2 LINE 抛错） | 6 |
| 8 | `BattleSystem` 主循环整合 + `BattleConsoleMain` + 全量验收（§九） | 5/6/7 |

> 每步完成时 `core:test` 必须全绿（测试与实现同提交，沿 Phase 2 切分纪律）。

---

## 十一、风险与开放问题

### WARNING 级既定发现（已按口径落地，无需再求证）

| # | 项 | 处置 |
|---|----|------|
| 1 | 缓冲带第 3 行通行性 | 按"可通行可停留"实现（口径 #1）；若设计日后改为禁停，仅动 `MovementSystem` 一处判定 |
| 2 | GDD §10.2 与 battle §八的 BattleUnit 结构冲突 | 以 battle_design 为准，§5.2 差异声明已记录 |
| 3 | 能量封顶 / 技能回能 / 取整三口径 | 口径 #5/#6/#7 定案并入管线设计 |
| 4 | StatusType→statKey 修正映射 | 口径 #8 写死于 `StatPipeline`，单点可改 |
| 5 | 事件池化 / gdx-ai | 池化推迟 Phase 4 前评估；gdx-ai 本期零 import |
| 6 | 控制台模拟工具 | 判定需要，T8 交付 `BattleConsoleMain` |

### 技术风险

| 项 | 说明 | 缓解 |
|----|------|------|
| `BattleSystem`/`SkillExecutor`/`StatusSystem` 体量 | 组合根与两大子系统是触线风险文件（§六 ⚠） | 已按子系统拆分；预拆位：`SkillExecutor`→shape 解析抽 `ShapeResolver`；`StatusSystem`→DOT/REGEN 心跳抽 `StatusTicker`；`BattleSystem`→行动链抽 `UnitActor` |
| 就地施放嵌套 | 能量跨百回调可嵌套触发链式施放 | 深度上限 16（口径 #19）+ 定界测试 |
| 测试运行时长 | 全场回放类测试若拖满 60s×60Hz=3600 tick 会拖慢 CI | 快速终局夹具（高攻速高伤害小血量）+ `runToEnd` 上限 |
| float 直存的显示层适配 | 事件 amount 为 float，Phase 4 飘字需取整规则 | 口径 #7 已预留"显示层取整留 Phase 4"；届时补一条渲染层口径即可 |
| `CombatEvent` 扁平结构演化 | 新事件类型/新字段会触碰公共类 | 集中在单一工厂文件，字段 nullable 语义文档化；池化改造时一并评估 |
| HTML 图谱离线不可渲染 | mermaid 走 CDN | `.md` 版为事实源（IDE/GitHub 可直接渲染） |
| `BattleUnit` 写方法误用 | 公开可变方法存在被 UI 层误调风险 | `framework-internal` javadoc 标注 + code review 检查项（口径 #22） |

### 开放问题（遗留，不阻塞）

- LINE 弹道细化（射程上限/方向量化/友军挡弹）——battle §九，随内容需要再立项
- 亡语 / 死亡触发钩子——清扫阶段（阶段④）已预留调用位，Phase 5+ 按内容接入
- 寻路精化（侧移绕行 / A*）——MVP 贪婪步 + 等待（battle §四/§九）
- SLOW 语义内容化（当前固定百分比减速，口径 #8 单点可改）
- `StatusType` 扩展（SILENCE/TAUNT 等）——先登记 battle §七词表再进 JSON（data_schema §三铁律）
- 技能"同 tick 先移后攻"调优开关（口径 #3 的一行变更位）

---

## 十二、附录：用户确认记录（2026-08-21，原样存档）

**Q1 交付物边界 → 选 B：按出生表。**
只建出生表（`docs/project_structure_design.md` 出生时间表）所列：`Unit` / `BattleUnit` / `BattleState` / `BattleSystem` / `SynergySystem`，外加 `Player` 名单字段增补（`core/src/main/java/com/voidvvv/kz_auto_chess_n/entities/Player.java` 中"名单字段随 Unit 实体 Phase 3 增补"的注释兑现）。战斗实例 id 用**注入的轻量发号器接口占位**；`RunState` / `UnitRegistry` 推迟到 Phase 5 接命令系统时出生。

**Q2 LINE 弹道 → 选 B：本期不做。**
`Delivery.LINE` 词表枚举保留；技能执行器遇 LINE 技能抛错（测试夹具不构造 LINE 技能）；LINE 的射程上限/方向量化/棋盘边界/友军挡弹规则细化与实现随内容需要推后（与 battle_design §九待定清单一致）。

**Q3 lifesteal 吸血 → 选 A：普攻触发。**
仅普攻直伤命中触发：回复 = 护甲后实际伤害 × lifesteal/100，不溢出 maxHp；DOT / 技能伤害 / 落空不触发（常规自走棋口径）。吸血环节补进 battle §5.2 伤害管线的实现设计中。

**Q4 装备修正源 → 选 A：修正源列表。**
`Unit` 本期不含装备槽字段；属性派生管线按"修正源列表"设计（本期两源：星级 + 羁绊），Phase 5 装备源作为新修正源插入时零改结算器。
