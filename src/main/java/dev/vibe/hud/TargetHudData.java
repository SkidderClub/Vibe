package dev.vibe.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import java.util.UUID;

/** The same data contract is used by live and editor renders. */
public final class TargetHudData {
    public final EntityLivingBase entity;
    public final String name;
    public final float health,maxHealth,absorption,ownHealth,angle;
    public final int hurtTicks,ping;
    public final UUID id;
    public final ResourceLocation skin;
    public TargetHudData(EntityLivingBase entity,String name,float health,float maxHealth,float absorption,
                         float ownHealth,int hurtTicks,float angle,int ping,UUID id,ResourceLocation skin) {
        this.entity=entity;this.name=name;this.health=Math.max(0,health);this.maxHealth=Math.max(1,maxHealth);
        this.absorption=Math.max(0,absorption);this.ownHealth=ownHealth;this.hurtTicks=Math.max(0,hurtTicks);
        this.angle=angle;this.ping=ping;this.id=id;this.skin=skin;
    }
    public static TargetHudData from(Minecraft mc,EntityLivingBase target) {
        float angle=(target.rotationYaw-mc.thePlayer.rotationYaw)%360;
        if(angle<0)angle+=360;
        NetworkPlayerInfo info=mc.getNetHandler()==null?null:mc.getNetHandler().getPlayerInfo(target.getUniqueID());
        return new TargetHudData(target,EnumChatFormatting.getTextWithoutFormattingCodes(target.getName()),
                target.getHealth(),target.getMaxHealth(),target.getAbsorptionAmount(),
                mc.thePlayer.getHealth()+mc.thePlayer.getAbsorptionAmount(),target.hurtTime,angle,
                info==null?-1:Math.max(0,info.getResponseTime()),target.getUniqueID(),
                target instanceof AbstractClientPlayer?((AbstractClientPlayer)target).getLocationSkin():DefaultPlayerSkin.getDefaultSkin(target.getUniqueID()));
    }
    public static TargetHudData sample() {
        UUID id=UUID.nameUUIDFromBytes("Vibe HUD preview".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new TargetHudData(null,"Alex",13.2F,20,0,16,4,354,28,id,DefaultPlayerSkin.getDefaultSkin(id));
    }
    public float total(boolean absorptionEnabled) { return health+(absorptionEnabled?absorption:0); }
    public float fraction() { return Math.max(0,Math.min(1,health/maxHealth)); }
    public String verdict(boolean absorptionEnabled) {
        float difference=ownHealth-total(absorptionEnabled);
        return Math.abs(difference)<.05F?"Draw":difference>0?"Winning":"Losing";
    }
}
