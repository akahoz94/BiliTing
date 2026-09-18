package com.tingbili.app.data.api

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class WebViewAudioSniffer(private val context: Context) {

    companion object {
        private const val TAG = "WebViewSniffer"
        private const val TIMEOUT_MS = 15_000L
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun sniff(bvid: String, cid: Long? = null): String? {
        val pageUrl = buildString {
            append("https://www.bilibili.com/video/")
            append(bvid)
            append("/")
            if (cid != null && cid > 0) append("?p=1")
        }

        return suspendCancellableCoroutine { cont ->
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
                    settings.blockNetworkImage = true
                    settings.setSupportMultipleWindows(false)

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
                                            var audios = dash.audio.sort(function(a,b){return b.id - a.id;});
                                            var url = audios[0].baseUrl || audios[0].base_url || "";
                                            if (url) {
                                                BiliTingBridge.onResult(url);
                                                return;
                                            }
                                        }
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
