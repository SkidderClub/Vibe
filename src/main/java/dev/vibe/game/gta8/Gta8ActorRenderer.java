package dev.vibe.game.gta8;

import dev.vibe.game.gta8.Gta8World.Prop;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

/** Vehicles, people, weapons, pickups, markers, the helicopter, particles, decals and light coronas. */
final class Gta8ActorRenderer {
    private final Gta8Renderer r;
    private final Map<Gta8Vehicle.Model, Gta8Mesh> vehicleMeshes = new EnumMap<Gta8Vehicle.Model, Gta8Mesh>(Gta8Vehicle.Model.class);
    private final Map<Gta8Vehicle.Model, Gta8Mesh> glassMeshes = new EnumMap<Gta8Vehicle.Model, Gta8Mesh>(Gta8Vehicle.Model.class);
    private final Gta8Mesh[] bodies = new Gta8Mesh[14];
    private final Gta8Mesh[] accessories = new Gta8Mesh[3];
    private final Map<Gta8Weapon, Gta8Mesh> weapons = new EnumMap<Gta8Weapon, Gta8Mesh>(Gta8Weapon.class);
    private final Map<Integer, Gta8Mesh> fallenProps = new HashMap<Integer, Gta8Mesh>();
    private Gta8Mesh heli, ferris, gondola, marker, health, armor, cash, hands;
    private int streamVbo, decalVbo, decalCount, decalRevision = -1;
    private FloatBuffer stream = floats(4 * 16 * 7000);
    private final FloatBuffer bones = floats(16 * 16), palette = floats(32), groups = floats(12);
    private final float[] model = new float[16], temp = new float[16], temp2 = new float[16], skin = new float[16 * 16], weaponMatrix = new float[16];
    private final double[] tmp3 = new double[3];
    private int streamCount;

    Gta8ActorRenderer(Gta8Renderer renderer) { this.r = renderer; }

    void create() {
        for (Gta8Vehicle.Model m : Gta8Vehicle.Model.values()) {
            Gta8MeshBuilder[] parts = Gta8VehicleModels.build(m);
            vehicleMeshes.put(m, Gta8Mesh.upload(parts[0]));
            glassMeshes.put(m, Gta8Mesh.upload(parts[1]));
        }
        for (int body = 0; body < 2; body++) for (int outfit = 0; outfit < 7; outfit++) bodies[body * 7 + outfit] = Gta8Mesh.upload(Gta8CharacterModels.body(body, outfit));
        for (int i = 0; i < 3; i++) accessories[i] = Gta8Mesh.upload(Gta8CharacterModels.accessory(i));
        for (Gta8Weapon w : Gta8Weapon.values()) if (w != Gta8Weapon.FISTS) weapons.put(w, Gta8Mesh.upload(Gta8CharacterModels.weapon(w)));
        heli = Gta8Mesh.upload(Gta8VehicleModels.helicopter());
        ferris = Gta8Mesh.upload(Gta8VehicleModels.ferris());
        gondola = Gta8Mesh.upload(Gta8VehicleModels.gondola());
        marker = Gta8Mesh.upload(markerMesh());
        Gta8MeshBuilder b = new Gta8MeshBuilder();
        b.ao(1).material(Gta8Materials.PAINT).color(0xF2F2EE); b.box(-.18, -.18, -.18, .18, .18, .18);
        b.material(Gta8Materials.EMISSIVE, Gta8Materials.ALWAYS).color(0x30E060); b.box(-.19, -.05, -.05, .19, .05, .05); b.box(-.05, -.19, -.05, .05, .19, .05); b.box(-.05, -.05, -.19, .05, .05, .19);
        health = Gta8Mesh.upload(b);
        b = new Gta8MeshBuilder();
        b.ao(1).material(Gta8Materials.CLOTH).color(0x2A4A7A); b.rounded(-.22, -.28, -.08, .22, .28, .08, .06, 2);
        b.material(Gta8Materials.EMISSIVE, Gta8Materials.ALWAYS).color(0x3080FF); b.box(-.1, .1, -.09, .1, .15, -.08);
        armor = Gta8Mesh.upload(b);
        b = new Gta8MeshBuilder();
        b.ao(1).material(Gta8Materials.PAINT).color(0x6E9A58); b.box(-.16, -.05, -.08, .16, .05, .08);
        b.color(0xD8D0B0); b.box(-.03, -.052, -.082, .03, .052, .082);
        cash = Gta8Mesh.upload(b);
        hands = Gta8Mesh.upload(handsMesh());
        streamVbo = GL15.glGenBuffers();
        decalVbo = GL15.glGenBuffers();
    }
    void close() {
        for (Gta8Mesh m : vehicleMeshes.values()) if (m != null) m.close();
        for (Gta8Mesh m : glassMeshes.values()) if (m != null) m.close();
        for (Gta8Mesh m : bodies) if (m != null) m.close();
        for (Gta8Mesh m : accessories) if (m != null) m.close();
        for (Gta8Mesh m : weapons.values()) if (m != null) m.close();
        for (Gta8Mesh m : fallenProps.values()) if (m != null) m.close();
        for (Gta8Mesh m : new Gta8Mesh[]{heli, ferris, gondola, marker, health, armor, cash, hands}) if (m != null) m.close();
        if (streamVbo != 0) GL15.glDeleteBuffers(streamVbo);
        if (decalVbo != 0) GL15.glDeleteBuffers(decalVbo);
        streamVbo = decalVbo = 0;
        vehicleMeshes.clear(); glassMeshes.clear(); weapons.clear(); fallenProps.clear();
    }
    void update(Gta8Game game, double dt) { }

