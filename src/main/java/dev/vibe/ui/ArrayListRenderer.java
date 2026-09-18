package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.hud.ArrayListSettings;
import dev.vibe.hud.HudManager.HudElement;
import dev.vibe.module.Module;
import dev.vibe.module.impl.HudModule;
import dev.vibe.module.impl.BlurModule;
import dev.vibe.setting.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/** Independent measured rows: HUD anchoring, blur silhouette, typography and paint share one layout. */
public final class ArrayListRenderer {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final NeverLoseFont smooth = new NeverLoseFont(java.awt.Font.PLAIN, 18);
    private final NeverLoseFont bold = new NeverLoseFont(java.awt.Font.BOLD, 18);

    public void draw(HudModule hud, HudElement element, ScaledResolution screen, boolean preview) {
        applyPreset(hud);
        ArrayListSettings s = hud.array;
        hud.synchronizeArrayListModules();
        List<Row> rows = new ArrayList<>();
        for (Module module : Vibe.getInstance().getModuleManager().getModules()) {
            if (module.isEnabled() && hud.getArrayListModules().isSelected(module.getRawName())) {
                rows.add(new Row(name(module.getName(), s), suffix(module,s), module.getCategory().ordinal()));
            }
        }
        if (preview && rows.isEmpty()) {
            rows.add(new Row(name("Inventory Manager",s), decorate("Normal",s),0));
            rows.add(new Row(name("Custom Cosmetics",s), "",0));
            rows.add(new Row(name("Sprint",s), decorate("Legit",s),0));
        }
        final float scale = s.scale.getFloat();
        float maximum = 12;
        for (Row row : rows) {
            row.width = textWidth(row.name+row.suffix,s)*scale + 2*s.padding.getFloat()*scale;
            maximum = Math.max(maximum,row.width);
        }
        Comparator<Row> width = Comparator.comparingDouble((Row r) -> -r.width).thenComparing(r -> r.name);
        Collections.sort(rows,s.sorting.is("Width") ? width : s.sorting.is("Category")
                ? Comparator.comparingInt((Row r) -> r.category).thenComparing(width) : Comparator.comparing(r -> r.name));
        float rh = Math.max(9,s.rowHeight.getFloat())*scale, gap = s.gap.getFloat()*scale;
        int maxRows = Math.max(1,(int)(screen.getScaledHeight()/(rh+gap)));
        if(rows.size()>maxRows)rows=new ArrayList<>(rows.subList(0,maxRows));
        int w = (int)Math.ceil(maximum), h = (int)Math.ceil(Math.max(1,rows.size()*(rh+gap)-gap));
        element.ensureOnScreen(screen,w,h);
        int left = element.left(screen,w);
        boolean right = s.alignment.is("Right") || s.alignment.is("Auto") && left+w/2 >= screen.getScaledWidth()/2;
        element.setRightAnchored(right,screen,w);
        left = element.left(screen,w);
        int top = element.getY();
        element.setBounds(left,top,w,rows.isEmpty()?0:h);
        if(rows.isEmpty() || !preview && DebugOverlay.overlaps(left-12,top-12,left+w+12,top+h+12))return;
        BlurModule blur = Vibe.getInstance().getModuleManager().getModule(BlurModule.class);
        boolean blurred = blur!=null && blur.isEnabled() && blur.getElements().isSelected(BlurModule.ARRAY_LIST);
        long now=System.currentTimeMillis();
        for(int i=0;i<rows.size();i++){
            Row row=rows.get(i); row.x=right?left+w-row.width:left; row.y=top+i*(rh+gap);
            if(s.background.is("Rectangle")){row.x=left;row.width=w;}
            row.color=color(hud,i,rows.size(),0,now);
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_CURRENT_BIT|GL11.GL_LINE_BIT);
        try {
            GlStateManager.enableBlend(); GlStateManager.tryBlendFuncSeparate(770,771,1,0);
            GlStateManager.alphaFunc(GL11.GL_GREATER,0.001f);
            // All blur samples happen before any list paint, avoiding repeated blur of previous rows.
            if(blurred)for(Row row:rows)KawaseBlur.drawRoundedRegion((int)row.x,(int)row.y,(int)Math.ceil(row.x+row.width),
                    (int)Math.ceil(row.y+rh),s.radius.getFloat()*scale,blur.getStrength().getInt(),0);
            if(s.glow.isEnabled())for(int i=0;i<rows.size();i++){
                Row r=rows.get(i);int radius=s.glowRadius.getInt();
                for(int pass=radius;pass>=1;pass--)RenderUtils.roundedOutline((int)r.x-pass,(int)r.y-pass,
                        (int)Math.ceil(r.x+r.width)+pass,(int)Math.ceil(r.y+rh)+pass,s.radius.getFloat()+pass,1,
                        alpha(r.color,s.glowStrength.getFloat()*(1-pass/(float)(radius+1))*.28f));
            }
            for(int i=0;i<rows.size();i++){
                Row r=rows.get(i);float fade=fade(s,i,rows.size());
                if(!s.background.is("None") && !hud.getArrayStyle().is("Minimal"))RenderUtils.roundedRect((int)r.x,(int)r.y,
                        (int)Math.ceil(r.x+r.width),(int)Math.ceil(r.y+rh),s.radius.getFloat()*scale,alpha(hud.getBackground().getArgb(),fade));
                float rail=s.railWidth.getFloat();
                if(s.rail.is("Both")||s.rail.is("Outer"))rect(right?r.x+r.width-rail:r.x,r.y,right?r.x+r.width:r.x+rail,r.y+rh,r.color);
                if(s.rail.is("Both")||s.rail.is("Inner"))rect(right?r.x:r.x+r.width-rail,r.y,right?r.x+rail:r.x+r.width,r.y+rh,r.color);
                float textWidth=textWidth(r.name+r.suffix,s)*scale;
                float x=right?r.x+r.width-s.padding.getFloat()*scale-textWidth:r.x+s.padding.getFloat()*scale;
                float y=r.y+(rh-9*scale)/2;
                drawText(r.name,x,y,s,hud,i,rows.size(),now,false);
                drawText(r.suffix,x+textWidth(r.name,s)*scale,y,s,hud,i,rows.size(),now,true);
            }
            if(hud.getArrayOutline().isEnabled())outline(rows,right,rh,gap,s,hud);
        } finally {
            GL11.glPopAttrib();GlStateManager.color(1,1,1,1);
        }
    }

