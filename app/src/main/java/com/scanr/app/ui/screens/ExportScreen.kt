package com.scanr.app.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scanr.app.data.PointCloud
import com.scanr.app.export.ExportFileResult
import com.scanr.app.export.ExportFormat
import com.scanr.app.export.ExportOptions
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    pointCloud: PointCloud,
    onExport: (ExportFormat, ExportOptions) -> Unit,
    onShare: (ExportFileResult.Success) -> Unit,
    exportState: ExportState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFormat by remember { mutableStateOf(ExportFormat.OBJ) }
    var includeNormals by remember { mutableStateOf(true) }
    var centerModel by remember { mutableStateOf(true) }
    var flipYZ by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    
    val numberFormat = remember { NumberFormat.getNumberInstance() }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exportar Modelo 3D") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ScanSummaryCard(
                    pointCount = pointCloud.size,
                    boundingBoxSize = pointCloud.boundingBoxSize,
                    numberFormat = numberFormat
                )
            }
            
            item {
                FormatSelectionCard(
                    selectedFormat = selectedFormat,
                    onFormatSelected = { selectedFormat = it }
                )
            }
            
            item {
                ExportOptionsCard(
                    includeNormals = includeNormals,
                    onIncludeNormalsChanged = { includeNormals = it },
                    centerModel = centerModel,
                    onCenterModelChanged = { centerModel = it },
                    flipYZ = flipYZ,
                    onFlipYZChanged = { flipYZ = it },
                    scale = scale,
                    onScaleChanged = { scale = it }
                )
            }
            
            item {
                when (exportState) {
                    is ExportState.Idle -> {
                        ExportButton(
                            format = selectedFormat,
                            onExport = {
                                val options = ExportOptions(
                                    includeNormals = includeNormals,
                                    centerModel = centerModel,
                                    flipYZ = flipYZ,
                                    scale = scale
                                )
                                onExport(selectedFormat, options)
                            }
                        )
                    }
                    is ExportState.Exporting -> {
                        ExportingIndicator()
                    }
                    is ExportState.Success -> {
                        ExportSuccessCard(
                            result = exportState.result,
                            onShare = { onShare(exportState.result) },
                            onExportAnother = { /* Reset state handled by parent */ }
                        )
                    }
                    is ExportState.Error -> {
                        ExportErrorCard(
                            message = exportState.message,
                            onRetry = {
                                val options = ExportOptions(
                                    includeNormals = includeNormals,
                                    centerModel = centerModel,
                                    flipYZ = flipYZ,
                                    scale = scale
                                )
                                onExport(selectedFormat, options)
                            }
                        )
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ScanSummaryCard(
    pointCount: Int,
    boundingBoxSize: com.scanr.app.data.Vector3,
    numberFormat: NumberFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Resumen del Escaneo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatColumn(
                    label = "Puntos",
                    value = numberFormat.format(pointCount)
                )
                StatColumn(
                    label = "Ancho",
                    value = String.format("%.2fm", boundingBoxSize.x)
                )
                StatColumn(
                    label = "Alto",
                    value = String.format("%.2fm", boundingBoxSize.y)
                )
                StatColumn(
                    label = "Profundidad",
                    value = String.format("%.2fm", boundingBoxSize.z)
                )
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FormatSelectionCard(
    selectedFormat: ExportFormat,
    onFormatSelected: (ExportFormat) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Formato de Exportación",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            ExportFormat.values().forEach { format ->
                FormatOption(
                    format = format,
                    isSelected = format == selectedFormat,
                    onSelect = { onFormatSelected(format) }
                )
            }
        }
    }
}

@Composable
private fun FormatOption(
    format: ExportFormat,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val description = when (format) {
        ExportFormat.OBJ -> "Compatible con Blender, Maya, 3ds Max"
        ExportFormat.PLY -> "Ideal para nubes de puntos con colores"
        ExportFormat.GLTF -> "Formato moderno, soporta materiales"
        ExportFormat.GLB -> "GLTF binario, más compacto"
        ExportFormat.STL -> "Para impresión 3D"
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onSelect() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onSelect
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ".${format.extension.uppercase()} - ${format.displayName}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExportOptionsCard(
    includeNormals: Boolean,
    onIncludeNormalsChanged: (Boolean) -> Unit,
    centerModel: Boolean,
    onCenterModelChanged: (Boolean) -> Unit,
    flipYZ: Boolean,
    onFlipYZChanged: (Boolean) -> Unit,
    scale: Float,
    onScaleChanged: (Float) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Opciones de Exportación",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            OptionRow(
                label = "Incluir normales",
                description = "Exportar vectores normales para iluminación",
                checked = includeNormals,
                onCheckedChange = onIncludeNormalsChanged
            )
            
            OptionRow(
                label = "Centrar modelo",
                description = "Mover el centro del modelo al origen",
                checked = centerModel,
                onCheckedChange = onCenterModelChanged
            )
            
            OptionRow(
                label = "Intercambiar Y/Z",
                description = "Para software que usa Z como eje vertical",
                checked = flipYZ,
                onCheckedChange = onFlipYZChanged
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Escala: ${String.format("%.1fx", scale)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = scale,
                onValueChange = onScaleChanged,
                valueRange = 0.1f..10f,
                steps = 98
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0.1x", style = MaterialTheme.typography.labelSmall)
                Text("10x", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun OptionRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ExportButton(
    format: ExportFormat,
    onExport: () -> Unit
) {
    Button(
        onClick = onExport,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Exportar como .${format.extension.uppercase()}",
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun ExportingIndicator() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Exportando modelo...",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun ExportSuccessCard(
    result: ExportFileResult.Success,
    onShare: () -> Unit,
    onExportAnother: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE8F5E9)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "¡Exportación Exitosa!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = result.fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF1B5E20)
            )
            
            Text(
                text = "${result.result.vertexCount} vértices • ${formatFileSize(result.result.fileSizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF388E3C)
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onExportAnother) {
                    Text("Exportar Otro")
                }
                
                Button(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Compartir")
                }
            }
        }
    }
}

@Composable
private fun ExportErrorCard(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Error al Exportar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Reintentar")
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

sealed class ExportState {
    object Idle : ExportState()
    object Exporting : ExportState()
    data class Success(val result: ExportFileResult.Success) : ExportState()
    data class Error(val message: String) : ExportState()
}
