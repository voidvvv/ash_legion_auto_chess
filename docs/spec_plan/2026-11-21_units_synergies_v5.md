# 棋子与羁绊 v5 落地 技术实施计划

> **日期**：2026-11-21　**状态**：待评审（libgdx-impl-executor 上游）
> **规格源（唯一）**：`docs/planning/units_synergies_design_v5.md`（v5 终稿——主人已终审通过，D3~D11 一律按文中临时方案执行）；补充载荷出处：`docs/planning/synergy_showcase_undead_deathrattle.md`（v5 §2.1「示范稿原样」行的数值权威）。
> **配图**：`../diagrams/units_synergies_v5_arch.md`（+.html，机制接线架构）；`../diagrams/units_synergies_v5_batches.md`（+.html，批次与验证门）。
> **范围**：① 24 棋子（18 重排 + 6 新增）+ 8 羁绊 2/4/6 替换制全量 + scenes.json 同步；② v5 §4.1 全部机制依赖与 C1~C13 口径；③ 渲染图形族（种族=图形、职业=变体）与星级颜色（颜色=级别）；④ implementation report 结构规划。
> **不含**：§五.1 敌方波次内容化（完整主题波次表）、§五.6 叠加制备选——登记为「未实施建议」（§8.4）；GDD/battle 等设计文档改写归 gdd-writer。
> **验证纪律（主人红线①）**：gradle 成功时控制台零输出——每批以**退出码** + `core/build/test-results/test/TEST-*.xml` 聚合计数核对；每批独立可构建、conventional commits 分批提交（无 attribution）。

---

## 1. 背景与目标

v5 终稿把棋子池从 18 补齐到 24（4 种族 × 4 职业行列各 6、费阶 10/9/5）、把 8 条羁绊全部重写为「2/4/6 替换制全量档 + 高档换打法」，并引入一批触发式机制（死亡经济、受击窗口、开局/定时/条件/附带）。本计划把它拆为 **6 个可独立构建的批次、28 个改动点（CP1~CP28）**，交给 libgdx-impl-executor 顺序执行。

**成功标准**：
1. 数据与 v5 §一/§二/§4.3 逐格一致（费阶 10/9/5、传奇全 3 费不入商店池、JsonLoader 硬校验全绿）；
2. C1~C13 口径逐条可测（同 tick 双顺序、级联收敛、放逐非死亡、倒地四过滤点等均有断言）；
3. 零新增 RNG 消耗点（暴击覆写复用既有 roll，architecture §六仅补注）；
4. 渲染：24 子图形族 + 星级色可读性达标（敌我/同族异职/星级三轴一眼可辨），且为像素素材到位前的可替换表达层；
5. 每批构建退出码 0 + 全部测试绿；批1 渲染遵守「先 1 子跑通再铺开」红线（主人红线③）。

## 2. 术语与约定

| GDD/v5 用语 | 代码标识符 | 位置/落点 |
|---|---|---|
| 档位替换制全量 | `SynergyData.activeThreshold`（不改） | `data/SynergyData.java:55-65` |
| 机制通道（stat/effect 之外的第三通道） | `SynergyMechanic`（新枚举）+ `EffectData.mechanic` | CP2 |
| 未清扫存活（含倒地） | `!unit.isCleaned()` | `entities/BattleUnit.java:110-112` |
| 可行动存活 | `!cleaned && !downed`（新谓词 `isActable()`） | CP17 |
| 真死 | 进入两遍式清扫的死亡（undying 首倒/放逐均非真死，C1） | CP14 |
| 亡语 | `DeathrattleSpec`（UnitData 新字段 `deathrattle`） | CP2/CP5 |
| 不死（誓约未竟） | `UndyingSpec`（UnitData 新字段 `undying`）+ `downed` 字段 | CP2/CP17 |
| 阵亡名册 / 大公点算 / 双份抚恤 | `DEATH_TAX` / `DEATH_AUDIT` / `DOUBLE_DEATHRATTLE` 机制词 | CP16 |
| 余震 / 免伤 / 旧疤 | `REFLECT` / `DAMAGE_REDUCTION` / `SCAR` 机制词 | CP20/CP21 |
| 卫队战吼 / 盾墙 / 洞察 / 收割 / 三连装填 / 兽潮 / 群星响应 / 连环清账 | `WARCRY_TAUNT` / `SHIELD_WALL` / `SPELL_PEN` / `EXECUTE` / `CRIT_RHYTHM` / `ENEMY_DEATH_SURGE` / `OPENING_ENERGY`+`AFTER_CAST_ENERGY` / `KILL_ENERGY_REFUND` | CP22~CP27 |
| 遗物/旧疤计数通道 | `BattleUnit.combatCounters`（`CombatCounters`）+ StatPipeline 第二级扩展 | CP12 |
| 击杀归因 | `BattleUnit.lastDamagerId` + `UNIT_DIED.killerId` | CP13 |
| 放逐（C10 非死亡移除） | `systems/Banish` + `BattleUnit.markBanished()` | CP8 |
| 亡语源弹道（C6/§五.7） | `Projectile.deathrattleBorn` | CP18 |
| 暴击属性化（§五.5） | `StatKey.CRIT_CHANCE` / `StatKey.CRIT_DAMAGE` | CP1 |
| D10 敌方档位截断 | `SynergySystem.resolve(..., countCap)` + `GameBalance.enemySynergyCountCap` | CP7 |
| 图形族 / 星级色 | `render/UnitShapeKit` / `render/StarColors`（新） | CP9~CP11 |

**约定**：Java 标识符全英文（中文只进注释与 `@DisplayName`）；全局数值只进 `GameBalance`；渲染层只读实体（铁律 1）；百分比一律百分点整数刻度（÷100 在结算点）；确定性：新机制全部零 RNG，`CRIT_RHYTHM` 覆写不新增消耗点。

## 3. 现状盘点（file:line 均为本次实读）

### 3.1 可直接复用（零改动）

| 资产 | 位置 | 说明 |
|---|---|---|
| 主循环五阶段固定序 | `systems/BattleSystem.java:128-185` | 顺序不动；④ 内部替换为 DeathSystem（CP14） |
| 胜负判定序（玩家从严在先） | `BattleSystem.java:175-181` | C3/C4 沿用（aliveCount=未清扫，天然含倒地） |
| 档位替换制 | `data/SynergyData.java:55-65` | v5 §〇.1 明认一致 |
| 伤害公式 / 吸血 / 回能 | `systems/DamagePipeline.java:35-37,46-48,68-78` | CP20 只在公式前后插窗口 |
| DOT 真伤 / REGEN 语义 | `systems/StatusSystem.java:100-107`、`SkillExecutor.java:237` | BURN 沿用同惯例 |
| 同 type 刷新取大 | `StatusSystem.java:61-67`、`StatPipeline.java:71-87` | 双响 SLOW「只刷新」已知特性即此 |
| HOMING 弹与消散 | `systems/ProjectileSystem.java:29-48`、`entities/Projectile.java` | skill==null 分界现成（`ProjectileSystem.java:52-54`） |
| 索敌四优先级 + 平局链 | `systems/TargetingSystem.java` | 嘲讽=外层 override，不动排序键 |
| 技能三步执行 + 延后施放 | `systems/SkillExecutor.java:43-79`（口径 #18） | 依账除名「持能等待」复用延后路径 |
| 3 合 1 自动合成（同名上限 2 的兑现） | `systems/ShopSystem.java:145-249`（`rosterInRemovalOrder` 含 bench+deployed） | §五.4 仅口径注记 |
| 羁绊计数=上场单位逐个计 1 | `systems/SynergySystem.java:52-60` | §五.4 仅口径注记 |
| 商店池门控（场景 shopUnlocks + 传奇互斥） | `save/ProfileService.java:143-171`、`config/JsonLoader.java:744-762` | 新单位自动进基础池/场景池 |
| 敌我色框 / 星级点 / 血条 | `render/board/SideColors.java`、`UnitView.drawBars` | 图形/颜色方案的敌我轴不变 |
| 真素材优先回退占位 | `render/Assets.java:31-42` | 图形层=可替换表达层（RealArt 逐 key 覆盖） |

### 3.2 需改造

| 资产 | 位置 | 改什么 |
|---|---|---|
| 死亡清扫单遍扫描 | `BattleSystem.java:160-172` | → 两遍式级联（CP14） |
| `applyDirectHit` 单一公式 | `DamagePipeline.java:31-49` | → 受击前/后窗口 + 穿甲/暴伤参数（CP20） |
| `applyTrueDamage` 无事件 | `DamagePipeline.java:51-55` | → 亡语致死归因记录 + 可选事件（CP13/CP15） |
| 开局效果只认 SHIELD | `BattleSystem.java:294-308` | → TRAITS 谓词 + OPENING_ENERGY（CP22） |
| 每 2s 全局重评估 | `BattleSystem.java:138-140` + `TargetingSystem.retargetAll` | → 嘲讽期豁免（CP23） |
| 弹道消散一律 Fizzle | `ProjectileSystem.java:33-37` | → 亡语源弹道重选（CP18） |
| 暴击全局常量 | `config/GameBalance.java:19-20`、`BattleSystem.java:219`、`DamagePipeline.java:36` | → StatKey 两键（CP1） |
| 单位占位图=方块+顶条 | `render/PlaceholderArt.paintUnit`（91-106） | → 图形族 + 可 tint 白体（CP10） |
| `UNIT_DIED` 无凶手 | `entities/CombatEvent.java:79-81` | → killerId（CP13） |

### 3.3 需新建

`data/SynergyMechanic`、`data/DeathrattleSpec`、`data/UndyingSpec`、`data/CastCondition`、`entities/CombatCounters`、`systems/DeathSystem`、`systems/DeathrattleExecutor`、`systems/Banish`、`systems/TimedTriggerSystem`（CP23 内）、`render/UnitShapeKit`、`render/StarColors`、实施报告模板（CP28）；测试类随各 CP 新建。

## 4. 已确认决策

### 4.1 主人裁决（本计划的上游输入，原样记录）

| # | 决策 | 内容 |
|---|---|---|
| D-A | v5 终审通过 | D3~D11 全部按 v5 §七「本稿采用（临时方案）」执行：D3 Boss 可被点算移除；D6 统一 2/4/6 替换制；D9 框架 §四「被动槽+触发映射」路线；D10 敌方结算羁绊维持 + enemyPool 主题重排 + 档位上限随轮次；D11 三批钩子切分。 |
| D-B | 派单范围 | 数据落地（24 子/8 羁绊/scenes）+ 机制落地（§4.1 依赖清单 + C1~C13）+ 渲染两条（图形族/星级色）+ 报告规划，见 §1。 |
| D-C | §五处理口径 | 仅实施「设计依赖」项：.7 明确实施；.2/.3/.5 判定见 4.2；.4 现状已满足仅口径注记；.1/.6 列「未实施建议」。 |
| D-D | 渲染红线③ | 图形改动先小步验证 1 个棋子跑通再铺开（CP10 两段提交）；表达层注明为素材到位前的程序员层，保持可替换。 |
| D-E | 红线①② | 每批可构建可运行、零编译错误；构建+测试验证点用退出码 + TEST-*.xml 聚合计数；分批 conventional commits。 |

### 4.2 §五 规则建议依赖判定（planner 结论，依据=代码实读）

| §五条目 | 判定 | 依据 | 落点 |
|---|---|---|---|
| .2 清扫级联化 | **设计依赖，实施** | C2 两遍式是全部亡语/名册/点算/兽潮/清账的兑现前提（v5 §4.1 缺口④行） | 批2 CP14 |
| .3 尸体状态入规则 | **设计依赖，实施** | C7 五项+四过滤点是「未清扫存活（含倒地）」胜负口径与不死机制的实现前提 | 批2 CP17 |
| .4 双上阵计数 | **非依赖，仅口径注记** | `SynergySystem.java:52-60` 对 templates 列表逐单位计 1（同名两份各计 1 已成立）；备战 3 合 1 按 bench+deployed 全名单计数（`ShopSystem.java:203-249`）——v5 §4.4 counts 口径与现状一致，零代码改动 | §8.3 注记 |
| .5 暴击属性化 | **设计依赖，实施** | 游侠 (4)/(6) 档 `critChance` 走 stat 通道，StatKey 无此键则档位无法表达（v5 §4.2 明言依赖） | 批0 CP1 |
| .7 亡语源弹道消散特例 | **设计必需，实施**（派单明确） | C6 空弦绝响在途重选；确定性不受影响 | 批2 CP18 |
| .1 敌方波次内容化 | 未实施建议 | enemyPool weight/minRound 雏形已由批0 落地；完整主题波次表（侦察可读）超出本次机制依赖 | §8.4 |
| .6 叠加制备选 | 未实施建议 | D6 已裁替换制；并存需改 GDD §5.1 + resolve 语义 | §8.4 |

### 4.3 实施口径（planner 裁决，语义从 v5 + 代码事实推导；争议项另见 §8）

| # | 口径 | 依据 |
|---|---|---|
| K1 | 机制通道=EffectData 第三通道 `mechanic`（stat/effect/mechanic 三选一互斥），带 `value/value2/status/duration/target/trait` 辅字段；SynergySnapshot 暴露 `mechanics` 列表，startBattle 按 target 谓词（ALLIES 全员 / TRAITS+trait 匹配 race 或 class）过滤下发到 BattleUnit 不可变字段 | 框架 §四 2+3 组合（D9）；v5 §4.1「EffectTarget 谓词扩展」行 |
| K2 | 遗物/旧疤走 `CombatCounters` 并入 StatPipeline 第二级 ADD/PCT 通道（先加后乘，与 combine 语义一致）：有效攻=(基准+遗物ADD+状态ADD)×(1+(旧疤PCT+状态PCT)/100)、有效甲=基准+旧疤ADD+状态ADD——**不走 StatusType、不走开战快照** | v5 §4.1 计数通道行写死 |
| K3 | 死亡两遍式：pass1 按 id 序「抓攻击快照（statuses 未清）→ undying 判定 → markCleaned + 移格 + UNIT_DIED(带 killer)」；pass2 按 id 序结算亡语(×双响)/名册/点算/兽潮/清账；亡语新致死并入下一轮循环，收敛后统一 retarget；循环上界=单位数 | C2/C9 |
| K4 | 击杀归因=`lastDamagerId`（applyDirectHit/applyTrueDamage 中 hpDamage>0 时记录攻击者/来源）；有效凶手=存在且可行动存活且敌对；DOT 致死归施加者（sourceId 失效则为空） | C5/C12 |
| K5 | 放逐（BANISH 技能与 DEATH_AUDIT 共用 `Banish.execute`）：markBanished（cleaned=true+banished 标记、清状态、不触发任何死亡结算）+ 移格 + UNIT_BANISHED 事件；索敌失效靠 currentTarget 惰性重选（现行为），不强制 retarget | C10/C13 |
| K6 | 倒地：`downed` 为 BattleUnit 字段非状态；`hasControl()` 扩为 downed∨STUN；四过滤点（currentTarget / tickStatuses / SkillExecutor resolveTargets 系 / TargetingSystem）补 downed 判定；tickStatuses 整体跳过=时钟冻结（DOT 计时与时长递减暂停）；占格维持、能量保留；1.0s 后原地复活 40% 血+能量回满（防御性保留「格被占→曼哈顿最近空格（x 后 y 序）」分支） | C7 |
| K7 | 卫队战吼：开战 6s 定时触发（BattleState 新增 pendingTimedTriggers，确定性 tick）；旗手=该侧 mechanics 含 WARCRY_TAUNT 的单位中「最前」（玩家侧 min y、敌方侧 max y，平局 min x）；曼哈顿 ≤1 敌人 targetId 强制指向旗手 3s；嘲讽期内 retargetAll 跳过、目标失效立即解除 | v5 §一.5/§4.1 |
| K8 | 盾墙：普攻弹（skill==null）且目标为「战士身后」（玩家侧 target.y>defender.y；敌方侧 target.y<defender.y）时，被**距目标最近**（曼哈顿、平局 id 小）的可行动存活 SHIELD_WALL 战士拦下：弹消散、PROJECTILE_BLOCKED 事件、无伤害转移；技能弹/近战不受限 | v5 §一.6/克网；「最近」以目标为参照（v5 未指定，登记 §8.2 R3） |
| K9 | 受击窗口序（applyDirectHit 内）：收割增伤（前，乘 (1+amp/100)）→ 免伤（前，乘 (1−v/100)，仅直伤）→ 护甲（含穿甲 armor×(1−pen)）→ 盾 → 血 → HIT 事件 → 回能 → 吸血 → 旧疤计数（仅普攻）→ 余震反弹（甲后×ratio 真伤回攻击者，可致死入级联）→ 普攻附带挂点。DOT/真伤全线豁免（免伤/反弹/旧疤均不触发） | v5 §4.1 缺口②行 |
| K10 | 三连装填：普攻出手时计数 +1（近战与发射同点）；第 value 刀 roll 后覆写 crit=true、该次命中暴伤额外 +value2 pp（×(1.5+0.5)=×2.0）；roll 本身照常消耗 RNG（序不变）。暴伤加成只作用于被覆写的必暴刀（见 §8.2 R2 备选） | v5 §一.8/§4.5 锚点 |
| K11 | 兽潮=敌方**真死**逐次触发（放逐/点算移除不触发，C13）：对侧含 ENEMY_DEATH_SURGE 的单位挂 ASPD_UP（value%、duration s，同 type 取大=「刷新制」）；连环清账=归因凶手含 KILL_ENERGY_REFUND 则 setEnergy(100)（不就地施放，下一行动 tick 施放，见 §8.2 R4） | v5 §3.1 #10/#11 |
| K12 | DEATH_TAX 继承者=清扫后可行动存活且 race/class 命中 trait 的友方，距尸体格曼哈顿最近、平局 id 小；死者自身不参与（C9）；无可继承者作废（C8 倒地边界同理）。DEATH_AUDIT 目标=敌方可行动存活中 hpRatio 最低且 <value%，平局 id 小；无合格不触发不缓存（C11） | C8/C9/C11 |
| K13 | 批0 一次落全量数据：新词表「解析+薄执行」同时上（BANISH/GAIN_ENERGY/KNOCKBACK/BURN 执行器与放逐原语进批0），保证批0 起任何可购棋子技能 cast 不抛异常；机制词（SCAR/REFLECT 等）批0 仅解析入快照、批2~4 接线——「schema 先行、wiring 分批」，每批均可构建 | 派单批0=全量数据 + 红线① |
| K14 | 渲染：形状=种族族（兽人三角/野兽菱形/亡灵五边/巨人六边/其他+Boss 兜底五角星）、族内变体=职业（战士顶带/法师核点/刺客斜切/游侠空心/无职业标记）、颜色=星级（1★ 骨白/2★ 金/3★ 紫，tint 白体实现）——三轴正交：敌我=SideColors 框（不变）、身份=图形、级别=颜色 | 派单渲染两条；详细理由 CP9/CP10 |

## 5. 总体技术方案

架构与数据流见 `../diagrams/units_synergies_v5_arch.md`（+.html）。要点：

1. **数据层三通道**：synergies.json 每档 effects 支持 stat / effect(SHIELD) / mechanic 三形态；units.json 新增 deathrattle/undying/skill condition。JsonLoader 显式解析 + 逐 kind 校验（fail-fast 文化不破）。
2. **快照下发**：SynergySnapshot 在 statModifiers/openingEffects 之外暴露 mechanics；startBattle（含 D10 敌方计数截断）按 target 谓词把机制下发为 BattleUnit 不可变 `mechanics` 字段——所有系统经 `unit.getMechanic(kind)` 单点读取，敌我对称天然成立。
3. **死亡簇**：BattleSystem ④ 委托新 DeathSystem（两遍式级联 + 亡语执行器 + 名册/点算/兽潮/清账 + undying→倒地），放逐走独立 Banish 路径（C10 分流）。
4. **受击簇**：DamagePipeline 单点开受击前/后窗口（免伤/收割/穿甲在前，旧疤/反弹/附带在后），全部经 mechanic 查询，无机制时零开销分支。
5. **批0~批5 顺序**：数据+词表 → 渲染（小步）→ 死亡 → 受击 → 开局/条件/附带 → 报告收尾；每批一个 conventional commit（或按 CP 细分多个），批内验证门见 §7。

## 6. 改动点清单（评审主入口）

> 按依赖顺序排列；「修改前」逐字摘自当前源码（批3/批4 引用的「修改前」为前置批合并后的形态，已在各 CP 标注）。同一段代码完整改动只出现在一个 CP，其余引用。

---

### CP1. 暴击属性化迁移（§五.5 依赖：游侠 (4)/(6) stat 档）

- **类型**：修改枚举 / 修改类 / 修改方法 / 删常量
- **位置**：`data/StatKey.java:14-23`、`data/BaseStats.java`（构造器）、`entities/BattleStats.java:14-36`、`systems/StatPipeline.java:116-139`、`config/GameBalance.java:19-20`、`systems/BattleSystem.java:219`、`systems/DamagePipeline.java:36`、`config/JsonLoader.java:186-194`
- **改动说明**：CRIT_CHANCE/CRIT_DAMAGE 升格为 StatKey 两键（百分点刻度：20=20%、150=×1.5，`isPercentScale` 纳入），baseStats 缺省 20/150；roll 点与乘数点改读**有效属性**（可被羁绊/装备修正）。GameBalance 旧常量删除（单一事实源=StatKey 缺省）。
- **代码**：

`data/StatKey.java` 修改前（14-23 行节选）：
```java
public enum StatKey implements Vocab {
    HP("hp", null),
    ATTACK("attack", null),
    ARMOR("armor", null),
    ATTACK_SPEED("attackSpeed", null),
    MOVE_SPEED("moveSpeed", null),
    RANGE("range", null),
    LIFESTEAL("lifesteal", 0),
    ENERGY_GAIN_RATE("energyGainRate", 100),
    SKILL_POWER("skillPower", 0);
```
修改后：
```java
public enum StatKey implements Vocab {
    HP("hp", null),
    ATTACK("attack", null),
    ARMOR("armor", null),
    ATTACK_SPEED("attackSpeed", null),
    MOVE_SPEED("moveSpeed", null),
    RANGE("range", null),
    LIFESTEAL("lifesteal", 0),
    ENERGY_GAIN_RATE("energyGainRate", 100),
    SKILL_POWER("skillPower", 0),
    /** 暴击率，百分点（20 = 20%；§五.5 属性化迁移，D1 已裁） */
    CRIT_CHANCE("critChance", 20),
    /** 暴击伤害倍率，百分点刻度（150 = ×1.5） */
    CRIT_DAMAGE("critDamage", 150);
```
`isPercentScale()` 同步追加两键。

`config/GameBalance.java` 修改前（19-20 行）：
```java
    public static final float CRIT_CHANCE = 0.20f;
    public static final float CRIT_MULTIPLIER = 1.5f;
```
修改后：整段删除（缺省值以 `StatKey.CRIT_CHANCE.baseStatsDefault()` 为唯一事实源）。

