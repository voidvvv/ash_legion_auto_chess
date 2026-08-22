# Phase 5 商店 / 经济 / 宝箱数据流图

> 经济域全链：商店槽位状态（ShopSystem）× 纯宝箱经济收支（GDD §三）× 宝箱三选一（Q2 裁决 A：文档定最小可玩规则）× 怜悯保底。
> 浏览器查看版：`phase5_economy_dataflow.html`（双击打开）
> 依据：`gdd_idea_0.0.0.1.md` §三（纯宝箱经济）/§5.2（掉落权重骨架）；`architecture_design.md` §四/§5.3（轮开始事件）/§六（RNG 消耗点）；Q1/Q2/Q6 裁决（2026-08-22）

```mermaid
flowchart TB
    subgraph SOURCES["收入来源（GDD §3.2：纯宝箱经济——无工资/利息/连胜）"]
        START["起始金币 10<br/>START_GOLD（GameBalance.java:50）"]
        CHEST["宝箱三选一（主要来源）<br/>槽1 金币 = chestGold(轮, boss) = 3+floor(轮/3)，21 轮封顶 10，Boss ×2"]
        SELL["卖棋子<br/>返还 spend 累计花费 100%（Q6：3 合 1 合并累加）"]
        MERCY["怜悯金币<br/>同轮第 3 败起每败 +1 · 每轮 ≤3 · 零棋子战败不计"]
    end

    subgraph SINKS["支出（GDD §3.3）"]
        BUY["BuyUnit(slot)<br/>按费阶 1/2/3 金"]
        REFRESH["RefreshShop<br/>2 金整批替换 5 槽"]
        EXPP["BuyExp<br/>4 金 = 4 经验"]
    end

    subgraph SHOP["ShopSystem（systems/ 新建；RunContext.shop 持有）"]
        SLOTS["slots: UnitData[5]<br/>买走即置 null，刷新前保持空槽"]
        REROLL["reroll(round, data, rng)<br/>每槽：费阶 roll（1 RNG）+ 池内抽取（1 RNG）<br/>整批固定消耗 10 RNG/次"]
        TIER["费阶概率 = GameBalance.shopTierProbabilities(round)<br/>锚点插值（GameBalance.java:123-129）×1000 转 int 权重"]
    end

    subgraph CHESTS["ChestSystem（systems/ 新建）+ ChestOffer/ChestOption（entities/ 新建）"]
        ROLLC["roll(round, data, rng)<br/>仅胜局进 RESULT 时执行一次<br/>固定消耗 2 RNG：稀有度 1 + 池内抽取 1"]
        OPT1["槽1 常驻金币（GDD §3.2 公式，零 RNG）"]
        OPT2["槽2 经验书 +4（CHEST_EXP_BOOK_GAIN，待调，零 RNG）"]
        OPT3["槽3 装备（Q1=A 全链）<br/>稀有度权重 白70/成25/传5；Boss 箱 0/80/20（必含 ≥1 成装）<br/>池 = equipments.json 全集按稀有度过滤"]
        PICK["PickChest(option)<br/>architecture §4.1：内容进 RESULT 时已 roll 好，本命令零 RNG"]
    end

    subgraph ROUND["轮开始事件（architecture §5.3；RunFlowSystem.advanceAfterVictory / StartRun）"]
        NEWR["round+1 → beginRound（敌阵重生成，RNG=杂兵数）<br/>→ ShopSystem.reroll 免费刷新（RNG=10）<br/>→ 怜悯计数清零（新轮重计）"]
        RETRY["判负重试（同轮）：敌阵不变 · 商店不变 · 怜悯计数+1<br/>（零 RNG 消耗）"]
    end

    PLAYER["Player（entities/Player.java）<br/>gold / level / currentExp<br/>addGold / addExp（Phase 3 已就位）"]
    RS["RunState（entities/RunState.java）<br/>mercyLossCount（:24 已建）+ mercyGoldThisRound（本期新增）<br/>pendingChest（本期新增）"]

    START --> PLAYER
    CHEST --> ROLLC
    ROLLC --> OPT1 & OPT2 & OPT3
    OPT1 & OPT2 & OPT3 --> PICK
    PICK --> PLAYER
    SELL --> PLAYER
    MERCY --> PLAYER
    BUY & REFRESH & EXPP --> PLAYER
    BUY --> SLOTS
    REFRESH --> REROLL --> SLOTS
    ROUND --> SHOP
    RS --> MERCY
```

## RNG 消耗点口径（architecture §六第 2/3 点本期落地，清单不新增类目）

| 消耗点 | 每次消耗 | 触发时机 | 出处 |
|--------|---------|---------|------|
| 敌阵生成 | = 杂兵数 | 轮开始（beginRound） | Phase 2 既有 |
| 商店刷新 | 固定 10（2/槽 × 5） | StartRun / 新轮进入（免费）+ RefreshShop（2 金） | 本期落地 |
| 宝箱 roll | 固定 2（稀有度 1 + 池内抽取 1）；败局 0 | 胜局进 RESULT 一次性 | 本期落地 |
| 暴击判定 | 1/次普攻 | 战斗内固定行动序 | Phase 3 既有 |

> 固定消耗规则：槽池为空也照常消耗（BuyUnit 槽位无效与池内容无关），保证同 seed 同数据集消耗序确定；内容版本变更会改变具体消耗结果但不破坏确定性本身。

## 边界与约束

| 约束 | 出处 |
|------|------|
| 金币唯一货币，无生命值 / 无利息 | GDD §三 / §10.2 注（决策 2026-08-19） |
| 商店免费刷新是系统行为不入命令队列；轮内主动刷新才走 RefreshShop | GDD §3.4 / architecture §4.1 |
| 备战席满 9 禁买；例外——购买即完成 3 合 1（名单已有同名同星 ×2）允许 | GDD §3.4 / architecture §5.2 校验要点 |
| 查价不信任载荷：BuyUnit 只带 slot，价格由 handler 现查模板 | input §6.3 |
| 零棋子战败：无宝箱收益、不计入怜悯连败 | GDD §2.2/§3.2 |
| 演示名单（grantDemoRoster）删除：起始 10 金商店自购（Q6 裁决 A） | RunFlowSystem.java:140-153（旧代码） |
