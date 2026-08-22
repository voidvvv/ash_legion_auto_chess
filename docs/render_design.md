# 🖼️ 渲染架构设计文档

> **版本**：V1.3（新增 §7.6 资源访问服务：注入式 Assets 门面定稿，音频口径补全）  
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
| 9 | 占位资源流水线 | **运行时生成占位图集 + 逐 key 兜底加载**（§7.5）：零素材文件跑通全部界面，真素材按命名约定渐进替换、永不阻塞功能 |
| 10 | 技能特效 | **四锚点 × 双命名空间**（§5.4）：一次性特效归 skillId、持续特效归状态类型；缺资源走通用兜底 |
| 11 | 事件通知面板 | **左下常驻小窗 + L 键大窗回看**（§5.5）：CombatEvent 第三消费者 + 命令执行监听，双流合并的战斗/经营日志 |
| 12 | 资源访问 | **注入式 `Assets` 门面**（§7.6）：region 逐 key 兜底 + 统一字体/皮肤/音效出口；**否决静态持有**（Android 生命周期失同步 / 隐式依赖不可测 / 与 RunContext 词义冲突） |

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
- 音效挂钩：事件 → `Assets.sound(id)` 门面（§7.6；MVP 经 AssetManager 直取，Phase 7 升级 AudioManager 池化限声道，调用方零改动）

### 5.4 技能特效（四锚点 × 双命名空间，2026-08-21 定稿）

特效挂载在技能三步执行（battle_design §六）的各阶段，事件流驱动：

| 执行阶段 | 驱动事件 | 特效 | 锚点 |
|---|---|---|---|
| 起手 | `Cast {skillId, sourceId, targetId}` | 起手光效（叠加于 cast 动画） | 单位 |
| 载体飞行 | `AttackLaunched` | 弹道本体+尾迹 | 弹道 |
| 效果落地 | `Hit / Healed / Shielded` | 落点爆炸/光柱/扩散 | 区域 |
| 状态持续 | **UnitView 轮询差分**（不发事件） | 循环特效+头顶状态图标 | 单位 |

**四锚点的生命周期规则**：

| 锚点 | 跟随 | 生命周期 |
|---|---|---|
| 单位锚点 | UnitView 插值坐标（含格间滑动） | 一次性播完即止；循环型随状态存续，**单位死亡提前结束** |
| 区域锚点 | 固定落点格坐标 | 播完即止（≤0.5s） |
| 弹道锚点 | 弹道连续坐标 | 命中/落空即止 |
| 全屏锚点 | 覆盖层 | 短时（Boss `ALL_ENEMIES` 白闪、震屏 0.2s） |

**双命名空间**（关键分家）：
- `fx_{skillId}_*` —— **一次性特效归技能**（起手 `fx_{skillId}` / 落点 `fx_{skillId}_burst` / 技能弹 `fx_projectile_{skillId}`）
- `fx_status_{type}_*` —— **持续特效归状态类型**：POISON 可能来自三个不同技能，状态的表现归状态，来源技能只决定"怎么得上"
- `Cast` 事件含**主目标 targetId**（MELEE_INSTANT 载体的 AOE 无弹道，区域特效中心从主目标格取）

**规格与纪律**：一次性 4~6 帧（0.06~0.1s/帧，总长 ≤0.5s）、状态循环 2 帧往返；尺寸 单位 32 / 区域 64 / 全屏 640×360；透明度 ≤80%（**特效永不盖过棋子本体**）；特效中性色（≤32 色调色板内），阵营可读性由飘字分色承担。**兜底**：缺 `fx_{skillId}_*` → `fx_cast_default` / `fx_hit_default`；缺 `fx_status_{type}_*` → 6×6 纯色圆点——永不 null 崩溃，美术不阻塞功能。表现层自由项：震屏、暴击飘字 ×1.2 尺寸。

### 5.5 事件通知面板（NotificationPanel，2026-08-21 定稿）

