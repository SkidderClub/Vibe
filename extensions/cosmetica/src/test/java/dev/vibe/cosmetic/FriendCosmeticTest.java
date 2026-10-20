package dev.vibe.cosmetic;

import dev.vibe.friend.FriendManager;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class FriendCosmeticTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void eachFriendCyclesAllProfilesAndPersistsIndependently() throws Exception {
        File folder = temporary.newFolder();
        CosmeticPresetManager presets = new CosmeticPresetManager(folder);
        String first = presets.getSelectedId(), second = presets.create("Preset 2").getId();
        assertEquals(first,presets.cycleId("",false));
        assertEquals(second,presets.cycleId(first,false));
        assertEquals("",presets.cycleId(second,false));
        assertEquals(second,presets.cycleId("",true));
        FriendManager friends = new FriendManager(folder);
        friends.add("Alice","Ally"); friends.add("Bob","Buddy");
        friends.setCosmeticPreset("Alice",first); friends.setCosmeticPreset("Bob",second);
        friends = new FriendManager(folder); presets = new CosmeticPresetManager(folder);
        assertEquals(first,presets.resolveFriend("ALICE",friends).getId());
        assertEquals(second,presets.resolveFriend("Bob",friends).getId());
        assertNull(presets.resolveFriend("Ally",friends));
        friends.setCosmeticPreset("Alice","");
        assertNull(presets.resolveFriend("Alice",friends));
        assertEquals(second,presets.resolveFriend("Bob",friends).getId());
        presets.delete(first);
        assertNull(presets.findById(first));
    }
}
