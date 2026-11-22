# 最终游戏素材清单（v1.1 · 棋盘单位贴图 + UI 抽屉框架件）

> 生成日期：2026-11-22 ｜ 范围：24 个常规棋子 + 9 个 Boss，共 **33 模板 × 12 帧 = 396 帧**；另含 **UI 抽屉框架件 12 张**（2026-11-22 补登，见 §9）
> 源包：0x72《DungeonTileset II》（**CC0 1.0**）｜ 管线：6 档中性灰 + 运行期星级色 tint ｜ 画布：32×32，脚底 y=26
> 状态：**集成测试已通过，待主人目检后决定正式入库**。本文是最终素材清单；源交付在 `assets_src/draft_batch2/`。
> UI 抽屉框架件（§9）：源包 Golden UI（Buch，OpenGameArt，**CC0 1.0**）｜ **已入库（2026-11-22）**，运行期路径 `assets/art/ui/`；存证 `assets_src/sourced/golden-ui/`，交付物在 `assets_src/ui_redesign/`。

---

## 0. 一句话

全部 33 个棋盘单位（24 常规 + 9 Boss）均已具备 12 帧占位替换级贴图：键名与 `PlaceholderKeys` 逐字符一致、32×32、脚底 26、透明硬边、单帧 ≤10 色；来源为单一 CC0 像素包，许可可商用免署名；FX 没有合适源素材，按“不硬凑”保留占位并由渲染层软兜底。

---

## 1. 总览

| 批次 | 内容 | 帧数 | 当前状态 |
|---|---|---|---|
| 批 1（pilot） | 4 个代表棋子 × 12 帧：`unit_warrior_01`、`unit_wolf_pup`、`unit_skeleton_soldier`、`unit_frost_giant` | 48 | **已被批 2 同名武器版覆盖**；文件留在 `assets_src/draft_batch1/` 作历史对照 |
| **批 2（全量）** | **24 常规（4 族 × 6）+ 9 Boss = 33 模板 × 12 帧** | **396** | 已集成测试；`assets_src/draft_batch2/` 为源交付 |
| P3 FX | 技能起手/弹道 16×16、落点 24×24、状态 8×8 等 | **0** | 0x72 无合适 FX；全部保留 `PlaceholderArt`，`Assets.regionFx` 软兜底 |
| UI 抽屉框架件 | 左侧抽屉的底板 / 内凹底 / 槽 / 高亮框 / 标签 / 滚动条 / 把手 / 关闭钮 / 分隔条 / 标题板（必需 12 张，1:1） | **12 张** | **已入库（2026-11-22）**；源包 Golden UI（CC0）；`1_25x/` 备用列不入库；详见 §9 |

- **运行期路径**：`assets/art/units/{unitId}_{anim}_{frame}.png`（`RealArt` 懒加载；缺文件自动回退占位）。
- **源交付路径**：`assets_src/draft_batch2/units/`；当前 `assets/art/units/` 已有 396 个批 2 PNG（集成测试态）。
- **manifest**：`assets_src/draft_batch2/manifest.csv`（396 行，UTF-8 + BOM）与 `manifest.json`。每行含 key、文件、32×32、SHA-256、unitId、race、class、body、variant、accessory、anim、frame、op、bottom_row、colors、来源/许可字段。
- **重复性**：批 2 与 pilot 均为 6 档灰 + 星级 tint；批 2 的 `variants_color/` 只有 24 张常规棋子 idle_0 彩色对照（不含武器），不参与运行。

### P3 FX 摘要

- 78 个 FX key：技能起手/落点 48、亡语落点 6、状态 10、数字 10、通用 4。
- **0 个 FX 文件**：0x72 只有药水/武器/宝箱/心形 UI，没有语义正确的技能/命中/状态特效；把药水当毒/攻、把心当回复属于硬凑，故不做。
- 运行期全部走 `PlaceholderArt` 占位 + `Assets.regionFx` 软兜底（缺键软告警、降级爆点/白片，不崩局）。
- 完整理由与补法：`assets_src/draft_batch2/FX_MAPPING.md`。

---

## 2. 素材来源与许可

| 项 | 值 |
|---|---|
| 素材包 | 16x16 DungeonTileset II |
| 作者 | 0x72 (Robert) |
| 作者页 / 原始来源 | https://0x72.itch.io/dungeontileset-ii |
| 本次下载页（镜像） | https://hydecogames.com/art/packs/0x72-dungeontileset-ii/ |
| 许可 | **CC0 1.0 Universal（公有领域贡献）** |
| 是否需署名 | **否**（仍完整记录作者/来源/许可） |
| 包文件 / SHA-256 | `0x72-dungeontileset-ii.zip` / `41ffdff444064755e1f87fa1512f8e4b6775146d4d85ebfd0d823478b43a7716` |
| 许可存证 | `assets_src/sourced/0x72-dungeontileset-ii/PROVENANCE.md`、`LICENSE.txt`、`provenance/hydecogames-pack.html`、`provenance/cc0-1.0-deed.html` |
| 取件备注 | 作者 itch 页取件时返回 HTTP 429，未能在本机存档；镜像页明确标注原作者/原始链接/CC0，包内 LICENSE 独立佐证。未使用任何许可含糊素材。 |

