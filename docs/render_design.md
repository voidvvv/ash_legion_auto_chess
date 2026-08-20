# 🖼️ 渲染架构设计文档

> **版本**：V1.0  
> **定位**：视口与坐标系 / 双通路渲染模型 / 帧循环与插值 / 事件驱动表现 / 视图类结构 / 图集规范 / 像素规则 / HUD 布局定稿（Phase 4 渲染层开工依据）  
> **依据**：GDD V0.9 §九（美术规格）、`architecture_design.md` V1.5（双实体 / Screen / 输入归属）、`battle_design.md` V1.1（主循环 / 跳格插值 / CombatEvent）、`user_input_design.md` V1.4 §2.5（输入归属）/§3（坐标陷阱）  
> **配图**：`docs/diagrams/battle_screen_layout.html`（V1.0 定稿）  
> **定稿日**：2026-08-20

---

## 一、已定决策总览

| # | 决策 | 结论 |
|---|------|------|
| 1 | UI 坐标系 | **UI 与世界同用 640×360 虚拟坐标**（两个同参数 FitViewport 实例）——UI 与棋盘共享像素颗粒感，风格浑然一体 |
| 2 | 渲染模型 | **双通路**：棋盘域 `SpriteBatch` 自绘 / UI 域 `uiStage`（Scene2D）——与输入归属（input §2.5）同一张地图 |
| 3 | HUD 布局 | **定稿**：`battle_screen_layout.html` V1.0（八区域坐标表见 §九），GDD 待定项"UI 布局草图"销账 |
| 4 | 战斗期商店栏 | **退场**，替换为战斗 HUD（变速 / 60s 计时条 / 投降）——聚焦观战 |
| 5 | 逻辑/表现时差 | 逻辑 60Hz 固定步 + **渲染插值**（UnitView 上一格→当前格 lerp） |
| 6 | 一次性表现 | **CombatEvent 事件驱动**（动画 / 飘字 / 音效）——事件流的第二用途（第一是单元测试） |
| 7 | 像素规则 | Nearest 过滤 / 整数像素吸附 / 禁旋转（死亡缩放与弹道为表现例外） |
| 8 | 资源策略 | LoadingScreen 全量加载、永不卸载；左右朝向用 flip 参数，不画两套 |

---

## 二、视口与坐标系

### 2.1 双 Viewport 同参数
```java
// 世界（棋盘域）：正交相机 + FitViewport
worldViewport = new FitViewport(640, 360, worldCamera);
// UI 域：Stage 使用同参数的独立 FitViewport
uiStage = new Stage(new FitViewport(640, 360, uiCamera));
```
- **两个实例、同一虚拟分辨率**：坐标语义不同（世界坐标 vs Stage 坐标），换算各自独立——输入层 unproject 归属见 input §3（棋盘处理器用棋盘 viewport，UI 用 uiStage 的）
- `resize()` 时两个 viewport 同步 `update()`
- PC（lwjgl3）默认窗口 **1280×720（整数 2 倍）**；Android 横屏可能非整数倍缩放——靠整数像素吸附（§八）保证画面内部干净，外缘允许轻微黑边

### 2.2 为什么 UI 不用屏幕原生坐标
UI 用原生坐标字体更锐利，但高清 UI 浮在低清像素棋盘上会产生"贴膜感"，破坏风格统一。像素字体（Zpix / Fusion Pixel，GDD §9.4）在 360p 下就是设计目标的一部分。**已定：统一虚拟坐标。**

---

## 三、双通路渲染模型

### 3.1 两域对照（与 input §2.5 输入归属一一对应）

| | 棋盘域通路 | UI 域通路 |
|---|---|---|
| 内容 | 棋盘 6×7、备战席、出售区、棋子、弹道、飘字、特效、拖拽 ghost | 顶栏、背包、羁绊面板、开战按钮、商店栏、战斗 HUD、弹窗、详情面板 |
| 技术 | `SpriteBatch` 自绘（`render/board/`） | `uiStage` Scene2D Actor（`render/ui/`） |
| 输入 | `boardProcessor`（unproject 棋盘 viewport） | Stage 自带命中检测 |
| 数据 | 只读 `BattleUnit` / `Player` 名单 | 只读 `Player` / `ShopSystem` / `RunState` |

