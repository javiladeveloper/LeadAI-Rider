package pe.leadai.rider.ui.cliente

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import pe.leadai.rider.ui.cliente.componentes.avisoSegunElMonto
import pe.leadai.rider.ui.cliente.componentes.esBajo
import pe.leadai.rider.ui.cliente.componentes.textoDeReferencia

class PopupPrecioTest {

    @Test
    fun `sin sugerencia no juzga el monto`() {
        // No tenemos con qué comparar: avisar "es poco" sería inventar.
        assertFalse(esBajo(100L, sugeridoCentavos = null))
    }

    @Test
    fun `ofrecer bastante menos que lo sugerido se avisa`() {
        assertTrue(esBajo(400L, sugeridoCentavos = 600L))
        assertEquals("Puede que tarde en aparecer alguien", avisoSegunElMonto(400L, 600L))
    }

    @Test
    fun `un poco menos que lo sugerido NO se avisa`() {
        // S/5.50 sobre S/6.00 es negociación normal, no una carrera muerta:
        // avisar acá entrenaría al cliente a ignorar el aviso.
        assertFalse(esBajo(550L, sugeridoCentavos = 600L))
    }

    @Test
    fun `ofrecer mas de lo sugerido promete respuesta rapida`() {
        assertEquals("Con este monto te van a responder rápido", avisoSegunElMonto(800L, 600L))
    }

    @Test
    fun `la referencia junta distancia y sugerencia`() {
        assertEquals("≈ 3.2 km · sugerido S/6.00", textoDeReferencia(600L, 3.2))
    }

    @Test
    fun `sin distancia ni sugerencia la referencia no queda vacia`() {
        assertEquals("Vos ponés el precio", textoDeReferencia(null, null))
    }

    @Test
    fun `km en cero no se muestra`() {
        // 0 km es "no lo pudimos calcular", no "estás al lado".
        assertEquals("sugerido S/6.00", textoDeReferencia(600L, 0.0))
    }
}
