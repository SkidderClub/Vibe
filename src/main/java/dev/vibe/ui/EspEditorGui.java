package dev.vibe.ui;

import com.mojang.authlib.GameProfile;
import dev.vibe.Vibe;
import dev.vibe.module.impl.*;
import dev.vibe.module.impl.Esp2DSettings.*;
import dev.vibe.setting.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.*;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** Responsive editor: controls and preview operate directly on the persisted ESP settings. */
public final class EspEditorGui extends GuiScreen {
    private static final int W=920,H=560,LIST_X=384,LIST_Y=83,LIST_W=518,LIST_BOTTOM=536;
    private final EspEditorModule module;
    private final GuiScreen parent;
    private boolean returning;
    private final Esp2DRenderer renderer=new Esp2DRenderer();
    private final Set<String> collapsed=new HashSet<String>();
    private final List<Row> rows=new ArrayList<Row>();
    private EspModule esp;
    private boolean initialized;
    private float uiScale=1,scroll,maxScroll,previewYaw;
    private int left,top,mouseX,mouseY,lastMouseX;
    private Element selected,dragged;
    private boolean resizing,rotating;
    private float resizeStart,resizeDistance;
    private NumberSetting slider;
    private int sliderX,sliderWidth;
    private Esp2DRenderer.Frame frame;
    private EspLayout.Rect box;
    private EntityOtherPlayerMP preview;
    private String previewName="Steve";
    private boolean previewOccluded,previewHurt,previewArmor;
    private EspPreviewRenderer previewRenderer;
    private static final int PREVIEW_TEAM=0xFF55AAFF;
    private ColorSetting activeColor;
    private GuiTextField hex;
    private float hue,saturation,brightness;
    private int pickerPart=-1;
    private static final int PICK_X=624,PICK_Y=144,PICK_W=268;

