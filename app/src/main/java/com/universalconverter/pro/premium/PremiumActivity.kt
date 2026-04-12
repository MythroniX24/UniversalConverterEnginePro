package com.universalconverter.pro.premium

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.*
import android.widget.*
import com.google.android.material.button.MaterialButton
import com.universalconverter.pro.R

class PremiumActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_premium)
        supportActionBar?.apply { title = "Upgrade to Pro"; setDisplayHomeAsUpEnabled(true) }

        val rv = findViewById<RecyclerView>(R.id.rvFeatures)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = FeatureAdapter(PremiumManager.features)

        val status = findViewById<TextView>(R.id.tvStatus)
        status.text = if(PremiumManager.isPremium(this)) "✓ Pro Active" else "${PremiumManager.remaining(this)} free conversions remaining today"

        findViewById<MaterialButton>(R.id.btnUpgrade).setOnClickListener {
            PremiumManager.setPremium(this, true)
            Toast.makeText(this, "🎉 Pro Unlocked! (Demo)", Toast.LENGTH_LONG).show()
            finish()
        }
        findViewById<MaterialButton>(R.id.btnRestore).setOnClickListener {
            Toast.makeText(this, "No purchases found (Demo)", Toast.LENGTH_SHORT).show()
        }
    }
    override fun onSupportNavigateUp() = true.also { finish() }

    inner class FeatureAdapter(private val items: List<Triple<String,String,Boolean>>) : RecyclerView.Adapter<FeatureAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.ivIcon)
            val name: TextView  = v.findViewById(R.id.tvName)
            val desc: TextView  = v.findViewById(R.id.tvDesc)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(LayoutInflater.from(p.context).inflate(R.layout.item_premium_feature, p, false))
        override fun getItemCount() = items.size
        override fun onBindViewHolder(h: VH, pos: Int) {
            val (name,desc,free) = items[pos]
            h.name.text = name; h.desc.text = desc
            h.icon.setImageResource(if(free) R.drawable.ic_check_free else R.drawable.ic_star_pro)
        }
    }
}
