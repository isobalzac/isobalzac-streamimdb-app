package com.streamimdb.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.ProgressBar
import android.widget.RelativeLayout

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tam ekran
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Yatay mod
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        val layout = RelativeLayout(this)
        layout.setBackgroundColor(0xFF000000.toInt())

        // Progress bar
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        progressBar.isIndeterminate = false
        progressBar.max = 100
        val pbParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT, 8
        )
        pbParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
        progressBar.layoutParams = pbParams
        progressBar.progressTintList = android.content.res.ColorStateList.valueOf(0xFFE50914.toInt())

        // WebView
        webView = WebView(this)
        val wvParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        )
        webView.layoutParams = wvParams

        layout.addView(webView)
        layout.addView(progressBar)
        setContentView(layout)

        setupWebView()
        webView.loadUrl("https://streamimdb.ru")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 11; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        // Cookie
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                // Sadece streamimdb ve ilgili domainleri aç
                return false
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String) {
                progressBar.visibility = View.GONE
                // TV için odak ve scroll iyileştirmesi
                injectTVCSS()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progressBar.progress = newProgress
            }

            // Tam ekran video desteği
            private var customView: View? = null
            private var originalOrientation = 0
            private var customViewCallback: CustomViewCallback? = null

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                originalOrientation = requestedOrientation
                customViewCallback = callback
                (window.decorView as? android.widget.FrameLayout)?.addView(
                    view,
                    android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
            }

            override fun onHideCustomView() {
                (window.decorView as? android.widget.FrameLayout)?.removeView(customView)
                customView = null
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                requestedOrientation = originalOrientation
                customViewCallback?.onCustomViewHidden()
            }
        }
    }

    private fun injectTVCSS() {
        val js = """
            (function() {
                var style = document.createElement('style');
                style.innerHTML = `
                    * { cursor: pointer !important; }
                    a:focus, button:focus, [tabindex]:focus {
                        outline: 3px solid #E50914 !important;
                        outline-offset: 2px !important;
                        box-shadow: 0 0 0 4px rgba(229,9,20,0.3) !important;
                    }
                    ::-webkit-scrollbar { width: 6px; }
                    ::-webkit-scrollbar-thumb { background: #E50914; border-radius: 3px; }
                `;
                document.head.appendChild(style);
                
                // İlk odaklanılabilir elemana odaklan
                var focusable = document.querySelector('a, button, input, [tabindex]');
                if (focusable) focusable.focus();
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    // TV kumanda tuşları
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (webView.canGoBack()) {
                    webView.goBack()
                    true
                } else {
                    super.onKeyDown(keyCode, event)
                }
            }
            KeyEvent.KEYCODE_DPAD_UP -> { webView.scrollBy(0, -150); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { webView.scrollBy(0, 150); true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { webView.scrollBy(-150, 0); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { webView.scrollBy(150, 0); true }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                webView.evaluateJavascript(
                    "var v=document.querySelector('video'); if(v){v.paused?v.play():v.pause();}",
                    null
                )
                true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                webView.evaluateJavascript(
                    "var v=document.querySelector('video'); if(v){v.currentTime+=10;}",
                    null
                )
                true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                webView.evaluateJavascript(
                    "var v=document.querySelector('video'); if(v){v.currentTime-=10;}",
                    null
                )
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
