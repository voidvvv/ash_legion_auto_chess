# 批 2 映射表（24 常规 + 9 Boss）

> 2026-11-22 ｜ 源包：0x72 DungeonTileset II（CC0）｜ 管线：6 档灰 + 星级 tint ｜ 12 帧/单位 ｜ 32×32 ｜ 脚底 y=26
> 视觉规范沿用 `assets_src/validation_grid/REVISION_ROUND2.md`（round-2 盲测 16/16）。

## 1. 24 常规棋子（4 族 × 6）

| # | unitId | 种族 | 职业 | 身体 | 目标高 | 武器/服饰变体 | 附加 |
|---|---|---|---|---|---|---|---|
| 1 | unit_warrior_01 | 兽人 | 战士 | orc_warrior | 21 | axe（盾+斧） | — |
| 2 | unit_boar_rider | 兽人 | 战士 | masked_orc | 21 | hammer（盾+锤） | — |
| 3 | unit_mage_apprentice | 兽人 | 法师 | orc_shaman | 21 | orb（7×7 杖头球 + 宽檐帽） | — |
| 4 | unit_legend_quartermaster | 兽人 | 法师 | masked_orc | 21 | crescent（新月杖 + 尖帽） | — |
| 5 | unit_shadow_blade | 兽人 | 刺客 | goblin | 17 | dual（双匕首 + 兜帽） | — |
| 6 | unit_spear_headhunter | 兽人 | 游侠 | orc_warrior | 21 | javelin（3 支标枪 + 手持矛） | — |
| 7 | unit_wolf_pup | 野兽 | 刺客 | wogol | 19 | dagger（短刃 + 兜帽） | — |
| 8 | unit_ambush_panther | 野兽 | 刺客 | chort | 21 | dual（双匕首） | — |
| 9 | unit_beast_archer | 野兽 | 游侠 | wogol | 19 | bow（弓 + 箭袋） | — |
| 10 | unit_ranger_01 | 野兽 | 游侠 | muddy | 18 | longbow（长弓 + 箭袋） | — |
| 11 | unit_legend_thornhart | 野兽 | 游侠 | wogol | 19 | longbow（长弓） | **antlers**（鹿角） |
| 12 | unit_fairy_druid | 野兽 | 法师 | swampy | 18 | orb（杖头球） | **flower**（花） |
| 13 | unit_skeleton_soldier | 亡灵 | 战士 | skelet | 20 | axe（盾+斧） | — |
| 14 | unit_death_knight | 亡灵 | 战士 | big_zombie | 25 | sword（盾+剑） | — |
| 15 | unit_debt_collector | 亡灵 | 刺客 | tiny_zombie | 17 | knife（短弯刀） | **ledger**（账本） |
| 16 | unit_wraith | 亡灵 | 法师 | necromancer | 21 | skull（骷髅杖 + 兜帽） | — |
| 17 | unit_lamplighter | 亡灵 | 法师 | zombie | 20 | lantern（提灯杖 + 兜帽） | — |
| 18 | unit_bowstring_archer | 亡灵 | 游侠 | ice_zombie | 20 | bow（弓 + 箭袋） | — |
| 19 | unit_frost_giant | 巨人 | 战士 | ogre | 25 | axe（盾+斧） | — |
| 20 | unit_glacial_giant | 巨人 | 战士 | big_zombie | 25 | hammer（盾+锤） | — |
| 21 | unit_frost_imp | 巨人 | 刺客 | imp | 17 | dagger（短刃 + 兜帽） | — |
| 22 | unit_assassin_01 | 巨人 | 刺客 | ogre | 25 | dual（双匕首） | — |
| 23 | unit_rubble_slinger | 巨人 | 游侠 | big_zombie | 25 | sling（投石索 + 石块） | — |
| 24 | unit_legend_warsong_singer | 巨人 | 法师 | ogre | 25 | crystal（水晶杖 + 宽檐帽） | **horn**（号角） |

**每族 6 子身体×武器组合均不重复**（同身体不同武器视为不同组合）：

