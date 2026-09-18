package dev.vibe.module.impl;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import org.lwjgl.input.Keyboard;

/** One ordinary held-item change immediately before an existing vanilla attack. */
public final class PickenSwitchModule extends Module {
    private final ModeSetting mode=addSetting(new ModeSetting("Mode","Basic","Basic","Silent"));
    private final ModeSetting priority=addSetting(new ModeSetting("Enchantment Priority","Both","Both","Knockback","Fire Aspect"));
    private final ModeSetting selection=addSetting(new ModeSetting("Enchanted Item","Automatic","Automatic","Hotbar Slot"));
    private final NumberSetting itemSlot=addSetting(new NumberSetting("Item Slot",9,1,9,1,()->selection.is("Hotbar Slot")));
    private final NumberSetting warmup=addSetting(new NumberSetting("Weapon Warmup (ticks)",0,0,10,1));
    private final NumberSetting cooldown=addSetting(new NumberSetting("Cooldown (ticks)",4,2,20,1));
    private final NumberSetting restoreDelay=addSetting(new NumberSetting("Restore Delay (ticks)",1,1,4,1));
    private final BooleanSetting playersOnly=addSetting(new BooleanSetting("Players Only",true));
    private final BooleanSetting respectTargets=addSetting(new BooleanSetting("Respect Targets",true));
    private final BooleanSetting skipBurning=addSetting(new BooleanSetting("Skip Fire On Burning Targets",true));
    private final BooleanSetting requireGround=addSetting(new BooleanSetting("On Ground Only",false));
    private final ColorSetting silentColor=addSetting(new ColorSetting("Silent Slot Color",0xFFFFAA55,()->mode.is("Silent")));
    private final Minecraft mc=Minecraft.getMinecraft();
    private EntityPlayer owner;
    private ItemStack stableStack;
    private int stableSlot=-1,stableTicks,tick,lastSwitch=-1000,restoreAt;
    private int originalSlot=-1,enchantedSlot=-1;
    private boolean silent;

    public PickenSwitchModule(){super("Picken Switch","Swap to hotbar enchantments during a vanilla 1.8 attack",Category.COMBAT,Keyboard.KEY_NONE);}

    /** Restoration is deferred past vanilla's movement update, never appended to the attack. */
    public void tick(){
        tick++;
        if(owner!=mc.thePlayer||mc.theWorld==null){clear();owner=mc.thePlayer;stableSlot=-1;stableStack=null;stableTicks=0;}
        if(mc.thePlayer==null||mc.playerController==null)return;
        if(enchantedSlot>=0) {
            int expected=silent?originalSlot:enchantedSlot;
            boolean manual=mc.thePlayer.inventory.currentItem!=expected;
            if(manual||!isEnabled()||mc.currentScreen!=null||tick>=restoreAt){
                int previous=originalSlot;boolean restoreVisible=!silent&&!manual;
                clear();stableTicks=0;
                if(restoreVisible)mc.thePlayer.inventory.currentItem=previous;
                mc.playerController.updateController();
            }
        }
        ItemStack stack=mc.thePlayer.getHeldItem();int slot=mc.thePlayer.inventory.currentItem;
        if(enchantedSlot<0&&stableSlot==slot&&sameWeapon(stableStack,stack))stableTicks++;
        else {stableTicks=0;stableSlot=slot;stableStack=stack;}
    }

    private int beforeAttack(Object target){
        if(!isEnabled()||enchantedSlot>=0||owner!=mc.thePlayer||mc.thePlayer==null||mc.theWorld==null||mc.playerController==null
                ||mc.currentScreen!=null||mc.thePlayer.isUsingItem()||mc.playerController.getIsHittingBlock()
                ||stableTicks<warmup.getInt()||tick-lastSwitch<cooldown.getInt()||!(target instanceof EntityLivingBase))return -1;
        EntityLivingBase victim=(EntityLivingBase)target;
        if(victim==mc.thePlayer||victim.isDead||!victim.isEntityAlive()||victim.worldObj!=mc.theWorld
                ||playersOnly.isEnabled()&&!(victim instanceof EntityPlayer)||requireGround.isEnabled()&&!mc.thePlayer.onGround)return -1;
        TargetsModule filters=Vibe.getInstance().getModuleManager().getModule(TargetsModule.class);
        if(respectTargets.isEnabled()&&filters!=null&&!filters.canTarget(victim))return -1;
        // AutoTool may own a temporary server selection. Wait for its action to settle.
        AutoToolModule auto=Vibe.getInstance().getModuleManager().getModule(AutoToolModule.class);
        if(auto!=null&&auto.hasSilentSlot())return -1;
        ScaffoldModule scaffold=Vibe.getInstance().getModuleManager().getModule(ScaffoldModule.class);
        if(scaffold!=null&&scaffold.hasSilentSlot())return -1;
        int visible=mc.thePlayer.inventory.currentItem;
        ItemStack weapon=mc.thePlayer.getHeldItem();
        if(visible!=stableSlot||!sameWeapon(weapon,stableStack)||weapon==null||!(weapon.getItem() instanceof ItemSword||weapon.getItem() instanceof ItemTool))return -1;
        int candidate=chooseSlot(mc.thePlayer.inventory.mainInventory,visible,victim.isBurning());
        if(candidate<0)return -1;
        originalSlot=visible;enchantedSlot=candidate;silent=mode.is("Silent");lastSwitch=tick;restoreAt=tick+restoreDelay.getInt();stableTicks=0;
        mc.thePlayer.inventory.currentItem=candidate;
        // attackEntity calls syncCurrentPlayItem itself before sending C02. No additional C09 or swing.
        return silent?visible:-1;
    }

