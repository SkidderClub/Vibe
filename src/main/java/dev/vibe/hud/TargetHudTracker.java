package dev.vibe.hud;

import dev.vibe.Vibe;
import dev.vibe.module.impl.combat.AimAssistModule;
import dev.vibe.module.impl.combat.KillAuraModule;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/** Display-only tracking. Never changes a module's target or sends an attack. */
public final class TargetHudTracker {
    private EntityLivingBase last;
    private long seen;
    private World world;
    public void clear() {last=null;world=null;seen=0;}
    public void expire(Minecraft mc,TargetHudSettings settings,long now) {
        if(mc.theWorld!=world||!valid(mc,last)||now-seen>settings.linger.getDouble()*1000)clear();
    }
    public void attack(EntityLivingBase entity,long now) { last=entity;seen=now;world=entity.worldObj; }
    public EntityLivingBase resolve(Minecraft mc,TargetHudSettings settings,long now) {
        if(mc.theWorld!=world) {last=null;world=mc.theWorld;}
        if(mc.thePlayer==null||mc.theWorld==null) {last=null;return null;}
        EntityLivingBase active=null;
        if(!settings.source.is("Crosshair")&&Vibe.getInstance()!=null&&Vibe.getInstance().getModuleManager()!=null) {
            KillAuraModule aura=Vibe.getInstance().getModuleManager().getModule(KillAuraModule.class);
            AimAssistModule aim=Vibe.getInstance().getModuleManager().getModule(AimAssistModule.class);
            if(aura!=null&&aura.isEnabled())active=aura.getTarget();
            if(active==null&&aim!=null&&aim.isEnabled())active=aim.getTarget();
        }
        if(!valid(mc,active))active=null;
        if(active==null&&!settings.source.is("Combat")&&mc.objectMouseOver!=null
                &&mc.objectMouseOver.entityHit instanceof EntityPlayer) active=(EntityPlayer)mc.objectMouseOver.entityHit;
        if(valid(mc,active)) {last=active;seen=now;}
        if(!valid(mc,last)||now-seen>settings.linger.getDouble()*1000) {last=null;return null;}
        return last;
    }
    private static boolean valid(Minecraft mc,EntityLivingBase target) {
        return target!=null&&target!=mc.thePlayer&&target.worldObj==mc.theWorld&&target.isEntityAlive()&&!target.isDead;
    }
}
