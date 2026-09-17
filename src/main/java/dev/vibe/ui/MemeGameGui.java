package dev.vibe.ui;

import com.mojang.authlib.GameProfile;
import dev.vibe.game.meme.ChessGameState;
import dev.vibe.game.meme.ConnectFourState;
import dev.vibe.game.meme.GameType;
import dev.vibe.game.meme.MemeGamePreferences;
import dev.vibe.game.meme.TicTacToeState;
import dev.vibe.module.impl.BlurModule;
import dev.vibe.module.impl.MemeGameModule;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** A compact, persistent game table shared by the three Meme board games. */
public final class MemeGameGui extends GuiScreen {
    private static final int CHESS_LIGHT = 0xFFF0D9B5;
    private static final int CHESS_DARK = 0xFF789656;

    private final MemeGameModule module;
    private int left, top, panelWidth, panelHeight;
    private int listLeft, listRight, listTop, listBottom, sideLeft, contentRight;
    private String selectedPlayer;
    private boolean robotStarts = true;
    private int selectedRow = -1, selectedColumn = -1;
    private int playerScroll;
    private int matchScroll;
    private float uiScale = 1;
    private boolean draggingPlayers, draggingMatch;
    private char promotion = 'Q';

    public MemeGameGui(MemeGameModule module) { this.module = module; }

    @Override public void initGui() {
        layout();
        if (module.getPendingOpponent() != null) selectedPlayer = module.getPendingOpponent();
    }

    private void layout() {
        uiScale = Math.max(.1F, Math.min(1, Math.min((width - 16) / 680.0F, (height - 16) / 430.0F)));
        int logicalWidth = Math.round(width / uiScale), logicalHeight = Math.round(height / uiScale);
        panelWidth = Math.min(Math.max(680, logicalWidth - 28), 1050);
        panelHeight = Math.min(Math.max(430, logicalHeight - 34), 650);
        left = (logicalWidth - panelWidth) / 2;
        top = (logicalHeight - panelHeight) / 2;
        listLeft = left + 16;
        listRight = left + Math.max(406, panelWidth * 58 / 100);
        sideLeft = listRight + 14;
        contentRight = left + panelWidth - 16;
        listTop = top + 64;
        listBottom = top + panelHeight - 18;
        matchScroll = Math.max(0, Math.min(matchMaximumScroll(), matchScroll));
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        layout();
        SkeetEditorStyle.backdrop(this, BlurModule.MEME_GAMES, partialTicks);
        mouseX = (int) (mouseX / uiScale); mouseY = (int) (mouseY / uiScale);
        GL11.glPushMatrix();
        GL11.glScalef(uiScale, uiScale, 1);
        try {
            String detail = module.hasActiveGame() ? opponentDetail() : "Chat invitations • local robot • saved match";
            SkeetEditorStyle.window(left, top, left + panelWidth, top + panelHeight, module.getType().getDisplayName(), detail);
            if (module.hasActiveGame()) drawGame(mouseX, mouseY); else drawLobby(mouseX, mouseY);
        } finally { GL11.glPopMatrix(); }
    }