### 选型对比（摘要）

| 候选 | 许可 | 结论 |
|---|---|---|
| **0x72 DungeonTileset II** | CC0 1.0 | **选用**：一包覆盖 4 族（orc_warrior / wogol / skelet / ogre），怪物自带 idle×4 + run×4 序列帧，16×16 为主、ogre 32×32 |
| Kenney Tiny Dungeon | CC0 1.0 | 备选未用：无明确巨人；全为单帧 tiles，walk/attack 只能整体位移合成 |
| Kenney Tiny Battle | CC0 1.0 | 否决：现代军事题材，与幻想棋盘不符 |
| Kenney Roguelike/RPG pack | CC0 1.0 | 否决：俯视地块/物件为主，没有“种族×职业”角色剪影 |

完整对比见 `assets_src/sourcing/SOURCING_REPORT.md`；备选包存证在 `assets_src/sourced/kenney-tiny-dungeon/`、`kenney-roguelike-rpg/`。

---

## 3. 视觉体系

### 3.1 三轴

| 轴 | 承载 | 本批做法 |
|---|---|---|
| **种族** | 身体族 | 兽人 = orc_warrior / orc_shaman / masked_orc / goblin；野兽 = wogol / chort / muddy / swampy；亡灵 = skelet / necromancer / zombie / tiny_zombie / ice_zombie / big_zombie；巨人 = ogre / big_zombie / imp；Boss 用大身体/风味身体 + Boss 细节 |
| **职业** | 武器/服饰剪影（主人裁决，不叠加 2px 角标） | 战士 = 盾 + 斧/锤/剑/钉锤/砍刀；法师 = 杖 + 球/新月/骷髅/提灯/水晶 + 宽檐帽或兜帽；游侠 = 弓/长弓/标枪/投石索 + 箭袋；刺客 = 兜帽 + 短刃/双刃/弯刀 |
| **星级** | 6 档中性灰 × 星级色 tint | 1★ 骨白 / 2★ 金 / 3★ 紫；贴图本身无色相，颜色全部来自 `StarColors` 乘法 tint |

- 种族标记：兽人保留源图獠牙；野兽加突出嘴鼻；亡灵加下颌线；巨人加双肩甲 + 腰带。标记 class-independent，帽/兜帽不遮。
- **为什么灰度**：彩色源在 3★ 会被压成暗蓝紫、1★ 过暗；灰度版三星干净可辨。证据：`assets_src/validation_grid/previews/color_vs_grey_stars.png`。

### 3.2 盲测验证结论

| 轮次 | 结果 | 处理 |
|---|---|---|
| round-1（外部，未污染） | 种族 **10/16**、职业 **11/16**，未过线；法师 2/4、wogol 2/4、ogre 2/4 | 按错误数据修订：法师杖头 7×7+星芒+宽檐帽；wogol 口鼻；ogre 肩甲+腰带；刺客短刃下移；撤销白块獠牙/2×2 眼窝 |
| round-2（外部，新会话） | **16/16 双轴满分**，四族四职业全 4/4 | 过线；修订 2 视觉规范成为批 2 基线 |

> 纪律说明：round-2 测试方在答题定稿后查 inbox 时意外看到 round-1 的 01~04 答案残片，已如实披露；裁定采信（判读过程先于暴露且全程留痕、02 与泄漏答案相左、05~16 未受影响）。记录见 `assets_src/validation_grid/SEPARABILITY.md`、`REVISION_ROUND2.md`。

验证盘：`assets_src/validation_grid/`（16 格 4 族×4 职业、24px 盲测拼版、答案键、自评）。

---

## 4. 完整清单：24 个常规棋子

> 每单位 12 帧：`idle_0/1`、`walk_0/1`、`attack_0/1/2`、`cast_0/1`、`death_0/1/2`。
> 路径列省略前缀 `assets_src/draft_batch2/units/`；运行期同名为 `assets/art/units/`。
> “帧来源”统一为：idle/walk 来自源包 idle/run 帧；attack/cast/death 为合成帧（lean/rim/squash/rotate，见 §6）。SHA-256 见 manifest 对应 12 行；§7.2 列 idle_0 代表值。

