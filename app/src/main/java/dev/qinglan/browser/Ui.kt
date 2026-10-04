package dev.qinglan.browser

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*

class Ui(val context:Context,val dark:Boolean) {
    private val prefs=context.getSharedPreferences("preferences",Context.MODE_PRIVATE)
    private val palette=prefs.getString("palette","green")
    private val base=Color.parseColor(if(palette=="custom")prefs.getString("customColor","#286658")!! else Palette.colors[palette]?:"#286658")
    val bg=blend(base,if(dark)Color.BLACK else Color.WHITE,if(dark).91f else .97f)
    val panel=if(dark)blend(base,Color.BLACK,.85f)else Color.WHITE
    val text=if(dark)Color.parseColor("#E7EBEA")else Color.parseColor("#24312C")
    val muted=if(dark)Color.parseColor("#AEBBB5")else Color.parseColor("#64756D")
    val soft=blend(base,if(dark)Color.BLACK else Color.WHITE,if(dark).75f else .9f)
    val accent=if(dark)blend(base,Color.WHITE,.65f)else if(contrast(base)==Color.BLACK)blend(base,Color.BLACK,.48f)else base
    companion object {
        fun contrast(color:Int):Int {fun linear(n:Int):Double{val x=n/255.0;return if(x<=.04045)x/12.92 else Math.pow((x+.055)/1.055,2.4)};val l=.2126*linear(Color.red(color))+.7152*linear(Color.green(color))+.0722*linear(Color.blue(color));return if(l>.179)Color.BLACK else Color.WHITE}
        private fun blend(a:Int,b:Int,f:Float)=Color.rgb((Color.red(a)*(1-f)+Color.red(b)*f).toInt(),(Color.green(a)*(1-f)+Color.green(b)*f).toInt(),(Color.blue(a)*(1-f)+Color.blue(b)*f).toInt())
    }
    fun dp(n:Int)=(n*context.resources.displayMetrics.density+.5f).toInt()
    fun round(color:Int,radius:Int=16)=GradientDrawable().apply{setColor(color);cornerRadius=dp(radius).toFloat()}
    fun column(padding:Int=0)=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(padding),dp(padding),dp(padding),dp(padding))}
    fun row()=LinearLayout(context).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
    fun label(s:String,size:Float=15f,color:Int=text)=TextView(context).apply{text=s;textSize=size;setTextColor(color);setPadding(dp(4),dp(5),dp(4),dp(5))}
    fun title(s:String)=label(s,22f).apply{typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)}
    fun button(s:String,primary:Boolean=false,action:()->Unit)=Button(context).apply {
        text=s;isAllCaps=false;textSize=14f;minHeight=dp(48);minimumHeight=dp(48);minWidth=0;minimumWidth=0
        setTextColor(if(primary)contrast(accent) else this@Ui.text);background=round(if(primary)accent else soft,12)
        setPadding(dp(12),dp(6),dp(12),dp(6));setOnClickListener{action()}
    }
    fun icon(name:String,description:String,action:()->Unit)=IconView(context,name,accent).apply {
        contentDescription=description;isClickable=true;isFocusable=true
        background=context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless)).let{a->try{a.getDrawable(0)}finally{a.recycle()}}
        layoutParams=LinearLayout.LayoutParams(dp(48),dp(48));setOnClickListener{action()}
    }
    fun edit(hint:String,value:String="",multiline:Boolean=false)=EditText(context).apply {
        this.hint=hint;setText(value);textSize=16f;setTextColor(this@Ui.text);setHintTextColor(muted)
        background=round(soft,12);setPadding(dp(12),dp(10),dp(12),dp(10));minHeight=dp(48)
        inputType=android.text.InputType.TYPE_CLASS_TEXT or if(multiline)android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0
        if(multiline){minLines=4;maxLines=10;gravity=Gravity.TOP}else setSingleLine(true)
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(10)}
    }
    fun rule()=View(context).apply{setBackgroundColor(soft);layoutParams=LinearLayout.LayoutParams(-1,dp(1))}
    fun item(title:String,subtitle:String="",action:()->Unit):LinearLayout {
        val r=row();r.setPadding(dp(5),dp(6),dp(5),dp(6));r.minimumHeight=dp(56)
        val c=column();c.addView(label(title));if(subtitle.isNotBlank())c.addView(label(subtitle,12f,muted))
        r.addView(c,LinearLayout.LayoutParams(0,-2,1f));r.addView(label("›",22f,muted));r.setOnClickListener{action()};r.isFocusable=true;return r
    }
}

