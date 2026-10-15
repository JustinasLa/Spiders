"""Synthesises the mimic's sounds into ../assets/tfmc_spiders/sounds/mimic/ and writes sounds.json.

Everything is generated from oscillators and noise, so the pack ships no third-party audio.
Needs numpy, scipy and an ffmpeg with libvorbis on PATH. Run: python mimic_sounds.py [--seed N]
"""
import argparse
import json
import os
import subprocess
import tempfile
import wave

import numpy as np
from scipy import signal

RATE = 44100
HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "assets", "tfmc_spiders")
OUT = os.path.join(ASSETS, "sounds", "mimic")

rng = np.random.default_rng(66)


# ---------------------------------------------------------------- building blocks

def t_axis(seconds):
    return np.arange(int(seconds * RATE)) / RATE


def envelope(seconds, points):
    """Piecewise-linear envelope through (time, level) points."""
    times, levels = zip(*points)
    return np.interp(t_axis(seconds), times, levels)


def smooth_noise(seconds, rate_hz, depth=1.0):
    """Band-limited random wobble, roughly rate_hz changes per second, in [-depth, depth]."""
    n = int(seconds * RATE)
    knots = max(2, int(seconds * rate_hz) + 2)
    values = rng.uniform(-1, 1, knots)
    x = np.linspace(0, knots - 1, n)
    return np.interp(x, np.arange(knots), values) * depth


def phase(freq):
    return np.cumsum(freq) / RATE


def saw(freq):
    p = phase(freq)
    return 2 * (p - np.floor(p)) - 1


def square(freq, duty=0.5):
    p = phase(freq)
    return np.where(p - np.floor(p) < duty, 1.0, -1.0)


def sine(freq):
    return np.sin(2 * np.pi * phase(freq))


def noise(seconds):
    return rng.uniform(-1, 1, int(seconds * RATE))


def bandpass(x, low, high, order=2):
    sos = signal.butter(order, [low, min(high, RATE / 2 - 100)], btype="band", fs=RATE, output="sos")
    return signal.sosfilt(sos, x)


def lowpass(x, cutoff, order=2):
    sos = signal.butter(order, cutoff, btype="low", fs=RATE, output="sos")
    return signal.sosfilt(sos, x)


def highpass(x, cutoff, order=2):
    sos = signal.butter(order, cutoff, btype="high", fs=RATE, output="sos")
    return signal.sosfilt(sos, x)


def resonator(x, freq, q):
    b, a = signal.iirpeak(freq, q, fs=RATE)
    return signal.lfilter(b, a, x)


def formants(x, bands):
    """Parallel vocal-tract resonances: (frequency, q, gain) per band."""
    return sum(resonator(x, f, q) * g for f, q, g in bands)


def moving_formants(x, start, end, curve=None):
    """Formants that glide from one vowel to another, rendered in short crossfaded blocks."""
    n = len(x)
    block = 1024
    hop = block // 2
    window = np.hanning(block)
    out = np.zeros(n + block)
    padded = np.concatenate([x, np.zeros(block)])
    for i in range(0, n, hop):
        k = i / max(1, n - 1)
        if curve is not None:
            k = float(np.interp(k, np.linspace(0, 1, len(curve)), curve))
        bands = [(fa + (fb - fa) * k, qa + (qb - qa) * k, ga + (gb - ga) * k)
                 for (fa, qa, ga), (fb, qb, gb) in zip(start, end)]
        lead = max(0, i - 2048)
        seg = formants(padded[lead:i + block], bands)[i - lead:]
        out[i:i + block] += seg[:block] * window
    return out[:n]


def distort(x, drive):
    return np.tanh(x * drive) / np.tanh(drive)


def fry(seconds, rate_hz, jitter=0.25, depth=0.85):
    """Vocal-fry rattle: irregular glottal pulses that turn a tone into a croak."""
    rate = rate_hz * (1 + smooth_noise(seconds, 9, jitter))
    p = phase(rate)
    pulse = np.exp(-((p - np.floor(p)) * 7.0))
    return 1 - depth + depth * pulse


