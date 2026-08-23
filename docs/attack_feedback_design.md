# 💫 战斗角色攻击/受击动作反馈特效 设计文档

> 状态：草案待评审 ｜ 归属：独立新文档，挂 `render_design.md` §5.1（事件驱动的表现层）之下 ｜ 下游：libgdx-impl-planner 可直接消费 ｜ 基准：代码现状（2026-08-23 file:line 实读核查）

---

## 1. 一句话概述

所有战斗单位（敌我双方 + Boss）每次普攻出手时以**底部中心为轴心小幅"扇扇子"式摆动**、每次被直伤命中时**小幅整数像素抖动**——纯表现层视觉反馈，战斗逻辑与数值零改动、零新增美术素材。

## 2. 动机与目标

**解决什么问题**：当前出手/受击的可读反馈只有 3 帧 attack 动画、0.1s 白闪与飘字三件套（`BattleRenderer.java:248` 路由注释所列）——32px 小精灵上帧差异微弱，"谁在打谁"的瞬间动感不足。

**期待什么体验**：出手有"发力感"、被打有"受力感"，战斗画面在不增加任何美术素材的前提下动起来。

**成功标准（可判定）**：
- 任意单位出手瞬间可见一次摆动、被直伤命中瞬间可见抖动，二者不干扰既有白闪/飘字/落点闪光/动画帧；
- `CombatEvent`、`BattleSystem`、`DamagePipeline`、`systems/` 与 `data/` 全部**零改动**（纯消费既有事件）；
- 素材 key 总量 201 不变（`art_asset_spec.md` §7）——零新增美术；
- 幅度常量归零后画面与现状逐帧一致（可完全关断）。

## 3. 用户想法对照表

| idea 原话（摘录） | 对应功能点 | 处理 |
|---|---|---|
| 范围：所有战斗角色（敌我双方全部单位） | FP1 / FP2 适用范围 | 采纳（含 Boss，§8 参数行 12） |
| 攻击动作触发时，角色精灵以「底部中心」为轴心做小幅转动/往复摆动，类似"扇扇子" | FP1（旋转主模式） | 采纳 |
| 被攻击命中时，角色做小范围抖动（shake） | FP2 | 采纳 |
| 纯表现层视觉反馈，不影响战斗逻辑与数值 | §4 分层声明 + §9 无冲突声明 | 采纳 |

## 4. 功能定位与分层声明（纯表现层）

- **是什么**：挂在 UnitView 上的两个**绘制期 transform 叠加层**——攻击摆动（旋转或平移）与受击抖动（位移），与 HitFlash 白闪同范式（`UnitAnimState.java:8-10`："HitFlash 为叠加层独立计时，不占状态位"）。
- **分层合规**：逻辑层零改动，表现状态归视图私有——battle_design §三"渲染层动画状态机……正是'逻辑无状态推导、表现有状态播放'的分层"（battle_design.md:128）的又一兑现。
- **绘制序归属**：属 render §3.2 绘制序 **④ 棋子（UnitView）的本体变换**，不新增绘制层、不进 ⑥ FxLayer（FxLayer 只管独立贴图特效：起手闪光/落点爆圈/状态图标）。
- **坐标纪律**：不改 `virtualX/virtualY`（`UnitView.java:73-81`）——插值位置、飘字锚点（`BattleRenderer.java:292-293`）、落点闪光锚点全部稳定；变换只在 `draw()` 内部作用于精灵的两次绘制（本体 + 白闪层，`UnitView.java:100,103`）。
- **性能**：每单位新增 2 个 float 计时器与若干静态常量，无对象创建——渲染段零分配纪律（render §八.6）不破。

## 5. 功能点拆分

### FP1. 攻击摆动（双模式：旋转主模式 + 平移备选模式）

