package dev.vibe.game.gta8;

/**
 * CPU side of the sky model: sun and moon positions for Los Vibes (34 degrees north), the scattering
 * coefficients passed to the shaders and hemisphere ambient light sampled from the same sky function.
 */
final class Gta8Atmosphere {
    private static final double LATITUDE = Math.toRadians(34), DECLINATION = Math.toRadians(20);
    /** Converts the Preetham model's radiance scale to the renderer's (sun irradiance / pi) units. */
    static final double SKY_SCALE = .075, SKY_SATURATION = .8;
    private static final double[] RAYLEIGH = {5.804542996261093E-6, 1.3562911419845635E-5, 3.0265902468824876E-5};
    private static final double[] MIE = {1.8399918514433978E14, 2.7798023919660528E14, 4.0790479543861094E14};
    final double[] sunDir = new double[3], moonDir = new double[3], lightDir = new double[3];
    final double[] betaR = new double[3], betaM = new double[3];
    final double[] sunColor = new double[3], lightColor = new double[3], skyAmbient = new double[3], groundAmbient = new double[3];
    double sunE, moonE, night, exposure = 1, cloud, fog;
    boolean moonIsKey;

    void update(double hours, Gta8Weather weather) {
        position(hours, sunDir);
        // A bright moon roughly opposite the sun, a little higher in the sky.
        double[] m = new double[3];
        position((hours + 12.4) % 24, m);
        moonDir[0] = m[0]; moonDir[1] = m[1] * .9 + .12; moonDir[2] = m[2] + .15;
        normalize(moonDir);
        cloud = weather == null ? 0 : weather.cloud;
        double rain = weather == null ? 0 : weather.rain;
        fog = weather == null ? .0012 : weather.fog;
        double turbidity = 2.6 + cloud * 5 + rain * 2, rayleigh = 1.25 + cloud * .5;
        for (int i = 0; i < 3; i++) {
            betaR[i] = RAYLEIGH[i] * rayleigh;
            betaM[i] = .434 * (.2 * turbidity * 10E-18) * MIE[i] * .0055;
        }
        sunE = intensity(sunDir[1]);
        moonE = intensity(moonDir[1]) * .0065;
        double[] ext = extinction(sunDir);
        double dim = 1 - .88 * cloud * cloud;
        for (int i = 0; i < 3; i++) sunColor[i] = Math.pow(ext[i], .6) * sunE / 1000 * 2.0 * dim;
        // Moonlight takes over as the shadow-casting key light at night.
        double[] mext = extinction(moonDir);
        double moonK = intensity(moonDir[1]) / 1000 * .055 * (1 - .8 * cloud);
        double sunK = sunE / 1000;
        moonIsKey = sunK < .02 && moonDir[1] > .02;
        double[] key = moonIsKey ? moonDir : sunDir;
        System.arraycopy(key, 0, lightDir, 0, 3);
        if (moonIsKey) {
            lightColor[0] = moonK * .62 * mext[0]; lightColor[1] = moonK * .78 * mext[1]; lightColor[2] = moonK * 1.15 * mext[2];
        } else System.arraycopy(sunColor, 0, lightColor, 0, 3);
        // Hemisphere ambient from the analytic sky.
        double[] acc = new double[3];
        double weight = 0;
        for (int i = 0; i < 9; i++) {
            double el = i == 0 ? Math.PI / 2 : Math.toRadians(i <= 4 ? 45 : 12);
            double az = i * Math.PI / 2 + (i > 4 ? Math.PI / 4 : 0);
            double[] d = {Math.cos(el) * Math.sin(az), Math.sin(el), -Math.cos(el) * Math.cos(az)};
            double[] c = sky(d);
            double w = Math.sin(el) + .2;
            for (int k = 0; k < 3; k++) acc[k] += c[k] * w;
            weight += w;
        }
        double grey = 0;
        for (int k = 0; k < 3; k++) { skyAmbient[k] = acc[k] / weight; grey += skyAmbient[k] / 3; }
        // Multiple scattering and ground bounce make real sky light far less saturated than the zenith.
        // Multiple bounces between buildings brighten shade beyond the single-scattering sky alone.
        for (int k = 0; k < 3; k++) skyAmbient[k] = Gta8Math.lerp(skyAmbient[k], grey * 1.05, .4 + cloud * .45) * 1.35 + (k == 2 ? .0026 : k == 1 ? .0016 : .0011);
        double groundAlbedo = .24;
        double sunUp = Math.max(0, sunDir[1]);
        for (int k = 0; k < 3; k++) groundAmbient[k] = groundAlbedo * (sunColor[k] * sunUp * .9 + skyAmbient[k]) + (k == 0 ? .0012 : .0008);
        night = Gta8Math.smooth(.06, -.08, sunDir[1]);
        night = Math.max(night, Gta8Math.smooth(.6, 1, cloud) * .55 * Gta8Math.smooth(.4, .1, sunDir[1]) + Gta8Math.smooth(.7, 1, cloud) * .25);
        double brightness = luma(skyAmbient) + luma(lightColor) * Math.max(0, lightDir[1]) * .9;
        exposure = Gta8Math.clamp(.85 / Math.pow(brightness + .01, .62), .65, 4.2);
    }

