# feedback06（战斗事件行主体）+ feedback07（装备效果查看）技术实施文档

> 版本：V1.0（2026-08-22）　分支：feature/new_idea　基线：f78f552（607 测试全绿；feedback01~05 已入库）
> 输入：用户手验反馈两条（feedback06 / feedback07）+ 两组用户裁决（裁决已随任务提供 → 未走澄清门禁；本次核对未发现 BLOCKER 级矛盾，见 §3.4/§8）
> 范围声明：两个 UI 信息缺失 bug 修复，不改玩法、不改数据结构、不改通知三流架构。executor 分两个 commit 交付（§7 交付切分）。

---

## 1. 背景与目标

### 1.1 Issue A（feedback06）：通知面板战斗事件行无主体

手验现象：小窗战斗事件行「技能施放：火球术」不知道是谁放的、「单位倒下」不知道谁倒下。

根因（本次实读核实，与任务描述一致）：

1. `NotificationPanel.formatEvent`（`render/ui/NotificationPanel.java:93-102`）格式化 `CAST`/`UNIT_DIED` 时丢弃了 `sourceId`——`UNIT_DIED` 返回写死的 `"单位倒下"`，`CAST` 只拼技能名。
2. 结构性根因：`formatEvent(CombatEvent, GameData)` 只拿到技能表，拿不到 `BattleState`。`NotificationPanel.syncBattle(BattleState)`（同文件 `:48-54`）只把 `state.getEvents()` 视图缓存进 `EventInbox`（`render/EventInbox.java:22-30`），`BattleState` 引用未保留，id → 单位名无从解析。

数据可用性（核实结论：链路完整，纯展示层缺口）：

- `CombatEvent` 携带 `sourceId`（`entities/CombatEvent.java:95`；`UNIT_DIED` 的 sourceId = 亡者 id `:78-81`，`CAST` 的 sourceId = 施放者 `:56-59`）。
- `BattleState.units` 列表终身持有含已清扫单位（`entities/BattleState.java:45-46` 注释「含已清扫——列表终身持有」），`getUnitById` 死后仍可查（`:51-58`）。
- `BattleUnit.getTemplate().getName()` 有中文名（`entities/BattleUnit.java:63`；`UnitData.getName()` `data/UnitData.java:43`，units.json 全中文）。
- `Side.ENEMY` 可判敌方（`entities/Side.java:4-7`）。
- **id 空间核实**：玩家侧 `BattleUnit` 的 id 是开战时 `IdIssuer.nextId()` 新发的（`systems/BattleSystem.java:88`），与玩家名单 `Unit` id 不同源——主体解析必须走 `BattleState.getUnitById`，**不得**走 `Player.getUnitById`。

### 1.2 Issue B（feedback07）：装备效果无任何 UI 查看入口

手验现象：数据层完备但全 UI 没有任何位置能看到装备效果，玩家无法做「穿给谁 / 选哪件」的决策。

根因（本次实读核实，与任务描述一致）：

- 数据层完备：`data/EquipmentData.java`（`effects: List<EquipmentEffect{stat,op,value}>` `:14,:32` + `passive: EquipmentPassive{type,power,tickInterval}` 可 null `:15,:34`）；`UnitInfoText.effectText`（`render/ui/UnitInfoText.java:50-63`）已有同词汇表 `{stat,op,value}` 的文案生成器，但入参类型是 `EffectData`（羁绊/技能用），与 `EquipmentEffect` 不同类，不能直接复用。
- 展示层只显示名字：背包格只画名字前 3 字 + 槽位字（`render/ui/InventoryPanel.java:116-119`，无任何 hover 监听——仅 ClickListener `:84-98`）；详情弹窗卸下按钮只写「名字  [卸下]」（`render/ui/UnitDetailDialog.java:199-206`）；宝箱选项只写模板名（`render/ui/ChestDialog.java:49-59`）；悬停卡设计口径 R1 明确不含已穿装备（`UnitInfoText.java:93` 注释）。
- 被动语义核实（文案定稿依据）：`passiveStatus` 本期仅支持 REGEN（`config/JsonLoader.java:477`）；REGEN 的 power = maxHp 比例/跳、tickInterval = 秒/跳（`data/EquipmentPassive.java:18,:21`；心跳落地 `systems/StatusSystem.java:100-103`，开局挂载 `systems/BattleSystem.java:255-267`）。龙心 `{"power": 0.02, "tick": 5}`（`assets/data/equipments.json:18`）→ 语义 = 每 5 秒回复 2% 最大生命，与 GDD §5.2 表原文「每 5 秒回复 2% 最大生命」一致（`docs/gdd_idea_0.0.0.1.md:195`）。

### 1.3 成功标准

1. 通知小窗/大窗：CAST 行形如「荆语法师 施放 火球术」、UNIT_DIED 行形如「兽人战士 倒下」；敌方主体带「（敌方）」标记（feedback04-2 悬停卡同款文案惯例，`render/ui/HoverPreviewCard.java:41`）；HIT/HEALED 过噪跳过维持不变（口径 #13，不扩事件类型范围）。
2. 装备效果三个展示点全部可读：详情弹窗装备行、背包格悬停卡、宝箱装备选项行；三处共用同一套纯函数文案，禁止三处各写一份格式化。
3. 607 基线全绿零回归 + 新增测试全绿（gradle XML 聚合计数核对，沿用 MEMORY 口径）。

### 明确不做

| 项 | 出处 |
|----|------|
| 事件行扩类型（HIT/HEALED/SHIELDED 等入面板） | 用户裁决：口径 #13 维持不变 |
| 事件行加星级（「兽人战士★2」）、目标数（「→ 3 目标」）、改「阵亡」措辞 | render §5.5 模板比本次裁决范围更丰富——本次仅加主体名 + 敌方标记（§8 WARNING-1） |
| 通知面板折行（多行占额） | 单帧 2 行上限口径（render §5.5 / WARNING-6）——本次定截断口径（§5.3-A2-3） |
| 装备合成/新装备数据/改 equipments.json | 纯展示层任务，数据零改动 |
| UnitInfoText.effectText 改成泛型双类型共用 | `EffectData` 与 `EquipmentEffect` 结构不同类；新文件并行更符合小文件惯例（§5.2-B1） |
| R1 棋子悬停卡加已穿装备 | R1 是模板级棋子卡口径（§5.4 差异声明 #D1），不在本次范围 |

---

## 2. 术语与约定

| GDD/设计文档用语 | 代码标识符 | UI 文案（定稿） | 备注 |
|------|------|------|------|
| 事件主体（施放者/亡者） | `CombatEvent.sourceId` → `BattleState.getUnitById` | 模板中文名 | id 为战斗发号空间，非名单 id |
| 敌方标记 | `Side.ENEMY` / `HoverCandidate.isEnemy()` | （敌方） | feedback04-2 同款（HoverPreviewCard.java:41） |
| 通知行截断 | `UnitInfoText.truncateColumns`（新增） | 末尾 … | §5.3-A2-3 |
| 装备效果条目 | `EquipmentEffect{stat,op,value}` | 攻击+20% / 生命+400 / 吸血+10% | 词汇/百分比口径复用 UnitInfoText（§2.1 同源） |
| 装备被动 | `EquipmentPassive{REGEN,power,tickInterval}` | 被动：每 5 秒 回复 2% 最大生命 | GDD §5.2 龙心行原文 +「被动：」前缀 |
| 装备稀有度（白/成/传） | `EquipmentRarity` WHITE/RARE/LEGENDARY | 白装 / 成装 / 传说 | GDD §5.2、data_schema §三 |
| 装备槽位（武器/盔甲/饰品） | `EquipmentSlot` WEAPON/ARMOR/TRINKET | 武器 / 盔甲 / 饰品 | GDD §5.2 表列原文（背包格短形沿用武/甲/饰） |
| 背包格悬停卡 | `HoverPreviewCard.inventoryHover` + `BoardGeometry.INVENTORY_HOVER_*` | — | 250ms 驻留复用 HoverStateMachine |

---

## 3. 现状盘点（file:line 均为本次实读核对，基线 f78f552）

### 3.1 可直接复用（零改动）

| 资产 | 位置 | 说明 |
|------|------|------|
| 事件三流消费骨架 | `NotificationPanel.refresh`（:62-84）、`EventInbox`（全文件）、`NotificationLog.appendCapped`（单帧 2 行） | 只改 formatEvent 一点，骨架零改动 |
| BattleState 只读查询 | `BattleState.getUnitById`（:51-58）、`units` 终身持有（:45-46） | UI 层只读，不触 framework-internal 写方法（口径 #22） |
| 敌方标记文案惯例 | `HoverPreviewCard.ENEMY_MARKER_LINE = "（敌方）"`（:41） | 通知行直接复用同字面 |
| 折行/截断/列宽纯函数 | `UnitInfoText.wrap / clipLines / columns`（UnitInfoText.java:184-266） | 装备三展示点折行全靠它们，仅新增 truncateColumns |
| statLabel/numberText 词表 | `UnitInfoText.statLabel`（:34-47，public）、`numberText`（:160，包级） | EquipmentInfoText 同包直用 |
| 商店悬停源先例 | `ShopBar.ShopCard` InputListener enter/exit + `getHoveredSlot()`（ShopBar.java:44-45,:73-75,:125-137） | 背包格悬停源逐字沿此模式 |
| 悬停状态机 | `HoverStateMachine`（250ms 驻留，全文件） | 背包第三源直接 new 一实例 |
| 悬停卡组件与抑制位 | `HoverPreviewCard.refresh/recompute`（:69-98）、BattleScreen frozen 位（BattleScreen.java:245,:268-269） | 加第三源分支 |
| 卸下按钮重建指纹 | `UnitDetailDialog.refresh` 指纹判定（:97-104）+ `sameEquippedIds`（:132-142） | 效果列为纯绘制追加，指纹输入零变化 |
| 拖拽查询 | `BoardInputProcessor.isDragging()`（:252，public） | 背包悬停拖拽抑制用 |
| 测试基建 | `BattleTestFixtures`（systems/support，public：`tpl/unit/state` 夹具）、`UnitDetailDialogTest`（null assets headless 先例 :147-148）、`ChestDialogTest.optionText/optionTint` 直测先例 | 各 CP 测试沿用 |

### 3.2 需改造