- **是什么**：每次普攻出手，棋子以底部中心为轴"扇"一下——默认用小幅旋转实现，另留一个渲染常量一键切换为整数像素平移探身，供试玩评审对比后定稿。
- **游戏中的表现**：出手瞬间（近战抬手 / 远程发射）棋子向面朝方向前倾、回摆、归位，全程约 0.25s，与 3 帧 attack 动画（0.1s/帧 × 3 = 0.3s，`UnitAnimState.java:19,29`）大致同步叠播。
- **具体细节**：
  - **触发**：`ATTACK_LAUNCHED` 事件 → `sourceId` 对应 UnitView，在现有路由点 `BattleRenderer.java:251-255` 的 `attacker.anim().onEvent(...)` 处并列追加 `triggerAttackSwing()`。近战/远程统一走该事件（`CombatEvent.java:46-49` 工厂注释"近战即时与远程发射统一"），无需分叉。
  - **近战不重复触发**：近战当拍同时发 `ATTACK_LAUNCHED` 与 `HIT`（"近战即时：伤害与出手同拍"，`UnitAnimState.java:55`）——摆动只认 `ATTACK_LAUNCHED`；HIT 的攻方动画路由（`BattleRenderer.java:264-268`）保持现状不动。
  - **旋转主模式（默认启用）**：以精灵**底部中心** `(cx, cy − size/2)` 为轴心与绘制原点，旋转角 `angle(t) = A × sin(2πt/T) × (1 − t/T)`，`t ∈ [0, T]`；缺省 `A = 6°`（上限 8°）、`T = 0.25s`——恰好 **1 个来回**且幅值线性**衰减**（前倾峰值约 +0.75A、回摆约 −0.25A、终了归零）。32px 身高上 8° 的顶端水平位移约 4px，观感克制。
  - **平移备选模式**：把旋转换成沿朝向的水平位移 `dx(t) = A' × sin(2πt/T)`，缺省 `A' = 2px`（1~2px 区间），**逐帧 `Math.round` 吸附整数像素**（render §八.2 惯例）——取整后天然形成 `+2 → 0 → −2 → 0` 的阶梯往复（1 个来回），无需额外衰减。
  - **摆动方向**：以单位自身朝向为正方向（朝右单位前倾向右；敌方经 `flipX`（`UnitView.java:95-98`）后天然镜像对称），不取攻击目标相对方位——省一次向量计算，左右侧观感一致。
  - **作用范围**：精灵本体绘制与白闪叠加层两次 `draw`（`UnitView.java:100,103`）共用同一变换（白闪跟随摆动）；**血条/能量条/星级点/敌我色框不随摆动**（`drawBars` 与 `SideColors.drawBorder` 用未变换的 cx/cy，`UnitView.java:110-112`）。
  - **与移动复合**：摆动轴心取当帧 `virtualX/virtualY` 计算的底部中心，与格间插值位移天然复合（出手时单位本已停稳，复合只是保险）。
  - **重复触发**：刷新满计时（同 HitFlash 惯例，`UnitAnimState.java:97-99`）——高攻速连续出手表现为持续小幅摆动。
  - **模式切换常量（裁决要求的"一键切换"）**：建议枚举 `AttackSwingMode { ROTATE, TRANSLATE }` + 常量 `ATTACK_SWING_MODE = AttackSwingMode.ROTATE`，落点建议 `UnitAnimState` 常量区（与 `HIT_FLASH_SECONDS` 同处，`UnitAnimState.java:23`）；缺省 **ROTATE**，试玩评审对比后定稿（届时删常量定死单模式）。