| 种族 | 组合 |
|---|---|
| 兽人 | orc_warrior+axe / masked_orc+hammer / orc_shaman+orb / masked_orc+crescent / goblin+dual / orc_warrior+javelin |
| 野兽 | wogol+dagger / chort+dual / wogol+bow / muddy+longbow / wogol+longbow+antlers / swampy+orb |
| 亡灵 | skelet+axe / big_zombie+sword / tiny_zombie+knife / necromancer+skull / zombie+lantern / ice_zombie+bow |
| 巨人 | ogre+axe / big_zombie+hammer / imp+dagger / ogre+dual / big_zombie+sling / ogre+crystal |

## 2. 9 Boss（class=Boss → PLAIN 变体，细节按种族放宽）

| # | bossId | 种族 | 身体 | 目标高 | Boss 细节 | 与同包其它 Boss 的区分点 |
|---|---|---|---|---|---|---|
| 1 | boss_thorn_mother | 植物 | swampy | 26 | vines + 荆棘冠 + 肩刺 | 荆棘冠 + 肩刺 |
| 2 | boss_thorn_true | 植物 | swampy | 26 | 大鹿角 + 胸口光核 | 大鹿角 + 光核 |
| 3 | boss_one_eye | 独眼 | ogre | 26 | 单只 5×5 巨眼 + 双角 | 独眼 |
| 4 | boss_tomb_colossus | 构造 | big_zombie | 26 | 石甲肩块 + 方石冠 | 方石冠/石甲 |
| 5 | boss_twin_lich | 亡灵 | necromancer | 26 | 双肩骷髅 + 小冠 | 双骷髅 |
| 6 | boss_bone_duke | 亡灵 | big_zombie | 26 | 骨冠 + 肋骨线 + 肩骨 | 骨冠/肋骨 |
| 7 | boss_frost_howler | 巨人 | big_zombie | 26 | 毛领 + 双角 | 毛领 |
| 8 | boss_star_breaker | 巨人 | ogre | 26 | 星冠 + 大锤 + 胸口星 | 星冠/大锤 |
| 9 | boss_star_warden | 巨人 | ogre | 26 | 星环 + 盾 + 盾上星 | 星环/盾 |

> 身体复用（swampy/ogre/big_zombie/necromancer）是刻意的：9 个 Boss 靠“身体×Boss 细节”组合区分；细节在 32px 可见，24px 下最弱的是 thorn_mother / thorn_true、one_eye，需集成测试确认。

## 3. 12 帧计划（所有单位/Boss 同构）

| key 后缀 | 源 | 处理 |
|---|---|---|
| idle_0 / idle_1 | idle 0 / 1 | plain |
| walk_0 / walk_1 | run 1 / 3 | plain |
| attack_0 | idle 0 | lean −1、×0.92 |
| attack_1 | run 2 | lean +2、×1.06 |
| attack_2 | idle 1 | lean +1 |
| cast_0 | idle 0 | plain |
| cast_1 | idle 0 | 1px rim、×1.06 |
| death_0 | idle 0 | plain |
| death_1 | idle 0 | squash 0.62 + lean +2、×0.85 |
| death_2 | idle 0 | 旋转 90°、×0.75 |

> 源包只有 idle/run；attack/cast/death 为**显式声明的合成帧**，与 pilot 惯例一致。全部 396 帧脚底 y=26。

## 4. 种族标记

沿用 revision 2：兽人保留源图獠牙；野兽加突出嘴鼻；亡灵加下颌线；巨人加 4×2 双肩甲 + 7×2 腰带。非 pilot 身体（goblin/chort/muddy/swampy/zombie/tiny_zombie/ice_zombie/necromancer/big_zombie/imp/big_demon）上的标记为近似，详见 README 自评。

## 5. 许可

全部来自 0x72 DungeonTileset II，CC0 1.0，作者 0x72 (Robert)，源 https://0x72.itch.io/dungeontileset-ii。存证：`assets_src/sourced/0x72-dungeontileset-ii/`。manifest 每行重复记录 source/license/author/attribution。
