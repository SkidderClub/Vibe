package dev.vibe.module.impl.combat;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.module.impl.world.AutoToolModule;
import dev.vibe.setting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;

/**
 * Cache the damage weapon between hits, select utility enchantments for C02,
 * and return through vanilla's C09 writer before leaving the controller call.
 */
public final class PickenSwitchModule extends Module {
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Basic", "Basic", "Silent"));
    private final ModeSetting priority = addSetting(new ModeSetting("Enchantment Priority", "Both", "Both", "Knockback", "Fire Aspect"));
    private final ModeSetting selection = addSetting(new ModeSetting("Enchanted Item", "Automatic", "Automatic", "Hotbar Slot"));
    private final NumberSetting itemSlot = addSetting(new NumberSetting("Item Slot", 9, 1, 9, 1, () -> selection.is("Hotbar Slot")));
    private final BooleanSetting autoWeapon = addSetting(new BooleanSetting("Auto Equip Damage Weapon", true));
    private final NumberSetting warmup = addSetting(new NumberSetting("Weapon Warmup (ticks)", 2, 1, 10, 1));
    private final NumberSetting cooldown = addSetting(new NumberSetting("Cooldown (ticks)", 0, 0, 20, 1));
    private final BooleanSetting playersOnly = addSetting(new BooleanSetting("Players Only", true));
    private final BooleanSetting respectTargets = addSetting(new BooleanSetting("Respect Targets", true));
    private final BooleanSetting skipBurning = addSetting(new BooleanSetting("Skip Fire On Burning Targets", true));
    private final BooleanSetting requireGround = addSetting(new BooleanSetting("On Ground Only", false));
    private final ColorSetting silentColor = addSetting(new ColorSetting("Silent Slot Color", 0xFFFFAA55, () -> mode.is("Silent")));
    private final Minecraft mc = Minecraft.getMinecraft();
    private EntityPlayer owner;
    private World ownerWorld;
    private ItemStack stableStack;
    private int stableSlot = -1, stableTicks, tick, lastSwitch = -1000;
    private int originalSlot = -1, enchantedSlot = -1;

    public PickenSwitchModule() {
        super("Picken Switch", "Combine cached weapon damage with an enchanted hotbar item's effects", Category.COMBAT, Keyboard.KEY_NONE);
    }

