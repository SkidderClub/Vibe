package dev.vibe.camera;

/** Render-only orbit, retargeted from the currently displayed pose when F5 is pressed again. */
public final class PerspectiveTransition {
    private int target=-1;
    private long start,duration;
    private double fromDistance,fromAngle,toDistance,toAngle,distance,angle,bump;
    private String direction="Top",easing="Sine";
    public void reset(int view){target=view;distance=view==0?0:4;angle=view==2?Math.PI:0;duration=0;}
    public boolean update(int view,long now,double speed,String ease,String route){
        if(target<0)reset(view);
        sample(now);
        if(view!=target){
            fromDistance=distance;fromAngle=angle;toDistance=view==0?0:4;
            direction=route;easing=ease;double canonical=view==2?Math.PI:0;
            if(route.equals("Top"))toAngle=canonical;
            else{
                double delta=(canonical-angle)%(Math.PI*2);
                if(route.equals("Left")){if(delta>1e-6)delta-=Math.PI*2;}
                else if(delta< -1e-6)delta+=Math.PI*2;
                toAngle=angle+delta;
            }
            bump=Math.abs(toAngle-fromAngle)<1e-6&&Math.abs(toDistance-fromDistance)>.01?Math.PI*.5:0;
            start=now;duration=(long)(1e9/Math.max(.1,speed));target=view;
        }
        sample(now);return active(now);
    }
    private void sample(long now){
        if(duration==0)return;
        double t=Math.max(0,Math.min(1,(now-start)/(double)duration)),p=ease(t,easing);
        distance=fromDistance+(toDistance-fromDistance)*p;
        angle=fromAngle+(toAngle-fromAngle)*p+bump*Math.sin(Math.PI*p)*(direction.equals("Left")?-1:1);
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
        double[] side=direction.equals("Top")?up:new double[]{Math.cos(y),0,Math.sin(y)};
        double[] result=new double[9],tangent=new double[3];
        for(int i=0;i<3;i++){result[i]=(-forward[i]*c+side[i]*s)*distance;result[3+i]=forward[i]*c-side[i]*s;tangent[i]=forward[i]*s+up[i]*c;}
        if(direction.equals("Top")){
            // A continuous roll through the pole keeps the front view upright,
            // instead of snapping yaw by 180 degrees directly above the head.
            double[] cross={result[4]*tangent[2]-result[5]*tangent[1],result[5]*tangent[0]-result[3]*tangent[2],result[3]*tangent[1]-result[4]*tangent[0]};
            for(int i=0;i<3;i++)result[6+i]=tangent[i]*c+cross[i]*s;
        }else for(int i=0;i<3;i++)result[6+i]=up[i];
        return result;
    }
}
