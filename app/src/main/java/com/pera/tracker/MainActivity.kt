package com.pera.tracker

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import java.text.NumberFormat
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var accounts: MutableList<Account>
    private lateinit var accountsContainer: LinearLayout
    private lateinit var netWorthText: TextView
    private val peso: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "PH"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accounts = Store.loadAccounts(this)

        val root = ScrollView(this)
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(40, 60, 40, 60)
        root.addView(page)

        val title = TextView(this)
        title.text = "Pera Tracker"
        title.textSize = 26f
        title.setTypeface(null, android.graphics.Typeface.BOLD)
        page.addView(title)

        netWorthText = TextView(this)
        netWorthText.textSize = 30f
        netWorthText.setPadding(0, 20, 0, 30)
        page.addView(netWorthText)

        val accountsLabel = TextView(this)
        accountsLabel.text = "Accounts"
        accountsLabel.textSize = 18f
        accountsLabel.setTypeface(null, android.graphics.Typeface.BOLD)
        page.addView(accountsLabel)

        accountsContainer = LinearLayout(this)
        accountsContainer.orientation = LinearLayout.VERTICAL
        page.addView(accountsContainer)

        val addLabel = TextView(this)
        addLabel.text = "\nAdd new account"
        addLabel.textSize = 18f
        addLabel.setTypeface(null, android.graphics.Typeface.BOLD)
        page.addView(addLabel)

        val nameInput = EditText(this)
        nameInput.hint = "Account name (e.g. BPI, Savings jar)"
        page.addView(nameInput)

        val balanceInput = EditText(this)
        balanceInput.hint = "Starting balance"
        balanceInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        page.addView(balanceInput)

        val addButton = Button(this)
        addButton.text = "Add account"
        page.addView(addButton)

        addButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val balanceStr = balanceInput.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Give the account a name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val balance = balanceStr.toDoubleOrNull() ?: 0.0
            accounts.add(Account(Store.newId(), name, balance))
            Store.saveAccounts(this, accounts)
            nameInput.setText("")
            balanceInput.setText("")
            refreshUI()
        }

        setContentView(root)
        refreshUI()
    }

    private fun refreshUI() {
        accountsContainer.removeAllViews()
        var netWorth = 0.0
        for (account in accounts) {
            netWorth += account.balance
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(0, 16, 0, 16)
            row.gravity = Gravity.CENTER_VERTICAL

            val info = TextView(this)
            info.text = "${account.name}\n${peso.format(account.balance)}"
            info.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(info)

            val editBtn = Button(this)
            editBtn.text = "Edit"
            editBtn.setOnClickListener { showEditDialog(account) }
            row.addView(editBtn)

            val deleteBtn = Button(this)
            deleteBtn.text = "Delete"
            deleteBtn.setOnClickListener {
                accounts.remove(account)
                Store.saveAccounts(this, accounts)
                refreshUI()
            }
            row.addView(deleteBtn)

            accountsContainer.addView(row)

            val divider = View(this)
            divider.setBackgroundColor(Color.LTGRAY)
            divider.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2)
            accountsContainer.addView(divider)
        }
        netWorthText.text = "Net worth\n" + peso.format(netWorth)
    }

    private fun showEditDialog(account: Account) {
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
                Store.saveAccounts(this, accounts)
                refreshUI()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
