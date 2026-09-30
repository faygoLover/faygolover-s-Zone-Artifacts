# 0.1.34.1: web snap — dull, fibrous tearing instead of the ringing twang (no tone at all).
# Writes .ogg through soundfile (libsndfile's Vorbis), no ffmpeg needed.
import os
import numpy as np
import scipy.signal as sg
import soundfile as sf

SR = 44100
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src', 'main', 'resources', 'assets', 'fl_zone_arts', 'sounds')
rng = np.random.default_rng(135)


def lp(x, f, o=4):
    b, a = sg.butter(o, f / (SR / 2), 'low')
    return sg.lfilter(b, a, x)


def bp(x, f1, f2, o=2):
    b, a = sg.butter(o, [f1 / (SR / 2), f2 / (SR / 2)], 'band')
    return sg.lfilter(b, a, x)


def save(name, x, peak=0.7):
    x = x / (np.max(np.abs(x)) + 1e-9) * peak
    sf.write(os.path.join(OUT, name + '.ogg'), x.astype(np.float32), SR, format='OGG', subtype='VORBIS')


for v in range(3):
    d = 0.45
    n = int(SR * d)
    x = np.zeros(n)
    # Strands giving way one after another: short soft crackles, low and muffled.
    k = 9 + v * 3
    for i in range(k):
        st = int((rng.random() ** 1.6) * SR * 0.22)
        L = int(SR * (0.006 + rng.random() * 0.02))
        burst = rng.standard_normal(L) * np.exp(-np.linspace(0, 6, L))
        x[st:st + L] += bp(burst, 250 + rng.random() * 200, 1200 + rng.random() * 600) * (0.4 + rng.random() * 0.6)
    # A soft pull under it (the sticky threads stretching), no pitch.
    pull = lp(rng.standard_normal(n), 500) * np.exp(-np.linspace(0, 9, n)) * 0.35
    x += pull
    x = lp(x, 2200)
    fade = np.ones(n)
    fade[-int(SR * 0.05):] = np.linspace(1, 0, int(SR * 0.05))
    save(f'web_snap{v + 1}', x * fade, 0.55)
print('done')
