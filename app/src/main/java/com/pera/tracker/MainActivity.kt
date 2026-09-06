package com.pera.tracker

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class MainActivity : Activity() {
    private lateinit var data: AppData
    private lateinit var contentContainer: SwipeContainer
    private lateinit var navBar: LinearLayout
    private var currentTab: String = "home"
    private val pesoFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "PH"))
    private val navButtons = mutableMapOf<String, TextView>()

    private val titleFont = Typeface.create("serif", Typeface.BOLD)
    private val headFont = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val bigFont = Typeface.create("sans-serif", Typeface.BOLD)
    private val bodyFont = Typeface.create("sans-serif", Typeface.NORMAL)

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        data = Store.load(this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        contentContainer = SwipeContainer(this)
        contentContainer.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        contentContainer.onSwipeLeft = { navigateRelative(1) }
        contentContainer.onSwipeRight = { navigateRelative(-1) }
        root.addView(contentContainer)

        navBar = LinearLayout(this)
        navBar.orientation = LinearLayout.HORIZONTAL
        navBar.setPadding(0, 30, 0, 30)
        root.addView(navBar)
        populateNavBar()

        setContentView(root)
        showTab("home", 0)
    }

    private fun persist() { Store.save(this, data) }

    private fun navigateRelative(delta: Int) {
        val items = navItemList().map { it.first }
        val idx = items.indexOf(currentTab)
        if (idx == -1) return
        val newIdx = (idx + delta).coerceIn(0, items.size - 1)
        if (newIdx != idx) showTab(items[newIdx], if (newIdx > idx) 1 else -1)
    }

    // ---------- GOAL AUTO-TILE / AUTO-COMPLETE ----------
    private fun checkGoalCompletions() {
        val nw = netWorth()
        val done = data.goals.filter { g -> nw - g.startAmount >= (g.targetAmount - g.startAmount) && g.targetAmount != g.startAmount }
        if (done.isNotEmpty()) {
            for (g in done) {
                Toast.makeText(this, "🎉 Goal reached: ${g.label}!", Toast.LENGTH_LONG).show()
            }
            data.goals.removeAll { g -> done.any { it.id == g.id } }
            persist()
        }
    }

    // ---------- THEMES ----------
    private fun contrastColor(color: Int): Int {
        val r = Color.red(color); val g = Color.green(color); val b = Color.blue(color)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255
        return if (luminance > 0.6) Color.BLACK else Color.WHITE
    }

    private fun palette(): Palette {
        val isDark = data.settings.theme == "dark"
        val solidText = if (isDark) Color.WHITE else Color.BLACK
        val base = when (data.settings.theme) {
            "dark" -> Palette(Color.parseColor("#0D1B24"), Color.parseColor("#132A38"), solidText, solidText, Color.parseColor("#FFB100"), Color.parseColor("#FFB100"), Color.parseColor("#4ADE80"), Color.parseColor("#FB7185"), Color.parseColor("#233F4F"), Color.parseColor("#0D1B24"))
            "sunset" -> Palette(Color.parseColor("#FFF7ED"), Color.WHITE, solidText, solidText, Color.parseColor("#C2410C"), Color.parseColor("#F59E0B"), Color.parseColor("#16A34A"), Color.parseColor("#DC2626"), Color.parseColor("#FED7AA"), Color.WHITE)
            "ocean" -> Palette(Color.parseColor("#F0F9FF"), Color.WHITE, solidText, solidText, Color.parseColor("#0369A1"), Color.parseColor("#06B6D4"), Color.parseColor("#059669"), Color.parseColor("#DC2626"), Color.parseColor("#BAE6FD"), Color.WHITE)
            else -> Palette(Color.parseColor("#F5F6F4"), Color.WHITE, solidText, solidText, Color.parseColor("#1B3A4B"), Color.parseColor("#FFB100"), Color.parseColor("#2E8B57"), Color.parseColor("#E4572E"), Color.parseColor("#E1E4E0"), Color.WHITE)
        }
        val customHex = data.settings.customAccent
        if (!customHex.isNullOrBlank()) {
            return try {
                val c = Color.parseColor(customHex)
                base.copy(primary = c, accent = c, onPrimary = contrastColor(c))
            } catch (e: Exception) { base }
        }
        return base
    }

    // ---------- ANIMATION HELPERS ----------
    private fun addPressAnim(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            false
        }
    }

    private fun exemptFromSwipe(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.parent?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
    }

    private fun showAnimatedDialog(builder: AlertDialog.Builder): AlertDialog {
        val dialog = builder.create()
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation
        dialog.show()
        return dialog
    }

    // ---------- STYLE HELPERS ----------
    private fun roundedBg(color: Int, radius: Float = 24f, strokeColor: Int? = null): GradientDrawable {
        val d = GradientDrawable()
        d.cornerRadius = radius
        d.setColor(color)
        if (strokeColor != null) d.setStroke(3, strokeColor)
        return d
    }

    private fun styledButton(text: String, bgColor: Int, textColor: Int, outline: Boolean = false): Button {
        val b = Button(this)
        b.text = text
        b.setTextColor(if (outline) bgColor else textColor)
        b.typeface = headFont
        b.textSize = 14f
        b.setPadding(40, 32, 40, 32)
        b.isAllCaps = false
        b.background = if (outline) roundedBg(Color.TRANSPARENT, 24f, bgColor) else roundedBg(bgColor, 24f)
        b.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        b.stateListAnimator = null
        b.minHeight = 0
        b.minimumHeight = 0
        addPressAnim(b)
        return b
    }

    private fun styledEditText(p: Palette, hint: String): EditText {
        val e = EditText(this)
        e.hint = hint
        e.typeface = bodyFont
        e.textSize = 15f
        e.setHintTextColor(Color.argb(140, Color.red(p.text), Color.green(p.text), Color.blue(p.text)))
        e.setTextColor(p.text)
        e.setPadding(28, 26, 28, 26)
        e.background = roundedBg(p.surface, 16f, p.border)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = 12
        e.layoutParams = lp
        return e
    }

    // big, all-caps, distinctly-fonted page header
    private fun pageTitle(p: Palette, text: String): TextView {
        val t = TextView(this)
        t.text = text.uppercase(Locale.US)
        t.typeface = titleFont
        t.textSize = 22f
        t.letterSpacing = 0.03f
        t.setTextColor(p.text)
        t.setPadding(0, 0, 0, 16)
        return t
    }

    private fun sectionTitle(p: Palette, text: String, topMargin: Int = 40): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 18f
        t.typeface = headFont
        t.setTextColor(p.text)
        t.setPadding(0, topMargin, 0, 14)
        return t
    }

    private fun card(p: Palette): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.isBaselineAligned = false
        c.setPadding(28, 28, 28, 28)
        c.background = roundedBg(p.surface, 24f, p.border)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = 16
        c.layoutParams = lp
        return c
    }

    private fun tappableCard(p: Palette, onTap: () -> Unit): LinearLayout {
        val c = card(p)
        c.isClickable = true
        c.isFocusable = true
        addPressAnim(c)
        c.setOnClickListener { onTap() }
        return c
    }

    private fun row(): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.isBaselineAligned = false
        return r
    }

    private fun bodyText(p: Palette, text: String, size: Float = 15f, muted: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.setTextColor(p.text)
        t.typeface = bodyFont
        t.textSize = size
        if (muted) t.alpha = 0.65f
        return t
    }

    private fun makeSpinnerAdapter(items: List<String>, textColor: Int): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(textColor)
                view.typeface = bodyFont
                view.textSize = 15f
                return view
            }
        }
    }

    private fun accountTile(p: Palette, acc: Account): LinearLayout {
        val t = LinearLayout(this)
        t.orientation = LinearLayout.VERTICAL
        t.setPadding(32, 32, 32, 32)
        t.background = roundedBg(p.surface, 20f, p.border)
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lp.marginEnd = 10; lp.marginStart = 10
        t.layoutParams = lp
        val name = TextView(this); name.text = acc.name; name.setTextColor(p.text); name.typeface = bodyFont; name.textSize = 14f; name.alpha = 0.7f
        t.addView(name)
        val bal = TextView(this); bal.text = pesoFormat.format(acc.balance); bal.setTextColor(p.text); bal.typeface = headFont; bal.textSize = 22f; bal.setPadding(0, 8, 0, 0)
        t.addView(bal)
        return t
    }

    private fun goalTile(p: Palette, goal: Goal): LinearLayout {
        val t = LinearLayout(this)
        t.orientation = LinearLayout.VERTICAL
        t.setPadding(32, 32, 32, 32)
        t.background = roundedBg(p.surface, 20f, p.accent)
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lp.marginEnd = 10; lp.marginStart = 10
        t.layoutParams = lp
        val name = TextView(this); name.text = "🎯 ${goal.label}"; name.setTextColor(p.text); name.typeface = bodyFont; name.textSize = 14f; name.alpha = 0.8f
        t.addView(name)
        val saved = (netWorth() - goal.startAmount).coerceAtLeast(0.0).coerceAtMost(goal.targetAmount - goal.startAmount)
        val bal = TextView(this)
        bal.text = pesoFormat.format(saved) + " / " + pesoFormat.format(goal.targetAmount - goal.startAmount)
        bal.setTextColor(p.accent); bal.typeface = headFont; bal.textSize = 16f; bal.setPadding(0, 8, 0, 6)
        t.addView(bal)
        val denom = (goal.targetAmount - goal.startAmount).let { if (it == 0.0) 1.0 else it }
        val pct = ((saved / denom) * 100).toInt().coerceIn(0, 100)
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 100; bar.progress = pct
        bar.progressDrawable?.setColorFilter(p.accent, PorterDuff.Mode.SRC_IN)
        t.addView(bar)
        return t
    }

    private fun accountTilePlaceholder(): LinearLayout {
        val t = LinearLayout(this)
        t.orientation = LinearLayout.VERTICAL
        t.setPadding(32, 32, 32, 32)
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lp.marginEnd = 10; lp.marginStart = 10
        t.layoutParams = lp
        t.alpha = 0f
        val name = TextView(this); name.text = " "; name.textSize = 14f; t.addView(name)
        val bal = TextView(this); bal.text = " "; bal.textSize = 22f; t.addView(bal)
        return t
    }

    private fun categoryRow(p: Palette, cat: Category?, amt: Double, pct: Int, big: Boolean): LinearLayout {
        val wrap = LinearLayout(this)
        wrap.orientation = LinearLayout.VERTICAL
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = if (big) 20 else 10
        wrap.layoutParams = lp
        val labelRow = row(); labelRow.gravity = Gravity.CENTER_VERTICAL
        val colorHex = cat?.colorHex ?: "#6B7280"
        val dot = View(this)
        val dotSize = if (big) 30 else 22
        val dotLp = LinearLayout.LayoutParams(dotSize, dotSize); dotLp.marginEnd = 14
        dot.layoutParams = dotLp
        dot.background = roundedBg(Color.parseColor(colorHex), dotSize / 2f)
        labelRow.addView(dot)
        val label = bodyText(p, "${cat?.emoji ?: "📦"} ${cat?.name ?: "Other"}", if (big) 17f else 15f)
        label.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        labelRow.addView(label)
        val amtText = TextView(this)
        amtText.text = pesoFormat.format(amt) + "  ($pct%)"
        amtText.setTextColor(p.text); amtText.typeface = headFont; amtText.textSize = if (big) 15f else 13f
        labelRow.addView(amtText)
        wrap.addView(labelRow)
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 100; bar.progress = pct
        bar.progressDrawable?.setColorFilter(Color.parseColor(colorHex), PorterDuff.Mode.SRC_IN)
        val barLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, if (big) 24 else LinearLayout.LayoutParams.WRAP_CONTENT)
        barLp.topMargin = 6
        bar.layoutParams = barLp
        wrap.addView(bar)
        return wrap
    }

    private fun renderCategoryBreakdown(container: LinearLayout, p: Palette, byCat: Map<String, Double>, total: Double, big: Boolean) {
        if (byCat.isEmpty()) { container.addView(bodyText(p, "No data yet.", if (big) 16f else 14f, muted = true)); return }
        for ((catId, amt) in byCat.entries.sortedByDescending { it.value }) {
            val cat = data.categories.find { it.id == catId }
            val pct = if (total > 0) ((amt / total) * 100).toInt() else 0
            container.addView(categoryRow(p, cat, amt, pct, big))
        }
    }

    private fun showEnlargeDialog(title: String, buildContent: (LinearLayout) -> Unit) {
        val outer = LinearLayout(this)
        outer.orientation = LinearLayout.VERTICAL
        outer.setPadding(20, 10, 20, 10)
        buildContent(outer)
        val scroll = ScrollView(this)
        scroll.addView(outer)
        val builder = AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("Close", null)
        showAnimatedDialog(builder)
    }

    private fun attachDatePicker(editText: EditText) {
        editText.isFocusable = false
        editText.isClickable = true
        editText.setOnClickListener {
            val cal = Calendar.getInstance()
            try {
                val parts = editText.text.toString().split("-")
                if (parts.size == 3) cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            } catch (e: Exception) { }
            DatePickerDialog(this, { _, year, month, day ->
                editText.setText(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day))
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
    }

    // ---------- CHART ----------
    inner class ChartMarker(context: Context, layoutResource: Int, private val dates: List<String>) : MarkerView(context, layoutResource) {
        private val tv: TextView = findViewById(R.id.markerText)
        override fun refreshContent(e: Entry?, highlight: Highlight?) {
            val idx = (e?.x ?: 0f).toInt()
            val dateStr = dates.getOrNull(idx) ?: ""
            tv.text = dateStr + "\n" + pesoFormat.format(e?.y ?: 0f)
            super.refreshContent(e, highlight)
        }
        override fun getOffset(): MPPointF = MPPointF(-(width / 2).toFloat(), -height.toFloat() - 20f)
    }

    private fun buildNetWorthChart(entries: List<NetWorthEntry>, p: Palette, heightPx: Int, visibleWindow: Float): LineChart {
        val chart = LineChart(this)
        chart.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, heightPx)

        val dateFmt = SimpleDateFormat("MMM d", Locale.US)
        val dateLabels = entries.map { dateFmt.format(Date(it.timestamp)) }
        val lineEntries = entries.mapIndexed { i, e -> Entry(i.toFloat(), e.amount.toFloat()) }

        val set = LineDataSet(lineEntries, "Net worth")
        set.color = p.primary
        set.setDrawCircles(true)
        set.setCircleColor(p.primary)
        set.circleRadius = 3.5f
        set.lineWidth = 2.5f
        set.setDrawValues(false)
        set.mode = LineDataSet.Mode.CUBIC_BEZIER
        set.setDrawFilled(true)
        set.fillColor = p.primary
        set.fillAlpha = 40
        set.highLightColor = p.accent

        chart.data = LineData(set)
        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)
        chart.setVisibleXRangeMaximum(visibleWindow.coerceAtMost(entries.size.toFloat()))
        chart.marker = ChartMarker(this, R.layout.marker_view, dateLabels)
        chart.setBackgroundColor(Color.TRANSPARENT)
        chart.setNoDataText("")

        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f
        xAxis.textColor = p.text
        xAxis.textSize = 10f
        xAxis.setDrawGridLines(false)
        xAxis.labelRotationAngle = -35f
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = dateLabels.getOrNull(value.toInt()) ?: ""
        }

        val yAxisLeft = chart.axisLeft
        yAxisLeft.textColor = p.text
        yAxisLeft.textSize = 10f
        yAxisLeft.setDrawGridLines(true)
        yAxisLeft.gridColor = p.border
        yAxisLeft.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = "₱" + (value / 1000).let {
                if (it >= 1f || it <= -1f) String.format(Locale.US, "%.1fk", it) else value.toInt().toString()
            }
        }
        chart.axisRight.isEnabled = false

        chart.moveViewToX(lineEntries.size.toFloat())
        chart.invalidate()
        return chart
    }

    // ---------- HELPERS ----------
    private fun todayString(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun daysUntil(dateStr: String): Long {
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US); fmt.isLenient = false
            val target = fmt.parse(dateStr) ?: return 0L
            val now = Calendar.getInstance()
            now.set(Calendar.HOUR_OF_DAY, 0); now.set(Calendar.MINUTE, 0); now.set(Calendar.SECOND, 0); now.set(Calendar.MILLISECOND, 0)
            Math.ceil((target.time - now.timeInMillis) / 86400000.0).toLong()
        } catch (e: Exception) { 0L }
    }

    private fun netWorth(): Double = data.accounts.sumOf { it.balance }

    private fun logChange(note: String) {
        data.netWorthLog.add(NetWorthEntry(System.currentTimeMillis(), netWorth(), note))
    }

    private fun showTab(tab: String, direction: Int) {
        checkGoalCompletions()
        currentTab = tab
        contentContainer.removeAllViews()
        val p = palette()
        val view = when (tab) {
            "home" -> buildHome()
            "expenses" -> buildExpenses()
            "stats" -> buildStats()
            "goals" -> buildGoals()
            "calendar" -> buildCalendar()
            else -> buildSettings()
        }
        contentContainer.setBackgroundColor(p.bg)
        contentContainer.addView(view)
        navBar.setBackgroundColor(p.surface)
        highlightNav()

        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        view.alpha = 0f
        when (direction) {
            1 -> view.translationX = screenWidth * 0.25f
            -1 -> view.translationX = -screenWidth * 0.25f
            else -> view.translationY = 30f
        }
        view.animate().translationX(0f).translationY(0f).alpha(1f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun navItemList(): List<Pair<String, String>> {
        val items = mutableListOf("home" to "Home", "expenses" to "Expenses", "stats" to "Stats", "goals" to "Goals")
        if (data.settings.showPaylaterTab) items.add("calendar" to "Credit")
        items.add("settings" to "Settings")
        return items
    }

    private fun populateNavBar() {
        navBar.removeAllViews()
        navButtons.clear()
        for ((id, label) in navItemList()) {
            val tv = TextView(this)
            tv.text = label
            tv.textSize = 11.5f
            tv.typeface = headFont
            tv.gravity = Gravity.CENTER
            tv.maxLines = 1
            tv.setPadding(2, 30, 2, 30)
            tv.isClickable = true; tv.isFocusable = true
            tv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            tv.setOnClickListener { showTab(id, 0) }
            addPressAnim(tv)
            navButtons[id] = tv
            navBar.addView(tv)
        }
    }

    private fun highlightNav() {
        val p = palette()
        for ((id, view) in navButtons) {
            view.setTextColor(if (id == currentTab) p.primary else p.text)
            view.alpha = if (id == currentTab) 1f else 0.55f
        }
    }

    // ---------- QUICK ADD EXPENSE (popup) ----------
    private fun showAddExpenseDialog() {
        if (data.accounts.isEmpty()) { Toast.makeText(this, "Add an account first, in Settings", Toast.LENGTH_SHORT).show(); return }
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(48, 30, 48, 10)

        val amountInput = EditText(this); amountInput.hint = "Amount"; amountInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        layout.addView(amountInput)

        val catLabel = TextView(this); catLabel.text = "Category"; catLabel.setPadding(0, 20, 0, 8)
        layout.addView(catLabel)

        val catScroll = HorizontalScrollView(this)
        val catRow = LinearLayout(this); catRow.orientation = LinearLayout.HORIZONTAL
        catScroll.addView(catRow)
        layout.addView(catScroll)

        var selectedCategoryId = data.categories.firstOrNull()?.id ?: ""
        val chipViews = mutableMapOf<String, TextView>()
        fun styleChip(view: TextView, cat: Category, selected: Boolean) {
            view.background = roundedBg(if (selected) Color.parseColor(cat.colorHex) else Color.parseColor("#E1E4E0"), 60f)
            view.setTextColor(if (selected) contrastColor(Color.parseColor(cat.colorHex)) else Color.DKGRAY)
        }
        for (cat in data.categories) {
            val chip = TextView(this)
            chip.text = "${cat.emoji} ${cat.name}"; chip.textSize = 15f; chip.setPadding(32, 22, 32, 22)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.marginEnd = 12
            chip.layoutParams = lp
            styleChip(chip, cat, cat.id == selectedCategoryId)
            addPressAnim(chip)
            chip.setOnClickListener {
                selectedCategoryId = cat.id
                for (c in data.categories) chipViews[c.id]?.let { v -> styleChip(v, c, c.id == selectedCategoryId) }
            }
            chipViews[cat.id] = chip
            catRow.addView(chip)
        }

        val accLabel = TextView(this); accLabel.text = "From account"; accLabel.setPadding(0, 20, 0, 8)
        layout.addView(accLabel)
        val accountSpinner = Spinner(this)
        accountSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, data.accounts.map { it.name })
        layout.addView(accountSpinner)

        val noteInput = EditText(this); noteInput.hint = "Note (optional)"
        val noteLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); noteLp.topMargin = 16
        noteInput.layoutParams = noteLp
        layout.addView(noteInput)

        val scrollWrap = ScrollView(this); scrollWrap.addView(layout)

        val builder = AlertDialog.Builder(this)
            .setTitle("Add expense")
            .setView(scrollWrap)
            .setPositiveButton("Save") { _, _ ->
                val amt = amountInput.text.toString().toDoubleOrNull()
                if (amt == null || amt <= 0) { Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val accIndex = accountSpinner.selectedItemPosition
                if (accIndex < 0 || accIndex >= data.accounts.size) { Toast.makeText(this, "Pick an account", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val account = data.accounts[accIndex]
                val expense = Expense(Store.newId(), todayString(), selectedCategoryId, amt, noteInput.text.toString(), account.id)
                account.balance -= amt
                data.expenses.add(0, expense)
                logChange("Expense: " + (noteInput.text.toString().ifBlank { selectedCategoryId }))
                persist()
                Toast.makeText(this, "Expense added", Toast.LENGTH_SHORT).show()
                showTab(currentTab, 0)
            }
            .setNegativeButton("Cancel", null)
        showAnimatedDialog(builder)
    }

    // ---------- ADD FUNDS (deposit/withdraw) ----------
    private fun showAddFundsDialog(account: Account) {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(40, 30, 40, 10)

        val label = TextView(this); label.text = "Adjust ${account.name}"; label.textSize = 15f
        layout.addView(label)

        val amountInput = EditText(this); amountInput.hint = "Amount"
        amountInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val amtLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); amtLp.topMargin = 12
        amountInput.layoutParams = amtLp
        layout.addView(amountInput)

        var isDeposit = true
        val toggleRow = LinearLayout(this); toggleRow.orientation = LinearLayout.HORIZONTAL
        val toggleLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); toggleLp.topMargin = 16
        toggleRow.layoutParams = toggleLp
        val depositBtn = styledButton("Deposit +", data.let { palette().good }, Color.WHITE)
        val withdrawBtn = styledButton("Withdraw -", palette().bad, palette().bad, outline = true)
        depositBtn.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = 8 }
        withdrawBtn.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        depositBtn.setOnClickListener {
            isDeposit = true
            depositBtn.background = roundedBg(palette().good, 24f); depositBtn.setTextColor(Color.WHITE)
            withdrawBtn.background = roundedBg(Color.TRANSPARENT, 24f, palette().bad); withdrawBtn.setTextColor(palette().bad)
        }
        withdrawBtn.setOnClickListener {
            isDeposit = false
            withdrawBtn.background = roundedBg(palette().bad, 24f); withdrawBtn.setTextColor(Color.WHITE)
            depositBtn.background = roundedBg(Color.TRANSPARENT, 24f, palette().good); depositBtn.setTextColor(palette().good)
        }
        toggleRow.addView(depositBtn); toggleRow.addView(withdrawBtn)
        layout.addView(toggleRow)

        val builder = AlertDialog.Builder(this)
            .setTitle("Add funds")
            .setView(layout)
            .setPositiveButton("Confirm") { _, _ ->
                val amt = amountInput.text.toString().toDoubleOrNull()
                if (amt == null || amt <= 0) { Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                account.balance += if (isDeposit) amt else -amt
                logChange((if (isDeposit) "+" else "-") + pesoFormat.format(amt) + " · ${account.name}")
                persist()
                showTab("settings", 0)
            }
            .setNegativeButton("Cancel", null)
        showAnimatedDialog(builder)
    }

    private fun showBalanceHistoryDialog() {
        showEnlargeDialog("Balance history") { container ->
            val entries = data.netWorthLog.asReversed().take(100)
            if (entries.isEmpty()) {
                container.addView(bodyText(palette(), "No history yet.", 14f, muted = true))
            } else {
                val p = palette()
                val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.US)
                for (e in entries) {
                    val r = row(); r.setPadding(0, 10, 0, 10)
                    val noteText = bodyText(p, e.note, 14f)
                    noteText.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    r.addView(noteText)
                    val amtText = TextView(this); amtText.text = pesoFormat.format(e.amount); amtText.setTextColor(p.text); amtText.typeface = headFont; amtText.textSize = 13f
                    r.addView(amtText)
                    container.addView(r)
                    val dateText = bodyText(p, fmt.format(Date(e.timestamp)), 11f, muted = true)
                    container.addView(dateText)
                }
            }
        }
    }

    // ---------- HOME ----------
    private fun buildHome(): View {
        val p = palette()
        val scroll = ScrollView(this)
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(40, 50, 40, 50)
        scroll.addView(page)

        page.addView(pageTitle(p, "Net worth"))

        val nwValue = TextView(this)
        nwValue.text = pesoFormat.format(netWorth())
        nwValue.setTextColor(p.text); nwValue.textSize = 36f; nwValue.typeface = bigFont
        page.addView(nwValue)

        val accHeader = TextView(this)
        accHeader.text = "Accounts"; accHeader.setTextColor(p.text); accHeader.typeface = headFont; accHeader.textSize = 16f
        accHeader.setPadding(0, 30, 0, 4)
        page.addView(accHeader)

        val tilesWrap = LinearLayout(this); tilesWrap.orientation = LinearLayout.VERTICAL
        for (chunk in data.accounts.chunked(2)) {
            val r = row(); r.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = 12 }
            for (acc in chunk) r.addView(accountTile(p, acc))
            if (chunk.size == 1) r.addView(accountTilePlaceholder())
            tilesWrap.addView(r)
        }
        page.addView(tilesWrap)

        if (data.goals.isNotEmpty()) {
            val goalsHeader = TextView(this)
            goalsHeader.text = "Goals"; goalsHeader.setTextColor(p.text); goalsHeader.typeface = headFont; goalsHeader.textSize = 16f
            goalsHeader.setPadding(0, 26, 0, 4)
            page.addView(goalsHeader)
            val goalTilesWrap = LinearLayout(this); goalTilesWrap.orientation = LinearLayout.VERTICAL
            for (chunk in data.goals.chunked(2)) {
                val r = row(); r.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = 12 }
                for (g in chunk) r.addView(goalTile(p, g))
                if (chunk.size == 1) r.addView(accountTilePlaceholder())
                goalTilesWrap.addView(r)
            }
            page.addView(goalTilesWrap)
        }

        val monthCard = card(p)
        val monthLabel = TextView(this)
        monthLabel.text = "This month's spending"; monthLabel.setTextColor(p.text); monthLabel.typeface = headFont; monthLabel.textSize = 16f
        monthCard.addView(monthLabel)

        val monthPrefix = todayString().substring(0, 7)
        val monthExpenses = data.expenses.filter { it.date.length >= 7 && it.date.substring(0, 7) == monthPrefix }
        val total = monthExpenses.sumOf { it.amount }
        val byCat = HashMap<String, Double>()
        for (e in monthExpenses) byCat[e.categoryId] = (byCat[e.categoryId] ?: 0.0) + e.amount

        val totalText = TextView(this)
        totalText.text = "Total: " + pesoFormat.format(total); totalText.setTextColor(p.accent); totalText.typeface = headFont; totalText.textSize = 15f
        totalText.setPadding(0, 8, 0, 8)
        monthCard.addView(totalText)
        renderCategoryBreakdown(monthCard, p, byCat, total, big = false)
        page.addView(monthCard)

        val addBtn = styledButton("Add Expense", p.primary, p.onPrimary)
        addBtn.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = 30 }
        addBtn.setOnClickListener { showAddExpenseDialog() }
        page.addView(addBtn)

        return scroll
    }

    // ---------- EXPENSES ----------
    private fun buildExpenses(): View {
        val p = palette()
        val scroll = ScrollView(this)
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(40, 50, 40, 50)
        scroll.addView(page)

        page.addView(pageTitle(p, "Add expense"))
        val formCard = card(p)

        val amountInput = styledEditText(p, "Amount")
        amountInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        formCard.addView(amountInput)

        val catLabel = bodyText(p, "Category (swipe for more →)", 14f, muted = true)
        catLabel.setPadding(0, 20, 0, 8)
        formCard.addView(catLabel)

        val catScroll = HorizontalScrollView(this)
        catScroll.setPadding(0, 6, 0, 6)
        catScroll.isHorizontalScrollBarEnabled = true
        catScroll.isScrollbarFadingEnabled = false
        exemptFromSwipe(catScroll)
        val catRow = LinearLayout(this); catRow.orientation = LinearLayout.HORIZONTAL
        catScroll.addView(catRow)
        formCard.addView(catScroll)

        var selectedCategoryId = data.categories.firstOrNull()?.id ?: ""
        val chipViews = mutableMapOf<String, TextView>()
        fun styleChip(view: TextView, cat: Category, selected: Boolean) {
            view.background = roundedBg(if (selected) Color.parseColor(cat.colorHex) else p.bg, 60f, if (selected) null else p.border)
            view.setTextColor(if (selected) contrastColor(Color.parseColor(cat.colorHex)) else p.text)
        }
        for (cat in data.categories) {
            val chip = TextView(this)
            chip.text = "${cat.emoji} ${cat.name}"; chip.textSize = 15f; chip.typeface = bodyFont; chip.setPadding(32, 22, 32, 22)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.marginEnd = 12
            chip.layoutParams = lp
            styleChip(chip, cat, cat.id == selectedCategoryId)
            addPressAnim(chip)
            chip.setOnClickListener {
                selectedCategoryId = cat.id
                for (c in data.categories) chipViews[c.id]?.let { v -> styleChip(v, c, c.id == selectedCategoryId) }
            }
            chipViews[cat.id] = chip
            catRow.addView(chip)
        }

        val accLabel = bodyText(p, "From account", 14f, muted = true)
        accLabel.setPadding(0, 20, 0, 8)
        formCard.addView(accLabel)
        val accountSpinner = Spinner(this)
        accountSpinner.adapter = makeSpinnerAdapter(data.accounts.map { it.name }, p.text)
        formCard.addView(accountSpinner)

        val noteInput = styledEditText(p, "Note (optional)")
        formCard.addView(noteInput)

        val addBtn = styledButton("Save expense", p.accent, contrastColor(p.accent))
        addBtn.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = 20 }
        formCard.addView(addBtn)
        page.addView(formCard)

        addBtn.setOnClickListener {
            val amt = amountInput.text.toString().toDoubleOrNull()
            if (amt == null || amt <= 0) { Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (data.accounts.isEmpty()) { Toast.makeText(this, "Add an account first, in Settings", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            val accIndex = accountSpinner.selectedItemPosition
            if (accIndex < 0 || accIndex >= data.accounts.size) { Toast.makeText(this, "Pick an account", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            val account = data.accounts[accIndex]
            val expense = Expense(Store.newId(), todayString(), selectedCategoryId, amt, noteInput.text.toString(), account.id)
            account.balance -= amt
            data.expenses.add(0, expense)
            logChange("Expense: " + (noteInput.text.toString().ifBlank { selectedCategoryId }))
            persist()
            Toast.makeText(this, "Expense added", Toast.LENGTH_SHORT).show()
            showTab("expenses", 0)
        }

        page.addView(sectionTitle(p, "Manage categories"))
        val catCard = card(p)
        for (cat in data.categories) {
            val r = row(); r.gravity = Gravity.CENTER_VERTICAL; r.setPadding(0, 10, 0, 10)
            val label = bodyText(p, "${cat.emoji} ${cat.name}")
            label.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            r.addView(label)
            val delBtn = styledButton("Delete", p.bad, p.bad, outline = true)
            delBtn.setOnClickListener { data.categories.removeAll { c -> c.id == cat.id }; persist(); showTab("expenses", 0) }
            r.addView(delBtn)
            catCard.addView(r)
        }
        val newCatName = styledEditText(p, "New category name")
        catCard.addView(newCatName)
        val addCatBtn = styledButton("Add category", p.primary, p.onPrimary)
        addCatBtn.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = 16 }
        addCatBtn.setOnClickListener {
            val name = newCatName.text.toString().trim()
            if (name.isEmpty()) { Toast.makeText(this, "Name it first", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            val colors = listOf("#FFB100", "#1B3A4B", "#2E8B57", "#E4572E", "#7A5195", "#0089BA")
            data.categories.add(Category(Store.newId(), name, "🏷️", colors.random()))
            persist(); showTab("expenses", 0)
        }
        catCard.addView(addCatBtn)
        page.addView(catCard)

        page.addView(sectionTitle(p, "Recent"))
        if (data.expenses.isEmpty()) page.addView(bodyText(p, "Nothing yet — add your first expense above.", 14f, muted = true))
        for (exp in data.expenses.take(40)) {
            val cat = data.categories.find { it.id == exp.categoryId }
            val accName = data.accounts.find { it.id == exp.accountId }?.name ?: "Deleted account"
            val r = card(p); r.orientation = LinearLayout.HORIZONTAL; r.gravity = Gravity.CENTER_VERTICAL
            val noteOrCat = if (exp.note.isNotBlank()) exp.note else (cat?.name ?: "Expense")
            val info = bodyText(p, "$noteOrCat\n${exp.date} · $accName · -" + pesoFormat.format(exp.amount))
            info.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            r.addView(info)
            val delBtn = styledButton("Delete", p.bad, p.bad, outline = true)
            delBtn.setOnClickListener { data.expenses.removeAll { e -> e.id == exp.id }; persist(); showTab("expenses", 0) }
            r.addView(delBtn)
            page.addView(r)
        }

        return scroll
    }

    // ---------- STATS ----------
    private fun buildStats(): View {
        val p = palette()
        val scroll = ScrollView(this)
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(40, 50, 40, 50)
        scroll.addView(page)

        page.addView(pageTitle(p, "Stats & graphs"))

        val monthPrefix = todayString().substring(0, 7)
        val monthExpenses = data.expenses.filter { it.date.length >= 7 && it.date.substring(0, 7) == monthPrefix }
        val byCat = HashMap<String, Double>()
        for (e in monthExpenses) byCat[e.categoryId] = (byCat[e.categoryId] ?: 0.0) + e.amount
        val monthTotal = monthExpenses.sumOf { it.amount }

        val catCard = tappableCard(p) {
            showEnlargeDialog("Spending by category") { container -> renderCategoryBreakdown(container, p, byCat, monthTotal, big = true) }
        }
        catCard.addView(sectionTitle(p, "Spending by category (this month)", 0))
        renderCategoryBreakdown(catCard, p, byCat, monthTotal, big = false)
        catCard.addView(bodyText(p, "Tap for full breakdown", 11.5f, muted = true).also { it.setPadding(0, 12, 0, 0) })
        page.addView(catCard)

        val cutoff = System.currentTimeMillis() - 90L * 86400000L
        val recentLog = data.netWorthLog.filter { it.timestamp >= cutoff }
        val nwCard = card(p)
        nwCard.addView(sectionTitle(p, "Net worth trend (last 3 months)", 0))
        if (recentLog.size < 2) {
            nwCard.addView(bodyText(p, "Add or spend money a few times to see your trend.", 14f, muted = true))
        } else {
            nwCard.addView(buildNetWorthChart(recentLog, p, 500, 14f))
            val hint = bodyText(p, "Tap a point for its value · pinch or drag to explore", 11.5f, muted = true)
            hint.setPadding(0, 10, 0, 0)
            nwCard.addView(hint)
        }
        page.addView(nwCard)

        val advCard = card(p)
        advCard.addView(sectionTitle(p, "Advanced stats", 0))
        val totalAllTime = data.expenses.sumOf { it.amount }
        val avgPerExpense = if (data.expenses.isNotEmpty()) totalAllTime / data.expenses.size else 0.0
        val daysTracked = if (data.netWorthLog.isNotEmpty()) {
            val d = (System.currentTimeMillis() - data.netWorthLog.first().timestamp) / 86400000.0
            if (d < 1.0) 1.0 else d
        } else 1.0
        val avgDailySpend = totalAllTime / daysTracked
        addStatRow(advCard, p, "Total tracked spend", pesoFormat.format(totalAllTime))
        addStatRow(advCard, p, "Average per expense", pesoFormat.format(avgPerExpense))
        addStatRow(advCard, p, "Average daily spend", pesoFormat.format(avgDailySpend))
        addStatRow(advCard, p, "Number of expenses logged", data.expenses.size.toString())
        addStatRow(advCard, p, "Current net worth", pesoFormat.format(netWorth()))
        page.addView(advCard)

        return scroll
    }

    private fun addStatRow(page: LinearLayout, p: Palette, label: String, value: String) {
        val r = row(); r.setPadding(0, 10, 0, 10)
        val l = bodyText(p, label, 14f, muted = true)
        l.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        r.addView(l)
        val v = TextView(this); v.text = value; v.setTextColor(p.text); v.typeface = headFont; v.textSize = 15f
        r.addView(v)
        page.addView(r)
    }

    // ---------- GOALS ----------
    private fun buildGoals(): View {
        val p = palette()
        val scroll = ScrollView(this)
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(40, 50, 40, 50)
        scroll.addView(page)

        page.addView(pageTitle(p, "Goals"))
        val formCard = card(p)

        val labelInput = styledEditText(p, "Goal name (e.g. Emergency fund)")
        formCard.addView(labelInput)
        val targetInput = styledEditText(p, "Target amount")
        targetInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        formCard.addView(targetInput)
        val dateInput = styledEditText(p, "Target date")
        dateInput.setText(todayString())
        attachDatePicker(dateInput)
        formCard.addView(dateInput)

        val addBtn = styledButton("Add goal", p.primary, p.onPrimary)
        addBtn.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = 16 }
        formCard.addView(addBtn)
        page.addView(formCard)

        addBtn.setOnClickListener {
            val label = labelInput.text.toString().trim()
            val target = targetInput.text.toString().toDoubleOrNull()
            val dateStr = dateInput.text.toString().trim()
            if (label.isEmpty() || target == null) { Toast.makeText(this, "Fill in a goal name and target amount", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            data.goals.add(Goal(Store.newId(), label, target, dateStr, netWorth(), todayString()))
            persist()
            Toast.makeText(this, "Goal added — it now has its own tile on Home", Toast.LENGTH_SHORT).show()
            showTab("goals", 0)
        }

        val avgDailySavings: Double = run {
            val log = data.netWorthLog
            if (log.size < 2) 0.0 else {
                val first = log.first(); val last = log.last()
                val days = (last.timestamp - first.timestamp) / 86400000.0
                if (days < 1.0) 0.0 else (last.amount - first.amount) / days
            }
        }

        if (data.goals.isEmpty()) {
            val empty = bodyText(p, "No goals yet — set one above, like ₱10,000 by end of month. Reaching it removes the goal and its tile automatically.", 14f, muted = true)
            empty.setPadding(0, 30, 0, 0)
            page.addView(empty)
        }

        for (goal in data.goals) {
            val gcard = card(p)
            val nameRow = row()
            val nameText = TextView(this)
            nameText.text = "${goal.label}\nTarget: " + pesoFormat.format(goal.targetAmount) + " by ${goal.targetDate}"
            nameText.setTextColor(p.text); nameText.typeface = headFont; nameText.textSize = 15f
            nameText.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            nameRow.addView(nameText)
            val delBtn = styledButton("Delete", p.bad, p.bad, outline = true)
            delBtn.setOnClickListener { data.goals.removeAll { g -> g.id == goal.id }; persist(); showTab("goals", 0) }
            nameRow.addView(delBtn)
            gcard.addView(nameRow)

            val remaining = goal.targetAmount - netWorth()
            val totalNeeded = goal.targetAmount - goal.startAmount
            val progressPct = if (totalNeeded != 0.0) (((netWorth() - goal.startAmount) / totalNeeded) * 100).toInt().coerceIn(0, 100) else 0
            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
            bar.max = 100; bar.progress = progressPct
            val barLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); barLp.topMargin = 16
            bar.layoutParams = barLp
            gcard.addView(bar)

            gcard.addView(bodyText(p, "$progressPct% there · " + pesoFormat.format(if (remaining > 0) remaining else 0.0) + " left", 13f, muted = true))

            val daysLeft = daysUntil(goal.targetDate).let { if (it < 1) 1 else it }
            val neededPerDay = if (remaining > 0) remaining / daysLeft else 0.0
            val perDayText = bodyText(p, if (remaining > 0) "Save " + pesoFormat.format(neededPerDay) + "/day to hit your date" else "Goal reached! 🎉", 15f)
            perDayText.setPadding(0, 16, 0, 0)
            gcard.addView(perDayText)

            gcard.addView(bodyText(p, when {
                remaining <= 0 -> "Done"
                avgDailySavings > 0 -> "At your current pace: ~${Math.ceil(remaining / avgDailySavings).toInt()} days"
                else -> "At your current pace: not enough history yet"
            }, 13f, muted = true))

            page.addView(gcard)
        }

        return scroll
    }

    // ---------- CREDIT (formerly PayLater) ----------
    private fun buildCalendar(): View {
        val p = palette()
        val scroll = ScrollView(this)
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(40, 50, 40, 50)
        scroll.addView(page)

        page.addView(pageTitle(p, "Credit Payment Calendar"))
        val formCard = card(p)

        val nameInput = styledEditText(p, "e.g. Shopee PayLater")
        formCard.addView(nameInput)
        val amountInput = styledEditText(p, "Amount")
        amountInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        formCard.addView(amountInput)
        val dueInput = styledEditText(p, "Due date")
        dueInput.setText(todayString())
        attachDatePicker(dueInput)
        formCard.addView(dueInput)

        val accLabel = bodyText(p, "Pay from", 14f, muted = true)
        accLabel.setPadding(0, 20, 0, 8)
        formCard.addView(accLabel)
        val accSpinner = Spinner(this)
        accSpinner.adapter = makeSpinnerAdapter(data.accounts.map { it.name }, p.text)
        formCard.addView(accSpinner)

        val addBtn = styledButton("Add due date", p.primary, p.onPrimary)
        addBtn.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = 16 }
        formCard.addView(addBtn)
        page.addView(formCard)

        addBtn.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val amt = amountInput.text.toString().toDoubleOrNull()
            val due = dueInput.text.toString().trim()
            if (name.isEmpty() || amt == null) { Toast.makeText(this, "Fill in name and amount", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (data.accounts.isEmpty()) { Toast.makeText(this, "Add an account first", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            val accIndex = accSpinner.selectedItemPosition.coerceIn(0, data.accounts.size - 1)
            val accountId = data.accounts[accIndex].id
            data.paylaters.add(PayLaterItem(Store.newId(), name, amt, due, accountId, false))
            persist()
            Toast.makeText(this, "Added", Toast.LENGTH_SHORT).show()
            showTab("calendar", 0)
        }

        page.addView(sectionTitle(p, "Upcoming"))
        val sorted = data.paylaters.sortedBy { it.dueDate }
        val upcoming = sorted.filter { !it.paid }
        if (upcoming.isEmpty()) page.addView(bodyText(p, "Nothing due — you're all clear.", 14f, muted = true))

        for (item in upcoming) {
            val daysLeft = daysUntil(item.dueDate)
            val soon = daysLeft <= data.settings.notifDaysBefore
            val accName = data.accounts.find { it.id == item.accountId }?.name ?: "Deleted account"
            val itemCard = card(p)
            if (soon) itemCard.background = roundedBg(p.bad, 24f).also { it.alpha = 40 }
            val r = row(); r.gravity = Gravity.CENTER_VERTICAL
            val info = bodyText(p, "${item.name}\nDue ${item.dueDate} · $accName · ${daysLeft}d left\n" + pesoFormat.format(item.amount))
            info.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            r.addView(info)
            val btnCol = LinearLayout(this); btnCol.orientation = LinearLayout.VERTICAL
            val payBtn = styledButton("Paid", p.good, Color.WHITE)
            payBtn.setOnClickListener { item.paid = true; persist(); showTab("calendar", 0) }
            btnCol.addView(payBtn)
            val delBtn = styledButton("Delete", p.bad, p.bad, outline = true)
            delBtn.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = 8 }
            delBtn.setOnClickListener { data.paylaters.removeAll { pl -> pl.id == item.id }; persist(); showTab("calendar", 0) }
            btnCol.addView(delBtn)
            r.addView(btnCol)
            itemCard.addView(r)
            page.addView(itemCard)
        }

        val paidItems = sorted.filter { it.paid }
        if (paidItems.isNotEmpty()) {
            page.addView(sectionTitle(p, "Paid"))
            for (item in paidItems) {
                val r2 = bodyText(p, "${item.name} — " + pesoFormat.format(item.amount), 14f)
                r2.alpha = 0.5f
                page.addView(r2)
            }
        }

        return scroll
    }

    // ---------- SETTINGS ----------
    private fun buildSettings(): View {
        val p = palette()
        val scroll = ScrollView(this)
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(40, 50, 40, 50)
        scroll.addView(page)

        page.addView(pageTitle(p, "Settings"))

        val themeCard = card(p)
        themeCard.addView(sectionTitle(p, "Theme", 0))
        val themeRow = row()
        val themes = listOf("light" to "Light", "dark" to "Dark", "sunset" to "Sunset", "ocean" to "Ocean")
        for ((id, label) in themes) {
            val active = data.settings.theme == id
            val btn = styledButton(label, if (active) p.primary else p.text, if (active) p.onPrimary else p.text, outline = !active)
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); lp.marginEnd = 8
            btn.layoutParams = lp; btn.textSize = 12f; btn.setPadding(8, 22, 8, 22)
            btn.setOnClickListener { data.settings.theme = id; persist(); showTab("settings", 0) }
            themeRow.addView(btn)
        }
        themeCard.addView(themeRow)

        val accentLabel = bodyText(p, "Custom accent color", 14f, muted = true)
        accentLabel.setPadding(0, 24, 0, 8)
        themeCard.addView(accentLabel)
        val swatchRow = row()
        for (hex in listOf("#FFB100", "#E4572E", "#2E8B57", "#0369A1", "#7A5195", "#DB2777")) {
            val dot = View(this)
            val dotLp = LinearLayout.LayoutParams(76, 76); dotLp.marginEnd = 12
            dot.layoutParams = dotLp
            dot.background = roundedBg(Color.parseColor(hex), 40f, if (data.settings.customAccent == hex) p.text else null)
            addPressAnim(dot)
            dot.setOnClickListener { data.settings.customAccent = hex; persist(); showTab("settings", 0) }
            swatchRow.addView(dot)
        }
        themeCard.addView(swatchRow)
        val hexInput = styledEditText(p, "Or type a hex code, e.g. #22AA88")
        data.settings.customAccent?.let { hexInput.setText(it) }
        themeCard.addView(hexInput)
        val accentBtnRow = row()
        val applyBtn = styledButton("Apply", p.primary, p.onPrimary)
        applyBtn.setOnClickListener {
            val hex = hexInput.text.toString().trim()
            try { Color.parseColor(hex); data.settings.customAccent = hex; persist(); showTab("settings", 0) }
            catch (e: Exception) { Toast.makeText(this, "That's not a valid color code", Toast.LENGTH_SHORT).show() }
        }
        accentBtnRow.addView(applyBtn)
        val clearBtn = styledButton("Reset", p.text, p.text, outline = true)
        clearBtn.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.marginStart = 10 }
        clearBtn.setOnClickListener { data.settings.customAccent = null; persist(); showTab("settings", 0) }
        accentBtnRow.addView(clearBtn)
        accentBtnRow.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = 12 }
        themeCard.addView(accentBtnRow)
        page.addView(themeCard)

        val tabsCard = card(p)
        tabsCard.addView(sectionTitle(p, "Tabs", 0))
        val toggleBtn = styledButton(if (data.settings.showPaylaterTab) "Hide Credit tab" else "Show Credit tab", p.primary, p.primary, outline = true)
        toggleBtn.setOnClickListener {
            data.settings.showPaylaterTab = !data.settings.showPaylaterTab
            persist(); populateNavBar()
            if (currentTab == "calendar" && !data.settings.showPaylaterTab) showTab("home", 0) else showTab(currentTab, 0)
        }
        tabsCard.addView(toggleBtn)
        page.addView(tabsCard)

        val accCard = card(p)
        accCard.addView(sectionTitle(p, "Accounts (net worth: " + pesoFormat.format(netWorth()) + ")", 0))
        for (acc in data.accounts) {
            val r = row(); r.gravity = Gravity.CENTER_VERTICAL; r.setPadding(0, 12, 0, 12)
            val info = bodyText(p, "${acc.name}\n" + pesoFormat.format(acc.balance))
            info.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            r.addView(info)
            val fundsBtn = styledButton("+/-", p.good, p.good, outline = true)
            fundsBtn.setOnClickListener { showAddFundsDialog(acc) }
            r.addView(fundsBtn)
            val editBtn = styledButton("Edit", p.primary, p.primary, outline = true)
            editBtn.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.marginStart = 6 }
            editBtn.setOnClickListener { showEditAccountDialog(acc) }
            r.addView(editBtn)
            val delBtn = styledButton("Delete", p.bad, p.bad, outline = true)
            delBtn.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.marginStart = 6 }
            delBtn.setOnClickListener {
                data.accounts.removeAll { a -> a.id == acc.id }
                logChange("Deleted account: ${acc.name}")
                persist(); showTab("settings", 0)
            }
            r.addView(delBtn)
            accCard.addView(r)
        }
        val newAccName = styledEditText(p, "New account name (e.g. BPI, Savings jar)")
        accCard.addView(newAccName)
        val newAccBalance = styledEditText(p, "Starting balance")
        newAccBalance.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        accCard.addView(newAccBalance)
        val addAccBtn = styledButton("Add account", p.primary, p.onPrimary)
        addAccBtn.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = 16 }
        addAccBtn.setOnClickListener {
            val name = newAccName.text.toString().trim()
            if (name.isEmpty()) { Toast.makeText(this, "Give the account a name", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            val balance = newAccBalance.text.toString().toDoubleOrNull() ?: 0.0
            data.accounts.add(Account(Store.newId(), name, balance))
            logChange("Added account: $name")
            persist(); showTab("settings", 0)
        }
        accCard.addView(addAccBtn)
        page.addView(accCard)

        val historyCard = card(p)
        historyCard.addView(sectionTitle(p, "History", 0))
        val historyBtn = styledButton("View balance history", p.primary, p.primary, outline = true)
        historyBtn.setOnClickListener { showBalanceHistoryDialog() }
        historyCard.addView(historyBtn)
        page.addView(historyCard)

        val notifCard = card(p)
        notifCard.addView(sectionTitle(p, "Notifications", 0))
        val notifDaysInput = styledEditText(p, "Remind me this many days before due date")
        notifDaysInput.setText(data.settings.notifDaysBefore.toString())
        notifDaysInput.inputType = InputType.TYPE_CLASS_NUMBER
        notifCard.addView(notifDaysInput)
        val saveNotifBtn = styledButton("Save", p.primary, p.primary, outline = true)
        saveNotifBtn.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = 12 }
        saveNotifBtn.setOnClickListener {
            data.settings.notifDaysBefore = notifDaysInput.text.toString().toIntOrNull() ?: 2
            persist()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }
        notifCard.addView(saveNotifBtn)
        val enableNotifBtn = styledButton("Enable notifications", p.accent, contrastColor(p.accent))
        enableNotifBtn.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = 12 }
        enableNotifBtn.setOnClickListener { requestNotificationPermissionAndShow() }
        notifCard.addView(enableNotifBtn)
        page.addView(notifCard)

        val dataCard = card(p)
        dataCard.addView(sectionTitle(p, "Data", 0))
        val resetBtn = styledButton("Reset all data", p.bad, p.bad, outline = true)
        resetBtn.setOnClickListener {
            val builder = AlertDialog.Builder(this)
                .setTitle("Reset all data?")
                .setMessage("This can't be undone.")
                .setPositiveButton("Reset") { _, _ -> data = Store.defaultData(); persist(); populateNavBar(); showTab("home", 0) }
                .setNegativeButton("Cancel", null)
            showAnimatedDialog(builder)
        }
        dataCard.addView(resetBtn)
        page.addView(dataCard)

        return scroll
    }

    private fun showEditAccountDialog(account: Account) {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(40, 30, 40, 10)
        val nameInput = EditText(this); nameInput.setText(account.name); layout.addView(nameInput)
        val balanceInput = EditText(this)
        balanceInput.setText(account.balance.toString())
        balanceInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        layout.addView(balanceInput)
        val builder = AlertDialog.Builder(this)
            .setTitle("Edit account")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                account.name = nameInput.text.toString().trim().ifEmpty { account.name }
                account.balance = balanceInput.text.toString().toDoubleOrNull() ?: account.balance
                logChange("Manual balance update: ${account.name}")
                persist(); showTab("settings", 0)
            }
            .setNegativeButton("Cancel", null)
        showAnimatedDialog(builder)
    }

    private fun requestNotificationPermissionAndShow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
                return
            }
        }
        showTestNotification()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) showTestNotification()
    }

    private fun showTestNotification() {
        val channelId = "pera_tracker_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Pera Tracker", NotificationManager.IMPORTANCE_DEFAULT)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Pera Tracker")
            .setContentText("You'll see reminders here for upcoming credit dues.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        NotificationManagerCompat.from(this).notify(1, builder.build())
    }
}

data class Palette(
    val bg: Int, val surface: Int, val text: Int, val muted: Int,
    val primary: Int, val accent: Int, val good: Int, val bad: Int, val border: Int,
    val onPrimary: Int
)

class SwipeContainer(context: Context) : FrameLayout(context) {
    var onSwipeLeft: (() -> Unit)? = null
    var onSwipeRight: (() -> Unit)? = null
    private var downX = 0f
    private var downY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var intercepting = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = ev.x; downY = ev.y; intercepting = false }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX; val dy = ev.y - downY
                if (!intercepting && abs(dx) > touchSlop && abs(dx) > abs(dy)) intercepting = true
            }
        }
        return intercepting
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val dx = event.x - downX
            if (abs(dx) > 120) { if (dx < 0) onSwipeLeft?.invoke() else onSwipeRight?.invoke() }
            intercepting = false
        }
        return true
    }
}
