# 战斗角色攻击/受击动作反馈特效 技术实施文档

> 版本：V1.0（2026-08-23）　分支：feature/attack_effect　基线：e4ab854（733 测试全绿）
> 输入：`docs/attack_feedback_design.md`（唯一设计事实源，2026-08-23 定稿）+ 配套修订（render_design V1.4 §一#7/§5.1/§5.3/§八#3/§十二、art_asset_spec §4.5、gdd_idea §6.6——均已实读核实到位）
> 澄清门禁结论：**无 BLOCKER**。C1（旋转 vs 像素禁旋转）已由用户裁决 C 方案并写入 GDD §10；GDD §8 的 11 项【待确认】参数按任务指示以缺省值直接消费（§4-D2），不重新发起裁决。本次核对发现 1 处 GDD 行文坐标笔误（不改实现方向，记 §5.4 差异声明 D1）。
> 范围声明：纯表现层——`CombatEvent`、`BattleSystem`、`DamagePipeline`、`systems/` 与 `data/` 零改动，零新增素材（key 总量 201 不变）。改动集中在 `render/board/` 三文件 + 两个纯逻辑新文件。

---

## 1. 背景与目标

### 1.1 问题

当前出手/受击的可读反馈只有 3 帧 attack 动画、0.1s 白闪与飘字三件套（`BattleRenderer.java:248` 路由注释）——32px 小精灵上帧差异微弱，"谁在打谁"的瞬间动感不足（GDD §2）。

### 1.2 方案一句话

所有战斗单位（敌我双方 + Boss）每次普攻出手时以**底部中心为轴小幅"扇扇子"式摆动**（旋转主模式，常量可一键切整数像素平移备选）、每次被直伤命中时**小幅整数像素抖动**——两个挂在 UnitView 上的**绘制期 transform 叠加层**，与 HitFlash 白闪同范式。

### 1.3 成功标准（可判定）

1. 任意单位出手瞬间可见一次摆动、被直伤命中瞬间可见抖动，二者不干扰既有白闪/飘字/落点闪光/动画帧；
2. `CombatEvent`、`BattleSystem`、`DamagePipeline`、`systems/`、`data/` 全部零改动（纯消费既有事件）；
3. 素材 key 总量 201 不变；
4. 幅度常量归零后画面与现状逐帧一致（可完全关断）；
5. 733 基线测试零回归 + 新增测试全绿（gradle XML 聚合计数核对，沿用 MEMORY 口径）。

### 明确不做

| 项 | 出处 |
|----|------|
| 技能施放（CAST）触发摆动（CAST 已有起手闪光 + cast 动画，`BattleRenderer.java:257-263`） | GDD FP1 边界 |
| 为 DOT 补事件（`applyTrueDamage` 口径 #10"无回能无吸血无事件"，补事件属逻辑层改动） | GDD FP2 边界 / §7 |
| 击退/位移/垂直方向抖动（不改逻辑坐标与插值） | GDD FP2 边界 |
| 备战席/侦察虚影/拖拽 ghost/商店头像摆动（走 `drawUnitFrame` 独立路径，`BattleRenderer.java:309-329`，无事件无 FSM 天然隔离） | GDD FP1 边界 |
| 参数配置文件化 / 运行时 UI 开关（静态常量，沿 `HIT_FLASH_SECONDS` 先例，不入 GameBalance——那是对战数值域） | GDD FP3 边界 |
| 新增 CombatEvent 类型、新增音效、改 UnitAnimState 状态机优先级与转移 | GDD FP1/FP3 边界 |

---

## 2. 术语与约定

| GDD 用语 | 代码标识符（现状 → 本文档定名） | 备注 |
|------|------|------|
| 攻击摆动（"扇扇子"） | `UnitAnimState.triggerAttackSwing()` / `attackSwingDegrees()` / `attackSwingDx()` | GDD §11 拟名 `hitShakeOffset` 类比，方法名 planner 定名权（GDD §8 建议"最终命名 planner 可调"） |
| 旋转主模式 | `UnitAnimState.AttackSwingMode.ROTATE`（嵌套枚举 + `ATTACK_SWING_MODE` 常量，缺省 ROTATE） | 裁决 C 方案（GDD §10 C1） |
| 平移备选模式（"探身"） | `UnitAnimState.AttackSwingMode.TRANSLATE` | 试玩评审对比后定稿，届时删常量定死单模式 |
| 受击抖动（shake） | `UnitAnimState.triggerHitShake(int)` / `hitShakeDx()` | 叠加层，与 HitFlash 同范式 |
| 抖动水平轴（方向） | `HitShakeAxis.resolve / fallback`（新建纯函数类） | +1 = 首拍向右，−1 = 首拍向左 |
| 白闪 | `UnitAnimState.triggerHitFlash() / hitFlashRatio()`（`UnitAnimState.java:97-105`，既有） | 摆动/抖动照此三件套范式 |
| 叠加层 | 不占 FSM 状态位的可叠加表现（`UnitAnimState.java:8-10` 类注释范式） | 独立计时、施法不抑制、死亡清零 |
| 底部中心轴 | UnitView 九参 draw 的 `originX = size/2, originY = 0`（绘制原点即精灵底边中点） | 见 §5.4 D1 坐标系勘误 |
| 出手 | `CombatEvent.Type.ATTACK_LAUNCHED`（近战即时与远程发射统一，`CombatEvent.java:46-49`） | sourceId = 出手者 |
| 直伤命中 | `CombatEvent.Type.HIT`（`CombatEvent.java:52-54`） | targetId = 受击者 |

---

## 3. 现状盘点（file:line 均为本次实读核对，基线 e4ab854）

### 3.1 GDD 锚点核对结论（全部一致，无幽灵机制）

| GDD 锚点 | 实读 | 结论 |
|---|---|---|
| `BattleRenderer.java:251-255` ATTACK_LAUNCHED case | ✅ 251-256（case 体 251-255 + break 256） | 追加点确认 |
| `BattleRenderer.java:257-263` CAST 路由（sparkCast） | ✅ 257-263 | 不动 |
| `BattleRenderer.java:264-268` HIT 攻方动画路由 | ✅ 264-269 | 不动（近战不重复触发摆动） |
| `BattleRenderer.java:271-273` HEALED/SHIELDED 只飘字 | ✅ 271-274 | 不动（不抖） |
| `BattleRenderer.java:294-295` 白闪触发点 | ✅ 294-296 | 抖动并列追加处 |
| `BattleRenderer.java:292-293` 飘字锚点 x/y | ✅ 292-293 | 不随动 |
| `BattleRenderer.java:305` sparkBurst | ✅ 305 | 不随动 |
| `BattleRenderer.java:309+` drawUnitFrame 独立路径 | ✅ 309-329 | 天然隔离不触碰 |
| `UnitView.java:73-81` virtualX/Y | ✅ 73-81 | 不改（坐标纪律） |
| `UnitView.java:95-98` flipX 镜像 | ✅ 95-98 | 摆动方向的镜像基础 |
| `UnitView.java:100,103` 两处四参 draw | ✅ 100 与 103 | 本体 + 白闪层，改统一变换 |
| `UnitView.java:110-112` drawBars / SideColors | ✅ 110-113 | 用未变换 cx/cy，不随动 |
| `UnitAnimState.java:8-10` 叠加层范式注释 | ✅ 8-10 | — |
| `UnitAnimState.java:19,29` attack 0.1s×3 帧 | ✅ 19 / 29 | 0.3s 与摆动 0.25s 大致同步 |
| `UnitAnimState.java:23` HIT_FLASH_SECONDS | ✅ 22-23 | 常量落点（同处） |
| `UnitAnimState.java:55` "近战即时：伤害与出手同拍" | ✅ 55 | — |
| `UnitAnimState.java:61-64` DEATH 锁定 | ✅ 61-65 | 死亡清零追加处 |
| `UnitAnimState.java:97-105` triggerHitFlash/hitFlashRatio | ✅ 97-105 | 三件套范式模板 |
| `CombatEvent.java:17,46-49,52-54` | ✅ 17 / 46-49 / 51-54 | — |
| `DamagePipeline.java:40-41`（attacker==null → -1） | ✅ 40-41 | 抖动方向回退的触发条件 |
| `DamagePipeline.java:51` applyTrueDamage 无事件 | ✅ 51-55（无 state.record 调用） | DOT 不抖的机制保证 |

