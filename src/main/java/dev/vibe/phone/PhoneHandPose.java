package dev.vibe.phone;

/** A left thumb points up from the grip and sweeps within a natural reach. */
final class PhoneHandPose {
    float gripX = -9, gripY = 344, angle = 40, curl;

    void advance(float focusX, float focusY, double sinceInput, boolean press, double elapsed) {
        boolean reaching = sinceInput >= 0 && sinceInput < .85;
        float targetAngle = reaching ? 30 + Math.max(0, Math.min(1, focusX / 232)) * 50 : 40;
        // Move the whole grip with the thumb. It must never float away from the palm.
        float targetY = reaching ? Math.max(220, Math.min(380, focusY))
                + (float) Math.cos(Math.toRadians(targetAngle)) * 43 : 344;
        float blend = (float) (1 - Math.exp(-Math.max(0, Math.min(.1, elapsed)) * 16));
        gripX += ((reaching ? 6 : -9) - gripX) * blend;
        gripY += (targetY - gripY) * blend;
        angle += (targetAngle - angle) * blend;
        curl = press && sinceInput >= 0 && sinceInput < .24
                ? (float) Math.sin(sinceInput / .24 * Math.PI) * .48F : 0;
    }

    void reset() { gripX = -9; gripY = 344; angle = 40; curl = 0; }
}
