// Procedural material library. All patterns are anti-aliased with fwidth and fade to their mean at distance.
uniform sampler2D uSigns;
uniform sampler2D uFoliage;
uniform sampler2D uSignals;
uniform vec4 uPalette[8];
uniform float uLightGroups[12];
uniform float uDamage;

vec3 tangentOf(vec3 N) { return abs(N.y) > .9 ? vec3(1.0, 0.0, 0.0) : normalize(vec3(N.z, 0.0, -N.x)); }
vec3 bump(vec3 N, vec2 grad) {
    vec3 T = tangentOf(N), B = cross(N, T);
    return normalize(N - T * grad.x - B * grad.y);
}
float band(float x, float c, float halfWidth, float fw) { return saturate((halfWidth - abs(x - c)) / max(fw, 1e-4) + .5); }
float aaStep(float edge, float x, float fw) { return saturate((x - edge) / max(fw, 1e-4) + .5); }
vec3 heightGrad(vec2 p, float scale, float amount) {
    float h = wnoise(p * scale), hx = wnoise((p + vec2(.35 / scale, 0.0)) * scale), hy = wnoise((p + vec2(0.0, .35 / scale)) * scale);
    return vec3((hx - h) * amount, (hy - h) * amount, h);
}

// ------------------------------------------------------------------ ground
void asphalt(inout Surface s, vec3 P, vec2 uv, float param, float len, bool markings) {
    float fw = max(fwidth(uv.x), fwidth(uv.y));
    float n2 = vnoise(P.xz * .27), n3 = wnoise(P.xz * 21.0), n1 = vnoise(P.xz * 1.7);
    vec3 base = vColor.rgb * (.82 + .3 * n2 + .14 * (n3 - .5));
    float repair = step(.74, vnoise(floor(P.xz / vec2(4.5, 2.2)) * 5.3 + 11.0));
    base *= 1.0 - .15 * repair;
    float cr = abs(fbm(P.xz * .8) - .5);
    base *= 1.0 - .22 * (1.0 - smoothstep(0.0, .005 + fw * .5, cr)) * step(.72, n2) * (1.0 - saturate(fw * 6.0));
    float rough = .86 - .08 * n1;
    if (markings) {
        float lanes = mod(param, 4.0), parking = step(3.5, param);
        float u = uv.x, au = abs(u), v = uv.y;
        // Tyre polish and oil drips darken each lane's wheel paths and centre.
        float lanePos = fract(au / 3.5);
        base *= 1.0 - .09 * (band(lanePos, .5, .12, .02) * step(au, lanes * 3.5));
        base *= 1.0 - .05 * (band(lanePos, .22, .1, .03) + band(lanePos, .78, .1, .03)) * step(au, lanes * 3.5);
        float yellow = band(au, .17, .06, fw);
        float white = 0.0;
        float inZone = step(6.0, v) * step(v, len - 6.0);
        if (lanes > 1.5) white += band(au, 3.5, .06, fw) * step(mod(v, 9.0), 3.0) * inZone;
        white += band(au, lanes * 3.5, .07, fw) * step(1.0, parking + step(1.5, lanes)) * inZone;
        if (parking > .5) white += band(au, lanes * 3.5 + 1.2, 1.2, fw) * band(mod(v - 9.0 + 3.15, 6.3), 0.0, .05, fw) * inZone;
        float halfW = lanes * 3.5 + (parking > .5 ? 2.6 : .4);
        float cross = (band(v, 2.5, 1.5, fw) + band(v, len - 2.5, 1.5, fw)) * step(au, halfW - .25);
        white += cross * band(fract(u / 1.2 + .25), .5, .25, fw / 1.2);
        white += band(v, len - 5.1, .2, fw) * step(0.0, u) * step(u, lanes * 3.5);
        white += band(v, 5.1, .2, fw) * step(u, 0.0) * step(-lanes * 3.5, u);
        yellow *= inZone;
        float wear = smoothstep(.15, .55, vnoise(P.xz * 3.7 + 5.0));
        float paint = saturate(white + yellow) * wear;
        vec3 paintColor = yellow > .5 ? vec3(.78, .6, .16) : vec3(.82, .82, .78);
        base = mix(base, paintColor, paint * .9);
        rough = mix(rough, .6, paint);
    }
    s.albedo = base;
    s.rough = rough;
    vec3 g = heightGrad(P.xz, 9.0, .9);
    s.normal = bump(s.normal, g.xy * (1.0 - saturate(fw * 4.0)));
}

