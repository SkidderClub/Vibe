package dev.vibe.camera;

/** Pure camera flight simulation. Units are blocks/metres, seconds and degrees. */
public final class FpvFlight {
    /** Optional camera-only movement resolver, called for each physics substep. */
    public interface Movement {
        void move(FpvFlight flight, double x, double y, double z);
        default boolean supports(FpvFlight flight) { return false; }
    }

    public static final double GRAVITY = 9.81;
    public double x, y, z, velocityX, velocityY, velocityZ, throttle, flightTime;
    public double homeX, homeY, homeZ, distanceTravelled, maxSpeed;
    private Quaternion attitude = Quaternion.identity();
    private double angleYaw, anglePitch;
    private double spinX, spinY, spinZ, impactTime, motorCutTime;
    private boolean grounded, landing;

    public void reset(double x, double y, double z, double yaw) {
        this.x = x; this.y = y; this.z = z;
        homeX = x; homeY = y; homeZ = z;
        distanceTravelled = maxSpeed = 0;
        velocityX = velocityY = velocityZ = flightTime = 0;
        angleYaw = yaw;
        anglePitch = 0;
        throttle = 0.5;
        spinX = spinY = spinZ = impactTime = motorCutTime = 0;
        grounded = landing = false;
        attitude = Quaternion.euler(yaw, 0, 0);
    }

    public void look(double yaw, double pitch, boolean angleMode) {
        if (angleMode) {
            angleYaw += yaw;
            anglePitch = clamp(anglePitch + pitch, -70, 70);
        } else {
            rotate(pitch, yaw, 0);
            angleYaw = this.yaw();
            anglePitch = clamp(this.pitch(), -70, 70);
        }
    }

    public void step(double dt, double pitchInput, double rollInput, double throttleInput,
                     boolean angleMode, double rates, double maxTilt, double thrust, double drag) {
        step(dt, pitchInput, rollInput, throttleInput, angleMode, rates, maxTilt, thrust, drag, null);
    }

    public void step(double dt, double pitchInput, double rollInput, double throttleInput,
                     boolean angleMode, double rates, double maxTilt, double thrust, double drag, Movement movement) {
        if (dt <= 0 || !Double.isFinite(dt)) return;
        dt = Math.min(dt, 0.1);
        pitchInput = clamp(pitchInput, -1, 1);
        rollInput = clamp(rollInput, -1, 1);
        throttleInput = clamp(throttleInput, -1, 1);
        if (movement == null) grounded = landing = false;
        if (throttleInput > 0) landing = false;
        int steps = Math.max(1, (int) Math.ceil(dt * 240));
        double h = dt / steps;
        for (int i = 0; i < steps; i++) {
            grounded = movement != null && velocityY <= .1 && movement.supports(this);
            if (grounded && throttleInput <= 0) landing = true;
            impactTime = Math.max(0, impactTime - h);
            motorCutTime = Math.max(0, motorCutTime - h);
            // Neutral still hovers in flight, but a landing cuts the motors until Space is pressed.
            throttle = landing || motorCutTime > 0 ? 0 : clamp(GRAVITY / thrust + throttleInput * .5, 0, 1);
            if (grounded && throttleInput <= 0) {
                // Settle on the nearest side; an inverted crash does not magically right the drone.
                boolean upright = attitude.vector(0, 1, 0)[1] >= 0;
                attitude = attitude.slerp(Quaternion.euler(yaw(), 0, upright ? 0 : 180), 1 - Math.exp(-12 * h));
                angleYaw = yaw(); anglePitch = 0;
                double speed = groundSpeed(), friction = Math.max(0, speed - GRAVITY * .65 * h);
                if (speed > 0) { velocityX *= friction / speed; velocityZ *= friction / speed; }
            } else if (angleMode) {
                Quaternion target = Quaternion.euler(angleYaw, clamp(anglePitch + pitchInput * maxTilt, -80, 80),
                        rollInput * maxTilt);
                attitude = attitude.slerp(target, 1 - Math.exp(-8 * h));
            } else {
                rotate(pitchInput * rates * h, 0, rollInput * rates * h);
                angleYaw = yaw();
            }
            double spin = angularSpeed();
            if (spin > 1e-8) {
                // Impact torque is in world space, independent of the pilot's local stick axes.
                attitude = Quaternion.axis(spinX / spin, spinY / spin, spinZ / spin, Math.toDegrees(spin * h))
                        .multiply(attitude).normalized();
            }
            double spinDamping = Math.exp(-(grounded ? 18 : 5) * h);
            spinX *= spinDamping; spinY *= spinDamping; spinZ *= spinDamping;
            double[] up = attitude.vector(0, 1, 0);
            velocityX += up[0] * thrust * throttle * h;
            velocityY += (up[1] * thrust * throttle - GRAVITY) * h;
            velocityZ += up[2] * thrust * throttle * h;
            double damping = Math.exp(-(drag + speed() * 0.012) * h);
            velocityX *= damping; velocityY *= damping; velocityZ *= damping;
            double previousX = x, previousY = y, previousZ = z;
            if (movement == null) {
                x += velocityX * h; y += velocityY * h; z += velocityZ * h;
            } else {
                movement.move(this, velocityX * h, velocityY * h, velocityZ * h);
            }
            double dx = x - previousX, dy = y - previousY, dz = z - previousZ;
            distanceTravelled += Math.sqrt(dx * dx + dy * dy + dz * dz);
            maxSpeed = Math.max(maxSpeed, speed());
        }
        grounded = movement != null && velocityY <= .1 && movement.supports(this);
        flightTime += dt;
    }

