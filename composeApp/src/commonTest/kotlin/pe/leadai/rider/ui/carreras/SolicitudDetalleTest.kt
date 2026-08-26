package pe.leadai.rider.ui.carreras

import pe.leadai.rider.ui.carreras.componentes.formatearKm
import pe.leadai.rider.ui.carreras.componentes.montosSugeridos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * LOS MONTOS DE UN TOQUE de la solicitud con mapa.
 *
 * Escribir el monto es la fricción que hace que el rider simplemente acepte o
 * ignore: en la calle, con casco y apurado, nadie tipea. Estos botones son la
 * alternativa, así que tienen que dar montos usables en todo el rango real
 * —de una carrera de S/4 a una de S/30— y verse como plata, no como el
 * resultado de una multiplicación.
 */
class SolicitudDetalleTest {

    @Test
    fun siempre_ofrecen_MAS_que_lo_ofrecido() {
        // Un botón que ofrezca lo mismo o menos no tiene sentido: para eso
        // está "Aceptar".
        listOf(400L, 500L, 1250L, 3000L).forEach { base ->
            montosSugeridos(base).forEach { m ->
                assertTrue(m > base, "con base $base salió $m, que no es más")
            }
        }
    }

    @Test
    fun van_de_menor_a_mayor() {
        // Desordenados obligan a leer los tres antes de elegir.
        val m = montosSugeridos(1000L)

        assertEquals(m.sorted(), m, "tienen que ir en orden: $m")
    }

    @Test
    fun terminan_en_decenas_de_centimos() {
        // "S/ 6.13" en un botón se lee como un error de la app.
        listOf(413L, 777L, 1234L).forEach { base ->
            montosSugeridos(base).forEach { m ->
                assertEquals(0L, m % 10, "con base $base salió $m, que no es redondo")
            }
        }
    }

    @Test
    fun escalan_con_el_monto_y_no_con_pasos_fijos() {
        // +S/1 sobre S/5 es muchísimo; sobre S/25 no se nota. Por eso son
        // porcentajes: el salto del monto grande tiene que ser mayor.
        val saltoChico = montosSugeridos(500L).first() - 500L
        val saltoGrande = montosSugeridos(2500L).first() - 2500L

        assertTrue(
            saltoGrande > saltoChico,
            "el salto debería crecer con el monto: $saltoChico vs $saltoGrande",
        )
    }

    @Test
    fun sin_monto_ofrecido_igual_muestra_algo_usable() {
        // Un cero dejaría tres botones de "S/ 0.00" y el rider sin forma de
        // ofertar.
        val m = montosSugeridos(0L)

        assertEquals(3, m.size)
        assertTrue(m.all { it > 0 }, "no puede haber botones en cero: $m")
    }

    @Test
    fun el_kilometraje_se_lee_como_lo_diria_alguien() {
        // "0.8 km" no se dice; "800 m" sí.
        assertEquals("800 m", formatearKm(0.8))
        assertEquals("1,4 km", formatearKm(1.42))
    }

    @Test
    fun una_distancia_de_cero_no_rompe_el_texto() {
        // Pasa cuando el rider ya está encima del punto de recojo.
        assertEquals("0 m", formatearKm(0.0))
    }
}
