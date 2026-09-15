package dev.vibe.ui;

import java.util.*;

/** Float-based, renderer-independent stacking. Coordinates stay smooth below one GUI pixel. */
public final class EspLayout {
    private EspLayout() { }
    public static final class Rect {
        public final float x, y, w, h;
        public Rect(float x, float y, float w, float h) { this.x=x; this.y=y; this.w=w; this.h=h; }
        public float right() { return x+w; }
        public float bottom() { return y+h; }
        public boolean contains(float px, float py) { return px>=x && px<=right() && py>=y && py<=bottom(); }
        public boolean overlaps(Rect r, float gap) { return x<r.right()+gap && right()+gap>r.x && y<r.bottom()+gap && bottom()+gap>r.y; }
        public Rect expand(float n) { return new Rect(x-n,y-n,w+2*n,h+2*n); }
    }
    public static final class Request {
        public final String id, side;
        public final float w, h, order, offset, padding;
        public Request(String id, String side, float w, float h, float order, float offset, float padding) {
            this.id=id; this.side=side; this.w=w; this.h=h; this.order=order; this.offset=offset; this.padding=padding;
        }
    }
    public static float scale(float projectedHeight, float strength) {
        return (float)Math.max(.3, Math.min(1.75, Math.pow(Math.max(.01,projectedHeight)/180.0, strength)));
    }

    public static Map<String, Rect> arrange(Rect box, List<Request> input, float gap) {
        List<Request> requests = new ArrayList<Request>(input);
        Collections.sort(requests, Comparator.comparingDouble(r -> r.order));
        Map<String, Rect> result = new LinkedHashMap<String, Rect>();
        List<Rect> occupied = new ArrayList<Rect>(); occupied.add(box);
        Map<Rect,String> sides = new IdentityHashMap<Rect,String>();
        for (Request r : requests) {
            boolean left=r.side.startsWith("Left"), right=r.side.startsWith("Right");
            boolean down=r.side.endsWith("Down"), bottom=r.side.equals("Bottom");
            float x=box.x+(box.w-r.w)/2, y=box.y-r.h-gap-r.padding;
            if (bottom) y=box.bottom()+gap+r.padding;
            if (left || right) {
                x=left ? box.x-r.w-gap-r.padding : box.right()+gap+r.padding;
                y=down ? box.bottom()-r.h : box.y;
                y+=r.offset;
            } else x+=r.offset;
            Rect rect = new Rect(x,y,r.w,r.h);
            // Move outward past each collision; every step strictly advances, so even oversized tags terminate.
            for (int pass=0; pass<=occupied.size(); pass++) {
                boolean moved=false;
                for (Rect obstacle : occupied) {
                    if (!rect.expand(r.padding).overlaps(obstacle,gap-.001F)) continue;
                    if(r.side.equals(sides.get(obstacle)) && r.side.endsWith("Up")) y=Math.max(y,obstacle.bottom()+gap+r.padding);
                    else if(r.side.equals(sides.get(obstacle)) && r.side.endsWith("Down")) y=Math.min(y,obstacle.y-gap-r.padding-r.h);
                    else if (left) x=Math.min(x,obstacle.x-gap-r.padding-r.w);
                    else if (right) x=Math.max(x,obstacle.right()+gap+r.padding);
                    else if (bottom) y=Math.max(y,obstacle.bottom()+gap+r.padding);
                    else y=Math.min(y,obstacle.y-gap-r.padding-r.h);
                    rect=new Rect(x,y,r.w,r.h); moved=true;
                }
                if (!moved) break;
            }
            result.put(r.id,rect); Rect reserved=rect.expand(r.padding);occupied.add(reserved);sides.put(reserved,r.side);
        }
        return result;
    }

    public static String snap(Rect box, float x, float y, boolean fourSides) {
        float dx=Math.max(box.x-x,Math.max(0,x-box.right()));
        float dy=Math.max(box.y-y,Math.max(0,y-box.bottom()));
        double[] d={Math.hypot(x-box.x,dy),Math.hypot(x-box.right(),dy),Math.hypot(dx,y-box.y),Math.hypot(dx,y-box.bottom())};
        int best=0; for(int i=1;i<4;i++) if(d[i]<d[best]) best=i;
        if(best==2) return "Top"; if(best==3) return "Bottom";
        return (best==0?"Left":"Right")+(fourSides?"":y<box.y+box.h/2?" Up":" Down");
    }
}
