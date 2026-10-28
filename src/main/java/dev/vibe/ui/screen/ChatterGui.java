package dev.vibe.ui.screen;

import dev.vibe.chatter.*;
import dev.vibe.chatter.ChatterData.*;
import dev.vibe.module.impl.client.ChatterModule;
import java.io.IOException;
import java.util.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import static dev.vibe.ui.screen.SkeetEditorStyle.*;

/** Skeet workspace with rule drafts, a match preview and a separate private password manager. */
public final class ChatterGui extends GuiScreen {
    private enum Page { RULES, PASSWORDS, GENERATION }
    private final ChatterModule module;
    private final ChatterEngine engine;
    private final GuiScreen parent;
    private final Map<String,GuiTextField> fields=new LinkedHashMap<>();
    private final List<Hit> hits=new ArrayList<>();
    private Page page=Page.RULES;
    private Rule draft;
    private Password password;
    private String passwordKey,confirmation="",notice="";
    private String insertionField="message";
    private Generation generation;
    private boolean perServer,reveal,error;
    private int left,top,scroll;
    private int pointerX,pointerY;
    private float scale=1;
    private static final int WIDTH=704,HEIGHT=438;
    public ChatterGui(ChatterModule module,GuiScreen parent){this.module=module;this.parent=parent;engine=module.engine();}
    @Override public void initGui(){
        Map<String,String> previous=new HashMap<>();for(Map.Entry<String,GuiTextField> e:fields.entrySet())previous.put(e.getKey(),e.getValue().getText());
        scale=Math.min(1F,Math.min(width/720F,height/458F));left=(Math.round(width/scale)-WIDTH)/2;top=Math.max(12,(Math.round(height/scale)-HEIGHT)/2);
        if(draft==null)draft=engine.data().rules.isEmpty()?new Rule():engine.data().rules.get(0).copy();
        if(generation==null){generation=engine.data().generation.copy();perServer=engine.data().perServer;}
        layout();for(Map.Entry<String,String> e:previous.entrySet())if(fields.containsKey(e.getKey()))put(e.getKey(),e.getValue());
        Keyboard.enableRepeatEvents(true);
    }
    private void layout(){
        fields.clear();reveal=false;confirmation="";
        if(page==Page.RULES){
            input("trigger",252,94,428,512,false);input("message",252,176,428,512,false);
            input("min",252,222,96,5,false);input("max",362,222,96,5,false);input("cooldown",472,222,208,6,false);input("test",252,325,428,512,false);fillRule();
        }else if(page==Page.PASSWORDS){
            input("search",22,98,196,96,false);input("name",252,105,428,16,false);input("server",252,157,428,255,false);input("password",252,209,310,64,true);fillPassword();
        }else{input("length",252,104,120,2,false);input("letters",252,281,196,2,false);input("digits",474,281,206,2,false);fillGeneration();}
    }
    private void input(String id,int x,int y,int width,int limit,boolean secret){
        GuiTextField value=secret?new SecretField(fields.size(),left+x+5,top+y+6,width-10,10):new GuiTextField(fields.size(),fontRendererObj,left+x+5,top+y+6,width-10,10);
        value.setMaxStringLength(limit);value.setEnableBackgroundDrawing(false);value.setTextColor(TEXT);fields.put(id,value);
    }
    private GuiTextField field(String id){return fields.get(id);}private String text(String id){return field(id).getText();}private void put(String id,Object text){field(id).setText(String.valueOf(text));}
    private void fillRule(){put("trigger",draft.trigger);put("message",draft.message);put("min",draft.minimum);put("max",draft.maximum);put("cooldown",draft.cooldown);}
    private void fillPassword(){put("name",password==null?"":password.name);put("server",password==null?"":password.server);put("password",password==null?"":password.value);}
    private void fillGeneration(){put("length",generation.length);put("letters",generation.lettersLength);put("digits",generation.digitsLength);}

