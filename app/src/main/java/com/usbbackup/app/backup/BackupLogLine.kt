package com.usbbackup.app.backup

data class BackupLogLine(
    val text: String,
    val type: LogType = LogType.INFO
)

enum class LogType {
    INFO,
    OK,
    ERROR,
    WARNING
}