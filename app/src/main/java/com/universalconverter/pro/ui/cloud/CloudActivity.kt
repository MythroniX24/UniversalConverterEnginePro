package com.universalconverter.pro.ui.cloud

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.universalconverter.pro.R

class CloudActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cloud)
        supportActionBar?.apply { title = "Cloud Integration"; setDisplayHomeAsUpEnabled(true) }
        // Google Drive + Dropbox setup UI
        Toast.makeText(this, "Cloud integration — connect your account to get started", Toast.LENGTH_LONG).show()
    }
    override fun onSupportNavigateUp() = true.also { finish() }
}
