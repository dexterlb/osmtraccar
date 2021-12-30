package org.qtrp.osmtraccar

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

object AndroidUtils {
    fun getUriForFile(context: Context, file: File): Uri {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            Uri.fromFile(file)
        } else {
            FileProvider.getUriForFile(context,  "net.osmand.telegram.fileprovider", file)
        }
    }

    fun resourceToUri(ctx: Context, resID: Int): Uri {
        return Uri.parse(
            ContentResolver.SCHEME_ANDROID_RESOURCE +
                "://${ctx.resources.getResourcePackageName(resID)}" +
                "/${ctx.resources.getResourceTypeName(resID)}" +
                "/${ctx.resources.getResourceEntryName(resID)}"
        )
    }
}