package dev.vibe.game.battlefront;

import java.util.*;

/** One geometry source for visible architecture, traversable floors and solid collision. */
public final class BattlefrontArchitecture {
    private BattlefrontArchitecture(){}
    public static final class Part {
        public final double x0,y0,z0,x1,y1,z1;public final int color,material;public final boolean solid,emissive;
        Part(double x,double y,double z,double X,double Y,double Z,int c,int m,boolean s,boolean e){x0=x;y0=y;z0=z;x1=X;y1=Y;z1=Z;color=c;material=m;solid=s;emissive=e;}
        public boolean overlaps(double x,double y,double z,double radius,double height){return x+radius>x0&&x-radius<x1&&z+radius>z0&&z-radius<z1&&y+height>y0+.015&&y<y1-.025;}
        public double ray(double x,double y,double z,double dx,double dy,double dz,double max){
            double near=0,far=max;
            for(int i=0;i<3;i++){double origin=i==0?x:i==1?y:z,direction=i==0?dx:i==1?dy:dz,lo=i==0?x0:i==1?y0:z0,hi=i==0?x1:i==1?y1:z1;
                if(Math.abs(direction)<1e-9){if(origin<lo||origin>hi)return max;}else{double a=(lo-origin)/direction,b=(hi-origin)/direction;near=Math.max(near,Math.min(a,b));far=Math.min(far,Math.max(a,b));if(far<near)return max;}}
            return near<=max?near:max;
        }
    }
    public static final class Building {
        public final String name,type;public final double x,z,w,d,floor;public double base;
        public final List<Part> parts=new ArrayList<Part>();
        public Building(String name,String type,double x,double z,double w,double d,double floor){this.name=name;this.type=type;this.x=x;this.z=z;this.w=w;this.d=d;this.floor=floor;}
        public double entranceX(){return x;}public double entranceZ(){return z+d+.8;}
        public double cacheX(){return x+w-2;}public double cacheZ(){return z-d+2.3;}public double cacheY(){return base+floor+.9;}
        public boolean contains(double px,double py,double pz){return Math.abs(px-x)<w+.6&&Math.abs(pz-z)<d+.6&&py>=base+floor-.3&&py<base+floor+(type.equals("factory")?10:5);}
        public double apron(double px,double pz){
            double dx=Math.max(0,Math.abs(px-x)-w-2),dz=Math.max(0,Math.abs(pz-z)-d-2);
            double distance=Math.max(dx,dz);
            if(type.equals("village"))distance=Math.min(distance,Math.max(Math.max(0,Math.abs(px-x)-3),Math.max(0,Math.abs(pz-z)-d-24)));
            return distance;
        }
        private void part(double x,double y,double z,double X,double Y,double Z,int c,int material,boolean solid,boolean light){parts.add(new Part(this.x+x,base+y,this.z+z,this.x+X,base+Y,this.z+Z,c,material,solid,light));}
        private void solid(double x,double y,double z,double X,double Y,double Z,int c){part(x,y,z,X,Y,Z,c,type.equals("village")?2:4,true,false);}
        private void detail(double x,double y,double z,double X,double Y,double Z,int c){part(x,y,z,X,Y,Z,c,type.equals("village")?2:4,false,false);}
        private void glow(double x,double y,double z,double X,double Y,double Z,int c){part(x,y,z,X,Y,Z,c,4,false,true);}
        public void build(double base){this.base=base;parts.clear();boolean wood=type.equals("village"),factory=type.equals("factory");double f=floor,h=f+(factory?8.5:4.2);int wall=wood?0x967C57:factory?0x96745D:0x7B8D89,trim=wood?0xB19A6F:0xA4B6B5;
            // Raised foundations are physically thick; trees use four structural stilts.
            if(wood){for(int sx:new int[]{-1,1})for(int sz:new int[]{-1,1})solid(sx*(w-1)-.3,0,sz*(d-1)-.3,sx*(w-1)+.3,f,sz*(d-1)+.3,0x64533E);}
            solid(-w,f-.3,-d,w,f,d,0x52686A);
            // Front doorway: 3.6m wide, 3.25m high. The back has a second usable exit.
            for(int face:new int[]{-1,1}){double zz=face*d;solid(-w,f,zz-.22,-1.8,h,zz+.22,wall);solid(1.8,f,zz-.22,w,h,zz+.22,wall);solid(-1.8,f+3.25,zz-.22,1.8,h,zz+.22,wall);
                detail(-2.03,f,zz-.27,-1.79,f+3.4,zz+.27,trim);detail(1.79,f,zz-.27,2.03,f+3.4,zz+.27,trim);glow(-1.7,f+3.15,zz-.28,1.7,f+3.22,zz+.28,wood?0xE6BA73:0x7FE6E8);}
            // Window apertures remain real openings between the sill and lintel.
            for(int face:new int[]{-1,1}){double xx=face*w;solid(xx-.23,f,-d,xx+.23,f+1.1,d,wall);solid(xx-.23,f+2.6,-d,xx+.23,h,d,wall);
                for(double zz=-d;zz<d;zz+=3.2)solid(xx-.24,f+1.1,zz,xx+.24,f+2.6,Math.min(d,zz+.5),trim);}
            solid(-w-.4,h,-d-.4,w+.4,h+.38,d+.4,wood?0x8A704C:0x87928A);
            for(double zz=-d;zz<=d;zz+=2.4){detail(-w,h-.3,zz-.12,w,h,zz+.12,trim);if(!wood)glow(-w+1,h-.33,zz-.04,w-1,h-.30,zz+.04,0xBDDCD9);}
            for(double xx=-w;xx<=w;xx+=2.5)detail(xx-.05,f+.008,-d,xx+.05,f+.018,d,wood?0x6D563D:0x879791);
            // Seamed wall panels, exterior ribs, door controls and exposed conduits.
            for(int face:new int[]{-1,1}){
                double xx=face*(w+.28);
                for(double zz=-d+1;zz<d;zz+=2.1){detail(xx-.055,f+.15,zz-.035,xx+.055,f+1.05,zz+.035,trim);detail(xx-.07,f+2.7,zz-.07,xx+.07,h-.1,zz+.07,trim);}
                double zz=face*(d+.29);
                detail(2.12,f+1.1,zz-.05,2.50,f+1.8,zz+.05,0x32484E);glow(2.18,f+1.3,zz-.06,2.44,f+1.65,zz+.06,wood?0xFFC784:0x86DCDD);
                for(int i=0;i<4;i++)detail(-w+.7+i*.45,f+.4,zz-.02,-w+.95+i*.45,f+.55,zz+.02,0xD3B875);
            }
            if(wood){
                // Overlapping pitched roof slats and crossed gable supports.
                for(int i=0;i<12;i++){double xx=-w-.65+i*(w*2+1.3)/12,roofY=h+.4+(1-Math.abs((i+.5)/6-1))*2.1;
                    detail(xx,roofY,-d-.7,xx+(w*2+1.3)/12+.08,roofY+.22,d+.7,i%2==0?0x9D8257:0xB29463);}
                for(int face:new int[]{-1,1})detail(-.10,f+3,face*d-.35,.10,h+2.4,face*d+.35,0x72593C);
            }else{
                for(int i=0;i<3;i++){double xx=-w+2+i*1.7;detail(xx,h+.39,-1,xx+1.2,h+.95,1,0x4C6065);for(int j=0;j<6;j++)detail(xx+.05,h+.96,-.85+j*.29,xx+1.15,h+.99,-.75+j*.29,0xA0ABA4);}
                for(int face:new int[]{-1,1}){double xx=face*(w-.45);detail(xx-.09,f+2.8,-d,xx+.09,f+2.97,d,0x465B61);}
            }
            // Both doors get an accessible staircase, including the elevated forest huts.
            int steps=wood?22:2;double rise=wood?f/steps:.12;
            for(int face:new int[]{-1,1})for(int i=0;i<steps;i++){
                double z0=face>0?d+i:d-i-1-2*d,z1=z0+1;
                double top=wood?f-i*rise:f-i*.12;
                solid(-1.65,Math.min(-.2,top-.25),z0,1.65,top,z1,wood?0x9D825B:0x87958D);
                if(wood){for(int sx:new int[]{-1,1})detail(sx*1.9-.07,top,z0,sx*1.9+.07,top+1.05,z1,0xB6A477);}
            }
            // Wall consoles, shelving, cable trays, work tables and luminaires.
            for(int face:new int[]{-1,1})for(int i=0;i<3;i++){
                double zz=-d+1.5+i*(d-.6);if(zz>d-1)continue;
                double xx=face*(w-1.1);solid(xx-.65,f,zz-.6,xx+.65,f+.9,zz+.6,wood?0x715943:0x475D63);
                detail(xx-.7,f+.9,zz-.65,xx+.7,f+1.02,zz+.65,trim);
                if(!wood){glow(xx-.5,f+1.03,zz-.4,xx+.5,f+1.055,zz+.3,i%2==0?0x78D8DC:0xE5B86C);detail(xx-.6,f+1.1,zz+.5,xx+.6,f+1.6,zz+.6,0x294651);glow(xx-.5,f+1.2,zz+.48,xx+.5,f+1.5,zz+.49,0x66B9C2);}
                else for(int j=0;j<3;j++)detail(xx-.5+j*.4,f+1.02,zz-.3,xx-.24+j*.4,f+1.3,zz+.2,0xB7A477);
            }
            if(factory){
                // Assembly conveyors flank a clear central route.
                for(int side:new int[]{-1,1}){solid(side*4-1.1,f,-2,side*4+1.1,f+1.1,3,0x4E6265);detail(side*4-1,f+1.1,-2,side*4+1,f+1.25,3,0x394B4D);for(int i=0;i<9;i++)detail(side*4-1,f+1.26,-1.8+i*.53,side*4+1,f+1.29,-1.65+i*.53,0xADB0A2);}
                solid(-w+.3,f+4.0,-d+.3,w-.3,f+4.25,-d+4,0x768C8E);
                double step=(2*d-5)/18;for(int i=0;i<18;i++){double zz=d-1-i*step;solid(-w+1,f,zz-step,-w+3,f+(i+1)*4.25/18,zz,0x849A9B);}
                for(int i=-3;i<=3;i++){if(i<-2)continue;detail(i*2-.04,f+4.25,-d+4,i*2+.04,f+5.25,-d+4.12,trim);}
                detail(-w+3,f+5.2,-d+4,w-.3,f+5.32,-d+4.15,trim);
                for(int i=-1;i<=1;i++){detail(i*4-.2,h-.8,-3,i*4+.2,h-.4,3,0xB3A18A);detail(i*4-.09,f+2.0,0,i*4+.09,h-.4,.12,0xA8AEA4);}
                for(int side:new int[]{-1,1}){detail(side*(w-1)-.3,h+.38,-d+1,side*(w-1)+.3,h+4.5,-d+2,0x756857);}
            }else if(type.equals("bunker")){
                // Control room with a central holotable, reactor alcoves and two side rooms.
                solid(-1.4,f,-2,1.4,f+.8,.2,0x53696E);glow(-1.25,f+.8,-1.9,1.25,f+.86,.1,0x66E5D6);
                for(int side:new int[]{-1,1}){double xx=side*(w-3.5);solid(xx-.15,f,-d,xx+.15,f+3.8,-2);}
            }else if(type.equals("power")){
                for(int side:new int[]{-1,1}){
                    double xx=side*(w-3.4);solid(xx-1.1,f,-3,xx+1.1,f+2.8,2,0x41545F);
                    for(int i=0;i<6;i++){detail(xx-1.16,f+.25+i*.4,-3.1,xx+1.16,f+.4+i*.4,2.1,0x869A9D);glow(xx-.7,f+.42+i*.4,-3.12,xx+.7,f+.49+i*.4,-3.11,0x72DADF);}
                    detail(xx-.5,f+2.8,-1.5,xx+.5,h-.1,-.5,0x586A71);
                }
            }else if(type.equals("armory")){
                for(int side:new int[]{-1,1}){
                    double xx=side*(w-2.8);solid(xx-.75,f,-3,xx+.75,f+.4,2.5,0x3B505A);
                    for(int i=0;i<5;i++){double zz=-2.5+i;detail(xx-.55,f+.4,zz-.12,xx+.55,f+1.85,zz+.12,0x2C3C45);detail(xx-.15,f+1.85,zz-.07,xx+.15,f+2.5,zz+.07,0xA0A9A7);glow(xx-.6,f+1.2,zz-.14,xx-.5,f+1.35,zz+.14,0x80CED5);}
                }
                detail(-1.3,f+.03,-d+1,1.3,f+.05,-d+2,0xB9A36A);
            }else if(type.equals("barracks")){
                for(int side:new int[]{-1,1})for(int i=0;i<3;i++){
                    double xx=side*(w-3),zz=-d+2+i*3.6;
                    for(int level=0;level<2;level++){double yy=f+.35+level*1.75;solid(xx-1,yy,zz-.8,xx+1,yy+.16,zz+.8,0x4F6264);detail(xx-.9,yy+.17,zz-.75,xx+.9,yy+.33,zz+.75,0x879081);detail(xx-.8,yy+.34,zz-.7,xx+.8,yy+.45,zz-.3,0xB7C1AF);}
                    for(int leg:new int[]{-1,1})solid(xx+leg*.94-.04,f,zz-.8,xx+leg*.94+.04,f+2.7,zz-.7,0x91A4A5);
                }
            }else if(type.equals("medical")){
                for(int side:new int[]{-1,1})for(int i=0;i<2;i++){double xx=side*(w-2.5),zz=-2+i*3;solid(xx-.9,f+.3,zz-1,xx+.9,f+.75,zz+1,0x4D6668);detail(xx-.85,f+.75,zz-.9,xx+.85,f+.92,zz+.9,0xC1CEC1);detail(xx-.7,f+.92,zz-.8,xx+.7,f+1.08,zz-.35,0x8AAEB0);}
            }else if(wood){
                solid(-w+1,f,0,-w+2.2,f+.55,2,0x796049);detail(-w+.9,f+.55,-.1,-w+2.3,f+.7,2.1,0xC2AA79);
                for(int i=0;i<5;i++)detail(w-2,f+.5+i*.43,-2,w-.4,f+.60+i*.43,-.8,0xBC9565);
                for(int side:new int[]{-1,1}){glow(side*(w-1)-.16,f+2.3,-d+1,side*(w-1)+.16,f+2.8,-d+1.3,0xFFBD62);}
            }else{
                solid(-1.8,f,-1,1.8,f+.95,1,0x617A7B);glow(-1.5,f+.96,-.75,1.5,f+1.0,.75,0x80CCC7);
            }
            // Searchable supply cache, placed inside every building.
            solid(w-2.55,f,-d+1.8,w-1.45,f+.8,-d+2.8,0x465D60);glow(w-2.4,f+.82,-d+2,w-1.6,f+.85,-d+2.6,0xA5EACA);
            detail(w-2.6,f+.15,-d+1.75,w-1.4,f+.23,-d+2.85,trim);detail(w-2.6,f+.62,-d+1.75,w-1.4,f+.7,-d+2.85,trim);
            // Small fixtures stay clear of the central route and staircases.
            for(int face:new int[]{-1,1}){double xx=face*(w-.5);
                for(int i=0;i<5;i++){double zz=-d+1+i*(2*d-2)/5;detail(xx-.12,f+2.75,zz-.25,xx+.12,f+3.08,zz+.25,wood?0x72543D:0x273C43);glow(xx-.13,f+2.83,zz-.16,xx+.13,f+2.96,zz+.16,wood?0xEDBC70:0x93CDD1);}
            }
        }
        private void solid(double x,double y,double z,double X,double Y,double Z){solid(x,y,z,X,Y,Z,0x738781);}
    }
}
