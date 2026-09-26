/*
 * Ported from Gothaj (CFR 0.152), revision 8aa9e1c056bc5b6393b29dc03bcb06b529b40a07.
 * Source mapping and Forge adaptations: LICENSES/GOTHAJ-SCAFFOLD.md.
 */
package dev.vibe.module.impl.scaffold;


public class MoveInputEvent
{
    private float forward;
    private float strafe;
    private boolean jump;
    private boolean sneak;
    private double slowdown;
    private boolean keyForward;
    private boolean keyBack;
    private boolean keyLeft;
    private boolean keyRight;
    private boolean keyJump;
    private boolean keySneak;

    public void recomputeFromKeys() {
        float f = 0.0f;
        float s = 0.0f;
        if (this.keyForward) {
            f += 1.0f;
        }
        if (this.keyBack) {
            f -= 1.0f;
        }
        if (this.keyLeft) {
            s += 1.0f;
        }
        if (this.keyRight) {
            s -= 1.0f;
        }
        this.forward = f;
        this.strafe = s;
        this.jump = this.keyJump;
        this.sneak = this.keySneak;
    }

    public float getForward() {
        return this.forward;
    }

    public float getStrafe() {
        return this.strafe;
    }

    public boolean isJump() {
        return this.jump;
    }

    public boolean isSneak() {
        return this.sneak;
    }

    public double getSlowdown() {
        return this.slowdown;
    }

    public boolean isKeyForward() {
        return this.keyForward;
    }

    public boolean isKeyBack() {
        return this.keyBack;
    }

    public boolean isKeyLeft() {
        return this.keyLeft;
    }

    public boolean isKeyRight() {
        return this.keyRight;
    }

    public boolean isKeyJump() {
        return this.keyJump;
    }

    public boolean isKeySneak() {
        return this.keySneak;
    }

    public void setForward(float forward) {
        this.forward = forward;
    }

    public void setStrafe(float strafe) {
        this.strafe = strafe;
    }

    public void setJump(boolean jump) {
        this.jump = jump;
    }

    public void setSneak(boolean sneak) {
        this.sneak = sneak;
    }

    public void setSlowdown(double slowdown) {
        this.slowdown = slowdown;
    }

    public void setKeyForward(boolean keyForward) {
        this.keyForward = keyForward;
    }

    public void setKeyBack(boolean keyBack) {
        this.keyBack = keyBack;
    }

    public void setKeyLeft(boolean keyLeft) {
        this.keyLeft = keyLeft;
    }

    public void setKeyRight(boolean keyRight) {
        this.keyRight = keyRight;
    }

    public void setKeyJump(boolean keyJump) {
        this.keyJump = keyJump;
    }

    public void setKeySneak(boolean keySneak) {
        this.keySneak = keySneak;
    }

    public MoveInputEvent(float forward, float strafe, boolean jump, boolean sneak, double slowdown, boolean keyForward, boolean keyBack, boolean keyLeft, boolean keyRight, boolean keyJump, boolean keySneak) {
        this.forward = forward;
        this.strafe = strafe;
        this.jump = jump;
        this.sneak = sneak;
        this.slowdown = slowdown;
        this.keyForward = keyForward;
        this.keyBack = keyBack;
        this.keyLeft = keyLeft;
        this.keyRight = keyRight;
        this.keyJump = keyJump;
        this.keySneak = keySneak;
    }
}