`systems/BattleSystem.java` 修改前（219 行，act 内出手分支）：
```java
            boolean crit = state.getRng().nextFloat() < GameBalance.CRIT_CHANCE;
```
修改后（CP27 再在此行后接入覆写，见 §6.CP27）：
```java
            boolean crit = state.getRng().nextFloat()
                    < unit.getEffective(StatKey.CRIT_CHANCE) / 100f;
```

`systems/DamagePipeline.java` 修改前（35-37 行）：
```java
        float mitigated = attackPower * multiplier
                * (crit ? GameBalance.CRIT_MULTIPLIER : 1f)
                * 100f / (100f + target.getEffective(StatKey.ARMOR));
```
修改后（CP20 将整体重写本方法，此处为 CP1 最小替换）：
```java
        float critMultiplier = attacker == null
                ? StatKey.CRIT_DAMAGE.baseStatsDefault() / 100f
                : attacker.getEffective(StatKey.CRIT_DAMAGE) / 100f;
        float mitigated = attackPower * multiplier
                * (crit ? critMultiplier : 1f)
                * 100f / (100f + target.getEffective(StatKey.ARMOR));
```

其余同型机械改动（不逐段展开，执行器照此口径）：`BaseStats`/`BattleStats` 增两字段与构造参（`BattleStats.get`/`equals`/`hashCode`/`toString` 同步）；`StatPipeline.build`/`templateValue` 增两 case；`JsonLoader.parseBaseStats` 增两个 `optionalInt`（沿 lifesteal 三键先例，186-191 行）；`BattleTestFixtures.base()/stats()` 补缺省参；全部 BattleStats/BaseStats 构造调用点（测试为主）机械补参。
- **测试要点**：`StatPipelineTest` 增「critChance/critDamage 走 ADD/PCT 修正」断言；`BattleSystemTest` 既有暴击用例回归（seed 固定，默认 20%/×1.5 下事件流逐位不变）；`JsonLoaderTest` 增 units.json 显式 critChance 覆写解析。

---

### CP2. 词表与数据类扩展（机制三通道 / 亡语 / 不死 / 条件）

- **类型**：新建枚举 ×2 / 新建类 ×3 / 修改枚举 ×2 / 修改类 ×2
- **位置**：新建 `data/SynergyMechanic.java`、`data/DeathrattleKind.java`、`data/DeathrattleSpec.java`、`data/UndyingSpec.java`、`data/CastCondition.java`；修改 `data/StatusType.java`、`data/SkillEffectType.java`、`data/EffectData.java`、`data/SkillData.java`、`data/UnitData.java`
- **改动说明**：§4.3 K1 的落地。机制词 17 个（见下）；亡语 4 kind 参数化（数值在 units.json）；BURN 入 StatusType；技能效果词 +3；SkillData 增可选 condition（依账除名「持能等待」）。
- **代码**：

新建 `data/SynergyMechanic.java`（完整）：
```java
package com.voidvvv.kz_auto_chess_n.data;

/** 羁绊机制词表（v5 §4.1 依赖清单；EffectData 第三通道，K1）。
 *  参数约定（value/value2/status/duration/trait 按词选择必填/可缺，加载期逐词校验）：
 *  BASIC_ATTACK_STATUS: status+value+duration（value=DOT 类为 atk 百分比、其余为 power 百分点）
 *  SCAR: value=每层承受普攻次数, value2=层数上限（每层幅度走 GameBalance 常量）
 *  DEATH_TAX: value=攻 ADD, value2=生命上限 ADD（继承立即回复 value2）
 *  DOUBLE_DEATHRATTLE / KNOCKBACK_IMMUNE / SHIELD_WALL / KILL_ENERGY_REFUND: 无参
 *  DEATH_AUDIT: value=生命比例阈值（百分点，25 = <25%）
 *  ENEMY_DEATH_SURGE: value=攻速增益百分点, duration=秒
 *  REFLECT: value=反弹百分比（甲后伤害 ×value%）
 *  DAMAGE_REDUCTION: value=免伤百分比
 *  WARCRY_TAUNT: value=开战延迟秒, value2=嘲讽持续秒（半径 GameBalance 常量）
 *  SPELL_PEN: value=无视护甲百分比（仅技能直伤）
 *  OPENING_ENERGY / AFTER_CAST_ENERGY: value=能量值（走 gainEnergy 加权）
 *  EXECUTE: value=增伤百分比, value2=生命比例阈值（百分点，35 = <35%）
 *  CRIT_RHYTHM: value=第 N 刀必暴, value2=必暴刀暴伤额外百分点 */
public enum SynergyMechanic implements Vocab {
    BASIC_ATTACK_STATUS("BASIC_ATTACK_STATUS"),
    SCAR("SCAR"),
    DEATH_TAX("DEATH_TAX"),
    DOUBLE_DEATHRATTLE("DOUBLE_DEATHRATTLE"),
    DEATH_AUDIT("DEATH_AUDIT"),
    ENEMY_DEATH_SURGE("ENEMY_DEATH_SURGE"),
    REFLECT("REFLECT"),
    DAMAGE_REDUCTION("DAMAGE_REDUCTION"),
    KNOCKBACK_IMMUNE("KNOCKBACK_IMMUNE"),
    WARCRY_TAUNT("WARCRY_TAUNT"),
    SHIELD_WALL("SHIELD_WALL"),
    SPELL_PEN("SPELL_PEN"),
    OPENING_ENERGY("OPENING_ENERGY"),
    AFTER_CAST_ENERGY("AFTER_CAST_ENERGY"),
    EXECUTE("EXECUTE"),
    KILL_ENERGY_REFUND("KILL_ENERGY_REFUND"),
    CRIT_RHYTHM("CRIT_RHYTHM");

    private final String jsonName;

    SynergyMechanic(String jsonName) { this.jsonName = jsonName; }

    @Override
    public String jsonName() { return jsonName; }
}
```

新建 `data/DeathrattleKind.java` + `data/DeathrattleSpec.java`（完整）：
```java
package com.voidvvv.kz_auto_chess_n.data;

/** 亡语载荷形态（4 kind 覆盖 v5 全部 5 条亡语；数值在 units.json，K1） */
public enum DeathrattleKind implements Vocab {
    AREA_TRUE_DAMAGE("AREA_TRUE_DAMAGE"),   // 尸体格为心曼哈顿半径内真伤（可附 SLOW）
    KILLER_TRUE_DAMAGE("KILLER_TRUE_DAMAGE"), // 对击杀者（无效则按 C5 转移）
    DEATH_VOLLEY("DEATH_VOLLEY"),           // 自尸体格发亡语弹（第 1 发 HIGHEST_ATK；双响第 2 发 LOWEST_HP）
    ALLY_REGEN("ALLY_REGEN");               // 存活友军 REGEN

    private final String jsonName;
    DeathrattleKind(String jsonName) { this.jsonName = jsonName; }
    @Override public String jsonName() { return jsonName; }
}
```
```java
package com.voidvvv.kz_auto_chess_n.data;

import java.util.Objects;

/** 个人亡语参数（units.json "deathrattle"；不可变）。全载荷走真伤（阵营轴：亡者的攻击无视生者的盔甲）。 */
public final class DeathrattleSpec {
    private final DeathrattleKind kind;
    private final float ratio;        // AREA/KILLER/VOLLEY：攻击快照 ×ratio
    private final int radius;         // AREA：曼哈顿半径
    private final StatusType statusType;      // AREA 可选附带（SLOW）
    private final float statusPower;  // 百分点（SLOW 30 = −30% 移速）
    private final float statusDuration;
    private final float regenRatio;   // ALLY_REGEN：maxHp 比例/秒（0.02 = 2%/s）
    private final float duration;     // ALLY_REGEN 秒

    public DeathrattleSpec(DeathrattleKind kind, float ratio, int radius,
                           StatusType statusType, float statusPower, float statusDuration,
                           float regenRatio, float duration) {
        this.kind = Objects.requireNonNull(kind, "kind 不能为 null");
        this.ratio = ratio; this.radius = radius;
        this.statusType = statusType; this.statusPower = statusPower; this.statusDuration = statusDuration;
        this.regenRatio = regenRatio; this.duration = duration;
    }

    public DeathrattleKind getKind() { return kind; }
    public float getRatio() { return ratio; }
    public int getRadius() { return radius; }
    public StatusType getStatusType() { return statusType; }
    public float getStatusPower() { return statusPower; }
    public float getStatusDuration() { return statusDuration; }
    public float getRegenRatio() { return regenRatio; }
    public float getDuration() { return duration; }
}
```

新建 `data/UndyingSpec.java`、`data/CastCondition.java`（完整）：
```java
package com.voidvvv.kz_auto_chess_n.data;

/** 不死参数（units.json "undying"；誓约未竟=单位属性非亡语，C1） */
public final class UndyingSpec {
    private final float reviveDelaySeconds;
    private final float reviveHpRatio;

    public UndyingSpec(float reviveDelaySeconds, float reviveHpRatio) {
        this.reviveDelaySeconds = reviveDelaySeconds;
        this.reviveHpRatio = reviveHpRatio;
    }
    public float getReviveDelaySeconds() { return reviveDelaySeconds; }
    public float getReviveHpRatio() { return reviveHpRatio; }
}
```
```java
package com.voidvvv.kz_auto_chess_n.data;

/** 施放条件（skills.json "condition"，可选）：不满足 → 延后施放（口径 #18「持能等待」） */
public final class CastCondition {
    /** 条件类型（本期一档：主目标生命比例低于 value） */
    public enum Type implements Vocab { TARGET_HP_BELOW("TARGET_HP_BELOW");
        private final String jsonName;
        Type(String jsonName) { this.jsonName = jsonName; }
        @Override public String jsonName() { return jsonName; } }

    private final Type type;
    private final float value;

    public CastCondition(Type type, float value) {
        this.type = type; this.value = value;
    }
    public Type getType() { return type; }
    public float getValue() { return value; }
}
```

修改 `data/StatusType.java` 修改前（4-13 行）：
```java
public enum StatusType implements Vocab {
    STUN("STUN"),
    BLEED("BLEED"),
    POISON("POISON"),
```
修改后（追加 BURN；POISON 后插入）：
```java
public enum StatusType implements Vocab {
    STUN("STUN"),
    BLEED("BLEED"),
    POISON("POISON"),
    BURN("BURN"),
```

修改 `data/SkillEffectType.java`：同法追加 `GAIN_ENERGY("GAIN_ENERGY")`、`KNOCKBACK("KNOCKBACK")`、`BANISH("BANISH")` 三词。

修改 `data/EffectData.java`：新增可空字段 `SynergyMechanic mechanic`、`Float value2`、`StatusType status`、`Float duration`、`String trait`（构造器重载保持旧 5 参签名委托，旧测试不破）；新增 `getMechanic()/getValue2()/getStatus()/getDuration()/getTrait()` 与 `isMechanicChannel()`。

修改 `data/SkillData.java`：新增可空字段 `CastCondition condition` + getter（构造器重载，旧签名委托）。

修改 `data/UnitData.java`：新增可空字段 `DeathrattleSpec deathrattle`、`UndyingSpec undying` + getter（构造器重载，旧签名委托，`JsonLoader` 用新签名）。
- **测试要点**：新数据类直测（不可变性、null 语义）；`EffectDataTest` 增三通道互斥便捷方法断言。

---

### CP3. JsonLoader 三通道解析与逐词校验

- **类型**：修改方法
- **位置**：`config/JsonLoader.java:124-125`（units 键集）、`235-270`（parseSkillEffect）、`330-351`（parseSynergyEffect）、新增 `parseDeathrattle/parseUndying/parseCastCondition` 私有方法
- **改动说明**：三通道互斥（stat/effect/mechanic 恰一）；逐词必填校验（K1 参数表）；units.json 新键 deathrattle/undying；skills.json 新词（BANISH 无 value、GAIN_ENERGY value>0、KNOCKBACK value≥1 且整值、APPLY_STATUS 允许 BURN）+ condition 对象；fail-fast 文化不变。
- **代码**（关键新逻辑，执行器按同风格补齐逐词 switch）：

`parseSynergyEffect` 修改前（332-339 行）：
```java
        checkUnknownKeys(ee, w, "stat", "effect", "op", "value", "target");

        JsonValue statNode = ee.get("stat");
        JsonValue effectNode = ee.get("effect");
        boolean hasStat = statNode != null && !statNode.isNull();
        boolean hasEffect = effectNode != null && !effectNode.isNull();
        if (hasStat == hasEffect) {
            fail(w, "stat 与 effect 必须二选一（属性修正走 stat，特殊效果走 effect）");
        }
```
修改后：
```java
        checkUnknownKeys(ee, w, "stat", "effect", "mechanic", "op", "value", "value2",
                "status", "duration", "target", "trait");

        JsonValue statNode = ee.get("stat");
        JsonValue effectNode = ee.get("effect");
        JsonValue mechanicNode = ee.get("mechanic");
        boolean hasStat = statNode != null && !statNode.isNull();
        boolean hasEffect = effectNode != null && !effectNode.isNull();
        boolean hasMechanic = mechanicNode != null && !mechanicNode.isNull();
        if (hasStat ^ hasEffect ^ hasMechanic ^ (hasStat && hasEffect && hasMechanic)) {
            // 恰一为真（下式等价展开，执行器可直接写三个 if 互斥判断）
        }
        if ((hasStat ? 1 : 0) + (hasEffect ? 1 : 0) + (hasMechanic ? 1 : 0) != 1) {
            fail(w, "stat / effect / mechanic 必须三选一（属性修正走 stat，开局 SHIELD 走 effect，触发式机制走 mechanic）");
        }
```
其余（示意，执行器补全逐词表）：mechanic 分支 `SynergyMechanic m = requireVocab(ee, "mechanic", SynergyMechanic.class, w);` 后按 K1 参数表 switch 校验必填项（如 BASIC_ATTACK_STATUS 必有 status+value+duration；DEATH_AUDIT 必有 value 且 0<v<100；target=TRAITS 时 trait 必填非空）；返回 `new EffectData(null, null, m, value, value2, status, duration, target, trait)`。units 键集 124-125 行追加 `"deathrattle", "undying"`；skills 键集追加 `"condition"`；parseSkillEffect 的 switch 追加：
```java
            case GAIN_ENERGY:
                if (value == null || value <= 0) { fail(w + "value", "GAIN_ENERGY 必须有 value > 0"); }
                if (status != null || duration != null) { fail(w, "GAIN_ENERGY 不允许 status/duration"); }
                break;
            case KNOCKBACK:
                if (value == null || value < 1f || value != Math.floor(value)) {
                    fail(w + "value", "KNOCKBACK 必须有整数 value ≥ 1（格）");
                }
                break;
            case BANISH:
                if (value != null || status != null || duration != null) {
                    fail(w, "BANISH 不携带 value/status/duration（条件在技能级 condition）");
                }
                break;
```
- **测试要点**：`JsonLoaderValidationTest` 增：三通道多选报错、mechanic 缺 trait 报错、BANISH 带 value 报错、deathrattle kind 未知报错、undying 负数报错、condition 缺 value 报错——每例断言异常文案含文件#id 定位前缀。

---

### CP4. synergies.json 全量重写（8 条 × 2/4/6 替换制）

- **类型**：重写数据文件
- **位置**：`assets/data/synergies.json`（整文件替换）
- **改动说明**：v5 §一逐格落地。stat 行走既有通道；触发档走 mechanic。数值均出自 v5 §一（兽人(6) 灼烧时长 v5 未写明，按 (4) 档 3s，登记 §8.2 R5）。
- **代码**（完整文件）：
```json
[
  { "id": "syn_orc", "name": "兽人", "desc": "焰痕部族的雇佣兵——每一道疤都是一次结清的账",
    "source": "RACE", "key": "兽人",
    "thresholds": [
      { "count": 2, "effects": [ { "stat": "lifesteal", "op": "ADD", "value": 12 } ] },
      { "count": 4, "effects": [ { "stat": "lifesteal", "op": "ADD", "value": 15 },
          { "mechanic": "BASIC_ATTACK_STATUS", "status": "BURN", "value": 30, "duration": 3,
            "target": "TRAITS", "trait": "兽人" } ] },
      { "count": 6, "effects": [ { "stat": "lifesteal", "op": "ADD", "value": 18 },
          { "mechanic": "BASIC_ATTACK_STATUS", "status": "BURN", "value": 35, "duration": 3,
            "target": "TRAITS", "trait": "兽人" },
          { "mechanic": "SCAR", "value": 3, "value2": 6, "target": "TRAITS", "trait": "兽人" } ] } ] },

  { "id": "syn_beast", "name": "野兽", "desc": "觉醒兽的猎群——猎物跑不掉",
    "source": "RACE", "key": "野兽",
    "thresholds": [
      { "count": 2, "effects": [ { "stat": "attackSpeed", "op": "PCT", "value": 15 },
          { "stat": "moveSpeed", "op": "ADD", "value": 0.4 } ] },
      { "count": 4, "effects": [ { "stat": "attackSpeed", "op": "PCT", "value": 20 },
          { "stat": "moveSpeed", "op": "ADD", "value": 0.6 },
          { "mechanic": "BASIC_ATTACK_STATUS", "status": "SLOW", "value": 30, "duration": 2,
            "target": "TRAITS", "trait": "野兽" } ] },
      { "count": 6, "effects": [ { "stat": "attackSpeed", "op": "PCT", "value": 25 },
          { "stat": "moveSpeed", "op": "ADD", "value": 0.8 },
          { "mechanic": "BASIC_ATTACK_STATUS", "status": "SLOW", "value": 35, "duration": 2.5,
            "target": "TRAITS", "trait": "野兽" },
          { "mechanic": "ENEMY_DEATH_SURGE", "value": 25, "duration": 4,
            "target": "TRAITS", "trait": "野兽" } ] } ] },

  { "id": "syn_undead", "name": "亡灵", "desc": "铁冠旧部不接受死亡——也不接受赖账",
    "source": "RACE", "key": "亡灵",
    "thresholds": [
      { "count": 2, "effects": [ { "mechanic": "DEATH_TAX", "value": 4, "value2": 20,
          "target": "TRAITS", "trait": "亡灵" } ] },
      { "count": 4, "effects": [ { "mechanic": "DEATH_TAX", "value": 5, "value2": 25,
          "target": "TRAITS", "trait": "亡灵" },
          { "mechanic": "DOUBLE_DEATHRATTLE", "target": "TRAITS", "trait": "亡灵" } ] },
      { "count": 6, "effects": [ { "mechanic": "DEATH_TAX", "value": 6, "value2": 30,
          "target": "TRAITS", "trait": "亡灵" },
          { "mechanic": "DOUBLE_DEATHRATTLE", "target": "TRAITS", "trait": "亡灵" },
          { "mechanic": "DEATH_AUDIT", "value": 25 } ] } ] },

  { "id": "syn_giant", "name": "巨人", "desc": "霜峰古族——城墙会还手",
    "source": "RACE", "key": "巨人",
    "thresholds": [
      { "count": 2, "effects": [ { "stat": "hp", "op": "ADD", "value": 180 },
          { "stat": "armor", "op": "ADD", "value": 15 } ] },
      { "count": 4, "effects": [ { "stat": "hp", "op": "ADD", "value": 330 },
          { "stat": "armor", "op": "ADD", "value": 25 },
          { "mechanic": "REFLECT", "value": 18, "target": "TRAITS", "trait": "巨人" } ] },
      { "count": 6, "effects": [ { "stat": "hp", "op": "ADD", "value": 360 },
          { "stat": "armor", "op": "ADD", "value": 30 },
          { "mechanic": "REFLECT", "value": 20, "target": "TRAITS", "trait": "巨人" },
          { "mechanic": "DAMAGE_REDUCTION", "value": 15, "target": "TRAITS", "trait": "巨人" },
          { "mechanic": "KNOCKBACK_IMMUNE", "target": "TRAITS", "trait": "巨人" },
          { "effect": "SHIELD", "value": 0.2 } ] } ] },

  { "id": "syn_warrior", "name": "战士", "desc": "执旗卫队——旗在，阵在",
    "source": "CLASS", "key": "战士",
    "thresholds": [
      { "count": 2, "effects": [ { "stat": "armor", "op": "ADD", "value": 18 } ] },
      { "count": 4, "effects": [ { "stat": "armor", "op": "ADD", "value": 38 },
          { "mechanic": "WARCRY_TAUNT", "value": 6, "value2": 3, "target": "TRAITS", "trait": "战士" } ] },
      { "count": 6, "effects": [ { "stat": "armor", "op": "ADD", "value": 60 },
          { "mechanic": "WARCRY_TAUNT", "value": 6, "value2": 3, "target": "TRAITS", "trait": "战士" },
          { "mechanic": "SHIELD_WALL", "target": "TRAITS", "trait": "战士" } ] } ] },

  { "id": "syn_mage", "name": "法师", "desc": "研究星辉残响的学者——我们先动手",
    "source": "CLASS", "key": "法师",
    "thresholds": [
      { "count": 2, "effects": [ { "stat": "skillPower", "op": "ADD", "value": 20 } ] },
      { "count": 4, "effects": [ { "stat": "skillPower", "op": "ADD", "value": 30 },
          { "mechanic": "SPELL_PEN", "value": 50, "target": "TRAITS", "trait": "法师" } ] },
      { "count": 6, "effects": [ { "stat": "skillPower", "op": "ADD", "value": 45 },
          { "mechanic": "SPELL_PEN", "value": 50, "target": "TRAITS", "trait": "法师" },
          { "mechanic": "OPENING_ENERGY", "value": 50, "target": "TRAITS", "trait": "法师" },
          { "mechanic": "AFTER_CAST_ENERGY", "value": 20, "target": "TRAITS", "trait": "法师" } ] } ] },

  { "id": "syn_assassin", "name": "刺客", "desc": "拾星者行会——账要当场结清",
    "source": "CLASS", "key": "刺客",
    "thresholds": [
      { "count": 2, "effects": [ { "stat": "attack", "op": "PCT", "value": 18 } ] },
      { "count": 4, "effects": [ { "stat": "attack", "op": "PCT", "value": 28 },
          { "mechanic": "EXECUTE", "value": 40, "value2": 35, "target": "TRAITS", "trait": "刺客" } ] },
      { "count": 6, "effects": [ { "stat": "attack", "op": "PCT", "value": 38 },
          { "mechanic": "EXECUTE", "value": 40, "value2": 35, "target": "TRAITS", "trait": "刺客" },
          { "mechanic": "KILL_ENERGY_REFUND", "target": "TRAITS", "trait": "刺客" } ] } ] },

  { "id": "syn_ranger", "name": "游侠", "desc": "失去森林的射手——箭术即乡愁",
    "source": "CLASS", "key": "游侠",
    "thresholds": [
      { "count": 2, "effects": [ { "stat": "range", "op": "ADD", "value": 1 } ] },
      { "count": 4, "effects": [ { "stat": "range", "op": "ADD", "value": 1 },
          { "stat": "attack", "op": "PCT", "value": 15 },
          { "stat": "critChance", "op": "ADD", "value": 15 },
          { "stat": "attackSpeed", "op": "PCT", "value": 10 } ] },
      { "count": 6, "effects": [ { "stat": "range", "op": "ADD", "value": 1 },
          { "stat": "attack", "op": "PCT", "value": 25 },
          { "stat": "critChance", "op": "ADD", "value": 20 },
          { "stat": "attackSpeed", "op": "PCT", "value": 15 },
          { "mechanic": "CRIT_RHYTHM", "value": 3, "value2": 50, "target": "TRAITS", "trait": "游侠" } ] } ] }
]
```
- **测试要点**：`JsonLoaderTest`（真实 assets 全量加载）零异常；`SynergySystemTest` 增快照断言：兽人 2/4/6 各档 stat 通道数值与 mechanic 列表内容（含 TRAITS trait 过滤）；SynergyAmp 放大仅作用 stat/effect 通道、mechanic 值不放大（裁决 D12 语义沿 stat/effect——mechanic 数值是否吃增幅 v5 未写，默认**不吃**，登记 §8.2 R6）。

