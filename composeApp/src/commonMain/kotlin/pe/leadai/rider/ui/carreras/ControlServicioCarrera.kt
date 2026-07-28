package pe.leadai.rider.ui.carreras

/**
 * Arranca y para el reporte de GPS en SEGUNDO PLANO. En Android es un
 * foreground service (`ServicioCarreraActiva`); en iOS todavía no hace nada
 * (Fase D: CoreLocation con `allowsBackgroundLocationUpdates`).
 *
 * `expect/actual` — mismo patrón que `obtenerUbicacionActual()` y
 * `tokenPushActual()`.
 *
 * [destino] va en la notificación persistente: al rider le sirve ver a dónde
 * va sin desbloquear, y Android exige que la notificación diga algo.
 */
expect fun iniciarServicioCarrera(destino: String)

/** Idempotente: parar un servicio que no corre es un no-op seguro. */
expect fun detenerServicioCarrera()
