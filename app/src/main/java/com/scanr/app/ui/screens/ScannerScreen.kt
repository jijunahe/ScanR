package com.scanr.app.ui.screens
import android.view.View

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.ar.core.Config
import com.google.ar.core.TrackingState
import com.scanr.app.ar.DepthMode
import com.scanr.app.ui.components.ARStatusOverlay
import com.scanr.app.ui.components.DepthNotSupportedMessage
import com.scanr.app.viewmodel.ScanState
import com.scanr.app.viewmodel.ScannerViewModel
import io.github.sceneview.ar.ARSceneView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat

enum class ScreenState {
    SCANNER,
    PREVIEW,
    EXPORT
}

@Composable
fun ScannerScreen(
    modifier: Modifier = Modifier,
    viewModel: ScannerViewModel = viewModel(),
    onBackPressed: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val pointCount by viewModel.pointCount.collectAsState()
    val statistics by viewModel.statistics.collectAsState()
    val exportState by viewModel.exportState.collectAsState()
    
    var currentScreen by remember { mutableStateOf(ScreenState.SCANNER) }
    var scanDuration by remember { mutableLongStateOf(0L) }
    
    val context = LocalContext.current
    
    LaunchedEffect(Unit) {
        viewModel.initExportManager(context)
    }
    
    LaunchedEffect(scanState) {
        if (scanState is ScanState.Completed) {
            val session = (scanState as ScanState.Completed).session
            scanDuration = session.duration
        }
    }
    
    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
        },
        label = "screen_transition"
    ) { screen ->
        when (screen) {
            ScreenState.SCANNER -> {
                ScannerContent(
                    viewModel = viewModel,
                    uiState = uiState,
                    scanState = scanState,
                    pointCount = pointCount,
                    onBackPressed = onBackPressed,
                    onPreview = {
                        if (viewModel.getPointCloud().size > 0) {
                            currentScreen = ScreenState.PREVIEW
                        }
                    },
                    modifier = modifier
                )
            }
            ScreenState.PREVIEW -> {
                val pointCloud = viewModel.getDownsampledPointCloud()
                if (pointCloud.isEmpty) {
                    EmptyPreviewScreen(
                        onNewScan = {
                            viewModel.reset()
                            currentScreen = ScreenState.SCANNER
                        },
                        onBack = { currentScreen = ScreenState.SCANNER }
                    )
                } else {
                    PreviewScreen(
                        pointCloud = pointCloud,
                        scanDuration = scanDuration,
                        onExport = { currentScreen = ScreenState.EXPORT },
                        onNewScan = {
                            viewModel.reset()
                            currentScreen = ScreenState.SCANNER
                        },
                        onBack = { currentScreen = ScreenState.SCANNER }
                    )
                }
            }
            ScreenState.EXPORT -> {
                ExportScreen(
                    pointCloud = viewModel.getDownsampledPointCloud(),
                    onExport = { format, options ->
                        viewModel.exportPointCloud(format, options)
                    },
                    onShare = {
                        viewModel.getShareIntent()?.let { intent ->
                            context.startActivity(Intent.createChooser(intent, "Compartir modelo 3D"))
                        }
                    },
                    exportState = exportState,
                    onBack = {
                        viewModel.resetExportState()
                        currentScreen = ScreenState.PREVIEW
                    }
                )
            }
        }
    }
}