---

### CP5. units.json 24 子重排 + skills.json +13 技能

- **类型**：重写/扩充数据文件
- **位置**：`assets/data/units.json`（非 Boss 24 条全量替换，9 条 Boss 原样保留）、`assets/data/skills.json`（追加 13 条）
- **改动说明**：v5 §2.1/§2.2 逐行落地。改名保 id 4 例（unit_mage_apprentice/unit_assassin_01/unit_fairy_druid/unit_legend_warsong_singer）；新增 6 例 id 出自 v5 §4.3；技能 id 命名沿 `skill_` 前缀。与 v5 §2.2 diff 表逐行对应：7 子不动 / 骸骨 atk→12+末班岗 / 怨灵 65/15 / 死骑 135/22/13+执旗冲锋+undying / 学徒→祭司 / 暗夜刺客→弃子 / 暗影改兽人降 2 费 / 丛林改野兽+缠绕箭 / 德鲁伊改野兽 / 歌者改巨人 / 军需官改兽人法师+发饷 / 冰巨改破阵踏。
- **代码**（units.json 非 Boss 24 条——逐条完整，Boss 9 条原样不动）：
```json
[
  { "id": "unit_warrior_01", "name": "兽人战士", "race": "兽人", "class": "战士", "cost": 1,
    "baseStats": { "hp": 100, "attack": 15, "armor": 10, "attackSpeed": 1.0, "range": 1, "moveSpeed": 1.0 },
    "upgradeMultiplier": 1.8,
    "defaultPriority": "NEAREST", "specialPriority": null,
    "skillId": "skill_warcry" },

  { "id": "unit_boar_rider", "name": "野猪骑士", "race": "兽人", "class": "战士", "cost": 1,
    "baseStats": { "hp": 110, "attack": 13, "armor": 12, "attackSpeed": 0.9, "range": 1, "moveSpeed": 1.2 },
    "upgradeMultiplier": 1.8, "skillId": "skill_rampage" },

  { "id": "unit_mage_apprentice", "name": "焰痕祭司", "race": "兽人", "class": "法师", "cost": 1,
    "baseStats": { "hp": 70, "attack": 12, "armor": 3, "attackSpeed": 0.8, "range": 3, "moveSpeed": 0.9 },
    "upgradeMultiplier": 1.8, "skillId": "skill_ignite" },

  { "id": "unit_legend_quartermaster", "name": "王家军需官", "race": "兽人", "class": "法师", "cost": 3,
    "baseStats": { "hp": 105, "attack": 16, "armor": 6, "attackSpeed": 0.9, "range": 3, "moveSpeed": 1.0 },
    "upgradeMultiplier": 1.8, "skillId": "skill_payday" },

  { "id": "unit_shadow_blade", "name": "暗影之刃", "race": "兽人", "class": "刺客", "cost": 2,
    "baseStats": { "hp": 80, "attack": 24, "armor": 5, "attackSpeed": 1.5, "range": 1, "moveSpeed": 1.9 },
    "upgradeMultiplier": 1.8,
    "defaultPriority": "NEAREST", "specialPriority": "BACKLINE",
    "skillId": "skill_execute" },

  { "id": "unit_spear_headhunter", "name": "掷矛猎头者", "race": "兽人", "class": "游侠", "cost": 1,
    "baseStats": { "hp": 70, "attack": 16, "armor": 3, "attackSpeed": 1.0, "range": 3, "moveSpeed": 0.9 },
    "upgradeMultiplier": 1.8, "skillId": "skill_headhunt" },

  { "id": "unit_wolf_pup", "name": "狼崽", "race": "野兽", "class": "刺客", "cost": 1,
    "baseStats": { "hp": 75, "attack": 17, "armor": 3, "attackSpeed": 1.4, "range": 1, "moveSpeed": 1.6 },
    "upgradeMultiplier": 1.8, "skillId": "skill_execute" },

  { "id": "unit_ambush_panther", "name": "伏击豹", "race": "野兽", "class": "刺客", "cost": 1,
    "baseStats": { "hp": 70, "attack": 16, "armor": 2, "attackSpeed": 1.3, "range": 1, "moveSpeed": 1.6 },
    "upgradeMultiplier": 1.8, "skillId": "skill_ambush_pounce" },

  { "id": "unit_beast_archer", "name": "兽猎手", "race": "野兽", "class": "游侠", "cost": 2,
    "baseStats": { "hp": 80, "attack": 19, "armor": 4, "attackSpeed": 1.1, "range": 3, "moveSpeed": 0.9 },
    "upgradeMultiplier": 1.8, "skillId": "skill_pierce" },

  { "id": "unit_ranger_01", "name": "丛林游侠", "race": "野兽", "class": "游侠", "cost": 2,
    "baseStats": { "hp": 70, "attack": 18, "armor": 3, "attackSpeed": 1.2, "range": 3, "moveSpeed": 0.8 },
    "upgradeMultiplier": 1.8, "skillId": "skill_entangle" },

  { "id": "unit_fairy_druid", "name": "荒野德鲁伊", "race": "野兽", "class": "法师", "cost": 2,
    "baseStats": { "hp": 95, "attack": 14, "armor": 5, "attackSpeed": 0.9, "range": 2, "moveSpeed": 1.0 },
    "upgradeMultiplier": 1.8, "skillId": "skill_mass_heal" },

  { "id": "unit_legend_thornhart", "name": "荆棘圣鹿", "race": "野兽", "class": "游侠", "cost": 3,
    "baseStats": { "hp": 110, "attack": 22, "armor": 6, "attackSpeed": 1.1, "range": 3, "moveSpeed": 1.0 },
    "upgradeMultiplier": 1.8, "skillId": "skill_pierce" },

  { "id": "unit_skeleton_soldier", "name": "骸骨士兵", "race": "亡灵", "class": "战士", "cost": 1,
    "baseStats": { "hp": 90, "attack": 12, "armor": 8, "attackSpeed": 1.0, "range": 1, "moveSpeed": 1.0 },
    "upgradeMultiplier": 1.8, "skillId": "skill_last_watch",
    "deathrattle": { "kind": "AREA_TRUE_DAMAGE", "radius": 1, "ratio": 0.8,
      "statusType": "SLOW", "statusPower": 30, "statusDuration": 2.5 } },

  { "id": "unit_debt_collector", "name": "收账人", "race": "亡灵", "class": "刺客", "cost": 1,
    "baseStats": { "hp": 70, "attack": 14, "armor": 3, "attackSpeed": 1.3, "range": 1, "moveSpeed": 1.5 },
    "upgradeMultiplier": 1.8,
    "defaultPriority": "NEAREST", "specialPriority": "LOWEST_HP",
    "skillId": "skill_repossess",
    "deathrattle": { "kind": "KILLER_TRUE_DAMAGE", "ratio": 1.5 } },

  { "id": "unit_wraith", "name": "怨灵", "race": "亡灵", "class": "法师", "cost": 2,
    "baseStats": { "hp": 65, "attack": 15, "armor": 2, "attackSpeed": 0.9, "range": 3, "moveSpeed": 0.9 },
    "upgradeMultiplier": 1.8, "skillId": "skill_poison_cloud",
    "deathrattle": { "kind": "AREA_TRUE_DAMAGE", "radius": 2, "ratio": 1.2 } },

  { "id": "unit_lamplighter", "name": "守灯人", "race": "亡灵", "class": "法师", "cost": 2,
    "baseStats": { "hp": 70, "attack": 13, "armor": 2, "attackSpeed": 0.8, "range": 3, "moveSpeed": 0.9 },
    "upgradeMultiplier": 1.8, "skillId": "skill_lamplight",
    "deathrattle": { "kind": "ALLY_REGEN", "regenRatio": 0.02, "duration": 5 } },

  { "id": "unit_bowstring_archer", "name": "空弦射手", "race": "亡灵", "class": "游侠", "cost": 2,
    "baseStats": { "hp": 75, "attack": 17, "armor": 3, "attackSpeed": 1.1, "range": 3, "moveSpeed": 0.9 },
    "upgradeMultiplier": 1.8,
    "defaultPriority": "NEAREST", "specialPriority": "HIGHEST_ATK",
    "skillId": "skill_audit_stamp",
    "deathrattle": { "kind": "DEATH_VOLLEY", "ratio": 1.8 } },

  { "id": "unit_death_knight", "name": "死亡骑士", "race": "亡灵", "class": "战士", "cost": 3,
    "baseStats": { "hp": 135, "attack": 22, "armor": 13, "attackSpeed": 0.9, "range": 1, "moveSpeed": 1.0 },
    "upgradeMultiplier": 1.8, "skillId": "skill_banner_charge",
    "undying": { "reviveDelaySeconds": 1.0, "reviveHpRatio": 0.4 } },

  { "id": "unit_frost_imp", "name": "小雪怪", "race": "巨人", "class": "刺客", "cost": 1,
    "baseStats": { "hp": 85, "attack": 15, "armor": 5, "attackSpeed": 1.2, "range": 1, "moveSpeed": 1.4 },
    "upgradeMultiplier": 1.8, "skillId": "skill_execute" },

  { "id": "unit_rubble_slinger", "name": "碎石仔", "race": "巨人", "class": "游侠", "cost": 1,
    "baseStats": { "hp": 85, "attack": 14, "armor": 6, "attackSpeed": 0.7, "range": 3, "moveSpeed": 0.8 },
    "upgradeMultiplier": 1.8, "skillId": "skill_rock_toss" },

  { "id": "unit_frost_giant", "name": "霜巨人", "race": "巨人", "class": "战士", "cost": 2,
    "baseStats": { "hp": 150, "attack": 18, "armor": 16, "attackSpeed": 0.7, "range": 1, "moveSpeed": 0.8 },
    "upgradeMultiplier": 1.8, "skillId": "skill_warcry" },

  { "id": "unit_assassin_01", "name": "霜峰弃子", "race": "巨人", "class": "刺客", "cost": 2,
    "baseStats": { "hp": 85, "attack": 22, "armor": 6, "attackSpeed": 1.4, "range": 1, "moveSpeed": 1.7 },
    "upgradeMultiplier": 1.8,
    "defaultPriority": "NEAREST", "specialPriority": null,
    "skillId": "skill_ambush_snow" },

  { "id": "unit_glacial_giant", "name": "冰霜巨人", "race": "巨人", "class": "战士", "cost": 3,
    "baseStats": { "hp": 170, "attack": 26, "armor": 18, "attackSpeed": 0.7, "range": 1, "moveSpeed": 0.8 },
    "upgradeMultiplier": 1.8, "skillId": "skill_break_stomp" },

  { "id": "unit_legend_warsong_singer", "name": "冰川颂者", "race": "巨人", "class": "法师", "cost": 3,
    "baseStats": { "hp": 95, "attack": 15, "armor": 5, "attackSpeed": 0.9, "range": 3, "moveSpeed": 0.9 },
    "upgradeMultiplier": 1.8, "skillId": "skill_mass_heal" }
]
```
（实际文件在上述 24 条之后原样保留 9 条 Boss 条目与既有格式。）

skills.json 追加 13 条（完整）：
```json
  { "id": "skill_ignite", "name": "点燃", "desc": "火苗咬进伤口，烧个不停",
    "shape": "SINGLE_TARGET", "delivery": "HOMING",
    "effects": [ { "effect": "DAMAGE", "value": 1.5 },
                 { "effect": "APPLY_STATUS", "status": "BURN", "value": 0.3, "duration": 4 } ] },

  { "id": "skill_headhunt", "name": "猎头", "desc": "掷出取命的短矛",
    "shape": "SINGLE_TARGET", "delivery": "HOMING",
    "effects": [ { "effect": "DAMAGE", "value": 2.0 } ] },

  { "id": "skill_ambush_pounce", "name": "伏击", "desc": "自暗处扑出，一击定身",
    "shape": "SINGLE_TARGET", "delivery": "HOMING",
    "effects": [ { "effect": "DAMAGE", "value": 1.8 },
                 { "effect": "APPLY_STATUS", "status": "STUN", "duration": 1 } ] },

  { "id": "skill_entangle", "name": "缠绕箭", "desc": "藤箭缠足，越追越远",
    "shape": "SINGLE_TARGET", "delivery": "HOMING",
    "effects": [ { "effect": "DAMAGE", "value": 1.6 },
                 { "effect": "APPLY_STATUS", "status": "SLOW", "value": 40, "duration": 3 } ] },

  { "id": "skill_ambush_snow", "name": "雪伏", "desc": "雪坡上的伏击，冷且重",
    "shape": "SINGLE_TARGET", "delivery": "HOMING",
    "effects": [ { "effect": "DAMAGE", "value": 2.3 },
                 { "effect": "APPLY_STATUS", "status": "SLOW", "value": 50, "duration": 2.5 } ] },

  { "id": "skill_rock_toss", "name": "抛石", "desc": "巨人的远程=扔石头",
    "shape": "AOE_1", "delivery": "HOMING",
    "effects": [ { "effect": "DAMAGE", "value": 1.3 } ] },

  { "id": "skill_break_stomp", "name": "破阵踏", "desc": "一脚踩散阵型",
    "shape": "AOE_1", "delivery": "MELEE_INSTANT",
    "effects": [ { "effect": "DAMAGE", "value": 1.2 },
                 { "effect": "KNOCKBACK", "value": 1 } ] },

  { "id": "skill_last_watch", "name": "末班岗", "desc": "最后一班岗，站到盾碎",
    "shape": "SELF", "delivery": "MELEE_INSTANT",
    "effects": [ { "effect": "SHIELD", "value": 0.3 } ] },

  { "id": "skill_lamplight", "name": "守夜", "desc": "灯还亮着，夜就不算深",
    "shape": "ALL_ALLIES", "delivery": "MELEE_INSTANT",
    "effects": [ { "effect": "SHIELD", "value": 0.12 } ] },

  { "id": "skill_audit_stamp", "name": "验讫", "desc": "盖在敌军军官身上的收讫章",
    "shape": "SINGLE_TARGET", "delivery": "HOMING",
    "effects": [ { "effect": "DAMAGE", "value": 2.2 } ] },

  { "id": "skill_banner_charge", "name": "执旗冲锋", "desc": "旗还没倒，他也不会倒",
    "shape": "SELF", "delivery": "MELEE_INSTANT",
    "effects": [ { "effect": "APPLY_STATUS", "status": "ATK_UP", "value": 35, "duration": 8 },
                 { "effect": "SHIELD", "value": 0.2 } ] },

  { "id": "skill_repossess", "name": "依账除名", "desc": "账本最后一页，划掉一个名字",
    "shape": "SINGLE_TARGET", "delivery": "MELEE_INSTANT",
    "effects": [ { "effect": "BANISH" } ],
    "condition": { "type": "TARGET_HP_BELOW", "value": 0.2 } },

  { "id": "skill_payday", "name": "发饷", "desc": "受雇于部族的旧王国账房",
    "shape": "ALL_ALLIES", "delivery": "MELEE_INSTANT",
    "effects": [ { "effect": "APPLY_STATUS", "status": "ATK_UP", "value": 15, "duration": 5 },
                 { "effect": "GAIN_ENERGY", "value": 20 } ] }
```
（执旗冲锋/白骨滩 SLOW/双响锚点数值出自示范稿 §设计正文三；末班岗/守夜盾无时限沿 v5 §2.1 与现有 SHIELD 词汇，见 §8.2 R3。）
- **测试要点**：新建 `config/UnitsMatrixTest`（读真实 assets）：① 费阶 1×10 / 2×9 / 3×5；② 传奇三子 cost=3 且不在任何 shopUnlocks；③ 4 种族 × 4 职业行列各 6（v5 §4.4 矩阵数数断言）；③ 亡灵 6 子全部携带 deathrattle 或 undying（死骑）且其余 18 子不携带；⑤改名 4 例 id 不变、名称已换。

---

### CP6. scenes.json shopUnlocks / enemyPool 重排（D10 雏形）

- **类型**：重写数据文件
- **位置**：`assets/data/scenes.json`（整文件替换）
- **改动说明**：v5 §4.3 逐行：墓穴 shopUnlocks=6 亡灵全列、雪山=5 非传奇巨人（颂者不入列，走奥兰多 Lv.3 门控）；enemyPool 主题重排（weight/minRound 按 v5）。掷矛/伏豹不入任何 shopUnlocks → 自动进基础池（森林起可购，v5 §4.4 森林 6 档清单前提）。
- **代码**（完整文件）：
```json
[
  {
    "id": "scene_forest", "name": "翡翠林地", "unlockAfter": null,
    "enemyPool": [
      { "unitId": "unit_wolf_pup",          "weight": 3, "minRound": 1 },
      { "unitId": "unit_beast_archer",      "weight": 2, "minRound": 2 },
      { "unitId": "unit_ambush_panther",    "weight": 2, "minRound": 5 },
      { "unitId": "unit_warrior_01",        "weight": 1, "minRound": 6 },
      { "unitId": "unit_ranger_01",         "weight": 1, "minRound": 8 },
      { "unitId": "unit_spear_headhunter",  "weight": 1, "minRound": 10 }
    ],
    "bosses": { "7": "boss_thorn_mother", "15": "boss_one_eye", "25": "boss_thorn_true" },
    "shopUnlocks": []
  },
  {
    "id": "scene_crypt", "name": "亡者墓穴", "unlockAfter": "scene_forest",
    "enemyPool": [
      { "unitId": "unit_skeleton_soldier",  "weight": 3, "minRound": 1 },
      { "unitId": "unit_wraith",            "weight": 2, "minRound": 4 },
      { "unitId": "unit_debt_collector",    "weight": 2, "minRound": 6 },
      { "unitId": "unit_bowstring_archer",  "weight": 1, "minRound": 8 },
      { "unitId": "unit_death_knight",      "weight": 1, "minRound": 10 }
    ],
    "bosses": { "7": "boss_tomb_colossus", "15": "boss_twin_lich", "25": "boss_bone_duke" },
    "shopUnlocks": ["unit_skeleton_soldier", "unit_wraith", "unit_debt_collector",
                    "unit_bowstring_archer", "unit_lamplighter", "unit_death_knight"]
  },
  {
    "id": "scene_snow", "name": "寒峰雪山", "unlockAfter": "scene_crypt",
    "enemyPool": [
      { "unitId": "unit_frost_imp",         "weight": 3, "minRound": 1 },
      { "unitId": "unit_rubble_slinger",    "weight": 2, "minRound": 3 },
      { "unitId": "unit_frost_giant",       "weight": 1, "minRound": 6 },
      { "unitId": "unit_assassin_01",       "weight": 2, "minRound": 8 },
      { "unitId": "unit_glacial_giant",     "weight": 1, "minRound": 12 }
    ],
    "bosses": { "7": "boss_frost_howler", "15": "boss_star_breaker", "25": "boss_star_warden" },
    "shopUnlocks": ["unit_frost_imp", "unit_rubble_slinger", "unit_frost_giant",
                    "unit_assassin_01", "unit_glacial_giant"]
  }
]
```
- **测试要点**：`JsonLoaderScenesTest` 更新 + 断言：三场景全量加载零异常；墓穴 6 亡灵/雪山 5 巨人 shopUnlocks 与传奇互斥校验通过；森林敌池首条 minRound=1（S5）；`WaveGeneratorTest` 回归（新池可抽、布阵可放满）。

---

### CP7. D10 敌方羁绊档位截断（resolve 计数上限）

- **类型**：修改方法 / 新增常量 / 修改签名
- **位置**：`systems/SynergySystem.java:33-44`、`config/GameBalance.java`、`systems/BattleSystem.java:63-90`、调用方 `systems/RunFlowSystem.java:77`、`tools/BattleConsoleMain.java:67`
- **改动说明**：v5 §4.1 末行「WaveGenerator 组阵后、resolve 前截断计数：r1~5 无、r6~12 ≤2 档、r13~19 ≤4 档、r20+ ≤6 档」。实现为 resolve 可选 `countCap`（cap 后再 activeThreshold）；BattleSystem 增 `round` 入参（敌方侧 cap；玩家侧不 cap；旧重载委托 round=TOTAL_ROUNDS=等效不 cap，存量测试不动）。
- **代码**：

`systems/SynergySystem.java` 修改前（42-44 行，resolve 带 amp 重载签名）：
```java
    public SynergySnapshot resolve(Collection<UnitData> templates, GameData data,
                                   Map<String, Float> synergyAmp) {
```
修改后（新增带 cap 重载，旧两个重载委托 cap=Integer.MAX_VALUE）：
```java
    public SynergySnapshot resolve(Collection<UnitData> templates, GameData data,
                                   Map<String, Float> synergyAmp) {
        return resolve(templates, data, synergyAmp, Integer.MAX_VALUE);
    }

    /** D10 敌方档位截断：每个羁绊计数取 min(count, countCap) 后再判档（r1~5 传 0 = 敌方无羁绊效果） */
    public SynergySnapshot resolve(Collection<UnitData> templates, GameData data,
                                   Map<String, Float> synergyAmp, int countCap) {
```
（方法体内 57-60 行计数处：`if (synergy.getKey().equals(value)) { count++; }` 后于 activeThreshold 前插 `count = Math.min(count, countCap);`。）

`config/GameBalance.java` 新增：
```java
    /** D10 敌方羁绊档位上限：r1~5=0（无）、r6~12=2、r13~19=4、r20+=6（v5 §4.1 临时口径） */
    public static int enemySynergyCountCap(int round) {
        checkRound(round);
        if (round <= 5) { return 0; }
        if (round <= 12) { return 2; }
        if (round <= 19) { return 4; }
        return 6;
    }
```

`systems/BattleSystem.java` 修改前（73-75 行 + 90 行）：
```java
    public BattleState startBattle(Player player, List<WaveSpec> enemyWave,
                                   GameData data, RandomGenerator rng, IdIssuer idIssuer,
                                   RunModifiers modifiers) {
```
```java
        SynergySnapshot enemySynergies = synergySystem.resolve(enemyTemplates, data);
```
修改后：新增六参全量重载 `startBattle(..., RunModifiers modifiers, int round)`，其内敌方行改为：
```java
        SynergySnapshot enemySynergies = synergySystem.resolve(enemyTemplates, data,
                java.util.Collections.<String, Float>emptyMap(),
                GameBalance.enemySynergyCountCap(round));
```
既有五参/六参重载委托 `round = TOTAL_ROUNDS`（cap=6，对 count≤6 阵容等效不截断；>6 同名池敌阵罕见，行为差异登记 §8.2 R7）。`RunFlowSystem.java:77` 与 `BattleConsoleMain.java:67` 改传 `runState.getRound()` / 控制台轮次参数。