    public EspEditorGui(EspEditorModule module) { this(module,null); }
    public EspEditorGui(EspEditorModule module,GuiScreen parent) { this.module=module;this.parent=parent; }
    @Override public void initGui() {
        uiScale=Math.min(1,Math.min(width/(float)W,height/(float)H));
        left=Math.round((width/uiScale-W)/2);top=Math.round((height/uiScale-H)/2);
        esp=Vibe.getInstance().getModuleManager().getModule(EspModule.class);
        if(!initialized){for(Element e:settings().elements)collapsed.add(e.title);collapsed.add("Default text");initialized=true;}
        hex=new GuiTextField(0,fontRendererObj,left+PICK_X+14,top+PICK_Y+267,240,20);
        hex.setMaxStringLength(9);
    }
    private Esp2DSettings settings() {return esp.get2D(esp.editingProfile());}
    private boolean inherited(){return esp.profileDefaults(esp.editingProfile());}
    private boolean hit(float x,float y,float w,float h,int mx,int my) {return mx>=left+x&&mx<left+x+w&&my>=top+y&&my<top+y+h;}
    private void text(String value,float x,float y,int color) {fontRendererObj.drawString(value,left+x,top+y,color,false);}
    private void rect(float x,float y,float w,float h,int color) {Gui.drawRect((int)(left+x),(int)(top+y),(int)(left+x+w),(int)(top+y+h),color);}
    private void button(String label,int x,int y,int w,boolean on) {
        SkeetEditorStyle.button(left+x,top+y,left+x+w,top+y+23,label,on);
    }
    @Override public void drawScreen(int mx,int my,float partialTicks) {
        mouseX=(int)(mx/uiScale);mouseY=(int)(my/uiScale);
        SkeetEditorStyle.backdrop(this,BlurModule.ESP_EDITOR,partialTicks);
        GlStateManager.pushMatrix();GlStateManager.scale(uiScale,uiScale,1);
        try {
            SkeetEditorStyle.window(left,top+6,left+W,top+H,"VIBE / ESP EDITOR","Drag / resize / wheel to scale / middle-click to edit");

            button(esp.isEnabled()?"ESP ON":"ESP OFF",800,45,101,esp.isEnabled());
            String[] modes={"2D","3D","Skeletal","Chams"};
            for(int i=0;i<modes.length;i++)button(modes[i],18+i*91,45,82,esp.getModes().isSelected(modes[i]));
            String[] profiles={"Players","Friends","Targets"};
            for(int i=0;i<3;i++)button(profiles[i],400+i*128,45,119,esp.editingProfile()==i);
            drawPreview(partialTicks);
            buildRows();drawRows();
            if(activeColor!=null)drawPicker();
        } finally {GlStateManager.popMatrix();}
        super.drawScreen(mx,my,partialTicks);
    }
    private void drawPreview(float partialTicks) {
        SkeetEditorStyle.panel(left+18,top+83,left+368,top+536,"LIVE PREVIEW / "+esp.getEditProfile().getValue());
        if(esp.getModes().isSelected("2D")&&!inherited()) {
            int i=0;for(Element e:settings().elements){button(e.title,28+(i%3)*111,117+(i/3)*27,105,e.enabled.isEnabled());i++;}
        }
        if(inherited()){text("Using Players appearance",32,125,SkeetEditorStyle.TEXT);text("Disable 'Use player defaults' to customize.",32,145,SkeetEditorStyle.MUTED);}
        box=new EspLayout.Rect(left+157,top+250,83,163);
        scissor(18,207,350,265);
        drawPreviewPlayer((int)(box.x+box.w/2),(int)box.bottom());
        if(esp.getModes().isSelected("3D"))draw3DPreview();

        frame=null;
        if(esp.getModes().isSelected("2D")) {
            frame=renderer.draw(esp.get2D(esp.resolvedProfile(esp.editingProfile())),previewActor(),box,Math.round(width/uiScale),Math.round(height/uiScale),true);
            if(selected!=null && selected.enabled.isEnabled()) {
                EspLayout.Rect r=selected==settings().box?box:frame.elements.get(selected.title);
                if(r!=null){RenderUtils.tacticalCorners((int)r.x-2,(int)r.y-2,(int)r.right()+2,(int)r.bottom()+2,SkeetEditorStyle.accent(0),1,5);
                    Gui.drawRect((int)r.right()-2,(int)r.bottom()-2,(int)r.right()+4,(int)r.bottom()+4,SkeetEditorStyle.TEXT);}
            }
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        text(selected==null?"Select an element to see its resize handle":selected.title+"  /  "+String.format(Locale.ROOT,"%.2fx",selected.scale.getDouble()),32,478,SkeetEditorStyle.TEXT);
        button(previewOccluded?"Occluded":"Visible",28,504,105,previewOccluded);
        button(previewHurt?"Hurt":"Healthy",139,504,105,previewHurt);
        button("Armor",250,504,105,previewArmor);
        if(dragged!=null)text(resizing?"Resizing "+dragged.title:"Place "+dragged.title,mouseX-left+9,mouseY-top+12,0xFFFFFFFF);
    }
    private Esp2DRenderer.Actor previewActor() {
        Esp2DRenderer.Actor actor=new Esp2DRenderer.Actor(previewName,15,20,16,12,"Diamond Sword",new ItemStack(Items.diamond_sword),
                new ItemStack[]{new ItemStack(Items.diamond_helmet),new ItemStack(Items.diamond_chestplate),new ItemStack(Items.diamond_leggings),new ItemStack(Items.diamond_boots)},1);
        actor.teamColor=PREVIEW_TEAM;actor.hurt=previewHurt;actor.profile=esp.resolvedProfile(esp.editingProfile());return actor;
    }
    private void draw3DPreview() {
        int profile=esp.resolvedProfile(esp.editingProfile());boolean walls=esp.getThroughWalls(profile).isEnabled();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(box.x+box.w/2,box.bottom(),50);GlStateManager.scale(82,-82,82);GlStateManager.rotate(previewYaw,0,1,0);
            WorldRenderUtils.begin(walls);
            try {EspModule.Style style=esp.getPreviewStyle(esp.editingProfile(),PREVIEW_TEAM,previewHurt);WorldRenderUtils.box(new net.minecraft.util.AxisAlignedBB(-.36,-.1,-.36,.36,1.9,.36),style.getOutline(),style.getFill(),esp.getLineWidth(profile).getFloat());}
            finally {WorldRenderUtils.end(walls);}
        } finally {GlStateManager.popMatrix();GL11.glPopAttrib();GuiRenderState.prepare(false);}
    }
    private void buildRows() {
        rows.clear();
        if(esp.editingProfile()!=0)setting(esp.editingProfile()==1?esp.getFriendsProfile().getUsePlayerDefaults():esp.getTargetsProfile().getUsePlayerDefaults(),"Use player defaults");
        if(esp.getModes().isSelected("2D")) {
            header("2D / Layout",0);if(!collapsed.contains("2D / Layout")) {
                setting(settings().distanceScaling,"Distance scaling");setting(settings().gap,"Element gap");
                if(settings().usesGlobalGradient()){header("Global gradient",1);if(!collapsed.contains("Global gradient"))gradient(settings().global);}
                if(settings().usesDefaultText()){header("Default text",1);if(!collapsed.contains("Default text"))textRows(settings().text);}
                for(Element e:settings().elements)if(e.enabled.isEnabled()) {
                    header(e.title,1);if(!collapsed.contains(e.title))elementRows(e);
                }
            }
        }
        if(esp.getModes().isSelected("3D")) {
            header("3D / Boxes",0);if(!collapsed.contains("3D / Boxes")){
                setting(esp.getOutlineColor(),"Color");setting(esp.getFillColor(),"Fill");setting(esp.getLineWidth(esp.editingProfile()),"Line width");setting(esp.getThroughWalls(esp.editingProfile()),"Through walls");
                setting(esp.getPlayerColorMode(),"Player color mode");setting(esp.getColorFade(),"Color fade");
                setting(esp.getFadeStart(),"Fade start");setting(esp.getFadeEnd(),"Fade end");setting(esp.getFadeSpeed(),"Fade speed");
                if(esp.editingProfile()==1)profileRows("Friends / teams",esp.getFriendsProfile());
                if(esp.editingProfile()==2)profileRows("Targets",esp.getTargetsProfile());
            }
        }
        if(esp.getModes().isSelected("Skeletal")){
            header("Skeletal / Pose",0);if(!collapsed.contains("Skeletal / Pose")){ EspModule.SkeletalSettings s=esp.getSkeletal(esp.editingProfile());
                setting(s.getColor(),"Color");setting(s.getRainbow(),"Rainbow");setting(s.getLineWidth(),"Line width");setting(s.getOnlyTargets(),"Only targets");setting(s.getThroughWalls(),"Through walls");setting(s.getDepthBackplate(),"Depth backplate"); }
        }
        if(esp.getModes().isSelected("Chams")){
            header("Chams / Materials",0);if(!collapsed.contains("Chams / Materials")){
                chamsRows("Visible surfaces",esp.getChams(esp.editingProfile(),true));chamsRows("Occluded surfaces",esp.getChams(esp.editingProfile(),false));
            }
        }
        float y=0;for(Row r:rows){r.y=y;y+=r.height;}
        maxScroll=Math.max(0,y-(LIST_BOTTOM-LIST_Y));scroll=Math.max(0,Math.min(scroll,maxScroll));
    }
    private void profileRows(String title,EspModule.ProfileSettings p) {
        header(title,1);if(collapsed.contains(title))return;
        setting(p.getUsePlayerDefaults(),"Use player defaults");setting(p.getColorMode(),"Color mode");setting(p.getOverrideColor(),"Override color");
        setting(p.getOutline(),"Outline");setting(p.getFill(),"Fill");
    }
    private void chamsRows(String label,EspModule.ChamsSettings c) {
        header(label,1);if(collapsed.contains(label))return;
        setting(c.getArmor(),"Include armor");setting(c.getShowSkin(),"Show skin");setting(c.getMode(),"Material");setting(c.getColor(),"Color / opacity");
    }
    private void elementRows(Element e) {
        setting(e.scale,"Scale");setting(e.position,"Position");setting(e.order,"Stack order");setting(e.offset,"Along edge");
        setting(e.width,"Width");setting(e.backgroundEnabled,"Background");setting(e.background,"Background / opacity");setting(e.outline,"Outline");setting(e.outlineWidth,"Outline width");setting(e.outlineColor,"Outline color");
        setting(e.corners,"Corners only");setting(e.cornerLength,"Corner length");setting(e.cornerDistance,"Corners beyond (m)");setting(e.rounding,"Edge rounding");
        if(e.kind==Kind.TEXT){setting(e.useDefaultText,"Use default text");if(!e.useDefaultText.isEnabled())textRows(e.text);setting(e.metric,"Distance unit");}
        else if(e.kind==Kind.BOX||e.kind==Kind.BAR)paintRows(e.color);
    }
    private void textRows(TextStyle t){setting(t.font,"Font");setting(t.size,"Size");setting(t.shadow,"Shadow");paintRows(t.color);}
    private void paintRows(Paint p){setting(p.mode,"Color mode");setting(p.solid,"Color / opacity");if(p.mode.is("Custom Gradient"))gradient(p.gradient);setting(p.rainbowSpeed,"Rainbow speed");setting(p.rainbowSaturation,"Rainbow saturation");}
    private void header(String title,int level){rows.add(new Row(title,null,null,level,31));}
    private void setting(Setting<?> setting,String label){
        if(!setting.isVisible())return;rows.add(new Row(label,setting,null,2,27));
        if(setting instanceof ColorSetting){ColorSetting c=(ColorSetting)setting;if(c.getEntityMode()!=null){
            setting(c.getEntityMode(),label+" source");if(c.getHurtOverride()!=null){setting(c.getHurtOverride(),label+" on damage");setting(c.getHurtColor(),"Damage color");}}}
    }
    private void gradient(Gradient g){rows.add(new Row("",null,g,2,92));for(int i=0;i<g.count.getInt();i++)setting(g.colors.get(i),"Stop "+(i+1)+" color");setting(g.direction,"Direction (degrees)");setting(g.speed,"Speed (cycles / sec)");}
    private void drawRows() {
        SkeetEditorStyle.panel(left+LIST_X,top+LIST_Y,left+LIST_X+LIST_W,top+LIST_BOTTOM,null);
        scissor(LIST_X,LIST_Y,LIST_W,LIST_BOTTOM-LIST_Y);
        try {for(Row r:rows){float y=LIST_Y+r.y-scroll;if(y+r.height<LIST_Y||y>LIST_BOTTOM)continue;
            if(r.gradient!=null){drawGradient(r.gradient,y);continue;}
            if(r.setting==null){rect(LIST_X+5,y+3,LIST_W-16,25,r.level==0?0xFF202023:0xFF171719);text((collapsed.contains(r.title)?"+  ":"-  ")+r.title,LIST_X+14,y+11,SkeetEditorStyle.TEXT);continue;}
            text(r.title,LIST_X+16,y+9,SkeetEditorStyle.TEXT);int x=LIST_X+259,w=232;
            Setting<?> s=r.setting;
            if(s instanceof BooleanSetting){boolean on=((BooleanSetting)s).isEnabled();rect(x,y+6,w,17,on?SkeetEditorStyle.accent(0):SkeetEditorStyle.FIELD);text(on?"Enabled":"Disabled",x+8,y+10,0xFFFFFFFF);}
            else if(s instanceof ModeSetting){rect(x,y+5,w,19,SkeetEditorStyle.FIELD);text(((ModeSetting)s).getValue()+"  >",x+8,y+10,SkeetEditorStyle.TEXT);}
            else if(s instanceof ColorSetting){RenderUtils.transparencyGrid(left+x,top+(int)y+5,left+x+w,top+(int)y+23,4);rect(x,y+5,w,18,((ColorSetting)s).getArgb());}
            else if(s instanceof NumberSetting){NumberSetting n=(NumberSetting)s;float f=(float)((n.getDouble()-n.getMinimum())/(n.getMaximum()-n.getMinimum()));
                rect(x,y+21,w,2,SkeetEditorStyle.BORDER);rect(x,y+21,w*f,2,SkeetEditorStyle.accent(0));text(format(n.getDouble()),x+8,y+8,SkeetEditorStyle.TEXT);}
        }}finally{GL11.glDisable(GL11.GL_SCISSOR_TEST);}
        if(maxScroll>0){float view=LIST_BOTTOM-LIST_Y,thumb=Math.max(25,view*view/(view+maxScroll));rect(LIST_X+LIST_W-5,LIST_Y+(view-thumb)*scroll/maxScroll,3,thumb,SkeetEditorStyle.accent(0));}
    }
    private static String format(double value){return String.format(Locale.ROOT,"%.2f",value);}
    private void drawGradient(Gradient g,float y) {
        int x=LIST_X+16,w=475;EspGradient gradient=new EspGradient(g,0);
        for(int i=0;i<w;i++)rect(x+i,y+9,1,16,gradient.at(i/(float)(w-1)));
        for(int i=0;i<g.count.getInt();i++){
            int sx=x+Math.round(g.positions.get(i).getFloat()*w);
            rect(sx-5,y+29,10,13,activeColor==g.colors.get(i)?0xFFFFFFFF:SkeetEditorStyle.BORDER);rect(sx-3,y+31,6,9,g.colors.get(i).getArgb());
        }
        text("Click a stop to edit color. Drag to position.",x,y+51,SkeetEditorStyle.MUTED);
        text(g.count.getInt()+" color stops",x,y+74,SkeetEditorStyle.TEXT);
        rect(x+w-60,y+66,25,20,SkeetEditorStyle.FIELD);text("-",x+w-51,y+72,0xFFFFFFFF);
        rect(x+w-28,y+66,25,20,SkeetEditorStyle.FIELD);text("+",x+w-20,y+72,0xFFFFFFFF);
    }
    private void scissor(int x,int y,int w,int h){
        float sx=mc.displayWidth/(float)width*uiScale,sy=mc.displayHeight/(float)height*uiScale;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor((int)((left+x)*sx),(int)(mc.displayHeight-(top+y+h)*sy),(int)Math.ceil(w*sx),(int)Math.ceil(h*sy));
    }
    private void select(Element e){
        selected=e;collapsed.remove("2D / Layout");collapsed.remove(e.title);activeColor=null;buildRows();
        for(Row r:rows)if(r.setting==null&&r.title.equals(e.title)){scroll=Math.min(maxScroll,r.y);break;}
    }
    private Element hovered(int mx,int my){
        if(inherited()||frame==null||!hit(18,207,350,265,mx,my))return null;
        for(Element e:settings().elements){EspLayout.Rect r=frame.elements.get(e.title);if(r!=null&&r.expand(3).contains(mx,my))return e;}
        if(settings().box.enabled.isEnabled()&&box.expand(4).contains(mx,my)&&!new EspLayout.Rect(box.x+6,box.y+6,box.w-12,box.h-12).contains(mx,my))return settings().box;
        return null;
    }
    @Override protected void mouseClicked(int mx,int my,int button)throws IOException {
        mx=(int)(mx/uiScale);my=(int)(my/uiScale);
        if(activeColor!=null){pickerClick(mx,my,button);return;}
        if(button==0&&hit(800,45,101,23,mx,my)){esp.toggle();return;}
        if(button==0){
            String[] profiles={"Players","Friends","Targets"};
            for(int i=0;i<3;i++)if(hit(400+i*128,45,119,23,mx,my)){esp.getEditProfile().setValue(profiles[i]);scroll=0;selected=dragged=null;frame=null;return;}
            if(hit(28,504,105,23,mx,my)){previewOccluded=!previewOccluded;return;}
            if(hit(139,504,105,23,mx,my)){previewHurt=!previewHurt;return;}
            if(hit(250,504,105,23,mx,my)){previewArmor=!previewArmor;return;}
        }
        String[] modes={"2D","3D","Skeletal","Chams"};
        if(button==0)for(int i=0;i<4;i++)if(hit(18+i*91,45,82,23,mx,my)){esp.getModes().toggle(modes[i]);scroll=0;dragged=null;return;}
        if(esp.getModes().isSelected("2D")&&!inherited()){
            int i=0;for(Element e:settings().elements){if(button==0&&hit(28+(i%3)*111,117+(i/3)*27,105,23,mx,my)){e.enabled.toggle();if(e.enabled.isEnabled())select(e);return;}i++;}
            if(selected!=null&&frame!=null&&button==0){EspLayout.Rect r=selected==settings().box?box:frame.elements.get(selected.title);
                if(r!=null&&new EspLayout.Rect(r.right()-5,r.bottom()-5,12,12).contains(mx,my)){
                    dragged=selected;resizing=true;resizeStart=selected.scale.getFloat();resizeDistance=Math.max(8,(float)Math.hypot(mx-r.x,my-r.y));return;}}
            Element e=hovered(mx,my);if(e!=null){select(e);if(button==0&&e!=settings().box){dragged=e;resizing=false;}return;}
        }
        if(hit(LIST_X,LIST_Y,LIST_W,LIST_BOTTOM-LIST_Y,mx,my)){
            for(Row r:rows){float y=LIST_Y+r.y-scroll;if(my<top+y||my>=top+y+r.height)continue;
                if(r.gradient!=null){gradientClick(r.gradient,y,mx,my,button);return;}
                if(r.setting==null){if(!collapsed.add(r.title))collapsed.remove(r.title);return;}
                if(mx<left+LIST_X+250)return;
                Setting<?> s=r.setting;
                if(s instanceof BooleanSetting)((BooleanSetting)s).toggle();
                else if(s instanceof ModeSetting)((ModeSetting)s).cycle(button==1);
                else if(s instanceof ColorSetting)openColor((ColorSetting)s);
                else if(s instanceof NumberSetting){slider=(NumberSetting)s;sliderX=left+LIST_X+259;sliderWidth=232;updateSlider(mx);}
                return;
            }
        }
        if(button==0&&hit(18,207,350,265,mx,my)){rotating=true;lastMouseX=mx;}
    }
    private void gradientClick(Gradient g,float y,int mx,int my,int button){
        int x=left+LIST_X+16,w=475;
        if(my>=top+y+66){if(mx>=x+w-28){if(g.count.getInt()<8){
                int i=g.count.getInt();EspGradient sorted=new EspGradient(g,0);float largest=-1,position=.5F;
                for(int j=1;j<sorted.positions.length;j++){float gap=sorted.positions[j]-sorted.positions[j-1];if(gap>largest){largest=gap;position=(sorted.positions[j]+sorted.positions[j-1])/2;}}
                g.positions.get(i).setValue((double)position);g.count.increase();
            }}else if(mx>=x+w-60)g.count.decrease();return;}
        if(my<top+y+7||my>top+y+44)return;
        int closest=0;float distance=Float.MAX_VALUE;
        for(int i=0;i<g.count.getInt();i++){float d=Math.abs(mx-(x+g.positions.get(i).getFloat()*w));if(d<distance){distance=d;closest=i;}}
        if(button==1){openColor(g.colors.get(closest));return;}
        activeStop=g;stopIndex=closest;stopMouseX=mx;slider=g.positions.get(closest);sliderX=x;sliderWidth=w;
    }
    private Gradient activeStop;private int stopIndex,stopMouseX;
    private void updateSlider(int x){if(slider!=null)slider.setValue(slider.getMinimum()+Math.max(0,Math.min(1,(x-sliderX)/(double)sliderWidth))*(slider.getMaximum()-slider.getMinimum()));}
    @Override protected void mouseClickMove(int mx,int my,int button,long elapsed){
        mx=(int)(mx/uiScale);my=(int)(my/uiScale);
        if(activeColor!=null&&pickerPart>=0){updatePicker(mx,my);return;}
        if(slider!=null){updateSlider(mx);return;}
        if(rotating){previewYaw+=mx-lastMouseX;lastMouseX=mx;}
        if(dragged!=null){
            if(resizing){EspLayout.Rect r=dragged==settings().box?box:frame.elements.get(dragged.title);if(r!=null)dragged.scale.setValue((double)(resizeStart*Math.max(8,Math.hypot(mx-r.x,my-r.y))/resizeDistance));}
            else place(dragged,mx,my);
        }
    }
    private void place(Element e,int mx,int my){
        if(!hit(18,207,350,265,mx,my))return;
        String side=EspLayout.snap(box,mx,my,e.kind==Kind.BAR||e.kind==Kind.ARMOR);e.position.setValue(side);
        EspLayout.Rect current=frame.elements.get(e.title);
        float elementHeight=current==null?0:current.h;
        float offset=e.vertical()?my-box.y-elementHeight/2:mx-(box.x+box.w/2);
        if(side.endsWith("Down"))offset=my-box.bottom()+elementHeight/2;
        if(e.kind==Kind.BAR)offset=0;
        e.offset.setValue((double)(offset/(box.h/180f)));
        List<Element> stack=new ArrayList<Element>();for(Element other:settings().elements)if(other!=e&&other.enabled.isEnabled()&&other.position.is(side)&&other.kind!=Kind.BOX)stack.add(other);
        Collections.sort(stack,Comparator.comparingDouble(other -> other.order.getDouble()));
        float outward=outward(side,mx,my);int at=0;
        for(Element other:stack){EspLayout.Rect r=frame.elements.get(other.title);if(r!=null&&outward>outward(side,r.x+r.w/2,r.y+r.h/2))at++;}
        stack.add(at,e);for(int i=0;i<stack.size();i++)stack.get(i).order.setValue((double)i);
    }
    private float outward(String side,float x,float y){if(side.endsWith("Up"))return y-box.y;if(side.endsWith("Down"))return box.bottom()-y;if(side.startsWith("Left"))return box.x-x;if(side.startsWith("Right"))return x-box.right();return side.equals("Top")?box.y-y:y-box.bottom();}
    @Override protected void mouseReleased(int mx,int my,int state){
        mx=(int)(mx/uiScale);my=(int)(my/uiScale);
        if(dragged!=null&&!resizing)place(dragged,mx,my);
        if(activeStop!=null&&Math.abs(mx-stopMouseX)<3)openColor(activeStop.colors.get(stopIndex));
        activeStop=null;slider=null;dragged=null;rotating=false;resizing=false;pickerPart=-1;
    }
    @Override public void handleMouseInput()throws IOException {
        super.handleMouseInput();int wheel=Mouse.getEventDWheel();if(wheel==0||activeColor!=null)return;
        int mx=(int)(Mouse.getEventX()*width/(float)mc.displayWidth/uiScale),my=(int)((height-Mouse.getEventY()*height/(float)mc.displayHeight-1)/uiScale);
        Element e=dragged!=null?dragged:hovered(mx,my);
        if(e!=null){e.scale.setValue(e.scale.getDouble()+(wheel>0?.05:-.05));selected=e;return;}
        if(hit(LIST_X,LIST_Y,LIST_W,LIST_BOTTOM-LIST_Y,mx,my)){
            if(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)||Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))for(Row r:rows)if(r.setting instanceof NumberSetting&&my>=top+LIST_Y+r.y-scroll&&my<top+LIST_Y+r.y-scroll+r.height){NumberSetting n=(NumberSetting)r.setting;n.setValue(n.getDouble()+(wheel>0?n.getIncrement():-n.getIncrement()));return;}
            scroll=Math.max(0,Math.min(maxScroll,scroll+(wheel>0?-38:38)));
        }
    }
    private void openColor(ColorSetting color){activeColor=color;float[] hsv=java.awt.Color.RGBtoHSB(color.getRed(),color.getGreen(),color.getBlue(),null);hue=hsv[0];saturation=hsv[1];brightness=hsv[2];hex.setText(color.getHex());hex.setFocused(false);}
    private void drawPicker(){
        int x=PICK_X,y=PICK_Y;rect(x-3,y-3,PICK_W+6,309,0xFF09090F);rect(x,y,PICK_W,303,SkeetEditorStyle.WINDOW);
        text("COLOR / OPACITY",x+14,y+12,SkeetEditorStyle.TEXT);text("x",x+247,y+12,0xFFAAAAAA);
        int hueColor=java.awt.Color.HSBtoRGB(hue,1,1)|0xFF000000;
        gradientQuad(x+14,y+32,240,160,0xFFFFFFFF,hueColor,hueColor,0xFFFFFFFF);
        gradientQuad(x+14,y+32,240,160,0,0,0xFF000000,0xFF000000);
        RenderUtils.tacticalCorners(left+x+12+(int)(saturation*240),top+y+30+(int)((1-brightness)*160),left+x+18+(int)(saturation*240),top+y+36+(int)((1-brightness)*160),0xFFFFFFFF,1,3);
        for(int i=0;i<6;i++){int a=java.awt.Color.HSBtoRGB(i/6F,1,1),b=java.awt.Color.HSBtoRGB((i+1)/6F,1,1);gradientQuad(x+14+i*40,y+205,40,10,a,b,b,a);}
        rect(x+13+hue*240,y+203,2,14,0xFFFFFFFF);
        RenderUtils.transparencyGrid(left+x+14,top+y+229,left+x+254,top+y+239,4);
        int rgb=activeColor.getArgb()&0xFFFFFF;gradientQuad(x+14,y+229,240,10,rgb,rgb|0xFF000000,rgb|0xFF000000,rgb);
        rect(x+13+activeColor.getAlpha()/255F*240,y+227,2,14,0xFFFFFFFF);
        text("Alpha "+Math.round(activeColor.getAlpha()/255F*100)+"%",x+14,y+250,SkeetEditorStyle.TEXT);hex.drawTextBox();
    }
    private void gradientQuad(float x,float y,float w,float h,int tl,int tr,int br,int bl){
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_CURRENT_BIT|GL11.GL_LIGHTING_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(770,771);GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glBegin(GL11.GL_QUADS);
        Esp2DRenderer.color(tl);GL11.glVertex2f(left+x,top+y);Esp2DRenderer.color(tr);GL11.glVertex2f(left+x+w,top+y);
        Esp2DRenderer.color(br);GL11.glVertex2f(left+x+w,top+y+h);Esp2DRenderer.color(bl);GL11.glVertex2f(left+x,top+y+h);
        GL11.glEnd();GL11.glPopAttrib();
    }
    private void pickerClick(int mx,int my,int button){
        if(button==1||!hit(PICK_X,PICK_Y,PICK_W,303,mx,my)||hit(PICK_X+240,PICK_Y,28,27,mx,my)){activeColor=null;return;}
        hex.mouseClicked(mx,my,button);
        if(hit(PICK_X+14,PICK_Y+32,240,160,mx,my))pickerPart=0;
        else if(hit(PICK_X+14,PICK_Y+202,240,16,mx,my))pickerPart=1;
        else if(hit(PICK_X+14,PICK_Y+225,240,18,mx,my))pickerPart=2;
        if(pickerPart>=0)updatePicker(mx,my);
    }
    private void updatePicker(int mx,int my){
        float x=Math.max(0,Math.min(1,(mx-left-PICK_X-14)/240F));
        if(pickerPart==0){saturation=x;brightness=1-Math.max(0,Math.min(1,(my-top-PICK_Y-32)/160F));}
        else if(pickerPart==1)hue=x;
        int rgb=java.awt.Color.HSBtoRGB(hue,saturation,brightness);activeColor.setRgba(rgb>>16&255,rgb>>8&255,rgb&255,pickerPart==2?Math.round(x*255):activeColor.getAlpha());hex.setText(activeColor.getHex());
    }
    @Override protected void keyTyped(char ch,int key)throws IOException {
        if(key==Keyboard.KEY_ESCAPE){if(activeColor!=null){activeColor=null;return;}returning=true;mc.displayGuiScreen(parent);return;}
        if(activeColor!=null&&hex.textboxKeyTyped(ch,key)){if(activeColor.setHex(hex.getText())){float[] hsv=java.awt.Color.RGBtoHSB(activeColor.getRed(),activeColor.getGreen(),activeColor.getBlue(),null);hue=hsv[0];saturation=hsv[1];brightness=hsv[2];}return;}
        super.keyTyped(ch,key);
    }
    @Override public void onGuiClosed(){
        if(parent instanceof Gta7Gui && !returning)((Gta7Gui)parent).closeFromEditor();
        if(module.isEnabled())module.setEnabled(false);
        if(Vibe.getInstance().getConfig()!=null)Vibe.getInstance().getConfig().save(Vibe.getInstance().getModuleManager());
        super.onGuiClosed();
    }
    @Override public boolean doesGuiPauseGame(){return false;}
    private static final class Row {
        final String title;final Setting<?> setting;final Gradient gradient;final int level,height;float y;
        Row(String title,Setting<?> setting,Gradient gradient,int level,int height){this.title=title;this.setting=setting;this.gradient=gradient;this.level=level;this.height=height;}
    }
    private void drawPreviewPlayer(int x, int y) {
        if (preview == null || !previewName.equals(preview.getName())) preview = createPreview(previewName);
        if(previewRenderer==null)previewRenderer=new EspPreviewRenderer(mc.getRenderManager(),"slim".equals(preview.getSkinType()));
        previewRenderer.esp=esp;previewRenderer.profile=esp.editingProfile();previewRenderer.team=PREVIEW_TEAM;previewRenderer.hurt=previewHurt;previewRenderer.occluded=previewOccluded;
        ItemStack[] armor={new ItemStack(Items.diamond_boots),new ItemStack(Items.diamond_leggings),new ItemStack(Items.diamond_chestplate),new ItemStack(Items.diamond_helmet)};
        for(int i=0;i<4;i++)preview.inventory.armorInventory[i]=previewArmor?armor[i]:null;
        GuiRenderState.prepare(true);
        float oldOffset = preview.renderYawOffset, oldYaw = preview.rotationYaw, oldPitch = preview.rotationPitch,
                oldHead = preview.rotationYawHead, oldPrevOffset = preview.prevRenderYawOffset,
                oldPrevYaw = preview.prevRotationYaw, oldPrevPitch = preview.prevRotationPitch,
                oldPrevHead = preview.prevRotationYawHead;
        RenderManager manager = mc.getRenderManager();
        float oldView = manager.playerViewY;
        boolean oldShadow = manager.isRenderShadow();
        boolean oldEntityShadows = mc.gameSettings.entityShadows;
        org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_ENABLE_BIT
                | org.lwjgl.opengl.GL11.GL_LIGHTING_BIT | org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT
                | org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_CURRENT_BIT);
        GlStateManager.pushMatrix();
        try {
            // This is the same stable basis used by GuiInventory. The larger
            // scale intentionally fills the 2D ESP bounds instead of leaving
            // a tiny player inside a life-size preview box.
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableColorMaterial();
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.translate(x, y, 50.0F);
            GlStateManager.scale(-82.0F, 82.0F, 82.0F);
            GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);
            RenderHelper.enableStandardItemLighting();
            preview.renderYawOffset = previewYaw;
            preview.rotationYaw = previewYaw;
            preview.rotationPitch = 0.0F;
            preview.rotationYawHead = previewYaw;
            preview.prevRenderYawOffset = previewYaw;
            preview.prevRotationYaw = previewYaw;
            preview.prevRotationPitch = 0.0F;
            preview.prevRotationYawHead = previewYaw;
            manager.setPlayerViewY(180.0F);
            // RenderManager controls the normal shadow, while this option is
            // also checked by OptiFine's entity render path.  Disable both
            // for the editor-only model so an opaque floor ellipse can never
            // bleed into the ESP preview.
            mc.gameSettings.entityShadows = false;
            manager.setRenderShadow(false);
            // The entity fields above are the sole yaw input. Passing a yaw
            // here as well is what caused skins to mirror at some angles.
            ChamsRenderer.beginPreview();
            try {previewRenderer.doRender(preview,0,0,0,0,1);}finally{ChamsRenderer.endPreview();}
        } finally {
            manager.setRenderShadow(oldShadow);
            mc.gameSettings.entityShadows = oldEntityShadows;
            manager.setPlayerViewY(oldView);
            preview.renderYawOffset = oldOffset;
            preview.rotationYaw = oldYaw;
            preview.rotationPitch = oldPitch;
            preview.rotationYawHead = oldHead;
            preview.prevRenderYawOffset = oldPrevOffset;
            preview.prevRotationYaw = oldPrevYaw;
            preview.prevRotationPitch = oldPrevPitch;
            preview.prevRotationYawHead = oldPrevHead;
            RenderHelper.disableStandardItemLighting();
            GlStateManager.popMatrix();
            org.lwjgl.opengl.GL11.glPopAttrib();
            GuiRenderState.prepare(false);
        }
    }

    private EntityOtherPlayerMP createPreview(final String name) {
        EntityOtherPlayerMP player=new EntityOtherPlayerMP(mc.theWorld==null?new EspPreviewWorld():mc.theWorld,new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)),name)) {
            @Override public boolean isSpectator(){return false;}
            @Override protected net.minecraft.client.network.NetworkPlayerInfo getPlayerInfo(){return null;}
            @Override public ResourceLocation getLocationSkin(){return mc.thePlayer==null?DefaultPlayerSkin.getDefaultSkinLegacy():mc.thePlayer.getLocationSkin();}
            @Override public String getSkinType(){return mc.thePlayer==null?"default":mc.thePlayer.getSkinType();}
            @Override public ResourceLocation getLocationCape(){return null;}
        };
        player.getDataWatcher().updateObject(10,Byte.valueOf((byte)0x7F));
        player.inventory.mainInventory[0]=new ItemStack(Items.diamond_sword);
        return player;
    }
}
