package com.scanr.app.export

import android.util.Base64
import com.scanr.app.data.MeshData
import com.scanr.app.data.PointCloud
import com.scanr.app.data.Vector3
import com.scanr.app.data.Vertex
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GltfExporter : MeshExporter {
    
    override val format = ExportFormat.GLTF
    
    override fun export(
        meshData: MeshData,
        outputStream: OutputStream,
        options: ExportOptions
    ): ExportResult {
        return try {
            val vertices = if (options.centerModel) {
                centerVertices(meshData.vertices, meshData.boundingBoxCenter)
            } else {
                meshData.vertices
            }
            
            val gltfJson = buildGltfJson(vertices, meshData, options)
            
            val writer = BufferedWriter(OutputStreamWriter(outputStream, Charsets.UTF_8))
            val jsonString = gltfJson.toString(2)
            writer.write(jsonString)
            writer.flush()
            writer.close()
            
            ExportResult(
                success = true,
                format = format,
                vertexCount = vertices.size,
                faceCount = meshData.triangles.size,
                fileSizeBytes = jsonString.length.toLong()
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
        return try {
            val vertices = if (options.centerModel) {
                centerVertices(pointCloud.points, pointCloud.boundingBoxCenter)
            } else {
                pointCloud.points
            }
            
            val gltfJson = buildGltfJsonPointCloud(vertices, options)
            
            val writer = BufferedWriter(OutputStreamWriter(outputStream, Charsets.UTF_8))
            val jsonString = gltfJson.toString(2)
            writer.write(jsonString)
            writer.flush()
            writer.close()
            
            ExportResult(
                success = true,
                format = format,
                vertexCount = vertices.size,
                faceCount = 0,
                fileSizeBytes = jsonString.length.toLong()
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
    
    private fun buildGltfJson(
        vertices: List<Vertex>,
        meshData: MeshData,
        options: ExportOptions
    ): JSONObject {
        val positionData = createPositionBuffer(vertices, options)
        val normalData = if (options.includeNormals) createNormalBuffer(vertices, options) else null
        val indexData = createIndexBuffer(meshData)
        
        val positionBase64 = Base64.encodeToString(positionData, Base64.NO_WRAP)
        val normalBase64 = normalData?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        val indexBase64 = indexData?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        
        val bounds = calculateBounds(vertices, options)
        
        return JSONObject().apply {
            put("asset", JSONObject().apply {
                put("version", "2.0")
                put("generator", "ScanR App")
            })
            
            put("scene", 0)
            
            put("scenes", JSONArray().apply {
                put(JSONObject().apply {
                    put("name", "Scene")
                    put("nodes", JSONArray().apply { put(0) })
                })
            })
            
            put("nodes", JSONArray().apply {
                put(JSONObject().apply {
                    put("name", options.objectName)
                    put("mesh", 0)
                })
            })
            
            put("meshes", JSONArray().apply {
                put(JSONObject().apply {
                    put("name", options.objectName)
                    put("primitives", JSONArray().apply {
                        put(JSONObject().apply {
                            put("attributes", JSONObject().apply {
                                put("POSITION", 0)
                                if (options.includeNormals) put("NORMAL", 1)
                            })
                            if (indexData != null) {
                                put("indices", if (options.includeNormals) 2 else 1)
                            }
                            put("mode", 4)
                        })
                    })
                })
            })
            
            val accessors = JSONArray()
            var accessorIndex = 0
            var bufferViewIndex = 0
            
            accessors.put(JSONObject().apply {
                put("bufferView", bufferViewIndex++)
                put("componentType", 5126)
                put("count", vertices.size)
                put("type", "VEC3")
                put("min", JSONArray().apply {
                    put(bounds.first.x); put(bounds.first.y); put(bounds.first.z)
                })
                put("max", JSONArray().apply {
                    put(bounds.second.x); put(bounds.second.y); put(bounds.second.z)
                })
            })
            accessorIndex++
            
            if (options.includeNormals) {
                accessors.put(JSONObject().apply {
                    put("bufferView", bufferViewIndex++)
                    put("componentType", 5126)
                    put("count", vertices.size)
                    put("type", "VEC3")
                })
                accessorIndex++
            }
            
            if (indexData != null) {
                accessors.put(JSONObject().apply {
                    put("bufferView", bufferViewIndex++)
                    put("componentType", 5125)
                    put("count", meshData.triangles.size * 3)
                    put("type", "SCALAR")
                })
            }
            
            put("accessors", accessors)
            
            val bufferViews = JSONArray()
            var byteOffset = 0
            
            bufferViews.put(JSONObject().apply {
                put("buffer", 0)
                put("byteOffset", byteOffset)
                put("byteLength", positionData.size)
                put("target", 34962)
            })
            byteOffset += positionData.size
            
            if (normalData != null) {
                bufferViews.put(JSONObject().apply {
                    put("buffer", 0)
                    put("byteOffset", byteOffset)
                    put("byteLength", normalData.size)
                    put("target", 34962)
                })
                byteOffset += normalData.size
            }
            
            if (indexData != null) {
                bufferViews.put(JSONObject().apply {
                    put("buffer", 0)
                    put("byteOffset", byteOffset)
                    put("byteLength", indexData.size)
                    put("target", 34963)
                })
                byteOffset += indexData.size
            }
            
            put("bufferViews", bufferViews)
            
            val combinedBuffer = ByteBuffer.allocate(byteOffset)
            combinedBuffer.put(positionData)
            normalData?.let { combinedBuffer.put(it) }
            indexData?.let { combinedBuffer.put(it) }
            
            val bufferBase64 = Base64.encodeToString(combinedBuffer.array(), Base64.NO_WRAP)
            
            put("buffers", JSONArray().apply {
                put(JSONObject().apply {
                    put("uri", "data:application/octet-stream;base64,$bufferBase64")
                    put("byteLength", byteOffset)
                })
            })
        }
    }
    
    private fun buildGltfJsonPointCloud(
        vertices: List<Vertex>,
        options: ExportOptions
    ): JSONObject {
        val positionData = createPositionBuffer(vertices, options)
        val normalData = if (options.includeNormals) createNormalBuffer(vertices, options) else null
        
        val bounds = calculateBounds(vertices, options)
        
        return JSONObject().apply {
            put("asset", JSONObject().apply {
                put("version", "2.0")
                put("generator", "ScanR App")
            })
            
            put("scene", 0)
            
            put("scenes", JSONArray().apply {
                put(JSONObject().apply {
                    put("name", "Scene")
                    put("nodes", JSONArray().apply { put(0) })
                })
            })
            
            put("nodes", JSONArray().apply {
                put(JSONObject().apply {
                    put("name", options.objectName)
                    put("mesh", 0)
                })
            })
            
            put("meshes", JSONArray().apply {
                put(JSONObject().apply {
                    put("name", options.objectName)
                    put("primitives", JSONArray().apply {
                        put(JSONObject().apply {
                            put("attributes", JSONObject().apply {
                                put("POSITION", 0)
                                if (options.includeNormals) put("NORMAL", 1)
                            })
                            put("mode", 0)
                        })
                    })
                })
            })
            
            val accessors = JSONArray()
            var bufferViewIndex = 0
            
            accessors.put(JSONObject().apply {
                put("bufferView", bufferViewIndex++)
                put("componentType", 5126)
                put("count", vertices.size)
                put("type", "VEC3")
                put("min", JSONArray().apply {
                    put(bounds.first.x); put(bounds.first.y); put(bounds.first.z)
                })
                put("max", JSONArray().apply {
                    put(bounds.second.x); put(bounds.second.y); put(bounds.second.z)
                })
            })
            
            if (options.includeNormals && normalData != null) {
                accessors.put(JSONObject().apply {
                    put("bufferView", bufferViewIndex++)
                    put("componentType", 5126)
                    put("count", vertices.size)
                    put("type", "VEC3")
                })
            }
            
            put("accessors", accessors)
            
            val bufferViews = JSONArray()
            var byteOffset = 0
            
            bufferViews.put(JSONObject().apply {
                put("buffer", 0)
                put("byteOffset", byteOffset)
                put("byteLength", positionData.size)
            })
            byteOffset += positionData.size
            
            if (normalData != null) {
                bufferViews.put(JSONObject().apply {
                    put("buffer", 0)
                    put("byteOffset", byteOffset)
                    put("byteLength", normalData.size)
                })
                byteOffset += normalData.size
            }
            
            put("bufferViews", bufferViews)
            
            val totalSize = byteOffset
            val combinedBuffer = ByteBuffer.allocate(totalSize)
            combinedBuffer.put(positionData)
            normalData?.let { combinedBuffer.put(it) }
            
            val bufferBase64 = Base64.encodeToString(combinedBuffer.array(), Base64.NO_WRAP)
            
            put("buffers", JSONArray().apply {
                put(JSONObject().apply {
                    put("uri", "data:application/octet-stream;base64,$bufferBase64")
                    put("byteLength", totalSize)
                })
            })
        }
    }
    
    private fun createPositionBuffer(vertices: List<Vertex>, options: ExportOptions): ByteArray {
        val buffer = ByteBuffer.allocate(vertices.size * 3 * 4)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        
        vertices.forEach { vertex ->
            val pos = vertex.position
            val scale = options.scale
            if (options.flipYZ) {
                buffer.putFloat(pos.x * scale)
                buffer.putFloat(pos.z * scale)
                buffer.putFloat(-pos.y * scale)
            } else {
                buffer.putFloat(pos.x * scale)
                buffer.putFloat(pos.y * scale)
                buffer.putFloat(pos.z * scale)
            }
        }
        
        return buffer.array()
    }
    
    private fun createNormalBuffer(vertices: List<Vertex>, options: ExportOptions): ByteArray {
        val buffer = ByteBuffer.allocate(vertices.size * 3 * 4)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        
        vertices.forEach { vertex ->
            val normal = vertex.normal
            if (options.flipYZ) {
                buffer.putFloat(normal.x)
                buffer.putFloat(normal.z)
                buffer.putFloat(-normal.y)
            } else {
                buffer.putFloat(normal.x)
                buffer.putFloat(normal.y)
                buffer.putFloat(normal.z)
            }
        }
        
        return buffer.array()
    }
    
    private fun createIndexBuffer(meshData: MeshData): ByteArray? {
        if (meshData.triangles.isEmpty()) return null
        
        val buffer = ByteBuffer.allocate(meshData.triangles.size * 3 * 4)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        
        meshData.triangles.forEach { triangle ->
            buffer.putInt(triangle.v0)
            buffer.putInt(triangle.v1)
            buffer.putInt(triangle.v2)
        }
        
        return buffer.array()
    }
    
    private fun calculateBounds(vertices: List<Vertex>, options: ExportOptions): Pair<Vector3, Vector3> {
        if (vertices.isEmpty()) {
            return Pair(Vector3.ZERO, Vector3.ZERO)
        }
        
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        var maxZ = Float.MIN_VALUE
        
        vertices.forEach { vertex ->
            val pos = vertex.position
            val scale = options.scale
            val (x, y, z) = if (options.flipYZ) {
                Triple(pos.x * scale, pos.z * scale, -pos.y * scale)
            } else {
                Triple(pos.x * scale, pos.y * scale, pos.z * scale)
            }
            
            minX = minOf(minX, x)
            minY = minOf(minY, y)
            minZ = minOf(minZ, z)
            maxX = maxOf(maxX, x)
            maxY = maxOf(maxY, y)
            maxZ = maxOf(maxZ, z)
        }
        
        return Pair(Vector3(minX, minY, minZ), Vector3(maxX, maxY, maxZ))
    }
    
    private fun centerVertices(vertices: List<Vertex>, center: Vector3): List<Vertex> {
        return vertices.map { vertex ->
            vertex.copy(position = vertex.position - center)
        }
    }
}
