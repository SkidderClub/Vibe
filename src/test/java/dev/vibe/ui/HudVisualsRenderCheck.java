package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.hud.*;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.Category;
import dev.vibe.module.impl.client.HudModule;
import dev.vibe.module.impl.visual.OofArrowsModule;
import dev.vibe.path.PathTestFixture;
import dev.vibe.ui.render.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.model.*;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Actual item bytecode, native pixels, warm GPU reuse, ArrayList alpha and arrow/domain samples. */
public final class HudVisualsRenderCheck {
    private static Minecraft mc;private static Path output;
    public static long fixedTime() {return 2450L;}
    public static void verify(Minecraft client,ModuleManager modules,Path directory) throws Exception {
        mc=client;output=directory;dev.vibe.camera.DroneCollisionsTest.registerBlocks();
        HudModule hud=new HudModule();modules.registerDynamic(hud);
        HudManager manager=new HudManager(output.resolve("hud-visuals-fixture").toFile());
        PathTestFixture.set(Vibe.class,Vibe.getInstance(),"hudManager",manager);
        verifyArray(hud,manager,modules);verifyArrows();verifyDomain(hud,manager);verifyItems();
        if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new AssertionError("HUD visuals GL error");
        System.out.println("ArrayList uniform alpha, gap-spanning outlines, OOF arrow shapes, domain color frames and native/cached item pixels passed.");
    }
    private static void verifyArray(HudModule hud,HudManager manager,ModuleManager modules) throws Exception {
        String[] names={"Longest visual module","Middle entry","Short"};
        for(String name:names) {Module m=new Module(name,"",Category.VISUAL,0){};modules.registerDynamic(m);m.setEnabled(true);}
        hud.synchronizeArrayListModules();hud.getArrayListModules().setValue(new LinkedHashSet<String>(Arrays.asList(names)));
        hud.getArrayOutline().setEnabled(false);hud.array.rail.setValue("None");hud.array.background.setValue("Steps");
        hud.array.color.setValue("Static");hud.array.suffix.setValue("None");hud.array.fade.setValue(.9D);
        hud.array.rowHeight.setValue(12.5D);hud.array.scale.setValue(1.25D);hud.getBackground().setValue(0x80000000);
        ArrayListRenderer renderer=new ArrayListRenderer();HudManager.HudElement e=manager.getElement(HudManager.ARRAY_LIST);
        frame();Gui.drawRect(0,0,960,540,-1);renderer.draw(hud,e,new ScaledResolution(mc),false);
        BufferedImage image=read();int x=e.getLeft()+e.getWidth()-1;
        for(int y=e.getTop();y<e.getTop()+e.getHeight();y++) {
            int red=image.getRGB(x,y)>>16&255;
            if(Math.abs(red-127)>2)throw new AssertionError("Array background alpha varies by row at "+x+","+y+": "+red);
        }
        save("array-background-uniform.png");
        frame();Gui.drawRect(0,0,960,540,-1);
        GlStateManager.enableBlend();GL11.glDisable(GL11.GL_BLEND);
        renderer.draw(hud,e,new ScaledResolution(mc),false);image=read();
        for(int y=e.getTop();y<e.getTop()+e.getHeight();y++)
            if(Math.abs((image.getRGB(x,y)>>16&255)-127)>2)throw new AssertionError("Stale blend cache darkened the first ArrayList row");
        dev.vibe.module.impl.client.BlurModule blur=new dev.vibe.module.impl.client.BlurModule();
        modules.registerDynamic(blur);blur.setEnabled(true);
        frame();Gui.drawRect(0,0,960,540,-1);KawaseBlur.beginHudFrame();
        renderer.draw(hud,e,new ScaledResolution(mc),false);image=read();save("array-background-blurred.png");
        for(int y=e.getTop()+2;y<e.getTop()+e.getHeight()-2;y++)
            if(Math.abs((image.getRGB(x,y)>>16&255)-127)>2)
                throw new AssertionError("Blur darkened ArrayList background at row "+(y-e.getTop())+": "+(image.getRGB(x,y)>>16&255));
        blur.setEnabled(false);
        hud.array.rail.setValue("Full");hud.array.gap.setValue(4D);hud.array.fade.setValue(0D);
        hud.getArrayPrimaryColor().setValue(0xFFFF6688);
        frame();renderer.draw(hud,e,new ScaledResolution(mc),false);save("array-accent-full.png");
        image=read();x=e.getLeft()+e.getWidth()-1;
        for(int y=e.getTop()+1;y<e.getTop()+e.getHeight()-1;y++)
            if((image.getRGB(x,y)>>16&255)<80)throw new AssertionError("Full rail has a break at row gap: "+y);
        hud.array.rail.setValue("Both");frame();renderer.draw(hud,e,new ScaledResolution(mc),false);save("array-accent-sides.png");
        hud.array.rail.setValue("None");hud.getArrayOutline().setEnabled(true);hud.array.outline.setValue("Steps");
        hud.array.glow.setEnabled(true);frame();renderer.draw(hud,e,new ScaledResolution(mc),false);save("array-outline-steps.png");
        hud.array.outline.setValue("Rectangle");frame();renderer.draw(hud,e,new ScaledResolution(mc),false);save("array-outline-rectangle.png");
        hud.getArrayOutline().setEnabled(false);hud.array.glow.setEnabled(false);hud.array.font.setValue("Minecraft");
        hud.array.textGlow.setEnabled(true);hud.array.textGlowStrength.setValue(1D);hud.array.textGlowRadius.setValue(3D);
        hud.array.rail.setValue("Full");frame();renderer.draw(hud,e,new ScaledResolution(mc),false);BufferedImage full=read();save("array-text-glow-full.png");
        hud.array.rail.setValue("Outer");frame();renderer.draw(hud,e,new ScaledResolution(mc),false);BufferedImage side=read();
        int glowPixels=0;
        hud.array.textGlow.setEnabled(false);frame();renderer.draw(hud,e,new ScaledResolution(mc),false);BufferedImage plain=read();
        for(int y=e.getTop()+3;y<e.getTop()+12;y++)for(int px=e.getLeft()+8;px<e.getLeft()+e.getWidth()-7;px++) {
            if(full.getRGB(px,y)!=side.getRGB(px,y))throw new AssertionError("Full outline changed text/glow blending");
            if(side.getRGB(px,y)!=plain.getRGB(px,y))glowPixels++;
        }
        if(glowPixels==0)throw new AssertionError("ArrayList text glow rendered no pixels");
    }
    private static void verifyArrows() throws Exception {
        verifyArrowCapture();
        OofArrowsModule settings=new OofArrowsModule();settings.names.setEnabled(true);settings.distances.setEnabled(true);
        settings.radius.setValue(142D);settings.colorMode.setValue("Fade");
        OofArrowsRenderer renderer=new OofArrowsRenderer();List<OofArrowsRenderer.Arrow> entries=new ArrayList<OofArrowsRenderer.Arrow>();
        for(int i=0;i<8;i++)entries.add(new OofArrowsRenderer.Arrow("Player "+(i+1),15+i*12,Math.PI*i/4));
        for(String shape:new String[]{"Circle","Oval"})for(String style:new String[]{"Arrow","Triangle","Chevron"}) {
            frame();settings.placement.setValue(shape);settings.style.setValue(style);
            renderer.draw(settings,entries,960,540,2000);save("oof-"+shape.toLowerCase()+"-"+style.toLowerCase()+".png");
        }
        settings.colorMode.setValue("Static");settings.color.setValue(0xFF70E0A0);settings.distanceFade.setEnabled(false);
        settings.glow.setEnabled(false);settings.outline.setEnabled(false);settings.names.setEnabled(false);settings.distances.setEnabled(false);
        for(String style:new String[]{"Arrow","Triangle","Chevron"}) {
            frame();settings.style.setValue(style);renderer.draw(settings,Collections.singletonList(new OofArrowsRenderer.Arrow("Shape",10,Math.PI/2)),960,540,0);
            BufferedImage glyph=read();int left=960,right=-1,top=540,bottom=-1;
            for(int y=0;y<540;y++)for(int x=0;x<960;x++)if((glyph.getRGB(x,y)&0xFFFFFF)==0x70E0A0) {
                left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
            }
            if(right<left||bottom<top||(right-left+1)>1.5*(bottom-top+1))throw new AssertionError("Arrow shape is missing or too elongated: "+style);
        }
    }
    private static void verifyArrowCapture() throws Exception {
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPushMatrix();GL11.glLoadIdentity();
        org.lwjgl.util.glu.GLU.gluPerspective(70,960/540F,.1F,512);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();GL11.glLoadIdentity();GL11.glRotatef(180,0,1,0);
        try(PathTestFixture f=new PathTestFixture()) {
            f.mc.displayWidth=960;f.mc.displayHeight=540;f.mc.gameSettings=mc.gameSettings;
            PathTestFixture.set(Minecraft.class,f.mc,"mcLanguageManager",mc.getLanguageManager());
            net.minecraft.client.renderer.entity.RenderManager camera=PathTestFixture.allocate(net.minecraft.client.renderer.entity.RenderManager.class);
            camera.viewerPosY=65.62;camera.playerViewY=0;
            PathTestFixture.set(Minecraft.class,f.mc,"renderManager",camera);
            f.player.setPosition(0,64,0);
            String[] names={"Front","Side","Rear","Hidden","Far","Friend","Dead"};
            double[][] positions={{0,10},{10,0},{0,-10},{-10,0},{0,-200},{10,-10},{-10,-10}};
            for(int i=0;i<names.length;i++) {
                PathTestFixture.Player p=f.addPlayer(i+2,names[i],positions[i][0],64,positions[i][1]);
                p.setPosition(p.posX,p.posY,p.posZ);
                if(names[i].equals("Hidden"))PathTestFixture.set(PathTestFixture.Player.class,p,"hidden",true);
                if(names[i].equals("Dead"))p.isDead=true;
            }
            dev.vibe.friend.FriendManager friends=new dev.vibe.friend.FriendManager(output.resolve("arrow-fixture").toFile());
            friends.add("Friend","");PathTestFixture.set(Vibe.class,f.vibe,"friendManager",friends);
            OofArrowsModule s=new OofArrowsModule();f.modules.registerDynamic(s);s.setEnabled(true);
            OofArrowsRenderer renderer=new OofArrowsRenderer();renderer.capture(1);
            assertArrowNames(renderer,"Side","Rear","Hidden");
            assertArrowSide(renderer,"Side",false);assertArrowSide(renderer,"Hidden",true);
            s.display.setValue("All directions");renderer.capture(1);assertArrowNames(renderer,"Front","Side","Rear","Hidden");
            s.display.setValue("In FOV");renderer.capture(1);assertArrowNames(renderer,"Front");
            s.display.setValue("Out of FOV");s.invisibility.setValue("Only");renderer.capture(1);assertArrowNames(renderer,"Hidden");
            s.invisibility.setValue("Exclude");renderer.capture(1);assertArrowNames(renderer,"Side","Rear");
            s.invisibility.setValue("Include");s.ignoreFriends.setEnabled(false);renderer.capture(1);
            assertArrowNames(renderer,"Side","Rear","Hidden","Friend");
            s.ignoreFriends.setEnabled(true);
            f.block(5,65,0,net.minecraft.init.Blocks.stone.getDefaultState());
            s.display.setValue("All directions");s.visibility.setValue("Visible only");renderer.capture(1);assertArrowNames(renderer,"Front","Rear","Hidden");
            s.visibility.setValue("Occluded only");renderer.capture(1);assertArrowNames(renderer,"Side");
            s.visibility.setValue("All");s.visibleOpacity.setValue(.3);renderer.capture(1);assertArrowNames(renderer,"Front","Side","Rear","Hidden");
            for(OofArrowsRenderer.Arrow a:capturedArrows(renderer))
                if(a.lineOfSight==a.name.equals("Side"))throw new AssertionError("Line of sight was not captured for "+a.name);
            s.visibleOpacity.setValue(0D);renderer.capture(1);assertArrowNames(renderer,"Side");
            s.occludedOpacity.setValue(0D);renderer.capture(1);assertArrowNames(renderer);
            s.occludedOpacity.setValue(1D);
            s.visibleOpacity.setValue(1D);s.display.setValue("Out of FOV");
            s.range.setValue(8D);renderer.capture(1);assertArrowNames(renderer);
            s.range.setValue(128D);camera.playerViewY=90;GL11.glLoadIdentity();GL11.glRotatef(270,0,1,0);
            renderer.capture(1);assertArrowNames(renderer,"Front","Side","Rear");
            assertArrowSide(renderer,"Front",false);assertArrowSide(renderer,"Rear",true);
            s.limit.setValue(1D);renderer.capture(1);
            if(arrowNames(renderer).size()!=1)throw new AssertionError("Arrow limit ignored");
            s.setEnabled(false);renderer.capture(1);assertArrowNames(renderer);
            renderer.clear();assertArrowNames(renderer);
            verifyMurderArrow(f,renderer,s);
        } finally {
            GL11.glPopMatrix();GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();GL11.glMatrixMode(GL11.GL_MODELVIEW);
        }
    }
    private static void verifyMurderArrow(PathTestFixture f,OofArrowsRenderer renderer,OofArrowsModule s) throws Exception {
        for(Object value:f.world.playerEntities) {
            net.minecraft.entity.player.EntityPlayer player=(net.minecraft.entity.player.EntityPlayer)value;
            player.inventory=new net.minecraft.entity.player.InventoryPlayer(player);
        }
        net.minecraft.entity.player.EntityPlayer killer=(net.minecraft.entity.player.EntityPlayer)f.world.getEntityByID(3);
        killer.inventory.mainInventory[0]=new ItemStack(Items.iron_sword);
        net.minecraft.scoreboard.Scoreboard board=new net.minecraft.scoreboard.Scoreboard();
        net.minecraft.scoreboard.ScoreObjective objective=board.addScoreObjective("murder",net.minecraft.scoreboard.IScoreObjectiveCriteria.DUMMY);
        objective.setDisplayName("MURDER MYSTERY");board.setObjectiveInDisplaySlot(1,objective);
        board.getValueFromObjective("Role: Innocent",objective).setScorePoints(1);
        PathTestFixture.set(net.minecraft.world.World.class,f.world,"worldScoreboard",board);
        dev.vibe.module.impl.meme.HypixelModule murder=new dev.vibe.module.impl.meme.HypixelModule();
        f.modules.registerDynamic(murder);PathTestFixture.set(Module.class,murder,"enabled",true);
        for(dev.vibe.setting.Setting<?> setting:murder.getSettings())if(setting.getRawName().equals("Murderer ESP color"))
            ((dev.vibe.setting.ColorSetting)setting).setValue(0xFFE648BC);
        murder.tick();
        s.setEnabled(true);s.display.setValue("All directions");s.visibility.setValue("All");s.invisibility.setValue("Include");
        s.limit.setValue(20D);s.distanceFade.setEnabled(false);s.colorMode.setValue("Rainbow");
        s.names.setEnabled(false);s.distances.setEnabled(false);s.glow.setEnabled(false);s.outline.setEnabled(false);
        renderer.capture(1);boolean overridden=false;
        for(OofArrowsRenderer.Arrow arrow:capturedArrows(renderer))if(arrow.name.equals("Side"))overridden=arrow.overrideColor==0xFFE648BC;
        if(!overridden)throw new AssertionError("Detected murderer color did not reach the OOF arrow");
        frame();renderer.draw(s,capturedArrows(renderer),960,540,2000);BufferedImage image=read();
        int pink=0;
        for(int y=0;y<540;y++)for(int x=0;x<960;x++)if((image.getRGB(x,y)&0xFFFFFF)==0xE648BC)pink++;
        if(pink<5)throw new AssertionError("Murderer OOF arrow used the ordinary rainbow color");
        save("oof-murderer-color.png");renderer.clear();
    }
    private static Set<String> arrowNames(OofArrowsRenderer renderer) throws Exception {
        Set<String> names=new LinkedHashSet<String>();
        for(OofArrowsRenderer.Arrow arrow:capturedArrows(renderer))names.add(arrow.name);
        return names;
    }
    @SuppressWarnings("unchecked")
    private static List<OofArrowsRenderer.Arrow> capturedArrows(OofArrowsRenderer renderer) throws Exception {
        java.lang.reflect.Field field=OofArrowsRenderer.class.getDeclaredField("arrows");field.setAccessible(true);
        return (List<OofArrowsRenderer.Arrow>)field.get(renderer);
    }
    private static void assertArrowSide(OofArrowsRenderer renderer,String name,boolean right) throws Exception {
        for(OofArrowsRenderer.Arrow arrow:capturedArrows(renderer))if(arrow.name.equals(name)) {
            float x=OofArrowsRenderer.position(960,540,90,11,false,arrow.bearing)[0];
            if(right?x<=480:x>=480)throw new AssertionError(name+" arrow is mirrored");
            return;
        }
        throw new AssertionError("Missing directional arrow: "+name);
    }
    private static void assertArrowNames(OofArrowsRenderer renderer,String... expected) throws Exception {
        Set<String> names=arrowNames(renderer);
        if(!names.equals(new HashSet<String>(Arrays.asList(expected))))throw new AssertionError("Unexpected offscreen entities: "+names);
    }
    private static void verifyDomain(HudModule hud,HudManager manager) throws Exception {
        hud.getReplaceScoreboardServer().setEnabled(true);manager.getElement(HudManager.SCOREBOARD).setTheme("Skeet");
        for(String mode:new String[]{"Static","Fade","Rainbow"}) {
            hud.scoreboardColors.mode.setValue(mode);hud.scoreboardColors.color.setValue(0xFFFF88CC);
            frame();manager.drawPreview(manager.getElement(HudManager.SCOREBOARD),mc.fontRendererObj);save("scoreboard-domain-"+mode.toLowerCase()+".png");
            frame();hud.scoreboardColors.draw(mc.fontRendererObj,"bipas.gay",30,30,0);BufferedImage a=read();
            frame();hud.scoreboardColors.draw(mc.fontRendererObj,"bipas.gay",30,30,1000);int different=different(a,read());
            if(mode.equals("Static")?different!=0:different==0)throw new AssertionError("Domain color mode did not behave: "+mode);
        }
    }
    private static void verifyItems() throws Exception {
        // The child defines the real native RenderItem with only our model-writer hook.
        // Its time source is fixed in both branches so glint frames can be compared exactly.
        ClassLoader loader=new ClassLoader(HudVisualsRenderCheck.class.getClassLoader()) {
            @Override protected synchronized Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                if(!name.equals("net.minecraft.client.renderer.entity.RenderItem")&&!name.startsWith("net.minecraft.client.renderer.entity.RenderItem$"))return super.loadClass(name,resolve);
                Class<?> type=findLoadedClass(name);
                if(type==null)try(InputStream in=getParent().getResourceAsStream(name.replace('.','/')+".class")) {
                    ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[8192];for(int n;(n=in.read(bytes))>=0;)out.write(bytes,0,n);
                    byte[] raw=new dev.vibe.core.HudItemsTransformer().transform(name,name,out.toByteArray());
                    ClassNode node=new ClassNode();new ClassReader(raw).accept(node,0);
                    for(Object entry:node.methods)for(AbstractInsnNode instruction:((MethodNode)entry).instructions.toArray())
                        if(instruction instanceof MethodInsnNode) {
                            MethodInsnNode call=(MethodInsnNode)instruction;
                            if(call.owner.equals("net/minecraft/client/Minecraft")&&call.name.equals("getSystemTime")&&call.desc.equals("()J")) {
                                call.owner="dev/vibe/ui/HudVisualsRenderCheck";call.name="fixedTime";
                            }
                        }
                    ClassWriter writer=new ClassWriter(0);node.accept(writer);raw=writer.toByteArray();
                    type=defineClass(name,raw,0,raw.length);
                }catch(Exception e){throw new ClassNotFoundException(name,e);}
                if(resolve)resolveClass(type);return type;
            }
        };
        IBakedModel[] current={model(false)};
        ModelManager models=new ModelManager(new TextureMap("textures")) {
            @Override public IBakedModel getModel(ModelResourceLocation location){return current[0];}
            @Override public IBakedModel getMissingModel(){return current[0];}
        };
        Class<?> type=loader.loadClass("net.minecraft.client.renderer.entity.RenderItem");
        Object renderer=type.getConstructor(TextureManager.class,ModelManager.class).newInstance(mc.renderEngine,models);
        Method render=type.getMethod("renderItemAndEffectIntoGUI",ItemStack.class,int.class,int.class);
        Method overlay=type.getMethod("renderItemOverlayIntoGUI",FontRenderer.class,ItemStack.class,int.class,int.class,String.class);
        ItemStack plain=new ItemStack(Items.diamond_sword),stack=new ItemStack(Items.diamond,64),damaged=new ItemStack(Items.diamond_pickaxe,1,730);
        ItemStack enchanted=new ItemStack(Items.diamond_sword);enchanted.addEnchantment(net.minecraft.enchantment.Enchantment.sharpness,1);
        ItemStack[] items={plain,stack,damaged,enchanted};
        atlas(0xFF82B4E8);mc.gameSettings.useVbo=true;HudItemGeometryRenderer.clear();
        for(float scale:new float[]{1,1.5F,2}) {
            frame();drawItems(renderer,render,overlay,items,scale,false);BufferedImage nativeFrame=read();
            frame();drawItems(renderer,render,overlay,items,scale,true);BufferedImage cachedFrame=read();
            if(different(nativeFrame,cachedFrame)!=0) {save("item-cache-mismatch.png");throw new AssertionError("Item pixels changed at scale "+scale+": "+different(nativeFrame,cachedFrame));}
        }
        save("hud-items-native-identical.png");
        int uploads=HudItemGeometryRenderer.uploads();
        frame();drawItems(renderer,render,overlay,items,1,true);BufferedImage blue=read();
        atlas(0xFFE8B482);frame();drawItems(renderer,render,overlay,items,1,true);
        if(different(blue,read())==0)throw new AssertionError("Item texture animation froze");
        if(HudItemGeometryRenderer.uploads()!=uploads)throw new AssertionError("Warm items uploaded geometry again");
        current[0]=model(true);
        type.getMethod("onResourceManagerReload",net.minecraft.client.resources.IResourceManager.class).invoke(renderer,mc.getResourceManager());
        if(HudItemGeometryRenderer.cachedModels()!=0)throw new AssertionError("Native resource reload retained stale item geometry");
        ItemStack leather=new ItemStack(Items.leather_chestplate);((net.minecraft.item.ItemArmor)Items.leather_chestplate).setColor(leather,0xAA4477);
        frame();drawItems(renderer,render,overlay,new ItemStack[]{leather},1,false);BufferedImage tinted=read();
        frame();drawItems(renderer,render,overlay,new ItemStack[]{leather},1,true);
        if(different(tinted,read())!=0)throw new AssertionError("Dynamic leather color changed");
        mc.gameSettings.useVbo=false;frame();drawItems(renderer,render,overlay,items,1,true);mc.gameSettings.useVbo=true;
        current[0]=model(false);type.getMethod("onResourceManagerReload",net.minecraft.client.resources.IResourceManager.class).invoke(renderer,mc.getResourceManager());
        // Warm both native paths before recording timings; no absolute performance threshold depends on this machine.
        for(int i=0;i<8;i++){frame();drawItems(renderer,render,overlay,items,1,i%2==0);}
        long nativeNanos=bench(renderer,render,overlay,items,false),cachedNanos=bench(renderer,render,overlay,items,true);
        System.out.println("HUD items: warmed native "+nativeNanos/1e6+" ms, cached "+cachedNanos/1e6+" ms; "+HudItemGeometryRenderer.uploads()+" GPU uploads, "+HudItemGeometryRenderer.hits()+" reuses.");
        if(HudItemGeometryRenderer.hits()<50)throw new AssertionError("The actual native item hook did not reuse GPU geometry");
        HudItemGeometryRenderer.clear();if(HudItemGeometryRenderer.cachedModels()!=0)throw new AssertionError("GPU cache did not clear");
        int previousBuffer=org.lwjgl.opengl.GL15.glGenBuffers();
        org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER,previousBuffer);
        try(HudItemGeometryRenderer.Scope scope=HudItemGeometryRenderer.begin()) {
            if(!HudItemGeometryRenderer.draw(model(false),-1,plain))throw new AssertionError("Native model was not cached");
            if(GL11.glGetInteger(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER_BINDING)!=previousBuffer)
                throw new AssertionError("Cold item upload changed the caller's VBO binding");
        } finally {
            org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER,0);
            org.lwjgl.opengl.GL15.glDeleteBuffers(previousBuffer);HudItemGeometryRenderer.clear();
        }
    }
    private static long bench(Object renderer,Method render,Method overlay,ItemStack[] items,boolean cache) throws Exception {
        GL11.glFinish();long start=System.nanoTime();
        for(int i=0;i<40;i++){frame();drawItems(renderer,render,overlay,items,1,cache);}
        GL11.glFinish();return System.nanoTime()-start;
    }
    private static void drawItems(Object renderer,Method render,Method overlay,ItemStack[] items,float scale,boolean cache) throws Exception {
        GlStateManager.pushMatrix();GlStateManager.translate(30,60,0);GlStateManager.scale(scale,scale,scale);
        RenderHelper.enableGUIStandardItemLighting();GlStateManager.enableDepth();
        HudItemGeometryRenderer.Scope scope=cache?HudItemGeometryRenderer.begin():null;
        try {
            for(int i=0;i<28;i++) {
                ItemStack item=items[i%items.length];int x=(i%9)*20,y=(i/9)*20;
                render.invoke(renderer,item,x,y);overlay.invoke(renderer,mc.fontRendererObj,item,x,y,null);
            }
        } finally {if(scope!=null)scope.close();RenderHelper.disableStandardItemLighting();GlStateManager.disableDepth();GlStateManager.popMatrix();}
        if(GL11.glGetInteger(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER_BINDING)!=0)throw new AssertionError("Items leaked a VBO binding");
    }
    private static IBakedModel model(boolean tint) {
        List<BakedQuad> quads=new ArrayList<BakedQuad>();
        // A detailed sprite's front/side geometry, rather than a single flat GUI quad.
        for(int strip=0;strip<48;strip++) {
            float x1=strip/48F,x2=(strip+1)/48F;
            int[] vertices=new int[28];float[][] p={{x1,1,0,x1,0},{x1,0,0,x1,1},{x2,0,0,x2,1},{x2,1,0,x2,0}};
            for(int v=0;v<4;v++){int at=v*7;for(int k=0;k<3;k++)vertices[at+k]=Float.floatToRawIntBits(p[v][k]);vertices[at+3]=-1;vertices[at+4]=Float.floatToRawIntBits(p[v][3]);vertices[at+5]=Float.floatToRawIntBits(p[v][4]);vertices[at+6]=0x007F0000;}
            quads.add(new BakedQuad(vertices,tint?0:-1,EnumFacing.SOUTH));
        }
        List<List<BakedQuad>> faces=new ArrayList<List<BakedQuad>>();for(int i=0;i<6;i++)faces.add(Collections.<BakedQuad>emptyList());
        return new SimpleBakedModel(quads,faces,false,false,null,ItemCameraTransforms.DEFAULT);
    }
    private static void atlas(int color) {
        BufferedImage texture=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=1;y<15;y++)for(int x=2;x<14;x++)texture.setRGB(x,y,color);
        mc.renderEngine.loadTexture(TextureMap.locationBlocksTexture,new DynamicTexture(texture));
    }
    private static void frame() throws Exception {mc.displayWidth=960;mc.displayHeight=540;mc.gameSettings.guiScale=1;ClientChangesRenderCheck.frame(960,540);}
    private static BufferedImage read() {
        ByteBuffer b=BufferUtils.createByteBuffer(960*540*4);GL11.glReadPixels(0,0,960,540,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);
        BufferedImage image=new BufferedImage(960,540,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<540;y++)for(int x=0;x<960;x++){int i=((539-y)*960+x)*4;image.setRGB(x,y,0xFF000000|(b.get(i)&255)<<16|(b.get(i+1)&255)<<8|(b.get(i+2)&255));}return image;
    }
    private static void save(String name) throws Exception {ImageIO.write(read(),"png",output.resolve(name).toFile());}
    private static int different(BufferedImage a,BufferedImage b) {int count=0;for(int y=0;y<540;y++)for(int x=0;x<960;x++)if(a.getRGB(x,y)!=b.getRGB(x,y))count++;return count;}
}
