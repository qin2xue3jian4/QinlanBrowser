package dev.qinglan.browser

import android.content.Context
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText

/** TextView adds navigation flags for nearby buttons; this field submits a URL. */
class AddressField(context:Context):EditText(context) {
    override fun onCreateInputConnection(outAttrs:EditorInfo):InputConnection? {
        val connection=super.onCreateInputConnection(outAttrs)
        outAttrs.imeOptions=(outAttrs.imeOptions and
            (EditorInfo.IME_MASK_ACTION or EditorInfo.IME_FLAG_NAVIGATE_NEXT or EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS).inv()) or EditorInfo.IME_ACTION_GO
        outAttrs.actionId=EditorInfo.IME_ACTION_GO
        outAttrs.actionLabel="前往"
        return connection
    }
}
