# 棋子与羁绊 v5 实施报告

> 执行方：libgdx-impl-executor ｜ 完成日期：2026-11-21 ｜ 计划：`docs/spec_plan/2026-11-21_units_synergies_v5.md`（CP1~CP28，批 0~5）
>
> 提交链（feature/new_idea-2）：`9a478f3`（批0）→ `b7ca513`+`31930d7`（批1 两段）→ `b00b91b`（批2）→ `d305802`（批3）→ `3439bea`（批4）；批5 = 本报告（纯文档，按派单口径不提交 git，保持 untracked）。

---

## 1. 改动清单（文件 × CP 映射表）

行数为 `git log --numstat 7f93b04..3439bea` 按文件聚合（跨批文件合并累计；分批贡献见 §2）。CP 归属以计划各 CP「位置」行为准。

### 1A. 数据文件（4 改，均在批0）

| 文件 | 批 | CP | 改动摘要 | 增/删行 |
|---|---|---|---|---|
| `assets/data/units.json` | 0 | CP5 | 非 Boss 24 子全量重写（4 种族×4 职业行列各 6、费阶 10/9/5），9 条 Boss 原样保留 | +103/−89 |
| `assets/data/skills.json` | 0 | CP5 | 追加 13 条技能 | +61/−1 |
| `assets/data/synergies.json` | 0 | CP4 | 8 条羁绊整文件替换为 2/4/6 替换制全量档 | +88/−68 |
| `assets/data/scenes.json` | 0 | CP6 | shopUnlocks 对齐 24 子 + enemyPool 主题重排（weight/minRound，D10 数据雏形） | +20/−11 |

### 1B. core 主代码（新建 15 / 修改 26）

