package dev.vibe.aiaura;

import java.util.Arrays;

/** Identical physical/context features in the recorder and Minecraft adapter. Velocities are blocks/tick. */
public final class AuraFeatures {
    public static final int WIDTH=40,HISTORY=4,INPUTS=WIDTH*HISTORY,OUTPUTS=10;
    public static final class Observation {
        public double eyeX,eyeY,eyeZ,yaw,pitch,vx,vy,vz,reach=3,targetX,targetY,targetZ,targetVx,targetVy,targetVz,targetWidth=.6,targetHeight=1.8,border=.1;
        public boolean visible,grounded,sprint,leftHeld,jump;public float sensitivity=.5F;public long targetId=-1;
        public double targetHurt,selfHurt,targetHealth=1,selfHealth=1,targetYaw;public boolean targetGrounded;
    }
    public static final class Memory {
        public double pointX=.5,pointY=.6,pointZ=.5,lastYaw,lastPitch,yawAcceleration,pitchAcceleration,lastDistance;
        public long target=-1,acquired,lastClick=Long.MIN_VALUE/2,lastHit=Long.MIN_VALUE/2;private double previousHurt;
        public final float[] history=new float[INPUTS];
        public void reset(){target=-1;lastYaw=lastPitch=yawAcceleration=pitchAcceleration=lastDistance=previousHurt=0;lastClick=lastHit=Long.MIN_VALUE/2;pointX=pointZ=.5;pointY=.6;Arrays.fill(history,0);}
        public void motion(double yaw,double pitch){yawAcceleration=yaw-lastYaw;pitchAcceleration=pitch-lastPitch;lastYaw=yaw;lastPitch=pitch;}
        public float[] push(float[] current){System.arraycopy(history,0,history,WIDTH,INPUTS-WIDTH);System.arraycopy(current,0,history,0,WIDTH);return history.clone();}
    }
    public static float[] build(Observation o,Memory m,long now){
        boolean changed=m.target!=o.targetId;if(changed){m.target=o.targetId;m.acquired=now;m.pointX=m.pointZ=.5;m.pointY=.6;m.lastDistance=distance(o);m.lastHit=Long.MIN_VALUE/2;m.previousHurt=0;}
        float[] f=new float[WIDTH];double distance=distance(o),px=o.targetX+(m.pointX-.5)*o.targetWidth,py=o.targetY+m.pointY*o.targetHeight,pz=o.targetZ+(m.pointZ-.5)*o.targetWidth;
        double[] angle=angles(o.eyeX,o.eyeY,o.eyeZ,px,py,pz),old=angles(o.eyeX,o.eyeY,o.eyeZ,px-o.targetVx,py-o.targetVy,pz-o.targetVz);
        double yaw=Math.toRadians(o.yaw),ratio=distance/Math.max(.1,o.reach);
        f[0]=clip(MouseMath.wrap(angle[0]-o.yaw)/180,-1,1);f[1]=clip((angle[1]-o.pitch)/90,-2,2);f[2]=clip(distance/9,0,3);
        f[3]=clip(Math.toDegrees(2*Math.atan2(o.targetWidth*.5,Math.max(.01,distance)))/90,0,2);f[4]=clip(Math.toDegrees(2*Math.atan2(o.targetHeight*.5,Math.max(.01,distance)))/90,0,2);
        f[5]=clip(MouseMath.wrap(angle[0]-old[0])/45,-4,4);f[6]=clip((angle[1]-old[1])/45,-4,4);
        f[7]=clip((o.vx*Math.cos(yaw)+o.vz*Math.sin(yaw))/.5,-4,4);f[8]=clip((-o.vx*Math.sin(yaw)+o.vz*Math.cos(yaw))/.5,-4,4);f[9]=clip(o.vy/.42,-4,4);f[10]=clip((o.targetY-o.eyeY)/5,-4,4);
        f[11]=clip((now-m.acquired)/1500D,0,4);f[12]=clip((now-m.lastClick)/1000D,0,4);f[13]=clip(m.lastYaw/128,-8,8);f[14]=clip(m.lastPitch/128,-8,8);f[15]=clip(m.yawAcceleration/256,-8,8);f[16]=clip(m.pitchAcceleration/256,-8,8);
        f[17]=o.grounded?1:0;f[18]=o.sprint?1:0;f[19]=o.visible?1:0;f[20]=MouseMath.step(o.sensitivity)/.15F;f[21]=clip((m.lastDistance-distance)/.5,-4,4);f[22]=o.leftHeld?1:0;f[23]=o.jump?1:0;
        f[24]=ratio<.7?1:0;f[25]=ratio>=.7&&ratio<=1?1:0;f[26]=ratio>1?1:0;f[27]=(float)m.pointX;f[28]=(float)m.pointY;f[29]=(float)m.pointZ;f[30]=clip(Math.hypot(o.targetVx,o.targetVz)/.3,0,4);f[31]=changed?1:0;m.lastDistance=distance;
        if(o.targetHurt>m.previousHurt)m.lastHit=now;m.previousHurt=o.targetHurt;
        f[32]=clip(o.targetHurt/10,0,2);f[33]=clip(o.selfHurt/10,0,2);f[34]=clip(o.targetHealth,0,1);f[35]=clip(o.selfHealth,0,1);f[36]=o.targetGrounded?1:0;f[37]=clip((now-m.lastHit)/1000D,0,4);f[38]=now-m.lastHit<150?1:0;
        f[39]=(float)Math.cos(Math.toRadians(o.targetYaw-angles(o.targetX,o.targetY,o.targetZ,o.eyeX,o.eyeY,o.eyeZ)[0]));
        if(!o.visible){f[0]=f[1]=f[3]=f[4]=f[5]=f[6]=f[10]=f[21]=f[24]=f[25]=f[26]=f[30]=0;}
        return f;
    }
    public static double[] angles(double ex,double ey,double ez,double x,double y,double z){double dx=x-ex,dy=y-ey,dz=z-ez;return new double[]{Math.toDegrees(Math.atan2(dz,dx))-90,-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)))};}
    public static double distance(Observation o){double dx=Math.max(0,Math.abs(o.eyeX-o.targetX)-o.targetWidth*.5-o.border),dz=Math.max(0,Math.abs(o.eyeZ-o.targetZ)-o.targetWidth*.5-o.border),dy=Math.max(0,Math.max(o.targetY-o.border-o.eyeY,o.eyeY-o.targetY-o.targetHeight-o.border));return Math.sqrt(dx*dx+dy*dy+dz*dz);}
    public static boolean inView(Observation o,float fov,double aspect){
        double yaw=Math.toRadians(o.yaw),pitch=Math.toRadians(o.pitch),sy=Math.sin(yaw),cy=Math.cos(yaw),sp=Math.sin(pitch),cp=Math.cos(pitch);
        double[] forward={-sy*cp,-sp,cy*cp},right={cy,0,sy},up={-sy*sp,cp,cy*sp},delta={o.targetX-o.eyeX,o.targetY+o.targetHeight*.5-o.eyeY,o.targetZ-o.eyeZ};
        double half=o.targetWidth*.5+o.border,vertical=o.targetHeight*.5+o.border,tan=Math.tan(Math.toRadians(Math.min(170,fov+8)*.5));
        if(outside(delta,forward,half,vertical))return false;
        for(int sign:new int[]{-1,1}){double[] side=new double[3],top=new double[3];for(int i=0;i<3;i++){side[i]=forward[i]*tan*aspect+right[i]*sign;top[i]=forward[i]*tan+up[i]*sign;}if(outside(delta,side,half,vertical)||outside(delta,top,half,vertical))return false;}
        return true;
    }
    private static boolean outside(double[] delta,double[] plane,double half,double vertical){return delta[0]*plane[0]+delta[1]*plane[1]+delta[2]*plane[2]+half*(Math.abs(plane[0])+Math.abs(plane[2]))+vertical*Math.abs(plane[1])<0;}
    public static int bucket(float[] f){int reach=f[24]>0?0:f[25]>0?1:2;double angle=Math.hypot(f[0]*180,f[1]*90);return reach*3+(angle>12?0:angle>2?1:2);}
    public static float clip(double value,double min,double max){return (float)Math.max(min,Math.min(max,Double.isFinite(value)?value:0));}
}
