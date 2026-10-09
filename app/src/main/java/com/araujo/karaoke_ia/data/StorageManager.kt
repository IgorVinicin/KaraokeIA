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

    val catalogCacheFile: File
        get() = File(context.applicationContext.noBackupFilesDir, "local_music_index.sqlite")

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
        val state = runCatching { Environment.getExternalStorageState(file) }.getOrNull()
        if (state in setOf(Environment.MEDIA_UNMOUNTED, Environment.MEDIA_REMOVED, Environment.MEDIA_BAD_REMOVAL,
                Environment.MEDIA_UNMOUNTABLE, Environment.MEDIA_CHECKING, Environment.MEDIA_NOFS)) return false
        return file.isDirectory
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
        rootPath?.takeIf { isPathValid(it) }?.let { return it }
        val detectedPath = findExternalStoragePath()
        if (rootPath != detectedPath) rootPath = detectedPath
        return detectedPath
    }

    /** Index each connected volume, while retaining rows for volumes absent from this list. */
    fun catalogRoots(): List<String> {
        val internal = Environment.getExternalStorageDirectory().absolutePath
        val candidates = mutableListOf<File>()
        rootPath?.let { candidates.add(File(it)) }
        listOf("Download/karaoke", "Download/KARAOKE", "karaoke", "KARAOKE", "Download/MUSICAS", "MUSICAS", "musicas", "Music")
            .mapTo(candidates) { File(internal, it) }
        File("/storage").listFiles()?.filter { it.isDirectory && it.name !in setOf("emulated", "self") }
            ?.forEach { candidates.add(findKaraokeDirectory(it) ?: it) }
        val paths = candidates.filter { isPathValid(it.absolutePath) }.map { it.absolutePath }.distinct()
        return paths.filter { path -> paths.none { other -> other != path && path.startsWith("$other/") } }
    }

    private fun findKaraokeDirectory(parent: File): File? {
        return parent.listFiles()
            ?.firstOrNull { it.isDirectory && it.name.equals("karaoke", ignoreCase = true) }
    }

    companion object {
        const val STORAGE_PERMISSION_REQUEST = 1001
    }
}
