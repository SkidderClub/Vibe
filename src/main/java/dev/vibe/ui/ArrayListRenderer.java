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
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

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
<<<<<<< HEAD
        // HUD editor scaling is independent from the typography scale.  Keep
        // both factors in the measured row geometry so a resized ArrayList
        // remains anchored and clickable exactly where it is painted.
        final float scale = s.scale.getFloat() * element.getScale();
=======
        final float scale = s.scale.getFloat();
>>>>>>> f485846c52ed7e011bd8968e6d4d06cf0ec60922
        float inset = Math.max(s.padding.getFloat(), Math.max(hud.getArrayOutline().isEnabled() ? s.outlineWidth.getFloat() + 1 : 1,
                s.rail.is("None") ? 0 : s.railWidth.getFloat() + 1));
        float maximum = 12;
        for (Row row : rows) {
            row.width = textWidth(row.name+row.suffix,s)*scale + 2*inset*scale;
            maximum = Math.max(maximum,row.width);
        }
        Comparator<Row> width = Comparator.comparingDouble((Row r) -> -r.width).thenComparing(r -> r.name);
        Collections.sort(rows,s.sorting.is("Width") ? width : s.sorting.is("Category")
                ? Comparator.comparingInt((Row r) -> r.category).thenComparing(width) : Comparator.comparing(r -> r.name));
        float textHeight = s.font.is("Minecraft") ? mc.fontRendererObj.FONT_HEIGHT : font(s).inkHeight("Agjpqy");
        float rh = Math.max(textHeight + 2 * (hud.getArrayOutline().isEnabled() ? s.outlineWidth.getFloat() + 1 : 1),s.rowHeight.getFloat())*scale;
        float gap = s.gap.getFloat()*scale;
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
        boolean blend=GL11.glIsEnabled(GL11.GL_BLEND), texture=GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST), alphaTest=GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        int alphaFunction=GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        float alphaReference=GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        int src=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dst=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_CURRENT_BIT|GL11.GL_LINE_BIT);
        try {
            GlStateManager.disableDepth();
            GlStateManager.enableBlend(); GlStateManager.tryBlendFuncSeparate(770,771,1,0);
            GlStateManager.alphaFunc(GL11.GL_GREATER,0.001f);
            // KawaseBlur captures and filters the complete framebuffer. Doing
            // that once per entry turned a twenty-row list into twenty full
            // screen blur passes. Blur the list silhouette as one region,
            // then paint the individual cards over it below.
            if(blurred){
                // Capture and filter once, then clip the shared blurred frame
                // to every visible card. This keeps blur inside each real
                // ArrayList background instead of filling the stair-step gap.
                KawaseBlur.prepareRoundedFrame(blur.getStrength().getInt(),0);
                for(Row row:rows)KawaseBlur.drawRoundedRegion((int)Math.floor(row.x),(int)Math.floor(row.y),
                        (int)Math.ceil(row.x+row.width),(int)Math.ceil(row.y+rh),s.radius.getFloat()*scale,blur.getStrength().getInt(),0);
            }
            if (!s.background.is("None")) for (Row row : rows)
                RenderUtils.roundedRect((int)row.x,(int)row.y,(int)Math.ceil(row.x+row.width),(int)Math.ceil(row.y+rh),
                        s.radius.getFloat()*scale,alpha(hud.getBackground().getArgb(),(row.color>>>24)/255F));
            if(s.glow.isEnabled() && hud.getArrayOutline().isEnabled()) drawOutlineGlow(rows,right,rh,gap,s);
            if(hud.getArrayOutline().isEnabled())outline(rows,right,rh,gap,s,hud);
            for(int i=0;i<rows.size();i++){
                Row r=rows.get(i);
                float rail=s.railWidth.getFloat()*scale;
                if(s.rail.is("Both")||s.rail.is("Outer"))rect(right?r.x+r.width-rail:r.x,r.y,right?r.x+r.width:r.x+rail,r.y+rh,r.color);
                if(s.rail.is("Both")||s.rail.is("Inner"))rect(right?r.x:r.x+r.width-rail,r.y,right?r.x+rail:r.x+r.width,r.y+rh,r.color);
                float textWidth=textWidth(r.name+r.suffix,s)*scale;
                float x=right?r.x+r.width-inset*scale-textWidth:r.x+inset*scale;
                float y=r.y+(rh-textHeight*scale)/2;
                if (!s.font.is("Minecraft")) y -= (font(s).inkTop("Agjpqy") - 1)*scale;
<<<<<<< HEAD
                drawText(r.name,x,y,s,hud,i,rows.size(),now,false,element.getScale());
                drawText(r.suffix,x+textWidth(r.name,s)*scale,y,s,hud,i,rows.size(),now,true,element.getScale());
=======
                drawText(r.name,x,y,s,hud,i,rows.size(),now,false);
                drawText(r.suffix,x+textWidth(r.name,s)*scale,y,s,hud,i,rows.size(),now,true);
>>>>>>> f485846c52ed7e011bd8968e6d4d06cf0ec60922
            }
        } finally {
            // Restore Minecraft's cache too; glPopAttrib alone only restores
            // driver state and leaves subsequent HUD elements out of sync.
            if(blend)GlStateManager.enableBlend();else GlStateManager.disableBlend();
            if(texture)GlStateManager.enableTexture2D();else GlStateManager.disableTexture2D();
            if(depth)GlStateManager.enableDepth();else GlStateManager.disableDepth();
            if(alphaTest)GlStateManager.enableAlpha();else GlStateManager.disableAlpha();
            GlStateManager.alphaFunc(alphaFunction,alphaReference);
            GlStateManager.tryBlendFuncSeparate(src,dst,srcAlpha,dstAlpha);
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
        // Vanilla treats alpha 0..3 as an unspecified alpha and makes it fully
        // opaque. A faint glow/faded row must never turn into solid copies.
        if((color>>>24)<(s.font.is("Minecraft")?4:1))return;
        if(s.font.is("Minecraft"))mc.fontRendererObj.drawString((s.bold.isEnabled()?"§l":"")+text,x,y,color,false);
        else font(s).draw(text,x,y-1,color);
    }
    private void drawText(String text,float x,float y,ArrayListSettings s,HudModule hud,int index,int count,long now,boolean suffix,float elementScale){
        if(text.isEmpty())return;
        GlStateManager.pushMatrix();
        try {
<<<<<<< HEAD
            GlStateManager.translate(x,y,0);GlStateManager.scale(s.scale.getFloat()*elementScale,s.scale.getFloat()*elementScale,1);
=======
            GlStateManager.translate(x,y,0);GlStateManager.scale(s.scale.getFloat(),s.scale.getFloat(),1);
>>>>>>> f485846c52ed7e011bd8968e6d4d06cf0ec60922
            boolean gradient = s.horizontal.isEnabled() && (!suffix || s.suffixAccent.isEnabled());
            float total=Math.max(1,textWidth(text,s));
            // Finish each effect for the whole label before painting glyphs.
            // Otherwise the next glyph's glow/shadow overwrites the previous one.
            for (int pass=0;pass<3;pass++) {
                if(pass==0&&!s.textGlow.isEnabled() || pass==1&&!s.shadow.isEnabled())continue;
                float cursor=0;
                for(int i=0;i<(gradient?text.length():1);i++){
                    String part=gradient?text.substring(i,i+1):text;
                    int c=suffix&&!s.suffixAccent.isEnabled()?alpha(s.suffixColor.getArgb(),fade(s,index,count))
                            :color(hud,index,count,gradient?cursor/total:0,now);
                    if(pass==0){
                        float r=Math.min(1.25f,s.textGlowRadius.getFloat()*.35f);int glow=alpha(c,s.textGlowStrength.getFloat()*.18f);
                        for(int j=0;j<4;j++){double a=j*Math.PI/2;text(part,cursor+(float)Math.cos(a)*r,(float)Math.sin(a)*r,glow,s);}
                    } else if(pass==1)text(part,cursor+1,1,(c&0xFF000000)|((c&0xFCFCFC)>>2),s);
                    else text(part,cursor,0,c,s);
                    cursor+=textWidth(part,s);
                }
            }
        }finally{GlStateManager.popMatrix();}
    }
    private void outline(List<Row> rows,boolean right,float rh,float gap,ArrayListSettings s,HudModule hud){
        float t=s.outlineWidth.getFloat()*s.scale.getFloat();Row first=rows.get(0),last=rows.get(rows.size()-1);
        if(s.outline.is("Rectangle")){
            float l=Float.MAX_VALUE,r=0;for(Row row:rows){l=Math.min(l,row.x);r=Math.max(r,row.x+row.width);}
            rect(l,first.y,r,first.y+t,border(first,s));rect(l,last.y+rh-t,r,last.y+rh,border(last,s));
            for(Row row:rows){float bottom=Math.min(last.y+rh,row.y+rh+gap);rect(l,row.y,l+t,bottom,border(row,s));rect(r-t,row.y,r,bottom,border(row,s));}return;
        }
        for(int i=0;i<rows.size();i++){
            Row r=rows.get(i);int c=border(r,s);
            if(s.outline.is("Rows")||gap>0){RenderUtils.roundedOutline((int)r.x,(int)r.y,(int)(r.x+r.width),(int)(r.y+rh),s.radius.getFloat()*s.scale.getFloat(),t,c);continue;}
            rect(r.x,r.y,r.x+t,r.y+rh,c);rect(r.x+r.width-t,r.y,r.x+r.width,r.y+rh,c);
            if(i==0)rect(r.x,r.y,r.x+r.width,r.y+t,c);
            if(i+1==rows.size())rect(r.x,r.y+rh-t,r.x+r.width,r.y+rh,c);
            else {Row next=rows.get(i+1);float a=right?r.x:r.x+r.width,b=right?next.x:next.x+next.width;rect(Math.min(a,b),r.y+rh-t,Math.max(a,b)+t,r.y+rh,c);}
        }
    }
    /**
     * Draw the glow ourselves instead of routing it through roundedOutline.
     * That helper restores the normal alpha blend function for every row,
     * which turned a large glow radius into a stack of hard, cyan outlines.
     */
    private void drawOutlineGlow(List<Row> rows,boolean right,float rh,float gap,ArrayListSettings s){
        float strength=Math.max(0.0F,Math.min(1.0F,s.glowStrength.getFloat()));
        float radius=Math.min(10.0F,Math.max(1.0F,s.glowRadius.getFloat()));
        int passes=Math.max(2,Math.min(4,(int)Math.ceil(radius*.4F)));
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_CURRENT_BIT|GL11.GL_LINE_BIT);
        try{
            // Normal alpha composition prevents bright colour channels from
            // accumulating into white when neighbouring stepped rows overlap.
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_LINE_SMOOTH);
            // Each pass is the real outer silhouette. The old implementation
            // expanded one rounded rectangle per row, leaving a stack of hard
            // boxes across the text instead of a glow around the ArrayList.
            for(int pass=passes;pass>=0;pass--){
                float progress=pass/(float)passes;
                GL11.glLineWidth(1.0F+radius*progress*.65F);
                float opacity=strength*.075F*(1.0F-progress)*(1.0F-progress);
                if(opacity<=.002F)continue;
                glowPath(rows,right,rh,gap,s,opacity);
            }
        }finally{GL11.glPopAttrib();}
    }
    private static void glowPath(List<Row> rows,boolean right,float rh,float gap,ArrayListSettings s,float opacity){
        if(rows.isEmpty())return;
        if(s.outline.is("Rectangle")){
            float left=Float.MAX_VALUE,rightEdge=-Float.MAX_VALUE;for(Row row:rows){left=Math.min(left,row.x);rightEdge=Math.max(rightEdge,row.x+row.width);}
            int color=alpha(border(rows.get(0),s),opacity);
            glowSegment(left,rows.get(0).y,rightEdge,rows.get(0).y,color);glowSegment(left,rows.get(rows.size()-1).y+rh,rightEdge,rows.get(rows.size()-1).y+rh,color);
            glowSegment(left,rows.get(0).y,left,rows.get(rows.size()-1).y+rh,color);glowSegment(rightEdge,rows.get(0).y,rightEdge,rows.get(rows.size()-1).y+rh,color);return;
        }
        for(int i=0;i<rows.size();i++){
            Row row=rows.get(i);int color=alpha(border(row,s),opacity);
            if(s.outline.is("Rows")||gap>0){
                glowSegment(row.x,row.y,row.x+row.width,row.y,color);glowSegment(row.x,row.y+rh,row.x+row.width,row.y+rh,color);
                glowSegment(row.x,row.y,row.x,row.y+rh,color);glowSegment(row.x+row.width,row.y,row.x+row.width,row.y+rh,color);continue;
            }
            glowSegment(row.x,row.y,row.x,row.y+rh,color);glowSegment(row.x+row.width,row.y,row.x+row.width,row.y+rh,color);
            if(i==0)glowSegment(row.x,row.y,row.x+row.width,row.y,color);
            if(i+1==rows.size())glowSegment(row.x,row.y+rh,row.x+row.width,row.y+rh,color);
            else {Row next=rows.get(i+1);float a=right?row.x:row.x+row.width,b=right?next.x:next.x+next.width;glowSegment(Math.min(a,b),row.y+rh,Math.max(a,b),row.y+rh,color);}
        }
    }
    private static void glowSegment(float x1,float y1,float x2,float y2,int color){
        if((color>>>24)==0)return;GlStateManager.color((color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F,(color>>>24)/255F);
        GL11.glBegin(GL11.GL_LINES);GL11.glVertex2f(x1,y1);GL11.glVertex2f(x2,y2);GL11.glEnd();
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
        hud.getArrayOutline().setEnabled(false);s.outlineAccent.setEnabled(true);s.outlineWidth.setValue(1d);
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
