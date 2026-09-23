# 战斗节奏三件套 技术实施文档

> **日期**：2026-09-02　**状态**：部分被取代（2026-09-09）——开战铺垫倒计时相关项被 `2026-09-09_battle_intro_transition.md` 取代：CP1 两常量（COUNTDOWN/GO_BEAT）已删、CP2/CP3 机制保留但语境与时长改转场（0.6s）、CP9 BattleIntroBanner 退役删除、CP10 横幅装配改转场驱动器装配；**CP4~CP8 / CP11（计时器归零、攻速系数、蓄力条、测试修订、旧计划回填）仍有效**。
> **依据**（均已经用户评审批准）：`battle_design.md` V1.7 §二「开战铺垫（倒计时阶段）」（冻结清单为机制语义权威）/ §5.1（含口径 #4 修订记录）/ §8.3 / §九；`render_design.md` V1.5 §5.6；`gdd_idea_0.0.0.1.md` V0.16 §6.6/§6.7；配图 `docs/diagrams/battle_intro_countdown.md`(.html) 与 `battle_main_loop.md`(.html)（已由 GDD 更新到位，**本计划无新增图**）。
> **范围**：开战铺垫倒计时（3s 待调）＋ 全局攻速系数 ×0.6 ＋ 攻击蓄力条，共三件；含既有测试修订与旧 spec_plan 回填。**不含**：battle_flow.html 孤本过时问题（用户未裁决，out of scope）、回能/60s 超时/快进档的联动补偿（battle §九已登记观察点，暂不动）。

---

## 1. 背景与目标

原节奏「开战即混战、攻击读条太快」（计时器初始即满 → 首 tick 出手，battle §5.1 旧口径 #4）。用户 2026-09-02 裁决三件套：

1. **开战铺垫**：开战先播 3s（区间 2~3s 待调）倒计时横幅，逻辑完全冻结；结束后攻击/移动计时器**从零蓄力**，首刀天然延后一个完整攻击间隔。
2. **全局攻速系数 ×0.6**（待调，敌我对称）：出手间隔 0.67~1.43s → 约 1.11~2.38s；只在消耗点乘算，units.json 与属性管线不动。
3. **攻击蓄力条**：单位头顶第四条微条，`attackTimer / attackInterval` 轮询可视化，纯表现零逻辑。

**成功标准**：同 seed 回放逐位一致不破（确定性）；倒计时期间 elapsed/RNG/CombatEvent/计时器/能量零变化（冻结清单）；×2 快进同步加速倒计时且零特判；战斗 HUD（变速/投降）在倒计时期间照常可用；全部既有测试语义保留、仅时序量修订。

## 2. 术语与约定

| GDD 用语 | 代码标识符 | 位置 |
|---|---|---|
| 开战铺垫 / 开战倒计时 | intro countdown：`BattleState.introRemaining` + `beginIntroCountdown / advanceIntroCountdown / isIntroCountdownActive / skipIntroCountdown` | `entities/BattleState.java`（CP2） |
| 开战倒计时横幅 | `render/ui/BattleIntroBanner`（新建，CP8） | UI 域 uiStage |
| 全局攻速系数 | `GameBalance.ATTACK_SPEED_GLOBAL_FACTOR`（CP1） | 消耗点 = `BattleUnit.attackInterval()`（CP5） |
| 攻击蓄力条（第四条微条） | `UnitView.drawBars()` 内 charge 段（CP7） | `render/board/UnitView.java:141` |
| 「开战即就绪」（旧口径 #4） | 旧测试 `BattleUnitTest.timersStartReady`（改写为 `timersStartFromZero`，CP4） | — |
| 冻结清单 | battle §二：`step` 门控折返（GDD §二实现落点②原文「step 门控」） | `BattleSystem.step`（CP3） |

**约定**：Java 标识符全英文（中文只进注释与 `@DisplayName`）；全局数值只进 `GameBalance`；渲染层只读实体；`BattleState` 写方法 framework-internal（口径 #22，测试充当 systems 层可调）。

## 3. 现状盘点（file:line 均为本次实读）

### 可直接复用（零改动）

| 资产 | 位置 | 说明 |
|---|---|---|
| 蓄力条数据源 | `BattleUnit.getAttackTimer()`（`core/src/main/java/com/voidvvv/kz_auto_chess_n/entities/BattleUnit.java:119`）、`attackInterval()`（:115） | 均已 public，render 只读铁律不破（交接点 #4） |
| 快进通路 | `BattleScreen.java:403`（`accumulator += … * speedFactor`）、`GameBalance.BATTLE_SPEED_FACTOR_FAST`（`config/GameBalance.java:47`） | 只乘 accumulator 消费速率；倒计时走同一 step 通路即自动加速（交接点 #6） |
| 60s 计时条 | `render/ui/BattleHud.java:51-54` 读 `state.getElapsed()` | elapsed 不含倒计时 → 倒计时期间自然满格静止 |
| 横幅先例 | `render/ui/ResultBanner.java`（Group + 全屏 ClickCatcher + font setScale） | 倒计时横幅**不加** ClickCatcher（不拦截输入，已批准表现默认） |
| 便利推进 | `BattleSystem.runToEnd`（`systems/BattleSystem.java:182-188`） | step 内门控后自动先耗尽倒计时，测试/控制台零改动 |
| 投降/放弃门控 | `RunFlowSystem.java:84-90/111-118`（Surrender/AbandonRun 仅 BATTLE） | 选 BattleState 字段案后 phase 全程为 BATTLE，门控零改动（见 §5） |

### 需改造

| 文件 | 变更 | CP |
|---|---|---|
| `config/GameBalance.java:17-20` 后 | 增 3 常量 | CP1 |
| `entities/BattleState.java` | 增 introRemaining 字段 + 2 读 3 写方法 | CP2 |
| `systems/BattleSystem.java:121-122/125-131` | startBattle 布防倒计时 + step 门控 | CP3 |
| `entities/BattleUnit.java:55-57` | 计时器初始赋值改 0 + 注释（交接点 #1） | CP4 |
| `entities/BattleUnit.java:114-117` | attackInterval 乘全局系数（交接点 #3 铁律落点） | CP5 |
| `render/board/UnitView.java:141-155` | drawBars 第四条微条 | CP7 |
| `screens/BattleScreen.java` | 横幅装配 + 刷新挂接 | CP9 |

### 需新建

`render/ui/BattleIntroBanner.java`（CP8）＋ 三个测试文件的新增用例（随各 CP）。

## 4. 已确认决策

### 4.1 用户裁决（经 team-lead 交接，照抄）

