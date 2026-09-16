package dev.vibe.game.battlefront;

/** Camera-space projection; normalized screen coordinates, with upward-positive Y. */
public final class BattlefrontProjection {
    private BattlefrontProjection(){}
    public static double[] project(BattlefrontGame game,double x,double y,double z,double aspect){
        BattlefrontGame.Camera c=game.camera(game.aiming);
        double dx=x-c.x,dy=y-c.y,dz=z-c.z,depth=dx*c.dx+dy*c.dy+dz*c.dz;
        if(depth<=.1)return null;
        double yaw=Math.toRadians(game.yaw),pitch=Math.toRadians(game.pitch),tan=Math.tan(Math.toRadians(game.fieldOfView(game.aiming)/2));
        double right=dx*Math.cos(yaw)+dz*Math.sin(yaw),up=dx*Math.sin(yaw)*Math.sin(pitch)+dy*Math.cos(pitch)-dz*Math.cos(yaw)*Math.sin(pitch);
        return new double[]{right/(depth*tan*aspect),up/(depth*tan),depth};
    }
}
