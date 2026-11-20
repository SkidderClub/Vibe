// What a server sees of your own fighting and of AIAura V2's, side by side.
//   node tools/aiaura-trace-stats.js <trace.csv>            own (aura column 0) vs aura (1)
//   node tools/aiaura-trace-stats.js <trace.csv> <other.csv> the first file's ticks vs the second's (e.g. simulated)
// Columns as AIAuraV2Module.record writes them; the simulated harness writes the first 15.
const fs = require("fs");
const load = file => fs.readFileSync(file, "utf8").trim().split(/\r?\n/).slice(1).map(l => l.split(",").map(Number));
const wrap = a => ((a + 180) % 360 + 360) % 360 - 180;
const q = (a, p) => { const s = [...a].sort((x, y) => x - y); return s.length ? s[Math.min(s.length - 1, Math.floor(s.length * p))] : NaN; };
const mean = a => a.reduce((s, v) => s + v, 0) / (a.length || 1);
const sd = a => { const m = mean(a); return Math.sqrt(mean(a.map(v => (v - m) ** 2))); };
const share = (a, pred) => a.length ? a.filter(pred).length / a.length : NaN;
const corr = (xs, ys) => { const mx = mean(xs), my = mean(ys); let a = 0, b = 0, c = 0; for (let i = 0; i < xs.length; i++) { a += (xs[i] - mx) * (ys[i] - my); b += (xs[i] - mx) ** 2; c += (ys[i] - my) ** 2; } return a / Math.sqrt(b * c); };

