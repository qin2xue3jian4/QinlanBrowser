package dev.qinglan.browser

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

fun EditText.onChange(changed:(String)->Unit) {
    addTextChangedListener(object:TextWatcher {
        override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
        override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){changed(s.toString())}
        override fun afterTextChanged(s:Editable?){}
    })
}