    private void drawLobby(int mouseX, int mouseY) {
        SkeetEditorStyle.panel(listLeft, top + 38, listRight, listBottom, "Players in your game");
        List<NetworkPlayerInfo> players = players();
        int columns = playerColumns(), cardWidth = playerCardWidth();
        int rowHeight = 40;
        int rows = (players.size() + columns - 1) / columns;
        int contentHeight = rows * rowHeight;
        int viewportHeight = Math.max(1, listBottom - listTop - 4);
        int maxScroll = Math.max(0, contentHeight - viewportHeight);
        playerScroll = Math.max(0, Math.min(maxScroll, playerScroll));

        if (players.isEmpty()) {
            fontRendererObj.drawStringWithShadow("No other players are visible in this game.", listLeft + 11, listTop + 7, SkeetEditorStyle.MUTED);
            fontRendererObj.drawStringWithShadow("A local robot match is always available.", listLeft + 11, listTop + 21, SkeetEditorStyle.MUTED);
        } else {
            try (GuiClip clip = clip(listLeft + 2, listTop, listRight - 8, listBottom - 4)) {
                for (int index = 0; index < players.size(); index++) {
                    int column = index % columns, row = index / columns;
                    int x = listLeft + 10 + column * (cardWidth + 6);
                    int y = listTop + row * rowHeight - playerScroll;
                    if (y + 34 < listTop || y > listBottom) continue;
                    NetworkPlayerInfo info = players.get(index);
                    GameProfile profile = info.getGameProfile();
                    String name = profile.getName();
                    boolean selected = name.equalsIgnoreCase(selectedPlayer);
                    SkeetEditorStyle.row(x, y, x + cardWidth, y + 34, selected, hit(x, y, x + cardWidth, y + 34, mouseX, mouseY));
                    SkinHeads.draw(profile.getId(), name, x + 6, y + 6, 22);
                    fontRendererObj.drawStringWithShadow(trim(name, cardWidth - 41), x + 35, y + 8, selected ? SkeetEditorStyle.accent(.15F) : SkeetEditorStyle.TEXT);
                    fontRendererObj.drawStringWithShadow(isSelf(name) ? "You" : selected ? "Selected" : "Click to select", x + 35, y + 21, SkeetEditorStyle.MUTED);
                }
            }
            if (maxScroll > 0) drawScrollBar(listRight - 6, listTop, listBottom - 4, playerScroll, maxScroll, viewportHeight);
        }

        int matchBottom = matchBottom();
        SkeetEditorStyle.panel(sideLeft, top + 38, contentRight, matchBottom, "Start a match");
        int x = sideLeft + 12, right = contentRight - 12;
        try (GuiClip clip = clip(sideLeft + 2, top + 60, contentRight - 8, listBottom - 36)) {
        int multiplayerTop = multiplayerTop(), multiplayerBottom = multiplayerBottom();
        SkeetEditorStyle.panel(x, multiplayerTop, right, multiplayerBottom, "Multiplayer");
        drawSharedChatControls(x + 9, multiplayerTop + 23, right - 9, mouseX, mouseY);
        int playerTop = multiplayerTop + 104;
        fontRendererObj.drawStringWithShadow("Player match", x + 9, playerTop, SkeetEditorStyle.MUTED);
        drawPlayerPreview(x + 9, playerTop + 11, right - 9);
        button(x + 9, playerTop + 55, right - 9, "Request player", selectedPlayer != null);
        if (module.getPendingOpponent() != null) {
            fontRendererObj.drawStringWithShadow(trim(module.getPendingOpponent() + " invited you", right - x - 18), x + 9, playerTop + 82, SkeetEditorStyle.accent(.35F));
            button(x + 9, playerTop + 96, right - 9, "Accept and play second", true);
        }

        int singleTop = singleplayerTop(), singleBottom = singleTop + 105;
        SkeetEditorStyle.panel(x, singleTop, right, singleBottom, "Singleplayer");
        drawRobotControls(x + 9, singleTop + 23, right - 9, mouseX, mouseY);
        int noticeY = singleBottom + 8;
        fontRendererObj.drawStringWithShadow(trim(module.getNotice(), right - x), x, noticeY, SkeetEditorStyle.accent(.08F));
        }
        if (matchMaximumScroll() > 0) drawScrollBar(contentRight - 6, top + 60, listBottom - 36, matchScroll, matchMaximumScroll(), listBottom - 36 - (top + 60));
        button(right - 84, matchBottom - 24, right, "Close", false);
    }

    private void drawPlayerPreview(int x, int y, int right) {
        String name = selectedPlayer == null ? "Select a player from the list" : selectedPlayer;
        SkeetEditorStyle.row(x, y, right, y + 36, selectedPlayer != null, false);
        NetworkPlayerInfo found = findPlayer(selectedPlayer);
        if (found != null) SkinHeads.draw(found.getGameProfile().getId(), name, x + 7, y + 7, 22);
        else {
            Gui.drawRect(x + 8, y + 8, x + 29, y + 29, 0xFF32333A);
            fontRendererObj.drawStringWithShadow("?", x + 15, y + 14, SkeetEditorStyle.MUTED);
        }
        fontRendererObj.drawStringWithShadow(trim(name, right - x - 45), x + 38, y + 10, selectedPlayer == null ? SkeetEditorStyle.MUTED : SkeetEditorStyle.TEXT);
        if (selectedPlayer != null) fontRendererObj.drawStringWithShadow("You start as first player", x + 38, y + 21, SkeetEditorStyle.MUTED);
    }

