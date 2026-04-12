package com.universalconverter.pro.ui.image

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
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.universalconverter.pro.databinding.FragmentImageBinding
import com.universalconverter.pro.engine.*
import com.universalconverter.pro.premium.PremiumActivity
import com.universalconverter.pro.premium.PremiumManager
import java.io.File

class ImageFragment : Fragment() {
    private var _b: FragmentImageBinding? = null
    private val b get() = _b!!
    private val vm: ImageViewModel by viewModels()

    private val pickFiles = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) vm.addFiles(requireContext(), uris)
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentImageBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupFormatChips()
        setupControls()
        observeVm()
    }

    private fun setupFormatChips() {
        val formats = listOf("WebP","JPG","PNG","BMP","GIF","TIFF","ICO","PDF")
        formats.forEach { fmt ->
            val chip = Chip(requireContext()).apply {
                text = fmt; isCheckable = true; isCheckedIconVisible = true
                if (fmt == "WebP") isChecked = true
                setOnCheckedChangeListener { _, checked -> if (checked) { vm.outputFormat = fmt.lowercase(); updatePrediction() } }
            }
            b.chipGroupFormat.addView(chip)
        }
        b.chipGroupFormat.isSingleSelection = true
    }

    private fun setupControls() {
        b.btnPickFiles.setOnClickListener { pickFiles.launch("image/*") }

        b.btnConvert.setOnClickListener {
            if (!PremiumManager.canConvert(requireContext())) { showUpgrade(); return@setOnClickListener }
            if (vm.files.value.isNullOrEmpty()) { snack("Pick at least one image"); return@setOnClickListener }
            vm.convert(requireContext())
            PremiumManager.record(requireContext())
        }

        b.btnSmartCompress.setOnClickListener {
            if (vm.files.value.isNullOrEmpty()) { snack("Pick an image first"); return@setOnClickListener }
            vm.useSmartCompress = true
            vm.compressMode = when(b.chipGroupMode.checkedChipId) {
                b.chipFast.id -> CompressMode.FAST; b.chipUltra.id -> CompressMode.ULTRA; else -> CompressMode.BALANCED
            }
            vm.convert(requireContext())
        }

        b.btnRace.setOnClickListener {
            if (vm.files.value.isNullOrEmpty()) { snack("Pick an image first"); return@setOnClickListener }
            vm.compressionRace(requireContext())
        }

        b.btnPrivacyScan.setOnClickListener {
            if (vm.files.value.isNullOrEmpty()) { snack("Pick an image first"); return@setOnClickListener }
            vm.scanPrivacy(requireContext())
        }

        b.btnNukeExif.setOnClickListener {
            if (vm.files.value.isNullOrEmpty()) { snack("Pick an image first"); return@setOnClickListener }
            vm.nukeMetadata(requireContext())
        }

        b.btnCancel.setOnClickListener { vm.cancel() }
        b.btnClear.setOnClickListener  { vm.clearFiles() }

        b.sliderQuality.addOnChangeListener { _, v, _ ->
            vm.quality = v.toInt(); b.tvQuality.text = "${v.toInt()}%"; updatePrediction()
        }
        b.sliderQuality.value = 85f; b.tvQuality.text = "85%"

        b.switchSmartCompress.setOnCheckedChangeListener { _, c -> vm.useSmartCompress = c; b.cardCompressMode.isVisible = c }
        b.switchRemoveExif.setOnCheckedChangeListener    { _, c -> vm.removeExif = c }
        b.switchKeepAspect.setOnCheckedChangeListener    { _, c -> vm.keepAspect = c }

        b.chipGroupMode.setOnCheckedStateChangeListener { _, _ ->
            vm.compressMode = when(b.chipGroupMode.checkedChipId) {
                b.chipFast.id -> CompressMode.FAST; b.chipUltra.id -> CompressMode.ULTRA; else -> CompressMode.BALANCED
            }
        }

        // Upscale
        b.spinnerUpscale.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item,
            listOf("No Upscale","2x","4x")).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        b.spinnerUpscale.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { vm.upscaleFactor = when(pos){1->2;2->4;else->1} }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun observeVm() {
        vm.files.observe(viewLifecycleOwner) { files ->
            b.tvFileCount.text = if(files.isEmpty()) "No images selected" else "${files.size} image(s) • ${FileDetector.formatSize(files.sumOf{it.sizeBytes})}"
            b.btnConvert.isEnabled = files.isNotEmpty()
            b.btnClear.isVisible   = files.isNotEmpty()
            b.btnSmartCompress.isEnabled = files.isNotEmpty()
            updatePrediction()
        }

        vm.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ImageUiState.Idle -> { b.progressBar.isVisible=false; b.btnCancel.isVisible=false; b.cardResult.isVisible=false; b.tvProgress.text="" }
                is ImageUiState.Running -> {
                    b.progressBar.isVisible=true; b.progressBar.progress=state.progress
                    b.tvProgress.text=state.message; b.btnCancel.isVisible=true
                    b.btnConvert.isEnabled=false; b.cardResult.isVisible=false
                }
                is ImageUiState.Success -> {
                    b.progressBar.isVisible=false; b.btnCancel.isVisible=false; b.btnConvert.isEnabled=true
                    b.tvProgress.text="✓ Done!"
                    showResults(state.jobs)
                }
                is ImageUiState.SmartResult -> {
                    b.progressBar.isVisible=false; b.btnConvert.isEnabled=true
                    val r = state.result
                    val ssim = if(r.ssimScore>0) " • SSIM: ${"%.3f".format(r.ssimScore)}" else ""
                    b.tvProgress.text = "✓ ${r.format.uppercase()} q${r.quality} • ↓${r.reductionPercent}%$ssim"
                    showResults(listOf(ConversionJob(inputUri=Uri.EMPTY,inputName="",outputPath=r.outputPath,
                        outputFormat=r.format,inputSizeBytes=r.originalSize,outputSizeBytes=r.compressedSize,status=JobStatus.SUCCESS)))
                }
                is ImageUiState.PrivacyScan -> {
                    b.progressBar.isVisible=false; b.tvProgress.text=state.result.summary
                    if(state.result.risks.isNotEmpty()) showPrivacyDialog(state.result)
                }
                is ImageUiState.Error -> {
                    b.progressBar.isVisible=false; b.btnConvert.isEnabled=true
                    snack("Error: ${state.msg}")
                }
            }
        }
    }

    private fun showResults(jobs: List<ConversionJob>) {
        val successful = jobs.filter { it.status == JobStatus.SUCCESS }
        if (successful.isEmpty()) return
        b.cardResult.isVisible = true
        val totalSaved = successful.sumOf { it.inputSizeBytes - it.outputSizeBytes }
        b.tvResultSummary.text = "${successful.size} converted • ${FileDetector.formatSize(totalSaved)} saved"
        val avgSsim = successful.filter{it.ssimScore>0}.map{it.ssimScore}.average()
        b.tvSsim.text = if(avgSsim.isFinite()) "Quality Score: ${"%.3f".format(avgSsim)}" else ""
        b.tvSsim.isVisible = avgSsim.isFinite()

        b.btnShare.setOnClickListener { shareFiles(successful.map{it.outputPath}) }
        b.btnOpen.setOnClickListener  { successful.firstOrNull()?.let { openFile(it.outputPath) } }
    }

    private fun showPrivacyDialog(result: com.universalconverter.pro.engine.privacy.PrivacyScanner.ScanResult) {
        val msg = result.risks.joinToString("\n") { "• ${it.category}: ${it.description}" }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Privacy Scan Results")
            .setMessage("${result.summary}\n\n$msg")
            .setPositiveButton("Remove Metadata") { _, _ -> vm.nukeMetadata(requireContext()) }
            .setNegativeButton("Dismiss", null)
            .show()
    }

    private fun updatePrediction() {
        val f = vm.files.value?.firstOrNull() ?: return
        val p = NativeEngine.predictSize(f.sizeBytes, vm.outputFormat, vm.quality)
        if (p > 0) b.tvPrediction.text = "Estimated: ~${FileDetector.formatSize(p)}"
    }

    private fun shareFiles(paths: List<String>) {
        val uris = paths.mapNotNull { runCatching { FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", File(it)) }.getOrNull() }
        if (uris.isEmpty()) return
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type="*/*"; putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris)); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share"))
    }

    private fun openFile(path: String) = runCatching {
        val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", File(path))
        startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "image/*"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) })
    }

    private fun showUpgrade() = MaterialAlertDialogBuilder(requireContext())
        .setTitle("Daily Limit Reached")
        .setMessage("Upgrade to Pro for unlimited conversions!")
        .setPositiveButton("Upgrade") { _, _ -> startActivity(Intent(requireContext(), PremiumActivity::class.java)) }
        .setNegativeButton("Later", null).show()

    private fun snack(msg: String) = Snackbar.make(b.root, msg, Snackbar.LENGTH_SHORT).show()

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
