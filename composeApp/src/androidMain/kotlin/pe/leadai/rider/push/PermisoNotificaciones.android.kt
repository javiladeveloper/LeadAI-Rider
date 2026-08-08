package pe.leadai.rider.push

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import pe.leadai.rider.datos.ContextoApp
import pe.leadai.rider.datos.ContextoActividad

/**
 * Android: pide `POST_NOTIFICATIONS` con el mismo patrón que el permiso de
 * ubicación — vía `activityResultRegistry`, sin tocar `MainActivity`.
 *
 * Antes de Android 13 el permiso no existe y se da por concedido.
 */
actual suspend fun pedirPermisoNotificaciones(): Boolean {
    // Antes de Android 13 (API 33) las notificaciones no piden permiso.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true

    val yaConcedido = ContextCompat.checkSelfPermission(
        ContextoApp.context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
    if (yaConcedido) return true

    // Sin Activity no hay diálogo que mostrar. Mismo holder que usa el
    // permiso de ubicación.
    val actividad = ContextoActividad.activity as? ComponentActivity ?: return false

    return suspendCancellableCoroutine { cont ->
        var launcher: ActivityResultLauncher<String>? = null
        launcher = actividad.activityResultRegistry.register(
            "permiso-notificaciones-rider",
            ActivityResultContracts.RequestPermission(),
        ) { concedido ->
            launcher?.unregister()
            cont.resume(concedido)
        }
        cont.invokeOnCancellation { launcher?.unregister() }
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
