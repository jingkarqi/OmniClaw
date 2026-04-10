package com.sora.omniclaw.core.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.sora.omniclaw.core.common.HostError
import com.sora.omniclaw.core.common.HostErrorCategory
import com.sora.omniclaw.core.common.HostResult
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidSecretStore(
    context: Context,
) : SecretStore {
    private val sharedPreferences = context.getSharedPreferences(PREFERENCE_FILE, Context.MODE_PRIVATE)

    override suspend fun saveApiKey(apiKey: String): HostResult<Unit> {
        if (apiKey.isBlank()) {
            return clearApiKey()
        }

        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())

            val encryptedBytes = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
            val iv = cipher.iv

            sharedPreferences.edit()
                .putString(EncryptedValueKey, Base64.encodeToString(encryptedBytes, Base64.NO_WRAP))
                .putString(InitializationVectorKey, Base64.encodeToString(iv, Base64.NO_WRAP))
                .apply()
        }.fold(
            onSuccess = { HostResult.Success(Unit) },
            onFailure = { throwable ->
                HostResult.Failure(
                    HostError(
                        category = HostErrorCategory.Storage,
                        message = throwable.message ?: "Failed to save provider API key.",
                        recoverable = true,
                    )
                )
            }
        )
    }

    override suspend fun readApiKey(): String? {
        val encryptedValue = sharedPreferences.getString(EncryptedValueKey, null) ?: return null
        val ivValue = sharedPreferences.getString(InitializationVectorKey, null) ?: return null

        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(ivValue, Base64.NO_WRAP))
            )

            val decryptedBytes = cipher.doFinal(Base64.decode(encryptedValue, Base64.NO_WRAP))
            decryptedBytes.toString(Charsets.UTF_8)
        }.getOrNull()
    }

    override suspend fun hasApiKey(): Boolean = !readApiKey().isNullOrBlank()

    override suspend fun clearApiKey(): HostResult<Unit> {
        return runCatching {
            sharedPreferences.edit()
                .remove(EncryptedValueKey)
                .remove(InitializationVectorKey)
                .apply()
        }.fold(
            onSuccess = { HostResult.Success(Unit) },
            onFailure = { throwable ->
                HostResult.Failure(
                    HostError(
                        category = HostErrorCategory.Storage,
                        message = throwable.message ?: "Failed to clear provider API key.",
                        recoverable = true,
                    )
                )
            }
        )
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
            load(null)
        }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) {
            return existingKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "omniclaw_provider_api_key"
        const val PREFERENCE_FILE = "omniclaw_secret_store"
        const val EncryptedValueKey = "provider_api_key_ciphertext"
        const val InitializationVectorKey = "provider_api_key_iv"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
