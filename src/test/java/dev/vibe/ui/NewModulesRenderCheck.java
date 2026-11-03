package dev.vibe.ui;

import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.visual.*;
import dev.vibe.path.PathTestFixture;
import dev.vibe.ui.screen.BlockEspEditorGui;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Native editor input/layout and transformed dropped-item pose checks. */
public final class NewModulesRenderCheck {
    public static void verify(Minecraft mc,ModuleManager modules,Path output)throws Exception{
        dev.vibe.camera.DroneCollisionsTest.registerBlocks();dev.vibe.ui.render.esp.EspRenderCheck.installItemRenderer(mc);
        BlockESPModule blocks=new BlockESPModule();modules.registerDynamic(blocks);
        BlockESPModule.Style style=new BlockESPModule.Style(0xFF49C5D9);style.enabled=true;blocks.put(Blocks.diamond_ore,style);
        for(int[] size:new int[][]{{960,540},{480,320},{320,260}}){
            mc.displayWidth=size[0];mc.displayHeight=size[1];mc.gameSettings.guiScale=1;
            BlockEspEditorGui gui=new BlockEspEditorGui(blocks);gui.setWorldAndResolution(mc,size[0],size[1]);
            PathTestFixture.set(BlockEspEditorGui.class,gui,"selected",Blocks.diamond_ore);
            ClientChangesRenderCheck.frame(size[0],size[1]);gui.drawScreen(-1,-1,0);save(output,"block-editor-"+size[0]+".png",size[0],size[1]);check();
            Method edit=BlockEspEditorGui.class.getDeclaredMethod("editColor",int.class);edit.setAccessible(true);edit.invoke(gui,1);
            ClientChangesRenderCheck.frame(size[0],size[1]);gui.drawScreen(-1,-1,0);save(output,"block-editor-rgba-"+size[0]+".png",size[0],size[1]);check();
            net.minecraft.client.gui.GuiTextField hex=(net.minecraft.client.gui.GuiTextField)field(gui,"hex");hex.setText("#AA42BDCA");
            GuiInput.mouseClicked(gui,(size[0]-264)/2+50,(size[1]-200)/2+180,0);
            if(blocks.style(Blocks.diamond_ore).outline!=0xAA42BDCA)throw new AssertionError("Block outline RGBA input did not save");
        }
        ItemPhysicsModule physics=new ItemPhysicsModule();modules.registerDynamic(physics);physics.setEnabled(true);
        ClassLoader child=new ClassLoader(NewModulesRenderCheck.class.getClassLoader()){
            @Override protected synchronized Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
                if(!name.equals("net.minecraft.client.renderer.entity.RenderEntityItem"))return super.loadClass(name,resolve);
                Class<?> type=findLoadedClass(name);if(type==null)try(InputStream in=getParent().getResourceAsStream(name.replace('.','/')+".class")){
                    ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];for(int n;(n=in.read(b))>=0;)out.write(b,0,n);
                    byte[] transformed=new dev.vibe.core.ItemPhysicsTransformer().transform(name,name,out.toByteArray());type=defineClass(name,transformed,0,transformed.length);
                }catch(IOException error){throw new ClassNotFoundException(name,error);}if(resolve)resolveClass(type);return type;
            }
        };
        Class<?> type=child.loadClass("net.minecraft.client.renderer.entity.RenderEntityItem");Object renderer=PathTestFixture.allocate(type);Method helper=null;
        for(Method m:type.getDeclaredMethods())if(m.getReturnType()==int.class&&m.getParameterTypes().length==6){helper=m;break;}if(helper==null)throw new AssertionError("Native dropped item helper missing");helper.setAccessible(true);
        EntityItem item=new EntityItem(null,2,3,4,new ItemStack(Items.diamond,64));item.onGround=true;item.motionX=.2;item.motionY=-.3;
        IBakedModel model=mc.getRenderItem().getItemModelMesher().getItemModel(item.getEntityItem());
        ClientChangesRenderCheck.frame(960,540);GlStateManager.pushMatrix();
        FloatBuffer matrix=BufferUtils.createFloatBuffer(16);try{GlStateManager.loadIdentity();int count=(Integer)helper.invoke(renderer,item,2D,3D,4D,.5F,model);GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,matrix);
            if(count!=5)throw new AssertionError("Item Physics lost native stack copies");if(Math.abs(matrix.get(5))>.001)throw new AssertionError("Ground item did not lie flat");
        }finally{GlStateManager.popMatrix();}
        if(item.posX!=2||item.posY!=3||item.posZ!=4||item.motionX!=.2||item.motionY!=-.3)throw new AssertionError("Cosmetic physics changed actual entity movement");
        physics.setEnabled(false);check();System.out.println("Block editor compact layouts and RGBA edits passed; actual transformed dropped-item helper keeps native stack copies and entity movement.");
    }
    private static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static void check(){int error=GL11.glGetError();if(error!=0)throw new AssertionError("New module GL error "+error);}
    private static void save(Path output,String name,int w,int h)throws Exception{ByteBuffer b=BufferUtils.createByteBuffer(w*h*4);GL11.glReadPixels(0,0,w,h,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=(y*w+x)*4;image.setRGB(x,h-1-y,0xFF000000|(b.get(i)&255)<<16|(b.get(i+1)&255)<<8|b.get(i+2)&255);}ImageIO.write(image,"png",output.resolve(name).toFile());}
}