| 文件 | 位置 | 改动 | CP |
|------|------|------|----|
| `render/ui/UnitInfoText.java` | :254-256（charColumns 后、clipLines 前） | 新增 `truncateColumns` 纯函数 | CP-A1 |
| `render/ui/NotificationPanel.java` | :29-34（字段段）、:48-54（syncBattle）、:72（refresh 调用点）、:92-108（formatEvent/skillName）、import 段 | 保留 BattleState 引用 + formatEvent 新签名（主体名/敌方标记/截断） | CP-A2 |
| `render/ui/UnitDetailDialog.java` | :38-41（常量段）、:123-142（指纹纯函数区）、:184-207（UnequipButton） | 卸下按钮右侧效果列（构造期预计算） | CP-B2 |
| `render/ui/InventoryPanel.java` | :36-48（字段/构造段）、:75-99（InventorySlot 构造）、import 段 | 悬停槽位源（enter/exit + getHoveredSlot） | CP-B3 |
| `render/ui/HoverPreviewCard.java` | :31-48（常量/字段段）、:21-28（类注释）、:60-98（refresh/recompute）、:100-110（静态区后）、import 段 | 背包第三源（状态机 + 归一 + 行集） | CP-B4 |
| `render/board/BoardGeometry.java` | :61-65（SHOP_HOVER 块后） | `INVENTORY_HOVER_*` 锚点常量 ×4 | CP-B4 |
| `screens/BattleScreen.java` | :268-269（render 悬停卡调用点） | refresh 传背包槽位（含拖拽抑制） | CP-B4 |
| `render/ui/ChestDialog.java` | :25-27（常量段）、:59-68（optionTint 后）、:85-98（OptionButton.draw）、import 段 | 装备选项效果行 | CP-B5 |
| 测试改写/增例 | `NotificationFormatTest`（:78-111 战斗事件行区块）、`UnitInfoTextTest`、`UnitDetailDialogTest`、`HoverPreviewCardTest`、`ChestDialogTest` | 随对应 CP（TDD 先行） | 各 CP |

### 3.3 需新建

| 文件 | CP |
|------|----|
| `core/src/main/java/com/voidvvv/kz_auto_chess_n/render/ui/EquipmentInfoText.java`（装备文案纯函数，三展示点共用） | CP-B1 |
| `core/src/test/java/com/voidvvv/kz_auto_chess_n/render/ui/EquipmentInfoTextTest.java` | CP-B1 |
| 图表：`docs/diagrams/feedback06_event_subject_flow.md/.html`、`docs/diagrams/feedback07_equipment_effect_view.md/.html` | 已随本文档落盘 |

### 3.4 事实核对结论（与任务描述的差异修正，以代码为准）

| # | 任务描述 | 实测 | 处置 |
|---|------|------|------|
| 1 | ChestDialog optionText 结构 `:48-61`、传说着色 `:61` | `optionText` 实际 `:49-59`、`optionTint` `:62-68`（传说分支 `:63-66`） | 文档按实测行号；改动内容不受影响 |
| 2 | UnitDetailDialog 按钮指纹机制 `:86-104` | 指纹 javadoc `:84-89`、判定代码 `:97-104`、重建 `:107-120` | 同上 |
| 3 | BattleUnit.java:27,63（getTemplate + 名字） | `getTemplate()` 在 `:63`；`:27` 为类声明行 | 同上 |
| 4 | 「大窗 = 最近 200 行，无过滤——WARNING-6」 | 与 NotificationLog/NotificationPanel.draw 一致（NotificationLog.java:12,:34-36） | 无矛盾 |
| 5 | render §5.5 文案模板含主体名（「荆语法师 施放【火球】」「兽人战士★2 阵亡」） | 现实现（Phase 5 CP15）无主体、措辞「倒下」 | 本次向设计靠拢（加主体），措辞/星级差异记 WARNING-1，不构成阻塞 |
| 6 | data_schema §三 rarity 词表为 `WHITE/FINISHED/LEGENDARY` | 代码为 `WHITE/RARE/LEGENDARY`（EquipmentRarity.java:5，Phase 5 已定） | 既有差异非本次引入；UI 词「成装」按 GDD 白/成/传，记 WARNING-2 |
| 7 | 任务提示「悬停卡 R1 口径不含装备是否与本次冲突」 | R1 口径落在 `UnitInfoText.previewLines`（棋子模板卡，UnitInfoText.java:93）；背包格装备卡展示的是装备本体，另一展示点 | 不冲突，差异声明 #D1（§5.4） |

---

## 4. 已确认决策（用户裁决，2026-08-22，最终决定，不得改回）

| # | 议题 | 裁决 | 本文档落地 |
|---|------|------|------|
| Q1 | feedback06：哪些事件行加主体、敌方如何标记 | **CAST 与 UNIT_DIED 两类都加主体名；敌方单位带「（敌方）」标记，与悬停卡 feedback04-2 惯例一致** | CP-A2（formatEvent 新签名 + subjectName 纯函数；标记字面同 HoverPreviewCard.java:41） |
| Q2 | feedback07：哪些展示点接入装备效果 | **三点全部接入：详情弹窗装备行、背包格悬停卡、宝箱选项行**；三处共用同一套纯函数文案，禁止三处各写一份格式化 | CP-B1（EquipmentInfoText 单一文案源）→ CP-B2/B3+B4/B5（三展示点） |

其余实现细节（BattleState 生命周期、formatEvent 签名、回退文案、行宽策略、文案文件归属、弹窗版式、悬停实现路径、BATTLE 期抑制、宝箱行版式）为用户明示授权本文档拟定项，见 §5.3 实现口径。

---

## 5. 总体技术方案

### 5.1 Issue A：事件行主体解析（数据流图：`docs/diagrams/feedback06_event_subject_flow.md` / `.html`）

- **生命周期**：`NotificationPanel` 新增字段 `battleState`，在 `syncBattle(BattleState)` 内与 `EventInbox` 同步双写——attach（非 null）时赋值、detach（null）时置 null。战毕 `BattleScreen.render` 每帧以 `runContext.getBattleState()` 观察调用（BattleScreen.java:267，调用点零改动），跨局旧 state 随置 null 可 GC。
- **纯函数化**：`formatEvent(CombatEvent, GameData, BattleState)` 包级静态纯函数保持 headless 可测；主体解析抽出 `subjectName(BattleState, int)` 静态纯函数（查表 → 模板名，ENEMY 附「（敌方）」，查不到/无 state 回退 `"#id"`）。测试用 `BattleTestFixtures.state/unit/tpl`（systems/support 公开夹具）构造微型战斗。
- **行宽策略（本次定口径）**：现状为硬画不折行（NotificationPanel.draw :126 直接 `font().draw`，无 clip 无 wrap），既有 notices 行已存在溢出面板底宽的先例（如 EquipmentSystem 的「兽人战士 穿戴 秘银胸甲」≈ 11 列 = 132px > 面板内宽 122px）。**选截断不折行**：折行会让单条事件占多行配额，与单帧 2 行上限（§5.5/WARNING-6）的计数语义冲突。截断上限 16 列（192px）：起点 NOTIFY_X+6=26，右缘 26+192=218 < 棋盘左缘 224（y 244~290 段棋盘外为空背景），覆盖全部现实文案（4 字名 + （敌方）+「 施放 」+ 5 字技能名 ≈ 15.5 列），截断只作极端防御。截断发生在 formatEvent（入队前），小窗大窗同文案（不存双份，YAGNI）。

### 5.2 Issue B：装备效果三展示点（数据流/锚点图：`docs/diagrams/feedback07_equipment_effect_view.md` / `.html`）

- **单一文案源**：新建 `render/ui/EquipmentInfoText`（纯函数、零 Gdx、headless 可测）。不并入 UnitInfoText：后者 267 行已近 400 行惯例上限，且「棋子文案」与「装备文案」是两个域——按项目「小文件、纯函数」惯例独立成文件。词表同源：`statLabel`/`numberText` 直接复用（同包），effectText 的 ADD/PCT/百分比刻度分支与 `UnitInfoText.effectText`（:50-63）逐字同构（入参类不同）。
- **文案格式**：效果条目 `标签+值`（PCT 与百分比刻度键附 %），如「攻击+20%」「生命+400」「吸血+10%」；被动「被动：每 5 秒 回复 2% 最大生命」（GDD §5.2 龙心行原文 + 前缀；空格为 wrap 断点服务）；悬停卡行集 = 名 → 「稀有度·槽位」（如「传说·盔甲」）→ 各效果条目 → 被动。
- **展示点 ①（详情弹窗，CP-B2）**：`UnequipButton` 右侧追加效果列——按钮本体（200×24、位置、指纹）零改动，效果列画在 `getX()+getWidth()+8f`（=298）起的右侧空白（BG 右缘 450 内，两行 12 列），构造期预计算（重建时才分配，draw 零分配）。不改 EQUIP_Y0/EQUIP_STEP/按钮尺寸——「加宽/两行/缩写」三案比较后右侧追加为最小侵入：不动 42d9104 布局修复的任何常量、不碰 feedback04 指纹机制输入、无高度挤压（三槽 26px 步进若改两行高需 34px+，EQUIP_Y0=100 下第三槽将越出 BG 底边 44——已推演否决）。
- **展示点 ②（背包格悬停卡，CP-B3/B4）**：最小侵入路径 = 源侧沿 ShopBar 先例（InventorySlot 加 InputListener enter/exit → `hoveredSlot` + getter），卡侧复用 HoverPreviewCard 加第三源（第三台 HoverStateMachine、250ms 驻留、同一 frozen 抑制位、锚点新常量）。不选「InventoryPanel 自画 tooltip」：绘制次序在 uiStage 中低于通知面板/悬停卡，且要另写一套卡壳绘制；复用 HoverPreviewCard 三处（卡壳/驻留/抑制）全免。
- **展示点 ③（宝箱选项行，CP-B5）**：`optionText`（名）/`optionTint`（传说金棕）不变；新增纯函数 `optionEffectLines`，OptionButton.draw 对装备选项改为「名（+46 行）+ 效果行×≤3（+32/+20/+8）」两段式，金币/经验选项维持原版式（「选择」字样让位于效果行，整钮可点击不变——口径 #B5-2）。

### 5.3 实现口径（文档未明说、本次定的执行细节——评审重点）

**A 组（feedback06）**

| # | 口径 | 依据 |
|---|------|------|
| A2-1 | 事件行文案：CAST = `主体名 + " 施放 " + 技能中文名`；UNIT_DIED = `主体名 + " 倒下"`；主体名 = 模板中文名 +（敌方则附「（敌方）」）。措辞沿用已交付「倒下」（render §5.5 模板的「阵亡」差异见 WARNING-1） | 用户裁决 1 的最小变更落地 |
| A2-2 | id 查不到单位（battleState 为 null / id 未登记）→ 主体名回退 `"#" + sourceId`（如「#5 倒下」）。正常路径不可达：units 终身持有 + 事件必来自附着战斗——纯防御（与 skillName 查表失败回退 id 同款思想，NotificationPanel.java:104-108） | 用户指定回退形态 |
| A2-3 | 通知行截断：`truncateColumns(line, 16)`，超宽保留 15 列内容 + 「…」（… 计 1 列）；≤0 或未超宽原样返回（沿 wrap 的 ≤0 不处理惯例）。仅施加于 formatEvent 产物，不改 notices/命令行现状 | §5.1 行宽策略 |
| A2-4 | `currentBattle()` 包级实例 getter 暴露附着态供 headless 生命周期测试（构造传 null assets——refresh 零 GL，沿 UnitDetailDialogTest 先例） | TDD |
| A2-5 | 大窗（L 键）与小窗显示同一（截断后）行——截断在入队前，日志只存一份 | WARNING-4 |