| 文件 | 批 | CP | 改动摘要 | 增/删行 |
|---|---|---|---|---|
| `data/SynergyMechanic.java` | 0 | CP2 | 新建：机制词枚举 | +45 |
| `data/DeathrattleKind.java` | 0 | CP2 | 新建：亡语 4 kind | +13 |
| `data/DeathrattleSpec.java` | 0 | CP2 | 新建：亡语规格 | +33 |
| `data/UndyingSpec.java` | 0 | CP2 | 新建：不死规格 | +14 |
| `data/CastCondition.java` | 0 | CP2 | 新建：cast 条件 | +19 |
| `systems/Banish.java` | 0 | CP8 | 新建：放逐原语（markBanished + 移格 + 事件，无死亡结算足迹） | +24 |
| `render/UnitShapeKit.java` | 1a | CP9 | 新建：种族→图形族、职业→变体、逐行半宽表 | +65 |
| `render/StarColors.java` | 1a | CP9 | 新建：星级→tint 色（1★骨白/2★金/3★紫，越界钳制） | +22 |
| `render/PlaceholderArt.java` | 1a/1b | CP10 | paintUnit 形状化：a=pilot 白名单 `unit_warrior_01`；b=白名单整段移除铺开 24 子 | +67/−32 |
| `render/board/BattleRenderer.java` | 1b | CP11 | drawUnitFrame 星级 tint 接线 | +19/−17 |
| `render/ui/ShopBar.java` | 1b | CP11 | 商店卡星级色接线 | +4/−1 |
| `entities/CombatCounters.java` | 2/3 | CP12/CP21 | 新建：遗物/旧疤/普攻计数通道（批3 扩 SCAR 计数） | +49 |
| `systems/DeathSystem.java` | 2 | CP14 | 新建：两遍式级联清扫（pass1 快照+undying+markCleaned，pass2 亡语/名册/点算/兽潮/清账） | +93 |
| `systems/DeathrattleExecutor.java` | 2 | CP15 | 新建：亡语 4 kind 执行 + 双响（裁定 2 弹：#1 HIGHEST_ATK、#2 LOWEST_HP） | +177 |
| `systems/DeathBenefitRule.java` | 2 | CP16/CP19 | 新建：DEATH_TAX 继承 / DEATH_AUDIT 点算 / 兽潮 / 连环清账 四静态方法 | +127 |
| `systems/UndyingRule.java` | 2 | CP17 | 新建：undying 倒地判定与复活（1.0s、40% 血、能量回满） | +71 |
| `entities/TimedTrigger.java` | 4 | CP23 | 新建：确定性定时触发实体 | +36 |
| `systems/TimedTriggerSystem.java` | 4 | CP23 | 新建：开战调度 + step 消费；卫队战吼范围嘲讽 | +75 |
| `data/StatKey.java` | 0 | CP1 | 暴击两键（CRIT_CHANCE/CRIT_DAMAGE）属性化 | +13/−7 |
| `data/BaseStats.java` | 0 | CP1 | 构造器扩暴击两参（触发 ~20 个测试类夹具签名适配，见 1C） | +13/−4 |
| `entities/BattleStats.java` | 0 | CP1 | 暴击字段组 | +22/−6 |
| `data/EffectData.java` | 0 | CP2 | mechanic 第三通道 + 辅字段；偏差：10 参构造补 `op` 参数 | +50/−9 |
| `data/SkillEffectType.java` | 0 | CP2 | BANISH/GAIN_ENERGY/KNOCKBACK 新词 | +5/−2 |
| `data/StatusType.java` | 0 | CP2 | BURN 等状态词扩展 | +2/−1 |
| `data/SkillData.java` | 0 | CP2 | deathrattle/undying/castCondition 装载 | +10 |
| `data/UnitData.java` | 0 | CP2 | 亡语/不死字段 | +18 |
| `config/JsonLoader.java` | 0 | CP3 | 三通道解析 + 逐词校验 + parseDeathrattle/parseUndying/parseCastCondition | +281/−14 |
| `systems/StatPipeline.java` | 0/2 | CP1/CP12 | CP1 暴击取值走 getEffective + **Q1 裁决豁免**（deriveBaseline 对敌方 scale 暴击两键 effectiveScale=1）；CP12 计数通道并入 ADD/PCT | +26/−4 |
| `systems/SynergySystem.java` | 0/4 | CP7/CP22 | CP7 D10 敌方档位截断（resolve 计数上限）；CP22 OPENING_ENERGY 并入 openingEffects 分流 | +22/−4 |
| `systems/SynergySnapshot.java` | 0 | CP2/CP7 | mechanics 列表暴露 | +14/−3 |
| `systems/BattleSystem.java` | 0/2/3/4 | CP1/CP7/CP14/CP17/CP20/CP22~24/CP27 | 暴击 roll 走 getEffective；startBattle 组装 DeathSystem（构造环→批4 改 bindStatusSystem 组装期绑定）；undying 复活检查；CP27 三连装填（:239 roll + 覆写） | +82/−31 |
| `systems/DamagePipeline.java` | 0/2/3/4 | CP1/CP13/CP20/CP25 | CP20 受击窗口全序（收割→免伤→甲/穿甲→盾→血→HIT→回能→吸血→旧疤→余震→附带挂点）；CP25 附带挂点单点化于 applyDirectHit | +122/−14 |
| `systems/SkillExecutor.java` | 0/2/3/4 | CP8/CP17/CP20/CP22 | CP8 三新词执行 + cast 条件 + isDotType；CP17 四过滤点补 downed；批4 穿甲接线（CP"31"笔误，实际落批4） | +86/−14 |
| `systems/StatusSystem.java` | 0/2/4 | CP8/CP17 | BURN 心跳（跳伤归施加者）；倒地 tickStatuses 整体跳过=时钟冻结；批4 isDotType 提为共用静态 + bindStatusSystem | +10/−3 |
| `entities/BattleUnit.java` | 0/2/3/4 | CP8/CP12/CP13/CP17/CP20/CP23 | markBanished；lastDamagerId 击杀归因；downed 字段组/hasControl/isActable（findUnit 重构含 ghost 星级）；CP23 嘲讽位 | +164/−3 |
| `entities/CombatEvent.java` | 0/2/4 | CP8/CP13/CP18/CP24 | 新事件词 + killerId；偏差：批2 扩 11 字段保旧 10 参构造委托 | +46/−5 |
| `entities/Projectile.java` | 2/4 | CP18/CP27 | deathrattleBorn 标记；critBonusPp 随载荷冻结 | +28/−1 |
| `systems/ProjectileSystem.java` | 2/3/4 | CP18/CP20/CP24/CP27 | 亡语源弹真伤路由 + 消散重选；盾墙拦截（排除 deathrattleBorn，防绝响被吞）；CP27 impact 透传 critBonusPp | +65/−7 |
| `systems/TargetingSystem.java` | 2/4 | CP17/CP23 | downed 过滤；战吼期 retarget 豁免与解除 | +7/−3 |
| `render/FloatTextFormat.java` | 2 | CP19 | 点算/放逐/倒地表现文案 | +4 |
| `render/board/UnitView.java` | 1b/2 | CP11/CP19 | 星级 tint；倒地表现 | +6/−1 |
| `config/GameBalance.java` | 0/2/4 | CP1/CP7/CP12/CP23 | 暴击常量、D10 上限、旧疤幅度、WARCRY_TAUNT_RADIUS | +20/−2 |
| `systems/RunFlowSystem.java` | 0 | CP7 | startBattle 新签名适配 | +1/−1 |
| `tools/BattleConsoleMain.java` | 0 | CP7 | 同上 | +3/−1 |