- **现状对照**（修改型功能）：`UnitView.java:100` 现为 `batch.draw(region, cx - size/2f, cy - size/2f, size, size)`（四参重载，无旋转、无原点）→ 旋转模式改用九参重载 `draw(region, x, y, originX = size/2, originY = 0, w, h, 1, 1, angle)`；平移模式仅平移绘制坐标；两模式共用同一计时/曲线代码，仅末端变换不同。
- **涉及现有系统**：`CombatEvent.Type.ATTACK_LAUNCHED`（`CombatEvent.java:17`）、`BattleRenderer.routeEvent`（`BattleRenderer.java:249-256`）、`UnitView.draw`（`UnitView.java:83-114`）、`UnitAnimState` 叠加层计时范式（`UnitAnimState.java:97-105`）。
- **边界（不做什么）**：
  - 不改 UnitAnimState 状态机优先级与转移（Attack/Cast/Death 语义不变，摆动不占状态位）；
  - **技能施放（CAST）不触发摆动**——CAST 已有起手闪光 + cast 动画（`BattleRenderer.java:257-263`）；仅普攻出手摆动；
  - 不动死亡缩放淡出路径（DEATH 锁定态与摆动的互斥见 FP3）；
  - 备战席/侦察虚影/拖拽 ghost/商店头像不摆（走 `BattleRenderer.drawUnitFrame` 独立路径，`BattleRenderer.java:309+`，无事件无 FSM，天然隔离）。

### FP2. 受击抖动（hit shake）

- **是什么**：被直伤命中的棋子原地小幅往复抖动约 0.15s，与白闪同时发生、各自独立计时。
- **游戏中的表现**：HIT 落地瞬间棋子沿受力方向往复抖约 2 个来回并衰减停稳；白闪、飘字、落点闪光照旧。
- **具体细节**：
  - **触发**：`HIT` 事件 → `targetId` 对应 UnitView，与白闪同点：`BattleRenderer.java:294-295` 的 `if (event.getType() == HIT) target.anim().triggerHitFlash()` 处并列追加 `triggerHitShake(...)`。
  - **方向（缺省建议，【待确认】）**：基于**攻击者来向**的水平轴——取攻击者 UnitView 与受击者 `virtualX` 的 x 差符号，抖动沿该轴往复（受力感可读）；攻击者取不到或 sourceId 为 -1（`DamagePipeline.applyDirectHit`：`attacker == null ? -1`，`DamagePipeline.java:40-41`）时按受击者 id 奇偶定左右（确定性回退，不引入随机源）。备选方案：纯随机左右（若采纳，随机数只在渲染层局部取，严禁触碰逻辑 RNG 消耗点）。
  - **幅度**：±2 整数像素（1~2px 区间），逐帧 `Math.round` 吸附（32px 精灵上约 6% 身位，可感知不越格）。
  - **时长与频率**：全程 0.15s；往复周期缺省 0.07s（60fps 下约 4 帧一循环）→ 约 2 个来回，幅值线性衰减到 0。
  - **作用范围**：同 FP1——仅精灵本体 + 白闪层位移；血条/色框/飘字锚点不动。
  - **重复触发**：刷新满（同 HitFlash，`UnitAnimState.java:97-99`）；同帧 AOE 多条 HIT → 各受击者独立抖动（按 targetId 路由天然分摊，render §4.3"同一 UnitView 的同类动画取最新触发"惯例）。
  - **不触发者**：`HEALED` / `SHIELDED` 不抖（治疗/护盾是正面反馈，与白闪同口径——现有代码这两类只出飘字 + 落点闪光，`BattleRenderer.java:271-273`）；DOT 心跳不抖（无事件，见 §7）。
- **现状对照**（修改型功能）：`UnitView.java:100,103` 两次 `draw` 的 `cx/cy` → 追加抖动偏移 `(dx, 0)`；`UnitAnimState` 现无 shake 字段 → 新增计时器与 `triggerHitShake()/hitShakeOffset()`（照 `HIT_FLASH_SECONDS → triggerHitFlash → hitFlashRatio` 三件套范式，`UnitAnimState.java:23,97-105`）。
- **涉及现有系统**：`CombatEvent.Type.HIT`（`CombatEvent.java:17,52-54`）、`BattleRenderer.onDamaged`（`BattleRenderer.java:287-306`）、`UnitView.draw`、`UnitAnimState`。
- **边界（不做什么）**：不做击退/位移（不改逻辑坐标与插值）；不做垂直方向抖动（缺省水平轴）；不屏蔽飘字与白闪；不为 DOT 补事件（`DamagePipeline.applyTrueDamage` 口径 #10 明确"无回能无吸血无事件"，`DamagePipeline.java:51`——补事件属逻辑层改动，超出本功能范围）。

