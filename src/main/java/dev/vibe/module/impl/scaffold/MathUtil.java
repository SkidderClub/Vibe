/*
 * Ported from Gothaj (CFR 0.152), revision 8aa9e1c056bc5b6393b29dc03bcb06b529b40a07.
 * Source mapping and Forge adaptations: LICENSES/GOTHAJ-SCAFFOLD.md.
 */
package dev.vibe.module.impl.scaffold;



public final class MathUtil {

    public static int getRandomNumber(int min, int max) {
        return (int)(Math.random() * (double)(max - min) + (double)min);
    }
}
