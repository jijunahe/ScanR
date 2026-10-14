package com.scanr.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.ar.core.TrackingState
import com.scanr.app.ar.DepthMode

@Composable
fun ARStatusOverlay(
    modifier: Modifier = Modifier,
    trackingState: TrackingState,
    isDepthSupported: Boolean,
    isRawDepthSupported: Boolean,
    depthMode: DepthMode,
    showDepthInfo: Boolean = true
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            TrackingStatusCard(trackingState = trackingState)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            AnimatedVisibility(
                visible = showDepthInfo,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                DepthSupportCard(
                    isDepthSupported = isDepthSupported,
                    isRawDepthSupported = isRawDepthSupported,
                    depthMode = depthMode
                )
            }
        }
        
        if (trackingState != TrackingState.TRACKING) {
            TrackingInstructionOverlay(trackingState = trackingState)
        }
    }
}

@Composable
fun TrackingStatusCard(trackingState: TrackingState) {
    val (icon, color, text) = when (trackingState) {
        TrackingState.TRACKING -> Triple(
            Icons.Default.Check,
            Color(0xFF4CAF50),
            "Rastreando"
        )
        TrackingState.PAUSED -> Triple(
            Icons.Default.Warning,
            Color(0xFFFF9800),
            "Pausado"
        )
        TrackingState.STOPPED -> Triple(
            Icons.Default.Close,
            Color(0xFFF44336),
            "Detenido"
        )
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = "Estado AR",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun DepthSupportCard(
    isDepthSupported: Boolean,
    isRawDepthSupported: Boolean,
    depthMode: DepthMode
) {
    val (icon, color, title, description) = when {
        isRawDepthSupported -> Quadruple(
            Icons.Default.Sensors,
            Color(0xFF4CAF50),
            "Sensor de profundidad detectado",
            "Hardware ToF/LiDAR disponible"
        )
        isDepthSupported -> Quadruple(
            Icons.Default.Info,
            Color(0xFF2196F3),
            "Profundidad por software",
            "Estimación de profundidad activa"
        )
        else -> Quadruple(
            Icons.Default.Warning,
            Color(0xFFFF9800),
            "Sin soporte de profundidad",
            "El escaneo 3D será limitado"
        )
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun TrackingInstructionOverlay(trackingState: TrackingState) {
    val instruction = when (trackingState) {
        TrackingState.PAUSED -> "Mueve el dispositivo lentamente para reanudar el rastreo"
        TrackingState.STOPPED -> "Apunta la cámara a una superficie bien iluminada"
        TrackingState.TRACKING -> ""
    }
    
    if (instruction.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 100.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    text = instruction,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun DepthNotSupportedMessage(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Profundidad no soportada",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Este dispositivo no tiene sensor de profundidad por hardware " +
                        "y no soporta estimación por software. " +
                        "Las funciones de escaneo 3D estarán limitadas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