function stats(rows) {
  const T = rows.map(r => ({ tick: r[0], ms: r[1], yaw: r[2], pitch: r[3], sx: r[4], sy: r[5], sz: r[6], ex: r[7], ey: r[8], ez: r[9], dist: r[10],
    swings: r[11], attacks: r[12], hurt: r[13], rest: r[14], fight: r.length > 18 ? r[18] : 0 }));
  for (const t of T) {
    const dx = t.ex - t.sx, dz = t.ez - t.sz, dy = t.ey + 0.9 - (t.sy + 1.62), flat = Math.hypot(dx, dz);
    t.toYaw = Math.atan2(dz, dx) * 180 / Math.PI - 90;
    t.toPitch = -Math.atan2(dy, flat) * 180 / Math.PI;
    t.offYaw = wrap(t.yaw - t.toYaw);
    t.offPitch = t.pitch - t.toPitch;
    t.halfWidth = Math.atan2(0.4, Math.max(flat, 0.3)) * 180 / Math.PI;
  }
  const D = [];
  for (let i = 1; i < T.length; i++) if (T[i].tick - T[i - 1].tick === 1 && T[i].fight === T[i - 1].fight && T[i].dist < 4.5)
    D.push({ dyaw: wrap(T[i].yaw - T[i - 1].yaw), dpitch: T[i].pitch - T[i - 1].pitch, bearing: wrap(T[i].toYaw - T[i - 1].toYaw), t: T[i] });
  const S = {};
  const ay = D.map(d => Math.abs(d.dyaw)), ap = D.map(d => Math.abs(d.dpitch)), mv = D.filter(d => d.dyaw !== 0);
  S["fight ticks (<4.5 blocks)"] = D.length;
  S["yaw still %"] = 100 * share(D, d => d.dyaw === 0);
  S["pitch still %"] = 100 * share(D, d => d.dpitch === 0);
  S["both still %"] = 100 * share(D, d => d.dyaw === 0 && d.dpitch === 0);
  S["|dyaw| q50"] = q(ay, .5); S["|dyaw| q90"] = q(ay, .9); S["|dyaw| q99"] = q(ay, .99);
  S["|dpitch| q50"] = q(ap, .5); S["|dpitch| q90"] = q(ap, .9);
  S["pitch/yaw movement"] = mean(ap) / mean(ay);
  S["yaw follows them, same tick"] = corr(D.map(d => d.dyaw), D.map(d => d.bearing));
  S["yaw follows them, 1 tick late"] = corr(D.slice(1).map(d => d.dyaw), D.slice(0, -1).map(d => d.bearing));
  S["yaw follows them, 2 ticks late"] = corr(D.slice(2).map(d => d.dyaw), D.slice(0, -2).map(d => d.bearing));
  const own = D.map(d => d.dyaw - d.bearing);       // the turn less what keeping up with them took
  S["sd of own turn (less keeping up)"] = sd(own);
  S["yaw autocorr 1 tick"] = corr(D.slice(1).map(d => d.dyaw), D.slice(0, -1).map(d => d.dyaw));
  let flips = 0, both = 0;
  for (let i = 1; i < D.length; i++) if (D[i].dyaw !== 0 && D[i - 1].dyaw !== 0) { both++; if (Math.sign(D[i].dyaw) !== Math.sign(D[i - 1].dyaw)) flips++; }
  S["yaw direction flips %"] = 100 * flips / (both || 1);
  const acc = []; for (let i = 1; i < D.length; i++) acc.push(D[i].dyaw - D[i - 1].dyaw);
  S["yaw acceleration sd"] = sd(acc);

  const R = T.filter(t => t.dist <= 3.0);
  const rel = R.map(t => Math.abs(t.offYaw / t.halfWidth));
  S["in reach: aim off middle, half widths q50"] = q(rel, .5);
  S["in reach: aim off middle, half widths q90"] = q(rel, .9);
  S["in reach: aim outside them %"] = 100 * share(rel, v => v > 1);
  S["in reach: pitch off middle sd"] = sd(R.map(t => t.offPitch));

  const sw = R.reduce((s, t) => s + t.swings, 0), at = R.reduce((s, t) => s + t.attacks, 0);
  S["in reach: CPS"] = sw / (R.length * 0.05);
  S["in reach: attacks per click %"] = 100 * at / (sw || 1);
  S["in reach: ticks with 2+ clicks %"] = 100 * share(R, t => t.swings >= 2);
  // gaps between ticks with a click, within a fight, while near
  const ticks = T.filter(t => t.swings > 0 && t.dist <= 4.0), gaps = [];
  for (let i = 1; i < ticks.length; i++) if (ticks[i].fight === ticks[i - 1].fight) { const g = ticks[i].tick - ticks[i - 1].tick; if (g <= 20) gaps.push(g); }
  S["click-tick gap q10 (ticks)"] = q(gaps, .1); S["click-tick gap q50"] = q(gaps, .5); S["click-tick gap q90"] = q(gaps, .9);
  S["click-tick gap mean"] = mean(gaps); S["click-tick gap sd"] = sd(gaps);
  const hist = {}; gaps.forEach(g => hist[g] = (hist[g] || 0) + 1);
  let H = 0; for (const k in hist) { const p = hist[k] / gaps.length; H -= p * Math.log2(p); }
  S["click-tick gap entropy (bits)"] = H;
  S["click-tick gaps of 1/2/3/4+ %"] = [1, 2, 3].map(g => (100 * (hist[g] || 0) / (gaps.length || 1)).toFixed(0)).join("/") + "/" + (100 * gaps.filter(g => g >= 4).length / (gaps.length || 1)).toFixed(0);
  // a click (swing) one tick after a tick without an attack, at the air: vanilla waits ten ticks after one of those
  let afterMiss = 0, misses = 0;
  for (let i = 0; i < T.length; i++) if (T[i].swings > 0 && T[i].attacks === 0) {
    misses++;
    for (let j = i + 1; j < Math.min(T.length, i + 10); j++) if (T[j].fight === T[i].fight && T[j].tick - T[i].tick < 10 && T[j].swings > 0) { afterMiss++; break; }
  }
  S["clicks at air followed by another within 10 ticks %"] = 100 * afterMiss / (misses || 1);
  // attacks while they cannot be hurt, and hits that land the very tick they can be hurt again
  const atk = T.filter(t => t.attacks > 0 && t.dist <= 3.2);
  S["attacks while they are hurt (no damage) %"] = 100 * share(atk, t => t.hurt > 1);
  const landed = []; let res = 0; for (const t of T) { if (res > 0) res--; if (t.attacks > 0 && res === 0) { landed.push(t); res = 10; } }
  const lg = landed.slice(1).map((v, i) => v.tick - landed[i].tick).filter(g => g < 30);
  S["damaging hit gap q50 (ticks)"] = q(lg, .5);
  S["damaging hit gap exactly 10 %"] = 100 * share(lg, g => g === 10);
  S["damaging hit gap 10-11 %"] = 100 * share(lg, g => g <= 11);
  S["resting ticks in reach %"] = 100 * share(R, t => t.rest === 1);
  return S;
}

const a = load(process.argv[2]);
const [left, right, names] = process.argv[3]
  ? [a, load(process.argv[3]), ["first", "second"]]
  : [a.filter(r => r[19] === 0), a.filter(r => r[19] === 1), ["you", "aura"]];
const L = stats(left), Rt = stats(right);
const fmt = v => typeof v === "string" ? v : Number.isFinite(v) ? (Math.abs(v) >= 100 ? v.toFixed(0) : v.toFixed(2)) : "-";
console.log("".padEnd(54) + names[0].padStart(10) + names[1].padStart(10));
for (const k of Object.keys(L)) console.log(k.padEnd(54) + fmt(L[k]).padStart(10) + fmt(Rt[k]).padStart(10));
