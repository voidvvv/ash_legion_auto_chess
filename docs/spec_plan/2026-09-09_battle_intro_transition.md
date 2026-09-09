# 开战转场「清场入阵」技术实施文档

> **日期**：2026-09-09　**状态**：待评审
> **依据**（均已经用户评审批准）：`docs/diagrams/battle_intro_slide_transition.md`（+ .html 三帧分镜，V1.0 定稿含 Q1~Q5 裁决记录）；`battle_design.md` V1.8 §二「开战转场（清场入阵）」（冻结清单六条 + 转场动作表为机制语义权威）/ §5.1 / §8.3 / §九；`render_design.md` V1.6 §5.6 / §九 / §十一；`gdd_idea_0.0.0.1.md` V0.17 §6.6/§6.7。
> **Q1 BLOCKER 裁决（2026-09-09，用户）**：**A——就地适配**。商店栏 ⑧（实机在底部条带）下滑离场/上滑归位；战斗 HUD（实机在顶部）自屏幕上缘外下落就位/上升退场；分镜「⑧ 与战斗 HUD 共用同一顶部槽位」前提废弃（两屏缘各自进出，观感为经营层/战斗层整层交换）；其余动作表行不变。依据链见附录 §9.1。
> **范围**：转场驱动器（UI 域 actor 姿态 + worldCamera zoom）＋ 反向转场（RESULT→SHOPPING）＋ 转场期输入禁用 ＋ SHOPPING 期 zoom 1.06 基线 ＋ BattleIntroBanner 退役 ＋ 常量改名改值 ＋ 测试修订 ＋ 旧 spec_plan 处理。
> **不含**：GDD 文档勘误（battle §二动作表「上滑」→「下滑」语境、render §5.6/分镜图按 A 口径修订）正由 gdd-writer 并行落位，本计划直接按 A 语义编写；§九 ⑥ 开战按钮行等既有文档漂移只登记不修（§8 W3）。

---

## 1. 背景与目标

原节奏案的开战铺垫是 3s 倒计时横幅（`BattleIntroBanner`，2026-09-02 落地）。用户 2026-09-09 看过三帧分镜后裁决改为**清场转场**：点击开战先播 0.6s（区间 0.4~0.8 待调）——备战 UI 滑出屏幕、战斗 HUD 落位、棋盘镜头轻微回正（zoom 1.06→1.0）、敌阵侦察虚影实体化，逻辑完全冻结；转场结束计时器从零蓄力，主循环起步。战毕回备战（RESULT→SHOPPING）播**反向同款转场**（Q3）。紧张感由「清场空拍 + 蓄力条充能 + 首刀延后」承担（battle §二）。

**成功标准**：
- 机制层零行为变更（除时长 3s→0.6s 与常量名）：intro 冻结门控原样复用，冻结清单（elapsed/RNG/事件/计时器/能量）逐条不破，确定性回放不含转场；
- 正向转场随 ×2 快进同步加速、随暂停/弹窗冻结，零特判；
- 反向转场在 RESULT→SHOPPING 翻转时播放一次，转场期输入禁用、结束后恢复；
- 备战期（zoom 1.06）拖拽布阵/出售命中换算不受 zoom 影响；
- 基线 **785 tests / 0 failures / 0 errors / 0 skipped**（当前工作区）语义保留，仅时序量随 0.6s 迁移；收尾 **791 全绿**（−1 横幅文案测试 +7 驱动器状态机测试）。

## 2. 术语与约定

| GDD 用语 | 代码标识符 | 位置 |
|---|---|---|
| 开战转场 / 清场入阵 | intro 门控：`BattleState.introRemaining` + `beginIntroCountdown / advanceIntroCountdown / isIntroCountdownActive / skipIntroCountdown`（**方法名不改**，口径 K1） | `entities/BattleState.java:29,93-143` |
| 转场时长 0.6s（0.4~0.8 待调） | `GameBalance.BATTLE_INTRO_TRANSITION_SECONDS`（采 GDD 暂名为终名，口径 K2） | `config/GameBalance.java`（CP1） |
| 镜头回正 zoom 1.06→1.0 | `GameBalance.SHOPPING_CAMERA_ZOOM`（备战基线）↔ 1.0（非 SHOPPING 稳态） | 同上（CP1） |
| ②③⑥ 左滑 / ⑧与HUD 屏缘滑距 | `BATTLE_TRANSITION_SLIDE_LEFT_PX = 140` / `BATTLE_TRANSITION_SLIDE_EDGE_PX = 72`（工作值待调） | 同上（CP1） |
| 转场驱动器 | `render/ui/BattleTransitionController`（新建，CP3） | 持 OrthographicCamera + 5 Actor |
| 正向进度（0→1） | `1 − introRemaining / BATTLE_INTRO_TRANSITION_SECONDS`（消费逻辑冻结门剩余值） | `BattleTransitionController.forwardProgress()` |
| 反向转场（Q3） | RESULT→SHOPPING 翻转触发、渲染侧自计时（`reverseRemaining`） | `BattleTransitionController.update()` |
| 转场期输入禁用 | `BattleTransitionController.isInputBlocked()` → boardProcessor `modalBlocked` 合流 + uiStage 全屏 `inputCatcher` | CP7 |
| 倒计时横幅（退役） | `render/ui/BattleIntroBanner`（删，CP8）+ `BATTLE_INTRO_COUNTDOWN_SECONDS` / `BATTLE_INTRO_GO_BEAT_SECONDS`（删，CP9） | — |
| 备战层 chrome（棋盘域） | ② 备战席槽+席上棋子 + ⑦ 出售区 + 布阵提示 + 敌阵虚影，现集中在 `BattleRenderer.drawShopping` | `render/board/BattleRenderer.java:164-217`（CP5） |

**约定**：Java 标识符全英文（中文只进注释与 `@DisplayName`）；全局数值只进 `GameBalance`；渲染层只读实体；像素位移一律取整吸附（render §八），zoom 插值是唯一例外（Q4 已批的镜头域）；测试验证纪律——gradle 成功时控制台零输出，用退出码 + `core/build/test-results/test/TEST-*.xml` 聚合计数核对。

## 3. 现状盘点（file:line 均为本次实读；代码行号在 T1 前稳定）

### 3.1 机制层（全部零改动复用）

| 资产 | 位置 | 说明 |
|---|---|---|
| intro 冻结门控 | `BattleState.java:29,93-95,131-143`（字段 + 2 读 3 写） | 机制原样；方法名保留（K1）；仅注释语境更新（CP2） |
| step 门控 | `BattleSystem.java:133-136`（isIntroCountdownActive → advance 后折返） | 冻结清单逐条成立；快进 = 同一 accumulator 通路（`BattleScreen.java:412`）零特判 |
| 布防点 | `BattleSystem.java:122` | 仅换常量名/值（CP10），位置与语义不变 |
| 计时器从零蓄力 | `BattleUnit.java` 构造器 + `attackInterval()` 乘 ×0.6 系数 | 2026-09-02 已落地，零改动（「去一留二」保留件） |
| 蓄力条 | `UnitView.drawBars()` 第四条微条 | 已落地；转场期 attackTimer=0 恒空条，符合 render §5.6 |
| 虚影实体化 | `BattleRenderer.syncBattleScope`（:106-132）+ `BattleEntryCells`（起始格=备战期位置） | UnitView 就位即现（部署单位/敌军起始格=战斗格，零位移），转场期虚影随层淡出即「实体化一拍」（K5） |
| 快进通路 | `BattleScreen.stepSimulation`（:412 `* speedFactor`） | 正向转场自动加速；反向在 SHOPPING 期无变速入口（HUD 已隐藏），天然一致（§8 W4 备注） |
| 投降/放弃门控 | `RunFlowSystem.java:84-90,111-118` | 零改动；转场期投降按钮被 Catcher 挡住即达成 GDD「转场结束后照常可用」 |
| 模态阻断挂点 | `BoardInputProcessor.java:60,90-92,170-173`（`modalBlocked` BooleanSupplier） | 已存在；CP7 仅在 BattleScreen 侧合流一个 `||` 条件 |

### 3.2 表现层（需改造/新建）

| 文件 | 现状 | 处置 | CP |
|---|---|---|---|
| `config/GameBalance.java:22-28` | 三常量（含两废弃） | 增 4 新常量（CP1）→ 删 2 废弃（CP9） | CP1/CP9 |
| `render/ui/BattleIntroBanner.java` + 其测试 | 上一轮未提交产物 | 直接删除 | CP8 |
| `screens/BattleScreen.java` | 横幅装配 5 处；可见性翻转 :332-334；无 zoom 变换 | 退役横幅 + 控制器装配 + 三处插桩 + 输入门控合流 | CP7 |
| `render/board/BattleRenderer.java` | `draw` 按 phase 二分支（:96-101）；②⑦提示虚影集中 `drawShopping`，换相即瞬隐 | 增转场姿态参数（benchOffsetX/chromeFadeAlpha），拆出 `drawShoppingChrome` | CP5 |
| `render/ui/BattleTransitionController.java` | 不存在 | 新建（纯 Java + scene2d Actor/OrthographicCamera，headless 可测） | CP3/CP4 |

### 3.3 关键代码事实（裁决 A 的落点）

- **商店栏 ⑧ 实机在底部**：`BattleScreen.java:226` addActor 后从未 setPosition → ShopBar 组停原点；子元素局部 y=4..60（`ShopBar.java:128`）→ 底部条带。旁证：`BoardGeometry.java:81` SHOP_HINT_Y 反馈记录「原 y=24 落在 ⑧ 商店栏带内被半透明卡牌遮挡」。
- **战斗 HUD 实机在顶部**：`BattleHud.java:43,46` 子元素 y=`VIRTUAL_H−46`=314、计时条 y=346 → battle §二「自顶部下落就位」逐字成立（A 裁决）。
- **③ 背包组停在原点、子元素绝对定位**（`InventoryPanel.java:25,90`「组保持原点」）→ 左滑 = 移组 x。⑥ 开战按钮同理（`ShoppingHud.java:34`，实机 x134..198 y88..128）。
- **⑨ 通知 / 顶栏**：转场不涉（动作表「不动」），零改动。
- **⑤ 羁绊面板**：既有 phase 联动置暗（`SynergyPanel.java:66-67`，BATTLE → alpha ×0.35）——「原地置暗保留」以既有语义兑现，无渐变（K7/W1）。
- **unproject 含 zoom**：`BoardInputProcessor.unproject`（:298-301）走 `boardViewport.unproject` → 相机逆变换自动吸收 zoom，备战期命中换算无需补偿（Q4 预判成立）。

## 4. 已确认决策

### 4.1 用户裁决（经 team-lead 交接，2026-09-09）

| # | 决策 | 值 |
|---|---|---|
| D1 | 分镜 Q1~Q5 | 滑出（②③ 随转场离场）/ 0.6s（0.4~0.8 待调）/ 反向同款转场 / 镜头回正保留 / 上摇变体否决 |
| D2 | Q1 BLOCKER（商店栏槽位） | **A——就地适配**：⑧ 底部下滑离场/上滑归位；战斗 HUD 顶部下落就位/上升退场；「交接同一槽位」废弃；其余动作行不变 |

