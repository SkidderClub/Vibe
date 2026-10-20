package dev.vibe.game.gta8;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Random;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;

/**
 * Procedurally synthesised game audio played directly through OpenAL. Sources are listener-relative
 * and attenuated here, so Minecraft's own sound system and listener are left untouched.
 */
public final class Gta8Audio {
    private static final int RATE = 22050;
    private enum S {
        ENGINE_LOW, ENGINE_HIGH, SKID, SIREN, RAIN, WIND, CITY, HELI, PISTOL, SMG, RIFLE, SHOTGUN, SNIPER, DISTANT, RICOCHET, METAL, THUD,
        GLASS, CRASH, EXPLOSION, PUNCH, CASH, PICKUP, RELOAD, EMPTY, HORN, DOOR, THUNDER, WASTED, JINGLE, FAIL, RADIO, ALARM, SPLASH, WHIZ
    }
    private final int[] buffers = new int[S.values().length];
    private final int[] pool = new int[24];
    private int engineLow, engineHigh, skid, rain, wind, city, heli;
    private final int[] traffic = new int[2], sirens = new int[2];
    private boolean available, failed, paused;
    public float volume = 1;
    private final Random random = new Random(3);
    private double cx, cy, cz, fx, fy, fz, rx, rz;

    public boolean available() { return available; }

    public void init() {
        if (available || failed) return;
        try {
            if (!AL.isCreated()) return;
            for (S s : S.values()) buffers[s.ordinal()] = buffer(synth(s));
            for (int i = 0; i < pool.length; i++) pool[i] = source(false);
            engineLow = source(true); engineHigh = source(true); skid = source(true);
            rain = source(true); wind = source(true); city = source(true); heli = source(true);
            for (int i = 0; i < 2; i++) { traffic[i] = source(true); sirens[i] = source(true); }
            attach(engineLow, S.ENGINE_LOW); attach(engineHigh, S.ENGINE_HIGH); attach(skid, S.SKID);
            attach(rain, S.RAIN); attach(wind, S.WIND); attach(city, S.CITY); attach(heli, S.HELI);
            for (int i = 0; i < 2; i++) { attach(traffic[i], S.ENGINE_LOW); attach(sirens[i], S.SIREN); }
            available = AL10.alGetError() == AL10.AL_NO_ERROR;
            if (!available) close();
        } catch (Throwable t) {
            failed = true; available = false;
        }
    }

