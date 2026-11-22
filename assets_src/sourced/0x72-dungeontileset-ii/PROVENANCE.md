# 来源与许可记录 — 0x72 DungeonTileset II

| 项 | 值 |
|---|---|
| 素材包 | 16x16 DungeonTileset II |
| 作者 | 0x72 (Robert) |
| 作者页 / 原始来源 | https://0x72.itch.io/dungeontileset-ii |
| 本次下载页（镜像） | https://hydecogames.com/art/packs/0x72-dungeontileset-ii/ |
| 镜像整理 | HydeCo（EZ-CC0 asset library） |
| 许可 | **CC0 1.0 Universal（公有领域贡献）** |
| 许可原文 | https://creativecommons.org/publicdomain/zero/1.0/ |
| 是否需署名 | **否**（仍记录作者与来源以便审计） |
| 取件日期 | 2026-11-22 |
| 包文件 | `0x72-dungeontileset-ii.zip`（74,600 bytes） |
| SHA-256 | `41ffdff444064755e1f87fa1512f8e4b6775146d4d85ebfd0d823478b43a7716` |
| 内容 | 151 个 PNG（characters 18 / monsters 34 / weapons 20 / dungeon-walls 45 / 其他 34）+ `LICENSE.txt` |

## 包内许可全文（`LICENSE.txt`）

```text
16x16 DungeonTileset II

By 0x72 (Robert)
Source: https://0x72.itch.io/dungeontileset-ii
Licence: CC0 1.0  https://creativecommons.org/publicdomain/zero/1.0/

Released into the public domain. Use it for anything, free or commercial. No credit needed.

Packed by HydeCo for the EZ-CC0 asset library, hydecogames.com/art/
```

## 存证文件

- `LICENSE.txt` — 包内许可（上）
- `provenance/hydecogames-pack.html` — 下载页快照，含 “CC0 1.0 · free to use for anything with no credit needed” 与作者源链接
- `provenance/cc0-1.0-deed.html` — CC0 1.0 法条/摘要页快照

> 取件时作者 itch 页返回 HTTP 429，未能在本机存档；镜像页明确标注原作者、原始链接与 CC0，包内 LICENSE 亦独立佐证。未使用任何许可含糊的素材。

## 本项目所做修改

由 `assets_src/tools/process_pilot.ps1` 完成：切帧、裁透明边、按亮度排名转 6 档中性灰、最近邻缩放、lean/squash/rotate/rim 姿态合成、32×32 画布脚底 y=26 对齐。产物在 `assets_src/draft_batch1/`。CC0 允许此类修改与商用；本项目不暗示作者为游戏背书。

## 使用到的精灵

| 单位 | 源文件 |
|---|---|
| `unit_warrior_01` | `monsters/orc_warrior_idle_anim.png`、`monsters/orc_warrior_run_anim.png` |
| `unit_wolf_pup` | `monsters/wogol_idle_anim.png`、`monsters/wogol_run_anim.png` |
| `unit_skeleton_soldier` | `monsters/skelet_idle_anim.png`、`monsters/skelet_run_anim.png` |
| `unit_frost_giant` | `monsters/ogre_idle_anim.png`、`monsters/ogre_run_anim.png` |
