package dev.vibe.module.impl.combat;

import dev.vibe.Vibe;
import dev.vibe.module.impl.client.FriendsModule;
import dev.vibe.module.impl.world.RavenBlockAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;

public final class RavenCombatAccess {
   public static Vec3 closestPointOnAabb(AxisAlignedBB box, Vec3 point) {
      double x = Math.max(box.minX, Math.min(box.maxX, point.xCoord));
      double y = Math.max(box.minY, Math.min(box.maxY, point.yCoord));
      double z = Math.max(box.minZ, Math.min(box.maxZ, point.zCoord));
      return new Vec3(x, y, z);
   }
   private static Minecraft mc() { return Minecraft.getMinecraft(); }
   private RavenCombatAccess() {
   }

   public static EntityPlayer findTarget(double maxDistanceSq) {
      return findTarget(maxDistanceSq, true);
   }

   public static EntityPlayer findTarget(double maxDistanceSq, boolean ignoreTeammates) {
      EntityPlayer mouseOverTarget = getMouseOverTarget(maxDistanceSq, ignoreTeammates);
      return mouseOverTarget != null ? mouseOverTarget : findClosestTarget(maxDistanceSq, ignoreTeammates);
   }

   public static EntityPlayer findClosestTarget(double maxDistanceSq) {
      return findClosestTarget(maxDistanceSq, true);
   }

   public static EntityPlayer findClosestTarget(double maxDistanceSq, boolean ignoreTeammates) {
      if (mc() != null && mc().theWorld != null) {
         EntityPlayer closest = null;
         double closestDistanceSq = Double.MAX_VALUE;

         for (EntityPlayer player : mc().theWorld.playerEntities) {
            if (isValidPlayer(player, maxDistanceSq, ignoreTeammates)) {
               double distanceSq = distanceSqFromEyeToClosestOnAABB(player);
               if (distanceSq < closestDistanceSq) {
                  closestDistanceSq = distanceSq;
                  closest = player;
               }
            }
         }

         return closest;
      } else {
         return null;
      }
   }

   public static EntityPlayer getMouseOverTarget(double maxDistanceSq) {
      return getMouseOverTarget(maxDistanceSq, true);
   }

   public static EntityPlayer getMouseOverTarget(double maxDistanceSq, boolean ignoreTeammates) {
      if (mc() != null && mc().objectMouseOver != null) {
         MovingObjectPosition objectMouseOver = mc().objectMouseOver;
         return asValidPlayer(objectMouseOver.entityHit, maxDistanceSq, ignoreTeammates);
      } else {
         return null;
      }
   }

   public static EntityPlayer asValidPlayer(Entity entity, double maxDistanceSq) {
      return asValidPlayer(entity, maxDistanceSq, true);
   }

   public static EntityPlayer asValidPlayer(Entity entity, double maxDistanceSq, boolean ignoreTeammates) {
      if (!(entity instanceof EntityPlayer)) {
         return null;
      } else {
         EntityPlayer player = (EntityPlayer)entity;
         return isValidPlayer(player, maxDistanceSq, ignoreTeammates) ? player : null;
      }
   }

   public static boolean isValidPlayer(EntityPlayer player, double maxDistanceSq) {
      return isValidPlayer(player, maxDistanceSq, true);
   }

   public static boolean isValidPlayer(EntityPlayer player, double maxDistanceSq, boolean ignoreTeammates) {
      return isTrackablePlayer(player, ignoreTeammates) && isWithinRange(player, maxDistanceSq);
   }

   public static boolean isTrackablePlayer(EntityPlayer player) {
      return isTrackablePlayer(player, true);
   }

   public static boolean isTrackablePlayer(EntityPlayer player, boolean ignoreTeammates) {
      if (!RavenBlockAccess.nullCheck() || player == null || player == mc().thePlayer || player.isDead || player.deathTime != 0) {
         return false;
      } else {
         return isFriended(player) ? false : !ignoreTeammates || !isTeammate(player);
      }
   }

   public static boolean isWithinRange(EntityPlayer player, double maxDistanceSq) {
      return player == null ? false : distanceSqFromEyeToClosestOnAABB(player) <= maxDistanceSq;
   }

   private static boolean isFriended(EntityPlayer player) {
      FriendsModule friends = Vibe.getInstance() == null || Vibe.getInstance().getModuleManager() == null ? null
         : Vibe.getInstance().getModuleManager().getModule(FriendsModule.class);
      return friends != null && !friends.shouldAttack(player);
   }
   public static boolean isTeammate(Entity entity) {
      try {
         if (mc().thePlayer.isOnSameTeam((EntityLivingBase)entity)
            || mc().thePlayer.getDisplayName().getUnformattedText().startsWith(entity.getDisplayName().getUnformattedText().substring(0, 2))
            || getNetworkDisplayName().startsWith(entity.getDisplayName().getUnformattedText().substring(0, 2))) {
            return true;
         }
      } catch (Exception var2) {
      }

      return false;
   }
   public static String getNetworkDisplayName() {
      try {
         NetworkPlayerInfo playerInfo = mc().getNetHandler().getPlayerInfo(mc().thePlayer.getUniqueID());
         return ScorePlayerTeam.formatPlayerName(playerInfo.getPlayerTeam(), playerInfo.getGameProfile().getName());
      } catch (Exception var1) {
         return "";
      }
   }
   public static double distanceSqFromEyeToClosestOnAABB(Entity entity) {
      if (entity != null && mc().thePlayer != null) {
         Vec3 eye = mc().thePlayer.getPositionEyes(1.0F);
         float borderSize = entity.getCollisionBorderSize();
         AxisAlignedBB bb = entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
         Vec3 closest = closestPointOnAabb(bb, eye);
         double dx = eye.xCoord - closest.xCoord;
         double dy = eye.yCoord - closest.yCoord;
         double dz = eye.zCoord - closest.zCoord;
         return dx * dx + dy * dy + dz * dz;
      } else {
         return Double.MAX_VALUE;
      }
   }
   public static double distanceSqFromEyeToClosestOnAABB(Entity entity, Vec3 position) {
      if (entity != null && position != null && mc().thePlayer != null) {
         Vec3 eye = mc().thePlayer.getPositionEyes(1.0F);
         float borderSize = entity.getCollisionBorderSize();
         double offsetX = position.xCoord - entity.posX;
         double offsetY = position.yCoord - entity.posY;
         double offsetZ = position.zCoord - entity.posZ;
         AxisAlignedBB bb = entity.getEntityBoundingBox().offset(offsetX, offsetY, offsetZ).expand(borderSize, borderSize, borderSize);
         Vec3 closest = closestPointOnAabb(bb, eye);
         double dx = eye.xCoord - closest.xCoord;
         double dy = eye.yCoord - closest.yCoord;
         double dz = eye.zCoord - closest.zCoord;
         return dx * dx + dy * dy + dz * dz;
      } else {
         return Double.MAX_VALUE;
      }
   }
}
