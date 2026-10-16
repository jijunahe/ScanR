package com.scanr.app.ui.screens

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
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
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.ar.core.Anchor
import com.google.ar.core.CameraConfig
import com.google.ar.core.Config
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import io.github.sceneview.ar.ARSceneView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

private class Shot(
    val file: File,
    val fx: Float,
    val fy: Float,
    val cx: Float,
    val cy: Float,
    val width: Int,
    val height: Int,
    val tx: Float,
    val ty: Float,
    val tz: Float,
    val qx: Float,
    val qy: Float,
    val qz: Float,
    val qw: Float
)

private class GuideRuntime {
    @Volatile var placeRequested: Boolean = false
    @Volatile var manualRequested: Boolean = false
    @Volatile var clearRequested: Boolean = false
    @Volatile var paused: Boolean = false
    @Volatile var generation: Int = 0
    var anchor: Anchor? = null
    var busy: Boolean = false
    var lastCaptureMs: Long = 0L
    var lastPosition: FloatArray? = null
    var lastPositionMs: Long = 0L
    var lastUiMs: Long = 0L
    val guide = OrbitGuide()
    val shots = Collections.synchronizedList(mutableListOf<Shot>())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuidedCaptureScreen(
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val runtime = remember { GuideRuntime() }
    val prefs = remember { context.getSharedPreferences("scanr", android.content.Context.MODE_PRIVATE) }
    var serverUrl by remember { mutableStateOf(prefs.getString("server", "http://192.168.1.20:8000") ?: "http://192.168.1.20:8000") }
    var message by remember { mutableStateOf("Centra el objeto en el círculo y pulsa «Este es el objeto».") }
    var detail by remember { mutableStateOf("Mueve el teléfono un momento para que reconozca la mesa.") }
    var photoCount by remember { mutableStateOf(0) }
    var lowBins by remember { mutableStateOf(List(12) { false }) }
    var highBins by remember { mutableStateOf(List(12) { false }) }
    var reticleReady by remember { mutableStateOf(false) }
    var hasTarget by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    val client = remember {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.MINUTES)
            .readTimeout(2, TimeUnit.MINUTES)
            .build()
    }
    val photoDir = remember {
        File(context.cacheDir, "guided").apply { mkdirs() }
    }

    fun persistUrl(value: String) {
        serverUrl = value
        prefs.edit().putString("server", value).apply()
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            GuidedCamera(
                modifier = Modifier.fillMaxSize(),
                runtime = runtime,
                photoDir = photoDir,
                onMessage = { message = it },
                onDetail = { detail = it },
                onCoverage = { low, high, count, target ->
                    lowBins = low
                    highBins = high
                    photoCount = count
                    hasTarget = target
                },
                onReticle = { reticleReady = it }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(190.dp)
                    .border(3.dp, if (reticleReady) Color(0xFF69F0AE) else Color.White, CircleShape)
            )
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xAA000000))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    if (hasTarget) "Objeto marcado" else "Apunta al objeto",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF101018))
                .padding(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackPressed) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
                }
                Text("ScanR Fotos", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("$photoCount / 24", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
            }
            Text(message, color = Color.White, fontSize = 15.sp)
            Text(detail, color = Color(0xFFB0BEC5), fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Text("Vuelta media", color = Color.Gray, fontSize = 11.sp)
            BinRow(lowBins)
            Text("Vuelta alta", color = Color.Gray, fontSize = 11.sp)
            BinRow(highBins)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { persistUrl(it) },
                    singleLine = true,
                    label = { Text("Computador") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White,
                        focusedBorderColor = Color(0xFF69F0AE),
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color(0xFF69F0AE),
                        unfocusedLabelColor = Color.Gray
                    )
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    enabled = !sending,
                    onClick = {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { probeServer(client, serverUrl) }
                            Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                        }
                    }
                ) { Text("Probar") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !sending,
                    onClick = { runtime.placeRequested = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                ) { Text("Este es el objeto") }
                Button(
                    enabled = !sending && hasTarget,
                    onClick = { runtime.manualRequested = true }
                ) { Text("Foto") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !sending && photoCount > 0,
                    onClick = {
                        runtime.clearRequested = true
                        runtime.generation += 1
                        synchronized(runtime.shots) {
                            runtime.shots.forEach { it.file.delete() }
                            runtime.shots.clear()
                        }
                        photoCount = 0
                        lowBins = List(12) { false }
                        highBins = List(12) { false }
                        hasTarget = false
                        message = "Centra el objeto y vuelve a marcarlo."
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6D4C41))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                }
                Button(
                    enabled = !sending && photoCount >= 8,
                    onClick = {
                        val shots = synchronized(runtime.shots) { runtime.shots.toList() }
                        sending = true
                        runtime.paused = true
                        scope.launch {
                            try {
                                val saved = sendAndDownload(client, serverUrl, shots, photoDir) { status ->
                                    message = status
                                }
                                message = "Modelo listo. Compártelo a Blender."
                                shareZip(context, saved)
                            } catch (error: Exception) {
                                message = error.message ?: "No se pudo enviar."
                            } finally {
                                sending = false
                                runtime.paused = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    if (sending) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (photoCount < 8) "Mínimo 8 fotos" else "Enviar al computador")
                    }
                }
            }
        }
    }
}

