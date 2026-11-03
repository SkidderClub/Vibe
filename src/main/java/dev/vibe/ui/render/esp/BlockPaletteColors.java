package dev.vibe.ui.render.esp;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
/** Alpha-weighted texture average, multiplied by the block's native default tint. */
public final class BlockPaletteColors {
    private static final Map<Block,Integer> CACHE=new IdentityHashMap<>();
    private static Collection<TextureAtlasSprite> sprites;
    private BlockPaletteColors(){}
    public static void clear(){CACHE.clear();sprites=null;}
    public static int average(Block block){
        Integer cached=CACHE.get(block);if(cached!=null)return cached;
        int fallback=block.getMapColor(block.getDefaultState()).colorValue,color=fallback;
        try{
            net.minecraft.client.resources.model.IBakedModel model=Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelShapes().getModelForState(block.getDefaultState());
            long[] average=new long[4];int tint=block.getRenderColor(block.getDefaultState());
            for(int face=0;face<7;face++)for(net.minecraft.client.renderer.block.model.BakedQuad quad:face==6?model.getGeneralQuads():model.getFaceQuads(net.minecraft.util.EnumFacing.values()[face])){
                TextureAtlasSprite sprite=sprite(quad);if(sprite==null||sprite.getFrameCount()<1)continue;int[][] data=sprite.getFrameTextureData(0);if(data==null||data.length==0||data[0]==null)continue;
                accumulate(average,data[0],quad.hasTintIndex()?tint:0xFFFFFF);
            }
            if(average[3]>0){color=(int)(average[0]/average[3])<<16|(int)(average[1]/average[3])<<8|(int)(average[2]/average[3]);color|=0xFF000000;CACHE.put(block,color);return color;}
            TextureAtlasSprite sprite=Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelShapes().getTexture(block.getDefaultState());
            if(sprite!=null&&sprite.getFrameCount()>0){int[][] data=sprite.getFrameTextureData(0);long r=0,g=0,b=0,weight=0;
                if(data!=null&&data.length>0&&data[0]!=null)for(int pixel:data[0]){int a=pixel>>>24;r+=(pixel>>16&255)*a;g+=(pixel>>8&255)*a;b+=(pixel&255)*a;weight+=a;}
                if(weight>0){color=(int)(r/weight)<<16|(int)(g/weight)<<8|(int)(b/weight);
                    color=((color>>16&255)*(tint>>16&255)/255)<<16|((color>>8&255)*(tint>>8&255)/255)<<8|(color&255)*(tint&255)/255;}
            }
        }catch(RuntimeException unavailable){color=fallback;}
        color|=0xFF000000;CACHE.put(block,color);return color;
    }
    private static TextureAtlasSprite sprite(net.minecraft.client.renderer.block.model.BakedQuad quad){
        // 1.8 quads only retain atlas UVs. Resolve their texture once per palette rebuild.
        if(sprites==null){
            try{java.lang.reflect.Field field=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(net.minecraft.client.renderer.texture.TextureMap.class,"mapUploadedSprites","field_110574_e");
                Map<?,?> atlas=(Map<?,?>)field.get(Minecraft.getMinecraft().getTextureMapBlocks());sprites=new ArrayList<>();for(Object value:atlas.values())if(value instanceof TextureAtlasSprite)sprites.add((TextureAtlasSprite)value);
            }catch(ReflectiveOperationException|RuntimeException unavailable){sprites=Collections.emptyList();}
        }
        int[] data=quad.getVertexData();if(data.length<28)return null;int stride=data.length/4;float u=0,v=0;
        for(int i=0;i<4;i++){u+=Float.intBitsToFloat(data[i*stride+4])*.25F;v+=Float.intBitsToFloat(data[i*stride+5])*.25F;}
        for(TextureAtlasSprite sprite:sprites)if(u>=sprite.getMinU()&&u<=sprite.getMaxU()&&v>=sprite.getMinV()&&v<=sprite.getMaxV())return sprite;return null;
    }
    private static void accumulate(long[] result,int[] pixels,int tint){for(int pixel:pixels){int a=pixel>>>24;result[0]+=(pixel>>16&255)*(long)(tint>>16&255)*a/255;result[1]+=(pixel>>8&255)*(long)(tint>>8&255)*a/255;result[2]+=(pixel&255)*(long)(tint&255)*a/255;result[3]+=a;}}
}