### 1C. 测试代码（新建 14 / 修改 39）

| 文件 | 批 | CP | 改动摘要 | 增/删行 |
|---|---|---|---|---|
| `config/UnitsMatrixTest.java` | 0 | CP5 | 新建：24 子行列/费阶矩阵校验 | +124 |
| `systems/BanishTest.java` | 0 | CP8 | 新建：放逐原语 | +68 |
| `render/UnitShapeKitTest.java` | 1a | CP9 | 新建：图形族/变体映射 | +47 |
| `render/StarColorsTest.java` | 1a | CP9 | 新建：三色互异 + 越界钳制 + 与敌我框色 RGB 欧氏距离 >0.3 | +52 |
| `entities/CombatEventTest.java` | 2 | CP13/CP18/CP24 | 新建：事件词与工厂 | +44 |
| `render/FloatTextFormatTest.java` | 2 | CP19 | 新建：表现文案 | +12 |
| `systems/DeathBenefitRuleTest.java` | 2 | CP16/CP19 | 新建：名册/点算/兽潮/清账 | +276 |
| `systems/DeathSystemTest.java` | 2 | CP14 | 新建：两遍式级联（偏差：构造器去未用参、TIME_EPSILON 复活判定沿 StatusSystem 惯例） | +147 |
| `systems/DeathrattleExecutorTest.java` | 2 | CP15 | 新建：4 kind + 双响 2 弹口径 | +213 |
| `systems/UndyingRuleTest.java` | 2/3 | CP17/CP20/CP21 | 新建：倒地全链 + 批3 扩 2 行 | +207/−1 |
| `systems/TimedTriggerSystemTest.java` | 4 | CP23 | 新建：定时触发/战吼/盾墙调度 | +200 |
| `systems/BattleSystemTest.java` | 0/2/4 | CP1/CP7/CP12~19/CP22~27 | 扩：确定性回放（deterministicReplay:525）、RNG 审计（rngConsumptionEqualsAttackRolls:547）、CRIT_RHYTHM 对拍（critRhythmOverwriteKeepsRngOrder:962）、开局/战吼/盾墙/三连装填 | +370/−5 |
| `systems/DamagePipelineTest.java` | 0/2/3/4 | CP20/CP21/CP25/CP26 | 扩：受击窗口全序、旧疤、收割专项（35% 严格边界/技能直伤同吃/同乘法链）、附带挂点 | +414/−27 |
| `systems/ProjectileSystemTest.java` | 0/2/4 | CP18/CP24/CP27 | 扩：亡语源弹、盾墙拦截、critBonusPp 透传 | +221/−2 |
| `systems/StatPipelineTest.java` | 0/2 | CP1/CP12 | 扩：**enemyScaleSkipsCritKeys:168（Q1 裁决锁定测试）**、计数通道 | +105/−4 |
| `systems/SkillExecutorTest.java` | 0/4 | CP8/CP22 | 扩：三新词、开局回能 | +153/−3 |
| `systems/SynergySystemTest.java` | 0/4 | CP4/CP7/CP22 | 扩：8 羁绊档位、D10 截断、opening 分流 | +84/−4 |
| `config/JsonLoaderTest.java` | 0 | CP3/CP5 | 扩：三通道解析 | +107/−58 |
| `config/JsonLoaderValidationTest.java` | 0 | CP3 | 扩：逐词校验 | +160/−3 |
| `systems/StatusSystemTest.java` | 0 | CP8 | 扩：BURN 心跳/GAIN_ENERGY（期望值修正 40，见 §2 批0） | +29 |
| `systems/WaveGeneratorTest.java` | 0 | CP6 | 扩：enemyPool 重排适配 | +15/−12 |
| `config/GameBalanceTest.java` | 0 | CP1/CP7 | 扩：常量断言 | +15/−2 |
| `systems/support/BattleTestFixtures.java` | 0 | CP1 | 夹具适配 | +6/−4 |
| 其余 23 个 `*Test.java`（entities/input/render/save/screens/systems 各 1~2 行） | 0 | CP1 | `BaseStats` 构造器扩暴击两参的夹具签名适配（各 +1/−1） | 各 +1/−1 |

