package dev.vibe.hud;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/** Display-only history of outgoing player attacks. Looking at or aiming at someone is not an attack. */
public final class TargetHudTracker {
    private final LinkedHashMap<EntityPlayer,Long> attacked = new LinkedHashMap<EntityPlayer,Long>();
    private World world;
    public void clear() { attacked.clear(); world=null; }
    public void expire(Minecraft mc,TargetHudSettings settings,long now) {
        if(mc.thePlayer==null||mc.theWorld==null||mc.theWorld!=world) { clear();world=mc.theWorld;return; }
        Iterator<Map.Entry<EntityPlayer,Long>> entries=attacked.entrySet().iterator();
        while(entries.hasNext()) {
            Map.Entry<EntityPlayer,Long> entry=entries.next();
            if(!valid(mc,entry.getKey())||now-entry.getValue()>Math.max(50,settings.linger.getDouble()*1000))entries.remove();
        }
    }
    public void attack(EntityLivingBase entity,long now) {
        if(!(entity instanceof EntityPlayer)||entity.worldObj==null||entity.isDead||!entity.isEntityAlive())return;
        if(world!=entity.worldObj) {clear();world=entity.worldObj;}
        EntityPlayer player=(EntityPlayer)entity;
        attacked.remove(player);attacked.put(player,now);
        while(attacked.size()>16)attacked.remove(attacked.keySet().iterator().next());
    }
    public List<EntityPlayer> resolveAll(Minecraft mc,TargetHudSettings settings,long now) {
        expire(mc,settings,now);
        List<EntityPlayer> result=new ArrayList<EntityPlayer>(attacked.keySet());
        Collections.reverse(result);
        return result.size()<=settings.maxTargets.getInt()?result:new ArrayList<EntityPlayer>(result.subList(0,settings.maxTargets.getInt()));
    }
    public EntityLivingBase resolve(Minecraft mc,TargetHudSettings settings,long now) {
        List<EntityPlayer> targets=resolveAll(mc,settings,now);
        return targets.isEmpty()?null:targets.get(0);
    }
    private static boolean valid(Minecraft mc,EntityPlayer target) {
        return target!=mc.thePlayer&&target.worldObj==mc.theWorld&&target.isEntityAlive()&&!target.isDead
                &&mc.theWorld.playerEntities.contains(target);
    }
}
