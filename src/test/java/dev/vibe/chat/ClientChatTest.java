package dev.vibe.chat;

import dev.vibe.path.PathTestFixture;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClientChatTest {
    @Test public void usesOneLocalPrefixAndNeverSendsServerChat() throws Exception {
        try (PathTestFixture f = new PathTestFixture()) {
            ClientChat.send("Hello");
            new dev.vibe.command.CommandManager().execute(".help");
            keystrokesmod.script.ScriptDefaults.client.print("Script message");
            assertTrue(f.player.sentMessages.isEmpty());
            assertTrue(f.player.localMessages.size() >= 3);
            for (String line : f.player.localMessages) assertTrue(line, line.startsWith("[Vibe] "));
        }
    }

    @Test public void preservesClickableStyledBodiesWithoutMutatingTheirStyle() {
        ChatComponentText body = new ChatComponentText("Repository");
        body.getChatStyle().setUnderlined(true)
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://example.invalid"))
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText("Open source")));
        IChatComponent message = ClientChat.message(body);
        IChatComponent copy = message.getSiblings().get(0);
        assertEquals("[Vibe] Repository", EnumChatFormatting.getTextWithoutFormattingCodes(message.getUnformattedText()));
        assertTrue(message.getFormattedText().contains(ClientChat.PREFIX));
        assertEquals(body.getChatStyle().getChatClickEvent(), copy.getChatStyle().getChatClickEvent());
        assertEquals(body.getChatStyle().getChatHoverEvent(), copy.getChatStyle().getChatHoverEvent());
        assertTrue(copy.getChatStyle().getUnderlined());
        assertEquals(EnumChatFormatting.GRAY, copy.getChatStyle().getColor());
        assertNull(body.getChatStyle().getColor());
    }

    @Test public void animatesExistingTextWithoutChangingWidthFadeShadowOrBody() throws Exception {
        RecordingFont font = PathTestFixture.allocate(RecordingFont.class);
        font.calls = new ArrayList<Draw>();
        String text = ClientChat.message(new ChatComponentText("\u00a7fMurderer detected: \u00a7cPlayer")).getFormattedText();
        int end = ClientChatRenderer.drawAt(font, text, 11, 23, 0x80FFFFFF, true, 0);
        assertEquals(11 + font.getStringWidth(text) + 1, end);
        assertEquals(6, font.calls.size());
        List<Draw> first = new ArrayList<Draw>(font.calls); font.calls.clear();
        ClientChatRenderer.drawAt(font, text, 11, 23, 0x80FFFFFF, true, 1300);
        for (int i = 0; i < 6; i++) {
            Draw a = first.get(i), b = font.calls.get(i);
            assertEquals(a.text, b.text); assertEquals(a.x, b.x, 0); assertEquals(a.y, b.y, 0);
            assertTrue(b.shadow); assertEquals(0x80000000, b.color & 0xFF000000);
            if (i > 0 && i < 5) assertNotEquals(a.color, b.color);
            else assertEquals(a.color, b.color);
        }
        assertTrue(font.calls.get(5).text.contains("Murderer detected:"));
        assertEquals(ClientChatRenderer.UNHANDLED, ClientChatRenderer.draw(font, "\u00a78[Vibe] Server message", 0, 0, -1, false));
        font.calls.clear();
        ClientChatRenderer.drawAt(font, text, 0, 0, 0xFFFFFF, false, 6000);
        assertEquals(0xFF000000, font.calls.get(1).color & 0xFF000000);
        assertEquals(ClientChatRenderer.rainbow(0, 0), ClientChatRenderer.rainbow(6000, 0));
        assertNotEquals(ClientChatRenderer.rainbow(0, 0), ClientChatRenderer.rainbow(50, 0));
    }

    private static final class Draw {
        final String text; final float x, y; final int color; final boolean shadow;
        Draw(String text, float x, float y, int color, boolean shadow) {
            this.text=text; this.x=x; this.y=y; this.color=color; this.shadow=shadow;
        }
    }

    private static class RecordingFont extends FontRenderer {
        List<Draw> calls;
        private RecordingFont() { super(null, null, null, false); }
        @Override public int getStringWidth(String text) {
            return EnumChatFormatting.getTextWithoutFormattingCodes(text).length() * 6;
        }
        @Override public int drawString(String text, float x, float y, int color, boolean shadow) {
            // Simulate the transformed final overload, including re-entry.
            int intercepted = ClientChatRenderer.draw(this, text, x, y, color, shadow);
            if (intercepted != ClientChatRenderer.UNHANDLED) return intercepted;
            calls.add(new Draw(text, x, y, color, shadow));
            return (int)x + getStringWidth(text) + (shadow ? 1 : 0);
        }
    }
}
