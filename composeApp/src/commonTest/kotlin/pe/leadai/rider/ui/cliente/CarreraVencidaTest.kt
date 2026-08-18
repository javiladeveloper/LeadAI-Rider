package pe.leadai.rider.ui.cliente

import pe.leadai.rider.datos.CarreraClienteDto
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cuando se acaba el tiempo, se dice.
 *
 * El backend deja de devolver la carrera al vencer, así que la pantalla volvía
 * sola al formulario sin decir NADA: el cronómetro llegaba a cero, el radar
 * seguía girando, y de golpe estaba de nuevo en el inicio. Se siente como si
 * el pedido se hubiera perdido.
 */
class CarreraVencidaTest {

    private fun carrera(estado: String) = CarreraClienteDto(
        id = "c1",
        tipo = "pasajero",
        estado = estado,
        origenTexto = "Mercado Central",
        destinoTexto = "Colegio FAZ",
        montoOfrecido = 800,
    )

    @Test
    fun si_vence_sin_que_nadie_la_tome_se_avisa() {
        // Estaba buscando —"disponible", sin rider— y el backend deja de
        // devolverla: venció.
        val buscando = ClienteUiState(miCarrera = carrera("disponible"))

        val despues = alLlegarLaCarrera(buscando, nueva = null)

        assertTrue(despues.carreraVencida, "hay que decirle que nadie la tomó")
        assertNull(despues.miCarrera)
    }

    @Test
    fun una_carrera_que_alguien_tomo_no_dispara_el_aviso() {
        // Con rider asignado, que desaparezca es otra cosa —terminó— y tiene
        // su propio camino: se le pide que califique.
        val conRider = ClienteUiState(miCarrera = carrera("aceptada"))

        val despues = alLlegarLaCarrera(conRider, nueva = null)

        assertFalse(despues.carreraVencida, "esa terminó, no venció")
        assertTrue(despues.carreraPorCalificar != null, "se le pide calificar")
    }

    @Test
    fun mientras_sigue_buscando_no_se_avisa_nada() {
        val buscando = ClienteUiState(miCarrera = carrera("disponible"))

        val despues = alLlegarLaCarrera(buscando, nueva = carrera("disponible"))

        assertFalse(despues.carreraVencida, "todavía está buscando")
        assertTrue(despues.miCarrera != null)
    }

    @Test
    fun sin_carrera_previa_no_se_inventa_un_aviso() {
        // Al abrir la app sin nada pendiente: `null` → `null` no es un
        // vencimiento, es que no había carrera.
        val sinNada = ClienteUiState(miCarrera = null)

        val despues = alLlegarLaCarrera(sinNada, nueva = null)

        assertFalse(despues.carreraVencida, "no había carrera que vencer")
    }
}
