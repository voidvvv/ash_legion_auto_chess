# 来源与许可记录 — Dark Cavern Ambient（Paul Wortmann，采用）

| 项 | 值 |
|---|---|
| 素材 | Dark Cavern Ambient |
| 作者 | Paul Wortmann（OpenGameArt 用户 `paul-wortmann`） |
| 来源页 | https://opengameart.org/content/dark-cavern-ambient |
| 发布日期 | 2021-10-25 |
| 许可 | **CC0 1.0 Universal（公有领域贡献）** |
| 许可原文 | https://creativecommons.org/publicdomain/zero/1.0/ |
| 是否需署名 | **否**（CC0；本项目仍在 manifest 记录作者） |
| 取件日期 | 2026-11-23 |
| 使用文件 | `dark_cavern_ambient_002.ogg`（1,693,463 bytes） |
| SHA-256 | `D010BB0C3F9E9F855E0CE36486F30673DB414739C02D62344398197927FDC24A` |
| 时长 / 格式 | 120.0s / OGG Vorbis 44.1kHz 立体声 |
| 作者说明 | 001 为淡入淡出版；**002 为 continuous loop（连续循环）**——本项目选用 002 |

## 来源页说明（摘录，来自 `provenance/source-page.html`）

> "Ambient sound for a dark cavern or dungeon.
> 1 fades in and out.
> 2 is a continuous loop."

页面 License(s) 标注 **CC0**；作者同时列出其使用的 CC0 源素材（鬼魂/木头碎裂/碰撞/风等），不改变本曲的 CC0 结论。

## 存证文件

- `provenance/source-page.html` — OpenGameArt 页面快照（标题、作者、CC0 标注、文件列表、"002 is a continuous loop"）
- `dark_cavern_ambient_002.ogg` — 下载原文件（未修改，留档）

## 本项目所做修改

由 `assets_src/tools/build_audio_miniset.py` 完成：`loudnorm=I=-21:TP=-2:LRA=11`、重采样 44.1kHz、保持立体声、OGG Vorbis q5 重编码；**未加淡入/淡出**，保留源文件的循环点。成品：`assets_src/audio/bgm_battle.ogg`。

> 循环接缝：源作者标注 002 为 continuous loop；本机测得首/尾 100ms RMS 分别为 -29.9 / -27.8 dB（环境音自然起伏），未做交叉淡化。引擎侧 `Music.setLooping(true)` 先试原样；若接缝可闻，再在引擎侧加 30–50ms 交叉淡化，不要改源文件。
