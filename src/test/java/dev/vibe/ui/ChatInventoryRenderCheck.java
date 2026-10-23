package dev.vibe.ui;

import dev.vibe.chat.ClientChat;
import dev.vibe.chat.ClientChatRenderer;
import dev.vibe.ui.render.InventoryHighlightRenderer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiUtilRenderComponents;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Real font/GL pixels: animation, chat wrapping/fade and slot anchoring. */
public final class ChatInventoryRenderCheck {
    private ChatInventoryRenderCheck() { }

    public static void verify(Minecraft mc, Path output) throws Exception {
        mc.displayWidth=960; mc.displayHeight=540;
        String text = ClientChat.message(new ChatComponentText("\u00a7fMurderer detected: \u00a7cPlayer \u00a77(iron sword)")).getFormattedText();
        int width=mc.fontRendererObj.getStringWidth(text);
        if(width!=mc.fontRendererObj.getStringWidth("[Vibe] Murderer detected: Player (iron sword)")) fail("Prefix changed chat width");
        IChatComponent message=ClientChat.message(new ChatComponentText("A long clickable message that wraps across several vanilla chat lines."));
        for(int wrap : new int[]{40,80,160}) {
            java.util.List<IChatComponent> lines=GuiUtilRenderComponents.splitText(message,wrap,mc.fontRendererObj,false,false);
            if(!lines.get(0).getFormattedText().contains(ClientChat.PREFIX)) fail("Chat wrapping removed prefix marker");
            for(IChatComponent line:lines) if(mc.fontRendererObj.getStringWidth(line.getFormattedText())>wrap) fail("Chat line overflow");
        }
        for(boolean shadow:new boolean[]{false,true}) {
            frame(960,540);
            ClientChatRenderer.drawAt(mc.fontRendererObj,text,20,20,0xFFFFFFFF,shadow,0);
            BufferedImage first=read(960,540);
            frame(960,540);
            ClientChatRenderer.drawAt(mc.fontRendererObj,text,20,20,0xFFFFFFFF,shadow,1500);
            BufferedImage next=read(960,540);
            int prefixEnd=20+mc.fontRendererObj.getStringWidth(ClientChat.PREFIX);
            if(different(first,next,20,20,prefixEnd,32)==0) fail("Existing prefix did not animate");
            if(different(first,next,prefixEnd,15,500,36)!=0) fail("Rainbow animation changed body pixels/position");
            frame(960,540);
            ClientChatRenderer.drawAt(mc.fontRendererObj,text,20,20,0x60FFFFFF,shadow,1500);
            if(different(next,read(960,540),20,20,500,36)==0) fail("Chat fade alpha was lost");
        }
        verifyTransformedFont(mc,text);
        InventoryHighlightTest.PotionInventory gui=new InventoryHighlightTest.PotionInventory();
        // Test all common GUI scales, arbitrary/custom container origins and
        // the same 60-pixel horizontal shift used by the potion sidebar.
        for(int scale:new int[]{1,2,3}) for(int shift:new int[]{0,60}) {
            int w=960/scale,h=540/scale; gui.position(33+shift,41);
            frame(w,h); InventoryHighlightRenderer.drawAt(gui,26,44,0xFF2DE2C2,0);
            BufferedImage low=read(960,540);
            frame(w,h); InventoryHighlightRenderer.drawAt(gui,26,44,0xFF2DE2C2,800);
            BufferedImage high=read(960,540);
            if(different(low,high,0,0,960,540)<20) fail("Slot pulse did not animate");
            int cx=(33+shift+26)*scale,cy=(41+44)*scale;
            int[] bounds=bounds(high,0xFF10151F);
            if(Math.abs((bounds[0]+bounds[2]+1)/2F-cx)>.6F
                    ||Math.abs((bounds[1]+bounds[3]+1)/2F-cy)>.6F) fail("Slot outline moved off center at scale "+scale);
            if(bounds[0]<cx-11*scale||bounds[2]>=cx+11*scale) fail("Slot highlight escaped its fixed bounds");
            frame(w,h); InventoryHighlightRenderer.drawAt(gui,26,44,0x002DE2C2,800);
            if(bounds(read(960,540),0xFF10151F)[2]>=0) fail("Configured zero alpha was ignored");
        }
        frame(960,540);
        GL11.glEnable(GL11.GL_SCISSOR_TEST); GL11.glScissor(1,2,3,4);
        GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(.2F,.3F,.4F,.5F);
        int binding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        InventoryHighlightRenderer.drawAt(gui,26,44,0xFF2DE2C2,800);
        FloatBuffer color=BufferUtils.createFloatBuffer(16); GL11.glGetFloat(GL11.GL_CURRENT_COLOR,color);
        if(!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)||!GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
                ||GL11.glIsEnabled(GL11.GL_BLEND)||binding!=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
                ||Math.abs(color.get(0)-.2F)>.001F||Math.abs(color.get(3)-.5F)>.001F) fail("Highlight leaked GL state");

