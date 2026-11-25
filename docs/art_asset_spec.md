# 美术素材逐 key 规格清单

> **2026-11-22 更新**：全量批 2 最终清单见 [`art/asset_inventory.md`](art/asset_inventory.md)（33 模板 × 12 帧 = 396 帧，CC0 0x72 源）。本文是 2026-08-22 的 13 模板 / 201 key 旧口径，保留作历史；**不要按本文的 13/201 数字对账**。
>
> **2026-11-22 更新（UI 抽屉框架素材）**：左侧抽屉的 UI 框架件走独立素材管线（`assets/art/ui/{id}.png` + 真 `NinePatch`）——键集 / 切分线 / 查找链 / 出图规范 / 来源存证见 **§11**。12 张必需（1:1）与 `assets_src/` 交付物**已入库（2026-11-22）**；`1_25x/` 备用列未入库（Q8：抽屉走不缩放层、1.25× 永不使用）；本文 §2~§7（单位 / 技能 / 状态图标）不受影响，仍是 2026-08-22 旧口径。

> 状态：草案待评审 ｜ 归属：独立新文档，挂 `render_design.md` §七（图集与素材规范）之下 ｜ 下游：美术准备真素材直接对照；libgdx-impl-planner 可据此排素材接入任务 ｜ 基准：**代码现状**（2026-08-22 全部 file:line 实读核查）

---

## 1. 一句话概述

把当前游戏**全部图片素材**的 key（文件名）、尺寸、类型（棋子 / 商店头像 / 特效 / UI…）、消费点与现状注意事项列成一张可直接照办的清单——按此准备 PNG 放对位置即生效，缺哪张落哪张占位图，永不阻塞功能。

---

## 2. 通用要求（准备任何素材前先读）

| 项 | 要求 | 依据 |
|---|---|---|
| 坐标基准 | 全部按 **640×360 虚拟分辨率 1:1 出图**；不要为 1280×720 窗口出 2 倍图（缩放由引擎整数倍完成） | render §2.1 / §八 |
| 像素规则 | 整数像素、无抗锯齿；调色板 ≤32 色（美术纪律） | render §八、GDD §9.1 |
| 格式 | PNG、透明底 | 手验清单 §附 |
| 朝向 | 单位一律**朝右**（敌方由代码 `flipX` 水平翻转，不画两套） | render §7.1 |
| 放置路径 | `assets/art/units/{key}.png`（逐帧 PNG 懒加载，**文件名 = key**） | `RealArt.java:21,28-30` |
| 生效方式 | 文件名放对即生效，零配置零代码；缺帧/缺图自动落占位 | `Assets.java:32-42` |
| 许可 | 免费素材只取 CC0 / CC-BY（BY 需署名）；随包放 LICENSE + README 记录作者/链接/许可 | render §7.4 |

---

## 3. 用户想法对照表

| 用户要求 | 本文档落点 |
|---|---|
| 每张图片的 template name | §4.1 主表 13 模板 + key 生成规则（156 张全覆盖）+ §4.2~4.4 逐 key 表 |
| 尺寸大小 | 各表"素材尺寸"列；与"显示尺寸"不一致处单独标注 |
| 类型（棋子、商店头像等） | §4 各节用途说明 + §5 消费点对照表 |

---

## 4. 逐类素材清单

### 4.1 单位精灵 —— 13 模板 × 12 帧 = **156 张**

- **素材尺寸：32×32 / 帧**（图形内容留 2px 描边余量，不要顶满，GDD §9.1）
- **帧数：idle ×2 / walk ×2 / attack ×3 / cast ×2 / death ×3**，帧号从 0 起（GDD §9.3；`PlaceholderKeys.frameCount`，`PlaceholderKeys.java:55-72`）
- **key 规则：`{unitId}_{anim}_{frame}`**（`PlaceholderKeys.java:34-36`；unitId 本身已含 `unit_`/`boss_` 前缀，无额外前缀）
- **156 个 key 全部由上述规则生成，无例外**——逐张列举即"13 模板 × 12 帧"矩阵

