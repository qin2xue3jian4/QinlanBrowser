package dev.qinglan.browser

import android.content.Context
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText

/** TextView adds navigation flags for nearby buttons; this field submits a URL. */
class AddressField(context:Context):EditText(context) {
    private var selectOnRelease=false
    override fun onTouchEvent(event:android.view.MotionEvent):Boolean {
        if(event.actionMasked==android.view.MotionEvent.ACTION_DOWN)selectOnRelease=!hasFocus()
        val handled=super.onTouchEvent(event)
        if(event.actionMasked==android.view.MotionEvent.ACTION_UP&&selectOnRelease){selectOnRelease=false;post{if(hasFocus())selectAll()}}
        if(event.actionMasked==android.view.MotionEvent.ACTION_CANCEL)selectOnRelease=false
        return handled
    }
    override fun performClick():Boolean = super.performClick()
    override fun onCreateInputConnection(outAttrs:EditorInfo):InputConnection? {
        val connection=super.onCreateInputConnection(outAttrs)
        outAttrs.imeOptions=(outAttrs.imeOptions and
            (EditorInfo.IME_MASK_ACTION or EditorInfo.IME_FLAG_NAVIGATE_NEXT or EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS).inv()) or EditorInfo.IME_ACTION_GO
        outAttrs.actionId=EditorInfo.IME_ACTION_GO
        outAttrs.actionLabel=tr("前往")
        return connection
    }
}