**双数据源**：
- 战斗事件：渲染段 drain `CombatEvent` 后分发——通知面板是**第三消费者**（特效/飘字之外）
- 经营事件：`CommandManager.onExecuted(cmd, success)` 监听（命令执行成功即产出一条；系统行为如怜悯金币、轮首免费刷新由所属 system 直接发）

**双窗形态**：
- **常驻小窗**（HUD 第 9 区，坐标见 §九）：半透明深底，最近 4 行，新行底部推入；**每帧最多追加 2 行**（战斗爆发期防刷屏）；备战/战斗两阶段均可见；点击或 `L` 键展开大窗
- **大窗（回看模式）**：半透明覆盖棋盘右半（可透视，不暂停游戏），最近 200 行 + 滚轮回看，过滤标签页 `全部 / 战斗 / 经营 / 仅关键（技能·死亡·控制）`；`L` 或关闭按钮收起

**分色与文案模板**（与飘字分色同源）：

| 类别 | 颜色 | 示例 |
|---|---|---|
| 购买 | 金 | `兽人战士★1 → 备战席（-1金）` |
| 放置/卖出 | 灰白 | `卖出 丛林游侠（+2金）` |
| 攻击/受伤 | 红白 | `游侠 击中 荆棘之母 -14（暴击!）` |
| 技能 | 紫 | `荆语法师 施放【火球】→ 3 目标` |
| 治疗 | 绿 | `圣光 +38 → 兽人战士★2` |
| 控制/死亡 | 琥珀/深红 | `震地 眩晕 2 单位`、`兽人战士★2 阵亡` |
| 系统 | 蓝 | `第 7 轮 · BOSS（商店免费刷新）`、`怜悯金币 +1（连败3）` |

**性能纪律**：行 Label 池化（4 + 200）、StringBuilder 复用、每帧一次批量提交——渲染段零分配不破。

---

## 六、视图类结构（`render/` 包）

```
render/
├── Assets.java                 # 资源访问门面（§7.6）：region 逐 key 兜底 / font / skin / sound
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
    ├── NotificationPanel.java  #   事件通知小窗 + L 键大窗回看（§5.5）
    └── UnitDetailDialog.java   #   棋子详情（卸装备入口）
```

**视图铁律**（结构文档三条硬约束的渲染侧细化）：
1. 视图**只读**实体（`UnitView` 持有 `BattleUnit` 引用仅用于读取；自身表现状态——动画态/插值坐标——归视图私有）
2. `entities/ data/ systems/` 反向 import `render/` 一律禁止
3. `UnitView` 等战斗视图生命周期 = `BattleState`；`render/ui/` 的 Actor 生命周期 = `BattleScreen`
4. `Assets`（§7.6）只属于表现层：**注入式门面，禁止静态持有**；`entities/ data/ systems/ command/` 永不 import

---

## 七、图集与素材规范

| 图集 | 内容 | region 命名 |
|------|------|-------------|
| `units.atlas` | 全部棋子精灵 | `{unitId}_{anim}_{frame}`，如 `unit_warrior_01_idle_0`、`..._attack_2` |
| `ui.atlas` | UI 九宫格 / 图标 / 面板底板 | `ui_{name}`，如 `ui_panel_9slice`、`ui_icon_gold` |
| `fx.atlas` | 特效闪光 / 弹道贴图 / 数字字模 | 一次性技能特效 `fx_{skillId}`（起手）/ `fx_{skillId}_burst`（落点）；状态循环 `fx_status_{type}`（§5.4 双命名空间）；通用兜底 `fx_cast_default` / `fx_hit_default`；弹道 `fx_projectile_{name}`；数字字模 `fx_digit_{n}` |

- **朝向**：单套贴图 + 绘制时 `flipX`（敌我相对而立，敌方水平翻转）——不画两套
- 动画帧时长（`Animation` 构造，待调）：idle 0.4s/帧、walk 0.2s/帧、attack 0.1s/帧、death 0.15s/帧
- 帧数标准沿用 GDD §9.3（idle 2 / walk 2 / attack 3 / cast 2 / death 3）
- 像素字体：Zpix 或 Fusion Pixel（GDD §9.4）；伤害数字用 `fx_digit_*` 字模贴图

