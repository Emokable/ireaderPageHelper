package dev.pageh.helper;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Separate process: normal mode does not call PAGE_H from the renderer. */
public final class TestReaderActivity extends Activity {
    private int page = 5;
    private boolean direct;
    private TextView sheet, footer;
    private final Commands commands = new Commands();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); direct=getIntent().getBooleanExtra("direct",false);
        if (state != null) page=state.getInt("page",5);
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_FULLSCREEN);
        LinearLayout layout=new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setBackgroundColor(Color.WHITE); layout.setPadding(40,32,40,30);
        TextView title=new TextView(this); title.setText(direct ? "进程内测试 · 左右点击翻页" : "跨进程测试 · 请按音量键或翻页键"); title.setTextSize(18); layout.addView(title);
        android.widget.Button popup=new android.widget.Button(this); popup.setText("弹窗恢复测试"); layout.addView(popup);
        popup.setOnClickListener(v -> {
            Journal.add(this,"test popup opened page="+page);
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle("模拟充值提示")
                .setMessage("这是本地测试弹窗，不会充值。关闭后应能直接继续水波纹翻页。")
                .setPositiveButton("关闭并继续阅读",null).create();
            dialog.setOnDismissListener(d -> Journal.add(this,"test popup dismissed page="+page));
            dialog.show();
        });
        sheet=new TextView(this); sheet.setTextColor(Color.BLACK); sheet.setTextSize(27); sheet.setGravity(Gravity.CENTER);
        layout.addView(sheet,new LinearLayout.LayoutParams(-1,0,1));
        footer=new TextView(this); footer.setTextSize(17); footer.setGravity(Gravity.CENTER); layout.addView(footer); setContentView(layout);
        sheet.setOnTouchListener((v,event)->{ if(event.getAction()==android.view.MotionEvent.ACTION_UP) { turn(event.getX()>v.getWidth()/2f); v.performClick(); } return true; });
        render();
        Journal.add(this,"test opened direct="+direct+" uid="+android.os.Process.myUid()+" pid="+android.os.Process.myPid());
        if (direct) try { Journal.add(this,"ordinary process default="+commands.check()); } catch(Throwable e) { Journal.add(this,"ordinary process check FAILED "+e); }
    }
    private void turn(boolean forward) {
        int next=page+(forward?1:-1); if(next<1||next>20) return;
        if(direct) try {
            int effect=Direction.effect(forward,false,getDisplay().getRotation(),64); commands.prepare(effect);
            Journal.add(this,"direct prepare OK effect="+effect);
        } catch(Throwable error) { Journal.add(this,"direct prepare FAILED "+error.getClass().getSimpleName()); }
        page=next; render(); Journal.add(this,"test page="+page+" forward="+forward);
    }
    private void render() {
        String content = page%2==0 ? "山间的风\n\n穿过松林，落在书页上。\n远处的光慢慢移向窗边。\n\n这一页，向前。" : "海上的月\n\n潮声停在安静的岸边。\n翻过一页，故事仍在继续。\n\n这一页，停留。";
        sheet.setText("第 "+page+" 页\n\n"+content); footer.setText(page+" / 20　　左：上一页　右：下一页");
    }
    @Override public boolean onKeyDown(int key,KeyEvent event) {
        if(key==KeyEvent.KEYCODE_VOLUME_DOWN||key==KeyEvent.KEYCODE_PAGE_DOWN) { turn(true); return true; }
        if(key==KeyEvent.KEYCODE_VOLUME_UP||key==KeyEvent.KEYCODE_PAGE_UP) { turn(false); return true; }
        return super.onKeyDown(key,event);
    }
    @Override public boolean onKeyUp(int key,KeyEvent event) {
        if(key==KeyEvent.KEYCODE_VOLUME_DOWN||key==KeyEvent.KEYCODE_PAGE_DOWN||key==KeyEvent.KEYCODE_VOLUME_UP||key==KeyEvent.KEYCODE_PAGE_UP) return true;
        return super.onKeyUp(key,event);
    }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putInt("page",page); }
}
