package dev.qinglan.browser
import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import java.io.File

object FeatureChecks {
    fun run(runner:Instrumentation){val context=runner.targetContext;val name="qa-passwords.enc";val alias="qinglan.qa.passwords";val result=Bundle()
        try{
            val vault=PasswordVault(context,name,alias);val entry=SavedPassword("https://synthetic.example.test/login","qa-user","qa-secret-password", "Synthetic")
            vault.write(listOf(entry));check(vault.read()==listOf(entry));val bytes=File(context.filesDir,name).readBytes();check(!String(bytes).contains(entry.password));check(!String(bytes).contains(entry.username))
            val changed=bytes.clone();changed[changed.lastIndex]=(changed.last().toInt() xor 1).toByte();File(context.filesDir,name).writeBytes(changed);check(runCatching{vault.read()}.isFailure)
            val raw=BackupCodec.export(null,null,null,listOf(entry));val encrypted=BackupCrypto.encrypt(raw,"qa-1234".toCharArray());check(BackupCrypto.decrypt(encrypted,"qa-1234".toCharArray())==raw);check(runCatching{BackupCrypto.decrypt(encrypted,"wrong".toCharArray())}.isFailure)
            val parsed=BookmarkHtml.parse("<DL><p><DT><H3>Folder</H3><DL><p><DT><A HREF=\"https://example.test/\">Example</A></DL><p></DL>");check(parsed.size==2&&parsed[0].folder&&parsed[1].parent==parsed[0].id)
            result.putString("stream","PASS: Keystore encrypted vault round trip, plaintext exclusion, tamper rejection, encrypted backup, wrong password, bookmark HTML folders\n");runner.finish(Activity.RESULT_OK,result)
        }catch(e:Throwable){result.putString("stream","FAIL: ${e.javaClass.simpleName}\n");runner.finish(Activity.RESULT_CANCELED,result)}finally{context.deleteFile(name);context.deleteFile("$name.bak");java.security.KeyStore.getInstance("AndroidKeyStore").apply{load(null);if(containsAlias(alias))deleteEntry(alias)}}
    }
}