### 4.2 实施口径（planner 裁决，语义从 GDD + 代码事实推导）

| # | 口径 | 依据 |
|---|---|---|
| K1 | intro 门控方法名/字段名**不改**（`introRemaining / beginIntroCountdown / advanceIntroCountdown / isIntroCountdownActive / skipIntroCountdown`）：battle §二实现落点与 render §5.6 驱动规格**逐字引用**这些标识符，改名即制造文档-代码漂移；仅注释更新语境。测试方法名同理保留 | battle §二「实现落点」原文、render §5.6「驱动」原文 |
| K2 | 常量终名采用 GDD 暂名 `BATTLE_INTRO_TRANSITION_SECONDS`；新增 `SHOPPING_CAMERA_ZOOM=1.06f`、`BATTLE_TRANSITION_SLIDE_LEFT_PX=140f`、`BATTLE_TRANSITION_SLIDE_EDGE_PX=72f`（后三为工作值待调，并入 render §十一待定项） | battle §8.3「终名由实施计划定」；铁律「全局数值只进 GameBalance」 |
| K3 | 正向进度源 = `introRemaining` 归一化（非渲染侧自计时）→ ×2 快进、暂停/弹窗冻结、MAX_TICKS 丢帧全部零特判自动正确；反向无逻辑计时器，渲染侧自计时，update 的 dt 传 `frozen ? 0 : delta`（沿 hoverPreview 冻结纪律，`BattleScreen.java:345` 同款） | battle §二快进交互 + 冻结清单 |
| K4 | 镜头 zoom 常驻归控制器：SHOPPING 稳态 1.06、其余相位稳态 1.0、转场线性插值；每帧 update 先于 `worldViewport.apply()` 写入（apply 会 camera.update() 使 zoom 同帧生效）；控制器中断自愈（相位离开转场窗即 snap 稳态） | render §5.6「镜头运动仅此一处回正插值」 |
| K5 | 正向转场期棋盘域只画 chrome（②⑦提示虚影随姿态退场），**不画玩家部署帧**（UnitView 已按备战期位置就位，避免叠影）；敌阵虚影随层淡出 = 「虚影实体化一拍」 | `syncBattleScope` 起始格事实；分镜 F1 |
| K6 | 输入封禁面最小化：棋盘域 = 既有 `modalBlocked` 合流 `isInputBlocked()`；UI 域 = 全屏透明 Catcher（沿 `ResultBanner.ClickCatcher` 先例，空 ClickListener 消费点击），仅转场窗口 touchable、uiStage 最顶层（挡变速/投降/商店/开战/TopBar 暂停钮）；dialogStage 弹窗不受影响（PickChest 链路安全）；Esc 暂停菜单与 L 通知回看保持可达（元层操作，不在 GDD「战场操作输入禁用」语义内）。投降/变速在转场结束帧即恢复（isInputBlocked 随 introRemaining 归零翻转） | render §5.6 交互条 |
| K7 | ③ 背包稳态可见性归控制器：**仅 SHOPPING 可见**（RESULT 亦隐——§九「仅 SHOPPING」的兑现；现状 RESULT 可见属旧行为，随本计划收敛）；⑤ 置暗复用既有 phase 联动（无渐变，W1 登记）；⑧⑥HUD 稳态可见性仍归 Screen 相位翻转行，控制器只在转场窗口覆写、稳态把位姿归零 | render §九 ②③ 行修订 |
| K8 | 0.6s 内时序配比缺省 = 全元件同一全窗线性（无错峰）；「交接滑切」处 ⑧ 与 HUD 同时运动即分镜语义。错峰/缓动留待调（render §十一） | 分镜 F1 表 |
| K9 | RESULT / RUN_END 稳态下 ②⑦提示虚影隐藏（chromeFadeAlpha=0）——§九「仅 SHOPPING」兑现；T3 中间态（中性字面量 1f）期间维持现状，CP7 后由控制器接管 | render §九 |
| K10 | `restartRun` 不复位控制器：RUN_END→SHOPPING ≠ RESULT→SHOPPING，反向不误播；快照续玩落地 SHOPPING 稳态同理不播 | RunFlowSystem :200,217 相位事实 |
| K11 | `BATTLE_INTRO_TRANSITION_SECONDS = 0f` 为软回滚杠杆（转场即关，行为回到「开战即解冻」，输入禁用窗口同步消失）；`SHOPPING_CAMERA_ZOOM = 1f` 关镜头回正 | 沿 pacing 计划回滚杠杆先例 |

## 5. 总体技术方案

架构与数据流（状态机图：`docs/diagrams/battle_intro_transition_flow.md` + `.html`）：

```
BattleScreen.render(delta)
  ├─ frozen 计算（:307，不变）
  ├─ phase 前提（原 :318 上提）
  ├─ transitionController.update(phase, introRemaining, frozen?0:delta)   ← CP7
  │     · 正向：introRemaining 归一化（消费逻辑冻结门，快进/冻结零特判）
  │     · 反向：prevPhase==RESULT && phase==SHOPPING 触发，reverseRemaining 自计时
  │     · 中断自愈：相位离开转场窗 → snap 稳态；写 worldCamera.zoom
  ├─ worldViewport.apply() / battleRenderer.draw(…, benchOffsetX, chromeFadeAlpha)  ← CP5/CP6/CP7
  │     · SHOPPING：drawShopping = chrome(姿态) + 部署帧(淡出系数) + 拖拽 overlay
  │     · BATTLE：chrome(姿态, 仅转场期 >0) + drawBattle（UnitView/弹道/特效/飘字，不变）
  ├─ 相位可见性翻转（:332-334，不变——稳态所有者仍是 Screen）
  ├─ transitionController.applyUiPose()                                    ← CP7
  │     · 转场窗：接管 ⑥⑧HUD③ 可见性与位姿（取整吸附）+ Catcher 显隐
  │     · 稳态：位姿归零，可见性交还相位行；③ 例外（K7，常驻归控制器）
  └─ uiStage.act/draw（不变）
```

**交互矩阵**（全部零特判）：

| 交互 | 生效路径 |
|---|---|
| ×2 快进加速正向转场 | speedFactor 乘 accumulator（:412）→ introRemaining 递减 ×2 → 正向进度 ×2；反向在 SHOPPING 无变速入口（HUD 隐藏），一致 |
| 暂停/弹窗冻结转场 | frozen → step 停（正向 introRemaining 不动）+ dt=0（反向停走） |
| 转场期投降不可点 | Catcher 挡 GiveUpButton（0.6s 后恢复——GDD「转场结束后照常可用」） |
| 转场期 Esc→放弃 | dialogStage 高于 Catcher → AbandonRun → RUN_END → 控制器 snap 稳态（zoom 1.0、chrome 隐、输入恢复） |
| 零棋子开战 | 转场照播（W2 缺陷随横幅废弃消失），解冻首步 aliveCount(PLAYER)==0 → ENEMY_WIN → RESULT |
| 重试/新战斗 | RESULT→SHOPPING 播反向；再次开战播正向——intro 门控每场重布防（`BattleSystem.java:122`），render §5.6「每场战斗播一次」 |
| 确定性回放/存档 | 转场纯表现（零 RNG/零事件/不入档）；快照仅 SHOPPING 稳态写（:325-330），续玩落地稳态不播转场 |

## 6. 改动点清单（评审主入口）

> 修改前代码逐字摘自当前源码（评审可 grep 复核）。路径省略前缀 `core/src/main/java/com/voidvvv/kz_auto_chess_n/`（测试为 `core/src/test/java/com/voidvvv/kz_auto_chess_n/`）。CP 按依赖序排列；T1~T3 期间全量测试保持绿（惰性/中性），T4 一次收口换相。

### CP1. GameBalance 新增转场四常量（只增不删，零行为）

- **类型**：修改类（新增字段）
- **位置**：`config/GameBalance.java:28`（`ATTACK_SPEED_GLOBAL_FACTOR` 行）之后
- **改动说明**：转场全部全局数值的唯一落点（K2）。`BATTLE_INTRO_TRANSITION_SECONDS` 本 CP 先行落入尚无消费者，行为零变化；两废弃常量在 CP9 才删（避免同段代码两处改动，且保 T1 编译绿）。
- **代码**：
  修改前：
  ```java
      /** 全局攻速系数（battle §5.1：敌我对称、只在消耗点乘算——BattleUnit.attackInterval() 一处；1 = 关闭） */
      public static final float ATTACK_SPEED_GLOBAL_FACTOR = 0.6f;

      // —— 能量 ——
  ```
  修改后：
  ```java
      /** 全局攻速系数（battle §5.1：敌我对称、只在消耗点乘算——BattleUnit.attackInterval() 一处；1 = 关闭） */
      public static final float ATTACK_SPEED_GLOBAL_FACTOR = 0.6f;

      // —— 开战转场「清场入阵」（battle §二 / render §5.6；2026-09-09 修订取代 2026-09-02 倒计时案）——
      /** 开战转场时长秒数（区间 0.4~0.8 待调；0 = 关闭转场——直接解冻且输入禁用窗口消失，软回滚杠杆 K11） */
      public static final float BATTLE_INTRO_TRANSITION_SECONDS = 0.6f;
      /** 备战期镜头基线 zoom（Q4：备战微拉远，开战回正 1.0「聚焦战场」；非整数缩放有轻微像素闪烁风险——计划 §8 W2） */
      public static final float SHOPPING_CAMERA_ZOOM = 1.06f;
      /** ② 备战席 / ③ 背包 / ⑥ 开战按钮 左滑（左移）距离，虚拟像素（≥ 备战席宽 108 + 留白；⑥ 同时淡出完成离场；待调） */
      public static final float BATTLE_TRANSITION_SLIDE_LEFT_PX = 140f;
      /** ⑧ 商店栏下滑 / 战斗 HUD 下落的单侧屏缘出场距离，虚拟像素（≥ 商店栏高 64 + 留白；待调） */
      public static final float BATTLE_TRANSITION_SLIDE_EDGE_PX = 72f;

      // —— 能量 ——
  ```
- **测试要点**：无独立单测（纯常量）；由 CP4 引用断言锚定。

### CP2. BattleState / BattleSystem 注释语境更新（零行为）