@Composable
private fun ScannerContent(
    viewModel: ScannerViewModel,
    uiState: com.scanr.app.viewmodel.ScannerUiState,
    scanState: ScanState,
    pointCount: Int,
    onBackPressed: () -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    var trackingState by remember { mutableStateOf(TrackingState.STOPPED) }
    var isDepthSupported by remember { mutableStateOf(false) }
    var isRawDepthSupported by remember { mutableStateOf(false) }
    var isSessionReady by remember { mutableStateOf(false) }
    var showDepthInfo by remember { mutableStateOf(true) }
    var showDepthWarning by remember { mutableStateOf(false) }
    var scanStartTime by remember { mutableLongStateOf(0L) }
    var elapsedTime by remember { mutableLongStateOf(0L) }
    
    val coroutineScope = rememberCoroutineScope()
    
    val depthMode = when {
        isRawDepthSupported -> DepthMode.HARDWARE
        isDepthSupported -> DepthMode.SOFTWARE
        else -> DepthMode.NOT_SUPPORTED
    }
    
    LaunchedEffect(isDepthSupported, isRawDepthSupported) {
        viewModel.updateDepthSupport(isDepthSupported, isRawDepthSupported)
    }
    
    LaunchedEffect(uiState.isScanning) {
        if (uiState.isScanning) {
            scanStartTime = System.currentTimeMillis()
            while (uiState.isScanning) {
                elapsedTime = System.currentTimeMillis() - scanStartTime
                delay(100)
            }
        } else {
            elapsedTime = 0L
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        ARCameraViewWithCapture(
            modifier = Modifier.fillMaxSize(),
            isScanning = uiState.isScanning,
            onSessionCreated = { isSessionReady = true },
            onSessionResumed = { isSessionReady = true },
            onSessionPaused = {
                isSessionReady = false
                if (uiState.isScanning) {
                    viewModel.stopScanning()
                }
            },
            onTrackingStateChanged = { state ->
                trackingState = state
                viewModel.updateTrackingState(state)
            },
            onDepthSupportChecked = { depth, rawDepth ->
                isDepthSupported = depth
                isRawDepthSupported = rawDepth
                showDepthWarning = !depth && !rawDepth
            },
            onFrameUpdate = { frame ->
                if (uiState.isScanning) {
                    coroutineScope.launch {
                        viewModel.onFrame(frame)
                    }
                }
            }
        )
        
        ScannerOverlay(
            trackingState = trackingState,
            isScanning = uiState.isScanning,
            pointCount = pointCount,
            elapsedTime = elapsedTime,
            depthMode = depthMode,
            showDepthInfo = showDepthInfo && !uiState.isScanning,
            isDepthSupported = isDepthSupported,
            isRawDepthSupported = isRawDepthSupported
        )
        
        TopControls(
            onInfoClick = { showDepthInfo = !showDepthInfo },
            onCloseClick = {
                if (uiState.isScanning) {
                    viewModel.cancelScanning()
                }
                onBackPressed()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 16.dp)
        )
        
        AnimatedVisibility(
            visible = showDepthWarning && isSessionReady && !uiState.isScanning,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            DepthNotSupportedMessage()
        }
        
        when (scanState) {
            is ScanState.Processing -> {
                ProcessingOverlay()
            }
            is ScanState.Completed -> {
                ScanCompletedOverlay(
                    pointCount = (scanState as ScanState.Completed).session.pointCloud.size,
                    duration = (scanState as ScanState.Completed).session.duration,
                    onNewScan = { viewModel.reset() },
                    onPreview = onPreview,
                    onClose = onBackPressed
                )
            }
            is ScanState.Error -> {
                ErrorOverlay(
                    message = (scanState as ScanState.Error).message,
                    onRetry = { viewModel.reset() },
                    onClose = onBackPressed
                )
            }
            else -> {}
        }
        
        if (scanState !is ScanState.Completed && scanState !is ScanState.Processing) {
            MainScanButton(
                isScanning = uiState.isScanning,
                isTrackingReady = trackingState == TrackingState.TRACKING,
                pointCount = pointCount,
                onScanToggle = { viewModel.toggleScanning() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            )
        }
    }
}

@Composable
private fun ScannerOverlay(
    trackingState: TrackingState,
    isScanning: Boolean,
    pointCount: Int,
    elapsedTime: Long,
    depthMode: DepthMode,
    showDepthInfo: Boolean,
    isDepthSupported: Boolean,
    isRawDepthSupported: Boolean
) {
    val numberFormat = remember { NumberFormat.getNumberInstance() }
    
    Box(modifier = Modifier.fillMaxSize()) {
        if (!isScanning && showDepthInfo) {
            ARStatusOverlay(
                trackingState = trackingState,
                isDepthSupported = isDepthSupported,
                isRawDepthSupported = isRawDepthSupported,
                depthMode = depthMode,
                showDepthInfo = true
            )
        }
        
        AnimatedVisibility(
            visible = isScanning,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .padding(top = 48.dp)
        ) {
            ScanningStatusCard(
                pointCount = pointCount,
                elapsedTime = elapsedTime,
                trackingState = trackingState,
                numberFormat = numberFormat
            )
        }
        
        if (trackingState != TrackingState.TRACKING && !isScanning) {
            TrackingInstructions(
                trackingState = trackingState,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
private fun ScanningStatusCard(
    pointCount: Int,
    elapsedTime: Long,
    trackingState: TrackingState,
    numberFormat: NumberFormat
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recording")
    val recordingAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recording_alpha"
    )
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.85f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .alpha(recordingAlpha)
                        .clip(CircleShape)
                        .background(Color.Red)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ESCANEANDO",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatTime(elapsedTime),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatBox(
                    value = numberFormat.format(pointCount),
                    label = "Puntos 3D",
                    color = Color(0xFF4FC3F7)
                )
                StatBox(
                    value = when (trackingState) {
                        TrackingState.TRACKING -> "Activo"
                        TrackingState.PAUSED -> "Pausado"
                        TrackingState.STOPPED -> "Detenido"
                    },
                    label = "Estado",
                    color = when (trackingState) {
                        TrackingState.TRACKING -> Color(0xFF81C784)
                        TrackingState.PAUSED -> Color(0xFFFFB74D)
                        TrackingState.STOPPED -> Color(0xFFE57373)
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Color(0xFF4FC3F7),
                trackColor = Color.White.copy(alpha = 0.2f)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Mueve lentamente alrededor del objeto",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StatBox(
    value: String,
    label: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun TrackingInstructions(
    trackingState: TrackingState,
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
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = Color.White,
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = when (trackingState) {
                    TrackingState.PAUSED -> "Rastreo pausado"
                    TrackingState.STOPPED -> "Buscando superficies..."
                    TrackingState.TRACKING -> ""
                },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when (trackingState) {
                    TrackingState.PAUSED -> "Mueve el dispositivo lentamente"
                    TrackingState.STOPPED -> "Apunta a una superficie bien iluminada"
                    TrackingState.TRACKING -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TopControls(
    onInfoClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SmallFloatingActionButton(
            onClick = onInfoClick,
            containerColor = Color.Black.copy(alpha = 0.5f),
            contentColor = Color.White
        ) {
            Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(20.dp))
        }
        SmallFloatingActionButton(
            onClick = onCloseClick,
            containerColor = Color.Black.copy(alpha = 0.5f),
            contentColor = Color.White
        ) {
            Icon(Icons.Default.Close, contentDescription = "Cerrar", modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun MainScanButton(
    isScanning: Boolean,
    isTrackingReady: Boolean,
    pointCount: Int,
    onScanToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonScale by animateFloatAsState(
        targetValue = if (isScanning) 1.1f else 1f,
        animationSpec = tween(300),
        label = "button_scale"
    )
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(
            visible = !isTrackingReady && !isScanning,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Preparando cámara...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Box(contentAlignment = Alignment.Center) {
            if (isScanning) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .scale(buttonScale)
                        .clip(CircleShape)
                        .border(3.dp, Color.Red.copy(alpha = 0.5f), CircleShape)
                )
            }
            
            LargeFloatingActionButton(
                onClick = onScanToggle,
                modifier = Modifier.scale(buttonScale),
                containerColor = if (isScanning) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                AnimatedContent(
                    targetState = isScanning,
                    transitionSpec = {
                        scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut()
                    },
                    label = "button_icon"
                ) { scanning ->
                    Icon(
                        imageVector = if (scanning) Icons.Default.Stop else Icons.Default.CameraAlt,
                        contentDescription = if (scanning) "Detener" else "Escanear",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = if (isScanning) "Toca para finalizar" else "Toca para escanear",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ScanCompletedOverlay(
    pointCount: Int,
    duration: Long,
    onNewScan: () -> Unit,
    onPreview: () -> Unit,
    onClose: () -> Unit
) {
    val numberFormat = remember { NumberFormat.getNumberInstance() }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF4FC3F7), Color(0xFF2196F3))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = Color.White
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Text(
                    text = "¡Escaneo Completado!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(40.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = numberFormat.format(pointCount),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Puntos 3D",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatTime(duration),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Duración",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(28.dp))
                
                Button(
                    onClick = onPreview,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ver Vista Previa 3D")
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onNewScan,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nuevo")
                    }
                    Button(
                        onClick = onClose,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("Cerrar")
                    }
                }
            }
        }
    }
}

@Composable
fun ProcessingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(56.dp),
                    strokeWidth = 4.dp
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Procesando...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Optimizando nube de puntos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ErrorOverlay(
    message: String,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Error",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) { Text("Reintentar") }
                    Button(onClick = onClose) { Text("Cerrar") }
                }
            }
        }
    }
}

@Composable

@Composable
fun ARCameraViewWithCapture(
    modifier: Modifier = Modifier,
    isScanning: Boolean,
    onSessionCreated: (ARSceneView) -> Unit = {},
    onSessionResumed: () -> Unit = {},
    onSessionPaused: () -> Unit = {},
    onTrackingStateChanged: (TrackingState) -> Unit = {},
    onDepthSupportChecked: (Boolean, Boolean) -> Unit = { _, _ -> },
    onFrameUpdate: (com.google.ar.core.Frame) -> Unit = {},
    onError: (String) -> Unit = {}
) {
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var sceneView by remember { mutableStateOf<ARSceneView?>(null) }
    var hasError by remember { mutableStateOf(false) }
    
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    onSessionResumed()
                }
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    onSessionPaused()
                }
                androidx.lifecycle.Lifecycle.Event.ON_DESTROY -> {
                    try {
                        sceneView?.destroy()
                    } catch (e: Exception) {
                        // Ignore destroy errors
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { 
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                sceneView?.destroy()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    if (hasError) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Error al iniciar la cámara AR.\nVerifica que ARCore esté instalado.",
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
        return
    }
    
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            try {
                ARSceneView(context).apply {
                    sceneView = this
                    
                    // Configure session with error handling
                    try {
                        sessionConfiguration = { session, config ->
                            val isDepthSupported = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
                            val isRawDepthSupported = session.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY)
                            onDepthSupportChecked(isDepthSupported, isRawDepthSupported)
                            
                            config.depthMode = if (isDepthSupported) {
                                Config.DepthMode.AUTOMATIC
                            } else {
                                Config.DepthMode.DISABLED
                            }
                            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                            config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                            config.focusMode = Config.FocusMode.AUTO
                        }
                    } catch (e: Exception) {
                        onError("Error configurando sesión AR: \${e.message}")
                    }
                    
                    // Set frame update listener
                    onFrame = { frame ->
                        try {
                            onTrackingStateChanged(frame.camera.trackingState)
                            if (isScanning && frame.camera.trackingState == TrackingState.TRACKING) {
                                onFrameUpdate(frame)
                            }
                        } catch (e: Exception) {
                            // Ignore frame errors
                        }
                    }
                    
                    onSessionCreated(this)
                }
            } catch (e: Exception) {
                hasError = true
                onError("Error creando vista AR: \${e.message}")
                // Return empty view
                android.view.View(context)
            }
        },
        update = { view ->
            if (view is ARSceneView) {
                view.onFrame = { frame ->
                    try {
                        onTrackingStateChanged(frame.camera.trackingState)
                        if (isScanning && frame.camera.trackingState == TrackingState.TRACKING) {
                            onFrameUpdate(frame)
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
        }
    )
}

private fun formatTime(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / 60000)
    return if (minutes > 0) {
        String.format("%d:%02d", minutes, seconds)
    } else {
        String.format("%ds", seconds)
    }
}
