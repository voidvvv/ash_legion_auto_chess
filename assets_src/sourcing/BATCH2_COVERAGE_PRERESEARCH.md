# 批 2 覆盖面预研（24 子 · 4×4 种族×职业）

> 2026-11-22 ｜ 状态：**预研，不落成品** ｜ 依据：主人裁决“职业轴=武器/服饰剪影；wogol 按视觉亲缘保留”
> 目的：在批 2 开工前回答两件事——现成包能不能覆盖 24 子？武器剪影这条路在 24px 下可行吗？

---

## 1. 24 子矩阵（units.json 非 Boss，4 族 × 6）

| 种族 | 战士 (6) | 法师 (6) | 刺客 (6) | 游侠 (6) |
|---|---|---|---|---|
| 兽人 (6) | warrior_01, boar_rider | mage_apprentice, legend_quartermaster | shadow_blade | spear_headhunter |
| 野兽 (6) | — | fairy_druid | wolf_pup, ambush_panther | beast_archer, ranger_01, legend_thornhart |
| 亡灵 (6) | skeleton_soldier, death_knight | wraith, lamplighter | debt_collector | bowstring_archer |
| 巨人 (6) | frost_giant, glacial_giant | legend_warsong_singer | frost_imp, assassin_01 | rubble_slinger |

- 职业总数正好各 6：战士 / 法师 / 刺客 / 游侠。
- 种族内职业分布不均：野兽 **0 战士**、3 游侠；兽人 **0 纯野兽型**（都是人形）；巨人 2 刺客且体型跨度大（小雪怪 ↔ 冰霜巨人）。
- 24 子每族 6 个，**每族至少需要 6 个可区分剪影**；交付管线会去掉颜色（星级 tint），所以“换色=新单位”不成立。

---

## 2. 0x72 包体量盘点（现选包）

| 项 | 数量 | 明细 |
|---|---|---|
| 可用怪物身体 | 18 | 兽人 4（orc_warrior / orc_shaman / masked_orc / goblin）；野兽 5（wogol / chort / imp / muddy / swampy）；亡灵 6（skelet / necromancer / zombie / tiny_zombie / ice_zombie / big_zombie）；巨人 3（ogre / big_demon / big_zombie*） |
| 人形职业参考 | 6 | knight / elf / wizzard 各 f/m；有甲/帽/袍，但 **elf 无弓、wizard 无杖**（武器没画出来） |
| 独立武器 | 20 | 剑 ×7、斧 ×2、锤/棒 ×4、刀 ×2、法杖 ×2、其他；**无弓、无矛、无投石索、无盾、无提灯、无鹿角** |
| 序列帧 | idle×4 + run×4 | 无 attack / cast / death 原画 |

\* big_zombie 同时像“亡灵大个”和“巨人”，只能归一族，否则两族共用会削弱种族区分。

**结论**：0x72 能提供 4 族身体，但**不能直接提供 24 个职业剪影**；尤其游侠所需的弓/矛/投石索、法师所需的手持杖、战士的盾，都不在包内。

---

## 3. 其他候选包（覆盖面）

| 候选 | 许可 | 身体/职业覆盖 | 结论 |
|---|---|---|---|
| Kenney Tiny Dungeon | CC0 | 职业：wizard / knight 清楚，但 **无弓、无匕首、无游侠**；种族：兽人/亡灵/野兽（蝙蝠/蜘蛛）可凑，**无巨人**；全单帧 | 不能单独覆盖 24 |
| Kenney Roguelike/RPG pack（1700 assets） | CC0 | 主体是俯视地块/物件/树/箱子；没有“种族×职业”的角色剪影 | 不适合作单位图 |
| 0x72 + 手绘道具库 | CC0 | 身体已有；补 10~15 个 2~6px 道具/服饰即可到 100% | **推荐路线**（见 §5） |
| 分层纸娃娃包（LPC / DCSS 等） | 需逐包核实（LPC 常见 CC-BY-SA/GPL） | 武器/服饰可分层，职业剪影完整；但 64×64 或风格差异、许可 SA 风险 | 备选，先核实再谈 |

> 说明：LPC/DCSS 只作为“若手绘道具路线失败”的备选，不在本预研中下载/落盘；许可未核实前不采用。

---

## 4. 24 子候选映射（身体 + 职业道具 + 缺口）

道具记为：剑/盾/斧/锤（战）、杖/球（法）、匕首/面罩（刺）、弓/矛/投石索（游）。

### 兽人（6）
| 单位 | 职业 | 身体 | 职业道具 | 缺口 |
|---|---|---|---|---|
| warrior_01 | 战 | orc_warrior | 剑 | pilot 已交 |
| boar_rider | 战 | orc_warrior（变体） | 斧 | **无野猪坐骑**，只能做“骑手感”站姿 |
| mage_apprentice | 法 | orc_shaman | 杖 + 兜帽 | 已有袍/帽，补杖即可 |
| legend_quartermaster | 法 | masked_orc | 杖 + 披风/账本 | 面罩偏“刺客”，需靠杖/袍压回法师 |
| shadow_blade | 刺 | goblin / masked_orc | 双匕首 + 面罩 | 匕首需 2~3px |
| spear_headhunter | 游 | orc_warrior | **矛**（4~6px） | 包内无矛，需手绘 |

### 野兽（6）
| 单位 | 职业 | 身体 | 职业道具 | 缺口 |
|---|---|---|---|---|
| wolf_pup | 刺 | wogol | 匕首 | pilot 已交 |
| ambush_panther | 刺 | chort | 匕首 + 低伏姿态 | chort 偏恶魔，豹感弱 |
| beast_archer | 游 | wogol | **弓**（4~5px 弧） | 包内无弓 |
| ranger_01 | 游 | wogol（变体）/ muddy | 弓 + 兜帽 | 需与 wolf_pup 拉开剪影 |
| legend_thornhart | 游 | chort | 弓 + **鹿角**（2~3px 分叉） | 包内无鹿角 |
| fairy_druid | 法 | muddy / swampy | 杖 + 藤蔓/花 | 需自然系点缀 |

