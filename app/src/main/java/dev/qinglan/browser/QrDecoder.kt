package dev.qinglan.browser

import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

object QrDecoder {
    fun decode(source:LuminanceSource):String? {
        val hints=mapOf(DecodeHintType.TRY_HARDER to true,DecodeHintType.CHARACTER_SET to "UTF-8")
        for(s in listOf(source,source.invert())) {
            val result=runCatching{QRCodeReader().decode(BinaryBitmap(HybridBinarizer(s)),hints).text}.getOrNull()
            if(result!=null)return result
        };return null
    }
    fun webUrl(text:String):String?=text.trim().takeIf{it.length<=8192&&runCatching{val u=java.net.URI(it);u.scheme in listOf("https","http")&&!u.host.isNullOrBlank()&&u.userInfo==null}.getOrDefault(false)}
}
