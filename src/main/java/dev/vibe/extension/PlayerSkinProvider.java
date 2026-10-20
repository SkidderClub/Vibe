package dev.vibe.extension;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

/** Optional local skin replacement. Return null to retain the player's normal skin. */
public interface PlayerSkinProvider {
    ResourceLocation skin(EntityPlayer player);
}