| # | 决策 | 值 |
|---|---|---|
| D1 | 开战倒计时时长 | **3s**（区间 2~3s 待调） |
| D2 | 全局攻速系数 | **×0.6**（待调，敌我对称，只在消耗点乘算；技能不吃攻速不受影响） |
| D3 | 蓄力条规格 | UnitView.drawBars 第四条：**y=cy+21、高 1px、宽 24px（x=cx−12 同上两条）、暗底白前景**（样式工作值待调） |
| D4 | 表现默认一 | ×2 快进**同步加速倒计时**（同 accumulator 通路，无特判） |
| D5 | 表现默认二 | 倒计时横幅**不拦截输入**（变速/投降照常） |

### 4.2 交接留白点的 planner 裁决（交接点 #1/#2 授权）

| # | 问题 | 裁决 | 理由 |
|---|---|---|---|
| P1 | 倒计时实现二选一（交接点 #2） | **选② BattleState 字段 + `step` 门控** | ① GDD `battle_design.md:69` 实现落点②原文即「step 门控」；② 冻结清单的每条后果（elapsed 不累计/RNG 零消耗/零事件/计时器保持 0/能量冻结）在门控折返下逐条成立，且 elapsed 绝不含倒计时（`beginTick` 在门后，交接硬约束满足）；③ phase 全程 BATTLE → Surrender/AbandonRun 门控（`RunFlowSystem.java:85/113`）、battleHud 可见性（`BattleScreen.java:329`）、D5「不拦截输入」全部零改动兑现——GamePhase 案需另改 4~5 处且把战斗作用域瞬态塞进 run 作用域；④ 快进 = 同一 step 通路，D4 零特判自然成立；⑤ 战斗作用域状态归 `BattleState`（重试即重播，render §5.6「每场战斗播一次」）。图面 `battle_intro_countdown.md` C2 节点「step 整体不调用」按本口径读作「主循环五阶段整体不执行」（其后果清单逐条为真） |
| P2 | 旧 spec_plan 过时差异（交接点 #1） | **回改旧文档两处**（`2026-08-21_phase3_battle_engine.md` §3.2 行 82 与 :465 注释，CP11），不在新计划重复差异表 | 该文档是 battle_design.md:35 明文指向的实现层口径注册表；留着过时的「开局即就绪」行会误导后续执行者。成本两行，且新计划以引用而非复制方式衔接（去重） |

### 4.3 实施口径（本文档新增，语义从 GDD 推导）

| # | 口径 | 依据 |
|---|---|---|
| K1 | 倒计时按 `LOGIC_STEP` 整步递减，浮点下限钳 0；「3s=180 步」允许 ±1 步浮点余量（测试断言用区间） | 确定性 + 浮点防御 |
| K2 | `skipIntroCountdown()` 为测试/调试后门（framework-internal，生产路径不调用）；直构 `BattleState`（不经 startBattle）默认无铺垫——子系统单测不受门控影响 | 最小测试面 |
| K3 | 「开战！」节拍：倒计时归零后横幅以 `BATTLE_INTRO_GO_BEAT_SECONDS`（缺省 0.5s，待调）显示一拍再隐藏——render §5.6「3→2→1→开战！」节拍与「倒计时归零即隐藏」两句张力，常量归 0 即回到严格读法（开放问题 W1） | 两句都可满足的单一实现 |
| K4 | 蓄力条前景宽度 = `min(1, attackTimer / attackInterval)`（恒累计可超 interval → 钳制满格，被眩晕时满格悬停「蓄满待发」） | render §5.6 原文 |
| K5 | 移动计时器同归零、但 `moveCooldown()` **不**乘攻速系数（系数只作用于攻击间隔） | battle §5.1 仅「有效攻击间隔」入式 |

## 5. 总体技术方案

数据流（复用既有图 `docs/diagrams/battle_intro_countdown.md`、`battle_main_loop.md`，无新增图）：

```
StartBattleCommand（RunFlowSystem.java:72-83，不变）
  → BattleSystem.startBattle（派生/布阵/开局效果/初始索敌，不变）
  → state.beginIntroCountdown(3s)                    ← CP3
  → BattleScreen.stepSimulation 每逻辑步仍调 battleSystem.step(state)（不变）
       └─ step 门控（CP3）：isIntroCountdownActive → advanceIntroCountdown(LOGIC_STEP) 后折返
            · beginTick 未达 → elapsed/tick 不动（60s 钟不起表）
            · RNG 零消耗 · 零 CombatEvent · advanceTimers 不执行（计时器保持 0）
       └─ 归零后：五阶段主循环起步，计时器从 0 蓄力（CP4），出手判据 = attackTimer ≥ 1/(攻速×0.6)（CP5）
  → 渲染段（BattleScreen.render）：
       · BattleIntroBanner（CP8/CP9）：BATTLE 期 refresh(state, dt)——只读 introRemaining
       · UnitView.drawBars（CP7）：只读 getAttackTimer()/attackInterval() 画第四条
```

**交互矩阵**（全部零特判）：

| 交互 | 生效路径 | 依据 |
|---|---|---|
| ×2 快进加速倒计时 | `speedFactor` 乘 accumulator（BattleScreen.java:403）→ 每帧 step 次数 ×2 → 倒计时递减 ×2 | D4 |
| 暂停 / 弹窗冻结 | `frozen` 跳过 stepSimulation（BattleScreen.java:303-309） | 既有 |
| 倒计时期间投降 | SurrenderCommand 门控 phase==BATTLE ✓ → `finish(ENEMY_WIN)` → 下一步 step 折返于 isOver → Screen 观察进 RESULT | D5 |
| 60s 计时条静止 | 读 elapsed，倒计时期间恒 0 | 冻结清单 |
| 重试重播倒计时 | 败局重开走新 StartBattle → 新 BattleState 重新布防 | render §5.6 |

**软回滚杠杆**：`ATTACK_SPEED_GLOBAL_FACTOR = 1f` 关闭系数件、`BATTLE_INTRO_COUNTDOWN_SECONDS = 0f` 关闭铺垫件（横幅随之不播），行为即回到旧节奏，无需改代码。

## 6. 改动点清单（评审主入口）

> 修改前代码逐字摘自当前源码（评审可 grep 复核）。路径省略前缀 `core/src/main/java/com/voidvvv/kz_auto_chess_n/`（测试为 `core/src/test/java/com/voidvvv/kz_auto_chess_n/`）。

