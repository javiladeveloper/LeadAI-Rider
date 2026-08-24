package pe.leadai.rider.ui.cliente

import pe.leadai.rider.datos.CarreraClienteDto
import pe.leadai.rider.ui.cliente.componentes.FiltroViajes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Los filtros del historial de viajes.
 *
 * La lista mezclaba los viajes que se hicieron con los que nadie tomó y los
 * cancelados, todos con la misma cara. Quien entra acá casi siempre viene a
 * ver lo que SÍ pasó —para un reclamo, o para acordarse de cuánto pagó— y eso
 * quedaba enterrado entre pedidos que nunca existieron.
 */
class FiltroViajesTest {

    private fun carrera(estado: String) = CarreraClienteDto(
        id = "c-$estado",
        tipo = "pasajero",
        estado = estado,
        origenTexto = "Mercado Central",
        destinoTexto = "Colegio FAZ",
        montoOfrecido = 800,
    )

    @Test
    fun completados_son_SOLO_los_que_llegaron_a_destino() {
        val filtro = FiltroViajes.COMPLETADOS

        assertTrue(filtro.incluye(carrera("entregada")))
        // Un pedido que nadie tomó no es un viaje que se hizo.
        assertFalse(filtro.incluye(carrera("expirada")))
        assertFalse(filtro.incluye(carrera("cancelada")))
    }

    @Test
    fun sin_concretar_junta_canceladas_y_expiradas() {
        // Van juntas a propósito: para el cliente son la misma categoría
        // —"esto no pasó"— y separarlas obligaría a mirar en dos lugares para
        // reclamar por algo que salió mal.
        val filtro = FiltroViajes.SIN_CONCRETAR

        assertTrue(filtro.incluye(carrera("cancelada")))
        assertTrue(filtro.incluye(carrera("expirada")))
        assertFalse(filtro.incluye(carrera("entregada")))
    }

    @Test
    fun todos_no_esconde_nada() {
        // El filtro por defecto no puede perder viajes: si alguien busca uno
        // y no aparece en "Todos", deja de confiar en la lista entera.
        val estados = listOf("entregada", "cancelada", "expirada", "aceptada", "recogida")

        assertTrue(estados.all { FiltroViajes.TODOS.incluye(carrera(it)) })
    }

    @Test
    fun un_estado_DESCONOCIDO_cae_en_sin_concretar_no_se_pierde() {
        // Si el backend agrega un estado nuevo mañana, la carrera tiene que
        // seguir apareciendo en algún lado. Perderla en silencio es peor que
        // mostrarla en la categoría equivocada.
        val rara = carrera("estado_que_no_existe_todavia")

        assertTrue(FiltroViajes.TODOS.incluye(rara))
        assertTrue(FiltroViajes.SIN_CONCRETAR.incluye(rara))
        assertFalse(FiltroViajes.COMPLETADOS.incluye(rara))
    }

    @Test
    fun cada_carrera_cae_en_exactamente_un_filtro_ademas_de_todos() {
        // Sin esto, los conteos de los chips no cerrarían: la suma de
        // "Completados" y "Sin concretar" tiene que dar el total.
        val historial = listOf(
            carrera("entregada"),
            carrera("entregada"),
            carrera("cancelada"),
            carrera("expirada"),
        )

        val completados = historial.count(FiltroViajes.COMPLETADOS::incluye)
        val sinConcretar = historial.count(FiltroViajes.SIN_CONCRETAR::incluye)

        assertEquals(historial.size, completados + sinConcretar)
        assertEquals(2, completados)
        assertEquals(2, sinConcretar)
    }

    @Test
    fun con_historial_vacio_los_conteos_son_cero_y_no_revientan() {
        val vacio = emptyList<CarreraClienteDto>()

        assertTrue(FiltroViajes.entries.all { f -> vacio.count(f::incluye) == 0 })
    }
}