- **类型**：修改注释（6 处；方法名/字段名/签名一律不动——口径 K1）
- **位置**：`entities/BattleState.java:29,92-95,130,135,140`；`systems/BattleSystem.java:126-127`
- **改动说明**：「开战铺垫/倒计时」措辞 → 「开战转场/清场入阵」，与 battle V1.8 §二 / render V1.6 §5.6 语境一致。标识符零改动 → 全量测试不受影响。
- **代码**：
  `BattleState.java:29`，修改前：
  ```java
      private float introRemaining;         // 开战铺垫剩余秒数（battle §二；0 = 主循环已起）
  ```
  修改后：
  ```java
      private float introRemaining;         // 开战转场「清场入阵」剩余秒数（battle §二；0 = 主循环已起）
  ```
  `BattleState.java:92-95`，修改前：
  ```java
      /** 开战铺垫是否进行中（battle §二：倒计时期间主循环冻结——step 门控读取） */
      public boolean isIntroCountdownActive() { return introRemaining > 0f; }
      /** 开战铺垫剩余秒数（倒计时横幅只读） */
      public float getIntroRemaining() { return introRemaining; }
  ```
  修改后：
  ```java
      /** 开战转场是否进行中（battle §二：转场期间主循环冻结——step 门控读取） */
      public boolean isIntroCountdownActive() { return introRemaining > 0f; }
      /** 开战转场剩余秒数（转场驱动器归一化进度的只读源——render §5.6） */
      public float getIntroRemaining() { return introRemaining; }
  ```
  `BattleState.java:130`，修改前：
  ```java
      /** framework-internal：startBattle 布阵/开局效果/初始索敌完成后开启倒计时（battle §二流程） */
  ```
  修改后：
  ```java
      /** framework-internal：startBattle 布阵/开局效果/初始索敌完成后开启转场（battle §二清场入阵流程） */
  ```
  `BattleState.java:135`，修改前：
  ```java
      /** framework-internal：倒计时推进（step 门控内按 LOGIC_STEP 递减；下限 0 不下穿） */
  ```
  修改后：
  ```java
      /** framework-internal：转场推进（step 门控内按 LOGIC_STEP 递减；下限 0 不下穿） */
  ```
  `BattleState.java:140`，修改前：
  ```java
      /** framework-internal：跳过铺垫直入主循环（测试/调试后门，生产路径不调用） */
  ```
  修改后：
  ```java
      /** framework-internal：跳过转场直入主循环（测试/调试后门，生产路径不调用） */
  ```
  `BattleSystem.java:126-127`，修改前：
  ```java
      /** 推进一个 LOGIC_STEP（五阶段固定序）；开战铺垫期间仅推倒计时（battle §二实现落点②「step 门控」：
       *  主循环五阶段整体不执行——elapsed/RNG/事件/计时器/能量全冻结）；战斗已结束则空操作 */
  ```
  修改后：
  ```java
      /** 推进一个 LOGIC_STEP（五阶段固定序）；开战转场期间仅推转场时钟（battle §二实现落点②「step 门控」：
       *  主循环五阶段整体不执行——elapsed/RNG/事件/计时器/能量全冻结）；战斗已结束则空操作 */
  ```
- **测试要点**：无（纯注释）；验收 = 全量测试零变化。

### CP3. 新建 BattleTransitionController（转场驱动器）

- **类型**：新建文件
- **位置**：`render/ui/BattleTransitionController.java`
- **改动说明**：转场的唯一驱动面（render §5.6「转场驱动器」）。正向消费 `introRemaining` 归一化（K3：快进/冻结零特判）；反向自计时、RESULT→SHOPPING 翻转触发（Q3）、相位离开转场窗即中断自愈（K4：转场期 Esc→放弃 → RUN_END 不卡死）。镜头 zoom 常驻归它（K4）；UI 姿态取整吸附（render §八）；`applyUiPose()` 必须在 Screen 相位可见性行之后调用（转场窗内覆写可见性，稳态归零不越权——K7）。纯表现层：只写 Actor 位姿/颜色 alpha 与 `worldCamera.zoom`，零逻辑接触（只读 `GamePhase` 与 introRemaining 数值，由调用方喂入——本类不 import RunContext/BattleState，headless 可测）。
- **代码**（完整新建）：
  ```java
  package com.voidvvv.kz_auto_chess_n.render.ui;

  import com.badlogic.gdx.graphics.OrthographicCamera;
  import com.badlogic.gdx.scenes.scene2d.Actor;
  import com.voidvvv.kz_auto_chess_n.config.GameBalance;
  import com.voidvvv.kz_auto_chess_n.entities.GamePhase;

  /**
   * 开战转场「清场入阵」驱动器（battle §二 / render §5.6；2026-09-09 用户裁决 A「就地适配」）。
   *
   * <p>正向（SHOPPING→BATTLE）进度 = 1 − introRemaining / 转场时长：消费逻辑冻结门的剩余值，
   * ×2 快进与暂停/弹窗冻结零特判自动正确；反向（RESULT→SHOPPING，Q3 裁决）无逻辑计时器，
   * 渲染侧自计时（调用方以 frozen ? 0 : delta 喂 dt，随冻结停走）。
   *
   * <p>纯表现层：只写 UI Actor 位姿/透明度与 worldCamera.zoom，零逻辑改动、零 CombatEvent、零 RNG，
   * 确定性回放不含转场（battle §二冻结清单）。像素位移一律取整吸附（render §八）；zoom 插值是唯一例外
   * （Q4 已批的镜头域）。命中换算：boardViewport.unproject 含 zoom 逆变换，备战期拖拽无需补偿。
   *
   * <p>调用序（BattleScreen.render）：update() 必须先于 worldViewport.apply()（zoom 同帧生效）；
   * applyUiPose() 必须在相位可见性翻转行之后、uiStage.draw 之前（转场窗内覆写可见性）。
   */
  public final class BattleTransitionController {

      private final OrthographicCamera worldCamera;
      private final Actor shopBar;        // ⑧ 商店栏（底部条带、组原点）：下滑离场 / 自底缘上滑归位（裁决 A）
      private final Actor shoppingHud;    // ⑥ 开战按钮（组原点）：左移淡出 / 淡入归位
      private final Actor battleHud;      // 战斗 HUD（顶部、组原点）：自上缘外下落就位 / 上升退场
      private final Actor inventoryPanel; // ③ 背包（组原点）：左滑离场 / 滑回；稳态可见性归本类（K7「仅 SHOPPING」）
      private final Actor inputCatcher;   // 全屏透明收点：转场窗口吞 UI 点击（ResultBanner.ClickCatcher 先例）

      private GamePhase phase = GamePhase.SHOPPING;
      private float introRemaining;    // 最近一次 update 收到的转场剩余秒数（正向激活判定）
      private float reverseRemaining;  // 反向转场剩余秒数（渲染自计时；0 = 不在反向）

      public BattleTransitionController(OrthographicCamera worldCamera, Actor shopBar, Actor shoppingHud,
                                        Actor battleHud, Actor inventoryPanel, Actor inputCatcher) {
          this.worldCamera = worldCamera;
          this.shopBar = shopBar;
          this.shoppingHud = shoppingHud;
          this.battleHud = battleHud;
          this.inventoryPanel = inventoryPanel;
          this.inputCatcher = inputCatcher;
      }

      // —— 状态推进（render 早期：写 camera.zoom，先于 worldViewport.apply()） ——

      /**
       * 推进转场状态机并写入镜头 zoom。
       *
       * @param introRemaining BATTLE 且 intro 激活时的剩余秒数；否则传 0（Screen 侧合流）
       * @param dt             渲染帧时长（冻结期传 0——反向自计时随暂停停走）
       */
      public void update(GamePhase phase, float introRemaining, float dt) {
          if (this.phase == GamePhase.RESULT && phase == GamePhase.SHOPPING) {
              reverseRemaining = GameBalance.BATTLE_INTRO_TRANSITION_SECONDS; // Q3：战毕回备战反向播一次
          }
          this.phase = phase;
          this.introRemaining = introRemaining;
          if (reverseRemaining > 0f && phase != GamePhase.SHOPPING) {
              reverseRemaining = 0f; // 中断自愈（转场期 Esc→放弃 → RUN_END 等）：瞬回稳态
          }
          reverseRemaining = Math.max(0f, reverseRemaining - dt);
          worldCamera.zoom = zoom();
      }

      // —— 只读姿态（棋盘域 chrome 由 Screen 转交给 BattleRenderer） ——

      /** ② 备战席层水平位移（负值向左；③⑥ UI 域同参取值）：转场外恒 0 */
      public float benchOffsetX() {
          if (forwardPlaying()) {
              return -snap(GameBalance.BATTLE_TRANSITION_SLIDE_LEFT_PX * forwardProgress());
          }
          if (reverseRemaining > 0f) {
              return -snap(GameBalance.BATTLE_TRANSITION_SLIDE_LEFT_PX * (1f - reverseProgress()));
          }
          return 0f;
      }

      /** 备战层淡出系数（⑦ 出售区 / 布阵提示 / 敌阵虚影 / 部署帧）：稳态 SHOPPING 恒 1、其余稳态恒 0 */
      public float chromeFadeAlpha() {
          if (forwardPlaying()) {
              return 1f - forwardProgress();
          }
          if (reverseRemaining > 0f) {
              return reverseProgress();
          }
          return phase == GamePhase.SHOPPING ? 1f : 0f;
      }

      /** 转场期输入封禁（boardProcessor modalBlocked 合流 + inputCatcher 显隐；转场结束帧即恢复——GDD） */
      public boolean isInputBlocked() {
          return forwardPlaying() || reverseRemaining > 0f;
      }

      // —— UI 姿态（render 后段：相位可见性行之后、uiStage.draw 之前） ——

      /**
       * 覆写 ⑥⑧HUD③ 的可见性与位姿：转场窗内接管（相位行可能已隐藏，转场期强制可见随位移动画）；
       * 稳态把位姿归零、可见性交还相位行（③ 例外——稳态可见性常驻归本类，K7）。
       */
      public void applyUiPose() {
          if (forwardPlaying()) {
              float p = forwardProgress();
              shopBar.setVisible(true); // 相位行已隐藏：转场期接管（render §5.6）
              shopBar.setPosition(0f, -snap(GameBalance.BATTLE_TRANSITION_SLIDE_EDGE_PX * p));
              battleHud.setVisible(true);
              battleHud.setPosition(0f, snap(GameBalance.BATTLE_TRANSITION_SLIDE_EDGE_PX * (1f - p)));
              poseExit(shoppingHud, p);
              poseExit(inventoryPanel, p);
          } else if (reverseRemaining > 0f) {
              float p = reverseProgress();
              shopBar.setVisible(true); // 相位行已显示：仅覆写位姿（自底缘上滑归位——裁决 A）
              shopBar.setPosition(0f, -snap(GameBalance.BATTLE_TRANSITION_SLIDE_EDGE_PX * (1f - p)));
              battleHud.setVisible(true); // 相位行已隐藏：上升退场
              battleHud.setPosition(0f, snap(GameBalance.BATTLE_TRANSITION_SLIDE_EDGE_PX * p));
              poseEnter(shoppingHud, p);
              poseEnter(inventoryPanel, p);
          } else {
              poseRest(shopBar, false);
              poseRest(battleHud, false);
              poseRest(shoppingHud, false);
              poseRest(inventoryPanel, true);
          }
          inputCatcher.setVisible(isInputBlocked()); // 仅转场窗口可命中（不可见 Actor 不参与 hit 测试）
      }

      /** ⑥/③ 退场姿态：左移 + 淡出（p: 0→1） */
      private void poseExit(Actor actor, float p) {
          actor.setVisible(true);
          actor.setColor(1f, 1f, 1f, 1f - p);
          actor.setPosition(-snap(GameBalance.BATTLE_TRANSITION_SLIDE_LEFT_PX * p), 0f);
      }

      /** ⑥/③ 归位姿态：左缘滑回 + 淡入（p: 0→1） */
      private void poseEnter(Actor actor, float p) {
          actor.setVisible(true);
          actor.setColor(1f, 1f, 1f, p);
          actor.setPosition(-snap(GameBalance.BATTLE_TRANSITION_SLIDE_LEFT_PX * (1f - p)), 0f);
      }

      /** 稳态归零（幂等，逐帧调用无害）；visibleByPhase=false 的 Actor 可见性交还 Screen 相位行 */
      private void poseRest(Actor actor, boolean visibleOwnedHere) {
          actor.setColor(1f, 1f, 1f, 1f);
          actor.setPosition(0f, 0f);
          if (visibleOwnedHere) {
              actor.setVisible(phase == GamePhase.SHOPPING);
          }
      }

      // —— 内部 ——

      private boolean forwardPlaying() {
          return phase == GamePhase.BATTLE && introRemaining > 0f;
      }

      /** 正向进度 0→1（introRemaining 归一化；×2 快进 = 同一 accumulator 通路自动加速） */
      private float forwardProgress() {
          return 1f - introRemaining / GameBalance.BATTLE_INTRO_TRANSITION_SECONDS;
      }

      private float reverseProgress() {
          return 1f - reverseRemaining / GameBalance.BATTLE_INTRO_TRANSITION_SECONDS;
      }

      /** 像素取整吸附（render §八；zoom 插值除外——Q4 镜头域） */
      private static float snap(float value) {
          return Math.round(value);
      }
  }
  ```
