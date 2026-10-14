package com.scanr.app.mesh

import android.media.Image
import android.util.Log
import com.google.ar.core.Camera
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import com.scanr.app.data.PointCloud
import com.scanr.app.data.Vector3
import com.scanr.app.data.Vertex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ShortBuffer
import kotlin.math.abs

private const val TAG = "PointCloudBuilder"

class PointCloudBuilder(
    private val config: PointCloudConfig = PointCloudConfig()
) {
    private val accumulatedCloud = PointCloud()
    private var lastCameraPose: Pose? = null
    private var framesSinceLastCapture = 0
    
    val pointCloud: PointCloud get() = accumulatedCloud
    val pointCount: Int get() = accumulatedCloud.size
    
    data class PointCloudConfig(
        val minDepth: Float = 0.1f,
        val maxDepth: Float = 5.0f,
        val confidenceThreshold: Float = 0.3f,
        val samplingStride: Int = 4,
        val minMovementThreshold: Float = 0.02f,
        val minRotationThreshold: Float = 0.02f,
        val frameSkipCount: Int = 2,
        val maxPointsPerFrame: Int = 10000,
        val voxelDownsampleSize: Float = 0.005f
    )
    
    data class ExtractionResult(
        val pointsExtracted: Int,
        val totalPoints: Int,
        val frameProcessed: Boolean,
        val skippedReason: String? = null
    )
    
    suspend fun extractFromFrame(frame: Frame): ExtractionResult = withContext(Dispatchers.Default) {
        if (frame.camera.trackingState != TrackingState.TRACKING) {
            return@withContext ExtractionResult(0, pointCount, false, "Camera not tracking")
        }
        
        framesSinceLastCapture++
        if (framesSinceLastCapture < config.frameSkipCount) {
            return@withContext ExtractionResult(0, pointCount, false, "Frame skipped")
        }
        
        val currentPose = frame.camera.pose
        if (!hasMovedEnough(currentPose)) {
            return@withContext ExtractionResult(0, pointCount, false, "Insufficient movement")
        }
        
        val depthImage = try {
            frame.acquireDepthImage16Bits()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire depth image: ${e.message}")
            return@withContext ExtractionResult(0, pointCount, false, "No depth data")
        }
        
        val extractedPoints = try {
            extractPointsFromDepthImage(depthImage, frame.camera)
        } finally {
            depthImage.close()
        }
        
        if (extractedPoints.isNotEmpty()) {
            accumulatedCloud.addPoints(extractedPoints)
            lastCameraPose = currentPose
            framesSinceLastCapture = 0
        }
        
        ExtractionResult(extractedPoints.size, pointCount, true)
    }
    
    private fun hasMovedEnough(currentPose: Pose): Boolean {
        val lastPose = lastCameraPose ?: return true
        
        val translation = floatArrayOf(
            currentPose.tx() - lastPose.tx(),
            currentPose.ty() - lastPose.ty(),
            currentPose.tz() - lastPose.tz()
        )
        val distance = kotlin.math.sqrt(
            translation[0] * translation[0] +
            translation[1] * translation[1] +
            translation[2] * translation[2]
        )
        
        if (distance > config.minMovementThreshold) {
            return true
        }
        
        val rotationDiff = floatArrayOf(
            abs(currentPose.qx() - lastPose.qx()),
            abs(currentPose.qy() - lastPose.qy()),
            abs(currentPose.qz() - lastPose.qz()),
            abs(currentPose.qw() - lastPose.qw())
        )
        val rotationChange = rotationDiff.sum()
        
        return rotationChange > config.minRotationThreshold
    }
    
    private fun extractPointsFromDepthImage(
        depthImage: Image,
        camera: Camera
    ): List<Vertex> {
        val width = depthImage.width
        val height = depthImage.height
        val plane = depthImage.planes[0]
        val buffer = plane.buffer.asShortBuffer()
        
        val intrinsics = camera.textureIntrinsics
        val fx = intrinsics.focalLength[0]
        val fy = intrinsics.focalLength[1]
        val cx = intrinsics.principalPoint[0]
        val cy = intrinsics.principalPoint[1]
        
        val cameraPose = camera.pose
        val points = mutableListOf<Vertex>()
        
        val stride = config.samplingStride
        var pointsAdded = 0
        
        for (y in 0 until height step stride) {
            for (x in 0 until width step stride) {
                if (pointsAdded >= config.maxPointsPerFrame) break
                
                val index = y * width + x
                val depthMillimeters = buffer.get(index).toInt() and 0xFFFF
                val depthMeters = depthMillimeters / 1000.0f
                
                if (depthMeters < config.minDepth || depthMeters > config.maxDepth) {
                    continue
                }
                
                val localX = (x - cx) * depthMeters / fx
                val localY = (y - cy) * depthMeters / fy
                val localZ = -depthMeters
                
                val localPoint = floatArrayOf(localX, localY, localZ)
                val worldPoint = FloatArray(3)
                cameraPose.transformPoint(localPoint, 0, worldPoint, 0)
                
                val position = Vector3(worldPoint[0], worldPoint[1], worldPoint[2])
                
                val normal = estimateNormal(buffer, x, y, width, height, fx, fy, cx, cy, cameraPose)
                
                val confidence = calculateConfidence(depthMeters, x, y, width, height)
                
                if (confidence >= config.confidenceThreshold) {
                    points.add(Vertex(position, normal, confidence = confidence))
                    pointsAdded++
                }
            }
            if (pointsAdded >= config.maxPointsPerFrame) break
        }
        
        Log.d(TAG, "Extracted $pointsAdded points from depth image (${width}x${height})")
        return points
    }
    
    private fun estimateNormal(
        buffer: ShortBuffer,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        fx: Float,
        fy: Float,
        cx: Float,
        cy: Float,
        cameraPose: Pose
    ): Vector3 {
        val step = config.samplingStride
        
        if (x - step < 0 || x + step >= width || y - step < 0 || y + step >= height) {
            return Vector3.UP
        }
        
        fun getWorldPoint(px: Int, py: Int): Vector3? {
            val idx = py * width + px
            val depthMm = buffer.get(idx).toInt() and 0xFFFF
            val depthM = depthMm / 1000.0f
            
            if (depthM < config.minDepth || depthM > config.maxDepth) return null
            
            val localX = (px - cx) * depthM / fx
            val localY = (py - cy) * depthM / fy
            val localZ = -depthM
            
            val localPoint = floatArrayOf(localX, localY, localZ)
            val worldPoint = FloatArray(3)
            cameraPose.transformPoint(localPoint, 0, worldPoint, 0)
            
            return Vector3(worldPoint[0], worldPoint[1], worldPoint[2])
        }
        
        val center = getWorldPoint(x, y) ?: return Vector3.UP
        val left = getWorldPoint(x - step, y)
        val right = getWorldPoint(x + step, y)
        val up = getWorldPoint(x, y - step)
        val down = getWorldPoint(x, y + step)
        
        val dx = if (left != null && right != null) {
            right - left
        } else if (right != null) {
            right - center
        } else if (left != null) {
            center - left
        } else {
            return Vector3.UP
        }
        
        val dy = if (up != null && down != null) {
            down - up
        } else if (down != null) {
            down - center
        } else if (up != null) {
            center - up
        } else {
            return Vector3.UP
        }
        
        val normal = dx.cross(dy).normalized
        return if (normal.magnitude > 0.5f) normal else Vector3.UP
    }
    
    private fun calculateConfidence(depth: Float, x: Int, y: Int, width: Int, height: Int): Float {
        val depthConfidence = when {
            depth < 0.3f -> 0.7f
            depth < 1.0f -> 1.0f
            depth < 2.0f -> 0.9f
            depth < 3.0f -> 0.7f
            else -> 0.5f
        }
        
        val centerX = width / 2f
        val centerY = height / 2f
        val distFromCenter = kotlin.math.sqrt(
            (x - centerX) * (x - centerX) + (y - centerY) * (y - centerY)
        )
        val maxDist = kotlin.math.sqrt(centerX * centerX + centerY * centerY)
        val edgeConfidence = 1.0f - (distFromCenter / maxDist) * 0.3f
        
        return depthConfidence * edgeConfidence
    }
    
    fun getDownsampledCloud(): PointCloud {
        return if (config.voxelDownsampleSize > 0) {
            accumulatedCloud.downsample(config.voxelDownsampleSize)
        } else {
            accumulatedCloud.copy()
        }
    }
    
    fun clear() {
        accumulatedCloud.clear()
        lastCameraPose = null
        framesSinceLastCapture = 0
    }
    
    fun getStatistics(): PointCloudStatistics {
        return PointCloudStatistics(
            totalPoints = accumulatedCloud.size,
            boundingBoxMin = accumulatedCloud.boundingBoxMin,
            boundingBoxMax = accumulatedCloud.boundingBoxMax,
            boundingBoxSize = accumulatedCloud.boundingBoxSize
        )
    }
}

data class PointCloudStatistics(
    val totalPoints: Int,
    val boundingBoxMin: Vector3,
    val boundingBoxMax: Vector3,
    val boundingBoxSize: Vector3
)
