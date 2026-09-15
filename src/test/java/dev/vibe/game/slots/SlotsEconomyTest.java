package dev.vibe.game.slots;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import org.junit.Test;

public class SlotsEconomyTest {
    @Test public void bookOfVibeConfigurationHasNegativeExpectedValue() {
        BookOfVibeSlots.validateConfiguration();
        assertTrue(BookOfVibeSlots.expectedReturn() < SlotConfig.MAX_PLAYER_RETURN);
        assertTrue(BookOfVibeSlots.expectedReturn() < 1.0D);
    }

    @Test public void walletIsEncryptedAndSettlesEachSpinExactlyOnce() throws Exception {
        Path root = Files.createTempDirectory("vibe-slots-test");
        Path profile = root.resolve("slots-economy.dat");
        SlotEconomy economy = SlotEconomy.load(profile);
        assertFalse(economy.isAdultVerified());
        assertFalse(economy.confirmAdultAge(17));
        assertTrue(economy.confirmAdultAge(18));
        assertTrue(SlotEconomy.encryptedFile(profile));

        long before = economy.getBalance();
        BookOfVibeSlots.Outcome outcome = new BookOfVibeSlots().spin(10, new SecureRandom());
        SlotEconomy.Transaction transaction = economy.resolveBookOfVibeSpin(10, outcome, 1_000L);
        assertNotNull(transaction);
        assertEquals(before - 10L + outcome.getPayout(), economy.getBalance());
        assertEquals(1, economy.getHistory().size());
        assertEquals(outcome.getPayout() - 10L, transaction.getNet());

        SlotEconomy restored = SlotEconomy.load(profile);
        assertTrue(restored.isAdultVerified());
        assertEquals(economy.getBalance(), restored.getBalance());
        assertEquals(1, restored.getHistory().size());
    }

    @Test public void lockedSpinCreditsOnlyWhenItsAnimationCompletes() throws Exception {
        Path profile = Files.createTempDirectory("vibe-slots-animation").resolve("slots-economy.dat");
        SlotEconomy economy = SlotEconomy.load(profile);
        assertTrue(economy.confirmAdultAge(20));
        long before = economy.getBalance();
        SlotEconomy.PendingRound pending = economy.beginRound("Book of Vibe", 25, "Test win", 100L, 50_000L);
        assertNotNull(pending);
        assertEquals(before - 25L, economy.getBalance());
        assertEquals(null, economy.completeRound("different-round", 50_001L));
        assertEquals(before - 25L, economy.getBalance());
        assertNotNull(economy.completeRound(pending.getId(), 50_002L));
        assertEquals(before + 75L, economy.getBalance());
    }

    @Test public void allowanceRejectsClockRollbackAndNeverCreatesNegativeBalance() throws Exception {
        Path profile = Files.createTempDirectory("vibe-slots-clock").resolve("slots-economy.dat");
        SlotEconomy economy = SlotEconomy.load(profile);
        assertTrue(economy.confirmAdultAge(21));
        assertNotNull(economy.claimAllowance(10_000L));
        assertFalse(economy.canClaimAllowance(9_000L));
        assertEquals(null, economy.claimAllowance(9_000L));
        assertTrue(economy.getBalance() >= 0L);
    }
}