### FP3. 触发接线、抑制与生命周期

- **是什么**：定义两类反馈"何时触发、何时被抑制、何时随战斗销毁"的统一规则（事件源总表见 §7）。
- **具体细节**：
  - **死亡抑制（缺省，【待确认】）**：单位进入 DEATH 锁定态（`UnitAnimState.java:61-64`）时**立即中止并清零**摆动与抖动计时——避免旋转/抖动叠加在"缩放淡出钉在脚下"的死亡表现上（死亡缩放本身是像素规则例外，`UnitView.java:91`）。
  - **施法抑制（缺省，【待确认】）**：摆动进行中单位施放技能（CAST 打断动画但不打断叠加层）→ 摆动**继续播完**（叠加层语义，与 HitFlash 一致）；0.25s 的重叠极短，不做特殊处理。
  - **生命周期**：两计时器是 UnitView 私有表现状态，随战斗 rebuild/clear 整体销毁（render §六铁律 3：视图生命周期 = BattleState）——零额外清理代码。
  - **关断方式**：幅度常量归零即完全关断、画面回到现状；不设运行时 UI 开关（MVP 不需要，试玩评审用改常量重跑；如 planner 需要，枚举可加 `OFF` 值）。
- **边界（不做什么）**：不新增 CombatEvent 类型；不感知备战期/商店（无事件即无反馈）；参数不做配置文件化（静态常量即可，沿 `UnitAnimState` 表现常量先例，不入 `GameBalance`——那是对战数值域）。

## 6. 与既有反馈的叠加与层级关系

| 既有反馈 | 层级（render §3.2） | 与本功能的关系 |
|---|---|---|
| HitFlash 白闪 0.1s（`UnitView.java:101-103`） | ④ 棋子本体叠加层 | **同单位同帧共存**：白闪层复用摆动旋转/抖动位移后的同一变换绘制（不互斥） |
| 落点闪光 sparkBurst（`BattleRenderer.java:305`） | ⑥ FxLayer | 独立；锚点取未变换 virtualX/Y，不随抖动漂移 |
| 伤害飘字（render §5.2，`BattleRenderer.java:292-303`） | ⑦ 飘字层 | 独立；锚点同上，错位堆叠逻辑不变 |
| attack/cast/death 动画帧（UnitAnimState FSM，render §5.1） | ④ 本体动画 | **叠播**：摆动/抖动是变换，动画是帧选择，互不取代；帧时长不变 |
| 震屏（Boss 全屏锚点，render §5.4 表现层自由项） | 全屏覆盖 | 独立并存（"屏幕晃 + 棋子抖"叠加观感预期成立） |
| 音效 | — | 本功能不新增音效 |

- **攻击者摆动与受击者抖动分属不同单位**，永远不冲突；同一单位"刚出手即被打"时两个计时器并行，互不抢占、各播各的。

## 7. 触发事件源表

| 表现 | 驱动事件 | 现有路由锚点 | 目标单位 | 备注 |
|---|---|---|---|---|
| 攻击摆动 | `ATTACK_LAUNCHED`（`CombatEvent.java:17,46-49`） | `BattleRenderer.routeEvent` case 分支（`BattleRenderer.java:251-255`） | `sourceId`（出手者） | 近战即时与远程发射统一出口 |
| 受击抖动 | `HIT`（`CombatEvent.java:17,52-54`） | `BattleRenderer.onDamaged` 白闪同点（`BattleRenderer.java:294-295`） | `targetId`（受击者） | 仅直伤命中；HEALED/SHIELDED 不触发 |
| （DOT 掉血） | **无事件**——`DamagePipeline.applyTrueDamage` 口径 #10"无回能无吸血无事件"（`DamagePipeline.java:51`） | — | — | **POISON/BLEED 心跳不触发抖动**（与白闪同口径：白闪也仅 HIT 触发）；未来若 DOT 要受击表现须先补事件，属逻辑层改动，不在本功能范围 |

