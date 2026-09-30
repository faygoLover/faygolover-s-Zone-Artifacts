# 0.1.34.2: cleans up the user's field recordings (anomaly_sfx/approved) into game sounds.
# No ffmpeg: reads/writes through soundfile. Output goes to anomaly_sfx/processed/<anomaly>/ for
# listening; the mod's copies are made by sync_0135.py from there and from approved/.
import os
import numpy as np
import scipy.signal as sg
import scipy.ndimage as nd
import soundfile as sf

SR = 44100
import os as _os
# The sound sources: a folder next to the repository (anomaly_sfx/approved, …/processed), or ZONE_SFX.
SFX = _os.environ.get('ZONE_SFX', _os.path.join(_os.path.dirname(_os.path.abspath(__file__)), '..', '..', '..', 'anomaly_sfx'))
APP = SFX + "/approved"
OUT = SFX + "/processed"


def load(path):
    x, sr = sf.read(path, always_2d=True)
    x = x.mean(axis=1)
    if sr != SR:
        g = np.gcd(SR, sr)
        x = sg.resample_poly(x, SR // g, sr // g)
    return x


def write_ogg(path, x):
    # libsndfile's Vorbis encoder overflows the stack on a long buffer written at once: in blocks.
    with sf.SoundFile(path, 'w', SR, 1, format='OGG', subtype='VORBIS') as f:
        x = x.astype(np.float32)
        for i in range(0, len(x), 8192):
            f.write(x[i:i + 8192])


def save(anomaly, name, x, peak=None, rms_db=None):
    os.makedirs(f"{OUT}/{anomaly}", exist_ok=True)
    if rms_db is not None:
        r = np.sqrt(np.mean(x ** 2)) + 1e-12
        x = x * (10 ** (rms_db / 20) / r)
    if peak is not None:
        x = x / (np.max(np.abs(x)) + 1e-12) * peak
    m = np.max(np.abs(x))
    if m > 0.98:
        x = x / m * 0.98
    write_ogg(f"{OUT}/{anomaly}/{name}.ogg", x)
    print(f"{anomaly}/{name}.ogg  {len(x) / SR:.2f}s  rms {20 * np.log10(np.sqrt(np.mean(x ** 2)) + 1e-12):.1f} dB")


def hp(x, f, o=4):
    return sg.sosfiltfilt(sg.butter(o, f / (SR / 2), 'high', output='sos'), x)


def lp(x, f, o=4):
    return sg.sosfiltfilt(sg.butter(o, f / (SR / 2), 'low', output='sos'), x)


def fade(x, fi=0.01, fo=0.05):
    x = x.copy()
    a, b = int(SR * fi), int(SR * fo)
    if a:
        x[:a] *= np.linspace(0, 1, a) ** 2
    if b:
        x[-b:] *= np.linspace(1, 0, b) ** 2
    return x


NPER = 2048


def stft(x):
    return sg.stft(x, SR, nperseg=NPER, noverlap=NPER * 3 // 4)


def istft(Z, n):
    _, y = sg.istft(Z, SR, nperseg=NPER, noverlap=NPER * 3 // 4)
    return y[:n]


def gate(x, noise, strength=1.5, floor=0.06, smooth=(3, 5)):
    """Spectral gating: subtract a noise profile (magnitude per frequency) taken from `noise`."""
    _, _, N = stft(noise)
    prof = np.median(np.abs(N), axis=1, keepdims=True)
    _, _, Z = stft(x)
    mag = np.abs(Z)
    gain = np.clip(1.0 - strength * prof / (mag + 1e-12), 0.0, 1.0)
    gain = nd.uniform_filter(gain, size=smooth)
    gain = np.maximum(gain, floor)
    return istft(Z * gain, len(x))


def hpss(x, kh=31, kp=31):
    """Harmonic (steady tones: ringing glass) / percussive (clicks, steps) split, soft masks."""
    _, _, Z = stft(x)
    mag = np.abs(Z)
    H = nd.median_filter(mag, size=(1, kh))
    P = nd.median_filter(mag, size=(kp, 1))
    mh = H ** 2 / (H ** 2 + P ** 2 + 1e-12)
    return istft(Z * mh, len(x)), istft(Z * (1 - mh), len(x))


def loopify(x, xf=1.5):
    n = int(SR * xf)
    a, b = x[:n], x[-n:]
    t = np.linspace(0, np.pi / 2, n)
    body = x[n:-n] if len(x) > 2 * n else x[n:]
    return np.concatenate([b * np.cos(t) + a * np.sin(t), body])


def seg(x, t0, t1):
    return x[int(t0 * SR):int(t1 * SR)]


def env_db(x, hop=0.01):
    h = int(SR * hop)
    e = np.array([np.sqrt(np.mean(x[i:i + h] ** 2)) for i in range(0, len(x) - h, h)])
    return 20 * np.log10(e + 1e-12)


# ---------------------------------------------------------------- Khlopushka
# Every ~5 s: a hissing build-up (~0.9 s) and the bang. Noise: steady tones (4-5 kHz lines), rumble.
raw = hp(load(APP + "/khlopushka/hlopushka_blow_samples.ogg"), 60)
d = env_db(raw)
quiet = np.concatenate([seg(raw, t, t + 1.0) for t in (1.6, 5.9, 10.9, 15.9, 21.0, 30.9)])
clean = gate(raw, quiet, strength=1.6)
# Bangs: the loudest point of each event (sharp rise of the high band), at least 3.5 s apart.
hi = env_db(hp(clean, 2500), 0.01)
peaks, _ = sg.find_peaks(hi, distance=350, prominence=15)
peaks = [p for p in peaks if 1.2 < p * 0.01 < len(raw) / SR - 1.6]
bangs, charges = [], []
for p in peaks:
    t = p * 0.01
    # the bang proper: the steepest rise in the 0.4 s before the high-band peak
    w = hi[max(0, p - 40):p + 1]
    rise = np.argmax(np.diff(w)) if len(w) > 1 else 0
    tb = (max(0, p - 40) + rise) * 0.01
    bangs.append(fade(seg(clean, tb - 0.01, tb + 1.3), 0.004, 0.5))
    charges.append(fade(seg(clean, tb - 1.05, tb - 0.02), 0.25, 0.03))
# Keep the cleanest: least energy left in a quiet stretch after each bang.
order = np.argsort([np.sqrt(np.mean(b[int(0.9 * SR):] ** 2)) for b in bangs])[:4]


def untone(x, above=1500, prom=12):
    # A hum line left over in the tail: notch every narrow peak standing out of the tail's spectrum.
    tail = x[int(0.7 * SR):]
    f, P = sg.welch(tail, SR, nperseg=8192)
    db = 10 * np.log10(P + 1e-20)
    base = nd.median_filter(db, size=61)
    for k in np.where((db - base > prom) & (f > above))[0]:
        b, a = sg.iirnotch(f[k], 25, SR)
        x = sg.filtfilt(b, a, x)
    return x


for i, k in enumerate(order, 1):
    save('khlopushka', f'khlopushka_bang{i}', untone(bangs[k]), peak=0.95)
    save('khlopushka', f'khlopushka_charge{i}', charges[k], rms_db=-24)

# ---------------------------------------------------------------- Kamerton
# Idle: faint crackle + now and then a soft glass touch. Hit: brighter ringing, louder touches,
# scratches; recorded over footsteps — drop them (low band, and the percussive part below 3 kHz).
idle_raw = hp(load(APP + "/kamerton/glass_shards_idle_samples.ogg"), 1000, 6)
idle_q = seg(idle_raw, 30.0, 34.0)
idle = gate(idle_raw, idle_q, strength=0.8, floor=0.25)
idle_loop = loopify(seg(idle, 22.0, 42.0), 1.5)
save('kamerton', 'kamerton_idle', fade(idle_loop, 0, 0), rms_db=-26)

hit_raw = hp(load(APP + "/kamerton/glass_shards_hit+idle+footsteps.ogg"), 450)
harm, perc = hpss(hit_raw)
scratch = hp(perc, 3500)
glass = harm + 0.35 * scratch
glass = gate(glass, seg(glass, 44.0, 48.0), strength=1.2, floor=0.05)
g = env_db(hp(harm, 800), 0.01)
peaks, _ = sg.find_peaks(g, distance=150, prominence=8)
cands = sorted(peaks, key=lambda p: -g[p])[:10]
hits = []
for p in sorted(cands):
    t = p * 0.01
    hits.append(fade(hp(seg(glass, max(0, t - 0.15), t + 1.1), 600), 0.02, 0.4))
for i, h in enumerate(hits[:6], 1):
    save('kamerton', f'kamerton_hit{i}', h, rms_db=-22)

# ---------------------------------------------------------------- Dymka (inside)
# Low wind-like rumble; night insects (3-6 kHz pulse trains) and a bird (~1.8 kHz) cut off above 1.5 kHz.
dy = load(APP + "/dymka/dymka_inside_samples.ogg")
dy = lp(hp(dy, 35), 1500, 6)
dy = seg(dy, 33.0, 64.0)
# Above 400 Hz keep only the noise (wind); the steady tones there are birds and other distant life.
_, noisy = hpss(dy, kh=41, kp=17)
dy = lp(dy, 400, 6) + hp(noisy, 400, 6)
dy = loopify(dy, 2.0)
save('dymka', 'dymka_inside', dy, rms_db=-24)

# ---------------------------------------------------------------- Gravi pops (soft)
for i, f in enumerate(["gravi_pop1.ogg", "gravy_blast.ogg", "gravy_blast2.ogg", "gravy_blast3.ogg"], 1):
    x = load(APP + "/gravi/" + f)
    save('gravi', f'gravi_pop{i}', fade(x, 0.002, 0.2), rms_db=-24)
x = load(APP + "/gravi/gravy_idle.ogg")
save('gravi', 'gravi_idle', loopify(x, 1.0), rms_db=-26)

# ---------------------------------------------------------------- Soap bubbles (mp3)
old = [load(APP + f"/soap_bubbles/soap_bubbles_pop{i}.ogg") for i in (1, 2)]
ref = float(np.mean([np.sqrt(np.mean(o ** 2)) for o in old]))
for i, f in enumerate(["bubble-pop1.mp3", "bubble-pop2.mp3", "bubble-pop3.mp3"], 3):
    x = load(APP + "/soap_bubbles/" + f)
    d = env_db(x, 0.005)
    on = max(0, np.argmax(d > d.max() - 40) - 2) * 0.005
    x = x[int(on * SR):]
    d = env_db(x, 0.005)
    off = (len(d) - np.argmax(d[::-1] > d.max() - 50)) * 0.005 + 0.05
    x = fade(x[:int(off * SR)], 0.002, 0.05)
    save('soap_bubbles', f'soap_bubbles_pop{i}', x, rms_db=20 * np.log10(ref))

# ---------------------------------------------------------------- Voronka blowout: cut the silence
x = load(APP + "/voronka/voronka_blowout.ogg")
d = env_db(x, 0.01)
end = np.argmax(d[300:] < -100) + 300 if np.any(d[300:] < -100) else len(d)
x = x[:int(end * 0.01 * SR)]
x[-int(0.4 * SR):] *= np.linspace(1, 0, int(0.4 * SR)) ** 2
save('voronka', 'voronka_blowout', x)
