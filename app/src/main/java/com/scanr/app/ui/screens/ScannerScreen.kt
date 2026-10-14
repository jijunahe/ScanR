package com.scanr.app.ui.screens

import android.content.Intent
import android.graphics.ImageFormat
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
import com.google.ar.core.*
import com.scanr.app.ui.viewer.Point3D
import com.scanr.app.ui.viewer.PointCloudRenderer
import io.github.sceneview.ar.ARSceneView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
    var depthSupported by remember { mutableStateOf(false) }
    var capturedPointCount by remember { mutableStateOf(0) }
    
    // Lista de puntos para exportar
    val allPoints = remember { mutableStateListOf<Point3D>() }
    
    // Renderer para la vista 3D
    val renderer = remember { PointCloudRenderer() }
    
    // Actualizar duración
    LaunchedEffect(isScanning) {
        if (isScanning) {
            scanStartTime = System.currentTimeMillis()
            while (isScanning) {
                scanDuration = System.currentTimeMillis() - scanStartTime
                capturedPointCount = renderer.getPointCount()
                kotlinx.coroutines.delay(100)
            }
        }
    }
    
    // Exportar
    fun exportAndShare(format: String) {
        scope.launch {
            val points = allPoints.toList()
            if (points.isEmpty()) {
                Toast.makeText(context, "No hay puntos para exportar", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            val file = withContext(Dispatchers.IO) {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "scan_$timestamp.$format"
                val file = File(context.cacheDir, fileName)
                
                file.bufferedWriter().use { writer ->
                    if (format == "ply") {
                        writer.write("ply\nformat ascii 1.0\n")
                        writer.write("element vertex ${points.size}\n")
                        writer.write("property float x\nproperty float y\nproperty float z\n")
                        writer.write("end_header\n")
                        points.forEach { writer.write("${it.x} ${it.y} ${it.z}\n") }
                    } else {
                        writer.write("# ScanR 3D Scan\n# Points: ${points.size}\n")
                        points.forEach { writer.write("v ${it.x} ${it.y} ${it.z}\n") }
                    }
                }
                file
            }
            
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Compartir $format"))
            showExportDialog = false
        }
    }
    
    // Diálogo de exportación
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Exportar Escaneo") },
            text = {
                Column {
                    Text("Puntos: ${allPoints.size}")
                    Text("Duración: ${scanDuration / 1000}s")
                    if (depthSupported) {
                        Text("✓ Depth API activa", color = Color.Green)
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { exportAndShare("obj") }) { Text("OBJ") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { exportAndShare("ply") }) { Text("PLY") }
                }
            },
            dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text("Cancelar") } }
        )
    }
    
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A15))) {
        if (hasError) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Error, null, Modifier.size(80.dp), tint = Color.Red)
                Spacer(Modifier.height(16.dp))
                Text("Error: $errorDetails", color = Color.White)
                Button(onClick = onBackPressed) { Text("Volver") }
            }
        } else {
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ScanR", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            if (depthSupported) "Depth API ✓" else "Depth no disponible",
                            color = if (depthSupported) Color.Green else Color.Yellow,
                            fontSize = 10.sp
                        )
                    }
                    IconButton(onClick = { if (allPoints.isNotEmpty()) showExportDialog = true }) {
                        Icon(Icons.Default.Save, "Exportar", tint = Color.White)
                    }
                }
                
                // Vista 3D principal
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    // Renderer OpenGL
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            GLSurfaceView(ctx).apply {
                                setEGLContextClientVersion(2)
                                setRenderer(renderer)
                                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                            }
                        }
                    )
                    
                    // Indicadores
                    Column(
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                    ) {
                        Text("Puntos: $capturedPointCount", color = Color.Cyan, fontWeight = FontWeight.Bold)
                        Text(trackingStatus, color = if (trackingStatus.contains("✓")) Color.Green else Color.Yellow, fontSize = 12.sp)
                    }
                    
                    // Vista cámara pequeña (PiP)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                            .size(width = 140.dp, height = 180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    ) {
                        DepthCameraView(
                            modifier = Modifier.fillMaxSize(),
                            isScanning = isScanning,
                            onReady = { isARReady = true },
                            onDepthSupported = { supported -> depthSupported = supported },
                            onError = { error -> hasError = true; errorDetails = error },
                            onTrackingChanged = { state ->
                                trackingStatus = when (state) {
                                    TrackingState.TRACKING -> "Rastreando ✓"
                                    TrackingState.PAUSED -> "Pausado ⏸"
                                    TrackingState.STOPPED -> "Detenido ⏹"
                                }
                            },
                            onPointsCaptured = { points ->
                                allPoints.addAll(points)
                                renderer.addPoints(points)
                            }
                        )
                    }
                    
                    // Controles
                    Column(
                        modifier = Modifier.align(Alignment.CenterEnd).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FloatingActionButton(
                            onClick = { allPoints.clear(); renderer.clearPoints() },
                            modifier = Modifier.size(48.dp),
                            containerColor = Color(0xFF333333)
                        ) { Icon(Icons.Default.Delete, "Limpiar", tint = Color.White) }
                        
                        FloatingActionButton(
                            onClick = {
                                isScanning = !isScanning
                                if (isScanning) Toast.makeText(context, "Apunta al objeto y muévete lento", Toast.LENGTH_LONG).show()
                            },
                            modifier = Modifier.size(72.dp),
                            containerColor = if (isScanning) Color.Red else Color(0xFFE91E63)
                        ) {
                            Icon(
                                if (isScanning) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                                "Escanear", tint = Color.White, modifier = Modifier.size(36.dp)
                            )
                        }
                        
                        FloatingActionButton(
                            onClick = { if (allPoints.isNotEmpty()) showExportDialog = true },
                            modifier = Modifier.size(48.dp),
                            containerColor = if (allPoints.isNotEmpty()) Color(0xFF4CAF50) else Color.Gray
                        ) { Icon(Icons.Default.Check, "Guardar", tint = Color.White) }
                    }
                    
                    // Timer
                    if (isScanning) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                                .background(Color.Red, RoundedCornerShape(20.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("⏺ ${scanDuration / 1000}s | ${capturedPointCount} pts", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DepthCameraView(
    modifier: Modifier = Modifier,
    isScanning: Boolean,
    onReady: () -> Unit,
    onDepthSupported: (Boolean) -> Unit,
    onError: (String) -> Unit,
    onTrackingChanged: (TrackingState) -> Unit,
    onPointsCaptured: (List<Point3D>) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var sceneView by remember { mutableStateOf<ARSceneView?>(null) }
    var lastCaptureTime by remember { mutableStateOf(0L) }
    var depthEnabled by remember { mutableStateOf(false) }
    
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
                        // Verificar y habilitar Depth API
                        val depthSupported = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
                        depthEnabled = depthSupported
                        onDepthSupported(depthSupported)
                        
                        config.depthMode = if (depthSupported) {
                            Log.d("ScanR", "Depth API HABILITADA")
                            Config.DepthMode.AUTOMATIC
                        } else {
                            Log.d("ScanR", "Depth API no soportada")
                            Config.DepthMode.DISABLED
                        }
                        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                        config.focusMode = Config.FocusMode.AUTO
                        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    }
                    
                    view.onSessionUpdated = { session, frame ->
                        onTrackingChanged(frame.camera.trackingState)
                        
                        if (isScanning && frame.camera.trackingState == TrackingState.TRACKING) {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastCaptureTime > 100) { // 10 FPS
                                lastCaptureTime = currentTime
                                
                                val points = mutableListOf<Point3D>()
                                
                                // Método 1: Usar Depth Image si está disponible
                                if (depthEnabled) {
                                    try {
                                        frame.acquireDepthImage16Bits()?.use { depthImage ->
                                            val width = depthImage.width
                                            val height = depthImage.height
                                            val plane = depthImage.planes[0]
                                            val buffer = plane.buffer
                                            
                                            val intrinsics = frame.camera.textureIntrinsics
                                            val fx = intrinsics.focalLength[0]
                                            val fy = intrinsics.focalLength[1]
                                            val cx = intrinsics.principalPoint[0]
                                            val cy = intrinsics.principalPoint[1]
                                            
                                            val cameraPose = frame.camera.pose
                                            
                                            // Muestrear cada N pixeles para no tener demasiados puntos
                                            val step = 8
                                            for (y in 0 until height step step) {
                                                for (x in 0 until width step step) {
                                                    val index = y * plane.rowStride / 2 + x
                                                    if (index < buffer.capacity() / 2) {
                                                        buffer.position(index * 2)
                                                        val depthMm = (buffer.get().toInt() and 0xFF) or 
                                                                     ((buffer.get().toInt() and 0xFF) shl 8)
                                                        val depth = depthMm / 1000f // Convertir a metros
                                                        
                                                        if (depth > 0.1f && depth < 5f) {
                                                            // Convertir pixel a coordenadas 3D
                                                            val localX = (x - cx) * depth / fx
                                                            val localY = (y - cy) * depth / fy
                                                            val localZ = depth
                                                            
                                                            // Transformar a coordenadas mundo
                                                            val worldPoint = cameraPose.transformPoint(
                                                                floatArrayOf(localX, -localY, -localZ)
                                                            )
                                                            
                                                            points.add(Point3D(
                                                                worldPoint[0],
                                                                worldPoint[1],
                                                                worldPoint[2],
                                                                1f
                                                            ))
                                                        }
                                                    }
                                                }
                                            }
                                            buffer.rewind()
                                        }
                                    } catch (e: Exception) {
                                        Log.e("ScanR", "Error depth image: ${e.message}")
                                    }
                                }
                                
                                // Método 2: PointCloud de ARCore (complementario)
                                try {
                                    frame.acquirePointCloud().use { pointCloud ->
                                        val buffer = pointCloud.points
                                        while (buffer.hasRemaining()) {
                                            val x = buffer.get()
                                            val y = buffer.get()
                                            val z = buffer.get()
                                            val confidence = buffer.get()
                                            if (confidence > 0.5f) {
                                                points.add(Point3D(x, y, z, confidence))
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("ScanR", "Error point cloud: ${e.message}")
                                }
                                
                                if (points.isNotEmpty()) {
                                    onPointsCaptured(points)
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

// Extension function para transformar puntos
fun Pose.transformPoint(point: FloatArray): FloatArray {
    val result = FloatArray(3)
    val rotated = FloatArray(3)
    
    // Rotar
    val qx = rotationQuaternion[0]
    val qy = rotationQuaternion[1]
    val qz = rotationQuaternion[2]
    val qw = rotationQuaternion[3]
    
    val px = point[0]
    val py = point[1]
    val pz = point[2]
    
    rotated[0] = (1 - 2*qy*qy - 2*qz*qz) * px + (2*qx*qy - 2*qz*qw) * py + (2*qx*qz + 2*qy*qw) * pz
    rotated[1] = (2*qx*qy + 2*qz*qw) * px + (1 - 2*qx*qx - 2*qz*qz) * py + (2*qy*qz - 2*qx*qw) * pz
    rotated[2] = (2*qx*qz - 2*qy*qw) * px + (2*qy*qz + 2*qx*qw) * py + (1 - 2*qx*qx - 2*qy*qy) * pz
    
    // Trasladar
    result[0] = rotated[0] + translation[0]
    result[1] = rotated[1] + translation[1]
    result[2] = rotated[2] + translation[2]
    
    return result
}
