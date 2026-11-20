package dev.vibe.aiaura;

import java.io.File;
import java.nio.file.Files;
import java.util.Random;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Nyx 26.2's AimModel self-check, carried over with the model. */
public class AimModelTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Before @After public void forget() {
        AimModel.detach();
        AimModel.clear();
    }

    @Test public void turnsAtTheTaughtPaceAndOvershootsOnlyWhereSeen() {
        Random r = new Random(3);
        // taught turns of half of what is left, a quarter of them slow starts: it turns about half of what is left
        for (int i = 0; i < 2000; i++) {
            float off = (float) Math.expm1(r.nextFloat() * 4.5f);
            AimModel.pace(off, off * (r.nextInt(4) == 0 ? 0.1f : 0.5f) * (0.8f + r.nextFloat() * 0.4f), off * 0.05f);
        }
        for (float off : new float[]{0.5f, 3f, 20f, 80f, 170f}) {
            float mean = 0f;
            for (int i = 0; i < 200; i++) mean += AimModel.turn(off, r.nextFloat())[0] / 200f;
            assertTrue("not the hand's speed from " + off + ": " + mean, mean > 0.4f * off && mean < 0.65f * off);
            assertTrue("the brisker turn is the slower", AimModel.turn(off, 0.99f)[0] >= AimModel.turn(off, 0f)[0]);
        }
        for (int i = 0; i < 40; i++) AimModel.overshot(0f, 0f, 1);
        AimModel.overshot(5f, -1f, 1);
        assertEquals("an overshoot asked for, and one that stayed on them given", 5f, AimModel.overshoot(1, true)[0], 0f);
        assertEquals("an overshoot made where none was seen", 0f, AimModel.overshoot(0, true)[0], 0f);
    }

    @Test public void clickingIsReplayedInRunsOfYourOwnGaps() {
        for (int burst = 0; burst < 60; burst++) {
            for (int i = 0; i < 10; i++) AimModel.click(i % 2 == 0 ? 40f : 110f);
            AimModel.click(0f);                          // the end of a burst
        }
        assertTrue(AimModel.rhythm());
        assertEquals(1000f * 2 / (40 + 110), AimModel.cps(), 0.01f);
        float previous = AimModel.nextGap();
        int alternated = 0;
        for (int i = 0; i < 500; i++) {
            float gap = AimModel.nextGap();
            assertTrue("only gaps you made", gap == 40f || gap == 110f);
            if (gap != previous) alternated++;
            previous = gap;
        }
        // drawn one at a time about half would alternate; played back in your order nearly all do
        assertTrue("played back in runs, not drawn one at a time: " + alternated, alternated > 400);
    }

    @Test public void yourTurnsArePlayedBackFromTheTickOfYoursMostAlikeMirroredToo() throws Exception {
        assertEquals("nothing learned, no turn", 0f, AimModel.act(new float[7])[0], 0f);
        // you, 10 degrees right of them, turn 5 back left; dead on them, you hold still
        for (int i = 0; i < AimModel.STEPS / 2; i++) {
            AimModel.step(new float[]{10f, 0f, 0f, 0f, 0f, 0f, 5f, -5f, 0f});
            AimModel.step(new float[]{0f, 0f, 0f, 0f, 0f, 0f, 5f, 0f, 0f});
        }
        assertTrue(AimModel.cloned());
        assertEquals(-5f, AimModel.act(new float[]{9f, 0f, 0f, 0f, 0f, 0f, 5f})[0], 0f);
        assertEquals("10 left of them is the same, mirrored", 5f, AimModel.act(new float[]{-9f, 0f, 0f, 0f, 0f, 0f, 5f})[0], 0f);
        assertEquals(0f, AimModel.act(new float[]{1f, 0f, 0f, 0f, 0f, 0f, 5f})[0], 0f);
        // a tick's numbers: the aim 10 right of them, they went round 2 a tick, you turned 3 right a tick before
        float[] now = {14f, 0f, 4f, 0f, 5f}, before = {11f, 0f, 2f, 0f, 5f}, two = {8f, 0f, 0f, 0f, 5f}, three = {5f, 0f, -2f, 0f, 5f};
        assertArrayEquals(new float[]{9f, 0f, 2f, 2f, 3f, 0f, 5f, 3f, 0f}, AimModel.sample(now, before, two, three), 1e-4f);
        float[] ahead = AimModel.seen(0f, 0f, 0, 1.62, 0, 0, 0.9, 3, 0.4);
        assertEquals("straight ahead, along +z, is yaw 0", 0f, ahead[2], 1e-4f);
        assertEquals("their middle below your eyes is down: pitch up from 0", 13.5f, ahead[3], 0.1f);
    }

    @Test public void recordedFightsTeachYourAimButNotTheOnesTheAuraHadAPartIn() throws Exception {
        File csv = temp.newFile("fights.csv");
        StringBuilder rows = new StringBuilder("tick,ms,yaw,pitch,sx,sy,sz,ex,ey,ez,dist,swings,attacks,hurt,resting,ownHurt,ground,id,fight,aura,presses,clicks\n");
        for (int t = 0; t < 10; t++) {                  // a fight of yours: you swing every third tick and turn 2 a tick
            rows.append(t).append(",0,").append(2 * t).append(",5,0,64,0,0,64,3,2.2,").append(t % 3 == 0 ? 1 : 0).append(",0,0,0,0,1,7,1,0,0,0\n");
        }
        for (int t = 10; t < 20; t++) {                 // one the aura was on
            rows.append(t).append(",0,0,5,0,64,0,0,64,3,2.2,1,0,0,0,0,1,7,2,").append(t == 12 ? 1 : 0).append(",0,0\n");
        }
        Files.write(csv.toPath(), rows.toString().getBytes());
        assertEquals("the ticks of yours with three before them", 7, AimModel.learnFrom(csv));
        assertEquals(7, AimModel.steps());
        assertEquals("your swings, 3 ticks apart - none of the aura's", 3, AimModel.swings());
        assertEquals(3, AimModel.nextSwing());
        // a draw: with so few, their mirror images are among the nearest too, just less likely
        int made = 0;
        for (int i = 0; i < 200; i++) if (AimModel.act(new float[]{2f, -8.5f, 0f, 0f, 2f, 0f, 7.6f})[0] == 2f) made++;
        assertTrue("and what was learned is mostly the turn you made: " + made + " of 200", made > 120);
    }

    @Test public void aFileFromBeforeClickingWasLearnedStillLoads() throws Exception {
        File file = new File(temp.newFolder(), "aim.bin");
        try (java.io.DataOutputStream out = new java.io.DataOutputStream(new java.io.FileOutputStream(file))) {
            out.writeInt(1);
            out.writeInt(1);
            out.writeFloat(0.25f);
            out.writeFloat(-0.5f);
            out.writeInt(0);
            out.writeInt(0);
        }
        AimModel.load(file);
        assertEquals(1, AimModel.size());
        assertEquals(0, AimModel.clicks());
    }

    @Test public void savedModelComesBackWholeAndABrokenFileIsNothingLearned() throws Exception {
        File file = new File(temp.newFolder(), "aim.bin");
        AimModel.load(file);
        assertEquals(0, AimModel.size());
        for (int i = 0; i < AimModel.ENOUGH; i++) AimModel.add(0.25f, -0.5f);
        AimModel.pace(10f, 5f, 0.5f);
        AimModel.overshot(3f, 1f, 2);
        AimModel.click(83f);
        AimModel.click(0f);
        AimModel.step(new float[]{1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f});
        AimModel.swing(-1);
        AimModel.swing(3);
        AimModel.save();
        AimModel.load(file);
        assertEquals(1, AimModel.steps());
        assertEquals(1, AimModel.swings());
        assertEquals(3, AimModel.nextSwing());
        assertTrue(AimModel.ready());
        assertEquals(1, AimModel.turns());
        assertEquals(1, AimModel.overshoots());
        assertEquals(1, AimModel.clicks());
        assertEquals(83f, AimModel.nextGap(), 0f);
        assertArrayEquals(new float[]{0.25f, -0.5f}, AimModel.pick(), 0f);
        Files.write(file.toPath(), new byte[]{0, 0, 0, 1, 0, 0});    // cut off mid-write
        AimModel.load(file);
        assertEquals(0, AimModel.size());
    }
}