| # | unitId / 名称 | 种族 | 职业 | 源身体精灵 | 武器/服饰剪影 | 帧数 | 帧来源 | 文件路径 |
|---|---|---|---|---|---|---|---|---|
| 1 | `unit_warrior_01` 兽人战士 | 兽人 | 战士 | `monsters/orc_warrior` | 盾 + 斧 | 12 | 源帧 + 合成 | `units/unit_warrior_01_{anim}_{frame}.png` |
| 2 | `unit_boar_rider` 野猪骑士 | 兽人 | 战士 | `monsters/masked_orc` | 盾 + 锤 | 12 | 源帧 + 合成 | `units/unit_boar_rider_{anim}_{frame}.png` |
| 3 | `unit_mage_apprentice` 焰痕祭司 | 兽人 | 法师 | `monsters/orc_shaman` | 7×7 球杖 + 宽檐帽 | 12 | 源帧 + 合成 | `units/unit_mage_apprentice_{anim}_{frame}.png` |
| 4 | `unit_legend_quartermaster` 王家军需官 | 兽人 | 法师 | `monsters/masked_orc` | 新月杖 + 尖帽 | 12 | 源帧 + 合成 | `units/unit_legend_quartermaster_{anim}_{frame}.png` |
| 5 | `unit_shadow_blade` 暗影之刃 | 兽人 | 刺客 | `monsters/goblin` | 双匕首 + 兜帽 | 12 | 源帧 + 合成 | `units/unit_shadow_blade_{anim}_{frame}.png` |
| 6 | `unit_spear_headhunter` 掷矛猎头者 | 兽人 | 游侠 | `monsters/orc_warrior` | 3 标枪 + 手持矛 | 12 | 源帧 + 合成 | `units/unit_spear_headhunter_{anim}_{frame}.png` |
| 7 | `unit_wolf_pup` 狼崽 | 野兽 | 刺客 | `monsters/wogol` | 短刃 + 兜帽 | 12 | 源帧 + 合成 | `units/unit_wolf_pup_{anim}_{frame}.png` |
| 8 | `unit_ambush_panther` 伏击豹 | 野兽 | 刺客 | `monsters/chort` | 双匕首 + 兜帽 | 12 | 源帧 + 合成 | `units/unit_ambush_panther_{anim}_{frame}.png` |
| 9 | `unit_beast_archer` 兽猎手 | 野兽 | 游侠 | `monsters/wogol` | 弓 + 箭袋 | 12 | 源帧 + 合成 | `units/unit_beast_archer_{anim}_{frame}.png` |
| 10 | `unit_ranger_01` 丛林游侠 | 野兽 | 游侠 | `monsters/muddy` | 长弓 + 箭袋 | 12 | 源帧 + 合成 | `units/unit_ranger_01_{anim}_{frame}.png` |
| 11 | `unit_legend_thornhart` 荆棘圣鹿 | 野兽 | 游侠 | `monsters/wogol` | 长弓 + 鹿角 | 12 | 源帧 + 合成 | `units/unit_legend_thornhart_{anim}_{frame}.png` |
| 12 | `unit_fairy_druid` 荒野德鲁伊 | 野兽 | 法师 | `monsters/swampy` | 球杖 + 花 | 12 | 源帧 + 合成 | `units/unit_fairy_druid_{anim}_{frame}.png` |
| 13 | `unit_skeleton_soldier` 骸骨士兵 | 亡灵 | 战士 | `monsters/skelet` | 盾 + 斧 | 12 | 源帧 + 合成 | `units/unit_skeleton_soldier_{anim}_{frame}.png` |
| 14 | `unit_death_knight` 死亡骑士 | 亡灵 | 战士 | `monsters/big_zombie` | 盾 + 剑 | 12 | 源帧 + 合成 | `units/unit_death_knight_{anim}_{frame}.png` |
| 15 | `unit_debt_collector` 收账人 | 亡灵 | 刺客 | `monsters/tiny_zombie` | 短弯刀 + 账本 | 12 | 源帧 + 合成 | `units/unit_debt_collector_{anim}_{frame}.png` |
| 16 | `unit_wraith` 怨灵 | 亡灵 | 法师 | `monsters/necromancer` | 骷髅杖 + 兜帽 | 12 | 源帧 + 合成 | `units/unit_wraith_{anim}_{frame}.png` |
| 17 | `unit_lamplighter` 守灯人 | 亡灵 | 法师 | `monsters/zombie` | 提灯杖 + 兜帽 | 12 | 源帧 + 合成 | `units/unit_lamplighter_{anim}_{frame}.png` |
| 18 | `unit_bowstring_archer` 空弦射手 | 亡灵 | 游侠 | `monsters/ice_zombie` | 弓 + 箭袋 | 12 | 源帧 + 合成 | `units/unit_bowstring_archer_{anim}_{frame}.png` |
| 19 | `unit_frost_giant` 霜巨人 | 巨人 | 战士 | `monsters/ogre` | 盾 + 斧 | 12 | 源帧 + 合成 | `units/unit_frost_giant_{anim}_{frame}.png` |
| 20 | `unit_glacial_giant` 冰霜巨人 | 巨人 | 战士 | `monsters/big_zombie` | 盾 + 锤 | 12 | 源帧 + 合成 | `units/unit_glacial_giant_{anim}_{frame}.png` |
| 21 | `unit_frost_imp` 小雪怪 | 巨人 | 刺客 | `monsters/imp` | 短刃 + 兜帽 | 12 | 源帧 + 合成 | `units/unit_frost_imp_{anim}_{frame}.png` |
| 22 | `unit_assassin_01` 霜峰弃子 | 巨人 | 刺客 | `monsters/ogre` | 双匕首 + 兜帽 | 12 | 源帧 + 合成 | `units/unit_assassin_01_{anim}_{frame}.png` |
| 23 | `unit_rubble_slinger` 碎石仔 | 巨人 | 游侠 | `monsters/big_zombie` | 投石索 + 石块 | 12 | 源帧 + 合成 | `units/unit_rubble_slinger_{anim}_{frame}.png` |
| 24 | `unit_legend_warsong_singer` 冰川颂者 | 巨人 | 法师 | `monsters/ogre` | 水晶杖 + 号角 | 12 | 源帧 + 合成 | `units/unit_legend_warsong_singer_{anim}_{frame}.png` |

