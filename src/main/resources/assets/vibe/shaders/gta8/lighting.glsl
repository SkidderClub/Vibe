// Shadows, BRDF, dynamic lights and the city's analytic street lighting.
uniform sampler2DShadow uShadowMap;
uniform mat4 uShadowMat0;
uniform mat4 uShadowMat1;
uniform vec4 uShadowParams;   // x: cascade 0 range, y: cascade 1 range, z/w: world texel sizes
uniform float uShadowOn;
uniform float uShadowSoft;
uniform vec2 uShadowTexel;
uniform vec4 uLightPos[16];   // xyz, w = radius
uniform vec4 uLightColor[16]; // rgb, w = spot outer cosine (<= -1 for point lights)
uniform vec4 uLightDir[16];   // spot direction, w = inner cosine
uniform int uLightCount;
uniform float uNsHalf[11];
uniform float uEwHalf[11];
uniform vec3 uStreetColor;
uniform vec3 uIndoorLight;

struct Surface {
    vec3 albedo; vec3 normal; float rough; float metal; vec3 emissive;
    float ao; float alpha; float f0; float clearcoat; float sss; float indoor;
};

float shadowTap(vec3 c) { return shadow2D(uShadowMap, c).r; }
float shadowFactor(vec3 P, vec3 N, float dist) {
    if (uShadowOn < .5 || dist > uShadowParams.y) return 1.0;
    bool near = dist < uShadowParams.x;
    float texel = near ? uShadowParams.z : uShadowParams.w;
    float slope = saturate(1.0 - abs(dot(N, uSunDir)));
    vec3 p = P + N * texel * (1.2 + 2.0 * slope);
    vec4 sc = near ? uShadowMat0 * vec4(p, 1.0) : uShadowMat1 * vec4(p, 1.0);
    float lo = near ? .0 : .5;
    float bias = .0004 + (near ? .0 : .0006);
    vec2 texelUv = uShadowTexel;
    float radius = (near ? 1.6 : 1.1) * uShadowSoft;
    float angle = hash12(gl_FragCoord.xy) * 6.2832;
    mat2 rot = mat2(cos(angle), sin(angle), -sin(angle), cos(angle));
    float sum = 0.0;
    vec2 taps[8];
    taps[0] = vec2(-.613, .617); taps[1] = vec2(.170, -.040); taps[2] = vec2(-.299, -.791); taps[3] = vec2(.645, .493);
    taps[4] = vec2(-.651, -.109); taps[5] = vec2(.421, -.742); taps[6] = vec2(-.034, .961); taps[7] = vec2(.920, -.221);
    for (int i = 0; i < 8; i++) {
        vec2 uv = sc.xy + rot * taps[i] * texelUv * radius * 2.0;
        uv.x = clamp(uv.x, lo + .0005, lo + .4995);
        sum += shadowTap(vec3(uv, sc.z - bias));
    }
    float s = sum / 8.0;
    float fade = near ? 0.0 : smoothstep(uShadowParams.y * .82, uShadowParams.y, dist);
    return mix(s, 1.0, fade);
}

vec3 brdf(vec3 N, vec3 V, vec3 L, Surface s, vec3 F0, vec3 diffuse, float wrap) {
    float NdotL = saturate(dot(N, L));
    float lit = s.sss > 0.0 ? saturate((dot(N, L) + s.sss) / (1.0 + s.sss)) : NdotL;
    if (lit <= 0.0) return vec3(0.0);
    vec3 H = normalize(L + V);
    float NdotV = max(dot(N, V), 1e-3), NdotH = saturate(dot(N, H)), VdotH = saturate(dot(V, H));
    float a = max(s.rough * s.rough, .0025), a2 = a * a;
    float d = NdotH * NdotH * (a2 - 1.0) + 1.0;
    float D = a2 / (PI * d * d);
    float k = a * .5;
    float G = NdotL / (NdotL * (1.0 - k) + k) * NdotV / (NdotV * (1.0 - k) + k);
    vec3 F = F0 + (1.0 - F0) * pow(1.0 - VdotH, 5.0);
    vec3 spec = D * G * F / (4.0 * NdotV * max(NdotL, 1e-3)) * PI;
    vec3 result = diffuse * lit + spec * NdotL;
    if (s.clearcoat > 0.0) {
        float ac = .03 * .03, dc = NdotH * NdotH * (ac - 1.0) + 1.0;
        float Fc = .04 + .96 * pow(1.0 - VdotH, 5.0);
        result += vec3(ac / (PI * dc * dc) * Fc * .25 * PI * s.clearcoat) * NdotL;
    }
    return result;
}

vec3 pointLights(vec3 P, vec3 N, vec3 V, Surface s, vec3 F0, vec3 diffuse) {
    vec3 sum = vec3(0.0);
    for (int i = 0; i < 16; i++) {
        if (i >= uLightCount) break;
        vec3 d = uLightPos[i].xyz - P;
        float dist2 = dot(d, d), r = uLightPos[i].w;
        if (dist2 > r * r) continue;
        vec3 L = d * inversesqrt(dist2);
        float window = saturate(1.0 - dist2 * dist2 / (r * r * r * r));
        float att = window * window / (dist2 + 1.0);
        if (uLightColor[i].w > -1.5) {
            float c = dot(-L, uLightDir[i].xyz);
            att *= smoothstep(uLightColor[i].w, uLightDir[i].w, c);
        }
        if (att <= 0.0) continue;
        sum += brdf(N, V, L, s, F0, diffuse, 0.0) * uLightColor[i].rgb * att;
    }
    return sum;
}

