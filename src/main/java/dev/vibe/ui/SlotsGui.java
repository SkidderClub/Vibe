package dev.vibe.ui;

import dev.vibe.game.slots.BookOfVibeSlots;
import dev.vibe.game.slots.BookOfVibeSlots.Outcome;
import dev.vibe.game.slots.BookOfVibeSlots.Symbol;
import dev.vibe.game.slots.BlackjackTable;
import dev.vibe.game.slots.HoldemTable;
import dev.vibe.game.slots.MinesTable;
import dev.vibe.game.slots.PlayingCard;
import dev.vibe.game.slots.RouletteTable;
import dev.vibe.game.slots.SlotConfig;
import dev.vibe.game.slots.SlotEconomy;
import dev.vibe.game.slots.SlotEconomy.Transaction;
import dev.vibe.game.slots.VibeCaseTable;
import dev.vibe.module.impl.BlurModule;
import dev.vibe.module.impl.SlotsModule;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.List;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/** Adult gate, game selector, and the locally persisted Book of Vibe table. */
public final class SlotsGui extends GuiScreen {
    private static final ArcadeGame[] GAMES = ArcadeGame.values();
    private final SlotsModule module;
    private final BookOfVibeSlots machine = new BookOfVibeSlots();
    private final SecureRandom outcomeRandom = new SecureRandom();
    private SlotEconomy economy;
    private GuiTextField ageField;
    private int left, top, panelWidth, panelHeight, selectedGame, selectedBet;
    private Outcome outcome;
    private long spinStartedAt;
    private String pendingRoundId;
    private MinesTable minesRound;
    private String minesRoundId;
    private HoldemTable holdem;
    private String holdemRoundId;
    private BlackjackTable blackjack;
    private String blackjackRoundId;
    private VibeCaseTable.Result caseResult;
    private String caseRoundId;
    private long caseStartedAt;
    private RouletteChoice rouletteChoice = RouletteChoice.RED;
    private int rouletteNumber = 1;
    private String notice = "Choose Book of Vibe to play";

    private enum ArcadeGame {
        BOOK_OF_VIBE("Book of Vibe", "Five reels, five paylines, original symbols, Wild and Rift scatter.", true),
        MINES("Mines", "Reveal safe tiles and cash out before finding a mine.", true),
        POKER("Texas Hold'em", "Virtual poker showdown with a virtual rake built into payouts.", true),
        BLACKJACK("Blackjack", "Virtual dealer table with automatic hit/stand resolution.", true),
        CASES("Vibe Cases", "Original collectible cases with weighted virtual-only rarity tiers.", true),
        ROULETTE("European Roulette", "One-zero colour table with a mathematically negative player return.", true);
        final String title, description; final boolean playable;
        ArcadeGame(String title, String description, boolean playable) { this.title = title; this.description = description; this.playable = playable; }
    }

    private enum RouletteChoice {
        RED("Red"), BLACK("Black"), ODD("Odd"), EVEN("Even"), LOW("1-18"), HIGH("19-36"),
        DOZEN_ONE("1st 12"), DOZEN_TWO("2nd 12"), DOZEN_THREE("3rd 12"),
        COLUMN_ONE("Col 1"), COLUMN_TWO("Col 2"), COLUMN_THREE("Col 3"),
        STRAIGHT("Straight"), SPLIT("Split"), STREET("Street"), CORNER("Corner");
        final String label; RouletteChoice(String label) { this.label = label; }
    }

    public SlotsGui(SlotsModule module) { this.module = module; }

    @Override public void initGui() {
        economy = module.getEconomy();
        layout();
        SlotEconomy.PendingRound pending = economy.getPendingRound();
        if (pending != null) {
            if ("Book of Vibe".equals(pending.getGame())) {
                pendingRoundId = pending.getId();
                spinStartedAt = System.currentTimeMillis();
                notice = "Restoring a previously locked Book of Vibe spin";
            } else if ("Vibe Cases".equals(pending.getGame())) {
                selectedGame = ArcadeGame.CASES.ordinal(); caseRoundId = pending.getId(); caseStartedAt = System.currentTimeMillis();
                notice = "Restoring a previously locked Vibe Case";
            } else {
                // A local interactive table cannot reconstruct its hidden deck
                // or mine layout after an interrupted client session. Settle it
                // as a zero-payout forfeiture, never as an unearned win.
                economy.updatePendingRound(pending.getId(), pending.getGame() + " session ended before completion", 0L, System.currentTimeMillis());
                economy.completeRound(pending.getId(), System.currentTimeMillis());
                notice = "Previous " + pending.getGame() + " round safely closed with no payout";
            }
        }
        if (!economy.isAdultVerified()) {
            ageField = new GuiTextField(0, fontRendererObj, left + 130, top + 114, 72, 18);
            ageField.setMaxStringLength(3); ageField.setFocused(true);
        }
    }

    private void layout() {
        panelWidth = Math.min(Math.max(690, width - 36), 930);
        panelHeight = Math.min(Math.max(440, height - 42), 570);
        left = (width - panelWidth) / 2;
        top = Math.max(20, (height - panelHeight) / 2);
    }

