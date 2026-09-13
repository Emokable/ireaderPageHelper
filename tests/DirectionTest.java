import dev.pageh.helper.Direction;

public final class DirectionTest {
    public static void main(String[] args) {
        int[] next={1,4,2,3}, prev={2,3,1,4};
        int cases=0;
        for(int r=0;r<4;r++) for(int speed:new int[]{0,64,128}) {
            require(Direction.effect(true,false,r,speed)==(next[r]|speed));
            require(Direction.effect(false,false,r,speed)==(prev[r]|speed));
            require(Direction.effect(true,true,r,speed)==(prev[r]|speed));
            require(Direction.effect(false,true,r,speed)==(next[r]|speed)); cases+=4;
        }
        try { Direction.effect(true,false,4,64); throw new AssertionError("rotation accepted"); } catch(IllegalArgumentException expected) {}
        try { Direction.effect(true,false,0,32); throw new AssertionError("speed accepted"); } catch(IllegalArgumentException expected) {}
        System.out.println("PASS: "+cases+" direction/speed/reversal cases and invalid inputs");
    }
    private static void require(boolean condition) { if(!condition) throw new AssertionError(); }
}