    private int playerColumns() { return listRight - listLeft >= 334 ? 2 : 1; }
    private int playerCardWidth() { return (listRight - listLeft - 28 - (playerColumns() - 1) * 6) / playerColumns(); }
    private boolean isSelf(String name) { return mc.thePlayer != null && mc.thePlayer.getName().equalsIgnoreCase(name); }
    private GuiClip clip(int x, int y, int right, int bottom) {
        int sx = (int) Math.ceil(x * uiScale), sy = (int) Math.ceil(y * uiScale);
        return new GuiClip(sx, sy, Math.max(0, (int) (right * uiScale) - sx), Math.max(0, (int) (bottom * uiScale) - sy));
    }
    private int multiplayerTop() { return top + 60 - matchScroll; }
    private int multiplayerBottom() { return multiplayerTop() + (module.getPendingOpponent() == null ? 187 : 228); }
    private int singleplayerTop() { return multiplayerBottom() + 9; }
    /** The fixed footer stays accessible while the controls scroll within the card. */
    private int matchBottom() { return listBottom; }
    private int matchMaximumScroll() {
        int content = (module.getPendingOpponent() == null ? 187 : 228) + 9 + 105 + 24;
        return Math.max(0, content - (listBottom - 36 - (top + 60)));
    }

    private void drawRobotControls(int x, int y, int right, int mouseX, int mouseY) {
        MemeGamePreferences preferences = module.getPreferences();
        SkeetEditorStyle.row(x, y, right, y + 24, false, false);
        fontRendererObj.drawStringWithShadow("Strength", x + 8, y + 7, SkeetEditorStyle.TEXT);
        drawSlider(x + 70, y + 5, right - 8, preferences.getRobotStrength(), 1, 10, preferences.getRobotStrength() + "/10");
        drawCheckbox(x, y + 29, "You start", robotStarts, hit(x, y + 25, right, y + 48, mouseX, mouseY));
        button(x, y + 53, right, "Play robot", true);
    }

    private void drawSharedChatControls(int x, int y, int right, int mouseX, int mouseY) {
        MemeGamePreferences preferences = module.getPreferences();
        drawCheckbox(x, y, "Check sent message", preferences.isCheckMessage(), hit(x, y - 4, right, y + 18, mouseX, mouseY));
        drawCheckbox(x, y + 21, "Add garbage", preferences.isAddGarbage(), hit(x, y + 17, right, y + 39, mouseX, mouseY));
        SkeetEditorStyle.row(x, y + 45, right, y + 75, false, false);
        fontRendererObj.drawStringWithShadow("Chat delay", x + 8, y + 55, SkeetEditorStyle.TEXT);
        drawSlider(x + 67, y + 52, right - 8, preferences.getChatDelay(), 0, 5000, preferences.getChatDelay() + " ms");
    }

    private void drawGame(int mouseX, int mouseY) {
        int boardSize = boardSize();
        int boardLeft = left + 20, boardTop = top + 45;
        if (module.getType() == GameType.CHESS) drawChess(boardLeft, boardTop, boardSize);
        else if (module.getType() == GameType.TIC_TAC_TOE) drawTicTacToe(boardLeft, boardTop, boardSize, mouseX, mouseY);
        else drawConnectFour(boardLeft, boardTop, boardSize, mouseX, mouseY);

        int side = boardLeft + boardSize + 20, right = contentRight;
        SkeetEditorStyle.panel(side, top + 43, right, top + panelHeight - 18, "Match");
        int x = side + 12, y = top + 65;
        fontRendererObj.drawStringWithShadow(module.isRobotGame() ? "Local robot" : "Chat match", x, y, SkeetEditorStyle.MUTED);
        fontRendererObj.drawStringWithShadow(trim(module.getOpponent(), right - x - 12), x, y + 18, SkeetEditorStyle.TEXT);
        fontRendererObj.drawStringWithShadow(trim(module.getGame().getStatus(), right - x - 12), x, y + 38,
                module.isMyTurn() ? SkeetEditorStyle.accent(.25F) : SkeetEditorStyle.MUTED);
        fontRendererObj.drawStringWithShadow(trim(module.getNotice(), right - x - 12), x, y + 55, SkeetEditorStyle.MUTED);
        SkeetEditorStyle.panel(x - 3, y + 80, right - 10, y + 132, "Chat moves");
        fontRendererObj.drawStringWithShadow(module.getType().getWireName() + ": " + exampleMove(), x + 5, y + 101, SkeetEditorStyle.accent(.1F));
        fontRendererObj.drawStringWithShadow(trim(module.isRobotGame() ? "No chat is used against Robot." : "Each legal move is sent to your opponent.", right - x - 22), x + 5, y + 117, SkeetEditorStyle.MUTED);
        if (module.getType() == GameType.CHESS && hasPromotionSelection()) drawPromotionPicker(x, y + 151);
        button(x, top + panelHeight - 76, right - 12, "Request Move", module.canRequestMove());
        button(x, top + panelHeight - 47, right - 12, module.getGame().isFinished() ? "Back to lobby" : "Cancel match", false);
    }

