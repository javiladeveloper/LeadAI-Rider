package pe.leadai.rider.ui.login

/**
 * Pide el idToken de Google del usuario vía Credential Manager (Android) —
 * `expect/actual` porque depende de la plataforma, mismo patrón que
 * `tokenPushActual()`/`crearDataStore()`/`epochMsAhora()` (ver
 * ARQUITECTURA.md, "Patrón expect/actual").
 *
 * Devuelve `null` ante CUALQUIER fallo (sin Google Play Services, el usuario
 * cancela el selector de cuentas, `DEVELOPER_ERROR` por SHA-1 no registrado
 * en la consola GCP, sin conexión, etc.) — nunca lanza. `LoginViewModel`
 * traduce `null` a un mensaje amable y el login por contraseña sigue
 * disponible. `iosMain` siempre devuelve `null` (stub: Fase D).
 */
expect suspend fun obtenerIdTokenGoogle(): String?

/**
 * Detalle técnico del ÚLTIMO fallo de [obtenerIdTokenGoogle] (diagnóstico en
 * vivo 2026-07-23: el login Google fallaba en el celular de Jonathan con el
 * mensaje genérico y sin cable USB no había forma de ver la causa). El actual
 * de Android lo llena en cada catch; `LoginViewModel` lo muestra en chico
 * debajo del mensaje amable. `null` = sin detalle (p.ej. en tests o iOS).
 */
object DiagnosticoGoogle {
    var ultimoDetalle: String? = null
}