| 模板 id（unitId） | 中文名 | 种族/职业 | 类型 | 素材尺寸 | 张数 |
|---|---|---|---|---|---|
| `unit_warrior_01` | 兽人战士 | 兽人/战士 | 普通棋子 | 32×32 | 12 |
| `unit_assassin_01` | 暗夜刺客 | 暗夜/刺客 | 普通棋子 | 32×32 | 12 |
| `unit_ranger_01` | 丛林游侠 | 精灵/游侠 | 普通棋子 | 32×32 | 12 |
| `unit_boar_rider` | 野猪骑士 | 兽人/战士 | 普通棋子 | 32×32 | 12 |
| `unit_wolf_pup` | 狼崽 | 野兽/刺客 | 普通棋子 | 32×32 | 12 |
| `unit_mage_apprentice` | 暗夜学徒 | 暗夜/法师 | 普通棋子 | 32×32 | 12 |
| `unit_fairy_druid` | 精灵德鲁伊 | 精灵/法师 | 普通棋子 | 32×32 | 12 |
| `unit_beast_archer` | 兽猎手 | 野兽/游侠 | 普通棋子 | 32×32 | 12 |
| `unit_shadow_blade` | 暗影之刃 | 暗夜/刺客 | 普通棋子 | 32×32 | 12 |
| `boss_thorn_mother` | 荆棘之母 | 植物/Boss | Boss（见 §6-1） | 32×32（现状） | 12 |
| `boss_one_eye` | 独眼猎神 | 独眼/Boss | Boss（见 §6-1） | 32×32（现状） | 12 |
| `boss_thorn_true` | 荆棘之母·真体 | 植物/Boss | Boss（见 §6-1） | 32×32（现状） | 12 |

数据源：`assets/data/units.json`（13 条）。完整文件名示例（`unit_warrior_01`，其余模板同构替换前缀）：

```
unit_warrior_01_idle_0.png    unit_warrior_01_idle_1.png
unit_warrior_01_walk_0.png    unit_warrior_01_walk_1.png
unit_warrior_01_attack_0.png  unit_warrior_01_attack_1.png  unit_warrior_01_attack_2.png
unit_warrior_01_cast_0.png    unit_warrior_01_cast_1.png
unit_warrior_01_death_0.png   unit_warrior_01_death_1.png   unit_warrior_01_death_2.png
```

帧数不齐不必补画——缺帧自动落占位（手验清单 §附 W-8 设计行为）。

### 4.2 技能特效 —— 11 技能 × 2 = **22 张**

每技能**两张静态图**即可：当前消费形态是"单帧贴图 + 程序缩放淡出"（`FxLayer.java:84-88`），无逐帧特效动画的消费路径——render §5.4 的"一次性 4~6 帧"规格是 Phase 7 升级后的形态。

| key | 素材尺寸 | 用途 | 显示尺寸 |
|---|---|---|---|
| `fx_{skillId}` | **16×16** | 起手闪光（施法者头顶）+ 技能弹道贴图（双用途） | 闪光按原尺寸 ×0.8~1.3 动态缩放（约 13~21px，`FxLayer.java:47-50`）；弹道固定 10×10 显示（`ProjectileView.java:18,38`） |
| `fx_{skillId}_burst` | **24×24** | 落点爆圈（命中/治疗/护盾落点） | 原尺寸 ×0.8~1.3（约 19~31px，`FxLayer.java:54-57`） |

逐技能 key 清单（数据源 `assets/data/skills.json`，11 条）：

| skillId | 中文名 | 起手/弹道（16×16） | 落点（24×24） |
|---|---|---|---|
| `skill_warcry` | 战吼 | `fx_skill_warcry` | `fx_skill_warcry_burst` |
| `skill_execute` | 处决 | `fx_skill_execute` | `fx_skill_execute_burst` |
| `skill_pierce` | 贯穿箭 | `fx_skill_pierce` | `fx_skill_pierce_burst` |
| `skill_thorn_vine` | 荆棘藤蔓 | `fx_skill_thorn_vine` | `fx_skill_thorn_vine_burst` |
| `skill_rampage` | 暴走 | `fx_skill_rampage` | `fx_skill_rampage_burst` |
| `skill_mass_heal` | 群体治疗 | `fx_skill_mass_heal` | `fx_skill_mass_heal_burst` |
| `skill_long_snipe` | 超远程狙击 | `fx_skill_long_snipe` | `fx_skill_long_snipe_burst` |
| `skill_starfall` | 星陨 | `fx_skill_starfall` | `fx_skill_starfall_burst` |
| `skill_poison_cloud` | 毒雾弹 | `fx_skill_poison_cloud` | `fx_skill_poison_cloud_burst` |
| `skill_pierce_sky` | 穿云箭 | `fx_skill_pierce_sky` | `fx_skill_pierce_sky_burst` |
| `skill_thorn_sea` | 荆棘海 | `fx_skill_thorn_sea` | `fx_skill_thorn_sea_burst` |

### 4.3 状态图标 —— **9 张**

