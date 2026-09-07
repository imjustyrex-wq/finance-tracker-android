package com.pera.tracker

data class Account(var id: String, var name: String, var balance: Double)
data class Category(var id: String, var name: String, var emoji: String, var colorHex: String)
data class Expense(var id: String, var date: String, var categoryId: String, var amount: Double, var note: String, var accountId: String)
data class GoalDeposit(var id: String, var timestamp: Long, var amount: Double, var accountId: String)
data class Goal(
    var id: String, var label: String, var targetAmount: Double, var targetDate: String,
    var startAmount: Double, var startDate: String,
    var savedAmount: Double = 0.0,
    var depositLog: MutableList<GoalDeposit> = mutableListOf()
)
data class PayLaterItem(var id: String, var name: String, var amount: Double, var dueDate: String, var accountId: String, var paid: Boolean)
data class NetWorthEntry(var timestamp: Long, var amount: Double, var note: String)
data class Settings(
    var theme: String = "light",
    var notifDaysBefore: Int = 2,
    var customAccent: String? = null,
    var showPaylaterTab: Boolean = true
)

data class AppData(
    var accounts: MutableList<Account> = mutableListOf(),
    var categories: MutableList<Category> = mutableListOf(),
    var expenses: MutableList<Expense> = mutableListOf(),
    var goals: MutableList<Goal> = mutableListOf(),
    var paylaters: MutableList<PayLaterItem> = mutableListOf(),
    var netWorthLog: MutableList<NetWorthEntry> = mutableListOf(),
    var settings: Settings = Settings()
)
