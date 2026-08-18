package pe.leadai.rider.ui.cliente

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Las duraciones de la app tienen que COINCIDIR con las del backend.
 *
 * La app repite estos números para dibujar las barras de tiempo sin esperar
 * una respuesta. Cuando el backend bajó la vigencia de 90s a 45 y la
 * expiración de 15 min a 5, la app quedó con los valores viejos: la barra
 * arrancaba por la mitad y se vaciaba al doble de lento que el reloj de al
 * lado. Se veía "con delay" aunque el conteo estuviera bien.
 *
 * Sin infraestructura para leer el backend desde acá, se verifica sobre el
 * FUENTE. Es un test humilde, pero cubre exactamente la desincronización que
 * ocurrió — y que es invisible en pantalla hasta que alguien mira fijo.
 */
class DuracionesTest {

    /**
     * Un archivo del BACKEND, que vive en otro repo al lado de este.
     *
     * Si no está —alguien clonó solo la app—, el test se salta en vez de
     * fallar: no tiene sentido romper la build por no poder comparar.
     */
    private fun fuenteBackend(ruta: String): String? {
        val rutas = listOf("../leadia/$ruta", "../../leadia/$ruta")
        return rutas.firstNotNullOfOrNull { r ->
            runCatching { java.io.File(r).takeIf { it.exists() }?.readText() }.getOrNull()
        }
    }

    private fun fuente(ruta: String): String {
        val rutas = listOf(ruta, "composeApp/$ruta")
        return rutas.firstNotNullOfOrNull { r ->
            runCatching { java.io.File(r).takeIf { it.exists() }?.readText() }.getOrNull()
        } ?: error("No se encontró $ruta")
    }

    @Test
    fun la_vigencia_de_la_oferta_coincide_con_el_backend() {
        // `SEGUNDOS_VIGENCIA_OFERTA` en src/core/ofertas.ts
        val texto = fuente("src/commonMain/kotlin/pe/leadai/rider/ui/cliente/componentes/OfertasRecibidas.kt")
        val valor = Regex("""SEGUNDOS_VIGENCIA = (\d+)f""").find(texto)?.groupValues?.get(1)

        assertEquals("45", valor, "el backend manda 45s: una barra con otro número se ve rota")
    }

    @Test
    fun la_duracion_de_la_busqueda_coincide_con_el_backend() {
        // `MINUTOS_HASTA_EXPIRAR` en src/core/carreras.ts
        val texto = fuente("src/commonMain/kotlin/pe/leadai/rider/ui/cliente/componentes/EstadoBusqueda.kt")
        val minutos = Regex("""SEGUNDOS_BUSQUEDA = (\d+) \* 60""").find(texto)?.groupValues?.get(1)

        // El número esperado se lee del BACKEND, no se escribe acá.
        //
        // Antes decía `assertEquals("5", ...)` a mano: cuando el backend bajó
        // a 2 minutos, el test siguió verde y la barra quedó calculando sobre
        // 5 —arrancaba al 40%—. Un test que hay que actualizar a mano cuando
        // cambia la otra punta no está comparando nada.
        val backend = fuenteBackend("src/core/carreras.ts") ?: return
        val delBackend = Regex("""MINUTOS_HASTA_EXPIRAR = (\d+)""")
            .find(backend)
            ?.groupValues?.get(1)

        assertEquals(
            delBackend,
            minutos,
            "la barra tiene que durar lo mismo que la carrera: backend=$delBackend app=$minutos",
        )
    }

    @Test
    fun el_minimo_coincide_con_el_backend() {
        // `MINIMO_CENTAVOS` en src/core/sugerencia.ts. Si acá fuera menor, los
        // botones dejarían ofrecer un monto que el servidor rechaza.
        val texto = fuente("src/commonMain/kotlin/pe/leadai/rider/ui/cliente/componentes/PopupPrecio.kt")
        val valor = Regex("""MINIMO_ABSOLUTO_CENTAVOS = (\d+)L""").find(texto)?.groupValues?.get(1)

        assertEquals("500", valor, "el mínimo es S/5.00 en los dos lados")
    }
}
