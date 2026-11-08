package dev.vibe.teleport;

import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import com.luciad.imageio.webp.WebPImageReaderSpi;

/** Circular layers from the supplied packed atlas; unrelated streak sprites are never sampled. */
public final class PortalSprite {
    public static final int[][] REGIONS={{1,1,277,277},{296,283,241,246},{2,543,266,244}};
    public static final String RESOURCE="/assets/vibe/teleport/portal.webp";
    private PortalSprite(){}
    public static BufferedImage read()throws IOException{
        try(InputStream raw=PortalSprite.class.getResourceAsStream(RESOURCE)){
            if(raw==null)throw new FileNotFoundException(RESOURCE);
            ImageReader reader=new WebPImageReaderSpi().createReaderInstance();
            try(MemoryCacheImageInputStream input=new MemoryCacheImageInputStream(raw)){reader.setInput(input);BufferedImage image=reader.read(0);if(image==null)throw new IOException("Portal atlas could not be decoded");return image;}finally{reader.dispose();}
        }
    }
}
