package dev.vibe.game.gta8;

import dev.vibe.game.gta8.Gta8Ped.State;
import dev.vibe.game.gta8.Gta8World.WalkLink;
import dev.vibe.game.gta8.Gta8World.WalkNode;
import java.util.List;
import java.util.Random;

/** Sidewalk pedestrians: wandering, signal-controlled crossings, idling, panic, cowering and witnessing. */
public final class Gta8Pedestrians {
    static final int[] SKIN = {0xF1D0B5, 0xE4B894, 0xD2A07A, 0xB67E58, 0x8E5E3E, 0x6A452C, 0x4A3020};
    static final int[] HAIR = {0x15110E, 0x2E2118, 0x4E3624, 0x7A5A3A, 0xB8986A, 0x8E8A84, 0x6A2E1A};
    static final int[] TOPS = {0x2A3448, 0xE8E6E0, 0x7A7E84, 0x1C1C1E, 0x4A5A36, 0x6E2230, 0xC8B89A, 0x8AA6C8, 0xD8A0A8, 0xC8A040, 0x3A6A8A, 0xA84A2E, 0x5A4A6E};
    static final int[] PANTS = {0x3A4A66, 0x2A3246, 0x1C1C1E, 0xB8A884, 0x6E6E6A, 0x4A3E32, 0x5A6A7A};
    static final int[] SHOES = {0xE8E8E4, 0x1A1A1A, 0x5A3E28, 0x2A2A2E, 0xB84A3A};
    private final Gta8Game game;
    private final Random random;
    public int target = 70;

    Gta8Pedestrians(Gta8Game game, Random random) { this.game = game; this.random = random; }

    // ------------------------------------------------------------------ appearance
    Gta8Ped dress(Gta8Ped p, Gta8World.District district) {
        int[] c = p.palette;
        c[0] = SKIN[random.nextInt(SKIN.length)];
        c[4] = HAIR[random.nextInt(HAIR.length)];
        if (c[0] == SKIN[5] || c[0] == SKIN[6]) c[4] = HAIR[random.nextInt(2)];
        double r = random.nextDouble();
        if (p.kind == Gta8Ped.Kind.COP) {
            p.outfit = 2; c[1] = 0x1E2A3E; c[2] = 0x1A2232; c[3] = 0x111111; c[5] = 0x151A24; c[6] = 0xC8A040; p.hat = true;
            return p;
        }
        if (p.kind == Gta8Ped.Kind.GANG) {
            p.outfit = 4; c[1] = random.nextBoolean() ? 0x2E5A2E : 0x6A2A7A; c[2] = 0x2A3246; c[3] = 0xE8E8E4; c[5] = c[1]; p.hat = random.nextBoolean();
            return p;
        }
        if (p.kind == Gta8Ped.Kind.CLERK) { p.outfit = 6; c[1] = 0xB02A22; c[2] = 0x1C1C1E; c[3] = 0x1A1A1A; c[5] = 0xE8E6E0; return p; }
        boolean business = district == Gta8World.District.DOWNTOWN ? r < .45 : r < .12;
        boolean beach = district == Gta8World.District.COAST && random.nextDouble() < .45;
        boolean sport = !business && !beach && random.nextDouble() < .12;
        if (business) {
            p.outfit = 1;
            c[1] = random.nextBoolean() ? 0x1E2230 : random.nextBoolean() ? 0x3A3E44 : 0x2A2E38;
            c[2] = c[1]; c[3] = random.nextBoolean() ? 0x1A1A1A : 0x3E2A1E; c[5] = random.nextBoolean() ? 0xE8E8EE : 0xB8C8E0;
            p.bag = random.nextDouble() < .4;
        } else if (beach) {
            p.outfit = 5; c[1] = TOPS[random.nextInt(TOPS.length)]; c[2] = TOPS[random.nextInt(TOPS.length)]; c[3] = c[0];
            p.glasses = random.nextDouble() < .6; p.hat = random.nextDouble() < .3;
        } else if (sport) {
            p.outfit = 3; c[1] = TOPS[random.nextInt(TOPS.length)]; c[2] = 0x1C1C1E; c[3] = 0xE8E8E4; p.speedPreference = 2.6;
        } else {
            p.outfit = 0; c[1] = TOPS[random.nextInt(TOPS.length)]; c[2] = PANTS[random.nextInt(PANTS.length)]; c[3] = SHOES[random.nextInt(SHOES.length)];
            c[5] = TOPS[random.nextInt(TOPS.length)];
            p.hat = random.nextDouble() < .12; p.glasses = random.nextDouble() < .15; p.bag = random.nextDouble() < .2;
        }
        p.longHair = p.body == 1 && random.nextDouble() < .75;
        c[6] = c[1]; c[7] = 0x2A2A2A;
        return p;
    }

