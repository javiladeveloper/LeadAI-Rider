package pe.leadai.rider.push

import pe.leadai.rider.datos.ApiCliente
import pe.leadai.rider.datos.DesregistrarDispositivoRequestDto
import pe.leadai.rider.datos.DispositivoPushResponseDto
import pe.leadai.rider.datos.RegistrarDispositivoRequestDto
import pe.leadai.rider.datos.Resultado

/**
 * Token push del dispositivo actual (FCM en Android). `expect/actual` porque
 * obtenerlo depende de la plataforma — mismo patrón que `crearDataStore()`/
 * `epochMsAhora()` (ver `datos/crearDataStore.kt`, `ui/tema/Reloj.kt`).
 *
 * Devuelve `null` cuando no hay token disponible: sin Firebase inicializado
 * (repo sin `google-services.json`, ver `composeApp/build.gradle.kts`), sin
 * Play Services, o cualquier error al pedirlo — nunca lanza. `iosMain`
 * siempre devuelve `null` por ahora (stub: Fase D trae APNs/FCM en iOS).
 */
expect suspend fun tokenPushActual(): String?

private const val PLATAFORMA_ANDROID = "android"

/**
 * Registra/desregistra el token push del dispositivo actual contra el
 * backend (`POST`/`DELETE /dispositivos-push`, Task 7 — ya autenticado con la
 * sesión normal vía `ApiCliente`). Errores silenciosos a propósito: push es
 * una mejora, nunca debe bloquear ni mostrar error al login/logout si falla
 * (p. ej. sin Firebase, sin Play Services, o sin conexión un momento).
 *
 * `obtenerToken` se inyecta (default [tokenPushActual]) para que los tests
 * puedan simular el token sin depender del `expect/actual` real de la
 * plataforma — ver `RegistroPushRepositorioTest.kt`.
 */
class RegistroPushRepositorio(
    private val api: ApiCliente,
    private val obtenerToken: suspend () -> String? = { tokenPushActual() },
) {

    /** Pide el token actual y lo registra en el backend. No-op si no hay token. */
    suspend fun registrar(): Resultado<Unit> {
        val token = obtenerToken() ?: return Resultado.Ok(Unit)
        return when (
            val respuesta = api.post<RegistrarDispositivoRequestDto, DispositivoPushResponseDto>(
                path = "/dispositivos-push",
                body = RegistrarDispositivoRequestDto(token = token, plataforma = PLATAFORMA_ANDROID),
                requiereSesion = true,
            )
        ) {
            is Resultado.Ok -> Resultado.Ok(Unit)
            is Resultado.Error -> respuesta
        }
    }

    /** Pide el token actual y lo da de baja en el backend. No-op si no hay token. */
    suspend fun desregistrar(): Resultado<Unit> {
        val token = obtenerToken() ?: return Resultado.Ok(Unit)
        return when (
            val respuesta = api.delete<DesregistrarDispositivoRequestDto, DispositivoPushResponseDto>(
                path = "/dispositivos-push",
                body = DesregistrarDispositivoRequestDto(token = token),
            )
        ) {
            is Resultado.Ok -> Resultado.Ok(Unit)
            is Resultado.Error -> respuesta
        }
    }
}
