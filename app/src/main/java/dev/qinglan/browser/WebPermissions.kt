package dev.qinglan.browser

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest

/** Only a foreground HTTPS top-level origin may ask. Never grants unknown WebView resources. */
class WebPermissions(private val a:BrowserActivity){
    private data class Pending(val token:Any,val tab:BrowserTab,val origin:String,val system:Array<String>,val answer:(Boolean)->Unit)
    private var pending:Pending?=null
    private var dialog:AlertDialog?=null
    private var runtimeActive=false
    internal val asking get()=pending!=null
    private fun allowed(tab:BrowserTab,origin:String)=a.current===tab&&tab.web!=null&&tab.error==null&&WebPermissionPolicy.sameOrigin(tab.url,origin)&&!a.isFinishing&&!a.isDestroyed
    private fun enabled(origin:String,key:String)=a.store.siteBool(origin,key,a.prefs.getBoolean(key+"Prompt",true))
    fun media(tab:BrowserTab,request:PermissionRequest){
        val kinds=request.resources.map{when(it){PermissionRequest.RESOURCE_VIDEO_CAPTURE->"camera";PermissionRequest.RESOURCE_AUDIO_CAPTURE->"microphone";else->"unknown"}}
        if(kinds.isEmpty()||kinds.any{it=="unknown"||!enabled(request.origin.toString(),it)}){request.deny();return}
        val system=kinds.map{if(it=="camera")Manifest.permission.CAMERA else Manifest.permission.RECORD_AUDIO}.distinct().toTypedArray()
        ask(Pending(request,tab,request.origin.toString(),system){yes->if(yes)request.grant(request.resources.filter{it==PermissionRequest.RESOURCE_VIDEO_CAPTURE||it==PermissionRequest.RESOURCE_AUDIO_CAPTURE}.toTypedArray())else request.deny()},kinds.joinToString("、"){if(it=="camera")"摄像头"else"麦克风"})
    }
    fun location(tab:BrowserTab,origin:String,callback:GeolocationPermissions.Callback){
        if(!enabled(origin,"location")){callback.invoke(origin,false,false);return}
        ask(Pending(callback,tab,origin,arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)){yes->callback.invoke(origin,yes,false)},"大致位置")
    }
    private fun ask(value:Pending,label:String){
        if(pending!=null||runtimeActive||!allowed(value.tab,value.origin)||!a.hasWindowFocus()||a.panels.pages.visible){value.answer(false);return}
        pending=value
        dialog=AlertDialog.Builder(a).setTitle("允许网站使用$label？").setMessage("${value.origin}\n\n仅允许此次请求。也可在网站设置中阻止它再次询问。")
            .setNegativeButton("拒绝"){_,_->finish(false)}.setPositiveButton("允许此次"){_,_->
                val next=pending?:return@setPositiveButton
                if(!allowed(next.tab,next.origin)){finish(false);return@setPositiveButton}
                val needed=next.system.filter{a.checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED}
                if(needed.isEmpty())finish(true)else{runtimeActive=true;a.requestPermissions(needed.toTypedArray(),105)}
            }.setOnCancelListener{finish(false)}.show()
        if(a.isIncognito)dialog?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }
    fun result(){runtimeActive=false;val next=pending?:return;finish(next.system.all{a.checkSelfPermission(it)==PackageManager.PERMISSION_GRANTED})}
    private fun finish(yes:Boolean){val next=pending?:return;pending=null;dialog?.dismiss();dialog=null;next.answer(yes&&allowed(next.tab,next.origin))}
    fun cancel(token:Any?=null){if(token==null||pending?.token===token)finish(false)}
}
