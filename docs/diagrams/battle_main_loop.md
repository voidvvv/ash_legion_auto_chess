# 战斗主循环流程图（Phase 3）

> `BattleSystem.startBattle`（开战一次）与 `step`（60Hz 固定序五阶段）的全流程。
> 浏览器查看版：`battle_main_loop.html`（双击打开）
> 依据：`battle_design.md` §二（H 语义）/§五/§六；实现层口径见 `docs/spec_plan/2026-08-21_phase3_battle_engine.md` §三（#2 弹道相位插入、#3 互斥行动链、#15 判定序）

```mermaid
flowchart TD
    subgraph INIT["startBattle · 开战一次（tick 0，零 RNG 消耗）"]
        direction TB
        I1["玩家部署名单（Player 部署表 18 格）<br/>+ 敌方 WaveSpec 列表（Phase 2 产出）"]
        I2["IdIssuer 发号：玩家侧先（扫描序 y升 x升）<br/>→ 敌方后（WaveSpec 列表序 = 抽取序 + Boss 殿后）"]
        I3["SynergySystem 两侧双通道统计（race / class）<br/>→ StatPipeline 第一级基准快照（battle §八）"]
        I4["开局效果落地（如兽人6 开局 SHIELD 30% maxHp）<br/>初始 HP=maxHp · 能量=0 · 攻击/移动计时器就绪"]
        I5["按 id 序初始 findTarget（GDD §6.6 步骤1）"]
        I1 --> I2 --> I3 --> I4 --> I5
    end
    INIT --> STEP

    subgraph LOOP["BattleSystem.step · 每逻辑 tick（60Hz，固定序）"]
        direction TB
        P0["tick+1 · elapsed += 1/60<br/>每 120 tick 全局强制重评估（id 序，结果可能不变）"]
        P1["① 状态推进<br/>DOT / REGEN 1s 心跳（DOT 真伤可致死）<br/>时长递减 · 到期移除 + 属性脏标记"]
        P2["② 弹道推进<br/>目标已被清扫 → 消散（ProjectileFizzled）<br/>到达 → 按冻结载荷走唯一伤害管线"]
        P3["③ 逐单位行动（id 升序，跳过已清扫单位）<br/>被控制 → 跳过（能量冻结）<br/>目标失效（已被标记死亡）→ findTarget<br/>能量 ≥ 100 → 就地施放（不清攻击计时）<br/>射程内（曼哈顿 ≤ range）→ 计时到点出手<br/>&nbsp;&nbsp;&nbsp;&nbsp;近战即时结算 / 远程发射锁定弹（roll 暴击）<br/>否则 → 跳格冷却就绪走一步（贪婪步）"]
        P4["④ 死亡清扫<br/>统一标记 · 腾格 · 状态销毁 · UnitDied 事件<br/>目标指向亡者的单位立即重选（id 序）"]
        P5{"⑤ 胜负 / 超时判定"}
        P0 --> P1 --> P2 --> P3 --> P4 --> P5
        P5 -->|"未分胜负"| P0
    end

    P5 -->|"玩家全灭或同 tick 双灭（从严判负）"| LOSE(["BattleEnded · ENEMY_WIN"])
    P5 -->|"敌方全灭"| WIN(["BattleEnded · PLAYER_WIN"])
    P5 -->|"elapsed ≥ 60s（GDD §6.4）"| TIMEOUT(["BattleEnded · TIMEOUT（玩家判负）"])
```

## 关键语义（H 语义，battle §二）

- **伤害立即落地**：结算点当场扣 HP，后行动者看到的是最新血量（治疗能奶到本 tick 刚被打残的人）。
- **死亡延迟清算**：HP ≤ 0 但尚未清扫的单位在本 tick 仍是合法目标、仍可行动——互秒与"濒死反打"自然发生。
- **同 tick 冲突一律由固定遍历序 + id 决胜**：确定性回放的根基（发号序 = 玩家先、敌方后，见实现层口径 #16）。
- **事件即产出**：每个结算点记录纯数据 `CombatEvent`，逻辑层只产出、不消费。
