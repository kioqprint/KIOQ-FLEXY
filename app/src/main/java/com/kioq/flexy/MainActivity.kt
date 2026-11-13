package com.kioqstore.flexy

import android.app.Activity
import androidx.core.view.WindowCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
    private var isAdmin = false
    private var customerName = ""
    private var customerPhone = ""
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

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
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = 0xFFF4F7F8.toInt()
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
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
        val page = column().apply { setPadding(dp(18), dp(8), dp(18), dp(12)) }
        val scroll = ScrollView(this)
        scroll.addView(page)
        frame.addView(scroll, FrameLayout.LayoutParams(-1, -1))

        val top = row()
        top.addView(button("ⓘ", { showInfo() }), params(dp(40), dp(40)))
        top.addView(button(if (french) "ع" else "FR", { french = !french; showHome() }), params(dp(44), dp(40)))
        top.addView(button("♧", { showMessage(if (french) "Aucune nouvelle notification" else "لا توجد تنبيهات جديدة") }), params(dp(40), dp(40)))
        top.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
        val brand = column().apply { gravity = Gravity.CENTER }
        brand.addView(label("kioq flexy", 21f, 0xFF123B3A.toInt(), true).apply { gravity = Gravity.CENTER })
        brand.addView(label("KIOQ.STORE", 10f, 0xFF74838E.toInt(), true).apply { gravity = Gravity.CENTER })
        top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(button("☰", { openDrawer(frame) }), params(dp(44), dp(42)))
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
        frame.alpha = 0f
        frame.translationY = dp(10).toFloat()
        frame.animate().alpha(1f).translationY(0f).setDuration(220).start()
        startBannerScroll()
    }

    private fun platformCard(p: Platform): LinearLayout {
        val card = column().apply {
            gravity = Gravity.CENTER
            background = shape(Color.WHITE, 18, 0xFFE4EAED.toInt())
            setPadding(dp(10), dp(10), dp(10), dp(10))
            elevation = dp(2).toFloat()
        }
        val logo = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setPadding(dp(5), dp(5), dp(5), dp(5)); background = shape(0xFFF5F8F8.toInt(), 14) }
        card.addView(logo, params(dp(52), dp(52)))
        val domain = when (p.name) {
            "FAWRIMOB" -> "fawrimob.com"
            "ALAADZ" -> "alaadz.com"
            "GIFTY" -> "play.google.com"
            else -> "oneclickdz.com"
        }
        thread {
            try {
                val bitmap = BitmapFactory.decodeStream(URL("https://www.google.com/s2/favicons?domain=$domain&sz=128").openStream())
                runOnUiThread { if (bitmap != null) logo.setImageBitmap(bitmap) }
            } catch (_: Exception) { }
        }
        card.addView(label(p.name, 14f, 0xFF1E3044.toInt(), true).apply { gravity = Gravity.CENTER }, params(-1).apply { topMargin = dp(6) })
        card.addView(label(p.subtitle, 11f, 0xFF82909A.toInt()).apply { gravity = Gravity.CENTER })
        val balance = prefs.getString("balance_${p.name}", "غير مسجل") ?: "غير مسجل"
        card.addView(label("الرصيد: $balance", 12f, 0xFF176B63.toInt(), true).apply { gravity = Gravity.CENTER })
        card.addView(button(if (french) "Voir le solde" else "عرض الرصيد", { openPlatform(p) }, true), params(-1, dp(32)).apply { topMargin = dp(6) })
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

    private fun startTickerAnimation(container: FrameLayout, text: TextView) {
        text.post {
            tickerAnimator?.cancel()
            val start = container.width.toFloat()
            val end = -text.width.toFloat()
            if (start <= 0f || text.width <= 0) return@post
            tickerAnimator = ObjectAnimator.ofFloat(text, View.TRANSLATION_X, start, end).apply {
                duration = ((100 - tickerSpeed).coerceAtLeast(10) * 180L)
                repeatCount = ObjectAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        }
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
            "التنبيهات" to { showMessage("لا توجد تنبيهات جديدة") },
            "التنزيلات والتحديثات" to { showMessage("نسخة التطبيق الحالية 1.0.0") },
            "الإعدادات" to { showAdminPanel() },
            "وسائل الاتصال" to { showContacts() },
            "اتصل بنا" to { showContacts() }
        )
        items.forEach { (name, action) ->
            val item = button("  $name", { closeDrawer(frame); action() })
            item.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            val lp = params(-1, dp(48)); lp.topMargin = dp(5); panel.addView(item, lp)
        }
        panel.addView(Space(this), LinearLayout.LayoutParams(1, 0, 1f))
        panel.addView(button("إغلاق القائمة", { closeDrawer(frame) }))
        frame.addView(panel, FrameLayout.LayoutParams(dp(300), -1, Gravity.RIGHT))
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
        if (!isAdmin) { showMessage("هذه الخاصية متاحة للإدارة فقط"); return }
        val options = arrayOf("الشريط الكتابي والسرعة", "صور البانر", "تعديل الأرصدة", "إدارة ظهور المنصات", "قائمة الزبائن")
        AlertDialog.Builder(this).setTitle("إعدادات الإدارة")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> editTickerAndSpeed()
                    1 -> editImages()
                    2 -> editBalances()
                    3 -> showMessage("إعدادات إظهار وإخفاء المنصات ستربط بصلاحيات الإدارة وقاعدة البيانات.")
                    4 -> showMessage("قائمة الزبائن تتطلب ربط قاعدة البيانات وتفعيل حساب الإدارة.")
                }
            }.show()
    }


    private fun showContacts() {
        AlertDialog.Builder(this).setTitle("وسائل الاتصال")
            .setItems(arrayOf("اتصال موبيليس 0699761222", "اتصال أوريدو 0555552711", "واتساب موبيليس", "واتساب أوريدو", "البريد الإلكتروني kioq.store@gmail.com")) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:0699761222")))
                    1 -> startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:0555552711")))
                    2 -> openWhatsApp("0699761222")
                    3 -> openWhatsApp("0555552711")
                    4 -> startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:kioq.store@gmail.com")))
                }
            }.setNegativeButton("إغلاق", null).show()
    }

    private fun openWhatsApp(number: String) {
        val international = "213" + number.removePrefix("0")
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$international"))) }
        catch (_: Exception) { showMessage("تعذر فتح واتساب") }
    }

    private fun editTickerAndSpeed() {
        val input = EditText(this).apply { setText(tickerText); hint = "اكتب النص المتحرك" }
        val seek = SeekBar(this).apply { max = 90; progress = tickerSpeed }
        val wrap = column().apply {
            setPadding(dp(16), dp(8), dp(16), dp(4))
            addView(label("النص", 13f, 0xFF445566.toInt(), true))
            addView(input, params(-1, dp(52)))
            addView(label("السرعة", 13f, 0xFF445566.toInt(), true))
            addView(seek)
        }
        AlertDialog.Builder(this).setTitle("الشريط الكتابي والسرعة").setView(wrap)
            .setPositiveButton("حفظ") { _, _ ->
                tickerText = input.text.toString()
                tickerSpeed = seek.progress.coerceIn(10, 90)
                prefs.edit().putString("ticker", tickerText).putInt("speed", tickerSpeed).apply()
                tickerView?.text = tickerText
                tickerView?.let { tv -> (tv.parent as? FrameLayout)?.let { startTickerAnimation(it, tv) } }
                startBannerScroll()
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun editBalances() {
        val wrap = column().apply { setPadding(dp(14), dp(8), dp(14), dp(4)) }
        val fields = platforms.map { p ->
            val field = EditText(this).apply {
                hint = "رصيد ${p.name} بالدينار"
                setText(prefs.getString("balance_${p.name}", ""))
                inputType = 8194
            }
            wrap.addView(label(p.name, 13f, 0xFF176B63.toInt(), true))
            wrap.addView(field, params(-1, dp(48)))
            p.name to field
        }
        AlertDialog.Builder(this).setTitle("تحديث الأرصدة").setMessage("أدخل آخر الأرصدة المؤكدة يدوياً. لا يوجد ربط مباشر مع المنصات حالياً.")
            .setView(wrap).setPositiveButton("حفظ") { _, _ ->
                val editor = prefs.edit()
                fields.forEach { (name, field) -> editor.putString("balance_$name", field.text.toString().trim().ifBlank { "غير مسجل" }) }
                editor.apply()
                showHome()
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun editTicker() {
        val input = EditText(this).apply { setText(tickerText); hint = "اكتب الإعلان المتحرك" }
        AlertDialog.Builder(this).setTitle("نص البانر").setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                tickerText = input.text.toString()
                prefs.edit().putString("ticker", tickerText).apply()
                tickerView?.text = tickerText
                tickerView?.let { tv ->
                    (tv.parent as? FrameLayout)?.let { container -> startTickerAnimation(container, tv) }
                }
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