**B 组（feedback07）**

| # | 口径 | 依据 |
|------|------|------|
| B1-1 | 稀有度词：白装/成装/传说（GDD §5.2「白装合成成装」「传说」；不用 data_schema 的 FINISHED 枚举名，见 WARNING-2）；槽位词：武器/盔甲/饰品（GDD §5.2 表列原文；背包格内短形武/甲/饰为既有现状不动） | GDD 词汇唯一词源 |
| B1-2 | 被动文案模板：`被动：每 {numberText(tickInterval)} 秒 回复 {round(power*100)}% 最大生命`；非 REGEN 类型回退 `被动：{type.jsonName()}`（加载期已限制 REGEN，纯防御不炸） | StatusSystem.java:100-103 落地语义 + GDD §5.2 原文 |
| B2-1 | 详情弹窗效果列：`effectSummary`（条目 " · " 连接单串）折 12 列 × 截 2 行；空条目（无效果无被动）= 空列表不绘制。右侧区 x 298~442（≤ BG 右缘 450-8），两行基线 y+16/y+4 | §5.2 版式推演 |
| B3-1 | 背包悬停源原始暴露 `getHoveredSlot()`（-1 = 无；空槽/越界归一在卡侧），逐字沿 ShopBar.java:125-137 模式 | 双源同构 |
| B4-1 | 背包悬停卡归一 `normalizeInventorySlot(phase, slot, size)`（纯函数三参）：**BATTLE 期 → -1**；slot<0 或 ≥背包数（空槽）→ -1。抑制理由：BATTLE 期背包 alpha 0.35 置灰 = 非交互只读快照（差异声明 #8），悬停提示对无可执行操作的置灰面板是噪声；宝箱决策期的装备效果由展示点 ③（宝箱选项行）承载，职责不缺 | 用户授权本次裁定 |
| B4-2 | 拖拽中背包源抑制：BattleScreen 传 `-1`（`boardProcessor.isDragging()` 时）。拖拽走 boardProcessor，uiStage 的 enter/exit 仍会触发（Stage 无按键也派发 pointerover），查询侧归一为拖拽期唯一入口（与棋盘源 isDragging 抑制同口径） | BoardInputProcessor.getHoverCandidate:193 同款 |
| B4-3 | 背包卡锚点 `(132, 140, 90, 100)`：折 6 列（(90-8)/12=6.83）、容量 7 行（(100-8)/12=7.67）。位于棋盘域卡同一空带（x 128~222）但指针命中域互斥（背包 y 172~244 x≤128 vs 棋盘/备战候选均在该区外）；底边 240 避开 ⑨ 通知（y 244 起）、顶边 140 避开 ⑥ 开战按钮（y 88~128，BoardGeometry.java:56 注释） | 沿 5.1 R1 锚点论证法 |
| B4-4 | 三源优先级 棋盘 > 商店 > 背包（单指针物理互斥，优先级为防御性定义，沿 5.1 双源先例） | §5.1 |
| B5-1 | 宝箱装备选项效果行：`effectEntries` 逐条折 8 列（(120-14-2)/12=8.67）× 截 3 行；金币/经验选项 = 空列表走原版式 | §5.2 |
| B5-2 | 装备选项不画「选择」字样（效果行让位；整钮 ClickListener 不变）。手验如认为点击提示缺失可回补到 y+4（低风险微调） | WARNING-7 |

### 5.4 与设计文档的差异声明（如实记录，不改设计文档）

| # | 差异 | 说明 |
|---|------|------|
| D1 | R1「悬停卡不含已穿装备」（UnitInfoText.java:93，棋子模板级卡口径）vs 本次新增背包格装备卡 | 不冲突：R1 限制的是**棋子**悬停卡的行集（previewLines，模板级）；背包格卡展示的是**装备本体**（另一展示点、另一行集函数）。棋子卡零改动 |
| D2 | render §5.5 小窗行文案模板（主体+技能+星级+目标数）与本次落地（主体+技能，无星级/目标数） | 本次按用户裁决 1 范围（主体名+敌方标记）最小落地；完整模板对齐留后续（WARNING-1） |
| D3 | BoardGeometry 新增背包悬停锚点（render §九 HUD 表无此区） | 与 5.1 R1 悬停锚点（§九表外补充常量）同款先例，Phase 6 回写候选 |
| D4 | 通知行 16 列截断可溢出小窗底宽 128px（至多到 x 218 空背景带） | 沿既有 notices 行溢出先例（§5.1）；不遮挡任何面板（右缘 < 224 棋盘左缘、L 大窗另算） |

---

## 6. 改动点清单（评审主入口）

> 编号规则：A 组 = feedback06（CP-A1~A2），B 组 = feedback07（CP-B1~B5）。按依赖序排列。

### CP-A1. UnitInfoText 新增 truncateColumns 截断纯函数

- **类型**：修改类（新增静态方法）
- **位置**：`core/src/main/java/com/voidvvv/kz_auto_chess_n/render/ui/UnitInfoText.java:254-258`（`charColumns` 之后、`clipLines` 之前插入；定位锚点：`charColumns` 方法体结束后）
- **改动说明**：通知行加主体名后文案变长（§5.1），需按列宽截断。折行会占多行配额、破坏单帧 2 行上限口径（render §5.5），故提供「保 maxColumns-1 列内容 + … 收尾」的截断原语，与既有 `wrap`/`clipLines`/`columns` 同居一处、同列宽口径（全角 1 列/半角 0.5 列）。术语对照：GDD 无对应概念，纯渲染层原语。
- **代码**（新增方法；上下文「修改前」逐字摘自 UnitInfoText.java:254-258）：

```java
修改前：
    private static float charColumns(char c) {
        return c > 0xFF ? 1f : 0.5f;
    }

    /** 卡高截断（§5.3-4）：超容量行丢弃、末行以 … 示意；capacity ≤ 0 或未超 = 原样返回 */
```

```java
修改后：
    private static float charColumns(char c) {
        return c > 0xFF ? 1f : 0.5f;
    }

    /** 按列宽截断（feedback06 通知行防溢出）：超宽时保留 maxColumns-1 列内容并以 … 收尾（… 计 1 列）；
     *  maxColumns ≤ 0、null 或未超宽 = 原样返回（沿 wrap 的 ≤0 不处理惯例） */
    public static String truncateColumns(String text, int maxColumns) {
        if (text == null || maxColumns <= 0 || columns(text) <= maxColumns) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        float used = 0f;
        for (int i = 0; i < text.length(); i++) {
            float w = charColumns(text.charAt(i));
            if (used + w > maxColumns - 1f) {
                break;
            }
            sb.append(text.charAt(i));
            used += w;
        }
        sb.append('…');
        return sb.toString();
    }

    /** 卡高截断（§5.3-4）：超容量行丢弃、末行以 … 示意；capacity ≤ 0 或未超 = 原样返回 */
```

- **测试要点**：`UnitInfoTextTest` 新增用例（TDD 先行）：(1) 未超宽（含恰好 16 列）原样返回；(2) 17 个全角字符截为 15 全角 + 「…」（精确串断言）；(3) 34 个半角字符截为 30 半角 + 「…」（15 列）；(4) `maxColumns ≤ 0` 与 `null` 原样返回。

### CP-A2. NotificationPanel 事件行加主体（BattleState 生命周期 + formatEvent 新签名）

- **类型**：修改类（新增字段 + 修改方法×2 + 新增静态方法×2 + 常量 + import + 测试改写）
- **位置**：`core/src/main/java/com/voidvvv/kz_auto_chess_n/render/ui/NotificationPanel.java`：import 段（:3-21）、字段段（:29-34）、`syncBattle`（:47-54）、`refresh` 消费者（:69-77）、`formatEvent`/`skillName`（:92-108）
- **改动说明**：feedback06 主体修复。① 新增字段 `battleState`，`syncBattle` 内与 `EventInbox` 同步双写（attach 赋值 / detach 置 null，§5.1 生命周期）；② `formatEvent` 签名扩为 `(CombatEvent, GameData, BattleState)`，CAST/UNIT_DIED 行加主体名（敌方附「（敌方）」，字面同 HoverPreviewCard.java:41 惯例），并施加 16 列截断（口径 A2-3）；③ 主体解析抽 `subjectName` 静态纯函数，查不到回退 `#id`（口径 A2-2）；④ HIT/HEALED 过噪跳过维持不变（口径 #13）；⑤ `currentBattle()` 包级 getter 供 headless 生命周期测试。术语对照：事件主体（GDD §5.5 文案模板的「荆语法师」「兽人战士★2」）↔ `subjectName(BattleState, int)`。
- **代码**：

（import 段，修改前逐字摘自 NotificationPanel.java:11-12）：

```java
修改前：
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
```

```java
修改后：
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.entities.Side;
```

（字段段，修改前逐字摘自 NotificationPanel.java:31-34）：

```java
修改前：
    private final Assets assets;
    private final Supplier<RunContext> context;
    private final NotificationLog log = new NotificationLog();
    private final EventInbox inbox = new EventInbox();
```

```java
修改后：
    private final Assets assets;
    private final Supplier<RunContext> context;
    private final NotificationLog log = new NotificationLog();
    private final EventInbox inbox = new EventInbox();
    /** 当前附着战斗（feedback06：事件主体 id → 单位名解析源；与 inbox 生命周期对齐——attach 赋值 / detach 置 null） */
    private BattleState battleState;
```

（syncBattle，修改前逐字摘自 NotificationPanel.java:47-54）：

```java
修改前：
    /** 战斗作用域同步（Screen 观察调用；attach 于战斗创建帧 = 0 事件，无历史回灌） */
    public void syncBattle(BattleState state) {
        if (state == null) {
            inbox.detach();
        } else {
            inbox.attach(state);
        }
    }
```

```java
修改后：
    /** 战斗作用域同步（Screen 观察调用；attach 于战斗创建帧 = 0 事件，无历史回灌）。
     *  同时保留 BattleState 引用供 formatEvent 解析事件主体（feedback06）；detach 置 null 可 GC */
    public void syncBattle(BattleState state) {
        this.battleState = state;
        if (state == null) {
            inbox.detach();
        } else {
            inbox.attach(state);
        }
    }

    /** 当前附着战斗（未附着 = null；包级实例 getter 供 headless 生命周期测试观察——沿 ShopBar.getHoveredSlot 暴露形态） */
    BattleState currentBattle() {
        return battleState;
    }
```

（refresh 消费者，修改前逐字摘自 NotificationPanel.java:69-77）：

```java
修改前：
        inbox.forEachNew(new Consumer<CombatEvent>() {
            @Override
            public void accept(CombatEvent event) {
                String line = formatEvent(event, ctx.getGameData());
                if (line != null && log.appendCapped(line, appended[0])) {
                    appended[0]++; // 超限丢弃（§5.5 防刷屏，WARNING-6）
                }
            }
        });
```

