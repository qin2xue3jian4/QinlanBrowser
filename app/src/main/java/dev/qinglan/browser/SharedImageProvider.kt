package dev.qinglan.browser

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/** Read-only, grant-only access to generated QR images. No browsing data is exposed. */
class SharedImageProvider:ContentProvider(){
    override fun onCreate()=true
    private fun file(uri:Uri):File {
        require(uri.pathSegments.size==1)
        val name=uri.lastPathSegment.orEmpty();require(name.matches(Regex("qr-[a-f0-9-]+\\.png")))
        return File(requireNotNull(context).cacheDir,"shared/$name").also{require(it.isFile)}
    }
    override fun getType(uri:Uri)="image/png"
    override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {require(mode=="r");return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY)}
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor {
        val f=file(uri);val columns=projection?:arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE)
        return MatrixCursor(columns).apply{addRow(columns.map{when(it){OpenableColumns.DISPLAY_NAME->f.name;OpenableColumns.SIZE->f.length();else->null}})}
    }
    override fun insert(uri:Uri,values:ContentValues?):Uri?=throw UnsupportedOperationException()
    override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?)=0
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?)=0
}
