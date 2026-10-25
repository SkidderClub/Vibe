package dev.vibe.ui;

/** Stable thumb grab offset, track clicks and clamping shared by settings scrollbars. */
public final class ScrollbarDrag {
    private final int top,travel,max,grab;
    public ScrollbarDrag(int top,int bottom,int scroll,int max,int minimumThumb,int pointer) {
        this.top=top;this.max=Math.max(0,max);
        int height=Math.max(0,bottom-top),thumb=thumb(height,max,minimumThumb);
        travel=Math.max(0,height-thumb);
        int thumbTop=top+(this.max==0?0:Math.round(travel*Math.max(0,Math.min(this.max,scroll))/(float)this.max));
        grab=pointer>=thumbTop&&pointer<thumbTop+thumb?pointer-thumbTop:thumb/2;
    }
    public int at(int pointer) {return travel==0?0:Math.max(0,Math.min(max,Math.round((pointer-top-grab)*max/(float)travel)));}
    public static int thumb(int height,int max,int minimum) {return Math.min(height,Math.max(minimum,Math.round(height*(float)height/Math.max(1,height+max))));}
}
