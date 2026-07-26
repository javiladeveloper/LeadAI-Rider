package pe.leadai.rider.ui.login

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialOption
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.suspendCancellableCoroutine
import pe.leadai.rider.datos.ContextoActividad
import kotlin.coroutines.resume

/**
 * Pide el idToken de Google vía Credential Manager, con el Web client ID de
 * `ClavesGoogle.kt` como `serverClientId` (Credential Manager exige el
 * client ID del BACKEND que va a verificar el token, no el del cliente
 * Android — el backend de LeadAI reusa ese mismo Web client ID para validar
 * `POST /auth/google`).
 *
 * DOS intentos en cascada (saga en vivo 2026-07-23 en el celular real de
 * Jonathan, instalado desde Play):
 * 1. `GetSignInWithGoogleOption` — el flujo que Google documenta para un
 *    BOTÓN de "Iniciar sesión con Google": abre el selector COMPLETO
 *    (todas las cuentas del sistema + "Agregar otra cuenta"). El bottom
 *    sheet (`GetGoogleIdOption`) quedó DESCARTADO: filtraba cuentas (4 de
 *    ~10) y producía `NoCredentialException` fantasma.
 * 2. El SDK clásico (`GoogleSignInClient`) como red de seguridad — la
 *    tubería de las apps Android de toda la vida.
 *
 * Envuelto en try/catch amplio a propósito: `GetCredentialException` cubre
 * "sin Google Play Services", "usuario cancela el selector de cuentas" y
 * `DEVELOPER_ERROR` (SHA-1 no registrado en la consola GCP para este
 * `applicationId`/keystore) — en TODOS los casos se degrada a `null` en vez
 * de crashear, para que `LoginViewModel.entrarConGoogle` muestre el mensaje
 * amable (+ el detalle de `DiagnosticoGoogle`) y el login por contraseña
 * siga disponible (ver brief Task B6).
 */
actual suspend fun obtenerIdTokenGoogle(): String? {
    DiagnosticoGoogle.ultimoDetalle = null
    val activity = ContextoActividad.activity ?: run {
        DiagnosticoGoogle.ultimoDetalle = "sin Activity en contexto"
        return null
    }
    val manager = CredentialManager.create(activity)

    // `GetSignInWithGoogleOption` PRIMERO (0.5.4, feedback en vivo): es el
    // flujo que Google documenta para un BOTÓN de "Iniciar con Google" y
    // abre el selector COMPLETO (todas las cuentas del sistema + "Agregar
    // otra cuenta"). El bottom sheet (`GetGoogleIdOption`) filtraba cuentas
    // — Jonathan veía 4 de sus ~10 — y fue la fuente de los NO_CREDENTIAL
    // fantasma del 2026-07-23, así que salió de la cascada.
    val opciones: List<CredentialOption> = listOf(
        GetSignInWithGoogleOption.Builder(GOOGLE_WEB_CLIENT_ID).build(),
    )

    for (opcion in opciones) {
        try {
            val respuesta = manager.getCredential(
                request = GetCredentialRequest.Builder().addCredentialOption(opcion).build(),
                context = activity,
            )
            val credential = respuesta.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return GoogleIdTokenCredential.createFrom(credential.data).idToken
            }
            DiagnosticoGoogle.ultimoDetalle = "credencial inesperada: ${credential.type}"
            return null
        } catch (e: NoCredentialException) {
            // "No credentials available": este flujo no tiene cuentas que
            // ofrecer — se anota y se pasa al siguiente intento.
            DiagnosticoGoogle.ultimoDetalle = "${e.type}: ${e.message ?: "sin mensaje"}"
        } catch (e: GetCredentialException) {
            DiagnosticoGoogle.ultimoDetalle = "${e.type}: ${e.message ?: "sin mensaje"}"
            // "Account reauth failed" viene disfrazado de USER_CANCELED:
            // Credential Manager no pudo renovar la sesión de la cuenta en
            // ESTE equipo (celular real 2026-07-23; las otras apps del
            // teléfono entraban porque usan el SDK clásico). Para ese caso
            // puntual sí se cae al SDK clásico. Una cancelación GENUINA del
            // usuario no trae "reauth" — ahí no se reabre nada.
            if (e.message?.contains("reauth", ignoreCase = true) == true) {
                return intentarConSdkClasico(activity)
            }
            return null
        } catch (e: GoogleIdTokenParsingException) {
            DiagnosticoGoogle.ultimoDetalle = "token ilegible: ${e.message ?: "sin mensaje"}"
            return null
        }
    }
    // Los dos flujos de Credential Manager dijeron "sin credenciales":
    // último intento por la tubería vieja.
    return intentarConSdkClasico(activity)
}

/**
 * Tercer y último intento: el SDK CLÁSICO de Google Sign-In
 * (`GoogleSignInClient`, deprecado pero plenamente funcional — es la
 * tubería que usan las apps Android de toda la vida). Lanza el Intent del
 * selector de cuentas vía `activityResultRegistry` (no necesita tocar
 * `MainActivity`) y espera el resultado como corrutina.
 */
@Suppress("DEPRECATION")
private suspend fun intentarConSdkClasico(activity: android.app.Activity): String? {
    val componente = activity as? ComponentActivity ?: run {
        DiagnosticoGoogle.ultimoDetalle = "clasico: Activity sin registry"
        return null
    }
    return suspendCancellableCoroutine { cont ->
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(GOOGLE_WEB_CLIENT_ID)
            .requestEmail()
            .build()
        val cliente = GoogleSignIn.getClient(componente, gso)
        var launcher: ActivityResultLauncher<Intent>? = null
        launcher = componente.activityResultRegistry.register(
            "login-google-clasico",
            ActivityResultContracts.StartActivityForResult(),
        ) { resultado ->
            launcher?.unregister()
            try {
                val cuenta = GoogleSignIn.getSignedInAccountFromIntent(resultado.data)
                    .getResult(ApiException::class.java)
                if (cuenta.idToken == null) {
                    DiagnosticoGoogle.ultimoDetalle = "clasico: cuenta sin idToken"
                }
                cont.resume(cuenta.idToken)
            } catch (e: ApiException) {
                DiagnosticoGoogle.ultimoDetalle = "clasico ${e.statusCode}: ${e.message ?: "sin mensaje"}"
                cont.resume(null)
            }
        }
        cont.invokeOnCancellation { launcher?.unregister() }
        launcher.launch(cliente.signInIntent)
    }
}