- **素材尺寸：8×8**；显示原尺寸，单位头顶横排、每单位最多 4 个、间隔 10px（`FxLayer.java:95-111`）
- key 规则：`fx_status_{type 小写}`（`PlaceholderKeys.java:46-48`）
- 8×8 做不了复杂图案——色块 + 单符号（星/水滴/箭头）是正确密度

| StatusType | key | 词义注记* |
|---|---|---|
| `STUN` | `fx_status_stun` | 眩晕 |
| `BLEED` | `fx_status_bleed` | 流血 |
| `POISON` | `fx_status_poison` | 中毒 |
| `SLOW` | `fx_status_slow` | 减速 |
| `ATK_UP` | `fx_status_atk_up` | 攻击提升 |
| `ATK_DOWN` | `fx_status_atk_down` | 攻击降低 |
| `ASPD_UP` | `fx_status_aspd_up` | 攻速提升 |
| `SHIELD` | `fx_status_shield` | 护盾 |
| `REGEN` | `fx_status_regen` | 持续回复 |

\* 词义注记为类型名直译，非游戏内文案。

### 4.4 UI 通用件与兜底件

| key | 素材尺寸 | 用途 | 需要准备？ |
|---|---|---|---|
| `ui_panel_9slice` | **24×24** | 既有面板/卡片/按钮/槽位的底板，**整图拉伸**使用（**不含左侧抽屉**——抽屉 2026-11-22 起走 `art/ui/*_9p` 真九宫格，见 §11） | **需要**（按"可整体拉伸"底纹设计，见 §6-2） |
| `fx_cast_default` | 16×16 | 技能缺图时的起手闪光兜底 | 可选（有占位） |
| `fx_hit_default` | 12×12 | 普攻/治疗/护盾落点兜底 | 可选（有占位） |
| `fx_white` | 1×1 | 程序着色源（血条/能量条/格底/格线/拖拽高亮/普攻弹道白点） | 不需要（运行时生成） |
| `fx_digit_0` ~ `fx_digit_9` | 6×10 | 伤害数字字模 | 不需要（生成但**当前无消费**，见 §6-3） |

`ui_panel_9slice` 当前被整图拉伸到的目标尺寸（代表性列举，锚点可 grep `PANEL_9SLICE`）：商店卡 84×56（`ShopBar.java:150`）、商店按钮 80×36（`ShopBar.java:165`）、备战席槽 36×40（`BattleRenderer.java:169-171`）、出售区 56×46（`BattleRenderer.java:204-205`）、装备背包槽 36×36（`InventoryPanel.java:134`）、通知小窗/大窗（`NotificationPanel.java:147,165`）、宝箱弹窗（`ChestDialog.java:114`）、暂停菜单（`PauseMenuDialog.java:76`）、悬停预览卡（`HoverPreviewCard.java:166`）、终局面板（`RunEndPanel.java:73`）、主菜单按钮（`MainMenuScreen.java:91`）、开战按钮（`ShoppingHud.java:47`）、羁绊面板（`SynergyPanel.java:70`）等十余处。**2026-11-22**：其中「备战席槽」「装备背包槽」两处已随 ②③ 退役（`InventoryPanel` 已删除；备战席改画在抽屉头像格里，用 `ui_slot_9p` 真九宫格，见 §11）。

### 4.5 无素材需求项（程序绘制，列出防误准备）

| 项 | 现状 |
|---|---|
| 棋盘格底 / 敌区·缓冲带·玩家区分区色 / 格线 | 1×1 白图 + 程序 tint（`BattleRenderer.java:136-150`），**无棋盘贴图消费路径**（背景层属 render §十一 Phase 7 待定项） |
| 血条 / 能量条 / 星级点 | 程序绘制（`UnitView.java:118-132`：血条 24×2、能量条 24×1、星级点 2×2） |
| 拖拽落点高亮（绿/红/金） | 程序 tint（`BattleRenderer.java:333-369`） |
| 伤害飘字 | **像素字体渲染**（`FloatingText.java:61-66`），非贴图 |
| 字体 | Fusion Pixel 12px 位图字体已入库（`Assets.java:20`），非图片素材 |
| 攻击摆动 / 受击抖动 | 纯程序化 transform 叠加层（小幅旋转 / 整数像素位移，`attack_feedback_design.md` 2026-08-23），零新增素材 |

---

## 5. 消费点对照表（类型 → 画在哪、多大、用哪张）

