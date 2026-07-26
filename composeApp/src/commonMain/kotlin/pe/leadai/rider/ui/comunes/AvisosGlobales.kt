package pe.leadai.rider.ui.comunes

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Mecanismo simple para que CUALQUIER pantalla muestre un mensaje efímero
 * (snackbar) sin que cada una tenga que traer su propio `SnackbarHostState`
 * ni que `HomeShell` conozca de antemano qué pantallas van a avisar algo
 * (Task B7, ítem c). Koin `single` — una sola instancia compartida por toda
 * la app.
 *
 * **Criterio snackbar vs `EstadoError`/`BannerError` persistente** (documentado
 * acá porque es la pregunta que decide dónde va cada mensaje nuevo):
 * - **Snackbar (`AvisosGlobales`)**: el resultado de una ACCIÓN puntual que
 *   el usuario disparó a propósito — confirmaciones ("Guardado ✓") y
 *   errores de un botón/gesto aislado (p.ej. `avanzar()` de Cocina falló
 *   para UN pedido). Aparece, se lee, desaparece solo — no bloquea nada y no
 *   necesita seguir visible después de que el usuario lo vio.
 * - **`EstadoError`/`BannerError` persistente**: el resultado de una CARGA
 *   (`cargar()`/`refrescar()` de una pantalla completa) que dejó a la
 *   pantalla sin datos que mostrar, o un problema que sigue vigente mientras
 *   el usuario no reintente — necesita quedarse en pantalla con un botón
 *   "Reintentar", porque un snackbar que desaparece a los pocos segundos
 *   escondería un estado que todavía es cierto.
 *
 * `SharedFlow` (no `StateFlow`): un snackbar es un EVENTO, no un estado — si
 * nadie está coleccionando cuando se emite, no debe "quedar pegado" para el
 * próximo colector que aparezca (a diferencia de `StateFlow`, que siempre
 * repite el último valor). `replay = 0` es el default de `MutableSharedFlow()`,
 * así que no hace falta configurarlo explícito.
 */
class AvisosGlobales {

    private val _avisos = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Colectado por `HomeShell` (único punto de la app con `SnackbarHost`). */
    val avisos: SharedFlow<String> = _avisos.asSharedFlow()

    /**
     * Encola [mensaje] para mostrarse como snackbar. `tryEmit` (no
     * `emit` suspend): los ViewModels que llaman esto no deberían tener que
     * lanzar una corrutina solo para avisar un mensaje; con
     * `extraBufferCapacity = 8` la emisión no bloquea ni se pierde en el uso
     * normal (como mucho unos pocos avisos en vuelo a la vez).
     */
    fun mostrar(mensaje: String) {
        _avisos.tryEmit(mensaje)
    }
}
