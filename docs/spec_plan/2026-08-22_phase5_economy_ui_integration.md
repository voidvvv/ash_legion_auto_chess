# Phase 5 经济·装备·UI 整合 技术实施文档

> 版本：V1.0（2026-08-22）　分支：feature/phase_5　前置：Phase 4（2852711，棋盘渲染全链已交付）
> 本文档为第二轮成稿：第一轮 6 条 BLOCKER 已由用户逐条裁决（§4/§9），按裁决落地方案。

---

## 1. 背景与目标

Phase 4 交付了 Screen/命令/渲染全链（`BattleScreen`、`CommandManager` 含 `onExecuted` 监听位、`RunFlowSystem` 三命令 handler），但经济与成长仍是"演示名单 + 胜负统一推进轮次"的占位闭环。Phase 5 按 GDD §三（纯宝箱经济）/§5.2（B2 装备）/§2.2（1C-R 判负原地重试）补齐**完整单局玩法**，并完成 UI 八区域布局中 Phase 4 遗留的 ③⑤⑦⑧⑨ 区。

**成功标准**：

1. 一局可从"起始 10 金商店自购"打到第 25 轮：买棋/刷新/买经验/3 合 1/卖棋/穿脱装备/宝箱三选一/怜悯保底全部可用且确定性（同 seed 同命令流 → 同结果）。
2. 判负原地重试（敌阵/商店/轮次不变）+ 胜局宝箱 → 下一轮（免费刷新）+ AbandonRun → RUN_END（放弃文案）。
3. 商店栏/背包/羁绊面板/出售区/通知面板/暂停菜单/宝箱弹窗/棋子详情按 render §九布局落位；dialogStage 四层输入链 + Escape/BACK 永可达。
4. Fusion Pixel 字体 + itch 小人替换 2~3 个棋子验证真素材流水线（Q4=B）。
5. 全量测试绿（gradle XML 核对，沿用 MEMORY 口径），每 CP 附测试要点（TDD 先行）。

**范围裁定**（详见 §4）：装备全链（Q1=A）；宝箱三选一按本文档最小可玩规则（Q2=A）；英雄选择/RunSetupScreen 推 Phase 6（Q3=B）；字体 + itch 小人（Q4=B）；终局三屏与存档推 Phase 6、暂停菜单本期做（Q5=A）；演示名单移除、严格 GDD 商店自购（Q6=A）。

### 明确不做（差异声明出处见 §5.2）

| 项 | 推迟到 | 出处 |
|----|--------|------|
| RunSetupScreen / 英雄选择界面 | Phase 6 | Q3 裁决 B |
| RunResultScreen / CodexScreen / 存档 `save/` | Phase 6 | Q5 裁决 A |
| Kenney CC0 UI 包 / `Assets.skin()` | 后续（延续 Phase 4 Q4） | Q4 裁决 B |
| 设置 Dialog | Phase 7 | Q5 裁决（暂停菜单 MVP=继续/放弃） |
| 音效 / `Assets.sound()` | Phase 7 | Phase 4 差异声明 #4 延续 |
| 装备合成 CombineEquipment | 大版本扩展 | GDD §5.2 |
| 通知大窗过滤标签页 | 后续 | §8 WARNING-6 |
| 商店锁定 / 拖拽购买 | 待定清单 | GDD §3.4 / render §十一 |

---

## 2. 术语与约定（设计文档 ↔ 代码标识符）

| GDD/设计文档用语 | 代码标识符 | 位置 |
|------|------|------|
| 商店系统 | `systems/ShopSystem`（本期新建；`RunContext.shop`） | CP10 |
| 宝箱三选一 / 领取 | `systems/ChestSystem` + `entities/ChestOffer`/`ChestOption` + `PickChestCommand` | CP9 |
| 装备 / 背包 / 三槽 | `entities/Equipment` + `Player.inventory` + `Unit.equipped`（`EquipmentSlot` WEAPON/ARMOR/TRINKET） | CP5/CP6 |
| 装备稀有度 白/成/传 | `EquipmentRarity` WHITE/RARE/LEGENDARY | CP2 |
| 累计花费（卖出 100% 返还） | `Unit.spend`（买入累加、合并折叠） | CP5 |
| 3 合 1（同名同星 ×3 自动合成） | `ShopSystem.mergeCascade`（BuyUnit 的系统后果，非命令） | CP10 |
| 怜悯金币 / 连败计数 | `RunState.mercyLossCount`（:24 已建）+ `RunState.mercyGoldThisRound`（本期新增） | CP13 |
| 判负同轮重试 | `RunFlowSystem.continueAfterDefeat`（architecture §5.1"重试返回"） | CP15 |
| 新轮进入（免费刷新） | `RunFlowSystem.advanceAfterVictory`（胜局 PickChest 后） | CP15 |
| 放弃远征 | `AbandonRunCommand` → `RunFlowSystem.endRun(ABANDONED)` | CP8/CP15 |
| 终局成因（通关/放弃） | `entities/RunEndCause` COMPLETED/ABANDONED | CP13 |
| 熟练度结算 | `systems/MasteryCalculator`（纯函数接口 stub） | CP15 |
| 开局域边界事件 | `StartRunCommand(seed, sceneId, heroId)`（回放第 0 条记录） | CP8 |
| 装备待定态（两段式点击中态） | `render/ui/EquipPendingState` | CP23 |
| 弹窗宿主 / 模态阻断 | `render/ui/UIDialogManager`（dialogStage + 弹窗栈 + `isShowing()`） | CP26 |
| 全局按键（Escape/BACK/L） | `input/GlobalKeyProcessor` | CP26 |
| 通知面板（render §5.5） | `render/ui/NotificationLog`（纯逻辑）+ `NotificationPanel` | CP28 |
| 出售区（render §九 ⑦） | `BoardGeometry.SELL_ZONE_*` + `BoardInputProcessor` 拖拽终点 | CP17/CP19 |
| 真素材层（itch 小人接入） | `render/RealArt`（key → `art/units/{key}.png` 懒加载） | CP18 |
| Fusion Pixel 字体 | `assets/font/fusion_pixel_12.fnt`（`Assets.font()` 换载零改调用方） | CP18 |

---

## 3. 现状盘点（file:line 均为本次实读核对）

### 3.1 可直接复用（零改动）

| 资产 | 位置 | 说明 |
|------|------|------|
| 经济常量全套 | `core/.../config/GameBalance.java:49-70` | 起始金/刷新价/买经验价/宝箱公式/怜悯/商店槽位/人口与经验表——经济侧数值零新增负担 |
| 费阶概率锚点表 | `GameBalance.java:80-86,123-129` | `shopTierProbabilities(round)` 直接可用 |
| 确定性 RNG | `utils/RandomGenerator.java:38-62` | `weightedPick`（1 消耗/次）即商店/宝箱 roll 的唯一入口 |
| 发号器 | `entities/SequentialIdIssuer` + `RunState.getIdIssuer()`（RunState.java:35） | 装备实体直接共用（architecture §2.2 单一 id 空间） |
| 金币/经验账本 | `entities/Player.java:42-69` | `addGold`/`addExp`（连续升级 + Lv.7 封顶）已就位 |
| 修正源列表管线 | `systems/StatPipeline.java:34-52` + `entities/StatModifierSource.java` | Phase 3 Q4 预留插点：装备源追加即插，结算器零改动 |
| 修正块 | `entities/StatModifierBlock.java:35-56` | `of`/`plus` 不可变合并，装备 effects 直接复用 |
| 命令管理器 | `command/CommandManager.java:60-66,82-86` | `addListener`/`onExecuted` 通知位就位（Phase 4 预留，本期订阅） |
| 模态阻断位 | `input/BoardInputProcessor.java:53,69-71` | `BooleanSupplier modalBlocked` 构造注入（BattleScreen.java:129-134 现传常 false） |
| 点击挂点位 | `BoardInputProcessor.java:109` | 死区松手注释"Phase 5 详情面板挂点位"——本期接详情/装备待定 |
| 事件缓冲 | `render/EventInbox.java:33-41` | cursor 游标，通知面板作 CombatEvent 第二消费者 |
| 羁绊结算 | `systems/SynergySystem.resolve`（BattleSystem.java:69 调用先例） | SynergyPanel 预演零改复用 |
| 移动执行器 | `systems/MoveUnitExecutor` | 迁入 RosterSystem，本体不改 |
| 测试基建 | `systems/support/BattleTestFixtures.java` + 38 个测试文件 | 演示数据集构造先例 |

### 3.2 需改造

| 文件 | 位置 | 改动 |
|------|------|------|
| `config/GameBalance.java` | :49-56 经济段尾 | 增宝箱三选一/装备常量（CP1） |
| `config/JsonLoader.java` | :52-67 入口 | 增 equipments.json 解析（CP3） |
| `data/GameData.java` | :17-46 | 增 equipments 容器（CP3） |
| `entities/Unit.java` | 全文（50 行） | 增 spend/star 可变/equipped 三槽（CP5） |
| `entities/Player.java` | :20-24 字段、:124-133 undeploy | 增 inventory/getUnitById/removeUnit；undeploy 收口（CP6） |
| `entities/ActiveStatus.java` | :15-29 | 增 tickInterval（CP7） |
| `systems/StatusSystem.java` | :85-101 心跳 | 心跳间隔化 + apply 重载（CP7） |
| `entities/RunState.java` | :18-59 | 增 pendingChest/endCause/mercyGoldThisRound/logicTick/notices/runStarted（CP13） |
| `command/CommandManager.java` | :44-52,73-89 | 逻辑钟统一入 RunState + discardPending（CP13） |
| `command/RunContext.java` | :18-32 | 增 shop 字段（CP14） |
| `systems/RunFlowSystem.java` | 全文（154 行） | StartRun 化/判负重试/怜悯/宝箱流转/AbandonRun/删 DEMO_SEED+grantDemoRoster（CP15） |
| `systems/BattleSystem.java` | :60-103,235-243 | 装备修正源 + passive 落地（CP16） |
| `render/board/BoardGeometry.java` | :12-30 | 增 ③⑤⑦⑧⑨ 区常量（CP17） |
| `render/Assets.java` | 全文（48 行） | 双素材层（font + RealArt）（CP18） |
| `input/BoardInputProcessor.java` | :57-63,103-116 | 出售区 + 点击回调（CP19） |
| `render/board/BattleRenderer.java` | :162-195,320-349 | 出售区绘制（CP20） |
| `render/ui/TopBar.java` | 全文（36 行） | EXP + 暂停按钮（CP21） |
| `render/ui/ResultBanner.java` | :49-60,62-75 | 败局重试/胜局领箱文案分流 + 怜悯行（CP27） |
| `render/ui/RunEndPanel.java` | :41-51 | endCause/seed/熟练度文案（CP30） |
| `screens/BattleScreen.java` | :69-108,113-140,143-168,222-235 | 装配整合 + seed 边界（CP29） |
| `screens/MainMenuScreen.java` | :73-93 | START 点击生成 seed 传 BattleScreen（CP29） |

### 3.3 需新建

| 类别 | 文件 |
|------|------|
| 数据层 | `data/EquipmentSlot`、`data/EquipmentRarity`、`data/EquipmentEffect`、`data/EquipmentPassive`、`data/EquipmentData`（CP2） |
| 实体 | `entities/Equipment`、`entities/ChestOption`、`entities/ChestOffer`、`entities/RunEndCause`（CP5/CP9/CP13） |
| 命令 | `StartRunCommand`、`BuyUnitCommand`、`SellUnitCommand`、`RefreshShopCommand`、`BuyExpCommand`、`EquipItemCommand`、`UnequipItemCommand`、`PickChestCommand`、`AbandonRunCommand`（CP8） |
| 系统 | `systems/ChestSystem`、`systems/ShopSystem`、`systems/EquipmentSystem`、`systems/EquipmentStats`、`systems/RosterSystem`、`systems/MasteryCalculator`（CP9-CP12/CP15） |
| UI | `render/RealArt`、`render/ui/ShopBar`、`render/ui/InventoryPanel`、`render/ui/EquipPendingState`、`render/ui/SynergyPanel`、`render/ui/UnitDetailDialog`、`render/ui/UIDialogManager`、`render/ui/PauseMenuDialog`、`render/ui/ChestDialog`、`render/ui/NotificationLog`、`render/ui/NotificationPanel`、`render/ui/NotificationFormat`（CP18/CP22-CP28） |
| 输入 | `input/GlobalKeyProcessor`（CP26） |
| 资产 | `assets/data/equipments.json`、`assets/font/*`、`assets/art/units/*`（CP4/CP18） |

### 3.4 幽灵机制核对（第一轮结论复核）

- GDD §3.6"累计花费"字段：项目中不存在 → 本期以 `Unit.spend` 落地（实现口径 #4）。
- GDD §3.2 怜悯触发：`RunState.mercyLossCount` 字段已建（RunState.java:24）但无任何写入点 → CP15 落地。
- GDD §3.4 商店免费刷新 / 宝箱 roll / SellUnit / UnitRegistry：均不存在 → CP10/CP9/CP12 落地；UnitRegistry 继续推迟（§8 开放问题-2）。
- GDD §2.1"暂停菜单 AbandonRun"：无对应命令与 UI → CP8/CP26 落地。
- data_schema §八 equipments.json"结构锁定"但文件不存在 → CP4 按 §八 结构产种子内容。

---

## 4. 已确认决策（用户裁决，最终决定，不得改回）

| # | 问题 | 用户裁决（2026-08-22，逐条原文口径见 §9） | 决定 |
|---|------|------|------|
| Q1 | 装备系统做多少 | **选 A 全链实现** | equipments.json 种子 + 装备数据层（EquipmentData + JsonLoader 扩展）+ 装备实体（id 空间第二类实体、背包归属）+ EquipItem/UnequipItem 命令 + InventoryPanel（render §九③区）+ passiveStatus 进 StatusSystem + StartBattle 派生插装备修正源 + 宝箱装备选项 + 卖出自动卸下（GDD §3.6）。落地：CP2-CP7、CP9、CP11、CP16、CP23、CP26 |
| Q2 | 宝箱三选一规则 | **选 A 文档定最小可玩规则** | 由本文档按 GDD 精神拟定：槽1 常驻金币（GDD §3.2 公式）、槽2 经验书 +4（待调）、槽3 装备（稀有度权重 白70/成25/传5；Boss 箱必含 ≥1 成装及以上，权重 0/80/20）；权重落 GameBalance 常量（理由见实现口径 #2）；装备池 = equipments.json 全集。落地：CP1/CP9/CP27 |
| Q3 | 英雄选择界面 | **选 B 整体推 Phase 6** | 不做 RunSetupScreen/英雄选择；维持 MainMenu→BattleScreen 流转；本期仅去 DEMO_SEED 硬编码、seed 改由 UI 域边界事件给定（兑现 StartRun 命令化，heroId 参数留 Phase 6 扩展位）；"英雄选择界面"里程碑字样推迟记差异声明。落地：CP8/CP15/CP29；差异声明 #1 |
| Q4 | 真素材 | **选 B 字体 + itch 小人** | Fusion Pixel 字体（.fnt/.png 经 Hiero 生成、OFL LICENSE 入库，`Assets.font()` 换载零改调用方）+ itch 免费小人替换 2~3 个棋子验证精灵动画流水线（含 LICENSE/来源记录）；Kenney CC0 UI 包继续占位自绘、推后记差异声明；测试不得依赖素材存在（守卫方式沿用 Phase 4：`exists()` 回退）。落地：CP18；差异声明 #5 |
| Q5 | 终局边界 | **选 A 三屏+存档全推 Phase 6** | RunResultScreen/CodexScreen/存档不做；本期做暂停菜单（Escape/BACK、GlobalKeyProcessor、dialogStage）+ AbandonRun 命令 → RUN_END（扩展现 RunEndPanel 放弃文案）；熟练度结算留纯函数接口 stub 供 Phase 6 接档案域；暂停菜单 MVP = 继续/放弃（设置 Dialog 推 Phase 7）。落地：CP8/CP13/CP15/CP26/CP30；差异声明 #2 |
| Q6 | 演示名单 | **选 A 移除，严格 GDD** | 删除 RunFlowSystem.grantDemoRoster（RunFlowSystem.java:140-153），起始 10 金商店自购；RunFlowSystemTest 等相关断言改口径；units.json 可购池单薄（3 模板）致首轮体验单薄与商店同质化记 WARNING，units.json 铺量列为本期内容任务（是否铺、铺多少由本文档给建议方案 CP31，标注内容性任务不影响架构）。落地：CP10/CP15/CP31；WARNING-1 |

---

## 5. 总体技术方案

### 5.1 架构与数据流

分层与 Phase 4 一致（data/entities → systems → command → render/input → screens）；本期的结构性变化：

1. **RunContext 增 `shop` 字段**（CP14）：经济态（商店槽位）随上下文生命周期重建；UnitRegistry 继续推迟（§8 开放问题-2）。
2. **handler 按所属 system 拆分**（input §6.1"Phase 5 拆分"兑现）：`RunFlowSystem`（StartRun/StartBattle/Surrender/PickChest/AbandonRun）+ `ShopSystem`（BuyUnit/RefreshShop/BuyExp）+ `RosterSystem`（MoveUnit/SellUnit）+ `EquipmentSystem`（EquipItem/UnequipItem），BattleScreen.show() 逐一注册。
3. **逻辑钟唯一归属 RunState**（CP13）：命令 tick 戳改为消费时盖 `RunState.getLogicTick()`，Phase 4 口径 #11"统一入 RunState"销账。
4. **dialogStage 第四层输入**（CP26）：dialogStage > uiStage > boardProcessor > keyProcessor；模态 = `UIDialogManager.isShowing()`（同时供 `modalBlocked` 与模拟冻结）。
5. **装备进战斗的两条通道**（CP16）：stat 通道走 `EquipmentStats implements StatModifierSource`（进第一级基准派生）；passiveStatus 通道走 `StatusSystem.apply` 重载（`tickInterval` 承载龙心类"每 5 秒回 2%"）。

图表（落盘 `docs/diagrams/`，本文档只放引用）：

| 图 | 覆盖 | 文件 |
|----|------|------|
| 商店/经济/宝箱数据流 | 收入/支出/商店槽位/宝箱 roll/RNG 消耗口径 | `docs/diagrams/phase5_economy_dataflow.md`（+ .html） |
| RESULT 期与 PickChest/判负重试状态流转 | 1C-R 重试/胜局领箱/怜悯/AbandonRun/RUN_END | `docs/diagrams/phase5_result_retry_flow.md`（+ .html） |
| 装备实体与命令链路 | 数据层→实体→穿脱→开战派生→宝箱/卖出闭环 | `docs/diagrams/phase5_equipment_chain.md`（+ .html） |
| 暂停菜单/dialogStage 模态阻断链 | 四层输入/弹窗栈/冻结/Escape 例外 | `docs/diagrams/phase5_dialog_modal_chain.md`（+ .html） |

### 5.2 与设计文档的差异声明（如实记录，不改设计文档）

| # | 设计文档条款 | 本期实现 | 依据 |
|---|------|------|------|
| 1 | GDD §2.1 开局"从已解锁英雄中选择一位" | RunSetupScreen/英雄选择推 Phase 6；`StartRunCommand.heroId` 恒 null（扩展位） | Q3 裁决 B |
| 2 | GDD §2.1/§8.1 按波数结算熟练度入档案 | `MasteryCalculator` 纯函数 stub + `RunState.masteryAwarded` 暂存，Phase 6 接档案域 | Q5 裁决 A |
| 3 | GDD §5.2 战歌号角"全体友军能量获取 +15%" | 实现为穿戴者自身 `energyGainRate +15%`（data_schema §八 effects 无 target 字段，光环装备待 schema 扩展） | schema 结构锁定约束 |
| 4 | render §九 ⑨ 区 (20,230,128,60) | 改 (20,244,128,46)——原值与 ③ 区（y 140~240）重叠 10px，超出 ±4px 微调容差 | 布局冲突消解 |
| 5 | render §7.6 `skin()` / Kenney UI 包 | 自绘 Actor 占位延续（延续 Phase 4 差异声明 #4） | Q4 裁决 B |
| 6 | render §5.5 通知大窗"过滤标签页" | 不做；L 键大小窗切换有（大窗最近 200 行无过滤） | §8 WARNING-6 |
| 7 | architecture §4.1"11 命令定稿" | +StartRun = 12：以命令形式入队作"回放流第 0 条记录"（Q3 裁决"StartRun 命令化"字面落地） | Q3 裁决 |
| 8 | render §九 ③⑤"全程（战斗期置灰）" | 置灰以 draw alpha 0.35 实现（不隐藏） | 视觉降级最小实现 |
| 9 | architecture §六 RNG 消耗点四处 | 第 2/3 点（商店刷新、宝箱 roll）本期落地，无新增类目；固定消耗量口径见 §5.4（文档内声明，不改 architecture_design.md） | 本文档 |

### 5.3 实现层口径（文档未明说、本次定的执行细节）

| # | 口径 | 理由/出处 |
|---|------|------|
| 1 | 宝箱槽序固定：槽1 金币（`chestGold(round,boss)`，GameBalance.java:115-120 既有）、槽2 经验书 `CHEST_EXP_BOOK_GAIN=4`（待调，对齐 4 金=4 经验的购买价比）、槽3 装备；胜局 roll 恰好 2 RNG（稀有度 1 + 池内抽取 1），金币/经验选项零 RNG；败局 0 | Q2 裁决 A 授权拟定；architecture §4.1"进 RESULT 时已 roll 好" |
| 2 | 掉落权重落 `GameBalance`（`CHEST_RARITY_WEIGHTS`/`BOSS_CHEST_RARITY_WEIGHTS`）：与费阶锚点/宝箱公式同属"待调数值单点"（data_schema §十），改表不改调用方 | Q2 裁决建议采纳 |
| 3 | Boss 箱装备槽权重 {0,80,20}：白位 0 兑现"必含 ≥1 成装及以上"（唯一装备槽即该件），传说 20% 兑现"传说概率大幅提升"（普通箱 5%）；全为待调 | GDD §5.2 骨架 |
| 4 | 商店 `reroll` 固定 10 RNG（2/槽 × 5）：费阶 roll 1 + 同费池均匀抽取 1；**池为空也照常消耗**（确定性：消耗序与内容无关）；概率 float × `PROBABILITY_WEIGHT_SCALE`(1000) 转 int 权重 | weightedPick(int[]) 入口约束 |
| 5 | 3 合 1：合成产物保留**首位参与者**的位置与装备；被吞并者装备自动卸下回背包、spend 折叠加总进产物；级联直到无可合（2 星 ×3 → 3 星）。首位序 = 备战席入席序优先、其次部署扫描序 y↑x↑ | GDD §4.3 未定落位；确定性序 |
| 6 | `EquipItem` 槽位被占 → 拒绝（UI 提示先卸下）；卖出/合成参与者自动卸下 | B2 哲学下最简确定语义 |
| 7 | `passiveStatus.type` 本期仅允许 REGEN（加载期 fail-fast）；`ActiveStatus.tickInterval` 缺省 1s，技能/羁绊零感知 | StatusSystem 唯一有 tick 语义的类型 |
| 8 | 判负 = ENEMY_WIN / TIMEOUT / Surrender（超时按 GDD §2.2 属失败）；零棋子判定 = 战斗实例玩家侧单位总数 == 0（含已清扫亡者——BattleState.getUnits 不移除） | 防刷规则 GDD §3.2 |
| 9 | RESULT 胜局无自动推进（必须 PickChest）；败局保留 3s 自动 + 点击继续；第 25 轮胜局仍先领箱再 RUN_END | architecture §4.4 回放流终点 |
| 10 | 怜悯在败局结算（continueAfterDefeat）发放：上场数>0 → `mercyLossCount+1`；`≥ MERCY_START_LOSS(3)` 且本轮已发 `< MERCY_CAP_PER_ROUND(3)` → +1 金；新轮进入双清零 | architecture §5.1"关键区分" |
| 11 | StartRun handler 一致性校验：round==1 且未 started 且 phase==SHOPPING 且 seed/sceneId 与上下文一致，否则静默 false | 域边界事件防装配点错位 |
| 12 | RESTART = UI 边界新 seed（`System.nanoTime()`）；`CommandManager.discardPending()` 清残留队列防跨局泄漏 | Q3 裁决 seed 口径 |
| 13 | 通知三流：`onExecuted`（命令行，`NotificationFormat` 纯函数）+ CombatEvent（仅 UNIT_DIED/CAST，HIT/HEALED 过噪跳过）+ `RunState.notices`（系统反应行：免费刷新/怜悯/宝箱/合成/买卖，有界 32）；单帧追加 ≤2 行超出丢弃 | render §5.5 双流的最小扩展 |
| 14 | 模拟冻结 = `paused || UIDialogManager.isShowing()`（沿 Screen.pause 既有分支模式；模态期间 RESULT 败局计时同冻结，关窗恢复） | architecture §4.2 暂停=表演层 |
| 15 | 熟练度 stub：`MasteryCalculator.GDD_BASIC = 轮数 × 3`（GDD §8.1"每已达 1 轮 +3"，放弃同口径） | Q5 裁决 |
| 16 | 背包无上限；InventoryPanel 显示前 6 件 + 总数角标（③ 区 3×2） | GDD/render 未定上限（WARNING-7） |
| 17 | 逻辑钟统一：命令 tick 戳从"入队时盖（管理器私有钟）"改为"消费时盖（RunState 唯一钟）"；语义 = 执行 tick（enqueue 处无 ctx，回放按执行序重演等价） | Phase 4 口径 #11 |
| 18 | units.json 铺量为内容性任务（CP31，建议 +6 模板至 9 可购，全部复用既有技能），不阻塞架构、可独立裁掉 | Q6 裁决授权 |

### 5.4 RNG 消耗点口径（architecture §六清单落地声明，不改其文档）

| 消耗点 | 每次消耗 | 触发 | 状态 |
|--------|---------|------|------|
| 敌阵生成 | = 杂兵数 | beginRound（StartRun / 新轮进入） | Phase 2 既有 |
| 商店刷新 | **固定 10** | StartRun、新轮进入（免费）、RefreshShop（2 金） | 本期落地（#4 口径） |
| 宝箱 roll | **固定 2（胜局）**；败局 0 | 胜局进 RESULT 一次性 | 本期落地（#1 口径） |
| 暴击判定 | 1/次普攻 | 战斗内固定行动序 | Phase 3 既有 |

> 装备掉落 RNG 属宝箱 roll 内部（稀有度 + 池内抽取），不单列。同 seed 同命令流的整局确定性与 25 轮对照测试沿用 Phase 4 验收口径（断言 `getConsumedCount()` 精确值）。

---

## 6. 改动点清单（评审主入口）

全部改动逐一列出，编号全局唯一（CP1~CP31），按依赖顺序排列。**去重规则**：同一段代码的完整改动只在一个 CP 出现，其余改动点一律章节引用。

---

### CP1. GameBalance 增补宝箱三选一与装备常量

- **类型**：修改类（增常量）
- **位置**：`core/src/main/java/com/voidvvv/kz_auto_chess_n/config/GameBalance.java:56`（`MERCY_CAP_PER_ROUND` 之后插入；锚点：经济段尾）
- **改动说明**：Q2 裁决 A 授权拟定的宝箱最小可玩规则数值 + 权重落点（实现口径 #1/#2/#3）。全部标注待调，与既有经济常量同住一处（data_schema §十"改这里不改调用方"）。
- **代码**（修改前 → 修改后；修改前逐字摘自 GameBalance.java:49-56）：

```java
修改前：
    // —— 经济 ——
    public static final int START_GOLD = 10;
    public static final int SHOP_REFRESH_COST = 2;
    public static final int BUY_EXP_COST = 4;
    public static final int BUY_EXP_GAIN = 4;
    public static final int CHEST_GOLD_CAP = 10;
    public static final int MERCY_START_LOSS = 3;
    public static final int MERCY_CAP_PER_ROUND = 3;
```

```java
修改后：
    // —— 经济 ——
    public static final int START_GOLD = 10;
    public static final int SHOP_REFRESH_COST = 2;
    public static final int BUY_EXP_COST = 4;
    public static final int BUY_EXP_GAIN = 4;
    public static final int CHEST_GOLD_CAP = 10;
    public static final int MERCY_START_LOSS = 3;
    public static final int MERCY_CAP_PER_ROUND = 3;

    // —— 宝箱三选一（Q2 裁决 A：最小可玩规则，数值待调）——
    /** 槽2 经验书固定经验值（对齐"4 金 = 4 经验"购买价比，待调） */
    public static final int CHEST_EXP_BOOK_GAIN = 4;
    /** 普通箱装备槽稀有度权重 [白, 成, 传]（GDD §5.2：70/25/5，待调） */
    public static final int[] CHEST_RARITY_WEIGHTS = {70, 25, 5};
    /** Boss 箱装备槽稀有度权重 [白, 成, 传]——白位 0 = 必含 ≥1 成装及以上；传说 20% = 大幅提升（待调） */
    public static final int[] BOSS_CHEST_RARITY_WEIGHTS = {0, 80, 20};
    /** 费阶概率 float → weightedPick int 权重的放大刻度（锚点概率和恒 100 → 权重和恒 100000） */
    public static final int PROBABILITY_WEIGHT_SCALE = 1000;

    // —— 装备（GDD §5.2 B2）——
    /** 每棋子装备槽数：武器 + 盔甲 + 饰品各一 */
    public static final int EQUIP_SLOTS_PER_UNIT = 3;
```

