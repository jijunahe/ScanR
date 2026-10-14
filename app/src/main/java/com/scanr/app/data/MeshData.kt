package com.scanr.app.data

import java.util.UUID

data class MeshData(
    val id: String = UUID.randomUUID().toString(),
    val vertices: List<Vertex>,
    val triangles: List<Triangle>,
    val createdAt: Long = System.currentTimeMillis()
) {
    val vertexCount: Int get() = vertices.size
    val triangleCount: Int get() = triangles.size
    val faceCount: Int get() = triangleCount
    
    val boundingBoxMin: Vector3 by lazy {
        if (vertices.isEmpty()) Vector3.ZERO
        else Vector3(
            vertices.minOf { it.position.x },
            vertices.minOf { it.position.y },
            vertices.minOf { it.position.z }
        )
    }
    
    val boundingBoxMax: Vector3 by lazy {
        if (vertices.isEmpty()) Vector3.ZERO
        else Vector3(
            vertices.maxOf { it.position.x },
            vertices.maxOf { it.position.y },
            vertices.maxOf { it.position.z }
        )
    }
    
    val boundingBoxCenter: Vector3 by lazy {
        Vector3(
            (boundingBoxMin.x + boundingBoxMax.x) / 2f,
            (boundingBoxMin.y + boundingBoxMax.y) / 2f,
            (boundingBoxMin.z + boundingBoxMax.z) / 2f
        )
    }
    
    fun toPositionBuffer(): FloatArray {
        val buffer = FloatArray(vertices.size * 3)
        vertices.forEachIndexed { index, vertex ->
            val offset = index * 3
            buffer[offset] = vertex.position.x
            buffer[offset + 1] = vertex.position.y
            buffer[offset + 2] = vertex.position.z
        }
        return buffer
    }
    
    fun toNormalBuffer(): FloatArray {
        val buffer = FloatArray(vertices.size * 3)
        vertices.forEachIndexed { index, vertex ->
            val offset = index * 3
            buffer[offset] = vertex.normal.x
            buffer[offset + 1] = vertex.normal.y
            buffer[offset + 2] = vertex.normal.z
        }
        return buffer
    }
    
    fun toIndexBuffer(): IntArray {
        val buffer = IntArray(triangles.size * 3)
        triangles.forEachIndexed { index, triangle ->
            val offset = index * 3
            buffer[offset] = triangle.v0
            buffer[offset + 1] = triangle.v1
            buffer[offset + 2] = triangle.v2
        }
        return buffer
    }
    
    fun toInterleavedBuffer(): FloatArray {
        val buffer = FloatArray(vertices.size * 6)
        vertices.forEachIndexed { index, vertex ->
            val offset = index * 6
            buffer[offset] = vertex.position.x
            buffer[offset + 1] = vertex.position.y
            buffer[offset + 2] = vertex.position.z
            buffer[offset + 3] = vertex.normal.x
            buffer[offset + 4] = vertex.normal.y
            buffer[offset + 5] = vertex.normal.z
        }
        return buffer
    }
    
    companion object {
        fun empty() = MeshData(
            vertices = emptyList(),
            triangles = emptyList()
        )
        
        fun fromPointCloud(pointCloud: PointCloud): MeshData {
            return MeshData(
                vertices = pointCloud.points,
                triangles = emptyList()
            )
        }
    }
}

data class ScanSession(
    val id: String = UUID.randomUUID().toString(),
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long? = null,
    val pointCloud: PointCloud = PointCloud(),
    var meshData: MeshData? = null,
    var frameCount: Int = 0,
    var status: ScanStatus = ScanStatus.IDLE
) {
    val duration: Long
        get() = (endTime ?: System.currentTimeMillis()) - startTime
    
    val isActive: Boolean
        get() = status == ScanStatus.SCANNING
    
    fun finish() {
        endTime = System.currentTimeMillis()
        status = ScanStatus.COMPLETED
    }
    
    fun cancel() {
        endTime = System.currentTimeMillis()
        status = ScanStatus.CANCELLED
    }
}

enum class ScanStatus {
    IDLE,
    SCANNING,
    PROCESSING,
    COMPLETED,
    CANCELLED,
    ERROR
}
