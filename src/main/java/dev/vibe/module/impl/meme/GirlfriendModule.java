package dev.vibe.module.impl.meme;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import dev.vibe.setting.*;
import dev.vibe.companion.CompanionFollower;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.util.ResourceLocation;
import net.minecraft.block.BlockBed;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Items;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.BlockPos;
import org.lwjgl.input.Keyboard;

/** A local companion whose non-overlapping reactions follow her model through Minecraft's sound engine. */
public final class GirlfriendModule extends Module {
    public final NumberSetting volume = addSetting(new NumberSetting("Volume", 100, 0, 100, 1));
    public final NumberSetting followDistance=addSetting(new NumberSetting("Follow Distance",2,1,3.5,.1));
    public final NumberSetting closeRadius=addSetting(new NumberSetting("Stay Within",6,3,12,.5));
    public final NumberSetting followSpeed=addSetting(new NumberSetting("Follow Speed",1,.6,1.6,.05));
    public final NumberSetting modelScale=addSetting(new NumberSetting("Model Scale",.95,.8,1.1,.05));
    public final ModeSetting outfit=addSetting(new ModeSetting("Outfit","Goth","Goth","Chic","Casual"));
    public final ColorSetting hairColor=addSetting(new ColorSetting("Hair Color",0xFF684332));
    public final ColorSetting outfitColor=addSetting(new ColorSetting("Outfit Color",0xFF507C79));
    public final ColorSetting accentColor=addSetting(new ColorSetting("Accent Color",0xFFD9C5DF));
    public final ColorSetting eyeColor=addSetting(new ColorSetting("Eye Color",0xFF5F9C91));
    private final CompanionFollower follower=new CompanionFollower();
    private EntityOtherPlayerMP companion;
    private net.minecraft.world.World companionWorld;
    private Voice voice;private long voiceStarted;
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final Random random = new Random();
    private float previousHealth = -1.0F;
    private boolean wasDead;
    private boolean wasEating;
    private final Queue<Digging> digging = new ConcurrentLinkedQueue<Digging>();
    private BlockPos breakingBed;
    private boolean breakingOther;

    public GirlfriendModule() {
        super("Girlfriend", "A nearby companion with local positional voice reactions", Category.MEME, Keyboard.KEY_NONE);
    }

    public void tick() {
        if (!isEnabled() || minecraft.thePlayer == null || minecraft.theWorld==null) {
            clear();
            previousHealth = -1.0F; wasDead = false; wasEating = false;
            digging.clear(); breakingBed = null; breakingOther = false;
            return;
        }
        if(companionWorld!=minecraft.theWorld){clear();companionWorld=minecraft.theWorld;
            companion=new EntityOtherPlayerMP(minecraft.theWorld,new com.mojang.authlib.GameProfile(java.util.UUID.fromString("39105420-171c-4e10-b826-414b3f53e8aa"),"Girlfriend"));
            // This render-only player is deliberately absent from every world entity list.
            companion.setEntityId(Integer.MIN_VALUE+219);companion.width=.56F;companion.height=1.8F*modelScale.getFloat();
        }
        if(!minecraft.isGamePaused()){
            follower.tick(minecraft.theWorld,minecraft.thePlayer,followDistance.getDouble(),Math.max(closeRadius.getDouble(),followDistance.getDouble()+1),followSpeed.getDouble(),modelScale.getDouble());
            if(follower.active){companion.ticksExisted++;companion.setPosition(follower.x,follower.y,follower.z);companion.lastTickPosX=companion.prevPosX=follower.previousX;companion.lastTickPosY=companion.prevPosY=follower.previousY;companion.lastTickPosZ=companion.prevPosZ=follower.previousZ;
                companion.prevRenderYawOffset=follower.previousYaw;companion.renderYawOffset=follower.yaw;companion.prevRotationYawHead=follower.previousHeadYaw;companion.rotationYawHead=follower.headYaw;companion.prevRotationPitch=follower.previousPitch;companion.rotationPitch=follower.pitch;
                companion.limbSwing=follower.limbSwing;companion.prevLimbSwingAmount=follower.previousWalkAmount;companion.limbSwingAmount=follower.walkAmount;companion.onGround=follower.onGround;
            }
        }
        if(voice!=null){voice.update();if(volume.getInt()==0)stopVoice();}
        Digging action;
        while ((action = digging.poll()) != null) processDigging(action);
        float health = minecraft.thePlayer.getHealth();
        boolean dead = minecraft.thePlayer.isDead || health <= 0.0F;
        if (dead && !wasDead) play("dying/");
        wasDead = dead;
        if (previousHealth >= 0.0F && health < previousHealth && health < minecraft.thePlayer.getMaxHealth() * .5F && random.nextBoolean()) play("gettingDamaged/");
        previousHealth = health;
        boolean eating = minecraft.thePlayer.isUsingItem() && minecraft.thePlayer.getItemInUse() != null
                && minecraft.thePlayer.getItemInUse().getItem() instanceof ItemFood;
        if (eating && !wasEating) play(isVegan(minecraft.thePlayer.getItemInUse()) ? "eating/vegan/" : "eating/brutal/");
        wasEating = eating;
    }