    /** Keep the damage weapon equipped long enough for the server's equipment update. */
    public void tick() {
        tick++;
        if (owner != mc.thePlayer || ownerWorld != mc.theWorld) {
            clear(); owner = mc.thePlayer; ownerWorld=mc.theWorld; stableSlot = -1; stableStack = null; stableTicks = 0;
        }
        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null) return;
        if (enchantedSlot >= 0) finishAttack();
        if(isEnabled()&&autoWeapon.isEnabled()&&canUseSlots())prepareDamageWeapon();
        ItemStack stack = mc.thePlayer.getHeldItem();
        int slot = mc.thePlayer.inventory.currentItem;
        if (stableSlot == slot && sameWeapon(stableStack, stack)) stableTicks++;
        else { stableTicks = 0; stableSlot = slot; stableStack = stack==null?null:stack.copy(); }
    }

    private boolean canUseSlots(){
        if(mc.currentScreen!=null||mc.thePlayer.isUsingItem()||mc.playerController.getIsHittingBlock()
                ||AutoBuffModule.ownsUseInput()||AutoToolModule.hasHotbarAction()||dev.vibe.module.impl.meme.HypixelModule.ownsSilentSlot())return false;
        int visible=mc.thePlayer.inventory.currentItem;
        return AutoToolModule.serverSlotHook(visible)==visible;
    }

    private void prepareDamageWeapon(){
        ItemStack held=mc.thePlayer.getHeldItem();
        if(held==null||(level(held,Enchantment.knockback)==0&&level(held,Enchantment.fireAspect)==0))return;
        int best=damageWeapon(mc.thePlayer.inventory.mainInventory,mc.thePlayer.inventory.currentItem);
        if(best!=mc.thePlayer.inventory.currentItem){mc.thePlayer.inventory.currentItem=best;mc.playerController.updateController();}
    }

    static int damageWeapon(ItemStack[] hotbar,int held){
        if(hotbar==null||held<0||held>=Math.min(9,hotbar.length))return held;
        int best=held;double damage=baseDamage(hotbar[held]);
        for(int i=0;i<Math.min(9,hotbar.length);i++){
            ItemStack stack=hotbar[i];if(stack==null||stack.stackSize<=0||!(stack.getItem() instanceof ItemSword||stack.getItem() instanceof ItemTool))continue;
            double next=baseDamage(stack);if(next>damage){damage=next;best=i;}
        }
        return best;
    }

    /** Called by the bytecode hook directly before vanilla syncCurrentPlayItem. */
    private int beforeAttack(Object target) {
        if (!isEnabled() || enchantedSlot >= 0 || owner != mc.thePlayer || ownerWorld != mc.theWorld || mc.thePlayer == null || mc.theWorld == null || mc.playerController == null
                || !canUseSlots()
                || stableTicks < warmup.getInt() || tick - lastSwitch < cooldown.getInt() || !(target instanceof EntityLivingBase)) return -1;
        EntityLivingBase victim = (EntityLivingBase) target;
        if (victim == mc.thePlayer || victim.isDead || !victim.isEntityAlive() || victim.worldObj != mc.theWorld
                || playersOnly.isEnabled() && !(victim instanceof EntityPlayer) || requireGround.isEnabled() && !mc.thePlayer.onGround) return -1;
        TargetsModule filters = Vibe.getInstance().getModuleManager().getModule(TargetsModule.class);
        if (respectTargets.isEnabled() && filters != null && !filters.canTarget(victim)) return -1;
        int visible = mc.thePlayer.inventory.currentItem;
        ItemStack weapon = mc.thePlayer.getHeldItem();
        if (visible != stableSlot || !sameWeapon(weapon, stableStack) || weapon == null
                || !(weapon.getItem() instanceof ItemSword || weapon.getItem() instanceof ItemTool)) return -1;
        int candidate = chooseSlot(mc.thePlayer.inventory.mainInventory, visible, victim.isBurning());
        if (candidate < 0) return -1;
        originalSlot = visible; enchantedSlot = candidate;
        lastSwitch = tick;
        // This is intentionally immediately before syncCurrentPlayItem, not at click time.
        if(!mode.is("Silent"))mc.thePlayer.inventory.currentItem = candidate;
        return visible;
    }

    int chooseSlot(ItemStack[] hotbar, int held, boolean burning) {
        if (hotbar == null || held < 0 || held >= Math.min(9, hotbar.length)) return -1;
        ItemStack source = hotbar[held];
        if (source == null) return -1;
        if (selection.is("Hotbar Slot")) {
            int slot = itemSlot.getInt() - 1;
            return slot != held && slot < hotbar.length && hotbar[slot] != null && hotbar[slot].stackSize > 0 ? slot : -1;
        }
        int best = -1, bestScore = 0;
        for (int slot = 0; slot < Math.min(9, hotbar.length); slot++) {
            ItemStack candidate = hotbar[slot];
            if (slot == held || candidate == null || candidate.stackSize <= 0) continue;
            int knock = Math.max(0, level(candidate, Enchantment.knockback) - level(source, Enchantment.knockback));
            int fire = burning && skipBurning.isEnabled() ? 0 : Math.max(0, level(candidate, Enchantment.fireAspect) - level(source, Enchantment.fireAspect));
            int score = priority.is("Knockback") ? knock * 100 + (knock > 0 ? fire : 0)
                    : priority.is("Fire Aspect") ? fire * 100 + (fire > 0 ? knock : 0) : knock * 10 + fire * 10;
            if (score > bestScore) { bestScore = score; best = slot; }
        }
        return best;
    }

    private static boolean sameWeapon(ItemStack first, ItemStack second) { return ItemStack.areItemStacksEqual(first,second); }
    private static int level(ItemStack stack, Enchantment enchantment) { return stack == null ? 0 : EnchantmentHelper.getEnchantmentLevel(enchantment.effectId, stack); }
    static double baseDamage(ItemStack stack) {
        double base = 1, additive = 0, multiplier = 1;
        if (stack == null) return base;
        for (AttributeModifier modifier : stack.getAttributeModifiers().get(SharedMonsterAttributes.attackDamage.getAttributeUnlocalizedName())) {
            if (modifier.getOperation() == 0) base += modifier.getAmount();
            else if (modifier.getOperation() == 1) additive += modifier.getAmount();
            else if (modifier.getOperation() == 2) multiplier *= 1 + modifier.getAmount();
        }
        return base * (1 + additive) * multiplier;
    }
    private void clear() { originalSlot = enchantedSlot = -1; }
    @Override protected void onEnable() { stableTicks = 0; }
    @Override protected void onDisable() { finishAttack();stableTicks=0; }

    private void finishAttack(){
        if(originalSlot<0)return;
        int weapon=originalSlot,utility=enchantedSlot;
        boolean sameContext=owner==mc.thePlayer&&ownerWorld==mc.theWorld&&mc.thePlayer!=null&&mc.theWorld!=null;
        clear(); // Release the resolver before syncing, otherwise it sends the utility slot again.
        if(!sameContext)return;
        int current=mc.thePlayer.inventory.currentItem;
        if(current==utility||current==weapon)mc.thePlayer.inventory.currentItem=weapon;
        if(mc.playerController!=null)mc.playerController.updateController();
    }
    private static PickenSwitchModule active() { Vibe vibe = Vibe.getInstance(); return vibe == null || vibe.getModuleManager() == null ? null : vibe.getModuleManager().getModule(PickenSwitchModule.class); }
    public static int beginAttackHook(Object target) { PickenSwitchModule module = active(); return module == null || dev.vibe.module.impl.meme.HypixelModule.ownsSilentSlot() ? -1 : module.beforeAttack(target); }
    /** The wrapper invokes this only after vanilla has sent and processed its attack path. */
    public static void endAttackHook(int ignored) {
        PickenSwitchModule module = active();
        if (module != null) module.finishAttack();
    }
    public static void abortAttackHook() {
        PickenSwitchModule module = active();
        if (module != null) module.finishAttack();
    }
    public static int serverSlotHook(int vanilla) { PickenSwitchModule module = active(); return module != null && module.hasSilentSlot() ? module.enchantedSlot : vanilla; }
    public static int beginActionHook() {
        PickenSwitchModule module = active();
        if (module == null || !module.hasSilentSlot() || module.mc.thePlayer == null) return -1;
        int old = module.mc.thePlayer.inventory.currentItem;
        module.mc.thePlayer.inventory.currentItem = module.enchantedSlot;
        return old;
    }
    public static boolean ownsSlot() { PickenSwitchModule module = active(); return module != null && module.enchantedSlot >= 0; }
    /** The override exists only inside this controller attack, never between ticks. */
    public boolean hasSilentSlot() { return enchantedSlot >= 0 && owner == mc.thePlayer && ownerWorld==mc.theWorld; }
    public int getSpoofedSlot() { return enchantedSlot; }
    public ColorSetting getSilentColor() { return silentColor; }
}
