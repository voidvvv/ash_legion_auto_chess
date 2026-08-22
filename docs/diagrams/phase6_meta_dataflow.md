# Phase 6 局外成长数据流图（档案域 ↔ 局内模拟域）

> Phase 6 = GDD §十二路线图第 6 阶段：英雄熟练度、场景解锁、存档系统。
> 浏览器查看版：`phase6_meta_dataflow.html`（双击打开）
> 依据：`gdd_idea_0.0.0.1.md` §八（局外成长）/§7.4（场景差异化）；`architecture_design.md` §一（三态域）/§三（档案层方案 A）/§八（持久化双轨）

```mermaid
flowchart TB
    subgraph STATIC["静态数据域（加载一次终身只读，JsonLoader fail-fast）"]
        HEROES["heroes.json（本期新增，3 英雄）<br/>id / name / desc / passive(type+value+synergyIds) / legendaryUnitId"]
        SCENES["scenes.json（本期扩展 shopUnlocks + 增至 3 场景）<br/>unlockAfter 前置链 + enemyPool + bosses + shopUnlocks"]
        UNITS["units.json（本期增补：场景棋子 6 + 英雄传奇 3 + 新 Boss 6）"]
        SYNERGIES["synergies.json（本期增补：亡灵 / 巨人，工作值待调）"]
    end

    subgraph PROFILE["档案域（方案 A：轻量语义调用，无命令队列）"]
        PF[("save/profile.json<br/>version / heroProgress{exp,level} / completedScenes")]
        PS["ProfileService（纯函数，零 Gdx）<br/>settle()：熟练度升级 + 通关场景登记 + 新解锁派生<br/>runModifiers()：英雄被动 × 熟练度等级 × 场景解锁 → RunModifiers<br/>unlockedSceneIds()：unlockAfter ∈ completedScenes 派生（不存解锁位，防漂移）"]
        MS["MetaService（Screen 唯一门面）<br/>settleRun / resolveRunModifiers / isSceneUnlocked / 快照读写"]
    end

    subgraph RUN["局内模拟域（确定性，命令流不变）"]
        RS["RunState（本期扩展）<br/>+ heroId（StartRun 参数，回放第 0 条记录）<br/>+ modifiers（RunModifiers，装配期算好即冻结）"]
        SHOP["ShopSystem.reroll / RefreshShop<br/>3 费概率 +5pp（Lv.2，基础 p3&gt;0 轮次）/ 刷新费 -1（Lv.5）<br/>商店池门控：场景 shopUnlocks + 本英雄传奇（Lv.3）"]
        BATTLE["BattleSystem.startBattle<br/>奥兰多「战歌」：全队 energyGainRate +15（修正源列表第 3 源）<br/>薇拉「荆语」：野兽/游侠羁绊效果 ×1.25（SynergySystem 增幅重载）"]
        FLOW["RunFlowSystem.endRun<br/>masteryAwarded = MasteryCalculator.settle(cause, round)<br/>COMPLETED = 60 + 轮数×3 / ABANDONED = 轮数×3"]
    end

    SETUP["RunSetupScreen（Screen 装配点）<br/>英雄 3 卡（含熟练度 Lv/经验）+ 场景 3 卡（未解锁灰置）<br/>开始 → seed + sceneId + heroId"]
    END["RUN_END（BattleScreen 观察首批触发，Screen 点火器原则）<br/>MetaService.settleRun → Settlement{expGained, levelFrom→To, 新解锁场景}<br/>ProfileStore.save → RunEndPanel 结算行展示 + 清快照"]

    HEROES --> PS
    SCENES --> PS
    UNITS --> SHOP
    SYNERGIES --> BATTLE
    PF --> PS
    PS --> MS
    SETUP -->|"resolveRunModifiers(heroId)"| MS
    MS -->|"RunModifiers（不可变值对象）"| RS
    RS --> SHOP
    RS --> BATTLE
    RS --> FLOW
    FLOW -->|"masteryAwarded（纯模拟态）"| END
    END -->|"settle + persist"| PF
    END -.->|"新解锁场景 → 下局 RunSetup 可选"| SETUP
```

## 关键边界（architecture §一 三态域）

| 边界事件 | 归宿 | 说明 |
|----|------|------|
| RunSetup「开始远征」 | 域边界事件 | UI 态结算为 `StartRun(seed, sceneId, heroId)` 参数（heroId 扩展位本期启用） |
| RUN_END 首帧观察 | 档案语义调用 | BattleScreen 调 `MetaService.settleRun`；模拟域（RunFlowSystem）不触碰档案 IO，回放性保持 |
| 进入 SHOPPING / pause / hide | 快照轨写入 | 存档点仅备战阶段（决策 2026-08-20）；RUN_END 删档 |
| 商店 reroll / 开战派生 | 确定性系统反应 | RunModifiers 在装配期冻结进 RunState，同 seed + 同 heroId + 同命令流 → 同结果 |

## 熟练度等级解锁表（GDD §8.1 + 本期工作值）

| 等级 | 解锁内容 | 代码承载 |
|------|----------|----------|
| Lv.1 | 初始金币 +2（全英雄基础权益） | `RunModifiers.startGoldBonus` |
| Lv.2 | 商店 3 费概率 +5pp（仅该英雄） | `RunModifiers.rareShopBonusPp`（ShopSystem 权重调整，RNG 消耗不变） |
| Lv.3 | 解锁该英雄专属传奇棋子（进商店池） | `RunModifiers.legendaryUnitId`（池门控） |
| Lv.4 | 开局金币额外 +3（**工作值待调**，GDD「更多待设计」） | `RunModifiers.startGoldBonus` 叠加 |
| Lv.5 | 商店刷新费 -1，最低实付 1 金（**工作值待调**） | `RunModifiers.refreshCostDiscount` |
