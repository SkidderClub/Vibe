package dev.vibe.ui.server;

import dev.vibe.Vibe;
import dev.vibe.server.*;
import dev.vibe.ui.*;
import dev.vibe.ui.account.AccountManagerGui;
import dev.vibe.ui.menu.MainMenuShaderManager;
import java.awt.Font;
import java.io.IOException;
import java.text.DateFormat;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.network.LanServerDetector;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import static dev.vibe.ui.menu.AccountScreenStyle.*;

/** Native servers.dat and connection flow, with a responsive Vibe presentation. */
public final class ServerBrowserGui extends GuiScreen {
    // The shared atlas has 32px cells. Scale a fitting glyph instead of clipping a larger font.
    private static final NeverLoseFont TITLE = new NeverLoseFont(Font.BOLD, 24);
    private static final NeverLoseFont NAME = new NeverLoseFont(Font.BOLD, 22);
    private static final NeverLoseFont TEXT_FONT = new NeverLoseFont(Font.PLAIN, 20);
    private static final NeverLoseFont SMALL = new NeverLoseFont(Font.PLAIN, 18);
    private final GuiScreen parent;
    private final MainMenuShaderManager shaders;
    private final ServerBrowserEvents joins;
    private final boolean network;
    private ServerList saved;
    private final List<ServerData> servers = new ArrayList<>(), lan = new ArrayList<>();
    private List<ServerData> rows = Collections.emptyList(), preview;
    private final Map<ServerData, Float> positions = new IdentityHashMap<>(), hovers = new IdentityHashMap<>();
    private final Map<ServerData,RowVisual> rowVisuals=new IdentityHashMap<>();
    private int lastRowsDrawn;
    private net.minecraft.util.Session accountSession;
    private String accountName="Player";
    private UUID accountId;
    private ServerData tooltipServer;
    private ServerJoinHistory.Result tooltipResult;
    private List<String> tooltipLines;
    private ServerData selected, candidate, dragged, draft, editOriginal;
    private ServerPings pings;
    private LanServerDetector.LanServerList lanList;
    private LanServerDetector.ThreadLanServerFind lanThread;
    private GuiTextField search;
    private String query = "", notice = "";
    private ServerBrowserLayout l;
    private float scroll, targetScroll;
    private int pressX, pressY, dragOffset, form;
    private long opened, previousFrame, lastClick;
    private ServerData lastClicked;
    private boolean deleting;
    private ScrollbarDrag scrollbar;

