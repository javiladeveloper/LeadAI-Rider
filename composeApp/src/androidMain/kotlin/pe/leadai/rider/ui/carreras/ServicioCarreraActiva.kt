package pe.leadai.rider.ui.carreras

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import pe.leadai.rider.MainActivity
import pe.leadai.rider.datos.MotorizadosApi

/**
 * Mantiene vivo el reporte de GPS mientras el rider tiene una carrera.
 *
 * Sin esto, al bloquear el teléfono los `LaunchedEffect` de
 * [CarrerasPantalla] se suspenden y el cliente ve la moto CONGELADA en el
 * mapa mientras el rider maneja con el celular en el bolsillo. Un foreground
 * service con notificación persistente es la única forma que Android permite
 * de seguir leyendo ubicación con la app en segundo plano.
 *
 * Vive SOLO mientras hay carrera: se arranca al aceptar y se para al
 * entregar (y en el `onDispose` de la pantalla). Un service permanente drena
 * batería y Play lo cuestiona en revisión.
 *
 * `KoinComponent`/`by inject()` (koin-core, sin `koin-android`): mismo patrón
 * que [pe.leadai.rider.push.LeadAIFirebaseService]. Es válido porque Koin ya
 * está arrancado en `LeadAIRiderApp.onCreate` antes de que el sistema pueda
 * instanciar este servicio. Se prefiere a `GlobalContext.get().get()` porque
 * `inject()` es perezoso y no explota si algo se resuelve tarde.
 */
class ServicioCarreraActiva : Service(), KoinComponent {

    private val motorizadosApi: MotorizadosApi by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val destino = intent?.getStringExtra(EXTRA_DESTINO).orEmpty()

        // `startForeground` TIENE que ir protegido, y acá está el motivo:
        //
        // Con `foregroundServiceType="location"` y targetSdk 34+, Android
        // lanza SecurityException si el permiso de ubicación todavía no está
        // concedido cuando el servicio pasa a primer plano. El intento
        // anterior de cubrir esto envolvía `startForegroundService` en el
        // `iniciar()` de abajo, pero ahí NO salta: ese arranque es asíncrono y
        // devuelve bien; la excepción explota después, en ESTA línea, ya en el
        // proceso del servicio — y sin capturar, se lleva la app entera.
        //
        // Pasaba de verdad: el rider aceptaba una carrera, Android le mostraba
        // el diálogo de permisos, y la app se cerraba sola.
        //
        // Si falla, el servicio se detiene y no se arranca el loop: sin
        // permiso no hay ubicación que reportar. La carrera sigue viva — se
        // pierde el rastreo en segundo plano, no el trabajo.
        val enPrimerPlano = runCatching {
            startForeground(ID_NOTIFICACION, construirNotificacion(destino))
        }
        if (enPrimerPlano.isFailure) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Un solo loop aunque el sistema reentregue el Intent (START_STICKY):
        // dos loops reportarían la misma posición dos veces por vuelta.
        if (reportando) return START_STICKY
        reportando = true

        scope.launch {
            while (isActive) {
                // Silencioso: sin GPS o sin red, se reintenta a la vuelta.
                runCatching {
                    obtenerUbicacionActual()?.let { motorizadosApi.reportarPosicion(it.lat, it.lng) }
                }
                delay(INTERVALO_MS)
            }
        }
        // START_STICKY: si Android lo mata por memoria, que vuelva solo — el
        // rider sigue manejando aunque el sistema haya hecho limpieza.
        return START_STICKY
    }

    override fun onDestroy() {
        reportando = false
        scope.cancel()
        super.onDestroy()
    }

    /** Tocar la notificación devuelve al rider a su carrera, no a una app en blanco. */
    private fun intentAlAbrir(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun construirNotificacion(destino: String): Notification {
        val canal = NotificationChannel(
            CANAL_ID,
            "Carrera en curso",
            // BAJA a propósito: debe estar ahí, pero no sonar ni vibrar. El
            // canal ruidoso es el de "nueva carrera en tu zona"; este solo
            // acompaña al rider mientras maneja.
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Mantiene tu ubicación en vivo mientras llevas una carrera" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(canal)

        return NotificationCompat.Builder(this, CANAL_ID)
            .setContentTitle("🛵 Carrera en curso")
            .setContentText(if (destino.isBlank()) "Compartiendo tu ubicación" else "Rumbo a $destino")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(intentAlAbrir())
            .build()
    }

    companion object {
        private const val CANAL_ID = "carrera_activa"
        private const val ID_NOTIFICACION = 1001
        private const val EXTRA_DESTINO = "destino"

        /** Mismo pulso que tenía la pantalla: la moto se mueve fluida en el mapa del cliente. */
        private const val INTERVALO_MS = 5_000L

        /**
         * Guarda contra loops duplicados: `onStartCommand` puede llamarse
         * varias veces sobre la MISMA instancia (reentrega por START_STICKY,
         * o la pantalla arrancándolo de nuevo tras una recomposición).
         */
        private var reportando = false

        /**
         * `minSdk = 26`, así que `startForegroundService` está siempre
         * disponible.
         *
         * No se arranca si todavía no hay permiso de ubicación: un service de
         * tipo "location" sin permiso muere en `startForeground` (ver el
         * comentario de `onStartCommand`). Salir temprano evita arrancar un
         * proceso para nada mientras el rider mira el diálogo de permisos.
         *
         * Esto NO reemplaza al `runCatching` de `onStartCommand`: entre este
         * chequeo y el arranque real el permiso puede revocarse, y el service
         * también puede reentregarse solo por START_STICKY. Las dos capas
         * hacen falta.
         */
        fun iniciar(contexto: Context, destino: String): Boolean {
            if (!hayPermisoDeUbicacion(contexto)) return false
            val intent = Intent(contexto, ServicioCarreraActiva::class.java)
                .putExtra(EXTRA_DESTINO, destino)
            return runCatching { contexto.startForegroundService(intent) }.isSuccess
        }

        /** Alcanza con una de las dos: el GPS fino o el aproximado. */
        private fun hayPermisoDeUbicacion(contexto: Context): Boolean {
            fun concedido(permiso: String) =
                ContextCompat.checkSelfPermission(contexto, permiso) ==
                    PackageManager.PERMISSION_GRANTED
            return concedido(android.Manifest.permission.ACCESS_FINE_LOCATION) ||
                concedido(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        fun detener(contexto: Context) {
            runCatching { contexto.stopService(Intent(contexto, ServicioCarreraActiva::class.java)) }
        }
    }
}
