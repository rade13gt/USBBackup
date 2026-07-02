package com.usbbackup.app.usb

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

class UsbStorageManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "usb_backup_prefs"
        private const val KEY_USB_URI = "usb_tree_uri"
    }

    fun saveUsbUri(uri: Uri) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USB_URI, uri.toString())
            .apply()
    }

    fun getUsbUri(): Uri? {
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USB_URI, null)

        return value?.let { Uri.parse(it) }
    }

    fun hasUsbSelected(): Boolean = getUsbUri() != null

    fun getUsbRoot(): DocumentFile? {
        val uri = getUsbUri() ?: return null
        return DocumentFile.fromTreeUri(context, uri)
    }

    fun createBackupFolder(): DocumentFile? {
        val root = getUsbRoot() ?: return null

        val existing = root.findFile("USB_Backup")
        if (existing != null && existing.isDirectory) return existing

        return root.createDirectory("USB_Backup")
    }

    fun takePersistablePermission(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        context.contentResolver.takePersistableUriPermission(uri, flags)
        saveUsbUri(uri)
    }

    fun getUsbInfo(): UsbInfo? {
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val volumes = sm.storageVolumes
        val uri = getUsbUri()

        var matchedVolume: android.os.storage.StorageVolume? = null
        var treeId: String? = null

        if (uri != null) {
            treeId = try {
                DocumentsContract.getTreeDocumentId(uri)
            } catch (_: Exception) {
                null
            }
            if (treeId != null) {
                val volumeId = treeId.substringBefore(":")
                matchedVolume = volumes.firstOrNull { 
                    val uuid = it.uuid
                    uuid != null && uuid.equals(volumeId, ignoreCase = true) 
                }
            }
        }

        if (matchedVolume == null) {
            matchedVolume = volumes.firstOrNull { it.isRemovable }
        }

        if (matchedVolume == null) return null

        val usbName = matchedVolume.getDescription(context)
        
        // Try to get space via SAF if authorized
        if (uri != null && treeId != null) {
            try {
                val rootId = treeId.substringBefore(":")
                val rootUri = DocumentsContract.buildRootUri(uri.authority!!, rootId)

                context.contentResolver.query(
                    rootUri,
                    arrayOf(
                        DocumentsContract.Root.COLUMN_AVAILABLE_BYTES,
                        DocumentsContract.Root.COLUMN_CAPACITY_BYTES
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val availableIndex = cursor.getColumnIndex(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES)
                        val capacityIndex = cursor.getColumnIndex(DocumentsContract.Root.COLUMN_CAPACITY_BYTES)

                        val available = if (availableIndex != -1 && !cursor.isNull(availableIndex)) cursor.getLong(availableIndex) else -1L
                        val capacity = if (capacityIndex != -1 && !cursor.isNull(capacityIndex)) cursor.getLong(capacityIndex) else -1L

                        if (available != -1L) {
                            return UsbInfo(usbName, available, capacity, true)
                        }
                    }
                }
            } catch (_: Exception) { }

            // Fallback for Android 11+ using the volume directory
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val dir = matchedVolume.directory
                if (dir != null) {
                    return UsbInfo(usbName, dir.usableSpace, dir.totalSpace, true)
                }
            }

            return UsbInfo(usbName, -1L, -1L, true)
        }

        return UsbInfo(usbName, -1L, -1L, false)
    }

    data class UsbInfo(
        val name: String,
        val availableBytes: Long,
        val totalBytes: Long,
        val isAuthorized: Boolean
    )

    fun getAvailableSpaceBytes(): Long {
        return getUsbInfo()?.availableBytes ?: -1L
    }
}