    private int boardSize() {
        int cap = module.getType() == GameType.CHESS ? 470 : 360;
        return Math.max(module.getType() == GameType.CHESS ? 192 : 168,
                Math.min(cap, Math.min(panelHeight - 82, panelWidth - 330)));
    }

    /** Chess.com-inspired timber board, rotated whenever the local player is Black. */
    private void drawChess(int x, int y, int size) {
        ChessGameState chess = (ChessGameState) module.getGame();
        int square = Math.max(24, size / 8), actual = square * 8;
        SkeetEditorStyle.panel(x - 5, y - 5, x + actual + 5, y + actual + 5, null);
        ChessGameState.Move last = chess.getLastMove();
        boolean flipped = module.getLocalSide() == 1;
        for (int visualRow = 0; visualRow < 8; visualRow++) for (int visualColumn = 0; visualColumn < 8; visualColumn++) {
            int row = flipped ? 7 - visualRow : visualRow;
            int column = flipped ? 7 - visualColumn : visualColumn;
            int sx = x + visualColumn * square, sy = y + visualRow * square;
            boolean light = (row + column) % 2 == 0;
            int color = light ? CHESS_LIGHT : CHESS_DARK;
            if (last != null && ((last.fromRow == row && last.fromColumn == column) || (last.toRow == row && last.toColumn == column))) color = light ? 0xFFF6EE86 : 0xFFB9CA43;
            if (selectedRow == row && selectedColumn == column) color = light ? 0xFFF7C976 : 0xFFD9963B;
            Gui.drawRect(sx, sy, sx + square, sy + square, color);
            if (selectedRow >= 0 && module.isMyTurn() && isChessDestination(chess, row, column)) {
                int dot = Math.max(4, square / 7);
                Gui.drawRect(sx + square / 2 - dot, sy + square / 2 - dot, sx + square / 2 + dot, sy + square / 2 + dot, 0x885E6A4C);
            }
            char piece = chess.get(row, column);
            if (piece != ' ') drawChessPiece(piece, sx, sy, square);
            if (visualColumn == 0) fontRendererObj.drawStringWithShadow(String.valueOf(8 - row), sx + 3, sy + 3, light ? CHESS_DARK : CHESS_LIGHT);
            if (visualRow == 7) fontRendererObj.drawStringWithShadow(String.valueOf((char) ('a' + column)), sx + square - 7, sy + square - 10, light ? CHESS_DARK : CHESS_LIGHT);
        }
    }