---

## 2. 分节说明（按批 0~5）

### 批0（CP1~CP8，`9a478f3`）——数据落地与词表基建

- **做了什么**：四件数据全量重写（24 子/13 技能/8 羁绊替换制/scenes 含 enemyPool 雏形）；暴击属性化（CP1）；机制三通道 + 亡语/不死/条件词表与解析（CP2/CP3）；D10 敌方档位截断（CP7）；放逐原语 + BANISH/GAIN_ENERGY/KNOCKBACK 薄执行 + BURN 心跳（CP8，K13「schema 先行、wiring 分批」）。文件：数据 4 改 + 主代码 20 改 6 新 + 测试 8 新 ~31 改。
- **与计划的偏差及原因**：
  1. **Q1 裁决（计划 CP1 缺陷）**：计划暴击两键漏写 scale 豁免，字面实现致 r25 敌方暴击 20%→68%、暴伤 ×5.1（敌方 3 星 scale 按暴击两键放大）。按 v5 规格三处证据裁决豁免——`StatPipeline.deriveBaseline` 对暴击两键 effectiveScale=1；锁定测试 `StatPipelineTest.enemyScaleSkipsCritKeys`。
  2. `EffectData` 构造器 10 参漏 `op`，实施时补齐（编译必需）。
  3. `OPENING_ENERGY` 批0 走 mechanics 通道（计划留待 CP22），CP22 时再并入 openingEffects——保证批0 可构建且行为等价（解析先行）。
  4. 测试类归属调整 3 条（按包职责就近落位）；`GAIN_ENERGY` 期望值修正 40（计划草案数与数据终稿不一致，以数据为准）。
- **验证记录**：`gradlew :core:test` 退出码 0；TEST-*.xml 聚合 **822/0/0/0**（基线 791，净增 31）。

### 批1（CP9~CP11，`b7ca513` + `31930d7`）——渲染图形/颜色

- **做了什么**：CP9 `UnitShapeKit`/`StarColors` + 2 测试类；CP10 两段提交——a 段 pilot 白名单仅 `unit_warrior_01` 走形状绘制，b 段铺开 24 子；CP11 星级 tint 接线（UnitView/BattleRenderer/ShopBar）。
- **与计划的偏差及原因**：① 铺开提交 b 采用**白名单整段移除**（计划原文「置空集合」），避免死代码与误导性开关；② `findUnit` 重构（顺带修复 ghost 星级残留）；③ 色距阈值取 0.3（StarColorsTest 断言星级色与敌我框色 RGB 欧氏距离 >0.3，为「可辨」给出可测下界）。
- **验证记录**：两道门禁均退出码 0、XML 聚合 **828/0/0/0**（a 段门禁后 +6，b 段门禁持平）。**lwjgl3:run 人工冒烟（CP10 四条 + CP11 三条）未见执行留痕**（批1 会话未回报），已并入 §7 待人工清单第①组，不阻塞后续批次（纯渲染层，headless 全绿）。

### 批2（CP12~CP19，`b00b91b`）——死亡簇

- **做了什么**：CombatCounters 计数通道（CP12）；lastDamagerId 击杀归因 + UNIT_DIED(killer)（CP13）；两遍式级联 DeathSystem（CP14）；亡语执行器 4 kind + 双响（CP15）；阵亡名册 + 大公点算（CP16）；undying 倒地全链——五项字段 + 四过滤点 + 时钟冻结 + 1.0s 复活（CP17）；亡语源弹道真伤路由 + 在途消散重选（CP18）；兽潮 + 连环清账 + 表现事件消费（CP19）。内部顺序遵守计划：CP12→CP13→**CP17 先行**→CP14→CP15→CP16→CP18→CP19。
- **与计划的偏差及原因**：① **CP15 双响弹数裁决**：计划内部矛盾（代码字面 3 弹 vs 三处语义 2 弹），按 **2 弹**落地（#1 HIGHEST_ATK、#2 LOWEST_HP）；主人若改判 3 弹，改点仅 `DeathrattleExecutor.executeOnce` VOLLEY 分支一处。② `DeathSystem` 构造器去未用参。③ 复活判定用 TIME_EPSILON（沿 StatusSystem 惯例）。④ `CombatEvent` 扩 11 字段、保旧 10 参构造委托（调用面零破坏）。⑤ UNIT_DOWNED 沿 unitDied 工厂形态。⑥ ALL_ALLIES 盾可挂倒地单位（盾为属性非行动）。
- **验证记录**：退出码 0；XML 聚合 **865/0/0/0**（+37）。