### 3.2 可直接复用（零改动）

| 资产 | 位置 | 说明 |
|------|------|------|
| HitFlash 叠加层三件套范式（常量 → trigger → ratio） | `UnitAnimState.java:22-23, 97-105` | 摆动/抖动完全照此范式 |
| 事件游标消费 | `EventInbox` + `drawBattle` 内 `forEachNew → routeEvent`（`BattleRenderer.java:222-227`） | 触发接线搭在既有路由上 |
| UnitAnimState 纯 Java 可测性（零 Gdx） | `UnitAnimState.java:6` | 新公式方法全部落此类 → headless 可测 |
| 视图生命周期 = BattleState | `BattleRenderer.syncBattleScope`（`BattleRenderer.java:106-132`） | 两计时器随 rebuild/clear 整体销毁，零清理代码 |
| flipX 镜像惯例（用后即还） | `UnitView.java:95-98, 106-108` | 敌方摆动镜像的实现基础 |
| 整数吸附惯例 | `virtualX/Y` 的 `Math.round`（`UnitView.java:73-81`）；render §八.2 | TRANSLATE/抖动位移取整依据 |
| DOT 无事件既有测试 | `DamagePipelineTest.java:193-204`（"无事件无回能"，`assertThat(state.getEvents()).hasSize(eventsBefore)`） | "DOT 不抖"的链路保证：无事件 → 无路由 → 无触发 |
| 测试基建 | JUnit5 + AssertJ（`UnitAnimStateTest` 同款）；render 包测试放置惯例（`render/UnitAnimStateTest.java` 测 `render/board` 类） | 新测试沿用 |

### 3.3 需改造 / 需新建

| 文件 | 类型 | 预期行数 | CP |
|------|------|------|----|
| `core/src/main/java/com/voidvvv/kz_auto_chess_n/render/board/UnitAnimState.java` | 修改 | 153 → ~250 | CP1 |
| `core/src/main/java/com/voidvvv/kz_auto_chess_n/render/board/HitShakeAxis.java` | 新建 | ~32 | CP2 |
| `core/src/main/java/com/voidvvv/kz_auto_chess_n/render/board/UnitView.java` | 修改 | 143 → ~170 | CP3 |
| `core/src/main/java/com/voidvvv/kz_auto_chess_n/render/board/BattleRenderer.java` | 修改 | 395 → ~410 | CP4 |
| `core/src/test/java/com/voidvvv/kz_auto_chess_n/render/AttackFeedbackOverlayTest.java` | 新建 | ~205 | CP5 |
| `core/src/test/java/com/voidvvv/kz_auto_chess_n/render/HitShakeAxisTest.java` | 新建 | ~58 | CP6 |
| `docs/diagrams/attack_feedback_event_transform.md` / `.html` | 新建 | 已随本文档落盘 | — |

均远低于 800 行上限，无拆分压力。

---

## 4. 已确认决策

| # | 议题 | 裁决来源 | 本文档落地 |
|---|------|------|------|
| D1 | 攻击摆动旋转 vs 像素规则"禁旋转" | **用户裁决（2026-08-23，GDD §10 C1）**：C 方案双模式——旋转主模式默认启用（底部中心轴、≤8°、~0.25s、1 来回衰减）+ 常量 `ATTACK_SWING_MODE` 一键切换平移备选（沿朝向 ±1~2 整数像素）；像素规则新增第三例外（仅默认模式），render_design §一#7/§5.3/§八#3/§十二 已同步修订（实读核实：render_design.md:21/:124/:133/:311/:375） | CP1 常量 + CP3 双模式绘制分支 |
| D2 | GDD §8 参数 #2~#12（幅度/时长/方向/来回次数/死亡抑制/施法抑制/Boss 同套等 11 项【待确认】） | **任务指示**：为非阻塞缺省值，spec 直接按缺省消费并列成常量表，不重新发起裁决 | §6.CP1 常量表（缺省值逐字取自 GDD §8；Boss 不做参数分叉——统一 32×32 显示天然同套） |

其余实现细节（旋转屏幕角符号、抖动首拍方向语义、死亡双保险、0 角早退路径、测试探针取值）为本文档拟定项，见 §5.3 实现口径。

---

## 5. 总体技术方案

总体数据流与绘制变换：`docs/diagrams/attack_feedback_event_transform.md` / `.html`（§1 事件接线与抑制、§2 UnitView 变换合成、§3 约束表）。

### 5.1 架构分层

- **逻辑层（零改动）**：BattleSystem / DamagePipeline 照常产 `ATTACK_LAUNCHED` / `HIT` / `UNIT_DIED` 等事件；DOT 走 `applyTrueDamage` 无事件。
- **路由层（2 行接线）**：`BattleRenderer.routeEvent` 的 ATTACK_LAUNCHED case 追加 `triggerAttackSwing()`；`onDamaged` 的 HIT 白闪同点追加 `triggerHitShake(axis)`。方向轴决策抽 `HitShakeAxis` 纯函数（render/board 惯例：逻辑可测类不进 Gdx 类——同 LerpMotion / BoardGeometry 先例）。
- **表现状态层**：`UnitAnimState` 新增两个叠加层计时器（swing 0.25s / shake 0.15s）+ 公式访问器。照 HitFlash 范式：独立计时、不占 FSM 状态位、重复触发刷新满、死亡立即清零、施法不抑制。
- **绘制层**：`UnitView.draw` 的本体与白闪两次 draw 收敛到私有 `drawBody(batch, region, cx, cy, size)`——先加受击抖动水平偏移（整数像素），再按 `ATTACK_SWING_MODE` 施加旋转（九参 draw，底部中心轴）或平移（四参 draw，沿朝向整数像素）。血条/色框/飘字/落点闪光锚点全部继续用未变换 cx/cy，天然不随动。
- **生命周期**：两计时器是 UnitView 私有表现状态，随 `syncBattleScope` rebuild/clear 整体销毁（render §六铁律 3）——零额外清理代码。

### 5.2 公式（缺省值即 GDD §8 表）

