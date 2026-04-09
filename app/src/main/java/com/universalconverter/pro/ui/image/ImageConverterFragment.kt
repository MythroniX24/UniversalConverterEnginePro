package com.universalconverter.pro.ui.image

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.universalconverter.pro.R
import com.universalconverter.pro.databinding.FragmentImageBinding
import com.universalconverter.pro.engine.FileDetector
import com.universalconverter.pro.engine.JobStatus
import com.universalconverter.pro.premium.PremiumManager
import java.io.File

class ImageConverterFragment : Fragment() {

    private var _binding: FragmentImageBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ImageViewModel by viewModels()

    // ─── File picker launcher ─────────────────────────────────────────────────
    private val pickImages = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addFiles(requireContext(), uris)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupFormatSpinner()
        setupQualitySlider()
        setupButtons()
        observeViewModel()
    }

    private fun setupFormatSpinner() {
        val formats = listOf("webp", "jpg", "png", "bmp", "pdf")
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            formats.map { it.uppercase() }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spinnerFormat.adapter = adapter
        binding.spinnerFormat.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                viewModel.selectedOutputFormat = formats[pos]
                updatePrediction()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun setupQualitySlider() {
        binding.sliderQuality.addOnChangeListener { _, value, _ ->
            viewModel.quality = value.toInt()
            binding.tvQualityValue.text = "${value.toInt()}%"
            updatePrediction()
        }
        binding.sliderQuality.value = 85f
        binding.tvQualityValue.text = "85%"
    }

    private fun setupButtons() {
        binding.btnPickFiles.setOnClickListener {
            pickImages.launch("image/*")
        }

        binding.btnConvert.setOnClickListener {
            if (!PremiumManager.canConvert(requireContext())) {
                showUpgradeDialog()
                return@setOnClickListener
            }
            val files = viewModel.selectedFiles.value ?: return@setOnClickListener
            if (files.isEmpty()) {
                Snackbar.make(binding.root, "Please select at least one image", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.startConversion(requireContext())
        }

        binding.btnCancel.setOnClickListener {
            viewModel.cancelConversion()
        }

        binding.btnClear.setOnClickListener {
            viewModel.clearFiles()
        }

        binding.switchRemoveExif.setOnCheckedChangeListener { _, checked ->
            viewModel.removeExif = checked
        }

        binding.switchKeepAspect.setOnCheckedChangeListener { _, checked ->
            viewModel.keepAspect = checked
        }
    }

    private fun observeViewModel() {
        viewModel.selectedFiles.observe(viewLifecycleOwner) { files ->
            binding.tvFileCount.text = if (files.isEmpty()) "No files selected"
                                       else "${files.size} file(s) selected"
            binding.tvTotalSize.text = if (files.isEmpty()) ""
                                       else "Total: ${FileDetector.formatSize(files.sumOf { it.sizeBytes })}"
            binding.btnConvert.isEnabled = files.isNotEmpty()
            binding.btnClear.isVisible   = files.isNotEmpty()
            updatePrediction()
        }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ImageUiState.Idle -> {
                    binding.progressBar.isVisible   = false
                    binding.btnCancel.isVisible     = false
                    binding.btnConvert.isEnabled    = true
                    binding.tvProgress.text         = ""
                    binding.cardResults.isVisible   = false
                }
                is ImageUiState.Running -> {
                    binding.progressBar.isVisible   = true
                    binding.progressBar.progress    = state.progress
                    binding.tvProgress.text         = state.message
                    binding.btnCancel.isVisible     = true
                    binding.btnConvert.isEnabled    = false
                    binding.cardResults.isVisible   = false
                }
                is ImageUiState.Success -> {
                    binding.progressBar.isVisible   = false
                    binding.btnCancel.isVisible     = false
                    binding.btnConvert.isEnabled    = true
                    binding.tvProgress.text         = "✓ All conversions complete"
                    showResults(state.jobs.map {
                        ResultItem(it.outputPath, it.inputSizeBytes, it.outputSizeBytes)
                    })
                    PremiumManager.recordConversion(requireContext())
                }
                is ImageUiState.PartialSuccess -> {
                    binding.progressBar.isVisible = false
                    binding.btnCancel.isVisible   = false
                    binding.btnConvert.isEnabled  = true
                    binding.tvProgress.text = "⚠ ${state.jobs.size - state.failedCount} succeeded, ${state.failedCount} failed"
                    showResults(state.jobs
                        .filter { it.status == JobStatus.SUCCESS }
                        .map { ResultItem(it.outputPath, it.inputSizeBytes, it.outputSizeBytes) }
                    )
                }
                is ImageUiState.Error -> {
                    binding.progressBar.isVisible = false
                    Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showResults(results: List<ResultItem>) {
        if (results.isEmpty()) return
        binding.cardResults.isVisible = true
        binding.tvResultCount.text    = "${results.size} file(s) converted"
        val totalSaved = results.sumOf { it.inputSize - it.outputSize }
        binding.tvSizeSaved.text = "Saved ${FileDetector.formatSize(totalSaved)}"

        binding.btnShareResults.setOnClickListener {
            shareFiles(results.map { it.outputPath })
        }
        binding.btnOpenFolder.setOnClickListener {
            results.firstOrNull()?.let { openFile(it.outputPath) }
        }
    }

    private fun updatePrediction() {
        val files = viewModel.selectedFiles.value ?: return
        if (files.isEmpty()) {
            binding.tvPrediction.text = ""
            return
        }
        val firstFile = files.first()
        val predicted = com.universalconverter.pro.engine.NativeEngine.predictOutputSize(
            firstFile.uri.path ?: "", viewModel.selectedOutputFormat, viewModel.quality
        )
        if (predicted > 0) {
            binding.tvPrediction.text = "Est. output: ~${FileDetector.formatSize(predicted)}"
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

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share Converted Files"))
    }

    private fun openFile(path: String) {
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                File(path)
            )
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, requireContext().contentResolver.getType(uri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) {}
    }

    private fun showUpgradeDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Daily Limit Reached")
            .setMessage("You've used all free conversions for today. Upgrade to Pro for unlimited conversions!")
            .setPositiveButton("Upgrade") { _, _ ->
                startActivity(Intent(requireContext(),
                    com.universalconverter.pro.premium.PremiumActivity::class.java))
            }
            .setNegativeButton("Later", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    data class ResultItem(
        val outputPath: String,
        val inputSize: Long,
        val outputSize: Long
    )
}