### CP1. GameBalance 新增战斗节奏三常量
> **[2026-09-09 部分取代]** 见 `2026-09-09_battle_intro_transition.md` 对应 CP；下文按原样存档。
- **类型**：修改类（新增字段）
- **位置**：`config/GameBalance.java:16-20`（「—— 战斗 ——」块）
- **改动说明**：三件套的全局数值唯一落点（拒绝魔法数字）。`BATTLE_INTRO_GO_BEAT_SECONDS` 见口径 K3/W1。常量随首个消费者分任务生效，先行落入不改变任何行为。
- **代码**：
  修改前：
  ```java
      // —— 战斗 ——
      public static final float LOGIC_STEP = 1f / 60f;
      public static final float BATTLE_TIMEOUT = 60f;
      public static final float CRIT_CHANCE = 0.20f;
      public static final float CRIT_MULTIPLIER = 1.5f;
  ```
  修改后：
  ```java
      // —— 战斗 ——
      public static final float LOGIC_STEP = 1f / 60f;
      public static final float BATTLE_TIMEOUT = 60f;
      public static final float CRIT_CHANCE = 0.20f;
      public static final float CRIT_MULTIPLIER = 1.5f;

      // —— 战斗节奏三件套（battle §二开战铺垫 / §5.1；2026-09-02，均待调）——
      /** 开战铺垫倒计时秒数（battle §二：逻辑冻结对峙；区间 2~3s 待调，缺省 3s；0 = 关闭铺垫） */
      public static final float BATTLE_INTRO_COUNTDOWN_SECONDS = 3f;
      /** 倒计时归零后「开战！」横幅节拍秒数（render §5.6「3→2→1→开战！」；0 = 严格按「归零即隐藏」） */
      public static final float BATTLE_INTRO_GO_BEAT_SECONDS = 0.5f;
      /** 全局攻速系数（battle §5.1：敌我对称、只在消耗点乘算——BattleUnit.attackInterval() 一处；1 = 关闭） */
      public static final float ATTACK_SPEED_GLOBAL_FACTOR = 0.6f;
  ```
- **测试要点**：无独立单测（纯常量）；正确性由 CP4/CP5 的引用断言覆盖。

### CP2. BattleState 开战铺垫字段与方法（惰性，无行为变化）
> **[2026-09-09 部分取代]** 见 `2026-09-09_battle_intro_transition.md` 对应 CP；下文按原样存档。
- **类型**：修改类（新增字段 + 4 方法）
- **位置**：`entities/BattleState.java:27-30`（字段块）、`:89-90`（读查询区）、`:125` 前（framework-internal 区，`beginTick` 之前）
- **改动说明**：开战铺垫的战斗作用域状态。本 CP 仅落字段与方法，`step` 尚未门控、`startBattle` 尚未布防 → 全量测试保持绿（K2：直构默认 0 = 不激活）。读写纪律沿口径 #22。
- **代码**：
  字段，修改前：
  ```java
      private int tick;
      private float elapsed;
      private boolean over;
      private BattleOutcome outcome;
  ```
  修改后：
  ```java
      private int tick;
      private float elapsed;
      private float introRemaining;         // 开战铺垫剩余秒数（battle §二；0 = 主循环已起）
      private boolean over;
      private BattleOutcome outcome;
  ```
  读查询，修改前：
  ```java
      public int getTick() { return tick; }
      public float getElapsed() { return elapsed; }
  ```
  修改后：
  ```java
      public int getTick() { return tick; }
      public float getElapsed() { return elapsed; }
      /** 开战铺垫是否进行中（battle §二：倒计时期间主循环冻结——step 门控读取） */
      public boolean isIntroCountdownActive() { return introRemaining > 0f; }
      /** 开战铺垫剩余秒数（倒计时横幅只读） */
      public float getIntroRemaining() { return introRemaining; }
  ```
  framework-internal 方法，修改前：
  ```java
      /** framework-internal：推进一个逻辑步的时钟 */
      public void beginTick() {
  ```
  修改后：
  ```java
      /** framework-internal：startBattle 布阵/开局效果/初始索敌完成后开启倒计时（battle §二流程） */
      public void beginIntroCountdown(float seconds) {
          introRemaining = seconds;
      }

      /** framework-internal：倒计时推进（step 门控内按 LOGIC_STEP 递减；下限 0 不下穿） */
      public void advanceIntroCountdown(float dt) {
          introRemaining = Math.max(0f, introRemaining - dt);
      }

      /** framework-internal：跳过铺垫直入主循环（测试/调试后门，生产路径不调用） */
      public void skipIntroCountdown() {
          introRemaining = 0f;
      }

      /** framework-internal：推进一个逻辑步的时钟 */
      public void beginTick() {
  ```
- **测试要点**（`entities/BattleStateTest.java` 新增两用例，夹具沿用该文件 `unit()/state()` 助手）：
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

### CP3. BattleSystem 布防倒计时 + step 门控（开战铺垫落地）
> **[2026-09-09 部分取代]** 见 `2026-09-09_battle_intro_transition.md` 对应 CP；下文按原样存档。
- **类型**：修改方法（两处）
- **位置**：`systems/BattleSystem.java:121-122`（startBattle 尾）、`:125-131`（step 头）
- **改动说明**：startBattle 在「初始索敌」完成后布防倒计时（GDD 流程：派生完成 → 铺垫 → 主循环）；step 在 isOver 防御之后加门控——主循环五阶段整体不执行（P1 裁决）。`runToEnd`（:182-188）与 `BattleScreen.stepSimulation`（:408-412）调用点零改动：runToEnd 自动先耗尽倒计时（测试 MAX_TICKS=4000 充裕，见 CP6 说明），快进经同一 step 通路加速倒计时（D4）。
- **代码**：
  startBattle，修改前：
  ```java
          targeting.retargetAll(state); // 按 id 序初始索敌
          return state;
      }
  ```
  修改后：
  ```java
          targeting.retargetAll(state); // 按 id 序初始索敌
          state.beginIntroCountdown(GameBalance.BATTLE_INTRO_COUNTDOWN_SECONDS); // 开战铺垫（battle §二）
          return state;
      }
  ```
  step，修改前：
  ```java
      /** 推进一个 LOGIC_STEP（五阶段固定序）；战斗已结束则空操作 */
      public void step(BattleState state) {
          Objects.requireNonNull(state, "state 不能为 null");
          if (state.isOver()) {
              return;
          }
          state.beginTick();
  ```
  修改后：
  ```java
      /** 推进一个 LOGIC_STEP（五阶段固定序）；开战铺垫期间仅推倒计时（battle §二实现落点②「step 门控」：
       *  主循环五阶段整体不执行——elapsed/RNG/事件/计时器/能量全冻结）；战斗已结束则空操作 */
      public void step(BattleState state) {
          Objects.requireNonNull(state, "state 不能为 null");
          if (state.isOver()) {
              return;
          }
          if (state.isIntroCountdownActive()) {
              state.advanceIntroCountdown(GameBalance.LOGIC_STEP); // ×2 快进 = 同一 accumulator 通路，无特判
              return;
          }
          state.beginTick();
  ```
