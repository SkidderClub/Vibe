package dev.vibe.easter;

import dev.vibe.core.HolidayTransformer;
import dev.vibe.command.CommandManager;
import dev.vibe.path.PathTestFixture;
import java.io.*;
import java.net.JarURLConnection;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.util.BlockPos;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.Assert.*;

public class HolidayHooksTest {
    private static final String RENDERER="net.minecraft.client.renderer.EntityRenderer",HOOK="dev/vibe/easter/EasterEggs";
    @Test public void mappedAndActualReleaseWeatherBytecodeUseValidCosmeticHooks()throws Exception{
        verify(RENDERER,resource(RENDERER));
        Path path=Paths.get(((JarURLConnection)getClass().getClassLoader().getResource("net/minecraft/client/Minecraft.class").openConnection()).getJarFileURL().toURI());
        JarFile original=null;for(Path dir=path.getParent();dir!=null&&original==null;dir=dir.getParent())try(DirectoryStream<Path> jars=Files.newDirectoryStream(dir,"*merged+MinecraftForge-FG2+forge*-official.jar")){for(Path jar:jars){original=new JarFile(jar.toFile());break;}}
        assertNotNull(original);try(JarFile jar=original){verify("bfk",read(jar.getInputStream(jar.getJarEntry("bfk.class"))));}
    }
    private void verify(String name,byte[] bytes)throws Exception{
        HolidayTransformer transformer=new HolidayTransformer();byte[] result=transformer.transform(name,RENDERER,bytes);ClassNode node=new ClassNode();new ClassReader(result).accept(node,0);
        Map<String,Integer> hooks=new HashMap<>();
        for(Object entry:node.methods){MethodNode method=(MethodNode)entry;new Analyzer(new BasicVerifier()).analyze(node.name,method);
            for(AbstractInsnNode insn:method.instructions.toArray())if(insn instanceof MethodInsnNode&&((MethodInsnNode)insn).owner.equals(HOOK)){
                String hook=((MethodInsnNode)insn).name;hooks.put(hook,hooks.getOrDefault(hook,0)+1);
                assertTrue("Only precipitation rendering is changed",method.name.equals("renderRainSnow")||method.name.equals("addRainParticles")||method.name.equals("c")&&method.desc.equals("(F)V")||method.name.equals("o")&&method.desc.equals("()V"));
            }
        }
        assertEquals(Integer.valueOf(2),hooks.get("rainStrength"));assertEquals(Integer.valueOf(2),hooks.get("temperature"));
        assertEquals(Integer.valueOf(2),hooks.get("canRain"));assertEquals(Integer.valueOf(1),hooks.get("snowBiome"));
        assertArrayEquals(result,transformer.transform(name,RENDERER,result));
    }
    @Test public void christmasOverridesHotDryBiomesWithoutMutatingTheWorld()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            EasterEggs.INSTANCE.command("off");float realRain=f.world.getRainStrength(0);BlockPos pos=new BlockPos(0,64,0);
            assertFalse(EasterEggs.canRain(BiomeGenBase.desert));EasterEggs.INSTANCE.command("christmas");
            assertEquals(1,EasterEggs.rainStrength(f.world,0),0);assertEquals(-.5,EasterEggs.temperature(BiomeGenBase.desert,pos),0);
            assertTrue(EasterEggs.canRain(BiomeGenBase.desert));assertTrue(EasterEggs.snowBiome(BiomeGenBase.desert));assertEquals(realRain,f.world.getRainStrength(0),0);
            EasterEggs.INSTANCE.command("off");assertFalse(EasterEggs.canRain(BiomeGenBase.desert));assertEquals(realRain,EasterEggs.rainStrength(f.world,0),0);
        }
    }
    @Test public void eventRootIsHiddenAndOnlyExplicitEventArgumentsComplete()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            CommandManager commands=new CommandManager();
            for(String prefix:Arrays.asList(".",".e"))for(String suggestion:commands.getSuggestions(prefix))assertFalse(suggestion.equals(".e")||suggestion.startsWith(".e "));
            assertTrue(commands.getSuggestions(".e ").contains(".e halloween"));assertEquals(Collections.singletonList(".e christmas"),commands.getSuggestions(".e chr"));
            commands.execute(".help");for(String line:f.player.localMessages)assertFalse(line.contains(".e ")||line.contains(".easter"));
            int count=f.player.localMessages.size();commands.execute(".e off");assertEquals(count,f.player.localMessages.size());assertTrue(f.player.sentMessages.isEmpty());
        }
    }
    @Test public void effectsCannotExecuteProgramsOrTransmitNetworkPackets()throws Exception{
        for(String type:new String[]{"HolidayRenderer","ClientFireworks","EasterEggs","HolidayTimeline"}){
            ClassNode node=new ClassNode();new ClassReader(resource("dev.vibe.easter."+type)).accept(node,0);
            for(Object entry:node.methods)for(AbstractInsnNode insn:((MethodNode)entry).instructions.toArray())if(insn instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)insn;
                assertFalse(call.owner.equals("java/lang/ProcessBuilder")||call.owner.equals("java/lang/Runtime")&&call.name.equals("exec"));
                assertFalse(call.owner.startsWith("net/minecraft/network/"));assertFalse(call.owner.equals("java/awt/Frame")||call.owner.equals("javax/swing/JFrame")||call.owner.equals("java/awt/Desktop"));
            }
        }
    }
    private static byte[] resource(String name)throws IOException{return read(HolidayHooksTest.class.getClassLoader().getResourceAsStream(name.replace('.','/')+".class"));}
    private static byte[] read(InputStream input)throws IOException{try(InputStream source=input;ByteArrayOutputStream output=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int count;while((count=source.read(buffer))!=-1)output.write(buffer,0,count);return output.toByteArray();}}
}
