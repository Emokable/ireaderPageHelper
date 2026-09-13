package dev.pageh.helper;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.io.DataInputStream;
import java.io.DataOutputStream;

public final class Bridge {
    private final Commands direct = new Commands();
    private String backend = "未检测";
    private boolean touchReady;
    public synchronized boolean canTouch() { return backend.equals("ADB 高权限") && touchReady; }
    public synchronized String backend() { return backend; }
    private String remote(int op, int effect) throws Exception {
        try (LocalSocket socket = new LocalSocket()) {
            socket.connect(new LocalSocketAddress(RootDaemon.SOCKET));
            socket.setSoTimeout(180);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeInt(op);
            if (op == 1) out.writeInt(effect);
            out.flush();
            String reply = new DataInputStream(socket.getInputStream()).readUTF();
            if (!reply.startsWith("OK ")) throw new IllegalStateException(reply);
            return reply.substring(3);
        }
    }
    public synchronized String check() {
        try { String mode = remote(0, 0); backend = "ADB 高权限"; touchReady=mode.contains("touch=true"); return backend + " · 默认模式 " + mode; }
        catch (Exception ignored) {}
        touchReady=false;
        try { String mode = direct.check(); backend = "普通 App 直连"; return backend + " · 默认模式 " + mode; }
        catch (Throwable error) { backend = "不可用"; return "接口不可用，请运行电脑端 start-bridge.ps1"; }
    }
    public synchronized void prepare(int effect) throws Exception {
        // Never retry a mutating operation after an ambiguous timeout.
        if (backend.equals("ADB 高权限")) remote(1, effect);
        else if (backend.equals("普通 App 直连")) direct.prepare(effect);
        else throw new IllegalStateException("接口未就绪");
    }
    public synchronized void configureTouch(String pkg,String screen,int next,int prev,int rotation,int[] blocked) throws Exception {
        try(LocalSocket socket=new LocalSocket()) {
            socket.connect(new LocalSocketAddress(RootDaemon.SOCKET)); socket.setSoTimeout(100);
            DataOutputStream out=new DataOutputStream(socket.getOutputStream());
            out.writeInt(7); out.writeUTF(pkg); out.writeUTF(screen);
            out.writeInt(next); out.writeInt(prev); out.writeInt(rotation); out.writeInt(blocked.length/4);
            for(int bound:blocked) out.writeInt(bound);
            out.flush();
            String reply=new DataInputStream(socket.getInputStream()).readUTF();
            if(!reply.startsWith("OK ")) throw new IllegalStateException(reply);
        }
    }
    public synchronized void cancelTouch() { if(backend.equals("ADB 高权限")) try { remote(5,0); } catch(Exception ignored) {} }
}