    @Override public void updateScreen() {
        if (ageField != null) ageField.updateCursorCounter();
        if (pendingRoundId != null && !spinning()) finishAnimatedRound();
        if (caseRoundId != null && System.currentTimeMillis() - caseStartedAt >= 1800L) finishCaseOpening();
        super.updateScreen();
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        layout();
        SkeetEditorStyle.backdrop(this, BlurModule.MEME_GAMES, partialTicks);
        if (!economy.isAdultVerified()) drawAgeGate(mouseX, mouseY);
        else drawArcade(mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawAgeGate(int mouseX, int mouseY) {
        int gateLeft = left + (panelWidth - 360) / 2, gateTop = top + (panelHeight - 220) / 2;
        SkeetEditorStyle.window(gateLeft, gateTop, gateLeft + 360, gateTop + 220, "Slots", "virtual tokens only");
        SkeetEditorStyle.panel(gateLeft + 14, gateTop + 34, gateLeft + 346, gateTop + 172, "Adults only");
        fontRendererObj.drawStringWithShadow("Enter your age to access the virtual arcade.", gateLeft + 27, gateTop + 61, SkeetEditorStyle.TEXT);
        fontRendererObj.drawStringWithShadow("Vibe Tokens cannot be bought, sold or withdrawn.", gateLeft + 27, gateTop + 78, SkeetEditorStyle.MUTED);
        fontRendererObj.drawStringWithShadow("Age", gateLeft + 54, gateTop + 119, SkeetEditorStyle.TEXT);
        SkeetEditorStyle.input(gateLeft + 122, gateTop + 111, gateLeft + 212, gateTop + 134);
        ageField.xPosition = gateLeft + 130; ageField.yPosition = gateTop + 114; ageField.drawTextBox();
        SkeetEditorStyle.button(gateLeft + 28, gateTop + 145, gateLeft + 204, gateTop + 168, "Continue", true);
        SkeetEditorStyle.button(gateLeft + 214, gateTop + 145, gateLeft + 332, gateTop + 168, "Close", false);
    }

    private void drawArcade(int mouseX, int mouseY) {
        SkeetEditorStyle.window(left, top, left + panelWidth, top + panelHeight, "Slots", "adult-only • virtual currency only");
        String balanceText = economy.getBalance() + " Vibe Tokens";
        fontRendererObj.drawStringWithShadow(balanceText, left + panelWidth - 14 - fontRendererObj.getStringWidth(balanceText), top + 30, SkeetEditorStyle.accent(.2F));
        int menuLeft = left + 14, menuRight = left + 236, bodyTop = top + 39, bottom = top + panelHeight - 14;
        SkeetEditorStyle.panel(menuLeft, bodyTop, menuRight, bottom, "Games");
        for (int index = 0; index < GAMES.length; index++) {
            ArcadeGame game = GAMES[index]; int y = bodyTop + 22 + index * 42;
            boolean selected = selectedGame == index;
            SkeetEditorStyle.row(menuLeft + 8, y, menuRight - 8, y + 35, selected, hit(menuLeft + 8, y, menuRight - 8, y + 35, mouseX, mouseY));
            fontRendererObj.drawStringWithShadow(game.title, menuLeft + 16, y + 5, selected ? SkeetEditorStyle.accent(.1F) : SkeetEditorStyle.TEXT);
            fontRendererObj.drawStringWithShadow(game.playable ? "Available" : "Rules preview", menuLeft + 16, y + 19, SkeetEditorStyle.MUTED);
        }
        drawWallet(menuLeft + 8, bodyTop + 282, menuRight - 8, bottom - 8, mouseX, mouseY);
        int contentLeft = menuRight + 14, contentRight = left + panelWidth - 14;
        ArcadeGame selected = GAMES[selectedGame];
        SkeetEditorStyle.panel(contentLeft, bodyTop, contentRight, bottom, selected.title);
        if (selected == ArcadeGame.BOOK_OF_VIBE) drawBookOfVibe(contentLeft + 13, bodyTop + 25, contentRight - 13, bottom - 10, mouseX, mouseY);
        else if (selected == ArcadeGame.MINES) drawMines(contentLeft + 13, bodyTop + 25, contentRight - 13, bottom - 10, mouseX, mouseY);
        else if (selected == ArcadeGame.POKER) drawHoldem(contentLeft + 18, bodyTop + 31, contentRight - 18, bottom - 12, mouseX, mouseY);
        else if (selected == ArcadeGame.BLACKJACK) drawBlackjack(contentLeft + 18, bodyTop + 31, contentRight - 18, bottom - 12, mouseX, mouseY);
        else if (selected == ArcadeGame.CASES) drawCases(contentLeft + 18, bodyTop + 31, contentRight - 18, bottom - 12, mouseX, mouseY);
        else drawRoulette(contentLeft + 18, bodyTop + 31, contentRight - 18, bottom - 12, mouseX, mouseY);
    }

    private void drawWallet(int x, int y, int right, int bottom, int mouseX, int mouseY) {
        SkeetEditorStyle.panel(x, y, right, bottom, "Virtual wallet");
        fontRendererObj.drawStringWithShadow("Balance", x + 9, y + 24, SkeetEditorStyle.MUTED);
        fontRendererObj.drawStringWithShadow(String.valueOf(economy.getBalance()), x + 9, y + 39, SkeetEditorStyle.accent(.2F));
        boolean claimable = economy.canClaimAllowance(System.currentTimeMillis());
        SkeetEditorStyle.button(x + 8, y + 55, right - 8, y + 77, claimable ? "Claim 100 tokens" : allowanceLabel(), claimable);
        List<Transaction> history = economy.getHistory();
        if (!history.isEmpty()) {
            Transaction last = history.get(0);
            fontRendererObj.drawStringWithShadow(trim(last.getGame(), right - x - 18), x + 9, y + 89, SkeetEditorStyle.MUTED);
            String net = (last.getNet() >= 0L ? "+" : "") + last.getNet();
            fontRendererObj.drawStringWithShadow(net, right - 9 - fontRendererObj.getStringWidth(net), y + 89, last.getNet() >= 0L ? SkeetEditorStyle.accent(.2F) : 0xFFFF7777);
        }
    }


    private void drawBookOfVibe(int x, int y, int right, int bottom, int mouseX, int mouseY) {
        fontRendererObj.drawStringWithShadow("5 reels • 5 paylines • outcome is secured before it animates", x, y, SkeetEditorStyle.MUTED);
        int reelTop = y + 20, reelHeight = 156, reelGap = 5, reelWidth = (right - x - reelGap * 4) / 5;
        for (int reel = 0; reel < 5; reel++) for (int row = 0; row < 3; row++) {
            int cellLeft = x + reel * (reelWidth + reelGap), cellTop = reelTop + row * 52;
            Symbol symbol = displaySymbol(row, reel);
            SkeetEditorStyle.row(cellLeft, cellTop, cellLeft + reelWidth, cellTop + 48, false, false);
            int color = symbolColor(symbol);
            String label = symbol.getLabel();
            fontRendererObj.drawStringWithShadow(label, cellLeft + (reelWidth - fontRendererObj.getStringWidth(label)) / 2, cellTop + 20, color);
        }
        drawPaylines(x, reelTop, reelWidth, reelGap);
        String result = spinning() ? "Outcome locked • reels are stopping" : outcome == null ? "Select a bet, then spin" : outcome.resultLabel();
        fontRendererObj.drawStringWithShadow(trim(result, right - x), x, reelTop + reelHeight + 10, outcome != null && outcome.getPayout() > 0L ? SkeetEditorStyle.accent(.25F) : SkeetEditorStyle.MUTED);
        int betTop = reelTop + reelHeight + 33;
        drawBetButtons(x, betTop, mouseX, mouseY);
        int spinLeft = x + 34 + SlotConfig.BETS.length * 53 + 10;
        boolean canSpin = pendingRoundId == null && economy.getBalance() >= SlotConfig.BETS[selectedBet];
        SkeetEditorStyle.button(spinLeft, betTop, right, betTop + 22, canSpin ? "Spin Book of Vibe" : "Not enough tokens", canSpin);
        fontRendererObj.drawStringWithShadow(trim(notice, right - x), x, betTop + 32, SkeetEditorStyle.MUTED);
        fontRendererObj.drawStringWithShadow("Rules: Wild substitutes. Rift scatters pay anywhere. Configured return: " + Math.round(BookOfVibeSlots.expectedReturn() * 100.0D) + "%.", x, bottom - 14, SkeetEditorStyle.MUTED);
    }

    private Symbol displaySymbol(int row, int reel) {
        if (outcome == null) {
            long frame = pendingRoundId == null ? 0L : (System.currentTimeMillis() - spinStartedAt) / 90L;
            return Symbol.values()[(int) ((frame + row * 3L + reel) % Symbol.values().length)];
        }
        if (!spinning() || reel < stoppedReels()) return outcome.get(row, reel);
        long frame = (System.currentTimeMillis() - spinStartedAt) / 90L;
        return Symbol.values()[(int) ((frame + row * 2L + reel * 3L) % Symbol.values().length)];
    }
    private boolean spinning() { return pendingRoundId != null && System.currentTimeMillis() - spinStartedAt < 5L * 340L; }
    private int stoppedReels() { return Math.min(5, (int) ((System.currentTimeMillis() - spinStartedAt) / 340L)); }
    private int symbolColor(Symbol symbol) {
        switch (symbol) {
            case VIBE_BOOK: return 0xFFF6D766; case WILD: return SkeetEditorStyle.accent(.35F); case RIFT: return 0xFF75B9FF;
            case CROWN: return 0xFFFFB86C; case COMET: return 0xFFFF8493; default: return SkeetEditorStyle.TEXT;
        }
    }

    private void drawBetButtons(int x, int top, int mouseX, int mouseY) {
        fontRendererObj.drawStringWithShadow("Bet", x, top + 7, SkeetEditorStyle.TEXT);
        for (int index = 0; index < SlotConfig.BETS.length; index++) {
            int bx = x + 34 + index * 53;
            SkeetEditorStyle.button(bx, top, bx + 48, top + 22, String.valueOf(SlotConfig.BETS[index]), selectedBet == index);
        }
    }

    private void drawHoldem(int x, int y, int right, int bottom, int mouseX, int mouseY) {
        fontRendererObj.drawStringWithShadow("Heads-up Texas Hold'em • 3% virtual rake from winning and split pots", x, y, SkeetEditorStyle.MUTED);
        if (holdem == null) {
            drawBetButtons(x, y + 26, mouseX, mouseY);
            int playLeft = x + 34 + SlotConfig.BETS.length * 53 + 10;
            boolean ready = economy.getPendingRound() == null && economy.getBalance() >= SlotConfig.BETS[selectedBet];
            SkeetEditorStyle.button(playLeft, y + 26, right, y + 48, ready ? "Deal Hold'em" : "Not enough tokens", ready);
        } else {
            drawCards("Your hole cards", holdem.getPlayer(), x, y + 25, false);
            drawCards("Community cards", holdem.getBoard(), x, y + 76, false);
            drawCards("House cards", holdem.getHouse(), x, y + 127, !holdem.isComplete());
            String action = holdem.isComplete() ? "Settle showdown" : holdem.getStreet() == HoldemTable.Street.PREFLOP ? "Deal flop" : holdem.getStreet() == HoldemTable.Street.FLOP ? "Deal turn" : holdem.getStreet() == HoldemTable.Street.TURN ? "Deal river" : "Showdown";
            SkeetEditorStyle.button(x, y + 180, right, y + 202, action, true);
        }
        fontRendererObj.drawStringWithShadow(trim(notice, right - x), x, bottom - 30, SkeetEditorStyle.accent(.08F));
        fontRendererObj.drawStringWithShadow("No real-money value • encrypted virtual round history", x, bottom - 14, SkeetEditorStyle.MUTED);
    }

    private void drawBlackjack(int x, int y, int right, int bottom, int mouseX, int mouseY) {
        fontRendererObj.drawStringWithShadow("Six-deck blackjack • dealer stands on 17 • blackjack pays 3:2", x, y, SkeetEditorStyle.MUTED);
        if (blackjack == null) {
            drawBetButtons(x, y + 26, mouseX, mouseY);
            int playLeft = x + 34 + SlotConfig.BETS.length * 53 + 10;
            boolean ready = economy.getPendingRound() == null && economy.getBalance() >= SlotConfig.BETS[selectedBet];
            SkeetEditorStyle.button(playLeft, y + 26, right, y + 48, ready ? "Deal Blackjack" : "Not enough tokens", ready);
        } else {
            drawCards("Dealer", blackjack.getDealerCards(), x, y + 24, !blackjack.isComplete());
            int handY = y + 78;
            for (int index = 0; index < blackjack.getHands().size(); index++) {
                BlackjackTable.Hand hand = blackjack.getHands().get(index);
                drawCards("Player hand " + (index + 1) + " • " + hand.value(), hand.getCards(), x, handY + index * 48, false);
            }
            int actionsTop = y + 180;
            if (blackjack.isComplete()) SkeetEditorStyle.button(x, actionsTop, right, actionsTop + 22, "Settle blackjack", true);
            else {
                int width = (right - x - 12) / 4;
                SkeetEditorStyle.button(x, actionsTop, x + width, actionsTop + 22, "Hit", blackjack.canHit());
                SkeetEditorStyle.button(x + width + 4, actionsTop, x + width * 2 + 4, actionsTop + 22, "Stand", true);
                SkeetEditorStyle.button(x + width * 2 + 8, actionsTop, x + width * 3 + 8, actionsTop + 22, "Double", blackjack.canDouble());
                SkeetEditorStyle.button(x + width * 3 + 12, actionsTop, right, actionsTop + 22, "Split", blackjack.canSplit());
            }
        }
        fontRendererObj.drawStringWithShadow(trim(notice, right - x), x, bottom - 30, SkeetEditorStyle.accent(.08F));
        fontRendererObj.drawStringWithShadow("No real-money value • hits, stand, double and split use one virtual round", x, bottom - 14, SkeetEditorStyle.MUTED);
    }

    private void drawCases(int x, int y, int right, int bottom, int mouseX, int mouseY) {
        fontRendererObj.drawStringWithShadow("Original virtual collectibles • outcome is selected before the case reveal", x, y, SkeetEditorStyle.MUTED);
        int reelTop = y + 29, cellWidth = (right - x - 16) / 5;
        for (int index = 0; index < 5; index++) {
            String item;
            if (caseRoundId != null && System.currentTimeMillis() - caseStartedAt > 1350L && index == 2 && caseResult != null) item = caseResult.getItem();
            else item = new String[] {"Static", "Orbital", "Aurora", "Dream", "Prism"}[(int) ((System.currentTimeMillis() / 110L + index) % 5L)];
            SkeetEditorStyle.row(x + index * (cellWidth + 4), reelTop, x + index * (cellWidth + 4) + cellWidth, reelTop + 62, index == 2 && caseRoundId != null, false);
            fontRendererObj.drawStringWithShadow(item, x + index * (cellWidth + 4) + (cellWidth - fontRendererObj.getStringWidth(item)) / 2, reelTop + 26, index == 2 ? SkeetEditorStyle.accent(.3F) : SkeetEditorStyle.TEXT);
        }
        drawBetButtons(x, reelTop + 82, mouseX, mouseY);
        int openLeft = x + 34 + SlotConfig.BETS.length * 53 + 10;
        boolean ready = caseRoundId == null && economy.getPendingRound() == null && economy.getBalance() >= SlotConfig.BETS[selectedBet];
        SkeetEditorStyle.button(openLeft, reelTop + 82, right, reelTop + 104, ready ? "Open Vibe Case" : "Case is opening", ready);
        if (caseResult != null && caseRoundId == null) fontRendererObj.drawStringWithShadow(caseResult.label(), x, reelTop + 122, caseRarityColor(caseResult.getRarity()));
        fontRendererObj.drawStringWithShadow(trim(notice, right - x), x, bottom - 30, SkeetEditorStyle.accent(.08F));
        fontRendererObj.drawStringWithShadow("No trading, resale, withdrawal or real-world value", x, bottom - 14, SkeetEditorStyle.MUTED);
    }

    private void drawRoulette(int x, int y, int right, int bottom, int mouseX, int mouseY) {
        fontRendererObj.drawStringWithShadow("European roulette • one zero • standard 2.70% virtual house edge", x, y, SkeetEditorStyle.MUTED);
        int boardTop = y + 23, cellWidth = 34;
        SkeetEditorStyle.row(x, boardTop, x + cellWidth, boardTop + 78, rouletteNumber == 0, false);
        fontRendererObj.drawStringWithShadow("0", x + 13, boardTop + 34, 0xFF72D59B);
        for (int number = 1; number <= 36; number++) {
            int index = number - 1, column = index / 3, row = index % 3, cx = x + cellWidth + column * cellWidth, cy = boardTop + row * 26;
            SkeetEditorStyle.row(cx, cy, cx + cellWidth - 2, cy + 24, rouletteNumber == number, false);
            String text = String.valueOf(number); fontRendererObj.drawStringWithShadow(text, cx + (cellWidth - fontRendererObj.getStringWidth(text)) / 2, cy + 8, RouletteTable.isRed(number) ? 0xFFFF6C75 : SkeetEditorStyle.TEXT);
        }
        int controlTop = boardTop + 88;
        RouletteChoice[] values = RouletteChoice.values();
        for (int index = 0; index < values.length; index++) {
            int column = index % 6, row = index / 6, bx = x + column * 73, by = controlTop + row * 26;
            SkeetEditorStyle.button(bx, by, bx + 68, by + 22, values[index].label, rouletteChoice == values[index]);
        }
        fontRendererObj.drawStringWithShadow("Selected number: " + rouletteNumber + " • " + rouletteChoice.label, x, controlTop + 76, SkeetEditorStyle.MUTED);
        drawBetButtons(x, controlTop + 91, mouseX, mouseY);
        int spinLeft = x + 34 + SlotConfig.BETS.length * 53 + 10;
        boolean ready = economy.getPendingRound() == null && economy.getBalance() >= SlotConfig.BETS[selectedBet];
        SkeetEditorStyle.button(spinLeft, controlTop + 91, right, controlTop + 113, ready ? "Spin selected bet" : "Not enough tokens", ready);
        fontRendererObj.drawStringWithShadow(trim(notice, right - x), x, bottom - 30, SkeetEditorStyle.accent(.08F));
        fontRendererObj.drawStringWithShadow("Click a table number for straight / split / street / corner coverage.", x, bottom - 14, SkeetEditorStyle.MUTED);
    }

    private void drawCards(String label, List<PlayingCard> cards, int x, int y, boolean hidden) {
        fontRendererObj.drawStringWithShadow(label, x, y, SkeetEditorStyle.MUTED);
        for (int index = 0; index < cards.size(); index++) {
            int cx = x + 92 + index * 42;
            SkeetEditorStyle.row(cx, y - 4, cx + 36, y + 16, false, false);
            String card = hidden && index == 1 ? "??" : cards.get(index).shortName();
            int color = card.indexOf('♥') >= 0 || card.indexOf('♦') >= 0 ? 0xFFFF7777 : SkeetEditorStyle.TEXT;
            fontRendererObj.drawStringWithShadow(card, cx + (36 - fontRendererObj.getStringWidth(card)) / 2, y + 2, color);
        }
    }

    private int caseRarityColor(VibeCaseTable.Rarity rarity) {
        return rarity == VibeCaseTable.Rarity.PRISMATIC ? SkeetEditorStyle.accent(.4F) : rarity == VibeCaseTable.Rarity.MYTHIC ? 0xFFFFB86C : rarity == VibeCaseTable.Rarity.RARE ? 0xFF75B9FF : SkeetEditorStyle.MUTED;
    }

    private void drawMines(int x, int y, int right, int bottom, int mouseX, int mouseY) {
        fontRendererObj.drawStringWithShadow("5 x 5 grid • 3 mines • cash out whenever you choose", x, y, SkeetEditorStyle.MUTED);
        int gridTop = y + 21, cell = 42, gridSize = cell * 5;
        for (int row = 0; row < 5; row++) for (int column = 0; column < 5; column++) {
            int index = row * 5 + column, cx = x + column * cell, cy = gridTop + row * cell;
            boolean safe = minesRound != null && minesRound.isRevealed(index);
            SkeetEditorStyle.row(cx, cy, cx + cell - 2, cy + cell - 2, safe, hit(cx, cy, cx + cell - 2, cy + cell - 2, mouseX, mouseY));
            if (safe) fontRendererObj.drawStringWithShadow("+", cx + 17, cy + 16, SkeetEditorStyle.accent(.2F));
        }
        int controlsX = x + gridSize + 16, controlsRight = right;
        if (minesRound == null) {
            fontRendererObj.drawStringWithShadow("Place a virtual wager and reveal safe tiles.", controlsX, gridTop + 8, SkeetEditorStyle.TEXT);
            drawBetButtons(controlsX, gridTop + 29, mouseX, mouseY);
            int playLeft = controlsX + 34 + SlotConfig.BETS.length * 53 + 10;
            boolean ready = economy.getPendingRound() == null && economy.getBalance() >= SlotConfig.BETS[selectedBet];
            SkeetEditorStyle.button(playLeft, gridTop + 29, controlsRight, gridTop + 51, ready ? "Start Mines" : "Not enough tokens", ready);
        } else {
            fontRendererObj.drawStringWithShadow("Safe tiles: " + minesRound.safeReveals(), controlsX, gridTop + 8, SkeetEditorStyle.TEXT);
            String potential = "Cash out " + (long) Math.floor(SlotConfig.BETS[selectedBet] * minesRound.multiplier());
            SkeetEditorStyle.button(controlsX, gridTop + 25, controlsRight, gridTop + 47, potential, minesRound.safeReveals() > 0);
            fontRendererObj.drawStringWithShadow("Multiplier " + String.format(java.util.Locale.ROOT, "%.2fx", minesRound.multiplier()), controlsX, gridTop + 61, SkeetEditorStyle.MUTED);
        }
        fontRendererObj.drawStringWithShadow(trim(notice, right - x), x, gridTop + gridSize + 9, SkeetEditorStyle.MUTED);
    }

    /** Draw every configured payline across the reel centres; winning lines brighten after the stop. */
    private void drawPaylines(int x, int reelTop, int reelWidth, int gap) {
        int[][] paylines = BookOfVibeSlots.paylines();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        try {
            GlStateManager.disableTexture2D(); GlStateManager.enableBlend();
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA); GL11.glEnable(GL11.GL_LINE_SMOOTH);
            for (int index = 0; index < paylines.length; index++) {
                boolean winning = outcome != null && !spinning() && outcome.isWinningPayline(index);
                int color = winning ? 0xEEFFFFFF : 0x669DA0A8;
                GL11.glColor4f((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F, (color & 255) / 255.0F, (color >>> 24) / 255.0F);
                GL11.glLineWidth(winning ? 2.4F : 1.0F); GL11.glBegin(GL11.GL_LINE_STRIP);
                for (int reel = 0; reel < 5; reel++) {
                    GL11.glVertex2f(x + reel * (reelWidth + gap) + reelWidth / 2.0F, reelTop + paylines[index][reel] * 52 + 24.0F);
                }
                GL11.glEnd();
            }
        } finally { GlStateManager.enableTexture2D(); GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F); GL11.glPopAttrib(); }
        for (int index = 0; index < paylines.length; index++) {
            boolean winning = outcome != null && !spinning() && outcome.isWinningPayline(index);
            fontRendererObj.drawStringWithShadow(String.valueOf(index + 1), x + 2, reelTop + paylines[index][0] * 52 + 2,
                    winning ? SkeetEditorStyle.accent(.3F) : SkeetEditorStyle.MUTED);
        }
    }

    private String allowanceLabel() {
        long next = economy.nextAllowanceAt(), remaining = Math.max(0L, next - System.currentTimeMillis());
        return remaining == 0L ? "Claim 100 tokens" : "Allowance in " + (remaining / 60000L + 1L) + "m";
    }
    private String trim(String text, int width) { return fontRendererObj.trimStringToWidth(text == null ? "" : text, Math.max(0, width)); }
    private boolean hit(int x, int y, int right, int bottom, int mouseX, int mouseY) { return mouseX >= x && mouseX < right && mouseY >= y && mouseY < bottom; }

    @Override protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        super.mouseClicked(mouseX, mouseY, button);
        if (button != 0) return;
        layout();
        if (!economy.isAdultVerified()) { clickAgeGate(mouseX, mouseY); return; }
        int menuLeft = left + 14, menuRight = left + 236, bodyTop = top + 39, bottom = top + panelHeight - 14;
        for (int index = 0; index < GAMES.length; index++) {
            int y = bodyTop + 22 + index * 42;
            if (hit(menuLeft + 8, y, menuRight - 8, y + 35, mouseX, mouseY)) { selectedGame = index; notice = GAMES[index].playable ? "Book of Vibe selected" : GAMES[index].title + " rules selected"; return; }
        }
        int walletTop = bodyTop + 282;
        if (hit(menuLeft + 16, walletTop + 55, menuRight - 16, walletTop + 77, mouseX, mouseY)) {
            Transaction claim = economy.claimAllowance(System.currentTimeMillis());
            notice = claim == null ? "Allowance is not available yet" : "Added " + claim.getPayout() + " virtual tokens";
            return;
        }
        if (selectedGame == ArcadeGame.MINES.ordinal()) { clickMines(mouseX, mouseY, menuRight + 27, bodyTop + 46, left + panelWidth - 27); return; }
        if (selectedGame == ArcadeGame.POKER.ordinal()) { clickHoldem(mouseX, mouseY, menuRight + 32, bodyTop + 31, left + panelWidth - 32); return; }
        if (selectedGame == ArcadeGame.BLACKJACK.ordinal()) { clickBlackjack(mouseX, mouseY, menuRight + 32, bodyTop + 31, left + panelWidth - 32); return; }
        if (selectedGame == ArcadeGame.CASES.ordinal()) { clickCases(mouseX, mouseY, menuRight + 32, bodyTop + 31, left + panelWidth - 32); return; }
        if (selectedGame == ArcadeGame.ROULETTE.ordinal()) { clickRoulette(mouseX, mouseY, menuRight + 32, bodyTop + 31, left + panelWidth - 32); return; }
        if (pendingRoundId != null) return;
        int x = menuRight + 27, reelTop = bodyTop + 25 + 20, betTop = reelTop + 156 + 33;
        for (int index = 0; index < SlotConfig.BETS.length; index++) {
            int bx = x + 34 + index * 53;
            if (hit(bx, betTop, bx + 48, betTop + 22, mouseX, mouseY)) { selectedBet = index; return; }
        }
        int spinLeft = x + 34 + SlotConfig.BETS.length * 53 + 10, contentRight = left + panelWidth - 27;
        if (hit(spinLeft, betTop, contentRight, betTop + 22, mouseX, mouseY)) startSpin();
    }

