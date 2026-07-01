package com.usbbackup.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import com.usbbackup.app.media.MediaScanner
import com.usbbackup.app.media.MediaStats
import com.usbbackup.app.usb.UsbStorageManager
import com.usbbackup.app.utils.formatBytes
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
                mediaStatsState?.value = MediaStats(
                    permissionGranted = false,
                    loading = false
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val stats = remember { mutableStateOf(MediaStats()) }
            val usb = remember { mutableStateOf(loadUsbState()) }

            mediaStatsState = stats
            usbState = usb

            LaunchedEffect(Unit) {
                if (hasMediaPermission()) {
                    stats.value = stats.value.copy(
                        loading = true,
                        permissionGranted = true
                    )
                    loadMediaStats()
                }
            }

            USBBackupApp(
                stats = stats.value,
                usbState = usb.value,
                onScanClick = {
                    if (hasMediaPermission()) {
                        stats.value = stats.value.copy(
                            loading = true,
                            permissionGranted = true
                        )
                        loadMediaStats()
                    } else {
                        requestMediaPermission()
                    }
                },
                onSelectUsbClick = {
                    usbFolderLauncher.launch(null)
                }
            )
        }
    }

    private fun loadUsbState(): UsbState {
        val manager = UsbStorageManager(this)
        return if (manager.hasUsbSelected()) {
            UsbState(
                selected = true,
                name = "Seleccionada",
                folder = "USB_Backup"
            )
        } else {
            UsbState()
        }
    }

    private fun hasMediaPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_MEDIA_VIDEO
                    ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
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
            permissionLauncher.launch(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            )
        }
    }

    private fun loadMediaStats() {
        val state = mediaStatsState ?: return

        MainScope().launch {
            val stats = withContext(Dispatchers.IO) {
                MediaScanner(contentResolver).scanMedia()
            }

            state.value = stats.copy(
                loading = false,
                permissionGranted = true
            )
        }
    }
}

@Composable
fun USBBackupApp(
    stats: MediaStats,
    usbState: UsbState,
    onScanClick: () -> Unit,
    onSelectUsbClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = TerminalBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 18.dp, end = 18.dp, top = 44.dp, bottom = 16.dp)
        ) {
            Header(status = if (usbState.selected) "USB READY" else "IDLE")

            Spacer(modifier = Modifier.height(22.dp))

            TerminalBox("> ESTADO") {
                StatusLine("Sistema", "OK")
                StatusLine("USB", usbState.name)
                StatusLine("Carpeta", usbState.folder)
                StatusLine("Fotos", if (stats.loading) "Buscando..." else stats.photos.toString())
                StatusLine("Videos", if (stats.loading) "Buscando..." else stats.videos.toString())
                StatusLine("Espacio", if (stats.loading) "Calculando..." else formatBytes(stats.totalBytes))
                StatusLine("Modo", "Manual")
            }

            Spacer(modifier = Modifier.height(16.dp))

            TerminalBox("> ACCIONES") {
                TerminalButton("[ ESCANEAR ]", onClick = onScanClick)
                Spacer(modifier = Modifier.height(9.dp))
                TerminalButton(
                    text = if (usbState.selected) "[ CAMBIAR USB ]" else "[ SELECCIONAR USB ]",
                    onClick = onSelectUsbClick
                )
                Spacer(modifier = Modifier.height(9.dp))
                TerminalButton("[ RESPALDAR ]", onClick = {})
            }

            Spacer(modifier = Modifier.height(18.dp))

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
                text = "> USB Backup v0.0.6",
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
fun Footer() {
    Column {
        Text(
            text = "SAF Storage",
            color = TerminalGreen,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "Build 0006 · Open Source",
            color = TerminalGreen,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
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
            .padding(14.dp)
    ) {
        Text(
            text = title,
            color = TerminalGreen,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(12.dp))

        content()
    }
}

@Composable
fun StatusLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            text = label.padEnd(18, '.'),
            color = TerminalGreen,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )

        Text(
            text = " $value",
            color = TerminalGreen,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun TerminalButton(
    text: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .border(
                BorderStroke(0.8.dp, TerminalGreen),
                RoundedCornerShape(3.dp)
            ),
        shape = RoundedCornerShape(3.dp)
    ) {
        Text(
            text = text,
            color = TerminalGreen,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}