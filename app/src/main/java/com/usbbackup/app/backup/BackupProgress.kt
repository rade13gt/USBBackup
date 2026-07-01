package com.usbbackup.app.backup

data class BackupProgress(
    val running: Boolean = false,
    val cancelling: Boolean = false,
    val total: Int = 0,
    val copied: Int = 0,
    val failed: Int = 0,
    val currentFile: String = "---",
    val message: String = "Listo",
    val logs: List<BackupLogLine> = emptyList()
)