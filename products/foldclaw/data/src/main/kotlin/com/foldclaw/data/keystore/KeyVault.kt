package com.foldclaw.data.keystore

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * API Key 保管库：Keystore AES-GCM 包装 + DataStore 密文。
 */
private val Context.keyStoreDataStore by preferencesDataStore(name = "foldclaw_keys")

@Singleton
class KeyVault @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private val dataStore = context.keyStoreDataStore

    private fun getOrCreateMasterKey(): SecretKey {
        keyStore.getKey(MASTER_KEY_ALIAS, null)?.let { return it as SecretKey }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    suspend fun storeApiKey(providerId: String, apiKey: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        val combined = android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP) +
            ":" + android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP)
        dataStore.edit { it[stringPreferencesKey(providerId)] = combined }
    }

    suspend fun getApiKey(providerId: String): String? {
        val combined = dataStore.data.first()[stringPreferencesKey(providerId)] ?: return null
        val parts = combined.split(":")
        if (parts.size != 2) return null
        return try {
            val iv = android.util.Base64.decode(parts[0], android.util.Base64.NO_WRAP)
            val encrypted = android.util.Base64.decode(parts[1], android.util.Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateMasterKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun hasApiKey(providerId: String): Boolean = !getApiKey(providerId).isNullOrBlank()

    suspend fun deleteApiKey(providerId: String) {
        dataStore.edit { it.remove(stringPreferencesKey(providerId)) }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "foldclaw_master_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        const val DEFAULT_PROVIDER_ID = "openai_compatible"
    }
}
