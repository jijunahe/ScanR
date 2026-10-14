package com.scanr.app.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.ar.core.Config
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit = {}
) {
    val context = LocalContext.current
    var statusMessage by remember { mutableStateOf("Iniciando cámara AR...") }
    var isARReady by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var errorDetails by remember { mutableStateOf("") }
    var trackingStatus by remember { mutableStateOf("Esperando...") }
    var pointCount by remember { mutableStateOf(0) }
    var isScanning by remember { mutableStateOf(false) }
    
    Log.d("ScanR", "ScannerScreen AR: Iniciando")
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ScanR") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
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
                ARCameraSimple(
                    modifier = Modifier.fillMaxSize(),
                    onReady = { 
                        isARReady = true
                        statusMessage = "AR listo"
                        Log.d("ScanR", "AR está listo")
                    },
                    onError = { error ->
                        hasError = true
                        errorDetails = error
                        Log.e("ScanR", "Error AR: $error")
                    },
                    onTrackingChanged = { state ->
                        trackingStatus = when (state) {
                            TrackingState.TRACKING -> "Rastreando ✓"
                            TrackingState.PAUSED -> "Pausado"
                            TrackingState.STOPPED -> "Detenido"
                        }
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
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = statusMessage, color = Color.White)
                            Text(text = trackingStatus, color = Color.Green)
                        }
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // Botón de escaneo
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        FloatingActionButton(
                            onClick = {
                                isScanning = !isScanning
                                Toast.makeText(
                                    context,
                                    if (isScanning) "Escaneando..." else "Escaneo pausado",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.size(72.dp),
                            containerColor = if (isScanning) Color.Red else Color.Green
                        ) {
                            Icon(
                                imageVector = if (isScanning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = if (isScanning) "Detener" else "Escanear",
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun ARCameraSimple(
    modifier: Modifier = Modifier,
    onReady: () -> Unit,
    onError: (String) -> Unit,
    onTrackingChanged: (TrackingState) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var sceneView by remember { mutableStateOf<ARSceneView?>(null) }
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                try {
                    sceneView?.destroy()
                } catch (e: Exception) {
                    Log.e("ScanR", "Error al destruir ARSceneView", e)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                sceneView?.destroy()
            } catch (e: Exception) {
                Log.e("ScanR", "Error en onDispose", e)
            }
        }
    }
    
    AndroidView(
        modifier = modifier,
        factory = { context ->
            Log.d("ScanR", "Creando ARSceneView...")
            try {
                ARSceneView(context).also { view ->
                    sceneView = view
                    Log.d("ScanR", "ARSceneView creado exitosamente")
                    
                    view.sessionConfiguration = { session, config ->
                        Log.d("ScanR", "Configurando sesión AR...")
                        config.depthMode = if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                            Config.DepthMode.AUTOMATIC
                        } else {
                            Config.DepthMode.DISABLED
                        }
                        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                        config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                        config.focusMode = Config.FocusMode.AUTO
                        Log.d("ScanR", "Sesión AR configurada")
                    }
                    
                    view.onSessionUpdated = { session, frame ->
                        onTrackingChanged(frame.camera.trackingState)
                    }
                    
                    view.onSessionResumed = { session ->
                        Log.d("ScanR", "Sesión AR resumida")
                        onReady()
                    }
                    
                    view.onSessionFailed = { exception ->
                        Log.e("ScanR", "Sesión AR falló", exception)
                        onError(exception.message ?: "Error desconocido en sesión AR")
                    }
                }
            } catch (e: Exception) {
                Log.e("ScanR", "Error creando ARSceneView", e)
                onError("Error creando vista AR: ${e.message}")
                android.view.View(context)
            }
        }
    )
}
