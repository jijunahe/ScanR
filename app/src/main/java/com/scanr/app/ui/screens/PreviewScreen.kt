package com.scanr.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    modifier: Modifier = Modifier,
    pointCount: Int = 0,
    onExport: () -> Unit = {},
    onNewScan: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vista Previa") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF0A0A15))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(100.dp),
                tint = Color.Green
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Escaneo Completado",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "$pointCount puntos capturados",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Cyan
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = onExport,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text("Exportar")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedButton(
                onClick = onNewScan,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text("Nuevo Escaneo")
            }
        }
    }
}

@Composable
fun EmptyPreviewScreen(
    onNewScan: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A15))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color.Yellow
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "No hay puntos",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(onClick = onNewScan) {
            Text("Escanear")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        TextButton(onClick = onBack) {
            Text("Volver")
        }
    }
}
