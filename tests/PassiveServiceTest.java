import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Source-level guards: complementary to logic tests, not a device/animation test. */
public final class PassiveServiceTest {
    private static int checks;
    private static String read(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("app/src/main/java/dev/pageh/helper/"+name+".java")),StandardCharsets.UTF_8);
    }
    private static void check(boolean value,String name) {
        checks++; if(!value) throw new AssertionError(name);
    }
    public static void main(String[] args) throws Exception {
        String service=read("PageService"),profiles=read("Profiles"),root=read("RootDaemon"),ui=read("MainActivity");
        String key=service.substring(service.indexOf("boolean onKeyEvent"),service.indexOf("private void observeTouch"));
        check(!key.contains("return true"),"key handler must never consume input");
        check(!service.contains("bridge.turn("),"no replacement keys");
        check(!service.contains("bridge.swipe("),"no replacement swipes");
        check(!service.contains("capturedKeys"),"no captured key pairs");
        check(!service.contains("boolean fault"),"no shared fault blocking touch");
        check(!profiles.contains(".managed"),"old managed preference ignored");
        check(!profiles.contains(".swap"),"old swap preference ignored");
        check(!profiles.contains(".swipe"),"old Duokan preference ignored");
        check(!root.contains("InputInjector"),"root has no injector");
        check(!root.contains("op == 2")&&!root.contains("op == 6"),"injection RPCs removed");
        check(!ui.contains("CheckBox managed")&&!ui.contains("CheckBox swap"),"UI options removed");
        check(service.contains("if(touchClient!=null) touchClient.close();"),"old touch client closed on reconnect");
        System.out.println("PASS: "+checks+" passive input source guards (not runtime tests)");
    }
}
