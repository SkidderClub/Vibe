package dev.vibe.raven;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.regex.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Token-level parity checks against the supplied reconstruction, without loading it. */
public class RavenSourceParityTest {
    private static final Path SOURCE = Paths.get("context/ravenBS/source/java/keystrokesmod");
    @Test public void scaffoldAlgorithmsRetainRavensMathAndBranching() throws Exception {
        org.junit.Assume.assumeTrue(Files.isRegularFile(SOURCE.resolve("module/impl/player/Scaffold.java")));
        String raven = read(SOURCE.resolve("module/impl/player/Scaffold.java"));
        String vibe = read(Paths.get("src/main/java/dev/vibe/module/impl/world/HypixelScaffold.java"));
        String[] methods = new String[]{"getBlockCount","resetMovementState","resetLongTellyQueue","autoClick","updateCameraYaw","beginTick","updateKeepYState","updateLongTellyState","aimAtPlacement","getFaceOffsets","aimYaw","aimYawWithJitter","aimPitch","updateTargetRotations","updateLookBucket","alignTakeoffRotations","snapGroundYaw","aimGroundPlacement","getGroundYawBuckets","isPlacementAllowed","verifyPlacementAim","aimLongTellyPlacement","easeActivationRotation","placeSelectedBlock","updateKeepYPlacement","allowPacket","allowMouse","buildCandidates","matchCandidate","buildPrediction","isAbovePredictedPath","getSupportOverhang","findNeededBlock","createSimulation","checkUnsupportedCorners","getFaceGeometry","findFacePitch","findNearestFacePitch","getCandidateScore","getTravelYaw","getTellyMode","getKeepYMode","isIceSelectionEnabled","isKeepYActive","getActiveKeepYMode","randomFloat","getPlayerYaw","quantizeMouseRotation","unwrapYaw","clampRotationDelta","quantizeRotation","jitter","updateYawJitter","nextYawJitter","rotationEaseScale","easeRotations","smooth","getRotationsToOffset","getMovementYaw","getForwardInput","getStrafeInput","isMoving","isJumpPressed","updateCardinalSide","isDiagonalMovement","getInputYaw","isTellyTakeoff","isManualTellyTakeoff","isLongTellyEnabled","isLookLocked","faceFromDelta","intersectsPlayer","findQueueSupport","buildLongTellyQueue","findLongTellyPlacement","getLongTellyCandidateYaws","hasCeilingAbove","hasJumpPotion","isReplaceable","isReplaceableAt","isSolidSupport","isInteractableAt","findPlacement","findPlacementTarget","findClosestPlacement","findBestPlacementFace","raycastFace","hasStableFaceAim","aimShortDiagonalFace","placeKeepYFromRay","getPlacementLimit","placeBlock","isUnsupportedAt","hasCornerSupport","isValidBlock","findPreferredIceSlot"};
        for (String method : methods) assertEquals(method, normalizeScaffold(body(raven, method)), normalizeScaffold(body(vibe, method)));
    }
    @Test public void vanillaAutoblockStateMachineRetainsRavensBranching() throws Exception {
        org.junit.Assume.assumeTrue(Files.isRegularFile(SOURCE.resolve("module/impl/combat/AutoBlock.java")));
        String raven = read(SOURCE.resolve("module/impl/combat/AutoBlock.java"));
        String vibe = read(Paths.get("src/main/java/dev/vibe/module/impl/combat/HypixelAutoblock.java"));
        for (String method : new String[]{"onPrePlayerInteract","onForceAttack","checkConditions","shouldPredictiveBlock",
                "shouldBlockVanillaUse","startBlocking","stopBlocking","isCooldownActive","isAlwaysUnblockMode","resetState"})
            assertEquals(method, normalizeBlock(body(raven, method)), normalizeBlock(body(vibe, method)));
    }
    @Test public void autoclickerDelayRetainsRavensRandomization() throws Exception {
        org.junit.Assume.assumeTrue(Files.isRegularFile(SOURCE.resolve("module/impl/combat/AutoClicker.java")));
        String raven = body(read(SOURCE.resolve("module/impl/combat/AutoClicker.java")), "nextDelay")
                .replace("this.targetCPS", "hypixelTargetCps").replace("this.simulateExhaust", "hypixelSimulateExhaust")
                .replace("this.rand", "random").replace("getInput()", "getDouble()").replace("isToggled()", "isEnabled()");
        String vibe = body(read(Paths.get("src/main/java/dev/vibe/module/impl/combat/KillAuraModule.java")), "hypixelAttackDelay");
        assertEquals(tokens(raven), tokens(vibe));
    }
    @Test public void simulationPhysicsRemainSourceIdentical() throws Exception {
        org.junit.Assume.assumeTrue(Files.isRegularFile(SOURCE.resolve("utility/SimulatedPlayer.java")));
        String raven = read(SOURCE.resolve("utility/SimulatedPlayer.java"));
        String vibe = read(Paths.get("src/main/java/dev/vibe/module/impl/world/RavenSimulatedPlayer.java"));
        for (String method : new String[]{"tick","onEntityUpdate","moveEntity","playerSideMoveEntityWithHeading","livingEntitySideMoveEntityWithHeading","moveFlying","jump",
                "getCollidingBoundingBoxes","handleWaterMovement","isInLava","pushOutOfBlocks"})
            assertEquals(method, tokens(body(raven, method)), tokens(body(vibe, method).replace("mc().", "mc.")));
    }
    private static String normalizeBlock(String body) {
        return tokens(body.replace("ModuleManager.bedAura", "bedAura()").replace("ModuleManager.killAura", "owner")
                .replace("!owner.isRequireMouseDown()", "true").replace("Utils.nullCheck()", "RavenBlockAccess.nullCheck()")
                .replace("Utils.holdingSword()", "holdingSword()").replace("Mouse.isButtonDown(0)", "mouseDown(0)")
                .replace("Mouse.isButtonDown(1)", "mouseDown(1)").replace("CombatTargeting.", "RavenCombatAccess."));
    }
    private static String normalizeScaffold(String body) {
        body = removeDebugBlocks(body);
        body = body.replaceAll("this\\.debug\\w*\\s*=[^;]*;", "");
        return tokens(body.replace("HypixelScaffold.", "Scaffold.").replace("RavenSimulatedPlayer", "SimulatedPlayer")
                .replace("BlockUtils.", "RavenBlockAccess.").replace("Utils.", "RavenBlockAccess.")
                .replace("RotationRavenBlockAccess.clampPitch", "clampPitch").replace("RotationRavenBlockAccess.rayCastBlock", "RavenBlockAccess.rayCastBlock")
                .replace("RavenRavenSimulatedPlayer", "RavenSimulatedPlayer").replace("vibeSilentSwing", "silentSwing").replace("mc().", "mc."));
    }
    private static String removeDebugBlocks(String body) {
        Pattern condition = Pattern.compile("(?m)^[ \\t]*if \\([^\\n]*(?:isBasicDebugEnabled|debugPlacementPitchDelta)[^\\n]*\\) \\{");
        Matcher match;
        while ((match = condition.matcher(body)).find()) {
            int end = closing(body, body.indexOf('{', match.start()));
            body = body.substring(0, match.start()) + body.substring(end + 1);
        }
        return body;
    }
    private static String tokens(String text) { return text.replaceAll("\\s+", ""); }
    private static String read(Path path) throws Exception { return new String(Files.readAllBytes(path), StandardCharsets.UTF_8); }
    private static String body(String source, String name) {
        Matcher method = Pattern.compile("(?m)^[ \\t]*(?:private|public|protected) (?:static )?(?:final )?[^\\n;]*?\\b"
                + name + "\\([^;]*?\\) \\{").matcher(source);
        assertTrue("Missing source method " + name, method.find());
        int opening = source.indexOf('{', method.start());
        return source.substring(opening + 1, closing(source, opening));
    }
    private static int closing(String source, int opening) {
        int depth = 1, index = opening + 1;
        while (depth > 0) {
            char c = source.charAt(index++);
            if (c == '{') depth++;
            else if (c == '}') depth--;
        }
        return index - 1;
    }
}
