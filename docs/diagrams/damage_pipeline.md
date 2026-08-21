# 伤害管线序列图（Phase 3，唯一管线）

> 普攻与技能直伤共用的结算序列：发射冻结 → 载体投送 → 命中结算（护甲 → 盾 → HP → 回能 → 吸血）。
> 浏览器查看版：`damage_pipeline.html`（双击打开）
> 依据：`battle_design.md` §5.2 / §5.3 / §六；Q2 裁决（LINE 本期不做）；Q3 裁决（吸血仅普攻直伤触发）

```mermaid
sequenceDiagram
    participant U as 攻击者 / 施放者
    participant L as 出手点（发射/施放）
    participant P as HOMING 锁定弹（6格/秒）
    participant D as DamagePipeline（唯一管线）
    participant T as 目标
    participant C as 就地施放回调（CastTrigger）

    U->>L: 普攻出手 / 能量满施放技能
    L->>L: 普攻 roll 暴击（rng.nextFloat < 20%，消耗序=发射序）<br/>冻结载荷：攻击力快照 + 暴击标志<br/>技能另冻：value × 星级缩放 × (1+skillPower/100)

    alt 近战普攻（range ≤ 1）/ MELEE_INSTANT 技能
        L->>D: 立即结算（AttackLaunched + Hit 同 tick）
    else 远程普攻 / HOMING 技能
        L->>P: 发射锁定弹（不可被阻挡，追踪目标当前格）
        L-->>U: AttackLaunched 事件（含载荷快照）
        P->>P: 每 tick 推进；目标已被清扫 → 消散<br/>（ProjectileFizzled，无伤害无能量）
        P->>D: 到达命中
    else LINE 载体技能
        L->>D: 本期不支持：直接抛错（Q2，测试夹具不构造 LINE）
    end

    D->>D: 伤害 = 攻击快照 × 倍率（普攻=1）× (暴击 ? ×1.5 : ×1)<br/>× 100 / (100 + 命中时目标有效护甲)
    D->>T: 有盾先扣盾（SHIELD 吸收条目，耗尽即移除）<br/>→ 扣 HP（溢出作废）→ Hit 事件（含 isCrit）

    D->>U: 攻击者 +10 能量 × 自身回能率（命中才得）<br/>普攻与技能直伤均触发（§5.2 管线内含回能步）
    D->>T: 受击者 +5 能量 × 自身回能率<br/>DOT / 落空 / 溢出不触发；被控制期回能冻结
    D->>U: 普攻限定（Q3）：吸血 = 护甲后实际伤害 × lifesteal/100<br/>不溢出 maxHp，Healed 事件<br/>技能直伤 / DOT / 落空不触发

    D-->>C: 任一方能量跨 100 → 就地施放回调<br/>（清零、无公共CD；控制期不触发；嵌套深度上限 16）
```

## 附：不走本管线的结算

| 结算类型 | 通路 |
|----------|------|
| DOT（BLEED/POISON）每跳 | `DamagePipeline.applyTrueDamage`：无视护甲（真伤可致死）、先扣盾、无回能无吸血 |
| 治疗 / 护盾量 | `applyHeal` / SHIELD 吸收条目：maxHp × value × 星级缩放 × (1+skillPower/100)，溢出作废 |
