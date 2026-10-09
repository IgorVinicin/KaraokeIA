package com.araujo.karaoke_ia.auth

import android.content.Context
import android.content.ContextWrapper
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.security.KeyPairGenerator
import java.security.Signature
import java.text.SimpleDateFormat
import java.util.*

class AccessRepositoryTest {
    private fun scenario(block: suspend (AccessRepository, (String) -> Unit) -> Unit) = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(base.cacheDir,"access-test-"+UUID.randomUUID()).apply { mkdirs() }
        val context = object: ContextWrapper(base) { override fun getNoBackupFilesDir() = folder }
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val public = Base64.encodeToString(keys.public.encoded,Base64.NO_WRAP)
        val flags = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        var mode = "online"
        val session = UUID.randomUUID().toString()
        val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX",Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val transport: (String,JSONObject,String?) -> JSONObject = { _,body,_ ->
            if(mode == "offline") throw IOException("network")
            if(mode == "blocked") throw AccessDenied("account_blocked")
            if(mode == "replaced") throw AccessDenied("session_replaced")
            val now = System.currentTimeMillis()
            val claims = JSONObject().put("version",1).put("audience","soltavoz-android").put("user_id","user")
                .put("session_id",session).put("installation_id",body.getString("installation_id"))
                .put("validated_at",date.format(Date(now))).put("offline_until",date.format(Date(now+86_400_000)))
            val payload = Base64.encodeToString(claims.toString().toByteArray(),flags)
            val signature = Signature.getInstance("SHA256withRSA").apply {initSign(keys.private); update(payload.toByteArray())}.sign()
            JSONObject().put("session",JSONObject().put("session_id",session)).put("auth",JSONObject().put("access_token","token").put("refresh_token","refresh"))
                .put("offline_lease",payload+"."+Base64.encodeToString(signature,flags))
        }
        try { block(AccessRepository(context,public,transport),{mode=it}) } finally { folder.deleteRecursively() }
    }
    @Test fun firstLoginNeedsInternetButValidatedSessionCanGoOffline() = scenario { repo,mode ->
        mode("offline"); repo.login("client","password"); assertFalse(repo.state.value.allowed)
        mode("online"); repo.login("client","password"); assertTrue(repo.state.value.allowed)
        mode("offline"); repo.validate(); assertTrue(repo.state.value.allowed); assertTrue(repo.state.value.offline)
    }
    @Test fun explicitBlockRemovesOfflineAuthorization() = scenario { repo,mode ->
        repo.login("client","password"); mode("blocked"); repo.validate(); assertFalse(repo.state.value.allowed)
        mode("offline"); repo.validate(); assertFalse(repo.state.value.allowed)
    }
    @Test fun replacementAndLogoutCannotReuseOldAuthorization() = scenario { repo,mode ->
        repo.login("client","password"); mode("replaced"); repo.validate(); assertFalse(repo.state.value.allowed)
        mode("online"); repo.login("client","password"); assertTrue(repo.state.value.allowed)
        mode("offline"); repo.logout(); assertFalse(repo.state.value.allowed); repo.validate(); assertFalse(repo.state.value.allowed)
    }
}
