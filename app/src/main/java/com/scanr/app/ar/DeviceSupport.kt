package com.scanr.app.ar

import android.content.Context
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class ARCoreAvailability {
    object Supported : ARCoreAvailability()
    object SupportedNotInstalled : ARCoreAvailability()
    object SupportedApkTooOld : ARCoreAvailability()
    object UnsupportedDeviceTooOld : ARCoreAvailability()
    object UnknownError : ARCoreAvailability()
    object UnknownChecking : ARCoreAvailability()
    object UnknownTimedOut : ARCoreAvailability()
}

data class DepthSupport(
    val isDepthSupported: Boolean,
    val isRawDepthSupported: Boolean,
    val depthMode: DepthMode
)

enum class DepthMode {
    HARDWARE,
    SOFTWARE,
    NOT_SUPPORTED
}

data class DeviceSupportInfo(
    val arCoreAvailability: ARCoreAvailability,
    val depthSupport: DepthSupport?,
    val errorMessage: String? = null
)

object DeviceSupportChecker {

    suspend fun checkARCoreAvailability(context: Context): ARCoreAvailability {
        return withContext(Dispatchers.Main) {
            try {
                val availability = ArCoreApk.getInstance().checkAvailability(context)
                
                when (availability) {
                    ArCoreApk.Availability.SUPPORTED_INSTALLED -> ARCoreAvailability.Supported
                    ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> ARCoreAvailability.SupportedNotInstalled
                    ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD -> ARCoreAvailability.SupportedApkTooOld
                    ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> ARCoreAvailability.UnsupportedDeviceTooOld
                    ArCoreApk.Availability.UNKNOWN_ERROR -> ARCoreAvailability.UnknownError
                    ArCoreApk.Availability.UNKNOWN_CHECKING -> ARCoreAvailability.UnknownChecking
                    ArCoreApk.Availability.UNKNOWN_TIMED_OUT -> ARCoreAvailability.UnknownTimedOut
                }
            } catch (e: Exception) {
                ARCoreAvailability.UnknownError
            }
        }
    }

    fun checkDepthSupport(session: Session): DepthSupport {
        val isDepthSupported = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
        val isRawDepthSupported = session.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY)
        
        val depthMode = when {
            isRawDepthSupported -> DepthMode.HARDWARE
            isDepthSupported -> DepthMode.SOFTWARE
            else -> DepthMode.NOT_SUPPORTED
        }
        
        return DepthSupport(
            isDepthSupported = isDepthSupported,
            isRawDepthSupported = isRawDepthSupported,
            depthMode = depthMode
        )
    }

    fun getDepthModeDescription(depthMode: DepthMode): String {
        return when (depthMode) {
            DepthMode.HARDWARE -> "Profundidad por hardware (ToF/LiDAR)"
            DepthMode.SOFTWARE -> "Profundidad por software (estimación)"
            DepthMode.NOT_SUPPORTED -> "Profundidad no soportada"
        }
    }

    fun getARCoreErrorMessage(availability: ARCoreAvailability): String {
        return when (availability) {
            ARCoreAvailability.Supported -> ""
            ARCoreAvailability.SupportedNotInstalled -> 
                "ARCore no está instalado. Por favor, instala Google Play Services para AR."
            ARCoreAvailability.SupportedApkTooOld -> 
                "ARCore necesita actualizarse. Por favor, actualiza Google Play Services para AR."
            ARCoreAvailability.UnsupportedDeviceTooOld -> 
                "Este dispositivo no es compatible con ARCore."
            ARCoreAvailability.UnknownError -> 
                "Error desconocido al verificar ARCore."
            ARCoreAvailability.UnknownChecking -> 
                "Verificando disponibilidad de ARCore..."
            ARCoreAvailability.UnknownTimedOut -> 
                "Tiempo de espera agotado al verificar ARCore."
        }
    }
}