> 每族 6 子的“身体 × 武器/附加”组合不重复；同身体不同武器视为不同组合。完整理由见 `assets_src/draft_batch2/MAPPING.md`。

---

## 5. 完整清单：9 个 Boss

| # | bossId / 名称 | 种族 | 职业 | 源身体精灵 | Boss 细节 | 帧数 | 帧来源 | 文件路径 |
|---|---|---|---|---|---|---|---|---|
| 1 | `boss_thorn_mother` 荆棘之母 | 植物 | Boss（PLAIN） | `monsters/swampy` | 藤蔓 + 荆棘冠 + 肩刺 | 12 | 源帧 + 合成 | `units/boss_thorn_mother_{anim}_{frame}.png` |
| 2 | `boss_thorn_true` 荆棘之母·真体 | 植物 | Boss（PLAIN） | `monsters/swampy` | 大鹿角 + 胸口光核 | 12 | 源帧 + 合成 | `units/boss_thorn_true_{anim}_{frame}.png` |
| 3 | `boss_one_eye` 独眼猎神 | 独眼 | Boss（PLAIN） | `monsters/ogre` | 7×7 巨眼 + 双角 | 12 | 源帧 + 合成 | `units/boss_one_eye_{anim}_{frame}.png` |
| 4 | `boss_tomb_colossus` 守陵巨像 | 构造 | Boss（PLAIN） | `monsters/big_zombie` | 方石冠 + 石甲肩块 | 12 | 源帧 + 合成 | `units/boss_tomb_colossus_{anim}_{frame}.png` |
| 5 | `boss_twin_lich` 双生巫妖 | 亡灵 | Boss（PLAIN） | `monsters/necromancer` | 双肩骷髅 + 小冠 | 12 | 源帧 + 合成 | `units/boss_twin_lich_{anim}_{frame}.png` |
| 6 | `boss_bone_duke` 白骨大公 | 亡灵 | Boss（PLAIN） | `monsters/big_zombie` | 骨冠 + 肋骨 + 肩骨 | 12 | 源帧 + 合成 | `units/boss_bone_duke_{anim}_{frame}.png` |
| 7 | `boss_frost_howler` 霜啸雪怪 | 巨人 | Boss（PLAIN） | `monsters/big_zombie` | 毛领 + 双角 | 12 | 源帧 + 合成 | `units/boss_frost_howler_{anim}_{frame}.png` |
| 8 | `boss_star_breaker` 断星者·格罗姆 | 巨人 | Boss（PLAIN） | `monsters/ogre` | 星冠 + 大锤 + 胸口星 | 12 | 源帧 + 合成 | `units/boss_star_breaker_{anim}_{frame}.png` |
| 9 | `boss_star_warden` 星骸守卫 | 巨人 | Boss（PLAIN） | `monsters/ogre` | 星环 + 盾 + 盾上星 | 12 | 源帧 + 合成 | `units/boss_star_warden_{anim}_{frame}.png` |

> Boss 的 class 在 `units.json` 为 `Boss`，映射到 `UnitShapeKit.ClassVariant.PLAIN`；因此**没有按职业轴分配的武器**，只保留 Boss 细节（个别 Boss 自带锤/盾等演出道具，属 Boss 细节而非职业剪影）。9 个 Boss 的“身体 × 细节”组合唯一。

---

## 6. 12 帧结构与合成声明

| key 后缀 | 源 | 处理（manifest `op`） | 说明 |
|---|---|---|---|
| `idle_0` / `idle_1` | idle 0 / 1 | `plain` | 源帧；呼吸/起落 |
| `walk_0` / `walk_1` | run 1 / 3 | `plain` | 源帧；交替步 |
| `attack_0` | idle 0 | `lean_back`（−1px、×0.92） | 合成：蓄力后仰 |
| `attack_1` | run 2 | `lunge`（+2px、×1.06） | 合成：前冲出招 |
| `attack_2` | idle 1 | `lean_fwd`（+1px） | 合成：收势 |
| `cast_0` | idle 0 | `plain` | 起手 |
| `cast_1` | idle 0 | `cast`（1px rim、×1.06） | 合成：亮边脉冲 |
| `death_0` | idle 0 | `plain` | 倒地前 |
| `death_1` | idle 0 | `fall`（squash 0.62 + lean +2、×0.85） | 合成：屈身 |
| `death_2` | idle 0 | `dead`（旋转 90°、×0.75） | 合成：横卧 |

- **源包只有 idle/run 两套序列**；attack/cast/death 是显式声明的程序化合成帧，不是作者原画。manifest 每行 `op` 列可追溯。
- 所有 396 帧：32×32、脚底 y=26、alpha 仅 0/255、单帧 ≤10 色（身体 6 档 + 道具/细节 4 档）。
- 帧顺序/帧数与 `PlaceholderKeys` 一致：idle 2 / walk 2 / attack 3 / cast 2 / death 3。

---

## 7. 校验、哈希与复现

