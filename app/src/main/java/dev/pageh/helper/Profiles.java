package dev.pageh.helper;

import android.content.Context;
import android.content.SharedPreferences;

public final class Profiles {
    public final SharedPreferences prefs;
    public Profiles(Context context) { prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE); }
    public boolean enabled() { return prefs.getBoolean("enabled", true); }
    public Profile get(String pkg) {
        if (pkg == null || pkg.isEmpty()) return null;
        boolean own = pkg.equals("dev.pageh.helper");
        if (!prefs.getBoolean(pkg + ".enabled", own)) return null;
        if (own && prefs.getBoolean("directTest", false)) return null;
        Profile p = new Profile(); p.pkg = pkg;
        p.screen = own ? "dev.pageh.helper.TestReaderActivity" : prefs.getString(pkg + ".screen", "");
        p.reverse = prefs.getBoolean(pkg + ".reverse", false);
        p.onUp = prefs.getBoolean(pkg + ".onUp", false);
        p.speed = prefs.getInt(pkg + ".speed", 64);
        p.touch = prefs.getBoolean(pkg + ".touch", true);
        return p;
    }
    public static final class Profile {
        public String pkg, screen;
        public boolean reverse, onUp, touch;
        public int speed;
    }
}
