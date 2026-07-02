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

    fun createBackupFolder(): DocumentFile? {
        val uri = getUsbUri() ?: return null
        val root = DocumentFile.fromTreeUri(context, uri) ?: return null

        // High-speed optimization for 1TB+ drives:
        // Instead of findFile (which scans everything), we use the resolver query
        try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                uri, DocumentsContract.getTreeDocumentId(uri)
            )
            
            context.contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                "${DocumentsContract.Document.COLUMN_DISPLAY_NAME} = ?",
                arrayOf("USB_Backup"),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getString(0)
                    return DocumentFile.fromSingleUri(
                        context, 
                        DocumentsContract.buildDocumentUriUsingTree(uri, id)
                    )
                }
            }
        } catch (_: Exception) {}

        // If not found via query, create it (this is atomic and fast)
        return root.createDirectory("USB_Backup")
    }

    fun takePersistablePermission(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        context.contentResolver.takePersistableUriPermission(uri, flags)
        saveUsbUri(uri)
    }

    private var cachedInfo: UsbInfo? = null

    fun getUsbInfo(forceRefresh: Boolean = false): UsbInfo? {
        if (!forceRefresh && cachedInfo != null) return cachedInfo

        val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val volumes = sm.storageVolumes.filter { it.isRemovable && it.state == "mounted" }
        
        if (volumes.isEmpty()) {
            cachedInfo = null
            return null
        }

        val vol = volumes.first()
        val usbName = vol.getDescription(context)
        val uri = getUsbUri()
        
        var available: Long = -1L
        var capacity: Long = -1L

        // Fast Strategy: Direct hardware query via path (Atomic and near-instant)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                vol.directory?.let {
                    val stats = StatFs(it.absolutePath)
                    available = stats.availableBytes
                    capacity = stats.totalBytes
                }
            } catch (_: Exception) { }
        }

        // Fallback Strategy: Only if hardware query failed and we have a URI
        if (available <= 0L && uri != null) {
            try {
                val documentId = DocumentsContract.getTreeDocumentId(uri)
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
                context.contentResolver.openFileDescriptor(documentUri, "r")?.use { pfd ->
                    val stats = Os.fstatvfs(pfd.fileDescriptor)
                    available = stats.f_bavail * stats.f_frsize
                    capacity = stats.f_blocks * stats.f_frsize
                }
            } catch (_: Exception) { }
        }

        cachedInfo = UsbInfo(usbName, available, capacity, uri != null)
        return cachedInfo
    }

    fun clearCache() {
        cachedInfo = null
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