| 量 | 公式 | 缺省 | 探针值（测试断言用） |
|---|---|---|---|
| 旋转摆角（度，正 = 前倾） | `angle(t) = A · sin(2π·t/T) · (1 − t/T)`，`t ∈ [0, T]` | A=6°，T=0.25s | t=T/4 → **+4.5°**（0.75A 前倾峰值）；t=T/2 → 0；t=3T/4 → **−1.5°**（−0.25A 回摆）；t=T → 0 |
| 平移位移（px，正 = 前倾） | `dx(t) = round(A' · sin(2π·t/T))` | A'=2px，T=0.25s | +2 → 0 → −2 → 0 阶梯（1 来回，无需衰减） |
| 抖动位移（px，水平轴） | `dx(t) = axis · round(A · sin(2π·t/P) · (1 − t/D))` | A=2px，P=0.07s，D=0.15s | axis=+1：t=P/4 → **+2**；t=3P/4 → **−1**；t=5P/4 → **+1**；t=7P/4 → 0；t=D → 0（约 2 来回线性衰减） |
| 抖动水平轴 | `resolve(attackerX, targetX, targetId)`：攻击者严格在左 → +1，在右 → −1，同列或无攻击者视图 → `fallback(targetId)` = 偶 +1 / 奇 −1 | — | 确定性回退，不引入随机源（sourceId=-1 来自 `DamagePipeline.java:40-41`） |

整数阶梯的取整边界均远离 0.5（1.767 / 0.833 / 0.367 等），float 计时累计误差（~1e-7 量级）不影响 `Math.round` 结果——测试断言精确 int 稳定可靠（§8-W5）。

### 5.3 实现口径（文档未明说、本次定的执行细节——评审重点）

1. **旋转屏幕角符号（y 向上坐标系）**：本项目虚拟坐标 y 向上（FitViewport + OrthographicCamera 默认 glOrtho y-up；互证：血条 `cy+17` 在头部、星级点 `cy−19`"脚下"在下，`UnitView.java:121-129`）。libGDX 正角 = 逆时针 → **玩家单位（朝右）前倾 = 顺时针 = 负角；敌方（flipX 镜像朝左）前倾 = 正角**。故 `drawBody` 内 `degrees = -anim.attackSwingDegrees() * (enemy ? -1f : 1f)`，公式访问器 `attackSwingDegrees()` 保持"正 = 前倾"的朝向无关语义。
2. **TRANSLATE 沿朝向符号**：玩家前进 = +x → `dx = +attackSwingDx()`；敌方前进 = −x → 取反。注意与旋转的 facing 系数**互为相反**（旋转是角、平移是位移，屏幕语义不同）——`drawBody` 内并列写清，两分支各自注释。
3. **抖动首拍方向语义**：`sin` 首拍为正 → `axis=+1` 首拍向右。定 `resolve`：攻击者在受击者左侧 → +1（首拍向受力反方向弹开，受力感可读）。抖动本身正弦双向往复，轴符号只决定首拍相位，不影响结构。
4. **死亡双保险**：`onEvent(UNIT_DIED)` 分支内清零两计时（GDD FP3"立即中止并清零"）+ 两个 trigger 方法开头 `if (current == Anim.DEATH) return;`（防"出手同拍被反杀"事件序下的残留触发）。两处均为一行级、有测试。
5. **0 角早退快路径**：ROTATE 模式下 `degrees == 0f`（未触发时访问器返回字面量 0f）走既有四参 draw——"幅度常量归零后画面与现状逐帧一致"的强保证（同一条代码路径）；摆动进行中仅过 T/2 一瞬为 0，无观感影响。
6. **触发帧时序**：`drawBattle` 先 `forEachNew` 路由触发（`BattleRenderer.java:222-227`）再 update/draw——触发当帧 t=0 偏移为 0（sin 起点即 0），动作次帧起步，与白闪"当帧即满亮"不同属正常物理（位移类叠加从 0 起步）。
7. **`hitShakeDx()` 的轴归一**：`triggerHitShake(axis)` 内 `axis < 0 ? -1 : 1`（0 归一 +1）——防御渲染层传 0（resolve 同列已回退，理论不达，兜底）。
8. **触发调用合并**：`routeEvent` 与 `onDamaged` 各只追加**一行**（`attacker.anim().triggerAttackSwing();` / `target.anim().triggerHitShake(...)`），不重构既有 case 结构——评审 diff 最小化。
9. **常量归属**：全部落 `UnitAnimState` 常量区（GDD §8 建议，与 `HIT_FLASH_SECONDS` 同处）；`AttackSwingMode` 为其嵌套 public 枚举。不入 GameBalance（对战数值域）、不入配置文件。

### 5.4 与设计文档的差异声明（如实记录，不改设计文档）

| # | 差异 | 处置 |
|---|------|------|
| D1 | GDD §5 FP1 行文"以精灵底部中心 `(cx, cy + size/2)` 为轴心"——本项目 y 向上坐标系中 `cy + size/2` 是精灵**顶部**中心；GDD 同节代码草图 `draw(region, x, y, originX = size/2, originY = 0, ...)` 中 originY=0（配 `y = cy − size/2`）指向的恰是**底部**中心。行文与草图不一致，意图（"底部中心为轴"、星级点在脚下）无歧义 | **按底部中心 `(cx, cy − size/2)` 实现**（与 GDD 代码草图一致）。建议完工后回写 GDD 该行坐标笔误（一行字，executor 顺手项，非阻塞） |

---

## 6. 改动点清单（评审主入口）

改动共 6 个 CP，按依赖顺序排列：CP5/CP6 测试先行（TDD RED）→ CP1/CP2 实现（GREEN）→ CP3/CP4 绘制与接线。同一段代码的完整改动只出现在一个 CP。

### CP1. UnitAnimState 新增攻击摆动/受击抖动叠加层（常量 + 计时器 + 触发器 + 公式访问器 + 死亡清零）

- **类型**：修改类（5 处小改，互不重叠）
- **位置**：`core/src/main/java/com/voidvvv/kz_auto_chess_n/render/board/UnitAnimState.java`
- **改动说明**：照 HitFlash 三件套范式（常量 `:22-23` → `triggerHitFlash :97-100` → `hitFlashRatio :102-105`）新增两个叠加层。术语：**攻击摆动**（attack swing，出手时"扇扇子"）与**受击抖动**（hit shake，被直伤命中时整数像素往复）。全部公式方法纯计算零 Gdx，headless 可测。改动点 a/b/c/d/e 依次为：类注释、常量区、字段区、DEATH 清零、update 计时、新方法块。
- **代码**：

a. 类注释（`UnitAnimState.java:5-11`）——叠加层清单补一句：

修改前：
```java
/**
 * 单位动画 FSM（render §5.1；纯 Java 可测，零 Gdx）。
 *
 * <p>优先级：Death 锁定 &gt; Attack/Cast &gt; Walk &gt; Idle；HitFlash 为叠加层独立计时，
 * 不占状态位。事件 → 状态的"归属哪个单位"路由（sourceId/targetId）由渲染层完成，
 * 本类只按事件类型转移。死亡淡出 0.5s（口径 #13 占位表现）。
 */
```
修改后：
```java
/**
 * 单位动画 FSM（render §5.1；纯 Java 可测，零 Gdx）。
 *
 * <p>优先级：Death 锁定 &gt; Attack/Cast &gt; Walk &gt; Idle；HitFlash 为叠加层独立计时，
 * 不占状态位。攻击摆动与受击抖动（attack_feedback FP1/FP2）同为叠加层：独立计时、
 * 重复触发刷新满、施法不抑制、死亡立即清零且死后忽略触发。
 * 事件 → 状态的"归属哪个单位"路由（sourceId/targetId）由渲染层完成，
 * 本类只按事件类型转移。死亡淡出 0.5s（口径 #13 占位表现）。
 */
```

