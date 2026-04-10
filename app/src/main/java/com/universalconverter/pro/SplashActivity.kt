package com.universalconverter.pro

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing && !isDestroyed) {
                try {
                    startActivity(Intent(this, MainActivity::class.java))
                } catch (e: Exception) {
                    // fallback — just finish, app will open normally
                }
                finish()
            }
        }, 1200L)
    }
}
