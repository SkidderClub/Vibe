"""Generate original, deterministic Battlefront 3 sound effects (numpy + soundfile).

Run with Python 3 after installing numpy and soundfile. No sampled third-party audio.
"""
from pathlib import Path
import numpy as np
import soundfile as sf

root = Path(__file__).resolve().parents[1] / "src/main/resources/assets/vibe/sounds/battlefront"
root.mkdir(parents=True, exist_ok=True)
rate = 22050
rng = np.random.default_rng(3077)


def save(name, samples):
    samples = np.tanh(samples) * 0.7
    samples[:100] *= np.linspace(0, 1, 100)
    samples[-150:] *= np.linspace(1, 0, 150)
    sf.write(root / (name + ".ogg"), samples, rate, format="OGG", subtype="VORBIS")


t = np.arange(int(rate * .24)) / rate
phase = 2 * np.pi * (1350 * .047 * (1 - np.exp(-t / .047)) + 150 * t)
save("fire", (np.sin(phase) + .3 * np.sin(phase * 1.97) + .12 * rng.normal(size=len(t))) * np.exp(-t * 18))
t = np.arange(int(rate * .52)) / rate
noise = rng.normal(size=len(t))
noise = np.convolve(noise, np.ones(9) / 9, mode="same")
save("blast", (noise * 2.1 + .5 * np.sin(2 * np.pi * (95 * t - 65 * t * t))) * np.exp(-t * 8))
t = np.arange(int(rate * .28)) / rate
save("confirm", .6 * np.sin(2 * np.pi * np.where(t < .12, 660 * t, 660 * .12 + 990 * (t - .12))) * np.exp(-t * 6))
print("Generated 3 original Ogg/Vorbis sound effects in", root)