**本 CP 一并落位「机制下发」基建**（批2~4 的 `unit.getMechanic(kind)` 依赖此，K13「schema 先行」）：① `SynergySnapshot` 新增 `List<EffectData> mechanics` 与 getter——`SynergySystem.resolve` 分流规则：`isStatChannel()` → statModifiers；`effect == SHIELD` 或 `mechanic == OPENING_ENERGY` → openingEffects（开局通道，CP22 消费）；其余非 stat（mechanic）→ mechanics；② `BattleUnit` 新增不可变字段 `List<EffectData> mechanics`（构造器尾部可缺省空列表）与查询：
```java
    /** 本单位命中的机制词（K1：按 target 谓词过滤后下发；无=null） */
    public EffectData getMechanic(SynergyMechanic kind) {
        for (EffectData effect : mechanics) {
            if (effect.getMechanic() == kind) {
                return effect;
            }
        }
        return null;
    }
```
③ `BattleSystem.deriveUnit` 末尾按谓词过滤下发（谓词方法 `effectAppliesTo` 与 CP22 共用，本 CP 先落位于 deriveUnit 旁）：
```java
        List<EffectData> granted = new ArrayList<EffectData>();
        for (EffectData effect : synergies.getMechanics()) {
            if (effectAppliesTo(effect, template)) {
                granted.add(effect);
            }
        }
        unit.setMechanics(granted); // framework-internal 写一次（ALLIES 全量 / TRAITS+trait 匹配 race 或 class）
```
（批0 阶段无系统读取 mechanics——惰性数据，行为零变化；批2 CP15/16/19、批3 CP20/21、批4 CP22~27 直接消费。）
- **测试要点**：`SynergySystemTest` 增 cap 四档断言（count 7 + cap 2 → (2) 档；cap 0 → EMPTY）；`BattleSystemTest` 增敌方 r2 双骸骨不触发亡灵(2)（cap 0）、r8 触发（cap 2）。

---

### CP8. 放逐原语 + BANISH/GAIN_ENERGY/KNOCKBACK 执行 + BURN 心跳接线

- **类型**：新建类 / 修改方法 ×4 / 修改字段
- **位置**：新建 `systems/Banish.java`、`entities/BattleUnit.java`（markBanished）、`entities/CombatEvent.java`（新事件词 + 工厂）、`systems/SkillExecutor.java`（cast 条件 + 三新词执行 + isDotType）、`systems/StatusSystem.java:91-95`（BURN 心跳）、`systems/DamagePipeline.java`（BANISH 无伤害路径不涉及）
- **改动说明**：K13「薄执行」落地——批0 起任何可购棋子的主动技 cast 不抛异常。放逐=C10 独立移除路径（点算 CP16 复用）。
- **代码**：

新建 `systems/Banish.java`（完整）：
```java
package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;

/** 放逐（C10）：独立移除路径——不走伤害管线与清扫，不触发亡语/不死/名册/点算，不视为击杀。
 *  索敌失效依赖 currentTarget 对 cleaned 的惰性重选（现行为），不强制 retarget。 */
public final class Banish {
    private Banish() {
    }

    /** 移除一个在场单位（对已清扫者幂等空操作） */
    public static void execute(BattleState state, BattleUnit victim) {
        if (victim == null || victim.isCleaned()) {
            return;
        }
        victim.markBanished();
        state.removeFromGrid(victim);
        state.record(CombatEvent.unitBanished(state.getTick(), victim.getId()));
    }
}
```

`entities/BattleUnit.java` 修改前（207-212 行）：
```java
    /** framework-internal：清扫（死亡标记）——状态销毁、目标失效 */
    public void markCleaned() {
        cleaned = true;
        statuses.clear();
        targetId = -1;
    }
```
修改后（新增 banished 标记与 markBanished；isBanished 供渲染区分淡出表现）：
```java
    /** framework-internal：清扫（死亡标记）——状态销毁、目标失效 */
    public void markCleaned() {
        cleaned = true;
        statuses.clear();
        targetId = -1;
    }

    private boolean banished;

    /** framework-internal：放逐移除（C10 非死亡）——同清扫清场，但额外携带 banished 标记供表现层区分 */
    public void markBanished() {
        markCleaned();
        banished = true;
    }

    /** 放逐移除标记（仅表现层读取；机制语义全部由「非死亡事件」口径保证） */
    public boolean isBanished() {
        return banished;
    }
```

`entities/CombatEvent.java`：Type 枚举追加 `UNIT_BANISHED`（后续 CP17/CP18 追加 UNIT_DOWNED/UNIT_REVIVED/PROJECTILE_BLOCKED 同法）；新增工厂：
```java
    /** 放逐移除（非死亡事件，C10）：sourceId = -1，targetId = 被放逐者 */
    public static CombatEvent unitBanished(int tick, int targetId) {
        return new CombatEvent(tick, Type.UNIT_BANISHED, -1, targetId, 0f, 0f, false, null, null, null);
    }
```

`systems/SkillExecutor.java` 修改前（57-60 行）：
```java
        BattleUnit mainTarget = resolveMainTarget(state, caster, skill);
        if (mainTarget == null) {
            return false; // 延后（口径 #18）
        }
```
修改后（插入技能级条件检查）：
```java
        BattleUnit mainTarget = resolveMainTarget(state, caster, skill);
        if (mainTarget == null) {
            return false; // 延后（口径 #18）
        }
        if (!castConditionMet(skill, mainTarget)) {
            return false; // 条件不满足 → 延后（依账除名「持能等待」）
        }
```
（新增私有静态方法 `castConditionMet`：condition 为 null 恒 true；TARGET_HP_BELOW → `mainTarget.hpRatio() < condition.getValue()`。）

`applyOneEffect` 的 switch（221 行起）追加三 case（放在 default 前）：
```java
            case GAIN_ENERGY:
                damagePipeline.gainEnergy(state, target, value); // 走回能管线（受 energyGainRate 加权）
                break;
            case KNOCKBACK:
                applyKnockback(state, target, caster, (int) value);
                break;
            case BANISH:
                Banish.execute(state, target);
                break;
```
（新增私有静态 `applyKnockback`：目标含 KNOCKBACK_IMMUNE 机制（经 CP7 下发的 unit.getMechanic）或已倒地/清扫则不动；方向=主轴远离施放者（|dx|≥|dy| 取 x 向，否则 y 向；同格不动），逐格 `removeFromGrid+placeUnit`，目标格被占/越界即止。）

`isDotType` 修改前（247-249 行）：
```java
    private static boolean isDotType(StatusType status) {
        return status == StatusType.BLEED || status == StatusType.POISON;
    }
```
修改后：
```java
    private static boolean isDotType(StatusType status) {
        return status == StatusType.BLEED || status == StatusType.POISON || status == StatusType.BURN;
    }
```

`systems/StatusSystem.java` 修改前（91-95 行）：
```java
    private void tickHeartbeats(BattleState state, BattleUnit owner, ActiveStatus status, float dt) {
        if (status.getType() != StatusType.BLEED && status.getType() != StatusType.POISON
                && status.getType() != StatusType.REGEN) {
            return; // 非 DOT/HOT 类无心跳
        }
```
修改后：
```java
    private void tickHeartbeats(BattleState state, BattleUnit owner, ActiveStatus status, float dt) {
        if (status.getType() != StatusType.BLEED && status.getType() != StatusType.POISON
                && status.getType() != StatusType.BURN
                && status.getType() != StatusType.REGEN) {
            return; // 非 DOT/HOT 类无心跳
        }
```
（tickHeartbeats 的 DOT 分支走 applyTrueDamage——BURN 自动继承「真伤、可致死、无回能无吸血」与同 type 刷新取大。）
- **测试要点**：新建 `systems/BanishTest`：放逐后 isCleaned/isBanished、无 UNIT_DIED 事件、有 UNIT_BANISHED、棋盘格腾空；`SkillExecutorTest` 增：依账除名目标 19% 施放成功（目标消失）/ 21% 延后能量保留；GAIN_ENERGY 受 energyGainRate 加权；KNOCKBACK 推格、被占即止；`StatusSystemTest` 增 BURN 每跳真伤、刷新取大。

---

### CP9. UnitShapeKit（种族=图形族 × 职业=变体）+ StarColors（星级=颜色）

- **类型**：新建文件 ×2（纯 JVM，零 Gdx，headless 可测）
- **位置**：新建 `render/UnitShapeKit.java`、`render/StarColors.java`
- **改动说明**：派单渲染要求一/二的数据层。**图形=身份**：种族→图形族（兽人=三角·三矛雇佣兵 / 野兽=菱形·灵动猎群 / 亡灵=五边形·铁冠 / 巨人=六边形·壁垒；风味与 Boss 种族兜底=五角星·异形）；职业→族内变体（战士=顶部横带，承接现「职业顶条」习惯 / 法师=中心核点 / 刺客=斜切缺口 / 游侠=双层空心 / Boss 与无职业=纯实心）。**颜色=级别**：星级 1/2/3 → 骨白/金/紫（common→rare→epic 通用梯度），与敌我框（SideColors 蓝/红）、血条（红绿）、能量条（黄）全部错开；敌阵恒 1 星=统一骨白，配合红框=敌方群像读法干净。两轴正交：升级只换色不换形、换棋子只换形不 confusion 级别。行规格以「中心对齐的逐行半宽表」表达（像素风手挑、确定性、32px 画布内 24px 本体）。
- **代码**：

新建 `render/UnitShapeKit.java`（完整；行表为手挑初值，冒烟后可微调——数值不属机制层）：
```java
package com.voidvvv.kz_auto_chess_n.render;

import com.voidvvv.kz_auto_chess_n.data.UnitData;

/** 单位占位图形目录（render 表达层；纯 JVM 可测）。图形=种族族、变体=职业（派单渲染要求一）。
 *  行规格：{y, halfWidth}（中心对齐逐行半宽，y 自本体顶部起）——PlaceholderArt 按行填充。 */
public final class UnitShapeKit {

    /** 图形族：种族身份的第一读法轴 */
    public enum ShapeFamily { TRIANGLE, DIAMOND, PENTAGON, HEXAGON, STAR }

    /** 族内变体：职业的第二读法轴（标记色深于本体，tint 后仍可辨） */
    public enum ClassVariant { WARRIOR, MAGE, ASSASSIN, RANGER, PLAIN }

    private UnitShapeKit() {
    }

    /** 种族 → 图形族；未登记风味种族（植物/构造/独眼等 Boss 种族）→ STAR 兜底 */
    public static ShapeFamily familyOf(String race) {
        switch (race) {
            case "兽人": return ShapeFamily.TRIANGLE;
            case "野兽": return ShapeFamily.DIAMOND;
            case "亡灵": return ShapeFamily.PENTAGON;
            case "巨人": return ShapeFamily.HEXAGON;
            default: return ShapeFamily.STAR;
        }
    }

    /** 职业 → 族内变体；Boss/未登记职业 → PLAIN */
    public static ClassVariant variantOf(String unitClass) {
        switch (unitClass) {
            case "战士": return ClassVariant.WARRIOR;
            case "法师": return ClassVariant.MAGE;
            case "刺客": return ClassVariant.ASSASSIN;
            case "游侠": return ClassVariant.RANGER;
            default: return ClassVariant.PLAIN;
        }
    }

    /** 本体高 22 行的逐行半宽表（0 顶 → 21 底；执行期允许按冒烟观感微调，行数不变） */
    public static int[] halfWidths(ShapeFamily family) {
        final int[] TRIANGLE = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 10, 11};      // 12 行（底 23 宽→顶尖）
        final int[] DIAMOND  = {1, 3, 5, 7, 9, 11, 11, 9, 7, 5, 3, 1};      // 12 行
        final int[] PENTAGON = {4, 6, 8, 10, 11, 11, 11, 11, 11, 11, 11, 11}; // 上梯形+下矩形
        final int[] HEXAGON  = {7, 9, 11, 11, 11, 11, 11, 11, 11, 11, 9, 7};
        final int[] STAR     = {1, 2, 2, 5, 8, 10, 8, 5, 3, 5, 9, 4};        // 五角星近似行表
        switch (family) {
            case TRIANGLE: return TRIANGLE;
            case DIAMOND: return DIAMOND;
            case PENTAGON: return PENTAGON;
            case HEXAGON: return HEXAGON;
            case STAR: return STAR;
        }
        throw new IllegalArgumentException("未知图形族: " + family);
    }

    /** 变体标记绘制参数（PlaceholderArt 消费；均为深色标记，乘法 tint 下保持可辨） */
    public static int[] variantMarkRows(ClassVariant variant) {
        // {起始行, 行数}：战士顶带 0~1；法师核点中心 5~6；刺客斜切 3~9；游侠空心=减行宽由画法处理
        switch (variant) {
            case WARRIOR: return new int[]{0, 2};
            case MAGE: return new int[]{5, 2};
            case ASSASSIN: return new int[]{3, 7};
            default: return new int[]{0, 0}; // RANGER/PLAIN 无附加实心带
        }
    }
}
```

新建 `render/StarColors.java`（完整）：
```java
package com.voidvvv.kz_auto_chess_n.render;

import com.badlogic.gdx.graphics.Color;

/** 星级色（派单渲染要求二：颜色=级别，与图形正交）。
 *  1★ 骨白 / 2★ 金 / 3★ 紫——common→rare→epic 梯度；与 SideColors（蓝/红框）、
 *  血条红绿、能量黄全部错开（2★ 金较能量黄更饱和且载体为 24px 本体，可辨）。 */
public final class StarColors {
    public static final Color STAR_1 = new Color(0.82f, 0.82f, 0.78f, 1f);
    public static final Color STAR_2 = new Color(0.98f, 0.78f, 0.22f, 1f);
    public static final Color STAR_3 = new Color(0.72f, 0.40f, 0.96f, 1f);

    private StarColors() {
    }

    /** 星级 → tint 色（越界钳制到 1/3 星） */
    public static Color of(int star) {
        if (star <= 1) { return STAR_1; }
        if (star == 2) { return STAR_2; }
        return STAR_3;
    }
}
```
- **测试要点**：新建 `render/UnitShapeKitTest`：familyOf/variantOf 全词表映射断言 + 未登记兜底（植物→STAR、Boss→PLAIN）+ halfWidths 全族长度>0 且逐值>0；新建 `render/StarColorsTest`：1/2/3 星三色互异、与 SideColors.PLAYER/ENEMY 色距断言（RGB 欧氏距离 > 阈值）。

---

### CP10. PlaceholderArt 形状绘制改造（🔴 小步：先 1 子 → 冒烟 → 铺开）

- **类型**：修改方法（两段提交：提交 a 仅 pilot 白名单 1 子生效；冒烟通过后提交 b 删白名单铺开）
- **位置**：`render/PlaceholderArt.java:91-106`（paintUnit）
- **改动说明**：本体从「种族底色方块+职业顶条」改为「形状族白体（0.92 亮度，保留帧亮度脉冲余地）+ 黑描边 + 深色职业变体标记」；种族色从本体移除（身份改由图形承载）。tint 乘法下：白体×星级色=星级色、黑描边/深色标记保持暗色。**红线③**：`SHAPE_PILOT_UNIT_IDS` 集合初始只含 `unit_warrior_01`，`gradlew :lwjgl3:run` 人工确认（三角/顶带/骨白/蓝框、敌向翻转正常）后清空集合铺开。RealArt 逐 key 覆盖机制不变（素材到位即整体可替换）。
- **代码**：

修改前（91-106 行）：
```java
    private Pixmap paintUnit(UnitData tpl, String anim, int frame) {
        com.badlogic.gdx.graphics.Color race = PalettePick.pick(tpl.getRace());
        com.badlogic.gdx.graphics.Color clazz = PalettePick.pick(tpl.getUnitClass());
        float alpha = PlaceholderKeys.ANIM_DEATH.equals(anim) ? deathAlpha(frame) : 1f;
        int dx = PlaceholderKeys.ANIM_ATTACK.equals(anim) ? 2 : 0;  // 出手前移（朝向翻转归 UnitView）
        int dy = PlaceholderKeys.ANIM_WALK.equals(anim) ? (frame == 0 ? -1 : 1) : 0;
        float bright = frameBrightness(anim, frame);

        Pixmap pm = new Pixmap(UNIT_SIZE, UNIT_SIZE, Pixmap.Format.RGBA8888);
        // 身体（种族底色）+ 描边 + 职业顶条
        fillRect(pm, 4 + dx, 6 + dy, 24, 22, race.r * bright, race.g * bright, race.b * bright, alpha);
        fillRect(pm, 4 + dx, 6 + dy, 24, 5, clazz.r * bright, clazz.g * bright, clazz.b * bright, alpha);
        pm.setColor(0f, 0f, 0f, alpha * 0.5f);
        pm.drawRectangle(4 + dx, 6 + dy, 24, 22);
        return pm;
    }
```
修改后：
```java
    /** 图形化小步验证白名单（主人红线③）：非空时仅名单内单位走形状绘制；冒烟通过后清空铺开 */
    private static final java.util.Set<String> SHAPE_PILOT_UNIT_IDS =
            new java.util.HashSet<String>(java.util.Arrays.asList("unit_warrior_01"));

    private Pixmap paintUnit(UnitData tpl, String anim, int frame) {
        float alpha = PlaceholderKeys.ANIM_DEATH.equals(anim) ? deathAlpha(frame) : 1f;
        int dx = PlaceholderKeys.ANIM_ATTACK.equals(anim) ? 2 : 0;  // 出手前移（朝向翻转归 UnitView）
        int dy = PlaceholderKeys.ANIM_WALK.equals(anim) ? (frame == 0 ? -1 : 1) : 0;
        float bright = frameBrightness(anim, frame);
        if (!SHAPE_PILOT_UNIT_IDS.isEmpty() && !SHAPE_PILOT_UNIT_IDS.contains(tpl.getId())) {
            return paintLegacyBlock(tpl, anim, frame); // 旧画法保留为铺开前的对照组
        }

        UnitShapeKit.ShapeFamily family = UnitShapeKit.familyOf(tpl.getRace());
        UnitShapeKit.ClassVariant variant = UnitShapeKit.variantOf(tpl.getUnitClass());
        int[] halfWidths = UnitShapeKit.halfWidths(family);
        int top = 6 + dy;                       // 本体顶部（32px 画布内 22px 高）
        int centerX = 16 + dx;

        Pixmap pm = new Pixmap(UNIT_SIZE, UNIT_SIZE, Pixmap.Format.RGBA8888);
        // 形状本体：白体系（0.92 基准亮度 × 帧亮度）——运行期由 UnitView 以星级色 tint（CP11）
        float body = 0.92f * bright;
        for (int row = 0; row < halfWidths.length; row++) {
            int half = halfWidths[row];
            boolean hollow = variant == UnitShapeKit.ClassVariant.RANGER
                    && row >= 2 && row < halfWidths.length - 2; // 游侠=空心（仅描边行区内缩 2px）
            if (!hollow) {
                fillRect(pm, centerX - half, top + row * 22 / halfWidths.length,
                        half * 2, 22 / halfWidths.length + 1, body, body, body, alpha);
            }
        }
        // 职业变体标记（深色，tint 后仍暗）
        int[] mark = UnitShapeKit.variantMarkRows(variant);
        if (mark[1] > 0) {
            for (int row = mark[0]; row < mark[0] + mark[1] && row < halfWidths.length; row++) {
                int half = halfWidths[row];
                if (variant == UnitShapeKit.ClassVariant.ASSASSIN) {
                    fillRect(pm, centerX - half, top + row * 22 / halfWidths.length, // 斜切：左侧楔形缺口
                            Math.max(1, half / 2), 1, 0.12f, 0.12f, 0.14f, alpha);
                } else {
                    fillRect(pm, centerX - half, top + row * 22 / halfWidths.length,
                            half * 2, 1, 0.12f, 0.12f, 0.14f, alpha);
                }
            }
        }
        // 外描边：逐行首尾像素（像素风 1px 描边）
        pm.setColor(0f, 0f, 0f, alpha * 0.6f);
        for (int row = 0; row < halfWidths.length; row++) {
            int half = halfWidths[row];
            pm.fillRectangle(centerX - half, top + row * 22 / halfWidths.length, 1,
                    22 / halfWidths.length + 1);
            pm.fillRectangle(centerX + half - 1, top + row * 22 / halfWidths.length, 1,
                    22 / halfWidths.length + 1);
        }
        return pm;
    }

    /** 旧方块画法（铺开前的对照组；铺开后整段删除） */
    private Pixmap paintLegacyBlock(UnitData tpl, String anim, int frame) {
        com.badlogic.gdx.graphics.Color race = PalettePick.pick(tpl.getRace());
        com.badlogic.gdx.graphics.Color clazz = PalettePick.pick(tpl.getUnitClass());
        float alpha = PlaceholderKeys.ANIM_DEATH.equals(anim) ? deathAlpha(frame) : 1f;
        int dx = PlaceholderKeys.ANIM_ATTACK.equals(anim) ? 2 : 0;
        int dy = PlaceholderKeys.ANIM_WALK.equals(anim) ? (frame == 0 ? -1 : 1) : 0;
        float bright = frameBrightness(anim, frame);
        Pixmap pm = new Pixmap(UNIT_SIZE, UNIT_SIZE, Pixmap.Format.RGBA8888);
        fillRect(pm, 4 + dx, 6 + dy, 24, 22, race.r * bright, race.g * bright, race.b * bright, alpha);
        fillRect(pm, 4 + dx, 6 + dy, 24, 5, clazz.r * bright, clazz.g * bright, clazz.b * bright, alpha);
        pm.setColor(0f, 0f, 0f, alpha * 0.5f);
        pm.drawRectangle(4 + dx, 6 + dy, 24, 22);
        return pm;
    }
```
（铺开提交 b = `SHAPE_PILOT_UNIT_IDS` 置空集合 + 删除 paintLegacyBlock 与本段注释更新；行表像素观感以 `:lwjgl3:run` 冒烟为准微调，属表现层自由项。）
- **测试要点**：PlaceholderArt 不做 headless 测（沿现约定，验收走 lwjgl3:run）；`PlaceholderKeysTest` 回归 enumerateFor 对账不破；冒烟清单（写入提交信息）：① 兽人战士=三角+顶带+骨白 1★；② 敌方同子=红框+翻转；③ walk/attack/death 帧差异仍在；④ 其余单位仍为旧方块（提交 a 阶段）。

---

### CP11. 星级 tint 接线（UnitView / BattleRenderer / ShopBar）

- **类型**：修改方法 ×4
- **位置**：`render/board/UnitView.java:100-101`、`render/board/BattleRenderer.java:336-357`（drawUnitFrame）+ 调用点 176/198/204/401、`render/ui/ShopBar.java:158-163`
- **改动说明**：绘制时 `batch.setColor(StarColors.of(star))` 替代白色 tint（alpha 并入）；商店卡与敌阵虚影恒 1★（骨白）；拖拽 ghost 带被拖单位星级。`drawUnitFrame` 增 `int star` 参数（4 处调用点同步）。
- **代码**：

