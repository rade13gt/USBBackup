package com.usbbackup.app.backup

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

object BackupState {
    val progress: MutableState<BackupProgress> = mutableStateOf(BackupProgress())

    fun update(newProgress: BackupProgress) {
        progress.value = newProgress
    }
}