# 攻击/受击动作反馈：事件接线与绘制变换图

> attack_feedback（2026-08-23）：出手底部中心轴小幅摆动 + 受击整数像素抖动（纯表现层叠加，零逻辑改动、零新增素材）。
> 浏览器查看版：`attack_feedback_event_transform.html`（双击打开）
> 依据：计划 `docs/spec_plan/2026-08-23_attack_feedback.md` §5/§6；`attack_feedback_design.md` FP1~FP3；render_design V1.4 §一#7 / §5.1 / §5.3 / §八#3

## 1. 事件接线与抑制（BattleRenderer.routeEvent / onDamaged，两处既有 case 内各追加一行）

```mermaid
flowchart TB
    SIM["systems（BattleSystem / DamagePipeline）<br/>state.record(CombatEvent) —— 全部零改动"]
    INBOX["EventInbox cursor 游标<br/>drawBattle 内 forEachNew → routeEvent（BattleRenderer.java:222-227）"]
    ROUTE{"routeEvent(event) 按类型分派"}
    SWING["ATTACK_LAUNCHED（近战/远程统一出口）<br/>attacker.anim().onEvent(既有) + triggerAttackSwing()（新增）<br/>摆动计时刷新满 0.25s"]
    HITBOX["HIT（仅直伤）→ onDamaged（与白闪同点）<br/>target.anim().triggerHitFlash()（既有）<br/>+ triggerHitShake(axis)（新增，0.15s）"]
    AXIS["HitShakeAxis（纯函数，新增）<br/>攻击者在受击者左 → +1 / 右 → −1<br/>视图缺失（sourceId=-1）或同列 → id 奇偶回退（偶+1/奇−1）<br/>确定性，不引入随机源"]
    POS["HEALED / SHIELDED<br/>只飘字 + 落点闪光——不白闪不抖（既有口径不动）"]
    DEATH["UNIT_DIED → DEATH 锁定<br/>swingTimer / shakeTimer 立即清零（FP3 死亡抑制）"]
    DOT["DOT 真伤 applyTrueDamage<br/>无事件（口径 #10，DamagePipeline.java:51-55）"]
    NOFX["不触发任何反馈（与白闪同口径）"]

    SIM --> INBOX --> ROUTE
    ROUTE -->|"sourceId = 出手者"| SWING
    ROUTE -->|"targetId = 受击者"| HITBOX
    ROUTE --> POS
    ROUTE -->|"sourceId = 亡者"| DEATH
    AXIS --> HITBOX
    DOT -.->|"无路由可达"| NOFX
```

## 2. UnitView.draw 本体绘制变换合成（render §3.2 层序 ④ 棋子本体；白闪层共用同一变换）

```mermaid
flowchart TB
    CXY["当帧 cx / cy = virtualX / virtualY（整数吸附，坐标纪律：不动）"]
    SHAKE["hitShakeDx()（整数像素）<br/>axis · round(2 · sin(2πt/0.07) · (1 − t/0.15))<br/>约 2 个来回线性衰减"]
    BASE["x = cx + hitShakeDx() − size/2<br/>y = cy − size/2（y 向上坐标系：精灵底部中心 = cy − size/2）"]
    MODE{"ATTACK_SWING_MODE<br/>（UnitAnimState 常量，缺省 ROTATE）"}
    ROT["ROTATE（主模式，像素规则第三例外）<br/>九参 draw：origin (size/2, 0) = 底部中心轴<br/>angle = ±[6° · sin(2πt/0.25) · (1 − t/0.25)]<br/>玩家前倾 = 负角（顺时针）/ 敌方 flipX 镜像 = 正角<br/>angle = 0 时早退四参路径（未触发与现状逐帧一致）"]
    TRA["TRANSLATE（备选）<br/>四参 draw 平移 dx = ±round(2 · sin(2πt/0.25))<br/>沿自身朝向（敌方取反）阶梯 +2→0→−2→0"]
    OUT["本体 + 白闪层两次 draw 共用同一变换<br/>（attack/cast/death 动画帧选择叠播，帧时长不变）"]
    STILL["不随动（用未变换 cx/cy）：血条/能量条/星级点（drawBars）、<br/>敌我色框（SideColors.drawBorder）"]
    FX["不随动（锚点取未变换 virtualX/Y）：伤害飘字（⑦）、落点闪光 sparkBurst（⑥ FxLayer）"]

    CXY --> BASE
    SHAKE --> BASE
    BASE --> MODE
    MODE -->|ROTATE| ROT --> OUT
    MODE -->|TRANSLATE| TRA --> OUT
    CXY --> STILL
    CXY --> FX
```

## 3. 约束与边界

| 约束 | 出处 |
|------|------|
| 纯表现层：CombatEvent / BattleSystem / DamagePipeline / systems / data 零改动，只消费既有事件 | attack_feedback §2 成功标准 |
| 像素规则：禁旋转新增第三例外「攻击摆动小幅旋转（仅默认模式，底部中心轴 ≤8°、~0.25s）」；TRANSLATE 备选仍整数像素 | render V1.4 §一#7 / §八#3（2026-08-23 裁决 C 方案） |
| CAST 不触发摆动（已有起手闪光 + cast 动画）；施法中摆动不抑制（叠加层播完） | attack_feedback FP1 / FP3 |
| 近战不重复触发：摆动只认 ATTACK_LAUNCHED；HIT 的攻方动画路由保持现状 | attack_feedback FP1 |
| 死亡抑制：DEATH 锁定立即清零两计时；死后触发调用被忽略——不与缩放淡出叠加 | attack_feedback FP3 |
| DOT（POISON/BLEED 心跳）无事件不抖；HEALED/SHIELDED 不抖（正面反馈与白闪同口径） | attack_feedback §7 / FP2 |
| 渲染段零分配：每单位新增 2 个 float 计时器 + 1 个 int 轴，无对象创建 | attack_feedback §4 |
| 幅度常量归零 → 画面与现状逐帧一致（可完全关断）；模式常量一键切 ROTATE/TRANSLATE | attack_feedback §2 / 裁决 C |
| 零新增素材：素材 key 总量 201 不变 | art_asset_spec §4.5 / §7 |
