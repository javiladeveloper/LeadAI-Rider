package pe.leadai.rider.push

import android.app.PendingIntent
import pe.leadai.rider.R
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import pe.leadai.rider.MainActivity
import kotlin.random.Random

/** Id del canal de notificaciones — debe coincidir con el creado en `LeadAIRiderApp.onCreate`. */
const val CANAL_NOTIFICACIONES_PEDIDOS = "pedidos"

/**
 * Canal aparte para "tu motorizado llegó".
 *
 * Es EL aviso que el cliente espera con el teléfono en la mano, y tiene que
 * distinguirse de todo lo demás: vibración larga y sonido propio. Si suena
 * igual que un mensaje cualquiera, el rider termina esperando en la puerta
 * llamando por teléfono — justo lo que la app debería evitar.
 *
 * Un canal separado además deja que la persona silencie los avisos comunes
 * sin perderse este.
 */
const val CANAL_LLEGADA = "llegada"

/** Marca que la app se abrió tocando una notificación, no desde el ícono. */
const val EXTRA_DESDE_NOTIFICACION = "desde_notificacion"

/**
 * Servicio FCM: recibe tokens nuevos y mensajes push. Registrado en
 * `AndroidManifest.xml` con el intent-filter `MESSAGING_EVENT`.
 *
 * - `onNewToken`: FCM puede rotar el token en cualquier momento (reinstalación,
 *   restauración de backup, etc.) — se re-registra contra el backend vía
 *   [RegistroPushRepositorio], igual que el registro post-login.
 * - `onMessageReceived`: arma una notificación local en el canal
 *   [CANAL_NOTIFICACIONES_PEDIDOS] (creado en `LeadAIRiderApp.onCreate`, no
 *   acá — un canal se crea una sola vez en la vida de la app). Usa
 *   `notification` si el backend lo manda (push visible estándar) o cae a los
 *   campos de `data` como respaldo (mensajes data-only, más flexibles del lado
 *   servidor).
 *
 * El tap solo abre `MainActivity`, sin extras: el push del rider es "nueva
 * carrera en tu zona" y su destino es el pool de carreras, que ya es la
 * pantalla principal. El `pedidoId` que manda el backend viaja en el `data`
 * pero acá no se usa — la carrera puede habérsela llevado otro rider antes de
 * que este abra el aviso, así que llevarlo a un detalle fijo sería mentirle.
 *
 * `KoinComponent`/`by inject()` (koin-core, sin la dependencia
 * `koin-android`): válido porque Koin ya está arrancado globalmente en
 * `LeadAIRiderApp.onCreate` (`startKoin { modules(moduloApp) }`) antes de que
 * el sistema pueda instanciar este servicio.
 */
class LeadAIFirebaseService : FirebaseMessagingService(), KoinComponent {

    private val registroPush: RegistroPushRepositorio by inject()
    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        scope.launch {
            registroPush.registrar()
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // Aviso de que una carrera YA NO ESTÁ (la cancelaron o la tomó otro):
        // no muestra nada, borra el que ya está en la barra. Un rider que toca
        // "nueva carrera" y no encuentra nada deja de mirar los avisos.
        if (message.data["tipo"] == "carrera_cerrada") {
            NotificationManagerCompat.from(this).cancelAll()
            return
        }

        val titulo = message.notification?.title ?: message.data["titulo"] ?: "LeadAI Rider"
        val cuerpo = message.notification?.body ?: message.data["cuerpo"] ?: "Hay una carrera nueva en tu zona"

        mostrarNotificacion(titulo, cuerpo, message.data["hito"])
        // Que la pantalla abierta refresque YA, sin esperar al polling: si no,
        // el aviso aparece en la barra antes que la carrera en la lista.
        pe.leadai.rider.ui.comunes.AvisoPush.avisar(message.data["hito"])
    }

    private fun mostrarNotificacion(titulo: String, cuerpo: String, hito: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Marca de que se entró TOCANDO una notificación.
            //
            // El rider vino a atender algo concreto —una carrera nueva, el
            // cliente que llegó— y meterle un diálogo de actualización encima
            // le tapa justo eso. El aviso se guarda para cuando abra la app
            // por su cuenta.
            putExtra(EXTRA_DESDE_NOTIFICACION, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            Random.nextInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // El aviso de llegada va por su propio canal, con vibración y sonido.
        val canal = if (hito == "llego") {
            CANAL_LLEGADA
        } else {
            CANAL_NOTIFICACIONES_PEDIDOS
        }
        val notificacion = NotificationCompat.Builder(this, canal)
            // EL ÍCONO DE LA MARCA, no el androide genérico.
            //
            // `sym_def_app_icon` es el muñeco de Android: en la barra de
            // estado la notificación no se distinguía de ninguna otra app.
            //
            // Va la variante MONOCROMA y no el launcher a color: Android
            // tiñe el ícono de notificación con un color plano y descarta
            // el resto, asi que un ícono a color sale como un cuadrado gris.
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(titulo)
            .setContentText(cuerpo)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(this).notify(Random.nextInt(), notificacion)
    }
}