### 7.1 合同校验（2026-11-22，最终版）

| 项 | 结果 |
|---|---|
| key 对账 | `units.json` 33 模板 × 12 = 396；实际 396；`Compare-Object` 零差异 |
| 尺寸 | 396/396 32×32 |
| 基线 | 396/396 最下非透明行 = 26 |
| alpha | 396/396 仅 0/255 |
| 用色 | 最大 10 色/帧（≤10 上限） |
| SHA-256 | manifest 396 行逐行重算，0 不一致 |
| manifest 编码 | UTF-8 + BOM；`race`/`class` 中文正常（兽人/战士 等） |
| pilot manifest 编码 | `draft_batch1/manifest.csv` 已按 UTF-8 + BOM 重存；48 行、哈希/基线仍全绿 |
| 重跑一致性 | 无随机数；重跑 manifest 逐字节一致 |
| 集成测试（claude） | 396/396 契约链生效、SHA 全量对账、917 测试绿、冒烟存活；游戏已带全量素材重启待主人目检 |

### 7.2 代表哈希（每单位 idle_0）

> 每单位 12 帧的完整 SHA-256 见 `assets_src/draft_batch2/manifest.csv`（按 `unit_id` 过滤；同 key 的 `file` 列可直接定位 PNG）。

| # | unitId | idle_0 SHA-256 |
|---|---|---|
| 1 | `unit_warrior_01` | `49e831bc81ad9ccd945ed6783e6e644afafdc48a6112dcfaa583984184602dea` |
| 2 | `unit_boar_rider` | `89b7b0d18b38d423dc4ef27d5b96cef9f2c07c55ae47306a61c54e6296450d84` |
| 3 | `unit_mage_apprentice` | `311a428218ff59061b7eff9e4b8a8b5266ace7eeba6f9bb266821037ff21b117` |
| 4 | `unit_legend_quartermaster` | `c02f7f895b8af622a073d2f4dec690bd4bd238dee91e98d5c4a7e784e6d69b24` |
| 5 | `unit_shadow_blade` | `591ad851abb99474c24988f50c602eb6b45e4f8474edeb13139e94c4f0b4085d` |
| 6 | `unit_spear_headhunter` | `ad2f3253c5011996fb0ea6c59ea6b3fa0ee258cdc1452826f26090ece5e917b5` |
| 7 | `unit_wolf_pup` | `b4231a5ca24a163ac254765a88870e5cbb8f7bc720d998172c242fc3013a500e` |
| 8 | `unit_ambush_panther` | `49dee412dfdd9688098ab4949108607a62e16aa21f2430e7a56b201c0d49b687` |
| 9 | `unit_beast_archer` | `047eb545d2a89a094584013e1937554406b9bc673093e7d289576bc4217b8621` |
| 10 | `unit_ranger_01` | `dcb9594a23b913ac896339545ecf975d3ab8d5851804ac08734fa5347dc616d6` |
| 11 | `unit_legend_thornhart` | `ddd5714eb63a3e3bea10422114a2f04bbdec7811c1f2baad45c2abe6c1ce85ff` |
| 12 | `unit_fairy_druid` | `9f4a93bb956ccf73d36d1d78158da0d2904091d9dfcdf861c058325190fff7b0` |
| 13 | `unit_skeleton_soldier` | `5687f008261844abfb05cf523913108dbc80e353bb169d3d960d0e812143f7b5` |
| 14 | `unit_death_knight` | `7e201d68eb8c0f15605c3b8b559a58060c1262c316755f49967826e8d6d2e747` |
| 15 | `unit_debt_collector` | `cc21250f8d567fbb260ba9528a16a7883c527ea43ef14a999631740398c89be8` |
| 16 | `unit_wraith` | `aca2551f5e39e181858945e52376996acd2127c2c44326588186a4fcdb8f8633` |
| 17 | `unit_lamplighter` | `15b769a603fc4ec330cb9fb44610a20bad90e28107e6e3d23d815b32303bdc98` |
| 18 | `unit_bowstring_archer` | `d96bf869491a59ea9aeaf5054cb91b1997f88f77e2d2b0f2eb4211decd4c1a70` |
| 19 | `unit_frost_giant` | `9bd913f5d64f3d8a3fd15e018bd323c1bb17771a6323d3757c001acd13b6203c` |
| 20 | `unit_glacial_giant` | `99ae26068b88be51539d3a2dbed26717b46aa3ac59bc613264897b086dad7bf7` |
| 21 | `unit_frost_imp` | `24fbf1a366b33a9947243763a5945f8779c6e74b36303b7359313afb02c522e2` |
| 22 | `unit_assassin_01` | `5041db24013f1714debe3a6c890e6e27bf33f2deb287a79228f7d321b4d57c9a` |
| 23 | `unit_rubble_slinger` | `dc63a98e6f164cdc3e454cb6b01e3da719ebf7ac7ac8fa52505edcd33ff06a9e` |
| 24 | `unit_legend_warsong_singer` | `e310d89def5df2f96cbd5dc516724f52a644d1d770b50de2aaa1ccdefe66e677` |
| 25 | `boss_thorn_mother` | `93652fec1b384a9212c0b58316c40c955cc2cbae2ce0e93c2f6d760270714048` |
| 26 | `boss_thorn_true` | `257feacfe8217ce9edbadf3610d5762e4212696291e1686aaf4e69ef95e8df4a` |
| 27 | `boss_one_eye` | `caa0e16a9fab9b92d679c36af1d67733e023dd67de1f4493d0aeb7da44fba793` |
| 28 | `boss_tomb_colossus` | `04e0aac47b93b08a7348819236d979e0495fe57c247cdb49cbdf5e68ccae7159` |
| 29 | `boss_twin_lich` | `df0f4373f67760bf1a4bb67726ce4208ae00ea4f3aa25040769e1a732f13783a` |
| 30 | `boss_bone_duke` | `3425cbeea90c5601f47a61b82efc5abac58631a2cfceb99f06deda245edc61bf` |
| 31 | `boss_frost_howler` | `d26198d29169d5495f04923717aa0f6cde82f73c42b1a39d68797d9f5b57b5bd` |
| 32 | `boss_star_breaker` | `aeb3a435c9fe5763a4712bb3fe8c16d6f37ce3b6b688cbdb45108a60af510ad7` |
| 33 | `boss_star_warden` | `15f604764eef5d6c09b0da7f39dc581232dd8f52278a4c23774c33e8456b7f8c` |