b. 常量区（`UnitAnimState.java:22-25` 之后追加；缺省值逐字取自 GDD §8 参数表 #1~#9）：

修改前：
```java
    /** 受击白闪时长（叠加层） */
    public static final float HIT_FLASH_SECONDS = 0.1f;
    /** 死亡缩放淡出时长（口径 #13） */
    public static final float DEATH_FADE_SECONDS = 0.5f;
```
修改后：
```java
    /** 受击白闪时长（叠加层） */
    public static final float HIT_FLASH_SECONDS = 0.1f;
    /** 死亡缩放淡出时长（口径 #13） */
    public static final float DEATH_FADE_SECONDS = 0.5f;

    // —— 攻击摆动 / 受击抖动（attack_feedback FP1/FP2；缺省值待试玩评审定稿，GDD §8） ——
    /** 摆动模式（裁决 C）：ROTATE = 底部中心轴小幅旋转（主模式，像素规则第三例外）；TRANSLATE = 沿朝向整数像素平移备选 */
    public enum AttackSwingMode { ROTATE, TRANSLATE }
    /** 模式切换常量（试玩评审对比后定稿，届时删常量定死单模式） */
    public static final AttackSwingMode ATTACK_SWING_MODE = AttackSwingMode.ROTATE;
    /** 旋转摆幅（度；上限 8°——render §八#3 第三例外） */
    public static final float ATTACK_SWING_DEGREES = 6f;
    /** 平移备选摆幅（像素，整数吸附） */
    public static final float ATTACK_SWING_PIXELS = 2f;
    /** 摆动全程时长（秒；恰好 1 个来回、幅值线性衰减） */
    public static final float ATTACK_SWING_SECONDS = 0.25f;
    /** 受击抖动幅度（像素，整数吸附） */
    public static final float HIT_SHAKE_PIXELS = 2f;
    /** 受击抖动时长（秒） */
    public static final float HIT_SHAKE_SECONDS = 0.15f;
    /** 受击抖动往复周期（秒；0.15/0.07 ≈ 2 个来回） */
    public static final float HIT_SHAKE_PERIOD_SECONDS = 0.07f;
```

c. 字段区（`UnitAnimState.java:33-37` 之后追加）：

修改前：
```java
    private Anim current = Anim.IDLE;
    private float animElapsed;
    private boolean moving;
    private float hitFlashTimer;
    private float deathElapsed;
```
修改后：
```java
    private Anim current = Anim.IDLE;
    private float animElapsed;
    private boolean moving;
    private float hitFlashTimer;
    private float deathElapsed;
    private float swingTimer;   // 攻击摆动剩余秒数（叠加层，attack_feedback FP1）
    private float shakeTimer;   // 受击抖动剩余秒数（叠加层，attack_feedback FP2）
    private int shakeAxis = 1;  // 受击抖动水平轴 ±1（渲染层定，含确定性回退；0 归一 +1）
```

d. DEATH 锁定分支清零（`UnitAnimState.java:61-65`）——GDD FP3"死亡态立即中止并清零"：

修改前：
```java
            case UNIT_DIED:
                current = Anim.DEATH; // 锁定
                animElapsed = 0f;
                deathElapsed = 0f;
                break;
```
修改后：
```java
            case UNIT_DIED:
                current = Anim.DEATH; // 锁定
                animElapsed = 0f;
                deathElapsed = 0f;
                swingTimer = 0f; // 死亡立即清零叠加反馈（attack_feedback FP3：不与缩放淡出叠加）
                shakeTimer = 0f;
                break;
```

e. update() 计时推进（`UnitAnimState.java:82-85`，与 hitFlashTimer 同款）：

修改前：
```java
    public void update(float dt) {
        if (hitFlashTimer > 0f) {
            hitFlashTimer = Math.max(0f, hitFlashTimer - dt);
        }
```
修改后：
```java
    public void update(float dt) {
        if (hitFlashTimer > 0f) {
            hitFlashTimer = Math.max(0f, hitFlashTimer - dt);
        }
        if (swingTimer > 0f) {
            swingTimer = Math.max(0f, swingTimer - dt);
        }
        if (shakeTimer > 0f) {
            shakeTimer = Math.max(0f, shakeTimer - dt);
        }
```

f. 新方法块（紧跟 `hitFlashRatio()` 之后、`deathFadeRatio()` 之前插入，即 `UnitAnimState.java:105` 与 `:107` 之间）：

```java
    // —— 攻击摆动 / 受击抖动叠加层（attack_feedback FP1/FP2/FP3） ——

    /** 攻击摆动触发（叠加层，重复触发刷新满；死亡态忽略——出手同拍被反杀不残留） */
    public void triggerAttackSwing() {
        if (current == Anim.DEATH) {
            return;
        }
        swingTimer = ATTACK_SWING_SECONDS;
    }

    /** 受击抖动触发（叠加层，重复触发刷新满；axis = 水平轴 ±1，0 归一 +1；死亡态忽略） */
    public void triggerHitShake(int axis) {
        if (current == Anim.DEATH) {
            return;
        }
        shakeAxis = axis < 0 ? -1 : 1;
        shakeTimer = HIT_SHAKE_SECONDS;
    }

    /** 旋转模式摆角（度）：A·sin(2πt/T)·(1−t/T)，t ∈ [0,T]——1 个来回、幅值线性衰减
     *  （t=T/4 前倾峰值 +0.75A、t=3T/4 回摆 −0.25A、终了归零）。正 = 前倾，
     *  屏幕角符号由 UnitView 按敌我朝向翻转（y 向上坐标系正角 = 逆时针）。未触发为 0。 */
    public float attackSwingDegrees() {
        if (swingTimer <= 0f) {
            return 0f;
        }
        float phase = (ATTACK_SWING_SECONDS - swingTimer) / ATTACK_SWING_SECONDS;
        return ATTACK_SWING_DEGREES * (float) Math.sin(Math.PI * 2.0 * phase) * (1f - phase);
    }

    /** 平移备选模式位移（整数像素阶梯）：round(A'·sin(2πt/T)) → +2→0→−2→0（1 来回）。
     *  正 = 前倾（沿自身朝向），敌方由 UnitView 取反。未触发为 0。 */
    public int attackSwingDx() {
        if (swingTimer <= 0f) {
            return 0;
        }
        float phase = (ATTACK_SWING_SECONDS - swingTimer) / ATTACK_SWING_SECONDS;
        return Math.round(ATTACK_SWING_PIXELS * (float) Math.sin(Math.PI * 2.0 * phase));
    }

    /** 受击抖动水平位移（整数像素）：axis·round(A·sin(2πt/P)·(1−t/D))——约 2 个来回线性衰减。
     *  未触发为 0。 */
    public int hitShakeDx() {
        if (shakeTimer <= 0f) {
            return 0;
        }
        float t = HIT_SHAKE_SECONDS - shakeTimer;
        float envelope = 1f - t / HIT_SHAKE_SECONDS;
        return shakeAxis * Math.round(HIT_SHAKE_PIXELS
                * (float) Math.sin(Math.PI * 2.0 * t / HIT_SHAKE_PERIOD_SECONDS) * envelope);
    }
```

- **测试要点**：见 §6.CP5（AttackFeedbackOverlayTest：公式探针/阶梯/重复触发/死亡清零与忽略/施法不抑制/并行/常量锚定）。

### CP2. 新建 HitShakeAxis：受击抖动水平轴决策纯函数