| 类型 | 消费点 | 显示尺寸 | 用哪张素材 |
|---|---|---|---|
| 战斗棋子 | `UnitView.java:91,100` | 32×32（死亡缩放淡出为像素规则例外） | 各动画各帧 |
| 备战席 / 部署 / 敌阵侦察虚影 | `BattleRenderer.java:320-321`（备战席一项 2026-11-22 已迁抽屉，见下行） | 32×32 | `idle_0` |
| **抽屉头像格 / 抽屉立绘**（2026-11-22 新增消费点） | `DrawerStripSection` / `DrawerInfoSection`（经 `UnitFramePainter`） | 32×32 / **64×64（idle 帧放大 2 倍，Q11）** | `idle_0` |
| 拖拽 ghost | `BattleRenderer.java:373-376` | 32×32（半透明 0.65） | `idle_0` |
| **商店头像** | `ShopBar.java:152-153` | **32×32** | **`idle_0`（无独立头像素材！）** |
| 起手闪光 | `FxLayer.java:47-50` | 原尺寸 ×0.8~1.3 | `fx_{skillId}` |
| 技能弹道 | `ProjectileView.java:18,38` | 10×10 | `fx_{skillId}`（16×16 素材压显） |
| 落点爆圈 | `FxLayer.java:54-57` | 原尺寸 ×0.8~1.3 | `fx_{skillId}_burst` |
| 状态图标 | `FxLayer.java:95-111` | 8×8 原尺寸 | `fx_status_{type}` |
| 面板底板 | §4.4 所列十余处 | 整图拉伸至各控件尺寸 | `ui_panel_9slice` |

---

## 6. 现状注意事项（坑位，防返工）

1. **Boss 被压到 32×32 显示**：GDD §9.1 允许 Boss 做 48×48 或 64×64（占 1 格视觉溢出），但当前代码对所有单位统一按 `CELL`（32×32）指定绘制尺寸（`UnitView.java:91,100`、`BattleRenderer.java:320-321`）。现阶段 Boss 素材按 32×32 交；要做 48/64 大图需渲染端加"按素材原始尺寸绘制"的小改动（改 `UnitView` 与 `drawUnitFrame` 两处）——口径见 §9 待确认。
2. **`ui_panel_9slice` 是整图拉伸，不是真九宫格**：代码未用 NinePatch（render §7.1"UI 九宫格"尚未落地）。带边框图案的 24×24 拉到 84×56 时边框会变形——现阶段按"可整体拉伸的底纹"（纯色/渐变/噪点）设计；精致边框面板需升级 NinePatch（§9 待确认）。**2026-11-22**：左侧抽屉已改走真 NinePatch（`UiKeys` / `Assets.uiPatch`，§11）；其余面板（顶栏 / 商店 / 羁绊 / 通知 / 弹窗 / 悬停卡）仍是整图拉伸的 `ui_panel_9slice`。
3. **`fx_digit_0~9` 生成但无消费**：飘字实际走字体渲染（`FloatingText.java:61-66`）。不要为它准备素材；Phase 7 若飘字贴图化再启用。
4. **棋盘没有贴图路径**：想换真棋盘图/地形 = 新增消费路径，属背景层话题（render §3.2① / §十一 Phase 7 待定项），不在本清单范围。
5. **不需要单独做商店头像**：商店卡、备战席、拖拽 ghost 全部复用 `idle_0` 原尺寸（§5），一套 12 帧动画全场景通用。

---

## 7. 素材总量与建议批次

全量 key = **201**：单位 156 + 技能 22 + 状态 9 + 数字 10 + 通用件 4（`PlaceholderKeys.enumerateFor`，`PlaceholderKeys.java:75-99`）。
其中：**必备 188**（156+22+9+panel 1）、可选 2（两兜底件）、**无需准备 11**（white 1 + digit 10）。

| 批次 | 内容 | 数量 | 效果 |
|---|---|---|---|
| ① 验证流水线 | 3 个常用棋子 ×12 帧（如战士/游侠/刺客） | 36 张 | 验证替换链路（手验清单 §附原计划） |
| ② 全量棋子 | 其余 6 普通 + 3 Boss | 120 张 | 战场观感质变 |
| ③ 状态图标 | 9 张 8×8 | 9 张 | 战斗信息可读性 |
| ④ UI 底板 | 1 张 24×24 可拉伸底纹 | 1 张 | 去"色块感" |
| ⑤ 技能特效 | 11 技能 ×2 | 22 张 | 锦上添花（有兜底不急） |

逐 key 兜底机制保证任何批次顺序、任何缺漏都安全——放进去的生效，没放的落占位。

---

## 8. 与现有文档的关系

