package dev.vibe.hud;

import dev.vibe.path.PathTestFixture;
import dev.vibe.event.ClientEvents;
import dev.vibe.module.impl.client.HudModule;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class TargetHudCombatTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    @Test public void multipleAttackedPlayersHaveIndependentTimersAndRespectTheLimit() throws Exception {
        try(PathTestFixture f=new PathTestFixture()) {
            TargetHudSettings settings=new TargetHudSettings(s->s);settings.linger.setValue(2D);
            TargetHudTracker tracker=new TargetHudTracker();
            PathTestFixture.Player a=f.addPlayer(2,"First",2,64,2),b=f.addPlayer(3,"Second",3,64,2);
            tracker.attack(a,1000);tracker.attack(b,2000);
            assertEquals(Arrays.asList(b,a),tracker.resolveAll(f.mc,settings,2500));
            tracker.attack(a,2600);
            assertEquals(Arrays.asList(a,b),tracker.resolveAll(f.mc,settings,3000));
            assertEquals(Collections.singletonList(a),tracker.resolveAll(f.mc,settings,4100));
            settings.maxTargets.setValue(1D);tracker.attack(b,4200);
            assertEquals(Collections.singletonList(b),tracker.resolveAll(f.mc,settings,4300));
            f.world.playerEntities.remove(b);
            assertEquals(Collections.singletonList(a),tracker.resolveAll(f.mc,settings,4350));
            tracker.attack(f.player,4400);
            EntityZombie mob=PathTestFixture.allocate(EntityZombie.class);mob.worldObj=f.world;tracker.attack(mob,4400);
            assertEquals(Collections.singletonList(a),tracker.resolveAll(f.mc,settings,4450));
            f.mc.theWorld=null;assertTrue(tracker.resolveAll(f.mc,settings,4500).isEmpty());
        }
    }
    @Test public void onlyUncancelledOutgoingPlayerAttackEventsRegisterCards() throws Exception {
        try(PathTestFixture f=new PathTestFixture()) {
            HudModule hud=new HudModule();f.modules.registerDynamic(hud);
            hud.getHudElements().setValue(new LinkedHashSet<String>(Collections.singleton(HudManager.TARGET_HUD)));
            PathTestFixture.set(dev.vibe.module.Module.class,hud,"enabled",true);
            HudManager manager=new HudManager(temporary.newFolder());
            PathTestFixture.set(dev.vibe.Vibe.class,f.vibe,"hudManager",manager);
            ClientEvents events=PathTestFixture.allocate(ClientEvents.class);
            PathTestFixture.set(ClientEvents.class,events,"minecraft",f.mc);
            java.lang.reflect.Field field=HudManager.class.getDeclaredField("targetTracker");field.setAccessible(true);
            TargetHudTracker tracker=(TargetHudTracker)field.get(manager);
            PathTestFixture.Player target=f.addPlayer(2,"Other",2,64,2);
            events.onTargetHudAttack(new AttackEntityEvent(target,f.player));
            assertTrue(tracker.resolveAll(f.mc,hud.targetHud,System.currentTimeMillis()).isEmpty());
            // The standalone test loader does not run Forge's @Cancelable transformer.
            AttackEntityEvent cancelled=new AttackEntityEvent(f.player,target) {
                @Override public boolean isCancelable() {return true;}
            };
            cancelled.setCanceled(true);
            events.onTargetHudAttack(cancelled);
            assertTrue(tracker.resolveAll(f.mc,hud.targetHud,System.currentTimeMillis()).isEmpty());
            EntityZombie mob=PathTestFixture.allocate(EntityZombie.class);mob.worldObj=f.world;
            events.onTargetHudAttack(new AttackEntityEvent(f.player,mob));
            assertTrue(tracker.resolveAll(f.mc,hud.targetHud,System.currentTimeMillis()).isEmpty());
            events.onTargetHudAttack(new AttackEntityEvent(f.player,target));
            assertEquals(Collections.singletonList(target),tracker.resolveAll(f.mc,hud.targetHud,System.currentTimeMillis()));
        }
    }
    @Test public void cardsPopInOutAndCanReverseAnExitWithoutSnapping() {
        TargetHudSettings settings=new TargetHudSettings(s->s);settings.animationDuration.setValue(200D);
        TargetHudCards cards=new TargetHudCards();TargetHudData a=data("First",16),b=data("Second",7);
        List<TargetHudCards.Card> first=cards.update(Collections.singletonList(a),settings,42,4,1000);
        assertEquals(0,first.get(0).scale,0);
        first=cards.update(Collections.singletonList(a),settings,42,4,1100);assertEquals(.5F,first.get(0).scale,.001F);
        first=cards.update(Arrays.asList(a,b),settings,42,4,1200);
        assertEquals(1,first.get(0).scale,0);assertEquals(0,first.get(1).scale,0);
        assertEquals(48,first.get(1).offset,0);
        first=cards.update(Arrays.asList(a,b),settings,42,4,1400);assertEquals(1,first.get(1).scale,0);
        cards.update(Collections.singletonList(b),settings,42,4,1500);
        first=cards.update(Collections.singletonList(b),settings,42,4,1600);
        assertEquals(.5F,first.get(0).scale,.001F);assertEquals(24,first.get(1).offset,.001F);
        first=cards.update(Arrays.asList(a,b),settings,42,4,1600);assertEquals(.5F,first.get(0).scale,.001F);
        first=cards.update(Arrays.asList(a,b),settings,42,4,1800);assertEquals(1,first.get(0).scale,0);
        cards.update(Collections.<TargetHudData>emptyList(),settings,42,4,1900);
        assertEquals(2,cards.update(Collections.<TargetHudData>emptyList(),settings,42,4,2000).size());
        assertTrue(cards.update(Collections.<TargetHudData>emptyList(),settings,42,4,2100).isEmpty());
    }
    @Test public void reducingCapacityReflowsCardsAndDisablingAnimationsRemovesClosedCardsImmediately() {
        TargetHudSettings settings=new TargetHudSettings(s->s);settings.animations.setEnabled(false);
        TargetHudCards cards=new TargetHudCards();TargetHudData a=data("First",16),b=data("Second",7);
        List<TargetHudCards.Card> visible=cards.update(Arrays.asList(a,b),settings,42,4,1000);
        assertEquals(90,TargetHudCards.height(visible,42),0);
        visible=cards.update(Collections.singletonList(b),settings,42,1,1001);
        assertEquals(1,visible.size());assertEquals(0,visible.get(0).offset,0);
        assertEquals(42,TargetHudCards.height(visible,42),0);
        assertTrue(cards.update(Collections.<TargetHudData>emptyList(),settings,42,1,1002).isEmpty());
        cards.update(Arrays.asList(a,b),settings,42,4,1100);
        visible=cards.update(Collections.singletonList(a),settings,42,1,1101);
        assertEquals(a.id,visible.get(0).data.id);assertEquals(0,visible.get(0).offset,0);
    }
    private static TargetHudData data(String name,float health) {
        TargetHudData sample=TargetHudData.sample();
        return new TargetHudData(null,name,health,20,0,16,0,0,20,UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),sample.skin);
    }
}