- **类型**：新建文件
- **位置**：`core/src/main/java/com/voidvvv/kz_auto_chess_n/render/board/HitShakeAxis.java`
- **改动说明**：GDD FP2"方向：基于攻击者来向的水平轴……攻击者取不到或 sourceId 为 -1 时按受击者 id 奇偶定左右（确定性回退，不引入随机源）"。抽纯函数类而非内联在 BattleRenderer，沿 render/board"逻辑可测类不进 Gdx 类"惯例（LerpMotion / BoardGeometry / UnitAnimState 先例）——BattleRenderer 无 headless 测试面（仓库无 BattleRendererTest，渲染测试均纯逻辑，见 §3.2），方向决策必须可单测。语义约定（§5.3-3）：+1 = 首拍向右。sourceId = -1 的来源是 `DamagePipeline.applyDirectHit` 的 `attacker == null ? -1`（`DamagePipeline.java:40-41`）。
- **代码**（完整文件）：

```java
package com.voidvvv.kz_auto_chess_n.render.board;

/**
 * 受击抖动水平轴决策（纯函数，attack_feedback FP2；零 Gdx 可测）。
 *
 * <p>+1 = 首拍向右、−1 = 首拍向左。抖动本身正弦双向往复，轴符号只决定首拍相位
 * （缺省取「受力反方向弹开」：攻击者在受击者左侧 → 首拍向右）。无随机源——
 * 攻击者视图取不到（sourceId = -1，DamagePipeline.applyDirectHit）或与受击者
 * 同列时，按受击者 id 奇偶确定性回退。
 */
public final class HitShakeAxis {

    private HitShakeAxis() {
    }

    /** 有攻击者视图：攻击者严格在受击者左侧 → +1；右侧 → −1；同列（x 相等）走奇偶回退 */
    public static int resolve(int attackerX, int targetX, int targetId) {
        if (attackerX != targetX) {
            return attackerX < targetX ? 1 : -1;
        }
        return fallback(targetId);
    }

    /** 无攻击者回退：受击者 id 偶数 → +1、奇数 → −1（确定性，不引入随机源） */
    public static int fallback(int targetId) {
        return (targetId & 1) == 0 ? 1 : -1;
    }
}
```

- **测试要点**：见 §6.CP6（来向左右/同列奇偶/无攻击者回退/恒 ±1）。

### CP3. UnitView 本体与白闪两次 draw 收敛到 drawBody 统一变换（抖动平移 + 双模式摆动）

- **类型**：修改方法 + 新增私有方法
- **位置**：`core/src/main/java/com/voidvvv/kz_auto_chess_n/render/board/UnitView.java`——`draw` 内 `:99-105` 两处四参 draw 调用替换；新私有方法插在 `draw` 与 `drawBars` 注释块之间（`:114` 与 `:116` 之间）。
- **改动说明**：把"受击抖动水平偏移（整数像素）"与"攻击摆动（ROTATE 九参旋转 / TRANSLATE 平移）"叠加进本体与白闪层共用的绘制路径。GDD 用语 ↔ 代码：**白闪层** = `hitFlashRatio() > 0f` 时的第二次 draw（`UnitView.java:101-103`）；**底部中心轴** = 九参 draw `originX = size/2, originY = 0`（y 向上坐标系下绘制原点即精灵底边中点，见 §5.4 D1 勘误）；**敌方镜像** = `enemy` 字段（构造时 `unit.getSide() == Side.ENEMY`，`UnitView.java:46`）+ region flipX。血条/色框（`:110-113`）与飘字/落点闪光锚点（BattleRenderer 侧）继续用未变换 cx/cy——本 CP 不触碰。`degrees == 0f` 早退四参路径保证未触发时与现状逐帧一致（§5.3-5）。渲染段零分配（无新对象）。
- **代码**：

`draw` 内两处调用替换（`UnitView.java:99-105`）：

修改前：
```java
        batch.setColor(1f, 1f, 1f, alpha);
        batch.draw(region, cx - size / 2f, cy - size / 2f, size, size);
        if (anim.hitFlashRatio() > 0f) { // 受击白闪叠加层
            batch.setColor(1f, 1f, 1f, anim.hitFlashRatio() * 0.8f * alpha);
            batch.draw(region, cx - size / 2f, cy - size / 2f, size, size);
        }
        batch.setColor(WHITE);
```
修改后：
```java
        batch.setColor(1f, 1f, 1f, alpha);
        drawBody(batch, region, cx, cy, size);
        if (anim.hitFlashRatio() > 0f) { // 受击白闪叠加层（与本体共用同一变换）
            batch.setColor(1f, 1f, 1f, anim.hitFlashRatio() * 0.8f * alpha);
            drawBody(batch, region, cx, cy, size);
        }
        batch.setColor(WHITE);
```

新私有方法（插在 `draw` 结束后、`// —— 血条` 注释前）：

```java
    /**
     * 本体精灵绘制：先叠加受击抖动水平偏移（整数像素），再叠加攻击摆动
     * （ROTATE：底部中心为轴小幅旋转——像素规则第三例外，render §八#3；
     * TRANSLATE：沿朝向整数像素平移探身）。白闪层复用同一变换（attack_feedback FP1/FP2）。
     * 摆动以单位自身朝向为正：y 向上坐标系正角 = 逆时针，玩家单位（朝右）前倾 = 负角，
     * 敌方经 flipX 镜像后取反（左右观感对称，不取攻击目标方位）。
     */
    private void drawBody(SpriteBatch batch, TextureRegion region, int cx, int cy, float size) {
        float x = cx + anim.hitShakeDx() - size / 2f;
        float y = cy - size / 2f;
        if (UnitAnimState.ATTACK_SWING_MODE == UnitAnimState.AttackSwingMode.TRANSLATE) {
            int swingDx = anim.attackSwingDx() * (enemy ? -1 : 1); // 沿朝向前倾（敌方前进 = −x）
            batch.draw(region, x + swingDx, y, size, size);
            return;
        }
        float degrees = -anim.attackSwingDegrees() * (enemy ? -1f : 1f); // 正 = 前倾
        if (degrees == 0f) {
            batch.draw(region, x, y, size, size); // 未摆动：既有四参路径（关断/未触发与现状一致）
            return;
        }
        batch.draw(region, x, y, size / 2f, 0f, size, size, 1f, 1f, degrees); // 底部中心轴
    }
```

- **测试要点**：UnitView 绑 SpriteBatch/Assets（Gdx GL），无 headless 测试面（仓库先例：render 测试均纯逻辑，§3.2）。本 CP 的公式正确性由 CP5 覆盖（公式全在 UnitAnimState）；朝向符号/早退路径/白闪同变换由 §7 手验清单覆盖（M1~M3、M7）。

### CP4. BattleRenderer 两处事件接线（ATTACK_LAUNCHED 摆动 + HIT 抖动含方向轴）

