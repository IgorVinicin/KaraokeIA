package com.araujo.karaoke_ia.data

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.content.Intent
import android.net.Uri
import java.io.File

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
            // Para versões anteriores, as permissões de READ_EXTERNAL_STORAGE são suficientes.
            // Mas seguiremos o fluxo de requisição na UI se necessário.
            true
        }
    }

    fun requestAllFilesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun isPathValid(path: String): Boolean {
        val file = File(path)
        return file.exists() && file.isDirectory
    }

    fun findExternalStoragePath(): String {
        val sharedStorageCandidates = listOf(
            "/sdcard/Download/karaoke",
            "/storage/emulated/0/Download/karaoke",
            "/storage/emulated/0/karaoke",
            "/storage/emulated/0/KARAOKE"
        )
        sharedStorageCandidates.firstOrNull { isPathValid(it) }?.let { return it }

        val storageDir = File("/storage")
        storageDir.listFiles()
            ?.filter { it.isDirectory && it.name != "emulated" && it.name != "self" }
            ?.firstNotNullOfOrNull { findKaraokeDirectory(it) }
            ?.let { return it.absolutePath }

        val extStorage = Environment.getExternalStorageDirectory().absolutePath
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
}
