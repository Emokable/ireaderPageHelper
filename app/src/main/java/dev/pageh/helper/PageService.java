package dev.pageh.helper;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayDeque;

public final class PageService extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static volatile PageService instance;
    public static volatile String status="无障碍服务未连接",lastKey="尚未收到按键";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService probeWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private Bridge bridge=new Bridge();
    private Profiles profiles;
    private final ReadingWindows readingWindows=new ReadingWindows();
    private String activityPackage="",activityClass="";
    private TouchClient touchClient;
    private boolean touchConnected;
    private Profiles.Profile snapshotProfile;
    private int snapshotRotation=-1;
    private int[] blockedSnapshot;
    private boolean snapshotDirty=true;
    private long connectionGeneration,touchId,serial;
    private int eligibleWindow=-1;
    private Turn touchTurn,pending;
    private static final class Turn {
        long id,time;
        boolean forward,confirmed;
        int fingerprint,rotation;
        Profiles.Profile profile;
    }
    @Override protected void onServiceConnected() {
        long generation=++connectionGeneration;
        snapshotProfile=null; blockedSnapshot=null; snapshotDirty=true;
        handler.removeCallbacksAndMessages(null);
        if(touchClient!=null) touchClient.close();
        if(profiles!=null) profiles.prefs.unregisterOnSharedPreferenceChangeListener(this);
        instance=this; profiles=new Profiles(this); pending=null;
        touchId=0; touchTurn=null; touchConnected=false; bridge=new Bridge();
        profiles.prefs.registerOnSharedPreferenceChangeListener(this);
        touchClient=new TouchClient((type,id,x,y,time,effect) -> handler.post(() -> {
            if(generation==connectionGeneration) observeTouch(type,id,x,y,time,effect);
        }));
        Journal.add(this,"service connected; passive keys; one touch client");
        updateConnectionStatus(); handler.post(bridgeDiscovery);
    }
    private void updateConnectionStatus() {
        status=bridge.backend()+" · "+(bridge.canTouch()?(touchConnected?"触屏监听已连接":"触屏监听重连中")
            :"触屏不可用：请在电脑启动高权限桥接");
        if(MainActivity.visible!=null) MainActivity.visible.refreshStatus();
    }
    private final Runnable bridgeDiscovery=new Runnable() {
        @Override public void run() {
            long generation=connectionGeneration;
            probeWorker.execute(() -> {
                Bridge checked=new Bridge(); checked.check();
                handler.post(() -> {
                    if(generation!=connectionGeneration) return;
                    boolean changed=!bridge.backend().equals(checked.backend());
                    bridge=checked;
                    if(!bridge.canTouch()) { touchId=0; touchTurn=null; }
                    if(changed) Journal.add(PageService.this,"bridge state="+bridge.backend());
                    updateConnectionStatus(); reconcileWindows();
                    handler.removeCallbacks(this); handler.postDelayed(this,4000);
                });
            });
        }
    };
    @Override public void onSharedPreferenceChanged(SharedPreferences prefs,String key) {
        if(key!=null && key.startsWith("candidate.")) return;
        handler.post(() -> {
            abandon("设置变化"); touchId=0; touchTurn=null; bridge.cancelTouch();
            snapshotProfile=null; refreshTouchSnapshot();
            handler.removeCallbacks(bridgeDiscovery); handler.post(bridgeDiscovery);
        });
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (profiles == null) return;
        snapshotDirty=true;
        // Window-removal/focus notifications often have no package or Activity class.
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            if ((event.getWindowChanges() & AccessibilityEvent.WINDOWS_CHANGE_REMOVED) != 0)
                readingWindows.remove(event.getWindowId());
            reconcileWindows();
            handler.removeCallbacks(windowRecheck);
            handler.postDelayed(windowRecheck, 100);
            return;
        }
        if (event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        String cls = event.getClassName() == null ? "" : event.getClassName().toString();
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            boolean activity = false;
            try { getPackageManager().getActivityInfo(new ComponentName(pkg, cls), 0); activity = true; }
            catch (Exception ignored) {}
            if (activity) {
                if (!pkg.equals(activityPackage) || !cls.equals(activityClass)) abandon("窗口切换");
                activityPackage = pkg; activityClass = cls;
                readingWindows.remember(event.getWindowId(), pkg, cls);
                Journal.add(this, "window activity id=" + event.getWindowId() + " " + pkg + "/" + cls);
                if (!pkg.equals(getPackageName()) && !pkg.equals("com.android.systemui")
                        && !pkg.contains("launcher") && !pkg.equals("com.android.settings")) {
                    // Candidate metadata only; never persist book text.
                    profiles.prefs.edit().putString("candidate.pkg", pkg).putString("candidate.screen", cls).apply();
                }
            }
            reconcileWindows();
        }
        if (pending != null && !pending.confirmed && pkg.equals(pending.profile.pkg)
                && (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                || event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED)) {
            Turn expected = pending;
            handler.postDelayed(() -> confirm(expected), 70);
        }
    }
    private final Runnable windowRecheck = this::reconcileWindows;
    private void reconcileWindows() {
        Profiles.Profile p = activeProfile();
        if (p == null) {
            if (touchId!=0 || snapshotProfile!=null) { touchId=0; touchTurn=null; snapshotProfile=null; bridge.cancelTouch(); }
            if (eligibleWindow != -1) Journal.add(this, "window blocked; waiting for reading-window focus");
            eligibleWindow = -1;
            abandon("阅读窗口失去焦点");
        } else {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            int id = root == null ? -1 : root.getWindowId();
            if (root != null) root.recycle();
            if (id != eligibleWindow) {
                Journal.add(this, "window resumed id=" + id + " pkg=" + p.pkg);
                status = "阅读窗口已就绪 · " + bridge.backend();
                refreshTouchSnapshot();
            }
            eligibleWindow = id;
        }
    }
    private void abandon(String reason) {
        if(pending!=null) Journal.add(this,"cancel "+reason+"; 不重放旧翻页");
        pending=null;
    }
    private Profiles.Profile activeProfile() {
        if (profiles==null || !profiles.enabled()) return null;
        if (!((PowerManager)getSystemService(POWER_SERVICE)).isInteractive()
                || ((KeyguardManager)getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked()) return null;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        try {
            String pkg = root.getPackageName() == null ? "" : root.getPackageName().toString();
            Profiles.Profile p = profiles.get(pkg);
            if (p == null || p.screen.isEmpty()) return null;
            int focusedId = -1;
            for (AccessibilityWindowInfo window : getWindows()) {
                if (window.isFocused()) focusedId = window.getId();
                window.recycle();
            }
            if (!readingWindows.matches(root.getWindowId(), focusedId, pkg, p.screen)) return null;
            AccessibilityNodeInfo input = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (input != null) { boolean editable = input.isEditable(); input.recycle(); if (editable) return null; }
            return p;
        } finally { root.recycle(); }
    }
    private int rotation() { return ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRotation(); }
    @Override protected boolean onKeyEvent(KeyEvent event) {
        int key=event.getKeyCode();
        if(!PageKeys.isPageKey(key)) return false;
        lastKey=KeyEvent.keyCodeToString(key)+(event.getAction()==KeyEvent.ACTION_DOWN?" ↓":" ↑")+" · 原样透传";
        Profiles.Profile p=activeProfile();
        if(p==null) return false;
        boolean forward=PageKeys.forward(key,false);
        int trigger=p.onUp?KeyEvent.ACTION_UP:KeyEvent.ACTION_DOWN;
        if(event.getAction()==trigger && !event.isCanceled() && !atKnownBoundary(p,forward)) {
            Turn turn=create(p,forward);
            if(dispatch(turn)) armConfirmation(turn);
        }
        // Never consume, replace, queue or remap the reader's original input.
        return false;
    }
    private final Runnable touchSnapshot=new Runnable() {
        @Override public void run() {
            try {
                Profiles.Profile p=activeProfile();
                if(p==null || !p.touch || !bridge.canTouch() || !touchConnected) {
                    if(snapshotProfile!=null) bridge.cancelTouch();
                    snapshotProfile=null; return;
                }
                int r=rotation();
                boolean changed=snapshotProfile==null || !p.pkg.equals(snapshotProfile.pkg)
                    || !p.screen.equals(snapshotProfile.screen) || r!=snapshotRotation;
                if(snapshotDirty || changed || blockedSnapshot==null) {
                    blockedSnapshot=captureBlockedRegions(); snapshotDirty=false;
                }
                if(blockedSnapshot==null) { bridge.cancelTouch(); snapshotProfile=null; return; }
                bridge.configureTouch(p.pkg,p.screen,Direction.effect(true,p.reverse,r,p.speed),
                    Direction.effect(false,p.reverse,r,p.speed),r,blockedSnapshot);
                snapshotProfile=p; snapshotRotation=r;
                if(changed) Journal.add(PageService.this,"TOUCH_LEASE_READY pkg="+p.pkg+" blocked="+blockedSnapshot.length/4);
            } catch(Exception error) {
                snapshotProfile=null;
                Journal.add(PageService.this,"TOUCH_LEASE_UNAVAILABLE "+error.getClass().getSimpleName());
            } finally {
                handler.removeCallbacks(this); handler.postDelayed(this,250);
            }
        }
    };
    private void refreshTouchSnapshot() {
        snapshotDirty=true;
        handler.removeCallbacks(touchSnapshot); handler.post(touchSnapshot);
    }
    private void observeTouch(int type,long id,int rawX,int rawY,long time,int effect) {
        if(type==3 || type==4) {
            touchConnected=type==3; touchId=0; touchTurn=null; snapshotProfile=null;
            Journal.add(this,touchConnected?"touch stream connected":"touch stream disconnected; retrying");
            updateConnectionStatus(); refreshTouchSnapshot(); return;
        }
        if(type==0) {
            // No accessibility traversal or IPC in the gesture-critical path.
            touchId=id; touchTurn=null;
            Profiles.Profile p=snapshotProfile;
            if(p!=null) {
                Turn t=new Turn(); t.id=++serial; t.profile=p; t.time=time; t.rotation=snapshotRotation;
                touchTurn=t;
            }
        } else if(type==2 && touchId==id) {
            Turn t=touchTurn; touchId=0; touchTurn=null;
            if(t==null) return;
            if(effect<=0) {
                Journal.add(this,"TOUCH_SKIPPED reason="+effect+" (-2=无区域授权 -3=取消 -4=非翻页手势 -5=长按 -6=焦点或过期 -7=保护区域 -8=过期)");
                return;
            }
            t.forward=effect==Direction.effect(true,t.profile.reverse,t.rotation,t.profile.speed);
            Journal.add(this,"turn="+t.id+" TOUCH prepare OK effect="+effect+" pkg="+t.profile.pkg
                +" preauthorized; original input untouched");
        }
    }
    private int[] captureBlockedRegions() {
        android.graphics.Point size=new android.graphics.Point();
        ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealSize(size);
        if(size.x<=0 || size.y<=0) return null;
        AccessibilityNodeInfo root=getRootInActiveWindow(); if(root==null) return null;
        ArrayDeque<AccessibilityNodeInfo> nodes=new ArrayDeque<>(); nodes.add(root);
        java.util.ArrayList<Integer> blocks=new java.util.ArrayList<>();
        boolean complete=true; int count=0;
        while(!nodes.isEmpty()) {
            AccessibilityNodeInfo node=nodes.removeFirst();
            if(++count>400) complete=false;
            if(count<=400 && node.isVisibleToUser()) {
                Rect bounds=new Rect(); node.getBoundsInScreen(bounds);
                String tag=((node.getViewIdResourceName()==null?"":node.getViewIdResourceName())+" "
                    +(node.getContentDescription()==null?"":node.getContentDescription())).toLowerCase(java.util.Locale.ROOT);
                String cls=node.getClassName()==null?"":node.getClassName().toString();
                boolean blocked=node.isEditable() || cls.endsWith("Button") || cls.endsWith("EditText")
                    || tag.matches(".*(comment|annotation|note_button|评论|想法|划线).*")
                    || (node.isClickable() && (long)bounds.width()*bounds.height()<(long)size.x*size.y/5);
                if(blocked && bounds.intersect(0,0,size.x,size.y)) {
                    if(blocks.size()>=256) complete=false;
                    else {
                        blocks.add(bounds.left*10000/size.x); blocks.add(bounds.top*10000/size.y);
                        blocks.add(bounds.right*10000/size.x); blocks.add(bounds.bottom*10000/size.y);
                    }
                }
                for(int i=0;i<node.getChildCount() && nodes.size()<400;i++) {
                    AccessibilityNodeInfo child=node.getChild(i); if(child!=null) nodes.add(child);
                }
            }
            node.recycle();
        }
        if(!complete) return null;
        int[] result=new int[blocks.size()];
        for(int i=0;i<result.length;i++) result[i]=blocks.get(i);
        return result;
    }
    private Turn create(Profiles.Profile p, boolean forward) {
        Turn t = new Turn(); t.id = ++serial; t.profile = p; t.forward = forward;
        t.rotation = rotation(); return t;
    }
    private boolean dispatch(Turn t) {
        t.fingerprint=fingerprint(); t.time=SystemClock.uptimeMillis();
        int effect=Direction.effect(t.forward,t.profile.reverse,t.rotation,t.profile.speed);
        try {
            bridge.prepare(effect);
            handler.post(() -> Journal.add(this,"turn="+t.id+" NATIVE_PREPARE effect="+effect
                +" pkg="+t.profile.pkg+" original key untouched"));
            return true;
        } catch(Throwable error) {
            Journal.add(this,"KEY_EFFECT_FAILED "+error.getClass().getSimpleName()+"; 原按键继续，触屏不暂停");
            handler.removeCallbacks(bridgeDiscovery); handler.post(bridgeDiscovery);
            return false;
        }
    }
    private void armConfirmation(Turn t) {
        pending=t;
        handler.postDelayed(() -> {
            if(pending!=t || t.confirmed) return;
            confirm(t);
            if(pending==t && !t.confirmed) {
                Journal.add(this,"turn="+t.id+" UNCONFIRMED 不重试，不暂停按键或触屏");
                pending=null;
            }
        },2200);
    }
    private void confirm(Turn t) {
        if (pending != t || t.confirmed) return;
        Profiles.Profile now = activeProfile();
        if (now == null || !now.pkg.equals(t.profile.pkg) || rotation() != t.rotation) { abandon("确认时窗口变化"); return; }
        int current = fingerprint();
        if (t.fingerprint == 0 || current == 0 || current == t.fingerprint) return;
        t.confirmed = true;
        Journal.add(this, "turn=" + t.id + " CONTENT_CHANGED elapsed=" + (SystemClock.uptimeMillis()-t.time)
                + "ms; 非面板动画完成信号");
        status = "内容已变化 #" + t.id + " · 动画需目视确认";
        long cooldown = t.profile.speed == 128 ? 850 : t.profile.speed == 64 ? 650 : 450;
        handler.postDelayed(() -> { if (pending == t) { pending = null;  } }, cooldown);
    }
    private boolean atKnownBoundary(Profiles.Profile p, boolean forward) {
        // The built-in reader exposes an exact page counter; generic apps need their own adapter.
        if (!p.pkg.equals(getPackageName())) return false;
        AccessibilityNodeInfo root=getRootInActiveWindow(); if(root==null) return false;
        try {
            java.util.List<AccessibilityNodeInfo> counters=root.findAccessibilityNodeInfosByText(forward ? "20 / 20" : "1 / 20");
            boolean found=false;
            for(AccessibilityNodeInfo node:counters) {
                String text=node.getText()==null?"":node.getText().toString();
                if(text.startsWith(forward?"20 / 20":"1 / 20")) found=true;
                node.recycle();
            }
            return found;
        } finally { root.recycle(); }
    }
    private int fingerprint() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return 0;
        ArrayDeque<AccessibilityNodeInfo> nodes = new ArrayDeque<>(); nodes.add(root);
        int hash = 1, meaningful = 0, visited = 0;
        while (!nodes.isEmpty()) {
            AccessibilityNodeInfo node = nodes.removeFirst();
            if (++visited <= 160 && node.isVisibleToUser()) {
                CharSequence text = node.getText();
                if (text == null) text = node.getContentDescription();
                if (text != null && text.length() > 0 && !text.toString().matches("\\d{1,2}:\\d{2}|\\d{1,3}%")) {
                    hash = hash * 31 + text.toString().hashCode(); meaningful++;
                }
                for (int i=0; i<node.getChildCount() && nodes.size()<160; i++) {
                    AccessibilityNodeInfo child = node.getChild(i); if (child != null) nodes.add(child);
                }
            }
            node.recycle();
        }
        return meaningful == 0 ? 0 : hash;
    }
    public void reconnect() {
        abandon("手动检查"); handler.removeCallbacks(bridgeDiscovery); handler.post(bridgeDiscovery);
    }
    @Override public void onInterrupt() {
        abandon("服务中断"); touchId=0; touchTurn=null; bridge.cancelTouch();
    }
    @Override public void onDestroy() {
        connectionGeneration++;
        if(touchClient!=null) touchClient.close();
        probeWorker.shutdownNow(); bridge.cancelTouch();
        instance=null; status="无障碍服务未连接"; handler.removeCallbacksAndMessages(null);
        if(profiles!=null) profiles.prefs.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }
}
