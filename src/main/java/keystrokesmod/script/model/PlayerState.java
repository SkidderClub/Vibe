package keystrokesmod.script.model;

import net.minecraft.client.entity.EntityPlayerSP;

/** Mutable local player snapshot for Raven's onPreMotion callback. */
public class PlayerState {
    public double x, y, z;
    public float yaw, pitch;
    public boolean onGround, isSprinting, isSneaking;
    public PlayerState(Object[] value) { x=(Double)value[0]; y=(Double)value[1]; z=(Double)value[2]; yaw=(Float)value[3]; pitch=(Float)value[4]; onGround=(Boolean)value[5]; isSprinting=(Boolean)value[6]; isSneaking=(Boolean)value[7]; }
    public PlayerState(double x, double y, double z, float yaw, float pitch, boolean ground, boolean sprinting, boolean sneaking) { this.x=x;this.y=y;this.z=z;this.yaw=yaw;this.pitch=pitch;onGround=ground;isSprinting=sprinting;isSneaking=sneaking; }
    public static PlayerState capture(EntityPlayerSP player) { return new PlayerState(player.posX, player.posY, player.posZ, player.rotationYaw, player.rotationPitch, player.onGround, player.isSprinting(), player.isSneaking()); }
    public void apply(EntityPlayerSP player) { player.setPosition(x,y,z); player.rotationYaw=yaw; player.rotationPitch=pitch; player.onGround=onGround; player.setSprinting(isSprinting); player.setSneaking(isSneaking); }
    public Object[] asArray() { return new Object[]{x,y,z,yaw,pitch,onGround,isSprinting,isSneaking}; }
    public boolean equals(PlayerState value) { return value != null && x==value.x && y==value.y && z==value.z && yaw==value.yaw && pitch==value.pitch && onGround==value.onGround && isSprinting==value.isSprinting && isSneaking==value.isSneaking; }
}