    private void clickAgeGate(int mouseX, int mouseY) {
        int gateLeft = left + (panelWidth - 360) / 2, gateTop = top + (panelHeight - 220) / 2;
        if (hit(gateLeft + 214, gateTop + 145, gateLeft + 332, gateTop + 168, mouseX, mouseY)) { mc.displayGuiScreen(null); return; }
        if (!hit(gateLeft + 28, gateTop + 145, gateLeft + 204, gateTop + 168, mouseX, mouseY)) return;
        try {
            int age = Integer.parseInt(ageField.getText().trim());
            if (age < 18) { mc.displayGuiScreen(null); return; }
            if (economy.confirmAdultAge(age)) { ageField = null; notice = "Adult access verified"; }
        } catch (NumberFormatException ignored) { }
    }

    private void startSpin() {
        int bet = SlotConfig.BETS[selectedBet];
        Outcome predetermined = machine.spin(bet, outcomeRandom);
        SlotEconomy.PendingRound pending = economy.beginRound("Book of Vibe", bet, predetermined.resultLabel(), predetermined.getPayout(), System.currentTimeMillis());
        if (pending == null) { notice = economy.getSaveError() == null ? "Unable to place that virtual bet" : economy.getSaveError(); return; }
        outcome = predetermined; pendingRoundId = pending.getId(); spinStartedAt = System.currentTimeMillis();
        notice = "Wager locked • stopping reels";
    }

