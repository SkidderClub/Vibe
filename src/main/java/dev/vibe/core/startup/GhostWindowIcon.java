package dev.vibe.core.startup;

import java.nio.ByteBuffer;
import org.lwjgl.opengl.Display;

public final class GhostWindowIcon {
    private GhostWindowIcon(){}
    public static boolean apply(){try{ByteBuffer[] sizes=new ByteBuffer[4];int i=0;for(int size:new int[]{16,32,48,256})sizes[i++]=GhostArt.rgba(GhostArt.image("icon-"+size+".png"));Display.setIcon(sizes);return true;}catch(Exception e){System.err.println("Vibe icon could not be applied: "+e.getClass().getSimpleName());return false;}}
}
