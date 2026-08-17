package pe.leadai.rider.ui.carreras

// Stub: el tracking en segundo plano en iOS llega con Fase D (CoreLocation
// con allowsBackgroundLocationUpdates). Mismo criterio que el resto de los
// `actual` de iosMain — ver ObtenerUbicacion.ios.kt / RegistroPush.ios.kt.
// `true` a propósito: no hay nada que arrancar, pero devolver `false` haría
// que la pantalla reintente en un loop para siempre esperando algo que hoy no
// existe. Cuando llegue Fase D, esto devolverá si CoreLocation arrancó.
actual fun iniciarServicioCarrera(destino: String): Boolean = true

actual fun detenerServicioCarrera() = Unit

/** iOS no tiene notificacion de servicio todavia (Fase D): no hay de donde salir. */
actual fun alSalirDeTurnoDesdeNotificacion(accion: () -> Unit) = Unit
