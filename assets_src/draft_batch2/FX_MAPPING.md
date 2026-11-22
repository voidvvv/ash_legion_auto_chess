# P3 FX 键映射结论

> 2026-11-22 ｜ 源包：0x72 DungeonTileset II ｜ 结论：**没有合适的法术/命中/状态特效精灵，全部保留占位，不硬凑。**

## 1. FX 键总量（按 `PlaceholderKeys.enumerateFor` 口径）

| 类别 | 键数 | 尺寸 | 本批处理 |
|---|---|---|---|
| 技能起手 `fx_{skillId}` | 24 | 16×16 | **占位**（0x72 无技能闪光） |
| 技能落点 `fx_{skillId}_burst` | 24 | 24×24 | **占位** |
| 亡语直伤落点 `fx_deathrattle_{unitId}_burst` | 5 | 24×24 | **占位** |
| 亡语弹命中 `fx_deathrattle_projectile_burst` | 1 | 24×24 | **占位** |
| 状态 `fx_status_{type}` | 10 | 8×8 | **占位**（0x72 无心/晕/毒等状态图标） |
| 数字 `fx_digit_0~9` | 10 | 6×10 | 占位（当前无消费） |
| 通用 `fx_cast_default` / `fx_hit_default` / `ui_panel_9slice` / `fx_white` | 4 | 16/12/24/1 | 占位（`ui_panel` 属 UI，不在 P3） |

合计 78 key；其中 P3 关注的 FX = 48 + 6 + 10 = **64**（技能/亡语 54 + 状态 10）。

## 2. 为什么 0x72 不映射

包内可用的非角色精灵只有：

- `potions/flask_*`（红/绿/蓝/黄药水，16×16）——是**道具**，不是状态/命中特效；
- `weapons/*`（剑/斧/锤/杖/刀，16×16）——是**武器图标**，不是 FX；
- `treasure/*`（宝箱/金币）、`ui/ui_heart_*`（心形 UI）、`dungeon-walls/wall_fountain_*`（喷泉动画）——题材/语义都不对。

把药水当“中毒/攻击提升”、把心当“回复”属于**硬凑**：颜色/语义会与 `PalettePick` 的状态色和战斗信息冲突，且 8×8 下药水瓶细节会糊。按派单“没有的留占位不硬凑”，本批不产出 FX 文件。

## 3. 运行期兜底

- `Assets.regionFx(key)`：真素材 → 占位图集 → 仍缺则软告警并降级通用爆点/白片；**装饰性贴图永不崩局**。
- `PlaceholderArt` 继续生成 78 个 FX key 的占位图；`FxKeyCoverageTest` 锁定键集。
- 因此批 2 只交 `units/`（396 帧），FX 无新增文件、无 manifest 行；游戏行为不变。

## 4. 若后续要补 FX

1. 选 CC0 特效包（如 Kenney Particle Pack，CC0；或 0x72 作者的其它包），优先 16×16/24×24/8×8 原生尺寸。
2. 仍需过 24px 可读性 + 调色板 ≤32 + 透明硬边 + 与敌框红/能量黄/3★ 紫的色役检查。
3. 只补 P3 的 64 个 FX key；数字字模当前无消费，不必做。
4. 接入仍走 `RealArt` 懒加载 + `Assets.regionFx` 软失败；建议先做 4 个代表 key（一个起手、一个 burst、一个状态、一个弹道）验证再铺量。
