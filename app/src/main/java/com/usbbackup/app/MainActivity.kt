package com.usbbackup.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.usbbackup.app.backup.*
import com.usbbackup.app.media.MediaScanner
import com.usbbackup.app.media.MediaStats
import com.usbbackup.app.usb.UsbStorageManager
import com.usbbackup.app.utils.formatBytes
import com.usbbackup.app.utils.shortenFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val TerminalGreen = Color(0xFF39FF2F)
val TerminalBlack = Color(0xFF020402)

data class UsbState(
    val selected: Boolean = false,
    val name: String = "Esperando",
    val folder: String = "---"
)

class MainActivity : ComponentActivity() {

    private var mediaStatsState: MutableState<MediaStats>? = null
    private var usbState: MutableState<UsbState>? = null
    private var currentBackupManager: BackupManager? = null

    private val usbFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                contentResolver.takePersistableUriPermission(uri, flags)

                val manager = UsbStorageManager(this)
                manager.saveUsbUri(uri)
                manager.createBackupFolder()

                usbState?.value = UsbState(
                    selected = true,
                    name = "Seleccionada",
                    folder = "USB_Backup"
                )

                Toast.makeText(this, "USB seleccionada correctamente", Toast.LENGTH_LONG).show()
            }
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions.values.any { it }

            if (granted) {
                mediaStatsState?.value = mediaStatsState?.value?.copy(
                    loading = true,
                    permissionGranted = true
                ) ?: MediaStats(loading = true, permissionGranted = true)

                loadMediaStats()
            } else {
                mediaStatsState?.value = MediaStats(permissionGranted = false, loading = false)
                Toast.makeText(this, "Permisos denegados", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }

        setContent {
            val stats = remember { mutableStateOf(MediaStats()) }
            val usb = remember { mutableStateOf(loadUsbState()) }
            val progress by BackupState.progress

            mediaStatsState = stats
            usbState = usb

            LaunchedEffect(Unit) {
                if (hasMediaPermission()) {
                    stats.value = stats.value.copy(loading = true, permissionGranted = true)
                    loadMediaStats()
                }
            }

            USBBackupApp(
                stats = stats.value,
                usbState = usb.value,
                progress = progress,
                onScanClick = {
                    if (hasMediaPermission()) {
                        stats.value = stats.value.copy(loading = true, permissionGranted = true)
                        loadMediaStats()
                    } else {
                        requestMediaPermission()
                    }
                },
                onSelectUsbClick = {
                    usbFolderLauncher.launch(null)
                },
                onBackupButtonClick = {
                    if (progress.running) {
                        cancelBackup()
                    } else {
                        backupAllPhotos()
                    }
                }
            )
        }
    }

    private fun loadUsbState(): UsbState {
        val manager = UsbStorageManager(this)
        return if (manager.hasUsbSelected()) {
            UsbState(true, "Seleccionada", "USB_Backup")
        } else {
            UsbState()
        }
    }

    private fun hasMediaPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestMediaPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
            )
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
        }
    }

    private fun loadMediaStats() {
        val state = mediaStatsState ?: return

        MainScope().launch {
            val stats = withContext(Dispatchers.IO) {
                MediaScanner(contentResolver).scanMedia()
            }

            state.value = stats.copy(loading = false, permissionGranted = true)
        }
    }

    private fun cancelBackup() {
        val intent = Intent(this, BackupService::class.java).apply {
            action = "STOP_BACKUP"
        }
        startService(intent)
    }

    private fun backupAllPhotos() {
        if (!hasMediaPermission()) {
            requestMediaPermission()
            return
        }

        val usbManager = UsbStorageManager(this)
        if (!usbManager.hasUsbSelected()) {
            Toast.makeText(this, "Primero selecciona la memoria USB", Toast.LENGTH_LONG).show()
            return
        }

        if (BackupState.progress.value.running) {
            cancelBackup()
            return
        }

        val intent = Intent(this, BackupService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

@Composable
fun USBBackupApp(
    stats: MediaStats,
    usbState: UsbState,
    progress: BackupProgress,
    onScanClick: () -> Unit,
    onSelectUsbClick: () -> Unit,
    onBackupButtonClick: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = TerminalBlack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 18.dp, end = 18.dp, top = 44.dp, bottom = 16.dp)
        ) {
            Header(
                status = when {
                    progress.cancelling -> "CANCEL"
                    progress.running -> "BACKUP"
                    progress.message == "Respaldo finalizado" -> "COMPLETED"
                    usbState.selected -> "USB READY"
                    else -> "IDLE"
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            TerminalBox("> ESTADO") {
                StatusLine("Sistema", "OK")
                StatusLine("USB", usbState.name)
                StatusLine("Carpeta", usbState.folder)
                StatusLine("Fotos", if (stats.loading) "Buscando..." else stats.photos.toString())
                StatusLine("Videos", if (stats.loading) "Buscando..." else stats.videos.toString())
                StatusLine("Espacio", if (stats.loading) "Calculando..." else formatBytes(stats.totalBytes))
                StatusLine("Modo", if (progress.running) "Respaldo" else "Manual")
            }

            Spacer(modifier = Modifier.height(12.dp))

            TerminalBox("> TERMINAL") {
                ShellLog(progress.logs)
            }

            Spacer(modifier = Modifier.height(12.dp))

            TerminalBox("> RESPALDO") {
                StatusLine("Estado", progress.message)
                StatusLine("Total", progress.total.toString())
                StatusLine("Copiadas", progress.copied.toString())
                StatusLine("Fallidas", progress.failed.toString())
                StatusLine("Tamaño", formatBytes(progress.totalSizeBytes))
                StatusLine("Archivo", shortenFileName(progress.currentFile))
                ProgressBar(progress.copied, progress.total)
            }

            Spacer(modifier = Modifier.height(12.dp))

            TerminalBox("> ACCIONES") {
                TerminalButton("[ ESCANEAR ]", onClick = onScanClick, enabled = !progress.running)
                Spacer(modifier = Modifier.height(8.dp))
                TerminalButton(
                    text = if (usbState.selected) "[ CAMBIAR USB ]" else "[ SELECCIONAR USB ]",
                    onClick = onSelectUsbClick,
                    enabled = !progress.running
                )
                Spacer(modifier = Modifier.height(8.dp))
                TerminalButton(
                    text = if (progress.running) "[ CANCELAR ]" else "[ RESPALDAR TODO ]",
                    onClick = onBackupButtonClick,
                    enabled = true
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Footer()
        }
    }
}

@Composable
fun Header(status: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column {
            Text(
                text = "> USB Backup v0.0.9",
                color = TerminalGreen,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Android USB Backup Utility",
                color = TerminalGreen,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "-------------------------",
                color = TerminalGreen,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Text(
            text = status,
            color = TerminalGreen,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun ShellLog(logs: List<BackupLogLine>) {
    val scrollState = rememberScrollState()
    
    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(logs.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    val displayLogs = if (logs.isEmpty()) {
        listOf(BackupLogLine("$ Esperando comando..."))
    } else {
        logs
    }

    Column(
        modifier = Modifier
            .height(80.dp)
            .verticalScroll(scrollState)
    ) {
        displayLogs.forEach { line ->
            Text(
                text = line.text,
                color = when (line.type) {
                    LogType.ERROR -> Color(0xFFFF5555)
                    LogType.WARNING -> Color(0xFFFFC857)
                    LogType.OK -> TerminalGreen
                    else -> TerminalGreen
                },
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        if (displayLogs.lastOrNull()?.text != "READY_") {
            Text(
                text = "READY_",
                color = TerminalGreen,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun Footer() {
    Column {
        Text("Live Terminal", color = TerminalGreen, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        Text("Build 0009 · Open Source", color = TerminalGreen, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun TerminalBox(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.7.dp, TerminalGreen, RoundedCornerShape(3.dp))
            .padding(12.dp)
    ) {
        Text(
            text = title,
            color = TerminalGreen,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

@Composable
fun StatusLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = label.padEnd(18, '.'),
            color = TerminalGreen,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )

        Text(
            text = " $value",
            color = TerminalGreen,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun ProgressBar(copied: Int, total: Int) {
    val blocks = 18
    val filled = if (total <= 0) 0 else ((copied.toFloat() / total.toFloat()) * blocks).toInt()
    val safeFilled = filled.coerceIn(0, blocks)

    val bar = "█".repeat(safeFilled) + "░".repeat(blocks - safeFilled)
    val percent = if (total <= 0) 0 else ((copied.toFloat() / total.toFloat()) * 100).toInt()

    Spacer(modifier = Modifier.height(6.dp))

    Text(
        text = "$bar $percent%",
        color = TerminalGreen,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace
    )
}

@Composable
fun TerminalButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .border(
                BorderStroke(0.8.dp, TerminalGreen),
                RoundedCornerShape(3.dp)
            ),
        shape = RoundedCornerShape(3.dp)
    ) {
        Text(
            text = text,
            color = if (enabled) TerminalGreen else Color(0xFF1F661F),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
