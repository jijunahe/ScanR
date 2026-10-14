package com.scanr.app.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.scanr.app.data.MeshData
import com.scanr.app.data.PointCloud
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "MeshExportManager"

class MeshExportManager(private val context: Context) {
    
    private val exporters = mapOf(
        ExportFormat.OBJ to ObjExporter(),
        ExportFormat.PLY to PlyExporter(),
        ExportFormat.GLTF to GltfExporter(),
        ExportFormat.STL to StlExporter()
    )
    
    fun getAvailableFormats(): List<ExportFormat> = exporters.keys.toList()
    
    fun getExporter(format: ExportFormat): MeshExporter? = exporters[format]
    
    suspend fun exportToFile(
        pointCloud: PointCloud,
        format: ExportFormat,
        fileName: String? = null,
        options: ExportOptions = ExportOptions()
    ): ExportFileResult = withContext(Dispatchers.IO) {
        val exporter = exporters[format]
            ?: return@withContext ExportFileResult.Error("Formato no soportado: $format")
        
        if (!exporter.canExportPointCloud()) {
            return@withContext ExportFileResult.Error("El formato $format no soporta nubes de puntos")
        }
        
        val actualFileName = fileName ?: generateFileName(format)
        
        try {
            val uri = saveToMediaStore(actualFileName, format)
            
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val result = exporter.export(pointCloud, outputStream, options)
                    if (result.success) {
                        return@withContext ExportFileResult.Success(
                            uri = uri,
                            fileName = actualFileName,
                            format = format,
                            result = result
                        )
                    } else {
                        return@withContext ExportFileResult.Error(
                            result.errorMessage ?: "Error desconocido"
                        )
                    }
                }
            }
            
            val file = saveToInternalStorage(actualFileName, format)
            FileOutputStream(file).use { outputStream ->
                val result = exporter.export(pointCloud, outputStream, options)
                if (result.success) {
                    val fileUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    return@withContext ExportFileResult.Success(
                        uri = fileUri,
                        fileName = actualFileName,
                        format = format,
                        result = result,
                        filePath = file.absolutePath
                    )
                } else {
                    file.delete()
                    return@withContext ExportFileResult.Error(
                        result.errorMessage ?: "Error desconocido"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting file", e)
            return@withContext ExportFileResult.Error(e.message ?: "Error desconocido")
        }
    }
    
    suspend fun exportToFile(
        meshData: MeshData,
        format: ExportFormat,
        fileName: String? = null,
        options: ExportOptions = ExportOptions()
    ): ExportFileResult = withContext(Dispatchers.IO) {
        val exporter = exporters[format]
            ?: return@withContext ExportFileResult.Error("Formato no soportado: $format")
        
        if (!exporter.canExportMesh()) {
            return@withContext ExportFileResult.Error("El formato $format no soporta mallas")
        }
        
        val actualFileName = fileName ?: generateFileName(format)
        
        try {
            val uri = saveToMediaStore(actualFileName, format)
            
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val result = exporter.export(meshData, outputStream, options)
                    if (result.success) {
                        return@withContext ExportFileResult.Success(
                            uri = uri,
                            fileName = actualFileName,
                            format = format,
                            result = result
                        )
                    } else {
                        return@withContext ExportFileResult.Error(
                            result.errorMessage ?: "Error desconocido"
                        )
                    }
                }
            }
            
            val file = saveToInternalStorage(actualFileName, format)
            FileOutputStream(file).use { outputStream ->
                val result = exporter.export(meshData, outputStream, options)
                if (result.success) {
                    val fileUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    return@withContext ExportFileResult.Success(
                        uri = fileUri,
                        fileName = actualFileName,
                        format = format,
                        result = result,
                        filePath = file.absolutePath
                    )
                } else {
                    file.delete()
                    return@withContext ExportFileResult.Error(
                        result.errorMessage ?: "Error desconocido"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting file", e)
            return@withContext ExportFileResult.Error(e.message ?: "Error desconocido")
        }
    }
    
    private fun saveToMediaStore(fileName: String, format: ExportFormat): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null
        }
        
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/ScanR")
        }
        
        return try {
            context.contentResolver.insert(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                contentValues
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not save to MediaStore", e)
            null
        }
    }
    
    private fun saveToInternalStorage(fileName: String, format: ExportFormat): File {
        val exportDir = File(context.filesDir, "exports")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        return File(exportDir, fileName)
    }
    
    fun createShareIntent(exportResult: ExportFileResult.Success): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = exportResult.format.mimeType
            putExtra(Intent.EXTRA_STREAM, exportResult.uri)
            putExtra(Intent.EXTRA_SUBJECT, "Modelo 3D - ${exportResult.fileName}")
            putExtra(
                Intent.EXTRA_TEXT,
                "Modelo 3D escaneado con ScanR\n" +
                "Formato: ${exportResult.format.displayName}\n" +
                "Vértices: ${exportResult.result.vertexCount}"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    
    fun createOpenWithIntent(exportResult: ExportFileResult.Success): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(exportResult.uri, exportResult.format.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    
    suspend fun exportAndShare(
        pointCloud: PointCloud,
        format: ExportFormat,
        options: ExportOptions = ExportOptions()
    ): Intent? {
        val result = exportToFile(pointCloud, format, options = options)
        return when (result) {
            is ExportFileResult.Success -> createShareIntent(result)
            is ExportFileResult.Error -> null
        }
    }
    
    private fun generateFileName(format: ExportFormat): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "ScanR_$timestamp.${format.extension}"
    }
    
    fun getExportDirectory(): File {
        val exportDir = File(context.filesDir, "exports")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        return exportDir
    }
    
    fun listExportedFiles(): List<ExportedFile> {
        val exportDir = getExportDirectory()
        return exportDir.listFiles()?.mapNotNull { file ->
            val format = ExportFormat.values().find { 
                file.extension.equals(it.extension, ignoreCase = true) 
            } ?: return@mapNotNull null
            
            ExportedFile(
                file = file,
                format = format,
                name = file.name,
                size = file.length(),
                lastModified = file.lastModified()
            )
        }?.sortedByDescending { it.lastModified } ?: emptyList()
    }
    
    fun deleteExportedFile(file: File): Boolean {
        return try {
            file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file", e)
            false
        }
    }
}

sealed class ExportFileResult {
    data class Success(
        val uri: Uri,
        val fileName: String,
        val format: ExportFormat,
        val result: ExportResult,
        val filePath: String? = null
    ) : ExportFileResult()
    
    data class Error(val message: String) : ExportFileResult()
}

data class ExportedFile(
    val file: File,
    val format: ExportFormat,
    val name: String,
    val size: Long,
    val lastModified: Long
)