- **测试要点**：`GameBalanceTest` 增断言——三组权重数组长度 3、普通箱权重和 100、Boss 箱权重和 100 且白位为 0；`CHEST_EXP_BOOK_GAIN > 0`。

---

### CP2. 装备数据层五类（EquipmentSlot/Rarity/Effect/Passive/EquipmentData）

- **类型**：新建文件 ×5
- **位置**：`core/src/main/java/com/voidvvv/kz_auto_chess_n/data/` 下
- **改动说明**：data_schema §八"结构锁定"的代码侧落地。`EquipmentSlot`/`EquipmentRarity` 实现 `Vocab`（JSON 词表三处共用先例，StatKey.java:14 同款）；effects 与 synergies 同一套 `{stat, op, value}` 词汇；`passiveStatus.tick`（秒）映射 `tickInterval`。GDD 用语"槽位（武器/盔甲/饰品）↔ WEAPON/ARMOR/TRINKET"、"稀有度（白/成/传）↔ WHITE/RARE/LEGENDARY"。
- **代码**（新建，五文件合一展示）：

```java
// data/EquipmentSlot.java
package com.voidvvv.kz_auto_chess_n.data;

/** 装备槽位词表（GDD §5.2 三槽；JSON "slot" 字段） */
public enum EquipmentSlot implements Vocab {
    WEAPON("WEAPON"), ARMOR("ARMOR"), TRINKET("TRINKET");

    private final String jsonName;

    EquipmentSlot(String jsonName) { this.jsonName = jsonName; }

    @Override
    public String jsonName() { return jsonName; }
}

// data/EquipmentRarity.java
package com.voidvvv.kz_auto_chess_n.data;

/** 装备稀有度词表（GDD §5.2 白/成/传；JSON "rarity" 字段；宝箱权重数组序 = values() 序） */
public enum EquipmentRarity implements Vocab {
    WHITE("WHITE"), RARE("RARE"), LEGENDARY("LEGENDARY");

    private final String jsonName;

    EquipmentRarity(String jsonName) { this.jsonName = jsonName; }

    @Override
    public String jsonName() { return jsonName; }
}

// data/EquipmentEffect.java
package com.voidvvv.kz_auto_chess_n.data;

import java.util.Objects;

/** 装备属性修正条目（与 synergies 同一套 {stat, op, value} 词汇，开战进基准快照） */
public final class EquipmentEffect {
    private final StatKey stat;
    private final EffectOp op;
    private final float value;

    public EquipmentEffect(StatKey stat, EffectOp op, float value) {
        this.stat = Objects.requireNonNull(stat, "stat 不能为 null");
        this.op = Objects.requireNonNull(op, "op 不能为 null");
        this.value = value;
    }

    public StatKey getStat() { return stat; }
    public EffectOp getOp() { return op; }
    public float getValue() { return value; }
}

// data/EquipmentPassive.java
package com.voidvvv.kz_auto_chess_n.data;

import java.util.Objects;

/** 穿着期常驻状态（data_schema §八 passiveStatus：type/power/tick 秒）——装备入口进 StatusSystem 的第二种形态 */
public final class EquipmentPassive {
    private final StatusType type;
    private final float power;
    private final float tickInterval;

    public EquipmentPassive(StatusType type, float power, float tickInterval) {
        this.type = Objects.requireNonNull(type, "type 不能为 null");
        this.power = power;
        this.tickInterval = tickInterval;
    }

    public StatusType getType() { return type; }
    /** REGEN 语义：maxHp 比例/跳（battle §7.2） */
    public float getPower() { return power; }
    /** 心跳间隔（秒）；技能/羁绊缺省 1s */
    public float getTickInterval() { return tickInterval; }
}

// data/EquipmentData.java
package com.voidvvv.kz_auto_chess_n.data;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 装备模板（不可变；加载一次终身只读，沿 UnitData 先例） */
public final class EquipmentData {
    private final String id;
    private final String name;
    private final EquipmentSlot slot;
    private final EquipmentRarity rarity;
    private final List<EquipmentEffect> effects;
    private final EquipmentPassive passive; // 可 null

    public EquipmentData(String id, String name, EquipmentSlot slot, EquipmentRarity rarity,
                         List<EquipmentEffect> effects, EquipmentPassive passive) {
        this.id = Objects.requireNonNull(id, "id 不能为 null");
        this.name = Objects.requireNonNull(name, "name 不能为 null");
        this.slot = Objects.requireNonNull(slot, "slot 不能为 null");
        this.rarity = Objects.requireNonNull(rarity, "rarity 不能为 null");
        this.effects = Collections.unmodifiableList(
                new java.util.ArrayList<EquipmentEffect>(Objects.requireNonNull(effects, "effects 不能为 null")));
        this.passive = passive;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public EquipmentSlot getSlot() { return slot; }
    public EquipmentRarity getRarity() { return rarity; }
    public List<EquipmentEffect> getEffects() { return effects; }
    /** 可 null：无被动 */
    public EquipmentPassive getPassive() { return passive; }
}
```

- **测试要点**：`data/EquipmentDataTest`——构造只读性（effects 视图不可变）、passive 可 null、词表 `jsonName()` 往返。

---

### CP3. JsonLoader/GameData 装备解析与交叉校验

- **类型**：修改类 ×2
- **位置**：`core/.../config/JsonLoader.java:52-67`（入口与 load）＋`core/.../data/GameData.java:17-46`（容器）
- **改动说明**：`loadFromDirectory` 增读 `equipments.json`（生产路径必存在，缺文件即启动死）；`load` 增带装备文件的重载，旧 4 参签名保留委托（空装备表——存量测试零改动）。解析沿既有手工映射 + fail-fast 风格；效果条数 ≤ `MAX_EFFECTS_PER_SKILL`（data_schema §九.3）；passiveStatus 仅 REGEN（实现口径 #7）。交叉校验增软告警：equipments 非空但某稀有度池为空（宝箱 roll 内容缺失预警）。
- **代码**（修改前逐字摘自 JsonLoader.java:52-67）：

```java
修改前：
    /** 从目录按标准文件名加载：units.json / skills.json / synergies.json / scenes.json */
    public static GameData loadFromDirectory(FileHandle dataDir) {
        return load(dataDir.child("units.json"), dataDir.child("skills.json"),
                dataDir.child("synergies.json"), dataDir.child("scenes.json"));
    }

    public static GameData load(FileHandle unitsFile, FileHandle skillsFile,
                                FileHandle synergiesFile, FileHandle scenesFile) {
        Map<String, UnitData> units = parseUnits(unitsFile);
        Map<String, SkillData> skills = parseSkills(skillsFile);
        Map<String, SynergyData> synergies = parseSynergies(synergiesFile);
        Map<String, SceneData> scenes = parseScenes(scenesFile);
        List<String> warnings = new ArrayList<String>();
        crossValidate(units, skills, synergies, scenes, warnings);
        return new GameData(units, skills, synergies, scenes, warnings);
    }
```

```java
修改后：
    /** 从目录按标准文件名加载：units.json / skills.json / synergies.json / scenes.json / equipments.json */
    public static GameData loadFromDirectory(FileHandle dataDir) {
        return load(dataDir.child("units.json"), dataDir.child("skills.json"),
                dataDir.child("synergies.json"), dataDir.child("scenes.json"),
                dataDir.child("equipments.json"));
    }

    /** 兼容重载：无装备文件（存量测试路径）——装备表为空 */
    public static GameData load(FileHandle unitsFile, FileHandle skillsFile,
                                FileHandle synergiesFile, FileHandle scenesFile) {
        return load(unitsFile, skillsFile, synergiesFile, scenesFile, null);
    }

    public static GameData load(FileHandle unitsFile, FileHandle skillsFile,
                                FileHandle synergiesFile, FileHandle scenesFile,
                                FileHandle equipmentsFile) {
        Map<String, UnitData> units = parseUnits(unitsFile);
        Map<String, SkillData> skills = parseSkills(skillsFile);
        Map<String, SynergyData> synergies = parseSynergies(synergiesFile);
        Map<String, SceneData> scenes = parseScenes(scenesFile);
        Map<String, EquipmentData> equipments = parseEquipments(equipmentsFile);
        List<String> warnings = new ArrayList<String>();
        crossValidate(units, skills, synergies, scenes, warnings);
        warnEmptyRarityPools(equipments, warnings);
        return new GameData(units, skills, synergies, scenes, equipments, warnings);
    }
```

（GameData 修改前逐字摘自 GameData.java:17-32）：

```java
修改前：
public final class GameData {
    private final Map<String, UnitData> units;
    private final Map<String, SkillData> skills;
    private final Map<String, SynergyData> synergies;
    private final Map<String, SceneData> scenes;
    private final List<String> warnings;

    public GameData(Map<String, UnitData> units, Map<String, SkillData> skills,
                    Map<String, SynergyData> synergies, Map<String, SceneData> scenes,
                    List<String> warnings) {
        this.units = Collections.unmodifiableMap(new LinkedHashMap<String, UnitData>(units));
        this.skills = Collections.unmodifiableMap(new LinkedHashMap<String, SkillData>(skills));
        this.synergies = Collections.unmodifiableMap(new LinkedHashMap<String, SynergyData>(synergies));
        this.scenes = Collections.unmodifiableMap(new LinkedHashMap<String, SceneData>(scenes));
        this.warnings = Collections.unmodifiableList(warnings);
    }
```

```java
修改后：
public final class GameData {
    private final Map<String, UnitData> units;
    private final Map<String, SkillData> skills;
    private final Map<String, SynergyData> synergies;
    private final Map<String, SceneData> scenes;
    private final Map<String, EquipmentData> equipments;
    private final List<String> warnings;

    /** 兼容重载：无装备表（存量测试构造先例） */
    public GameData(Map<String, UnitData> units, Map<String, SkillData> skills,
                    Map<String, SynergyData> synergies, Map<String, SceneData> scenes,
                    List<String> warnings) {
        this(units, skills, synergies, scenes, new LinkedHashMap<String, EquipmentData>(), warnings);
    }

    public GameData(Map<String, UnitData> units, Map<String, SkillData> skills,
                    Map<String, SynergyData> synergies, Map<String, SceneData> scenes,
                    Map<String, EquipmentData> equipments, List<String> warnings) {
        this.units = Collections.unmodifiableMap(new LinkedHashMap<String, UnitData>(units));
        this.skills = Collections.unmodifiableMap(new LinkedHashMap<String, SkillData>(skills));
        this.synergies = Collections.unmodifiableMap(new LinkedHashMap<String, SynergyData>(synergies));
        this.scenes = Collections.unmodifiableMap(new LinkedHashMap<String, SceneData>(scenes));
        this.equipments = Collections.unmodifiableMap(new LinkedHashMap<String, EquipmentData>(equipments));
        this.warnings = Collections.unmodifiableList(warnings);
    }

    public EquipmentData getEquipment(String id) { return equipments.get(id); }
    public Map<String, EquipmentData> getEquipments() { return equipments; }
```

（JsonLoader 新增私有解析段，插于 parseScenes 之后）：

```java
    // ==================================================================
    // equipments.json（data_schema §八 结构锁定版；Phase 5）
    // ==================================================================

    /** equipmentsFile 可 null（兼容重载）：null 或不存在 → 空表；loadFromDirectory 路径缺文件即死 */
    private static Map<String, EquipmentData> parseEquipments(FileHandle file) {
        Map<String, EquipmentData> result = new LinkedHashMap<String, EquipmentData>();
        if (file == null || !file.exists()) {
            return result;
        }
        Set<String> ids = new HashSet<String>();
        for (JsonValue e = parseArray(file).child; e != null; e = e.next) {
            requireObject(e, file.name());
            String id = requireString(e, "id", file.name() + "#?");
            String w = file.name() + "#" + id + "/";
            if (!ids.add(id)) {
                fail(w, "id 全文件唯一，重复声明");
            }
            checkUnknownKeys(e, w, "id", "name", "slot", "rarity", "effects", "passiveStatus");

            String name = requireString(e, "name", w);
            EquipmentSlot slot = requireVocab(e, "slot", EquipmentSlot.class, w);
            EquipmentRarity rarity = requireVocab(e, "rarity", EquipmentRarity.class, w);

            JsonValue effectsNode = require(e, "effects", w);
            if (!effectsNode.isArray() || effectsNode.size < 1) {
                fail(w + "effects", "必须为非空数组（1~" + GameBalance.MAX_EFFECTS_PER_SKILL + " 条）");
            }
            if (effectsNode.size > GameBalance.MAX_EFFECTS_PER_SKILL) {
                fail(w + "effects", "每装备效果至多 " + GameBalance.MAX_EFFECTS_PER_SKILL + " 条，实际=" + effectsNode.size);
            }
            List<EquipmentEffect> effects = new ArrayList<EquipmentEffect>(effectsNode.size);
            for (JsonValue fe = effectsNode.child; fe != null; fe = fe.next) {
                effects.add(parseEquipmentEffect(fe, w + "effects[" + effects.size() + "]/"));
            }
            EquipmentPassive passive = null;
            JsonValue passiveNode = e.get("passiveStatus");
            if (passiveNode != null && !passiveNode.isNull()) {
                passive = parseEquipmentPassive(passiveNode, w + "passiveStatus/");
            }
            result.put(id, new EquipmentData(id, name, slot, rarity, effects, passive));
        }
        return result;
    }

    private static EquipmentEffect parseEquipmentEffect(JsonValue fe, String w) {
        requireObject(fe, w);
        checkUnknownKeys(fe, w, "stat", "op", "value");
        StatKey stat = requireVocab(fe, "stat", StatKey.class, w);
        EffectOp op = requireVocab(fe, "op", EffectOp.class, w);
        float value = requireFloat(fe, "value", w);
        return new EquipmentEffect(stat, op, value);
    }

    private static EquipmentPassive parseEquipmentPassive(JsonValue node, String w) {
        requireObject(node, w);
        checkUnknownKeys(node, w, "type", "power", "tick");
        StatusType type = requireVocab(node, "type", StatusType.class, w);
        if (type != StatusType.REGEN) {
            fail(w + "type", "passiveStatus 本期仅支持 REGEN，遇到: " + type.jsonName());
        }
        float power = requireFloat(node, "power", w);
        if (power <= 0) {
            fail(w + "power", "必须 > 0，实际=" + power);
        }
        float tick = requireFloat(node, "tick", w);
        if (tick <= 0) {
            fail(w + "tick", "必须 > 0（秒），实际=" + tick);
        }
        return new EquipmentPassive(type, power, tick);
    }

    /** 软告警：equipments 非空但某稀有度池为空（宝箱 roll 将降级——内容缺失预警，不阻断） */
    private static void warnEmptyRarityPools(Map<String, EquipmentData> equipments, List<String> warnings) {
        if (equipments.isEmpty()) {
            return;
        }
        for (EquipmentRarity rarity : EquipmentRarity.values()) {
            boolean any = false;
            for (EquipmentData equipment : equipments.values()) {
                if (equipment.getRarity() == rarity) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                warnings.add("装备稀有度池为空（宝箱该档将降级/退化金币）: " + rarity.jsonName());
            }
        }
    }
```

（JsonLoader import 段增：`com.voidvvv.kz_auto_chess_n.data.EquipmentData`、`EquipmentEffect`、`EquipmentPassive`、`EquipmentRarity`、`EquipmentSlot`。）

- **测试要点**：`config/JsonLoaderEquipmentsTest`——正常解析（字段/词表/条数）；未知字段报错；passiveStatus 非 REGEN 报错；power/tick ≤0 报错；id 重复报错；equipmentsFile=null → 空表；稀有度空池软告警进 `getWarnings()`；`loadFromDirectory` 缺 equipments.json 抛 `DataValidationException`（沿 JsonLoaderValidationTest 的临时文件先例）。

---

### CP4. equipments.json 种子内容（8 件）

- **类型**：新建资产文件
- **位置**：`assets/data/equipments.json`
- **改动说明**：按 data_schema §八结构与 GDD §5.2 示例集扩充：GDD 最小可验收集 4 件 + 补满三稀有度池（白 4 / 成 3 / 传 1）。全部数值待调（GDD §5.2"数值待调"）。战歌号角按差异声明 #3 落自身加成。
- **代码**（完整文件）：

```json
[
  { "id": "eq_iron_sword", "name": "铁剑", "slot": "WEAPON", "rarity": "WHITE",
    "effects": [ { "stat": "attack", "op": "PCT", "value": 20 } ] },
  { "id": "eq_bronze_blade", "name": "青铜短刃", "slot": "WEAPON", "rarity": "WHITE",
    "effects": [ { "stat": "attackSpeed", "op": "PCT", "value": 10 } ] },
  { "id": "eq_mithril_armor", "name": "秘银胸甲", "slot": "ARMOR", "rarity": "WHITE",
    "effects": [ { "stat": "armor", "op": "ADD", "value": 20 } ] },
  { "id": "eq_vampire_fang", "name": "吸血獠牙", "slot": "TRINKET", "rarity": "WHITE",
    "effects": [ { "stat": "lifesteal", "op": "ADD", "value": 10 } ] },
  { "id": "eq_war_horn", "name": "战歌号角", "slot": "TRINKET", "rarity": "RARE",
    "effects": [ { "stat": "energyGainRate", "op": "PCT", "value": 15 } ] },
  { "id": "eq_hunting_bow", "name": "猎风长弓", "slot": "WEAPON", "rarity": "RARE",
    "effects": [ { "stat": "attack", "op": "PCT", "value": 35 } ] },
  { "id": "eq_plate_armor", "name": "玄铁板甲", "slot": "ARMOR", "rarity": "RARE",
    "effects": [ { "stat": "hp", "op": "ADD", "value": 200 } ] },
  { "id": "eq_dragon_heart", "name": "龙心", "slot": "ARMOR", "rarity": "LEGENDARY",
    "effects": [ { "stat": "hp", "op": "ADD", "value": 400 } ],
    "passiveStatus": { "type": "REGEN", "power": 0.02, "tick": 5 } }
]
```

- **测试要点**：由 CP3 测试以真实 assets 文件断言（`Gdx.files.local` 走 lwjgl3 运行时；单测侧用内联 JSON 字符串等价覆盖）；`Main.create` 启动零告警（稀有度池全非空）。

---

### CP5. entities/Equipment 新建 + Unit 扩展（spend/升星/装备三槽）

- **类型**：新建文件 + 修改类
- **位置**：新建 `core/.../entities/Equipment.java`；修改 `core/.../entities/Unit.java`（全文重写，现 50 行）
- **改动说明**：装备实体 = id 空间第二类实体（单一 int 空间与棋子共用，architecture §2.2，Q1 裁决"背包归属"）。`Unit` 从"完全不可变"转为**受控可变**（沿 `BattleUnit` 的 framework-internal 纪律，类注释改口径）：`star`/`spend` 变可变（3 合 1 与买入后果），`equipped` 三槽列表（槽位唯一）。旧 3 参构造保留（存量测试零改动），新增 4 参（带 spend）。equals 仍按 id——升星不变身份。
- **代码**（新建 `entities/Equipment.java`）：

```java
package com.voidvvv.kz_auto_chess_n.entities;

import com.voidvvv.kz_auto_chess_n.data.EquipmentData;

import java.util.Objects;

/**
 * 装备实体（architecture §2.2 单一 int id 空间的第二类实体；Q1 裁决 A）：
 * id 由 IdIssuer 发号（与棋子共用）、模板直接引用。完全不可变。
 */
public final class Equipment {
    private final int id;
    private final EquipmentData template;

    public Equipment(int id, EquipmentData template) {
        this.id = id;
        this.template = Objects.requireNonNull(template, "template 不能为 null");
    }

    public int getId() { return id; }
    public EquipmentData getTemplate() { return template; }

    /** id 空间全局唯一，身份即 id */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Equipment)) {
            return false;
        }
        return id == ((Equipment) o).id;
    }

    @Override
    public int hashCode() {
        return 31 * 17 + id;
    }
}
```

（`entities/Unit.java` 修改前逐字摘自 Unit.java:15-50——全文；修改后为全文重写）：

```java
修改前：
public final class Unit {
    private final int id;
    private final UnitData template;
    /** 1~3（3 合 1 上限 3 星），构造校验 */
    private final int star;

    public Unit(int id, UnitData template, int star) {
        if (star < 1 || star > 3) {
            throw new IllegalArgumentException("星级必须在 1~3（3 合 1 上限 3 星），实际=" + star);
        }
        this.id = id;
        this.template = Objects.requireNonNull(template, "template 不能为 null");
        this.star = star;
    }

    public int getId() { return id; }
    public UnitData getTemplate() { return template; }
    public int getStar() { return star; }

    /** id 空间全局唯一，身份即 id（模板与星级不参与判等） */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Unit)) {
            return false;
        }
        return id == ((Unit) o).id;
    }

    @Override
    public int hashCode() {
        return 31 * 17 + id;
    }
}
```

```java
修改后：
public final class Unit {
    private final int id;
    private final UnitData template;
    /** 1~3（3 合 1 上限 3 星），构造校验；3 合 1 升星经 framework-internal upgradeStar */
    private int star;
    /** 累计花费（GDD §3.6 卖出 100% 返还；买入累加、合并折叠） */
    private int spend;
    /** 已穿装备 ≤ EQUIP_SLOTS_PER_UNIT(3)，槽位唯一（武器/盔甲/饰品各一） */
    private final List<Equipment> equipped = new ArrayList<Equipment>();

    public Unit(int id, UnitData template, int star) {
        this(id, template, star, 0);
    }

    public Unit(int id, UnitData template, int star, int spend) {
        if (star < 1 || star > 3) {
            throw new IllegalArgumentException("星级必须在 1~3（3 合 1 上限 3 星），实际=" + star);
        }
        if (spend < 0) {
            throw new IllegalArgumentException("累计花费必须 ≥ 0，实际=" + spend);
        }
        this.id = id;
        this.template = Objects.requireNonNull(template, "template 不能为 null");
        this.star = star;
        this.spend = spend;
    }

    public int getId() { return id; }
    public UnitData getTemplate() { return template; }
    public int getStar() { return star; }
    public int getSpend() { return spend; }

    /** 已穿装备（不可变视图，穿着序） */
    public List<Equipment> getEquipped() {
        return Collections.unmodifiableList(equipped);
    }

    /** 指定槽位已穿装备；空槽返回 null */
    public Equipment equippedIn(EquipmentSlot slot) {
        for (Equipment item : equipped) {
            if (item.getTemplate().getSlot() == slot) {
                return item;
            }
        }
        return null;
    }

    // —— framework-internal 写方法（仅供 systems 包命令结算调用，渲染层只读；沿 BattleUnit 纪律） ——

    /** 3 合 1 升星（star+1；已是 3 星抛错——调用方保证 ≤2 星才合） */
    public void upgradeStar() {
        if (star >= 3) {
            throw new IllegalStateException("3 星上限，不可再合: " + template.getId());
        }
        star++;
    }

    /** 累计花费累加（买入花费 / 合并折叠，增量 ≥ 0） */
    public void addSpend(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("花费增量必须 ≥ 0，实际=" + amount);
        }
        spend += amount;
    }

    /** 穿戴：满 3 件或同槽已穿抛 IllegalStateException */
    public void equip(Equipment item) {
        Objects.requireNonNull(item, "item 不能为 null");
        if (equipped.size() >= GameBalance.EQUIP_SLOTS_PER_UNIT) {
            throw new IllegalStateException("装备槽已满（" + GameBalance.EQUIP_SLOTS_PER_UNIT + " 件）: " + template.getId());
        }
        if (equippedIn(item.getTemplate().getSlot()) != null) {
            throw new IllegalStateException("槽位已占用: " + item.getTemplate().getSlot());
        }
        equipped.add(item);
    }

    /** 卸下（未穿戴在此棋子抛 IllegalArgumentException） */
    public void unequip(Equipment item) {
        if (!equipped.remove(item)) {
            throw new IllegalArgumentException("该装备未穿戴在此棋子: " + (item == null ? "null" : item.getId()));
        }
    }

    /** id 空间全局唯一，身份即 id（模板与星级不参与判等——升星不变身份） */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Unit)) {
            return false;
        }
        return id == ((Unit) o).id;
    }

    @Override
    public int hashCode() {
        return 31 * 17 + id;
    }
}
```

（Unit.java import 段增：`com.voidvvv.kz_auto_chess_n.config.GameBalance`、`java.util.ArrayList`、`java.util.Collections`、`java.util.List`；类 Javadoc 改为"受控可变：升星/spend/穿脱为 framework-internal 写方法"。）

- **测试要点**：`entities/UnitTest` 增——`equippedIn` 空槽 null；`equip` 同槽二次抛 ISE、满 3 件抛 ISE；`unequip` 未穿戴抛 IAE；`upgradeStar` 3 星抛 ISE；`addSpend` 负值抛 IAE；4 参构造 spend 透传；升星后 equals 不变（同 id）。`entities/EquipmentTest`——equals/hashCode 按 id。

---

### CP6. Player 扩展（inventory/getUnitById/removeUnit）与 undeploy 收口

- **类型**：修改类
- **位置**：`core/.../entities/Player.java:20-24`（字段段）、:124-133（undeploy）
- **改动说明**：背包归属（Q1 裁决）；`getUnitById`/`removeUnit` 供 Sell/Equip/详情等按 id 定位与移除（板/席通用）；undeploy 堵 Phase 3 遗留假设洞（Phase 4 口径 #26 指名的"名单收口归 Phase 5"）——备战席满时抛错而非静默越界（ArrayList.add 无上限）。
- **代码**（修改前逐字摘自 Player.java:20-24）：

```java
修改前：
public class Player {
    private int gold;
    private int level = 1;
    private int currentExp;
    private final List<Unit> bench = new ArrayList<Unit>();      // 备战席 ≤ BENCH_SIZE(9)
    private final Unit[] deployment = new Unit[GameBalance.BOARD_COLS * 3]; // 玩家区 18 格
```

```java
修改后：
public class Player {
    private int gold;
    private int level = 1;
    private int currentExp;
    private final List<Unit> bench = new ArrayList<Unit>();      // 备战席 ≤ BENCH_SIZE(9)
    private final Unit[] deployment = new Unit[GameBalance.BOARD_COLS * 3]; // 玩家区 18 格
    private final List<Equipment> inventory = new ArrayList<Equipment>();   // 背包：未穿戴装备（无上限，实现口径 #16）
```

（修改前逐字摘自 Player.java:124-133）：

```java
修改前：
    /** 撤下：清格并回备战席（单位本占名单一席，回席必有余位）；空格抛 IllegalStateException */
    public void undeploy(int gridX, int gridY) {
        int idx = playerZoneIndex(gridX, gridY);
        Unit unit = deployment[idx];
        if (unit == null) {
            throw new IllegalStateException("该格无部署单位: (" + gridX + "," + gridY + ")");
        }
        deployment[idx] = null;
        bench.add(unit);
    }
```

```java
修改后：
    /** 撤下：清格并回备战席；备战席已满抛 IllegalStateException（Phase 3 假设洞收口——
     *  理论 27 上限下的溢出路径全部前置校验后，此处为防御兜底而非静默越界） */
    public void undeploy(int gridX, int gridY) {
        int idx = playerZoneIndex(gridX, gridY);
        Unit unit = deployment[idx];
        if (unit == null) {
            throw new IllegalStateException("该格无部署单位: (" + gridX + "," + gridY + ")");
        }
        if (bench.size() >= GameBalance.BENCH_SIZE) {
            throw new IllegalStateException("备战席已满（" + GameBalance.BENCH_SIZE + " 格），无法撤下: " + unit.getId());
        }
        deployment[idx] = null;
        bench.add(unit);
    }
```

（Player.java 新增方法段，插于 getRosterSize 之后）：

