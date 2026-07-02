package com.usbbackup.app.utils

fun formatBytes(bytes: Long): String {
    if (bytes < 0L) return "---"
    if (bytes == 0L) return "0 B"

    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0

    return when {
        gb >= 1 -> "%.2f GB".format(gb)
        mb >= 1 -> "%.2f MB".format(mb)
        kb >= 1 -> "%.2f KB".format(kb)
        else -> "$bytes B"
    }
}

fun shortenFileName(name: String): String {
    if (name.length <= 22) return name
    return name.take(9) + "..." + name.takeLast(10)
}