    private void finishAnimatedRound() {
        Transaction transaction = economy.completeRound(pendingRoundId, System.currentTimeMillis());
        if (transaction == null) { notice = economy.getSaveError() == null ? "Unable to settle the recorded round" : economy.getSaveError(); return; }
        pendingRoundId = null;
        if (transaction.getNet() > 0L) notice = "You won " + transaction.getNet() + " Vibe Tokens";
        else if (transaction.getNet() == 0L) notice = "You broke even • " + transaction.getPayout() + " Vibe Tokens returned";
        else if (transaction.getPayout() == 0L) notice = "You lost " + transaction.getBet() + " Vibe Tokens";
        else notice = "You lost " + (-transaction.getNet()) + " Vibe Tokens";
    }

    private void clickHoldem(int mouseX, int mouseY, int x, int y, int right) {
        if (holdem == null) {
            int betTop = y + 26;
            if (chooseBet(mouseX, mouseY, x, betTop)) return;
            int playLeft = x + 34 + SlotConfig.BETS.length * 53 + 10;
            if (!hit(playLeft, betTop, right, betTop + 22, mouseX, mouseY)) return;
            int bet = SlotConfig.BETS[selectedBet];
            SlotEconomy.PendingRound pending = economy.beginRound("Texas Hold'em", bet, "Texas Hold'em showdown pending", 0L, System.currentTimeMillis());
            if (pending == null) { notice = "Unable to deal Hold'em"; return; }
            holdem = new HoldemTable(bet, outcomeRandom); holdemRoundId = pending.getId(); notice = "Hold'em dealt • reveal the flop"; return;
        }
        if (!hit(x, y + 180, right, y + 202, mouseX, mouseY)) return;
        if (!holdem.isComplete()) { holdem.advance(); notice = holdem.isComplete() ? "Showdown ready" : "Texas Hold'em " + holdem.getStreet().name().toLowerCase(java.util.Locale.ROOT); return; }
        long payout = holdem.payout();
        if (!economy.updatePendingRound(holdemRoundId, holdem.result(), payout, System.currentTimeMillis())) { notice = "Unable to record showdown"; return; }
        finishTableRound(holdemRoundId); holdem = null; holdemRoundId = null;
    }

