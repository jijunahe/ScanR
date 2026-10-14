package com.scanr.app.export

import com.scanr.app.data.MeshData
import com.scanr.app.data.PointCloud
import java.io.OutputStream

enum class ExportFormat(val extension: String, val mimeType: String, val displayName: String) {
    OBJ("obj", "model/obj", "Wavefront OBJ"),
    PLY("ply", "application/x-ply", "Stanford PLY"),
    GLTF("gltf", "model/gltf+json", "glTF 2.0"),
    GLB("glb", "model/gltf-binary", "glTF Binary"),
    STL("stl", "model/stl", "STL")
}

data class ExportOptions(
    val includeNormals: Boolean = true,
    val includeColors: Boolean = false,
    val scale: Float = 1.0f,
    val centerModel: Boolean = true,
    val flipYZ: Boolean = false,
    val objectName: String = "ScanR_Model",
    val authorName: String = "ScanR App"
)

data class ExportResult(
    val success: Boolean,
    val format: ExportFormat,
    val vertexCount: Int,
    val faceCount: Int,
    val fileSizeBytes: Long,
    val errorMessage: String? = null
)

interface MeshExporter {
    val format: ExportFormat
    
    fun export(meshData: MeshData, outputStream: OutputStream, options: ExportOptions = ExportOptions()): ExportResult
    
    fun export(pointCloud: PointCloud, outputStream: OutputStream, options: ExportOptions = ExportOptions()): ExportResult
    
    fun canExportMesh(): Boolean = true
    
    fun canExportPointCloud(): Boolean = true
}