- **依据**：render §7.1（命名约定）/ §7.4（分源策略与许可）/ §7.5（占位流水线与逐 key 兜底）、GDD §9.1~9.3（尺寸/帧数规格）、手验清单 §附（放置指引）
- **无冲突声明**：本清单为代码现状的整理，不引入新决策；GDD §9.1（Boss 48/64）与代码（统一 32×32）的差异、GDD §9.6（`assets/units/`）与实际路径（`assets/art/units/`）的差异均如实记录于 §6 与 §9，未替用户裁决
- 素材来源多源化（atlas / 精灵图 / 逐帧 PNG）与终态形态的讨论仍在进行中（C1~C6 待裁决），本清单按**当前逐帧 PNG 过渡形态**编写；裁决落定后本清单的"放置路径"节随之一并更新，key 与尺寸规格不受影响
- **UI 抽屉框架件（2026-11-22，§11）**：依据 `render_design.md` §9.1（抽屉结构与几何）、采购单 `assets_src/ui_redesign/ASSET_REQUIREMENTS.md` §1（口径）与 §2（清单）、交付说明 `assets_src/ui_redesign/ASSET_DELIVERY.md`；键集 / 尺寸 / 切分线以代码 `render/UiKeys.java` 为准（本清单照录，`UiKeysTest` 逐字锁定）

---

## 9. 待确认参数

| 参数 | 缺省建议 | 状态 |
|---|---|---|
| Boss 素材口径 | 现阶段全部按 32×32 交（含 Boss）；48/64 待渲染端支持后切换 | 【待确认】 |
| `ui_panel_9slice` 九宫格升级 | 先交可整体拉伸底纹；NinePatch 升级列为后续独立小任务（2026-11-22 部分落地：抽屉已用真九宫格，见 §11；其余面板是否跟进仍待定） | 【待确认】 |
| UI 抽屉素材入库时点 | **已定**：主人终审通过，12 张 1:1 必需与 `assets_src/` 交付物于 2026-11-22 入库；`1_25x/` 备用列不入库（Q8）；`docs/art/asset_inventory.md` §9 已登记 | 已裁决（2026-11-22） |
| UI 抽屉加分 8 张是否补交 | 先不补（缺件走占位、不影响功能）；若目检觉得装备图标位太素，优先补 B07~B09 装备占位图标 | 【待确认】 |
| `fx_digit` 字模去留 | 保留生成（零成本），Phase 7 飘字贴图化时再定 | 【待确认】 |
| 素材目录 | 沿用现状 `assets/art/units/`；GDD §9.6 `assets/units/` 条目随多源体系 C6 裁决一并销账 | 【待确认】 |

---

## 10. 术语对照

| 口语 | 本清单 / GDD 用语 | 代码标识 |
|---|---|---|
| template name | 模板 id（unitId） | `UnitData.getId()` / `units.json` 的 `id` |
| 素材名 / 文件名 | key（region 名） | `PlaceholderKeys.unitFrame/skillFx/...` → `art/units/{key}.png`（`RealArt.pathOf`） |
| 占位图 | 运行时占位图集 | `PlaceholderArt` / `PlaceholderKeys` |
| 商店头像 | （无独立素材）`idle_0` 复用 | `ShopBar.java:152` |
| 图集 / atlas | TextureAtlas（终态形态，render §7.1） | 未接入（多源讨论进行中） |
| 兜底 | 逐 key 回退链 | `Assets.region`（`Assets.java:32-42`） |
| 九宫格 / 切分线 | 九宫格四边不拉伸的固定像素（左 / 右 / 上 / 下） | `UiKeys.Entry` 的 left/right/top/bottom → `Assets.uiPatch`（§11） |
| UI 框架件 | 抽屉底板 / 槽 / 标签 / 滚动条 / 把手 / 关闭钮 | `art/ui/{id}.png`（`UiArt.pathOf`），键集 `UiKeys`（§11） |

---

## 11. UI 抽屉框架素材管线（2026-11-22）

> **状态：已入库（2026-11-22）**——12 张 1:1 必需（`assets/art/ui/`）与 `assets_src/` 交付物已提交入库（主人终审通过）；`1_25x/` 备用列**未入库**（Q8 裁定抽屉走不缩放层，1.25× 永不使用）。加分 8 张未交付，相应件仍显示运行时平色占位。
> **依据**：采购单 `assets_src/ui_redesign/ASSET_REQUIREMENTS.md`（§1 口径 / §2 清单）；交付说明 `assets_src/ui_redesign/ASSET_DELIVERY.md`；存证 `assets_src/sourced/golden-ui/PROVENANCE.md`；代码 `render/UiKeys.java`、`render/UiArt.java`、`render/UiPlaceholderPainter.java`、`render/Assets.java`（HEAD 14ffb45 实读，本节数字与之逐项一致）。消费方：`render_design.md` §9.1。

