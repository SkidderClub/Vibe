package dev.vibe.hud;

/** Edge-triggered Tab visibility, scoped to the current world and game focus. */
final class TabKeyState {
    private Object world;
    private boolean down,toggled,mode;
    boolean update(boolean pressed,boolean toggle,boolean allowed,Object currentWorld){
        if(!allowed||world!=currentWorld){down=toggled=false;world=currentWorld;if(!allowed)return false;}
        if(mode!=toggle){toggled=false;mode=toggle;}
        if(toggle&&pressed&&!down)toggled=!toggled;
        down=pressed;return toggle?toggled:pressed;
    }
}
