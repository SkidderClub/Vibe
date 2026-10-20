package dev.vibe.phone;

import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

public class FlappyGameTest {
    @Test public void waitsForInputDiesAndCanRestart() {
        FlappyGame game = new FlappyGame(new Random(8));
        game.advance(10);
        assertEquals(110, game.getBirdY(), 0);
        assertEquals(FlappyGame.Phase.READY, game.getPhase());
        game.flap();
        assertTrue(game.getVelocity() < 0);
        for (int i = 0; i < 100; i++) game.advance(.05);
        assertEquals(FlappyGame.Phase.OVER, game.getPhase());
        game.flap();
        assertEquals(FlappyGame.Phase.PLAYING, game.getPhase());
        assertEquals(0, game.getScore());
    }
    @Test public void physicsIsStableAtDifferentFrameRatesAndLargeStallsAreBounded() {
        FlappyGame slow = new FlappyGame(new Random(4)), fast = new FlappyGame(new Random(4));
        slow.flap(); fast.flap();
        for (int i = 0; i < 15; i++) slow.advance(1D / 30);
        for (int i = 0; i < 60; i++) fast.advance(1D / 120);
        assertEquals(slow.getBirdY(), fast.getBirdY(), .1F);
        assertEquals(slow.getPipes().get(0).x, fast.getPipes().get(0).x, .6F);
        float before = slow.getPipes().get(0).x;
        slow.advance(400);
        assertTrue(before - slow.getPipes().get(0).x <= 6.1F);
    }
}
