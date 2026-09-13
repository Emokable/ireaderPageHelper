package dev.pageh.helper;

/** Normalized touchscreen coordinates, with center taps reserved for the reader's menu. */
public final class TouchDecision {
    public static float[] rotate(float x,float y,int rotation) {
        switch(rotation) {
            case 0:return new float[]{x,y};
            case 1:return new float[]{y,1-x};
            case 2:return new float[]{1-x,1-y};
            case 3:return new float[]{1-y,x};
            default:throw new IllegalArgumentException("rotation");
        }
    }
    public static int direction(float startX,float startY,float endX,float endY,long duration) {
        if(duration<0 || duration>500) return 0;
        float dx=endX-startX,dy=endY-startY;
        if(Math.abs(dx)>.035f && Math.abs(dx)>Math.abs(dy)*1.5f) return dx<0?1:-1;
        if(Math.abs(dx)>.035f || Math.abs(dy)>.035f) return 0;
        return startX<.35f?-1:startX>.65f?1:0;
    }
}
