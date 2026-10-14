package com.scanr.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.ar.core.ArCoreApk
import com.scanr.app.ui.screens.ScannerScreen
import com.scanr.app.ui.theme.ScanRTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ScanR"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "MainActivity onCreate iniciado")
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Error no capturado: ${throwable.message}", throwable)
            runOnUiThread {
                setContent {
                    ScanRTheme {
                        ErrorScreen(
                            error = throwable.message ?: "Error desconocido",
                            stackTrace = throwable.stackTraceToString(),
                            onRetry = { recreate() }
                        )
                    }
                }
            }
        }
        
        setContent {
            ScanRTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ScanRApp()
                }
            }
        }
    }
}

@Composable
fun ErrorScreen(
    error: String,
    stackTrace: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
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
            text = "La aplicación encontró un error",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = error,
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFFFF6B6B),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = stackTrace.take(2000),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0D0D1A))
                .padding(12.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(onClick = onRetry) {
            Text("Reintentar")
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScanRApp() {
    val context = LocalContext.current
    var arCoreStatus by remember { mutableStateOf<String?>(null) }
    var showScanner by remember { mutableStateOf(false) }
    
    Log.d("ScanR", "ScanRApp: showScanner=$showScanner")
    
    LaunchedEffect(Unit) {
        val availability = ArCoreApk.getInstance().checkAvailability(context)
        arCoreStatus = when (availability) {
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> "OK"
            ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> "ARCore no instalado"
            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD -> "ARCore necesita actualización"
            ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> "Dispositivo no compatible"
            else -> "Verificando..."
        }
        Log.d("ScanR", "ARCore: $arCoreStatus")
    }
    
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    when {
        arCoreStatus != null && arCoreStatus != "OK" && arCoreStatus != "Verificando..." -> {
            ARCoreErrorScreen(status = arCoreStatus!!)
        }
        
        cameraPermissionState.status.isGranted -> {
            if (showScanner) {
                Log.d("ScanR", "Renderizando ScannerScreen...")
                ScannerScreen(
                    onBackPressed = { 
                        showScanner = false 
                    }
                )
            } else {
                WelcomeScreen(
                    arCoreStatus = arCoreStatus,
                    onStartScanner = { 
                        Log.d("ScanR", "Boton presionado!")
                        Toast.makeText(context, "Iniciando...", Toast.LENGTH_SHORT).show()
                        showScanner = true
                    }
                )
            }
        }
        
        cameraPermissionState.status.shouldShowRationale -> {
            PermissionRationaleScreen(
                onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
            )
        }
        
        else -> {
            PermissionRequestScreen(
                onRequestPermission = { cameraPermissionState.launchPermissionRequest() },
                onOpenSettings = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            )
        }
    }
}

@Composable
fun ARCoreErrorScreen(status: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Problema con ARCore", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = status, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun WelcomeScreen(arCoreStatus: String?, onStartScanner: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "ScanR", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Escaneo 3D con ARCore", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (arCoreStatus != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "ARCore: $arCoreStatus",
                style = MaterialTheme.typography.bodySmall,
                color = if (arCoreStatus == "OK") Color.Green else Color.Yellow
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onStartScanner) {
            Text("Iniciar Escaneo")
        }
    }
}

@Composable
fun PermissionRationaleScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = Icons.Default.Camera, contentDescription = null, modifier = Modifier.size(80.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Permiso de Cámara Requerido", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "ScanR necesita acceso a la cámara.", textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermission) { Text("Conceder Permiso") }
    }
}

@Composable
fun PermissionRequestScreen(onRequestPermission: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Permiso de Cámara Necesario", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Para usar ScanR, necesitas permitir el acceso a la cámara.", textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermission) { Text("Solicitar Permiso") }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onOpenSettings) { Text("Abrir Configuración") }
    }
}
