package dev.pageh.helper;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.io.DataInputStream;

public final class TouchClient {
    public interface Listener { void event(int type,long id,int x,int y,long time,int effect); }
    private volatile boolean running=true;
    private volatile LocalSocket socket;
    private final Thread thread;
    public TouchClient(Listener listener) {
        thread=new Thread(() -> {
            while(running) {
                try(LocalSocket client=new LocalSocket()) {
                    socket=client; if(!running) return; client.connect(new LocalSocketAddress(RootDaemon.SOCKET+".touch"));
                    client.setSoTimeout(1000);
                    DataInputStream input=new DataInputStream(client.getInputStream());
                    if(!input.readUTF().equals("OK RAW 1680 1264")) throw new IllegalStateException("unsupported touchscreen");
                    client.setSoTimeout(0);
                    if(running) listener.event(3,0,0,0,android.os.SystemClock.uptimeMillis(),0);
                    while(running) listener.event(input.readInt(),input.readLong(),input.readInt(),input.readInt(),input.readLong(),input.readInt());
                } catch(Exception ignored) {
                    if(running) listener.event(4,0,0,0,android.os.SystemClock.uptimeMillis(),0);
                    if(running) try { Thread.sleep(2000); } catch(InterruptedException stopped) { return; }
                } finally { socket=null; }
            }
        },"pageh-touch-client");
        thread.start();
    }
    public void close() { running=false; thread.interrupt(); try { if(socket!=null) socket.close(); } catch(Exception ignored) {} }
}
