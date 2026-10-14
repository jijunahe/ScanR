package com.scanr.app.ar

import android.app.Activity
import android.util.Log
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "ARSessionManager"

sealed class ARSessionState {
    object NotInitialized : ARSessionState()
    object Initializing : ARSessionState()
    data class Ready(val depthSupport: DepthSupport) : ARSessionState()
    data class Tracking(val frame: Frame, val depthSupport: DepthSupport) : ARSessionState()
    object Paused : ARSessionState()
    data class Error(val message: String, val isRecoverable: Boolean) : ARSessionState()
}

class ARSessionManager {
    
    private var session: Session? = null
    private var installRequested = false
    
    private val _sessionState = MutableStateFlow<ARSessionState>(ARSessionState.NotInitialized)
    val sessionState: StateFlow<ARSessionState> = _sessionState.asStateFlow()
    
    private val _depthSupport = MutableStateFlow<DepthSupport?>(null)
    val depthSupport: StateFlow<DepthSupport?> = _depthSupport.asStateFlow()
    
    private val _trackingState = MutableStateFlow(TrackingState.STOPPED)
    val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()
    
    fun initializeSession(activity: Activity): Boolean {
        _sessionState.value = ARSessionState.Initializing
        
        if (session != null) {
            return true
        }
        
        try {
            when (ArCoreApk.getInstance().requestInstall(activity, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    installRequested = true
                    _sessionState.value = ARSessionState.Error(
                        "Instalando ARCore...",
                        isRecoverable = true
                    )
                    return false
                }
                ArCoreApk.InstallStatus.INSTALLED -> {
                    // ARCore está instalado, continuar
                }
            }
            
            session = Session(activity).also { arSession ->
                val depthSupportInfo = DeviceSupportChecker.checkDepthSupport(arSession)
                _depthSupport.value = depthSupportInfo
                
                val config = Config(arSession).apply {
                    if (depthSupportInfo.isDepthSupported) {
                        depthMode = Config.DepthMode.AUTOMATIC
                        Log.d(TAG, "Depth mode AUTOMATIC enabled")
                    } else {
                        depthMode = Config.DepthMode.DISABLED
                        Log.d(TAG, "Depth mode DISABLED - not supported")
                    }
                    
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                    focusMode = Config.FocusMode.AUTO
                }
                
                arSession.configure(config)
                
                _sessionState.value = ARSessionState.Ready(depthSupportInfo)
                Log.d(TAG, "ARCore session initialized successfully")
                Log.d(TAG, "Depth support: ${DeviceSupportChecker.getDepthModeDescription(depthSupportInfo.depthMode)}")
            }
            
            return true
            
        } catch (e: UnavailableArcoreNotInstalledException) {
            _sessionState.value = ARSessionState.Error(
                "ARCore no está instalado",
                isRecoverable = true
            )
        } catch (e: UnavailableUserDeclinedInstallationException) {
            _sessionState.value = ARSessionState.Error(
                "ARCore es necesario para esta aplicación",
                isRecoverable = true
            )
        } catch (e: UnavailableApkTooOldException) {
            _sessionState.value = ARSessionState.Error(
                "Por favor actualiza ARCore",
                isRecoverable = true
            )
        } catch (e: UnavailableSdkTooOldException) {
            _sessionState.value = ARSessionState.Error(
                "Por favor actualiza la aplicación",
                isRecoverable = false
            )
        } catch (e: UnavailableDeviceNotCompatibleException) {
            _sessionState.value = ARSessionState.Error(
                "Este dispositivo no soporta ARCore",
                isRecoverable = false
            )
        } catch (e: Exception) {
            _sessionState.value = ARSessionState.Error(
                "Error al inicializar ARCore: ${e.message}",
                isRecoverable = false
            )
            Log.e(TAG, "Failed to create ARCore session", e)
        }
        
        return false
    }
    
    fun resume() {
        session?.let { arSession ->
            try {
                arSession.resume()
                _depthSupport.value?.let { depth ->
                    _sessionState.value = ARSessionState.Ready(depth)
                }
                Log.d(TAG, "ARCore session resumed")
            } catch (e: CameraNotAvailableException) {
                _sessionState.value = ARSessionState.Error(
                    "Cámara no disponible",
                    isRecoverable = true
                )
                Log.e(TAG, "Camera not available on resume", e)
            }
        }
    }
    
    fun pause() {
        session?.pause()
        _sessionState.value = ARSessionState.Paused
        Log.d(TAG, "ARCore session paused")
    }
    
    fun update(): Frame? {
        return session?.let { arSession ->
            try {
                val frame = arSession.update()
                _trackingState.value = frame.camera.trackingState
                
                _depthSupport.value?.let { depth ->
                    _sessionState.value = ARSessionState.Tracking(frame, depth)
                }
                
                frame
            } catch (e: CameraNotAvailableException) {
                _sessionState.value = ARSessionState.Error(
                    "Cámara no disponible",
                    isRecoverable = true
                )
                Log.e(TAG, "Camera not available on update", e)
                null
            }
        }
    }
    
    fun getSession(): Session? = session
    
    fun destroy() {
        session?.close()
        session = null
        _sessionState.value = ARSessionState.NotInitialized
        Log.d(TAG, "ARCore session destroyed")
    }
    
    fun isSessionInitialized(): Boolean = session != null
    
    fun getDepthImage(frame: Frame) = try {
        if (_depthSupport.value?.isDepthSupported == true) {
            frame.acquireDepthImage16Bits()
        } else {
            null
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to acquire depth image", e)
        null
    }
    
    fun getRawDepthImage(frame: Frame) = try {
        if (_depthSupport.value?.isRawDepthSupported == true) {
            frame.acquireRawDepthImage16Bits()
        } else {
            null
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to acquire raw depth image", e)
        null
    }
}
