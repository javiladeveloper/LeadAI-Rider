package pe.leadai.rider.push

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

/**
 * Token FCM real del dispositivo. Envuelto en try/catch a propósito: sin
 * `google-services.json` en el repo (Jonathan aún no crea el proyecto
 * Firebase, ver `composeApp/FIREBASE.md`), `FirebaseApp` nunca se inicializa
 * y `FirebaseMessaging.getInstance()` lanza `IllegalStateException` — acá se
 * traduce a `null` en vez de propagar la excepción, para que
 * `RegistroPushRepositorio.registrar()/desregistrar()` simplemente no hagan
 * nada (no-op) hasta que Jonathan complete la guía. Cualquier otro error
 * (sin Play Services, sin conexión al pedir el token, etc.) se trata igual:
 * push es una mejora, nunca debe bloquear el login/logout.
 */
actual suspend fun tokenPushActual(): String? =
    try {
        FirebaseMessaging.getInstance().token.await()
    } catch (e: Exception) {
        null
    }
