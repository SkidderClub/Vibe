package dev.vibe.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The six ModelBiped parts a custom player model is split into so it can follow vanilla's pose. */
public enum BodyPart {
    HEAD(0.0F, 0.0F, 0.0F),
    BODY(0.0F, 0.0F, 0.0F),
    RIGHT_ARM(-5.0F, 2.0F, 0.0F),
    LEFT_ARM(5.0F, 2.0F, 0.0F),
    RIGHT_LEG(-1.9F, 12.0F, 0.1F),
    LEFT_LEG(1.9F, 12.0F, 0.1F);

    /** ModelBiped's resting rotation point in model pixels (y points down). */
    public final float restX, restY, restZ;

    BodyPart(float restX, float restY, float restZ) {
        this.restX = restX;
        this.restY = restY;
        this.restZ = restZ;
    }

    public boolean isArm() {
        return this == RIGHT_ARM || this == LEFT_ARM;
    }

    public boolean isLeg() {
        return this == RIGHT_LEG || this == LEFT_LEG;
    }

    /**
     * Classifies a skeleton joint by name. Covers Mixamo ("mixamorig:LeftForeArm"), Unreal
     * ("upperarm_l"), 3ds Max Biped ("Bip01 L Thigh") and Blender ("forearm.L") conventions.
     * Returns null when the name alone does not identify a limb, so the caller can inherit
     * the parent joint's part.
     */
    public static BodyPart classifyJoint(String name) {
        List<String> tokens = tokens(name);
        boolean left = false, right = false, arm = false, leg = false, head = false, torso = false;
        for (String token : tokens) {
            if (token.equals("left") || token.equals("l") || token.equals("lft")) left = true;
            else if (token.equals("right") || token.equals("r") || token.equals("rt") || token.equals("rgt")) right = true;
            if (token.equals("shoulder") || token.equals("clavicle") || token.equals("collar")) torso = true;
            else if (token.contains("arm") || token.contains("hand") || token.contains("elbow") || token.contains("wrist")
                    || token.contains("finger") || token.contains("thumb") || token.equals("index") || token.equals("middle")
                    || token.equals("ring") || token.equals("pinky") || token.equals("palm")) arm = true;
            else if (token.contains("leg") || token.contains("thigh") || token.contains("knee") || token.contains("calf")
                    || token.contains("shin") || token.contains("foot") || token.contains("toe") || token.contains("ankle")) leg = true;
            else if (token.equals("head") || token.equals("jaw") || token.contains("eye") || token.equals("hair")
                    || token.equals("face") || token.equals("nose") || token.equals("ear") || token.equals("skull")
                    || token.equals("helmet") || token.equals("brow") || token.equals("lip") || token.equals("tongue")) head = true;
            else if (token.contains("spine") || token.contains("hip") || token.contains("pelvis") || token.contains("chest")
                    || token.equals("neck") || token.equals("root") || token.equals("torso") || token.equals("waist")) torso = true;
        }
        if (torso && !arm && !leg) return BODY;
        if (arm && (left != right)) return left ? LEFT_ARM : RIGHT_ARM;
        if (leg && (left != right)) return left ? LEFT_LEG : RIGHT_LEG;
        if (head) return HEAD;
        if (torso) return BODY;
        return null;
    }

    /** Splits "mixamorig:LeftForeArm", "upperarm_l" and "Bip01 L Thigh" into lower-case words. */
    static List<String> tokens(String name) {
        List<String> tokens = new ArrayList<String>();
        if (name == null) return tokens;
        StringBuilder current = new StringBuilder();
        char previous = 0;
        for (int index = 0; index < name.length(); index++) {
            char c = name.charAt(index);
            boolean boundary = !Character.isLetterOrDigit(c)
                    || (Character.isUpperCase(c) && Character.isLowerCase(previous))
                    || (Character.isDigit(c) != Character.isDigit(previous) && current.length() > 0 && Character.isLetterOrDigit(previous));
            if (boundary && current.length() > 0) {
                tokens.add(current.toString().toLowerCase(Locale.ROOT));
                current.setLength(0);
            }
            if (Character.isLetter(c)) current.append(c);
            previous = c;
        }
        if (current.length() > 0) tokens.add(current.toString().toLowerCase(Locale.ROOT));
        return tokens;
    }
}
