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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * WebDAV 客户端 + 本地加密（AES-GCM）备份/恢复工具。
 *  - 备份：BookRecord 列表 + 设置快照 → AES-GCM 加密 → PUT 到远端「BiliTing/<时间戳>.bin」
 *  - 恢复：GET 最新的那份密文 → 解密 → 反序列化 → 还原
 *  - 保留最近 KEEP_VERSIONS 份历史备份，避免误备份毁掉好备份
 */
class WebDavBackup(
    private val baseUrl: String,
    private val user: String,
    private val pass: String,
    private val backupPass: String
) {
    /**
     * 显式超时：OkHttp 默认 10s 读写，弱网下用户会干等且拿不到明确提示。
     * 这里放宽到 connect 15s / read & write 30s / 整体 60s，并在文案里区分超时。
     */
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    private val remoteDir = "BiliTing"
    private val legacyFile = "library_backup.bin"
    private val keepVersions = 5

    private val legacyUrl: String get() = baseUrl.trimEnd('/') + "/" + legacyFile
    private fun fileUrl(name: String): String = baseUrl.trimEnd('/') + "/" + remoteDir + "/" + name

    private fun stamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

    @Serializable
    data class BackupPayload(val version: Int = 1, val records: List<BookRecord>, val settings: SettingsSnapshot? = null)

    private fun authHeader(): String = "Basic " + Base64.encodeToString(
        "${user.trim()}:${pass.trim()}".toByteArray(Charsets.UTF_8), Base64.NO_WRAP
    )

    /**
     * 把 HTTP 状态码翻译成可行动的人话。
     * 以前只报裸状态码（"HTTP 401: Unauthorized"），用户无法判断是该改密码还是改地址。
     */
    private fun explain(method: String, code: Int, serverMsg: String = ""): String = when (code) {
        401 -> "$method 认证失败（$code）：WebDAV 用户名或应用密码不正确，注意别把空格复制进去（坚果云要用「第三方应用管理」生成的应用密码，不是登录密码）"
        403 -> "$method 被拒绝（$code）：账号无写权限或空间已满，请检查坚果云配额"
        404 -> "$method 路径不存在（$code）：WebDAV 地址应填 https://dav.jianguoyun.com/dav/ 并确认云端目录存在"
        405 -> "$method 方法被禁用（$code）：服务端不支持该操作，请更换服务商或地址"
        409 -> "$method 父目录不存在（$code）：云端目录缺失，请在网页端先建好 BiliTing 目录"
        423, 507 -> "$method 失败（$code）：云端存储已满，请清理坚果云空间后重试"
        else -> "$method 失败（HTTP $code${if (serverMsg.isNotBlank()) " $serverMsg" else ""}）"
    }

    /** 网络层异常 → 人话 */
    private fun explainIO(t: Throwable): String = when (t) {
        is SocketTimeoutException -> "网络超时：请检查网络后重试（连接 15s / 传输 30s 上限）"
        is UnknownHostException -> "无法解析服务器地址：请检查网络或 WebDAV 地址是否正确"
        else -> "网络异常：${t.message ?: t::class.java.simpleName}"
    }

    // ------------------------------------------------------------------ 备份

    suspend fun backup(
        records: List<BookRecord>,
        settings: SettingsSnapshot? = null,
        fileName: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            ensureRemoteDir()
            val json = Json.encodeToString(BackupPayload(records = records, settings = settings))
            val (cipherText, iv) = aesEncrypt(json.toByteArray(Charsets.UTF_8), backupPass.toKey())
            val body = (iv + cipherText)
            val name = fileName ?: "backup_${stamp()}.bin"
            val req = Request.Builder().url(fileUrl(name))
                    .header("Authorization", authHeader())
                    .put(body.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                    .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error(explain("PUT", resp.code, resp.message))
            }
            Log.i("WebDavBackup", "backup uploaded: $name (${body.size} bytes)")
            pruneOldBackups()
            name
        }.onFailure { Log.e("WebDavBackup", "backup failed", it) }
    }

    // ------------------------------------------------------------------ 恢复

    /** 列出云端备份文件（按名字倒序，最新在前） */
    suspend fun listBackups(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            ensureRemoteDir()
            val dirUrl = baseUrl.trimEnd('/') + "/" + remoteDir + "/"
            val body = "<d:propfind xmlns:d=\"DAV:\"><d:prop><d:displayname/></d:prop></d:propfind>"
                .toRequestBody("application/xml".toMediaTypeOrNull())
            val req = Request.Builder().url(dirUrl)
                    .method("PROPFIND", body)
                    .header("Authorization", authHeader())
                    .header("Depth", "1")
                    .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error(explain("PROPFIND", resp.code, resp.message))
                val xml = resp.body?.string().orEmpty()
                Regex("<[^>]*displayname[^>]*>([^<]+)</")
                    .findAll(xml)
                    .map { it.groupValues[1].trim() }
                    .filter { it.endsWith(".bin") }
                    .sortedDescending()
                    .toList()
            }
        }.onFailure { Log.e("WebDavBackup", "list failed", it) }
    }

    suspend fun restore(): Result<BackupPayload> = withContext(Dispatchers.IO) {
        runCatching {
            // 优先最新的时间戳备份；没有则退回旧版本遗留的 library_backup.bin
            val names = listBackups().getOrDefault(emptyList())
            val target = names.firstOrNull { it.startsWith("backup_") } ?: names.firstOrNull()
            val url = target?.let { fileUrl(it) } ?: legacyUrl
            val req = Request.Builder().url(url)
                    .header("Authorization", authHeader())
                    .get().build()
            client.newCall(req).execute().use { resp ->
                if (resp.code == 404) return@use BackupPayload(records = emptyList())
                if (!resp.isSuccessful) error(explain("GET", resp.code, resp.message))
                val body = resp.body?.bytes() ?: error("云端返回空内容")
                if (body.size < 28) error("云端备份内容不完整（${body.size} 字节）")
                val iv = body.copyOfRange(0, 12)
                val cipherText = body.copyOfRange(12, body.size)
                val plain = aesDecrypt(cipherText, iv, backupPass.toKey())
                Json.decodeFromString(BackupPayload.serializer(), String(plain, Charsets.UTF_8))
                    .also { Log.i("WebDavBackup", "restored from $url: ${it.records.size} records") }
            }
        }.onFailure { Log.e("WebDavBackup", "restore failed", it) }
    }

    /** 删除超出保留份数的旧备份（失败不影响主流程） */
    private suspend fun pruneOldBackups() {
        runCatching {
            val names = listBackups().getOrNull() ?: return
            names.drop(keepVersions).forEach { old ->
                runCatching {
                    val req = Request.Builder().url(fileUrl(old))
                        .header("Authorization", authHeader())
                        .delete().build()
                    client.newCall(req).execute().use { Log.i("WebDavBackup", "prune $old -> ${it.code}") }
                }
            }
        }
    }

    // ------------------------------------------------------------------ 测试连接

    /**
     * 先 MKCOL 子目录（坚果云不允许直接往 /dav/ 根目录 PUT 建文件，必须先进目录），
     * 再 PROPFIND 探测认证：坚果云对未认证请求返回 401，认证通过返回 207。
     * 不要用 OPTIONS —— 坚果云 OPTIONS 不挑战认证，会误报成功。
     */
    suspend fun ping(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        runCatching {
            val auth = authHeader()
            val debug = "user=[${user.trim()}] passLen=${pass.trim().length}"
            Log.i("WebDavBackup", "ping: url=$baseUrl $debug")

            // 1) 写权限预检：确保备份目录可用
            ensureRemoteDir()?.let { (code, msg) ->
                if (code == 401 || code == 403) {
                    Log.i("WebDavBackup", "ping MKCOL -> $code")
                    return@runCatching false to explain("MKCOL", code).also { Log.i("WebDavBackup", "ping failed at MKCOL: $code") }
                }
            }

            // 2) 认证探测
            val testUrl = baseUrl.trimEnd('/') + "/"
            val propfindBody = "<d:propfind xmlns:d=\"DAV:\"><d:prop><d:current-user-principal/></d:prop></d:propfind>"
                .toRequestBody("application/xml".toMediaTypeOrNull())
            val req = Request.Builder().url(testUrl)
                    .method("PROPFIND", propfindBody)
                    .header("Authorization", auth)
                    .header("Depth", "0")
                    .build()
            client.newCall(req).execute().use {
                Log.i("WebDavBackup", "ping PROPFIND resp: ${it.code} ${it.message}")
                when {
                    it.isSuccessful -> true to "连接成功 ✓（已确认读写权限）"
                    it.code == 401 -> false to explain("PROPFIND", 401)
                    it.code == 404 -> false to explain("PROPFIND", 404)
                    else -> false to explain("PROPFIND", it.code, it.message)
                }
            }
        }.getOrElse {
            Log.e("WebDavBackup", "ping failed", it)
            false to explainIO(it)
        }
    }

    /**
     * 确保远端备份目录存在。返回 HTTP 码，null 表示请求本身没成功发出。
     * 201=已创建、405=已存在，都算通过。
     */
    private suspend fun ensureRemoteDir(): Pair<Int, String>? {
        val dirUrl = baseUrl.trimEnd('/') + "/" + remoteDir
        return runCatching {
            val req = Request.Builder().url(dirUrl)
                    .header("Authorization", authHeader())
                    .method("MKCOL", null)
                    .build()
            client.newCall(req).execute().use { resp ->
                Log.i("WebDavBackup", "MKCOL $dirUrl -> ${resp.code}")
                resp.code to resp.message
            }
        }.getOrNull()
    }

    // ------------------------------------------------------------------ 加密

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
}