`UnitView.draw` 修改前（100-101 行）：
```java
        batch.setColor(1f, 1f, 1f, alpha);
        drawBody(batch, region, cx, cy, size);
```
修改后：
```java
        Color star = StarColors.of(unit.getStar());
        batch.setColor(star.r, star.g, star.b, alpha);
        drawBody(batch, region, cx, cy, size);
```
（受击白闪层维持白色叠加不变——白闪覆盖 tint 属期望表现。）

`BattleRenderer.drawUnitFrame` 修改前（337-350 行）：
```java
    private void drawUnitFrame(SpriteBatch batch, String unitId, String anim, int frame,
                               int cx, int cy, boolean enemyFace, float alpha,
                               com.badlogic.gdx.graphics.Color border) {
        TextureRegion region = assets.region(PlaceholderKeys.unitFrame(unitId, anim, frame));
        boolean wasFlip = region.isFlipX();
        if (enemyFace != wasFlip) {
            region.flip(true, false); // 用后即还（region 共享，铁律：不残留状态）
        }
        if (alpha < 1f) {
            batch.setColor(1f, 1f, 1f, alpha);
        }
        batch.draw(region, cx - BoardGeometry.CELL / 2f, cy - BoardGeometry.CELL / 2f,
                BoardGeometry.CELL, BoardGeometry.CELL);
        batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
```
修改后（签名 + tint；`drawShopping:176` 传 `unit.getStar()`、`drawShoppingChrome:198` 传 bench 单位星级、`:204` 敌阵虚影传 1、`:401` ghost 由 `findTemplateId` 同名单查 `Unit.getStar()`）：
```java
    private void drawUnitFrame(SpriteBatch batch, String unitId, String anim, int frame,
                               int cx, int cy, boolean enemyFace, float alpha, int star,
                               com.badlogic.gdx.graphics.Color border) {
        TextureRegion region = assets.region(PlaceholderKeys.unitFrame(unitId, anim, frame));
        boolean wasFlip = region.isFlipX();
        if (enemyFace != wasFlip) {
            region.flip(true, false); // 用后即还（region 共享，铁律：不残留状态）
        }
        com.badlogic.gdx.graphics.Color tint = StarColors.of(star);
        batch.setColor(tint.r, tint.g, tint.b, alpha);
        batch.draw(region, cx - BoardGeometry.CELL / 2f, cy - BoardGeometry.CELL / 2f,
                BoardGeometry.CELL, BoardGeometry.CELL);
        batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
```

`ShopBar` 修改前（158-163 行）：
```java
            Color old = batch.getColor();
            batch.setColor(can ? 0.3f : 0.18f, can ? 0.32f : 0.18f, can ? 0.38f : 0.2f, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            batch.draw(assets.region(PlaceholderKeys.unitFrame(template.getId(), PlaceholderKeys.ANIM_IDLE, 0)),
                    getX() + 4f, getY() + 18f, 32f, 32f);
```
修改后（商店预览恒 1★）：
```java
            Color old = batch.getColor();
            batch.setColor(can ? 0.3f : 0.18f, can ? 0.32f : 0.18f, can ? 0.38f : 0.2f, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            Color star = StarColors.of(1);
            batch.setColor(star.r, star.g, star.b, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.unitFrame(template.getId(), PlaceholderKeys.ANIM_IDLE, 0)),
                    getX() + 4f, getY() + 18f, 32f, 32f);
            batch.setColor(old);
```
- **测试要点**：渲染层 headless 直测沿现状不做像素断言；`ShopBarLogicTest`/`BattleEntryCellsTest` 回归不破；冒烟（并入 CP10 提交 b 验证）：同族四职业并排可辨、1/2/3 星三色并排可辨、敌我同子仅框色不同。

---

### CP12. CombatCounters 计数通道（遗物/旧疤/普攻计数——K2）

- **类型**：新建类 / 修改方法 / 修改字段
- **位置**：新建 `entities/CombatCounters.java`；`entities/BattleUnit.java:80-87`（getEffective）+ 新字段；`systems/StatPipeline.java:55-63`（deriveEffective 重载）；`config/GameBalance.java`（旧疤幅度常量）
- **改动说明**：v5 §4.1「遗物/旧疤计数通道」行——战斗内可变计数器并入第二级有效值（先加后乘），**不走 StatusType（同 type 取大）、不走 stat 修正源（开战快照）**。旧疤幅度全局常量（GameBalance），每层出现次数/封顶在机制 value/value2。
- **代码**：

新建 `entities/CombatCounters.java`（完整）：
```java
package com.voidvvv.kz_auto_chess_n.entities;

/** 战斗内计数器（v5 §4.1 计数通道；framework-internal 写方法仅 systems 包）。
 *  并入 StatPipeline 第二级：遗物=攻/血 ADD、旧疤=攻 PCT×层数 + 甲 ADD×层数。 */
public final class CombatCounters {
    private float relicAtkAdd;
    private float relicHpAdd;
    private int scarStacks;
    private int basicAttackCount;   // 三连装填计数（CP27 消费）

    public float getRelicAtkAdd() { return relicAtkAdd; }
    public float getRelicHpAdd() { return relicHpAdd; }
    public int getScarStacks() { return scarStacks; }
    public int getBasicAttackCount() { return basicAttackCount; }

    /** framework-internal：名册遗物入账（攻 ADD / 血 ADD） */
    public void addRelic(float atkAdd, float hpAdd) {
        relicAtkAdd += atkAdd;
        relicHpAdd += hpAdd;
    }

    /** framework-internal：旧疤 +1 层（封顶由调用方按机制 value2 判定） */
    public void addScarStack() {
        scarStacks++;
    }

    /** framework-internal：普攻出手 +1（三连装填） */
    public void addBasicAttack() {
        basicAttackCount++;
    }
}
```

`config/GameBalance.java` 新增：
```java
    // —— 旧疤（兽人(6) SCAR；每层幅度全局常量，次数/封顶在 synergies.json value/value2）——
    /** 每层攻击加成（百分点，×(1+0.05×层数)） */
    public static final float SCAR_ATK_PCT_PER_STACK = 5f;
    /** 每层护甲加成（ADD） */
    public static final float SCAR_ARMOR_ADD_PER_STACK = 3f;
```

`systems/StatPipeline.java` 修改前（55-63 行）：
```java
    /** 第二级：基准 + 状态修正块（先加后乘） */
    public static BattleStats deriveEffective(BattleStats base, StatModifierBlock statusModifiers) {
        Objects.requireNonNull(base, "base 不能为 null");
        Objects.requireNonNull(statusModifiers, "statusModifiers 不能为 null");
        float[] values = new float[StatKey.values().length];
        for (StatKey key : StatKey.values()) {
            values[key.ordinal()] = combine(base.get(key), statusModifiers.addOf(key), statusModifiers.pctOf(key));
        }
        return build(values);
    }
```
修改后（新增三参重载；旧两参签名委托 ZERO 计数器，存量测试不动）：
```java
    /** 第二级：基准 + 状态修正块 + 战斗内计数器（先加后乘，K2） */
    public static BattleStats deriveEffective(BattleStats base, StatModifierBlock statusModifiers) {
        return deriveEffective(base, statusModifiers, ZERO_COUNTERS);
    }

    public static BattleStats deriveEffective(BattleStats base, StatModifierBlock statusModifiers,
                                              CombatCounters counters) {
        Objects.requireNonNull(base, "base 不能为 null");
        Objects.requireNonNull(statusModifiers, "statusModifiers 不能为 null");
        Objects.requireNonNull(counters, "counters 不能为 null");
        StatModifierBlock merged = statusModifiers
                .plus(StatModifierBlock.of(StatKey.ATTACK, EffectOp.ADD, counters.getRelicAtkAdd()))
                .plus(StatModifierBlock.of(StatKey.HP, EffectOp.ADD, counters.getRelicHpAdd()))
                .plus(StatModifierBlock.of(StatKey.ATTACK, EffectOp.PCT,
                        counters.getScarStacks() * GameBalance.SCAR_ATK_PCT_PER_STACK))
                .plus(StatModifierBlock.of(StatKey.ARMOR, EffectOp.ADD,
                        counters.getScarStacks() * GameBalance.SCAR_ARMOR_ADD_PER_STACK));
        float[] values = new float[StatKey.values().length];
        for (StatKey key : StatKey.values()) {
            values[key.ordinal()] = combine(base.get(key), merged.addOf(key), merged.pctOf(key));
        }
        return build(values);
    }

    private static final CombatCounters ZERO_COUNTERS = new CombatCounters();
```
（import `entities.CombatCounters`。）

`entities/BattleUnit.java` 修改前（80-87 行）：
```java
    /** 有效属性（第二级，脏则重算；口径 #7 float 全精度） */
    public float getEffective(StatKey key) {
        if (statsDirty) {
            effectiveStats = StatPipeline.deriveEffective(baseStats, StatPipeline.statusModifiers(statuses));
            statsDirty = false;
        }
        return effectiveStats.get(key);
    }
```
修改后（新增字段 `private final CombatCounters combatCounters = new CombatCounters();` + 只读 getter `getCombatCounters()`；计数器变更后调用 `invalidateStats()` 打脏）：
```java
    /** 有效属性（第二级，脏则重算；口径 #7 float 全精度；计数通道并入 K2） */
    public float getEffective(StatKey key) {
        if (statsDirty) {
            effectiveStats = StatPipeline.deriveEffective(baseStats,
                    StatPipeline.statusModifiers(statuses), combatCounters);
            statsDirty = false;
        }
        return effectiveStats.get(key);
    }
```
- **测试要点**：`StatPipelineTest` 增：遗物 ADD 后有效攻/血变化且按先加后乘与状态 PCT 叠加序断言；旧疤 3 层=攻 +15% + 甲 +9；计数器为 0 时与旧两参结果逐位相等（回归锚）。

---

### CP13. 击杀归因（缺口①：lastDamagerId + UNIT_DIED killer）

- **类型**：新增字段 / 修改方法 ×2 / 修改事件类
- **位置**：`entities/BattleUnit.java`（新字段）、`systems/DamagePipeline.java:31-55`（记录点）、`entities/CombatEvent.java:79-81`（unitDied 工厂）
- **改动说明**：C5/C12 的前提。直伤与真伤（含 DOT、亡语、反弹）在 hpDamage>0 时记录来源 id；清扫期 UNIT_DIED 携带 killerId（-1=无有效归因）。CombatEvent 新字段进 equals/hashCode（确定性对拍口径维持；事件流不落盘，无存档兼容面）。
- **代码**：

`BattleUnit` 新增字段与访问器（framework-internal 写）：
```java
    private int lastDamagerId = -1;

    /** 最近有效伤害来源（普攻/技能直伤/DOT/亡语/反弹；-1 = 无） */
    public int getLastDamagerId() { return lastDamagerId; }

    /** framework-internal：伤害命中记录（DamagePipeline 调） */
    public void setLastDamagerId(int damagerId) { this.lastDamagerId = damagerId; }
```

`DamagePipeline.applyDirectHit` 修改前（38-39 行）：
```java
        float hpDamage = absorbShield(target, mitigated);
        target.modifyHp(-hpDamage);
```
修改后：
```java
        float hpDamage = absorbShield(target, mitigated);
        target.modifyHp(-hpDamage);
        if (hpDamage > 0f && attacker != null) {
            target.setLastDamagerId(attacker.getId());
        }
```
`applyTrueDamage` 修改前（52-55 行）：
```java
    public void applyTrueDamage(BattleState state, BattleUnit source, BattleUnit target, float amount) {
        float hpDamage = absorbShield(target, amount);
        target.modifyHp(-hpDamage);
    }
```
修改后（同时补 v5 §4.1「亡语结算事件与飘字口径」的载荷事件位：causeId 非 null 时记 HIT 事件走紫飘字；DOT 调用旧三参重载维持无事件口径 #10）：
```java
    public void applyTrueDamage(BattleState state, BattleUnit source, BattleUnit target, float amount) {
        applyTrueDamage(state, source, target, amount, null);
    }

    /** 全参：causeId = 亡语等真伤载荷的事件标注（null = DOT 静默，口径 #10） */
    public void applyTrueDamage(BattleState state, BattleUnit source, BattleUnit target,
                                float amount, String causeId) {
        float hpDamage = absorbShield(target, amount);
        target.modifyHp(-hpDamage);
        if (hpDamage > 0f && source != null) {
            target.setLastDamagerId(source.getId());
        }
        if (hpDamage > 0f && causeId != null) {
            state.record(CombatEvent.hit(state.getTick(),
                    source == null ? -1 : source.getId(), target.getId(), hpDamage, false, causeId));
        }
    }
```

`CombatEvent.unitDied` 修改前（78-81 行）：
```java
    /** 单位死亡：sourceId = 亡者 id */
    public static CombatEvent unitDied(int tick, int unitId) {
        return new CombatEvent(tick, Type.UNIT_DIED, unitId, -1, 0f, 0f, false, null, null, null);
    }
```
修改后（新增 killer 字段与全参工厂；旧工厂委托 killer=-1；equals/hashCode 追加 `killerId` 位）：
```java
    private final int killerId; // UNIT_DIED 专用：击杀者 unit id，-1 = 无有效归因（DOT 来源先亡等）

    /** 单位死亡：sourceId = 亡者 id（killer = -1，存量兼容） */
    public static CombatEvent unitDied(int tick, int unitId) {
        return unitDied(tick, unitId, -1);
    }

    /** 单位死亡（带击杀归因，缺口①）：sourceId = 亡者 id，killerId = 击杀者（-1 = 无） */
    public static CombatEvent unitDied(int tick, int unitId, int killerId) {
        return new CombatEvent(tick, Type.UNIT_DIED, unitId, -1, killerId, 0f, 0f, false, null, null, null, null);
    }
```
（构造器与 equals/hashCode/toString 相应扩位——执行器按现有 10 字段模式扩为 11。）
- **测试要点**：`DamagePipelineTest` 增：直伤/真伤记录 lastDamagerId、盾全额吸收（hpDamage=0）不记录、DOT 记施加者；`CombatEventTest` 增 killer 字段 equals 对拍。

---

### CP14. 两遍式级联清扫：DeathSystem（C2/C9，§五.2）

- **类型**：新建类 / 修改方法（BattleSystem ④ 替换）
- **位置**：新建 `systems/DeathSystem.java`；`systems/BattleSystem.java:160-172`、构造器（52-56）
- **改动说明**：④ 单遍扫描 → DeathSystem 两遍式级联：pass1「id 序：攻击快照（statuses 未清）→ undying 分流（CP17）→ markCleaned+移格+UNIT_DIED(killer)」；pass2「id 序结算亡语/名册/点算/兽潮/清账（CP15/16/19 接入点）」；亡语致死并入下一轮，收敛后统一 retarget；循环上界=单位数（每轮至少清 1 人，确定性收敛）。
- **代码**：

`BattleSystem` 修改前（160-172 行）：
```java
        // ④ 死亡清扫（H 语义：本 tick 内 HP≤0 者统一于此清除）
        List<Integer> deadIds = new ArrayList<Integer>();
        for (BattleUnit unit : state.getUnits()) {
            if (!unit.isCleaned() && unit.getCurrentHp() <= 0f) {
                unit.markCleaned();
                state.removeFromGrid(unit);
                state.record(CombatEvent.unitDied(state.getTick(), unit.getId()));
                deadIds.add(unit.getId());
            }
        }
        for (int deadId : deadIds) {
            targeting.retargetOnDeath(state, deadId); // 清扫后立即重选（battle §三）
        }
```
修改后：
```java
        // ④ 死亡清扫（两遍式级联，C2/C9——battle §二 H 语义不变，收敛后才进 ⑤）
        deathSystem.sweep(state);
```
（字段 `private final DeathSystem deathSystem;` 于构造器组装：`this.deathSystem = new DeathSystem(targeting, damagePipeline, statusSystem, skillExecutor);`——依赖注入与 ProjectileSystem 同风格。）

新建 `systems/DeathSystem.java`（完整骨架；CP15/16/19 的结算项以 TODO 锚点标注插入位）：
```java
package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;

import java.util.ArrayList;
import java.util.List;

/** 死亡清算（C2 两遍式级联）：pass1 标记+快照（先于 markCleaned 清状态）→ pass2 按 id 序结算；
 *  结算造成的 HP≤0 并入下一轮，级联收敛后统一 retarget。undying 首倒在此分流为倒地（C1/C7）。 */
public final class DeathSystem {
    private final TargetingSystem targeting;
    private final DamagePipeline damagePipeline;
    private final StatusSystem statusSystem;
    private final DeathrattleExecutor deathrattleExecutor;

    public DeathSystem(TargetingSystem targeting, DamagePipeline damagePipeline,
                       StatusSystem statusSystem, SkillExecutor skillExecutor) {
        this.targeting = targeting;
        this.damagePipeline = damagePipeline;
        this.statusSystem = statusSystem;
        this.deathrattleExecutor = new DeathrattleExecutor(damagePipeline, statusSystem);
    }

    /** 单条死亡记录（pass1 产出，pass2 消费——快照语义：statuses 未清时的攻击与坐标） */
    static final class DeathRecord {
        final int unitId;
        final int corpseX;
        final int corpseY;
        final float attackSnapshot;
        final int killerId;

        DeathRecord(int unitId, int corpseX, int corpseY, float attackSnapshot, int killerId) {
            this.unitId = unitId; this.corpseX = corpseX; this.corpseY = corpseY;
            this.attackSnapshot = attackSnapshot; this.killerId = killerId;
        }
    }

    public void sweep(BattleState state) {
        List<Integer> allDeadIds = new ArrayList<Integer>();
        int guard = state.getUnits().size(); // 每轮至少清 1 人，循环上界 = 单位数
        while (guard-- > 0) {
            List<DeathRecord> batch = markBatch(state);      // —— 第一遍：标记 + 快照 ——
            if (batch.isEmpty()) {
                break;
            }
            for (DeathRecord record : batch) {               // —— 第二遍：id 序结算 ——
                settle(state, record);
                allDeadIds.add(record.unitId);
            }
        }
        for (int deadId : allDeadIds) {
            targeting.retargetOnDeath(state, deadId);        // 收敛后统一重选（battle §三）
        }
    }

    /** 第一遍：本 tick 新达 HP≤0 且未清扫未倒地者——快照 → undying 分流 → 清扫标记 + UNIT_DIED(killer) */
    private List<DeathRecord> markBatch(BattleState state) {
        List<DeathRecord> batch = new ArrayList<DeathRecord>();
        for (BattleUnit unit : state.getUnits()) {           // id 升序 = 构造序
            if (unit.isCleaned() || unit.getCurrentHp() > 0f) {
                continue;
            }
            if (unit.isDowned()) {                           // 倒地者不再进清扫（CP17）
                continue;
            }
            if (UndyingRule.tryEnterDowned(state, unit)) {   // undying 首倒分流（C1，CP17）
                continue;
            }
            DeathRecord record = new DeathRecord(unit.getId(), unit.getGridX(), unit.getGridY(),
                    unit.getEffective(StatKey.ATTACK), unit.getLastDamagerId()); // 快照先于清状态
            unit.markCleaned();
            state.removeFromGrid(unit);
            state.record(CombatEvent.unitDied(state.getTick(), unit.getId(), record.killerId));
            batch.add(record);
        }
        return batch;
    }

    /** 第二遍：单死者结算序（亡语 → 名册 → 点算 → 敌方死亡触发 → 击杀回能；CP15/16/19 各自插入） */
    private void settle(BattleState state, DeathRecord record) {
        BattleUnit dead = state.getUnitById(record.unitId);
        deathrattleExecutor.execute(state, dead, record);          // CP15：个人亡语（含双响 C5）
        DeathBenefitRule.settleDeathTax(state, dead, record);      // CP16：阵亡名册（C8/C9）
        DeathBenefitRule.settleDeathAudit(state, dead, record);    // CP16：大公点算（C11）
        DeathBenefitRule.settleEnemyDeathSurge(state, dead);       // CP19：兽潮（C13）
        DeathBenefitRule.settleKillEnergyRefund(state, record);    // CP19：连环清账（C12）
    }
}
```
（CP15/16/19 落地前，三个 Rule 类先以空实现 + 单测锚占位——每批独立可构建。`UndyingRule` 见 CP17；其落地前 `tryEnterDowned` 恒 false，行为=现状单遍语义的超集（快照/killer 已生效）。）
- **测试要点**：新建 `systems/DeathSystemTest`：① 同 tick 双死（互灭）→ 两条 UNIT_DIED 均在 ⑤ 判定前、玩家从严判负；② 亡语链式致死（A 死炸死 B、B 亡语再炸死 C）→ 一轮 sweep 内三级级联、事件序=A-DIED,B-DIED,A-载荷,B-载荷…（两遍式：同批 DIED 连续、载荷按 id 序）；③ 快照含 ATK_UP 状态（死后状态清空不影响载荷）；④ killer 归因透传（CP13 联测）；⑤ `BattleSystemTest` 存量清扫用例回归。

---

### CP15. 亡语执行器 DeathrattleExecutor（4 kind + 双响 C5）

