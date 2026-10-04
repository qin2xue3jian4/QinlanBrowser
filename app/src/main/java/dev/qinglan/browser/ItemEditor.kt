package dev.qinglan.browser

import android.app.AlertDialog

/** Short object edits stay close to the collection instead of opening another route. */
object ItemEditor {
    fun show(a:BrowserActivity,title:String,name:String,url:String?,save:(String,String)->Unit){
        val u=a.ui;val col=u.column(18);val n=u.edit("名称",name);col.addView(n);val address=url?.let{u.edit("https://example.com",it).also(col::addView)}
        val dialog=AlertDialog.Builder(a).setTitle(title).setView(col).setNegativeButton("取消",null).setPositiveButton("保存",null).create()
        dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            val label=n.text.toString().trim();var link=address?.text?.toString()?.trim().orEmpty();if(address!=null&&!link.contains("://"))link="https://$link"
            if(label.isBlank()){n.error="请输入名称";return@setOnClickListener};if(address!=null&&!a.isHttp(link)){address.error="请输入 HTTP/HTTPS 网址";return@setOnClickListener}
            save(label.take(1000),link);dialog.dismiss()
        }};dialog.show()
    }
}
