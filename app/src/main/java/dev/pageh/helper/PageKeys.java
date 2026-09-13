package dev.pageh.helper;

/** Android key codes, kept independent of Android for regression tests. */
public final class PageKeys {
    public static boolean isPageKey(int key) { return key==24 || key==25 || key==92 || key==93; }
    public static boolean forward(int key, boolean swap) {
        if (!isPageKey(key)) throw new IllegalArgumentException("page key");
        return (key==25 || key==93) != swap;
    }
    public static int output(int original, boolean forward) {
        if (!isPageKey(original)) throw new IllegalArgumentException("page key");
        return original==92 || original==93 ? (forward?93:92) : (forward?25:24);
    }
}