@Composable
private fun BinRow(bins: List<Boolean>) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 2.dp)) {
        bins.forEach { filled ->
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (filled) Color(0xFF69F0AE) else Color(0xFF37474F))
            )
        }
    }
}

@Composable
private fun GuidedCamera(
    modifier: Modifier,
    runtime: GuideRuntime,
    photoDir: File,
    onMessage: (String) -> Unit,
    onDetail: (String) -> Unit,
    onCoverage: (List<Boolean>, List<Boolean>, Int, Boolean) -> Unit,
    onReticle: (Boolean) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var sceneView by remember { mutableStateOf<ARSceneView?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                try {
                    sceneView?.destroy()
                } catch (_: Exception) {
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                runtime.anchor?.detach()
            } catch (_: Exception) {
            }
            try {
                sceneView?.destroy()
            } catch (_: Exception) {
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            ARSceneView(context).also { view ->
                sceneView = view
                view.planeRenderer.isEnabled = false
                view.sessionConfiguration = { session, config ->
                    if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                        config.depthMode = Config.DepthMode.AUTOMATIC
                    }
                    config.focusMode = Config.FocusMode.AUTO
                    config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                }
                view.onSessionCreated = { session ->
                    selectLargestCamera(session)
                    try {
                        val config = session.config
                        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                            config.depthMode = Config.DepthMode.AUTOMATIC
                        }
                        config.focusMode = Config.FocusMode.AUTO
                        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                        session.configure(config)
                    } catch (_: Throwable) {
                    }
                }
                view.onSessionUpdated = { _, frame ->
                    if (runtime.clearRequested) {
                        runtime.clearRequested = false
                        try {
                            runtime.anchor?.detach()
                        } catch (_: Exception) {
                        }
                        runtime.anchor = null
                        runtime.guide.reset()
                        runtime.lastPosition = null
                    }
                    val tracking = frame.camera.trackingState == TrackingState.TRACKING
                    if (runtime.placeRequested && tracking) {
                        runtime.placeRequested = false
                        val placed = placeTarget(view, frame, runtime)
                        mainHandler.post {
                            if (placed) {
                                onMessage("Objeto marcado. Camina alrededor, despacio.")
                                onDetail("La foto se toma sola cuando el ángulo es nuevo.")
                            } else {
                                onMessage("Todavía no distingo el objeto.")
                                onDetail("Acércate, mueve el teléfono un poco y vuelve a pulsar.")
                            }
                        }
                    }
                    val anchor = runtime.anchor
                    val pose = frame.camera.pose
                    val position = floatArrayOf(pose.tx(), pose.ty(), pose.tz())
                    val now = System.currentTimeMillis()
                    val speed = cameraSpeed(runtime, position, now)
                    var decision: OrbitGuide.Decision? = null
                    if (tracking && anchor != null) {
                        val target = anchor.pose
                        val forward = FloatArray(3)
                        pose.rotateVector(floatArrayOf(0f, 0f, -1f), 0, forward, 0)
                        decision = runtime.guide.evaluate(
                            position[0], position[1], position[2],
                            target.tx(), target.ty(), target.tz(),
                            forward[0], forward[1], forward[2],
                            speed
                        )
                    }
                    val wantAuto = decision?.capture == true && !runtime.paused && tracking
                    val wantManual = runtime.manualRequested && tracking
                    if (wantManual) runtime.manualRequested = false
                    val cooled = now - runtime.lastCaptureMs > 900
                    if ((wantAuto || wantManual) && cooled && !runtime.busy && runtime.shots.size < 40) {
                        runtime.busy = true
                        val generation = runtime.generation
                        val saved = captureShot(frame, photoDir, runtime.shots.size + 1)
                        runtime.busy = false
                        if (saved != null && generation == runtime.generation) {
                            runtime.lastCaptureMs = now
                            if (wantAuto && decision != null) runtime.guide.mark(decision.ring, decision.bin)
                            runtime.shots.add(saved)
                        }
                    }
                    if (!runtime.paused && now - runtime.lastUiMs > 200) {
                        runtime.lastUiMs = now
                        val low = runtime.guide.low.toList()
                        val high = runtime.guide.high.toList()
                        val count = runtime.shots.size
                        val targetSet = runtime.anchor != null
                        val text = when {
                            !tracking -> "Mueve el teléfono despacio hasta que aparezca el entorno."
                            decision != null -> decision.message
                            else -> "Centra el objeto y pulsa «Este es el objeto»."
                        }
                        val ready = decision?.reticleReady == true
                        mainHandler.post {
                            onMessage(text)
                            onReticle(ready)
                            onCoverage(low, high, count, targetSet)
                            if (count > 0) onDetail("$count fotos guardadas. 24 cubren bien un objeto.")
                        }
                    }
                }
            }
        }
    )
}

