package com.usbbackup.app.usb

import android.content.Context
import android.content.Intent
import android.net.Uri
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

    fun getAvailableSpaceBytes(): Long {
        val uri = getUsbUri() ?: return -1L

        return try {
            val treeId = DocumentsContract.getTreeDocumentId(uri)
            val rootId = treeId.substringBefore(":")
            val rootUri = DocumentsContract.buildRootUri(uri.authority!!, rootId)

            context.contentResolver.query(
                rootUri,
                arrayOf(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES)
                    if (index != -1 && !cursor.isNull(index)) {
                        return cursor.getLong(index)
                    }
                }
            }
            -1L
        } catch (_: Exception) {
            -1L
        }
    }
}