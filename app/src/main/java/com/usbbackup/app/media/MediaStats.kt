package com.usbbackup.app.media

data class MediaStats(
    val photos: Int = 0,
    val videos: Int = 0,
    val totalBytes: Long = 0L,
    val loading: Boolean = false,
    val permissionGranted: Boolean = false
)