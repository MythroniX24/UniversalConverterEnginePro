package com.universalconverter.pro.ui.image

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.universalconverter.pro.engine.*
import kotlinx.coroutines.launch

class ImageViewModel : ViewModel() {

    private val _selectedFiles = MutableLiveData<List<FileInfo>>(emptyList())
    val selectedFiles: LiveData<List<FileInfo>> = _selectedFiles

    private val _jobs = MutableLiveData<List<ConversionJob>>(emptyList())
    val jobs: LiveData<List<ConversionJob>> = _jobs

    private val _uiState = MutableLiveData<ImageUiState>(ImageUiState.Idle)
    val uiState: LiveData<ImageUiState> = _uiState

    var selectedOutputFormat: String = "webp"
    var quality: Int      = 85
    var targetWidth: Int  = 0
    var targetHeight: Int = 0
    var keepAspect: Boolean = true
    var removeExif: Boolean = false
    var rotation: Int     = 0
    var targetSizeKb: Long = 0L

    fun addFiles(context: Context, uris: List<Uri>) {
        val newInfos = uris.map { FileDetector.analyze(context, it) }
            .filter { it.category == FileCategory.IMAGE }
        val current = _selectedFiles.value?.toMutableList() ?: mutableListOf()
        current.addAll(newInfos)
        _selectedFiles.value = current

        // Set suggested format from first file
        newInfos.firstOrNull()?.let {
            selectedOutputFormat = it.suggestedFormat
        }
    }

    fun removeFile(info: FileInfo) {
        val updated = _selectedFiles.value?.filter { it.uri != info.uri } ?: return
        _selectedFiles.value = updated
    }

    fun clearFiles() {
        _selectedFiles.value = emptyList()
    }

    fun startConversion(context: Context) {
        val files = _selectedFiles.value ?: return
        if (files.isEmpty()) return

        _uiState.value = ImageUiState.Running(0, "Starting…")

        viewModelScope.launch {
            val results = mutableListOf<ConversionJob>()
            files.forEachIndexed { index, fileInfo ->
                val job = ConversionJob(
                    inputUri       = fileInfo.uri,
                    inputName      = fileInfo.name,
                    inputSizeBytes = fileInfo.sizeBytes,
                    outputFormat   = selectedOutputFormat,
                    quality        = quality,
                    width          = targetWidth,
                    height         = targetHeight,
                    keepAspectRatio = keepAspect,
                    removeExif     = removeExif,
                    rotation       = rotation,
                    targetSizeBytes = targetSizeKb * 1024L
                )

                val baseProgress = (index * 100 / files.size)
                val result = ImageConverter.convert(context, job) { progress, msg ->
                    val overall = baseProgress + (progress / files.size)
                    _uiState.postValue(
                        ImageUiState.Running(overall,
                            "[${index + 1}/${files.size}] $msg")
                    )
                }
                results.add(result)
            }

            _jobs.postValue(results)
            val failed = results.count { it.status == JobStatus.FAILED }
            _uiState.postValue(
                if (failed == 0) ImageUiState.Success(results)
                else ImageUiState.PartialSuccess(results, failed)
            )
        }
    }

    fun cancelConversion() {
        NativeEngine.cancelOperation()
        _uiState.value = ImageUiState.Idle
    }
}

sealed class ImageUiState {
    object Idle : ImageUiState()
    data class Running(val progress: Int, val message: String) : ImageUiState()
    data class Success(val jobs: List<ConversionJob>) : ImageUiState()
    data class PartialSuccess(val jobs: List<ConversionJob>, val failedCount: Int) : ImageUiState()
    data class Error(val message: String) : ImageUiState()
}
