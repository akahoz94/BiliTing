package com.tingbili.app.data.backup

import android.util.Base64
import android.util.Log
import com.tingbili.app.data.local.BookRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * WebDAV 客户端 + 本地加密（AES-GCM）备份/恢复工具。
 *  - 备份：将 LibraryRepository 中的 BookRecord 列表序列化为 JSON 后 AES-GCM 加密 → PUT 到远端
 *  - 恢复：GET 远端密文 → 解密 → 反序列化 → 还原
 *  - "密码"用作 AES 密钥派生（SHA-256），保证远端只看到密文，WebDAV Basic 认证单独用账户密码
 */
class WebDavBackup(
    private val baseUrl: String,        // 例：https://dav.jianguoyun.com/dav/BiliTing
    private val user: String,
    private val pass: String,
    private val backupPass: String       // 用于派生 AES 密钥；用户自设
) {
    private val client = OkHttpClient()
    private val remoteFile = "library_backup.bin"
    private val remoteUrl get() = "$baseUrl/$remoteFile"

    @Serializable
    data class BackupPayload(val version: Int = 1, val records: List<BookRecord>)

    suspend fun backup(records: List<BookRecord>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val json = Json.encodeToString(BackupPayload(records = records))
            val (cipherText, iv) = aesEncrypt(json.toByteArray(Charsets.UTF_8), backupPass.toKey())
            val body = (iv + cipherText)
            val req = Request.Builder().url(remoteUrl)
                    .header("Authorization", Credentials.basic(user, pass))
                    .put(body.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                    .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("PUT ${resp.code}: ${resp.message}")
            }
        }.onFailure { Log.e("WebDavBackup", "backup failed", it) }
    }

    suspend fun restore(): Result<List<BookRecord>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(remoteUrl)
                    .header("Authorization", Credentials.basic(user, pass))
                    .get().build()
            client.newCall(req).execute().use { resp ->
                if (resp.code == 404) return@use emptyList<BookRecord>()
                if (!resp.isSuccessful) error("GET ${resp.code}: ${resp.message}")
                val body = resp.body?.bytes() ?: error("empty body")
                if (body.size < 28) error("body too short")
                val iv = body.copyOfRange(0, 12)
                val cipherText = body.copyOfRange(12, body.size)
                val plain = aesDecrypt(cipherText, iv, backupPass.toKey())
                Json.decodeFromString(BackupPayload.serializer(), String(plain, Charsets.UTF_8)).records
            }
        }.onFailure { Log.e("WebDavBackup", "restore failed", it) }
    }

    private fun aesEncrypt(data: ByteArray, key: ByteArray): Pair<ByteArray, ByteArray> {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(data) to iv
    }

    private fun aesDecrypt(cipherText: ByteArray, iv: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(cipherText)
    }

    private fun String.toKey(): ByteArray = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))

    /** 用于调试的 URL 校验：返回 true 表示 endpoint 可达 */
    suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(baseUrl)
                    .header("Authorization", Credentials.basic(user, pass))
                    .method("OPTIONS", null).build()
            client.newCall(req).execute().use { it.isSuccessful || it.code == 207 }
        }.getOrDefault(false)
    }
}