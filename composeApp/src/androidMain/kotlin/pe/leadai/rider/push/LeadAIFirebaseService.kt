package pe.leadai.rider.push

import android.app.PendingIntent
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

        val titulo = message.notification?.title ?: message.data["titulo"] ?: "LeadAI Rider"
        val cuerpo = message.notification?.body ?: message.data["cuerpo"] ?: "Hay una carrera nueva en tu zona"

        mostrarNotificacion(titulo, cuerpo)
    }

    private fun mostrarNotificacion(titulo: String, cuerpo: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            Random.nextInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notificacion = NotificationCompat.Builder(this, CANAL_NOTIFICACIONES_PEDIDOS)
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle(titulo)
            .setContentText(cuerpo)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(this).notify(Random.nextInt(), notificacion)
    }
}
