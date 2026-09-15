package keystrokesmod.script.model;

/** Mutable input callback payload used by onPrePlayerInput. */
public class MovementInput {
    public float forward, strafe;
    public boolean jump, sneak;
    public MovementInput(float forward, float strafe, boolean jump, boolean sneak) { this.forward=forward; this.strafe=strafe; this.jump=jump; this.sneak=sneak; }
    public MovementInput(Object[] state) { this((Float) state[0], (Float) state[1], (Boolean) state[2], (Boolean) state[3]); }
    public Object[] asArray() { return new Object[]{forward, strafe, jump, sneak}; }
    public boolean equals(MovementInput value) { return value != null && forward == value.forward && strafe == value.strafe && jump == value.jump && sneak == value.sneak; }
}
