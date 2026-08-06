package pe.leadai.rider.ui.carreras

import pe.leadai.rider.datos.ContextoApp

/**
 * `ContextoApp.context` (el `applicationContext`, inicializado en
 * `LeadAIRiderApp.onCreate`) y no el de la Activity: el service tiene que
 * sobrevivir a que el rider cierre la pantalla o bloquee el teléfono — que es
 * justo el caso que este service existe para resolver.
 */
actual fun iniciarServicioCarrera(destino: String): Boolean =
    ServicioCarreraActiva.iniciar(ContextoApp.context, destino)

actual fun detenerServicioCarrera() {
    ServicioCarreraActiva.detener(ContextoApp.context)
}
