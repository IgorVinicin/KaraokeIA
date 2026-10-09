package com.araujo.karaoke_ia.auth

import android.util.Base64
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature

class OfflineLeaseTest {
    private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val publicKey = Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP)
    private fun token(device: String = "box", end: String = "2026-10-10T10:00:00+00:00"): String {
        val claims = JSONObject().put("version",1).put("audience","soltavoz-android").put("user_id","user")
            .put("session_id","session").put("installation_id",device).put("generation",1)
            .put("validated_at","2026-10-09T10:00:00+00:00").put("offline_until",end)
        val flags = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        val payload = Base64.encodeToString(claims.toString().toByteArray(),flags)
        val signature = Signature.getInstance("SHA256withRSA").apply { initSign(pair.private); update(payload.toByteArray()) }.sign()
        return payload + "." + Base64.encodeToString(signature,flags)
    }
    private fun denied(action: () -> Unit) { var rejected = false; try { action() } catch (_: Exception) { rejected = true }; assertTrue(rejected) }
    @Test fun verifiesAndBindsDeviceAndSession() {
        assertEquals(86_400_000L, OfflineLease.verify(token(),publicKey,"box","session").let { it.until-it.validatedAt })
        denied { OfflineLease.verify(token(),publicKey,"other","session") }
        denied { OfflineLease.verify(token(),publicKey,"box","other") }
    }
    @Test fun rejectsTamperedSignatureAndLongLease() {
        val t = token(); val parts = t.split('.')
        denied { OfflineLease.verify(parts[0]+"A."+parts[1],publicKey,"box","session") }
        denied { OfflineLease.verify(token(end="2026-10-11T10:00:00+00:00"),publicKey,"box","session") }
    }
    @Test fun offlineDeadlineUsesMonotonicTimeAndBoot() {
        val lease = VerifiedLease("s","box",1000,10000)
        assertTrue(OfflineLease.permits(lease,100,1000,10000,10900,4,4,false))
        assertFalse(OfflineLease.permits(lease,100,9100,10000,19000,4,4,false))
        assertFalse(OfflineLease.permits(lease,100,1000,10000,10900,4,5,true))
        assertFalse(OfflineLease.permits(lease,100,99,10000,9999,4,4,true))
    }
    @Test fun clockChangesAndUnknownRebootsRequireInternet() {
        val lease = VerifiedLease("s","box",1000,86_401_000)
        assertFalse(OfflineLease.permits(lease,100,1000,10000,900000,4,4,true))
        assertFalse(OfflineLease.permits(lease,100,1000,10000,10900,-1,-1,false))
        assertTrue(OfflineLease.permits(lease,100,1000,10000,10900,-1,-1,true))
    }
}
