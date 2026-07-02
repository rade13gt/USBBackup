package com.usbbackup.app.backup

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.usbbackup.app.MainActivity
import com.usbbackup.app.media.MediaScanner
import com.usbbackup.app.usb.UsbStorageManager
import com.usbbackup.app.utils.formatBytes
import com.usbbackup.app.utils.shortenFileName
import kotlinx.coroutines.*

class BackupService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var backupJob: Job? = null
    private var currentManager: BackupManager? = null
    private val CHANNEL_ID = "backup_service_channel"
    private val NOTIFICATION_ID = 1

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP_BACKUP") {
            stopBackup()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification("Preparando respaldo..."))
        startBackup()
        
        return START_STICKY
    }

    private fun startBackup() {
        backupJob?.cancel()
        backupJob = serviceScope.launch {
            val usbManager = UsbStorageManager(this@BackupService)
            
            BackupState.update(
                BackupProgress(
                    running = true,
                    message = "Iniciando",
                    logs = listOf(BackupLogLine("USBBackup> Service started"))
                )
            )

            val scanner = MediaScanner(contentResolver)
            val mediaItems = scanner.getAllMedia()
            
            if (mediaItems.isEmpty()) {
                BackupState.update(
                    BackupProgress(
                        running = false,
                        message = "No se encontraron archivos",
                        logs = listOf(BackupLogLine("USBBackup> No media found", LogType.ERROR))
                    )
                )
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }

            // Pre-check space
            val requiredBytes = mediaItems.sumOf { it.size.coerceAtLeast(0L) }
            val availableBytes = usbManager.getAvailableSpaceBytes()

            if (availableBytes >= 0 && availableBytes < requiredBytes) {
                BackupState.update(
                    BackupProgress(
                        running = false,
                        message = "Espacio insuficiente",
                        logs = listOf(
                            BackupLogLine("USBBackup> Required: ${formatBytes(requiredBytes)}", LogType.ERROR),
                            BackupLogLine("USBBackup> Available: ${formatBytes(availableBytes)}", LogType.ERROR),
                            BackupLogLine("USBBackup> Aborted", LogType.ERROR)
                        )
                    )
                )
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }

            val manager = BackupManager(this@BackupService)
            currentManager = manager
            val result = manager.copyMediaToUsb(mediaItems) { progress ->
                BackupState.update(progress)
                updateNotification(
                    "Copiando: ${progress.copied}/${progress.total} (${shortenFileName(progress.currentFile)})"
                )
            }
            
            currentManager = null
            BackupState.update(result)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopBackup() {
        currentManager?.cancel()
        backupJob?.cancel()
        BackupState.update(BackupState.progress.value.copy(
            running = false, 
            message = "Cancelado por el usuario",
            logs = (BackupState.progress.value.logs + BackupLogLine("^C Proceso abortado", LogType.ERROR)).takeLast(50)
        ))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "USB Backup Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("USB Backup en progreso")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(content))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        backupJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}