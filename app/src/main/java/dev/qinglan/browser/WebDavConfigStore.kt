package dev.qinglan.browser

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Separate from browser preferences and exports; Android backup is disabled for this app. */
class WebDavConfigStore(context:Context,filename:String="webdav.enc",private val alias:String="qinglan.webdav.v1") {
    private val file=AtomicFile(File(context.filesDir,filename))
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        if(store.containsAlias(alias))return store.getKey(alias,null) as SecretKey
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    @Synchronized fun read():WebDavConfig? {
        if(!file.baseFile.exists())return null
        try {
            val bytes=file.openRead().use{it.readBytes()};require(bytes.size in 30..32_768&&bytes[0]==1.toByte())
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(1,13)))
            val json=JSONObject(cipher.doFinal(bytes.copyOfRange(13,bytes.size)).toString(Charsets.UTF_8))
            return WebDavConfig(json.getString("directory"),json.getString("username"),json.getString("password")).validated()
        }catch(e:Exception){throw IllegalStateException(tr("无法解密 WebDAV 配置，请重新保存；原文件已保留"))}
    }
    @Synchronized fun write(config:WebDavConfig) {
        val c=config.validated()
        val raw=JSONObject().put("directory",c.directory).put("username",c.username).put("password",c.password).toString().toByteArray(Charsets.UTF_8)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
        val bytes=byteArrayOf(1)+cipher.iv+cipher.doFinal(raw)
        val out=file.startWrite()
        try{out.write(bytes);file.finishWrite(out)}catch(e:Exception){file.failWrite(out);throw e}
    }
    @Synchronized fun clear(){file.delete()}
}
