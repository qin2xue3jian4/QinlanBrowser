@file:Suppress("DEPRECATION")
package dev.qinglan.browser

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.hardware.Camera
import android.os.Bundle
import android.view.*
import android.widget.*
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import java.util.concurrent.Executors

/** Camera access belongs to this native scanner only; webpages receive no camera permission. */
class QrScanActivity:Activity(),SurfaceHolder.Callback {
    private lateinit var ui:Ui
    private lateinit var surface:SurfaceView
    private lateinit var frame:FrameLayout
    private lateinit var status:TextView
    private var camera:Camera?=null
    private var ready=false
    private var resumed=false
    private var handling=false
    private var permissionAsked=false
    private val worker=Executors.newSingleThreadExecutor()
    override fun onCreate(state:Bundle?){
        val prefs=getSharedPreferences("preferences",MODE_PRIVATE)
        val dark=when(prefs.getString("theme","system")){"dark"->true;"light"->false;else->resources.configuration.uiMode and 48==32}
        ui=Ui(this,dark);setTheme(if(dark)R.style.AppThemeDark else R.style.AppTheme);super.onCreate(state)
        permissionAsked=state?.getBoolean("permissionAsked")?:false
        val root=ui.column().apply{setBackgroundColor(ui.bg)}
        val header=ui.row();header.addView(ui.icon("back","返回网页"){finish()});header.addView(ui.title("扫描二维码"));root.addView(header)
        status=ui.label("将二维码置于画面内，识别网页后自动打开",14f,ui.muted);root.addView(status)
        frame=FrameLayout(this).apply{setBackgroundColor(android.graphics.Color.BLACK)}
        surface=SurfaceView(this);surface.holder.addCallback(this);frame.addView(surface,FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));root.addView(frame,LinearLayout.LayoutParams(-1,0,1f))
        root.addView(ui.button("从图片识别二维码"){handling=true;releaseCamera();startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),1)},LinearLayout.LayoutParams(-1,ui.dp(56)))
        setContentView(root)
        if(android.os.Build.VERSION.SDK_INT>=30){window.setDecorFitsSystemWindows(false);root.setOnApplyWindowInsetsListener{v,insets->val b=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);insets};window.insetsController?.setSystemBarsAppearance(if(dark)0 else android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)}else root.fitsSystemWindows=true
    }
    override fun onSaveInstanceState(out:Bundle){super.onSaveInstanceState(out);out.putBoolean("permissionAsked",permissionAsked)}
    override fun onResume(){super.onResume();resumed=true
        if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCamera()
        else if(!permissionAsked){permissionAsked=true;requestPermissions(arrayOf(Manifest.permission.CAMERA),2)}
        else status.text="相机权限未开启，可从图片识别二维码"
    }
    override fun onPause(){resumed=false;releaseCamera();super.onPause()}
    override fun onDestroy(){worker.shutdownNow();super.onDestroy()}
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,results:IntArray){super.onRequestPermissionsResult(requestCode,permissions,results);if(requestCode==2){if(results.firstOrNull()==PackageManager.PERMISSION_GRANTED)startCamera()else status.text="相机权限未开启，可从图片识别二维码"}}
    override fun surfaceCreated(holder:SurfaceHolder){ready=true;startCamera()}
    override fun surfaceDestroyed(holder:SurfaceHolder){ready=false;releaseCamera()}
    override fun surfaceChanged(holder:SurfaceHolder,format:Int,width:Int,height:Int){}
    private fun startCamera(){
        if(!resumed||!ready||handling||camera!=null||checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return
        try{
            val id=(0 until Camera.getNumberOfCameras()).firstOrNull{val info=Camera.CameraInfo();Camera.getCameraInfo(it,info);info.facing==Camera.CameraInfo.CAMERA_FACING_BACK}?:throw Exception("没有可用的后置相机")
            val cam=Camera.open(id);camera=cam;val params=cam.parameters
            val size=params.supportedPreviewSizes.filter{it.width<=1280&&it.height<=1280}.maxByOrNull{it.width*it.height}?:params.previewSize
            params.setPreviewSize(size.width,size.height);params.previewFormat=android.graphics.ImageFormat.NV21
            if(params.supportedFocusModes?.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)==true)params.focusMode=Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            cam.parameters=params
            val info=Camera.CameraInfo();Camera.getCameraInfo(id,info)
            val rotation=when(windowManager.defaultDisplay.rotation){Surface.ROTATION_90->90;Surface.ROTATION_180->180;Surface.ROTATION_270->270;else->0}
            val orientation=(info.orientation-rotation+360)%360;cam.setDisplayOrientation(orientation)
            frame.post{if(camera===cam){val w=if(orientation%180==0)size.width else size.height;val h=if(orientation%180==0)size.height else size.width;val scale=minOf(frame.width.toFloat()/w,frame.height.toFloat()/h);surface.layoutParams=FrameLayout.LayoutParams((w*scale).toInt(),(h*scale).toInt(),Gravity.CENTER)}}
            cam.setPreviewDisplay(surface.holder);cam.startPreview();nextFrame(cam)
        }catch(e:Exception){releaseCamera();status.text="无法打开相机，可使用图片识别"}
    }
    private fun nextFrame(cam:Camera){if(camera!==cam||!resumed||handling)return
        runCatching{cam.setOneShotPreviewCallback{data,sourceCamera->
            if(camera!==sourceCamera||!resumed||handling)return@setOneShotPreviewCallback
            val size=runCatching{sourceCamera.parameters.previewSize}.getOrNull()?:return@setOneShotPreviewCallback
            worker.execute{
                val text=runCatching{QrDecoder.decode(PlanarYUVLuminanceSource(data,size.width,size.height,0,0,size.width,size.height,false))}.getOrNull()
                runOnUiThread{if(camera===cam&&resumed&&!handling){if(text!=null)found(text)else surface.postDelayed({nextFrame(cam)},180)}}
            }
        }}
    }
    private fun releaseCamera(){camera?.let{runCatching{it.setPreviewCallback(null);it.stopPreview();it.release()}};camera=null}
    private fun found(text:String){
        handling=true;releaseCamera()
        val url=QrDecoder.webUrl(text)
        if(url!=null){setResult(RESULT_OK,Intent().putExtra("url",url));finish();return}
        AlertDialog.Builder(this).setTitle("识别结果不是网页地址").setMessage(text.take(2000)).setPositiveButton("继续扫描",null).setOnDismissListener{handling=false;startCamera()}.show()
    }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=1)return
        val uri=data?.data
        if(resultCode!=RESULT_OK||uri==null){handling=false;return}
        handling=true;releaseCamera()
        status.text="正在识别图片…"
        worker.execute{
            val result=runCatching{
                val options=BitmapFactory.Options().apply{inJustDecodeBounds=true}
                contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,options)}
                require(options.outWidth>0&&options.outHeight>0){"无法读取图片"}
                var sample=1;while(maxOf(options.outWidth,options.outHeight)/sample>2048)sample*=2
                options.inSampleSize=sample;options.inJustDecodeBounds=false
                val bitmap=contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,options)}?:throw Exception("无法读取图片")
                try{val pixels=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height);QrDecoder.decode(RGBLuminanceSource(bitmap.width,bitmap.height,pixels))}finally{bitmap.recycle()}
            }
            runOnUiThread{if(!isFinishing&&!isDestroyed){val text=result.getOrNull();if(text!=null)found(text)else{status.text="未识别到二维码，请选择清晰图片或继续扫描";handling=false;startCamera()}}}
        }
    }
}