    // ------------------------------------------------------------------ lights
    void lights(Gta8Game game, List<double[]> out) {
        double night = r.atmosphere.night, a = game.alpha;
        double cx = game.camera.x, cz = game.camera.z;
        for (Gta8Vehicle v : game.vehicles) {
            double d = Math.hypot(v.x - cx, v.z - cz);
            if (d > 150 || v.destroyed()) continue;
            if (v.lightsOn && d < 110) {
                double fx = v.forwardX(), fz = v.forwardZ();
                double hx = v.worldX(0, v.halfLength() + .1), hz = v.worldZ(0, v.halfLength() + .1);
                out.add(new double[]{hx, v.y + .7, hz, 42, 9 * night + 1, 8.6 * night + 1, 7.6 * night + .9, .82, fx, -.12, fz, .95});
                double tx = v.worldX(0, -v.halfLength() - .2), tz = v.worldZ(0, -v.halfLength() - .2);
                double brake = v.brake > .1 ? 2.2 : .6;
                out.add(new double[]{tx, v.y + .7, tz, 5, brake, .05, .03, -2, 0, -1, 0, 0});
            }
            if (v.siren) {
                boolean red = (int) (v.sirenPhase * 6) % 2 == 0;
                out.add(new double[]{v.x, v.y + v.model.height + .4, v.z, 26, red ? 6 : .2, .2, red ? .3 : 7, -2, 0, -1, 0, 0});
            }
            if (v.fire > 0 || v.burnTimer > 0) out.add(new double[]{v.worldX(0, v.halfLength() - .9), v.y + 1.4, v.worldZ(0, v.halfLength() - .9), 14, 7, 3, .8, -2, 0, -1, 0, 0});
        }
        if (game.flashLight > 0) {
            Gta8Ped p = game.player;
            out.add(new double[]{p.x + p.forwardX() * .7, p.y + 1.4, p.z + p.forwardZ() * .7, 9, 16, 11, 5, -2, 0, -1, 0, 0});
        }
        for (int i = 0; i < game.particles.count; i++) if (game.particles.type[i] == Gta8Particles.FIRE && i % 7 == 0) {
            Gta8Particles pp = game.particles;
            out.add(new double[]{pp.x[i], pp.y[i], pp.z[i], 9, 3, 1.4, .4, -2, 0, -1, 0, 0});
        }
        Gta8Police.Helicopter h = game.police.heli;
        if (h != null && night > .2) {
            double dx = h.spotX - h.x, dz = h.spotZ - h.z, dy = game.world.groundHeight(h.spotX, h.spotZ) - (h.y - 1);
            double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
            out.add(new double[]{h.x, h.y - 1, h.z, 140, 60 * night, 60 * night, 55 * night, .985, dx / l, dy / l, dz / l, .993});
        }
    }

