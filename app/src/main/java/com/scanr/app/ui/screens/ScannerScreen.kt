package com.scanr.app.ui.screens

import android.content.Intent
import android.opengl.GLSurfaceView
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.ar.core.Config
import com.google.ar.core.TrackingState
import com.scanr.app.ui.viewer.Point3D
import com.scanr.app.ui.viewer.PointCloudRenderer
import io.github.sceneview.ar.ARSceneView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isARReady by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var errorDetails by remember { mutableStateOf("") }
    var trackingStatus by remember { mutableStateOf("Iniciando...") }
    var isScanning by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var scanDuration by remember { mutableStateOf(0L) }
    var scanStartTime by remember { mutableStateOf(0L) }
    var fps by remember { mutableStateOf(0) }
    var frameCount by remember { mutableStateOf(0) }
    var lastFpsTime by remember { mutableStateOf(System.currentTimeMillis()) }
    
    // Renderer para la vista 3D
    val renderer = remember { PointCloudRenderer() }
    var glSurfaceView by remember { mutableStateOf<GLSurfaceView?>(null) }
    
    // Actualizar duración y FPS
    LaunchedEffect(isScanning) {
        if (isScanning) {
            scanStartTime = System.currentTimeMillis()
            while (isScanning) {
                scanDuration = System.currentTimeMillis() - scanStartTime
                
                // Calcular FPS
                val now = System.currentTimeMillis()
                if (now - lastFpsTime >= 1000) {
                    fps = frameCount
                    frameCount = 0
                    lastFpsTime = now
                }
                
                kotlinx.coroutines.delay(100)
            }
        }
    }
    
    // Funciones de exportación
    fun exportToOBJ(points: List<Point3D>): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(context.cacheDir, "scan_$timestamp.obj")
        file.bufferedWriter().use { writer ->
            writer.write("# ScanR 3D Scan - ${points.size} points\n")
            points.forEach { writer.write("v ${it.x} ${it.y} ${it.z}\n") }
        }
        return file
    }
    
    fun exportToPLY(points: List<Point3D>): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(context.cacheDir, "scan_$timestamp.ply")
        file.bufferedWriter().use { writer ->
            writer.write("ply\nformat ascii 1.0\nelement vertex ${points.size}\n")
            writer.write("property float x\nproperty float y\nproperty float z\nend_header\n")
            points.forEach { writer.write("${it.x} ${it.y} ${it.z}\n") }
        }
        return file
    }
    
    fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
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
                    Text("Puntos: ${renderer.getPointCount()}")
                    Text("Duración: ${scanDuration / 1000}s")
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        scope.launch {
                            // TODO: Get points from renderer
                            showExportDialog = false
                            Toast.makeText(context, "Exportando OBJ...", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("OBJ") }
                    TextButton(onClick = {
                        scope.launch {
                            showExportDialog = false
                            Toast.makeText(context, "Exportando PLY...", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("PLY") }
                }
            },
            dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text("Cancelar") } }
        )
    }
    
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A15))) {
        if (hasError) {
            // Pantalla de error
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Error, null, Modifier.size(80.dp), tint = Color.Red)
                Spacer(Modifier.height(16.dp))
                Text("Error: $errorDetails", color = Color.White)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onBackPressed) { Text("Volver") }
            }
        } else {
            // Vista principal: Modelo 3D
            Column(modifier = Modifier.fillMaxSize()) {
                // Barra superior
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1A2E))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White)
                    }
                    Text("ScanR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Row {
                        IconButton(onClick = { showExportDialog = true }) {
                            Icon(Icons.Default.Save, "Exportar", tint = Color.White)
                        }
                    }
                }
                
                // Vista 3D principal (ocupa la mayor parte)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // GLSurfaceView para renderizar puntos 3D
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            GLSurfaceView(ctx).apply {
                                setEGLContextClientVersion(2)
                                setRenderer(renderer)
                                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                                glSurfaceView = this
                            }
                        }
                    )
                    
                    // Indicadores superpuestos
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    ) {
                        // FPS
                        Text(
                            text = "FPS $fps",
                            color = Color.Green,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Puntos ${renderer.getPointCount()}",
                            color = Color.Cyan,
                            fontSize = 12.sp
                        )
                    }
                    
                    // Barra de calidad/distancia
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text("Cerca", color = Color.Gray, fontSize = 10.sp)
                        Spacer(Modifier.width(8.dp))
                        LinearProgressIndicator(
                            progress = 0.5f,
                            modifier = Modifier.width(100.dp),
                            color = Color.Green,
                            trackColor = Color.DarkGray
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Lejos", color = Color.Gray, fontSize = 10.sp)
                    }
                    
                    // Vista de cámara pequeña (Picture-in-Picture)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                            .size(width = 120.dp, height = 160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    ) {
                        ARCameraView(
                            modifier = Modifier.fillMaxSize(),
                            isScanning = isScanning,
                            onReady = { isARReady = true },
                            onError = { error -> hasError = true; errorDetails = error },
                            onTrackingChanged = { state ->
                                trackingStatus = when (state) {
                                    TrackingState.TRACKING -> "✓"
                                    TrackingState.PAUSED -> "⏸"
                                    TrackingState.STOPPED -> "⏹"
                                }
                            },
                            onPointsCaptured = { points ->
                                frameCount++
                                renderer.addPoints(points)
                            }
                        )
                        
                        // Indicador de estado en la cámara
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .background(
                                    if (trackingStatus == "✓") Color.Green else Color.Yellow,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(trackingStatus, fontSize = 10.sp, color = Color.Black)
                        }
                    }
                    
                    // Controles a la derecha
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Botón cerrar/cancelar
                        FloatingActionButton(
                            onClick = onBackPressed,
                            modifier = Modifier.size(48.dp),
                            containerColor = Color(0xFF333333)
                        ) {
                            Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
                        }
                        
                        // Botón de escaneo (grande, rojo/verde)
                        FloatingActionButton(
                            onClick = {
                                isScanning = !isScanning
                                if (isScanning) {
                                    Toast.makeText(context, "Escaneando...", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(64.dp),
                            containerColor = if (isScanning) Color.Red else Color(0xFFE91E63)
                        ) {
                            Icon(
                                if (isScanning) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                                "Escanear",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        
                        // Botón confirmar/guardar
                        FloatingActionButton(
                            onClick = { 
                                if (renderer.getPointCount() > 0) {
                                    showExportDialog = true
                                } else {
                                    Toast.makeText(context, "Escanea primero", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(48.dp),
                            containerColor = Color(0xFF4CAF50)
                        ) {
                            Icon(Icons.Default.Check, "Guardar", tint = Color.White)
                        }
                    }
                    
                    // Timer de escaneo
                    if (isScanning) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                                .background(Color.Red, RoundedCornerShape(20.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "⏺ ${scanDuration / 1000}s",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ARCameraView(
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
                        config.focusMode = Config.FocusMode.AUTO
                    }
                    
                    view.onSessionUpdated = { session, frame ->
                        onTrackingChanged(frame.camera.trackingState)
                        
                        if (isScanning && frame.camera.trackingState == TrackingState.TRACKING) {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastCaptureTime > 50) {
                                lastCaptureTime = currentTime
                                
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
                    
                    view.onSessionResumed = { onReady() }
                    view.onSessionFailed = { e -> onError(e.message ?: "Error AR") }
                }
            } catch (e: Exception) {
                onError("Error: ${e.message}")
                android.view.View(context)
            }
        }
    )
}
