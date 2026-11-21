# 棋子与羁绊 v5 落地——机制接线架构图

> 实施计划配图：`../spec_plan/2026-11-21_units_synergies_v5.md` §5。
> 浏览器查看版：`units_synergies_v5_arch.html`（双击打开）
> 规格源：`../planning/units_synergies_design_v5.md`（v5 终稿，2026-11-21）

```mermaid
flowchart TD
    subgraph DATA["数据层（批0 全量落地）"]
        direction LR
        U["units.json<br/>24 子 + deathrattle/undying 字段"]
        S["skills.json<br/>+13 技能（BURN/GAIN_ENERGY/KNOCKBACK/BANISH/condition）"]
        Y["synergies.json<br/>8 条 × 2/4/6 替换制全量档<br/>stat | effect(SHIELD) | mechanic 三通道"]
        C["scenes.json<br/>shopUnlocks 6 亡灵/5 巨人 + enemyPool 主题重排"]
    end

    subgraph LOADER["JsonLoader（CP3）"]
        L1["词表扩展：StatusType.BURN ｜<br/>SkillEffectType.{GAIN_ENERGY,KNOCKBACK,BANISH} ｜<br/>SynergyMechanic 18 词 ｜ DeathrattleKind 4 词"]
    end

    subgraph RESOLVE["开战结算（BattleSystem.startBattle）"]
        R1["SynergySystem.resolve<br/>+ D10 敌方计数截断（r1-5:0 / r6-12:2 / r13-19:4 / r20+:6）"]
        R2["SynergySnapshot<br/>statModifiers → 基准快照（现有）<br/>openingEffects → 开局通道（CP22 扩 TRAITS 谓词）<br/>mechanics → 按 trait 过滤下发到 BattleUnit"]
    end

    subgraph UNIT["BattleUnit 运行态"]
        M["mechanics（不可变，按单位 trait 过滤）"]
        K["CombatCounters（战斗内计数器，CP12）<br/>遗物攻/血 ADD · 旧疤层数 · 普攻计数"]
        D["downed/undying 状态（CP17）<br/>lastDamagerId 击杀归因（CP13）"]
    end

    subgraph LOOP["主循环五阶段（battle §二）"]
        direction TB
        P1["① StatusSystem.tickStatuses<br/>BURN 心跳（CP8）· downed 时钟冻结（CP17）"]
        P2["② ProjectileSystem.advanceAll<br/>盾墙拦截普攻弹（CP24）· 亡语源弹道重选（CP18）"]
        P3["③ 行动链 act<br/>暴击 roll 读 CRIT_CHANCE（CP1）·<br/>三连装填覆写（CP27）· 嘲讽 override（CP23）·<br/>TimedTriggerSystem 卫队战吼 6s（CP23）"]
        P4["④ DeathSystem 两遍式级联清扫（CP14）<br/>标记+快照 → id 序结算：<br/>亡语×双响(C15) · 名册(C16) · 点算(C16)<br/>兽潮/清账(C19) · undying→倒地(C17)"]
        P5["⑤ 胜负判定（顺序不变：玩家从严在先）"]
        P1 --> P2 --> P3 --> P4 --> P5
    end

    subgraph DMG["DamagePipeline（CP20 受击窗口）"]
        D1["受击前：免伤 ×(1−15%)（DOT/真伤豁免）"]
        D2["公式：×100/(100+甲×(1−穿甲))"]
        D3["受击后：旧疤计数（普攻）· 余震反弹（真伤回攻击者）·<br/>普攻附带状态挂点（CP25）· 归因记录（CP13）"]
    end

    subgraph RENDER["渲染表达层（批1，素材到位前可替换）"]
        V1["UnitShapeKit：种族=图形族（三角/菱/五边/六边/星）<br/>职业=族内变体（顶带/核点/斜切/空心）"]
        V2["StarColors：星级=颜色（1 白灰 / 2 金 / 3 紫）<br/>与图形正交；敌我=SideColors 色框"]
        V3["PlaceholderArt.paintUnit 形状化 + UnitView/BattleRenderer/ShopBar tint"]
    end

    DATA --> LOADER --> RESOLVE
    R1 --> R2
    R2 --> M
    UNIT --> LOOP
    P3 -.直伤/弹道命中.-> DMG
    DMG -.HP≤0.-> P4
    P4 -.放逐（非死亡）.-> X["Banish 独立移除路径（CP8）<br/>不触发亡语/名册/点算/不死/击杀"]
    LOOP --> RENDER
```

## 关键口径速记

| 口径 | 出处 | 落点 CP |
|---|---|---|
| C2 两遍式级联 | v5 §3.3 | CP14 |
| C5 双响/凶手转移/绝响重选 | v5 §3.3 | CP15/CP18 |
| C7 倒地五项+四过滤点+时钟冻结 | v5 §3.3 | CP17 |
| C8 最近继承=可行动存活 | v5 §3.3 | CP16 |
| C10/C13 放逐=非死亡事件 | v5 §3.3 | CP8/CP16 |
| 计数通道不走 StatusType/开战快照 | v5 §4.1 | CP12 |
| 只拦普攻弹道（skill==null） | v5 §4.1 | CP24 |
| 必暴仍消耗 RNG 序、覆写结果 | v5 §一.8 | CP27 |
| D10 敌方档位截断 | v5 §4.1 | CP7 |