void concreteSlabs(inout Surface s, vec3 P, float size) {
    vec2 q = abs(s.normal.y) > .5 ? P.xz : vec2(dot(P.xz, vec2(s.normal.z, -s.normal.x)), P.y);
    float fw = max(fwidth(q.x), fwidth(q.y));
    vec2 g = q / size, f = fract(g);
    float seam = min(min(f.x, 1.0 - f.x), min(f.y, 1.0 - f.y)) * size;
    float seamMask = (1.0 - smoothstep(.004, .012 + fw, seam)) * (1.0 - saturate(fw * 6.0));
    float tone = hash12(floor(g));
    vec3 c = vColor.rgb * (.9 + .12 * tone) * (.9 + .14 * fbm(q * .6));
    c *= 1.0 - .25 * step(.965, wnoise(q * 9.0)) * (1.0 - saturate(fw * 10.0));
    s.albedo = c * (1.0 - .32 * seamMask);
    s.rough = .82 + .08 * tone;
    vec3 g2 = heightGrad(q, 14.0, .5);
    s.normal = bump(s.normal, g2.xy * (1.0 - saturate(fw * 5.0)));
}

void pavers(inout Surface s, vec3 P) {
    float fw = fwidth(P.x) + fwidth(P.z);
    vec2 g = P.xz / vec2(.6, .3);
    g.x += mod(floor(g.y), 2.0) * .5;
    vec2 f = fract(g);
    float seam = min(min(f.x, 1.0 - f.x) * .6, min(f.y, 1.0 - f.y) * .3);
    float m = (1.0 - smoothstep(.004, .01 + fw, seam)) * (1.0 - saturate(fw * 8.0));
    s.albedo = vColor.rgb * (.85 + .2 * hash12(floor(g))) * (1.0 - .35 * m);
    s.rough = .78;
}

void grass(inout Surface s, vec3 P) {
    float n = fbm(P.xz * .09), d = vnoise(P.xz * .8), fine = wnoise(P.xz * 30.0);
    vec3 lush = vColor.rgb, dry = vec3(.46, .42, .24);
    vec3 c = mix(lush, dry, smoothstep(.45, .8, n) * .7);
    c *= .78 + .3 * d + .16 * (fine - .5);
    s.albedo = c; s.rough = .96; s.sss = .25;
    vec3 g = heightGrad(P.xz, 22.0, 1.6);
    s.normal = bump(s.normal, g.xy * (1.0 - saturate(fwidth(P.x) * 5.0)));
}

void terrain(inout Surface s, vec3 P) {
    vec3 N = normalize(vNormal);
    float slope = 1.0 - N.y;
    float n = fbm(P.xz * .035), d = vnoise(P.xz * .4);
    vec3 dryGrass = mix(vec3(.52, .45, .27), vec3(.33, .36, .2), smoothstep(.35, .7, n));
    vec3 rock = mix(vec3(.42, .38, .33), vec3(.55, .5, .44), vnoise(P.xz * .12 + P.y * .3));
    vec3 dirt = vec3(.5, .4, .3);
    vec3 c = mix(dryGrass, dirt, smoothstep(.62, .75, d) * .6);
    c = mix(c, rock, smoothstep(.28, .5, slope + .15 * (n - .5)));
    c *= .85 + .25 * vnoise(P.xz * 1.3);
    s.albedo = c; s.rough = .92;
    vec3 g = heightGrad(P.xz + P.y, 3.0, 1.2);
    s.normal = bump(s.normal, g.xy * (.5 + slope));
}

void sand(inout Surface s, vec3 P) {
    float ripple = sin(P.z * 2.1 + vnoise(P.xz * .3) * 6.0) * .5 + .5;
    vec3 c = vColor.rgb * (.9 + .08 * ripple + .1 * (wnoise(P.xz * 40.0) - .5));
    float wet = smoothstep(-.5, -1.15, P.y);
    c *= 1.0 - .38 * wet;
    s.albedo = c; s.rough = mix(.9, .35, wet);
    s.normal = bump(s.normal, vec2(cos(P.z * 2.1) * .08, 0.0) * (1.0 - saturate(fwidth(P.z) * 3.0)));
}

