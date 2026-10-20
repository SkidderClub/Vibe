package dev.vibe.phone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Fixed-step, frame-rate-independent game in the phone's 192 x 252 play field. */
public final class FlappyGame {
    public static final float WIDTH = 192, HEIGHT = 252, BIRD_X = 46, RADIUS = 7, GAP = 79, PIPE_WIDTH = 27;
    public enum Phase { READY, PLAYING, OVER }
    public static final class Pipe {
        public float x;
        public final float gapY;
        private boolean scored;
        private Pipe(float x, float gapY) { this.x = x; this.gapY = gapY; }
    }
    private final Random random;
    private final List<Pipe> pipes = new ArrayList<Pipe>();
    private Phase phase;
    private float birdY, velocity;
    private double accumulated;
    private int score;

    public FlappyGame() { this(new Random()); }
    public FlappyGame(Random random) { this.random = random; reset(); }
    public void reset() {
        phase = Phase.READY; birdY = 110; velocity = 0; score = 0; accumulated = 0;
        pipes.clear();
        for (int i = 0; i < 3; i++) pipes.add(pipe(225 + i * 108));
    }
    private Pipe pipe(float x) { return new Pipe(x, 64 + random.nextInt(110)); }
    public void flap() {
        if (phase == Phase.OVER) reset();
        phase = Phase.PLAYING;
        velocity = -125;
    }
    public void advance(double seconds) {
        if (phase != Phase.PLAYING || !Double.isFinite(seconds) || seconds <= 0) return;
        accumulated += Math.min(.10, seconds);
        while (accumulated >= 1.0 / 120 && phase == Phase.PLAYING) {
            accumulated -= 1.0 / 120;
            step(1F / 120);
        }
    }
    private void step(float dt) {
        velocity += 365 * dt;
        birdY += velocity * dt;
        for (Pipe pipe : pipes) {
            pipe.x -= 60 * dt;
            if (!pipe.scored && pipe.x + PIPE_WIDTH < BIRD_X) { pipe.scored = true; score++; }
            if (pipe.x < BIRD_X + RADIUS && pipe.x + PIPE_WIDTH > BIRD_X - RADIUS
                    && (birdY - RADIUS < pipe.gapY - GAP / 2 || birdY + RADIUS > pipe.gapY + GAP / 2)) phase = Phase.OVER;
        }
        if (birdY < RADIUS || birdY > HEIGHT - 20 - RADIUS) {
            birdY = Math.max(RADIUS, Math.min(HEIGHT - 20 - RADIUS, birdY)); phase = Phase.OVER;
        }
        if (pipes.get(0).x < -PIPE_WIDTH) {
            pipes.remove(0); pipes.add(pipe(pipes.get(pipes.size() - 1).x + 108));
        }
    }
    public Phase getPhase() { return phase; }
    public float getBirdY() { return birdY; }
    public float getVelocity() { return velocity; }
    public int getScore() { return score; }
    public List<Pipe> getPipes() { return Collections.unmodifiableList(pipes); }
}
