package dev.vibe.camera;

/** Render-only orbit, retargeted from the currently displayed pose when F5 is pressed again. */
public final class PerspectiveTransition {
    private int target=-1;
    private long start,duration;
    private double fromDistance,fromAngle,toDistance,toAngle,distance,angle;
    private String direction="Top",easing="Sine";
    public void reset(int view){target=view;distance=view==0?0:4;angle=view==2?Math.PI:0;duration=0;}
    public boolean update(int view,long now,double speed,String ease,String route){
        if(target<0)reset(view);
        sample(now);
        if(view!=target){
            fromDistance=distance;fromAngle=angle;toDistance=view==0?0:4;
            if(duration==0)direction=route;
            easing=ease;
            double canonical=view==2?Math.PI:0;
            double delta=Math.IEEEremainder(canonical-angle,Math.PI*2);
            if(Math.abs(Math.abs(delta)-Math.PI)<1e-6&&!direction.equals("Top")){
                delta=direction.equals("Left")?-Math.PI:Math.PI;
            }
            toAngle=angle+delta;
            start=now;duration=(long)(1e9/Math.max(.1,speed));target=view;
        }
        sample(now);return active(now);
    }
    private void sample(long now){
        if(duration==0)return;
        double t=Math.max(0,Math.min(1,(now-start)/(double)duration)),p=ease(t,easing);
        distance=fromDistance+(toDistance-fromDistance)*p;
        angle=fromAngle+(toAngle-fromAngle)*p;
        if(t>=1){distance=toDistance;angle=target==2?Math.PI:0;duration=0;}
    }
    public boolean active(long now){return duration>0&&now-start<duration;}
    public double distance(){return distance;}
    public double angle(){return angle;}
    public static double ease(double t,String mode){
        if(mode.equals("Linear"))return t;
        if(mode.equals("Cubic"))return t*t*(3-2*t);
        if(mode.equals("Quintic"))return t*t*t*(t*(t*6-15)+10);
        return .5-.5*Math.cos(Math.PI*t);
    }
    /** Offset, viewing direction and up vector in world axes; all use the real view entity's rotation. */
    public double[] frame(double yaw,double pitch){
        double y=Math.toRadians(yaw),p=Math.toRadians(pitch),c=Math.cos(angle),s=Math.sin(angle);
        double[] forward={-Math.sin(y)*Math.cos(p),-Math.sin(p),Math.cos(y)*Math.cos(p)};
        double[] up={-Math.sin(y)*Math.sin(p),Math.cos(p),Math.cos(y)*Math.sin(p)};
        double[] side={Math.cos(y),0,Math.sin(y)};
        boolean overhead=direction.equals("Top");
        // Pass just to the side of the vertical axis so an upright look-at
        // camera can follow the player over the top without a pole flip.
        double lateral=overhead?0.30*s:s;
        double vertical=overhead?s:0;
        double inverseLength=1/Math.sqrt(c*c+lateral*lateral+vertical*vertical);
        double[] result=new double[9];
        for(int i=0;i<3;i++){
            result[3+i]=(forward[i]*c-up[i]*vertical-side[i]*lateral)*inverseLength;
            result[i]=-result[3+i]*distance;
        }
        double horizontal=Math.hypot(result[3],result[5]);
        if(horizontal<1e-7){
            System.arraycopy(up,0,result,6,3);
        }else{
            result[6]=-result[3]*result[4]/horizontal;
            result[7]=horizontal;
            result[8]=-result[5]*result[4]/horizontal;
        }
        return result;
    }
}
