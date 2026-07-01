package com.usbbackup.app.media

import android.net.Uri

data class MediaItem(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val size: Long
)