## 8. 参数总表（缺省值 + 待确认标记）

| # | 参数 | 缺省建议 | 理由 | 状态 |
|---|---|---|---|---|
| 1 | 摆动模式 `ATTACK_SWING_MODE` | `ROTATE` | 用户裁决 C 方案：旋转为主模式 | 已定（裁决）；终稿试玩评审后定 |
| 2 | 摆动幅度 A（旋转） | 6°（上限 8°） | 8° 顶端位移约 4px 已够醒目，6° 更克制 | 【待确认】 |
| 3 | 摆动幅度 A'（平移备选） | ±2px（1~2px 区间） | 整数像素下可感知的最小有效值 | 【待确认】 |
| 4 | 摆动时长 T | 0.25s | 与 attack 动画 0.3s 大致同步，快于攻击间隔下限 | 【待确认】 |
| 5 | 摆动来回次数 | 1 个来回（旋转衰减正弦 / 平移阶梯） | "扇一下"手感；多来回显躁 | 【待确认】 |
| 6 | 抖动方向 | 攻击者来向水平轴；无攻击者回退按 id 奇偶 | 受力方向可读；回退确定性不引随机源 | 【待确认】 |
| 7 | 抖动幅度 | ±2px（1~2px 区间） | 同 #3 | 【待确认】 |
| 8 | 抖动时长 | 0.15s | 略长于白闪 0.1s、短于摆动，快节奏反馈 | 【待确认】 |
| 9 | 抖动往复周期 | 0.07s（60fps 约 4 帧一循环，全程约 2 来回） | 更快则 60fps 下欠采样 | 【待确认】 |
| 10 | 死亡态抑制 | 立即中止并清零 | 避免与死亡缩放淡出叠加 | 【待确认】 |
| 11 | 施法态抑制 | 不抑制，叠加层播完 | 与 HitFlash 叠加语义一致 | 【待确认】 |
| 12 | Boss 参数 | 与普通棋子同套 | Boss 统一 32×32 显示（`art_asset_spec.md` §6-1），底部中心轴天然成立 | 【待确认】 |

- 摆动"频率"不设独立参数，由 #4 时长与 #5 来回次数派生（0.25s 内 1 个来回）。

建议常量命名（落 `UnitAnimState` 常量区，沿 `HIT_FLASH_SECONDS` 先例，最终命名 planner 可调）：

```java
public enum AttackSwingMode { ROTATE, TRANSLATE }           // 预留 OFF 可选
public static final AttackSwingMode ATTACK_SWING_MODE = AttackSwingMode.ROTATE; // 试玩评审后定稿
public static final float ATTACK_SWING_DEGREES = 6f;        // 旋转幅度（上限 8°）
public static final float ATTACK_SWING_PIXELS = 2f;         // 平移备选幅度（整数像素）
public static final float ATTACK_SWING_SECONDS = 0.25f;     // 摆动全程时长
public static final float HIT_SHAKE_PIXELS = 2f;            // 抖动幅度（整数像素）
public static final float HIT_SHAKE_SECONDS = 0.15f;        // 抖动时长
public static final float HIT_SHAKE_PERIOD_SECONDS = 0.07f; // 抖动往复周期
```

## 9. 与现有功能的关系（无冲突声明，基于 2026-08-23 核查）

- **新增**：UnitView 两个绘制期 transform 叠加层；UnitAnimState 计时器与常量；`BattleRenderer` 两处既有 case 内各追加一行触发调用。
- **修改的文档**：render_design §一#7 / §5.1 / §5.3 / §5.4 / §八#3 / §十二（像素规则第三例外 + 挂链 + 决策日志）；GDD §6.6 第 4 条挂链；art_asset_spec §4.5 零新增素材注记。
- **不改动**：`CombatEvent`、`BattleSystem`、`DamagePipeline`、`systems/` 与 `data/` 全部、素材 key 总量 201。
- **无冲突核查结论**：
  - 受击抖动与 HitFlash（`UnitAnimState.java:23`）同范式叠加，叠加不互斥；整数像素偏移与 render §八.2 吸附兼容；
  - 摆动/抖动是 ④ 棋子本体变换，不进 ⑥ FxLayer——HitFlash/落点闪光/飘字不受影响；
  - 纯程序化 transform，零新增素材（`art_asset_spec.md` 全量清单不变）；
  - Boss 统一 32×32 显示（`art_asset_spec.md` §6-1），底部中心轴心天然成立；
  - 分层合规：逻辑无状态仲裁、表现有状态播放（battle_design.md:128）。
  - 唯一红线冲突（像素规则禁旋转 vs 棋子本体旋转）已经用户裁决 C 方案解决（§10），render_design 三处条文已同步修订。

