package pe.leadai.rider.ui.carreras

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import pe.leadai.rider.datos.ContextoActividad
import kotlin.coroutines.resume

/**
 * El permiso se pide UNA vez por proceso: esto corre dentro del polling de
 * 15s de la sala del rider, y si lo denegó no se le puede reabrir el diálogo
 * en cada vuelta.
 */
private var permisoYaPedido = false

/** Cuántas veces se pidió, para que cada registro tenga una clave distinta. */
private var pedidosDePermiso = 0

actual suspend fun obtenerUbicacionActual(loPidioElUsuario: Boolean): UbicacionRider? {
    val activity = ContextoActividad.activity ?: return null
    if (!tienePermisoUbicacion(activity)) {
        // Un TOQUE del usuario siempre vuelve a preguntar.
        //
        // `permisoYaPedido` es global al proceso y no se reseteaba nunca, así
        // que bastaba un intento fallido —la pantalla abriendo antes de que la
        // Activity estuviera lista, o un "ahora no"— para que TODAS las
        // llamadas siguientes devolvieran null sin pedir nada. El botón verde
        // quedaba muerto y sin ninguna señal de por qué.
        //
        // En automático se sigue pidiendo una sola vez: insistir en cada
        // pantalla sería un diálogo cada dos por tres.
        if (permisoYaPedido && !loPidioElUsuario) return null
        permisoYaPedido = true
        val componente = activity as? ComponentActivity ?: return null
        if (!pedirPermisoUbicacion(componente)) return null
    }
    return try {
        val manager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val proveedores = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        // La ÚLTIMA CONOCIDA primero, que es instantánea.
        //
        // Antes se pedía un fix fresco al GPS y recién después se caía a la
        // última conocida. Pero `getCurrentLocation` no tiene timeout y puede
        // tardar varios segundos —o no volver nunca bajo techo—, así que el
        // campo "¿Desde dónde?" quedaba vacío mientras el cliente miraba.
        //
        // Una posición de hace un minuto alcanza de sobra para prellenar el
        // origen: el cliente está en su casa, no en movimiento. Y si el fix
        // fresco llega después, el ViewModel lo pisa igual.
        val conocida = proveedores
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }

        // Si no hay ninguna guardada (emulador recién borrado, teléfono que
        // nunca usó el GPS) sí se espera el fix fresco: es eso o nada.
        val ubicacion = conocida
            ?: proveedores.firstNotNullOfOrNull { ubicacionFresca(activity, manager, it) }
        ubicacion?.let { UbicacionRider(it.latitude, it.longitude) }
    } catch (e: SecurityException) {
        null
    }
}

private fun tienePermisoUbicacion(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Diálogo de permiso vía `activityResultRegistry` (mismo patrón que el SDK
 * clásico de `ObtenerIdTokenGoogle.android.kt` — no toca `MainActivity`).
 */
private suspend fun pedirPermisoUbicacion(componente: ComponentActivity): Boolean =
    suspendCancellableCoroutine { cont ->
        var launcher: ActivityResultLauncher<String>? = null
        launcher = componente.activityResultRegistry.register(
            // Clave única por pedido: `activityResultRegistry` revienta si se
            // registra dos veces la misma, y ahora se puede pedir más de una
            // vez en la misma sesión.
            "permiso-ubicacion-" + (++pedidosDePermiso),
            ActivityResultContracts.RequestPermission(),
        ) { concedido ->
            launcher?.unregister()
            cont.resume(concedido)
        }
        cont.invokeOnCancellation { launcher?.unregister() }
        launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

private suspend fun ubicacionFresca(
    context: Context,
    manager: LocationManager,
    proveedor: String,
): Location? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    return try {
        suspendCancellableCoroutine { cont ->
            manager.getCurrentLocation(proveedor, null, ContextCompat.getMainExecutor(context)) {
                cont.resume(it)
            }
        }
    } catch (e: SecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}
