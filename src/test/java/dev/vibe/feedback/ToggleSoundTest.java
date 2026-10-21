package dev.vibe.feedback;

import dev.vibe.module.impl.client.ToggleSoundModule;
import net.minecraft.client.audio.ISound;
import org.junit.Test;
import paulscode.sound.SoundBuffer;
import paulscode.sound.codecs.CodecJOrbis;
import static org.junit.Assert.*;

public class ToggleSoundTest {
    @Test public void allBundledOnOffSoundsDecodeWithTheGameCodecAndRespectVolume() throws Exception {
        ToggleSoundModule module = new ToggleSoundModule(); module.volume.setValue(42D);
        for (String style : module.style.getModes()) {
            module.style.setValue(style);
            for (boolean enabled : new boolean[]{true, false}) {
                ISound sound = module.sound(enabled);
                assertEquals(.42F, sound.getVolume(), .0001F);
                assertEquals(ISound.AttenuationType.NONE, sound.getAttenuationType()); assertFalse(sound.canRepeat());
                String event = sound.getSoundLocation().getResourcePath();
                String path = "/assets/vibe/sounds/" + event.replace('.', '/') + ".ogg";
                path = path.replace("/on.ogg", "_on.ogg").replace("/off.ogg", "_off.ogg");
                java.net.URL url = getClass().getResource(path); assertNotNull(path, url);
                CodecJOrbis codec = new CodecJOrbis();
                try {
                    assertTrue(path, codec.initialize(url));
                    SoundBuffer decoded = codec.readAll(); assertNotNull(path, decoded);
                    assertEquals(1, decoded.audioFormat.getChannels());
                    assertTrue(decoded.audioData.length > 4000); assertTrue(decoded.audioData.length < 30000);
                } finally { codec.cleanup(); }
            }
        }
    }
}
