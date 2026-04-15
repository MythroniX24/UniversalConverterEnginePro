package com.universalconverter.pro.ui.image

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.universalconverter.pro.engine.*
import com.universalconverter.pro.engine.privacy.MetadataNuker
import com.universalconverter.pro.engine.privacy.PrivacyScanner
import kotlinx.coroutines.launch

sealed class ImageUiState {
    object Idle : ImageUiState()
    data class Running(val progress: Int, val message: String) : ImageUiState()
    data class Success(val jobs: List<ConversionJob>) : ImageUiState()
    data class Error(val msg: String) : ImageUiState()
    data class SmartResult(val result: SmartCompressor.CompressionResult) : ImageUiState()
    data class PrivacyScan(val result: PrivacyScanner.ScanResult) : ImageUiState()
}

class ImageViewModel : ViewModel() {
    private val _files    = MutableLiveData<List<FileInfo>>(emptyList())
    private val _state    = MutableLiveData<ImageUiState>(ImageUiState.Idle)
    private val _jobs     = MutableLiveData<List<ConversionJob>>(emptyList())
    val files: LiveData<List<FileInfo>> = _files
    val state: LiveData<ImageUiState>   = _state
    val jobs:  LiveData<List<ConversionJob>> = _jobs

    var outputFormat  = "webp"
    var quality       = 85
    var targetWidth   = 0; var targetHeight = 0
    var keepAspect    = true; var removeExif = false
    var rotation      = 0; var targetSizeKb = 0L
    var compressMode  = CompressMode.BALANCED
    var useSmartCompress = false
    var upscaleFactor = 1

    fun addFiles(context: Context, uris: List<Uri>) {
        val current = _files.value?.toMutableList() ?: mutableListOf()
        uris.map { FileDetector.analyze(context, it) }
            .filter { it.category == FileCategory.IMAGE }
            .forEach { if (current.none { f -> f.uri == it.uri }) current.add(it) }
        _files.value = current
        current.firstOrNull()?.let { outputFormat = it.suggestedFormat }
    }

    fun removeFile(info: FileInfo) { _files.value = _files.value?.filter { it.uri != info.uri } }
    fun clearFiles() { _files.value = emptyList() }

    fun convert(context: Context) {
        val files = _files.value ?: return
        if (files.isEmpty()) return
        viewModelScope.launch {
            _state.value = ImageUiState.Running(0, "Starting…")
            val results = files.mapIndexed { i, info ->
                val base = i * 100 / files.size
                if (useSmartCompress) {
                    SmartCompressor.smartCompress(context, info.uri, compressMode,
                        targetSizeKb * 1024L) { p, m -> _state.postValue(ImageUiState.Running(base + p/files.size, "[${i+1}/${files.size}] $m")) }
                        .getOrNull()?.let { r ->
                            ConversionJob(inputUri=info.uri,inputName=info.name,inputSizeBytes=info.sizeBytes,
                                outputFormat=r.format,quality=r.quality,status=JobStatus.SUCCESS,
                                outputPath=r.outputPath,outputSizeBytes=r.compressedSize,ssimScore=r.ssimScore)
                        } ?: ConversionJob(inputUri=info.uri,inputName=info.name,inputSizeBytes=info.sizeBytes,status=JobStatus.FAILED)
                } else {
                    ImageConverter.convert(context, ConversionJob(
                        inputUri=info.uri,inputName=info.name,inputSizeBytes=info.sizeBytes,
                        outputFormat=outputFormat,quality=quality,width=targetWidth,height=targetHeight,
                        keepAspect=keepAspect,removeExif=removeExif,rotation=rotation,
                        targetSizeBytes=targetSizeKb*1024L,compressMode=compressMode,upscaleFactor=upscaleFactor
                    )) { p, m -> _state.postValue(ImageUiState.Running(base + p/files.size, "[${i+1}/${files.size}] $m")) }
                }
            }
            _jobs.value = results
            _state.value = if (results.all { it.status == JobStatus.SUCCESS }) ImageUiState.Success(results)
                           else ImageUiState.Error("${results.count { it.status == JobStatus.FAILED }} failed")
        }
    }

    fun compressionRace(context: Context) {
        val first = _files.value?.firstOrNull() ?: return
        viewModelScope.launch {
            _state.value = ImageUiState.Running(0, "Starting compression race…")
            SmartCompressor.compressionRace(context, first.uri, targetSizeKb * 1024L) { p, m ->
                _state.postValue(ImageUiState.Running(p, m))
            }.onSuccess { r -> _state.value = ImageUiState.SmartResult(r) }
             .onFailure { _state.value = ImageUiState.Error(it.message ?: "Race failed") }
        }
    }

    fun scanPrivacy(context: Context) {
        val first = _files.value?.firstOrNull() ?: return
        viewModelScope.launch {
            _state.value = ImageUiState.Running(20, "Scanning metadata…")
            val result = PrivacyScanner.scan(context, first.uri)
            _state.value = ImageUiState.PrivacyScan(result)
        }
    }

    fun nukeMetadata(context: Context) {
        val first = _files.value?.firstOrNull() ?: return
        viewModelScope.launch {
            _state.value = ImageUiState.Running(0, "Removing metadata…")
            MetadataNuker.nukeAll(context, first.uri) { p, m -> _state.postValue(ImageUiState.Running(p, m)) }
                ?.let { _state.value = ImageUiState.Success(listOf(
                    ConversionJob(inputUri=first.uri,inputName=first.name,status=JobStatus.SUCCESS,outputPath=it))) }
                ?: run { _state.value = ImageUiState.Error("Failed to remove metadata") }
        }
    }

    fun cancel() { NativeEngine.cancel(); _state.value = ImageUiState.Idle }
}
