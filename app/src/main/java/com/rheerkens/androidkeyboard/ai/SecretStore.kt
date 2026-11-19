package com.rheerkens.androidkeyboard.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The secrets the AI actions use; each is stored on its own. */
enum class Secret(val key: String) {
    OPENAI_API_KEY("openai_api_key"),
    SERVER_TOKEN("ai_server_token"),
}

/**
 * API keys and tokens, encrypted with a key that never leaves Android's keystore, in their own
 * preferences file: outside Settings, so a backup export never contains them, and (like all app
 * data) excluded from Android's cloud backup. [saved] shows the last four characters of each, for
 * the settings page; the value itself is read only when a request is sent.
 */
class SecretStore(context: Context) {
    private val prefs = context.getSharedPreferences("secrets", Context.MODE_PRIVATE)
    private val _saved = MutableStateFlow(Secret.entries.associateWith { hint(it) })
    /** For each secret, a hint like "…a1B2" while one is saved, else null. */
    val saved: StateFlow<Map<Secret, String?>> = _saved.asStateFlow()

    private val _refused = MutableStateFlow(Secret.entries.filter { prefs.getBoolean(refusedKey(it), false) }.toSet())
    /**
     * Keys or tokens the AI service refused (401). The keyboard and Settings say "Refused" until that
     * secret is saved again (or removed); kept with the secrets, so a restart doesn't forget it.
     */
    val refused: StateFlow<Set<Secret>> = _refused.asStateFlow()

    /** Counts saves of each secret in this process, so a late answer can tell whether its key is still the saved one. */
    private val versions = IntArray(Secret.entries.size)

    @Synchronized
    fun version(secret: Secret): Int = versions[secret.ordinal]

    /** Marks [secret] refused, if it is still the value saved at [version]. */
    @Synchronized
    fun markRefused(secret: Secret, version: Int) {
        if (versions[secret.ordinal] != version || prefs.getString(secret.key, null) == null) return
        prefs.edit { putBoolean(refusedKey(secret), true) }
        _refused.value = _refused.value + secret
    }

    /** Whether [secret]'s saved value is the one that was refused. */
    fun isRefused(secret: Secret, saved: Map<Secret, String?> = this.saved.value, refused: Set<Secret> = this.refused.value): Boolean =
        secret in refused && saved[secret] != null

    private fun refusedKey(secret: Secret) = secret.key + "_refused"

    @Synchronized
    fun get(secret: Secret): String? {
        val stored = prefs.getString(secret.key, null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            if (bytes.size <= IV_BYTES) return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_BYTES))
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: java.security.ProviderException) {
            null
        } catch (_: java.io.IOException) {
            null
        }
    }

    /** Saves [value] (trimmed), or removes the secret when it is null or blank. Returns false if it couldn't be encrypted. */
    @Synchronized
    fun set(secret: Secret, value: String?): Boolean {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) {
            prefs.edit(commit = true) { remove(secret.key).remove(hintKey(secret)).remove(refusedKey(secret)) }
        } else {
            val sealed = seal(text) ?: run {
                // A key the keystore invalidated (or lost) can't come back: start over with a new one.
                // Secrets sealed with the old key can't be read either, so they go too.
                resetKey()
                seal(text)
            } ?: return false
            prefs.edit(commit = true) {
                putString(secret.key, Base64.encodeToString(sealed, Base64.NO_WRAP))
                putString(hintKey(secret), "…" + text.takeLast(4))
                remove(refusedKey(secret))
            }
        }
        versions[secret.ordinal]++
        _saved.value = Secret.entries.associateWith { hint(it) }
        _refused.value = _refused.value - secret
        return true
    }

    private fun seal(text: String): ByteArray? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8))
    } catch (_: GeneralSecurityException) {
        null
    } catch (_: java.security.ProviderException) {
        // Key generation failing inside the keystore, which some devices report this way.
        null
    } catch (_: java.io.IOException) {
        null
    }

    private fun resetKey() {
        try {
            KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(ALIAS)
        } catch (_: GeneralSecurityException) {
        } catch (_: java.io.IOException) {
        } catch (_: java.security.ProviderException) {
        }
        prefs.edit(commit = true) { Secret.entries.forEach { remove(it.key).remove(hintKey(it)) } }
    }

    private fun hint(secret: Secret): String? =
        if (prefs.contains(secret.key)) prefs.getString(hintKey(secret), "saved") else null

    private fun hintKey(secret: Secret) = secret.key + "_hint"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "lowkey-secrets"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
