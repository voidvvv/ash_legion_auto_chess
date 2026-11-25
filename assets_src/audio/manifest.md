# 音频最小集 manifest（6 SFX + 1 BGM）

> 交付目录：`assets_src/audio/` ｜ 生成日期：2026-11-23
> 规格：OGG Vorbis 44.1kHz；SFX 单声道，BGM 立体声
> **已对齐** `docs/spec_plan/quality_v2/stage1_design.md` §1.2（6 条 SFX 的时长/淡化/峰值）：victory/defeat 1.50s、cast 0.33s、kill 0.30s、hit 0.12s、upgrade 0.58s；首尾 4–5ms 淡入 + 8ms 淡出；峰值 ≤ -1.47 dBFS。
> 处理脚本：`assets_src/tools/build_audio_miniset.py`（可重复执行）＋ `assets_src/tools/synth_stingers.py`
> 确定性：Ogg 编码加 `-fflags +bitexact -serial_offset 1`，同源重跑 sha256 一致（已验证两次）
> 许可：全部 **CC0 1.0**（Kenney 两包 / Paul Wortmann 一曲 / 本项目自制合成），无需署名；来源存证见 `assets_src/sourced/audio-*/PROVENANCE.md`

## 1. 成品清单

| 文件 | 用途 / 触发点 | 时长(s) | 格式 | 峰值(dBFS) | RMS(dBFS) | 积分响度(LUFS) | 来源 | 许可 | SHA-256 |
|---|---|---:|---|---:|---:|---:|---|---|---|
| `sfx_hit.ogg` | 普通/关键命中（codex 限频：普通 140ms、同目标 240ms） | 0.12 | Vorbis 44.1k mono | -1.47 | -12.60 | 短音不适用* | Kenney Impact Sounds（软击+拳击） | CC0 1.0 | `F3786F4136671265D8948719BB1E7DC2B0B594C6C6CD3478C576058C54DD578A` |
| `sfx_cast.ogg` | 关键施法（codex：v0.68） | 0.33 | Vorbis 44.1k mono | -2.72 | -13.48 | 短音不适用* | 自制合成（上扫频+ping） | CC0 1.0（自制） | `83402C19E103E748D2E1884B452DCA9A3731B14E5F60F023907FE5FDAEFAC6DB` |
| `sfx_kill.ogg` | 击倒确认（codex：v0.60） | 0.30 | Vorbis 44.1k mono | -1.56 | -14.33 | 短音不适用* | Kenney Impact Sounds（重拳+金属下坠） | CC0 1.0 | `3EB5C85C9EC9035B1D7103EC0FDD17F739E6B4C1D402632F43B1EF6DA4792AE4` |
| `sfx_victory.ogg` | 战斗胜利（codex：v0.80） | 1.50 | Vorbis 44.1k mono | -4.10 | -16.13 | -15.6（参考） | 自制合成（C 大三和弦上行） | CC0 1.0（自制） | `4631BF97984761B5605EB0285E4718E0D0030C7E04088A8922E5A37E6A2B93FF` |
| `sfx_defeat.ogg` | 战斗失败（含超时；codex：v0.80） | 1.50 | Vorbis 44.1k mono | -2.66 | -14.52 | -15.3（参考） | 自制合成（下行小调+低通） | CC0 1.0（自制） | `7D6AA466C6BCAA3069FB3069EC7C98E431C7B6850B04B201041881BD4FB6AD73` |
| `sfx_upgrade.ogg` | 备战升星 / 合成（codex：v0.68） | 0.58 | Vorbis 44.1k mono | -2.16 | -15.14 | -13.8（参考） | 自制合成 + Kenney RPG Audio（金币） | CC0 1.0（自制 + Kenney） | `CB6A6DA2B50EB99FBFDF9BB681B04B515372426156625778F9A4E8A1317D13E0` |
| `bgm_battle.ogg` | 战斗 BGM 循环（**派单额外项**；codex §1.2 本批未接入） | 120.00 | Vorbis 44.1k stereo | -3.68 | -19.55 | -22.0 | Paul Wortmann《Dark Cavern Ambient》002 | CC0 1.0 | `62AE030E1E9B3DF9E56B0DF7731DA09E8D020FB07DA674272986057AC4D1BE0C` |

