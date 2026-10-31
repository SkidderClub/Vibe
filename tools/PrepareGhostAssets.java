import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Deterministic alpha trimming, frame registration and icon sizing; never redraws the artwork. */
public final class PrepareGhostAssets {
    public static void main(String[] args)throws Exception{
        Path original=Paths.get(args[0]),cutout=Paths.get(args[1]),output=Paths.get(args[2]);Files.createDirectories(output);
        BufferedImage still=read(original),sheet=read(cutout),paint=args.length>3?read(Paths.get(args[3])):null;Rectangle bounds=bounds(still);if(bounds==null)throw new IOException("Empty mascot");
        BufferedImage cropped=still.getSubimage(bounds.x,bounds.y,bounds.width,bounds.height);write(fit(cropped,512,6),output.resolve("ghost-static.png"));
        for(int size:new int[]{16,32,48,256})write(fit(cropped,size,Math.max(1,size/32)),output.resolve("icon-"+size+".png"));
        if(sheet.getWidth()!=1536||sheet.getHeight()!=1024)throw new IOException("Unexpected sprite-sheet dimensions");
        BufferedImage[] frames=new BufferedImage[15];Rectangle union=null;int[] rows={0,300,600,940};
        for(int i=0;i<15;i++){int col=i%5,row=i/5,left=(int)Math.round(col*sheet.getWidth()/5D),right=(int)Math.round((col+1)*sheet.getWidth()/5D);frames[i]=new BufferedImage(308,340,BufferedImage.TYPE_INT_ARGB);Graphics2D g=frames[i].createGraphics();g.setComposite(AlphaComposite.Src);g.drawImage(paint==null?sheet:paint,0,0,right-left,rows[row+1]-rows[row],left,rows[row],right,rows[row+1],null);g.dispose();if(paint!=null){BufferedImage guide=new BufferedImage(308,340,BufferedImage.TYPE_INT_ARGB);Graphics2D mg=guide.createGraphics();mg.drawImage(sheet,0,0,right-left,rows[row+1]-rows[row],left,rows[row],right,rows[row+1],null);mg.dispose();frames[i]=extract(frames[i],guide);}Rectangle box=bounds(frames[i]);if(box==null||box.width<150||box.height<180)throw new IOException("Missing sprite "+i);union=union==null?box:union.union(box);}
        BufferedImage atlas=new BufferedImage(1024,1024,BufferedImage.TYPE_INT_ARGB);Graphics2D g=atlas.createGraphics();
        for(int i=0;i<frames.length;i++){BufferedImage common=new BufferedImage(union.width,union.height,BufferedImage.TYPE_INT_ARGB);Graphics2D cg=common.createGraphics();cg.drawImage(frames[i],-union.x,-union.y,null);cg.dispose();BufferedImage frame=fit(common,256,4);g.drawImage(frame,(i%4)*256,(i/4)*256,null);}g.dispose();write(atlas,output.resolve("ghost-animation.png"));
        Files.write(output.resolve("ghost-animation.properties"),("frames=15\ncolumns=4\nframeSize=256\nfps=12\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        System.out.println("Prepared trimmed mascot, 15 registered transparent frames and 16/32/48/256 pixel icons. Original bounds: "+bounds+"; common frame bounds: "+union);
    }
    private static BufferedImage read(Path path)throws IOException{BufferedImage image=ImageIO.read(path.toFile());if(image==null)throw new IOException("Not an image: "+path);return image;}
    private static BufferedImage extract(BufferedImage source,BufferedImage guide)throws IOException{
        int w=source.getWidth(),h=source.getHeight(),n=w*h;boolean[] outside=new boolean[n],body=new boolean[n],seen=new boolean[n];java.util.ArrayDeque<Integer> queue=new java.util.ArrayDeque<>();
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(x==0||y==0||x==w-1||y==h-1){int i=y*w+x;if(!outside[i]){outside[i]=true;queue.add(i);}}
        while(!queue.isEmpty()){int i=queue.remove(),x=i%w,y=i/w;for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){int xx=x+d[0],yy=y+d[1];if(xx<0||yy<0||xx>=w||yy>=h)continue;int j=yy*w+xx,p=source.getRGB(xx,yy),max=Math.max(p>>16&255,Math.max(p>>8&255,p&255));if(!outside[j]&&((p>>>24)==0||max>95&&!core(guide,xx,yy))){outside[j]=true;queue.add(j);}}}
        java.util.List<Integer> largest=new java.util.ArrayList<>();for(int i=0;i<n;i++)if(!outside[i]&&!seen[i]){java.util.List<Integer> component=new java.util.ArrayList<>();queue.add(i);seen[i]=true;while(!queue.isEmpty()){int j=queue.remove(),x=j%w,y=j/w;component.add(j);for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){int xx=x+dx,yy=y+dy;if(xx>=0&&yy>=0&&xx<w&&yy<h){int k=yy*w+xx;if(!outside[k]&&!seen[k]){seen[k]=true;queue.add(k);}}}}if(component.size()>largest.size())largest=component;}
        if(largest.size()<12000)throw new IOException("Sprite outline is not closed");for(int i:largest)body[i]=true;BufferedImage result=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=y*w+x,p=source.getRGB(x,y);boolean edge=false,near=false;for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){int xx=x+dx,yy=y+dy;if(xx>=0&&yy>=0&&xx<w&&yy<h){edge|=outside[yy*w+xx];near|=body[yy*w+xx];}}
            if(body[i]&&!edge){result.setRGB(x,y,p);continue;}if(!body[i]&&!near)continue;int light=Math.max(p>>16&255,Math.max(p>>8&255,p&255)),bg=150;for(int dy=-4;dy<=4;dy++)for(int dx=-4;dx<=4;dx++){int xx=x+dx,yy=y+dy;if(xx>=0&&yy>=0&&xx<w&&yy<h&&outside[yy*w+xx]){int q=source.getRGB(xx,yy);if((q>>>24)>0)bg=Math.max(bg,Math.min(q>>16&255,Math.min(q>>8&255,q&255)));}}
            int alpha=Math.max(0,Math.min(255,(int)Math.round((bg-light)/(double)(bg-8)*255)));if(alpha>0)result.setRGB(x,y,alpha<<24|0x080809);
        }return result;
    }
    private static boolean core(BufferedImage image,int x,int y){if(x<1||y<1||x>=image.getWidth()-1||y>=image.getHeight()-1)return false;for(int yy=y-1;yy<=y+1;yy++)for(int xx=x-1;xx<=x+1;xx++)if((image.getRGB(xx,yy)>>>24)<128)return false;return true;}
    private static Rectangle bounds(BufferedImage image){int left=image.getWidth(),top=image.getHeight(),right=-1,bottom=-1;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getRGB(x,y)>>>24)>8){left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x);bottom=Math.max(bottom,y);}return right<left?null:new Rectangle(left,top,right-left+1,bottom-top+1);}
    private static BufferedImage fit(BufferedImage source,int size,int padding){BufferedImage result=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB_PRE);Graphics2D g=result.createGraphics();g.setComposite(AlphaComposite.Src);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);double scale=Math.min((size-padding*2D)/source.getWidth(),(size-padding*2D)/source.getHeight());int w=(int)Math.round(source.getWidth()*scale),h=(int)Math.round(source.getHeight()*scale);g.drawImage(source,(size-w)/2,(size-h)/2,w,h,null);g.dispose();return result;}
    private static void write(BufferedImage image,Path path)throws IOException{BufferedImage straight=new BufferedImage(image.getWidth(),image.getHeight(),BufferedImage.TYPE_INT_ARGB);for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int pixel=image.getRGB(x,y);straight.setRGB(x,y,(pixel>>>24)==0?0:pixel);}ImageIO.write(straight,"png",path.toFile());}
}