    // ------------------------------------------------------------------ shared uniform helpers
    private void paletteFor(Gta8Ped p) {
        palette.clear();
        double[] rough = {.5, .88, .82, .45, .65, .55, .88, .3};
        for (int i = 0; i < 8; i++) {
            int c = p.palette[i];
            palette.put(srgb(c >> 16)).put(srgb(c >> 8)).put(srgb(c)).put((float) rough[i]);
        }
        palette.flip();
    }
    private void paletteFor(Gta8Vehicle v) {
        palette.clear();
        palette.put(srgb(v.paint >> 16)).put(srgb(v.paint >> 8)).put(srgb(v.paint)).put(.3f);
        palette.put(srgb(v.paint2 >> 16)).put(srgb(v.paint2 >> 8)).put(srgb(v.paint2)).put(.3f);
        for (int i = 2; i < 8; i++) palette.put(.5f).put(.5f).put(.5f).put(.5f);
        palette.flip();
    }
    private static float srgb(int channel) { float c = (channel & 255) / 255f; return (float) Math.pow(c, 2.0) * 1.2f; }
    private void lightGroups(Gta8Vehicle v, double time) {
        groups.clear();
        boolean dead = v.destroyed();
        double night = r.atmosphere.night;
        boolean blink = (time % .8) < .4;
        float head = dead ? 0 : v.lightsOn ? 1f : .06f;
        float tail = dead ? 0 : v.lightsOn ? .45f : .05f;
        groups.put(head).put(tail).put(dead ? 0 : v.brake > .1 ? 1f : tail * .8f).put(v.indicator < 0 && blink && !dead ? 1f : 0).put(v.indicator > 0 && blink && !dead ? 1f : 0)
                .put(v.reversing && v.throttle < 0 && !dead ? 1f : 0);
        boolean phase = (int) (v.sirenPhase * 6) % 2 == 0;
        groups.put(v.siren && phase ? 1.4f : .02f).put(v.siren && !phase ? 1.4f : .02f).put((float) (v.model == Gta8Vehicle.Model.TAXI ? .3 + night * .7 : 0));
        groups.put(0).put(0).put(0);
        groups.flip();
    }
    private void identityBones(int count) {
        bones.clear();
        Gta8Math.identity(temp);
        for (int i = 0; i < count; i++) bones.put(temp);
        bones.flip();
    }
    private void vehiclePose(Gta8Vehicle v, double a) {
        double x = Gta8Math.lerp(v.prevX, v.x, a), y = Gta8Math.lerp(v.prevY, v.y, a), z = Gta8Math.lerp(v.prevZ, v.z, a);
        double yaw = v.prevYaw + Gta8Math.angleDelta(v.prevYaw, v.yaw) * a;
        Gta8Math.pose(model, x, y, z, yaw, Gta8Math.lerp(v.prevPitch, v.pitch, a), Gta8Math.lerp(v.prevRoll, v.roll, a));
        bones.clear();
        bones.put(Gta8Math.identity(temp));
        double wr = v.model.wheelRadius(), hw = v.model.track / 2;
        double[] axle = {Gta8VehicleModels.frontAxle(v.model), Gta8VehicleModels.rearAxle(v.model)};
        for (int w = 0; w < 4; w++) {
            double side = w % 2 == 0 ? -1 : 1, az = axle[w / 2];
            Gta8Math.translation(temp, side * hw, wr - (v.burst[w] ? .09 : 0), az);
            if (w < 2) Gta8Math.multiply(temp, Gta8Math.rotationY(temp2, -Math.toDegrees(v.steer)), temp);
            Gta8Math.multiply(temp, Gta8Math.rotationX(temp2, -Math.toDegrees(v.wheelSpin)), temp);
            bones.put(temp);
        }
        bones.flip();
    }
    private void pedPose(Gta8Ped p, double a, double time) {
        double x = Gta8Math.lerp(p.prevX, p.x, a), y = Gta8Math.lerp(p.prevY, p.y, a), z = Gta8Math.lerp(p.prevZ, p.z, a);
        double yaw = p.prevYaw + Gta8Math.angleDelta(p.prevYaw, p.yaw) * a;
        Gta8Math.pose(model, x, y, z, yaw, 0, 0);
        Gta8CharacterModels.pose(p, time, skin, weaponMatrix);
        bones.clear();
        bones.put(skin, 0, Gta8CharacterModels.BONES * 16);
        bones.flip();
    }
    private int bodyIndex(Gta8Ped p) { return Math.min(1, p.body) * 7 + Math.max(0, Math.min(6, p.outfit)); }

    // ------------------------------------------------------------------ shadows
    void shadows(Gta8Game game, int cascade, float[] viewProjection, double radius, double sx, double sy, float[] lightView) {
        Gta8Shader s = r.shadowSkinnedShader();
        s.bind();
        s.matrix("uViewProj", viewProjection);
        s.set("uFoliage", 3);
        double a = game.alpha;
        double limit = cascade == 0 ? radius + 4 : Math.min(radius, 120);
        for (Gta8Vehicle v : game.vehicles) {
            if (Math.hypot(v.x - r.camera.x, v.z - r.camera.z) > limit + 40) continue;
            vehiclePose(v, a);
            s.matrix("uModel", model);
            s.matrices("uBones", bones);
            vehicleMeshes.get(v.model).draw();
        }
        for (Gta8Ped p : game.peds) {
            if (p.vehicle != null && p.state == Gta8Ped.State.DRIVE) continue;
            if (Math.hypot(p.x - r.camera.x, p.z - r.camera.z) > Math.min(limit, 70)) continue;
            pedPose(p, a, game.time);
            s.matrix("uModel", model);
            s.matrices("uBones", bones);
            bodies[bodyIndex(p)].draw();
        }
        Gta8Shader st = r.shadowShader();
        st.bind();
        st.matrix("uViewProj", viewProjection);
        s.bind();
        Gta8Police.Helicopter h = game.police.heli;
        if (h != null && cascade == 1) { heliPose(h, a, game.time); s.matrix("uModel", model); s.matrices("uBones", bones); heli.draw(); }
    }

