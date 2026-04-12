package com.universalconverter.pro.ui.dashboard

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView
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
    private val _history = androidx.lifecycle.MutableLiveData<List<ConversionHistoryEntity>>(emptyList())
    private val _stats   = androidx.lifecycle.MutableLiveData<DashboardStats>()
    val history: androidx.lifecycle.LiveData<List<ConversionHistoryEntity>> = _history
    val stats:   androidx.lifecycle.LiveData<DashboardStats> = _stats

    data class DashboardStats(val total: Int, val bytesSaved: Long, val todayCount: Int, val successRate: Int)

    fun load(ctx: android.content.Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val dao  = ConverterDatabase.getInstance(ctx).historyDao()
                val all  = dao.getAll()
                val total = dao.getTotalCount()
                val saved = dao.getTotalBytesSaved() ?: 0L
                val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                val todayCount = all.count { SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(it.createdAt)) == today }
                val successRate = if(all.isNotEmpty()) all.count{it.status=="SUCCESS"}*100/all.size else 0
                _history.postValue(all)
                _stats.postValue(DashboardStats(total, saved, todayCount, successRate))
            }
        }
    }

    fun clearHistory(ctx: android.content.Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                ConverterDatabase.getInstance(ctx).historyDao().deleteOlderThan(0)
                _history.postValue(emptyList())
                _stats.postValue(DashboardStats(0,0L,0,0))
            }
        }
    }
}

class DashboardFragment : Fragment() {
    private var _b: FragmentDashboardBinding? = null
    private val b get() = _b!!
    private val vm: DashboardViewModel by viewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentDashboardBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        vm.load(requireContext())

        vm.stats.observe(viewLifecycleOwner) { s ->
            b.tvTotalConversions.text = "${s.total}"
            b.tvBytesSaved.text       = FileDetector.formatSize(s.bytesSaved)
            b.tvTodayCount.text       = "${s.todayCount}"
            b.tvSuccessRate.text      = "${s.successRate}%"
        }

        vm.history.observe(viewLifecycleOwner) { history ->
            b.rvHistory.adapter = HistoryAdapter(history)
            b.tvEmpty.visibility = if (history.isEmpty()) View.VISIBLE else View.GONE
        }

        b.btnClearHistory.setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Clear History").setMessage("Delete all conversion records?")
                .setPositiveButton("Clear") { _, _ -> vm.clearHistory(requireContext()) }
                .setNegativeButton("Cancel", null).show()
        }
    }

    inner class HistoryAdapter(private val items: List<ConversionHistoryEntity>) :
        RecyclerView.Adapter<HistoryAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView  = v.findViewById(com.universalconverter.pro.R.id.tvHistName)
            val info: TextView  = v.findViewById(com.universalconverter.pro.R.id.tvHistInfo)
            val status: TextView= v.findViewById(com.universalconverter.pro.R.id.tvHistStatus)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(LayoutInflater.from(p.context).inflate(com.universalconverter.pro.R.layout.item_history, p, false))
        override fun getItemCount() = items.size
        override fun onBindViewHolder(h: VH, pos: Int) {
            val item = items[pos]
            h.name.text   = item.inputName
            h.info.text   = "${FileDetector.formatSize(item.inputSizeBytes)} → ${FileDetector.formatSize(item.outputSizeBytes)} (${item.outputFormat.uppercase()})"
            h.status.text = if(item.status=="SUCCESS") "✓ -${item.compressionPercent}%" else "✗"
            h.status.setTextColor(if(item.status=="SUCCESS") 0xFF4CAF50.toInt() else 0xFFF44336.toInt())
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