### 批3（CP20~CP21，`d305802`）——受击簇

- **做了什么**：DamagePipeline 受击窗口全序（免伤/收割/反弹/穿甲参数/事件序，K9）；旧疤 SCAR 受击计数接 CombatCounters（CP21）。
- **与计划的偏差及原因**：无实质偏差；穿甲在 SkillExecutor 的接线因计划 CP 编号笔误（"CP31"不存在）顺延至批4 完成，登记于批4。
- **验证记录**：退出码 0；XML 聚合 **874/0/0/0**（+9）。

### 批4（CP22~CP27，`3439bea`）——开局/条件/附带

- **做了什么**：开局 TRAITS 谓词 + 群星响应（OPENING_ENERGY 并入 openingEffects、AFTER_CAST_ENERGY 施法尾钩，CP22）；TimedTriggerSystem + 卫队战吼范围嘲讽（CP23，含 GameBalance.WARCRY_TAUNT_RADIUS）；盾墙拦截（CP24）；普攻附带 BURN/SLOW（CP25，挂点收敛于 applyDirectHit 单点）；收割 EXECUTE 专项测试（CP26，35% 严格边界/技能直伤同吃/×1.4×0.85 同乘法链/敌我对称）；三连装填 CRIT_RHYTHM（CP27，计数/必暴覆写/×2.0/Projectile.critBonusPp 冻结透传）。
- **与计划的偏差及原因**：① CP"31"笔误的穿甲接线落本批（SkillExecutor DAMAGE 分支 armorPenRatio=SPELL_PEN value/100）；② 盾墙拦截**排除 deathrattleBorn 弹**（防空弦绝响被盾吞掉，符合「只拦普攻弹」语义）；③ 普攻附带挂点单点化于 `applyDirectHit`（近战=有效攻、弹道=发射快照由入参天然保证，避免双挂点漂移）；④ `bindStatusSystem` 改组装期绑定（解 DeathSystem↔StatusSystem 构造环）；⑤ TimedTrigger 调度点取 beginTick 后（开战 0 tick 不触发、首个 step 消费，确定性 tick 对齐）。
- **验证记录**：退出码 0；XML 聚合 **896/0/0/0**（+22）。**确定性对拍专项通过**：见 §6 证据链。

### 批5（CP28，本报告）——报告 + 全量自测收口

- **做了什么**：按 CP28 模板生成本报告；全量回归复核；`gradlew build` 全链构建。
- **与计划的偏差及原因**：报告路径按 CP28 明文规定落 `docs/spec_plan/2026-11-21_units_synergies_v5_impl_report.md`（派单描述中的默认路径 `docs/planning/implementation_report.md` 让位于 CP28 专项规定）；按派单口径**不提交 git**，与 docs/planning 其余文档同为 untracked，提交时机由主人决定。
- **验证记录**：`./gradlew :core:test` 退出码 0，XML 聚合 **896 tests / 0 failures / 0 errors / 0 skipped**（93 个 TEST-*.xml）；`./gradlew build` 退出码 **0**。

---

## 3. 图形与颜色映射表及理由

### 3.1 三轴映射规则（`UnitShapeKit` / `StarColors` / K14）

| 轴 | 载体 | 映射 |
|---|---|---|
| 种族→图形族 | `UnitShapeKit.familyOf` | 兽人→三角 TRIANGLE、野兽→菱形 DIAMOND、亡灵→五边 PENTAGON、巨人→六边 HEXAGON、未登记风味种族（植物/构造/独眼等 Boss 种族）→五角星 STAR 兜底 |
| 职业→族内变体 | `UnitShapeKit.variantMarkRows` | 战士→顶带（行 0~1）、法师→核点（行 5~6）、刺客→斜切（行 3~9）、游侠→空心（减行宽画法，无附加实心带）、Boss/未登记→PLAIN 无标记 |
| 星级→颜色 | `StarColors.of` | 1★ 骨白 (0.82,0.82,0.78) common、2★ 金 (0.98,0.78,0.22) rare、3★ 紫 (0.72,0.40,0.96) epic；越界钳制到 1/3 星 |

