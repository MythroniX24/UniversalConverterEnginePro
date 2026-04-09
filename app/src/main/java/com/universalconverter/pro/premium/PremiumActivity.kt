package com.universalconverter.pro.premium

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.universalconverter.pro.R

class PremiumActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_premium)

        supportActionBar?.apply {
            title = "Upgrade to Pro"
            setDisplayHomeAsUpEnabled(true)
        }

        val recycler = findViewById<RecyclerView>(R.id.rvFeatures)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = FeaturesAdapter(PremiumManager.features)

        val btnUpgrade = findViewById<MaterialButton>(R.id.btnUpgrade)
        btnUpgrade.setOnClickListener {
            // In a real app, launch billing flow here
            PremiumManager.setPremium(this, true)
            Toast.makeText(this, "Pro features unlocked! (Demo mode)", Toast.LENGTH_LONG).show()
            finish()
        }

        val btnRestore = findViewById<MaterialButton>(R.id.btnRestore)
        btnRestore.setOnClickListener {
            Toast.makeText(this, "No purchases found to restore (Demo mode)", Toast.LENGTH_SHORT).show()
        }

        updateStatusBanner()
    }

    private fun updateStatusBanner() {
        val tvStatus = findViewById<TextView>(R.id.tvPremiumStatus)
        if (PremiumManager.isPremium(this)) {
            tvStatus.text = "✓ Pro Active"
        } else {
            val remaining = PremiumManager.getRemainingFreeConversions(this)
            tvStatus.text = "$remaining free conversions remaining today"
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // ─── Features adapter ────────────────────────────────────────────────────
    inner class FeaturesAdapter(
        private val items: List<PremiumManager.PremiumFeature>
    ) : RecyclerView.Adapter<FeaturesAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView  = view.findViewById(R.id.ivFeatureIcon)
            val name: TextView   = view.findViewById(R.id.tvFeatureName)
            val desc: TextView   = view.findViewById(R.id.tvFeatureDesc)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_premium_feature, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val feature = items[position]
            holder.name.text = feature.name
            holder.desc.text = feature.description
            holder.icon.setImageResource(
                if (feature.freeAllowed) R.drawable.ic_check_free
                else R.drawable.ic_check_pro
            )
        }
    }
}
