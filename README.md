# ScanR

La app guía la vuelta de fotos alrededor de un objeto. El computador, con el programa de la carpeta `server`, reconstruye la superficie y devuelve un ZIP para Blender. La instalación del computador está en [server/README.md](server/README.md).

También queda el escaneo en vivo anterior, que exporta una nube de puntos.

## Características

- **Escaneo en tiempo real** con ARCore y Depth API
- **Vista previa 3D interactiva** con rotación y zoom táctil
- **Múltiples temas de visualización** (6 esquemas de colores)
- **Exportación multi-formato** (OBJ, PLY, GLTF, STL)
- **Compartir directamente** desde la app
- Interfaz moderna con Jetpack Compose y Material Design 3
- Indicadores de progreso en tiempo real
- Soporte para profundidad por hardware (ToF/LiDAR) y software

## Capturas de Pantalla

### Flujo de Escaneo
1. **Pantalla principal** - Visor de cámara AR con botón de escaneo
2. **Durante el escaneo** - Contador de puntos y tiempo en vivo
3. **Escaneo completado** - Resumen con estadísticas
4. **Vista previa 3D** - Modelo interactivo antes de exportar
5. **Exportación** - Selección de formato y opciones

## Requisitos

- Android SDK 24+ (Android 7.0 Nougat)
- Dispositivo compatible con ARCore
- Cámara con autofocus
- OpenGL ES 2.0+

## Tecnologías

- **Kotlin** - Lenguaje de programación
- **Jetpack Compose** - UI declarativa moderna
- **ARCore** - SDK de realidad aumentada de Google
- **Sceneview** - Renderizado AR para Android
- **OpenGL ES 2.0** - Visualizador 3D personalizado
- **Coroutines** - Programación asíncrona
- **ViewModel** - Arquitectura MVVM

## Estructura del Proyecto

```
app/
├── src/main/
│   ├── java/com/scanr/app/
│   │   ├── MainActivity.kt              # Actividad principal
│   │   ├── ar/
│   │   │   ├── ARSessionManager.kt      # Gestión de sesión ARCore
│   │   │   └── DeviceSupport.kt         # Verificación de soporte
│   │   ├── data/
│   │   │   ├── Vector3.kt               # Matemáticas 3D
│   │   │   ├── Vertex.kt                # Vértice con normal
│   │   │   ├── PointCloud.kt            # Nube de puntos optimizada
│   │   │   └── MeshData.kt              # Datos de malla
│   │   ├── mesh/
│   │   │   ├── PointCloudBuilder.kt     # Extractor de puntos
│   │   │   └── MeshExtractor.kt         # Gestor de captura
│   │   ├── export/
│   │   │   ├── ObjExporter.kt           # Wavefront OBJ
│   │   │   ├── PlyExporter.kt           # Stanford PLY
│   │   │   ├── GltfExporter.kt          # glTF 2.0
│   │   │   ├── StlExporter.kt           # STL
│   │   │   └── MeshExportManager.kt     # Gestor de exportación
│   │   ├── viewmodel/
│   │   │   └── ScannerViewModel.kt      # Estado de la app
│   │   └── ui/
│   │       ├── components/
│   │       │   ├── ARCameraView.kt      # Vista AR
│   │       │   └── ARStatusOverlay.kt   # Indicadores
│   │       ├── screens/
│   │       │   ├── ScannerScreen.kt     # Pantalla de escaneo
│   │       │   ├── PreviewScreen.kt     # Vista previa 3D
│   │       │   └── ExportScreen.kt      # Opciones de exportación
│   │       ├── viewer/
│   │       │   ├── PointCloudRenderer.kt # Renderizador OpenGL
│   │       │   └── PointCloudViewer.kt   # Componente Compose
│   │       └── theme/
│   ├── res/
│   └── AndroidManifest.xml
└── build.gradle.kts
```

## Flujo de Usuario

```
┌─────────────────┐
│  Pantalla Home  │
│    [Escanear]   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Vista AR +     │◄──────┐
│  Botón Escaneo  │       │
└────────┬────────┘       │
         │ Iniciar        │
         ▼                │
┌─────────────────┐       │
│  Escaneando...  │       │
│  ● Puntos: 5000 │       │
│  ○ Tiempo: 0:15 │       │
└────────┬────────┘       │
         │ Detener        │
         ▼                │
┌─────────────────┐       │
│   Completado!   │       │
│  [Vista Previa] │       │
│  [Nuevo][Cerrar]│───────┘
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Vista 3D       │
│  ↻ Rotar táctil │
│  ⊕ Pellizcar    │
│  [Exportar]     │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Exportar       │
│  ○ OBJ ○ PLY    │
│  ○ GLTF ○ STL   │
│  [Exportar]     │
│  [Compartir]    │
└─────────────────┘
```

## Formatos de Exportación

| Formato | Extensión | Uso Recomendado |
|---------|-----------|-----------------|
| **OBJ** | .obj | Blender, Maya, 3ds Max |
| **PLY** | .ply | MeshLab, CloudCompare |
| **glTF** | .gltf | Web, Three.js |
| **STL** | .stl | Impresión 3D |

## Temas de Visualización 3D

- 🌊 **Océano** - Fondo azul oscuro, puntos cyan
- 🌲 **Bosque** - Fondo verde oscuro, puntos verdes
- 🌅 **Atardecer** - Fondo rojizo, puntos naranjas
- 🌙 **Noche** - Fondo negro, puntos púrpura
- ☀️ **Claro** - Fondo claro, puntos azules
- ✨ **Dorado** - Fondo oscuro, puntos dorados

## Compilación

```bash
./gradlew assembleDebug
```

## Instalación

```bash
./gradlew installDebug
```

## Fases de Desarrollo

### Fase 1 ✅ - Configuración Base
- [x] Proyecto Android con Kotlin y Compose
- [x] Permisos y dependencias ARCore
- [x] Gestión de permisos de cámara

### Fase 2 ✅ - Captura AR
- [x] Sesión ARCore con Depth API
- [x] Validación de soporte de dispositivo
- [x] Preview de cámara en tiempo real

### Fase 3 ✅ - Extracción 3D
- [x] PointCloudBuilder con normales
- [x] Estructuras de datos optimizadas
- [x] Captura de puntos en tiempo real

### Fase 4 ✅ - Exportación
- [x] Exportadores OBJ, PLY, GLTF, STL
- [x] FileProvider y compartir
- [x] Opciones de exportación

### Fase 5 ✅ - Interfaz Completa
- [x] Botón flotante de escaneo animado
- [x] Indicador de progreso en tiempo real
- [x] Vista previa 3D interactiva con OpenGL
- [x] Múltiples temas de visualización
- [x] Controles táctiles (rotar, zoom)
- [x] Flujo completo Scanner → Preview → Export

### Fase 6 (Próxima)
- [ ] Triangulación de nubes de puntos
- [ ] Texturas desde la cámara
- [ ] Optimización de rendimiento

## Licencia

MIT License