    /** Solar position for the given local solar time. */
    static void position(double hours, double[] out) {
        // Clock time runs on daylight saving: solar noon is at 13:00.
        double h = Math.toRadians((hours - 13) * 15);
        double sinEl = Math.sin(LATITUDE) * Math.sin(DECLINATION) + Math.cos(LATITUDE) * Math.cos(DECLINATION) * Math.cos(h);
        double el = Math.asin(Gta8Math.clamp(sinEl, -1, 1));
        double az = Math.atan2(-Math.sin(h), Math.tan(DECLINATION) * Math.cos(LATITUDE) - Math.sin(LATITUDE) * Math.cos(h));
        out[0] = Math.cos(el) * Math.sin(az);
        out[1] = Math.sin(el);
        out[2] = -Math.cos(el) * Math.cos(az);
    }
    static double intensity(double cosZenith) {
        return 1000 * Math.max(0, 1 - Math.exp(-((1.6110731556870734 - Math.acos(Gta8Math.clamp(cosZenith, -1, 1))) / 1.5)));
    }
    double[] extinction(double[] dir) {
        double zen = Math.acos(Math.max(0, dir[1]));
        double inv = 1 / (Math.cos(zen) + .15 * Math.pow(93.885 - Math.toDegrees(zen), -1.253));
        double[] out = new double[3];
        for (int i = 0; i < 3; i++) out[i] = Math.exp(-(betaR[i] * 8.4e3 * inv + betaM[i] * 1.25e3 * inv));
        return out;
    }
    /** Port of the shader's scatter(): linear radiance of the clear sky in a direction. */
    double[] sky(double[] dir) {
        double[] result = scatter(dir, sunDir, sunE);
        double[] moon = scatter(dir, moonDir, moonE);
        result[0] = (result[0] + moon[0] * .55) * SKY_SCALE; result[1] = (result[1] + moon[1] * .7) * SKY_SCALE; result[2] = (result[2] + moon[2] * 1.25) * SKY_SCALE;
        double l = luma(result);
        for (int i = 0; i < 3; i++) result[i] = l + (result[i] - l) * SKY_SATURATION;
        result[0] += .0022 * .35; result[1] += .0036 * .35; result[2] += .0075 * .35;
        return result;
    }
    private double[] scatter(double[] dir, double[] sun, double e) {
        double[] fex = extinction(dir);
        double c = dir[0] * sun[0] + dir[1] * sun[1] + dir[2] * sun[2];
        double rPhase = 3 / (16 * Math.PI) * (1 + Math.pow(c * .5 + .5, 2));
        double g = .76, mPhase = (1 / (4 * Math.PI)) * ((1 - g * g) / Math.pow(1 - 2 * g * c + g * g, 1.5));
        double horizon = Gta8Math.clamp(Math.pow(1 - sun[1], 5), 0, 1);
        double[] out = new double[3];
        for (int i = 0; i < 3; i++) {
            double ratio = (betaR[i] * rPhase + betaM[i] * mPhase) / (betaR[i] + betaM[i]);
            double lin = Math.pow(e * ratio * (1 - fex[i]), 1.5);
            lin *= Gta8Math.lerp(1, Math.sqrt(e * ratio * fex[i]), horizon);
            out[i] = lin * .04;
        }
        return out;
    }
    private static void normalize(double[] v) { double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]); for (int i = 0; i < 3; i++) v[i] /= l; }
    static double luma(double[] c) { return c[0] * .2126 + c[1] * .7152 + c[2] * .0722; }
}
