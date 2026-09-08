package com.pera.tracker

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

class SplashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setBackgroundColor(Color.parseColor("#0D1B24"))
        root.setPadding(80, 80, 80, 80)

        val logo = ImageView(this)
        logo.setImageResource(R.drawable.logo_full)
        logo.layoutParams = LinearLayout.LayoutParams(700, 700)
        root.addView(logo)

        val slogan = TextView(this)
        slogan.text = "Know your pera. Grow your goals."
        slogan.setTextColor(Color.WHITE)
        slogan.textSize = 14f
        slogan.gravity = Gravity.CENTER
        slogan.setPadding(0, 24, 0, 60)
        root.addView(slogan)

        root.addView(ProgressBar(this))

        val status = TextView(this)
        status.text = "Setting up your finances…"
        status.setTextColor(Color.parseColor("#9FB2BC"))
        status.textSize = 13f
        status.gravity = Gravity.CENTER
        status.setPadding(0, 24, 0, 0)
        root.addView(status)

        setContentView(root)

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 1200)
    }
}