def reverb(x, seconds=0.9, mix=0.3, damp=4500):
    """A dark room: convolution with decaying filtered noise."""
    tail = noise(seconds) * np.exp(-t_axis(seconds) * (6.0 / seconds))
    tail = lowpass(tail, damp)
    tail[: int(0.012 * RATE)] *= np.linspace(0, 1, int(0.012 * RATE))
    wet = signal.fftconvolve(x, tail)
    dry = np.concatenate([x, np.zeros(len(wet) - len(x))])
    wet = wet / (np.max(np.abs(wet)) + 1e-9) * np.max(np.abs(x))
    return dry * (1 - mix) + wet * mix


def fade(x, seconds_in=0.003, seconds_out=0.03):
    n_in, n_out = int(seconds_in * RATE), int(seconds_out * RATE)
    y = x.copy()
    if n_in: y[:n_in] *= np.linspace(0, 1, n_in)
    if n_out: y[-n_out:] *= np.linspace(1, 0, n_out)
    return y


def normalize(x, peak=0.97):
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def mix(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts: out[: len(p)] += p
    return out


def at(x, seconds, total):
    out = np.zeros(int(total * RATE))
    start = int(seconds * RATE)
    end = min(len(out), start + len(x))
    out[start:end] = x[: end - start]
    return out


def knock(freqs, decays, seconds=0.25, gains=None):
    """Struck wood: damped partials plus a click."""
    t = t_axis(seconds)
    gains = gains or [1.0] * len(freqs)
    body = sum(g * np.sin(2 * np.pi * f * t * (1 + 0.02 * rng.uniform(-1, 1))) * np.exp(-t / d)
               for f, d, g in zip(freqs, decays, gains))
    click = highpass(noise(seconds), 2500) * np.exp(-t / 0.004)
    return body + click * 0.6


# ---------------------------------------------------------------- the sounds

AA = [(800, 6, 1.0), (1250, 7, 0.8), (2650, 9, 0.55), (3600, 10, 0.4)]
EE = [(320, 6, 0.8), (2400, 8, 1.0), (3100, 10, 0.7), (4200, 10, 0.5)]
OO = [(420, 6, 1.0), (850, 7, 0.7), (2500, 10, 0.25), (3400, 10, 0.15)]


def screech(seconds=2.4):
    """The wake scream: a choir of torn voices, a tritone apart, cracking upward and collapsing."""
    t = t_axis(seconds)
    low, high = rng.uniform(300, 380), rng.uniform(900, 1120)
    crack_at, crack_ratio = rng.uniform(0.45, 0.7), rng.uniform(1.35, 1.5)
    rise = np.clip(t / 0.12, 0, 1)
    base = low + (high - low) * (1 - (1 - rise) ** 3)
    # it breaks into a higher register mid-scream, then sags and gurgles out
    crack = 1.0 + (crack_ratio - 1) * np.clip((t - crack_at) / 0.04, 0, 1) * np.clip((crack_at + 0.7 - t) / 0.05, 0, 1)
    droop = 1 - 0.6 * (np.clip(t - 1.5, 0, None) / (seconds - 1.5)) ** 1.5
    pitch = base * crack * droop * (1 + smooth_noise(seconds, 14, 0.035)) * (1 + 0.025 * np.sin(2 * np.pi * 6.5 * t))

    voices = sum(saw(pitch * d) for d in (1.0, 1.011, 0.989, 1.023)) / 4
    tritone = saw(pitch * 1.4142 * (1 + smooth_noise(seconds, 6, 0.008))) * 0.5
    minor_second = saw(pitch * 1.0595) * 0.3
    sub = square(pitch * 0.5) * 0.3
    tone = (voices + tritone + minor_second + sub) * fry(seconds, 46, 0.35, 0.45)

    sung = moving_formants(tone, EE, AA, curve=[0, 1, 1, 0.7, 0.2])
    rasp = bandpass(noise(seconds), 2500, 7000) * (0.5 + 0.5 * fry(seconds, 33, 0.4, 0.9))
    roar = lowpass(saw(pitch * 0.25) * fry(seconds, 28, 0.5, 0.9), 900)
    gurgle = lowpass(noise(seconds), 600) * fry(seconds, 18, 0.6, 1.0) * envelope(seconds, [(0, 0), (1.6, 0), (2.0, 1), (seconds, 0.6)])

    env = envelope(seconds, [(0, 0), (0.02, 1), (1.4, 0.9), (1.85, 0.5), (seconds - 0.15, 0.12), (seconds, 0)])
    x = (normalize(sung) + rasp * 0.12 + normalize(roar) * 0.25 + normalize(gurgle) * 0.3) * env
    x = distort(normalize(x), 2.0)
    return fade(normalize(reverb(x, 1.1, 0.22)), 0.003, 0.3)


def shriek(seconds=0.75):
    """A short, piercing shriek for bites and lunges."""
    t = t_axis(seconds)
    pitch = (1500 + 1300 * np.clip(t / 0.08, 0, 1) - 900 * np.clip((t - 0.35) / 0.4, 0, 1)) * (1 + smooth_noise(seconds, 25, 0.07))
    tone = sum(saw(pitch * d) for d in (1.0, 1.02, 0.979, 1.4142)) * fry(seconds, 55, 0.4, 0.6)
    sung = formants(tone, [(2900, 6, 1.0), (4100, 8, 0.8), (1300, 6, 0.5)])
    rasp = bandpass(noise(seconds), 3000, 9000) * 0.45
    env = envelope(seconds, [(0, 0), (0.008, 1), (0.3, 0.85), (seconds, 0)])
    x = distort(normalize(normalize(sung) + rasp) * env, 4.0)
    return fade(normalize(reverb(x, 0.7, 0.25)))


def stinger(seconds=2.0):
    """Screaming strings: stabbed clusters a semitone apart, the classic horror-film shock chord."""
    t = t_axis(seconds)
    notes = [2489, 2637, 2794, 2960, 1865]
    vib = 1 + 0.012 * np.sin(2 * np.pi * 7.3 * t + rng.uniform(0, 6))
    strings = sum(saw(n * vib * (1 + smooth_noise(seconds, 4, 0.004))) for n in notes) / len(notes)
    strings = bandpass(strings, 1400, 9000, 3) + highpass(noise(seconds), 6000) * 0.08
    stabs = np.zeros_like(t)
    for start in (0.0, 0.2, 0.4, 0.6):
        stabs = np.maximum(stabs, envelope(seconds, [(0, 0), (start, 0), (start + 0.006, 1), (start + 0.15, 0.6), (start + 0.19, 0), (seconds, 0)]))
    stabs = np.maximum(stabs, envelope(seconds, [(0, 0), (0.8, 0), (0.81, 1), (1.3, 0.8), (seconds, 0)]))
    boom_t = t_axis(1.2)
    boom = np.sin(2 * np.pi * (48 - 18 * boom_t) * boom_t) * np.exp(-boom_t / 0.35) + lowpass(noise(1.2), 300) * np.exp(-boom_t / 0.12) * 0.6
    x = mix(normalize(strings) * stabs, normalize(boom) * 0.9)
    return fade(normalize(reverb(distort(x, 1.6), 1.4, 0.32)))


def chomp(seconds=0.42):
    """The lid slamming on something: a hard wooden clap, a bone crunch and a wet squelch."""
    clap = knock([170, 410, 930, 1900], [0.05, 0.035, 0.02, 0.01], seconds, [1.0, 0.8, 0.5, 0.3])
    crunch = np.zeros(int(seconds * RATE))
    for _ in range(int(rng.integers(7, 12))):
        start = rng.uniform(0.015, 0.16)
        tick = highpass(noise(0.012), 900) * np.exp(-t_axis(0.012) / 0.003) * rng.uniform(0.4, 1.0)
        crunch += at(tick, start, seconds)
    crunch = bandpass(crunch, 900, 6000)
    wet_t = t_axis(0.25)
    squelch = bandpass(noise(0.25), 180, 900) * np.exp(-wet_t / 0.07) * (0.6 + 0.4 * np.sin(2 * np.pi * 23 * wet_t))
    x = mix(normalize(clap), normalize(crunch) * 0.7, at(normalize(squelch) * 0.5, 0.03, seconds))
    return fade(normalize(reverb(distort(x, 2.2), 0.45, 0.18)))


def crack(seconds=0.32):
    """A joint snapping into place: a sharp split and splintering."""
    t = t_axis(seconds)
    snap = highpass(noise(seconds), 1200) * np.exp(-t / 0.006)
    splinter = np.zeros_like(t)
    for _ in range(int(rng.integers(10, 18))):
        start = rng.uniform(0.004, 0.12)
        splinter += at(highpass(noise(0.006), 2000) * rng.uniform(0.2, 0.8), start, seconds)
    body = knock([95, 260, 640], [0.06, 0.03, 0.015], seconds)
    x = mix(normalize(snap), splinter * 0.6, normalize(body) * 0.8)
    return fade(normalize(reverb(distort(x, 2.5), 0.4, 0.15)))


def breath(seconds=3.2):
    """Slow, wet breathing from inside the wood: a long inhale, a rattling exhale."""
    t = t_axis(seconds)
    inhale = bandpass(noise(seconds), 300, 2600) * envelope(seconds, [(0, 0), (0.9, 0.55), (1.15, 0), (seconds, 0)])
    rattle = fry(seconds, 26, 0.5, 0.95)
    growl = lowpass(saw(62 * (1 + smooth_noise(seconds, 3, 0.05))), 500) * rattle
    exhale = (formants(noise(seconds), OO) * 0.04 + growl * 0.5) * envelope(seconds, [(0, 0), (1.35, 0), (1.5, 1), (2.6, 0.6), (seconds, 0)])
    clicks = np.zeros_like(t)
    for _ in range(14):
        clicks += at(bandpass(noise(0.01), 600, 3000) * np.exp(-t_axis(0.01) / 0.002), rng.uniform(1.3, seconds - 0.1), seconds)
    x = normalize(inhale) * 0.5 + normalize(exhale) + clicks * 0.4
    return fade(normalize(reverb(lowpass(x, 5000), 0.6, 0.2)), 0.05, 0.2)


def growl(seconds=2.4):
    """A low, rattling growl for when it stops and stares."""
    t = t_axis(seconds)
    pitch = 70 * (1 + smooth_noise(seconds, 4, 0.08)) * (1 - 0.15 * t / seconds)
    tone = (saw(pitch) + saw(pitch * 1.01) + square(pitch * 0.5) * 0.6) * fry(seconds, 24, 0.5, 0.95)
    sung = formants(tone, OO)
    rumble = lowpass(noise(seconds), 180) * 1.5
    env = envelope(seconds, [(0, 0), (0.35, 1), (1.8, 0.85), (seconds, 0)])
    x = distort(normalize(normalize(sung) + rumble * 0.4) * env, 2.5)
    return fade(normalize(reverb(x, 0.8, 0.22)), 0.05, 0.25)


def heartbeat(seconds=0.75):
    """One lub-dub, felt more than heard."""
    def thump(length, f0, f1, decay):
        tt = t_axis(length)
        return np.sin(2 * np.pi * np.cumsum(np.interp(tt, [0, length], [f0, f1])) / RATE) * np.exp(-tt / decay) * (1 - np.exp(-tt / 0.004))
    x = mix(at(thump(0.35, 62, 38, 0.07), 0.0, seconds), at(thump(0.35, 55, 34, 0.08) * 0.75, 0.21, seconds))
    return fade(normalize(lowpass(distort(x, 1.5), 260)), 0.001, 0.05)


def creak(seconds=1.4):
    """Old wood straining: a stick-slip ratchet through the planks' resonances."""
    t = t_axis(seconds)
    rate = np.interp(t, [0, 0.3, 0.8, seconds], [18, 55, 85, 30]) * (1 + smooth_noise(seconds, 12, 0.3))
    p = phase(rate)
    pulses = np.diff(np.floor(p), prepend=0) * rng.uniform(0.5, 1.0, len(t))
    wood = formants(pulses, [(520, 18, 1.0), (1150, 22, 0.8), (2300, 26, 0.5), (3400, 30, 0.25)])
    env = envelope(seconds, [(0, 0), (0.1, 1), (seconds - 0.2, 0.8), (seconds, 0)])
    return fade(normalize(reverb(normalize(wood) * env, 0.5, 0.18)))


def skitter(seconds=0.22):
    """Wooden legs drumming the floor at a gallop."""
    x = np.zeros(int(seconds * RATE))
    for i in range(int(rng.integers(3, 5))):
        k = knock([260 * rng.uniform(0.85, 1.2), 720 * rng.uniform(0.9, 1.1), 1500], [0.03, 0.015, 0.008], 0.1)
        x += at(k * rng.uniform(0.5, 1.0), i * rng.uniform(0.03, 0.05), seconds)
    return fade(normalize(x))


def gulp(seconds=0.9):
    """Swallowing: a wet drop in pitch down a long throat."""
    t = t_axis(seconds)
    pitch = np.interp(t, [0, 0.25, 0.6, seconds], [260, 120, 70, 60])
    tone = sine(pitch) * envelope(seconds, [(0, 0), (0.03, 1), (0.5, 0.6), (seconds, 0)])
    wet = bandpass(noise(seconds), 150, 1200) * envelope(seconds, [(0, 0), (0.05, 0.8), (0.3, 0.1), (0.55, 0.6), (0.7, 0), (seconds, 0)])
    x = normalize(tone) + normalize(wet) * 0.5
    return fade(normalize(reverb(distort(x, 1.8), 0.6, 0.2)))


# ---------------------------------------------------------------- output

SOUNDS = {
    # event: (generator, variants, attenuation distance in blocks, subtitle)
    "mimic.screech": (screech, 3, 64, "Mimic screeches"),
    "mimic.shriek": (shriek, 3, 48, "Mimic shrieks"),
    "mimic.stinger": (stinger, 1, 16, "Something is wrong"),
    "mimic.chomp": (chomp, 3, 24, "Mimic bites"),
    "mimic.crack": (crack, 3, 16, "Wood snaps"),
    "mimic.breath": (breath, 2, 8, "Something breathes"),
    "mimic.growl": (growl, 2, 24, "Mimic growls"),
    "mimic.heartbeat": (heartbeat, 1, 4, "Heart pounds"),
    "mimic.creak": (creak, 2, 12, "Chest creaks"),
    "mimic.skitter": (skitter, 4, 20, "Wooden legs skitter"),
    "mimic.gulp": (gulp, 2, 12, "Mimic swallows"),
}


def write_ogg(path, samples):
    pcm = (np.clip(samples, -1, 1) * 32767).astype(np.int16)
    with tempfile.TemporaryDirectory() as tmp:
        wav_path = os.path.join(tmp, "x.wav")
        with wave.open(wav_path, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", wav_path, "-ac", "1", "-c:a", "libvorbis", "-q:a", "5", path], check=True)


def main():
    global rng
    parser = argparse.ArgumentParser()
    parser.add_argument("--seed", type=int, default=66)
    parser.add_argument("--wav", help="also write WAV previews into this directory")
    args = parser.parse_args()
    rng = np.random.default_rng(args.seed)
    os.makedirs(OUT, exist_ok=True)

    sounds_json = {}
    for event, (generate, variants, distance, subtitle) in SOUNDS.items():
        name = event.split(".", 1)[1]
        entries = []
        for i in range(1, variants + 1):
            samples = generate()
            file = f"{name}{i}" if variants > 1 else name
            write_ogg(os.path.join(OUT, file + ".ogg"), samples)
            if args.wav:
                os.makedirs(args.wav, exist_ok=True)
                with wave.open(os.path.join(args.wav, file + ".wav"), "wb") as w:
                    w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
                    w.writeframes((np.clip(samples, -1, 1) * 32767).astype(np.int16).tobytes())
            entries.append({"name": f"tfmc_spiders:mimic/{file}", "attenuation_distance": distance})
        sounds_json[event] = {"subtitle": f"subtitles.tfmc_spiders.{event}", "sounds": entries}

    with open(os.path.join(ASSETS, "sounds.json"), "w", newline="\n") as f:
        json.dump(sounds_json, f, indent=2)
        f.write("\n")

    lang = {f"subtitles.tfmc_spiders.{event}": subtitle for event, (_, _, _, subtitle) in SOUNDS.items()}
    os.makedirs(os.path.join(ASSETS, "lang"), exist_ok=True)
    with open(os.path.join(ASSETS, "lang", "en_us.json"), "w", newline="\n") as f:
        json.dump(lang, f, indent=2)
        f.write("\n")
    print(f"wrote {sum(v[1] for v in SOUNDS.values())} sounds to {os.path.normpath(OUT)}")


if __name__ == "__main__":
    main()