// ------------------------------------------------------------------ walls
void brick(inout Surface s, vec2 uv) {
    float fw = max(fwidth(uv.x), fwidth(uv.y));
    vec2 b = vec2(uv.x / .23, uv.y / .075);
    float row = floor(b.y);
    b.x += mod(row, 2.0) * .5;
    vec2 f = fract(b), id = floor(b);
    float ex = min(f.x, 1.0 - f.x) * .23, ey = min(f.y, 1.0 - f.y) * .075;
    float lod = saturate(fw * 25.0);
    float mortar = (1.0 - smoothstep(.004, .009 + fw, min(ex, ey))) * (1.0 - lod);
    float h = hash12(id + 13.0);
    vec3 brickColor = vColor.rgb * (.78 + .38 * h) * vec3(1.0, .96 + .06 * hash12(id), .95);
    brickColor = mix(brickColor, vColor.rgb * .97, lod);
    s.albedo = mix(brickColor, vec3(.6, .58, .54), mortar * .85);
    s.rough = .88;
    vec2 grad = vec2(ex < ey ? sign(f.x - .5) : 0.0, ey <= ex ? sign(f.y - .5) : 0.0) * mortar * .6;
    s.normal = bump(s.normal, grad);
}
void plaster(inout Surface s, vec2 uv) {
    float fw = max(fwidth(uv.x), fwidth(uv.y));
    s.albedo = vColor.rgb * (.93 + .09 * fbm(uv * 2.3) + .05 * (wnoise(uv * 37.0) - .5) * (1.0 - saturate(fw * 10.0)));
    // Streaks below sills and near the ground.
    s.albedo *= 1.0 - .08 * smoothstep(1.2, 0.0, uv.y);
    s.rough = .9;
    vec3 g = heightGrad(uv, 26.0, .8);
    s.normal = bump(s.normal, g.xy * (1.0 - saturate(fw * 8.0)));
}
void concreteWall(inout Surface s, vec2 uv, float fh) {
    float fw = max(fwidth(uv.x), fwidth(uv.y));
    vec2 panel = vec2(uv.x / 3.0, uv.y / fh);
    vec2 f = fract(panel);
    float joint = (1.0 - smoothstep(.005, .012 + fw, min(min(f.x, 1.0 - f.x) * 3.0, min(f.y, 1.0 - f.y) * fh))) * (1.0 - saturate(fw * 8.0));
    s.albedo = vColor.rgb * (.9 + .12 * hash12(floor(panel)) + .06 * fbm(uv * 1.7)) * (1.0 - .3 * joint);
    s.rough = .86;
}
void stone(inout Surface s, vec2 uv) {
    float fw = max(fwidth(uv.x), fwidth(uv.y));
    vec2 b = vec2(uv.x / 1.2, uv.y / .6);
    b.x += mod(floor(b.y), 2.0) * .5;
    vec2 f = fract(b);
    float joint = (1.0 - smoothstep(.004, .01 + fw, min(min(f.x, 1.0 - f.x) * 1.2, min(f.y, 1.0 - f.y) * .6))) * (1.0 - saturate(fw * 10.0));
    s.albedo = vColor.rgb * (.88 + .16 * hash12(floor(b))) * (.95 + .08 * fbm(uv * 4.0)) * (1.0 - .35 * joint);
    s.rough = .8;
}
void wallBase(inout Surface s, float wallType, vec2 uv, float fh) {
    if (wallType < .5) plaster(s, uv);
    else if (wallType < 1.5) brick(s, uv);
    else if (wallType < 2.5) concreteWall(s, uv, fh);
    else stone(s, uv);
}

