package dev.vibe.module.impl.meme;

import dev.vibe.chat.ClientChat;
import dev.vibe.features.FeatureModule;
import dev.vibe.inventory.ContainerClickDispatcher;
import dev.vibe.module.Category;
import dev.vibe.module.impl.combat.TargetsModule;
import dev.vibe.module.impl.movement.*;
import dev.vibe.pit.*;
import dev.vibe.setting.*;
import java.util.*;
import java.util.function.LongSupplier;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.entity.*;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.world.World;

/** Spawn-only, acknowledged menu workflow. Never invents a purchase or advances on a sent click alone. */
public final class RewoundPitModule extends FeatureModule {
    public static final String UPGRADE="Auto Upgrade",PRESTIGE="Auto Prestige";
    public final MultiSelectSetting modes=addSetting(new MultiSelectSetting("Modes",Arrays.asList(UPGRADE,PRESTIGE),Arrays.asList(UPGRADE,PRESTIGE)));
    public final BooleanSetting filterMessages=addSetting(new BooleanSetting("Filter Messages",true));
    public final BooleanSetting alwaysUseTargetArea=addSetting(new BooleanSetting("Always Use TargetArea",true));
    public final BooleanSetting autoUnstuck=addSetting(new BooleanSetting("Auto Unstuck",true));
    public final NumberSetting unstuckSeconds=addSetting(new NumberSetting("Unstuck Delay (s)",60,10,600,5,autoUnstuck::isEnabled));
    public final NumberSetting clickDelay=addSetting(new NumberSetting("Menu Delay (ms)",200,125,1000,25));
    public final ModeSetting perk1=perkSetting(1,PitPerk.GOLDEN_HEADS),perk2=perkSetting(2,PitPerk.STRENGTH),
            perk3=perkSetting(3,PitPerk.DIAMOND),perk4=perkSetting(4,PitPerk.SAFETY);
    private ModeSetting perkSetting(int slot,PitPerk value){return addSetting(new ModeSetting("Perk Slot "+slot,value.label,()->modes.isSelected(UPGRADE),PitPerk.options()));}
    private enum Phase { IDLE,WALKING,OPENING,PERMANENT,CHOOSE,EQUIP,CONFIRM,RESET }
    private Phase phase=Phase.IDLE;
    private boolean prestigeJob,extraSlot;
    private final boolean[] known=new boolean[4];
    private final PitPerk[] equipped=new PitPerk[4];
    private final LongSupplier clock;
    private final ContainerClickDispatcher clicks;
    private World world;
    private EntityPlayer player;
    private EntityVillager npc;
    private ContainerChest ownedMenu;
    private PitStatus stats=PitStatus.EMPTY,previous=PitStatus.EMPTY;
    private long lastFight,nextSpawn,nextJob,phaseSince,started,nextInteract;
    private int pending=-1,interactions;
    private PitPerk buying;
    private double budget;
    private String status="Idle";
    private String reported="";
    private long nextReport;
    public RewoundPitModule(){this(System::currentTimeMillis);}
    public RewoundPitModule(LongSupplier clock){super("Rewound Pit","Buy configured perks and prestige at spawn",Category.MEME);this.clock=clock;clicks=new ContainerClickDispatcher(clock);}
    @Override protected void onEnable(){lastFight=clock.getAsLong();nextSpawn=nextJob=0;reported="";nextReport=lastFight+2000;resetKnowledge();}
    @Override protected void onDisable(){finish(true,0);resetKnowledge();world=null;player=null;stats=previous=PitStatus.EMPTY;status="Idle";}
    private void resetKnowledge(){Arrays.fill(known,false);Arrays.fill(equipped,null);extraSlot=false;}
    private TargetsModule targets(){return module(TargetsModule.class);}
    private boolean spawn(double x,double y,double z){TargetsModule t=targets();return t!=null&&!t.inConfiguredTargetArea(x,y,z);}
    public boolean atSpawn(){return mc.thePlayer!=null&&spawn(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ);}
    private boolean allowed(BlockPos p){return spawn(p.getX()+.5,p.getY()+.05,p.getZ()+.5);}
    public boolean busy(){return isEnabled()&&phase!=Phase.IDLE&&stats.active&&world==mc.theWorld&&player==mc.thePlayer&&mc.thePlayer!=null&&!mc.thePlayer.isDead&&atSpawn();}
    public static boolean reservesActions(){RewoundPitModule pit=module(RewoundPitModule.class);return pit!=null&&pit.busy();}
    public static boolean forcesTargetArea(){RewoundPitModule pit=module(RewoundPitModule.class);return pit!=null&&pit.isEnabled()&&pit.alwaysUseTargetArea.isEnabled();}
    public String getStatus(){return status;}
    public PitStatus getPitStatus(){return stats;}
    public void fought(Entity entity){if(isEnabled()&&entity instanceof EntityPlayer&&entity!=mc.thePlayer)lastFight=clock.getAsLong();}
    public boolean filterMessage(String text){return isEnabled()&&filterMessages.isEnabled()&&PitStatus.read(mc).active&&PitMenus.noisyMessage(text);}

