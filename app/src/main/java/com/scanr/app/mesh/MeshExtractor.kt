package com.scanr.app.mesh

import android.util.Log
import com.google.ar.core.Frame
import com.google.ar.core.PointCloud as ARPointCloud
import com.google.ar.core.TrackingState
import com.scanr.app.data.MeshData
import com.scanr.app.data.PointCloud
import com.scanr.app.data.ScanSession
import com.scanr.app.data.ScanStatus
import com.scanr.app.data.Vector3
import com.scanr.app.data.Vertex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

private const val TAG = "MeshExtractor"

class MeshExtractor(
    private val config: MeshExtractorConfig = MeshExtractorConfig()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var extractionJob: Job? = null
    
    private val pointCloudBuilder = PointCloudBuilder(
        PointCloudBuilder.PointCloudConfig(
            minDepth = config.minDepth,
            maxDepth = config.maxDepth,
            confidenceThreshold = config.confidenceThreshold,
            samplingStride = config.samplingStride,
            frameSkipCount = config.frameSkipCount,
            maxPointsPerFrame = config.maxPointsPerFrame,
            voxelDownsampleSize = config.voxelDownsampleSize
        )
    )
    
    private var currentSession: ScanSession? = null
    
    private val _extractionState = MutableStateFlow<ExtractionState>(ExtractionState.Idle)
    val extractionState: StateFlow<ExtractionState> = _extractionState.asStateFlow()
    
    private val _pointCount = MutableStateFlow(0)
    val pointCount: StateFlow<Int> = _pointCount.asStateFlow()
    
    private val _lastExtractionResult = MutableStateFlow<ExtractionResult?>(null)
    val lastExtractionResult: StateFlow<ExtractionResult?> = _lastExtractionResult.asStateFlow()
    
    data class MeshExtractorConfig(
        val minDepth: Float = 0.1f,
        val maxDepth: Float = 4.0f,
        val confidenceThreshold: Float = 0.4f,
        val samplingStride: Int = 4,
        val frameSkipCount: Int = 3,
        val maxPointsPerFrame: Int = 8000,
        val voxelDownsampleSize: Float = 0.008f,
        val useARCorePointCloud: Boolean = true,
        val useDepthAPI: Boolean = true,
        val mergeThreshold: Float = 0.01f
    )
    
    sealed class ExtractionState {
        object Idle : ExtractionState()
        object Scanning : ExtractionState()
        object Processing : ExtractionState()
        data class Completed(val session: ScanSession) : ExtractionState()
        data class Error(val message: String) : ExtractionState()
    }
    
    data class ExtractionResult(
        val depthPointsExtracted: Int,
        val arCorePointsExtracted: Int,
        val totalPointsInCloud: Int,
        val frameNumber: Int,
        val processingTimeMs: Long
    )
    
    fun startScanning(): ScanSession {
        pointCloudBuilder.clear()
        currentSession = ScanSession(status = ScanStatus.SCANNING)
        _extractionState.value = ExtractionState.Scanning
        _pointCount.value = 0
        Log.d(TAG, "Started scanning session: ${currentSession?.id}")
        return currentSession!!
    }
    
    fun stopScanning(): ScanSession? {
        currentSession?.let { session ->
            session.finish()
            _extractionState.value = ExtractionState.Processing
            
            scope.launch {
                try {
                    val finalCloud = pointCloudBuilder.getDownsampledCloud()
                    session.meshData = MeshData.fromPointCloud(finalCloud)
                    _extractionState.value = ExtractionState.Completed(session)
                    Log.d(TAG, "Scanning completed. Total points: ${finalCloud.size}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing point cloud", e)
                    _extractionState.value = ExtractionState.Error(e.message ?: "Unknown error")
                }
            }
        }
        return currentSession
    }
    
    fun cancelScanning() {
        extractionJob?.cancel()
        currentSession?.cancel()
        pointCloudBuilder.clear()
        _extractionState.value = ExtractionState.Idle
        _pointCount.value = 0
        Log.d(TAG, "Scanning cancelled")
    }
    
    suspend fun processFrame(frame: Frame): ExtractionResult? {
        if (_extractionState.value !is ExtractionState.Scanning) {
            return null
        }
        
        if (frame.camera.trackingState != TrackingState.TRACKING) {
            return null
        }
        
        val startTime = System.currentTimeMillis()
        currentSession?.frameCount = (currentSession?.frameCount ?: 0) + 1
        
        var depthPoints = 0
        var arCorePoints = 0
        
        if (config.useDepthAPI) {
            val depthResult = pointCloudBuilder.extractFromFrame(frame)
            depthPoints = depthResult.pointsExtracted
        }
        
        if (config.useARCorePointCloud) {
            val arPointCloud = frame.acquirePointCloud()
            try {
                val extracted = extractFromARCorePointCloud(arPointCloud)
                arCorePoints = extracted
            } finally {
                arPointCloud.close()
            }
        }
        
        val totalPoints = pointCloudBuilder.pointCount
        _pointCount.value = totalPoints
        
        val processingTime = System.currentTimeMillis() - startTime
        
        val result = ExtractionResult(
            depthPointsExtracted = depthPoints,
            arCorePointsExtracted = arCorePoints,
            totalPointsInCloud = totalPoints,
            frameNumber = currentSession?.frameCount ?: 0,
            processingTimeMs = processingTime
        )
        
        _lastExtractionResult.value = result
        
        if (depthPoints > 0 || arCorePoints > 0) {
            Log.v(TAG, "Frame ${result.frameNumber}: depth=$depthPoints, arcore=$arCorePoints, total=$totalPoints (${processingTime}ms)")
        }
        
        return result
    }
    
    private fun extractFromARCorePointCloud(arPointCloud: ARPointCloud): Int {
        val points = arPointCloud.points
        val ids = arPointCloud.ids
        
        if (points.remaining() < 4) return 0
        
        val numPoints = points.remaining() / 4
        var extracted = 0
        
        val existingIds = mutableSetOf<Int>()
        
        for (i in 0 until minOf(numPoints, config.maxPointsPerFrame / 2)) {
            val offset = i * 4
            val x = points.get(offset)
            val y = points.get(offset + 1)
            val z = points.get(offset + 2)
            val confidence = points.get(offset + 3)
            
            if (confidence < config.confidenceThreshold) continue
            
            val id = if (ids.remaining() > i) ids.get(i) else i
            if (existingIds.contains(id)) continue
            existingIds.add(id)
            
            val position = Vector3(x, y, z)
            val vertex = Vertex(position, Vector3.UP, confidence = confidence)
            
            pointCloudBuilder.pointCloud.addPoint(vertex)
            extracted++
        }
        
        return extracted
    }
    
    fun getPointCloud(): PointCloud = pointCloudBuilder.pointCloud
    
    fun getDownsampledPointCloud(): PointCloud = pointCloudBuilder.getDownsampledCloud()
    
    fun getStatistics(): PointCloudStatistics = pointCloudBuilder.getStatistics()
    
    fun getCurrentSession(): ScanSession? = currentSession
    
    fun isScanning(): Boolean = _extractionState.value is ExtractionState.Scanning
    
    fun reset() {
        cancelScanning()
        currentSession = null
        _lastExtractionResult.value = null
    }
}