// Room behind a window, ray-marched analytically as an axis-aligned box ("interior mapping").
vec3 room(vec2 cell, vec3 N, vec3 V, float bay, float fh, float rnd, float lit, float shop) {
    vec3 r = vec3(N.z, 0.0, -N.x);
    vec3 d = -V;
    vec3 ld = vec3(dot(d, r), d.y, -dot(d, N));
    ld.z = max(ld.z, .02);
    vec3 o = vec3(cell.x, cell.y, 0.0);
    float depth = shop > .5 ? 7.0 : 4.0 + rnd * 3.0;
    float tx = abs(ld.x) < 1e-4 ? 1e4 : ((ld.x > 0.0 ? bay * .5 : -bay * .5) - o.x) / ld.x;
    float ty = abs(ld.y) < 1e-4 ? 1e4 : ((ld.y > 0.0 ? fh : 0.0) - o.y) / ld.y;
    float tz = depth / ld.z;
    float t = min(min(tx, ty), tz);
    vec3 h = o + ld * t;
    vec3 wallCol = mix(vec3(.82, .78, .7), vec3(.6, .7, .78), rnd);
    wallCol = mix(wallCol, vec3(.75, .62, .5), step(.7, fract(rnd * 7.3)));
    vec3 c;
    if (t == tz) {
        c = wallCol * (.9 + .1 * vnoise(h.xy * 3.0));
        if (shop > .5) {
            // Shelves of goods on the back wall.
            float shelf = step(.3, fract(h.y / .55)) * step(h.y, 2.2) * step(.3, h.y);
            vec3 goods = vec3(hash12(floor(h.xy * vec2(4.0, 1.8)) + rnd * 9.0), hash12(floor(h.xy * vec2(4.0, 1.8)) + 3.1), hash12(floor(h.xy * vec2(4.0, 1.8)) + 7.7));
            c = mix(c, goods * .8 + .1, shelf);
        } else {
            // A picture, a shelf or a doorway on some back walls.
            float frameX = abs(h.x - (rnd - .5) * bay * .4), frameY = abs(h.y - fh * .55);
            c = mix(c, vec3(.25, .22, .2) + rnd * .3, step(frameX, .35) * step(frameY, .25) * step(.4, rnd));
        }
    } else if (t == tx) c = wallCol * .78;
    else if (ld.y < 0.0) c = shop > .5 ? vec3(.7, .7, .68) : mix(vec3(.35, .24, .16), vec3(.42, .42, .45), step(.5, fract(rnd * 3.7)));
    else {
        c = vec3(.88);
        float fixture = 1.0 - smoothstep(.2, .45, length(vec2(h.x, h.z - depth * .5)) / (shop > .5 ? 4.0 : 1.0));
        c += fixture * lit * 1.2;
    }
    // Furniture silhouettes: a sofa or desk against the back half of the room.
    if (shop < .5 && t > 1.0 && h.y < .8 && h.z > depth * .45 && abs(h.x) < bay * .3) c *= .45;
    float falloff = mix(1.0, .45, saturate(h.z / depth));
    vec3 day = (uSkyAmbient * .55 + uSunColor * .06) * falloff;
    vec3 night = mix(vec3(1.0, .8, .55), vec3(.8, .9, 1.0), step(.6, rnd)) * (shop > .5 ? .26 : .3) * mix(.55, 1.0, falloff);
    return c * (day * (1.0 - lit * uNight) + night * lit * max(uNight, shop * .8));
}

void layoutParams(float layout, out float bay, out float ww, out float wh, out float sill) {
    if (layout < .5) { bay = 3.0; ww = 1.4; wh = 1.7; sill = .9; }
    else if (layout < 1.5) { bay = 2.4; ww = 1.1; wh = 2.0; sill = .75; }
    else if (layout < 2.5) { bay = 4.2; ww = 2.9; wh = 1.6; sill = .95; }
    else if (layout < 3.5) { bay = 1.8; ww = 1.25; wh = 2.3; sill = .55; }
    else if (layout < 4.5) { bay = 6.0; ww = 5.4; wh = 1.5; sill = 1.0; }
    else if (layout < 5.5) { bay = 3.6; ww = 2.2; wh = 2.25; sill = .3; }
    else if (layout < 6.5) { bay = 3.4; ww = 1.3; wh = 1.35; sill = 1.0; }
    else { bay = 4.4; ww = 2.2; wh = 1.3; sill = .95; }
}

float litFraction(float rnd) { return step(rnd, .12 + .26 * uNight); }

