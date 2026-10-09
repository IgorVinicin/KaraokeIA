package com.araujo.karaoke_ia.auth

import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class VerifiedLease(val sessionId: String, val installationId: String, val validatedAt: Long, val until: Long)
object OfflineLease {
    fun verify(token: String, publicKey: String, installation: String, session: String): VerifiedLease {
        require(token.length <= 4096)
        val parts = token.split('.')
        require(parts.size == 2)
        val decoder = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.decode(publicKey, Base64.DEFAULT)))
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(key); verifier.update(parts[0].toByteArray(Charsets.UTF_8))
        require(verifier.verify(Base64.decode(parts[1], decoder)))
        val claims = JSONObject(String(Base64.decode(parts[0], decoder), Charsets.UTF_8))
        require(claims.getInt("version") == 1 && claims.getString("audience") == "soltavoz-android")
        require(claims.getString("installation_id") == installation && claims.getString("session_id") == session)
        val start = timestamp(claims.getString("validated_at"))
        val end = timestamp(claims.getString("offline_until"))
        require(end > start && end - start <= 86_400_000L)
        return VerifiedLease(session, installation, start, end)
    }
    private fun timestamp(value: String): Long {
        // PostgreSQL ISO timestamps may have up to six fractional digits.
        val normalized = value.replace(Regex("\\.(\\d{3})\\d+"), ".$1")
        val format = SimpleDateFormat(if (normalized.contains('.')) "yyyy-MM-dd'T'HH:mm:ss.SSSXXX" else "yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC"); format.isLenient = false
        return requireNotNull(format.parse(normalized)).time
    }
    fun permits(lease: VerifiedLease, anchorElapsed: Long, elapsed: Long, anchorWall: Long, wall: Long,
                savedBoot: Int, boot: Int, sameProcess: Boolean): Boolean {
        if (elapsed < anchorElapsed || (boot < 0 && !sameProcess) || (boot >= 0 && savedBoot != boot)) return false
        val delta = elapsed - anchorElapsed
        if (kotlin.math.abs((wall - anchorWall) - delta) > 300_000L) return false
        return delta >= 0 && lease.validatedAt + delta < lease.until
    }
}