### 亡灵（6）
| 单位 | 职业 | 身体 | 职业道具 | 缺口 |
|---|---|---|---|---|
| skeleton_soldier | 战 | skelet | 剑 | pilot 已交 |
| death_knight | 战 | skelet / big_zombie | 剑 + **盾** | 包内无盾 |
| debt_collector | 刺 | tiny_zombie / zombie | 匕首 + 账本 | 账本可 2px |
| wraith | 法 | necromancer | 杖 | 袍/兜帽已有 |
| lamplighter | 法 | necromancer（变体） | 杖 + **提灯** | 包内无提灯 |
| bowstring_archer | 游 | skelet | **弓**（空弦） | 包内无弓 |

### 巨人（6）
| 单位 | 职业 | 身体 | 职业道具 | 缺口 |
|---|---|---|---|---|
| frost_imp | 刺 | imp / chort | 匕首 | 体型小，符合“小雪怪” |
| assassin_01 | 刺 | big_demon | 匕首 + 面罩 | 需压低头部比例 |
| rubble_slinger | 游 | ogre | **投石索/石块** | 包内无投石索 |
| frost_giant | 战 | ogre | 剑/斧 | pilot 已交 |
| glacial_giant | 战 | ogre（变体） | 锤 | 需与 frost_giant 拉开剪影 |
| legend_warsong_singer | 法 | big_demon / ogre | 杖 + 号角/披风 | 包内无号角 |

---

## 5. 可行性判断

### 5.1 0x72 单包：**不满足 24 子直接覆盖**
- 游侠 6 个里 4 个要弓、1 个矛、1 个投石索——包内 **0 个**。
- 法师 6 个只有 2 个自带袍/帽（orc_shaman、necromancer），其余 4 个需要服饰/杖。
- 刺客 6 个只有 2 个爪子/面罩身体（wogol、masked_orc），匕首需手绘。
- 巨人只有 3 个大身体，6 个巨人要复用 + 道具/姿态区分。

### 5.2 “武器/服饰剪影”路线：**可行，但需补一个小道具库**
最小道具集（6 档灰、2~6px、轮廓必须超出身体）：

| 类别 | 道具 | 用途 |
|---|---|---|
| 战士 | 剑（4px）、斧（4px）、盾（4×5px）、锤（4px） | 6 战士 |
| 法师 | 杖 + 顶端球（5~6px）、尖帽/兜帽（3~5px） | 6 法师 |
| 刺客 | 双匕首（2~3px）、面罩/兜帽（2~3px） | 6 刺客 |
| 游侠 | 弓（4~5px 弧）、矛（5~6px）、投石索/石块（2~3px）、鹿角（2~3px） | 6 游侠 |
| 杂项 | 提灯、账本、号角、野猪头（可选） | 名字特征 |

- 约 **12~15 个 2~6px 形状**；全部可在现有 6 档灰管线里绘制/量化，不引入新风格。
- 关键风险：24px 下 1px 道具会消失。弓/矛/杖必须 **≥2px 粗、超出身体 2~3px**；匕首可能只能靠“面罩+短刃”双线索。
- 身体与道具的锚点：0x72 是正面/3-4 视角，手持位置不固定；需为 18 个身体各标 1 个手部锚点（可脚本化，人工校 18 次）。

### 5.3 推荐路径
1. **先做 16 格验证盘**（4 族代表身体 × 4 职业）：orc_warrior / wogol / skelet / ogre × 剑+盾 / 杖 / 弓 / 匕首+面罩。
2. 在 24px 下做盲测：4 族、4 职业是否各自可分；若职业可分度 <3/4，先调道具尺寸/形状，不铺 24。
3. 验证通过后再补 12~15 个道具，按 §4 映射铺 24；每族 6 个必须“身体或道具”至少一项不同。
4. 若验证失败：再评估分层纸娃娃包（LPC/DCSS）——许可与 64→32 缩放是硬门槛，需主人先裁许可。

---

## 6. 给主人的待决点

1. **16 格验证盘**是否作为批 2 的开工前置（建议是；成本约 1 个道具库草样 + 16 张合成图，不铺全量）。
2. 名字特征（野猪坐骑、鹿角、提灯、投石索、号角）是否必须——若必须，道具库 +5 个形状；若可弱化，按职业剪影优先。
3. 巨人族共用 3 个大身体是否可接受；若要求 6 个明显不同的巨人，0x72 不够，需要第二个包或手绘变体。
4. 若 0x72 + 道具路线在 24px 验证不过，是否允许引入分层包（许可 SA 需先裁决）。

---

## 7. 结论（一句话）

0x72 能覆盖 **种族**，覆盖不了 **24 个职业/名字剪影**；武器/服饰剪影路线可行为 **“0x72 身体 + 12~15 个手绘 2~6px 道具”**，但必须先过 **16 格 × 24px 验证盘** 再铺 24 子。批 2 暂缓期间不动成品，等 R8 通过后按此路径开工。

---

## 8. 验证盘（2026-11-22 完成）

- 产物：`assets_src/validation_grid/`（4 族 × 4 职业 × idle，16 张 + 24px 盲测拼版 + 3★ tint + manifest + 脚本）。
- 自读结论：种族 16/16、职业 16/16；刺客 4 格为“中”把握，是外部盲测最可能的失败点。
- 详见 `validation_grid/README.md` 与 `validation_grid/SEPARABILITY.md`；外部盲测协议与通过线见后者 §4。