    public ServerBrowserGui(GuiScreen parent, MainMenuShaderManager shaders, ServerBrowserEvents joins) {
        this(parent, shaders, joins, true);
    }
    /** No network activity in the offscreen rendering/interaction harness. */
    public ServerBrowserGui(GuiScreen parent, MainMenuShaderManager shaders, ServerBrowserEvents joins, boolean network) {
        this.parent = parent; this.shaders = shaders; this.joins = joins; this.network = network;
    }
    @Override public void initGui() {
        l = new ServerBrowserLayout(width, height); buttonList.clear(); positions.clear(); hovers.clear();rowVisuals.clear();
        if (saved == null) saved = new ServerList(mc);
        servers.clear(); for (int i = 0; i < saved.countServers(); i++) servers.add(saved.getServerData(i));
        if (pings != null) pings.close(); pings = new ServerPings(network);
        stopLan(); lan.clear();
        if (network) try { lanList = new LanServerDetector.LanServerList(); lanThread = new LanServerDetector.ThreadLanServerFind(lanList); lanThread.setDaemon(true); lanThread.start(); }
        catch (IOException ignored) { notice = "LAN discovery is unavailable."; }
        search = new GuiTextField(20, fontRendererObj, l.listX + 28, l.listY - 24, l.listW - 52, 14);
        search.setEnableBackgroundDrawing(false); search.setTextColor(TEXT); search.setMaxStringLength(128); search.setText(query);
        for(ServerBrowserLayout.FooterButton b:l.footerButtons())button(b.id,b.x,b.y,b.w,20,b.label,b.id==4);
        buttonList.add(new AccountButton(l.x+l.w-(l.compact?128:200)-16,l.y+8,l.compact?128:200));
        int cx = width / 2, cy = height / 2;
        button(8, cx - 134, cy + 26, 130, 28, "Cancel", false);
        button(9, cx + 4, cy + 26, 130, 28, "Delete server", true);
        opened = previousFrame = System.nanoTime(); candidate = dragged = null; preview = null; scrollbar = null;
        updateRows(); clampScroll(); updateButtons();
    }
    private void button(int id, int x, int y, int w, int h, String text, boolean primary) {
        buttonList.add(new AnimatedButton(id, x, y, w, h, text, primary));
    }
    @Override public void updateScreen() {
        search.updateCursorCounter(); pings.tick();
        if (lanList != null && lanList.getWasUpdated()) {
            for (LanServerDetector.LanServer item : lanList.getLanServers()) {
                boolean found = false; for (ServerData old : lan) if (old.serverIP.equals(item.getServerIpPort())) found = true;
                if (!found) { ServerData data = new ServerData(item.getServerMotd(), item.getServerIpPort(), true); data.serverMOTD = "Local network"; lan.add(data); }
            }
            lanList.setWasNotUpdated(); updateRows();
        }
        updateButtons();
    }
    private void updateButtons() {
        net.minecraft.util.Session session=mc.getSession();
        if(accountSession!=session){
            accountSession=session;accountName=session==null?"Player":session.getUsername();
            accountId=session==null?null:session.getProfile().getId();
            for(RowVisual visual:rowVisuals.values())visual.nextResult=0;
            tooltipServer=null;
        }
        for (GuiButton b : buttonList) {
            b.visible = deleting ? b.id >= 8 : b.id < 8;
            b.enabled = b.id == 4 ? selected != null : (b.id == 5 || b.id == 6) ? selected != null && !selected.isOnLAN() : true;
            if (b.id == 7) {
                b.displayString = accountName;
                b.enabled = Vibe.getInstance() != null && Vibe.getInstance().getAccountManager() != null;
            }
        }
    }
    private void updateRows() {
        List<ServerData> visible = new ArrayList<>(); String filter = query.toLowerCase(Locale.ROOT).trim();
        for (ServerData data : preview == null ? servers : preview)
            if (matches(data, filter)) visible.add(data);
        for (ServerData data : lan) if (matches(data, filter)) visible.add(data);
        rows = visible;
        if (selected != null && !rows.contains(selected)) selected = rows.isEmpty() ? null : rows.get(0);
    }
    private boolean matches(ServerData data, String filter) {
        return data.serverName.toLowerCase(Locale.ROOT).contains(filter)
                || (!mc.gameSettings.hideServerAddress && data.serverIP.toLowerCase(Locale.ROOT).contains(filter));
    }
    private void clampScroll() {
        targetScroll = Math.max(0, Math.min(l.maxScroll(rows.size()), targetScroll));
        scroll = Math.max(0, Math.min(l.maxScroll(rows.size()), scroll));
    }
    @Override public void drawScreen(int mx, int my, float partialTicks) {
        long now = System.nanoTime(); float dt = Math.min(.05F, (now - previousFrame) / 1e9F); previousFrame = now;
        float ease = 1 - (float) Math.exp(-18 * dt);
        if (dragged != null) {
            if (my < l.listY + 24) targetScroll -= 260 * dt;
            if (my > l.listY + l.listH - 24) targetScroll += 260 * dt;
        }
        clampScroll(); scroll += (targetScroll - scroll) * ease;
        if (dragged != null) updateDrag(my);
        if (shaders != null) try { shaders.draw(width, height,160000,100); } catch (RuntimeException e) { fallback(); }
        else fallback();
        GuiRenderState.prepare(false);
        MenuRoundedRenderer.rect(l.x - 4, l.y + 5, l.w + 8, l.h + 3, 20, 0x38000000);
        MenuRoundedRenderer.rect(l.x, l.y, l.w, l.h, 16, BORDER);
        MenuRoundedRenderer.rect(l.x + 1, l.y + 1, l.w - 2, l.h - 2, 15, BACKGROUND);
        GlStateManager.pushMatrix(); GlStateManager.translate(l.x + 18, l.y + 12, 0); GlStateManager.scale(1.2F, 1.2F, 1);
        TITLE.draw("Multiplayer", 0, 0, TEXT); GlStateManager.popMatrix();
        int searchY = l.listY - 30;
        surface(l.listX, searchY, l.listW, 24, SURFACE, search.isFocused() ? ACCENT : BORDER, 7);
        drawSearch(l.listX + 11, searchY + 12);
        search.drawTextBox();
        if (query.isEmpty() && !search.isFocused()) TEXT_FONT.draw("Search servers", l.listX + 28, searchY + 4, MUTED);
        float entrance = Math.min(1, (now - opened) / 180e6F);
        clip(true);
        ServerData hovered = null;
        lastRowsDrawn=0;
        for (int i = l.firstVisible(scroll),end=l.lastVisible(rows.size(),scroll); i < end; i++) {
            ServerData data = rows.get(i);
            float target = l.listY + i * l.step - scroll;
            Float previous=positions.get(data);
            float y = previous!=null&&Math.abs(previous-target)<l.listH ? previous+(target-previous)*ease : target+(1-entrance)*9;
            positions.put(data, y);
            if (y + l.row < l.listY || y > l.listY + l.listH) continue;
            if (data == dragged) { surface(l.listX + 3, Math.round(y), l.listW - 12, l.row, TINT, BORDER, 10); continue; }
            boolean over = !deleting && dragged == null && l.inList(mx, my) && mx < l.listX + l.listW - 9 && my >= y && my < y + l.row;
            float hover = hovers.getOrDefault(data, 0F); hover += ((over ? 1 : 0) - hover) * ease; hovers.put(data, hover);
            row(data, Math.round(y), hover, false);lastRowsDrawn++;if (over) hovered = data;
        }
        if (dragged != null) row(dragged, Math.max(l.listY - l.row / 2, Math.min(l.listY + l.listH - l.row / 2, my - dragOffset)), 1, true);
        clip(false);
        if (rows.isEmpty()) {
            NAME.draw(servers.isEmpty() && lan.isEmpty() ? "Your next world starts here" : "No matching servers", l.listX + 20, l.listY + 26, TEXT);
            SMALL.draw(servers.isEmpty() ? "Add a server or join by address below." : "Try a different name or address.", l.listX + 20, l.listY + 49, MUTED);
        }
        int max = l.maxScroll(rows.size());
        if (max > 0) {
            int thumb = ScrollbarDrag.thumb(l.listH, max, 24), y = l.listY + Math.round((l.listH - thumb) * scroll / max);
            MenuRoundedRenderer.rect(l.listX + l.listW - 4, l.listY, 3, l.listH, 2, SURFACE);
            MenuRoundedRenderer.rect(l.listX + l.listW - 4, y, 3, thumb, 2, MUTED);
        }
        super.drawScreen(mx, my, partialTicks);
        if (!deleting && hovered != null && mx > l.listX + l.listW - 95 && joins != null) {
            ServerJoinHistory.Result result=rowVisuals.get(hovered).result;
            if(tooltipServer!=hovered||tooltipResult!=result){
                tooltipServer=hovered;tooltipResult=result;tooltipLines=new ArrayList<>();tooltipLines.add(accountName+" - "+statusLabel(result.status));
                if(result.time>0)tooltipLines.add(DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(result.time)));
                tooltipLines.addAll(fontRendererObj.listFormattedStringToWidth(result.reason,210));
            }
            drawHoveringText(tooltipLines,mx,my);
        }
        if (deleting) {
            // Buttons are drawn again above the modal scrim so their input and visual bounds agree.
            Gui.drawRect(0, 0, width, height, 0xA0000000);
            surface(width / 2 - 150, height / 2 - 55, 300, 122, BACKGROUND, BORDER, 14);
            NAME.draw("Delete this server?", width / 2 - 134, height / 2 - 38, TEXT);
            TEXT_FONT.draw(TEXT_FONT.fit(selected == null ? "" : selected.serverName, 268), width / 2 - 134, height / 2 - 13, MUTED);
            for (GuiButton button : buttonList) if (button.visible) button.drawButton(mc, mx, my);
        }
    }
    private void fallback() { Gui.drawRect(0, 0, width, height, 0xFF111218); }
    private void row(ServerData data, int y, float hover, boolean ghost) {
        int x = l.listX, w = l.listW - 9;
        if (ghost) MenuRoundedRenderer.rect(x - 2, y + 3, w + 4, l.row + 3, 12, 0x70000000);
        surface(x, y, w, l.row, RenderUtils.blend(data == selected ? TINT : SURFACE, HOVER, hover * .7F),
                data == selected || ghost ? RenderUtils.blend(BORDER, ACCENT, .65F) : BORDER, 8);
        ServerData ping = pings.get(data);RowVisual visual=rowVisuals.get(data);
        if(visual==null){visual=new RowVisual(data);rowVisuals.put(data,visual);}visual.update(ping);
        int iconSize = l.icon, iy = y + (l.row - iconSize) / 2;
        ResourceLocation icon = pings.icon(ping);
        if (icon != null) {
            GlStateManager.color(1, 1, 1, 1); mc.getTextureManager().bindTexture(icon);
            Gui.drawScaledCustomSizeModalRect(x + 12, iy, 0, 0, 64, 64, iconSize, iconSize, 64, 64);
        } else {
            MenuRoundedRenderer.rect(x + 12, iy, iconSize, iconSize, 6, RenderUtils.blend(TINT, ACCENT, .16F));
            String initial = data.serverName.isEmpty() ? "S" : data.serverName.substring(0, 1).toUpperCase(Locale.ROOT);
            NAME.draw(initial, x + 12 + (iconSize - NAME.width(initial)) / 2, iy + (iconSize - 13) / 2, ACCENT);
        }
        int textX = x + iconSize + 23;
        NAME.draw(visual.name,textX,y+4,TEXT);
        fontRendererObj.drawString(visual.motd,textX,y+21,MUTED);
        if(l.row>=50)SMALL.draw(visual.address,textX,y+36,MUTED);
        if(l.listW>=420)SMALL.draw(visual.population,x+w-161,y+6,MUTED);
        SMALL.draw(visual.latency,x+w-84,y+6,MUTED);
        ServerStatusBadge.draw(x+w-85,y+l.row-25,visual.badge);
        if (!data.isOnLAN()) for (int dy = 0; dy < 3; dy++) for (int dx = 0; dx < 2; dx++)
            MenuRoundedRenderer.rect(x + 4 + dx * 3, y + l.row / 2 - 5 + dy * 4, 2, 2, 1, RenderUtils.blend(BORDER, MUTED, hover));
    }
    private static String statusLabel(ServerJoinHistory.Status status) {
        switch (status) { case JOINED: return "Joined successfully"; case FAILED: return "Last join failed";
            case CONNECTING: return "Connecting"; default: return "No previous join"; }
    }
    private static void drawSearch(int x, int y) {
        line(new float[] {x + 3, y + 3, x + 6, y + 6}, MUTED);
        float[] circle = new float[34]; for (int i = 0; i < 17; i++) { double a = i * Math.PI / 8; circle[i * 2] = x + (float) Math.cos(a) * 4; circle[i * 2 + 1] = y + (float) Math.sin(a) * 4; } line(circle, MUTED);
    }
    private static void line(float[] points, int color) {
        GlStateManager.disableTexture2D(); GlStateManager.enableBlend(); GlStateManager.disableAlpha();
        GL11.glEnable(GL11.GL_LINE_SMOOTH); GL11.glLineWidth(1.6F);
        GlStateManager.color((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, (color >>> 24) / 255F);
        GL11.glBegin(GL11.GL_LINE_STRIP); for (int i = 0; i < points.length; i += 2) GL11.glVertex2f(points[i], points[i + 1]); GL11.glEnd();
        GL11.glLineWidth(1); GL11.glDisable(GL11.GL_LINE_SMOOTH); GlStateManager.enableAlpha(); GlStateManager.enableTexture2D(); GlStateManager.color(1, 1, 1, 1);
    }
    private static void surface(int x, int y, int w, int h, int fill, int border, int radius) {
        MenuRoundedRenderer.rect(x, y, w, h, radius, border); MenuRoundedRenderer.rect(x + 1, y + 1, w - 2, h - 2, radius - 1, fill);
    }
    private void clip(boolean enable) {
        if (!enable) { GL11.glDisable(GL11.GL_SCISSOR_TEST); return; }
        double sx = mc.displayWidth / (double) width, sy = mc.displayHeight / (double) height;
        GL11.glEnable(GL11.GL_SCISSOR_TEST); GL11.glScissor((int) (l.listX * sx), (int) ((height - l.listY - l.listH) * sy), (int) Math.ceil(l.listW * sx), (int) Math.ceil(l.listH * sy));
    }
    @Override protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 0: mc.displayGuiScreen(parent); break;
            case 1: form = 1; draft = new ServerData("", "", false); mc.displayGuiScreen(new GuiScreenAddServer(this, draft)); break;
            case 2: form = 2; draft = new ServerData("Direct connection", "", false); mc.displayGuiScreen(new GuiScreenServerList(this, draft)); break;
            case 3: pings.close(); pings = new ServerPings(network); notice = ""; break;
            case 4: connect(selected); break;
            case 5: if (selected != null && !selected.isOnLAN()) { form = 3; editOriginal = selected; draft = new ServerData(selected.serverName, selected.serverIP, false); draft.copyFrom(selected); mc.displayGuiScreen(new GuiScreenAddServer(this, draft)); } break;
            case 6: deleting = selected != null && !selected.isOnLAN(); updateButtons(); break;
            case 7: if (Vibe.getInstance() != null && Vibe.getInstance().getAccountManager() != null) mc.displayGuiScreen(new AccountManagerGui(this, shaders, Vibe.getInstance().getAccountManager())); break;
            case 8: deleting = false; updateButtons(); break;
            case 9: if (selected != null) { int index = servers.indexOf(selected); if (index >= 0) { saved.removeServerData(index); saved.saveServerList(); servers.remove(index); } selected = null; deleting = false; updateRows(); clampScroll(); updateButtons(); } break;
            default: break;
        }
    }
    private void connect(ServerData data) {
        if (data == null) return;
        if (joins != null) joins.prepare(data);
        mc.displayGuiScreen(new GuiConnecting(this, mc, data));
    }
    @Override public void confirmClicked(boolean accepted, int id) {
        int action = form; form = 0;
        if (accepted && draft != null) {
            if (action == 1) { saved.addServerData(draft); saved.saveServerList(); selected = draft; }
            else if (action == 3 && editOriginal != null) {
                editOriginal.serverName = draft.serverName; editOriginal.serverIP = draft.serverIP; editOriginal.copyFrom(draft); saved.saveServerList(); selected = editOriginal;
            }
        }
        mc.displayGuiScreen(this);
        if (accepted && action == 2) connect(draft);
        draft = editOriginal = null;
    }
    @Override protected void mouseClicked(int mx, int my, int button) throws IOException {
        if (deleting) { super.mouseClicked(mx, my, button); return; }
        search.mouseClicked(mx, my, button);
        if (button == 0 && l.inList(mx, my)) {
            if (mx >= l.listX + l.listW - 8 && l.maxScroll(rows.size()) > 0) {
                scrollbar = new ScrollbarDrag(l.listY, l.listY + l.listH, Math.round(scroll), l.maxScroll(rows.size()), 24, my); targetScroll = scroll = scrollbar.at(my); return;
            }
            for (int i=l.firstVisible(scroll),end=l.lastVisible(rows.size(),scroll);i<end;i++) {
                ServerData data=rows.get(i);float y = positions.getOrDefault(data,l.listY+i*l.step-scroll);
                if (my < y || my >= y + l.row) continue;
                selected = data; updateButtons(); long now = System.currentTimeMillis();
                if (data == lastClicked && now - lastClick < 250) { candidate = null; connect(data); return; }
                lastClick = now; lastClicked = data;
                if (!data.isOnLAN()) { candidate = data; pressX = mx; pressY = my; dragOffset = (int) (my - y); }
                return;
            }
        }
        super.mouseClicked(mx, my, button);
    }
    @Override protected void mouseClickMove(int mx, int my, int button, long elapsed) {
        if (deleting || button != 0) return;
        if (scrollbar != null) { targetScroll = scroll = scrollbar.at(my); return; }
        if (candidate != null && dragged == null && Math.abs(mx - pressX) + Math.abs(my - pressY) >= 5) { dragged = candidate; lastClicked = null; }
        if (dragged == null) return;
        updateDrag(my);
    }
    private void updateDrag(int my) {
        List<ServerData> visible = new ArrayList<>(); for (ServerData data : servers) if (data != dragged && matches(data, query.toLowerCase(Locale.ROOT).trim())) visible.add(data);
        int index = Math.max(0, Math.min(visible.size(), Math.round((my - dragOffset + l.row / 2F - l.listY + scroll) / l.step)));
        ServerData before = index < visible.size() ? visible.get(index) : null;
        if (before == null && !visible.isEmpty()) {
            List<ServerData> without = new ArrayList<>(servers); without.remove(dragged);
            int after = without.indexOf(visible.get(visible.size() - 1)) + 1; before = after < without.size() ? without.get(after) : null;
        }
        preview = ServerOrder.before(servers, dragged, before); updateRows();
    }
    @Override protected void mouseReleased(int mx, int my, int button) {
        if (button == 0 && dragged != null && preview != null && l.inList(mx, my) && !preview.equals(servers)) {
            servers.clear(); servers.addAll(preview);
            for (int i = 0; i < servers.size(); i++) saved.func_147413_a(i, servers.get(i)); saved.saveServerList();
        }
        candidate = dragged = null; preview = null; scrollbar = null; updateRows(); super.mouseReleased(mx, my, button);
    }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput(); int wheel = Mouse.getEventDWheel();
        int mx = Mouse.getEventX() * width / mc.displayWidth, my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (!deleting && wheel != 0 && l.inList(mx, my)) { targetScroll -= Integer.signum(wheel) * l.step * .8F; clampScroll(); }
    }
    @Override protected void keyTyped(char typed, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) {
            if (deleting) { deleting = false; updateButtons(); }
            else if (dragged != null) { dragged = candidate = null; preview = null; updateRows(); }
            else if (search.isFocused()) search.setFocused(false); else mc.displayGuiScreen(parent);
            return;
        }
        if (deleting) return;
        if (key == Keyboard.KEY_F && isCtrlKeyDown()) { search.setFocused(true); return; }
        if (search.isFocused() && search.textboxKeyTyped(typed, key)) { query = search.getText(); targetScroll = scroll = 0; updateRows(); return; }
        if (key == Keyboard.KEY_F5) { for (GuiButton button : buttonList) if (button.id == 3) { actionPerformed(button); break; } return; }
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) { connect(selected); return; }
        if (key == Keyboard.KEY_DELETE && selected != null && !selected.isOnLAN()) { deleting = true; updateButtons(); return; }
        if ((key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN) && !rows.isEmpty()) {
            int index = rows.indexOf(selected); index = Math.max(0, Math.min(rows.size() - 1, index + (key == Keyboard.KEY_DOWN ? 1 : -1))); selected = rows.get(index);
            targetScroll = Math.max(index * l.step + l.row - l.listH, Math.min(targetScroll, index * l.step)); clampScroll(); updateButtons();
        }
    }
    @Override public void onGuiClosed() { if (pings != null) pings.close(); stopLan(); candidate = dragged = null; preview = null; scrollbar = null; }
    private void stopLan() { if (lanThread != null) { lanThread.interrupt(); lanThread = null; } lanList = null; }

    private final class RowVisual {
        final ServerData source;String rawName,rawMotd,rawPopulation,rawAddress;
        String name="",motd="",population="",address="",latency="";long ping=Long.MIN_VALUE,nextResult;
        boolean hidden;
        ServerJoinHistory.Result result=new ServerJoinHistory.Result(ServerJoinHistory.Status.UNKNOWN,0,"No previous join");
        ServerStatusBadge.Kind badge=ServerStatusBadge.Kind.UNKNOWN;
        RowVisual(ServerData source){this.source=source;}
        void update(ServerData data){
            int available=l.listW-l.icon-(l.listW>=420?200:126);
            if(!Objects.equals(rawName,source.serverName)){rawName=source.serverName;name=NAME.fit(rawName,available);}
            if(!Objects.equals(rawMotd,data.serverMOTD)||motd.isEmpty()){
                rawMotd=data.serverMOTD;String text=rawMotd==null||rawMotd.isEmpty()?"Checking server...":rawMotd;
                int newline=text.indexOf('\n');if(newline>=0)text=text.substring(0,newline);
                motd=fontRendererObj.trimStringToWidth(text,Math.max(1,available));
            }
            if(!Objects.equals(rawPopulation,data.populationInfo)){rawPopulation=data.populationInfo;population=SMALL.fit(rawPopulation==null?"":rawPopulation.replaceAll("\u00a7.",""),70);}
            if(ping!=data.pingToServer){ping=data.pingToServer;latency=source.isOnLAN()?"LAN":ping==-2?"Pinging":ping<0?"Offline":ping+" ms";}
            if(!Objects.equals(rawAddress,source.serverIP)||hidden!=mc.gameSettings.hideServerAddress){rawAddress=source.serverIP;hidden=mc.gameSettings.hideServerAddress;address=SMALL.fit(source.isOnLAN()?"Local network":hidden?"Saved server":rawAddress,available);}
            long now=System.currentTimeMillis();if(joins!=null&&now>=nextResult){
                nextResult=now+200;ServerJoinHistory.Result value=joins.result(source);if(value!=result){result=value;badge=ServerStatusBadge.kind(value);}
            }
        }
    }

    private final class AccountButton extends GuiButton {
        private float hover;private long last=System.nanoTime();
        AccountButton(int x,int y,int w){super(7,x,y,w,28,"");}
        @Override public void drawButton(Minecraft mc,int mx,int my){
            if(!visible)return;hovered=mx>=xPosition&&mx<xPosition+width&&my>=yPosition&&my<yPosition+height;
            long now=System.nanoTime();float dt=Math.min(.05F,(now-last)/1e9F);last=now;hover+=((hovered?1:0)-hover)*(1-(float)Math.exp(-18*dt));
            surface(xPosition,yPosition,width,height,RenderUtils.blend(SURFACE,HOVER,hover),RenderUtils.blend(BORDER,ACCENT,hover*.65F),9);
            MenuRoundedRenderer.rect(xPosition+6,yPosition+3,22,22,5,TINT);
            dev.vibe.ui.account.SkinHeads.draw(accountId,accountName,xPosition+7,yPosition+4,20,network);
            NAME.draw(NAME.fit(accountName,width-50),xPosition+36,yPosition,TEXT);
            SMALL.draw(width<160?"Accounts":"Switch account",xPosition+36,yPosition+14,MUTED);
            line(new float[]{xPosition+width-14,yPosition+11,xPosition+width-11,yPosition+14,xPosition+width-14,yPosition+17},MUTED);
        }
    }

    private static final class AnimatedButton extends GuiButton {
        private final boolean primary;
        private float hover;
        private long last = System.nanoTime();
        AnimatedButton(int id, int x, int y, int w, int h, String text, boolean primary) { super(id, x, y, w, h, text); this.primary = primary; }
        @Override public void drawButton(Minecraft mc, int mx, int my) {
            if (!visible) return; hovered = mx >= xPosition && my >= yPosition && mx < xPosition + width && my < yPosition + height;
            long now = System.nanoTime(); float dt = Math.min(.05F, (now - last) / 1e9F); last = now;
            hover += ((hovered && enabled ? 1 : 0) - hover) * (1 - (float) Math.exp(-19 * dt));
            int fill = !enabled ? BACKGROUND : RenderUtils.blend(primary ? ACCENT : SURFACE, primary ? TEXT : HOVER, hover * (primary ? .12F : 1));
            surface(xPosition, yPosition, width, height, fill, primary && enabled ? ACCENT : RenderUtils.blend(BORDER, ACCENT, hover * .7F), 8);
            String text = TEXT_FONT.fit(displayString, width - 16);
            TEXT_FONT.draw(text, xPosition + (width - TEXT_FONT.width(text)) / 2, yPosition + (height - 12) / 2, !enabled ? MUTED : primary ? BACKGROUND : TEXT);
        }
    }
}
