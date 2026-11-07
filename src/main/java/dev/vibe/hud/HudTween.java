package dev.vibe.hud;

/** Retargets from the current value, so rapidly changing counts/views never jump. */
final class HudTween {
    private float from,value,target;
    private long started;
    private boolean initialized;
    float update(float requested,long now,float duration,String easing){
        if(!initialized){snap(requested);initialized=true;}
        if(requested!=target){from=value;target=requested;started=now;}
        float t=duration<=0?1:Math.max(0,Math.min(1,(now-started)/duration));
        if("Smoothstep".equals(easing))t=t*t*(3-2*t);
        else if("Cubic".equals(easing))t=1-(1-t)*(1-t)*(1-t);
        value=from+(target-from)*t;return value;
    }
    void snap(float v){from=value=target=v;initialized=true;}
    float value(){return value;}
}