- **类型**：修改方法 ×2 + 新增私有方法 ×1
- **位置**：`core/src/main/java/com/voidvvv/kz_auto_chess_n/render/board/BattleRenderer.java`——`routeEvent` 的 ATTACK_LAUNCHED case（`:251-256`）；`onDamaged` 的 HIT 分支（`:294-296`）；新私有方法插在 `onDamaged` 与 `drawUnitFrame` 之间（`:306` 与 `:308` 之间）。
- **改动说明**：两处各追加一行触发调用 + 一个方向轴私有方法（调 CP2 的 HitShakeAxis）。术语：**sourceId**（HIT 的攻击者 id，-1 = 无攻击者）、**targetId**（受击者 id）取自 `CombatEvent`（`CombatEvent.java:95-96`）。近战当拍 ATTACK_LAUNCHED 与 HIT 同发：摆动只认 ATTACK_LAUNCHED case（`:251-255`），HIT 的攻方动画路由（`:264-268`）不动——近战不重复触发。HEALED/SHIELDED 走 `onDamaged` 但 `getType() != HIT`，在 if 分支外天然不抖。`x` 复用 `onDamaged` 已算好的受击者锚点（`:292`）。import 段新增 `HitShakeAxis` 无需（同包 `render.board`）。
- **代码**：

a. ATTACK_LAUNCHED case（`BattleRenderer.java:250-256`）：

修改前：
```java
        switch (event.getType()) {
            case ATTACK_LAUNCHED:
                UnitView attacker = unitViews.get(event.getSourceId());
                if (attacker != null) {
                    attacker.anim().onEvent(CombatEvent.Type.ATTACK_LAUNCHED);
                }
                break;
```
修改后：
```java
        switch (event.getType()) {
            case ATTACK_LAUNCHED:
                UnitView attacker = unitViews.get(event.getSourceId());
                if (attacker != null) {
                    attacker.anim().onEvent(CombatEvent.Type.ATTACK_LAUNCHED);
                    attacker.anim().triggerAttackSwing(); // 攻击摆动叠加层（attack_feedback FP1；近战/远程统一出口）
                }
                break;
```

b. onDamaged 的 HIT 分支（`BattleRenderer.java:288-296`）：

修改前：
```java
        UnitView target = unitViews.get(event.getTargetId());
        if (target == null) {
            return;
        }
        float x = target.virtualX(renderClock);
        float y = target.virtualY(renderClock);
        if (event.getType() == CombatEvent.Type.HIT) {
            target.anim().triggerHitFlash();
        }
```
修改后：
```java
        UnitView target = unitViews.get(event.getTargetId());
        if (target == null) {
            return;
        }
        float x = target.virtualX(renderClock);
        float y = target.virtualY(renderClock);
        if (event.getType() == CombatEvent.Type.HIT) {
            target.anim().triggerHitFlash();
            target.anim().triggerHitShake(hitShakeAxis(event, target, x, renderClock)); // 受击抖动（attack_feedback FP2）
        }
```

c. 新私有方法（插在 `onDamaged` 方法结束后、`drawUnitFrame` javadoc 前）：

```java
    /** 受击抖动水平轴（attack_feedback FP2）：攻击者来向（在受击者左 → +1，首拍向受力反方向弹开）；
     *  攻击者视图缺失（sourceId = -1，DamagePipeline.java:40-41）→ 按受击者 id 奇偶确定性回退，不引入随机源 */
    private int hitShakeAxis(CombatEvent event, UnitView target, int targetX, float renderClock) {
        UnitView attacker = unitViews.get(event.getSourceId());
        if (attacker == null) {
            return HitShakeAxis.fallback(event.getTargetId());
        }
        return HitShakeAxis.resolve(attacker.virtualX(renderClock), targetX, event.getTargetId());
    }
```

- **测试要点**：方向决策逻辑由 CP6 覆盖；接线本身（各一行）无 headless 测试面（同 CP3 说明），由 §7 手验清单 M1/M2/M4/M5/M6 覆盖。既有行为回归：routeEvent 其余 case、onDamaged 飘字/落点闪光路径零变化（diff 审查确认）。

### CP5. 新建 AttackFeedbackOverlayTest（TDD 先行，对应 CP1）

- **类型**：新建文件
- **位置**：`core/src/test/java/com/voidvvv/kz_auto_chess_n/render/AttackFeedbackOverlayTest.java`（沿 `render/UnitAnimStateTest.java` 的包放置惯例测 `render/board` 类）
- **改动说明**：覆盖公式探针值（§5.2 表）、整数阶梯、重复触发刷新、不占状态位、施法不抑制、死亡清零与死后忽略、并行不互斥、常量锚定（防手滑改常量导致公式漂移）。探针取值避开取整边界（§5.2 末行），float 噪声安全。摆动探针 t = T/4、T/2、3T/4 均为 2 的幂次除商（0.0625/0.125/0.1875），二进制精确。
- **代码**（完整文件）：

