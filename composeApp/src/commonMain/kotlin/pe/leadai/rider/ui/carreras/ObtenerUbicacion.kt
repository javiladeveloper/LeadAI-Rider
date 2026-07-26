package pe.leadai.rider.ui.carreras

/** Posición GPS del rider — lo único que viaja al backend (`POST /motorizados/posicion`). */
data class UbicacionRider(val lat: Double, val lng: Double)

/**
 * Última ubicación conocida del dispositivo (tracking nivel 2: mientras el
 * rider tiene carrera en curso, su posición alimenta el mapa público
 * `/track/:pedidoId` que ve el cliente). `expect/actual` como
 * `tokenPushActual()` (ver `push/RegistroPush.kt`).
 *
 * Devuelve `null` y NUNCA lanza cuando no hay ubicación: permiso denegado,
 * GPS apagado, sin fix todavía, o sin Activity. El tracking es una mejora —
 * jamás rompe la carrera. En Android pide el permiso UNA sola vez por
 * proceso; `iosMain` es stub `null` (Fase D). Tests: se inyecta como lambda
 * en `CarrerasViewModel`, mismo patrón que `RegistroPushRepositorio`.
 */
expect suspend fun obtenerUbicacionActual(): UbicacionRider?