**大白话**：左侧抽屉的底板、槽格、标签页、滚动条、把手、关闭钮，用的是一套外购的像素风 UI 素材（Golden UI），逐张切成小 PNG 放进 `assets/art/ui/`。每张图登记在代码的一张键表里（叫什么名、多大、九宫格四边各留几像素不拉伸）；图不在就用程序画的纯色占位顶上，绝不因为缺图崩游戏。

### 11.1 文件与键集（`UiKeys`，照录代码）

- 路径 `assets/art/ui/{id}.png`，**文件名 = id**（`UiArt.ROOT = "art/ui/"`、`UiArt.pathOf`）；逐张单 PNG 懒加载，暂不合图集。
- 键集 = **必需 12 + 加分 8 = 20**；B02 `ui_btn_sell` 不采购、不入键集（Q3：保留拖拽出售区）。
- 每条 = id + 1:1 源尺寸 + 九宫格切分线（左 / 右 / 上 / 下；0 = 该边不固定）。切分线是 `Assets.uiPatch` 包 `NinePatch` 的唯一事实源；源尺寸既是占位画布尺寸，也是真图对账的预期值。
- 类加载自检（fail-fast）：切分线非负、左+右 ≤ 源宽、上+下 ≤ 源高、id 互异。

**必需 12 张（M01~M12；1:1 列）**

| # | id | 1:1 源尺寸 | 切分线 左/右/上/下 | 用途 | 实际绘制尺寸 | 来源 |
|---|---|---|---|---|---|---|
| M01 | `ui_panel_body_9p` | 64×64 | 12/12/12/12 | 抽屉面板底 | 244×218 | 程序化重绘 |
| M02 | `ui_inset_9p` | 24×24 | 6/6/6/6 | 内凹底：描述框 / 立绘位 / 装备预览底 | 120×64 / 64×64 / 106×72 | 程序化重绘（返修：线宽 2px） |
| M03 | `ui_slot_9p` | 36×36 | 8/8/8/8 | 头像槽 / 装备槽（空槽态） | 34×34 | 程序化重绘（返修：线宽 2px） |
| M04 | `ui_slot_selected_9p` | 36×36 | 8/8/8/8 | 选中 / 落点 / 悬停高亮框（中心透明，叠在 M03 上） | 34×34 | 直接裁切（`ui_split.png` 13,140,18,18） |
| M05 | `ui_tab_active_9p` | 32×18 | 6/6/6/6 | 标签页·选中（「介绍 / 装备」） | 32×30（纵向拉伸） | 程序化重绘（返修：透明列 / 端饰） |
| M06 | `ui_tab_inactive_9p` | 32×18 | 6/6/6/6 | 标签页·未选中 | 32×30（纵向拉伸） | 程序化重绘（返修：同 M05） |
| M07 | `ui_scroll_track_9p` | 16×16 | 4/4/4/4 | 滚动条轨（横竖共用） | 224×8（头像条）/ 8×72（装备网格） | 程序化重绘 |
| M08 | `ui_scroll_knob_9p` | 12×12 | 4/4/4/4 | 滚动条滑块（横竖共用） | 12（横）/ 8（纵） | 直接裁切（`ui_split.png` 266,59,12,12） |
| M09 | `ui_handle` | 14×72 | 0/0/12/12 | 左缘把手（收起态唯一可见件） | 14×53（纵向收到与头像条分区等高） | 程序化重绘（返修：拉槽 / 图标位移入端帽） |
| M10 | `ui_btn_close` | 14×14 | 不拉伸 | 关闭钮 | 14×14 | 程序化重绘（返修：补齐透明缺口） |
| M11 | `ui_divider_h` | 24×4 | 4/4/0/0 | 横向分隔条 | 232×4（两条） | 程序化重绘 |
| M12 | `ui_title_plate_9p` | 48×18 | 8/8/6/6 | 标题板 / 收起态角标底 | 232×14 / 54×16 | 程序化重绘 |

**加分 8 张（未交付，走占位）**：`ui_corner_ornament` 16×16、`ui_scroll_arrow` 8×8、`ui_slot_hover_9p` 36×36（8/8/8/8）、`ui_preview_board_9p` 80×56（8/8/8/8）、`ui_btn_close_pressed` 14×14、`ui_icon_equip_weapon` / `ui_icon_equip_armor` / `ui_icon_equip_trinket` 各 16×16（以上不拉伸件四边切分线为 0）。当前抽屉内**装备图标位显示的是占位色块**（16px ×2 = 32px）。

### 11.2 查找链（逐 id 兜底）

