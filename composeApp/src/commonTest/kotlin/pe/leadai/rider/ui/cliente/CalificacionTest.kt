package pe.leadai.rider.ui.cliente

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import pe.leadai.rider.datos.CarreraClienteDto
import pe.leadai.rider.datos.OfertaDto
import pe.leadai.rider.datos.RiderDeOfertaDto

private fun carrera(id: String = "c1", estado: String = "aceptada") =
    CarreraClienteDto(id = id, estado = estado)

class CalificacionTest {

    @Test
    fun `al desaparecer una carrera con rider se pide calificar`() {
        val antes = ClienteUiState(miCarrera = carrera(estado = "recogida"))

        val despues = alLlegarLaCarrera(antes, nueva = null)

        assertEquals("c1", despues.carreraPorCalificar?.id)
        assertNull(despues.miCarrera)
    }

    @Test
    fun `entregada tambien dispara la calificacion`() {
        val antes = ClienteUiState(miCarrera = carrera(estado = "aceptada"))

        val despues = alLlegarLaCarrera(antes, nueva = carrera(estado = "entregada"))

        assertEquals("c1", despues.carreraPorCalificar?.id)
    }

    @Test
    fun `una carrera que nadie tomo no se califica`() {
        // Venció sin rider: no hay a quién puntuar.
        val antes = ClienteUiState(miCarrera = carrera(estado = "disponible"))

        val despues = alLlegarLaCarrera(antes, nueva = null)

        assertNull(despues.carreraPorCalificar)
    }

    @Test
    fun `mientras la carrera sigue en curso no se pregunta nada`() {
        val antes = ClienteUiState(miCarrera = carrera(estado = "aceptada"))

        val despues = alLlegarLaCarrera(antes, nueva = carrera(estado = "recogida"))

        assertNull(despues.carreraPorCalificar)
        assertEquals("recogida", despues.miCarrera?.estado)
    }

    @Test
    fun `tras omitir no vuelve a preguntar por la misma carrera`() {
        // El polling sigue devolviendo null cada 5s: sin esta guarda, el
        // diálogo reaparecería para siempre después de tocar "Ahora no".
        val yaOmitida = ClienteUiState(miCarrera = null, carreraPorCalificar = null)

        val despues = alLlegarLaCarrera(yaOmitida, nueva = null)

        assertNull(despues.carreraPorCalificar)
    }

    @Test
    fun `al cerrarse la carrera se limpian las ofertas viejas`() {
        // Si no, la próxima carrera arranca mostrando propuestas de la anterior.
        val antes = ClienteUiState(
            miCarrera = carrera(estado = "aceptada"),
            ofertas = listOf(
                OfertaDto(id = "o1", montoCentavos = 600, rider = RiderDeOfertaDto()),
            ),
        )

        val despues = alLlegarLaCarrera(antes, nueva = null)

        assertEquals(emptyList(), despues.ofertas)
    }

    @Test
    fun `sin carrera previa no inventa una calificacion`() {
        val despues = alLlegarLaCarrera(ClienteUiState(), nueva = null)

        assertNull(despues.carreraPorCalificar)
    }
}
