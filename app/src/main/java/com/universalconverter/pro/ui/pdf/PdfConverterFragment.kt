package com.universalconverter.pro.ui.pdf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.universalconverter.pro.R
import com.universalconverter.pro.databinding.FragmentPdfBinding
import com.universalconverter.pro.engine.FileCategory
import com.universalconverter.pro.engine.FileDetector
import com.universalconverter.pro.engine.PdfConverter
import kotlinx.coroutines.launch
import java.io.File

// ─── ViewModel ───────────────────────────────────────────────────────────────
class PdfViewModel : ViewModel() {
    var selectedUris = mutableListOf<Uri>()
    var selectedMode = PdfMode.IMAGE_TO_PDF

    private val _state = androidx.lifecycle.MutableLiveData<PdfUiState>(PdfUiState.Idle)
    val state: androidx.lifecycle.LiveData<PdfUiState> = _state

    fun runConversion(context: android.content.Context, quality: Int) {
        if (selectedUris.isEmpty()) return
        _state.value = PdfUiState.Running(0, "Starting…")

        viewModelScope.launch {
            val result = when (selectedMode) {
                PdfMode.IMAGE_TO_PDF -> {
                    PdfConverter.imagesToPdf(context, selectedUris, "converted", quality) { p, m ->
                        _state.postValue(PdfUiState.Running(p, m))
                    }?.let { listOf(it) }
                }
                PdfMode.PDF_TO_IMAGE -> {
                    val paths = PdfConverter.pdfToImages(context, selectedUris.first(), "jpg", quality) { p, m ->
                        _state.postValue(PdfUiState.Running(p, m))
                    }
                    if (paths.isNotEmpty()) paths else null
                }
                PdfMode.COMPRESS -> {
                    PdfConverter.compressPdf(context, selectedUris.first(), quality) { p, m ->
                        _state.postValue(PdfUiState.Running(p, m))
                    }?.let { listOf(it) }
                }
                PdfMode.MERGE -> {
                    PdfConverter.mergePdfs(context, selectedUris) { p, m ->
                        _state.postValue(PdfUiState.Running(p, m))
                    }?.let { listOf(it) }
                }
            }
            if (result != null) _state.postValue(PdfUiState.Success(result))
            else _state.postValue(PdfUiState.Error("Conversion failed"))
        }
    }
}

enum class PdfMode { IMAGE_TO_PDF, PDF_TO_IMAGE, COMPRESS, MERGE }
sealed class PdfUiState {
    object Idle : PdfUiState()
    data class Running(val progress: Int, val message: String) : PdfUiState()
    data class Success(val outputPaths: List<String>) : PdfUiState()
    data class Error(val message: String) : PdfUiState()
}

// ─── Fragment ────────────────────────────────────────────────────────────────
class PdfConverterFragment : Fragment() {

    private var _binding: FragmentPdfBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PdfViewModel by viewModels()

    private val pickFiles = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.selectedUris.clear()
            viewModel.selectedUris.addAll(uris)
            updateFileLabel()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPdfBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupModeTabs()
        setupButtons()
        observeState()
    }

    private fun setupModeTabs() {
        binding.tabsPdfMode.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                viewModel.selectedMode = when (tab?.position) {
                    0 -> PdfMode.IMAGE_TO_PDF
                    1 -> PdfMode.PDF_TO_IMAGE
                    2 -> PdfMode.COMPRESS
                    3 -> PdfMode.MERGE
                    else -> PdfMode.IMAGE_TO_PDF
                }
                viewModel.selectedUris.clear()
                updateFileLabel()
                updatePickMimeType()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun updatePickMimeType() {
        // No-op: mimeType determined at pick time
    }

    private fun setupButtons() {
        binding.btnPickFiles.setOnClickListener {
            val mime = when (viewModel.selectedMode) {
                PdfMode.IMAGE_TO_PDF -> "image/*"
                else -> "application/pdf"
            }
            pickFiles.launch(mime)
        }

        binding.btnConvert.setOnClickListener {
            val quality = binding.sliderQuality.value.toInt()
            viewModel.runConversion(requireContext(), quality)
        }
    }

    private fun updateFileLabel() {
        val count = viewModel.selectedUris.size
        binding.tvFilesSelected.text = if (count == 0) "No files selected"
                                       else "$count file(s) selected"
        binding.btnConvert.isEnabled = count > 0
    }

    private fun observeState() {
        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is PdfUiState.Idle -> {
                    binding.progressBar.isVisible = false
                    binding.tvProgress.text       = ""
                    binding.cardResult.isVisible  = false
                }
                is PdfUiState.Running -> {
                    binding.progressBar.isVisible    = true
                    binding.progressBar.progress     = state.progress
                    binding.tvProgress.text          = state.message
                    binding.btnConvert.isEnabled     = false
                    binding.cardResult.isVisible     = false
                }
                is PdfUiState.Success -> {
                    binding.progressBar.isVisible = false
                    binding.btnConvert.isEnabled  = true
                    binding.tvProgress.text       = "✓ Done — ${state.outputPaths.size} file(s)"
                    binding.cardResult.isVisible  = true
                    binding.tvResultPath.text     = state.outputPaths.joinToString("\n") {
                        File(it).name
                    }
                    binding.btnShare.setOnClickListener { shareFiles(state.outputPaths) }
                }
                is PdfUiState.Error -> {
                    binding.progressBar.isVisible = false
                    binding.btnConvert.isEnabled  = true
                    Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun shareFiles(paths: List<String>) {
        val uris = paths.mapNotNull { path ->
            try {
                FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    File(path)
                )
            } catch (_: Exception) { null }
        }
        if (uris.isEmpty()) return
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Share"
        ))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