```java
package com.voidvvv.kz_auto_chess_n.render;

import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.render.board.UnitAnimState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 攻击摆动 / 受击抖动叠加层测试（attack_feedback FP1/FP2/FP3）：
 * 公式探针（1 来回衰减正弦 / 整数像素阶梯）、重复触发刷新满、不占状态位、
 * 施法不抑制、死亡立即清零且死后忽略触发、摆动与抖动并行不互斥、常量锚定。
 */
class AttackFeedbackOverlayTest {

    private static final float T = UnitAnimState.ATTACK_SWING_SECONDS;     // 0.25f
    private static final float D = UnitAnimState.HIT_SHAKE_SECONDS;        // 0.15f
    private static final float P = UnitAnimState.HIT_SHAKE_PERIOD_SECONDS; // 0.07f

    /** 触发摆动并推进到 elapsed 秒 */
    private static UnitAnimState swingAt(float elapsed) {
        UnitAnimState state = new UnitAnimState();
        state.triggerAttackSwing();
        state.update(elapsed);
        return state;
    }

    /** 触发抖动（给定轴）并推进到 elapsed 秒 */
    private static UnitAnimState shakeAt(float elapsed, int axis) {
        UnitAnimState state = new UnitAnimState();
        state.triggerHitShake(axis);
        state.update(elapsed);
        return state;
    }

    @Test
    @DisplayName("未触发：摆角/摆动位移/抖动位移全为 0")
    void idleStateHasNoOverlayOffset() {
        UnitAnimState state = new UnitAnimState();
        assertThat(state.attackSwingDegrees()).isZero();
        assertThat(state.attackSwingDx()).isZero();
        assertThat(state.hitShakeDx()).isZero();
    }

    @Test
    @DisplayName("旋转公式：T/4 前倾峰值 +0.75A=4.5°、T/2 归零、3T/4 回摆 −0.25A=−1.5°、T 播完归零")
    void swingDegreesFormula() {
        assertThat(swingAt(T / 4f).attackSwingDegrees()).isCloseTo(4.5f, within(1e-4f));
        assertThat(swingAt(T / 2f).attackSwingDegrees()).isCloseTo(0f, within(1e-4f));
        assertThat(swingAt(3f * T / 4f).attackSwingDegrees()).isCloseTo(-1.5f, within(1e-4f));
        assertThat(swingAt(T).attackSwingDegrees()).isZero();
    }

    @Test
    @DisplayName("平移阶梯：+2 → 0 → −2 → 0（1 个来回，整数像素）")
    void swingTranslateLadder() {
        assertThat(swingAt(T / 4f).attackSwingDx()).isEqualTo(2);
        assertThat(swingAt(T / 2f).attackSwingDx()).isEqualTo(0);
        assertThat(swingAt(3f * T / 4f).attackSwingDx()).isEqualTo(-2);
        assertThat(swingAt(T).attackSwingDx()).isEqualTo(0);
    }

    @Test
    @DisplayName("抖动阶梯与线性衰减（axis=+1）：t=P/4 → +2、3P/4 → −1、5P/4 → +1、7P/4 → 0、播完 0")
    void hitShakeLadderDecays() {
        assertThat(shakeAt(P / 4f, 1).hitShakeDx()).isEqualTo(2);
        assertThat(shakeAt(P / 2f, 1).hitShakeDx()).isEqualTo(0);
        assertThat(shakeAt(3f * P / 4f, 1).hitShakeDx()).isEqualTo(-1);
        assertThat(shakeAt(5f * P / 4f, 1).hitShakeDx()).isEqualTo(1);
        assertThat(shakeAt(7f * P / 4f, 1).hitShakeDx()).isEqualTo(0);
        assertThat(shakeAt(D, 1).hitShakeDx()).isEqualTo(0);
    }

    @Test
    @DisplayName("抖动方向轴：axis=−1 全程取反；axis=0 归一为 +1")
    void hitShakeAxisSign() {
        assertThat(shakeAt(P / 4f, -1).hitShakeDx()).isEqualTo(-2);
        assertThat(shakeAt(3f * P / 4f, -1).hitShakeDx()).isEqualTo(1);
        assertThat(shakeAt(P / 4f, 0).hitShakeDx()).isEqualTo(2);
    }

    @Test
    @DisplayName("重复触发刷新满计时（高攻速连击 → 持续小幅摆动；同 HitFlash 惯例）")
    void retriggerRefreshes() {
        UnitAnimState state = new UnitAnimState();
        state.triggerAttackSwing();
        state.update(T / 2f);
        state.triggerAttackSwing(); // 刷新
        state.update(T / 4f);
        assertThat(state.attackSwingDegrees()).isCloseTo(4.5f, within(1e-4f));

        state.triggerHitShake(1);
        state.update(D / 2f);
        state.triggerHitShake(1); // 刷新
        state.update(P / 4f);
        assertThat(state.hitShakeDx()).isEqualTo(2);
    }

    @Test
    @DisplayName("不占状态位：触发摆动/抖动后动画态仍 Idle")
    void overlaysDoNotOccupyAnimState() {
        UnitAnimState state = new UnitAnimState();
        state.triggerAttackSwing();
        state.triggerHitShake(1);
        assertThat(state.current()).isEqualTo(UnitAnimState.Anim.IDLE);
    }

    @Test
    @DisplayName("施法不抑制：CAST 只切动画态，摆动叠加层照播（FP3）")
    void castDoesNotSuppressSwing() {
        UnitAnimState state = new UnitAnimState();
        state.triggerAttackSwing();
        state.onEvent(CombatEvent.Type.CAST);
        assertThat(state.current()).isEqualTo(UnitAnimState.Anim.CAST);
        state.update(T / 4f);
        assertThat(state.attackSwingDegrees()).isCloseTo(4.5f, within(1e-4f));
    }

    @Test
    @DisplayName("死亡立即清零：DEATH 锁定瞬间摆动/抖动中止（不与缩放淡出叠加）")
    void deathClearsOverlays() {
        UnitAnimState state = new UnitAnimState();
        state.triggerAttackSwing();
        state.triggerHitShake(1);
        state.update(0.05f);
        assertThat(state.attackSwingDegrees()).isNotZero(); // 前置：确实在播
        state.onEvent(CombatEvent.Type.UNIT_DIED);
        assertThat(state.attackSwingDegrees()).isZero();
        assertThat(state.attackSwingDx()).isZero();
        assertThat(state.hitShakeDx()).isZero();
    }

    @Test
    @DisplayName("死亡后忽略新触发：出手同拍被反杀不残留反馈")
    void deathIgnoresNewTriggers() {
        UnitAnimState state = new UnitAnimState();
        state.onEvent(CombatEvent.Type.UNIT_DIED);
        state.triggerAttackSwing();
        state.triggerHitShake(1);
        assertThat(state.attackSwingDegrees()).isZero();
        assertThat(state.hitShakeDx()).isZero();
    }

    @Test
    @DisplayName("并行不互斥：同一单位刚出手即被打，两计时器各播各的")
    void swingAndShakeRunInParallel() {
        UnitAnimState state = new UnitAnimState();
        state.triggerAttackSwing();
        state.triggerHitShake(1);
        state.update(P / 4f); // t=0.0175：抖动首拍 +2，摆动早期前倾约 2.376°
        assertThat(state.hitShakeDx()).isEqualTo(2);
        assertThat(state.attackSwingDegrees()).isCloseTo(2.376f, within(1e-2f));
    }

    @Test
    @DisplayName("常量锚定（GDD §8 缺省值）：ROTATE / 6° / 2px / 0.25s / 2px / 0.15s / 0.07s")
    void constantsMatchGddDefaults() {
        assertThat(UnitAnimState.ATTACK_SWING_MODE).isEqualTo(UnitAnimState.AttackSwingMode.ROTATE);
        assertThat(UnitAnimState.ATTACK_SWING_DEGREES).isEqualTo(6f);
        assertThat(UnitAnimState.ATTACK_SWING_PIXELS).isEqualTo(2f);
        assertThat(UnitAnimState.ATTACK_SWING_SECONDS).isEqualTo(0.25f);
        assertThat(UnitAnimState.HIT_SHAKE_PIXELS).isEqualTo(2f);
        assertThat(UnitAnimState.HIT_SHAKE_SECONDS).isEqualTo(0.15f);
        assertThat(UnitAnimState.HIT_SHAKE_PERIOD_SECONDS).isEqualTo(0.07f);
    }
}
```

- **测试要点**：本文件即测试（TDD：先于 CP1 落盘跑 RED，CP1 完成后 GREEN）。探针值推导见 §5.2；`swingAndShakeRunInParallel` 的 2.376° = 6°·sin(2π·0.07)·0.93（t=0.0175）。

### CP6. 新建 HitShakeAxisTest（TDD 先行，对应 CP2）

- **类型**：新建文件
- **位置**：`core/src/test/java/com/voidvvv/kz_auto_chess_n/render/HitShakeAxisTest.java`
- **改动说明**：覆盖来向左右、同列奇偶回退、无攻击者回退（含负 id 防御）、返回值恒 ±1。
- **代码**（完整文件）：

```java
package com.voidvvv.kz_auto_chess_n.render;

import com.voidvvv.kz_auto_chess_n.render.board.HitShakeAxis;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 受击抖动水平轴决策测试（attack_feedback FP2）：攻击者来向 / 同列奇偶回退 /
 * 无攻击者（sourceId=-1 / 视图缺失）奇偶回退 / 返回值恒 ±1。
 */
class HitShakeAxisTest {

    @Test
    @DisplayName("攻击者在受击者左侧 → +1（首拍向右弹开）；右侧 → −1")
    void resolveByAttackerSide() {
        assertThat(HitShakeAxis.resolve(100, 200, 7)).isEqualTo(1);
        assertThat(HitShakeAxis.resolve(300, 200, 8)).isEqualTo(-1);
    }

    @Test
    @DisplayName("攻击者与受击者同列（x 相等）→ 按受击者 id 奇偶回退")
    void resolveSameColumnFallsBackToParity() {
        assertThat(HitShakeAxis.resolve(200, 200, 8)).isEqualTo(1);
        assertThat(HitShakeAxis.resolve(200, 200, 7)).isEqualTo(-1);
    }

    @Test
    @DisplayName("无攻击者回退：id 偶 +1 / 奇 −1（确定性，无随机源；负 id 防御）")
    void fallbackByParity() {
        assertThat(HitShakeAxis.fallback(2)).isEqualTo(1);
        assertThat(HitShakeAxis.fallback(3)).isEqualTo(-1);
        assertThat(HitShakeAxis.fallback(-4)).isEqualTo(1);
        assertThat(HitShakeAxis.fallback(-3)).isEqualTo(-1);
    }

    @Test
    @DisplayName("返回值恒为 ±1（不允许 0 或其他幅度）")
    void alwaysUnitVector() {
        for (int attackerX = -5; attackerX <= 5; attackerX++) {
            for (int targetId = 0; targetId < 5; targetId++) {
                int axis = HitShakeAxis.resolve(attackerX, 0, targetId);
                assertThat(Math.abs(axis)).isEqualTo(1);
            }
        }
    }
}
```

