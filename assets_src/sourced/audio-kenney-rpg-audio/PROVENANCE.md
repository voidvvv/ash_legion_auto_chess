# 来源与许可记录 — Kenney RPG Audio（采用）

| 项 | 值 |
|---|---|
| 素材包 | RPG Audio |
| 作者 | Kenney Vleugels（Kenney.nl） |
| 来源页 | https://kenney.nl/assets/rpg-audio |
| 下载方式 | 页面 `download-link` 签名地址（2026-11-23） |
| 许可 | **CC0 1.0 Universal（公有领域贡献）** |
| 许可原文 | https://creativecommons.org/publicdomain/zero/1.0/ |
| 是否需署名 | **否**（"Credit (Kenney or www.kenney.nl) would be nice but is not mandatory"） |
| 取件日期 | 2026-11-23 |
| 包文件 | `assets_src/sourcing/audio/kenney_rpg-audio.zip`（964,837 bytes） |
| SHA-256（zip） | `6DBEAF8544DA958D8F2ADCB4A4A4B76C1ADE34A05F8AB9EDCCD327DA7375F38B` |
| 内容 | 52 个 OGG（门/书/布料/脚步/刀剑/金币/金属等 RPG 拟音） |

## 包内许可摘录（`License.txt`）

```text
RPG Audio
by  Kenney Vleugels (Kenney.nl)

License (Creative Commons Zero, CC0)
http://creativecommons.org/publicdomain/zero/1.0/

You may use these assets in personal and commercial projects.
Credit (Kenney or www.kenney.nl) would be nice but is not mandatory.
```

## 存证文件

- `License.txt` — 包内许可原文
- `provenance/source-page.html` — Kenney 素材页快照
- `Audio/` — 完整 52 个 OGG

## 本项目使用到的文件

| 源文件 | 字节 | SHA-256 | 用途 |
|---|---:|---|---|
| `Audio/handleCoins2.ogg` | 13,094 | `4B857968D64F9AC9336A10FFAC6694D7547E1F01566AD6857B30BC8DB3EE6C32` | `sfx_upgrade` 的金币层 |

## 本项目所做修改

由 `assets_src/tools/build_audio_miniset.py` 完成：裁剪 0–0.32s、淡出、与自制上行 ping 叠层、loudnorm、转 44.1kHz 单声道 OGG Vorbis q5。CC0 允许此类修改与商用。
