package com.usbbackup.app.media

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore

class MediaScanner(
    private val contentResolver: ContentResolver
) {
    fun scanMedia(): MediaStats {
        var photos = 0
        var videos = 0
        var totalBytes = 0L

        val projection = arrayOf(
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

        contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
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

    fun getFirstPhoto(): MediaItem? {
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE
        )

        contentResolver.query(
            uri,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                    ?: "photo_test.jpg"
                val mime = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE))
                    ?: "image/jpeg"
                val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE))

                return MediaItem(
                    uri = ContentUris.withAppendedId(uri, id),
                    name = name,
                    mimeType = mime,
                    size = size
                )
            }
        }

        return null
    }
}