```java
    // —— Phase 5 背包与按 id 名单操作（framework-internal 纪律：写方法仅供命令结算调用） ——

    /** 背包（不可变视图，入包序） */
    public List<Equipment> getInventory() {
        return Collections.unmodifiableList(inventory);
    }

    /** 入包（背包无上限——实现口径 #16） */
    public void addToInventory(Equipment item) {
        inventory.add(Objects.requireNonNull(item, "item 不能为 null"));
    }

    /** 出包：不在包抛 IllegalArgumentException */
    public void removeFromInventory(Equipment item) {
        if (!inventory.remove(item)) {
            throw new IllegalArgumentException("装备不在背包: " + (item == null ? "null" : item.getId()));
        }
    }

    /** 背包按 id 查装备；未找到返回 null */
    public Equipment findInventoryItem(int itemId) {
        for (Equipment item : inventory) {
            if (item.getId() == itemId) {
                return item;
            }
        }
        return null;
    }

    /** 名单按 id 查棋子（备战席 + 部署表）；未找到返回 null */
    public Unit getUnitById(int unitId) {
        for (Unit unit : bench) {
            if (unit.getId() == unitId) {
                return unit;
            }
        }
        for (int y = 4; y <= 6; y++) {
            for (int x = 0; x < GameBalance.BOARD_COLS; x++) {
                Unit unit = deployedAt(x, y);
                if (unit != null && unit.getId() == unitId) {
                    return unit;
                }
            }
        }
        return null;
    }

    /** 名单移除（席上/板上皆可；板上移除同步释放人口）；不在名单返回 false */
    public boolean removeUnit(Unit unit) {
        Objects.requireNonNull(unit, "unit 不能为 null");
        if (bench.remove(unit)) {
            return true;
        }
        for (int i = 0; i < deployment.length; i++) {
            if (deployment[i] == unit) {
                deployment[i] = null;
                return true;
            }
        }
        return false;
    }

    /** 全名单装备中按 id 找穿戴者；未找到返回 null（UnequipItem 载荷无 unitId，architecture §4.1） */
    public Unit findEquipOwner(int itemId) {
        for (Unit unit : bench) {
            for (Equipment item : unit.getEquipped()) {
                if (item.getId() == itemId) {
                    return unit;
                }
            }
        }
        for (int y = 4; y <= 6; y++) {
            for (int x = 0; x < GameBalance.BOARD_COLS; x++) {
                Unit unit = deployedAt(x, y);
                if (unit != null) {
                    for (Equipment item : unit.getEquipped()) {
                        if (item.getId() == itemId) {
                            return unit;
                        }
                    }
                }
            }
        }
        return null;
    }
```

- **测试要点**：`entities/PlayerTest` 增——undeploy 席满抛 ISE（先 deploy 9 个 + 撤 1）；inventory 增删/视图不可变；`getUnitById` 席/板/未找到三态；`removeUnit` 席/板/false 三态；`findEquipOwner` 穿戴者定位。

---

### CP7. ActiveStatus.tickInterval + StatusSystem 心跳间隔化

- **类型**：修改类 ×2
- **位置**：`core/.../entities/ActiveStatus.java:15-29`；`core/.../systems/StatusSystem.java:35-64（apply）、85-101（tickHeartbeats）`
- **改动说明**：passiveStatus 进 StatusSystem 的承载前提（Q1 裁决；data_schema §八"装备入口进 StatusSystem 的第二种形态"）。缺省 1s 构造保持既有技能/羁绊调用零改动；`apply` 增带 tickInterval 的重载供装备落地（旧签名委托缺省值）。
- **代码**（修改前逐字摘自 ActiveStatus.java:15-29）：

```java
修改前：
public final class ActiveStatus {
    private final StatusType type;
    /** 施加者 unit id；开局效果等无单位来源时为 -1 */
    private final int sourceId;
    private float remainingTime;
    /** DOT/REGEN 的 1s 心跳累积器（施加时 0，首跳在满 1s——口径 #10） */
    private float tickTimer;
    private float power;

    public ActiveStatus(StatusType type, int sourceId, float power, float duration) {
        this.type = Objects.requireNonNull(type, "type 不能为 null");
        this.sourceId = sourceId;
        this.power = power;
        this.remainingTime = duration;
    }
```

```java
修改后：
public final class ActiveStatus {
    private final StatusType type;
    /** 施加者 unit id；开局效果等无单位来源时为 -1 */
    private final int sourceId;
    private float remainingTime;
    /** DOT/REGEN 的心跳累积器（施加时 0，首跳在满一个间隔——口径 #10） */
    private float tickTimer;
    private float power;
    /** 心跳间隔（秒）：技能/羁绊缺省 1s（DOT_TICK_INTERVAL）；装备 passiveStatus 可自定义（龙心 5s） */
    private final float tickInterval;

    public ActiveStatus(StatusType type, int sourceId, float power, float duration) {
        this(type, sourceId, power, duration, GameBalance.DOT_TICK_INTERVAL);
    }

    public ActiveStatus(StatusType type, int sourceId, float power, float duration, float tickInterval) {
        this.type = Objects.requireNonNull(type, "type 不能为 null");
        this.sourceId = sourceId;
        this.power = power;
        this.remainingTime = duration;
        if (tickInterval <= 0) {
            throw new IllegalArgumentException("心跳间隔必须 > 0（秒），实际=" + tickInterval);
        }
        this.tickInterval = tickInterval;
    }

    public float getTickInterval() { return tickInterval; }
```

（ActiveStatus import 段增 `com.voidvvv.kz_auto_chess_n.config.GameBalance`；StatusSystem.apply 增重载——修改前逐字摘自 StatusSystem.java:34-36）：

```java
修改前：
    /** 挂载或刷新一个状态（sourceId：施加者 unit id，开局效果为 -1） */
    public void apply(BattleState state, BattleUnit target, StatusType type,
                      float power, float duration, int sourceId) {
```

```java
修改后：
    /** 挂载或刷新一个状态（sourceId：施加者 unit id，开局效果为 -1；心跳间隔缺省 1s） */
    public void apply(BattleState state, BattleUnit target, StatusType type,
                      float power, float duration, int sourceId) {
        apply(state, target, type, power, duration, sourceId, GameBalance.DOT_TICK_INTERVAL);
    }

    /** 全参重载：装备 passiveStatus 落地（自定义心跳间隔，实现口径 #7） */
    public void apply(BattleState state, BattleUnit target, StatusType type,
                      float power, float duration, int sourceId, float tickInterval) {
```

（StatusSystem.apply 体内 `new ActiveStatus(StatusType.SHIELD, sourceId, power, ...)` 与 `new ActiveStatus(type, sourceId, power, duration)` 两处构造改为传 tickInterval 的全参构造；tickHeartbeats 修改前逐字摘自 StatusSystem.java:90-93）：

```java
修改前：
        status.setTickTimer(status.getTickTimer() + dt);
        while (status.getTickTimer() >= GameBalance.DOT_TICK_INTERVAL - TIME_EPSILON) {
            status.setTickTimer(status.getTickTimer() - GameBalance.DOT_TICK_INTERVAL);
```

```java
修改后：
        status.setTickTimer(status.getTickTimer() + dt);
        float interval = status.getTickInterval();
        while (status.getTickTimer() >= interval - TIME_EPSILON) {
            status.setTickTimer(status.getTickTimer() - interval);
```

- **测试要点**：`entities/ActiveStatus` 相关 + `systems/StatusSystemTest` 增——自定义间隔 REGEN（power=0.02、interval=5）：4.9s 零跳、5.1s 恰一跳回 2% maxHp（TIME_EPSILON 边界沿用既有测试口径）；缺省构造 interval == DOT_TICK_INTERVAL（技能/羁绊回归零变化）。

---

### CP8. 新命令类 ×9（StartRun/Buy/Sell/RefreshShop/BuyExp/Equip/Unequip/PickChest/AbandonRun）

- **类型**：新建文件 ×9
- **位置**：`core/src/main/java/com/voidvvv/kz_auto_chess_n/command/`
- **改动说明**：input §4.1 纯数据载体（禁业务方法）；载荷与 architecture §4.1 一致（BuyUnit=slot / SellUnit=unitId / EquipItem=itemId+unitId / UnequipItem=itemId / PickChest=option）；StartRun 为 11+1 第 12 命令（差异声明 #7）；无载荷命令用 INSTANCE 单例（沿 `SurrenderCommand` 先例）。至此 12 命令全集到齐（MoveUnit/StartBattle/Surrender 为 Phase 4 既有）。
- **代码**（九文件合一展示）：

```java
// command/StartRunCommand.java
package com.voidvvv.kz_auto_chess_n.command;

import java.util.Objects;

/** 开局域边界事件（architecture §一：回放流第 0 条记录；Q3 裁决：seed 由 UI 给定，heroId 留 Phase 6 扩展位恒 null） */
public final class StartRunCommand implements GameCommand {
    private final long seed;
    private final String sceneId;
    private final String heroId;

    public StartRunCommand(long seed, String sceneId, String heroId) {
        this.seed = seed;
        this.sceneId = Objects.requireNonNull(sceneId, "sceneId 不能为 null");
        this.heroId = heroId;
    }

    public long getSeed() { return seed; }
    public String getSceneId() { return sceneId; }
    /** Phase 6 扩展位：本期恒 null */
    public String getHeroId() { return heroId; }

    @Override
    public String toString() {
        return "StartRun(seed=" + seed + ", scene=" + sceneId + ", hero=" + heroId + ")";
    }
}

// command/BuyUnitCommand.java
package com.voidvvv.kz_auto_chess_n.command;

/** 购买（GDD §3.4）：载荷仅槽位索引——查价不信任载荷（input §6.3） */
public final class BuyUnitCommand implements GameCommand {
    private final int slotIndex;

    public BuyUnitCommand(int slotIndex) { this.slotIndex = slotIndex; }

    public int getSlotIndex() { return slotIndex; }

    @Override
    public String toString() { return "BuyUnit(slot=" + slotIndex + ")"; }
}

// command/SellUnitCommand.java
package com.voidvvv.kz_auto_chess_n.command;

/** 卖出（GDD §3.6）：板/席皆可；返还 = Unit.spend 累计花费 100% */
public final class SellUnitCommand implements GameCommand {
    private final int unitId;

    public SellUnitCommand(int unitId) { this.unitId = unitId; }

    public int getUnitId() { return unitId; }

    @Override
    public String toString() { return "SellUnit(unit=" + unitId + ")"; }
}

// command/RefreshShopCommand.java
package com.voidvvv.kz_auto_chess_n.command;

/** 轮内主动刷新（2 金整批替换）；轮首免费那次是系统行为不入队（architecture §4.1） */
public final class RefreshShopCommand implements GameCommand {
    public static final RefreshShopCommand INSTANCE = new RefreshShopCommand();

    private RefreshShopCommand() {
    }

    @Override
    public String toString() { return "RefreshShop"; }
}

// command/BuyExpCommand.java
package com.voidvvv.kz_auto_chess_n.command;

/** 购买经验（4 金 = 4 经验，GDD §3.5）；Lv.7 封顶禁买（handler 校验 + UI 灰置） */
public final class BuyExpCommand implements GameCommand {
    public static final BuyExpCommand INSTANCE = new BuyExpCommand();

    private BuyExpCommand() {
    }

    @Override
    public String toString() { return "BuyExp"; }
}

// command/EquipItemCommand.java
package com.voidvvv.kz_auto_chess_n.command;

/** 穿戴（背包→棋子）：槽位由装备类型推导（武器/盔甲/饰品，architecture §4.1） */
public final class EquipItemCommand implements GameCommand {
    private final int itemId;
    private final int unitId;

    public EquipItemCommand(int itemId, int unitId) {
        this.itemId = itemId;
        this.unitId = unitId;
    }

    public int getItemId() { return itemId; }
    public int getUnitId() { return unitId; }

    @Override
    public String toString() { return "EquipItem(item=" + itemId + ", unit=" + unitId + ")"; }
}

// command/UnequipItemCommand.java
package com.voidvvv.kz_auto_chess_n.command;

/** 卸下（棋子→背包）：载荷仅 itemId，穿戴者由名单扫描（architecture §4.1） */
public final class UnequipItemCommand implements GameCommand {
    private final int itemId;

    public UnequipItemCommand(int itemId) { this.itemId = itemId; }

    public int getItemId() { return itemId; }

    @Override
    public String toString() { return "UnequipItem(item=" + itemId + ")"; }
}

// command/PickChestCommand.java
package com.voidvvv.kz_auto_chess_n.command;

/** 宝箱三选一领取（RESULT 期）：内容进 RESULT 时已 roll 好，本命令零 RNG（architecture §4.1） */
public final class PickChestCommand implements GameCommand {
    private final int optionIndex;

    public PickChestCommand(int optionIndex) { this.optionIndex = optionIndex; }

    public int getOptionIndex() { return optionIndex; }

    @Override
    public String toString() { return "PickChest(option=" + optionIndex + ")"; }
}

// command/AbandonRunCommand.java
package com.voidvvv.kz_auto_chess_n.command;

/** 放弃远征（GDD §2.1：暂停菜单 + 二次确认；按已达波数结算部分熟练度） */
public final class AbandonRunCommand implements GameCommand {
    public static final AbandonRunCommand INSTANCE = new AbandonRunCommand();

    private AbandonRunCommand() {
    }

    @Override
    public String toString() { return "AbandonRun"; }
}
```

- **测试要点**：`command/GameCommandTest`（新建，纯载荷）——各命令 getter/toString/INSTANCE 单例性；`StartRunCommand` heroId=null 合法。

---

### CP9. ChestOption/ChestOffer 实体 + ChestSystem

- **类型**：新建文件 ×3
- **位置**：`core/.../entities/ChestOption.java`、`entities/ChestOffer.java`、`systems/ChestSystem.java`
- **改动说明**：Q2 裁决 A 最小可玩规则的核心实现。roll 固定 2 RNG（实现口径 #1）；Boss 箱权重保证 ≥成装（口径 #3）；池空降级/退化（防御）。`ChestOffer` 不可变，picked 状态由 `RunState.pendingChest == null` 表达。
- **代码**（三文件合一展示）：

```java
// entities/ChestOption.java
package com.voidvvv.kz_auto_chess_n.entities;

import java.util.Objects;

/** 宝箱单选项（不可变）：GDD 用语 金币/经验书/装备 ↔ GOLD/EXP_BOOK/EQUIPMENT */
public final class ChestOption {
    public enum Kind { GOLD, EXP_BOOK, EQUIPMENT }

    private final Kind kind;
    /** GOLD/EXP_BOOK 金额；EQUIPMENT 为 0 */
    private final int amount;
    /** 仅 EQUIPMENT 非 null */
    private final String equipmentId;

    private ChestOption(Kind kind, int amount, String equipmentId) {
        this.kind = Objects.requireNonNull(kind, "kind 不能为 null");
        this.amount = amount;
        this.equipmentId = equipmentId;
    }

    public static ChestOption gold(int amount) { return new ChestOption(Kind.GOLD, amount, null); }
    public static ChestOption expBook(int amount) { return new ChestOption(Kind.EXP_BOOK, amount, null); }
    public static ChestOption equipment(String equipmentId) {
        return new ChestOption(Kind.EQUIPMENT, 0, Objects.requireNonNull(equipmentId, "equipmentId 不能为 null"));
    }

    public Kind getKind() { return kind; }
    public int getAmount() { return amount; }
    public String getEquipmentId() { return equipmentId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChestOption)) {
            return false;
        }
        ChestOption that = (ChestOption) o;
        return amount == that.amount && kind == that.kind && Objects.equals(equipmentId, that.equipmentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, amount, equipmentId);
    }
}

// entities/ChestOffer.java
package com.voidvvv.kz_auto_chess_n.entities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 宝箱三选一 offer（不可变；roll 于胜局进 RESULT 时一次性；领取后 RunState.pendingChest 置 null） */
public final class ChestOffer {
    private final int round;
    private final boolean boss;
    private final List<ChestOption> options;

    public ChestOffer(int round, boolean boss, List<ChestOption> options) {
        this.round = round;
        this.boss = boss;
        this.options = Collections.unmodifiableList(new ArrayList<ChestOption>(
                Objects.requireNonNull(options, "options 不能为 null")));
        if (options.size() != 3) {
            throw new IllegalArgumentException("宝箱必须恰有三个选项，实际=" + options.size());
        }
    }

    /** 选项（槽序固定：0=金币 1=经验书 2=装备，实现口径 #1） */
    public ChestOption optionAt(int index) {
        if (index < 0 || index >= options.size()) {
            return null;
        }
        return options.get(index);
    }

    public int getRound() { return round; }
    public boolean isBoss() { return boss; }
    public List<ChestOption> getOptions() { return options; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChestOffer)) {
            return false;
        }
        ChestOffer that = (ChestOffer) o;
        return round == that.round && boss == that.boss && options.equals(that.options);
    }

    @Override
    public int hashCode() {
        return Objects.hash(round, boss, options);
    }
}

// systems/ChestSystem.java
package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.EquipmentData;
import com.voidvvv.kz_auto_chess_n.data.EquipmentRarity;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.ChestOffer;
import com.voidvvv.kz_auto_chess_n.entities.ChestOption;
import com.voidvvv.kz_auto_chess_n.entities.Equipment;
import com.voidvvv.kz_auto_chess_n.entities.IdIssuer;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 宝箱系统（GDD §3.2/§5.2；Q2 裁决 A 最小可玩规则）。
 *
 * <p>roll 固定消耗 2 RNG：稀有度 1 + 池内均匀抽取 1；金币/经验选项零 RNG（公式确定）。
 * 装备池 = equipments.json 全集按稀有度过滤（GameData 声明序）；池空逐级向低稀有度降级、
 * 全空退化为金币选项（内容缺失防御，加载期软告警预警——CP3）。
 */
public final class ChestSystem {

    /** 胜局进入 RESULT 时 roll 三选项（槽1 金币常驻 / 槽2 经验书 / 槽3 装备） */
    public ChestOffer roll(int round, GameData data, RandomGenerator rng) {
        boolean boss = GameBalance.isBossRound(round);
        int gold = GameBalance.chestGold(round, boss);
        ChestOption equipment = rollEquipment(data, rng,
                boss ? GameBalance.BOSS_CHEST_RARITY_WEIGHTS : GameBalance.CHEST_RARITY_WEIGHTS, gold);
        return new ChestOffer(round, boss, Arrays.asList(
                ChestOption.gold(gold),
                ChestOption.expBook(GameBalance.CHEST_EXP_BOOK_GAIN),
                equipment));
    }

    /** 领取（PickChest handler 调）：装备发号入包，金币/经验入账；返回通知行文案 */
    public String apply(ChestOption option, Player player, IdIssuer idIssuer, GameData data) {
        switch (option.getKind()) {
            case GOLD:
                player.addGold(option.getAmount());
                return "宝箱：金币 +" + option.getAmount();
            case EXP_BOOK:
                player.addExp(option.getAmount());
                return "宝箱：经验 +" + option.getAmount();
            case EQUIPMENT:
            default:
                EquipmentData template = data.getEquipment(option.getEquipmentId());
                player.addToInventory(new Equipment(idIssuer.nextId(), template));
                return "宝箱：获得 " + template.getName();
        }
    }

    private ChestOption rollEquipment(GameData data, RandomGenerator rng,
                                      int[] rarityWeights, int fallbackGold) {
        int rarityIndex = rng.weightedPick(rarityWeights);                                   // RNG #1
        List<EquipmentData> pool = rarityPool(data, EquipmentRarity.values()[rarityIndex]);
        while (pool.isEmpty() && rarityIndex > 0) {
            rarityIndex--;                                                                   // 内容缺失防御：向低稀有度降级
            pool = rarityPool(data, EquipmentRarity.values()[rarityIndex]);
        }
        int pick = rng.weightedPick(uniform(pool.size()));                                   // RNG #2（池空也消耗，保确定性）
        return pool.isEmpty() ? ChestOption.gold(fallbackGold)                               // 全空兜底：退化为金币
                : ChestOption.equipment(pool.get(pick).getId());
    }

    private static List<EquipmentData> rarityPool(GameData data, EquipmentRarity rarity) {
        List<EquipmentData> pool = new ArrayList<EquipmentData>();
        for (EquipmentData template : data.getEquipments().values()) {
            if (template.getRarity() == rarity) {
                pool.add(template);
            }
        }
        return pool;
    }

    private static int[] uniform(int size) {
        int[] weights = new int[Math.max(1, size)];
        Arrays.fill(weights, 1);
        return weights;
    }
}
```

> 注：ChestSystem 完整 import 见上方代码块首部（含 `utils.RandomGenerator`）；`apply` 与私有方法随正式实现保留。

- **测试要点**：`systems/ChestSystemTest`——同 seed 两次 roll 结果 `equals`（ChestOffer equals 已备）；`getConsumedCount()` 恰 +2；普通箱第 4 轮（非 Boss）槽1 金币 = chestGold(4,false)=4；Boss 轮（7）装备槽 `getEquipmentId()` 查表稀有度 ∈ {RARE, LEGENDARY}（全量遍历多个 seed 断言白不出现）；单稀有度装备数据集 → 降级路径；空装备表 → 槽3 退化金币且 RNG 仍 2；apply 三分支入账/入包/发号正确。

---

### CP10. systems/ShopSystem（商店 + 购买 + 3 合 1 级联）

- **类型**：新建文件
- **位置**：`core/src/main/java/com/voidvvv/kz_auto_chess_n/systems/ShopSystem.java`
- **改动说明**：Q6 裁决后玩家兵源唯一入口（演示名单移除）。槽位状态 + reroll（固定 10 RNG，口径 #4）+ BuyUnit/RefreshShop/BuyExp handler + mergeCascade（口径 #5：首位保留位置与装备、级联）。UI 预校验（灰置/提示）另见 CP22，本层是最后防线（input §4.3 双层校验）。
- **代码**（完整）：

```java
package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.command.BuyExpCommand;
import com.voidvvv.kz_auto_chess_n.command.BuyUnitCommand;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.RefreshShopCommand;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 商店系统（GDD §3.4；Q6 裁决：起始 10 金商店自购，演示名单移除）。
 *
 * <p>槽位状态：买走即置 null（刷新前不回填）。整批重掷 = 轮首免费（系统行为，RunFlowSystem 调）
 * 与 RefreshShop（2 金）共用 {@link #reroll}——每槽固定消耗 2 RNG（费阶 1 + 池内抽取 1），
 * 池为空也照常消耗（实现口径 #4：消耗序与内容无关，保确定性）。
 * 3 合 1（GDD §4.3）是 BuyUnit 的系统后果：合成产物保留首位参与者位置与装备，
 * 被吞并者装备回背包、spend 折叠加总，级联直到无可合（实现口径 #5）。
 */
public final class ShopSystem {

    private final UnitData[] slots = new UnitData[GameBalance.SHOP_SLOTS];

    /** 注册经营命令 handler（input §6.1：handler 由所属 system 注册） */
    public void registerHandlers(CommandManager manager) {
        manager.registerHandler(BuyUnitCommand.class, (cmd, ctx) -> {
            if (ctx.getRunState().getPhase() != GamePhase.SHOPPING) {
                return false;
            }
            return buy(ctx, ((BuyUnitCommand) cmd).getSlotIndex());
        });
        manager.registerHandler(RefreshShopCommand.class, (cmd, ctx) -> {
            if (ctx.getRunState().getPhase() != GamePhase.SHOPPING
                    || !ctx.getPlayer().canAfford(GameBalance.SHOP_REFRESH_COST)) {
                return false;
            }
            ctx.getPlayer().addGold(-GameBalance.SHOP_REFRESH_COST);
            reroll(ctx.getRunState().getRound(), ctx.getGameData(), ctx.getRng());
            return true;
        });
        manager.registerHandler(BuyExpCommand.class, (cmd, ctx) -> {
            Player player = ctx.getPlayer();
            if (ctx.getRunState().getPhase() != GamePhase.SHOPPING
                    || player.getLevel() >= GameBalance.MAX_PLAYER_LEVEL
                    || !player.canAfford(GameBalance.BUY_EXP_COST)) {
                return false;
            }
            player.addGold(-GameBalance.BUY_EXP_COST);
            player.addExp(GameBalance.BUY_EXP_GAIN);
            return true;
        });
    }

    /** 槽位模板；空槽/越界返回 null */
    public UnitData slotAt(int index) {
        if (index < 0 || index >= slots.length) {
            return null;
        }
        return slots[index];
    }

    /** 槽位快照（不可变；空槽为 null 元素） */
    public List<UnitData> getSlots() {
        return Collections.unmodifiableList(Arrays.asList(slots));
    }

    /** 整批重掷 5 槽：每槽 = 费阶 roll + 同费池均匀抽取（RNG 恒 2/槽，共 10） */
    public void reroll(int round, GameData data, RandomGenerator rng) {
        float[] probabilities = GameBalance.shopTierProbabilities(round);
        int[] tierWeights = {
                Math.round(probabilities[0] * GameBalance.PROBABILITY_WEIGHT_SCALE),
                Math.round(probabilities[1] * GameBalance.PROBABILITY_WEIGHT_SCALE),
                Math.round(probabilities[2] * GameBalance.PROBABILITY_WEIGHT_SCALE)};
        for (int i = 0; i < slots.length; i++) {
            int tier = rng.weightedPick(tierWeights);                          // RNG #1（费阶 0/1/2 → cost 1/2/3）
            List<UnitData> pool = tierPool(data, tier + 1);
            int pick = rng.weightedPick(uniform(pool.size()));                 // RNG #2（池空也消耗）
            slots[i] = pool.isEmpty() ? null : pool.get(pick);
        }
    }

    /** 购买（architecture §5.2 校验要点）：查价不信任载荷；席满禁买，例外 = 购买即完成 3 合 1 */
    boolean buy(RunContext ctx, int slotIndex) {
        Player player = ctx.getPlayer();
        UnitData template = slotAt(slotIndex);
        if (template == null || !player.canAfford(template.getCost())) {
            return false;
        }
        boolean mergeReady = countSameTemplateStar(player, template.getId(), 1) >= 2;
        if (player.getBench().size() >= GameBalance.BENCH_SIZE && !mergeReady) {
            return false; // 备战席已满且不会立即合成（UI 预校验灰置 + 提示，input §4.3）
        }
        player.addGold(-template.getCost());
        slots[slotIndex] = null;
        Unit bought = new Unit(ctx.getRunState().getIdIssuer().nextId(), template, 1, template.getCost());
        player.addToBench(bought);
        ctx.getRunState().addNotice("购入 " + template.getName() + "（-" + template.getCost() + " 金）");
        mergeCascade(ctx);
        return true;
    }

    /** 3 合 1 级联合成（GDD §4.3：买到第三个立即触发；可级联 2→3 星） */
    private void mergeCascade(RunContext ctx) {
        boolean mergedAny = true;
        while (mergedAny) {
            mergedAny = false;
            for (Unit survivor : rosterInRemovalOrder(ctx.getPlayer())) {
                List<Unit> group = new ArrayList<Unit>();
                for (Unit candidate : rosterInRemovalOrder(ctx.getPlayer())) {
                    if (candidate.getTemplate().getId().equals(survivor.getTemplate().getId())
                            && candidate.getStar() == survivor.getStar()) {
                        group.add(candidate);
                    }
                }
                if (group.size() >= 3 && survivor.getStar() < 3) {
                    int foldedSpend = 0;
                    for (int i = 1; i < 3; i++) {
                        Unit consumed = group.get(i);
                        foldedSpend += consumed.getSpend();
                        EquipmentSystem.unequipAll(consumed, ctx.getPlayer());
                        ctx.getPlayer().removeUnit(consumed);
                    }
                    survivor.addSpend(foldedSpend); // 首位保留位置与装备（实现口径 #5）
                    survivor.upgradeStar();
                    ctx.getRunState().addNotice(
                            survivor.getTemplate().getName() + " 升至 " + survivor.getStar() + " 星");
                    mergedAny = true;
                    break; // 重扫（级联 + 名单已变）
                }
            }
        }
    }

    /** 名单移除序（确定性）：备战席入席序优先，其次部署扫描序 y↑x↑ */
    private static List<Unit> rosterInRemovalOrder(Player player) {
        List<Unit> roster = new ArrayList<Unit>(player.getBench());
        roster.addAll(player.getDeployedUnits());
        return roster;
    }

    private static int countSameTemplateStar(Player player, String templateId, int star) {
        int count = 0;
        for (Unit unit : rosterInRemovalOrder(player)) {
            if (unit.getTemplate().getId().equals(templateId) && unit.getStar() == star) {
                count++;
            }
        }
        return count;
    }

    /** 同费池（非 Boss、cost 匹配；GameData 声明序——LinkedHashMap 确定性） */
    private static List<UnitData> tierPool(GameData data, int cost) {
        List<UnitData> pool = new ArrayList<UnitData>();
        for (UnitData template : data.getUnits().values()) {
            if (!template.isBoss() && template.getCost() == cost) {
                pool.add(template);
            }
        }
        return pool;
    }

    private static int[] uniform(int size) {
        int[] weights = new int[Math.max(1, size)];
        Arrays.fill(weights, 1);
        return weights;
    }
}
```

- **测试要点**：`systems/ShopSystemTest`——reroll 同 seed 同结果、`getConsumedCount()` 恰 +10、1~3 轮全部槽位 cost==1（概率 100% 一费）；buy：扣钱/槽置 null/新 Unit spend=cost/入席；金币不足 false；席满禁买；席满 + 同名同星 ×2 → 例外放行且合成后 bench 净 -1（3 入 1 出）；mergeCascade 级联（9 个同名 1 星 → 3 个 2 星 → 1 个 3 星）；合并 spend 折叠（1+1+1=3 → 2 星 spend 3）；被吞并者装备回背包、首位装备保留；deployed 参与合并时首位居板（部署位保留）。

---

### CP11. systems/EquipmentSystem + EquipmentStats 修正源

- **类型**：新建文件 ×2
- **位置**：`core/.../systems/EquipmentSystem.java`、`systems/EquipmentStats.java`
- **改动说明**：穿脱命令 handler（Q1 裁决）+ 卖出/合成共用的 `unequipAll` + Phase 3 Q4 预留的第二个 `StatModifierSource`（装备·单体作用域，与羁绊·侧全体并列进 `deriveBaseline`）。
- **代码**（两文件合一展示）：