- **类型**：新建类
- **位置**：新建 `systems/DeathrattleExecutor.java`（DeathSystem 内部组装）
- **改动说明**：白骨滩/魂爆（AREA_TRUE_DAMAGE：尸体格曼哈顿半径、真伤快照×ratio、可附 SLOW）、坏账（KILLER_TRUE_DAMAGE：凶手无效→尸体格曼哈顿最近可行动存活敌，双响两发同目标）、空弦绝响（DEATH_VOLLEY：第 1 发锁当前 HIGHEST_ATK、双响第 2 发按 LOWEST_HP，deathrattleBorn 弹 CP18 接路由）、灯留下了（ALLY_REGEN：可行动存活友军 REGEN）。双响=同 tick 顺序执行两次（DOUBLE_DEATHRATTLE 机制在**死者自身**，C5）；目标候选一律「可行动存活」（CP17 谓词，其落地前=未清扫）。
- **代码**（完整）：
```java
package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.data.DeathrattleKind;
import com.voidvvv.kz_auto_chess_n.data.DeathrattleSpec;
import com.voidvvv.kz_auto_chess_n.data.SynergyMechanic;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.Projectile;

import java.util.ArrayList;
import java.util.List;

/** 个人亡语执行（载荷全真伤；双份抚恤=同 tick 两发，C5）。目标候选=可行动存活敌方（含倒地豁免）。 */
final class DeathrattleExecutor {
    private final DamagePipeline damagePipeline;
    private final StatusSystem statusSystem;

    DeathrattleExecutor(DamagePipeline damagePipeline, StatusSystem statusSystem) {
        this.damagePipeline = damagePipeline;
        this.statusSystem = statusSystem;
    }

    void execute(BattleState state, BattleUnit dead, DeathSystem.DeathRecord record) {
        DeathrattleSpec spec = dead.getTemplate().getDeathrattle();
        if (spec == null) {
            return;
        }
        int times = dead.getMechanic(SynergyMechanic.DOUBLE_DEATHRATTLE) != null ? 2 : 1;
        for (int i = 0; i < times; i++) {
            executeOnce(state, dead, spec, record, i);
        }
    }

    private void executeOnce(BattleState state, BattleUnit dead, DeathrattleSpec spec,
                             DeathSystem.DeathRecord record, int shotIndex) {
        switch (spec.getKind()) {
            case AREA_TRUE_DAMAGE: {
                List<BattleUnit> hits = actableUnitsWithin(state, dead, record.corpseX, record.corpseY,
                        spec.getRadius());
                for (BattleUnit target : hits) {
                    damagePipeline.applyTrueDamage(state, dead, target,
                            record.attackSnapshot * spec.getRatio(),
                            "deathrattle_" + dead.getTemplate().getId());
                    if (spec.getStatusType() != null) { // SLOW 附带：同 type 取大=双响只刷新（已知特性）
                        statusSystem.apply(state, target, spec.getStatusType(),
                                spec.getStatusPower(), spec.getStatusDuration(), dead.getId());
                    }
                }
                break;
            }
            case KILLER_TRUE_DAMAGE: {
                BattleUnit victim = validKiller(state, dead, record);
                if (victim != null) { // 凶手两发均对其；无有效凶手→转移目标的每一发也各自转移（C5）
                    damagePipeline.applyTrueDamage(state, dead, victim,
                            record.attackSnapshot * spec.getRatio(),
                            "deathrattle_" + dead.getTemplate().getId());
                }
                break;
            }
            case DEATH_VOLLEY: {
                BattleUnit first = DeathrattleExecutor.pickByPriority(state, dead,
                        TargetPriority.HIGHEST_ATK, -1);
                spawnVolleyShot(state, dead, record, first, spec);
                if (shotIndex == 1) { // 双响第二发按 LOWEST_HP（发射时重选，C5）
                    BattleUnit second = DeathrattleExecutor.pickByPriority(state, dead,
                            TargetPriority.LOWEST_HP, -1);
                    spawnVolleyShot(state, dead, record, second, spec);
                }
                break;
            }
            case ALLY_REGEN: {
                for (BattleUnit ally : state.getUnits()) {
                    if (ally.getSide() == dead.getSide() && ally.isActable()) {
                        statusSystem.apply(state, ally, com.voidvvv.kz_auto_chess_n.data.StatusType.REGEN,
                                spec.getRegenRatio(), spec.getDuration(), dead.getId());
                    }
                }
                break;
            }
            default:
                throw new IllegalArgumentException("未知亡语 kind: " + spec.getKind());
        }
    }

    private void spawnVolleyShot(BattleState state, BattleUnit dead, DeathSystem.DeathRecord record,
                                 BattleUnit target, DeathrattleSpec spec) {
        if (target == null) {
            return;
        }
        state.spawnProjectile(new Projectile(dead.getId(), target.getId(),
                record.corpseX + 0.5f, record.corpseY + 0.5f,
                record.attackSnapshot * spec.getRatio(), false, null, 1f, true)); // deathrattleBorn
    }

    /** 坏账目标：凶手可行动存活且敌对 → 凶手；否则尸体格曼哈顿最近可行动存活敌（平局 id 小） */
    private static BattleUnit validKiller(BattleState state, BattleUnit dead,
                                          DeathSystem.DeathRecord record) {
        if (record.killerId >= 0) {
            BattleUnit killer = state.getUnitById(record.killerId);
            if (killer != null && killer.isActable() && killer.getSide() != dead.getSide()) {
                return killer;
            }
        }
        return nearestActableEnemy(state, dead, record.corpseX, record.corpseY);
    }

    // —— 目标选取助手（曼哈顿 + id 升序平局；谓词=isActable，CP17 落地前=未清扫） ——
    static List<BattleUnit> actableUnitsWithin(BattleState state, BattleUnit dead,
                                               int cx, int cy, int radius) { /* 实现按 enemiesWithin 同型 */ return new ArrayList<BattleUnit>(); }
    static BattleUnit nearestActableEnemy(BattleState state, BattleUnit dead, int cx, int cy) { /* 同型 */ return null; }
    static BattleUnit pickByPriority(BattleState state, BattleUnit dead, TargetPriority priority, int unused) { /* HIGHEST_ATK=最大有效攻；LOWEST_HP=最小 hpRatio；平局 id 小 */ return null; }
}
```
（三个助手方法实现与 `SkillExecutor.enemiesWithin/aliveEnemies`、`TargetingSystem.sortKey` 同型，执行器照抄改谓词即可——正文留签名 + 语义注释。）
- **测试要点**：新建 `systems/DeathrattleExecutorTest`（用 BattleTestFixtures）：① 白骨滩半径 1 命中 2 敌真伤=快照×0.8、SLOW 双响只刷新（power 取大时长取大）；② 魂爆半径 2；③ 坏账：凶手在→两发同对凶手；凶手先亡→转最近敌；DOT 致死（killer=-1）→转最近敌；④ 绝响：双响两弹 HIGHEST_ATK/LOWEST_HP 各一、deathrattleBorn=true；⑤ 灯：友军全员 REGEN 0.02/5s、倒地友军不吃（联 CP17）。

---

### CP16. 阵亡名册 + 大公点算（DEATH_TAX / DEATH_AUDIT）

- **类型**：新建类（DeathBenefitRule，DeathSystem 第二遍的结算位）
- **位置**：新建 `systems/DeathBenefitRule.java`（含 CP19 两项；一个文件四静态方法，<200 行）
- **改动说明**：C8/C9/C11/C12。名册：死者结算后，可行动存活且 race/class 命中 trait 的友方中距尸体格最近（平局 id 小）者入账 `counters.addRelic(value, value2)` + `invalidateStats()` + `modifyHp(+value2)`（封顶新上限）；死者不计、无可继承作废（倒地窗口边界即天然成立）。点算：敌方可行动存活中 hpRatio 最低且 <value%（平局 id 小）→ `Banish.execute`；无合格不触发不缓存。
- **代码**（DeathBenefitRule 关键两方法，完整；CP19 两方法见该 CP）：
```java
package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.data.EffectData;
import com.voidvvv.kz_auto_chess_n.data.SynergyMechanic;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;

/** 死亡收益结算（第二遍逐死者调用）：名册（C8/C9）、点算（C11）、兽潮（C13）、清账（C12）。 */
final class DeathBenefitRule {
    private DeathBenefitRule() {
    }

    /** 阵亡名册：最近可行动存活 trait 友方获 遗物（攻 ADD + 上限 ADD 并立即回复） */
    static void settleDeathTax(BattleState state, BattleUnit dead, DeathSystem.DeathRecord record) {
        EffectData tax = mechanicOnSide(state, dead.getSide(), SynergyMechanic.DEATH_TAX);
        if (tax == null) {
            return;
        }
        BattleUnit heir = nearestTraitActableAlly(state, dead, tax.getTrait(), record.corpseX, record.corpseY);
        if (heir == null) {
            return; // 无可行动存活继承者 → 作废（C8，含死骑倒地窗口边界）
        }
        heir.getCombatCounters().addRelic(tax.getValue(), tax.getValue2());
        heir.invalidateStats();
        heir.modifyHp(tax.getValue2()); // 立即回复（钳制到新上限）
    }

    /** 大公点算：放逐敌方生命比例最低且 <阈值 者（C10/C11；D3：Boss 亦可被移除） */
    static void settleDeathAudit(BattleState state, BattleUnit dead, DeathSystem.DeathRecord record) {
        EffectData audit = mechanicOnSide(state, dead.getSide(), SynergyMechanic.DEATH_AUDIT);
        if (audit == null) {
            return;
        }
        BattleUnit best = null;
        for (BattleUnit enemy : state.getUnits()) {
            if (enemy.getSide() == dead.getSide() || !enemy.isActable()) {
                continue;
            }
            if (enemy.hpRatio() >= audit.getValue() / 100f) {
                continue; // 无合格目标不触发、不缓存（C11）
            }
            if (best == null || enemy.hpRatio() < best.hpRatio()
                    || (enemy.hpRatio() == best.hpRatio() && enemy.getId() < best.getId())) {
                best = enemy;
            }
        }
        Banish.execute(state, best); // 独立移除路径（C10）：非死亡事件
    }

    // mechanicOnSide / nearestTraitActableAlly：按 side 任一存活单位的机制快照取词（同侧同档一致）；
    // trait 匹配 = race 或 class 等值。实现同型于 DeathrattleExecutor 助手（执行器照抄）。
}
```
- **测试要点**：`systems/DeathBenefitRuleTest`：① 名册 2/4/6 档 +4/+5/+6 与回复值随档替换制；② 等距继承取 id 小；③ 死骑倒地窗口内亡灵真死且无其他可行动亡灵→作废（联 CP17）；④ 点算 <25% 放逐、=25% 不触发、平局 id 小、Boss 可被移除（D3）；⑤ 放逐目标无 UNIT_DIED、有 UNIT_BANISHED。

---

### CP17. undying 倒地全链（C7：五项 + 四过滤点 + 时钟冻结 + §五.3）

- **类型**：新增字段与方法 / 修改方法 ×7
- **位置**：`entities/BattleUnit.java`（downed 字段组/hasControl/isActable）、新建 `systems/UndyingRule.java`、`systems/StatusSystem.java:73-77`、`systems/BattleSystem.java`（currentTarget 250-259 + step 复活检查）、`systems/TargetingSystem.java:27-30`、`systems/SkillExecutor.java`（validLockedTarget 151-160 / lowestAlly 162-178 / aliveEnemies 180-188 / enemiesWithin 190-204 四过滤点）
- **改动说明**：K6 全量。倒地=字段非状态（markCleaned 清状态不波及）；aliveCount 不变（未清扫）→ ⑤ 口径与 S 列自动成立。
- **代码**：

`BattleUnit` 新增（节选）：
```java
    private boolean downed;
    private float downedTimerRemaining;
    private boolean undyingUsed;

    /** 倒地中（未清扫但不可行动/不可被索敌伤害治疗——C7 ③） */
    public boolean isDowned() { return downed; }
    public float getDownedTimerRemaining() { return downedTimerRemaining; }
    public boolean isUndyingUsed() { return undyingUsed; }

    /** 可行动存活 = 未清扫且非倒地（§〇.4：名册继承/点算候选/索敌过滤的统一资格谓词） */
    public boolean isActable() { return !cleaned && !downed; }

    /** framework-internal：进入倒地（UndyingRule 调；占格维持、能量保留冻结） */
    public void enterDowned(float seconds) {
        downed = true;
        downedTimerRemaining = seconds;
        undyingUsed = true;
        targetId = -1;
    }

    /** framework-internal：倒地计时递减（advanceTimers 内，恒累计口径不破） */
    public void advanceDownedTimer(float dt) {
        if (downed) { downedTimerRemaining = Math.max(0f, downedTimerRemaining - dt); }
    }

    /** framework-internal：原地站起（40% 血 + 能量回满；格被占→曼哈顿最近空格，x 后 y 序——防御分支） */
    public void revive(float hpRatio) {
        downed = false;
        setEnergy(GameBalance.ENERGY_MAX);
        currentHp = maxHp() * hpRatio;
        invalidateStats();
    }
```

`hasControl` 修改前（99-107 行）：
```java
    /** 是否被控制（STUN 在身——列表内状态均为未到期） */
    public boolean hasControl() {
        for (ActiveStatus status : statuses) {
            if (status.getType() == StatusType.STUN) {
                return true;
            }
        }
        return false;
    }
```
修改后：
```java
    /** 是否被控制（C7 ②：倒地 ∨ STUN 在身——列表内状态均为未到期） */
    public boolean hasControl() {
        if (downed) {
            return true;
        }
        for (ActiveStatus status : statuses) {
            if (status.getType() == StatusType.STUN) {
                return true;
            }
        }
        return false;
    }
```

新建 `systems/UndyingRule.java`（完整）：
```java
package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.data.UndyingSpec;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;

/** 不死判定（C1/C7）：undying 单位首次 HP≤0 → 倒地（非真死：不触发亡语/名册/点算/不死再触发）。 */
final class UndyingRule {
    private UndyingRule() {
    }

    /** 死亡清扫 pass1 调用：命中则单位转入倒地并返回 true（不产死亡记录） */
    static boolean tryEnterDowned(BattleState state, BattleUnit unit) {
        UndyingSpec spec = unit.getTemplate().getUndying();
        if (spec == null || unit.isUndyingUsed() || unit.isDowned()) {
            return false;
        }
        unit.enterDowned(spec.getReviveDelaySeconds());
        state.record(CombatEvent.unitDowned(state.getTick(), unit.getId()));
        return true;
    }

    /** step ③ 计时循环调用：倒地期满 → 原地站起（占格从未释放，「格被占」为防御分支） */
    static void tryRevive(BattleState state, BattleUnit unit) {
        if (!unit.isDowned() || unit.getDownedTimerRemaining() > 0f) {
            return;
        }
        if (state.unitAt(unit.getGridX(), unit.getGridY()) != unit) { // 防御：格被占 → 最近空格
            relocateToNearestFreeCell(state, unit);
        }
        UndyingSpec spec = unit.getTemplate().getUndying();
        unit.revive(spec == null ? 0.4f : spec.getReviveHpRatio());
        state.record(CombatEvent.unitRevived(state.getTick(), unit.getId()));
    }

    /** 曼哈顿最近空格（平局 x 后 y 序）；全满抛错（防御，理论不可达——人口 < 格数） */
    private static void relocateToNearestFreeCell(BattleState state, BattleUnit unit) {
        // 实现同 MovementSystem 贪婪遍历风格：全盘扫描取最小曼哈顿距离空格，平局 x 升序后 y 升序
        throw new IllegalStateException("防御分支：无空格可复活落位 " + unit.getId());
    }
}
```
（`CombatEvent` 增 `UNIT_DOWNED`/`UNIT_REVIVED` 两词与工厂，同 CP8 模式。）

`BattleSystem` 修改前（250-259 行，currentTarget）：
```java
    private static BattleUnit currentTarget(BattleState state, BattleUnit unit) {
        if (unit.getTargetId() < 0) {
            return null;
        }
        BattleUnit target = state.getUnitById(unit.getTargetId());
        if (target == null || target.isCleaned() || target.getSide() == unit.getSide()) {
            return null;
        }
        return target;
    }
```
修改后（过滤点一）：
```java
    private static BattleUnit currentTarget(BattleState state, BattleUnit unit) {
        if (unit.getTargetId() < 0) {
            return null;
        }
        BattleUnit target = state.getUnitById(unit.getTargetId());
        if (target == null || !target.isActable() || target.getSide() == unit.getSide()) {
            return null; // isActable = 未清扫且非倒地（C7 ③过滤点）
        }
        return target;
    }
```
（`SkillExecutor.validLockedTarget` 156 行 `target.isCleaned()` → `!target.isActable()`；`lowestAlly` 166 行、`aliveEnemies` 183 行 `isAlive()` → `isActable()`；`enemiesWithin` 195 行 `isCleaned()` → `!isActable()`——四过滤点其余三处同型替换。）

`StatusSystem.tickStatuses` 修改前（73-77 行）：
```java
    public void tickStatuses(BattleState state, float dt) {
        for (BattleUnit unit : state.getUnits()) {
            if (unit.isCleaned()) {
                continue;
            }
```
修改后（过滤点二 + 时钟冻结：整体跳过=DOT 计时与时长递减暂停）：
```java
    public void tickStatuses(BattleState state, float dt) {
        for (BattleUnit unit : state.getUnits()) {
            if (!unit.isActable()) {
                continue; // 清扫/倒地（C7 ③：倒地中状态时钟冻结——防倒地中被毒死）
            }
```
`TargetingSystem.findTarget` 修改前（27-30 行）：
```java
        for (BattleUnit candidate : state.getUnits()) {
            if (candidate.getSide() == self.getSide() || candidate.isCleaned()) {
                continue;
            }
```
修改后（过滤点三）：
```java
        for (BattleUnit candidate : state.getUnits()) {
            if (candidate.getSide() == self.getSide() || !candidate.isActable()) {
                continue;
            }
```
（过滤点四 = SkillExecutor 系，见上。另：`DamagePipeline.applyDirectHit/applyTrueDamage/applyHeal` 入口补 `!target.isActable()` 空返回防御——「不可被伤害/治疗」的单点兜底。）

`BattleSystem.step` ③ 计时循环修改前（149-153 行）：
```java
        for (BattleUnit unit : state.getUnits()) {
            if (!unit.isCleaned()) {
                unit.advanceTimers(GameBalance.LOGIC_STEP); // 恒累计（口径 #4）
            }
        }
```
修改后：
```java
        for (BattleUnit unit : state.getUnits()) {
            if (!unit.isCleaned()) {
                unit.advanceTimers(GameBalance.LOGIC_STEP); // 恒累计（口径 #4）
                unit.advanceDownedTimer(GameBalance.LOGIC_STEP); // C7 ⑤：倒地计时（非倒地空操作）
                UndyingRule.tryRevive(state, unit);
            }
        }
```
- **测试要点**：新建 `systems/UndyingRuleTest` + 扩 `BattleSystemTest`：① 死骑 HP≤0 → 无 UNIT_DIED、有 UNIT_DOWNED、aliveCount 不减、⑤ 不判负（S≥1 口径）；② 倒地中不可被索敌（敌人改打他人）/不可被 heal/DOT 冻结（挂毒后倒地，毒跳与时长均停）；③ 1.0s 后原地复活 40% 血+能量 100，下个行动 tick 施放；④ 二次 HP≤0=真死（亡语无、名册计）；⑤ 倒地不喂名册继承（联 CP16）。

---

### CP18. 亡语源弹道（deathrattleBorn 真伤路由 + 消散重选，§五.7/C6）

- **类型**：修改构造器 / 修改方法
- **位置**：`entities/Projectile.java:28-38`、`systems/ProjectileSystem.java:29-59`
- **改动说明**：Projectile 增 `deathrattleBorn` 标志（第 9 参，旧构造器委托 false）；消散分支按来源分流——亡语源弹按 LOWEST_HP 重选目标（无可选才消散），其余维持 Fizzled；impact 分流——亡语源弹走真伤+事件（紫飘字）。在途目标倒地（未清扫）视为无效（同清扫处理路径）。
- **代码**：

`Projectile` 修改前（28-38 行）：
```java
    public Projectile(int sourceId, int targetId, float posX, float posY,
                      float attackSnapshot, boolean crit, SkillData skill, float scaleFactor) {
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.posX = posX;
        this.posY = posY;
        this.attackSnapshot = attackSnapshot;
        this.crit = crit;
        this.skill = skill;
        this.scaleFactor = scaleFactor;
    }
```
修改后（新字段 + 全参构造；旧 8 参构造委托 `false`；getter `isDeathrattleBorn()`）：
```java
    public Projectile(int sourceId, int targetId, float posX, float posY,
                      float attackSnapshot, boolean crit, SkillData skill, float scaleFactor,
                      boolean deathrattleBorn) {
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.posX = posX;
        this.posY = posY;
        this.attackSnapshot = attackSnapshot;
        this.crit = crit;
        this.skill = skill;
        this.scaleFactor = scaleFactor;
        this.deathrattleBorn = deathrattleBorn;
    }
```

`ProjectileSystem.advanceAll` 修改前（31-47 行）：
```java
        List<Projectile> snapshot = new ArrayList<Projectile>(state.getProjectiles());
        for (Projectile projectile : snapshot) {
            BattleUnit target = state.getUnitById(projectile.getTargetId());
            if (target == null || target.isCleaned()) {
                state.record(CombatEvent.projectileFizzled(state.getTick(),
                        projectile.getSourceId(), projectile.getTargetId()));
                state.removeProjectile(projectile);
                continue;
            }
```
修改后（CP24 盾墙拦截将在此循环头部再插一步，见 §6.CP24）：
```java
        List<Projectile> snapshot = new ArrayList<Projectile>(state.getProjectiles());
        for (Projectile projectile : snapshot) {
            BattleUnit target = state.getUnitById(projectile.getTargetId());
            if (target == null || !target.isActable()) {
                if (projectile.isDeathrattleBorn()) { // §五.7：亡语源弹道按 LOWEST_HP 重选
                    BattleUnit reselected = DeathrattleRetarget.pickLowestHpActableEnemy(
                            state, projectile.getSourceId());
                    if (reselected != null) {
                        projectile.retarget(reselected.getId());
                        continue;
                    }
                }
                state.record(CombatEvent.projectileFizzled(state.getTick(),
                        projectile.getSourceId(), projectile.getTargetId()));
                state.removeProjectile(projectile);
                continue;
            }
```
（`Projectile` 补 framework-internal `retarget(int)`；`DeathrattleRetarget` 小助手=source 侧敌对可行动存活中最小 hpRatio 平局 id 小——与 DeathrattleExecutor 助手合并为一处静态工具。）

`impact` 修改前（52-59 行）：
```java
        if (projectile.getSkill() == null) {
            damagePipeline.applyDirectHit(state, caster, target,
                    projectile.getAttackSnapshot(), 1f, projectile.isCrit(), true, null);
        } else {
            skillExecutor.applyAtImpact(state, caster, target, projectile.getSkill(),
                    projectile.getAttackSnapshot(), projectile.getScaleFactor());
        }
```
修改后（亡语源弹=真伤载荷已含倍率；走 CP20 后的扩参签名——见 CP20，此处示意按最终形态）：
```java
        if (projectile.isDeathrattleBorn()) {
            damagePipeline.applyTrueDamage(state, caster, target,
                    projectile.getAttackSnapshot(), "deathrattle_projectile");
        } else if (projectile.getSkill() == null) {
            damagePipeline.applyDirectHit(state, caster, target,
                    projectile.getAttackSnapshot(), 1f, projectile.isCrit(), 0f, true, null, 0f);
        } else {
            skillExecutor.applyAtImpact(state, caster, target, projectile.getSkill(),
                    projectile.getAttackSnapshot(), projectile.getScaleFactor());
        }
```
（注：`applyDirectHit` 扩参（critBonusPp/armorPenRatio）在 CP20 一次到位；CP18 若先于 CP20 合入则暂以现 8 参调用、CP20 统一替换——批次顺序保证 CP20 在后。）
- **测试要点**：`ProjectileSystemTest` 增：① 普攻弹目标清扫→FIZZLED 维持；② 亡语源弹目标清扫→无 FIZZLED、新目标=最低血可行动敌、命中真伤=快照值（无视护甲）；③ 亡语源弹无可选目标→FIZZLED；④ 目标倒地同清扫路径（重选）。

---

### CP19. 兽潮 + 连环清账（C13/C12）+ 渲染新事件消费

