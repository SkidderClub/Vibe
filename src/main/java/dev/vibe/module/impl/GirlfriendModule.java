package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import javazoom.jl.player.Player;
import net.minecraft.block.BlockBed;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Items;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.BlockPos;
import org.lwjgl.input.Keyboard;

/** Plays one non-overlapping local MP3 reaction for selected player events. */
public final class GirlfriendModule extends Module {
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final Random random = new Random();
    private final List<String> sounds = new ArrayList<String>();
    private final AtomicBoolean playing = new AtomicBoolean();
    private float previousHealth = -1.0F;
    private boolean wasDead;
    private boolean wasEating;
    private final Queue<Digging> digging = new ConcurrentLinkedQueue<Digging>();
    private BlockPos breakingBed;
    private boolean breakingOther;

    public GirlfriendModule() {
        super("Girlfriend", "Play local reactions to player actions", Category.MEME, Keyboard.KEY_NONE);
        loadManifest();
    }

    public void tick() {
        if (!isEnabled() || minecraft.thePlayer == null) {
            previousHealth = -1.0F; wasDead = false; wasEating = false;
            digging.clear(); breakingBed = null; breakingOther = false;
            return;
        }
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
        if (playing.get()) return;
        List<String> pool = new ArrayList<String>();
        for (String sound : sounds) if (sound.startsWith(prefix)) pool.add(sound);
        if (pool.isEmpty()) return;
        final String selected = pool.get(random.nextInt(pool.size()));
        if (!playing.compareAndSet(false, true)) return;
        Thread thread = new Thread(new Runnable() { @Override public void run() {
            try { InputStream input = GirlfriendModule.class.getResourceAsStream("/assets/vibe/girlfriend/" + selected); if (input != null) try { new Player(input).play(); } finally { input.close(); } }
            catch (Throwable ignored) { } finally { playing.set(false); }
        }}, "Vibe-Girlfriend-Audio");
        thread.setDaemon(true); thread.start();
    }

    private void loadManifest() {
        InputStream stream = GirlfriendModule.class.getResourceAsStream("/assets/vibe/girlfriend/presets.txt");
        if (stream == null) return;
        try { BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8")); try { String line; while ((line = reader.readLine()) != null) if (line.endsWith(".mp3")) sounds.add(line.trim()); } finally { reader.close(); } }
        catch (Exception ignored) { }
    }

    private static final class Digging {
        private final C07PacketPlayerDigging.Action action;
        private final BlockPos pos;
        private Digging(C07PacketPlayerDigging.Action action, BlockPos pos) { this.action = action; this.pos = pos; }
    }
}
