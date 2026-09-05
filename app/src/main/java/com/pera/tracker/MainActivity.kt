package com.pera.tracker

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
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
        root.addView(buildBottomNav())

        setContentView(root)
        showTab("home")
    }

    private fun persist() { Store.save(this, data) }

    private fun palette(): Palette {
        return if (data.settings.theme == "dark") {
            Palette(
                bg = Color.parseColor("#0D1B24"), surface = Color.parseColor("#132A38"),
                text = Color.WHITE, muted = Color.parseColor("#9FB2BC"),
                primary = Color.parseColor("#FFB100"), accent = Color.parseColor("#FFB100"),
                good = Color.parseColor("#4ADE80"), bad = Color.parseColor("#FB7185"),
                border = Color.parseColor("#233F4F")
            )
        } else {
            Palette(
                bg = Color.parseColor("#F5F6F4"), surface = Color.WHITE,
                text = Color.parseColor("#12242F"), muted = Color.parseColor("#5B6B72"),
                primary = Color.parseColor("#1B3A4B"), accent = Color.parseColor("#FFB100"),
                good = Color.parseColor("#2E8B57"), bad = Color.parseColor("#E4572E"),
                border = Color.parseColor("#E1E4E0")
            )
        }
    }

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
        val view = when (tab) {
            "home" -> buildHome()
            "expenses" -> buildExpenses()
            "stats" -> buildStats()
            "goals" -> buildGoals()
            "calendar" -> buildCalendar()
            else -> buildSettings()
        }
        contentContainer.addView(view)
        highlightNav()
        contentContainer.setBackgroundColor(palette().bg)
    }

    private fun buildBottomNav(): LinearLayout {
        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.setBackgroundColor(Color.WHITE)
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
        }
    }

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
        nwValue.textSize = 30f
        nwValue.setTypeface(null, Typeface.BOLD)
        page.addView(nwValue)

        val accLabel = TextView(this)
        accLabel.text = "\nAccounts"
        accLabel.setTextColor(p.text)
        accLabel.textSize = 16f
        accLabel.setTypeface(null, Typeface.BOLD)
        page.addView(accLabel)

        for (acc in data.accounts) {
            val row = TextView(this)
            row.text = "${acc.name}: " + pesoFormat.format(acc.balance)
            row.setTextColor(p.text)
            row.setPadding(0, 10, 0, 10)
            page.addView(row)
        }

        val monthLabel = TextView(this)
        monthLabel.text = "\nThis month's spending"
        monthLabel.setTextColor(p.text)
        monthLabel.textSize = 16f
        monthLabel.setTypeface(null, Typeface.BOLD)
        page.addView(monthLabel)

        val monthPrefix = todayString().substring(0, 7)
        val monthExpenses = data.expenses.filter { it.date.length >= 7 && it.date.substring(0, 7) == monthPrefix }
        val total = monthExpenses.sumOf { it.amount }
        val byCat = HashMap<String, Double>()
        for (e in monthExpenses) byCat[e.categoryId] = (byCat[e.categoryId] ?: 0.0) + e.amount

        val totalText = TextView(this)
        totalText.text = "Total: " + pesoFormat.format(total)
        totalText.setTextColor(p.accent)
        totalText.setTypeface(null, Typeface.BOLD)
        page.addView(totalText)

        if (byCat.isEmpty()) {
            val empty = TextView(this)
            empty.text = "No expenses logged yet this month."
            empty.setTextColor(p.muted)
            page.addView(empty)
        } else {
            for ((catId, amt) in byCat.entries.sortedByDescending { it.value }) {
                val cat = data.categories.find { it.id == catId }
                val label = TextView(this)
                label.text = "${cat?.emoji ?: "📦"} ${cat?.name ?: catId}: " + pesoFormat.format(amt)
                label.setTextColor(p.text)
                label.setPadding(0, 8, 0, 0)
                page.addView(label)

                val pct = if (total > 0) ((amt / total) * 100).toInt() else 0
                val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
                bar.max = 100
                bar.progress = pct
                page.addView(bar)
            }
        }

        val addBtn = Button(this)
        addBtn.text = "Add an expense"
        addBtn.setOnClickListener { showTab("expenses") }
        page.addView(addBtn)

        return scroll
    }

    private fun buildExpenses(): View {
        val p = palette()
        val scroll = ScrollView(this)
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(40, 50, 40, 50)
        scroll.addView(page)

        val title = TextView(this)
        title.text = "Add expense"
        title.textSize = 18f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(p.text)
        page.addView(title)

        val amountInput = EditText(this)
        amountInput.hint = "Amount"
        amountInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        page.addView(amountInput)

        val catLabel = TextView(this)
        catLabel.text = "\nCategory"
        catLabel.setTextColor(p.text)
        page.addView(catLabel)

        val catScroll = HorizontalScrollView(this)
        val catRow = LinearLayout(this)
        catRow.orientation = LinearLayout.HORIZONTAL
        catScroll.addView(catRow)
        page.addView(catScroll)

        var selectedCategoryId = data.categories.firstOrNull()?.id ?: ""
        val chipViews = mutableMapOf<String, TextView>()

        fun styleChip(view: TextView, cat: Category, selected: Boolean) {
            val bgDrawable = GradientDrawable()
            bgDrawable.cornerRadius = 60f
            bgDrawable.setColor(if (selected) Color.parseColor(cat.colorHex) else Color.parseColor("#E1E4E0"))
            view.background = bgDrawable
            view.setTextColor(if (selected) Color.WHITE else Color.DKGRAY)
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
                for (c in data.categories) {
                    chipViews[c.id]?.let { v -> styleChip(v, c, c.id == selectedCategoryId) }
                }
            }
            chipViews[cat.id] = chip
            catRow.addView(chip)
        }

        val accLabel = TextView(this)
        accLabel.text = "\nFrom account"
        accLabel.setTextColor(p.text)
        page.addView(accLabel)

        val accountSpinner = Spinner(this)
        val accountNames = data.accounts.map { it.name }
        val accAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, accountNames)
        accAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        accountSpinner.adapter = accAdapter
        page.addView(accountSpinner)

        val noteLabel = TextView(this)
        noteLabel.text = "\nNote (optional)"
        noteLabel.setTextColor(p.text)
        page.addView(noteLabel)

        val noteInput = EditText(this)
        noteInput.hint = "e.g. lunch with friends"
        page.addView(noteInput)

        val addBtn = Button(this)
        addBtn.text = "Save expense"
        page.addView(addBtn)

        addBtn.setOnClickListener {
            val amt = amountInput.text.toString().toDoubleOrNull()
            if (amt == null || amt <= 0) {
                Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (data.accounts.isEmpty()) {
                Toast.makeText(this, "Add an account first, in Settings", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val accIndex = accountSpinner.selectedItemPosition
            if (accIndex < 0 || accIndex >= data.accounts.size) {
                Toast.makeText(this, "Pick an account", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val account = data.accounts[accIndex]
            val expense = Expense(Store.newId(), todayString(), selectedCategoryId, amt, noteInput.text.toString(), account.id)
            account.balance -= amt
            data.expenses.add(0, expense)
            data.netWorthLog.add(NetWorthEntry(System.currentTimeMillis(), netWorth(), "Expense"))
            persist()
            Toast.makeText(this, "Expense added", Toast.LENGTH_SHORT).show()
            showTab("expenses")
        }

        val manageLabel = TextView(this)
        manageLabel.text = "\nManage categories"
        manageLabel.setTextColor(p.text)
        manageLabel.textSize = 16f
        manageLabel.setTypeface(null, Typeface.BOLD)
        page.addView(manageLabel)

        for (cat in data.categories) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val label = TextView(this)
            label.text = "${cat.emoji} ${cat.name}"
            label.setTextColor(p.text)
            label.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(label)
            val delBtn = Button(this)
            delBtn.text = "Delete"
            delBtn.setOnClickListener {
                data.categories.removeAll { it.id == cat.id }
                persist()
                showTab("expenses")
            }
            row.addView(delBtn)
            page.addView(row)
        }

        val newCatName = EditText(this)
        newCatName.hint = "New category name"
        page.addView(newCatName)

        val addCatBtn = Button(this)
        addCatBtn.text = "Add category"
        addCatBtn.setOnClickListener {
            val name = newCatName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Name it first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val colors = listOf("#FFB100", "#1B3A4B", "#2E8B57", "#E4572E", "#7A5195", "#0089BA")
            data.categories.add(Category(Store.newId(), name, "🏷️", colors.random()))
            persist()
            showTab("expenses")
        }
        page.addView(addCatBtn)

        val recentLabel = TextView(this)
        recentLabel.text = "\nRecent"
        recentLabel.setTextColor(p.text)
        recentLabel.textSize = 16f
        recentLabel.setTypeface(null, Typeface.BOLD)
        page.addView(recentLabel)

        if (data.expenses.isEmpty()) {
            val empty = TextView(this)
            empty.text = "Nothing yet — add your first expense above."
            empty.setTextColor(p.muted)
            page.addView(empty)
        }

        for (exp in data.expenses.take(40)) {
            val cat = data.categories.find { it.id == exp.categoryId }
            val accName = data.accounts.find { it.id == exp.accountId }?.name ?: "Deleted account"
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, 12, 0, 12)

            val info = TextView(this)
            val noteOrCat = if (exp.note.isNotBlank()) exp.note else (cat?.name ?: "Expense")
            info.text = "$noteOrCat\n${exp.date} · $accName · -" + pesoFormat.format(exp.amount)
            info.setTextColor(p.text)
            info.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(info)

            val delBtn = Button(this)
            delBtn.text = "Delete"
            delBtn.setOnClickListener {
                data.expenses.removeAll { it.id == exp.id }
                persist()
                showTab("expenses")
            }
            row.addView(delBtn)

            page.addView(row)
        }

        return scroll
    }

    private fun buildStats(): View {
        val p = palette()
        val scroll = ScrollView(this)
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(40, 50, 40, 50)
        scroll.addView(page)

        val title = TextView(this)
        title.text = "Stats & graphs"
        title.textSize = 18f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(p.text)
        page.addView(title)

        val monthPrefix = todayString().substring(0, 7)
        val monthExpenses = data.expenses.filter { it.date.length >= 7 && it.date.substring(0, 7) == monthPrefix }
        val byCat = HashMap<String, Double>()
        for (e in monthExpenses) byCat[e.categoryId] = (byCat[e.categoryId] ?: 0.0) + e.amount
        val monthTotal = monthExpenses.sumOf { it.amount }

        val catTitle = TextView(this)
        catTitle.text = "\nSpending by category (this month)"
        catTitle.setTextColor(p.text)
        catTitle.setTypeface(null, Typeface.BOLD)
        page.addView(catTitle)

        if (byCat.isEmpty()) {
            val empty = TextView(this)
            empty.text = "No data yet."
            empty.setTextColor(p.muted)
            page.addView(empty)
        } else {
            for ((catId, amt) in byCat.entries.sortedByDescending { it.value }) {
                val cat = data.categories.find { it.id == catId }
                val pct = if (monthTotal > 0) ((amt / monthTotal) * 100).toInt() else 0
                val label = TextView(this)
                label.text = "${cat?.emoji ?: "📦"} ${cat?.name ?: catId} — $pct%"
                label.setTextColor(p.text)
                label.setPadding(0, 8, 0, 0)
                page.addView(label)
                val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
                bar.max = 100
                bar.progress = pct
                page.addView(bar)
            }
        }

        val nwTitle = TextView(this)
        nwTitle.text = "\nNet worth trend"
        nwTitle.setTextColor(p.text)
        nwTitle.setTypeface(null, Typeface.BOLD)
        page.addView(nwTitle)

        val recentLog = data.netWorthLog.takeLast(30)
        if (recentLog.size < 2) {
            val empty = TextView(this)
            empty.text = "Add or spend money a few times to see your trend."
            empty.setTextColor(p.muted)
            page.addView(empty)
        } else {
            val sparkline = SimpleLineView(this)
            sparkline.values = recentLog.map { it.amount.toFloat() }
            sparkline.lineColor = p.primary
            sparkline.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 300)
            page.addView(sparkline)
        }

        val advTitle = TextView(this)
        advTitle.text = "\nAdvanced stats"
        advTitle.setTextColor(p.text)
        advTitle.setTypeface(null, Typeface.BOLD)
        page.addView(advTitle)

        val totalAllTime = data.expenses.sumOf { it.amount }
        val avgPerExpense = if (data.expenses.isNotEmpty()) totalAllTime / data.expenses.size else 0.0
        val daysTracked = if (data.netWorthLog.isNotEmpty()) {
            val d = (System.currentTimeMillis() - data.netWorthLog.first().timestamp) / 86400000.0
            if (d < 1.0) 1.0 else d
        } else 1.0
        val avgDailySpend = totalAllTime / daysTracked

        addStatRow(page, p, "Total tracked spend", pesoFormat.format(totalAllTime))
        addStatRow(page, p, "Average per expense", pesoFormat.format(avgPerExpense))
        addStatRow(page, p, "Average daily spend", pesoFormat.format(avgDailySpend))
        addStatRow(page, p, "Number of expenses logged", data.expenses.size.toString())
        addStatRow(page, p, "Current net worth", pesoFormat.format(netWorth()))

        return scroll
    }

    private fun addStatRow(page: LinearLayout, p: Palette, label: String, value: String) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, 10, 0, 10)
        val l = TextView(this)
        l.text = label
        l.setTextColor(p.muted)
        l.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(l)
        val v = TextView(this)
        v.text = value
        v.setTextColor(p.text)
        v.setTypeface(null, Typeface.BOLD)
        row.addView(v)
        page.addView(row)
    }

    private fun buildGoals(): View {
        val p = palette()
        val scroll = ScrollView(this)
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(40, 50, 40, 50)
        scroll.addView(page)

        val title = TextView(this)
        title.text = "Goals"
        title.textSize = 18f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(p.text)
        page.addView(title)

        val labelInput = EditText(this)
        labelInput.hint = "Goal name (e.g. Emergency fund)"
        page.addView(labelInput)

        val targetInput = EditText(this)
        targetInput.hint = "Target amount"
        targetInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        page.addView(targetInput)

        val dateInput = EditText(this)
        dateInput.hint = "Target date (YYYY-MM-DD)"
        dateInput.setText(todayString())
        page.addView(dateInput)

        val addBtn = Button(this)
        addBtn.text = "Add goal"
        addBtn.setOnClickListener {
            val label = labelInput.text.toString().trim()
            val target = targetInput.text.toString().toDoubleOrNull()
            val dateStr = dateInput.text.toString().trim()
            if (label.isEmpty() || target == null) {
                Toast.makeText(this, "Fill in a goal name and target amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            data.goals.add(Goal(Store.newId(), label, target, dateStr, netWorth(), todayString()))
            persist()
            Toast.makeText(this, "Goal added", Toast.LENGTH_SHORT).show()
            showTab("goals")
        }
        page.addView(addBtn)

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
            empty.text = "\nNo goals yet — set one above, like ₱10,000 by end of month."
            empty.setTextColor(p.muted)
            page.addView(empty)
        }

        for (goal in data.goals) {
            val card = LinearLayout(this)
            card.orientation = LinearLayout.VERTICAL
            card.setPadding(24, 24, 24, 24)
            val bgDrawable = GradientDrawable()
            bgDrawable.cornerRadius = 20f
            bgDrawable.setColor(p.surface)
            bgDrawable.setStroke(2, p.border)
            card.background = bgDrawable
            val cardLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            cardLp.topMargin = 24
            card.layoutParams = cardLp

            val nameRow = LinearLayout(this)
            nameRow.orientation = LinearLayout.HORIZONTAL
            val nameText = TextView(this)
            nameText.text = "${goal.label}\nTarget: " + pesoFormat.format(goal.targetAmount) + " by ${goal.targetDate}"
            nameText.setTextColor(p.text)
            nameText.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            nameRow.addView(nameText)
            val delBtn = Button(this)
            delBtn.text = "Delete"
            delBtn.setOnClickListener {
                data.goals.removeAll { it.id == goal.id }
                persist()
                showTab("goals")
            }
            nameRow.addView(delBtn)
            card.addView(nameRow)

            val remaining = goal.targetAmount - netWorth()
            val totalNeeded = goal.targetAmount - goal.startAmount
            val progressPct = if (totalNeeded != 0.0) (((netWorth() - goal.startAmount) / totalNeeded) * 100).toInt().coerceIn(0, 100) else 0
            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
            bar.max = 100
            bar.progress = progressPct
            card.addView(bar)

            val progressText = TextView(this)
            progressText.text = "$progressPct% there · " + pesoFormat.format(if (remaining > 0) remaining else 0.0) + " left"
            progressText.setTextColor(p.muted)
            progressText.textSize = 12f
            card.addView(progressText)

            val daysLeft = daysUntil(goal.targetDate).let { if (it < 1) 1 else it }
            val neededPerDay = if (remaining > 0) remaining / daysLeft else 0.0
            val perDayText = TextView(this)
            perDayText.text = if (remaining > 0) "Save " + pesoFormat.format(neededPerDay) + "/day to hit your date" else "Goal reached! 🎉"
            perDayText.setTextColor(p.text)
            perDayText.setPadding(0, 12, 0, 0)
            card.addView(perDayText)

            val paceText = TextView(this)
            paceText.text = when {
                remaining <= 0 -> "Done"
                avgDailySavings > 0 -> "At your current pace: ~${Math.ceil(remaining / avgDailySavings).toInt()} days"
                else -> "At your current pace: not enough history yet"
            }
            paceText.setTextColor(p.muted)
            paceText.textSize = 12f
            card.addView(paceText)

            page.addView(card)
        }

        return scroll
    }

    private fun buildCalendar(): View {
        val p = palette()
        val scroll = ScrollView(this)
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(40, 50, 40, 50)
        scroll.addView(page)

        val title = TextView(this)
        title.text = "PayLater calendar"
        title.textSize = 18f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(p.text)
        page.addView(title)

        val nameInput = EditText(this)
        nameInput.hint = "e.g. Shopee PayLater"
        page.addView(nameInput)

        val amountInput = EditText(this)
        amountInput.hint = "Amount"
        amountInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        page.addView(amountInput)

        val dueInput = EditText(this)
        dueInput.hint = "Due date (YYYY-MM-DD)"
        dueInput.setText(todayString())
        page.addView(dueInput)

        val accSpinner = Spinner(this)
        val accNames = data.accounts.map { it.name }
        val accAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, accNames)
        accAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        accSpinner.adapter = accAdapter
        page.addView(accSpinner)

        val addBtn = Button(this)
        addBtn.text = "Add due date"
        addBtn.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val amt = amountInput.text.toString().toDoubleOrNull()
            val due = dueInput.text.toString().trim()
            if (name.isEmpty() || amt == null) {
                Toast.makeText(this, "Fill in name and amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (data.accounts.isEmpty()) {
                Toast.makeText(this, "Add an account first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val accIndex = accSpinner.selectedItemPosition.coerceIn(0, data.accounts.size - 1)
            val accountId = data.accounts[accIndex].id
            data.paylaters.add(PayLaterItem(Store.newId(), name, amt, due, accountId, false))
            persist()
            Toast.makeText(this, "PayLater added", Toast.LENGTH_SHORT).show()
            showTab("calendar")
        }
        page.addView(addBtn)

        val upcomingTitle = TextView(this)
        upcomingTitle.text = "\nUpcoming"
        upcomingTitle.setTextColor(p.text)
        upcomingTitle.setTypeface(null, Typeface.BOLD)
        page.addView(upcomingTitle)

        val sorted = data.paylaters.sortedBy { it.dueDate }
        val upcoming = sorted.filter { !it.paid }

        if (upcoming.isEmpty()) {
            val empty = TextView(this)
            empty.text = "Nothing due — you're all clear."
            empty.setTextColor(p.muted)
            page.addView(empty)
        }

        for (item in upcoming) {
            val daysLeft = daysUntil(item.dueDate)
            val soon = daysLeft <= data.settings.notifDaysBefore
            val accName = data.accounts.find { it.id == item.accountId }?.name ?: "Deleted account"

            val card = LinearLayout(this)
            card.orientation = LinearLayout.HORIZONTAL
            card.gravity = Gravity.CENTER_VERTICAL
            card.setPadding(20, 20, 20, 20)
            val bgDrawable = GradientDrawable()
            bgDrawable.cornerRadius = 16f
            bgDrawable.setColor(if (soon) Color.parseColor("#FDE8E6") else p.surface)
            card.background = bgDrawable
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = 12
            card.layoutParams = lp

            val info = TextView(this)
            info.text = "${item.name}\nDue ${item.dueDate} · $accName · ${daysLeft}d left\n" + pesoFormat.format(item.amount)
            info.setTextColor(p.text)
            info.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            card.addView(info)

            val payBtn = Button(this)
            payBtn.text = "Paid"
            payBtn.setOnClickListener {
                item.paid = true
                persist()
                showTab("calendar")
            }
            card.addView(payBtn)

            val delBtn = Button(this)
            delBtn.text = "Delete"
            delBtn.setOnClickListener {
                data.paylaters.removeAll { it.id == item.id }
                persist()
                showTab("calendar")
            }
            card.addView(delBtn)

            page.addView(card)
        }

        val paidItems = sorted.filter { it.paid }
        if (paidItems.isNotEmpty()) {
            val paidTitle = TextView(this)
            paidTitle.text = "\nPaid"
            paidTitle.setTextColor(p.muted)
            paidTitle.setTypeface(null, Typeface.BOLD)
            page.addView(paidTitle)
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

    private fun buildSettings(): View {
        val p = palette()
        val scroll = ScrollView(this)
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(40, 50, 40, 50)
        scroll.addView(page)

        val title = TextView(this)
        title.text = "Settings"
        title.textSize = 18f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(p.text)
        page.addView(title)

        val themeBtn = Button(this)
        themeBtn.text = "Switch to " + (if (data.settings.theme == "dark") "light" else "dark") + " mode"
        themeBtn.setOnClickListener {
            data.settings.theme = if (data.settings.theme == "dark") "light" else "dark"
            persist()
            showTab("settings")
        }
        page.addView(themeBtn)

        val accTitle = TextView(this)
        accTitle.text = "\nAccounts (net worth: " + pesoFormat.format(netWorth()) + ")"
        accTitle.setTextColor(p.text)
        accTitle.setTypeface(null, Typeface.BOLD)
        page.addView(accTitle)

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

            val editBtn = Button(this)
            editBtn.text = "Edit"
            editBtn.setOnClickListener { showEditAccountDialog(acc) }
            row.addView(editBtn)

            val delBtn = Button(this)
            delBtn.text = "Delete"
            delBtn.setOnClickListener {
                data.accounts.removeAll { it.id == acc.id }
                data.netWorthLog.add(NetWorthEntry(System.currentTimeMillis(), netWorth(), "Deleted account: ${acc.name}"))
                persist()
                showTab("settings")
            }
            row.addView(delBtn)

            page.addView(row)
        }

        val newAccName = EditText(this)
        newAccName.hint = "New account name (e.g. BPI, Savings jar)"
        page.addView(newAccName)

        val newAccBalance = EditText(this)
        newAccBalance.hint = "Starting balance"
        newAccBalance.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        page.addView(newAccBalance)

        val addAccBtn = Button(this)
        addAccBtn.text = "Add account"
        addAccBtn.setOnClickListener {
            val name = newAccName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Give the account a name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val balance = newAccBalance.text.toString().toDoubleOrNull() ?: 0.0
            data.accounts.add(Account(Store.newId(), name, balance))
            data.netWorthLog.add(NetWorthEntry(System.currentTimeMillis(), netWorth(), "Added account: $name"))
            persist()
            showTab("settings")
        }
        page.addView(addAccBtn)

        val notifTitle = TextView(this)
        notifTitle.text = "\nNotifications"
        notifTitle.setTextColor(p.text)
        notifTitle.setTypeface(null, Typeface.BOLD)
        page.addView(notifTitle)

        val notifDaysInput = EditText(this)
        notifDaysInput.hint = "Remind me this many days before due date"
        notifDaysInput.setText(data.settings.notifDaysBefore.toString())
        notifDaysInput.inputType = InputType.TYPE_CLASS_NUMBER
        page.addView(notifDaysInput)

        val saveNotifBtn = Button(this)
        saveNotifBtn.text = "Save"
        saveNotifBtn.setOnClickListener {
            data.settings.notifDaysBefore = notifDaysInput.text.toString().toIntOrNull() ?: 2
            persist()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }
        page.addView(saveNotifBtn)

        val enableNotifBtn = Button(this)
        enableNotifBtn.text = "Enable notifications"
        enableNotifBtn.setOnClickListener { requestNotificationPermissionAndShow() }
        page.addView(enableNotifBtn)

        val dataTitle = TextView(this)
        dataTitle.text = "\nData"
        dataTitle.setTextColor(p.text)
        dataTitle.setTypeface(null, Typeface.BOLD)
        page.addView(dataTitle)

        val resetBtn = Button(this)
        resetBtn.text = "Reset all data"
        resetBtn.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset all data?")
                .setMessage("This can't be undone.")
                .setPositiveButton("Reset") { _, _ ->
                    data = Store.defaultData()
                    persist()
                    showTab("home")
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        page.addView(resetBtn)

        return scroll
    }

    private fun showEditAccountDialog(account: Account) {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(40, 30, 40, 10)

        val nameInput = EditText(this)
        nameInput.setText(account.name)
        layout.addView(nameInput)

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
                persist()
                showTab("settings")
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
    val primary: Int, val accent: Int, val good: Int, val bad: Int, val border: Int
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