```java
修改后：
        inbox.forEachNew(new Consumer<CombatEvent>() {
            @Override
            public void accept(CombatEvent event) {
                String line = formatEvent(event, ctx.getGameData(), battleState);
                if (line != null && log.appendCapped(line, appended[0])) {
                    appended[0]++; // 超限丢弃（§5.5 防刷屏，WARNING-6）
                }
            }
        });
```

（formatEvent 区，修改前逐字摘自 NotificationPanel.java:92-108）：

```java
修改前：
    /** 战斗事件行（仅 UNIT_DIED/CAST——HIT/HEALED 过噪跳过，口径 #13；技能行显中文名，查表失败回退 id） */
    static String formatEvent(CombatEvent event, GameData data) {
        switch (event.getType()) {
            case UNIT_DIED:
                return "单位倒下";
            case CAST:
                return "技能施放：" + skillName(data, event.getSkillId());
            default:
                return null;
        }
    }

    /** 技能中文名（GameData 查表；未登记 id 回退原值——防御，正常路径加载期已校验存在） */
    private static String skillName(GameData data, String skillId) {
        SkillData skill = data.getSkill(skillId);
        return skill != null ? skill.getName() : skillId;
    }
```

```java
修改后：
    /** 通知行截断列宽上限（feedback06 口径 A2-3）：16 列 = 192px，NOTIFY_X+6 起 → 右缘 218 < 棋盘左缘 224；
     *  覆盖全部现实文案（4 字名 +（敌方）+「 施放 」+ 5 字技能名 ≈ 15.5 列），截断仅极端防御 */
    static final int NOTIFY_MAX_COLUMNS = 16;

    /** 战斗事件行（仅 UNIT_DIED/CAST——HIT/HEALED 过噪跳过，口径 #13 不扩；feedback06 加主体名：
     *  主体查 BattleState（含已清扫单位，死后仍可查）；技能行显中文名，查表失败回退 id） */
    static String formatEvent(CombatEvent event, GameData data, BattleState battleState) {
        switch (event.getType()) {
            case UNIT_DIED:
                return UnitInfoText.truncateColumns(
                        subjectName(battleState, event.getSourceId()) + " 倒下", NOTIFY_MAX_COLUMNS);
            case CAST:
                return UnitInfoText.truncateColumns(
                        subjectName(battleState, event.getSourceId()) + " 施放 "
                                + skillName(data, event.getSkillId()), NOTIFY_MAX_COLUMNS);
            default:
                return null;
        }
    }

    /** 事件主体名（纯函数）：查 BattleState.getUnitById → 模板中文名；敌方附「（敌方）」标记
     *  （feedback04-2 悬停卡同款字面）；查不到（state 缺失 / id 未登记——防御路径）回退 "#id" */
    static String subjectName(BattleState battleState, int unitId) {
        BattleUnit unit = battleState == null ? null : battleState.getUnitById(unitId);
        if (unit == null) {
            return "#" + unitId;
        }
        return unit.getTemplate().getName() + (unit.getSide() == Side.ENEMY ? "（敌方）" : "");
    }

    /** 技能中文名（GameData 查表；未登记 id 回退原值——防御，正常路径加载期已校验存在） */
    private static String skillName(GameData data, String skillId) {
        SkillData skill = data.getSkill(skillId);
        return skill != null ? skill.getName() : skillId;
    }
```

（`NotificationFormatTest.java` 战斗事件行区块改写，修改前逐字摘自 NotificationFormatTest.java:77-111；夹具新增 `import` 段增 `com.voidvvv.kz_auto_chess_n.entities.Side`、`com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures`、`com.voidvvv.kz_auto_chess_n.entities.BattleState`）：

```java
修改前：
    @Test
    @DisplayName("战斗事件行：UNIT_DIED → 单位倒下")
    void unitDiedLine() {
        assertThat(NotificationPanel.formatEvent(CombatEvent.unitDied(3, 11), emptyData()))
                .isEqualTo("单位倒下");
    }

    @Test
    @DisplayName("战斗事件行：CAST 显中文技能名（GameData 查表）")
    void castLineWithSkillName() {
        GameData data = new GameData(new LinkedHashMap<String, UnitData>(),
                skillsOf("skill_fireball", "火球术"),
                new LinkedHashMap<String, SynergyData>(),
                new LinkedHashMap<String, SceneData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.EquipmentData>(),
                new ArrayList<String>());
        assertThat(NotificationPanel.formatEvent(
                CombatEvent.cast(3, 1, 2, "skill_fireball"), data))
                .isEqualTo("技能施放：火球术");
    }

    @Test
    @DisplayName("战斗事件行：CAST 查表失败回退原始 id（防御路径）")
    void castLineFallsBackToId() {
        assertThat(NotificationPanel.formatEvent(
                CombatEvent.cast(3, 1, 2, "skill_ghost"), emptyData()))
                .isEqualTo("技能施放：skill_ghost");
    }

    @Test
    @DisplayName("战斗事件行：HIT 过噪跳过（返回 null）")
    void hitFilteredOut() {
        assertThat(NotificationPanel.formatEvent(CombatEvent.hit(3, 1, 2, 12.5f, false, null), emptyData()))
                .isNull();
    }
```

```java
修改后：
    // —— feedback06 夹具：微型战斗（BattleTestFixtures 公开夹具；名字 = "夹具" + 模板 id） ——

    private static BattleState battleWith(BattleUnit... units) {
        return BattleTestFixtures.state(units);
    }

    @Test
    @DisplayName("战斗事件行：UNIT_DIED 带主体名（玩家侧）")
    void unitDiedLineWithSubject() {
        BattleState state = battleWith(BattleTestFixtures.unit(11, Side.PLAYER,
                BattleTestFixtures.tpl("u_a"), 0, 4));
        assertThat(NotificationPanel.formatEvent(CombatEvent.unitDied(3, 11), emptyData(), state))
                .isEqualTo("夹具u_a 倒下");
    }

    @Test
    @DisplayName("战斗事件行：UNIT_DIED 敌方主体带（敌方）标记（feedback04-2 同款字面）")
    void unitDiedLineMarksEnemy() {
        BattleState state = battleWith(BattleTestFixtures.unit(21, Side.ENEMY,
                BattleTestFixtures.tpl("u_e"), 0, 0));
        assertThat(NotificationPanel.formatEvent(CombatEvent.unitDied(3, 21), emptyData(), state))
                .isEqualTo("夹具u_e（敌方） 倒下");
    }

    @Test
    @DisplayName("战斗事件行：CAST 带主体名 + 中文技能名（GameData 查表）")
    void castLineWithSubjectAndSkillName() {
        GameData data = new GameData(new LinkedHashMap<String, UnitData>(),
                skillsOf("skill_fireball", "火球术"),
                new LinkedHashMap<String, SynergyData>(),
                new LinkedHashMap<String, SceneData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.EquipmentData>(),
                new ArrayList<String>());
        BattleState state = battleWith(BattleTestFixtures.unit(1, Side.PLAYER,
                BattleTestFixtures.tpl("u_a"), 0, 4));
        assertThat(NotificationPanel.formatEvent(
                CombatEvent.cast(3, 1, 2, "skill_fireball"), data, state))
                .isEqualTo("夹具u_a 施放 火球术");
    }

    @Test
    @DisplayName("战斗事件行：CAST 敌方主体带（敌方）标记")
    void castLineMarksEnemy() {
        GameData data = new GameData(new LinkedHashMap<String, UnitData>(),
                skillsOf("skill_fireball", "火球术"),
                new LinkedHashMap<String, SynergyData>(),
                new LinkedHashMap<String, SceneData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.EquipmentData>(),
                new ArrayList<String>());
        BattleState state = battleWith(BattleTestFixtures.unit(1, Side.ENEMY,
                BattleTestFixtures.tpl("u_e"), 0, 0));
        assertThat(NotificationPanel.formatEvent(
                CombatEvent.cast(3, 1, 2, "skill_fireball"), data, state))
                .isEqualTo("夹具u_e（敌方） 施放 火球术");
    }

    @Test
    @DisplayName("战斗事件行：技能查表失败回退原始 id（防御路径，主体仍解析）")
    void castLineFallsBackToSkillId() {
        BattleState state = battleWith(BattleTestFixtures.unit(1, Side.PLAYER,
                BattleTestFixtures.tpl("u_a"), 0, 4));
        assertThat(NotificationPanel.formatEvent(
                CombatEvent.cast(3, 1, 2, "skill_ghost"), emptyData(), state))
                .isEqualTo("夹具u_a 施放 skill_ghost");
    }

    @Test
    @DisplayName("战斗事件行：主体查不到回退 #id（id 不在战斗 / state 为 null——防御路径）")
    void subjectFallsBackToHashId() {
        BattleState state = battleWith(BattleTestFixtures.unit(1, Side.PLAYER,
                BattleTestFixtures.tpl("u_a"), 0, 4));
        assertThat(NotificationPanel.formatEvent(CombatEvent.unitDied(3, 99), emptyData(), state))
                .isEqualTo("#99 倒下");
        assertThat(NotificationPanel.formatEvent(CombatEvent.unitDied(3, 99), emptyData(), null))
                .isEqualTo("#99 倒下");
    }

    @Test
    @DisplayName("战斗事件行：极端长主体截断 ≤16 列且以 … 收尾（口径 A2-3）")
    void longSubjectLineTruncated() {
        BattleState state = battleWith(BattleTestFixtures.unit(1, Side.ENEMY,
                BattleTestFixtures.tpl("名字特别长的测试单位"), 0, 0));
        String line = NotificationPanel.formatEvent(
                CombatEvent.cast(3, 1, 2, "skill_fireball"), emptyData(), state);
        assertThat(UnitInfoText.columns(line)).isLessThanOrEqualTo(NotificationPanel.NOTIFY_MAX_COLUMNS);
        assertThat(line).endsWith("…");
    }

    @Test
    @DisplayName("战斗事件行：HIT 过噪跳过（返回 null；口径 #13 维持不变）")
    void hitFilteredOut() {
        assertThat(NotificationPanel.formatEvent(
                CombatEvent.hit(3, 1, 2, 12.5f, false, null), emptyData(), null)).isNull();
    }

    // —— feedback06：BattleState 生命周期（syncBattle 与 inbox 对齐） ——

    @Test
    @DisplayName("syncBattle 保留/释放 BattleState 引用（attach 赋值、detach 置 null）")
    void syncBattleRetainsAndReleasesState() {
        NotificationPanel panel = new NotificationPanel(null, () -> null,
                new com.voidvvv.kz_auto_chess_n.command.CommandManager()); // refresh 零 GL：assets 可 null
        BattleState state = battleWith(BattleTestFixtures.unit(1, Side.PLAYER,
                BattleTestFixtures.tpl("u_a"), 0, 4));

        panel.syncBattle(state);
        assertThat(panel.currentBattle()).isSameAs(state);

        panel.syncBattle(null);
        assertThat(panel.currentBattle()).isNull();
    }
```

（测试类 import 增 `com.voidvvv.kz_auto_chess_n.entities.BattleState`、`com.voidvvv.kz_auto_chess_n.entities.BattleUnit`、`com.voidvvv.kz_auto_chess_n.entities.Side`、`com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures`；类注释「战斗事件行三态」更新为「带主体名」。）

