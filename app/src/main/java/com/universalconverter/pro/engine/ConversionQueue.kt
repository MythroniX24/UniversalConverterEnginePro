package com.universalconverter.pro.engine

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedDeque

object ConversionQueue {

    private val _jobs = MutableLiveData<List<ConversionJob>>(emptyList())
    val jobs: LiveData<List<ConversionJob>> = _jobs

    private val queue = ConcurrentLinkedDeque<ConversionJob>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isProcessing = false

    fun enqueue(job: ConversionJob) {
        queue.add(job)
        updateLiveData()
        if (!isProcessing) processNext()
    }

    fun enqueueAll(jobs: List<ConversionJob>) {
        jobs.forEach { queue.add(it) }
        updateLiveData()
        if (!isProcessing) processNext()
    }

    fun cancelJob(jobId: String) {
        val updated = _jobs.value?.map {
            if (it.id == jobId && !it.isComplete)
                it.copy(status = JobStatus.CANCELLED, statusMessage = "Cancelled")
            else it
        } ?: return
        _jobs.postValue(updated)
        NativeEngine.cancelOperation()
    }

    fun cancelAll() {
        val updated = _jobs.value?.map {
            if (!it.isComplete)
                it.copy(status = JobStatus.CANCELLED, statusMessage = "Cancelled")
            else it
        } ?: return
        _jobs.postValue(updated)
        queue.clear()
        NativeEngine.cancelOperation()
    }

    fun clearCompleted() {
        val updated = _jobs.value?.filter { !it.isComplete } ?: return
        _jobs.postValue(updated)
    }

    fun updateJob(job: ConversionJob) {
        val current = _jobs.value?.toMutableList() ?: mutableListOf()
        val idx = current.indexOfFirst { it.id == job.id }
        if (idx >= 0) current[idx] = job else current.add(job)
        _jobs.postValue(current)
    }

    private fun processNext() {
        val next = queue.poll() ?: run { isProcessing = false; return }
        isProcessing = true
        updateJob(next.copy(status = JobStatus.RUNNING, statusMessage = "Processing…"))
        // Actual processing delegated to ConversionWorker via ViewModel
    }

    private fun updateLiveData() {
        val all = _jobs.value?.toMutableList() ?: mutableListOf()
        queue.forEach { job ->
            if (all.none { it.id == job.id }) all.add(job)
        }
        _jobs.postValue(all)
    }
}
