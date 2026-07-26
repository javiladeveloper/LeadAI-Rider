package pe.leadai.rider

import pe.leadai.rider.ui.tema.centavosASoles
import pe.leadai.rider.ui.tema.epochMsDesdeIso
import pe.leadai.rider.ui.tema.haceMinutos
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatoTest {
    @Test
    fun centavos_a_soles() {
        assertEquals("S/38.00", centavosASoles(3800))
        assertEquals("S/482.50", centavosASoles(48250))
        assertEquals("S/0.00", centavosASoles(0))
    }

    @Test
    fun hace_minutos() {
        val ahora = 1_000_000_000L
        assertEquals("ahora", haceMinutos(ahora - 30_000, ahora))
        assertEquals("hace 4 min", haceMinutos(ahora - 4 * 60_000, ahora))
        assertEquals("hace 2 h", haceMinutos(ahora - 2 * 3_600_000, ahora))
    }

    @Test
    fun epoch_ms_desde_iso_parsea_timestamps_utc_del_backend() {
        // Verificado contra epoch real (Python datetime UTC).
        assertEquals(1_784_628_600_000L, epochMsDesdeIso("2026-07-21T10:10:00.000Z"))
        assertEquals(1_784_627_940_000L, epochMsDesdeIso("2026-07-21T09:59:00.000Z"))
        // Epoch cero.
        assertEquals(0L, epochMsDesdeIso("1970-01-01T00:00:00.000Z"))
        // Año bisiesto (2028-02-29 existe).
        assertEquals(1_835_395_200_000L, epochMsDesdeIso("2028-02-29T00:00:00.000Z"))
    }
}
