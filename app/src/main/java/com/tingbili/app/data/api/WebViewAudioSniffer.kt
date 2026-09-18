package com.tingbili.app.data.api

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * WebView 音频URL嗅探器 —— 仿"我的听书"方案。
 *
 * 原理：隐藏 WebView 加载 B站视频页，页面 SSR 注入 window.__playinfo__，
 * 执行 JS 直接取出 DASH 音频流地址。WebView 自带浏览器指纹，B站不风控。
 *
 * 比直接调 playurl API 稳定得多：不需要 WBI 签名、不依赖 UA/cookie 精度。
 */
class WebViewAudioSniffer(private val context: Context) {

    companion object {
        private const val TAG = "WebViewSniffer"
        private const val TIMEOUT_MS = 15_000L
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    }

    /**
     * 嗅探视频的音频流 URL。
     * @param bvid 视频 BV号
     * @param cid  分P cid（可选，默认第一P）
     * @return 音频流绝对URL，失败返回 null
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun sniff(bvid: String, cid: Long? = null): String? {
        val pageUrl = buildString {
            append("https://www.bilibili.com/video/")
            append(bvid)
            append("/")
            if (cid != null && cid > 0) append("?p=1")
        }

        return suspendCancellableCoroutine { cont ->
            // WebView 必须在 UI 线程创建
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
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

                try {
                    webView = WebView(context.applicationContext)
                    val settings = webView!!.settings
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.userAgentString = DESKTOP_UA
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.blockNetworkImage = true // 不加载图片，省流量加速
                    settings.setSupportMultipleWindows(false)

                    // JS 接口：页面加载完成后由 onPageFinished 调用 evaluateJavascript 取地址
                    webView!!.addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onResult(url: String) {
                            done(url)
                        }
                        @JavascriptInterface
                        fun onFail(reason: String) {
                            android.util.Log.w(TAG, "JS 取地址失败: $reason")
                            done(null)
                        }
                    }, "BiliTingBridge")

                    webView!!.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            // 页面加载完后执行 JS 提取 __playinfo__
                            val js = """
                                (function() {
                                    try {
                                        var pi = window.__playinfo__;
                                        if (!pi || !pi.data) {
                                            BiliTingBridge.onFail("no __playinfo__");
                                            return;
                                        }
                                        var dash = pi.data.dash;
                                        if (dash && dash.audio && dash.audio.length > 0) {
                                            // 按音质降序取第一个（id 越大音质越高）
                                            var audios = dash.audio.sort(function(a,b){return b.id - a.id;});
                                            var url = audios[0].baseUrl || audios[0].base_url || "";
                                            if (url) {
                                                BiliTingBridge.onResult(url);
                                                return;
                                            }
                                        }
                                        // 兜底：durl
                                        var durl = pi.data.durl;
                                        if (durl && durl.length > 0) {
                                            var u = durl[0].url || "";
                                            if (u) { BiliTingBridge.onResult(u); return; }
                                        }
                                        BiliTingBridge.onFail("no audio in playinfo");
                                    } catch(e) {
                                        BiliTingBridge.onFail("exception: " + e.message);
                                    }
                                })();
                            """.trimIndent()
                            view?.evaluateJavascript(js, null)
                        }
                    }

                    webView!!.loadUrl(pageUrl)

                    // 超时兜底
                    handler.postDelayed({
                        android.util.Log.w(TAG, "嗅探超时 bvid=$bvid")
                        done(null)
                    }, TIMEOUT_MS)

                } catch (t: Throwable) {
                    android.util.Log.e(TAG, "WebView 创建失败", t)
                    done(null)
                }
            }
        }
    }
}