class IconView @JvmOverloads constructor(context:Context,private val name:String="globe",private val tint:Int=Color.DKGRAY):View(context) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=tint;strokeWidth=1.7f;style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
    private val path=Path()
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas);canvas.save();val s=resources.displayMetrics.density;canvas.translate((width-24*s)/2,(height-24*s)/2);canvas.scale(s,s)
        fun line(vararg pts:Float){path.reset();path.moveTo(pts[0],pts[1]);for(i in 2 until pts.size step 2)path.lineTo(pts[i],pts[i+1]);canvas.drawPath(path,p)}
        fun rect(l:Float,t:Float,r:Float,b:Float)=canvas.drawRoundRect(l,t,r,b,2f,2f,p)
        when(name){
            "media"->{rect(2f,4f,22f,20f);line(10f,8f,16f,12f,10f,16f,10f,8f)}
            "shield"->{line(12f,2f,21f,6f,20f,15f,12f,22f,4f,15f,3f,6f,12f,2f);line(8f,12f,11f,15f,17f,9f)}
            "back"->line(15f,5f,8f,12f,15f,19f)
            "forward"->line(9f,5f,16f,12f,9f,19f)
            "home"->{line(3f,11f,12f,3f,21f,11f);line(6f,10f,6f,21f,10f,21f,10f,15f,14f,15f,14f,21f,18f,21f,18f,10f)}
            "menu"->{line(4f,6f,20f,6f);line(4f,12f,20f,12f);line(4f,18f,20f,18f)}
            "tabs"->{rect(6f,6f,21f,21f);line(3f,17f,3f,3f,17f,3f)}
            "refresh"->{canvas.drawArc(4f,4f,20f,20f,40f,285f,false,p);line(20f,3f,20f,9f,14f,9f)}
            "qr"->{rect(3f,3f,9f,9f);rect(15f,3f,21f,9f);rect(3f,15f,9f,21f);line(15f,15f,18f,15f,18f,18f,21f,18f,21f,21f);line(15f,18f,15f,21f)}
            "close"->{line(6f,6f,18f,18f);line(6f,18f,18f,6f)}
            "plus"->{line(12f,4f,12f,20f);line(4f,12f,20f,12f)}
            "site"->{line(4f,6f,20f,6f);line(4f,18f,20f,18f);rect(7f,3f,11f,9f);rect(14f,15f,18f,21f)}
            "bookmark"->line(6f,3f,18f,3f,18f,21f,12f,17f,6f,21f,6f,3f)
            "book"->{line(12f,5f,12f,21f);line(12f,5f,3f,3f,3f,19f,12f,21f,21f,19f,21f,3f,12f,5f)}
            "history"->{canvas.drawCircle(12f,12f,9f,p);line(12f,6f,12f,12f,16f,14f)}
            "download"->{line(12f,3f,12f,16f);line(7f,11f,12f,16f,17f,11f);line(4f,17f,4f,21f,20f,21f,20f,17f)}
            "desktop"->{rect(2f,3f,22f,17f);line(12f,17f,12f,22f);line(7f,22f,17f,22f)}
            "moon"->{path.reset();path.moveTo(15f,3f);path.cubicTo(1f,1f,0f,21f,14f,21f);path.cubicTo(19f,21f,22f,16f,21f,13f);path.cubicTo(12f,18f,8f,8f,15f,3f);canvas.drawPath(path,p)}
            "search"->{canvas.drawCircle(10f,10f,6f,p);line(15f,15f,21f,21f)}
            "speaker"->{line(3f,9f,7f,9f,12f,5f,12f,19f,7f,15f,3f,15f,3f,9f);canvas.drawArc(10f,7f,20f,17f,-60f,120f,false,p);canvas.drawArc(8f,3f,24f,21f,-60f,120f,false,p)}
            "folder"->{line(3f,7f,3f,4f,10f,4f,13f,7f,21f,7f,21f,20f,3f,20f,3f,7f);line(3f,9f,21f,9f)}
            "image"->{rect(3f,3f,21f,21f);canvas.drawCircle(8f,8f,1f,p);line(3f,18f,10f,11f,14f,15f,17f,12f,21f,16f)}
            "fullscreen"->{line(3f,9f,3f,3f,9f,3f);line(15f,3f,21f,3f,21f,9f);line(21f,15f,21f,21f,15f,21f);line(9f,21f,3f,21f,3f,15f)}
            "cookie"->{canvas.drawCircle(12f,12f,9f,p);for((x,y) in listOf(8f to 8f,15f to 7f,11f to 13f,7f to 16f,17f to 15f))canvas.drawCircle(x,y,.7f,p)}
            "settings"->{canvas.drawCircle(12f,12f,4f,p);canvas.drawCircle(12f,12f,8f,p);for(i in 0..7){val a=i*Math.PI/4;line((12+8*kotlin.math.cos(a)).toFloat(),(12+8*kotlin.math.sin(a)).toFloat(),(12+11*kotlin.math.cos(a)).toFloat(),(12+11*kotlin.math.sin(a)).toFloat())}}
            else->{canvas.drawCircle(12f,12f,9f,p);canvas.drawOval(8f,3f,16f,21f,p);line(3f,12f,21f,12f)}
        }
        canvas.restore()
    }
}