- **测试要点**：CP4 状态机测试（TDD：本 CP 与 CP4 同任务，先红后绿）。

### CP4. 新建 BattleTransitionControllerTest（状态机 + 姿态）

- **类型**：新建测试文件
- **位置**：`core/src/test/java/com/voidvvv/kz_auto_chess_n/render/ui/BattleTransitionControllerTest.java`
- **改动说明**：headless（Actor/OrthographicCamera 零 GL 依赖）；断言用 GameBalance 常量推导，不写魔法数（除派生中间值）。7 用例覆盖：稳态、正向全周期、正向随 introRemaining（暂停/快进语义）、反向全周期、反向中断自愈、RUN_END→SHOPPING 不误播、③ 稳态可见性。
- **代码**（完整新建）：
  ```java
  package com.voidvvv.kz_auto_chess_n.render.ui;

  import com.badlogic.gdx.graphics.OrthographicCamera;
  import com.badlogic.gdx.scenes.scene2d.Actor;
  import com.voidvvv.kz_auto_chess_n.config.GameBalance;
  import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
  import org.junit.jupiter.api.BeforeEach;
  import org.junit.jupiter.api.DisplayName;
  import org.junit.jupiter.api.Test;

  import static org.assertj.core.api.Assertions.assertThat;
  import static org.assertj.core.api.Assertions.within;

  /** 开战转场驱动器状态机测试（battle §二 / render §5.6；headless——Actor/OrthographicCamera 零 GL） */
  class BattleTransitionControllerTest {

      private OrthographicCamera camera;
      private Actor shopBar;
      private Actor shoppingHud;
      private Actor battleHud;
      private Actor inventoryPanel;
      private Actor catcher;
      private BattleTransitionController controller;

      @BeforeEach
      void setUp() {
          camera = new OrthographicCamera();
          shopBar = new Actor();
          shoppingHud = new Actor();
          battleHud = new Actor();
          inventoryPanel = new Actor();
          catcher = new Actor();
          controller = new BattleTransitionController(
                  camera, shopBar, shoppingHud, battleHud, inventoryPanel, catcher);
      }

      /** 按剩余秒数推进正向转场（每步 dt = LOGIC_STEP，模拟 step 门控递减） */
      private void runForward(float remaining, int steps) {
          for (int i = 0; i < steps; i++) {
              remaining = Math.max(0f, remaining - GameBalance.LOGIC_STEP);
              controller.update(GamePhase.BATTLE, remaining, GameBalance.LOGIC_STEP);
          }
          controller.applyUiPose();
      }

      @Test
      @DisplayName("SHOPPING 稳态：zoom 1.06、chrome 全显、③ 可见、输入放行、Catcher 隐")
      void steadyShopping() {
          controller.update(GamePhase.SHOPPING, 0f, GameBalance.LOGIC_STEP);
          controller.applyUiPose();
          assertThat(camera.zoom).isCloseTo(GameBalance.SHOPPING_CAMERA_ZOOM, within(1e-6f));
          assertThat(controller.benchOffsetX()).isZero();
          assertThat(controller.chromeFadeAlpha()).isEqualTo(1f);
          assertThat(controller.isInputBlocked()).isFalse();
          assertThat(catcher.isVisible()).isFalse();
          assertThat(inventoryPanel.isVisible()).isTrue();
      }

      @Test
      @DisplayName("正向转场：起点姿态满格、中点 zoom 插值、终点解冻归稳态（zoom 1.0、输入恢复）")
      void forwardFullCycle() {
          controller.update(GamePhase.SHOPPING, 0f, 0f); // 预置 prevPhase
          controller.update(GamePhase.BATTLE, GameBalance.BATTLE_INTRO_TRANSITION_SECONDS, 0f);
          controller.applyUiPose();
          assertThat(controller.isInputBlocked()).isTrue();
          assertThat(catcher.isVisible()).isTrue();
          assertThat(shopBar.getY()).isLessThan(0f);                 // ⑧ 下滑离场中
          assertThat(battleHud.getY()).isGreaterThan(0f);            // HUD 上缘外下落中
          assertThat(inventoryPanel.getX()).isLessThan(0f);          // ③ 左滑中

          runForward(GameBalance.BATTLE_INTRO_TRANSITION_SECONDS, 18); // 半程
          assertThat(camera.zoom)
                  .isCloseTo((1f + GameBalance.SHOPPING_CAMERA_ZOOM) / 2f, within(1e-4f)); // 1.03
          assertThat(controller.chromeFadeAlpha()).isCloseTo(0.5f, within(0.05f));

          runForward(GameBalance.BATTLE_INTRO_TRANSITION_SECONDS / 2f, 18); // 后半程：remaining 0.3 → 0
          controller.update(GamePhase.BATTLE, 0f, GameBalance.LOGIC_STEP); // 解冻帧（remaining==0 显式兜底）
          controller.applyUiPose();
          assertThat(camera.zoom).isCloseTo(1f, within(1e-6f));
          assertThat(controller.isInputBlocked()).isFalse();
          assertThat(catcher.isVisible()).isFalse();
          assertThat(shopBar.getY()).isZero();                       // 稳态位姿归零
          assertThat(battleHud.getY()).isZero();
      }

      @Test
      @DisplayName("正向进度只随 introRemaining 走：冻结（dt=0）不动、跳变 remaining 即跳变（快进语义）")
      void forwardFollowsIntroRemainingOnly() {
          controller.update(GamePhase.BATTLE, GameBalance.BATTLE_INTRO_TRANSITION_SECONDS, 0f);
          float zoomAtStart = camera.zoom;
          controller.update(GamePhase.BATTLE, GameBalance.BATTLE_INTRO_TRANSITION_SECONDS, 0f); // 冻结帧
          assertThat(camera.zoom).isEqualTo(zoomAtStart);

          controller.update(GamePhase.BATTLE,
                  GameBalance.BATTLE_INTRO_TRANSITION_SECONDS / 2f, GameBalance.LOGIC_STEP); // ×2 快进一瞬
          assertThat(camera.zoom)
                  .isCloseTo((1f + GameBalance.SHOPPING_CAMERA_ZOOM) / 2f, within(1e-4f));
      }

      @Test
      @DisplayName("反向转场：RESULT→SHOPPING 翻转触发一次，随 dt 自计时，终点归 SHOPPING 稳态")
      void reverseFullCycle() {
          controller.update(GamePhase.RESULT, 0f, GameBalance.LOGIC_STEP); // prevPhase = RESULT
          controller.update(GamePhase.SHOPPING, 0f, GameBalance.LOGIC_STEP); // 触发
          controller.applyUiPose();
          assertThat(controller.isInputBlocked()).isTrue();
          assertThat(catcher.isVisible()).isTrue();
          assertThat(controller.chromeFadeAlpha()).isLessThan(1f);
          assertThat(shopBar.getY()).isLessThan(0f);                 // ⑧ 自底缘滑入中
          assertThat(battleHud.getY()).isGreaterThan(0f);            // HUD 上升退场中

          for (int i = 0; i < 36; i++) { // 0.6s = 36 步
              controller.update(GamePhase.SHOPPING, 0f, GameBalance.LOGIC_STEP);
          }
          controller.applyUiPose();
          assertThat(camera.zoom).isCloseTo(GameBalance.SHOPPING_CAMERA_ZOOM, within(1e-6f));
          assertThat(controller.isInputBlocked()).isFalse();
          assertThat(controller.chromeFadeAlpha()).isEqualTo(1f);
          assertThat(inventoryPanel.isVisible()).isTrue();
      }

      @Test
      @DisplayName("反向中断自愈：SHOPPING 反向中相位跳 RUN_END → 立即 snap 稳态（zoom 1.0、输入恢复）")
      void reverseInterruptedByRunEndSnaps() {
          controller.update(GamePhase.RESULT, 0f, GameBalance.LOGIC_STEP);
          controller.update(GamePhase.SHOPPING, 0f, GameBalance.LOGIC_STEP);
          controller.update(GamePhase.RUN_END, 0f, GameBalance.LOGIC_STEP); // 转场期 Esc→放弃
          controller.applyUiPose();
          assertThat(controller.isInputBlocked()).isFalse();
          assertThat(catcher.isVisible()).isFalse();
          assertThat(camera.zoom).isCloseTo(1f, within(1e-6f));
          assertThat(controller.chromeFadeAlpha()).isZero();
      }

      @Test
      @DisplayName("RUN_END→SHOPPING（重开新局）不误播反向：直接落地 SHOPPING 稳态（口径 K10）")
      void runEndToShoppingDoesNotReplayReverse() {
          controller.update(GamePhase.RUN_END, 0f, GameBalance.LOGIC_STEP);
          controller.update(GamePhase.SHOPPING, 0f, GameBalance.LOGIC_STEP);
          controller.applyUiPose();
          assertThat(controller.isInputBlocked()).isFalse();
          assertThat(catcher.isVisible()).isFalse();
          assertThat(controller.chromeFadeAlpha()).isEqualTo(1f);
          assertThat(camera.zoom).isCloseTo(GameBalance.SHOPPING_CAMERA_ZOOM, within(1e-6f));
      }

      @Test
      @DisplayName("③ 背包稳态可见性归驱动器（K7）：仅 SHOPPING 可见——BATTLE/RESULT 稳态隐藏")
      void inventoryVisibleOnlyInSteadyShopping() {
          runForward(GameBalance.BATTLE_INTRO_TRANSITION_SECONDS, 36); // 正向播完 → BATTLE 稳态
          assertThat(inventoryPanel.isVisible()).isFalse();
          controller.update(GamePhase.RESULT, 0f, GameBalance.LOGIC_STEP);
          controller.applyUiPose();
          assertThat(inventoryPanel.isVisible()).isFalse();
          controller.update(GamePhase.SHOPPING, 0f, GameBalance.LOGIC_STEP);
          controller.applyUiPose();
          assertThat(inventoryPanel.isVisible()).isTrue();
      }
  }
  ```
- **测试要点**：本 CP 即测试；验收 = 新文件 7 用例绿 + 全量无回归。

