# 美术素材逐 key 规格清单

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
| `ui_panel_9slice` | **24×24** | 全部面板/卡片/按钮/槽位的底板，**整图拉伸**使用 | **需要**（按"可整体拉伸"底纹设计，见 §6-2） |
| `fx_cast_default` | 16×16 | 技能缺图时的起手闪光兜底 | 可选（有占位） |
| `fx_hit_default` | 12×12 | 普攻/治疗/护盾落点兜底 | 可选（有占位） |
| `fx_white` | 1×1 | 程序着色源（血条/能量条/格底/格线/拖拽高亮/普攻弹道白点） | 不需要（运行时生成） |
| `fx_digit_0` ~ `fx_digit_9` | 6×10 | 伤害数字字模 | 不需要（生成但**当前无消费**，见 §6-3） |

`ui_panel_9slice` 当前被整图拉伸到的目标尺寸（代表性列举，锚点可 grep `PANEL_9SLICE`）：商店卡 84×56（`ShopBar.java:150`）、商店按钮 80×36（`ShopBar.java:165`）、备战席槽 36×40（`BattleRenderer.java:169-171`）、出售区 56×46（`BattleRenderer.java:204-205`）、装备背包槽 36×36（`InventoryPanel.java:134`）、通知小窗/大窗（`NotificationPanel.java:147,165`）、宝箱弹窗（`ChestDialog.java:114`）、暂停菜单（`PauseMenuDialog.java:76`）、悬停预览卡（`HoverPreviewCard.java:166`）、终局面板（`RunEndPanel.java:73`）、主菜单按钮（`MainMenuScreen.java:91`）、开战按钮（`ShoppingHud.java:47`）、羁绊面板（`SynergyPanel.java:70`）等十余处。

### 4.5 无素材需求项（程序绘制，列出防误准备）

| 项 | 现状 |
|---|---|
| 棋盘格底 / 敌区·缓冲带·玩家区分区色 / 格线 | 1×1 白图 + 程序 tint（`BattleRenderer.java:136-150`），**无棋盘贴图消费路径**（背景层属 render §十一 Phase 7 待定项） |
| 血条 / 能量条 / 星级点 | 程序绘制（`UnitView.java:118-132`：血条 24×2、能量条 24×1、星级点 2×2） |
| 拖拽落点高亮（绿/红/金） | 程序 tint（`BattleRenderer.java:333-369`） |
| 伤害飘字 | **像素字体渲染**（`FloatingText.java:61-66`），非贴图 |
| 字体 | Fusion Pixel 12px 位图字体已入库（`Assets.java:20`），非图片素材 |

---

## 5. 消费点对照表（类型 → 画在哪、多大、用哪张）

| 类型 | 消费点 | 显示尺寸 | 用哪张素材 |
|---|---|---|---|
| 战斗棋子 | `UnitView.java:91,100` | 32×32（死亡缩放淡出为像素规则例外） | 各动画各帧 |
| 备战席 / 部署 / 敌阵侦察虚影 | `BattleRenderer.java:320-321` | 32×32 | `idle_0` |
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
2. **`ui_panel_9slice` 是整图拉伸，不是真九宫格**：代码未用 NinePatch（render §7.1"UI 九宫格"尚未落地）。带边框图案的 24×24 拉到 84×56 时边框会变形——现阶段按"可整体拉伸的底纹"（纯色/渐变/噪点）设计；精致边框面板需升级 NinePatch（§9 待确认）。
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

---

## 9. 待确认参数

| 参数 | 缺省建议 | 状态 |
|---|---|---|
| Boss 素材口径 | 现阶段全部按 32×32 交（含 Boss）；48/64 待渲染端支持后切换 | 【待确认】 |
| `ui_panel_9slice` 九宫格升级 | 先交可整体拉伸底纹；NinePatch 升级列为后续独立小任务 | 【待确认】 |
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

---

## 附：决策日志

| 日期 | 决策 | 结论 |
|---|---|---|
| 2026-08-22 | 素材逐 key 规格清单成文 | 依用户要求将代码现状整理为独立文档（挂 render §七）；纯现状整理无冲突裁决；4 项参数留待确认（§9） |
