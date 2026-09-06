package com.pera.tracker

import android.content.Context
import com.google.gson.Gson
import java.util.UUID

object Store {
    private const val PREFS = "pera_tracker_prefs"
    private const val KEY_DATA = "app_data"
    private val gson = Gson()

    fun load(context: Context): AppData {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_DATA, null) ?: return defaultData()
        return try {
            gson.fromJson(json, AppData::class.java) ?: defaultData()
        } catch (e: Exception) {
            defaultData()
        }
    }

    fun save(context: Context, data: AppData) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DATA, gson.toJson(data)).apply()
    }

    fun newId(): String = UUID.randomUUID().toString()

    fun defaultData(): AppData {
        return AppData(
            accounts = mutableListOf(
                Account(newId(), "GCash", 0.0),
                Account(newId(), "Maya", 0.0),
                Account(newId(), "Cash", 0.0)
            ),
            categories = mutableListOf(
                Category("food", "Food", "🍜", "#FFB100"),
                Category("transport", "Transport", "🚌", "#1B3A4B"),
                Category("utilities", "Utilities", "💡", "#2E8B57"),
                Category("shopping", "Shopping", "🛍️", "#E4572E"),
                Category("health", "Health", "💊", "#7A5195"),
                Category("fun", "Fun", "🎮", "#0089BA"),
                Category("credit", "Credit", "🧾", "#C05C36"),
                Category("other", "Other", "📦", "#6B7280")
            )
        )
    }
}
