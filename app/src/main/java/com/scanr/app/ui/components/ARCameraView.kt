package com.scanr.app.ui.components

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.ar.core.Config
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.configure
import io.github.sceneview.ar.arcore.isTrackingPlane

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
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    arSceneView?.onResume(lifecycleOwner)
                    onSessionResumed()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    arSceneView?.onPause(lifecycleOwner)
                    onSessionPaused()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    arSceneView?.onDestroy(lifecycleOwner)
                }
                else -> {}
            }
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            arSceneView?.onDestroy(lifecycleOwner)
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                ARSceneView(ctx).apply {
                    arSceneView = this
                    
                    configureSession { session, config ->
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
                            
                            if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                                depthMode = Config.DepthMode.AUTOMATIC
                            }
                        }
                    }
                    
                    onSessionCreated(this)
                }
            },
            update = { view ->
                view.session?.let { session ->
                    try {
                        val frame = session.update()
                        onTrackingStateChanged(frame.camera.trackingState)
                    } catch (e: Exception) {
                        // Frame update might fail occasionally
                    }
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
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> sceneView?.onResume(lifecycleOwner)
                Lifecycle.Event.ON_PAUSE -> sceneView?.onPause(lifecycleOwner)
                Lifecycle.Event.ON_DESTROY -> sceneView?.onDestroy(lifecycleOwner)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            ARSceneView(context).apply {
                sceneView = this
                
                configureSession { session, config ->
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
        }
    )
}