private fun placeTarget(view: ARSceneView, frame: Frame, runtime: GuideRuntime): Boolean {
    if (view.width <= 0 || view.height <= 0) return false
    val hits = frame.hitTest(view.width / 2f, view.height / 2f)
    val hit = hits.firstOrNull { it.trackable is DepthPoint }
        ?: hits.firstOrNull { it.trackable is Point }
        ?: hits.firstOrNull { it.trackable is Plane }
        ?: return false
    try {
        runtime.anchor?.detach()
    } catch (_: Exception) {
    }
    runtime.anchor = try {
        hit.createAnchor()
    } catch (_: Exception) {
        return false
    }
    runtime.guide.reset()
    return true
}

private fun cameraSpeed(runtime: GuideRuntime, position: FloatArray, now: Long): Float {
    val previous = runtime.lastPosition
    val previousMs = runtime.lastPositionMs
    runtime.lastPosition = position.copyOf()
    runtime.lastPositionMs = now
    if (previous == null || previousMs == 0L) return 0f
    val dt = (now - previousMs) / 1000f
    if (dt <= 0.001f) return 0f
    val dx = position[0] - previous[0]
    val dy = position[1] - previous[1]
    val dz = position[2] - previous[2]
    return sqrt(dx * dx + dy * dy + dz * dz) / dt
}

private fun captureShot(frame: Frame, directory: File, index: Int): Shot? {
    val image = try {
        frame.acquireCameraImage()
    } catch (_: NotYetAvailableException) {
        null
    } catch (_: Throwable) {
        null
    } ?: return null
    return try {
        if (image.format != ImageFormat.YUV_420_888) return null
        val jpeg = yuvToJpeg(image, 92)
        val file = File(directory, "img_%03d.jpg".format(index))
        FileOutputStream(file).use { it.write(jpeg) }
        val intrinsics = frame.camera.imageIntrinsics
        var fx = intrinsics.focalLength[0]
        var fy = intrinsics.focalLength[1]
        var cx = intrinsics.principalPoint[0]
        var cy = intrinsics.principalPoint[1]
        val sourceWidth = intrinsics.imageDimensions[0]
        val sourceHeight = intrinsics.imageDimensions[1]
        if (sourceWidth > 0 && sourceHeight > 0 && (sourceWidth != image.width || sourceHeight != image.height)) {
            val scaleX = image.width.toFloat() / sourceWidth
            val scaleY = image.height.toFloat() / sourceHeight
            fx *= scaleX
            fy *= scaleY
            cx *= scaleX
            cy *= scaleY
        }
        val pose = frame.camera.pose
        val rotation = pose.rotationQuaternion
        Shot(
            file = file,
            fx = fx,
            fy = fy,
            cx = cx,
            cy = cy,
            width = image.width,
            height = image.height,
            tx = pose.tx(),
            ty = pose.ty(),
            tz = pose.tz(),
            qx = rotation[0],
            qy = rotation[1],
            qz = rotation[2],
            qw = rotation[3]
        )
    } catch (_: Throwable) {
        null
    } finally {
        try {
            image.close()
        } catch (_: Throwable) {
        }
    }
}

