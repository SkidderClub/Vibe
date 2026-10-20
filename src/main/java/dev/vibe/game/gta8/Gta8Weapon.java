package dev.vibe.game.gta8;

/** Firearms with realistic magazine sizes, cyclic rates, spread, recoil and reload times. */
public enum Gta8Weapon {
    FISTS("Fists", true, false, 14, 0, 1, .55, 1.5, 0, 0, 0, 0, 0),
    PISTOL("Pistol", false, false, 34, 15, 1, .2, 75, .011, 1.55, 1.7, 450, 2),
    SMG("Micro SMG", false, true, 21, 30, 1, .066, 50, .028, 2.0, .6, 2400, 3),
    RIFLE("Carbine Rifle", false, true, 33, 30, 1, .092, 160, .014, 2.35, .85, 4600, 4),
    SHOTGUN("Pump Shotgun", false, false, 14, 8, 8, .85, 32, .055, 3.2, 3.6, 1900, 6),
    SNIPER("Sniper Rifle", false, false, 125, 5, 1, 1.35, 520, .0006, 3.0, 5.5, 8200, 15);

    public final String title;
    public final boolean melee, automatic;
    public final double damage, interval, range, spread, reload, recoil;
    public final int magazine, pellets, price, ammoPrice;

    Gta8Weapon(String title, boolean melee, boolean automatic, double damage, int magazine, int pellets, double interval, double range,
               double spread, double reload, double recoil, int price, int ammoPrice) {
        this.title = title; this.melee = melee; this.automatic = automatic; this.damage = damage; this.magazine = magazine; this.pellets = pellets;
        this.interval = interval; this.range = range; this.spread = spread; this.reload = reload; this.recoil = recoil; this.price = price; this.ammoPrice = ammoPrice;
    }
    /** Damage falls off beyond the effective range. */
    public double damageAt(double distance) {
        double effective = range * .45;
        return distance <= effective ? damage : damage * Math.max(.35, 1 - (distance - effective) / (range - effective + 1) * .65);
    }
    public boolean twoHanded() { return this == SMG || this == RIFLE || this == SHOTGUN || this == SNIPER; }
    public int ammoBox() { return this == SNIPER ? 10 : this == SHOTGUN ? 16 : magazine * 2; }
}
