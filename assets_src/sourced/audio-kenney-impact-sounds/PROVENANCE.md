# 来源与许可记录 — Kenney Impact Sounds（采用）

| 项 | 值 |
|---|---|
| 素材包 | Impact Sounds (1.0) |
| 作者 | Kenney Vleugels（www.kenney.nl） |
| 来源页 | https://kenney.nl/assets/impact-sounds |
| 下载方式 | 页面 `download-link` 签名地址（2026-11-23） |
| 许可 | **CC0 1.0 Universal（公有领域贡献）** |
| 许可原文 | https://creativecommons.org/publicdomain/zero/1.0/ |
| 是否需署名 | **否**（Kenney 表示"credit would be nice but not mandatory"；本项目仍在 manifest 记录） |
| 取件日期 | 2026-11-23 |
| 包文件 | `assets_src/sourcing/audio/kenney_impact-sounds.zip`（800,850 bytes） |
| SHA-256（zip） | `029D734AF1582474EDF3A694D1B0CEBC97C1C152F2F39FA34D4C2BAFC5DE77F8` |
| 内容 | 130 个 OGG（脚步骤、玻璃/金属/木板/拳击/软击/钟声等） |

## 包内许可摘录（`License.txt`）

```text
Impact Sounds (1.0)
Created/distributed by Kenney (www.kenney.nl)
Creation date: 19-12-2019

License: (Creative Commons Zero, CC0)
http://creativecommons.org/publicdomain/zero/1.0/

This content is free to use in personal, educational and commercial projects.
Support us by crediting Kenney or www.kenney.nl (this is not mandatory)
```

## 存证文件

- `License.txt` — 包内许可原文
- `provenance/source-page.html` — Kenney 素材页快照（CC0 标注、包内容说明）
- `provenance/cc0-1.0-deed.html` — CC0 1.0 摘要页快照
- `Audio/` — 完整 130 个 OGG（保持包内目录结构）

## 本项目使用到的文件

| 源文件 | 字节 | SHA-256 | 用途 |
|---|---:|---|---|
| `Audio/impactSoft_medium_000.ogg` | 5,351 | `7D3BA0BB5E60A11B5D3E558C141303DCF494256675FBF753C0D252D2CF0481E3` | `sfx_hit` 主层（软击） |
| `Audio/impactPunch_medium_000.ogg` | 8,800 | `486988AA2D6440FFC4C62A0E8CCF3C23673BA84424BD4723378D451B7255EB5C` | `sfx_hit` 体感层（拳击） |
| `Audio/impactPunch_heavy_000.ogg` | 11,617 | `B33A8F14068AEC24EC69BA85E5E87FDC41228975F6A1A3E44A6E7D6FC3D9F8D8` | `sfx_kill` 主层 |
| `Audio/impactMetal_light_000.ogg` | 7,651 | `33B5E6E37C6E9D54E07BF5A89B12C76E879F40C1EA83CDD82714DF1D6F9FEC6D` | `sfx_kill` 金属下坠层 |

## 本项目所做修改

由 `assets_src/tools/build_audio_miniset.py` 完成：裁剪、低通、rubberband 变调、amix 叠层、acompressor、loudnorm（SFX `I=-16 LUFS / TP=-1.5 dBTP`）、转 44.1kHz 单声道 OGG Vorbis q5。CC0 允许此类修改与商用；本项目不暗示作者为游戏背书。

> 注意：`Music Jingles` 包曾下载评估（86 条，0.28–1.76s），但其命名无胜/败语义、无试听条件下无法可靠区分大调/小调，未采用，已从 `sourcing/` 删除；本目录只保留实际采用的 Impact Sounds。
