package com.universalconverter.pro.ui.media

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
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.universalconverter.pro.databinding.FragmentMediaBinding
import com.universalconverter.pro.engine.FileDetector
import com.universalconverter.pro.engine.MediaConverter
import kotlinx.coroutines.launch
import java.io.File

enum class MediaMode { EXTRACT_AUDIO, VIDEO_TO_GIF, COMPRESS_VIDEO, SUBTITLES, INFO }

sealed class MediaUiState {
    object Idle : MediaUiState()
    object Loading : MediaUiState()
    data class InfoLoaded(val info: MediaConverter.MediaInfo?) : MediaUiState()
    data class Running(val progress: Int, val message: String) : MediaUiState()
    data class Success(val outputPath: String) : MediaUiState()
    data class Error(val msg: String) : MediaUiState()
}

class MediaViewModel : ViewModel() {
    var selectedUri: Uri? = null
    var mode = MediaMode.EXTRACT_AUDIO
    var audioFormat = "aac"
    var gifDuration = 5; var gifFps = 10; var gifWidth = 480
    var videoBitrateKbps = 1000

    private val _state = androidx.lifecycle.MutableLiveData<MediaUiState>(MediaUiState.Idle)
    val state: androidx.lifecycle.LiveData<MediaUiState> = _state

    fun loadMedia(context: android.content.Context, uri: Uri) {
        selectedUri = uri
        _state.value = MediaUiState.Loading
        viewModelScope.launch {
            val info = MediaConverter.getInfo(context, uri)
            _state.value = MediaUiState.InfoLoaded(info)
        }
    }

    fun run(context: android.content.Context) {
        val uri = selectedUri ?: return
        _state.value = MediaUiState.Running(0, "Starting…")
        viewModelScope.launch {
            val path = when(mode) {
                MediaMode.EXTRACT_AUDIO  -> MediaConverter.extractAudio(context, uri, audioFormat) { p,m -> _state.postValue(MediaUiState.Running(p,m)) }
                MediaMode.VIDEO_TO_GIF   -> MediaConverter.videoToGif(context, uri, gifDuration, gifFps, gifWidth) { p,m -> _state.postValue(MediaUiState.Running(p,m)) }
                MediaMode.COMPRESS_VIDEO -> MediaConverter.compressVideo(context, uri, videoBitrateKbps) { p,m -> _state.postValue(MediaUiState.Running(p,m)) }
                MediaMode.SUBTITLES      -> MediaConverter.extractSubtitles(context, uri) { p,m -> _state.postValue(MediaUiState.Running(p,m)) }
                MediaMode.INFO           -> null
            }
            if (path != null) _state.value = MediaUiState.Success(path)
            else _state.value = MediaUiState.Error("Operation failed or no data found")
        }
    }
}

class MediaFragment : Fragment() {
    private var _b: FragmentMediaBinding? = null
    private val b get() = _b!!
    private val vm: MediaViewModel by viewModels()

    private val pickVideo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.loadMedia(requireContext(), it) }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentMediaBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTabs(); setupButtons(); observeVm()
    }

    private fun setupTabs() {
        b.tabsMode.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(t: TabLayout.Tab?) {
                vm.mode = MediaMode.values()[t?.position ?: 0]; updateModeUI()
            }
            override fun onTabUnselected(t: TabLayout.Tab?) {}
            override fun onTabReselected(t: TabLayout.Tab?) {}
        })
    }

    private fun updateModeUI() {
        b.cardAudioOptions.isVisible     = vm.mode == MediaMode.EXTRACT_AUDIO
        b.cardGifOptions.isVisible       = vm.mode == MediaMode.VIDEO_TO_GIF
        b.cardCompressOptions.isVisible  = vm.mode == MediaMode.COMPRESS_VIDEO
    }

    private fun setupButtons() {
        b.btnPickVideo.setOnClickListener { pickVideo.launch("video/*") }
        b.btnRun.setOnClickListener {
            vm.audioFormat = when(b.rgAudioFmt.checkedRadioButtonId) {
                b.radioMp3.id -> "mp3"; b.radioWav.id -> "wav"; else -> "aac"
            }
            vm.gifDuration = b.sliderGifDuration.value.toInt()
            vm.gifFps      = b.sliderGifFps.value.toInt()
            vm.gifWidth    = b.sliderGifWidth.value.toInt()
            vm.videoBitrateKbps = b.sliderVideoBitrate.value.toInt()
            vm.run(requireContext())
        }
    }

    private fun observeVm() {
        vm.state.observe(viewLifecycleOwner) { state ->
            when(state) {
                is MediaUiState.Idle    -> { b.progressBar.isVisible=false; b.cardResult.isVisible=false; b.cardInfo.isVisible=false }
                is MediaUiState.Loading -> { b.progressBar.isVisible=true; b.tvProgress.text="Loading media info…" }
                is MediaUiState.InfoLoaded -> {
                    b.progressBar.isVisible=false
                    val info = state.info
                    if (info != null) {
                        b.cardInfo.isVisible=true
                        b.tvDuration.text   = "Duration: ${info.durationFmt}"
                        b.tvResolution.text = "Resolution: ${info.resolution}"
                        b.tvBitrate.text    = "Bitrate: ${info.bitrateKbps} Kbps"
                        b.tvHasAudio.text   = "Audio: ${if(info.hasAudio) "✓" else "✗"}"
                        b.tvHasVideo.text   = "Video: ${if(info.hasVideo) "✓" else "✗"}"
                        b.btnRun.isEnabled  = true
                    }
                }
                is MediaUiState.Running -> {
                    b.progressBar.isVisible=true; b.progressBar.progress=state.progress
                    b.tvProgress.text=state.message; b.btnRun.isEnabled=false; b.cardResult.isVisible=false
                }
                is MediaUiState.Success -> {
                    b.progressBar.isVisible=false; b.btnRun.isEnabled=true
                    b.tvProgress.text="✓ Done!"
                    b.cardResult.isVisible=true
                    val f = File(state.outputPath)
                    b.tvResultFile.text = f.name
                    b.tvResultSize.text = FileDetector.formatSize(f.length())
                    b.btnShare.setOnClickListener {
                        runCatching {
                            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", f)
                            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type="*/*"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }, "Share"))
                        }
                    }
                }
                is MediaUiState.Error -> {
                    b.progressBar.isVisible=false; b.btnRun.isEnabled=true
                    Snackbar.make(b.root, state.msg, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
