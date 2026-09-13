import dev.pageh.helper.TouchLease;
import dev.pageh.helper.TouchDecision;
public final class TouchLeaseTest {
    static int checks;
    static void check(boolean ok,String label) { checks++; if(!ok) throw new AssertionError(label); }
    public static void main(String[] args) {
        int[] bounds={7000,4000,8000,6000};
        TouchLease lease=new TouchLease("com.reader","com.reader.Reader",65,66,0,bounds,1000);
        check(lease.allows(.9f,.5f,999),"valid region");
        check(!lease.allows(.9f,.5f,1000),"expired at boundary");
        check(!lease.allows(.75f,.5f,900),"comment excluded");
        check(!lease.allows(.7f,.4f,900),"comment boundary excluded");
        check(!lease.allows(.9f,.01f,900),"top excluded");
        check(!lease.allows(.9f,.99f,900),"bottom excluded");
        check(!lease.allows(Float.NaN,.5f,900),"invalid coordinates");
        bounds[0]=0;
        check(lease.allows(.2f,.5f,900),"defensive region copy");
        check(lease.matchesFocus("ActivityRecord{x u0 com.reader/.Reader t1}"),"short activity form");
        check(lease.matchesFocus("ActivityRecord{x u0 com.reader/com.reader.Reader t1}"),"full activity form");
        check(!lease.matchesFocus("ActivityRecord{x u0 com.reader/.ReaderOther t1}"),"different activity");
        check(!lease.matchesFocus("ActivityRecord{x u0 com.other/.Reader t1}"),"different package");
        check(!lease.matchesFocus(null),"no foreground");
        check(lease.sameTarget(new TouchLease("com.reader","com.reader.Reader",129,130,0,new int[0],1200)),"renewal target");
        check(!lease.sameTarget(new TouchLease("com.reader","com.reader.Reader",65,66,1,new int[0],1200)),"rotation invalidates");
        for(long duration:new long[]{12,26,40,54,100})
            check(TouchDecision.direction(.8f,.5f,.8f,.5f,duration)==1,"short tap "+duration);
        check(TouchDecision.direction(.8f,.5f,.8f,.5f,501)==0,"long hold still excluded");
        boolean rejected=false;
        try { new TouchLease("com.reader","com.reader.Reader",65,66,0,new int[]{500,0,0,0},1000); }
        catch(IllegalArgumentException expected) { rejected=true; }
        check(rejected,"invalid rectangle rejected");
        System.out.println("PASS: "+checks+" touch lease and short tap checks");
    }
}
