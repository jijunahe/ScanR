# ScanR en tu computador

Este programa recibe las fotos del teléfono y devuelve un modelo 3D para Blender.

El teléfono y el computador tienen que estar en la misma red WiFi.

## 1. Instala Python

Necesitas Python 3.10 o más nuevo. En la instalación de Windows, marca **Add python.exe to PATH**.

## 2. Instala COLMAP

COLMAP es el programa que reconstruye el objeto.

- Windows: descarga el ZIP de https://github.com/colmap/colmap/releases
  - Si tu PC tiene tarjeta NVIDIA, usa la versión CUDA.
  - Si no, usa la versión sin CUDA.
  - Descomprime la carpeta. La ruta de `COLMAP.bat` se indica así, en el mismo símbolo del sistema donde arrancas el servidor:

```bat
set COLMAP_PATH=C:\ruta\COLMAP\COLMAP.bat
```

- Linux:

```bash
sudo apt install colmap
```

Con tarjeta NVIDIA puedes activar la malla detallada y la textura:

```bat
set SCANR_USE_GPU=1
```

## 3. Arranca el servidor

En esta carpeta `server`:

- Windows: doble clic en `run.bat`
- Linux o macOS: `sh run.sh`

Cuando esté listo, abre en el navegador del computador `http://127.0.0.1:8000/api/health`.
Tiene que decir `"colmap": true`.

## 4. Anota la IP del computador

- Windows: `ipconfig` y copia la IPv4 de WiFi, por ejemplo `192.168.1.20`
- Linux: `hostname -I`

En el teléfono, en ScanR, escribe:

```text
http://192.168.1.20:8000
```

Si Windows pregunta, permite Python en redes privadas.

## 5. Cómo fotografiar

1. Pon el objeto quieto, con luz pareja. Un pocillo liso sale mejor sobre un periódico o con un trozo de cinta, para que tenga marcas.
2. Centra el círculo en el objeto y pulsa **Este es el objeto**.
3. Camina alrededor. La app toma la foto sola cuando el ángulo es nuevo y el teléfono está quieto.
4. Haz una vuelta a media altura y otra más arriba, hasta llenar los puntos.
5. Pulsa **Enviar al computador**. En unos minutos el teléfono recibe un ZIP. Ábrelo en Blender con el archivo `modelo.ply`.
