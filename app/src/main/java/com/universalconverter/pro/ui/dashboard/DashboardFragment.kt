package com.universalconverter.pro.ui.dashboard

import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.universalconverter.pro.database.ConversionHistoryEntity
import com.universalconverter.pro.database.ConverterDatabase
import com.universalconverter.pro.databinding.FragmentDashboardBinding
import com.universalconverter.pro.engine.FileDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class DashboardViewModel : ViewModel() {

    data class DashStats(
        val total: Int,
        val saved: Long,
        val avgReduction: Int,
        val byType: Map<String, Int>
    )

    private val _stats   = androidx.lifecycle.MutableLiveData<DashStats>()
    private val _history = androidx.lifecycle.MutableLiveData<List<ConversionHistoryEntity>>()
    val stats:   androidx.lifecycle.LiveData<DashStats> = _stats
    val history: androidx.lifecycle.LiveData<List<ConversionHistoryEntity>> = _history

    fun load(context: android.content.Context) {
        viewModelScope.launch {
            val db    = ConverterDatabase.getInstance(context)
            val items = withContext(Dispatchers.IO) { db.historyDao().getSuccessful() }
            val total = items.size
            val saved = items.sumOf { (it.inputSizeBytes - it.outputSizeBytes).coerceAtLeast(0L) }
            val avgR  = if (items.isEmpty()) 0 else items.map { it.compressionPercent }.average().toInt()
            val byType = items.groupBy { it.conversionType }.mapValues { it.value.size }
            _stats.value   = DashStats(total, saved, avgR, byType)
            _history.value = items.take(30)
        }
    }

    fun clearHistory(context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            ConverterDatabase.getInstance(context).historyDao().deleteOlderThan(0)
            withContext(Dispatchers.Main) { load(context) }
        }
    }
}

class DashboardFragment : Fragment() {
    private var _b: FragmentDashboardBinding? = null
    private val b get() = _b!!
    private val vm: DashboardViewModel by viewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentDashboardBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        b.btnClearHistory.setOnClickListener { vm.clearHistory(requireContext()) }

        vm.stats.observe(viewLifecycleOwner) { s ->
            b.tvTotalConversions.text = "${s.total}"
            b.tvTotalSaved.text       = FileDetector.formatSize(s.saved)
            b.tvAvgReduction.text     = "${s.avgReduction}%"
            b.tvByType.text           = s.byType.entries.joinToString(" • ") { "${it.key}: ${it.value}" }
        }

        vm.history.observe(viewLifecycleOwner) { items ->
            b.rvHistory.adapter = HistoryAdapter(items)
        }

        vm.load(requireContext())
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }

    inner class HistoryAdapter(
        private val items: List<ConversionHistoryEntity>
    ) : RecyclerView.Adapter<HistoryAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v)

        override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(
            LayoutInflater.from(p.context).inflate(
                com.universalconverter.pro.R.layout.item_history, p, false
            )
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val item = items[pos]
            h.itemView.findViewById<TextView>(com.universalconverter.pro.R.id.tvHistName).text = item.inputName
            h.itemView.findViewById<TextView>(com.universalconverter.pro.R.id.tvHistInfo).text =
                "${item.outputFormat.uppercase()} • ${FileDetector.formatReduction(item.inputSizeBytes, item.outputSizeBytes)}"
            h.itemView.findViewById<TextView>(com.universalconverter.pro.R.id.tvHistDate).text =
                SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(item.createdAt))
        }
    }
}