    int chooseSlot(ItemStack[] hotbar,int held,boolean burning){
        if(hotbar==null||held<0||held>=Math.min(9,hotbar.length))return -1;
        ItemStack source=hotbar[held];int best=-1,bestScore=0;
        // An explicitly chosen slot is an instruction, independent of enchantment scoring.
        if(selection.is("Hotbar Slot")){
            int slot=itemSlot.getInt()-1;
            return slot!=held&&slot<hotbar.length&&hotbar[slot]!=null&&hotbar[slot].stackSize>0?slot:-1;
        }
        for(int slot=0;slot<Math.min(9,hotbar.length);slot++){
            ItemStack candidate=hotbar[slot];
            if(slot==held||candidate==null||candidate.stackSize<=0
                    ||selection.is("Hotbar Slot")&&slot!=itemSlot.getInt()-1)continue;
            int knock=Math.max(0,level(candidate,Enchantment.knockback)-level(source,Enchantment.knockback));
            int fire=burning&&skipBurning.isEnabled()?0:Math.max(0,level(candidate,Enchantment.fireAspect)-level(source,Enchantment.fireAspect));
            int score=priority.is("Knockback")?knock*100+ (knock>0?fire:0):priority.is("Fire Aspect")?fire*100+(fire>0?knock:0):knock*10+fire*10;
            if(score>bestScore){bestScore=score;best=slot;}
        }
        return best;
    }
    // Server inventory updates replace ItemStack instances and change durability on every hit.
    // Neither change means that the player selected a different weapon.
    private static boolean sameWeapon(ItemStack a,ItemStack b){return a==b||a!=null&&b!=null&&a.getItem()==b.getItem();}
    private static int level(ItemStack stack,Enchantment enchant){return stack==null?0:EnchantmentHelper.getEnchantmentLevel(enchant.effectId,stack);}
    static double baseDamage(ItemStack stack){
        double base=1,added=0,multiplier=1;
        if(stack==null)return base;
        for(AttributeModifier modifier:stack.getAttributeModifiers().get(SharedMonsterAttributes.attackDamage.getAttributeUnlocalizedName())){
            if(modifier.getOperation()==0)base+=modifier.getAmount();
            else if(modifier.getOperation()==1)added+=modifier.getAmount();
            else if(modifier.getOperation()==2)multiplier*=1+modifier.getAmount();
        }
        return base*(1+added)*multiplier;
    }
    private void clear(){originalSlot=enchantedSlot=-1;silent=false;}
    @Override protected void onEnable(){stableTicks=0;}
    // tick() runs while disabled as well, allowing the pending slot to restore at a normal tick boundary.
    private static PickenSwitchModule active(){Vibe v=Vibe.getInstance();return v==null||v.getModuleManager()==null?null:v.getModuleManager().getModule(PickenSwitchModule.class);}
    public static int beginAttackHook(Object target){PickenSwitchModule module=active();return module==null?-1:module.beforeAttack(target);}
    public static void endAttackHook(int visible){Minecraft mc=Minecraft.getMinecraft();if(visible>=0&&mc!=null&&mc.thePlayer!=null)mc.thePlayer.inventory.currentItem=visible;}
    public static int serverSlotHook(int vanilla){PickenSwitchModule m=active();return m!=null&&m.hasSilentSlot()?m.enchantedSlot:vanilla;}
    public static int beginActionHook(){PickenSwitchModule m=active();if(m==null||!m.hasSilentSlot()||m.mc.thePlayer==null)return -1;int old=m.mc.thePlayer.inventory.currentItem;m.mc.thePlayer.inventory.currentItem=m.enchantedSlot;return old;}
    public static boolean ownsSlot(){PickenSwitchModule m=active();return m!=null&&m.enchantedSlot>=0;}
    public boolean hasSilentSlot(){return silent&&enchantedSlot>=0&&owner==mc.thePlayer;}
    public int getSpoofedSlot(){return enchantedSlot;}
    public ColorSetting getSilentColor(){return silentColor;}
}