    // ------------------------------------------------------------------ population
    void populate() {
        Gta8Ped player = game.player;
        int count = 0;
        for (int i = game.peds.size() - 1; i >= 0; i--) {
            Gta8Ped p = game.peds.get(i);
            if (p.kind != Gta8Ped.Kind.CIVILIAN || p.vehicle != null) continue;
            double d = Math.hypot(p.x - player.x, p.z - player.z);
            boolean seen = game.visible(p.x, p.y + 1, p.z, 1);
            if (d > 170 && !seen || d > 260 || p.dead && p.deadTime > 90 && !seen) { game.peds.remove(i); continue; }
            if (!p.dead) count++;
        }
        double density = (1 - .45 * Gta8Math.smooth(.4, 1, game.renderNight())) * (1 - .4 * game.weather.rain);
        if (count >= target * density) return;
        List<WalkNode> nodes = game.world.walkNodes;
        for (int attempt = 0; attempt < 8; attempt++) {
            WalkNode n = nodes.get(random.nextInt(nodes.size()));
            WalkLink link = n.links.get(random.nextInt(n.links.size()));
            if (link.crossing()) continue;
            double t = random.nextDouble();
            double x = n.x + (link.to.x - n.x) * t, z = n.z + (link.to.z - n.z) * t;
            double d = Math.hypot(x - player.x, z - player.z);
            if (d < 35 || d > 135 || d < 75 && game.visible(x, 1, z, 1)) continue;
            Gta8Ped p = game.createCivilian(x, z);
            p.node = n; p.goal = link.to;
            p.lateral = (random.nextDouble() - .5) * 2.4;
            game.peds.add(p);
            return;
        }
    }

