package com.kioqstore.flexy

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private var webView: WebView? = null
    private var french = false

    private data class Platform(val name: String, val url: String, val packageName: String? = null)
    private val platforms = listOf(
        Platform("FAWRIMOB", "https://fawrimob.com"),
        Platform("ALAADZ", "https://alaadz.com"),
        Platform("GIFTY", "", "com.gifty.pro.android"),
        Platform("ONECLICK", "https://oneclickdz.com/ar")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
    }

    private fun baseLayout(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(24, 28, 24, 24)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(0xFFF5F7FA.toInt())
    }

    private fun showHome() {
        webView?.destroy()
        webView = null
        root = baseLayout()
        val header = TextView(this).apply {
            text = "KIOQ FLEXY"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(0xFF14243A.toInt())
            setPadding(8, 12, 8, 18)
        }
        root.addView(header)
        val subtitle = TextView(this).apply {
            text = if (french) "Votre espace — KIOQ.STORE" else "فضاؤك الموحد — مملوك لمحل KIOQ.STORE"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(8, 0, 8, 22)
        }
        root.addView(subtitle)
        val language = Button(this).apply {
            text = if (french) "العربية" else "Français"
            setOnClickListener { french = !french; showHome() }
        }
        root.addView(language)
        platforms.forEach { platform ->
            val button = Button(this).apply {
                text = platform.name
                textSize = 17f
                isAllCaps = false
                setOnClickListener {
                    if (platform.packageName != null) openGifty(platform.packageName)
                    else if (platform.url.isNotBlank()) openPlatform(platform)
                    else showMessage(if (french) "Adresse officielle à ajouter" else "نحتاج إلى الرابط الرسمي لهذه المنصة")
                }
            }
            root.addView(button, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 9, 0, 9) })
        }
        setContentView(root)
    }

    private fun openGifty(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            val marketIntent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("market://details?id=$packageName")
            )
            try {
                startActivity(marketIntent)
            } catch (_: android.content.ActivityNotFoundException) {
                val webIntent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                )
                startActivity(webIntent)
            }
        }
    }

    private fun showMessage(message: String) {
        android.app.AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun openPlatform(platform: Platform) {
        root = baseLayout()
        val back = Button(this).apply {
            text = if (french) "Retour" else "رجوع"
            setOnClickListener { showHome() }
        }
        root.addView(back)
        val view = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            loadUrl(platform.url)
        }
        webView = view
        root.addView(view, LinearLayout.LayoutParams(0, 0, 1f).apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = 0
        })
        setContentView(root)
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        val current = webView
        if (current != null && current.canGoBack()) current.goBack()
        else if (current != null) showHome()
        else super.onBackPressed()
    }
}