```java
// systems/EquipmentSystem.java
package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.EquipItemCommand;
import com.voidvvv.kz_auto_chess_n.command.UnequipItemCommand;
import com.voidvvv.kz_auto_chess_n.entities.Equipment;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.Unit;

import java.util.ArrayList;
import java.util.List;

/**
 * 装备系统（GDD §5.2 B2 灵活可拆卸）：穿脱命令 handler + 卖出/合成共用的卸下助手。
 * EquipItem 槽位被占 → 拒绝（实现口径 #6：先手动卸下，UI 提示）。
 */
public final class EquipmentSystem {

    /** 注册穿脱命令 handler（门控：SHOPPING） */
    public void registerHandlers(CommandManager manager) {
        manager.registerHandler(EquipItemCommand.class, (cmd, ctx) -> {
            if (ctx.getRunState().getPhase() != GamePhase.SHOPPING) {
                return false;
            }
            EquipItemCommand equip = (EquipItemCommand) cmd;
            Equipment item = ctx.getPlayer().findInventoryItem(equip.getItemId());
            Unit unit = ctx.getPlayer().getUnitById(equip.getUnitId());
            if (item == null || unit == null
                    || unit.equippedIn(item.getTemplate().getSlot()) != null) {
                return false; // 物品不在包 / 棋子不在名单 / 槽位被占
            }
            ctx.getPlayer().removeFromInventory(item);
            unit.equip(item);
            ctx.getRunState().addNotice(
                    unit.getTemplate().getName() + " 穿戴 " + item.getTemplate().getName());
            return true;
        });
        manager.registerHandler(UnequipItemCommand.class, (cmd, ctx) -> {
            if (ctx.getRunState().getPhase() != GamePhase.SHOPPING) {
                return false;
            }
            UnequipItemCommand unequip = (UnequipItemCommand) cmd;
            Unit owner = ctx.getPlayer().findEquipOwner(unequip.getItemId());
            if (owner == null) {
                return false;
            }
            for (Equipment item : owner.getEquipped()) {
                if (item.getId() == unequip.getItemId()) {
                    owner.unequip(item);
                    ctx.getPlayer().addToInventory(item);
                    ctx.getRunState().addNotice("卸下 " + item.getTemplate().getName());
                    return true;
                }
            }
            return false;
        });
    }

    /** 卖出/合成共用：卸下单位全部装备回背包（GDD §3.6——不随棋子消失） */
    public static void unequipAll(Unit unit, Player player) {
        List<Equipment> equipped = new ArrayList<Equipment>(unit.getEquipped());
        for (Equipment item : equipped) {
            unit.unequip(item);
            player.addToInventory(item);
        }
    }
}

// systems/EquipmentStats.java
package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.data.EquipmentEffect;
import com.voidvvv.kz_auto_chess_n.entities.Equipment;
import com.voidvvv.kz_auto_chess_n.entities.StatModifierBlock;
import com.voidvvv.kz_auto_chess_n.entities.StatModifierSource;

import java.util.List;
import java.util.Objects;

/**
 * 装备属性修正源（Phase 3 Q4 修正源列表的第二个实现）：该单位所穿装备 effects 的 ΣADD/ΣPCT。
 * 作用域 = 单体（羁绊快照为侧全体）；StatPipeline.deriveBaseline 结算器零改动。
 */
public final class EquipmentStats implements StatModifierSource {

    public static final EquipmentStats EMPTY = new EquipmentStats(StatModifierBlock.empty());

    private final StatModifierBlock block;

    private EquipmentStats(StatModifierBlock block) {
        this.block = Objects.requireNonNull(block, "block 不能为 null");
    }

    /** 装备列表 → 合并修正块（空列表/零修正返回 EMPTY 单例） */
    public static EquipmentStats of(List<Equipment> equipped) {
        Objects.requireNonNull(equipped, "equipped 不能为 null");
        StatModifierBlock merged = StatModifierBlock.empty();
        for (Equipment item : equipped) {
            for (EquipmentEffect effect : item.getTemplate().getEffects()) {
                merged = merged.plus(StatModifierBlock.of(effect.getStat(), effect.getOp(), effect.getValue()));
            }
        }
        return merged.isEmpty() ? EMPTY : new EquipmentStats(merged);
    }

    @Override
    public StatModifierBlock modifiers() {
        return block;
    }
}
```

- **测试要点**：`systems/EquipmentSystemTest`——equip 成功路径（包→身）；物品不在包/棋子不在名单/槽位被占三态 false；BATTLE 期 false；unequip 经 owner 扫描回包；`unequipAll` 清空并保序入包。`systems/EquipmentStatsTest`——`of(空)` 返回 EMPTY；铁剑（PCT20）+ 玄铁板甲（ADD200）合并后 `addOf(HP)==200 && pctOf(ATTACK)==20`；`modifiers()` 与 StatPipeline.deriveBaseline 联动（模板 × 星级 × scale 后先加后乘，复用 StatPipelineTest 口径）。

---

### CP12. systems/RosterSystem（MoveUnit 迁移 + SellUnit）

- **类型**：新建文件
- **位置**：`core/src/main/java/com/voidvvv/kz_auto_chess_n/systems/RosterSystem.java`
- **改动说明**：input §6.1"handler 由所属 system 注册；Phase 5 拆分"兑现：MoveUnit 从 RunFlowSystem 迁来（`RunFlowSystem.registerHandlers` 中对应块的删除见 §6.CP15）；SellUnit 落地（GDD §3.6：返还 spend 100%、装备自动卸下、板上卖出释放人口）。
- **代码**（完整）：

```java
package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.MoveUnitCommand;
import com.voidvvv.kz_auto_chess_n.command.SellUnitCommand;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.Unit;

/**
 * 名单系统（Phase 4 由 RunFlowSystem 注册的 MoveUnit 本期迁入 + SellUnit 新增）。
 * 卖出（GDD §3.6）：装备自动卸下回背包、返还累计花费 100%、板上卖出同步释放人口。
 */
public final class RosterSystem {

    private final MoveUnitExecutor moveUnitExecutor = new MoveUnitExecutor();

    public void registerHandlers(CommandManager manager) {
        manager.registerHandler(MoveUnitCommand.class, (cmd, ctx) -> {
            if (ctx.getRunState().getPhase() != GamePhase.SHOPPING) {
                return false;
            }
            MoveUnitCommand move = (MoveUnitCommand) cmd;
            return moveUnitExecutor.move(ctx.getPlayer(), move.getUnitId(), move.getTarget());
        });
        manager.registerHandler(SellUnitCommand.class, (cmd, ctx) -> {
            if (ctx.getRunState().getPhase() != GamePhase.SHOPPING) {
                return false;
            }
            Player player = ctx.getPlayer();
            Unit unit = player.getUnitById(((SellUnitCommand) cmd).getUnitId());
            if (unit == null) {
                return false;
            }
            EquipmentSystem.unequipAll(unit, player); // GDD §3.6：不随棋子消失
            int refund = unit.getSpend();
            player.removeUnit(unit);
            player.addGold(refund);
            ctx.getRunState().addNotice("卖出 " + unit.getTemplate().getName() + "（+" + refund + " 金）");
            return true;
        });
    }
}
```

- **测试要点**：`systems/RosterSystemTest`——MoveUnit 迁移后行为与 `MoveUnitExecutorTest` 一致（席→板/交换/门控）；SellUnit 席上/板上两路径：金币 +spend、名单移除（板上格清空）、装备回背包；unitId 不存在 false；BATTLE 期 false。

---

### CP13. RunState 扩展（pendingChest/endCause/mercyGoldThisRound/logicTick/notices）+ RunEndCause + CommandManager 逻辑钟统一

- **类型**：修改类 ×2 + 新建文件 ×1
- **位置**：`core/.../entities/RunState.java:18-59`；`core/.../command/CommandManager.java:44-52,73-89`；新建 `entities/RunEndCause.java`
- **改动说明**：Phase 4 建好的骨架字段本期通电：宝箱 offer/终局成因/本轮怜悯金/全局逻辑钟（Phase 4 口径 #11 销账，实现口径 #17）/系统反应通知流（口径 #13）/StartRun 防重入标记。CommandManager 私有钟删除、tick 戳改消费时盖；`discardPending()` 供重开清残留队列（口径 #12）。
- **代码**（新建 `entities/RunEndCause.java`）：

```java
package com.voidvvv.kz_auto_chess_n.entities;

/** RUN_END 成因（RunEndPanel 文案区分；GDD §2.1 胜利条件 / 放弃远征） */
public enum RunEndCause {
    /** 击败第 25 轮最终 Boss（通关） */
    COMPLETED,
    /** 暂停菜单放弃远征（AbandonRun） */
    ABANDONED
}
```

（RunState 修改前逐字摘自 RunState.java:18-31）：

```java
修改前：
public final class RunState {
    private final long seed;
    private final String sceneId;
    private final IdIssuer idIssuer;
    private int round = 1;
    private GamePhase phase = GamePhase.SHOPPING;
    private int mercyLossCount;
    private List<WaveSpec> enemyWave = Collections.emptyList();

    public RunState(long seed, String sceneId, IdIssuer idIssuer) {
        this.seed = seed;
        this.sceneId = Objects.requireNonNull(sceneId, "sceneId 不能为 null");
        this.idIssuer = Objects.requireNonNull(idIssuer, "idIssuer 不能为 null");
    }
```

```java
修改后：
public final class RunState {
    private final long seed;
    private final String sceneId;
    private final IdIssuer idIssuer;
    private int round = 1;
    private GamePhase phase = GamePhase.SHOPPING;
    private int mercyLossCount;
    private List<WaveSpec> enemyWave = Collections.emptyList();
    /** StartRun 已执行标记（防重入；重开 = 新鲜 RunState 天然复位） */
    private boolean runStarted;
    /** 胜局 RESULT 期的宝箱 offer；领取后置 null（非胜局恒 null） */
    private ChestOffer pendingChest;
    /** RUN_END 期非 null */
    private RunEndCause endCause;
    /** 本轮已发怜悯金币（GDD §3.2 每轮 ≤3；新轮进入清零） */
    private int mercyGoldThisRound;
    /** 全局逻辑钟（CommandManager 消费 tick 后推进——Phase 4 口径 #11 统一销账） */
    private int logicTick;
    /** 熟练度结算暂存（MasteryCalculator stub 产出；Phase 6 接档案域） */
    private int masteryAwarded;
    /** 系统反应通知行（有界 32，UI drain 后清空——实现口径 #13 第三流） */
    private final List<String> notices = new java.util.ArrayList<String>();

    public RunState(long seed, String sceneId, IdIssuer idIssuer) {
        this.seed = seed;
        this.sceneId = Objects.requireNonNull(sceneId, "sceneId 不能为 null");
        this.idIssuer = Objects.requireNonNull(idIssuer, "idIssuer 不能为 null");
    }
```

（RunState getter 段尾追加——修改前逐字摘自 RunState.java:38-39）：

```java
修改前：
    public int getMercyLossCount() { return mercyLossCount; }
    public List<WaveSpec> getEnemyWave() { return enemyWave; }
```

```java
修改后：
    public int getMercyLossCount() { return mercyLossCount; }
    public List<WaveSpec> getEnemyWave() { return enemyWave; }
    public boolean isRunStarted() { return runStarted; }
    public ChestOffer getPendingChest() { return pendingChest; }
    public RunEndCause getEndCause() { return endCause; }
    public int getMercyGoldThisRound() { return mercyGoldThisRound; }
    public int getLogicTick() { return logicTick; }
    public int getMasteryAwarded() { return masteryAwarded; }
```

（RunState framework-internal 写方法段追加——修改前逐字摘自 RunState.java:51-53）：

```java
修改前：
    public void setMercyLossCount(int count) {
        this.mercyLossCount = count;
    }
```

```java
修改后：
    public void setMercyLossCount(int count) {
        this.mercyLossCount = count;
    }

    public void setMercyGoldThisRound(int count) {
        this.mercyGoldThisRound = count;
    }

    public void setPendingChest(ChestOffer offer) {
        this.pendingChest = offer;
    }

    public void setEndCause(RunEndCause cause) {
        this.endCause = Objects.requireNonNull(cause, "cause 不能为 null");
    }

    public void setMasteryAwarded(int awarded) {
        this.masteryAwarded = awarded;
    }

    /** StartRun handler 专属（一次性） */
    public void markRunStarted() {
        this.runStarted = true;
    }

    /** 逻辑钟推进（CommandManager.executeAll 尾调用——唯一调用点） */
    public void advanceTick() {
        logicTick++;
    }

    /** 系统反应通知行（有界 32，FIFO 丢最老；null/空串忽略） */
    public void addNotice(String line) {
        if (line == null || line.trim().isEmpty()) {
            return;
        }
        if (notices.size() >= 32) {
            notices.remove(0);
        }
        notices.add(line);
    }

    /** 取走全部通知行（拷贝后清空——UI 每帧 drain） */
    public List<String> drainNotices() {
        List<String> drained = new java.util.ArrayList<String>(notices);
        notices.clear();
        return drained;
    }
```

（CommandManager 修改前逐字摘自 CommandManager.java:44-52）：

```java
修改前：
    private final List<CommandExecutedListener> listeners = new ArrayList<CommandExecutedListener>();
    private int logicTick;

    /** 入队并盖当前逻辑 tick 戳入历史 */
    public void addCommand(GameCommand cmd) {
        Objects.requireNonNull(cmd, "cmd 不能为 null");
        history.add(new StampedCommand(logicTick, cmd));
        commandQueue.add(cmd);
    }
```

```java
修改后：
    private final List<CommandExecutedListener> listeners = new ArrayList<CommandExecutedListener>();

    /** 入队（tick 戳改在消费时盖——RunState.getLogicTick 为唯一逻辑钟，实现口径 #17） */
    public void addCommand(GameCommand cmd) {
        Objects.requireNonNull(cmd, "cmd 不能为 null");
        commandQueue.add(cmd);
    }

    /** 丢弃未消费的排队命令（重开新局前清残留——实现口径 #12；历史保留） */
    public void discardPending() {
        commandQueue.clear();
    }
```

（CommandManager.executeAll 修改前逐字摘自 CommandManager.java:73-89）：

```java
修改前：
    public void executeAll(RunContext ctx) {
        Objects.requireNonNull(ctx, "ctx 不能为 null");
        GameCommand cmd;
        while ((cmd = commandQueue.poll()) != null) {
            CommandHandler handler = handlers.get(cmd.getClass());
            if (handler == null) {
                System.err.println("[CommandManager] 未注册 handler，命令丢弃: " + cmd.getClass().getSimpleName());
                continue;
            }
            if (handler.handle(cmd, ctx)) {
                for (CommandExecutedListener listener : listeners) {
                    listener.onExecuted(cmd, true);
                }
            }
        }
        logicTick++;
    }
```

```java
修改后：
    public void executeAll(RunContext ctx) {
        Objects.requireNonNull(ctx, "ctx 不能为 null");
        GameCommand cmd;
        while ((cmd = commandQueue.poll()) != null) {
            history.add(new StampedCommand(ctx.getRunState().getLogicTick(), cmd)); // 消费时盖执行 tick
            CommandHandler handler = handlers.get(cmd.getClass());
            if (handler == null) {
                System.err.println("[CommandManager] 未注册 handler，命令丢弃: " + cmd.getClass().getSimpleName());
                continue;
            }
            if (handler.handle(cmd, ctx)) {
                for (CommandExecutedListener listener : listeners) {
                    listener.onExecuted(cmd, true);
                }
            }
        }
        ctx.getRunState().advanceTick(); // 逻辑钟唯一归属：RunState
    }
```

- **测试要点**：`entities/RunStateTest` 增——notices 有界 32/drain 清空/markRunStarted/pendingChest 置换/advanceTick 计数。`command/CommandManagerTest` 改口径——历史条目 tick 为执行 tick 且首命令为 0、两次 executeAll 后第二命令 tick=1（原"入队时盖戳"断言作废，Q6/口径 #17）；discardPending 后 executeAll 零执行但历史保留。

---

### CP14. RunContext 增 shop 字段

- **类型**：修改类
- **位置**：`core/.../command/RunContext.java:18-32`
- **改动说明**：Phase 4 注释预留的 `ShopSystem shop` 字段落地（经济态随上下文生命周期重建——重开即新商店）；UnitRegistry 仍推迟（§8 开放问题-2），注释同步。旧 4 参构造保留委托（自建默认 ShopSystem），新 5 参为生产路径。
- **代码**（修改前逐字摘自 RunContext.java:18-32）：

```java
修改前：
 * <p>Phase 5 预留字段位（本期仅注释声明，不建字段）：{@code ShopSystem shop} / {@code UnitRegistry registry}。
 */
public final class RunContext {
    private final Player player;
    private final RunState runState;
    private final GameData gameData;
    private final RandomGenerator rng;
    private BattleState battleState;

    public RunContext(Player player, RunState runState, GameData gameData, RandomGenerator rng) {
        this.player = Objects.requireNonNull(player, "player 不能为 null");
        this.runState = Objects.requireNonNull(runState, "runState 不能为 null");
        this.gameData = Objects.requireNonNull(gameData, "gameData 不能为 null");
        this.rng = Objects.requireNonNull(rng, "rng 不能为 null");
    }
```

```java
修改后：
 * <p>Phase 5：{@code ShopSystem shop} 已落地（Phase 4 预留字段位兑现）；
 * {@code UnitRegistry} 继续推迟——GDD 无全局池/池耗尽机制可承载（见实施文档 §8 开放问题-2）。
 */
public final class RunContext {
    private final Player player;
    private final RunState runState;
    private final GameData gameData;
    private final RandomGenerator rng;
    private final com.voidvvv.kz_auto_chess_n.systems.ShopSystem shop;
    private BattleState battleState;

    /** 兼容构造（存量测试）：自建默认商店（槽位全空） */
    public RunContext(Player player, RunState runState, GameData gameData, RandomGenerator rng) {
        this(player, runState, gameData, rng, new com.voidvvv.kz_auto_chess_n.systems.ShopSystem());
    }

    public RunContext(Player player, RunState runState, GameData gameData, RandomGenerator rng,
                      com.voidvvv.kz_auto_chess_n.systems.ShopSystem shop) {
        this.player = Objects.requireNonNull(player, "player 不能为 null");
        this.runState = Objects.requireNonNull(runState, "runState 不能为 null");
        this.gameData = Objects.requireNonNull(gameData, "gameData 不能为 null");
        this.rng = Objects.requireNonNull(rng, "rng 不能为 null");
        this.shop = Objects.requireNonNull(shop, "shop 不能为 null");
    }

    public com.voidvvv.kz_auto_chess_n.systems.ShopSystem getShop() { return shop; }
```

> 注：正式实现建议以常规 import 替代全限定名（成稿为凸显跨包引用而全限定；执行时统一 `import com.voidvvv.kz_auto_chess_n.systems.ShopSystem`）。

- **测试要点**：`command/RunContextTest` 增——4 参构造 shop 非空且槽位全 null；5 参构造注入同一实例 `getShop()` 同引用；null shop 抛 NPE。

---

### CP15. RunFlowSystem 重构（StartRun 化 / 判负重试 / 怜悯 / 宝箱流转 / AbandonRun / 删演示名单）

- **类型**：修改类（全文重写，现 154 行）
- **位置**：`core/.../systems/RunFlowSystem.java`
- **改动说明**：本 CP 是流程域收口，多处代码与 CP12（MoveUnit 迁出）/CP13（RunState 新字段）/CP9（ChestSystem）联动：
  - **删除** `DEMO_SEED`（:31）与 `grantDemoRoster`/`grantBenchUnit`（:140-153）——Q6 裁决 A + Q3 裁决 seed 口径；
  - `startNewRun` → `startRun`（StartRun handler 体；轮开始事件 + 商店免费刷新 + 通知行）；
  - `registerHandlers` 缩编为流程五命令（MoveUnit 块删除——代码改动见 §6.CP12；本点叠加：RunFlowSystem 不再注册 MoveUnit/不再持有 `moveUnitExecutor`）；
  - `onBattleOver` 分流胜败（胜局 roll 宝箱 2 RNG）；
  - `continueAfterResult` 拆为 `continueAfterDefeat`（同轮重试 + 怜悯，口径 #8/#10）与 `advanceAfterVictory`（PickChest 后推进/终局）；
  - `tickResult` 仅败局自动推进；
  - 新增 `endRun`（endCause + MasteryCalculator stub，口径 #15）与 AbandonRun handler；
  - `restart` 契约改为"新 seed 新鲜上下文复入 startRun"。
- **代码**（修改后全文；修改前对照 = 现 RunFlowSystem.java 全文，评审者可整文件 diff——关键删除块摘录如下）：

修改前（逐字摘自 RunFlowSystem.java:30-31）：

```java
    /** 演示局固定 seed（口径 #22：重开同 seed 确定性对照；Phase 5 StartRun 命令化后由 UI 域给定） */
    public static final long DEMO_SEED = 42L;
```

修改前（逐字摘自 RunFlowSystem.java:71-81）：

```java
    /**
     * 新开一局（要求新鲜 RunContext：round=1 / phase=SHOPPING 由 RunState 初始态保证）：
     * 发放演示名单（战士/刺客/游侠各 1，1 星，只入备战席）→ beginRound。
     */
    public void startNewRun(RunContext ctx) {
        RunState runState = ctx.getRunState();
        runState.setPhase(GamePhase.SHOPPING);
        ctx.setBattleState(null);
        grantDemoRoster(ctx);
        beginRound(ctx);
    }
```

修改前（逐字摘自 RunFlowSystem.java:112-130）：

```java
    /**
     * 点击继续（横幅 Actor 调用）：round==TOTAL_ROUNDS → RUN_END；
     * 否则 round+1 + battleState=null + phase=SHOPPING + beginRound（敌阵重生成）。
     * 本期胜负统一推进轮次（差异声明 #6：判负同轮重试与怜悯推 Phase 5）。
     */
    public void continueAfterResult(RunContext ctx) {
        RunState runState = ctx.getRunState();
        if (runState.getPhase() != GamePhase.RESULT) {
            return;
        }
        ctx.setBattleState(null); // 战斗实例整体丢弃（双实体语义）
        if (runState.getRound() >= GameBalance.TOTAL_ROUNDS) {
            runState.setPhase(GamePhase.RUN_END);
            return;
        }
        runState.advanceRound();
        runState.setPhase(GamePhase.SHOPPING);
        beginRound(ctx);
    }
```

```java
修改后（全文）：
package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.command.AbandonRunCommand;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.PickChestCommand;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.command.StartBattleCommand;
import com.voidvvv.kz_auto_chess_n.command.StartRunCommand;
import com.voidvvv.kz_auto_chess_n.command.SurrenderCommand;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.BattleOutcome;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.ChestOffer;
import com.voidvvv.kz_auto_chess_n.entities.ChestOption;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.RunEndCause;
import com.voidvvv.kz_auto_chess_n.entities.RunState;
import com.voidvvv.kz_auto_chess_n.entities.Side;

import java.util.Objects;

/**
 * 局内流程守卫（architecture §五）：阶段推进 / 轮开始事件 / 宝箱流转 / 怜悯 / 五个流程命令 handler。
 *
 * <p>系统行为不经命令队列（input §7.1）——阶段推进由 BattleScreen 在逻辑 tick 内观察
 * {@code isOver} 后委托。handler 拆分（input §6.1）：经营归 ShopSystem、名单归 RosterSystem、
 * 穿脱归 EquipmentSystem（MoveUnit 注册块已迁 RosterSystem，见 §6.CP12）。
 * 零 Gdx（JUnit 零后端前提不破）。Q6 裁决：演示名单已删，兵源 = 商店自购。
 */
public final class RunFlowSystem {

    private final WaveGenerator waveGenerator = new WaveGenerator();
    private final BattleSystem battleSystem = new BattleSystem();
    private final ChestSystem chestSystem = new ChestSystem();
    private final MasteryCalculator masteryCalculator;
    /** RESULT 横幅已停留秒数（onBattleOver 归零；仅败局自动推进消费） */
    private float resultTimer;

    public RunFlowSystem() {
        this(MasteryCalculator.GDD_BASIC);
    }

    /** 注入式熟练度结算（Q5 裁决：纯函数 stub，Phase 6 接档案域换实现） */
    public RunFlowSystem(MasteryCalculator masteryCalculator) {
        this.masteryCalculator = Objects.requireNonNull(masteryCalculator, "masteryCalculator 不能为 null");
    }

    /**
     * 注册流程命令 handler（门控矩阵 architecture §5.2）：
     * StartRun 仅新鲜上下文 / StartBattle 仅 SHOPPING / Surrender 仅 BATTLE /
     * PickChest 仅 RESULT 且有未领宝箱 / AbandonRun 仅 SHOPPING+BATTLE（口径 #14）。
     */
    public void registerHandlers(CommandManager manager) {
        manager.registerHandler(StartRunCommand.class, (cmd, ctx) -> {
            StartRunCommand start = (StartRunCommand) cmd;
            RunState runState = ctx.getRunState();
            if (runState.isRunStarted() || runState.getRound() != 1
                    || runState.getPhase() != GamePhase.SHOPPING
                    || start.getSeed() != runState.getSeed()
                    || !start.getSceneId().equals(runState.getSceneId())) {
                return false; // 非新鲜上下文或装配点错位（口径 #11，静默防线）
            }
            startRun(ctx);
            return true;
        });
        manager.registerHandler(StartBattleCommand.class, (cmd, ctx) -> {
            RunState runState = ctx.getRunState();
            if (runState.getPhase() != GamePhase.SHOPPING) {
                return false;
            }
            BattleState state = battleSystem.startBattle(ctx.getPlayer(), runState.getEnemyWave(),
                    ctx.getGameData(), ctx.getRng(), runState.getIdIssuer()); // 零棋子允许开战
            ctx.setBattleState(state);
            runState.setPhase(GamePhase.BATTLE);
            return true;
        });
        manager.registerHandler(SurrenderCommand.class, (cmd, ctx) -> {
            if (ctx.getRunState().getPhase() != GamePhase.BATTLE || ctx.getBattleState() == null) {
                return false;
            }
            ctx.getBattleState().finish(BattleOutcome.ENEMY_WIN); // 幂等（finish 首个结局生效）
            return true;
        });
        manager.registerHandler(PickChestCommand.class, (cmd, ctx) -> {
            RunState runState = ctx.getRunState();
            ChestOffer offer = runState.getPendingChest();
            if (runState.getPhase() != GamePhase.RESULT || offer == null) {
                return false;
            }
            ChestOption option = offer.optionAt(((PickChestCommand) cmd).getOptionIndex());
            if (option == null) {
                return false;
            }
            runState.addNotice(chestSystem.apply(option, ctx.getPlayer(),
                    runState.getIdIssuer(), ctx.getGameData()));
            advanceAfterVictory(ctx); // 领取即推进（唯一出口，口径 #9）
            return true;
        });
        manager.registerHandler(AbandonRunCommand.class, (cmd, ctx) -> {
            GamePhase phase = ctx.getRunState().getPhase();
            if (phase != GamePhase.SHOPPING && phase != GamePhase.BATTLE) {
                return false;
            }
            endRun(ctx, RunEndCause.ABANDONED);
            return true;
        });
    }

    /**
     * 新开一局（StartRun handler 体；测试与 restart 直调）。要求新鲜 RunContext
     * （round=1 / phase=SHOPPING / runStarted=false 由 RunState 初始态保证）。
     * 轮开始事件：敌阵生成（RNG=杂兵数）+ 商店免费刷新（RNG=10）+ 通知行。
     */
    public void startRun(RunContext ctx) {
        RunState runState = ctx.getRunState();
        runState.markRunStarted();
        runState.setPhase(GamePhase.SHOPPING);
        ctx.setBattleState(null);
        beginRound(ctx);
        ctx.getShop().reroll(runState.getRound(), ctx.getGameData(), ctx.getRng());
        runState.addNotice("第 " + runState.getRound() + " 轮开始（商店免费刷新）");
    }

    /** 轮开始事件子集（Phase 2 口径）：enemyWave = generateEnemyWave(...)（RNG 消耗 = 杂兵数）。 */
    public void beginRound(RunContext ctx) {
        RunState runState = ctx.getRunState();
        java.util.List<com.voidvvv.kz_auto_chess_n.entities.WaveSpec> wave = waveGenerator.generateEnemyWave(
                runState.getRound(), runState.getSceneId(), ctx.getGameData(), ctx.getRng());
        runState.setEnemyWave(wave);
    }

    /**
     * BATTLE→RESULT（BattleScreen 观察 isOver 后委托，口径 #7）：
     * 胜局 roll 宝箱（RNG=2，pendingChest 非空）；败局（全灭/超时/投降）不 roll 零消耗。
     * battleState 保留供横幅读 outcome。
     */
    public void onBattleOver(RunContext ctx) {
        if (ctx.getRunState().getPhase() != GamePhase.BATTLE) {
            return;
        }
        resultTimer = 0f;
        ctx.getRunState().setPhase(GamePhase.RESULT);
        if (ctx.getBattleState().getOutcome() == BattleOutcome.PLAYER_WIN) {
            ctx.getRunState().setPendingChest(chestSystem.roll(
                    ctx.getRunState().getRound(), ctx.getGameData(), ctx.getRng()));
        }
    }

    /** RESULT 横幅计时：仅败局自动推进（胜局必须 PickChest，无自动出口——口径 #9） */
    public void tickResult(RunContext ctx, float dt) {
        if (ctx.getRunState().getPhase() != GamePhase.RESULT
                || ctx.getRunState().getPendingChest() != null) {
            return;
        }
        resultTimer += dt;
        if (resultTimer >= GameBalance.RESULT_BANNER_SECONDS) {
            continueAfterDefeat(ctx);
        }
    }

    /**
     * 败局继续（横幅点击或自动）：同轮重试（GDD §2.2 1C-R——round/敌阵/商店全不变）
     * + 怜悯（GDD §3.2：上场数>0 才计数，第 3 败起每轮 ≤3 金——口径 #8/#10）。
     */
    public void continueAfterDefeat(RunContext ctx) {
        RunState runState = ctx.getRunState();
        if (runState.getPhase() != GamePhase.RESULT || runState.getPendingChest() != null) {
            return;
        }
        int deployedCount = playerSideCount(ctx.getBattleState());
        ctx.setBattleState(null); // 战斗实例整体丢弃（双实体语义）
        applyMercy(ctx, deployedCount);
        runState.setPhase(GamePhase.SHOPPING);
    }

    /**
     * 胜局推进（PickChest handler 结算奖励后调用）：round==25 → RUN_END(COMPLETED)；
     * 否则 round+1 + 怜悯双清零 + 敌阵重生成 + 商店免费刷新（architecture §5.1"新轮进入"）。
     */
    public void advanceAfterVictory(RunContext ctx) {
        RunState runState = ctx.getRunState();
        ctx.setBattleState(null);
        runState.setPendingChest(null);
        if (runState.getRound() >= GameBalance.TOTAL_ROUNDS) {
            endRun(ctx, RunEndCause.COMPLETED); // 第 25 轮领箱后通关（architecture §4.4 回放流终点）
            return;
        }
        runState.advanceRound();
        runState.setMercyLossCount(0); // 新轮重计（§5.1 关键区分：重试不清、新轮清）
        runState.setMercyGoldThisRound(0);
        runState.setPhase(GamePhase.SHOPPING);
        beginRound(ctx);
        ctx.getShop().reroll(runState.getRound(), ctx.getGameData(), ctx.getRng());
        runState.addNotice("第 " + runState.getRound() + " 轮开始（商店免费刷新）");
    }

    /** RUN_END 进入：endCause + 熟练度结算 stub（Q5 裁决；Phase 6 接档案域） */
    private void endRun(RunContext ctx, RunEndCause cause) {
        RunState runState = ctx.getRunState();
        ctx.setBattleState(null);
        runState.setPendingChest(null);
        runState.setEndCause(cause);
        runState.setMasteryAwarded(masteryCalculator.settle(cause, runState.getRound()));
        runState.setPhase(GamePhase.RUN_END);
    }

    /** RUN_END 重开：调用方（Screen 装配点）已用 UI 边界新 seed 组装新鲜 RunContext，复入 startRun */
    public void restart(RunContext ctx) {
        startRun(ctx);
    }

    /** 怜悯：零棋子战败不计（GDD §3.2 防刷）；第 3 败起且本轮怜悯金 <3 → +1 金 */
    private void applyMercy(RunContext ctx, int deployedCount) {
        if (deployedCount == 0) {
            return;
        }
        RunState runState = ctx.getRunState();
        runState.setMercyLossCount(runState.getMercyLossCount() + 1);
        if (runState.getMercyLossCount() >= GameBalance.MERCY_START_LOSS
                && runState.getMercyGoldThisRound() < GameBalance.MERCY_CAP_PER_ROUND) {
            runState.setMercyGoldThisRound(runState.getMercyGoldThisRound() + 1);
            ctx.getPlayer().addGold(1);
            runState.addNotice("怜悯金币 +1（连败 " + runState.getMercyLossCount() + "）");
        }
    }

    /** 刚结束战斗的玩家侧单位总数（含已清扫亡者——零棋子判定，口径 #8） */
    private static int playerSideCount(BattleState state) {
        int count = 0;
        for (BattleUnit unit : state.getUnits()) {
            if (unit.getSide() == Side.PLAYER) {
                count++;
            }
        }
        return count;
    }
}
```

