package com.scanr.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt

data class Point3D(
    val x: Float,
    val y: Float,
    val z: Float,
    val confidence: Float = 1f
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var statusMessage by remember { mutableStateOf("Iniciando cámara AR...") }
    var isARReady by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var errorDetails by remember { mutableStateOf("") }
    var trackingStatus by remember { mutableStateOf("Esperando...") }
    var isScanning by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var scanDuration by remember { mutableStateOf(0L) }
    var scanStartTime by remember { mutableStateOf(0L) }
    
    // Lista de puntos capturados
    val capturedPoints = remember { mutableStateListOf<Point3D>() }
    
    // Actualizar duración del escaneo
    LaunchedEffect(isScanning) {
        if (isScanning) {
            scanStartTime = System.currentTimeMillis()
            while (isScanning) {
                scanDuration = System.currentTimeMillis() - scanStartTime
                kotlinx.coroutines.delay(100)
            }
        }
    }
    
    // Función para exportar a OBJ
    fun exportToOBJ(points: List<Point3D>): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "scan_$timestamp.obj"
        val file = File(context.cacheDir, fileName)
        
        file.bufferedWriter().use { writer ->
            writer.write("# ScanR 3D Scan\n")
            writer.write("# Points: ${points.size}\n")
            writer.write("# Date: ${Date()}\n\n")
            
            // Escribir vértices
            points.forEach { point ->
                writer.write("v ${point.x} ${point.y} ${point.z}\n")
            }
        }
        
        return file
    }
    
    // Función para exportar a PLY
    fun exportToPLY(points: List<Point3D>): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "scan_$timestamp.ply"
        val file = File(context.cacheDir, fileName)
        
        file.bufferedWriter().use { writer ->
            writer.write("ply\n")
            writer.write("format ascii 1.0\n")
            writer.write("comment ScanR 3D Scan\n")
            writer.write("element vertex ${points.size}\n")
            writer.write("property float x\n")
            writer.write("property float y\n")
            writer.write("property float z\n")
            writer.write("property float confidence\n")
            writer.write("end_header\n")
            
            points.forEach { point ->
                writer.write("${point.x} ${point.y} ${point.z} ${point.confidence}\n")
            }
        }
        
        return file
    }
    
    // Función para compartir archivo
    fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(Intent.createChooser(intent, "Compartir escaneo 3D"))
    }
    
    // Diálogo de exportación
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Exportar Escaneo") },
            text = {
                Column {
                    Text("Puntos capturados: ${capturedPoints.size}")
                    Text("Duración: ${scanDuration / 1000}s")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Elige el formato:")
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        scope.launch {
                            isExporting = true
                            val file = withContext(Dispatchers.IO) {
                                exportToOBJ(capturedPoints.toList())
                            }
                            isExporting = false
                            showExportDialog = false
                            shareFile(file)
                            Toast.makeText(context, "Exportado: ${file.name}", Toast.LENGTH_LONG).show()
                        }
                    }) {
                        Text("OBJ")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        scope.launch {
                            isExporting = true
                            val file = withContext(Dispatchers.IO) {
                                exportToPLY(capturedPoints.toList())
                            }
                            isExporting = false
                            showExportDialog = false
                            shareFile(file)
                            Toast.makeText(context, "Exportado: ${file.name}", Toast.LENGTH_LONG).show()
                        }
                    }) {
                        Text("PLY")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ScanR") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                actions = {
                    if (capturedPoints.size > 0) {
                        IconButton(onClick = { showExportDialog = true }) {
                            Icon(Icons.Default.Save, "Exportar")
                        }
                    }
                    IconButton(onClick = {
                        capturedPoints.clear()
                        Toast.makeText(context, "Puntos eliminados", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Delete, "Limpiar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (hasError) {
                // Pantalla de error
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1A1A2E))
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = Color.Red
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Error al iniciar AR",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorDetails,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onBackPressed) {
                        Text("Volver")
                    }
                }
            } else {
                // Vista AR
                ARCameraWithCapture(
                    modifier = Modifier.fillMaxSize(),
                    isScanning = isScanning,
                    onReady = { 
                        isARReady = true
                        statusMessage = "Listo para escanear"
                    },
                    onError = { error ->
                        hasError = true
                        errorDetails = error
                    },
                    onTrackingChanged = { state ->
                        trackingStatus = when (state) {
                            TrackingState.TRACKING -> "Rastreando ✓"
                            TrackingState.PAUSED -> "Pausado"
                            TrackingState.STOPPED -> "Detenido"
                        }
                    },
                    onPointsCaptured = { points ->
                        capturedPoints.addAll(points)
                    }
                )
                
                // UI superpuesta
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Status bar superior
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Black.copy(alpha = 0.7f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = statusMessage, color = Color.White, fontSize = 14.sp)
                                Text(
                                    text = trackingStatus, 
                                    color = if (trackingStatus.contains("✓")) Color.Green else Color.Yellow,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Puntos: ${capturedPoints.size}",
                                    color = Color.Cyan,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isScanning) {
                                    Text(
                                        text = "⏱ ${scanDuration / 1000}s",
                                        color = Color.Yellow
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // Controles inferiores
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Botón exportar
                        FloatingActionButton(
                            onClick = { 
                                if (capturedPoints.size > 0) {
                                    showExportDialog = true
                                } else {
                                    Toast.makeText(context, "Escanea primero", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(56.dp),
                            containerColor = if (capturedPoints.size > 0) Color(0xFF4CAF50) else Color.Gray
                        ) {
                            Icon(Icons.Default.Save, "Exportar")
                        }
                        
                        // Botón de escaneo principal
                        FloatingActionButton(
                            onClick = {
                                isScanning = !isScanning
                                if (isScanning) {
                                    statusMessage = "Escaneando..."
                                    Toast.makeText(context, "Mueve el teléfono lentamente", Toast.LENGTH_SHORT).show()
                                } else {
                                    statusMessage = "Escaneo pausado"
                                }
                            },
                            modifier = Modifier.size(80.dp),
                            containerColor = if (isScanning) Color.Red else Color(0xFF2196F3)
                        ) {
                            Icon(
                                imageVector = if (isScanning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = if (isScanning) "Detener" else "Escanear",
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        
                        // Botón limpiar
                        FloatingActionButton(
                            onClick = {
                                capturedPoints.clear()
                                scanDuration = 0L
                                statusMessage = "Listo para escanear"
                                Toast.makeText(context, "Escaneo limpiado", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(56.dp),
                            containerColor = Color(0xFFFF5722)
                        ) {
                            Icon(Icons.Default.Delete, "Limpiar")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun ARCameraWithCapture(
    modifier: Modifier = Modifier,
    isScanning: Boolean,
    onReady: () -> Unit,
    onError: (String) -> Unit,
    onTrackingChanged: (TrackingState) -> Unit,
    onPointsCaptured: (List<Point3D>) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var sceneView by remember { mutableStateOf<ARSceneView?>(null) }
    var lastCaptureTime by remember { mutableStateOf(0L) }
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                try { sceneView?.destroy() } catch (e: Exception) { }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try { sceneView?.destroy() } catch (e: Exception) { }
        }
    }
    
    AndroidView(
        modifier = modifier,
        factory = { context ->
            try {
                ARSceneView(context).also { view ->
                    sceneView = view
                    
                    view.sessionConfiguration = { session, config ->
                        config.depthMode = if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                            Config.DepthMode.AUTOMATIC
                        } else {
                            Config.DepthMode.DISABLED
                        }
                        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                        config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                        config.focusMode = Config.FocusMode.AUTO
                    }
                    
                    view.onSessionUpdated = { session, frame ->
                        onTrackingChanged(frame.camera.trackingState)
                        
                        // Capturar puntos si está escaneando
                        if (isScanning && frame.camera.trackingState == TrackingState.TRACKING) {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastCaptureTime > 100) { // Cada 100ms
                                lastCaptureTime = currentTime
                                
                                // Capturar puntos de la nube de puntos de ARCore
                                frame.acquirePointCloud().use { pointCloud ->
                                    val points = mutableListOf<Point3D>()
                                    val buffer = pointCloud.points
                                    
                                    while (buffer.hasRemaining()) {
                                        val x = buffer.get()
                                        val y = buffer.get()
                                        val z = buffer.get()
                                        val confidence = buffer.get()
                                        
                                        if (confidence > 0.3f) {
                                            points.add(Point3D(x, y, z, confidence))
                                        }
                                    }
                                    
                                    if (points.isNotEmpty()) {
                                        onPointsCaptured(points)
                                    }
                                }
                            }
                        }
                    }
                    
                    view.onSessionResumed = { session ->
                        onReady()
                    }
                    
                    view.onSessionFailed = { exception ->
                        onError(exception.message ?: "Error desconocido")
                    }
                }
            } catch (e: Exception) {
                onError("Error: ${e.message}")
                android.view.View(context)
            }
        }
    )
}