    // ------------------------------------------------------------------ opaque actors
    void opaque(Gta8Game game) {
        Gta8Shader s = r.skinnedShader();
        s.bind();
        r.common(s);
        r.lighting(s);
        s.matrix("uViewProj", r.viewProjection);
        s.set("uTransparent", 0f);
        double a = game.alpha, cx = r.camera.x, cy = r.camera.y, cz = r.camera.z;
        for (Gta8Vehicle v : game.vehicles) {
            double d = Math.hypot(v.x - cx, v.z - cz);
            if (d > 320 || !r.visible(v.x, v.y + 1, v.z, v.halfLength() + 1)) continue;
            vehiclePose(v, a);
            paletteFor(v);
            lightGroups(v, game.time);
            s.matrix("uModel", model);
            s.matrices("uBones", bones);
            s.vec4s("uPalette", palette);
            s.floats("uLightGroups", groups);
            s.set("uDamage", v.exploded ? 1f : (float) Gta8Math.clamp((300 - v.health) / 600, 0, .5));
            vehicleMeshes.get(v.model).draw();
        }
        s.set("uDamage", 0f);
        boolean hidePlayer = game.firstPerson;
        for (Gta8Ped p : game.peds) {
            if (p == game.player && hidePlayer) continue;
            double d = Math.hypot(p.x - cx, p.z - cz);
            if (d > 140 || !r.visible(p.x, p.y + 1, p.z, 1.2)) continue;
            if (p.vehicle != null && p.state == Gta8Ped.State.DRIVE && d > 60) continue;
            pedPose(p, a, game.time);
            paletteFor(p);
            s.matrix("uModel", model);
            s.matrices("uBones", bones);
            s.vec4s("uPalette", palette);
            bodies[bodyIndex(p)].draw();
            if (p.hat && p.outfit != 2 || p.glasses || p.bag) {
                float[] headSkin = new float[16];
                System.arraycopy(skin, Gta8CharacterModels.HEAD * 16, headSkin, 0, 16);
                float[] chestSkin = new float[16];
                System.arraycopy(skin, Gta8CharacterModels.CHEST * 16, chestSkin, 0, 16);
                bones.clear(); bones.put(headSkin); bones.flip();
                s.matrices("uBones", bones);
                if (p.hat && p.outfit != 2) accessories[0].draw();
                if (p.glasses) accessories[1].draw();
                if (p.bag) { bones.clear(); bones.put(chestSkin); bones.flip(); s.matrices("uBones", bones); accessories[2].draw(); }
            }
            if (p.weapon != Gta8Weapon.FISTS && (p.aim > .05 || p.weapon.twoHanded() || p.kind == Gta8Ped.Kind.PLAYER) && d < 70 && !(p.vehicle != null && p.aim < .5)) {
                Gta8Mesh w = weapons.get(p.weapon);
                if (w != null) {
                    Gta8Math.multiply(model, weaponMatrix, temp);
                    s.matrix("uModel", temp);
                    identityBones(1);
                    s.matrices("uBones", bones);
                    w.draw();
                }
            }
        }
        // Helicopter, Ferris wheel and gondolas.
        Gta8Police.Helicopter h = game.police.heli;
        if (h != null) {
            heliPose(h, a, game.time);
            palette.clear();
            palette.put(.02f).put(.03f).put(.05f).put(.3f).put(.9f).put(.9f).put(.9f).put(.3f);
            for (int i = 2; i < 8; i++) palette.put(.5f).put(.5f).put(.5f).put(.5f);
            palette.flip();
            groups.clear();
            boolean phase = (int) (game.time * 3) % 2 == 0;
            for (int i = 0; i < 12; i++) groups.put(i == 6 ? (phase ? 1.2f : 0) : i == 7 ? (phase ? 0 : 1.2f) : 0);
            groups.flip();
            s.matrix("uModel", model); s.matrices("uBones", bones); s.vec4s("uPalette", palette); s.floats("uLightGroups", groups);
            heli.draw();
        }
        for (Prop p : r.world().props) if (p.type == Prop.FERRIS && Math.hypot(p.x - cx, p.z - cz) < 1500) ferris(s, p, game.time);
        // Pickups spin and bob.
        identityBones(1);
        s.matrices("uBones", bones);
        paletteNeutral();
        s.vec4s("uPalette", palette);
        for (Gta8Game.Pickup p : game.pickups) {
            if (p.source != null && p.source.respawnAt > game.time) continue;
            if (Math.hypot(p.x - cx, p.z - cz) > 90) continue;
            double spin = game.time * 120 + p.x * 10, bob = Math.sin(game.time * 2.2 + p.z) * .08;
            Gta8Math.pose(model, p.x, p.y + .55 + bob, p.z, spin, 0, 0);
            s.matrix("uModel", model);
            Gta8Mesh mesh = p.type == Gta8World.Pickup.HEALTH ? health : p.type == Gta8World.Pickup.ARMOR ? armor : p.type == Gta8World.Pickup.CASH ? cash : weapons.get(p.weapon == null ? Gta8Weapon.PISTOL : p.weapon);
            if (mesh != null) mesh.draw();
        }
        fallen(s, game);
    }
    private void paletteNeutral() {
        palette.clear();
        for (int i = 0; i < 8; i++) palette.put(.5f).put(.5f).put(.5f).put(.5f);
        palette.flip();
    }
    private void heliPose(Gta8Police.Helicopter h, double a, double time) {
        double x = Gta8Math.lerp(h.prevX, h.x, a), y = Gta8Math.lerp(h.prevY, h.y, a), z = Gta8Math.lerp(h.prevZ, h.z, a);
        Gta8Math.pose(model, x, y - 1.4, z, h.prevYaw + Gta8Math.angleDelta(h.prevYaw, h.yaw) * a, -h.tilt, 0);
        bones.clear();
        bones.put(Gta8Math.identity(temp));
        bones.put(Gta8Math.rotationY(temp, h.rotor * 57.3));
        Gta8Math.translation(temp, .12, 2.45, 7.25);
        Gta8Math.multiply(temp, Gta8Math.rotationX(temp2, h.rotor * 90), temp);
        bones.put(temp);
        bones.flip();
    }
    private void ferris(Gta8Shader s, Prop p, double time) {
        double angle = time * 1.6;
        Gta8Math.pose(model, p.x, p.y, p.z, p.yaw, 0, 0);
        bones.clear();
        bones.put(Gta8Math.identity(temp));
        Gta8Math.translation(temp, 0, 19.5, 0);
        Gta8Math.multiply(temp, Gta8Math.rotationX(temp2, angle), temp);
        Gta8Math.multiply(temp, Gta8Math.translation(temp2, 0, -19.5, 0), temp);
        bones.put(temp);
        bones.flip();
        palette.clear();
        palette.put(.8f).put(.12f).put(.2f).put(.35f);
        palette.put(.9f).put(.9f).put(.9f).put(.3f);
        for (int i = 2; i < 8; i++) palette.put(.5f).put(.5f).put(.5f).put(.5f);
        palette.flip();
        s.matrix("uModel", model); s.matrices("uBones", bones); s.vec4s("uPalette", palette);
        ferris.draw();
        identityBones(1);
        s.matrices("uBones", bones);
        float[] base = model.clone();
        for (int i = 0; i < 16; i++) {
            double a = Math.toRadians(angle) + i * Math.PI * 2 / 16;
            Gta8Math.translation(temp, 0, 19.5 + Math.sin(a) * 17.5, Math.cos(a) * 17.5);
            Gta8Math.multiply(temp, Gta8Math.rotationX(temp2, Math.sin(time * .8 + i) * 3), temp);
            Gta8Math.multiply(base, temp, temp2);
            s.matrix("uModel", temp2);
            gondola.draw();
        }
    }
    /** Knocked-over street furniture lies where it fell until the chunk streams out. */
    private void fallen(Gta8Shader s, Gta8Game game) {
        identityBones(1);
        s.matrices("uBones", bones);
        paletteNeutral();
        s.vec4s("uPalette", palette);
        for (Prop p : r.world().props) {
            if (!p.broken || p.fallTime < 0 || p.type == Prop.GLASS) continue;
            if (Math.hypot(p.x - r.camera.x, p.z - r.camera.z) > 90) continue;
            Gta8Mesh mesh = fallenProps.get(p.type * 64 + (p.variant & 63));
            if (mesh == null) {
                Gta8MeshBuilder b = new Gta8MeshBuilder(), f = new Gta8MeshBuilder();
                Gta8PropModels.model(b, f, p, r.atlas());
                mesh = Gta8Mesh.upload(b);
                fallenProps.put(p.type * 64 + (p.variant & 63), mesh);
            }
            if (mesh == null) continue;
            double k = Gta8Math.clamp((game.time - p.fallTime) * 2.5, 0, 1);
            Gta8Math.pose(model, p.x, p.y, p.z, p.fallYaw, 0, 0);
            Gta8Math.multiply(model, Gta8Math.rotationX(temp, -84 * k * k), model);
            Gta8Math.multiply(model, Gta8Math.rotationY(temp, -(p.yaw - p.fallYaw)), model);
            s.matrix("uModel", model);
            mesh.draw();
        }
    }

