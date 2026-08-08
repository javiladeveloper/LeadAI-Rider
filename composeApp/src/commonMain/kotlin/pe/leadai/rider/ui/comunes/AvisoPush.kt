package pe.leadai.rider.ui.comunes

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * El timbre que avisa "llegó un push, andá a buscar novedades".
 *
 * El push entra al instante, pero las pantallas solo se enteraban en el
 * siguiente ciclo de polling: el rider veía "nueva carrera" en la barra de
 * Android y al abrir la app la lista seguía vacía unos segundos. La app
 * quedaba atrasada respecto de su propio aviso.
 *
 * Acá el servicio de Firebase toca el timbre y quien esté en pantalla refresca
 * de una. El polling queda como red de seguridad para cuando el push no llega
 * —que pasa: sin red, con la batería restringida, o si Google lo demora—.
 *
 * Es un `SharedFlow` sin réplica a propósito: un aviso viejo no sirve para
 * nada, y no queremos que una pantalla que recién abre refresque por un push
 * de hace media hora.
 */
object AvisoPush {
    private val _avisos = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Los avisos que van llegando. El valor es el `hito`, si vino alguno. */
    val avisos: SharedFlow<String> = _avisos.asSharedFlow()

    /** Lo llama el servicio de push al recibir un mensaje. */
    fun avisar(hito: String?) {
        _avisos.tryEmit(hito ?: "")
    }
}
