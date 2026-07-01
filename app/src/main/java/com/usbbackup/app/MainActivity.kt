package com.usbbackup.app


import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


val TerminalGreen = Color(0xFF39FF2F)
val TerminalBlack = Color(0xFF020402)


data class MediaStats(
    val photos: Int = 0,
    val videos: Int = 0,
    val totalBytes: Long = 0L,
    val loading: Boolean = false,
    val permissionGranted: Boolean = false
)


class MainActivity : ComponentActivity() {


    private var mediaStatsState: MutableState<MediaStats>? = null


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
            mediaStatsState = stats


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
                }
            )
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


        kotlinx.coroutines.MainScope().launch {
            val stats = withContext(Dispatchers.IO) {
                scanMedia()
            }


            state.value = stats.copy(
                loading = false,
                permissionGranted = true
            )
        }
    }


    private fun scanMedia(): MediaStats {
        var photos = 0
        var videos = 0
        var totalBytes = 0L


        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.SIZE
        )


        val selection =
            "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"


        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )


        val uri = MediaStore.Files.getContentUri("external")


        contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val typeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)


            while (cursor.moveToNext()) {
                val type = cursor.getInt(typeColumn)
                val size = cursor.getLong(sizeColumn)


                if (type == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) photos++
                if (type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) videos++


                if (size > 0) totalBytes += size
            }
        }


        return MediaStats(
            photos = photos,
            videos = videos,
            totalBytes = totalBytes,
            permissionGranted = true
        )
    }
}


@Composable
fun USBBackupApp(
    stats: MediaStats,
    onScanClick: () -> Unit
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
            Header(status = if (stats.loading) "SCAN" else "IDLE")


            Spacer(modifier = Modifier.height(22.dp))


            TerminalBox("> ESTADO") {
                StatusLine("Sistema", "OK")
                StatusLine("USB", "Esperando")
                StatusLine("Fotos", if (stats.loading) "Buscando..." else stats.photos.toString())
                StatusLine("Videos", if (stats.loading) "Buscando..." else stats.videos.toString())
                StatusLine("Espacio", if (stats.loading) "Calculando..." else formatBytes(stats.totalBytes))
                StatusLine("Modo", "Manual")
            }


            Spacer(modifier = Modifier.height(16.dp))


            TerminalBox("> ACCIONES") {
                TerminalButton("[ ESCANEAR ]", onClick = onScanClick)
                Spacer(modifier = Modifier.height(9.dp))
                TerminalButton("[ RESPALDAR ]", onClick = {})
                Spacer(modifier = Modifier.height(9.dp))
                TerminalButton("[ ARCHIVOS ]", onClick = {})
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
                text = "> USB Backup v0.0.4",
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
            text = "Scanner",
            color = TerminalGreen,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "Build 0004 · Open Source",
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


fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "---"


    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0


    return when {
        gb >= 1 -> "%.2f GB".format(gb)
        mb >= 1 -> "%.2f MB".format(mb)
        kb >= 1 -> "%.2f KB".format(kb)
        else -> "$bytes B"
    }
}