### 7.3 复现

```powershell
# 批 2 全量（396 帧 + manifest + 预览）
powershell -NoProfile -ExecutionPolicy Bypass -File assets_src\tools\make_batch2.ps1

# pilot 回归（48 帧；会被批 2 同名版本覆盖，历史对照用）
powershell -NoProfile -ExecutionPolicy Bypass -File assets_src\tools\process_pilot.ps1

# 16 格验证盘（4 族 × 4 职业）
powershell -NoProfile -ExecutionPolicy Bypass -File assets_src\tools\make_validation_grid.ps1
```

- 脚本无随机数；输入固定（源包 zip + 配置表 + 6 档灰 LUT）；重跑 manifest 逐字节一致。
- 源包 zip SHA-256：`41ffdff444064755e1f87fa1512f8e4b6775146d4d85ebfd0d823478b43a7716`。
- 关键输出：`assets_src/draft_batch2/units/`、`manifest.csv|json`、`previews/`、`variants_color/`。
- 集成命令：`Copy-Item assets_src\draft_batch2\units\*.png assets\art\units\ -Force`，然后 `gradlew :lwjgl3:run`；回退删除对应 PNG 即落占位。

---

## 8. 状态与待决

### 8.1 当前状态

- `assets/art/units/` 现有 **396 个批 2 PNG**（集成测试用），游戏已带全量素材重启；`assets_src/draft_batch2/` 为源交付，SHA/清单以 manifest 为准。
- **尚未做正式“入库”决定**：是否保留、是否提交版本库、是否替换 `art_asset_spec.md` 的旧 201-key 口径，等主人目检后裁决。
- 批 1 pilot 的 48 帧仍在 `assets_src/draft_batch1/`，但 4 个同名单位已在批 2 中升级为带武器版；最终清单以批 2 为准。
- **P3 FX 未交付**：0 个文件，78 个 FX key 全部由 `PlaceholderArt` 生成占位；`Assets.regionFx` 缺键软告警并降级爆点/白片，装饰性贴图不崩局。说明见 `assets_src/draft_batch2/FX_MAPPING.md`。
- **UI 抽屉框架件**：12 张 1:1 必需已于 2026-11-22 入库（`assets/art/ui/`），源包 Golden UI（CC0），见 §9；`1_25x/` 备用列与加分 8 张未入库。

### 8.2 已知偏弱项 / 风险

| 项 | 说明 | 建议 |
|---|---|---|
| 扩展武器变体未二次盲测 | round-2 只验证了 axe/staff/bow/dagger；hammer/sword/mace/cleaver/javelin/sling/longbow/skull/lantern/crystal/dual/knife 是新增 | 主人目检重点看 `roster_24_24px.png`；最弱是 sling（碎石仔）、javelin（掷矛猎头者）、knife（收账人） |
| Boss 24px 细节 | thorn_mother / thorn_true 同用 swampy 大身体，仅荆棘冠 vs 鹿角+光核区分；one_eye 的 7×7 巨眼在 24px 可能被压暗 | 看 `roster_bosses_24px.png`；若不可辨，优先加大鹿角/眼窝/肩刺 |
| tint 观感 | 灰度 + 星级 tint 是“星级=色”的代价：1★ 偏骨白灰、3★ 整只紫；不是全彩 | 备选 A：保持现状；B：代码加“真素材不 tint”分支 + 全彩帧（当前只有 24 张彩色 idle_0 对照，需重生成 396 帧）；C：调灰度对比/色阶（成本最低） |
| 道具固定像素尺寸 | 小身体（goblin/imp/tiny_zombie）道具比例偏大；大身体（ogre/big_zombie）偏小 | 集成测试后按具体单位微调锚点/尺寸，不整体返工 |
| 身体复用 | masked_orc / wogol / big_zombie / ogre 在族内或跨族复用；同族组合唯一，跨族靠种族标记 | 可接受；若主人要求每个单位独立身体，需要第二个 CC0 包或手绘变体 |
| 合成帧 | attack/cast/death 为 lean/rim/squash/rotate 合成，不是原画 | 与 pilot 一致；若主人要求正式动作，需选带 attack/death 序列的包（许可另审） |
| 种族标记近似 | 亡灵下颌线画在 necromancer/zombie 等身体上像嘴线；巨人肩甲/腰带画在 imp/big_zombie 上像护甲 | 可用；若要精修，按身体逐个调标记位置 |

