package pe.leadai.rider.ui.cliente

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import pe.leadai.rider.esperarCondicion

/**
 * Cómo se comporta el buscador de direcciones mientras se escribe.
 *
 * Dos cosas que se reportaron probando en el emulador: que buscaba lento, y
 * que "no agarra mi ubicación" —los resultados salían de Lima estando en
 * Tacna—. Las dos salían del mismo lugar: la búsqueda esperaba al GPS,
 * en pleno tecleo y hasta 8 segundos.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BuscadorDireccionesTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest fun antes() { Dispatchers.setMain(testDispatcher) }
    @AfterTest fun despues() { Dispatchers.resetMain() }

    /**
     * Espera a que la búsqueda llegue al motor.
     *
     * `advanceUntilIdle` no alcanza: la petición viaja con I/O real —MockEngine
     * y DataStore leyendo la sesión— que el reloj virtual no adelanta. Mismo
     * perro guardián de 5s que usa el resto de los tests del repo.
     */
    private suspend fun esperarLaLlamada(pedidas: MutableList<String>) {
        flow { while (true) { emit(pedidas.toList()); delay(10) } }
            .esperarCondicion { it.isNotEmpty() }
    }

    /** Anota cada `GET /carreras/direcciones` que sale de verdad. */
    private fun motorQueAnota(pedidas: MutableList<String>) = MockEngine { req ->
        val url = req.url.toString()
        if ("/carreras/direcciones" in url) pedidas.add(url)
        respond(
            content = when {
                "/carreras/direcciones" in url -> "{\"sugerencias\":[]}"
                "/carreras/mias" in url -> "{\"carreras\":[]}"
                else -> "{}"
            },
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }

    @Test
    fun busca_cuando_deja_de_escribir_no_en_cada_tecla() = runTest {
        val pedidas = mutableListOf<String>()
        // Dispatcher REAL, como en el teléfono: con el de test el trabajo se
        // encola y la cancelación entre teclas no alcanza a correr, así que el
        // test no vería el problema que justamente tiene que cuidar.
        val vm = ClienteViewModelPruebas.conGps(
            motorQueAnota(pedidas),
            Dispatchers.Default,
        )

        // Escribe "jose olaya" letra por letra, rápido, como cualquiera.
        //
        // Con pausas REALES entre teclas, no con el reloj virtual: el `delay`
        // del buscador corre en tiempo real, así que adelantar el reloj de
        // `runTest` no lo destraba y el test pasaba aunque se buscara por
        // tecla —no servía de nada—.
        val texto = "jose olaya"
        withContext(Dispatchers.Default) {
            for (i in 3..texto.length) {
                vm.cambiarDestino(texto.take(i))
                delay(120) // entre tecla y tecla se tarda mucho menos que la espera
            }
            // Ya dejó de escribir: recién ahora debe salir la búsqueda.
            delay(1200)
        }
        esperarLaLlamada(pedidas)

        // UNA sola llamada: la de lo que terminó de escribir. Antes cada tecla
        // disparaba la suya y la lista parpadeaba con resultados a medio
        // escribir ("jose ola") que enseguida se reemplazaban.
        assertEquals(1, pedidas.size, "debe buscar al terminar, no por tecla: $pedidas")
        assertTrue("jose%20olaya" in pedidas[0], "busca el texto completo: ${pedidas[0]}")
    }

    @Test
    fun la_busqueda_lleva_las_coordenadas_del_cliente() = runTest {
        val pedidas = mutableListOf<String>()
        val vm = ClienteViewModelPruebas.conGps(motorQueAnota(pedidas), testDispatcher)

        // `cargar()` deja la ubicación lista antes de que se escriba.
        vm.cargar()
        advanceUntilIdle()
        vm.cambiarDestino("jose olaya")
        advanceUntilIdle()
        esperarLaLlamada(pedidas)

        // Sin coordenadas el backend no sabe la ciudad y cae a Lima: buscar
        // "jose olaya 110" desde Tacna devolvía una oficina de Lima.
        assertEquals(1, pedidas.size, "una llamada: $pedidas")
        assertTrue("lat=" in pedidas[0] && "lng=" in pedidas[0], "faltan coords: ${pedidas[0]}")
    }

    @Test
    fun si_el_gps_no_contesta_igual_se_puede_buscar() = runTest {
        val pedidas = mutableListOf<String>()
        // Emulador recién arrancado: el GPS no da nada.
        val vm = ClienteViewModelPruebas.sinGps(motorQueAnota(pedidas), testDispatcher)

        vm.cambiarDestino("jose olaya")
        advanceUntilIdle()
        esperarLaLlamada(pedidas)

        // Sale sin coordenadas —peor que con ellas, pero muchísimo mejor que
        // un buscador que se queda cargando esperando un GPS que no viene—.
        assertEquals(1, pedidas.size, "debe buscar igual sin GPS: $pedidas")
    }
}
