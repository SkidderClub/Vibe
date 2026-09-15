package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.impl.QolModule;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

/** Opt into vanilla's translucent model branch without changing entity flags or effects. */
public final class InvisibleModelHooks {
    private InvisibleModelHooks() { }
    private static QolModule enabled(Object entity) {
        Vibe vibe = Vibe.getInstance();
        if (!(entity instanceof EntityLivingBase) || !((EntityLivingBase) entity).isInvisible()
                || vibe == null || vibe.getModuleManager() == null) return null;
        QolModule qol = vibe.getModuleManager().getModule(QolModule.class);
        return qol != null && qol.isEnabled() && qol.getFeatures().isSelected("AntiInvisibility") ? qol : null;
    }
    public static boolean isInvisibleToPlayer(Object entity, Object viewer) {
        return !ChamsRenderer.appliesTo(entity) && enabled(entity) == null
                && ((EntityLivingBase) entity).isInvisibleToPlayer((EntityPlayer) viewer);
    }
    public static float alpha(float vanilla, Object entity) {
        QolModule qol = enabled(entity);
        return qol == null ? vanilla : qol.getInvisibleAlpha().getFloat() / 255.0F;
    }
}
