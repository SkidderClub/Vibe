package dev.vibe.extension;

import dev.vibe.model.CompiledModel;
import net.minecraft.entity.player.EntityPlayer;

/** Supplies a PlayerFitter model to the existing custom character renderer. */
public interface PlayerModelProvider {
    boolean appliesTo(EntityPlayer player);
    /** Return null until asynchronous loading finishes. */
    CompiledModel model();
    default boolean hideArmor() { return true; }
}
