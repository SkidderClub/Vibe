package dev.vibe.movement;

/** Unit-length orbit direction and the closest native keyboard pair at any server yaw. */
public final class OrbitSteering {
    private OrbitSteering(){}
    public static double[] direction(double x,double z,double radius,int side,float fallbackYaw){double distance=Math.hypot(x,z);if(distance<.001){double angle=Math.toRadians(fallbackYaw);x=-Math.sin(angle);z=Math.cos(angle);distance=1;}
        double radial=Math.max(-1,Math.min(1,(radius-distance)*1.5)),rx=x/distance,rz=z/distance,dx=-rz*side+rx*radial,dz=rx*side+rz*radial,len=Math.hypot(dx,dz);return new double[]{dx/len,dz/len};}
    public static float[] keys(double dx,double dz,float yaw){double sin=Math.sin(Math.toRadians(yaw)),cos=Math.cos(Math.toRadians(yaw)),best=-Double.MAX_VALUE;float f=0,s=0;
        for(int forward=-1;forward<=1;forward++)for(int strafe=-1;strafe<=1;strafe++){if(forward==0&&strafe==0)continue;double len=Math.hypot(forward,strafe),score=((-sin*forward+cos*strafe)*dx+(cos*forward+sin*strafe)*dz)/len;if(score>best){best=score;f=forward;s=strafe;}}
        return new float[]{f,s};}
}
