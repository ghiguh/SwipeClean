package com.aditya.swipeclean

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns
import android.provider.MediaStore.MediaColumns
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MediaRepository(private val ctx: Context) {
    private val resolver get() = ctx.contentResolver
    private fun exifFmt() = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)

    suspend fun load(): List<MediaItem> = withContext(Dispatchers.IO) {
        val out = ArrayList<MediaItem>()
        val proj = arrayOf(
            FileColumns._ID, FileColumns.MEDIA_TYPE, MediaColumns.DISPLAY_NAME,
            MediaColumns.DATE_TAKEN, MediaColumns.DATE_ADDED, MediaColumns.DURATION, MediaColumns.MIME_TYPE
        )
        val sel = "${FileColumns.MEDIA_TYPE}=? OR ${FileColumns.MEDIA_TYPE}=?"
        val args = arrayOf(FileColumns.MEDIA_TYPE_IMAGE.toString(), FileColumns.MEDIA_TYPE_VIDEO.toString())
        resolver.query(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), proj, sel, args,
            "${MediaColumns.DATE_ADDED} DESC"
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(FileColumns._ID)
            val iType = c.getColumnIndexOrThrow(FileColumns.MEDIA_TYPE)
            val iName = c.getColumnIndexOrThrow(MediaColumns.DISPLAY_NAME)
            val iTaken = c.getColumnIndexOrThrow(MediaColumns.DATE_TAKEN)
            val iAdded = c.getColumnIndexOrThrow(MediaColumns.DATE_ADDED)
            val iDur = c.getColumnIndexOrThrow(MediaColumns.DURATION)
            val iMime = c.getColumnIndexOrThrow(MediaColumns.MIME_TYPE)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val video = c.getInt(iType) == FileColumns.MEDIA_TYPE_VIDEO
                val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                out.add(
                    MediaItem(
                        id = id, uri = ContentUris.withAppendedId(base, id), isVideo = video,
                        name = c.getString(iName) ?: "", dateTakenMs = c.getLong(iTaken),
                        dateAddedMs = c.getLong(iAdded) * 1000L, durationMs = c.getLong(iDur),
                        mime = c.getString(iMime) ?: ""
                    )
                )
            }
        }
        out
    }

    private fun openForRead(uri: Uri): InputStream? = try {
        resolver.openInputStream(MediaStore.setRequireOriginal(uri))
    } catch (e: Exception) {
        resolver.openInputStream(uri)
    }

    private fun parse(s: String?): Long? =
        if (s.isNullOrBlank()) null else runCatching { exifFmt().parse(s)?.time }.getOrNull()

    suspend fun readMeta(item: MediaItem): Meta = withContext(Dispatchers.IO) {
        if (item.isVideo) return@withContext Meta(item.dateTakenMs.takeIf { it > 0 }, "", "", "", null, null)
        runCatching {
            openForRead(item.uri)?.use { s ->
                val e = ExifInterface(s)
                val ll = e.latLong
                Meta(
                    parse(e.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: e.getAttribute(ExifInterface.TAG_DATETIME)),
                    e.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION).orEmpty(),
                    e.getAttribute(ExifInterface.TAG_MAKE).orEmpty(),
                    e.getAttribute(ExifInterface.TAG_MODEL).orEmpty(),
                    ll?.get(0), ll?.get(1)
                )
            }
        }.getOrNull() ?: Meta(null, "", "", "", null, null)
    }

    /** Needs write access granted first (MediaStore.createWriteRequest). Returns a user-facing message. */
    suspend fun writeMeta(item: MediaItem, m: Meta): String = withContext(Dispatchers.IO) {
        var msg = "Info saved"
        if (!item.isVideo) {
            try {
                resolver.openFileDescriptor(item.uri, "rw")?.use { pfd ->
                    val e = ExifInterface(pfd.fileDescriptor)
                    m.dateTaken?.let {
                        val s = exifFmt().format(Date(it))
                        e.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, s)
                        e.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, s)
                        e.setAttribute(ExifInterface.TAG_DATETIME, s)
                    }
                    e.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, m.description.ifBlank { null })
                    e.setAttribute(ExifInterface.TAG_MAKE, m.make.ifBlank { null })
                    e.setAttribute(ExifInterface.TAG_MODEL, m.model.ifBlank { null })
                    if (m.lat != null && m.lng != null) e.setLatLong(m.lat, m.lng)
                    e.saveAttributes()
                }
            } catch (t: Throwable) {
                msg = "Only the date was saved (this format can't hold full EXIF)"
            }
        }
        m.dateTaken?.let {
            runCatching {
                resolver.update(item.uri, ContentValues().apply { put(MediaColumns.DATE_TAKEN, it) }, null, null)
            }
        }
        msg
    }
}