### 3.2 每帧绘制顺序（固定）
```
① 背景层（场景美术，Phase 7 细化）
② 棋盘格与高亮（敌我分区着色 / 布阵提示 / 拖拽落点预览）
③ 备战席格子、出售区
④ 棋子（UnitView：插值位置 + 动画 + 血条/能量条/星级底光）
⑤ 弹道（ProjectileView）
⑥ 特效层（FxLayer）
⑦ 飘字层（FloatingText）
⑧ 拖拽 ghost（半透明，跟随指针）
⑨ uiStage.draw()          ← UI 永远盖在棋盘域之上
⑩ dialogStage.draw()      ← 弹窗永远最上（若有）
```
- ②~⑧ 在同一个 `batch.begin()/end()` 内完成，按序即分层
- MVP 不做 render target / shader 分层——单 batch 直绘，640×360 像素画 drawcall 无压力

---

## 四、帧循环：逻辑步与插值

### 4.1 结构（接 input §5.3 的逻辑段）
```java
public void render(float delta) {
    // —— 逻辑段（input §5.3 已定：钳制 / 最大 tick / 阶段感知）——
    accumulator += Math.min(delta, MAX_DELTA);
    while (accumulator >= LOGIC_STEP && ticks++ < MAX_TICKS_PER_FRAME) {
        commandManager.executeAll(runContext);
        if (phase == BATTLE) battleSystem.tick(LOGIC_STEP);   // 产生 CombatEvent
        accumulator -= LOGIC_STEP;
    }
    // —— 渲染段 ——
    float alpha = accumulator / LOGIC_STEP;   // ∈ [0,1)：本帧未消费时间的比例
    battleRenderer.draw(alpha);               // 棋盘域用 alpha 插值
    uiStage.draw();
    dialogStage.draw();
}
```

### 4.2 插值规则（离散跳格 → 平滑滑动）
- 每个 `UnitView` 保存 `fromCell / toCell / 切换时刻`：逻辑跳格（冷却 = 1/moveSpeed）在表现上即"以 moveSpeed 匀速滑过一格"
- 位置 = `lerp(from, to, min(1, elapsed × moveSpeed))`；到达后停稳待机
- 表现落后逻辑最多 1 tick（16ms），不可感知；**不做预测/回滚**
- 战斗结束 / 重试：全部 UnitView 随 `BattleState` **整体销毁重建**——与双实体的"战斗作用域"对齐，视图生命周期 = 一场战斗

### 4.3 事件消费时机
- 逻辑 tick 产生的 `CombatEvent` 进入**帧事件缓冲**，渲染段一次性消费
- 同帧多条（如 AOE 命中 4 个目标）：4 条 `Hit` 全部生成飘字，各自独立动画；同一 UnitView 的同类动画取最新触发
- 事件消费后清空缓冲——渲染帧与逻辑帧的事件严格对应，不重播

---

## 五、事件驱动的表现层

### 5.1 UnitView 动画状态机（battle_design §三 预告的"渲染层 FSM"在此落地）

| 状态 | 触发 | 说明 |
|------|------|------|
| `Idle` | 默认 / 一次性动画播完 | 2 帧呼吸 |
| `Walk` | 逻辑格变化 | 插值位移中自动保持 |
| `Attack` | `AttackLaunched` / `Hit`（近战即时） | 3 帧前摇/命中/收招 |
| `Cast` | `Cast` | 2 帧 + 技能闪光 |
| `Death` | `UnitDied` | **锁定态**：3 帧或缩放淡出，播完停留尸体残影直至清扫 |

- 优先级：`Death（锁定） > Attack / Cast（可打断 Idle/Walk） > Walk > Idle`
- `HitFlash`（受击白闪 0.1s）为**可叠加层**，不占用状态位
- 这就是"逻辑无状态仲裁、表现有状态播放"分层的渲染侧兑现（battle_design §三）