### CP5. BattleRenderer 转场姿态参数（棋盘域 chrome 随转场退场/归位）

- **类型**：修改方法（`draw` 签名 + `drawShopping` 拆分 + 两子绘制加淡出系数）
- **位置**：`render/board/BattleRenderer.java:90-103`（draw）、`:162-198`（drawShopping）、`:200-208`（drawSellZone）、`:210-217`（drawShoppingHint）
- **改动说明**：②⑦布阵提示虚影集中在 `drawShopping`，phase 换相即瞬隐（:96-101 分支）——转场要求它们随姿态退场/归位（battle §二动作表棋盘域行）。拆出 `drawShoppingChrome`（chrome = ② 席槽+席上棋子[左滑位移] + 敌阵虚影/⑦提示[淡出系数]），`drawShopping` = chrome + 玩家部署帧（淡出系数）；BATTLE 分支在 `chromeFadeAlpha > 0` 时补画 chrome（正向转场窗口），**不画部署帧**（K5 防与 UnitView 叠影）。`chromeFadeAlpha==0` 时 BATTLE 分支零额外绘制（稳态零开销）。位移植由调用方取整（控制器 snap），此处原样相加。
- **代码**：
  `draw`，修改前：
  ```java
      public void draw(SpriteBatch batch, RunContext ctx, float alpha, float renderClock, float dt,
                       BoardInputProcessor input) {
          syncBattleScope(ctx);
          batch.begin();
          drawGrid(batch);
          floatSlot = 0;
          if (ctx.getRunState().getPhase() == GamePhase.SHOPPING || ctx.getBattleState() == null) {
              drawShopping(batch, ctx);
              drawDropOverlay(batch, ctx, input);
          } else {
              drawBattle(batch, ctx, alpha, renderClock, dt);
          }
          batch.end();
      }
  ```
  修改后：
  ```java
      public void draw(SpriteBatch batch, RunContext ctx, float alpha, float renderClock, float dt,
                       BoardInputProcessor input, float benchOffsetX, float chromeFadeAlpha) {
          syncBattleScope(ctx);
          batch.begin();
          drawGrid(batch);
          floatSlot = 0;
          if (ctx.getRunState().getPhase() == GamePhase.SHOPPING || ctx.getBattleState() == null) {
              drawShopping(batch, ctx, benchOffsetX, chromeFadeAlpha);
              drawDropOverlay(batch, ctx, input);
          } else {
              if (chromeFadeAlpha > 0f) { // 开战转场窗口：备战层 chrome 随姿态退场（render §5.6；K5 不画部署帧）
                  drawShoppingChrome(batch, ctx, benchOffsetX, chromeFadeAlpha);
              }
              drawBattle(batch, ctx, alpha, renderClock, dt);
          }
          batch.end();
      }
  ```
  `drawShopping`，修改前：
  ```java
      private void drawShopping(SpriteBatch batch, RunContext ctx) {
          TextureRegion panel = assets.region(PlaceholderKeys.PANEL_9SLICE);
          for (int slot = 0; slot < GameBalance.BENCH_SIZE; slot++) {
              int[] center = BoardGeometry.benchSlotCenter(slot);
              batch.setColor(0.5f, 0.48f, 0.45f, 0.8f);
              batch.draw(panel, center[0] - BoardGeometry.BENCH_SLOT_W / 2f,
                      center[1] - BoardGeometry.BENCH_SLOT_H / 2f,
                      BoardGeometry.BENCH_SLOT_W, BoardGeometry.BENCH_SLOT_H);
          }
          batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
          Player player = ctx.getPlayer();
          List<Unit> bench = player.getBench();
          for (int slot = 0; slot < bench.size(); slot++) {
              int[] center = BoardGeometry.benchSlotCenter(slot);
              drawUnitFrame(batch, bench.get(slot).getTemplate().getId(),
                      PlaceholderKeys.ANIM_IDLE, 0, center[0], center[1], false, 1f, SideColors.PLAYER);
          }
          for (int y = 4; y <= 6; y++) {
              for (int x = 0; x < GameBalance.BOARD_COLS; x++) {
                  Unit unit = player.deployedAt(x, y);
                  if (unit != null) {
                      int[] center = BoardGeometry.cellCenter(x, y);
                      drawUnitFrame(batch, unit.getTemplate().getId(),
                              PlaceholderKeys.ANIM_IDLE, 0, center[0], center[1], false, 1f, SideColors.PLAYER);
                  }
              }
          }
          for (WaveSpec spec : ctx.getRunState().getEnemyWave()) { // 敌阵侦察虚影（红框 + 半透明，P1b）
              int[] center = BoardGeometry.cellCenter(spec.getGridX(), spec.getGridY());
              drawUnitFrame(batch, spec.getTemplate().getId(), PlaceholderKeys.ANIM_IDLE, 0,
                      center[0], center[1], true, SideColors.ENEMY_PREVIEW_ALPHA, SideColors.ENEMY);
          }
          drawSellZone(batch);
          drawShoppingHint(batch);
      }
  ```
  修改后：
  ```java
      /** 备战层总入口：chrome（②⑦提示虚影，随转场姿态）+ 玩家部署帧（随淡出系数；转场期由 UnitView 接管不画——K5） */
      private void drawShopping(SpriteBatch batch, RunContext ctx, float benchOffsetX, float chromeFadeAlpha) {
          drawShoppingChrome(batch, ctx, benchOffsetX, chromeFadeAlpha);
          Player player = ctx.getPlayer();
          for (int y = 4; y <= 6; y++) {
              for (int x = 0; x < GameBalance.BOARD_COLS; x++) {
                  Unit unit = player.deployedAt(x, y);
                  if (unit != null) {
                      int[] center = BoardGeometry.cellCenter(x, y);
                      drawUnitFrame(batch, unit.getTemplate().getId(),
                              PlaceholderKeys.ANIM_IDLE, 0, center[0], center[1], false,
                              chromeFadeAlpha, SideColors.PLAYER);
                  }
              }
          }
      }

      /** 备战层 chrome：② 备战席（槽 + 席上棋子，左滑位移）+ 敌阵虚影（淡出=实体化一拍）+ ⑦ 出售区 + 布阵提示（淡出） */
      private void drawShoppingChrome(SpriteBatch batch, RunContext ctx, float benchOffsetX, float chromeFadeAlpha) {
          TextureRegion panel = assets.region(PlaceholderKeys.PANEL_9SLICE);
          for (int slot = 0; slot < GameBalance.BENCH_SIZE; slot++) {
              int[] center = BoardGeometry.benchSlotCenter(slot);
              batch.setColor(0.5f, 0.48f, 0.45f, 0.8f);
              batch.draw(panel, center[0] - BoardGeometry.BENCH_SLOT_W / 2f + benchOffsetX,
                      center[1] - BoardGeometry.BENCH_SLOT_H / 2f,
                      BoardGeometry.BENCH_SLOT_W, BoardGeometry.BENCH_SLOT_H);
          }
          batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
          List<Unit> bench = ctx.getPlayer().getBench();
          for (int slot = 0; slot < bench.size(); slot++) {
              int[] center = BoardGeometry.benchSlotCenter(slot);
              drawUnitFrame(batch, bench.get(slot).getTemplate().getId(),
                      PlaceholderKeys.ANIM_IDLE, 0, (int) (center[0] + benchOffsetX), center[1],
                      false, 1f, SideColors.PLAYER);
          }
          for (WaveSpec spec : ctx.getRunState().getEnemyWave()) { // 敌阵侦察虚影（红框 + 半透明，P1b；转场随层淡出）
              int[] center = BoardGeometry.cellCenter(spec.getGridX(), spec.getGridY());
              drawUnitFrame(batch, spec.getTemplate().getId(), PlaceholderKeys.ANIM_IDLE, 0,
                      center[0], center[1], true, SideColors.ENEMY_PREVIEW_ALPHA * chromeFadeAlpha,
                      SideColors.ENEMY);
          }
          drawSellZone(batch, chromeFadeAlpha);
          drawShoppingHint(batch, chromeFadeAlpha);
      }
  ```
  `drawSellZone`，修改前：
  ```java
      /** ⑦ 出售区（render §九；棋盘域自绘，仅 SHOPPING 路径可达） */
      private void drawSellZone(SpriteBatch batch) {
          TextureRegion panel = assets.region(PlaceholderKeys.PANEL_9SLICE);
          batch.setColor(0.45f, 0.32f, 0.16f, 0.9f);
          batch.draw(panel, BoardGeometry.SELL_ZONE_X, BoardGeometry.SELL_ZONE_Y,
                  BoardGeometry.SELL_ZONE_W, BoardGeometry.SELL_ZONE_H);
          batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
          assets.font().draw(batch, "出售", BoardGeometry.SELL_ZONE_X + 16f, BoardGeometry.SELL_ZONE_Y + 28f);
      }
  ```
  修改后：
  ```java
      /** ⑦ 出售区（render §九；棋盘域自绘，仅 SHOPPING 路径可达；fade = 转场淡出系数） */
      private void drawSellZone(SpriteBatch batch, float fade) {
          TextureRegion panel = assets.region(PlaceholderKeys.PANEL_9SLICE);
          batch.setColor(0.45f, 0.32f, 0.16f, 0.9f * fade);
          batch.draw(panel, BoardGeometry.SELL_ZONE_X, BoardGeometry.SELL_ZONE_Y,
                  BoardGeometry.SELL_ZONE_W, BoardGeometry.SELL_ZONE_H);
          batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
          assets.font().setColor(1f, 1f, 1f, fade); // 用后即还（共用字体纪律）
          assets.font().draw(batch, "出售", BoardGeometry.SELL_ZONE_X + 16f, BoardGeometry.SELL_ZONE_Y + 28f);
          assets.font().setColor(com.badlogic.gdx.graphics.Color.WHITE);
      }
  ```
  `drawShoppingHint`，修改前：
  ```java
      private void drawShoppingHint(SpriteBatch batch) {
          if (hintLayout == null) {
              hintLayout = new GlyphLayout(assets.font(), SHOPPING_HINT);
          }
          assets.font().draw(batch, SHOPPING_HINT,
                  Math.round((BoardGeometry.VIRTUAL_W - hintLayout.width) / 2f), BoardGeometry.SHOP_HINT_Y);
      }
  ```
  修改后：
  ```java
      private void drawShoppingHint(SpriteBatch batch, float fade) {
          if (hintLayout == null) {
              hintLayout = new GlyphLayout(assets.font(), SHOPPING_HINT);
          }
          assets.font().setColor(1f, 1f, 1f, fade); // 用后即还（共用字体纪律）
          assets.font().draw(batch, SHOPPING_HINT,
                  Math.round((BoardGeometry.VIRTUAL_W - hintLayout.width) / 2f), BoardGeometry.SHOP_HINT_Y);
          assets.font().setColor(com.badlogic.gdx.graphics.Color.WHITE);
      }
  ```
- **测试要点**：纯绘制无逻辑分支，不设单测（沿血/能量条待遇）；语义锚定 = 稳态调用 `(0f, 1f)` 时与旧绘制逐位等价（T3 手验 + T4 手验清单第 1/4 条）；边界 `chromeFadeAlpha==0` 时 BATTLE 分支不进 chrome（CP4 性质由 T4 手验第 5 条覆盖）。

