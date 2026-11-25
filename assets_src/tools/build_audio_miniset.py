#!/usr/bin/env python3
# 音频最小集构建（CC0 源 + 自制合成 -> assets_src/audio）
# 7 条：sfx_hit / sfx_cast / sfx_kill / sfx_victory / sfx_defeat / sfx_upgrade / bgm_battle
# 依赖：ffmpeg（rubberband/loudnorm/libvorbis）、python
# 运行：从仓库根执行 `python assets_src/tools/build_audio_miniset.py`
# 确定性：Ogg 加 `-fflags +bitexact -serial_offset 1`，同源重跑哈希一致。

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
IMPACT = ROOT / "assets_src/sourced/audio-kenney-impact-sounds/Audio"
RPG = ROOT / "assets_src/sourced/audio-kenney-rpg-audio/Audio"
BGM_SRC = ROOT / "assets_src/sourced/audio-paul-wortmann-dark-cavern-ambient/dark_cavern_ambient_002.ogg"
OUT = ROOT / "assets_src/audio"
TMP = ROOT / "build/audio_tmp"
OUT.mkdir(parents=True, exist_ok=True)
TMP.mkdir(parents=True, exist_ok=True)

OGG_FLAGS = ["-fflags", "+bitexact", "-serial_offset", "1"]
COMMON = ["-ar", "44100", "-c:a", "libvorbis", "-q:a", "5"]
LN = "loudnorm=I=-16:TP=-1.5:LRA=11"


def run(args):
    p = subprocess.run([str(a) for a in args], cwd=str(ROOT), capture_output=True, text=True)
    if p.returncode != 0:
        print("CMD:", " ".join(str(a) for a in args))
        print(p.stdout)
        print(p.stderr)
        raise SystemExit(p.returncode)


def write_filter(name, text):
    (TMP / name).write_text(text, encoding="ascii")


# 0) 合成 4 条 stinger
run([sys.executable, ROOT / "assets_src/tools/synth_stingers.py"])

# 1) sfx_hit：软击 + 拳击体感，<0.3s
write_filter("sfx_hit.txt", """[0:a]aformat=channel_layouts=mono,volume=1.0[a];
[1:a]aformat=channel_layouts=mono,atrim=0:0.22,lowpass=f=3500,volume=0.45[b];
[a][b]amix=inputs=2:normalize=0,alimiter=limit=0.9,loudnorm=I=-16:TP=-1.5:LRA=11,atrim=0:0.12,afade=t=in:st=0:d=0.004,afade=t=out:st=0.112:d=0.008[out]
""")
run(["ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
     "-i", IMPACT / "impactSoft_medium_000.ogg",
     "-i", IMPACT / "impactPunch_medium_000.ogg",
     "-filter_complex_script", TMP / "sfx_hit.txt", "-map", "[out]",
     *OGG_FLAGS, *COMMON, OUT / "sfx_hit.ogg"])

# 2) sfx_cast：上扫频 + 高频 ping + 噪声闪光，<0.6s
run(["ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
     "-i", TMP / "cast.wav", "-af", f"{LN},atrim=0:0.33,afade=t=in:st=0:d=0.005,afade=t=out:st=0.322:d=0.008",
     *OGG_FLAGS, *COMMON, OUT / "sfx_cast.ogg"])

# 3) sfx_kill：重拳 + 金属下坠，<0.5s
write_filter("sfx_kill.txt", """[0:a]aformat=channel_layouts=mono,atrim=0:0.30,lowpass=f=2500,volume=1.0[a];
[1:a]aformat=channel_layouts=mono,rubberband=pitch=0.749,atrim=0:0.30,afade=t=out:st=0.20:d=0.10,volume=0.50[b];
[a][b]amix=inputs=2:normalize=0,acompressor=threshold=0.6:ratio=3:attack=5:release=80,alimiter=limit=0.9,loudnorm=I=-16:TP=-1.5:LRA=11,atrim=0:0.30,afade=t=in:st=0:d=0.004,afade=t=out:st=0.292:d=0.008[out]
""")
run(["ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
     "-i", IMPACT / "impactPunch_heavy_000.ogg",
     "-i", IMPACT / "impactMetal_light_000.ogg",
     "-filter_complex_script", TMP / "sfx_kill.txt", "-map", "[out]",
     *OGG_FLAGS, *COMMON, OUT / "sfx_kill.ogg"])

# 4) sfx_victory：C5-E5-G5-C6 上行大三和弦，1.7s
run(["ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
     "-i", TMP / "victory.wav", "-af", f"{LN},atrim=0:1.50,afade=t=in:st=0:d=0.005,afade=t=out:st=1.492:d=0.008",
     *OGG_FLAGS, *COMMON, OUT / "sfx_victory.ogg"])

# 5) sfx_defeat：C4-Ab3-F3 下行小调，1.88s
run(["ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
     "-i", TMP / "defeat.wav", "-af", f"{LN},atrim=0:1.50,afade=t=in:st=0:d=0.005,afade=t=out:st=1.492:d=0.008",
     *OGG_FLAGS, *COMMON, OUT / "sfx_defeat.ogg"])

# 6) sfx_upgrade：双 ping 上行 + Kenney 金币音，<0.8s
write_filter("sfx_upgrade.txt", """[0:a]aformat=channel_layouts=mono,volume=0.95[a];
[1:a]aformat=channel_layouts=mono,atrim=0:0.32,afade=t=out:st=0.20:d=0.12,volume=0.55[b];
[a][b]amix=inputs=2:normalize=0,alimiter=limit=0.9,loudnorm=I=-16:TP=-1.5:LRA=11,atrim=0:0.58,afade=t=in:st=0:d=0.004,afade=t=out:st=0.572:d=0.008[out]
""")
run(["ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
     "-i", TMP / "upgrade.wav", "-i", RPG / "handleCoins2.ogg",
     "-filter_complex_script", TMP / "sfx_upgrade.txt", "-map", "[out]",
     *OGG_FLAGS, *COMMON, OUT / "sfx_upgrade.ogg"])

# 7) bgm_battle：120s CC0 连续循环，立体声，-21 LUFS
run(["ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
     "-i", BGM_SRC, "-af", "loudnorm=I=-21:TP=-2:LRA=11,aresample=44100",
     "-ac", "2", *OGG_FLAGS, "-c:a", "libvorbis", "-q:a", "5", OUT / "bgm_battle.ogg"])

print("build done")
for f in sorted(OUT.glob("*.ogg")):
    d = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                        "-of", "default=nw=1:nk=1", str(f)],
                       capture_output=True, text=True).stdout.strip()
    print(f"{f.name:<18} {float(d):7.2f}s {f.stat().st_size:8d} bytes")
