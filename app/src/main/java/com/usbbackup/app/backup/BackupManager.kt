package com.usbbackup.app.backup

import android.content.Context
import com.usbbackup.app.media.MediaItem
import com.usbbackup.app.usb.UsbStorageManager
import androidx.documentfile.provider.DocumentFile

class BackupManager(
    private val context: Context
) {
    @Volatile
    private var cancelled = false

    fun cancel() {
        cancelled = true
    }

    fun resetCancel() {
        cancelled = false
    }

    fun copyOneFileToUsb(item: MediaItem): Boolean {
        val usbManager = UsbStorageManager(context)
        val backupFolder = usbManager.createBackupFolder() ?: return false
        return copyFileToFolder(item, backupFolder)
    }

    fun copyPhotosToUsb(
        items: List<MediaItem>,
        onProgress: (BackupProgress) -> Unit
    ): BackupProgress {
        resetCancel()

        val usbManager = UsbStorageManager(context)
        val backupFolder = usbManager.createBackupFolder()

        val logs = mutableListOf<BackupLogLine>()

        fun pushLog(text: String, type: LogType = LogType.INFO) {
            logs.add(BackupLogLine(text, type))
            if (logs.size > 8) logs.removeAt(0)
        }

        pushLog("$ Detectando almacenamiento...")
        pushLog("✔ USB seleccionada", LogType.OK)

        if (backupFolder == null) {
            pushLog("✖ USB no disponible", LogType.ERROR)

            return BackupProgress(
                running = false,
                total = items.size,
                copied = 0,
                failed = items.size,
                message = "USB no disponible",
                logs = logs.toList()
            )
        }

        pushLog("$ Creando carpeta USB_Backup...")
        pushLog("✔ Carpeta lista", LogType.OK)
        pushLog("$ Preparando respaldo...")
        pushLog("✔ ${items.size} fotos encontradas", LogType.OK)

        var copied = 0
        var failed = 0

        onProgress(
            BackupProgress(
                running = true,
                total = items.size,
                copied = copied,
                failed = failed,
                currentFile = "---",
                message = "Iniciando respaldo",
                logs = logs.toList()
            )
        )

        for (item in items) {
            if (cancelled) {
                pushLog("^C Cancelación solicitada", LogType.WARNING)
                pushLog("✔ Proceso detenido", LogType.WARNING)

                return BackupProgress(
                    running = false,
                    cancelling = false,
                    total = items.size,
                    copied = copied,
                    failed = failed,
                    currentFile = "---",
                    message = "Cancelado por el usuario",
                    logs = logs.toList()
                )
            }

            pushLog("USBBackup> copy ${shortName(item.name)}")

            onProgress(
                BackupProgress(
                    running = true,
                    total = items.size,
                    copied = copied,
                    failed = failed,
                    currentFile = item.name,
                    message = "Copiando fotos",
                    logs = logs.toList()
                )
            )

            val ok = try {
                copyFileToFolder(item, backupFolder)
            } catch (_: Exception) {
                false
            }

            if (ok) copied++ else failed++

            onProgress(
                BackupProgress(
                    running = true,
                    total = items.size,
                    copied = copied,
                    failed = failed,
                    currentFile = item.name,
                    message = "Copiando fotos",
                    logs = logs.toList()
                )
            )
        }

        pushLog("✔ Respaldo finalizado", LogType.OK)

        return BackupProgress(
            running = false,
            total = items.size,
            copied = copied,
            failed = failed,
            currentFile = "---",
            message = "Respaldo finalizado",
            logs = logs.toList()
        )
    }

    private fun copyFileToFolder(
        item: MediaItem,
        folder: DocumentFile
    ): Boolean {
        val existing = folder.findFile(item.name)
        if (existing != null) return true

        val outputFile = folder.createFile(item.mimeType, item.name) ?: return false

        context.contentResolver.openInputStream(item.uri).use { input ->
            context.contentResolver.openOutputStream(outputFile.uri).use { output ->
                if (input == null || output == null) return false
                input.copyTo(output)
            }
        }

        return true
    }
    private fun shortName(name: String): String {
        if (name.length <= 24) return name

        val start = name.take(10)
        val end = name.takeLast(10)

        return "$start...$end"
    }
}