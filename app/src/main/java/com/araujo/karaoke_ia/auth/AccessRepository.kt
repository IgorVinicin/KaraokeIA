package com.araujo.karaoke_ia.auth

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.AtomicFile
import com.araujo.karaoke_ia.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class AccessState(val allowed: Boolean = false, val checking: Boolean = true, val offline: Boolean = false,
                       val message: String = "Validando acesso…", val username: String = "")
internal class AccessDenied(val code: String): Exception()
class AccessRepository(context: Context, private val publicKey: String = BuildConfig.OFFLINE_PUBLIC_KEY,
                       private val transport: ((String, JSONObject, String?) -> JSONObject)? = null) {
    companion object {
        @Volatile private var instance: AccessRepository? = null
        fun getInstance(context: Context): AccessRepository = instance ?: synchronized(this) {
            instance ?: AccessRepository(context.applicationContext).also { instance = it }
        }
    }
    private val context = context.applicationContext
    private val file = AtomicFile(File(this.context.noBackupFilesDir, "account_session"))
    private val installationFile = AtomicFile(File(this.context.noBackupFilesDir, "account_installation"))
    private val process = UUID.randomUUID().toString()
    private val lock = Mutex()
    private var saved: JSONObject? = null
    private var loaded = false
    private val mutableState = MutableStateFlow(AccessState())
    val state = mutableState.asStateFlow()
    val installation: String by lazy {
        runCatching { installationFile.openRead().bufferedReader().use { UUID.fromString(it.readText()).toString() } }
            .getOrElse { UUID.randomUUID().toString().also { write(installationFile, it) } }
    }
    private fun boot() = Settings.Global.getInt(context.contentResolver, "boot_count", -1)
    private fun write(target: AtomicFile, text: String) {
        val stream = target.startWrite()
        try { stream.write(text.toByteArray(Charsets.UTF_8)); target.finishWrite(stream) }
        catch (error: Exception) { target.failWrite(stream); throw error }
    }
    private fun load() {
        if (loaded) return
        saved = runCatching { file.openRead().bufferedReader().use { JSONObject(it.readText()) } }.getOrNull()
        loaded = true
    }
    private fun lease(data: JSONObject) = OfflineLease.verify(data.getString("lease"), publicKey,
        installation, data.getString("session"))
    private fun offlineAllowed(data: JSONObject) = runCatching {
        OfflineLease.permits(lease(data), data.getLong("elapsed"), SystemClock.elapsedRealtime(), data.getLong("wall"),
            System.currentTimeMillis(), data.getInt("boot"), boot(), data.optString("process") == process)
    }.getOrDefault(false)
    private fun accept(response: JSONObject, previous: JSONObject, username: String, anchor: Long) {
        val session = response.getJSONObject("session").getString("session_id")
        val token = response.getString("offline_lease")
        OfflineLease.verify(token, publicKey, installation, session)
        val auth = response.optJSONObject("auth") ?: previous.getJSONObject("auth")
        val data = JSONObject().put("auth", auth).put("session", session).put("lease", token).put("username", username)
            .put("elapsed", anchor).put("wall", System.currentTimeMillis() - (SystemClock.elapsedRealtime() - anchor)).put("boot", boot()).put("process", process)
        write(file, data.toString()); saved = data; loaded = true
        mutableState.value = AccessState(allowed = true, checking = false, username = username, message = "")
    }
    private fun clear(message: String) {
        file.delete(); saved = null; loaded = true
        mutableState.value = AccessState(checking = false, message = message)
    }
    suspend fun login(username: String, password: String) = withContext(Dispatchers.IO) { lock.withLock {
        mutableState.value = AccessState(message = "Entrando…")
        val anchor = SystemClock.elapsedRealtime()
        try {
            val response = request("/login", JSONObject().put("username", username).put("password", password)
                .put("installation_id", installation).put("device_label", android.os.Build.MODEL.take(120)))
            accept(response, JSONObject(), username.trim().lowercase(), anchor)
        } catch (e: AccessDenied) { clear(message(e.code)) }
        catch (_: Exception) { clear("Não foi possível entrar. Confira a internet e tente novamente.") }
    } }
    suspend fun validate() = withContext(Dispatchers.IO) { lock.withLock { validateLocked() } }
    private fun validateLocked() {
        load()
        val data = saved ?: run {
            if (mutableState.value.checking) mutableState.value = AccessState(checking = false, message = "Entre com a conta liberada pelo administrador.")
            return
        }
        val anchor = SystemClock.elapsedRealtime()
        try {
            val auth = data.getJSONObject("auth")
            val payload = JSONObject().put("session_id", data.getString("session")).put("installation_id", installation)
            val response = try { request("/validate", payload, auth.getString("access_token")) }
                catch (e: AccessDenied) {
                    if (e.code != "unauthorized") throw e
                    request("/refresh", payload.put("refresh_token", auth.getString("refresh_token")))
                }
            accept(response, data, data.getString("username"), anchor)
        } catch (e: AccessDenied) {
            if (e.code == "too_many_attempts" || e.code == "service_unavailable") fallback(data)
            else clear(message(e.code))
        } catch (_: IOException) { fallback(data) }
        catch (_: Exception) { clear("Valide novamente seu acesso com internet.") }
    }
    private fun fallback(data: JSONObject) {
        val valid = offlineAllowed(data)
        mutableState.value = AccessState(allowed = valid, checking = false, offline = valid, username = data.optString("username"),
            message = if (valid) "Modo offline · acesso temporário validado" else "Conecte à internet para validar o acesso. A tolerância terminou ou o aparelho reiniciou.")
    }
    suspend fun logout() = withContext(Dispatchers.IO) { lock.withLock {
        load(); saved?.let { data -> runCatching { request("/logout", JSONObject().put("session_id", data.getString("session"))
            .put("installation_id", installation), data.getJSONObject("auth").getString("access_token")) } }
        clear("Você saiu da conta.")
    } }
    suspend fun search(query: String): JSONObject = withContext(Dispatchers.IO) { lock.withLock {
        load()
        val data = saved ?: throw IllegalStateException("Entre novamente para pesquisar.")
        val payload = JSONObject().put("query",query).put("session_id",data.getString("session")).put("installation_id",installation)
        try {
            try { request("/search",payload,data.getJSONObject("auth").getString("access_token")) }
            catch (e: AccessDenied) {
                if (e.code != "unauthorized") throw e
                validateLocked()
                if (!mutableState.value.allowed || mutableState.value.offline) throw IOException("Revalidação necessária")
                val renewed = saved ?: throw IOException("Sessão encerrada")
                request("/search",payload,renewed.getJSONObject("auth").getString("access_token"))
            }
        } catch (e: AccessDenied) {
            if (e.code in listOf("unauthorized","session_replaced","account_blocked","account_expired","device_blocked")) clear(message(e.code))
            throw IllegalStateException(when(e.code) {
                "search_daily_limit","too_many_attempts" -> "Limite de buscas atingido. Tente novamente mais tarde."
                "search_not_configured","search_unavailable","service_unavailable" -> "Pesquisa indisponível. Tente novamente mais tarde."
                else -> message(e.code)
            })
        }
    } }
    private fun request(route: String, body: JSONObject, token: String? = null): JSONObject {
        transport?.let { return it(route, body, token) }
        val connection = URL(BuildConfig.ACCESS_API_URL + route).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"; connection.doOutput = true
            connection.connectTimeout = 5000; connection.readTimeout = 5000; connection.instanceFollowRedirects = false
            connection.setRequestProperty("Content-Type", "application/json")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
            val result = try { JSONObject(raw) } catch (_: Exception) {
                if (status >= 500) throw IOException("Serviço temporariamente indisponível")
                throw AccessDenied(if (status == 401) "unauthorized" else "service_unavailable")
            }
            if (status !in 200..299) throw AccessDenied(result.optString("error", "service_unavailable"))
            return result
        } finally { connection.disconnect() }
    }
    private fun message(code: String) = when (code) {
        "invalid_credentials" -> "Usuário ou senha incorretos."
        "session_replaced" -> "Sua conta entrou em outro aparelho. Entre novamente para usar este."
        "account_blocked" -> "Acesso bloqueado. Fale com o administrador."
        "account_expired" -> "Seu acesso venceu. Fale com o administrador para renovar."
        "device_blocked" -> "Este aparelho está bloqueado. Fale com o administrador."
        else -> "Entre novamente para validar o acesso."
    }
}