    /** Vehicle glass in the transparent pass, after opaque geometry and water. */
    void transparent(Gta8Game game) {
        Gta8Shader s = r.worldShader();
        s.bind();
        s.set("uTransparent", 2f);
        s.set("uDamage", 0f);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        double a = game.alpha;
        for (Gta8Vehicle v : game.vehicles) {
            if (v.exploded || Math.hypot(v.x - r.camera.x, v.z - r.camera.z) > 260 || !r.visible(v.x, v.y + 1, v.z, v.halfLength() + 1)) continue;
            vehiclePose(v, a);
            s.matrix("uModel", model);
            Gta8Mesh g = glassMeshes.get(v.model);
            if (g != null) g.draw();
        }
        // Mission marker: an additive beacon cylinder.
        Gta8Missions m = game.missions;
        if (m.marker) {
            GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
            s.set("uTransparent", 0f);
            double gy = game.world.groundHeight(m.markerX, m.markerZ);
            Gta8Math.pose(model, m.markerX, gy, m.markerZ, game.time * 40, 0, 0);
            s.matrix("uModel", model);
            marker.draw();
        }
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
        s.set("uTransparent", 0f);
        s.matrix("uModel", Gta8Math.identity(temp));
    }

    // ------------------------------------------------------------------ particles, decals and coronas
    void effects(Gta8Game game) {
        transparent(game);
        Gta8Shader s = r.particleShader();
        s.bind();
        r.common(s);
        s.matrix("uViewProj", r.viewProjection);
        double yaw = Math.toRadians(r.camera.yaw), pitch = Math.toRadians(r.camera.pitch);
        s.set("uCamRight", (float) Math.cos(yaw), 0f, (float) Math.sin(yaw));
        s.set("uCamUp", (float) (Math.sin(yaw) * Math.sin(pitch)), (float) Math.cos(pitch), (float) (-Math.cos(yaw) * Math.sin(pitch)));
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
        // Decals, rebuilt only when they change.
        Gta8Decals d = game.decals;
        if (d.revision != decalRevision) { uploadDecals(d); decalRevision = d.revision; }
        if (decalCount > 0) {
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(-1, -3);
            drawStream(decalVbo, decalCount);
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        }
        stream.clear();
        streamCount = 0;
        Gta8Particles p = game.particles;
        double cx = r.camera.x, cy = r.camera.y, cz = r.camera.z;
        for (int i = 0; i < p.count; i++) {
            double dx = p.x[i] - cx, dz = p.z[i] - cz;
            if (dx * dx + dz * dz > 350 * 350) continue;
            float life = p.life[i] / p.maxLife[i];
            float alpha = p.type[i] == Gta8Particles.SMOKE || p.type[i] == Gta8Particles.DUST ? p.alpha[i] * Math.min(1, life * 2.5f) * Math.min(1, (1 - life) * 6 + .2f) : p.alpha[i] * Math.min(1, life * 3);
            int c = p.color[i];
            quad(p.x[i], p.y[i], p.z[i], (c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f, alpha, p.size[i], p.rot[i], p.type[i] == Gta8Particles.TRACER ? 2 : p.type[i], p.stretch[i], p.vx[i], p.vy[i], p.vz[i]);
        }
        coronas(game);
        if (streamCount > 0) {
            stream.flip();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, streamVbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, stream, GL15.GL_STREAM_DRAW);
            drawStream(streamVbo, streamCount);
        }
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }
    private void drawStream(int vbo, int quads) {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        int stride = 64;
        GL20.glVertexAttribPointer(Gta8Shader.POSITION, 3, GL11.GL_FLOAT, false, stride, 0);
        GL20.glVertexAttribPointer(Gta8Shader.UV, 2, GL11.GL_FLOAT, false, stride, 12);
        GL20.glVertexAttribPointer(Gta8Shader.COLOR, 4, GL11.GL_FLOAT, false, stride, 20);
        GL20.glVertexAttribPointer(Gta8Shader.MATERIAL, 4, GL11.GL_FLOAT, false, stride, 36);
        GL20.glVertexAttribPointer(Gta8Shader.NORMAL, 3, GL11.GL_FLOAT, false, stride, 52);
        GL11.glDrawArrays(GL11.GL_QUADS, 0, quads * 4);
    }
    private void quad(double x, double y, double z, float red, float green, float blue, float alpha, double size, double rot, int type, double stretch, double vx, double vy, double vz) {
        if (stream.remaining() < 64) return;
        float[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (float[] c : corners) {
            stream.put((float) x).put((float) y).put((float) z).put(c[0]).put(c[1]).put(red).put(green).put(blue).put(alpha)
                    .put((float) size).put((float) rot).put(type).put((float) stretch).put((float) vx).put((float) vy).put((float) vz);
        }
        streamCount++;
    }
    private void uploadDecals(Gta8Decals d) {
        FloatBuffer b = floats(d.count * 64);
        for (int i = 0; i < d.count; i++) {
            float s = d.size[i], len = d.type[i] == Gta8Decals.SKID ? d.length[i] : s;
            // Tangent frame: along (ax, az) for skids, arbitrary for radial marks.
            double nx = d.nx[i], ny = d.ny[i], nz = d.nz[i];
            double tx, ty, tz;
            if (Math.abs(ny) > .7) { tx = d.type[i] == Gta8Decals.SKID ? d.ax[i] : 1; ty = 0; tz = d.type[i] == Gta8Decals.SKID ? d.az[i] : 0; }
            else { tx = -nz; ty = 0; tz = nx; }
            double bx = ny * tz - nz * ty, by = nz * tx - nx * tz, bz = nx * ty - ny * tx;
            int c = d.type[i] == Gta8Decals.BLOOD ? 0x4A0806 : d.type[i] == Gta8Decals.BULLET ? 0x2A2826 : 0x0C0C0C;
            float[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
            for (float[] k : corners) {
                double px = d.x[i] + bx * k[0] * s + tx * k[1] * len, py = d.y[i] + by * k[0] * s + ty * k[1] * len, pz = d.z[i] + bz * k[0] * s + tz * k[1] * len;
                b.put((float) px).put((float) py).put((float) pz).put(k[0]).put(k[1])
                        .put((c >> 16 & 255) / 255f).put((c >> 8 & 255) / 255f).put((c & 255) / 255f).put(d.alpha[i])
                        .put(0).put(0).put(10 + d.type[i]).put(0).put(0).put(0).put(0);
            }
        }
        b.flip();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, decalVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, b, GL15.GL_DYNAMIC_DRAW);
        decalCount = d.count;
    }
    private void glow(double x, double y, double z, int rgb, double size, double intensity) {
        double dx = x - r.camera.x, dy = y - r.camera.y, dz = z - r.camera.z, dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < .5 || !r.visible(x, y, z, size * 4)) return;
        double grow = 1 + dist / 70;
        double k = intensity / (1 + dist / 400);
        // Pull the sprite towards the camera so it clears its own lamp housing.
        double pull = Math.min(.6, dist * .1);
        quad(x - dx / dist * pull, y - dy / dist * pull, z - dz / dist * pull, (float) ((rgb >> 16 & 255) / 255.0 * k), (float) ((rgb >> 8 & 255) / 255.0 * k), (float) ((rgb & 255) / 255.0 * k), 1f, size * grow, 0, 8, 0, 0, 0, 0);
    }
    private void coronas(Gta8Game game) {
        double night = r.atmosphere.night;
        double cx = r.camera.x, cz = r.camera.z;
        if (night > .05) for (Gta8World.Light l : r.world().lights) {
            double d = Math.hypot(l.x - cx, l.z - cz);
            if (d > 900) continue;
            switch (l.kind) {
                case Gta8World.Light.STREET: glow(l.x, l.y - .1, l.z, l.color, .5, 2.4 * night); break;
                case Gta8World.Light.BEACON: if ((game.time + l.x * .01) % 1.8 < .25) glow(l.x, l.y, l.z, l.color, .5, 4 * night); break;
                case Gta8World.Light.NEON: if (d < 400) glow(l.x, l.y, l.z, l.color, 1.2, .9 * night); break;
                case Gta8World.Light.FLOOD: if (d < 600) glow(l.x, l.y, l.z, l.color, .8, 2 * night); break;
                case Gta8World.Light.WARM: if (d < 300) glow(l.x, l.y, l.z, l.color, .35, 1.6 * night); break;
                default: break;
            }
        }
        for (Gta8Vehicle v : game.vehicles) {
            double d = Math.hypot(v.x - cx, v.z - cz);
            if (d > 500 || v.destroyed()) continue;
            double hw = v.halfWidth() * .66, front = v.halfLength(), y = v.y + .62;
            if (v.lightsOn) {
                for (int side = -1; side <= 1; side += 2) {
                    glow(v.worldX(side * hw, front), y, v.worldZ(side * hw, front), 0xFFF2DC, .35, 2.2 + night);
                    glow(v.worldX(side * hw, -front), y + .08, v.worldZ(side * hw, -front), 0xFF2010, .22, v.brake > .1 ? 2.2 : 1);
                }
            } else if (v.brake > .1 && d < 120) for (int side = -1; side <= 1; side += 2) glow(v.worldX(side * hw, -front), y + .08, v.worldZ(side * hw, -front), 0xFF2010, .18, 1.2);
            if (v.indicator != 0 && (game.time % .8) < .4) {
                double side = v.indicator;
                glow(v.worldX(side * v.halfWidth() * .85, front), y - .05, v.worldZ(side * v.halfWidth() * .85, front), 0xFFA020, .18, 1.5);
                glow(v.worldX(side * v.halfWidth() * .85, -front), y, v.worldZ(side * v.halfWidth() * .85, -front), 0xFFA020, .18, 1.5);
            }
            if (v.siren) {
                boolean phase = (int) (v.sirenPhase * 6) % 2 == 0;
                double top = v.y + v.model.height + .15;
                glow(v.worldX(-.35, 0), top, v.worldZ(-.35, 0), 0xFF1010, .5, phase ? 3.5 : .2);
                glow(v.worldX(.35, 0), top, v.worldZ(.35, 0), 0x2050FF, .5, phase ? .2 : 3.5);
            }
        }
        // Active traffic signal lamps.
        for (Prop p : r.world().props) {
            if (p.type != Prop.SIGNAL) continue;
            double d = Math.hypot(p.x - cx, p.z - cz);
            if (d > 320) continue;
            Gta8World.Signal sig = r.world().signalGrid[p.signalId % 11][p.signalId / 11];
            int state = r.world().signalState(sig, p.dir, game.time);
            int color = state == Gta8World.GREEN_LIGHT ? 0x30FF90 : state == Gta8World.YELLOW_LIGHT ? 0xFFB020 : 0xFF2A1A;
            double lampY = 5.63 - (state == Gta8World.GREEN_LIGHT ? 2 : state == Gta8World.YELLOW_LIGHT ? 1 : 0) * .31;
            double[] heads = p.sx > 5 ? new double[]{p.sx - .9, p.sx * .45} : new double[]{p.sx - .9};
            double yawR = Math.toRadians(p.yaw);
            for (double hx : heads) {
                // Local (hx, lampY, -0.2) rotated by the prop's yaw.
                double lx = hx, lz = -.22;
                double wx = p.x + lx * Math.cos(yawR) - lz * Math.sin(yawR), wz = p.z + lx * Math.sin(yawR) + lz * Math.cos(yawR);
                glow(wx, CURB_Y + lampY, wz, color, .28, 1.2 + night * 1.6);
            }
        }
    }
    private static final double CURB_Y = Gta8World.CURB;

