package pe.leadai.rider.ui.comunes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import pe.leadai.rider.esperarCondicion
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `AvisosGlobales`: el `SharedFlow` que `HomeShell` colecciona para dibujar
 * el `SnackbarHost` global (Task B7, ítem c). Cubre que un aviso emitido
 * ANTES de que exista un colector no se pierde para el PRÓXIMO `mostrar()`
 * (SharedFlow sin buffer no "recuerda" el pasado, pero tampoco debe romper
 * la emisión siguiente) y que un colector activo sí recibe lo que se avisa
 * mientras está escuchando.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AvisosGlobalesTest {

    @Test
    fun mostrar_emite_el_mensaje_a_un_colector_activo() = runTest {
        val avisos = AvisosGlobales()
        // El colector arranca ANTES de `mostrar()` — mismo orden que en la app
        // real: `HomeShell` colecciona `avisos.avisos` en un `LaunchedEffect`
        // que vive mientras el shell está en pantalla, y recién ENTONCES un
        // ViewModel llama `mostrar()` en respuesta a una acción del usuario.
        val recibidoDeferred = async {
            avisos.avisos.esperarCondicion { it == "Guardado ✓" }
        }
        yield() // deja que el `async` llegue a `collect` antes de emitir

        avisos.mostrar("Guardado ✓")

        assertEquals("Guardado ✓", recibidoDeferred.await())
    }

    @Test
    fun mostrar_varias_veces_emite_cada_mensaje_en_orden() = runTest {
        val avisos = AvisosGlobales()
        val recibidos = mutableListOf<String>()

        val colector = launch(Dispatchers.Unconfined) {
            avisos.avisos.collect { recibidos.add(it) }
        }

        avisos.mostrar("Guardado ✓")
        avisos.mostrar("No pudimos actualizar el pedido. Intenta de nuevo.")

        // Con Dispatchers.Unconfined el colector procesa cada emisión al instante.
        assertEquals(listOf("Guardado ✓", "No pudimos actualizar el pedido. Intenta de nuevo."), recibidos)
        colector.cancel()
    }

    @Test
    fun mostrar_sin_colectores_no_lanza_y_no_bloquea() {
        val avisos = AvisosGlobales()

        // No debe lanzar excepción aunque nadie esté escuchando.
        avisos.mostrar("Mensaje al vacío")
        avisos.mostrar("Otro mensaje")
    }
}
