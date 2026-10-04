package dev.qinglan.browser

import com.google.zxing.*
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.*
import org.junit.Test

class QrDecoderTest {
    @Test fun readsNormalAndInvertedQr(){
        val url="https://example.com/shared?q=qinglan"
        val matrix=QRCodeWriter().encode(url,BarcodeFormat.QR_CODE,400,400)
        val pixels=IntArray(400*400){if(matrix[it%400,it/400])0xff000000.toInt()else 0xffffffff.toInt()}
        assertEquals(url,QrDecoder.decode(RGBLuminanceSource(400,400,pixels)))
        assertEquals(url,QrDecoder.decode(RGBLuminanceSource(400,400,pixels).invert()))
        // Synthetic image for the device's document-picker scan check.
        val image=java.awt.image.BufferedImage(400,400,java.awt.image.BufferedImage.TYPE_INT_RGB)
        image.setRGB(0,0,400,400,pixels,0,400)
        val file=java.io.File("build/qa/qr-web.png");file.parentFile?.mkdirs();javax.imageio.ImageIO.write(image,"png",file)
    }
    @Test fun onlyNavigatesWebLinks(){assertEquals("https://example.com/",QrDecoder.webUrl(" https://example.com/ "));listOf("javascript:alert(1)","intent://example.com/","file:///secret","WIFI:S:test;","https://user:pass@example.com/").forEach{assertNull(QrDecoder.webUrl(it))}}
}