private fun selectLargestCamera(session: Session) {
    try {
        val configs = session.supportedCameraConfigs.filter {
            it.facingDirection == CameraConfig.FacingDirection.BACK
        }
        val chosen = configs.maxByOrNull { it.imageSize.width.toLong() * it.imageSize.height } ?: return
        session.cameraConfig = chosen
    } catch (_: Throwable) {
    }
}

private fun yuvToJpeg(image: Image, quality: Int): ByteArray {
    val width = image.width
    val height = image.height
    val nv21 = ByteArray(width * height * 3 / 2)
    val yPlane = image.planes[0]
    val uPlane = image.planes[1]
    val vPlane = image.planes[2]
    copyPlane(yPlane.buffer, yPlane.rowStride, yPlane.pixelStride, width, height, nv21, 0)
    var offset = width * height
    val chromaHeight = height / 2
    val chromaWidth = width / 2
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer
    for (row in 0 until chromaHeight) {
        for (col in 0 until chromaWidth) {
            val vIndex = row * vPlane.rowStride + col * vPlane.pixelStride
            val uIndex = row * uPlane.rowStride + col * uPlane.pixelStride
            nv21[offset++] = vBuffer.get(vIndex)
            nv21[offset++] = uBuffer.get(uIndex)
        }
    }
    val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val stream = ByteArrayOutputStream()
    yuv.compressToJpeg(Rect(0, 0, width, height), quality, stream)
    return stream.toByteArray()
}

private fun copyPlane(
    buffer: ByteBuffer,
    rowStride: Int,
    pixelStride: Int,
    width: Int,
    height: Int,
    out: ByteArray,
    outOffset: Int
) {
    val source = buffer.duplicate()
    source.rewind()
    var output = outOffset
    for (row in 0 until height) {
        var input = row * rowStride
        if (pixelStride == 1 && rowStride == width) {
            source.position(input)
            source.get(out, output, width)
            output += width
        } else {
            for (col in 0 until width) {
                out[output++] = source.get(input)
                input += pixelStride
            }
        }
    }
}

private fun normalizeServer(url: String): String {
    var value = url.trim().trimEnd('/')
    if (!value.startsWith("http://") && !value.startsWith("https://")) {
        value = "http://$value"
    }
    return value
}

private fun probeServer(client: OkHttpClient, serverUrl: String): String {
    return try {
        val response = client.newCall(
            Request.Builder().url("${normalizeServer(serverUrl)}/api/health").get().build()
        ).execute()
        val body = response.body?.string().orEmpty()
        response.close()
        if (!response.isSuccessful) {
            "El computador respondió ${response.code}."
        } else if (body.contains("\"colmap\": true") || body.contains("\"colmap\":true")) {
            "Conectado. COLMAP está listo."
        } else {
            "Conectado, pero falta instalar COLMAP en el computador."
        }
    } catch (error: Exception) {
        "No llegué al computador. Misma WiFi, IP correcta y run.bat abierto. ${error.javaClass.simpleName}"
    }
}

private suspend fun sendAndDownload(
    client: OkHttpClient,
    serverUrl: String,
    shots: List<Shot>,
    directory: File,
    onStatus: (String) -> Unit
): File {
    val base = normalizeServer(serverUrl)
    onStatus("Enviando ${shots.size} fotos…")
    val jobId = withContext(Dispatchers.IO) {
        val meta = JSONObject()
        val images = JSONArray()
        shots.forEach { shot ->
            images.put(
                JSONObject()
                    .put("file", shot.file.name)
                    .put("fx", shot.fx.toDouble())
                    .put("fy", shot.fy.toDouble())
                    .put("cx", shot.cx.toDouble())
                    .put("cy", shot.cy.toDouble())
                    .put("width", shot.width)
                    .put("height", shot.height)
                    .put("tx", shot.tx.toDouble())
                    .put("ty", shot.ty.toDouble())
                    .put("tz", shot.tz.toDouble())
                    .put("qx", shot.qx.toDouble())
                    .put("qy", shot.qy.toDouble())
                    .put("qz", shot.qz.toDouble())
                    .put("qw", shot.qw.toDouble())
            )
        }
        meta.put("images", images)
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("meta", meta.toString())
            .apply {
                shots.forEach { shot ->
                    addFormDataPart("images", shot.file.name, shot.file.asRequestBody("image/jpeg".toMediaType()))
                }
            }
            .build()
        val response = client.newCall(
            Request.Builder().url("$base/api/jobs").post(body).build()
        ).execute()
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            response.close()
            throw IllegalStateException(text.ifBlank { "El computador rechazó las fotos (${response.code})." })
        }
        response.close()
        JSONObject(text).getString("id")
    }
    while (true) {
        delay(2000)
        val status = withContext(Dispatchers.IO) {
            val response = client.newCall(
                Request.Builder().url("$base/api/jobs/$jobId").get().build()
            ).execute()
            val text = response.body?.string().orEmpty()
            response.close()
            if (!response.isSuccessful) {
                throw IllegalStateException("Perdí el contacto con el computador.")
            }
            JSONObject(text)
        }
        val state = status.optString("status")
        val note = status.optString("message")
        if (note.isNotBlank()) onStatus(note)
        if (state == "error") throw IllegalStateException(note.ifBlank { "El computador no pudo armar el modelo." })
        if (state == "done") break
    }
    onStatus("Descargando el modelo…")
    return withContext(Dispatchers.IO) {
        val response = client.newCall(
            Request.Builder().url("$base/api/jobs/$jobId/model").get().build()
        ).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IllegalStateException("No pude descargar el modelo.")
        }
        val file = File(directory, "modelo-$jobId.zip")
        response.body?.byteStream().use { input ->
            FileOutputStream(file).use { output -> input?.copyTo(output) }
        }
        response.close()
        file
    }
}

