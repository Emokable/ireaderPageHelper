package dev.pageh.helper;

import java.lang.reflect.Method;

public final class Commands {
    private Method post;
    private void load() throws Exception {
        if (post == null) post = Class.forName("android.eink.EPDCDevice")
                .getMethod("nativePostCommand", String.class, String[].class);
    }
    private int send(String command, String[] reply) throws Exception {
        load();
        return ((Integer) post.invoke(null, command, reply)).intValue();
    }
    public synchronized String check() throws Exception {
        String[] reply = new String[1];
        int rc = send("epdc-default-mode", reply);
        if (rc != 0 || reply[0] == null) throw new IllegalStateException("default rc=" + rc);
        return reply[0];
    }
    public synchronized void prepare(int effect) throws Exception {
        int speed = effect & ~7;
        int direction = effect & 7;
        if (direction < 1 || direction > 4 || (speed != 0 && speed != 64 && speed != 128))
            throw new IllegalArgumentException("effect");
        int rc = send("next-effect-type " + effect, null);
        if (rc != 0) throw new IllegalStateException("direction rc=" + rc);
        rc = send("epdc-force-next-post-mode 1000063", null);
        if (rc != 0) throw new IllegalStateException("prepare rc=" + rc);
    }
}