### CP6. BattleScreen 绘制调用点补两参（中性字面量，T3 惰性）

- **类型**：修改方法（1 行）
- **位置**：`screens/BattleScreen.java:317`
- **改动说明**：CP5 改了 `draw` 签名，本调用点同任务补参以保编译。T3 期间传中性字面量 `(0f, 1f)` = 逐位旧行为（chrome 全显无位移），**不提前接控制器**（控制器装配在 CP7）；CP7 落地后本行替换为控制器取值（见 CP7 第 3 hunk，届时以该 hunk 为准，本 hunk 的字面量行作为其中间态不复存在）。
- **代码**：
  修改前：
  ```java
          battleRenderer.draw(batch, runContext, alpha, renderClock, frozen ? 0f : delta, boardProcessor);
  ```
  修改后（T3 中间态）：
  ```java
          battleRenderer.draw(batch, runContext, alpha, renderClock, frozen ? 0f : delta, boardProcessor,
                  0f, 1f); // 中性姿态：chrome 全显无位移（CP7 后替换为转场驱动器取值）
  ```
- **测试要点**：全量测试绿（纯编译适配，行为零变化）。

### CP7. BattleScreen 总装配（退役横幅 + 控制器 + Catcher + 输入门控合流）

- **类型**：修改类（多 hunk；T4 收口）
- **位置**：`screens/BattleScreen.java:31`（import）、`:98`（字段）、`:177-178`（构造器 hunk A）、`:194`（构造器 hunk B）、`:230`（addActor）、`:276-281`（boardProcessor 门控）、`:305-318`（render 前段：phase 前提 + update + draw 调用）、`:335-338`（applyUiPose 插桩）、`:354-358`（introBanner 块删除）
- **改动说明**：screens 是唯一装配点（architecture §七）。控制器构造依赖 shopBar/inventoryPanel 等，置于 `hoverPreview` 之后；Catcher 为最顶层 Actor（不可见时不参与 hit 测试，稳态零影响；转场窗吞 UI 点击含 TopBar 暂停钮——K6，Esc 通路保留）。`update` 必须先于 `worldViewport.apply()`（zoom 同帧生效），故把 `phase` 声明上提。`applyUiPose` 插在相位可见性行与 `inventoryPanel.refresh()` 之间（覆写相位行的可见性）。③ 的注释同步修正（K7：不再是「全程可见」）。
- **代码**：
  import，修改前：
  ```java
  import com.voidvvv.kz_auto_chess_n.render.ui.BattleIntroBanner;
  ```
  修改后：
  ```java
  import com.voidvvv.kz_auto_chess_n.render.ui.BattleTransitionController;
  ```
  并在 scene2d import 区补：
  ```java
  import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
  ```
  字段，修改前：
  ```java
      private final ResultBanner resultBanner;
      private final BattleIntroBanner introBanner;
  ```
  修改后：
  ```java
      private final ResultBanner resultBanner;
      /** 开战转场驱动器（battle §二 / render §5.6；裁决 A）：UI 姿态 + worldCamera zoom + 输入封禁位 */
      private final BattleTransitionController transitionController;
      /** 全屏透明收点（仅转场窗口 touchable）：吞 UI 点击——render §5.6 转场期输入禁用的 UI 域封禁面（K6） */
      private final Actor inputCatcher;
  ```
  构造器 hunk A，修改前：
  ```java
          this.resultBanner = new ResultBanner(runFlowSystem, contextSupplier(), assets);
          this.introBanner = new BattleIntroBanner(assets);
  ```
  修改后：
  ```java
          this.resultBanner = new ResultBanner(runFlowSystem, contextSupplier(), assets);
  ```
  构造器 hunk B，修改前：
  ```java
          this.hoverPreview = new HoverPreviewCard(assets, contextSupplier());
  ```
  修改后：
  ```java
          this.hoverPreview = new HoverPreviewCard(assets, contextSupplier());
          // 开战转场装配（battle §二 / render §5.6）：收点先于控制器构造（控制器持有其引用）
          this.inputCatcher = new Actor(); // 无绘制；空 ClickListener 即消费点击（ResultBanner.ClickCatcher 先例）
          this.inputCatcher.setSize(BoardGeometry.VIRTUAL_W, BoardGeometry.VIRTUAL_H);
          this.inputCatcher.addListener(new ClickListener());
          this.transitionController = new BattleTransitionController(
                  worldCamera, shopBar, shoppingHud, battleHud, inventoryPanel, inputCatcher);
  ```
  addActor，修改前：
  ```java
          uiStage.addActor(introBanner); // 开战倒计时横幅（render §5.6）：面板之上、悬停卡之下；无输入监听
          uiStage.addActor(hoverPreview); // 最上层：瞬态悬停卡（无输入监听，不阻断任何交互）
  ```
  修改后：
  ```java
          uiStage.addActor(hoverPreview); // 最上层：瞬态悬停卡（无输入监听，不阻断任何交互）
          uiStage.addActor(inputCatcher); // 转场窗口全屏吞点击（render §5.6 输入禁用）：最顶层、仅转场期可命中
  ```
  boardProcessor 门控合流，修改前：
  ```java
                  new java.util.function.BooleanSupplier() { // 模态阻断位（Phase 4 预留位兑现）
                      @Override
                      public boolean getAsBoolean() {
                          return dialogManager.isShowing();
                      }
                  },
  ```
  修改后：
  ```java
                  new java.util.function.BooleanSupplier() { // 模态/转场阻断位（input §3）：弹窗或转场窗口期吞棋盘输入
                      @Override
                      public boolean getAsBoolean() {
                          return dialogManager.isShowing() || transitionController.isInputBlocked();
                      }
                  },
  ```
  render 前段（phase 前提 + update + draw 调用），修改前：
  ```java
          boolean frozen = paused || dialogManager.isShowing(); // 模拟冻结（口径 #14：暂停或任一弹窗模态）
          if (!frozen) {
              stepSimulation(delta);
              renderClock += delta;
          } else {
              commandManager.executeAll(runContext); // 冻结的只是模拟推进（战斗步进/RESULT 计时/动画），命令照常结算——弹窗按钮链路（PickChest/AbandonRun/UnequipItem）否则死锁（feedback02 修复）
          }
          float alpha = frozen ? 0f : accumulator / GameBalance.LOGIC_STEP;
          worldViewport.apply();
          batch.setProjectionMatrix(worldCamera.combined);
          battleRenderer.draw(batch, runContext, alpha, renderClock, frozen ? 0f : delta, boardProcessor);
          GamePhase phase = runContext.getRunState().getPhase();
  ```
  修改后：
  ```java
          boolean frozen = paused || dialogManager.isShowing(); // 模拟冻结（口径 #14：暂停或任一弹窗模态）
          GamePhase phase = runContext.getRunState().getPhase(); // 前提：转场驱动器与绘制同帧同相位
          // 开战转场驱动（battle §二 / render §5.6）：先于 worldViewport.apply() 写 zoom；dt 冻结感知——反向自计时随暂停停走
          transitionController.update(phase,
                  phase == GamePhase.BATTLE && runContext.getBattleState() != null
                          && runContext.getBattleState().isIntroCountdownActive()
                          ? runContext.getBattleState().getIntroRemaining() : 0f,
                  frozen ? 0f : delta);
          if (!frozen) {
              stepSimulation(delta);
              renderClock += delta;
          } else {
              commandManager.executeAll(runContext); // 冻结的只是模拟推进（战斗步进/RESULT 计时/动画），命令照常结算——弹窗按钮链路（PickChest/AbandonRun/UnequipItem）否则死锁（feedback02 修复）
          }
          float alpha = frozen ? 0f : accumulator / GameBalance.LOGIC_STEP;
          worldViewport.apply();
          batch.setProjectionMatrix(worldCamera.combined);
          battleRenderer.draw(batch, runContext, alpha, renderClock, frozen ? 0f : delta, boardProcessor,
                  transitionController.benchOffsetX(), transitionController.chromeFadeAlpha());
  ```
  applyUiPose 插桩，修改前：
  ```java
          if (phase == GamePhase.SHOPPING) {
              shopBar.refresh(runContext);
          }
          inventoryPanel.refresh(); // ③⑤⑨ 全程可见（BATTLE 置灰在各自 draw 内，差异声明 #8）
  ```
  修改后：
  ```java
          if (phase == GamePhase.SHOPPING) {
              shopBar.refresh(runContext);
          }
          transitionController.applyUiPose(); // 转场姿态覆写（⑥⑧HUD③ + Catcher；稳态归零不越权——K7）
          inventoryPanel.refresh(); // ⑤⑨ 全程可见（BATTLE 置灰在各自 draw 内，差异声明 #8）；③ 稳态可见性归转场驱动器（K7）
  ```
  introBanner 块删除，修改前：
  ```java
          if (phase == GamePhase.BATTLE && runContext.getBattleState() != null) {
              introBanner.refresh(runContext.getBattleState(), frozen ? 0f : delta); // 开战铺垫横幅；冻结期节拍不走
          } else {
              introBanner.reset(); // 离开 BATTLE 复位（重试/新战斗重播）
          }
          syncChestDialog(phase);
  ```
  修改后：
  ```java
          syncChestDialog(phase);
  ```
- **测试要点**：libGDX Screen 无 headless 单测先例（`BattleScreenStatusLineTest` 仅测静态方法）；走 §7 T4 手验清单。编译期锚点 = CP8 删除后无残留引用。

### CP8. 删除 BattleIntroBanner 及其测试

- **类型**：删除文件（2 个）
- **位置**：`core/src/main/java/com/voidvvv/kz_auto_chess_n/render/ui/BattleIntroBanner.java`；`core/src/test/java/com/voidvvv/kz_auto_chess_n/render/ui/BattleIntroBannerTextTest.java`
- **改动说明**：render §5.6「横幅退役删除」（2026-09-09 修订）。两者均为上一轮未提交产物，直接删除；生产引用面已由 CP7 清空（import/字段/构造/addActor/render 块五处）。
- **测试要点**：`BattleIntroBannerTextTest` 随文件消失（基线 785 → 784）；验收 = 全库 grep `BattleIntroBanner` 零命中。

### CP9. GameBalance 删除两废弃常量

- **类型**：修改类（删除字段，2 处）
- **位置**：`config/GameBalance.java:22-26`（节奏三件套块头注释 + 两常量及其 javadoc）
- **改动说明**：battle §8.3「`BATTLE_INTRO_GO_BEAT_SECONDS` 废弃删除」+ `BATTLE_INTRO_COUNTDOWN_SECONDS` 被 CP1 新常量取代。消费面在 CP7/CP10/CP11/CP12 已全部迁移，本 CP 收口后全库无引用。块头注释随旧常量一并退役（新块头已由 CP1 落位）；`ATTACK_SPEED_GLOBAL_FACTOR`（去一留二的保留件）不动。
- **代码**：
  修改前：
  ```java
      // —— 战斗节奏三件套（battle §二开战铺垫 / §5.1；2026-09-02，均待调）——
      /** 开战铺垫倒计时秒数（battle §二：逻辑冻结对峙；区间 2~3s 待调，缺省 3s；0 = 关闭铺垫） */
      public static final float BATTLE_INTRO_COUNTDOWN_SECONDS = 3f;
      /** 倒计时归零后「开战！」横幅节拍秒数（render §5.6「3→2→1→开战！」；0 = 严格按「归零即隐藏」） */
      public static final float BATTLE_INTRO_GO_BEAT_SECONDS = 0.5f;
      /** 全局攻速系数（battle §5.1：敌我对称、只在消耗点乘算——BattleUnit.attackInterval() 一处；1 = 关闭） */
  ```
  修改后：
  ```java
      /** 全局攻速系数（battle §5.1：敌我对称、只在消耗点乘算——BattleUnit.attackInterval() 一处；1 = 关闭） */
  ```
