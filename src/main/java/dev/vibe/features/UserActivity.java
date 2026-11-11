package dev.vibe.features;

/** Physical activity only: synthetic movement from automation must not keep an AFK emote awake. */
public final class UserActivity {
    private static long last=System.nanoTime();
    private static long version;
    private UserActivity(){}
    public static void mark(){last=System.nanoTime();version++;}
    public static long version(){return version;}
    public static double idleSeconds(){return Math.max(0,(System.nanoTime()-last)/1E9);}
}
