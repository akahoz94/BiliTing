package com.tingbili.app.data.backup

import android.util.Base64
import android.util.Log
import com.tingbili.app.data.local.BookRecord
import com.tingbili.app.data.local.SettingsSnapshot
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
 *  - 备份：BookRecord 列表 + 设置快照 → AES-GCM 加密 → PUT 到远端
 *  - 恢复：GET 远端密文 → 解密 → 反序列化 → 还原
 */
class WebDavBackup(
    private val baseUrl: String,
    private val user: String,
    private val pass: String,
    private val backupPass: String
) {
    private val client = OkHttpClient()
    private val remoteFile = "library_backup.bin"
    private val remoteUrl: String get() = baseUrl.trimEnd('/') + "/" + remoteFile

    @Serializable
    data class BackupPayload(val version: Int = 1, val records: List<BookRecord>, val settings: SettingsSnapshot? = null)

    private fun authHeader(): String = "Basic " + android.util.Base64.encodeToString(
        "$user:$pass".toByteArray(), android.util.Base64.NO_WRAP
    )

    suspend fun backup(records: List<BookRecord>, settings: SettingsSnapshot? = null): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val json = Json.encodeToString(BackupPayload(records = records, settings = settings))
            val (cipherText, iv) = aesEncrypt(json.toByteArray(Charsets.UTF_8), backupPass.toKey())
            val body = (iv + cipherText)
            val req = Request.Builder().url(remoteUrl)
                    .header("Authorization", authHeader())
                    .put(body.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                    .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("PUT ${resp.code}: ${resp.message}")
            }
        }.onFailure { Log.e("WebDavBackup", "backup failed", it) }
    }

    suspend fun restore(): Result<BackupPayload> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(remoteUrl)
                    .header("Authorization", authHeader())
                    .get().build()
            client.newCall(req).execute().use { resp ->
                if (resp.code == 404) return@use BackupPayload(records = emptyList())
                if (!resp.isSuccessful) error("GET ${resp.code}: ${resp.message}")
                val body = resp.body?.bytes() ?: error("empty body")
                if (body.size < 28) error("body too short")
                val iv = body.copyOfRange(0, 12)
                val cipherText = body.copyOfRange(12, body.size)
                val plain = aesDecrypt(cipherText, iv, backupPass.toKey())
                Json.decodeFromString(BackupPayload.serializer(), String(plain, Charsets.UTF_8))
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

    suspend fun ping(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        runCatching {
            val auth = authHeader()
            Log.i("WebDavBackup", "ping: url=$baseUrl user=$user passLen=${pass.length}")
            // 用 OPTIONS 测试连接（坚果云 OPTIONS 200 = 认证通过）
            val testUrl = baseUrl.trimEnd('/') + "/"
            val req = Request.Builder().url(testUrl)
                    .method("OPTIONS", null)
                    .header("Authorization", auth)
                    .build()
            client.newCall(req).execute().use {
                Log.i("WebDavBackup", "ping OPTIONS resp: ${it.code} ${it.message}")
                if (it.isSuccessful || it.code == 200 || it.code == 204) {
                    true to "连接成功"
                } else {
                    false to "HTTP ${it.code}: ${it.message}"
                }
            }
        }.getOrElse {
            Log.e("WebDavBackup", "ping failed", it)
            false to (it.message ?: "未知错误")
        }
    }
}