### 8.3 待主人裁决

1. **入库/保留**：`assets/art/units/` 的 396 个批 2 文件是否作为正式素材保留并提交。
2. **tint 观感**：保持灰度 tint，还是走“真素材不 tint + 全彩帧”方案（需代码改动 + 全量彩色帧）。
3. **弱项是否修**：sling/javelin/knife 三个变体、Boss 24px 细节，是否在 v1 内修一版。
4. **FX 是否补**：若需要，按 `FX_MAPPING.md` §4 选 CC0 特效包，先做 4 个代表 key 验证再铺 64 个。
5. **旧文档口径**：`docs/art_asset_spec.md` 仍是 2026-08-22 的 201-key 旧口径；是否用本清单替代/挂链，需裁决。

---

## 9. UI 抽屉框架件（2026-11-22 补登，12 张）

左侧抽屉（取代原屏上备战席与装备背包）用的 UI 框架件：逐张 1:1 单 PNG 放 `assets/art/ui/{id}.png`，文件名 = id；运行期由 `UiArt` 懒加载、`Assets.uiPatch` 按 `UiKeys` 登记的切分线包成真 `NinePatch`，缺件自动回退 `PlaceholderArt` 平色占位。**与 §2~§7 的棋盘单位贴图是两个独立来源包**。

- **运行期路径**：`assets/art/ui/{id}.png`（`UiArt.ROOT = "art/ui/"`，Nearest 过滤；按 640×360 虚拟像素 1:1 出图）。
- **键集与切分线**：代码 `render/UiKeys.java` 为唯一事实源（必需 12 + 加分 8；本节只登记已入库的必需 12）。
- **manifest**：`assets_src/ui_redesign/ui_asset_manifest.csv`（UTF-8 + BOM）与 `.json`——每行含素材名、用途、来源矩形、切分线、许可、尺寸、SHA-256。
- **成品副本 / 预览**：`assets_src/ui_redesign/sourced_assets/`（与运行期同名 12 张）；`assets_src/ui_redesign/previews/`（逐张预览、抽屉 mock、九宫格拉伸测试）。
- **消费方**：`render_design.md` §9.1；逐张规格与出图规范见 `art_asset_spec.md` §11。

### 9.1 素材来源与许可

| 项 | 值 |
|---|---|
| 素材包 | Golden UI（`ui_split.png` 606×414 拆件图 + `ui.psd` 分层源文件） |
| 作者 | Buch |
| 作者页 / 原始来源 | https://opengameart.org/users/buch ／ https://opengameart.org/content/golden-ui |
| 本次下载页（镜像） | 同上（OpenGameArt 原始页，无镜像） |
| 许可 | **CC0 1.0**（Public Domain Dedication） |
| 是否需署名 | **否**；作者希望 credit “Buch / OpenGameArt”，本项目在存证与 manifest 中保留 |
| 包文件 / SHA-256 | `ui_split.png`（21,699 bytes）/ `1b5422a4d66121f6727be855055fa23f0b21fe620bc52da0a117d480f8c7dd75`；`ui.psd`（573,124 bytes）/ `56c660e25a8484fc84ad92c2cfa668937869286560ec17567bd3eb07ec59948d` |
| 许可存证 | `assets_src/sourced/golden-ui/PROVENANCE.md`、`LICENSE.txt`（按 OGA 页面声明整理——包内无独立 LICENSE 文件）、`provenance/opengameart-golden-ui.html`、`provenance/cc0-1.0-deed.html` |
| 取件备注 | 取件日期 2026-11-22。本项目做了裁切 / 九宫格重排 / 调色（深蓝灰石材 + 暗棕木 + 暗金，整体调暗冷化）；脚本 `assets_src/tools/process_ui_assets.ps1`，无手改原图。未使用任何许可含糊素材。 |

### 9.2 完整清单：12 张必需（1:1）

> 路径列省略前缀 `assets/art/ui/`。
> “画法”列：**程序化重绘** = 以 Golden UI 的 16 色盘 / 2px 线宽 / 角钉·木牌母题为约束用脚本生成（拆件图里没有满足“干净四角 + 2px 线 + 无邻件溢出”的九宫格安全裁片）；**直接裁切** = 从 `ui_split.png` 按来源矩形裁出。12 张中 10 张重绘、2 张裁切；**重绘件中 6 张经一次返修**（M02 / M03 / M05 / M06 / M09 / M10，见“返修”列）。SHA-256 逐张见 manifest。