（配套新建 `systems/MasteryCalculator.java`）：

```java
package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.entities.RunEndCause;

/**
 * 熟练度结算纯函数接口（GDD §8.1：每已达 1 轮 +3；放弃远征同口径按已达轮数——GDD §2.1）。
 * Phase 5 stub（Q5 裁决）：产出暂存 RunState.masteryAwarded，Phase 6 接档案域持久化。
 */
@FunctionalInterface
public interface MasteryCalculator {

    /** cause 目前不参与基线公式（预留：通关加成/放弃折扣等未来口径） */
    int settle(RunEndCause cause, int roundsReached);

    MasteryCalculator GDD_BASIC = new MasteryCalculator() {
        @Override
        public int settle(RunEndCause cause, int roundsReached) {
            return roundsReached * 3;
        }
    };
}
```

- **测试要点**：`systems/RunFlowSystemTest` 大改（Q6 裁决明示"相关断言改口径"）——
  - 删：`startNewRunGrantsDemoRoster`（演示名单断言作废）；DEMO_SEED 引用改本地 `TEST_SEED = 42L`；
  - 改：`beginRoundDeterministicWithKnownRngCost` 的 RNG 消耗断言 = enemyCount(1) + 10（商店免费刷新）；`continueAfterResultAdvancesRound` 拆为败局重试（round/敌阵不变/无免费刷新——`getConsumedCount()` 不变）与胜局推进两测；`round25LeadsToRunEnd` 改为胜局路径（需造必胜局：用强模板 deployed）；`tickResultAutoAdvancesAfterBannerSeconds` 仅败局；`restartReplaysIdenticalEventStreams` 改用 startRun/restart 新口径；
  - 增：StartRun 命令路径（manager.addCommand(StartRunCommand) 后 executeAll 生效）；重复 StartRun 第二次 false；seed/sceneId 不一致 false；胜局 RESULT `pendingChest` 非空且 tickResult 不推进；PickChest 三个选项各自入账/入包后 advance；零棋子战败 → mercyLossCount 仍 0、金不变；有上场连败 3 次起 +1 金、每轮封顶 3；新轮双清零；AbandonRun 在 SHOPPING/BATTLE 生效、RESULT 拒绝，endCause/masteryAwarded 正确（注入自定义 MasteryCalculator 断言调用参数）。

---

### CP16. BattleSystem 装备派生（修正源列表 + passive 落地）

- **类型**：修改类
- **位置**：`core/.../systems/BattleSystem.java:78-87`（玩家侧派生循环）、:235-243（deriveUnit）
- **改动说明**：Q1 裁决"StartBattle 派生插装备修正源"落点——Phase 3 Q4 预留插点（BattleSystem.java:237 现为 `Collections.singletonList(synergies)`）。两通道：stat 通道进 `deriveBaseline` 的修正源列表（羁绊 + 装备）；passiveStatus 通道在布阵完成后经 `StatusSystem.apply` 全参重载落地（REGEN 常驻，sourceId=-1，duration=∞）。玩家侧 roster 序与 BattleUnit 序按索引对齐（同一扫描循环产出，确定性）。**零新增 RNG**。
- **代码**（修改前逐字摘自 BattleSystem.java:76-87）：

```java
修改前：
        List<BattleUnit> units = new ArrayList<BattleUnit>();
        // 玩家侧：部署表扫描序 y↑x↑（= getDeployedUnits 序，坐标同源）
        for (int y = 4; y <= 6; y++) {
            for (int x = 0; x < GameBalance.BOARD_COLS; x++) {
                Unit unit = player.deployedAt(x, y);
                if (unit == null) {
                    continue;
                }
                units.add(deriveUnit(idIssuer.nextId(), unit.getTemplate(), unit.getStar(),
                        Side.PLAYER, 1.0f, playerSynergies, data, x, y));
            }
        }
```

```java
修改后：
        List<BattleUnit> units = new ArrayList<BattleUnit>();
        List<Unit> rosterDeployed = player.getDeployedUnits(); // 与上方扫描同序（y↑x↑，口径 #16）
        // 玩家侧：部署表扫描序 y↑x↑（= getDeployedUnits 序，坐标同源）
        for (int y = 4; y <= 6; y++) {
            for (int x = 0; x < GameBalance.BOARD_COLS; x++) {
                Unit unit = player.deployedAt(x, y);
                if (unit == null) {
                    continue;
                }
                units.add(deriveUnit(idIssuer.nextId(), unit.getTemplate(), unit.getStar(),
                        Side.PLAYER, 1.0f, playerSynergies, data, x, y, unit.getEquipped()));
            }
        }
```

（修改前逐字摘自 BattleSystem.java:95-101）：

```java
修改前：
        BattleState state = new BattleState(units, rng, playerSynergies, enemySynergies);
        for (BattleUnit unit : units) {
            state.placeUnit(unit, unit.getGridX(), unit.getGridY());
        }
        applyOpeningEffects(state, playerSynergies, Side.PLAYER);
        applyOpeningEffects(state, enemySynergies, Side.ENEMY);
```

```java
修改后：
        BattleState state = new BattleState(units, rng, playerSynergies, enemySynergies);
        for (BattleUnit unit : units) {
            state.placeUnit(unit, unit.getGridX(), unit.getGridY());
        }
        applyEquipmentPassives(state, rosterDeployed); // 装备被动（龙心类）：索引对齐玩家侧前 N 个 BattleUnit
        applyOpeningEffects(state, playerSynergies, Side.PLAYER);
        applyOpeningEffects(state, enemySynergies, Side.ENEMY);
```

（修改前逐字摘自 BattleSystem.java:235-243）：

```java
修改前：
    private static BattleUnit deriveUnit(int id, UnitData template, int star, Side side, float scale,
                                         SynergySnapshot synergies, GameData data, int x, int y) {
        List<StatModifierSource> sources = Collections.<StatModifierSource>singletonList(synergies);
        BattleStats baseStats = StatPipeline.deriveBaseline(template, star, scale, sources);
        BattleUnit unit = new BattleUnit(id, template, star, side,
                data.getSkill(template.getSkillId()), baseStats); // 加载校验保证技能存在
        unit.setPosition(x, y);
        return unit;
    }
```

```java
修改后：
    private static BattleUnit deriveUnit(int id, UnitData template, int star, Side side, float scale,
                                         SynergySnapshot synergies, GameData data, int x, int y,
                                         List<Equipment> equipped) {
        List<StatModifierSource> sources = java.util.Arrays.asList(
                synergies, EquipmentStats.of(equipped)); // Q4 修正源列表：羁绊（侧全体）+ 装备（单体）
        BattleStats baseStats = StatPipeline.deriveBaseline(template, star, scale, sources);
        BattleUnit unit = new BattleUnit(id, template, star, side,
                data.getSkill(template.getSkillId()), baseStats); // 加载校验保证技能存在
        unit.setPosition(x, y);
        return unit;
    }

    /** 装备被动落地（data_schema §八：装备入口进 StatusSystem 的第二种形态）。
     *  rosterDeployed[i] ↔ units[i]（玩家侧前 N 个，同一扫描序）；REGEN 常驻（duration=∞，sourceId=-1）。 */
    private void applyEquipmentPassives(BattleState state, List<Unit> rosterDeployed) {
        List<BattleUnit> units = state.getUnits();
        for (int i = 0; i < rosterDeployed.size(); i++) {
            for (Equipment item : rosterDeployed.get(i).getEquipped()) {
                EquipmentPassive passive = item.getTemplate().getPassive();
                if (passive == null) {
                    continue;
                }
                statusSystem.apply(state, units.get(i), passive.getType(), passive.getPower(),
                        Float.POSITIVE_INFINITY, -1, passive.getTickInterval());
            }
        }
    }
```

（BattleSystem.java import 段增：`com.voidvvv.kz_auto_chess_n.data.EquipmentPassive`、`com.voidvvv.kz_auto_chess_n.entities.Equipment`；`java.util.Collections` import 若无他用可移除。敌方 WaveSpec 派生调用（:91-92）同步补空装备参数 `java.util.Collections.<Equipment>emptyList()`。）

- **测试要点**：`systems/BattleSystemTest` 增——穿戴铁剑（attack PCT20）的 1 星模板派生后 `getBaseStats().get(ATTACK)` = 模板值 ×（1+0.20）吻合（先加后乘公式，无羁绊时）；龙心（hp ADD400 + REGEN 0.02/5s）派生后 HP +400 且单位 statuses 含 REGEN（interval 5）；卸下后重开战属性回落（穿脱影响即时性经"每战重派生"保证）；敌我同装零串扰（敌方无装备路径）。

---

### CP17. BoardGeometry 增 ③⑤⑦⑧⑨ 区常量与命中判定

- **类型**：修改类
- **位置**：`core/.../render/board/BoardGeometry.java:22-29`（BENCH 常量段后）
- **改动说明**：render §九坐标表 Phase 4 遗留区落位（GDD 用语 装备背包/羁绊面板/出售区/商店栏/事件通知 ↔ INVENTORY/SYNERGY/SELL_ZONE/SHOP_BAR/NOTIFY）。⑨ 区 y 230→244 避让 ③ 底边（差异声明 #4）。
- **代码**（修改前逐字摘自 BoardGeometry.java:21-29）：

```java
修改前：
    /** ② 备战席 3×3（槽 36×40） */
    public static final int BENCH_X = 20;
    public static final int BENCH_Y = 48;
    public static final int BENCH_W = 108;
    public static final int BENCH_H = 120;

    public static final int CELL = 32;
    public static final int BENCH_SLOT_W = 36;
    public static final int BENCH_SLOT_H = 40;
```

```java
修改后：
    /** ② 备战席 3×3（槽 36×40） */
    public static final int BENCH_X = 20;
    public static final int BENCH_Y = 48;
    public static final int BENCH_W = 108;
    public static final int BENCH_H = 120;

    /** ③ 装备背包 3×2（UI 域 InventoryPanel 定位同源；槽 36×50） */
    public static final int INVENTORY_X = 20;
    public static final int INVENTORY_Y = 140;
    public static final int INVENTORY_W = 108;
    public static final int INVENTORY_H = 100;
    /** ⑤ 羁绊面板（UI 域） */
    public static final int SYNERGY_X = 508;
    public static final int SYNERGY_Y = 48;
    public static final int SYNERGY_W = 112;
    public static final int SYNERGY_H = 144;
    /** ⑦ 出售区（棋盘域拖拽终点，仅 SHOPPING） */
    public static final int SELL_ZONE_X = 564;
    public static final int SELL_ZONE_Y = 246;
    public static final int SELL_ZONE_W = 56;
    public static final int SELL_ZONE_H = 46;
    /** ⑧ 商店栏（UI 域 ShopBar，全宽 640） */
    public static final int SHOP_BAR_Y = 296;
    public static final int SHOP_BAR_H = 64;
    /** ⑨ 事件通知小窗（render §九原值 y=230 与 ③ 底边 240 重叠 10px——差异声明 #4，改 244 起） */
    public static final int NOTIFY_X = 20;
    public static final int NOTIFY_Y = 244;
    public static final int NOTIFY_W = 128;
    public static final int NOTIFY_H = 46;

    public static final int CELL = 32;
    public static final int BENCH_SLOT_W = 36;
    public static final int BENCH_SLOT_H = 40;
    public static final int INVENTORY_SLOT_W = 36;
    public static final int INVENTORY_SLOT_H = 50;
```

（BoardGeometry 方法段尾追加）：

```java
    /** 像素点是否在 ⑦ 出售区内（boardProcessor 拖拽终点判定） */
    public static boolean isInSellZone(int px, int py) {
        return px >= SELL_ZONE_X && px < SELL_ZONE_X + SELL_ZONE_W
                && py >= SELL_ZONE_Y && py < SELL_ZONE_Y + SELL_ZONE_H;
    }

    /** ③ 背包槽中心（3 列 × 2 行；row 0 在下——scene2d y 向上） */
    public static int[] inventorySlotCenter(int slotIndex) {
        int col = slotIndex / 2;
        int row = slotIndex % 2;
        return new int[]{INVENTORY_X + col * INVENTORY_SLOT_W + INVENTORY_SLOT_W / 2,
                INVENTORY_Y + INVENTORY_H - row * INVENTORY_SLOT_H - INVENTORY_SLOT_H / 2};
    }
```

- **测试要点**：`render/BoardGeometryTest` 增——`isInSellZone` 四角/界外；`inventorySlotCenter(0)` 在 ③ 区内且 6 槽互异；⑨ 区顶边 ≥ ③ 区底边（布局冲突回归断言）；各区常量与 render §九表逐项对照（±4px 容差内，⑨ 例外已声明）。

---

### CP18. Assets 双素材层（Fusion Pixel 字体 + RealArt 真图集）与素材入库

- **类型**：修改类 + 新建文件 + 新建资产
- **位置**：修改 `core/.../render/Assets.java`（全文，48 行）；新建 `core/.../render/RealArt.java`；资产 `assets/font/`、`assets/art/units/`
- **改动说明**：Q4 裁决 B。字体：`Assets.font()` 换载 `font/fusion_pixel_12.fnt`（Hiero 生成），**文件缺失回退内置默认**（守卫沿用 Phase 4"测试不依赖素材存在"做法——headless JUnit 不触 GL）；调用方零改动（ShoppingHud/TopBar/BattleRenderer 等 `assets.font()` 原样）。真图集：`RealArt` 按 key 懒加载 `art/units/{key}.png`（Nearest），miss 缓存避免每帧磁盘 stat；`Assets.region` 先真后占位（Phase 4 注释"真图集接入点在 Phase 5+"兑现）。itch 小人先铺 3 个可购棋子（unit_warrior_01 / unit_ranger_01 / unit_assassin_01）各 12 帧（idle2/walk2/attack3/cast2/death3，帧数须与 `PlaceholderKeys.frameCount` 对齐）。
- **代码**（修改后 `Assets.java` 关键段；修改前逐字摘自 Assets.java:15-47——全文对照）：

```java
修改前（全文）：
public final class Assets {
    private final PlaceholderArt art;
    private BitmapFont font;

    public Assets(PlaceholderArt art) {
        this.art = Objects.requireNonNull(art, "art 不能为 null");
    }

    /** @throws IllegalStateException key 未生成（占位图集与 PlaceholderKeys 对账后仍缺 = 程序错误） */
    public TextureRegion region(String key) {
        TextureRegion region = art.region(key);
        if (region == null) {
            throw new IllegalStateException("资源缺 key: " + key);
        }
        return region;
    }

    /** 内置默认字体（懒构造单例；dispose 后重新获取会重建） */
    public BitmapFont font() {
        if (font == null) {
            font = new BitmapFont();
        }
        return font;
    }

    /** Main.dispose 调：字体与占位 Texture 全弃 */
    public void dispose() {
        if (font != null) {
            font.dispose();
            font = null;
        }
        art.dispose();
    }
}
```

```java
修改后（全文）：
public final class Assets {
    private final PlaceholderArt art;
    /** 真素材层（Q4=B：itch 小人；miss 自动回退占位） */
    private final RealArt realArt = new RealArt();
    private BitmapFont font;

    public Assets(PlaceholderArt art) {
        this.art = Objects.requireNonNull(art, "art 不能为 null");
    }

    /** 真素材优先、未命中落占位；仍缺抛 IllegalStateException（= 程序错误） */
    public TextureRegion region(String key) {
        TextureRegion real = realArt.region(key);
        if (real != null) {
            return real;
        }
        TextureRegion region = art.region(key);
        if (region == null) {
            throw new IllegalStateException("资源缺 key: " + key);
        }
        return region;
    }

    /** Fusion Pixel 12px 位图字体（Q4=B；文件缺失回退内置默认——headless 测试不依赖素材存在） */
    public BitmapFont font() {
        if (font == null) {
            com.badlogic.gdx.files.FileHandle fontFile =
                    com.badlogic.gdx.Gdx.files.internal("font/fusion_pixel_12.fnt");
            font = fontFile.exists() ? new BitmapFont(fontFile, false) : new BitmapFont();
        }
        return font;
    }

    /** Main.dispose 调：字体、真素材与占位 Texture 全弃 */
    public void dispose() {
        if (font != null) {
            font.dispose();
            font = null;
        }
        realArt.dispose();
        art.dispose();
    }
}
```

（新建 `render/RealArt.java`）：

```java
package com.voidvvv.kz_auto_chess_n.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 真素材层（Q4=B：itch 免费小人替换 2~3 棋子验证精灵动画流水线）。
 * key → assets/art/units/{key}.png 懒加载（GL 线程），未命中返回 null（Assets 落占位）。
 * miss 结果缓存（避免每帧磁盘 stat）；pathOf 纯函数可 headless 测。
 */
public final class RealArt {
    static final String ROOT = "art/units/";

    private final Map<String, TextureRegion> cache = new HashMap<String, TextureRegion>();
    private final Set<String> misses = new HashSet<String>();
    private final List<Texture> textures = new ArrayList<Texture>();

    /** key → 素材相对路径（纯函数，测试用） */
    public static String pathOf(String key) {
        return ROOT + key + ".png";
    }

    /** 懒加载查 key；文件不存在返回 null（回退占位；miss 缓存） */
    public TextureRegion region(String key) {
        TextureRegion cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        if (misses.contains(key)) {
            return null;
        }
        FileHandle file = Gdx.files.internal(pathOf(key));
        if (!file.exists()) {
            misses.add(key);
            return null;
        }
        Texture texture = new Texture(file);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        textures.add(texture);
        cached = new TextureRegion(texture);
        cache.put(key, cached);
        return cached;
    }

    public void dispose() {
        for (Texture texture : textures) {
            texture.dispose();
        }
        textures.clear();
        cache.clear();
        misses.clear();
    }
}
```

（素材入库清单——`TODO(executor): 素材为二进制产出，执行期完成采购/生成`）：

| 文件 | 来源与要求 |
|------|-----------|
| `assets/font/fusion_pixel_12.fnt` + `fusion_pixel_12.png` | Fusion Pixel Font（TakWolf，OFL）12px 经 Hiero 导出（ASCII+CJK 常用集，Nearest） |
| `assets/font/OFL-LICENSE.txt` + `assets/font/README.md` | OFL 许可证原文 + 来源/版本/生成参数记录 |
| `assets/art/units/unit_warrior_01_{anim}_{frame}.png` 等 3 套 ×12 帧 | itch.io 免费小人包（逐帧 32×32、透明底、右向）；`README.md` 记作者/商店链接/许可（须允许商用或本项目用途） |
| `assets/art/units/README.md` | 帧命名约定（= PlaceholderKeys.unitFrame）、帧数表、来源记录 |

- **测试要点**：`render/RealArtTest`——`pathOf("unit_warrior_01_idle_0") == "art/units/unit_warrior_01_idle_0.png"`；Gdx 依赖方法（region）不在 headless 测（沿 PlaceholderArt 先例：验收走 lwjgl3:run 手验——真素材加载/回退/中文渲染）。Assets 类同不 headless 测；守卫逻辑（exists 回退）以代码评审 + lwjgl3 双态手验（临时改名 font 目录验证回退）。

---

### CP19. BoardInputProcessor：出售区拖拽终点 + 点击回调

- **类型**：修改类
- **位置**：`core/.../input/BoardInputProcessor.java:57-63`（构造器）、:103-116（touchUp）
- **改动说明**：⑦ 出售区作为拖拽终点产出 `SellUnitCommand`（input §2.4：拖到出售区卖出）；死区内松手的点击接 `unitClickListener`（BattleScreen 注入：装备待定态落点或详情面板——input §2.5 配套规则"翻译层维护轻量 pending 状态"）。模态阻断位零改动（`modalBlocked` 供应者换真实源在 §6.CP29）。
- **代码**（修改前逐字摘自 BoardInputProcessor.java:50-63）：

```java
修改前：
    private final Viewport boardViewport;
    private final CommandManager commandManager;
    private final Supplier<RunContext> context;
    private final BooleanSupplier modalBlocked;
    private final Map<Integer, DragContext> drags = new HashMap<Integer, DragContext>();
    private final Vector2 touch = new Vector2(); // 复用（unproject 输出，零分配）

    public BoardInputProcessor(Viewport boardViewport, CommandManager commandManager,
                               Supplier<RunContext> context, BooleanSupplier modalBlocked) {
        this.boardViewport = boardViewport;
        this.commandManager = commandManager;
        this.context = context;
        this.modalBlocked = modalBlocked;
    }
```

```java
修改后：
    private final Viewport boardViewport;
    private final CommandManager commandManager;
    private final Supplier<RunContext> context;
    private final BooleanSupplier modalBlocked;
    /** 死区内松手 = 点击棋子的回调（Phase 5：详情面板 / 装备待定态落点；null = 无监听） */
    private final java.util.function.IntConsumer unitClickListener;
    private final Map<Integer, DragContext> drags = new HashMap<Integer, DragContext>();
    private final Vector2 touch = new Vector2(); // 复用（unproject 输出，零分配）

    public BoardInputProcessor(Viewport boardViewport, CommandManager commandManager,
                               Supplier<RunContext> context, BooleanSupplier modalBlocked) {
        this(boardViewport, commandManager, context, modalBlocked, null);
    }

    public BoardInputProcessor(Viewport boardViewport, CommandManager commandManager,
                               Supplier<RunContext> context, BooleanSupplier modalBlocked,
                               java.util.function.IntConsumer unitClickListener) {
        this.boardViewport = boardViewport;
        this.commandManager = commandManager;
        this.context = context;
        this.modalBlocked = modalBlocked;
        this.unitClickListener = unitClickListener;
    }
```

（修改前逐字摘自 BoardInputProcessor.java:103-116）：

```java
修改前：
    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        DragContext drag = drags.remove(pointer);
        if (drag == null) {
            return false;
        }
        if (!drag.dragging) {
            return true; // 死区内松手 = 点击：本期无命令（Phase 5 详情面板挂点位）
        }
        PlacementTarget target = dropTargetAt(drag.currentX, drag.currentY);
        if (target != null) {
            commandManager.addCommand(new MoveUnitCommand(drag.unitId, target));
        } // 非法落点：不产生命令，回弹由 ghost 消失自然实现
        return true;
    }
```

```java
修改后：
    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        DragContext drag = drags.remove(pointer);
        if (drag == null) {
            return false;
        }
        if (!drag.dragging) {
            if (unitClickListener != null) {
                unitClickListener.accept(drag.unitId); // 死区内松手 = 点击（input §2.4：查看详情/装备落点）
            }
            return true;
        }
        if (BoardGeometry.isInSellZone((int) drag.currentX, (int) drag.currentY)) {
            commandManager.addCommand(new SellUnitCommand(drag.unitId)); // ⑦ 出售区（GDD §3.6）
            return true;
        }
        PlacementTarget target = dropTargetAt(drag.currentX, drag.currentY);
        if (target != null) {
            commandManager.addCommand(new MoveUnitCommand(drag.unitId, target));
        } // 非法落点：不产生命令，回弹由 ghost 消失自然实现
        return true;
    }
```

（渲染只读暴露段追加——插于 `getDropPreview` 之后）：

```java
    /** 拖拽悬停是否在 ⑦ 出售区（渲染金红高亮用） */
    public boolean isDropOnSellZone() {
        DragContext drag = dropContext();
        return drag != null && BoardGeometry.isInSellZone((int) drag.currentX, (int) drag.currentY);
    }
```

（import 段增：`com.voidvvv.kz_auto_chess_n.command.SellUnitCommand`。类 Javadoc "（出售区 Phase 5）"字样同步删除。）

- **测试要点**：`input/BoardInputProcessorTest`（新建，headless 仿 FitViewport 口径——unproject 需真 viewport，改用 MockViewport/正交单位换算先例若无可注入 fake；不可行则将该类的纯判定逻辑经 `BoardGeometry.isInSellZone` 覆盖 + 集成测走 lwjgl3 手验清单）——出售区松手入队 `SellUnitCommand`（历史断言 `manager.getHistory()`）；死区松手触发 `unitClickListener` 且零命令；悬停出售区 `isDropOnSellZone()` true。

---

### CP20. BattleRenderer：⑦ 出售区绘制与拖拽高亮

- **类型**：修改类
- **位置**：`core/.../render/board/BattleRenderer.java:189-195`（drawShopping 尾）、:320-340（drawDropOverlay 前段）
- **改动说明**：出售区为棋盘域自绘（render §九⑦ 行"棋盘域"——input §2.5 渲染约束同构）；拖拽悬停出售区金红高亮提示可卖出。引导文案 SHOPPING_HINT 同步提及出售区。
- **代码**（修改前逐字摘自 BattleRenderer.java:189-195）：

```java
修改前：
        for (WaveSpec spec : ctx.getRunState().getEnemyWave()) { // 敌阵侦察虚影（红框 + 半透明，P1b）
            int[] center = BoardGeometry.cellCenter(spec.getGridX(), spec.getGridY());
            drawUnitFrame(batch, spec.getTemplate().getId(), PlaceholderKeys.ANIM_IDLE, 0,
                    center[0], center[1], true, SideColors.ENEMY_PREVIEW_ALPHA, SideColors.ENEMY);
        }
        drawShoppingHint(batch);
    }
```

