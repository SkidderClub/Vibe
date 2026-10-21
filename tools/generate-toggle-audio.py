"""Original Vibe UI tones. Requires numpy and soundfile; no sampled third-party audio."""
from pathlib import Path
import numpy as np
import soundfile as sf

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/vibe/sounds/toggle"
RATE = 44100
ROOT.mkdir(parents=True, exist_ok=True)


def save(name, samples):
    samples *= np.minimum(1, np.arange(len(samples)) / (RATE * .004))
    samples *= np.minimum(1, np.arange(len(samples))[::-1] / (RATE * .018))
    peak = max(.001, np.max(np.abs(samples)))
    samples *= .55 / peak
    sf.write(ROOT / (name + ".ogg"), samples, RATE, format="OGG", subtype="VORBIS")


for enabled in (True, False):
    suffix = "on" if enabled else "off"
    t = np.arange(int(RATE * .17)) / RATE
    frequency = 620 if enabled else 415
    phase = 2 * np.pi * (frequency * t + (110 if enabled else -55) * t * t)
    save("soft_" + suffix, (np.sin(phase) + .12 * np.sin(phase * 2)) * (1 - np.exp(-t * 650)) * np.exp(-t * 32))

    t = np.arange(int(RATE * .12)) / RATE
    base = 540 if enabled else 350
    phase = 2 * np.pi * (base * .014 * (1 - np.exp(-t / .014)) + 130 * t)
    save("pop_" + suffix, np.sin(phase) * np.exp(-t * 43))

    t = np.arange(int(RATE * .29)) / RATE
    samples = np.zeros_like(t)
    notes = (659.25, 880) if enabled else (659.25, 493.88)
    for delay, frequency in zip((0, .07), notes):
        u = np.maximum(0, t - delay)
        envelope = (t >= delay) * (1 - np.exp(-u * 500)) * np.exp(-u * 22)
        samples += (np.sin(2 * np.pi * frequency * u) + .1 * np.sin(2 * np.pi * frequency * 2 * u)) * envelope
    save("chime_" + suffix, samples)

for path in sorted(ROOT.glob("*.ogg")):
    samples, rate = sf.read(path)
    assert rate == RATE and np.max(np.abs(samples)) < .65 and .1 <= len(samples) / rate <= .3
    print(path.name, "%.2fs" % (len(samples) / rate), "peak %.3f" % np.max(np.abs(samples)))
