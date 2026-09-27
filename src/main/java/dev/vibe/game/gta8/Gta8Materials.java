package dev.vibe.game.gta8;

/** Material identifiers shared with {@code materials.glsl}. Add {@link #INDOOR} for interior lighting. */
final class Gta8Materials {
    static final int PAINT = 0, ASPHALT = 1, CONCRETE = 2, BRICK = 3, PLASTER = 4, FACADE = 5, CURTAIN = 6, ROOF = 7, GRASS = 8,
            METAL = 9, WOOD = 10, EMISSIVE = 11, SAND = 12, TERRAIN = 13, FOLIAGE = 14, CARPAINT = 15, CHROME = 16, RUBBER = 17,
            CARGLASS = 18, CHARACTER = 19, CORRUGATED = 20, SHOPFRONT = 21, SIGN = 22, TILEROOF = 23, SIDING = 24, FLOORTILE = 25,
            PAVERS = 26, DIRT = 27, BARK = 28, POOL = 29, CHAINLINK = 30, DECK = 31, LAMP = 32, ROADPLAIN = 33, PLASTIC = 34,
            GLASSPANE = 35, CONCRETE_WALL = 36, MARBLE = 37, CLOTH = 38, STONE = 39;
    static final int INDOOR = 128;
    /** Emissive modes (material parameter). */
    static final int ALWAYS = 0, NIGHT = 1, SIGNAL = 2, VEHICLE = 3, BEACON = 4, NEON = 5;
    /** Foliage atlas cells. */
    static final int LEAVES = 0, FROND = 1, NEEDLES = 2, SHRUB = 3;

    private Gta8Materials() { }

    static int wall(int wallType) {
        switch (wallType & 3) { case 0: return PLASTER; case 1: return BRICK; case 2: return CONCRETE_WALL; default: return STONE; }
    }
}
