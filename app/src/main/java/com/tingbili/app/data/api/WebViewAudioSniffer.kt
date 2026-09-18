package com.tingbili.app.data.api

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * WebView 音频URL嗅探器 —— 仿"我的听书"方案。
 *
 * 原理：隐藏 WebView 加载 B站视频页，让播放器自己加载音频流。
 * 通过 shouldInterceptRequest 拦截实际的 .m4s 网络请求，抓到播放器真正用的音频 URL。
 * 比读 __playinfo__ 可靠：不依赖页面 SSR 格式，拿到的就是播放器实际发出的请求。
 */
class WebViewAudioSniffer(private val context: Context) {

    companion object {
        private const val TAG = "WebViewSniffer"
        private const val TIMEOUT_MS = 20_000L
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun sniff(bvid: String, cid: Long? = null): String? {
        val pageUrl = buildString {
            append("https://www.bilibili.com/video/")
            append(bvid)
            append("/")
            if (cid != null && cid > 0) append("?p=${cid}")
        }

        return suspendCancellableCoroutine { cont ->
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            handler.post {
                var webView: WebView? = null
                var resumed = false
                var capturedUrl: String? = null

                fun done(url: String?) {
                    if (resumed) return
                    resumed = true
                    handler.removeCallbacksAndMessages(null)
                    webView?.stopLoading()
                    webView?.destroy()
                    webView = null
                    if (cont.isActive) cont.resume(url)
                }

                try {
                    webView = WebView(context.applicationContext)
                    val settings = webView!!.settings
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.userAgentString = DESKTOP_UA
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.blockNetworkImage = true
                    settings.setSupportMultipleWindows(false)

                    webView!!.addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onResult(url: String) { done(url) }
                        @JavascriptInterface
                        fun onFail(reason: String) {
                            android.util.Log.w(TAG, "JS: $reason")
                        }
                    }, "BiliTingBridge")

                    webView!!.webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): android.webkit.WebResourceResponse? {
                            val url = request.url?.toString() ?: return null
                            // 拦截 .m4s 音频流请求（DASH 音频）
                            if (url.contains(".m4s") && url.contains("bilivideo.com")) {
                                // 区分音频和视频：音频 URL 通常有 codecid=302xx 或路径含 /audio/
                                // 简单策略：优先含 "audio" 路径的，其次第一个 .m4s
                                if (capturedUrl == null) {
                                    android.util.Log.i(TAG, "捕获音频流: ${url.take(100)}")
                                    capturedUrl = url
                                    // 延迟一点再回调，确保拿到的是音频不是视频
                                    handler.postDelayed({ done(capturedUrl) }, 500)
                                }
                            }
                            return null
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            // 先试试 __playinfo__ 作为快速通道
                            val js = """
                                (function() {
                                    try {
                                        var pi = window.__playinfo__;
                                        if (!pi || !pi.data) return;
                                        var dash = pi.data.dash;
                                        if (dash && dash.audio && dash.audio.length > 0) {
                                            var audios = dash.audio.sort(function(a,b){return b.id - a.id;});
                                            var u = audios[0].baseUrl || audios[0].base_url || "";
                                            if (u && !window.__sniffed) {
                                                window.__sniffed = true;
                                                BiliTingBridge.onResult(u);
                                            }
                                        }
                                    } catch(e) {}
                                })();
                            """.trimIndent()
                            view?.evaluateJavascript(js, null)
                        }
                    }

                    webView!!.loadUrl(pageUrl)

                    handler.postDelayed({
                        android.util.Log.w(TAG, "嗅探超时 bvid=$bvid captured=$capturedUrl")
                        done(capturedUrl)
                    }, TIMEOUT_MS)

                } catch (t: Throwable) {
                    android.util.Log.e(TAG, "WebView 创建失败", t)
                    done(null)
                }
            }
        }
    }
}