- **测试要点**：编译期锚点——全库 grep `BATTLE_INTRO_COUNTDOWN_SECONDS|BATTLE_INTRO_GO_BEAT_SECONDS` 零命中。

### CP10. BattleSystem 布防点换常量（3s → 0.6s 生效点）

- **类型**：修改方法（1 行）
- **位置**：`systems/BattleSystem.java:122`（startBattle 尾）
- **改动说明**：转场时长切换的唯一逻辑层落点（battle §二「实现落点」：机制复用 intro 门控、时长改由转场常量提供）。位置与调用时机（初始索敌完成后）不变。与 CP11/CP12 同任务落地（测试断言随值迁移）。
- **代码**：
  修改前：
  ```java
          state.beginIntroCountdown(GameBalance.BATTLE_INTRO_COUNTDOWN_SECONDS); // 开战铺垫（battle §二）
  ```
  修改后：
  ```java
          state.beginIntroCountdown(GameBalance.BATTLE_INTRO_TRANSITION_SECONDS); // 开战转场 0.6s（battle §二清场入阵）
  ```
- **测试要点**：CP12 冻结清单用例（0.6s = 36 步口径）；`timeoutCountsAsPlayerLoss` 零改动自绿（4000 − 36 ≥ 3600，隐式验证 elapsed 不含转场）。

### CP11. BattleStateTest 两用例迁移（0.6f / 36 步）

- **类型**：修改方法（2 用例；因 CP1 删旧常量 + CP10 换值）
- **位置**：`core/src/test/java/com/voidvvv/kz_auto_chess_n/entities/BattleStateTest.java:116-142`
- **改动说明**：常量引用换名；循环步数按 0.6s = 36 步重推（口径 K1 沿 pacing 计划：整步递减、浮点容差 ±1 步——35 步时剩余 0.0167 仍激活）。DisplayName 更新转场语境；测试方法名保留（K1）。
- **代码**：
  修改前：
  ```java
      @Test
      @DisplayName("开战铺垫字段：直构缺省不激活；begin → active；advance 递减至 0 不下穿")
      void introCountdownLifecycle() {
          BattleState state = state(unit(1, Side.PLAYER));
          assertThat(state.isIntroCountdownActive()).isFalse(); // 直构（非 startBattle）无铺垫（口径 K2）
          state.beginIntroCountdown(GameBalance.BATTLE_INTRO_COUNTDOWN_SECONDS);
          assertThat(state.isIntroCountdownActive()).isTrue();
          assertThat(state.getIntroRemaining())
                  .isCloseTo(GameBalance.BATTLE_INTRO_COUNTDOWN_SECONDS, within(1e-6f));
          for (int i = 0; i < 179; i++) { // 3s = 180 步（浮点容差：179 步仍在）
              state.advanceIntroCountdown(GameBalance.LOGIC_STEP);
          }
          assertThat(state.isIntroCountdownActive()).isTrue();
          state.advanceIntroCountdown(GameBalance.LOGIC_STEP);
          state.advanceIntroCountdown(GameBalance.LOGIC_STEP); // 越界不下穿
          assertThat(state.isIntroCountdownActive()).isFalse();
          assertThat(state.getIntroRemaining()).isEqualTo(0f);
      }

      @Test
      @DisplayName("skipIntroCountdown：测试/调试后门，剩余清零直入主循环")
      void skipIntroCountdownZerosRemaining() {
          BattleState state = state(unit(1, Side.PLAYER));
          state.beginIntroCountdown(GameBalance.BATTLE_INTRO_COUNTDOWN_SECONDS);
          state.skipIntroCountdown();
          assertThat(state.isIntroCountdownActive()).isFalse();
      }
  ```
  修改后：
  ```java
      @Test
      @DisplayName("开战转场字段：直构缺省不激活；begin → active；advance 递减至 0 不下穿")
      void introCountdownLifecycle() {
          BattleState state = state(unit(1, Side.PLAYER));
          assertThat(state.isIntroCountdownActive()).isFalse(); // 直构（非 startBattle）无转场（口径 K2）
          state.beginIntroCountdown(GameBalance.BATTLE_INTRO_TRANSITION_SECONDS);
          assertThat(state.isIntroCountdownActive()).isTrue();
          assertThat(state.getIntroRemaining())
                  .isCloseTo(GameBalance.BATTLE_INTRO_TRANSITION_SECONDS, within(1e-6f));
          for (int i = 0; i < 35; i++) { // 0.6s = 36 步（浮点容差：35 步仍在转场）
              state.advanceIntroCountdown(GameBalance.LOGIC_STEP);
          }
          assertThat(state.isIntroCountdownActive()).isTrue();
          state.advanceIntroCountdown(GameBalance.LOGIC_STEP);
          state.advanceIntroCountdown(GameBalance.LOGIC_STEP); // 越界不下穿
          assertThat(state.isIntroCountdownActive()).isFalse();
          assertThat(state.getIntroRemaining()).isEqualTo(0f);
      }

      @Test
      @DisplayName("skipIntroCountdown：测试/调试后门，剩余清零直入主循环")
      void skipIntroCountdownZerosRemaining() {
          BattleState state = state(unit(1, Side.PLAYER));
          state.beginIntroCountdown(GameBalance.BATTLE_INTRO_TRANSITION_SECONDS);
          state.skipIntroCountdown();
          assertThat(state.isIntroCountdownActive()).isFalse();
      }
  ```
- **测试要点**：本 CP 即测试修订；验收 = 该文件全绿。

### CP12. BattleSystemTest 冻结清单用例迁移（36 步）

- **类型**：修改方法（1 用例；因 CP10）
- **位置**：`core/src/test/java/com/voidvvv/kz_auto_chess_n/systems/BattleSystemTest.java:195-223`
- **改动说明**：冻结清单断言逐条保留（tick/elapsed/事件/RNG/计时器），仅步数按 36 步口径迁移（K1 沿用：区间断言已含浮点余量）。其余用例零改动：8 处 `skipIntroCountdown()` 调用（方法名未改）、`firstAttackDelayedByFullInterval`（skip 隔离变量）、`timeoutCountsAsPlayerLoss`（36 步余量充裕）。
- **代码**：
  修改前：
  ```java
      @Test
      @DisplayName("开战铺垫冻结清单（battle §二）：倒计时期间 tick/elapsed/事件/RNG/计时器全冻结，归零后主循环起步")
      void introCountdownFreezesMainLoop() {
          GameData data = data();
          Player player = deployPlayer(data, data.getUnit("orc"), 2, 4);
          List<WaveSpec> wave = waveOf(data.getUnit("grunt"), 1f, 2, 0);
          BattleState state = start(data, player, wave, 42L);

          assertThat(state.isIntroCountdownActive()).isTrue();
          assertThat(state.getIntroRemaining())
                  .isCloseTo(GameBalance.BATTLE_INTRO_COUNTDOWN_SECONDS, within(1e-6f));
          int rngBefore = state.getRng().getConsumedCount();
          for (int i = 0; i < 100; i++) {
              SYSTEM.step(state);
          }
          assertThat(state.getTick()).isZero();          // beginTick 未达
          assertThat(state.getElapsed()).isEqualTo(0f);   // 60s 超时钟不起表
          assertThat(state.getEvents()).isEmpty();        // 零 CombatEvent
          assertThat(state.getRng().getConsumedCount()).isEqualTo(rngBefore); // 零 RNG
          assertThat(state.getUnits().get(0).getAttackTimer()).isEqualTo(0f); // 计时器保持 0

          for (int i = 100; i < 185; i++) { // 3s = 180 步 ± 浮点余量（口径 K1）
              SYSTEM.step(state);
          }
          assertThat(state.isIntroCountdownActive()).isFalse();
          assertThat(state.getTick()).isBetween(1, 6);    // 主循环刚起步
          assertThat(state.getElapsed())                  // elapsed 恒 = tick × LOGIC_STEP（不含倒计时）
                  .isCloseTo(state.getTick() * GameBalance.LOGIC_STEP, within(1e-5f));
      }
  ```
  修改后：
  ```java
      @Test
      @DisplayName("开战转场冻结清单（battle §二）：转场期间 tick/elapsed/事件/RNG/计时器全冻结，归零后主循环起步")
      void introCountdownFreezesMainLoop() {
          GameData data = data();
          Player player = deployPlayer(data, data.getUnit("orc"), 2, 4);
          List<WaveSpec> wave = waveOf(data.getUnit("grunt"), 1f, 2, 0);
          BattleState state = start(data, player, wave, 42L);

          assertThat(state.isIntroCountdownActive()).isTrue();
          assertThat(state.getIntroRemaining())
                  .isCloseTo(GameBalance.BATTLE_INTRO_TRANSITION_SECONDS, within(1e-6f));
          int rngBefore = state.getRng().getConsumedCount();
          for (int i = 0; i < 35; i++) { // 0.6s = 36 步（浮点容差：35 步仍在转场）
              SYSTEM.step(state);
          }
          assertThat(state.getTick()).isZero();          // beginTick 未达
          assertThat(state.getElapsed()).isEqualTo(0f);   // 60s 超时钟不起表
          assertThat(state.getEvents()).isEmpty();        // 零 CombatEvent
          assertThat(state.getRng().getConsumedCount()).isEqualTo(rngBefore); // 零 RNG
          assertThat(state.getUnits().get(0).getAttackTimer()).isEqualTo(0f); // 计时器保持 0

          for (int i = 35; i < 41; i++) { // 转场归零后主循环刚起步（± 浮点余量，口径 K1）
              SYSTEM.step(state);
          }
          assertThat(state.isIntroCountdownActive()).isFalse();
          assertThat(state.getTick()).isBetween(1, 6);    // 主循环刚起步
          assertThat(state.getElapsed())                  // elapsed 恒 = tick × LOGIC_STEP（不含转场）
                  .isCloseTo(state.getTick() * GameBalance.LOGIC_STEP, within(1e-5f));
      }
  ```
- **测试要点**：本 CP 即测试修订；验收 = 该文件全绿（含 8 处 skipIntro 用例与 runToEnd 系零改动自绿）。

### CP13. 旧 spec_plan 处理（回填一处 + 标注取代一处）