| # | id / 名称 | 用途 | 1:1 尺寸 | 九宫格切分 左/右/上/下 | 画法 | 返修 | 文件路径 |
|---|---|---|---|---|---|---|---|
| M01 | `ui_panel_body_9p` 面板底 | 抽屉面板底（自带外边框） | 64×64 | 12/12/12/12 | 程序化重绘 | — | `ui_panel_body_9p.png` |
| M02 | `ui_inset_9p` 内凹底 | 描述框 / 立绘位 / 装备预览的内凹底 | 24×24 | 6/6/6/6 | 程序化重绘 | **返修**：线宽加到 2px | `ui_inset_9p.png` |
| M03 | `ui_slot_9p` 槽格 | 头像槽 / 装备槽（空槽态） | 36×36 | 8/8/8/8 | 程序化重绘 | **返修**：线宽加到 2px | `ui_slot_9p.png` |
| M04 | `ui_slot_selected_9p` 高亮框 | 选中 / 落点 / 悬停高亮（中心透明，叠在 M03 上） | 36×36 | 8/8/8/8 | 直接裁切 `13,140,18,18` | — | `ui_slot_selected_9p.png` |
| M05 | `ui_tab_active_9p` 标签·选中 | “介绍 / 装备”标签页选中态 | 32×18 | 6/6/6/6 | 程序化重绘 | **返修**：清除 x=29 透明列、端饰移出中段 | `ui_tab_active_9p.png` |
| M06 | `ui_tab_inactive_9p` 标签·未选中 | 标签页未选中态 | 32×18 | 6/6/6/6 | 程序化重绘 | **返修**：同 M05 | `ui_tab_inactive_9p.png` |
| M07 | `ui_scroll_track_9p` 滚动条轨 | 横竖共用 | 16×16 | 4/4/4/4 | 程序化重绘 | — | `ui_scroll_track_9p.png` |
| M08 | `ui_scroll_knob_9p` 滚动条滑块 | 横竖共用 | 12×12 | 4/4/4/4 | 直接裁切 `266,59,12,12` | — | `ui_scroll_knob_9p.png` |
| M09 | `ui_handle` 把手 | 左缘把手（收起态唯一可见件；纵向可拉伸） | 14×72 | 0/0/12/12 | 程序化重绘 | **返修**：拉槽 / 图标位移入上下端帽 | `ui_handle.png` |
| M10 | `ui_btn_close` 关闭钮 | 关闭钮 | 14×14 | 不拉伸 | 程序化重绘 | **返修**：补齐左侧 2×2 透明缺口 | `ui_btn_close.png` |
| M11 | `ui_divider_h` 分隔条 | 横向分隔条 | 24×4 | 4/4/0/0 | 程序化重绘 | — | `ui_divider_h.png` |
| M12 | `ui_title_plate_9p` 标题板 | 标题板 / 收起态角标底 | 48×18 | 8/8/6/6 | 程序化重绘 | — | `ui_title_plate_9p.png` |

> **未入库**：`assets/art/ui/1_25x/`（1.25× 备用列）——Q8 裁定抽屉走不缩放层，1.25× 永不使用。**未交付**：加分 8 张（B01 / B03~B09），相应件走占位。**不采购**：B02 `ui_btn_sell`（Q3：保留拖拽出售区）。
> 对账测试：`UiArtInventoryTest` 4 例——入库后干净检出下实跑（目录文件 ⊆ 键集、必需 12 张 IHDR 尺寸、在位键尺寸、manifest 尺寸 / 切分线 / SHA-256）。
> 返修逐项记录与接入建议：`assets_src/ui_redesign/ASSET_DELIVERY.md` §4 / §4.1；采购口径：`assets_src/ui_redesign/ASSET_REQUIREMENTS.md`。

---

## 附录 A：12 帧 key 模板

```
{unitId}_idle_0.png      {unitId}_idle_1.png
{unitId}_walk_0.png      {unitId}_walk_1.png
{unitId}_attack_0.png    {unitId}_attack_1.png    {unitId}_attack_2.png
{unitId}_cast_0.png      {unitId}_cast_1.png
{unitId}_death_0.png     {unitId}_death_1.png     {unitId}_death_2.png
```

`unitId` 含 `unit_` / `boss_` 前缀，与 `assets/data/units.json` 的 `id` 一致；文件名 = key，放在 `assets/art/units/` 即被 `RealArt` 懒加载。

## 附录 B：文件数量

| 目录 | 文件数 | 说明 |
|---|---|---|
| `assets_src/draft_batch2/units/` | 396 | 33 模板 × 12 帧 |
| `assets_src/draft_batch2/variants_color/` | 24 | 常规棋子 idle_0 彩色对照（不含武器），不参与运行 |
| `assets_src/draft_batch2/previews/` | 10 | roster 4 + contact 5 + star_tints 1 |
| `assets_src/draft_batch2/` 根 | 5 | manifest.csv/json、MAPPING、README、FX_MAPPING |
| `assets/art/units/` | 396 | 集成测试态（claude 已复制） |
| `assets/art/ui/` | 12 | UI 抽屉框架件 1:1（2026-11-22 入库）；`1_25x/` 备用列不入库 |
| `assets_src/ui_redesign/sourced_assets/` | 12 | UI 框架件成品副本（与 `assets/art/ui/` 同名） |