    // ------------------------------------------------------------------ behaviour
    void update(Gta8Ped p, double dt) {
        if (p.state == State.RAGDOLL) { ragdoll(p, dt); return; }
        if (p.dead) { p.deadTime += dt; p.fall = Math.min(1, p.fall + dt * 2.2); p.vx = p.vz = 0; return; }
        p.flinch = Math.max(0, p.flinch - dt);
        p.thinkTimer -= dt;
        if (p.witness && p.reportTimer > 0) {
            p.reportTimer -= dt;
            p.phone = 1;
            if (p.reportTimer <= 0) { p.witness = false; game.police.witnessReport(p); }
        }
        double speed = 0, dirX = 0, dirZ = 0;
        switch (p.state) {
            case WANDER: case CROSSING: {
                if (p.goal == null || p.node == null) { reattach(p); break; }
                double ex = p.goal.x - p.node.x, ez = p.goal.z - p.node.z, el = Math.max(.01, Math.hypot(ex, ez));
                double lat = p.state == State.CROSSING ? p.lateral * .3 : p.lateral;
                double tx = p.goal.x + -ez / el * lat, tz = p.goal.z + ex / el * lat;
                double dx = tx - p.x, dz = tz - p.z, d = Math.hypot(dx, dz);
                if (d < .8) { arrive(p); break; }
                dirX = dx / d; dirZ = dz / d;
                speed = p.state == State.CROSSING ? Math.max(1.55, p.speedPreference) : p.speedPreference * (1 + .25 * game.weather.rain);
                break;
            }
            case WAIT_CROSSING: {
                WalkLink link = linkTo(p.node, p.goal);
                if (link == null) { p.state = State.WANDER; break; }
                int walk = game.world.walkState(link.signal, link.crossesNorthSouthRoad, game.time);
                p.waitTimer += dt;
                if (walk == Gta8World.WALK || p.waitTimer > 70) { p.state = State.CROSSING; p.waitTimer = 0; }
                p.yaw = Gta8Math.damp(p.yaw, p.yaw + Gta8Math.angleDelta(p.yaw, Math.toDegrees(Math.atan2(p.goal.x - p.x, -(p.goal.z - p.z)))), 4, dt);
                break;
            }
            case IDLE: {
                p.waitTimer -= dt;
                p.phone = Math.min(1, p.phone + dt * 2);
                if (p.waitTimer <= 0) { p.state = State.WANDER; p.phone = 0; }
                break;
            }
            case FLEE: {
                p.fleeTimer -= dt;
                double dx = p.x - p.fleeX, dz = p.z - p.fleeZ, d = Math.max(.1, Math.hypot(dx, dz));
                dirX = dx / d; dirZ = dz / d;
                double wobble = Math.sin(game.time * 1.3 + p.id) * .35;
                double c = Math.cos(wobble), s = Math.sin(wobble);
                double rx = dirX * c - dirZ * s, rz = dirX * s + dirZ * c;
                dirX = rx; dirZ = rz;
                speed = p.body == 1 ? 5.0 : 5.6;
                if (p.fleeTimer <= 0 && d > 30) { p.state = State.WANDER; p.node = null; }
                break;
            }
            case COWER: {
                p.fleeTimer -= dt;
                p.cower = Math.min(1, p.cower + dt * 3);
                p.crouch = p.cower;
                if (p.fleeTimer <= 0) { p.state = State.FLEE; p.fleeTimer = 6; p.cower = 0; p.crouch = 0; }
                break;
            }
            case HANDS_UP: {
                p.handsUp = Math.min(1, p.handsUp + dt * 3);
                p.fleeTimer -= dt;
                if (p.fleeTimer <= 0 && p.kind != Gta8Ped.Kind.CLERK) { p.state = State.FLEE; p.handsUp = 0; p.fleeTimer = 10; }
                break;
            }
            case WORK: default: break;
        }
        if (p.state != State.HANDS_UP) p.handsUp = Math.max(0, p.handsUp - dt * 2);
        if (p.state != State.COWER) p.cower = Math.max(0, p.cower - dt * 2);
        game.walk(p, dirX, dirZ, speed, dt, true);
    }

    private void arrive(Gta8Ped p) {
        WalkNode previous = p.node;
        p.node = p.goal;
        List<WalkLink> links = p.node.links;
        WalkLink choice = null;
        for (int i = 0; i < 4 && choice == null; i++) {
            WalkLink l = links.get(random.nextInt(links.size()));
            if (l.to != previous || links.size() == 1) choice = l;
        }
        if (choice == null) choice = links.get(0);
        p.goal = choice.to;
        p.state = State.WANDER;
        if (choice.crossing()) {
            int walk = game.world.walkState(choice.signal, choice.crossesNorthSouthRoad, game.time);
            p.state = walk == Gta8World.WALK ? State.CROSSING : State.WAIT_CROSSING;
            p.waitTimer = 0;
        } else if (random.nextDouble() < .07) {
            p.state = State.IDLE;
            p.waitTimer = 3 + random.nextDouble() * 7;
        }
    }
    private static WalkLink linkTo(WalkNode from, WalkNode to) {
        if (from == null) return null;
        for (WalkLink l : from.links) if (l.to == to) return l;
        return null;
    }
    private void reattach(Gta8Ped p) {
        WalkNode n = game.world.nearestWalkNode(p.x, p.z);
        p.node = n;
        WalkLink l = n.links.get(random.nextInt(n.links.size()));
        p.goal = l.crossing() ? n : l.to;
        if (p.goal == n) for (WalkLink k : n.links) if (!k.crossing()) { p.goal = k.to; break; }
        p.state = State.WANDER;
    }