void windowFacade(inout Surface s, vec3 P, vec3 V, vec2 uv, float style, float seed) {
    float wallType = mod(style, 4.0);
    float layout = mod(floor(style / 4.0), 8.0);
    float fh = 3.0 + .2 * floor(style / 32.0);
    float bay, ww, wh, sill;
    layoutParams(layout, bay, ww, wh, sill);
    vec3 N = s.normal;
    wallBase(s, wallType, uv, fh);
    float fw = max(fwidth(uv.x), fwidth(uv.y));
    float cellU = floor(uv.x / bay), xu = (fract(uv.x / bay) - .5) * bay;
    float floorI = floor(uv.y / fh), yv = uv.y - floorI * fh;
    float rnd = hash12(vec2(cellU, floorI) + seed * 17.13);
    float lit = litFraction(hash12(vec2(cellU * 1.7, floorI * 3.1) + seed));
    float wx = ww * .5 - abs(xu), wy = min(yv - sill, sill + wh - yv);
    float edge = min(wx, wy);
    float inWin = saturate(edge / max(fw, 1e-4) + .5);
    float inGlass = saturate((edge - .075) / max(fw, 1e-4) + .5);
    float lod = smoothstep(.12, .45, fw);
    // Cornice/sill ledge under each window and a thin floor band on concrete.
    float ledge = band(yv, sill - .05, .05, fw) * step(abs(xu), ww * .5 + .12) * (1.0 - lod);
    s.albedo *= 1.0 - .18 * ledge;
    vec3 frameColor = mod(seed, 3.0) < 1.0 ? vec3(.85, .84, .8) : vec3(.12, .13, .14);
    float blinds = step(.55, rnd) * fract(rnd * 13.1);
    float blindY = sill + wh * (1.0 - blinds);
    float isBlind = step(blindY, yv);
    vec3 roomC = room(vec2(xu, yv), vec3(N.x, 0.0, N.z), V, bay, fh, rnd, lit, 0.0);
    vec3 blindC = mix(vec3(.85, .82, .74), vec3(.55, .6, .65), step(.8, rnd)) * (uSkyAmbient * .6 + uSunColor * .2 + lit * uNight * vec3(.36, .28, .2));
    vec3 inside = mix(roomC, blindC, isBlind);
    // Lintel shadow inside the reveal.
    inside *= mix(.6, 1.0, saturate((sill + wh - yv) / .3));
    // Distant windows collapse to an average so facades never shimmer.
    float coverage = (ww * wh) / (bay * fh);
    vec3 avgInside = (uSkyAmbient * .35 * (1.0 - uNight) + vec3(1.0, .82, .6) * uNight * (.22 + .45 * uNight) * .28);
    vec3 insideLod = mix(inside, avgInside, lod);
    float glass = mix(inGlass, coverage, lod);
    float frame = mix(inWin - inGlass, 0.0, lod);
    s.albedo = mix(s.albedo, frameColor, frame);
    s.albedo = mix(s.albedo, vec3(0.0), glass);
    s.rough = mix(s.rough, .04, glass);
    s.f0 = mix(s.f0, .06, glass);
    s.normal = normalize(mix(s.normal, N, glass));
    s.emissive += insideLod * glass * .9;
}