### 5.2 飘字（FloatingText）
- 分色（GDD §9.3）：普攻白 / **暴击橙红** / 技能紫 / 治疗绿 / 护盾蓝
- 像素数字字模；上浮 + 淡出 0.8s；**对象池**（高频，禁逐帧分配）
- 同一目标同帧多段伤害：错位堆叠显示

### 5.3 弹道与特效
- `ProjectileView`：按弹道速度连续飞行；HOMING 追踪目标当前格；LINE 允许**旋转绘制**（像素规则的唯一特例，小贴图破碎感可接受）
- 命中粒子（FxLayer）：MVP 手绘闪光帧，ParticleEffect 备选（Phase 7）
- 音效挂钩：事件 → `AudioManager`（MVP 直接 `Gdx.sound.play`，Phase 7 池化限声道）

---

## 六、视图类结构（`render/` 包）

```
render/
├── board/                      # 棋盘域自绘族（boardProcessor 辖区）
│   ├── BattleRenderer.java     #   总绘制：格子/高亮/备战席/出售区，持有下列视图集合
│   ├── UnitView.java           #   单位视图：动画FSM/插值/血条/能量条/星级底光（id 索引）
│   ├── ProjectileView.java     #   弹道
│   ├── FloatingText.java       #   飘字（池）
│   └── FxLayer.java            #   特效（池）
└── ui/                         # UI 域 Stage Actor 族（uiStage 辖区）
    ├── ShopBar.java            #   商店栏（5 卡 + 刷新 + 买经验）
    ├── BattleHud.java          #   战斗 HUD（变速/计时/投降），战斗期替换商店栏
    ├── SynergyPanel.java       #   羁绊面板
    ├── InventoryPanel.java     #   背包（两段式点击穿戴的入口）
    ├── TopBar.java             #   顶栏（金币/等级经验/轮次/暂停）
    └── UnitDetailDialog.java   #   棋子详情（卸装备入口）
```

**视图铁律**（结构文档三条硬约束的渲染侧细化）：
1. 视图**只读**实体（`UnitView` 持有 `BattleUnit` 引用仅用于读取；自身表现状态——动画态/插值坐标——归视图私有）
2. `entities/ data/ systems/` 反向 import `render/` 一律禁止
3. `UnitView` 等战斗视图生命周期 = `BattleState`；`render/ui/` 的 Actor 生命周期 = `BattleScreen`

---

## 七、图集与素材规范

| 图集 | 内容 | region 命名 |
|------|------|-------------|
| `units.atlas` | 全部棋子精灵 | `{unitId}_{anim}_{frame}`，如 `unit_warrior_01_idle_0`、`..._attack_2` |
| `ui.atlas` | UI 九宫格 / 图标 / 面板底板 | `ui_{name}`，如 `ui_panel_9slice`、`ui_icon_gold` |
| `fx.atlas` | 特效闪光 / 弹道贴图 / 数字字模 | `fx_{name}`，如 `fx_projectile_arrow`、`fx_digit_7` |

- **朝向**：单套贴图 + 绘制时 `flipX`（敌我相对而立，敌方水平翻转）——不画两套
- 动画帧时长（`Animation` 构造，待调）：idle 0.4s/帧、walk 0.2s/帧、attack 0.1s/帧、death 0.15s/帧
- 帧数标准沿用 GDD §9.3（idle 2 / walk 2 / attack 3 / cast 2 / death 3）
- 像素字体：Zpix 或 Fusion Pixel（GDD §9.4）；伤害数字用 `fx_digit_*` 字模贴图

---

## 八、像素完美规则清单（code review 检查项）

1. **全局 `TextureFilter.Nearest`**——禁止线性过滤（atlas 加载后统一设置）
2. **绘制坐标 `Math.round` 吸附到整数虚拟像素**——防半像素抖动
3. **禁用旋转**；例外仅两处：死亡缩放淡出（表现例外）、弹道旋转（§5.3）
4. PC 窗口 1280×720（整数 2 倍）；禁止任意拉伸
5. 调色板 ≤ 32 色（美术侧纪律，GDD §9.1）
6. 渲染段**零对象分配**（池获取除外）——Android 低端机 GC 纪律

