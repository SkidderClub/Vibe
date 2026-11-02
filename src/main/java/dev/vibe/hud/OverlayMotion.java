package dev.vibe.hud;

/** Reversible smoothstep transition, timed independently of frame rate. */
final class OverlayMotion {
    private float from,value,target;
    private long started;
    float update(boolean shown){return update(shown,System.nanoTime());}
    float update(boolean shown,long now){return update(shown?1F:0F,now);}
    float update(float requested){return update(requested,System.nanoTime());}
    private float update(float requested,long now){
        if(requested!=target){from=value;target=requested;started=now;}
        float t=Math.max(0,Math.min(1,(now-started)/180000000F));
        value=from+(target-from)*t*t*(3-2*t);return value;
    }
}
