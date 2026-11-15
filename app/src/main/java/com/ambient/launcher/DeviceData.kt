package com.ambient.launcher

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.time.LocalDate
import java.time.ZoneId

// ---------- Calendar ----------

data class CalEvent(val title: String, val begin: Long, val end: Long, val allDay: Boolean)

fun hasCalendarPermission(ctx: Context): Boolean {
    return ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED
}

fun loadTodayEvents(ctx: Context): List<CalEvent> {
    if (!hasCalendarPermission(ctx)) return emptyList()
    val zone = ZoneId.systemDefault()
    val start = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
    val end = start + 24L * 60L * 60L * 1000L

    val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
    ContentUris.appendId(builder, start)
    ContentUris.appendId(builder, end)

    val projection = arrayOf(
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY
    )
    val out = ArrayList<CalEvent>()
    try {
        val cursor = ctx.contentResolver.query(
            builder.build(),
            projection,
            null,
            null,
            CalendarContract.Instances.BEGIN + " ASC"
        )
        if (cursor != null) {
            cursor.use { c ->
                while (c.moveToNext()) {
                    out.add(
                        CalEvent(
                            title = c.getString(0) ?: "Event",
                            begin = c.getLong(1),
                            end = c.getLong(2),
                            allDay = c.getInt(3) == 1
                        )
                    )
                }
            }
        }
    } catch (e: Exception) {
        // Ignore: the calendar provider may be missing on some devices.
    }
    return out
}

// ---------- Installed apps ----------

data class AppItem(val label: String, val pkg: String, val icon: ImageBitmap)

suspend fun loadApps(pm: PackageManager, selfPackage: String): List<AppItem> =
    withContext(Dispatchers.Default) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = ArrayList<AppItem>()
        for (ri in pm.queryIntentActivities(intent, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == selfPackage) continue
            try {
                val icon = ri.loadIcon(pm).toBitmap(128, 128).asImageBitmap()
                list.add(AppItem(ri.loadLabel(pm).toString(), pkg, icon))
            } catch (e: Exception) {
                // Skip apps whose icon cannot be loaded.
            }
        }
        list.sortedBy { it.label.lowercase() }
    }

// ---------- Photos and wallpaper files ----------

object ImageStore {
    fun photosDir(ctx: Context): File {
        val dir = File(ctx.filesDir, "photos")
        dir.mkdirs()
        return dir
    }

    fun wallpaperFile(ctx: Context): File = File(ctx.filesDir, "wallpaper.jpg")

    fun listPhotos(ctx: Context): List<File> {
        val files = photosDir(ctx).listFiles() ?: return emptyList()
        return files.filter { it.extension == "jpg" }.sortedBy { it.name }
    }

    fun clearPhotos(ctx: Context) {
        photosDir(ctx).listFiles()?.forEach { it.delete() }
    }

    private fun exifRotation(stream: InputStream): Int {
        val orientation = ExifInterface(stream).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }

    /** Copies an image the user picked into app storage, scaled down and upright. */
    fun importImage(ctx: Context, uri: Uri, dest: File, maxSide: Int): Boolean {
        return try {
            val resolver = ctx.contentResolver
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) {
                sample *= 2
            }
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return false

            val rotation = try {
                resolver.openInputStream(uri)?.use { exifRotation(it) } ?: 0
            } catch (e: Exception) {
                0
            }

            val scale = maxSide.toFloat() / maxOf(decoded.width, decoded.height).toFloat()
            val matrix = Matrix()
            if (scale < 1f) matrix.postScale(scale, scale)
            if (rotation != 0) matrix.postRotate(rotation.toFloat())
            val result = if (matrix.isIdentity) {
                decoded
            } else {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            }

            dest.parentFile?.mkdirs()
            FileOutputStream(dest).use { out ->
                result.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            true
        } catch (e: Throwable) {
            false
        }
    }

    fun loadBitmap(file: File, maxSide: Int): ImageBitmap? {
        return try {
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) {
                sample *= 2
            }
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
        } catch (e: Throwable) {
            null
        }
    }
}
