package com.scanr.app.ui.screens

import android.view.View
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Versión de prueba simplificada
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit = {}
) {
    Log.d("ScanR", "ScannerScreen: Iniciando composición")
    
    var testStep by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Log de entrada
    LaunchedEffect(Unit) {
        Log.d("ScanR", "ScannerScreen: LaunchedEffect ejecutado")
        testStep = 1
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ScanR - Test") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E)
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF1A1A2E)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    tint = Color.Green
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "¡ScannerScreen funciona!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Si ves esta pantalla, el problema\nestaba en el código del escáner AR.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Step: $testStep",
                    color = Color.Yellow
                )
                
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Error: $errorMessage",
                        color = Color.Red
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(onClick = onBackPressed) {
                    Text("Volver al inicio")
                }
            }
        }
    }
}
