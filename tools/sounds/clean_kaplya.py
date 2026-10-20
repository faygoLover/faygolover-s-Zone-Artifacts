# Kaplya (working name; the S.T.A.L.K.E.R. 2 puddle with a liquid cone): the idle bubbling loop.
# Source: approved/kaplya/stalker2_bubbling_video.wav, the sound track of a 68 s capture (48 kHz stereo,
# pulled out of the mp4 with ffmpeg once). Steady bubbling over a hiss/rumble floor; other game sounds
# at 0-2.5 s (tones), 9-10 s, 14.5 s (thump), 37.2-37.6 s and 51.7-55 s (voice-like) stay out of the loop.
# Output: processed/kaplya/ — kaplya_idle.ogg (the loop), kaplya_idle_x3.ogg (three times in a row,
# to listen for the seam), kaplya_clean_full.ogg (the whole cleaned steady stretch, for picking by ear).
import os
import numpy as np
import scipy.ndimage as nd
import scipy.signal as sg
import soundfile as sf

# Same helpers as clean_0135.py (importing it would rerun all of its processing).
SR = 44100
_HERE = os.path.dirname(os.path.abspath(__file__))
SFX = os.environ.get('ZONE_SFX') or next((p for p in (os.path.join(_HERE, '..', '..', '..', n) for n in ('sounds/anomaly_sfx', 'anomaly_sfx'))
                                          if os.path.isdir(p)), os.path.join(_HERE, '..', '..', '..', 'sounds', 'anomaly_sfx'))
APP = SFX + "/approved"
OUT = SFX + "/processed"
NPER = 2048


def load(path):
    x, sr = sf.read(path, always_2d=True)
    x = x.mean(axis=1)
    if sr != SR:
        g = np.gcd(SR, sr)
        x = sg.resample_poly(x, SR // g, sr // g)
    return x


def save(anomaly, name, x, rms_db=None):
    os.makedirs(f"{OUT}/{anomaly}", exist_ok=True)
    if rms_db is not None:
        x = x * (10 ** (rms_db / 20) / (np.sqrt(np.mean(x ** 2)) + 1e-12))
    m = np.max(np.abs(x))
    if m > 0.98:
        x = x / m * 0.98
    with sf.SoundFile(f"{OUT}/{anomaly}/{name}.ogg", 'w', SR, 1, format='OGG', subtype='VORBIS') as f:
        x = x.astype(np.float32)
        for i in range(0, len(x), 8192):
            f.write(x[i:i + 8192])
    print(f"{anomaly}/{name}.ogg  {len(x) / SR:.2f}s  rms {20 * np.log10(np.sqrt(np.mean(x ** 2)) + 1e-12):.1f} dB")


def hp(x, f, o=4):
    return sg.sosfiltfilt(sg.butter(o, f / (SR / 2), 'high', output='sos'), x)


def lp(x, f, o=4):
    return sg.sosfiltfilt(sg.butter(o, f / (SR / 2), 'low', output='sos'), x)


def stft(x):
    return sg.stft(x, SR, nperseg=NPER, noverlap=NPER * 3 // 4)


def istft(Z, n):
    _, y = sg.istft(Z, SR, nperseg=NPER, noverlap=NPER * 3 // 4)
    return y[:n]


def loopify(x, xf):
    n = int(SR * xf)
    t = np.linspace(0, np.pi / 2, n)
    return np.concatenate([x[-n:] * np.cos(t) + x[:n] * np.sin(t), x[n:-n]])


def seg(x, t0, t1):
    return x[int(t0 * SR):int(t1 * SR)]

STEADY = (16.0, 50.5)
LOOP = 18.0
XF = 1.5
AVOID = [(37.1, 37.8)]


def floor_gate(x, ref, pct=20, strength=1.0, floor=0.2, smooth=(3, 5)):
    """Spectral gating against the floor under the bubbles: a low percentile of each frequency's
    magnitude over `ref` (the median would eat the bubbling itself, it never stops)."""
    _, _, R = stft(ref)
    prof = np.percentile(np.abs(R), pct, axis=1, keepdims=True)
    _, _, Z = stft(x)
    gain = np.clip(1.0 - strength * prof / (np.abs(Z) + 1e-12), 0.0, 1.0)
    gain = np.maximum(nd.uniform_filter(gain, size=smooth), floor)
    return istft(Z * gain, len(x))


def bands(x):
    _, _, Z = stft(x)
    p = np.mean(np.abs(Z) ** 2, axis=1)
    edges = np.geomspace(4, len(p), 13).astype(int)
    return 10 * np.log10(np.array([p[a:b].sum() for a, b in zip(edges[:-1], edges[1:])]) + 1e-20)


raw = lp(hp(load(APP + "/kaplya/stalker2_bubbling_video.wav"), 40), 12000)
steady = seg(raw, *STEADY)
clean = floor_gate(steady, steady)

# Loop window: the seam blends the last XF seconds into the first, so the two should sound alike;
# and no single moment should stand out (it would come back every LOOP seconds).
best = None
for s in np.arange(0.0, STEADY[1] - STEADY[0] - LOOP - XF, 0.25):
    t0, t1 = STEADY[0] + s, STEADY[0] + s + LOOP + XF
    if any(a < t1 and b > t0 for a, b in AVOID):
        continue
    w = seg(clean, s, s + LOOP + XF)
    seam = np.mean(np.abs(bands(w[:int(XF * SR)]) - bands(w[-int(XF * SR):])))
    h = int(0.1 * SR)
    e = np.array([np.sqrt(np.mean(w[i:i + h] ** 2)) for i in range(0, len(w) - h, h)])
    spike = 20 * np.log10(e.max() / np.median(e))
    score = seam + 0.5 * spike
    if best is None or score < best[0]:
        best = (score, s, seam, spike)
_, s, seam, spike = best
print(f"loop {STEADY[0] + s:.2f}-{STEADY[0] + s + LOOP + XF:.2f} s  seam {seam:.2f} dB  spike {spike:.1f} dB")

loop = loopify(seg(clean, s, s + LOOP + XF), XF)
gain_db = -26 - 20 * np.log10(np.sqrt(np.mean(loop ** 2)))
save('kaplya', 'kaplya_idle', loop, rms_db=-26)
save('kaplya', 'kaplya_idle_x3', np.tile(loop, 3), rms_db=-26)
save('kaplya', 'kaplya_clean_full', clean * 10 ** (gain_db / 20))