```java
修改后：
        for (WaveSpec spec : ctx.getRunState().getEnemyWave()) { // 敌阵侦察虚影（红框 + 半透明，P1b）
            int[] center = BoardGeometry.cellCenter(spec.getGridX(), spec.getGridY());
            drawUnitFrame(batch, spec.getTemplate().getId(), PlaceholderKeys.ANIM_IDLE, 0,
                    center[0], center[1], true, SideColors.ENEMY_PREVIEW_ALPHA, SideColors.ENEMY);
        }
        drawSellZone(batch);
        drawShoppingHint(batch);
    }

    /** ⑦ 出售区（render §九；棋盘域自绘，仅 SHOPPING 路径可达） */
    private void drawSellZone(SpriteBatch batch) {
        TextureRegion panel = assets.region(PlaceholderKeys.PANEL_9SLICE);
        batch.setColor(0.45f, 0.32f, 0.16f, 0.9f);
        batch.draw(panel, BoardGeometry.SELL_ZONE_X, BoardGeometry.SELL_ZONE_Y,
                BoardGeometry.SELL_ZONE_W, BoardGeometry.SELL_ZONE_H);
        batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
        assets.font().draw(batch, "SELL", BoardGeometry.SELL_ZONE_X + 10f, BoardGeometry.SELL_ZONE_Y + 28f);
    }
```

（修改前逐字摘自 BattleRenderer.java:320-333；SHOPPING_HINT 常量 :38 同步改 `"DRAG UNITS TO BOARD · DRAG TO SELL · THEN FIGHT"`）：

```java
修改前：
    private void drawDropOverlay(SpriteBatch batch, RunContext ctx, BoardInputProcessor input) {
        if (input == null || !input.isDragging()) {
            return;
        }
        TextureRegion white = assets.region(PlaceholderKeys.WHITE);
        com.voidvvv.kz_auto_chess_n.command.PlacementTarget preview = input.getDropPreview();
        if (preview instanceof com.voidvvv.kz_auto_chess_n.command.PlacementTarget.Cell) {
```

```java
修改后：
    private void drawDropOverlay(SpriteBatch batch, RunContext ctx, BoardInputProcessor input) {
        if (input == null || !input.isDragging()) {
            return;
        }
        TextureRegion white = assets.region(PlaceholderKeys.WHITE);
        if (input.isDropOnSellZone()) { // ⑦ 出售区悬停：金红高亮（可卖出提示）
            batch.setColor(SELL_DROP_TINT);
            batch.draw(white, BoardGeometry.SELL_ZONE_X, BoardGeometry.SELL_ZONE_Y,
                    BoardGeometry.SELL_ZONE_W, BoardGeometry.SELL_ZONE_H);
            batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
            return;
        }
        com.voidvvv.kz_auto_chess_n.command.PlacementTarget preview = input.getDropPreview();
        if (preview instanceof com.voidvvv.kz_auto_chess_n.command.PlacementTarget.Cell) {
```

（常量段 :50-51 后追加）：

```java
    private static final com.badlogic.gdx.graphics.Color SELL_DROP_TINT =
            new com.badlogic.gdx.graphics.Color(1f, 0.8f, 0.25f, 0.45f);  // 出售区悬停金
```

- **测试要点**：绘制本体走 lwjgl3 手验（Phase 4 先例：BattleRenderer 不 headless 测）；`SELL_ZONE` 常量正确性由 CP17 几何测试背书；手验清单记"拖棋子悬停 ⑦ 区金红高亮、松手卖出、回弹"。

---

### CP21. TopBar 完整化（EXP 显示 + 暂停按钮）

- **类型**：修改类（重写，现 36 行）
- **位置**：`core/.../render/ui/TopBar.java`
- **改动说明**：Phase 4 口径 #21"完整 TopBar 随 Phase 5"销账——增经验进度（Lv.7 显 MAX）与暂停按钮（① 区右侧，回调开暂停菜单，Screen 装配见 §6.CP29）。文字变更才 setText（渲染段零分配，既有约定保持）。
- **代码**（修改前逐字摘自 TopBar.java:15-35——核心段；修改后全文）：

```java
修改前：
public final class TopBar extends Group {
    private final Label label;
    private String lastText = "";

    public TopBar(Assets assets) {
        this.label = new Label("", new Label.LabelStyle(assets.font(), Color.WHITE));
        addActor(label);
        setPosition(8f, BoardGeometry.VIRTUAL_H - 18f);
    }

    /** 每帧刷新（值变化才重建字符串） */
    public void refresh(RunContext ctx) {
        int round = ctx.getRunState().getRound();
        String text = "ROUND " + round + "/" + GameBalance.TOTAL_ROUNDS
                + "  GOLD " + ctx.getPlayer().getGold()
                + "  LV " + ctx.getPlayer().getLevel();
        if (!text.equals(lastText)) {
            lastText = text;
            label.setText(text);
        }
    }
}
```

```java
修改后：
public final class TopBar extends Group {

    /** 暂停回调（Screen 实现：开暂停菜单） */
    public interface PauseListener {
        void onPauseRequested();
    }

    private final Label label;
    private final Assets assets;
    private final PauseListener pauseListener;
    private String lastText = "";

    public TopBar(Assets assets, PauseListener pauseListener) {
        this.assets = assets;
        this.pauseListener = pauseListener;
        this.label = new Label("", new Label.LabelStyle(assets.font(), Color.WHITE));
        addActor(label);
        setPosition(8f, BoardGeometry.VIRTUAL_H - 18f);
        Actor pause = new PauseButton();
        pause.setPosition(BoardGeometry.VIRTUAL_W - 56f, BoardGeometry.VIRTUAL_H - 26f);
        addActor(pause);
    }

    /** 每帧刷新（值变化才重建字符串）：轮次 / 金币 / 等级+经验（① 区完整版） */
    public void refresh(RunContext ctx) {
        int round = ctx.getRunState().getRound();
        int need = GameBalance.expToNextLevel(ctx.getPlayer().getLevel());
        String exp = need == 0 ? "MAX" : ctx.getPlayer().getCurrentExp() + "/" + need;
        String text = "ROUND " + round + "/" + GameBalance.TOTAL_ROUNDS
                + "  GOLD " + ctx.getPlayer().getGold()
                + "  LV " + ctx.getPlayer().getLevel() + " (" + exp + ")";
        if (!text.equals(lastText)) {
            lastText = text;
            label.setText(text);
        }
    }

    /** 暂停按钮（自绘，无 Skin——Q4=B） */
    private final class PauseButton extends Actor {
        PauseButton() {
            setSize(48f, 22f);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    pauseListener.onPauseRequested();
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Color old = batch.getColor();
            batch.setColor(0.35f, 0.4f, 0.5f, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            assets.font().draw(batch, "II", getX() + 19f, getY() + 15f);
        }
    }
}
```

（import 段增：`com.badlogic.gdx.graphics.g2d.Batch`、`com.badlogic.gdx.scenes.scene2d.Actor`、`com.badlogic.gdx.scenes.scene2d.InputEvent`、`com.badlogic.gdx.scenes.scene2d.utils.ClickListener`、`com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys`。）

- **测试要点**：UI Actor 不 headless 测（Phase 4 先例）；EXP 文案拼装抽静态纯函数不引入（量小）；lwjgl3 手验清单记"EXP 显示/Lv.7 MAX/暂停按钮可达"。

---

### CP22. ShopBar（⑧ 商店栏：5 卡 + 刷新 + 买经验）

- **类型**：新建文件
- **位置**：`core/src/main/java/com/voidvvv/kz_auto_chess_n/render/ui/ShopBar.java`
- **改动说明**：⑧ 区 (0,296,640,64) 仅 SHOPPING 可见。5 张商店卡（点击 `BuyUnitCommand(slot)`，input §2.4 点击购买）+ REFRESH（2 金）+ EXP（4 金）按钮。**预校验只读**（input §4.3：金币不足/席满不合成/空槽 → 灰置；不改状态不产生命令）；最终防线在 handler。
- **代码**（完整）：

```java
package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.voidvvv.kz_auto_chess_n.command.BuyExpCommand;
import com.voidvvv.kz_auto_chess_n.command.BuyUnitCommand;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.RefreshShopCommand;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;

import java.util.function.Supplier;

/**
 * ⑧ 商店栏（render §九；仅 SHOPPING）：5 卡 + 刷新（2 金）+ 买经验（4 金）。
 * 点击卡片入队 BuyUnit(slot)——查价不信任载荷（input §6.3）；灰置 = 表现层预校验
 * （金币不足/席满不立即合成/空槽/Lv.7 封顶），最终防线在 ShopSystem handler。
 */
public final class ShopBar extends Group {

    private static final float CARD_W = 84f;
    private static final float CARD_H = 56f;
    private static final float CARD_GAP = 8f;
    private static final float CARD_X0 = 12f;

    private final CommandManager commandManager;
    private final Assets assets;
    private final Supplier<RunContext> context;
    private final boolean[] affordable = new boolean[GameBalance.SHOP_SLOTS];

    public ShopBar(CommandManager commandManager, Assets assets, Supplier<RunContext> context) {
        this.commandManager = commandManager;
        this.assets = assets;
        this.context = context;
        for (int i = 0; i < GameBalance.SHOP_SLOTS; i++) {
            addActor(new ShopCard(i));
        }
        Actor refresh = new ActionButton("REFRESH 2G") {
            @Override
            protected void onClicked() {
                commandManager.addCommand(RefreshShopCommand.INSTANCE);
            }
        };
        refresh.setPosition(452f, 14f);
        addActor(refresh);
        Actor exp = new ActionButton("EXP 4G") {
            @Override
            protected void onClicked() {
                commandManager.addCommand(BuyExpCommand.INSTANCE);
            }
        };
        exp.setPosition(560f, 14f);
        addActor(exp);
    }

    /** 每帧刷新预校验态（只读；SHOPPING 期 Screen 调用） */
    public void refresh(RunContext ctx) {
        for (int i = 0; i < affordable.length; i++) {
            UnitData template = ctx.getShop().slotAt(i);
            boolean canBuy = template != null
                    && ctx.getPlayer().canAfford(template.getCost())
                    && (ctx.getPlayer().getBench().size() < GameBalance.BENCH_SIZE
                        || countSameTemplateStar1(ctx, template.getId()) >= 2); // 席满例外：立即 3 合 1
            affordable[i] = canBuy;
        }
    }

    private static int countSameTemplateStar1(RunContext ctx, String templateId) {
        int count = 0;
        for (com.voidvvv.kz_auto_chess_n.entities.Unit unit : ctx.getPlayer().getBench()) {
            if (unit.getTemplate().getId().equals(templateId) && unit.getStar() == 1) {
                count++;
            }
        }
        for (com.voidvvv.kz_auto_chess_n.entities.Unit unit : ctx.getPlayer().getDeployedUnits()) {
            if (unit.getTemplate().getId().equals(templateId) && unit.getStar() == 1) {
                count++;
            }
        }
        return count;
    }

    /** 商店卡（占位帧 + 费价 + 灰置态） */
    private final class ShopCard extends Actor {
        private final int slot;

        ShopCard(int slot) {
            this.slot = slot;
            setSize(CARD_W, CARD_H);
            setPosition(CARD_X0 + slot * (CARD_W + CARD_GAP), 4f);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    commandManager.addCommand(new BuyUnitCommand(slot));
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            RunContext ctx = context.get();
            UnitData template = ctx.getShop().slotAt(slot);
            if (template == null) {
                return; // 已购空槽：不绘制（点击预校验在 handler 拒绝）
            }
            boolean can = affordable[slot];
            Color old = batch.getColor();
            batch.setColor(can ? 0.3f : 0.18f, can ? 0.32f : 0.18f, can ? 0.38f : 0.2f, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            batch.draw(assets.region(PlaceholderKeys.unitFrame(template.getId(), PlaceholderKeys.ANIM_IDLE, 0)),
                    getX() + 4f, getY() + 18f, 32f, 32f);
            assets.font().draw(batch, String.valueOf(template.getCost()) + "G", getX() + 44f, getY() + 44f);
            assets.font().draw(batch, template.getName(), getX() + 6f, getY() + 12f);
        }
    }

    /** 矩形动作按钮（刷新/买经验共用壳） */
    private abstract class ActionButton extends Actor {
        private final String text;

        ActionButton(String text) {
            this.text = text;
            setSize(100f, 36f);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    onClicked();
                }
            });
        }

        protected abstract void onClicked();

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Color old = batch.getColor();
            batch.setColor(0.32f, 0.36f, 0.3f, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            assets.font().draw(batch, text, getX() + 8f, getY() + 22f);
        }
    }
}
```

- **测试要点**：绘制走 lwjgl3 手验（点击购入/灰置/空槽不响应）；`refresh` 的预校验逻辑经手验清单核对（金币不足灰、席满灰、席满+同名 ×2 亮）——headless 直测受限（Group/Actor 无 GL 需求，可构造断言 `affordable` 数组，但数组私有：将 `refresh` 的判定抽包级可见静态方法 `canBuy(ctx, slot)` 供 `render/ShopBarLogicTest` 断言——执行时按此微调封装，属实现细节不扩 CP）。

---

### CP23. InventoryPanel（③ 背包）+ EquipPendingState（两段式点击）

- **类型**：新建文件 ×2
- **位置**：`core/.../render/ui/InventoryPanel.java`、`render/ui/EquipPendingState.java`
- **改动说明**：Q1 裁备入口。两段式点击（input §2.4）：点背包格 → 待定态（高亮）→ 点目标棋子完成 `EquipItemCommand`（棋子点击链路经 CP19 `unitClickListener` → CP29 装配翻译）；再点同一格/点空白取消。显示前 6 件 + 总数角标（口径 #16）。
- **代码**（两文件合一展示）：

```java
// render/ui/EquipPendingState.java
package com.voidvvv.kz_auto_chess_n.render.ui;

/**
 * 装备待定态（input §2.5 配套规则：翻译层维护的轻量 pending 状态——跨域交互退化为点击的中转）。
 * 纯状态容器，BattleScreen 注入 InventoryPanel 与棋盘点击回调共用。
 */
public final class EquipPendingState {
    private int pendingItemId = -1;

    public boolean hasPending() { return pendingItemId >= 0; }
    public int pendingItemId() { return pendingItemId; }
    public void set(int itemId) { this.pendingItemId = itemId; }
    public void clear() { this.pendingItemId = -1; }
}

// render/ui/InventoryPanel.java
package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.data.EquipmentRarity;
import com.voidvvv.kz_auto_chess_n.entities.Equipment;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;

import java.util.List;
import java.util.function.Supplier;

/**
 * ③ 装备背包 3×2（render §九；全程可见，BATTLE 期置灰 alpha 0.35——差异声明 #8）。
 * 显示前 6 件 + 总数角标（口径 #16 背包无上限）；两段式点击的起点（EquipPendingState）。
 */
public final class InventoryPanel extends Group {

    private static final int VISIBLE_SLOTS = 6;

    private final Assets assets;
    private final Supplier<RunContext> context;
    private final EquipPendingState pending;

    public InventoryPanel(Assets assets, Supplier<RunContext> context, EquipPendingState pending) {
        this.assets = assets;
        this.context = context;
        this.pending = pending;
        for (int i = 0; i < VISIBLE_SLOTS; i++) {
            addActor(new InventorySlot(i));
        }
    }

    /** 每帧刷新（无内部缓存：待定高亮与置灰随 ctx 变化即时反映） */
    public void refresh() {
        // 待定物品可能已不在包（已穿/卖出）：失配自动取消
        if (pending.hasPending() && context.get().getPlayer().findInventoryItem(pending.pendingItemId()) == null) {
            pending.clear();
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float alpha = context.get().getRunState().getPhase() == GamePhase.BATTLE
                ? parentAlpha * 0.35f : parentAlpha; // 战斗期置灰（差异声明 #8）
        super.draw(batch, alpha);
        List<Equipment> inventory = context.get().getPlayer().getInventory();
        if (inventory.size() > VISIBLE_SLOTS) {
            assets.font().draw(batch, "+" + (inventory.size() - VISIBLE_SLOTS),
                    BoardGeometry.INVENTORY_X + BoardGeometry.INVENTORY_W - 18f,
                    BoardGeometry.INVENTORY_Y + 10f);
        }
    }

    private final class InventorySlot extends Actor {
        private final int index;

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

        private Equipment itemAt() {
            List<Equipment> inventory = context.get().getPlayer().getInventory();
            return index < inventory.size() ? inventory.get(index) : null;
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Equipment item = itemAt();
            Color tint = rarityTint(item);
            Color old = batch.getColor();
            boolean isPendingSource = item != null && pending.pendingItemId() == item.getId();
            batch.setColor(isPendingSource ? new Color(1f, 0.9f, 0.3f, parentAlpha)
                    : new Color(tint.r, tint.g, tint.b, 0.9f * parentAlpha));
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            if (item != null) {
                assets.font().draw(batch, item.getTemplate().getName().substring(0,
                                Math.min(3, item.getTemplate().getName().length())),
                        getX() + 4f, getY() + 14f);
                assets.font().draw(batch, slotMark(item), getX() + 4f, getY() + 30f);
            }
        }
    }

    private static Color rarityTint(Equipment item) {
        if (item == null) {
            return new Color(0.25f, 0.24f, 0.28f, 1f);
        }
        if (item.getTemplate().getRarity() == EquipmentRarity.LEGENDARY) {
            return new Color(0.55f, 0.42f, 0.12f, 1f);
        }
        if (item.getTemplate().getRarity() == EquipmentRarity.RARE) {
            return new Color(0.2f, 0.3f, 0.5f, 1f);
        }
        return new Color(0.32f, 0.32f, 0.34f, 1f);
    }

    private static String slotMark(Equipment item) {
        switch (item.getTemplate().getSlot()) {
            case WEAPON: return "武";
            case ARMOR: return "甲";
            case TRINKET: default: return "饰";
        }
    }
}
```

- **测试要点**：`render/ui/EquipPendingStateTest`——set/clear/hasPending 边界；InventoryPanel 交互走 lwjgl3 手验（待定高亮→点棋子完成→再点取消→失配自动取消）；置灰与角标入手验清单。

---

### CP24. SynergyPanel（⑤ 羁绊面板·备战期预演）

- **类型**：新建文件
- **位置**：`core/src/main/java/com/voidvvv/kz_auto_chess_n/render/ui/SynergyPanel.java`
- **改动说明**：`SynergySystem.resolve` 零改复用（第一轮预判确认）：备战期按**已上场名单**预演达档羁绊（WARNING-4：不计备战席同名——TFT 预演口径差异，GDD 未定）；战斗期显示实际生效档（同一 resolve 产物随部署名单不变）。每帧 resolve 的分配开销记 WARNING-5。
- **代码**（完整）：

```java
package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;
import com.voidvvv.kz_auto_chess_n.systems.SynergySnapshot;
import com.voidvvv.kz_auto_chess_n.systems.SynergySystem;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * ⑤ 羁绊面板（render §九；全程可见，BATTLE 期置灰 alpha 0.35——差异声明 #8）。
 * 备战期 = 按已上场名单的达档预演（SynergySystem.resolve 零改复用）；
 * 战斗期 = 同一产物（开战时部署名单已冻结，数值一致）。
 */
public final class SynergyPanel extends Group {

    private final Assets assets;
    private final Supplier<RunContext> context;
    private final SynergySystem synergySystem = new SynergySystem();
    private List<String> lastLines = new ArrayList<String>();

    public SynergyPanel(Assets assets, Supplier<RunContext> context) {
        this.assets = assets;
        this.context = context;
        setPosition(BoardGeometry.SYNERGY_X, BoardGeometry.SYNERGY_Y);
        setSize(BoardGeometry.SYNERGY_W, BoardGeometry.SYNERGY_H);
    }

    /** 每帧刷新（行变更才重建列表——渲染段零额外分配） */
    public void refresh(RunContext ctx) {
        List<UnitData> templates = new ArrayList<UnitData>();
        for (com.voidvvv.kz_auto_chess_n.entities.Unit unit : ctx.getPlayer().getDeployedUnits()) {
            templates.add(unit.getTemplate());
        }
        SynergySnapshot snapshot = synergySystem.resolve(templates, ctx.getGameData());
        List<String> lines = new ArrayList<String>();
        for (SynergySnapshot.ActiveSynergy active : snapshot.getActives()) {
            lines.add(active.getName() + " (" + active.getThresholdCount() + ")");
        }
        if (lines.isEmpty()) {
            lines.add("-"); // 空态占位
        }
        lastLines = lines;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float alpha = context.get().getRunState().getPhase() == GamePhase.BATTLE
                ? parentAlpha * 0.35f : parentAlpha;
        Color old = batch.getColor();
        batch.setColor(0.2f, 0.19f, 0.24f, 0.85f * alpha);
        batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
        batch.setColor(old);
        float y = getY() + getHeight() - 14f;
        for (int i = 0; i < lastLines.size() && i < 8; i++) {
            assets.font().draw(batch, lastLines.get(i), getX() + 8f, y);
            y -= 16f;
        }
    }
}
```

- **测试要点**：resolve 复用逻辑已由 `systems/SynergySystemTest` 背书；面板行拼装走 lwjgl3 手验（上阵 2 兽人 → "兽人 (2)" 出现、撤下消失）；每帧分配开销记 WARNING-5。

---

### CP25. UnitDetailDialog（棋子详情 + 卸下）

- **类型**：新建文件
- **位置**：`core/src/main/java/com/voidvvv/kz_auto_chess_n/render/ui/UnitDetailDialog.java`
- **改动说明**：Phase 4 欠账（Q2 推迟项）。棋盘域死区点击唤起（经 CP19 回调 + CP29 装配）；弹窗宿主归 dialogStage（architecture §七"弹窗永不做 Screen"）；MVP 范围：名/星/累计花费/模板属性 + 已穿装备三槽各带卸下按钮（`UnequipItemCommand(itemId)`——input §2.4 卸下入口）+ 关闭。**无 Tooltip**（render §十一待定延续）。展示期名单可能变化 → 每帧 refresh，单位消失自动关闭。
- **代码**（完整）：

```java
package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.command.UnequipItemCommand;
import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.Equipment;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;

import java.util.function.Supplier;

/**
 * 棋子详情弹窗（Phase 4 欠账；dialogStage 弹窗族）。MVP：名/星/spend/模板属性 + 三槽卸下 + 关闭。
 * 每帧 refresh（名单/装备可能经命令变化）；单位已不在名单 → 请求关闭（isExpired）。
 */
public final class UnitDetailDialog extends Group {

    /** 关闭请求（Screen 实现：dialogManager.closeTop） */
    public interface CloseListener {
        void onCloseRequested();
    }

    private final CommandManager commandManager;
    private final Assets assets;
    private final Supplier<RunContext> context;
    private final CloseListener closeListener;
    private int unitId = -1;

    public UnitDetailDialog(CommandManager commandManager, Assets assets,
                            Supplier<RunContext> context, CloseListener closeListener) {
        this.commandManager = commandManager;
        this.assets = assets;
        this.context = context;
        this.closeListener = closeListener;
        Actor close = new CloseButton();
        close.setPosition(310f, 200f);
        addActor(close);
    }

    /** 打开（棋盘域点击回调） */
    public void showUnit(int unitId) {
        this.unitId = unitId;
    }

    /** 单位已不在名单（被卖出/合并）→ true，装配点据此收起 */
    public boolean isExpired() {
        return context.get().getPlayer().getUnitById(unitId) == null;
    }

    /** 每帧刷新：三槽卸下按钮重建（装备集变化幂等——按钮为轻量 Actor） */
    public void refresh() {
        for (int i = getChildren().size - 1; i >= 0; i--) {
            if (getChildren().get(i) instanceof UnequipButton) {
                getChildren().get(i).remove();
            }
        }
        Unit unit = context.get().getPlayer().getUnitById(unitId);
        if (unit == null) {
            return;
        }
        float y = 170f;
        for (Equipment item : unit.getEquipped()) {
            UnequipButton button = new UnequipButton(item);
            button.setPosition(150f, y);
            addActor(button);
            y -= 30f;
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        Unit unit = context.get().getPlayer().getUnitById(unitId);
        if (unit == null) {
            return;
        }
        Color old = batch.getColor();
        batch.setColor(0f, 0f, 0f, 0.75f * parentAlpha);
        batch.draw(assets.region(PlaceholderKeys.WHITE), 70f, 60f, 380f, 180f);
        batch.setColor(old);
        UnitData template = unit.getTemplate();
        BaseStats stats = template.getBaseStats();
        assets.font().draw(batch, template.getName() + "  " + unit.getStar() + "星"
                + "  spend " + unit.getSpend(), 90f, 215f);
        assets.font().draw(batch, "HP " + stats.getHp() + "  ATK " + stats.getAttack()
                + "  ARMOR " + stats.getArmor(), 90f, 195f);
        assets.font().draw(batch, "ASPD " + stats.getAttackSpeed() + "  RANGE " + stats.getRange()
                + "  MSPD " + stats.getMoveSpeed(), 90f, 178f);
        super.draw(batch, parentAlpha); // 卸下/关闭按钮
    }

    private final class CloseButton extends Actor {
        CloseButton() {
            setSize(48f, 22f);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    closeListener.onCloseRequested();
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Color old = batch.getColor();
            batch.setColor(0.4f, 0.32f, 0.32f, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            assets.font().draw(batch, "X", getX() + 20f, getY() + 15f);
        }
    }

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
}
```

- **测试要点**：`isExpired` 逻辑可 headless 构造（Group 无 GL 依赖，`new UnitDetailDialog(...)` 需 Assets——改为静态包级 `unitGone(ctx, id)` 判定入测试，或沿 UI 不 headless 测先例走手验）；手验清单：点击棋子弹窗、卸下入队回包、卖出后弹窗自动收起。

---

### CP26. UIDialogManager + GlobalKeyProcessor + PauseMenuDialog（含放弃二次确认）

- **类型**：新建文件 ×3
- **位置**：`core/.../render/ui/UIDialogManager.java`、`core/.../render/ui/PauseMenuDialog.java`、`core/.../input/GlobalKeyProcessor.java`
- **改动说明**：Q5 裁决本期部分。`UIDialogManager` 持 dialogStage + 弹窗栈（`isShowing()` 供 modalBlocked 与模拟冻结——模态阻断链见 `docs/diagrams/phase5_dialog_modal_chain.md`）；`GlobalKeyProcessor` 承接 Escape/BACK（input §3 例外条款：永不被模态吞）与 L 键（通知大窗切换）；`PauseMenuDialog` MVP = 继续/放弃（设置推 Phase 7），放弃经二次确认子弹窗 → `AbandonRunCommand`。
- **代码**（三文件合一展示）：

```java
// render/ui/UIDialogManager.java
package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.ArrayDeque;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;

/**
 * 弹窗宿主（input §2.2 第 1 层；render §九 RESULT 弹窗层 = 输入最高优先级）。
 * dialogStage + 弹窗栈 + 全屏收点背板；isShowing() 同时供 boardProcessor.modalBlocked
 * 与 BattleScreen 模拟冻结（实现口径 #14）。act/draw/resize/dispose 由 Screen 委托调用。
 */
public final class UIDialogManager {
    private final Assets assets;
    private final Stage dialogStage;
    private final ArrayDeque<Actor> stack = new ArrayDeque<Actor>();
    private final Actor backdrop;

    public UIDialogManager(Assets assets) {
        this.assets = Objects.requireNonNull(assets, "assets 不能为 null");
        this.dialogStage = new Stage(new FitViewport(BoardGeometry.VIRTUAL_W, BoardGeometry.VIRTUAL_H));
        this.backdrop = new Actor() {
            {
                setSize(BoardGeometry.VIRTUAL_W, BoardGeometry.VIRTUAL_H);
                addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        // 背板只吞点击不动作（模态穿透防御，input §3）
                    }
                });
            }

            @Override
            public void draw(com.badlogic.gdx.graphics.g2d.Batch batch, float parentAlpha) {
                // 半透明压暗（可见性随栈空切换——见 syncBackdrop）
                batch.setColor(0f, 0f, 0f, 0.45f * parentAlpha);
                batch.draw(assets.region(PlaceholderKeys.WHITE), 0f, 0f, getWidth(), getHeight());
                batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
            }
        };
        dialogStage.addActor(backdrop);
    }

    public Stage getStage() {
        return dialogStage;
    }

    /** 压栈展示（同一弹窗重复 push 幂等跳过） */
    public void push(Actor dialog) {
        if (!stack.contains(dialog)) {
            stack.addLast(dialog);
            dialogStage.addActor(dialog);
            dialog.toFront();
        }
        syncBackdrop();
    }

    /** 关顶层 */
    public void closeTop() {
        Actor top = stack.pollLast();
        if (top != null) {
            top.remove();
        }
        syncBackdrop();
    }

    /** 清空（重开新局） */
    public void clearAll() {
        while (!stack.isEmpty()) {
            closeTop();
        }
    }

    public boolean isShowing() {
        return !stack.isEmpty();
    }

    public void act(float delta) {
        dialogStage.act(delta);
    }

    public void draw() {
        if (isShowing()) {
            dialogStage.getViewport().apply();
            dialogStage.draw();
        }
    }

    public void resize(int width, int height) {
        dialogStage.getViewport().update(width, height, true);
    }

    public void dispose() {
        dialogStage.dispose();
    }

    private void syncBackdrop() {
        backdrop.setVisible(isShowing());
        backdrop.toFront();
        for (Actor dialog : stack) {
            dialog.toFront();
        }
    }
}
```