    private void clickBlackjack(int mouseX, int mouseY, int x, int y, int right) {
        if (blackjack == null) {
            int betTop = y + 26;
            if (chooseBet(mouseX, mouseY, x, betTop)) return;
            int dealLeft = x + 34 + SlotConfig.BETS.length * 53 + 10;
            if (!hit(dealLeft, betTop, right, betTop + 22, mouseX, mouseY)) return;
            int bet = SlotConfig.BETS[selectedBet];
            SlotEconomy.PendingRound pending = economy.beginRound("Blackjack", bet, "Blackjack in progress", 0L, System.currentTimeMillis());
            if (pending == null) { notice = "Unable to deal Blackjack"; return; }
            blackjack = new BlackjackTable(bet, outcomeRandom); blackjackRoundId = pending.getId(); notice = blackjack.isComplete() ? "Natural blackjack or dealer blackjack • settle" : "Blackjack dealt"; return;
        }
        int actionsTop = y + 180;
        if (!hit(x, actionsTop, right, actionsTop + 22, mouseX, mouseY)) return;
        if (blackjack.isComplete()) { settleBlackjack(); return; }
        int width = (right - x - 12) / 4;
        if (hit(x, actionsTop, x + width, actionsTop + 22, mouseX, mouseY)) blackjack.hit();
        else if (hit(x + width + 4, actionsTop, x + width * 2 + 4, actionsTop + 22, mouseX, mouseY)) blackjack.stand();
        else if (hit(x + width * 2 + 8, actionsTop, x + width * 3 + 8, actionsTop + 22, mouseX, mouseY) && blackjack.canDouble()) {
            long extra = SlotConfig.BETS[selectedBet];
            if (economy.increasePendingWager(blackjackRoundId, extra, System.currentTimeMillis())) blackjack.doubleDown(); else notice = "Not enough tokens to double";
        } else if (hit(x + width * 3 + 12, actionsTop, right, actionsTop + 22, mouseX, mouseY) && blackjack.canSplit()) {
            long extra = SlotConfig.BETS[selectedBet];
            if (economy.increasePendingWager(blackjackRoundId, extra, System.currentTimeMillis())) blackjack.split(); else notice = "Not enough tokens to split";
        }
        if (blackjack.isComplete()) notice = "Blackjack complete • settle the cards";
    }

