package dev.vibe.module.impl.combat;

import dev.vibe.features.FeatureModule;
import dev.vibe.module.Category;
import dev.vibe.setting.BooleanSetting;
import net.minecraft.entity.player.EntityPlayer;

/** Controls only the two successful native attack slowdown/reset sites. */
public final class KeepSprintModule extends FeatureModule {
    public final BooleanSetting removeResetSprint=addSetting(new BooleanSetting("Remove reset sprint",true));
    public final BooleanSetting removeSlowDown=addSetting(new BooleanSetting("Remove slow down",true));
    // Exire's "Only in air" guard actually returns in air; describe its real behavior.
    public final BooleanSetting onlyOnGround=addSetting(new BooleanSetting("Only on ground",false));
    public KeepSprintModule(){super("KeepSprint","Keep sprint and horizontal speed when a native hit lands",Category.COMBAT);}
    private boolean applies(Object entity){return isEnabled()&&entity==mc.thePlayer&&(!onlyOnGround.isEnabled()||mc.thePlayer.onGround);}
    public static double slowdown(double original,Object entity){KeepSprintModule m=module(KeepSprintModule.class);return m!=null&&m.applies(entity)&&m.removeSlowDown.isEnabled()?1:original;}
    public static void resetSprint(Object entity,boolean value){
        KeepSprintModule m=module(KeepSprintModule.class);
        if(value||m==null||!m.applies(entity)||!m.removeResetSprint.isEnabled())((EntityPlayer)entity).setSprinting(value);
    }
}
