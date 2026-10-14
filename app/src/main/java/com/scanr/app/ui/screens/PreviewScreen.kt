package com.scanr.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ThreeDRotation
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scanr.app.data.PointCloud
import com.scanr.app.ui.viewer.PointCloudGLSurfaceView
import com.scanr.app.ui.viewer.PointCloudViewer
import java.text.NumberFormat

data class PreviewColorScheme(
    val name: String,
    val background: Color,
    val pointColor: Color
)

val colorSchemes = listOf(
    PreviewColorScheme("Océano", Color(0xFF1A1A2E), Color(0xFF4FC3F7)),
    PreviewColorScheme("Bosque", Color(0xFF1B2D1B), Color(0xFF81C784)),
    PreviewColorScheme("Atardecer", Color(0xFF2D1B1B), Color(0xFFFFAB91)),
    PreviewColorScheme("Noche", Color(0xFF0D0D0D), Color(0xFFCE93D8)),
    PreviewColorScheme("Claro", Color(0xFFF5F5F5), Color(0xFF1976D2)),
    PreviewColorScheme("Dorado", Color(0xFF1A1A1A), Color(0xFFFFD54F))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    pointCloud: PointCloud,
    scanDuration: Long,
    onExport: () -> Unit,
    onNewScan: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pointSize by remember { mutableFloatStateOf(5f) }
    var colorSchemeIndex by remember { mutableIntStateOf(0) }
    var showControls by remember { mutableStateOf(true) }
    var glView by remember { mutableStateOf<PointCloudGLSurfaceView?>(null) }
    
    val currentScheme = colorSchemes[colorSchemeIndex]
    val numberFormat = remember { NumberFormat.getNumberInstance() }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vista Previa 3D") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { showControls = !showControls }) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = "Mostrar controles"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = currentScheme.background,
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            PointCloudViewer(
                pointCloud = pointCloud,
                modifier = Modifier.fillMaxSize(),
                backgroundColor = currentScheme.background,
                pointColor = currentScheme.pointColor,
                pointSize = pointSize,
                onViewCreated = { glView = it }
            )
            
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                StatsCard(
                    pointCount = pointCloud.size,
                    boundingBox = pointCloud.boundingBoxSize,
                    duration = scanDuration,
                    numberFormat = numberFormat,
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                ViewControls(
                    onResetView = { glView?.resetView() },
                    onNextColorScheme = {
                        colorSchemeIndex = (colorSchemeIndex + 1) % colorSchemes.size
                    },
                    currentSchemeName = currentScheme.name,
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                PointSizeControl(
                    pointSize = pointSize,
                    onPointSizeChange = { pointSize = it }
                )
            }
            
            InteractionHint(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 100.dp)
            )
            
            BottomActions(
                onNewScan = onNewScan,
                onExport = onExport,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun StatsCard(
    pointCount: Int,
    boundingBox: com.scanr.app.data.Vector3,
    duration: Long,
    numberFormat: NumberFormat,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ThreeDRotation,
                    contentDescription = null,
                    tint = Color(0xFF4FC3F7),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Modelo Escaneado",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            StatRow(label = "Puntos", value = numberFormat.format(pointCount))
            StatRow(label = "Ancho", value = String.format("%.2f m", boundingBox.x))
            StatRow(label = "Alto", value = String.format("%.2f m", boundingBox.y))
            StatRow(label = "Prof.", value = String.format("%.2f m", boundingBox.z))
            StatRow(label = "Tiempo", value = "${duration / 1000}s")
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ViewControls(
    onResetView: () -> Unit,
    onNextColorScheme: () -> Unit,
    currentSchemeName: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SmallFloatingActionButton(
            onClick = onResetView,
            containerColor = Color.Black.copy(alpha = 0.7f),
            contentColor = Color.White
        ) {
            Icon(
                imageVector = Icons.Default.CenterFocusStrong,
                contentDescription = "Centrar vista",
                modifier = Modifier.size(20.dp)
            )
        }
        
        SmallFloatingActionButton(
            onClick = onNextColorScheme,
            containerColor = Color.Black.copy(alpha = 0.7f),
            contentColor = Color.White
        ) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = "Cambiar color",
                modifier = Modifier.size(20.dp)
            )
        }
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = currentSchemeName,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun PointSizeControl(
    pointSize: Float,
    onPointSizeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.width(200.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Tamaño: ${String.format("%.1f", pointSize)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
            Slider(
                value = pointSize,
                onValueChange = onPointSizeChange,
                valueRange = 1f..15f,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun InteractionHint(modifier: Modifier = Modifier) {
    var showHint by remember { mutableStateOf(true) }
    
    AnimatedVisibility(
        visible = showHint,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(20.dp),
            onClick = { showHint = false }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Arrastra para rotar • Pellizca para zoom",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun BottomActions(
    onNewScan: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onNewScan,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Nuevo Escaneo")
        }
        
        Button(
            onClick = onExport,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.Save,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Exportar")
        }
    }
}

@Composable
fun EmptyPreviewScreen(
    onNewScan: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.ThreeDRotation,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = Color.White.copy(alpha = 0.5f)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "No hay datos de escaneo",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Realiza un escaneo para ver\nla vista previa 3D",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("Volver")
                }
                
                Button(onClick = onNewScan) {
                    Icon(
                        imageVector = Icons.Default.ThreeDRotation,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Iniciar Escaneo")
                }
            }
        }
    }
}
