package dev.qinglan.browser

import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCrypto {
    private const val iterations=210000
    private const val marker="qinglan-encrypted-v1"
    private fun derive(password:CharArray,salt:ByteArray):ByteArray {val spec=PBEKeySpec(password,salt,iterations,256);return try{SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded}finally{spec.clearPassword()}}
    fun encrypted(text:String)=runCatching{JSONObject(text).optString("format")=="qinglan-encrypted"}.getOrDefault(false)
    fun encrypt(text:String,password:CharArray):String {
        require(password.size>=4){tr("导出密码至少 4 个字符")};require(text.toByteArray().size<=1_048_576){tr("备份超过 1 MB")}
        val random=SecureRandom();val salt=ByteArray(16).also(random::nextBytes);val iv=ByteArray(12).also(random::nextBytes);val key=derive(password,salt)
        try{val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,iv));c.updateAAD(marker.toByteArray());val encode=Base64.getEncoder()
            return JSONObject().put("format","qinglan-encrypted").put("version",1).put("iterations",iterations).put("salt",encode.encodeToString(salt)).put("iv",encode.encodeToString(iv)).put("ciphertext",encode.encodeToString(c.doFinal(text.toByteArray(Charsets.UTF_8)))).toString()
        }finally{key.fill(0)}
    }
    fun decrypt(text:String,password:CharArray):String {
        require(text.toByteArray().size<=2_097_152){tr("加密文件过大")};val o=JSONObject(text);require(o.getString("format")=="qinglan-encrypted"&&o.getInt("version")==1&&o.getInt("iterations")==iterations){tr("不支持的加密文件格式")}
        val decode=Base64.getDecoder();val salt=decode.decode(o.getString("salt"));val iv=decode.decode(o.getString("iv"));val body=decode.decode(o.getString("ciphertext"));require(salt.size==16&&iv.size==12&&body.size in 16..1_048_592){tr("加密文件格式无效")};val key=derive(password,salt)
        try{val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,iv));c.updateAAD(marker.toByteArray());return String(c.doFinal(body),Charsets.UTF_8)}catch(e:Exception){throw IllegalArgumentException(tr("密码不正确，或文件已损坏")) }finally{key.fill(0)}
    }
}