- **测试要点**：即上述 NotificationFormatTest 改写与新增（TDD 先行，合计 9 个用例：主体 5 + 回退/截断 2 + HIT 1 + 生命周期 1）。`BattleTestFixtures.state` 落格要求格位互异——玩家 (0,4)、敌方 (0,0) 各一。

### CP-B1. EquipmentInfoText 装备文案纯函数（三展示点共用文案源）

- **类型**：新建文件
- **位置**：`core/src/main/java/com/voidvvv/kz_auto_chess_n/render/ui/EquipmentInfoText.java`（新建）+ `core/src/test/java/com/voidvvv/kz_auto_chess_n/render/ui/EquipmentInfoTextTest.java`（新建）
- **改动说明**：feedback07 单一文案源（用户裁决 2：禁止三处各写一份格式化）。词表与 UnitInfoText 同源（`statLabel` public 直用、`numberText` 同包包级直用）；effectText 的 ADD/PCT/百分比刻度分支与 `UnitInfoText.effectText`（UnitInfoText.java:50-63）逐字同构；被动文案对齐 GDD §5.2 龙心行原文（口径 B1-2）。三种出口形态：`effectEntries`（逐条目，悬停卡/宝箱行）、`effectSummary`（" · " 连接单串，详情弹窗）、`lines`（名 + 稀有度·槽位 + 条目，背包卡首选用）。零 Gdx 依赖。
- **代码**（新建文件完整代码）：

```java
package com.voidvvv.kz_auto_chess_n.render.ui;

import com.voidvvv.kz_auto_chess_n.data.EffectOp;
import com.voidvvv.kz_auto_chess_n.data.EquipmentData;
import com.voidvvv.kz_auto_chess_n.data.EquipmentEffect;
import com.voidvvv.kz_auto_chess_n.data.EquipmentPassive;
import com.voidvvv.kz_auto_chess_n.data.EquipmentRarity;
import com.voidvvv.kz_auto_chess_n.data.EquipmentSlot;
import com.voidvvv.kz_auto_chess_n.data.StatusType;

import java.util.ArrayList;
import java.util.List;

/**
 * 装备文案格式化纯函数（feedback07）：详情弹窗装备行 / 背包格悬停卡 / 宝箱选项行
 * 三展示点共用同一文案源（用户裁决：禁止三处各写一份格式化）。
 * 零 Gdx 依赖，headless 可测（TDD 先行）。
 *
 * <p>词汇表与 {@link UnitInfoText} 同源：statLabel/numberText 复用、百分比刻度键附 %，
 * 装备 effects 与 synergies 共用 {stat, op, value} 词汇（data_schema §八）。
 * 被动文案对齐 GDD §5.2 龙心行原文（「每 5 秒回复 2% 最大生命」），加「被动：」前缀
 * 区分属性条目；空格为 {@link UnitInfoText#wrap} 的断点服务。
 */
public final class EquipmentInfoText {

    private EquipmentInfoText() {
    }

    /** 稀有度文案（GDD §5.2 白/成/传；词表名 WHITE/RARE/LEGENDARY 见 WARNING-2） */
    public static String rarityLabel(EquipmentRarity rarity) {
        switch (rarity) {
            case LEGENDARY:
                return "传说";
            case RARE:
                return "成装";
            case WHITE:
            default:
                return "白装";
        }
    }

    /** 槽位文案（GDD §5.2 武器/盔甲/饰品；背包格内短形武/甲/饰为 InventoryPanel.slotMark 既有现状） */
    public static String slotLabel(EquipmentSlot slot) {
        switch (slot) {
            case WEAPON:
                return "武器";
            case ARMOR:
                return "盔甲";
            case TRINKET:
            default:
                return "饰品";
        }
    }

    /** 单条效果 → 数值文案（与 UnitInfoText.effectText 同词汇）：PCT → 标签+v%；ADD → 标签+v（百分比刻度键附 %） */
    public static String effectText(EquipmentEffect effect) {
        String label = UnitInfoText.statLabel(effect.getStat());
        if (effect.getOp() == EffectOp.PCT) {
            return label + "+" + UnitInfoText.numberText(effect.getValue()) + "%";
        }
        String suffix = effect.getStat().isPercentScale() ? "%" : "";
        return label + "+" + UnitInfoText.numberText(effect.getValue()) + suffix;
    }

    /** 被动文案（本期仅 REGEN——JsonLoader 加载期已限制；power = maxHp 比例/跳、tickInterval = 秒/跳，
     *  StatusSystem 心跳落地语义）。非 REGEN 回退词表名（防御不炸，正常路径不可达） */
    public static String passiveText(EquipmentPassive passive) {
        if (passive.getType() == StatusType.REGEN) {
            return "被动：每 " + UnitInfoText.numberText(passive.getTickInterval())
                    + " 秒 回复 " + Math.round(passive.getPower() * 100f) + "% 最大生命";
        }
        return "被动：" + passive.getType().jsonName();
    }

    /** 效果条目行集（悬停卡/宝箱行形态）：各效果一行 + 被动一行；无任何条目 = 空列表 */
    public static List<String> effectEntries(EquipmentData template) {
        List<String> entries = new ArrayList<String>();
        for (EquipmentEffect effect : template.getEffects()) {
            entries.add(effectText(effect));
        }
        if (template.getPassive() != null) {
            entries.add(passiveText(template.getPassive()));
        }
        return entries;
    }

    /** 效果摘要单串（详情弹窗形态）：条目 " · " 连接；无任何条目 = 空串 */
    public static String effectSummary(EquipmentData template) {
        StringBuilder sb = new StringBuilder();
        for (String entry : effectEntries(template)) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(entry);
        }
        return sb.toString();
    }

    /** 悬停卡行集：名 → 稀有度·槽位 → 效果条目（模板级，调用方折行/截断） */
    public static List<String> lines(EquipmentData template) {
        List<String> lines = new ArrayList<String>();
        lines.add(template.getName());
        lines.add(rarityLabel(template.getRarity()) + "·" + slotLabel(template.getSlot()));
        lines.addAll(effectEntries(template));
        return lines;
    }
}
```

- **测试要点**：新建 `EquipmentInfoTextTest`（TDD 先行），夹具沿 `InventoryPanelTest.sword` / `ChestDialogTest.eq` 手搓 `EquipmentData` 先例，另加龙心镜像模板（HP ADD 400 + REGEN 0.02/5）。断言意图：
  - `effectText`：PCT attack 35 → 「攻击+35%」；ADD hp 400 → 「生命+400」；ADD armor 20 → 「护甲+20」；ADD lifesteal 10 → 「吸血+10%」（百分比刻度键）；PCT energyGainRate 15 → 「回能+15%」。
  - `passiveText`：REGEN 0.02/5 → 「被动：每 5 秒 回复 2% 最大生命」；STUN（防御）→ 「被动：STUN」。
  - `rarityLabel`/`slotLabel`：三值全覆盖（白装/成装/传说；武器/盔甲/饰品）。
  - `effectEntries` 龙心 → `["生命+400", "被动：每 5 秒 回复 2% 最大生命"]`；无效果无被动 → 空列表。
  - `effectSummary` 龙心 → `"生命+400 · 被动：每 5 秒 回复 2% 最大生命"`；空 → `""`。
  - `lines` 铁剑（attack PCT 20）→ `["铁剑", "白装·武器", "攻击+20%"]`。

### CP-B2. UnitDetailDialog 卸下按钮右侧效果列

- **类型**：修改类（常量×2 + 静态方法×1 + UnequipButton 字段/构造/draw）+ import
- **位置**：`core/src/main/java/com/voidvvv/kz_auto_chess_n/render/ui/UnitDetailDialog.java`：常量段（:38-41）、`sameEquippedIds` 后（:142）、`UnequipButton`（:184-207）、import 段（:17-19）
- **改动说明**：feedback07 展示点 ①。版式 = 按钮右侧追加效果列（§5.2 推演：不动 EQUIP_Y0/EQUIP_STEP/按钮尺寸与位置、不碰重建指纹输入——feedback04 的「单位 id + 装备 id 序列」指纹（:97-104）原样保留，效果文本在按钮重建时预计算一次，draw 零分配）。效果列画在 `getX()+getWidth()+8f`（=298）起、两行基线 y+16/y+4，右缘 298+144=442 < BG 右缘 450；三槽按钮 y 100/74/48 的对应行均在 BG（44~296）内，与文案区（末行基线 170）、关闭按钮（y 272~294）无重叠。术语对照：装备效果条目 ↔ `EquipmentInfoText.effectSummary`；效果列 ↔ `effectSideLines`。
- **代码**：

（import 段，修改前逐字摘自 UnitDetailDialog.java:17-19）：

```java
修改前：
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
```

```java
修改后：
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
```

（常量段，修改前逐字摘自 UnitDetailDialog.java:38-41）：

```java
修改前：
    /** 装备区（42d9104 防遮挡方案的延续：卸下按钮自 y 下排，与文案区行底留 ≥28px） */
    private static final float EQUIP_X = 90f;
    private static final float EQUIP_Y0 = 100f;
    private static final float EQUIP_STEP = 26f;
```

```java
修改后：
    /** 装备区（42d9104 防遮挡方案的延续：卸下按钮自 y 下排，与文案区行底留 ≥28px） */
    private static final float EQUIP_X = 90f;
    private static final float EQUIP_Y0 = 100f;
    private static final float EQUIP_STEP = 26f;
    /** 卸下按钮右侧效果列（feedback07 口径 B2-1）：12 列 = 144px ≤ 右侧可用 152px（298~442 < BG 右缘 450-8）；
     *  2 行 = 24px 与按钮同高（基线 y+16/y+4） */
    private static final int EFFECT_COLUMNS = 12;
    private static final int EFFECT_MAX_LINES = 2;
```

（`sameEquippedIds` 后新增静态纯函数；定位锚点：`UnitDetailDialog.java:142` 的 `}` 与 `:144` `@Override` 之间）：

```java
新增：
    /** 卸下按钮右侧效果列行集（feedback07；纯函数，headless 可测）：摘要折行 12 列 × 截断 2 行；
     *  无效果无被动 = 空列表（不绘制） */
    static List<String> effectSideLines(Equipment item) {
        String summary = EquipmentInfoText.effectSummary(item.getTemplate());
        if (summary.isEmpty()) {
            return Collections.emptyList();
        }
        return UnitInfoText.clipLines(UnitInfoText.wrap(summary, EFFECT_COLUMNS), EFFECT_MAX_LINES);
    }
```

（UnequipButton，修改前逐字摘自 UnitDetailDialog.java:184-207）：

```java
修改前：
    /** 单件卸下按钮（UnequipItem 命令路径，input §2.4） */
    private final class UnequipButton extends Actor {
        private final Equipment item;

        UnequipButton(final Equipment item) {
            this.item = item;
            setSize(200f, 24f);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    commandManager.addCommand(new UnequipItemCommand(item.getId()));
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Color old = batch.getColor();
            batch.setColor(0.3f, 0.34f, 0.42f, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            assets.font().draw(batch, item.getTemplate().getName() + "  [卸下]",
                    getX() + 8f, getY() + 16f);
        }
    }
```

