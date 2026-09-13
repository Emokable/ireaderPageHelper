package dev.pageh.helper;

import android.os.IBinder;
import java.lang.reflect.Method;

/** Root-side foreground and wakefulness check; no input interception or injection. */
public final class TouchFocus {
    private final Method activity,interactive;
    private final Object power;
    public TouchFocus() throws Exception {
        activity=Class.forName("android.eink.api.EinkAPI").getMethod("getFocusActivityName");
        Object binder=Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"power");
        power=Class.forName("android.os.IPowerManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
        interactive=Class.forName("android.os.IPowerManager").getMethod("isInteractive");
    }
    public boolean matches(TouchLease lease) {
        try { return Boolean.TRUE.equals(interactive.invoke(power)) && lease.matchesFocus((String)activity.invoke(null)); }
        catch(Exception unavailable) { return false; }
    }
}
