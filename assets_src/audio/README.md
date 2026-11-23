# 音频最小集（6 SFX + 1 BGM）· 交付说明

> 交付日期：2026-11-23 ｜ 许可：全部 CC0 1.0 ｜ 规格：OGG Vorbis 44.1kHz；SFX 单声道，BGM 立体声
> 清单与哈希：`manifest.md` / `manifest.csv` ｜ 试听检查表：`PREVIEW.md` ｜ 来源存证：`assets_src/sourced/audio-*/PROVENANCE.md`
> **已对齐** `docs/spec_plan/quality_v2/stage1_design.md` §1.2（时长/淡化/峰值/播放接口）。

## 1. 交付内容

| 文件 | 用途 | 时长 | codex 区间 | 达标 |
|---|---|---:|---|---|
| `sfx_hit.ogg` | 普通/关键命中 | 0.12s | 0.05–0.14s | ✅ |
| `sfx_cast.ogg` | 关键施法 | 0.33s | 0.16–0.35s | ✅ |
| `sfx_kill.ogg` | 击倒确认 | 0.30s | 0.15–0.32s | ✅ |
| `sfx_victory.ogg` | 战斗胜利 | 1.50s | 0.6–1.6s | ✅ |
| `sfx_defeat.ogg` | 战斗失败（含超时） | 1.50s | 0.6–1.6s | ✅ |
| `sfx_upgrade.ogg` | 备战升星 / 合成 | 0.58s | 0.25–0.60s | ✅ |
| `bgm_battle.ogg` | 战斗 BGM 循环（**派单额外项**） | 120s | 派单 1–2 分钟 | ✅ |

全部：OGG Vorbis / 44.1kHz；SFX 单声道、BGM 立体声；首尾 4–5ms 淡入 + 8ms 淡出；峰值 ≤ -1.47 dBFS（codex 要求 ≤ -1 dBFS）；无过载。

## 2. 许可结论

**7/7 = CC0 1.0，无需署名，可商用、可修改、可再分发。**

- `sfx_hit` / `sfx_kill`：Kenney《Impact Sounds》（包内 `License.txt` 明写 CC0）。
- `sfx_upgrade` 金币层：Kenney《RPG Audio》（CC0）。
- `bgm_battle`：Paul Wortmann《Dark Cavern Ambient》002（OpenGameArt 页面标 CC0，作者注明 002 为 continuous loop）。
- `sfx_cast` / `sfx_victory` / `sfx_defeat` / `sfx_upgrade` 合成层：本项目原创，主动以 CC0 1.0 放弃权利（脚本 `assets_src/tools/synth_stingers.py`）。
- 礼节性 credits 建议：`Audio: Kenney (CC0), Paul Wortmann (CC0)`——不是义务。

未采用任何 CC-BY / 许可含糊素材；Kenney《Music Jingles》下载评估后未采用（命名无胜/败语义，无法在无试听条件下可靠判别调性），已删除。

## 3. 集成注意

1. **运行期路径**：codex §1.2 要求 6 个文件放 `assets/audio/`；已按此复制一份（byte-identical，`assets/audio/sfx_*.ogg`）。`assets_src/audio/` 是交付/存证母本。
2. **BGM 不在 codex 本批**：`bgm_battle.ogg` 是派单额外要求；codex §1.2 只消费 6 个 SFX，BGM 先入库、后续批次接 `Music`，不要塞进 `Gdx.audio.newSound`。
3. **播放接口**：本批按 codex 用 `Gdx.audio.newSound` + `Assets` 持有并 dispose，屏只管理播放句柄；`render_design.md` §7.6 的 `Assets.sound()` 若仍硬编码 `.wav`，需改 `.ogg`（或 wav→ogg 回退）。
4. **音量/限频**：按 codex 表的音量（hit 0.24/0.58、cast 0.68、kill 0.60、victory/defeat 0.80、upgrade 0.68）与限频规则执行；文件峰值已统一在 -1.47 dBFS 以下。
5. **循环**：BGM 保留源循环点、未加淡入淡出；先 `setLooping(true)`，若接缝可闻再加 30–50ms 引擎侧交叉淡化。

## 4. 重建 / 校验

```bash
# 从仓库根执行（需要 ffmpeg + python）
python assets_src/tools/build_audio_miniset.py
```

- 脚本对 Ogg 使用 `-fflags +bitexact -serial_offset 1`，同源重跑哈希一致；`manifest.csv` 的 sha256 可用于校验。
- 合成参数在 `assets_src/tools/synth_stingers.py` 顶部注释；4 条 stinger 的音高/结构见 `PROVENANCE-synth.md`。
- 本机验收：时长/峰值/RMS/LUFS/频谱图；**人耳试听待 codex / 主人按 `PREVIEW.md` 终审**。
