package com.pera.tracker
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
class MainActivity : Activity() {
    private lateinit var data: AppData
    private lateinit var contentContainer: FrameLayout
    private lateinit var navBar: LinearLayout
    private var currentTab: String = "home"
    private val pesoFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "PH"))
    private val navButtons = mutableMapOf<String, TextView>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        data = Store.load(this)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        contentContainer = FrameLayout(this)
        contentContainer.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
        root.addView(contentContainer)
        navBar = buildBottomNav()
        root.addView(navBar)
        setContentView(root)
        showTab("home")
    }
    private fun persist() { Store.save(this, data) }
    // ---------- THEMES ----------
    private fun palette(): Palette {
        return when (data.settings.theme) {
            "dark" -> Palette(
                bg = Color.parseColor("#0D1B24"), surface = Color.parseColor("#132A38"),
                text = Color.WHITE, muted = Color.parseColor("#9FB2BC"),
                primary = Color.parseColor("#FFB100"), accent = Color.parseColor("#FFB100"),
                good = Color.parseColor("#4ADE80"), bad = Color.parseColor("#FB7185"),
                border = Color.parseColor("#233F4F"), onPrimary = Color.parseColor("#0D1B24")
            )
            "sunset" -> Palette(
                bg = Color.parseColor("#FFF7ED"), surface = Color.WHITE,
                text = Color.parseColor("#2D1B0E"), muted = Color.parseColor("#8B6F52"),
                primary = Color.parseColor("#C2410C"), accent = Color.parseColor("#F59E0B"),
                good = Color.parseColor("#16A34A"), bad = Color.parseColor("#DC2626"),
                border = Color.parseColor("#FED7AA"), onPrimary = Color.WHITE
            )
            "ocean" -> Palette(
                bg = Color.parseColor("#F0F9FF"), surface = Color.WHITE,
                text = Color.parseColor("#0C2340"), muted = Color.parseColor("#5B7B95"),
                primary = Color.parseColor("#0369A1"), accent = Color.parseColor("#06B6D4"),
                good = Color.parseColor("#059669"), bad = Color.parseColor("#DC2626"),
                border = Color.parseColor("#BAE6FD"), onPrimary = Color.WHITE
            )
            else -> Palette(
                bg = Color.parseColor("#F5F6F4"), surface = Color.WHITE,
                text = Color.parseColor("#12242F"), muted = Color.parseColor("#5B6B72"),
                primary = Color.parseColor("#1B3A4B"), accent = Color.parseColor("#FFB100"),
                good = Color.parseColor("#2E8B57"), bad = Color.parseColor("#E4572E"),
                border = Color.parseColor("#E1E4E0"), onPrimary = Color.WHITE
            )
        }
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
        b.setTextColor(textColor)
        b.setPadding(36, 24, 36, 24)
        b.isAllCaps = false
        b.background = if (outline) roundedBg(Color.TRANSPARENT, 24f, bgColor) else roundedBg(bgColor, 24f)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        b.layoutParams = lp
        b.stateListAnimator = null
        return b
    }
    private fun styledEditText(p: Palette, hint: String): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setHintTextColor(p.muted)
        e.setTextColor(p.text)
        e.setPadding(28, 24, 28, 24)
        e.background = roundedBg(p.surface, 16f, p.border)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = 12
        e.layoutParams = lp
        return e
    }
    private fun sectionTitle(p: Palette, text: String, topMargin: Int = 40): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 17f
        t.setTypeface(null, Typeface.BOLD)
        t.setTextColor(p.text)
        t.setPadding(0, topMargin, 0, 12)
        return t
    }
    private fun card(p: Palette): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(28, 28, 28, 28)
        c.background = roundedBg(p.surface, 24f, p.border)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = 16
        c.layoutParams = lp
        return c
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
    // ---------- HELPERS ----------
    private fun todayString(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private fun daysUntil(dateStr: String): Long {
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            fmt.isLenient = false
            val target = fmt.parse(dateStr) ?: return 0L
            val now = Calendar.getInstance()
            now.set(Calendar.HOUR_OF_DAY, 0); now.set(Calendar.MINUTE, 0)
            now.set(Calendar.SECOND, 0); now.set(Calendar.MILLISECOND, 0)
            val diff = target.time - now.timeInMillis
            Math.ceil(diff / 86400000.0).toLong()
        } catch (e: Exception) { 0L }
    }
    private fun netWorth(): Double = data.accounts.sumOf { it.balance }
    private fun showTab(tab: String) {
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
    }
    private fun buildBottomNav(): LinearLayout {
        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.setPadding(0, 16, 0, 16)
        val items = listOf(
            "home" to "Home", "expenses" to "Expenses", "stats" to "Stats",
            "goals" to "Goals", "calendar" to "PayLater", "settings" to "Settings"
        )
        for ((id, label) in items) {
            val tv = TextView(this)
            tv.text = label
            tv.textSize = 10f
            tv.gravity = Gravity.CENTER
            tv.setPadding(4, 8, 4, 8)
            tv.isClickable = true
            tv.isFocusable = true
            tv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            tv.setOnClickListener { showTab(id) }
            navButtons[id] = tv
            nav.addView(tv)
        }
        return nav
    }
    private fun highlightNav() {
        val p = palette()
        for ((id, view) in navButtons) {
            view.setTextColor(if (id == currentTab) p.primary else p.muted)
            view.setTypeface(null, if (id == currentTab) Typeface.BOLD else Typeface.NORMAL)
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
        val nwLabel = TextView(this)
        nwLabel.text = "Net worth"
        nwLabel.setTextColor(p.muted)
        nwLabel.textSize = 13f
        page.addView(nwLabel)
        val nwValue = TextView(this)
        nwValue.text = pesoFormat.format(netWorth())
        nwValue.setTextColor(p.text)
        nwValue.textSize = 32f
        nwValue.setTypeface(null, Typeface.BOLD)
        page.addView(nwValue)
        val accCard = card(p)
        val accLabel = TextView(this)
        accLabel.text = "Accounts"
        accLabel.setTextColor(p.text)
        accLabel.textSize = 15f
        accLabel.setTypeface(null, Typeface.BOLD)
        accCard.addView(accLabel)
        for (acc in data.accounts) {
            val row = TextView(this)
            row.text = "${acc.name}   " + pesoFormat.format(acc.balance)
            row.setTextColor(p.text)
            row.setPadding(0, 12, 0, 0)
            accCard.addView(row)
        }
        page.addView(accCard)
        val monthCard = card(p)
        val monthLabel = TextView(this)
        monthLabel.text = "This month's spending"
        monthLabel.setTextColor(p.text)
        monthLabel.textSize = 15f
        monthLabel.setTypeface(null, Typeface.BOLD)
        monthCard.addView(monthLabel)
        val monthPrefix = todayString().substring(0, 7)
        val monthExpenses = data.expenses.filter { it.date.length >= 7 && it.date.substring(0, 7) == monthPrefix }
        val total = monthExpenses.sumOf { it.amount }
        val byCat = HashMap<String, Double>()
        for (e in monthExpenses) byCat[e.categoryId] = (byCat[e.categoryId] ?: 0.0) + e.amount
        val totalText = TextView(this)
        totalText.text = "Total: " + pesoFormat.format(total)
        totalText.setTextColor(p.accent)
        totalText.setTypeface(null, Typeface.BOLD)
        totalText.setPadding(0, 8, 0, 8)
        monthCard.addView(totalText)
        if (byCat.isEmpty()) {
            val empty = TextView(this)
            empty.text = "No expenses logged yet this month."
            empty.setTextColor(p.muted)
            monthCard.addView(empty)
        } else {
            for ((catId, amt) in byCat.entries.sortedByDescending { it.value }) {
                val cat = data.categories.find { it.id == catId }
                val label = TextView(this)
                label.text = "${cat?.emoji ?: "📦"} ${cat?.name ?: catId}   " + pesoFormat.format(amt)
                label.setTextColor(p.text)
                label.setPadding(0, 10, 0, 0)
                monthCard.addView(label)
                val pct = if (total > 0) ((amt / total) * 100).toInt() else 0
                val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
                bar.max = 100
                bar.progress = pct
                monthCard.addView(bar)
            }
        }
        page.addView(monthCard)
        val addBtn = styledButton("+ Add an expense", p.primary, p.onPrimary)
        addBtn.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = 30 }
        addBtn.setOnClickListener { showTab("expenses") }
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
        page.addView(sectionTitle(p, "Add expense", 0))
        val formCard = card(p)
        val amountInput = styledEditText(p, "Amount")
        amountInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        formCard.addView(amountInput)
        val catLabel = TextView(this)
        catLabel.text = "Category"
        catLabel.setTextColor(p.muted)
        catLabel.setPadding(0, 20, 0, 8)
        formCard.addView(catLabel)
        val catScroll = HorizontalScrollView(this)
        val catRow = LinearLayout(this)
        catRow.orientation = LinearLayout.HORIZONTAL
        catScroll.addView(catRow)
        formCard.addView(catScroll)
        var selectedCategoryId = data.categories.firstOrNull()?.id ?: ""
        val chipViews = mutableMapOf<String, TextView>()
        fun styleChip(view: TextView, cat: Category, selected: Boolean) {
            view.background = roundedBg(if (selected) Color.parseColor(cat.colorHex) else p.bg, 60f, if (selected) null else p.border)
            view.setTextColor(if (selected) Color.WHITE else p.text)
        }
        for (cat in data.categories) {
            val chip = TextView(this)
            chip.text = "${cat.emoji} ${cat.name}"
            chip.textSize = 12f
            chip.setPadding(28, 16, 28, 16)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = 12
            chip.layoutParams = lp
            styleChip(chip, cat, cat.id == selectedCategoryId)
            chip.setOnClickListener {
                selectedCategoryId = cat.id
                for (c in data.categories) chipViews[c.id]?.let { v -> styleChip(v, c, c.id == selectedCategoryId) }
            }
            chipViews[cat.id] = chip
            catRow.addView(chip)
        }
        val accLabel = TextView(this)
        accLabel.text = "From account"
        accLabel.setTextColor(p.muted)
        accLabel.setPadding(0, 20, 0, 8)
        formCard.addView(accLabel)
        val accountSpinner = Spinner(this)
        val accountNames = data.accounts.map { it.name }
        val accAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, accountNames)
        accAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        accountSpinner.adapter = accAdapter
        formCard.addView(accountSpinner)
        val noteInput = styledEditText(p, "Note (optional)")
        formCard.addView(noteInput)
        val addBtn = styledButton("Save expense", p.accent, Color.parseColor("#12242F"))
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
            data.netWorthLog.add(NetWorthEntry(System.currentTimeMillis(), netWorth(), "Expense"))
            persist()
            Toast.makeText(this, "Expense added", Toast.LENGTH_SHORT).show()
            showTab("expenses")
        }
        page.addView(sectionTitle(p, "Manage categories"))
        val catCard = card(p)
        for (cat in data.categories) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, 10, 0, 10)
            val label = TextView(this)
            label.text = "${cat.emoji} ${cat.name}"
            label.setTextColor(p.text)
            label.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(label)
            val delBtn = styledButton("Delete", p.bad, Color.WHITE, outline = true)
            delBtn.setOnClickListener { data.categories.removeAll { c -> c.id == cat.id }; persist(); showTab("expenses") }
            row.addView(delBtn)
            catCard.addView(row)
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
            persist()
            showTab("expenses")
        }
        catCard.addView(addCatBtn)
        page.addView(catCard)
        page.addView(sectionTitle(p, "Recent"))
        if (data.expenses.isEmpty()) {
            val empty = TextView(this)
            empty.text = "Nothing yet — add your first expense above."
            empty.setTextColor(p.muted)
            page.addView(empty)
        }
        for (exp in data.expenses.take(40)) {
            val cat = data.categories.find { it.id == exp.categoryId }
            val accName = data.accounts.find { it.id == exp.accountId }?.name ?: "Deleted account"
            val row = card(p)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val info = TextView(this)
            val noteOrCat = if (exp.note.isNotBlank()) exp.note else (cat?.name ?: "Expense")
            info.text = "$noteOrCat\n${exp.date} · $accName · -" + pesoFormat.format(exp.amount)
            info.setTextColor(p.text)
            info.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(info)
            val delBtn = styledButton("Delete", p.bad, Color.WHITE, outline = true)
            delBtn.setOnClickListener { data.expenses.removeAll { e -> e.id == exp.id }; persist(); showTab("expenses") }
            row.addView(delBtn)
            page.addView(row)
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
        page.addView(sectionTitle(p, "Stats & graphs", 0))
        val monthPrefix = todayString().substring(0, 7)
        val monthExpenses = data.expenses.filter { it.date.length >= 7 && it.date.substring(0, 7) == monthPrefix }
        val byCat = HashMap<String, Double>()
        for (e in monthExpenses) byCat[e.categoryId] = (byCat[e.categoryId] ?: 0.0) + e.amount
        val monthTotal = monthExpenses.sumOf { it.amount }
        val catCard = card(p)
        catCard.addView(sectionTitle(p, "Spending by category (this month)", 0))
        if (byCat.isEmpty()) {
            val empty = TextView(this); empty.text = "No data yet."; empty.setTextColor(p.muted); catCard.addView(empty)
        } else {
            for ((catId, amt) in byCat.entries.sortedByDescending { it.value }) {
                val cat = data.categories.find { it.id == catId }
                val pct = if (monthTotal > 0) ((amt / monthTotal) * 100).toInt() else 0
                val label = TextView(this)
                label.text = "${cat?.emoji ?: "📦"} ${cat?.name ?: catId} — $pct%"
                label.setTextColor(p.text)
                label.setPadding(0, 10, 0, 0)
                catCard.addView(label)
                val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
                bar.max = 100; bar.progress = pct
                catCard.addView(bar)
            }
        }
        page.addView(catCard)
        val nwCard = card(p)
        nwCard.addView(sectionTitle(p, "Net worth trend", 0))
        val recentLog = data.netWorthLog.takeLast(30)
        if (recentLog.size < 2) {
            val empty = TextView(this); empty.text = "Add or spend money a few times to see your trend."; empty.setTextColor(p.muted); nwCard.addView(empty)
        } else {
            val sparkline = SimpleLineView(this)
            sparkline.values = recentLog.map { it.amount.toFloat() }
            sparkline.lineColor = p.primary
            sparkline.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 300)
            nwCard.addView(sparkline)
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
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, 10, 0, 10)
        val l = TextView(this); l.text = label; l.setTextColor(p.muted); l.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(l)
        val v = TextView(this); v.text = value; v.setTextColor(p.text); v.setTypeface(null, Typeface.BOLD)
        row.addView(v)
        page.addView(row)
    }
    // ---------- GOALS ----------
    private fun buildGoals(): View {
        val p = palette()
        val scroll = ScrollView(this)
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(40, 50, 40, 50)
        scroll.addView(page)
        page.addView(sectionTitle(p, "Goals", 0))
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
            Toast.makeText(this, "Goal added", Toast.LENGTH_SHORT).show()
            showTab("goals")
        }
        val avgDailySavings: Double = run {
            val log = data.netWorthLog
            if (log.size < 2) 0.0
            else {
                val first = log.first(); val last = log.last()
                val days = (last.timestamp - first.timestamp) / 86400000.0
                if (days < 1.0) 0.0 else (last.amount - first.amount) / days
            }
        }
        if (data.goals.isEmpty()) {
            val empty = TextView(this)
            empty.text = "No goals yet — set one above, like ₱10,000 by end of month."
            empty.setTextColor(p.muted)
            empty.setPadding(0, 30, 0, 0)
            page.addView(empty)
        }
        for (goal in data.goals) {
            val gcard = card(p)
            val nameRow = LinearLayout(this)
            nameRow.orientation = LinearLayout.HORIZONTAL
            val nameText = TextView(this)
            nameText.text = "${goal.label}\nTarget: " + pesoFormat.format(goal.targetAmount) + " by ${goal.targetDate}"
            nameText.setTextColor(p.text)
            nameText.setTypeface(null, Typeface.BOLD)
            nameText.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            nameRow.addView(nameText)
            val delBtn = styledButton("Delete", p.bad, Color.WHITE, outline = true)
            delBtn.setOnClickListener { data.goals.removeAll { g -> g.id == goal.id }; persist(); showTab("goals") }
            nameRow.addView(delBtn)
            gcard.addView(nameRow)
            val remaining = goal.targetAmount - netWorth()
            val totalNeeded = goal.targetAmount - goal.startAmount
            val progressPct = if (totalNeeded != 0.0) (((netWorth() - goal.startAmount) / totalNeeded) * 100).toInt().coerceIn(0, 100) else 0
            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
            bar.max = 100; bar.progress = progressPct
            val barLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            barLp.topMargin = 16
            bar.layoutParams = barLp
            gcard.addView(bar)
            val progressText = TextView(this)
            progressText.text = "$progressPct% there · " + pesoFormat.format(if (remaining > 0) remaining else 0.0) + " left"
            progressText.setTextColor(p.muted)
            progressText.textSize = 12f
            gcard.addView(progressText)
            val daysLeft = daysUntil(goal.targetDate).let { if (it < 1) 1 else it }
            val neededPerDay = if (remaining > 0) remaining / daysLeft else 0.0
            val perDayText = TextView(this)
            perDayText.text = if (remaining > 0) "Save " + pesoFormat.format(neededPerDay) + "/day to hit your date" else "Goal reached! 🎉"
            perDayText.setTextColor(p.text)
            perDayText.setPadding(0, 16, 0, 0)
            gcard.addView(perDayText)
            val paceText = TextView(this)
            paceText.text = when {
                remaining <= 0 -> "Done"
                avgDailySavings > 0 -> "At your current pace: ~${Math.ceil(remaining / avgDailySavings).toInt()} days"
                else -> "At your current pace: not enough history yet"
            }
            paceText.setTextColor(p.muted)
            paceText.textSize = 12f
            gcard.addView(paceText)
            page.addView(gcard)
        }
        return scroll
    }
    // ---------- PAYLATER ----------
    private fun buildCalendar(): View {
        val p = palette()
        val scroll = ScrollView(this)
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(40, 50, 40, 50)
        scroll.addView(page)
        page.addView(sectionTitle(p, "PayLater calendar", 0))
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
        val accLabel = TextView(this)
        accLabel.text = "Pay from"
        accLabel.setTextColor(p.muted)
        accLabel.setPadding(0, 20, 0, 8)
        formCard.addView(accLabel)
        val accSpinner = Spinner(this)
        val accNames = data.accounts.map { it.name }
        val accAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, accNames)
        accAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        accSpinner.adapter = accAdapter
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
            Toast.makeText(this, "PayLater added", Toast.LENGTH_SHORT).show()
            showTab("calendar")
        }
        page.addView(sectionTitle(p, "Upcoming"))
        val sorted = data.paylaters.sortedBy { it.dueDate }
        val upcoming = sorted.filter { !it.paid }
        if (upcoming.isEmpty()) {
            val empty = TextView(this); empty.text = "Nothing due — you're all clear."; empty.setTextColor(p.muted); page.addView(empty)
        }
        for (item in upcoming) {
            val daysLeft = daysUntil(item.dueDate)
            val soon = daysLeft <= data.settings.notifDaysBefore
            val accName = data.accounts.find { it.id == item.accountId }?.name ?: "Deleted account"
            val itemCard = card(p)
            if (soon) itemCard.background = roundedBg(p.bad, 24f).also { it.alpha = 40 }
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val info = TextView(this)
            info.text = "${item.name}\nDue ${item.dueDate} · $accName · ${daysLeft}d left\n" + pesoFormat.format(item.amount)
            info.setTextColor(p.text)
            info.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(info)
            val btnCol = LinearLayout(this)
            btnCol.orientation = LinearLayout.VERTICAL
            val payBtn = styledButton("Paid", p.good, Color.WHITE)
            payBtn.setOnClickListener { item.paid = true; persist(); showTab("calendar") }
            btnCol.addView(payBtn)
            val delBtn = styledButton("Delete", p.bad, Color.WHITE, outline = true)
            delBtn.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = 8 }
            delBtn.setOnClickListener { data.paylaters.removeAll { pl -> pl.id == item.id }; persist(); showTab("calendar") }
            btnCol.addView(delBtn)
            row.addView(btnCol)
            itemCard.addView(row)
            page.addView(itemCard)
        }
        val paidItems = sorted.filter { it.paid }
        if (paidItems.isNotEmpty()) {
            page.addView(sectionTitle(p, "Paid"))
            for (item in paidItems) {
                val row = TextView(this)
                row.text = "${item.name} — " + pesoFormat.format(item.amount)
                row.setTextColor(p.muted)
                row.alpha = 0.6f
                page.addView(row)
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
        page.addView(sectionTitle(p, "Settings", 0))
        val themeCard = card(p)
        themeCard.addView(sectionTitle(p, "Theme", 0))
        val themeRow = LinearLayout(this)
        themeRow.orientation = LinearLayout.HORIZONTAL
        val themes = listOf("light" to "Light", "dark" to "Dark", "sunset" to "Sunset", "ocean" to "Ocean")
        for ((id, label) in themes) {
            val btn = styledButton(label, if (data.settings.theme == id) p.primary else p.bg, if (data.settings.theme == id) p.onPrimary else p.text, outline = data.settings.theme != id)
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginEnd = 8
            btn.layoutParams = lp
            btn.textSize = 11f
            btn.setPadding(8, 20, 8, 20)
            btn.setOnClickListener { data.settings.theme = id; persist(); showTab("settings") }
            themeRow.addView(btn)
        }
        themeCard.addView(themeRow)
        page.addView(themeCard)
        val accCard = card(p)
        accCard.addView(sectionTitle(p, "Accounts (net worth: " + pesoFormat.format(netWorth()) + ")", 0))
        for (acc in data.accounts) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, 12, 0, 12)
            val info = TextView(this)
            info.text = "${acc.name}\n" + pesoFormat.format(acc.balance)
            info.setTextColor(p.text)
            info.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(info)
            val editBtn = styledButton("Edit", p.primary, p.onPrimary, outline = true)
            editBtn.setOnClickListener { showEditAccountDialog(acc) }
            row.addView(editBtn)
            val delBtn = styledButton("Delete", p.bad, Color.WHITE, outline = true)
            delBtn.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.marginStart = 8 }
            delBtn.setOnClickListener {
                data.accounts.removeAll { a -> a.id == acc.id }
                data.netWorthLog.add(NetWorthEntry(System.currentTimeMillis(), netWorth(), "Deleted account: ${acc.name}"))
                persist(); showTab("settings")
            }
            row.addView(delBtn)
            accCard.addView(row)
        }
        val newAccName = styledEditText(p, "New account name (e.g. BPI, Savings jar)")
        accCard.addView(newAccName)
        val newAccBalance = styledEditText(p, "Starting balance")
        newAccBalance.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        accCard.addView(newAccBalance)
        val addAccBtn = styledButton("+ Add account", p.primary, p.onPrimary)
        addAccBtn.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = 16 }
        addAccBtn.setOnClickListener {
            val name = newAccName.text.toString().trim()
            if (name.isEmpty()) { Toast.makeText(this, "Give the account a name", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            val balance = newAccBalance.text.toString().toDoubleOrNull() ?: 0.0
            data.accounts.add(Account(Store.newId(), name, balance))
            data.netWorthLog.add(NetWorthEntry(System.currentTimeMillis(), netWorth(), "Added account: $name"))
            persist(); showTab("settings")
        }
        accCard.addView(addAccBtn)
        page.addView(accCard)
        val notifCard = card(p)
        notifCard.addView(sectionTitle(p, "Notifications", 0))
        val notifDaysInput = styledEditText(p, "Remind me this many days before due date")
        notifDaysInput.setText(data.settings.notifDaysBefore.toString())
        notifDaysInput.inputType = InputType.TYPE_CLASS_NUMBER
        notifCard.addView(notifDaysInput)
        val saveNotifBtn = styledButton("Save", p.primary, p.onPrimary, outline = true)
        saveNotifBtn.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = 12 }
        saveNotifBtn.setOnClickListener {
            data.settings.notifDaysBefore = notifDaysInput.text.toString().toIntOrNull() ?: 2
            persist()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }
        notifCard.addView(saveNotifBtn)
        val enableNotifBtn = styledButton("Enable notifications", p.accent, Color.parseColor("#12242F"))
        enableNotifBtn.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = 12 }
        enableNotifBtn.setOnClickListener { requestNotificationPermissionAndShow() }
        notifCard.addView(enableNotifBtn)
        page.addView(notifCard)
        val dataCard = card(p)
        dataCard.addView(sectionTitle(p, "Data", 0))
        val resetBtn = styledButton("Reset all data", p.bad, Color.WHITE, outline = true)
        resetBtn.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset all data?")
                .setMessage("This can't be undone.")
                .setPositiveButton("Reset") { _, _ -> data = Store.defaultData(); persist(); showTab("home") }
                .setNegativeButton("Cancel", null)
                .show()
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
        AlertDialog.Builder(this)
            .setTitle("Edit account")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                account.name = nameInput.text.toString().trim().ifEmpty { account.name }
                account.balance = balanceInput.text.toString().toDoubleOrNull() ?: account.balance
                data.netWorthLog.add(NetWorthEntry(System.currentTimeMillis(), netWorth(), "Manual balance update"))
                persist(); showTab("settings")
            }
            .setNegativeButton("Cancel", null)
            .show()
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
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            showTestNotification()
        }
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
            .setContentText("You'll see reminders here for upcoming PayLater dues.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        NotificationManagerCompat.from(this).notify(1, builder.build())
    }
}
data class Palette(
    val bg: Int, val surface: Int, val text: Int, val muted: Int,
    val primary: Int, val accent: Int, val good: Int, val bad: Int, val border: Int,
    val onPrimary: Int
)

class SimpleLineView(context: Context) : View(context) {
    var values: List<Float> = listOf()
    var lineColor: Int = Color.BLUE

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.size < 2) return
        val paint = Paint()
        paint.color = lineColor
        paint.strokeWidth = 5f
        paint.isAntiAlias = true
        val maxV = values.max()
        val minV = values.min()
        val range = if (maxV - minV == 0f) 1f else maxV - minV
        val w = width.toFloat()
        val h = height.toFloat()
        val stepX = if (values.size > 1) w / (values.size - 1) else w
        var prevX = 0f
        var prevY = h - ((values[0] - minV) / range) * h
        for (i in 1 until values.size) {
            val x = i * stepX
            val y = h - ((values[i] - minV) / range) * h
            canvas.drawLine(prevX, prevY, x, y, paint)
            prevX = x
            prevY = y
        }
    }
}