（UIDialogManager import 段：`com.badlogic.gdx.scenes.scene2d.Actor/InputEvent/Stage`、`ClickListener`、`FitViewport`、`ArrayDeque`、`java.util.Objects`、`Assets`、`PlaceholderKeys`、`BoardGeometry`。）

```java
// input/GlobalKeyProcessor.java
package com.voidvvv.kz_auto_chess_n.input;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;

/**
 * 全局按键处理器（input §2.2 第 4 层）。Escape / Android BACK 永不被模态吞
 * （§3 例外条款）：有弹窗 → 关顶层；无弹窗 → 开暂停菜单（回调制，装配点决定）。
 * L 键 → 通知面板大小窗切换（render §5.5）。
 */
public final class GlobalKeyProcessor implements InputProcessor {

    /** 按键回调（返回是否已消费） */
    public interface Listener {
        boolean onEscapeOrBack();
        boolean onNotificationToggle();
    }

    private final Listener listener;

    public GlobalKeyProcessor(Listener listener) {
        this.listener = listener;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACK) {
            return listener.onEscapeOrBack();
        }
        if (keycode == Input.Keys.L) {
            return listener.onNotificationToggle();
        }
        return false;
    }

    @Override public boolean keyUp(int keycode) { return false; }
    @Override public boolean keyTyped(char character) { return false; }
    @Override public boolean touchDown(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean touchUp(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean touchDragged(int screenX, int screenY, int pointer) { return false; }
    @Override public boolean touchCancelled(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean mouseMoved(int screenX, int screenY) { return false; }
    @Override public boolean scrolled(float amountX, float amountY) { return false; }
}

// render/ui/PauseMenuDialog.java
package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.voidvvv.kz_auto_chess_n.command.AbandonRunCommand;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;

/**
 * 暂停菜单（Q5 裁决 MVP = 继续/放弃；设置 Dialog 推 Phase 7）。放弃走二次确认
 * （GDD §2.1 防误触）：确认子弹窗 push 同一 dialogStage（栈式）→ AbandonRunCommand 入队。
 * 模拟冻结由 UIDialogManager.isShowing() 驱动（口径 #14），本类不触碰模拟。
 */
public final class PauseMenuDialog extends Group {

    private final CommandManager commandManager;
    private final Assets assets;
    private final UIDialogManager dialogManager;
    private final Group confirmDialog = new Group();
    private boolean confirmShown;

    public PauseMenuDialog(CommandManager commandManager, Assets assets, UIDialogManager dialogManager) {
        this.commandManager = commandManager;
        this.assets = assets;
        this.dialogManager = dialogManager;
        Actor resume = new MenuButton(assets, "继续") {
            @Override
            protected void onClicked() {
                dialogManager.closeTop();
            }
        };
        resume.setPosition(250f, 170f);
        addActor(resume);
        Actor abandon = new MenuButton(assets, "放弃远征") {
            @Override
            protected void onClicked() {
                showConfirm();
            }
        };
        abandon.setPosition(250f, 130f);
        addActor(abandon);

        Actor yes = new MenuButton(assets, "确认放弃") {
            @Override
            protected void onClicked() {
                commandManager.addCommand(AbandonRunCommand.INSTANCE);
                dialogManager.closeTop(); // 收确认
                dialogManager.closeTop(); // 收菜单（RUN_END 后 Screen 观察收全）
            }
        };
        yes.setPosition(190f, 150f);
        confirmDialog.addActor(yes);
        Actor no = new MenuButton(assets, "取消") {
            @Override
            protected void onClicked() {
                dialogManager.closeTop(); // 只收确认，菜单保留
            }
        };
        no.setPosition(330f, 150f);
        confirmDialog.setPosition(0f, 0f);
    }

    private void showConfirm() {
        if (!confirmShown) {
            dialogManager.push(confirmDialog);
            confirmShown = true;
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        Color old = batch.getColor();
        batch.setColor(0.1f, 0.1f, 0.14f, 0.95f * parentAlpha);
        batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), 220f, 110f, 200f, 120f);
        batch.setColor(old);
        assets.font().draw(batch, "暂停", 296f, 212f);
        super.draw(batch, parentAlpha);
    }

    /** 自绘菜单按钮（Assets 构造注入——render §7.6 注入式裁决，禁静态持有） */
    abstract static class MenuButton extends Actor {
        final Assets assets;
        private final String text;

        MenuButton(final Assets assets, final String text) {
            this.assets = assets;
            this.text = text;
            setSize(140f, 32f);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    onClicked();
                }
            });
        }

        protected abstract void onClicked();

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Color old = batch.getColor();
            batch.setColor(0.35f, 0.36f, 0.42f, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            assets.font().draw(batch, text, getX() + 44f, getY() + 21f);
        }
    }
}
```

- **测试要点**：`input/GlobalKeyProcessorTest`——ESCAPE/BACK/L 三键回调触发与返回值透传，其余键 false；`render/ui/UIDialogManagerTest` 不 headless（Stage 需 GL）→ 栈语义（push 幂等/closeTop/clearAll/isShowing）以接口评审 + lwjgl3 手验背书；手验清单：Escape 开/关暂停、弹窗打开时 Escape 关顶层不穿透、拖拽中开弹窗拖拽作废、二次确认两键路径、BACK 键（Android 真机回归 WARNING-9）。

---

### CP27. ChestDialog + ResultBanner 重构（胜败分流）

- **类型**：新建文件 + 修改类
- **位置**：新建 `core/.../render/ui/ChestDialog.java`；修改 `core/.../render/ui/ResultBanner.java:49-60,62-75`
- **改动说明**：RESULT 期 UI 分流（状态图见 `docs/diagrams/phase5_result_retry_flow.md`）：胜局 = 横幅 VICTORY + ChestDialog（dialogStage）三选一，**无自动推进**；败局 = 横幅 DEFEAT/TIMEOUT + 怜悯提示行 + 点击或 3s 自动 `continueAfterDefeat`。横幅点击对胜局天然 no-op（`continueAfterDefeat` 有 pendingChest 守卫——双保险，因 dialogStage 在上层已拦截点击）。
- **代码**（新建 `render/ui/ChestDialog.java`）：

```java
package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.PickChestCommand;
import com.voidvvv.kz_auto_chess_n.data.EquipmentRarity;
import com.voidvvv.kz_auto_chess_n.entities.ChestOffer;
import com.voidvvv.kz_auto_chess_n.entities.ChestOption;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;

/**
 * 宝箱三选一弹窗（RESULT 胜局；Q2 裁决 A）。内容进 RESULT 时已 roll 好（architecture §4.1），
 * 本弹窗只读 offer；点击入队 PickChest(option)，领取后 Screen 观察 pendingChest==null 收起（§6.CP29）。
 */
public final class ChestDialog extends Group {

    private final CommandManager commandManager;
    private final Assets assets;
    private final com.voidvvv.kz_auto_chess_n.data.GameData data;
    private ChestOffer offer;

    public ChestDialog(CommandManager commandManager, Assets assets,
                       com.voidvvv.kz_auto_chess_n.data.GameData data) {
        this.commandManager = commandManager;
        this.assets = assets;
        this.data = data;
        for (int i = 0; i < 3; i++) {
            addActor(new OptionButton(i));
        }
    }

    /** Screen 在 push 前刷新（offer 不可变，无逐帧刷新需求） */
    public void refresh(ChestOffer offer) {
        this.offer = offer;
    }

    private String optionText(ChestOption option) {
        switch (option.getKind()) {
            case GOLD: return "金币 +" + option.getAmount();
            case EXP_BOOK: return "经验 +" + option.getAmount();
            case EQUIPMENT: default:
                return data.getEquipment(option.getEquipmentId()).getName();
        }
    }

    private Color optionTint(ChestOption option) {
        if (option.getKind() == ChestOption.Kind.EQUIPMENT
                && data.getEquipment(option.getEquipmentId()).getRarity() == EquipmentRarity.LEGENDARY) {
            return new Color(0.55f, 0.42f, 0.12f, 1f);
        }
        return new Color(0.3f, 0.32f, 0.4f, 1f);
    }

    private final class OptionButton extends Actor {
        private final int index;

        OptionButton(final int index) {
            this.index = index;
            setSize(120f, 60f);
            setPosition(140f + index * 130f, 130f);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    commandManager.addCommand(new PickChestCommand(index));
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            if (offer == null) {
                return;
            }
            Color tint = optionTint(offer.optionAt(index));
            Color old = batch.getColor();
            batch.setColor(tint.r, tint.g, tint.b, 0.95f * parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            assets.font().draw(batch, optionText(offer.optionAt(index)), getX() + 14f, getY() + 34f);
            assets.font().draw(batch, "选择", getX() + 44f, getY() + 14f);
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        Color old = batch.getColor();
        batch.setColor(0f, 0f, 0f, 0.8f * parentAlpha);
        batch.draw(assets.region(PlaceholderKeys.WHITE), 110f, 100f, 420f, 140f);
        batch.setColor(old);
        assets.font().getData().setScale(1.5f);
        assets.font().draw(batch, offer != null && offer.isBoss() ? "BOSS 宝箱" : "宝箱", 268f, 216f);
        assets.font().getData().setScale(1f);
        super.draw(batch, parentAlpha);
    }
}
```

（ResultBanner 修改前逐字摘自 ResultBanner.java:48-60）：

```java
修改前：
    /** 每帧刷新文案（RESULT 期由 Screen 调用） */
    public void refresh(BattleOutcome outcome) {
        if (outcome == BattleOutcome.PLAYER_WIN) {
            text = "VICTORY";
            tint = Color.GREEN;
        } else if (outcome == BattleOutcome.ENEMY_WIN) {
            text = "DEFEAT";
            tint = Color.RED;
        } else {
            text = "TIMEOUT";
            tint = Color.YELLOW;
        }
    }
```

```java
修改后：
    /** 每帧刷新文案（RESULT 期由 Screen 调用；mercyLine 可 null——败局怜悯提示） */
    public void refresh(BattleOutcome outcome, String mercyLine) {
        if (outcome == BattleOutcome.PLAYER_WIN) {
            text = "VICTORY";
            tint = Color.GREEN;
            hint = "pick a chest"; // 胜局唯一出口 = PickChest（口径 #9，无自动推进）
        } else if (outcome == BattleOutcome.ENEMY_WIN) {
            text = "DEFEAT";
            tint = Color.RED;
            hint = mercyLine != null ? "click to retry · " + mercyLine : "click to retry";
        } else {
            text = "TIMEOUT";
            tint = Color.YELLOW;
            hint = mercyLine != null ? "click to retry · " + mercyLine : "click to retry";
        }
    }
```

（ResultBanner 字段 :38-39 与 draw 尾行 :74 同步——修改前逐字摘自 ResultBanner.java:38-39、73-74）：

```java
修改前（逐字，ResultBanner.java:38-39）：
    private String text = "";
    private Color tint = Color.WHITE;
```

```java
修改后：
    private String text = "";
    private Color tint = Color.WHITE;
    private String hint = "";
```

```java
修改前（逐字，ResultBanner.java:73-74）：
        assets.font().getData().setScale(1f);
        assets.font().setColor(Color.WHITE);
        assets.font().draw(batch, "click to continue", 258f, 150f);
```

```java
修改后：
        assets.font().getData().setScale(1f);
        assets.font().setColor(Color.WHITE);
        assets.font().draw(batch, hint, 258f, 150f);
```

（ClickCatcher :28-30 调用改为 `flow.continueAfterDefeat(context.get())`——修改前逐字摘自 ResultBanner.java:27-31）：

```java
修改前：
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    flow.continueAfterResult(context.get());
                }
```

```java
修改后：
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    flow.continueAfterDefeat(context.get()); // 胜局 no-op（pendingChest 守卫，口径 #9）
                }
```

- **测试要点**：`ResultBanner.refresh` 文案分流属 UI 纯拼装（走手验）；ChestDialog 选项文案/着色经 `optionText/optionTint` 可提包级静态（执行时）供 headless 断言：金币/经验/装备三分支与传说着色；手验清单：胜局无自动推进且必须选箱、Boss 箱标题、败局怜悯行显示、TIMEOUT 走重试。

---

### CP28. NotificationLog + NotificationPanel + NotificationFormat（⑨ 事件通知）

- **类型**：新建文件 ×3
- **位置**：`core/.../render/ui/NotificationLog.java`、`render/ui/NotificationPanel.java`、`render/ui/NotificationFormat.java`
- **改动说明**：render §5.5 落地（Phase 4 预留 `onExecuted` 订阅兑现）。三流合并（口径 #13）：命令流（`NotificationFormat.formatCommand` 纯函数）/ 战斗事件流（自有 EventInbox，仅 UNIT_DIED/CAST）/ 系统反应流（`RunState.drainNotices`）。有界 200 行、小窗 4 行、单帧 ≤2 行超出丢弃（WARNING-6）；L 键大窗无过滤。**挂接时机**：EventInbox attach 于战斗创建帧（0 事件），无历史回灌。
- **代码**（三文件合一展示）：

```java
// render/ui/NotificationLog.java
package com.voidvvv.kz_auto_chess_n.render.ui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * 通知日志模型（render §5.5；纯逻辑零 Gdx，headless 可测）。
 * 有界 200 行 FIFO；小窗最近 4 行；单帧追加 ≤2 行（战斗爆发期防刷屏——超出丢弃，WARNING-6）。
 */
public final class NotificationLog {
    public static final int CAPACITY = 200;
    public static final int SMALL_WINDOW_LINES = 4;
    public static final int MAX_APPENDS_PER_FRAME = 2;

    private final ArrayDeque<String> lines = new ArrayDeque<String>(CAPACITY);
    private boolean largeMode;

    /** 追加一行（超单帧上限丢弃；null/空忽略） */
    public boolean appendCapped(String line, int appendedThisFrame) {
        if (line == null || line.trim().isEmpty() || appendedThisFrame >= MAX_APPENDS_PER_FRAME) {
            return false;
        }
        if (lines.size() >= CAPACITY) {
            lines.pollFirst();
        }
        lines.addLast(line);
        return true;
    }

    public List<String> visibleLines() {
        List<String> visible = new ArrayList<String>();
        if (largeMode) {
            visible.addAll(lines);
        } else {
            int skip = Math.max(0, lines.size() - SMALL_WINDOW_LINES);
            int i = 0;
            for (String line : lines) {
                if (i++ >= skip) {
                    visible.add(line);
                }
            }
        }
        return visible;
    }

    public void setLargeMode(boolean largeMode) { this.largeMode = largeMode; }
    public void toggleLargeMode() { this.largeMode = !largeMode; }
    public boolean isLargeMode() { return largeMode; }
    public void clear() { lines.clear(); }
}

// render/ui/NotificationFormat.java
package com.voidvvv.kz_auto_chess_n.render.ui;

import com.voidvvv.kz_auto_chess_n.command.GameCommand;
import com.voidvvv.kz_auto_chess_n.command.SellUnitCommand;
import com.voidvvv.kz_auto_chess_n.command.RefreshShopCommand;
import com.voidvvv.kz_auto_chess_n.command.BuyExpCommand;

/** 命令/战斗事件 → 通知行文案（纯函数，headless 可测；动态数值行由 RunState.notices 承担——口径 #13） */
public final class NotificationFormat {

    private NotificationFormat() {
    }

    /** 命令执行行（onExecuted 数据源）；返回 null = 不入面板 */
    public static String formatCommand(GameCommand cmd) {
        if (cmd instanceof RefreshShopCommand) {
            return "刷新商店（-2 金）";
        }
        if (cmd instanceof BuyExpCommand) {
            return "购买经验（-4 金 +4 经验）";
        }
        if (cmd instanceof SellUnitCommand) {
            return "卖出棋子"; // 返还数额动态 → notices 富行已覆盖，此处静态行去重跳过
        }
        return null; // 其余命令（买/穿/脱/领箱）动态行均走 notices
    }
}

// render/ui/NotificationPanel.java
package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.EventInbox;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;

import java.util.function.Supplier;

/**
 * ⑨ 事件通知（render §5.5）：三流合并（命令 onExecuted + 战斗 CombatEvent + RunState.notices）。
 * 小窗最近 4 行常驻；L 键（GlobalKeyProcessor）切大窗（最近 200 行，无过滤——WARNING-6）。
 */
public final class NotificationPanel extends Group {

    private final Assets assets;
    private final Supplier<RunContext> context;
    private final NotificationLog log = new NotificationLog();
    private final EventInbox inbox = new EventInbox();
    /** 命令行中转队列（onExecuted 回调线程与渲染帧解耦——渲染帧统一消费） */
    private final java.util.List<String> pendingCommandLines = new java.util.ArrayList<String>();

    public NotificationPanel(Assets assets, Supplier<RunContext> context, CommandManager commandManager) {
        this.assets = assets;
        this.context = context;
        commandManager.addListener(new CommandManager.CommandExecutedListener() {
            @Override
            public void onExecuted(com.voidvvv.kz_auto_chess_n.command.GameCommand command, boolean success) {
                queueLine(NotificationFormat.formatCommand(command)); // 渲染帧统一消费（附队列）
            }
        });
    }

    /** 战斗作用域同步（Screen 观察调用；attach 于战斗创建帧 = 0 事件，无历史回灌） */
    public void syncBattle(BattleState state) {
        if (state == null) {
            inbox.detach();
        } else {
            inbox.attach(state);
        }
    }

    /** 渲染帧统一消费三流（BattleScreen 每帧调用；三流共享单帧 2 行上限——int[1] 计数透传匿名类） */
    public void refresh(RunContext ctx) {
        final int[] appended = new int[1];
        for (String notice : ctx.getRunState().drainNotices()) {
            if (log.appendCapped(notice, appended[0])) {
                appended[0]++;
            }
        }
        inbox.forEachNew(new java.util.function.Consumer<CombatEvent>() {
            @Override
            public void accept(CombatEvent event) {
                String line = formatEvent(event);
                if (line != null && log.appendCapped(line, appended[0])) {
                    appended[0]++; // 超限丢弃（§5.5 防刷屏，WARNING-6）
                }
            }
        });
        for (String queued : pendingCommandLines) {
            if (log.appendCapped(queued, appended[0])) {
                appended[0]++;
            }
        }
        pendingCommandLines.clear();
    }

    private void queueLine(String line) {
        if (line != null) {
            pendingCommandLines.add(line);
        }
    }

    /** 战斗事件行（仅 UNIT_DIED/CAST——HIT/HEALED 过噪跳过，口径 #13） */
    static String formatEvent(CombatEvent event) {
        switch (event.getType()) {
            case UNIT_DIED: return "单位倒下";
            case CAST: return "技能施放: " + event.getSkillId();
            default: return null;
        }
    }

    public void toggleLargeMode() {
        log.toggleLargeMode();
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        Color old = batch.getColor();
        batch.setColor(0.05f, 0.05f, 0.08f, 0.7f * parentAlpha);
        batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE),
                BoardGeometry.NOTIFY_X, BoardGeometry.NOTIFY_Y,
                BoardGeometry.NOTIFY_W, BoardGeometry.NOTIFY_H);
        batch.setColor(old);
        java.util.List<String> lines = log.visibleLines();
        float y = BoardGeometry.NOTIFY_Y + BoardGeometry.NOTIFY_H - 8f;
        int count = 0;
        for (int i = lines.size() - 1; i >= 0 && count < NotificationLog.SMALL_WINDOW_LINES; i--, count++) {
            assets.font().draw(batch, lines.get(i), BoardGeometry.NOTIFY_X + 6f, y);
            y -= 12f;
        }
        if (log.isLargeMode()) {
            drawLarge(batch);
        }
    }

    private void drawLarge(Batch batch) {
        batch.setColor(0.05f, 0.05f, 0.08f, 0.88f);
        batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), 320f, 40f, 300f, 250f);
        batch.setColor(Color.WHITE);
        float y = 275f;
        java.util.List<String> lines = log.visibleLines();
        for (int i = lines.size() - 1; i >= 0 && y > 50f; i--) {
            assets.font().draw(batch, lines.get(i), 330f, y);
            y -= 12f;
        }
    }
}
```

> 口径补充：命令行 / 战斗事件行 / notices 行三流共享同一"单帧 ≤2 行"上限（`refresh` 内 `int[1]` 计数透传匿名类）——超限丢弃记 WARNING-6。

- **测试要点**：`render/ui/NotificationLogTest`——有界 200（FIFO 丢最老）、小窗恰最近 4 行、`appendCapped` 超第 2 行起 false（同帧）、大窗全量、clear。`render/ui/NotificationFormatTest`——RefreshShop/BuyExp/SellUnit/null 四态；`formatEvent` UNIT_DIED/CAST/其他三态。Panel 装配走手验（三流真实合并、L 切换）。

---

### CP29. BattleScreen 装配整合 + MainMenu seed 边界

- **类型**：修改类 ×2（BattleScreen 多区域 + MainMenuScreen 按钮）
- **位置**：`core/.../screens/BattleScreen.java`（:69-108 构造、:113-140 show、:143-168 render、:222-235 newContext/restartRun）；`core/.../screens/MainMenuScreen.java:73-93`
- **改动说明**：装配点收口：四 system 注册 handler；四层 multiplexer（dialogStage > uiStage > boardProcessor > keyProcessor，input §2.2）；modalBlocked 接 `dialogManager::isShowing`（Phase 4 常 false 位兑现）；模拟冻结 = paused || isShowing（口径 #14）；ChestDialog push/pop 观察；通知三流挂接；seed 由 UI 边界给定（构造参数；MainMenu START 点击 `System.nanoTime()`；RESTART 新 seed——Q3 裁决）；StartRun 以命令入队（回放第 0 条记录）；restart 前 `discardPending` + `clearAll` 弹窗。文件预算预估 ~400 行（800 上限内，WARNING-12）。
- **代码**（关键段对照；修改前逐字摘自 BattleScreen.java:113-140）：

```java
修改前：
    @Override
    public void show() {
        this.runContext = newContext();
        runFlowSystem.registerHandlers(commandManager);
        runFlowSystem.startNewRun(runContext);
        accumulator = 0f;
        renderClock = 0f;
        paused = false;
        speedFactor = 1f;
        battleHud.resetSpeed();
        this.boardProcessor = new BoardInputProcessor(worldViewport, commandManager,
                new java.util.function.Supplier<RunContext>() {
                    @Override
                    public RunContext get() {
                        return runContext;
                    }
                },
                new java.util.function.BooleanSupplier() { // 模态阻断位（本期常 false；Phase 5 接 UIDialogManager）
                    @Override
                    public boolean getAsBoolean() {
                        return false;
                    }
                });
        // 两层 multiplexer（口径 #20）：uiStage > boardProcessor（dialogStage/keyProcessor 位预留）
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(uiStage);
        multiplexer.addProcessor(boardProcessor);
        Gdx.input.setInputProcessor(multiplexer);
    }
```

```java
修改后：
    @Override
    public void show() {
        this.runContext = newContext(seed);
        runFlowSystem.registerHandlers(commandManager);   // 流程五命令（StartRun/StartBattle/Surrender/PickChest/AbandonRun）
        shopSystem.registerHandlers(commandManager);      // BuyUnit/RefreshShop/BuyExp
        rosterSystem.registerHandlers(commandManager);    // MoveUnit/SellUnit（§6.CP12 迁入）
        equipmentSystem.registerHandlers(commandManager); // EquipItem/UnequipItem
        commandManager.addCommand(new com.voidvvv.kz_auto_chess_n.command.StartRunCommand(
                seed, runContext.getRunState().getSceneId(), null)); // 回放第 0 条记录（Q3 裁决）
        accumulator = 0f;
        renderClock = 0f;
        paused = false;
        speedFactor = 1f;
        battleHud.resetSpeed();
        this.boardProcessor = new BoardInputProcessor(worldViewport, commandManager,
                contextSupplier(),
                new java.util.function.BooleanSupplier() { // 模态阻断位（Phase 4 预留位兑现）
                    @Override
                    public boolean getAsBoolean() {
                        return dialogManager.isShowing();
                    }
                },
                new java.util.function.IntConsumer() { // 死区点击：装备待定落点 / 详情弹窗
                    @Override
                    public void accept(int unitId) {
                        if (equipPending.hasPending()) {
                            commandManager.addCommand(new com.voidvvv.kz_auto_chess_n.command.EquipItemCommand(
                                    equipPending.pendingItemId(), unitId));
                            equipPending.clear();
                        } else {
                            unitDetailDialog.showUnit(unitId);
                            dialogManager.push(unitDetailDialog);
                        }
                    }
                });
        // 四层 multiplexer（input §2.2）：dialogStage > uiStage > boardProcessor > keyProcessor
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(dialogManager.getStage());
        multiplexer.addProcessor(uiStage);
        multiplexer.addProcessor(boardProcessor);
        multiplexer.addProcessor(keyProcessor);
        Gdx.input.setInputProcessor(multiplexer);
    }
```

（修改前逐字摘自 BattleScreen.java:143-149）：

```java
修改前：
    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.05f, 0.04f, 0.08f, 1f); // 清屏（与菜单/装载屏同底色）：否则棋盘外区域无重绘，拖拽 ghost 与已隐藏 HUD 留余像
        if (!paused) {
            stepSimulation(delta);
            renderClock += delta;
        }
        float alpha = paused ? 0f : accumulator / GameBalance.LOGIC_STEP;
```

```java
修改后：
    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.05f, 0.04f, 0.08f, 1f); // 清屏（与菜单/装载屏同底色）：否则棋盘外区域无重绘，拖拽 ghost 与已隐藏 HUD 留余像
        boolean frozen = paused || dialogManager.isShowing(); // 模态冻结（口径 #14）
        if (!frozen) {
            stepSimulation(delta);
            renderClock += delta;
        }
        float alpha = frozen ? 0f : accumulator / GameBalance.LOGIC_STEP;
```

（HUD 可见性段——修改前逐字摘自 BattleScreen.java:153-164）：

```java
修改前：
        GamePhase phase = runContext.getRunState().getPhase();
        topBar.refresh(runContext);
        shoppingHud.setVisible(phase == GamePhase.SHOPPING);
        battleHud.setVisible(phase == GamePhase.BATTLE);
        if (phase == GamePhase.BATTLE) {
            battleHud.refresh(runContext.getBattleState());
        }
        resultBanner.setVisible(phase == GamePhase.RESULT);
        if (phase == GamePhase.RESULT && runContext.getBattleState() != null) {
            resultBanner.refresh(runContext.getBattleState().getOutcome()); // 横幅读 outcome（口径 #6）
        }
        runEndPanel.setVisible(phase == GamePhase.RUN_END);
        uiStage.act(delta);
        uiStage.getViewport().apply();
        uiStage.draw();
    }
```

```java
修改后：
        GamePhase phase = runContext.getRunState().getPhase();
        topBar.refresh(runContext);
        shoppingHud.setVisible(phase == GamePhase.SHOPPING);
        battleHud.setVisible(phase == GamePhase.BATTLE);
        shopBar.setVisible(phase == GamePhase.SHOPPING);
        if (phase == GamePhase.SHOPPING) {
            shopBar.refresh(runContext);
        }
        inventoryPanel.refresh();   // ③⑤⑨ 全程可见（BATTLE 置灰在各自 draw 内，差异声明 #8）
        synergyPanel.refresh(runContext);
        notificationPanel.refresh(runContext);
        notificationPanel.syncBattle(runContext.getBattleState());
        if (phase == GamePhase.BATTLE) {
            battleHud.refresh(runContext.getBattleState());
        }
        resultBanner.setVisible(phase == GamePhase.RESULT);
        if (phase == GamePhase.RESULT && runContext.getBattleState() != null) {
            resultBanner.refresh(runContext.getBattleState().getOutcome(), mercyLine()); // 口径 #10 提示行
        }
        syncChestDialog(phase); // 胜局宝箱弹窗 push/pop（数据一致性见模态链路图）
        if (dialogManager.isShowing()) {
            unitDetailDialog.refresh(); // 详情弹窗每帧刷新（名单可能变化）
        }
        if (unitDetailDialog.isExpired()) {
            closeDialog(unitDetailDialog);
        }
        runEndPanel.setVisible(phase == GamePhase.RUN_END);
        uiStage.act(delta);
        uiStage.getViewport().apply();
        uiStage.draw();
        dialogManager.act(delta);
        dialogManager.draw();
    }

    /** 败局怜悯提示行（刚发的怜悯金 → 横幅行；否则 null） */
    private String mercyLine() {
        RunState runState = runContext.getRunState();
        return runState.getMercyGoldThisRound() > 0 && runState.getMercyLossCount() >= GameBalance.MERCY_START_LOSS
                ? "怜悯 +1（连败 " + runState.getMercyLossCount() + "）" : null;
    }

    /** 胜局 RESULT：宝箱弹窗 push/pop（Screen 观察——领取后 pendingChest==null 自动收起） */
    private void syncChestDialog(GamePhase phase) {
        boolean shouldShow = phase == GamePhase.RESULT && runContext.getRunState().getPendingChest() != null;
        if (shouldShow && !chestShown) {
            chestDialog.refresh(runContext.getRunState().getPendingChest());
            dialogManager.push(chestDialog);
            chestShown = true;
        } else if (!shouldShow && chestShown) {
            closeDialog(chestDialog);
        }
    }

    private void closeDialog(com.badlogic.gdx.scenes.scene2d.Actor dialog) {
        dialogManager.closeTop();
        if (dialog == chestDialog) {
            chestShown = false;
        }
    }
```