```java
修改后：
    /** 单件卸下按钮（UnequipItem 命令路径，input §2.4）；右侧效果列 = feedback07（重建时预计算，draw 零分配） */
    private final class UnequipButton extends Actor {
        private final Equipment item;
        private final List<String> effectLines;

        UnequipButton(final Equipment item) {
            this.item = item;
            this.effectLines = effectSideLines(item);
            setSize(200f, 24f);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    commandManager.addCommand(new UnequipItemCommand(item.getId()));
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Color old = batch.getColor();
            batch.setColor(0.3f, 0.34f, 0.42f, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            assets.font().draw(batch, item.getTemplate().getName() + "  [卸下]",
                    getX() + 8f, getY() + 16f);
            for (int i = 0; i < effectLines.size(); i++) { // 右侧效果列（按钮命中域之外，纯绘制）
                assets.font().draw(batch, effectLines.get(i),
                        getX() + getWidth() + 8f, getY() + 16f - i * 12f);
            }
        }
    }
```

- **测试要点**：`UnitDetailDialogTest` 新增 `effectSideLines` 直测（TDD 先行）：(1) 龙心镜像模板（HP ADD 400 + REGEN 0.02/5）→ 恰好 `["生命+400 · 被动：每 5 秒", "回复 2% 最大生命"]`（12 列贪心折行断点实测推演，见 §8 WARNING-8 的复核提示）；(2) 空效果模板（`effects=[]`、`passive=null`，沿既有 `eq` 夹具）→ 空列表。既有指纹用例（:138-211）必须原样全绿——本 CP 不改 `refresh`/`rebuildUnequipButtons`/`sameEquippedIds`。

### CP-B3. InventoryPanel 悬停槽位源（enter/exit + getter）

- **类型**：修改类（字段 + getter + InventorySlot 构造追加监听）+ import
- **位置**：`core/src/main/java/com/voidvvv/kz_auto_chess_n/render/ui/InventoryPanel.java`：import 段（:5-8）、字段段（:37-39）、`refresh()`（:50-53）、`InventorySlot` 构造（:78-99）
- **改动说明**：feedback07 展示点 ②的源侧。逐字沿 `ShopBar.ShopCard` 的 enter/exit 模式（ShopBar.java:125-137）：`hoveredSlot` 原始暴露（-1 = 无），空槽/越界/置灰期归一在卡侧（CP-B4 的 `normalizeInventorySlot`，§5.3-8 同口径）。本 CP 不绘制任何东西——卡的驻留/绘制全在 HoverPreviewCard。ClickListener（两段式穿戴起点）零改动。
- **代码**：

（import 段，修改前逐字摘自 InventoryPanel.java:5-8）：

```java
修改前：
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
```

```java
修改后：
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
```

（字段段 + getter，修改前逐字摘自 InventoryPanel.java:37-39 与 :50-54）：

```java
修改前：
    private final Assets assets;
    private final Supplier<RunContext> context;
    private final EquipPendingState pending;
```

```java
修改后：
    private final Assets assets;
    private final Supplier<RunContext> context;
    private final EquipPendingState pending;
    /** 悬停中的背包槽位（feedback07：InventorySlot enter/exit 维护；-1 = 无；原始暴露，归一见 HoverPreviewCard） */
    private int hoveredSlot = -1;
```

（getter 插入 `refresh()` 之前；定位锚点：InventoryPanel.java:50-53 的 refresh 方法块）：

```java
修改前：
    /** 每帧刷新（无内部缓存：待定高亮与置灰随 ctx 变化即时反映） */
    public void refresh() {
        reconcilePending(pending, context.get().getPlayer());
    }
```

```java
修改后：
    /** 悬停中的背包槽位（feedback07）；无悬停 = -1（空槽/BATTLE 置灰期的归一在 HoverPreviewCard.refresh） */
    public int getHoveredSlot() {
        return hoveredSlot;
    }

    /** 每帧刷新（无内部缓存：待定高亮与置灰随 ctx 变化即时反映） */
    public void refresh() {
        reconcilePending(pending, context.get().getPlayer());
    }
```

（InventorySlot 构造，修改前逐字摘自 InventoryPanel.java:78-99）：

```java
修改前：
        InventorySlot(int index) {
            this.index = index;
            int[] center = BoardGeometry.inventorySlotCenter(index);
            setSize(BoardGeometry.INVENTORY_SLOT_W, BoardGeometry.INVENTORY_SLOT_H);
            setPosition(center[0] - BoardGeometry.INVENTORY_SLOT_W / 2f,
                    center[1] - BoardGeometry.INVENTORY_SLOT_H / 2f);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    Equipment item = itemAt();
                    if (item == null) {
                        pending.clear(); // 点空白取消
                        return;
                    }
                    if (pending.pendingItemId() == item.getId()) {
                        pending.clear(); // 再点同一装备 = 取消（input §2.4）
                    } else {
                        pending.set(item.getId()); // 进入待定态（等棋子点击落点）
                    }
                }
            });
        }
```

```java
修改后：
        InventorySlot(int index) {
            this.index = index;
            int[] center = BoardGeometry.inventorySlotCenter(index);
            setSize(BoardGeometry.INVENTORY_SLOT_W, BoardGeometry.INVENTORY_SLOT_H);
            setPosition(center[0] - BoardGeometry.INVENTORY_SLOT_W / 2f,
                    center[1] - BoardGeometry.INVENTORY_SLOT_H / 2f);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    Equipment item = itemAt();
                    if (item == null) {
                        pending.clear(); // 点空白取消
                        return;
                    }
                    if (pending.pendingItemId() == item.getId()) {
                        pending.clear(); // 再点同一装备 = 取消（input §2.4）
                    } else {
                        pending.set(item.getId()); // 进入待定态（等棋子点击落点）
                    }
                }
            });
            addListener(new InputListener() { // feedback07 悬停槽位轨迹（Scene2D enter/exit，沿 ShopBar 先例）
                @Override
                public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                    hoveredSlot = index;
                }

                @Override
                public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                    if (hoveredSlot == index) {
                        hoveredSlot = -1;
                    }
                }
            });
        }
```

- **测试要点**：enter/exit 交互走 lwjgl3 手验（沿 InventoryPanelTest 既有口径「槽位点击交互与绘制走 lwjgl3 手验」，ShopBar 悬停同款无单测）；既有 `reconcilePending` 四用例必须原样全绿（本 CP 不触EquipPendingState 逻辑）。

### CP-B4. HoverPreviewCard 背包第三源 + BoardGeometry 锚点 + BattleScreen 传参

- **类型**：修改类（BoardGeometry 常量 + HoverPreviewCard 常量/字段/refresh/recompute/新增静态×2/类注释 + BattleScreen 调用点）+ import
- **位置**：
  - `core/src/main/java/com/voidvvv/kz_auto_chess_n/render/board/BoardGeometry.java:61-65`（SHOP_HOVER 块后）
  - `core/src/main/java/com/voidvvv/kz_auto_chess_n/render/ui/HoverPreviewCard.java`：类注释（:21-28）、常量段（:36-39）、字段段（:47-48）、`refresh`（:60-77）、`recompute`（:78-98）、`boardCardLines` 后（:110）、import 段（:3-9）
  - `core/src/main/java/com/voidvvv/kz_auto_chess_n/screens/BattleScreen.java:268-269`
- **改动说明**：feedback07 展示点 ②的卡侧。HoverPreviewCard 从双源扩三源（棋盘 > 商店 > 背包，口径 B4-4）：第三台 `HoverStateMachine` 复用（250ms 驻零新代码）；`normalizeInventorySlot(phase, slot, size)` 三参纯函数承担 BATTLE 置灰抑制 + 空槽/越界归一（口径 B4-1，抑制施加于查询侧沿 §5.3-8）；`inventoryCardLines` 静态纯函数生成装备卡行集（`EquipmentInfoText.lines` 折 6 列截 7 行，口径 B4-3）；锚点 `(132,140,90,100)` 落在棋盘域卡同一空带但指针命中域互斥（§5.2 论证）。BattleScreen 调用点加第四参（含拖拽抑制，口径 B4-2）。术语对照：背包格悬停卡 ↔ `inventoryHover`/`INVENTORY_HOVER_*`/`inventoryCardLines`。
- **代码**：

（BoardGeometry，修改前逐字摘自 BoardGeometry.java:61-66）：

```java
修改前：
    /** 商店卡悬停卡：精确覆盖 ⑤ 羁绊面板区（瞬态覆盖，移开即恢复；⑥ 实际在左侧——差异声明 #2） */
    public static final int SHOP_HOVER_X = 508;
    public static final int SHOP_HOVER_Y = 48;
    public static final int SHOP_HOVER_W = 112;
    public static final int SHOP_HOVER_H = 192;

    public static final int CELL = 32;
```

```java
修改后：
    /** 商店卡悬停卡：精确覆盖 ⑤ 羁绊面板区（瞬态覆盖，移开即恢复；⑥ 实际在左侧——差异声明 #2） */
    public static final int SHOP_HOVER_X = 508;
    public static final int SHOP_HOVER_Y = 48;
    public static final int SHOP_HOVER_W = 112;
    public static final int SHOP_HOVER_H = 192;

    /** feedback07 背包格装备悬停卡：③ 背包（右缘 128）与 ④ 棋盘（左缘 224）之间空带内、③ 顶 172 之上。
     *  与棋盘域卡（128,48,94,192）同带不同源——单指针命中域互斥（背包 x≤128 y172~244，棋盘/备战
     *  候选均在界外）；底边 240 避开 ⑨ 通知（y 244 起）、顶边 140 避开 ⑥ 开战按钮（y 88~128）。 */
    public static final int INVENTORY_HOVER_X = 132;
    public static final int INVENTORY_HOVER_Y = 140;
    public static final int INVENTORY_HOVER_W = 90;
    public static final int INVENTORY_HOVER_H = 100;

    public static final int CELL = 32;
```

（HoverPreviewCard import 段，修改前逐字摘自 HoverPreviewCard.java:3-9）：

```java
修改前：
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
```

```java
修改后：
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.data.EquipmentData;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
```

（常量段，修改前逐字摘自 HoverPreviewCard.java:37-39）：

```java
修改前：
    /** 折行列宽（§5.3-5：(W - 8) / 12 取整） */
    private static final int BOARD_MAX_COLUMNS = 7;
    private static final int SHOP_MAX_COLUMNS = 8;
```

```java
修改后：
    /** 折行列宽（§5.3-5：(W - 8) / 12 取整） */
    private static final int BOARD_MAX_COLUMNS = 7;
    private static final int SHOP_MAX_COLUMNS = 8;
    /** feedback07 背包格装备卡：折行列宽 (90-8)/12 = 6；行容量 (100-8)/12 = 7 */
    private static final int INVENTORY_MAX_COLUMNS = 6;
    private static final int INVENTORY_LINE_CAPACITY = 7;
```

