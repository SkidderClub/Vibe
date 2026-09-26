/* Gothaj counter rendering, revision 8aa9e1c056bc5b6393b29dc03bcb06b529b40a07. See LICENSES/GOTHAJ-SCAFFOLD.md. */
/*
 * Decompiled with CFR 0.152.
 */
package dev.vibe.ui.scaffold;

import java.awt.Color;

public interface IFontRenderer {
    public float getCharWidthFloat(char var1);

    public float getStringWidth(String var1);

    public int drawStringWithShadow(String var1, float var2, float var3, int var4);

    public void drawStringWithShadow(String var1, float var2, float var3, Color var4);

    public int drawCenteredString(String var1, float var2, float var3, int var4);

    public void drawCenteredString(String var1, float var2, float var3, Color var4);

    public void drawCenteredStringWithShadow(String var1, float var2, float var3, int var4);

    public String trimStringToWidth(String var1, int var2);

    public String trimStringToWidth(String var1, int var2, boolean var3);

    public int drawString(String var1, float var2, float var3, int var4, boolean var5);

    public void drawString(String var1, float var2, float var3, Color var4);

    public int drawString(String var1, float var2, float var3, int var4);

    public float getMiddleOfBox(float var1);

    public int getHeight();
}