    @Override public void tick(){
        try{tickAutomation();}finally{
            if(isEnabled()&&mc.thePlayer!=null&&clock.getAsLong()>=nextReport&&!status.equals(reported)
                    &&(status.startsWith("Waiting")||status.startsWith("Looking")||status.equals("In combat area"))){
                reported=status;nextReport=clock.getAsLong()+10000;
                ClientChat.send("Rewound Pit: "+status+(status.equals("In combat area")?". Spawn is outside the configured combat area (inversion applies).":" (level "+stats.level+", gold "+stats.gold+")."));
            }
        }
    }
    private void tickAutomation(){
        if(!isEnabled())return;
        long now=clock.getAsLong();
        MoveFixModule movement=module(MoveFixModule.class);if(movement!=null)movement.clearForcedMovement(getId());
        if(world!=mc.theWorld||player!=mc.thePlayer){finish(false,0);resetKnowledge();world=mc.theWorld;player=mc.thePlayer;previous=PitStatus.EMPTY;lastFight=now;nextSpawn=0;}
        if(!playing()||!mc.thePlayer.isEntityAlive()||mc.thePlayer.isSpectator()||mc.thePlayer.isRiding()||FreecamModule.cameraActiveHook()){
            finish(false,0);stats=PitStatus.EMPTY;status="Paused";return;
        }
        stats=PitStatus.read(mc);
        if(!stats.active){finish(false,0);status="Waiting for the Rewound Pit sidebar";return;}
        boolean reset=previous.active&&(stats.level<previous.level||stats.prestige>previous.prestige&&previous.prestige>=0);
        if(reset)resetKnowledge();previous=stats;
        if(mc.thePlayer.hurtTime>0)lastFight=now;
        if(!atSpawn()){
            finish(false,0);status="In combat area";
            if(autoUnstuck.isEnabled()&&mc.currentScreen==null&&now-lastFight>=unstuckSeconds.getDouble()*1000&&now>=nextSpawn){
                mc.thePlayer.sendChatMessage("/spawn");lastFight=now;nextSpawn=now+(long)(unstuckSeconds.getDouble()*1000);status="Returning to spawn";
            }return;
        }
        // Time at spawn does not consume the next combat inactivity window.
        lastFight=now;
        if(phase==Phase.RESET&&reset){finish(true,1500);status="Prestige complete";ClientChat.send("Prestiged. Returning to normal walking.");return;}
        if(phase!=Phase.IDLE&&(!modes.isSelected(prestigeJob?PRESTIGE:UPGRADE)||!prestigeJob&&stats.level>=120))finish(true,0);
        if(phase==Phase.IDLE){
            if(mc.currentScreen!=null||now<nextJob)return;
            budget=stats.gold;int choice=nextPerk();
            boolean prestige=stats.level==120&&modes.isSelected(PRESTIGE);
            if(!prestige&&(stats.level>=120||!modes.isSelected(UPGRADE)||choice<0)){status="Nothing to buy";return;}
            npc=findNpc(prestige);if(npc==null){nextJob=now+3000;status="Looking for the "+(prestige?"prestige":"upgrades")+" villager";return;}
            prestigeJob=prestige;pending=choice;started=now;interactions=0;change(Phase.WALKING);
        }
        if(!busy()){finish(false,1000);return;}
        if(now-started>45000||now-phaseSince>(phase==Phase.WALKING?30000:6000)){fail("Menu or route timed out",15000);return;}
        if(mc.currentScreen!=null){
            if(!(mc.currentScreen instanceof GuiChest)||!(mc.thePlayer.openContainer instanceof ContainerChest)){finish(false,3000);return;}
            ContainerChest chest=(ContainerChest)mc.thePlayer.openContainer;
            String title=PitMenus.title(chest);
            if(!expectedTitle(title)){finish(false,3000);return;}
            ownedMenu=chest;hold();
            if(now-phaseSince<clickDelay.getInt())return;
            menu(chest,title,now);return;
        }
        if(phase==Phase.RESET){hold();return;}
        // Server close/open packets can arrive on different ticks while changing menus.
        if(phase!=Phase.WALKING&&phase!=Phase.OPENING){hold();return;}
        if(npc==null||npc.isDead||npc.worldObj!=world||world.getEntityByID(npc.getEntityId())!=npc){fail("Villager is no longer available",5000);return;}
        if(mc.thePlayer.getDistanceSqToEntity(npc)<=6.25&&mc.thePlayer.canEntityBeSeen(npc)){
            hold();if(phase==Phase.WALKING)change(Phase.OPENING);status="Opening "+(prestigeJob?"prestige":"permanent upgrades");
        }else{
            if(phase==Phase.OPENING)change(Phase.WALKING);
            AutoWalkModule walk=module(AutoWalkModule.class);if(walk==null){fail("AutoWalk navigation is unavailable",15000);return;}
            walk.requestNavigation(this,npc,this::allowed);status="Walking to "+(prestigeJob?"prestige":"upgrades");
        }
    }
    private boolean expectedTitle(String title){return prestigeJob?title.equals("prestige")||title.equals("confirm prestige"):
            title.equals("permanent upgrades")||title.equals("permament upgrades")||title.equals("choose a perk");}
    private void hold(){AutoWalkModule walk=module(AutoWalkModule.class);if(walk!=null)walk.requestNavigation(this,null,this::allowed);}
    @Override public void input(MovementInput input){
        if(busy()&&(phase!=Phase.WALKING||mc.currentScreen!=null)){
            input.moveForward=input.moveStrafe=0;input.jump=false;
            MoveFixModule fix=module(MoveFixModule.class);if(fix!=null)fix.setForcedMovement(getId(),0,0,false);
        }else{MoveFixModule fix=module(MoveFixModule.class);if(fix!=null)fix.clearForcedMovement(getId());}
    }
    @Override public void applyRotation(){
        MoveFixModule fix=module(MoveFixModule.class);if(fix==null)return;
        if(!busy()||phase!=Phase.OPENING||npc==null||mc.currentScreen!=null){fix.clearFakeRotation(getId());return;}
        Vec3 eyes=mc.thePlayer.getPositionEyes(1);AxisAlignedBB box=npc.getEntityBoundingBox();
        double dx=(box.minX+box.maxX)*.5-eyes.xCoord,dz=(box.minZ+box.maxZ)*.5-eyes.zCoord,dy=box.minY+npc.height*.65-eyes.yCoord;
        float yaw=(float)Math.toDegrees(Math.atan2(dz,dx))-90,pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
        fix.setFakeRotation(getId(),AutoWalkModule.turn(fix.getRotationYaw(),yaw,35),AutoWalkModule.turn(fix.getRotationPitch(),pitch,35),"Silent");
    }
    @Override public void afterWalking(){
        if(!busy()||phase!=Phase.OPENING||npc==null||mc.currentScreen!=null||mc.thePlayer.isUsingItem()||clock.getAsLong()<nextInteract||interactions>=3||mc.playerController==null)return;
        MoveFixModule fix=module(MoveFixModule.class);if(fix==null||!fix.ownsRotation(getId())||mc.thePlayer.getDistanceSqToEntity(npc)>6.25||!mc.thePlayer.canEntityBeSeen(npc))return;
        Vec3 eyes=mc.thePlayer.getPositionEyes(1);double yaw=Math.toRadians(fix.getRotationYaw()),pitch=Math.toRadians(fix.getRotationPitch());
        Vec3 end=eyes.addVector(-Math.sin(yaw)*Math.cos(pitch)*3,-Math.sin(pitch)*3,Math.cos(yaw)*Math.cos(pitch)*3);
        MovingObjectPosition hit=npc.getEntityBoundingBox().expand(.1,.1,.1).calculateIntercept(eyes,end);if(hit==null)return;
        // The native controller sends interact-at first, then the normal interact fallback, after C03 rotation.
        if(!mc.playerController.isPlayerRightClickingOnEntity(mc.thePlayer,npc,new MovingObjectPosition(npc,hit.hitVec)))
            mc.playerController.interactWithEntitySendPacket(mc.thePlayer,npc);
        interactions++;nextInteract=clock.getAsLong()+1500;
    }
    private int nextPerk(){
        ModeSetting[] choices={perk1,perk2,perk3,perk4};
        for(int i=0;i<4;i++){
            PitPerk perk=PitPerk.named(choices[i].getValue());
            if(perk==null||!perk.affordable(stats,i)||budget<perk.price||known[i]&&equipped[i]==perk||i==3&&known[3]&&!extraSlot)continue;
            boolean duplicate=false;for(int j=0;j<4;j++)if(j!=i&&known[j]&&equipped[j]==perk)duplicate=true;
            if(!duplicate)return i;
        }return -1;
    }
    private void inspect(ContainerChest chest){
        for(int i=0;i<4;i++){
            int slot=PitMenus.findPerkSlot(chest,i+1);if(slot<0)continue;
            ItemStack item=chest.getSlot(slot).getStack();known[i]=true;equipped[i]=PitPerk.from(item);
            if(i==3)extraSlot=!PitMenus.locked(item);
        }
    }
    private void menu(ContainerChest chest,String title,long now){
        if(mc.thePlayer.inventory.getItemStack()!=null)return; // Wait for the server's cursor correction; never place a carried item into a menu.
        if(prestigeJob){
            if(phase==Phase.RESET){if(now-phaseSince>=300)closeOwned();return;}
            if(stats.level!=120){finish(true,1500);return;}
            if(title.equals("prestige")&&(phase==Phase.OPENING||phase==Phase.WALKING)){
                int diamond=PitMenus.findItem(chest,"diamond");if(diamond<0)return;
                if(!PitMenus.prestigeReady(chest.getSlot(diamond).getStack())){fail("Prestige requirements or cooldown are not ready",60000);return;}
                if(clicks.click(chest,diamond,0,0,clickDelay.getInt()))change(Phase.CONFIRM);
            }else if(title.equals("confirm prestige")&&phase==Phase.CONFIRM){
                int emerald=PitMenus.findItem(chest,"emerald");if(emerald<0)return;
                ItemStack confirm=chest.getSlot(emerald).getStack();
                if(!PitMenus.text(confirm).toLowerCase(Locale.ROOT).startsWith("confirm")||PitMenus.locked(confirm))return;
                if(clicks.click(chest,emerald,0,0,clickDelay.getInt())){change(Phase.RESET);status="Waiting for prestige reset";}
            }return;
        }
        if(title.equals("permanent upgrades")||title.equals("permament upgrades")){
            if(phase==Phase.CHOOSE)return; // A sent slot click must produce the chooser before any next action.
            if(phase==Phase.EQUIP){
                int equippedSlot=PitMenus.findPerkSlot(chest,pending+1);
                if(equippedSlot<0||PitPerk.from(chest.getSlot(equippedSlot).getStack())!=buying)return;
                budget=Math.max(0,budget-buying.price);ClientChat.send("Equipped "+buying.label+" in perk slot "+(pending+1)+".");
                change(Phase.PERMANENT);
            }
            inspect(chest);pending=nextPerk();
            if(pending<0){finish(true,1500);status="Perks ready";return;}
            int slot=PitMenus.findPerkSlot(chest,pending+1);if(slot<0)return;
            if(PitMenus.locked(chest.getSlot(slot).getStack())){known[pending]=true;fail("Perk slot "+(pending+1)+" is locked",15000);return;}
            ModeSetting[] choices={perk1,perk2,perk3,perk4};buying=PitPerk.named(choices[pending].getValue());
            if(clicks.click(chest,slot,0,0,clickDelay.getInt())){change(Phase.CHOOSE);status="Choosing "+buying.label;}
        }else if(title.equals("choose a perk")&&phase==Phase.CHOOSE){
            ModeSetting[] choices={perk1,perk2,perk3,perk4};
            if(buying!=PitPerk.named(choices[pending].getValue())){finish(true,1500);return;}
            if(buying==null||!buying.affordable(stats,pending)||budget<buying.price){fail("Perk requirements changed",5000);return;}
            int slot=-1;for(int i=0;i<chest.getLowerChestInventory().getSizeInventory();i++)if(buying.matches(chest.getSlot(i).getStack())){slot=i;break;}
            if(slot<0)return;
            if(PitMenus.locked(chest.getSlot(slot).getStack())){fail("The server did not offer this perk for purchase",30000);return;}
            if(clicks.click(chest,slot,0,0,clickDelay.getInt()))change(Phase.EQUIP);
        }
    }
    private EntityVillager findNpc(boolean prestige){
        EntityVillager best=null;double distance=64*64;
        for(Entity entity:world.loadedEntityList){
            if(!(entity instanceof EntityVillager)||entity.isDead)continue;
            StringBuilder labels=new StringBuilder(PitStatus.plain(entity.getCustomNameTag()));
            for(Entity stand:world.loadedEntityList)if(stand instanceof EntityArmorStand&&stand.hasCustomName()
                    &&Math.abs(stand.posX-entity.posX)<2.5&&Math.abs(stand.posZ-entity.posZ)<2.5&&stand.posY>=entity.posY-.5&&stand.posY-entity.posY<6)
                labels.append(' ').append(PitStatus.plain(stand.getCustomNameTag()));
            String text=labels.toString().toLowerCase(Locale.ROOT);
            boolean match=prestige?text.contains("prestige"):text.contains("upgrades");
            double d=mc.thePlayer.getDistanceSqToEntity(entity);if(match&&d<distance&&new dev.vibe.path.PathTerrain(world,mc.thePlayer,this::allowed).interactionGoal(entity)!=null){distance=d;best=(EntityVillager)entity;}
        }return best;
    }
    private void change(Phase next){phase=next;phaseSince=clock.getAsLong();}
    private void closeOwned(){if(ownedMenu!=null&&mc.thePlayer!=null&&mc.thePlayer.openContainer==ownedMenu&&mc.currentScreen instanceof GuiChest)mc.thePlayer.closeScreen();ownedMenu=null;}
    private void finish(boolean close,long cooldown){
        if(close)closeOwned();else ownedMenu=null;
        AutoWalkModule walk=module(AutoWalkModule.class);if(walk!=null)walk.releaseNavigation(this);
        MoveFixModule fix=module(MoveFixModule.class);if(fix!=null){fix.clearFakeRotation(getId());fix.clearForcedMovement(getId());}
        phase=Phase.IDLE;npc=null;buying=null;pending=-1;clicks.reset();nextInteract=0;
        if(cooldown>0)nextJob=clock.getAsLong()+cooldown;
    }
    private void fail(String reason,long cooldown){finish(true,cooldown);status=reason;ClientChat.send("Rewound Pit: "+reason+". Retrying later.");}
}
