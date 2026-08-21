# 战斗状态数据流图（Phase 3）

> 开战输入 → `BattleSystem.startBattle` 派生 → `BattleState`（一场战斗的受控可变状态）→ `CombatEvent` 事件流（正式产出）。
> 浏览器查看版：`battle_dataflow.html`（双击打开）
> 依据：`architecture_design.md` §二（双实体 / 所有权地图 / 三层不可变）、§六（RNG 消耗点）；`battle_design.md` §二（事件流）；Q1 裁决（交付物按出生表 + 发号器接口占位）

```mermaid
flowchart LR
    subgraph IN["开战输入（全部显式注入，无全局单例）"]
        direction TB
        PL["Player 名单<br/>bench 备战席 9 格 + 部署表 18 格<br/>（本期增补字段，Q1）"]
        WV["List&lt;WaveSpec&gt;<br/>WaveGenerator 产出（Phase 2）<br/>模板引用 + star + scale + 格坐标"]
        GD["GameData<br/>units / skills / synergies / scenes<br/>（只读，加载期已交叉校验）"]
        RG["RandomGenerator<br/>seed 注入 · 消耗计数<br/>（战斗内唯一消耗点 = 暴击 roll）"]
        II["IdIssuer（接口占位）<br/>单一 int id 空间发号<br/>Phase 5 归 RunState / UnitRegistry"]
    end

    subgraph SB["BattleSystem.startBattle（tick 0）"]
        direction TB
        D1["派生 BattleUnit（两侧）<br/>身份不可变（id/模板/星级/阵营）<br/>+ 受控战斗态（HP/能量/坐标/状态）"]
        D2["SynergySystem 双通道统计<br/>race 与 class 各自计数<br/>→ SynergySnapshot × 两侧"]
        D3["StatPipeline 第一级基准快照<br/>raw = 模板 × 星级倍率 × scale<br/>→ ( raw + ΣADD ) × ( 1 + ΣPCT )"]
        D4["布格 grid[x][y]（6 列 × 7 行）<br/>敌区 0~2 · 缓冲行 3（可通行可停留）· 我区 4~6"]
        D1 --> D2 --> D3 --> D4
    end

    IN --> SB
    SB --> ST["BattleState（一场战斗，战斗结束整体丢弃）<br/>units（id 升序）· grid · projectiles<br/>events（追加式）· tick / elapsed · outcome"]

    STEP["BattleSystem.step（60Hz 固定步）"]
    STEP -->|"五阶段固定序读写（见主循环图）"| ST
    ST --> EV["CombatEvent 事件流（纯数据 · 只产出不消费）<br/>AttackLaunched / Hit / Cast / StatusApplied<br/>Healed / Shielded / UnitDied / ProjectileFizzled / BattleEnded"]
    ST --> OC["BattleOutcome<br/>PLAYER_WIN / ENEMY_WIN / TIMEOUT"]

    EV --> C1["单元测试断言<br/>（equals 逐位对拍）"]
    EV --> C2["回放复盘<br/>（同 seed 同输入 = 同事件流）"]
    EV --> C3["Phase 4 渲染消费<br/>FxLayer / 飘字 / 战斗日志面板"]
```

## 边界与约束

| 约束 | 出处 |
|------|------|
| `BattleUnit` 战斗作用域内受控可变、出作用域即弃（不可变优先的显式例外） | architecture §2.4 第三层 |
| 棋盘占位归 `BattleState`，不归 `Player` | GDD §10.2 注 / architecture §2.3 |
| 战斗内 RNG 消耗仅暴击判定一点（发射序消耗） | architecture §六 |
| `Unit`（名单）绝不被战斗污染——战斗只读名单派生新实例 | architecture §2.1 |
| 逻辑层只产出事件；渲染/测试/回放是消费者 | battle §二 |