（字段段，修改前逐字摘自 HoverPreviewCard.java:47-48）：

```java
修改前：
    private final HoverStateMachine boardHover = new HoverStateMachine();
    private final HoverStateMachine shopHover = new HoverStateMachine();
```

```java
修改后：
    private final HoverStateMachine boardHover = new HoverStateMachine();
    private final HoverStateMachine shopHover = new HoverStateMachine();
    /** feedback07 背包格第三源（驻留键 = 槽位索引；与双源同一状态机实现） */
    private final HoverStateMachine inventoryHover = new HoverStateMachine();
```

（refresh，修改前逐字摘自 HoverPreviewCard.java:69-77；javadoc 的 `@param` 增 `inventorySlot` 说明）：

```java
修改前：
    public void refresh(HoverCandidate boardCandidate, int shopSlot, boolean suppressed, float delta) {
        RunContext ctx = context.get();
        boolean shopping = ctx.getRunState().getPhase() == GamePhase.SHOPPING;
        int shop = shopping && shopSlot >= 0 && ctx.getShop().slotAt(shopSlot) != null ? shopSlot : -1;
        boardHover.update(boardCandidate.key(), suppressed, delta);
        shopHover.update(shop, suppressed, delta);
        recompute(ctx, boardCandidate);
    }
```

```java
修改后：
    public void refresh(HoverCandidate boardCandidate, int shopSlot, int inventorySlot,
                        boolean suppressed, float delta) {
        RunContext ctx = context.get();
        boolean shopping = ctx.getRunState().getPhase() == GamePhase.SHOPPING;
        int shop = shopping && shopSlot >= 0 && ctx.getShop().slotAt(shopSlot) != null ? shopSlot : -1;
        int inventory = normalizeInventorySlot(ctx.getRunState().getPhase(), inventorySlot,
                ctx.getPlayer().getInventory().size()); // BATTLE 置灰/空槽/越界 → -1（口径 B4-1）
        boardHover.update(boardCandidate.key(), suppressed, delta);
        shopHover.update(shop, suppressed, delta);
        inventoryHover.update(inventory, suppressed, delta);
        recompute(ctx, boardCandidate);
    }
```

（recompute，修改前逐字摘自 HoverPreviewCard.java:78-98）：

```java
修改前：
    /** 行集与卡位重算（棋盘源优先；可见键必等于本帧候选键——update 在候选变化帧即时清可见） */
    private void recompute(RunContext ctx, HoverCandidate boardCandidate) {
        if (boardHover.visibleId() >= 0) {
            place(BoardGeometry.BOARD_HOVER_X, BoardGeometry.BOARD_HOVER_Y,
                    BoardGeometry.BOARD_HOVER_W, BoardGeometry.BOARD_HOVER_H);
            lines = boardCardLines(boardCandidate.template(), boardCandidate.isEnemy(),
                    ctx.getGameData());
            return;
        }
        UnitData shopTemplate = shopHover.visibleId() >= 0
                ? ctx.getShop().slotAt(shopHover.visibleId()) : null;
        if (shopTemplate == null) {
            lines = Collections.emptyList();
            return;
        }
        place(BoardGeometry.SHOP_HOVER_X, BoardGeometry.SHOP_HOVER_Y,
                BoardGeometry.SHOP_HOVER_W, BoardGeometry.SHOP_HOVER_H);
        lines = UnitInfoText.clipLines(
                UnitInfoText.previewLines(shopTemplate, ctx.getGameData(), SHOP_MAX_COLUMNS),
                SHOP_LINE_CAPACITY);
    }
```

```java
修改后：
    /** 行集与卡位重算（优先级：棋盘 > 商店 > 背包——单指针物理互斥，优先级为防御性定义；
     *  可见键必等于本帧候选键——update 在候选变化帧即时清可见） */
    private void recompute(RunContext ctx, HoverCandidate boardCandidate) {
        if (boardHover.visibleId() >= 0) {
            place(BoardGeometry.BOARD_HOVER_X, BoardGeometry.BOARD_HOVER_Y,
                    BoardGeometry.BOARD_HOVER_W, BoardGeometry.BOARD_HOVER_H);
            lines = boardCardLines(boardCandidate.template(), boardCandidate.isEnemy(),
                    ctx.getGameData());
            return;
        }
        UnitData shopTemplate = shopHover.visibleId() >= 0
                ? ctx.getShop().slotAt(shopHover.visibleId()) : null;
        if (shopTemplate != null) {
            place(BoardGeometry.SHOP_HOVER_X, BoardGeometry.SHOP_HOVER_Y,
                    BoardGeometry.SHOP_HOVER_W, BoardGeometry.SHOP_HOVER_H);
            lines = UnitInfoText.clipLines(
                    UnitInfoText.previewLines(shopTemplate, ctx.getGameData(), SHOP_MAX_COLUMNS),
                    SHOP_LINE_CAPACITY);
            return;
        }
        if (inventoryHover.visibleId() >= 0) { // feedback07 背包格装备卡（第三源）
            place(BoardGeometry.INVENTORY_HOVER_X, BoardGeometry.INVENTORY_HOVER_Y,
                    BoardGeometry.INVENTORY_HOVER_W, BoardGeometry.INVENTORY_HOVER_H);
            lines = inventoryCardLines(ctx.getPlayer().getInventory()
                    .get(inventoryHover.visibleId()).getTemplate());
            return;
        }
        lines = Collections.emptyList();
    }
```

（`boardCardLines` 后新增两个静态纯函数；定位锚点：HoverPreviewCard.java:110 的 `}` 与 `:112` `place` 之间）：

```java
新增：
    /** 背包格装备卡行集（feedback07；纯静态，headless 可测）：lines 逐行折行 → 容量截断（§5.3-4 同口径） */
    static List<String> inventoryCardLines(EquipmentData template) {
        List<String> wrapped = new ArrayList<String>();
        for (String line : EquipmentInfoText.lines(template)) {
            wrapped.addAll(UnitInfoText.wrap(line, INVENTORY_MAX_COLUMNS));
        }
        return UnitInfoText.clipLines(wrapped, INVENTORY_LINE_CAPACITY);
    }

    /** 背包悬停归一（feedback07 口径 B4-1；纯函数三参）：BATTLE 置灰期（差异声明 #8——非交互只读
     *  快照）/ 空槽（slot ≥ 背包数）/ 负值 → -1；抑制施加于查询侧（§5.3-8 同口径） */
    static int normalizeInventorySlot(GamePhase phase, int slot, int inventorySize) {
        if (phase == GamePhase.BATTLE || slot < 0 || slot >= inventorySize) {
            return -1;
        }
        return slot;
    }
```

（类注释首段更新——修改前逐字摘自 HoverPreviewCard.java:21-24）：

```java
修改前：
 * 悬停预览卡（Phase 5.1 R1，裁决 A：固定锚、不跟随鼠标、只读精简；feedback04 敌方悬停）。
 * 棋盘域与商店卡各一锚点、各一 {@link HoverStateMachine}（双源互斥——单指针不同时悬停
 * 两处，棋盘源优先）；行集由 {@link UnitInfoText#previewLines} 生成（模板级，不含
 * spend/已穿装备）；超出卡高的行按优先序截断、末行示 …（§5.3-4，完整信息走详情弹窗）。
```

```java
修改后：
 * 悬停预览卡（Phase 5.1 R1，裁决 A：固定锚、不跟随鼠标、只读精简；feedback04 敌方悬停；
 * feedback07 背包格装备卡）。棋盘域/商店卡/背包格各一锚点、各一 {@link HoverStateMachine}
 * （三源互斥——单指针不同时悬停多处，棋盘源优先）；棋子卡行集由
 * {@link UnitInfoText#previewLines} 生成（模板级，不含 spend/已穿装备——R1 口径限于棋子卡，
 * 背包格卡展示装备本体，行集走 EquipmentInfoText，差异声明 #D1）；超出卡高的行按优先序
 * 截断、末行示 …（§5.3-4，完整信息走详情弹窗）。
```

（BattleScreen 调用点，修改前逐字摘自 BattleScreen.java:268-269）：

```java
修改前：
        hoverPreview.refresh(boardProcessor == null ? HoverCandidate.NONE : boardProcessor.getHoverCandidate(),
                shopBar.getHoveredSlot(), frozen, frozen ? 0f : delta); // R1+feedback04：候选/槽位/冻结位（§5.3-8）
```

```java
修改后：
        hoverPreview.refresh(boardProcessor == null ? HoverCandidate.NONE : boardProcessor.getHoverCandidate(),
                shopBar.getHoveredSlot(),
                boardProcessor != null && boardProcessor.isDragging() ? -1 : inventoryPanel.getHoveredSlot(), // feedback07：拖拽中抑制背包源（口径 B4-2）
                frozen, frozen ? 0f : delta); // R1+feedback04+feedback07：候选/槽位/冻结位（§5.3-8）
```

- **测试要点**：`HoverPreviewCardTest` 新增（TDD 先行）：
  - `inventoryCardLines`：(1) 铁剑镜像（attack PCT 20）→ `["铁剑", "白装·武器", "攻击+20%"]`（各行 ≤6 列不折）；(2) 龙心镜像 → 恰好 `["龙心", "传说·盔甲", "生命+400", "被动：每 5", "秒 回复 2%", "最大生命"]`（6 列贪心断点推演值，评审可 grep 复核）；(3) 多效果模板（4 效果 + 被动，条目行超 7）→ 截断为 6 行 + 末行 `…`。
  - `normalizeInventorySlot`：`(SHOPPING, 2, 3) → 2`；`(BATTLE, 2, 3) → -1`（置灰抑制）；`(SHOPPING, 3, 3) → -1`（空槽）；`(SHOPPING, -1, 3) → -1`；`(RESULT, 2, 3) → 2`；`(RUN_END, 0, 1) → 0`。
  - 既有双源用例改 `refresh` 四参签名（原第三参 `suppressed` 前插入 `-1`）后必须原样全绿。
  - BoardGeometry 常量为纯常量不加单测（沿 5.1 R1 锚点先例）。

### CP-B5. ChestDialog 装备选项效果行

- **类型**：修改类（常量×2 + 静态方法×1 + OptionButton.draw 两段式）+ import
- **位置**：`core/src/main/java/com/voidvvv/kz_auto_chess_n/render/ui/ChestDialog.java`：import 段（:9-16 后的 java 段）、常量段（:25-27）、`optionTint` 后（:68）、`OptionButton.draw`（:85-98）
- **改动说明**：feedback07 展示点 ③。`optionText`（:49-59，装备分支取模板名）与 `optionTint`（:62-68，传说金棕）零改动；新增 `optionEffectLines(data, option)` 纯函数（金币/经验 → 空列表，装备 → `effectEntries` 逐条折 8 列截 3 行，口径 B5-1）；OptionButton.draw 两段式——无效果行走原版式（名 y+34 + 「选择」y+14），有效果行画 名 y+46 + 效果行 y+32/20/8（「选择」让位，口径 B5-2；按钮 120×60、ClickListener 零改动）。术语对照：装备选项效果行 ↔ `optionEffectLines`。
- **代码**：

