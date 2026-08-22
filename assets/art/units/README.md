# assets/art/units/ — 真素材层（itch 小人）

真素材层目录：`RealArt` 按 key 懒加载 `art/units/{key}.png`（Nearest 过滤），
未命中自动回退运行时占位图集（`PlaceholderArt`）——**缺文件不炸、不阻塞功能**
（render §7.5 渐进替换策略；Phase 5 计划 WARNING-8）。

## 现状（2026-08-22，T4 交付时）

**本目录暂无素材文件。** 计划要求的 3 套 itch 免费小人未能在执行期完成机器可核实的
采购与入库（itch.io 直连不可达，作者 GitHub 镜像不含 LICENSE 文件，许可无法核实——
不伪造素材与许可记录）。以下为待放置清单与约定，素材到位后本 README 需补记
作者 / 商店链接 / 许可。

## 命名约定（= 代码约定，见 `PlaceholderKeys.unitFrame`）

```
{unitId}_{anim}_{frame}.png
```

- `unitId`：units.json 的模板 id
- `anim`：`idle` / `walk` / `attack` / `cast` / `death`
- `frame`：从 0 起的帧序号

## 帧数表（= `PlaceholderKeys.frameCount`，缺帧自动回退占位）

| anim | 帧数 | frame 取值 |
|------|------|-----------|
| idle | 2 | 0, 1 |
| walk | 2 | 0, 1 |
| attack | 3 | 0, 1, 2 |
| cast | 2 | 0, 1 |
| death | 3 | 0, 1, 2 |

## 待放置文件清单（3 棋子 × 12 帧 = 36 个 PNG）

规格：逐帧 32×32、透明底、朝右（左右朝向由渲染层 flip 参数处理，不画两套）。

| 棋子（units.json） | 文件 |
|------|------|
| unit_warrior_01（兽人战士） | `unit_warrior_01_{anim}_{frame}.png` × 12 |
| unit_ranger_01（丛林游侠） | `unit_ranger_01_{anim}_{frame}.png` × 12 |
| unit_assassin_01（暗夜刺客） | `unit_assassin_01_{anim}_{frame}.png` × 12 |

展开示例（unit_warrior_01 全 12 帧）：

```
unit_warrior_01_idle_0.png   unit_warrior_01_idle_1.png
unit_warrior_01_walk_0.png   unit_warrior_01_walk_1.png
unit_warrior_01_attack_0.png unit_warrior_01_attack_1.png unit_warrior_01_attack_2.png
unit_warrior_01_cast_0.png   unit_warrior_01_cast_1.png
unit_warrior_01_death_0.png  unit_warrior_01_death_1.png  unit_warrior_01_death_2.png
```

## 来源要求（Phase 5 计划 CP18 / Q4=B）

- itch.io 免费小人包；许可须允许商用或至少覆盖本项目用途
- 入库时在本文件登记：素材包名 / 作者 / itch 页面链接 / 许可类型与原文链接
  （若素材包自带 LICENSE 文件，复制一份放本目录，如 `LICENSE-<素材包名>.txt`）
- 帧对齐属手工任务（WARNING-8）：素材原始帧数与上表不一致时，挑选/裁切对齐
