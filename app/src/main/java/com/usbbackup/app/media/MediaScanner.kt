package com.usbbackup.app.media

import android.content.ContentResolver
import android.provider.MediaStore

class MediaScanner(
    private val contentResolver: ContentResolver
) {
    fun scanMedia(): MediaStats {
        var photos = 0
        var videos = 0
        var totalBytes = 0L

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.SIZE
        )

        val selection =
            "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"

        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )

        val uri = MediaStore.Files.getContentUri("external")

        contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val typeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)

            while (cursor.moveToNext()) {
                val type = cursor.getInt(typeColumn)
                val size = cursor.getLong(sizeColumn)

                if (type == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) photos++
                if (type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) videos++

                if (size > 0) totalBytes += size
            }
        }

        return MediaStats(
            photos = photos,
            videos = videos,
            totalBytes = totalBytes,
            permissionGranted = true
        )
    }
}