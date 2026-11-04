package dev.vibe.extension;

import net.minecraft.entity.player.EntityPlayer;

/** Optional cape layer supplied by an extension. */
public interface PlayerCapeProvider {
    boolean replacesVanillaCape(EntityPlayer player);
}