\* 时长 <0.6s 时 EBU R128 积分响度门限不适用（会报 -70），按峰值/RMS 验收；`upgrade/victory/defeat` 的 LUFS 仅作参考。全部峰值 ≤ -1.47 dBFS，无过载。

## 2. 源层与处理链

| 成品 | 源层 | 处理（最终裁切按 codex §1.2） |
|---|---|---|
| `sfx_hit` | Kenney `impactSoft_medium_000.ogg` + `impactPunch_medium_000.ogg` | 拳击层 0–0.22s、低通 3.5k、-55%；amix；limiter；loudnorm(-16/TP-1.5)；**trim 0.12 + 4/8ms 淡化** |
| `sfx_cast` | 自制 `cast.wav`（220→880Hz 扫频 + 1318.5Hz ping + 噪声） | loudnorm(-16/TP-1.5)；**trim 0.33 + 5/8ms 淡化** |
| `sfx_kill` | Kenney `impactPunch_heavy_000.ogg` + `impactMetal_light_000.ogg` | 金属层 pitch×0.749；amix；compressor；limiter；loudnorm；**trim 0.30 + 4/8ms 淡化** |
| `sfx_victory` | 自制 `victory.wav`（C5-E5-G5-C6） | loudnorm；**trim 1.50 + 5/8ms 淡化** |
| `sfx_defeat` | 自制 `defeat.wav`（C4-Ab3-F3，1100Hz 低通） | loudnorm；**trim 1.50 + 5/8ms 淡化** |
| `sfx_upgrade` | 自制 `upgrade.wav`（C6/G6 双 ping + 上扫） + Kenney `handleCoins2.ogg` | 金币层 0–0.32s、淡出、-45%；amix；limiter；loudnorm；**trim 0.58 + 4/8ms 淡化** |
| `bgm_battle` | OGA `dark_cavern_ambient_002.ogg`（作者标注 continuous loop） | loudnorm(-21/TP-2)；重采样 44.1k；立体声；Vorbis q5；**不加淡入淡出** |

## 3. 许可结论

- **7/7 为 CC0 1.0**：Kenney 两包（`License.txt` 明写 CC0，可选署名）；Paul Wortmann 曲（OGA 页面标 CC0，002 为 continuous loop）；4 条 stinger 为本项目自制并主动 CC0 放弃权利。
- **无需署名**；建议在 credits 里写 "Audio: Kenney (CC0), Paul Wortmann (CC0)" 作为礼节，不构成义务。
- 未采用任何 CC-BY / 许可含糊的素材；`Music Jingles` 包下载评估后未采用（无胜/败语义、无法在无试听下可靠判别调性），已删除。
- 逐文件来源/作者/许可/源文件 SHA-256 见 `assets_src/sourced/audio-*/PROVENANCE.md` 与 `PROVENANCE-synth.md`。

## 4. 集成注意（给 codex / 实现组）

1. **运行期路径**：codex §1.2 要求 6 个文件放 `assets/audio/`，已按此复制一份（byte-identical）；`assets_src/audio/` 是交付/存证母本。
2. **BGM 不在本批**：`bgm_battle.ogg` 是派单额外要求；codex §1.2 只消费 6 个 SFX，BGM 可先入库、等后续批次接 `Music`，不要塞进 `Gdx.audio.newSound`。
3. **播放接口**：本批按 codex 用 `Gdx.audio.newSound` + `Assets` 持有；`render_design.md` §7.6 的 `Assets.sound()` 若仍硬编码 `.wav`，需改 `.ogg`（或 wav→ogg 回退）。
4. **音量/限频**：按 codex 表的音量（hit 0.24/0.58、cast 0.68、kill 0.60、victory/defeat 0.80、upgrade 0.68）与限频规则执行；文件已统一峰值 ≤ -1.47 dBFS。
5. **循环**：BGM 保留源循环点、未加淡入淡出；先 `setLooping(true)`，若接缝可闻再加 30–50ms 引擎侧交叉淡化。

## 5. 附：机器可读清单

同目录 `manifest.csv`（列：file,use,duration_s,codec,sample_rate,channels,peak_dbfs,rms_dbfs,lufs,source,license,sha256）。
