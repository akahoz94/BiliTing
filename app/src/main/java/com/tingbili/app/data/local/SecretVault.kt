package com.tingbili.app.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 敏感字符串（目前用于 WebDAV 密码）的本地加密封装。
 *  - 密钥存 AndroidKeyStore（系统级不可导出），AES-256/GCM/NoPadding
 *  - 密文格式：Base64(12 字节 IV + 密文+TAG)
 *  - 不依赖任何三方库，minSdk 26 可用（KeyStore 要求 API 23+）
 * 任何环节失败都返回 null，由调用方决定是否降级为明文存储，绝不抛异常导致崩溃。
 */
object SecretVault {

    private const val TAG = "SecretVault"
    private const val ALIAS = "biliting_secret_vault"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val IV_LEN = 12
    private const val TAG_LEN = 128

    private fun key(): SecretKey? = runCatching {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(
                    KeyGenParameterSpec.Builder(
                        ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
                generateKey()
            }
        }
        ks.load(null)
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }.onFailure { Log.e(TAG, "keystore key unavailable", it) }.getOrNull()

    fun encrypt(plain: String): String? {
        if (plain.isEmpty()) return null
        val k = key() ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, k)
            val iv = cipher.iv
            val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(iv + body, Base64.NO_WRAP)
        }.onFailure { Log.e(TAG, "encrypt failed", it) }.getOrNull()
    }

    fun decrypt(payload: String): String? {
        if (payload.isBlank()) return null
        val k = key() ?: return null
        return runCatching {
            val raw = Base64.decode(payload, Base64.NO_WRAP)
            if (raw.size <= IV_LEN) return@runCatching null
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(TAG_LEN, raw, 0, IV_LEN))
            String(cipher.doFinal(raw, IV_LEN, raw.size - IV_LEN), Charsets.UTF_8)
        }.onFailure { Log.e(TAG, "decrypt failed", it) }.getOrNull()
    }
}