### 7.4 素材获取途径（正式美术的分源策略）

| 层 | 途径 | 许可注意 |
|---|---|---|
| 棋子 / Boss 精灵 | 自绘（Aseprite，32px 入门可行）/ itch.io · OpenGameArt 免费与付费像素包 / 约稿 | 免费素材**只取 CC0 或 CC-BY**（BY 需署名）；**严禁扒商业游戏素材** |
| 技能特效 | 同上（特效包覆盖率高，帧一致性要求低，可 1~2 帧 + 程序缩放凑帧） | 同上 |
| UI 九宫格 / 图标 | Kenney（全 CC0）、itch UI 包 | 风格风险最低的层，可长期使用免费素材 |
| 场景背景 / Boss 演出 | AI 生成做底 + 手工像素化精修 | AI 帧一致性差，**仅适合静态层**，不适合逐帧动画 |
| 英雄立绘 / 关键视觉 | 约稿或自绘（最后做） | 决定卖相的少数资产，值得投入 |

**接入节奏（与 Phase 对齐）**：Phase 1~3 纯占位 → Phase 4 接入 CC0 UI 包 + OFL 像素字体（验证真素材管线）→ Phase 5 用 itch 免费小人替换部分棋子（验证精灵动画流水线）→ Phase 6~7 正式美术按 §七命名约定逐批替换。

### 7.5 占位资源流水线（当前阶段：零素材跑通全部界面）

**原理**：region 命名约定（§7.1）是"换皮不动代码"的钥匙——启动期工厂按正式命名**运行时生成**全部占位资源（Pixmap → Texture，零素材文件），加载链**逐 key 兜底**：真图集缺哪个 key，哪个落占位——**渐进替换、美术永不阻塞功能**。

```java
/** 占位图集工厂：读 data_schema 的 JSON，为每个模板生成符合命名约定的色块资源 */
public class PlaceholderArt {
    // 遍历 units.json  → {unitId}_{anim}_{frame}：
    //    32×32 色块：种族→调色板内 hash 取色（确定性）、深色描边、职业首字母居中；
    //    帧差异使动画可见：idle 亮度 ±5% / walk 上下 1px 抖动 / attack 前移 2px / death 透明度递减
    // 遍历 skills.json → fx_{skillId}（起手闪光）、fx_{skillId}_burst（落点爆圈）
    // 遍历 StatusType  → fx_status_{type}（循环小气泡，命名规范见 §5.4）
    // 通用件：ui_panel_9slice / ui_button / fx_hit_default / fx_cast_default / fx_digit_*
}

/** 逐 key 兜底加载链 */
public TextureRegion region(String key) {
    TextureRegion r = realAtlas != null ? realAtlas.findRegion(key) : null;
    return r != null ? r : placeholder.get(key);   // 真图集缺哪个，哪个落占位
}
```

- **能验证**：布局比例、操作流程、战斗节奏、特效触发时机、移动手感、可读性反馈——除"美"以外的一切
- **不能验证**：调色板观感、32px 辨识度、动画最终手感——只能靠真素材（Phase 6~7）
- 观感下限参照：`docs/diagrams/battle_screen_layout.html` 布局图本身就是色块风格
- **字体不占位**：Fusion Pixel Font 为 **OFL 开源**，第一天即可下载集成（Hiero 生成 BitmapFont）——全部文字从首个可玩版本起即像素风格正确，只有图形需要占位

### 7.6 资源访问服务（Assets 门面，2026-08-21 定稿）

**决策**：**注入式 `Assets` 门面**，不做静态持有（static 字段方案被否决，三条理由：① 与 Android Activity 重建的 GL 上下文失同步——static 引用指向已销毁的 native 句柄；② 隐式依赖 + 时序耦合，view 类不可测、加载顺序错误只能在运行期爆；③ 与 `RunContext` 的 "Context" 词义冲突）。

