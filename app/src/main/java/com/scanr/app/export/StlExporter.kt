package com.scanr.app.export

import com.scanr.app.data.MeshData
import com.scanr.app.data.PointCloud
import com.scanr.app.data.Vector3
import com.scanr.app.data.Vertex
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.Locale

class StlExporter : MeshExporter {
    
    override val format = ExportFormat.STL
    
    override fun canExportPointCloud(): Boolean = false
    
    override fun export(
        meshData: MeshData,
        outputStream: OutputStream,
        options: ExportOptions
    ): ExportResult {
        return try {
            val writer = BufferedWriter(OutputStreamWriter(outputStream, Charsets.UTF_8))
            var bytesWritten = 0L
            
            val vertices = if (options.centerModel) {
                centerVertices(meshData.vertices, meshData.boundingBoxCenter)
            } else {
                meshData.vertices
            }
            
            val header = "solid ${options.objectName}\n"
            writer.write(header)
            bytesWritten += header.length
            
            meshData.triangles.forEach { triangle ->
                val v0 = vertices.getOrNull(triangle.v0) ?: return@forEach
                val v1 = vertices.getOrNull(triangle.v1) ?: return@forEach
                val v2 = vertices.getOrNull(triangle.v2) ?: return@forEach
                
                val normal = calculateFaceNormal(v0.position, v1.position, v2.position, options)
                
                val facet = buildString {
                    appendLine("  facet normal ${formatVector(normal)}")
                    appendLine("    outer loop")
                    appendLine("      vertex ${formatVertex(v0.position, options)}")
                    appendLine("      vertex ${formatVertex(v1.position, options)}")
                    appendLine("      vertex ${formatVertex(v2.position, options)}")
                    appendLine("    endloop")
                    appendLine("  endfacet")
                }
                writer.write(facet)
                bytesWritten += facet.length
            }
            
            val footer = "endsolid ${options.objectName}\n"
            writer.write(footer)
            bytesWritten += footer.length
            
            writer.flush()
            writer.close()
            
            ExportResult(
                success = true,
                format = format,
                vertexCount = vertices.size,
                faceCount = meshData.triangles.size,
                fileSizeBytes = bytesWritten
            )
        } catch (e: Exception) {
            ExportResult(
                success = false,
                format = format,
                vertexCount = 0,
                faceCount = 0,
                fileSizeBytes = 0,
                errorMessage = e.message
            )
        }
    }
    
    override fun export(
        pointCloud: PointCloud,
        outputStream: OutputStream,
        options: ExportOptions
    ): ExportResult {
        return ExportResult(
            success = false,
            format = format,
            vertexCount = 0,
            faceCount = 0,
            fileSizeBytes = 0,
            errorMessage = "STL format does not support point clouds. Please use OBJ, PLY, or GLTF."
        )
    }
    
    private fun calculateFaceNormal(v0: Vector3, v1: Vector3, v2: Vector3, options: ExportOptions): Vector3 {
        val edge1 = v1 - v0
        val edge2 = v2 - v0
        val normal = edge1.cross(edge2).normalized
        
        return if (options.flipYZ) {
            Vector3(normal.x, normal.z, -normal.y)
        } else {
            normal
        }
    }
    
    private fun formatVertex(position: Vector3, options: ExportOptions): String {
        val scale = options.scale
        val (x, y, z) = if (options.flipYZ) {
            Triple(position.x * scale, position.z * scale, -position.y * scale)
        } else {
            Triple(position.x * scale, position.y * scale, position.z * scale)
        }
        return "${formatFloat(x)} ${formatFloat(y)} ${formatFloat(z)}"
    }
    
    private fun formatVector(v: Vector3): String {
        return "${formatFloat(v.x)} ${formatFloat(v.y)} ${formatFloat(v.z)}"
    }
    
    private fun formatFloat(value: Float): String {
        return String.format(Locale.US, "%.6f", value)
    }
    
    private fun centerVertices(vertices: List<Vertex>, center: Vector3): List<Vertex> {
        return vertices.map { vertex ->
            vertex.copy(position = vertex.position - center)
        }
    }
}
