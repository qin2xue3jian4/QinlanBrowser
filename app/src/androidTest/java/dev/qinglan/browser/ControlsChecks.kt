package dev.qinglan.browser

import android.app.Activity
import android.app.AlertDialog
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import android.widget.PopupMenu
import org.json.JSONArray
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Local synthetic pages only. Restore existing preferences and selected tab after checks. */
object ControlsChecks {
    fun run(r:Instrumentation,mode:String){val result=Bundle();var passed=true;var a:BrowserActivity?=null;var oldTab=0L;val created=mutableListOf<Long>();var oldPrefs=mapOf<String,Any?>();var account=""
        val keys=listOf("httpsOnly","certificateExceptions","edgeScroll","blobDownloads","recordHistory","shareFormat","site.127.0.0.1.ua","site.127.0.0.1.desktop","certificate.https://127.0.0.1:8893")
        fun main(run:()->Unit){var failure:Throwable?=null;r.runOnMainSync{try{run()}catch(e:Throwable){failure=e}};failure?.let{throw it}}
        try{
            val activity=r.startActivitySync(Intent(r.targetContext,BrowserActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as BrowserActivity;a=activity
            if(mode=="cleanup"){
                main{activity.panels.pages.close();activity.tabs.filter{val uri=android.net.Uri.parse(it.url);uri.host=="127.0.0.1"&&uri.port in listOf(8892,8893)}.map{it.id}.forEach(activity::closeTab)
                    activity.accounts.all().filter{it.name=="Controls secondary"&&android.net.Uri.parse(it.startUrl).port==8892}.forEach{activity.accounts.remove(it.id){}}
                    val edit=activity.prefs.edit();keys.filter{it!="recordHistory"}.forEach(edit::remove);edit.apply();activity.security.clear();activity.updateScrollButtons();activity.persistSession()
                };result.putString("stream","PASS: removed synthetic controls tabs/accounts and new test settings.\n");r.finish(Activity.RESULT_OK,result);return
            }
            main{oldPrefs=keys.associateWith{activity.prefs.all[it]};oldTab=activity.current!!.id;check(!activity.isIncognito){"Exit private mode before controls checks"};activity.prefs.edit().remove(keys.last()).putBoolean("httpsOnly",false).putBoolean("certificateExceptions",false).putBoolean("recordHistory",false).putBoolean("blobDownloads",true).putBoolean("edgeScroll",true).apply();activity.newHome();created.add(activity.current!!.id)}
            fun eval(js:String):String {val latch=CountDownLatch(1);var value="";main{activity.current!!.web!!.evaluateJavascript(js){value=it;latch.countDown()}};check(latch.await(10,TimeUnit.SECONDS));return value}
            var step="initial"
            fun trace(name:String){step=name;r.sendStatus(0,Bundle().apply{putString("stream","CHECK: $name\n")})}
            fun waitFor(checker:()->Boolean){val until=System.currentTimeMillis()+20000;while(System.currentTimeMillis()<until){var ready=false;main{ready=checker()};if(ready)return;Thread.sleep(100)};error("Timed out waiting for $mode / $step")}
            fun loaded(){Thread.sleep(400);waitFor{activity.current?.error==null&&activity.progress.visibility==View.GONE&&activity.current?.web?.progress==100&&activity.current?.web?.title=="Controls fixture"}}
            fun prompt():AlertDialog? {val f=WebSecurity::class.java.getDeclaredField("prompt");f.isAccessible=true;return f.get(activity.security) as? AlertDialog}
            fun choose(i:Int){waitFor{prompt()?.isShowing==true};main{prompt()!!.getButton(when(i){0->AlertDialog.BUTTON_NEGATIVE;1->AlertDialog.BUTTON_POSITIVE;else->AlertDialog.BUTTON_NEUTRAL}).performClick()}}
            if(mode in listOf("blobCsp","blobSite")){
                val url=if(mode=="blobSite")"https://47.107.169.3:8002/"else"http://127.0.0.1:8892/csp"
                main{
                    if(mode=="blobSite"){
                        check(PrivateSession.supported());account=activity.accounts.create("Controls CSP temporary",url).id
                        activity.prefs.edit().putBoolean("certificateExceptions",true).apply();activity.openWithAccount(url,account,false);created.add(activity.current!!.id)
                    }else activity.open(url)
                }
                waitFor{prompt()?.isShowing==true||(activity.current?.web?.progress==100&&activity.current?.committedUrl==url&&activity.current?.error==null)}
                var sslPrompt=false;main{sslPrompt=prompt()?.isShowing==true};if(sslPrompt)choose(1)
                waitFor{activity.current?.error==null&&activity.current?.web?.progress==100&&activity.current?.committedUrl==url}
                eval("window.__qaBlobFetch=0;window.__qaOriginalFetch=window.fetch;window.fetch=function(url,...args){if(String(url).startsWith('blob:')){window.__qaBlobFetch++;return Promise.reject(new Error('Blob fetch blocked by test policy'));}return window.__qaOriginalFetch.call(this,url,...args);};")
                val saved=CountDownLatch(2);var failure:Throwable?=null
                val monitor=object:Instrumentation.ActivityMonitor(){override fun onStartActivity(intent:Intent):Instrumentation.ActivityResult?{
                    if(intent.action!=Intent.ACTION_CREATE_DOCUMENT)return null
                    try{val field=PageExports::class.java.getDeclaredField("pending").apply{isAccessible=true};val file=field.get(activity.exports) as java.io.File;val bytes=file.readBytes();check(bytes.size==200003);check(bytes.indices.all{(bytes[it].toInt() and 255)==it%251})}
                    catch(e:Throwable){failure=e}finally{saved.countDown()};return Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null)
                }}
                fun blobDialog():AlertDialog?{val field=BlobDownloads::class.java.getDeclaredField("states").apply{isAccessible=true};val map=field.get(activity.blobs) as Map<*,*>;val state=map[activity.current!!.web]?:return null;return state.javaClass.getDeclaredField("dialog").apply{isAccessible=true}.get(state) as? AlertDialog}
                r.addMonitor(monitor)
                try{repeat(2){index->
                    eval("(function(){const bytes=new Uint8Array(200003);for(let i=0;i<bytes.length;i++)bytes[i]=i%251;const link=document.createElement('a');link.href=URL.createObjectURL(new Blob([bytes],{type:'application/octet-stream'}));link.download='controls-blob.bin';document.body.append(link);link.click();${if(index==1)"URL.revokeObjectURL(link.href);link.remove();"else""}})();")
                    waitFor{blobDialog()?.isShowing==true};main{blobDialog()!!.getButton(AlertDialog.BUTTON_POSITIVE).performClick()};waitFor{saved.count==1L-index};failure?.let{throw it};Thread.sleep(200)
                };check(eval("window.__qaBlobFetch")=="0"){"Blob download attempted a prohibited fetch"}}
                finally{r.removeMonitor(monitor)}
                result.putString("stream","PASS: $mode cached Blob reads with prohibited fetch; persistent download anchor; programmatic click with immediate revoke; both 200003-byte files verified; save dialogs intercepted and canceled.\n")
            }else if(mode in listOf("screenshotPreview","sharePreview")){
                main{activity.open("http://127.0.0.1:8892/")};loaded()
                main{if(mode=="screenshotPreview")PageExports::class.java.getDeclaredMethod("capture",java.lang.Boolean.TYPE).apply{isAccessible=true}.invoke(activity.exports,true)
                    else {activity.prefs.edit().putString("shareFormat","ask").apply();activity.exports.share()}}
                trace("preview ready");Thread.sleep(30000);result.putString("stream","PASS: preview closed and preferences restored.\n")
            }else if(mode=="tls"){
                trace("default certificate block")
                main{activity.open("https://127.0.0.1:8893/")}
                waitFor{activity.current!!.error!=null};main{check(prompt()==null){"Default unexpectedly prompted"};activity.prefs.edit().putBoolean("certificateExceptions",true).apply();activity.reload()}
                trace("trust once prompt");choose(1);trace("load after trust once");loaded();main{check(activity.security.trusted(activity.current!!)){"Exception status lost"};check(!activity.prefs.contains(keys.last())){"Once persisted"};activity.reload()}
                trace("prompt again after reload");choose(2);trace("load after persistent trust");loaded();main{check(activity.prefs.getString(keys.last(),"")!!.length==64);activity.reload()};trace("persistent trust reload");loaded()
                main{check(prompt()==null);activity.prefs.edit().putString(keys.last(),"different-certificate").apply();activity.security.clear();activity.reload()}
                trace("changed pin prompt");choose(0);waitFor{activity.current!!.error!=null}
                main{activity.prefs.edit().putBoolean("httpsOnly",true).apply();activity.open("http://127.0.0.1:8893/");check(activity.currentUrl.startsWith("https://"))};trace("https upgrade prompt");choose(1);loaded()
                result.putString("stream","PASS: default SSL block; explicit trust once; re-prompt on reload; certificate-pinned address trust; changed pin rejection; HTTPS address upgrade.\n")
            }else{
                main{activity.open("http://127.0.0.1:8892/")};loaded()
                main{check(activity.address.text.toString()=="Controls fixture"){"Unfocused address did not show title"};check(!activity.bottom.getChildAt(0).isEnabled);check(!activity.bottom.getChildAt(1).isEnabled);check(activity.content.childCount==2){"Missing edge controls"}
                    activity.panels.menu();val overlay=activity.content.getChildAt(activity.content.childCount-1);val edge=activity.content.getChildAt(1);check(overlay.elevation>edge.elevation){"Menu behind edge controls"};activity.dismissTabs();activity.focusAddress()}
                Thread.sleep(300);main{check(activity.address.text.toString()==activity.currentUrl){"Focused address did not show URL"};check(activity.address.selectionStart==0&&activity.address.selectionEnd==activity.address.text.length){"Address not selected"};activity.address.clearFocus();activity.content.requestFocus()
                    val site="named-account.example.test";val oldName=activity.accounts.label("",site)
                    try{activity.accounts.renameDefault(site,"Controls named");val menu=PopupMenu(activity,activity.bottom).menu;CollectionActions.add(activity,menu,"https://$site/");check(menu.getItem(0).hasSubMenu()&&menu.getItem(1).hasSubMenu());check(menu.getItem(0).title==tr("打开"));check(menu.getItem(0).subMenu!!.getItem(0).title=="Controls named")}
                    finally{activity.accounts.renameDefault(site,oldName)}
                }
                main{activity.open("http://127.0.0.1:8892/second")};loaded();main{check(activity.bottom.getChildAt(0).isEnabled);activity.current!!.web!!.goBack()};loaded()
                waitFor{activity.current!!.web!!.canGoForward()};main{check(activity.bottom.getChildAt(1).isEnabled)}
                val web=activity.current!!.web!!
                main{web.scrollTo(0,0)};Thread.sleep(200)
                main{val whole=activity.exports.snapshot(web,true);val visible=activity.exports.snapshot(web,false)
                    check(whole.height>visible.height*2){"Full-page screenshot truncated"}
                    val top=whole.getPixel(whole.width/2,20);val bottom=whole.getPixel(whole.width/2,whole.height-20)
                    check(Color.red(top)>200&&Color.green(top)<80){"Screenshot lost top"};check(Color.green(bottom)>200&&Color.red(bottom)<80){"Screenshot lost bottom"};whole.recycle();visible.recycle()
                    val qr=activity.exports.qr("https://example.test/path?q=中文");val pixels=IntArray(qr.width*qr.height);qr.getPixels(pixels,0,qr.width,0,0,qr.width,qr.height);check(QrDecoder.decode(com.google.zxing.RGBLuminanceSource(qr.width,qr.height,pixels))=="https://example.test/path?q=中文");qr.recycle()
                    val uaKey="site.127.0.0.1.ua";activity.prefs.edit().putString(uaKey,"Controls-Test-UA").apply();activity.configure(web,activity.currentUrl);check(web.settings.userAgentString=="Controls-Test-UA");activity.prefs.edit().remove(uaKey).apply();activity.configure(web,activity.currentUrl)
                }
                val callback=object:ActionMode.Callback{override fun onCreateActionMode(mode:ActionMode,menu:Menu)=true;override fun onPrepareActionMode(mode:ActionMode,menu:Menu):Boolean{menu.clear();return true};override fun onDestroyActionMode(mode:ActionMode){};override fun onActionItemClicked(mode:ActionMode,item:MenuItem)=false}
                eval("const s=window.getSelection();s.removeAllRanges();const range=document.createRange();range.selectNodeContents(document.getElementById('text'));s.addRange(range);")
                main{val wrapped=SelectionActions.wrap(activity,activity.current!!,web,callback);val action=activity.startActionMode(wrapped,ActionMode.TYPE_FLOATING)!!;val titles=(0 until action.menu.size()).map{action.menu.getItem(it).title.toString()};check(tr("翻译") in titles&&tr("页面查找") in titles);val find=(0 until action.menu.size()).map{action.menu.getItem(it)}.first{it.title==tr("页面查找")};check(wrapped.onActionItemClicked(action,find))}
                Thread.sleep(500);main{check(activity.root.childCount>=5){"Find did not open"}}
                if(PrivateSession.supported()){
                    main{account=activity.accounts.create("Controls secondary","http://127.0.0.1:8892/").id;val menu=PopupMenu(activity,activity.bottom).menu;CollectionActions.add(activity,menu,"http://127.0.0.1:8892/second");check(menu.getItem(0).hasSubMenu());check(menu.getItem(1).hasSubMenu());activity.openWithAccount("http://127.0.0.1:8892/second",account,true);created.add(activity.current!!.id);check(activity.current!!.accountId==account)};loaded()
                }
                check(JSONArray("[${eval("typeof window.__qinglanReadBlob")}]").getString(0)=="function"){"Blob bridge script not installed"}
                val blobSaved=CountDownLatch(1);var blobFailure:Throwable?=null
                val monitor=object:Instrumentation.ActivityMonitor(){override fun onStartActivity(intent:Intent):Instrumentation.ActivityResult? {
                    if(intent.action!=Intent.ACTION_CREATE_DOCUMENT)return null
                    try{val f=PageExports::class.java.getDeclaredField("pending").apply{isAccessible=true};val file=f.get(activity.exports) as java.io.File;val bytes=file.readBytes();check(bytes.size==200003){"Blob size ${bytes.size}"};check(bytes.indices.all{(bytes[it].toInt() and 255)==it%251}){"Blob chunk content corrupted"}}
                    catch(e:Throwable){blobFailure=e}finally{blobSaved.countDown()}
                    return Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null)
                }}
                r.addMonitor(monitor)
                try{
                    main{activity.blobs.request(activity.current!!,activity.current!!.web!!,"blob:http://127.0.0.1:8892/missing","","")}
                    Thread.sleep(400)
                    // Read the generated Blob URL inside the original account's WebView.
                    val blobUrl=JSONArray("[${eval("document.getElementById('blob').href")}]").getString(0)
                    main{activity.blobs.request(activity.current!!,activity.current!!.web!!,blobUrl,"","application/octet-stream","controls-blob.bin")}
                    fun blobDialog():AlertDialog? {val field=BlobDownloads::class.java.getDeclaredField("states").apply{isAccessible=true};val map=field.get(activity.blobs) as Map<*,*>;val state=map[activity.current!!.web]?:return null;return state.javaClass.getDeclaredField("dialog").apply{isAccessible=true}.get(state) as? AlertDialog}
                    waitFor{blobDialog()?.isShowing==true};main{blobDialog()!!.getButton(AlertDialog.BUTTON_POSITIVE).performClick()}
                    check(blobSaved.await(20,TimeUnit.SECONDS)){"Blob never reached save picker"};blobFailure?.let{throw it}
                }finally{r.removeMonitor(monitor)}
                val captured=CountDownLatch(3);var exportFailure:Throwable?=null;var expected="png"
                val exportMonitor=object:Instrumentation.ActivityMonitor(){override fun onStartActivity(intent:Intent):Instrumentation.ActivityResult? {
                    if(intent.action!=Intent.ACTION_CREATE_DOCUMENT)return null
                    try{val field=PageExports::class.java.getDeclaredField("pending").apply{isAccessible=true};val file=field.get(activity.exports) as java.io.File;val bytes=file.readBytes()
                        if(expected=="pdf")check(String(bytes.take(5).toByteArray())=="%PDF-"){"PDF header missing"}
                        else {val bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size);check(bitmap!=null&&bitmap.height>bitmap.width){"Invalid screenshot $expected"};bitmap.recycle()}
                    }catch(e:Throwable){exportFailure=e}finally{captured.countDown()}
                    return Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null)
                }}
                r.addMonitor(exportMonitor)
                fun views(root:View):List<View> = listOf(root)+(if(root is android.view.ViewGroup)(0 until root.childCount).flatMap{views(root.getChildAt(it))}else emptyList())
                try{listOf("png","jpeg","pdf").forEachIndexed{index,format->expected=format
                    main{PageExports::class.java.getDeclaredMethod("capture",java.lang.Boolean.TYPE).apply{isAccessible=true}.invoke(activity.exports,true)
                        val field=PageHost::class.java.getDeclaredField("dialog").apply{isAccessible=true};val dialog=field.get(activity.panels.pages) as android.app.Dialog
                        val all=views(dialog.window!!.decorView)
                        all.filterIsInstance<android.widget.RadioButton>().first{it.text.toString()==format.uppercase()}.performClick()
                        all.filterIsInstance<android.widget.Button>().first{it.text.toString()==tr("保存")}.performClick()
                    }
                    waitFor{captured.count==2L-index};exportFailure?.let{throw it};Thread.sleep(100)
                }}finally{r.removeMonitor(exportMonitor)}
                val shared=CountDownLatch(1);var shareFailure:Throwable?=null
                val shareMonitor=object:Instrumentation.ActivityMonitor(){override fun onStartActivity(intent:Intent):Instrumentation.ActivityResult? {
                    if(intent.action!=Intent.ACTION_CHOOSER)return null
                    try{@Suppress("DEPRECATION") val send=intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
                        @Suppress("DEPRECATION") val uri=send.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)!!
                        check(send.type=="image/png"&&send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION!=0)
                        val bytes=activity.contentResolver.openInputStream(uri)!!.use{it.readBytes()};check(bytes.size>1000){"Shared QR provider empty"}
                    }catch(e:Throwable){shareFailure=e}finally{shared.countDown()};return Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null)
                }}
                val oldImages=java.io.File(activity.cacheDir,"shared").listFiles().orEmpty().map{it.name}.toSet()
                r.addMonitor(shareMonitor);try{main{activity.prefs.edit().putString("shareFormat","qr").apply();activity.exports.share()}
                    if(!shared.await(2,TimeUnit.SECONDS)){
                        // Some vendor choosers bypass ActivityMonitor; verify their actual granted image.
                        val files=java.io.File(activity.cacheDir,"shared").listFiles().orEmpty().filter{it.name !in oldImages};check(files.size==1){"QR share image missing"}
                        val uri=android.net.Uri.parse("content://${activity.packageName}.images/${files.single().name}")
                        val bytes=activity.contentResolver.openInputStream(uri)!!.use{it.readBytes()};check(bytes.size>1000)
                    };shareFailure?.let{throw it}
                }finally{r.removeMonitor(shareMonitor)}
                result.putString("stream","PASS: native navigation and URL selection; edge controls; full screenshot top/bottom; PNG/JPEG/PDF saved content; QR encode/decode and share provider; UA; selection find/translate; collection account submenu and direct opening; Blob 200003-byte multi-chunk content.\n")
            }
        }catch(e:Throwable){passed=false;result.putString("stream","FAIL: ${e.javaClass.simpleName}: ${e.message}\n")}
        finally{if(mode!="cleanup")a?.let{activity->main{
            activity.security.cancel();activity.panels.pages.close();created.reversed().forEach{activity.closeTab(it)}
            if(account.isNotEmpty()&&activity.accounts.find(account)!=null)activity.accounts.remove(account){}
            val edit=activity.prefs.edit();keys.forEach{key->when(val value=oldPrefs[key]){is Boolean->edit.putBoolean(key,value);is String->edit.putString(key,value);is Int->edit.putInt(key,value);else->edit.remove(key)}};edit.apply();activity.security.clear()
            activity.tabs.indexOfFirst{it.id==oldTab}.takeIf{it>=0}?.let(activity::switchTab)
        }}}
        r.finish(if(passed)Activity.RESULT_OK else Activity.RESULT_CANCELED,result)
    }
}
