package dev.vibe.module.impl;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.input.Keyboard;

/** Always-on shared friend policy for all combat modules. */
public final class FriendsModule extends Module {
    private final BooleanSetting deathmatch = addSetting(new BooleanSetting("Deathmatch", false));
    public FriendsModule() { super("Friends", "Keep saved friends out of combat", Category.CLIENT, Keyboard.KEY_NONE); setEnabled(true); }
    @Override public boolean isToggleable() { return false; }

    public boolean shouldAttack(EntityPlayer player) {
        if (Vibe.getInstance().getFriendManager() == null || !Vibe.getInstance().getFriendManager().isFriend(player)) return true;
        if (!deathmatch.isEnabled() || player == null || player.worldObj == null) return false;
        // Friends become eligible only after every other alive player has
        // gone, which makes this safe for team deathmatch endgames.
        for (Object object : player.worldObj.playerEntities) {
            if (!(object instanceof EntityPlayer)) continue;
            EntityPlayer other = (EntityPlayer) object;
            if (other == player || other == net.minecraft.client.Minecraft.getMinecraft().thePlayer || other.isDead || !other.isEntityAlive()) continue;
            if (!Vibe.getInstance().getFriendManager().isFriend(other)) return false;
        }
        return true;
    }
    public BooleanSetting getDeathmatch() { return deathmatch; }
}
