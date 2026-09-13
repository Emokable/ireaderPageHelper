import dev.pageh.helper.PageKeys;
import dev.pageh.helper.TouchDecision;

public final class InputLogicTest {
    private static int checks;
    public static void main(String[] args) {
        for(int key:new int[]{24,25,92,93}) {
            boolean next=key==25||key==93;
            require(PageKeys.forward(key,false)==next,"normal logical direction");
            require(PageKeys.forward(key,true)!=next,"swapped logical direction");
            require(PageKeys.output(key,PageKeys.forward(key,false))==key,"unchanged physical output");
            int opposite=key==24?25:key==25?24:key==92?93:92;
            require(PageKeys.output(key,PageKeys.forward(key,true))==opposite,"swap changes actual output key");
        }
        require(TouchDecision.direction(.8f,.5f,.8f,.5f,100)==1,"right tap");
        require(TouchDecision.direction(.2f,.5f,.2f,.5f,100)==-1,"left tap");
        require(TouchDecision.direction(.5f,.5f,.5f,.5f,100)==0,"center menu excluded");
        require(TouchDecision.direction(.8f,.5f,.8f,.5f,700)==0,"long press excluded");
        require(TouchDecision.direction(.8f,.3f,.8f,.7f,200)==0,"vertical selection excluded");
        require(TouchDecision.direction(.5f,.5f,.2f,.5f,200)==1,"full-screen swipe left");
        require(TouchDecision.direction(.5f,.5f,.8f,.5f,200)==-1,"full-screen swipe right");
        float[] p=TouchDecision.rotate(.2f,.3f,1);
        require(Math.abs(p[0]-.3f)<.001 && Math.abs(p[1]-.8f)<.001,"landscape transform");
        try { PageKeys.output(3,true); throw new AssertionError("arbitrary key accepted"); } catch(IllegalArgumentException expected) {}
        System.out.println("PASS: "+checks+" key remapping and touch classification cases");
    }
    private static void require(boolean condition,String label) { checks++; if(!condition) throw new AssertionError(label); }
}