    // ------------------------------------------------------------------ first-person view model
    void viewModel(Gta8Game game) {
        Gta8Ped p = game.player;
        if (!game.firstPerson || p.vehicle != null || game.dead || p.weapon == Gta8Weapon.FISTS && p.punch <= 0) return;
        Gta8Mesh w = weapons.get(p.weapon);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        float[] projection = Gta8Math.perspective(new float[16], 62, r.renderWidth() / (double) r.renderHeight(), .03, 10);
        float[] viewProjection = Gta8Math.multiply(projection, r.view, new float[16]);
        float[] cameraWorld = Gta8Math.invert(r.view, new float[16]);
        double aim = p.aim, bob = Math.sin(p.phase * 2) * .012 * Math.min(1, p.moveSpeed / 3), sway = Math.sin(p.phase) * .01 * Math.min(1, p.moveSpeed / 3);
        double x = Gta8Math.lerp(.17, 0, aim) + sway, y = Gta8Math.lerp(-.2, p.weapon == Gta8Weapon.SNIPER ? -.13 : -.105, aim) + bob - p.reloadAnim * .12, z = Gta8Math.lerp(-.42, -.3, aim) + p.recoil * .05;
        Gta8Math.translation(temp, x, y, z);
        Gta8Math.multiply(temp, Gta8Math.rotationX(temp2, p.recoil * 4 + p.reloadAnim * 25), temp);
        Gta8Math.multiply(temp, Gta8Math.rotationZ(temp2, p.reloadAnim * 20), temp);
        Gta8Math.multiply(cameraWorld, temp, model);
        Gta8Shader s = r.skinnedShader();
        s.bind();
        s.matrix("uViewProj", viewProjection);
        s.matrix("uModel", model);
        identityBones(2);
        s.matrices("uBones", bones);
        paletteFor(p);
        s.vec4s("uPalette", palette);
        if (w != null && !(game.player.aim > .8 && p.weapon == Gta8Weapon.SNIPER)) w.draw();
        hands.draw();
        s.matrix("uViewProj", r.viewProjection);
    }

