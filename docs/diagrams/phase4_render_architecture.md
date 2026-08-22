# Phase 4 帧循环与双通路渲染架构图

> `BattleScreen.render(delta)` 的逻辑段 / 渲染段两段式结构，与双通路（棋盘域自绘 × UI 域 Stage）的数据流。
> 浏览器查看版：`phase4_render_architecture.html`（双击打开）
> 依据：`render_design.md` §三（双通路）/§四（帧循环与插值）/§5.1（动画 FSM）；`user_input_design.md` §5.3（固定步长消费）；Q1/Q2/Q3/Q4 裁决（2026-08-22）

```mermaid
flowchart TB
    subgraph FRAME["BattleScreen（screens/，唯一装配点，内部零换屏）"]
        direction TB
        subgraph LOGIC["逻辑段（固定步长 60Hz，input §5.3）"]
            ACC["accumulator += min(delta, MAX_DELTA) × speedFactor<br/>×1/×2 变速 = 表演层消费速率，不改逻辑（architecture §4.2）"]
            LOOP["while accumulator ≥ LOGIC_STEP 且 ticks++ < MAX_TICKS_PER_FRAME(5)<br/>超限丢弃积压 + 记日志（死亡螺旋防御）"]
            CMD["commandManager.executeAll(runContext)<br/>所有阶段执行 · 阶段门控矩阵拦截非法命令（静默忽略）"]
            BTL["phase == BATTLE → battleSystem.step(state)<br/>五阶段固定序（Phase 3 既有，零改动）<br/>产出 CombatEvent（追加式事件流）"]
            OBS["phase == BATTLE 且 state.isOver()<br/>→ runFlowSystem.onBattleOver（Screen 只做观察-委托）"]
        end
        subgraph RD["渲染段（每帧一次，零对象分配纪律）"]
            direction TB
            ALPHA["alpha = accumulator / LOGIC_STEP ∈ [0,1)"]
            INBOX["EventInbox（cursor 游标，只前进不重播）<br/>forEachNew → UnitAnimState / FloatTextFormat / FxLayer"]
            WORLD["棋盘域 SpriteBatch 自绘（render §3.2 层序）<br/>② 棋盘格+高亮 ③ 备战席 ④ UnitView（插值+血条+能量条+星级）<br/>⑤ ProjectileView ⑥ FxLayer ⑦ FloatingText ⑧ 拖拽 ghost"]
            UI["uiStage.draw()（① 顶栏 / ⑥ 开战按钮 / 战斗 HUD / RESULT 横幅）<br/>dialogStage 推 Phase 5（multiplexer 位置预留）"]
        end
        ACC --> LOOP --> CMD --> BTL --> OBS --> ALPHA --> INBOX --> WORLD --> UI
    end

    subgraph SRC["模拟层（Phase 1~3 既有，只读消费）"]
        direction TB
        BS["BattleState<br/>getUnits（id 序含亡者）· getEvents（追加式）<br/>getProjectiles · getTick / getElapsed / isOver / getOutcome"]
        BU["BattleUnit（只读）<br/>gridX / gridY / currentHp / energy / star / statuses / isCleaned"]
        EV["CombatEvent（9 类，纯数据）<br/>Hit / Cast / Healed / Shielded / UnitDied / ..."]
    end

    BIP["BoardInputProcessor（input/）<br/>unproject(棋盘 viewport) · 死区 20 虚拟像素<br/>拖拽状态机（同 pointer 一份 DragContext）"]
    CM["CommandManager（command/，Q1 出生）<br/>队列 + (tick, cmd) 历史 + onExecuted"]
    RC["RunContext（Q1 减配：Player / RunState / BattleState 可空 / GameData / RNG）"]

    BIP -->|"松手才入队（非法落点不产生命令）"| CM
    CM --> RC
    BS --> BU
    BS --> EV
    BS -->|"EventInbox 游标差分（渲染段一次消费）"| INBOX
    BS -->|"逐帧轮询：坐标 / 血量 / 能量 / 状态（移动不产事件，口径 #20）"| WORLD
    ALPHA -.->|"弹道外推：lerp(prevPos, pos, alpha)"| WORLD
    BU -.->|"UnitView 插值：lerp(fromCell, toCell, elapsed × moveSpeed)（render §4.2）"| WORLD

    classDef pure fill:#eef,stroke:#88a
```

## 边界与约束

| 约束 | 出处 |
|------|------|
| 渲染只读模拟层：`entities/ data/ systems/` 禁 import 渲染类与 `Gdx.*` | project_structure §四 |
| 逻辑 tick 产出的 CombatEvent 进帧事件缓冲，渲染段一次性消费、不重播 | render §4.3（cursor 实现见差异声明 #2） |
| 变速 / 暂停是表演层（accumulator 消费速率），不进命令不改逻辑 | architecture §4.2 |
| 渲染与占位资源生成禁止触碰模拟 `RandomGenerator`（配色用 FNV-1a 纯 hash） | architecture §六 / 本期口径 #14 |
| UnitView 等战斗视图生命周期 = BattleState（一场战斗整体销毁重建） | render §六视图铁律 3 |
| 渲染段零对象分配（池获取除外）——事件消费走 cursor + 回调，events 视图只取一次 | render §八.6 |
