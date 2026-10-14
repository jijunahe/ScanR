package com.scanr.app.viewmodel

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import com.scanr.app.data.PointCloud
import com.scanr.app.data.ScanSession
import com.scanr.app.data.ScanStatus
import com.scanr.app.export.ExportFileResult
import com.scanr.app.export.ExportFormat
import com.scanr.app.export.ExportOptions
import com.scanr.app.export.MeshExportManager
import com.scanr.app.mesh.MeshExtractor
import com.scanr.app.mesh.PointCloudStatistics
import com.scanr.app.ui.screens.ExportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "ScannerViewModel"

class ScannerViewModel : ViewModel() {
    
    private val meshExtractor = MeshExtractor(
        MeshExtractor.MeshExtractorConfig(
            minDepth = 0.15f,
            maxDepth = 3.5f,
            confidenceThreshold = 0.35f,
            samplingStride = 4,
            frameSkipCount = 2,
            maxPointsPerFrame = 6000,
            voxelDownsampleSize = 0.006f,
            useARCorePointCloud = true,
            useDepthAPI = true
        )
    )
    
    private var exportManager: MeshExportManager? = null
    
    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()
    
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()
    
    private val _pointCount = MutableStateFlow(0)
    val pointCount: StateFlow<Int> = _pointCount.asStateFlow()
    
    private val _statistics = MutableStateFlow<PointCloudStatistics?>(null)
    val statistics: StateFlow<PointCloudStatistics?> = _statistics.asStateFlow()
    
    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()
    
    private val _lastExportResult = MutableStateFlow<ExportFileResult.Success?>(null)
    val lastExportResult: StateFlow<ExportFileResult.Success?> = _lastExportResult.asStateFlow()
    
    init {
        viewModelScope.launch {
            meshExtractor.extractionState.collect { state ->
                when (state) {
                    is MeshExtractor.ExtractionState.Idle -> {
                        _scanState.value = ScanState.Idle
                    }
                    is MeshExtractor.ExtractionState.Scanning -> {
                        _scanState.value = ScanState.Scanning
                    }
                    is MeshExtractor.ExtractionState.Processing -> {
                        _scanState.value = ScanState.Processing
                    }
                    is MeshExtractor.ExtractionState.Completed -> {
                        _scanState.value = ScanState.Completed(state.session)
                        updateStatistics()
                    }
                    is MeshExtractor.ExtractionState.Error -> {
                        _scanState.value = ScanState.Error(state.message)
                    }
                }
            }
        }
        
        viewModelScope.launch {
            meshExtractor.pointCount.collect { count ->
                _pointCount.value = count
                _uiState.value = _uiState.value.copy(pointCount = count)
            }
        }
    }
    
    fun initExportManager(context: Context) {
        if (exportManager == null) {
            exportManager = MeshExportManager(context)
        }
    }
    
    fun startScanning() {
        Log.d(TAG, "Starting scan...")
        val session = meshExtractor.startScanning()
        _uiState.value = _uiState.value.copy(
            isScanning = true,
            currentSession = session,
            pointCount = 0
        )
    }
    
    fun stopScanning() {
        Log.d(TAG, "Stopping scan...")
        val session = meshExtractor.stopScanning()
        _uiState.value = _uiState.value.copy(
            isScanning = false,
            currentSession = session
        )
        updateStatistics()
    }
    
    fun cancelScanning() {
        Log.d(TAG, "Cancelling scan...")
        meshExtractor.cancelScanning()
        _uiState.value = _uiState.value.copy(
            isScanning = false,
            currentSession = null,
            pointCount = 0
        )
    }
    
    fun toggleScanning() {
        if (_uiState.value.isScanning) {
            stopScanning()
        } else {
            startScanning()
        }
    }
    
    suspend fun onFrame(frame: Frame) {
        if (!_uiState.value.isScanning) return
        
        _uiState.value = _uiState.value.copy(
            trackingState = frame.camera.trackingState
        )
        
        if (frame.camera.trackingState == TrackingState.TRACKING) {
            meshExtractor.processFrame(frame)
        }
    }
    
    fun updateTrackingState(trackingState: TrackingState) {
        _uiState.value = _uiState.value.copy(trackingState = trackingState)
    }
    
    fun updateDepthSupport(isSupported: Boolean, isRawSupported: Boolean) {
        _uiState.value = _uiState.value.copy(
            isDepthSupported = isSupported,
            isRawDepthSupported = isRawSupported
        )
    }
    
    fun getPointCloud(): PointCloud = meshExtractor.getPointCloud()
    
    fun getDownsampledPointCloud(): PointCloud = meshExtractor.getDownsampledPointCloud()
    
    private fun updateStatistics() {
        _statistics.value = meshExtractor.getStatistics()
    }
    
    fun exportPointCloud(format: ExportFormat, options: ExportOptions) {
        val manager = exportManager ?: return
        val pointCloud = getDownsampledPointCloud()
        
        if (pointCloud.isEmpty) {
            _exportState.value = ExportState.Error("No hay puntos para exportar")
            return
        }
        
        _exportState.value = ExportState.Exporting
        
        viewModelScope.launch {
            val result = manager.exportToFile(pointCloud, format, options = options)
            
            when (result) {
                is ExportFileResult.Success -> {
                    _exportState.value = ExportState.Success(result)
                    _lastExportResult.value = result
                    Log.d(TAG, "Export successful: ${result.fileName}")
                }
                is ExportFileResult.Error -> {
                    _exportState.value = ExportState.Error(result.message)
                    Log.e(TAG, "Export failed: ${result.message}")
                }
            }
        }
    }
    
    fun getShareIntent(): Intent? {
        val manager = exportManager ?: return null
        val result = _lastExportResult.value ?: return null
        return manager.createShareIntent(result)
    }
    
    fun resetExportState() {
        _exportState.value = ExportState.Idle
    }
    
    fun reset() {
        meshExtractor.reset()
        _uiState.value = ScannerUiState()
        _statistics.value = null
        _exportState.value = ExportState.Idle
        _lastExportResult.value = null
    }
    
    override fun onCleared() {
        super.onCleared()
        meshExtractor.reset()
    }
}

data class ScannerUiState(
    val isScanning: Boolean = false,
    val trackingState: TrackingState = TrackingState.STOPPED,
    val isDepthSupported: Boolean = false,
    val isRawDepthSupported: Boolean = false,
    val pointCount: Int = 0,
    val currentSession: ScanSession? = null,
    val errorMessage: String? = null
)

sealed class ScanState {
    object Idle : ScanState()
    object Scanning : ScanState()
    object Processing : ScanState()
    data class Completed(val session: ScanSession) : ScanState()
    data class Error(val message: String) : ScanState()
}