void curtainWall(inout Surface s, vec3 P, vec3 V, vec2 uv, float style, float seed) {
    float fw = max(fwidth(uv.x), fwidth(uv.y));
    float bay = 1.2 + .15 * mod(style, 4.0), fh = 3.8 + .2 * mod(floor(style / 4.0), 2.0);
    float spandrel = .9 + .3 * step(.5, fract(seed * .37));
    float xu = fract(uv.x / bay) * bay, floorI = floor(uv.y / fh), yv = uv.y - floorI * fh;
    float lod = smoothstep(.06, .3, fw);
    float mullion = (band(xu, 0.0, .045, fw) + band(xu, bay, .045, fw)) * (1.0 - lod);
    float slab = aaStep(fh - spandrel, yv, fw);
    float transom = band(yv, 0.0, .06, fw) * (1.0 - lod);
    float frame = saturate(mullion + transom);
    vec3 tint = vColor.rgb;
    float rnd = hash12(vec2(floor(uv.x / (bay * 3.0)), floorI) + seed);
    float floorBusy = step(.45, hash12(vec2(floorI, seed * 1.7)));
    float lit = step(rnd, (.08 + .3 * uNight) * (.4 + floorBusy));
    vec3 N = s.normal;
    vec3 inside = room(vec2((fract(uv.x / (bay * 3.0)) - .5) * bay * 3.0, yv), N, V, bay * 3.0, fh, rnd, lit, 0.0) * .55;
    // Office ceilings: rows of panel lights visible at night.
    inside += lit * uNight * vec3(.85, .92, 1.0) * .45 * step(fh - spandrel - .35, yv) * step(yv, fh - spandrel - .05);
    inside = mix(inside, (uSkyAmbient * .12 + vec3(.85, .9, 1.0) * uNight * (.15 + .5 * uNight) * .16), lod);
    vec3 spandrelC = tint * .45;
    s.albedo = mix(vec3(0.0), spandrelC, slab * (1.0 - frame));
    s.albedo = mix(s.albedo, vec3(.32, .34, .36), frame);
    s.metal = mix(0.0, .6, frame);
    s.rough = mix(.03, .35, frame);
    s.f0 = .09 + .06 * mod(style, 2.0);
    s.emissive += inside * (1.0 - slab) * (1.0 - frame) * mix(vec3(1.0), tint * 1.6, .35);
    // Slight panel warping breaks up perfect mirror reflections.
    vec2 warp = vec2(vnoise(vec2(floor(uv.x / bay), floorI) * 3.1) - .5, vnoise(vec2(floorI, floor(uv.x / bay)) * 2.3) - .5) * .045;
    s.normal = bump(N, warp * (1.0 - lod));
}

void shopfront(inout Surface s, vec3 P, vec3 V, vec2 uv, float style, float seed, float height) {
    float fw = max(fwidth(uv.x), fwidth(uv.y));
    bool lobby = style >= 16.0;
    float bay = lobby ? 3.0 : 2.4 + .4 * mod(style, 3.0);
    float glassTop = lobby ? height - .6 : 3.05;
    float xu = (fract(uv.x / bay) - .5) * bay;
    float cellU = floor(uv.x / bay);
    float kick = lobby ? .05 : .45;
    float inGlass = aaStep(kick, uv.y, fw) * (1.0 - aaStep(glassTop, uv.y, fw)) * saturate((bay * .5 - .06 - abs(xu)) / max(fw, 1e-4) + .5);
    float mullion = 1.0 - saturate((bay * .5 - .06 - abs(xu)) / max(fw, 1e-4) + .5);
    float isDoor = step(abs(mod(cellU + seed, 4.0) - 1.0), .2) * step(uv.y, 2.4);
    float rnd = hash12(vec2(cellU, seed));
    float open = lobby ? 1.0 : step(.2, fract(seed * .13 + .5)) * (1.0 - uNight * step(.75, fract(seed * .71)));
    vec3 N = s.normal;
    vec3 inside = room(vec2(xu, uv.y), N, V, bay, glassTop + .6, rnd, open, 1.0);
    if (lobby) inside = mix(inside, vec3(.9, .88, .82) * (uSkyAmbient * .8 + vec3(1.0, .95, .85) * max(uNight, .15) * .16), .5);
    // Base: dark kickplate, frames; above the glazing is the sign band in the wall colour.
    vec3 wall = vColor.rgb * (.9 + .08 * fbm(uv * 2.0));
    vec3 kickC = vec3(.12, .12, .13);
    s.albedo = uv.y < kick ? kickC : wall;
    s.albedo = mix(s.albedo, vec3(.1, .1, .11), mullion * step(kick, uv.y) * step(uv.y, glassTop));
    s.albedo = mix(s.albedo, vec3(0.0), inGlass);
    s.rough = mix(.7, .03, inGlass);
    s.f0 = mix(s.f0, .05, inGlass);
    s.emissive += inside * inGlass * (isDoor > .5 ? .7 : 1.0);
    if (isDoor > .5) s.albedo += vec3(.35) * band(xu, bay * .25, .03, fw) * band(uv.y, 1.1, .02, fw);
}

