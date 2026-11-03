package dev.vibe.module.impl.visual;

import com.google.gson.*;
import dev.vibe.setting.*;
import dev.vibe.module.Category;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.util.*;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;

/** Incremental searches only loaded terrain; profiles store separate per-block fill and outline styles. */
public final class BlockESPModule extends dev.vibe.module.Module {
    public final BooleanSetting openEditor=addSetting(new BooleanSetting("Open Editor",false).onEnable(()->open()));
    public final JsonSetting palette=addSetting(new JsonSetting("Block Profiles"));
    public final NumberSetting range=addSetting(new NumberSetting("Range",48,8,128,4));
    public final NumberSetting verticalRange=addSetting(new NumberSetting("Vertical Range",32,8,128,4));
    public final NumberSetting scanBudget=addSetting(new NumberSetting("Blocks Per Tick",4096,256,16384,256));
    public final NumberSetting renderLimit=addSetting(new NumberSetting("Render Limit",512,32,2048,32));
    public final BooleanSetting throughWalls=addSetting(new BooleanSetting("Through Walls",true));
    private JsonObject observed;private final Map<Block,Style> styles=new LinkedHashMap<>();
    private final Map<BlockPos,Block> found=new LinkedHashMap<>();private final ArrayDeque<Cursor> scan=new ArrayDeque<>();
    private World world;private int centerX=Integer.MIN_VALUE,centerZ,centerY,oldRange,oldVertical;private long nextCycle;
    public BlockESPModule(){super("BlockESP","Highlight selected blocks with individual fill and outline colours",Category.VISUAL,Keyboard.KEY_NONE);}
    private void open(){openEditor.setEnabled(false);if(!isConfigLoading())Minecraft.getMinecraft().displayGuiScreen(new dev.vibe.ui.screen.BlockEspEditorGui(this));}
    public static final class Style{
        public boolean enabled;public int fill,outline;public float width=1.2F;
        public Style(int color){fill=0x28000000|color&0xFFFFFF;outline=0xDC000000|color&0xFFFFFF;}
    }
    public Map<Block,Style> styles(){refresh();return Collections.unmodifiableMap(styles);}
    public Map<BlockPos,Block> matches(){return Collections.unmodifiableMap(found);}
    public Style style(Block block){refresh();Style s=styles.get(block);return s==null?new Style(dev.vibe.ui.render.esp.BlockPaletteColors.average(block)):s;}
    public void put(Block block,Style s){
        refresh();JsonObject data=new JsonParser().parse(palette.getValue().toString()).getAsJsonObject(),entry=new JsonObject();
        entry.addProperty("enabled",s.enabled);entry.addProperty("fill",s.fill);entry.addProperty("outline",s.outline);entry.addProperty("width",Math.max(.5,Math.min(4,s.width)));
        data.add(Block.blockRegistry.getNameForObject(block).toString(),entry);palette.setValue(data);refresh();
    }
    private void refresh(){
        if(observed==palette.getValue())return;observed=palette.getValue();styles.clear();
        for(Map.Entry<String,JsonElement> item:observed.entrySet())try{
            ResourceLocation id=new ResourceLocation(item.getKey());if(!Block.blockRegistry.containsKey(id)||!item.getValue().isJsonObject())continue;
            Block block=Block.blockRegistry.getObject(id);if(block==net.minecraft.init.Blocks.air)continue;JsonObject p=item.getValue().getAsJsonObject();
            Style s=new Style(0xFFAAAAAA);s.enabled=p.has("enabled")&&p.get("enabled").getAsBoolean();s.fill=p.get("fill").getAsInt();s.outline=p.get("outline").getAsInt();s.width=p.has("width")?Math.max(.5F,Math.min(4,p.get("width").getAsFloat())):1.2F;styles.put(block,s);
        }catch(RuntimeException invalid){/* A malformed profile cannot interrupt rendering. */}
        scan.clear();centerX=Integer.MIN_VALUE;found.entrySet().removeIf(e->!selected(e.getValue()));
    }
    private boolean selected(Block b){Style s=styles.get(b);return s!=null&&s.enabled;}
    public void tick(){
        Minecraft mc=Minecraft.getMinecraft();refresh();if(!isEnabled()||mc.thePlayer==null||mc.theWorld==null||styles.values().stream().noneMatch(s->s.enabled)){clear();return;}
        if(world!=mc.theWorld){clear();world=mc.theWorld;}
        int cx=MathHelper.floor_double(mc.thePlayer.posX)>>4,cz=MathHelper.floor_double(mc.thePlayer.posZ)>>4,cy=MathHelper.floor_double(mc.thePlayer.posY);long now=System.nanoTime();
        if(cx!=centerX||cz!=centerZ||Math.abs(cy-centerY)>8||oldRange!=range.getInt()||oldVertical!=verticalRange.getInt()){
            scan.clear();found.entrySet().removeIf(e->e.getKey().distanceSq(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ)>range.getDouble()*range.getDouble());centerX=cx;centerZ=cz;centerY=cy;oldRange=range.getInt();oldVertical=verticalRange.getInt();nextCycle=0;
        }
        if(scan.isEmpty()&&now>=nextCycle){List<Cursor> chunks=new ArrayList<>();int radius=(range.getInt()+15)/16;
            for(int x=cx-radius;x<=cx+radius;x++)for(int z=cz-radius;z<=cz+radius;z++)if(world.isBlockLoaded(new BlockPos(x*16,cy,z*16)))chunks.add(new Cursor(x,z,Math.max(0,cy-verticalRange.getInt()),Math.min(255,cy+verticalRange.getInt())));
            chunks.sort(Comparator.comparingInt(c->(c.x-cx)*(c.x-cx)+(c.z-cz)*(c.z-cz)));scan.addAll(chunks);nextCycle=now+2000000000L;
        }
        long stop=now+2000000L;int budget=scanBudget.getInt();
        while(budget-->0&&!scan.isEmpty()){
            Cursor c=scan.peek();if(c.index>=256*(c.high-c.low+1)){scan.remove();continue;}
            int i=c.index++;BlockPos pos=new BlockPos(c.x*16+(i&15),c.low+(i>>8),c.z*16+(i>>4&15));
            if(pos.distanceSq(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ)<=range.getDouble()*range.getDouble()&&world.isBlockLoaded(pos)){
                Block block=world.getBlockState(pos).getBlock();if(selected(block)&&(found.size()<8192||found.containsKey(pos)))found.put(pos,block);else found.remove(pos);
            }
            if((budget&31)==0&&System.nanoTime()>=stop)break;
        }
    }
    public void clear(){found.clear();scan.clear();world=null;centerX=Integer.MIN_VALUE;nextCycle=0;}
    @Override protected void onDisable(){clear();}
    private static final class Cursor{final int x,z,low,high;int index;Cursor(int x,int z,int low,int high){this.x=x;this.z=z;this.low=low;this.high=high;}}
}
