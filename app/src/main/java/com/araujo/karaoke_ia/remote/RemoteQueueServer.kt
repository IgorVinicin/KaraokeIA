package com.araujo.karaoke_ia.remote

import android.content.Context
import com.araujo.karaoke_ia.R

import com.araujo.karaoke_ia.data.MusicRepository
import com.araujo.karaoke_ia.data.ArtworkRepository
import kotlinx.coroutines.runBlocking
import com.araujo.karaoke_ia.data.QueueRepository
import java.io.Closeable
import java.net.ServerSocket
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URLDecoder
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

class RemoteQueueServer(
    private val context: Context,
    private val musicRepository: MusicRepository,
    private val queueRepository: QueueRepository,
    private val artworkRepository: ArtworkRepository,
    private val port: Int = 8765
) : Closeable {
    private val executor = ThreadPoolExecutor(8, 8, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(64))
    private val artworkRequests = java.util.concurrent.Semaphore(3)
    private val lifecycleLock = Any()
    private var starting = false
    private var closed = false
    private val clients = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<java.net.Socket, Boolean>())
    @Volatile private var serverSocket: ServerSocket? = null

    fun start() {
        synchronized(lifecycleLock) {
            if (closed || starting || serverSocket != null) return
            starting = true
        }
        try {
        executor.execute {
            try {
                val bound = synchronized(lifecycleLock) {
                    if (closed) return@execute
                    ServerSocket(port).also { serverSocket = it; starting = false }
                }
                bound.use { server ->
                    while (!server.isClosed) {
                        val client = server.accept()
                        val accepted = synchronized(lifecycleLock) {
                            if (closed) false else { clients.add(client); true }
                        }
                        if (!accepted) { client.close(); break }
                        try {
                            executor.execute {
                                try {
                                    handle(client)
                                } catch (_: Exception) {
                                    client.close()
                                } finally {
                                    clients.remove(client)
                                }
                            }
                        } catch (_: java.util.concurrent.RejectedExecutionException) {
                            client.close()
                            clients.remove(client)
                        }
                    }
                }
            } catch (_: Exception) {
                synchronized(lifecycleLock) { serverSocket = null; starting = false }
            }
        }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            synchronized(lifecycleLock) { starting = false }
        }
    }

    fun localUrl(): String? {
        val address = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress &&
                it.hostAddress?.let { address -> address !in listOf("10.0.2.15", "10.0.2.16") } == true }
            ?.hostAddress
        return address?.let { "http://$it:$port" }
    }

    override fun close() {
        synchronized(lifecycleLock) {
            closed = true
            serverSocket?.close()
            serverSocket = null
        }
        clients.forEach { runCatching { it.close() } }
        clients.clear()
        executor.shutdownNow()
    }

    // Font files are packaged binary resources and can be opened as a resource stream.
    // Lint's raw-only annotation excludes font IDs although this API reads their bytes.
    @android.annotation.SuppressLint("ResourceType")
    private fun openFontResource(@androidx.annotation.FontRes resource: Int): ByteArray =
        context.resources.openRawResource(resource).use { it.readBytes() }

    private fun handle(socket: java.net.Socket) {
        socket.use { client ->
            client.soTimeout = 3000
            val reader = client.getInputStream().bufferedReader()
            val requestLine = reader.readLine() ?: return
            while (reader.readLine()?.isNotEmpty() == true) { }
            val path = requestLine.split(" ").getOrNull(1) ?: "/"
            val route = path.substringBefore('?')
            if (route == "/fonts/body" || route == "/fonts/heading") {
                val resource = if (route == "/fonts/body") R.font.manrope else R.font.unbounded
                val bytes = openFontResource(resource)
                val header = "HTTP/1.1 200 OK\r\nContent-Type: font/ttf\r\nContent-Length: ${bytes.size}\r\nCache-Control: public, max-age=86400\r\nConnection: close\r\n\r\n"
                client.getOutputStream().apply { write(header.toByteArray()); write(bytes); flush() }
                return
            }
            val query = parseQuery(path.substringAfter('?', ""))
            if (route.startsWith("/photo/")) {
                val name = route.removePrefix("/photo/")
                val token = name.removeSuffix(".jpg")
                val file = java.io.File(context.filesDir, "score_exports/$token.jpg")
                val output = client.getOutputStream()
                if (!token.matches(Regex("[a-f0-9-]{36}")) || !file.isFile) {
                    output.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                } else if (name.endsWith(".jpg")) {
                    val disposition = if (query["download"] == "1") "Content-Disposition: attachment; filename=karaoke-$token.jpg\r\n" else ""
                    output.write("HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: ${file.length()}\r\n${disposition}Cache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray())
                    file.inputStream().use { it.copyTo(output) }
                } else {
                    val html = """<!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Sua foto do karaokê</title><style>body{margin:0;padding:24px;background:#0f1017;color:white;font:16px sans-serif;text-align:center}img{display:block;width:100%;max-width:480px;margin:20px auto;border-radius:16px}a{display:inline-block;padding:16px 24px;background:#ed307e;color:white;border-radius:12px;text-decoration:none}p{color:#b9bac6}</style><h1>Sua foto do karaokê</h1><img src="/photo/$token.jpg" alt="Foto com moldura colorida"><a href="/photo/$token.jpg?download=1" download>Baixar minha foto</a><p>Se necessário, mantenha a imagem pressionada para salvá-la.</p></html>""".toByteArray(Charsets.UTF_8)
                    output.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${html.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray())
                    output.write(html)
                }
                output.flush()
                return
            }
            val code = query["code"]
            if (route == "/api/artwork") {
                val music = code?.let { musicRepository.findMusicByCode(it) }
                if (music == null || !artworkRequests.tryAcquire()) {
                    val status = if (music == null) "404 Not Found" else "503 Service Unavailable"
                    client.getOutputStream().write("HTTP/1.1 $status\r\nContent-Length: 0\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray())
                    return
                }
                try {
                    val file = runBlocking { artworkRepository.getThumbnailFile(music) }
                    val output = client.getOutputStream()
                    if (file == null) {
                        output.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray())
                    } else {
                        output.write("HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: ${file.length()}\r\nCache-Control: private, max-age=3600\r\nConnection: close\r\n\r\n".toByteArray())
                        file.inputStream().use { it.copyTo(output) }
                    }
                    output.flush()
                } finally {
                    artworkRequests.release()
                }
                return
            }
            val body: String
            val contentType: String

            when (route) {
                "/api/songs" -> {
                    body = songsJson()
                    contentType = "application/json; charset=utf-8"
                }
                "/api/queue" -> {
                    body = queueJson()
                    contentType = "application/json; charset=utf-8"
                }
                "/api/add" -> {
                    val music = code?.let { musicRepository.findMusicByCode(it) }
                    if (music != null) {
                        queueRepository.addToQueue(music.code)
                    }
                    body = queueJson()
                    contentType = "application/json; charset=utf-8"
                }
                "/api/remove" -> {
                    if (!code.isNullOrBlank()) queueRepository.removeFromQueue(code)
                    body = queueJson()
                    contentType = "application/json; charset=utf-8"
                }
                "/api/clear" -> {
                    queueRepository.clearQueue()
                    body = queueJson()
                    contentType = "application/json; charset=utf-8"
                }
                else -> {
                    body = renderPage()
                    contentType = "text/html; charset=utf-8"
                }
            }

            val response = "HTTP/1.1 200 OK\r\nContent-Type: $contentType\r\nContent-Length: ${body.toByteArray().size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n$body"
            client.getOutputStream().bufferedWriter().use { it.write(response); it.flush() }
        }
    }

    private fun parseQuery(raw: String): Map<String, String> = raw.split('&')
        .filter { it.contains('=') }
        .associate {
            val parts = it.split('=', limit = 2)
            URLDecoder.decode(parts[0], StandardCharsets.UTF_8.name()) to
                URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name())
        }

    private fun songsJson(): String {
        val songs = musicRepository.loadCachedCatalog().filter { it.available }
        return songs.joinToString(prefix = "[", postfix = "]") { music ->
            "{\"code\":\"${jsonEscape(music.code)}\",\"title\":\"${jsonEscape(music.title)}\",\"artist\":\"${jsonEscape(music.artist)}\"}"
        }
    }

    private fun queueJson(): String {
        val queue = queueRepository.getQueue()
        return queue.joinToString(prefix = "[", postfix = "]") { code ->
            val music = musicRepository.findMusicByCode(code)
            "{\"code\":\"${jsonEscape(code)}\",\"title\":\"${jsonEscape(music?.title ?: "Musica $code")}\",\"artist\":\"${jsonEscape(music?.artist ?: "")}\"}"
        }
    }

    private fun renderPage(): String = context.assets.open("remote-control.html")
        .bufferedReader(Charsets.UTF_8).use { it.readText() }
    // Android's JSON encoder also escapes tabs, carriage returns and control characters.
    private fun jsonEscape(value: String): String = JSONObject.quote(value).drop(1).dropLast(1)
}
