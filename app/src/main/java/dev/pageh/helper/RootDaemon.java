package dev.pageh.helper;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Process;
import java.io.DataInputStream;
import java.io.DataOutputStream;

/** Only exposes a validated one-shot PAGE_H command, never arbitrary shell commands. */
public final class RootDaemon {
    public static final String SOCKET = "dev.pageh.helper.bridge.v4";
    public static void main(String[] args) throws Exception {
        if (Process.myUid() != 0 || args.length != 1) throw new SecurityException("root and APK uid required");
        int allowedUid = Integer.parseInt(args[0]);
        if (allowedUid < 10000) throw new SecurityException("invalid APK uid");
        Commands commands = new Commands();
        TouchFeed touch = new TouchFeed(commands, allowedUid); touch.start();
        System.out.println("PAGEH root bridge uid=" + allowedUid + " default=" + commands.check());
        try (LocalServerSocket server = new LocalServerSocket(SOCKET)) {
            while (true) {
                try (LocalSocket client = server.accept()) {
                    client.setSoTimeout(1000);
                    if (client.getPeerCredentials().getUid() != allowedUid) continue;
                    DataInputStream in = new DataInputStream(client.getInputStream());
                    DataOutputStream out = new DataOutputStream(client.getOutputStream());
                    try {
                        int op = in.readInt();
                        if (op == 0) out.writeUTF("OK v4 " + commands.check() + " touch=" + touch.isAvailable());
                        else if (op == 1) {
                            commands.prepare(in.readInt());
                            out.writeUTF("OK prepared");
                        } else if (op == 7) {
                            String pkg=in.readUTF(),screen=in.readUTF();
                            int next=in.readInt(),prev=in.readInt(),rotation=in.readInt(),count=in.readInt();
                            if(count<0 || count>64) throw new IllegalArgumentException("regions");
                            int[] blocked=new int[count*4];
                            for(int i=0;i<blocked.length;i++) blocked[i]=in.readInt();
                            touch.configure(pkg,screen,next,prev,rotation,blocked);
                            out.writeUTF("OK touch lease");
                        } else if (op == 5) {
                            touch.cancel(); out.writeUTF("OK touch cancelled");
                        } else out.writeUTF("ERR invalid operation");
                    } catch (Throwable error) {
                        System.err.println("PAGEH operation: " + error);
                        out.writeUTF("ERR " + error.getClass().getSimpleName());
                    }
                    out.flush();
                } catch (Exception error) {
                    System.err.println("PAGEH client: " + error.getClass().getSimpleName());
                }
            }
        }
    }
}
