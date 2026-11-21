package dev.vibe.ui.render.esp;

import dev.vibe.Vibe;
import dev.vibe.friend.FriendManager;
import dev.vibe.module.impl.visual.Esp2DSettings;
import dev.vibe.module.impl.visual.EspModule;
import dev.vibe.path.PathTestFixture;
import dev.vibe.setting.Setting;
import dev.vibe.target.TargetManager;
import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.world.World;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class EspAppearanceTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void inheritedTargetsOverrideOnlyRgbAndCanReturnExactlyToPlayerDefaults(){
        EspModule esp=new EspModule();esp.getEditProfile().setValue("Targets");
        assertTrue(esp.getTargetsProfile().getOverrideColors().isVisible());
        assertEquals(0xFFFF0000,esp.getTargetsProfile().getOverrideColor().getArgb());
        esp.getOutlineColor().setValue(0x9A1256AB);esp.getFillColor().setValue(0x23123456);
        esp.getTargetsProfile().getOverrideColor().setValue(0x04EE1122);
        EspModule.Style target=esp.getPreviewStyle(2);
        assertEquals(0x9AEE1122,target.getOutline());assertEquals(0x23EE1122,target.getFill());assertEquals(0,esp.resolvedProfile(2));
        esp.getTargetsProfile().getOverrideColors().setEnabled(false);
        assertFalse(esp.getTargetsProfile().getOverrideColor().isVisible());
        assertEquals(esp.getPreviewStyle(0).getOutline(),esp.getPreviewStyle(2).getOutline());
        assertEquals(esp.getPreviewStyle(0).getFill(),esp.getPreviewStyle(2).getFill());
        esp.getTargetsProfile().getUsePlayerDefaults().setEnabled(false);assertFalse(esp.getTargetsProfile().getOverrideColors().isVisible());assertEquals(2,esp.resolvedProfile(2));
    }
    @Test public void profilesAreIndependentAndAllColorsSupportContext(){
        EspModule esp=new EspModule();esp.getModes().toggle("2D");esp.getModes().toggle("Chams");
        assertEquals(0,esp.resolvedProfile(1));
        esp.getFriendsProfile().getUsePlayerDefaults().setValue(false);
        esp.getEditProfile().setValue("Friends");
        assertEquals(1,esp.resolvedProfile(1));assertTrue(esp.get2D(1).name.scale.isVisible());assertFalse(esp.get2D().name.scale.isVisible());
        esp.get2D(1).name.scale.setValue(2D);assertEquals(1,esp.get2D().name.scale.getDouble(),0);
        esp.getChams(1,true).getColor().getEntityMode().setValue("Team");
        dev.vibe.setting.ColorSetting color=esp.getChams(1,true).getColor();
        color.setValue(0x80112233);assertEquals(0x8055AAFF,color.resolve(0xFF55AAFF,false));
        assertEquals(0x80112233,color.resolve(0,false));
        color.getHurtColor().setValue(0xCCFF0000);color.getHurtOverride().setValue(true);
        assertEquals(0xCCFF0000,color.resolve(0xFF55AAFF,true));
        assertNotEquals(color,esp.getChams(2,true).getColor());
        for(int p=0;p<3;p++)for(Esp2DSettings.Element e:esp.get2D(p).elements)assertFalse(e.backgroundEnabled.isEnabled());
        assertTrue(EspLayout.scale(2,1)<EspLayout.scale(10,1));
        assertTrue(EspLayout.scale(20,1)>EspLayout.scale(10,1));
        assertTrue(EspLayout.scale(20,1)<EspLayout.scale(10,1)*1.1F);
    }
    @Test public void inheritedFriendTintPreservesOpacityAndDoesNotChangeTargets() {
        EspModule esp = new EspModule();
        esp.getEditProfile().setValue("Friends");
        esp.getOutlineColor().setValue(0x80335577);
        esp.getFriendsProfile().getOverrideColor().setValue(0x0055FF33);
        assertTrue(esp.getFriendsProfile().getOverrideColors().isVisible());
        assertEquals(0xFF55FF33, esp.inheritedColorOverride(1, 0, false));
        assertEquals(0x8055FF33, esp.getPreviewStyle(1).getOutline());
        assertEquals(0x80FF0000, esp.getPreviewStyle(2).getOutline());
        esp.getFriendsProfile().getOverrideColors().setEnabled(false);
        assertEquals(esp.getPreviewStyle(0).getOutline(), esp.getPreviewStyle(1).getOutline());
    }
    @Test public void friendOverrideUsesTheRealRosterAndExcludesTheLocalProfile() throws Exception {
        try (PathTestFixture f = new PathTestFixture()) {
            PathTestFixture.Player teammate = f.addPlayer(2, "Teammate", 2, 64, 1);
            PathTestFixture.Player friend = f.addPlayer(3, "Friend", 3, 64, 1);
            PathTestFixture.Player target = f.addPlayer(4, "Target", 4, 64, 1);
            PathTestFixture.Player localCopy = f.addPlayer(5, f.player.getName(), 5, 64, 1);
            UUID localId = UUID.randomUUID();
            PathTestFixture.set(Entity.class, f.player, "entityUniqueID", localId);
            PathTestFixture.set(Entity.class, localCopy, "entityUniqueID", localId);
            Scoreboard scoreboard = new Scoreboard();
            PathTestFixture.set(World.class, f.world, "worldScoreboard", scoreboard);
            scoreboard.createTeam("team");
            for (Entity player : Arrays.asList(f.player, friend, teammate)) scoreboard.addPlayerToTeam(player.getName(), "team");
            assertTrue(f.filters.isTeammate(teammate));

            FriendManager friends = new FriendManager(temporary.getRoot());
            TargetManager targets = new TargetManager(temporary.getRoot());
            PathTestFixture.set(Vibe.class, f.vibe, "friendManager", friends);
            PathTestFixture.set(Vibe.class, f.vibe, "targetManager", targets);
            friends.add(friend.getName(), teammate.getName());
            friends.add(f.player.getName(), null);
            targets.add(target.getName());
            targets.add(f.player.getName());

            EspModule esp = new EspModule();
            assertEquals(1, esp.profileFor(friend));
            assertEquals(esp.getFriendColor().getArgb(), esp.getStyleFor(friend).getOutline());
            for (PathTestFixture.Player ordinary : Arrays.asList(teammate, f.player, localCopy)) {
                assertEquals(0, esp.profileFor(ordinary));
                assertEquals(esp.getPreviewStyle(0).getOutline(), esp.getStyleFor(ordinary).getOutline());
            }
            assertEquals(2, esp.profileFor(target));
        }
    }
    @Test public void disabledModesElementsAndInheritedTextHideDetails() {
        EspModule esp=new EspModule();Esp2DSettings s=esp.get2D();
        assertFalse(s.name.scale.isVisible());esp.getModes().toggle("2D");assertTrue(s.name.scale.isVisible());
        s.name.backgroundEnabled.setValue(true);
        assertFalse(s.name.backgroundEnabled.isVisible());assertFalse(s.name.background.isVisible());
        assertTrue(s.healthBar.backgroundEnabled.isVisible());
        assertFalse(s.name.text.font.isVisible());s.name.useDefaultText.setValue(false);assertTrue(s.name.text.font.isVisible());
        s.name.enabled.setValue(false);assertFalse(s.name.scale.isVisible());assertFalse(s.name.text.font.isVisible());
        assertFalse(esp.getSkeletal().getColor().isVisible());esp.getModes().toggle("Skeletal");assertTrue(esp.getSkeletal().getColor().isVisible());
        assertFalse(s.global.count.isVisible());s.box.color.mode.setValue("Global Gradient");assertTrue(s.global.count.isVisible());
        s.box.enabled.setValue(false);assertFalse(s.global.count.isVisible());
        for(Esp2DSettings.Element e:s.elements)e.enabled.setValue(false);assertFalse(s.text.font.isVisible());
        Set<String> keys=new HashSet<String>();for(Setting<?> setting:esp.getSettings())assertTrue("Duplicate persisted setting "+setting.getRawName(),keys.add(setting.getRawName()));
    }
    @Test public void gradientSortsStopsHonorsAlphaAndDirectionAndLoopsContinuously() {
        EspModule esp=new EspModule();Esp2DSettings.Gradient g=esp.get2D().global;
        g.direction.setValue(0D);g.count.setValue(3D);
        g.positions.get(0).setValue(1D);g.colors.get(0).setValue(0x000000FF);
        g.positions.get(1).setValue(0D);g.colors.get(1).setValue(0xFFFF0000);
        g.positions.get(2).setValue(.5);g.colors.get(2).setValue(0x8000FF00);
        EspGradient gradient=new EspGradient(g,0);
        assertEquals(0xFFFF0000,gradient.sample(0,50,100,100));assertEquals(0x000000FF,gradient.sample(100,50,100,100));
        assertEquals(0x8000FF00,gradient.sample(50,50,100,100));
        g.direction.setValue(90D);assertEquals(0x8000FF00,new EspGradient(g,0).sample(15,50,100,100));
        assertEquals(EspGradient.triangle(-.001F),EspGradient.triangle(.001F),.00001);
        g.speed.setValue(.2);assertEquals(new EspGradient(g,0).sample(25,50,100,100),new EspGradient(g,10).sample(25,50,100,100));
    }
}