    // ------------------------------------------------------------------ small meshes
    private static Gta8MeshBuilder markerMesh() {
        Gta8MeshBuilder b = new Gta8MeshBuilder();
        b.ao(1).material(Gta8Materials.EMISSIVE, Gta8Materials.ALWAYS).color(0x9A7A10);
        int seg = 24;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg, rr = 1.25;
            b.card(Math.cos(a0) * rr, 0, Math.sin(a0) * rr, Math.cos(a1) * rr, 0, Math.sin(a1) * rr, Math.cos(a1) * rr, 1.4, Math.sin(a1) * rr, Math.cos(a0) * rr, 1.4, Math.sin(a0) * rr);
        }
        b.color(0xC8A020);
        b.push(); b.translate(0, 2.4, 0);
        b.tri(-.35, .35, 0, .35, .35, 0, 0, -.2, 0, 0, 0, 1, 0, .5, 1);
        b.tri(.35, .35, 0, -.35, .35, 0, 0, -.2, 0, 0, 0, 1, 0, .5, 1);
        b.box(-.12, .35, -.02, .12, .75, .02);
        b.pop();
        return b;
    }
    private static Gta8MeshBuilder handsMesh() {
        Gta8MeshBuilder b = new Gta8MeshBuilder();
        b.ao(1);
        b.bone(0).material(Gta8Materials.CHARACTER, 6);
        b.tube(.02, -.06, .32, .0, -.05, .06, .045, .038, 8, false);
        b.material(Gta8Materials.CHARACTER, 0);
        b.rounded(-.028, -.075, -.01, .028, .01, .07, .018, 2);
        b.bone(1).material(Gta8Materials.CHARACTER, 6);
        b.tube(-.2, -.12, .2, -.03, -.04, -.12, .045, .038, 8, false);
        b.material(Gta8Materials.CHARACTER, 0);
        b.rounded(-.06, -.07, -.2, 0, 0, -.1, .018, 2);
        return b;
    }
    static FloatBuffer floats(int n) { return ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer(); }
}
