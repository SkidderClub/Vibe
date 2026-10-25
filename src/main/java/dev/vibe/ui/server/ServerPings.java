package dev.vibe.ui.server;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.OldServerPinger;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

/** Bounded background DNS/pings; all texture creation and network polling stay on the client thread. */
final class ServerPings implements AutoCloseable {
    private final OldServerPinger pinger = new OldServerPinger();
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(3, 3, 5, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(256), task -> { Thread t = new Thread(task, "Vibe server ping"); t.setDaemon(true); return t; });
    private final Map<ServerData, ServerData> pending = new IdentityHashMap<>();
    private final Map<ServerData, Icon> icons = new LinkedHashMap<>(64,.75F,true);
    private volatile boolean closed;
    private final boolean enabled;
    ServerPings(boolean enabled) { this.enabled = enabled; }
    ServerData get(ServerData source) {
        if (!enabled || source.isOnLAN()) return source;
        ServerData copy = pending.get(source);
        if (copy == null && !closed) {
            copy = new ServerData(source.serverName, source.serverIP, false);
            copy.setBase64EncodedIconData(source.getBase64EncodedIconData()); copy.pingToServer = -2;
            pending.put(source, copy); final ServerData target = copy;
            try { workers.execute(() -> {
                try { if (!closed) pinger.ping(target); }
                catch (Exception e) { target.pingToServer = -1; target.serverMOTD = "Cannot reach server"; }
                finally { if (closed) pinger.clearPendingNetworks(); }
            }); } catch (RejectedExecutionException e) { pending.remove(source); }
        }
        return copy == null ? source : copy;
    }
    void tick() { if (enabled && !closed) pinger.pingPendingNetworks(); }
    ResourceLocation icon(ServerData data) {
        String encoded = data.getBase64EncodedIconData();
        Icon icon = icons.get(data);
        if (icon != null && Objects.equals(icon.encoded, encoded)) return icon.texture;
        if (icon != null && icon.texture != null) Minecraft.getMinecraft().getTextureManager().deleteTexture(icon.texture);
        ResourceLocation texture = null;
        if (encoded != null && encoded.length() < 65536) {
            try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (readers.hasNext()) {
                    ImageReader reader = readers.next();
                    try {
                        reader.setInput(input);
                        if (reader.getWidth(0) == 64 && reader.getHeight(0) == 64) {
                            BufferedImage image = reader.read(0);
                            DynamicTexture iconTexture=new DynamicTexture(image);iconTexture.setBlurMipmap(true,false);
                            texture = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("vibe_server", iconTexture);
                        }
                    } finally { reader.dispose(); }
                }
            } catch (Exception ignored) { }
        }
        if (icon==null && icons.size() >= 256) {
            Iterator<Icon> oldest=icons.values().iterator();Icon evicted=oldest.next();oldest.remove();
            if(evicted.texture!=null)Minecraft.getMinecraft().getTextureManager().deleteTexture(evicted.texture);
        }
        icons.put(data, new Icon(encoded, texture)); return texture;
    }
    @Override public void close() {
        closed = true; workers.shutdownNow(); pinger.clearPendingNetworks();
        for (Icon icon : icons.values()) if (icon.texture != null) Minecraft.getMinecraft().getTextureManager().deleteTexture(icon.texture);
        icons.clear(); pending.clear();
    }
    private static final class Icon {
        final String encoded; final ResourceLocation texture;
        Icon(String encoded, ResourceLocation texture) { this.encoded = encoded; this.texture = texture; }
    }
}