- **类型**：新增方法（DeathBenefitRule 两方法）+ 修改渲染两处
- **位置**：`systems/DeathBenefitRule.java`（补两方法）、`render/FloatTextFormat.java:41-58`、`render/board/UnitView.draw`（倒地表现）
- **改动说明**：兽潮=敌方真死逐次触发（放逐/点算不触发——Banish 不进 DeathSystem 天然成立）；对侧含 ENEMY_DEATH_SURGE 单位挂 ASPD_UP（同 type 取大=刷新制）。清账=凶手含 KILL_ENERGY_REFUND → setEnergy(100)（不就地施放，K11）。渲染：UNIT_BANISHED 灰字「除名」飘字；倒地=半透明+免血条刷新观感（表现层自由项）。
- **代码**（DeathBenefitRule 补两方法）：
```java
    /** 兽潮（野兽6）：敌方真死 → 对侧含机制单位 ASPD_UP（value% / duration s，同 type 取大=刷新制，C13） */
    static void settleEnemyDeathSurge(BattleState state, BattleUnit dead) {
        for (BattleUnit unit : state.getUnits()) {
            if (unit.getSide() == dead.getSide() || !unit.isActable()) {
                continue;
            }
            EffectData surge = unit.getMechanic(SynergyMechanic.ENEMY_DEATH_SURGE);
            if (surge == null) {
                continue;
            }
            statusSystemApply(state, unit, surge); // ASPD_UP value/duration（StatusSystem.apply 经构造器注入）
        }
    }

    /** 连环清账（刺客6）：真死归因凶手含机制 → 能量回满（K11：下一行动 tick 施放；放逐不触发，C12） */
    static void settleKillEnergyRefund(BattleState state, DeathSystem.DeathRecord record) {
        if (record.killerId < 0) {
            return;
        }
        BattleUnit killer = state.getUnitById(record.killerId);
        if (killer == null || !killer.isActable()
                || killer.getMechanic(SynergyMechanic.KILL_ENERGY_REFUND) == null) {
            return;
        }
        killer.setEnergy(GameBalance.ENERGY_MAX);
    }
```
（`deathrattle 致死归因`由 CP13 的 applyTrueDamage 记录天然覆盖——「真杀死因含亡语致死」自动成立。StatusSystem 引用经 DeathSystem 透传注入。）

渲染侧（示意，表现层自由）：`FloatTextFormat.of` 增 `case UNIT_BANISHED: return new Spec("除名", new Color(0.7f,0.7f,0.75f,1f), 1f);`；`UnitView.draw` 增 `if (unit.isDowned()) { alpha *= 0.55f; }`。
- **测试要点**：① 兽潮：敌死一次→全体野兽 ASPD_UP 25/4s、两敌同 tick 死只刷新不叠双倍时长上限、放逐不触发；② 清账：刺客普攻击杀→能量 100；亡语致死归因凶手=施放亡灵单位也回满（亡灵内战互动）；放逐移除不触发；DOT 致死归施加者。

---

### CP20. DamagePipeline 受击窗口（免伤/收割/穿甲/反弹/事件序，K9）

- **类型**：修改方法（applyDirectHit 重写 + 扩参）
- **位置**：`systems/DamagePipeline.java:31-49`；调用点 `BattleSystem.java:226-227`（近战）、`ProjectileSystem.impact`（CP18 已示最终形态）、`SkillExecutor.applyOneEffect` DAMAGE 分支
- **改动说明**：签名一次到位（批3 落地、批4 复用）：`applyDirectHit(state, attacker, target, attackPower, multiplier, crit, critBonusPp, basicAttack, skillId, armorPenRatio)`。窗口序=K9：收割增伤（前）→ 免伤（前，直伤专属）→ 护甲（×(1−pen)）→ 盾 → 血 → HIT → 回能 → 吸血 → 旧疤计数（CP21）→ 余震反弹 → 普攻附带（CP25）。DOT/真伤不经过本方法（豁免天然成立）。EXECUTE 读取即本 CP 完成接线（CP26 仅剩专项测试）。
- **代码**（批0 CP1+CP13 合并后的当前形态为「修改前」）：

修改前（CP1/CP13 后形态）：
```java
    /** 直伤命中（普攻与技能同路；skillId null = 普攻） */
    public void applyDirectHit(BattleState state, BattleUnit attacker, BattleUnit target,
                               float attackPower, float multiplier, boolean crit,
                               boolean basicAttack, String skillId) {
        Objects.requireNonNull(target, "target 不能为 null");
        float critMultiplier = attacker == null
                ? StatKey.CRIT_DAMAGE.baseStatsDefault() / 100f
                : attacker.getEffective(StatKey.CRIT_DAMAGE) / 100f;
        float mitigated = attackPower * multiplier
                * (crit ? critMultiplier : 1f)
                * 100f / (100f + target.getEffective(StatKey.ARMOR));
        float hpDamage = absorbShield(target, mitigated);
        target.modifyHp(-hpDamage);
        if (hpDamage > 0f && attacker != null) {
            target.setLastDamagerId(attacker.getId());
        }
        state.record(CombatEvent.hit(state.getTick(),
                attacker == null ? -1 : attacker.getId(), target.getId(), hpDamage, crit, skillId));

        gainEnergy(state, attacker, GameBalance.ENERGY_PER_HIT);
        gainEnergy(state, target, GameBalance.ENERGY_PER_HIT_TAKEN);

        if (basicAttack && attacker != null) {
            applyLifesteal(state, attacker, hpDamage);
        }
    }
```
修改后（含 CP21 旧疤调用位与 CP25 附带调用位——两个后置 CP 各以一私有方法占位空实现先行，避免方法体重写三次）：
```java
    /** 直伤命中（普攻与技能同路；skillId null = 普攻）。窗口序 K9：
     *  收割(前) → 免伤(前,直伤专属) → 护甲(×(1−pen)) → 盾 → 血 → HIT → 回能 → 吸血
     *  → 旧疤计数(仅普攻) → 余震反弹 → 普攻附带。DOT/真伤不经此（豁免天然成立）。 */
    public void applyDirectHit(BattleState state, BattleUnit attacker, BattleUnit target,
                               float attackPower, float multiplier, boolean crit, float critBonusPp,
                               boolean basicAttack, String skillId, float armorPenRatio) {
        Objects.requireNonNull(target, "target 不能为 null");
        float critMultiplier = attacker == null
                ? StatKey.CRIT_DAMAGE.baseStatsDefault() / 100f
                : attacker.getEffective(StatKey.CRIT_DAMAGE) / 100f + critBonusPp / 100f;
        float executeAmp = executeAmpOf(attacker, target);           // 收割：目标 <阈值 → +value%（CP26 语义）
        float reduction = mechanicValueOf(target, SynergyMechanic.DAMAGE_REDUCTION); // 免伤 15（仅直伤）
        float mitigated = attackPower * multiplier * (1f + executeAmp / 100f)
                * (crit ? critMultiplier : 1f)
                * (1f - reduction / 100f)
                * 100f / (100f + target.getEffective(StatKey.ARMOR) * (1f - armorPenRatio));
        float hpDamage = absorbShield(target, mitigated);
        target.modifyHp(-hpDamage);
        if (hpDamage > 0f && attacker != null) {
            target.setLastDamagerId(attacker.getId());
        }
        state.record(CombatEvent.hit(state.getTick(),
                attacker == null ? -1 : attacker.getId(), target.getId(), hpDamage, crit, skillId));

        gainEnergy(state, attacker, GameBalance.ENERGY_PER_HIT);
        gainEnergy(state, target, GameBalance.ENERGY_PER_HIT_TAKEN);

        if (basicAttack && attacker != null) {
            applyLifesteal(state, attacker, hpDamage);
        }
        if (basicAttack && hpDamage > 0f) {
            onBasicAttackHit(state, attacker, target, hpDamage); // CP21 旧疤 + CP25 附带（后置 CP 充实）
        }
        applyReflect(state, attacker, target, hpDamage);           // 余震：甲后 ×ratio 真伤回攻击者
    }

    /** 收割增伤（刺客4/6 EXECUTE：目标 hpRatio < value2% → +value%） */
    private static float executeAmpOf(BattleUnit attacker, BattleUnit target) {
        EffectData execute = attacker == null ? null
                : attacker.getMechanic(SynergyMechanic.EXECUTE);
        if (execute == null || target.hpRatio() >= execute.getValue2() / 100f) {
            return 0f;
        }
        return execute.getValue();
    }

    /** 机制值查询（无词/无单位=0；REFLECT 同法） */
    private static float mechanicValueOf(BattleUnit unit, SynergyMechanic kind) {
        EffectData data = unit == null ? null : unit.getMechanic(kind);
        return data == null ? 0f : data.getValue();
    }

    /** 余震（巨人4/6 REFLECT）：甲后实际扣血 ×value% 真伤给攻击者——可致死、入级联、归因=反弹者 */
    private void applyReflect(BattleState state, BattleUnit attacker, BattleUnit target, float hpDamage) {
        float ratio = mechanicValueOf(target, SynergyMechanic.REFLECT);
        if (ratio <= 0f || hpDamage <= 0f || attacker == null || !attacker.isActable()) {
            return;
        }
        applyTrueDamage(state, target, attacker, hpDamage * ratio / 100f, "reflect");
    }

    /** 普攻命中落地挂点（CP21 旧疤计数 + CP25 附带状态；空实现先占位） */
    private void onBasicAttackHit(BattleState state, BattleUnit attacker, BattleUnit target,
                                  float hpDamage) {
        // CP21/CP25 充实（本 CP 空占位，保证方法体不再重写）
    }
```
（调用点统一替换：近战传 `critBonusPp=0f, armorPenRatio=0f`；技能 DAMAGE 分支传 `armorPenRatio = caster 机制 SPELL_PEN 的 value/100`——接线在 CP31，本 CP 先传 0；`SkillExecutor` 的 DAMAGE case 同步换新签名。）
- **测试要点**：`DamagePipelineTest` 增：① 免伤 15% 只作用直伤（DOT/真伤全额）；② 反弹=甲后实际扣血×18% 真伤回攻击者、无视攻击者护甲、可致死且归因=反弹者（联 CP13/14）；③ 反弹对 DOT 零触发；④ EXECUTE 阈值 35% 上下伤害 +40%/+0%；⑤ 穿甲 50% 对甲 47 的有效伤提升断言（CP31 接线后复测）。

---

### CP21. 旧疤（SCAR：受击计数 → 计数通道）

- **类型**：修改方法（充实 onBasicAttackHit 的旧疤半段）
- **位置**：`systems/DamagePipeline.java`（onBasicAttackHit，CP20 占位）
- **改动说明**：目标含 SCAR 机制时，普攻直伤命中计数（DOT/真伤/反弹天然不经此）；每 value 次命中 +1 层（封顶 value2）；层数经 CombatCounters 进第二级（攻 PCT×5/层、甲 ADD×3/层，K2 公式即 v5 §一.1(6)）。
- **代码**（充实 CP20 占位方法）：
```java
    /** 普攻命中落地挂点：旧疤计数（仅普攻直伤；DOT/真伤/反弹不喂层）+ 附带状态（CP25） */
    private void onBasicAttackHit(BattleState state, BattleUnit attacker, BattleUnit target,
                                  float hpDamage) {
        EffectData scar = target.getMechanic(SynergyMechanic.SCAR);
        if (scar != null && hpDamage > 0f) {
            target.onScarHit(); // 见下：计数入 combatCounters（内部按 value 封顶判层）
        }
        applyBasicAttackStatus(state, attacker, target); // CP25 充实（本 CP 空占位）
    }
```
（`BattleUnit.onScarHit()`：`combatCounters.addBasicAttackHit()` 累计**被击**次数（复用计数器新增一个 `basicHitTakenCount` 私有 int），达 SCAR.value 次→`addScarStack()`（层数<value2 才加）并清零被击计数，随后 `invalidateStats()`。——实现细节归执行器，语义如上。）
- **测试要点**：`DamagePipelineTest` 增：① 3 刀一层、6 层封顶；② 层数立即反映到有效攻/甲（+5%×N / +3×N）；③ DOT 与反弹命中不喂层；④ 战斗内永久（无到期），新一场不继承（开战新 BattleUnit 天然成立）。

---

### CP22. 开局 TRAITS 谓词 + 群星响应（OPENING_ENERGY / AFTER_CAST_ENERGY）

- **类型**：修改方法（applyOpeningEffects 重写）+ SynergySnapshot 分流 + 修改方法（SkillExecutor.cast 尾钩）
- **位置**：`systems/BattleSystem.java:293-308`、`systems/SynergySystem.java`（mechanics→opening 分流，批0 已暴露 mechanics——本 CP 把 OPENING_ENERGY 并入 openingEffects 列表）、`systems/SkillExecutor.java:76-78`（cast 成功路径尾部）
- **改动说明**：开局通道从「只认 SHIELD」扩为「SHIELD（现状）+ OPENING_ENERGY」，target 谓词 ALLIES（该侧全体）/TRAITS+trait（该侧匹配 race 或 class 者）。敌方侧对称（D10 截断后档位决定）。
- **代码**：

`applyOpeningEffects` 修改前（293-308 行）：
```java
    /** 开局效果（口径 #17）：effect 通道对"该侧"全部单位落地；本期仅支持 SHIELD */
    private void applyOpeningEffects(BattleState state, SynergySnapshot snapshot, Side side) {
        for (EffectData effect : snapshot.getOpeningEffects()) {
            if (effect.getEffect() != StatusType.SHIELD) {
                throw new IllegalStateException("本期 effect 通道仅支持 SHIELD，遇到: " + effect.getEffect());
            }
            for (BattleUnit unit : state.getUnits()) {
                if (unit.getSide() != side) {
                    continue;
                }
                statusSystem.apply(state, unit, StatusType.SHIELD,
                        unit.getEffective(StatKey.HP) * effect.getValue(),
                        Float.POSITIVE_INFINITY, -1);
            }
        }
    }
```
修改后（谓词过滤 + 双词分发）：
```java
    /** 开局效果（口径 #17 扩：SHIELD + OPENING_ENERGY；target 谓词 ALLIES/TRAITS+trait） */
    private void applyOpeningEffects(BattleState state, SynergySnapshot snapshot, Side side) {
        for (EffectData effect : snapshot.getOpeningEffects()) {
            for (BattleUnit unit : state.getUnits()) {
                if (unit.getSide() != side || !effectAppliesTo(effect, unit)) {
                    continue;
                }
                if (effect.getEffect() == StatusType.SHIELD) {
                    statusSystem.apply(state, unit, StatusType.SHIELD,
                            unit.getEffective(StatKey.HP) * effect.getValue(),
                            Float.POSITIVE_INFINITY, -1);
                } else if (effect.getMechanic() == SynergyMechanic.OPENING_ENERGY) {
                    damagePipeline.gainEnergy(state, unit, effect.getValue()); // 走回能加权
                } else {
                    throw new IllegalStateException("开局通道不支持的效果: " + effect);
                }
            }
        }
    }

    /** target 谓词（K1）：ALLIES=该侧全体；TRAITS=race 或 class 命中 trait */
    private static boolean effectAppliesTo(EffectData effect, BattleUnit unit) {
        if (effect.getTarget() != EffectTarget.TRAITS) {
            return true;
        }
        String trait = effect.getTrait();
        return trait != null && (trait.equals(unit.getTemplate().getRace())
                || trait.equals(unit.getTemplate().getUnitClass()));
    }
```
（SynergySystem 分流规则：`isStatChannel()` → stat；`effect == SHIELD || mechanic == OPENING_ENERGY` → openingEffects；其余 mechanic → mechanics。BattleUnit 机制下发在 deriveUnit 内按 `effectAppliesTo` 同谓词过滤——`unit.getMechanic(kind)` 返回首个命中条目。）

`SkillExecutor.cast` 成功路径（76-78 行）`return true;` 前插入：
```java
        EffectData afterCast = caster.getMechanic(SynergyMechanic.AFTER_CAST_ENERGY);
        if (afterCast != null) {
            damagePipeline.gainEnergy(state, caster, afterCast.getValue()); // 群星：施法后 +20（K 口径：施放者本人）
        }
```
- **测试要点**：`BattleSystemTest` 增：① 法师(6)+双野兽杂兵阵容开局仅法师侧法师系单位能量 +50（TRAITS 过滤、敌方不入）；② 施法后 +20（受 energyGainRate 加权）；③ 遇未知开局 effect 抛错（fail-fast 保留）；④ SynergySystemTest：OPENING_ENERGY 进 openingEffects、AFTER_CAST_ENERGY 进 mechanics。

---

### CP23. TimedTriggerSystem + 卫队战吼范围嘲讽（WARCRY_TAUNT，K7）

- **类型**：新建类 / 修改字段与方法（BattleState 触发队列、BattleUnit 嘲讽位、retargetAll 豁免）
- **位置**：新建 `systems/TimedTriggerSystem.java`；`entities/BattleState.java`（pendingTimedTriggers）、`entities/BattleUnit.java`（tauntTargetId/tauntUntilTick）、`systems/BattleSystem.java`（startBattle 调度 + step 消费 + retargetAll 豁免 + act 失效解除）、`systems/TargetingSystem.java:46-55`
- **改动说明**：定时触发器=独立机制类（v5 §4.1「不走开局通道（t=0 语义）；确定性时间点」）。BattleState 持 `List<TimedTrigger>{atTick, EffectData, Side}`（framework-internal add/consume，id 序=调度序）。战吼：旗手选取/半径/持续按 K7；嘲讽期内 retargetAll 跳过该单位；目标失效（清扫/倒地）立即解除并按需重选。
- **代码**（关键新逻辑）：

新建 `systems/TimedTriggerSystem.java`（完整）：
```java
package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.EffectData;
import com.voidvvv.kz_auto_chess_n.data.SynergyMechanic;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import com.voidvvv.kz_auto_chess_n.entities.TimedTrigger;

import java.util.ArrayList;
import java.util.List;

/** 定时触发器（v5 §4.1：确定性时间点、不走开局通道）。本期唯一词条=卫队战吼（WARCRY_TAUNT）。 */
public final class TimedTriggerSystem {

    /** 开战调度：两侧快照中的 WARCRY_TAUNT（value=延迟秒）入队（BattleSystem.startBattle 调） */
    public void scheduleFrom(BattleState state, SynergySnapshot snapshot, Side side) {
        for (EffectData effect : snapshot.getMechanics()) {
            if (effect.getMechanic() == SynergyMechanic.WARCRY_TAUNT) {
                state.addTimedTrigger(new TimedTrigger(
                        Math.round(effect.getValue() / GameBalance.LOGIC_STEP), effect, side));
            }
        }
    }

    /** step 消费：到期触发（BattleSystem.step 于 beginTick 后调用；id 序=调度序） */
    public void advance(BattleState state) {
        List<TimedTrigger> due = new ArrayList<TimedTrigger>();
        state.drainDueTimedTriggers(state.getTick(), due);
        for (TimedTrigger trigger : due) {
            if (trigger.getEffect().getMechanic() == SynergyMechanic.WARCRY_TAUNT) {
                executeWarcry(state, trigger);
            }
        }
    }

    /** 卫队战吼：旗手=该侧含机制单位中「最前」（玩家 min y / 敌方 max y，平局 min x）；
     *  曼哈顿 ≤半径 的敌方可行动存活者 targetId 强制指向旗手 value2 秒（豁免全局重评估） */
    private void executeWarcry(BattleState state, TimedTrigger trigger) {
        BattleUnit banner = null;
        for (BattleUnit unit : state.getUnits()) {
            if (unit.getSide() != trigger.getSide() || !unit.isActable()
                    || unit.getMechanic(SynergyMechanic.WARCRY_TAUNT) == null) {
                continue;
            }
            int frontness = trigger.getSide() == Side.PLAYER ? -unit.getGridY() : unit.getGridY();
            int bannerFrontness = banner == null ? Integer.MIN_VALUE
                    : (trigger.getSide() == Side.PLAYER ? -banner.getGridY() : banner.getGridY());
            if (banner == null || frontness > bannerFrontness
                    || (frontness == bannerFrontness && unit.getGridX() < banner.getGridX())) {
                banner = unit;
            }
        }
        if (banner == null) {
            return;
        }
        int untilTick = state.getTick() + Math.round(
                trigger.getEffect().getValue2() / GameBalance.LOGIC_STEP);
        for (BattleUnit enemy : state.getUnits()) {
            if (enemy.getSide() == trigger.getSide() || !enemy.isActable()) {
                continue;
            }
            int distance = Math.abs(enemy.getGridX() - banner.getGridX())
                    + Math.abs(enemy.getGridY() - banner.getGridY());
            if (distance <= GameBalance.WARCRY_TAUNT_RADIUS) {
                enemy.setTaunted(banner.getId(), untilTick);
            }
        }
    }
}
```
（`GameBalance.WARCRY_TAUNT_RADIUS = 1`；`entities/TimedTrigger` 不可变小记录；`BattleUnit` 增 `tauntTargetId/tauntUntilTick` + `setTaunted/isTaunted/clearExpiredTaunt(tick)`。）

`TargetingSystem.retargetAll` 修改前（46-55 行）：
```java
    /** 每 120 tick（RETARGET_INTERVAL）全局强制重评估（id 序，含清空无候选者的目标） */
    public void retargetAll(BattleState state) {
        for (BattleUnit unit : state.getUnits()) {
            if (unit.isCleaned()) {
                continue;
            }
            BattleUnit target = findTarget(state, unit);
            unit.setTargetId(target == null ? -1 : target.getId());
        }
    }
```
修改后（嘲讽豁免；到期自动出豁免）：
```java
    /** 每 120 tick（RETARGET_INTERVAL）全局强制重评估（id 序；嘲讽 override 期豁免——K7） */
    public void retargetAll(BattleState state) {
        for (BattleUnit unit : state.getUnits()) {
            if (unit.isCleaned()) {
                continue;
            }
            if (unit.isTaunted(state.getTick())) {
                continue; // 嘲讽期不参与重评估（BattleSystem.java:138-140 会被冲掉的防护）
            }
            BattleUnit target = findTarget(state, unit);
            unit.setTargetId(target == null ? -1 : target.getId());
        }
    }
```
（`BattleSystem.act`：currentTarget 为 null 且单位被嘲讽→先 `clearExpiredTaunt`；嘲讽目标失效（isCleaned/倒地）→ 解除嘲讽走常规重选——实现落 currentTarget 调用点前三行。）
- **测试要点**：新建 `systems/TimedTriggerSystemTest`：① 开战 6s 恰好触发（tick=360）、半径 1 内 3 敌全部 targetId=旗手、圈外不受影响；② 嘲讽期第 2 次 retargetAll 不改写被嘲讽者目标；③ 3s 到期后重评估恢复自由索敌；④ 旗手死亡→被嘲讽者 currentTarget 失效立即重选；⑤ 敌方战士(4) 对玩家镜像成立（对称）。

---

### CP24. 盾墙拦截（SHIELD_WALL，只拦普攻弹道，K8）

