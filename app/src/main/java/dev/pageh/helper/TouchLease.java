package dev.pageh.helper;

/** Immutable short-lived permission for passive touch; coordinates are normalized to 0..10000. */
public final class TouchLease {
    public final String pkg,screen;
    public final int next,prev,rotation;
    public final long expires;
    private final int[] blocked;
    public TouchLease(String pkg,String screen,int next,int prev,int rotation,int[] blocked,long expires) {
        if(pkg==null || screen==null || !pkg.matches("[A-Za-z0-9_.]+") || !screen.matches("[A-Za-z0-9_.$]+"))
            throw new IllegalArgumentException("component");
        if(rotation<0 || rotation>3 || blocked==null || blocked.length%4!=0 || blocked.length>256)
            throw new IllegalArgumentException("regions");
        valid(next); valid(prev);
        for(int v:blocked) if(v<0||v>10000) throw new IllegalArgumentException("bounds");
        for(int i=0;i<blocked.length;i+=4) if(blocked[i]>blocked[i+2]||blocked[i+1]>blocked[i+3])
            throw new IllegalArgumentException("inverted bounds");
        this.pkg=pkg; this.screen=screen; this.next=next; this.prev=prev; this.rotation=rotation;
        this.blocked=blocked.clone(); this.expires=expires;
    }
    private static void valid(int effect) {
        int d=effect&7,s=effect&~7;
        if(d<1||d>4||(s!=0&&s!=64&&s!=128)) throw new IllegalArgumentException("effect");
    }
    public boolean allows(float x,float y,long now) {
        if(now>=expires || !Float.isFinite(x) || !Float.isFinite(y) || x<0||x>1||y<.06f||y>.93f) return false;
        int px=(int)(x*10000),py=(int)(y*10000);
        for(int i=0;i<blocked.length;i+=4)
            if(px>=blocked[i]&&px<=blocked[i+2]&&py>=blocked[i+1]&&py<=blocked[i+3]) return false;
        return true;
    }
    public boolean matchesFocus(String focus) {
        if(focus==null) return false;
        if(focus.contains(pkg+"/"+screen+" ")) return true;
        return screen.startsWith(pkg+".") && focus.contains(pkg+"/"+screen.substring(pkg.length())+" ");
    }
    public boolean sameTarget(TouchLease other) {
        return other!=null && pkg.equals(other.pkg)&&screen.equals(other.screen)&&rotation==other.rotation;
    }
}
