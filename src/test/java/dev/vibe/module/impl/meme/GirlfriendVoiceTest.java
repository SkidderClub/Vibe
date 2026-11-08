package dev.vibe.module.impl.meme;

import dev.vibe.path.PathTestFixture;
import dev.vibe.companion.CompanionSkin;
import com.google.gson.*;
import java.io.*;
import net.minecraft.client.audio.ISound;
import org.junit.Test;
import static org.junit.Assert.*;

public class GirlfriendVoiceTest {
    @Test public void voiceFollowsTheModelsMouthAndStopsOnDisable()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            GirlfriendModule m=new GirlfriendModule();f.modules.registerDynamic(m);m.setEnabled(true);m.follower().active=true;m.follower().x=3;m.follower().y=64;m.follower().z=-2;
            PathTestFixture.set(GirlfriendModule.class,m,"companionWorld",f.world);
            GirlfriendModule.Voice voice=m.new Voice("dying");assertEquals(ISound.AttenuationType.LINEAR,voice.getAttenuationType());assertEquals(3,voice.getXPosF(),0);assertEquals(64+1.55*.95,voice.getYPosF(),.001);
            m.follower().x=5;m.volume.setValue(40D);voice.update();assertEquals(5,voice.getXPosF(),0);assertEquals(.4,voice.getVolume(),.0001);m.setEnabled(false);voice.update();assertTrue(voice.isDonePlaying());
        }
    }
    @Test public void everySpatialClipExistsAndIsMonoVorbis()throws Exception{
        JsonObject root=new JsonParser().parse(new InputStreamReader(getClass().getResourceAsStream("/assets/vibe/sounds.json"),"UTF-8")).getAsJsonObject();int groups=0,clips=0;
        for(java.util.Map.Entry<String,JsonElement> group:root.entrySet())if(group.getKey().startsWith("girlfriend.")){
            groups++;assertEquals("player",group.getValue().getAsJsonObject().get("category").getAsString());
            for(JsonElement value:group.getValue().getAsJsonObject().getAsJsonArray("sounds")){JsonObject clip=value.getAsJsonObject();assertTrue(clip.get("stream").getAsBoolean());String path="/assets/vibe/sounds/"+clip.get("name").getAsString().substring(5)+".ogg";
                try(InputStream in=getClass().getResourceAsStream(path)){assertNotNull(path,in);byte[] header=new byte[128];assertTrue(in.read(header)>48);assertEquals('O',header[0]);assertEquals('g',header[1]);int signature=-1;for(int i=0;i<header.length-11;i++)if(header[i]=='v'&&header[i+1]=='o'&&header[i+2]=='r'&&header[i+3]=='b'&&header[i+4]=='i'&&header[i+5]=='s'){signature=i;break;}assertTrue(signature>0);assertEquals(1,header[signature+10]);}clips++;
            }
        }assertEquals(7,groups);assertEquals(19,clips);
        java.awt.image.BufferedImage skin=CompanionSkin.create(0xFF553322,0xFF447788,0xFFDDCCEE,0xFF55AA99);assertEquals(128,skin.getWidth());int eyes=0;
        for(int x=16;x<32;x++)for(int y=16;y<32;y++){assertEquals(255,skin.getRGB(x,y)>>>24);if(skin.getRGB(x,y)==0xFF55AA99)eyes++;}assertTrue(eyes>=4);
        assertNotEquals(skin.getRGB(40,52),CompanionSkin.create(0xFF553322,0xFF447788,0xFFDDCCEE,0xFF55AA99,false).getRGB(40,52));
        for(String style:new String[]{"Goth","Chic","Casual"}){
            java.awt.image.BufferedImage styled=CompanionSkin.create(0xFF553322,0xFF447788,0xFFDDCCEE,0xFF55AA99,style);
            // Native head-top UVs include the last row which previously exposed face-colored pixels.
            for(int x=16;x<32;x++)for(int y=0;y<16;y++)assertEquals(style+" scalp",0xFF553322,styled.getRGB(x,y));
        }
    }
}
