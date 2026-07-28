package pe.leadai.rider.ui.carreras

import pe.leadai.rider.datos.CarreraDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun carrera(
    tipo: String = "pedido",
    recogido: Boolean = false,
    montoCompraEstimado: Long? = null,
) = CarreraDto(
    pedidoId = "p1",
    tipo = tipo,
    negocio = "El Pollon",
    origenTexto = "El Pollon",
    destinoTexto = "Jose Olaya 110",
    montoOfrecido = 800,
    montoCompraEstimado = montoCompraEstimado,
    creadoEn = "2026-07-28T10:00:00.000Z",
    recogido = recogido,
)

class TipoCarreraTest {

    @Test
    fun cada_tipo_tiene_su_etiqueta() {
        assertEquals("🍽️ Delivery", etiquetaTipo("pedido"))
        assertEquals("🛍️ Mandado", etiquetaTipo("mandado"))
        assertEquals("📦 Encomienda", etiquetaTipo("encomienda"))
        assertEquals("🚕 Pasajero", etiquetaTipo("pasajero"))
    }

    @Test
    fun un_tipo_desconocido_no_rompe_la_pantalla() {
        // El backend podría agregar un tipo nuevo antes de que la app se
        // actualice: mejor una etiqueta genérica que un crash.
        assertEquals("🛵 Carrera", etiquetaTipo("teletransporte"))
    }

    @Test
    fun el_titulo_dice_en_que_tramo_va() {
        assertEquals("📦 Recoge en el local", tituloTramo(carrera(tipo = "pedido")))
        assertEquals("🛵 Llevando el pedido", tituloTramo(carrera(tipo = "pedido", recogido = true)))
    }

    @Test
    fun el_pasajero_no_se_recoge_se_pasa_a_buscar() {
        assertEquals("🚕 Pasa a buscarlo", tituloTramo(carrera(tipo = "pasajero")))
        assertEquals("🚕 Llevando al pasajero", tituloTramo(carrera(tipo = "pasajero", recogido = true)))
    }

    @Test
    fun el_mandado_dice_que_hay_que_comprar() {
        assertEquals("🛍️ Ve a comprar", tituloTramo(carrera(tipo = "mandado")))
        assertEquals("🛵 Llevando la compra", tituloTramo(carrera(tipo = "mandado", recogido = true)))
    }

    @Test
    fun solo_es_mandado_si_tiene_monto_de_compra() {
        assertTrue(esMandado(carrera(tipo = "mandado", montoCompraEstimado = 6000)))
        // Un mandado sin monto declarado no debe mostrar "llevas S/0".
        assertFalse(esMandado(carrera(tipo = "mandado", montoCompraEstimado = null)))
        assertFalse(esMandado(carrera(tipo = "pasajero", montoCompraEstimado = 6000)))
    }
}
