package dev.qinglan.browser

import android.graphics.Color
import android.widget.*

class AppearancePanels(private val p:BrowserPanels){
    fun palette(){p.pages.show(tr("配色方案")){col->val u=p.u;Palette.names.forEach{(key,name)->val row=u.item((if(p.prefs.getString("palette","green")==key)"✓ "else"")+name){if(key=="custom")custom()else{p.prefs.edit().putString("palette",key).apply();p.a.retheme();p.pages.refresh()}};row.addView(u.label("●",24f,Color.parseColor(Palette.colors[key]?:p.prefs.getString("customColor","#286658")!!)),0);col.addView(row)}}}
    private fun custom(){p.pages.show(tr("自定义配色")){col->val u=p.u;val initial=p.prefs.getString("customColor","#286658")!!;val hex=u.edit("#RRGGBB",initial);col.addView(hex);val preview=u.label(tr("主题色预览"),20f).apply{gravity=android.view.Gravity.CENTER;minimumHeight=u.dp(80)};col.addView(preview)
        val bars=mutableListOf<SeekBar>();var syncing=false
        fun update(color:Int){preview.setBackgroundColor(color);preview.setTextColor(Ui.contrast(color))}
        listOf(tr("红"),tr("绿"),tr("蓝")).forEachIndexed{i,label->col.addView(u.label(label,12f,u.muted));val bar=SeekBar(p.a).apply{max=255;progress=(Color.parseColor(initial) shr (16-i*8)) and 255};bars.add(bar);col.addView(bar)}
        bars.forEach{bar->bar.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(s:SeekBar?){};override fun onStopTrackingTouch(s:SeekBar?){};override fun onProgressChanged(s:SeekBar?,v:Int,fromUser:Boolean){if(fromUser&&!syncing){val value=String.format("#%02X%02X%02X",bars[0].progress,bars[1].progress,bars[2].progress);hex.setText(value)}}})}
        hex.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){if(Palette.valid(s.toString())){val c=Color.parseColor(s.toString());syncing=true;bars.forEachIndexed{i,b->b.progress=(c shr (16-i*8)) and 255};syncing=false;update(c)}};override fun afterTextChanged(s:android.text.Editable?) {}});update(Color.parseColor(initial))
        col.addView(u.label(tr("背景会使用同色系浅色或深色；文字自动保持对比度。"),12f,u.muted));col.addView(u.button(tr("使用此配色"),true){val value=hex.text.toString().trim();if(!Palette.valid(value)){hex.error=tr("请输入 # 加六位十六进制颜色");return@button};p.prefs.edit().putString("palette","custom").putString("customColor",value.uppercase()).apply();p.a.retheme();p.pages.back()})
    }}
}