### 3.2 二十四子逐行表（units.json 实读，费阶 10/9/5、行列各 6 已由 UnitsMatrixTest 锁定）

| 棋子 | id | 种族→图形族 | 职业→变体 | 费 |
|---|---|---|---|---|
| 兽人战士 | unit_warrior_01 | 兽人→三角 | 战士→顶带 | 1 |
| 野猪骑士 | unit_boar_rider | 兽人→三角 | 战士→顶带 | 1 |
| 焰痕祭司 | unit_mage_apprentice | 兽人→三角 | 法师→核点 | 1 |
| 王家军需官 | unit_legend_quartermaster | 兽人→三角 | 法师→核点 | 3 |
| 暗影之刃 | unit_shadow_blade | 兽人→三角 | 刺客→斜切 | 2 |
| 掷矛猎头者 | unit_spear_headhunter | 兽人→三角 | 游侠→空心 | 1 |
| 狼崽 | unit_wolf_pup | 野兽→菱形 | 刺客→斜切 | 1 |
| 伏击豹 | unit_ambush_panther | 野兽→菱形 | 刺客→斜切 | 1 |
| 兽猎手 | unit_beast_archer | 野兽→菱形 | 游侠→空心 | 2 |
| 丛林游侠 | unit_ranger_01 | 野兽→菱形 | 游侠→空心 | 2 |
| 荒野德鲁伊 | unit_fairy_druid | 野兽→菱形 | 法师→核点 | 2 |
| 荆棘圣鹿 | unit_legend_thornhart | 野兽→菱形 | 游侠→空心 | 3 |
| 骸骨士兵 | unit_skeleton_soldier | 亡灵→五边 | 战士→顶带 | 1 |
| 收账人 | unit_debt_collector | 亡灵→五边 | 刺客→斜切 | 1 |
| 怨灵 | unit_wraith | 亡灵→五边 | 法师→核点 | 2 |
| 守灯人 | unit_lamplighter | 亡灵→五边 | 法师→核点 | 2 |
| 空弦射手 | unit_bowstring_archer | 亡灵→五边 | 游侠→空心 | 2 |
| 死亡骑士 | unit_death_knight | 亡灵→五边 | 战士→顶带 | 3 |
| 小雪怪 | unit_frost_imp | 巨人→六边 | 刺客→斜切 | 1 |
| 碎石仔 | unit_rubble_slinger | 巨人→六边 | 游侠→空心 | 1 |
| 霜巨人 | unit_frost_giant | 巨人→六边 | 战士→顶带 | 2 |
| 霜峰弃子 | unit_assassin_01 | 巨人→六边 | 刺客→斜切 | 2 |
| 冰霜巨人 | unit_glacial_giant | 巨人→六边 | 战士→顶带 | 3 |
| 冰川颂者 | unit_legend_warsong_singer | 巨人→六边 | 法师→核点 | 3 |

Boss 9 条（植物/构造/独眼/亡灵/巨人种族、Boss 职业）：风味种族走 STAR 兜底 + PLAIN 无标记；亡灵/巨人 Boss 沿用族图形但职业为 PLAIN——与棋子形成「同族不同标记」的层级读法。

### 3.3 理由与自查结论

- **三轴正交**：敌我=SideColors 框（蓝/红，不变）、身份=图形（种族+职业两级）、级别=颜色（星级 tint）——三轴互不挤占，任一轴变化不牺牲另两轴读法。
- **颜色避让**：骨白/金/紫与血条红绿、能量黄全部错开；2★ 金较能量黄更饱和且载体为 24px 本体，可辨。可辨性有测试下界：星级三色互异、且与 SideColors 蓝/红的 RGB 欧氏距离 >0.3（`StarColorsTest:37-42`）。
- **乘法 tint 下的标记可辨**：白体（0.92 亮度，保留帧亮度脉冲余地）×星级色=星级色；黑描边与深色职业标记乘法后保持暗色，不需额外通道。
- **微调记录**：行表（halfWidths 12 行）执行期**未做**像素微调——`UnitShapeKit` 注释已预留「按冒烟观感微调，行数不变」的表达层自由项，待 §7 第①组人工冒烟后定；若调，仅改 `halfWidths` 数组字面量。