    /** Panic: nearby civilians run from (x, z); some freeze and cower; witnesses call the police. */
    void alarm(double x, double z, double radius, boolean crime) {
        for (Gta8Ped p : game.peds) {
            if (p.dead || p.kind == Gta8Ped.Kind.PLAYER || p.kind == Gta8Ped.Kind.COP || p.vehicle != null && p.state == State.DRIVE) {
                if (p.vehicle != null && p.vehicle.ai != null && Math.hypot(p.x - x, p.z - z) < radius * .6) p.vehicle.ai.panic = true;
                continue;
            }
            double d = Math.hypot(p.x - x, p.z - z);
            if (d > radius) continue;
            if (p.kind == Gta8Ped.Kind.GANG) { if (d < radius * .5) { p.provoked = true; p.state = State.ATTACK; } continue; }
            if (p.kind == Gta8Ped.Kind.CLERK) { p.state = State.COWER; p.fleeTimer = 20; continue; }
            p.fleeX = x; p.fleeZ = z;
            if (d < 9 && random.nextDouble() < .3) { p.state = State.COWER; p.fleeTimer = 3 + random.nextDouble() * 4; }
            else if (p.state != State.FLEE) { p.state = State.FLEE; p.fleeTimer = 8 + random.nextDouble() * 10; }
            else p.fleeTimer = Math.max(p.fleeTimer, 6);
            if (crime && !p.witness && random.nextDouble() < .35 && game.canSee(p, game.player)) { p.witness = true; p.reportTimer = 3 + random.nextDouble() * 5; }
        }
    }

    /** Tumbling body after a vehicle impact or explosion, then either death or getting up. */
    void ragdoll(Gta8Ped p, double dt) {
        p.vy -= 9.81 * dt;
        double nx = p.x + p.vx * dt, nz = p.z + p.vz * dt;
        if (!game.world.blocked(nx, p.y + .3, p.z, .25, .5)) p.x = nx; else p.vx *= -.3;
        if (!game.world.blocked(p.x, p.y + .3, nz, .25, .5)) p.z = nz; else p.vz *= -.3;
        p.y += p.vy * dt;
        double ground = game.world.support(p.x, p.z, p.y + .5, .2);
        p.fall = Math.min(1, p.fall + dt * 3);
        p.tumbleX += p.tumbleSpin * dt * 60;
        if (p.y <= ground) {
            p.y = ground;
            if (p.vy < -6) p.damage((-p.vy - 6) * 12);
            p.vy = Math.abs(p.vy) > 2 ? -p.vy * .2 : 0;
            double friction = Math.exp(-dt * 6);
            p.vx *= friction; p.vz *= friction; p.tumbleSpin *= friction;
            p.grounded = true;
        }
        if (p.grounded && Math.hypot(p.vx, p.vz) < .4 && Math.abs(p.vy) < .5) {
            p.tumbleSpin = 0;
            if (p.dead) { p.state = State.DEAD; return; }
            p.waitTimer += dt;
            if (p.waitTimer > 1.6) {
                p.fall = Math.max(0, p.fall - dt * 2.5);
                if (p.fall <= 0) { p.waitTimer = 0; p.state = p.kind == Gta8Ped.Kind.COP ? State.CHASE : State.FLEE; p.fleeX = p.x - p.vx; p.fleeZ = p.z - p.vz; p.fleeTimer = 10; p.tumbleX = 0; }
            }
        }
    }
}