（import 段，修改前逐字摘自 ChestDialog.java:15-16）：

```java
修改前：
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
```

```java
修改后：
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
```

（常量段，修改前逐字摘自 ChestDialog.java:25-27）：

```java
修改前：
    /** 选项底色（static final：渲染段零分配；传说金棕与 InventoryPanel 同源） */
    private static final Color TINT_LEGENDARY = new Color(0.55f, 0.42f, 0.12f, 1f);
    private static final Color TINT_DEFAULT = new Color(0.3f, 0.32f, 0.4f, 1f);
```

```java
修改后：
    /** 选项底色（static final：渲染段零分配；传说金棕与 InventoryPanel 同源） */
    private static final Color TINT_LEGENDARY = new Color(0.55f, 0.42f, 0.12f, 1f);
    private static final Color TINT_DEFAULT = new Color(0.3f, 0.32f, 0.4f, 1f);
    /** feedback07 装备选项效果行：折行列宽 (120-14-2)/12 = 8；行容量 3（名行 y+46 之下 y+32/20/8） */
    private static final int OPTION_MAX_COLUMNS = 8;
    private static final int OPTION_LINE_CAPACITY = 3;
```

（`optionTint` 后新增静态纯函数；定位锚点：ChestDialog.java:68 的 `}` 与 `:70` `private final class OptionButton` 之间）：

```java
新增：
    /** 装备选项效果行（feedback07；纯函数，headless 可测）：effectEntries 逐条折行 8 列 × 截断 3 行；
     *  金币/经验选项 = 空列表（走原版式） */
    static List<String> optionEffectLines(GameData data, ChestOption option) {
        if (option.getKind() != ChestOption.Kind.EQUIPMENT) {
            return Collections.emptyList();
        }
        List<String> wrapped = new ArrayList<String>();
        for (String entry : EquipmentInfoText.effectEntries(data.getEquipment(option.getEquipmentId()))) {
            wrapped.addAll(UnitInfoText.wrap(entry, OPTION_MAX_COLUMNS));
        }
        return UnitInfoText.clipLines(wrapped, OPTION_LINE_CAPACITY);
    }
```

（OptionButton.draw，修改前逐字摘自 ChestDialog.java:85-98）：

```java
修改前：
        @Override
        public void draw(Batch batch, float parentAlpha) {
            if (offer == null) {
                return;
            }
            ChestOption option = offer.optionAt(index);
            Color tint = optionTint(data, option);
            Color old = batch.getColor();
            batch.setColor(tint.r, tint.g, tint.b, 0.95f * parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            assets.font().draw(batch, optionText(data, option), getX() + 14f, getY() + 34f);
            assets.font().draw(batch, "选择", getX() + 44f, getY() + 14f);
        }
```

```java
修改后：
        @Override
        public void draw(Batch batch, float parentAlpha) {
            if (offer == null) {
                return;
            }
            ChestOption option = offer.optionAt(index);
            Color tint = optionTint(data, option);
            Color old = batch.getColor();
            batch.setColor(tint.r, tint.g, tint.b, 0.95f * parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            List<String> effects = optionEffectLines(data, option);
            if (effects.isEmpty()) { // 金币/经验书：原版式（名 + 选择）
                assets.font().draw(batch, optionText(data, option), getX() + 14f, getY() + 34f);
                assets.font().draw(batch, "选择", getX() + 44f, getY() + 14f);
                return;
            }
            // feedback07 装备选项：名 y+46 + 效果行 y+32/20/8（「选择」让位，口径 B5-2；整钮可点击不变）
            assets.font().draw(batch, optionText(data, option), getX() + 14f, getY() + 46f);
            for (int i = 0; i < effects.size(); i++) {
                assets.font().draw(batch, effects.get(i), getX() + 10f, getY() + 32f - i * 12f);
            }
        }
```

- **测试要点**：`ChestDialogTest` 新增（TDD 先行）：(1) 龙心镜像装备选项 → `["生命+400", "被动：每 5 秒", "回复 2% 最大生命"]`（8 列断点推演值）；(2) 金币/经验选项 → 空列表；(3) 4 效果模板（每条 ≤8 列）→ 截断为 2 行 + `…`。既有 `optionText`/`optionTint` 六用例原样全绿（本 CP 不改这两个函数）。

---

## 7. 分阶段任务拆解（executor 按序执行）与交付切分

| 任务 | 所含改动点 | 前置 | 验收标准 |
|------|-----------|------|----------|
| T1（A 组 = feedback06） | CP-A1 → CP-A2 | 无 | ① 607 基线全绿 + 新增用例全绿（UnitInfoTextTest 截断 4~5 例、NotificationFormatTest 改写+新增合计 9 例）；② 手验：小窗战斗期 CAST/倒下行带主体名、敌方带（敌方）、L 大窗同步、长行以 … 截断且不越过 x≈224 |
| T2（B 组 = feedback07） | CP-B1 → CP-B2 → CP-B3 → CP-B4 → CP-B5 | T1（同分支串行；代码上无依赖，CP-B1 先行供 B2/B4/B5 复用） | ① T1 后全绿基线 + 新增用例全绿（EquipmentInfoTextTest 全量、UnitDetailDialogTest/HoverPreviewCardTest/ChestDialogTest 增例）；② 手验：详情弹窗装备行右侧效果列（龙心两行）、背包格悬停 ~250ms 出卡（含龙心 6 行）、BATTLE 期不出卡、拖拽中不出卡、宝箱装备选项三行效果、传说底色保留 |

**交付切分（executor 执行约束）**：

1. Commit 1（T1）：`fix: Phase 5 feedback06 通知面板战斗事件行无主体——…`（A 组两 CP + 本文档入库；若惯例偏好文档单独提交，可拆为 `docs: feedback06/07 实施文档` 前置 commit，二选一，推荐随 Commit 1）。
2. Commit 2（T2）：`fix: Phase 5 feedback07 装备效果无 UI 查看入口——…`（B 组五 CP；含 docs/diagrams 四个图表文件若未随 Commit 1）。
3. 两 commit 各自独立全绿（T1 不得依赖 T2 的任何产物）；commit message 沿既有 feedback 修复体例（现象 → 根因 → 修法 → 测试计数）。

**验收核对方式（沿 MEMORY 口径）**：`gradle test` 成功时控制台零输出——用退出码 + `core/build/test-results/test/TEST-*.xml` 聚合 `<testsuite>` 计数核对（基线 607 + 本期新增 ≈ 25~30，以实际为准，旧用例零失败零删除）。

---

## 8. 风险与开放问题（WARNING，不阻塞）

| # | 风险/开放问题 | 说明 |
|---|------|------|
| W1 | render §5.5 文案模板比本次更丰富（「兽人战士★2 阵亡」「→ 3 目标」、分色、过滤标签页） | 本次按裁决范围最小落地（主体 + 敌方标记 + 截断），措辞沿用已交付「倒下」；星级/目标数/分色/过滤页对齐属后续任务（大窗无过滤已是 WARNING-6 既有项） |
| W2 | data_schema §三 rarity 词表名 `FINISHED` vs 代码 `RARE`（Phase 5 既有差异） | 本次 UI 词「成装」按 GDD §5.2 白/成/传，不触枚举名；Phase 6 回写候选 |
| W3 | 通知行 16 列截断可溢出小窗底宽（128px）至多到 x≈218 空背景带 | 沿既有 notices 行溢出先例（§5.1）；不遮挡任何面板；如手验认为溢出难看，可下调 NOTIFY_MAX_COLUMNS（常量单点改） |
| W4 | 大窗显示同样截断行 | 截断在入队前（口径 A2-5）；如需大窗全文需日志存双份（行宽不一时 UI 复杂化，YAGNI 未做） |
| W5 | 模态期 InventorySlot 的 enter/exit 可能停留在 stale 值（dialogStage 消费输入时 uiStage 不再派发） | frozen 抑制位兜底（模态期卡必隐藏）；关模态后首次 mouseMoved 恢复；与 ShopBar 悬停同款行为 |
| W6 | 主体解析依赖 `BattleState.units` 终身持有（已核实 :45-58） | 若未来改为清扫即移除，主体名将退化为 `#id`（防御回退已备，不炸）；BattleStateTest 已锁「含已清扫」契约 |
| W7 | 宝箱装备选项不画「选择」字样（口径 B5-2） | 手验如认为点击提示缺失，回补 `选择` 到 y+4 即可（低风险微调，不涉结构） |
| W8 | CP-B2/CP-B4/CP-B5 测试中的折行断点期望串（12/6/8 列贪心推演值）按 UnitInfoText.wrap 现行算法逐字符推演得出 | 执行时如断言失败，先核对 wrap 实际输出再判断是推演误差还是实现偏差——**以 wrap 实际行为为准修期望串**（wrap/clipLines 本身有既有测试锁定，不改动它们） |
| W9 | 背包格悬停与棋盘域卡锚区几何同带（x 128~222 重叠） | 单指针命中域互斥（B4-3 论证）；若未来出现双指针/触屏多点并发，需加显式互斥（当前单指针输入模型下不发生） |

---

## 9. 附录：用户确认记录（2026-08-22 裁决原文存档，随任务提供）

> **Q1（feedback06 范围与标记）**——用户裁决（最终，不再追问）：
> 「1. CAST 与 UNIT_DIED 两类事件行都加主体名。2. 敌方单位带「（敌方）」标记，与悬停卡 feedback04-2 的（敌方）标记惯例一致（参考 render/ui/HoverPreviewCard 与 commit 8735b25）。」
> 另：「口径 #13：HIT/HEALED 过噪跳过维持不变，不要扩事件类型范围。」

> **Q2（feedback07 展示点）**——用户裁决（最终，不再追问）：
> 「三个展示点全部接入——1. 详情弹窗装备行（UnitDetailDialog 卸下按钮行加效果文案）。2. 背包格悬停卡（InventoryPanel 悬停显示：名字+稀有度+槽位+效果行+被动描述）。3. 宝箱选项行（ChestDialog 装备选项加效果文案，便于三选一决策）。」
> 另：「三处展示点共用同一套纯函数文案，禁止三处各写一份格式化。」

> **授权拟定项**（用户原文摘录）：BattleState 生命周期方案、formatEvent 新签名与回退文案、行宽策略（「可能需要截断而非折行——你来定并写入口径」）、EquipmentInfoText 归属（「按项目小文件、纯函数、headless 可测惯例定」）、详情弹窗版式（「给出版式方案」）、背包悬停实现路径（「选最小侵入方案」）与 BATTLE 期抑制裁定（「给出理由」）、宝箱行文案格式——均已按 §5.3 口径落定。

> 本次核对未发现 BLOCKER 级矛盾（§3.4 七项核对全部收敛：四项为行号修正、两项为既有 WARNING 归档、一项确认不冲突），未触发澄清门禁。