---

## 4. 未实施项（与计划 §8.4 对账）

| 项 | 内容 | 未做原因 | 建议后续载体 | 状态 |
|---|---|---|---|---|
| §五.1 敌方波次内容化 | 完整主题波次表（侦察可读、反制可备） | 主人派单口径「仅实施设计依赖」；批0 scenes.json enemyPool weight/minRound 重排已承载数据雏形 | scenes.json 扩 `waves` 结构 + WaveGenerator 波次表驱动（独立小计划） | **未实施**（按计划登记） |
| §五.6 叠加制备选 | 低高档并存（P0-1 方案 B） | D6 已裁 2/4/6 替换制；并存需改 GDD §5.1 + resolve 档位合并语义 | GDD §5.1 + `SynergySystem.resolve` 合并语义 + SynergyPanel 展示 | **未实施**（按计划登记） |
| §五.4 双上阵计数 | 同名双上阵各计 1 / 备战 3 合 1 折叠 | **代码现状已一致，零改动**（`SynergySystem.resolve` 对上场模板逐个计 1；`ShopSystem.mergeCascade` 按 bench+deployed 全名单折叠）——口径注记，非未实施 | 无需载体；如 GDD 改写需引用，归 gdd-writer | 口径注记（§8.3） |
| GDD/battle/render 设计文档改写 | v5 全链文档同步 | 计划 §1 明确不含，归 gdd-writer | gdd-writer 任务线 | 范围外 |

---

## 5. 遗留风险与观察点

### 5.1 性能与体验观察点（计划 §8.1，均不阻塞、均待 §7 人工冒烟/实测取证）

| # | 项 | 现状判定 | 调节杆 |
|---|---|---|---|
| O1 | 纯 6 巨人 37~74s / 纯 6 战士超时高危 | 未实测（§7 第③组可顺带观察时长） | 混编正解；巨人 hp+300 或免伤 12%（改 synergies.json 即回滚） |
| O2 | 点算空转率 >50% | 未实测（需多局统计 DEATH_AUDIT 触发率） | Boss 轮门槛 25%→30%（schema 单值，启用需按轮次分档再议） |
| O3 | 双响 0.3s 演出间隔 | 按 C5 同 tick 两发落地，无间隔 | 表现层如需错峰，走渲染侧延迟播放入场（不改逻辑） |
| O4 | 亡灵镜像局互喂点算 | 未实测（§7 第②组可构造镜像观察） | D10 截断已压敌方低轮档位；实测难受再调敌方池亡灵配比（纯数据） |

### 5.2 实施口径开放点（计划 §8.2 R1~R8：均按默认口径**已落地**，主人可随时改判，改点如下）

| # | 已落地口径 | 改判改点 |
|---|---|---|
| R1 | 余震/免伤**作用于技能直伤**（仅豁免 DOT/真伤） | 改「仅普攻」= executeAmpOf/applyReflect 各加 `basicAttack` 判断一行 |
| R2 | 暴伤 +50 **仅必暴刀** ×2.0 | 档位全体暴击 ×2.0 = value2 移入 stat critDamage ADD 50 一行（火力大涨，需复锚） |
| R3 | 末班岗/守夜盾**无时限** | 时限盾 = SHIELD duration 词表扩（parseSkillEffect 放行 + StatusSystem 时长语义），独立小任务 |
| R4 | 连环清账回满能**不就地施放**（下一行动 tick） | 就地 = 复用 tryCastInline 深度保护，1 行但有重入面 |
| R5 | 兽人(6) 灼烧 3s | 任意值 = synergies.json duration 一处 |
| R6 | 机制词**不吃** synergyAmp | 若放大需逐词定义放大语义（DEATH_TAX 与 REFLECT 不同构） |
| R7 | startBattle 旧重载委托 round=25（cap=6 等效不截断） | 存量测试语义不变；若造 >6 同质敌方阵容需按 cap 口径复核期望 |
| R8 | 盾墙「最近战士」以**目标**为参照（曼哈顿、平局 id 小） | 不建议改弹道当前位置参照（每 tick 变化，确定性更差） |

### 5.3 执行期裁决与测试锁定（供改判时定位）

