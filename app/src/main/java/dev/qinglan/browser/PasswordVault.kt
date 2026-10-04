package dev.qinglan.browser

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keystore-backed AES-GCM. Decryption failure is never treated as an empty vault. */
class PasswordVault(context:Context,filename:String="passwords.enc",private val alias:String="qinglan.passwords.v1"){
    private val file=AtomicFile(File(context.filesDir,filename))
    private fun key():SecretKey {val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)};if(store.containsAlias(alias))return store.getKey(alias,null)as SecretKey
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build())}.generateKey()}
    fun read():List<SavedPassword>{if(!file.baseFile.exists())return emptyList();try{val bytes=file.openRead().use{it.readBytes()};require(bytes.size>29&&bytes[0]==1.toByte());val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(1,13)));return PasswordCodec.parseJson(JSONArray(String(cipher.doFinal(bytes.copyOfRange(13,bytes.size)),Charsets.UTF_8)))}catch(e:Exception){throw IllegalStateException(tr("无法解密本机密码库，原文件已保留"))}}
    fun write(entries:List<SavedPassword>){entries.forEach(PasswordCodec::validate);val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());val bytes=byteArrayOf(1)+cipher.iv+cipher.doFinal(PasswordCodec.json(entries).toString().toByteArray());val out=file.startWrite();try{out.write(bytes);file.finishWrite(out)}catch(e:Exception){file.failWrite(out);throw e}}
    fun upsert(entry:SavedPassword){val entries=read().filterNot{it.origin==entry.origin&&it.username==entry.username}+entry;write(entries)}
}
