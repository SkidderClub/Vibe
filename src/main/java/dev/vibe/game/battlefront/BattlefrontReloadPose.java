package dev.vibe.game.battlefront;

/** Continuous, timed power-cell exchange shared by arms, weapon and magazine. */
public final class BattlefrontReloadPose {
    private static final BattlefrontReloadPose IDLE=new BattlefrontReloadPose(0,0,0);
    public final double lift,cellTravel,seat;
    private BattlefrontReloadPose(double lift,double cell,double seat){this.lift=lift;cellTravel=cell;this.seat=seat;}
    public static BattlefrontReloadPose at(double progress){
        if(progress<=0||progress>=1||!Double.isFinite(progress))return IDLE;
        double lift=smooth(progress/.18)*(1-smooth((progress-.82)/.18));
        double cell=smooth((progress-.22)/.25)*(1-smooth((progress-.53)/.25));
        double seat=Math.sin(Math.PI*smooth((progress-.77)/.10))*.025;
        return new BattlefrontReloadPose(lift,cell,seat);
    }
    private static double smooth(double value){double t=Math.max(0,Math.min(1,value));return t*t*(3-2*t);}
    /** Matches the exact weapon matrix, allowing both hands to stay attached. */
    public double[] point(double x,double y,double z){
        double a=Math.toRadians(-32*lift),b=Math.toRadians(23*lift);
        double yy=y*Math.cos(b)-z*Math.sin(b),zz=y*Math.sin(b)+z*Math.cos(b);
        return new double[]{.25+x*Math.cos(a)-yy*Math.sin(a),1.19-.13*lift+x*Math.sin(a)+yy*Math.cos(a),-.37+.13*lift+seat+zz};
    }
}