// ------------------------------------------------------------------ misc surfaces
void roof(inout Surface s, vec3 P) {
    float g = wnoise(P.xz * 18.0), n = fbm(P.xz * .4);
    s.albedo = vColor.rgb * (.82 + .25 * n + .12 * (g - .5));
    s.rough = .93;
}
void corrugated(inout Surface s, vec2 uv) {
    float fw = fwidth(uv.x);
    float w = sin(uv.x * 6.2832 / .19);
    float lod = saturate(fw * 12.0);
    s.normal = bump(s.normal, vec2(cos(uv.x * 6.2832 / .19) * .35 * (1.0 - lod), 0.0));
    float rust = smoothstep(.62, .8, fbm(uv * vec2(.8, .25))) * smoothstep(3.0, 0.0, uv.y) + smoothstep(.7, .85, fbm(uv * vec2(3.0, .2) + 7.0)) * .4;
    s.albedo = mix(vColor.rgb * (.92 + .06 * w * (1.0 - lod)), vec3(.35, .2, .12), rust * .6);
    s.rough = .55 + .3 * rust;
    s.metal = .35 * (1.0 - rust);
}
void siding(inout Surface s, vec2 uv) {
    float fw = fwidth(uv.y);
    float f = fract(uv.y / .2);
    float shadowLine = (1.0 - smoothstep(0.0, .1 + fw * 5.0, f)) * (1.0 - saturate(fw * 12.0));
    s.albedo = vColor.rgb * (.94 + .05 * fbm(uv * vec2(.5, 4.0))) * (1.0 - .35 * shadowLine);
    s.normal = bump(s.normal, vec2(0.0, -shadowLine * .5));
    s.rough = .75;
}
void tileRoof(inout Surface s, vec2 uv) {
    float fw = max(fwidth(uv.x), fwidth(uv.y));
    vec2 t = vec2(uv.x / .3, uv.y / .35);
    t.x += mod(floor(t.y), 2.0) * .5;
    vec2 f = fract(t);
    float lod = saturate(fw * 10.0);
    float barrel = sin(f.x * PI);
    s.albedo = vColor.rgb * (.8 + .3 * hash12(floor(t)) * (1.0 - lod)) * mix(.75 + .3 * barrel, .9, lod) * (1.0 - .3 * smoothstep(.85, 1.0, f.y) * (1.0 - lod));
    s.normal = bump(s.normal, vec2(cos(f.x * PI) * .6, -f.y * .3) * (1.0 - lod));
    s.rough = .7;
}
void wood(inout Surface s, vec2 uv, float plank) {
    float fw = max(fwidth(uv.x), fwidth(uv.y));
    float row = floor(uv.x / plank);
    float grain = sin((uv.y + hash12(vec2(row, 3.0)) * 10.0) * 18.0 + vnoise(uv * vec2(20.0, 1.0)) * 6.0) * .5 + .5;
    float gap = (1.0 - smoothstep(.004, .012 + fw, min(fract(uv.x / plank), 1.0 - fract(uv.x / plank)) * plank)) * (1.0 - saturate(fw * 10.0));
    s.albedo = vColor.rgb * (.8 + .2 * grain * (1.0 - saturate(fw * 8.0)) + .15 * hash12(vec2(row, 1.0))) * (1.0 - .6 * gap);
    s.rough = .78;
}
void floorTile(inout Surface s, vec3 P) {
    float fw = fwidth(P.x) + fwidth(P.z);
    vec2 g = P.xz / .6, f = fract(g);
    float seam = (1.0 - smoothstep(.003, .008 + fw, min(min(f.x, 1.0 - f.x), min(f.y, 1.0 - f.y)) * .6)) * (1.0 - saturate(fw * 8.0));
    s.albedo = vColor.rgb * (.92 + .08 * hash12(floor(g))) * (1.0 - .25 * seam);
    s.rough = .3;
}
void chainlink(inout Surface s, vec2 uv) {
    float fw = max(fwidth(uv.x), fwidth(uv.y));
    vec2 d = vec2(uv.x + uv.y, uv.x - uv.y) / .06;
    vec2 f = abs(fract(d) - .5);
    float wire = 1.0 - smoothstep(.36, .5, max(f.x, f.y) + fw * 4.0);
    float post = band(fract(uv.x / 3.0), 0.0, .02, fw) + band(fract(uv.x / 3.0), 1.0, .02, fw);
    float rail = band(uv.y, 2.15, .03, fw);
    s.alpha = saturate(max(max(1.0 - wire, post), rail) + saturate(fw * 18.0) * .45);
    s.albedo = vec3(.5, .52, .5); s.metal = .6; s.rough = .5;
}
void pool(inout Surface s, vec3 P, vec3 V) {
    float c1 = vnoise(P.xz * 1.3 + uTime * .3), c2 = vnoise(P.xz * 1.7 - uTime * .25);
    float caustic = pow(saturate(1.0 - abs(c1 - c2) * 4.0), 3.0);
    s.albedo = vec3(.02, .06, .08);
    s.emissive += vec3(.15, .55, .65) * (uSkyAmbient * 1.2 + uSunColor * .35) * (.6 + .6 * caustic);
    s.rough = .02; s.f0 = .02;
    s.normal = bump(s.normal, vec2(c1 - .5, c2 - .5) * .15);
}
void emissive(inout Surface s, float mode, float seed, float extra) {
    vec3 c = vColor.rgb;
    float on = 1.0;
    if (mode < .5) on = 1.0;
    else if (mode < 1.5) on = uNight;
    else if (mode < 2.5) {
        vec4 st = texture2D(uSignals, vec2((mod(seed, 11.0) + .5) / 11.0, (floor(seed / 11.0) + .5) / 11.0));
        float lamp = mod(extra, 8.0), ew = step(7.5, extra);
        float state = ew > .5 ? st.g : st.r;
        if (lamp < .5) on = step(.75, state);
        else if (lamp < 1.5) on = step(.25, state) * step(state, .75);
        else if (lamp < 2.5) on = step(state, .25);
        else {
            float walk = ew > .5 ? st.a : st.b;
            if (lamp < 3.5) on = step(walk, .25);
            else on = step(.75, walk) + step(.25, walk) * step(walk, .75) * step(.5, fract(uTime * 1.1));
        }
        on = mix(.05, 1.0, on);
    } else if (mode < 3.5) on = uLightGroups[int(seed + .5)];
    else if (mode < 4.5) on = step(fract(uTime * .55 + seed / 256.0), .12) * uNight;
    else on = uNight * (.85 + .15 * step(.08, fract(sin(uTime * 13.0 + seed) * 43758.5)));
    s.albedo = c * .15;
    s.emissive += c * on * (mode > 1.5 && mode < 2.5 ? 6.0 : 3.5);
    s.rough = .25;
}
void signMaterial(inout Surface s, float lit) {
    vec4 t = texture2D(uSigns, vUV);
    s.albedo = t.rgb * .85;
    s.rough = .45;
    s.emissive += t.rgb * t.a * lit * (uNight * 1.6 + .04);
}
void character(inout Surface s, float slot, vec3 P) {
    // 8 lips, 9 eye whites, 10 irises: derived from the skin tone rather than a palette slot.
    if (slot > 7.5) {
        vec3 skin = uPalette[0].rgb;
        s.albedo = slot < 8.5 ? skin * vec3(.66, .42, .42) : slot < 9.5 ? vec3(.62, .6, .57) : vec3(.05, .032, .02);
        s.rough = slot < 8.5 ? .42 : .12;
        s.sss = slot < 8.5 ? .4 : 0.0;
        return;
    }
    vec4 p = uPalette[int(slot + .5)];
    s.albedo = p.rgb;
    s.rough = p.a;
    if (slot < .5) { s.sss = .45; s.rough = .5; s.albedo *= .95 + .05 * vnoise(P.xz * 30.0); }
    else if (slot < 4.5) s.albedo *= .9 + .12 * wnoise(vUV * 90.0);
}
void carPaint(inout Surface s, float slot, vec3 P) {
    vec4 p = uPalette[int(slot + .5)];
    s.albedo = p.rgb * (1.0 - uDamage * .85);
    s.metal = .25 * (1.0 - uDamage);
    s.rough = mix(.32, .9, uDamage);
    s.clearcoat = 1.0 - uDamage;
    s.f0 = .04;
    // Road grime on the lower body.
    s.albedo = mix(s.albedo, vec3(.18, .16, .14), smoothstep(.55, .15, vUV.y) * .25);
}
