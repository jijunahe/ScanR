package com.scanr.app.ui.screens

import android.content.Intent
import android.opengl.GLSurfaceView
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import java.nio.ShortBuffer
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
    
    var isScanning by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var scanDuration by remember { mutableStateOf(0L) }
    var scanStartTime by remember { mutableStateOf(0L) }
    
    var debugInfo by remember { mutableStateOf("Iniciando...") }
    var depthModeStatus by remember { mutableStateOf("Verificando...") }
    var depthImageStatus by remember { mutableStateOf("--") }
    var pointCloudStatus by remember { mutableStateOf("--") }
    var trackingState by remember { mutableStateOf("--") }
    var totalDepthPoints by remember { mutableStateOf(0) }
    var totalCloudPoints by remember { mutableStateOf(0) }
    var lastDepthSize by remember { mutableStateOf("--") }
    var cameraInfo by remember { mutableStateOf("--") }
    
    val allPoints = remember { mutableStateListOf<Point3D>() }
    val renderer = remember { PointCloudRenderer() }
    
    LaunchedEffect(isScanning) {
        if (isScanning) {
            scanStartTime = System.currentTimeMillis()
            while (isScanning) {
                scanDuration = System.currentTimeMillis() - scanStartTime
                kotlinx.coroutines.delay(100)
            }
        }
    }
    
    fun exportAndShare() {
        scope.launch {
            val points = allPoints.toList()
            if (points.isEmpty()) {
                Toast.makeText(context, "No hay puntos", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            val file = withContext(Dispatchers.IO) {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val file = File(context.cacheDir, "scan_$timestamp.obj")
                file.bufferedWriter().use { writer ->
                    writer.write("# ScanR - ${points.size} puntos\n")
                    points.forEach { writer.write("v ${it.x} ${it.y} ${it.z}\n") }
                }
                file
            }
            
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Compartir"))
            showExportDialog = false
        }
    }
    
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Exportar") },
            text = { Text("${allPoints.size} puntos capturados") },
            confirmButton = { TextButton(onClick = { exportAndShare() }) { Text("Exportar OBJ") } },
            dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text("Cancelar") } }
        )
    }
    
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A15))) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1A1A2E)).padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackPressed) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White)
            }
            Text("ScanR v2", color = Color.White, fontWeight = FontWeight.Bold)
            IconButton(onClick = { if (allPoints.isNotEmpty()) showExportDialog = true }) {
                Icon(Icons.Default.Save, null, tint = Color.White)
            }
        }
        
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(2.dp, Color.Cyan, RoundedCornerShape(8.dp))
            ) {
                DepthCameraView(
                    modifier = Modifier.fillMaxSize(),
                    isScanning = isScanning,
                    onDebugInfo = { info -> debugInfo = info },
                    onDepthModeStatus = { status -> depthModeStatus = status },
                    onDepthImageStatus = { status -> depthImageStatus = status },
                    onPointCloudStatus = { status -> pointCloudStatus = status },
                    onTrackingState = { state -> trackingState = state },
                    onDepthImageSize = { size -> lastDepthSize = size },
                    onCameraInfo = { info -> cameraInfo = info },
                    onDepthPointsCaptured = { count -> totalDepthPoints += count },
                    onCloudPointsCaptured = { count -> totalCloudPoints += count },
                    onPointsCaptured = { points ->
                        allPoints.addAll(points)
                        renderer.addPoints(points)
                    }
                )
                
                Text(
                    "CÁMARA AR",
                    modifier = Modifier.align(Alignment.TopCenter).padding(4.dp),
                    color = Color.Cyan,
                    fontSize = 10.sp
                )
            }
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(2.dp, Color.Magenta, RoundedCornerShape(8.dp))
            ) {
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
                
                Text(
                    "VISTA 3D",
                    modifier = Modifier.align(Alignment.TopCenter).padding(4.dp),
                    color = Color.Magenta,
                    fontSize = 10.sp
                )
            }
        }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A2E))
                .padding(8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("=== DIAGNÓSTICO ===", color = Color.Yellow, fontWeight = FontWeight.Bold)
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Depth Mode:", color = Color.Gray, fontSize = 12.sp)
                Text(depthModeStatus, color = if (depthModeStatus.contains("AUTO") || depthModeStatus.contains("OK")) Color.Green else Color.Red, fontSize = 12.sp)
            }
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Depth Image:", color = Color.Gray, fontSize = 12.sp)
                Text(depthImageStatus, color = if (depthImageStatus.contains("OK") || depthImageStatus.contains("pts")) Color.Green else Color.Yellow, fontSize = 12.sp)
            }
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Depth Size:", color = Color.Gray, fontSize = 12.sp)
                Text(lastDepthSize, color = Color.Cyan, fontSize = 12.sp)
            }
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("PointCloud:", color = Color.Gray, fontSize = 12.sp)
                Text(pointCloudStatus, color = Color.Cyan, fontSize = 12.sp)
            }
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Tracking:", color = Color.Gray, fontSize = 12.sp)
                Text(trackingState, color = if (trackingState == "TRACKING") Color.Green else Color.Yellow, fontSize = 12.sp)
            }
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Camera:", color = Color.Gray, fontSize = 12.sp)
                Text(cameraInfo, color = Color.Cyan, fontSize = 12.sp)
            }
            
            Divider(color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Puntos Depth:", color = Color.Gray, fontSize = 12.sp)
                Text("$totalDepthPoints", color = Color.Green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Puntos Cloud:", color = Color.Gray, fontSize = 12.sp)
                Text("$totalCloudPoints", color = Color.Blue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("TOTAL:", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("${allPoints.size}", color = Color.Yellow, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            
            Text(debugInfo, color = Color.Gray, fontSize = 10.sp, maxLines = 3)
        }
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    allPoints.clear()
                    renderer.clearPoints()
                    totalDepthPoints = 0
                    totalCloudPoints = 0
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))
            ) {
                Icon(Icons.Default.Delete, null)
                Spacer(Modifier.width(4.dp))
                Text("Limpiar")
            }
            
            Button(
                onClick = { isScanning = !isScanning },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isScanning) Color.Red else Color(0xFF4CAF50)
                ),
                modifier = Modifier.height(56.dp)
            ) {
                Icon(if (isScanning) Icons.Default.Stop else Icons.Default.PlayArrow, null, Modifier.size(32.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (isScanning) "STOP (${scanDuration/1000}s)" else "ESCANEAR")
            }
            
            Button(
                onClick = { if (allPoints.isNotEmpty()) showExportDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
            ) {
                Icon(Icons.Default.Share, null)
            }
        }
    }
}