```
Assets.uiRegion(id) ─→ UiArt.region(id)             art/ui/{id}.png 存在 → TextureRegion
                      └→ PlaceholderArt.region(id)  按 UiKeys 生成的平色占位
                      └→ 仍缺 → 抛 IllegalStateException（"UI 资源缺 key: …"）
Assets.uiPatch(id)  ─→ 按 UiKeys 切分线把 uiRegion(id) 包成 NinePatch（按 id 缓存；未登记 id 直接抛错）
```

- 与单位素材 `Assets.region(key)` 是同一条「真素材 → 占位 → 抛错」链，但**分根**：单位走 `art/units/`（`RealArt`），UI 走 `art/ui/`（`UiArt`）。
- `UiArt`：GL 线程懒加载；**Nearest 过滤**（min 与 mag 都是）；文件不存在返回 null 并把 miss 记入缓存（不每帧探磁盘）；统一 `dispose`。
- 占位：`PlaceholderArt` 构造期逐 `UiKeys.ALL` 条目调用 `UiPlaceholderPainter.paint` 生成——画布 = 条目 1:1 源尺寸，九宫格件按切分线可正确拉伸；明暗仿采购单（面板底最暗 → 内凹再暗一阶 → 槽略亮 → 选中 / 高亮暖金）。`PlaceholderKeys.enumerateFor` 已纳入全部 20 个 UI id，`verifyCoverage` 逐 key 对账防漏。
- 旧通用件 `ui_panel_9slice`（`PlaceholderKeys.PANEL_9SLICE`，24×24 整图硬拉伸）**不在 `UiKeys`**，仍归顶栏 / 商店 / 羁绊 / 通知 / 弹窗 / 悬停卡等既有面板。
- `art/ui/1_25x/` 是备用列，不进代码、**未入库**（Q8 裁定抽屉走不缩放层，1.25× 永不使用；运行期只认 `art/ui/` 顶层的 12 张 1:1）。

### 11.3 出图规范（抽屉走不缩放层）

| 项 | 规范 | 依据 |
|---|---|---|
| 尺寸单位 | 按 **640×360 虚拟像素 1:1 出图**：抽屉挂在不缩放层（反向缩放抵消 0.8 根缩放），素材 1 像素 = 1 舞台像素 = 默认窗口下 2 整屏幕像素。不要 2 倍 / 4 倍高清图 | 采购单 §1 第 1 条；render §9.1 |
| 像素规则 | 整数像素、**Nearest 过滤**（`UiArt` 加载即设）；无抗锯齿、无羽化边 | `UiArt.java`；render §八 |
| 线宽 | **线宽不小于 2px**；不靠 1px 细节传达信息（非整数缩放下 1px 线会时粗时细甚至丢行） | 采购单 §1 第 3 条 |
| 九宫格 | 名字带 `_9p` 的是九宫格件：四角固定、中间可拉伸；四角取偶数、中心留纯色或弱纹理，拉伸后不见接缝。注：采购单 §1 第 4 条另写「四角不小于 6px」，但 M07 / M08 两张小件按其清单表取 4px——以 `UiKeys` 登记值为准 | 采购单 §1 第 4 条；`UiKeys` |
| 透明底 | RGBA PNG，背景 alpha = 0；阴影只画框内，不要框外光晕 / 投影 | 采购单 §1 第 2 条 |
| 风格 | 16px 像素风地牢 UI，与已用的 0x72 DungeonTileset II 协调：深蓝灰石材 + 暗棕木 + 少量暗金；面板底暗于棋盘格；一套内色板统一、总用色 ≤ 32 | 采购单 §1 第 5 条 / §4 |
| 文字 | 一律由游戏字体画，不烘进图（自带文字的按钮不采购） | 采购单 §1 第 10 条 |

### 11.4 来源与存证

| 项 | 值 |
|---|---|
| 来源包 | **Golden UI**（`ui_split.png` 606×414 拆件图 + `ui.psd` 分层源文件） |
| 作者 / 来源页 | **Buch**（OpenGameArt）／ https://opengameart.org/content/golden-ui |
| 许可 | **CC0 1.0**（https://creativecommons.org/publicdomain/zero/1.0/）。署名不强制；作者希望 credit「Buch / OpenGameArt」——本项目在存证与 manifest 中保留 |
| 存证路径 | `assets_src/sourced/golden-ui/`：`PROVENANCE.md`（作者 / 来源 URL / 许可结论 / 文件 SHA-256 / 修改说明 / 返修记录）、`LICENSE.txt`（按 OGA 页面声明整理的说明——包内没有独立 LICENSE 文件）、`provenance/opengameart-golden-ui.html` 与 `provenance/cc0-1.0-deed.html`（页面快照） |
| 取件日期 | 2026-11-22 |
| 成品清单 | `assets_src/ui_redesign/ui_asset_manifest.csv` / `.json`（素材名 / 用途 / 来源矩形 / 切分线 / 许可 / 尺寸 / SHA-256）；成品副本 `assets_src/ui_redesign/sourced_assets/` |
| 预览与拉伸验证 | `assets_src/ui_redesign/previews/`（逐张预览、抽屉 mock、九宫格拉伸测试图） |
| 脚本 | `assets_src/tools/process_ui_assets.ps1`（裁切 / 九宫格 / 调色 / 重绘 + manifest）、`assets_src/tools/make_ui_previews.ps1`（预览 / mock / 拉伸测试）——不手改原图 |

