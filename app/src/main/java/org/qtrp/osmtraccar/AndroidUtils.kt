package org.qtrp.osmtraccar

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

object AndroidUtils {
    fun getUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context,  "org.qtrp.osmtraccar.fileprovider", file)
    }

    fun resourceToUri(ctx: Context, resID: Int): Uri {
        return Uri.parse(
            ContentResolver.SCHEME_ANDROID_RESOURCE +
                "://${ctx.resources.getResourcePackageName(resID)}" +
                "/${ctx.resources.getResourceTypeName(resID)}" +
                "/${ctx.resources.getResourceEntryName(resID)}"
        )
    }

    // shamelessly copy-pasted from stack overflow: I now feel like a real java programmer!
    // source: https://stackoverflow.com/a/55920601
    fun sha256(msg: String): String {
        val digest = MessageDigest.getInstance("SHA-256")

        digest.reset()

        return bin2hex(digest.digest(msg.toByteArray()))
    }

    private fun bin2hex(data: ByteArray): String {
        val hex = StringBuilder(data.size * 2)
        for (b in data) hex.append(String.format("%02x", b))
        return hex.toString()
    }

    fun <T> removeNulls(l: List<T?>): List<T>{
        var result = mutableListOf<T>()
        for (x in l) {
            if (x != null) {
                result.add(x)
            }
        }
        return result
    }

    fun <T> noNulls(l: List<T?>): List<T>{
        var result = mutableListOf<T>()
        for (x in l) {
            if (x == null) {
                throw Exception("null element in list")
            }
            result.add(x)
        }
        return result
    }

    fun copyStream(source: InputStream, target: OutputStream) {
        val buf = ByteArray(8192)
        var length: Int
        while (source.read(buf).also { length = it } > 0) {
            target.write(buf, 0, length)
        }
    }
}