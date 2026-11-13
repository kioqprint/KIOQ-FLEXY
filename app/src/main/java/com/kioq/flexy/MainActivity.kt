package com.kioqstore.flexy

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
        tickerText = prefs.getString("ticker", "مرحبا بكم في kioq flexy — فضاء خدماتكم") ?: ""
        tickerSpeed = prefs.getInt("speed", 45)
        imageUrls = (prefs.getString("images", "") ?: "").split("\\n").filter { it.startsWith("http") || it.startsWith("content://") }.toMutableList()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = 0xFFF2F6FF.toInt()
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val user = auth.currentUser
        if (user == null) {
            prefs.edit().putBoolean("customer_session", false).apply()
            showLogin()
        } else if (user.email.equals("KIOQ.STORE@gmail.com", true)) {
            // The dedicated administrator is authenticated by Firebase Auth.
            // Do not require a Firestore role document for the first admin login.
            isAdmin = true
            customerName = "الإدارة"
            customerPhone = ""
            showHome()
        } else {
            isAdmin = false
            customerName = user.displayName?.takeIf { it.isNotBlank() } ?: "زبون"
            customerPhone = user.email?.substringBefore("@")?.removePrefix("customer_") ?: ""
            if (customerPhone.startsWith("213") && customerPhone.length == 12) customerPhone = "0" + customerPhone.drop(3)
            showHome()
        }
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
            setTextColor(if (primary) Color.WHITE else 0xFF18324F.toInt())
            background = shape(if (primary) 0xFF1558B0.toInt() else 0xFFE1EAF5.toInt(), 13)
            setPadding(dp(16), dp(10), dp(16), dp(10)); setOnClickListener { onClick() }
        }

    private fun column(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun params(w: Int = -1, h: Int = -2) = LinearLayout.LayoutParams(w, h)


    private fun normalizePhone(raw: String): String {
        var digits = raw.filter { it.isDigit() }
        if (digits.startsWith("00213")) digits = digits.drop(5)
        else if (digits.startsWith("213") && digits.length == 12) digits = digits.drop(3)
        else if (digits.startsWith("0")) digits = digits.drop(1)
        return digits
    }

    private fun phoneLoginEmail(raw: String): String = "customer_" + normalizePhone(raw) + "@kioqflexy.app"

    private fun showLogin() {
        val root = FrameLayout(this).apply { setBackgroundColor(0xFFF2F6FF.toInt()) }
        val body = column().apply { setPadding(dp(22), dp(32), dp(22), dp(24)) }
        val scroll = ScrollView(this); scroll.addView(body); root.addView(scroll)
        body.addView(label("kioq flexy", 30f, 0xFF123B75.toInt(), true).apply { gravity = Gravity.CENTER })
        body.addView(label("فضاء KIOQ.STORE", 14f, 0xFF526783.toInt()).apply { gravity = Gravity.CENTER })
        val card = column().apply { background = shape(Color.WHITE, 20); setPadding(dp(16), dp(18), dp(16), dp(16)); elevation = dp(3).toFloat() }
        card.addView(label("مرحباً بك", 19f, 0xFF123B75.toInt(), true))
        card.addView(label("أدخل رقم هاتفك وكلمة المرور", 13f, 0xFF7486A4.toInt()))
        val name = EditText(this).apply { hint = "الاسم الكامل (للحساب الجديد)"; setText(prefs.getString("customer_name", "")); isSingleLine = true }
        val identity = EditText(this).apply { hint = "رقم الهاتف"; setText(prefs.getString("customer_phone", "")); isSingleLine = true }
        val password = EditText(this).apply { hint = "كلمة المرور"; inputType = 129; isSingleLine = true }
        card.addView(name, params(-1, dp(50)))
        card.addView(identity, params(-1, dp(50)))
        card.addView(password, params(-1, dp(50)))
        card.addView(button("تسجيل الدخول", {
            val id = identity.text.toString().trim()
            val pass = password.text.toString()
            if (id.contains("@")) {
                if (!id.equals("KIOQ.STORE@gmail.com", true) || pass.isBlank()) {
                    showMessage("تعذر الدخول. تحقق من بيانات الحساب.")
                } else {
                    auth.signInWithEmailAndPassword("KIOQ.STORE@gmail.com", pass)
                        .addOnSuccessListener {
                            isAdmin = true
                            customerName = "الإدارة"
                            customerPhone = ""
                            showHome()
                        }
                        .addOnFailureListener { showMessage("تعذر الدخول. تحقق من بيانات الحساب.") }
                }
            } else {
                val phone = normalizePhone(id)
                if (phone.length !in 9..10 || pass.length < 6) {
                    showMessage("أدخل رقم هاتف صحيحاً وكلمة مرور من 6 أحرف على الأقل.")
                } else {
                    auth.signInWithEmailAndPassword(phoneLoginEmail(id), pass)
                        .addOnSuccessListener { result ->
                            isAdmin = false
                            customerName = result.user?.displayName?.takeIf { it.isNotBlank() } ?: "زبون"
                            customerPhone = id
                            prefs.edit().putString("customer_name", customerName).putString("customer_phone", id).apply()
                            showHome()
                        }
                        .addOnFailureListener { showMessage("تعذر الدخول. تحقق من رقم الهاتف وكلمة المرور، أو أنشئ حساباً جديداً.") }
                }
            }
        }, true), params(-1, dp(46)).apply { topMargin = dp(12) })
        card.addView(button("إنشاء حساب زبون جديد", {
            val n = name.text.toString().trim()
            val phoneText = identity.text.toString().trim()
            val phone = normalizePhone(phoneText)
            val pass = password.text.toString()
            if (n.isBlank() || phone.length !in 9..10 || pass.length < 6 || phoneText.contains("@")) {
                showMessage("أدخل الاسم ورقم الهاتف وكلمة مرور من 6 أحرف على الأقل.")
            } else {
                auth.createUserWithEmailAndPassword(phoneLoginEmail(phoneText), pass)
                    .addOnSuccessListener { result ->
                        val user = result.user ?: return@addOnSuccessListener
                        val profile = com.google.firebase.auth.UserProfileChangeRequest.Builder().setDisplayName(n).build()
                        user.updateProfile(profile).addOnCompleteListener {
                            val data = hashMapOf<String, Any>(
                                "name" to n,
                                "phone" to phoneText,
                                "role" to "customer",
                                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                            )
                            db.collection("users").document(user.uid).set(data)
                                .addOnSuccessListener {
                                    isAdmin = false; customerName = n; customerPhone = phoneText
                                    prefs.edit().putString("customer_name", n).putString("customer_phone", phoneText).apply()
                                    showHome()
                                }
                                .addOnFailureListener {
                                    auth.signOut()
                                    showMessage("تم إنشاء الحساب لكن تعذر حفظ الملف الشخصي. راجع إعدادات الأمان وحاول مجدداً.")
                                }
                        }
                    }
                    .addOnFailureListener { showMessage("لم نتمكن من إنشاء الحساب. قد يكون رقم الهاتف مسجلاً من قبل.") }
            }
        }), params(-1, dp(44)).apply { topMargin = dp(8) })
        body.addView(card, params(-1))
        body.addView(label("للمساعدة في الدخول، تواصل مع المحل.", 11f, 0xFF7B8993.toInt()).apply { gravity = Gravity.CENTER; setPadding(0, dp(12), 0, 0) })
        setContentView(root)
        root.alpha = 0f
        root.animate().alpha(1f).setDuration(220).start()
    }

    private fun logout() {
        auth.signOut()
        isAdmin = false
        prefs.edit().putBoolean("customer_session", false).apply()
        showLogin()
    }

    private fun showHome() {
        webView?.destroy(); webView = null
        autoScroll = false
        val frame = FrameLayout(this).apply { setBackgroundColor(0xFFF2F6FF.toInt()) }
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
        brand.addView(label("kioq flexy", 21f, 0xFF123B75.toInt(), true).apply { gravity = Gravity.CENTER })
        brand.addView(label("KIOQ.STORE", 10f, 0xFF526783.toInt(), true).apply { gravity = Gravity.CENTER })
        top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(button("☰", { openDrawer(frame) }), params(dp(44), dp(42)))
        page.addView(top)

        val welcome = column().apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFF123B75.toInt(), 0xFF1769D2.toInt())).apply { cornerRadius = dp(22).toFloat() }
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        welcome.addView(label(if (french) "Bienvenue sur" else "مرحباً بك في", 14f, 0xFFDCEAFF.toInt()))
        welcome.addView(label("kioq flexy", 26f, Color.WHITE, true))
        welcome.addView(label(if (french) "Gérez vos plateformes depuis un seul espace" else "كل منصاتك في فضاء واحد، بسهولة ووضوح", 13f, Color.WHITE))
        val wlp = params(); wlp.topMargin = dp(14); page.addView(welcome, wlp)

        val tickerFrame = FrameLayout(this).apply {
            background = shape(0xFFE5F0FF.toInt(), 12)
            clipChildren = false; clipToPadding = false
        }
        val ticker = TextView(this).apply {
            text = tickerText; textSize = 14f; setTextColor(0xFF164A9A.toInt())
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
        page.addView(label(if (french) "Choisissez une plateforme pour consulter votre espace" else "اختر المنصة للدخول إلى حسابك", 12f, 0xFF526783.toInt()))
        val visiblePlatforms = platforms.filter { prefs.getBoolean("platform_visible_${it.name}", true) }
        visiblePlatforms.chunked(2).forEach { pair ->
            val line = row()
            line.addView(platformCard(pair[0]), LinearLayout.LayoutParams(0, dp(148), 1f).apply { marginEnd = dp(7); topMargin = dp(12) })
            if (pair.size > 1) {
                line.addView(platformCard(pair[1]), LinearLayout.LayoutParams(0, dp(148), 1f).apply { marginStart = dp(7); topMargin = dp(12) })
            } else {
                line.addView(Space(this), LinearLayout.LayoutParams(0, dp(148), 1f).apply { marginStart = dp(7) })
            }
            page.addView(line)
        }
        if (visiblePlatforms.isEmpty()) {
            page.addView(label("لا توجد منصات ظاهرة حالياً", 13f, 0xFF526783.toInt()).apply { gravity = Gravity.CENTER; setPadding(0, dp(20), 0, dp(20)) })
        }

        val store = button("⌂   KIOQ.STORE", { showMessage(if (french) "Lien de la boutique à configurer" else "رابط المتجر سيضاف من إعدادات الإدارة") }, true)
        val stlp = params(-1, dp(48)); stlp.topMargin = dp(18); page.addView(store, stlp)

        val footer = row().apply { gravity = Gravity.CENTER }
        footer.addView(button("‹", { if (webView != null) showHome() else onBackPressed() }))
        footer.addView(label(if (french) "  Espace KIOQ.STORE" else "  فضاء KIOQ.STORE", 11f, 0xFF526783.toInt()))
        val flp = params(); flp.topMargin = dp(14); page.addView(footer, flp)

        setContentView(frame)
        applySystemInsets(frame)
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
        val logoUrl = when (p.name) {
            "FAWRIMOB" -> "https://fawrimob.com/favicon.ico"
            "ALAADZ" -> "https://alaadz.com/favicon.ico"
            "GIFTY" -> "https://is1-ssl.mzstatic.com/image/thumb/Purple221/v4/a8/49/a2/a849a2ac-73bb-e0ae-5d7c-4f7851d6dc8e/AppIcon-0-0-1x_U007emarketing-0-8-0-85-220.png/512x512bb.webp"
            else -> "https://oneclickdz.com/favicon.ico"
        }
        thread {
            try {
                val bitmap = URL(logoUrl).openStream().use { BitmapFactory.decodeStream(it) }
                runOnUiThread { if (bitmap != null) logo.setImageBitmap(bitmap) }
            } catch (_: Exception) { }
        }
        card.addView(label(p.name, 14f, 0xFF1E3044.toInt(), true).apply { gravity = Gravity.CENTER }, params(-1).apply { topMargin = dp(6) })
        card.addView(label(p.subtitle, 11f, 0xFF526783.toInt()).apply { gravity = Gravity.CENTER })
        card.addView(button(if (french) "Ouvrir" else "فتح المنصة", { openPlatform(p) }, true), params(-1, dp(34)).apply { topMargin = dp(8) })
        return card
    }

    private fun renderImageBanners() {
        val content = imageStripContent ?: return
        content.removeAllViews()
        if (imageUrls.isEmpty()) {
            val placeholder = TextView(this).apply {
                text = if (french) "Vos offres et annonces apparaîtront ici" else "مساحة إعلانات وعروض المحل — تُضاف الصور من لوحة الإدارة"
                textSize = 12f; setTextColor(0xFF496A9C.toInt()); gravity = Gravity.CENTER
                background = shape(0xFFEAF2FF.toInt(), 16); setPadding(dp(14), 0, dp(14), 0)
            }
            content.addView(placeholder, LinearLayout.LayoutParams(dp(290), dp(100)))
            return
        }
        imageUrls.forEachIndexed { index, uriText ->
            val card = column().apply { background = shape(0xFFEAF2FF.toInt(), 16); gravity = Gravity.CENTER }
            val img = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            card.addView(img, params(dp(260), dp(100)))
            val caption = prefs.getString("caption_$index", "") ?: ""
            if (caption.isNotBlank()) card.addView(label(caption, 12f, 0xFF24344B.toInt()).apply { gravity = Gravity.CENTER; setPadding(dp(8), dp(4), dp(8), dp(4)) })
            content.addView(card, LinearLayout.LayoutParams(dp(260), -2).apply { marginEnd = dp(10) })
            if (uriText.startsWith("content://")) {
                try { img.setImageURI(Uri.parse(uriText)) } catch (_: Exception) { }
            } else thread {
                try {
                    val bitmap = URL(uriText).openStream().use { BitmapFactory.decodeStream(it) }
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
        applySystemInsets(layout)
        layout.alpha = 0f; layout.translationY = dp(10).toFloat()
        layout.animate().alpha(1f).translationY(0f).setDuration(220).start()
    }

    private fun applySystemInsets(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun openDrawer(frame: FrameLayout) {
        if (drawer != null) return
        val shade = View(this).apply { setBackgroundColor(0x77000000); setOnClickListener { closeDrawer(frame) } }
        frame.addView(shade, FrameLayout.LayoutParams(-1, -1))
        val panel = column().apply {
            setPadding(dp(18), dp(28), dp(18), dp(18)); background = shape(Color.WHITE, 0)
        }
        panel.addView(label("kioq flexy", 24f, 0xFF174A9A.toInt(), true))
        panel.addView(label("KIOQ.STORE", 11f, 0xFF526783.toInt()))
        panel.addView(View(this).apply { setBackgroundColor(0xFFE8EEEE.toInt()) }, params(-1, dp(1)))
        val items = mutableListOf<Pair<String, () -> Unit>>()
        items.add("معلومات الحساب" to { showMessage("حساب KIOQ.STORE") })
        if (isAdmin) {
            items.add("زبائني" to { showMessage("إدارة الزبائن تحتاج استكمال ربط قاعدة البيانات.") })
            items.add("الإعدادات" to { showAdminPanel() })
        }
        items.add("المنصات" to { showHome() })
        items.add("التنبيهات" to { showMessage("لا توجد تنبيهات جديدة") })
        items.add("التنزيلات والتحديثات" to { showMessage("نسخة التطبيق الحالية 1.0.0") })
        items.add("وسائل الاتصال" to { showContacts() })
        items.add("تسجيل الخروج" to { logout() })
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
        val options = arrayOf("الشريط الكتابي والسرعة", "صور البانر", "إدارة ظهور المنصات", "قائمة الزبائن")
        AlertDialog.Builder(this).setTitle("إعدادات الإدارة")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> editTickerAndSpeed()
                    1 -> editImages()
                    2 -> editPlatformVisibility()
                    3 -> showMessage("قائمة الزبائن غير مفعّلة بعد.")
                }
            }.show()
    }


    private fun showContacts() {
        val choices = arrayOf("اتصال موبيليس 0699761222", "اتصال أوريدو 0555552711", "واتساب موبيليس", "واتساب أوريدو", "البريد الإلكتروني kioq.store@gmail.com")
        AlertDialog.Builder(this).setTitle("وسائل الاتصال").setItems(choices) { _, which ->
            when (which) {
                0 -> startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:0699761222")))
                1 -> startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:0555552711")))
                2 -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/213699761222")))
                3 -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/213555552711")))
                4 -> startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:kioq.store@gmail.com")))
            }
        }.setNegativeButton("إغلاق", null).show()
    }

    private fun editTickerAndSpeed() {
        val input = EditText(this).apply { setText(tickerText); hint = "النص المتحرك" }
        val seek = SeekBar(this).apply { max = 90; progress = tickerSpeed }
        val box = column().apply { setPadding(dp(16), dp(8), dp(16), dp(8)); addView(input); addView(label("السرعة", 13f)); addView(seek) }
        AlertDialog.Builder(this).setTitle("الشريط الكتابي والسرعة").setView(box)
            .setPositiveButton("حفظ") { _, _ ->
                tickerText = input.text.toString()
                tickerSpeed = seek.progress.coerceIn(10, 90)
                prefs.edit().putString("ticker", tickerText).putInt("speed", tickerSpeed).apply()
                tickerView?.text = tickerText
                tickerView?.let { tv -> (tv.parent as? FrameLayout)?.let { startTickerAnimation(it, tv) } }
                startBannerScroll()
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun editPlatformVisibility() {
        if (!isAdmin) { showMessage("هذه الخاصية متاحة للإدارة فقط"); return }
        val names = platforms.map { it.name }.toTypedArray()
        val checked = BooleanArray(platforms.size) { i -> prefs.getBoolean("platform_visible_${platforms[i].name}", true) }
        AlertDialog.Builder(this)
            .setTitle("إظهار أو إخفاء المنصات")
            .setMultiChoiceItems(names, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("حفظ") { _, _ ->
                val edit = prefs.edit()
                platforms.forEachIndexed { i, p -> edit.putBoolean("platform_visible_${p.name}", checked[i]) }
                edit.apply()
                showHome()
            }
            .setNegativeButton("إلغاء", null)
            .show()
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
        val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(pick, 2401)
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 2401 || resultCode != RESULT_OK || data == null) return
        val selected = mutableListOf<Uri>()
        data.clipData?.let { clip -> for (i in 0 until clip.itemCount) selected.add(clip.getItemAt(i).uri) }
        if (selected.isEmpty()) data.data?.let { selected.add(it) }
        selected.forEach { uri ->
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            imageUrls.add(uri.toString())
        }
        imageUrls = imageUrls.distinct().toMutableList()
        prefs.edit().putString("images", imageUrls.joinToString("\n")).apply()
        editBannerCaptions()
    }

    private fun editBannerCaptions() {
        val box = column().apply { setPadding(dp(14), dp(8), dp(14), dp(4)) }
        val fields = imageUrls.mapIndexed { index, _ ->
            val input = EditText(this).apply { hint = "النص أسفل الصورة " + (index + 1); setText(prefs.getString("caption_" + index, "")) }
            box.addView(label("الصورة " + (index + 1), 13f, 0xFF1769D2.toInt(), true))
            box.addView(input, params(-1, dp(48)))
            index to input
        }
        AlertDialog.Builder(this).setTitle("وصف صور البانر").setView(box)
            .setPositiveButton("حفظ") { _, _ ->
                val edit = prefs.edit()
                fields.forEach { pair -> edit.putString("caption_" + pair.first, pair.second.text.toString()) }
                edit.apply(); renderImageBanners()
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
