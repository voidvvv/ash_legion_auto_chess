# feedback06 战斗事件行主体解析数据流图

> feedback06（2026-08-22）：通知面板 CAST / UNIT_DIED 行加主体名，敌方附「（敌方）」标记（feedback04-2 惯例）。
> 浏览器查看版：`feedback06_event_subject_flow.html`（双击打开）
> 依据：计划 `2026-08-22_feedback06_event_subject_and_equipment_effect_view.md` §5.1/§6.CP-A2；render_design §5.5

## 1. 事件行生成链（三流之一：战斗 CombatEvent 流）

```mermaid
flowchart TB
    SIM["systems（BattleSystem 五阶段）<br/>state.record(CombatEvent) —— 追加式事件流"]
    BS["BattleState<br/>getEvents() 不可变视图（口径 #12）<br/>units 列表终身持有（含已清扫，BattleState.java:45-58）<br/>getUnitById 死后仍可查"]
    INBOX["EventInbox（attach 缓存视图 + cursor）<br/>NotificationPanel.syncBattle(state) 时 attach"]
    FIELD["NotificationPanel.battleState 字段（feedback06 新增）<br/>attach 时赋值 / detach 时置 null（生命周期与 inbox 对齐）"]
    FMT["formatEvent(event, data, battleState)（包级静态纯函数）<br/>CAST → 主体名 + \" 施放 \" + 技能中文名<br/>UNIT_DIED → 主体名 + \" 倒下\"<br/>主体名 = getUnitById(sourceId) → 模板名<br/>（Side.ENEMY → 名后附「（敌方）」）<br/>查不到（防御）→ \"#id\"<br/>→ truncateColumns(16 列)"]
    LOG["NotificationLog.appendCapped<br/>单帧 ≤2 行（§5.5 防刷屏，WARNING-6）"]
    DRAW["小窗 4 行（NOTIFY 128×46，12px 字体硬画不折行）<br/>大窗 200 行（L 键）"]

    SIM --> BS
    BS --> INBOX
    BS --> FIELD
    INBOX --> FMT
    FIELD --> FMT
    FMT -->|"截断在入队前完成（两窗同文案）"| LOG
    LOG --> DRAW
```

## 2. 主体解析与回退（subjectName 纯函数）

```mermaid
flowchart LR
    ID["event.getSourceId()<br/>（UNIT_DIED = 亡者 id；CAST = 施放者 id）"]
    NULL{"battleState == null ?"}
    LOOK["state.getUnitById(id)<br/>（units 构造序线性扫描，≤13 单位）"]
    FOUND{"unit != null ?"}
    SIDE{"unit.getSide() == ENEMY ?"}
    OK["模板中文名<br/>（BattleUnit.getTemplate().getName()）"]
    ENEMY["模板中文名 + 「（敌方）」"]
    FB["\"#\" + id（防御回退）"]

    ID --> NULL
    NULL -->|"是"| FB
    NULL -->|"否"| LOOK
    LOOK --> FOUND
    FOUND -->|"否"| FB
    FOUND -->|"是"| SIDE
    SIDE -->|"否（玩家侧）"| OK
    SIDE -->|"是"| ENEMY
```

## 3. 边界与约束

| 约束 | 出处 |
|------|------|
| HIT/HEALED 过噪跳过维持不变，不扩事件类型范围 | 口径 #13 / 用户裁决（feedback06 范围） |
| 行宽策略 = 截断不折行：16 列上限（192px），NOTIFY_X+6 起右缘 218 < 棋盘左缘 224；折行会占多行配额、破坏单帧 2 行上限口径 | 计划 §5.3-A2 / render §5.5 |
| BattleUnit id 为战斗发号空间（IdIssuer.nextId，开战派生），与玩家名单 Unit id 不同源——主体解析必须走 BattleState，不得走 Player.getUnitById | BattleSystem.java:88-97 |
| 截断发生在 formatEvent（入队前），大窗显示同样截断行（两窗一致，YAGNI 不存双份） | 计划 §5.3-A2 |
| battleState 生命周期与 EventInbox 同步（syncBattle 双写），战毕 detach 后字段置 null 可 GC | 用户指定生命周期方案 |
