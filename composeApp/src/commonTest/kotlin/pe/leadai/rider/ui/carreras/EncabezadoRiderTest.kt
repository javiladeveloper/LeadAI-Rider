package pe.leadai.rider.ui.carreras

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El interruptor de turno no puede disparar dos veces con UN toque.
 *
 * La fila del turno es `clickable` y adentro tiene un `Switch`. Cuando el
 * Switch también manejaba su `onCheckedChange`, un solo toque ejecutaba los
 * dos manejadores con valores OPUESTOS: encendía y apagaba. La guarda
 * `cambiandoTurno` tapaba la segunda llamada casi siempre, pero si la primera
 * ya había respondido, la segunda apagaba el turno recién encendido.
 *
 * El rider quedaba fuera de turno sin tocar nada, y su moto desaparecía del
 * radar del cliente a los pocos segundos.
 *
 * Sin infraestructura de UI en commonTest, se verifica sobre el FUENTE: el
 * Switch tiene que delegar el toque a la fila (`onCheckedChange = null`).
 * Es un test humilde, pero cubre exactamente la regresión que ocurrió — un
 * test de ViewModel no la ve, porque el doble disparo pasa antes de llegar
 * ahí.
 */
class EncabezadoRiderTest {

    private val fuente: String by lazy {
        val rutas = listOf(
            "src/commonMain/kotlin/pe/leadai/rider/ui/carreras/componentes/EncabezadoRider.kt",
            "composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/carreras/componentes/EncabezadoRider.kt",
        )
        rutas.firstNotNullOfOrNull { ruta ->
            runCatching { java.io.File(ruta).takeIf { it.exists() }?.readText() }.getOrNull()
        } ?: error("No se encontró EncabezadoRider.kt")
    }

    @Test
    fun el_switch_del_turno_delega_el_toque_a_la_fila() {
        val bloqueSwitch = fuente.substringAfter("Switch(").substringBefore(")")
        assertTrue(
            bloqueSwitch.contains("onCheckedChange = null"),
            "el Switch debe delegar en la fila: si maneja el toque, un tap " +
                "dispara DOS cambios con valores opuestos y apaga el turno",
        )
    }

    @Test
    fun la_fila_del_turno_sigue_siendo_tocable() {
        // El arreglo no puede dejar el interruptor muerto: la fila es la que
        // maneja el toque ahora, así que tiene que seguir teniendo `clickable`.
        assertTrue(
            fuente.contains("onCambiar(!enTurno)"),
            "la fila tiene que seguir manejando el toque",
        )
        assertFalse(
            fuente.contains("onCheckedChange = { if (!cambiando) onCambiar(it) }"),
            "el manejador duplicado del Switch no debe volver",
        )
    }
}
