package dev.pageh.helper;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class Journal {
    public static synchronized void add(Context context, String message) {
        Log.i("PageH", message);
        try {
            File file = new File(context.getFilesDir(), "events.log");
            boolean append = file.length() < 160000;
            try (FileOutputStream stream = new FileOutputStream(file, append)) {
                String stamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(new Date());
                stream.write((stamp + " " + message + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}
    }
    public static String read(Context context) {
        try {
            String text = new String(java.nio.file.Files.readAllBytes(new File(context.getFilesDir(), "events.log").toPath()), StandardCharsets.UTF_8);
            return text.substring(Math.max(0, text.length() - 12000));
        } catch (Exception error) { return "暂无日志"; }
    }
}