### 11.5 重绘与返修

- 12 张中 **10 张为程序化重绘**（M01 / M02 / M03 / M05 / M06 / M07 / M09 / M10 / M11 / M12），**2 张直接裁切**（M04、M08）。原因：拆件图里没有同时满足「干净四角 + 2px 线 + 无相邻件 / 文字 / 装饰溢出」的九宫格安全裁片。重绘 = 以 Golden UI 的 16 色盘、2px 线宽、角钉 / 木牌母题为约束用脚本生成，不是手改原图，也不是纯色占位。
- **返修 6 张**（2026-11-22，批 0 核验后）：M05 / M06（清除 x=29 透明列、端饰移出中段）、M09（拉槽 / 图标位移入上下端帽）、M10（补齐左侧 2×2 透明缺口）、M02 / M03（线宽加到 2px）。逐项记录见 **`assets_src/ui_redesign/ASSET_DELIVERY.md` §4 / §4.1**（同文 §2 为 12 张成品清单、§6 为交付前自查）。

### 11.6 对账测试

| 测试 | 断什么 |
|---|---|
| `UiKeysTest` | 必需 12 / 加分 8 的 id 与采购单逐字一致、顺序一致；文件名 = `{id}.png`、路径 = `art/ui/{id}.png`；20 键源尺寸与切分线逐一对照采购单；切分线自洽；不拉伸件四边切分线恒 0 |
| `UiArtTest` | `pathOf` 分根；缺文件返回 null（Assets 层落占位）；miss 缓存；`dispose` 清空 |
| `PlaceholderKeysTest` | `enumerateFor` 覆盖全部 UI 框架键（占位必存在、与旧通用件键不碰撞、无重复） |
| `UiArtInventoryTest`（4 例，条件执行） | 目录文件 ⊆ 键集（含大小写逐字符）；必需 12 张 IHDR 尺寸 == 源尺寸；在位任意键尺寸对账；manifest（尺寸 / 切分线 / SHA-256）对账——**入库后干净检出下实跑**（2026-11-22 起）；仅当素材目录或 manifest 缺失时才按设计跳过、不红测 |

### 11.7 入库记录（2026-11-22）

- **已入库**：`assets/art/ui/` 12 张 1:1 必需、`assets_src/sourced/golden-ui/`、`assets_src/ui_redesign/`（文档 / manifest / sourced_assets / previews / 草图）、两个 ps1 脚本。
- **未入库**：`assets/art/ui/1_25x/` 备用列（Q8：抽屉走不缩放层，1.25× 永不使用）。
- 登记：`docs/art/asset_inventory.md` §9（12 条，2026-11-22 补登）。

---

## 附：决策日志

| 日期 | 决策 | 结论 |
|---|---|---|
| 2026-08-22 | 素材逐 key 规格清单成文 | 依用户要求将代码现状整理为独立文档（挂 render §七）；纯现状整理无冲突裁决；4 项参数留待确认（§9） |
| 2026-11-22 | UI 抽屉框架素材管线成文（§11） | 键集 / 尺寸 / 切分线照录代码 `UiKeys`（必需 12 + 加分 8，B02 不采购——Q3 裁决）；查找链 = 真素材 → 占位 → 抛错；Nearest 过滤、不缩放层下按 1:1 出图、线宽不小于 2px；来源 Golden UI（Buch，OpenGameArt，CC0 1.0），存证 `assets_src/sourced/golden-ui/`；12 张必需已产出但**尚未入库**，入库时点待主人目检后定（§9）；同日终审后已入库，见下行 |
| 2026-11-22 | UI 抽屉素材入库（终审后） | 12 张 1:1 必需与 `assets_src/` 交付物提交入库；`1_25x/` 备用列按 Q8（不缩放层、永不使用）**不入库**；`docs/art/asset_inventory.md` §9 补登 12 条 |