- **Q1 敌方暴击 scale 豁免**（批0，计划 CP1 缺陷）：`StatPipeline.deriveBaseline` 对暴击两键 effectiveScale=1；锁定测试 `StatPipelineTest.enemyScaleSkipsCritKeys:168`。r25 敌方暴击回落 20% 基线、暴伤 ×1.5。
- **CP15 双响 2 弹**（批2，计划内部矛盾）：#1 HIGHEST_ATK、#2 LOWEST_HP；改 3 弹仅改 `DeathrattleExecutor.executeOnce` VOLLEY 分支一处。

---

## 6. RNG 消耗点登记复核

- **全计划唯一随机消耗点 = 普攻暴击 roll**，现位于 `BattleSystem.java:239-240`（计划锚点 :219，批内前置插入致行号漂移，内容一致——WARNING 级自行适配，已核对）。CP27 三连装填**覆写复用该消耗点**：roll 照常先行消耗、覆写仅改结果，零新增消耗点。
- **对拍证据链（四测试，本次收口已核实落点）**：
  1. `BattleSystemTest.deterministicReplay`（:525）——同 seed 双 run 事件流逐位一致；
  2. `RunFlowSystemTest.restartReplaysIdenticalEventStreams`（:854）——重开重放一致；
  3. `BattleSystemTest.rngConsumptionEqualsAttackRolls`（:547）——RNG 消耗数 == 出手数（无隐藏消耗）；
  4. `BattleSystemTest.critRhythmOverwriteKeepsRngOrder`（:962，批4 专项）——同 seed 有/无 CRIT_RHYTHM 两场 ATTACK_LAUNCHED 逐位 equals、`rng.getConsumedCount()` 相等且等于出手数。
- **architecture §六补注建议**（归 gdd-writer/文档线）：登记「暴击 roll（BattleSystem 普攻出手）仍为唯一消耗点；CRIT_RHYTHM 覆写不增耗（对拍测试 ④ 证明）」。

---

## 7. 手动冒烟清单与结果

启动：`./gradlew :lwjgl3:run`。**四组均待主人人工执行**（批0~4 各会话未回报 lwjgl3:run 留痕，本报告如实标注，不虚构结果）；每组对应计划验证门的观察项如下。

| 组 | 阵容/场景 | 观察项（对应计划冒烟门禁） | 结果 |
|---|---|---|---|
| ① 渲染 | 混编上阵 + 敌阵对照 | 三角族 pilot（兽人战士=三角+顶带+骨白 1★）；各族形状并排（菱形/五边/六边）；同族四职业变体并排可辨；1/2/3 星白金紫三色并排可辨；敌阵红框 + 骨白群像、敌我同子仅框色不同；walk/attack/death 帧差异仍在（CP10 四条 + CP11 三条） | **待人工** |
| ② 亡灵死亡经济 | 墓穴 6 亡灵 vs r12 敌阵一场 | 级联亡语紫飘字真伤；DEATH_AUDIT「除名」灰字放逐；死亡骑士倒地→复活满能放大；空弦死后弹转向（在途重选）；顺带观察 O4 镜像互喂 | **待人工** |
| ③ 受击 | 4~6 巨人 / 刺客打残血 | reflect 紫飘字反弹；免伤与旧疤成长（受击越多越硬）；<35% 斩杀伤害跳变；顺带观察 O1 纯巨人时长 | **待人工** |
| ④ 开局 | 6 法师 / 4~6 战士 / 6 游侠 | 法师开局半能速大（OPENING_ENERGY）；战吼 6s 敌方集体转向旗手 3s；盾墙拦弹无飘字（PROJECTILE_BLOCKED 不产伤害字）；游侠第 3 刀橙字 ×2（必暴刀） | **待人工** |
| 附加 | 旧机制回归 | 商店购买/合成/开战/宝箱 打 3 轮（批0 验证门人工项，同样未见留痕，建议与①合并执行） | **待人工** |

---

## 收口判定

- CP1~CP28 全部落位（批0~4 六提交 + 批5 本报告）；`./gradlew :core:test` XML 聚合 **896/0/0/0**（基线 791 → 净增 105，只增不减红线满足）；`./gradlew build` 退出码 **0**。
- 计划验证门的自动化部分全部通过；人工冒烟部分统一收敛至 §7 待主人执行。
- 本报告按派单口径不提交 git（untracked），提交切分由主人决定。
