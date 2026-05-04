package com.streamimdb.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var navBar: LinearLayout
    private var selectedIndex = 0
    private var isFullscreen = false
    private val handler = Handler(Looper.getMainLooper())

    data class NavItem(val label: String, val url: String, val icon: String)

    private val navItems = listOf(
        NavItem("Home",   "https://streamimdb.ru",         "⌂"),
        NavItem("Movies", "https://streamimdb.ru/movies",  "🎬"),
        NavItem("Series", "https://streamimdb.ru/tv",      "📺"),
        NavItem("Search", "https://streamimdb.ru/search",  "🔍"),
        NavItem("More",   "https://streamimdb.ru/trending","☰")
    )

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        val root = LinearLayout(this)
        root.orientation = LinearLayout.HORIZONTAL
        root.setBackgroundColor(Color.BLACK)

        // SOL NAV BAR
        navBar = LinearLayout(this)
        navBar.orientation = LinearLayout.VERTICAL
        navBar.setBackgroundColor(Color.parseColor("#0D0D0D"))
        navBar.gravity = Gravity.CENTER_HORIZONTAL
        navBar.setPadding(0, 40, 0, 40)
        navBar.layoutParams = LinearLayout.LayoutParams(150, LinearLayout.LayoutParams.MATCH_PARENT)

        navItems.forEachIndexed { index, item ->
            val btn = LinearLayout(this)
            btn.orientation = LinearLayout.VERTICAL
            btn.gravity = Gravity.CENTER
            btn.setPadding(8, 18, 8, 18)
            btn.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            btn.isClickable = true
            btn.isFocusable = true

            val icon = TextView(this)
            icon.text = item.icon
            icon.textSize = 20f
            icon.gravity = Gravity.CENTER
            icon.setTextColor(Color.parseColor("#888888"))

            val label = TextView(this)
            label.text = item.label
            label.textSize = 10f
            label.gravity = Gravity.CENTER
            label.setTextColor(Color.parseColor("#888888"))

            btn.addView(icon)
            btn.addView(label)

            btn.setOnClickListener {
                selectNav(index)
                webView.loadUrl(item.url)
            }

            navBar.addView(btn)
        }

        // WEBVIEW
        webView = WebView(this)
        webView.layoutParams = LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
        )

        root.addView(navBar)
        root.addView(webView)
        setContentView(root)

        setupWebView()
        selectNav(0)
        webView.loadUrl(navItems[0].url)
    }

    private fun selectNav(index: Int) {
        selectedIndex = index
        for (i in 0 until navBar.childCount) {
            val btn = navBar.getChildAt(i) as? LinearLayout ?: continue
            val icon  = btn.getChildAt(0) as? TextView ?: continue
            val label = btn.getChildAt(1) as? TextView ?: continue
            if (i == index) {
                btn.setBackgroundColor(Color.parseColor("#E50914"))
                icon.setTextColor(Color.WHITE)
                label.setTextColor(Color.WHITE)
            } else {
                btn.setBackgroundColor(Color.TRANSPARENT)
                icon.setTextColor(Color.parseColor("#888888"))
                label.setTextColor(Color.parseColor("#888888"))
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.mediaPlaybackRequiresUserGesture = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        s.allowFileAccess = true
        s.allowContentAccess = true
        s.setSupportZoom(false)
        // Masaüstü user-agent — oynatma çubuğu ve dil seçimi düzgün görünsün
        s.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        // JS arayüzü — player kontrolü için
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onPlayerReady() {
                handler.post { injectPlayerFix() }
            }
        }, "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String) = false

            override fun onPageFinished(view: WebView, url: String) {
                // Sayfa yüklenince player düzeltmelerini enjekte et
                injectPlayerFix()
                injectNavHide()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            private var customView: View? = null
            private var customViewCallback: CustomViewCallback? = null

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                isFullscreen = true
                customView = view
                customViewCallback = callback
                // Nav barı gizle
                navBar.visibility = View.GONE
                val decor = window.decorView as android.widget.FrameLayout
                decor.addView(view, android.widget.FrameLayout.LayoutParams(-1, -1))
                // Tam ekran — sistem UI gizle
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
            }

            override fun onHideCustomView() {
                isFullscreen = false
                val decor = window.decorView as android.widget.FrameLayout
                decor.removeView(customView)
                customView = null
                navBar.visibility = View.VISIBLE
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                customViewCallback?.onCustomViewHidden()
            }

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (newProgress == 100) injectPlayerFix()
            }
        }
    }

    private fun injectPlayerFix() {
        val js = """
        (function() {
            // Sitenin kendi header/footer navigasyonunu gizle
            var selectors = ['header','nav','.navbar','.header','.site-header',
                             'footer','.footer','.site-footer'];
            selectors.forEach(function(sel) {
                var el = document.querySelector(sel);
                if(el) el.style.display = 'none';
            });

            // Video player tam ekran düzeltmesi
            var videos = document.querySelectorAll('video');
            videos.forEach(function(v) {
                v.style.width  = '100%';
                v.style.height = '100%';
                v.setAttribute('controls','true');
                v.setAttribute('playsinline','false');
                // Dil/altyazı track'lerini etkinleştir
                var tracks = v.querySelectorAll('track');
                tracks.forEach(function(t){ t.mode = 'showing'; });
            });

            // iframe player varsa ona da ulaşmaya çalış
            var iframes = document.querySelectorAll('iframe');
            iframes.forEach(function(f) {
                f.setAttribute('allowfullscreen','true');
                f.setAttribute('allow','autoplay; fullscreen; picture-in-picture');
            });

            // Player wrapper'ı tam ekran yap
            var playerSelectors = ['.player','.video-player','#player',
                                   '.plyr','[class*="player"]','[id*="player"]'];
            playerSelectors.forEach(function(sel) {
                var el = document.querySelector(sel);
                if(el) {
                    el.style.width  = '100%';
                    el.style.height = '100vh';
                    el.style.position = 'fixed';
                    el.style.top = '0';
                    el.style.left = '0';
                    el.style.zIndex = '9999';
                }
            });
        })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun injectNavHide() {
        // Sitenin üst menüsünü gizle — kendi nav barımız var
        val js = """
        (function() {
            var style = document.createElement('style');
            style.innerHTML = `
                header, nav, .navbar, .header, .site-header,
                footer, .footer, .site-footer, .topbar, .top-bar {
                    display: none !important;
                }
                body { margin-top: 0 !important; padding-top: 0 !important; }
                /* Oynatma çubuğu ve dil butonu her zaman görünsün */
                video::-webkit-media-controls { display: flex !important; }
                video::-webkit-media-controls-panel { display: flex !important; }
                video::-webkit-media-controls-fullscreen-button { display: block !important; }
            `;
            document.head.appendChild(style);
        })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (isFullscreen) {
                    webView.evaluateJavascript(
                        "document.webkitExitFullscreen && document.webkitExitFullscreen()", null
                    )
                    true
                } else if (webView.canGoBack()) {
                    webView.goBack(); true
                } else {
                    super.onKeyDown(keyCode, event)
                }
            }
            KeyEvent.KEYCODE_DPAD_UP    -> { webView.scrollBy(0, -150); true }
            KeyEvent.KEYCODE_DPAD_DOWN  -> { webView.scrollBy(0,  150); true }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                webView.evaluateJavascript(
                    "var v=document.querySelector('video');if(v){v.paused?v.play():v.pause()}", null)
                true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                webView.evaluateJavascript(
                    "var v=document.querySelector('video');if(v)v.currentTime+=10", null)
                true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                webView.evaluateJavascript(
                    "var v=document.querySelector('video');if(v)v.currentTime-=10", null)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onPause()   { super.onPause();   webView.onPause()  }
    override fun onResume()  { super.onResume();  webView.onResume() }
    override fun onDestroy() { webView.destroy(); super.onDestroy()  }
}