## 10. 冲突与裁决记录

### C1. 攻击摆动需要棋子本体旋转 vs 像素规则"禁旋转"

- **新想法**：攻击时角色精灵以底部中心为轴小幅转动/往复摆动（"扇扇子"）。
- **现有设定**：render_design.md:21 §一#7、:310 §八#3、:132 §5.3、:369 §十二（2026-08-20 像素规则行）——"禁旋转，例外仅死亡缩放与弹道"；spec_plan/2026-08-22_phase4_board_rendering.md:191 同口径；代码现状 `UnitView.java:100,103` 为无旋转的四参绘制。
- **冲突描述**：旋转摆动与"禁旋转"条文互斥——棋子本体旋转产生非整数像素采样，破坏像素对齐，而用户明确想要旋转式摆动手感。
- **当时选项**：A 修订像素规则新增第三例外（允许小幅旋转）；B 放弃旋转，改为整数像素平移探身；C A+B 双模式开关。
- **用户裁决（2026-08-23）**：**C 方案——A+B 双模式开关**。以「底部中心轴小幅旋转摆动」为主模式（默认启用），同时留一个渲染常量可一键切换到「沿朝向 ±1~2px 整数像素平移探身」备选模式，供试玩评审后对比定稿。因此仍按 A 的口径修订像素规则新增第三例外「攻击摆动小幅旋转（仅默认模式）」，同步修订 render_design §一#7 与 §八#3（本次连同 §5.3"唯一特例"措辞一并修正），并在 §十二决策日志追加本条裁决记录（注明双模式开关的存在与切换常量建议名 `ATTACK_SWING_MODE`）。

## 11. 术语对照

| 口语 | 本文档 / GDD 用语 | 代码标识（现状 → 拟新增） |
|---|---|---|
| 扇扇子 | 攻击摆动（旋转主模式） | 拟 `UnitAnimState.triggerAttackSwing()` |
| 探身 | 平移备选模式 | 拟 `AttackSwingMode.TRANSLATE` |
| 抖动 shake | 受击抖动 | 拟 `UnitAnimState.triggerHitShake() / hitShakeOffset()` |
| 白闪 | 受击白闪叠加层 | `UnitAnimState.triggerHitFlash() / hitFlashRatio()`（`UnitAnimState.java:97-105`） |
| 叠加层 | 不占状态位的可叠加表现 | HitFlash 范式（`UnitAnimState.java:8-10`） |
| 出手 | 普攻攻击动作发起 | `CombatEvent.Type.ATTACK_LAUNCHED` |

## 附：决策日志

| 日期 | 决策 | 结论 |
|---|---|---|
| 2026-08-23 | C1 攻击摆动旋转 vs 像素禁旋转 | **C 方案双模式**：旋转主模式默认启用（底部中心轴、≤8°、~0.25s、1 来回衰减）+ 常量 `ATTACK_SWING_MODE` 一键切换平移备选（沿朝向 ±1~2 整数像素）；像素规则新增第三例外（仅默认模式），render_design §一#7/§5.3/§八#3 同步修订、§十二追加记录 |
| 2026-08-23 | 文档成文 | 受击抖动挂 HIT 与白闪同点叠加；纯表现层零逻辑改动、零新增素材；§8 其余 11 项参数给缺省值待试玩确认 |
