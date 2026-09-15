package dev.vibe.game.slots;

/**
 * Local, virtual-only balancing for Slots.  These values deliberately live
 * outside the renderer so a future server-backed implementation can share the
 * same rules without trusting a UI supplied payout.
 */
public final class SlotConfig {
    public static final long STARTING_BALANCE = 1_000L;
    public static final long FREE_ALLOWANCE = 100L;
    public static final long FREE_ALLOWANCE_COOLDOWN_MS = 4L * 60L * 60L * 1000L;
    public static final int[] BETS = {10, 25, 50, 100};
    public static final double MAX_PLAYER_RETURN = 0.95D;

    private SlotConfig() { }

    public static void validate() {
        if (STARTING_BALANCE < 0L || FREE_ALLOWANCE <= 0L || FREE_ALLOWANCE_COOLDOWN_MS <= 0L) {
            throw new IllegalStateException("Invalid virtual currency configuration");
        }
        if (BETS.length == 0) throw new IllegalStateException("Slots requires at least one bet");
        int previous = 0;
        for (int bet : BETS) {
            if (bet <= previous) throw new IllegalStateException("Slot bets must be positive and ascending");
            previous = bet;
        }
        BookOfVibeSlots.validateConfiguration();
        VibeCaseTable.validate();
        if (MinesTable.expectedReturn() >= 1.0D || HoldemTable.expectedReturn() >= 1.0D || 36.0D / 37.0D >= 1.0D) {
            throw new IllegalStateException("Every virtual arcade table must retain a house edge");
        }
    }
}