- **类型**：修改文档（两个文件）
- **改动说明**：① `2026-08-21_phase3_battle_engine.md` 是 battle_design.md:35 明文指向的实现层口径注册表——§3.2 口径 #4 行上一棒已按 2026-09-02 回填，本次仅补一句 2026-09-09 语境括注并指向本计划（沿 CP11 先例的「回填」裁决：注册表留旧语境会误导执行者，成本一行）。② `2026-09-02_battle_pacing.md` 是倒计时案的执行计划——**标注被取代而非重写**（裁决理由：未提交的历史执行文档，重写 CP 正文会伪造历史记录并与本计划重复；头部状态注 + 受影响 CP 逐条一行标注即可穷尽歧义。CP4~CP8/CP11 仍有效，不动）。
- **代码**：
  `docs/spec_plan/2026-08-21_phase3_battle_engine.md:82`，修改前：
  ```
  | 4 | 攻击/移动计时器每 tick 恒累计（与是否在射程无关），出手/走步消耗后**结转余数**；开战铺垫倒计时后从零蓄力（2026-09-02 修订「开局即就绪」，battle §5.1 修订记录；实施见 2026-09-02_battle_pacing.md） | battle §5.1（V1.7 修订）——计时器是蓄力不是冷却 |
  ```
  修改后：
  ```
  | 4 | 攻击/移动计时器每 tick 恒累计（与是否在射程无关），出手/走步消耗后**结转余数**；开战铺垫倒计时后从零蓄力（2026-09-02 修订「开局即就绪」，battle §5.1 修订记录；实施见 2026-09-02_battle_pacing.md；2026-09-09 语境修订：铺垫形式 = 开战转场「清场入阵」0.6s，机制不变——实施见 2026-09-09_battle_intro_transition.md） | battle §5.1（V1.7 修订）——计时器是蓄力不是冷却 |
  ```
  `docs/spec_plan/2026-09-02_battle_pacing.md:3`，修改前：
  ```
  > **日期**：2026-09-02　**状态**：待评审
  ```
  修改后：
  ```
  > **日期**：2026-09-02　**状态**：部分被取代（2026-09-09）——开战铺垫倒计时相关项被 `2026-09-09_battle_intro_transition.md` 取代：CP1 两常量（COUNTDOWN/GO_BEAT）已删、CP2/CP3 机制保留但语境与时长改转场（0.6s）、CP9 BattleIntroBanner 退役删除、CP10 横幅装配改转场驱动器装配；**CP4~CP8 / CP11（计时器归零、攻速系数、蓄力条、测试修订、旧计划回填）仍有效**。
  ```
  并在 CP1/CP2/CP3/CP9/CP10 各节标题后紧插一行（以 CP9 为例，其余同构）：
  ```
  > **[2026-09-09 部分取代]** 见 `2026-09-09_battle_intro_transition.md` 对应 CP；下文按原样存档。
  ```
- **测试要点**：无（纯文档）。

## 7. 分阶段任务拆解

> 每任务收尾必须全绿才可进入下一任务（小步可验证）。TDD 序：任务内先落测试（RED）再落实现（GREEN）。
> 测试验证纪律：`gradlew.bat :core:test`（bash 下 `./gradlew :core:test; echo $?`）——成功时控制台零输出，用**退出码** + `core/build/test-results/test/TEST-*.xml` 聚合计数核对。

| 任务 | 所含 CP | 前置 | 验收标准（含测试计数） |
|---|---|---|---|
| T1 常量与注释（惰性） | CP1、CP2 | — | 全量 785 绿零变化（新常量无消费者、注释零行为） |
| T2 驱动器（惰性） | CP3、CP4 | T1 | BattleTransitionControllerTest 7 用例绿；全量 **792** 绿（785 + 7） |
| T3 渲染器姿态（中性） | CP5、CP6 | T2 | 全量 792 绿（调用点传 `(0f, 1f)` 逐位旧行为）；手验：备战/战斗画面与 T1 前无差异 |
| T4 收口换相 | CP7、CP8、CP9、CP10、CP11、CP12 | T3 | 全库 grep `BattleIntroBanner|BATTLE_INTRO_COUNTDOWN_SECONDS|BATTLE_INTRO_GO_BEAT_SECONDS` 零命中；全量 **791** 绿（−1 横幅文案测试）；BattleStateTest/BattleSystemTest 迁移用例绿 |
| T5 文档与总验收 | CP13 | T4 | 两旧文档回填/标注落地；`./gradlew :core:test` 退出码 0 且 XML 聚合 791/0/0/0；手验清单全过 |

**手验清单（T4/T5，跑 lwjgl3 桌面包，PC 1280×720）**：
1. SHOPPING 点开战：0.6s 内——⑧ 商店栏自底部下滑沉出、战斗 HUD 自上缘外下落就位、⑥ 开战按钮左移淡出、③ 背包左滑淡出、② 备战席（含席上棋子）左滑、⑦ 出售区/布阵提示淡出、敌阵虚影淡出同时实体单位就位（实体化一拍）、镜头 1.06→1.0 微回正；解冻后蓄力条从零充能。
2. 转场期点击：商店卡/刷新/经验/开战钮/变速/投降/TopBar 暂停钮均无响应；棋盘拖拽无响应；Esc 可开暂停菜单、L 可展开通知。
3. ×2 快进（转场结束后开启再开新战斗，或上一局遗留 ×2）：转场同步 ×2 加速；Esc 暂停时转场画面定格、恢复续播不跳变。
4. 战毕回备战：横幅点击/3s 自动 → 0.6s 反向——HUD 上升退出、⑧ 自底缘上滑归位、⑥③淡入滑回、②⑦提示淡入、镜头 1.0→1.06 复位；胜局 PickChest 与败局重试两条路径都播反向。
5. 边界：转场期 Esc→暂停菜单→放弃 → 直接 RUN_END 无卡死无残影（控制器中断自愈）；RUN_END 重开新局无反向误播；零棋子开战转场照播、解冻即判负；转场中窗口失焦/恢复不跳变。
6. zoom 1.06 命中校验：备战期拖棋子布阵/拖至出售区/悬停预览卡位置全部正常（unproject 吸收 zoom）。
7. RESULT/RUN_END 稳态：②⑦提示虚影与 ③ 背包不显示（§九「仅 SHOPPING」）；⑤ 羁绊面板 BATTLE 期置暗 0.35（既有行为）。

**回滚面**：按任务粒度 git revert（T1~T5 各自独立成 commit）；机制级软回滚 = `BATTLE_INTRO_TRANSITION_SECONDS = 0f`（转场与输入禁用窗口即关）与 `SHOPPING_CAMERA_ZOOM = 1f`（镜头回正即关），无需改代码（K11）。

## 8. 风险与开放问题

| # | 级别 | 问题 | 处置 |
|---|---|---|---|
| W1 | WARNING | ⑤ 羁绊面板「原地置暗保留」以既有 phase 联动兑现（相位翻转即 0.35，无 0.6s 渐变）——与分镜 F1 的动作化表述存在粒度差 | K7 已定口径；手验第 7 条确认观感，若需渐变再提请裁决（需给 SynergyPanel 加 dim 系数，一处改动） |
| W2 | WARNING | zoom 1.06 非整数倍缩放 → SHOPPING 期 nearest 采样可能有轻微像素闪烁（render §八整数像素纪律的镜头域例外） | Q4 已批 zoom 域；手验第 6 条观察，待调区间可下探 1.0 或改 1.5/2.0 整数倍档 |
| W3 | WARNING | 既有文档漂移（只登记不修，非本次引入）：render §九 战斗 HUD 行 (0,296,640,64) 与代码顶部实现（A 裁决维持现状）；§九 ⑥ 行 (508,200,112,40) 与代码 (134,88,64,40)（同文档 hover 注已自认 x134~198）；Q1 勘误正由 gdd-writer 并行处理（本计划行号如遇漂移以语义为准） | gdd-writer 工作面；本计划不修 GDD |
| W4 | WARNING | `speedFactor` 跨局不重置（仅 show/restartRun 复位，`BattleScreen.java:272,494`）——上一局 ×2 会延续到下一局的开战转场（GDD「×2 同步加速」恰经此通路成立；但玩家感知可能是「转场莫名变快」） | 既有行为，零改动；登记观察，若裁决「每场归 ×1」是独立一行变更 |
| W5 | WARNING | CP11/CP12 步数按公式推算（float 累计 ±1 步级漂移） | 红则只调步数余量（区间断言已留容差），禁改断言语义；XML 定位 |
| W6 | WARNING | 转场期悬停残留：Catcher 挡新 enter/exit，已置的 `hoveredSlot`（ShopBar/InventoryPanel）0.6s 内不清零 → 悬停预览卡可能短暂滞留 | 纯表现瞬态；手验第 2 条观察，若刺眼可在 applyUiPose 稳态分支清两槽位（两行变更，暂不做） |
| W7 | 声明 | `docs/diagrams/battle_intro_countdown.md(.html)`（倒计时案配图）与定稿分镜图 `battle_intro_slide_transition.md(.html)` 的「上滑」帧 | 前者 render §5.6 已声明「取代」；后者 gdd-writer 勘误中——本计划新增 `battle_intro_transition_flow` 状态机图，不动旧图 |
| W8 | 声明 | 转场期 Catcher 挡 TopBar 暂停钮（uiStage 域无法「高于 HUD 低于 TopBar」——TopBar 是 uiStage 最底层） | K6 口径：0.6s 元层入口被挡可接受，Esc 通路保留；若不可接受需把 TopBar 迁 dialogStage（范围扩张，不默认做） |

## 9. 附录：用户确认记录

### 9.1 Q1 BLOCKER 问答存档（2026-09-09）

- **问题**（planner → team-lead → 用户）：转场分镜「商店栏 ⑧ 与战斗 HUD 共用同一顶部槽位 (0,296,640,64)、⑧ 上滑离场」前提与代码事实矛盾——`BattleScreen.java:226` shopBar 从未 setPosition（组停原点、子元素 y=4..60 = 底部条带，`BoardGeometry.java:81` 遮挡记录佐证实机一直在底部）；战斗 HUD 则实机在顶部（`BattleHud.java:43,46`）。定稿图/§九 自洽读法/代码三方在 ⑧=底部上一致，「顶部」仅出自 2026-09-09 转场文案对 §九 坐标的 y-up 误读（分镜 html 自注「(0,296,640,64)up = 顶 128px」）。
- **备选**：A 就地适配（⑧ 底部下滑 / HUD 顶部下落，布局零改动）/ B 战斗 HUD 迁底槽（交接成真，战斗屏布局变更）/ C 商店栏迁顶（顶部 64px 已被 TopBar+HUD 占满，不可行警告）。
- **用户答复**：**A——就地适配**。商店栏（底部实机）下滑离场/回位；战斗 HUD（顶部实机）自屏幕上缘外下落就位/上升退场；「交接同一槽位」语义废弃（两屏缘各自进出，观感为经营层/战斗层整层交换）；其余动作表行不变。GDD 文档勘误由 gdd-writer 并行落位；⑤ 置暗与棋盘域驱动按 planner 方案落入；既有文档漂移只登记不修。

### 9.2 交接既定裁决（沿袭）

- 2026-09-09 分镜评审 Q1~Q5（滑出 / 0.6s（0.4~0.8 待调）/ 反向同款 / 镜头回正保留 / 上摇否决）。
- 2026-09-02 节奏案承继件：全局攻速系数 ×0.6、计时器从零蓄力、攻击蓄力条（去一留二）；intro 冻结门控机制（BattleState 字段 + step 门控）原样复用，仅时长 3s→0.6s。