    /** Lichess cburnett pieces, bundled locally so the board also works offline. */
    private void drawChessPiece(char piece, int x, int y, int square) {
        String name = (Character.isUpperCase(piece) ? "w" : "b") + Character.toUpperCase(piece);
        mc.getTextureManager().bindTexture(new net.minecraft.util.ResourceLocation("vibe", "chess/" + name + ".png"));
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1, 1, 1, 1);
        Gui.drawScaledCustomSizeModalRect(x + 2, y + 2, 0, 0, 128, 128, square - 4, square - 4, 128, 128);
    }

    private void drawTicTacToe(int x, int y, int size, int mouseX, int mouseY) {
        TicTacToeState game = (TicTacToeState) module.getGame(); int cell = Math.max(56, size / 3), actual = cell * 3;
        SkeetEditorStyle.panel(x - 5, y - 5, x + actual + 5, y + actual + 5, null);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
            int sx = x + col * cell, sy = y + row * cell;
            SkeetEditorStyle.row(sx, sy, sx + cell, sy + cell, false, hit(sx, sy, sx + cell, sy + cell, mouseX, mouseY) && module.isMyTurn());
            char mark = game.get(row, col);
            if (mark != ' ') drawTicMark(mark, sx, sy, cell);
        }
    }

    /** Render deliberately oversized, unambiguous X and O marks instead of fragile pixel glyphs. */
    private void drawTicMark(char mark, int x, int y, int cell) {
        float centreX = x + cell / 2.0F, centreY = y + cell / 2.0F;
        float inset = Math.max(11.0F, cell * 0.24F), stroke = Math.max(5.0F, cell * 0.12F);
        int color = mark == 'X' ? SkeetEditorStyle.accent(.2F) : 0xFFFFB86C;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT
                | GL11.GL_TEXTURE_BIT | GL11.GL_LINE_BIT);
        try {
            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            if (mark == 'X') {
                ticLine(0x66000000, x + inset + 2, y + inset + 2, x + cell - inset + 2, y + cell - inset + 2, stroke + 3);
                ticLine(0x66000000, x + cell - inset + 2, y + inset + 2, x + inset + 2, y + cell - inset + 2, stroke + 3);
                ticLine(color, x + inset, y + inset, x + cell - inset, y + cell - inset, stroke);
                ticLine(color, x + cell - inset, y + inset, x + inset, y + cell - inset, stroke);
            } else {
                ticRing(0x66000000, centreX + 2, centreY + 2, cell / 2.0F - inset, stroke + 3);
                ticRing(color, centreX, centreY, cell / 2.0F - inset, stroke);
            }
        } finally {
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopAttrib();
        }
    }

    private void ticLine(int color, float x1, float y1, float x2, float y2, float width) {
        setPrimitiveColor(color);
        GL11.glLineWidth(width);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x1, y1); GL11.glVertex2f(x2, y2);
        GL11.glEnd();
    }

    private void ticRing(int color, float centreX, float centreY, float radius, float width) {
        setPrimitiveColor(color);
        // An annulus keeps an even stroke regardless of driver line-width limits.
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for (int index = 0; index <= 96; index++) {
            double angle = Math.PI * 2.0D * index / 96.0D;
            float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
            GL11.glVertex2f(centreX + dx * (radius + width / 2), centreY + dy * (radius + width / 2));
            GL11.glVertex2f(centreX + dx * (radius - width / 2), centreY + dy * (radius - width / 2));
        }
        GL11.glEnd();
    }

    private void setPrimitiveColor(int color) {
        GL11.glColor4f((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F,
                (color & 255) / 255.0F, (color >>> 24) / 255.0F);
    }

    private void drawConnectFour(int x, int y, int size, int mouseX, int mouseY) {
        ConnectFourState game = (ConnectFourState) module.getGame();
        int cell = Math.max(28, Math.min(size / 7, (panelHeight - 100) / 6)), actualWidth = cell * 7, actualHeight = cell * 6;
        SkeetEditorStyle.panel(x - 5, y - 25, x + actualWidth + 5, y + actualHeight + 5, null);
        for (int col = 0; col < 7; col++) {
            boolean hover = hit(x + col * cell, y - 21, x + (col + 1) * cell, y + actualHeight, mouseX, mouseY);
            Gui.drawRect(x + col * cell, y - 21, x + (col + 1) * cell, y, hover && module.isMyTurn() ? SkeetEditorStyle.accent(.3F) : 0xFF1D2446);
            fontRendererObj.drawStringWithShadow(String.valueOf(col + 1), x + col * cell + cell / 2 - 3, y - 15, SkeetEditorStyle.TEXT);
        }
        for (int row = 0; row < 6; row++) for (int col = 0; col < 7; col++) {
            int sx = x + col * cell, sy = y + row * cell;
            Gui.drawRect(sx, sy, sx + cell, sy + cell, 0xFF263A92);
            char disc = game.get(row, col); int color = disc == 'R' ? 0xFFE94B4B : disc == 'Y' ? 0xFFFFD34E : 0xFF11162E;
            int pad = Math.max(3, cell / 7);
            RenderUtils.roundedRect(sx + pad, sy + pad, sx + cell - pad, sy + cell - pad, Math.max(4, cell / 2 - pad), color);
            if (game.getLastColumn() == col && game.getLastRow() == row) RenderUtils.roundedOutline(sx + pad, sy + pad, sx + cell - pad, sy + cell - pad, Math.max(4, cell / 2 - pad), 1, 0xFFFFFFFF);
        }
    }

    private void drawPromotionPicker(int x, int y) {
        fontRendererObj.drawStringWithShadow("Promotion", x, y, SkeetEditorStyle.MUTED);
        int itemX = x;
        for (char item : new char[] {'Q', 'R', 'B', 'N'}) {
            SkeetEditorStyle.row(itemX, y + 12, itemX + 28, y + 37, promotion == item, false);
            fontRendererObj.drawStringWithShadow(String.valueOf(item), itemX + 10, y + 20, SkeetEditorStyle.TEXT);
            itemX += 32;
        }
    }

    private void drawSlider(int x, int y, int right, int value, int min, int max, String label) {
        int trackRight = sliderTrackRight(x, right, label);
        Gui.drawRect(x, y + 5, trackRight, y + 7, 0xFF383A43);
        int knob = x + Math.round((trackRight - x) * (value - min) / (float) (max - min));
        Gui.drawRect(x, y + 5, knob, y + 7, SkeetEditorStyle.accent(.2F));
        RenderUtils.roundedRect(knob - 3, y + 2, knob + 3, y + 10, 3, SkeetEditorStyle.accent(.4F));
        fontRendererObj.drawStringWithShadow(label, trackRight + 6, y + 2, SkeetEditorStyle.TEXT);
    }

    private int sliderTrackRight(int x, int right, String label) { return Math.max(x + 20, right - fontRendererObj.getStringWidth(label) - 8); }

    private void drawCheckbox(int x, int y, String label, boolean checked, boolean hovered) {
        SkeetEditorStyle.row(x, y, x + 13, y + 13, checked, hovered);
        if (checked) fontRendererObj.drawString("✓", x + 3, y + 3, 0xFF151518);
        fontRendererObj.drawStringWithShadow(label, x + 19, y + 3, SkeetEditorStyle.TEXT);
    }

    private void drawScrollBar(int x, int y, int bottom, int offset, int maximum, int viewport) {
        Gui.drawRect(x, y, x + 2, bottom, 0xFF282A31);
        int track = bottom - y, thumb = Math.max(14, viewport * viewport / (viewport + maximum));
        int thumbY = y + Math.round((track - thumb) * offset / (float) maximum);
        Gui.drawRect(x, thumbY, x + 2, thumbY + thumb, SkeetEditorStyle.accent(.3F));
    }

    private void button(int x, int y, int right, String text, boolean active) { SkeetEditorStyle.button(x, y, right, y + 22, text, active); }

    @Override protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton != 0) { super.mouseClicked(mouseX, mouseY, mouseButton); return; }
        layout();
        mouseX = (int) (mouseX / uiScale); mouseY = (int) (mouseY / uiScale);
        if (module.hasActiveGame()) clickGame(mouseX, mouseY); else clickLobby(mouseX, mouseY);
    }

    private void clickLobby(int mouseX, int mouseY) {
        List<NetworkPlayerInfo> players = players();
        int columns = playerColumns(), cardWidth = playerCardWidth();
        if (hit(listRight - 9, listTop, listRight, listBottom - 4, mouseX, mouseY)) { draggingPlayers = true; dragScroll(mouseY); return; }
        if (hit(contentRight - 9, top + 60, contentRight, listBottom - 36, mouseX, mouseY)) { draggingMatch = true; dragScroll(mouseY); return; }
        for (int index = 0; index < players.size(); index++) {
            int x = listLeft + 10 + (index % columns) * (cardWidth + 6), y = listTop + (index / columns) * 40 - playerScroll;
            if (!isSelf(players.get(index).getGameProfile().getName()) && hit(x, y, x + cardWidth, y + 34, mouseX, mouseY) && hit(listLeft, listTop, listRight - 8, listBottom - 4, mouseX, mouseY)) {
                selectedPlayer = players.get(index).getGameProfile().getName(); return;
            }
        }
        int x = sideLeft + 12, right = contentRight - 12;
        if (hit(right - 84, matchBottom() - 24, right, matchBottom() - 2, mouseX, mouseY)) { mc.displayGuiScreen(null); return; }
        if (!hit(sideLeft + 2, top + 60, contentRight - 8, listBottom - 36, mouseX, mouseY)) return;
        int multiplayerTop = multiplayerTop();
        int controlsX = x + 9, controlsRight = right - 9, controlsY = multiplayerTop + 23;
        MemeGamePreferences preferences = module.getPreferences();
        if (hit(controlsX, controlsY - 4, controlsRight, controlsY + 18, mouseX, mouseY)) { preferences.setCheckMessage(!preferences.isCheckMessage()); return; }
        if (hit(controlsX, controlsY + 17, controlsRight, controlsY + 39, mouseX, mouseY)) { preferences.setAddGarbage(!preferences.isAddGarbage()); return; }
        if (hit(controlsX + 67, controlsY + 45, controlsRight, controlsY + 75, mouseX, mouseY)) {
            String label = preferences.getChatDelay() + " ms";
            preferences.setChatDelay(Math.round(sliderValue(controlsX + 67, sliderTrackRight(controlsX + 67, controlsRight, label), mouseX, 0, 5000))); return;
        }
        int playerTop = multiplayerTop + 104;
        if (selectedPlayer != null && hit(controlsX, playerTop + 55, controlsRight, playerTop + 77, mouseX, mouseY)) { module.request(selectedPlayer); return; }
        if (module.getPendingOpponent() != null) {
            if (hit(controlsX, playerTop + 96, controlsRight, playerTop + 118, mouseX, mouseY)) { module.accept(); return; }
        }
        int robotTop = singleplayerTop() + 23;
        String strength = preferences.getRobotStrength() + "/10";
        if (hit(controlsX + 70, robotTop, controlsRight, robotTop + 24, mouseX, mouseY)) {
            preferences.setRobotStrength(Math.round(sliderValue(controlsX + 70, sliderTrackRight(controlsX + 70, controlsRight, strength), mouseX, 1, 10))); return;
        }
        if (hit(controlsX, robotTop + 25, controlsRight, robotTop + 48, mouseX, mouseY)) { robotStarts = !robotStarts; return; }
        if (hit(controlsX, robotTop + 53, controlsRight, robotTop + 75, mouseX, mouseY)) { module.startRobot(robotStarts); return; }
    }

    private void clickGame(int mouseX, int mouseY) {
        int boardSize = boardSize(), x = left + 20, y = top + 45, side = x + boardSize + 20;
        if (hit(side + 12, top + panelHeight - 76, contentRight - 12, top + panelHeight - 54, mouseX, mouseY)) { module.requestMove(); return; }
        if (hit(side + 12, top + panelHeight - 47, contentRight - 12, top + panelHeight - 25, mouseX, mouseY)) { module.cancel(); return; }
        if (module.getType() == GameType.CHESS) {
            int square = Math.max(24, boardSize / 8), visualColumn = (mouseX - x) / square, visualRow = (mouseY - y) / square;
            if (mouseX >= x && mouseY >= y && visualRow >= 0 && visualRow < 8 && visualColumn >= 0 && visualColumn < 8) {
                boolean flipped = module.getLocalSide() == 1;
                clickChess(flipped ? 7 - visualRow : visualRow, flipped ? 7 - visualColumn : visualColumn);
            }
            if (hasPromotionSelection()) {
                int itemX = side + 12, py = top + 65 + 151 + 12;
                for (char item : new char[] {'Q', 'R', 'B', 'N'}) { if (hit(itemX, py, itemX + 28, py + 25, mouseX, mouseY)) { promotion = item; return; } itemX += 32; }
            }
        } else if (module.getType() == GameType.TIC_TAC_TOE) {
            int cell = Math.max(56, boardSize / 3), col = (mouseX - x) / cell, row = (mouseY - y) / cell;
            if (mouseX >= x && mouseY >= y && row >= 0 && row < 3 && col >= 0 && col < 3) module.play("" + (char) ('a' + col) + (char) ('1' + (2 - row)));
        } else {
            int cell = Math.max(28, Math.min(boardSize / 7, (panelHeight - 100) / 6)), col = (mouseX - x) / cell;
            if (mouseX >= x && mouseY >= y - 21 && mouseY < y + cell * 6 && col >= 0 && col < 7) module.play(String.valueOf(col + 1));
        }
    }

    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if (module.hasActiveGame()) return;
        layout();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int mouseX = (int) ((Mouse.getEventX() * width / mc.displayWidth) / uiScale);
        int mouseY = (int) ((height - Mouse.getEventY() * height / mc.displayHeight - 1) / uiScale);
        if (hit(sideLeft, top + 60, contentRight, listBottom - 36, mouseX, mouseY)) {
            matchScroll = Math.max(0, Math.min(matchMaximumScroll(), matchScroll - Integer.signum(wheel) * 28)); return;
        }
        if (!hit(listLeft, listTop, listRight, listBottom, mouseX, mouseY)) return;
        int count = players().size();
        int columns = playerColumns();
        int rows = (count + columns - 1) / columns, viewport = Math.max(1, listBottom - listTop - 4);
        int maximum = Math.max(0, rows * 40 - viewport);
        playerScroll = Math.max(0, Math.min(maximum, playerScroll - Integer.signum(wheel) * 28));
    }

    @Override protected void mouseClickMove(int x, int y, int button, long elapsed) {
        if (button == 0 && (draggingPlayers || draggingMatch)) dragScroll((int) (y / uiScale));
    }
    @Override protected void mouseReleased(int x, int y, int button) { draggingPlayers = draggingMatch = false; }
    private void dragScroll(int y) {
        int start = draggingPlayers ? listTop : top + 60, end = draggingPlayers ? listBottom - 4 : listBottom - 36;
        int viewport = end - start;
        int maximum = draggingPlayers ? Math.max(0, ((players().size() + playerColumns() - 1) / playerColumns()) * 40 - viewport) : matchMaximumScroll();
        int thumb = Math.max(14, viewport * viewport / (viewport + maximum));
        int offset = Math.round(maximum * Math.max(0, Math.min(1, (y - start - thumb / 2F) / Math.max(1, viewport - thumb))));
        if (draggingPlayers) playerScroll = offset; else matchScroll = offset;
    }

    private void clickChess(int row, int col) {
        ChessGameState chess = (ChessGameState) module.getGame();
        if (!module.isMyTurn()) { selectedRow = selectedColumn = -1; return; }
        char piece = chess.get(row, col);
        if (selectedRow < 0) {
            if (piece != ' ' && Character.isUpperCase(piece) == (module.getLocalSide() == 0)) { selectedRow = row; selectedColumn = col; }
            return;
        }
        String notation = chess.notationFor(selectedRow, selectedColumn, row, col, promotion);
        if (notation != null) { module.play(notation); selectedRow = selectedColumn = -1; }
        else if (piece != ' ' && Character.isUpperCase(piece) == (module.getLocalSide() == 0)) { selectedRow = row; selectedColumn = col; }
        else selectedRow = selectedColumn = -1;
    }

    private boolean isChessDestination(ChessGameState chess, int row, int col) { return chess.notationFor(selectedRow, selectedColumn, row, col, promotion) != null; }
    private boolean hasPromotionSelection() { return module.getType() == GameType.CHESS && selectedRow >= 0; }
    private String exampleMove() { return module.getType() == GameType.CHESS ? "e4" : module.getType() == GameType.TIC_TAC_TOE ? "b2" : "4"; }
    private String opponentDetail() { return (module.isRobotGame() ? "Robot • strength " + module.getPreferences().getRobotStrength() : "vs " + module.getOpponent()) + " • " + (module.isMyTurn() ? "your move" : "their move"); }

    private List<NetworkPlayerInfo> players() {
        if (mc.getNetHandler() == null || mc.thePlayer == null) return Collections.emptyList();
        List<NetworkPlayerInfo> result = new ArrayList<NetworkPlayerInfo>();
        for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
            if (info != null && info.getGameProfile() != null && info.getGameProfile().getName() != null) result.add(info);
        }
        // Merge nearby players omitted from the server's tab list.
        if (mc.theWorld != null) for (net.minecraft.entity.player.EntityPlayer player : mc.theWorld.playerEntities) {
            boolean present = false;
            for (NetworkPlayerInfo info : result) if (player.getUniqueID().equals(info.getGameProfile().getId())) { present = true; break; }
            if (!present) result.add(new NetworkPlayerInfo(player.getGameProfile()));
        }
        Collections.sort(result, new Comparator<NetworkPlayerInfo>() { @Override public int compare(NetworkPlayerInfo first, NetworkPlayerInfo second) {
            return first.getGameProfile().getName().compareToIgnoreCase(second.getGameProfile().getName());
        }});
        return result;
    }

    private NetworkPlayerInfo findPlayer(String name) { if (name != null) for (NetworkPlayerInfo info : players()) if (name.equalsIgnoreCase(info.getGameProfile().getName())) return info; return null; }
    private float sliderValue(int x, int right, int mouse, int min, int max) { return min + (max - min) * Math.max(0, Math.min(1, (mouse - x) / (float) (right - x))); }
    private boolean hit(int x, int y, int right, int bottom, int mouseX, int mouseY) { return mouseX >= x && mouseX < right && mouseY >= y && mouseY < bottom; }
    private String trim(String text, int available) { return fontRendererObj.trimStringToWidth(text == null ? "" : text, Math.max(0, available)); }
    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException { if (keyCode == Keyboard.KEY_ESCAPE) { mc.displayGuiScreen(null); return; } super.keyTyped(typedChar, keyCode); }
    @Override public void onGuiClosed() { if (module.isEnabled()) module.setEnabled(false); super.onGuiClosed(); }
    @Override public boolean doesGuiPauseGame() { return false; }
}