    private void settleBlackjack() {
        long payout = blackjack.payout(); String result = blackjack.result();
        if (!economy.updatePendingRound(blackjackRoundId, result, payout, System.currentTimeMillis())) { notice = "Unable to record Blackjack"; return; }
        finishTableRound(blackjackRoundId); blackjack = null; blackjackRoundId = null;
    }

    private void clickCases(int mouseX, int mouseY, int x, int y, int right) {
        int betTop = y + 111;
        if (chooseBet(mouseX, mouseY, x, betTop)) return;
        int openLeft = x + 34 + SlotConfig.BETS.length * 53 + 10;
        if (!hit(openLeft, betTop, right, betTop + 22, mouseX, mouseY) || caseRoundId != null) return;
        int bet = SlotConfig.BETS[selectedBet]; caseResult = VibeCaseTable.open(bet, outcomeRandom);
        SlotEconomy.PendingRound pending = economy.beginRound("Vibe Cases", bet, caseResult.label(), caseResult.getPayout(), System.currentTimeMillis());
        if (pending == null) { notice = "Unable to open Vibe Case"; return; }
        caseRoundId = pending.getId(); caseStartedAt = System.currentTimeMillis(); notice = "Case outcome locked • revealing item";
    }

    private void finishCaseOpening() {
        Transaction transaction = economy.completeRound(caseRoundId, System.currentTimeMillis());
        if (transaction == null) { notice = "Unable to settle Vibe Case"; return; }
        caseRoundId = null;
        notice = transaction.getResult() + (transaction.getNet() >= 0L ? " • won " + transaction.getNet() : " • lost " + (-transaction.getNet()));
    }