- **测试要点**（`systems/BattleSystemTest.java` 新增两用例；同文件 8 处既有用例需插 `skipIntroCountdown()`，见 CP6）：
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

      @Test
      @DisplayName("首刀延后（§5.1 修订）：铺垫后从零蓄力，首个出手 = 满 1/(aspd×0.6) 秒")
      void firstAttackDelayedByFullInterval() {
          GameData data = data();
          Player player = deployPlayer(data, ranged("sentry", 10000, 1, 1f), 2, 4); // 射程 3，敌 (2,1) 距 3：开局即在射程
          List<WaveSpec> wave = waveOf(melee("dummy", "哥布林", 10000, 1, 1f), 1f, 2, 1);
          BattleState state = start(data, player, wave, 42L);
          state.skipIntroCountdown(); // 隔离变量：本用例只验证蓄力口径，不验证铺垫
          int launchTick = -1;
          for (int i = 0; i < 120 && launchTick < 0; i++) {
              SYSTEM.step(state);
              for (CombatEvent e : state.getEvents()) {
                  if (e.getType() == CombatEvent.Type.ATTACK_LAUNCHED && e.getSourceId() == 1) {
                      launchTick = e.getTick();
                  }
              }
          }
          assertThat(launchTick).isBetween(100, 101); // 1/(1×0.6) = 1.6667s ≈ 100 tick（浮点容差 ±1）
      }
  ```

### CP4. BattleUnit 计时器初始归零（口径 #4 修订，交接点 #1）
- **类型**：修改方法（构造器尾）
- **位置**：`entities/BattleUnit.java:54-57`
- **改动说明**：「开局即就绪」→「开战铺垫后从零蓄力」（battle §5.1 修订记录）。攻击/移动计时器初始 0；「恒累计 + 结转余数」半句不动（`advanceTimers`:147-151 / `consumeAttackTimer`:163-167 零改动）。走位中的单位到达时计时器已同步蓄满即出手（恒累计语义）。注意此时 CP3 已让 step 在倒计时期间不累计 → 两者配合才成立（任务序 T2→T3）。
- **代码**：
  修改前：
  ```java
          this.currentHp = baseStats.getHp();
          this.effectiveStats = baseStats;
          // 开局即就绪（口径 #4）：计时器初始已满——计时器是冷却不是蓄力
          this.attackTimer = 1f / baseStats.getAttackSpeed();
          this.moveTimer = 1f / baseStats.getMoveSpeed();
  ```
  修改后：
  ```java
          this.currentHp = baseStats.getHp();
          this.effectiveStats = baseStats;
          // 开战铺垫后从零蓄力（口径 #4 修订，2026-09-02）：计时器初始为 0——首刀/首步各延后一个完整间隔
          this.attackTimer = 0f;
          this.moveTimer = 0f;
  ```
- **测试要点**（`entities/BattleUnitTest.java:139-145` 改写）：
  修改前：
  ```java
      @Test
      @DisplayName("开局即就绪（口径 #4）：计时器初始已满，本 tick 即可出手/走步")
      void timersStartReady() {
          BattleUnit unit = unit();
          assertThat(unit.canActOnAttackTimer()).isTrue();
          assertThat(unit.canActOnMoveTimer()).isTrue();
      }
  ```
  修改后：
  ```java
      @Test
      @DisplayName("开战铺垫后从零蓄力（口径 #4 修订，2026-09-02）：初始不可出手/走步，蓄满一个间隔方可行动")
      void timersStartFromZero() {
          BattleUnit unit = unit();
          assertThat(unit.canActOnAttackTimer()).isFalse();
          assertThat(unit.canActOnMoveTimer()).isFalse();
          unit.advanceTimers(unit.attackInterval());
          assertThat(unit.canActOnAttackTimer()).isTrue();
          unit.advanceTimers(unit.moveCooldown());
          assertThat(unit.canActOnMoveTimer()).isTrue();
      }
  ```

### CP5. BattleUnit.attackInterval 乘全局系数（消耗点单点，交接点 #3）
- **类型**：修改方法
- **位置**：`entities/BattleUnit.java:114-117`
- **改动说明**：交接铁律落点——**只在消耗点乘算**。`attackInterval()` 是唯一消耗点（全库引用仅 `canActOnAttackTimer`:155 / `consumeAttackTimer`:166 / 蓄力条显示 CP7，本次 grep 核实）；`StatPipeline`、units.json、`moveCooldown()`(:130-132) 一律不动 → ASPD_UP 等 PCT 修正相对收益不变（新测试锚定）、技能不吃攻速天然不受影响（施放走能量门槛，`BattleSystem.act`:204-208 不查 attackInterval）。
- **代码**：
  修改前：
  ```java
      /** 攻击间隔（秒）：1 / 有效攻速 */
      public float attackInterval() {
          return 1f / getEffective(StatKey.ATTACK_SPEED);
      }
  ```
  修改后：
  ```java
      /** 攻击间隔（秒）：1 / (有效攻速 × 全局攻速系数)——系数只在消耗点乘算（battle §5.1，2026-09-02；
       *  canActOnAttackTimer/consumeAttackTimer/蓄力条显示共用本式，units.json 与属性管线不动） */
      public float attackInterval() {
          return 1f / (getEffective(StatKey.ATTACK_SPEED) * GameBalance.ATTACK_SPEED_GLOBAL_FACTOR);
      }
  ```
- **测试要点**（`entities/BattleUnitTest.java`：改 `intervalConversions` + 增 1 用例 + 增 import `com.voidvvv.kz_auto_chess_n.config.GameBalance`）：
  修改前（:129-137）：
  ```java
      @Test
      @DisplayName("attackInterval/moveCooldown 换算：aspd2 → 0.5s/击；ms2 → 0.5s/格")
      void intervalConversions() {
          BattleUnit fast = new BattleUnit(2, tpl(), 1, Side.ENEMY, skill(),
                  new BattleStats(100f, 10f, 5f, 2f, 1f, 2f, 0f, 100f, 0f));
          assertThat(fast.attackInterval()).isCloseTo(0.5f, within(1e-6f));
          assertThat(fast.moveCooldown()).isCloseTo(0.5f, within(1e-6f));
          assertThat(unit().attackInterval()).isCloseTo(1f, within(1e-6f));
      }
  ```
  修改后：
  ```java
      @Test
      @DisplayName("attackInterval/moveCooldown 换算：aspd2 → 1/(2×0.6)s/击；ms2 → 0.5s/格（移动不吃系数）")
      void intervalConversions() {
          BattleUnit fast = new BattleUnit(2, tpl(), 1, Side.ENEMY, skill(),
                  new BattleStats(100f, 10f, 5f, 2f, 1f, 2f, 0f, 100f, 0f));
          assertThat(fast.attackInterval())
                  .isCloseTo(1f / (2f * GameBalance.ATTACK_SPEED_GLOBAL_FACTOR), within(1e-6f));
          assertThat(fast.moveCooldown()).isCloseTo(0.5f, within(1e-6f)); // 口径 K5：移速不受攻速系数影响
          assertThat(unit().attackInterval())
                  .isCloseTo(1f / GameBalance.ATTACK_SPEED_GLOBAL_FACTOR, within(1e-6f));
      }

      @Test
      @DisplayName("攻速系数不改变 PCT 修正相对收益：ASPD_UP +30% → 间隔缩短恰 1.3 倍")
      void attackSpeedStatusRelativeGainUnaffectedByFactor() {
          BattleUnit unit = unit();
          float base = unit.attackInterval();
          unit.addStatus(new ActiveStatus(StatusType.ASPD_UP, 9, 30f, 5f));
          assertThat(base / unit.attackInterval()).isCloseTo(1.3f, within(1e-4f));
      }
  ```

### CP6. 既有测试修订一：BattleSystemTest 8 处插入 skipIntro
- **类型**：修改方法（8 个用例各插 1 行；因 CP3）
- **位置**：`systems/BattleSystemTest.java` —— `actionOrderById`(:201)、`castConsumesSoleAction`(:223)、`attackTimerCarryOver`(:250)、`mutualKillSameTick`(:270)、`deathFreesCellAndRetargets`(:307)、`retargetEvery120Ticks`(:330)、`runToEndRespectsCap`(:374)、`inlineCastOnEnergyCrossing`(:387)，各为 `BattleState state = start(...)` 之后
- **改动说明**：CP3 后 startBattle 布防倒计时，既有用例直接 step 会先耗 180 步空转。这些用例关注主循环语义而非铺垫（铺垫由 CP3 新用例覆盖），按口径 K2 统一跳过。**不插**的用例及理由：`timeoutCountsAsPlayerLoss`（4000 − 180 = 3820 ≥ 3600，顺带隐式验证 elapsed 不含倒计时）、`deterministicReplay` / `rngConsumptionEqualsAttackRolls`（runToEnd 4000 且只断言确定性与审计）、`RunFlowSystemTest` 全部（MAX_TICKS=4000，champion 秒杀 / trainee 速败 / 零棋子第一步即判负，倒计时余量充足）、`MovementSystemTest`（直调 tryStep，不经计时器）。
- **代码**（8 处同款，示例取 :201-202，其余逐字同构）：
  修改前：
  ```java
          BattleState state = start(data, player, wave, 42L);
  ```
  修改后：
  ```java
          BattleState state = start(data, player, wave, 42L);
          state.skipIntroCountdown(); // 既有用例关注主循环语义，跳过开战铺垫（battle §二新口径）
  ```
- **测试要点**：本 CP 自身即测试修订；验收 = 该文件全绿。

### CP7. 既有测试修订二：节奏变化引发的时序量调整
- **类型**：修改方法（5 个用例；因 CP4+CP5 联合——计时器归零 + 间隔 ×1/0.6）
- **位置**：`systems/BattleSystemTest.java`，逐用例如下。数值推导：aspd1 → 间隔 1.6667s=100 tick；aspd2 → 50 tick；跳格冷却 1/moveSpeed=1s=60 tick（K5 不变）。
- **改动说明与代码**：
  ① `actionOrderById`(:203-204)——双方距 3，60 tick 接敌 + 100 tick 蓄满，首 HIT 落 ~tick 100（id 1 先结算的断言语义不变）：
  修改前：
  ```java
          SYSTEM.step(state); // tick1：双方各走一步（玩家先动、占 (2,3)；敌方跟进 (2,2)）
          SYSTEM.step(state); // tick2：双方同处射程起点——id 1 先结算
  ```
  修改后：
  ```java
          for (int i = 0; i < 120; i++) { // 归零蓄力：接敌 60 tick + 首刀 100 tick（浮点余量）
              SYSTEM.step(state);
          }
  ```
  ② `castConsumesSoleAction`(:240)——施放仍第一步即发（能量门槛与计时器无关），其后走步恢复需 60+ tick：
  修改前：
  ```java
          SYSTEM.step(state); // 能量清零后走步恢复
          assertThat(playerUnit.getGridY()).isLessThan(y);
  ```
  修改后：
  ```java
          for (int i = 0; i < 70; i++) { // 首步 60 tick（归零蓄力）后走步恢复
              SYSTEM.step(state);
          }
          assertThat(playerUnit.getGridY()).isLessThan(y);
  ```
  ③ `attackTimerCarryOver`(:245-253)——aspd2 间隔 50 tick、60 tick 接敌：出手落 60/110/160/210/260，300 步 5 次（维持 4~6 断言与「无掉次」语义）：
  修改前：
  ```java
      @DisplayName("计时器结转（口径 #4）：aspd 2 → 0.5s/击，150 tick 内 4~6 次普攻无掉次")
  ```
  ```java
          for (int i = 0; i < 150; i++) {
              SYSTEM.step(state);
          }
  ```
  修改后：
  ```java
      @DisplayName("计时器结转（口径 #4）：aspd 2 → 1/(2×0.6)s/击，300 tick 内 4~6 次普攻无掉次")
  ```
  ```java
          for (int i = 0; i < 300; i++) { // 间隔 50 tick + 接敌 60 tick：出手 60/110/…/260 共 5 次
              SYSTEM.step(state);
          }
  ```
  ④ `mutualKillSameTick`(:272)——aspd1 间隔 100 tick、60 tick 接敌：第 3 刀落 ~tick 300（同 tick 双灭语义不变，留 100 tick 余量）：
  修改前：
  ```java
          SYSTEM.runToEnd(state, 300);
  ```
  修改后：
  ```java
          SYSTEM.runToEnd(state, 400); // 第 3 刀落 ~tick 300（间隔 100 + 接敌 60），留浮点余量
  ```
  ⑤ `inlineCastOnEnergyCrossing`(:390)——弓手距 4：60 tick 走一步进射程 + 100 tick 蓄满发射 + ~30 tick 弹道：
  修改前：
  ```java
          for (int i = 0; i < 60; i++) { // 弹道对开进目标：对向闭合约 7 格/秒
              SYSTEM.step(state);
          }
  ```
  修改后：
  ```java
          for (int i = 0; i < 240; i++) { // 走位 60 + 蓄力 100 + 弹道 ~30 tick（归零蓄力 + 系数 0.6）
              SYSTEM.step(state);
          }
  ```
- **测试要点**：本 CP 自身即测试修订；验收 = 全绿。若个别用例因浮点边界红（W4）：只调步数余量，禁改断言语义。

### CP8. UnitView.drawBars 第四条攻击蓄力条（纯表现）
- **类型**：修改方法（+1 常量 +1 段注释）
- **位置**：`render/board/UnitView.java:23`（常量区）、`:139`（节注释）、`:148-149` 后（能量条与星级点之间）
- **改动说明**：D3 规格——y=cy+21、高 1px、宽 24px、x=cx−12（与血/能量条同基准）、暗底白前景；数据逐帧轮询不发事件（与 §5.4 状态图标同路线）；钳制满格（K4）；开战铺垫期间恒空条（attackTimer=0）；不占动画状态位。零新增接口（复用 `getAttackTimer()/attackInterval()`，交接点 #4）。
- **代码**：
  常量，修改前：
  ```java
      private static final Color BAR_YELLOW = new Color(0.95f, 0.85f, 0.2f, 1f);
  ```
  修改后：
  ```java
      private static final Color BAR_YELLOW = new Color(0.95f, 0.85f, 0.2f, 1f);
      private static final Color BAR_DARK = new Color(0.12f, 0.12f, 0.15f, 1f); // 蓄力条底槽（render §5.6，待调）
  ```
  节注释，修改前：
  ```java
      // —— 血条（红绿 2px）/ 能量条（黄 1px）/ 星级色点（口径 #19） ——
  ```
  修改后：
  ```java
      // —— 血条（红绿 2px）/ 能量条（黄 1px）/ 攻击蓄力条（白 1px，render §5.6）/ 星级色点（口径 #19） ——
  ```
  方法体，修改前：
  ```java
          batch.setColor(BAR_YELLOW);
          batch.draw(white, cx - 12f, cy + 19f, 24f * unit.getEnergy() / GameBalance.ENERGY_MAX, 1f);
          batch.setColor(STAR_GOLD);
  ```
  修改后：
  ```java
          batch.setColor(BAR_YELLOW);
          batch.draw(white, cx - 12f, cy + 19f, 24f * unit.getEnergy() / GameBalance.ENERGY_MAX, 1f);
          // 攻击蓄力条（render §5.6 第四条微条）：轮询 attackTimer/attackInterval、钳制满格（眩晕时满格悬停）；纯表现零事件
          float charge = Math.min(1f, unit.getAttackTimer() / unit.attackInterval());
          batch.setColor(BAR_DARK);
          batch.draw(white, cx - 12f, cy + 21f, 24f, 1f);
          batch.setColor(WHITE);
          batch.draw(white, cx - 12f, cy + 21f, 24f * charge, 1f);
          batch.setColor(STAR_GOLD);
  ```
- **测试要点**：纯绘制无逻辑分支，不设单测（与血/能量条同待遇）；走 §7 T6 手验清单第 3~4 条。

### CP9. 新建 BattleIntroBanner（UI 域倒计时横幅）
> **[2026-09-09 部分取代]** 见 `2026-09-09_battle_intro_transition.md` 对应 CP；下文按原样存档。
- **类型**：新建文件
- **位置**：`render/ui/BattleIntroBanner.java`
- **改动说明**：render §5.6 规格——UI 域 uiStage 顶层 Group（ResultBanner 同通路）；中央大字倒数「3→2→1」，归零后「开战！」节拍 `BATTLE_INTRO_GO_BEAT_SECONDS` 再隐藏（K3）；**无任何输入监听**（D5：不拦截 HUD 变速/投降——Scene2D 无 listener 的 Actor 不消费输入）；只读 `BattleState`（渲染铁律 1）；重试/新战斗由 Screen 离开 BATTLE 时 `reset()` 重播。字号/坐标为工作值待调（沿 ResultBanner 硬编码先例）。
- **代码**（完整新建）：
  ```java
  package com.voidvvv.kz_auto_chess_n.render.ui;

  import com.badlogic.gdx.graphics.Color;
  import com.badlogic.gdx.graphics.g2d.Batch;
  import com.badlogic.gdx.scenes.scene2d.Group;
  import com.voidvvv.kz_auto_chess_n.config.GameBalance;
  import com.voidvvv.kz_auto_chess_n.entities.BattleState;
  import com.voidvvv.kz_auto_chess_n.render.Assets;
  import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
  import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;

  /**
   * 开战铺垫倒计时横幅（render §5.6 / battle §二）：BATTLE 进入开战铺垫即显示，
   * 中央大字倒数 3→2→1，归零后「开战！」短节拍（BATTLE_INTRO_GO_BEAT_SECONDS，待调）即隐藏。
   * 纯表现层：无输入监听（不拦截 HUD 变速/投降）、不产事件；倒计时数据只读 BattleState。
   */
  public final class BattleIntroBanner extends Group {

      /** 倒计时文案：剩余秒数向上取整（调用方保证 > 0）——静态纯函数供单测 */
      static String countdownText(float introRemaining) {
          return String.valueOf((int) Math.ceil(introRemaining));
      }

      private final Assets assets;
      private String text = "";
      private float goBeatElapsed = -1f; // -1 = 未显示（含未进入「开战！」节拍）

      public BattleIntroBanner(Assets assets) {
          this.assets = assets;
          setVisible(false);
      }

      /** 每帧刷新（BATTLE 期由 Screen 调用；冻结期 dt 传 0——暂停/弹窗时节拍不走） */
      public void refresh(BattleState state, float dt) {
          if (state.isIntroCountdownActive()) {
              text = countdownText(state.getIntroRemaining());
              goBeatElapsed = 0f;
          } else if (goBeatElapsed >= 0f) {
              text = "开战！";
              goBeatElapsed += dt;
              if (goBeatElapsed >= GameBalance.BATTLE_INTRO_GO_BEAT_SECONDS) {
                  goBeatElapsed = -1f; // 节拍播完隐藏
              }
          }
          setVisible(goBeatElapsed >= 0f);
      }

      /** 离开 BATTLE 即复位（重试/新战斗重播，render §5.6「每场战斗播一次」） */
      public void reset() {
          goBeatElapsed = -1f;
          setVisible(false);
      }

      @Override
      public void draw(Batch batch, float parentAlpha) {
          super.draw(batch, parentAlpha);
          batch.setColor(0f, 0f, 0f, 0.25f * parentAlpha); // 半透明压暗带（工作值待调，render §5.6「可选」）
          batch.draw(assets.region(PlaceholderKeys.WHITE), 0f, 120f, BoardGeometry.VIRTUAL_W, 120f);
          batch.setColor(Color.WHITE);
          assets.font().getData().setScale(4f);
          assets.font().setColor(Color.WHITE);
          // 单数字宽 ~48px / 「开战！」3 字宽 ~144px，居中基准（工作值待调）
          assets.font().draw(batch, text, text.length() == 1 ? 296f : 248f, 200f);
          assets.font().getData().setScale(1f); // 用后即还（共用字体，ResultBanner 同款纪律）
          assets.font().setColor(Color.WHITE);
      }
  }
  ```
- **测试要点**：新建 `render/ui/BattleIntroBannerTextTest.java`（沿 ChestDialogTextTest 静态助手先例，headless 可跑）：
  ```java
  package com.voidvvv.kz_auto_chess_n.render.ui;

  import org.junit.jupiter.api.DisplayName;
  import org.junit.jupiter.api.Test;

  import static org.assertj.core.api.Assertions.assertThat;

  /** 开战倒计时文案纯函数测试（render §5.6） */
  class BattleIntroBannerTextTest {

      @Test
      @DisplayName("倒计时文案 = 剩余秒数向上取整（3.0→3 / 2.98→3 / 1.0→1 / 0.02→1）")
      void countdownTextRoundsUp() {
          assertThat(BattleIntroBanner.countdownText(3f)).isEqualTo("3");
          assertThat(BattleIntroBanner.countdownText(2.9833f)).isEqualTo("3");
          assertThat(BattleIntroBanner.countdownText(2f)).isEqualTo("2");
          assertThat(BattleIntroBanner.countdownText(1f)).isEqualTo("1");
          assertThat(BattleIntroBanner.countdownText(0.0167f)).isEqualTo("1");
      }
  }
  ```

### CP10. BattleScreen 装配与刷新挂接
> **[2026-09-09 部分取代]** 见 `2026-09-09_battle_intro_transition.md` 对应 CP；下文按原样存档。
- **类型**：修改类（1 字段 + 构造器 2 行 + render 挂接）
- **位置**：`screens/BattleScreen.java:96`（字段区，resultBanner 之后）、`:175`（构造器，resultBanner 构造之后）、`:226-227`（addActor，notificationPanel 与 hoverPreview 之间）、`:345-349`（render，resultBanner 刷新块之后）
- **改动说明**：screens 是唯一装配点（architecture §七）。z 序：面板之上、悬停卡之下；与 dialogStage 无涉（弹窗永远最上）。`frozen ? 0f : delta` 沿 hoverPreview 同款冻结纪律（:341）。逻辑段（stepSimulation）零改动——门控在 CP3。
- **代码**：
  字段，修改前：
  ```java
      private final ResultBanner resultBanner;
  ```
  修改后：
  ```java
      private final ResultBanner resultBanner;
      private final BattleIntroBanner introBanner;
  ```
  构造器，修改前：
  ```java
          this.resultBanner = new ResultBanner(runFlowSystem, contextSupplier(), assets);
  ```
  修改后：
  ```java
          this.resultBanner = new ResultBanner(runFlowSystem, contextSupplier(), assets);
          this.introBanner = new BattleIntroBanner(assets);
  ```
  addActor，修改前：
  ```java
          uiStage.addActor(notificationPanel);
          uiStage.addActor(hoverPreview); // 最上层：瞬态悬停卡（无输入监听，不阻断任何交互）
  ```
  修改后：
  ```java
          uiStage.addActor(notificationPanel);
          uiStage.addActor(introBanner); // 开战倒计时横幅（render §5.6）：面板之上、悬停卡之下；无输入监听
          uiStage.addActor(hoverPreview); // 最上层：瞬态悬停卡（无输入监听，不阻断任何交互）
  ```
  render 挂接，修改前：
  ```java
          resultBanner.setVisible(phase == GamePhase.RESULT);
          if (phase == GamePhase.RESULT && runContext.getBattleState() != null) {
              resultBanner.refresh(runContext.getBattleState().getOutcome(),
                      resultStatusLine(runContext.getRunState())); // 横幅读 outcome + 机会制状态行（GDD §2.2）
          }
  ```
  修改后：
  ```java
          resultBanner.setVisible(phase == GamePhase.RESULT);
          if (phase == GamePhase.RESULT && runContext.getBattleState() != null) {
              resultBanner.refresh(runContext.getBattleState().getOutcome(),
                      resultStatusLine(runContext.getRunState())); // 横幅读 outcome + 机会制状态行（GDD §2.2）
          }
          if (phase == GamePhase.BATTLE && runContext.getBattleState() != null) {
              introBanner.refresh(runContext.getBattleState(), frozen ? 0f : delta); // 开战铺垫横幅；冻结期节拍不走
          } else {
              introBanner.reset(); // 离开 BATTLE 复位（重试/新战斗重播）
          }
  ```
- **测试要点**：libGDX Screen 无 headless 单测先例（BattleScreenStatusLineTest 仅测静态方法）；走 §7 T5/T6 手验清单第 1~2、5 条。

### CP11. 旧 spec_plan 回填（交接点 #1，裁决 P2）
- **类型**：修改文档（两处，`docs/spec_plan/2026-08-21_phase3_battle_engine.md`）
- **位置**：`:82`（§3.2 实现层口径 #4 行）、`:465`（§7.6 startBattle javadoc）
- **改动说明**：该文档是 battle_design.md:35 明文指向的实现层口径注册表，两处「开局即就绪/计时器就绪」表述已过时，回填为修订后口径并指向本计划（裁决 P2 理由见 §4.2）。
- **代码**：
  :82，修改前：
  ```
  | 4 | 攻击/移动计时器每 tick 恒累计（与是否在射程无关），出手/走步消耗后**结转余数**；开局即就绪 | battle §5.1"开战即就绪（无前摇）"——计时器是冷却不是蓄力 |
  ```
  修改后：
  ```
  | 4 | 攻击/移动计时器每 tick 恒累计（与是否在射程无关），出手/走步消耗后**结转余数**；开战铺垫倒计时后从零蓄力（2026-09-02 修订「开局即就绪」，battle §5.1 修订记录；实施见 2026-09-02_battle_pacing.md） | battle §5.1（V1.7 修订）——计时器是蓄力不是冷却 |
  ```
  :464-465，修改前：
  ```
       *  → BattleState 布格 → 开局效果落地（openingEffects，口径 #17）
       *  → HP=maxHp、能量 0、计时器就绪 → 按 id 序初始 findTarget */
  ```
  修改后：
  ```
       *  → BattleState 布格 → 开局效果落地（openingEffects，口径 #17）
       *  → HP=maxHp、能量 0、计时器归零 + 开战铺垫倒计时开启（2026-09-02 修订）→ 按 id 序初始 findTarget */
  ```
- **测试要点**：无（纯文档）。

## 7. 分阶段任务拆解

> 每任务收尾必须全绿才可进入下一任务（小步可验证）。TDD 序：任务内先落测试（RED）再落实现（GREEN）。
> 测试验证纪律：gradle 成功时控制台零输出——用**退出码** + `core/build/test-results/test/TEST-*.xml` 聚合计数核对（`gradlew.bat :core:test`，bash 下 `./gradlew :core:test; echo $?`）。

| 任务 | 所含 CP | 前置 | 验收标准 |
|---|---|---|---|
| T1 基础（惰性） | CP1、CP2 | — | BattleStateTest 新增 2 用例绿；全量测试与基线一致（新字段默认 0，零行为变化） |
| T2 开战铺垫落地 | CP3、CP6 | T1 | `introCountdownFreezesMainLoop` / `firstAttackDelayedByFullInterval` 绿（冻结清单逐条断言：tick/elapsed/事件/RNG/计时器）；8 处 skipIntro 后 BattleSystemTest 全绿；RunFlowSystemTest 零改动仍绿 |
| T3 节奏两件（归零 + 系数） | CP4、CP5、CP7 | T2 | BattleUnitTest 改写 2 + 新增 1 用例绿（含「PCT 相对收益不变」锚定）；CP7 五处时序修订后 BattleSystemTest 全绿；`deterministicReplay` 不改自绿（确定性不破） |
| T4 攻击蓄力条 | CP8 | —（建议 T3 后一并手验） | 全量测试绿；手验：蓄力条随出手循环增长/清空、移动中照常蓄力、眩晕满格悬停、铺垫期空条 |
| T5 倒计时横幅 | CP9、CP10 | T2 | BattleIntroBannerTextTest 绿；手验：3→2→1→开战！节拍、×2 加速、暂停冻结、投降可用、重试重播 |
| T6 文档回填与总验收 | CP11 | T1~T5 | 旧 spec_plan 两处回填；`./gradlew :core:test` 退出码 0 且 XML 聚合无 fail/error；§下 手验清单全过 |

**手验清单（T5/T6，跑 lwjgl 桌面包）**：
1. 点开战：中央横幅 3→2→1→「开战！」（~0.5s），双方 idle 对峙、蓄力条空。
2. 倒计时期间：点 ×2 倒数加速一倍；点投降立即进 RESULT；Esc 暂停冻结倒数。
3. 归零后：已在射程的远程单位约 1.67s（aspd1）/1.11s（aspd1.5）后首刀；蓄力条同步增长、出手清空重蓄。
4. 走位中单位蓄力条照常增长、到位即出手；被眩晕单位满格悬停。
5. 60s 计时条倒计时期间满格静止，主循环起表后才走。
6. 整场体感时长约 ×1.4~1.7（battle §九观察点登记项，不做补偿）。

## 8. 风险与开放问题

| # | 级别 | 问题 | 处置 |
|---|---|---|---|
| W1 | WARNING | render §5.6 内部张力：「3→2→1→开战！」节拍 vs「倒计时归零即隐藏」 | 已按 K3 实现两读法兼得（`BATTLE_INTRO_GO_BEAT_SECONDS` 缺省 0.5s 待调，归 0 即严格读法）；样式并入 render §十一「血条/能量条最终样式」待定项一并调 |
| W2 | WARNING | 零棋子开战同样播 3s 倒计时后第一步即判负（GDD 无豁免条款） | 按 GDD 字面执行；实测若体感尴尬再提请裁决（届时可在 startBattle 对空侧跳过布防，一行变更） |
| W3 | WARNING | battle §九已登记联动观察点：战斗时长 ×1.4~1.7 → 60s 超时风险↑、出手变少 → 技能频率↓ | 本轮不补偿（用户裁决「先看手感」）；软回滚杠杆见 §5 |
| W4 | WARNING | CP7 时序步数按公式推算（浮点累计 ±1 tick 级漂移） | 红则只调步数余量（isBetween 已留容差），禁改断言语义；XML 定位 |
| W5 | WARNING | `tools/BattleConsoleMain` 输出前 ~3s 无事件（倒计时） | 预期行为，工具零改动；如需观察可在该工具自加 skipIntroCountdown（out of scope） |
| W6 | 声明 | `docs/diagrams/battle_flow.html` V1.0 孤本过时 | **out of scope**（用户未裁决，交接点 #6）；本计划依据的 battle_main_loop / battle_intro_countdown 两图已是新版 |
| W7 | 提示 | RESULT 期蓄力条随血/能量条同规则继续绘制（既有行为） | 与现状一致，不处理 |

**回滚面**：按任务粒度 git revert（T1~T5 各自独立成 commit）；机制级软回滚 = 常量归零/归一（§5），横幅随之不播、间隔还原，无需动代码。

## 9. 附录：用户确认记录

- 2026-09-02（经 team-lead 交接的已批准裁决）：开战倒计时 3s（区间 2~3s 待调）；全局攻速系数 ×0.6（敌我对称、只在消耗点乘算、units.json 与属性管线不动、技能不吃攻速不受影响）；蓄力条规格 y=cy+21/高 1px/宽 24px/暗底白前景（样式待调）；表现层默认——×2 快进同步加速倒计时（同 accumulator 通路无特判）、倒计时横幅不拦截输入。
- 2026-09-02（battle_design.md V1.7 §5.1 修订记录，用户裁决）：口径 #4「开局即就绪」→「开战铺垫倒计时后从零蓄力」，「恒累计 + 结转余数」保留；修订理由——出手读条太快、开战即混战，缺乏紧张感。
- 交接留白（team-lead 授权 planner 裁决）：倒计时实现选型（裁决 P1：BattleState 字段 + step 门控）；旧 spec_plan 差异处理（裁决 P2：回填两处）。依据与理由见 §4.2。
