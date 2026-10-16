# ScanR — instrucciones para el agente del computador

Sigue este archivo en orden. Al terminar, el computador tiene que estar recibiendo fotos del teléfono y devolviendo un modelo 3D, y el teléfono tiene que tener instalada la app `com.scanr.app`.

Repositorio: https://github.com/jijunahe/ScanR  
Rama: `main`  
El repositorio es público.

No subas `server/data/`. Ahí quedan las fotos y los modelos de cada trabajo. No publiques el servidor en Internet: solo tiene que escuchar en la red local.

## 1. Descargar el proyecto

Detecta el sistema antes de elegir comandos.

```bash
git clone https://github.com/jijunahe/ScanR.git
cd ScanR
git checkout main
git pull origin main
```

Si la carpeta `ScanR` ya existe, entra en ella y actualiza `main`. No clones una segunda copia.

Comprueba que existen estas rutas:

- `server/scanr_backend/main.py`
- `server/requirements.txt`
- `server/run.bat`
- `server/run.sh`
- `gradlew`
- `app/build.gradle.kts`
- `settings.gradle.kts`

La raíz del proyecto es la carpeta que contiene `settings.gradle.kts`. Android Studio y Gradle se abren ahí, no dentro de `app/`.

## 2. Backend en el computador

El teléfono envía las fotos a este proceso. COLMAP reconstruye el objeto. FastAPI entrega un ZIP.

### 2.1 Python

Hace falta Python 3.10 o más nuevo. Compruébalo:

```bash
python --version
```

En Windows, si `python` no existe, prueba `py --version`. En Linux o macOS usa `python3`.

Si falta Python:

- Windows: instálalo desde https://www.python.org/downloads/ y marca **Add python.exe to PATH**. Cierra y abre de nuevo la terminal.
- Linux: `sudo apt update && sudo apt install -y python3 python3-pip python3-venv`

Crea el entorno dentro de `server`:

Windows:

```bat
cd server
py -m venv .venv
.venv\Scripts\activate
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

Linux o macOS:

```bash
cd server
python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

`server/.venv/` no se sube a git.

### 2.2 COLMAP

COLMAP es un programa aparte. El `pip install` no lo incluye.

Comprueba si ya está:

```bash
colmap -h
```

Si ese comando escribe la ayuda, no hace falta instalarlo. Si no está:

**Windows**

1. Abre https://github.com/colmap/colmap/releases y descarga el ZIP más reciente.
2. Si el computador tiene GPU NVIDIA, descarga la versión CUDA. Si no, la versión sin CUDA.
3. Descomprime el ZIP. Dentro tiene que haber `COLMAP.bat` o `bin\colmap.exe`.
4. Deja fija la ruta. Ajusta la carpeta real:

```bat
setx COLMAP_PATH "C:\Program Files\COLMAP\COLMAP.bat"
```

Cierra la terminal y ábrela otra vez. `setx` no afecta a la ventana actual.

Para la ventana actual, además:

```bat
set COLMAP_PATH=C:\Program Files\COLMAP\COLMAP.bat
```

**Linux**

```bash
sudo apt update
sudo apt install -y colmap
```

**macOS**

```bash
brew install colmap
```

**GPU NVIDIA, opcional**

Solo si COLMAP es la build CUDA y `nvidia-smi` funciona. Produce una malla más fina y puede tardar menos.

Windows, permanente:

```bat
setx SCANR_USE_GPU 1
```

Linux o macOS, en el mismo shell donde arrancarás el servidor:

```bash
export SCANR_USE_GPU=1
```

Sin esa variable el servidor usa CPU. Es el camino normal en un computador sin NVIDIA.

### 2.3 Arrancar

El proceso tiene que escuchar en todas las interfaces, puerto 8000, para que el teléfono llegue por WiFi. El entorno virtual tiene que estar activado y `COLMAP_PATH` tiene que verse en esa misma terminal.

Desde `server`:

```bash
python -m uvicorn scanr_backend.main:app --host 0.0.0.0 --port 8000
```

Déjalo en primer plano, o en una terminal que no cierres. `run.bat` y `run.sh` hacen el `pip install` y este mismo arranque, pero no activan el entorno virtual ni `COLMAP_PATH`. Si usas esos scripts, exporta `COLMAP_PATH` antes.

### 2.4 Comprobar

En otra terminal:

```bash
curl http://127.0.0.1:8000/api/health
```

La respuesta correcta es un JSON con `"ok": true` y `"colmap": true`. `"colmap": false` significa que el proceso no encontró COLMAP: revisa `COLMAP_PATH`, abre una terminal nueva y vuelve a arrancar uvicorn.

Contrato que ya implementa el servidor:

| Método | Ruta | Uso |
| --- | --- | --- |
| GET | `/api/health` | Dice si COLMAP está visible |
| POST | `/api/jobs` | Campo de formulario `meta` (JSON) y archivos `images` |
| GET | `/api/jobs/{id}` | `queued`, `running`, `done` o `error`, más `message` |
| GET | `/api/jobs/{id}/model` | ZIP con `modelo.ply`, `modelo.obj` y `nube.ply` |

Los trabajos quedan en `server/data/<id>/`. El log de COLMAP está en `server/data/<id>/job.log`.

### 2.5 IP que debe escribir el teléfono

El teléfono y el computador tienen que estar en la misma WiFi. No uses la IP del cable si el teléfono está en WiFi, ni `127.0.0.1`: esa dirección es el propio teléfono.

Windows:

```bat
ipconfig
```

Linux:

```bash
hostname -I
```

macOS:

```bash
ipconfig getifaddr en0
```

Quédate con la IPv4 de la WiFi, por ejemplo `192.168.1.20`. La URL de la app es:

```text
http://192.168.1.20:8000
```

Sin barra final. Sin `https`.

**Windows Firewall.** Cuando salga el aviso, permite Python en redes privadas. Si no sale y el teléfono no conecta, permite el puerto 8000 en el perfil privado:

```bat
netsh advfirewall firewall add rule name="ScanR" dir=in action=allow protocol=TCP localport=8000 profile=private
```

Comprueba desde el propio computador, usando la IP de la WiFi y no localhost:

```bash
curl http://192.168.1.20:8000/api/health
```

Sustituye la IP. Si localhost responde y esta IP no, el firewall está bloqueando el puerto.

## 3. App Android

Hay dos caminos. Usa el 3.1 si solo hay que instalar la app. Usa el 3.2 si hay que compilarla en este computador.

Datos fijos del proyecto:

- `applicationId` y paquete: `com.scanr.app`
- `minSdk` 24, `compileSdk` 34, `targetSdk` 34
- Versión actual: `versionName` 2.0.0, `versionCode` 2
- JDK 17
- Android Gradle Plugin 8.2.2
- Kotlin 1.9.22
- Gradle 8.5, mediante `gradlew`
- La app ya permite HTTP sin cifrar, porque el servidor local no usa HTTPS

El teléfono de prueba es un Samsung Galaxy S23 Ultra. Tiene que tener **Google Play Services for AR** (ARCore). La app no abre el flujo de fotos si ARCore no está instalado.

### 3.1 Instalar el APK ya compilado

En cada push a `main` corre la acción **Build Android APK**. El artefacto se llama `ScanR-debug-apk` y dentro está `app-debug.apk`.

Con GitHub CLI, autenticado:

```bash
gh run list --repo jijunahe/ScanR --workflow "Build Android APK" --limit 5
gh run download --repo jijunahe/ScanR --name ScanR-debug-apk
```

Sin `gh`, abre https://github.com/jijunahe/ScanR/actions, entra en la ejecución verde más reciente de `main` y descarga el artefacto `ScanR-debug-apk`.

Instalación por USB:

1. En el teléfono: Ajustes, Opciones de desarrollador, Depuración USB.
2. Conecta el cable y acepta la huella del computador.
3. Comprueba `adb devices`. Tiene que salir el dispositivo como `device`, no como `unauthorized`.
4. Instala, sustituyendo la versión anterior:

```bash
adb install -r app-debug.apk
```

Si `adb` no existe, instala Android SDK Platform-Tools y añade su carpeta al `PATH`.

### 3.2 Compilar en este computador

Hace falta JDK 17 y Android SDK con la plataforma 34 y las build-tools correspondientes.

Variables:

- `JAVA_HOME` apuntando al JDK 17
- `ANDROID_HOME` apuntando al SDK

La primera vez, acepta licencias:

```bash
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses
```

En la raíz del repositorio, no dentro de `app/`:

Windows:

```bat
gradlew.bat assembleDebug
```

Linux o macOS:

```bash
chmod +x gradlew
./gradlew assembleDebug
```

El APK queda en:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Instálalo con `adb install -r` como en el apartado 3.1.

Android Studio, si se usa: **File → Open** y elige la raíz del repositorio. Gradle JDK: 17. No hace falta un archivo de firma para el APK de debug.

## 4. Prueba de punta a punta

1. El servidor sigue en marcha y `/api/health` responde `"colmap": true` usando la IP de la WiFi.
2. La app está instalada. Ábrela. El botón principal es **Fotografiar objeto**.
3. En el campo **Computador** escribe `http://IP-DEL-COMPUTADOR:8000` y pulsa **Probar**.
   - "Conectado. COLMAP está listo." es el resultado correcto.
   - "Conectado, pero falta instalar COLMAP" es el servidor sin `COLMAP_PATH`.
   - "No llegué al computador" es WiFi, IP o firewall.
4. Pon el objeto quieto, con luz pareja. Un objeto liso, como un pocillo blanco, necesita marcas: un periódico debajo o un trozo de cinta.
5. Centra el círculo y pulsa **Este es el objeto**.
6. Camina alrededor. La app toma la foto sola cuando el ángulo es nuevo y el teléfono está quieto. Haz una vuelta a media altura y otra más arriba. Con 8 fotos ya deja enviar; 24 cubren mejor.
7. Pulsa **Enviar al computador**. El teléfono muestra el mensaje de progreso. En el computador aparecen líneas de COLMAP y una carpeta nueva en `server/data/`.
8. Al terminar, el teléfono comparte un ZIP. En Blender abre `modelo.ply`.

**Escaneo en vivo** es el flujo anterior y exporta una nube de puntos. El flujo de fotos es el que usa este servidor.

## 5. Si algo falla

| Síntoma | Qué hacer |
| --- | --- |
| `python` no se reconoce | Reabre la terminal después de instalar Python, o usa `py` / `python3` |
| `"colmap": false` | `COLMAP_PATH` apunta al `.bat` o al ejecutable, y uvicorn se arrancó después de definirlo |
| El teléfono no conecta y localhost sí | Misma WiFi, IP de esa WiFi, firewall del puerto 8000 |
| La app se queda en "Problema con ARCore" | Instala o actualiza Google Play Services for AR |
| El trabajo pasa a `error` | Lee `server/data/<id>/job.log` y el campo `message`. Casi siempre faltan fotos, solape o textura en el objeto |
| Gradle no encuentra el SDK | Define `ANDROID_HOME` y acepta las licencias |
| `adb devices` vacío | Cable de datos, depuración USB y autorización en la pantalla del teléfono |

No cambies el puerto ni el nombre de los campos `meta` e `images` sin cambiar también `GuidedCaptureScreen.kt`. La app y el servidor tienen que coincidir.

## 6. Terminado

Entrega estos datos a la persona:

- Ruta local del repositorio.
- Resultado de `curl` a `/api/health` con la IP de la WiFi.
- URL exacta que hay que pegar en la app, con la forma `http://192.168.x.x:8000`.
- Confirmación de que `com.scanr.app` quedó instalada, o la ruta del APK si el teléfono no estaba conectado.