    private void clickRoulette(int mouseX, int mouseY, int x, int y, int right) {
        int boardTop = y + 23, cellWidth = 34;
        if (hit(x, boardTop, x + cellWidth, boardTop + 78, mouseX, mouseY)) { rouletteNumber = 0; return; }
        for (int number = 1; number <= 36; number++) {
            int index = number - 1, column = index / 3, row = index % 3, cx = x + cellWidth + column * cellWidth, cy = boardTop + row * 26;
            if (hit(cx, cy, cx + cellWidth - 2, cy + 24, mouseX, mouseY)) { rouletteNumber = number; return; }
        }
        int controlTop = boardTop + 88;
        RouletteChoice[] choices = RouletteChoice.values();
        for (int index = 0; index < choices.length; index++) {
            int column = index % 6, row = index / 6, bx = x + column * 73, by = controlTop + row * 26;
            if (hit(bx, by, bx + 68, by + 22, mouseX, mouseY)) { rouletteChoice = choices[index]; return; }
        }
        int betTop = controlTop + 91;
        if (chooseBet(mouseX, mouseY, x, betTop)) return;
        int spinLeft = x + 34 + SlotConfig.BETS.length * 53 + 10;
        if (!hit(spinLeft, betTop, right, betTop + 22, mouseX, mouseY)) return;
        int bet = SlotConfig.BETS[selectedBet]; RouletteTable.Result result = RouletteTable.spin(java.util.Collections.singletonList(rouletteBet(bet)), outcomeRandom);
        SlotEconomy.PendingRound pending = economy.beginRound("European Roulette", bet, result.label(), result.getPayout(), System.currentTimeMillis());
        if (pending == null) { notice = "Unable to spin roulette"; return; }
        finishTableRound(pending.getId());
    }

