package keystrokesmod.script;

import dev.vibe.Vibe;
import dev.vibe.module.Module;
import dev.vibe.script.ScriptModule;
import dev.vibe.script.ScriptRuntime;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.Setting;
import dev.vibe.ui.RenderUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import keystrokesmod.script.model.Block;
import keystrokesmod.script.model.Bridge;
import keystrokesmod.script.model.Entity;
import keystrokesmod.script.model.ItemStack;
import keystrokesmod.script.model.Message;
import keystrokesmod.script.model.NetworkPlayer;
import keystrokesmod.script.model.TileEntity;
import keystrokesmod.script.model.Vec3;
import keystrokesmod.script.packet.clientbound.SPacket;
import keystrokesmod.script.packet.serverbound.CPacket;
import keystrokesmod.script.packet.serverbound.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.item.ItemBlock;
import net.minecraft.network.Packet;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * Source-compatible Raven BS script surface implemented against Vibe's shared
 * module, rotation, render and input layers. All nested API names intentionally
 * retain Raven's lower-case convention so existing Java scripts compile as-is.
 */
public class ScriptDefaults {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public static final Bridge bridge = new Bridge();
    private static ExecutorService executor;

    public static void reloadModules() { }

    public static class client {
        public static boolean allowFlying() { return mc.thePlayer != null && mc.thePlayer.capabilities.allowFlying; }
        public static void removePotionEffect(int id) { if (mc.thePlayer != null) mc.thePlayer.removePotionEffectClient(id); }
        public static int getUID() { return 0; }
        public static String getUser() { return Vibe.getInstance().getIdentity() == null ? "" : Vibe.getInstance().getIdentity().getGamertag(); }
        public static void addEnemy(String username) { if (Vibe.getInstance().getTargetManager() != null) Vibe.getInstance().getTargetManager().add(username); }
        public static void removeEnemy(String username) { if (Vibe.getInstance().getTargetManager() != null) Vibe.getInstance().getTargetManager().remove(username); }
        public static void addFriend(String username) { if (Vibe.getInstance().getFriendManager() != null) Vibe.getInstance().getFriendManager().add(username, username); }
        public static void removeFriend(String username) { if (Vibe.getInstance().getFriendManager() != null) Vibe.getInstance().getFriendManager().remove(username); }
        public static boolean isFriend(String username) { return Vibe.getInstance().getFriendManager() != null && Vibe.getInstance().getFriendManager().isFriend(username); }
        public static boolean isEnemy(String username) { return Vibe.getInstance().getTargetManager() != null && Vibe.getInstance().getTargetManager().isTarget(username); }
        public static void async(Runnable runnable) { if(executor==null)executor=Executors.newCachedThreadPool();executor.execute(runnable); }
        public static int getFPS() { return Minecraft.getDebugFPS(); }
        public static void chat(String message) { if(mc.thePlayer!=null)mc.thePlayer.sendChatMessage(message); }
        public static void print(String value) { if(mc.thePlayer!=null)mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(value)); }
        public static void print(Object value) { print(String.valueOf(value)); }
        public static void print(Message value) { if(mc.thePlayer!=null&&value!=null)mc.thePlayer.addChatMessage(value.component); }
        public static boolean isDiagonal() { return mc.thePlayer!=null&&mc.thePlayer.movementInput.moveForward!=0&&mc.thePlayer.movementInput.moveStrafe!=0; }
        public static void setTimer(float value) { /* no global timer mutation: Vibe preserves vanilla timing */ }
        public static boolean isCreative() { return mc.thePlayer!=null&&mc.thePlayer.capabilities.isCreativeMode; }
        public static void processPacket(SPacket packet) { if(packet!=null&&packet.packet!=null&&mc.getNetHandler()!=null) packet.packet.processPacket(mc.getNetHandler()); }
        public static void processPacketNoEvent(SPacket packet) { processPacket(packet); }
        public static void multiplyMotion(double factor) { if(mc.thePlayer!=null){mc.thePlayer.motionX*=factor;mc.thePlayer.motionY*=factor;mc.thePlayer.motionZ*=factor;} }
        public static String getTitle() { return ""; } public static String getSubTitle(){return "";} public static String getRecordPlaying(){return "";}
        public static boolean isFlying(){return mc.thePlayer!=null&&mc.thePlayer.capabilities.isFlying;} public static boolean isSinglePlayer(){return mc.isSingleplayer();} public static boolean isSpectator(){return mc.thePlayer!=null&&mc.thePlayer.isSpectator();}
        public static void setFlying(boolean value){if(mc.thePlayer!=null)mc.thePlayer.capabilities.isFlying=value;} public static void setJump(boolean value){if(mc.thePlayer!=null)mc.thePlayer.movementInput.jump=value;} public static void setJumping(boolean value){if(mc.thePlayer!=null)mc.thePlayer.setJumping(value);}
        public static void setRenderArmPitch(float value){if(mc.thePlayer!=null){mc.thePlayer.prevRenderArmPitch=value;mc.thePlayer.renderArmPitch=value;}} public static float getRenderArmPitch(){return mc.thePlayer==null?0:mc.thePlayer.renderArmPitch;} public static void setRenderArmYaw(float value){if(mc.thePlayer!=null){mc.thePlayer.prevRenderArmYaw=value;mc.thePlayer.renderArmYaw=value;}} public static float getRenderArmYaw(){return mc.thePlayer==null?0:mc.thePlayer.renderArmYaw;} public static float getEquippedProgress(){return 0F;}
        public static long getTotalMemory(){return Runtime.getRuntime().totalMemory();} public static long getFreeMemory(){return Runtime.getRuntime().freeMemory();} public static long getMaxMemory(){return Runtime.getRuntime().maxMemory();}
        public static List<String[]> getResourcePacks(){List<String[]> result=new ArrayList<String[]>();if(mc.getResourcePackRepository()!=null)for(net.minecraft.client.resources.ResourcePackRepository.Entry entry:mc.getResourcePackRepository().getRepositoryEntries())result.add(new String[]{entry.getResourcePackName(),entry.getTexturePackDescription()});return result;}
        public static void disconnect(){if(mc.theWorld!=null)mc.theWorld.sendQuittingDisconnectingPacket();mc.loadWorld(null);mc.displayGuiScreen(new net.minecraft.client.gui.GuiMainMenu());}
        public static void attack(Entity entity){if(entity!=null&&entity.entity!=null&&mc.thePlayer!=null)mc.playerController.attackEntity(mc.thePlayer,entity.entity);} public static void jump(){if(mc.thePlayer!=null)mc.thePlayer.jump();}
        public static boolean allowEditing(){return mc.thePlayer!=null&&mc.thePlayer.capabilities.allowEdit;} public static void setItemInUseCount(int count){} public static int getItemInUseCount(){return mc.thePlayer==null?0:mc.thePlayer.getItemInUseCount();} public static int getItemInUseDuration(){return mc.thePlayer==null?0:mc.thePlayer.getItemInUseDuration();}
        public static void log(Object value){System.out.println("[Vibe Script] "+value);} public static void setSneaking(boolean value){if(mc.thePlayer!=null)mc.thePlayer.setSneaking(value);} public static void setSneak(boolean value){if(mc.thePlayer!=null)mc.thePlayer.movementInput.sneak=value;} public static boolean isSneak(){return mc.thePlayer!=null&&mc.thePlayer.movementInput.sneak;}
        public static Entity getPlayer(){return mc.thePlayer==null?null:Entity.convert(mc.thePlayer);} public static boolean isRiding(){return mc.thePlayer!=null&&mc.thePlayer.isRiding();} public static Vec3 getMotion(){return mc.thePlayer==null?new Vec3(0,0,0):new Vec3(mc.thePlayer.motionX,mc.thePlayer.motionY,mc.thePlayer.motionZ);}
        public static void sleep(long ms){try{Thread.sleep(ms);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}} public static void sleep(int ms){sleep((long)ms);} public static void ping(){playSound("note.pling",1,1);} public static void playSound(String name,float volume,float pitch){if(mc.thePlayer!=null)mc.thePlayer.playSound(name,volume,pitch);}
        public static boolean isMoving(){return mc.thePlayer!=null&&(mc.thePlayer.movementInput.moveForward!=0||mc.thePlayer.movementInput.moveStrafe!=0);} public static boolean isJump(){return mc.thePlayer!=null&&mc.thePlayer.movementInput.jump;} public static float getStrafe(){return mc.thePlayer==null?0:mc.thePlayer.movementInput.moveStrafe;} public static float getForward(){return mc.thePlayer==null?0:mc.thePlayer.movementInput.moveForward;}
        public static void closeScreen(){if(mc.thePlayer!=null)mc.thePlayer.closeScreen();else mc.displayGuiScreen(null);} public static String getScreen(){return mc.currentScreen==null?"":mc.currentScreen.getClass().getSimpleName();}
        public static float[] getRotationsToEntity(Entity entity){return entity==null?new float[]{0,0}:rotationsTo(entity.entity.posX,entity.entity.posY+entity.entity.getEyeHeight()*0.5D,entity.entity.posZ);} public static void sendPacket(CPacket packet){Packet raw=PacketHandler.convertCPacket(packet);if(raw!=null&&mc.getNetHandler()!=null)mc.getNetHandler().getNetworkManager().sendPacket(raw);} public static void sendPacketNoEvent(CPacket packet){sendPacket(packet);}
        public static boolean inFocus(){return mc.inGameHasFocus;} public static void dropItem(boolean stack){if(mc.thePlayer!=null)mc.thePlayer.dropOneItem(stack);} public static void setMotion(double x,double y,double z){if(mc.thePlayer!=null){mc.thePlayer.motionX=x;mc.thePlayer.motionY=y;mc.thePlayer.motionZ=z;}} public static void setSpeed(double speed){if(mc.thePlayer!=null){double angle=Math.toRadians(mc.thePlayer.rotationYaw);mc.thePlayer.motionX=-Math.sin(angle)*speed;mc.thePlayer.motionZ=Math.cos(angle)*speed;}}
        public static void setForward(float value){if(mc.thePlayer!=null)mc.thePlayer.movementInput.moveForward=value;} public static void setStrafe(float value){if(mc.thePlayer!=null)mc.thePlayer.movementInput.moveStrafe=value;} public static String getServerIP(){return mc.getCurrentServerData()==null||mc.isSingleplayer()?"":mc.getCurrentServerData().serverIP;} public static int[] getDisplaySize(){ScaledResolution r=new ScaledResolution(mc);return new int[]{r.getScaledWidth(),r.getScaledHeight(),r.getScaleFactor()};}
        public static float getServerDirection(keystrokesmod.script.model.PlayerState state){return state==null?0:state.yaw;} public static void setSprinting(boolean value){if(mc.thePlayer!=null)mc.thePlayer.setSprinting(value);} public static void swing(){if(mc.thePlayer!=null)mc.thePlayer.swingItem();} public static long time(){return System.currentTimeMillis();}
        public static void enableMovementFix(){} public static void disableMovementFix(){} public static boolean isMovementFixActive(){return false;} public static boolean isRotationActive(){return false;}
        /** Rotation management was removed with MoveFix. Script rotation calls now use the normal visible player view. */
        public static void setRotations(float yaw,float pitch){if(mc.thePlayer!=null){mc.thePlayer.rotationYaw=yaw;mc.thePlayer.rotationPitch=Math.max(-90F,Math.min(90F,pitch));mc.thePlayer.rotationYawHead=yaw;mc.thePlayer.renderYawOffset=yaw;}} public static void setYaw(float yaw){setRotations(yaw,getServerPitch());} public static void setPitch(float pitch){setRotations(getServerYaw(),pitch);} public static Float getServerYaw(){return mc.thePlayer==null?0F:mc.thePlayer.rotationYaw;} public static Float getServerPitch(){return mc.thePlayer==null?0F:mc.thePlayer.rotationPitch;}
        public static float[] getRotationsToBlock(Vec3 value){return rotationsTo(value.x,value.y,value.z);} public static Object[] raycastBlock(double range){return raycastBlock(range,mc.thePlayer.rotationYaw,mc.thePlayer.rotationPitch);} public static Object[] raycastBlock(double range,float yaw,float pitch){if(mc.thePlayer==null||mc.theWorld==null)return null;net.minecraft.util.Vec3 start=mc.thePlayer.getPositionEyes(1);net.minecraft.util.Vec3 look=look(yaw,pitch);MovingObjectPosition hit=mc.theWorld.rayTraceBlocks(start,start.addVector(look.xCoord*range,look.yCoord*range,look.zCoord*range),false,false,false);if(hit==null||hit.typeOfHit!=MovingObjectPosition.MovingObjectType.BLOCK)return null;Vec3 pos=new Vec3(hit.getBlockPos());return new Object[]{pos,new Vec3(hit.hitVec.xCoord-pos.x,hit.hitVec.yCoord-pos.y,hit.hitVec.zCoord-pos.z),hit.sideHit.name()};}
        public static Object[] raycastEntity(double range){return raycastEntity(range,mc.thePlayer==null?0:mc.thePlayer.rotationYaw,mc.thePlayer==null?0:mc.thePlayer.rotationPitch);} public static Object[] raycastEntity(double range,float yaw,float pitch){if(mc.thePlayer==null||mc.theWorld==null)return null;net.minecraft.util.Vec3 eye=mc.thePlayer.getPositionEyes(1);net.minecraft.util.Vec3 end=eye.addVector(look(yaw,pitch).xCoord*range,look(yaw,pitch).yCoord*range,look(yaw,pitch).zCoord*range);net.minecraft.entity.Entity picked=null;net.minecraft.util.Vec3 point=null;double best=range;for(Object raw:mc.theWorld.getEntitiesWithinAABBExcludingEntity(mc.thePlayer,mc.thePlayer.getEntityBoundingBox().addCoord(end.xCoord-eye.xCoord,end.yCoord-eye.yCoord,end.zCoord-eye.zCoord).expand(1,1,1))){if(!(raw instanceof EntityLivingBase))continue;net.minecraft.entity.Entity candidate=(net.minecraft.entity.Entity)raw;MovingObjectPosition hit=candidate.getEntityBoundingBox().expand(candidate.getCollisionBorderSize(),candidate.getCollisionBorderSize(),candidate.getCollisionBorderSize()).calculateIntercept(eye,end);if(hit!=null){double distance=eye.distanceTo(hit.hitVec);if(distance<best){best=distance;picked=candidate;point=hit.hitVec;}}}return picked==null?null:new Object[]{Entity.convert(picked),new Vec3(point.xCoord-picked.posX,point.yCoord-picked.posY,point.zCoord-picked.posZ),best*best};}
        public static boolean canPlaceBlock(ItemStack stack,Vec3 pos,String side){return stack!=null&&stack.isBlock&&mc.theWorld!=null;} public static boolean placeBlock(Vec3 target,String side,Vec3 hit){if(mc.thePlayer==null||target==null)return false;EnumFacing face;try{face=EnumFacing.valueOf(side.toUpperCase(java.util.Locale.ROOT));}catch(Exception ignored){return false;}return mc.playerController.onPlayerRightClick(mc.thePlayer,mc.theWorld,mc.thePlayer.getHeldItem(),Vec3.getBlockPos(target),face,hit==null?new net.minecraft.util.Vec3(0.5,0.5,0.5):Vec3.getVec3(hit));}
        private static float[] rotationsTo(double x,double y,double z){if(mc.thePlayer==null)return new float[]{0,0};double dx=x-mc.thePlayer.posX,dy=y-(mc.thePlayer.posY+mc.thePlayer.getEyeHeight()),dz=z-mc.thePlayer.posZ;double horizontal=Math.sqrt(dx*dx+dz*dz);return new float[]{(float)(Math.atan2(dz,dx)*180/Math.PI)-90F,(float)-Math.toDegrees(Math.atan2(dy,horizontal))};} private static net.minecraft.util.Vec3 look(float yaw,float pitch){float cy=MathHelper.cos(-yaw*0.017453292F-(float)Math.PI),sy=MathHelper.sin(-yaw*0.017453292F-(float)Math.PI),cp=-MathHelper.cos(-pitch*0.017453292F),sp=MathHelper.sin(-pitch*0.017453292F);return new net.minecraft.util.Vec3(sy*cp,sp,cy*cp);}
    }

    public static class world {
        public static Block getBlockAt(int x,int y,int z){return mc.theWorld==null?null:new Block(new Vec3(x,y,z));} public static Block getBlockAt(Vec3 value){return value==null?null:getBlockAt((int)value.x,(int)value.y,(int)value.z);} public static String getDimension(){return mc.theWorld==null?"":String.valueOf(mc.theWorld.provider.getDimensionId());}
        public static List<Entity> getEntities(){List<Entity> result=new ArrayList<Entity>();if(mc.theWorld!=null)for(Object value:mc.theWorld.loadedEntityList)if(value instanceof net.minecraft.entity.Entity)result.add(Entity.convert((net.minecraft.entity.Entity)value));return result;} public static Entity getEntityById(int id){return mc.theWorld==null?null:Entity.convert(mc.theWorld.getEntityByID(id));}
        public static List<NetworkPlayer> getNetworkPlayers(){List<NetworkPlayer> result=new ArrayList<NetworkPlayer>();if(mc.getNetHandler()!=null)for(net.minecraft.client.network.NetworkPlayerInfo value:mc.getNetHandler().getPlayerInfoMap())result.add(NetworkPlayer.convert(value));return result;} public static List<Entity> getPlayerEntities(){List<Entity> result=new ArrayList<Entity>();if(mc.theWorld!=null)for(EntityPlayer player:mc.theWorld.playerEntities)result.add(Entity.convert(player));return result;}
        public static List<String> getScoreboard(){return new ArrayList<String>();} public static String getTabHeader(){return "";} public static String getTabFooter(){return "";} public static Map<String,List<String>> getTeams(){return new LinkedHashMap<String,List<String>>();} public static List<TileEntity> getTileEntities(){List<TileEntity> out=new ArrayList<TileEntity>();if(mc.theWorld!=null)for(Object value:mc.theWorld.loadedTileEntityList)if(value instanceof net.minecraft.tileentity.TileEntity)out.add(new TileEntity((net.minecraft.tileentity.TileEntity)value));return out;}
    }

    public static class modules {
        private final String superName;
        public modules(String name){superName=name;}
        /**
         * Do not read Vibe's singleton while a generated script class is
         * initialising. ScriptRuntime registers the owner before Class.forName
         * and removes it atomically during unload/reload.
         */
        private ScriptModule own(){
            ScriptModule direct=ScriptRuntime.getRegisteredModule(superName);
            if(direct!=null)return direct;
            Vibe vibe=Vibe.getInstance();
            return vibe==null||vibe.getScriptRuntime()==null?null:vibe.getScriptRuntime().getModule(superName);
        }
        private Module find(String name){Vibe vibe=Vibe.getInstance();return vibe==null||vibe.getModuleManager()==null?null:vibe.getModuleManager().getModule(name);}
        public void enable(String name){Module m=find(name);if(m!=null)m.setEnabled(true);} public void disable(String name){Module m=find(name);if(m!=null)m.setEnabled(false);} public boolean isEnabled(String name){Module m=find(name);return m!=null&&m.isEnabled();} public Entity getKillAuraTarget(){return null;}
        public Map<String,Object> getSettings(String name){Map<String,Object> result=new LinkedHashMap<String,Object>();Module m=find(name);if(m!=null)for(Setting<?> setting:m.getSettings())result.put(setting.getRawName(),setting.getValue());return result;} public Map<String,List<String>> getCategories(){Map<String,List<String>> out=new LinkedHashMap<String,List<String>>();for(dev.vibe.module.Category category:dev.vibe.module.Category.values()){List<String> names=new ArrayList<String>();for(Module m:Vibe.getInstance().getModuleManager().getModules(category))names.add(m.getRawName());out.put(category.name().toLowerCase(),names);}return out;}
        public void registerGroup(String name){} public void registerDescription(String description){} public void registerButton(String name,boolean value){ScriptModule m=own();if(m!=null)m.registerBoolean(name,value);} public void registerButton(String group,String name,boolean value){registerButton(name,value);} public void registerKey(String name,int code){ScriptModule m=own();if(m!=null)m.registerNumber(name,code,0,Keyboard.KEYBOARD_SIZE,1);} public void registerKey(String group,String name,int code){registerKey(name,code);} public void registerSlider(String name,double value,double min,double max,double step){ScriptModule m=own();if(m!=null)m.registerNumber(name,value,min,max,step);} public void registerSlider(String name,String suffix,double value,double min,double max,double step){registerSlider(name,value,min,max,step);} public void registerSlider(String group,String name,String suffix,double value,double min,double max,double step){registerSlider(name,value,min,max,step);} public void registerSlider(String name,int value,String[] values){ScriptModule m=own();if(m!=null)m.registerMode(name,value,values);} public void registerSlider(String name,String suffix,int value,String[] values){registerSlider(name,value,values);} public void registerSlider(String group,String name,String suffix,int value,String[] values){registerSlider(name,value,values);}
        public void registerColor(String name,int r,int g,int b){registerColor(name,r,g,b,255);} public void registerColor(String name,int r,int g,int b,int a){ScriptModule m=own();if(m!=null)m.registerColor(name,r,g,b,a);} public void registerColor(String group,String name,int r,int g,int b){registerColor(name,r,g,b);} public void registerColor(String group,String name,int r,int g,int b,int a){registerColor(name,r,g,b,a);} public int getColor(String module,String name){Module m=find(module);return m instanceof ScriptModule?((ScriptModule)m).getColor(name):0xFFFFFFFF;} public void setColor(String module,String name,int r,int g,int b){setColor(module,name,r,g,b,255);} public void setColor(String module,String name,int r,int g,int b,int a){Module m=find(module);if(m instanceof ScriptModule)((ScriptModule)m).setColor(name,r,g,b,a);} public boolean getButton(String module,String name){Module m=find(module);return m instanceof ScriptModule&&((ScriptModule)m).getButton(name);} public double getSlider(String module,String name){Module m=find(module);return m instanceof ScriptModule?((ScriptModule)m).getSlider(name):0;} public boolean getKeyPressed(String module,String name){return keybinds.isKeyDown((int)getSlider(module,name));} public void setButton(String module,String name,boolean value){Module m=find(module);if(m instanceof ScriptModule)((ScriptModule)m).setButton(name,value);} public void setSlider(String module,String name,double value){Module m=find(module);if(m instanceof ScriptModule)((ScriptModule)m).setSlider(name,value);} public void setKey(String module,String name,int code){setSlider(module,name,code);} public boolean isHidden(String name){return false;} public Vec3 getBedAuraPosition(){return null;} public float[] getBedAuraProgress(){return null;} public boolean isScaffolding(){return false;} public boolean isTowering(){return false;}
    }

    /** Direct OpenGL compatibility helpers used by Raven's visual scripts. */
    public static class gl {
        public static void alpha(boolean value){if(value)GL11.glEnable(GL11.GL_ALPHA_TEST);else GL11.glDisable(GL11.GL_ALPHA_TEST);} public static void begin(int mode){GL11.glBegin(mode);} public static void blend(boolean value){if(value)GL11.glEnable(GL11.GL_BLEND);else GL11.glDisable(GL11.GL_BLEND);} public static void color(float r,float g,float b,float a){GL11.glColor4f(r,g,b,a);} public static void cull(boolean value){if(value)GL11.glEnable(GL11.GL_CULL_FACE);else GL11.glDisable(GL11.GL_CULL_FACE);} public static void depth(boolean value){if(value)GL11.glEnable(GL11.GL_DEPTH_TEST);else GL11.glDisable(GL11.GL_DEPTH_TEST);} public static void depthMask(boolean value){GL11.glDepthMask(value);} public static void disable(int cap){GL11.glDisable(cap);} public static void enable(int cap){GL11.glEnable(cap);} public static void disableItemLighting(){net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();} public static void enableItemLighting(boolean gui){net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();} public static void resetColor(){GL11.glColor4f(1,1,1,1);} public static void end(){GL11.glEnd();} public static void lighting(boolean value){if(value)GL11.glEnable(GL11.GL_LIGHTING);else GL11.glDisable(GL11.GL_LIGHTING);} public static void lineSmooth(boolean value){if(value)GL11.glEnable(GL11.GL_LINE_SMOOTH);else GL11.glDisable(GL11.GL_LINE_SMOOTH);} public static void lineWidth(float value){GL11.glLineWidth(value);} public static void normal(float x,float y,float z){GL11.glNormal3f(x,y,z);} public static void pop(){GL11.glPopMatrix();} public static void push(){GL11.glPushMatrix();} public static void rotate(float angle,float x,float y,float z){GL11.glRotatef(angle,x,y,z);} public static void scale(float x,float y,float z){GL11.glScalef(x,y,z);} public static void scissor(int x,int y,int width,int height){ScaledResolution r=new ScaledResolution(mc);GL11.glScissor(x*r.getScaleFactor(),mc.displayHeight-(y+height)*r.getScaleFactor(),width*r.getScaleFactor(),height*r.getScaleFactor());} public static void scissor(boolean value){if(value)GL11.glEnable(GL11.GL_SCISSOR_TEST);else GL11.glDisable(GL11.GL_SCISSOR_TEST);} public static void texture2d(boolean value){if(value)GL11.glEnable(GL11.GL_TEXTURE_2D);else GL11.glDisable(GL11.GL_TEXTURE_2D);} public static void translate(float x,float y,float z){GL11.glTranslatef(x,y,z);} public static void vertex2(float x,float y){GL11.glVertex2f(x,y);} public static void vertex3(float x,float y,float z){GL11.glVertex3f(x,y,z);}
    }

    /** Per-script string persistence stored under .minecraft/vibe/scripts. */
    public static class config {
        private static final Map<String,String> VALUES = new HashMap<String,String>();
        public static boolean set(String key,String value){if(key==null)return false;VALUES.put(key,value==null?"":value);return true;} public static String get(String key){return VALUES.get(key);}
    }

    /** Raven's screen/world visual primitives, mapped to Vibe's safe render state. */
    public static class render {
        public static void block(Vec3 position,int color,boolean outline,boolean shade){if(position==null)return;AxisAlignedBB box=new AxisAlignedBB(position.x,position.y,position.z,position.x+1,position.y+1,position.z+1);box(box,color,outline,shade);} public static void block(int x,int y,int z,int color,boolean outline,boolean shade){block(new Vec3(x,y,z),color,outline,shade);}
        public static void entity(Entity entity,int color,float partial,boolean outline,boolean shade){if(entity==null||entity.entity==null)return;AxisAlignedBB box=entity.entity.getEntityBoundingBox().offset(-mc.getRenderManager().viewerPosX,-mc.getRenderManager().viewerPosY,-mc.getRenderManager().viewerPosZ);box(box,color,outline,shade);} public static void entityGui(Entity entity,int x,int y,float mouseX,float mouseY,int scale){if(entity==null||!(entity.entity instanceof EntityLivingBase))return;net.minecraft.client.gui.inventory.GuiInventory.drawEntityOnScreen(x,y,scale,mouseX-x,mouseY-y,(EntityLivingBase)entity.entity);}
        public static void resetEquippedProgress(){try{mc.getItemRenderer().resetEquippedProgress();}catch(Exception ignored){}} public static void tracer(Entity entity,float width,int color,float partial){if(entity==null)return;line3D(new Vec3(0,mc.thePlayer.getEyeHeight(),0),entity.getPosition(),width,color);} public static void showGui(){Vibe.getInstance().openClickGui();}
        public static void item(ItemStack item,float x,float y,float scale){if(item==null||item.itemStack==null)return;GL11.glPushMatrix();GL11.glScalef(scale,scale,1);mc.getRenderItem().renderItemAndEffectIntoGUI(item.itemStack,(int)(x/scale),(int)(y/scale));GL11.glPopMatrix();} public static void image(Object image,float x,float y,float width,float height){}
        public static Vec3 worldToScreen(double x,double y,double z,int scale,float partial){return new Vec3(x,y,z);} public static void roundedRect(float x,float y,float right,float bottom,float radius,int color){RenderUtils.roundedRect((int)x,(int)y,(int)right,(int)bottom,radius,color);} public static void gradientRect(float x,float y,float right,float bottom,int left,int rightColor){Gui.drawRect((int)x,(int)y,(int)right,(int)bottom,left);} public static void gradientRect(float x,float y,float right,float bottom,int tl,int bl,int tr,int br){Gui.drawRect((int)x,(int)y,(int)right,(int)bottom,tl);}
        public static double[] getRotations(){return new double[]{mc.thePlayer==null?0:mc.thePlayer.rotationYaw,mc.thePlayer==null?0:mc.thePlayer.rotationPitch};} public static double[] getCameraRotations(){return getRotations();} public static int getFontWidth(String text){return mc.fontRendererObj.getStringWidth(text);} public static int getFontHeight(){return mc.fontRendererObj.FONT_HEIGHT;} public static Vec3 getPosition(){return mc.thePlayer==null?new Vec3(0,0,0):new Vec3(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ);}
        public static void text2d(String text,float x,float y,float scale,int color,boolean shadow){GL11.glPushMatrix();GL11.glScalef(scale,scale,1);if(shadow)mc.fontRendererObj.drawStringWithShadow(text,x/scale,y/scale,color);else mc.fontRendererObj.drawString(text,(int)(x/scale),(int)(y/scale),color);GL11.glPopMatrix();} public static void text3d(String text,Vec3 position,float scale,boolean shadow,boolean depth,boolean background,int color){}
        public static void rect(float x,float y,float right,float bottom,int color){Gui.drawRect((int)x,(int)y,(int)right,(int)bottom,color);} public static void line2D(double x,double y,double right,double bottom,float width,int color){GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_LINE_BIT);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_BLEND);GL11.glLineWidth(width);GL11.glColor4ub((byte)(color>>16),(byte)(color>>8),(byte)color,(byte)(color>>24));GL11.glBegin(GL11.GL_LINES);GL11.glVertex2d(x,y);GL11.glVertex2d(right,bottom);GL11.glEnd();GL11.glPopAttrib();} public static void line3D(Vec3 first,Vec3 second,float width,int color){if(first!=null&&second!=null)line3D(first.x,first.y,first.z,second.x,second.y,second.z,width,color);} public static void line3D(double x,double y,double z,double right,double bottom,double back,float width,int color){GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_LINE_BIT);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_BLEND);GL11.glLineWidth(width);GL11.glColor4ub((byte)(color>>16),(byte)(color>>8),(byte)color,(byte)(color>>24));GL11.glBegin(GL11.GL_LINES);GL11.glVertex3d(x,y,z);GL11.glVertex3d(right,bottom,back);GL11.glEnd();GL11.glPopAttrib();} public static boolean isInView(Entity entity){return entity!=null&&entity.entity!=null&&mc.getRenderManager().isRenderShadow();}
        private static void box(AxisAlignedBB box,int color,boolean outline,boolean shade){GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_LINE_BIT);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glColor4ub((byte)(color>>16),(byte)(color>>8),(byte)color,(byte)(color>>24));if(shade)net.minecraft.client.renderer.RenderGlobal.drawSelectionBoundingBox(box);if(outline){GL11.glLineWidth(1);net.minecraft.client.renderer.RenderGlobal.drawSelectionBoundingBox(box);}GL11.glPopAttrib();}
        public static class blur { public static void prepare(){} public static void apply(int passes,float radius){} } public static class bloom { public static void prepare(){} public static void apply(int passes,float radius){} }
    }

    public static class inventory {
        public static int getSlot(){return mc.thePlayer==null?0:mc.thePlayer.inventory.currentItem;} public static void setSlot(int slot){if(mc.thePlayer!=null&&slot>=0&&slot<9)mc.thePlayer.inventory.currentItem=slot;} public static void click(int slot,int button,int mode){if(mc.thePlayer!=null)mc.playerController.windowClick(mc.thePlayer.openContainer.windowId,slot,button,mode,mc.thePlayer);} public static List<String> getBookContents(){return new ArrayList<String>();} public static String getChest(){return mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiChest?"Chest":"";} public static String getContainer(){return mc.thePlayer==null?"":mc.thePlayer.openContainer.getClass().getSimpleName();} public static int getSize(){return mc.thePlayer==null?0:mc.thePlayer.inventoryContainer.inventorySlots.size();} public static int getChestSize(){return mc.thePlayer!=null&&mc.thePlayer.openContainer instanceof ContainerChest?((ContainerChest)mc.thePlayer.openContainer).getLowerChestInventory().getSizeInventory():0;} public static ItemStack getStackInSlot(int slot){return mc.thePlayer==null||slot<0||slot>=mc.thePlayer.inventoryContainer.inventorySlots.size()?null:ItemStack.convert(mc.thePlayer.inventoryContainer.getSlot(slot).getStack());} public static ItemStack getStackInChestSlot(int slot){return getStackInSlot(slot);} public static ItemStack getStackInCraftingSlot(int slot){return getStackInSlot(slot);} public static ItemStack getCraftResult(){return getStackInSlot(0);} public static void open(){mc.displayGuiScreen(new GuiInventory(mc.thePlayer));}
    }
    public static class keybinds {
        public static int[] getMousePosition(){ScaledResolution r=new ScaledResolution(mc);return new int[]{Mouse.getX()*r.getScaledWidth()/mc.displayWidth,r.getScaledHeight()-Mouse.getY()*r.getScaledHeight()/mc.displayHeight-1};}
        public static boolean isPressed(String key){int code=getKeyCode(key);return code>0&&Keyboard.isKeyDown(code);}
        public static void setPressed(String key,boolean value){int code=getKeyCode(key);if(code>0)KeyBinding.setKeyBindState(code,value);}
        public static void onTick(String key){int code=getKeyCode(key);if(code>0)KeyBinding.onTick(code);}
        /** Supports Raven's gameplay aliases such as "forward" and "jump" as well as LWJGL names. */
        public static int getKeyCode(String key){
            if(key==null)return Keyboard.KEY_NONE;
            String wanted=normalise(key);
            if(mc.gameSettings!=null&&mc.gameSettings.keyBindings!=null) for(KeyBinding binding:mc.gameSettings.keyBindings){
                String description=normalise(binding.getKeyDescription());
                if(wanted.equals(description)||wanted.equals(description.replace("key", ""))) return binding.getKeyCode();
            }
            int keyCode=Keyboard.getKeyIndex(key.toUpperCase(java.util.Locale.ROOT));
            return keyCode==Keyboard.KEY_NONE&&key.length()==1?Keyboard.getKeyIndex(key.toUpperCase(java.util.Locale.ROOT)):keyCode;
        }
        private static String normalise(String value){return value==null?"":value.toLowerCase(java.util.Locale.ROOT).replace("key.","").replaceAll("[^a-z0-9]","");}
        public static int getKeyIndex(String key){return getKeyCode(key);} public static boolean isMouseDown(int button){return Mouse.isButtonDown(button);} public static boolean isKeyDown(int key){return Keyboard.isKeyDown(key);} public static void rightClick(){if(mc.gameSettings.keyBindUseItem!=null)KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());} public static void leftClick(){if(mc.gameSettings.keyBindAttack!=null)KeyBinding.onTick(mc.gameSettings.keyBindAttack.getKeyCode());} public static int getScroll(){return Mouse.getDWheel();}
    }
    public static class util { public static String color(String message){return message==null?"":message.replace('&','\u00a7');} public static String color(Object message){return color(message==null?null:String.valueOf(message));} public static String strip(String value){return value==null?"":net.minecraft.util.StringUtils.stripControlCodes(value);} public static String strip(Object value){return value==null?"":net.minecraft.util.StringUtils.stripControlCodes(String.valueOf(value));} public static double round(double value,int decimals){double p=Math.pow(10,Math.max(0,decimals));return Math.round(value*p)/p;} public static int randomInt(int min,int max){return min+new Random().nextInt(Math.max(1,max-min+1));} public static double randomDouble(double min,double max){return min+new Random().nextDouble()*(max-min);} }
}
