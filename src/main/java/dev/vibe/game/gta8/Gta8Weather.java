package dev.vibe.game.gta8;

import java.util.Random;

/**
 * Day/night clock and weather fronts. Clouds build up before rain, roads stay wet after it,
 * and thunderstorms schedule lightning with a delayed thunderclap.
 */
public final class Gta8Weather {
    public enum Type {
        CLEAR("Clear", 0, 0, 0), SUNNY_HAZE("Hazy", .15, 0, .0007), CLOUDY("Cloudy", .55, 0, .0004), OVERCAST("Overcast", .85, 0, .0009),
        RAIN("Rain", .9, .65, .0016), THUNDER("Thunderstorm", 1, 1, .0022), FOG("Fog", .7, 0, .0105), DRIZZLE("Drizzle", .8, .25, .0012);
        public final String title; public final double cloud, rain, fog;
        Type(String title, double cloud, double rain, double fog) { this.title = title; this.cloud = cloud; this.rain = rain; this.fog = fog; }
    }
    /** In-game minutes per real second: 48 real minutes per day. */
    public static final double MINUTES_PER_SECOND = 0.5;
    public double hours = 9.5;
    public Type type = Type.CLEAR, next = Type.CLEAR;
    public double cloud, rain, wet, fog = .00035, lightning, windX = .8, windZ = .3;
    public double thunderIn = -1, thunderVolume;
    private double hold = 60 * 8, blend = 1;
    private final Random random = new Random(20260927L);

    public void advance(double seconds) {
        hours = (hours + seconds * MINUTES_PER_SECOND / 60.0) % 24.0;
        hold -= seconds * MINUTES_PER_SECOND;
        if (hold <= 0) {
            type = next;
            next = pickNext(type);
            hold = 60 * (3 + random.nextDouble() * 7);
            blend = 0;
        }
        blend = Math.min(1, blend + seconds / 90.0);
        Type target = blend < 1 ? type : type;
        double k = 1 - Math.exp(-seconds / 25.0);
        cloud += (target.cloud - cloud) * k;
        // Rain only starts once the sky is dark enough.
        double wantRain = cloud > target.cloud * .8 ? target.rain : 0;
        rain += (wantRain - rain) * (1 - Math.exp(-seconds / 12.0));
        double morningMist = hours > 4.5 && hours < 8.5 ? .0012 * Math.sin((hours - 4.5) / 4 * Math.PI) : 0;
        fog += (target.fog + morningMist + .00042 - fog) * k;
        if (rain > .05) wet = Math.min(1, wet + seconds * rain / 25.0);
        else wet = Math.max(0, wet - seconds / (cloud > .6 ? 420.0 : 160.0));
        lightning = Math.max(0, lightning - seconds * 5);
        if (type == Type.THUNDER && rain > .6 && random.nextDouble() < seconds / 9.0) {
            lightning = .7 + random.nextDouble() * .6;
            thunderIn = .4 + random.nextDouble() * 3.5;
            thunderVolume = 1 - thunderIn / 5;
        }
        if (thunderIn >= 0) thunderIn -= seconds;
        windX = Math.cos(hours * .7) * (.6 + rain * 2.4);
        windZ = Math.sin(hours * .5) * (.4 + rain * 1.6);
    }
    private Type pickNext(Type current) {
        double r = random.nextDouble();
        switch (current) {
            case CLEAR: return r < .55 ? Type.CLEAR : r < .75 ? Type.SUNNY_HAZE : Type.CLOUDY;
            case SUNNY_HAZE: return r < .5 ? Type.CLEAR : Type.CLOUDY;
            case CLOUDY: return r < .35 ? Type.CLEAR : r < .65 ? Type.OVERCAST : r < .8 ? Type.DRIZZLE : Type.FOG;
            case OVERCAST: return r < .4 ? Type.RAIN : r < .6 ? Type.THUNDER : r < .8 ? Type.DRIZZLE : Type.CLOUDY;
            case RAIN: return r < .5 ? Type.OVERCAST : r < .7 ? Type.THUNDER : Type.CLOUDY;
            case THUNDER: return r < .6 ? Type.RAIN : Type.OVERCAST;
            case FOG: return r < .6 ? Type.CLOUDY : Type.CLEAR;
            default: return r < .5 ? Type.CLOUDY : Type.RAIN;
        }
    }
    /** Immediately switch weather, e.g. from the pause menu or tests. */
    public void set(Type t) {
        type = next = t; hold = 60 * 8; cloud = t.cloud; rain = t.rain; fog = t.fog + .00042; wet = t.rain > 0 ? 1 : 0;
    }
    public String clock() {
        int minutes = (int) (hours * 60) % (24 * 60);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }
    /** Road friction: wet asphalt grips roughly 70% as well as dry. */
    public double grip() { return 1 - .3 * wet; }
}
