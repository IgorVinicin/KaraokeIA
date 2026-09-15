package com.araujo.karaoke_ia.remote

import com.araujo.karaoke_ia.data.MusicRepository
import com.araujo.karaoke_ia.data.QueueRepository
import java.io.Closeable
import java.net.ServerSocket
import java.net.NetworkInterface
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class RemoteQueueServer(
    private val musicRepository: MusicRepository,
    private val queueRepository: QueueRepository,
    private val port: Int = 8765
) : Closeable {
    private val executor = Executors.newCachedThreadPool()
    @Volatile private var serverSocket: ServerSocket? = null

    fun start() {
        if (serverSocket != null) return
        executor.execute {
            try {
                ServerSocket(port).also { serverSocket = it }.use { server ->
                    while (!server.isClosed) {
                        executor.execute { handle(server.accept()) }
                    }
                }
            } catch (_: Exception) {
                serverSocket = null
            }
        }
    }

    fun localUrl(): String? {
        val address = NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':') == false }
            ?.hostAddress
        return address?.let { "http://$it:$port" }
    }

    override fun close() {
        serverSocket?.close()
        serverSocket = null
        executor.shutdownNow()
    }

    private fun handle(socket: java.net.Socket) {
        socket.use { client ->
            client.soTimeout = 3000
            val reader = client.getInputStream().bufferedReader()
            val requestLine = reader.readLine() ?: return
            while (reader.readLine()?.isNotEmpty() == true) { }
            val path = requestLine.split(" ").getOrNull(1) ?: "/"
            val route = path.substringBefore('?')
            val query = parseQuery(path.substringAfter('?', ""))
            val code = query["code"]
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
                    if (!code.isNullOrBlank() && musicRepository.findMusicByCode(code) != null) {
                        queueRepository.addToQueue(code)
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
        val songs = musicRepository.scanAllMusics().take(500)
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

    private fun renderPage(): String {
        return """
            <!doctype html><html lang="pt-BR"><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Karaoke IA - Controle</title><style>
            *{box-sizing:border-box}body{margin:0;background:#101114;color:#f5f5f5;font:15px system-ui,sans-serif}main{max-width:720px;margin:auto;padding:18px}
            header{position:sticky;top:0;background:#101114ee;padding-bottom:12px;z-index:2}h1{font-size:24px;margin:0 0 4px;color:#f3c768}h2{font-size:17px;margin:20px 0 9px}
            .muted{color:#a8a8ad;font-size:13px}.tools{display:flex;gap:8px;margin-top:14px}.tools input{flex:1;min-width:0;background:#202126;border:1px solid #45454c;border-radius:10px;padding:12px;color:#fff;font-size:15px}
            button{border:0;border-radius:9px;padding:10px 13px;background:#f3c768;color:#17120a;font-weight:700;font-size:14px}button.danger{background:#39251d;color:#ffb7a2}button.small{padding:7px 10px;font-size:12px}
            ul{list-style:none;padding:0;margin:0}.song,.queue{display:flex;align-items:center;gap:10px;background:#1b1c21;border:1px solid #2b2c32;border-radius:11px;padding:11px;margin:8px 0}.info{flex:1;min-width:0}.title{font-weight:700;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.artist,.code{color:#a8a8ad;font-size:12px}.queue{border-color:#6d5525}.queue .position{color:#f3c768;font-weight:800;width:22px}.empty{color:#a8a8ad;padding:12px 0}.status{color:#8de0a8;font-size:13px;min-height:18px}
            </style></head><body><main><header><h1>Karaoke IA</h1><div class="muted">Controle remoto local</div><div class="tools"><input id="search" placeholder="Buscar música ou artista"><button onclick="loadAll()">Atualizar</button></div></header>
            <div id="status" class="status"></div><section><h2>Fila atual <button class="small danger" onclick="clearQueue()">Limpar fila</button></h2><ul id="queue"></ul></section>
            <section><h2>Músicas</h2><ul id="songs"></ul></section></main><script>
            let songs=[];const esc=s=>String(s).replace(/[&<>\"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;'}[c]));
            async function api(path){const r=await fetch(path,{cache:'no-store'});return r.json()}
            function renderSongs(){const q=document.getElementById('search').value.toLowerCase();const list=songs.filter(x=>(x.title+' '+x.artist+' '+x.code).toLowerCase().includes(q));document.getElementById('songs').innerHTML=list.map(x=>`<li class="song"><div class="info"><div class="title">${'$'}{esc(x.title)}</div><div class="artist">${'$'}{esc(x.artist)} · ${'$'}{esc(x.code)}</div></div><button class="small" onclick="add('${'$'}{esc(x.code)}')">Adicionar</button></li>`).join('')||'<li class="empty">Nenhuma música encontrada.</li>'}
            async function renderQueue(){const list=await api('/api/queue');document.getElementById('queue').innerHTML=list.map((x,i)=>`<li class="queue"><span class="position">${'$'}{i+1}</span><div class="info"><div class="title">${'$'}{esc(x.title)}</div><div class="artist">${'$'}{esc(x.artist)} · ${'$'}{esc(x.code)}</div></div><button class="small danger" onclick="remove('${'$'}{esc(x.code)}')">Remover</button></li>`).join('')||'<li class="empty">A fila está vazia.</li>'}
            async function add(code){await api('/api/add?code='+encodeURIComponent(code));document.getElementById('status').textContent='Música adicionada à fila.';await renderQueue()}
            async function remove(code){await api('/api/remove?code='+encodeURIComponent(code));await renderQueue()}
            async function clearQueue(){if(confirm('Limpar toda a fila?')){await api('/api/clear');await renderQueue()}}
            async function loadAll(){songs=await api('/api/songs');renderSongs();await renderQueue()}
            document.getElementById('search').addEventListener('input',renderSongs);loadAll();setInterval(renderQueue,3000)
            </script></body></html>
        """.trimIndent()
    }

    private fun jsonEscape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