- **类型**：修改方法（ProjectileSystem.advanceAll 循环头插一步）+ 新事件词
- **位置**：`systems/ProjectileSystem.java:31`（advanceAll 循环头部；CP18 已重写该方法——本 CP 叠加）+ `entities/CombatEvent.java`（PROJECTILE_BLOCKED）
- **改动说明**：代码改动叠加于 §6.CP18 重写后的 advanceAll：循环体最前插入拦截判定。条件：`projectile.getSkill() == null`（普攻弹分界现成）且目标侧存在「可行动存活 + 含 SHIELD_WALL 机制 + 目标在其身后」的战士→取**距目标曼哈顿最近**（平局 id 小）者挡下：弹移除 + PROJECTILE_BLOCKED 事件（sourceId=拦截者，targetId=原目标）、无伤害、附带状态不落地（CP25 天然落空）。技能弹/近战不受限。
- **代码**（插入块）：
```java
            if (projectile.getSkill() == null && tryShieldWallIntercept(state, projectile, target)) {
                continue; // 被最近身前战士挡下（只拦不弹）；附带状态因 impact 未发生而落空
            }
```
```java
    /** 盾墙：目标侧「含 SHIELD_WALL、可行动存活、目标在其身后」的战士中距目标最近者挡下普攻弹 */
    private static boolean tryShieldWallIntercept(BattleState state, Projectile projectile,
                                                  BattleUnit target) {
        if (target == null) {
            return false;
        }
        BattleUnit blocker = null;
        int bestDistance = 0;
        for (BattleUnit candidate : state.getUnits()) {
            if (candidate.getSide() != target.getSide() || !candidate.isActable()
                    || candidate.getMechanic(SynergyMechanic.SHIELD_WALL) == null) {
                continue;
            }
            boolean behind = candidate.getSide() == Side.PLAYER
                    ? target.getGridY() > candidate.getGridY()
                    : target.getGridY() < candidate.getGridY();
            if (!behind) {
                continue;
            }
            int distance = Math.abs(candidate.getGridX() - target.getGridX())
                    + Math.abs(candidate.getGridY() - target.getGridY());
            if (blocker == null || distance < bestDistance
                    || (distance == bestDistance && candidate.getId() < blocker.getId())) {
                blocker = candidate;
                bestDistance = distance;
            }
        }
        if (blocker == null) {
            return false;
        }
        state.record(CombatEvent.projectileBlocked(state.getTick(), blocker.getId(), target.getId()));
        state.removeProjectile(projectile);
        return true;
    }
```
（`CombatEvent` 增 `PROJECTILE_BLOCKED` 词与工厂。）
- **测试要点**：`ProjectileSystemTest` 增：① 普攻弹飞向战士身后友军→拦截（无 HIT、有 BLOCKED、拦截者零掉血）；② 技能弹/近战直击不受限；③ 目标不在任何战士身后（目标自己最靠后位）→ 不拦；④ 拦截后普攻附带状态未挂（联 CP25）；⑤ 多战士取距目标最近。

---

### CP25. 普攻附带状态挂点（BASIC_ATTACK_STATUS：BURN / SLOW）

- **类型**：修改方法（充实 onBasicAttackHit 的附带半段）
- **位置**：`systems/DamagePipeline.java`（onBasicAttackHit，CP21 留空位）
- **改动说明**：攻击者含 BASIC_ATTACK_STATUS → 命中落地对目标挂状态。威力口径：DOT 类（BURN）power=本次 attackPower×value/100（百分点刻度，K1）；非 DOT（SLOW）power=value。近战在 applyDirectHit 内（attackPower=有效攻）；弹道在 impact（attackPower=发射快照，盾墙拦截则不发生）。
- **代码**（充实占位）：
```java
    /** 普攻附带状态（兽人灼烧/野兽围狩）：近战在直伤内、弹道在 impact 后调（attackPower=对应快照） */
    void applyBasicAttackStatusOnPower(BattleState state, BattleUnit attacker, BattleUnit target,
                                       float attackPower) {
        EffectData attach = attacker.getMechanic(SynergyMechanic.BASIC_ATTACK_STATUS);
        if (attach == null || !target.isActable()) {
            return;
        }
        float power = statusSystem.isDotType(attach.getStatus())
                ? attackPower * attach.getValue() / 100f   // BURN：atk×30% 每跳（百分点刻度）
                : attach.getValue();                        // SLOW：power=百分点
        statusSystem.apply(state, target, attach.getStatus(), power,
                attach.getDuration(), attacker.getId());
    }
```
（近战路径：onBasicAttackHit 调 `applyBasicAttackStatusOnPower(state, attacker, target, 本发 attackPower)`；弹道路径：`ProjectileSystem.impact` 普攻分支命中后调同方法传 `projectile.getAttackSnapshot()`；`isDotType` 从 SkillExecutor 提为 StatusSystem/DamagePipeline 共用静态。）
- **测试要点**：`DamagePipelineTest`/`ProjectileSystemTest` 增：① 兽人(4) 普攻命中挂 BURN=atk×0.3/s 3s（刷新取大）；② 野兽(4) 挂 SLOW 30/2s；③ 弹道附带取发射快照而非命中时攻；④ 盾墙拦截后不挂（CP24 联测）；⑤ DOT/真伤命中不触发附带（非普攻路径）。

---

### CP26. 收割条件增伤（EXECUTE）专项测试

- **类型**：新增测试（接线已在 CP20 完成）
- **位置**：`systems/DamagePipelineTest` 扩充；数据已在 CP4（syn_assassin 4/6 档）
- **改动说明**：代码改动见 §6.CP20（executeAmpOf）；本 CP 为验证收口：阈值语义（<35% 严格小于）、与免伤/穿甲/暴击的乘算序（同一乘法链，顺序无关但断言总乘积）、技能直伤同样吃收割（载体=处决 ×2.5 联动）。
- **测试要点**：① 目标 34%/36% 两档断言；② 技能直伤（处决）对残血目标 +40%；③ 与免伤叠加=×1.4×0.85 互不影响；④ 敌方刺客(4) 对玩家残血单位同样生效（对称）。

---

### CP27. 三连装填（CRIT_RHYTHM：计数 + 必暴覆写 + ×2.0，K10）

- **类型**：修改方法（BattleSystem.act 出手段）
- **位置**：`systems/BattleSystem.java:219-228`（CP1 后形态）
- **改动说明**：出手时计数 +1（近战与发射同点=确定性）；第 value 刀 roll 后覆写 crit=true 并带 value2 pp 暴伤加成（经 critBonusPp 参数直达 DamagePipeline，CP20 已就位）。**RNG 消耗序不变**（roll 照常先行，覆写仅改结果）——确定性回放不受影响。
- **代码**（CP1 合并后的当前形态为「修改前」）：

修改前（CP1 后形态，219-228 行）：
```java
            boolean crit = state.getRng().nextFloat()
                    < unit.getEffective(StatKey.CRIT_CHANCE) / 100f;
            state.record(CombatEvent.attackLaunched(state.getTick(), unit.getId(), target.getId()));
            if (unit.getEffective(StatKey.RANGE) >= 2f) {
                state.spawnProjectile(new Projectile(unit.getId(), target.getId(),
                        unit.getGridX() + 0.5f, unit.getGridY() + 0.5f,
                        unit.getEffective(StatKey.ATTACK), crit, null, 1f)); // 口径 #12：远程普攻 = HOMING 锁定弹
            } else {
                damagePipeline.applyDirectHit(state, unit, target,
                        unit.getEffective(StatKey.ATTACK), 1f, crit, true, null); // 近战即时结算
            }
```
修改后：
```java
            boolean rolled = state.getRng().nextFloat()
                    < unit.getEffective(StatKey.CRIT_CHANCE) / 100f; // roll 照常消耗（序不变）
            unit.getCombatCounters().addBasicAttack();               // 三连装填计数（近战/发射同点）
            float critBonusPp = 0f;
            boolean crit = rolled;
            EffectData rhythm = unit.getMechanic(SynergyMechanic.CRIT_RHYTHM);
            if (rhythm != null
                    && unit.getCombatCounters().getBasicAttackCount() % (int) rhythm.getValue() == 0f) {
                crit = true;                                          // 必暴：覆写结果、不增消耗
                critBonusPp = rhythm.getValue2();                     // 暴伤 +50pp（×1.5→×2.0，仅必暴刀）
            }
            state.record(CombatEvent.attackLaunched(state.getTick(), unit.getId(), target.getId()));
            if (unit.getEffective(StatKey.RANGE) >= 2f) {
                state.spawnProjectile(new Projectile(unit.getId(), target.getId(),
                        unit.getGridX() + 0.5f, unit.getGridY() + 0.5f,
                        unit.getEffective(StatKey.ATTACK), crit, critBonusPp, null, 1f, false));
            } else {
                damagePipeline.applyDirectHit(state, unit, target,
                        unit.getEffective(StatKey.ATTACK), 1f, crit, critBonusPp, true, null, 0f);
            }
```
（`Projectile` 增 `critBonusPp` 字段随载荷冻结，impact 透传——构造器在 CP18 全参基础上再扩一位或并入 scaleFactor 旁新参；执行器按「第 10 参 float critBonusPp」落位，旧构造委托 0。）
- **测试要点**：① 同 seed 对拍：游侠(6) 在场与不在场两种 run 的**RNG 消耗序列逐位一致**（覆写不改序——确定性红线）；② 第 3/6/9 刀必暴且倍率 2.0、其余刀按 40% 概率（seed 固定断言具体刀序）；③ critChance 35%（20+15）生效于非必暴刀；④ 计数不因目标死亡/换目标清零（per-unit 累计）。

---

### CP28. 实施报告模板 + 全量自测与冒烟收口

- **类型**：新建文档（模板落盘，执行方填充）
- **位置**：新建 `docs/spec_plan/2026-XX-XX_units_synergies_v5_impl_report.md`（日期=执行完成日）
- **改动说明**：报告结构（派单第 5 项）由本计划定死，执行方按批填充。模板骨架（执行方照抄落盘后填写）：
```markdown
# 棋子与羁绊 v5 实施报告

> 执行方：libgdx-impl-executor ｜ 完成日期：YYYY-MM-DD ｜ 计划：2026-11-21_units_synergies_v5.md

## 1. 改动清单（文件 × CP 映射表）
| 文件 | 批 | CP | 改动摘要 | 增/删行数 |

## 2. 分节说明（按批 0~5：每批「做了什么 / 与计划的偏差及原因 / 验证记录」）
（含：每批 gradle 退出码 + TEST-*.xml 聚合 tests/failures/errors/skipped 计数、批1 两次冒烟记录）

## 3. 图形与颜色映射表及理由
（24 子 ×（种族→图形族、职业→变体、星级→色）逐行表 + 三轴正交可读性自查结论 + 微调记录）

## 4. 未实施项（与计划 §8.4 对账）
（§五.1 波次内容化 / §五.6 叠加制 / 其余登记项——逐条「未做原因 + 建议后续载体」）

## 5. 遗留风险与观察点
（§8.1/8.2 各 R/W 项的现状判定：纯 6 巨人超时、点算空转率、双上阵口径注记等）

## 6. RNG 消耗点登记复核
（architecture §六清单补注：CRIT_RHYTHM 覆写复用既有消耗点、零新增——对拍证据）

## 7. 手动冒烟清单与结果
（lwjgl3:run：图形/颜色/嘲讽/盾墙/亡语连环/倒地复活/放逐 各一条观察记录）
```
- **测试要点**（收口验收）：① 全量 `gradlew build` 退出码 0；② `gradlew :core:test` XML 聚合 0 failures/0 errors（总数对比批0 记录的基线，只增不减）；③ 冒烟七条全过并留痕；④ 报告七节齐全、未实施项与计划 §8 逐条对账。

## 7. 分阶段任务拆解（批次总览）

配图：`../diagrams/units_synergies_v5_batches.md`（+.html）。每批 = 1 个及以上 conventional commit（`feat:`/`test:`/`docs:`，无 attribution），批末验证门全过才准合入下一批。批0 开始前执行方先记录**测试基线计数**（当前工作区 `gradlew :core:test` 的 XML 聚合 tests 数）供后续只增不减核对。

| 批 | 内容 | CP | 预估文件数（改+新） | 验证门 |
|---|---|---|---|---|
| 批0 纯数据+词表基建 | 数据四件全量重写、暴击属性化、三通道解析、D10 截断、放逐原语与薄执行器 | CP1~CP8 | 数据 4 + 主代码 ~14 + 测试 ~8 | 构建 0 错；全测试绿（XML 聚合）；`gradlew :lwjgl3:run` 可进局打 3 轮（旧机制回归冒烟：商店购买/合成/开战/宝箱） |
| 批1 渲染图形/颜色 | UnitShapeKit/StarColors、paintUnit 形状化、tint 接线 | CP9~CP11 | 主代码 4 + 测试 2 | 🔴 两段提交：a=仅 unit_warrior_01 新绘制 → lwjgl3:run 冒烟四条（CP10 清单）→ b=铺开 24 子 + 全量测试绿 + 冒烟三条（CP11 清单） |
| 批2 死亡簇 | 计数通道、击杀归因、两遍式级联、亡语+双响、名册、点算、undying 倒地、亡语弹道、兽潮/清账、表现事件 | CP12~CP19 | 主代码 ~10 + 测试 ~7 | 构建+全测试绿；冒烟：6 亡灵阵容对 r12 敌阵一场，肉眼确认连环亡语/点算放逐/死骑倒地复活 |
| 批3 受击簇 | 受击窗口（免伤/收割/反弹/穿甲参数）、旧疤 | CP20~CP21 | 主代码 2 + 测试 2 | 构建+全测试绿；冒烟：6 巨人 vs 近战敌阵观察反弹飘字与超时风险 |
| 批4 开局/条件/附带 | 开局谓词+群星、定时触发+战吼、盾墙、穿甲接线、普攻附带、收割测试、三连装填 | CP22~CP27 | 主代码 ~6 + 测试 ~6 | 构建+全测试绿；**确定性对拍专项**：同 seed 双 run 事件流逐位一致（RNG 零新增证明）；冒烟：战吼劫持/盾墙拦弹/法师开局速大 |
| 批5 报告+全量自测 | 实施报告、全量回归、冒烟收口 | CP28 | 文档 1 | 报告七节齐全；全量 build+test 0 错；冒烟七条留痕 |

**各批文件清单**（新增 N / 修改 M，详单见各 CP「位置」）：

- **批0**：assets/data/{units,skills,synergies,scenes}.json（4 改）；core 主代码——改：`StatKey`/`StatusType`/`SkillEffectType`/`EffectData`/`SkillData`/`UnitData`/`BaseStats`/`BattleStats`/`StatPipeline`/`GameBalance`/`JsonLoader`/`BattleSystem`/`DamagePipeline`/`SkillExecutor`/`StatusSystem`/`SynergySystem`/`CombatEvent`/`BattleUnit`/`RunFlowSystem`/`BattleConsoleMain`；新：`SynergyMechanic`/`DeathrattleKind`/`DeathrattleSpec`/`UndyingSpec`/`CastCondition`/`Banish`。测试：新 `UnitsMatrixTest`/`BanishTest` + 扩 `JsonLoader*`/`SynergySystemTest`/`StatPipelineTest`/`BattleSystemTest`/`SkillExecutorTest`/`StatusSystemTest`/`DamagePipelineTest`/`WaveGeneratorTest`/`BattleTestFixtures`。
- **批1**：新 `render/UnitShapeKit`/`render/StarColors` + 测试 ×2；改 `PlaceholderArt`/`UnitView`/`BattleRenderer`/`ShopBar`。
- **批2**：新 `entities/CombatCounters`/`systems/DeathSystem`/`systems/DeathrattleExecutor`/`systems/DeathBenefitRule`/`systems/UndyingRule` + 测试 ×5；改 `BattleUnit`/`BattleState`/`BattleSystem`/`TargetingSystem`/`SkillExecutor`/`StatusSystem`/`DamagePipeline`/`Projectile`/`ProjectileSystem`/`CombatEvent`/`FloatTextFormat`/`UnitView`。
- **批3**：改 `DamagePipeline`/`BattleUnit`/`BattleSystem`/`SkillExecutor`/`ProjectileSystem`（调用点签名）+ 扩测 ×2。
- **批4**：新 `entities/TimedTrigger`/`systems/TimedTriggerSystem` + 测试；改 `BattleState`/`BattleUnit`/`BattleSystem`/`TargetingSystem`/`ProjectileSystem`/`CombatEvent`/`SkillExecutor`/`DamagePipeline`（挂点充实）+ 扩测。
- **批5**：新报告文档 ×1。

**可独立构建说明**：批0 机制词「解析先行、接线后置」（K13），未接线词条只是快照里的惰性数据，不改变既有行为；批2 前置批1 无代码依赖（纯渲染），若渲染冒烟受阻可先合批2（批序可交换批1↔批2，验证门不变——执行方按实际情况选择并在报告 §2 注明）；批4 的 CP20 扩参签名在批3 已就位，批4 各 CP 均为局部插入；批5 无代码。**批2 内部执行顺序**（同批多 commit 时遵守）：CP12 → CP13 → **CP17 先行**（isDowned/isActable/UndyingRule 等谓词与字段是 CP14/15/16 的编译前提）→ CP14 → CP15 → CP16 → CP18 → CP19。

## 8. 风险与开放问题

### 8.1 性能与体验观察点（v5 登记，不阻塞）

| # | 项 | 口径 |
|---|---|---|
| O1 | 纯 6 巨人 37~74s / 纯 6 战士超时高危 | 混编正解；调节杆=巨人 hp +300 或免伤 12%（纯数值，改 synergies.json 即回滚） |
| O2 | 点算空转率 >50% | 调节杆=Boss 轮门槛 25%→30%（v5 §3.4，不进默认；改 DEATH_AUDIT value 需按轮次分档——当前 schema 单值，若启用再议） |
| O3 | 双响 0.3s 间隔为演出层取舍 | 本计划按同 tick 两发实现（C5），无演出间隔——表现层如需错峰走渲染侧延迟播放入场（不改逻辑） |
| O4 | 亡灵镜像局（墓穴敌我双亡灵）互喂点算 | v5 示范稿担忧 #2；D10 截断已压敌方低轮档位；实测难受再调敌方池亡灵配比（纯数据） |

### 8.2 实施口径的开放点（默认按字面/计划口径实施，主人可随时改判——改动面均小）

| # | 开放点 | 本计划默认 | 备选与代价 |
|---|---|---|---|
| R1 | 余震/免伤是否作用于**技能直伤** | **作用**（字面：v5 §4.1 仅豁免 DOT/真伤，且「旧疤只认普攻」的对比措辞暗示反弹/免伤不限普攻） | 改「仅普攻」= executeAmpOf/applyReflect 各加 `basicAttack` 判断一行 |
| R2 | 游侠(6)「暴伤+50」归属 | **仅必暴刀 ×2.0**（§4.5 锚点只按必暴箭 2.0 计价） | 档位全体暴击 ×2.0=把 value2 移入 stat critDamage ADD 50 一行（火力大涨，需复锚） |
| R3 | 末班岗/守夜护盾时长 | **无时限**（v5 §2.1 未给时长；现 SHIELD 词汇无时限，示范稿的 8s 早于 v5） | 时限盾需扩 SHIELD duration 词表（parseSkillEffect 放行 + StatusSystem 时长语义）——独立小任务 |
| R4 | 连环清账回满能是否就地施放 | **不就地**（下一行动 tick 施放；避免清扫期嵌套 cast） | 就地施放=复用 tryCastInline 深度保护，1 行但有重入面 |
| R5 | 兽人(6) 灼烧时长 | 3s（同 (4) 档；v5 (6) 行未写时长） | 任意值=synergies.json duration 一处 |
| R6 | synergyAmp（薇拉）是否放大机制词数值 | **不放大**（裁决 D12 只声明 stat/effect 通道；机制词触发语义不宜乘算） | 若要放大需逐词定义放大语义（DEATH_TAX 加成 vs REFLECT 加成不同构） |
| R7 | startBattle 旧重载的敌方档位 | 委托 round=25（cap=6，等效不截断；存量测试语义不变） | 若测试造 >6 同质敌方阵容，期望值需按 cap 口径复核 |
| R8 | 盾墙「最近战士」参照系 | 距**目标**最近（确定性且稳定；v5 未指定） | 距弹道当前位置=每 tick 变化，不确定性更差，不建议 |

### 8.3 口径注记（无需改动，登记备查）

- **§五.4 双上阵计数**：`SynergySystem.resolve` 对上场模板逐个计 1（`SynergySystem.java:52-60`）——同名双上阵各计 1 已成立；备战侧 3 合 1 按 bench+deployed 全名单折叠（`ShopSystem.mergeCascade`），「同名上场+备战合计上限 2」由购买事件自动兑现，零代码改动。
- **零新增 RNG**：全计划唯一随机消耗点仍是普攻暴击 roll（`BattleSystem.java:219`）；CRIT_RHYTHM 覆写不增耗（CP27 对拍测试证明）；architecture §六补注归 gdd-writer/报告 §6。
- **CombatEvent 扩字段**（killerId + 4 新事件词）：事件流不落盘、无持久化对拍文件（save/ 包核实仅快照/档案），无兼容面。
- **v5 锚点复核结论**：本文引用的全部代码锚点（BattleSystem:160-181/219/294-307、BattleUnit:110-112/208-212、DamagePipeline:35-55/68-78、SkillExecutor:50-52/62-64/237、StatusSystem:73-76/100-105、StatPipeline:71-87、ProjectileSystem:33-37/52-54、WaveGenerator:66/70-73、JsonLoader:736-737/759-761、SynergyData:50-65）已逐条与当前源码对照，**无漂移**。
- **移除非死亡事件的面**：放逐后 `aliveCount` 立即减一（cleaned=true）——⑤ 判定即时反映；放逐不触发「战斗结束前亡语预算」任何路径（Banish 无 DeathSystem 足迹）。

### 8.4 未实施建议（主人派单口径：仅实施设计依赖）

| 项 | 内容 | 后续载体建议 |
|---|---|---|
| §五.1 敌方波次内容化 | 主题波次表（侦察可读、反制可备）；批0 的 enemyPool 重排已承载其数据雏形（weight/minRound） | scenes.json 扩 `waves` 结构 + WaveGenerator 波次表驱动（独立小计划） |
| §五.6 叠加制备选 | 低高档并存（P0-1 方案 B）；D6 已裁替换制 | 若启用：GDD §5.1 + `SynergySystem.resolve` 档位合并语义 + SynergyPanel 展示 |

## 9. 附录：用户确认记录

| 日期 | 事项 | 记录 |
|---|---|---|
| 2026-11-21 | v5 终审 | 主人终审通过 `docs/planning/units_synergies_design_v5.md`（四轮评审收敛、deepseek 终审无异议）；D3~D11 决策点一律按 v5 §七「本稿采用（临时方案）」执行（本计划 §4.1 D-A）。 |
| 2026-11-21 | 实施派单 | 范围=数据落地/机制落地（§4.1+C1~C13+§五.7）/渲染图形族/星级颜色/实施报告规划；批次形状（批0~批5）与三条红线（可构建分批提交、图形小步验证、XML 计数核对）经派单原文确立（§4.1 D-B/D/D-E）。 |
| 2026-11-21 | §五处理口径 | 仅实施设计依赖项（.2/.3/.5/.7 实施、.4 口径注记、.1/.6 未实施建议）——planner 判定结论见 §4.2，依据均为代码实读。 |
| — | 待主人裁决项 | §8.2 R1~R8（不阻塞，默认按本计划口径实施；任何一条改判均为局部小改动，执行方按改判单点返工）。 |

> 本计划无 BLOCKER 级未决问题（Phase 2 门禁通过）：v5 + 亡灵示范稿已覆盖全部内容缺口（含执旗冲锋/白骨滩 SLOW 等载荷数值）；与代码现实的全部接触点已实读核对（§8.3 锚点清单）。执行方可直接按 §7 批序开工。

