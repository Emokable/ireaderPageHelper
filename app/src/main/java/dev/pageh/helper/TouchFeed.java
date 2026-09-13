package dev.pageh.helper;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.SystemClock;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Passive evdev reader. Never grabs input, injects touch, or modifies touch delivery. */
public final class TouchFeed {
    private final Commands commands;
    private final int uid;
    private LocalSocket subscriber;
    private DataOutputStream output;
    private long gesture,down;
    private TouchLease lease,gestureLease;
    private TouchFocus focus;
    private int rejection;
    private int startX,startY,lastX,lastY,rotation,nextEffect,prevEffect;
    private boolean authorized,active,cancelled;
    private String path;
    private volatile boolean available;
    public boolean isAvailable() { return available; }
    public TouchFeed(Commands commands,int uid) { this.commands=commands; this.uid=uid; }
    public void start() {
        // These raw ranges were verified from dumpsys input on Ocean 5 Pro.
        for(int i=0;i<32;i++) {
            File name=new File("/sys/class/input/event"+i+"/device/name");
            try(FileInputStream in=new FileInputStream(name)) {
                byte[] buffer=new byte[128]; int n=in.read(buffer);
                if(n>0 && new String(buffer,0,n,StandardCharsets.UTF_8).trim().equals("fts_ts")) { path="/dev/input/event"+i; break; }
            } catch(IOException ignored) {}
        }
        if(path==null) { System.err.println("Touch: supported touchscreen not found"); return; }
        try { focus=new TouchFocus(); } catch(Exception e) { System.err.println("Touch focus unavailable: "+e); return; }
        available=true;
        new Thread(this::accept,"pageh-touch-clients").start();
        new Thread(this::read,"pageh-touch-input").start();
    }
    private void accept() {
        try(LocalServerSocket server=new LocalServerSocket(RootDaemon.SOCKET+".touch")) {
            while(true) {
                LocalSocket socket=server.accept();
                if(socket.getPeerCredentials().getUid()!=uid) { socket.close(); continue; }
                synchronized(this) {
                    if(subscriber!=null) subscriber.close();
                    subscriber=socket; output=new DataOutputStream(socket.getOutputStream());
                    output.writeUTF("OK RAW 1680 1264"); output.flush(); authorized=false; lease=null;
                }
            }
        } catch(Exception e) { System.err.println("Touch server: "+e); }
    }
    public synchronized void configure(String pkg,String screen,int next,int prev,int r,int[] blocked) {
        TouchLease nextLease=new TouchLease(pkg,screen,next,prev,r,blocked,SystemClock.uptimeMillis()+900);
        if(active && authorized) {
            float[] start=TouchDecision.rotate(startX/1680f,startY/1264f,r);
            if(!nextLease.sameTarget(gestureLease) || !nextLease.allows(start[0],start[1],SystemClock.uptimeMillis())) authorized=false;
            else gestureLease=nextLease;
        }
        lease=nextLease;
    }
    public synchronized void cancel() { authorized=false; lease=null; gestureLease=null; }
    private void emit(int type,int effect) {
        if(output==null) return;
        try {
            output.writeInt(type); output.writeLong(gesture); output.writeInt(lastX); output.writeInt(lastY);
            output.writeLong(SystemClock.uptimeMillis()); output.writeInt(effect); output.flush();
        } catch(IOException e) { try{subscriber.close();}catch(Exception ignored){} subscriber=null; output=null; authorized=false; }
    }
    private synchronized void frame(int count,int x,int y) {
        long now=SystemClock.uptimeMillis();
        if(count>1) { cancelled=true; authorized=false; }
        if(count>0 && !active) {
            active=true; authorized=false; cancelled=count!=1; gesture++;
            down=now; startX=lastX=x; startY=lastY=y; gestureLease=lease; rejection=-2;
            if(gestureLease!=null && !cancelled) {
                float[] point=TouchDecision.rotate(x/1680f,y/1264f,gestureLease.rotation);
                if(now>=gestureLease.expires) rejection=-8;
                else if(!gestureLease.allows(point[0],point[1],now)) rejection=-7;
                else if(!focus.matches(gestureLease)) rejection=-6;
                else {
                    authorized=true; rotation=gestureLease.rotation;
                    nextEffect=gestureLease.next; prevEffect=gestureLease.prev;
                }
            }
            emit(0,0);
        } else if(count>0) { lastX=x; lastY=y; }
        else if(active) {
            int effect=cancelled?-3:rejection;
            if(authorized && gestureLease!=null && (now>=gestureLease.expires || !focus.matches(gestureLease))) { authorized=false; effect=-6; }
            if(authorized && !cancelled) {
                float[] start=TouchDecision.rotate(startX/1680f,startY/1264f,rotation);
                float[] end=TouchDecision.rotate(lastX/1680f,lastY/1264f,rotation);
                int dir=TouchDecision.direction(start[0],start[1],end[0],end[1],now-down);
                effect=now-down>500?-5:-4;
                if(dir!=0) {
                    effect=dir>0?nextEffect:prevEffect;
                    try { commands.prepare(effect); } catch(Exception e) { effect=-1; }
                }
            }
            if(effect<0) System.err.println("TOUCH_SKIPPED reason="+effect+" duration="+(now-down)+"ms authorized="+authorized);
            active=false; authorized=false; gestureLease=null; emit(2,effect);
        }
    }
    private void read() {
        int size=android.os.Process.is64Bit()?24:16,offset=size-8,slot=0;
        boolean[] slots=new boolean[32]; int[] xs=new int[32],ys=new int[32];
        try(DataInputStream in=new DataInputStream(new FileInputStream(path))) {
            byte[] raw=new byte[size];
            while(true) {
                in.readFully(raw); ByteBuffer b=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
                int type=b.getShort(offset)&65535,code=b.getShort(offset+2)&65535,value=b.getInt(offset+4);
                if(type==3) {
                    if(code==47) slot=Math.max(0,Math.min(31,value));
                    else if(code==57) slots[slot]=value>=0;
                    else if(code==53) xs[slot]=value;
                    else if(code==54) ys[slot]=value;
                } else if(type==0 && code==0) {
                    int count=0,primary=0;
                    for(int i=31;i>=0;i--) if(slots[i]) { count++; primary=i; }
                    frame(count,xs[primary],ys[primary]);
                } else if(type==0 && code==3) {
                    synchronized(this) { authorized=false; cancelled=true; }
                }
            }
        } catch(Exception e) {
            available=false;
            System.err.println("Touch reader: "+e); cancel();
            synchronized(this) { try { if(subscriber!=null) subscriber.close(); } catch(Exception ignored) {} }
        }
    }
}