    /** Camera-body impulse: a soft touchdown settles, a hard impact rebounds and induces tumble. */
    public void impact(double nx, double ny, double nz) {
        double closing = -(velocityX * nx + velocityY * ny + velocityZ * nz);
        if (closing <= 0) return;
        double oldX = velocityX, oldY = velocityY, oldZ = velocityZ;
        double restitution = closing > 1.8 ? .12 + .12 * Math.min(1, (closing - 1.8) / 10) : 0;
        double impulse = closing * (1 + restitution);
        velocityX += nx * impulse; velocityY += ny * impulse; velocityZ += nz * impulse;
        double normal = velocityX * nx + velocityY * ny + velocityZ * nz;
        double tangentX = velocityX - nx * normal, tangentY = velocityY - ny * normal,
                tangentZ = velocityZ - nz * normal;
        double tangentSpeed = Math.sqrt(tangentX * tangentX + tangentY * tangentY + tangentZ * tangentZ);
        // Coulomb friction scales with the normal impulse, not the number of contact substeps.
        double friction = Math.min(1, .12 * impulse / Math.max(1e-9, tangentSpeed));
        velocityX -= (velocityX - nx * normal) * friction;
        velocityY -= (velocityY - ny * normal) * friction;
        velocityZ -= (velocityZ - nz * normal) * friction;
        if (ny > .5) { landing = true; grounded = velocityY <= .1; }
        if (closing <= 1.8) return;
        impactTime = .65;
        motorCutTime = Math.max(motorCutTime, Math.min(.55, .15 + closing * .015));
        // Support point of the small frame, with its propeller plane above the centre of mass.
        double[] right = attitude.vector(1, 0, 0), up = attitude.vector(0, 1, 0), back = attitude.vector(0, 0, 1);
        double rx = up[0] * .04, ry = up[1] * .04, rz = up[2] * .04;
        double[][] axes = {right, up, back};
        for (int i = 0; i < axes.length; i++) {
            double[] axis = axes[i];
            double dot = axis[0] * nx + axis[1] * ny + axis[2] * nz;
            double extent = i == 1 ? .10 : .20;
            double lever = -Math.signum(Math.abs(dot) < 1e-6 ? 0 : dot) * extent;
            rx += axis[0] * lever; ry += axis[1] * lever; rz += axis[2] * lever;
        }
        double dx = velocityX - oldX, dy = velocityY - oldY, dz = velocityZ - oldZ;
        spinX += (ry * dz - rz * dy) * 24;
        spinY += (rz * dx - rx * dz) * 24;
        spinZ += (rx * dy - ry * dx) * 24;
        double limit = Math.min(1, 14 / Math.max(1e-9, angularSpeed()));
        spinX *= limit; spinY *= limit; spinZ *= limit;
    }

    public boolean isGrounded() { return grounded; }
    public boolean isCrashing() { return impactTime > 0; }
    public double angularSpeed() { return Math.sqrt(spinX * spinX + spinY * spinY + spinZ * spinZ); }

    private void rotate(double pitch, double yaw, double roll) {
        // Camera-local +X is right, +Y up, -Z forward. Quaternion integration
        // allows Acro flips/inverted flight without an Euler pitch clamp.
        attitude = attitude.multiply(Quaternion.axis(0, 1, 0, -yaw))
                .multiply(Quaternion.axis(1, 0, 0, -pitch))
                .multiply(Quaternion.axis(0, 0, 1, -roll)).normalized();
    }