    private RouletteTable.Bet rouletteBet(long amount) {
        switch (rouletteChoice) {
            case RED: return RouletteTable.colour(true, amount);
            case BLACK: return RouletteTable.colour(false, amount);
            case ODD: return new RouletteTable.Bet(RouletteTable.Type.ODD, amount, rangeFiltered(1, 36, 2));
            case EVEN: return new RouletteTable.Bet(RouletteTable.Type.EVEN, amount, rangeFiltered(2, 36, 2));
            case LOW: return new RouletteTable.Bet(RouletteTable.Type.LOW, amount, range(1, 18));
            case HIGH: return new RouletteTable.Bet(RouletteTable.Type.HIGH, amount, range(19, 36));
            case DOZEN_ONE: return new RouletteTable.Bet(RouletteTable.Type.DOZEN, amount, range(1, 12));
            case DOZEN_TWO: return new RouletteTable.Bet(RouletteTable.Type.DOZEN, amount, range(13, 24));
            case DOZEN_THREE: return new RouletteTable.Bet(RouletteTable.Type.DOZEN, amount, range(25, 36));
            case COLUMN_ONE: return new RouletteTable.Bet(RouletteTable.Type.COLUMN, amount, rangeFiltered(1, 34, 3));
            case COLUMN_TWO: return new RouletteTable.Bet(RouletteTable.Type.COLUMN, amount, rangeFiltered(2, 35, 3));
            case COLUMN_THREE: return new RouletteTable.Bet(RouletteTable.Type.COLUMN, amount, rangeFiltered(3, 36, 3));
            case SPLIT: { int first = rouletteNumber == 0 ? 0 : rouletteNumber, second = first == 0 ? 1 : first % 3 == 0 ? first - 1 : first + 1; return new RouletteTable.Bet(RouletteTable.Type.SPLIT, amount, first, second); }
            case STREET: { int start = rouletteNumber == 0 ? 1 : ((rouletteNumber - 1) / 3) * 3 + 1; return new RouletteTable.Bet(RouletteTable.Type.STREET, amount, start, start + 1, start + 2); }
            case CORNER: { int base = rouletteNumber == 0 ? 1 : Math.max(1, Math.min(32, rouletteNumber - (rouletteNumber - 1) % 3)); return new RouletteTable.Bet(RouletteTable.Type.CORNER, amount, base, base + 1, base + 3, base + 4); }
            default: return new RouletteTable.Bet(RouletteTable.Type.STRAIGHT, amount, rouletteNumber);
        }
    }
    private int[] range(int first, int last) { int[] values = new int[last - first + 1]; for (int index = 0; index < values.length; index++) values[index] = first + index; return values; }
    private int[] rangeFiltered(int first, int last, int step) { int[] values = new int[(last - first) / step + 1]; for (int index = 0; index < values.length; index++) values[index] = first + index * step; return values; }

    private boolean chooseBet(int mouseX, int mouseY, int x, int betTop) {
        for (int index = 0; index < SlotConfig.BETS.length; index++) {
            int bx = x + 34 + index * 53;
            if (hit(bx, betTop, bx + 48, betTop + 22, mouseX, mouseY)) { selectedBet = index; return true; }
        }
        return false;
    }

    private void finishTableRound(String roundId) {
        Transaction transaction = economy.completeRound(roundId, System.currentTimeMillis());
        if (transaction == null) { notice = "Unable to settle the virtual round"; return; }
        notice = transaction.getResult() + (transaction.getNet() > 0L ? " • won " + transaction.getNet() : transaction.getNet() < 0L ? " • lost " + (-transaction.getNet()) : " • push");
    }

    private void clickMines(int mouseX, int mouseY, int x, int gridTop, int right) {
        int cell = 42, gridSize = cell * 5;
        if (minesRound == null) {
            int controlsX = x + gridSize + 16, betTop = gridTop + 29;
            for (int index = 0; index < SlotConfig.BETS.length; index++) {
                int bx = controlsX + 34 + index * 53;
                if (hit(bx, betTop, bx + 48, betTop + 22, mouseX, mouseY)) { selectedBet = index; return; }
            }
            int playLeft = controlsX + 34 + SlotConfig.BETS.length * 53 + 10;
            if (!hit(playLeft, betTop, right, betTop + 22, mouseX, mouseY)) return;
            int bet = SlotConfig.BETS[selectedBet];
            SlotEconomy.PendingRound pending = economy.beginRound("Mines", bet, "Mine hit", 0L, System.currentTimeMillis());
            if (pending == null) { notice = "Unable to start Mines"; return; }
            minesRound = new MinesTable(outcomeRandom); minesRoundId = pending.getId(); notice = "Mines round started"; return;
        }
        if (mouseX >= x && mouseX < x + gridSize && mouseY >= gridTop && mouseY < gridTop + gridSize) {
            int column = (mouseX - x) / cell, row = (mouseY - gridTop) / cell, index = row * 5 + column;
            if (minesRound.isRevealed(index)) return;
            if (minesRound.reveal(index)) { notice = "Safe tile • cash out or continue"; }
            else finishMines("Mine hit", 0L);
            return;
        }
        int controlsX = x + gridSize + 16;
        if (hit(controlsX, gridTop + 25, right, gridTop + 47, mouseX, mouseY) && minesRound.safeReveals() > 0) {
            finishMines("Mines cashout after " + minesRound.safeReveals() + " safe tiles", minesRound.cashout(SlotConfig.BETS[selectedBet]));
        }
    }

    private void finishMines(String result, long payout) {
        if (!economy.updatePendingRound(minesRoundId, result, payout, System.currentTimeMillis())) { notice = "Unable to record Mines result"; return; }
        Transaction transaction = economy.completeRound(minesRoundId, System.currentTimeMillis());
        minesRound = null; minesRoundId = null;
        notice = transaction == null ? "Unable to settle Mines" : transaction.getResult() + (transaction.getNet() >= 0 ? " • won " + transaction.getNet() : " • lost " + (-transaction.getNet()));
    }

    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) { mc.displayGuiScreen(null); return; }
        if (ageField != null) ageField.textboxKeyTyped(typedChar, keyCode);
        super.keyTyped(typedChar, keyCode);
    }
    @Override public void onGuiClosed() { if (module.isEnabled()) module.setEnabled(false); super.onGuiClosed(); }
    @Override public boolean doesGuiPauseGame() { return false; }
}
