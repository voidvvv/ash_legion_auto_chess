# 8-bit 风格 stingers 合成（victory / defeat / cast / upgrade）
# 输出：build/audio_tmp/{victory,defeat,cast,upgrade}.wav（44.1k mono 16-bit）
# 说明：纯程序合成，无第三方采样；音色=三角波+正弦泛音，贴合像素风。
# 语义编码：胜利=大三和弦上行琶音；失败=下行小调三音；施法=上扫频+泛音 ping；升星=双 ping 上行。

import math, wave, struct, random
from pathlib import Path

SR = 44100
ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build" / "audio_tmp"
OUT.mkdir(parents=True, exist_ok=True)
random.seed(20261123)

def tri(ph):
    return 4.0 * abs(ph - math.floor(ph + 0.5)) - 1.0

def add_note(buf, start, freq, dur, amp, tau, kind='tri'):
    n0 = int(start * SR)
    n = int(dur * SR)
    ph = 0.0
    for i in range(n):
        idx = n0 + i
        if idx >= len(buf):
            break
        t = i / SR
        env = (1.0 - math.exp(-t / 0.004)) * math.exp(-t / tau)
        ph += freq / SR
        if kind == 'tri':
            s = 0.65 * tri(ph) + 0.25 * math.sin(2 * math.pi * ph * 2) + 0.10 * math.sin(2 * math.pi * ph * 3)
        else:
            s = math.sin(2 * math.pi * ph)
        buf[idx] += amp * env * s

def add_sweep(buf, start, f0, f1, dur, amp, tau):
    n0 = int(start * SR)
    n = int(dur * SR)
    ph = 0.0
    for i in range(n):
        idx = n0 + i
        if idx >= len(buf):
            break
        t = i / SR
        f = f0 * ((f1 / f0) ** (t / dur))
        ph += f / SR
        env = (1.0 - math.exp(-t / 0.006)) * math.exp(-t / tau)
        buf[idx] += amp * env * math.sin(2 * math.pi * ph)

def add_noise(buf, start, dur, amp, tau, hp=2000):
    n0 = int(start * SR)
    n = int(dur * SR)
    prev = 0.0
    for i in range(n):
        idx = n0 + i
        if idx >= len(buf):
            break
        t = i / SR
        x = random.uniform(-1, 1)
        # one-pole lowpass then subtract = cheap highpass
        prev = 0.85 * prev + 0.15 * x
        y = x - prev
        env = (1.0 - math.exp(-t / 0.005)) * math.exp(-t / tau)
        buf[idx] += amp * env * y

def lowpass(buf, cutoff):
    a = math.exp(-2 * math.pi * cutoff / SR)
    y = 0.0
    out = []
    for x in buf:
        y = (1 - a) * x + a * y
        out.append(y)
    return out

def reverb(buf, taps=((0.060, 0.22), (0.115, 0.15), (0.190, 0.10), (0.290, 0.06))):
    extra = int(max(d for d, _ in taps) * SR) + 1
    out = buf + [0.0] * extra
    for d, g in taps:
        di = int(d * SR)
        for i in range(len(buf)):
            out[i + di] += buf[i] * g
    return out

def fade_out(buf, sec=0.25):
    n = int(sec * SR)
    for i in range(n):
        idx = len(buf) - n + i
        if 0 <= idx < len(buf):
            buf[idx] *= (1 - i / n)

def write_wav(name, buf):
    peak = max(1e-9, max(abs(x) for x in buf))
    g = 0.70 / peak
    with wave.open(str(OUT / name), 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        frames = bytearray()
        for x in buf:
            v = int(max(-1.0, min(1.0, x * g)) * 32767)
            frames += struct.pack('<h', v)
        w.writeframes(bytes(frames))

# ---------- victory：C5-E5-G5-C6 上行大三和弦 + 低音根音 ----------
n = int(1.70 * SR)
v = [0.0] * n
for t, f, a, tau in ((0.00, 523.25, 0.30, 0.55), (0.16, 659.25, 0.28, 0.55),
                     (0.32, 783.99, 0.26, 0.55), (0.48, 1046.50, 0.32, 0.75)):
    add_note(v, t, f, 0.95, a, tau, 'tri')
add_note(v, 0.00, 261.63, 1.30, 0.10, 0.90, 'sine')
v = reverb(v, ((0.060, 0.20), (0.120, 0.12), (0.210, 0.07)))
v = v[:n]
fade_out(v, 0.30)
write_wav('victory.wav', v)

# ---------- defeat：C4-Ab3-F3 下行 + 低通 + 低音持续 ----------
n = int(1.90 * SR)
d = [0.0] * n
for t, f, a, tau in ((0.00, 261.63, 0.28, 0.85), (0.45, 207.65, 0.26, 0.85),
                     (0.90, 174.61, 0.30, 1.10)):
    add_note(d, t, f, 1.20, a, tau, 'tri')
add_note(d, 0.00, 130.81, 1.60, 0.09, 1.10, 'sine')
d = lowpass(d, 1100)
d = reverb(d, ((0.090, 0.18), (0.170, 0.10)))
d = d[:n]
fade_out(d, 0.55)
write_wav('defeat.wav', d)

# ---------- cast：上扫频 + 高频 ping + 噪声闪光 ----------
n = int(0.58 * SR)
c = [0.0] * n
add_sweep(c, 0.00, 220.0, 880.0, 0.38, 0.30, 0.28)
add_note(c, 0.02, 1318.51, 0.30, 0.16, 0.12, 'sine')
add_noise(c, 0.00, 0.22, 0.14, 0.10, 2200)
c = reverb(c, ((0.055, 0.22), (0.110, 0.14), (0.190, 0.08)))
c = c[:n]
fade_out(c, 0.10)
write_wav('cast.wav', c)

# ---------- upgrade：C6/G6 双 ping + 上行扫频 ----------
n = int(0.72 * SR)
u = [0.0] * n
add_note(u, 0.00, 1046.50, 0.45, 0.22, 0.22, 'sine')
add_note(u, 0.09, 1567.98, 0.42, 0.18, 0.22, 'sine')
add_sweep(u, 0.00, 523.25, 1046.50, 0.30, 0.12, 0.22)
add_noise(u, 0.02, 0.12, 0.06, 0.06, 3500)
u = reverb(u, ((0.055, 0.18), (0.110, 0.10)))
u = u[:n]
fade_out(u, 0.14)
write_wav('upgrade.wav', u)

print('synth wav done')
