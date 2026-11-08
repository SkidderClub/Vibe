package dev.vibe.companion;

import java.awt.image.BufferedImage;

/** Native skin UVs at double resolution, with an adult face and two fully clothed outfits. */
public final class CompanionSkin {
    private CompanionSkin(){}
    public static BufferedImage create(int hair,int outfit,int accent,int eyes){
        return create(hair,outfit,accent,eyes,true);
    }
    public static BufferedImage create(int hair,int outfit,int accent,int eyes,boolean chic){
        return create(hair,outfit,accent,eyes,chic?"Chic":"Casual");
    }
    public static BufferedImage create(int hair,int outfit,int accent,int eyes,String style){
        boolean chic=!"Casual".equals(style),goth="Goth".equals(style);
        BufferedImage skin=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);int face=0xFFF0C5AE,denim=0xFF34485B,shoe=0xFFE4E5DB;
        fill(skin,0,0,32,16,hair);fill(skin,32,0,32,16,0);fill(skin,0,16,56,16,outfit);fill(skin,16,48,32,16,outfit);
        // Base head faces. Keep a shaped fringe and shaded cheek/ear edges.
        for(int x=0;x<32;x++)for(int y=8;y<16;y++)skin.setRGB(x,y,face);
        fill(skin,0,8,8,8,shade(hair,.84F));fill(skin,16,8,16,8,shade(hair,.92F));
        // The top face is UV (8,0)-(16,8). Row seven used to be incorrectly painted as skin.
        fill(skin,8,0,8,8,hair);
        for(int x=8;x<16;x++){fill(skin,x,8,1,x<11?3:x<14?2:4,hair);skin.setRGB(x,15,shade(face,.92F));}
        fill(skin,9,11,2,2,0xFFF9F9EF);fill(skin,13,11,2,2,0xFFF9F9EF);skin.setRGB(10,12,eyes);skin.setRGB(13,12,eyes);skin.setRGB(10,11,shade(hair,.65F));skin.setRGB(13,11,shade(hair,.65F));
        fill(skin,11,14,2,1,0xFFAE7978);skin.setRGB(8,13,0xFFEAB09E);skin.setRGB(15,13,0xFFEAB09E);
        // Hair cap uses transparent outer face except for the fringe and back.
        fill(skin,32,0,32,16,hair);fill(skin,32,8,8,8,shade(hair,.84F));fill(skin,48,8,16,8,shade(hair,.9F));
        for(int x=33;x<64;x+=3)fill(skin,x,1,1,7,shade(hair,1.12F));
        // Hoodie front: zipper, drawstrings, a front pocket, contrasting hem/cuffs.
        fill(skin,16,16,24,16,outfit);fill(skin,20,20,8,1,shade(outfit,.82F));fill(skin,23,21,1,10,shade(accent,.85F));fill(skin,21,21,1,3,accent);fill(skin,26,21,1,3,accent);fill(skin,21,27,6,3,shade(outfit,.83F));fill(skin,20,31,8,1,shade(outfit,.75F));
        fill(skin,40,16,16,16,outfit);fill(skin,32,48,16,16,outfit);fill(skin,40,28,16,4,face);fill(skin,32,60,16,4,face);fill(skin,40,27,16,1,accent);fill(skin,32,59,16,1,accent);
        // Both trouser legs and shoes, with edge shading and small lace highlights.
        for(int[] region:new int[][]{{0,16},{16,48}}){int x=region[0],y=region[1];fill(skin,x,y,16,16,denim);fill(skin,x+4,y+4,1,8,shade(denim,1.2F));fill(skin,x,y+12,16,4,shoe);fill(skin,x+5,y+13,2,1,shade(shoe,.6F));fill(skin,x,y+15,16,1,shade(shoe,.78F));}
        // Matching native outer layers, with transparent faces to avoid doubling the skin.
        fill(skin,16,32,24,16,0);fill(skin,40,32,16,16,0);fill(skin,48,48,16,16,0);fill(skin,0,32,16,16,0);fill(skin,0,48,16,16,0);
        // Small custom feature UVs in unused head-top texels.
        fill(skin,0,0,4,3,0xFF985E61);fill(skin,4,0,4,4,accent);fill(skin,0,4,4,4,face);
        if(chic)chic(skin,outfit,accent,face);
        if(goth)goth(skin,outfit,accent,face);
        int garment=goth?dev.vibe.ui.RenderUtils.blend(0xFF27212F,outfit,.1F):chic?0xFF292832:outfit;
        fill(skin,48,32,8,8,garment);fill(skin,49,34,1,3,shade(garment,1.25F));fill(skin,54,34,1,3,shade(garment,1.25F));
        fill(skin,48,40,12,6,0xFF201C27);fill(skin,48,46,4,2,0xFFCBC3D5);
        BufferedImage detailed=new BufferedImage(128,128,BufferedImage.TYPE_INT_ARGB);
        for(int x=0;x<128;x++)for(int y=0;y<128;y++)detailed.setRGB(x,y,skin.getRGB(x/2,y/2));
        face(detailed,hair,eyes,face,goth);
        return detailed;
    }
    private static void goth(BufferedImage skin,int outfit,int accent,int face){
        int fabric=dev.vibe.ui.RenderUtils.blend(0xFF201C29,outfit,.16F),leather=dev.vibe.ui.RenderUtils.blend(0xFF17151E,outfit,.09F),metal=0xFFBBB6C8,trim=shade(accent,.65F);
        fill(skin,16,16,24,16,fabric);fill(skin,20,20,2,7,leather);fill(skin,26,20,2,7,leather);fill(skin,32,20,8,7,leather);
        fill(skin,23,20,2,2,face);skin.setRGB(24,21,metal);
        for(int y=23;y<27;y++){skin.setRGB(y%2==0?23:24,y,trim);skin.setRGB(22,y,shade(metal,.7F));skin.setRGB(25,y,shade(metal,.7F));}
        fill(skin,17,27,20,2,face);fill(skin,17,29,20,3,leather);fill(skin,23,29,2,1,metal);
        fill(skin,16,32,28,16,leather);for(int x=18;x<43;x+=3)fill(skin,x,37,1,10,shade(fabric,1.3F));fill(skin,16,43,28,1,shade(trim,.65F));
        for(int i=0;i<6;i++)skin.setRGB(25+i,37+i%3,metal);
        for(int[] region:new int[][]{{0,16},{16,48}}){int x=region[0],y=region[1];fill(skin,x,y,16,16,face);fill(skin,x,y+8,16,8,leather);fill(skin,x,y+8,16,1,trim);for(int row=9;row<15;row++){skin.setRGB(x+5+(row%2),y+row,shade(metal,.75F));}skin.setRGB(x+7,y+9,metal);fill(skin,x,y+15,16,1,shade(leather,.7F));}
        fill(skin,0,32,16,16,leather);fill(skin,0,36,16,1,trim);
        fill(skin,40,16,16,12,leather);fill(skin,32,48,16,12,leather);fill(skin,40,27,16,1,trim);fill(skin,32,59,16,1,trim);
        fill(skin,40,28,16,3,fabric);fill(skin,32,60,16,3,fabric);
    }
    private static void chic(BufferedImage skin,int outfit,int accent,int face){
        int dark=0xFF292832,boot=0xFF24232C,metal=0xFFD8BA82,skirt=shade(outfit,.56F);
        fill(skin,16,16,24,16,dark);
        // Open cropped jacket around a fitted top; a narrow waist and a small midriff.
        fill(skin,20,20,2,7,outfit);fill(skin,26,20,2,7,outfit);fill(skin,32,20,8,7,outfit);fill(skin,20,26,8,1,shade(outfit,.8F));
        fill(skin,23,20,2,1,face);fill(skin,23,21,2,1,shade(face,.95F));skin.setRGB(24,22,metal);
        fill(skin,17,27,20,2,face);fill(skin,17,29,20,3,skirt);fill(skin,20,29,8,1,boot);fill(skin,23,29,2,1,metal);
        // Pleated skirt UVs occupy the otherwise unused torso outer-layer region.
        fill(skin,16,32,28,16,skirt);for(int x=18;x<43;x+=3)fill(skin,x,37,1,10,shade(skirt,1.2F));fill(skin,16,46,28,1,accent);
        // Tall dark boots with contrasting cuffs, seams and small metal fastenings.
        for(int[] region:new int[][]{{0,16},{16,48}}){int x=region[0],y=region[1];fill(skin,x,y,16,16,face);fill(skin,x,y+8,16,8,boot);fill(skin,x,y+8,16,1,shade(accent,.7F));fill(skin,x+5,y+10,1,4,shade(boot,1.4F));skin.setRGB(x+7,y+9,metal);fill(skin,x,y+15,16,1,shade(boot,.7F));}
        fill(skin,0,32,16,16,boot);fill(skin,0,36,16,1,shade(accent,.7F));
        // Refined jacket lapels and cuff details, retaining configurable outfit colors.
        fill(skin,40,16,16,12,outfit);fill(skin,32,48,16,12,outfit);fill(skin,40,27,16,1,accent);fill(skin,32,59,16,1,accent);
    }
    private static void face(BufferedImage skin,int hair,int eyes,int tone,boolean goth){
        int x=16,y=16;fill(skin,x,y,16,16,tone);fill(skin,x,y+15,16,1,shade(tone,.94F));
        fill(skin,x,y+8,1,7,shade(tone,.95F));fill(skin,x+15,y+8,1,7,shade(tone,.95F));
        // Eyebrows, fine upper lashes, iris shading and small catchlights.
        int liner=goth?0xFF25202C:shade(hair,.52F);fill(skin,x+2,y+5,4,1,shade(hair,.8F));fill(skin,x+10,y+5,4,1,shade(hair,.8F));
        for(int eye:new int[]{2,10}){
            fill(skin,x+eye,y+7,4,2,0xFFF6EDE4);fill(skin,x+eye+1,y+7,2,2,eyes);skin.setRGB(x+eye+1,y+8,shade(eyes,.44F));skin.setRGB(x+eye+2,y+7,0xFFFDF7EC);fill(skin,x+eye,y+6,4,1,liner);
        }
        skin.setRGB(x+1,y+6,liner);skin.setRGB(x+14,y+6,liner);
        fill(skin,x+7,y+10,2,1,shade(tone,.94F));fill(skin,x+2,y+10,2,1,0xFFE7B09F);fill(skin,x+12,y+10,2,1,0xFFE7B09F);
        fill(skin,x+6,y+12,4,1,goth?0xFF81465E:0xFFAE6D78);fill(skin,x+7,y+13,2,1,goth?0xFFA6667C:0xFFD29498);
        // Continuous side-swept hairline: no central rectangular patch or empty forehead.
        int[] fringe={6,5,4,3,3,2,2,2,3,3,3,4,5,6,8,10};
        for(int i=0;i<16;i++)for(int row=0;row<fringe[i];row++)skin.setRGB(x+i,y+row,shade(hair,i%4==1?1.08F:row==fringe[i]-1?.94F:1));
    }
    private static void fill(BufferedImage image,int x,int y,int w,int h,int c){for(int xx=x;xx<x+w;xx++)for(int yy=y;yy<y+h;yy++)image.setRGB(xx,yy,c);}
    private static int shade(int color,float factor){return color&0xFF000000|Math.min(255,Math.round((color>>16&255)*factor))<<16|Math.min(255,Math.round((color>>8&255)*factor))<<8|Math.min(255,Math.round((color&255)*factor));}
}
