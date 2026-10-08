package com.araujo.karaoke_ia.data

import android.content.Context
import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.content.Intent
import android.net.Uri
import java.io.File
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class StorageManager(private val context: Context) {

    var rootPath: String?
        get() = context.getSharedPreferences("storage_prefs", Context.MODE_PRIVATE)
            .getString("root_path", null)
        set(value) {
            context.getSharedPreferences("storage_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("root_path", value)
                .apply()
        }

    fun hasAllFilesPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestAllFilesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val settingsIntents = listOf(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                },
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            )
            for (intent in settingsIntents) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (runCatching { context.startActivity(intent) }.isSuccess) return
            }
        } else if (context is Activity) {
            ActivityCompat.requestPermissions(
                context,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_REQUEST
            )
        }
    }

    fun isPathValid(path: String): Boolean {
        val file = File(path)
        return file.exists() && file.isDirectory
    }

    fun findExternalStoragePath(): String {
        val extStorage = Environment.getExternalStorageDirectory().absolutePath
        val sharedStorageCandidates = listOf(
            "$extStorage/Download/karaoke",
            "$extStorage/karaoke",
            "$extStorage/KARAOKE"
        )
        sharedStorageCandidates.firstOrNull { isPathValid(it) }?.let { return it }

        val storageDir = File("/storage")
        storageDir.listFiles()
            ?.filter { it.isDirectory && it.name != "emulated" && it.name != "self" }
            ?.firstNotNullOfOrNull { findKaraokeDirectory(it) }
            ?.let { return it.absolutePath }

        val possiblePaths = listOf(
            "$extStorage/Download/karaoke",
            "$extStorage/Download/KARAOKE",
            "$extStorage/karaoke",
            "$extStorage/KARAOKE",
            "$extStorage/Download/MUSICAS",
            "$extStorage/MUSICAS",
            "$extStorage/musicas",
            "$extStorage/Music"
        )

        possiblePaths.firstOrNull { isPathValid(it) }?.let { return it }

        return storageDir.listFiles()
            ?.filter { it.isDirectory && it.name != "emulated" && it.name != "self" }
            ?.firstOrNull { it.isDirectory }
            ?.absolutePath
            ?: "$extStorage/karaoke"
    }

    fun refreshRootPath(): String {
        val detectedPath = findExternalStoragePath()
        if (rootPath != detectedPath) rootPath = detectedPath
        return detectedPath
    }

    private fun findKaraokeDirectory(parent: File): File? {
        return parent.listFiles()
            ?.firstOrNull { it.isDirectory && it.name.equals("karaoke", ignoreCase = true) }
    }

    companion object {
        const val STORAGE_PERMISSION_REQUEST = 1001
    }
}