    @Override public void drawScreen(int mouseX,int mouseY,float partial){
        backdrop(this,"Chatter",partial);hits.clear();int mx=Math.round(mouseX/scale),my=Math.round(mouseY/scale);
        pointerX=mx;pointerY=my;
        GlStateManager.pushMatrix();GlStateManager.scale(scale,scale,1);
        try{
            window(left,top,left+WIDTH,top+HEIGHT,"Chatter","");label("Chat, on your terms.",12,32,MUTED);
            button(464,28,148,22,module.isEnabled()?"Chatter: enabled":"Chatter: disabled",module.isEnabled(),module::toggle);
            button(626,0,28,18,"",page!=Page.RULES,()->navigate(page==Page.RULES?Page.PASSWORDS:Page.RULES));gear(left+640,top+9);
            button(664,0,28,18,"x",false,this::close);
            if(page==Page.RULES)rules(mx,my);else{
                button(12,62,216,24,"< Message rules",false,()->navigate(Page.RULES));
                button(242,62,214,24,"Saved passwords",page==Page.PASSWORDS,()->navigate(Page.PASSWORDS));
                button(464,62,228,24,"Generation",page==Page.GENERATION,()->navigate(Page.GENERATION));
                if(page==Page.PASSWORDS)passwords(mx,my);else generation();
            }
            Gui.drawRect(left+12,top+410,left+692,top+411,BORDER);
            String footer=!engine.available()?engine.status():notice.isEmpty()?engine.status()+"   |   "+engine.pending()+" queued":notice;
            label(trim(footer,668),18,420,error||!engine.available()?0xFFFF8B90:MUTED);
        }finally{GlStateManager.popMatrix();}
    }
    private void rules(int mx,int my){
        panel(left+12,top+62,left+230,top+400,"Message rules");panel(left+242,top+62,left+692,top+400,"Rule details");
        List<Rule> values=engine.data().rules;scroll=Math.max(0,Math.min(scroll,Math.max(0,values.size()-5)));int y=86;
        for(int i=scroll;i<values.size()&&y+48<=352;i++,y+=52){Rule value=values.get(i);
            row(left+20,top+y,left+222,top+y+48,value.id.equals(draft.id),hover(mx,my,20,y,202,48));
            check(28,y+8,value.enabled,()->{if(clean()){boolean old=value.enabled;value.enabled=!old;if(!persist())value.enabled=old;draft=value.copy();fillRule();}});
            label(trim(value.trigger,160),46,y+9,TEXT);label((value.action==Action.MESSAGE?"Message":"Leave server")+"  "+value.minimum+" - "+value.maximum+" ms",28,y+29,MUTED);
            hit(44,y,178,48,()->{if(clean()){draft=value.copy();fillRule();notice="";}});
        }
        scrollbar(225,86,256,values.size(),5);
        button(22,358,92,22,"Add rule",false,()->{if(clean()&&values.size()<64){draft=new Rule();fillRule();say("New rule: enter a trigger and action.",false);}});
        button(122,358,96,22,"Duplicate",false,()->{if(clean()&&values.size()<64){draft=draft.copy();draft.id=UUID.randomUUID().toString();fillRule();say("Duplicated rule. Save to add it.",false);}});
        label(values.size()+" of 64 rules",24,387,MUTED);
        label("When chat contains",252,82,TEXT);drawField("trigger",252,94,428);label("* matches any text. The ? character is literal.",252,119,MUTED);
        label("Action",252,139,MUTED);button(302,134,132,22,"Message",draft.action==Action.MESSAGE,()->draft.action=Action.MESSAGE);button(442,134,132,22,"Leave server",draft.action==Action.LEAVE,()->draft.action=Action.LEAVE);
        label(draft.action==Action.MESSAGE?"Message to send":"Message is unused for this action",252,164,MUTED);field("message").setEnabled(draft.action==Action.MESSAGE);drawField("message",252,176,428);
        label("Min delay (ms)",252,210,MUTED);label("Max delay (ms)",362,210,MUTED);label("Cooldown (ms)",472,210,MUTED);
        drawField("min",252,222,96);drawField("max",362,222,96);drawField("cooldown",472,222,208);
        check(252,255,draft.oncePerConnection,()->draft.oncePerConnection=!draft.oncePerConnection);label("Once per connection",270,256,TEXT);check(514,255,draft.enabled,()->draft.enabled=!draft.enabled);label("Rule enabled",532,256,TEXT);
        label("Insert a placeholder",252,280,MUTED);int x=252;
        for(String token:new String[]{"%name%","%password%","%pass%","%s","%n"}){int w=fontRendererObj.getStringWidth(token)+16;button(x,292,w,20,token,false,()->insert(token));x+=w+6;}
        label("Try an incoming chat line",252,314,MUTED);drawField("test",252,325,428);
        boolean match=!text("test").isEmpty()&&ChatterEngine.matches(text("trigger"),text("test"),currentName());
        label(text("test").isEmpty()?"Preview only. This never sends a message.":match?"Match: the selected action would run.":"No match: check text and wildcard order.",252,352,match?0xFFB0D581:MUTED);
        button(252,372,96,22,"Save rule",false,this::saveRule);button(356,372,84,22,"Discard",false,this::discard);
        button(448,372,124,22,"Rearm rules",false,()->{engine.cancel();say("One-time rules can run again on the next matching chat line.",false);});
        button(580,372,100,22,confirmation.equals(draft.id)?"Confirm delete":"Delete",false,this::deleteRule);
    }
    private List<Password> visiblePasswords(){List<Password> values=new ArrayList<>();String needle=text("search").toLowerCase(Locale.ROOT);for(Password p:engine.data().passwords)if((p.name+" "+p.server).toLowerCase(Locale.ROOT).contains(needle))values.add(p);values.sort(Comparator.comparing((Password p)->p.name.toLowerCase(Locale.ROOT)).thenComparing(p->p.server));return values;}
    private void passwords(int mx,int my){
        panel(left+12,top+92,left+230,top+400,"");drawField("search",22,98,196);if(text("search").isEmpty()&&!field("search").isFocused())label("Search accounts...",29,104,MUTED);
        List<Password> values=visiblePasswords();scroll=Math.max(0,Math.min(scroll,Math.max(0,values.size()-4)));int y=132;
        for(int i=scroll;i<values.size()&&y+46<=338;i++,y+=50){Password value=values.get(i);row(left+20,top+y,left+222,top+y+46,value.key().equals(passwordKey),hover(mx,my,20,y,202,46));
            label(trim(value.name,184),28,y+9,TEXT);label(trim(value.server.isEmpty()?"All servers":value.server,184),28,y+27,MUTED);
            hit(20,y,202,46,()->{if(clean()){password=value.copy();passwordKey=value.key();fillPassword();reveal=false;}});
        }
        if(values.isEmpty()){label("No saved accounts",28,143,MUTED);label("Passwords appear after",28,163,MUTED);label("register or login rules run.",28,177,MUTED);}
        scrollbar(225,132,200,values.size(),4);button(22,352,196,22,"Add current account",false,this::addCurrentAccount);label(engine.data().passwords.size()+" saved accounts",24,385,MUTED);
        panel(left+242,top+92,left+692,top+400,"");label("Username",252,93,TEXT);drawField("name",252,105,428);
        label("Server address",252,145,TEXT);drawField("server",252,157,428);label("Leave empty to use this entry on all servers.",252,183,MUTED);
        label("Password",252,197,TEXT);drawField("password",252,209,310);button(570,209,110,22,reveal?"Hide":"Show",reveal,()->reveal=!reveal);
        label("Stored encrypted. Kept out of shareable profiles.",252,241,MUTED);label("Edit the password your server accepts. Saving here",252,263,MUTED);label("does not change the password on the server.",252,277,MUTED);
        button(252,302,130,22,"Generate draft",false,()->put("password",engine.examplePassword(engine.data().generation)));
        button(390,302,130,22,"Copy password",false,()->{if(!text("password").isEmpty()){setClipboardString(text("password"));say("Password copied.",false);}});
        button(252,344,130,22,"Save password",false,this::savePassword);button(390,344,130,22,"Discard",false,this::discard);button(528,344,152,22,passwordKey!=null&&passwordKey.equals(confirmation)?"Confirm delete":"Delete entry",false,this::deletePassword);label("Generation settings are in the next tab.",252,383,MUTED);
    }
    private void generation(){
        panel(left+12,top+92,left+230,top+400,"Placeholders");int y=120;
        for(String[] item:new String[][]{{"%name%","Your Minecraft username"},{"%password%","Saved account password"},{"%pass%","Same as %password%"},{"%s","Fresh random letters"},{"%n","Fresh random digits"}}){label(item[0],24,y,TEXT);label(item[1],24,y+16,MUTED);y+=46;}
        label("Repeated password tokens",24,360,MUTED);label("use the same saved value.",24,374,MUTED);panel(left+242,top+92,left+692,top+400,"");
        label("Password length",252,93,TEXT);drawField("length",252,104,120);label("4 - 64 characters",386,111,MUTED);
        check(252,143,generation.lower,()->generation.lower=!generation.lower);label("Lowercase",270,144,TEXT);check(474,143,generation.upper,()->generation.upper=!generation.upper);label("Uppercase",492,144,TEXT);
        check(252,169,generation.digits,()->generation.digits=!generation.digits);label("Digits",270,170,TEXT);check(474,169,generation.symbols,()->generation.symbols=!generation.symbols);label("Symbols",492,170,TEXT);
        check(252,201,generation.excludeAmbiguous,()->generation.excludeAmbiguous=!generation.excludeAmbiguous);label("Avoid confusing characters (O, 0, I, l)",270,202,TEXT);
        check(252,231,perServer,()->perServer=!perServer);label("Separate passwords for each server",270,232,TEXT);
        label("Random letters length (%s)",252,267,MUTED);label("Random digits length (%n)",474,267,MUTED);drawField("letters",252,281,196);drawField("digits",474,281,206);
        label("Changes affect new passwords only.",252,318,MUTED);label("Existing accounts keep their saved password.",252,333,MUTED);button(252,360,160,22,"Save generation",false,this::saveGeneration);button(420,360,122,22,"Discard",false,this::discard);
    }
    private void drawField(String id,int x,int y,int width){SkeetEditorStyle.input(left+x,top+y,left+x+width,top+y+22);GuiTextField value=field(id);if(value instanceof SecretField)((SecretField)value).shown=reveal;value.drawTextBox();}
    private void insert(String token){if(token.equals("%name%")&&insertionField.equals("trigger")){field("trigger").setFocused(true);field("trigger").writeText(token);}else if(draft.action==Action.MESSAGE){field("message").setFocused(true);field("message").writeText(token);}}
    private void button(int x,int y,int width,int height,String text,boolean active,Runnable action){
        boolean over=hover(pointerX,pointerY,x,y,width,height);
        Gui.drawRect(left+x,top+y,left+x+width,top+y+height,active?0xFF29252F:over?0xFF2A2A30:0xFF202025);
        border(left+x,top+y,left+x+width,top+y+height,active?accent(.2F):over?0xFF5A5963:0xFF35363C);
        if(active)Gui.drawRect(left+x+1,top+y+height-2,left+x+width-1,top+y+height-1,accent(.2F));
        fontRendererObj.drawStringWithShadow(text,left+x+(width-fontRendererObj.getStringWidth(text))/2,top+y+(height-8)/2,TEXT);
        hit(x,y,width,height,action);
    }
    private void check(int x,int y,boolean enabled,Runnable action){Gui.drawRect(left+x,top+y,left+x+11,top+y+11,FIELD);border(left+x,top+y,left+x+11,top+y+11,BORDER);if(enabled)Gui.drawRect(left+x+3,top+y+3,left+x+8,top+y+8,accent(.2F));hit(x,y,13,13,action);}
    private void label(String text,int x,int y,int color){fontRendererObj.drawStringWithShadow(text,left+x,top+y,color);}private String trim(String text,int width){return fontRendererObj.trimStringToWidth(text,width);}
    private void hit(int x,int y,int width,int height,Runnable action){hits.add(new Hit(left+x,top+y,width,height,action));}private boolean hover(int mx,int my,int x,int y,int w,int h){return mx>=left+x&&mx<left+x+w&&my>=top+y&&my<top+y+h;}
    private void scrollbar(int x,int y,int height,int size,int visible){if(size<=visible)return;int thumb=Math.max(12,height*visible/size),offset=(height-thumb)*scroll/(size-visible);Gui.drawRect(left+x,top+y+offset,left+x+2,top+y+offset+thumb,accent(.3F));}
    private static void gear(int x,int y){GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_CURRENT_BIT|GL11.GL_LINE_BIT);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glLineWidth(1);GL11.glColor4f(.75F,.75F,.78F,1);for(int ring=0;ring<2;ring++){GL11.glBegin(GL11.GL_LINE_LOOP);for(int i=0;i<32;i++){double a=i*Math.PI/16,r=ring==0?(i%4<2?6:4.5):2;GL11.glVertex2d(x+Math.cos(a)*r,y+Math.sin(a)*r);}GL11.glEnd();}GL11.glPopAttrib();}
    private void say(String message,boolean failed){notice=message;error=failed;}private int number(String id){try{return Integer.parseInt(text(id));}catch(NumberFormatException failure){throw new IllegalArgumentException("Enter whole numbers in delay and length fields.");}}
    private Rule editedRule(){Rule v=draft.copy();v.trigger=text("trigger");v.message=text("message");v.minimum=number("min");v.maximum=number("max");v.cooldown=number("cooldown");return v;}
    private Rule savedRule(){for(Rule value:engine.data().rules)if(value.id.equals(draft.id))return value;return null;}
    private static boolean same(Rule a,Rule b){return a.trigger.equals(b.trigger)&&a.message.equals(b.message)&&a.action==b.action&&a.enabled==b.enabled&&a.oncePerConnection==b.oncePerConnection&&a.minimum==b.minimum&&a.maximum==b.maximum&&a.cooldown==b.cooldown;}
    private boolean dirty(){if(fields.isEmpty())return false;try{
        if(page==Page.RULES){Rule v=editedRule(),saved=savedRule();return saved==null?!v.trigger.isEmpty()||!v.message.isEmpty():!same(v,saved);}
        if(page==Page.PASSWORDS){if(password==null)return !text("name").isEmpty()||!text("password").isEmpty()||!text("server").isEmpty();return !text("name").equals(password.name)||!text("server").equals(password.server)||!text("password").equals(password.value)||passwordKey==null;}
        Generation v=editedGeneration(),s=engine.data().generation;return v.length!=s.length||v.lettersLength!=s.lettersLength||v.digitsLength!=s.digitsLength||v.lower!=s.lower||v.upper!=s.upper||v.digits!=s.digits||v.symbols!=s.symbols||v.excludeAmbiguous!=s.excludeAmbiguous||perServer!=engine.data().perServer;
    }catch(IllegalArgumentException failure){return true;}}
    private boolean clean(){if(!dirty())return true;say("Save or Discard your changes first.",true);return false;}
    private boolean persist(){try{engine.save();say("Saved locally.",false);return true;}catch(IOException|IllegalArgumentException failure){say(failure.getMessage(),true);return false;}}
    private void saveRule(){if(!engine.available())return;try{Rule value=editedRule();value.validate();List<Rule> values=engine.data().rules;Rule old=savedRule();int index=old==null?values.size():values.indexOf(old);if(old==null&&values.size()>=64)throw new IllegalArgumentException("You can save up to 64 rules.");if(old==null)values.add(value);else values.set(index,value);if(!persist()){if(old==null)values.remove(value);else values.set(index,old);return;}draft=value.copy();}catch(IllegalArgumentException failure){say(failure.getMessage(),true);}}
    private void deleteRule(){Rule saved=savedRule();if(saved==null){discard();return;}if(!confirmation.equals(saved.id)){confirmation=saved.id;say("Click Confirm delete to remove this rule.",false);return;}int i=engine.data().rules.indexOf(saved);engine.data().rules.remove(saved);if(!persist()){engine.data().rules.add(i,saved);return;}draft=engine.data().rules.isEmpty()?new Rule():engine.data().rules.get(0).copy();fillRule();confirmation="";}
    private void savePassword(){if(!engine.available())return;try{Password value=new Password(text("name").trim(),ChatterEngine.normalizeServer(text("server")),text("password"));value.validate();List<Password> values=engine.data().passwords;Password old=null;for(Password p:values){if(p.key().equals(passwordKey))old=p;else if(p.key().equals(value.key()))throw new IllegalArgumentException("That username and server already have a password.");}int i=old==null?values.size():values.indexOf(old);if(old==null&&values.size()>=1000)throw new IllegalArgumentException("Password limit reached.");if(old==null)values.add(value);else values.set(i,value);if(!persist()){if(old==null)values.remove(value);else values.set(i,old);return;}password=value.copy();passwordKey=value.key();fillPassword();}catch(IllegalArgumentException failure){say(failure.getMessage(),true);}}
    private void addCurrentAccount(){
        if(!clean())return;Password selected=null;
        for(Password value:engine.data().passwords)if(value.name.equalsIgnoreCase(currentName())) {
            if(value.server.equalsIgnoreCase(currentServer())){selected=value;break;}
            if(value.server.isEmpty())selected=value;
        }
        if(selected!=null){password=selected.copy();passwordKey=selected.key();}
        else{password=new Password(currentName(),engine.data().perServer?currentServer():"",engine.examplePassword(engine.data().generation));passwordKey=null;}
        fillPassword();reveal=false;
    }
    private void deletePassword(){if(passwordKey==null){discard();return;}if(!passwordKey.equals(confirmation)){confirmation=passwordKey;say("Click Confirm delete to remove this local password.",false);return;}List<Password> values=engine.data().passwords;for(int i=0;i<values.size();i++)if(values.get(i).key().equals(passwordKey)){Password old=values.remove(i);if(!persist()){values.add(i,old);return;}break;}password=null;passwordKey=null;fillPassword();confirmation="";}
    private Generation editedGeneration(){Generation v=generation.copy();v.length=number("length");v.lettersLength=number("letters");v.digitsLength=number("digits");return v;}
    private void saveGeneration(){if(!engine.available())return;try{Generation value=editedGeneration();value.validate();Generation old=engine.data().generation;boolean scope=engine.data().perServer;engine.data().generation=value;engine.data().perServer=perServer;if(!persist()){engine.data().generation=old;engine.data().perServer=scope;return;}generation=value.copy();}catch(IllegalArgumentException failure){say(failure.getMessage(),true);}}
    private void discard(){if(page==Page.RULES){Rule saved=savedRule();draft=saved==null?(engine.data().rules.isEmpty()?new Rule():engine.data().rules.get(0).copy()):saved.copy();fillRule();}else if(page==Page.PASSWORDS){password=null;if(passwordKey!=null)for(Password p:engine.data().passwords)if(p.key().equals(passwordKey))password=p.copy();fillPassword();}else{generation=engine.data().generation.copy();perServer=engine.data().perServer;fillGeneration();}say("Changes discarded.",false);}
    private void navigate(Page next){if(!clean())return;page=next;scroll=0;notice="";layout();}private String currentName(){return mc.getSession()==null?"Player":mc.getSession().getUsername();}private String currentServer(){return mc.getCurrentServerData()==null?"":ChatterEngine.normalizeServer(mc.getCurrentServerData().serverIP);}private void close(){if(clean())mc.displayGuiScreen(parent);}
    @Override protected void mouseClicked(int mx,int my,int button)throws IOException{int x=Math.round(mx/scale),y=Math.round(my/scale);for(Map.Entry<String,GuiTextField> entry:fields.entrySet()){entry.getValue().mouseClicked(x,y,button);if(entry.getValue().isFocused()&&(entry.getKey().equals("trigger")||entry.getKey().equals("message")))insertionField=entry.getKey();}if(button==0)for(Hit hit:new ArrayList<>(hits))if(hit.inside(x,y)){hit.action.run();return;}}
    @Override protected void keyTyped(char c,int key)throws IOException{if(key==Keyboard.KEY_ESCAPE){close();return;}if(key==Keyboard.KEY_TAB){List<GuiTextField> values=new ArrayList<>(fields.values());int index=-1;for(int i=0;i<values.size();i++)if(values.get(i).isFocused())index=i;for(GuiTextField f:values)f.setFocused(false);values.get((index+1)%values.size()).setFocused(true);return;}if(key==Keyboard.KEY_RETURN){if(page==Page.RULES)saveRule();else if(page==Page.PASSWORDS)savePassword();else saveGeneration();return;}for(GuiTextField f:fields.values())if(f.isFocused()){f.textboxKeyTyped(c,key);confirmation="";break;}}
    @Override public void handleMouseInput()throws IOException{super.handleMouseInput();int wheel=Mouse.getEventDWheel();if(wheel!=0&&page!=Page.GENERATION)scroll=Math.max(0,scroll+(wheel>0?-1:1));}
    @Override public void updateScreen(){for(GuiTextField f:fields.values())f.updateCursorCounter();}@Override public void onGuiClosed(){Keyboard.enableRepeatEvents(false);reveal=false;}@Override public boolean doesGuiPauseGame(){return false;}
    private static final class Hit{final int x,y,w,h;final Runnable action;Hit(int x,int y,int w,int h,Runnable action){this.x=x;this.y=y;this.w=w;this.h=h;this.action=action;}boolean inside(int px,int py){return px>=x&&py>=y&&px<x+w&&py<y+h;}}
    private final class SecretField extends GuiTextField{boolean shown;SecretField(int id,int x,int y,int w,int h){super(id,ChatterGui.this.fontRendererObj,x,y,w,h);}@Override public void drawTextBox(){if(shown){super.drawTextBox();return;}String actual=getText();int cursor=getCursorPosition(),selection=getSelectionEnd();try{super.setText(String.join("",Collections.nCopies(actual.length(),"*")));setCursorPosition(cursor);setSelectionPos(selection);super.drawTextBox();}finally{super.setText(actual);setCursorPosition(cursor);setSelectionPos(selection);}}}
}
