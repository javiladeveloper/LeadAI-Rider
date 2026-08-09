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
 * jamás rompe la carrera. `iosMain` es stub `null` (Fase D). Tests: se
 * inyecta como lambda en `CarrerasViewModel`, mismo patrón que
 * `RegistroPushRepositorio`.
 *
 * @param loPidioElUsuario si es un TOQUE EXPLÍCITO (el botón "usar mi
 * ubicación") o un intento automático al abrir la pantalla.
 *
 * La diferencia importa. En automático el permiso se pide una sola vez por
 * proceso: insistir en cada pantalla sería un diálogo cada dos por tres. Pero
 * cuando el usuario TOCA el botón está pidiendo justo eso, y ahí hay que
 * volver a preguntar — si no, un "ahora no" de la primera vez deja el botón
 * muerto para siempre, sin ninguna señal de por qué.
 */
expect suspend fun obtenerUbicacionActual(
    loPidioElUsuario: Boolean = false,
): UbicacionRider?
