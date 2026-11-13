package com.kioqstore.flexy

import android.app.Activity
import android.animation.ObjectAnimator
import android.view.animation.LinearInterpolator
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("kioq_flexy_ui", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private var french = false
    private var webView: WebView? = null
    private var drawer: LinearLayout? = null
    private var tickerText = ""
    private var tickerSpeed = 45
    private var imageUrls = mutableListOf<String>()
    private var tickerView: TextView? = null
    private var tickerAnimator: ObjectAnimator? = null
    private var imageStrip: HorizontalScrollView? = null
    private var imageStripContent: LinearLayout? = null
    private var autoScroll = false

    private data class Platform(val name: String, val subtitle: String, val url: String, val mark: String, val packageName: String? = null)
    private val platforms = listOf(
        Platform("FAWRIMOB", "فوري موب", "https://fawrimob.com", "F"),
        Platform("ALAADZ", "ألا دز", "https://alaadz.com", "A"),
        Platform("GIFTY", "جيڤتي", "https://play.google.com/store/apps/details?id=com.gifty.pro.android", "G", "com.gifty.pro.android"),
        Platform("ONECLICK", "وان كليك", "https://oneclickdz.com/ar", "1")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tickerText = prefs.getString("ticker", "مرحبا بكم في kioq flexy — فضاؤكم لخدمات التعبئة") ?: ""
        tickerSpeed = prefs.getInt("speed", 45)
        imageUrls = (prefs.getString("images", "") ?: "").split("\n").filter { it.startsWith("http") }.toMutableList()
        showHome()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, radius: Int = 18, stroke: Int = 0): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat(); if (stroke != 0) setStroke(dp(1), stroke) }

    private fun label(text: String, size: Float, color: Int = 0xFF17243A.toInt(), bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun button(text: String, onClick: () -> Unit, primary: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text; textSize = 14f; gravity = Gravity.CENTER
            setTextColor(if (primary) Color.WHITE else 0xFF24344B.toInt())
            background = shape(if (primary) 0xFF176B63.toInt() else 0xFFEAF0F4.toInt(), 13)
            setPadding(dp(16), dp(10), dp(16), dp(10)); setOnClickListener { onClick() }
        }

    private fun column(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun params(w: Int = -1, h: Int = -2) = LinearLayout.LayoutParams(w, h)

    private fun showHome() {
        webView?.destroy(); webView = null
        autoScroll = false
        val frame = FrameLayout(this).apply { setBackgroundColor(0xFFF4F7F8.toInt()) }
        val page = column().apply { setPadding(dp(18), dp(18), dp(18), dp(12)) }
        val scroll = ScrollView(this)
        scroll.addView(page)
        frame.addView(scroll, FrameLayout.LayoutParams(-1, -1))

        val top = row()
        val menu = button("☰", { openDrawer(frame) })
        top.addView(menu, params(dp(44), dp(42)))
        val brand = column().apply { gravity = Gravity.CENTER }
        brand.addView(label("kioq flexy", 23f, 0xFF123B3A.toInt(), true).apply { gravity = Gravity.CENTER })
        brand.addView(label("KIOQ.STORE", 10f, 0xFF74838E.toInt(), true).apply { gravity = Gravity.CENTER })
        top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(button("♧", { showMessage(if (french) "Aucune nouvelle notification" else "لا توجد تنبيهات جديدة") }))
        top.addView(Space(this), params(dp(5), 1))
        top.addView(button(if (french) "ع" else "FR", { french = !french; showHome() }))
        page.addView(top)

        val welcome = column().apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFF123B3A.toInt(), 0xFF237D73.toInt())).apply { cornerRadius = dp(22).toFloat() }
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        welcome.addView(label(if (french) "Bienvenue sur" else "مرحباً بك في", 14f, 0xFFD5E9E5.toInt()))
        welcome.addView(label("kioq flexy", 26f, Color.WHITE, true))
        welcome.addView(label(if (french) "Gérez vos plateformes depuis un seul espace" else "كل منصاتك في فضاء واحد، بسهولة ووضوح", 13f, Color.WHITE))
        val wlp = params(); wlp.topMargin = dp(14); page.addView(welcome, wlp)

        val tickerFrame = FrameLayout(this).apply {
            background = shape(0xFFE2F1ED.toInt(), 12)
            clipChildren = false; clipToPadding = false
        }
        val ticker = TextView(this).apply {
            text = tickerText; textSize = 14f; setTextColor(0xFF164D49.toInt())
            setSingleLine(true); gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
        }
        tickerView = ticker
        tickerFrame.addView(ticker, FrameLayout.LayoutParams(-2, -1, Gravity.CENTER_VERTICAL))
        val tlp = params(-1, dp(42)); tlp.topMargin = dp(12); page.addView(tickerFrame, tlp)
        tickerFrame.post { startTickerAnimation(tickerFrame, ticker) }

        val bannerTitle = label(if (french) "Actualités" else "آخر المستجدات", 16f, 0xFF24344B.toInt(), true)
        val btlp = params(); btlp.topMargin = dp(18); page.addView(bannerTitle, btlp)
        val strip = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val stripContent = row()
        imageStrip = strip; imageStripContent = stripContent
        strip.addView(stripContent)
        val slp = params(-1, dp(112)); slp.topMargin = dp(8); page.addView(strip, slp)
        renderImageBanners()

        val title = label(if (french) "Vos plateformes" else "منصاتك", 18f, 0xFF24344B.toInt(), true)
        val titleLp = params(); titleLp.topMargin = dp(20); page.addView(title, titleLp)
        page.addView(label(if (french) "Choisissez une plateforme pour consulter votre espace" else "اختر المنصة لعرض حسابك ورصيدك", 12f, 0xFF788692.toInt()))
        for (i in platforms.indices step 2) {
            val line = row()
            line.addView(platformCard(platforms[i]), LinearLayout.LayoutParams(0, dp(156), 1f).apply { marginEnd = dp(7); topMargin = dp(12) })
            line.addView(platformCard(platforms[i + 1]), LinearLayout.LayoutParams(0, dp(156), 1f).apply { marginStart = dp(7); topMargin = dp(12) })
            page.addView(line)
        }

        val store = button("⌂   KIOQ.STORE", { showMessage(if (french) "Lien de la boutique à configurer" else "رابط المتجر سيضاف من إعدادات الإدارة") }, true)
        val stlp = params(-1, dp(48)); stlp.topMargin = dp(18); page.addView(store, stlp)

        val footer = row().apply { gravity = Gravity.CENTER }
        footer.addView(button("‹", { if (webView != null) showHome() else onBackPressed() }))
        footer.addView(label(if (french) "  Espace KIOQ.STORE" else "  فضاء KIOQ.STORE", 11f, 0xFF8B98A2.toInt()))
        val flp = params(); flp.topMargin = dp(14); page.addView(footer, flp)

        setContentView(frame)
        startBannerScroll()
    }

    private fun platformCard(p: Platform): LinearLayout {
        val card = column().apply {
            gravity = Gravity.CENTER
            background = shape(Color.WHITE, 18, 0xFFE4EAED.toInt())
            setPadding(dp(10), dp(10), dp(10), dp(10))
            elevation = dp(2).toFloat()
        }
        val mark = TextView(this).apply {
            text = p.mark; textSize = 22f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = shape(when (p.mark) { "F" -> 0xFF1683A5.toInt(); "A" -> 0xFF4D6BB3.toInt(); "G" -> 0xFF8254B5.toInt(); else -> 0xFFE18A36.toInt() }, 16)
        }
        card.addView(mark, params(dp(48), dp(48)))
        val name = label(p.name, 14f, 0xFF1E3044.toInt(), true).apply { gravity = Gravity.CENTER }
        val nlp = params(-1); nlp.topMargin = dp(7); card.addView(name, nlp)
        card.addView(label(p.subtitle, 11f, 0xFF82909A.toInt()).apply { gravity = Gravity.CENTER })
        val open = button(if (french) "Voir le solde" else "عرض الرصيد", { openPlatform(p) }, true)
        val olp = params(-1, dp(34)); olp.topMargin = dp(8); card.addView(open, olp)
        return card
    }

    private fun renderImageBanners() {
        val content = imageStripContent ?: return
        content.removeAllViews()
        if (imageUrls.isEmpty()) {
            val placeholder = TextView(this).apply {
                text = if (french) "Vos offres et annonces apparaîtront ici" else "مساحة إعلانات وعروض المحل — تُضاف الصور من لوحة الإدارة"
                textSize = 12f; setTextColor(0xFF56716F.toInt()); gravity = Gravity.CENTER
                background = shape(0xFFE5EFED.toInt(), 16); setPadding(dp(14), 0, dp(14), 0)
            }
            content.addView(placeholder, LinearLayout.LayoutParams(dp(290), dp(100)))
            return
        }
        imageUrls.forEach { url ->
            val card = column().apply { background = shape(0xFFE5EFED.toInt(), 16); gravity = Gravity.CENTER }
            val img = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            card.addView(img, params(dp(260), dp(100)))
            content.addView(card, LinearLayout.LayoutParams(dp(260), dp(100)).apply { marginEnd = dp(10) })
            thread {
                try {
                    val bitmap = URL(url).openStream().use { BitmapFactory.decodeStream(it) }
                    runOnUiThread { img.setImageBitmap(bitmap) }
                } catch (_: Exception) { runOnUiThread { img.setImageDrawable(null) } }
            }
        }
    }

    private val scrollTask = object : Runnable {
        override fun run() {
            val strip = imageStrip ?: return
            if (autoScroll && strip.childCount > 0) {
                val max = strip.getChildAt(0).width - strip.width
                if (max > 0) {
                    val next = strip.scrollX + 2
                    if (next >= max) strip.scrollTo(0, 0) else strip.scrollTo(next, 0)
                }
                handler.postDelayed(this, (110 - tickerSpeed).coerceAtLeast(20).toLong())
            }
        }
    }
    private fun startBannerScroll() {
        autoScroll = true
        handler.removeCallbacks(scrollTask)
        handler.postDelayed(scrollTask, (110 - tickerSpeed).coerceAtLeast(20).toLong())
    }

    private fun openPlatform(p: Platform) {
        if (p.packageName != null) {
            val launch = packageManager.getLaunchIntentForPackage(p.packageName)
            if (launch != null) { startActivity(launch); return }
        }
        showWebPage(p.name, p.url)
    }

    private fun showWebPage(title: String, url: String) {
        autoScroll = false
        val layout = column().apply { setBackgroundColor(Color.WHITE) }
        val head = row().apply { setPadding(dp(12), dp(8), dp(12), dp(8)) }
        head.addView(button("‹", { showHome() }))
        head.addView(label(title, 16f, 0xFF203448.toInt(), true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, dp(42), 1f))
        layout.addView(head)
        val web = WebView(this).apply {
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
            webViewClient = WebViewClient(); webChromeClient = WebChromeClient()
            loadUrl(url)
        }
        webView = web
        layout.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        val bottom = row().apply { gravity = Gravity.CENTER }
        bottom.addView(button("‹", { if (web.canGoBack()) web.goBack() else showHome() }))
        layout.addView(bottom, params(-1, dp(48)))
        setContentView(layout)
    }

    private fun openDrawer(frame: FrameLayout) {
        if (drawer != null) return
        val shade = View(this).apply { setBackgroundColor(0x77000000); setOnClickListener { closeDrawer(frame) } }
        frame.addView(shade, FrameLayout.LayoutParams(-1, -1))
        val panel = column().apply {
            setPadding(dp(18), dp(28), dp(18), dp(18)); background = shape(Color.WHITE, 0)
        }
        panel.addView(label("kioq flexy", 24f, 0xFF14544D.toInt(), true))
        panel.addView(label("KIOQ.STORE", 11f, 0xFF83918F.toInt()))
        panel.addView(View(this).apply { setBackgroundColor(0xFFE8EEEE.toInt()) }, params(-1, dp(1)))
        val items = listOf(
            "معلومات الحساب" to { showMessage("ملف الحساب — سيتم ربط بيانات العميل عند تفعيل تسجيل الدخول") },
            "زبائني" to { showMessage("قائمة الزبائن ستظهر بعد ربط قاعدة البيانات ولوحة الإدارة") },
            "المنصات" to { showHome() },
            "تعريف عن المحل" to { showInfo() },
            "التنبيهات" to { showMessage("لا توجد تنبيهات جديدة") },
            "التنزيلات والتحديثات" to { showMessage("نسخة التطبيق الحالية 1.0.0") },
            "الإعدادات" to { showAdminPanel() },
            "اتصل بنا" to { showMessage("KIOQ.STORE") }
        )
        items.forEach { (name, action) ->
            val item = button("  $name", { closeDrawer(frame); action() })
            item.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            val lp = params(-1, dp(48)); lp.topMargin = dp(5); panel.addView(item, lp)
        }
        panel.addView(Space(this), LinearLayout.LayoutParams(1, 0, 1f))
        panel.addView(button("إغلاق القائمة", { closeDrawer(frame) }))
        frame.addView(panel, FrameLayout.LayoutParams(dp(300), -1, Gravity.END))
        drawer = panel
        panel.tag = shade
    }

    private fun closeDrawer(frame: FrameLayout) {
        val panel = drawer ?: return
        val shade = panel.tag as? View
        frame.removeView(panel); if (shade != null) frame.removeView(shade)
        drawer = null
    }

    private fun showInfo() {
        AlertDialog.Builder(this).setTitle("عن KIOQ.STORE")
            .setMessage("فضاء موحد لتسهيل الوصول إلى منصات الخدمات ومتابعة الحسابات. يمكن للإدارة تعديل معلومات المحل ووسائل التواصل لاحقاً.")
            .setPositiveButton("حسناً", null).show()
    }

    private fun showAdminPanel() {
        val options = arrayOf("تعديل الشريط الكتابي", "سرعة حركة الشريط", "إدارة صور البانر", "إدارة ظهور المنصات", "قائمة الزبائن")
        AlertDialog.Builder(this).setTitle("إعدادات الإدارة")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> editTicker()
                    1 -> editSpeed()
                    2 -> editImages()
                    3 -> showMessage("إعدادات إظهار وإخفاء المنصات ستربط بصلاحيات الإدارة وقاعدة البيانات.")
                    4 -> showMessage("قائمة الزبائن تتطلب ربط قاعدة البيانات وتفعيل حساب الإدارة.")
                }
            }.show()
    }

    private fun editTicker() {
        val input = EditText(this).apply { setText(tickerText); hint = "اكتب الإعلان المتحرك" }
        AlertDialog.Builder(this).setTitle("نص البانر").setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                tickerText = input.text.toString()
                prefs.edit().putString("ticker", tickerText).apply()
                tickerView?.text = tickerText\n                tickerView?.let { tv -> (tv.parent as? FrameLayout)?.let { startTickerAnimation(it, tv) } }
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun editSpeed() {
        val seek = SeekBar(this).apply { max = 90; progress = tickerSpeed }
        val wrap = column().apply { setPadding(dp(18), dp(12), dp(18), dp(4)); addView(seek) }
        AlertDialog.Builder(this).setTitle("سرعة الحركة").setView(wrap)
            .setPositiveButton("حفظ") { _, _ ->
                tickerSpeed = seek.progress.coerceIn(10, 90)
                prefs.edit().putInt("speed", tickerSpeed).apply()
                startBannerScroll()
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun editImages() {
        val input = EditText(this).apply {
            setText(imageUrls.joinToString("\n")); hint = "ضع رابط كل صورة في سطر مستقل"
            minLines = 4; gravity = Gravity.TOP
        }
        AlertDialog.Builder(this).setTitle("روابط صور البانر").setMessage("أضف رابط صورة مباشر لكل سطر.")
            .setView(input).setPositiveButton("حفظ") { _, _ ->
                imageUrls = input.text.toString().lines().map { it.trim() }.filter { it.startsWith("https://") }.toMutableList()
                prefs.edit().putString("images", imageUrls.joinToString("\n")).apply()
                renderImageBanners()
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun showMessage(message: String) {
        AlertDialog.Builder(this).setMessage(message).setPositiveButton("حسناً", null).show()
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        val current = webView
        if (current != null && current.canGoBack()) current.goBack()
        else { webView = null; showHome() }
    }

    override fun onDestroy() {
        autoScroll = false
        handler.removeCallbacks(scrollTask)
        webView?.destroy()
        super.onDestroy()
    }
}
