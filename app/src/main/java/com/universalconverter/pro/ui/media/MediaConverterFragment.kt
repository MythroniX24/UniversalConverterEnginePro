package com.universalconverter.pro.ui.media

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
import com.universalconverter.pro.databinding.FragmentMediaBinding
import com.universalconverter.pro.engine.FileDetector
import com.universalconverter.pro.engine.MediaConverter
import kotlinx.coroutines.launch
import java.io.File

// ─── ViewModel ───────────────────────────────────────────────────────────────
class MediaViewModel : ViewModel() {
    var selectedUri: Uri? = null
    var mediaInfo: MediaConverter.MediaInfo? = null
    private val _state = androidx.lifecycle.MutableLiveData<MediaUiState>(MediaUiState.Idle)
    val state: androidx.lifecycle.LiveData<MediaUiState> = _state

    fun loadMedia(context: android.content.Context, uri: Uri) {
        selectedUri = uri
        viewModelScope.launch {
            _state.postValue(MediaUiState.Loading)
            val info = MediaConverter.getMediaInfo(context, uri)
            mediaInfo = info
            _state.postValue(MediaUiState.FileLoaded(info))
        }
    }

    fun extractAudio(context: android.content.Context, format: String) {
        val uri = selectedUri ?: return
        _state.value = MediaUiState.Running(0, "Starting extraction…")
        viewModelScope.launch {
            val path = MediaConverter.extractAudio(context, uri, format) { p, m ->
                _state.postValue(MediaUiState.Running(p, m))
            }
            if (path != null) _state.postValue(MediaUiState.Success(path))
            else _state.postValue(MediaUiState.Error("Audio extraction failed"))
        }
    }
}

sealed class MediaUiState {
    object Idle : MediaUiState()
    object Loading : MediaUiState()
    data class FileLoaded(val info: MediaConverter.MediaInfo?) : MediaUiState()
    data class Running(val progress: Int, val message: String) : MediaUiState()
    data class Success(val outputPath: String) : MediaUiState()
    data class Error(val message: String) : MediaUiState()
}

// ─── Fragment ────────────────────────────────────────────────────────────────
class MediaConverterFragment : Fragment() {

    private var _binding: FragmentMediaBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MediaViewModel by viewModels()

    private val pickVideo = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        viewModel.loadMedia(requireContext(), uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMediaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupButtons()
        observeState()
    }

    private fun setupButtons() {
        binding.btnPickMedia.setOnClickListener { pickVideo.launch("video/*") }

        binding.btnExtractAudio.setOnClickListener {
            val format = when (binding.radioGroupAudio.checkedRadioButtonId) {
                com.universalconverter.pro.R.id.radioAac  -> "aac"
                com.universalconverter.pro.R.id.radioMp3  -> "mp3"
                com.universalconverter.pro.R.id.radioWav  -> "wav"
                else -> "aac"
            }
            viewModel.extractAudio(requireContext(), format)
        }
    }

    private fun observeState() {
        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is MediaUiState.Idle -> {
                    binding.cardMediaInfo.isVisible = false
                    binding.progressBar.isVisible   = false
                    binding.cardResult.isVisible    = false
                }
                is MediaUiState.Loading -> {
                    binding.progressBar.isVisible   = true
                    binding.tvProgress.text         = "Loading…"
                }
                is MediaUiState.FileLoaded -> {
                    binding.progressBar.isVisible   = false
                    binding.cardMediaInfo.isVisible = true
                    val info = state.info
                    if (info != null) {
                        binding.tvMediaDuration.text   = "Duration: ${info.durationFormatted}"
                        binding.tvMediaResolution.text = "Resolution: ${info.resolutionLabel}"
                        binding.tvMediaBitrate.text    = "Bitrate: ${info.bitRate / 1000} Kbps"
                        binding.tvMediaHasAudio.text   = "Audio: ${if (info.hasAudio) "Yes" else "No"}"
                        binding.btnExtractAudio.isEnabled = info.hasAudio
                    }
                }
                is MediaUiState.Running -> {
                    binding.progressBar.isVisible    = true
                    binding.progressBar.progress     = state.progress
                    binding.tvProgress.text          = state.message
                    binding.btnExtractAudio.isEnabled = false
                    binding.cardResult.isVisible     = false
                }
                is MediaUiState.Success -> {
                    binding.progressBar.isVisible    = false
                    binding.btnExtractAudio.isEnabled = true
                    binding.tvProgress.text          = "✓ Audio extracted!"
                    binding.cardResult.isVisible     = true
                    binding.tvResultFile.text        = File(state.outputPath).name
                    val size = File(state.outputPath).length()
                    binding.tvResultSize.text        = FileDetector.formatSize(size)
                    binding.btnShareAudio.setOnClickListener { shareFile(state.outputPath) }
                }
                is MediaUiState.Error -> {
                    binding.progressBar.isVisible = false
                    binding.btnExtractAudio.isEnabled = true
                    Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun shareFile(path: String) {
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                File(path)
            )
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "audio/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Share Audio"
            ))
        } catch (_: Exception) {}
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
