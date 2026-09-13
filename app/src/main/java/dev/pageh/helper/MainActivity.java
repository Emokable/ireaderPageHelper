package dev.pageh.helper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public final class MainActivity extends Activity {
    public static MainActivity visible;
    private Profiles profiles;
    private LinearLayout body;
    private TextView statusView;
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    @Override public void onCreate(Bundle state) { super.onCreate(state); profiles = new Profiles(this); }
    @Override protected void onResume() { super.onResume(); visible=this; render(); }
    @Override protected void onPause() { visible=null; super.onPause(); }
    public void refreshStatus() {
        if (statusView != null) statusView.setText(PageService.status + "\n" + PageService.lastKey);
    }
    private TextView text(LinearLayout parent, String value, int size) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(Color.BLACK);
        view.setPadding(0, dp(8), 0, dp(8)); parent.addView(view); return view;
    }
    private void button(LinearLayout parent, String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(16);
        parent.addView(b, new LinearLayout.LayoutParams(-1, dp(54))); b.setOnClickListener(v -> action.run());
    }
    private void render() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(22), dp(20), dp(22), dp(30));
        body.setBackgroundColor(Color.WHITE); scroll.addView(body); setContentView(scroll);
        LinearLayout brand=new LinearLayout(this); brand.setGravity(android.view.Gravity.CENTER_VERTICAL); body.addView(brand);
        ImageView mark=new ImageView(this); mark.setImageResource(R.drawable.brand_mark); mark.setContentDescription("几何波阵图标");
        brand.addView(mark,new LinearLayout.LayoutParams(dp(70),dp(70)));
        LinearLayout title=new LinearLayout(this); title.setOrientation(LinearLayout.VERTICAL); title.setPadding(dp(16),0,0,0); brand.addView(title);
        text(title, "掌阅水波纹", 29); text(title, "nuku  /  0.3.1", 14);
        Switch enabled = new Switch(this); enabled.setText("启用翻页动效"); enabled.setTextSize(20); enabled.setPadding(0,dp(15),0,dp(15));
        enabled.setChecked(profiles.enabled()); enabled.setOnCheckedChangeListener((v, checked) -> profiles.prefs.edit().putBoolean("enabled", checked).apply()); body.addView(enabled);
        statusView = text(body, PageService.status + "\n" + PageService.lastKey, 15);
        button(body, "① 打开无障碍服务", () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        button(body, "② 绑定刚才的阅读界面", this::bindCandidate);
        button(body, "应用与翻页设置", this::chooseApp);
        text(body, "先打开书籍正文，再回到这里绑定。只在绑定的界面启用；阅读器应已开启音量键翻页，并关闭软件翻页动画。", 15);
        button(body, "检查接口 / 恢复暂停", () -> {
            statusView.setText("正在检测…");
            new Thread(() -> {
                Bridge bridge = new Bridge(); String result = bridge.check();
                String ordinary;
                try { ordinary = "普通 UID 读取：" + new Commands().check(); }
                catch (Throwable error) { ordinary = "普通 UID 读取失败：" + error.getClass().getSimpleName(); }
                String report = result + "\n" + ordinary + "\nuid=" + android.os.Process.myUid();
                Journal.add(this, "probe " + report.replace('\n', ' '));
                runOnUiThread(() -> {
                    if (PageService.instance != null) PageService.instance.reconnect();
                    statusView.setText(report + "\n" + PageService.lastKey);
                });
            }, "interface-check").start();
        });
        button(body, "测试书页 · 跨进程按键", () -> {
            profiles.prefs.edit().putBoolean("directTest", false).apply();
            startActivity(new Intent(this, TestReaderActivity.class));
        });
        button(body, "测试书页 · 进程内调用", () -> {
            profiles.prefs.edit().putBoolean("directTest", true).apply();
            startActivity(new Intent(this, TestReaderActivity.class).putExtra("direct", true));
        });
        button(body, "查看 / 分享诊断日志", this::showLogs);
        button(body, "关于 · 项目信息与致谢", () -> startActivity(new Intent(this,AboutActivity.class)));
        text(body, "按键始终原样透传，不交换、不托管、不模拟触摸。触屏动效需要高权限桥接；失联时首页会提示。请关闭阅读器自身翻页动画。", 14);
    }
    private void bindCandidate() {
        String pkg = profiles.prefs.getString("candidate.pkg", "");
        String screen = profiles.prefs.getString("candidate.screen", "");
        if (pkg.isEmpty() || screen.isEmpty()) {
            new AlertDialog.Builder(this).setMessage("请先启用无障碍服务，打开阅读器的书籍正文，再回到助手。").setPositiveButton("知道了", null).show(); return;
        }
        new AlertDialog.Builder(this).setTitle("绑定这个阅读界面？")
            .setMessage(label(pkg) + "\n" + screen + "\n\n请确认这是正文界面。书架、菜单或浏览器页面不应绑定。")
            .setPositiveButton("绑定并设置", (d,w) -> {
                profiles.prefs.edit().putBoolean(pkg + ".enabled", true).putString(pkg + ".screen", screen).apply(); edit(pkg);
            }).setNegativeButton("取消", null).show();
    }
    private String label(String pkg) {
        try { return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString(); }
        catch (Exception error) { return pkg; }
    }
    private void chooseApp() {
        List<ResolveInfo> found = getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        List<String> packages = new ArrayList<>(); HashSet<String> seen = new HashSet<>();
        for (ResolveInfo info : found) if (seen.add(info.activityInfo.packageName)) packages.add(info.activityInfo.packageName);
        Collections.sort(packages, (a,b) -> label(a).compareTo(label(b)));
        String[] labels = new String[packages.size()];
        for (int i=0;i<labels.length;i++) labels[i] = (profiles.get(packages.get(i)) != null ? "✓ " : "") + label(packages.get(i));
        new AlertDialog.Builder(this).setTitle("选择应用").setItems(labels, (d,w) -> edit(packages.get(w))).setNegativeButton("返回", null).show();
    }
    private CheckBox check(LinearLayout pane, String label, boolean value) {
        CheckBox box = new CheckBox(this); box.setText(label); box.setChecked(value); pane.addView(box); return box;
    }
    private EditText input(LinearLayout pane, String label, String value, boolean numeric) {
        text(pane, label, 14); EditText edit = new EditText(this); edit.setText(value); edit.setTextSize(15);
        edit.setSingleLine(true); if (numeric) edit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); pane.addView(edit); return edit;
    }
    private void edit(String pkg) {
        ScrollView scroll = new ScrollView(this); LinearLayout pane = new LinearLayout(this); pane.setOrientation(LinearLayout.VERTICAL);
        pane.setPadding(dp(18),0,dp(18),dp(10)); scroll.addView(pane);
        boolean own = pkg.equals(getPackageName());
        CheckBox enable = check(pane,"允许这个应用",profiles.prefs.getBoolean(pkg+".enabled",own));
        EditText screen = input(pane,"阅读界面类名（通过绑定自动填写）",own ? "dev.pageh.helper.TestReaderActivity" : profiles.prefs.getString(pkg+".screen",""),false);
        if (own) screen.setEnabled(false);
        CheckBox reverse = check(pane,"反转水波纹方向",profiles.prefs.getBoolean(pkg+".reverse",false));
        CheckBox onUp = check(pane,"松开原按键时准备动效（默认按下时）",profiles.prefs.getBoolean(pkg+".onUp",false));
        CheckBox touch = check(pane,"原触摸翻页动效（全屏观察，实验）",profiles.prefs.getBoolean(pkg+".touch",true));
        text(pane,"动效速度",14); Spinner speed = new Spinner(this);
        speed.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"慢速","中速","快速"}));
        int existing = profiles.prefs.getInt(pkg+".speed",64); speed.setSelection(existing==128?0:existing==64?1:2); pane.addView(speed);
        text(pane,"阅读器需要支持按键翻页。原触摸：左侧点击上一页、右侧下一页；横滑按方向判断。中心菜单点击、长按、多指及可识别评论控件跳过。请关闭阅读器的软件翻页动画。",14);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(label(pkg)).setView(scroll).setPositiveButton("保存",null).setNegativeButton("取消",null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                String cls=screen.getText().toString().trim();
                if (enable.isChecked() && cls.isEmpty()) { screen.setError("请先绑定阅读界面"); return; }
                profiles.prefs.edit().putBoolean(pkg+".enabled",enable.isChecked()).putString(pkg+".screen",cls)

                    .putBoolean(pkg+".reverse",reverse.isChecked()).putBoolean(pkg+".onUp",onUp.isChecked())
                    .putBoolean(pkg+".touch",touch.isChecked())
                    .putInt(pkg+".speed",new int[]{128,64,0}[speed.getSelectedItemPosition()])
                    .apply();
                dialog.dismiss(); render();
            } catch (IllegalArgumentException error) { screen.setError("设置无效"); }
        })); dialog.show();
    }
    private void showLogs() {
        String log = Journal.read(this); TextView view = new TextView(this); view.setText(log); view.setTextSize(12); view.setTextIsSelectable(true); view.setPadding(dp(14),dp(8),dp(14),dp(8));
        ScrollView scroll = new ScrollView(this); scroll.addView(view);
        new AlertDialog.Builder(this).setTitle("诊断（不记录正文）").setView(scroll).setPositiveButton("关闭",null)
            .setNeutralButton("分享",(d,w)->startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,log),"分享诊断日志"))).show();
    }
}