- **测试要点**：本文件即测试（先于 CP2 落盘跑 RED，CP2 完成后 GREEN）。

---

## 7. 分阶段任务拆解（executor 按序执行）

| 任务 | 所含 CP | 前置 | 验收标准 |
|------|------|------|------|
| T1. 叠加层状态机（TDD） | CP5 → CP1 | 无 | ① CP5 测试先落盘跑 RED（新方法不存在编译失败即红）；② CP1 完成后 AttackFeedbackOverlayTest 12 例全绿；③ 既有 UnitAnimStateTest 10 例全绿（叠加层不破坏 FSM 既有行为）；④ 全量 733 基线 + 新增零失败（gradle XML 聚合计数核对：退出码 0、TEST-*.xml 无 failure/error） |
| T2. 抖动方向轴（TDD） | CP6 → CP2 | 无（可与 T1 并行） | ① CP6 先落盘 RED；② CP2 完成后 4 例全绿；③ 全量回归零失败 |
| T3. 绘制变换 | CP3 | T1 | 编译通过；UnitView 仅 draw 两处调用替换 + drawBody 新增，其余零 diff（`git diff` 审查）；手验 M3/M7（见下） |
| T4. 事件接线 | CP4 | T1、T2 | 编译通过；BattleRenderer 仅 3 处小 diff（两 case 各一行 + 一个私有方法）；手验 M1/M2/M4/M5/M6 |
| T5. 回归与手验收口 | — | T1~T4 | ① 全量测试 XML 聚合零失败零忽略，总数 = 733 + 16（AttackFeedbackOverlayTest 12 + HitShakeAxisTest 4）；② 手验清单 M1~M8 全过；③ （可选顺手项）回写 GDD §5 FP1 底部中心坐标笔误（§5.4 D1） |

**手验清单（T5，lwj3 跑真机）**：

- M1 出手摆动：近战单位出手"扇一下"（前倾约 4.5° 回摆归位，全程约 0.25s）；远程单位发射瞬间同样摆动；高攻速单位连续出手呈持续小幅摆动。
- M2 敌我镜像：敌方单位摆动方向与玩家镜像对称（均朝各自面朝方向前倾）；受击抖动沿攻击者来向水平轴。
- M3 不随动：摆动/抖动进行中血条、能量条、星级点、敌我色框原地不动；伤害飘字与落点闪光锚点不随抖动漂移。
- M4 白闪跟随：受击瞬间白闪层与本体同角度摆动/同位移抖动（同一变换）。
- M5 不触发面：POISON/BLEED 心跳掉血不抖不白闪；治疗（绿色飘字）与护盾落地不抖；技能施放只起手闪光 + cast 动画，无摆动。
- M6 死亡中止：单位摆动/抖动进行中被击杀，立即停止并进入死亡缩放淡出（无旋转/抖动叠加）。
- M7 关断等价：把 `ATTACK_SWING_DEGREES`、`ATTACK_SWING_PIXELS`、`HIT_SHAKE_PIXELS` 临时改 0 重跑——战斗画面与现状无可见差异（改回）。
- M8 模式切换：`ATTACK_SWING_MODE` 改 `TRANSLATE` 重跑——出手呈 ±2 整数像素阶梯探身（阶梯感清晰、无亚像素抖动），敌方沿其朝向；对比后改回 ROTATE（终稿待试玩评审）。

---

## 8. 风险与开放问题（WARNING，不阻塞）

| # | 风险/疑点 | 说明与缓解 |
|---|------|------|
| W1 | GDD §5 FP1 行文轴心坐标 `(cx, cy + size/2)` 在 y 向上坐标系中是顶部中心（与其自身代码草图 originY=0 矛盾） | §5.4 D1：按底部中心 `(cx, cy − size/2)` 实现（意图与草图一致）；建议完工回写 GDD 一行 |
| W2 | 旋转产生非整数像素采样（像素规则第三例外本身） | 用户已裁决（GDD §10 C1）；Nearest 采样下小幅旋转的破碎感与弹道旋转先例（render §5.3）同口径；8° 上限内顶端位移约 4px，观感克制 |
| W3 | 九参 draw 常走路径的每帧开销 | 每单位每帧 2 次 draw 的矩阵合成可忽略（≤27 单位）；0 角早退保证未触发时走四参快路径（与现状同路径） |
| W4 | 11 项【待确认】参数终稿 | 全部为 `UnitAnimState` 静态常量，试玩评审改常量重跑即可（M7/M8 即验证流程），无结构风险；`ATTACK_SWING_MODE` 定稿后删常量 |
| W5 | float 计时累计误差 vs 整数阶梯断言 | 探针取整边界远离 0.5（1.767/0.833/0.367），误差 ~1e-7 量级不影响 `Math.round`；摆动主探针取 2 的幂次除商（二进制精确） |
| W6 | BattleRenderer 接线无自动化测试（Gdx GL 绑定，仓库无 BattleRendererTest 先例） | 接线仅两行 + 纯函数调用；方向逻辑全部抽 CP2 可测；接线正确性由手验 M1/M2/M4/M5/M6 覆盖；若未来建 Gdx-headless 测试基建可回补 |
| W7 | 摆动中施法（CAST 打断动画态但叠加层播完）的观感 | GDD FP3 明确定稿"不抑制"；0.25s 重叠极短；如试玩反馈不佳属参数/规则调整（记回 GDD 待确认项，不在本 spec 范围） |
| W8 | 同帧 AOE 多条 HIT 打同一目标 | 按 targetId 路由各自刷新满计时（render §4.3"同类动画取最新触发"惯例），表现为一次完整抖动——符合预期，无需去重 |

---

## 9. 附录：用户确认记录

本轮（2026-08-23）未发起新的澄清——无 BLOCKER 级矛盾。既往裁决存档：

1. **C1（2026-08-23，用户裁决，已写入 GDD §10）**：攻击摆动旋转 vs 像素规则禁旋转 → **C 方案双模式**。旋转主模式默认启用（底部中心轴、≤8°、~0.25s、1 来回衰减）+ 常量 `ATTACK_SWING_MODE` 一键切换平移备选（沿朝向 ±1~2 整数像素）；像素规则新增第三例外（仅默认模式），render_design §一#7/§5.3/§八#3 同步修订、§十二追加记录。本文档 §4-D1 落地。
2. **参数缺省消费（任务指示，2026-08-23）**：GDD §8 的 11 项【待确认】参数为非阻塞缺省值，直接按缺省消费并列成常量表（§6.CP1），不重新发起裁决；终稿由试玩评审定（M7/M8 验证流程）。本文档 §4-D2 落地。
