package com.scanr.app.ui.components

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.ar.core.Config
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView

@Composable
fun ARCameraView(
    modifier: Modifier = Modifier,
    onSessionCreated: (ARSceneView) -> Unit = {},
    onSessionResumed: () -> Unit = {},
    onSessionPaused: () -> Unit = {},
    onTrackingStateChanged: (TrackingState) -> Unit = {},
    onDepthSupportChecked: (Boolean, Boolean) -> Unit = { _, _ -> },
    onError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var arSceneView by remember { mutableStateOf<ARSceneView?>(null) }
    var hasError by remember { mutableStateOf(false) }
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    onSessionResumed()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    onSessionPaused()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    try {
                        arSceneView?.destroy()
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
                else -> {}
            }
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                arSceneView?.destroy()
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
                text = "Error al iniciar AR.\nVerifica que ARCore esté instalado.",
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
        return
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                try {
                    ARSceneView(ctx).apply {
                        arSceneView = this
                        
                        sessionConfiguration = { session, config ->
                            val isDepthSupported = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
                            val isRawDepthSupported = session.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY)
                            
                            onDepthSupportChecked(isDepthSupported, isRawDepthSupported)
                            
                            config.apply {
                                depthMode = if (isDepthSupported) {
                                    Config.DepthMode.AUTOMATIC
                                } else {
                                    Config.DepthMode.DISABLED
                                }
                                
                                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                                lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                                focusMode = Config.FocusMode.AUTO
                            }
                        }
                        
                        onSessionUpdated = { _, frame ->
                            try {
                                onTrackingStateChanged(frame.camera.trackingState)
                            } catch (e: Exception) {
                                // Ignore
                            }
                        }
                        
                        onSessionCreated(this)
                    }
                } catch (e: Exception) {
                    hasError = true
                    onError("Error: \${e.message}")
                    View(ctx)
                }
            }
        )
    }
}

@Composable
fun ARCameraViewSimple(
    modifier: Modifier = Modifier,
    onARSceneViewCreated: (ARSceneView) -> Unit = {},
    onFrameUpdate: (com.google.ar.core.Frame) -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var sceneView by remember { mutableStateOf<ARSceneView?>(null) }
    
    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                sceneView?.destroy()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            try {
                ARSceneView(context).apply {
                    sceneView = this
                    
                    sessionConfiguration = { session, config ->
                        config.depthMode = if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                            Config.DepthMode.AUTOMATIC
                        } else {
                            Config.DepthMode.DISABLED
                        }
                        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                        config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                        config.focusMode = Config.FocusMode.AUTO
                    }
                    
                    onARSceneViewCreated(this)
                }
            } catch (e: Exception) {
                View(context)
            }
        }
    )
}