        for(long time:new long[]{0,800,1500}) {
            frame(480,270);
            mc.fontRendererObj.drawString("Inventory highlights and animated local chat",16,14,0xFFFFFFFF,false);
            mc.fontRendererObj.drawString("Inventory",45,42,0xFFAEBBCD,false);
            mc.fontRendererObj.drawString("Chest",255,42,0xFFAEBBCD,false);
            GlStateManager.color(1,1,1,1);
            mc.getTextureManager().bindTexture(new ResourceLocation("textures/gui/container/inventory.png"));
            Gui.drawModalRectWithCustomSizedTexture(40,58,0,0,176,166,256,256);
            gui.position(40,58);
            InventoryHighlightRenderer.drawAt(gui,8+3*18+8,84+18+8,0xFF2DE2C2,time);
            mc.getTextureManager().bindTexture(new ResourceLocation("textures/gui/container/generic_54.png"));
            GlStateManager.color(1,1,1,1);
            Gui.drawModalRectWithCustomSizedTexture(250,58,0,0,176,71,256,256);
            Gui.drawModalRectWithCustomSizedTexture(250,129,0,126,176,96,256,256);
            gui.position(250,58);
            InventoryHighlightRenderer.drawAt(gui,8+4*18+8,18+18+8,0xFF2DE2C2,time);
            ClientChatRenderer.drawAt(mc.fontRendererObj,text,40,240,0xFFFFFFFF,true,time);
            ImageIO.write(read(960,540),"png",output.resolve("chat-inventory-"+time+".png").toFile());
        }
        if(GL11.glGetError()!=GL11.GL_NO_ERROR) fail("Rendering GL error");
        System.out.println("Chat prefix frame animation, unchanged body pixels, shadow/fade, wrapping, slot pulse, GUI scales/origins and GL state passed.");
    }

    private static void verifyTransformedFont(Minecraft mc,String text) throws Exception {
        // Execute the actual patched Minecraft final draw overload, rather
        // than only calling the helper or checking that an ASM call exists.
        ClassLoader patched=new ClassLoader(ChatInventoryRenderCheck.class.getClassLoader()) {
            @Override protected synchronized Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                if(!name.equals("net.minecraft.client.gui.FontRenderer")&&!name.equals("dev.vibe.chat.ClientChatRenderer"))
                    return super.loadClass(name,resolve);
                Class<?> type=findLoadedClass(name);
                if(type==null) try(InputStream in=getParent().getResourceAsStream(name.replace('.','/')+".class")) {
                    ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[8192];
                    for(int n;(n=in.read(bytes))>=0;)out.write(bytes,0,n);
                    byte[] code=out.toByteArray();
                    if(name.endsWith("FontRenderer"))code=new dev.vibe.core.FontLocalizationTransformer().transform(name,name,code);
                    type=defineClass(name,code,0,code.length);
                } catch(Exception e) { throw new ClassNotFoundException(name,e); }
                if(resolve)resolveClass(type);return type;
            }
        };
        Class<?> type=patched.loadClass("net.minecraft.client.gui.FontRenderer");
        Object font=type.getConstructor(net.minecraft.client.settings.GameSettings.class,ResourceLocation.class,
                net.minecraft.client.renderer.texture.TextureManager.class,boolean.class)
                .newInstance(mc.gameSettings,new ResourceLocation("textures/font/ascii.png"),mc.renderEngine,false);
        type.getMethod("onResourceManagerReload",net.minecraft.client.resources.IResourceManager.class)
                .invoke(font,mc.getResourceManager());
        frame(960,540);mc.fontRendererObj.drawString(text,20,20,-1,true);
        BufferedImage nativeText=read(960,540);
        frame(960,540);
        Object end=type.getMethod("drawString",String.class,float.class,float.class,int.class,boolean.class)
                .invoke(font,text,20F,20F,-1,true);
        BufferedImage animated=read(960,540);
        int body=20+mc.fontRendererObj.getStringWidth(ClientChat.PREFIX);
        if(different(nativeText,animated,20,20,body,32)==0)fail("Patched FontRenderer did not animate local prefix");
        if(different(nativeText,animated,body,15,500,36)!=0)fail("Patched FontRenderer changed body rendering");
        if(!end.equals(21+mc.fontRendererObj.getStringWidth(text)))fail("Patched FontRenderer changed return width");
    }

    private static void frame(int width,int height) {
        GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glViewport(0,0,960,540); GL11.glClearColor(16/255F,21/255F,31/255F,1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity(); GL11.glOrtho(0,width,height,0,-100,100);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity();
        GlStateManager.enableTexture2D(); GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,1,0);
        GlStateManager.color(1,1,1,1);
        GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glEnable(GL11.GL_BLEND); GL11.glColor4f(1,1,1,1);
    }
    private static BufferedImage read(int width,int height) {
        ByteBuffer pixels=BufferUtils.createByteBuffer(width*height*4);
        GL11.glReadPixels(0,0,width,height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
            int i=((height-1-y)*width+x)*4;
            image.setRGB(x,y,0xFF000000|(pixels.get(i)&255)<<16|(pixels.get(i+1)&255)<<8|(pixels.get(i+2)&255));
        }
        return image;
    }
    private static int different(BufferedImage a,BufferedImage b,int left,int top,int right,int bottom) {
        int n=0;for(int y=top;y<bottom;y++)for(int x=left;x<right;x++)if(a.getRGB(x,y)!=b.getRGB(x,y))n++;return n;
    }
    private static int[] bounds(BufferedImage image,int background) {
        int left=image.getWidth(),top=image.getHeight(),right=-1,bottom=-1;
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if(image.getRGB(x,y)!=background) {
            left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
        }
        return new int[]{left,top,right,bottom};
    }
    private static void fail(String reason) { throw new AssertionError(reason); }
}