private fun shareZip(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(android.content.Intent.createChooser(share, "Abrir en Blender o compartir"))
}

private class OrbitGuide {
    val low = BooleanArray(12)
    val high = BooleanArray(12)

    fun reset() {
        low.fill(false)
        high.fill(false)
    }

    data class Decision(
        val message: String,
        val capture: Boolean,
        val ring: Int,
        val bin: Int,
        val reticleReady: Boolean
    )

    fun evaluate(
        cameraX: Float,
        cameraY: Float,
        cameraZ: Float,
        targetX: Float,
        targetY: Float,
        targetZ: Float,
        forwardX: Float,
        forwardY: Float,
        forwardZ: Float,
        speed: Float
    ): Decision {
        val vx = cameraX - targetX
        val vy = cameraY - targetY
        val vz = cameraZ - targetZ
        val distance = sqrt(vx * vx + vy * vy + vz * vz)
        if (distance < 0.22f) return Decision("Aléjate un poco del objeto", false, -1, -1, false)
        if (distance > 1.05f) return Decision("Acércate un poco", false, -1, -1, false)
        val towardX = targetX - cameraX
        val towardY = targetY - cameraY
        val towardZ = targetZ - cameraZ
        val towardLength = sqrt(towardX * towardX + towardY * towardY + towardZ * towardZ).coerceAtLeast(1e-4f)
        val forwardLength = sqrt(forwardX * forwardX + forwardY * forwardY + forwardZ * forwardZ).coerceAtLeast(1e-4f)
        val facing = (towardX * forwardX + towardY * forwardY + towardZ * forwardZ) / (towardLength * forwardLength)
        if (facing < 0.94f) return Decision("Centra el objeto en el círculo", false, -1, -1, false)
        val horizontal = sqrt(vx * vx + vz * vz).coerceAtLeast(1e-4f)
        val elevation = Math.toDegrees(atan2(vy.toDouble(), horizontal.toDouble())).toFloat()
        val ring = when {
            elevation < -8f -> return Decision("Sube un poco el teléfono", false, -1, -1, true)
            elevation <= 30f -> 0
            elevation <= 70f -> 1
            else -> return Decision("Baja un poco el teléfono", false, -1, -1, true)
        }
        val bin = (((atan2(vx.toDouble(), vz.toDouble()) + PI) / (2 * PI)) * 12).toInt().mod(12)
        val ringBins = if (ring == 0) low else high
        if (speed > 0.2f) return Decision("Muévete más lento", false, ring, bin, true)
        if (ringBins[bin]) {
            val message = when {
                low.all { it } && high.all { it } -> "Cobertura lista. Envía las fotos al computador."
                low.all { it } && ring == 0 -> "Vuelta de abajo lista. Ahora desde más arriba."
                else -> "Sigue girando: este ángulo ya está."
            }
            return Decision(message, false, ring, bin, true)
        }
        return Decision("Quieto… tomando foto", true, ring, bin, true)
    }

    fun mark(ring: Int, bin: Int) {
        if (ring == 0 && bin in low.indices) low[bin] = true
        if (ring == 1 && bin in high.indices) high[bin] = true
    }
}