    public static String name(String name,ArrayListSettings s){
        String result=s.spaces.isEnabled()?name.replaceAll("([a-z])([A-Z])","$1 $2"):name.replace(" ","");
        return s.casing.is("Lowercase")?result.toLowerCase(Locale.ROOT):s.casing.is("Uppercase")?result.toUpperCase(Locale.ROOT):result;
    }
    public static String suffix(Module module,ArrayListSettings s){
        if(s.suffix.is("None"))return "";
        List<String> values=new ArrayList<>();
        for(Setting<?> setting:module.getSettings())if(setting instanceof ModeSetting && setting.isVisible()){
            if(s.suffix.is("All Modes"))values.add(((ModeSetting)setting).getValue());
            else if(setting.getRawName().equalsIgnoreCase("Mode")){values.clear();values.add(((ModeSetting)setting).getValue());break;}
        }
        if(values.isEmpty()&&s.suffix.is("Mode"))for(Setting<?> setting:module.getSettings())if(setting instanceof ModeSetting&&setting.isVisible()){
            values.add(((ModeSetting)setting).getValue());break;
        }
        return values.isEmpty()?"":decorate(String.join(" / ",values),s);
    }
    private static String decorate(String value,ArrayListSettings s){
        if(s.suffix.is("None"))return "";
        if(s.casing.is("Lowercase"))value=value.toLowerCase(Locale.ROOT);
        if(s.casing.is("Uppercase"))value=value.toUpperCase(Locale.ROOT);
        return s.separator.is("Dash")?" - "+value:s.separator.is("Brackets")?" ["+value+"]":s.separator.is("Parentheses")?" ("+value+")":" "+value;
    }
    private NeverLoseFont font(ArrayListSettings s){return s.font.is("Smooth Bold")?bold:smooth;}
    private float textWidth(String text,ArrayListSettings s){return s.font.is("Minecraft")?mc.fontRendererObj.getStringWidth((s.bold.isEnabled()?"§l":"")+text):font(s).width(text);}
    private void text(String text,float x,float y,int color,ArrayListSettings s){
        if((color>>>24)<2)return;
        if(s.font.is("Minecraft"))mc.fontRendererObj.drawString((s.bold.isEnabled()?"§l":"")+text,x,y,color,false);
        else font(s).draw(text,x,y-1,color);
    }
    private void drawText(String text,float x,float y,ArrayListSettings s,HudModule hud,int index,int count,long now,boolean suffix){
        if(text.isEmpty())return;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x,y,0);GlStateManager.scale(s.scale.getFloat(),s.scale.getFloat(),1);
            float cursor=0,total=Math.max(1,textWidth(text,s));
            for(int i=0;i<text.length();i++){
                String ch=text.substring(i,i+1);
                int c=suffix&&!s.suffixAccent.isEnabled()?alpha(s.suffixColor.getArgb(),fade(s,index,count))
                        :color(hud,index,count,s.horizontal.isEnabled()?cursor/total:0,now);
                if(s.textGlow.isEnabled()){
                    float r=s.textGlowRadius.getFloat();int glow=alpha(c,s.textGlowStrength.getFloat()*.3f);
                    for(int j=0;j<8;j++){double a=j*Math.PI/4;text(ch,cursor+(float)Math.cos(a)*r,(float)Math.sin(a)*r,glow,s);}
                }
                if(s.shadow.isEnabled())text(ch,cursor+1,1,(c&0xFF000000)|((c&0xFCFCFC)>>2),s);
                text(ch,cursor,0,c,s);cursor+=textWidth(ch,s);
            }
        }finally{GlStateManager.popMatrix();}
    }
    private void outline(List<Row> rows,boolean right,float rh,float gap,ArrayListSettings s,HudModule hud){
        float t=s.outlineWidth.getFloat();Row first=rows.get(0),last=rows.get(rows.size()-1);
        if(s.outline.is("Rectangle")){
            float l=Float.MAX_VALUE,r=0;for(Row row:rows){l=Math.min(l,row.x);r=Math.max(r,row.x+row.width);}
            rect(l,first.y,r,first.y+t,border(first,s));rect(l,last.y+rh-t,r,last.y+rh,border(last,s));
            for(Row row:rows){rect(l,row.y,l+t,row.y+rh+gap,border(row,s));rect(r-t,row.y,r,row.y+rh+gap,border(row,s));}return;
        }
        for(int i=0;i<rows.size();i++){
            Row r=rows.get(i);int c=border(r,s);
            if(s.outline.is("Rows")||gap>0){RenderUtils.roundedOutline((int)r.x,(int)r.y,(int)(r.x+r.width),(int)(r.y+rh),s.radius.getFloat(),t,c);continue;}
            rect(r.x,r.y,r.x+t,r.y+rh,c);rect(r.x+r.width-t,r.y,r.x+r.width,r.y+rh,c);
            if(i==0)rect(r.x,r.y,r.x+r.width,r.y+t,c);
            if(i+1==rows.size())rect(r.x,r.y+rh-t,r.x+r.width,r.y+rh,c);
            else {Row next=rows.get(i+1);float a=right?r.x:r.x+r.width,b=right?next.x:next.x+next.width;rect(Math.min(a,b),r.y+rh-t,Math.max(a,b)+t,r.y+rh,c);}
        }
    }
    private static int border(Row row,ArrayListSettings s){return s.outlineAccent.isEnabled()?row.color:alpha(s.outlineColor.getArgb(),(row.color>>>24)/255f);}
    private static float fade(ArrayListSettings s,int row,int count){return s.opacity.getFloat()*(1-s.fade.getFloat()*row/Math.max(1f,count-1));}
    public static int color(HudModule hud,int row,int count,float x,long time){
        ArrayListSettings s=hud.array;float phase=row/Math.max(1f,count-1)*s.spread.getFloat()+x;
        float clock=(time%1000000L)/4000f*s.speed.getFloat(),t=0;
        int first=hud.getArrayPrimaryColor().getArgb(),second=hud.getArraySecondaryColor().getArgb(),c;
        if(s.color.is("Rainbow"))c=java.awt.Color.HSBtoRGB((phase*.2f+clock)%1,s.saturation.getFloat(),s.brightness.getFloat());
        else {if(s.color.is("Gradient"))t=Math.min(1,phase);else if(s.color.is("Wave")||s.color.is("Fade"))t=(float)(Math.sin((phase-clock)*Math.PI*2)*.5+.5);
            if(s.color.is("Fade"))second=(first&0xFF000000)|((first&0xFCFCFC)>>2);
            c=RenderUtils.blend(first,second,t);}
        return alpha(c,fade(s,row,count));
    }
    private static int alpha(int color,float opacity){return (Math.max(0,Math.min(255,Math.round((color>>>24)*opacity)))<<24)|(color&0xFFFFFF);}
    private static void rect(float l,float t,float r,float b,int color){
        if(r<=l||b<=t||(color>>>24)==0)return;
        GlStateManager.disableTexture2D();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
        GlStateManager.color((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,(color>>>24)/255f);
        GL11.glBegin(GL11.GL_QUADS);GL11.glVertex2f(l,b);GL11.glVertex2f(r,b);GL11.glVertex2f(r,t);GL11.glVertex2f(l,t);GL11.glEnd();
        GlStateManager.enableTexture2D();GlStateManager.color(1,1,1,1);
    }
    public static void applyPreset(HudModule hud){
        ArrayListSettings s=hud.array;if(s.preset.is("Custom"))return;String preset=s.preset.getValue();
        s.font.setValue("Smooth");s.scale.setValue(1d);s.casing.setValue("Original");s.spaces.setEnabled(true);s.bold.setEnabled(false);
        s.shadow.setEnabled(true);s.suffix.setValue("Mode");s.separator.setValue("Space");s.suffixAccent.setEnabled(false);s.suffixColor.setValue(0xFFCCCCCC);
        s.sorting.setValue("Width");s.alignment.setValue("Auto");s.rowHeight.setValue(12d);s.padding.setValue(4d);s.gap.setValue(0d);
        s.radius.setValue(0d);s.background.setValue("Steps");s.rail.setValue("None");s.railWidth.setValue(1d);s.outline.setValue("Steps");
        hud.getArrayStyle().setValue("Compact");hud.getArrayOutline().setEnabled(false);s.outlineAccent.setEnabled(true);s.outlineWidth.setValue(1d);
        s.color.setValue("Wave");s.horizontal.setEnabled(false);s.fade.setValue(0d);s.opacity.setValue(1d);s.glow.setEnabled(false);s.textGlow.setEnabled(false);
        s.speed.setValue(1d);s.spread.setValue(1d);hud.getBackground().setValue(0xC0101117);
        if(preset.equals("Rose Cards")){s.casing.setValue("Lowercase");hud.getArrayPrimaryColor().setValue(0xFFFF969D);hud.getArraySecondaryColor().setValue(0xFFA54E60);}
        else if(preset.equals("Purple Rail")){s.background.setValue("None");s.rail.setValue("Outer");s.separator.setValue("Dash");s.horizontal.setEnabled(true);s.suffixAccent.setEnabled(true);s.textGlow.setEnabled(true);hud.getArrayPrimaryColor().setValue(0xFF6730FF);hud.getArraySecondaryColor().setValue(0xFFCDC0ED);}
        else if(preset.equals("Rounded White")){s.suffix.setValue("None");s.radius.setValue(3d);s.color.setValue("Static");hud.getArrayPrimaryColor().setValue(0xFFFFFFFF);hud.getBackground().setValue(0xEE16171C);}
        else if(preset.equals("Green Fade")){s.font.setValue("Smooth Bold");s.spaces.setEnabled(false);s.background.setValue("None");s.rail.setValue("Outer");s.railWidth.setValue(2d);s.fade.setValue(.75);s.color.setValue("Gradient");hud.getArrayPrimaryColor().setValue(0xFF26FF69);hud.getArraySecondaryColor().setValue(0xFF006B3E);}
        else if(preset.equals("Pixel Outline")){s.font.setValue("Minecraft");s.spaces.setEnabled(false);s.rowHeight.setValue(10d);hud.getArrayOutline().setEnabled(true);s.rail.setValue("Outer");s.glow.setEnabled(true);hud.getArrayPrimaryColor().setValue(0xFFFF85D5);hud.getArraySecondaryColor().setValue(0xFF67D8F2);}
        else if(preset.equals("Dark Teal")){s.scale.setValue(.8);s.fade.setValue(.7);hud.getArrayPrimaryColor().setValue(0xFF008A96);hud.getArraySecondaryColor().setValue(0xFF00404C);hud.getBackground().setValue(0x8C121B20);}
        s.preset.setValue("Custom");
        if(Vibe.getInstance()!=null&&Vibe.getInstance().getConfig()!=null)Vibe.getInstance().getConfig().save(Vibe.getInstance().getModuleManager());
    }
    private static final class Row {
        final String name,suffix;final int category;float width,x,y;int color;
        Row(String name,String suffix,int category){this.name=name;this.suffix=suffix;this.category=category;}
    }
}