```java
/** 资源访问门面：真图集缺哪个 key 哪个落占位（§7.5）；统一字体/皮肤/音效出口 */
public final class Assets {
    private final AssetManager manager;         // LoadingScreen 全量加载（已定）
    private final PlaceholderArt placeholder;   // 运行时占位工厂（§7.5）

    public TextureRegion region(String key) {   // units/ui/fx 三图集依次查 → 占位兜底，永不 null
        ...
    }
    public BitmapFont font() { ... }            // Fusion Pixel（§7.5）
    public Skin skin() { ... }                  // UI 域
    public Sound sound(String id) {             // 音频统一出口
        return manager.get("audio/sfx/" + id + ".wav", Sound.class);
    }
}
```

**装配链**（"screens 是唯一装配点"规则的直接应用，对象图仅 2~3 层、传参成本可忽略）：

```
Main.create → LoadingScreen（全量加载）→ new Assets(manager, placeholder)
    → 注入 BattleScreen 构造器 → 注入 BattleRenderer / uiStage Actor / UnitView …
```

**音频口径**：`Assets.sound(id)` 为唯一出口——MVP 经 AssetManager 直取；Phase 7 升级 `AudioManager`（池化、限声道、按 skillId 细分）时**只改门面内部，调用方零改动**。

**纪律**：`Assets` 只允许出现在 `render/` 与 `screens/`（结构文档约束 1 自动涵盖）；`region(key)` 的 key 一律来自命名约定（§7.1），禁字面量魔法。若未来注入传参成为真实痛点，允许降级为"单例实例 + `get()/clear()` 成对"的 service locator 形态——实例语义不变，仅加访问器。

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
| ③ | 装备背包 3×2 | (20, 172, 108, 72) | UI | 全程（战斗期置灰）；原表值 (20,140,108,100) 与 ② 压叠 28px，feedback01 修正 |
| ④ | 共享棋盘 6×7 | (224, 50, 192, 224) | **棋盘域** | 全程（敌区 0~2 行即侦察） |
| ⑤ | 羁绊面板 | (508, 48, 112, 144) | UI | 全程（战斗期显示实际生效档） |
| ⑥ | 开战按钮 | (508, 200, 112, 40) | UI | 仅 SHOPPING |
| ⑦ | 出售区 | (564, 246, 56, 46) | **棋盘域** | 仅 SHOPPING（拖拽终点） |
| ⑧ | 商店栏 | (0, 296, 640, 64) | UI | 仅 SHOPPING |
| — | 战斗 HUD | (0, 296, 640, 64) | UI | 仅 BATTLE（替换 ⑧：变速 ×1/×2、60s 计时条、投降） |
| ⑨ | 事件通知小窗 | (20, 230, 128, 60) | UI | 全程（点击 / `L` 键展开大窗回看，见 §5.5） |

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
| 2026-08-21 | 占位资源 | **运行时占位图集 + 逐 key 兜底加载**（§7.5）——零素材跑通全部界面，真素材渐进替换；正式素材分源策略与接入节奏（§7.4） |
| 2026-08-21 | 技能特效 | **四锚点 × 双命名空间**（§5.4）：一次性归 `fx_{skillId}`、持续归 `fx_status_{type}`（轮询差分驱动）；通用兜底永不阻塞美术 |
| 2026-08-21 | 通知面板 | **左下常驻小窗 + L 键大窗回看**（§5.5）：CombatEvent 第三消费者 + `CommandManager.onExecuted` 经营监听，双流合并；HUD 增第 9 区 |
| 2026-08-21 | 资源访问 | **注入式 `Assets` 门面**定稿（§7.6）：否决静态 GameContext 方案（Android 生命周期失同步 / 隐式依赖不可测 / Context 词义冲突）；音频统一 `Assets.sound()` 出口（Phase 7 平滑升级 AudioManager）；分层铁律入册 |
