package com.pera.tracker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Account(var id: String, var name: String, var balance: Double)

object Store {
    private const val PREFS = "pera_tracker_prefs"
    private const val KEY_ACCOUNTS = "accounts"

    fun loadAccounts(context: Context): MutableList<Account> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_ACCOUNTS, null) ?: return defaultAccounts()
        val arr = JSONArray(json)
        val list = mutableListOf<Account>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(Account(obj.getString("id"), obj.getString("name"), obj.getDouble("balance")))
        }
        return list
    }

    fun saveAccounts(context: Context, accounts: List<Account>) {
        val arr = JSONArray()
        for (a in accounts) {
            val obj = JSONObject()
            obj.put("id", a.id)
            obj.put("name", a.name)
            obj.put("balance", a.balance)
            arr.put(obj)
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACCOUNTS, arr.toString()).apply()
    }

    private fun defaultAccounts(): MutableList<Account> {
        return mutableListOf(
            Account("gcash", "GCash", 0.0),
            Account("maya", "Maya", 0.0),
            Account("cash", "Cash", 0.0)
        )
    }

    fun newId(): String = System.currentTimeMillis().toString()
}
