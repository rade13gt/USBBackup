package com.usbbackup.app.backup

import android.content.Context
import com.usbbackup.app.media.MediaItem
import com.usbbackup.app.usb.UsbStorageManager

class BackupManager(
    private val context: Context
) {
    fun copyOneFileToUsb(item: MediaItem): Boolean {
        val usbManager = UsbStorageManager(context)
        val backupFolder = usbManager.createBackupFolder() ?: return false

        val existing = backupFolder.findFile(item.name)
        if (existing != null) return true

        val outputFile = backupFolder.createFile(item.mimeType, item.name) ?: return false

        context.contentResolver.openInputStream(item.uri).use { input ->
            context.contentResolver.openOutputStream(outputFile.uri).use { output ->
                if (input == null || output == null) return false
                input.copyTo(output)
            }
        }

        return true
    }
}