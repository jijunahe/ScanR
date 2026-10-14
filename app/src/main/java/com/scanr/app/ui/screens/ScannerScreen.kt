package com.scanr.app.ui.screens

import android.content.Intent
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import com.scanr.app.ui.viewer.Point3D
import com.scanr.app.ui.viewer.PointCloudRenderer
import io.github.sceneview.ar.ARSceneView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.EnumSet
import java.util.Locale

/**
 * Plain JVM flag. Compose state read from SceneView's render thread stays at the
 * initial value, so the scan button never reached the depth capture.
 */
private class ScanGate {
    @Volatile var scanning: Boolean = false
}

@Composable
fun ScannerScreen(
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gate = remember { ScanGate() }

    var isScanning by remember { mutableStateOf(false) }
    gate.scanning = isScanning

    var showExportDialog by remember { mutableStateOf(false) }
    var scanDuration by remember { mutableStateOf(0L) }
    var scanStartTime by remember { mutableStateOf(0L) }

    var debugInfo by remember { mutableStateOf("Iniciando...") }
    var depthModeStatus by remember { mutableStateOf("Verificando...") }
    var depthImageStatus by remember { mutableStateOf("esperando frame") }
    var pointCloudStatus by remember { mutableStateOf("--") }
    var trackingState by remember { mutableStateOf("--") }
    var flagSeen by remember { mutableStateOf("OFF") }
    var frameCount by remember { mutableStateOf(0) }
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
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                File(context.cacheDir, "scan_$timestamp.obj").apply {
                    bufferedWriter().use { writer ->
                        writer.write("# ScanR - ${points.size} puntos\n")
                        points.forEach { writer.write("v ${it.x} ${it.y} ${it.z}\n") }
                    }
                }
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

    Column(modifier = modifier.fillMaxSize().background(Color(0xFF0A0A15))) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1A1A2E)).padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackPressed) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White)
            }
            Text("ScanR v3", color = Color.White, fontWeight = FontWeight.Bold)
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
                    gate = gate,
                    onDebugInfo = { debugInfo = it },
                    onDepthModeStatus = { depthModeStatus = it },
                    onDepthImageStatus = { depthImageStatus = it },
                    onPointCloudStatus = { pointCloudStatus = it },
                    onTrackingState = { trackingState = it },
                    onFlagSeen = { flagSeen = it },
                    onFrame = { frameCount = it },
                    onDepthImageSize = { lastDepthSize = it },
                    onCameraInfo = { cameraInfo = it },
                    onDepthPointsCaptured = { totalDepthPoints += it },
                    onCloudPointsCaptured = { totalCloudPoints += it },
                    onPointsCaptured = { points ->
                        if (allPoints.size < 80_000) {
                            allPoints.addAll(points)
                            renderer.addPoints(points)
                        }
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
            Text("=== DIAGNÓSTICO v3 ===", color = Color.Yellow, fontWeight = FontWeight.Bold)
            StatusRow("Depth config", depthModeStatus, depthModeStatus.contains("AUTOMATIC"))
            StatusRow("Depth image", depthImageStatus, depthImageStatus.startsWith("OK"))
            StatusRow("Depth size", lastDepthSize, lastDepthSize != "--")
            StatusRow("PointCloud", pointCloudStatus, pointCloudStatus.any { it.isDigit() })
            StatusRow("Tracking", trackingState, trackingState == "TRACKING")
            StatusRow("Flag escaneo", flagSeen, flagSeen == "ON")
            StatusRow("Frames", frameCount.toString(), frameCount > 0)
            StatusRow("Camera", cameraInfo, true)
            Divider(color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))
            StatusRow("Puntos Depth", totalDepthPoints.toString(), totalDepthPoints > 0)
            StatusRow("Puntos Cloud", totalCloudPoints.toString(), totalCloudPoints > 0)
            StatusRow("TOTAL", allPoints.size.toString(), allPoints.isNotEmpty())
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
                Icon(
                    if (isScanning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    null,
                    Modifier.size(32.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(if (isScanning) "STOP (${scanDuration / 1000}s)" else "ESCANEAR")
            }
            Button(
                onClick = { if (allPoints.isNotEmpty()) showExportDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(4.dp))
                Text("Guardar")
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray, fontSize = 12.sp)
        Text(
            value,
            color = if (ok) Color(0xFF69F0AE) else Color(0xFFFFD54F),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DepthCameraView(
    modifier: Modifier,
    gate: ScanGate,
    onDebugInfo: (String) -> Unit,
    onDepthModeStatus: (String) -> Unit,
    onDepthImageStatus: (String) -> Unit,
    onPointCloudStatus: (String) -> Unit,
    onTrackingState: (String) -> Unit,
    onFlagSeen: (String) -> Unit,
    onFrame: (Int) -> Unit,
    onDepthImageSize: (String) -> Unit,
    onCameraInfo: (String) -> Unit,
    onDepthPointsCaptured: (Int) -> Unit,
    onCloudPointsCaptured: (Int) -> Unit,
    onPointsCaptured: (List<Point3D>) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var sceneView by remember { mutableStateOf<ARSceneView?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                try { sceneView?.destroy() } catch (_: Exception) { }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try { sceneView?.destroy() } catch (_: Exception) { }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            try {
                ARSceneView(context).also { view ->
                    sceneView = view
                    view.planeRenderer.isEnabled = false
                    val state = CaptureState()

                    view.sessionConfiguration = { session, config ->
                        applyDepthConfig(session, config)
                    }

                    view.onSessionCreated = { session ->
                        val note = enableDepthCamera(session)
                        mainHandler.post { onDebugInfo(note) }
                    }

                    view.onSessionResumed = { session ->
                        try {
                            val config = session.config
                            applyDepthConfig(session, config)
                            session.configure(config)
                            mainHandler.post {
                                onDepthModeStatus(session.config.depthMode.name)
                            }
                        } catch (e: Throwable) {
                            mainHandler.post { onDebugInfo("resume: ${e.message}") }
                        }
                    }

                    view.onSessionUpdated = { session, frame ->
                        state.frames++
                        val scanning = gate.scanning
                        if (state.frames % 15 == 0) {
                            val pose = frame.camera.pose
                            mainHandler.post {
                                onFrame(state.frames)
                                onFlagSeen(if (scanning) "ON" else "OFF")
                                onTrackingState(frame.camera.trackingState.name)
                                onDepthModeStatus(session.config.depthMode.name)
                                onCameraInfo(
                                    "x:%.2f y:%.2f z:%.2f".format(pose.tx(), pose.ty(), pose.tz())
                                )
                            }
                        }

                        val now = System.currentTimeMillis()
                        val ready = now - state.lastCaptureMs >= 120L &&
                            frame.camera.trackingState == TrackingState.TRACKING
                        if (ready) {
                            state.lastCaptureMs = now
                            val points = ArrayList<Point3D>(1500)
                            val depthResult = sampleDepth(frame, points)
                            val cloudCount = if (scanning) sampleFeaturePoints(frame, points) else 0
                            mainHandler.post {
                                onDepthImageStatus(depthResult.status)
                                onDepthImageSize(depthResult.size)
                                onPointCloudStatus(if (scanning) "$cloudCount pts" else "en pausa")
                                if (scanning && points.isNotEmpty()) {
                                    if (depthResult.count > 0) onDepthPointsCaptured(depthResult.count)
                                    if (cloudCount > 0) onCloudPointsCaptured(cloudCount)
                                    onPointsCaptured(points)
                                }
                            }
                        }
                    }

                    view.onSessionFailed = { error ->
                        mainHandler.post { onDebugInfo("Session: ${error.message}") }
                    }
                }
            } catch (e: Throwable) {
                onDebugInfo("Init: ${e.message}")
                android.view.View(context)
            }
        },
        update = { gate.scanning = gate.scanning }
    )
}

private class CaptureState {
    var frames: Int = 0
    var lastCaptureMs: Long = 0L
}

private data class DepthSample(val status: String, val size: String, val count: Int)

private fun applyDepthConfig(session: Session, config: Config) {
    config.depthMode = when {
        session.isDepthModeSupported(Config.DepthMode.AUTOMATIC) -> Config.DepthMode.AUTOMATIC
        session.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY) -> Config.DepthMode.RAW_DEPTH_ONLY
        else -> Config.DepthMode.DISABLED
    }
    config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
    config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
    config.focusMode = Config.FocusMode.AUTO
}

private fun enableDepthCamera(session: Session): String {
    return try {
        val filter = CameraConfigFilter(session).apply {
            depthSensorUsage = EnumSet.of(CameraConfig.DepthSensorUsage.REQUIRE_AND_USE)
        }
        val configs = session.getSupportedCameraConfigs(filter)
        if (configs.isEmpty()) {
            "Sin cámara ToF; se usa la cámara por defecto"
        } else {
            val chosen = configs.minBy { it.imageSize.width * it.imageSize.height }
            session.cameraConfig = chosen
            val config = session.config
            applyDepthConfig(session, config)
            session.configure(config)
            "ToF ${chosen.imageSize.width}x${chosen.imageSize.height} ${config.depthMode}"
        }
    } catch (e: Throwable) {
        "Cámara depth: ${e.javaClass.simpleName} ${e.message}"
    }
}

private fun sampleDepth(frame: Frame, out: MutableList<Point3D>): DepthSample {
    if (frame.camera.trackingState != TrackingState.TRACKING) {
        return DepthSample("sin tracking", "--", 0)
    }
    val depthImage = try {
        frame.acquireDepthImage16Bits()
    } catch (_: NotYetAvailableException) {
        return DepthSample("Esperando depth...", "--", 0)
    } catch (e: Throwable) {
        return DepthSample("Err ${e.javaClass.simpleName}", "--", 0)
    }

    try {
        val width = depthImage.width
        val height = depthImage.height
        val plane = depthImage.planes[0]
        val buffer = plane.buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        val intrinsics = frame.camera.imageIntrinsics
        val imageWidth = intrinsics.imageDimensions[0].coerceAtLeast(1)
        val imageHeight = intrinsics.imageDimensions[1].coerceAtLeast(1)
        val scaleX = width.toFloat() / imageWidth
        val scaleY = height.toFloat() / imageHeight
        val fx = intrinsics.focalLength[0] * scaleX
        val fy = intrinsics.focalLength[1] * scaleY
        val cx = intrinsics.principalPoint[0] * scaleX
        val cy = intrinsics.principalPoint[1] * scaleY
        if (fx < 1f || fy < 1f) {
            return DepthSample("intrinsics invalidas", "${width}x${height}", 0)
        }

        val pose = frame.camera.pose
        val local = FloatArray(3)
        val world = FloatArray(3)
        var count = 0
        var minDepth = Float.MAX_VALUE
        val step = 3

        for (y in 0 until height step step) {
            val row = y * rowStride
            for (x in 0 until width step step) {
                val offset = row + x * pixelStride
                if (offset + 1 >= buffer.limit()) continue
                val depthMm = buffer.getShort(offset).toInt() and 0xFFFF
                if (depthMm == 0) continue
                val depthM = depthMm / 1000f
                if (depthM < 0.12f || depthM > 2.5f) continue

                local[0] = (x - cx) * depthM / fx
                local[1] = (cy - y) * depthM / fy
                local[2] = -depthM
                pose.transformPoint(local, 0, world, 0)
                out.add(Point3D(world[0], world[1], world[2], 1f))
                if (depthM < minDepth) minDepth = depthM
                count++
            }
        }

        val status = if (count > 0) {
            "OK $count pts  min ${"%.2f".format(minDepth)}m"
        } else {
            "imagen vacía ${width}x${height}"
        }
        return DepthSample(status, "${width}x${height}", count)
    } catch (e: Throwable) {
        return DepthSample("lectura ${e.javaClass.simpleName}", "--", 0)
    } finally {
        try { depthImage.close() } catch (_: Throwable) { }
    }
}

private fun sampleFeaturePoints(frame: Frame, out: MutableList<Point3D>): Int {
    return try {
        frame.acquirePointCloud().use { cloud ->
            val buffer = cloud.points
            buffer.rewind()
            var kept = 0
            while (buffer.remaining() >= 4 && kept < 250) {
                val x = buffer.get()
                val y = buffer.get()
                val z = buffer.get()
                val confidence = buffer.get()
                if (confidence > 0.4f) {
                    out.add(Point3D(x, y, z, confidence))
                    kept++
                }
            }
            kept
        }
    } catch (_: Throwable) {
        0
    }
}