（newContext/restartRun——修改前逐字摘自 BattleScreen.java:221-235）：

```java
修改前：
    /** 同 DEMO_SEED 组装新鲜上下文（口径 #22：重开确定性对照） */
    private RunContext newContext() {
        String sceneId = data.getScenes().keySet().iterator().next(); // 首场景（种子仅森林）
        return new RunContext(new Player(GameBalance.START_GOLD),
                new RunState(RunFlowSystem.DEMO_SEED, sceneId, new SequentialIdIssuer()),
                data, new RandomGenerator(RunFlowSystem.DEMO_SEED));
    }

    /** RUN_END 重开：换新鲜上下文后复入 startNewRun（RunFlowSystem.restart 契约） */
    private void restartRun() {
        this.runContext = newContext();
        runFlowSystem.restart(runContext);
        accumulator = 0f;
        battleHud.resetSpeed();
    }
```

```java
修改后：
    /** 组装新鲜上下文（seed 来自 UI 域边界事件——Q3 裁决；首场景 MVP 仅森林） */
    private RunContext newContext(long runSeed) {
        String sceneId = data.getScenes().keySet().iterator().next();
        return new RunContext(new Player(GameBalance.START_GOLD),
                new RunState(runSeed, sceneId, new SequentialIdIssuer()),
                data, new RandomGenerator(runSeed), shopSystem);
    }

    /** RUN_END 重开：新 seed + 清弹窗/残留命令 + 复入 startRun（RunFlowSystem.restart 契约） */
    private void restartRun() {
        this.seed = System.nanoTime(); // UI 边界新 seed（口径 #12）
        this.runContext = newContext(seed);
        commandManager.discardPending();       // 跨局残留命令防泄漏（口径 #12）
        dialogManager.clearAll();
        chestShown = false;
        runFlowSystem.restart(runContext);
        accumulator = 0f;
        battleHud.resetSpeed();
    }
```

（构造器与字段段——修改前逐字摘自 BattleScreen.java:40-67、69-108）：

```java
修改前（字段段，BattleScreen.java:40-67）：
public final class BattleScreen implements Screen {
    private final Game game;
    private final Assets assets;
    private final GameData data;

    private final SpriteBatch batch;
    private final OrthographicCamera worldCamera = new OrthographicCamera();
    private final FitViewport worldViewport;
    private final com.badlogic.gdx.scenes.scene2d.Stage uiStage;

    private final BattleSystem battleSystem = new BattleSystem();
    private final RunFlowSystem runFlowSystem = new RunFlowSystem();
    private final CommandManager commandManager = new CommandManager();
    private RunContext runContext;

    private final BattleRenderer battleRenderer;
    private final TopBar topBar;
    private final ShoppingHud shoppingHud;
    private final BattleHud battleHud;
    private final ResultBanner resultBanner;
    private final RunEndPanel runEndPanel;
    private BoardInputProcessor boardProcessor;

    private float accumulator;
    private float renderClock;
    private boolean paused;
    /** ×1/×2 变速（口径 #5：只乘 accumulator 消费速率，不进模拟路径） */
    private float speedFactor = 1f;
```

```java
修改前（构造器，BattleScreen.java:69-108）：
    public BattleScreen(Game game, Assets assets, GameData data) {
        this.game = game;
        this.assets = assets;
        this.data = data;
        this.batch = new SpriteBatch();
        this.worldViewport = new FitViewport(BoardGeometry.VIRTUAL_W, BoardGeometry.VIRTUAL_H, worldCamera);
        this.uiStage = new com.badlogic.gdx.scenes.scene2d.Stage(
                new FitViewport(BoardGeometry.VIRTUAL_W, BoardGeometry.VIRTUAL_H));
        this.battleRenderer = new BattleRenderer(assets);
        this.topBar = new TopBar(assets);
        this.shoppingHud = new ShoppingHud(commandManager, assets);
        this.battleHud = new BattleHud(commandManager, assets, new BattleHud.SpeedListener() {
            @Override
            public void onSpeedChanged(float factor) {
                speedFactor = factor;
            }
        });
        this.resultBanner = new ResultBanner(runFlowSystem, new java.util.function.Supplier<RunContext>() {
            @Override
            public RunContext get() {
                return runContext;
            }
        }, assets);
        this.runEndPanel = new RunEndPanel(assets, new RunEndPanel.RestartListener() {
            @Override
            public void onRestart() {
                restartRun();
            }
        }, new java.util.function.Supplier<RunContext>() {
            @Override
            public RunContext get() {
                return runContext;
            }
        });
        uiStage.addActor(topBar);
        uiStage.addActor(shoppingHud);
        uiStage.addActor(battleHud);
        uiStage.addActor(resultBanner);
        uiStage.addActor(runEndPanel);
    }
```

```java
修改后（新增字段段，插于 speedFactor 之后；依赖构造参数的字段只声明、构造器内初始化）：
    /** 本局 seed（UI 域边界给定——MainMenu START 传入；RESTART 换新） */
    private long seed;
    private final com.voidvvv.kz_auto_chess_n.systems.ShopSystem shopSystem = new com.voidvvv.kz_auto_chess_n.systems.ShopSystem();
    private final com.voidvvv.kz_auto_chess_n.systems.RosterSystem rosterSystem = new com.voidvvv.kz_auto_chess_n.systems.RosterSystem();
    private final com.voidvvv.kz_auto_chess_n.systems.EquipmentSystem equipmentSystem = new com.voidvvv.kz_auto_chess_n.systems.EquipmentSystem();
    private final com.voidvvv.kz_auto_chess_n.render.ui.EquipPendingState equipPending = new com.voidvvv.kz_auto_chess_n.render.ui.EquipPendingState();
    /** 以下依赖构造参数（assets/data/commandManager）——构造器内初始化 */
    private final com.voidvvv.kz_auto_chess_n.render.ui.UIDialogManager dialogManager;
    private final com.voidvvv.kz_auto_chess_n.input.GlobalKeyProcessor keyProcessor;
    private final com.voidvvv.kz_auto_chess_n.render.ui.ShopBar shopBar;
    private final com.voidvvv.kz_auto_chess_n.render.ui.InventoryPanel inventoryPanel;
    private final com.voidvvv.kz_auto_chess_n.render.ui.SynergyPanel synergyPanel;
    private final com.voidvvv.kz_auto_chess_n.render.ui.NotificationPanel notificationPanel;
    private final com.voidvvv.kz_auto_chess_n.render.ui.ChestDialog chestDialog;
    private final com.voidvvv.kz_auto_chess_n.render.ui.UnitDetailDialog unitDetailDialog;
    private final com.voidvvv.kz_auto_chess_n.render.ui.PauseMenuDialog pauseMenuDialog;
    private boolean chestShown;
```

```java
修改后（构造器全文）：
    public BattleScreen(Game game, Assets assets, GameData data, long seed) {
        this.game = game;
        this.assets = assets;
        this.data = data;
        this.seed = seed;
        this.batch = new SpriteBatch();
        this.worldViewport = new FitViewport(BoardGeometry.VIRTUAL_W, BoardGeometry.VIRTUAL_H, worldCamera);
        this.uiStage = new com.badlogic.gdx.scenes.scene2d.Stage(
                new FitViewport(BoardGeometry.VIRTUAL_W, BoardGeometry.VIRTUAL_H));
        this.battleRenderer = new BattleRenderer(assets);
        this.dialogManager = new com.voidvvv.kz_auto_chess_n.render.ui.UIDialogManager(assets);
        this.topBar = new TopBar(assets, new TopBar.PauseListener() {
            @Override
            public void onPauseRequested() {
                dialogManager.push(pauseMenuDialog);
            }
        });
        this.shoppingHud = new ShoppingHud(commandManager, assets);
        this.battleHud = new BattleHud(commandManager, assets, new BattleHud.SpeedListener() {
            @Override
            public void onSpeedChanged(float factor) {
                speedFactor = factor;
            }
        });
        this.resultBanner = new ResultBanner(runFlowSystem, contextSupplier(), assets);
        this.runEndPanel = new RunEndPanel(assets, new RunEndPanel.RestartListener() {
            @Override
            public void onRestart() {
                restartRun();
            }
        }, contextSupplier());
        this.shopBar = new ShopBar(commandManager, assets, contextSupplier());
        this.inventoryPanel = new InventoryPanel(assets, contextSupplier(), equipPending);
        this.synergyPanel = new SynergyPanel(assets, contextSupplier());
        this.notificationPanel = new NotificationPanel(assets, contextSupplier(), commandManager);
        this.chestDialog = new ChestDialog(commandManager, assets, data);
        this.unitDetailDialog = new UnitDetailDialog(commandManager, assets, contextSupplier(),
                new UnitDetailDialog.CloseListener() {
                    @Override
                    public void onCloseRequested() {
                        closeDialog(unitDetailDialog);
                    }
                });
        this.pauseMenuDialog = new PauseMenuDialog(commandManager, assets, dialogManager);
        this.keyProcessor = new GlobalKeyProcessor(new GlobalKeyProcessor.Listener() {
            @Override
            public boolean onEscapeOrBack() {
                if (dialogManager.isShowing()) {
                    dialogManager.closeTop(); // input §3：有弹窗关顶层
                    return true;
                }
                dialogManager.push(pauseMenuDialog); // 无弹窗开暂停
                return true;
            }

            @Override
            public boolean onNotificationToggle() {
                notificationPanel.toggleLargeMode();
                return true;
            }
        });
        uiStage.addActor(topBar);
        uiStage.addActor(shoppingHud);
        uiStage.addActor(battleHud);
        uiStage.addActor(resultBanner);
        uiStage.addActor(runEndPanel);
        uiStage.addActor(shopBar);
        uiStage.addActor(inventoryPanel);
        uiStage.addActor(synergyPanel);
        uiStage.addActor(notificationPanel);
    }

    /** 上下文供应者（面板/横幅共用——值随 restartRun 换新） */
    private java.util.function.Supplier<RunContext> contextSupplier() {
        return new java.util.function.Supplier<RunContext>() {
            @Override
            public RunContext get() {
                return runContext;
            }
        };
    }
```

（resize/dispose 同步——修改前逐字摘自 BattleScreen.java:194-197、215-219）：

```java
修改前：
    @Override
    public void resize(int width, int height) {
        worldViewport.update(width, height, true); // 双 viewport 同参数同步（render §2.1）
        uiStage.getViewport().update(width, height, true);
    }
```

```java
修改后：
    @Override
    public void resize(int width, int height) {
        worldViewport.update(width, height, true); // 双 viewport 同参数同步（render §2.1）
        uiStage.getViewport().update(width, height, true);
        dialogManager.resize(width, height); // 三 viewport 同参数（dialogStage）
    }
```

```java
修改前：
    @Override
    public void dispose() {
        batch.dispose();
        uiStage.dispose();
        // Assets 归 Main 持有，不在此弃
    }
```

```java
修改后：
    @Override
    public void dispose() {
        batch.dispose();
        uiStage.dispose();
        dialogManager.dispose();
        // Assets 归 Main 持有，不在此弃
    }
```

（MainMenuScreen——修改前逐字摘自 MainMenuScreen.java:77-82）：

```java
修改前：
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    game.setScreen(new BattleScreen(game, assets, data));
                }
            });
```

```java
修改后：
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    // UI 域边界事件：点击"开始远征"结算 seed → StartRun 参数（architecture §一）
                    long runSeed = System.nanoTime();
                    game.setScreen(new BattleScreen(game, assets, data, runSeed));
                }
            });
```

- **测试要点**：Screen 装配不 headless 测（Phase 4 先例）；手验清单（lwjgl3）——开局商店 5 卡可购（免费刷新已发生）、四层输入不穿透（弹窗开时点棋盘无反应）、Escape 开关暂停、胜局强制选箱、RESTART 后 seed 变化（TopBar 轮次复位 + 新商店）、放弃 → RunEndPanel 放弃文案、装备两段式点击全流程、L 大窗。确定性：`RunFlowSystemTest` 的整局对照已覆盖逻辑层；装配层经"同 seed 重启进同一状态"手验。

---

### CP30. RunEndPanel 终局信息（endCause 文案 + seed + 熟练度）

- **类型**：修改类
- **位置**：`core/.../render/ui/RunEndPanel.java:41-51`（draw）
- **改动说明**：Q5 裁决配套——放弃文案区分（`RunEndCause`）、显示本局 seed（复现参考，Phase 6 RunSetup 提供输入前的过渡）、显示熟练度 stub 产出。
- **代码**（修改前逐字摘自 RunEndPanel.java:40-51）：

```java
修改前：
    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
        batch.setColor(0f, 0f, 0f, 0.65f * parentAlpha);
        batch.draw(assets.region(PlaceholderKeys.WHITE), 90f, 80f, 460f, 200f);
        batch.setColor(Color.WHITE);
        assets.font().getData().setScale(2f);
        assets.font().draw(batch, "RUN END", 250f, 230f);
        assets.font().getData().setScale(1f);
        int round = context.get().getRunState().getRound();
        assets.font().draw(batch, "survived to round " + round + "/" + GameBalance.TOTAL_ROUNDS, 240f, 200f);
    }
```

```java
修改后：
    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
        batch.setColor(0f, 0f, 0f, 0.65f * parentAlpha);
        batch.draw(assets.region(PlaceholderKeys.WHITE), 90f, 80f, 460f, 200f);
        batch.setColor(Color.WHITE);
        RunContext ctx = context.get();
        boolean abandoned = ctx.getRunState().getEndCause() == com.voidvvv.kz_auto_chess_n.entities.RunEndCause.ABANDONED;
        assets.font().getData().setScale(2f);
        assets.font().draw(batch, abandoned ? "RUN ABANDONED" : "RUN COMPLETE", abandoned ? 215f : 225f, 230f);
        assets.font().getData().setScale(1f);
        int round = ctx.getRunState().getRound();
        assets.font().draw(batch, "survived to round " + round + "/" + GameBalance.TOTAL_ROUNDS, 240f, 200f);
        assets.font().draw(batch, "mastery +" + ctx.getRunState().getMasteryAwarded() + " (stub, Phase 6)", 235f, 180f);
        assets.font().draw(batch, "seed " + ctx.getRunState().getSeed(), 262f, 160f);
    }
```

- **测试要点**：文案分支走 lwjgl3 手验（通关/放弃两态）；mastery 数值正确性由 CP15 测试背书。

---

### CP31. units.json 铺量建议（内容性任务，可独立裁掉）

- **类型**：修改资产文件（内容扩充，不影响架构）
- **位置**：`assets/data/units.json`（现 3 可购 + 3 Boss）
- **改动说明**：Q6 裁决授权的建议方案（WARNING-1 缓解）：铺至 **9 个可购模板**（1 费 4 / 2 费 3 / 3 费 2），使 6 个首发羁绊各有 ≥2 个可购模板（(2) 档预演可达、商店不再同质化）。全部**复用既有技能**（零 skills.json 改动、零代码改动）；数值待调。是否采纳与数量增减由内容侧终裁——本 CP 可整块裁掉不影响其他 CP。
- **代码**（units.json 追加的 6 条——插于 `unit_ranger_01` 之后、Boss 段之前）：

```json
  {
    "id": "unit_boar_rider", "name": "野猪骑士", "race": "兽人", "class": "战士", "cost": 1,
    "baseStats": { "hp": 110, "attack": 13, "armor": 12, "attackSpeed": 0.9, "range": 1, "moveSpeed": 1.2 },
    "upgradeMultiplier": 1.8,
    "skillId": "skill_rampage"
  },
  {
    "id": "unit_wolf_pup", "name": "狼崽", "race": "野兽", "class": "刺客", "cost": 1,
    "baseStats": { "hp": 75, "attack": 17, "armor": 3, "attackSpeed": 1.4, "range": 1, "moveSpeed": 1.6 },
    "upgradeMultiplier": 1.8,
    "skillId": "skill_execute"
  },
  {
    "id": "unit_mage_apprentice", "name": "暗夜学徒", "race": "暗夜", "class": "法师", "cost": 1,
    "baseStats": { "hp": 70, "attack": 10, "armor": 3, "attackSpeed": 0.8, "range": 3, "moveSpeed": 0.9 },
    "upgradeMultiplier": 1.8,
    "skillId": "skill_poison_cloud"
  },
  {
    "id": "unit_fairy_druid", "name": "精灵德鲁伊", "race": "精灵", "class": "法师", "cost": 2,
    "baseStats": { "hp": 95, "attack": 14, "armor": 5, "attackSpeed": 0.9, "range": 2, "moveSpeed": 1.0 },
    "upgradeMultiplier": 1.8,
    "skillId": "skill_mass_heal"
  },
  {
    "id": "unit_beast_archer", "name": "兽猎手", "race": "野兽", "class": "游侠", "cost": 2,
    "baseStats": { "hp": 80, "attack": 19, "armor": 4, "attackSpeed": 1.1, "range": 3, "moveSpeed": 0.9 },
    "upgradeMultiplier": 1.8,
    "skillId": "skill_pierce"
  },
  {
    "id": "unit_shadow_blade", "name": "暗影之刃", "race": "暗夜", "class": "刺客", "cost": 3,
    "baseStats": { "hp": 90, "attack": 28, "armor": 6, "attackSpeed": 1.5, "range": 1, "moveSpeed": 1.9 },
    "upgradeMultiplier": 1.8,
    "defaultPriority": "NEAREST", "specialPriority": "BACKLINE",
    "skillId": "skill_execute"
  },
```

（铺量后羁绊覆盖核对：兽人 ×3 / 战士 ×3 / 法师 ×2 / 刺客 ×3 / 游侠 ×2 / 野兽 ×2——全部 ≥2 达 (2) 档。）

- **测试要点**：`config/JsonLoaderTest` 真实资产断言（沿既有口径）；铺量后费阶池分布断言（1 费 4 / 2 费 3 / 3 费 2）防手误；`Main.create` 零软告警（风味种族"精灵/暗夜"既有告警口径不变——非新增羁绊键）。

---

## 7. 分阶段任务拆解

按执行顺序组织 CP（实现与代码见 §6 各 CP，此处不复述）。提交切分建议沿用 Phase 4 惯例（每任务 1~2 个 feat 提交）。

| 任务 | 所含 CP | 前置 | 验收标准（要点） |
|------|---------|------|------|
| T1 数据与实体基座 | CP1~CP7 | 无 | 新测试全绿（GameBalance/EquipmentData/JsonLoaderEquipments/Unit/Player/ActiveStatus）；存量测试零回归（GameData/JsonLoader 兼容重载保旧签名）；equipments.json 可加载且零软告警 |
| T2 命令与经济/装备/名单系统 | CP8~CP12 | T1 | ShopSystemTest/ChestSystemTest/EquipmentSystemTest/RosterSystemTest 全绿；RNG 消耗精确断言（reroll=10、chest=2）；3 合 1 级联与 spend 折叠正确；门控矩阵（BATTLE 拒经营命令）覆盖 |
| T3 流程重构与战斗接入 | CP13~CP16 | T2 | RunFlowSystemTest 新口径全绿（Q6 改造完成：无演示名单断言）；判负重试敌阵/商店不变且 RNG 零消耗；怜悯 3 败起 +1、每轮封顶 3、零棋子不计；胜局必须 PickChest；25 轮通关经领箱入 RUN_END；AbandonRun 两阶段生效；BattleSystemTest 装备派生/龙心被动绿；CommandManagerTest tick 新口径绿；整局同 seed 对照（restartReplaysIdenticalEventStreams 新口径）绿 |
| T4 布局与素材基座 | CP17、CP18 | T1 | BoardGeometryTest 新区常量/命中绿；`RealArtTest.pathOf` 绿；素材文件入库（font + 3 套小人 + LICENSE/README）；lwjgl3 启动：中文字幕渲染正常、3 棋子真素材动画播动、其余回退占位不炸 |
| T5 棋盘域交互扩展 | CP19、CP20 | T2、T4 | 拖棋子入 ⑦ 区松手入队 SellUnitCommand；死区点击回调触发；手验：出售区高亮/回弹/引导文案更新 |
| T6 UI 域面板 | CP21~CP24 | T4 | 手验：TopBar EXP/暂停、ShopBar 购买/灰置/刷新/买经验、InventoryPanel 两段式待定高亮与角标、SynergyPanel 上阵预演（2 兽人 → "兽人 (2)"） |
| T7 弹窗与通知 | CP25~CP28 | T3、T6 | GlobalKeyProcessorTest 绿；NotificationLogTest/NotificationFormatTest 绿；手验：Escape 开关暂停且弹窗期不穿透棋盘、二次确认两路径、ChestDialog 三选一领取流转、UnitDetailDialog 卸下与自动收起、通知三流合并显示与 L 大窗 |
| T8 装配整合 | CP29、CP30 | T5、T6、T7 | 手验全清单（§6.CP29 测试要点）逐项过；RESTART 新 seed + 无残留命令/弹窗；放弃文案/seed/熟练度显示 |
| T9 内容铺量（可选） | CP31 | T1 | JsonLoaderTest 真实资产断言绿；费阶池分布断言绿；商店 5 卡异质化手验 |
| T10 全量回归 | 全部 | T1~T9 | `gradlew :core:test` 退出码 0 + TEST-*.xml 聚合全绿（MEMORY 口径）；lwjgl3:run 完整通关 + 放弃 + 重开各一次；Android 真机回归（WARNING-9：BACK 键/挂起恢复/横屏） |

---

## 8. 风险与开放问题

### WARNING（不阻塞，已按口径落地或观察项）

| # | 项 | 处置 |
|---|----|------|
| 1 | units.json 可购模板仅 3 个：首轮体验单薄、商店同质化（Q6 裁决记入） | CP31 建议铺至 9（内容性任务可裁） |
| 2 | SellUnit"累计花费"字段落地为 `Unit.spend`（买入累加/合并折叠）——GDD §3.6 注"若滥用可下调 90%" | 数值待调；口径已定，改返还系数只动 RosterSystem 一处 |
| 3 | 商店池允许同模板多槽重复（有放回抽取，与波次生成同口径） | GDD 未禁止；确认为设计内行为 |
| 4 | 羁绊面板备战期按**已上场**名单预演（SynergySystem.resolve 零改复用）——不计备战席同名 | TFT 预演口径差异，GDD 未定；如需"席内预演"后续加模式参数 |
| 5 | SynergyPanel 每帧 resolve 有小量分配（GC 压力） | 名单 ≤27、60fps 可忽略；优化（名单版本号缓存）留后 |
| 6 | 通知面板 MVP 裁剪：大窗无过滤标签页；三流共享单帧 ≤2 行、超限**丢弃**（不缓存顺延） | render §5.5 完整形态推后；丢行风险=战斗爆发期非关键行 |
| 7 | 背包无上限（GDD/render 未定）；UI 显示前 6 + 总数角标 | 若运营需要上限，加常量与 Buy/equip 前校验即可（落点已隔离在 InventoryPanel） |
| 8 | itch 素材 3 套 ×12 帧须与 `PlaceholderKeys.frameCount` 手工对齐；素材采购/生成是执行期任务 | README 记帧表；缺帧自动回退占位（miss 缓存）不炸 |
| 9 | Android 真机回归沿用 Phase 4 遗留：BACK 键、挂起恢复、横屏 | T10 回归项；GlobalKeyProcessor 已按 BACK=4 处理 |
| 10 | RNG 消耗点回写：architecture §六第 2/3 点本期落地、无新增类目——仅本文档声明，未改 architecture_design.md | 用户如需同步设计文档另行指示 |
| 11 | 命令 tick 戳语义从"入队 tick"改为"执行 tick"（逻辑钟统一入 RunState 的代价） | 回放轨 Phase 6+ 消费历史时按执行序重演（等价）；已在 CP13 测试断言固化 |
| 12 | 文件预算：BattleScreen 预计 ~400 行、BattleRenderer ~430 行 | 超 200~400 建议带但在 800 上限内；后续若再膨胀优先拆 HUD 聚合根 |
| 13 | StartRun 一致性校验失败仅静默 false（装配点 bug 无用户可见反馈） | 以 CP15 测试（seed/sceneId 错位 false）+ 装配评审覆盖 |
| 14 | AbandonRun 在 RESULT 期不合法（裁决矩阵未覆盖 RESULT 列） | 实现口径 #14：胜局必须领箱、败局走继续；Phase 6 随 RunResultScreen 复核 |
| 15 | 通知命令行与 notices 富行可能重复（如卖出既有静态行又有富行） | NotificationFormat 已对 SellUnit 返回 null 去重；其余命令动态行走 notices 单源 |

### 开放问题（遗留，不阻塞）

1. **UnitRegistry**：architecture §2.x 提及但 GDD 无全局池/池耗尽机制可承载——继续推迟，Phase 6 随存档/回放复核是否需要"全局实体登记"。
2. **光环装备**（战歌号角"全体友军"）：需 data_schema §八 effects 增 `target` 字段（结构锁定变更）+ EffectTarget 通道扩展——差异声明 #3，待内容需要时立项。
3. **宝箱 UI 的"宝箱浏览"只读态**（architecture §4.2 列为 UI 态）：本期 ChestDialog 即领即走，无独立浏览态。
4. **零棋子开战的无反馈体验**：允许开战但秒败——横幅 DEFEAT 已有提示，是否加预校验警告（input §4.3 表现层）留内容侧定。

---

## 9. 附录：用户确认记录（2026-08-22，第一轮 6 条 BLOCKER 裁决原样存档）

> 以下为用户对第一轮问题清单的逐条答复（原文口径），为本文档 §4 已确认决策的原始依据。

**Q1 装备系统：选 A 全链实现。** equipments.json 种子内容 + 装备数据层（EquipmentData + JsonLoader 扩展）+ 装备实体（id 空间第二类实体、背包归属）+ EquipItem/UnequipItem 命令 + InventoryPanel（render §九③区）+ passiveStatus 进 StatusSystem + StartBattle 派生插装备修正源（StatModifierSource 预留插点，BattleSystem.java:237 现 singletonList）+ 宝箱装备选项 + 卖出自动卸下（GDD §3.6）。

**Q2 宝箱三选一：选 A 文档定最小可玩规则。** 由文档作者按 GDD 精神拟定并写入实施文档实现层口径：槽1 常驻金币选项（GDD §3.2）、槽2 经验书（数值自定，标注"待调"）、槽3 装备（依 Q1=A 全链，稀有度权重 白70/成25/传5、Boss 箱必含 ≥1 成装及以上）；掉落权重落点自定（建议 GameBalance 常量，说明理由）；装备池 = equipments.json 全集。（落地：§5.3 口径 #1~#3、CP1/CP9。）

**Q3 英雄选择界面：选 B 整体推 Phase 6。** 不做 RunSetupScreen/英雄选择；维持 MainMenu→BattleScreen 流转；本期仅去 DEMO_SEED 硬编码、seed 改由 UI 域边界事件给定（兑现 Phase 4 预留的 StartRun 命令化口径，heroId 参数留 Phase 6 扩展位）；里程碑字样"英雄选择界面"推迟记差异声明。

**Q4 真素材：选 B 字体 + itch 小人。** Fusion Pixel 字体（Phase 4 欠账，.fnt/.png 经 Hiero 生成、OFL LICENSE 入库，Assets.font() 换载零改调用方）+ itch 免费小人替换 2~3 个棋子验证精灵动画流水线（含 LICENSE/来源记录）；Kenney CC0 UI 包继续占位自绘、推后记差异声明（延续 Phase 4 Q4）。素材侧约束沿用：测试不得依赖素材存在（守卫方式沿用 Phase 4 做法）。

**Q5 终局边界：选 A 三屏+存档全推 Phase 6。** RunResultScreen/CodexScreen/存档不做；本期做暂停菜单（Escape/BACK、GlobalKeyProcessor、dialogStage）+ AbandonRun 命令 → RUN_END（扩展现 render/ui/RunEndPanel 的放弃文案）；熟练度结算留纯函数接口 stub 供 Phase 6 接档案域；暂停菜单 MVP = 继续/放弃（设置 Dialog 推 Phase 7）。

**Q6 演示名单：选 A 移除，严格 GDD。** 删除 RunFlowSystem.grantDemoRoster（RunFlowSystem.java:140-153 附近，以实际读码为准——本次实读核对为 :140-153），起始 10 金商店自购；RunFlowSystemTest 等相关断言改口径；units.json 可购池单薄（3 模板）致首轮体验单薄与商店同质化记 WARNING，并可列 units.json 铺量为本期内容任务（是否铺、铺多少由文档给出建议方案，标注为内容性任务不影响架构）。（落地：CP10/CP15/CP31、WARNING-1。）

---

（完）




