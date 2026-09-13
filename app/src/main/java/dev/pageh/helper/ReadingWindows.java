package dev.pageh.helper;

import java.util.LinkedHashMap;
import java.util.Map;

/** Activity identities belong to windows, not to the last window event. */
public final class ReadingWindows {
    private final Map<Integer, String[]> identities = new LinkedHashMap<>();
    public void remember(int windowId, String pkg, String activity) {
        if (windowId < 0) return;
        identities.put(windowId, new String[]{pkg, activity});
        if (identities.size() > 32) identities.remove(identities.keySet().iterator().next());
    }
    public void remove(int windowId) { identities.remove(windowId); }
    public boolean matches(int rootId, int focusedId, String pkg, String activity) {
        String[] identity = identities.get(rootId);
        return rootId >= 0 && rootId == focusedId && identity != null
                && identity[0].equals(pkg) && identity[1].equals(activity);
    }
}
