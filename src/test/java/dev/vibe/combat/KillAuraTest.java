package dev.vibe.combat;

import dev.vibe.Vibe;
import dev.vibe.friend.FriendManager;
import dev.vibe.module.impl.client.FriendsModule;
import dev.vibe.module.impl.combat.KillAuraModule;
import dev.vibe.module.impl.movement.MoveFixModule;
import dev.vibe.path.PathTestFixture;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.RangeSetting;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class KillAuraTest {
    @Rule public final TemporaryFolder temp = new TemporaryFolder();

    @Test public void reachableEnemyWinsOverLowerHealthEnemyInBlockRange() throws Exception {
        try (ProjectileFixture f = new ProjectileFixture()) {
            KillAuraModule aura = setup(f);
            ProjectileFixture.Player near = f.player("Near", 0.5, 64, 2.5);
            ProjectileFixture.Player far = f.player("Far", 0.5, 64, 5.5);
            far.setHealth(5.0F);
            ((ModeSetting) setting(aura, "Autoblock Mode")).setValue("Fake");
            ((NumberSetting) setting(aura, "Blocking Range")).setValue(6.0D);
            aura.tickStart();
            assertSame(near, aura.getTarget());

            near.isDead = true;
            aura.tickStart();
            assertSame(far, aura.getTarget());
            aura.setEnabled(false);
        }
    }

    @Test public void firstClickWaitsForTheHitRayAndFiresWhenTheAimArrives() throws Exception {
        try (ProjectileFixture f = new ProjectileFixture()) {
            KillAuraModule aura = setup(f);
            ProjectileFixture.Player target = f.player("Enemy", 0.5, 64, 2.5);
            f.self.rotationYaw = 90.0F;
            ((RangeSetting) setting(aura, "Rotation Speed")).setRange(15.0D, 15.0D);
            aura.tickStart();
            KillAuraModule.prepareInputHook();
            assertSame(target, aura.getTarget());
            assertFalse(f.base.mc.gameSettings.keyBindAttack.isPressed());

            boolean attacked = false;
            for (int tick = 0; tick < 6 && !attacked; tick++) {
                aura.tickEnd();
                aura.tickStart();
                KillAuraModule.prepareInputHook();
                attacked = f.base.mc.gameSettings.keyBindAttack.isPressed();
            }
            assertTrue("the first aligned tick queues an attack", attacked);
            assertSame(target, f.base.mc.objectMouseOver.entityHit);
            aura.setEnabled(false);
        }
    }

    @Test public void noFriendsBypassesCombatProtectionWithoutChangingTheRoster() throws Exception {
        try (ProjectileFixture f = new ProjectileFixture()) {
            FriendsModule friends = new FriendsModule();
            f.base.modules.registerDynamic(friends);
            FriendManager roster = new FriendManager(temp.newFolder());
            PathTestFixture.set(Vibe.class, f.base.vibe, "friendManager", roster);
            ProjectileFixture.Player friend = f.player("Friend", 0.5, 64, 2.5);
            assertTrue(roster.add(friend.getName(), null));
            BooleanSetting noFriends = (BooleanSetting) setting(friends, "NoFriends");
            assertFalse(noFriends.isEnabled());
            assertFalse(friends.shouldAttack(friend));
            noFriends.setEnabled(true);
            assertTrue(friends.shouldAttack(friend));
            assertTrue(roster.isFriend(friend));
        }
    }

    private static KillAuraModule setup(ProjectileFixture f) {
        KillAuraModule aura = new KillAuraModule();
        f.base.modules.registerDynamic(new MoveFixModule());
        f.base.modules.registerDynamic(aura);
        f.base.filters.getIgnoreTeammates().setEnabled(false);
        aura.setEnabled(true);
        return aura;
    }

    private static dev.vibe.setting.Setting<?> setting(dev.vibe.module.Module module, String name) {
        return module.getSettings().stream().filter(setting -> setting.getRawName().equals(name))
                .findFirst().orElseThrow(() -> new AssertionError(name));
    }
}
