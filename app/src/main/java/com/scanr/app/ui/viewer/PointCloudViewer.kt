package com.scanr.app.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.scanr.app.data.PointCloud

@Composable
fun PointCloudViewer(
    pointCloud: PointCloud,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF1A1A2E),
    pointColor: Color = Color(0xFF4FC3F7),
    pointSize: Float = 5f,
    onViewCreated: (PointCloudGLSurfaceView) -> Unit = {}
) {
    val context = LocalContext.current
    var glSurfaceView by remember { mutableStateOf<PointCloudGLSurfaceView?>(null) }
    
    DisposableEffect(pointCloud) {
        glSurfaceView?.setPointCloud(pointCloud)
        onDispose { }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PointCloudGLSurfaceView(ctx).apply {
                    glSurfaceView = this
                    setBackgroundColor(
                        backgroundColor.red,
                        backgroundColor.green,
                        backgroundColor.blue
                    )
                    setPointColor(
                        pointColor.red,
                        pointColor.green,
                        pointColor.blue
                    )
                    setPointSize(pointSize)
                    setPointCloud(pointCloud)
                    onViewCreated(this)
                }
            },
            update = { view ->
                view.setPointCloud(pointCloud)
                view.setPointSize(pointSize)
                view.setPointColor(
                    pointColor.red,
                    pointColor.green,
                    pointColor.blue
                )
            }
        )
    }
}

@Composable
fun InteractivePointCloudViewer(
    pointCloud: PointCloud,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF1A1A2E),
    pointColor: Color = Color(0xFF4FC3F7),
    initialPointSize: Float = 5f
) {
    var pointSize by remember { mutableFloatStateOf(initialPointSize) }
    var glView by remember { mutableStateOf<PointCloudGLSurfaceView?>(null) }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    pointSize = (pointSize * zoom).coerceIn(1f, 20f)
                    glView?.setPointSize(pointSize)
                }
            }
    ) {
        PointCloudViewer(
            pointCloud = pointCloud,
            modifier = Modifier.fillMaxSize(),
            backgroundColor = backgroundColor,
            pointColor = pointColor,
            pointSize = pointSize,
            onViewCreated = { glView = it }
        )
    }
}