    public void onAttack(EntityLivingBase target) { if (isEnabled() && target != null && random.nextInt(100) == 0) play("attacking/"); }
    public void onBreak(BlockBed bed) { if (isEnabled()) play("blockBreaking/BreakingBed/"); }
    public void onBreakOther() { if (isEnabled() && random.nextInt(10) == 0) play("blockBreaking/"); }

    /** Queue Netty packet state; inspect the world only on the client tick. */
    public void onDigging(C07PacketPlayerDigging packet) {
        if (isEnabled() && packet != null && packet.getPosition() != null) {
            digging.offer(new Digging(packet.getStatus(), packet.getPosition()));
        }
    }

    private void processDigging(Digging action) {
        if (minecraft.theWorld == null || action == null) return;
        if (action.action == C07PacketPlayerDigging.Action.START_DESTROY_BLOCK) {
            net.minecraft.block.Block block = minecraft.theWorld.getBlockState(action.pos).getBlock();
            breakingBed = null;
            breakingOther = !(block instanceof BlockBed);
            if (block instanceof BlockBed) {
                if (minecraft.thePlayer.capabilities.isCreativeMode) onBreak((BlockBed) block);
                else breakingBed = action.pos;
            }
        } else if (action.action == C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK) {
            if (breakingBed != null) onBreak((BlockBed) net.minecraft.init.Blocks.bed);
            else if (breakingOther) onBreakOther();
            breakingBed = null;
            breakingOther = false;
        } else if (action.action == C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK) {
            breakingBed = null;
            breakingOther = false;
        }
    }

    private boolean isVegan(ItemStack stack) {
        return stack != null && (stack.getItem() == Items.carrot || stack.getItem() == Items.potato
                || stack.getItem() == Items.baked_potato || stack.getItem() == Items.apple
                || stack.getItem() == Items.golden_apple || stack.getItem() == Items.melon);
    }

    private void play(String prefix) {
        if(!isEnabled()||volume.getInt()==0||!follower.active||minecraft.getSoundHandler()==null)return;
        long now=System.currentTimeMillis();if(voice!=null&&(now-voiceStarted<500||minecraft.getSoundHandler().isSoundPlaying(voice)))return;
        String event;
        switch(prefix){case "dying/":event="dying";break;case "gettingDamaged/":event="hurt";break;case "attacking/":event="attacking";break;case "eating/vegan/":event="eating_vegan";break;case "eating/brutal/":event="eating_other";break;case "blockBreaking/BreakingBed/":event="breaking_bed";break;default:event="breaking_other";}
        voice=new Voice(event);voiceStarted=now;minecraft.getSoundHandler().playSound(voice);
    }
    public CompanionFollower follower(){return follower;}
    public EntityOtherPlayerMP companion(){return companion;}
    public boolean speaking(){return voice!=null&&minecraft.getSoundHandler()!=null&&minecraft.getSoundHandler().isSoundPlaying(voice);}
    private void stopVoice(){if(voice!=null){voice.stop();if(minecraft.getSoundHandler()!=null)minecraft.getSoundHandler().stopSound(voice);voice=null;}}
    public void clear(){stopVoice();follower.clear();companion=null;companionWorld=null;digging.clear();breakingBed=null;breakingOther=false;previousHealth=-1;wasDead=wasEating=false;}
    @Override protected void onDisable(){clear();}
    final class Voice extends MovingSound {
        Voice(String event){super(new ResourceLocation("vibe","girlfriend."+event));attenuationType=AttenuationType.LINEAR;repeat=false;pitch=1;update();}
        @Override public void update(){
            if(!isEnabled()||!follower.active||minecraft.theWorld!=companionWorld){donePlaying=true;volume=0;return;}
            xPosF=(float)follower.x;yPosF=(float)(follower.y+1.55*modelScale.getFloat());zPosF=(float)follower.z;volume=GirlfriendModule.this.volume.getFloat()/100;
        }
        void stop(){donePlaying=true;volume=0;}
    }

    private static final class Digging {
        private final C07PacketPlayerDigging.Action action;
        private final BlockPos pos;
        private Digging(C07PacketPlayerDigging.Action action, BlockPos pos) { this.action = action; this.pos = pos; }
    }
}