vec3 streetLamp(vec3 lamp, vec3 P, vec3 N, vec3 V, Surface s, vec3 F0, vec3 diffuse) {
    vec3 d = lamp - P;
    float dist2 = dot(d, d);
    if (dist2 > 900.0) return vec3(0.0);
    vec3 L = d * inversesqrt(dist2);
    float window = saturate(1.0 - dist2 * dist2 / 810000.0);
    // Cobra-head optics throw light downwards and along the road.
    float cone = pow(saturate(L.y), .6);
    return brdf(N, V, L, s, F0, diffuse, 0.0) * (window * window * cone / (dist2 + 4.0));
}
vec3 streetLights(vec3 P, vec3 N, vec3 V, Surface s, vec3 F0, vec3 diffuse) {
    if (uStreetColor.r <= 0.0 || abs(P.x) > 530.0 || abs(P.z) > 530.0 || P.y > 40.0) return vec3(0.0);
    vec3 sum = vec3(0.0);
    // North-south roads: lamp rows on both kerbs, three per block edge (Gta8CityGen.lampFractions: near both corners and mid-block).
    float fi = clamp(floor((P.x + 480.0) / 96.0 + .5), 0.0, 10.0);
    float lineX = -480.0 + 96.0 * fi;
    float offX = uNsHalf[int(fi)] - 1.25;
    float bz = clamp(floor((P.z + 480.0) / 96.0), 0.0, 9.0);
    for (int k = -1; k <= 1; k++) {
        float b = bz + float(k);
        if (b < 0.0 || b > 9.0) continue;
        float a0 = -480.0 + 96.0 * b + uEwHalf[int(b)] + 4.5;
        float a1 = -480.0 + 96.0 * (b + 1.0) - uEwHalf[int(b) + 1] - 4.5;
        for (int f = 0; f < 3; f++) {
            float z = a0 + (a1 - a0) * (f == 0 ? .03 : f == 1 ? .5 : .97);
            if (abs(z - P.z) > 30.0) continue;
            sum += streetLamp(vec3(lineX - offX, 8.25, z), P, N, V, s, F0, diffuse);
            sum += streetLamp(vec3(lineX + offX, 8.25, z), P, N, V, s, F0, diffuse);
        }
    }
    float fj = clamp(floor((P.z + 480.0) / 96.0 + .5), 0.0, 10.0);
    float lineZ = -480.0 + 96.0 * fj;
    float offZ = uEwHalf[int(fj)] - 1.25;
    float bx = clamp(floor((P.x + 480.0) / 96.0), 0.0, 9.0);
    for (int k = -1; k <= 1; k++) {
        float b = bx + float(k);
        if (b < 0.0 || b > 9.0) continue;
        float a0 = -480.0 + 96.0 * b + uNsHalf[int(b)] + 4.5;
        float a1 = -480.0 + 96.0 * (b + 1.0) - uNsHalf[int(b) + 1] - 4.5;
        for (int f = 0; f < 3; f++) {
            float x = a0 + (a1 - a0) * (f == 0 ? .03 : f == 1 ? .5 : .97);
            if (abs(x - P.x) > 30.0) continue;
            sum += streetLamp(vec3(x, 8.25, lineZ - offZ), P, N, V, s, F0, diffuse);
            sum += streetLamp(vec3(x, 8.25, lineZ + offZ), P, N, V, s, F0, diffuse);
        }
    }
    return sum * uStreetColor;
}

vec3 shade(Surface s, vec3 P, vec3 V, float dist) {
    vec3 N = s.normal;
    float NdotV = max(dot(N, V), 1e-3);
    vec3 F0 = mix(vec3(s.f0), s.albedo, s.metal);
    vec3 diffuse = s.albedo * (1.0 - s.metal);
    vec3 color = vec3(0.0);
    if (s.indoor < .5) {
        float sh = shadowFactor(P, N, dist);
        if (sh > 0.0) color += brdf(N, V, uSunDir, s, F0, diffuse, 0.0) * uSunColor * sh;
        vec3 amb = mix(uGroundAmbient, uSkyAmbient, N.y * .5 + .5);
        color += diffuse * amb * s.ao;
        color += diffuse * uLightning * .6 * saturate(N.y * .5 + .5);
    } else {
        color += diffuse * uIndoorLight * s.ao;
    }
    vec3 R = reflect(-V, N);
    vec3 Fr = F0 + (max(vec3(1.0 - s.rough), F0) - F0) * pow(1.0 - NdotV, 5.0);
    // High facades see open sky; street level sees the skyline.
    float city = saturate(1.0 - (P.y - 4.0) / 70.0);
    vec3 env = s.indoor > .5 ? uIndoorLight * .5 : environment(R, s.rough, city);
    float specOcc = saturate(s.ao * 1.2) * saturate(1.0 + R.y * 3.0 * (1.0 - N.y));
    color += env * Fr * specOcc * (s.indoor > .5 ? .35 : 1.0);
    if (s.clearcoat > 0.0) color += environment(R, .02, city) * (.04 + .96 * pow(1.0 - NdotV, 5.0)) * s.clearcoat * specOcc * (1.0 - s.indoor * .7);
    color += pointLights(P, N, V, s, F0, diffuse);
    if (s.indoor < .5) color += streetLights(P, N, V, s, F0, diffuse);
    return color + s.emissive;
}
