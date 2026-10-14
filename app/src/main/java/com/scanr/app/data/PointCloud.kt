package com.scanr.app.data

import java.nio.FloatBuffer
import java.util.concurrent.CopyOnWriteArrayList

class PointCloud {
    
    private val _points = CopyOnWriteArrayList<Vertex>()
    val points: List<Vertex> get() = _points.toList()
    
    val size: Int get() = _points.size
    
    val isEmpty: Boolean get() = _points.isEmpty()
    
    private var _boundingBoxMin = Vector3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
    private var _boundingBoxMax = Vector3(Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE)
    
    val boundingBoxMin: Vector3 get() = _boundingBoxMin
    val boundingBoxMax: Vector3 get() = _boundingBoxMax
    
    val boundingBoxCenter: Vector3
        get() = Vector3(
            (_boundingBoxMin.x + _boundingBoxMax.x) / 2f,
            (_boundingBoxMin.y + _boundingBoxMax.y) / 2f,
            (_boundingBoxMin.z + _boundingBoxMax.z) / 2f
        )
    
    val boundingBoxSize: Vector3
        get() = Vector3(
            _boundingBoxMax.x - _boundingBoxMin.x,
            _boundingBoxMax.y - _boundingBoxMin.y,
            _boundingBoxMax.z - _boundingBoxMin.z
        )
    
    fun addPoint(vertex: Vertex) {
        _points.add(vertex)
        updateBoundingBox(vertex.position)
    }
    
    fun addPoints(vertices: List<Vertex>) {
        _points.addAll(vertices)
        vertices.forEach { updateBoundingBox(it.position) }
    }
    
    fun addPointBatch(positions: List<Vector3>, normals: List<Vector3>? = null, confidences: List<Float>? = null) {
        positions.forEachIndexed { index, position ->
            val normal = normals?.getOrNull(index) ?: Vector3.UP
            val confidence = confidences?.getOrNull(index) ?: 1.0f
            addPoint(Vertex(position, normal, confidence = confidence))
        }
    }
    
    private fun updateBoundingBox(position: Vector3) {
        _boundingBoxMin = Vector3(
            minOf(_boundingBoxMin.x, position.x),
            minOf(_boundingBoxMin.y, position.y),
            minOf(_boundingBoxMin.z, position.z)
        )
        _boundingBoxMax = Vector3(
            maxOf(_boundingBoxMax.x, position.x),
            maxOf(_boundingBoxMax.y, position.y),
            maxOf(_boundingBoxMax.z, position.z)
        )
    }
    
    fun clear() {
        _points.clear()
        _boundingBoxMin = Vector3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        _boundingBoxMax = Vector3(Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE)
    }
    
    fun toPositionArray(): FloatArray {
        val array = FloatArray(_points.size * 3)
        _points.forEachIndexed { index, vertex ->
            val offset = index * 3
            array[offset] = vertex.position.x
            array[offset + 1] = vertex.position.y
            array[offset + 2] = vertex.position.z
        }
        return array
    }
    
    fun toNormalArray(): FloatArray {
        val array = FloatArray(_points.size * 3)
        _points.forEachIndexed { index, vertex ->
            val offset = index * 3
            array[offset] = vertex.normal.x
            array[offset + 1] = vertex.normal.y
            array[offset + 2] = vertex.normal.z
        }
        return array
    }
    
    fun toVertexArray(): FloatArray {
        val array = FloatArray(_points.size * 6)
        _points.forEachIndexed { index, vertex ->
            val offset = index * 6
            array[offset] = vertex.position.x
            array[offset + 1] = vertex.position.y
            array[offset + 2] = vertex.position.z
            array[offset + 3] = vertex.normal.x
            array[offset + 4] = vertex.normal.y
            array[offset + 5] = vertex.normal.z
        }
        return array
    }
    
    fun filterByConfidence(minConfidence: Float): PointCloud {
        val filtered = PointCloud()
        _points.filter { it.confidence >= minConfidence }.forEach { filtered.addPoint(it) }
        return filtered
    }
    
    fun downsample(voxelSize: Float): PointCloud {
        if (voxelSize <= 0f) return this
        
        val voxelMap = mutableMapOf<Triple<Int, Int, Int>, MutableList<Vertex>>()
        
        _points.forEach { vertex ->
            val voxelKey = Triple(
                (vertex.position.x / voxelSize).toInt(),
                (vertex.position.y / voxelSize).toInt(),
                (vertex.position.z / voxelSize).toInt()
            )
            voxelMap.getOrPut(voxelKey) { mutableListOf() }.add(vertex)
        }
        
        val downsampled = PointCloud()
        voxelMap.values.forEach { voxelPoints ->
            val avgPosition = Vector3(
                voxelPoints.map { it.position.x }.average().toFloat(),
                voxelPoints.map { it.position.y }.average().toFloat(),
                voxelPoints.map { it.position.z }.average().toFloat()
            )
            val avgNormal = Vector3(
                voxelPoints.map { it.normal.x }.average().toFloat(),
                voxelPoints.map { it.normal.y }.average().toFloat(),
                voxelPoints.map { it.normal.z }.average().toFloat()
            ).normalized
            val avgConfidence = voxelPoints.map { it.confidence }.average().toFloat()
            
            downsampled.addPoint(Vertex(avgPosition, avgNormal, confidence = avgConfidence))
        }
        
        return downsampled
    }
    
    fun merge(other: PointCloud) {
        addPoints(other.points)
    }
    
    fun copy(): PointCloud {
        val copy = PointCloud()
        copy.addPoints(_points.toList())
        return copy
    }
    
    override fun toString(): String {
        return "PointCloud(points=${_points.size}, bounds=$boundingBoxSize)"
    }
}
