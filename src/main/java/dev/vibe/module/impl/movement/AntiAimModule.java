package dev.vibe.module.impl.movement;

import dev.vibe.features.FeatureModule;
import dev.vibe.module.Category;
import dev.vibe.setting.*;
import dev.vibe.input.VanillaClicks;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;

/** Adjust's yaw/pitch generators, submitted as a fallback to Vibe's rotation service. */
public final class AntiAimModule extends FeatureModule {
    public final ModeSetting yaw=addSetting(new ModeSetting("Yaw","Backwards","Disabled","Backwards","Spin","Random"));
    public final ModeSetting pitch=addSetting(new ModeSetting("Pitch","Down","Disabled","Down","Up","Down Headless","Up Headless","Spin","Random"));
    public final NumberSetting spinSpeed=addSetting(new NumberSetting("Spin Speed",10,1,180,.5,()->yaw.is("Spin")||pitch.is("Spin")));
    public final MultiSelectSetting pauseFor = addSetting(new MultiSelectSetting("Pause For",
            Arrays.asList("NotWhileBow", "NotWhilePot"), Arrays.asList("NotWhileBow", "NotWhilePot")));
    private float spinYaw,spinPitch;
    public AntiAimModule(){super("AntiAim","Adjust yaw and pitch; other rotation modules always take priority",Category.MOVEMENT);}
    @Override protected void onEnable(){spinYaw=random(4,150);spinPitch=random(4,150);}
    public void applyFallback(){
        MoveFixModule fix=module(MoveFixModule.class);
        if(fix==null)return;
        if(!playing()||mc.thePlayer.isRiding()||FreecamModule.cameraActiveHook()||fix.hasOtherRotationOwner(getId())){fix.clearFakeRotation(getId());return;}
        if (pauseForHeldItem()) {
            fix.discardOwnedState(getId());
            return;
        }
        float y=mc.thePlayer.rotationYaw,p=mc.thePlayer.rotationPitch;
        if(yaw.is("Backwards"))y-=180;
        else if(yaw.is("Spin"))y=spinYaw=(spinYaw+spinSpeed.getFloat()+random(0,.2F))%360;
        else if(yaw.is("Random"))y=random(0,360);
        if(pitch.is("Down"))p=90;else if(pitch.is("Up"))p=-90;
        else if(pitch.is("Down Headless"))p=-180;else if(pitch.is("Up Headless"))p=180;
        else if(pitch.is("Spin")){spinPitch=(spinPitch+spinSpeed.getFloat()+random(0,.2F))%179;p=spinPitch-90;}
        else if(pitch.is("Random"))p=random(0,179.89999F)-90;
        if(yaw.is("Disabled")&&pitch.is("Disabled"))fix.clearFakeRotation(getId());
        else fix.setFallbackRotation(getId(),y,p);
    }
    private boolean pauseForHeldItem() {
        ItemStack held = mc.thePlayer.getHeldItem();
        ItemStack using = mc.thePlayer.getItemInUse();
        Item item = held == null ? null : held.getItem();
        return pauseFor.isSelected("NotWhilePot") && (item instanceof ItemPotion || using != null && using.getItem() instanceof ItemPotion)
                || pauseFor.isSelected("NotWhileBow") && item instanceof ItemBow
                && (mc.thePlayer.isUsingItem() || VanillaClicks.physicallyDown(mc.gameSettings.keyBindUseItem));
    }
    private static float random(float min,float max){return min+ThreadLocalRandom.current().nextFloat()*(max-min);}
    @Override protected void onDisable(){MoveFixModule fix=module(MoveFixModule.class);if(fix!=null)fix.clearFakeRotation(getId());}
}
