package com.universalconverter.pro.ui.document

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.universalconverter.pro.databinding.FragmentDocumentBinding
import com.universalconverter.pro.engine.FileDetector
import com.universalconverter.pro.engine.PdfConverter
import kotlinx.coroutines.launch
import java.io.File

enum class DocMode { IMG_TO_PDF, PDF_TO_IMG, MERGE, SPLIT, COMPRESS, WATERMARK, PASSWORD, TO_WORD }

sealed class DocUiState {
    object Idle : DocUiState()
    data class Running(val progress: Int, val message: String) : DocUiState()
    data class Success(val paths: List<String>) : DocUiState()
    data class Error(val msg: String) : DocUiState()
}

class DocumentViewModel : ViewModel() {
    var mode = DocMode.IMG_TO_PDF
    val uris = mutableListOf<Uri>()
    var quality = 85; var splitStart = 0; var splitEnd = 0
    var watermarkText = "CONFIDENTIAL"; var pdfPassword = ""
    private val _state = androidx.lifecycle.MutableLiveData<DocUiState>(DocUiState.Idle)
    val state: androidx.lifecycle.LiveData<DocUiState> = _state

    fun run(context: android.content.Context) {
        if (uris.isEmpty()) return
        _state.value = DocUiState.Running(0, "Starting…")
        viewModelScope.launch {
            val result: List<String>? = when (mode) {
                DocMode.IMG_TO_PDF  -> PdfConverter.imagesToPdf(context, uris, "converted", quality) { p,m -> _state.postValue(DocUiState.Running(p,m)) }?.let { listOf(it) }
                DocMode.PDF_TO_IMG  -> PdfConverter.pdfToImages(context, uris.first(), "jpg", quality, 150) { p,m -> _state.postValue(DocUiState.Running(p,m)) }
                DocMode.MERGE       -> PdfConverter.mergePdfs(context, uris) { p,m -> _state.postValue(DocUiState.Running(p,m)) }?.let { listOf(it) }
                DocMode.COMPRESS    -> PdfConverter.compressPdf(context, uris.first(), 96) { p,m -> _state.postValue(DocUiState.Running(p,m)) }?.let { listOf(it) }
                DocMode.SPLIT       -> PdfConverter.splitPdf(context, uris.first(), listOf(0..splitEnd, splitEnd+1..99)) { p,m -> _state.postValue(DocUiState.Running(p,m)) }
                DocMode.WATERMARK   -> PdfConverter.addWatermark(context, uris.first(), watermarkText) { p,m -> _state.postValue(DocUiState.Running(p,m)) }?.let { listOf(it) }
                DocMode.PASSWORD    -> PdfConverter.passwordProtect(context, uris.first(), pdfPassword) { p,m -> _state.postValue(DocUiState.Running(p,m)) }?.let { listOf(it) }
                DocMode.TO_WORD     -> PdfConverter.pdfToWord(context, uris.first()) { p,m -> _state.postValue(DocUiState.Running(p,m)) }?.let { listOf(it) }
            }
            if (result != null) _state.value = DocUiState.Success(result)
            else _state.value = DocUiState.Error("Operation failed")
        }
    }
}

class DocumentFragment : Fragment() {
    private var _b: FragmentDocumentBinding? = null
    private val b get() = _b!!
    private val vm: DocumentViewModel by viewModels()

    private val pickFiles = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        vm.uris.clear(); vm.uris.addAll(uris)
        b.tvFiles.text = "${uris.size} file(s) selected"
        b.btnRun.isEnabled = uris.isNotEmpty()
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentDocumentBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTabs(); setupButtons(); observeState()
    }

    private fun setupTabs() {
        val modes = listOf("Image→PDF","PDF→Images","Merge","Split","Compress","Watermark","Password","To Word")
        modes.forEach { b.tabs.addTab(b.tabs.newTab().setText(it)) }
        b.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                vm.mode = DocMode.values()[tab?.position ?: 0]
                vm.uris.clear(); b.tvFiles.text = "No files selected"; b.btnRun.isEnabled = false
                updateExtraControls()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun updateExtraControls() {
        b.layoutWatermark.isVisible = vm.mode == DocMode.WATERMARK
        b.layoutPassword.isVisible  = vm.mode == DocMode.PASSWORD
        b.layoutSplit.isVisible     = vm.mode == DocMode.SPLIT
        b.layoutQuality.isVisible   = vm.mode == DocMode.IMG_TO_PDF || vm.mode == DocMode.PDF_TO_IMG
    }

    private fun setupButtons() {
        b.btnPickFiles.setOnClickListener {
            val mime = if (vm.mode == DocMode.IMG_TO_PDF) "image/*" else "application/pdf"
            pickFiles.launch(mime)
        }
        b.btnRun.setOnClickListener {
            vm.watermarkText = b.etWatermark.text.toString().ifEmpty { "CONFIDENTIAL" }
            vm.pdfPassword   = b.etPassword.text.toString()
            vm.quality       = b.sliderQuality.value.toInt()
            vm.splitEnd      = b.etSplitEnd.text.toString().toIntOrNull()?.minus(1) ?: 0
            vm.run(requireContext())
        }
        b.sliderQuality.addOnChangeListener { _, v, _ -> b.tvQualityVal.text = "${v.toInt()}%" }
        b.sliderQuality.value = 85f; b.tvQualityVal.text = "85%"
    }

    private fun observeState() {
        vm.state.observe(viewLifecycleOwner) { st ->
            when (st) {
                is DocUiState.Idle -> { b.progressBar.isVisible=false; b.tvStatus.text=""; b.cardResult.isVisible=false }
                is DocUiState.Running -> { b.progressBar.isVisible=true; b.progressBar.progress=st.progress; b.tvStatus.text=st.message; b.cardResult.isVisible=false }
                is DocUiState.Success -> {
                    b.progressBar.isVisible=false; b.btnRun.isEnabled=true
                    b.tvStatus.text = "✓ ${st.paths.size} file(s) created"
                    b.cardResult.isVisible = true
                    b.tvResultFiles.text = st.paths.joinToString("\n") { File(it).name }
                    b.btnShare.setOnClickListener { share(st.paths) }
                }
                is DocUiState.Error -> { b.progressBar.isVisible=false; b.btnRun.isEnabled=true; Snackbar.make(b.root,st.msg,Snackbar.LENGTH_LONG).show() }
            }
        }
    }

    private fun share(paths: List<String>) {
        val uris = paths.mapNotNull { runCatching { FileProvider.getUriForFile(requireContext(),"${requireContext().packageName}.fileprovider",File(it)) }.getOrNull() }
        if (uris.isEmpty()) return
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type="*/*"; putParcelableArrayListExtra(Intent.EXTRA_STREAM,ArrayList(uris)); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },"Share"))
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
