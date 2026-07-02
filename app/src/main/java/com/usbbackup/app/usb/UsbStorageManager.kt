package com.usbbackup.app.usb

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.os.StatFs
import android.system.Os
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
        val volumes = sm.storageVolumes.filter { it.isRemovable && it.state == "mounted" }
        
        if (volumes.isEmpty()) return null

        val uri = getUsbUri()
        val vol = volumes.first()
        val usbName = vol.getDescription(context)
        
        var available: Long = -1L
        var capacity: Long = -1L

        val treeId = uri?.let { 
            try { DocumentsContract.getTreeDocumentId(it) } catch (_: Exception) { null } 
        }

        // Strategy 1: Low-level File Descriptor (fstatvfs) - Most accurate for SAF
        if (uri != null) {
            try {
                // Ensure we use a direct document URI, not a tree URI for fstatvfs
                val documentId = DocumentsContract.getTreeDocumentId(uri)
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
                
                context.contentResolver.openFileDescriptor(documentUri, "r")?.use { pfd ->
                    val stats = Os.fstatvfs(pfd.fileDescriptor)
                    // Use f_bavail (available to non-privileged users)
                    available = stats.f_bavail * stats.f_frsize
                    capacity = stats.f_blocks * stats.f_frsize
                }
            } catch (_: Exception) { }
        }

        // Strategy 2: Direct Path via Volume ID (Fallback)
        if (available <= 0L && treeId != null) {
            val volumeId = treeId.substringBefore(":")
            val possiblePath = "/storage/$volumeId"
            val file = java.io.File(possiblePath)
            if (file.exists() && file.canRead()) {
                try {
                    val stats = StatFs(file.absolutePath)
                    available = stats.availableBytes
                    capacity = stats.totalBytes
                } catch (_: Exception) { }
            }
        }

        // Strategy 3: Use getExternalFilesDirs as fallback
        if (available <= 0L) {
            try {
                val externalDirs = context.getExternalFilesDirs(null)
                for (file in externalDirs) {
                    if (file != null && android.os.Environment.isExternalStorageRemovable(file)) {
                        val stats = StatFs(file.absolutePath)
                        available = stats.availableBytes
                        capacity = stats.totalBytes
                        break
                    }
                }
            } catch (_: Exception) { }
        }

        // Strategy 4: SAF Roots query (last resort)
        if (available <= 0L && uri != null) {
            try {
                val rootsUri = DocumentsContract.buildRootsUri(uri.authority!!)
                context.contentResolver.query(
                    rootsUri,
                    arrayOf(
                        DocumentsContract.Root.COLUMN_ROOT_ID,
                        DocumentsContract.Root.COLUMN_AVAILABLE_BYTES,
                        DocumentsContract.Root.COLUMN_CAPACITY_BYTES
                    ),
                    null, null, null
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(DocumentsContract.Root.COLUMN_ROOT_ID)
                    val availIdx = cursor.getColumnIndex(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES)
                    val capIdx = cursor.getColumnIndex(DocumentsContract.Root.COLUMN_CAPACITY_BYTES)
                    
                    while (cursor.moveToNext()) {
                        val rootId = if (idIdx != -1) cursor.getString(idIdx) else ""
                        if (treeId != null && treeId.startsWith(rootId)) {
                            available = if (availIdx != -1 && !cursor.isNull(availIdx)) cursor.getLong(availIdx) else -1L
                            capacity = if (capIdx != -1 && !cursor.isNull(capIdx)) cursor.getLong(capIdx) else -1L
                            break
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return UsbInfo(usbName, available, capacity, uri != null)
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