@Composable
fun DepthCameraView(
    modifier: Modifier,
    isScanning: Boolean,
    onDebugInfo: (String) -> Unit,
    onDepthModeStatus: (String) -> Unit,
    onDepthImageStatus: (String) -> Unit,
    onPointCloudStatus: (String) -> Unit,
    onTrackingState: (String) -> Unit,
    onDepthImageSize: (String) -> Unit,
    onCameraInfo: (String) -> Unit,
    onDepthPointsCaptured: (Int) -> Unit,
    onCloudPointsCaptured: (Int) -> Unit,
    onPointsCaptured: (List<Point3D>) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var sceneView by remember { mutableStateOf<ARSceneView?>(null) }
    var lastCaptureTime by remember { mutableStateOf(0L) }
    var depthAvailable by remember { mutableStateOf(false) }
    
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
                        val autoSupported = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
                        val rawSupported = session.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY)
                        
                        onDebugInfo("Auto=$autoSupported, Raw=$rawSupported")
                        
                        when {
                            autoSupported -> {
                                config.depthMode = Config.DepthMode.AUTOMATIC
                                depthAvailable = true
                                onDepthModeStatus("AUTOMATIC OK ✓")
                            }
                            rawSupported -> {
                                config.depthMode = Config.DepthMode.RAW_DEPTH_ONLY
                                depthAvailable = true
                                onDepthModeStatus("RAW OK ✓")
                            }
                            else -> {
                                config.depthMode = Config.DepthMode.DISABLED
                                depthAvailable = false
                                onDepthModeStatus("NO DISPONIBLE ✗")
                            }
                        }
                        
                        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                        config.focusMode = Config.FocusMode.AUTO
                        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    }
                    
                    view.onSessionUpdated = { session, frame ->
                        onTrackingState(frame.camera.trackingState.name)
                        
                        try {
                            val pose = frame.camera.pose
                            onCameraInfo("x:%.2f y:%.2f z:%.2f".format(pose.tx(), pose.ty(), pose.tz()))
                        } catch (e: Exception) {
                            onCameraInfo("Error")
                        }
                        
                        if (isScanning && frame.camera.trackingState == TrackingState.TRACKING) {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastCaptureTime > 150) {
                                lastCaptureTime = currentTime
                                
                                val points = mutableListOf<Point3D>()
                                var depthPts = 0
                                var cloudPts = 0
                                
                                // MÉTODO 1: acquireDepthImage (API estándar)
                                if (depthAvailable) {
                                    try {
                                        val depthImage = frame.acquireDepthImage()
                                        if (depthImage != null) {
                                            val w = depthImage.width
                                            val h = depthImage.height
                                            onDepthImageSize("${w}x${h}")
                                            
                                            val plane = depthImage.planes[0]
                                            val buffer = plane.buffer
                                            val rowStride = plane.rowStride
                                            
                                            val intrinsics = frame.camera.textureIntrinsics
                                            val fx = intrinsics.focalLength[0]
                                            val fy = intrinsics.focalLength[1]
                                            val cx = intrinsics.principalPoint[0]
                                            val cy = intrinsics.principalPoint[1]
                                            
                                            val cameraPose = frame.camera.pose
                                            
                                            val step = 8
                                            for (y in 0 until h step step) {
                                                for (x in 0 until w step step) {
                                                    val byteIndex = y * rowStride + x * 2
                                                    if (byteIndex + 1 < buffer.capacity()) {
                                                        val low = buffer.get(byteIndex).toInt() and 0xFF
                                                        val high = buffer.get(byteIndex + 1).toInt() and 0xFF
                                                        val depthMm = (high shl 8) or low
                                                        val depthM = depthMm / 1000f
                                                        
                                                        if (depthM > 0.15f && depthM < 4f) {
                                                            val localX = (x - cx) * depthM / fx
                                                            val localY = -(y - cy) * depthM / fy
                                                            val localZ = -depthM
                                                            
                                                            val localPoint = floatArrayOf(localX, localY, localZ)
                                                            val worldPoint = FloatArray(3)
                                                            cameraPose.transformPoint(localPoint, 0, worldPoint, 0)
                                                            
                                                            points.add(Point3D(worldPoint[0], worldPoint[1], worldPoint[2], 1f))
                                                            depthPts++
                                                        }
                                                    }
                                                }
                                            }
                                            
                                            depthImage.close()
                                            onDepthImageStatus("OK: $depthPts pts")
                                        } else {
                                            onDepthImageStatus("null - sin imagen")
                                        }
                                    } catch (e: com.google.ar.core.exceptions.NotYetAvailableException) {
                                        onDepthImageStatus("Esperando...")
                                    } catch (e: Exception) {
                                        onDepthImageStatus("Err: ${e.javaClass.simpleName}")
                                        onDebugInfo("Depth: ${e.message}")
                                    }
                                }
                                
                                // MÉTODO 2: PointCloud de ARCore
                                try {
                                    frame.acquirePointCloud().use { pointCloud ->
                                        val pcBuffer = pointCloud.points
                                        val numPoints = pcBuffer.remaining() / 4
                                        onPointCloudStatus("$numPoints puntos")
                                        
                                        var count = 0
                                        while (pcBuffer.hasRemaining() && count < 500) {
                                            val px = pcBuffer.get()
                                            val py = pcBuffer.get()
                                            val pz = pcBuffer.get()
                                            val conf = pcBuffer.get()
                                            
                                            if (conf > 0.3f) {
                                                points.add(Point3D(px, py, pz, conf))
                                                cloudPts++
                                            }
                                            count++
                                        }
                                    }
                                } catch (e: Exception) {
                                    onPointCloudStatus("Err: ${e.javaClass.simpleName}")
                                }
                                
                                if (depthPts > 0) onDepthPointsCaptured(depthPts)
                                if (cloudPts > 0) onCloudPointsCaptured(cloudPts)
                                
                                if (points.isNotEmpty()) {
                                    onPointsCaptured(points)
                                }
                            }
                        }
                    }
                    
                    view.onSessionFailed = { e ->
                        onDebugInfo("Session error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                onDebugInfo("Init error: ${e.message}")
                android.view.View(context)
            }
        }
    )
}
