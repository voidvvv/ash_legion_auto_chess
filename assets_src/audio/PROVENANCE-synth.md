# 来源与许可记录 — 自制 8-bit stingers（本项目原创）

| 项 | 值 |
|---|---|
| 素材 | `sfx_cast` / `sfx_victory` / `sfx_defeat` / `sfx_upgrade` 的合成层 |
| 作者 | 本项目（会诊/采购任务代笔：deepseek） |
| 生成脚本 | `assets_src/tools/synth_stingers.py`（可重复执行，`random.seed(20261123)`） |
| 生成方式 | 纯程序合成：三角波 + 正弦泛音 + 噪声 + 简单多抽头延迟；无任何第三方采样 |
| 许可 | **CC0 1.0 Universal**（本项目主动放弃权利，便于后续替换/分发；出处记录为项目独创） |
| 许可原文 | https://creativecommons.org/publicdomain/zero/1.0/ |
| 取件日期 | 不适用（2026-11-23 生成） |
| 输出 | `build/audio_tmp/{cast,victory,defeat,upgrade}.wav` → 由 `build_audio_miniset.py` 编码为 OGG |

> 最终 OGG 按 `docs/spec_plan/quality_v2/stage1_design.md` §1.2 裁切：cast 0.33s、victory/defeat 1.50s、upgrade 0.58s；源 WAV 保留完整尾音（cast 0.58s / victory 1.70s / defeat 1.90s / upgrade 0.72s），便于按上限 0.35 / 1.60 / 0.60 重新裁切。若主设计师要更长的胜负旋律，改 `build_audio_miniset.py` 的 `atrim` 后重建即可。

## 设计意图（可复核）

| 文件 | 音高/结构 | 语义 |
|---|---|---|
| `victory.wav` | C5(523.25) → E5(659.25) → G5(783.99) → C6(1046.50)，间隔 0.16s；大三和弦上行 + C4 低音 | 胜利，明亮、上扬 |
| `defeat.wav` | C4(261.63) → Ab3(207.65) → F3(174.61)，间隔 0.45s；下行 + 1100Hz 低通 + C3 持续低音 | 失败，下沉、收敛 |
| `cast.wav` | 220→880Hz 上扫频 0.38s + 1318.5Hz ping + 高通噪声闪光 + 延迟 | 施法，短促、有辨识度 |
| `upgrade.wav` | C6(1046.5) / G6(1568) 双 ping（间隔 0.09s）+ 523→1046Hz 上扫 | 升星/合成，上扬、金币感 |

## 备注

- 与 Kenney 实录冲击音（`sfx_hit` / `sfx_kill`）混用：拟音负责"重量"，合成负责"语义"（胜/败/施法/升星）。这是像素游戏的常见分层，若主设计师希望全拟音，可只替换这 4 条，不影响许可。
- `sfx_upgrade` 另叠了 Kenney RPG Audio 的 `handleCoins2.ogg`（CC0），见 `assets_src/sourced/audio-kenney-rpg-audio/PROVENANCE.md`。