---

## 九、HUD 布局定稿（逻辑像素坐标表）

> 定稿图：`docs/diagrams/battle_screen_layout.html`（V1.0）。下表为实现坐标，允许 ±4px 微调。

| # | 区域 | 坐标 (x, y, w, h) | 域 | 阶段可见性 |
|---|------|------------------|-----|-----------|
| ① | 顶栏（金币/等级/轮次/暂停） | (0, 0, 640, 28) | UI | 全程 |
| ② | 备战席 3×3 | (20, 48, 108, 120) | **棋盘域** | 全程（战斗期置灰） |
| ③ | 装备背包 3×2 | (20, 140, 108, 100) | UI | 全程（战斗期置灰） |
| ④ | 共享棋盘 6×7 | (224, 50, 192, 224) | **棋盘域** | 全程（敌区 0~2 行即侦察） |
| ⑤ | 羁绊面板 | (508, 48, 112, 144) | UI | 全程（战斗期显示实际生效档） |
| ⑥ | 开战按钮 | (508, 200, 112, 40) | UI | 仅 SHOPPING |
| ⑦ | 出售区 | (564, 246, 56, 46) | **棋盘域** | 仅 SHOPPING（拖拽终点） |
| ⑧ | 商店栏 | (0, 296, 640, 64) | UI | 仅 SHOPPING |
| — | 战斗 HUD | (0, 296, 640, 64) | UI | 仅 BATTLE（替换 ⑧：变速 ×1/×2、60s 计时条、投降） |

- RESULT 阶段：宝箱三选一弹窗（`dialogStage` 最上层），背景压暗
- 弹窗层级与输入优先级一致（input §2.2 multiplexer：dialogStage > uiStage > boardProcessor > keyProcessor）
- **侦察即棋盘**：共享棋盘设计使敌情侦察无需独立面板——敌区三行直接可见（GDD §4.1 的红利）

---

## 十、性能预算

- 常驻对象：≤ 30 UnitView + 少量弹道 / 特效 / 飘字（池化）——单 batch + 图集分组，640×360 下无 drawcall 压力
- 池化强制：`FloatingText` / `FxLayer`（高频短命）；`UnitView` 按战斗作用域创建销毁（每场 ≤ 30 个，可接受）
- 不引入：render target、shader、粒子系统框架（Phase 7 按需评估）

---

## 十一、待定 / 后续细化

- [ ] 背景层内容（纯色 / 视差 / 场景装饰）——Phase 7
- [ ] 死亡表现二选一：3 帧动画 vs 缩放淡出——Phase 7
- [ ] 特效实现：手绘闪光帧 vs `ParticleEffect`——Phase 7
- [ ] 血条 / 能量条 / 星级底光的最终样式（棋子下方微型条）
- [ ] 商店卡拖拽购买增强（input §2.4 列为待定增强）
- [ ] 宝箱三选一弹窗与棋子详情面板的视觉细化

---

## 十二、决策日志

| 日期 | 决策 | 结论 |
|------|------|------|
| 2026-08-20 | UI 坐标系 | **UI 与世界同用 640×360 虚拟坐标**（双 FitViewport 同参数） |
| 2026-08-20 | 渲染模型 | **双通路**：棋盘域自绘 / UI 域 Stage（与输入归属同构） |
| 2026-08-20 | HUD 布局 | **八区域布局定稿**（坐标表 §九 + 配图 V1.0），GDD 待定项销账 |
| 2026-08-20 | 战斗期商店栏 | **退场换战斗 HUD**（变速/计时/投降） |
| 2026-08-20 | 插值与事件 | 60Hz 逻辑 + UnitView 格间 lerp；一次性表现由 CombatEvent 驱动 |
| 2026-08-20 | 像素规则 | Nearest / 整数吸附 / 禁旋转（死亡与弹道为例外） |