    private int source(boolean loop) {
        int s = AL10.alGenSources();
        AL10.alSourcei(s, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
        AL10.alSourcef(s, AL10.AL_ROLLOFF_FACTOR, 0);
        AL10.alSourcei(s, AL10.AL_LOOPING, loop ? AL10.AL_TRUE : AL10.AL_FALSE);
        AL10.alSourcef(s, AL10.AL_GAIN, 0);
        return s;
    }
    private void attach(int source, S sound) { AL10.alSourcei(source, AL10.AL_BUFFER, buffers[sound.ordinal()]); AL10.alSourcePlay(source); }
    private static int buffer(short[] samples) {
        int b = AL10.alGenBuffers();
        ShortBuffer data = ByteBuffer.allocateDirect(samples.length * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
        data.put(samples).flip();
        AL10.alBufferData(b, AL10.AL_FORMAT_MONO16, data, RATE);
        return b;
    }

    public void close() {
        try {
            if (!AL.isCreated()) return;
            for (int s : pool) if (s != 0) { AL10.alSourceStop(s); AL10.alDeleteSources(s); }
            for (int s : new int[]{engineLow, engineHigh, skid, rain, wind, city, heli, traffic[0], traffic[1], sirens[0], sirens[1]}) if (s != 0) { AL10.alSourceStop(s); AL10.alDeleteSources(s); }
            for (int b : buffers) if (b != 0) AL10.alDeleteBuffers(b);
        } catch (Throwable ignored) { /* The device may already be gone. */ }
        java.util.Arrays.fill(pool, 0); java.util.Arrays.fill(buffers, 0);
        engineLow = engineHigh = skid = rain = wind = city = heli = 0;
        available = false;
    }

    public void pause(boolean value) {
        if (!available || paused == value) return;
        paused = value;
        try {
            for (int s : new int[]{engineLow, engineHigh, skid, rain, wind, city, heli, traffic[0], traffic[1], sirens[0], sirens[1]}) AL10.alSourcef(s, AL10.AL_GAIN, 0);
        } catch (Throwable t) { available = false; }
    }

    // ------------------------------------------------------------------ per-frame update
    public void update(Gta8Game game, double dt) {
        if (!available) { game.events.clear(); return; }
        try {
            if (!AL.isCreated()) { available = false; return; }
            Gta8Camera c = game.camera;
            cx = c.x; cy = c.y; cz = c.z;
            double yaw = Math.toRadians(c.yaw), pitch = Math.toRadians(c.pitch);
            fx = Math.sin(yaw) * Math.cos(pitch); fy = -Math.sin(pitch); fz = -Math.cos(yaw) * Math.cos(pitch);
            rx = Math.cos(yaw); rz = Math.sin(yaw);
            if (paused) { game.events.clear(); return; }
            events(game);
            loops(game, dt);
        } catch (Throwable t) {
            available = false; failed = true;
        }
    }
    private void place(int source, double x, double y, double z) {
        double dx = x - cx, dy = y - cy, dz = z - cz;
        double lx = dx * rx + dz * rz, lz = -(dx * fx + dy * fy + dz * fz);
        double ly = dy - (dx * fx + dy * fy + dz * fz) * fy;
        AL10.alSource3f(source, AL10.AL_POSITION, (float) lx, (float) ly, (float) lz);
    }
    private static double attenuate(double distance, double reference, double max) {
        if (distance >= max) return 0;
        double g = reference / (reference + Math.max(0, distance - reference));
        return g * Gta8Math.smooth(max, max * .7, distance);
    }
    private void play(S sound, double x, double y, double z, double gain, double pitch, double reference, double max) {
        double d = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz));
        double g = gain * attenuate(d, reference, max) * volume;
        if (g < .01) return;
        int best = -1;
        for (int s : pool) if (AL10.alGetSourcei(s, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) { best = s; break; }
        if (best < 0) best = pool[random.nextInt(pool.length)];
        AL10.alSourceStop(best);
        AL10.alSourcei(best, AL10.AL_BUFFER, buffers[sound.ordinal()]);
        place(best, x, y, z);
        AL10.alSourcef(best, AL10.AL_GAIN, (float) Math.min(1, g));
        AL10.alSourcef(best, AL10.AL_PITCH, (float) Gta8Math.clamp(pitch, .5, 2));
        AL10.alSourcePlay(best);
    }
    private void events(Gta8Game game) {
        for (Gta8Game.GameEvent e : game.events) {
            double d = Math.sqrt((e.x - cx) * (e.x - cx) + (e.y - cy) * (e.y - cy) + (e.z - cz) * (e.z - cz));
            double vary = .94 + random.nextDouble() * .12;
            switch (e.type) {
                case GUNSHOT: {
                    S s = e.weapon == Gta8Weapon.SMG ? S.SMG : e.weapon == Gta8Weapon.RIFLE ? S.RIFLE : e.weapon == Gta8Weapon.SHOTGUN ? S.SHOTGUN : e.weapon == Gta8Weapon.SNIPER ? S.SNIPER : S.PISTOL;
                    if (d > 90) play(S.DISTANT, e.x, e.y, e.z, .9, vary, 60, 900);
                    else play(s, e.x, e.y, e.z, e.value, vary, 8, 400);
                    break;
                }
                case IMPACT: play(e.value == 1 ? S.METAL : e.value == 3 ? S.WHIZ : S.RICOCHET, e.x, e.y, e.z, .5, vary, 3, 60); break;
                case HIT: play(S.THUD, e.x, e.y, e.z, .7, vary, 3, 50); break;
                case EXPLOSION: play(S.EXPLOSION, e.x, e.y, e.z, 1, vary * .9, 25, 1200); break;
                case CRASH: play(S.CRASH, e.x, e.y, e.z, Math.min(1, e.value / 14), vary, 6, 180); break;
                case GLASS: case BREAK_WINDOW: play(S.GLASS, e.x, e.y, e.z, .8, vary, 5, 90); break;
                case PUNCH: play(e.value > 0 ? S.PUNCH : S.THUD, e.x, e.y, e.z, .6, vary, 3, 40); break;
                case CASH: case BUY: play(S.CASH, cx, cy, cz, .7, 1, 1, 10); break;
                case PICKUP: play(S.PICKUP, cx, cy, cz, .6, 1, 1, 10); break;
                case RELOAD: play(S.RELOAD, e.x, e.y + 1.3, e.z, .6, vary, 2, 25); break;
                case EMPTY: play(S.EMPTY, e.x, e.y + 1.3, e.z, .6, 1, 2, 20); break;
                case HORN: play(S.HORN, e.x, e.y, e.z, .8, e.value > 1 ? 1 : vary, 8, 160); break;
                case DOOR: case ENTER: case EXIT: play(S.DOOR, e.x, e.y, e.z, .6, vary, 3, 40); break;
                case THUNDER: play(S.THUNDER, cx + fx * 300, cy + 200, cz + fz * 300, .9 * e.value + .3, vary, 400, 5000); break;
                case WASTED: case BUSTED: play(S.WASTED, cx, cy, cz, .9, e.type == Gta8Game.Event.BUSTED ? 1.2 : 1, 1, 10); break;
                case MISSION_PASSED: case EVADED: play(S.JINGLE, cx, cy, cz, .8, 1, 1, 10); break;
                case MISSION_FAILED: play(S.FAIL, cx, cy, cz, .8, 1, 1, 10); break;
                case MISSION_START: case WANTED: play(S.RADIO, cx, cy, cz, .6, 1, 1, 10); break;
                case ALARM: play(S.ALARM, e.x, e.y, e.z, 1, 1, 10, 150); break;
                case SPLASH: play(S.SPLASH, e.x, e.y, e.z, .8, vary, 5, 60); break;
                case BAIL: play(S.THUD, e.x, e.y, e.z, 1, .7, 3, 40); break;
                default: break;
            }
        }
        game.events.clear();
    }

    private void loops(Gta8Game game, double dt) {
        Gta8Vehicle car = game.player.vehicle;
        // Player engine: two loops crossfaded by RPM, louder under load.
        if (car != null && !car.destroyed()) {
            double rpm = car.rpm, load = .55 + .45 * Math.abs(car.throttle);
            double mix = Gta8Math.smooth(1800, 3800, rpm);
            place(engineLow, car.x, car.y + .6, car.z + 0); place(engineHigh, car.x, car.y + .6, car.z);
            AL10.alSourcef(engineLow, AL10.AL_GAIN, (float) ((1 - mix) * .55 * load * volume));
            AL10.alSourcef(engineHigh, AL10.AL_GAIN, (float) (mix * .5 * load * volume));
            AL10.alSourcef(engineLow, AL10.AL_PITCH, (float) Gta8Math.clamp(rpm / 1400, .5, 2));
            AL10.alSourcef(engineHigh, AL10.AL_PITCH, (float) Gta8Math.clamp(rpm / 4200, .5, 2));
            place(skid, car.x, car.y, car.z);
            AL10.alSourcef(skid, AL10.AL_GAIN, (float) (Gta8Math.clamp((car.skid - .25) * 1.4, 0, .75) * volume));
            AL10.alSourcef(skid, AL10.AL_PITCH, (float) (.85 + car.skid * .3));
        } else {
            AL10.alSourcef(engineLow, AL10.AL_GAIN, 0); AL10.alSourcef(engineHigh, AL10.AL_GAIN, 0); AL10.alSourcef(skid, AL10.AL_GAIN, 0);
        }
        // Two nearest other engines and sirens, with Doppler shift.
        Gta8Vehicle[] engines = new Gta8Vehicle[2], sirenCars = new Gta8Vehicle[2];
        double[] ed = {1e9, 1e9}, sd = {1e9, 1e9};
        for (Gta8Vehicle v : game.vehicles) {
            if (v == car || v.destroyed()) continue;
            double d = Math.hypot(v.x - cx, v.z - cz);
            if (Math.hypot(v.vx, v.vz) > .5 || v.ai != null) insert(engines, ed, v, d);
            if (v.siren) insert(sirenCars, sd, v, d);
        }
        for (int i = 0; i < 2; i++) {
            Gta8Vehicle v = engines[i];
            if (v == null) { AL10.alSourcef(traffic[i], AL10.AL_GAIN, 0); continue; }
            place(traffic[i], v.x, v.y + .5, v.z);
            double speed = Math.hypot(v.vx, v.vz);
            AL10.alSourcef(traffic[i], AL10.AL_GAIN, (float) (attenuate(ed[i], 6, 90) * (.18 + speed * .012) * volume));
            AL10.alSourcef(traffic[i], AL10.AL_PITCH, (float) (Gta8Math.clamp(v.rpm / 1400, .55, 1.9) * doppler(v)));
            Gta8Vehicle s = sirenCars[i];
            if (s == null) { AL10.alSourcef(sirens[i], AL10.AL_GAIN, 0); continue; }
            place(sirens[i], s.x, s.y + 1.5, s.z);
            AL10.alSourcef(sirens[i], AL10.AL_GAIN, (float) (attenuate(sd[i], 15, 500) * .8 * volume));
            AL10.alSourcef(sirens[i], AL10.AL_PITCH, (float) doppler(s));
        }
        // Weather and city beds, attached to the listener.
        boolean indoors = game.cameraIndoors;
        double rainGain = game.weather.rain * (indoors ? .25 : .85);
        double windGain = .08 + game.weather.rain * .3 + Math.max(0, game.camera.y - 20) * .004;
        double cityGain = .22 * (1 - Gta8Math.smooth(300, 700, Math.hypot(cx, cz))) * (indoors ? .4 : 1);
        for (int s : new int[]{rain, wind, city}) AL10.alSource3f(s, AL10.AL_POSITION, 0, 0, 0);
        AL10.alSourcef(rain, AL10.AL_GAIN, (float) (rainGain * volume));
        AL10.alSourcef(wind, AL10.AL_GAIN, (float) (windGain * volume));
        AL10.alSourcef(city, AL10.AL_GAIN, (float) (cityGain * volume));
        Gta8Police.Helicopter h = game.police.heli;
        if (h != null) {
            place(heli, h.x, h.y, h.z);
            double d = Math.sqrt((h.x - cx) * (h.x - cx) + (h.y - cy) * (h.y - cy) + (h.z - cz) * (h.z - cz));
            AL10.alSourcef(heli, AL10.AL_GAIN, (float) (attenuate(d, 30, 900) * .9 * volume));
        } else AL10.alSourcef(heli, AL10.AL_GAIN, 0);
    }
    private static void insert(Gta8Vehicle[] list, double[] dist, Gta8Vehicle v, double d) {
        if (d < dist[0]) { dist[1] = dist[0]; list[1] = list[0]; dist[0] = d; list[0] = v; }
        else if (d < dist[1]) { dist[1] = d; list[1] = v; }
    }
    private double doppler(Gta8Vehicle v) {
        double dx = cx - v.x, dz = cz - v.z, d = Math.max(.1, Math.hypot(dx, dz));
        double approach = (v.vx * dx + v.vz * dz) / d;
        return Gta8Math.clamp(343 / (343 - approach), .8, 1.25);
    }

    // ------------------------------------------------------------------ synthesis
    private short[] synth(S s) {
        switch (s) {
            case ENGINE_LOW: return engine(40, 1.0, .35, 7);
            case ENGINE_HIGH: return engine(110, 1.0, .55, 11);
            case SKID: return skid();
            case SIREN: return siren();
            case RAIN: return rain();
            case WIND: return wind();
            case CITY: return city();
            case HELI: return heli();
            case PISTOL: return gunshot(.32, 1400, 90, .9, 18);
            case SMG: return gunshot(.22, 1800, 110, .8, 24);
            case RIFLE: return gunshot(.45, 1100, 70, 1.0, 13);
            case SHOTGUN: return gunshot(.6, 700, 55, 1.0, 9);
            case SNIPER: return gunshot(.9, 900, 45, 1.0, 6);
            case DISTANT: return lowpass(gunshot(.7, 500, 50, .8, 7), .06);
            case RICOCHET: return ricochet();
            case METAL: return metal();
            case THUD: return thud(.25, 90);
            case GLASS: return glass();
            case CRASH: return crash();
            case EXPLOSION: return explosion();
            case PUNCH: return thud(.18, 140);
            case CASH: return chime(new double[]{1318, 1760, 2093}, .09, .5);
            case PICKUP: return chime(new double[]{880, 1320}, .07, .3);
            case RELOAD: return clicks(new double[]{0, .25, .7}, .9);
            case EMPTY: return clicks(new double[]{0}, .2);
            case HORN: return horn();
            case DOOR: return thud(.3, 70);
            case THUNDER: return thunder();
            case WASTED: return wasted();
            case JINGLE: return chime(new double[]{523, 659, 784, 1046}, .14, .9);
            case FAIL: return chime(new double[]{392, 349, 311}, .2, .9);
            case RADIO: return radio();
            case ALARM: return alarm();
            case SPLASH: return lowpass(noise(.6, 3), .3);
            case WHIZ: return whiz();
            default: return new short[RATE / 10];
        }
    }
    private short[] pcm(double[] v) {
        double peak = 1e-6;
        for (double x : v) peak = Math.max(peak, Math.abs(x));
        short[] out = new short[v.length];
        for (int i = 0; i < v.length; i++) out[i] = (short) Math.round(Math.tanh(v[i] / peak * 1.2) / Math.tanh(1.2) * 30000);
        return out;
    }
    private double[] white(int n) { double[] v = new double[n]; for (int i = 0; i < n; i++) v[i] = random.nextDouble() * 2 - 1; return v; }
    private short[] engine(double firing, double seconds, double noiseAmount, int harmonics) {
        int n = (int) (RATE * seconds);
        double f = Math.round(firing * seconds) / seconds;
        double[] v = new double[n];
        double[] noise = white(n);
        double lp = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE, phase = (t * f) % 1;
            double sum = 0;
            for (int h = 1; h <= harmonics; h++) sum += Math.sin(2 * Math.PI * f * h * t + h * 1.7) / Math.pow(h, 1.1);
            double pulse = Math.exp(-phase * 7) * 1.2;
            lp += (noise[i] - lp) * .08;
            v[i] = sum * (.55 + .45 * pulse) + lp * noiseAmount * 3 * pulse + Math.sin(2 * Math.PI * f * .5 * t) * .5;
        }
        return pcm(v);
    }
    private short[] skid() {
        int n = RATE;
        double[] v = new double[n], w = white(n);
        double bp = 0, lp = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            lp += (w[i] - lp) * .35; bp = lp - bp * .5;
            v[i] = bp * .6 + Math.sin(2 * Math.PI * (1150 + 60 * Math.sin(2 * Math.PI * 3 * t)) * t) * .5 * (.8 + .2 * Math.sin(2 * Math.PI * 7 * t));
        }
        return pcm(v);
    }
    private short[] siren() {
        int n = RATE * 4;
        double[] v = new double[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE, cycle = (t % 4) / 4;
            double tri = cycle < .5 ? cycle * 2 : 2 - cycle * 2;
            double f = 620 + 780 * tri;
            phase += 2 * Math.PI * f / RATE;
            v[i] = Math.sin(phase) + .35 * Math.sin(phase * 2) + .15 * Math.sin(phase * 3);
        }
        return pcm(v);
    }
    private short[] rain() {
        int n = RATE * 2;
        double[] v = new double[n], w = white(n);
        double a = 0, b = 0;
        for (int i = 0; i < n; i++) {
            a += (w[i] - a) * .5; b += (a - b) * .2;
            v[i] = a - b * .7 + (random.nextDouble() < .002 ? (random.nextDouble() - .5) * 4 : 0);
        }
        return pcm(crossfade(v));
    }
    private short[] wind() {
        int n = RATE * 3;
        double[] v = new double[n], w = white(n);
        double lp = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            lp += (w[i] - lp) * .02;
            v[i] = lp * (.6 + .4 * Math.sin(2 * Math.PI * t / 3 + Math.sin(2 * Math.PI * t * 2 / 3)));
        }
        return pcm(crossfade(v));
    }
    private short[] city() {
        int n = RATE * 4;
        double[] v = new double[n], w = white(n);
        double lp = 0, lp2 = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            lp += (w[i] - lp) * .03; lp2 += (lp - lp2) * .05;
            v[i] = lp2 * 3 + Math.sin(2 * Math.PI * 55 * t) * .08 + lp * .4;
        }
        return pcm(crossfade(v));
    }
    private short[] heli() {
        int n = RATE;
        double[] v = new double[n], w = white(n);
        double lp = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE, blade = (t * 18) % 1;
            lp += (w[i] - lp) * .15;
            v[i] = lp * Math.exp(-blade * 9) * 2.5 + Math.sin(2 * Math.PI * 18 * t) * .4 + Math.sin(2 * Math.PI * 610 * t) * .05;
        }
        return pcm(v);
    }
    private short[] gunshot(double seconds, double crack, double body, double tail, double decay) {
        int n = (int) (RATE * seconds);
        double[] v = new double[n], w = white(n);
        double lp = 0, lp2 = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            lp += (w[i] - lp) * Math.min(1, crack / RATE * 6);
            lp2 += (w[i] - lp2) * .02;
            double attack = Math.min(1, t * 2000);
            v[i] = attack * (w[i] * Math.exp(-t * 90) * 1.4 + lp * Math.exp(-t * decay) + lp2 * 4 * tail * Math.exp(-t * decay * .5) + Math.sin(2 * Math.PI * body * t) * Math.exp(-t * 30) * 1.2);
        }
        return pcm(v);
    }
    private short[] lowpass(short[] in, double k) {
        double[] v = new double[in.length];
        double lp = 0;
        for (int i = 0; i < in.length; i++) { lp += (in[i] - lp) * k; v[i] = lp; }
        return pcm(v);
    }
    private short[] noise(double seconds, double decay) {
        int n = (int) (RATE * seconds);
        double[] v = white(n);
        for (int i = 0; i < n; i++) v[i] *= Math.exp(-i / (double) RATE * decay);
        return pcm(v);
    }
    private short[] ricochet() {
        int n = (int) (RATE * .3);
        double[] v = new double[n], w = white(n);
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            v[i] = w[i] * Math.exp(-t * 60) + Math.sin(2 * Math.PI * (3200 - t * 5000) * t) * Math.exp(-t * 14) * .6;
        }
        return pcm(v);
    }
    private short[] metal() {
        int n = (int) (RATE * .4);
        double[] v = new double[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            v[i] = (Math.sin(2 * Math.PI * 1830 * t) + .7 * Math.sin(2 * Math.PI * 2750 * t) + .5 * Math.sin(2 * Math.PI * 4120 * t)) * Math.exp(-t * 18) + (random.nextDouble() - .5) * Math.exp(-t * 120);
        }
        return pcm(v);
    }
    private short[] thud(double seconds, double f) {
        int n = (int) (RATE * seconds);
        double[] v = new double[n], w = white(n);
        double lp = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            lp += (w[i] - lp) * .1;
            v[i] = Math.sin(2 * Math.PI * f * t * (1 - t)) * Math.exp(-t * 22) + lp * Math.exp(-t * 40) * 1.5;
        }
        return pcm(v);
    }
    private short[] glass() {
        int n = (int) (RATE * .9);
        double[] v = new double[n], w = white(n);
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            double hp = i > 0 ? w[i] - w[i - 1] : 0;
            v[i] = hp * Math.exp(-t * 9);
            if (random.nextDouble() < .004) for (int k = 0; k < 600 && i + k < n; k++) v[i + k] += Math.sin(2 * Math.PI * (3000 + random.nextDouble() * 4000) * k / RATE) * Math.exp(-k / 90.0) * .5;
        }
        return pcm(v);
    }
    private short[] crash() {
        int n = (int) (RATE * 1.1);
        double[] v = new double[n], w = white(n);
        double lp = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            lp += (w[i] - lp) * .08;
            v[i] = lp * 2 * Math.exp(-t * 6) + w[i] * Math.exp(-t * 20) * .7 + Math.sin(2 * Math.PI * 55 * t) * Math.exp(-t * 12)
                    + (Math.sin(2 * Math.PI * 620 * t) + Math.sin(2 * Math.PI * 940 * t)) * Math.exp(-t * 8) * .25;
        }
        return pcm(v);
    }
    private short[] explosion() {
        int n = RATE * 3;
        double[] v = new double[n], w = white(n);
        double lp = 0, lp2 = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            lp += (w[i] - lp) * .05; lp2 += (lp - lp2) * .04;
            v[i] = (w[i] * Math.exp(-t * 25) + lp * 3 * Math.exp(-t * 2.2) + lp2 * 10 * Math.exp(-t * 1.2)) * Math.min(1, t * 400) + Math.sin(2 * Math.PI * 38 * t) * Math.exp(-t * 3) * 1.5;
        }
        return pcm(v);
    }
    private short[] chime(double[] notes, double spacing, double seconds) {
        int n = (int) (RATE * seconds);
        double[] v = new double[n];
        for (int k = 0; k < notes.length; k++) {
            int start = (int) (k * spacing * RATE);
            for (int i = start; i < n; i++) {
                double t = (i - start) / (double) RATE;
                v[i] += (Math.sin(2 * Math.PI * notes[k] * t) + .3 * Math.sin(4 * Math.PI * notes[k] * t)) * Math.exp(-t * 6);
            }
        }
        return pcm(v);
    }
    private short[] clicks(double[] times, double seconds) {
        int n = (int) (RATE * seconds);
        double[] v = new double[n];
        for (double at : times) {
            int start = (int) (at * RATE);
            for (int i = start; i < Math.min(n, start + 1200); i++) {
                double t = (i - start) / (double) RATE;
                v[i] += (random.nextDouble() - .5) * Math.exp(-t * 300) + Math.sin(2 * Math.PI * 2400 * t) * Math.exp(-t * 200) * .5;
            }
        }
        return pcm(v);
    }
    private short[] horn() {
        int n = (int) (RATE * .55);
        double[] v = new double[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            double env = Math.min(1, t * 60) * Math.min(1, (n - i) / 400.0);
            v[i] = env * (Math.signum(Math.sin(2 * Math.PI * 415 * t)) * .5 + Math.signum(Math.sin(2 * Math.PI * 494 * t)) * .5) * .6;
        }
        return lowpass(pcm(v), .35);
    }
    private short[] thunder() {
        int n = RATE * 5;
        double[] v = new double[n], w = white(n);
        double lp = 0, lp2 = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            lp += (w[i] - lp) * .02; lp2 += (lp - lp2) * .05;
            double rumble = Math.exp(-t * .8) * (1 + .6 * Math.sin(2 * Math.PI * 1.3 * t) * Math.sin(2 * Math.PI * .4 * t));
            v[i] = (lp2 * 12 + lp * 2 * Math.exp(-t * 3)) * rumble * Math.min(1, t * 8);
        }
        return pcm(v);
    }
    private short[] wasted() {
        int n = RATE * 3;
        double[] v = new double[n], w = white(n);
        double lp = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            lp += (w[i] - lp) * .03;
            v[i] = Math.sin(2 * Math.PI * 55 * t) * Math.exp(-t * .9) * 1.2 + lp * 3 * Math.exp(-t * 1.5) + Math.sin(2 * Math.PI * 110 * t + Math.sin(2 * Math.PI * 3 * t)) * .3 * Math.exp(-t);
        }
        return pcm(v);
    }
    private short[] radio() {
        int n = (int) (RATE * .5);
        double[] v = new double[n], w = white(n);
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            v[i] = t < .08 ? Math.sin(2 * Math.PI * 1200 * t) : t < .38 ? w[i] * .35 * (.5 + .5 * Math.sin(2 * Math.PI * 23 * t)) : t < .44 ? Math.sin(2 * Math.PI * 900 * t) : 0;
        }
        return pcm(v);
    }
    private short[] alarm() {
        int n = RATE * 4;
        double[] v = new double[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            v[i] = Math.signum(Math.sin(2 * Math.PI * 22 * t)) * Math.sin(2 * Math.PI * 1800 * t) * .7 * Math.min(1, (n - i) / 2000.0);
        }
        return pcm(v);
    }
    private short[] whiz() {
        int n = (int) (RATE * .25);
        double[] v = new double[n], w = white(n);
        for (int i = 0; i < n; i++) { double t = i / (double) RATE; v[i] = w[i] * Math.sin(Math.PI * t / .25) * (.3 + Math.sin(2 * Math.PI * (2400 - t * 3000) * t) * .5); }
        return pcm(v);
    }
    /** Seamless loop: the tail is blended into the head and removed, so the last sample flows into the first. */
    private static double[] crossfade(double[] v) {
        int f = Math.min(v.length / 4, 2000);
        double[] out = java.util.Arrays.copyOf(v, v.length - f);
        for (int i = 0; i < f; i++) { double k = i / (double) f; out[i] = v[i] * k + v[v.length - f + i] * (1 - k); }
        return out;
    }
}
