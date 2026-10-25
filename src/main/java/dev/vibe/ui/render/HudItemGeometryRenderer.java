package dev.vibe.ui.render;

import java.util.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.client.resources.model.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.pipeline.LightUtil;
import org.lwjgl.opengl.*;

/** Bounded GPU cache of immutable native geometry. Never caches an item's pixels or overlays. */
public final class HudItemGeometryRenderer {
    private static final int LIMIT=192;
    private static final Map<Key,VertexBuffer> CACHE=new LinkedHashMap<Key,VertexBuffer>(32,.75F,true);
    private static final Map<IBakedModel,Boolean> TINTS=new IdentityHashMap<IBakedModel,Boolean>();
    private static final Set<IBakedModel> UNSUPPORTED=Collections.newSetFromMap(new IdentityHashMap<IBakedModel,Boolean>());
    private static final EnumFacing[] FACES=EnumFacing.values();
    private static int depth,uploads,hits;
    private static boolean available;
    private HudItemGeometryRenderer() { }

    public static Scope begin() {
        if(depth++==0) {
            available=OpenGlHelper.useVbo()&&DefaultVertexFormats.ITEM.getNextOffset()==28;
        }
        return new Scope();
    }
    public static final class Scope implements AutoCloseable {
        private boolean closed;
        private Scope() { }
        @Override public void close() {if(!closed) {closed=true;depth=Math.max(0,depth-1);}}
    }
    public static boolean draw(Object object,int color,Object stack) {
        if(depth==0||!available||object==null||object.getClass()!=SimpleBakedModel.class)return false;
        IBakedModel model=(IBakedModel)object;
        if(UNSUPPORTED.contains(model))return false;
        Boolean tinted=TINTS.get(model);
        if(tinted==null) {
            boolean tint=false;
            for(int face=0;face<=FACES.length;face++) {
                List<BakedQuad> quads=face==FACES.length?model.getGeneralQuads():model.getFaceQuads(FACES[face]);
                for(BakedQuad quad:quads) {
                    // Custom/mutable quad providers and shader-expanded formats stay native.
                    if(quad.getClass()!=BakedQuad.class||quad.getVertexData().length!=28) {
                        if(UNSUPPORTED.size()>=LIMIT*2)UNSUPPORTED.clear();
                        UNSUPPORTED.add(model);return false;
                    }
                    tint|=quad.hasTintIndex();
                }
            }
            if(TINTS.size()>=LIMIT*2)TINTS.clear();
            TINTS.put(model,tint);tinted=tint;
        }
        // Leather, potions and other dynamic item colors keep the original color resolver.
        if(tinted&&color==-1&&stack instanceof ItemStack)return false;
        int previous=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        Key key=new Key(model,color);VertexBuffer buffer=CACHE.get(key);
        if(buffer==null) {
            WorldRenderer vertices=new WorldRenderer(4096);
            vertices.begin(GL11.GL_QUADS,DefaultVertexFormats.ITEM);
            for(EnumFacing face:FACES)append(vertices,model.getFaceQuads(face),color);
            append(vertices,model.getGeneralQuads(),color);vertices.finishDrawing();
            buffer=new VertexBuffer(DefaultVertexFormats.ITEM);buffer.bufferData(vertices.getByteBuffer());
            CACHE.put(key,buffer);uploads++;
            if(CACHE.size()>LIMIT) {
                Iterator<VertexBuffer> oldest=CACHE.values().iterator();VertexBuffer evicted=oldest.next();oldest.remove();evicted.deleteGlBuffers();
            }
        } else hits++;
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        try {
            buffer.bindBuffer();GL13.glClientActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);GL11.glVertexPointer(3,GL11.GL_FLOAT,28,0L);
            GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);GL11.glColorPointer(4,GL11.GL_UNSIGNED_BYTE,28,12L);
            GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);GL11.glTexCoordPointer(2,GL11.GL_FLOAT,28,16L);
            GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);GL11.glNormalPointer(GL11.GL_BYTE,28,24L);
            buffer.drawArrays(GL11.GL_QUADS);
        } finally {
            GL11.glPopClientAttrib();OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER,previous);GlStateManager.resetColor();
        }
        return true;
    }
    private static void append(WorldRenderer buffer,List<BakedQuad> quads,int color) {
        for(BakedQuad quad:quads)LightUtil.renderQuadColor(buffer,quad,color);
    }
    public static void clear() {
        for(VertexBuffer buffer:CACHE.values())buffer.deleteGlBuffers();
        CACHE.clear();TINTS.clear();UNSUPPORTED.clear();uploads=hits=0;
    }
    public static int uploads() {return uploads;}
    public static int hits() {return hits;}
    public static int cachedModels() {return CACHE.size();}
    private static final class Key {
        final IBakedModel model;final int color;
        Key(IBakedModel model,int color) {this.model=model;this.color=color;}
        @Override public int hashCode() {return System.identityHashCode(model)*31+color;}
        @Override public boolean equals(Object other) {return other instanceof Key&&((Key)other).model==model&&((Key)other).color==color;}
    }
}
