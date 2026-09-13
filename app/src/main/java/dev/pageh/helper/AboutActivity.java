package dev.pageh.helper;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;

public final class AboutActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad=(int)(28*getResources().getDisplayMetrics().density); page.setPadding(pad,pad,pad,pad); page.setBackgroundColor(Color.WHITE);
        ScrollView scroll=new ScrollView(this); scroll.addView(page); setContentView(scroll);
        ImageView mark=new ImageView(this); mark.setImageResource(R.drawable.brand_mark); mark.setContentDescription("几何波阵图标");
        page.addView(mark,new LinearLayout.LayoutParams(pad*5,pad*5));
        text(page,"掌阅水波纹",30); text(page,"PAGEH / 几何之间，翻页生波",16); text(page,"VERSION 0.3.1",14);
        section(page,"01 / 开发者","nuku\n独立开发与维护");
        Button email=new Button(this); email.setText("krisia@foxmail.com"); email.setAllCaps(false); page.addView(email);
        email.setOnClickListener(v -> {
            Intent intent=new Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:krisia@foxmail.com"));
            intent.putExtra(Intent.EXTRA_SUBJECT,"掌阅水波纹助手反馈");
            try { startActivity(intent); } catch(android.content.ActivityNotFoundException e) {
                ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("邮箱","krisia@foxmail.com"));
                Toast.makeText(this,"邮箱已复制",Toast.LENGTH_SHORT).show();
            }
        });
        section(page,"02 / API 来源与致谢","水波纹动效 API 来自掌阅（iReader）。\n本项目调用掌阅系统提供的 E-Ink / PAGE_H 接口，水波纹底层实现由掌阅系统提供，并非本项目原创。\n\n本项目为第三方独立工具，非掌阅官方产品，与掌阅不存在官方合作或背书关系。相关商标与接口权利归各自权利人所有。");
        section(page,"03 / 源码与许可","© 2026 nuku\nGNU GPL v3.0（GPL-3.0-only）\n\n源码与许可证：\nhttps://github.com/Emokable/ireaderPageHelper\n\n本项目原创代码与资源按 GNU GPL v3.0 授权，不附带任何担保。使用、修改与分发请遵守仓库 LICENSE 文件。掌阅系统实现、API 及第三方商标不属于本项目的开源授权范围。");
        section(page,"04 / 使用说明","在阅读器中开启按键翻页，并把阅读器自己的翻页动画设置为“无”。\n\n全屏触摸动效为实验功能，保留阅读器原本的触摸操作；不同阅读器的评论与自绘控件识别仍需适配。");
        Button back=new Button(this); back.setText("返回"); page.addView(back); back.setOnClickListener(v -> finish());
    }
    private void section(LinearLayout page,String title,String body) {
        android.view.View rule=new android.view.View(this); rule.setBackgroundColor(Color.rgb(215,220,224));
        LinearLayout.LayoutParams line=new LinearLayout.LayoutParams(-1,Math.max(1,(int)getResources().getDisplayMetrics().density));
        line.topMargin=24; line.bottomMargin=12; page.addView(rule,line);
        TextView heading=new TextView(this); heading.setText(title); heading.setTextSize(15); heading.setTextColor(Color.rgb(45,62,70));
        heading.setTypeface(null,android.graphics.Typeface.BOLD); heading.setPadding(0,12,0,12); page.addView(heading,new LinearLayout.LayoutParams(-1,-2));
        TextView copy=new TextView(this); copy.setText(body); copy.setTextSize(16); copy.setTextColor(Color.rgb(30,35,40));
        copy.setLineSpacing(6,1.1f); copy.setTextIsSelectable(true); page.addView(copy,new LinearLayout.LayoutParams(-1,-2));
    }
    private void text(LinearLayout page,String value,int size) {
        TextView view=new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(Color.BLACK);
        view.setGravity(Gravity.CENTER); view.setPadding(0,20,0,20); page.addView(view);
    }
}
