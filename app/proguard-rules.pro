# YouTube invokes this method through the WebView JavaScript bridge.
-keepclassmembers class com.araujo.karaoke_ia.online.PlayerEvents {
    @android.webkit.JavascriptInterface <methods>;
}
