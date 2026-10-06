package dev.stone.pinshot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File
import kotlin.math.max

/** Copy a shared URI while the receiving activity still owns its temporary read grant. */
internal object SharedImageImport {
    fun copy(context: Context, uri: Uri): File {
        val bitmap = ImageDecoder.decodeBitmap(
            ImageDecoder.createSource(context.contentResolver, uri)
        ) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > 4096) {
                decoder.setTargetSize(
                    max(1, (info.size.width * 4096L / longest).toInt()),
                    max(1, (info.size.height * 4096L / longest).toInt())
                )
            }
        }
        var file: File? = null
        try {
            file = File.createTempFile("shared-pin-", ".png", context.cacheDir)
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            return file
        } catch (error: Throwable) {
            file?.delete()
            throw error
        } finally {
            bitmap.recycle()
        }
    }
}
