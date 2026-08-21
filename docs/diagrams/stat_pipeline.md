# 属性派生管线图（Phase 3，两级）

> `UnitData.baseStats` → 第一级基准快照（开战一次算死）→ 第二级战斗期有效属性（状态变化时脏标记重算）。
> 浏览器查看版：`stat_pipeline.html`（双击打开）
> 依据：`battle_design.md` §八（8.1 基准 / 8.2 战斗期修正 / 8.3 全局常量）；Q4 裁决"修正源列表"（Phase 5 装备源零改插入）

```mermaid
flowchart TD
    subgraph L1["第一级 · 基准快照（startBattle 一次算死 → BattleUnit.baseStats，不可变）"]
        direction TB
        T["UnitData.baseStats 模板 9 键<br/>hp · attack · armor · attackSpeed · moveSpeed · range<br/>lifesteal · energyGainRate · skillPower（百分点整数）"]
        R["raw = 模板值 × starStatMultiplier( upgradeMultiplier , star ) × scale<br/>scale：玩家侧 = 1.0 / 敌方杂兵 = k（enemyScale）/ Boss = 1.0（烘焙不二次放大）"]
        F["基准 = ( raw + ΣADD ) × ( 1 + ΣPCT )<br/>先加后乘，合成顺序写死（battle §8.1）"]
        T --> R
        R --> F
        SRCS["修正源列表 List&lt;StatModifierSource&gt;（Q4）<br/>本期源①：羁绊 SynergySnapshot（实现 StatModifierSource）<br/>星级不占源——已并入上面的 raw 阶段<br/>Phase 5 源②：装备（追加进列表，结算器零改动）"]
        SRCS -.->|"按 StatKey 聚合的 ADD / PCT"| F
        F --> BASE["BattleStats（不可变 · 9 键浮点）"]
    end

    subgraph L2["第二级 · 战斗期有效属性（battle §8.2）"]
        direction TB
        ST["ActiveStatus 列表（可组合事实，同 type 不叠加）"]
        MAP["StatusType → StatKey 修正映射表（写死于 StatPipeline）<br/>ATK_UP / ATK_DOWN → attack · PCT ±value<br/>ASPD_UP → attackSpeed · PCT +value<br/>SLOW → moveSpeed · PCT −value<br/>STUN / BLEED / POISON / REGEN / SHIELD 非属性类"]
        RE["有效 = ( 基准 + Σ状态ADD ) × ( 1 + Σ状态PCT )"]
        BASE --> RE
        ST --> MAP
        MAP --> RE
        RE --> EFF["有效属性缓存（BattleUnit.effectiveStats，一切结算读它）<br/>状态集合变化 → 脏标记 → 下一 tick 重算<br/>maxHp 被降低 → currentHp 同步钳制<br/>百分比键（lifesteal / skillPower / energyGainRate）<br/>以百分点存储，结算处统一 ÷100"]
    end

    L1 --> L2
```

## 消费点

| 结算处 | 读取 |
|--------|------|
| 普攻出手 / 技能施放的攻击力基数 | 有效 attack（发射时冻结快照） |
| 命中时护甲公式 `100/(100+armor)` | 目标命中时有效 armor |
| 攻击间隔 = 1/attackSpeed、跳格冷却 = 1/moveSpeed | 有效值（攻速/移速类状态即时生效） |
| 技能幅度 ×(1+skillPower/100)、回能 ×energyGainRate/100、吸血 ×lifesteal/100 | 有效值（百分点 ÷100） |
