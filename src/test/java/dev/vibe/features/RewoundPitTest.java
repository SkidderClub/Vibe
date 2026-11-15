package dev.vibe.features;

import dev.vibe.module.impl.meme.RewoundPitModule;
import dev.vibe.module.impl.movement.*;
import dev.vibe.path.*;
import dev.vibe.pit.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.*;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.*;
import net.minecraft.init.*;
import net.minecraft.inventory.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.scoreboard.*;
import net.minecraft.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RewoundPitTest {
    @Test public void parsesFormattedSidebarValuesAndDoesNotConfuseXpStreakOrRedScoreColumn(){
        PitStatus stats=PitStatus.parse(Arrays.asList("§fPrestige: §eII","§fLevel:§d[103]","Needed XP: 1,223","Gold: §616,447g","Bounty: 700g","play.rewound.cc"),false);
        assertTrue(stats.active);assertEquals(103,stats.level);assertEquals(2,stats.prestige);assertEquals(16447,stats.gold,0);
        assertEquals(31272.47,PitStatus.parse(Arrays.asList("Level:[120]","Gold:31,272.47g"),true).gold,1E-6);
        assertFalse(PitStatus.parse(Arrays.asList("Level:[12]","Gold:2,000g"),false).active);
        assertFalse(PitStatus.parse(Arrays.asList("Level:[120]","Gold:--","play.rewound.cc"),false).active);
    }
    @Test public void filtersOnlyServerNotificationPrefixes(){
        for(String text:new String[]{"DOUBLE KILL! on [113] GrimRT +31XP +14.00g","PIT LEVEL UP! [2] -> [3]","ASSIST! 27% on x","STREAK! of 1100 kills","MEGASTREAK! [120] someone activated OVERDRIVE!","BOUNTY! 100g","MULTIKILL! x","KILL! x"})assertTrue(text,PitMenus.noisyMessage("§a"+text));
        for(String text:new String[]{"<Player> DOUBLE KILL!","[MVP] Name: ASSIST!","You cannot use /spawn in combat!","Prestige available!","Assist! text","[Vibe] KILL!"})assertFalse(text,PitMenus.noisyMessage(text));
    }
    @Test public void acceptsPrestigeDecoratedLevelsAndInvisibleSidebarSeparators(){
        PitStatus status=PitStatus.parse(Arrays.asList("§eLevel: [V-120]","Gold: 31\u202f272.47g","Prestige: IV","play.re\u200bwound.cc"),false);
        assertTrue(status.active);assertEquals(120,status.level);assertEquals(31272.47,status.gold,1E-6);
    }
    @Test public void prestigeMenuEnforcesGoldCooldownAndReadiness(){
        assertTrue(PitMenus.prestigeReady(item(Items.diamond,"Prestige IV","Level 120: reached","Gold this prestige: 31,272.47 / 26,000","Cooldown: ready","Click to prestige!")));
        assertFalse(PitMenus.prestigeReady(item(Items.diamond,"Prestige IV","Gold this prestige: 25,999 / 26,000","Cooldown: ready","Click to prestige!")));
        assertFalse(PitMenus.prestigeReady(item(Items.diamond,"Prestige IV","Cooldown: 15m","Click to prestige!")));
        assertFalse(PitMenus.prestigeReady(item(Items.diamond,"Prestige IV","Requires level 120","Click to prestige!")));
    }
    @Test public void purchasesDefaultPerksWithAcknowledgedTransitionsThenClosesAndResumesWalking()throws Exception{
        try(Fixture f=new Fixture()){
            f.stats(103,2,16447);f.villager(false,1.5,64,.5);f.walk.setEnabled(true);f.tick();assertTrue(f.pit.busy());assertTrue(f.walk.isEnabled());
            f.menu("Permanent upgrades",27);f.perkSlots(null,null,null,false);f.advance();assertEquals(Arrays.asList(11),f.controller.slots);
            f.advance();assertEquals("Do not repeat while the server has not opened the chooser",1,f.controller.slots.size());
            f.menu("Choose a perk",36);f.put(10,item(Blocks.gold_block,"Golden Heads","Cost: 500g"));f.advance();assertEquals(Arrays.asList(11,10),f.controller.slots);
            f.menu("Permanent upgrades",27);f.perkSlots(PitPerk.GOLDEN_HEADS,null,null,false);f.advance();assertEquals(Arrays.asList(11,10,12),f.controller.slots);
            f.menu("Choose a perk",36);f.put(13,item(Items.redstone,"Strength-Chaining"));f.advance();
            f.menu("Permanent upgrades",27);f.perkSlots(PitPerk.GOLDEN_HEADS,PitPerk.STRENGTH,null,false);f.advance();assertEquals(13,(int)f.controller.slots.get(4));
            f.menu("Choose a perk",36);f.put(20,item(Items.diamond,"Lucky Diamond"));f.advance();
            f.menu("Permanent upgrades",27);f.perkSlots(PitPerk.GOLDEN_HEADS,PitPerk.STRENGTH,PitPerk.DIAMOND,false);f.advance();
            assertFalse(f.pit.busy());assertTrue(f.walk.isEnabled());assertFalse(f.walk.ownsNavigation(f.pit));assertNull(f.mc.currentScreen);assertEquals(1,f.player.closed);
            f.time+=2000;f.tick();assertFalse("Do not reopen for already equipped perks",f.pit.busy());
        }
    }
    @Test public void prestigeTakesPriorityRequiresLevel120AndWaitsForTheActualReset()throws Exception{
        try(Fixture f=new Fixture()){
            f.stats(120,3,40000);f.villager(true,1.5,64,.5);f.villager(false,3.5,64,.5);f.tick();
            assertTrue(f.pit.getStatus().contains("prestige"));
            f.menu("Prestige",27);f.put(11,item(Items.diamond,"Prestige IV","Level 120: reached","Gold this prestige: 31,272.47 / 26,000","Cooldown: ready","Click to prestige!"));f.advance();
            assertEquals(Arrays.asList(11),f.controller.slots);
            f.advance();assertEquals(1,f.controller.slots.size());
            f.menu("Confirm prestige",27);f.put(11,item(Items.emerald,"Confirm","Your level, gold, perks and upgrades reset."));f.advance();
            assertEquals(Arrays.asList(11,11),f.controller.slots);assertTrue(f.pit.busy());
            f.time+=400;f.tick();assertNull(f.mc.currentScreen);assertTrue(f.pit.busy());
            f.stats(1,4,0);f.advance();assertFalse(f.pit.busy());assertFalse(f.walk.ownsNavigation(f.pit));assertEquals("Prestige complete",f.pit.getStatus());
        }
        try(Fixture f=new Fixture()){
            f.stats(119,3,40000);f.pit.modes.setValue(new LinkedHashSet<>(Arrays.asList(RewoundPitModule.PRESTIGE)));f.villager(true,1.5,64,.5);f.tick();assertFalse(f.pit.busy());assertTrue(f.controller.slots.isEmpty());
        }
    }
    @Test public void slotFourRequiresTheActualRenownUnlockAndLowerSlotsRespectLevels()throws Exception{
        try(Fixture f=new Fixture()){
            f.stats(69,3,20000);f.pit.perk1.setValue("None");f.pit.perk2.setValue("None");f.pit.perk4.setValue("None");f.villager(false,1.5,64,.5);f.tick();assertFalse(f.pit.busy());
            f.pit.perk3.setValue("None");f.pit.perk4.setValue("Safety First");f.tick();assertTrue(f.pit.busy());
            f.menu("Permanent upgrades",27);f.perkSlots(null,null,null,false);f.advance();assertFalse(f.pit.busy());assertTrue(f.controller.slots.isEmpty());
        }
        try(Fixture f=new Fixture()){
            f.stats(80,3,20000);f.pit.perk1.setValue("None");f.pit.perk2.setValue("None");f.pit.perk3.setValue("None");f.villager(false,1.5,64,.5);f.tick();
            f.menu("Permanent upgrades",27);f.perkSlots(null,null,null,true);f.advance();assertEquals(Arrays.asList(14),f.controller.slots);
        }
    }
    @Test public void combatAreaInversionAndTemporaryEnforcementDoNotRewriteTargetSettings()throws Exception{
        try(Fixture f=new Fixture()){
            assertFalse(f.base.base.filters.targetArea.isEnabled());assertTrue(f.base.base.filters.isTargetAreaActive());
            f.base.base.filters.displayTargetArea.setEnabled(true);assertTrue(f.base.base.filters.shouldDisplayTargetArea());
            f.stats(103,2,15000);f.villager(false,1.5,64,.5);f.player.setPosition(8.5,64,.5);f.tick();assertFalse(f.pit.busy());
            f.base.base.filters.invertArea.setEnabled(true);f.tick();assertFalse("NPC outside the inverted spawn cannot be selected",f.pit.busy());
            f.villager(false,9.5,64,.5);f.time+=4000;f.tick();assertTrue(f.pit.busy());
            f.pit.setEnabled(false);assertFalse(f.base.base.filters.targetArea.isEnabled());assertFalse(f.base.base.filters.isTargetAreaActive());assertFalse(f.base.base.filters.shouldDisplayTargetArea());
        }
    }
    @Test public void autoUnstuckUsesFightActivityAndDoesNotSpamOrRunAtSpawn()throws Exception{
        try(Fixture f=new Fixture()){
            f.stats(8,0,0);f.player.setPosition(8.5,64,.5);f.tick();f.time+=59000;f.tick();assertTrue(f.player.sent.isEmpty());
            f.pit.fought(f.villager(false,9.5,64,.5)); // NPC interaction is not combat.
            f.time+=1000;f.tick();assertEquals(Arrays.asList("/spawn"),f.player.sent);f.tick();assertEquals(1,f.player.sent.size());
            EntityPlayer opponent=f.base.player("Opponent",9.5,64,.5);f.time+=59000;f.pit.fought(opponent);f.tick();assertEquals(1,f.player.sent.size());
            f.time+=59000;f.tick();assertEquals(1,f.player.sent.size());f.time+=1000;f.tick();assertEquals(2,f.player.sent.size());
            f.player.setPosition(.5,64,.5);f.time+=120000;f.tick();assertEquals(2,f.player.sent.size());
        }
    }
    @Test public void manualMenusStayUntouchedAndDisableReleasesNavigation()throws Exception{
        try(Fixture f=new Fixture()){
            f.stats(103,2,15000);f.villager(false,5.5,64,.5);f.menu("Storage",27);f.tick();assertFalse(f.pit.busy());assertTrue(f.controller.slots.isEmpty());assertEquals(0,f.player.closed);
            f.mc.currentScreen=null;f.tick();assertTrue(f.walk.ownsNavigation(f.pit));assertFalse(f.walk.isEnabled());
            f.base.base.finishRoute();assertFalse(f.base.base.module.getRoute().nodes.isEmpty());
            f.pit.setEnabled(false);assertFalse(f.walk.ownsNavigation(f.pit));assertFalse(f.walk.isEnabled());assertFalse(f.fix.hasForcedMovement());
        }
    }
    @Test public void npcInteractionUsesRotationThenNativeInteractAtAndFallbackOnlyOnce()throws Exception{
        try(Fixture f=new Fixture()){
            f.stats(10,0,500);f.villager(false,1.5,64,.5);f.tick();
            for(int i=0;i<4;i++){f.pit.applyRotation();f.pit.afterWalking();f.time+=50;}
            assertEquals(Arrays.asList("interact-at","interact"),f.controller.interactions);
            assertTrue(f.fix.ownsRotation(f.pit.getId()));
            f.player.setPosition(8.5,64,.5);f.tick();f.pit.applyRotation();f.pit.afterWalking();
            assertFalse(f.pit.busy());assertFalse(f.fix.ownsRotation(f.pit.getId()));assertEquals(2,f.controller.interactions.size());
        }
    }
    @Test public void villagerFeetMayBeOutsideSpawnWhileThePlayerInteractsFromAValidSpawnSpot()throws Exception{
        try(Fixture f=new Fixture()){
            f.base.base.filters.setCorner(1,new BlockPos(-10,0,-10));f.base.base.filters.setCorner(2,new BlockPos(10,63,10));
            f.stats(10,0,500);f.villager(false,1.5,63.5,.5);f.tick();
            assertTrue(f.pit.atSpawn());assertTrue(f.pit.busy());assertTrue(f.pit.getStatus().startsWith("Opening"));
        }
    }
    @Test public void rejectedPurchaseWaitsForServerAndTimesOutWithoutReclickingOrClaimingEquip()throws Exception{
        try(Fixture f=new Fixture()){
            f.stats(10,0,500);f.villager(false,1.5,64,.5);f.tick();
            f.menu("Permanent upgrades",27);f.perkSlots(null,null,null,false);f.advance();
            f.menu("Choose a perk",36);f.put(10,item(Blocks.gold_block,"Golden Heads"));f.advance();
            for(int i=0;i<4;i++)f.advance();assertTrue(f.pit.busy());assertEquals(Arrays.asList(11,10),f.controller.slots);
            f.time+=6100;f.tick();assertFalse(f.pit.busy());assertNull(f.mc.currentScreen);assertFalse(f.walk.ownsNavigation(f.pit));
            f.advance();assertFalse(f.pit.busy());assertEquals(2,f.controller.slots.size());
        }
    }
    @Test public void npcRouteStaysInSpawnEvenWhenTheShortestLineCrossesCombat()throws Exception{
        try(Fixture f=new Fixture()){
            f.base.base.filters.setCorner(1,new BlockPos(2,0,-1));f.base.base.filters.setCorner(2,new BlockPos(3,255,1));
            f.stats(10,0,500);f.villager(false,5.5,64,.5);f.tick();f.base.base.finishRoute();
            PathRoute route=f.base.base.module.getRoute();assertTrue(route.complete);
            boolean detour=false;for(BlockPos node:route.nodes){assertFalse(f.base.base.filters.inConfiguredTargetArea(node.getX()+.5,node.getY()+.05,node.getZ()+.5));detour|=node.getZ()<=-2||node.getZ()>=2;}
            assertTrue(detour);assertFalse(f.walk.isEnabled());
        }
    }
    @Test public void sharedWalkerActuallyFollowsTheSpawnDetourWithNormalCollisionPhysics()throws Exception{
        try(Fixture f=new Fixture()){
            f.base.base.filters.setCorner(1,new BlockPos(2,0,-1));f.base.base.filters.setCorner(2,new BlockPos(3,255,1));
            f.stats(10,0,500);EntityVillager npc=f.villager(false,6.5,64,.5);f.tick();f.base.base.finishRoute();
            dev.vibe.module.impl.world.RavenSimulatedPlayer sim=dev.vibe.module.impl.world.RavenSimulatedPlayer.fromClientPlayer(f.player.movementInput);
            for(int tick=0;tick<240&&f.player.getDistanceSqToEntity(npc)>6.25;tick++){
                f.time+=50;f.tick();f.player.movementInput.updatePlayerMoveState();MovementInput input=f.player.movementInput;
                sim.movementInput.moveForward=input.moveForward;sim.movementInput.moveStrafe=input.moveStrafe;sim.movementInput.jump=input.jump;sim.rotationYaw=f.player.rotationYaw;sim.tick();
                Vec3 pos=sim.getPos();f.player.setPosition(pos.xCoord,pos.yCoord,pos.zCoord);f.player.setEntityBoundingBox(sim.box);
                f.player.motionX=sim.motionX;f.player.motionY=sim.motionY;f.player.motionZ=sim.motionZ;f.player.onGround=sim.onGround;f.player.isCollidedHorizontally=sim.isCollidedHorizontally;f.player.fallDistance=sim.fallDistance;
                assertTrue("Walker entered combat",f.pit.atSpawn());if(tick%5==4)f.base.base.forceReplan();f.base.base.module.tick();
            }
            assertTrue("Could not reach NPC: "+f.player.posX+", "+f.player.posZ+"; "+f.walk.getStatus(),f.player.getDistanceSqToEntity(npc)<=6.25);
            assertFalse(f.walk.isEnabled());
        }
    }

    private static ItemStack item(net.minecraft.block.Block block,String name,String... lore){return decorate(new ItemStack(block),name,lore);}
    private static ItemStack item(Item item,String name,String... lore){return decorate(new ItemStack(item),name,lore);}
    private static ItemStack decorate(ItemStack stack,String name,String... lore){stack.setStackDisplayName(name);NBTTagCompound display=stack.getTagCompound().getCompoundTag("display");NBTTagList list=new NBTTagList();for(String line:lore)list.appendTag(new NBTTagString(line));display.setTag("Lore",list);stack.getTagCompound().setTag("display",display);return stack;}
    private static final class Fixture implements AutoCloseable {
        final MovementForecastTest.Fixture scene=new MovementForecastTest.Fixture();
        final dev.vibe.combat.ProjectileFixture base=scene.base;final Minecraft mc=base.base.mc;
        final Player player=PathTestFixture.allocate(Player.class);final Controller controller=new Controller(mc);
        final MoveFixModule fix=new MoveFixModule();final AutoWalkModule walk=new AutoWalkModule();
        long time=10000;int window=10;final RewoundPitModule pit=new RewoundPitModule(()->time);
        Scoreboard board;InventoryBasic lower;
        Fixture()throws Exception{
            for(Class<?> type=EntityPlayerSP.class;type!=Object.class;type=type.getSuperclass())for(Field field:type.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())){field.setAccessible(true);field.set(player,field.get(base.self));}
            player.worldObj=scene.world;player.inventory=new InventoryPlayer(player);player.sent=new ArrayList<>();
            scene.world.loadedEntityList.remove(base.self);scene.world.playerEntities.remove(base.self);scene.world.loadedEntityList.add(player);scene.world.playerEntities.add(player);
            mc.thePlayer=player;mc.playerController=controller;PathTestFixture.set(Minecraft.class,mc,"renderViewEntity",player);
            base.base.modules.registerDynamic(fix);base.base.modules.registerDynamic(walk);base.base.modules.registerDynamic(pit);fix.installInputHook();
            base.base.filters.setCorner(1,new BlockPos(8,0,-8));base.base.filters.setCorner(2,new BlockPos(20,255,8));pit.setEnabled(true);
        }
        void stats(int level,int prestige,double gold)throws Exception{
            board=new Scoreboard();PathTestFixture.set(dev.vibe.combat.ProjectileFixture.TestWorld.class,scene.world,"scoreboard",board);
            ScoreObjective objective=board.addScoreObjective("pit",IScoreObjectiveCriteria.DUMMY);board.setObjectiveInDisplaySlot(1,objective);
            String[] rows={"Level:["+level+"]","Prestige:"+prestige,"Gold:"+gold+"g","play.rewound.cc"};for(int i=0;i<rows.length;i++)board.getValueFromObjective(rows[i],objective).setScorePoints(i);
        }
        EntityVillager villager(boolean prestige,double x,double y,double z)throws Exception{
            EntityVillager npc=PathTestFixture.allocate(EntityVillager.class);init(npc,x,y,z,"Vendor");
            for(String name:prestige?new String[]{"PRESTIGE","Reset & Renown"}:new String[]{"UPGRADES","Permanent"}){EntityArmorStand stand=PathTestFixture.allocate(EntityArmorStand.class);init(stand,x,y+2,z,name);}
            return npc;
        }
        void init(Entity entity,double x,double y,double z,String name)throws Exception{
            entity.setEntityId(100+scene.world.loadedEntityList.size());entity.worldObj=scene.world;entity.width=.6F;entity.height=1.8F;entity.setPosition(x,y,z);
            DataWatcher watcher=new DataWatcher(entity);watcher.addObject(2,name);watcher.addObject(3,(byte)1);watcher.addObject(6,20F);PathTestFixture.set(Entity.class,entity,"dataWatcher",watcher);scene.world.loadedEntityList.add(entity);
        }
        void menu(String title,int size){lower=new InventoryBasic(title,true,size);GuiChest gui=new GuiChest(player.inventory,lower);mc.currentScreen=gui;player.openContainer=gui.inventorySlots;player.openContainer.windowId=window++;}
        void put(int slot,ItemStack stack){lower.setInventorySlotContents(slot,stack);}
        void perkSlots(PitPerk first,PitPerk second,PitPerk third,boolean fourth){
            PitPerk[] perks={first,second,third,null};for(int i=0;i<4;i++){
                ItemStack stack=perks[i]==null?new ItemStack(Items.dye,1,8):new ItemStack(i==0?Item.getItemFromBlock(Blocks.gold_block):i==1?Items.redstone:Items.diamond);
                decorate(stack,"Perk Slot #"+(i+1),i==3&&!fourth?"Requires the Extra Perk Slot renown upgrade (Prestige III).":"Click to choose perk!");put(11+i,stack);
            }
        }
        void tick(){pit.tick();walk.tick();player.ticksExisted++;}
        void advance(){time+=250;player.ticksExisted+=4;tick();}
        public void close()throws Exception{pit.setEnabled(false);walk.setEnabled(false);scene.close();}
    }
    public static class Player extends EntityPlayerSP {
        List<String> sent;int closed;private Player(){super(null,null,null,null);}
        @Override public String getName(){return "Self";}@Override public boolean isEntityAlive(){return !isDead;}@Override public boolean isSpectator(){return false;}
        @Override public boolean canEntityBeSeen(Entity entity){return true;}@Override public void sendChatMessage(String message){sent.add(message);}
        @Override public void addChatMessage(IChatComponent message){}@Override public boolean isSprinting(){return false;}
        @Override public void closeScreen(){closed++;Minecraft.getMinecraft().currentScreen=null;openContainer=inventoryContainer;}
    }
    private static final class Controller extends PlayerControllerMP {
        final List<Integer> slots=new ArrayList<>();final List<String> interactions=new ArrayList<>();Controller(Minecraft mc){super(mc,null);}
        @Override public ItemStack windowClick(int window,int slot,int button,int mode,EntityPlayer player){slots.add(slot);assertEquals(0,button);assertEquals(0,mode);return null;}
        @Override public boolean isPlayerRightClickingOnEntity(EntityPlayer player,Entity entity,MovingObjectPosition hit){interactions.add("interact-at");return false;}
        @Override public boolean interactWithEntitySendPacket(EntityPlayer player,Entity entity){interactions.add("interact");return true;}
    }
}
