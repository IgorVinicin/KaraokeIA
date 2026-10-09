package com.araujo.karaoke_ia.online

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import java.util.concurrent.atomic.AtomicBoolean

internal class PlayerEvents(val active: AtomicBoolean, val ready: () -> Unit, val ended: () -> Unit, val error: (String) -> Unit,
    val playing: () -> Unit = {}, val state: (Int) -> Unit = {}) {
    private val handler = Handler(Looper.getMainLooper())
    @JavascriptInterface fun event(name: String, code: Int) {
        handler.post {
            if (active.get()) when (name) {
                "ready" -> ready()
                "ended" -> ended()
                "playing" -> playing()
                "state" -> state(code)
                "blocked" -> error("O YouTube bloqueou o início automático. Pressione Reproduzir.")
                "error" -> error(when (code) {
                    2 -> "O identificador deste vídeo é inválido."
                    5 -> "O player não conseguiu decodificar este vídeo neste aparelho."
                    100 -> "Este vídeo foi removido ou é privado."
                    101, 150 -> "O canal não permite reproduzir este vídeo dentro do app."
                    153 -> "O YouTube não conseguiu identificar o player. Tente novamente."
                    else -> "Não foi possível reproduzir este vídeo (YouTube $code)."
                })
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayer(videoId: String, modifier: Modifier, onReady: () -> Unit, onEnded: () -> Unit,
    onError: (String) -> Unit, onView: (WebView?) -> Unit, onPlaying: () -> Unit = {},
    onStateChanged: (Int) -> Unit = {}) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val ready by rememberUpdatedState(onReady)
    val ended by rememberUpdatedState(onEnded)
    val error by rememberUpdatedState(onError)
    val viewCallback by rememberUpdatedState(onView)
    val playing by rememberUpdatedState(onPlaying)
    val state by rememberUpdatedState(onStateChanged)
    val startedAt = remember(videoId) { android.os.SystemClock.elapsedRealtime() }
    val active = remember(videoId) { AtomicBoolean(true) }
    val web = remember(videoId) {
        object : WebView(context) {
            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
                post { if (active.get()) evaluateJavascript("if(window.fitPlayer)fitPlayer();", null) }
            }
        }.apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // Playback starts only from the visible native/YouTube controls (autoplay=0).
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.isForMainFrame && request.url.host !in listOf(context.packageName, "www.youtube.com", "youtube.com")
                override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                    if (request.isForMainFrame && active.get()) error("Falha de conexão. Confira sua internet e tente novamente.")
                }
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    if (active.get()) error("O player foi interrompido. Tente novamente.")
                    return true
                }
            }
            addJavascriptInterface(PlayerEvents(active, {
                android.util.Log.d("SoltaVozMedia", "youtube_ready_ms=${android.os.SystemClock.elapsedRealtime() - startedAt}")
                ready()
            }, { ended() }, { error(it) }, { playing() }, {
                android.util.Log.d("SoltaVozMedia", "youtube_state=$it elapsed_ms=${android.os.SystemClock.elapsedRealtime() - startedAt}")
                state(it)
            }), "KaraokePlayer")
            val origin = "https://${context.packageName}"
            loadDataWithBaseURL("$origin/", """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="referrer" content="strict-origin-when-cross-origin"><style>html,body,#player{margin:0;width:100%;height:100%;background:black}</style></head><body><div id="player"></div><script src="https://www.youtube.com/iframe_api"></script><script>var player;function fitPlayer(){var h=Math.max(200,innerHeight);document.documentElement.style.height=h+"px";document.body.style.height=h+"px";var frame=document.getElementById("player");if(frame)frame.style.height=h+"px";if(window.player&&player.setSize)player.setSize(innerWidth,h);}window.addEventListener("resize",fitPlayer);function onYouTubeIframeAPIReady(){player=new YT.Player('player',{videoId:'$videoId',width:'100%',height:'100%',playerVars:{playsinline:1,origin:'$origin',controls:1,autoplay:0},events:{onReady:function(){fitPlayer();KaraokePlayer.event('ready',0)},onAutoplayBlocked:function(){KaraokePlayer.event('blocked',0)},onStateChange:function(e){KaraokePlayer.event('state',e.data);if(e.data===0)KaraokePlayer.event('ended',0);if(e.data===1)KaraokePlayer.event('playing',1)},onError:function(e){KaraokePlayer.event('error',e.data)}}});}</script></body></html>""", "text/html", "UTF-8", null)
        }
    }
    DisposableEffect(web, owner) {
        viewCallback(web)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    web.evaluateJavascript("if(window.player&&player.pauseVideo)player.pauseVideo();", null)
                    web.onPause()
                }
                Lifecycle.Event.ON_RESUME -> web.onResume()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            active.set(false)
            owner.lifecycle.removeObserver(observer)
            viewCallback(null)
            web.setOnKeyListener(null)
            web.stopLoading()
            web.removeJavascriptInterface("KaraokePlayer")
            web.loadUrl("about:blank")
            web.destroy()
        }
    }
    AndroidView(factory = { web }, modifier = modifier)
}
