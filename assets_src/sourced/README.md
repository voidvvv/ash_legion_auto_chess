# 外部素材存放区

本目录只放**原始素材包 + 许可存证**，不放游戏运行用的成品。成品在 `assets_src/draft_batch1/`。

| 目录 | 包 | 许可 | 状态 |
|---|---|---|---|
| `0x72-dungeontileset-ii/` | 0x72 DungeonTileset II | CC0 1.0 | **采用**（pilot 4 单位） |
| `kenney-tiny-dungeon/` | Kenney Tiny Dungeon | CC0 1.0 | 备选，未采用 |
| `kenney-roguelike-rpg/` | Kenney Roguelike/RPG pack | CC0 1.0 | 已评估，不采用（俯视地块/物件） |

每个目录内：

- `PROVENANCE.md` — 作者、来源 URL、许可结论、zip SHA-256、修改说明
- `LICENSE.txt` — 包内许可原文
- `provenance/*.html` — 下载页 / 许可页快照
- 原始 zip 与解包目录

## 规则

1. 只收许可清晰（CC0 优先；CC-BY/MIT/页面明示可商用可接受）的素材；含糊的不收。
2. 包内 LICENSE 与来源页同时留证；作者页不可达时记录原因并用镜像/包内许可互证。
3. 不修改原始包；所有裁切/量化/缩放/调色统一由 `assets_src/tools/` 下的脚本生成到 `draft_batch1/`。
4. 成品 manifest 必须能回溯到本目录的具体文件与帧号。
