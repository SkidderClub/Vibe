package dev.vibe.module.impl.world;
import dev.vibe.features.*;
import dev.vibe.inventory.*;
import dev.vibe.module.Category;
import dev.vibe.setting.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.projectile.EntityFishHook;
public final class AutoFishModule extends FeatureModule {
    public final NumberSetting delay=addSetting(new NumberSetting("Delay (ms)",400,0,2500,25)),reserve=addSetting(new NumberSetting("Stop At Durability",5,0,64,1)),timeout=addSetting(new NumberSetting("Cast Timeout (s)",45,5,90,1));
    public final ItemSwitching switching=new ItemSwitching(this::addSetting,"Basic");
    private HotbarSwitches.Lease lease;private Object world;private EntityFishHook hook;private long actionAt,castAt;private int wetTicks;private boolean reel,awaitingHook;
    public AutoFishModule(){super("AutoFish","Reel on observed bites and recast with a protected fishing rod",Category.WORLD);}
    public String getStatus(){if(!isEnabled())return "Off";if(!controlling()||busy())return "Paused";if(reel)return "Reeling in";if(hook!=null)return "Waiting for a bite";if(awaitingHook)return "Waiting for hook";for(int i=0;i<9;i++){ItemStack s=mc.thePlayer.inventory.mainInventory[i];if(s!=null&&s.getItem()==Items.fishing_rod&&s.getMaxDamage()-s.getItemDamage()>reserve.getInt())return "Ready";}return "No usable rod";}
    @Override public String getDescription(){return super.getDescription()+(isEnabled()?" · "+getStatus():"");}
    private boolean busy(){if(!playing())return true;dev.vibe.module.impl.combat.KillAuraModule aura=module(dev.vibe.module.impl.combat.KillAuraModule.class);dev.vibe.module.impl.combat.AIAuraModule ai=module(dev.vibe.module.impl.combat.AIAuraModule.class);
        return mc.thePlayer.isUsingItem()||mc.playerController.getIsHittingBlock()||PlayerInventorySession.isBusy()||HotbarSwitches.busy(this,HotbarSwitches.TOOL)||dev.vibe.module.impl.combat.PearlCatcherModule.reserving()||dev.vibe.module.impl.combat.ProjectileAuraModule.reserving()||dev.vibe.module.impl.combat.AutoBuffModule.ownsUseInput()||dev.vibe.module.impl.combat.AntiFireballModule.reserving()||dev.vibe.module.impl.combat.BowAimbotModule.drawing()||aura!=null&&aura.isEnabled()&&aura.getTarget()!=null||ai!=null&&ai.isEnabled()&&ai.getTarget()!=null;
    }
    @Override public void applyRotation(){if(!controlling()||busy())release();}
    @Override public void tick(){if(!controlling()||mc.theWorld!=world||busy()||dev.vibe.input.VanillaClicks.physicallyDown(mc.gameSettings.keyBindUseItem)||dev.vibe.input.VanillaClicks.physicallyDown(mc.gameSettings.keyBindAttack)){release();world=mc.theWorld;actionAt=(System.nanoTime()/1000000)+delay.getInt();return;}
        if(lease!=null&&!lease.valid()){release();return;}net.minecraft.entity.projectile.EntityFishHook observed=mc.thePlayer.fishEntity;if(observed!=null&&observed!=hook){castAt=(System.nanoTime()/1000000);wetTicks=0;}hook=observed;
        if(hook!=null){awaitingHook=false;if(hook.isInWater())wetTicks++;else wetTicks=0;if((wetTicks>8&&hook.motionY<-.035)||(System.nanoTime()/1000000)-castAt>timeout.getInt()*1000L){if(!reel){reel=true;actionAt=(System.nanoTime()/1000000)+delay.getInt();}}}
        else{wetTicks=0;if(reel){reel=false;actionAt=(System.nanoTime()/1000000)+delay.getInt();}}
    }
    @Override public void afterWalking(){if(!controlling()||busy()){release();return;}if((System.nanoTime()/1000000)<actionAt)return;
        if(hook!=null&&!reel||hook==null&&awaitingHook&&(System.nanoTime()/1000000)-castAt<3000)return;int slot=-1;for(int i=0;i<9;i++){ItemStack s=mc.thePlayer.inventory.mainInventory[i];if(s!=null&&s.getItem()==Items.fishing_rod&&s.getMaxDamage()-s.getItemDamage()>reserve.getInt()){slot=i;break;}}
        if(slot<0){release();return;}if(lease==null)lease=HotbarSwitches.acquire(this,switching,slot,HotbarSwitches.TOOL-1);if(lease==null||!lease.active())return;
        mc.playerController.sendUseItem(mc.thePlayer,mc.theWorld,HotbarSwitches.stack());if(hook!=null){release();reel=false;}else{castAt=(System.nanoTime()/1000000);awaitingHook=true;}actionAt=(System.nanoTime()/1000000)+delay.getInt();
    }
    private void release(){if(lease!=null){lease.close();lease=null;}hook=null;wetTicks=0;reel=false;awaitingHook=false;}
    @Override protected void onDisable(){release();world=null;}
}
