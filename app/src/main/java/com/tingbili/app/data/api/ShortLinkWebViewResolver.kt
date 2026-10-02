package com.tingbili.app.data.api

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * b23.tv 短链解析的 WebView 兜底通道。
 *
 * 裸 OkHttp 请求 b23.tv 常被 B 站风控 412（不返回 302，拿不到落地页）；真机浏览器/WebView
 * 带正常 UA + JS 环境时会被放行。这里用一个隐藏 WebView 加载短链，监听 URL 跳转，
 * 一旦落地到 bilibili.com（视频/音频页）就把该 URL 回吐给协程。
 *
 * 必须主线程创建/操作 WebView；协程取消时 destroy。10s 超时兜底。
 */
object ShortLinkWebViewResolver {
    private const val TIMEOUT_MS = 10_000L

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun resolve(context: Context, shortUrl: String): String? =
        suspendCancellableCoroutine { cont ->
            val handler = Handler(Looper.getMainLooper())
            handler.post {
                var webView: WebView? = null
                var resumed = false

                fun done(url: String?) {
                    if (resumed) return
                    resumed = true
                    handler.removeCallbacksAndMessages(null)
                    webView?.stopLoading()
                    webView?.destroy()
                    webView = null
                    if (cont.isActive) cont.resume(url)
                }

                cont.invokeOnCancellation {
                    handler.post { done(null) }
                }

                try {
                    webView = WebView(context.applicationContext)
                    val settings = webView!!.settings
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.blockNetworkImage = true
                    settings.setSupportMultipleWindows(false)
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

                    webView!!.webViewClient = object : WebViewClient() {
                        private fun maybeGrab(url: String?): Boolean {
                            if (url == null) return false
                            // 落到 bilibili.com 的视频/音频页（或中间带 BV 的 URL）即认为成功
                            if (url.contains("bilibili.com") &&
                                (url.contains("/video/") || url.contains("/audio/") ||
                                    LinkParser.parse(url) != null)
                            ) {
                                done(url)
                                return true
                            }
                            return false
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val u = request?.url?.toString()
                            return if (maybeGrab(u)) true else false
                        }

                        @Suppress("DEPRECATION")
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            return if (maybeGrab(url)) true else false
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            // 兜底：页面加载完后若还没拿到，直接看 webView.url 是否已经跳成落地页
                            val cur = view?.url ?: url
                            if (!maybeGrab(cur)) {
                                // 再给一次机会：10s 超时统一兜底
                            }
                        }
                    }

                    webView!!.loadUrl(shortUrl)

                    handler.postDelayed({
                        // 超时：若 webView.url 已经是 bilibili 落地页就用它，否则 null
                        val cur = webView?.url
                        if (cur != null && cur.contains("bilibili.com")) done(cur) else done(null)
                    }, TIMEOUT_MS)
                } catch (t: Throwable) {
                    android.util.Log.w("ShortLinkWebView", "WebView 创建失败: ${t.message}")
                    done(null)
                }
            }
        }
}