    public double speed() { return Math.sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ); }
    public double groundSpeed() { return Math.sqrt(velocityX * velocityX + velocityZ * velocityZ); }
    public double relativeAltitude() { return y - homeY; }
    public double homeDistance() { return Math.hypot(homeX - x, homeZ - z); }
    public double homeBearing() { return (Math.toDegrees(Math.atan2(homeX - x, z - homeZ)) + 360) % 360; }
    /** Compass degrees: Minecraft yaw zero faces south, whereas compass zero is north. */
    public static double compassHeading(double yaw) { return ((yaw + 180) % 360 + 360) % 360; }

    public double yaw() {
        double[] forward = attitude.vector(0, 0, -1);
        return Math.toDegrees(Math.atan2(-forward[0], forward[2]));
    }

    public double pitch() {
        return Math.toDegrees(Math.asin(clamp(-attitude.vector(0, 0, -1)[1], -1, 1)));
    }

    public double roll() {
        double[] up = attitude.vector(0, 1, 0);
        double yaw = Math.toRadians(yaw());
        double pitch = Math.toRadians(pitch());
        double levelUp = -up[0] * Math.sin(yaw) * Math.sin(pitch) + up[1] * Math.cos(pitch)
                + up[2] * Math.cos(yaw) * Math.sin(pitch);
        return Math.toDegrees(Math.atan2(-up[0] * Math.cos(yaw) - up[2] * Math.sin(yaw), levelUp));
    }

    public double cameraPitch(double tilt) {
        double[] forward = lens(tilt).vector(0, 0, -1);
        return Math.toDegrees(Math.asin(clamp(-forward[1], -1, 1)));
    }

    public double cameraYaw(double tilt) {
        double[] forward = lens(tilt).vector(0, 0, -1);
        return Math.toDegrees(Math.atan2(-forward[0], forward[2]));
    }

    /** Inverse camera rotation, column-major for OpenGL. Translation is applied by the renderer. */
    public float[] viewMatrix(double tilt) {
        Quaternion lens = lens(tilt);
        double[] right = lens.vector(1, 0, 0), up = lens.vector(0, 1, 0), back = lens.vector(0, 0, 1);
        return new float[] {
                (float) right[0], (float) up[0], (float) back[0], 0,
                (float) right[1], (float) up[1], (float) back[1], 0,
                (float) right[2], (float) up[2], (float) back[2], 0,
                0, 0, 0, 1 };
    }

    private Quaternion lens(double tilt) { return attitude.multiply(Quaternion.axis(1, 0, 0, tilt)); }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }

    private static final class Quaternion {
        final double w, x, y, z;
        Quaternion(double w, double x, double y, double z) { this.w = w; this.x = x; this.y = y; this.z = z; }
        static Quaternion identity() { return new Quaternion(1, 0, 0, 0); }
        static Quaternion axis(double x, double y, double z, double degrees) {
            double half = Math.toRadians(degrees) * 0.5, s = Math.sin(half);
            return new Quaternion(Math.cos(half), x * s, y * s, z * s);
        }
        static Quaternion euler(double yaw, double pitch, double roll) {
            return axis(0, 1, 0, -yaw - 180).multiply(axis(1, 0, 0, -pitch)).multiply(axis(0, 0, 1, -roll));
        }
        Quaternion multiply(Quaternion b) {
            return new Quaternion(w*b.w-x*b.x-y*b.y-z*b.z, w*b.x+x*b.w+y*b.z-z*b.y,
                    w*b.y-x*b.z+y*b.w+z*b.x, w*b.z+x*b.y-y*b.x+z*b.w);
        }
        Quaternion normalized() {
            double n = Math.sqrt(w*w+x*x+y*y+z*z);
            return new Quaternion(w/n, x/n, y/n, z/n);
        }
        double[] vector(double a, double b, double c) {
            double tx = 2*(y*c-z*b), ty = 2*(z*a-x*c), tz = 2*(x*b-y*a);
            return new double[] {a+w*tx+y*tz-z*ty, b+w*ty+z*tx-x*tz, c+w*tz+x*ty-y*tx};
        }
        Quaternion slerp(Quaternion b, double t) {
            double dot = w*b.w+x*b.x+y*b.y+z*b.z;
            if (dot < 0) { b = new Quaternion(-b.w,-b.x,-b.y,-b.z); dot = -dot; }
            if (dot > 0.9995) return new Quaternion(w+(b.w-w)*t, x+(b.x-x)*t, y+(b.y-y)*t, z+(b.z-z)*t).normalized();
            double theta = Math.acos(clamp(dot, -1, 1)), sin = Math.sin(theta);
            double aScale = Math.sin((1-t)*theta)/sin, bScale = Math.sin(t*theta)/sin;
            return new Quaternion(w*aScale+b.w*bScale, x*aScale+b.x*bScale, y*aScale+b.y*bScale, z*aScale+b.z*bScale);
        }
    }
}
